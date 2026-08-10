import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize, first } from 'rxjs';
import { ImportsModule } from '../../imports';
import { LegalDocumentStatus, LegalStatus } from '../../module';
import { LegalService, ToastService } from '../../service';

@Component({
    selector: 'app-legal-acceptance',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './legal-acceptance.component.html',
    styleUrl: './legal-acceptance.component.scss'
})
export class LegalAcceptanceComponent implements OnInit {
    status?: LegalStatus;
    confirmations: Record<number, boolean> = {};
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
                    if (status.compliant) {
                        void this.router.navigateByUrl(this.safeReturnUrl());
                    }
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
