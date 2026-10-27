import { HttpErrorResponse } from '@angular/common/http';
import { AfterViewChecked, Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Meta } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { DialogService } from 'primeng/dynamicdialog';
import { first } from 'rxjs';
import { InventoryExpirationBadgeComponent } from '../../../components/inventory-expiration-badge/inventory-expiration-badge.component';
import { inventoryConditionLabel } from '../../../constants';
import { InventoryIssueDialogComponent } from '../../../dialogs/inventory-issue-dialog/inventory-issue-dialog.component';
import { InventoryLabelDialogComponent } from '../../../dialogs/inventory-label-dialog/inventory-label-dialog.component';
import { InventoryQrRotateDialogComponent } from '../../../dialogs/inventory-qr-rotate-dialog/inventory-qr-rotate-dialog.component';
import { ImportsModule } from '../../../imports';
import { InventoryCondition, InventoryItem, InventoryLabelRequest, InventoryScanAction, InventoryScanAssignment, InventoryScanResult } from '../../../module';
import { InventoryScanService, InventoryService, KeycloakService, ToastService, UserInventoryService } from '../../../service';

/** Un collegamento della scheda personale: ogni azione apre la sezione della consegna che la completa. */
interface AssignmentLink {
    action: InventoryScanAction;
    label: string;
    icon: string;
    fragment?: string;
}

const ASSIGNMENT_LINKS: AssignmentLink[] = [
    { action: 'COMPLETE_DECISION', label: 'Completa presa visione', icon: 'pi pi-check-square', fragment: 'presa-visione' },
    { action: 'REQUEST_RETURN', label: 'Richiedi riconsegna', icon: 'pi pi-replay', fragment: 'riconsegna' },
    { action: 'ADD_RETURN_PHOTO', label: 'Foto di riconsegna', icon: 'pi pi-camera', fragment: 'riconsegna' },
    { action: 'ADD_ISSUE_PHOTO', label: 'Foto della segnalazione', icon: 'pi pi-images', fragment: 'segnalazioni' }
];

@Component({
    selector: 'app-inventory-scan',
    standalone: true,
    imports: [ImportsModule, RouterModule, InventoryExpirationBadgeComponent],
    templateUrl: './inventory-scan.component.html',
    styleUrl: './inventory-scan.component.scss',
    providers: [DialogService]
})
export class InventoryScanComponent implements OnInit, AfterViewChecked {
    @ViewChild('resultTitle') private resultTitle?: ElementRef<HTMLHeadingElement>;
    protected loading = true;
    protected unavailable = false;
    protected uploading = false;
    protected result?: InventoryScanResult;
    /** La versione emessa dalla rotazione fatta da questa pagina: il codice scansionato non è più valido. */
    protected rotatedVersion?: number;
    protected readonly tenantCode: string;
    protected readonly conditionLabel = inventoryConditionLabel;
    private focusPending = false;

    constructor(
        private readonly route: ActivatedRoute,
        private readonly router: Router,
        private readonly scanService: InventoryScanService,
        private readonly inventoryService: InventoryService,
        private readonly userInventoryService: UserInventoryService,
        private readonly keycloakService: KeycloakService,
        private readonly dialogService: DialogService,
        private readonly toastService: ToastService,
        meta: Meta
    ) {
        this.tenantCode = keycloakService.currentUserTenantCode ?? '';
        meta.updateTag({ name: 'referrer', content: 'no-referrer' });
    }

    ngOnInit(): void {
        this.resolve();
    }

    ngAfterViewChecked(): void {
        if (this.focusPending && this.resultTitle) {
            this.resultTitle.nativeElement.focus();
            this.focusPending = false;
        }
    }

    protected resolve(): void {
        const publicId = this.route.snapshot.paramMap.get('publicId') ?? '';
        this.loading = true;
        this.unavailable = false;
        this.scanService
            .resolve(publicId)
            .pipe(first())
            .subscribe({
                next: (result) => {
                    // Una sola consegna pertinente: la scheda della consegna ha già tutte le azioni, e sostituisce
                    // la scansione nella cronologia, così «indietro» non torna a una pagina intermedia.
                    if (result.target === 'OWN_ASSIGNMENTS' && result.assignments.length === 1) {
                        this.router.navigate(['/inventory/assignments', result.assignments[0].assignmentId], { replaceUrl: true });
                        return;
                    }
                    this.result = result;
                    this.loading = false;
                    this.focusPending = true;
                },
                error: (_: HttpErrorResponse) => {
                    this.result = undefined;
                    this.loading = false;
                    this.unavailable = true;
                    this.focusPending = true;
                }
            });
    }

    protected can(action: InventoryScanAction): boolean {
        return !!this.result?.allowedActions.includes(action);
    }

