import { Filter } from ".";

export class BooleanFilter extends Filter<boolean> {
    constructor(booleanFilter?: BooleanFilter) {
        super(booleanFilter);
    }
}