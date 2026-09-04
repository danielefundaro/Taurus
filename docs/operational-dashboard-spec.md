# Dashboard operativa trasversale

## Stato del documento

ID catalogo: `operational-dashboard`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

## Stato dell'implementazione e rilascio

La prima versione è implementata nei moduli backend e frontend. Il contratto API, le soglie e il comportamento parziale descritti in questo documento sono quelli effettivamente esposti dall'applicazione. La console amministrativa delle consegne tecniche è disponibile in `/admin/notification-delivery` e non espone contenuti, payload o destinatari.

Il rollout iniziale mantiene `application.dashboard.enabled=false`: l'attivazione deve essere eseguita tramite configurazione esterna prima in staging e poi in produzione. Non sono richieste migrazioni del database; eventuali indici devono derivare dalla misurazione dei query plan PostgreSQL su dati realistici.

## Obiettivo

La dashboard deve rispondere alla domanda «che cosa richiede la mia attenzione adesso?» usando informazioni gia presenti in calendario, inventario, economia, notifiche e conformita legale.

La nuova sezione **Da fare** deve:

- mostrare soltanto attivita che l'utente autenticato puo realmente consultare o risolvere;
- aggregare le attivita omogenee per non trasformare la home in una seconda pagina elenco;
- ordinare prima le situazioni bloccanti o scadute, poi quelle prossime alla scadenza;
- condurre con un solo collegamento alla pagina gia filtrata in cui si completa il lavoro;
- aggiornarsi automaticamente dopo un'azione riuscita o tramite ricaricamento esplicito;
- degradare in modo comprensibile se un singolo dominio non e temporaneamente disponibile;
- rispettare isolamento tenant, minimizzazione dei dati e autorizzazioni backend.

## Decisione fondamentale: nessuna seconda lista di task

Le attivita non vengono salvate in una nuova tabella e non hanno un comando generico «completata».

Ogni voce e una proiezione dello stato autorevole del relativo dominio. Per esempio:

- una disponibilita scompare quando viene registrata la risposta all'evento;
- una presa visione scompare quando l'utente accetta o rifiuta la revisione;
- una riconsegna scompare quando l'amministratore la chiude;
- un movimento scompare dal conteggio quando viene riconciliato;
- un errore di consegna scompare quando l'evento tecnico viene riprocessato con successo.

Questa scelta evita divergenze, duplicazione di audit e attivita dichiarate concluse mentre il dato di dominio e ancora pendente.

## Situazione attuale

La dashboard corrente carica in modo indipendente conteggi di tenant, utenti, album e tracce, gli ultimi inserimenti, i prossimi eventi, le notifiche e i riepiloghi inventario. Il ruolo effettivo selezionato dal frontend decide quali chiamate eseguire.

La base visuale e gia valida: `DetailSectionComponent`, `ListRowComponent`, `EmptyStateComponent`, `InlineAlertComponent`, `p-skeleton`, `p-tag` e `p-button` costituiscono il vocabolario da riutilizzare. Non serve introdurre una seconda libreria di card.

Le criticita da risolvere sono:

- l'utente deve interpretare piu widget per capire se esiste qualcosa di urgente;
- il frontend orchestra molte richieste e non ottiene uno snapshot operativo unico;
- un utente con piu autorita riceve oggi una vista determinata da un solo ruolo effettivo;
- non esiste un ordinamento comune tra scadenze, attivita amministrative e anomalie tecniche;
- alcuni collegamenti aprono il modulo corretto, ma non applicano gia il filtro che ha originato il conteggio.

## Ambito della prima versione

La prima versione comprende:

1. disponibilita personali mancanti per eventi futuri visibili;
2. eventi futuri con risposte mancanti, per chi amministra il calendario;
3. prese visione inventario pendenti, personali e amministrative;
4. richieste di riconsegna da verificare;
5. assegnazioni inventario scadute o prossime alla scadenza;
6. movimenti economici ordinari non riconciliati;
7. notifiche tecniche in stato `FAILED`;
8. stato parziale della dashboard quando un dominio non e disponibile;
9. collegamenti profondi verso viste gia filtrate.

L'accettazione dei documenti legali rimane governata da `legalDocumentsGuard`: un utente non conforme viene portato alla pagina di accettazione prima di poter aprire la dashboard. Il backend puo comunque restituire `LEGAL_ACCEPTANCE_REQUIRED` come protezione difensiva e per client futuri, ma nella web app corrente la voce non appare nel normale percorso.

## Fuori ambito della prima versione

