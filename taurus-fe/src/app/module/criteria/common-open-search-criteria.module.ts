import { LongFilter, StringFilter } from "./filter";

export class CommonOpenSearchCriteria {
    id?: LongFilter;
    name?: StringFilter;
    description?: StringFilter;
    page?: number;
    size?: number;
    sort?: Array<string>;
}