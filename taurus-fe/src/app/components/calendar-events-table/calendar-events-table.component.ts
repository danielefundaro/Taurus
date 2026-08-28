import { CommonModule, DatePipe } from "@angular/common";
import { Component, Input } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { DatePickerModule } from "primeng/datepicker";
import { FloatLabelModule } from "primeng/floatlabel";
import { TableLazyLoadEvent, TableModule } from "primeng/table";
import { first } from "rxjs";
import { CalendarEvents, Page, UsersCalendarEventsCriteria } from "../../module";
import { DateConverterPipe } from "../../pipe";
import { UsersService } from "../../service";

@Component({
    selector: 'app-calendar-events-table',
    imports: [
        CommonModule,
        FormsModule,
        TableModule,
        FloatLabelModule,
        DatePickerModule,
        DatePipe,
    ],
    templateUrl: './calendar-events-table.component.html',
    styleUrl: './calendar-events-table.component.scss',
})
export class CalendarEventsTableComponent {

    protected totalRecords: number = 0;
    protected startDate?: Date;
    protected endDate?: Date;
    protected calendarEvents: CalendarEvents[] = [];

    private _userId?: number;
    private tableLazyLoadEvent: TableLazyLoadEvent = { first: 0, rows: 10, sortField: 'startDate', sortOrder: -1 };

    @Input() set userId(value: number | undefined) {
        this._userId = value;
        this.loadCalendarEvents();
    }
    constructor(
        private readonly usersService: UsersService,
        private readonly dateConverterPipe: DateConverterPipe,
    ) { }

    protected onLazyLoadCalendarEvents(event: TableLazyLoadEvent): void {
        this.tableLazyLoadEvent = event;
        this.loadCalendarEvents();
    }

    protected onDateFilter(): void {
        this.loadCalendarEvents();
    }

    private loadCalendarEvents() {
        let criteria: UsersCalendarEventsCriteria = new UsersCalendarEventsCriteria();
        criteria.startDate = this.dateConverterPipe.transform(this.startDate?.toDateString());
        criteria.endDate = this.dateConverterPipe.transform(this.endDate?.toDateString());
        criteria.page = this.tableLazyLoadEvent.first! / this.tableLazyLoadEvent.rows!;
        criteria.size = this.tableLazyLoadEvent.rows!;
        criteria.sort = this.tableLazyLoadEvent.sortField ? [`${this.tableLazyLoadEvent.sortField},${this.tableLazyLoadEvent.sortOrder === 1 ? 'asc' : 'desc'}`] : ['startDate,desc'];

        let observable = this._userId ? this.usersService.getUserCalendarEvents(this._userId, criteria) : this.usersService.getOwnCalendarEvents(criteria);

        observable.pipe(first()).subscribe({
            next: (page: Page<CalendarEvents>) => {
                this.calendarEvents = page.content;
                this.totalRecords = page.totalElements;
            }
        });
    }
}
