import { DialogService } from 'primeng/dynamicdialog';
import { of } from 'rxjs';
import { SignatureActFlow } from '../../dialogs/signature-act-dialog/signature-act-flow';
import { SignatureAct, SignatureActSigner } from '../../module';
import { ConfirmRequest, ConfirmService, InventoryService, ToastService } from '../../service';
import { LoanSheetSignaturesComponent } from './loan-sheet-signatures.component';

describe('LoanSheetSignaturesComponent', () => {
    const parent: SignatureActSigner = {
        id: 30,
        role: 'HOLDER',
        displayName: 'Giulia Bianchi',
        required: true,
        status: 'PENDING',
        signedPurposes: [],
        maskedEmail: 'g***@example.org',
        link: { fingerprint: 'abcd1234', sentAt: '2026-10-19T10:00:00Z', expiresAt: '2026-10-26T10:00:00Z', active: true }
    };
    const admin: SignatureActSigner = { id: 31, role: 'ASSOCIATION', userId: 'admin-1', displayName: 'Anna Verdi', required: true, status: 'PENDING', signedPurposes: [] };
    const act = (overrides: Partial<SignatureAct> = {}): SignatureAct => ({
        id: 5,
        type: 'LOAN_SHEET_ISSUE',
        status: 'AWAITING_DATA',
        sheetNumber: '2026/007',
        holderRole: 'GUARDIAN',
        operation: 'Emissione del foglio',
        createdAt: '2026-10-19T10:00:00Z',
        documents: [],
        signers: [parent, admin],
        attestationAvailable: false,
        associationSignerChangeable: true,
        dataRequired: false,
        pendingPurposes: [],
        cieSignAvailable: false,
        entityVersion: 1,
        ...overrides
    });
    let inventory: jasmine.SpyObj<InventoryService>;
    let confirm: jasmine.SpyObj<ConfirmService>;
    let toast: jasmine.SpyObj<ToastService>;

    function create(): LoanSheetSignaturesComponent {
        inventory = jasmine.createSpyObj<InventoryService>('InventoryService', ['getSignatureActs', 'resendSignatureLink', 'revokeSignatureLink']);
        inventory.getSignatureActs.and.returnValue(of([act()]));
        confirm = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['confirmReversible']);
        toast = jasmine.createSpyObj<ToastService>('ToastService', ['success']);
        const component = new LoanSheetSignaturesComponent(inventory, toast, confirm, {} as DialogService, {} as SignatureActFlow);
        component.sheetId = 7;
        component.userIndex = 42;
        component.sheetNumber = '2026/007';
        return component;
    }

    it('shows the link of the parent and sends a new one while the act waits', () => {
        const component = create();
        component.ngOnChanges();
        inventory.resendSignatureLink.and.returnValue(of(act()));

        expect(component['linkSigners'](act())).toEqual([parent]);
        expect(component['canResendLink'](act(), parent)).toBeTrue();
        expect(component['canResendLink'](act({ status: 'SIGNED_ON_PAPER' }), parent)).toBeFalse();
        expect(component['canResendLink'](act({ status: 'SIGNED' }), { ...parent, status: 'SIGNED' })).toBeTrue();

        component['resendLink'](act(), parent);

        expect(inventory.resendSignatureLink).toHaveBeenCalledWith(5, 30, jasmine.anything());
        expect(toast.success).toHaveBeenCalledWith('Link inviato', jasmine.stringContaining('g***@example.org'));
        expect(inventory.getSignatureActs).toHaveBeenCalledTimes(2);
    });

    it('revokes the link only after the confirmation', () => {
        const component = create();
        inventory.revokeSignatureLink.and.returnValue(of(act()));

        component['revokeLink'](act(), parent);
        expect(inventory.revokeSignatureLink).not.toHaveBeenCalled();

        (confirm.confirmReversible.calls.mostRecent().args[0] as ConfirmRequest).accept();
        expect(inventory.revokeSignatureLink).toHaveBeenCalledWith(5, 30, jasmine.anything());
        expect(toast.success).toHaveBeenCalledWith('Link revocato', jasmine.any(String));
    });
});
