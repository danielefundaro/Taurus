import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { DialogService } from 'primeng/dynamicdialog';
import { finalize, first } from 'rxjs';
import {
    shortHash,
    signatureActStatusLabel,
    signatureActStatusSeverity,
    signatureActTypeLabel,
    signatureDocumentLabel,
    signatureMethodLabel,
    signaturePurposeLabel,
    signatureRevocationLabel,
    signerRoleLabel,
    signerStatusLabel
} from '../../constants';
import { SignatureActFlow } from '../../dialogs/signature-act-dialog/signature-act-flow';
import { SignaturePaperDialogComponent, SignaturePaperDialogData } from '../../dialogs/signature-paper-dialog/signature-paper-dialog.component';
import { SignatureSignerDialogComponent, SignatureSignerDialogData } from '../../dialogs/signature-signer-dialog/signature-signer-dialog.component';
import { ImportsModule } from '../../imports';
import { SignatureAct, SignatureActSigner, SignatureDocumentRole, SignatureEvidence } from '../../module';
import { ConfirmService, InventoryService, ToastService, withErrorSummary } from '../../service';

/**
 * Acts of a loan sheet signed electronically, for the administrators: state of every signer, signature for the
 * association, change of the signer, links of a parent or guardian, declaration of paper signature, documents, evidence
 * and the dossier the tenant keeps.
 */
@Component({
    selector: 'app-loan-sheet-signatures',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './loan-sheet-signatures.component.html',
    providers: [DialogService, SignatureActFlow]
})
export class LoanSheetSignaturesComponent implements OnChanges {
    @Input({ required: true }) sheetId!: number;
    @Input({ required: true }) userIndex!: number;
    @Input({ required: true }) sheetNumber!: string;
    /** Emitted when an act changed in a way that changes the sheet, for example a return report that settles it. */
    @Output() changed = new EventEmitter<void>();

    protected acts: SignatureAct[] = [];
    protected evidence: Record<number, SignatureEvidence[]> = {};
    protected loading = false;
    protected downloading = false;
    /** Signer whose link is being sent or revoked. */
    protected linkBusy?: number;
    protected readonly typeLabel = signatureActTypeLabel;
    protected readonly statusLabel = signatureActStatusLabel;
    protected readonly statusSeverity = signatureActStatusSeverity;
    protected readonly roleLabel = signerRoleLabel;
    protected readonly signerStatusLabel = signerStatusLabel;
    protected readonly purposeLabel = signaturePurposeLabel;
    protected readonly methodLabel = signatureMethodLabel;
    protected readonly revocationLabel = signatureRevocationLabel;
    protected readonly documentLabel = signatureDocumentLabel;
    protected readonly shortHash = shortHash;

    constructor(
        private readonly inventoryService: InventoryService,
        private readonly toastService: ToastService,
        private readonly confirmService: ConfirmService,
        private readonly dialogService: DialogService,
        private readonly flow: SignatureActFlow
    ) {}

    ngOnChanges(): void {
        this.load();
    }

    /** Date when Taurus removes the signed acts, once the sheet is settled or cancelled. */
    protected get purgeAfter(): string | undefined {
        return this.acts.find((act) => act.purgeAfter && !act.purgedAt)?.purgeAfter;
    }

    protected get dossierAvailable(): boolean {
        return this.acts.some((act) => (act.status === 'SIGNED' || act.status === 'SIGNED_ON_PAPER') && !act.purgedAt);
    }

    protected canSign(act: SignatureAct): boolean {
        return act.pendingPurposes.includes('ASSOCIATION_SIGNATURE');
    }

    protected awaiting(act: SignatureAct): boolean {
        return act.status === 'AWAITING_DATA' || act.status === 'AWAITING_SIGNATURES';
    }

    /** Signers without an account, who sign from a link: a parent or guardian. */
    protected linkSigners(act: SignatureAct): SignatureActSigner[] {
        return act.signers.filter((signer) => !signer.userId && !!signer.maskedEmail);
    }

    /** A new link lets the parent sign an act still waiting or download the documents of a signed one. */
    protected canResendLink(act: SignatureAct, signer: SignatureActSigner): boolean {
        return (this.awaiting(act) && signer.status === 'PENDING') || (act.status === 'SIGNED' && !act.purgedAt);
    }

    protected resendLink(act: SignatureAct, signer: SignatureActSigner): void {
        this.linkBusy = signer.id;
        this.inventoryService
            .resendSignatureLink(act.id, signer.id, withErrorSummary('Link non inviato'))
            .pipe(
                first(),
                finalize(() => (this.linkBusy = undefined))
            )
            .subscribe(() => {
                this.toastService.success('Link inviato', `Il nuovo link è stato inviato a ${signer.maskedEmail}; quello precedente non vale più.`);
                this.load();
            });
    }

