import { DatePipe, NgClass, NgFor, NgIf } from '@angular/common';
import { NgModule } from "@angular/core";
import { FormsModule } from '@angular/forms';
import { RouterModule, RouterOutlet } from '@angular/router';
import { AvatarModule } from 'primeng/avatar';
import { ButtonModule } from 'primeng/button';
import { DataViewModule } from 'primeng/dataview';
import { DropdownModule } from 'primeng/dropdown';
import { FluidModule } from 'primeng/fluid';
import { InputTextModule } from 'primeng/inputtext';
import { OrderListModule } from 'primeng/orderlist';
import { PickListModule } from 'primeng/picklist';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { FloatLabelModule } from 'primeng/floatlabel';
import { TableModule } from 'primeng/table';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { ToolbarModule } from 'primeng/toolbar';
import { DatePickerModule } from 'primeng/datepicker';
import { DynamicDialogModule } from 'primeng/dynamicdialog';

@NgModule({
    imports: [
        NgClass,
        NgIf,
        NgFor,
        FormsModule,
        RouterModule,
        RouterOutlet,
        ButtonModule,
        DropdownModule,
        InputTextModule,
        SelectModule,
        DataViewModule,
        SelectButtonModule,
        PickListModule,
        OrderListModule,
        TagModule,
        AvatarModule,
        DatePipe,
        FluidModule,
        TextareaModule,
        FloatLabelModule,
        TableModule,
        InputIconModule,
        IconFieldModule,
        ToolbarModule,
        DatePickerModule,
        DynamicDialogModule,
    ],
    exports: [
        NgClass,
        NgIf,
        NgFor,
        FormsModule,
        RouterModule,
        RouterOutlet,
        ButtonModule,
        DropdownModule,
        InputTextModule,
        SelectModule,
        DataViewModule,
        SelectButtonModule,
        PickListModule,
        OrderListModule,
        TagModule,
        AvatarModule,
        DatePipe,
        FluidModule,
        TextareaModule,
        FloatLabelModule,
        TableModule,
        InputIconModule,
        IconFieldModule,
        ToolbarModule,
        DatePickerModule,
        DynamicDialogModule,
    ],
})
export class ImportsModule { }
