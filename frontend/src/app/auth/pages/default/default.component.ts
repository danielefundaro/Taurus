import { Component, OnInit } from '@angular/core';
import { KeycloakProfile } from 'keycloak-js';
import { LanguageService, UserService } from 'src/app/services';

@Component({
    templateUrl: './default.component.html',
    styleUrls: ['./default.component.scss']
})
export class DefaultComponent {
    public languages: string[];
    public user?: KeycloakProfile;
    public isLoggedIn!: boolean;

    constructor(private userService: UserService, private languageSelected: LanguageService) {
        this.languages = languageSelected.languages;
        
        this.userService.isLoggedIn().then(data => this.isLoggedIn = data);
        this.userService.loadUserProfile().then(data => this.user = data);
    }

    public openProfile(): void {
        this.userService.redirectToProfile();
    }

    public changeLang(lang: string): void {
        this.languageSelected.current = lang;
    }

    public signout(): void {
        this.userService.logout();
    }
}