    protected revokeLink(act: SignatureAct, signer: SignatureActSigner): void {
        this.confirmService.confirmReversible({
            title: 'Revoca link',
            consequence: `Il link inviato a ${signer.maskedEmail} smetterà di funzionare: per firmare servirà un nuovo link.`,
            actionLabel: 'Revoca link',
            accept: () => {
                this.linkBusy = signer.id;
                this.inventoryService
                    .revokeSignatureLink(act.id, signer.id, withErrorSummary('Link non revocato'))
                    .pipe(
                        first(),
                        finalize(() => (this.linkBusy = undefined))
                    )
                    .subscribe(() => {
                        this.toastService.success('Link revocato', 'La pagina di firma non si apre più con quel link.');
                        this.load();
                    });
            }
        });
    }

    protected sign(act: SignatureAct): void {
        this.flow
            .sign(act)
            .pipe(first())
            .subscribe(() => this.reload());
    }

    protected changeSigner(act: SignatureAct): void {
        this.inventoryService
            .getAssociationSigners(this.userIndex)
            .pipe(first())
            .subscribe((options) => {
                const data: SignatureSignerDialogData = { act, options };
                this.dialogService
                    .open(SignatureSignerDialogComponent, { header: 'Cambia firmatario', showHeader: false, modal: true, width: '32rem', breakpoints: { '767px': 'calc(100vw - 1rem)' }, data })
                    .onClose.pipe(first())
                    .subscribe((userId?: string) => {
                        if (!userId) return;
                        this.inventoryService
                            .changeAssociationSigner(act.id, userId, act.entityVersion, withErrorSummary('Firmatario non cambiato'))
                            .pipe(first())
                            .subscribe((updated) => {
                                this.toastService.success('Firmatario cambiato', updated.status === 'AWAITING_DATA' ? 'Il socio deve inserire di nuovo i dati prima delle firme.' : 'Il nuovo firmatario riceve la richiesta di firma.');
                                this.load();
                            });
                    });
            });
    }

    protected declarePaper(act: SignatureAct): void {
        const data: SignaturePaperDialogData = { act };
        this.dialogService
            .open(SignaturePaperDialogComponent, { header: 'Firmato su carta', showHeader: false, modal: true, width: '34rem', breakpoints: { '767px': 'calc(100vw - 1rem)' }, data })
            .onClose.pipe(first())
            .subscribe((note?: string) => {
                if (!note) return;
                this.flow
                    .confirm({ purpose: 'PAPER_DECLARATION', items: [{ actId: act.id, note }] }, act)
                    .pipe(first())
                    .subscribe((confirmed) => {
                        if (confirmed) this.toastService.success('Atto firmato su carta', 'La dichiarazione è registrata con le prove dell’atto.');
                        this.reload();
                    });
            });
    }

    protected toggleEvidence(act: SignatureAct): void {
        if (this.evidence[act.id]) {
            delete this.evidence[act.id];
            return;
        }
        this.inventoryService
            .getSignatureEvidence(act.id)
            .pipe(first())
            .subscribe((evidence) => (this.evidence[act.id] = evidence));
    }

    protected openDocument(act: SignatureAct, role: SignatureDocumentRole): void {
        this.inventoryService
            .downloadSignatureDocument(act.id, role)
            .pipe(first())
            .subscribe((blob) => this.save(blob, `foglio-${this.fileNumber}-atto-${act.id}-${role === 'CLAUSES' ? 'clausole' : 'documento'}.pdf`));
    }

    protected openAttestation(act: SignatureAct): void {
        this.inventoryService
            .downloadSignatureAttestation(act.id)
            .pipe(first())
            .subscribe((blob) => this.save(blob, `foglio-${this.fileNumber}-atto-${act.id}-attestazione.pdf`));
    }

    protected downloadDossier(): void {
        this.downloading = true;
        this.inventoryService
            .downloadSignatureDossier(this.sheetId, withErrorSummary('Fascicolo non disponibile'))
            .pipe(
                first(),
                finalize(() => (this.downloading = false))
            )
            .subscribe((blob) => this.save(blob, `fascicolo-foglio-${this.fileNumber}.zip`));
    }

    private get fileNumber(): string {
        return this.sheetNumber.replace('/', '-');
    }

    private reload(): void {
        this.load();
        this.changed.emit();
    }

    private load(): void {
        if (!this.sheetId) return;
        this.loading = true;
        this.evidence = {};
        this.inventoryService
            .getSignatureActs(this.sheetId)
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe((acts) => (this.acts = acts));
    }

    private save(blob: Blob, fileName: string): void {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = fileName;
        anchor.click();
        URL.revokeObjectURL(url);
    }
}
