import { Component } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { FloatLabelModule } from 'primeng/floatlabel';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { Albums } from '../../module';
import { FormsModule } from '@angular/forms';
import { DatePickerModule } from 'primeng/datepicker';
import { FluidModule } from 'primeng/fluid';

@Component({
    selector: 'app-add-albums-dialog',
    imports: [
        ButtonModule,
        InputTextModule,
        FloatLabelModule,
        TextareaModule,
        DatePickerModule,
        FormsModule,
        FluidModule,
    ],
    templateUrl: './add-albums-dialog.html',
    styleUrl: './add-albums-dialog.component.scss',
})
export class AddAlbumsDialogComponent {

    protected album: Albums;

    constructor(private readonly dialogRef: DynamicDialogRef<AddAlbumsDialogComponent>) {
        this.album = new Albums();
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    protected save(): void {
        this.dialogRef.close(this.album);
    }
}
