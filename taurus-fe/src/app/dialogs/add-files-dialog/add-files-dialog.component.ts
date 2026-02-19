import { HttpHeaders } from '@angular/common/http';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { ChipModule } from 'primeng/chip';
import { DatePickerModule } from 'primeng/datepicker';
import { FileUploadModule } from 'primeng/fileupload';
import { FloatLabelModule } from 'primeng/floatlabel';
import { FluidModule } from 'primeng/fluid';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { KeycloakService, TracksService } from '../../service';

@Component({
    selector: 'app-add-files-dialog',
    imports: [
        ButtonModule,
        InputTextModule,
        FloatLabelModule,
        TextareaModule,
        DatePickerModule,
        FormsModule,
        FluidModule,
        ChipModule,
        FileUploadModule
    ],
    providers: [
        TracksService,
        KeycloakService,
    ],
    templateUrl: './add-files-dialog.component.html',
    styleUrl: './add-files-dialog.component.scss',
})
export class AddFilesDialogComponent {

    constructor(private readonly tracksService: TracksService,
            private readonly keycloakService: KeycloakService,) {
    }

    protected trackStream(): string {
        return this.tracksService.stream();
    }

    protected httpHeaders(): HttpHeaders {
        return new HttpHeaders({ 'Authorization': `Bearer ${this.keycloakService.token}` });
    }
}
