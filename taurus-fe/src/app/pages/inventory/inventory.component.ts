import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ConfirmationService, SelectItem } from 'primeng/api';
import { DataViewLazyLoadEvent } from 'primeng/dataview';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { SelectChangeEvent } from 'primeng/select';
import { first } from 'rxjs';
import { AddInventoryDialogComponent } from '../../dialogs/add-inventory-dialog/add-inventory-dialog.component';
import { ImportsModule } from '../../imports';
import { InventoryCondition, InventoryErasureRequest, InventoryItem, Page } from '../../module';
import { InventoryService, ToastService } from '../../service';

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
    protected erasureRequests: InventoryErasureRequest[] = [];
    protected layout: 'list' | 'grid' = 'list';
    protected readonly layoutOptions = ['list', 'grid'];
    protected sortOptions: SelectItem[] = [];
    protected totalRecords = 0;
    protected loading = false;
    protected search = '';
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
        private readonly toastService: ToastService,
        private readonly confirmationService: ConfirmationService,
        private readonly dialogService: DialogService
    ) { }

    ngOnInit(): void {
        this.sortOptions = [
            { label: 'Nome A-Z', value: 'name' },
            { label: 'Nome Z-A', value: '!name' },
            { label: 'Numero inventariale A-Z', value: 'inventoryNumber' },
            { label: 'Numero inventariale Z-A', value: '!inventoryNumber' }
        ];
        this.loadErasureRequests();
    }

    protected onLazyLoad(event: DataViewLazyLoadEvent): void {
        this.dataViewLazyLoadEvent = event;
        this.loadItems();
    }

    protected onGlobalFilter(event: Event): void {
        this.search = (event.target as HTMLInputElement).value;
        this.dataViewLazyLoadEvent = { ...this.dataViewLazyLoadEvent, first: 0 };
        this.loadItems();
    }

    protected onSortChange(event: SelectChangeEvent): void {
        const value = event.value as string;
        this.dataViewLazyLoadEvent = {
            ...this.dataViewLazyLoadEvent,
            first: 0,
            sortField: value.startsWith('!') ? value.substring(1) : value,
            sortOrder: value.startsWith('!') ? -1 : 1
        };
        this.loadItems();
    }

    protected addNew(): void {
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
                    this.loadItems();
                });
        });
    }

    protected deleteItem(item: InventoryItem): void {
        if (!item.id) return;
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
                        this.loadItems();
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

    protected photoUrl(item: InventoryItem): string | undefined {
        const photo = item.photos?.[0];
        return photo ? this.inventoryService.photoUrl(photo.id, false) : undefined;
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

    private loadErasureRequests(): void {
        this.inventoryService
            .getErasureRequests()
            .pipe(first())
            .subscribe((values) => (this.erasureRequests = values));
    }
}
