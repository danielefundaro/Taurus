import { ApplicationRef, Injectable, isDevMode } from '@angular/core';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { MessageService } from 'primeng/api';
import { concat, filter, first, interval } from 'rxjs';

/**
 * Porta la scheda aperta sulla versione che il service worker ha appena scaricato.
 *
 * <p>Senza questo passaggio il worker resta padrone dell'origine e continua a servire dalla
 * cache la versione con cui il client e' partito: la nuova viene scaricata ma non diventa mai
 * attiva, e l'utente resta su un bundle vecchio finche' non apre una scheda nuova.
 */
@Injectable({ providedIn: 'root' })
export class AppUpdateService {
    /** Chiave del toast dedicato: il toast senza chiave e' quello generico dell'app. */
    static readonly TOAST_KEY = 'app-update';

    private static readonly CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000;

    constructor(
        private readonly swUpdate: SwUpdate,
        private readonly appRef: ApplicationRef,
        private readonly messageService: MessageService
    ) { }

    init(): void {
        if (!this.swUpdate.isEnabled) return;

        this.swUpdate.versionUpdates
            .pipe(filter((event): event is VersionReadyEvent => event.type === 'VERSION_READY'))
            .subscribe(() => this.onVersionReady());

        // Il worker non trova piu' i file della versione che sta servendo: la pagina e' gia'
        // rotta, l'unica uscita e' ripartire dalla rete senza chiedere nulla.
        this.swUpdate.unrecoverable.subscribe(() => this.reload());

        this.schedulePeriodicChecks();
    }

    /** Attiva la versione scaricata e ricarica. Il reload avviene anche se l'attivazione fallisce. */
    activate(): void {
        this.messageService.clear(AppUpdateService.TOAST_KEY);
        this.swUpdate.activateUpdate().then(
            () => this.reload(),
            () => this.reload()
        );
    }

    private onVersionReady(): void {
        // In sviluppo ogni ricompilazione e' una versione nuova: chiedere conferma ogni volta
        // sarebbe solo rumore, quindi ci si comporta come il live reload.
        if (isDevMode()) {
            this.activate();
            return;
        }

        // In produzione no: una ricarica non annunciata butterebbe via un form a meta'.
        this.messageService.add({
            key: AppUpdateService.TOAST_KEY,
            severity: 'info',
            summary: 'Nuova versione disponibile',
            detail: 'Ricarica la pagina per applicare gli ultimi aggiornamenti.',
            sticky: true,
            closable: true
        });
    }

    /**
     * Il primo controllo aspetta che l'app sia stabile: un interval avviato prima terrebbe
     * sveglio il change detection e {@link ApplicationRef.isStable} non emetterebbe mai true.
     */
    private schedulePeriodicChecks(): void {
        const whenStable = this.appRef.isStable.pipe(first((stable) => stable));
        const everyFewHours = interval(AppUpdateService.CHECK_INTERVAL_MS);

        concat(whenStable, everyFewHours).subscribe(() => {
            this.swUpdate.checkForUpdate().catch(() => undefined);
        });
    }

    private reload(): void {
        document.location.reload();
    }
}
