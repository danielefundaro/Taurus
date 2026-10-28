import { Albums, EventPreparationProgramEntry } from '../../../module';
import { programFromAlbum } from './event-program';

describe('programFromAlbum', () => {
    it('creates the event program using the album track order', () => {
        const album = {
            tracks: [
                { index: 20, name: 'Secondo', order: 2 },
                { index: 10, name: 'Primo', order: 1 }
            ]
        } as Albums;

        expect(programFromAlbum(album)).toEqual([jasmine.objectContaining({ trackId: 10, trackName: 'Primo', order: 0 }), jasmine.objectContaining({ trackId: 20, trackName: 'Secondo', order: 1 })]);
    });

    it('keeps execution details for tracks already in the program', () => {
        const album = { tracks: [{ index: 10, name: 'Primo', order: 1 }] } as Albums;
        const current = [
            {
                id: 7,
                trackId: 10,
                trackName: 'Vecchio nome',
                trackState: 'PUBLIC',
                order: 4,
                plannedDurationSeconds: 180,
                notes: 'Bis',
                encore: true,
                scores: [{ id: 3, instruments: ['Tromba'], ready: true, needsReview: false, files: [] }]
            }
        ] as unknown as EventPreparationProgramEntry[];

        expect(programFromAlbum(album, current)).toEqual([jasmine.objectContaining({ id: 0, trackId: 10, trackName: 'Primo', trackState: 'PUBLIC', trackAvailable: true, encore: true, order: 0, plannedDurationSeconds: 180, notes: 'Bis' })]);
        expect(programFromAlbum(album, current)[0].scores.length).toBe(1);
    });

    it('starts the tracks new to the program as available and without encore', () => {
        const album = { tracks: [{ index: 10, name: 'Primo', order: 1 }] } as Albums;

        expect(programFromAlbum(album)).toEqual([jasmine.objectContaining({ trackAvailable: true, encore: false, scores: [] })]);
    });
});
