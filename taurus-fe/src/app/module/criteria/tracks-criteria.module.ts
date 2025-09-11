import { CommonOpenSearchCriteria } from "./common-open-search-criteria.module";
import { BooleanFilter, LongFilter, StringFilter } from "./filter";

export class TracksCriteria extends CommonOpenSearchCriteria {
    subName?: StringFilter;
    composer?: StringFilter;
    arranger?: StringFilter;
    tempo?: StringFilter;
    tone?: StringFilter;
    complete?: BooleanFilter;
    type?: StringFilter;
    instrumentId?: LongFilter;
}