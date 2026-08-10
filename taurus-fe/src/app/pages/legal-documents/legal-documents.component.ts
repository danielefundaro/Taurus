import { DatePipe } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { SelectItem } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { finalize, first } from 'rxjs';
import { ImportsModule } from '../../imports';
import { LegalDocument, LegalDocumentAction, LegalDocumentType } from '../../module';
import { LegalService, ToastService } from '../../service';

@Component({
    selector: 'app-legal-documents',
    standalone: true,
    imports: [ImportsModule, DialogModule, DatePipe],
    templateUrl: './legal-documents.component.html',
    styleUrl: './legal-documents.component.scss'
})
export class LegalDocumentsComponent implements OnInit {
    protected documents: LegalDocument[] = [];
    protected loading = false;
    protected saving = false;
    protected dialogVisible = false;
    protected editingDocument: LegalDocument = this.emptyDocument();
    protected publishedAt = new Date();

    protected readonly documentTypes: SelectItem<LegalDocumentType>[] = [
        { label: 'Termini di utilizzo', value: 'TERMS' },
        { label: 'Informativa Privacy', value: 'PRIVACY' }
    ];

    protected readonly actions: SelectItem<LegalDocumentAction>[] = [
        { label: 'Accettazione', value: 'ACCEPT' },
        { label: 'Presa visione', value: 'ACKNOWLEDGE' }
    ];

    constructor(
        private readonly legalService: LegalService,
        private readonly toastService: ToastService
    ) {}

    ngOnInit(): void {
        this.loadDocuments();
    }

    protected addDocument(): void {
        this.editingDocument = this.emptyDocument();
        this.publishedAt = new Date();
        this.dialogVisible = true;
    }

    protected editDocument(document: LegalDocument): void {
        this.editingDocument = { ...document };
        this.publishedAt = document.publishedAt ? new Date(document.publishedAt) : new Date();
        this.dialogVisible = true;
    }

    protected documentTypeChanged(documentType: LegalDocumentType): void {
        this.editingDocument.action = documentType === 'PRIVACY' ? 'ACKNOWLEDGE' : 'ACCEPT';
    }

    protected saveDocument(): void {
        if (!this.isValid || this.saving) {
            return;
        }

        this.saving = true;
        const document: LegalDocument = {
            ...this.editingDocument,
            version: this.editingDocument.version.trim(),
            title: this.editingDocument.title.trim(),
            url: this.editingDocument.url.trim(),
            publishedAt: this.publishedAt.toISOString()
        };
        const request = document.id ? this.legalService.updateDocument(document.id, document) : this.legalService.createDocument(document);

        request
            .pipe(
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe({
                next: () => {
                    this.dialogVisible = false;
                    this.toastService.success('Documenti legali', document.id ? 'Documento aggiornato.' : 'Documento creato.');
                    this.loadDocuments();
                },
                error: () => {
                    this.toastService.error('Salvataggio non riuscito', 'Se il documento è già stato accettato, crea una nuova versione invece di modificarne il contenuto.');
                }
            });
    }

    protected get isValid(): boolean {
        return Boolean(this.editingDocument.documentType && this.editingDocument.action && this.editingDocument.version.trim() && this.editingDocument.title.trim() && this.editingDocument.url.trim() && this.publishedAt);
    }

    protected typeLabel(type: LegalDocumentType): string {
        return this.documentTypes.find((item) => item.value === type)?.label ?? type;
    }

    protected actionLabel(action: LegalDocumentAction): string {
        return this.actions.find((item) => item.value === action)?.label ?? action;
    }

    private loadDocuments(): void {
        this.loading = true;
        this.legalService
            .getDocuments()
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe({
                next: (documents) => (this.documents = documents),
                error: () => this.toastService.error('Errore', 'Impossibile caricare i documenti legali.')
            });
    }

    private emptyDocument(): LegalDocument {
        return {
            documentType: 'TERMS',
            version: '',
            title: '',
            url: '',
            action: 'ACCEPT',
            active: false,
            required: true
        };
    }
}
