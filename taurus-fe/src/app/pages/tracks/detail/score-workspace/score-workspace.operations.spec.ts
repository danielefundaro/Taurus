import { SheetsMusic } from '../../../../module';
import { cloneAndNormalizeScores, compareScores, mergeScores, mergeServerScores, moveMedia, reorderMedia, splitScoreAfter, splitScoreByPage } from './score-workspace.operations';

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
    it('inserts the moved pages where the drop indicator was, counting from the original order', () => {
        const source: SheetsMusic[] = [{ id: 10, order: 1, media: [{ index: 1 }, { index: 2 }, { index: 3 }], instruments: [] }];

        // La pagina 1 va rilasciata fra la 2 e la 3, cioè alla posizione 2 della lista di partenza.
        const result = moveMedia(source, new Set([1]), 0, 2);

        expect(result[0].media?.map((media) => media.index)).toEqual([2, 1, 3]);
    });

    it('tells the parts the server added apart from the ones the draft removed', () => {
        const baseline: SheetsMusic[] = [
            { id: 1, order: 1, description: 'Clarinetto', media: [], instruments: [] },
            { id: 2, order: 2, description: 'Tromba', media: [], instruments: [] }
        ];
        const draft: SheetsMusic[] = [{ id: 1, order: 1, description: 'Clarinetto solo', media: [], instruments: [] }, { order: 2, description: 'Nuova', media: [], instruments: [] }];
        const server: SheetsMusic[] = [...baseline, { id: 3, order: 3, description: 'Flauto', media: [], instruments: [] }];

        const comparison = compareScores(draft, server, baseline);

        expect(comparison.serverAdded.map((score) => score.id)).toEqual([3]);
        expect(comparison.draftRemoved).toBe(1);
        expect(comparison.draftAdded).toBe(1);
        expect(comparison.draftChanged).toBe(1);
        expect(comparison.draftReordered).toBeFalse();
    });

    it('sees a different order of the common parts', () => {
        const server: SheetsMusic[] = [
            { id: 1, order: 1, media: [], instruments: [] },
            { id: 2, order: 2, media: [], instruments: [] }
        ];
        const comparison = compareScores([server[1], server[0]], server);

        expect(comparison.draftReordered).toBeTrue();
        expect(comparison.draftChanged).toBe(0);
    });

    it('appends the server parts the draft does not have and keeps the local edits', () => {
        const draft: SheetsMusic[] = [{ id: 1, order: 1, description: 'Rinominata', media: [], instruments: [] }];
        const server: SheetsMusic[] = [
            { id: 1, order: 1, description: 'Clarinetto', media: [], instruments: [] },
            { id: 3, order: 2, description: 'Flauto', media: [], instruments: [] }
        ];

        const merged = mergeServerScores(draft, server);

        expect(merged.map((score) => score.id)).toEqual([1, 3]);
        expect(merged[0].description).toBe('Rinominata');
        expect(merged.map((score) => score.order)).toEqual([1, 2]);
    });
});
