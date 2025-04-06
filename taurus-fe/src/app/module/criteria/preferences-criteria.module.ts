import { CommonCriteria } from ".";
import { StringFilter } from "./filter";

export class PreferencesCriteria extends CommonCriteria {
    key?: StringFilter;
    value?: StringFilter;
}