import { DialogService } from 'primeng/dynamicdialog';
import { of } from 'rxjs';
import { SignatureAct } from '../../module';
import { SignatureService, ToastService } from '../../service';
import { SignatureCodeDialogComponent } from '../signature-code-dialog/signature-code-dialog.component';
import { SignatureActDialogComponent } from './signature-act-dialog.component';
import { SignatureActFlow } from './signature-act-flow';

describe('SignatureActFlow', () => {
    const act = (pending: SignatureAct['pendingPurposes'], status: SignatureAct['status'] = 'AWAITING_SIGNATURES'): SignatureAct => ({
        id: 5,
        type: 'LOAN_SHEET_ISSUE',
        status,
        sheetNumber: '2026/001',
        operation: 'Emissione del foglio',
        createdAt: '2026-10-19T10:00:00Z',
        documents: [{ role: 'MAIN', frozenHash: 'a'.repeat(64), available: true }],
        signers: [],
        attestationAvailable: false,
        associationSignerChangeable: false,
        dataRequired: false,
        pendingPurposes: pending,
        cieSignAvailable: true,
        entityVersion: 1
    });
    const request = { purpose: 'ACCEPTANCE' as const, items: [{ actId: 5, documentRole: 'MAIN' as const, documentHash: 'a'.repeat(64) }] };

    it('opens the act again until the user has no signature left, then confirms the outcome', () => {
        const dialogService = jasmine.createSpyObj<DialogService>('DialogService', ['open']);
        const signatureService = jasmine.createSpyObj<SignatureService>('SignatureService', ['getAct']);
        const toastService = jasmine.createSpyObj<ToastService>('ToastService', ['success']);
        const first = act(['ACCEPTANCE', 'CLAUSE_APPROVAL']);
        const second = act(['CLAUSE_APPROVAL']);
        const done = act([], 'SIGNED');
        const closes = [{ act: first, request }, { confirmation: { challengeId: 1, acts: [] } }, { act: second, request: { ...request, purpose: 'CLAUSE_APPROVAL' as const } }, { confirmation: { challengeId: 2, acts: [] } }];
        dialogService.open.and.callFake(() => ({ onClose: of(closes.shift()) }) as never);
        signatureService.getAct.and.returnValues(of(second), of(done));
        let result: SignatureAct | undefined;

        new SignatureActFlow(dialogService, signatureService, toastService).sign(first).subscribe((value) => (result = value));

        expect(dialogService.open.calls.allArgs().map((args) => args[0])).toEqual([SignatureActDialogComponent, SignatureCodeDialogComponent, SignatureActDialogComponent, SignatureCodeDialogComponent]);
        expect(result).toEqual(done);
        expect(toastService.success).toHaveBeenCalledWith('Atto firmato', jasmine.any(String));
    });

    it('goes on without the code window when the document is signed with CieSign', () => {
        const dialogService = jasmine.createSpyObj<DialogService>('DialogService', ['open']);
        const signatureService = jasmine.createSpyObj<SignatureService>('SignatureService', ['getAct']);
        const toastService = jasmine.createSpyObj<ToastService>('ToastService', ['success']);
        const value = act(['ACCEPTANCE']);
        const waiting = act([]);
        dialogService.open.and.returnValue({ onClose: of({ act: value, outcome: { actId: 5, type: 'LOAN_SHEET_ISSUE', status: 'AWAITING_SIGNATURES' } }) } as never);
        signatureService.getAct.and.returnValue(of(waiting));
        let result: SignatureAct | undefined;

        new SignatureActFlow(dialogService, signatureService, toastService).sign(value).subscribe((next) => (result = next));

        expect(dialogService.open).toHaveBeenCalledOnceWith(SignatureActDialogComponent, jasmine.any(Object));
        expect(result).toEqual(waiting);
        expect(toastService.success).toHaveBeenCalledWith('Firma registrata', jasmine.any(String));
    });

    it('stops without changes when the act window is closed', () => {
        const dialogService = jasmine.createSpyObj<DialogService>('DialogService', ['open']);
        const signatureService = jasmine.createSpyObj<SignatureService>('SignatureService', ['getAct']);
        const value = act(['ACCEPTANCE']);
        dialogService.open.and.returnValue({ onClose: of({ act: value }) } as never);
        let result: SignatureAct | undefined = value;

        new SignatureActFlow(dialogService, signatureService, jasmine.createSpyObj<ToastService>('ToastService', ['success'])).sign(value).subscribe((next) => (result = next));

        expect(result).toBeUndefined();
        expect(signatureService.getAct).not.toHaveBeenCalled();
    });
});
