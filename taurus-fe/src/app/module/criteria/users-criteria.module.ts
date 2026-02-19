import { CommonOpenSearchCriteria } from "./common-open-search-criteria.module";
import { BooleanFilter, DateFilter, LongFilter, StringFilter } from "./filter";

export class UsersCriteria extends CommonOpenSearchCriteria {
    lastName?: StringFilter;
    birthDate?: DateFilter;
    email?: StringFilter;
    roles?: StringFilter;
    active?: BooleanFilter;
    instrumentId?: LongFilter;
}