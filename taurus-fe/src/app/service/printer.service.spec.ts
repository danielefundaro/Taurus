import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Albums, ChildrenEntities } from '../module';
import { PrinterService } from './printer.service';
import { TracksService } from './tracks.service';

const album = (): Albums => {
    const value = new Albums();
    value.name = 'Concerto di Natale';
    value.tracks = [{ index: 3 } as ChildrenEntities];

    return value;
};

describe('PrinterService album preview', () => {
    let getById: jasmine.Spy;
    let navigate: jasmine.Spy;
    let service: PrinterService;

    beforeEach(() => {
        getById = jasmine.createSpy('getById');
        navigate = jasmine.createSpy('navigate').and.returnValue(Promise.resolve(true));

        TestBed.configureTestingModule({
            providers: [
                { provide: TracksService, useValue: { getById } },
                { provide: Router, useValue: { navigate } }
            ]
        });

        service = TestBed.inject(PrinterService);
    });

    it('does not open a partial preview and offers a retry when a track fails', () => {
        getById.and.returnValue(throwError(() => new Error('non disponibile')));

        service.preview(album());

        expect(service.scores).toEqual([]);
        expect(service.previewError).toBeTrue();
        expect(navigate).not.toHaveBeenCalled();
    });

    it('repeats the failed command with the same source and selection', () => {
        getById.and.returnValue(throwError(() => new Error('non disponibile')));
        const selected = [{ index: 9 } as ChildrenEntities];
        service.preview(album(), selected);
        getById.calls.reset();
        getById.and.returnValue(of({ scores: [{ instruments: [], media: [{ index: 21 }] }] }));

        service.retry();

        expect(getById.calls.argsFor(0)[0]).toBe(9);
        expect(service.previewError).toBeFalse();
        expect(service.scores.length).toBe(1);
        expect(navigate).toHaveBeenCalledWith(['preview']);
    });

    it('hides the alert when a source page opens again', () => {
        getById.and.returnValue(throwError(() => new Error('non disponibile')));
        service.preview(album());

        service.dismissError();

        expect(service.previewError).toBeFalse();
    });

    it('clears the error when the state is released', () => {
        getById.and.returnValue(throwError(() => new Error('non disponibile')));
        service.preview(album());

        service.clear();

        expect(service.previewError).toBeFalse();
    });
});
