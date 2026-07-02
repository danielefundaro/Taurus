import { NgClass } from "@angular/common";
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { ConfirmationService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { MenuModule } from 'primeng/menu';
import { PaginatorModule } from "primeng/paginator";
import { Notices, Page } from '../../../../module';

@Component({
    standalone: true,
    selector: 'app-notifications-widget',
    imports: [
        ButtonModule,
        ConfirmDialogModule,
        MenuModule,
        NgClass,
        PaginatorModule,
    ],
    templateUrl: './notification-widget.component.html',
    styleUrl: './notification-widget.component.scss',
    providers: [ConfirmationService],
})
export class NotificationsWidgetComponent implements OnChanges {
    protected items: any[] = [];
    protected first: number = 0;
    protected rows: number = 10;
    protected totalRecords: number = 0;

    @Input() notices?: Page<Notices>;
    @Output() markAsRead: EventEmitter<number[] | null> = new EventEmitter<number[] | null>();
    @Output() delete: EventEmitter<number[] | null> = new EventEmitter<number[] | null>();
    @Output() pageChange: EventEmitter<{ page: number; size: number }> = new EventEmitter<{ page: number; size: number }>();

    constructor(private readonly confirmationService: ConfirmationService) {
        this.items = [
            {
                label: 'Segna tutti come letti',
                icon: 'pi pi-fw pi-check-square',
                disabled: true,
                name: 'markAll',
                command: () => this.markAll(),
            },
            {
                label: 'Elimina tutti',
                icon: 'pi pi-fw pi-trash',
                disabled: true,
                name: 'deleteAll',
                command: () => this.deleteAll(),
            },
        ];
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (this.notices) {
            this.totalRecords = this.notices.totalElements;
            this.items.find(item => item.name === 'deleteAll')!.disabled = this.totalRecords === 0;
            this.items.find(item => item.name === 'markAll')!.disabled = this.totalRecords === 0 || this.notices.content.every(notice => notice.readDate);
        }
    }

    protected mark(notice: Notices): void {
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
            accept: () => this.delete.emit([notice.id]),
        });
    }

    protected onPageChange(event: any): void {
        this.first = event.page;
        this.rows = event.rows;
        this.pageChange.emit({ page: this.first, size: this.rows });
    }

    private markAll(): void {
        if (this.notices && this.notices.totalElements > 0) {
            this.markAsRead.emit();
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
                accept: () => this.delete.emit(),
            });
        }
    }
}
