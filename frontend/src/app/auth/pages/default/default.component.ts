import { Component, OnDestroy, OnInit } from '@angular/core';
import { KeycloakProfile } from 'keycloak-js';
import { Subscription } from 'rxjs';
import { SettingsService, UserService } from 'src/app/services';

@Component({
    templateUrl: './default.component.html',
    styleUrls: ['./default.component.scss']
})
export class DefaultComponent implements OnDestroy {
    public languages: string[];
    public user?: KeycloakProfile;
    public isLoggedIn!: boolean;
    public isDarkTheme: boolean;
    public isLoading!: boolean;
    private loadState: Subscription;

    constructor(private userService: UserService, private settingsService: SettingsService) {
        this.languages = settingsService.languages;
        this.isDarkTheme = this.settingsService.isDarkTheme;
        
        this.userService.isLoggedIn().then(data => this.isLoggedIn = data);
        this.userService.loadUserProfile().then(data => this.user = data);

        // Set default theme
        this.settingsService.setDefalutTheme();
        this.isDarkTheme = this.settingsService.isDarkTheme;

        // Check loading state
        this.loadState = this.settingsService.loadStateChanged().subscribe(data => this.isLoading = data);
    }

    ngOnDestroy(): void {
        this.loadState.unsubscribe();
    }

    public openProfile(): void {
        this.userService.redirectToProfile();
    }

    public changeTheme(): void {
        this.isDarkTheme = this.settingsService.toggleTheme();
    }

    public changeLang(lang: string): void {
        this.settingsService.currentLang = lang;
    }

    public signout(): void {
        this.userService.logout();
    }
}
