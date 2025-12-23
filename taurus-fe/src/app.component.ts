import { Component, HostListener, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ToastModule } from 'primeng/toast';
import { first, switchMap } from 'rxjs';
import { LoadingSpinnerComponent } from "./app/components/loading-spinner/loading-spinner.component";
import { ImportsModule } from './app/imports';
import { Page, Preferences, PreferencesCriteria } from './app/module';
import { LayoutService, LocalStorageService } from './app/service';
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
export class AppComponent implements OnInit {
    constructor(
        private readonly localStorageService: LocalStorageService,
        private readonly preferencesService: PreferencesService,
        private readonly layoutService: LayoutService,
    ) { }

    ngOnInit(): void {
        this.preferencesService.count().pipe(first(), switchMap((count: any) => {
            const criteria = new PreferencesCriteria();
            criteria.size = count;
            criteria.page = 0;

            return this.preferencesService.getAll(criteria).pipe(first());
        })).subscribe({
            next: (result?: Page<Preferences>) => {
                if (!result?.empty) {
                    result?.content.forEach((preference: Preferences) => {
                        this.localStorageService.setItem(preference.key, preference);

                        switch (preference.key) {
                            case "preset":
                                this.layoutService.layoutConfig.update((state) => ({ ...state, preset: preference.value! }));
                                break;
                            case "primary":
                                this.layoutService.layoutConfig.update((state) => ({ ...state, primary: preference.value! }));
                                break;
                            case "menuMode":
                                this.layoutService.layoutConfig.update((state) => ({ ...state, menuMode: preference.value! }));
                                break;
                            case "surface":
                                this.layoutService.layoutConfig.update((state) => ({ ...state, surface: preference.value! }));
                                break;
                            case "darkTheme":
                                this.layoutService.layoutConfig.update((state) => ({ ...state, darkTheme: preference.value?.toLowerCase().includes("true") }));
                                break;
                            case "menuHoverActive":
                                this.layoutService.layoutState.update((state) => ({ ...state, menuHoverActive: preference.value?.toLowerCase().includes("true") }));
                                break;
                            case "staticMenuDesktopInactive":
                                this.layoutService.layoutState.update((state) => ({ ...state, staticMenuDesktopInactive: preference.value?.toLowerCase().includes("true") }));
                                break;
                            case "overlayMenuActive":
                                this.layoutService.layoutState.update((state) => ({ ...state, overlayMenuActive: preference.value?.toLowerCase().includes("true") }));
                                break;
                            case "configSidebarVisible":
                                this.layoutService.layoutState.update((state) => ({ ...state, configSidebarVisible: preference.value?.toLowerCase().includes("true") }));
                                break;
                            case "staticMenuMobileActive":
                                this.layoutService.layoutState.update((state) => ({ ...state, staticMenuMobileActive: preference.value?.toLowerCase().includes("true") }));
                                break;
                        }
                    });
                }
            }
        });
    }

    @HostListener('window:unload', ['$event'])
    unloadHandler(event: any) {
        // Clear all the storage when leaves the app
        this.localStorageService.clear();
    }
}
