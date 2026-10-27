import { ChildrenEntities, SheetsMusic } from '../../../../module';

export function cloneAndNormalizeScores(scores: SheetsMusic[]): SheetsMusic[] {
    return structuredClone(scores ?? []).map((score, scoreIndex) => ({
        ...score,
        order: scoreIndex + 1,
        media: normalizeChildren(score.media),
        instruments: normalizeChildren(score.instruments)
    }));
}

/**
 * Sposta le pagine selezionate nella parte di destinazione.
 *
 * `targetMediaIndex` è una posizione della lista come la vede chi trascina, cioè prima che le
 * pagine spostate ne escano: le pagine che la precedono e che stanno per uscire vengono scalate,
 * altrimenti riordinare dentro la stessa parte sposterebbe di una posizione in meno.
 */
export function moveMedia(scores: SheetsMusic[], mediaIds: ReadonlySet<number>, targetScoreIndex: number, targetMediaIndex?: number): SheetsMusic[] {
    const draft = cloneAndNormalizeScores(scores);
    if (!draft[targetScoreIndex] || mediaIds.size === 0) return draft;
    const originalTarget = (draft[targetScoreIndex].media ?? []).map((media) => media.index);

    const moved: ChildrenEntities[] = [];
    for (const score of draft) {
        const retained: ChildrenEntities[] = [];
        for (const media of score.media ?? []) (mediaIds.has(media.index) ? moved : retained).push(media);
        score.media = retained;
    }
    if (moved.length !== mediaIds.size) return cloneAndNormalizeScores(scores);

    const target = draft[targetScoreIndex];
    const retained = target.media?.length ?? 0;
    const requested = targetMediaIndex === undefined ? retained : targetMediaIndex - originalTarget.slice(0, targetMediaIndex).filter((id) => mediaIds.has(id)).length;
    const insertion = Math.max(0, Math.min(requested, retained));
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

/**
 * Scostamento tra la bozza locale e le parti lette dal server.
 *
 * L'unità di confronto è l'`id` della parte: è l'unica identità che sopravvive al salvataggio e
 * all'elaborazione asincrona di un PDF. Una parte senza `id` esiste solo nella bozza.
 */
export interface DraftComparison {
    /** Parti presenti sul server e assenti dalla bozza: le ha aggiunte l'elaborazione di un PDF. */
    serverAdded: SheetsMusic[];
    /** Parti create nella bozza e non ancora salvate. */
    draftAdded: number;
    /** Parti persistite che la bozza ha rimosso. */
    draftRemoved: number;
    /** Parti con lo stesso `id` e contenuto diverso. */
    draftChanged: number;
    /** Le parti persistite comuni non sono nello stesso ordine. */
    draftReordered: boolean;
}

/**
 * Confronta bozza e server. `baseline` sono le parti come sono state lette all'apertura: senza di
 * esse una parte persistita assente dalla bozza è indistinguibile da una parte nuova del server,
 * perché in entrambi i casi è «sul server e non nella bozza».
 */
export function compareScores(draft: SheetsMusic[], server: SheetsMusic[], baseline: SheetsMusic[] = draft): DraftComparison {
    const draftScores = draft ?? [];
    const serverScores = server ?? [];
    const draftById = new Map(persistedEntries(draftScores));
    const serverById = new Map(persistedEntries(serverScores));
    const baselineIds = new Set((baseline ?? []).map((score) => score.id).filter((id) => id !== undefined));
    const commonInDraft = draftScores.filter((score) => score.id !== undefined && serverById.has(score.id)).map((score) => score.id!);
    const commonInServer = serverScores.filter((score) => score.id !== undefined && draftById.has(score.id)).map((score) => score.id!);

    return {
        serverAdded: serverScores.filter((score) => score.id !== undefined && !draftById.has(score.id) && !baselineIds.has(score.id)),
        draftAdded: draftScores.filter((score) => score.id === undefined).length,
        draftRemoved: serverScores.filter((score) => score.id !== undefined && !draftById.has(score.id) && baselineIds.has(score.id)).length,
        draftChanged: commonInDraft.filter((id) => fingerprint(draftById.get(id)!) !== fingerprint(serverById.get(id)!)).length,
        draftReordered: commonInDraft.join(',') !== commonInServer.join(',')
    };
}

/**
 * Riallinea la bozza alle parti del server aggiungendo in coda quelle che la bozza non ha.
 *
 * L'elaborazione di un PDF accoda parti e non tocca quelle esistenti, quindi la fusione non deve
 * scegliere fra due versioni della stessa parte: le modifiche locali restano, le parti nuove
 * arrivano dopo. Le parti persistite sparite dal server non vengono reintrodotte.
 */
export function mergeServerScores(draft: SheetsMusic[], server: SheetsMusic[]): SheetsMusic[] {
    const known = new Set((draft ?? []).map((score) => score.id).filter((id) => id !== undefined));
    const added = (server ?? []).filter((score) => score.id !== undefined && !known.has(score.id));
    return cloneAndNormalizeScores([...(draft ?? []), ...added]);
}

function persistedEntries(scores: SheetsMusic[]): [number, SheetsMusic][] {
    return scores.filter((score) => score.id !== undefined).map((score) => [score.id!, score]);
}

/** Identità del contenuto di una parte: descrizione, verifica, pagine e strumenti nel loro ordine. */
function fingerprint(score: SheetsMusic): string {
    return JSON.stringify([
        score.description?.trim() ?? '',
        score.needsReview === true,
        (score.media ?? []).map((media) => media.index),
        (score.instruments ?? []).map((instrument) => instrument.index)
    ]);
}
