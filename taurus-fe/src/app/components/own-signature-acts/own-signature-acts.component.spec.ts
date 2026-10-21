import { of } from 'rxjs';
import { SignatureAct, SignatureActSigner } from '../../module';
import { SignatureActFlow } from '../../dialogs/signature-act-dialog/signature-act-flow';
import { SignatureService } from '../../service';
import { OwnSignatureActsComponent } from './own-signature-acts.component';

describe('OwnSignatureActsComponent', () => {
    const act = (id: number, overrides: Partial<SignatureAct> = {}): SignatureAct => ({
        id,
        type: 'LOAN_SHEET_VARIATION',
        status: 'AWAITING_SIGNATURES',
        sheetNumber: '2026/001',
        operation: 'Consegna successiva: Leggio',
        createdAt: '2026-10-19T10:00:00Z',
        documents: [{ role: 'MAIN', frozenHash: String(id).repeat(64).slice(0, 64), available: true }],
        signers: [],
        attestationAvailable: false,
        associationSignerChangeable: false,
        dataRequired: false,
        pendingPurposes: ['ACCEPTANCE'],
        cieSignAvailable: false,
        entityVersion: 1,
        ...overrides
    });

    function create(acts: SignatureAct[]): { component: OwnSignatureActsComponent; flow: jasmine.SpyObj<SignatureActFlow>; signatureService: jasmine.SpyObj<SignatureService> } {
        const signatureService = jasmine.createSpyObj<SignatureService>('SignatureService', ['getActs', 'downloadDocument', 'downloadAttestation']);
        signatureService.getActs.and.returnValue(of(acts));
        const flow = jasmine.createSpyObj<SignatureActFlow>('SignatureActFlow', ['sign', 'confirm']);
        const component = new OwnSignatureActsComponent(signatureService, flow);
        component.ngOnInit();
        return { component, flow, signatureService };
    }

    it('separates the acts waiting for the user, the ones waiting for the other parties and the signed ones', () => {
        const issue = act(1, { type: 'LOAN_SHEET_ISSUE', status: 'AWAITING_DATA', dataRequired: true, pendingPurposes: [] });
        const signed = act(2, { status: 'SIGNED', pendingPurposes: [], attestationAvailable: true });
        const waitingForOthers = act(3, { pendingPurposes: [] });
        const { component } = create([issue, signed, waitingForOthers]);

        expect(component['pending']).toEqual([issue]);
        expect(component['waiting']).toEqual([waitingForOthers]);
        expect(component['completed']).toEqual([signed]);
        expect(component['actionLabel'](issue)).toBe('Inserisci i dati e firma');
        expect(component['actionLabel'](act(4, { pendingPurposes: ['ASSOCIATION_SIGNATURE'] }))).toBe('Firma per l’Associazione');
    });

    it('names the required signers still missing once the user has signed', () => {
        const signer = (id: number, role: SignatureActSigner['role'], status: SignatureActSigner['status'], required = true): SignatureActSigner => ({
            id,
            role,
            displayName: `Firmatario ${id}`,
            required,
            status,
            signedPurposes: []
        });
        const issue = act(1, {
            type: 'LOAN_SHEET_ISSUE',
            pendingPurposes: [],
            signers: [signer(1, 'HOLDER', 'SIGNED'), signer(2, 'ASSOCIATION', 'PENDING'), signer(3, 'MINOR', 'PENDING', false)]
        });
        const { component } = create([issue]);

        expect(component['waiting']).toEqual([issue]);
        expect(component['missingSigners'](issue)).toBe('Per l’Associazione (Firmatario 2)');
    });

    it('signs several variations with one code and reloads the acts', () => {
        const first = act(1);
        const second = act(2);
        const { component, flow, signatureService } = create([first, second, act(3, { type: 'LOAN_SHEET_RETURN' })]);
        flow.confirm.and.returnValue(of(true));

        expect(component['batchable'](first)).toBeTrue();
        expect(component['batchable'](act(3, { type: 'LOAN_SHEET_RETURN' }))).toBeFalse();
        component['selected'] = [1, 2];
        component['signSelected']();

        expect(flow.confirm).toHaveBeenCalledWith({
            purpose: 'ACCEPTANCE',
            items: [
                { actId: 1, documentRole: 'MAIN', documentHash: first.documents[0].frozenHash },
                { actId: 2, documentRole: 'MAIN', documentHash: second.documents[0].frozenHash }
            ]
        });
        expect(signatureService.getActs).toHaveBeenCalledTimes(2);
        expect(component['selected']).toEqual([]);
    });

    it('keeps out of the signature with one code the variations waiting for acknowledgments', () => {
        const waiting = act(1, { pendingAcknowledgments: 2 });
        const { component } = create([waiting]);

        expect(component['batchable'](waiting)).toBeFalse();
        expect(component['acknowledgmentsText'](waiting)).toBe('Prima della firma prendi visione delle 2 consegne in attesa');
        expect(component['acknowledgmentsText'](act(2, { pendingAcknowledgments: 1 }))).toBe('Prima della firma prendi visione della consegna in attesa');
    });

    it('does not mix different signatures in one code', () => {
        const { component, flow } = create([act(1), act(2, { pendingPurposes: ['ASSOCIATION_SIGNATURE'] })]);
        component['selected'] = [1, 2];

        expect(component['batchPurpose']).toBeUndefined();
        component['signSelected']();
        expect(flow.confirm).not.toHaveBeenCalled();
    });
});
