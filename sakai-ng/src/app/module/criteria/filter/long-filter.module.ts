import { RangeFilter } from ".";

export class LongFilter extends RangeFilter<number> {
    constructor(longFilter?: LongFilter) {
        super(longFilter);
    }
}