- creazione manuale di promemoria o task;
- assegnazione di un'attivita a un altro utente;
- commenti, checklist o scadenze indipendenti dai dati di dominio;
- personalizzazione o riordinamento manuale dei widget;
- riepiloghi periodici via e-mail;
- previsioni basate su machine learning;
- aggregazione cross-tenant nella home del Super Admin;
- chiusura massiva delle attivita direttamente dalla dashboard;
- aggiornamento real-time via WebSocket o Server-Sent Events.

## Principi di esperienza utente

### Un punto di ingresso, non una nuova area gestionale

La sezione mostra un massimo di una riga aggregata per tipo di attivita. Il lavoro viene completato nella pagina del dominio, dove sono disponibili contesto, conferme e controlli specifici.

### Priorita spiegabile

Sono ammesse tre severita:

| Severita | Significato | PrimeNG |
| --- | --- | --- |
| `DANGER` | situazione scaduta, bloccante o tecnicamente fallita | `danger` |
| `WARNING` | azione richiesta a breve o arretrato gestionale rilevante | `warn` |
| `INFO` | azione richiesta senza urgenza immediata | `info` |

Il colore non e mai l'unico segnale: ogni riga mostra anche un'etichetta testuale come «Scaduto», «Da gestire» o «Da verificare».

### Nessuna falsa precisione

Il conteggio principale descrive oggetti sui quali e possibile agire: eventi, assegnazioni, riconsegne, movimenti o consegne tecniche. Eventuali conteggi secondari, per esempio il numero complessivo di persone che non hanno risposto, vengono presentati nella descrizione e non sommati al totale della dashboard.

### Stato vuoto positivo

Quando non esistono attivita viene mostrato:

- titolo: **Tutto sotto controllo**;
- descrizione: **Non ci sono attivita che richiedono la tua attenzione.**;
- icona: `pi-check-circle`;
- nessuna call to action artificiale.

### Aggiornamento

I dati vengono caricati:

- all'ingresso nella dashboard;
- quando la finestra torna visibile dopo almeno cinque minuti;
- dopo il ritorno da una pagina aperta tramite una voce operativa;
- quando l'utente usa il comando **Aggiorna**.

Non e previsto polling a intervallo fisso nella prima versione. Durante il ricaricamento i dati gia presenti restano visibili e il comando mostra lo stato occupato; gli skeleton sono usati soltanto per il primo caricamento.

## Composizione della pagina

L'ordine raccomandato e:

1. intestazione `Dashboard`, sottotitolo contestuale e istante dell'ultimo aggiornamento;
2. sezione **Da fare**, a tutta larghezza;
3. indicatori sintetici autorizzati per il ruolo;
4. inventario e prossimi eventi;
5. notifiche personali;
6. contenuti recenti del catalogo.

La sezione **Da fare** non e nascosta quando e vuota: lo stato positivo rende esplicito che il caricamento e riuscito.

Su desktop ogni attivita e una riga. Su mobile identita, descrizione e azione si dispongono verticalmente; il collegamento mantiene un'area di attivazione di almeno 44 pixel.

## Catalogo delle attivita

### Calendario personale: `CALENDAR_AVAILABILITY_REQUIRED`

Destinatari:

- Super Admin e Admin per la propria disponibilita;
- Archivista e Utente;
- Utente Esterno per gli eventi `PUBLIC`;
- Tesoriere soltanto se possiede anche una delle autorita partecipanti precedenti.

Regola:

- evento non cancellato e non escluso da una serie;
- evento non ancora iniziato;
- data di inizio entro `calendar-look-ahead-days`, valore predefinito 14;
- stato visibile al ruolo: `COMPLETE` o `PUBLIC` per `ROLE_USER`, solo `PUBLIC` per `ROLE_USER_EXTERNAL`;
- nessuna riga `calendar_event_availability` per l'utente autenticato.

Conteggio principale: numero di eventi a cui l'utente deve rispondere.

Severita:

- `DANGER` se il primo evento inizia entro 24 ore;
- `WARNING` negli altri casi.

Collegamento: `/calendar?attention=my-missing-availability`.

### Calendario amministrativo: `CALENDAR_RESPONSES_MISSING`

Destinatari: Super Admin e Admin del tenant attivo.

La prima versione non introduce inviti espliciti per evento. Il pubblico atteso viene quindi derivato in modo deterministico:

- evento `COMPLETE`: utenti attivi con `ROLE_USER`;
- evento `PUBLIC`: utenti attivi con `ROLE_USER` o `ROLE_USER_EXTERNAL`;
- un amministratore o archivista e incluso soltanto se possiede anche uno dei ruoli partecipanti;
- eventi `DRAFT` e `TRASHED` non generano attivita.

Un evento e contato quando almeno un componente del pubblico atteso non ha una risposta. Il conteggio principale e il numero di eventi incompleti; la descrizione contiene il numero complessivo delle risposte mancanti e il nome dell'evento piu vicino.

Severita:

