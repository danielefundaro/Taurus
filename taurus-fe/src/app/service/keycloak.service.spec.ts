import { TestBed } from '@angular/core/testing';
import Keycloak from 'keycloak-js';
import { KeycloakService } from './keycloak.service';

describe('KeycloakService', () => {
    it('ends the Keycloak session without clearing the token, which would start a new login over the logout', async () => {
        const keycloak = jasmine.createSpyObj<Keycloak>('Keycloak', ['logout', 'clearToken', 'login', 'isTokenExpired'], { authenticated: true });
        keycloak.logout.and.resolveTo();
        keycloak.isTokenExpired.and.returnValue(false);
        TestBed.configureTestingModule({ providers: [{ provide: Keycloak, useValue: keycloak }] });
        const service = TestBed.inject(KeycloakService);
        expect(service.isUserLoggedIn).toBeTrue();

        await service.logout();

        expect(keycloak.logout).toHaveBeenCalledOnceWith();
        expect(keycloak.clearToken).not.toHaveBeenCalled();
        expect(keycloak.login).not.toHaveBeenCalled();
        expect(service.isUserLoggedIn).toBeFalse();
    });
});
