import { Component, OnInit } from '@angular/core';
import { KeycloakProfile } from 'keycloak-js';
import { SettingsService, UserService } from 'src/app/services';

@Component({
    templateUrl: './default.component.html',
    styleUrls: ['./default.component.scss']
})
export class DefaultComponent {
    public languages: string[];
    public user?: KeycloakProfile;
    public isLoggedIn!: boolean;
    public isDarkTheme: boolean;

    constructor(private userService: UserService, private settingsService: SettingsService) {
        this.languages = settingsService.languages;
        this.isDarkTheme = this.settingsService.isDarkTheme;
        
        this.userService.isLoggedIn().then(data => this.isLoggedIn = data);
        this.userService.loadUserProfile().then(data => this.user = data);
        this.settingsService.themeChanged().subscribe(isDarkTheme => this.setTheme(isDarkTheme));

        // Set default theme
        this.setTheme(this.settingsService.isDarkTheme);
    }

    public openProfile(): void {
        this.userService.redirectToProfile();
    }

    public changeTheme(): void {
        this.settingsService.isDarkTheme = !this.settingsService.isDarkTheme;
    }

    public changeLang(lang: string): void {
        this.settingsService.currentLang = lang;
    }

    public signout(): void {
        this.userService.logout();
    }

    private setTheme(isDarkTheme: boolean): void {
        this.isDarkTheme = isDarkTheme;
        document.body.classList.toggle('darkTheme', isDarkTheme);
    }
}
