import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AutoCompleteCompleteEvent, AutoCompleteModule } from 'primeng/autocomplete';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DatePickerModule } from 'primeng/datepicker';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { FloatLabelModule } from 'primeng/floatlabel';
import { FluidModule } from 'primeng/fluid';
import { InputTextModule } from 'primeng/inputtext';
import { MultiSelectModule } from 'primeng/multiselect';
import { TextareaModule } from 'primeng/textarea';
import { RoleEnums, RoleLabel, RoleLabelsMap } from '../../constants';
import { ChildrenEntities, Instruments, Users } from '../../module';
import { DialogShellComponent } from '../../components/dialog-shell/dialog-shell.component';
import { FormFieldComponent } from '../../components/form-field/form-field.component';

@Component({
    selector: 'app-add-users-dialog',
    imports: [ButtonModule, InputTextModule, FloatLabelModule, TextareaModule, DatePickerModule, FormsModule, FluidModule, AutoCompleteModule, MultiSelectModule, CheckboxModule, DialogShellComponent, FormFieldComponent],
    templateUrl: './add-users-dialog.component.html',
    styleUrl: './add-users-dialog.component.scss'
})
export class AddUsersDialogComponent {
    @Input() public readonly instruments: Instruments[];

    protected user: Users;
    protected readonly roleOptions: RoleLabel[];
    protected autoFilteredInstruments: ChildrenEntities[];

    private readonly instrumentsChildrenEntities: ChildrenEntities[];

    constructor(
        private readonly dialogRef: DynamicDialogRef<AddUsersDialogComponent>,
        private readonly config: DynamicDialogConfig<any, { instruments: Instruments[] }>
    ) {
        this.user = new Users();
        this.user.active = true;
        this.roleOptions = RoleLabelsMap.filter((role) => role.code !== RoleEnums.SUPER_ADMIN);
        this.instruments = this.config.inputValues?.instruments ?? [];

        this.instrumentsChildrenEntities =
            this.config.inputValues?.instruments.map((instrument) => {
                const childrenEntity = new ChildrenEntities();
                childrenEntity.name = instrument.name;
                childrenEntity.index = instrument.id;

                return childrenEntity;
            }) ?? [];

        this.autoFilteredInstruments = this.instrumentsChildrenEntities;
    }

    protected filterInstruments(event: AutoCompleteCompleteEvent) {
        this.autoFilteredInstruments = this.instrumentsChildrenEntities.filter((instrument) => instrument.name?.toLowerCase()?.includes(event.query.toLowerCase()));
    }

    protected onReorderInstruments(): void {
        this.user.instruments?.forEach((instrument, i) => (instrument.order = i + 1));
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    protected save(): void {
        if (!this.user.name?.trim() || !this.user.email?.trim() || !this.user.roles?.length) return;
        this.dialogRef.close(this.user);
    }
}