- `DANGER` se almeno un evento incompleto inizia entro 24 ore;
- `WARNING` negli altri casi.

Collegamento: `/calendar?attention=missing-availability`.

Questa euristica deve essere sostituita da un pubblico invitato esplicito se in futuro gli eventi vengono indirizzati a sottoinsiemi di utenti. Fino ad allora non si devono inventare filtri per strumento o sezione musicale.

### Inventario personale: `INVENTORY_DECISION_REQUIRED`

Destinatari: ogni utente autenticato che ha un'assegnazione nel tenant.

Regola: revisione corrente di un'assegnazione attiva senza decisione dell'assegnatario.

Conteggio principale: assegnazioni da accettare o rifiutare.

Severita: `WARNING`.

Collegamento: `/inventory?view=mine&attention=pending-decisions`.

### Inventario amministrativo: `INVENTORY_DECISIONS_PENDING`

Destinatari: Super Admin e Admin del tenant attivo.

Regola: stessa proiezione gia usata da `InventoryAdminSummaryDTO.pendingDecisions`.

Conteggio principale: assegnazioni senza presa visione.

Severita: `WARNING`.

Collegamento: `/inventory?attention=pending-decisions`.

### Riconsegne: `INVENTORY_RETURNS_PENDING`

Destinatari: Super Admin e Admin del tenant attivo.

Regola: riconsegne non cancellate nello stato operativo che richiede verifica o chiusura amministrativa.

Conteggio principale: richieste di riconsegna aperte.

Severita: `WARNING`.

Collegamento: `/inventory?attention=pending-returns`.

### Scadenze inventario: `INVENTORY_ASSIGNMENTS_EXPIRING`

Destinatari:

- Super Admin e Admin per tutte le assegnazioni del tenant;
- ogni altro utente soltanto per le proprie assegnazioni.

Regola:

- assegnazione non cancellata e con quantita residua maggiore di zero;
- stato che rappresenta materiale ancora affidato;
- `expiration_date` valorizzata e non successiva a oggi piu `inventory-expiration-look-ahead-days`, valore predefinito 30.

Conteggio principale: assegnazioni coinvolte, non quantita fisiche residue.

Severita:

- `DANGER` se esiste almeno una scadenza precedente a oggi;
- `WARNING` se la scadenza piu vicina e entro sette giorni;
- `INFO` negli altri casi.

Collegamenti:

- amministratore: `/inventory?attention=expiring`;
- assegnatario: `/inventory?view=mine&attention=expiring`.

### Economia: `FINANCE_MOVEMENTS_UNRECONCILED`

Destinatari: Super Admin, Admin e Tesoriere.

Regola:

- movimento ordinario, attivo e non cancellato;
- esercizio corrente oppure data compresa dall'inizio dell'anno a oggi;
- `reconciled = false`;
- le due gambe tecniche dei trasferimenti non sono conteggiate come movimenti ordinari da riconciliare.

Conteggio principale: movimenti non riconciliati. La descrizione mostra l'importo complessivo nella valuta del tenant; se fossero introdotte piu valute, deve mostrare un totale distinto per valuta e non sommarle.

Severita:

- `WARNING` se il movimento non riconciliato piu vecchio supera `finance-unreconciled-warning-days`, valore predefinito 30;
- `INFO` negli altri casi.

Collegamento: `/finance?section=movements&reconciled=false`.

### Consegna notifiche: `NOTIFICATION_DELIVERY_FAILED`

Destinatari: Super Admin e Admin del tenant attivo.

Regola: righe `notification_outbox` in stato `FAILED`.

Conteggio principale: eventi tecnici falliti. Titolo e descrizione non espongono destinatari, payload o testo della notifica.

Severita: sempre `DANGER`.

Collegamento: `/admin/notification-delivery?status=FAILED`.

La pagina di destinazione e parte della stessa evolutiva. Deve mostrare soltanto metadati operativi, consentire il riprocessamento idempotente e richiedere conferma. L'errore completo resta nei log; all'utente si espongono classe normalizzata, numero di tentativi, sorgente, operazione e istanti tecnici.

### Conformita: `LEGAL_ACCEPTANCE_REQUIRED`

Destinatari: ogni utente autenticato non conforme.

Regola: la stessa usata da `GET /api/legal/status`.

Severita: `DANGER`.

Collegamento: `/legal/accept`.

La voce e difensiva. Il guard frontend continua ad avere precedenza e impedisce che una persona usi altre funzioni prima dell'accettazione.

## Matrice per autorita

Le attivita sono l'unione delle autorita presenti nel token, non il risultato di un unico ruolo scelto dal frontend.

