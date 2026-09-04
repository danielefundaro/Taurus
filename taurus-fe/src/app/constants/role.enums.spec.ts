import { RoleEnums, RoleLabelsMap } from './role.enums';

describe('RoleLabelsMap', () => {
    it('provides an Italian label for every supported role without changing its code', () => {
        expect(RoleLabelsMap).toEqual([
            { name: 'Super amministratore', code: RoleEnums.SUPER_ADMIN },
            { name: 'Amministratore', code: RoleEnums.ADMIN },
            { name: 'Tesoriere', code: RoleEnums.TREASURER },
            { name: 'Archivista', code: RoleEnums.ARCHIVIST },
            { name: 'Utente', code: RoleEnums.USER },
            { name: 'Utente esterno', code: RoleEnums.USER_EXTERNAL }
        ]);
    });
});