    protected assignmentLinks(assignment: InventoryScanAssignment): AssignmentLink[] {
        return ASSIGNMENT_LINKS.filter((link) => assignment.allowedActions.includes(link.action));
    }

    protected conditionSeverity(condition?: InventoryCondition): 'success' | 'info' | 'warn' | 'danger' {
        if (condition === 'OUT_OF_SERVICE') return 'danger';
        if (condition === 'TO_REPAIR') return 'warn';
        return condition === 'FAIR' ? 'info' : 'success';
    }

    protected adminPhotoUrl(): string | undefined {
        return this.result?.previewPhotoId ? this.inventoryService.photoUrl(this.result.previewPhotoId) : undefined;
    }

    protected assignmentPhotoUrl(assignment: InventoryScanAssignment): string | undefined {
        return assignment.previewPhotoId ? this.userInventoryService.photoUrl(assignment.previewPhotoId) : undefined;
    }

    protected reportAdminIssue(): void {
        if (!this.result?.itemId || !this.result.totalQuantity) return;
        this.openIssueDialog({ itemId: this.result.itemId, maxQuantity: this.result.totalQuantity }, true);
    }

    protected reportAssignmentIssue(assignment: InventoryScanAssignment): void {
        this.openIssueDialog({ assignmentId: assignment.assignmentId, maxQuantity: assignment.outstandingQuantity }, false);
    }

    protected uploadItemPhoto(event: Event): void {
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];
        input.value = '';
        if (!file || !this.result?.itemId) return;
        if (file.size > 10 * 1024 * 1024 || !['image/jpeg', 'image/png'].includes(file.type)) {
            this.toastService.error('Fotografia non valida', 'Sono ammessi JPEG/PNG fino a 10 MB.');
            return;
        }
        this.uploading = true;
        this.inventoryService
            .uploadPhoto(this.result.itemId, file)
            .pipe(first())
            .subscribe({
                next: () => {
                    this.uploading = false;
                    this.toastService.success('Fotografia aggiunta', 'La fotografia è nella scheda dell’oggetto.');
                    if (!this.rotatedVersion) this.resolve();
                },
                error: () => (this.uploading = false)
            });
    }

    protected rotateCode(): void {
        const itemId = this.result?.itemId;
        if (!itemId) return;
        this.dialogService
            .open(InventoryQrRotateDialogComponent, {
                header: 'Ruota codice QR',
                showHeader: false,
                modal: true,
                width: '28rem',
                breakpoints: { '767px': 'calc(100vw - 1rem)' }
            })
            .onClose.pipe(first())
            .subscribe((reason?: string) => {
                if (!reason) return;
                this.inventoryService
                    .rotateQrCode(itemId, reason)
                    .pipe(first())
                    .subscribe((rotation) => {
                        this.rotatedVersion = rotation.version;
                        this.toastService.success('Codice ruotato', 'Stampa una nuova etichetta per rendere nuovamente scansionabile il bene.');
                    });
            });
    }

    protected printLabel(): void {
        if (!this.result?.itemId) return;
        const item: InventoryItem = {
            id: this.result.itemId,
            inventoryNumber: this.result.inventoryNumber ?? '',
            name: this.result.name ?? '',
            totalQuantity: this.result.totalQuantity ?? 1,
            conditionStatus: this.result.conditionStatus ?? 'GOOD'
        };
        this.dialogService
            .open(InventoryLabelDialogComponent, {
                header: 'Stampa etichetta',
                showHeader: false,
                modal: true,
                width: '40rem',
                breakpoints: { '767px': 'calc(100vw - 1rem)' },
                data: { items: [item] }
            })
            .onClose.pipe(first())
            .subscribe((request?: InventoryLabelRequest) => {
                if (!request) return;
                this.inventoryService
                    .generateLabels(request)
                    .pipe(first())
                    .subscribe((blob) => this.download(blob));
            });
    }

    private openIssueDialog(data: { itemId?: number; assignmentId?: number; maxQuantity: number }, refresh: boolean): void {
        this.dialogService
            .open(InventoryIssueDialogComponent, {
                header: 'Segnala guasto',
                showHeader: false,
                modal: true,
                width: '28rem',
                breakpoints: { '767px': 'calc(100vw - 1rem)' },
                data
            })
            .onClose.pipe(first())
            .subscribe((issue) => {
                if (!issue) return;
                this.toastService.success('Segnalazione inviata', 'Il guasto è stato registrato.');
                if (refresh && !this.rotatedVersion) this.resolve();
            });
    }

    private download(blob: Blob): void {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = 'etichette-inventario.pdf';
        anchor.click();
        URL.revokeObjectURL(url);
    }
}
