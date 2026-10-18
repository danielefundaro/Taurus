import { TICKS_PER_QUARTER, variableLength, writeMidiFile } from './midi-file';
import { readMidi } from './score-player.testing';

describe('writeMidiFile', () => {
    it('encodes variable-length quantities', () => {
        expect(variableLength(0)).toEqual([0x00]);
        expect(variableLength(127)).toEqual([0x7f]);
        expect(variableLength(128)).toEqual([0x81, 0x00]);
        expect(variableLength(0x3fff)).toEqual([0xff, 0x7f]);
        expect(variableLength(0x200000)).toEqual([0x81, 0x80, 0x80, 0x00]);
    });

    it('writes a format 1 file with a tempo track and one track per part', () => {
        const file = writeMidiFile(
            [
                { at: 0, bpm: 120 },
                { at: 4, bpm: 60 }
            ],
            [
                { name: 'Clarinetto', port: 0, channel: 0, program: 71, bank: 0, notes: [{ start: 0, duration: 1, key: 62, velocity: 85 }] },
                { name: 'Rullante', port: 1, channel: 9, notes: [] }
            ]
        );
        const midi = readMidi(file);

        expect(midi.format).toBe(1);
        expect(midi.division).toBe(TICKS_PER_QUARTER);
        expect(midi.tracks.length).toBe(3);
        const tempo = midi.tracks[0].filter((event) => event.status === 0xff && event.data[0] === 0x51);
        expect(tempo.map((event) => event.tick)).toEqual([0, 4 * TICKS_PER_QUARTER]);
        expect(tempo[0].data).toEqual([0x51, 0x07, 0xa1, 0x20]);
        expect(tempo[1].data).toEqual([0x51, 0x0f, 0x42, 0x40]);
    });

    it('names the part, sets its port, bank, program and volume, then plays the notes', () => {
        const file = writeMidiFile([{ at: 0, bpm: 100 }], [{ name: 'Tromba', port: 2, channel: 3, program: 56, bank: 130, notes: [{ start: 1, duration: 0.5, key: 60, velocity: 200 }] }]);
        const part = readMidi(file).tracks[1];

        expect(part[0]).toEqual({ tick: 0, status: 0xff, data: [0x03, ...Array.from(new TextEncoder().encode('Tromba'))] });
        expect(part[1]).toEqual({ tick: 0, status: 0xff, data: [0x21, 2] });
        expect(part.filter((event) => event.status === 0xb3).map((event) => event.data)).toEqual([
            [0, 1],
            [32, 2],
            [7, 100]
        ]);
        expect(part.find((event) => event.status === 0xc3)?.data).toEqual([56]);
        expect(part.find((event) => event.status === 0x93)).toEqual({ tick: TICKS_PER_QUARTER, status: 0x93, data: [60, 127] });
        expect(part.find((event) => event.status === 0x83)).toEqual({ tick: 1.5 * TICKS_PER_QUARTER, status: 0x83, data: [60, 0] });
        expect(part[part.length - 1]).toEqual({ tick: 1.5 * TICKS_PER_QUARTER, status: 0xff, data: [0x2f] });
    });

    it('releases repeated notes before striking them again and skips program changes on drum channels', () => {
        const file = writeMidiFile(
            [],
            [
                {
                    name: 'Rullante',
                    port: 0,
                    channel: 9,
                    notes: [
                        { start: 0, duration: 1, key: 38, velocity: 80 },
                        { start: 1, duration: 1, key: 38, velocity: 80 }
                    ]
                }
            ]
        );
        const midi = readMidi(file);
        const notes = midi.tracks[1].filter((event) => (event.status & 0xf0) === 0x80 || (event.status & 0xf0) === 0x90);

        expect(midi.tracks[0].find((event) => event.data[0] === 0x51)?.data).toEqual([0x51, 0x07, 0xa1, 0x20]);
        expect(midi.tracks[1].some((event) => (event.status & 0xf0) === 0xc0)).toBeFalse();
        expect(notes.map((event) => [event.tick, event.status])).toEqual([
            [0, 0x99],
            [TICKS_PER_QUARTER, 0x89],
            [TICKS_PER_QUARTER, 0x99],
            [2 * TICKS_PER_QUARTER, 0x89]
        ]);
    });
});
