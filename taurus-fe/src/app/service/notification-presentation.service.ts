import { Injectable } from '@angular/core';

const SOURCE_ICONS: Readonly<Record<string, string>> = {
    GENERAL: 'pi pi-bell',
    CONTENT: 'pi pi-file',
    CALENDAR: 'pi pi-calendar',
    IDENTITY: 'pi pi-users',
    TENANT: 'pi pi-building',
    INVENTORY: 'pi pi-box',
    FINANCE: 'pi pi-wallet'
};

const SOURCE_LABELS: Readonly<Record<string, string>> = {
    GENERAL: 'Generale',
    CONTENT: 'Contenuti',
    CALENDAR: 'Calendario',
    IDENTITY: 'Utenti',
    TENANT: 'Organizzazione',
    INVENTORY: 'Inventario',
    FINANCE: 'Economia'
};

const STATUS_LABELS: Readonly<Record<string, string>> = {
    PENDING: 'In attesa',
    DELIVERED: 'Consegnata',
    FAILED: 'Fallita'
};

const OPERATION_LABELS: Readonly<Record<string, string>> = {
    ALBUM_AGGIORNATO: 'Album aggiornato',
    ALBUM_CREATO: 'Album creato',
    ALBUM_PUBBLICATO: 'Album pubblicato',
    ALBUM_RIMOSSO: 'Album rimosso',
    ACCOUNT_ARCHIVED: 'Conto archiviato',
    ACCOUNT_CREATED: 'Conto creato',
    ACCOUNT_REACTIVATED: 'Conto riattivato',
    ACCOUNT_UPDATED: 'Conto aggiornato',
    ASSIGNMENT_REVISION_CREATED: 'Revisione assegnazione creata',
    ATTACHMENT_ADDED: 'Allegato aggiunto',
    ATTACHMENT_DOWNLOADED: 'Allegato scaricato',
    ATTACHMENT_REMOVED: 'Allegato rimosso',
    CATEGORY_ARCHIVED: 'Categoria archiviata',
    CATEGORY_CREATED: 'Categoria creata',
    CATEGORY_REACTIVATED: 'Categoria riattivata',
    CATEGORY_UPDATED: 'Categoria aggiornata',
    EVENTO_AGGIORNATO: 'Evento aggiornato',
    EVENTO_CREATO: 'Evento creato',
    EVENTO_DISPONIBILITA_ANNULLATA: 'Disponibilità annullata',
    EVENTO_DISPONIBILITA_CONFERMATA: 'Disponibilità confermata',
    EVENTO_DISPONIBILITA_RIFIUTATA: 'Disponibilità rifiutata',
    EVENTO_PRESENZE_AGGIORNATE: 'Presenze aggiornate',
    EVENTO_PUBBLICATO: 'Evento pubblicato',
    EVENTO_RIMOSSO: 'Evento rimosso',
    EVENT_BUDGET_UPDATED: 'Preventivo evento aggiornato',
    INVENTARIO_ASSEGNAZIONE_AGGIORNATA: 'Assegnazione aggiornata',
    INVENTARIO_ASSEGNAZIONE_IN_SCADENZA: 'Assegnazione in scadenza',
    INVENTARIO_ASSEGNAZIONE_RIMOSSA: 'Assegnazione rimossa',
    INVENTARIO_ASSEGNAZIONE_SCADUTA: 'Assegnazione scaduta',
    INVENTARIO_FOTOGRAFIA_AGGIUNTA: 'Fotografia aggiunta',
    INVENTARIO_FOTOGRAFIA_RIMOSSA: 'Fotografia rimossa',
    INVENTARIO_FOTOGRAFIE_AGGIORNATE: 'Fotografie aggiornate',
    INVENTARIO_OGGETTO_AGGIORNATO: 'Oggetto aggiornato',
    INVENTARIO_OGGETTO_ASSEGNATO: 'Oggetto assegnato',
    INVENTARIO_OGGETTO_CREATO: 'Oggetto creato',
    INVENTARIO_OGGETTO_RIMOSSO: 'Oggetto rimosso',
    INVENTARIO_PRESA_VISIONE_ACCETTATA: 'Presa visione accettata',
    INVENTARIO_PRESA_VISIONE_RICHIESTA: 'Presa visione richiesta',
    INVENTARIO_PRESA_VISIONE_RIEMESSA: 'Presa visione riemessa',
    INVENTARIO_PRESA_VISIONE_RIFIUTATA: 'Presa visione rifiutata',
    INVENTARIO_RICONSEGNA_COMPLETATA: 'Riconsegna completata',
    INVENTARIO_RICONSEGNA_RICHIESTA: 'Riconsegna richiesta',
    MOVEMENT_CREATED: 'Movimento registrato',
    MOVEMENT_RECONCILED: 'Movimento riconciliato',
    MOVEMENT_REMOVED: 'Movimento rimosso',
    MOVEMENT_UNRECONCILED: 'Riconciliazione annullata',
    MOVEMENT_UPDATED: 'Movimento aggiornato',
    REPORT_EXPORTED: 'Rendiconto esportato',
    STRUMENTO_AGGIORNATO: 'Strumento aggiornato',
    STRUMENTO_CREATO: 'Strumento creato',
    STRUMENTO_RIMOSSO: 'Strumento rimosso',
    TENANT_AGGIORNATO: 'Organizzazione aggiornata',
    TENANT_CREATO: 'Organizzazione creata',
    TENANT_RIMOSSO: 'Organizzazione rimossa',
    TRACCIA_AGGIORNATA: 'Traccia aggiornata',
    TRACCIA_CREATA: 'Traccia creata',
    TRACCIA_PUBBLICATA: 'Traccia pubblicata',
    TRACCIA_RIMOSSA: 'Traccia rimossa',
    TRANSFER_CREATED: 'Trasferimento registrato',
    TRANSFER_REMOVED: 'Trasferimento rimosso',
    TRANSFER_UPDATED: 'Trasferimento aggiornato',
    UTENTE_AGGIORNATO: 'Utente aggiornato',
    UTENTE_CREATO: 'Utente creato',
    UTENTE_RIMOSSO: 'Utente rimosso',
    YEAR_RECALCULATED: 'Esercizio ricalcolato',
    YEAR_ROLLED_OVER: 'Riporto annuale completato'
};

@Injectable({ providedIn: 'root' })
export class NotificationPresentationService {
    icon(source?: string): string {
        return SOURCE_ICONS[source ?? 'GENERAL'] ?? SOURCE_ICONS['GENERAL'];
    }

    sourceLabel(source?: string): string {
        return this.label(SOURCE_LABELS, source, 'Generale');
    }

    statusLabel(status?: string): string {
        return this.label(STATUS_LABELS, status, 'Stato sconosciuto');
    }

    operationLabel(operation?: string): string {
        return this.label(OPERATION_LABELS, operation, operation ? 'Operazione non catalogata' : 'Operazione non specificata');
    }

    private label(labels: Readonly<Record<string, string>>, value: string | undefined, fallback: string): string {
        if (!value) return fallback;
        return labels[value] ?? fallback;
    }
}
