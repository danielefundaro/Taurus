import { DatePipe } from '@angular/common';
import { Component, OnInit, ViewChild } from '@angular/core';
import { SelectItem } from 'primeng/api';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Table } from 'primeng/table';
import { finalize, first } from 'rxjs';
import { LegalDocumentDialogComponent } from '../../dialogs/legal-document-dialog/legal-document-dialog.component';
import { ImportsModule } from '../../imports';
import { LegalDocument, LegalDocumentAction, LegalDocumentType } from '../../module';
import { LegalService, ToastService } from '../../service';

@Component({
    selector: 'app-legal-documents',
    standalone: true,
    imports: [ImportsModule, DatePipe],
    templateUrl: './legal-documents.component.html',
    styleUrl: './legal-documents.component.scss',
    providers: [DialogService]
})
export class LegalDocumentsComponent implements OnInit {
    @ViewChild('documentTable') private readonly documentTable?: Table;

    protected documents: LegalDocument[] = [];
    protected searchTerm = '';
    protected loading = false;
    protected saving = false;

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
        private readonly toastService: ToastService,
        private readonly dialogService: DialogService
    ) {}

    protected onSearchChange(value: string): void {
        this.searchTerm = value;
        this.documentTable?.filterGlobal(value, 'contains');
    }

    ngOnInit(): void {
        this.loadDocuments();
    }

    protected addDocument(): void {
        this.openDialog();
    }

    protected editDocument(document: LegalDocument): void {
        this.openDialog(document);
    }

    private openDialog(document?: LegalDocument): void {
        const ref: DynamicDialogRef = this.dialogService.open(LegalDocumentDialogComponent, {
            inputValues: { document: document ? { ...document } : undefined },
            closable: true,
            modal: true,
            showHeader: false,
            width: '40rem',
            breakpoints: { '960px': '75vw', '640px': '94vw' }
        });

        ref.onClose.pipe(first()).subscribe((result?: LegalDocument) => {
            if (result) this.saveDocument(result);
        });
    }

    private saveDocument(document: LegalDocument): void {
        if (this.saving) return;
        this.saving = true;
        const request = document.id ? this.legalService.updateDocument(document.id, document) : this.legalService.createDocument(document);

        request
            .pipe(
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe({
                next: () => {
                    this.toastService.success('Documenti legali', document.id ? 'Documento aggiornato.' : 'Documento creato.');
                    this.loadDocuments();
                },
                error: () => {
                    this.toastService.error('Salvataggio non riuscito', 'Se il documento è già stato accettato, crea una nuova versione invece di modificarne il contenuto.');
                }
            });
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
}
