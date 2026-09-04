import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TableLazyLoadEvent } from 'primeng/table';
import { finalize, first } from 'rxjs';
import { ImportsModule } from '../../../imports';
import { NotificationDeliveryAdmin, NotificationDeliveryStatus } from '../../../module';
import { ConfirmService, NotificationDeliveryAdminService, NotificationPresentationService, ToastService } from '../../../service';

@Component({
    selector: 'app-notification-delivery',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './notification-delivery.component.html',
    styleUrl: './notification-delivery.component.scss'
})
export class NotificationDeliveryComponent implements OnInit {
    protected deliveries: NotificationDeliveryAdmin[] = [];
    protected selected: NotificationDeliveryAdmin[] = [];
    protected readonly skeletonRows = Array.from({ length: 12 });
    protected readonly statusOptions = [
        { label: 'Fallite', value: 'FAILED' },
        { label: 'In attesa', value: 'PENDING' },
        { label: 'Consegnate', value: 'DELIVERED' }
    ];
    protected readonly sortOptions = [
        { label: 'Evento meno recente', value: 'occurredAt,asc' },
        { label: 'Evento più recente', value: 'occurredAt,desc' },
        { label: 'Più tentativi', value: 'attempts,desc' },
        { label: 'Aggiornamento più recente', value: 'editDate,desc' },
        { label: 'ID crescente', value: 'id,asc' }
    ];
    protected totalRecords = 0;
    protected loading = false;
    protected loadError = false;
    protected status: NotificationDeliveryStatus = 'FAILED';
    protected sort = 'occurredAt,asc';
    protected retryingId?: number;
    protected bulkRetrying = false;
    protected lazyEvent: TableLazyLoadEvent = { first: 0, rows: 12, sortField: 'occurredAt', sortOrder: 1 };

    protected get tableFirst(): number {
        return this.lazyEvent.first ?? 0;
    }

    protected get tableRows(): number {
        return this.lazyEvent.rows ?? 12;
    }

    protected get tableSortField(): string {
        const sortField = this.lazyEvent.sortField;
        return (Array.isArray(sortField) ? sortField[0] : sortField) ?? 'occurredAt';
    }

    protected get tableSortOrder(): number {
        return this.lazyEvent.sortOrder ?? 1;
    }

    constructor(
        private readonly service: NotificationDeliveryAdminService,
        private readonly confirmService: ConfirmService,
        private readonly toastService: ToastService,
        protected readonly notificationPresentation: NotificationPresentationService,
        private readonly route: ActivatedRoute
    ) {}

    ngOnInit(): void {
        const status = this.route.snapshot.queryParamMap.get('status');
        if (status && ['PENDING', 'DELIVERED', 'FAILED'].includes(status)) this.status = status as NotificationDeliveryStatus;
    }

    protected load(event: TableLazyLoadEvent = this.lazyEvent): void {
        this.lazyEvent = event;
        const rows = event.rows ?? 12;
        const sortField = Array.isArray(event.sortField) ? event.sortField[0] : event.sortField;
        this.sort = `${sortField ?? 'occurredAt'},${(event.sortOrder ?? 1) < 0 ? 'desc' : 'asc'}`;
        this.loading = true;
        this.loadError = false;
        this.service
            .getDeliveries(this.status, Math.floor((event.first ?? 0) / rows), rows, this.sort)
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe({
                next: (page) => {
                    this.deliveries = page.content;
                    this.totalRecords = page.totalElements;
                    this.selected = this.selected.filter((selected) => page.content.some((entry) => entry.id === selected.id));
                },
                error: () => (this.loadError = true)
            });
    }

    protected onStatusChange(status: NotificationDeliveryStatus): void {
        this.status = status;
        this.selected = [];
        this.lazyEvent = { ...this.lazyEvent, first: 0 };
        this.load();
    }

    protected onSortChange(sort: string): void {
        const [sortField, direction] = sort.split(',');
        this.sort = sort;
        this.lazyEvent = { ...this.lazyEvent, first: 0, sortField, sortOrder: direction === 'desc' ? -1 : 1 };
        this.load();
    }

    protected get allVisibleSelected(): boolean {
        return this.deliveries.length > 0 && this.deliveries.every((delivery) => this.selected.some((selected) => selected.id === delivery.id));
    }

    protected toggleAllVisible(select: boolean): void {
        this.selected = select ? [...this.deliveries] : [];
    }

    protected emptyTitle(): string {
        if (this.status === 'PENDING') return 'Nessuna consegna in attesa';
        if (this.status === 'DELIVERED') return 'Nessuna consegna completata';
        return 'Nessuna consegna fallita';
    }

    protected emptyMessage(): string {
        if (this.status === 'PENDING') return 'Non ci sono eventi tecnici in attesa di elaborazione.';
        if (this.status === 'DELIVERED') return 'Non ci sono eventi tecnici consegnati da mostrare.';
        return 'Non ci sono eventi tecnici che richiedono un retry.';
    }

    protected retry(delivery: NotificationDeliveryAdmin): void {
        this.confirmService.confirmReversible({
            title: 'Riprova consegna',
            consequence: `La consegna tecnica ${delivery.id} verrà riportata in coda mantenendo la stessa chiave evento.`,
            actionLabel: 'Riprova',
            accept: () => {
                this.retryingId = delivery.id;
                this.service
                    .retry(delivery.id)
                    .pipe(
                        first(),
                        finalize(() => (this.retryingId = undefined))
                    )
                    .subscribe(() => {
                        this.toastService.success('Retry richiesto', 'La consegna è stata riportata in coda.');
                        this.load();
                    });
            }
        });
    }

    protected retrySelected(): void {
        const ids = this.selected.map((delivery) => delivery.id);
        if (ids.length === 0 || ids.length > 100) return;
        this.confirmService.confirmReversible({
            title: 'Riprova consegne selezionate',
            consequence: `${ids.length} consegne verranno riportate in coda mantenendo le rispettive chiavi evento.`,
            actionLabel: 'Riprova selezionate',
            accept: () => {
                this.bulkRetrying = true;
                this.service
                    .retrySelected(ids)
                    .pipe(
                        first(),
                        finalize(() => (this.bulkRetrying = false))
                    )
                    .subscribe((result) => {
                        this.toastService.success('Retry richiesto', `${result.retriedCount} consegne riportate in coda.`);
                        this.selected = [];
                        this.load();
                    });
            }
        });
    }
}
