import { RangeFilter } from './range-filter.module';

export class LongFilter extends RangeFilter<number> {
    constructor(longFilter?: LongFilter) {
        super(longFilter);
    }
}
