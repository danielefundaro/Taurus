import { Filter } from './filter.module';

export class StringFilter extends Filter<string> {
    public contains?: string;
    public doesNotContain?: string;

    constructor(stringFilter?: StringFilter) {
        super(stringFilter);
    }
}
