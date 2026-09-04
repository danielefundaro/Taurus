import { RangeFilter } from './range-filter.module';

export class DateFilter extends RangeFilter<Date> {
    constructor(dateFilter?: DateFilter) {
        super(dateFilter);
    }
}
