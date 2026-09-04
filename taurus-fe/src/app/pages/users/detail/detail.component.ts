import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { MultiSelectModule } from 'primeng/multiselect';
import { delay, finalize, first, firstValueFrom } from 'rxjs';
import { CalendarEventsTableComponent } from '../../../components/calendar-events-table/calendar-events-table.component';
import { InventoryAssignmentsComponent } from '../../../components/inventory-assignments/inventory-assignments.component';
import { RoleEnums, RoleLabel, RoleLabelsMap } from '../../../constants';
import { ImportsModule } from '../../../imports';
import { DetailPageBase } from '../../_shared/detail-page.base';
import { ChildrenEntities, CommonFieldsOpenSearch, CommonOpenSearchCriteria, Instruments, InstrumentsCriteria, Users } from '../../../module';
import { ConfirmService, InstrumentsService, TenantFeatureService, ToastService, UsersService } from '../../../service';
import { CommonOpenSearchService } from '../../../service/common-open-search.service';

@Component({
    selector: 'app-user-detail',
    imports: [ImportsModule, MultiSelectModule, CalendarEventsTableComponent, InventoryAssignmentsComponent],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [InstrumentsService]
})
export class DetailComponent extends DetailPageBase implements OnInit {
    protected sortOptions!: SelectItem[];
    protected user: Users = new Users();
    protected selectedTracks: ChildrenEntities[];

    protected readonly roleOptions: RoleLabel[];
    protected autoFilteredInstruments: ChildrenEntities[] = [];
    private instrumentsChildrenEntities: ChildrenEntities[] = [];

    private readonly instruments: Instruments[];

    constructor(
        private readonly usersService: UsersService,
        private readonly instrumentsService: InstrumentsService,
        private readonly toastService: ToastService,
        private readonly routeService: ActivatedRoute,
        private readonly router: Router,
        private readonly confirmService: ConfirmService,
        protected readonly tenantFeatureService: TenantFeatureService
    ) {
        super();
        this.selectedTracks = [];

        this.roleOptions = RoleLabelsMap.filter((role) => role.code !== RoleEnums.SUPER_ADMIN);

        this.instruments = [];

        // Preload all instruments
        const instrumentsCriteria: InstrumentsCriteria = { page: 0, sort: ['name,asc'] };
        this.preloadEntities(this.instrumentsService, instrumentsCriteria, this.instruments);
        this.autoFilteredInstruments = [];
        this.instrumentsChildrenEntities = [];
    }

    ngOnInit() {
        this.routeService.params.pipe(first()).subscribe((params) => {
            this.loadElement(params['id']);
        });
    }

    protected sendSetupEmail(): void {
        this.confirmService.confirmReversible({
            title: 'Invita utente',
            consequence: 'Verrà inviata l’email di configurazione account a questo utente.',
            actionLabel: 'Invia',
            accept: () => {
                this.usersService
                    .sendSetupEmail(this.user.id)
                    .pipe(first())
                    .subscribe({
                        next: () => this.toastService.success('Successo', 'Email di configurazione inviata')
                    });
            }
        });
    }

    protected confirmDelete(): void {
        this.confirmService.confirmReversible({
            title: 'Disattiva utente',
            consequence: 'L’utente non potrà più accedere finché non verrà riattivato.',
            actionLabel: 'Disattiva',
            accept: () => {
                this.usersService
                    .delete(this.user.id)
                    .pipe(first())
                    .subscribe({
                        next: () => {
                            this.isDirty = false;
                            this.toastService.success('Successo', 'Utente eliminato');
                            this.router.navigate(['/users']);
                        }
                    });
            }
        });
    }

    protected confirmGdprDeletion(): void {
        this.confirmService.confirmDestructive({
            title: 'Cancellazione definitiva GDPR',
            consequence: 'I dati personali associati a questo tenant verranno eliminati fisicamente e non potranno essere recuperati.',
            actionLabel: 'Elimina definitivamente',
            accept: () => {
                this.usersService
                    .deleteForGdpr(this.user.id)
                    .pipe(first())
                    .subscribe({
                        next: () => {
                            this.isDirty = false;
                            this.toastService.success('Successo', 'Utente eliminato definitivamente ai sensi del GDPR');
                            this.router.navigate(['/users']);
                        }
                    });
            }
        });
    }

    protected save(): void {
        this.saving = true;
        this.usersService
            .update(this.user.id, this.user)
            .pipe(
                delay(1000),
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe({
                next: (user: Users) => {
                    this.isDirty = false;
                    this.toastService.success('Successo', 'Utente aggiornato con successo');
                    this.loadElement(user.id);
                }
            });
    }

    protected filterInstruments(event: AutoCompleteCompleteEvent) {
        this.autoFilteredInstruments = this.instrumentsChildrenEntities.filter((instrument) => instrument.name?.toLowerCase()?.includes(event.query.toLowerCase()));
    }

    protected onReorderInstruments(): void {
        this.user.instruments?.forEach((instrument, i) => (instrument.order = i + 1));
        this.isDirty = true;
    }

    private loadElement(id: number | string) {
        this.usersService
            .getById(Number(id))
            .pipe(first())
            .subscribe({
                next: (user: Users) => {
                    this.user = user;
                    this.isDirty = false;
                }
            });
    }

    private preloadEntities<T extends CommonFieldsOpenSearch, T1 extends CommonOpenSearchCriteria>(service: CommonOpenSearchService<T, T1>, criteria: T1, results: T[]): void {
        let page = criteria.page ?? 0;

        service
            .getAll(criteria)
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

                this.instrumentsChildrenEntities =
                    results.map((instrument) => {
                        const childrenEntity = new ChildrenEntities();
                        childrenEntity.name = instrument.name;
                        childrenEntity.index = instrument.id;

                        return childrenEntity;
                    }) ?? [];

                this.autoFilteredInstruments = this.instrumentsChildrenEntities;
            });
    }
}
