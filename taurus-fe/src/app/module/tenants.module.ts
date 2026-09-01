import { CommonFieldsOpenSearch } from "./common-fields-open-search.module";

export class Tenants extends CommonFieldsOpenSearch {
    code?: string;
    email?: string;
    domain?: string;
    maxUsers?: number;
    expireDate?: Date;
    active?: boolean;
    address?: string;
    postalCode?: string;
    city?: string;
    province?: string;
    country?: string;
    taxCode?: string;
    vatNumber?: string;
    logoUrl?: string;
    timeZone?: string = 'Europe/Rome';
}
