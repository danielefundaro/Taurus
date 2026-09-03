import { Component } from '@angular/core';
import { SelectItem } from 'primeng/api';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ImportsModule } from '../../imports';
import { LegalDocument, LegalDocumentAction, LegalDocumentType } from '../../module';

@Component({
    selector: 'app-legal-document-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './legal-document-dialog.component.html'
})
export class LegalDocumentDialogComponent {
    protected readonly document: LegalDocument;
    protected publishedAt: Date;

    protected readonly documentTypes: SelectItem<LegalDocumentType>[] = [
        { label: 'Termini di utilizzo', value: 'TERMS' },
        { label: 'Informativa Privacy', value: 'PRIVACY' }
    ];

    protected readonly actions: SelectItem<LegalDocumentAction>[] = [
        { label: 'Accettazione', value: 'ACCEPT' },
        { label: 'Presa visione', value: 'ACKNOWLEDGE' }
    ];

    constructor(
        private readonly dialogRef: DynamicDialogRef<LegalDocumentDialogComponent>,
        private readonly config: DynamicDialogConfig<any, { document?: LegalDocument }>
    ) {
        const existing = this.config.inputValues?.document;
        this.document = existing ? { ...existing } : { documentType: 'TERMS', version: '', title: '', url: '', action: 'ACCEPT', active: false, required: true };
        this.publishedAt = existing?.publishedAt ? new Date(existing.publishedAt) : new Date();
    }

    protected get editing(): boolean {
        return !!this.document.id;
    }

    protected get versionError(): string | undefined {
        return this.document.version.trim() ? undefined : 'Indica la versione del documento.';
    }

    protected get titleError(): string | undefined {
        return this.document.title.trim() ? undefined : 'Indica il titolo del documento.';
    }

    protected get urlError(): string | undefined {
        return this.document.url.trim() ? undefined : 'Indica l’URL pubblico del documento.';
    }

    protected get invalidCount(): number {
        return (this.versionError ? 1 : 0) + (this.titleError ? 1 : 0) + (this.urlError ? 1 : 0) + (this.publishedAt ? 0 : 1);
    }

    protected documentTypeChanged(documentType: LegalDocumentType): void {
        this.document.action = documentType === 'PRIVACY' ? 'ACKNOWLEDGE' : 'ACCEPT';
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    protected save(): void {
        if (this.invalidCount > 0) return;
        this.dialogRef.close({
            ...this.document,
            version: this.document.version.trim(),
            title: this.document.title.trim(),
            url: this.document.url.trim(),
            publishedAt: this.publishedAt.toISOString()
        } satisfies LegalDocument);
    }
}
