import { HttpContext, HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { of, throwError } from 'rxjs';
import { SignatureAct } from '../../module';
import { ApiErrorService, SignatureService } from '../../service';
import { SignatureActDialogComponent, SignatureActDialogData } from './signature-act-dialog.component';

describe('SignatureActDialogComponent', () => {
    const issue = (overrides: Partial<SignatureAct> = {}): SignatureAct => ({
        id: 5,
        type: 'LOAN_SHEET_ISSUE',
        status: 'AWAITING_DATA',
        sheetId: 1,
        sheetNumber: '2026/001',
        operation: 'Emissione del foglio',
        createdAt: '2026-10-19T10:00:00Z',
        documents: [],
        signers: [],
        attestationAvailable: false,
        associationSignerChangeable: true,
        dataRequired: true,
        memberBirthDate: '1990-03-04',
        pendingPurposes: [],
        cieSignAvailable: false,
        entityVersion: 1,
        ...overrides
    });
    const withDocuments = issue({
        status: 'AWAITING_SIGNATURES',
        dataRequired: false,
        pendingPurposes: ['ACCEPTANCE', 'CLAUSE_APPROVAL'],
        documents: [
            { role: 'MAIN', frozenHash: 'a'.repeat(64), available: true },
            { role: 'CLAUSES', frozenHash: 'b'.repeat(64), available: true }
        ],
        entityVersion: 2
    });
    let close: jasmine.Spy;
    let signatureService: jasmine.SpyObj<SignatureService>;
    let router: jasmine.SpyObj<Router>;

    function create(act: SignatureAct): SignatureActDialogComponent {
        close = jasmine.createSpy('close');
        signatureService = jasmine.createSpyObj<SignatureService>('SignatureService', ['submitHolderData', 'downloadDocument', 'signWithCieSign']);
        router = jasmine.createSpyObj<Router>('Router', ['navigate']);
        const config = new DynamicDialogConfig<SignatureActDialogData>();
        config.data = { act };
        return new SignatureActDialogComponent({ close } as unknown as DynamicDialogRef<SignatureActDialogComponent>, signatureService, TestBed.inject(ApiErrorService), config, router);
    }

    function fill(component: SignatureActDialogComponent): void {
        component['form'] = {
            ...component['form'],
            birthPlace: 'Roma',
            residence: 'Via Garibaldi 5',
            postalCode: '00100',
            cityProvince: 'Roma (RM)',
            taxCode: 'rssmra85t10a562s',
            noTaxCode: false,
            document: 'Carta d’identità CA00000AA',
            documentIssuedBy: 'Comune di Roma',
            documentIssuedOn: new Date(2024, 4, 2),
            phone: ''
        };
    }

    it('asks for the holder data first and moves to the signature once they are saved', () => {
        const component = create(issue());
        expect(component['invalidCount']).toBe(8);
        component['confirm']();
        expect(signatureService.submitHolderData).not.toHaveBeenCalled();

        fill(component);
        signatureService.submitHolderData.and.returnValue(of(withDocuments));
        expect(component['invalidCount']).toBe(0);
        component['confirm']();

        expect(signatureService.submitHolderData).toHaveBeenCalledWith(5, jasmine.objectContaining({ taxCode: 'RSSMRA85T10A562S', documentIssuedOn: '2024-05-02', phone: undefined }), jasmine.any(HttpContext));
        expect(component['dataStep']).toBeFalse();
        expect(component['purpose']).toBe('ACCEPTANCE');
        expect(close).not.toHaveBeenCalled();
    });

    it('asks a parent or guardian also for the own data and the birthplace of the minor', () => {
        const component = create(issue({ holderRole: 'GUARDIAN' }));
        fill(component);
        expect(component['invalidCount']).toBe(4);

        component['form'].holderFirstName = 'Giulia';
        component['form'].holderLastName = 'Bianchi';
        component['form'].holderBirthDate = new Date();
        component['form'].minorBirthPlace = 'Napoli';
        expect(component['invalidCount']).toBe(1);
        expect(component['fieldError']('holderBirthDate', true)).toContain('maggiorenne');

        component['form'].holderBirthDate = new Date(1980, 2, 4);
        signatureService.submitHolderData.and.returnValue(of(withDocuments));
        component['confirm']();

        expect(signatureService.submitHolderData).toHaveBeenCalledWith(5, jasmine.objectContaining({ holderFirstName: 'Giulia', holderLastName: 'Bianchi', holderBirthDate: '1980-03-04', minorBirthPlace: 'Napoli' }), jasmine.any(HttpContext));
    });

    it('lets the minor acknowledge the sheet signed by the parent', () => {
        const component = create(issue({ status: 'AWAITING_SIGNATURES', dataRequired: false, pendingPurposes: ['MINOR_ACKNOWLEDGMENT'], documents: withDocuments.documents }));

        expect(component['heading']).toBe('Presa visione del foglio');
        expect(component['readLabel']).toContain('prendo visione');
        component['read'] = true;
        component['confirm']();
        expect(close).toHaveBeenCalledWith(jasmine.objectContaining({ request: { purpose: 'MINOR_ACKNOWLEDGMENT', items: [{ actId: 5, documentRole: 'MAIN', documentHash: 'a'.repeat(64) }] } }));
    });

    it('marks the fields refused by the server', () => {
        const component = create(issue());
        fill(component);
        signatureService.submitHolderData.and.returnValue(throwError(() => new HttpErrorResponse({ status: 422, error: { message: 'error.signature.holderData.invalid', args: { fields: ['taxCode'] } } })));

        component['confirm']();

        expect(component['missing']('taxCode')).toBeTrue();
        expect(component['fieldError']('taxCode', false)).toContain('codice fiscale');
        expect(close).not.toHaveBeenCalled();
    });

    it('sends the member to the profile while the date of birth is missing', () => {
        const component = create(issue({ memberBirthDate: undefined }));
        fill(component);

        expect(component['birthDateMissing']).toBeTrue();
        expect(component['confirmLabel']).toBe('Vai al profilo');
        expect(component['invalidCount']).toBe(0);
        component['confirm']();

        expect(signatureService.submitHolderData).not.toHaveBeenCalled();
        expect(close).toHaveBeenCalledWith({ act: jasmine.objectContaining({ id: 5 }) });
        expect(router.navigate).toHaveBeenCalledWith(['/profile']);
    });

    it('asks the parent to turn to the association for the date of birth of the minor', () => {
        const component = create(issue({ holderRole: 'GUARDIAN', memberBirthDate: undefined }));

        expect(component['confirmLabel']).toBe('Chiudi');
        component['confirm']();

        expect(close).toHaveBeenCalled();
        expect(router.navigate).not.toHaveBeenCalled();
    });

    it('lets the holder sign only after the acknowledgments of the items of the sheet', () => {
        const data = create(issue({ pendingAcknowledgments: 2 }));
        fill(data);
        expect(data['blocked']).toBeTrue();
        expect(data['acknowledgmentsHeading']).toBe('Prendi prima visione delle 2 consegne in attesa');
        expect(data['confirmLabel']).toBe('Chiudi');
        data['confirm']();
        expect(signatureService.submitHolderData).not.toHaveBeenCalled();
        expect(close).toHaveBeenCalled();
        expect(router.navigate).not.toHaveBeenCalled();

        const signature = create({ ...withDocuments, pendingAcknowledgments: 1 });
        signature['read'] = true;
        expect(signature['acknowledgmentsHeading']).toBe('Prendi prima visione della consegna in attesa');
        signature['confirm']();
        expect(close).toHaveBeenCalledWith({ act: jasmine.objectContaining({ id: 5 }) });

        expect(create({ ...withDocuments, holderRole: 'GUARDIAN', pendingAcknowledgments: 1 })['acknowledgmentsHeading']).toContain('Il socio deve prima');
        expect(create({ ...withDocuments, pendingPurposes: ['ASSOCIATION_SIGNATURE'], pendingAcknowledgments: 1 })['blocked']).toBeFalse();
    });

    it('stops at the acknowledgments when the server refuses the data for them', () => {
        const component = create(issue());
        fill(component);
        signatureService.submitHolderData.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'error.signature.act.acknowledgmentsPending', args: { count: 3 } } })));

        component['confirm']();

        expect(component['blocked']).toBeTrue();
        expect(component['acknowledgmentsHeading']).toBe('Prendi prima visione delle 3 consegne in attesa');
        expect(close).not.toHaveBeenCalled();
    });

    it('stops at the date of birth when the server finds it missing', () => {
        const component = create(issue());
        fill(component);
        signatureService.submitHolderData.and.returnValue(throwError(() => new HttpErrorResponse({ status: 422, error: { message: 'error.signature.holderData.birthDateMissing', args: { holderRole: 'MEMBER' } } })));

        component['confirm']();

        expect(component['birthDateMissing']).toBeTrue();
        expect(close).not.toHaveBeenCalled();
    });

    it('accepts no tax code when the holder declares not to have one', () => {
        const component = create(issue());
        fill(component);
        component['form'].taxCode = '';
        expect(component['invalidCount']).toBe(1);
        component['form'].noTaxCode = true;
        expect(component['invalidCount']).toBe(0);
    });

    it('closes with the confirmation of the document read by the signer', () => {
        const component = create(withDocuments);
        component['confirm']();
        expect(close).not.toHaveBeenCalled();

        component['read'] = true;
        component['confirm']();

        expect(close).toHaveBeenCalledWith({
            act: withDocuments,
            request: { purpose: 'ACCEPTANCE', items: [{ actId: 5, documentRole: 'MAIN', documentHash: 'a'.repeat(64) }] }
        });
    });

    it('offers CieSign to the holder with an Italian tax code and records the signature with the uploaded file', () => {
        const component = create({ ...withDocuments, cieSignAvailable: true });
        expect(component['cieSignOffered']).toBeTrue();
        expect(component['wayOptions'].map((option) => option.label)).toEqual(['Codice via e-mail', 'CieSign']);
        // The same list at every change detection: a new one makes the SelectButton recreate its buttons endlessly.
        expect(component['wayOptions']).toBe(component['wayOptions']);
        component['way'] = 'CIESIGN';
        component['read'] = true;
        expect(component['confirmLabel']).toBe('Carica e firma');
        expect(component['invalidCount']).toBe(1);
        expect(component['cieSignFileError'](true)).toContain('Scegli il file');

        const file = new File(['%PDF-signed'], 'firmato.pdf', { type: 'application/pdf' });
        component['onCieSignFile']({ target: { files: [file] } } as unknown as Event);
        expect(component['invalidCount']).toBe(0);
        const outcome = { actId: 5, type: 'LOAN_SHEET_ISSUE' as const, status: 'AWAITING_SIGNATURES' as const };
        signatureService.signWithCieSign.and.returnValue(of(outcome));
        component['confirm']();

        expect(signatureService.signWithCieSign).toHaveBeenCalledWith(5, 'MAIN', file, jasmine.any(HttpContext));
        expect(close).toHaveBeenCalledWith({ act: jasmine.objectContaining({ id: 5 }), outcome });
    });

    it('keeps the window open with the reason of a refused CieSign file', () => {
        const component = create({ ...withDocuments, holderRole: 'GUARDIAN', cieSignAvailable: true, pendingPurposes: ['CLAUSE_APPROVAL'] });
        expect(component['wayOptions'][1].label).toBe('CieSign (consigliato)');
        component['way'] = 'CIESIGN';
        component['read'] = true;
        component['onCieSignFile']({ target: { files: [new File(['x'], 'altro.pdf')] } } as unknown as Event);
        signatureService.signWithCieSign.and.returnValue(throwError(() => new HttpErrorResponse({ status: 422, error: { message: 'error.signature.cieSign.signerMismatch' } })));

        component['confirm']();

        expect(signatureService.signWithCieSign).toHaveBeenCalledWith(5, 'CLAUSES', jasmine.any(File), jasmine.any(HttpContext));
        expect(component['cieSignError']).toContain('altra persona');
        expect(close).not.toHaveBeenCalled();

        component['way'] = 'CODE';
        component['changeWay']();
        expect(component['cieSignError']).toBeUndefined();
        component['confirm']();
        expect(close).toHaveBeenCalledWith(jasmine.objectContaining({ request: jasmine.objectContaining({ purpose: 'CLAUSE_APPROVAL' }) }));
    });

    it('refuses a file larger than 25 MB and does not offer CieSign to other signers', () => {
        const component = create({ ...withDocuments, cieSignAvailable: true });
        component['way'] = 'CIESIGN';
        component['read'] = true;
        const large = new File(['x'], 'grande.pdf');
        Object.defineProperty(large, 'size', { value: 25 * 1024 * 1024 + 1 });
        component['onCieSignFile']({ target: { files: [large] } } as unknown as Event);
        expect(component['cieSignFileError'](false)).toContain('25 MB');
        component['confirm']();
        expect(signatureService.signWithCieSign).not.toHaveBeenCalled();

        expect(create({ ...withDocuments, cieSignAvailable: true, pendingPurposes: ['ASSOCIATION_SIGNATURE'] })['cieSignOffered']).toBeFalse();
        expect(create(withDocuments)['cieSignOffered']).toBeFalse();
    });

    it('signs the approval of the clauses on its own document', () => {
        const component = create({ ...withDocuments, pendingPurposes: ['CLAUSE_APPROVAL'] });
        component['read'] = true;
        component['confirm']();

        expect(component['readLabel']).toContain('Approvo specificamente');
        expect(close).toHaveBeenCalledWith(jasmine.objectContaining({ request: jasmine.objectContaining({ purpose: 'CLAUSE_APPROVAL', items: [{ actId: 5, documentRole: 'CLAUSES', documentHash: 'b'.repeat(64) }] }) }));
    });
});
