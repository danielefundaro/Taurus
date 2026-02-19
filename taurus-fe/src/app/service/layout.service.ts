import { computed, effect, Injectable, signal } from '@angular/core';
import { first, of, Subject, switchMap } from 'rxjs';
import { Preferences, PreferencesCriteria } from '../module';
import { LocalStorageService } from './local-storage.service';
import { PreferencesService } from './preferences.service';

export interface LayoutConfig {
    preset?: string;
    primary?: string;
    surface?: string | null;
    darkTheme?: boolean;
    menuMode?: string;
}

interface LayoutState {
    staticMenuDesktopInactive?: boolean;
    overlayMenuActive?: boolean;
    configSidebarVisible?: boolean;
    staticMenuMobileActive?: boolean;
    menuHoverActive?: boolean;
}

interface MenuChangeEvent {
    key: string;
    routeEvent?: boolean;
}

@Injectable({
    providedIn: 'root'
})
export class LayoutService {
    _config: LayoutConfig = {
        preset: 'Aura',
        primary: 'emerald',
        surface: null,
        darkTheme: false,
        menuMode: 'static'
    };

    _state: LayoutState = {
        staticMenuDesktopInactive: false,
        overlayMenuActive: false,
        configSidebarVisible: false,
        staticMenuMobileActive: false,
        menuHoverActive: false
    };

    layoutConfig = signal<LayoutConfig>(this._config);

    layoutState = signal<LayoutState>(this._state);

    private readonly configUpdate = new Subject<LayoutConfig>();

    private readonly overlayOpen = new Subject<any>();

    private readonly menuSource = new Subject<MenuChangeEvent>();

    private readonly resetSource = new Subject();

    menuSource$ = this.menuSource.asObservable();

    resetSource$ = this.resetSource.asObservable();

    configUpdate$ = this.configUpdate.asObservable();

    overlayOpen$ = this.overlayOpen.asObservable();

    theme = computed(() => (this.layoutConfig()?.darkTheme ? 'light' : 'dark'));

    isSidebarActive = computed(() => this.layoutState().overlayMenuActive || this.layoutState().staticMenuMobileActive);

    isDarkTheme = computed(() => this.layoutConfig().darkTheme);

    getPrimary = computed(() => this.layoutConfig().primary);

    getSurface = computed(() => this.layoutConfig().surface);

    isOverlay = computed(() => this.layoutConfig().menuMode === 'overlay');

    transitionComplete = signal<boolean>(false);

    private initialized = false;

    constructor(
        private readonly preferencesService: PreferencesService,
        private readonly localStorageService: LocalStorageService,
    ) {
        effect(() => {
            const config = this.layoutConfig();

            if (config) {
                this.onConfigUpdate();

                this.saveConfig(config);
            }
        });

        effect(() => {
            const state = this.layoutState();

            if (state) {
                this.saveConfig(state);
            }
        })

        effect(() => {
            const config = this.layoutConfig();

            if (!this.initialized || !config) {
                this.initialized = true;
                return;
            }

            this.handleDarkModeTransition(config);
        });
    }

    private handleDarkModeTransition(config: LayoutConfig): void {
        if ((document as any).startViewTransition) {
            this.startViewTransition(config);
        } else {
            this.toggleDarkMode(config);
            this.onTransitionEnd();
        }
    }

    private startViewTransition(config: LayoutConfig): void {
        const transition = (document as any).startViewTransition(() => {
            this.toggleDarkMode(config);
        });

        transition.ready
            .then(() => {
                this.onTransitionEnd();
            })
            .catch(() => { });
    }

    toggleDarkMode(config?: LayoutConfig): void {
        const _config = config || this.layoutConfig();
        if (_config.darkTheme) {
            document.documentElement.classList.add('app-dark');
        } else {
            document.documentElement.classList.remove('app-dark');
        }
    }

    private onTransitionEnd() {
        this.transitionComplete.set(true);
        setTimeout(() => {
            this.transitionComplete.set(false);
        });
    }

    onMenuToggle() {
        if (this.isOverlay()) {
            this.layoutState.update((prev) => ({ ...prev, overlayMenuActive: !this.layoutState().overlayMenuActive }));

            if (this.layoutState().overlayMenuActive) {
                this.overlayOpen.next(null);
            }
        }

        if (this.isDesktop()) {
            this.layoutState.update((prev) => ({ ...prev, staticMenuDesktopInactive: !this.layoutState().staticMenuDesktopInactive }));
        } else {
            this.layoutState.update((prev) => ({ ...prev, staticMenuMobileActive: !this.layoutState().staticMenuMobileActive }));

            if (this.layoutState().staticMenuMobileActive) {
                this.overlayOpen.next(null);
            }
        }
    }

    isDesktop() {
        return window.innerWidth > 991;
    }

    isMobile() {
        return !this.isDesktop();
    }

    onConfigUpdate() {
        this._config = { ...this.layoutConfig() };
        this.configUpdate.next(this.layoutConfig());
    }

    private saveConfig(config: any) {
        for (let key of Object.keys(config)) {
            let preference = this.localStorageService.getItem(key);
            const value = config[key];

            if (!preference) {
                preference = new Preferences();
                preference.key = key;
                preference.value = value;
                this.localStorageService.setItem(key, preference);

                this.preferencesService.count().pipe(first(), switchMap(count => {
                    const criteria = new PreferencesCriteria();
                    criteria.size = count;
                    criteria.page = 0;

                    return this.preferencesService.getAll(criteria).pipe(first());
                }), switchMap(result => {
                    if (!result.content.some(p => p.key === key)) {
                        return this.preferencesService.create(preference!);
                    }

                    return of(undefined);
                })).subscribe({
                    next: (result?: Preferences) => {
                        if (result) {
                            this.localStorageService.setItem(key, result);
                        }
                    }
                });
            } else if (preference.value !== value) {
                preference.value = value;
                this.preferencesService.update(preference.id, preference).pipe(first()).subscribe(result => {
                    this.localStorageService.setItem(key, result);
                });
                this.localStorageService.setItem(key, preference);
            }
        }
    }

    onMenuStateChange(event: MenuChangeEvent) {
        this.menuSource.next(event);
    }

    reset() {
        this.resetSource.next(true);
    }
}
