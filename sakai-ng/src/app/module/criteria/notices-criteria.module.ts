import { CommonCriteria } from ".";
import { DateFilter, StringFilter } from "./filter";

export class NoticesCriteria extends CommonCriteria {
    name?: StringFilter;
    message?: StringFilter;
    readDate?: DateFilter;
}