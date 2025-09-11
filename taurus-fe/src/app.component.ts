import { Component, HostListener } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ToastModule } from 'primeng/toast';
import { LoadingSpinnerComponent } from "./app/components/loading-spinner/loading-spinner.component";
import { ImportsModule } from './app/imports';
import { LocalStorageService } from './app/service';
import { PreferencesService } from './app/service/preferences.service';

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
    providers: [
        PreferencesService,
    ]
})
export class AppComponent {
    constructor(
        private readonly localStorageService: LocalStorageService,
        private readonly preferencesService: PreferencesService,
    ) { }

    @HostListener('window:unload', ['$event'])
    unloadHandler(event: any) {
        // Clear all the storage when leaves the app
        this.localStorageService.clear();
    }
}
