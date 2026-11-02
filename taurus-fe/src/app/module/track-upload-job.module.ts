export type TrackUploadJobStatus = 'TO_PROCESS' | 'IN_PROGRESS' | 'DONE' | 'ERROR' | 'NOT_FOUND';

export interface TrackUploadJob {
    id: number;
    name: string;
    trackId: number;
    status: TrackUploadJobStatus;
    /** How the parts of the PDF were matched with the instruments; empty for the jobs processed before the summary. */
    partsWithInstruments?: number | null;
    partsWithSuggestions?: number | null;
    partsWithoutInstruments?: number | null;
}
