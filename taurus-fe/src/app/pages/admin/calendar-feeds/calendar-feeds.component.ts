import { Component } from '@angular/core';
import { CalendarFeedPanelComponent } from '../../../components/calendar-feed-panel/calendar-feed-panel.component';
import { ImportsModule } from '../../../imports';

@Component({
    selector: 'app-admin-calendar-feeds',
    standalone: true,
    imports: [ImportsModule, CalendarFeedPanelComponent],
    templateUrl: './calendar-feeds.component.html',
    styleUrl: './calendar-feeds.component.scss'
})
export class CalendarFeedsComponent { }