| Attivita | Super Admin | Admin | Tesoriere | Archivista | Utente | Utente esterno |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Disponibilita personale | si | si | solo con altro ruolo | si | si | solo `PUBLIC` |
| Risposte evento mancanti | si | si | no | no | no | no |
| Presa visione personale | se assegnatario | se assegnatario | se assegnatario | se assegnatario | si | se assegnatario |
| Prese visione amministrative | si | si | no | no | no | no |
| Riconsegne da chiudere | si | si | no | no | no | no |
| Scadenze inventario globali | si | si | no | no | no | no |
| Scadenze inventario personali | se assegnatario | se assegnatario | se assegnatario | se assegnatario | si | se assegnatario |
| Movimenti non riconciliati | si | si | si | no | no | no |
| Consegne tecniche fallite | si | si | no | no | no | no |
| Accettazione legale | si | si | si | si | si | si |

Se una persona possiede sia un ruolo amministrativo sia un ruolo partecipante, le due viste non devono duplicare la stessa attivita. Per l'inventario prevale la riga amministrativa globale quando il conteggio globale contiene anche la riga personale.

## Ordinamento

Il backend restituisce le righe gia ordinate con i seguenti criteri:

1. severita: `DANGER`, `WARNING`, `INFO`;
2. `dueAt` crescente, con valori nulli in fondo;
3. ordine funzionale stabile: conformita, calendario, inventario, economia, notifiche tecniche;
4. codice dell'attivita come ultimo criterio deterministico.

Il frontend non ricalcola la priorita e non riordina in base al colore.

## Contratto API

### Endpoint

```http
GET /api/dashboard/operations
```

L'endpoint:

- non accetta un tenant come parametro;
- usa esclusivamente il tenant attivato dalla sessione autenticata;
- risolve l'insieme completo delle autorita dal token;
- non accetta soglie arbitrarie dal client;
- restituisce `Cache-Control: no-store` per evitare riuso tra sessioni o tenant;
- usa date ISO 8601 e istanti con offset.

### Risposta indicativa

```json
{
  "generatedAt": "2026-09-03T10:15:30+02:00",
  "status": "COMPLETE",
  "summary": {
    "groupCount": 4,
    "dangerCount": 1,
    "warningCount": 2,
    "infoCount": 1
  },
  "items": [
    {
      "key": "CALENDAR_AVAILABILITY_REQUIRED",
      "type": "CALENDAR_AVAILABILITY_REQUIRED",
      "domain": "CALENDAR",
      "severity": "DANGER",
      "count": 2,
      "relatedCount": null,
      "title": "Disponibilita da indicare",
      "description": "Il primo evento inizia domani alle 20:30.",
      "dueAt": "2026-09-04T20:30:00+02:00",
      "actionLabel": "Rispondi",
      "targetPath": "/calendar?attention=my-missing-availability"
    }
  ],
  "unavailableDomains": []
}
```

### DTO backend

```java
public record OperationalDashboardDTO(
    ZonedDateTime generatedAt,
    DashboardResultStatus status,
    OperationalSummaryDTO summary,
    List<OperationalItemDTO> items,
    List<DashboardDomain> unavailableDomains
) {}

public record OperationalSummaryDTO(
    int groupCount,
    int dangerCount,
    int warningCount,
    int infoCount
) {}

public record OperationalItemDTO(
    String key,
    DashboardOperationType type,
    DashboardDomain domain,
    DashboardSeverity severity,
    long count,
    Long relatedCount,
    String title,
    String description,
    ZonedDateTime dueAt,
    String actionLabel,
    String targetPath
) {}
```

`key` e stabile per tipo nella prima versione e viene usato dal frontend per il tracking. Non deve contenere tenant, ID utente o dati personali.

`relatedCount` serve soltanto quando due unita sono utili, per esempio quattro eventi e ventitre risposte mancanti. Un valore assente viene serializzato come `null` oppure omesso in modo coerente con la configurazione Jackson esistente.

### Risposta parziale

Se una query di dominio fallisce, l'endpoint restituisce `200 OK` con:

- `status = PARTIAL`;
- gli elementi prodotti dagli altri domini;
- il dominio fallito in `unavailableDomains`;
- nessun messaggio tecnico o stack trace.

Il frontend mostra un `InlineAlertComponent` con `role="status"`: **Alcune attivita non sono disponibili. Riprova tra poco.**

Autenticazione assente, tenant non risolto o errore che impedisce ogni provider continuano a produrre la normale risposta RFC 7807 con stato HTTP appropriato. Una risposta parziale non deve mascherare un problema di sicurezza o di tenant routing.

## Architettura backend

### Componenti

```text
OperationalDashboardResource
        |
OperationalDashboardService
        |
        +-- CalendarOperationProvider
        +-- InventoryOperationProvider
        +-- FinanceOperationProvider
        +-- NotificationOperationProvider
        +-- LegalOperationProvider
```

