import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { FloatLabelModule } from 'primeng/floatlabel';
import { FluidModule } from 'primeng/fluid';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import {
    CalendarEventDialogResult,
    CalendarEventSeriesPreview,
    CalendarEventSeriesRequest,
    CalendarEvents,
    RecurrenceEndType,
    RecurrenceFrequency,
    RecurrenceWeekDay,
} from '../../module';
import { CalendarEventSeriesService } from '../../service';
import { first } from 'rxjs';

@Component({
    selector: 'app-add-calendar-events-dialog',
    imports: [
        ButtonModule,
        InputTextModule,
        InputNumberModule,
        SelectModule,
        FloatLabelModule,
        DatePickerModule,
        FluidModule,
        FormsModule,
        CommonModule,
    ],
    templateUrl: './add-calendar-events-dialog.component.html',
})
export class AddCalendarEventsDialogComponent {

    protected event: CalendarEvents;
    protected recurring = false;
    protected frequency: RecurrenceFrequency = 'WEEKLY';
    protected interval = 1;
    protected weekDays: RecurrenceWeekDay[] = [];
    protected endType: RecurrenceEndType = 'COUNT';
    protected occurrenceCount = 10;
    protected untilDate?: Date;
    protected previewResult?: CalendarEventSeriesPreview;
    protected isPreviewing = false;
    protected readonly frequencyOptions: { value: RecurrenceFrequency; label: string }[] = [
        { value: 'DAILY', label: 'Giornaliera' },
        { value: 'WEEKLY', label: 'Settimanale' },
        { value: 'MONTHLY', label: 'Mensile' },
        { value: 'YEARLY', label: 'Annuale' },
    ];
    protected readonly weekDayOptions: { code: RecurrenceWeekDay; label: string }[] = [
        { code: 'MO', label: 'Lun' },
        { code: 'TU', label: 'Mar' },
        { code: 'WE', label: 'Mer' },
        { code: 'TH', label: 'Gio' },
        { code: 'FR', label: 'Ven' },
        { code: 'SA', label: 'Sab' },
        { code: 'SU', label: 'Dom' },
    ];

    constructor(
        private readonly dialogRef: DynamicDialogRef<AddCalendarEventsDialogComponent>,
        private readonly config: DynamicDialogConfig,
        private readonly seriesService: CalendarEventSeriesService,
    ) {
        this.event = new CalendarEvents();
        if (config.data?.startDate) {
            this.event.startDate = new Date(config.data.startDate);
        }
        this.syncInitialWeekDay();
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    protected save(): void {
        const result: CalendarEventDialogResult = this.recurring
            ? { series: this.buildSeriesRequest() }
            : { event: this.event };
        this.dialogRef.close(result);
    }

    protected onRecurrenceChange(): void {
        this.previewResult = undefined;
        this.syncInitialWeekDay();
    }

    protected onStartDateChange(): void {
        this.previewResult = undefined;
        this.syncInitialWeekDay();
    }

    protected isWeekDaySelected(day: RecurrenceWeekDay): boolean {
        return this.weekDays.includes(day);
    }

    protected toggleWeekDay(day: RecurrenceWeekDay, selected: boolean): void {
        this.weekDays = selected
            ? [...new Set([...this.weekDays, day])]
            : this.weekDays.filter(value => value !== day);
        this.previewResult = undefined;
    }

    protected preview(): void {
        this.isPreviewing = true;
        this.seriesService.preview(this.buildSeriesRequest()).pipe(first()).subscribe({
            next: result => {
                this.previewResult = result;
                this.isPreviewing = false;
            },
            error: () => this.isPreviewing = false,
        });
    }

    protected get recurrenceInvalid(): boolean {
        if (!this.recurring) return false;
        if (!this.interval || this.interval < 1) return true;
        if (this.frequency === 'WEEKLY' && this.weekDays.length === 0) return true;
        if (this.endType === 'COUNT') return !this.occurrenceCount || this.occurrenceCount < 1 || this.occurrenceCount > 500;
        return !this.untilDate;
    }

    private buildSeriesRequest(): CalendarEventSeriesRequest {
        return {
            template: this.event,
            recurrence: {
                frequency: this.frequency,
                interval: this.interval,
                weekDays: this.frequency === 'WEEKLY' ? this.weekDays : [],
                end: this.endType === 'COUNT'
                    ? { type: 'COUNT', count: this.occurrenceCount }
                    : { type: 'UNTIL', until: this.formatLocalDate(this.untilDate!) },
            },
        };
    }

    private syncInitialWeekDay(): void {
        if (!this.event.startDate || this.frequency !== 'WEEKLY') return;
        const codes: RecurrenceWeekDay[] = ['SU', 'MO', 'TU', 'WE', 'TH', 'FR', 'SA'];
        const startDay = codes[new Date(this.event.startDate).getDay()];
        if (!this.weekDays.includes(startDay)) this.weekDays = [...this.weekDays, startDay];
    }

    private formatLocalDate(value: Date): string {
        const year = value.getFullYear();
        const month = String(value.getMonth() + 1).padStart(2, '0');
        const day = String(value.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    }
}
