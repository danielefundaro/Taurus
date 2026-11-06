import { fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of } from 'rxjs';
import { LegalDocument } from '../../../module';
import { ConfirmRequest, ConfirmService, LegalService, ToastService } from '../../../service';
import { LegalDocumentDetailComponent } from './legal-document-detail.component';

describe('LegalDocumentDetailComponent', () => {
    const version = (overrides: Partial<LegalDocument> = {}): LegalDocument => ({
        id: 7,
        documentType: 'TERMS',
        version: '1.3',
        title: 'Termini di utilizzo',
        action: 'ACCEPT',
        required: true,
        content: '# Termini\n\nTesto.',
        changeSummary: null,
        state: 'DRAFT',
        publishedAt: null,
        active: false,
        contentSha256: 'b'.repeat(64),
        url: null,
        ...overrides
    });
    const inForce = version({ id: 3, version: '1.2', state: 'ACTIVE', active: true, publishedAt: '2026-09-07T09:00:00Z' });

    let legal: jasmine.SpyObj<LegalService>;
    let confirm: jasmine.SpyObj<ConfirmService>;
    let router: jasmine.SpyObj<Router>;
    let component: LegalDocumentDetailComponent;
    const state = () => component as unknown as Record<string, any>;
    const lastConfirmation = (spy: jasmine.Spy): ConfirmRequest => spy.calls.mostRecent().args[0];

    function open(params: Record<string, string>, query: Record<string, string> = {}): void {
        const route = { snapshot: { paramMap: convertToParamMap(params), queryParamMap: convertToParamMap(query) } } as unknown as ActivatedRoute;
        component = new LegalDocumentDetailComponent(route, router, legal, confirm, jasmine.createSpyObj<ToastService>('ToastService', ['success']));
        component.ngOnInit();
    }

    beforeEach(() => {
        legal = jasmine.createSpyObj<LegalService>('LegalService', ['getDocument', 'getDocuments', 'createDocument', 'updateDocument', 'deleteDocument', 'activateDocument', 'withdrawDocument', 'preview']);
        confirm = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['confirmReversible', 'confirmDestructive']);
        router = jasmine.createSpyObj<Router>('Router', ['navigate']);
        router.navigate.and.resolveTo(true);
        legal.getDocuments.and.returnValue(of([inForce]));
        legal.preview.and.callFake((content: string) => of(`<p>${content.length}</p>`));
    });

    afterEach(() => component?.ngOnDestroy());

    it('edits a draft and shows its preview at once', fakeAsync(() => {
        legal.getDocument.and.returnValue(of(version()));

        open({ id: '7' });
        tick();

        expect(state()['isDraft']).toBeTrue();
        expect(state()['contentEditable']).toBeTrue();
        expect(legal.preview).toHaveBeenCalledOnceWith('# Termini\n\nTesto.', jasmine.anything());
        expect(state()['previewHtml']).toBe('<p>17</p>');
        expect(component.isDirty).toBeFalse();
    }));

    it('updates the preview 500 ms after the last change and marks the page as changed', fakeAsync(() => {
        legal.getDocument.and.returnValue(of(version()));
        open({ id: '7' });
        tick();
        legal.preview.calls.reset();

        state()['document'].content = 'A';
        state()['contentChanged']('A');
        tick(200);
        state()['document'].content = 'AB';
        state()['contentChanged']('AB');
        tick(499);
        expect(legal.preview).not.toHaveBeenCalled();
        tick(1);

        expect(legal.preview).toHaveBeenCalledOnceWith('AB', jasmine.anything());
        expect(component.isDirty).toBeTrue();
        expect(component.dirtyUnitLabels).toEqual(['documento']);
    }));

    it('checks the version, the title and the text before saving', () => {
        open({});

        state()['save']();

        expect(legal.createDocument).not.toHaveBeenCalled();
        expect(state()['versionError']).toBe('Indica la versione del documento.');
        expect(state()['titleError']).toBe('Indica il titolo del documento.');
        expect(state()['contentError']).toBe('Scrivi il testo del documento.');

        state()['document'].version = '1 3';
        expect(state()['versionError']).toContain('Usa solo lettere, numeri');
        state()['document'].content = 'x'.repeat(100_001);
        expect(state()['contentError']).toBe('Il testo supera i 100.000 caratteri.');
    });

    it('saves a new draft and moves to its address', () => {
        legal.createDocument.and.returnValue(of(version({ id: 9 })));
        open({});
        Object.assign(state()['document'], { version: ' 1.3 ', title: ' Termini di utilizzo ', content: '# Termini', changeSummary: '  ' });

        state()['save']();

        expect(legal.createDocument).toHaveBeenCalledOnceWith({ documentType: 'TERMS', version: '1.3', title: 'Termini di utilizzo', action: 'ACCEPT', required: true, content: '# Termini', changeSummary: null }, jasmine.anything());
        expect(router.navigate).toHaveBeenCalledWith(['/legal-documents', 9], { replaceUrl: true });
        expect(component.isDirty).toBeFalse();
    });

    it('publishes after a confirmation that names the version it replaces', () => {
        legal.getDocument.and.returnValue(of(version()));
        legal.activateDocument.and.returnValue(of(version({ state: 'ACTIVE', active: true, publishedAt: '2026-11-12T09:00:00Z' })));
        open({ id: '7' });

        state()['publish']();
        const request = lastConfirmation(confirm.confirmReversible);
        expect(request.consequence).toBe('La versione 1.3 sostituisce la 1.2. Da ora il testo non si può più modificare e gli utenti dovranno confermarlo al prossimo accesso.');
        request.accept();

        expect(legal.updateDocument).not.toHaveBeenCalled();
        expect(legal.activateDocument).toHaveBeenCalledWith(7, jasmine.anything());
        expect(state()['isDraft']).toBeFalse();
        expect(state()['contentEditable']).toBeFalse();
    });

    it('saves the changes before publishing an optional document', () => {
        legal.getDocument.and.returnValue(of(version({ documentType: 'PRIVACY', required: false })));
        legal.updateDocument.and.callFake((id: number, document: LegalDocument) => of(version({ ...document, id })));
        legal.activateDocument.and.returnValue(of(version({ documentType: 'PRIVACY', state: 'ACTIVE', active: true })));
        open({ id: '7' });
        state()['document'].title = 'Informativa';
        component.isDirty = true;

        state()['publish']();
        const request = lastConfirmation(confirm.confirmReversible);
        expect(request.consequence).toBe('La versione 1.3 entra in vigore. Da ora il testo non si può più modificare e sarà visibile a tutti.');
        request.accept();

        expect(legal.updateDocument).toHaveBeenCalledWith(7, jasmine.objectContaining({ title: 'Informativa' }), jasmine.anything());
        expect(legal.activateDocument).toHaveBeenCalledWith(7, jasmine.anything());
    });

    it('shows a published version read only, to withdraw or to copy into a new version', () => {
        legal.getDocument.and.returnValue(of(version({ state: 'ACTIVE', active: true, publishedAt: '2026-11-12T09:00:00Z' })));
        legal.withdrawDocument.and.returnValue(of(version({ state: 'RETIRED', active: false, publishedAt: '2026-11-12T09:00:00Z' })));
        open({ id: '7' });

        expect(state()['isDraft']).toBeFalse();
        expect(state()['completing']).toBeFalse();
        expect(state()['publicPath']).toBe('/legal/terms/1.3');

        state()['newVersion']();
        expect(router.navigate).toHaveBeenCalledWith(['/legal-documents/new'], { queryParams: { from: 7 } });

        state()['withdraw']();
        const request = lastConfirmation(confirm.confirmReversible);
        expect(request.consequence).toContain('«Documento non disponibile»');
        request.accept();
        expect(legal.withdrawDocument).toHaveBeenCalledWith(7, jasmine.anything());
        expect(state()['saved'].state).toBe('RETIRED');
    });

    it('reactivates a retired version in place of the one in force', () => {
        legal.getDocument.and.returnValue(of(version({ id: 2, version: '1.1', state: 'RETIRED', publishedAt: '2026-06-01T09:00:00Z' })));
        legal.activateDocument.and.returnValue(of(version({ id: 2, version: '1.1', state: 'ACTIVE', active: true })));
        open({ id: '2' });

        state()['reactivate']();
        const request = lastConfirmation(confirm.confirmReversible);
        expect(request.consequence).toContain('La versione 1.1 torna in vigore al posto della 1.2.');
        request.accept();

        expect(legal.activateDocument).toHaveBeenCalledWith(2, jasmine.anything());
    });

    it('starts a new version from a published one, with an empty version and summary', () => {
        legal.getDocument.and.returnValue(of(version({ state: 'ACTIVE', active: true, changeSummary: 'Vecchia sintesi', required: false })));

        open({}, { from: '7' });

        expect(legal.getDocument).toHaveBeenCalledWith(7, jasmine.anything());
        expect(state()['isNew']).toBeTrue();
        expect(state()['document']).toEqual(jasmine.objectContaining({ documentType: 'TERMS', title: 'Termini di utilizzo', action: 'ACCEPT', required: false, content: '# Termini\n\nTesto.', version: '', changeSummary: '' }));
    });

    it('adds once the text of a document published with its address', () => {
        const withAddress = version({ id: 4, state: 'ACTIVE', active: true, content: null, contentSha256: null, url: 'https://example.test/termini', publishedAt: '2026-09-07T09:00:00Z' });
        legal.getDocument.and.returnValue(of(withAddress));
        legal.updateDocument.and.returnValue(of({ ...withAddress, content: 'Testo', contentSha256: 'c'.repeat(64) }));
        open({ id: '4' });
        expect(state()['completing']).toBeTrue();
        expect(state()['contentEditable']).toBeTrue();

        state()['document'].content = 'Testo';
        state()['completeText']();
        const request = lastConfirmation(confirm.confirmReversible);
        expect(request.consequence).toContain('https://example.test/termini');
        request.accept();

        expect(legal.updateDocument).toHaveBeenCalledWith(4, jasmine.objectContaining({ version: '1.3', content: 'Testo' }), jasmine.anything());
        expect(state()['completing']).toBeFalse();
    });

    it('deletes a draft after a confirmation and returns to the list', () => {
        legal.getDocument.and.returnValue(of(version()));
        legal.deleteDocument.and.returnValue(of(undefined));
        open({ id: '7' });

        state()['executeDanger']('delete');
        lastConfirmation(confirm.confirmDestructive).accept();

        expect(legal.deleteDocument).toHaveBeenCalledWith(7, jasmine.anything());
        expect(router.navigate).toHaveBeenCalledWith(['/legal-documents']);
    });
});
