import { ScorePart } from '../score-player/musicxml-score';

/**
 * Le battute indicate di una parte, eseguite di seguito senza ritornelli né salti, per ascoltare un passaggio durante la
 * correzione. Il tempo è l'ultima indicazione metronomica che precede il passaggio.
 */
export function excerpt(part: ScorePart, from: number, to: number): ScorePart {
    const first = Math.max(0, Math.min(from, to));
    const last = Math.min(part.measures.length - 1, Math.max(from, to));
    const tempo = part.measures.slice(0, first + 1).reduce<number | null>((current, measure) => measure.tempo ?? current, null);
    return {
        ...part,
        measures: part.measures.slice(first, last + 1).map((measure, index) => ({
            ...measure,
            index,
            forwardRepeat: false,
            backwardRepeatTimes: 0,
            ending: null,
            segno: false,
            coda: false,
            toCoda: false,
            fine: false,
            daCapo: false,
            dalSegno: false,
            tempo: index === 0 ? tempo : measure.tempo
        }))
    };
}
