# Eventi ricorrenti del calendario

La consegna delle notifiche interne descritte in questa specifica segue `docs/notification-delivery-generalization-spec.md`.

## Stato del documento

ID catalogo: `recurring-calendar-events`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

## Obiettivo

Consentire ad Admin e Super Admin di creare e gestire serie di eventi ricorrenti senza perdere il comportamento già disponibile per il singolo evento:

- visualizzazione nel calendario e nelle liste;
- stato e visibilità per ruolo;
- disponibilità degli utenti;
- rilevazione delle presenze;
- compenso e costi previsti;
- promemoria push;
- cancellazione logica e audit;
- isolamento nello schema PostgreSQL del tenant.

La funzionalità deve permettere di modificare o cancellare una singola occorrenza senza alterare necessariamente le altre occorrenze della serie.

## Decisioni approvate

| Aspetto | Decisione |
| --- | --- |
| Rappresentazione | Occorrenze materializzate come normali eventi, collegate a una serie |
| Serie senza fine | Non supportate nella prima versione |
| Limite | Massimo 500 occorrenze per serie, configurabile lato backend |
| Frequenze | Giornaliera, settimanale, mensile per giorno del mese e annuale |
| Giorno 29/30/31 | I mesi privi del giorno richiesto vengono saltati |
| Timezone | Configurata per tenant; la serie conserva uno snapshot del valore risolto |
| Eccezioni | Una modifica singola protegge l'intera occorrenza dagli aggiornamenti del template |
| Ambiti di modifica | Singola occorrenza oppure da un'occorrenza in poi (azione dell'interfaccia); tutte le future solo via API |
| Eccezioni fuori dalla nuova regola | Restano visibili nella serie fino a gestione esplicita |
| Cancellazione serie | Cancella il futuro e conserva lo storico passato |
| Disponibilità | È autonoma per ogni occorrenza e può essere diversa tra eventi della stessa serie |
| Disponibilità bulk | Opzione facoltativa di compilazione iniziale; le risposte restano indipendenti |
| Presenze | Sono autonome per ogni occorrenza e possono essere diverse tra eventi della stessa serie |
| Persistenza della regola | Campi strutturati; RRULE e interoperabilità ICS rinviate |

## Fuori ambito per la prima versione

- importazione ed esportazione iCalendar/ICS;
- sincronizzazione bidirezionale con Google Calendar, Outlook o altri calendari;
- ricorrenze infinite;
- regole RFC 5545 arbitrarie inserite manualmente dall'utente;
- propagazione automatica delle presenze da un'occorrenza alle altre;
- conversione automatica degli eventi singoli esistenti in serie;
- modifica "questa e tutte le successive" tramite divisione della serie.

## Modello di partenza

Ogni record `calendar_event` rappresenta un evento con una sola data/ora di inizio e una sola data/ora di fine. Disponibilità, presenze, costi e promemoria fanno riferimento all'identificativo del singolo evento.

Per questo `calendar_event` resta l'entità della singola occorrenza. Una nuova entità descrive la serie e aggiunge identità di serie, regola di ricorrenza, data originaria dell'occorrenza, eccezioni e operazioni con ambito di serie.

## Terminologia

