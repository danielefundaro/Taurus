import { ChangeDetectionStrategy, Component, OnInit, ViewChild } from '@angular/core';
import { RouterModule } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Popover } from 'primeng/popover';
import { delay, finalize, first, forkJoin, Observable } from 'rxjs';
import { RoleEnums, StateLabelsMap } from '../../constants';
import { AddCalendarEventsDialogComponent } from '../../dialogs/add-calendar-events-dialog/add-calendar-events-dialog.component';
import { ImportsModule } from '../../imports';
import { CalendarEventDialogResult, CalendarEvents, CalendarEventsCriteria, Page } from '../../module';
import { DateFilter, StringFilter } from '../../module/criteria/filter';
import { CalendarEventSeriesService, CalendarEventsService, ConfirmService, ListLayout, ListLayoutService, ToastService } from '../../service';
import { ListPageBase } from '../_shared/list-page.base';

interface CalendarDay {
    date: Date;
    isCurrentMonth: boolean;
    isToday: boolean;
    isFuture: boolean;
    events: CalendarEvents[];
}

@Component({
    selector: 'app-calendar-events',
    imports: [RouterModule, ImportsModule],
    templateUrl: './calendar-events.component.html',
    styleUrl: './calendar-events.component.scss',
    providers: [CalendarEventsService, CalendarEventSeriesService, DialogService],
    changeDetection: ChangeDetectionStrategy.Default
})
export class CalendarEventsComponent extends ListPageBase implements OnInit {
    protected sortOptions!: SelectItem[];
    protected layout: ListLayout = 'list';
    protected events: CalendarEvents[] = [];
    protected selectedEvents: CalendarEvents[] = [];
    protected readonly RolesEnum: typeof RoleEnums = RoleEnums;

    // Calendar (grid) mode state
    protected currentMonth: Date = new Date();
    protected calendarDays: CalendarDay[] = [];

    @ViewChild('monthPicker') private readonly monthPickerRef?: Popover;
    protected readonly DAY_LABELS = ['Lun', 'Mar', 'Mer', 'Gio', 'Ven', 'Sab', 'Dom'];
    protected readonly MONTH_LABELS = ['Gennaio', 'Febbraio', 'Marzo', 'Aprile', 'Maggio', 'Giugno', 'Luglio', 'Agosto', 'Settembre', 'Ottobre', 'Novembre', 'Dicembre'];

    constructor(
        private readonly calendarEventsService: CalendarEventsService,
        private readonly calendarEventSeriesService: CalendarEventSeriesService,
        private readonly toastService: ToastService,
        private readonly dialogService: DialogService,
        private readonly confirmService: ConfirmService,
        private readonly listLayoutService: ListLayoutService
    ) {
        super();
        this.dataViewLazyLoadEvent = { first: 0, rows: 12, sortField: 'startDate', sortOrder: 1 };
    }

    ngOnInit(): void {
        this.sortOptions = [
            { label: 'Data ↑', value: 'startDate' },
            { label: 'Data ↓', value: '!startDate' },
            { label: 'Nome A-Z', value: 'name' },
            { label: 'Nome Z-A', value: '!name' }
        ];
        this.listLayoutService.observe('calendar', (value) => this.applyLayout(value));
    }

    protected onLayoutChange(value: ListLayout): void {
        this.applyLayout(value);
        this.listLayoutService.set('calendar', this.layout);
    }

    protected visibleDayEvents(day: CalendarDay): CalendarEvents[] {
        return day.events.slice(0, 3);
    }

    protected hiddenDayEvents(day: CalendarDay): number {
        return Math.max(day.events.length - 3, 0);
    }

    private applyLayout(value: ListLayout): void {
        this.layout = value;
        if (this.layout === 'grid') {
            this.loadCalendarMonth();
        }
    }

    protected goToToday(): void {
        this.currentMonth = new Date();
        this.loadCalendarMonth();
    }

    protected onMonthSelected(): void {
        this.loadCalendarMonth();
        this.monthPickerRef?.hide();
    }