`OperationalDashboardService` orchestra provider indipendenti, applica ordinamento e deduplicazione e costruisce il riepilogo. Ogni provider:

- dichiara il proprio `DashboardDomain`;
- verifica le autorita ricevute prima di eseguire query;
- lavora in una transazione `readOnly` propria;
- restituisce zero o piu righe semantiche;
- non chiama endpoint HTTP interni;
- non modifica entita e non pubblica notifiche.

I provider vengono eseguiti in sequenza nella prima versione. Il tenant context e basato sul flusso di esecuzione e la parallelizzazione introdurrebbe complessita e pressione sul pool JDBC senza un beneficio dimostrato.

Per permettere una risposta parziale senza mantenere una transazione gia marcata per il rollback, ogni provider usa un confine transazionale indipendente. L'orchestratore non apre una transazione globale.

### Query

Le query devono restituire proiezioni aggregate e non caricare collezioni di entita per contarle.

Sono richieste almeno:

- calendario personale: conteggio e minima `start_date` degli eventi visibili privi di risposta dell'utente;
- calendario amministrativo: raggruppamento degli eventi visibili nel periodo e conteggio degli utenti attivi attesi senza risposta;
- inventario: riuso dei conteggi gia presenti e nuove proiezioni per minima scadenza, scaduti e in scadenza;
- economia: conteggio, importo e minima `booking_date` dei movimenti ordinari non riconciliati;
- notifiche: conteggio e minima `occurred_at` delle righe `FAILED`.

Prima di aggiungere indici si deve verificare il piano PostgreSQL su un dataset realistico. Indici candidati:

- `calendar_event(state, start_date)` con esclusione logica coerente con lo schema;
- `calendar_event_availability(event_id, user_id)`, gia naturalmente candidato a vincolo univoco;
- `inventory_assignment(expiration_date, status)` limitato alle righe non cancellate;
- `financial_movement(reconciled, booking_date)` per movimenti attivi ordinari;
- `notification_outbox(status, occurred_at)`.

Nessuna migrazione e obbligatoria per il modello funzionale; gli eventuali indici derivano dalle misure, non da supposizioni.

### Soglie configurabili

Configurazione proposta:

```yaml
application:
  dashboard:
    enabled: true
    calendar-look-ahead-days: 14
    inventory-expiration-look-ahead-days: 30
    inventory-warning-days: 7
    finance-unreconciled-warning-days: 30
```

Le proprieta devono essere validate all'avvio:

- valori maggiori o uguali a zero;
- orizzonti massimi di 366 giorni;
- `inventory-warning-days` non superiore all'orizzonte inventario.

### Autorizzazioni

In `SecurityConfiguration`:

```java
.requestMatchers(HttpMethod.GET, "/api/dashboard/operations").authenticated()
```

Questa regola rende raggiungibile l'aggregatore, ma non sostituisce le verifiche nei provider. Il frontend non e un confine di sicurezza e le direttive `hasRoles` controllano soltanto la presentazione.

Il provider non deve accettare user ID, tenant o ruolo dal client. L'identita personale deriva dal subject autenticato; il tenant deriva dal contesto gia validato.

### Contenuti e privacy

- La risposta non contiene elenchi di persone che non hanno risposto.
- Per il calendario amministrativo puo nominare soltanto l'evento piu vicino, gia visibile al ruolo.
- Per l'inventario personale non espone altri assegnatari.
- Per la finanza non espone descrizioni o controparti dei movimenti.
- Per le notifiche tecniche non espone testo, audience o eccezioni complete.
- `targetPath` deve iniziare con una sola `/`, non con `//`, e appartenere a una allowlist per tipo.

## Frontend

### Moduli e componenti

Nuove aree previste:

```text
taurus-fe/src/app/module/operational-dashboard.ts
taurus-fe/src/app/service/operational-dashboard.service.ts
taurus-fe/src/app/pages/dashboard/components/operations-widget/
taurus-fe/src/app/pages/admin/notification-delivery/
```

`OperationalDashboardService` espone `getOperations()` e non conserva dati globali tra tenant. Un cambio tenant invalida immediatamente lo stato precedente.

`OperationsWidgetComponent` riceve il DTO gia ordinato e produce:

- intestazione con conteggio dei gruppi e pulsante **Aggiorna**;
- `p-skeleton` conformi alla forma di tre righe nel primo caricamento;
- una lista semantica di `ListRowComponent`;
- `p-tag` testuale per la severita;
- `EmptyStateComponent` nello stato positivo;
- `InlineAlertComponent` per risultato parziale o errore recuperabile.

