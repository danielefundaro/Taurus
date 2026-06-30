import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { RippleModule } from 'primeng/ripple';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ImportsModule } from '../../../../imports';
import { CalendarEvents } from '../../../../module';

@Component({
    selector: 'app-calendar-events-widget',
    imports: [
        RouterModule,
        TableModule,
        ButtonModule,
        RippleModule,
        TagModule,
        ImportsModule,
    ],
    templateUrl: './calendar-events-widget.component.html',
    styleUrl: './calendar-events-widget.component.scss',
    host: {
        class: 'col-span-12 xl:col-span-6',
    },
    changeDetection: ChangeDetectionStrategy.Default,
})
export class CalendarEventsWidgetComponent {
    @Input() events: CalendarEvents[] = [];
}
