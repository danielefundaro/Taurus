import { CommonFieldsOpenSearch } from "./common-fields-open-search.module";

export class Instruments extends CommonFieldsOpenSearch {
    /** Quanti utenti hanno assegnato questo strumento. Valorizzato solo negli elenchi. */
    usersCount?: number;
    /** Semitoni tra nota scritta e suono reale; vuoto finché lo strumento non è configurato per l'ascolto. */
    transpositionSemitones?: number | null;
    /** Semitoni per le parti in chiave di basso, se diversi. */
    bassClefTranspositionSemitones?: number | null;
    soundBank?: number | null;
    soundProgram?: number | null;
    /** Nota di batteria General MIDI: se presente lo strumento è una percussione. */
    percussionKey?: number | null;
    /** Calcolato dal backend: lo strumento ha suono e trasposizione. */
    playbackConfigured?: boolean;
}