Il widget usa `ChangeDetectionStrategy.OnPush` e segnali oppure input immutabili. Non apre dialoghi e non contiene logica di autorizzazione.

### Collegamenti profondi

Le pagine di destinazione devono interpretare i parametri senza eseguire automaticamente azioni distruttive:

| Percorso | Comportamento |
| --- | --- |
| `/calendar?attention=my-missing-availability` | eventi futuri visibili senza risposta personale |
| `/calendar?attention=missing-availability` | eventi futuri con almeno una risposta attesa mancante |
| `/inventory?view=mine&attention=pending-decisions` | assegnazioni personali da confermare |
| `/inventory?attention=pending-decisions` | assegnazioni amministrative senza presa visione |
| `/inventory?attention=pending-returns` | riconsegne aperte |
| `/inventory?attention=expiring` | assegnazioni scadute o in scadenza |
| `/finance?section=movements&reconciled=false` | movimenti ordinari non riconciliati |
| `/admin/notification-delivery?status=FAILED` | consegne tecniche fallite |

Parametri sconosciuti vengono ignorati in sicurezza e producono la vista predefinita. Filtri non autorizzati non devono ampliare i dati restituiti dalle API.

### Errori

- Primo caricamento fallito: banner di errore con `role="alert"` e azione **Riprova**.
- Aggiornamento fallito con dati precedenti: i dati restano visibili, viene mostrato un avviso non bloccante e l'istante non viene aggiornato.
- Risposta `PARTIAL`: le righe disponibili restano utilizzabili e i domini mancanti non vengono rappresentati come zero.
- Collegamento non valido: viene ignorato e registrato in console soltanto in sviluppo; non viene eseguita navigazione esterna.

### Accessibilita

- La sezione ha titolo `h2` collegato tramite `aria-labelledby`.
- La lista usa `ul` e `li`; non si rende cliccabile l'intera riga se contiene gia un pulsante o un link.
- Le icone decorative hanno `aria-hidden="true"`.
- Severita e quantita sono disponibili come testo accessibile.
- Il contenitore usa `aria-busy="true"` durante il primo caricamento.
- Il messaggio dopo aggiornamento manuale usa una live region `polite` senza annunciare ogni refresh automatico.
- Focus, ordine di tabulazione e contrasto seguono i componenti condivisi e i token PrimeNG.
- La preferenza `prefers-reduced-motion` disabilita eventuali transizioni non necessarie.

## Console delle consegne tecniche

Per rendere azionabile `NOTIFICATION_DELIVERY_FAILED` serve una superficie amministrativa minima.

### API amministrative

```http
GET  /api/admin/notification-delivery?status=FAILED&page=0&size=20&sort=occurredAt,asc
POST /api/admin/notification-delivery/{id}/retry
POST /api/admin/notification-delivery/retry
```

Il retry massivo riceve un corpo `{ "ids": [1, 2, 3] }`, rifiuta liste vuote, duplicate o superiori a 100 elementi e restituisce il numero di righe riportate in `PENDING`. Tutti gli endpoint sono riservati a Super Admin e Admin e operano soltanto nello schema del tenant attivo.

La ricerca accetta esclusivamente gli stati previsti dal modello e impone una dimensione massima della pagina pari a 100. L'ordinamento predefinito mostra prima l'evento fallito piu vecchio.

### Elenco

Campi visibili:

- ID tecnico;
- sorgente e operazione;
- stato;
- istante dell'evento;
- tentativi eseguiti;
- ultimo aggiornamento tecnico, ricavato da `edit_date`, e prossimo tentativo se presente;
- classe di errore normalizzata;
- hash della chiave evento gia usato nei log.

Non vengono mostrati contenuto, descrizione, destinatari o payload completi.

### Azioni

- **Riprova** su una riga `FAILED`;
- **Riprova selezionate** con conferma e limite massimo di 100 righe;
- nessuna eliminazione manuale nella prima versione.

Il retry:

- mantiene la stessa chiave evento;
- azzera soltanto i campi necessari a riportare la riga in `PENDING`;
- non crea una seconda riga;
- lascia che deduplicazione e dispatcher impediscano doppie notifiche;
- registra autore e istante nell'audit.

## Coerenza e concorrenza

La dashboard e una fotografia informativa, non una transazione di lavoro. Tra visualizzazione e apertura del dettaglio lo stato puo cambiare.

Le pagine di destinazione devono quindi gestire normalmente:

- attivita gia risolta da un altro amministratore;
- record cancellato o non piu visibile;
- conteggio diminuito dopo un aggiornamento concorrente;
- retry tecnico gia richiesto da un'altra istanza.

Non si usano lock pessimisti per costruire la dashboard. Le operazioni di dominio mantengono i propri controlli transazionali e idempotenti.

## Prestazioni

