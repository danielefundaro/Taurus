import { CommonOpenSearchCriteria } from "./common-open-search-criteria.module";
import { LongFilter, StringFilter } from "./filter";

export class TracksCriteria extends CommonOpenSearchCriteria {
    composer?: StringFilter;
    arranger?: StringFilter;
    type?: StringFilter;
    instrumentId?: LongFilter;
}