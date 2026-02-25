import { RangeFilter } from ".";

export class DateFilter extends RangeFilter<Date> {
    constructor(dateFilter?: DateFilter) {
        super(dateFilter);
    }
}