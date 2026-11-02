import { TestBed } from '@angular/core/testing';
import Keycloak from 'keycloak-js';
import { RoleEnums } from '../constants/role.enums';
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

    describe('for the board', () => {
        const roles = [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.BOARD, RoleEnums.TREASURER, RoleEnums.USER].map((code) => ({ code }));

        function sessionOf(role: RoleEnums): KeycloakService {
            TestBed.configureTestingModule({ providers: [{ provide: Keycloak, useValue: { idTokenParsed: { role } } }] });
            return TestBed.inject(KeycloakService);
        }

        it('lets the board manage the association but not the administrators', () => {
            const service = sessionOf(RoleEnums.BOARD);

            expect(service.isTenantManager).toBeTrue();
            expect(service.isAdmin).toBeFalse();
            expect(service.canManageUser([RoleEnums.USER, RoleEnums.TREASURER])).toBeTrue();
            expect(service.canManageUser([RoleEnums.USER, RoleEnums.ADMIN])).toBeFalse();
            expect(service.canManageUser([RoleEnums.SUPER_ADMIN])).toBeFalse();
            expect(service.canManageUser(null)).toBeTrue();
            expect(service.assignableRoles(roles).map((role) => role.code)).toEqual([RoleEnums.BOARD, RoleEnums.TREASURER, RoleEnums.USER]);
        });

        it('lets the administrator manage the administrators too', () => {
            const service = sessionOf(RoleEnums.ADMIN);

            expect(service.isTenantManager).toBeTrue();
            expect(service.canManageUser([RoleEnums.ADMIN])).toBeTrue();
            expect(service.assignableRoles(roles).map((role) => role.code)).toEqual([RoleEnums.ADMIN, RoleEnums.BOARD, RoleEnums.TREASURER, RoleEnums.USER]);
        });

        it('does not count the other roles as managers', () => {
            expect(sessionOf(RoleEnums.ARCHIVIST).isTenantManager).toBeFalse();
        });
    });
});
