import { Component, ChangeDetectionStrategy } from '@angular/core';
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
import { DialogShellComponent } from '../../components/dialog-shell/dialog-shell.component';
import { FormFieldComponent } from '../../components/form-field/form-field.component';
import { ToggleButtonDisabledDirective } from '../../directive/toggle-button-disabled.directive';

@Component({
    selector: 'app-add-tenants-dialog',
    imports: [ButtonModule, InputTextModule, FloatLabelModule, TextareaModule, DatePickerModule, FormsModule, FluidModule, ToggleButtonModule, ToggleButtonDisabledDirective, DialogShellComponent, FormFieldComponent],
    templateUrl: './add-tenants-dialog.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './add-tenants-dialog.component.scss'
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
        if (!this.tenant.name?.trim() || !this.tenant.code?.trim()) return;
        this.dialogRef.close(this.tenant);
    }
}
