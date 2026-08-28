import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { RouterModule } from '@angular/router';
import { CalendarEvents } from '../../../../module';

@Component({
    selector: 'app-calendar-events-widget',
    imports: [CommonModule, RouterModule],
    templateUrl: './calendar-events-widget.component.html',
    styleUrl: './calendar-events-widget.component.scss',
    host: {
        class: 'col-span-12 xl:col-span-6'
    },
    changeDetection: ChangeDetectionStrategy.Default
})
export class CalendarEventsWidgetComponent {
    @Input() events: CalendarEvents[] = [];

    protected get visibleEvents(): CalendarEvents[] {
        return this.events.slice(0, 4);
    }

    protected relativeDate(value?: Date): string {
        const days = this.daysUntil(value);

        if (days === null) return 'Data da definire';
        if (days < 0) return 'In corso';
        if (days === 0) return 'Oggi';
        if (days === 1) return 'Domani';
        if (days <= 30) return `Tra ${days} giorni`;
        return 'In programma';
    }

    protected isSoon(value?: Date): boolean {
        const days = this.daysUntil(value);
        return days !== null && days >= 0 && days <= 2;
    }

    private daysUntil(value?: Date): number | null {
        if (!value) return null;

        const eventDate = new Date(value as unknown as string);
        if (Number.isNaN(eventDate.getTime())) return null;

        const today = new Date();
        today.setHours(0, 0, 0, 0);
        eventDate.setHours(0, 0, 0, 0);
        return Math.round((eventDate.getTime() - today.getTime()) / 86_400_000);
    }
}
