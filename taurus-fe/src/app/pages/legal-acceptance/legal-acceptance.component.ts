import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize, first } from 'rxjs';
import { LegalTextComponent } from '../../components/legal-text/legal-text.component';
import { ImportsModule } from '../../imports';
import { LegalDocumentStatus, LegalStatus } from '../../module';
import { legalDocumentPath, LegalService, ToastService, withHandledErrors } from '../../service';

/** Testo di un documento da confermare, letto dall'API pubblica. */
interface DocumentText {
    loading: boolean;
    html?: string | null;
    failed?: boolean;
}

@Component({
    selector: 'app-legal-acceptance',
    standalone: true,
    imports: [ImportsModule, LegalTextComponent],
    templateUrl: './legal-acceptance.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './legal-acceptance.component.scss'
})
export class LegalAcceptanceComponent implements OnInit {
    status?: LegalStatus;
    confirmations: Record<number, boolean> = {};
    texts: Record<number, DocumentText> = {};
    loading = true;
    submitting = false;
    errorMessage?: string;

    constructor(
        private readonly legalService: LegalService,
        private readonly route: ActivatedRoute,
        private readonly router: Router,
        private readonly toastService: ToastService
    ) {}

    ngOnInit(): void {
        this.loadStatus(true);
    }

    get pendingDocuments(): LegalDocumentStatus[] {
        return this.status?.documents.filter((document) => document.required && !document.accepted) ?? [];
    }

    get canSubmit(): boolean {
        return this.pendingDocuments.length > 0 && this.pendingDocuments.every((document) => this.confirmations[document.id]);
    }

    confirmationLabel(document: LegalDocumentStatus): string {
        return document.action === 'ACKNOWLEDGE' ? `Dichiaro di aver preso visione di ${document.title}.` : `Dichiaro di aver letto e accetto ${document.title}.`;
    }

    /** Indirizzo permanente della versione da confermare, da aprire in una nuova scheda. */
    documentPath(document: LegalDocumentStatus): string {
        return legalDocumentPath(document.documentType, document.version);
    }

    /** «Cosa cambia» serve solo a chi aveva confermato un'altra versione dello stesso documento. */
    showsChanges(document: LegalDocumentStatus): boolean {
        return document.previousVersionAccepted && !!document.changeSummary?.trim();
    }

    submit(): void {
        if (!this.canSubmit || this.submitting) {
            return;
        }

        this.submitting = true;
        this.errorMessage = undefined;
        const documentIds = this.pendingDocuments.map((document) => document.id);
        this.legalService
            .accept(documentIds)
            .pipe(
                first(),
                finalize(() => (this.submitting = false))
            )
            .subscribe({
                next: (status) => {
                    this.status = status;
                    this.toastService.success('Documenti legali', 'Le tue scelte sono state registrate.');
                    void this.router.navigateByUrl(this.safeReturnUrl());
                },
                error: () => {
                    this.errorMessage = 'Non è stato possibile registrare le scelte. Ricarica i documenti e riprova.';
                }
            });
    }

    retry(): void {
        this.loadStatus(true);
    }

    loadText(document: LegalDocumentStatus): void {
        this.texts[document.id] = { loading: true };
        this.legalService
            .getPublicDocument(document.documentType, document.version, withHandledErrors(['*']))
            .pipe(first())
            .subscribe({
                next: (published) => (this.texts[document.id] = { loading: false, html: published.html }),
                error: () => (this.texts[document.id] = { loading: false, failed: true })
            });
    }

    private loadStatus(forceRefresh: boolean): void {
        this.loading = true;
        this.errorMessage = undefined;
        this.legalService
            .getStatus(forceRefresh)
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe({
                next: (status) => {
                    this.status = status;
                    this.confirmations = {};
                    this.texts = {};
                    if (status.compliant) {
                        void this.router.navigateByUrl(this.safeReturnUrl());
                        return;
                    }
                    // A document published before its text lived in Taurus keeps the link to its address.
                    this.pendingDocuments.filter((document) => !document.url).forEach((document) => this.loadText(document));
                },
                error: () => {
                    this.errorMessage = 'Non è stato possibile caricare i documenti legali richiesti.';
                }
            });
    }

    private safeReturnUrl(): string {
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        return returnUrl?.startsWith('/') && !returnUrl.startsWith('/legal/accept') ? returnUrl : '/';
    }
}
