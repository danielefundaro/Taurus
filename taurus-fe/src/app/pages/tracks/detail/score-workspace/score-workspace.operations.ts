import { ChildrenEntities, SheetsMusic } from '../../../../module';

export function cloneAndNormalizeScores(scores: SheetsMusic[]): SheetsMusic[] {
    return structuredClone(scores ?? []).map((score, scoreIndex) => ({
        ...score,
        order: scoreIndex + 1,
        media: normalizeChildren(score.media),
        instruments: normalizeChildren(score.instruments)
    }));
}

export function moveMedia(scores: SheetsMusic[], mediaIds: ReadonlySet<number>, targetScoreIndex: number, targetMediaIndex?: number): SheetsMusic[] {
    const draft = cloneAndNormalizeScores(scores);
    if (!draft[targetScoreIndex] || mediaIds.size === 0) return draft;

    const moved: ChildrenEntities[] = [];
    for (const score of draft) {
        const retained: ChildrenEntities[] = [];
        for (const media of score.media ?? []) (mediaIds.has(media.index) ? moved : retained).push(media);
        score.media = retained;
    }
    if (moved.length !== mediaIds.size) return cloneAndNormalizeScores(scores);

    const target = draft[targetScoreIndex];
    const insertion = Math.max(0, Math.min(targetMediaIndex ?? target.media?.length ?? 0, target.media?.length ?? 0));
    target.media = [...(target.media ?? []).slice(0, insertion), ...moved, ...(target.media ?? []).slice(insertion)];
    return cloneAndNormalizeScores(draft);
}

export function reorderScore(scores: SheetsMusic[], fromIndex: number, toIndex: number): SheetsMusic[] {
    const draft = cloneAndNormalizeScores(scores);
    if (!draft[fromIndex] || !draft[toIndex] || fromIndex === toIndex) return draft;
    const [score] = draft.splice(fromIndex, 1);
    draft.splice(toIndex, 0, score);
    return cloneAndNormalizeScores(draft);
}

export function reorderMedia(scores: SheetsMusic[], scoreIndex: number, fromIndex: number, toIndex: number): SheetsMusic[] {
    const draft = cloneAndNormalizeScores(scores);
    const media = draft[scoreIndex]?.media;
    if (!media?.[fromIndex] || !media[toIndex] || fromIndex === toIndex) return draft;
    const [page] = media.splice(fromIndex, 1);
    media.splice(toIndex, 0, page);
    return cloneAndNormalizeScores(draft);
}

export function mergeScores(scores: SheetsMusic[], selectedIds: ReadonlySet<string>, keyOf: (score: SheetsMusic) => string): SheetsMusic[] {
    const draft = cloneAndNormalizeScores(scores);
    const selected = draft.filter((score) => selectedIds.has(keyOf(score)));
    if (selected.length < 2) return draft;
    const firstIndex = draft.findIndex((score) => selectedIds.has(keyOf(score)));
    const seenInstruments = new Set<number>();
    const merged: SheetsMusic = {
        id: undefined,
        description: selected[0].description,
        needsReview: selected.some((score) => score.needsReview),
        media: selected.flatMap((score) => score.media ?? []),
        instruments: selected
            .flatMap((score) => score.instruments ?? [])
            .filter((instrument) => {
                if (seenInstruments.has(instrument.index)) return false;
                seenInstruments.add(instrument.index);
                return true;
            })
    };
    const remaining = draft.filter((score) => !selectedIds.has(keyOf(score)));
    remaining.splice(firstIndex, 0, merged);
    return cloneAndNormalizeScores(remaining);
}

export function splitScoreAfter(scores: SheetsMusic[], scoreIndex: number, pageIndex: number): SheetsMusic[] {
    const draft = cloneAndNormalizeScores(scores);
    const score = draft[scoreIndex];
    if (!score || pageIndex < 0 || pageIndex >= (score.media?.length ?? 0) - 1) return draft;
    const before: SheetsMusic = { ...score, media: score.media?.slice(0, pageIndex + 1) };
    const after: SheetsMusic = { ...score, id: undefined, media: score.media?.slice(pageIndex + 1) };
    draft.splice(scoreIndex, 1, before, after);
    return cloneAndNormalizeScores(draft);
}

export function splitScoreByPage(scores: SheetsMusic[], scoreIndex: number): SheetsMusic[] {
    const draft = cloneAndNormalizeScores(scores);
    const score = draft[scoreIndex];
    if (!score || (score.media?.length ?? 0) < 2) return draft;
    const split = (score.media ?? []).map((media, index) => ({ ...score, id: index === 0 ? score.id : undefined, media: [media] }));
    draft.splice(scoreIndex, 1, ...split);
    return cloneAndNormalizeScores(draft);
}

function normalizeChildren(children?: ChildrenEntities[]): ChildrenEntities[] {
    return (children ?? []).map((child, index) => ({ ...child, order: index + 1 }));
}
