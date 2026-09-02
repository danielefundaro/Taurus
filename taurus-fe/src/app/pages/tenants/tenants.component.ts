import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { delay, finalize, first, forkJoin } from 'rxjs';
import { AddTenantsDialogComponent } from '../../dialogs/add-tenants-dialog/add-tenants-dialog.component';
import { ImportsModule } from '../../imports';
import { Page, Tenants, TenantsCriteria } from '../../module';
import { StringFilter } from '../../module/criteria/filter';
import { ConfirmService, ListLayout, ListLayoutService, TenantsService, ToastService, UsersService } from '../../service';
import { ListPageBase } from '../_shared/list-page.base';

@Component({
    selector: 'app-tenants',
    imports: [RouterModule, ImportsModule],
    templateUrl: './tenants.component.html',
    styleUrl: './tenants.component.scss',
    providers: [UsersService, DialogService]
})
export class TenantsComponent extends ListPageBase implements OnInit {
    protected sortOptions!: SelectItem[];
    protected layout: ListLayout = 'list';
    protected tenants: Tenants[];
    protected selectedTenants: Tenants[] = [];

    constructor(
        private readonly tenantsService: TenantsService,
        private readonly toastService: ToastService,
        private readonly dialogService: DialogService,
        private readonly confirmService: ConfirmService,
        private readonly listLayoutService: ListLayoutService
    ) {
        super();
        this.tenants = [];
    }

    ngOnInit() {
        this.sortOptions = [
            { label: 'Nome A-Z', value: 'name' },
            { label: 'Nome Z-A', value: '!name' }
        ];
        this.listLayoutService.observe('tenants', (value) => (this.layout = value));
    }

    protected onLayoutChange(value: ListLayout): void {
        this.layout = value;
        this.listLayoutService.set('tenants', value);
    }

    protected initials(name?: string | null): string {
        return (name ?? '')
            .split(' ')
            .slice(0, 2)
            .map((s) => s[0])
            .join(' ')
            .toUpperCase();
    }

    protected addNew(): void {
        const dynamicDialogRef: DynamicDialogRef = this.dialogService.open(AddTenantsDialogComponent, {
            header: 'Aggiungi tenant',
            closable: true,
            draggable: true,
            resizable: true,
            modal: true,
            width: '40rem',
            breakpoints: { '767px': 'calc(100vw - 2rem)' }
        });

        dynamicDialogRef.onClose.pipe(first()).subscribe((result: Tenants) => {
            if (result) {
                this.tenantsService
                    .create(result)
                    .pipe(delay(1000), first())
                    .subscribe({
                        next: (tenant: Tenants) => {
                            this.toastService.success('Successo', 'Tenant aggiunto con successo');
                            this.loadElements(this.searchTerm);
                        }
                    });
            }
        });
    }

    protected deleteElement(tenant: Tenants): void {
        this.confirmService.confirmDestructive({
            title: 'Elimina tenant',
            consequence: 'Il tenant verrà eliminato definitivamente.',
            actionLabel: 'Elimina',
            accept: () => {
                this.tenantsService
                    .delete(tenant.id)
                    .pipe(delay(1000), first())
                    .subscribe({
                        next: (value: any) => {
                            this.toastService.success('Successo', 'Tenant eliminato con successo');
                            this.loadElements(this.searchTerm);
                        }
                    });
            }
        });
    }

    protected isSelected(item: Tenants): boolean {
        return this.selectedTenants.some((s) => s.id === item.id);
    }

    protected isAllSelected(items: Tenants[]): boolean {
        return items.length > 0 && items.every((item) => this.isSelected(item));
    }

    protected toggleSelection(item: Tenants): void {
        if (this.isSelected(item)) {
            this.selectedTenants = this.selectedTenants.filter((s) => s.id !== item.id);
        } else {
            this.selectedTenants = [...this.selectedTenants, item];
        }
    }

    protected toggleSelectAll(items: Tenants[]): void {
        if (this.isAllSelected(items)) {
            this.selectedTenants = this.selectedTenants.filter((s) => !items.some((item) => item.id === s.id));
        } else {
            const notYetSelected = items.filter((item) => !this.isSelected(item));
            this.selectedTenants = [...this.selectedTenants, ...notYetSelected];
        }
    }

    protected deleteSelectedElements(): void {
        const count = this.selectedTenants.length;
        this.confirmService.confirmDestructive({
            title: 'Elimina tenant selezionati',
            consequence: `I ${count} tenant selezionati verranno eliminati definitivamente.`,
            actionLabel: 'Elimina',
            accept: () => {
                forkJoin(this.selectedTenants.map((item) => this.tenantsService.delete(item.id)))
                    .pipe(delay(1000), first())
                    .subscribe({
                        next: () => {
                            this.selectedTenants = [];
                            this.toastService.success('Successo', `${count} tenant eliminati con successo`);
                            this.loadElements(this.searchTerm);
                        }
                    });
            }
        });
    }

    protected loadElements(search?: string) {
        this.loading = true;
        this.selectedTenants = [];
        const tenantsCriteria: TenantsCriteria = new TenantsCriteria();
        tenantsCriteria.page = this.pageIndex;
        tenantsCriteria.size = this.pageSize;
        tenantsCriteria.sort = this.sortCriteria;

        if (search) {
            tenantsCriteria.name = new StringFilter();
            tenantsCriteria.name.contains = search;
        }

        this.tenantsService
            .getAll(tenantsCriteria)
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe({
                next: (value: Page<Tenants>) => {
                    this.tenants = value.content;
                    this.totalRecords = value.totalElements;
                }
            });
    }
}
