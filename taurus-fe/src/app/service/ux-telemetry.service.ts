import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { environment } from '../../environments/environment';

/**
 * Eventi di uso misurati nell'interfaccia. L'elenco è chiuso: il nome arriva al backend come
 * etichetta di una metrica, quindi non può dipendere da dati dell'utente.
 */
export type UxTelemetryEvent = 'open' | 'search' | 'reorder' | 'move' | 'merge' | 'split' | 'undo' | 'save' | 'save-error' | 'conflict' | 'reload';

/** Quel che viene spedito: conteggi per evento e durata della sessione di lavoro, niente contenuti. */
export interface UxTelemetrySnapshot {
    feature: string;
    durationMs: number;
    events: Record<string, number>;
}

/**
 * Telemetria d'uso aggregata in locale e spedita una volta sola per sessione di lavoro.
 *
 * Non esce nulla che riguardi la musica: né nomi di parti o di file, né testi cercati, né
 * identificativi di tracce. Restano i nomi degli eventi di {@link UxTelemetryEvent}, i loro
 * conteggi e il tempo trascorso dalla prima apertura, che è la misura chiesta dalla specifica del
 * workspace. Il backend li traduce in contatori Micrometer, così la misura resta a costo zero e
 * dentro l'installazione.
 */
@Injectable({ providedIn: 'root' })
export class UxTelemetryService {
    private feature?: string;
    private startedAt = 0;
    private readonly counters = new Map<UxTelemetryEvent, number>();

    constructor(private readonly http: HttpClient) {}

    /** Apre la sessione di lavoro di una funzione; una seconda apertura non azzera il cronometro. */
    start(feature: string): void {
        if (this.feature === feature) return;
        this.flush();
        this.feature = feature;
        this.startedAt = Date.now();
        this.counters.clear();
        this.record('open');
    }

    record(event: UxTelemetryEvent): void {
        if (!this.feature) return;
        this.counters.set(event, (this.counters.get(event) ?? 0) + 1);
    }

    /** Gli eventi non ancora spediti: serve ai test e a evitare spedizioni vuote. */
    get pending(): number {
        return this.counters.size;
    }

    snapshot(): UxTelemetrySnapshot | undefined {
        if (!this.feature || !this.counters.size) return undefined;
        return {
            feature: this.feature,
            durationMs: Math.max(0, Date.now() - this.startedAt),
            events: Object.fromEntries(this.counters)
        };
    }

    /**
     * Spedisce l'aggregato e riparte da zero. L'esito non interessa: una misura persa non deve
     * disturbare chi sta lavorando, quindi l'errore viene ignorato e la sessione resta aperta.
     */
    flush(): void {
        const snapshot = this.snapshot();
        this.counters.clear();
        this.startedAt = Date.now();
        if (!snapshot) return;
        this.http.post(`${environment.baseUrl}/ux-telemetry`, snapshot).subscribe({ error: () => undefined });
    }
}
