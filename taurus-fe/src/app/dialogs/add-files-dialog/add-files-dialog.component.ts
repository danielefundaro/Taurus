import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { FileUploadModule } from 'primeng/fileupload';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { KeycloakService, TracksService } from '../../service';
import { PdfAnnotations } from '../../module/pdf-annotations.module';
import { PdfManipulatorDialogComponent } from '../pdf-manipulator-dialog/pdf-manipulator-dialog.component';
import { first } from 'rxjs';
import { DialogShellComponent } from '../../components/dialog-shell/dialog-shell.component';

@Component({
    selector: 'app-add-files-dialog',
    standalone: true,
    imports: [CommonModule, ButtonModule, FileUploadModule, DialogShellComponent],
    providers: [TracksService, KeycloakService, DialogService],
    templateUrl: './add-files-dialog.component.html',
    styleUrl: './add-files-dialog.component.scss'
})
export class AddFilesDialogComponent {
    protected selectedFile: File | null = null;
    protected annotations: PdfAnnotations | null = null;
    protected uploading = false;

    constructor(
        private readonly dialogRef: DynamicDialogRef,
        private readonly dialogService: DialogService,
        private readonly tracksService: TracksService
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
            data: { file: this.selectedFile, annotations: this.annotations },
            contentStyle: {
                overflow: 'hidden',
                padding: '0',
                display: 'flex',
                flexDirection: 'column',
                height: 'calc(90vh - 54px)'
            }
        });
        ref.onClose.pipe(first()).subscribe((result: PdfAnnotations | null | undefined) => {
            if (result !== null && result !== undefined) {
                this.annotations = result;
            }
        });
    }

    protected upload(): void {
        const file = this.selectedFile;
        if (!file) return;

        this.uploading = true;
        const formData = new FormData();
        formData.append('file', file);
        if (this.hasAnnotations) {
            formData.append('annotations', JSON.stringify(this.annotations));
        }

        this.tracksService.uploadPdf(formData).subscribe({
            next: (job) => {
                this.uploading = false;
                this.dialogRef.close(job);
            },
            error: () => {
                this.uploading = false;
            }
        });
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    protected get hasAnnotations(): boolean {
        return !!(this.annotations && (this.annotations.excludedPages.length > 0 || this.annotations.cropRegions.length > 0 || this.annotations.pageTransforms.length > 0));
    }
}
