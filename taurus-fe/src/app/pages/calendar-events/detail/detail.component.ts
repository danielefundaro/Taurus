import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { Table } from 'primeng/table';
import { delay, finalize, first } from 'rxjs';
import { RoleEnums, StateLabel, StateLabelsMap } from '../../../constants';
import { DangerZoneOperation } from '../../../components/danger-zone/danger-zone.component';
import { ImportsModule } from '../../../imports';
import { DetailPageBase } from '../../_shared/detail-page.base';
import { CalendarEventSeries, CalendarEventSeriesPreview, CalendarEventSeriesRequest, CalendarEvents, EventCost, FinancialEventSummary, EventPresentUser, RecurrenceWeekDay, Users } from '../../../module';
import { DateConverterPipe } from '../../../pipe';
import { CalendarEventSeriesService, CalendarEventsService, ConfirmService, FinanceService, KeycloakService, TenantFeatureService, ToastService, UsersService } from '../../../service';

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
    imports: [ImportsModule],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [CalendarEventsService, CalendarEventSeriesService]
})
export class DetailComponent extends DetailPageBase implements OnInit {
    protected isSavingPresence = false;

    /** Seconda unità salvabile della pagina, con il proprio pulsante «Salva presenze». */
    get isDirtyPresence(): boolean {
        return this.isUnitDirty(DetailComponent.PRESENCE_UNIT);
    }

    set isDirtyPresence(value: boolean) {
        this.setUnitDirty(DetailComponent.PRESENCE_UNIT, value);
    }

    private static readonly PRESENCE_UNIT = 'presenze';

    protected get dangerZoneOperations(): DangerZoneOperation[] {
        const deleteEvent: DangerZoneOperation = {
            id: 'event',
            title: 'Elimina evento',
            consequence: 'Questo evento e tutti i dati associati verranno eliminati definitivamente.',
            label: 'Elimina evento'
        };

        if (!this.event.seriesId) return [deleteEvent];

        return [
            deleteEvent,
            {
                id: 'series',
                title: 'Elimina serie futura',
                consequence: 'Le occorrenze future verranno eliminate conservando lo storico passato.',
                label: 'Elimina serie futura'
            }
        ];
    }

    protected event: CalendarEvents = new CalendarEvents();
    protected autoFilteredStatesLabels: StateLabel[];
    protected readonly RolesEnum: typeof RoleEnums = RoleEnums;

    protected newCostDescription: string = '';
    protected newCostAmount: number | null = null;
    protected economicSummary?: FinancialEventSummary;
    protected readonly financeEnabled;

    protected presenceRows: UserPresenceRow[] = [];
    protected currentUserId?: number;
    protected series?: CalendarEventSeries;
    protected seriesUntilDate?: Date;
    protected seriesPreview?: CalendarEventSeriesPreview;
    protected isPreviewingSeries = false;
    protected applyAvailabilityToFuture = false;

    /** Promemoria personale: undefined usa quello dell'evento, 0 lo disattiva. */
    protected personalReminderMinutes?: number;
    protected savingReminder = false;
    private savedPersonalReminderMinutes?: number;
    protected readonly reminderUnit = 'promemoria';
    protected readonly weekDayOptions: { code: RecurrenceWeekDay; label: string }[] = [
        { code: 'MO', label: 'Lun' },
        { code: 'TU', label: 'Mar' },
        { code: 'WE', label: 'Mer' },
        { code: 'TH', label: 'Gio' },
        { code: 'FR', label: 'Ven' },
        { code: 'SA', label: 'Sab' },
        { code: 'SU', label: 'Dom' }
    ];
    protected readonly frequencyOptions = [
        { value: 'DAILY', label: 'Giornaliera' },
        { value: 'WEEKLY', label: 'Settimanale' },
        { value: 'MONTHLY', label: 'Mensile' },
        { value: 'YEARLY', label: 'Annuale' }
    ];

    constructor(
        private readonly calendarEventsService: CalendarEventsService,
        private readonly calendarEventSeriesService: CalendarEventSeriesService,
        private readonly financeService: FinanceService,
        private readonly usersService: UsersService,
        private readonly keycloakService: KeycloakService,
        private readonly toastService: ToastService,
        private readonly activatedRoute: ActivatedRoute,
        private readonly router: Router,
        private readonly confirmService: ConfirmService,
        private readonly dateConverterPipe: DateConverterPipe,
        tenantFeatureService: TenantFeatureService
    ) {
        super();
        this.financeEnabled = tenantFeatureService.financeEnabled;
        this.autoFilteredStatesLabels = StateLabelsMap;
    }

