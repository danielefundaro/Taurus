import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { DataViewLazyLoadEvent } from 'primeng/dataview';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { SelectChangeEvent } from 'primeng/select';
import { delay, first, firstValueFrom } from 'rxjs';
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
        DialogService
    ]
})
export class UsersComponent implements OnInit {
    protected sortOptions!: SelectItem[];
    protected layout: 'list' | 'grid' = 'list';
    protected options = ['list', 'grid'];
    protected totalRecords: number = 0;
    protected dataViewLazyLoadEvent: DataViewLazyLoadEvent = { first: 0, rows: 5, sortField: 'name.keyword', sortOrder: 1 };
    protected users: Users[];

    private readonly instruments: Instruments[];

    constructor(
        private readonly usersService: UsersService,
        private readonly instrumentsService: InstrumentsService,
        private readonly toastService: ToastService,
        private readonly dialogService: DialogService,
    ) {
        this.users = [];
        this.instruments = [];

        // Preload all instruments
        const instrumentsCriteria: InstrumentsCriteria = { page: 0, sort: ['name.keyword,asc'] };
        this.preloadEntities(this.instrumentsService, instrumentsCriteria, this.instruments);
    }

    ngOnInit() {
        this.sortOptions = [
            { label: 'Name A-Z', value: 'name.keyword' },
            { label: 'Name Z-A', value: '!name.keyword' },
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

    protected deleteElement(user: Users) {
        this.usersService.delete(user.id).pipe(delay(1000), first()).subscribe({
            next: (value: any) => {
                this.toastService.success("Successo", "Utente eliminato con successo");
                this.loadElements();
            }
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
