import { TrackUploadJob, Tracks } from '../../../module';
import { of } from 'rxjs';
import { RoleEnums } from '../../../constants';
import { DetailComponent } from './detail.component';

describe('Track DetailComponent', () => {
    const tenantFeatures = { scorePlaybackEnabled: () => false, loaded: () => true } as any;

    it('loads upload jobs only once during initialization', () => {
        const tracksService = jasmine.createSpyObj('TracksService', ['getById', 'getUploadJobs']);
        const instrumentsService = jasmine.createSpyObj('InstrumentsService', ['getAll']);
        tracksService.getById.and.returnValue(of(new Tracks()));
        tracksService.getUploadJobs.and.returnValue(of([]));
        instrumentsService.getAll.and.returnValue(of({ content: [], totalElements: 0 }));
        const route = { params: of({ id: 8 }), snapshot: { queryParamMap: { get: () => null } } };
        const keycloakService = { currentUserRole: RoleEnums.ARCHIVIST };
        const component = new DetailComponent(tracksService, instrumentsService, null!, keycloakService as any, null!, route as any, null!, null!, null!, tenantFeatures);

        component.ngOnInit();

        expect(tracksService.getUploadJobs).toHaveBeenCalledOnceWith(8);
    });

    [RoleEnums.USER, RoleEnums.USER_EXTERNAL].forEach((role) => {
        it(`hides PDF management and ignores its deep link for ${role}`, () => {
            const tracksService = jasmine.createSpyObj('TracksService', ['getById', 'getUploadJobs']);
            const instrumentsService = jasmine.createSpyObj('InstrumentsService', ['getAll']);
            tracksService.getById.and.returnValue(of(new Tracks()));
            tracksService.getUploadJobs.and.returnValue(of([]));
            instrumentsService.getAll.and.returnValue(of({ content: [], totalElements: 0 }));
            const route = { params: of({ id: 8 }), snapshot: { queryParamMap: { get: () => 'pdf' } } };
            const keycloakService = { currentUserRole: role, isUser: role === RoleEnums.USER, isUserExternal: role === RoleEnums.USER_EXTERNAL };
            const component = new DetailComponent(tracksService, instrumentsService, null!, keycloakService as any, null!, route as any, null!, null!, null!, tenantFeatures);

            component.ngOnInit();

            expect(component['canManagePdf']).toBeFalse();
            expect(component['activeTab']).toBe('details');
            expect(tracksService.getUploadJobs).not.toHaveBeenCalled();
        });
    });

    it('opens the playback tab from a deep link only when score playback is enabled', () => {
        const route = { params: of({ id: 8 }), snapshot: { queryParamMap: { get: () => 'playback' } } };
        const keycloakService = { currentUserRole: RoleEnums.USER, isUser: true, isUserExternal: false };
        const open = (enabled: boolean) => {
            const tracksService = jasmine.createSpyObj('TracksService', ['getById']);
            const instrumentsService = jasmine.createSpyObj('InstrumentsService', ['getAll']);
            tracksService.getById.and.returnValue(of(new Tracks()));
            instrumentsService.getAll.and.returnValue(of({ content: [], totalElements: 0 }));
            const component = new DetailComponent(tracksService, instrumentsService, null!, keycloakService as any, null!, route as any, null!, null!, null!, {
                scorePlaybackEnabled: () => enabled,
                loaded: () => true
            } as any);
            component.ngOnInit();
            return component['activeTab'];
        };

        expect(open(true)).toBe('playback');
        expect(open(false)).toBe('details');
    });

    it('opens the playback tab of a deep link once the tenant features arrive', () => {
        const route = { params: of({ id: 8 }), snapshot: { queryParamMap: { get: () => 'playback' } } };
        const keycloakService = { currentUserRole: RoleEnums.USER, isUser: true, isUserExternal: false };
        const tracksService = jasmine.createSpyObj('TracksService', ['getById']);
        const instrumentsService = jasmine.createSpyObj('InstrumentsService', ['getAll']);
        tracksService.getById.and.returnValue(of(new Tracks()));
        instrumentsService.getAll.and.returnValue(of({ content: [], totalElements: 0 }));
        let enabled = false;
        const tenantFeatures = {
            scorePlaybackEnabled: () => enabled,
            loaded: () => false,
            refresh: jasmine.createSpy().and.callFake(() => {
                enabled = true;
                return of({});
            })
        };
        const component = new DetailComponent(tracksService, instrumentsService, null!, keycloakService as any, null!, route as any, null!, null!, null!, tenantFeatures as any);

        component.ngOnInit();

        expect(tenantFeatures.refresh).toHaveBeenCalled();
        expect(component['activeTab']).toBe('playback');
    });

    it('treats details and parts as one track save unit', () => {
        const component = new DetailComponent(null!, null!, null!, null!, null!, null!, null!, null!, null!, tenantFeatures);

        component['onScoresDirtyChange'](true);
        expect(component.isDirtyForm).toBeTrue();

        component['markDetailsDirty']();
        component['onScoresDirtyChange'](false);
        expect(component.isDirtyForm).toBeTrue();
    });

    it('replaces one source page with every generated crop image', () => {
        const toastService = jasmine.createSpyObj('ToastService', ['success']);
        const component = new DetailComponent(null!, null!, null!, null!, toastService, null!, null!, null!, null!, tenantFeatures);
        component['track'] = {
            id: 4,
            version: 2,
            scores: [{ id: 5, media: [{ index: 6 }, { index: 7 }], instruments: [] }]
        } as Tracks;

        component['applyImageEditResult']({
            trackId: 4,
            trackVersion: 3,
            scoreId: 5,
            replacedMediaId: 6,
            media: [
                { index: 8, name: 'page-edited-1.png', order: 1 },
                { index: 9, name: 'page-edited-2.png', order: 2 }
            ]
        });

        expect(component['track'].scores![0].media!.map((media) => media.index)).toEqual([8, 9, 7]);
        expect(component['track'].version).toBe(3);
        expect(toastService.success).toHaveBeenCalledWith('Immagini create', 'La pagina è stata sostituita con 2 nuove immagini.');
    });

    it('names every upload state in Italian and tells the pending ones apart', () => {
        const component = new DetailComponent(null!, null!, null!, null!, null!, null!, null!, null!, null!, tenantFeatures);

        expect(component['uploadStatusLabel']('TO_PROCESS')).toBe('In coda');
        expect(component['uploadStatusLabel']('IN_PROGRESS')).toBe('Elaborazione in corso');
        expect(component['uploadStatusLabel']('DONE')).toBe('Completato');
        expect(component['uploadStatusLabel']('ERROR')).toBe('Elaborazione non riuscita');
        expect(component['uploadStatusLabel']('NOT_FOUND')).toBe('File sorgente non disponibile');
        expect(component['isPendingUpload']({ id: 1, name: 'a.pdf', trackId: 8, status: 'TO_PROCESS' })).toBeTrue();
        expect(component['isPendingUpload']({ id: 1, name: 'a.pdf', trackId: 8, status: 'IN_PROGRESS' })).toBeTrue();
        expect(component['isPendingUpload']({ id: 1, name: 'a.pdf', trackId: 8, status: 'DONE' })).toBeFalse();
    });

    it('shows the job of the accepted upload at once, without asking the server again', () => {
        const tracksService = jasmine.createSpyObj('TracksService', ['uploadPdf']);
        const toastService = jasmine.createSpyObj('ToastService', ['success']);
        tracksService.uploadPdf.and.returnValue(of({ id: 12, name: 'partitura.pdf', trackId: 8, status: 'TO_PROCESS' }));
        const component = new DetailComponent(tracksService, null!, null!, null!, toastService, null!, null!, null!, null!, tenantFeatures);
        component['track'] = { id: 8 } as Tracks;
        component['uploadJobs'] = [{ id: 11, name: 'vecchio.pdf', trackId: 8, status: 'DONE' }];

        component['handleUpload']({ files: [new File([new Uint8Array([1])], 'partitura.pdf', { type: 'application/pdf' })] });

        expect(component['uploadJobs'][0]).toEqual({ id: 12, name: 'partitura.pdf', trackId: 8, status: 'TO_PROCESS' });
        expect(component['uploadStatusLabel'](component['uploadJobs'][0].status)).toBe('In coda');
        expect(component['uploading']).toBeFalse();
        expect(toastService.success).toHaveBeenCalled();
    });

    it('puts a failed job back in the queue when the user retries it', () => {
        const tracksService = jasmine.createSpyObj('TracksService', ['retryUploadJob']);
        const toastService = jasmine.createSpyObj('ToastService', ['success']);
        tracksService.retryUploadJob.and.returnValue(of({ id: 12, name: 'partitura.pdf', trackId: 8, status: 'TO_PROCESS' }));
        const component = new DetailComponent(tracksService, null!, null!, null!, toastService, null!, null!, null!, null!, tenantFeatures);
        component['track'] = { id: 8 } as Tracks;
        const failed: TrackUploadJob = { id: 12, name: 'partitura.pdf', trackId: 8, status: 'ERROR' };
        component['uploadJobs'] = [failed];

        component['retryUpload'](failed);

        expect(tracksService.retryUploadJob).toHaveBeenCalledOnceWith(8, 12);
        expect(component['uploadJobs'][0].status).toBe('TO_PROCESS');
        expect(toastService.success).toHaveBeenCalled();
    });

    it('shows at most the five most recent jobs', () => {
        const component = new DetailComponent(null!, null!, null!, null!, null!, null!, null!, null!, null!, tenantFeatures);
        component['uploadJobs'] = Array.from({ length: 7 }, (ignored, index) => ({
            id: index,
            name: `file-${index}.pdf`,
            trackId: 8,
            status: 'DONE' as const
        }));

        expect(component['visibleUploadJobs'].map((job) => job.id)).toEqual([0, 1, 2, 3, 4]);
    });
});
