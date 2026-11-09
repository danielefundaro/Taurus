import { registerLocaleData } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import localeIt from '@angular/common/locales/it';
import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, ParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { PublicLegalDocument } from '../../module';
import { LegalService } from '../../service';
import { LegalDocumentComponent } from './legal-document.component';

describe('LegalDocumentComponent', () => {
    const published = (overrides: Partial<PublicLegalDocument> = {}): PublicLegalDocument => ({
        documentType: 'TERMS',
        version: '1.3',
        title: 'Termini di utilizzo di Taurus',
        action: 'ACCEPT',
        publishedAt: '2026-11-12T09:00:00Z',
        active: true,
        changeSummary: null,
        html: '<h2 id="1-accesso">1. Accesso</h2><p>Testo dei termini.</p>',
        contentSha256: 'a'.repeat(64),
        url: null,
        versions: [
            { version: '1.3', title: 'Termini di utilizzo di Taurus', publishedAt: '2026-11-12T09:00:00Z', active: true },
            { version: '1.2', title: 'Termini di utilizzo di Taurus', publishedAt: '2026-09-07T09:00:00Z', active: false }
        ],
        ...overrides
    });

    let fixture: ComponentFixture<LegalDocumentComponent>;
    let legal: jasmine.SpyObj<LegalService>;
    let meta: Meta;
    let title: Title;
    let params: BehaviorSubject<ParamMap>;

    async function open(documentType: string, version?: string): Promise<HTMLElement> {
        legal = legal ?? jasmine.createSpyObj<LegalService>('LegalService', ['getPublicDocument']);
        params = new BehaviorSubject(convertToParamMap(version ? { version } : {}));
        await TestBed.configureTestingModule({
            imports: [LegalDocumentComponent],
            providers: [provideRouter([]), { provide: LOCALE_ID, useValue: 'it' }, { provide: LegalService, useValue: legal }, { provide: ActivatedRoute, useValue: { snapshot: { data: { documentType } }, paramMap: params } }]
        }).compileComponents();
        meta = TestBed.inject(Meta);
        title = TestBed.inject(Title);
        fixture = TestBed.createComponent(LegalDocumentComponent);
        fixture.detectChanges();
        return fixture.nativeElement as HTMLElement;
    }

    beforeAll(() => registerLocaleData(localeIt));

    beforeEach(() => {
        legal = jasmine.createSpyObj<LegalService>('LegalService', ['getPublicDocument']);
    });

    afterEach(() => fixture?.destroy());

    it('shows the version in force with its text, fingerprint and the other versions', async () => {
        legal.getPublicDocument.and.returnValue(of(published()));

        const page = await open('TERMS');

        expect(legal.getPublicDocument).toHaveBeenCalledWith('TERMS', undefined, jasmine.anything());
        expect(page.querySelector('h1')?.textContent).toContain('Termini di utilizzo di Taurus');
        expect(page.querySelector('.facts')?.textContent).toContain('Versione 1.3 · in vigore dal 12 novembre 2026');
        expect(page.querySelector('.legal-text h2')?.id).toBe('1-accesso');
        expect(page.querySelector('.fingerprint')?.textContent).toContain('a'.repeat(64));
        const versions = Array.from(page.querySelectorAll('.versions a'), (link) => link.getAttribute('href'));
        expect(versions).toEqual(['/legal/terms/1.2']);
        expect(page.textContent).not.toContain('non è più in vigore');
        expect(title.getTitle()).toBe('Termini di utilizzo di Taurus');
        expect(meta.getTag("name='robots'")).toBeNull();
    });

    it('marks a retired version and keeps it out of the search engines', async () => {
        legal.getPublicDocument.and.returnValue(of(published({ version: '1.2', active: false, changeSummary: 'Nuova sezione sui dati.' })));

        const page = await open('TERMS', '1.2');

        expect(legal.getPublicDocument).toHaveBeenCalledWith('TERMS', '1.2', jasmine.anything());
        expect(page.textContent).toContain('Questa versione non è più in vigore');
        expect(page.textContent).toContain('La versione in vigore è la 1.3.');
        expect(page.querySelector('.facts')?.textContent).toContain('pubblicata il');
        expect(page.querySelector('.change-summary')?.textContent).toContain('Nuova sezione sui dati.');
        expect(meta.getTag("name='robots'")?.content).toBe('noindex');

        fixture.destroy();
        expect(meta.getTag("name='robots'")).toBeNull();
    });

    it('says when no version is in force', async () => {
        legal.getPublicDocument.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 })));

        const page = await open('PRIVACY');

        expect(legal.getPublicDocument).toHaveBeenCalledWith('PRIVACY', undefined, jasmine.anything());
        expect(page.querySelector('h1')?.textContent).toContain('Informativa privacy');
        expect(page.textContent).toContain('Documento non disponibile');
        expect(meta.getTag("name='robots'")?.content).toBe('noindex');
    });

    it('says when a version does not exist or is a draft, with the way to the version in force', async () => {
        legal.getPublicDocument.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 })));

        const page = await open('PRIVACY', '9.9');

        expect(page.textContent).toContain('Versione non trovata');
        expect(page.textContent).toContain('La versione 9.9');
    });

    it('offers a new attempt when Taurus cannot be reached', async () => {
        legal.getPublicDocument.and.returnValues(
            throwError(() => new HttpErrorResponse({ status: 0 })),
            of(published())
        );

        const page = await open('TERMS');
        expect(page.textContent).toContain('Caricamento non riuscito');

        fixture.componentInstance['load']();
        fixture.detectChanges();
        expect(page.querySelector('h1')?.textContent).toContain('Termini di utilizzo di Taurus');
    });

    it('links the address of a document published before its text lived in Taurus', async () => {
        legal.getPublicDocument.and.returnValue(of(published({ html: null, contentSha256: null, url: 'https://example.test/termini', versions: [] })));

        const page = await open('TERMS');

        const link = page.querySelector('.external a') as HTMLAnchorElement;
        expect(link.href).toBe('https://example.test/termini');
        expect(link.target).toBe('_blank');
        expect(page.querySelector('.fingerprint')).toBeNull();
        expect(page.querySelector('.versions')).toBeNull();
    });

    it('opens another version in the same page and prints from the browser', async () => {
        legal.getPublicDocument.and.returnValues(of(published()), of(published({ version: '1.2', active: false })));
        await open('TERMS');
        const scroll = spyOn(window, 'scrollTo');
        const print = spyOn(window, 'print');

        params.next(convertToParamMap({ version: '1.2' }));
        fixture.detectChanges();
        fixture.componentInstance['print']();

        expect(legal.getPublicDocument).toHaveBeenCalledWith('TERMS', '1.2', jasmine.anything());
        expect(scroll).toHaveBeenCalledWith(0, 0);
        expect(print).toHaveBeenCalled();
    });
});
