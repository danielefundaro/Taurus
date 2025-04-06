import { Filter } from ".";

export class StringFilter extends Filter<string> {
    public contains?: string;
    public doesNotContain?: string;

    constructor(stringFilter?: StringFilter) {
        super(stringFilter);
    }
}