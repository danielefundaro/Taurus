import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ConfirmationService, SelectItem } from 'primeng/api';
import { DataViewLazyLoadEvent } from 'primeng/dataview';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { SelectChangeEvent } from 'primeng/select';
import { delay, first, forkJoin } from 'rxjs';
import { AddTenantsDialogComponent } from '../../dialogs/add-tenants-dialog/add-tenants-dialog.component';
import { ImportsModule } from '../../imports';
import { Page, Tenants, TenantsCriteria } from '../../module';
import { StringFilter } from '../../module/criteria/filter';
import { TenantsService, ToastService, UsersService } from '../../service';

@Component({
    selector: 'app-tenants',
    imports: [
        RouterModule,
        ImportsModule,
    ],
    templateUrl: './tenants.component.html',
    styleUrl: './tenants.component.scss',
    providers: [
        UsersService,
        DialogService,
        ConfirmationService,
    ]
})
export class TenantsComponent implements OnInit {
    protected sortOptions!: SelectItem[];
    protected layout: 'list' | 'grid' = 'list';
    protected options = ['list', 'grid'];
    protected totalRecords: number = 0;
    protected dataViewLazyLoadEvent: DataViewLazyLoadEvent = { first: 0, rows: 10, sortField: 'name', sortOrder: 1 };
    protected tenants: Tenants[];
    protected selectedTenants: Tenants[] = [];

    constructor(
        private readonly tenantsService: TenantsService,
        private readonly toastService: ToastService,
        private readonly dialogService: DialogService,
        private readonly confirmationService: ConfirmationService,
    ) {
        this.tenants = [];
    }

    ngOnInit() {
        this.sortOptions = [
            { label: 'Name A-Z', value: 'name' },
            { label: 'Name Z-A', value: '!name' },
        ];
    }

    protected onSortChange(event: SelectChangeEvent) {
        let value = event.value;

        if (value.indexOf('!') === 0) {
            this.dataViewLazyLoadEvent.sortOrder = -1;
            this.dataViewLazyLoadEvent.sortField = value.substring(1, value.length);
        } else {
            this.dataViewLazyLoadEvent.sortOrder = 1;
            this.dataViewLazyLoadEvent.sortField = value;
        }
    }

    protected onLazyLoad(event: DataViewLazyLoadEvent) {
        this.dataViewLazyLoadEvent = event;
        this.loadElements();
    }

    protected onGlobalFilter(event: Event): void {
        this.loadElements((event.target as HTMLInputElement).value);
    }

    protected initials(name?: string | null): string {
        return (name ?? '').split(" ").slice(0, 2).map(s => s[0]).join(" ").toUpperCase();
    }

    protected addNew(): void {
        const dynamicDialogRef: DynamicDialogRef = this.dialogService.open(AddTenantsDialogComponent, {
            header: "Aggiungi tenant",
            closable: true,
            draggable: true,
            resizable: true,
            modal: true,
            width: '50vw',
            breakpoints: { '1199px': '75vw', '575px': '90vw' },
        });

        dynamicDialogRef.onClose.pipe(first()).subscribe((result: Tenants) => {
            if (result) {
                this.tenantsService.create(result).pipe(delay(1000), first()).subscribe({
                    next: (tenant: Tenants) => {
                        this.toastService.success("Successo", "Tenant aggiunto con successo");
                        this.loadElements();
                    }
                });
            }
        });
    }

    protected deleteElement(tenant: Tenants): void {
        this.confirmationService.confirm({
            header: 'Conferma eliminazione',
            message: 'Eliminare definitivamente questo tenant?',
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Elimina',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () => {
                this.tenantsService.delete(tenant.id).pipe(delay(1000), first()).subscribe({
                    next: (value: any) => {
                        this.toastService.success("Successo", "Tenant eliminato con successo");
                        this.loadElements();
                    }
                });
            },
        });
    }

    protected isSelected(item: Tenants): boolean {
        return this.selectedTenants.some(s => s.id === item.id);
    }

    protected isAllSelected(items: Tenants[]): boolean {
        return items.length > 0 && items.every(item => this.isSelected(item));
    }

    protected toggleSelection(item: Tenants): void {
        if (this.isSelected(item)) {
            this.selectedTenants = this.selectedTenants.filter(s => s.id !== item.id);
        } else {
            this.selectedTenants = [...this.selectedTenants, item];
        }
    }

    protected toggleSelectAll(items: Tenants[]): void {
        if (this.isAllSelected(items)) {
            this.selectedTenants = this.selectedTenants.filter(s => !items.some(item => item.id === s.id));
        } else {
            const notYetSelected = items.filter(item => !this.isSelected(item));
            this.selectedTenants = [...this.selectedTenants, ...notYetSelected];
        }
    }

    protected deleteSelectedElements(): void {
        const count = this.selectedTenants.length;
        this.confirmationService.confirm({
            header: 'Conferma eliminazione',
            message: `Eliminare definitivamente i ${count} tenant selezionati?`,
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Elimina',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () => {
                forkJoin(this.selectedTenants.map(item => this.tenantsService.delete(item.id))).pipe(delay(1000), first()).subscribe({
                    next: () => {
                        this.selectedTenants = [];
                        this.toastService.success('Successo', `${count} tenant eliminati con successo`);
                        this.loadElements();
                    }
                });
            },
        });
    }

    private loadElements(search?: string) {
        this.selectedTenants = [];
        const tenantsCriteria: TenantsCriteria = new TenantsCriteria();
        tenantsCriteria.page = this.dataViewLazyLoadEvent.first / this.dataViewLazyLoadEvent.rows;
        tenantsCriteria.size = this.dataViewLazyLoadEvent.rows;
        tenantsCriteria.sort = [`${this.dataViewLazyLoadEvent.sortField},${this.dataViewLazyLoadEvent.sortOrder > 0 ? "asc" : "desc"}`];

        if (search) {
            tenantsCriteria.name = new StringFilter();
            tenantsCriteria.name.contains = search;
        }

        this.tenantsService.getAll(tenantsCriteria).pipe(first()).subscribe({
            next: (value: Page<Tenants>) => {
                this.tenants = value.content;
                this.totalRecords = value.totalElements;
            }
        });
    }
}
