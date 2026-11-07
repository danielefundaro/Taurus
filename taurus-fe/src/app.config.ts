import { HTTP_INTERCEPTORS, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { ApplicationConfig, LOCALE_ID, provideZoneChangeDetection } from '@angular/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter } from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';
import { definePreset } from '@primeng/themes';
import Aura from '@primeng/themes/aura';
import { AutoRefreshTokenService, provideKeycloak, UserActivityService, withAutoRefreshToken } from 'keycloak-angular';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { appRoutes } from './app.routes';
import { HttpInterceptorService } from './app/interceptor/http-interceptor.service';
import { ACCESSIBLE_LIGHT_SCHEME, accessibleComponents, PRIMENG_TRANSLATION_IT, publicPage } from './app/constants';
import { LoadingService } from './app/service';
import { environment } from "./environments/environment";

/**
 * Il preset di partenza: Aura con i colori del tema portati sopra la soglia di contrasto. È il solo
 * tema delle pagine pubbliche, che non hanno il configuratore, e il primo disegno delle altre.
 */
const taurusPreset = definePreset(Aura, {
    semantic: { colorScheme: { light: ACCESSIBLE_LIGHT_SCHEME } },
    components: accessibleComponents('Aura')
});

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
            // The public pages (legal documents, signature of a parent) work without login: Keycloak is not initialized there.
            initOptions: publicPage()
                ? undefined
                : {
                      onLoad: 'login-required',
                      // scope: 'openid profile email offline_access'
                  },
            features: [
                withAutoRefreshToken({
                    sessionTimeout: 15 * 60 * 1000, // 15 minutes
                    onInactivityTimeout: 'logout'
                })
            ],
            providers: [AutoRefreshTokenService, UserActivityService],
        }),
        providePrimeNG({
            translation: PRIMENG_TRANSLATION_IT,
            theme: {
                preset: taurusPreset,
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
        // Registrato anche in sviluppo: la configurazione `development` di angular.json costruisce
        // il worker, e lasciarlo servito ma non registrato significa che una registrazione fatta una
        // volta sola sopravvive per sempre senza che nulla la aggiorni. Meglio che il locale si
        // comporti come la produzione, con AppUpdateService a far avanzare le versioni.
        provideServiceWorker('ngsw-worker.js', { enabled: true, registrationStrategy: 'registerWhenStable:30000' }),
    ],
}