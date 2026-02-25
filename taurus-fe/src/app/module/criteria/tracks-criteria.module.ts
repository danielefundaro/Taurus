import { CommonOpenSearchCriteria } from "./common-open-search-criteria.module";
import { LongFilter, StateFilter, StringFilter } from "./filter";

export class TracksCriteria extends CommonOpenSearchCriteria {
    subName?: StringFilter;
    composer?: StringFilter;
    arranger?: StringFilter;
    tempo?: StringFilter;
    tone?: StringFilter;
    state?: StateFilter;
    type?: StringFilter;
    instrumentId?: LongFilter;
}