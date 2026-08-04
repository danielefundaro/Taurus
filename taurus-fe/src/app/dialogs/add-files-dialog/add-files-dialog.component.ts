import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { FileUploadModule } from 'primeng/fileupload';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { KeycloakService, TracksService } from '../../service';
import { PdfAnnotations } from '../../module/pdf-annotations.module';
import { PdfManipulatorDialogComponent } from '../pdf-manipulator-dialog/pdf-manipulator-dialog.component';
import { first } from 'rxjs';

@Component({
    selector: 'app-add-files-dialog',
    standalone: true,
    imports: [
        CommonModule,
        ButtonModule,
        FileUploadModule,
    ],
    providers: [
        TracksService,
        KeycloakService,
        DialogService,
    ],
    templateUrl: './add-files-dialog.component.html',
    styleUrl: './add-files-dialog.component.scss',
})
export class AddFilesDialogComponent {
    protected selectedFile: File | null = null;
    protected annotations: PdfAnnotations | null = null;
    protected uploading = false;

    constructor(
        private readonly dialogRef: DynamicDialogRef,
        private readonly dialogService: DialogService,
        private readonly tracksService: TracksService,
        private readonly keycloakService: KeycloakService,
        private readonly http: HttpClient,
    ) {}

    protected onFileSelect(event: any): void {
        this.selectedFile = event.currentFiles?.[0] ?? event.files?.[0] ?? null;
        this.annotations = null;
    }

    protected onFileClear(): void {
        this.selectedFile = null;
        this.annotations = null;
    }

    protected openManipulator(): void {
        if (!this.selectedFile) return;
        const ref = this.dialogService.open(PdfManipulatorDialogComponent, {
            header: 'Manipolazione PDF',
            width: '90vw',
            height: '90vh',
            focusTrap: false,
            focusOnShow: false,
            data: { file: this.selectedFile },
            contentStyle: {
                overflow: 'hidden',
                padding: '0',
                display: 'flex',
                flexDirection: 'column',
                height: 'calc(90vh - 54px)',
            },
        });
        ref.onClose.pipe(first()).subscribe((result: PdfAnnotations | null | undefined) => {
            if (result !== null && result !== undefined) {
                this.annotations = result;
            }
        });
    }

    protected handleUpload(event: any): void {
        const file: File = event.files?.[0] ?? this.selectedFile;
        if (!file) return;

        this.uploading = true;
        const formData = new FormData();
        formData.append('file', file);
        if (this.annotations && (this.annotations.excludedPages.length > 0 || this.annotations.cropRegions.length > 0)) {
            formData.append('annotations', JSON.stringify(this.annotations));
        }

        const headers = new HttpHeaders({ 'Authorization': `Bearer ${this.keycloakService.token}` });
        this.http.post(this.tracksService.stream(), formData, { headers }).subscribe({
            next: () => {
                this.uploading = false;
                this.dialogRef.close(true);
            },
            error: () => {
                this.uploading = false;
            },
        });
    }

    protected get hasAnnotations(): boolean {
        return !!(this.annotations && (
            this.annotations.excludedPages.length > 0 ||
            this.annotations.cropRegions.length > 0
        ));
    }
}
