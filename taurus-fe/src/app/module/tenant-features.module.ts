export enum TenantFeature {
    FINANCE = 'FINANCE',
    INVENTORY = 'INVENTORY'
}

export interface TenantFeatures {
    tenantCode: string;
    version: number;
    financeEnabled: boolean;
    inventoryEnabled: boolean;
}
