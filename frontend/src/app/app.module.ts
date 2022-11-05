import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { DefaultRoutingModule } from './auth/pages/default/default-routing.module';
import { DefaultComponent } from './auth/pages/default/default.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { FlexLayoutModule } from '@angular/flex-layout';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatMenuModule } from '@angular/material/menu';
import { MainTranslateModule } from './main-translate/main-translate.module';
import { KeycloakInitModule } from './init/keycloak-init.module';


@NgModule({
    declarations: [
        AppComponent,
        DefaultComponent,
    ],
    imports: [
        BrowserModule,
        AppRoutingModule,
        DefaultRoutingModule,
        BrowserAnimationsModule,
        FlexLayoutModule,
        MatToolbarModule,
        MatIconModule,
        MatButtonModule,
        MatSidenavModule,
        MatListModule,
        MatProgressBarModule,
        MatTooltipModule,
        MatSnackBarModule,
        MatMenuModule,
        MainTranslateModule,
        KeycloakInitModule,
    ],
    providers: [],
    bootstrap: [AppComponent]
})
export class AppModule { }
