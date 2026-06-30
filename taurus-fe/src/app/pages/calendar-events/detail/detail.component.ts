import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { first, delay } from 'rxjs';
import { RoleEnums, StateEnums } from '../../../constants';
import { ImportsModule } from '../../../imports';
import { CalendarEvents, EventCost } from '../../../module';
import { DateConverterPipe, EnumConverterPipe } from '../../../pipe';
import { CalendarEventsService, KeycloakService, ToastService } from '../../../service';

@Component({
    selector: 'app-calendar-event-detail',
    imports: [
        ImportsModule,
    ],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [
        CalendarEventsService,
    ],
})
export class DetailComponent implements OnInit {
    protected event: CalendarEvents = new CalendarEvents();
    protected autoFilteredStates: StateEnums[] = [];
    protected readonly RolesEnum: typeof RoleEnums = RoleEnums;

    protected newCostDescription: string = '';
    protected newCostAmount: number | null = null;

    private readonly states: StateEnums[];

    constructor(
        private readonly calendarEventsService: CalendarEventsService,
        private readonly keycloakService: KeycloakService,
        private readonly toastService: ToastService,
        private readonly activatedRoute: ActivatedRoute,
        private readonly dateConverterPipe: DateConverterPipe,
        private readonly enumConverterPipe: EnumConverterPipe<StateEnums>,
    ) {
        this.states = this.enumConverterPipe.transform(StateEnums as unknown as StateEnums);
        this.autoFilteredStates = this.states;
    }

    ngOnInit(): void {
        this.activatedRoute.params.pipe(first()).subscribe(params => {
            this.loadElement(params['id']);
        });
    }

    protected get isAdmin(): boolean {
        return [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN].includes(this.keycloakService.currentUserRole);
    }

    protected get isUser(): boolean {
        return this.keycloakService.currentUserRole === RoleEnums.USER;
    }

    protected save(): void {
        this.calendarEventsService.update(this.event.id, this.event).pipe(delay(1000), first()).subscribe({
            next: (updated: CalendarEvents) => {
                this.toastService.success('Successo', 'Evento aggiornato con successo');
                this.loadElement(updated.id);
            },
        });
    }

    protected setAvailability(available: boolean): void {
        this.calendarEventsService.setAvailability(this.event.id, available).pipe(delay(500), first()).subscribe({
            next: (updated: CalendarEvents) => {
                const msg = available ? 'Disponibilità confermata' : 'Non disponibile registrato';
                this.toastService.success('Successo', msg);
                this.event = updated;
            },
        });
    }

    protected filterStates(event: AutoCompleteCompleteEvent): void {
        this.autoFilteredStates = this.states.filter(s =>
            s?.toLowerCase().includes(event.query.toLowerCase()),
        );
    }

    protected addCost(): void {
        if (!this.newCostDescription) return;
        this.event.costs ??= [];
        const cost = new EventCost();
        cost.description = this.newCostDescription;
        cost.amount = this.newCostAmount ?? undefined;
        this.event.costs.push(cost);
        this.newCostDescription = '';
        this.newCostAmount = null;
    }

    protected removeCost(index: number): void {
        this.event.costs?.splice(index, 1);
    }

    private loadElement(id: string): void {
        this.calendarEventsService.getById(id).pipe(first()).subscribe({
            next: (ev: CalendarEvents) => {
                this.event = ev;
                this.event.startDate = this.dateConverterPipe.transform(this.event.startDate);
                this.event.endDate = this.dateConverterPipe.transform(this.event.endDate);
            },
        });
    }
}
