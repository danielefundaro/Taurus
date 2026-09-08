import { Tracks } from '../../../module';
import { of } from 'rxjs';
import { DetailComponent } from './detail.component';

describe('Track DetailComponent', () => {
    it('loads upload jobs only once during initialization', () => {
        const tracksService = jasmine.createSpyObj('TracksService', ['getById', 'getUploadJobs']);
        const instrumentsService = jasmine.createSpyObj('InstrumentsService', ['getAll']);
        tracksService.getById.and.returnValue(of(new Tracks()));
        tracksService.getUploadJobs.and.returnValue(of([]));
        instrumentsService.getAll.and.returnValue(of({ content: [], totalElements: 0 }));
        const route = { params: of({ id: 8 }), snapshot: { queryParamMap: { get: () => null } } };
        const component = new DetailComponent(tracksService, instrumentsService, null!, null!, null!, route as any, null!, null!, null!);

        component.ngOnInit();

        expect(tracksService.getUploadJobs).toHaveBeenCalledOnceWith(8);
    });

    it('treats details and parts as one track save unit', () => {
        const component = new DetailComponent(null!, null!, null!, null!, null!, null!, null!, null!, null!);

        component['onScoresDirtyChange'](true);
        expect(component.isDirtyForm).toBeTrue();

        component['markDetailsDirty']();
        component['onScoresDirtyChange'](false);
        expect(component.isDirtyForm).toBeTrue();
    });
});
