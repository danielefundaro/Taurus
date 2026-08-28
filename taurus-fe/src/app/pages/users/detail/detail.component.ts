import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmationService, SelectItem } from 'primeng/api';
import { AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { delay, finalize, first, firstValueFrom } from 'rxjs';
import { CalendarEventsTableComponent } from "../../../components/calendar-events-table/calendar-events-table.component";
import { InventoryAssignmentsComponent } from '../../../components/inventory-assignments/inventory-assignments.component';
import { RoleEnums } from '../../../constants';
import { HasUnsavedChanges } from '../../../guard';
import { ImportsModule } from '../../../imports';
import { ChildrenEntities, CommonFieldsOpenSearch, CommonOpenSearchCriteria, Instruments, InstrumentsCriteria, Users } from '../../../module';
import { EnumConverterPipe } from '../../../pipe';
import { InstrumentsService, ToastService, UsersService } from '../../../service';
import { CommonOpenSearchService } from '../../../service/common-open-search.service';

@Component({
    selector: 'app-user-detail',
    imports: [
        ImportsModule,
        CalendarEventsTableComponent,
        InventoryAssignmentsComponent
    ],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [
        InstrumentsService,
        ConfirmationService,
    ],
})
export class DetailComponent implements OnInit, HasUnsavedChanges {
    protected sortOptions!: SelectItem[];
    protected user: Users = new Users();
    protected selectedTracks: ChildrenEntities[];
    isDirty = false;
    isSaving = false;

    protected autoFilteredRoles: Array<string>;
    protected autoFilteredInstruments: ChildrenEntities[] = [];
    private instrumentsChildrenEntities: ChildrenEntities[] = [];

    private readonly roles: Array<RoleEnums>;
    private readonly instruments: Instruments[];

    constructor(
        private readonly usersService: UsersService,
        private readonly instrumentsService: InstrumentsService,
        private readonly toastService: ToastService,
        private readonly routeService: ActivatedRoute,
        private readonly router: Router,
        private readonly confirmationService: ConfirmationService,
        private readonly enumConverterPipe: EnumConverterPipe<RoleEnums>,
    ) {
        this.selectedTracks = [];

        this.roles = this.enumConverterPipe.transform(RoleEnums as unknown as RoleEnums);
        this.roles = this.roles.filter(role => role !== RoleEnums.SUPER_ADMIN);
        this.autoFilteredRoles = this.roles;

        this.instruments = [];

        // Preload all instruments
        const instrumentsCriteria: InstrumentsCriteria = { page: 0, sort: ['name,asc'] };
        this.preloadEntities(this.instrumentsService, instrumentsCriteria, this.instruments);
        this.autoFilteredInstruments = [];
        this.instrumentsChildrenEntities = [];
    }

    ngOnInit() {
        this.routeService.params.pipe(first()).subscribe(params => {
            this.loadElement(params['id']);
        });
    }

    protected sendSetupEmail(): void {
        this.confirmationService.confirm({
            header: 'Invita utente',
            message: 'Inviare l\'email di configurazione account a questo utente?',
            icon: 'pi pi-envelope',
            acceptLabel: 'Invia',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'primary' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () => {
                this.usersService.sendSetupEmail(this.user.id).pipe(first()).subscribe({
                    next: () => this.toastService.success('Successo', 'Email di configurazione inviata'),
                });
            },
        });
    }

    protected confirmDelete(): void {
        this.confirmationService.confirm({
            header: 'Disattiva utente',
            message: 'Vuoi disattivare questo utente?',
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Disattiva',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'warn' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () => {
                this.usersService.delete(this.user.id).pipe(first()).subscribe({
                    next: () => {
                        this.isDirty = false;
                        this.toastService.success('Successo', 'Utente eliminato');
                        this.router.navigate(['/users']);
                    },
                });
            },
        });
    }

    protected confirmGdprDeletion(): void {
        this.confirmationService.confirm({
            header: 'Cancellazione definitiva GDPR',
            message: "La cancellazione dell'utente ai sensi del GDPR è definitiva e irreversibile.</br>I dati personali associati a questo tenant verranno eliminati fisicamente e non sarà possibile recuperarli in futuro.</br>Vuoi procedere?",
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Elimina definitivamente',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () => {
                this.usersService.deleteForGdpr(this.user.id).pipe(first()).subscribe({
                    next: () => {
                        this.isDirty = false;
                        this.toastService.success('Successo', 'Utente eliminato definitivamente ai sensi del GDPR');
                        this.router.navigate(['/users']);
                    },
                });
            },
        });
    }

    protected save(): void {
        this.isSaving = true;
        this.usersService.update(this.user.id, this.user).pipe(delay(1000), first(), finalize(() => this.isSaving = false)).subscribe({
            next: (user: Users) => {
                this.isDirty = false;
                this.toastService.success("Successo", "Utente aggiornato con successo");
                this.loadElement(user.id);
            }
        });
    }

    protected filterRoles(event: AutoCompleteCompleteEvent) {
        this.autoFilteredRoles = this.roles.filter(role => role?.toLowerCase()?.includes(event.query.toLowerCase()));
    }

    protected filterInstruments(event: AutoCompleteCompleteEvent) {
        this.autoFilteredInstruments = this.instrumentsChildrenEntities.filter(instrument => instrument.name?.toLowerCase()?.includes(event.query.toLowerCase()));
    }

    protected onReorderInstruments(): void {
        this.user.instruments?.forEach((instrument, i) => instrument.order = i + 1);
        this.isDirty = true;
    }

    private loadElement(id: number | string) {
        this.usersService.getById(Number(id)).pipe(first()).subscribe({
            next: (user: Users) => {
                this.user = user;
                this.isDirty = false;
            }
        });
    }

    private preloadEntities<T extends CommonFieldsOpenSearch, T1 extends CommonOpenSearchCriteria>(service: CommonOpenSearchService<T, T1>, criteria: T1, results: T[]): void {
        let page = criteria.page ?? 0;

        service.getAll(criteria).pipe(first()).subscribe(async (result) => {
            let totalElements = result.totalElements;
            results.push(...result.content);

            while (totalElements > results.length) {
                criteria.page = ++page;

                const data = await firstValueFrom(service.getAll(criteria));
                results.push(...data.content);
                totalElements = data.totalElements;
            }

            this.instrumentsChildrenEntities = results.map(instrument => {
                const childrenEntity = new ChildrenEntities();
                childrenEntity.name = instrument.name;
                childrenEntity.index = instrument.id;

                return childrenEntity;
            }) ?? [];

            this.autoFilteredInstruments = this.instrumentsChildrenEntities;
        });
    }
}
