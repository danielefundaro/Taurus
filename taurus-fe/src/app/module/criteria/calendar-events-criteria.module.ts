import { CommonOpenSearchCriteria } from './common-open-search-criteria.module';
import { DateFilter, StateFilter, StringFilter } from './filter';

export class CalendarEventsCriteria extends CommonOpenSearchCriteria {
    startDate?: DateFilter;
    endDate?: DateFilter;
    location?: StringFilter;
    state?: StateFilter;
}
