import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ImportsModule } from './app/imports';
import { LoadingSpinnerComponent } from "./app/components/loading-spinner/loading-spinner.component";
import { ToastModule } from 'primeng/toast';

@Component({
    selector: 'app-root',
    standalone: true,
    imports: [
        RouterModule,
        ImportsModule,
        ToastModule,
        LoadingSpinnerComponent,
    ],
    templateUrl: './app.component.html',
})
export class AppComponent { }
