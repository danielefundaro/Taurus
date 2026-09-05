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
    protected secret?: CalendarFeedSecret;
    protected draft: CalendarFeedCreate = this.emptyDraft();
    protected readonly details = [
        { label: 'Minimo: nome, orari e luogo', value: 'MINIMAL' as CalendarFeedDetailLevel },
        { label: 'Standard: include descrizione e link Taurus', value: 'STANDARD' as CalendarFeedDetailLevel }
    ];
    protected readonly scopes = [
        { label: 'Interno: eventi completi e pubblici', value: 'INTERNAL' as CalendarFeedScope },
        { label: 'Solo pubblico', value: 'PUBLIC_ONLY' as CalendarFeedScope }
    ];
    constructor(private readonly service: CalendarFeedService, private readonly confirm: ConfirmService, private readonly toast: ToastService) {}
    ngOnInit(): void { this.load(); }
    protected create(): void {
        if (!this.draft.name.trim() || this.saving) return;
        this.saving = true;
        this.service.create(this.draft, this.admin).pipe(first(), finalize(() => (this.saving = false))).subscribe({
            next: (secret) => { this.secret = secret; this.draft = this.emptyDraft(); this.toast.success('Feed creato', 'Copia ora il link: non sarà più mostrato.'); this.load(); },
            error: () => this.toast.error('Creazione non riuscita', 'Controlla i dati e il numero di feed attivi.')
        });
    }
    protected rotate(feed: CalendarFeed): void {
        this.confirm.confirmReversible({ title: 'Ruota link calendario', consequence: 'Il link precedente smetterà subito di funzionare e dovrai aggiornare ogni sottoscrizione.', actionLabel: 'Ruota link', accept: () =>
            this.service.rotate(feed.id, this.admin).pipe(first()).subscribe({ next: (secret) => { this.secret = secret; this.toast.success('Link ruotato', 'Copia ora il nuovo link.'); this.load(); }, error: () => this.toast.error('Rotazione non riuscita', 'Il feed potrebbe essere già stato revocato.') })
        });
    }
    protected revoke(feed: CalendarFeed): void {
        this.confirm.confirmDestructive({ title: 'Revoca feed calendario', consequence: 'Il link non potrà più scaricare aggiornamenti. Le copie già memorizzate dai provider non vengono eliminate.', actionLabel: 'Revoca', accept: () =>
            this.service.revoke(feed.id, this.admin).pipe(first()).subscribe({ next: () => { this.secret = undefined; this.toast.success('Feed revocato', 'Il link non è più valido.'); this.load(); }, error: () => this.toast.error('Revoca non riuscita', 'Riprova tra poco.') })
        });
    }
    protected copySecret(): void { if (this.secret) navigator.clipboard.writeText(this.secret.subscriptionUrl).then(() => this.toast.success('Link copiato', 'Conservalo come una password.')); }
    protected openWebcal(): void { if (this.secret) window.location.href = this.secret.subscriptionUrl.replace(/^https?:\/\//, 'webcal://'); }
    protected canRotate(feed: CalendarFeed): boolean { return feed.status === 'ACTIVE' && (!this.admin || feed.feedType === 'TENANT'); }
    private load(): void { this.loading = true; this.service.list(this.admin).pipe(first(), finalize(() => (this.loading = false))).subscribe({ next: (feeds) => (this.feeds = feeds), error: () => this.toast.error('Feed non disponibili', 'Non è stato possibile caricare i feed calendario.') }); }
    private emptyDraft(): CalendarFeedCreate { return { name: '', visibilityScope: 'INTERNAL', detailLevel: 'MINIMAL', pastDays: 90, futureMonths: 18, idempotencyKey: crypto.randomUUID() }; }
}
