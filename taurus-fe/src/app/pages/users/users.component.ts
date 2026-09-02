import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { delay, finalize, first, firstValueFrom, forkJoin } from 'rxjs';
import { AddUsersDialogComponent } from '../../dialogs/add-users-dialog/add-users-dialog.component';
import { ImportsModule } from '../../imports';
import { ChildrenEntities, CommonFieldsOpenSearch, CommonOpenSearchCriteria, Instruments, InstrumentsCriteria, Page, Users, UsersCriteria } from '../../module';
import { StringFilter } from '../../module/criteria/filter';
import { ConfirmService, InstrumentsService, ListLayout, ListLayoutService, ToastService, UsersService } from '../../service';
import { ListPageBase } from '../_shared/list-page.base';
import { CommonOpenSearchService } from '../../service/common-open-search.service';

@Component({
    selector: 'app-users',
    imports: [RouterModule, ImportsModule],
    templateUrl: './users.component.html',
    styleUrl: './users.component.scss',
    providers: [UsersService, DialogService]
})
export class UsersComponent extends ListPageBase implements OnInit {
    protected sortOptions!: SelectItem[];
    protected layout: ListLayout = 'list';
    protected users: Users[];
    protected selectedUsers: Users[] = [];

    private readonly instruments: Instruments[];

    constructor(
        private readonly usersService: UsersService,
        private readonly instrumentsService: InstrumentsService,
        private readonly toastService: ToastService,
        private readonly dialogService: DialogService,
        private readonly confirmService: ConfirmService,
        private readonly listLayoutService: ListLayoutService
    ) {
        super();
        this.users = [];
        this.instruments = [];

        // Preload all instruments
        const instrumentsCriteria: InstrumentsCriteria = { page: 0, sort: ['name,asc'] };
        this.preloadEntities(this.instrumentsService, instrumentsCriteria, this.instruments);
    }

    ngOnInit() {
        this.sortOptions = [
            { label: 'Nome A-Z', value: 'name' },
            { label: 'Nome Z-A', value: '!name' }
        ];
        this.listLayoutService.observe('users', (value) => (this.layout = value));
    }

    protected onLayoutChange(value: ListLayout): void {
        this.layout = value;
        this.listLayoutService.set('users', value);
    }

    protected addNew(): void {
        const dynamicDialogRef: DynamicDialogRef = this.dialogService.open(AddUsersDialogComponent, {
            header: 'Aggiungi utente',
            inputValues: {
                instruments: this.instruments
            },
            closable: true,
            draggable: true,
            resizable: true,
            modal: true,
            width: '40rem',
            breakpoints: { '767px': 'calc(100vw - 2rem)' }
        });

        dynamicDialogRef.onClose.pipe(first()).subscribe((result: Users) => {
            if (result) {
                this.usersService
                    .create(result)
                    .pipe(delay(1000), first())
                    .subscribe({
                        next: (user: Users) => {
                            this.toastService.success('Successo', 'Utente aggiunto con successo');
                            this.loadElements(this.searchTerm);
                        }
                    });
            }
        });
    }

    protected sendSetupEmail(user: Users): void {
        this.confirmService.confirmReversible({
            title: 'Invita utente',
            consequence: `Verrà inviata l'email di configurazione account a ${user.name} ${user.lastName}.`,
            actionLabel: 'Invia',
            accept: () => {
                this.usersService
                    .sendSetupEmail(user.id)
                    .pipe(first())
                    .subscribe({
                        next: () => this.toastService.success('Successo', 'Email di configurazione inviata')
                    });
            }
        });
    }

    protected deleteElement(user: Users): void {
        this.confirmService.confirmDestructive({
            title: 'Elimina utente',
            consequence: 'L’utente verrà rimosso dal tenant; i dati saranno conservati.',
            actionLabel: 'Elimina',
            accept: () => {
                this.usersService
                    .delete(user.id)
                    .pipe(delay(1000), first())
                    .subscribe({
                        next: (value: any) => {
                            this.toastService.success('Successo', 'Utente eliminato con successo');
                            this.loadElements(this.searchTerm);
                        }
                    });
            }
        });
    }

    protected visibleInstruments(user: Users): ChildrenEntities[] {
        return (user.instruments ?? []).slice(0, 3);
    }

    protected hiddenInstruments(user: Users): number {
        return Math.max((user.instruments?.length ?? 0) - 3, 0);
    }

    protected isSelected(item: Users): boolean {
        return this.selectedUsers.some((s) => s.id === item.id);
    }

    protected isAllSelected(items: Users[]): boolean {
        return items.length > 0 && items.every((item) => this.isSelected(item));
    }

    protected toggleSelection(item: Users): void {
        if (this.isSelected(item)) {
            this.selectedUsers = this.selectedUsers.filter((s) => s.id !== item.id);
        } else {
            this.selectedUsers = [...this.selectedUsers, item];
        }
    }

    protected toggleSelectAll(items: Users[]): void {
        if (this.isAllSelected(items)) {
            this.selectedUsers = this.selectedUsers.filter((s) => !items.some((item) => item.id === s.id));
        } else {
            const notYetSelected = items.filter((item) => !this.isSelected(item));
            this.selectedUsers = [...this.selectedUsers, ...notYetSelected];
        }
    }

    protected deleteSelectedElements(): void {
        const count = this.selectedUsers.length;
        this.confirmService.confirmDestructive({
            title: 'Elimina utenti selezionati',
            consequence: `I ${count} utenti selezionati verranno rimossi; i dati saranno conservati.`,
            actionLabel: 'Elimina',
            accept: () => {
                forkJoin(this.selectedUsers.map((item) => this.usersService.delete(item.id)))
                    .pipe(delay(1000), first())
                    .subscribe({
                        next: () => {
                            this.selectedUsers = [];
                            this.toastService.success('Successo', `${count} utenti eliminati con successo`);
                            this.loadElements(this.searchTerm);
                        }
                    });
            }
        });
    }

    private preloadEntities<T extends CommonFieldsOpenSearch, T1 extends CommonOpenSearchCriteria>(service: CommonOpenSearchService<T, T1>, criteria: T1, results: T[]): void {
        let page = criteria.page ?? 0;

        service
            .getAll()
            .pipe(first())
            .subscribe(async (result) => {
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

    protected loadElements(search?: string) {
        this.loading = true;
        this.selectedUsers = [];
        const usersCriteria: UsersCriteria = new UsersCriteria();
        usersCriteria.page = this.pageIndex;
        usersCriteria.size = this.pageSize;
        usersCriteria.sort = this.sortCriteria;

        if (search) {
            usersCriteria.name = new StringFilter();
            usersCriteria.name.contains = search;
        }

        this.usersService
            .getAll(usersCriteria)
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe({
                next: (value: Page<Users>) => {
                    this.users = value.content;
                    this.totalRecords = value.totalElements;
                }
            });
    }
}
