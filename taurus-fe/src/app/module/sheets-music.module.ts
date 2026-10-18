import { ChildrenEntities } from './children-entities.module';

export class SheetsMusic {
    id?: number;
    description?: string;
    order?: number;
    media?: Array<ChildrenEntities>;
    instruments?: Array<ChildrenEntities>;
    needsReview?: boolean;
    /** Sola lettura: versione MusicXML ascoltata per la parte. */
    currentScoreVersionId?: number | null;
    transpositionOverride?: number | null;
    soundBankOverride?: number | null;
    soundProgramOverride?: number | null;
}
