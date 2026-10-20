import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize, first } from 'rxjs';
import { InventoryExpirationBadgeComponent } from '../../../components/inventory-expiration-badge/inventory-expiration-badge.component';
import { inventoryAssignmentStatusLabel, inventoryDecisionLabel, inventoryReturnStatusLabel } from '../../../constants';
import { InventoryIssueDialogComponent } from '../../../dialogs/inventory-issue-dialog/inventory-issue-dialog.component';
import { SignatureCodeDialogComponent, SignatureCodeDialogData, SignatureCodeDialogResult } from '../../../dialogs/signature-code-dialog/signature-code-dialog.component';
import { ImportsModule } from '../../../imports';
import { InventoryAssignment, InventoryCondition, InventoryDecisionType, InventoryReturn } from '../../../module';
import { SignatureService, TenantFeatureService, ToastService, UserInventoryService } from '../../../service';
import { DialogService } from 'primeng/dynamicdialog';

@Component({
    selector: 'app-inventory-assignment-detail',
    standalone: true,
    imports: [ImportsModule, InventoryExpirationBadgeComponent],
    templateUrl: './assignment-detail.component.html',
    styleUrl: './assignment-detail.component.scss',
    providers: [DialogService]
})
export class InventoryAssignmentDetailComponent implements OnInit {
    protected assignment?: InventoryAssignment;
    protected loading = false;
    protected decision?: InventoryDecisionType;
    protected rejectionReason = '';
    protected returnQuantity = 1;
    protected returnNotes = '';
    /** L'associazione richiede la firma con codice per la presa visione. */
    protected signatureEnabled = false;
    protected readonly inventoryQrEnabled;
    protected readonly assignmentStatusLabel = inventoryAssignmentStatusLabel;
    protected readonly decisionLabel = inventoryDecisionLabel;
    protected readonly returnStatusLabel = inventoryReturnStatusLabel;

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
        private readonly dialogService: DialogService,
        private readonly signatureService: SignatureService,
        tenantFeatureService: TenantFeatureService,
        private readonly route: ActivatedRoute,
        private readonly router: Router
    ) {
        this.inventoryQrEnabled = tenantFeatureService.inventoryQrEnabled;
    }

    protected get canConfirmDecision(): boolean {
        return !!this.assignment && !!this.decision && (this.decision !== 'REJECTED' || !!this.rejectionReason.trim());
    }

    protected get pendingReturnQuantity(): number {
        return (this.assignment?.returns ?? []).filter((value) => value.status === 'REQUESTED').reduce((total, value) => total + value.quantity, 0);
    }

    protected get returnableQuantity(): number {
        return Math.max(0, (this.assignment?.outstandingQuantity ?? 0) - this.pendingReturnQuantity);
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
        this.signatureService
            .getMethods()
            .pipe(first())
            .subscribe((methods) => (this.signatureEnabled = methods.enabled));
    }

    protected choose(choice: InventoryDecisionType, selected: boolean): void {
        this.decision = selected ? choice : undefined;
    }

    protected confirmDecision(): void {
        if (!this.assignment || !this.decision || !this.canConfirmDecision) return;
        if (this.signatureEnabled) {
            this.signDecision(this.assignment, this.decision);
            return;
        }
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

    /** Presa visione confermata con un codice, via e-mail o dall’app di autenticazione. */
    private signDecision(assignment: InventoryAssignment, decision: InventoryDecisionType): void {
        const data: SignatureCodeDialogData = {
            heading: 'Firma la presa visione',
            subtitle: assignment.inventoryNumber + ' – ' + assignment.itemName,
            request: {
                purpose: 'ASSIGNMENT_DECISION',
                items: [{ assignmentId: assignment.id, revisionHash: assignment.revisionHash, decision, rejectionReason: decision === 'REJECTED' ? this.rejectionReason.trim() : undefined }]
            }
        };
        this.dialogService
            .open(SignatureCodeDialogComponent, {
                header: data.heading,
                showHeader: false,
                modal: true,
                width: '34rem',
                breakpoints: { '767px': 'calc(100vw - 1rem)' },
                data
            })
            .onClose.pipe(first())
            .subscribe((result?: SignatureCodeDialogResult) => {
                if (!result) return;
                if (result.confirmation) {
                    this.decision = undefined;
                    this.rejectionReason = '';
                    this.toastService.success('Presa visione firmata', 'La scelta è stata firmata con il codice ed è definitiva per questa revisione.');
                }
                this.load(assignment.id);
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

    protected reportIssue(): void {
        if (!this.assignment?.outstandingQuantity) return;
        this.dialogService
            .open(InventoryIssueDialogComponent, {
                header: 'Segnala guasto',
                showHeader: false,
                modal: true,
                width: '28rem',
                breakpoints: { '767px': 'calc(100vw - 1rem)' },
                data: { assignmentId: this.assignment.id, maxQuantity: this.assignment.outstandingQuantity }
            })
            .onClose.pipe(first())
            .subscribe((issue) => {
                if (issue) this.toastService.success('Segnalazione inviata', 'Il guasto è stato registrato e notificato agli amministratori.');
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
