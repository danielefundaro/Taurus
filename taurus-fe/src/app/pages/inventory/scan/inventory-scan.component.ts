import { HttpErrorResponse } from '@angular/common/http';
import { AfterViewChecked, Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Meta } from '@angular/platform-browser';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { DialogService } from 'primeng/dynamicdialog';
import { first } from 'rxjs';
import { InventoryIssueDialogComponent } from '../../../dialogs/inventory-issue-dialog/inventory-issue-dialog.component';
import { InventoryLabelDialogComponent } from '../../../dialogs/inventory-label-dialog/inventory-label-dialog.component';
import { ImportsModule } from '../../../imports';
import { InventoryItem, InventoryLabelRequest, InventoryScanAssignment, InventoryScanResult } from '../../../module';
import { InventoryScanService, InventoryService, KeycloakService, ToastService, UserInventoryService } from '../../../service';

@Component({
    selector: 'app-inventory-scan',
    standalone: true,
    imports: [ImportsModule, RouterModule],
    templateUrl: './inventory-scan.component.html',
    styleUrl: './inventory-scan.component.scss',
    providers: [DialogService]
})
export class InventoryScanComponent implements OnInit, AfterViewChecked {
    @ViewChild('resultTitle') private resultTitle?: ElementRef<HTMLHeadingElement>;
    protected loading = true;
    protected unavailable = false;
    protected result?: InventoryScanResult;
    protected readonly tenantCode: string;
    private focusPending = false;

    constructor(
        private readonly route: ActivatedRoute,
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

    protected adminPhotoUrl(): string | undefined {
        return this.result?.previewPhotoId ? this.inventoryService.photoUrl(this.result.previewPhotoId) : undefined;
    }

    protected assignmentPhotoUrl(assignment: InventoryScanAssignment): string | undefined {
        return assignment.previewPhotoId ? this.userInventoryService.photoUrl(assignment.previewPhotoId) : undefined;
    }

    protected reportAdminIssue(): void {
        if (!this.result?.itemId || !this.result.totalQuantity) return;
        this.dialogService
            .open(InventoryIssueDialogComponent, {
                header: 'Segnala guasto',
                modal: true,
                width: '34rem',
                breakpoints: { '767px': 'calc(100vw - 1rem)' },
                data: { itemId: this.result.itemId, maxQuantity: this.result.totalQuantity }
            })
            .onClose.pipe(first())
            .subscribe((issue) => {
                if (!issue) return;
                this.toastService.success('Segnalazione inviata', 'Il guasto è stato registrato.');
                this.resolve();
            });
    }

    protected reportAssignmentIssue(assignment: InventoryScanAssignment): void {
        this.dialogService
            .open(InventoryIssueDialogComponent, {
                header: 'Segnala guasto',
                modal: true,
                width: '34rem',
                breakpoints: { '767px': 'calc(100vw - 1rem)' },
                data: { assignmentId: assignment.assignmentId, maxQuantity: assignment.outstandingQuantity }
            })
            .onClose.pipe(first())
            .subscribe((issue) => {
                if (issue) this.toastService.success('Segnalazione inviata', 'Il guasto è stato registrato.');
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
                modal: true,
                width: '38rem',
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

    private download(blob: Blob): void {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = 'etichette-inventario.pdf';
        anchor.click();
        URL.revokeObjectURL(url);
    }
}
