import { HTTP_INTERCEPTORS, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { ApplicationConfig, isDevMode, LOCALE_ID, provideZoneChangeDetection } from '@angular/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter } from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';
import Aura from '@primeng/themes/aura';
import { AutoRefreshTokenService, provideKeycloak, UserActivityService, withAutoRefreshToken } from 'keycloak-angular';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { appRoutes } from './app.routes';
import { HttpInterceptorService } from './app/interceptor/http-interceptor.service';
import { LoadingService } from './app/service';
import { environment } from "./environments/environment";

export const appConfig: ApplicationConfig = {
    providers: [
        provideZoneChangeDetection({ eventCoalescing: true }),
        provideRouter(appRoutes),
        provideAnimationsAsync(),
        provideKeycloak({
            config: {
                url: environment.keycloak.baseurl,
                realm: environment.keycloak.realm,
                clientId: environment.keycloak.clientId,
            },
            initOptions: {
                onLoad: 'login-required',
                // scope: 'openid profile email offline_access'
            },
            features: [
                withAutoRefreshToken({
                    onInactivityTimeout: 'logout'
                })
            ],
            providers: [AutoRefreshTokenService, UserActivityService],
        }),
        providePrimeNG({
            theme: {
                preset: Aura,
                options: {
                    darkModeSelector: '.app-dark'
                }
            }
        }),
        {
            provide: HTTP_INTERCEPTORS,
            useClass: HttpInterceptorService,
            multi: true,
        },
        provideHttpClient(withInterceptorsFromDi()),
        MessageService,
        ConfirmationService,
        LoadingService,
        { provide: LOCALE_ID, useValue: 'it' },
        provideServiceWorker('ngsw-worker.js', { enabled: !isDevMode(), registrationStrategy: 'registerWhenStable:30000' }),
    ],
};