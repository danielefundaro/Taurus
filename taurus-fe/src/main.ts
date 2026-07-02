import { registerLocaleData } from '@angular/common';
import localeIt from '@angular/common/locales/it';
import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app.config';
import { AppComponent } from './app.component';

registerLocaleData(localeIt);

bootstrapApplication(AppComponent, appConfig).catch((err) => console.error(err));
