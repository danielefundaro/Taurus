import { playbackOrder } from './playback-order';
import { measure } from './score-player.testing';

describe('playbackOrder', () => {
    it('plays every measure once when the score has no structure marks', () => {
        const measures = [0, 1, 2].map((index) => measure(index));

        expect(playbackOrder(measures, true)).toEqual([0, 1, 2]);
        expect(playbackOrder(measures, false)).toEqual([0, 1, 2]);
    });

    it('repeats from the forward repeat, as many times as indicated, only with repeats', () => {
        const measures = [measure(0), measure(1, { forwardRepeat: true }), measure(2, { backwardRepeatTimes: 3 }), measure(3)];

        expect(playbackOrder(measures, true)).toEqual([0, 1, 2, 1, 2, 1, 2, 3]);
        expect(playbackOrder(measures, false)).toEqual([0, 1, 2, 3]);
    });

    it('repeats from the beginning when the repeat has no start', () => {
        const measures = [measure(0), measure(1, { backwardRepeatTimes: 2 }), measure(2)];

        expect(playbackOrder(measures, true)).toEqual([0, 1, 0, 1, 2]);
    });

    it('follows first and second endings, taking only the last one without repeats', () => {
        const measures = [
            measure(0),
            measure(1),
            measure(2, { ending: { numbers: [1], start: true, stop: false } }),
            measure(3, { ending: { numbers: [1], start: false, stop: true }, backwardRepeatTimes: 2 }),
            measure(4, { ending: { numbers: [2], start: true, stop: true } }),
            measure(5)
        ];

        expect(playbackOrder(measures, true)).toEqual([0, 1, 2, 3, 0, 1, 4, 5]);
        expect(playbackOrder(measures, false)).toEqual([0, 1, 4, 5]);
    });

    it('starts a later repeat after the last ending instead of the previous repeat', () => {
        const measures = [measure(0), measure(1, { ending: { numbers: [1], start: true, stop: true }, backwardRepeatTimes: 2 }), measure(2, { ending: { numbers: [2], start: true, stop: true } }), measure(3), measure(4, { backwardRepeatTimes: 2 })];

        expect(playbackOrder(measures, true)).toEqual([0, 1, 0, 2, 3, 4, 3, 4]);
    });

    it('jumps da capo once and stops at Fine, without repeating after the jump', () => {
        const measures = [measure(0), measure(1, { fine: true, backwardRepeatTimes: 2 }), measure(2), measure(3, { daCapo: true })];

        expect(playbackOrder(measures, true)).toEqual([0, 1, 0, 1, 2, 3, 0, 1]);
    });

    it('jumps dal segno and to the coda after the jump', () => {
        const measures = [measure(0), measure(1, { segno: true }), measure(2, { toCoda: true }), measure(3, { dalSegno: true }), measure(4, { coda: true }), measure(5)];

        expect(playbackOrder(measures, true)).toEqual([0, 1, 2, 3, 1, 2, 4, 5]);
        expect(playbackOrder(measures, false)).toEqual([0, 1, 2, 3, 1, 2, 4, 5]);
    });

    it('plays the last ending after a jump', () => {
        const measures = [measure(0), measure(1, { ending: { numbers: [1], start: true, stop: true }, backwardRepeatTimes: 2 }), measure(2, { ending: { numbers: [2], start: true, stop: true }, daCapo: true })];

        expect(playbackOrder(measures, true)).toEqual([0, 1, 0, 2, 0, 2]);
    });

    it('stops on malformed structures instead of looping forever', () => {
        const measures = [measure(0, { forwardRepeat: true }), measure(1, { backwardRepeatTimes: 1000 })];

        expect(playbackOrder(measures, true).length).toBe(32);
        expect(playbackOrder([], true)).toEqual([]);
    });
});
