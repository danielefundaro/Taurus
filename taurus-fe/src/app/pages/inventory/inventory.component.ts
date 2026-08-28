import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { ConfirmationService, SelectItem } from 'primeng/api';
import { DataViewLazyLoadEvent } from 'primeng/dataview';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { SelectChangeEvent } from 'primeng/select';
import { first } from 'rxjs';
import { AddInventoryDialogComponent } from '../../dialogs/add-inventory-dialog/add-inventory-dialog.component';
import { ImportsModule } from '../../imports';
import { InventoryAssignmentScope, InventoryAssignmentSummary, InventoryCondition, InventoryErasureRequest, InventoryItem, Page } from '../../module';
import { InventoryService, KeycloakService, ToastService, UserInventoryService } from '../../service';

type InventoryViewMode = 'TENANT' | 'MINE';

@Component({
    selector: 'app-inventory',
    standalone: true,
    imports: [RouterModule, ImportsModule],
    templateUrl: './inventory.component.html',
    styleUrl: './inventory.component.scss',
    providers: [ConfirmationService, DialogService]
})
export class InventoryComponent implements OnInit {
    protected items: InventoryItem[] = [];
    protected assignments: InventoryAssignmentSummary[] = [];
    protected erasureRequests: InventoryErasureRequest[] = [];
    protected layout: 'list' | 'grid' = 'list';
    protected readonly layoutOptions = ['list', 'grid'];
    protected readonly viewOptions = [
        { label: 'Inventario', value: 'TENANT' },
        { label: 'I miei oggetti', value: 'MINE' }
    ];
    protected readonly scopeOptions = [
        { label: 'In possesso', value: 'POSSESSED' },
        { label: 'Riconsegnati', value: 'RETURNED' }
    ];
    protected viewMode: InventoryViewMode = 'TENANT';
    protected scope: InventoryAssignmentScope = 'POSSESSED';
    protected sortOptions: SelectItem[] = [];
    protected totalRecords = 0;
    protected loading = false;
    protected search = '';
    protected reportIncludeAssigned = true;
    protected reportIncludeReturned = true;
    protected reportIncludePhotos = true;
    protected dataViewLazyLoadEvent: DataViewLazyLoadEvent = {
        first: 0,
        rows: 10,
        sortField: 'name',
        sortOrder: 1
    };

    private readonly conditionLabels: Record<InventoryCondition, string> = {
        NEW: 'Nuovo',
        EXCELLENT: 'Eccellente',
        GOOD: 'Buono',
        FAIR: 'Discreto',
        TO_REPAIR: 'Da riparare',
        OUT_OF_SERVICE: 'Fuori servizio'
    };

    constructor(
        private readonly inventoryService: InventoryService,
        private readonly userInventoryService: UserInventoryService,
        private readonly keycloakService: KeycloakService,
        private readonly toastService: ToastService,
        private readonly confirmationService: ConfirmationService,
        private readonly dialogService: DialogService,
        private readonly route: ActivatedRoute,
        private readonly router: Router
    ) {}

    protected get isAdmin(): boolean {
        return this.keycloakService.isAdmin;
    }

    protected get personalView(): boolean {
        return this.viewMode === 'MINE';
    }

    protected get values(): Array<InventoryItem | InventoryAssignmentSummary> {
        return this.personalView ? this.assignments : this.items;
    }

    protected get canDownloadReport(): boolean {
        return this.reportIncludeAssigned || this.reportIncludeReturned;
    }

    ngOnInit(): void {
        this.viewMode = this.isAdmin && this.route.snapshot.queryParamMap.get('view') !== 'mine' ? 'TENANT' : 'MINE';
        this.configureSortOptions();
        if (this.isAdmin) this.loadErasureRequests();
        if (this.isAdmin && this.route.snapshot.queryParamMap.get('action') === 'new') {
            this.router.navigate([], {
                relativeTo: this.route,
                queryParams: { action: null },
                queryParamsHandling: 'merge',
                replaceUrl: true
            });
            this.addNew();
        }
    }

    protected onLazyLoad(event: DataViewLazyLoadEvent): void {
        this.dataViewLazyLoadEvent = event;
        this.loadElements();
    }

    protected onGlobalFilter(event: Event): void {
        this.search = (event.target as HTMLInputElement).value;
        this.dataViewLazyLoadEvent = { ...this.dataViewLazyLoadEvent, first: 0 };
        this.loadElements();
    }

    protected onSortChange(event: SelectChangeEvent): void {
        const value = event.value as string;
        this.dataViewLazyLoadEvent = {
            ...this.dataViewLazyLoadEvent,
            first: 0,
            sortField: value.startsWith('!') ? value.substring(1) : value,
            sortOrder: value.startsWith('!') ? -1 : 1
        };
        this.loadElements();
    }

    protected onViewChange(mode: InventoryViewMode): void {
        this.viewMode = mode;
        this.router.navigate([], {
            relativeTo: this.route,
            queryParams: { view: mode === 'MINE' ? 'mine' : null },
            queryParamsHandling: 'merge',
            replaceUrl: true
        });
        this.search = '';
        this.totalRecords = 0;
        this.dataViewLazyLoadEvent = { ...this.dataViewLazyLoadEvent, first: 0 };
        this.configureSortOptions();
        this.loadElements();
    }

