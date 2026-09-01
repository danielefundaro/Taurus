import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { ConfirmationService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { MenuModule } from 'primeng/menu';
import { Notices, Page } from '../../../../module';

@Component({
    standalone: true,
    selector: 'app-notifications-widget',
    imports: [ButtonModule, ConfirmDialogModule, MenuModule],
    templateUrl: './notification-widget.component.html',
    styleUrl: './notification-widget.component.scss',
    providers: [ConfirmationService]
})
export class NotificationsWidgetComponent implements OnChanges {
    protected items: any[] = [];
    protected currentPage: number = 0;
    protected rows: number = 10;
    protected totalRecords: number = 0;

    @Input() notices?: Page<Notices>;
    @Output() markAsRead: EventEmitter<number[] | null> = new EventEmitter<number[] | null>();
    @Output() navigateToNotice: EventEmitter<Notices> = new EventEmitter<Notices>();
    @Output() delete: EventEmitter<number[] | null> = new EventEmitter<number[] | null>();
    @Output() pageChange: EventEmitter<{ page: number; size: number }> = new EventEmitter<{ page: number; size: number }>();

    constructor(private readonly confirmationService: ConfirmationService) {
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
        if (notice.targetPath?.startsWith('/finance')) {
            this.navigateToNotice.emit(notice);
            return;
        }
        if (!notice.readDate) {
            this.markAsRead.emit([notice.id]);
        }
    }

    protected deleteOne(notice: Notices): void {
        this.confirmationService.confirm({
            header: 'Conferma eliminazione',
            message: 'Eliminare questa notifica?',
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Elimina',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () => this.delete.emit([notice.id])
        });
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
            this.confirmationService.confirm({
                header: 'Conferma eliminazione',
                message: 'Eliminare tutte le notifiche?',
                icon: 'pi pi-exclamation-triangle',
                acceptLabel: 'Elimina',
                rejectLabel: 'Annulla',
                acceptButtonProps: { severity: 'danger' },
                rejectButtonProps: { severity: 'secondary' },
                accept: () => this.delete.emit(null)
            });
        }
    }
}
