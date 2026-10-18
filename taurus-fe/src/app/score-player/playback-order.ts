import { ScoreMeasure } from './musicxml-score';

/**
 * Ordine di esecuzione delle battute (indici), dalla struttura della partitura.
 *
 * Con i ritornelli ogni ritornello si esegue quante volte indicato (due per default) e le volte seguono il passaggio.
 * Senza ritornelli ogni sezione si esegue una volta, prendendo l'ultima volta. In entrambi i casi D.C. e D.S. si
 * eseguono una sola volta e, dopo il salto, i ritornelli non si ripetono; «Fine» e «alla Coda» valgono dopo il salto.
 */
export function playbackOrder(measures: ScoreMeasure[], withRepeats: boolean): number[] {
    const order: number[] = [];
    const limit = Math.max(1, measures.length) * 16;
    const segno = measures.findIndex((measure) => measure.segno);
    const coda = measures.findIndex((measure) => measure.coda);
    const lastEndings = lastEndingNumbers(measures);

    let index = 0;
    let repeatStart = 0;
    let pass = 1;
    let jumped = false;

    while (index < measures.length && order.length < limit) {
        const measure = measures[index];
        if (measure.forwardRepeat && index !== repeatStart) {
            repeatStart = index;
            pass = 1;
        }

        const repeating = withRepeats && !jumped;
        if (measure.ending?.start) {
            const wanted = repeating ? pass : (lastEndings.get(index) ?? pass);
            if (!measure.ending.numbers.includes(wanted)) {
                index = endingEnd(measures, index) + 1;
                continue;
            }
        }

        order.push(index);

        if (jumped && measure.fine) break;
        if (jumped && measure.toCoda && coda > index) {
            index = coda;
            continue;
        }
        if (repeating && measure.backwardRepeatTimes > 0 && pass < measure.backwardRepeatTimes) {
            pass++;
            index = repeatStart;
            continue;
        }
        if (measure.backwardRepeatTimes > 0 || (measure.ending?.stop && !jumped)) {
            pass = 1;
            repeatStart = index + 1;
        }
        if (!jumped && (measure.daCapo || measure.dalSegno)) {
            jumped = true;
            index = measure.dalSegno && segno >= 0 ? segno : 0;
            repeatStart = index;
            pass = 1;
            continue;
        }
        index++;
    }
    return order;
}

/** Ultima battuta di una volta che inizia in `start`. */
function endingEnd(measures: ScoreMeasure[], start: number): number {
    for (let index = start; index < measures.length; index++) {
        if (measures[index].ending?.stop) return index;
        if (index > start && measures[index].ending?.start) return index - 1;
    }
    return start;
}

/**
 * Per ogni volta, il numero più alto del suo gruppo di volte consecutive: è quella eseguita senza ritornelli e dopo
 * un salto.
 */
function lastEndingNumbers(measures: ScoreMeasure[]): Map<number, number> {
    const result = new Map<number, number>();
    let group: number[] = [];
    let highest = 0;
    const close = () => {
        group.forEach((start) => result.set(start, highest));
        group = [];
        highest = 0;
    };
    for (let index = 0; index < measures.length; index++) {
        const ending = measures[index].ending;
        if (ending?.start) {
            group.push(index);
            highest = Math.max(highest, ...ending.numbers);
            index = endingEnd(measures, index);
            if (!measures[index + 1]?.ending?.start) close();
        }
    }
    close();
    return result;
}