Obiettivi iniziali su ambiente di staging con un tenant realistico:

- `p95` inferiore a 500 ms;
- massimo 12 query aggregate per richiesta completa;
- nessun caricamento N+1;
- risposta inferiore a 32 kB;
- nessun accesso a file binari o OpenSearch per costruire le attivita;
- nessuna query cross-tenant.

Se un provider supera stabilmente 200 ms, deve essere ottimizzato o reso facoltativo prima di introdurre cache. Un'eventuale cache futura deve avere chiave composta da tenant, subject e insieme delle autorita e deve essere invalidata al cambio tenant; nella prima versione non e prevista.

## Osservabilita

Metriche Micrometer proposte:

- timer `taurus.dashboard.operations.duration` con tag `result=complete|partial|failed`;
- counter `taurus.dashboard.operations.requests`;
- counter `taurus.dashboard.provider.failures` con tag `domain`;
- summary `taurus.dashboard.operations.groups`.

Le metriche non devono usare tenant o subject come tag, per evitare cardinalita non limitata. I log strutturati includono tenant, dominio, durata e classe di errore, senza dati delle attivita personali.

Alert raccomandato: rapporto di risposte `PARTIAL` o `failed` maggiore al 5% per dieci minuti.

## Strategia di test

### Unit test backend

- ogni provider restituisce zero righe quando l'autorita non e applicabile;
- calcolo delle tre severita sulle date limite;
- evento che inizia esattamente al confine delle 24 ore;
- visibilita `COMPLETE` e `PUBLIC` per utente ed esterno;
- utente con piu autorita riceve l'unione senza duplicati;
- scadenza inventario personale non espone assegnazioni altrui;
- trasferimenti economici esclusi dal conteggio ordinario;
- valute differenti non vengono sommate;
- path generati conformi alla allowlist;
- ordinamento deterministico;
- riepilogo coerente con le righe restituite.

### Integration test PostgreSQL

- query delle disponibilita con utenti attivi, inattivi e risposte esistenti;
- evento escluso da serie e record cancellati non producono attivita;
- conteggi inventario con restituzioni parziali;
- conteggi finanziari su esercizio corrente e anni precedenti;
- `notification_outbox` `FAILED` visibile soltanto agli amministratori;
- errore di un provider produce `PARTIAL` senza perdere gli altri risultati;
- due tenant con dati omonimi restano isolati;
- cambio tenant nella stessa sessione non riusa il risultato precedente.

### Security test

- endpoint anonimo rifiutato;
- nessun parametro puo selezionare tenant o user ID;
- Tesoriere non riceve inventario amministrativo o notifiche tecniche;
- Utente Esterno non riceve eventi `COMPLETE`;
- Admin non puo ottenere dati di un tenant non attivo;
- `targetPath` non accetta URL assoluti o protocol-relative;
- la risposta tecnica non contiene messaggi, destinatari o stack trace.

### Frontend test

- skeleton, errore iniziale, risultato parziale, vuoto e successo;
- etichette singolari e plurali;
- rendering e ordinamento di tutte le severita;
- refresh conserva i dati precedenti durante la richiesta;
- parametro di attenzione applicato dalle pagine di destinazione;
- cambio tenant svuota immediatamente il vecchio snapshot;
- link non valido non viene navigato;
- test tastiera e controlli accessibili del widget;
- layout a 320, 768 e 1280 pixel.

### End-to-end principali

1. Un utente apre la dashboard, vede due eventi senza risposta, risponde al primo e al ritorno il conteggio diventa uno.
2. Un Admin apre le prese visione pendenti, filtra l'inventario e verifica che la riga scompaia dopo la decisione dell'utente.
3. Un Tesoriere apre i movimenti non riconciliati, ne riconcilia uno e vede il nuovo conteggio.
4. Un Admin riprocessa una consegna `FAILED`; la dashboard non mostra piu l'anomalia dopo la consegna riuscita.
5. Il provider economico fallisce: calendario e inventario restano disponibili con indicazione di risultato parziale.
6. Un Utente Esterno non vede eventi non pubblici ne attivita amministrative.

## Piano di implementazione

### Fase 1 - Contratto e infrastruttura

1. aggiungere enum, DTO, proprieta validate e resource;
2. costruire orchestratore e interfaccia provider;
3. implementare risultato parziale, ordinamento e allowlist dei path;
4. aggiungere test di sicurezza e isolamento tenant.

### Fase 2 - Provider funzionali

1. calendario personale e amministrativo;
2. inventario personale e amministrativo;
3. movimenti non riconciliati;
4. conformita difensiva;
5. query plan e indici soltanto se necessari.

### Fase 3 - Widget frontend

