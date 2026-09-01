import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmationService } from 'primeng/api';
import { AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { Table } from 'primeng/table';
import { delay, finalize, first } from 'rxjs';
import { RoleEnums, StateLabel, StateLabelsMap } from '../../../constants';
import { HasUnsavedChanges } from '../../../guard';
import { ImportsModule } from '../../../imports';
import {
    CalendarEventSeries,
    CalendarEventSeriesPreview,
    CalendarEventSeriesRequest,
    CalendarEvents,
    EventCost,
    EventPresentUser,
    RecurrenceWeekDay,
    Users,
} from '../../../module';
import { DateConverterPipe } from '../../../pipe';
import { CalendarEventSeriesService, CalendarEventsService, KeycloakService, ToastService, UsersService } from '../../../service';

interface UserPresenceRow {
    id: number;
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
        CalendarEventSeriesService,
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
    protected autoFilteredStatesLabels: StateLabel[];
    protected readonly RolesEnum: typeof RoleEnums = RoleEnums;

    protected newCostDescription: string = '';
    protected newCostAmount: number | null = null;

    protected presenceRows: UserPresenceRow[] = [];
    protected currentUserId?: number;
    protected series?: CalendarEventSeries;
    protected seriesUntilDate?: Date;
    protected seriesPreview?: CalendarEventSeriesPreview;
    protected isPreviewingSeries = false;
    protected applyAvailabilityToFuture = false;
    protected readonly weekDayOptions: { code: RecurrenceWeekDay; label: string }[] = [
        { code: 'MO', label: 'Lun' },
        { code: 'TU', label: 'Mar' },
        { code: 'WE', label: 'Mer' },
        { code: 'TH', label: 'Gio' },
        { code: 'FR', label: 'Ven' },
        { code: 'SA', label: 'Sab' },
        { code: 'SU', label: 'Dom' },
    ];
    protected readonly frequencyOptions = [
        { value: 'DAILY', label: 'Giornaliera' },
        { value: 'WEEKLY', label: 'Settimanale' },
        { value: 'MONTHLY', label: 'Mensile' },
        { value: 'YEARLY', label: 'Annuale' },
    ];

    constructor(
        private readonly calendarEventsService: CalendarEventsService,
        private readonly calendarEventSeriesService: CalendarEventSeriesService,
        private readonly usersService: UsersService,
        private readonly keycloakService: KeycloakService,
        private readonly toastService: ToastService,
        private readonly activatedRoute: ActivatedRoute,
        private readonly router: Router,
        private readonly confirmationService: ConfirmationService,
        private readonly dateConverterPipe: DateConverterPipe,
    ) {
        this.autoFilteredStatesLabels = StateLabelsMap;
    }

    ngOnInit(): void {
        this.activatedRoute.params.pipe(first()).subscribe(params => {
            this.loadElement(params['id']);
        });
        this.usersService.getOwn().pipe(first()).subscribe(user => this.currentUserId = user.id);
    }

    protected get isAdmin(): boolean {
        return this.keycloakService.isAdmin;
    }

    protected get isUser(): boolean {
        return this.keycloakService.isUser;
    }

    protected get recurrenceLabel(): string {
        if (!this.series) return '';
        const labels: Record<string, [string, string]> = {
            DAILY: ['giorno', 'giorni'],
            WEEKLY: ['settimana', 'settimane'],
            MONTHLY: ['mese', 'mesi'],
            YEARLY: ['anno', 'anni'],
        };
        const interval = this.series.recurrence.interval;
        const frequencyLabels = labels[this.series.recurrence.frequency];
        const cadence = interval === 1 ? `ogni ${frequencyLabels[0]}` : `ogni ${interval} ${frequencyLabels[1]}`;
        const end = this.series.recurrence.end.type === 'COUNT'
            ? `${this.series.recurrence.end.count} occorrenze`
            : `fino al ${this.series.recurrence.end.until}`;
        return `${cadence}, ${end} · ${this.series.timeZone}`;
    }

    protected confirmDelete(): void {
        this.confirmationService.confirm({
            header: 'Conferma eliminazione',
            message: this.event.seriesId
                ? 'Eliminare soltanto questa occorrenza? Le altre date della serie non saranno modificate.'
                : 'Eliminare definitivamente questo evento?',
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

    protected saveSeriesFuture(): void {
        if (!this.series || this.seriesRuleInvalid) return;
        const request = this.buildSeriesRequest();
        this.isSaving = true;
        this.calendarEventSeriesService.update(this.series.id, request).pipe(first(), finalize(() => this.isSaving = false)).subscribe({
            next: updated => {
                this.series = updated;
                this.isDirty = false;
                this.toastService.success('Successo', `${updated.updatedCount ?? 0} eventi aggiornati da questa occorrenza in poi`);
                this.loadElement(this.event.id);
            },
        });
    }

    protected onSeriesDefinitionChange(): void {
        this.isDirty = true;
        this.seriesPreview = undefined;
    }

    protected onEventScheduleChange(): void {
        this.isDirty = true;
        this.seriesPreview = undefined;
    }

    protected isSeriesWeekDaySelected(day: RecurrenceWeekDay): boolean {
        return this.series?.recurrence.weekDays.includes(day) ?? false;
    }

    protected toggleSeriesWeekDay(day: RecurrenceWeekDay, selected: boolean): void {
        if (!this.series) return;
        const days = this.series.recurrence.weekDays;
        this.series.recurrence.weekDays = selected
            ? [...new Set([...days, day])]
            : days.filter(value => value !== day);
        this.onSeriesDefinitionChange();
    }

    protected previewSeriesUpdate(): void {
        if (!this.series || this.seriesRuleInvalid) return;
        this.isPreviewingSeries = true;
        this.calendarEventSeriesService.preview(this.buildSeriesRequest()).pipe(first(), finalize(() => this.isPreviewingSeries = false)).subscribe({
            next: result => this.seriesPreview = result,
        });
    }

    protected get seriesRuleInvalid(): boolean {
        if (!this.series) return false;
        const rule = this.series.recurrence;
        if (!this.event.startDate || !rule.interval || rule.interval < 1) return true;
        if (rule.frequency === 'WEEKLY' && rule.weekDays.length === 0) return true;
        if (rule.end.type === 'COUNT') {
            return !rule.end.count || rule.end.count < 1 || rule.end.count > 500;
        }
        return !this.seriesUntilDate;
    }

    protected restoreOccurrence(): void {
        if (!this.event.seriesId) return;
        this.calendarEventSeriesService.restoreOccurrence(this.event.seriesId, this.event.id).pipe(first()).subscribe({
            next: updated => {
                this.series = updated;
                this.toastService.success('Successo', 'Occorrenza ripristinata dai valori della serie');
                this.loadElement(this.event.id);
            },
        });
    }

    protected confirmDeleteSeries(): void {
        if (!this.event.seriesId) return;
        this.confirmationService.confirm({
            header: 'Elimina eventi futuri',
            message: 'Eliminare tutte le occorrenze future della serie? Gli eventi passati resteranno nello storico.',
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Elimina eventi futuri',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () => this.calendarEventSeriesService.deleteFuture(this.event.seriesId!).pipe(first()).subscribe({
                next: result => {
                    this.isDirty = false;
                    this.toastService.success('Successo', `${result.deletedCount ?? 0} eventi futuri eliminati`);
                    this.router.navigate(['/calendar']);
                },
            }),
        });
    }

    protected get currentUserAvailability(): boolean | null {
        const id = this.currentUserId;
        if (!id) return null;
        if (this.event.availableUsers?.some(u => u.index === id)) return true;
        if (this.event.unavailableUsers?.some(u => u.index === id)) return false;
        return null;
    }

    protected setAvailability(available: boolean): void {
        if (this.applyAvailabilityToFuture && this.event.seriesId) {
            this.calendarEventsService.setSeriesAvailability(this.event.seriesId, available).pipe(first()).subscribe({
                next: result => {
                    const msg = available ? 'Disponibilità confermata' : 'Non disponibilità registrata';
                    this.toastService.success('Successo', `${msg} per ${result.affectedOccurrences} eventi futuri`);
                    this.loadElement(this.event.id);
                },
            });
            return;
        }
        this.calendarEventsService.setAvailability(this.event.id, available).pipe(delay(500), first()).subscribe({
            next: (updated: CalendarEvents) => {
                const msg = available ? 'Disponibilità confermata' : 'Non disponibile registrato';
                this.toastService.success('Successo', msg);
                this.updateEventDates(updated);
            },
        });
    }

    protected cancelAvailability(): void {
        if (this.applyAvailabilityToFuture && this.event.seriesId) {
            this.calendarEventsService.cancelSeriesAvailability(this.event.seriesId).pipe(first()).subscribe({
                next: result => {
                    this.toastService.success('Successo', `Disponibilità annullata per ${result.affectedOccurrences} eventi futuri`);
                    this.loadElement(this.event.id);
                },
            });
            return;
        }
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
        this.autoFilteredStatesLabels = StateLabelsMap.filter(state => state.name.toLowerCase().includes(event.query.toLowerCase()) ? state : null).filter(state => state !== null) as StateLabel[];
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

    private loadElement(id: number | string): void {
        this.calendarEventsService.getById(Number(id)).pipe(first()).subscribe({
            next: (ev: CalendarEvents) => {
                this.event = ev;
                this.isDirty = false;
                this.isDirtyPresence = false;
                this.event.startDate = this.dateConverterPipe.transform(this.event.startDate);
                this.event.endDate = this.dateConverterPipe.transform(this.event.endDate);
                if (this.event.seriesId && this.isAdmin) {
                    this.loadSeries(this.event.seriesId);
                } else {
                    this.series = undefined;
                }
                if (this.isAdmin) {
                    this.loadAllUsers();
                }
            },
        });
    }

    private loadSeries(seriesId: number): void {
        this.calendarEventSeriesService.get(seriesId).pipe(first()).subscribe({
            next: series => {
                series.template.startDate = this.dateConverterPipe.transform(series.template.startDate);
                series.template.endDate = this.dateConverterPipe.transform(series.template.endDate);
                this.series = series;
                this.seriesUntilDate = series.recurrence.end.until
                    ? new Date(`${series.recurrence.end.until}T00:00:00`)
                    : undefined;
                this.seriesPreview = undefined;
            },
        });
    }

    private buildSeriesRequest(): CalendarEventSeriesRequest {
        const series = this.series!;
        const propagatedDates = this.propagatedSeriesDates(series);
        const recurrence = {
            ...series.recurrence,
            weekDays: series.recurrence.frequency === 'WEEKLY' ? series.recurrence.weekDays : [],
            end: series.recurrence.end.type === 'COUNT'
                ? { type: 'COUNT' as const, count: series.recurrence.end.count }
                : { type: 'UNTIL' as const, until: this.formatLocalDate(this.seriesUntilDate!) },
        };
        return {
            entityVersion: series.entityVersion,
            sourceOccurrenceId: this.event.id,
            template: {
                ...series.template,
                name: this.event.name,
                description: this.event.description,
                state: this.event.state,
                startDate: propagatedDates.startDate,
                endDate: propagatedDates.endDate,
                location: this.event.location,
                fee: this.event.fee,
                reminderMinutes: this.event.reminderMinutes,
                costs: this.event.costs,
            } as CalendarEvents,
            recurrence,
        };
    }

    private propagatedSeriesDates(series: CalendarEventSeries): { startDate?: Date; endDate?: Date } {
        if (!this.event.startDate) return {};

        const eventStart = new Date(this.event.startDate);
        const originalOccurrenceStart = this.event.originalStartDate
            ? new Date(this.event.originalStartDate)
            : undefined;
        const seriesStart = series.template.startDate
            ? new Date(series.template.startDate)
            : undefined;

        if (!originalOccurrenceStart || !seriesStart) {
            return { startDate: eventStart, endDate: this.event.endDate };
        }

        const occurrenceShift = eventStart.getTime() - originalOccurrenceStart.getTime();
        const propagatedStart = new Date(seriesStart.getTime() + occurrenceShift);
        const duration = this.event.endDate
            ? new Date(this.event.endDate).getTime() - eventStart.getTime()
            : undefined;

        return {
            startDate: propagatedStart,
            endDate: duration === undefined ? undefined : new Date(propagatedStart.getTime() + duration),
        };
    }

    private formatLocalDate(value: Date): string {
        const year = value.getFullYear();
        const month = String(value.getMonth() + 1).padStart(2, '0');
        const day = String(value.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    }

    private loadAllUsers(): void {
        this.usersService.getAll({ size: 1000, sort: ['name,asc'] } as any).pipe(first()).subscribe({
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
