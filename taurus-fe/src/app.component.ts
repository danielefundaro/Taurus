import { Component, HostListener, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ToastModule } from 'primeng/toast';
import { first, switchMap } from 'rxjs';
import { InlineAlertComponent } from './app/components/inline-alert/inline-alert.component';
import { LoadingSpinnerComponent } from './app/components/loading-spinner/loading-spinner.component';
import { ImportsModule } from './app/imports';
import { Page, Preferences, PreferencesCriteria } from './app/module';
import { LayoutService, ListLayoutService, LocalStorageService, NoticesService, PreferencesService, PushNotificationService, ToastService } from './app/service';

@Component({
    selector: 'app-root',
    standalone: true,
    imports: [RouterModule, ImportsModule, ToastModule, LoadingSpinnerComponent, InlineAlertComponent],
    templateUrl: './app.component.html',
    providers: [PreferencesService]
})
export class AppComponent implements OnInit {
    /** Invito non invasivo ad attivare i promemoria push: mai un prompt automatico del browser. */
    protected pushPromptVisible = false;

    private static readonly PUSH_PROMPT_DISMISSED = 'pushPromptDismissed';

    constructor(
        private readonly localStorageService: LocalStorageService,
        private readonly preferencesService: PreferencesService,
        private readonly noticesService: NoticesService,
        private readonly layoutService: LayoutService,
        private readonly pushNotificationService: PushNotificationService,
        private readonly listLayoutService: ListLayoutService,
        private readonly toastService: ToastService
    ) {}

    ngOnInit(): void {
        this.pushNotificationService.init();
        this.pushPromptVisible = this.pushNotificationService.permission === 'default' && sessionStorage.getItem(AppComponent.PUSH_PROMPT_DISMISSED) !== 'true';

        setInterval(() => {
            this.noticesService
                .countUnread()
                .pipe(first())
                .subscribe((count) => {
                    this.layoutService.notificationNumber.set(count);
                });
        }, 300000); // 5 minutes

        this.preferencesService
            .count()
            .pipe(
                first(),
                switchMap((count: any) => {
                    const criteria = new PreferencesCriteria();
                    criteria.size = count;
                    criteria.page = 0;

                    return this.preferencesService.getAll(criteria).pipe(first());
                })
            )
            .subscribe({
                next: (result?: Page<Preferences>) => {
                    this.listLayoutService.hydrate(result?.content ?? []);
                    if (!result?.empty) {
                        result?.content.forEach((preference: Preferences) => {
                            this.localStorageService.setItem(preference.key, preference);

                            switch (preference.key) {
                                case 'preset':
                                    this.layoutService.layoutConfig.update((state) => ({ ...state, preset: preference.value! }));
                                    break;
                                case 'primary':
                                    this.layoutService.layoutConfig.update((state) => ({ ...state, primary: preference.value! }));
                                    break;
                                case 'menuMode':
                                    this.layoutService.layoutConfig.update((state) => ({ ...state, menuMode: preference.value! }));
                                    break;
                                case 'surface':
                                    this.layoutService.layoutConfig.update((state) => ({ ...state, surface: preference.value! }));
                                    break;
                                case 'darkTheme':
                                    this.layoutService.layoutConfig.update((state) => ({ ...state, darkTheme: preference.value?.toLowerCase().includes('true') }));
                                    break;
                                case 'menuHoverActive':
                                    this.layoutService.layoutState.update((state) => ({ ...state, menuHoverActive: preference.value?.toLowerCase().includes('true') }));
                                    break;
                                case 'staticMenuDesktopInactive':
                                    this.layoutService.layoutState.update((state) => ({ ...state, staticMenuDesktopInactive: preference.value?.toLowerCase().includes('true') }));
                                    break;
                                case 'overlayMenuActive':
                                    this.layoutService.layoutState.update((state) => ({ ...state, overlayMenuActive: preference.value?.toLowerCase().includes('true') }));
                                    break;
                                case 'configSidebarVisible':
                                    this.layoutService.layoutState.update((state) => ({ ...state, configSidebarVisible: preference.value?.toLowerCase().includes('true') }));
                                    break;
                                case 'staticMenuMobileActive':
                                    this.layoutService.layoutState.update((state) => ({ ...state, staticMenuMobileActive: preference.value?.toLowerCase().includes('true') }));
                                    break;
                            }
                        });
                    }
                }
            });
    }

    protected enablePush(): void {
        this.pushNotificationService.requestPermissionAndSubscribe().then((enabled) => {
            this.pushPromptVisible = false;
            if (enabled) this.toastService.success('Notifiche attive', 'Riceverai un promemoria prima degli eventi a cui hai dato disponibilità.');
            else this.toastService.info('Notifiche non attivate', 'Puoi attivarle in qualsiasi momento dal tuo profilo.');
        });
    }

    protected dismissPushPrompt(): void {
        this.pushPromptVisible = false;
        sessionStorage.setItem(AppComponent.PUSH_PROMPT_DISMISSED, 'true');
    }

    @HostListener('window:unload', ['$event'])
    unloadHandler(event: any) {
        // Clear all the storage when leaves the app
        this.localStorageService.clear();
    }
}
