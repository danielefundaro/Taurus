import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmationService } from 'primeng/api';
import { AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { Table } from 'primeng/table';
import { delay, finalize, first } from 'rxjs';
import { RoleEnums, StateEnums } from '../../../constants';
import { HasUnsavedChanges } from '../../../guard/unsaved-changes.guard';
import { ImportsModule } from '../../../imports';
import { CalendarEvents, EventCost, EventPresentUser, Users } from '../../../module';
import { DateConverterPipe, EnumConverterPipe } from '../../../pipe';
import { CalendarEventsService, KeycloakService, ToastService, UsersService } from '../../../service';

interface UserPresenceRow {
    id: string;
    name: string;
    lastName: string;
    present: boolean;
    arrivalTime?: Date;
    note?: string;
}

@Component({
    selector: 'app-calendar-event-detail',
    imports: [
        ImportsModule,
    ],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [
        CalendarEventsService,
        ConfirmationService,
    ],
})
export class DetailComponent implements OnInit, HasUnsavedChanges {
    private _isDirtyForm = false;
    isDirtyPresence = false;
    protected isSaving = false;

    get isDirty(): boolean {
        return this._isDirtyForm || this.isDirtyPresence;
    }

    set isDirty(value: boolean) {
        this._isDirtyForm = value;
    }

    protected event: CalendarEvents = new CalendarEvents();
    protected autoFilteredStates: StateEnums[] = [];
    protected readonly RolesEnum: typeof RoleEnums = RoleEnums;

    protected newCostDescription: string = '';
    protected newCostAmount: number | null = null;

    protected presenceRows: UserPresenceRow[] = [];

    private readonly states: StateEnums[];

    constructor(
        private readonly calendarEventsService: CalendarEventsService,
        private readonly usersService: UsersService,
        private readonly keycloakService: KeycloakService,
        private readonly toastService: ToastService,
        private readonly activatedRoute: ActivatedRoute,
        private readonly router: Router,
        private readonly confirmationService: ConfirmationService,
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
        return this.keycloakService.isAdmin;
    }

    protected get isUser(): boolean {
        return this.keycloakService.isUser;
    }

    protected confirmDelete(): void {
        this.confirmationService.confirm({
            header: 'Conferma eliminazione',
            message: 'Eliminare definitivamente questo evento?',
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Conferma',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () => {
                this.isDirty = false;
                this.isDirtyPresence = false;
                this.calendarEventsService.delete(this.event.id).pipe(first()).subscribe({
                    next: () => {
                        this.toastService.success('Successo', 'Evento eliminato');
                        this.router.navigate(['/calendar']);
                    },
                });
            },
        });
    }

    protected save(): void {
        this.isSaving = true;
        this.calendarEventsService.update(this.event.id, this.event).pipe(delay(1000), first(), finalize(() => this.isSaving = false)).subscribe({
            next: (updated: CalendarEvents) => {
                this.isDirty = false;
                this.toastService.success('Successo', 'Evento aggiornato con successo');
                this.loadElement(updated.id);
            },
        });
    }

    protected get currentUserId(): string | undefined {
        return this.keycloakService.currentUserId;
    }

    protected get currentUserAvailability(): boolean | null {
        const id = this.currentUserId;
        if (!id) return null;
        if (this.event.availableUsers?.some(u => u.index === id)) return true;
        if (this.event.unavailableUsers?.some(u => u.index === id)) return false;
        return null;
    }

    protected setAvailability(available: boolean): void {
        this.calendarEventsService.setAvailability(this.event.id, available).pipe(delay(500), first()).subscribe({
            next: (updated: CalendarEvents) => {
                const msg = available ? 'Disponibilità confermata' : 'Non disponibile registrato';
                this.toastService.success('Successo', msg);
                this.updateEventDates(updated);
            },
        });
    }

    protected cancelAvailability(): void {
        this.calendarEventsService.cancelAvailability(this.event.id).pipe(delay(500), first()).subscribe({
            next: (updated: CalendarEvents) => {
                this.toastService.success('Successo', 'Disponibilità annullata');
                this.updateEventDates(updated);
            },
        });
    }

    protected onPresenceToggle(row: UserPresenceRow): void {
        if (row.present && !row.arrivalTime) {
            row.arrivalTime = new Date();
        }
        this.isDirtyPresence = true;
    }

    protected savePresentUsers(): void {
        const presentUsers: EventPresentUser[] = this.presenceRows
            .filter(r => r.present)
            .map(r => {
                const entry = new EventPresentUser();
                entry.index = r.id;
                entry.name = r.name;
                entry.lastName = r.lastName;
                entry.arrivalTime = r.arrivalTime;
                entry.note = r.note;
                return entry;
            });

        this.calendarEventsService.setPresentUsers(this.event.id, presentUsers).pipe(first()).subscribe({
            next: (updated: CalendarEvents) => {
                this.isDirtyPresence = false;
                this.toastService.success('Successo', 'Presenze salvate');
                this.updateEventDates(updated);
            },
        });
    }

    protected onGlobalFilter(table: Table, event: Event): void {
        table.filterGlobal((event.target as HTMLInputElement).value, 'contains');
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
        this.isDirty = true;
        this.newCostDescription = '';
        this.newCostAmount = null;
    }

    protected confirmRemoveCost(index: number): void {
        this.confirmationService.confirm({
            header: 'Conferma eliminazione',
            message: 'Rimuovere questo costo dall\'evento?',
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Rimuovi',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () => this.removeCost(index),
        });
    }

    protected removeCost(index: number): void {
        this.event.costs?.splice(index, 1);
        this.isDirty = true;
    }

    protected get totalCosts(): number {
        return (this.event.costs ?? []).reduce((sum, c) => sum + (c.amount ?? 0), 0);
    }

    private updateEventDates(updated: CalendarEvents): void {
        this.event = updated;
        this.event.startDate = this.dateConverterPipe.transform(this.event.startDate);
        this.event.endDate = this.dateConverterPipe.transform(this.event.endDate);
        if (this.isAdmin) {
            this.buildPresenceRows();
        }
    }

    private loadElement(id: string): void {
        this.calendarEventsService.getById(id).pipe(first()).subscribe({
            next: (ev: CalendarEvents) => {
                this.event = ev;
                this.isDirty = false;
                this.isDirtyPresence = false;
                this.event.startDate = this.dateConverterPipe.transform(this.event.startDate);
                this.event.endDate = this.dateConverterPipe.transform(this.event.endDate);
                if (this.isAdmin) {
                    this.loadAllUsers();
                }
            },
        });
    }

    private loadAllUsers(): void {
        this.usersService.getAll({ size: 1000, sort: ['name.keyword,asc'] } as any).pipe(first()).subscribe({
            next: page => {
                this.buildPresenceRows(page.content ?? []);
            },
        });
    }

    private buildPresenceRows(users?: Users[]): void {
        const source = users ?? this.presenceRows.map(r => ({
            id: r.id, name: r.name, lastName: r.lastName,
        } as any));

        this.presenceRows = source.map((u: Users) => {
            const existing = this.event.presentUsers?.find(p => p.index === u.id);
            return {
                id: u.id,
                name: u.name ?? '',
                lastName: u.lastName ?? '',
                present: !!existing,
                arrivalTime: existing?.arrivalTime
                    ? this.dateConverterPipe.transform(existing.arrivalTime as any)
                    : undefined,
                note: existing?.note,
            } as UserPresenceRow;
        });
    }
}
