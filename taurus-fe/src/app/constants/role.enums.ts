export enum RoleEnums {
    SUPER_ADMIN = 'ROLE_SUPER_ADMIN',
    ADMIN = 'ROLE_ADMIN',
    BOARD = 'ROLE_BOARD',
    TREASURER = 'ROLE_TREASURER',
    ARCHIVIST = 'ROLE_ARCHIVIST',
    USER = 'ROLE_USER',
    USER_EXTERNAL = 'ROLE_USER_EXTERNAL',
    UNKNOWN = ''
}

export interface RoleLabel {
    name: string;
    code: RoleEnums;
}

export const RoleLabelsMap: RoleLabel[] = [
    { name: 'Super amministratore', code: RoleEnums.SUPER_ADMIN },
    { name: 'Amministratore', code: RoleEnums.ADMIN },
    { name: 'Direttivo', code: RoleEnums.BOARD },
    { name: 'Tesoriere', code: RoleEnums.TREASURER },
    { name: 'Archivista', code: RoleEnums.ARCHIVIST },
    { name: 'Utente', code: RoleEnums.USER },
    { name: 'Utente esterno', code: RoleEnums.USER_EXTERNAL }
];

/** Platform and tenant administrators: tenant configuration, technical tools and the administrators themselves. */
export const TENANT_ADMIN_ROLES: readonly RoleEnums[] = [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN];

/** Who manages the association: catalogue, finance, inventory, calendar, events and users. */
export const TENANT_MANAGER_ROLES: readonly RoleEnums[] = [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.BOARD];

export const CATALOGUE_MANAGER_ROLES: readonly RoleEnums[] = [...TENANT_MANAGER_ROLES, RoleEnums.ARCHIVIST];

export const FINANCE_MANAGER_ROLES: readonly RoleEnums[] = [...TENANT_MANAGER_ROLES, RoleEnums.TREASURER];
