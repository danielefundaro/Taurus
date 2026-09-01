import { StateEnums } from '../constants';
import { CommonFieldsOpenSearch } from './common-fields-open-search.module';
import { EventCost } from './event-cost.module';
import { EventPresentUser } from './event-present-user.module';
import { EventUserEntry } from './event-user-entry.module';

export class CalendarEvents extends CommonFieldsOpenSearch {
    startDate?: Date;
    endDate?: Date;
    location?: string;
    fee?: number;
    state?: StateEnums;
    costs?: EventCost[];
    availableUsers?: EventUserEntry[];
    unavailableUsers?: EventUserEntry[];
    presentUsers?: EventPresentUser[];
    reminderMinutes?: number;
    seriesId?: number;
    originalStartDate?: Date;
    seriesSequence?: number;
    seriesException?: boolean;
}
