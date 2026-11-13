import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { LegalDocument } from '../module';
import { legalDocumentPath, LegalService } from './legal.service';

describe('LegalService', () => {
    let service: LegalService;
    let controller: HttpTestingController;
    const admin = `${environment.baseUrl}/legal/documents`;
    const draft: LegalDocument = { documentType: 'TERMS', version: '1.3', title: 'Termini', action: 'ACCEPT', required: true, content: '# Termini' };

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(withXhr()), provideHttpClientTesting()] });
        service = TestBed.inject(LegalService);
        controller = TestBed.inject(HttpTestingController);
    });
    afterEach(() => controller.verify());

    it('builds the public address of a document and of each version', () => {
        expect(legalDocumentPath('TERMS')).toBe('/legal/terms');
        expect(legalDocumentPath('PRIVACY', '1.1')).toBe('/legal/privacy/1.1');
        expect(legalDocumentPath('TERMS', '2026/1')).toBe('/legal/terms/2026%2F1');
    });

    it('reads the published versions from the public API', () => {
        service.getPublicDocument('TERMS').subscribe();
        controller.expectOne(`${environment.baseUrl}/legal-documents/v1/terms`).flush({});

        service.getPublicDocument('PRIVACY', '1.1').subscribe();
        controller.expectOne(`${environment.baseUrl}/legal-documents/v1/privacy/1.1`).flush({});
    });

    it('manages the versions and forgets the cached status after each change', () => {
        service.getStatus().subscribe();
        controller.expectOne(`${environment.baseUrl}/legal/status`).flush({ compliant: true, documents: [] });

        service.getDocument(7).subscribe();
        controller.expectOne(`${admin}/7`).flush(draft);
        service.createDocument(draft).subscribe();
        expect(controller.expectOne({ method: 'POST', url: admin }).request.body).toEqual(draft);
        service.updateDocument(7, draft).subscribe();
        controller.expectOne({ method: 'PUT', url: `${admin}/7` }).flush(draft);
        service.deleteDocument(7).subscribe();
        controller.expectOne({ method: 'DELETE', url: `${admin}/7` }).flush(null);
        service.activateDocument(7).subscribe();
        controller.expectOne({ method: 'POST', url: `${admin}/7/activation` }).flush(draft);
        service.withdrawDocument(7).subscribe();
        controller.expectOne({ method: 'DELETE', url: `${admin}/7/activation` }).flush(draft);

        service.getStatus().subscribe();
        controller.expectOne(`${environment.baseUrl}/legal/status`).flush({ compliant: true, documents: [] });
    });

    it('turns the text into the HTML of the preview', () => {
        let html: string | undefined;
        service.preview('# Titolo').subscribe((value) => (html = value));

        const request = controller.expectOne({ method: 'POST', url: `${admin}/preview` });
        expect(request.request.body).toEqual({ content: '# Titolo' });
        request.flush({ html: '<h2 id="titolo">Titolo</h2>' });
        expect(html).toBe('<h2 id="titolo">Titolo</h2>');
    });
});
