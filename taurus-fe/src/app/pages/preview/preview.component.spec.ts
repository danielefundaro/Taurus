import { Location } from '@angular/common';
import { ChangeDetectorRef } from '@angular/core';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { DomSanitizer } from '@angular/platform-browser';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { SheetsMusic } from '../../module';
import { MediaService, PrinterService } from '../../service';
import { PreviewComponent } from './preview.component';

const TRANSPARENT_PIXEL = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';

const imageBlob = (): Blob => new Blob([Uint8Array.from(atob(TRANSPARENT_PIXEL), (character) => character.charCodeAt(0))], { type: 'image/png' });

const scores: SheetsMusic[] = [
    { instruments: [{ index: 7, name: 'Flauto' }], media: [{ index: 11 }, { index: 12 }] },
    { instruments: [], media: [{ index: 13 }] }
];

describe('PreviewComponent', () => {
    let component: PreviewComponent;

    beforeEach(() => {
        component = new PreviewComponent(
            { scores: [], previewTitle: 'Titolo', previewSource: 'Traccia', clear: () => undefined } as unknown as PrinterService,
            { stream: (id: number) => `/media/${id}`, streamImage: () => of(imageBlob()) } as unknown as MediaService,
            { navigate: () => Promise.resolve(true) } as unknown as Router,
            { back: () => undefined } as Location,
            { bypassSecurityTrustUrl: (url: string) => url } as unknown as DomSanitizer,
            { markForCheck: () => undefined } as ChangeDetectorRef
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

    it('starts with the sidebar open above the responsive breakpoint', () => {
        spyOn(window, 'matchMedia').and.returnValue({ matches: true } as MediaQueryList);

        component.ngOnInit();

        expect(component['filtersOpen']).toBeTrue();
    });

    it('starts with the sidebar closed up to the responsive breakpoint', () => {
        spyOn(window, 'matchMedia').and.returnValue({ matches: false } as MediaQueryList);

        component.ngOnInit();

        expect(component['filtersOpen']).toBeFalse();
    });
});

describe('PreviewComponent template', () => {
    let fixture: ComponentFixture<PreviewComponent>;
    let component: PreviewComponent;
    let streamImage: jasmine.Spy;

    beforeEach(async () => {
        streamImage = jasmine.createSpy('streamImage').and.callFake(() => of(imageBlob()));

        await TestBed.configureTestingModule({
            imports: [PreviewComponent],
            providers: [
                provideNoopAnimations(),
                provideRouter([]),
                { provide: Location, useValue: { back: () => undefined } },
                { provide: PrinterService, useValue: { scores, previewTitle: 'Marcia sinfonica', previewSource: 'Traccia', clear: () => undefined } }
            ]
        })
            .overrideComponent(PreviewComponent, {
                set: { providers: [{ provide: MediaService, useValue: { stream: (id: number) => `/media/${id}`, streamImage } }] }
            })
            .compileComponents();

        fixture = TestBed.createComponent(PreviewComponent);
        component = fixture.componentInstance;
        spyOn(component as unknown as { isDesktopViewport: () => boolean }, 'isDesktopViewport').and.returnValue(true);
        fixture.detectChanges();
    });

    afterEach(() => fixture.destroy());

    const query = <T extends HTMLElement>(selector: string): T => fixture.nativeElement.querySelector(selector);

    const click = (selector: string): void => {
        query(selector).click();
        fixture.detectChanges();
    };

    // Le modifiche applicate direttamente allo stato non passano da un evento: la view OnPush va marcata.
    const render = (): void => {
        fixture.componentRef.changeDetectorRef.markForCheck();
        fixture.detectChanges();
    };

    it('keeps aria-expanded aligned with the sidebar that is really visible', () => {
        expect(query('.filter-action').getAttribute('aria-expanded')).toBe('true');

        click('.close-filters');

        expect(query('.filter-action').getAttribute('aria-expanded')).toBe('false');

        click('.filter-action');

        expect(query('.filter-action').getAttribute('aria-expanded')).toBe('true');
    });

    it('keeps the closed sidebar out of the focus order', () => {
        click('.close-filters');

        expect(getComputedStyle(query('.preview-sidebar')).visibility).toBe('hidden');
    });

    it('returns the focus to the header command when the sidebar closes', fakeAsync(() => {
        click('.close-filters');
        tick();

        expect(document.activeElement).toBe(query('.filter-action'));
    }));

    it('moves the focus to the first useful control when the sidebar opens', fakeAsync(() => {
        click('.close-filters');
        tick();

        click('.filter-action');
        tick();

        expect(document.activeElement).toBe(query('#selectAll'));
    }));

    it('closes the presentation before the sidebar when Escape is pressed', fakeAsync(() => {
        component['openGalleria']();
        render();

        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
        fixture.detectChanges();

        expect(component['displayGalleria']).toBeFalse();
        expect(component['filtersOpen']).toBeTrue();

        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
        fixture.detectChanges();
        tick();

        expect(component['filtersOpen']).toBeFalse();
        expect(query('.filter-action').getAttribute('aria-expanded')).toBe('false');
    }));

    it('opens the presentation on the current page and keeps it synchronised on close', () => {
        component['currentPage'] = 3;

        component['openGalleria']();
        expect(component['presentationIndex']).toBe(2);

        component['presentationIndexChange'](0);
        component['presentationVisibleChange'](false);
        render();

        expect(component['displayGalleria']).toBeFalse();
        expect(component['currentPage']).toBe(1);
    });

    it('announces the number of included pages in a polite live region', () => {
        expect(query('[aria-live="polite"]').textContent?.trim()).toBe("3 pagine incluse nell'anteprima.");
    });

    it('shows a recoverable placeholder for a page that cannot be loaded', () => {
        streamImage.and.returnValue(throwError(() => new Error('stream non disponibile')));

        component['retryMediaPage']('/media/11');
        render();

        const placeholder = query('.media-placeholder--error');
        expect(placeholder).not.toBeNull();
        expect(placeholder.textContent).toContain('Pagina 1 non disponibile');

        streamImage.and.callFake(() => of(imageBlob()));
        placeholder.querySelector('button')!.click();
        fixture.detectChanges();

        expect(query('.media-placeholder--error')).toBeNull();
        expect(query('.preview-image')).not.toBeNull();
    });

    it('leaves the other pages usable when a single page fails', () => {
        streamImage.and.returnValue(throwError(() => new Error('stream non disponibile')));
        component['retryMediaPage']('/media/11');
        render();

        click('[aria-label="Pagina successiva"]');

        expect(query('.media-placeholder--error')).toBeNull();
        expect(query('.preview-image')).not.toBeNull();
    });
});