1. service e modelli TypeScript;
2. `OperationsWidgetComponent` con tutti gli stati;
3. integrazione in testa alla dashboard;
4. refresh e invalidazione al cambio tenant;
5. deep link e filtri nelle pagine calendario, inventario e finanza.

### Fase 4 - Consegne tecniche

1. provider `NotificationOperationProvider`;
2. endpoint amministrativo paginato per l'outbox;
3. retry singolo e massivo idempotente;
4. pagina `/admin/notification-delivery`;
5. audit, metriche e test concorrenti.

### Fase 5 - Hardening e rilascio

1. test end-to-end per ogni profilo;
2. verifica accessibilita e responsive;
3. misurazione `p95` e query plan con dataset realistico;
4. smoke test del risultato parziale;
5. rilascio iniziale con `application.dashboard.enabled=false`, attivazione in staging e poi produzione.

## Criteri di accettazione

La funzionalita e completa quando:

1. ogni utente autenticato vede soltanto attivita autorizzate nel tenant attivo;
2. un utente con piu ruoli riceve l'unione corretta senza duplicazioni;
3. nessuna attivita possiede uno stato persistito separato dal dominio;
4. la risoluzione nel modulo autorevole rimuove la voce al refresh successivo;
5. severita e ordinamento rispettano le regole definite;
6. tutti i collegamenti aprono la pagina gia filtrata corretta;
7. gli eventi personali rispettano visibilita e orizzonte temporale;
8. il pubblico atteso amministrativo segue esclusivamente la regola documentata;
9. scadenze inventario e movimenti non riconciliati gestiscono correttamente i confini temporali;
10. un dominio fallito produce una risposta parziale senza rappresentare dati mancanti come zero;
11. un errore di sicurezza o tenant non viene convertito in risultato parziale;
12. le consegne tecniche non espongono payload o destinatari;
13. il retry mantiene idempotenza e deduplicazione;
14. il frontend mostra caricamento, vuoto, parziale, errore e successo;
15. il widget e utilizzabile da tastiera e non dipende dal colore;
16. il cambio tenant invalida lo snapshot precedente;
17. l'endpoint rispetta gli obiettivi di prestazione in staging;
18. test unitari, di integrazione, sicurezza e frontend sono verdi;
19. nessuna query della dashboard attraversa schemi tenant diversi;
20. documentazione API e note di rilascio descrivono soglie e comportamento.

## File e aree previste

### Backend da creare

```text
taurus-be/src/main/java/com/fundaro/zodiac/taurus/web/rest/OperationalDashboardResource.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/web/rest/NotificationDeliveryAdminResource.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/OperationalDashboardService.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/NotificationDeliveryAdminService.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/dashboard/DashboardOperationProvider.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/dashboard/CalendarOperationProvider.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/dashboard/InventoryOperationProvider.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/dashboard/FinanceOperationProvider.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/dashboard/NotificationOperationProvider.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/dashboard/LegalOperationProvider.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/dto/dashboard/
```

### Backend da modificare

```text
taurus-be/src/main/java/com/fundaro/zodiac/taurus/config/ApplicationProperties.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/config/SecurityConfiguration.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/repository/CalendarEventsRepository.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/repository/inventory/InventoryAssignmentRepository.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/repository/finance/FinancialMovementRepository.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/repository/notification/NotificationOutboxRepository.java
taurus-be/src/main/resources/config/application.yml
```

### Frontend da creare

```text
taurus-fe/src/app/module/operational-dashboard.ts
taurus-fe/src/app/service/operational-dashboard.service.ts
taurus-fe/src/app/pages/dashboard/components/operations-widget/
taurus-fe/src/app/pages/admin/notification-delivery/
```

### Frontend da modificare

```text
taurus-fe/src/app/pages/dashboard/dashboard.component.ts
taurus-fe/src/app/pages/dashboard/dashboard.component.html
taurus-fe/src/app/pages/calendar-events/
taurus-fe/src/app/pages/inventory/
taurus-fe/src/app/pages/finance/
taurus-fe/src/app/components/menu/menu.component.ts
taurus-fe/src/app/service/index.ts
taurus-fe/src/app/module/index.ts
```

## Evoluzioni successive compatibili

Il contratto per tipo e dominio permette di aggiungere in seguito:

- presenze di eventi passati ancora da consolidare, quando esistera uno stato esplicito di chiusura presenze;
- eventi economici con importi ancora da incassare o pagare;
- richieste GDPR da lavorare;
- media in stato `MIGRATION_PENDING` o `FAILED`;
- processi di caricamento spartiti bloccati;
- preferenze personali per nascondere gruppi puramente informativi;
- aggiornamento real-time come semplice invalidazione dello snapshot.

Queste evoluzioni devono continuare a derivare dal dominio autorevole e non introdurre una tabella generica di task.
