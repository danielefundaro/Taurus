import { CommonCriteria } from '.';
import { BooleanFilter, DateFilter, StringFilter } from './filter';

export class NoticesCriteria extends CommonCriteria {
    name?: StringFilter;
    message?: StringFilter;
    readDate?: DateFilter;
    source?: StringFilter;
    unread?: BooleanFilter;
    view?: 'ACTIVE' | 'SNOOZED';
}
