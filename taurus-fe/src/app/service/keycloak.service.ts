import { inject, Injectable } from "@angular/core";
import Keycloak, { KeycloakProfile } from 'keycloak-js';
import { BehaviorSubject } from "rxjs";
import { RoleEnums } from "../constants";

@Injectable({
    providedIn: 'root'
})
export class KeycloakService {
    private readonly keycloak = inject(Keycloak);
    private readonly $isUserLoggedIn: BehaviorSubject<boolean>;

    public get isUserLoggedIn(): boolean {
        return this.$isUserLoggedIn.value;
    }

    public get token(): string | undefined {
        return this.keycloak.token;
    }

    public get currentUserRole(): RoleEnums {
        return this.keycloak.idTokenParsed?.['role'] as RoleEnums || RoleEnums.UNKNOWN;
    }

    constructor() {
        this.$isUserLoggedIn = new BehaviorSubject(!this.keycloak.isTokenExpired());
    }

    public accountManagement(): Promise<void> {
        return this.keycloak.accountManagement();
    }

    public loadUserInfo(): Promise<{}> {
        return this.keycloak.loadUserInfo();
    }

    public loadUserProfile(): Promise<KeycloakProfile> {
        return this.keycloak.loadUserProfile();
    }

    public refreshToken(): string | undefined {
        return this.keycloak.refreshToken;
    }

    public logout() {
        return this.keycloak.logout().then(() => {
            this.$isUserLoggedIn.next(false);
            this.keycloak.clearToken();
        });
    }
}
