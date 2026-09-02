import { AsyncPipe, CommonModule, DatePipe, NgClass } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule, RouterOutlet } from '@angular/router';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { AvatarModule } from 'primeng/avatar';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DataViewModule } from 'primeng/dataview';
import { DatePickerModule } from 'primeng/datepicker';
import { DragDropModule } from 'primeng/dragdrop';
import { DropdownModule } from 'primeng/dropdown';
import { DynamicDialogModule } from 'primeng/dynamicdialog';
import { FileUploadModule } from 'primeng/fileupload';
import { FloatLabelModule } from 'primeng/floatlabel';
import { FluidModule } from 'primeng/fluid';
import { GalleriaModule } from 'primeng/galleria';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { OrderListModule } from 'primeng/orderlist';
import { PopoverModule } from 'primeng/popover';
import { ScrollTopModule } from 'primeng/scrolltop';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { SkeletonModule } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { ToggleButtonModule } from 'primeng/togglebutton';
import { ToolbarModule } from 'primeng/toolbar';
import { TooltipModule } from 'primeng/tooltip';
import { HasRolesDirective } from './directive';
import { DangerZoneComponent } from './components/danger-zone/danger-zone.component';
import { DetailSectionComponent } from './components/detail-section/detail-section.component';
import { DialogShellComponent } from './components/dialog-shell/dialog-shell.component';
import { EmptyStateComponent } from './components/empty-state/empty-state.component';
import { EntityCardComponent } from './components/entity-card/entity-card.component';
import { FormFieldComponent } from './components/form-field/form-field.component';
import { InlineAlertComponent } from './components/inline-alert/inline-alert.component';
import { ListRowComponent } from './components/list-row/list-row.component';
import { ListToolbarComponent } from './components/list-toolbar/list-toolbar.component';
import { PageHeaderComponent } from './components/page-header/page-header.component';
import { SelectionBarComponent } from './components/selection-bar/selection-bar.component';
import { DateConverterPipe, EnumConverterPipe, InitialsPipe, SecurePipe } from './pipe';

@NgModule({
    imports: [
        NgClass,
        DatePipe,
        AsyncPipe,
        CommonModule,
        FormsModule,
        RouterModule,
        RouterOutlet,
        ButtonModule,
        DropdownModule,
        InputTextModule,
        InputNumberModule,
        SelectModule,
        DataViewModule,
        SelectButtonModule,
        OrderListModule,
        TagModule,
        AvatarModule,
        FluidModule,
        TextareaModule,
        FloatLabelModule,
        TableModule,
        InputIconModule,
        IconFieldModule,
        ToolbarModule,
        DatePickerModule,
        DynamicDialogModule,
        FileUploadModule,
        GalleriaModule,
        SecurePipe,
        PopoverModule,
        TooltipModule,
        CheckboxModule,
        ScrollTopModule,
        DragDropModule,
        ToggleButtonModule,
        AutoCompleteModule,
        DateConverterPipe,
        EnumConverterPipe,
        HasRolesDirective,
        InitialsPipe,
        ConfirmDialogModule,
        MessageModule,
        SkeletonModule,
        PageHeaderComponent,
        ListToolbarComponent,
        ListRowComponent,
        EntityCardComponent,
        EmptyStateComponent,
        SelectionBarComponent,
        InlineAlertComponent,
        FormFieldComponent,
        DialogShellComponent,
        DetailSectionComponent,
        DangerZoneComponent
    ],
    exports: [
        NgClass,
        DatePipe,
        AsyncPipe,
        CommonModule,
        FormsModule,
        RouterModule,
        RouterOutlet,
        ButtonModule,
        DropdownModule,
        InputTextModule,
        InputNumberModule,
        SelectModule,
        DataViewModule,
        SelectButtonModule,
        OrderListModule,
        TagModule,
        AvatarModule,
        FluidModule,
        TextareaModule,
        FloatLabelModule,
        TableModule,
        InputIconModule,
        IconFieldModule,
        ToolbarModule,
        DatePickerModule,
        DynamicDialogModule,
        FileUploadModule,
        GalleriaModule,
        SecurePipe,
        PopoverModule,
        TooltipModule,
        CheckboxModule,
        ScrollTopModule,
        DragDropModule,
        ToggleButtonModule,
        AutoCompleteModule,
        DateConverterPipe,
        EnumConverterPipe,
        HasRolesDirective,
        InitialsPipe,
        ConfirmDialogModule,
        MessageModule,
        SkeletonModule,
        PageHeaderComponent,
        ListToolbarComponent,
        ListRowComponent,
        EntityCardComponent,
        EmptyStateComponent,
        SelectionBarComponent,
        InlineAlertComponent,
        FormFieldComponent,
        DialogShellComponent,
        DetailSectionComponent,
        DangerZoneComponent
    ],
    providers: [SecurePipe, DateConverterPipe, EnumConverterPipe, HasRolesDirective, InitialsPipe]
})
export class ImportsModule {}
