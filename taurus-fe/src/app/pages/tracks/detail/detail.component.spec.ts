import { SheetsMusic, Tracks } from '../../../module';
import { of } from 'rxjs';
import { DetailComponent } from './detail.component';

describe('Track DetailComponent', () => {
    it('replaces the scores collection when splitting a part so the paginator updates', () => {
        const component = new DetailComponent(null!, null!, null!, null!, null!, null!, null!, null!, null!, null!);
        const score = new SheetsMusic();
        score.order = 1;
        score.description = 'Parte completa';
        score.media = Array.from({ length: 77 }, (_, index) => ({ index: index + 1, name: `Pagina ${index + 1}`, order: index + 1 }));
        score.instruments = [{ index: 1, name: 'Clarinetto', order: 1 }];

        const originalScores = [score];
        component['track'] = Object.assign(new Tracks(), { scores: originalScores });

        component['splitScore'](score);

        expect(component['track'].scores).not.toBe(originalScores);
        expect(component['track'].scores).toHaveSize(77);
        expect(component['track'].scores?.map((value) => value.order)).toEqual(Array.from({ length: 77 }, (_, index) => index + 1));
        expect(component['track'].scores?.every((value) => value.media?.length === 1)).toBeTrue();
    });

    it('loads upload jobs only once during initialization', () => {
        const tracksService = jasmine.createSpyObj('TracksService', ['getById', 'getUploadJobs']);
        const instrumentsService = jasmine.createSpyObj('InstrumentsService', ['getAll']);
        tracksService.getById.and.returnValue(of(new Tracks()));
        tracksService.getUploadJobs.and.returnValue(of([]));
        instrumentsService.getAll.and.returnValue(of({ content: [], totalElements: 0 }));
        const route = { params: of({ id: 8 }) };
        const component = new DetailComponent(tracksService, null!, instrumentsService, null!, null!, null!, route as any, null!, null!, null!);

        component.ngOnInit();

        expect(tracksService.getUploadJobs).toHaveBeenCalledOnceWith(8);
    });
});
