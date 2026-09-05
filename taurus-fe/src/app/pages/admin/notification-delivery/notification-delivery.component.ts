import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TableLazyLoadEvent } from 'primeng/table';
import { finalize, first } from 'rxjs';
import { ImportsModule } from '../../../imports';
import { NotificationDeliveryAdmin, NotificationDeliveryFilters, NotificationDeliveryOrigin, NotificationDeliveryStatus } from '../../../module';
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
        { label: 'Consegnate', value: 'DELIVERED' },
        { label: 'Saltate', value: 'SKIPPED' }
    ];
    protected readonly originOptions: { label: string; value: NotificationDeliveryOrigin | null }[] = [
        { label: 'Tutte le origini', value: null },
        { label: 'Fan-out in-app', value: 'OUTBOX' },
        { label: 'Consegna push', value: 'PUSH' },
        { label: 'Promemoria evento', value: 'REMINDER' }
    ];
    protected readonly sourceOptions = [
        { label: 'Tutte le categorie', value: null },
        { label: 'Calendario', value: 'CALENDAR' },
        { label: 'Inventario', value: 'INVENTORY' },
        { label: 'Economia', value: 'FINANCE' },
        { label: 'Contenuti', value: 'CONTENT' },
        { label: 'Utenti e accessi', value: 'IDENTITY' },
        { label: 'Organizzazione', value: 'TENANT' },
        { label: 'Generali', value: 'GENERAL' }
    ];
    protected readonly closeReasons = [
        { label: 'Non più pertinente', value: 'NO_LONGER_RELEVANT' },
        { label: 'Dispositivo non raggiungibile', value: 'DEVICE_UNREACHABLE' },
        { label: 'Chiusura manuale', value: 'MANUAL_CLOSE' }
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
    protected filters: NotificationDeliveryFilters = { origin: null, source: null, operation: null, from: null, to: null };
    protected fromDate?: Date;
    protected toDate?: Date;
    protected sort = 'occurredAt,asc';
    protected pendingRowKey?: string;
    protected bulkRetrying = false;
    protected rangeInvalid = false;
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
        if (status && ['PENDING', 'DELIVERED', 'FAILED', 'SKIPPED'].includes(status)) this.status = status as NotificationDeliveryStatus;
        const origin = this.route.snapshot.queryParamMap.get('origin');
        if (origin && ['OUTBOX', 'PUSH', 'REMINDER'].includes(origin)) this.filters.origin = origin as NotificationDeliveryOrigin;
    }

    protected originLabel(origin: NotificationDeliveryOrigin): string {
        return this.originOptions.find((option) => option.value === origin)?.label ?? origin;
    }

    /** Il fan-out in-app non ha una chiusura tecnica: solo le code push possono essere chiuse. */
    protected canClose(delivery: NotificationDeliveryAdmin): boolean {
        return delivery.origin !== 'OUTBOX' && (delivery.status === 'FAILED' || delivery.status === 'PENDING');
    }

    protected load(event: TableLazyLoadEvent = this.lazyEvent): void {
        this.lazyEvent = event;
        const rows = event.rows ?? 12;
        const sortField = Array.isArray(event.sortField) ? event.sortField[0] : event.sortField;
        this.sort = `${sortField ?? 'occurredAt'},${(event.sortOrder ?? 1) < 0 ? 'desc' : 'asc'}`;
        this.loading = true;
        this.loadError = false;
        this.service
            .getDeliveries(this.status, Math.floor((event.first ?? 0) / rows), rows, this.sort, this.filters)
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe({
                next: (page) => {
                    this.deliveries = page.content;
                    this.totalRecords = page.totalElements;
                    this.selected = this.selected.filter((selected) => page.content.some((entry) => entry.rowKey === selected.rowKey));
                },
                error: () => (this.loadError = true)
            });
    }

    protected onStatusChange(status: NotificationDeliveryStatus): void {
        this.status = status;
        this.resetAndLoad();
    }

    protected onFilterChange(): void {
        // Il backend riceve istanti ISO completi di offset; il picker lavora su Date locali.
        this.filters.from = this.fromDate ? this.fromDate.toISOString() : null;
        this.filters.to = this.toDate ? this.toDate.toISOString() : null;
        this.rangeInvalid = !!this.fromDate && !!this.toDate && this.fromDate >= this.toDate;
        if (this.rangeInvalid) return;
        this.resetAndLoad();
    }


    private resetAndLoad(): void {
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
        return this.deliveries.length > 0 && this.deliveries.every((delivery) => this.selected.some((selected) => selected.rowKey === delivery.rowKey));
    }

    protected toggleAllVisible(select: boolean): void {
        this.selected = select ? [...this.deliveries] : [];
    }

    protected emptyTitle(): string {
        if (this.status === 'PENDING') return 'Nessuna consegna in attesa';
        if (this.status === 'DELIVERED') return 'Nessuna consegna completata';
        if (this.status === 'SKIPPED') return 'Nessuna consegna saltata';
        return 'Nessuna consegna fallita';
    }

    protected emptyMessage(): string {
        if (this.status === 'PENDING') return 'Non ci sono eventi tecnici in attesa di elaborazione.';
        if (this.status === 'DELIVERED') return 'Non ci sono eventi tecnici consegnati da mostrare.';
        if (this.status === 'SKIPPED') return 'Non ci sono consegne chiuse senza invio: le soppressioni per preferenza non sono guasti.';
        return 'Non ci sono eventi tecnici che richiedono un retry.';
    }

    protected retry(delivery: NotificationDeliveryAdmin): void {
        this.confirmService.confirmReversible({
            title: 'Riprova consegna',
            consequence: `La consegna tecnica ${delivery.rowKey} verrà riportata in coda mantenendo la stessa chiave evento. Utente, preferenze e sottoscrizioni vengono rivalidati prima dell'invio.`,
            actionLabel: 'Riprova',
            accept: () => {
                this.pendingRowKey = delivery.rowKey;
                this.service
                    .retry(delivery.origin, delivery.id)
                    .pipe(
                        first(),
                        finalize(() => (this.pendingRowKey = undefined))
                    )
                    .subscribe(() => {
                        this.toastService.success('Retry richiesto', 'La consegna è stata riportata in coda.');
                        this.load();
                    });
            }
        });
    }

    protected close(delivery: NotificationDeliveryAdmin, reason: string): void {
        this.confirmService.confirmReversible({
            title: 'Chiudi consegna',
            consequence: `La consegna tecnica ${delivery.rowKey} verrà chiusa senza invio con motivo ${reason}.`,
            actionLabel: 'Chiudi',
            accept: () => {
                this.pendingRowKey = delivery.rowKey;
                this.service
                    .close(delivery.origin, delivery.id, reason)
                    .pipe(
                        first(),
                        finalize(() => (this.pendingRowKey = undefined))
                    )
                    .subscribe(() => {
                        this.toastService.success('Consegna chiusa', 'La consegna non verrà più tentata.');
                        this.load();
                    });
            }
        });
    }

    protected retrySelected(): void {
        const refs = this.selected.map((delivery) => ({ origin: delivery.origin, id: delivery.id }));
        if (refs.length === 0 || refs.length > 100) return;
        this.confirmService.confirmReversible({
            title: 'Riprova consegne selezionate',
            consequence: `${refs.length} consegne verranno riportate in coda mantenendo le rispettive chiavi evento.`,
            actionLabel: 'Riprova selezionate',
            accept: () => {
                this.bulkRetrying = true;
                this.service
                    .retrySelected(refs)
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
