import { HttpErrorResponse } from '@angular/common/http';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { publicSignaturePage } from '../../constants';
import { SignatureActFlow } from '../../dialogs/signature-act-dialog/signature-act-flow';
import { SignatureAct, SignatureLinkAct } from '../../module';
import { PublicSignatureService, ToastService } from '../../service';
import { SignatureLinkComponent } from './signature-link.component';

describe('SignatureLinkComponent', () => {
    const act = (overrides: Partial<SignatureAct> = {}): SignatureAct => ({
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
        pendingPurposes: ['ACCEPTANCE', 'CLAUSE_APPROVAL'],
        cieSignAvailable: false,
        entityVersion: 1,
        ...overrides
    });
    const link = (value: SignatureAct): SignatureLinkAct => ({ tenantName: 'Banda musicale', minorName: 'Luca Rossi', maskedEmail: 'g***@example.org', linkExpiresAt: '2026-10-26T10:00:00Z', act: value });
    let signatures: jasmine.SpyObj<PublicSignatureService>;
    let flow: jasmine.SpyObj<SignatureActFlow>;
    let toast: jasmine.SpyObj<ToastService>;
    let meta: jasmine.SpyObj<Meta>;

    function create(): SignatureLinkComponent {
        signatures = jasmine.createSpyObj<PublicSignatureService>('PublicSignatureService', ['use', 'getLinkAct', 'downloadDocument', 'downloadAttestation']);
        flow = jasmine.createSpyObj<SignatureActFlow>('SignatureActFlow', ['openAct', 'confirmWith']);
        toast = jasmine.createSpyObj<ToastService>('ToastService', ['success']);
        meta = jasmine.createSpyObj<Meta>('Meta', ['updateTag']);
        const route = { snapshot: { paramMap: convertToParamMap({ token: 'A'.repeat(43) }) } } as unknown as ActivatedRoute;
        return new SignatureLinkComponent(route, signatures, flow, toast, meta, jasmine.createSpyObj<Title>('Title', ['setTitle']));
    }

    it('opens the act of the token without sending the address as referrer', () => {
        const component = create();
        signatures.getLinkAct.and.returnValue(of(link(act())));

        component.ngOnInit();

        expect(signatures.use).toHaveBeenCalledWith('A'.repeat(43));
        expect(meta.updateTag).toHaveBeenCalledWith({ name: 'referrer', content: 'no-referrer' });
        expect(component['state']).toBe('ready');
        expect(component['toSign']).toBeTrue();
        expect(component['signLabel']).toBe('Firma');
    });

    it('explains why a link cannot be opened', () => {
        const component = create();
        signatures.getLinkAct.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
        component.ngOnInit();
        expect(component['state']).toBe('unavailable');
        expect(component['problem']).toBe('unavailable');

        signatures.getLinkAct.and.returnValue(throwError(() => new HttpErrorResponse({ status: 429 })));
        component['load']();
        expect(component['problem']).toBe('tooManyRequests');

        signatures.getLinkAct.and.returnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
        component['load']();
        expect(component['problem']).toBe('network');
    });

    it('goes on with the approval of the clauses and stops once the act is signed by everybody', () => {
        const component = create();
        const first = act();
        const clauses = act({ pendingPurposes: ['CLAUSE_APPROVAL'] });
        signatures.getLinkAct.and.returnValues(of(link(first)), of(link(clauses)));
        flow.openAct.and.callFake((value: SignatureAct) => of({ act: value, request: { purpose: value.pendingPurposes[0], items: [{ actId: 5, documentRole: 'MAIN', documentHash: 'a'.repeat(64) }] } }));
        flow.confirmWith.and.returnValues(of({ challengeId: 1, acts: [{ actId: 5, type: 'LOAN_SHEET_ISSUE', status: 'AWAITING_SIGNATURES' }] }), of({ challengeId: 2, acts: [{ actId: 5, type: 'LOAN_SHEET_ISSUE', status: 'SIGNED' }] }));
        component.ngOnInit();

        component['sign']();

        expect(flow.openAct).toHaveBeenCalledTimes(2);
        expect(flow.openAct.calls.mostRecent().args[0]).toEqual(clauses);
        expect(component['state']).toBe('completed');
        expect(signatures.getLinkAct).toHaveBeenCalledTimes(2);
    });

    it('completes the act signed with CieSign without the code window', () => {
        const component = create();
        signatures.getLinkAct.and.returnValue(of(link(act({ pendingPurposes: ['ACCEPTANCE'], type: 'LOAN_SHEET_VARIATION', cieSignAvailable: true }))));
        flow.openAct.and.callFake((value: SignatureAct) => of({ act: value, outcome: { actId: 5, type: 'LOAN_SHEET_VARIATION', status: 'SIGNED' } }));
        component.ngOnInit();

        component['sign']();

        expect(flow.confirmWith).not.toHaveBeenCalled();
        expect(component['state']).toBe('completed');
    });

    it('reports a signature still waiting for the other signers', () => {
        const component = create();
        signatures.getLinkAct.and.returnValues(of(link(act({ pendingPurposes: ['ACCEPTANCE'], type: 'LOAN_SHEET_VARIATION' }))), of(link(act({ pendingPurposes: [], type: 'LOAN_SHEET_VARIATION' }))));
        flow.openAct.and.callFake((value: SignatureAct) => of({ act: value, request: { purpose: 'ACCEPTANCE', items: [] } }));
        flow.confirmWith.and.returnValue(of({ challengeId: 1, acts: [{ actId: 5, type: 'LOAN_SHEET_VARIATION', status: 'AWAITING_SIGNATURES' }] }));
        component.ngOnInit();

        component['sign']();

        expect(component['state']).toBe('ready');
        expect(component['waitingForOthers']).toBeTrue();
        expect(toast.success).toHaveBeenCalledWith('Firma registrata', jasmine.any(String));
    });

    it('reloads the act when the window closes without a signature', () => {
        const component = create();
        signatures.getLinkAct.and.returnValues(of(link(act({ dataRequired: true, status: 'AWAITING_DATA', pendingPurposes: [] }))), of(link(act())));
        flow.openAct.and.callFake((value: SignatureAct) => of({ act: value }));
        component.ngOnInit();
        expect(component['signLabel']).toBe('Inserisci i dati e firma');

        component['sign']();

        expect(flow.confirmWith).not.toHaveBeenCalled();
        expect(component['act']?.pendingPurposes).toEqual(['ACCEPTANCE', 'CLAUSE_APPROVAL']);
        expect(toast.success).not.toHaveBeenCalled();
    });

    it('recognizes the public page, where the application starts without login', () => {
        expect(publicSignaturePage('/firma/' + 'A'.repeat(43))).toBeTrue();
        expect(publicSignaturePage('/inventory')).toBeFalse();
        expect(publicSignaturePage('/firmare')).toBeFalse();
    });
});
