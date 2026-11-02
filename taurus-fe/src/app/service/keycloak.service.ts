import { inject, Injectable } from "@angular/core";
import Keycloak, { KeycloakProfile } from 'keycloak-js';
import { BehaviorSubject } from "rxjs";
import { RoleEnums, TENANT_ADMIN_ROLES, TENANT_MANAGER_ROLES } from "../constants";

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

    public get currentUserId(): string | undefined {
        return this.keycloak.tokenParsed?.['sub'] as string | undefined;
    }

    public get currentUserRole(): RoleEnums {
        return this.keycloak.idTokenParsed?.['role'] as RoleEnums || RoleEnums.UNKNOWN;
    }

    /** True when the user chose this role among more roles of the session tenant. */
    public get currentUserRoleChosen(): boolean {
        return this.keycloak.idTokenParsed?.['role_choice'] === true;
    }

    public get currentUserFirstName(): string {
        return (this.keycloak.idTokenParsed?.['given_name'] as string) || '';
    }

    public get currentUserLastName(): string {
        return (this.keycloak.idTokenParsed?.['family_name'] as string) || '';
    }

    public get currentUserTenantCode(): string | undefined {
        return (this.keycloak.tokenParsed?.['tenant'] as string) || undefined;
    }

    /** Administrator of the tenant or of the platform: tenant configuration, technical tools, the administrators. */
    public get isAdmin(): boolean {
        return TENANT_ADMIN_ROLES.includes(this.currentUserRole);
    }

    /** Manages the association: administrators and board members. */
    public get isTenantManager(): boolean {
        return TENANT_MANAGER_ROLES.includes(this.currentUserRole);
    }

    /**
     * A board member manages the users of the tenant but not its administrators; the server decides in the end.
     */
    public canManageUser(roles?: readonly string[] | null): boolean {
        return this.isAdmin || !(roles ?? []).some((role) => TENANT_ADMIN_ROLES.includes(role as RoleEnums));
    }

    /** The roles offered when creating or editing a user: only an administrator gives the administrator role. */
    public assignableRoles<T extends { code: RoleEnums }>(roles: readonly T[]): T[] {
        return roles.filter((role) => role.code !== RoleEnums.SUPER_ADMIN && (this.isAdmin || role.code !== RoleEnums.ADMIN));
    }

    public get isUser(): boolean {
        return [RoleEnums.USER].includes(this.currentUserRole);
    }

    public get isUserExternal(): boolean {
        return this.currentUserRole === RoleEnums.USER_EXTERNAL;
    }

    constructor() {
        // On the public signature page Keycloak is not initialized: nobody is logged in.
        this.$isUserLoggedIn = new BehaviorSubject(!!this.keycloak.authenticated && !this.keycloak.isTokenExpired());
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

    /**
     * Ends the Keycloak session: keycloak-js leaves the page for the end-session endpoint. The token is not cleared
     * here: with `onLoad: 'login-required'` clearing it starts a new login, whose redirect replaces the logout one
     * and leaves the session open.
     */
    public logout(): Promise<void> {
        return this.keycloak.logout().then(() => this.$isUserLoggedIn.next(false));
    }
}
