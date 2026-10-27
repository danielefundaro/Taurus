# Dashboard operativa trasversale

## Stato del documento

ID catalogo: `operational-dashboard`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

## Attivazione e rilascio

La console amministrativa delle consegne tecniche è in `/admin/notification-delivery` e non espone contenuti, payload o destinatari.

`application.dashboard.enabled` vale `false` in `application.yml`. Con il flag spento il servizio risponde `COMPLETE` senza voci e il widget mostra lo stato positivo **Tutto sotto controllo**. L'attivazione avviene tramite configurazione esterna, prima in staging e poi in produzione. Non sono richieste migrazioni del database; eventuali indici devono derivare dalla misurazione dei query plan PostgreSQL su dati realistici.

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
- una firma richiesta scompare quando l'utente ha inserito i dati e dato tutte le conferme che l'atto attende da lui, o quando l'atto viene annullato o dichiarato firmato su carta;
- una riconsegna scompare quando l'amministratore la chiude;
- un movimento scompare dal conteggio quando viene riconciliato;
- un errore di consegna scompare quando l'evento tecnico viene riprocessato con successo.

Questa scelta evita divergenze, duplicazione di audit e attivita dichiarate concluse mentre il dato di dominio e ancora pendente.

## Contesto

Accanto alla sezione **Da fare**, la dashboard carica in modo indipendente conteggi di tenant, utenti, album e tracce, gli ultimi inserimenti, i prossimi eventi, le notifiche e i riepiloghi inventario. Per questi widget il ruolo effettivo selezionato dal frontend decide quali chiamate eseguire.

La base visuale riusa `DetailSectionComponent`, `ListRowComponent`, `EmptyStateComponent`, `InlineAlertComponent`, `p-skeleton`, `p-tag` e `p-button`, senza una seconda libreria di card.

La sezione **Da fare** risponde a questi limiti dei soli widget:

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
9. collegamenti profondi verso viste gia filtrate;
10. firme richieste sui fogli di affidamento;
11. guasti inventario non sicuri o limitanti;
12. preparazione degli eventi bloccata, da verificare o da chiudere.

Le voci dipendono dalle funzionalita del tenant:

- i provider Inventario ed Economia vengono saltati senza `INVENTORY` o `FINANCE`;
- la firma richiesta compare solo con `INVENTORY_SIGNATURE`;
- i guasti compaiono solo con `INVENTORY_QR` e `application.inventory.qr.enabled`;
- le voci di preparazione compaiono solo con `EVENT_PREPARATION`;
- nel frontend il riquadro Inventario della dashboard compare solo con la funzionalita Inventario.

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
- quando la finestra torna visibile dopo almeno cinque minuti, o subito se nel frattempo il tenant del token è cambiato;
- dopo il ritorno da una pagina aperta tramite una voce operativa;
- quando l'utente usa il comando **Aggiorna**.

Non e previsto polling a intervallo fisso nella prima versione. Durante il ricaricamento i dati gia presenti restano visibili e il comando mostra lo stato occupato; gli skeleton sono usati soltanto per il primo caricamento.

## Composizione della pagina

L'ordine e:

1. intestazione `Dashboard`, sottotitolo contestuale e istante dell'ultimo aggiornamento;
2. sezione **Da fare**, a tutta larghezza;
3. indicatori sintetici autorizzati per il ruolo;
4. inventario, solo con la funzionalita attiva;
5. contenuti recenti del catalogo;
6. prossimi eventi;
7. notifiche personali.

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

Con la funzionalita `EVENT_PREPARATION` attiva, un evento non entra in questo conteggio soltanto quando una voce di preparazione lo conta gia con la criticita `AVAILABILITY_INCOMPLETE`: un blocco entro l'orizzonte del calendario o un avviso entro sette giorni. Un evento tra sette e quattordici giorni con il solo avviso sulle disponibilita resta quindi in `CALENDAR_RESPONSES_MISSING`; la deduplicazione usa la stessa selezione che produce le righe di preparazione, non la sola presenza della criticita.

