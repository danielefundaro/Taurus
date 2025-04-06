import { LongFilter } from "./filter";

export class CommonCriteria {
    id!: LongFilter;
    distinct: boolean = false;
    page?: number;
    size?: number;
    sort?: Array<string>;
}