    protected onScopeChange(scope: InventoryAssignmentScope): void {
        this.scope = scope;
        this.dataViewLazyLoadEvent = { ...this.dataViewLazyLoadEvent, first: 0 };
        this.loadElements();
    }

    protected addNew(): void {
        if (!this.isAdmin || this.personalView) return;
        const dynamicDialogRef: DynamicDialogRef = this.dialogService.open(AddInventoryDialogComponent, {
            header: 'Aggiungi oggetto di inventario',
            closable: true,
            draggable: true,
            resizable: true,
            modal: true,
            width: '50vw',
            breakpoints: { '1199px': '75vw', '575px': '90vw' }
        });

        dynamicDialogRef.onClose.pipe(first()).subscribe((result?: InventoryItem) => {
            if (!result) return;
            this.inventoryService
                .createItem(result)
                .pipe(first())
                .subscribe(() => {
                    this.toastService.success('Successo', 'Oggetto aggiunto all’inventario.');
                    this.loadElements();
                });
        });
    }

    protected deleteItem(item: InventoryItem): void {
        if (!item.id || !this.isAdmin || this.personalView) return;
        this.confirmationService.confirm({
            header: 'Conferma eliminazione',
            message: `Rimuovere “${item.name}” dall’inventario?`,
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Elimina',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () =>
                this.inventoryService
                    .deleteItem(item.id!)
                    .pipe(first())
                    .subscribe(() => {
                        this.toastService.success('Oggetto eliminato', 'L’oggetto è stato rimosso dall’inventario.');
                        this.loadElements();
                    })
        });
    }

    protected completeErasure(request: InventoryErasureRequest): void {
        this.inventoryService
            .completeErasureRequest(request.id)
            .pipe(first())
            .subscribe(() => {
                this.toastService.success('Cancellazione completata', 'Lo storico inventario è stato pseudonimizzato.');
                this.loadErasureRequests();
            });
    }

    protected conditionLabel(condition: InventoryCondition): string {
        return this.conditionLabels[condition];
    }

    protected itemPhotoUrl(item: InventoryItem): string | undefined {
        const photo = item.photos?.find((value) => value.preview) ?? item.photos?.[0];
        return photo ? this.inventoryService.photoUrl(photo.id) : undefined;
    }

    protected assignmentPhotoUrl(assignment: InventoryAssignmentSummary): string | undefined {
        return assignment.photo ? this.userInventoryService.photoUrl(assignment.photo.id) : undefined;
    }

    protected downloadReport(): void {
        this.userInventoryService
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

    private configureSortOptions(): void {
        if (this.personalView) {
            this.sortOptions = [
                { label: 'Consegna più recente', value: '!assignedAt' },
                { label: 'Consegna meno recente', value: 'assignedAt' },
                { label: 'Nome A-Z', value: 'item.name' },
                { label: 'Nome Z-A', value: '!item.name' },
                { label: 'Numero inventariale A-Z', value: 'item.inventoryNumber' }
            ];
            this.dataViewLazyLoadEvent = { ...this.dataViewLazyLoadEvent, sortField: 'assignedAt', sortOrder: -1 };
        } else {
            this.sortOptions = [
                { label: 'Nome A-Z', value: 'name' },
                { label: 'Nome Z-A', value: '!name' },
                { label: 'Numero inventariale A-Z', value: 'inventoryNumber' },
                { label: 'Numero inventariale Z-A', value: '!inventoryNumber' }
            ];
            this.dataViewLazyLoadEvent = { ...this.dataViewLazyLoadEvent, sortField: 'name', sortOrder: 1 };
        }
    }

    private loadElements(): void {
        if (this.personalView) this.loadAssignments();
        else this.loadItems();
    }

    private loadItems(): void {
        const rows = this.dataViewLazyLoadEvent.rows || 10;
        const page = Math.floor((this.dataViewLazyLoadEvent.first || 0) / rows);
        const sortField = this.dataViewLazyLoadEvent.sortField || 'name';
        const sort = `${sortField},${(this.dataViewLazyLoadEvent.sortOrder || 1) > 0 ? 'asc' : 'desc'}`;
        this.loading = true;
        this.inventoryService
            .getItems(this.search, page, rows, sort)
            .pipe(first())
            .subscribe({
                next: (result: Page<InventoryItem>) => {
                    this.items = result.content;
                    this.totalRecords = result.totalElements;
                    this.loading = false;
                },
                error: () => (this.loading = false)
            });
    }

    private loadAssignments(): void {
        const rows = this.dataViewLazyLoadEvent.rows || 10;
        const page = Math.floor((this.dataViewLazyLoadEvent.first || 0) / rows);
        const sortField = this.dataViewLazyLoadEvent.sortField || 'assignedAt';
        const sort = `${sortField},${(this.dataViewLazyLoadEvent.sortOrder || -1) > 0 ? 'asc' : 'desc'}`;
        this.loading = true;
        this.userInventoryService
            .getAssignments(this.search, this.scope, page, rows, sort)
            .pipe(first())
            .subscribe({
                next: (result) => {
                    this.assignments = result.content;
                    this.totalRecords = result.totalElements;
                    this.loading = false;
                },
                error: () => (this.loading = false)
            });
    }

    private loadErasureRequests(): void {
        this.inventoryService
            .getErasureRequests()
            .pipe(first())
            .subscribe((values) => (this.erasureRequests = values));
    }
}
