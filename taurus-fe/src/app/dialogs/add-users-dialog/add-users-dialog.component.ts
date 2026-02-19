import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AutoCompleteCompleteEvent, AutoCompleteModule } from 'primeng/autocomplete';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { FloatLabelModule } from 'primeng/floatlabel';
import { FluidModule } from 'primeng/fluid';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { ChildrenEntities, Instruments, Tenants, Users } from '../../module';
import { RoleEnums } from '../../constants';
import { CheckboxModule } from 'primeng/checkbox';

@Component({
    selector: 'app-add-users-dialog',
    imports: [
        ButtonModule,
        InputTextModule,
        FloatLabelModule,
        TextareaModule,
        DatePickerModule,
        FormsModule,
        FluidModule,
        AutoCompleteModule,
        CheckboxModule,
    ],
    templateUrl: './add-users-dialog.component.html',
    styleUrl: './add-users-dialog.component.scss',
})
export class AddUsersDialogComponent {

    @Input() public readonly instruments: Instruments[];

    protected user: Users;
    protected autoFilteredRoles: Array<string>;
    protected autoFilteredInstruments: ChildrenEntities[];

    private readonly roles: Array<RoleEnums>;
    private readonly instrumentsChildrenEntities: ChildrenEntities[];

    constructor(private readonly dialogRef: DynamicDialogRef<AddUsersDialogComponent>,
            private readonly config: DynamicDialogConfig<any, { instruments: Instruments[]}>,) {
        this.user = new Users();
        this.user.active = true;
        this.roles = RoleEnums ? Object.values(RoleEnums) : [];
        this.instruments = this.config.inputValues?.instruments ?? [];

        this.autoFilteredRoles = this.roles;

        this.instrumentsChildrenEntities = this.config.inputValues?.instruments.map(instrument => {
            const childrenEntity = new ChildrenEntities();
            childrenEntity.name = instrument.name;
            childrenEntity.index = instrument.id;

            return childrenEntity;
        }) ?? [];

        this.autoFilteredInstruments = this.instrumentsChildrenEntities;
    }
    
    protected filterRoles(event: AutoCompleteCompleteEvent) {
        this.autoFilteredRoles = this.roles.filter(role => role?.toLowerCase()?.includes(event.query.toLowerCase()));
    }

    protected filterInstruments(event: AutoCompleteCompleteEvent) {
        this.autoFilteredInstruments = this.instrumentsChildrenEntities.filter(instrument => instrument.name?.toLowerCase()?.includes(event.query.toLowerCase()));
    }

    protected onReorderInstruments(): void {
        this.user.instruments?.forEach((instrument, i) => instrument.order = i + 1);
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    protected save(): void {
        this.dialogRef.close(this.user);
    }
}
