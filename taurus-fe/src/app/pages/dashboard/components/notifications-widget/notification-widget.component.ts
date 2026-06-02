import { NgClass } from "@angular/common";
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { MenuModule } from 'primeng/menu';
import { PaginatorModule } from "primeng/paginator";
import { Notices, Page } from '../../../../module';

@Component({
    standalone: true,
    selector: 'app-notifications-widget',
    imports: [
        ButtonModule,
        MenuModule,
        NgClass,
        PaginatorModule,
    ],
    templateUrl: './notification-widget.component.html',
    styleUrl: './notification-widget.component.scss',
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

    constructor() {
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
        this.delete.emit([notice.id]);
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
            this.delete.emit();
        }
    }
}
