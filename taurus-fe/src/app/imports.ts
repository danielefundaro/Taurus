import { AsyncPipe, DatePipe, NgClass, NgFor, NgIf } from '@angular/common';
import { NgModule } from "@angular/core";
import { FormsModule } from '@angular/forms';
import { RouterModule, RouterOutlet } from '@angular/router';
import { AvatarModule } from 'primeng/avatar';
import { ButtonModule } from 'primeng/button';
import { DataViewModule } from 'primeng/dataview';
import { DatePickerModule } from 'primeng/datepicker';
import { DropdownModule } from 'primeng/dropdown';
import { DynamicDialogModule } from 'primeng/dynamicdialog';
import { FileUploadModule } from 'primeng/fileupload';
import { FloatLabelModule } from 'primeng/floatlabel';
import { FluidModule } from 'primeng/fluid';
import { GalleriaModule } from 'primeng/galleria';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { OrderListModule } from 'primeng/orderlist';
import { PickListModule } from 'primeng/picklist';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { ToolbarModule } from 'primeng/toolbar';

@NgModule({
    imports: [
        NgClass,
        NgIf,
        NgFor,
        DatePipe,
        AsyncPipe,
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
    ],
    exports: [
        NgClass,
        NgIf,
        NgFor,
        DatePipe,
        AsyncPipe,
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
    ],
})
export class ImportsModule { }