Questa euristica deve essere sostituita da un pubblico invitato esplicito se in futuro gli eventi vengono indirizzati a sottoinsiemi di utenti. Fino ad allora non si devono inventare filtri per strumento o sezione musicale.

### Preparazione eventi: `EVENT_PREPARATION_BLOCKED`, `EVENT_PREPARATION_ATTENTION`, `EVENT_FOLLOW_UP_REQUIRED`

Destinatari: Super Admin e Admin, con la funzionalita `EVENT_PREPARATION`.

Regole, sulla valutazione della [preparazione operativa](event-preparation-spec.md):

- `EVENT_PREPARATION_BLOCKED`: eventi non ancora iniziati, entro `calendar-look-ahead-days`, con stato `BLOCKED`; severita `DANGER`;
- `EVENT_PREPARATION_ATTENTION`: eventi non ancora iniziati entro sette giorni con stato `ATTENTION`; severita `WARNING`;
- `EVENT_FOLLOW_UP_REQUIRED`: eventi terminati negli ultimi 30 giorni con chiusura `TO_CLOSE`; severita `WARNING`.

Conteggio principale: eventi coinvolti. La descrizione nomina l'evento piu vicino.

Collegamenti: `/calendar?attention=event-preparation` per le prime due voci, `/calendar?attention=event-follow-up` per il follow-up. L'elenco del calendario chiede a `GET /api/dashboard/operations/calendar-events` gli eventi contati dalle righe e li pagina sul server: i criteri di preparazione non si ricalcolano nel client.

Un evento tra sette e quattordici giorni con il solo avviso sulle disponibilita non compare in `EVENT_PREPARATION_ATTENTION` e resta in `CALENDAR_RESPONSES_MISSING`.

La valutazione della preparazione esegue alcune query per ogni evento candidato. Con 6-10 eventi nell'orizzonte il costo e contenuto, ma il bilancio di query delle [Prestazioni](#prestazioni) vale con la preparazione spenta: il costo per evento e un requisito aperto della [preparazione operativa](event-preparation-spec.md#requisiti-aperti).

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

### Firma personale: `INVENTORY_SIGNATURE_REQUIRED`

Destinatari: ogni utente autenticato firmatario di un atto di un foglio di affidamento firmato elettronicamente, amministratori compresi quando firmano per l'Associazione. La voce richiede la funzionalita `INVENTORY_SIGNATURE`.

Regola: atti non annullati e non eliminati, in attesa dei dati o delle firme, per cui l'utente deve ancora inserire i dati dell'emissione o dare una conferma: accettazione o approvazione delle clausole come Affidatario, presa visione come socio minorenne, firma per l'Associazione. È la stessa regola del gruppo «In attesa della tua firma» del riquadro **Firme** (`LoanSheetSignatureService.countAwaitingUser`). Gli atti che l'Affidatario non può ancora firmare per prese visione in attesa sono contati: la voce di presa visione compare accanto.

Conteggio principale: atti che attendono l'utente.

Severita: `WARNING`.

Collegamento: `/inventory?view=mine`, la vista personale che si apre con il riquadro **Firme**.

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

### Guasti inventario: `INVENTORY_UNSAFE_ISSUES` e `INVENTORY_LIMITING_ISSUES`

Destinatari: Super Admin e Admin, con la funzionalita `INVENTORY_QR` e `application.inventory.qr.enabled`.

Regola: segnalazioni di guasto non cancellate in stato `OPEN` o `ACKNOWLEDGED`, rispettivamente con gravita `UNSAFE` o `LIMITING`.

Conteggio principale: segnalazioni aperte.

Severita: `DANGER` per `INVENTORY_UNSAFE_ISSUES`, `WARNING` per `INVENTORY_LIMITING_ISSUES`.

Collegamenti: `/inventory?attention=issues-unsafe` e `/inventory?attention=issues-limiting`.

### Economia: `FINANCE_MOVEMENTS_UNRECONCILED`

Destinatari: Super Admin, Admin e Tesoriere.

