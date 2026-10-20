import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { SignatureAct, SignatureLinkAct } from '../module';
import { PublicSignatureService } from './public-signature.service';

describe('PublicSignatureService', () => {
    let service: PublicSignatureService;
    let http: HttpTestingController;
    const token = 'A'.repeat(43);
    const link = `${environment.baseUrl}/signature-links/v1/${token}`;
    const act: SignatureAct = {
        id: 5,
        type: 'LOAN_SHEET_ISSUE',
        status: 'AWAITING_SIGNATURES',
        sheetNumber: '2026/007',
        holderRole: 'GUARDIAN',
        operation: 'Emissione del foglio',
        createdAt: '2026-10-19T10:00:00Z',
        documents: [{ role: 'MAIN', frozenHash: 'a'.repeat(64), available: true }],
        signers: [],
        attestationAvailable: false,
        associationSignerChangeable: false,
        dataRequired: false,
        pendingPurposes: ['ACCEPTANCE'],
        cieSignAvailable: false,
        entityVersion: 1
    };
    const opened: SignatureLinkAct = { tenantName: 'Banda musicale', minorName: 'Luca Rossi', maskedEmail: 'g***@example.org', linkExpiresAt: '2026-10-26T10:00:00Z', act };

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), PublicSignatureService] });
        service = TestBed.inject(PublicSignatureService);
        service.use(token);
        http = TestBed.inject(HttpTestingController);
    });
    afterEach(() => http.verify());

    it('reads the act of the link and offers only the code sent by e-mail', () => {
        service.getAct().subscribe((value) => expect(value).toEqual(act));
        const request = http.expectOne(link);
        expect(request.request.method).toBe('GET');
        expect(request.request.headers.has('Authorization')).toBeFalse();
        request.flush(opened);

        service.getMethods().subscribe((methods) => {
            expect(methods.proposed).toBe('EMAIL_OTP');
            expect(methods.totpAvailable).toBeFalse();
            expect(methods.maskedEmail).toBe('g***@example.org');
        });
    });

    it('confirms the signatures through the endpoints of the link', () => {
        service.createChallenge({ purpose: 'ACCEPTANCE', method: 'EMAIL_OTP', items: [{ actId: 5, documentRole: 'MAIN', documentHash: 'a'.repeat(64) }] }).subscribe();
        const create = http.expectOne(`${link}/challenges`);
        expect(create.request.method).toBe('POST');
        expect(create.request.body).toEqual({ purpose: 'ACCEPTANCE', documentHash: 'a'.repeat(64) });
        create.flush({});

        service.resend(9).subscribe();
        http.expectOne(`${link}/challenges/9/resend`).flush({});
        service.confirm(9, '123456').subscribe();
        const confirm = http.expectOne(`${link}/challenges/9/confirm`);
        expect(confirm.request.body).toEqual({ code: '123456' });
        confirm.flush({ challengeId: 9, acts: [] });
    });

    it('saves the data of the parent and downloads the documents of the link', () => {
        service
            .submitHolderData(5, { birthPlace: 'Roma', residence: 'Via Roma 1', postalCode: '00100', cityProvince: 'Roma (RM)', noTaxCode: true, document: 'CI', documentIssuedBy: 'Comune', documentIssuedOn: '2024-01-01', holderFirstName: 'Giulia' })
            .subscribe((value) => expect(value.id).toBe(5));
        const data = http.expectOne(`${link}/holder-data`);
        expect(data.request.method).toBe('PUT');
        expect(data.request.body.holderFirstName).toBe('Giulia');
        data.flush(opened);

        service.downloadDocument(5, 'CLAUSES').subscribe();
        expect(http.expectOne(`${link}/documents/CLAUSES`).request.responseType).toBe('blob');
        service.downloadAttestation().subscribe();
        expect(http.expectOne(`${link}/attestation`).request.responseType).toBe('blob');
    });

    it('uploads the document signed with CieSign to the endpoint of the link', () => {
        const file = new File(['%PDF-signed'], 'firmato.pdf', { type: 'application/pdf' });
        service.signWithCieSign(5, 'MAIN', file).subscribe((outcome) => expect(outcome.status).toBe('AWAITING_SIGNATURES'));

        const upload = http.expectOne(`${link}/documents/MAIN/ciesign`);
        expect(upload.request.method).toBe('POST');
        expect(upload.request.headers.has('Authorization')).toBeFalse();
        expect((upload.request.body as FormData).get('file')).toEqual(jasmine.any(File));
        upload.flush({ actId: 5, type: 'LOAN_SHEET_ISSUE', status: 'AWAITING_SIGNATURES' });
    });
});
