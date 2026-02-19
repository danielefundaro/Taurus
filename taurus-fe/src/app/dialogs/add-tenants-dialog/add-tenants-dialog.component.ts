import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { FloatLabelModule } from 'primeng/floatlabel';
import { FluidModule } from 'primeng/fluid';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { ToggleButtonModule } from 'primeng/togglebutton';
import { Tenants } from '../../module';

@Component({
    selector: 'app-add-tenants-dialog',
    imports: [
        ButtonModule,
        InputTextModule,
        FloatLabelModule,
        TextareaModule,
        DatePickerModule,
        FormsModule,
        FluidModule,
        ToggleButtonModule,
    ],
    templateUrl: './add-tenants-dialog.html',
    styleUrl: './add-tenants-dialog.component.scss',
})
export class AddTenantsDialogComponent {

    protected tenant: Tenants;

    constructor(private readonly dialogRef: DynamicDialogRef<AddTenantsDialogComponent>) {
        this.tenant = new Tenants();
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    protected save(): void {
        this.dialogRef.close(this.tenant);
    }
}