    protected prevMonth(): void {
        this.currentMonth = new Date(this.currentMonth.getFullYear(), this.currentMonth.getMonth() - 1, 1);
        this.loadCalendarMonth();
    }

    protected nextMonth(): void {
        this.currentMonth = new Date(this.currentMonth.getFullYear(), this.currentMonth.getMonth() + 1, 1);
        this.loadCalendarMonth();
    }

    protected get currentMonthLabel(): string {
        return `${this.MONTH_LABELS[this.currentMonth.getMonth()]} ${this.currentMonth.getFullYear()}`;
    }

    protected addNew(): void {
        this.openDialog();
    }

    protected openCreateOnDate(date: Date): void {
        this.openDialog({ startDate: date });
    }

    private openDialog(data?: { startDate?: Date }): void {
        const ref: DynamicDialogRef = this.dialogService.open(AddCalendarEventsDialogComponent, {
            header: 'Aggiungi evento',
            closable: true,
            draggable: true,
            resizable: true,
            modal: true,
            width: '40rem',
            breakpoints: { '767px': 'calc(100vw - 2rem)' },
            data
        });

        ref.onClose.pipe(first()).subscribe((result: CalendarEventDialogResult) => {
            if (result?.event || result?.series) {
                const creation: Observable<unknown> = result.series ? this.calendarEventSeriesService.create(result.series) : this.calendarEventsService.create(result.event!);
                creation.pipe(delay(1000), first()).subscribe({
                    next: () => {
                        this.toastService.success('Successo', result.series ? 'Serie di eventi aggiunta con successo' : 'Evento aggiunto con successo');
                        if (this.layout === 'grid') {
                            this.loadCalendarMonth();
                        } else {
                            this.loadElements(this.searchTerm);
                        }
                    }
                });
            }
        });
    }

    protected deleteElement(event: CalendarEvents): void {
        this.confirmService.confirmDestructive({
            title: 'Elimina evento',
            consequence: 'L’evento verrà eliminato definitivamente.',
            actionLabel: 'Elimina',
            accept: () => {
                this.calendarEventsService
                    .delete(event.id)
                    .pipe(delay(1000), first())
                    .subscribe({
                        next: () => {
                            this.toastService.success('Successo', 'Evento eliminato con successo');
                            if (this.layout === 'grid') {
                                this.loadCalendarMonth();
                            } else {
                                this.loadElements(this.searchTerm);
                            }
                        }
                    });
            }
        });
    }

    protected isSelected(item: CalendarEvents): boolean {
        return this.selectedEvents.some((s) => s.id === item.id);
    }

    protected isAllSelected(items: CalendarEvents[]): boolean {
        return items.length > 0 && items.every((item) => this.isSelected(item));
    }

    protected toggleSelection(item: CalendarEvents): void {
        if (this.isSelected(item)) {
            this.selectedEvents = this.selectedEvents.filter((s) => s.id !== item.id);
        } else {
            this.selectedEvents = [...this.selectedEvents, item];
        }
    }

    protected toggleSelectAll(items: CalendarEvents[]): void {
        if (this.isAllSelected(items)) {
            this.selectedEvents = this.selectedEvents.filter((s) => !items.some((item) => item.id === s.id));
        } else {
            const notYetSelected = items.filter((item) => !this.isSelected(item));
            this.selectedEvents = [...this.selectedEvents, ...notYetSelected];
        }
    }

    protected deleteSelectedElements(): void {
        const count = this.selectedEvents.length;
        this.confirmService.confirmDestructive({
            title: 'Elimina eventi selezionati',
            consequence: `I ${count} eventi selezionati verranno eliminati definitivamente.`,
            actionLabel: 'Elimina',
            accept: () => {
                forkJoin(this.selectedEvents.map((item) => this.calendarEventsService.delete(item.id)))
                    .pipe(delay(1000), first())
                    .subscribe({
                        next: () => {
                            this.selectedEvents = [];
                            this.toastService.success('Successo', `${count} eventi eliminati con successo`);
                            if (this.layout === 'grid') {
                                this.loadCalendarMonth();
                            } else {
                                this.loadElements(this.searchTerm);
                            }
                        }
                    });
            }
        });
    }

