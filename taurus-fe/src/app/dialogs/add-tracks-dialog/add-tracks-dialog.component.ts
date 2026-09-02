import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { ChipModule } from 'primeng/chip';
import { DatePickerModule } from 'primeng/datepicker';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { FloatLabelModule } from 'primeng/floatlabel';
import { FluidModule } from 'primeng/fluid';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { TypeHandlerComponent } from '../../components/type-handler/type-handler.component';
import { Tracks } from '../../module';
import { DialogShellComponent } from '../../components/dialog-shell/dialog-shell.component';
import { FormFieldComponent } from '../../components/form-field/form-field.component';

@Component({
    selector: 'app-add-tracks-dialog',
    imports: [ButtonModule, InputTextModule, FloatLabelModule, TextareaModule, DatePickerModule, FormsModule, FluidModule, ChipModule, TypeHandlerComponent, DialogShellComponent, FormFieldComponent],
    templateUrl: './add-tracks-dialog.component.html',
    styleUrl: './add-tracks-dialog.component.scss'
})
export class AddTracksDialogComponent {
    protected track: Tracks;

    constructor(private readonly dialogRef: DynamicDialogRef<AddTracksDialogComponent>) {
        this.track = new Tracks();
    }

    protected removeType(currentType: string) {
        this.track.type?.splice(this.track.type.indexOf(currentType), 1);
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    protected save(): void {
        if (!this.track.name?.trim()) return;
        this.dialogRef.close(this.track);
    }
}
