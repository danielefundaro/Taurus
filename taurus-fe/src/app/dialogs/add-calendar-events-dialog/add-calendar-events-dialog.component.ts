import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { FloatLabelModule } from 'primeng/floatlabel';
import { FluidModule } from 'primeng/fluid';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { CalendarEvents } from '../../module';

@Component({
    selector: 'app-add-calendar-events-dialog',
    imports: [
        ButtonModule,
        InputTextModule,
        InputNumberModule,
        FloatLabelModule,
        DatePickerModule,
        FluidModule,
        FormsModule,
    ],
    templateUrl: './add-calendar-events-dialog.component.html',
})
export class AddCalendarEventsDialogComponent {

    protected event: CalendarEvents;

    constructor(
        private readonly dialogRef: DynamicDialogRef<AddCalendarEventsDialogComponent>,
        private readonly config: DynamicDialogConfig,
    ) {
        this.event = new CalendarEvents();
        if (config.data?.startDate) {
            this.event.startDate = new Date(config.data.startDate);
        }
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    protected save(): void {
        this.dialogRef.close(this.event);
    }
}
