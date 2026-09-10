import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { MediaService, PrinterService } from '../../service';
import { PreviewComponent } from './preview.component';

describe('PreviewComponent', () => {
    let component: PreviewComponent;

    beforeEach(() => {
        component = new PreviewComponent(
            { scores: [], previewTitle: 'Titolo', previewSource: 'Traccia', clear: () => undefined } as unknown as PrinterService,
            { stream: (id: number) => `/media/${id}` } as MediaService,
            { navigate: () => Promise.resolve(true) } as unknown as Router,
            { back: () => undefined } as Location
        );
    });

    afterEach(() => component.ngOnDestroy());

    it('groups consecutive pages into two-up print sheets', () => {
        component['mediaStreams'] = ['/media/1', '/media/2', '/media/3'];
        component['printPagesPerSheet'] = 2;

        expect(component['printSheets']).toEqual([
            ['/media/1', '/media/2'],
            ['/media/3', null]
        ]);
        expect(component['printSheetCount']).toBe(2);
    });

    it('keeps one media per sheet in the default layout', () => {
        component['mediaStreams'] = ['/media/1', '/media/2'];

        expect(component['printSheets']).toEqual([['/media/1'], ['/media/2']]);
        expect(component['printSheetCount']).toBe(2);
    });

    it('uses one consistent orientation for every two-up sheet', () => {
        component['mediaStreams'] = ['/media/landscape-1', '/media/landscape-2', '/media/landscape-3'];
        component['printPagesPerSheet'] = 2;
        component['printPageDimensions'].set('/media/landscape-1', { width: 1400, height: 1000 });
        component['printPageDimensions'].set('/media/landscape-2', { width: 1400, height: 1000 });
        component['printPageDimensions'].set('/media/landscape-3', { width: 1400, height: 1000 });

        expect(component['printOrientation']).toBe('portrait');
        expect(component['printSheets'][1]).toEqual(['/media/landscape-3', null]);
    });

    it('places portrait pages side by side in two-up mode', () => {
        component['mediaStreams'] = ['/media/portrait-1', '/media/portrait-2'];
        component['printPagesPerSheet'] = 2;
        component['printPageDimensions'].set('/media/portrait-1', { width: 1000, height: 1400 });
        component['printPageDimensions'].set('/media/portrait-2', { width: 1000, height: 1400 });

        expect(component['printOrientation']).toBe('landscape');
    });

    it('opens and closes the filter sidebar explicitly', () => {
        component['openFilters']();
        expect(component['filtersOpen']).toBeTrue();

        component['closeFilters']();
        expect(component['filtersOpen']).toBeFalse();
    });

    it('delegates paper format and printing to the browser', () => {
        const print = spyOn(window, 'print');

        component['print']();

        expect(print).toHaveBeenCalled();
    });
});
