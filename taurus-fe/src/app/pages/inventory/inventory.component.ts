import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { first, forkJoin } from 'rxjs';
import { AddInventoryDialogComponent } from '../../dialogs/add-inventory-dialog/add-inventory-dialog.component';
import { InventoryExpirationBadgeComponent } from '../../components/inventory-expiration-badge/inventory-expiration-badge.component';
import { ImportsModule } from '../../imports';
import { InventoryAssignmentScope, InventoryAssignmentSummary, InventoryCondition, InventoryErasureRequest, InventoryItem, Page } from '../../module';
import { ConfirmService, InventoryService, KeycloakService, ListLayout, ListLayoutService, ToastService, UserInventoryService } from '../../service';
import { ListPageBase } from '../_shared/list-page.base';

type InventoryViewMode = 'TENANT' | 'MINE';

@Component({
    selector: 'app-inventory',
    standalone: true,
    imports: [RouterModule, ImportsModule, InventoryExpirationBadgeComponent],
    templateUrl: './inventory.component.html',
    providers: [DialogService]
})
export class InventoryComponent extends ListPageBase implements OnInit {
    protected items: InventoryItem[] = [];
    protected selectedItems: InventoryItem[] = [];
    protected assignments: InventoryAssignmentSummary[] = [];
    protected erasureRequests: InventoryErasureRequest[] = [];
    protected layout: ListLayout = 'list';
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
    protected reportIncludeAssigned = true;
    protected reportIncludeReturned = true;
    protected reportIncludePhotos = true;
    protected attention?: 'pending-decisions' | 'pending-returns' | 'expiring';

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
        private readonly confirmService: ConfirmService,
        private readonly listLayoutService: ListLayoutService,
        private readonly dialogService: DialogService,
        private readonly route: ActivatedRoute,
        private readonly router: Router
    ) {
        super();
        this.dataViewLazyLoadEvent = { first: 0, rows: 12, sortField: 'name', sortOrder: 1 };
    }

    protected get isAdmin(): boolean {
        return this.keycloakService.isAdmin;
    }

    protected get personalView(): boolean {
        return this.viewMode === 'MINE';
    }

    protected get values(): Array<InventoryItem | InventoryAssignmentSummary> {
        return this.personalView ? this.assignments : this.items;
    }

    protected get emptyMessage(): string {
        if (this.searchTerm) return 'Prova a modificare o azzerare la ricerca.';
        if (!this.personalView) return 'Aggiungi il primo oggetto all’inventario.';
        return this.scope === 'POSSESSED' ? 'Non hai materiale attualmente in possesso.' : 'Non risultano consegne completamente riconsegnate.';
    }

    protected get canDownloadReport(): boolean {
        return this.reportIncludeAssigned || this.reportIncludeReturned;
    }

    ngOnInit(): void {
        this.listLayoutService.observe('inventory', (value) => (this.layout = value));
        this.viewMode = this.isAdmin && this.route.snapshot.queryParamMap.get('view') !== 'mine' ? 'TENANT' : 'MINE';
        const attention = this.route.snapshot.queryParamMap.get('attention');
        if (attention && ['pending-decisions', 'pending-returns', 'expiring'].includes(attention)) {
            this.attention = attention as typeof this.attention;
        }
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

    protected onLayoutChange(value: ListLayout): void {
        this.layout = value;
        this.listLayoutService.set('inventory', value);
    }

    protected onViewChange(mode: InventoryViewMode): void {
        this.viewMode = mode;
        this.router.navigate([], {
            relativeTo: this.route,
            queryParams: { view: mode === 'MINE' ? 'mine' : null, attention: null },
            queryParamsHandling: 'merge',
            replaceUrl: true
        });
        this.searchTerm = '';
        this.attention = undefined;
        this.selectedItems = [];
        this.totalRecords = 0;
        this.dataViewLazyLoadEvent = { ...this.dataViewLazyLoadEvent, first: 0 };
        this.configureSortOptions();
        this.loadElements(this.searchTerm);
    }

    protected onScopeChange(scope: InventoryAssignmentScope): void {
        this.scope = scope;
        this.dataViewLazyLoadEvent = { ...this.dataViewLazyLoadEvent, first: 0 };
        this.loadElements(this.searchTerm);
    }

    protected addNew(): void {
        if (!this.isAdmin || this.personalView) return;
        const dynamicDialogRef: DynamicDialogRef = this.dialogService.open(AddInventoryDialogComponent, {
            header: 'Aggiungi oggetto di inventario',
            closable: true,
            draggable: true,
            resizable: true,
            modal: true,
            width: '40rem',
            breakpoints: { '767px': 'calc(100vw - 2rem)' }
        });

        dynamicDialogRef.onClose.pipe(first()).subscribe((result?: InventoryItem) => {
            if (!result) return;
            this.inventoryService
                .createItem(result)
                .pipe(first())
                .subscribe(() => {
                    this.toastService.success('Successo', 'Oggetto aggiunto all’inventario.');
                    this.loadElements(this.searchTerm);
                });
        });
    }

    protected get canSelect(): boolean {
        return this.isAdmin && !this.personalView;
    }

    protected isSelected(item: InventoryItem): boolean {
        return this.selectedItems.some((selected) => selected.id === item.id);
    }

    protected isAllSelected(items: InventoryItem[]): boolean {
        return items.length > 0 && items.every((item) => this.isSelected(item));
    }

    protected toggleSelection(item: InventoryItem): void {
        this.selectedItems = this.isSelected(item) ? this.selectedItems.filter((selected) => selected.id !== item.id) : [...this.selectedItems, item];
    }

    protected toggleSelectAll(items: InventoryItem[]): void {
        if (this.isAllSelected(items)) {
            this.selectedItems = this.selectedItems.filter((selected) => !items.some((item) => item.id === selected.id));
            return;
        }
        this.selectedItems = [...this.selectedItems, ...items.filter((item) => !this.isSelected(item))];
    }

    protected clearSelection(): void {
        this.selectedItems = [];
    }

    protected deleteSelectedItems(): void {
        const selected = this.selectedItems.filter((item) => !!item.id);
        if (!this.canSelect || selected.length === 0) return;
        this.confirmService.confirmDestructive({
            title: 'Elimina oggetti selezionati',
            consequence: `I ${selected.length} oggetti selezionati non saranno più visibili in inventario.`,
            actionLabel: 'Elimina definitivamente',
            accept: () =>
                forkJoin(selected.map((item) => this.inventoryService.deleteItem(item.id!)))
                    .pipe(first())
                    .subscribe(() => {
                        this.selectedItems = [];
                        this.toastService.success('Oggetti eliminati', `${selected.length} oggetti rimossi dall’inventario.`);
                        this.loadElements(this.searchTerm);
                    })
        });
    }

    protected deleteItem(item: InventoryItem): void {
        if (!item.id || !this.isAdmin || this.personalView) return;
        this.confirmService.confirmDestructive({
            title: 'Elimina oggetto',
            consequence: `“${item.name}” verrà rimosso dall’inventario.`,
            actionLabel: 'Elimina',
            accept: () =>
                this.inventoryService
                    .deleteItem(item.id!)
                    .pipe(first())
                    .subscribe(() => {
                        this.toastService.success('Oggetto eliminato', 'L’oggetto è stato rimosso dall’inventario.');
                        this.loadElements(this.searchTerm);
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

    protected loadElements(search?: string): void {
        this.searchTerm = search ?? this.searchTerm;
        if (this.personalView) this.loadAssignments();
        else this.loadItems();
    }

    private loadItems(): void {
        this.selectedItems = [];
        const rows = this.dataViewLazyLoadEvent.rows || 10;
        const page = Math.floor((this.dataViewLazyLoadEvent.first || 0) / rows);
        const sortField = this.dataViewLazyLoadEvent.sortField || 'name';
        const sort = `${sortField},${(this.dataViewLazyLoadEvent.sortOrder || 1) > 0 ? 'asc' : 'desc'}`;
        this.loading = true;
        this.inventoryService
            .getItems(this.searchTerm, page, rows, sort, this.attention)
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
            .getAssignments(this.searchTerm, this.scope, page, rows, sort, this.attention)
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
