import { Component, OnDestroy } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { concatMap, first, from, Observable, toArray } from 'rxjs';
import { ImportsModule } from '../../imports';
import { InventoryIssue, InventoryIssueSeverity } from '../../module';
import { InventoryService, ToastService, UserInventoryService } from '../../service';

/** Le fotografie scelte prima dell'invio: il file e l'URL locale dell'anteprima, da revocare quando non serve più. */
interface PendingPhoto {
    file: File;
    previewUrl: string;
}

export const INVENTORY_ISSUE_MAX_PENDING_PHOTOS = 5;
const MAX_PHOTO_BYTES = 10 * 1024 * 1024;

@Component({
    selector: 'app-inventory-issue-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './inventory-issue-dialog.component.html',
    styleUrl: './inventory-issue-dialog.component.scss'
})
export class InventoryIssueDialogComponent implements OnDestroy {
    protected readonly severities = [
        { label: 'Minore — difetto non bloccante', value: 'MINOR' },
        { label: 'Limitante — riparazione necessaria', value: 'LIMITING' },
        { label: 'Non sicuro — possibile rischio', value: 'UNSAFE' }
    ];
    protected readonly maxPhotos = INVENTORY_ISSUE_MAX_PENDING_PHOTOS;
    protected quantity = 1;
    protected severity: InventoryIssueSeverity = 'MINOR';
    protected description = '';
    protected photos: PendingPhoto[] = [];
    protected saving = false;
    private readonly itemId?: number;
    private readonly assignmentId?: number;
    protected readonly maxQuantity: number;

    constructor(
        private readonly dialogRef: DynamicDialogRef<InventoryIssueDialogComponent>,
        config: DynamicDialogConfig<{ itemId?: number; assignmentId?: number; maxQuantity: number }>,
        private readonly inventoryService: InventoryService,
        private readonly userInventoryService: UserInventoryService,
        private readonly toastService: ToastService
    ) {
        this.itemId = config.data?.itemId;
        this.assignmentId = config.data?.assignmentId;
        this.maxQuantity = config.data?.maxQuantity ?? 1;
    }

    ngOnDestroy(): void {
        this.photos.forEach((photo) => URL.revokeObjectURL(photo.previewUrl));
    }

    protected get invalidCount(): number {
        return (this.quantity < 1 || this.quantity > this.maxQuantity ? 1 : 0) + (this.description.trim() ? 0 : 1);
    }

    protected addPhotos(event: Event): void {
        const input = event.target as HTMLInputElement;
        const files = Array.from(input.files ?? []);
        input.value = '';
        const valid = files.filter((file) => file.size > 0 && file.size <= MAX_PHOTO_BYTES && ['image/jpeg', 'image/png'].includes(file.type));
        if (valid.length < files.length) this.toastService.error('Fotografia non valida', 'Sono ammessi JPEG/PNG fino a 10 MB.');
        const room = this.maxPhotos - this.photos.length;
        if (valid.length > room) this.toastService.warn('Troppe fotografie', `Puoi allegare al massimo ${this.maxPhotos} fotografie alla segnalazione.`);
        this.photos = [...this.photos, ...valid.slice(0, Math.max(0, room)).map((file) => ({ file, previewUrl: URL.createObjectURL(file) }))];
    }

    protected removePhoto(index: number): void {
        const [removed] = this.photos.splice(index, 1);
        if (removed) URL.revokeObjectURL(removed.previewUrl);
        this.photos = [...this.photos];
    }

    protected save(): void {
        if (this.invalidCount || this.saving) return;
        const request = this.itemId
            ? this.inventoryService.createIssue(this.itemId, this.quantity, this.severity, this.description.trim())
            : this.userInventoryService.createIssue(this.assignmentId!, this.quantity, this.severity, this.description.trim());
        this.saving = true;
        request.pipe(first()).subscribe({
            next: (issue: InventoryIssue) => this.uploadPhotos(issue),
            error: () => (this.saving = false)
        });
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    /**
     * Le fotografie partono dopo la segnalazione, una alla volta. Se una non arriva la segnalazione resta valida: il
     * dialogo si chiude comunque e avvisa che le fotografie mancanti si possono aggiungere dalla segnalazione.
     */
    private uploadPhotos(issue: InventoryIssue): void {
        if (!this.photos.length) {
            this.dialogRef.close(issue);
            return;
        }
        from(this.photos)
            .pipe(
                concatMap((photo) => this.upload(issue.id, photo.file)),
                toArray()
            )
            .subscribe({
                next: () => this.dialogRef.close(issue),
                error: () => {
                    this.toastService.warn('Fotografie non caricate', 'La segnalazione è registrata: aggiungi le fotografie mancanti dalla segnalazione.');
                    this.dialogRef.close(issue);
                }
            });
    }

    private upload(issueId: number, file: File): Observable<unknown> {
        return this.itemId ? this.inventoryService.uploadIssuePhoto(issueId, file) : this.userInventoryService.uploadIssuePhoto(issueId, file);
    }
}
