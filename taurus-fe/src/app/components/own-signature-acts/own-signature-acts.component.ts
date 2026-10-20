import { Component, OnInit } from '@angular/core';
import { DialogService } from 'primeng/dynamicdialog';
import { first } from 'rxjs';
import { signatureActStatusLabel, signatureActStatusSeverity, signatureActTypeLabel, signaturePurposeLabel } from '../../constants';
import { SignatureActFlow } from '../../dialogs/signature-act-dialog/signature-act-flow';
import { ImportsModule } from '../../imports';
import { SignatureAct, SignatureDocumentRole, SignaturePurpose } from '../../module';
import { SignatureService } from '../../service';

/**
 * Acts of the loan sheets the signed-in user signs, as holder or for the association: the ones waiting for the user,
 * with the signature window, and the signed ones with their documents and attestation.
 */
@Component({
    selector: 'app-own-signature-acts',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './own-signature-acts.component.html',
    styleUrl: './own-signature-acts.component.scss',
    providers: [DialogService, SignatureActFlow]
})
export class OwnSignatureActsComponent implements OnInit {
    protected acts: SignatureAct[] = [];
    protected selected: number[] = [];
    protected readonly typeLabel = signatureActTypeLabel;
    protected readonly statusLabel = signatureActStatusLabel;
    protected readonly statusSeverity = signatureActStatusSeverity;
    protected readonly purposeLabel = signaturePurposeLabel;

    constructor(
        private readonly signatureService: SignatureService,
        private readonly flow: SignatureActFlow
    ) {}

    ngOnInit(): void {
        this.load();
    }

    protected get pending(): SignatureAct[] {
        return this.acts.filter((act) => act.dataRequired || act.pendingPurposes.length > 0);
    }

    protected get completed(): SignatureAct[] {
        return this.acts.filter((act) => act.status === 'SIGNED' || act.status === 'SIGNED_ON_PAPER');
    }

    /** Variazioni che attendono dall'utente una sola firma: si possono firmare insieme con un codice. */
    protected batchable(act: SignatureAct): boolean {
        return act.type === 'LOAN_SHEET_VARIATION' && act.pendingPurposes.length === 1 && !act.dataRequired;
    }

    protected get batchPurpose(): SignaturePurpose | undefined {
        const purposes = new Set(this.selectedActs.map((act) => act.pendingPurposes[0]));
        return purposes.size === 1 ? [...purposes][0] : undefined;
    }

    protected get selectedActs(): SignatureAct[] {
        return this.pending.filter((act) => this.selected.includes(act.id));
    }

    protected pendingText(act: SignatureAct): string {
        return act.pendingPurposes.map((purpose) => this.purposeLabel(purpose).toLowerCase()).join(' e ');
    }

    protected actionLabel(act: SignatureAct): string {
        if (act.dataRequired) return 'Inserisci i dati e firma';
        if (act.pendingPurposes.includes('MINOR_ACKNOWLEDGMENT')) return 'Prendi visione';
        return act.pendingPurposes.includes('ASSOCIATION_SIGNATURE') ? 'Firma per l’Associazione' : 'Firma';
    }

    protected sign(act: SignatureAct): void {
        this.flow
            .sign(act)
            .pipe(first())
            .subscribe(() => this.load());
    }

    protected signSelected(): void {
        const purpose = this.batchPurpose;
        const acts = this.selectedActs;
        if (!purpose || acts.length < 2) return;
        this.flow
            .confirm({
                purpose,
                items: acts.map((act) => ({ actId: act.id, documentRole: 'MAIN' as SignatureDocumentRole, documentHash: act.documents.find((document) => document.role === 'MAIN')!.frozenHash }))
            })
            .pipe(first())
            .subscribe(() => this.load());
    }

    protected openDocument(act: SignatureAct, role: SignatureDocumentRole): void {
        this.signatureService
            .downloadDocument(act.id, role)
            .pipe(first())
            .subscribe((blob) => this.save(blob, `foglio-${(act.sheetNumber ?? '').replace('/', '-')}-${role === 'CLAUSES' ? 'clausole' : 'atto-' + act.id}.pdf`));
    }

    protected openAttestation(act: SignatureAct): void {
        this.signatureService
            .downloadAttestation(act.id)
            .pipe(first())
            .subscribe((blob) => this.save(blob, `foglio-${(act.sheetNumber ?? '').replace('/', '-')}-attestazione-${act.id}.pdf`));
    }

    private load(): void {
        this.selected = [];
        this.signatureService
            .getActs()
            .pipe(first())
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
