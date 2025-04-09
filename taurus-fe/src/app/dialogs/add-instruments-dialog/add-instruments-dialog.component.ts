import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { FloatLabelModule } from 'primeng/floatlabel';
import { FluidModule } from 'primeng/fluid';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { Instruments } from '../../module';

@Component({
    selector: 'app-add-instruments-dialog',
    imports: [
        ButtonModule,
        InputTextModule,
        FloatLabelModule,
        TextareaModule,
        DatePickerModule,
        FormsModule,
        FluidModule,
    ],
    templateUrl: './add-instruments-dialog.html',
    styleUrl: './add-instruments-dialog.component.scss',
})
export class AddInstrumentsDialogComponent {

    protected instrument: Instruments;

    constructor(private readonly dialogRef: DynamicDialogRef<AddInstrumentsDialogComponent>) {
        this.instrument = new Instruments();
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    protected save(): void {
        this.dialogRef.close(this.instrument);
    }
}
