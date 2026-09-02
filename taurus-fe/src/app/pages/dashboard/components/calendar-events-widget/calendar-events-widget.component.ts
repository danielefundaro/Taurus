import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { RouterModule } from '@angular/router';
import { DetailSectionComponent } from '../../../../components/detail-section/detail-section.component';
import { EmptyStateComponent } from '../../../../components/empty-state/empty-state.component';
import { ListRowComponent } from '../../../../components/list-row/list-row.component';
import { CalendarEvents, Page } from '../../../../module';

@Component({
    selector: 'app-calendar-events-widget',
    imports: [CommonModule, RouterModule, DetailSectionComponent, EmptyStateComponent, ListRowComponent],
    templateUrl: './calendar-events-widget.component.html',
    styleUrl: './calendar-events-widget.component.scss',
    host: {
        class: 'col-span-12 xl:col-span-6'
    },
    changeDetection: ChangeDetectionStrategy.Default
})
export class CalendarEventsWidgetComponent implements OnChanges {
    @Input() events?: Page<CalendarEvents>;
    @Output() pageChange: EventEmitter<{ page: number; size: number }> = new EventEmitter<{ page: number; size: number }>();

    protected currentPage: number = 0;
    protected rows: number = 4;
    protected totalRecords: number = 0;

    ngOnChanges(): void {
        if (this.events) {
            this.totalRecords = this.events.totalElements;
            this.currentPage = this.events.number;
            this.rows = this.events.size || this.rows;
        }
    }

    protected get visibleEvents(): CalendarEvents[] {
        return this.events?.content ?? [];
    }

    protected get firstRecord(): number {
        return this.totalRecords === 0 ? 0 : this.currentPage * this.rows + 1;
    }

    protected get lastRecord(): number {
        return Math.min((this.currentPage + 1) * this.rows, this.totalRecords);
    }

    protected get canGoBack(): boolean {
        return this.currentPage > 0;
    }

    protected get canGoForward(): boolean {
        return this.events ? !this.events.last : false;
    }

    protected previousPage(): void {
        if (this.canGoBack) {
            this.pageChange.emit({ page: this.currentPage - 1, size: this.rows });
        }
    }

    protected nextPage(): void {
        if (this.canGoForward) {
            this.pageChange.emit({ page: this.currentPage + 1, size: this.rows });
        }
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