- **Serie**: configurazione comune e regola che generano più eventi.
- **Occorrenza**: uno specifico `calendar_event` appartenente a una serie.
- **Evento singolo**: `calendar_event` privo di serie, identico agli eventi attuali.
- **Template di serie**: valori predefiniti copiati nelle occorrenze, per esempio nome, luogo, stato, compenso e costi.
- **Eccezione**: occorrenza modificata autonomamente rispetto al template.
- **Data originaria**: istante che identifica la posizione dell'occorrenza nella regola, anche quando l'occorrenza viene successivamente spostata. La modifica da un'occorrenza in poi la riallinea alla nuova regola (vedi [Da un'occorrenza in poi](#da-unoccorrenza-in-poi)).
- **Evento futuro**: evento la cui data/ora di inizio non è precedente all'istante in cui viene eseguita l'operazione.

## Alternative architetturali

| Alternativa | Descrizione | Vantaggi | Svantaggi |
| --- | --- | --- | --- |
| Occorrenze virtuali | Si salva soltanto la regola e si calcolano gli eventi quando vengono richiesti | Pochi record, serie infinite semplici da rappresentare | Disponibilità, presenze, costi, promemoria, ID e paginazione diventano molto più complessi |
| Occorrenze materializzate | Ogni occorrenza è un normale `calendar_event`, collegato alla serie | Compatibile con quasi tutti i flussi attuali; ogni occorrenza ha un ID stabile | Richiede limiti di generazione e logica di riallineamento |
| Modello ibrido a finestra | Si materializzano solo i prossimi mesi e un job estende periodicamente la serie | Supporta ricorrenze senza fine limitando i record | Introduce scheduler, concorrenza, idempotenza e casi di errore aggiuntivi |

### Scelta approvata

Usare **occorrenze materializzate e serie finite** nella prima versione.

Questa scelta conserva il modello operativo esistente: disponibilità, presenze, costi e promemoria continuano a riferirsi a un normale `calendar_event`. Evita inoltre di introdurre un secondo processo pianificato multi-tenant soltanto per estendere serie senza fine.

Una ricorrenza senza fine potrà essere aggiunta in seguito adottando una finestra mobile, senza cambiare l'identità delle occorrenze già materializzate.

## Regole funzionali

### Frequenze

La prima versione propone:

- giornaliera;
- settimanale, su uno o più giorni della settimana;
- mensile, nello stesso giorno del mese;
- annuale, nello stesso giorno e mese.

Ogni frequenza accetta un intervallo intero compreso tra 1 e 366. Esempi:

- ogni giorno;
- ogni 2 settimane il lunedì e il giovedì;
- ogni 3 mesi il giorno 15;
- ogni anno.

Non sono inizialmente comprese regole come "il secondo martedì del mese", "l'ultimo giorno lavorativo" o combinazioni libere di regole RFC 5545.

### Termine della serie

Ogni serie deve terminare:

- dopo un numero di occorrenze; oppure
- a una data inclusiva.

La prima occorrenza è compresa nel conteggio. Non è ammessa una serie priva di termine nella prima versione.

Il limite è di massimo 500 occorrenze per serie. Deve essere configurabile lato backend e validato prima di scrivere qualsiasi record.

### Durata

La durata è calcolata dalla differenza tra fine e inizio dell'evento usato per creare la serie e viene applicata a ogni occorrenza.

La data/ora di fine deve essere successiva alla data/ora di inizio. Il comportamento attuale che assegna automaticamente un'ora quando la fine non è presente può essere mantenuto anche nella creazione della serie.

### Fuso orario e ora legale

La ricorrenza deve essere calcolata nel fuso orario IANA configurato per il tenant, per esempio `Europe/Rome`, e non aggiungendo un numero fisso di secondi all'istante precedente.

Una ricorrenza settimanale impostata alle 20:00 deve quindi rimanere alle 20:00 anche dopo il passaggio tra ora solare e ora legale.

La configurazione deve rispettare queste regole:

- aggiungere `time_zone` al tenant nello schema `public`;
- valorizzare i tenant esistenti con `Europe/Rome` durante la migrazione;
- usare `Europe/Rome` come valore fisso per i nuovi tenant creati senza fuso;
- se il fuso del tenant è nullo o non valido, ripiegare su `application.notification-preferences.default-time-zone` (default `Europe/Rome`, `TenantTimeZoneService`);
- non consentire la scelta di una timezone diversa per la singola serie;
- copiare sulla serie il `time_zone` del tenant al momento della creazione, come snapshot;
- continuare a salvare `calendar_event.start_date` ed `end_date` come `TIMESTAMPTZ`;
- generare gli istanti tramite `ZonedDateTime` nel fuso della serie.

Un successivo cambio della timezone del tenant si applica alle nuove serie, ma non sposta retroattivamente le serie già create. Un eventuale riallineamento di una serie esistente deve essere un'operazione amministrativa futura ed esplicita.

Per orari locali inesistenti o ambigui durante il cambio dell'ora si applicano le regole standard della timezone Java. Questo caso deve essere coperto da test espliciti.

### Giorni mensili non esistenti

Per una serie mensile iniziata il 29, 30 o 31, vengono **saltati i mesi che non contengono quel giorno**, coerentemente con la semantica usuale delle regole di calendario.

L'alternativa è spostare l'occorrenza all'ultimo giorno del mese. Se richiesta, deve essere presentata come modalità distinta e salvata esplicitamente, perché i due comportamenti non sono equivalenti.

## Modello dati

### Estensione di `tenant`

La tabella `public.tenant` riceve il campo `time_zone VARCHAR(64) NOT NULL`. Il valore deve essere un identificatore IANA valido ed è modificabile attraverso la gestione del tenant.

Il cambio del valore non altera automaticamente date, orari o snapshot delle serie esistenti.

Il campo viene esposto nei DTO e nel dettaglio di gestione del tenant, mantenendo le autorizzazioni già previste per tale gestione. Non vengono concessi nuovi permessi soltanto per la modifica della timezone.

### Tabella `calendar_event_series`

La serie è autorevole per la regola e per il template applicato alle nuove occorrenze.

| Campo | Tipo indicativo | Vincoli e significato |
| --- | --- | --- |
| `id` | `BIGINT` | PK |
| campi audit esistenti | come le altre entità tenant | inclusi `deleted` ed `entity_version` |
| `name` | `VARCHAR(255)` | obbligatorio |
| `description` | `TEXT` | facoltativo |
| `state` | `VARCHAR(32)` | stesso enum degli eventi |
| `location` | `VARCHAR(1000)` | facoltativo |
| `fee` | `NUMERIC(19,4)` | facoltativo; scritto solo con la funzionalità tenant `FINANCE` |
| `reminder_minutes` | `INTEGER` | stessa semantica dell'evento, da 0 a 1440 |
| `time_zone` | `VARCHAR(64)` | snapshot obbligatorio della timezone IANA del tenant |
| `first_start_local` | `TIMESTAMP WITHOUT TIME ZONE` | data/ora locale della prima occorrenza |
| `duration_minutes` | `INTEGER` | maggiore di zero |
| `frequency` | `VARCHAR(16)` | `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY` |
| `interval_value` | `INTEGER` | maggiore di zero |
| `week_days` | `VARCHAR(32)` | codici canonici separati da virgola; obbligatori per `WEEKLY`, salvati se presenti anche per le altre frequenze (il frontend li invia solo per `WEEKLY`) |
| `end_type` | `VARCHAR(16)` | `COUNT` oppure `UNTIL` |
| `occurrence_count` | `INTEGER` | valorizzato solo con `COUNT` |
| `until_local_date` | `DATE` | valorizzato solo con `UNTIL`, inclusivo |

La regola viene salvata in forma strutturata perché la prima versione espone soltanto un sottoinsieme controllato di ricorrenze. Se in futuro sarà necessaria interoperabilità ICS, i campi potranno essere convertiti in una RRULE canonica oppure affiancati da una rappresentazione RFC 5545 versionata.

### Tabella `calendar_event_series_cost`

Contiene il template delle voci di costo della serie:

- `id`;
- `series_id`;
- `description`;
- `amount`;
- `display_order`;
- campi audit e cancellazione logica coerenti con `calendar_event_cost`.

Ogni occorrenza riceve copie indipendenti in `calendar_event_cost`, così la modifica economica di una singola data non altera le altre. Compenso e costi della serie e delle occorrenze vengono scritti solo con la funzionalità `FINANCE` attiva; senza di essa il template restituito non li contiene.

### Estensioni di `calendar_event`

| Campo | Tipo indicativo | Significato |
| --- | --- | --- |
| `series_id` | `BIGINT`, nullable | FK alla serie; `NULL` per gli eventi singoli |
| `original_start_date` | `TIMESTAMPTZ`, nullable | posizione dell'occorrenza nella regola; riallineata dalla modifica da un'occorrenza in poi |
| `series_sequence` | `INTEGER`, nullable | posizione a partire da 1 |
| `series_exception` | `BOOLEAN NOT NULL DEFAULT FALSE` | indica una modifica autonoma dell'occorrenza |
| `series_excluded` | `BOOLEAN NOT NULL DEFAULT FALSE` | tombstone esplicito che impedisce la rigenerazione dopo la cancellazione utente |

Vincoli:

- tutti i campi di ricorrenza dell'occorrenza sono null quando `series_id` è null;
- `original_start_date` e `series_sequence` sono obbligatori quando `series_id` non è null;
- unicità di `(series_id, original_start_date)` anche per i record cancellati logicamente;
- indice di `(series_id, series_sequence)` per ordinamento e accesso efficiente;
- indici su `(series_id, deleted, start_date)` e sull'indice temporale già esistente.

Mantenere l'unicità anche sui record cancellati fa sì che una singola occorrenza cancellata operi come tombstone e non venga ricreata accidentalmente durante un riallineamento della serie. Il ripristino esplicito via API (`POST .../occurrences/{eventId}/restore`) riattiva invece anche un'occorrenza cancellata o con `series_excluded = true`; l'interfaccia lo offre solo per le eccezioni visibili.

## Creazione di una serie

Il backend deve:

1. validare template, fuso, regola e termine;
2. calcolare tutte le date locali;
3. rifiutare la richiesta se il limite massimo viene superato;
4. convertire ogni data locale in un istante usando il fuso della serie;
5. creare serie, costi template, eventi e copie dei costi in un'unica transazione;
6. restituire il riepilogo della serie e il numero di occorrenze create.

Non devono rimanere serie o occorrenze parziali se una singola generazione fallisce.

### Anteprima

L'endpoint di anteprima non persiste nulla e restituisce fuso usato, numero totale di occorrenze, prime 20 date generate e ultima occorrenza. Il dialog di creazione e il pannello della serie nel dettaglio mostrano numero di occorrenze, ultima data e fuso.

L'anteprima e la creazione usano lo stesso generatore backend (`RecurringEventGenerator`), evitando che frontend e backend interpretino diversamente la regola.

## Modifica

### Evento singolo

Le API e l'interfaccia attuali restano invariate.

### Singola occorrenza

La modifica agisce soltanto sul `calendar_event` selezionato e imposta `series_exception = true`.

Disponibilità e presenze esistenti restano associate allo stesso ID. Se cambiano data/ora o promemoria, tutti i promemoria pendenti dell'occorrenza devono essere ricalcolati.

### Regole comuni alla modifica della serie

`PATCH /api/calendar-event-series/{id}` aggiorna template e regola della serie. Le due modalità, descritte sotto, condividono queste regole:

- un'occorrenza marcata come eccezione non riceve le modifiche al template;
- il pannello della serie nel dettaglio mostra il numero di eccezioni attive;
- l'azione "Ripristina dalla serie" rimuove l'eccezione e riallinea l'occorrenza;
- le occorrenze aggiornate ricalcolano i promemoria pendenti degli utenti disponibili;
- le occorrenze non eccezionali non più generate vengono cancellate logicamente;
- le occorrenze cancellate da un riallineamento precedente vengono riattivate se la regola torna a generarle;
- un tombstone cancellato dall'utente (`series_excluded = true`) non viene riutilizzato.

Questo comportamento è più semplice e prevedibile di un sistema di override diverso per ogni campo.

Le eccezioni che restano fuori dalla nuova regola rimangono visibili come occorrenze eccezionali della serie fino a cancellazione esplicita, e la risposta del `PATCH` ne riporta il numero in `outsideRuleExceptionCount`: l'interfaccia lo mostra nel riepilogo del salvataggio. Prima di salvare, il dettaglio avvisa con il numero di eccezioni che non verranno aggiornate e chiede conferma.

### Da un'occorrenza in poi

È l'unica modifica di serie offerta dall'interfaccia: l'azione "Salva da questo evento in poi" nel dettaglio di un'occorrenza. Il `PATCH` contiene `sourceOccurrenceId`, l'identificativo dell'occorrenza aperta.

Il frontend compone il template dai campi dell'evento. Calcola l'inizio della serie spostando l'inizio del template dello stesso scarto che l'occorrenza ha rispetto alla propria `originalStartDate` e conserva la durata (`propagatedSeriesDates`).

Il backend:

1. abbina le occorrenze per `series_sequence`, dalla posizione della sorgente in poi;
2. aggiorna le occorrenze non eccezionali di quelle posizioni, anche se passate;
3. aggiorna sempre la sorgente, che perde il contrassegno di eccezione;
4. crea le posizioni mancanti e cancella logicamente quelle oltre il nuovo termine;
5. assegna a ogni posizione, dalla sorgente in poi, l'inizio dato dalla nuova regola come `original_start_date`;
6. libera la data delle posizioni non più generate e delle righe duplicate di una posizione con un valore parcheggiato;
7. risponde 409 `calendar.series.occurrence.conflict` se la nuova data coincide con quella di un'altra occorrenza della serie.

L'aggiornamento delle occorrenze passate dalla sorgente in poi e la perdita del contrassegno di eccezione della sorgente sono scelte intenzionali. La serie non viene divisa: tutte le occorrenze restano collegate alla stessa serie.

### Tutte le occorrenze future (solo API)

Senza `sourceOccurrenceId` il `PATCH` opera sulle sole occorrenze con inizio non precedente all'istante della richiesta. Le occorrenze passate non vengono riscritte, per preservare lo storico.

Il riallineamento:

1. calcola il nuovo insieme di `original_start_date`;
2. conserva gli eventi futuri che hanno la stessa identità originaria;
3. crea le nuove identità mancanti;
4. cancella logicamente le identità non più previste;
5. conserva le eccezioni;
6. non riutilizza un tombstone cancellato dall'utente.

## Cancellazione

### Singola occorrenza

- cancellazione logica del `calendar_event`;
- cancellazione logica o annullamento dei dati figli secondo le regole esistenti;
- eliminazione dei promemoria pendenti;
- conservazione del record come tombstone;
- nessuna modifica alla regola della serie.

### Serie futura

La cancellazione della serie deve cancellare logicamente tutte le occorrenze non ancora iniziate e impedire nuove generazioni. Le occorrenze passate restano consultabili come storico e mantengono il riferimento alla serie cancellata.

Questa semantica protegge presenze e dati economici storici. Un'eventuale azione futura di eliminazione completa, comprese le occorrenze passate, deve essere distinta e riservata a un caso amministrativo esplicito.

## Disponibilità e presenze

### Disponibilità

La disponibilità è registrata per singola occorrenza. La risposta data per un evento non modifica né vincola la risposta relativa agli altri eventi della stessa serie.

Per evitare che un utente debba rispondere manualmente a decine di eventi, è disponibile come opzione facoltativa un'operazione bulk con due ambiti:

- solo questa occorrenza;
- tutte le occorrenze future visibili della serie.

L'operazione bulk crea o aggiorna una risposta distinta su ciascun evento. È soltanto una compilazione iniziale multipla: dopo l'operazione ogni risposta resta indipendente e può essere modificata o annullata sulla singola occorrenza senza propagazione. Questo mantiene compatibili query, conteggi e promemoria attuali.

Nel dettaglio la casella "Applica anche a tutte le occorrenze future della serie" invia l'operazione bulk senza conferma preventiva. Un messaggio finale riporta il numero di occorrenze coinvolte.

### Presenze

Le presenze restano sempre specifiche dell'occorrenza, possono variare liberamente tra eventi della stessa serie e non possono essere propagate in massa.

## Promemoria

Ogni promemoria continua a riferirsi all'ID della singola occorrenza.

Devono essere aggiunte le seguenti garanzie, valide anche per gli eventi non ricorrenti:

- modifica di `startDate` o `reminderMinutes`: cancellare e rigenerare i promemoria pendenti degli utenti disponibili;
- modifica del nome: aggiornare il testo snapshot del promemoria pendente;
- cancellazione di occorrenza o serie: eliminare i promemoria pendenti coinvolti;
- disponibilità bulk: pianificare o cancellare il promemoria per ogni occorrenza coinvolta;
- operazioni massive idempotenti, con un solo promemoria pendente per coppia evento/utente.

Il vincolo di unicità per il promemoria pendente deve essere garantito a livello di database o mediante una strategia equivalente resistente alla concorrenza.

## Stato, visibilità e autorizzazioni

| Operazione | Super Admin | Admin | Utente | Utente esterno | Tesoriere |
| --- | ---: | ---: | ---: | ---: | ---: |
| Creare/modificare/cancellare serie | sì | sì | no | no | no |
| Vedere occorrenze consentite dallo stato | sì | sì | sì | solo `PUBLIC` | solo il riepilogo economico, tramite `/api/finance/events` |
| Modificare disponibilità propria | sì | sì | sì | sì | no |
| Disponibilità bulk propria | sì | sì | sì | sì | no |
| Gestire presenze | sì | sì | no | no | no |
| Modificare compenso/costi | sì | sì | no | no | sì, con `PATCH /api/finance/events/{id}/budget` |

L'archivista non gestisce le serie, ma usa le API amministrative degli eventi: legge anche le occorrenze `DRAFT` e può usare la disponibilità bulk amministrativa per sé.

La serie non deve diventare un modo per aggirare il mascheramento di compenso e costi applicato agli endpoint utente ed esterno.

Ogni occorrenza conserva il proprio stato. Una modifica dello stato della serie aggiorna le occorrenze non eccezionali dell'ambito scelto: future nella modalità API, dalla sorgente in poi nella modalità dell'interfaccia. Le query utente continuano a filtrare le occorrenze con le regole attuali.

## API

Gli endpoint esistenti degli eventi singoli devono rimanere compatibili.

### Gestione amministrativa della serie

- `POST /api/calendar-event-series/preview`
- `POST /api/calendar-event-series`
- `GET /api/calendar-event-series/{id}`
- `PATCH /api/calendar-event-series/{id}`
- `DELETE /api/calendar-event-series/{id}`
- `POST /api/calendar-event-series/{id}/occurrences/{eventId}/restore`

Il `POST` riceve template, costi e regola strutturata. Il `PATCH` riceve template e regola completi, `entityVersion` (se diversa risponde 409 `version.conflict`) e, facoltativo, `sourceOccurrenceId` (400 `calendar.series.sourceOccurrence.invalid` se l'occorrenza non appartiene alla serie). La risposta contiene `createdCount`, `updatedCount`, `deletedCount`, `keptExceptionCount` (occorrenze lasciate come sono perché eccezioni), `skippedPastCount` (posizioni già passate, non riscritte), `outsideRuleExceptionCount` (eccezioni che la nuova regola non genera più), il numero di occorrenze attive (`occurrenceCount`) e di eccezioni attive (`exceptionCount`). Una regola che supera il limite risponde `400` con `calendar.series.recurrence.tooManyOccurrences` e il limite come parametro, distinto dalla regola non valida.

### Operazioni sulla singola occorrenza

Le chiamate `PUT/PATCH /api/calendar-events/{id}` mantengono il significato di modifica della singola occorrenza. Quando l'evento appartiene a una serie, il backend imposta l'indicatore di eccezione.

`DELETE /api/calendar-events/{id}` continua a cancellare il singolo evento. La cancellazione dell'intera parte futura passa invece dall'endpoint della serie.

### Disponibilità bulk

Per Admin:

- `PATCH /api/calendar-events/series/{seriesId}/availability?available=true|false`, imposta la disponibilità sulle occorrenze della serie;
- `PATCH /api/calendar-events/series/{seriesId}/availability/cancel`, azzera la risposta riportando quelle occorrenze a «nessuna risposta».

Le operazioni toccano solo le occorrenze future (inizio non precedente all'istante della richiesta), non cancellate e visibili al ruolo. Le API amministrative sono aperte anche all'archivista e non filtrano per stato. Gli equivalenti sono esposti sotto `/api/user/calendar-events/series/{seriesId}/availability` e
`/api/external/calendar-events/series/{seriesId}/availability`, applicando gli stessi filtri di visibilità già presenti. La risposta riporta il numero di occorrenze coinvolte (`affectedOccurrences`).

Il verbo `DELETE` non è usato per queste operazioni: la disponibilità è un valore a tre stati — disponibile, non disponibile, nessuna risposta — e azzerarla è una transizione di stato, non una cancellazione. `DELETE` resta riservato alla cancellazione logica.

In alternativa, per ridurre la duplicazione dei controller, può essere introdotto un servizio condiviso con facciate autorizzative separate, coerente con l'architettura corrente.

## DTO indicativi

### Richiesta di creazione serie

```json
{
  "template": {
    "name": "Prova settimanale",
    "description": "Prova orchestra",
    "state": "PUBLIC",
    "startDate": "2026-09-07T20:00:00+02:00",
    "endDate": "2026-09-07T22:00:00+02:00",
    "location": "Sala prove",
    "fee": 0,
    "reminderMinutes": 60,
    "costs": []
  },
  "recurrence": {
    "frequency": "WEEKLY",
    "interval": 1,
    "weekDays": ["MO"],
    "end": {
      "type": "COUNT",
      "count": 12
    }
  }
}
```

La timezone non viene accettata dalla richiesta: il backend la ricava dal tenant corrente, con il ripiego sul fuso predefinito se nulla o non valida, e la salva come snapshot sulla serie. Le risposte di anteprima e creazione espongono la timezone effettivamente utilizzata.

### Estensione del DTO evento

```json
{
  "id": 123,
  "seriesId": 10,
  "seriesSequence": 4,
  "originalStartDate": "2026-09-28T18:00:00Z",
  "seriesException": false
}
```

I campi sono null o assenti per gli eventi singoli.

## Interfaccia utente

### Creazione

Nel dialog di creazione:

- selezione `Evento singolo` / `Evento ricorrente`;
- frequenza;
- intervallo;
- giorni della settimana quando necessario;
- termine per data o numero (da 1 a 500 occorrenze);
- anteprima con numero di occorrenze, ultima data e fuso.

Oltre il limite il backend risponde con l'errore generico `calendar.series.recurrence.invalid`.

### Calendario e lista

- icona di ricorrenza sulle occorrenze appartenenti a una serie;
- la stessa icona per le eccezioni, con il tooltip «Eccezione della serie» al posto di «Evento ricorrente»;
- nessun raggruppamento obbligatorio: la paginazione continua a operare sulle occorrenze;
- eventuale filtro per `seriesId` in una fase successiva.

### Dettaglio

Il dettaglio mostra:

- appartenenza alla serie, con il tag «Evento ricorrente» o «Eccezione della serie»;
- descrizione della ricorrenza con fuso, occorrenze attive ed eccezioni;
- sezione "Modifica pianificazione della serie" con regola e anteprima;
- azioni "Salva solo questo evento" e "Salva da questo evento in poi";
- azione "Ripristina dalla serie" per un'eccezione;
- scelta esplicita dell'ambito prima di una cancellazione ("Elimina evento" o "Elimina serie futura").

Il numero progressivo dell'occorrenza non viene mostrato.

## Compatibilità e migrazione

- Tutti gli eventi esistenti restano eventi singoli con `series_id = NULL`.
- Non è necessaria una migrazione dei dati applicativi esistenti.
- La migrazione dello schema `public` aggiunge `tenant.time_zone` e valorizza i tenant esistenti con `Europe/Rome`; deve essere inclusa nel `master.xml` pubblico.
- Gli endpoint attuali continuano a restituire le occorrenze come normali eventi.
- I nuovi campi del DTO sono additivi.
- Le query mensili esistenti continuano a funzionare perché le occorrenze sono materializzate.
- La migrazione delle serie e delle occorrenze deve essere aggiunta al `tenant-master.xml`, perché tali dati appartengono allo schema del tenant.

## Concorrenza e atomicità

- Creazione e riallineamento della serie sono transazionali.
- `entity_version` applica optimistic locking alla serie.
- La modifica della serie deve bloccare o versionare coerentemente le occorrenze future coinvolte.
- I vincoli univoci impediscono duplicazioni di occorrenze nella stessa serie.
- La creazione non è idempotente: una richiesta ripetuta dopo timeout crea una seconda serie.
- Le operazioni massive restituiscono un riepilogo e non un generico `204`.

## Validazioni

- nome e inizio obbligatori;
- fine successiva all'inizio;
- timezone IANA del tenant valida, altrimenti fuso predefinito;
- intervallo tra 1 e 366;
- promemoria tra 0 e 1440 minuti;
- almeno un giorno per frequenza settimanale;
- il giorno iniziale deve essere coerente con i giorni settimanali selezionati;
- esattamente un termine tra numero e data;
- numero positivo e non superiore al limite;
- data termine non precedente alla prima data;
- almeno una e non più del massimo numero di occorrenze generate;
- nessuna data duplicata;
- importi e costi con le stesse regole dell'evento singolo;
- `seriesId` non impostabile liberamente tramite il normale DTO evento.

## Osservabilità

`CalendarSeriesMetrics` raccoglie, con tag a cardinalità nota e senza identificativi:

- `taurus.calendar.series.operations{operation}`: serie create, modificate e cancellate;
- `taurus.calendar.series.occurrences{outcome}`: occorrenze create, aggiornate, cancellate, lasciate come eccezioni, saltate perché passate o fuori dalla nuova regola;
- `taurus.calendar.series.bulk.occurrences{operation}` e `taurus.calendar.series.bulk.duration{operation}`: dimensione e durata delle operazioni massive;
- `taurus.calendar.series.failures{reason}`: limite superato, regola non valida, conflitto di occorrenza, conflitto di versione;
- `taurus.calendar.series.reminders{action}`: promemoria ripianificati o cancellati;
- `taurus.calendar.series.availability.occurrences`: occorrenze toccate da una disponibilità massiva.

I log strutturati `calendar_series_created`, `calendar_series_updated` e `calendar_series_deleted` riportano identificativo della serie, ambito e conteggi. Nei log non finiscono descrizioni o altri contenuti non necessari.

## Test di accettazione principali

1. Creazione di un evento singolo invariata.
2. Serie giornaliera terminata per conteggio.
3. Serie settimanale su più giorni.
4. Serie mensile iniziata il 31 con mesi non validi saltati.
5. Serie annuale che attraversa un anno bisestile.
6. Serie che attraversa entrambi i cambi dell'ora in `Europe/Rome` mantenendo l'ora locale.
7. Rifiuto oltre il massimo di occorrenze senza dati parziali.
8. Modifica di una singola occorrenza e marcatura come eccezione.
9. Modifica del template senza sovrascrivere l'eccezione.
10. Ripristino dell'eccezione dalla serie.
11. Cambio della regola con conservazione degli ID compatibili.
12. Cancellazione singola e mancata rigenerazione del tombstone.
13. Cancellazione della serie futura con storico passato conservato.
14. Disponibilità singola e bulk, incluse le autorizzazioni utente/esterno.
15. Presenze indipendenti per occorrenza.
16. Copia indipendente di compenso e costi.
17. Ripianificazione dei promemoria dopo cambio orario.
18. Assenza di promemoria duplicati dopo retry concorrenti.
19. Visibilità `COMPLETE`/`PUBLIC` per utente e solo `PUBLIC` per utente esterno.
20. Isolamento completo tra schemi tenant.
21. Creazione di serie in tenant con timezone differenti, mantenendo la rispettiva ora locale.
22. Cambio della timezone del tenant senza modifica delle serie già esistenti.

## Fasi di sviluppo ipotizzate

1. Migrazione della timezone tenant e modello della serie.
2. Generatore puro di ricorrenze con test timezone e calendario.
3. Creazione e anteprima API.
4. Estensione DTO e visualizzazione delle occorrenze.
5. Modifica, eccezioni, ripristino e riallineamento.
6. Cancellazione singola e futura.
7. Disponibilità bulk e integrazione promemoria.
8. Form frontend e indicatori nel calendario.
9. Test di integrazione multi-tenant, autorizzativi e di concorrenza.

## Punti di attenzione

- La modifica di un evento, anche singolo, ripianifica i promemoria pendenti degli utenti disponibili (`CalendarEventsServiceImpl.update`).
- I controller Admin, Utente e Utente esterno espongono percorsi separati: le operazioni di disponibilità bulk devono mantenere questa separazione autorizzativa o centralizzarla senza allargare i permessi.
- La cancellazione logica corrente propaga ai figli dell'evento; una serie cancellata deve invece preservare le occorrenze storiche secondo la semantica approvata.
- Il calendario mensile carica un massimo fisso di eventi: serie numerose possono richiedere una paginazione completa o un endpoint temporale non paginato con limite controllato.
- La gestione economica prevista considera compenso e costi a livello di evento: materializzare le occorrenze mantiene questa semantica, ma moltiplica correttamente il preventivo per ogni data.

## Requisiti aperti

- Idempotenza della creazione: chiave di idempotenza o conflitto riconoscibile per una richiesta ripetuta dopo timeout ([Concorrenza e atomicità](#concorrenza-e-atomicità)).
- Riepilogo testuale della regola, anteprima delle date ed evidenza dei mesi saltati nel form ([Anteprima](#anteprima), [Creazione](#creazione)).
- Indicatore visivo distinto per le eccezioni nel calendario e nella lista ([Calendario e lista](#calendario-e-lista)).
- Test di integrazione multi-tenant, autorizzativi e di concorrenza ([Fasi di sviluppo ipotizzate](#fasi-di-sviluppo-ipotizzate), [Test di accettazione principali](#test-di-accettazione-principali)).
