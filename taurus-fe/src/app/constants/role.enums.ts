export enum RoleEnums {
    SUPER_ADMIN = 'ROLE_SUPER_ADMIN',
    ADMIN = 'ROLE_ADMIN',
    ARCHIVIST = 'ROLE_ARCHIVIST',
    USER = 'ROLE_USER',
    USER_EXTERNAL = 'ROLE_USER_EXTERNAL',
    UNKNOWN = '',
}

export interface RoleLabel {
    name: string;
    code: RoleEnums;
}

export const RoleLabelsMap: RoleLabel[] = [
    // { name: 'Super Admin', code: RoleEnums.SUPER_ADMIN },
    { name: 'Admin', code: RoleEnums.ADMIN },
    { name: 'Archivista', code: RoleEnums.ARCHIVIST },
    { name: 'Utente', code: RoleEnums.USER },
    { name: 'Utente Esterno', code: RoleEnums.USER_EXTERNAL },
    // { name: 'Sconosciuto', code: RoleEnums.UNKNOWN },
];