    protected getStateLabel(state: string): string {
        const stateLabel = StateLabelsMap.find((s) => s.code === state);
        return stateLabel ? stateLabel.name : state;
    }

    protected loadElements(search?: string): void {
        this.loading = true;
        this.selectedEvents = [];
        const criteria = new CalendarEventsCriteria();
        criteria.page = this.pageIndex;
        criteria.size = this.pageSize;
        criteria.sort = this.sortCriteria;

        if (search) {
            criteria.name = new StringFilter();
            criteria.name.contains = search;
        }

        this.calendarEventsService
            .getAll(criteria)
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe({
                next: (value: Page<CalendarEvents>) => {
                    this.events = value.content;
                    this.totalRecords = value.totalElements;
                }
            });
    }

    private loadCalendarMonth(): void {
        this.loading = true;
        const year = this.currentMonth.getFullYear();
        const month = this.currentMonth.getMonth();

        this.buildCalendarDays(year, month, []);

        const criteria = new CalendarEventsCriteria();
        criteria.page = 0;
        criteria.size = 500;
        criteria.sort = ['startDate,asc'];
        // Fetch events that overlap with the current month:
        // event starts before or on the last day of the month AND ends on or after the first day
        criteria.startDate = new DateFilter();
        criteria.startDate.lessThanOrEqual = new Date(year, month + 1, 0, 23, 59, 59);
        criteria.endDate = new DateFilter();
        criteria.endDate.greaterThanOrEqual = new Date(year, month, 1);

        this.calendarEventsService
            .getAll(criteria)
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe({
                next: (page: Page<CalendarEvents>) => {
                    this.buildCalendarDays(year, month, page.content);
                }
            });
    }

    private buildCalendarDays(year: number, month: number, events: CalendarEvents[]): void {
        const today = new Date();
        const todayKey = this.toDateKey(today);
        const todayTime = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime();

        const eventsByDay = new Map<string, CalendarEvents[]>();
        events.forEach((event) => {
            if (event.startDate) {
                const start = new Date(event.startDate);
                start.setHours(0, 0, 0, 0);
                const end = event.endDate ? new Date(event.endDate) : new Date(start);
                end.setHours(0, 0, 0, 0);

                const cur = new Date(start);
                while (cur <= end) {
                    const key = this.toDateKey(cur);
                    if (!eventsByDay.has(key)) eventsByDay.set(key, []);
                    eventsByDay.get(key)!.push(event);
                    cur.setDate(cur.getDate() + 1);
                }
            }
        });

        const firstDayOfMonth = new Date(year, month, 1);
        const lastDayOfMonth = new Date(year, month + 1, 0);

        // Day of week for first day (0=Sun → treat as 6 for Mon-first grid)
        let startOffset = firstDayOfMonth.getDay() - 1;
        if (startOffset < 0) startOffset = 6;

        const days: CalendarDay[] = [];

        // Previous month padding
        const prevMonthLast = new Date(year, month, 0).getDate();
        for (let i = startOffset - 1; i >= 0; i--) {
            const date = new Date(year, month - 1, prevMonthLast - i);
            days.push({ date, isCurrentMonth: false, isToday: false, isFuture: false, events: [] });
        }

        // Current month
        for (let d = 1; d <= lastDayOfMonth.getDate(); d++) {
            const date = new Date(year, month, d);
            const key = this.toDateKey(date);
            days.push({
                date,
                isCurrentMonth: true,
                isToday: key === todayKey,
                isFuture: date.getTime() > todayTime,
                events: eventsByDay.get(key) ?? []
            });
        }

        // Next month padding (fill to 42 = 6 rows × 7 cols)
        const remaining = 42 - days.length;
        for (let i = 1; i <= remaining; i++) {
            const date = new Date(year, month + 1, i);
            days.push({ date, isCurrentMonth: false, isToday: false, isFuture: false, events: [] });
        }

        this.calendarDays = days;
    }

    private toDateKey(date: Date): string {
        return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`;
    }
}
