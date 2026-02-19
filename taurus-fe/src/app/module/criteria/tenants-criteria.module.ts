import { CommonOpenSearchCriteria } from "./common-open-search-criteria.module";
import { BooleanFilter, StringFilter } from "./filter";

export class TenantsCriteria extends CommonOpenSearchCriteria {
    code?: StringFilter;
    email?: StringFilter;
    domain?: StringFilter;
    active?: BooleanFilter;
}