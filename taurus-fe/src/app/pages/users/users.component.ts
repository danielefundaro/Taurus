import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ConfirmationService, SelectItem } from 'primeng/api';
import { DataViewLazyLoadEvent } from 'primeng/dataview';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { SelectChangeEvent } from 'primeng/select';
import { delay, first, firstValueFrom, forkJoin } from 'rxjs';
import { AddUsersDialogComponent } from '../../dialogs/add-users-dialog/add-users-dialog.component';
import { ImportsModule } from '../../imports';
import { CommonFieldsOpenSearch, CommonOpenSearchCriteria, Instruments, InstrumentsCriteria, Page, Users, UsersCriteria } from '../../module';
import { StringFilter } from '../../module/criteria/filter';
import { InstrumentsService, ToastService, UsersService } from '../../service';
import { CommonOpenSearchService } from '../../service/common-open-search.service';

@Component({
    selector: 'app-users',
    imports: [
        RouterModule,
        ImportsModule,
    ],
    templateUrl: './users.component.html',
    styleUrl: './users.component.scss',
    providers: [
        UsersService,
        DialogService,
        ConfirmationService,
    ]
})
export class UsersComponent implements OnInit {
    protected sortOptions!: SelectItem[];
    protected layout: 'list' | 'grid' = 'list';
    protected options = ['list', 'grid'];
    protected totalRecords: number = 0;
    protected dataViewLazyLoadEvent: DataViewLazyLoadEvent = { first: 0, rows: 10, sortField: 'name', sortOrder: 1 };
    protected users: Users[];
    protected selectedUsers: Users[] = [];

    private readonly instruments: Instruments[];

    constructor(
        private readonly usersService: UsersService,
        private readonly instrumentsService: InstrumentsService,
        private readonly toastService: ToastService,
        private readonly dialogService: DialogService,
        private readonly confirmationService: ConfirmationService,
    ) {
        this.users = [];
        this.instruments = [];

        // Preload all instruments
        const instrumentsCriteria: InstrumentsCriteria = { page: 0, sort: ['name,asc'] };
        this.preloadEntities(this.instrumentsService, instrumentsCriteria, this.instruments);
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

    protected addNew(): void {
        const dynamicDialogRef: DynamicDialogRef = this.dialogService.open(AddUsersDialogComponent, {
            header: "Aggiungi utente",
            inputValues: {
                instruments: this.instruments,
            },
            closable: true,
            draggable: true,
            resizable: true,
            modal: true,
            width: '50vw',
            breakpoints: { '1199px': '75vw', '575px': '90vw' },
        });

        dynamicDialogRef.onClose.pipe(first()).subscribe((result: Users) => {
            if (result) {
                this.usersService.create(result).pipe(delay(1000), first()).subscribe({
                    next: (user: Users) => {
                        this.toastService.success("Successo", "Utente aggiunto con successo");
                        this.loadElements();
                    }
                });
            }
        });
    }

    protected sendSetupEmail(user: Users): void {
        this.confirmationService.confirm({
            header: 'Invita utente',
            message: `Inviare l'email di configurazione account a ${user.name} ${user.lastName}?`,
            icon: 'pi pi-envelope',
            acceptLabel: 'Invia',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'primary' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () => {
                this.usersService.sendSetupEmail(user.id).pipe(first()).subscribe({
                    next: () => this.toastService.success('Successo', 'Email di configurazione inviata'),
                });
            },
        });
    }

    protected deleteElement(user: Users): void {
        this.confirmationService.confirm({
            header: 'Conferma eliminazione',
            message: 'Eliminare questo utente nel tenant? I dati verranno conservati.',
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Elimina',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () => {
                this.usersService.delete(user.id).pipe(delay(1000), first()).subscribe({
                    next: (value: any) => {
                        this.toastService.success("Successo", "Utente eliminato con successo");
                        this.loadElements();
                    }
                });
            },
        });
    }

    protected isSelected(item: Users): boolean {
        return this.selectedUsers.some(s => s.id === item.id);
    }

    protected isAllSelected(items: Users[]): boolean {
        return items.length > 0 && items.every(item => this.isSelected(item));
    }

    protected toggleSelection(item: Users): void {
        if (this.isSelected(item)) {
            this.selectedUsers = this.selectedUsers.filter(s => s.id !== item.id);
        } else {
            this.selectedUsers = [...this.selectedUsers, item];
        }
    }

    protected toggleSelectAll(items: Users[]): void {
        if (this.isAllSelected(items)) {
            this.selectedUsers = this.selectedUsers.filter(s => !items.some(item => item.id === s.id));
        } else {
            const notYetSelected = items.filter(item => !this.isSelected(item));
            this.selectedUsers = [...this.selectedUsers, ...notYetSelected];
        }
    }

    protected deleteSelectedElements(): void {
        const count = this.selectedUsers.length;
        this.confirmationService.confirm({
            header: 'Conferma eliminazione',
            message: `Eliminare i ${count} utenti selezionati? I dati verranno conservati.`,
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Elimina',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () => {
                forkJoin(this.selectedUsers.map(item => this.usersService.delete(item.id))).pipe(delay(1000), first()).subscribe({
                    next: () => {
                        this.selectedUsers = [];
                        this.toastService.success('Successo', `${count} utenti eliminati con successo`);
                        this.loadElements();
                    }
                });
            },
        });
    }

    private preloadEntities<T extends CommonFieldsOpenSearch, T1 extends CommonOpenSearchCriteria>(service: CommonOpenSearchService<T, T1>, criteria: T1, results: T[]): void {
        let page = criteria.page ?? 0;

        service.getAll().pipe(first()).subscribe(async (result) => {
            let totalElements = result.totalElements;
            results.push(...result.content);

            while (totalElements > results.length) {
                criteria.page = ++page;

                const data = await firstValueFrom(service.getAll(criteria));
                results.push(...data.content);
                totalElements = data.totalElements;
            }
        });
    }

    private loadElements(search?: string) {
        this.selectedUsers = [];
        const usersCriteria: UsersCriteria = new UsersCriteria();
        usersCriteria.page = this.dataViewLazyLoadEvent.first / this.dataViewLazyLoadEvent.rows;
        usersCriteria.size = this.dataViewLazyLoadEvent.rows;
        usersCriteria.sort = [`${this.dataViewLazyLoadEvent.sortField},${this.dataViewLazyLoadEvent.sortOrder > 0 ? "asc" : "desc"}`];

        if (search) {
            usersCriteria.name = new StringFilter();
            usersCriteria.name.contains = search;
        }

        this.usersService.getAll(usersCriteria).pipe(first()).subscribe({
            next: (value: Page<Users>) => {
                this.users = value.content;
                this.totalRecords = value.totalElements;
            }
        });
    }
}
