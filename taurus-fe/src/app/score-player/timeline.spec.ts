import { PlaybackSound } from '../module';
import { partSound, quartersAt, secondsAt, segmentIndexAt } from './timeline';

describe('playback timeline', () => {
    const tempo = [
        { at: 0, bpm: 60 },
        { at: 4, bpm: 120 }
    ];

    it('converts between seconds and quarters following tempo changes', () => {
        expect(secondsAt(2, tempo)).toBe(2);
        expect(secondsAt(4, tempo)).toBe(4);
        expect(secondsAt(8, tempo)).toBe(6);
        expect(quartersAt(3, tempo)).toBe(3);
        expect(quartersAt(5, tempo)).toBe(6);
        [0, 1.5, 4, 7.25, 12].forEach((quarters) => expect(quartersAt(secondsAt(quarters, tempo), tempo)).toBeCloseTo(quarters, 6));
    });

    it('finds the measure being played, before and after the count-in', () => {
        const segments = [
            { measureIndex: 0, start: 0, length: 4 },
            { measureIndex: 1, start: 4, length: 3 },
            { measureIndex: 0, start: 7, length: 4 }
        ];

        expect(segmentIndexAt(2, segments, 4)).toBe(-1);
        expect(segmentIndexAt(4, segments, 4)).toBe(0);
        expect(segmentIndexAt(8.5, segments, 4)).toBe(1);
        expect(segmentIndexAt(11, segments, 4)).toBe(2);
        expect(segmentIndexAt(40, segments, 4)).toBe(2);
    });

    it('uses the bass-clef transposition only for parts in F clef and plays unconfigured parts on the piano', () => {
        const flugelhorn: PlaybackSound = {
            transpositionSemitones: -14,
            bassClefTranspositionSemitones: 0,
            soundBank: 0,
            soundProgram: 57,
            percussionKeys: [],
            configured: true,
            conflicting: false
        };
        const unconfigured: PlaybackSound = { ...flugelhorn, transpositionSemitones: null, bassClefTranspositionSemitones: null, soundBank: null, soundProgram: null, configured: false };

        expect(partSound(flugelhorn, 'G')).toEqual({ transposition: -14, bank: 0, program: 57, percussionKeys: [] });
        expect(partSound(flugelhorn, 'F').transposition).toBe(0);
        expect(partSound({ ...flugelhorn, bassClefTranspositionSemitones: null }, 'F').transposition).toBe(-14);
        expect(partSound(unconfigured, 'G')).toEqual({ transposition: 0, bank: 0, program: 0, percussionKeys: [] });
    });
});
