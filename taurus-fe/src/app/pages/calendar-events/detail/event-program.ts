import { Albums, EventPreparationProgramEntry } from '../../../module';

export function programFromAlbum(album: Albums, currentProgram: EventPreparationProgramEntry[] = []): EventPreparationProgramEntry[] {
    const currentByTrackId = new Map(currentProgram.map((entry) => [entry.trackId, entry]));
    const tracks = [...(album.tracks ?? [])].sort((left, right) => (left.order ?? Number.MAX_SAFE_INTEGER) - (right.order ?? Number.MAX_SAFE_INTEGER));

    return tracks.map((track, order) => {
        const current = currentByTrackId.get(track.index);
        return {
            id: 0,
            trackId: track.index,
            trackName: track.name ?? '',
            trackState: current?.trackState ?? '',
            order,
            plannedDurationSeconds: current?.plannedDurationSeconds,
            notes: current?.notes
        };
    });
}
