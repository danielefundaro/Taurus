import { Filter } from ".";

export class RangeFilter<T> extends Filter<T> {
    greaterThan?: T;
    lessThan?: T;
    greaterThanOrEqual?: T;
    lessThanOrEqual?: T;

    constructor(range?: RangeFilter<T>) {
        super(range);
        this.greaterThan = range?.greaterThan;
        this.lessThan = range?.lessThan;
        this.greaterThanOrEqual = range?.greaterThanOrEqual;
        this.lessThanOrEqual = range?.lessThanOrEqual;
    }
}