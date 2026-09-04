import { Filter } from './filter.module';

export class BooleanFilter extends Filter<boolean> {
    constructor(booleanFilter?: BooleanFilter) {
        super(booleanFilter);
    }
}
