import { CATALOGUE_MANAGER_ROLES, FINANCE_MANAGER_ROLES, RoleEnums, RoleLabelsMap, TENANT_ADMIN_ROLES, TENANT_MANAGER_ROLES } from './role.enums';

describe('RoleLabelsMap', () => {
    it('provides an Italian label for every supported role without changing its code', () => {
        expect(RoleLabelsMap).toEqual([
            { name: 'Super amministratore', code: RoleEnums.SUPER_ADMIN },
            { name: 'Amministratore', code: RoleEnums.ADMIN },
            { name: 'Direttivo', code: RoleEnums.BOARD },
            { name: 'Tesoriere', code: RoleEnums.TREASURER },
            { name: 'Archivista', code: RoleEnums.ARCHIVIST },
            { name: 'Utente', code: RoleEnums.USER },
            { name: 'Utente esterno', code: RoleEnums.USER_EXTERNAL }
        ]);
    });
});

describe('role groups', () => {
    it('gives the board the association management but not the administration', () => {
        expect(TENANT_ADMIN_ROLES).toEqual([RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN]);
        expect(TENANT_MANAGER_ROLES).toEqual([RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.BOARD]);
        expect(CATALOGUE_MANAGER_ROLES).toEqual([...TENANT_MANAGER_ROLES, RoleEnums.ARCHIVIST]);
        expect(FINANCE_MANAGER_ROLES).toEqual([...TENANT_MANAGER_ROLES, RoleEnums.TREASURER]);
    });
});