    ngOnInit(): void {
        this.activatedRoute.params.pipe(first()).subscribe((params) => {
            this.loadElement(params['id']);
        });
        this.usersService
            .getOwn()
            .pipe(first())
            .subscribe((user) => (this.currentUserId = user.id));
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
            YEARLY: ['anno', 'anni']
        };
        const interval = this.series.recurrence.interval;
        const frequencyLabels = labels[this.series.recurrence.frequency];
        const cadence = interval === 1 ? `ogni ${frequencyLabels[0]}` : `ogni ${interval} ${frequencyLabels[1]}`;
        const end = this.series.recurrence.end.type === 'COUNT' ? `${this.series.recurrence.end.count} occorrenze` : `fino al ${this.series.recurrence.end.until}`;
        return `${cadence}, ${end} · ${this.series.timeZone}`;
    }

    protected confirmDelete(): void {
        this.confirmService.confirmDestructive({
            title: this.event.seriesId ? 'Elimina occorrenza' : 'Elimina evento',
            consequence: this.event.seriesId ? 'Soltanto questa occorrenza verrà eliminata; le altre date della serie non saranno modificate.' : 'L’evento verrà eliminato definitivamente.',
            actionLabel: 'Elimina',
            accept: () => {
                this.clearDirtyUnits();
                this.calendarEventsService
                    .delete(this.event.id)
                    .pipe(first())
                    .subscribe({
                        next: () => {
                            this.toastService.success('Successo', 'Evento eliminato');
                            this.router.navigate(['/calendar']);
                        }
                    });
            }
        });
    }

    protected save(): void {
        this.saving = true;
        this.calendarEventsService
            .update(this.event.id, this.event)
            .pipe(
                delay(1000),
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe({
                next: (updated: CalendarEvents) => {
                    this.isDirty = false;
                    this.toastService.success('Successo', 'Evento aggiornato con successo');
                    if (this.isDirtyPresence) {
                        this.toastService.info('Presenze non salvate', 'Le presenze modificate restano da salvare con «Salva presenze».');
                    }
                    this.loadElement(updated.id);
                }
            });
    }

    protected saveSeriesFuture(): void {
        if (!this.series || this.seriesRuleInvalid) return;
        const request = this.buildSeriesRequest();
        this.saving = true;
        this.calendarEventSeriesService
            .update(this.series.id, request)
            .pipe(
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe({
                next: (updated) => {
                    this.series = updated;
                    this.isDirty = false;
                    this.toastService.success('Successo', `${updated.updatedCount ?? 0} eventi aggiornati da questa occorrenza in poi`);
                    this.loadElement(this.event.id);
                }
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
        this.series.recurrence.weekDays = selected ? [...new Set([...days, day])] : days.filter((value) => value !== day);
        this.onSeriesDefinitionChange();
    }

    protected previewSeriesUpdate(): void {
        if (!this.series || this.seriesRuleInvalid) return;
        this.isPreviewingSeries = true;
        this.calendarEventSeriesService
            .preview(this.buildSeriesRequest())
            .pipe(
                first(),
                finalize(() => (this.isPreviewingSeries = false))
            )
            .subscribe({
                next: (result) => (this.seriesPreview = result)
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
        this.calendarEventSeriesService
            .restoreOccurrence(this.event.seriesId, this.event.id)
            .pipe(first())
            .subscribe({
                next: (updated) => {
                    this.series = updated;
                    this.toastService.success('Successo', 'Occorrenza ripristinata dai valori della serie');
                    this.loadElement(this.event.id);
                }
            });
    }

    protected confirmDeleteSeries(): void {
        if (!this.event.seriesId) return;
        this.confirmService.confirmDestructive({
            title: 'Elimina eventi futuri',
            consequence: 'Tutte le occorrenze future della serie verranno eliminate; gli eventi passati resteranno nello storico.',
            actionLabel: 'Elimina eventi futuri',
            accept: () =>
                this.calendarEventSeriesService
                    .deleteFuture(this.event.seriesId!)
                    .pipe(first())
                    .subscribe({
                        next: (result) => {
                            this.isDirty = false;
                            this.toastService.success('Successo', `${result.deletedCount ?? 0} eventi futuri eliminati`);
                            this.router.navigate(['/calendar']);
                        }
                    })
        });
    }

    protected get currentUserAvailability(): boolean | null {
        const id = this.currentUserId;
        if (!id) return null;
        if (this.event.availableUsers?.some((u) => u.index === id)) return true;
        if (this.event.unavailableUsers?.some((u) => u.index === id)) return false;
        return null;
    }

    protected get reminderDirty(): boolean {
        return (this.personalReminderMinutes ?? null) !== (this.savedPersonalReminderMinutes ?? null);
    }

    protected onReminderChange(): void {
        this.setUnitDirty(this.reminderUnit, this.reminderDirty);
    }

    protected clearPersonalReminder(): void {
        this.personalReminderMinutes = undefined;
        this.onReminderChange();
    }

    protected saveReminder(): void {
        if (!this.event?.id) return;
        this.savingReminder = true;
        this.calendarEventsService
            .setReminder(this.event.id, this.personalReminderMinutes)
            .pipe(first())
            .subscribe({
                next: () => {
                    this.savedPersonalReminderMinutes = this.personalReminderMinutes;
                    this.savingReminder = false;
                    this.setUnitDirty(this.reminderUnit, false);
                    this.toastService.success('Promemoria aggiornato', this.personalReminderMinutes === 0 ? 'Non riceverai promemoria per questo evento.' : 'Il promemoria vale solo per te.');
                },
                error: () => {
                    this.savingReminder = false;
                    this.toastService.error('Salvataggio non riuscito', 'Non è stato possibile salvare il promemoria personale.');
                }
            });
    }

    private loadPersonalReminder(): void {
        if (!this.event?.id || this.currentUserAvailability !== true) {
            this.personalReminderMinutes = undefined;
            this.savedPersonalReminderMinutes = undefined;
            this.setUnitDirty(this.reminderUnit, false);
            return;
        }
        this.calendarEventsService
            .getReminder(this.event.id)
            .pipe(first())
            .subscribe({
                next: (minutes) => {
                    this.personalReminderMinutes = minutes ?? undefined;
                    this.savedPersonalReminderMinutes = this.personalReminderMinutes;
                    this.setUnitDirty(this.reminderUnit, false);
                },
                error: () => undefined
            });
    }

    protected setAvailability(available: boolean): void {
        if (this.applyAvailabilityToFuture && this.event.seriesId) {
            this.calendarEventsService
                .setSeriesAvailability(this.event.seriesId, available)
                .pipe(first())
                .subscribe({
                    next: (result) => {
                        const msg = available ? 'Disponibilità confermata' : 'Non disponibilità registrata';
                        this.toastService.success('Successo', `${msg} per ${result.affectedOccurrences} eventi futuri`);
                        this.loadElement(this.event.id);
                    }
                });
            return;
        }
        this.calendarEventsService
            .setAvailability(this.event.id, available)
            .pipe(delay(500), first())
            .subscribe({
                next: (updated: CalendarEvents) => {
                    const msg = available ? 'Disponibilità confermata' : 'Non disponibile registrato';
                    this.toastService.success('Successo', msg);
                    this.updateEventDates(updated);
                    this.loadPersonalReminder();
                }
            });
    }

    protected cancelAvailability(): void {
        if (this.applyAvailabilityToFuture && this.event.seriesId) {
            this.calendarEventsService
                .cancelSeriesAvailability(this.event.seriesId)
                .pipe(first())
                .subscribe({
                    next: (result) => {
                        this.toastService.success('Successo', `Disponibilità annullata per ${result.affectedOccurrences} eventi futuri`);
                        this.loadElement(this.event.id);
                    }
                });
            return;
        }
        this.calendarEventsService
            .cancelAvailability(this.event.id)
            .pipe(delay(500), first())
            .subscribe({
                next: (updated: CalendarEvents) => {
                    this.toastService.success('Successo', 'Disponibilità annullata');
                    this.updateEventDates(updated);
                    this.loadPersonalReminder();
                }
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
            .filter((r) => r.present)
            .map((r) => {
                const entry = new EventPresentUser();
                entry.index = r.id;
                entry.name = r.name;
                entry.lastName = r.lastName;
                entry.arrivalTime = r.arrivalTime;
                entry.note = r.note;
                return entry;
            });

        this.isSavingPresence = true;
        this.calendarEventsService
            .setPresentUsers(this.event.id, presentUsers)
            .pipe(
                first(),
                finalize(() => (this.isSavingPresence = false))
            )
            .subscribe({
                next: (updated: CalendarEvents) => {
                    this.isDirtyPresence = false;
                    this.toastService.success('Successo', 'Presenze salvate');
                    this.updateEventDates(updated);
                }
            });
    }

    protected onGlobalFilter(table: Table, event: Event): void {
        table.filterGlobal((event.target as HTMLInputElement).value, 'contains');
    }

    protected filterStates(event: AutoCompleteCompleteEvent): void {
        this.autoFilteredStatesLabels = StateLabelsMap.filter((state) => (state.name.toLowerCase().includes(event.query.toLowerCase()) ? state : null)).filter((state) => state !== null) as StateLabel[];
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
        this.confirmService.confirmDestructive({
            title: 'Rimuovi costo',
            consequence: 'Il costo verrà rimosso dall’evento.',
            actionLabel: 'Rimuovi',
            accept: () => this.removeCost(index)
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
        this.calendarEventsService
            .getById(Number(id))
            .pipe(first())
            .subscribe({
                next: (ev: CalendarEvents) => {
                    this.event = ev;
                    this.isDirty = false;
                    this.event.startDate = this.dateConverterPipe.transform(this.event.startDate);
                    this.event.endDate = this.dateConverterPipe.transform(this.event.endDate);
                    this.loadPersonalReminder();
                    if (this.event.seriesId && this.isAdmin) {
                        this.loadSeries(this.event.seriesId);
                    } else {
                        this.series = undefined;
                    }
                    if (this.isAdmin) {
                        this.loadAllUsers();
                    }
                    if (this.isAdmin && this.financeEnabled()) {
                        this.financeService
                            .getEvent(this.event.id)
                            .pipe(first())
                            .subscribe((summary) => (this.economicSummary = summary));
                    }
                }
            });
    }

    private loadSeries(seriesId: number): void {
        this.calendarEventSeriesService
            .get(seriesId)
            .pipe(first())
            .subscribe({
                next: (series) => {
                    series.template.startDate = this.dateConverterPipe.transform(series.template.startDate);
                    series.template.endDate = this.dateConverterPipe.transform(series.template.endDate);
                    this.series = series;
                    this.seriesUntilDate = series.recurrence.end.until ? new Date(`${series.recurrence.end.until}T00:00:00`) : undefined;
                    this.seriesPreview = undefined;
                }
            });
    }

    private buildSeriesRequest(): CalendarEventSeriesRequest {
        const series = this.series!;
        const propagatedDates = this.propagatedSeriesDates(series);
        const recurrence = {
            ...series.recurrence,
            weekDays: series.recurrence.frequency === 'WEEKLY' ? series.recurrence.weekDays : [],
            end: series.recurrence.end.type === 'COUNT' ? { type: 'COUNT' as const, count: series.recurrence.end.count } : { type: 'UNTIL' as const, until: this.formatLocalDate(this.seriesUntilDate!) }
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
                costs: this.event.costs
            } as CalendarEvents,
            recurrence
        };
    }

    private propagatedSeriesDates(series: CalendarEventSeries): { startDate?: Date; endDate?: Date } {
        if (!this.event.startDate) return {};

        const eventStart = new Date(this.event.startDate);
        const originalOccurrenceStart = this.event.originalStartDate ? new Date(this.event.originalStartDate) : undefined;
        const seriesStart = series.template.startDate ? new Date(series.template.startDate) : undefined;

        if (!originalOccurrenceStart || !seriesStart) {
            return { startDate: eventStart, endDate: this.event.endDate };
        }

        const occurrenceShift = eventStart.getTime() - originalOccurrenceStart.getTime();
        const propagatedStart = new Date(seriesStart.getTime() + occurrenceShift);
        const duration = this.event.endDate ? new Date(this.event.endDate).getTime() - eventStart.getTime() : undefined;

        return {
            startDate: propagatedStart,
            endDate: duration === undefined ? undefined : new Date(propagatedStart.getTime() + duration)
        };
    }

    private formatLocalDate(value: Date): string {
        const year = value.getFullYear();
        const month = String(value.getMonth() + 1).padStart(2, '0');
        const day = String(value.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    }

    private loadAllUsers(): void {
        this.usersService
            .getAll({ size: 1000, sort: ['name,asc'] } as any)
            .pipe(first())
            .subscribe({
                next: (page) => {
                    this.buildPresenceRows(page.content ?? []);
                }
            });
    }

    /**
     * Ricostruisce le righe dalle presenze del server. Non tocca nulla finché
     * la sezione presenze ha modifiche non salvate: un ricaricamento causato
     * da un'altra unità non può scartare le presenze in corso di modifica.
     */
    private buildPresenceRows(users?: Users[]): void {
        if (this.isDirtyPresence) return;

        const source =
            users ??
            this.presenceRows.map(
                (r) =>
                    ({
                        id: r.id,
                        name: r.name,
                        lastName: r.lastName
                    }) as any
            );

        this.presenceRows = source.map((u: Users) => {
            const existing = this.event.presentUsers?.find((p) => p.index === u.id);
            return {
                id: u.id,
                name: u.name ?? '',
                lastName: u.lastName ?? '',
                present: !!existing,
                arrivalTime: existing?.arrivalTime ? this.dateConverterPipe.transform(existing.arrivalTime as any) : undefined,
                note: existing?.note
            } as UserPresenceRow;
        });
    }
}
