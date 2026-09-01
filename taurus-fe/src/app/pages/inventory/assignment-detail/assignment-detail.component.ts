import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize, first } from 'rxjs';
import { InventoryExpirationBadgeComponent } from '../../../components/inventory-expiration-badge/inventory-expiration-badge.component';
import { ImportsModule } from '../../../imports';
import { InventoryAssignment, InventoryCondition, InventoryDecisionType, InventoryReturn } from '../../../module';
import { ToastService, UserInventoryService } from '../../../service';

@Component({
    selector: 'app-inventory-assignment-detail',
    standalone: true,
    imports: [ImportsModule, InventoryExpirationBadgeComponent],
    templateUrl: './assignment-detail.component.html',
    styleUrl: './assignment-detail.component.scss'
})
export class InventoryAssignmentDetailComponent implements OnInit {
    protected assignment?: InventoryAssignment;
    protected loading = false;
    protected decision?: InventoryDecisionType;
    protected rejectionReason = '';
    protected returnQuantity = 1;
    protected returnNotes = '';
    protected reportIncludeAssigned = true;
    protected reportIncludeReturned = true;
    protected reportIncludePhotos = true;

    private readonly conditionLabels: Record<InventoryCondition, string> = {
        NEW: 'Nuovo',
        EXCELLENT: 'Eccellente',
        GOOD: 'Buono',
        FAIR: 'Discreto',
        TO_REPAIR: 'Da riparare',
        OUT_OF_SERVICE: 'Fuori servizio'
    };

    constructor(
        private readonly inventoryService: UserInventoryService,
        private readonly toastService: ToastService,
        private readonly route: ActivatedRoute,
        private readonly router: Router
    ) {}

    protected get canConfirmDecision(): boolean {
        return !!this.assignment && !!this.decision && (this.decision !== 'REJECTED' || !!this.rejectionReason.trim());
    }

    protected get pendingReturnQuantity(): number {
        return (this.assignment?.returns ?? []).filter((value) => value.status === 'REQUESTED').reduce((total, value) => total + value.quantity, 0);
    }

    protected get returnableQuantity(): number {
        return Math.max(0, (this.assignment?.outstandingQuantity ?? 0) - this.pendingReturnQuantity);
    }

    protected get canDownloadReport(): boolean {
        return this.reportIncludeAssigned || this.reportIncludeReturned;
    }

    ngOnInit(): void {
        this.route.params.pipe(first()).subscribe((params) => {
            const id = Number(params['id']);
            if (!Number.isInteger(id) || id < 1) {
                this.router.navigate(['/inventory']);
                return;
            }
            this.load(id);
        });
    }

    protected choose(choice: InventoryDecisionType, selected: boolean): void {
        this.decision = selected ? choice : undefined;
    }

    protected confirmDecision(): void {
        if (!this.assignment || !this.decision || !this.canConfirmDecision) return;
        this.inventoryService
            .decide(this.assignment.id, this.decision, this.assignment.revisionHash, this.rejectionReason)
            .pipe(first())
            .subscribe((updated) => {
                this.assignment = updated;
                this.decision = undefined;
                this.rejectionReason = '';
                this.toastService.success('Presa visione registrata', 'La scelta è definitiva per questa revisione.');
            });
    }

    protected requestReturn(): void {
        if (!this.assignment || this.returnQuantity < 1 || this.returnQuantity > this.returnableQuantity) return;
        this.inventoryService
            .requestReturn(this.assignment.id, this.returnQuantity, this.returnNotes)
            .pipe(first())
            .subscribe(() => {
                this.toastService.success('Riconsegna avviata', 'La richiesta è stata inviata agli amministratori.');
                this.returnQuantity = 1;
                this.returnNotes = '';
                this.load(this.assignment!.id);
            });
    }

    protected uploadReturnPhoto(value: InventoryReturn, event: Event): void {
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];
        if (!file) return;
        if (!this.isValidPhoto(file)) {
            input.value = '';
            return;
        }
        this.inventoryService
            .uploadReturnPhoto(value.id, file)
            .pipe(first())
            .subscribe(() => {
                input.value = '';
                this.load(this.assignment!.id);
            });
    }

    protected conditionLabel(condition: InventoryCondition): string {
        return this.conditionLabels[condition];
    }

    protected photoUrl(id: number): string {
        return this.inventoryService.photoUrl(id);
    }

    protected returnPhotoUrl(id: number): string {
        return this.inventoryService.returnPhotoUrl(id);
    }

    protected downloadReport(): void {
        this.inventoryService
            .downloadReport(this.reportIncludeAssigned, this.reportIncludeReturned, this.reportIncludePhotos)
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

    private load(id: number): void {
        this.loading = true;
        this.inventoryService
            .getAssignment(id)
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe((value) => {
                this.assignment = value;
                this.returnQuantity = Math.min(Math.max(1, this.returnableQuantity), this.returnableQuantity || 1);
            });
    }

    private isValidPhoto(file: File): boolean {
        const valid = file.size <= 10 * 1024 * 1024 && ['image/jpeg', 'image/png'].includes(file.type);
        if (!valid) {
            this.toastService.error('Fotografia non valida', 'Sono ammessi JPEG/PNG fino a 10 MB. WebP non è supportato.');
        }
        return valid;
    }
}