Regola:

- movimento non cancellato di natura `ORDINARY`: aperture (`OPENING`) e le due gambe dei trasferimenti (`TRANSFER`) non sono conteggiate;
- movimento di un qualsiasi esercizio `OPEN` oppure con data compresa dall'inizio dell'anno a oggi;
- `reconciled = false`.

Conteggio principale: movimenti non riconciliati. La descrizione mostra l'importo complessivo con un totale distinto per valuta, separati da punto e virgola, senza sommare valute diverse.

Severita:

- `WARNING` se il movimento non riconciliato piu vecchio supera `finance-unreconciled-warning-days`, valore predefinito 30;
- `INFO` negli altri casi.

Collegamento: `/finance?section=movements&reconciled=false`.

### Consegna notifiche: `NOTIFICATION_DELIVERY_FAILED`

Destinatari: Super Admin e Admin del tenant attivo.

Regola: righe in stato `FAILED` dell'unione di `notification_outbox`, `notification_push_delivery` e `push_reminders`.

Conteggio principale: consegne tecniche fallite. Titolo e descrizione non espongono destinatari, payload o testo della notifica.

Severita: sempre `DANGER`.

Collegamento: `/admin/notification-delivery?status=FAILED`.

La pagina di destinazione mostra soltanto metadati operativi e consente il riprocessamento idempotente con conferma e, per le code push, la chiusura tecnica. L'errore completo resta nei log; all'utente si espongono classe normalizzata, numero di tentativi, sorgente, operazione e istanti tecnici.

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
| Preparazione eventi bloccata o da verificare | si | si | no | no | no | no |
| Eventi da chiudere | si | si | no | no | no | no |
| Presa visione personale | se assegnatario | se assegnatario | se assegnatario | se assegnatario | si | se assegnatario |
| Prese visione amministrative | si | si | no | no | no | no |
| Firma personale dei fogli di affidamento | se firmatario | se firmatario | se firmatario | se firmatario | se firmatario | se firmatario |
| Riconsegne da chiudere | si | si | no | no | no | no |
| Scadenze inventario globali | si | si | no | no | no | no |
| Scadenze inventario personali | se assegnatario | se assegnatario | se assegnatario | se assegnatario | si | se assegnatario |
| Guasti inventario non sicuri o limitanti | si | si | no | no | no | no |
| Movimenti non riconciliati | si | si | si | no | no | no |
| Consegne tecniche fallite | si | si | no | no | no | no |
| Accettazione legale | si | si | si | si | si | si |

