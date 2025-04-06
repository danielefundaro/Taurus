import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ImportsModule } from './app/imports';

@Component({
    selector: 'app-root',
    standalone: true,
    imports: [
        RouterModule,
        ImportsModule,
    ],
    templateUrl: './app.component.html',
})
export class AppComponent {}
