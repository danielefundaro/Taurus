import { PlaybackSound } from '../module';
import { MidiTempoChange } from './midi-file';
import { PartSound, TimelineSegment } from './playback-builder';

/** Posizione in semiminime al tempo indicato in secondi del brano, seguendo i cambi di tempo. */
export function quartersAt(seconds: number, tempo: MidiTempoChange[]): number {
    let elapsed = 0;
    for (let index = 0; index < tempo.length; index++) {
        const change = tempo[index];
        const next = tempo[index + 1];
        const secondsPerQuarter = 60 / Math.max(1, change.bpm);
        const spanSeconds = next ? (next.at - change.at) * secondsPerQuarter : Number.POSITIVE_INFINITY;
        if (seconds <= elapsed + spanSeconds) return change.at + (seconds - elapsed) / secondsPerQuarter;
        elapsed += spanSeconds;
    }
    return 0;
}

/** Tempo in secondi del brano alla posizione indicata in semiminime. */
export function secondsAt(quarters: number, tempo: MidiTempoChange[]): number {
    let seconds = 0;
    for (let index = 0; index < tempo.length; index++) {
        const change = tempo[index];
        const end = tempo[index + 1]?.at ?? Number.POSITIVE_INFINITY;
        const secondsPerQuarter = 60 / Math.max(1, change.bpm);
        if (quarters <= end) return seconds + (quarters - change.at) * secondsPerQuarter;
        seconds += (end - change.at) * secondsPerQuarter;
    }
    return seconds;
}

/** Indice della battuta eseguita alla posizione indicata, -1 durante la battuta di attacco. */
export function segmentIndexAt(quarters: number, segments: TimelineSegment[], countIn: number): number {
    const position = quarters - countIn;
    if (position < 0) return -1;
    let low = 0;
    let high = segments.length - 1;
    while (low <= high) {
        const middle = (low + high) >> 1;
        const segment = segments[middle];
        if (position < segment.start) high = middle - 1;
        else if (position >= segment.start + segment.length) low = middle + 1;
        else return middle;
    }
    return segments.length - 1;
}

/**
 * Suono di una parte per il generatore MIDI: la trasposizione in chiave di basso vale per le parti scritte in chiave
 * di Fa; senza configurazione la parte suona con il pianoforte e senza trasposizione.
 */
export function partSound(sound: PlaybackSound, clefSign: string): PartSound {
    const bassClef = clefSign === 'F' && sound.bassClefTranspositionSemitones !== null && sound.bassClefTranspositionSemitones !== undefined;
    return {
        transposition: (bassClef ? sound.bassClefTranspositionSemitones : sound.transpositionSemitones) ?? 0,
        bank: sound.soundBank ?? 0,
        program: sound.soundProgram ?? 0,
        percussionKeys: sound.percussionKeys ?? []
    };
}
