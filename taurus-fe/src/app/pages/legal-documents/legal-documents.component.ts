import { DatePipe } from '@angular/common';
import { Component, OnInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { Table } from 'primeng/table';
import { finalize, first } from 'rxjs';
import { legalDocumentActionLabel, legalDocumentStateLabel, legalDocumentStateSeverity, legalDocumentTypeLabel, shortHash } from '../../constants';
import { ImportsModule } from '../../imports';
import { LegalDocument } from '../../module';
import { LegalService, withErrorSummary } from '../../service';

/** Elenco delle versioni dei documenti legali; ogni versione si apre nella sua pagina di dettaglio. */
@Component({
    selector: 'app-legal-documents',
    standalone: true,
    imports: [ImportsModule, DatePipe],
    templateUrl: './legal-documents.component.html',
    styleUrl: './legal-documents.component.scss'
})
export class LegalDocumentsComponent implements OnInit {
    @ViewChild('documentTable') private readonly documentTable?: Table;

    protected documents: LegalDocument[] = [];
    protected searchTerm = '';
    protected loading = false;

    protected readonly typeLabel = legalDocumentTypeLabel;
    protected readonly actionLabel = legalDocumentActionLabel;
    protected readonly stateLabel = legalDocumentStateLabel;
    protected readonly stateSeverity = legalDocumentStateSeverity;
    protected readonly shortHash = shortHash;

    constructor(
        private readonly legalService: LegalService,
        private readonly router: Router
    ) { }

    ngOnInit(): void {
        this.loadDocuments();
    }

    protected onSearchChange(value: string): void {
        this.searchTerm = value;
        this.documentTable?.filterGlobal(value, 'contains');
    }

    protected addDocument(): void {
        void this.router.navigate(['/legal-documents/new']);
    }

    /** Documento pubblicato prima che il testo stesse in Taurus: il testo si inserisce una volta. */
    protected toComplete(document: LegalDocument): boolean {
        return !document.contentSha256 && !!document.url;
    }

    private loadDocuments(): void {
        this.loading = true;
        this.legalService
            .getDocuments(withErrorSummary('Caricamento non riuscito'))
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe({
                next: (documents) => (this.documents = documents),
                error: () => undefined
            });
    }
}
