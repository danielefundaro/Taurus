import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { first } from 'rxjs';
import { ImportsModule } from '../../imports';
import { InventoryAssignment, InventoryCondition, InventoryReturn } from '../../module';
import { InventoryService, ToastService } from '../../service';
import { InventoryExpirationBadgeComponent } from '../inventory-expiration-badge/inventory-expiration-badge.component';

@Component({
    selector: 'app-inventory-assignments',
    standalone: true,
    imports: [ImportsModule, InventoryExpirationBadgeComponent],
    templateUrl: './inventory-assignments.component.html'
})
export class InventoryAssignmentsComponent implements OnInit, OnChanges {
    @Input({ required: true }) userIndex!: number;

    protected assignments: InventoryAssignment[] = [];
    protected loading = false;
    protected returnQuantities: Record<number, number> = {};
    protected returnNotes: Record<number, string> = {};
    protected returnConditions: Record<number, InventoryCondition | undefined> = {};
    protected returnCompletionNotes: Record<number, string> = {};
    protected reportIncludeAssigned = true;
    protected reportIncludeReturned = true;
    protected reportIncludePhotos = true;
    protected readonly conditions: { label: string; value: InventoryCondition }[] = [
        { label: 'Nuovo', value: 'NEW' },
        { label: 'Eccellente', value: 'EXCELLENT' },
        { label: 'Buono', value: 'GOOD' },
        { label: 'Discreto', value: 'FAIR' },
        { label: 'Da riparare', value: 'TO_REPAIR' },
        { label: 'Fuori servizio', value: 'OUT_OF_SERVICE' }
    ];

    constructor(
        private readonly inventoryService: InventoryService,
        private readonly toastService: ToastService
    ) {}

    ngOnInit(): void {
        this.load();
    }
    ngOnChanges(changes: SimpleChanges): void {
        if (!changes['userIndex']?.firstChange) this.load();
    }

    protected requestReturn(assignment: InventoryAssignment): void {
        const quantity = this.returnQuantities[assignment.id] ?? assignment.outstandingQuantity;
        this.inventoryService
            .requestReturn(assignment.id, quantity, this.returnNotes[assignment.id])
            .pipe(first())
            .subscribe({
                next: () => {
                    this.toastService.success('Riconsegna avviata', 'La richiesta è stata registrata.');
                    this.load();
                }
            });
    }

    protected completeReturn(value: InventoryReturn): void {
        this.inventoryService
            .completeReturn(value.id, value.quantity, this.returnConditions[value.id], this.returnCompletionNotes[value.id])
            .pipe(first())
            .subscribe({
                next: () => {
                    this.toastService.success('Riconsegna completata', 'Il materiale è stato segnato come riconsegnato.');
                    this.load();
                }
            });
    }

    protected uploadReturnPhoto(value: InventoryReturn, event: Event): void {
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];
        if (!file) return;
        if (file.size > 10 * 1024 * 1024 || !['image/jpeg', 'image/png'].includes(file.type)) {
            this.toastService.error('Fotografia non valida', 'Sono ammessi JPEG/PNG fino a 10 MB. WebP non è supportato.');
            input.value = '';
            return;
        }
        this.inventoryService
            .uploadReturnPhoto(value.id, file)
            .pipe(first())
            .subscribe(() => {
                input.value = '';
                this.load();
            });
    }

    protected downloadReport(): void {
        this.inventoryService
            .downloadReport(this.userIndex, this.reportIncludeAssigned, this.reportIncludeReturned, this.reportIncludePhotos)
            .pipe(first())
            .subscribe((blob) => {
                const url = URL.createObjectURL(blob);
                const anchor = document.createElement('a');
                anchor.href = url;
                anchor.download = 'prospetto-inventario.pdf';
                anchor.click();
                URL.revokeObjectURL(url);
            });
    }

    protected get canDownloadReport(): boolean {
        return this.reportIncludeAssigned || this.reportIncludeReturned;
    }

    protected hasOpenReturn(assignment: InventoryAssignment): boolean {
        return assignment.returns.some((value) => value.status === 'REQUESTED');
    }

    protected photoUrl(photoId: number): string {
        return this.inventoryService.photoUrl(photoId);
    }
    protected returnPhotoUrl(photoId: number): string {
        return `${this.inventoryService.photoUrl(photoId).replace('/photos/', '/return-photos/')}`;
    }

    private load(): void {
        if (!this.userIndex) return;
        this.loading = true;
        this.inventoryService
            .getUserAssignments(this.userIndex)
            .pipe(first())
            .subscribe({
                next: (values) => {
                    this.assignments = values;
                    values.forEach((value) => (this.returnQuantities[value.id] = Math.max(1, value.outstandingQuantity)));
                    values
                        .flatMap((value) => value.returns)
                        .forEach((value) => {
                            this.returnConditions[value.id] = value.condition;
                            this.returnCompletionNotes[value.id] = value.notes ?? '';
                        });
                    this.loading = false;
                },
                error: () => (this.loading = false)
            });
    }
}
