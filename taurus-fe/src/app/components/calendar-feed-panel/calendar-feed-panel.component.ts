import { Component, Input, OnInit } from '@angular/core';
import { finalize, first } from 'rxjs';
import { ImportsModule } from '../../imports';
import { CalendarFeed, CalendarFeedCreate, CalendarFeedDetailLevel, CalendarFeedScope, CalendarFeedSecret } from '../../module';
import { CalendarFeedService, ConfirmService, ToastService } from '../../service';

@Component({
    selector: 'app-calendar-feed-panel',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './calendar-feed-panel.component.html',
    styleUrl: './calendar-feed-panel.component.scss'
})
export class CalendarFeedPanelComponent implements OnInit {
    @Input() admin = false;
    protected feeds: CalendarFeed[] = [];
    protected loading = true;
    protected saving = false;
    protected nameTouched = false;
    protected secret?: CalendarFeedSecret;
    protected draft: CalendarFeedCreate = this.emptyDraft();
    private readonly rotationKeys = new Map<string, string>();
    protected readonly details = [
        { label: 'Minimo: nome, orari e luogo', value: 'MINIMAL' as CalendarFeedDetailLevel },
        { label: 'Standard: include descrizione e link Taurus', value: 'STANDARD' as CalendarFeedDetailLevel }
    ];
    protected readonly scopes = [
        { label: 'Interno: eventi completi e pubblici', value: 'INTERNAL' as CalendarFeedScope },
        { label: 'Solo pubblico', value: 'PUBLIC_ONLY' as CalendarFeedScope }
    ];
    constructor(
        private readonly service: CalendarFeedService,
        private readonly confirm: ConfirmService,
        private readonly toast: ToastService
    ) {}
    ngOnInit(): void {
        this.load();
    }
    protected create(): void {
        this.nameTouched = true;
        if (!this.draft.name.trim() || this.saving) return;
        this.saving = true;
        this.service
            .create(this.draft, this.admin)
            .pipe(
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe({
                next: (secret) => {
                    this.secret = secret;
                    this.draft = this.emptyDraft();
                    this.nameTouched = false;
                    this.toast.success('Feed creato', 'Copia subito il link: per sicurezza non potrai recuperarlo in seguito.');
                    this.load();
                },
                error: () => this.toast.error('Creazione non riuscita', 'Controlla i dati e il numero di feed attivi.')
            });
    }
    protected rotate(feed: CalendarFeed): void {
        this.confirm.confirmReversible({
            title: 'Genera un nuovo link',
            consequence: 'Il link attuale smetterà subito di funzionare. Dovrai sostituirlo in tutti i calendari che usano questo feed.',
            actionLabel: 'Genera nuovo link',
            accept: () => {
                const idempotencyKey = this.rotationKeys.get(feed.id) ?? crypto.randomUUID();
                this.rotationKeys.set(feed.id, idempotencyKey);
                this.service
                    .rotate(feed.id, this.admin, idempotencyKey)
                    .pipe(first())
                    .subscribe({
                        next: (secret) => {
                            this.rotationKeys.delete(feed.id);
                            this.secret = secret;
                            this.toast.success('Nuovo link generato', 'Copialo subito: per sicurezza non potrai recuperarlo in seguito.');
                            this.load();
                        },
                        error: () => this.toast.error('Rotazione non riuscita', 'Il feed potrebbe essere già stato revocato.')
                    });
            }
        });
    }
    protected revoke(feed: CalendarFeed): void {
        this.confirm.confirmDestructive({
            title: 'Revoca feed calendario',
            consequence: 'Il link non potrà più scaricare aggiornamenti. Le copie già memorizzate dai provider non vengono eliminate.',
            actionLabel: 'Revoca',
            accept: () =>
                this.service
                    .revoke(feed.id, this.admin)
                    .pipe(first())
                    .subscribe({
                        next: () => {
                            this.secret = undefined;
                            this.toast.success('Feed revocato', 'Il link non è più valido.');
                            this.load();
                        },
                        error: () => this.toast.error('Revoca non riuscita', 'Riprova tra poco.')
                    })
        });
    }
    protected remove(feed: CalendarFeed): void {
        this.confirm.confirmDestructive({
            title: 'Elimina feed revocato',
            consequence: "Il feed scomparirà dall'elenco. I dati essenziali resteranno conservati per finalità di sicurezza e audit.",
            actionLabel: "Elimina dall'elenco",
            accept: () =>
                this.service
                    .remove(feed.id, this.admin)
                    .pipe(first())
                    .subscribe({
                        next: () => {
                            this.toast.success('Feed eliminato', "Il feed revocato non è più visibile nell'elenco.");
                            this.load();
                        },
                        error: () => this.toast.error('Eliminazione non riuscita', 'Il feed deve essere revocato prima di poterlo eliminare.')
                    })
        });
    }
    protected copySecret(): void {
        if (this.secret) navigator.clipboard.writeText(this.secret.subscriptionUrl).then(() => this.toast.success('Link copiato', 'Conservalo come una password.'));
    }
    protected openWebcal(): void {
        if (this.secret) window.location.href = this.secret.subscriptionUrl.replace(/^https?:\/\//, 'webcal://');
    }
    protected canRotate(feed: CalendarFeed): boolean {
        return feed.status === 'ACTIVE' && (!this.admin || feed.feedType === 'TENANT');
    }
    private load(): void {
        this.loading = true;
        this.service
            .list(this.admin)
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe({ next: (feeds) => (this.feeds = feeds), error: () => this.toast.error('Feed non disponibili', 'Non è stato possibile caricare i feed calendario.') });
    }
    private emptyDraft(): CalendarFeedCreate {
        return { name: '', visibilityScope: 'INTERNAL', detailLevel: 'MINIMAL', pastDays: 90, futureMonths: 18, idempotencyKey: crypto.randomUUID() };
    }
}
