import { CommonFieldsOpenSearch } from "./common-fields-open-search.module";

export class Tenants extends CommonFieldsOpenSearch {
    code?: string;
    email?: string;
    domain?: string;
    active?: boolean;
}