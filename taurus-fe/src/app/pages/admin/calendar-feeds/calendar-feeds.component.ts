import { Component } from '@angular/core';
import { CalendarFeedPanelComponent } from '../../../components/calendar-feed-panel/calendar-feed-panel.component';
import { ImportsModule } from '../../../imports';

@Component({
    selector: 'app-admin-calendar-feeds',
    standalone: true,
    imports: [ImportsModule, CalendarFeedPanelComponent],
    template: `<p-confirmdialog /><p-fluid><app-page-header kicker="Amministrazione" title="Feed calendario" subtitle="Gestisci i calendari condivisi del tenant e revoca i feed personali in caso di incidente." /><div class="card"><app-calendar-feed-panel [admin]="true" /></div></p-fluid>`
})
export class CalendarFeedsComponent {}
