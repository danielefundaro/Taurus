import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { MenuModule } from 'primeng/menu';
import { DatePickerModule } from 'primeng/datepicker';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { DetailSectionComponent } from '../../../../components/detail-section/detail-section.component';
import { EmptyStateComponent } from '../../../../components/empty-state/empty-state.component';
import { ListRowComponent } from '../../../../components/list-row/list-row.component';
import { Notices, Page } from '../../../../module';
import { ConfirmService, NotificationPresentationService } from '../../../../service';

@Component({
    standalone: true,
    selector: 'app-notifications-widget',
    imports: [ButtonModule, MenuModule, DatePickerModule, FormsModule, DatePipe, DetailSectionComponent, EmptyStateComponent, ListRowComponent],
    templateUrl: './notification-widget.component.html',
    styleUrl: './notification-widget.component.scss'
})
export class NotificationsWidgetComponent implements OnChanges {
    protected items: any[] = [];
    protected currentPage: number = 0;
    protected rows: number = 10;
    protected totalRecords: number = 0;

    @Input() notices?: Page<Notices>;
    @Input() view: 'ACTIVE' | 'SNOOZED' = 'ACTIVE';
    @Output() markAsRead: EventEmitter<number[] | null> = new EventEmitter<number[] | null>();
    @Output() navigateToNotice: EventEmitter<Notices> = new EventEmitter<Notices>();
    @Output() delete: EventEmitter<number[] | null> = new EventEmitter<number[] | null>();
    @Output() pageChange: EventEmitter<{ page: number; size: number }> = new EventEmitter<{ page: number; size: number }>();
    @Output() viewChange = new EventEmitter<'ACTIVE' | 'SNOOZED'>();
    @Output() snooze = new EventEmitter<{ notice: Notices; until: Date }>();
    @Output() unsnooze = new EventEmitter<Notices>();
    @Output() disableCategory = new EventEmitter<Notices>();
    protected readonly customSnooze: Record<number, Date | undefined> = {};

    constructor(
        private readonly confirmService: ConfirmService,
        private readonly notificationPresentation: NotificationPresentationService
    ) {
        this.items = [
            {
                label: 'Elimina tutti',
                icon: 'pi pi-fw pi-trash',
                disabled: true,
                name: 'deleteAll',
                command: () => this.deleteAll()
            }
        ];
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (this.notices) {
            this.totalRecords = this.notices.totalElements;
            this.currentPage = this.notices.number;
            this.rows = this.notices.size || this.rows;
            this.items.find((item) => item.name === 'deleteAll')!.disabled = this.totalRecords === 0;
        }
    }

    protected get unreadCount(): number {
        return this.notices?.content.filter((notice) => !notice.readDate).length ?? 0;
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
        return this.notices ? !this.notices.last : false;
    }

    protected mark(notice: Notices): void {
        if (notice.targetPath?.startsWith('/') && !notice.targetPath.startsWith('//')) {
            this.navigateToNotice.emit(notice);
            return;
        }
        if (!notice.readDate) {
            this.markAsRead.emit([notice.id]);
        }
    }

    protected noticeIcon(notice: Notices): string {
        return notice.readDate ? 'pi pi-check' : this.notificationPresentation.icon(notice.source);
    }

    protected deleteOne(notice: Notices): void {
        this.confirmService.confirmDestructive({
            title: 'Elimina notifica',
            consequence: `La notifica “${notice.name}” verrà eliminata definitivamente.`,
            actionLabel: 'Elimina',
            accept: () => this.delete.emit([notice.id])
        });
    }

    /**
     * L'opt-out di categoria vale soltanto per le notifiche configurabili: una riga
     * con politica REQUIRED non può essere soppressa dal centro notifiche.
     */
    protected canDisableCategory(notice: Notices): boolean {
        return !!notice.source && notice.preferencePolicy !== 'REQUIRED';
    }

    protected snoozeForOneHour(notice: Notices): void {
        this.snooze.emit({ notice, until: new Date(Date.now() + 60 * 60 * 1000) });
    }

    protected snoozeUntilTomorrow(notice: Notices): void {
        const tomorrow = new Date();
        tomorrow.setDate(tomorrow.getDate() + 1);
        tomorrow.setHours(8, 0, 0, 0);
        this.snooze.emit({ notice, until: tomorrow });
    }

    protected applyCustomSnooze(notice: Notices): void {
        const until = this.customSnooze[notice.id];
        if (until) this.snooze.emit({ notice, until });
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

    protected markAll(): void {
        if (this.notices && this.notices.totalElements > 0) {
            this.markAsRead.emit(null);
        }
    }

    private deleteAll(): void {
        if (this.notices && this.notices.totalElements > 0) {
            this.confirmService.confirmDestructive({
                title: 'Elimina tutte le notifiche',
                consequence: 'Tutte le notifiche verranno eliminate definitivamente.',
                actionLabel: 'Elimina tutte',
                accept: () => this.delete.emit(null)
            });
        }
    }
}
