import { NgModule, APP_INITIALIZER } from '@angular/core';
import { CommonModule } from '@angular/common';

import { KeycloakAngularModule, KeycloakService } from 'keycloak-angular';
import { environment } from 'src/environments/environment.prod';


export function initializeKeycloak(keycloakService: KeycloakService) {
    return () => keycloakService.init({
        config: {
            url: environment.keycloak.baseurl,
            realm: environment.keycloak.realm,
            clientId: environment.keycloak.clientId,
        },
        loadUserProfileAtStartUp: false,
        initOptions: {
            flow: 'implicit',
            useNonce: true
        },
        bearerExcludedUrls: []
    });
}

@NgModule({
    declarations: [],
    imports: [
        CommonModule,
        KeycloakAngularModule,
    ],
    providers: [
        {
            provide: APP_INITIALIZER,
            useFactory: initializeKeycloak,
            multi: true,
            deps: [KeycloakService],
        }
    ]
})
export class KeycloakInitModule { }
