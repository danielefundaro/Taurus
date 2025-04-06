import { CommonOpenSearchCriteria } from "./common-open-search-criteria.module";
import { DateFilter, StringFilter } from "./filter";

export class AlbumsCriteria extends CommonOpenSearchCriteria {
    date?: DateFilter;
    trackName?: StringFilter;
}