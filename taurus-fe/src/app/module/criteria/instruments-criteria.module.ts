import { CommonOpenSearchCriteria } from "./common-open-search-criteria.module";
import { BooleanFilter } from "./filter/boolean-filter.module";

export class InstrumentsCriteria extends CommonOpenSearchCriteria {
    playbackConfigured?: BooleanFilter;
}