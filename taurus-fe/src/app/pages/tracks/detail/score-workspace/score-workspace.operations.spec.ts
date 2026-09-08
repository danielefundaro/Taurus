import { SheetsMusic } from '../../../../module';
import { cloneAndNormalizeScores, mergeScores, moveMedia, reorderMedia, splitScoreAfter, splitScoreByPage } from './score-workspace.operations';

describe('score workspace operations', () => {
    const scores = (): SheetsMusic[] => [
        { id: 10, order: 8, description: 'Clarinetto', media: [{ index: 1 }, { index: 2 }], instruments: [{ index: 4, name: 'Clarinetto' }] },
        {
            id: 11,
            order: 2,
            description: 'Tromba',
            media: [{ index: 3 }],
            instruments: [
                { index: 5, name: 'Tromba' },
                { index: 4, name: 'Clarinetto' }
            ]
        }
    ];

    it('normalizes every persisted order', () => {
        const result = cloneAndNormalizeScores(scores());
        expect(result.map((score) => score.order)).toEqual([1, 2]);
        expect(result[0].media?.map((media) => media.order)).toEqual([1, 2]);
        expect(result[1].instruments?.map((instrument) => instrument.order)).toEqual([1, 2]);
    });

    it('moves selected pages and keeps empty source parts', () => {
        const result = moveMedia(scores(), new Set([1, 2]), 1);
        expect(result).toHaveSize(2);
        expect(result[0].media).toEqual([]);
        expect(result[1].media?.map((media) => media.index)).toEqual([3, 1, 2]);
    });

    it('reorders pages without changing their identity', () => {
        const result = reorderMedia(scores(), 0, 0, 1);
        expect(result[0].media?.map((media) => media.index)).toEqual([2, 1]);
    });

    it('merges pages and removes duplicate instruments preserving order', () => {
        const result = mergeScores(scores(), new Set(['10', '11']), (score) => String(score.id));
        expect(result).toHaveSize(1);
        expect(result[0].media?.map((media) => media.index)).toEqual([1, 2, 3]);
        expect(result[0].instruments?.map((instrument) => instrument.index)).toEqual([4, 5]);
    });

    it('splits after the chosen page', () => {
        const result = splitScoreAfter(scores(), 0, 0);
        expect(result).toHaveSize(3);
        expect(result.slice(0, 2).map((score) => score.media?.map((media) => media.index))).toEqual([[1], [2]]);
    });

    it('creates one part per page', () => {
        const result = splitScoreByPage(scores(), 0);
        expect(result).toHaveSize(3);
        expect(result[0].id).toBe(10);
        expect(result[1].id).toBeUndefined();
    });
});
