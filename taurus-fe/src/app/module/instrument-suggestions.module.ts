/** What to do with a name read on the parts: create the instrument, associate an existing one, or ignore the name. */
export type InstrumentSuggestionAction = 'CREATE' | 'ASSOCIATE' | 'IGNORE';

/** The instruments to add of a track, one per name, read with the version of the track they refer to. */
export interface InstrumentSuggestions {
    trackVersion: number;
    names: InstrumentSuggestionName[];
}

/** A name found on one or more parts of the track. */
export interface InstrumentSuggestionName {
    /** The name in the form of the uniqueness check: identifies the name in the decisions. */
    normalizedName: string;
    proposedName: string;
    catalogKeyword?: string | null;
    /** `CREATE` when no instrument of the tenant matches, `ASSOCIATE` when some do. */
    action: 'CREATE' | 'ASSOCIATE';
    candidates: InstrumentSuggestionCandidate[];
    parts: InstrumentSuggestionPart[];
}

export interface InstrumentSuggestionCandidate {
    id: number;
    name: string;
    usersCount: number;
}

export interface InstrumentSuggestionPart {
    sheetMusicId: number;
    readText: string;
    source: 'PDF_TEXT' | 'OCR';
    /** The part already has instruments: it is not preselected. */
    hasInstruments: boolean;
    /** More than four names on the part: probably the full score, not preselected. */
    possibleScore: boolean;
}

export interface InstrumentSuggestionDecision {
    normalizedName: string;
    action: InstrumentSuggestionAction;
    name?: string;
    instrumentId?: number;
    sheetMusicIds?: number[];
}

export interface InstrumentSuggestionApplyRequest {
    trackVersion: number;
    decisions: InstrumentSuggestionDecision[];
}
