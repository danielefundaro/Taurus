export type TrackUploadJobStatus = 'TO_PROCESS' | 'IN_PROGRESS' | 'DONE' | 'ERROR' | 'NOT_FOUND';

export interface TrackUploadJob {
    id: number;
    name: string;
    trackId: number;
    status: TrackUploadJobStatus;
}