La matrice vale con le funzionalita del tenant richieste da ciascuna voce (vedi [Ambito della prima versione](#ambito-della-prima-versione)).

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

```http
GET /api/dashboard/operations/calendar-events?attention=event-preparation|event-follow-up
```

Restituisce gli identificativi degli eventi che le righe di preparazione contano, ordinati per scadenza e poi per identificativo, con `Cache-Control: no-store`. È riservato a Super Admin e Admin; usa la stessa selezione delle righe (`CalendarOperationProvider.attentionEventIds`), quindi l'elenco filtrato e il conteggio della dashboard coincidono. Un valore di `attention` diverso dai due ammessi risponde `400` con `dashboard.attention.invalid`. Senza `EVENT_PREPARATION` l'elenco è vuoto. Non dipende da `application.dashboard.enabled`: il collegamento resta valido anche se il widget non è attivo.

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
- notifiche: conteggio e minimo istante delle righe `FAILED` di outbox, consegne push e promemoria push.

Prima di aggiungere indici si deve verificare il piano PostgreSQL su un dataset realistico. Indici candidati:

- `calendar_event(state, start_date)` con esclusione logica coerente con lo schema;
- `calendar_event_availability(event_id, user_id)`, gia naturalmente candidato a vincolo univoco;
- `inventory_assignment(expiration_date, status)` limitato alle righe non cancellate;
- `financial_movement(reconciled, booking_date)` per movimenti attivi ordinari;
- `notification_outbox(status, occurred_at)`.

Nessuna migrazione e obbligatoria per il modello funzionale; gli eventuali indici derivano dalle misure, non da supposizioni.

### Soglie configurabili

Configurazione in `application.yml`:

```yaml
application:
  dashboard:
    enabled: false
    calendar-look-ahead-days: 14
    inventory-expiration-look-ahead-days: 30
    inventory-warning-days: 7
    finance-unreconciled-warning-days: 30
```

Le proprieta sono validate all'avvio:

- valori maggiori o uguali a zero;
- orizzonti massimi di 366 giorni;
- `inventory-warning-days` non superiore a `inventory-expiration-look-ahead-days`: con un valore maggiore l'applicazione non parte e il messaggio nomina le due proprieta.

`DashboardPropertiesValidationTest` verifica i valori predefiniti, il caso limite con soglie uguali e i tre rifiuti.

### Autorizzazioni

In `SecurityConfiguration`:

```java
.requestMatchers(HttpMethod.GET, "/api/dashboard/operations").authenticated()
.requestMatchers(HttpMethod.GET, "/api/dashboard/operations/calendar-events")
    .hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN)
.requestMatchers("/api/dashboard/**").denyAll()
```

Ogni altro metodo o percorso sotto `/api/dashboard` è rifiutato. Questa regola rende raggiungibile l'aggregatore, ma non sostituisce le verifiche nei provider. Il frontend non e un confine di sicurezza e le direttive `hasRoles` controllano soltanto la presentazione.

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

Aree:

```text
taurus-fe/src/app/module/operational-dashboard.ts
taurus-fe/src/app/service/operational-dashboard.service.ts
taurus-fe/src/app/pages/dashboard/components/operations-widget/
taurus-fe/src/app/pages/admin/notification-delivery/
```

`OperationalDashboardService` espone `getOperations()` e `getAttentionEvents(attention)` e non conserva dati globali tra tenant. `DashboardComponent` ricorda il tenant del token con cui ha letto lo snapshot:

- se al ricaricamento il tenant del token è cambiato, lo snapshot sparisce prima della nuova richiesta e il widget torna allo scheletro del primo caricamento;
- una risposta arrivata dopo un cambio di tenant viene scartata e la lettura si ripete per il tenant nuovo;
- al ritorno sulla finestra un tenant diverso fa ricaricare subito, senza attendere i cinque minuti;
- un errore di lettura del tenant nuovo è un errore iniziale, non un avviso sopra i dati del tenant precedente.

Oggi il tenant cambia soltanto con un nuovo accesso, che ricarica l'applicazione; l'invalidazione protegge anche un token rinnovato con un altro tenant.

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
| `/inventory?view=mine` | vista personale con il riquadro **Firme** degli atti da firmare |
| `/inventory?attention=pending-returns` | riconsegne aperte |
| `/inventory?attention=expiring` | assegnazioni scadute o in scadenza |
| `/inventory?view=mine&attention=expiring` | proprie assegnazioni scadute o in scadenza |
| `/inventory?attention=issues-unsafe` | guasti non sicuri aperti |
| `/inventory?attention=issues-limiting` | guasti limitanti aperti |
| `/calendar?attention=event-preparation` | eventi con preparazione bloccata o da verificare contati dalla dashboard, paginati sul server |
| `/calendar?attention=event-follow-up` | eventi conclusi con le attivita successive da chiudere, paginati sul server |
| `/finance?section=movements&reconciled=false` | movimenti ordinari non riconciliati |
| `/admin/notification-delivery?status=FAILED` | consegne tecniche fallite |

Il backend accetta per ogni tipo soltanto i percorsi della propria allowlist (questa tabella piu `/legal/accept`): una voce con un altro percorso fa considerare non disponibile il suo dominio. Parametri sconosciuti vengono ignorati in sicurezza e producono la vista predefinita. Filtri non autorizzati non devono ampliare i dati restituiti dalle API.

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
GET  /api/admin/notification-delivery?status=FAILED&origin=&source=&operation=&from=&to=&page=0&size=20&sort=occurredAt,asc
POST /api/admin/notification-delivery/{origin}/{id}/retry
POST /api/admin/notification-delivery/{origin}/{id}/close
POST /api/admin/notification-delivery/retry
```

Le righe provengono da tre origini: `OUTBOX` (fan-out in-app di `notification_outbox`), `PUSH` (`notification_push_delivery`) e `REMINDER` (`push_reminders`). Ogni riga e identificata da origine e ID.

- La ricerca filtra per `status` (default `FAILED`), `origin`, `source`, `operation` e intervallo `from`/`to`; impone una pagina massima di 100 righe. L'ordinamento predefinito mostra prima l'evento fallito piu vecchio.
- Il retry singolo riporta in `PENDING` una riga `FAILED`; su una riga gia `PENDING` non fa nulla, sugli altri stati risponde `409 notification.delivery.notFailed`.
- Il retry massivo riceve un corpo `{ "refs": [{ "origin": "OUTBOX", "id": 1 }] }`, rifiuta liste vuote, duplicate o superiori a 100 elementi, ignora le righe non `FAILED` e restituisce `retriedCount`.
- La chiusura tecnica riceve un motivo `{ "reason": "NO_LONGER_RELEVANT" }` (maiuscole, cifre e `_`, massimo 32 caratteri) e porta in `SKIPPED` una riga `PENDING` o `FAILED`. Vale solo per `PUSH` e `REMINDER`; per `OUTBOX` risponde `409 notification.delivery.close.unsupported`.

Tutti gli endpoint sono riservati a Super Admin e Admin e operano soltanto nello schema del tenant attivo.

### Elenco

Campi visibili:

- ID tecnico;
- origine e tipo di consegna;
- sorgente e operazione;
- stato ed eventuale motivo di chiusura (`skipReason`);
- istante dell'evento;
- tentativi eseguiti;
- ultimo aggiornamento tecnico, ricavato da `edit_date`, e prossimo tentativo se presente;
- classe di errore normalizzata;
- hash della chiave evento gia usato nei log.

Non vengono mostrati contenuto, descrizione, destinatari o payload completi.

### Azioni

- **Riprova** su una riga `FAILED`;
- **Riprova selezionate** con conferma e limite massimo di 100 righe;
- **Chiudi…** per le righe `PUSH` e `REMINDER` in stato `PENDING` o `FAILED`, con i motivi «Non piu pertinente», «Dispositivo non raggiungibile» e «Chiusura manuale»;
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

### Misura su dataset realistico

`OperationalDashboardIT` prepara un tenant con il volume di qualche anno di attività — 150 persone, 700 eventi con 73.500 risposte, 1.500 movimenti, 2.000 assegnazioni con revisioni e prese visione, 5.000 righe di outbox —, esegue `ANALYZE` e misura la richiesta completa di un amministratore con la preparazione spenta. Al 2026-10-27, su PostgreSQL in Testcontainers e in tre esecuzioni:

| Misura | Obiettivo | Valore misurato |
| --- | --- | --- |
| `p95` della richiesta completa | sotto 500 ms | 157-181 ms |
| `p95` del provider più lento | sotto 200 ms | 48-79 ms (Calendario o Inventario) |
| Query per richiesta | al massimo 12 | 11 |
| Dimensione della risposta | sotto 32 kB | circa 2,4 kB |

I piani generici di PostgreSQL (`EXPLAIN (GENERIC_PLAN)`) di ogni query finiscono in `taurus-be/target/dashboard-query-plans.txt`:

- calendario: gli eventi del periodo usano `idx_calendar_event_range`, le risposte `uq_calendar_event_availability_active_user` con sola lettura dell'indice; `app_user` si legge per intero o per `keycloak_id` a seconda di quanti utenti sono attesi;
- economia: `idx_financial_movement_reconciled_date` sui movimenti non riconciliati;
- scadenze inventario: `idx_inventory_assignment_expiration`, anche con il filtro facoltativo sull'utente;
- riconsegne, consegne push e promemoria: indici esistenti;
- prese visione dell'inventario: anti join con scansione sequenziale di assegnazioni, revisioni e decisioni, la query più costosa per il pianificatore; il costo cresce con le assegnazioni del tenant;
- notifiche: `notification_outbox` si legge per intero, perché il solo indice parziale riguarda le righe `PENDING`; con 5.000 righe il provider resta sotto i 40 ms.

A questi volumi nessun indice nuovo è giustificato: i primi quattro candidati di [Query](#query) esistono già e i piani li usano. `notification_outbox(status, occurred_at)` e un indice per le prese visione servono soltanto se outbox o assegnazioni crescono di un ordine di grandezza; il file dei piani è la verifica da ripetere. La misura non sostituisce quella su staging, che resta al profilo dashboard di k6 del [runbook](runbook-test.md).

## Osservabilita

Metriche Micrometer (`DashboardMetrics`):

- timer `taurus.dashboard.operations.duration` con tag `result=complete|partial|failed`;
- counter `taurus.dashboard.operations.requests` con lo stesso tag `result`;
- counter `taurus.dashboard.provider.failures` con tag `domain`;
- summary `taurus.dashboard.operations.groups` con tag `result`, escluse le richieste fallite.

Le metriche non devono usare tenant o subject come tag, per evitare cardinalita non limitata. I log strutturati includono tenant, dominio, durata e classe di errore, senza dati delle attivita personali.

Alert raccomandato: rapporto di risposte `PARTIAL` o `failed` maggiore al 5% per dieci minuti.

## Strategia di test

I test sono:

- backend, unitari: `OperationalDashboardServiceTest`, `CalendarOperationProviderTest`, `InventoryOperationProviderTest`, `FinanceOperationProviderTest`, `LegalOperationProviderTest`, `NotificationOperationProviderTest` e `NotificationDeliveryAdminServiceTest`;
- backend, configurazione e sicurezza: `DashboardPropertiesValidationTest` e `DashboardSecurityConfigurationTest`;
- backend, PostgreSQL: `OperationalDashboardIT`, che copre anche le [Prestazioni](#prestazioni);
- frontend: `operations-widget.component.spec.ts`, `dashboard.component.spec.ts`, `calendar-events.component.spec.ts` e `notification-delivery-admin.service.spec.ts`;
- end-to-end: `taurus-fe/e2e/specs/operational-dashboard.spec.ts`.

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

`OperationalDashboardIT` usa tenant provisionati con Liquibase e dati scritti dai servizi di dominio o in SQL dove il servizio non li produce (eventi esclusi da una serie, restituzioni parziali, consegne fallite):

- query delle disponibilita con utenti attivi, inattivi e risposte esistenti;
- evento escluso da serie e record cancellati non producono attivita;
- conteggi inventario con restituzioni parziali;
- conteggi finanziari su esercizio corrente e anni precedenti;
- `notification_outbox` `FAILED` visibile soltanto agli amministratori;
- errore di un provider produce `PARTIAL` senza perdere gli altri risultati;
- due tenant con dati omonimi restano isolati;
- cambio tenant nella stessa sessione non riusa il risultato precedente.

### Security test

`DashboardSecurityConfigurationTest` verifica le regole HTTP; i controlli sul tenant e sulle autorita sono nei test del servizio, dei provider e in `OperationalDashboardIT`:

- endpoint anonimo rifiutato;
- il filtro `calendar-events` e riservato a Super Admin e Admin, ogni altro percorso o metodo sotto `/api/dashboard` e rifiutato;
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

`operational-dashboard.spec.ts` verifica per ogni ruolo configurato nei tenant di prova che le voci restino nella matrice del ruolo e che il widget le mostri tutte, che ogni azione di un amministratore apra la pagina gia filtrata, il comando **Aggiorna** da tastiera, axe-core sulla sezione e il layout a 320, 768 e 1280 pixel. I dati dei tenant di prova non sono fissati, quindi la suite verifica invarianti; i percorsi che modificano dati sono coperti cosi:

- scomparsa della voce dopo la risoluzione nel dominio (percorsi 1-4): `OperationalDashboardIT` per le disponibilita, i test dei provider per gli altri domini;
- risultato parziale (percorso 5): `OperationalDashboardIT`, che rende illeggibile la tabella dei movimenti;
- Utente Esterno (percorso 6): suite end-to-end e `OperationalDashboardIT`.

I percorsi di riferimento sono:

1. Un utente apre la dashboard, vede due eventi senza risposta, risponde al primo e al ritorno il conteggio diventa uno.
2. Un Admin apre le prese visione pendenti, filtra l'inventario e verifica che la riga scompaia dopo la decisione dell'utente.
3. Un Tesoriere apre i movimenti non riconciliati, ne riconcilia uno e vede il nuovo conteggio.
4. Un Admin riprocessa una consegna `FAILED`; la dashboard non mostra piu l'anomalia dopo la consegna riuscita.
5. Il provider economico fallisce: calendario e inventario restano disponibili con indicazione di risultato parziale.
6. Un Utente Esterno non vede eventi non pubblici ne attivita amministrative.

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

## File e aree

### Backend principali

```text
taurus-be/src/main/java/com/fundaro/zodiac/taurus/web/rest/OperationalDashboardResource.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/web/rest/NotificationDeliveryAdminResource.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/OperationalDashboardService.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/NotificationDeliveryAdminService.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/DashboardMetrics.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/repository/notification/NotificationDeliveryAdminQueryRepository.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/dashboard/DashboardOperationProvider.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/dashboard/CalendarOperationProvider.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/dashboard/InventoryOperationProvider.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/dashboard/FinanceOperationProvider.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/dashboard/NotificationOperationProvider.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/dashboard/LegalOperationProvider.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/dto/dashboard/
```

### Backend estesi

```text
taurus-be/src/main/java/com/fundaro/zodiac/taurus/config/ApplicationProperties.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/config/SecurityConfiguration.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/repository/CalendarEventsRepository.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/repository/inventory/InventoryAssignmentRepository.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/repository/finance/FinancialMovementRepository.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/repository/notification/NotificationOutboxRepository.java
taurus-be/src/main/resources/config/application.yml
```

### Frontend principali

```text
taurus-fe/src/app/module/operational-dashboard.ts
taurus-fe/src/app/service/operational-dashboard.service.ts
taurus-fe/src/app/pages/dashboard/components/operations-widget/
taurus-fe/src/app/pages/admin/notification-delivery/
```

### Test

```text
taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/OperationalDashboardIT.java
taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/OperationalDashboardServiceTest.java
taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/dashboard/
taurus-be/src/test/java/com/fundaro/zodiac/taurus/config/DashboardPropertiesValidationTest.java
taurus-be/src/test/java/com/fundaro/zodiac/taurus/config/DashboardSecurityConfigurationTest.java
taurus-fe/src/app/pages/dashboard/dashboard.component.spec.ts
taurus-fe/src/app/pages/calendar-events/calendar-events.component.spec.ts
taurus-fe/e2e/specs/operational-dashboard.spec.ts
```

### Frontend estesi

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

- eventi economici con importi ancora da incassare o pagare;
- richieste GDPR da lavorare;
- media in stato `MIGRATION_PENDING` o `FAILED`;
- processi di caricamento spartiti bloccati;
- preferenze personali per nascondere gruppi puramente informativi;
- aggiornamento real-time come semplice invalidazione dello snapshot.

Queste evoluzioni devono continuare a derivare dal dominio autorevole e non introdurre una tabella generica di task.

## Requisiti aperti

Nessuno: la specifica è coperta dal codice e dai test. La suite end-to-end richiede un'istanza completa con due tenant di prova e, come il resto della suite, non è ancora stata eseguita su un ambiente reale; il costo per evento delle voci di preparazione è un requisito aperto della [preparazione operativa](event-preparation-spec.md#requisiti-aperti).
