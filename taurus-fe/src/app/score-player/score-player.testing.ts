import { ScoreMeasure } from './musicxml-score';

/** Battuta di 4/4 senza note, da completare con le indicazioni di struttura del caso di prova. */
export function measure(index: number, overrides: Partial<ScoreMeasure> = {}): ScoreMeasure {
    return {
        index,
        number: String(index + 1),
        length: 4,
        beats: 4,
        beatType: 4,
        notes: [],
        forwardRepeat: false,
        backwardRepeatTimes: 0,
        ending: null,
        segno: false,
        coda: false,
        toCoda: false,
        fine: false,
        daCapo: false,
        dalSegno: false,
        tempo: null,
        ...overrides
    };
}

/** Parte MusicXML score-partwise con le battute indicate. */
export function musicXml(measures: string[], partName = 'Clarinetto in Sib'): string {
    return `<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="4.0">
  <part-list><score-part id="P1"><part-name>${partName}</part-name></score-part></part-list>
  <part id="P1">${measures.map((content, index) => `<measure number="${index + 1}">${content}</measure>`).join('')}</part>
</score-partwise>`;
}

export interface MidiEvent {
    tick: number;
    status: number;
    data: number[];
}

/** Lettura di un file MIDI scritto da `writeMidiFile`: formato, risoluzione ed eventi per traccia, con tick assoluti. */
export function readMidi(file: Uint8Array): { format: number; division: number; tracks: MidiEvent[][] } {
    const view = new DataView(file.buffer, file.byteOffset, file.byteLength);
    expectChunk(file, 0, 'MThd');
    const format = view.getUint16(8);
    const count = view.getUint16(10);
    const division = view.getUint16(12);
    const tracks: MidiEvent[][] = [];
    let offset = 14;
    for (let track = 0; track < count; track++) {
        expectChunk(file, offset, 'MTrk');
        const end = offset + 8 + view.getUint32(offset + 4);
        offset += 8;
        const events: MidiEvent[] = [];
        let tick = 0;
        while (offset < end) {
            const delta = readVariableLength(file, offset);
            offset = delta.next;
            tick += delta.value;
            const status = file[offset++];
            if (status === 0xff) {
                const type = file[offset++];
                const length = readVariableLength(file, offset);
                offset = length.next;
                events.push({ tick, status, data: [type, ...Array.from(file.slice(offset, offset + length.value))] });
                offset += length.value;
            } else {
                const size = (status & 0xf0) === 0xc0 ? 1 : 2;
                events.push({ tick, status, data: Array.from(file.slice(offset, offset + size)) });
                offset += size;
            }
        }
        tracks.push(events);
    }
    return { format, division, tracks };
}

function expectChunk(file: Uint8Array, offset: number, id: string): void {
    const found = String.fromCharCode(...Array.from(file.slice(offset, offset + 4)));
    if (found !== id) throw new Error(`Chunk ${id} atteso, trovato ${found}`);
}

function readVariableLength(file: Uint8Array, offset: number): { value: number; next: number } {
    let value = 0;
    let byte: number;
    do {
        byte = file[offset++];
        value = (value << 7) | (byte & 0x7f);
    } while (byte & 0x80);
    return { value, next: offset };
}
