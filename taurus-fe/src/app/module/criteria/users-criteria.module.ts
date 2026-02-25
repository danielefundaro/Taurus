import { CommonOpenSearchCriteria } from "./common-open-search-criteria.module";
import { BooleanFilter, DateFilter, LongFilter, RoleFilter, StringFilter } from "./filter";

export class UsersCriteria extends CommonOpenSearchCriteria {
    lastName?: StringFilter;
    birthDate?: DateFilter;
    email?: StringFilter;
    roles?: RoleFilter;
    active?: BooleanFilter;
    instrumentId?: LongFilter;
}