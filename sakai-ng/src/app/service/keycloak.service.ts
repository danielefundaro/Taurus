import { inject, Injectable } from "@angular/core";
import Keycloak from 'keycloak-js';
import { BehaviorSubject } from "rxjs";

@Injectable({
    providedIn: 'root'
})
export class KeycloakService {
    private readonly keycloak = inject(Keycloak);
    public $isUserLoggedIn: BehaviorSubject<boolean>;
    public $loading: BehaviorSubject<boolean> = new BehaviorSubject<boolean>(false);

    public get token() {
        return this.keycloak.token;
    }

    constructor() {
        this.$isUserLoggedIn = new BehaviorSubject(!this.keycloak.isTokenExpired());
    }

    public refreshToken() {
        return this.keycloak.refreshToken;
    }

    public logout() {
        return this.keycloak.logout().then(() => this.$isUserLoggedIn.next(false));
    }
}
