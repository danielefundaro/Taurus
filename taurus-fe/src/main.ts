import { registerLocaleData } from '@angular/common';
import localeIt from '@angular/common/locales/it';
import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app.component';
import { loadRuntimeConfig } from './runtime-config';

registerLocaleData(localeIt);

// `app.config` legge `environment` mentre viene importato, per costruire la configurazione di
// Keycloak: la configurazione a runtime va caricata prima, e il modulo importato dopo, altrimenti
// l'applicazione partirebbe con gli indirizzi compilati nel bundle.
loadRuntimeConfig()
    .then(() => import('./app.config'))
    .then(({ appConfig }) => bootstrapApplication(AppComponent, appConfig))
    .catch((err) => console.error(err));
