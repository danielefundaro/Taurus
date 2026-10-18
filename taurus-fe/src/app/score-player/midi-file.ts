/** Scrittura di un file MIDI standard (formato 1) con una traccia di tempo e una traccia per parte. */

export const TICKS_PER_QUARTER = 480;

export interface MidiNoteEvent {
    /** Inizio in semiminime dall'inizio del brano. */
    start: number;
    duration: number;
    key: number;
    velocity: number;
}

export interface MidiTrackData {
    name: string;
    /** Porta MIDI: ogni porta ha 16 canali propri. */
    port: number;
    channel: number;
    /** Programma e banco; assenti per i canali di batteria. */
    program?: number;
    bank?: number;
    notes: MidiNoteEvent[];
}

export interface MidiTempoChange {
    /** Posizione in semiminime. */
    at: number;
    /** Semiminime al minuto. */
    bpm: number;
}

interface RawEvent {
    tick: number;
    /** Ordine a parità di tick: meta e controlli prima, poi note-off, poi note-on. */
    rank: number;
    bytes: number[];
}

export function writeMidiFile(tempo: MidiTempoChange[], tracks: MidiTrackData[]): Uint8Array {
    const chunks = [header(tracks.length + 1), trackChunk(tempoEvents(tempo)), ...tracks.map((track) => trackChunk(trackEvents(track)))];
    const total = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
    const file = new Uint8Array(total);
    let offset = 0;
    for (const chunk of chunks) {
        file.set(chunk, offset);
        offset += chunk.length;
    }
    return file;
}

function header(trackCount: number): Uint8Array {
    return Uint8Array.from([0x4d, 0x54, 0x68, 0x64, 0, 0, 0, 6, 0, 1, (trackCount >> 8) & 0xff, trackCount & 0xff, (TICKS_PER_QUARTER >> 8) & 0xff, TICKS_PER_QUARTER & 0xff]);
}

function tempoEvents(tempo: MidiTempoChange[]): RawEvent[] {
    const changes = tempo.length ? tempo : [{ at: 0, bpm: 120 }];
    return changes.map((change) => {
        const microseconds = Math.round(60_000_000 / Math.max(1, change.bpm));
        return { tick: ticks(change.at), rank: 0, bytes: [0xff, 0x51, 0x03, (microseconds >> 16) & 0xff, (microseconds >> 8) & 0xff, microseconds & 0xff] };
    });
}

function trackEvents(track: MidiTrackData): RawEvent[] {
    const channel = track.channel & 0x0f;
    const name = Array.from(new TextEncoder().encode(track.name.slice(0, 60)));
    const events: RawEvent[] = [
        { tick: 0, rank: 0, bytes: [0xff, 0x03, ...variableLength(name.length), ...name] },
        { tick: 0, rank: 0, bytes: [0xff, 0x21, 0x01, track.port & 0x7f] }
    ];
    if (track.program !== undefined) {
        const bank = track.bank ?? 0;
        events.push({ tick: 0, rank: 1, bytes: [0xb0 | channel, 0, (bank >> 7) & 0x7f] });
        events.push({ tick: 0, rank: 1, bytes: [0xb0 | channel, 32, bank & 0x7f] });
        events.push({ tick: 0, rank: 1, bytes: [0xc0 | channel, track.program & 0x7f] });
    }
    events.push({ tick: 0, rank: 1, bytes: [0xb0 | channel, 7, 100] });
    for (const note of track.notes) {
        const key = Math.min(127, Math.max(0, Math.round(note.key)));
        const velocity = Math.min(127, Math.max(1, Math.round(note.velocity)));
        const start = ticks(note.start);
        const end = Math.max(start + 1, ticks(note.start + note.duration));
        events.push({ tick: start, rank: 3, bytes: [0x90 | channel, key, velocity] });
        events.push({ tick: end, rank: 2, bytes: [0x80 | channel, key, 0] });
    }
    return events;
}

function trackChunk(events: RawEvent[]): Uint8Array {
    const sorted = [...events].sort((a, b) => a.tick - b.tick || a.rank - b.rank);
    const body: number[] = [];
    let previous = 0;
    for (const event of sorted) {
        body.push(...variableLength(event.tick - previous), ...event.bytes);
        previous = event.tick;
    }
    body.push(0x00, 0xff, 0x2f, 0x00);
    const length = body.length;
    return Uint8Array.from([0x4d, 0x54, 0x72, 0x6b, (length >>> 24) & 0xff, (length >>> 16) & 0xff, (length >>> 8) & 0xff, length & 0xff, ...body]);
}

export function variableLength(value: number): number[] {
    let remaining = Math.max(0, Math.round(value));
    const bytes = [remaining & 0x7f];
    remaining >>= 7;
    while (remaining > 0) {
        bytes.unshift((remaining & 0x7f) | 0x80);
        remaining >>= 7;
    }
    return bytes;
}

function ticks(quarters: number): number {
    return Math.round(quarters * TICKS_PER_QUARTER);
}
