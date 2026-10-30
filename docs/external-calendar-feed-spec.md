# Feed calendario esterno in sola lettura

La funzionalità estende il calendario esistente con un feed iCalendar/ICS sottoscrivibile da Google Calendar, Apple Calendar, Outlook e altri client compatibili. Le regole di visibilità degli eventi restano quelle definite in [Eventi ricorrenti del calendario](recurring-calendar-events-spec.md); questa specifica sostituisce esclusivamente il rinvio relativo all'esportazione ICS, non introduce sincronizzazione bidirezionale.

## Stato del documento

ID catalogo: `external-calendar-feed`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

## Obiettivo

Permettere a un utente di vedere nel proprio calendario esterno gli eventi Taurus che può già consultare, senza copiarli manualmente e senza concedere al provider esterno capacità di modifica.

La prima versione deve:

- pubblicare un URL HTTPS stabile che restituisce un calendario ICS aggiornato;
- supportare feed personali e feed condivisi del tenant;
- applicare a ogni lettura lo stato corrente di tenant, utente, ruoli ed eventi;
- consentire rotazione e revoca immediate della credenziale contenuta nell'URL;
- mantenere stabile l'identità degli eventi tra aggiornamenti, rotazioni del feed e client diversi;
- preservare l'isolamento a schema PostgreSQL dei tenant;
- non esporre dati operativi, economici o personali non necessari.

Il feed è una vista derivata: non diventa una nuova sorgente autorevole del calendario.

## Decisioni principali

| Aspetto | Decisione |
| --- | --- |
| Formato | iCalendar conforme a RFC 5545, estensione `.ics` |
| Trasporto | HTTPS; `webcal://` è soltanto un collegamento di comodità opzionale |
| Autenticazione del download | token casuale nell'URL, trattato come credenziale bearer |
| Persistenza del token | digest SHA-256 nel registro; il valore originale è mostrato alla creazione o rotazione e conservato solo cifrato per i retry idempotenti |
| Feed personali | creati e revocati dal proprietario; visibilità ricalcolata a ogni richiesta |
| Feed tenant | creati da Super Admin o Admin, con ambito interno oppure pubblico |
| Eventi inclusi | occorrenze materializzate, una per `VEVENT` |
| Ricorrenze ICS | nessuna `RRULE` nella prima versione |
| Stati | `DRAFT` sempre escluso; `COMPLETE` solo interno; `PUBLIC` interno ed esterno |
| Dati sensibili | mai esportati disponibilità, presenze, compensi, costi, utenti, inventario o preparazione |
| Promemoria | nessun `VALARM`, per non duplicare le notifiche Taurus |
| Date | `DTSTART` e `DTEND` in UTC; il client le visualizza nel proprio fuso |
| Aggiornamenti | polling deciso dal client; Taurus non promette propagazione immediata |
| Cancellazioni | `VEVENT` con `STATUS:CANCELLED` conservato per 90 giorni |
| Cache | `ETag`, `Last-Modified` e risposte `304 Not Modified` |
| Limiti iniziali | massimo 3 feed personali attivi per utente e 10 feed tenant attivi |

## Ambito della prima versione

### Compreso

- creazione, elenco, rotazione e revoca di feed personali;
- creazione, elenco, rotazione e revoca di feed tenant;
- livelli di dettaglio `MINIMAL` e `STANDARD`;
- finestra temporale configurabile entro limiti amministrati;
- generazione condizionale HTTP con ETag;
- cancellazioni e restrizioni di visibilità propagate ai client tramite tombstone;
- istruzioni di sottoscrizione per Google Calendar, Apple Calendar e Outlook;
- metriche, audit e protezioni da abuso.

### Fuori ambito

- importazione di file ICS in Taurus;
- sincronizzazione bidirezionale;
- OAuth con Google, Microsoft o Apple;
- CalDAV;
- inviti iTIP/iMIP, RSVP e risposte di partecipazione;
- creazione o modifica di eventi Taurus dal calendario esterno;
- scelta di singole serie, categorie o eventi da includere;
- feed composto soltanto dagli eventi per cui l'utente si è dichiarato disponibile;
- allegati, spartiti, programma, inventario o documenti evento;
- promemoria `VALARM`;
- garanzia di un intervallo di aggiornamento imposto ai provider esterni.

## Tipi di feed e autorizzazioni

### Feed personale

Il feed personale appartiene a un solo `app_user` del tenant. Non è un feed amministrativo: espone soltanto il calendario partecipante.

L'ambito viene fissato alla creazione in base ai ruoli correnti e validato a ogni download usando l'utente nel database:

| Ruoli correnti del proprietario | Eventi inclusi |
| --- | --- |
| contiene `ROLE_USER` | ambito `INTERNAL`: `COMPLETE` e `PUBLIC` |
| non contiene `ROLE_USER`, ma contiene `ROLE_USER_EXTERNAL` | ambito `PUBLIC_ONLY`: solo `PUBLIC` |
| nessun ruolo partecipante, utente inattivo o cancellato | feed non disponibile |

La presenza dei soli ruoli `ROLE_SUPER_ADMIN`, `ROLE_ADMIN`, `ROLE_ARCHIVIST` o `ROLE_TREASURER` non abilita un feed personale. Un amministratore che possiede anche `ROLE_USER` riceve il normale ambito interno, mai gli eventi `DRAFT`.

Questa regola evita che un URL di lunga durata trasformi l'accesso amministrativo in una pubblicazione permanente. Il feed non cambia silenziosamente significato: se viene meno il ruolo richiesto dal suo ambito, viene revocato e dalla richiesta successiva restituisce `404`. L'utente può creare un nuovo feed con l'ambito consentito. La revoca proattiva avviene alla modifica dell'utente (`UsersServiceImpl.update`), alla sua cancellazione (`UsersServiceImpl.delete` e `DataErasureService`, tramite `CalendarFeedOwnerRevocationService`) e alla disattivazione o cancellazione del tenant (`TenantsServiceImpl`, tramite `CalendarFeedTenantRevocationService`, che chiude prima le righe globali e poi le sottoscrizioni dello schema). Resta comunque il controllo a ogni download: utente e ruoli vengono riletti e una credenziale non più ammessa riceve `404`.

### Feed tenant

Un Super Admin o Admin può pubblicare un feed condiviso con uno dei seguenti ambiti:

| Ambito | Eventi inclusi | Uso previsto |
| --- | --- | --- |
| `INTERNAL` | `COMPLETE`, `PUBLIC` | calendario condiviso con membri interni |
| `PUBLIC_ONLY` | solo `PUBLIC` | calendario destinato a collaboratori o pubblico selezionato |

Entrambi restano protetti da URL segreto. `PUBLIC_ONLY` descrive la visibilità degli eventi, non rende l'URL indicizzabile né privo di credenziale. Gli eventi `DRAFT` non sono esportabili nemmeno da un amministratore.

Solo Super Admin e Admin possono gestire i feed tenant. L'elenco non restituisce mai il token originale. Un amministratore può revocare qualunque feed tenant e, per risposta a incidenti, qualunque feed personale del proprio tenant, ma non può recuperarne l'URL segreto.

## Livelli di dettaglio

Ogni feed salva un livello esplicito:

| Livello | Contenuto |
| --- | --- |
| `MINIMAL` | nome, inizio, fine e luogo |
| `STANDARD` | contenuto `MINIMAL`, descrizione in testo semplice e collegamento autenticato al dettaglio Taurus |

Il valore predefinito è `MINIMAL`. Il collegamento al dettaglio ha la forma `<public-base-url>/calendar/uid/<calendar_uid>` e non concede accesso: l'apertura continua a richiedere autenticazione e ruolo compatibile. La pagina risolve l'UID nell'evento (`GET /api/calendar-feeds/events/{uid}`, con le stesse regole di visibilità del feed) e sostituisce il proprio passaggio nella cronologia del browser; se l'evento non esiste più o non è visibile con il profilo che apre il link, mostra un messaggio invece di una pagina vuota.

Non vengono mai esportati, indipendentemente dal livello:

- ID numerici del database: l'`URL` del livello `STANDARD` contiene soltanto il `calendar_uid`, già esportato come `UID` (`CalendarFeedTokenResolver`);
- elenco utenti, disponibilità e presenze;
- compenso, costi, preventivo e movimenti;
- promemoria personali o di evento;
- dati di preparazione operativa;
- programma, spartiti e media;
- inventario e assegnazioni;
- campi audit o identificativi Keycloak;
- note interne di domini diversi dalla descrizione pubblicabile dell'evento.

La descrizione `STANDARD` viene convertita in testo semplice, privata di markup e caratteri di controllo, limitata a 4.000 caratteri e codificata secondo le regole iCalendar.

## Esperienza utente

### Gestione personale

Nelle impostazioni profilo compare la sezione **Calendario esterno**. Se l'utente possiede un ruolo partecipante può:

1. scegliere nome del feed, livello di dettaglio e finestra temporale;
2. creare il feed;
3. copiare l'URL HTTPS mostrato una sola volta;
4. leggere le istruzioni specifiche per il proprio client;
5. vedere metadati, stato e ultimo accesso approssimativo;
6. ruotare l'URL ("Genera nuovo link") oppure revocare il feed;
7. eliminare dall'elenco un feed revocato ("Elimina dall'elenco");
8. aprire la cronologia del feed, con creazione, rotazioni e revoche.

La sezione mostra sempre l'avviso **Il link viene mostrato una sola volta**: chi possiede il link può leggere gli eventi inclusi e, se lo si perde, occorre generarne uno nuovo. L'avviso invita inoltre a generare subito un nuovo link se il precedente è finito in una chat, in un messaggio inoltrato o in un calendario condiviso per errore: è l'unico modo per togliere l'accesso a chi lo ha ricevuto.

La rotazione richiede conferma perché interrompe la sottoscrizione esistente. Dopo la rotazione il nuovo URL è mostrato una sola volta e il precedente smette di funzionare.

### Gestione tenant

Nell'amministrazione del tenant compare **Feed calendario** con:

- nome riconoscibile, livello di dettaglio e fingerprint;
- ambito `INTERNAL` o `PUBLIC_ONLY`;
- finestra temporale;
- tipo (tenant o personale) e proprietario;
- creatore e data di creazione;
- stato e ultimo accesso approssimativo;
- azioni di cronologia, rotazione (solo feed tenant), revoca ed eliminazione dall'elenco dei feed revocati.

La cronologia mostra operazione, origine, versione e fingerprint del token, autore e momento; non mostra mai un valore segreto.

La UI distingue chiaramente **sottoscrizione** da **importazione**: importare un file produce una fotografia statica, mentre aggiungere l'URL come calendario sottoscritto consente gli aggiornamenti periodici.

### Istruzioni ai client

La UI fornisce il normale URL `https://.../calendar.ics`, sempre copiabile. Può inoltre offrire un pulsante `webcal://` per Apple Calendar, senza usarlo come URL canonico.

Le istruzioni devono comunicare che:

- Google Calendar richiede l'aggiunta da URL tramite interfaccia web desktop;
- Apple Calendar crea un calendario sottoscritto non modificabile;
- Outlook deve usare **Sottoscrivi dal Web**, non **Carica da file**;
- l'intervallo di aggiornamento è deciso dal provider e può richiedere molte ore;
- ruotare o revocare il link non elimina immediatamente copie già memorizzate dal provider.

## Modello dati

### Identità ICS dell'evento

La tabella tenant `calendar_event` riceve:

| Campo | Tipo indicativo | Regola |
| --- | --- | --- |
| `calendar_uid` | `UUID` | non nullo e univoco nello schema; generato una volta |
| `calendar_sequence` | `INTEGER` | non nullo, default `0`, mai decrescente |
| `calendar_feed_modified_at` | `TIMESTAMPTZ` | istante dell'ultima modifica visibile nel feed |

Il valore ICS è `UID:urn:uuid:<calendar_uid>`. Non contiene tenant, ID database, hostname, email o altri dati identificativi e rimane uguale:

- tra feed personali e tenant;
- dopo rotazione del token;
- dopo spostamento di data/ora;
- per un'occorrenza modificata autonomamente;
- dopo cancellazione e successivo ripristino dello stesso record.

`calendar_sequence` viene incrementato soltanto quando cambia la proiezione ICS: nome, descrizione, inizio, fine, luogo, stato, cancellazione o ripristino. Disponibilità, presenze, promemoria, costi e altri dati esclusi non lo modificano.

Le operazioni massive sulle serie applicano la stessa regola a ciascuna occorrenza interessata. Non viene introdotto un UID della serie perché la prima versione pubblica le occorrenze materializzate senza `RRULE`.

### Sottoscrizione nello schema tenant

Nuova tabella `calendar_feed_subscription`:

| Campo | Tipo indicativo | Regola |
| --- | --- | --- |
| `id` | `UUID` | PK, generato dal backend |
| `name` | `VARCHAR(120)` | obbligatorio |
| `feed_type` | `VARCHAR(16)` | `PERSONAL` o `TENANT` |
| `owner_user_id` | `BIGINT` | obbligatorio per `PERSONAL`, nullo per `TENANT` |
| `visibility_scope` | `VARCHAR(16)` | `INTERNAL` o `PUBLIC_ONLY`, anche per i personali |
| `detail_level` | `VARCHAR(16)` | `MINIMAL` o `STANDARD` |
| `past_days` | `INTEGER` | da 0 a 365, default 90 |
| `future_months` | `INTEGER` | da 1 a 36, default 18 |
| `status` | `VARCHAR(16)` | `ACTIVE` o `REVOKED` |
| `token_version` | `INTEGER` | incrementato a ogni rotazione |
| `token_fingerprint` | `VARCHAR(12)` | prefisso del digest, solo identificazione operativa |
| `last_accessed_at` | `TIMESTAMPTZ` | facoltativo e aggiornato al massimo una volta al giorno |
| `insert_by`, `insert_date`, `edit_by`, `edit_date` | campi audit standard tenant | creatore e ultima modifica |
| `deleted` | `BOOLEAN` | cancellazione logica di un feed revocato |
| `entity_version` | `BIGINT` | concorrenza ottimistica |

Vincoli e indici:

- FK `owner_user_id -> app_user.id` senza cancellazione a cascata distruttiva;
- indice su `(owner_user_id, status)`;
- indice su `(feed_type, status)`;
- indice su `(deleted, insert_date)` per l'elenco;
- massimo 3 feed `PERSONAL` attivi per proprietario e 10 feed `TENANT` attivi, verificati sotto advisory lock transazionale per proprietario o per tenant;
- un feed revocato non è riattivabile: si crea un nuovo token;
- un feed revocato può essere tolto dall'elenco con la cancellazione logica (`deleted = true`); un feed attivo va prima revocato.

Il digest completo del token non viene duplicato nello schema tenant. `token_fingerprint` non consente autenticazione e serve soltanto a distinguere feed in UI e log amministrativi.

### Ricevute di idempotenza

La tabella tenant `calendar_feed_idempotency` conserva l'esito di creazioni e rotazioni per i retry:

| Campo | Tipo | Regola |
| --- | --- | --- |
| `request_digest` | `BYTEA` | PK, SHA-256 di utente, operazione, feed e chiave di idempotenza |
| `subscription_id` | `UUID` | FK a `calendar_feed_subscription`, `ON DELETE CASCADE` |
| `request_fingerprint` | `BYTEA` | impronta del payload, per riconoscere il riuso della chiave |
| `token_version` | `INTEGER` | versione del token restituito |
| `token_ciphertext`, `token_nonce` | `BYTEA` | token cifrato con AES-GCM (`CalendarFeedIdempotencyCodec`) |
| `created_at` | `TIMESTAMPTZ` | indicizzato |

La chiave AES deriva dalla chiave di idempotenza del client, che non viene salvata; il digest della richiesta è il dato autenticato. Il retry restituisce lo stesso URL solo se il feed è attivo e la versione del token è ancora quella registrata. Le ricevute più vecchie di `application.calendar-feed.idempotency-retention-days` (default 30) vengono eliminate dallo stesso job che fa retention dei tombstone: oltre quella finestra nessun client ripete una creazione o una rotazione e il token cifrato non ha ragione di sopravvivere.

### Registro globale del token

Una richiesta anonima non contiene il claim JWT `tenant`; inoltre il token non deve codificare il nome dello schema. Per risolvere il tenant senza iterare tutti gli schemi viene aggiunta in `public` la tabella minimale `calendar_feed_token_registry`:

| Campo | Tipo indicativo | Regola |
| --- | --- | --- |
| `token_digest` | `BYTEA` | PK, SHA-256 dei 32 byte casuali |
| `subscription_id` | `UUID` | identificativo opaco del feed tenant |
| `tenant_id` | `BIGINT` | FK al tenant globale |
| `token_version` | `INTEGER` | deve coincidere con la sottoscrizione |
| `status` | `VARCHAR(16)` | `ACTIVE` o `REVOKED` |
| `created_at` | `TIMESTAMPTZ` | audit tecnico |
| `revoked_at` | `TIMESTAMPTZ` | facoltativo |

Il registro non contiene nome del feed, utente, eventi o altri dati personali. La risoluzione usa poi `tenant_schema_registry` e accetta soltanto tenant e schema `ACTIVE`.

Creazione, rotazione e revoca aggiornano tabella tenant e registro `public` nella stessa transazione PostgreSQL. La cancellazione GDPR del tenant revoca o elimina tutte le righe del registro prima del `DROP SCHEMA`; l'operazione deve essere idempotente e coperta da test di provisioning.

### Tombstone di pubblicazione

Nuova tabella tenant `calendar_event_feed_tombstone`:

| Campo | Tipo indicativo | Regola |
| --- | --- | --- |
| `id` | `BIGINT` | PK |
| `event_uid` | `UUID` | UID stabile dell'evento |
| `audience` | `VARCHAR(16)` | `INTERNAL` o `EXTERNAL` |
| `sequence` | `INTEGER` | sequenza successiva all'ultima versione visibile |
| `original_start_date` | `TIMESTAMPTZ` | data necessaria all'annullamento |
| `original_end_date` | `TIMESTAMPTZ` | fine precedente |
| `summary_snapshot` | `VARCHAR(255)` | nome precedente sanificato |
| `cancelled_at` | `TIMESTAMPTZ` | modifica che ha ristretto la visibilità |
| `expires_at` | `TIMESTAMPTZ` | `cancelled_at + 90 giorni` |

Si crea un tombstone quando un evento precedentemente visibile:

- viene cancellato logicamente o escluso da una serie;
- passa da `PUBLIC` a `COMPLETE`: tombstone `EXTERNAL`;
- passa da `PUBLIC` a `DRAFT`: tombstone `INTERNAL` ed `EXTERNAL`;
- passa da `COMPLETE` a `DRAFT`: tombstone `INTERNAL`.

La coppia `(event_uid, audience)` è univoca tra i tombstone attivi. Un ripristino elimina il tombstone per il pubblico nuovamente autorizzato e pubblica l'evento con una sequenza superiore. Un job multi-tenant elimina i tombstone scaduti.

Lo spostamento naturale di un evento fuori dalla finestra temporale del feed non è una cancellazione e non crea tombstone.

## Credenziale del feed

### Formato e generazione

Il token contiene 32 byte prodotti da `SecureRandom`, codificati Base64 URL-safe senza padding, per un valore di 43 caratteri. L'URL canonico è:

```text
https://<taurus-public-base-url>/api/calendar-subscriptions/v1/<token>/calendar.ics
```

Il token è opaco: non contiene tenant, feed, utente, timestamp o firma decodificabile. Prima della persistenza il backend calcola SHA-256 sui byte originali e memorizza il digest nel registro. Il valore originale è conservato solo cifrato nella [ricevuta di idempotenza](#ricevute-di-idempotenza).

Il valore originale e l'URL completo vengono restituiti esclusivamente dalla risposta di creazione o rotazione. Non compaiono in risposte di elenco, audit, eccezioni, log, tracing o metriche.

### Risoluzione di una richiesta

1. validare sintassi e lunghezza del token prima di interrogare il database;
2. calcolare il digest;
3. cercare una riga `ACTIVE` nel registro `public`;
4. verificare tenant e schema `ACTIVE`;
5. aprire esplicitamente il contesto tenant in un blocco con ripristino garantito;
6. caricare sottoscrizione e confrontare ID, versione, stato e fingerprint;
7. per un feed personale verificare utente non cancellato, attivo e ancora titolare del ruolo richiesto dall'ambito salvato;
8. calcolare l'ambito effettivo senza fidarsi di dati provenienti dall'URL;
9. leggere eventi e tombstone, generare la risposta e chiudere il contesto tenant.

Il codice di routing non riusa `TenantContextInterceptor`, che dipende dal JWT. Usa un servizio dedicato e garantisce il ripristino dello schema `public` anche in caso di eccezione.

Token inesistenti, revocati, di tenant disattivati o incoerenti restituiscono tutti `404 Not Found` con corpo vuoto. Non vengono distinti errori utili a enumerare credenziali. Un calendario valido senza eventi restituisce invece `200 OK` con un `VCALENDAR` vuoto.

### Rotazione e revoca

La rotazione:

1. genera un nuovo token;
2. incrementa `token_version`;
3. inserisce la nuova riga globale;
4. revoca la riga precedente;
5. aggiorna fingerprint e audit tenant;
6. restituisce il nuovo URL una sola volta.

Tutto avviene in una transazione. Il token precedente non è accettato dopo il commit. In caso di retry applicativo, il comando usa una chiave di idempotenza per non produrre più URL validi senza mostrarli al chiamante.

La revoca è definitiva. Le copie già scaricate da un provider non possono essere eliminate da Taurus; da quel momento, però, nessun nuovo download con quel token riesce.

## Proiezione iCalendar

### Calendario

Ogni risposta contiene almeno:

```text
BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//Zodiac Taurus//Calendar Feed 1.0//IT
CALSCALE:GREGORIAN
METHOD:PUBLISH
X-WR-CALNAME:Nome del feed
REFRESH-INTERVAL;VALUE=DURATION:PT6H
...
END:VCALENDAR
```

`REFRESH-INTERVAL` è un suggerimento minimo conforme a RFC 7986, non un impegno: i client possono ignorarlo. Il nome del feed viene sanificato e non contiene il nome completo del proprietario per impostazione predefinita.

### Evento attivo

Per ogni `calendar_event` incluso viene generato un singolo `VEVENT`:

```text
BEGIN:VEVENT
UID:urn:uuid:550e8400-e29b-41d4-a716-446655440000
DTSTAMP:20260903T101500Z
LAST-MODIFIED:20260903T101500Z
SEQUENCE:2
DTSTART:20261010T183000Z
DTEND:20261010T203000Z
SUMMARY:Concerto d'autunno
LOCATION:Teatro comunale
STATUS:CONFIRMED
TRANSP:OPAQUE
END:VEVENT
```

Con livello `STANDARD` sono aggiunti `DESCRIPTION` e `URL`. Tutti gli istanti sono convertiti in UTC e terminano con `Z`; non viene quindi emesso `VTIMEZONE`. La timezone del tenant continua a governare creazione e ricorrenza in Taurus, mentre il client visualizza l'istante nel proprio fuso.

Gli eventi senza `start_date`, con `end_date` non successiva all'inizio o altrimenti corrotti non vengono pubblicati silenziosamente: la generazione fallisce con errore operativo, metrica e log privo di token. I vincoli correnti dovrebbero rendere il caso impossibile.

### Evento cancellato

Un tombstone produce un componente minimale:

```text
BEGIN:VEVENT
UID:urn:uuid:550e8400-e29b-41d4-a716-446655440000
DTSTAMP:20260904T080000Z
LAST-MODIFIED:20260904T080000Z
SEQUENCE:3
DTSTART:20261010T183000Z
DTEND:20261010T203000Z
SUMMARY:Concerto d'autunno
STATUS:CANCELLED
END:VEVENT
```

Un feed interno legge i tombstone `INTERNAL`; un feed esterno o `PUBLIC_ONLY` legge quelli `EXTERNAL`. Se per lo stesso UID esiste nuovamente un evento attivo visibile con sequenza superiore, viene emesso soltanto l'evento attivo.

### Ricorrenze

Le serie Taurus possiedono già occorrenze materializzate con identità e modifiche autonome. Il feed pubblica pertanto ogni occorrenza come `VEVENT` indipendente:

- nessuna conversione della regola strutturata in `RRULE`;
- nessun `RECURRENCE-ID` nella prima versione;
- eccezioni e spostamenti sono già riflessi nei dati dell'occorrenza;
- cancellare una singola occorrenza produce il tombstone del suo UID;
- modificare tutte le occorrenze future incrementa la sequenza delle sole occorrenze la cui proiezione cambia.

Questa rappresentazione aumenta il numero di componenti, ma evita interpretazioni diverse della ricorrenza tra Taurus e i client esterni e conserva il limite già esistente di 500 occorrenze per serie.

### Serializzazione

`IcalendarRenderer` costruisce direttamente le content line. La libreria Java `ical4j` serve solo a rileggere e validare il calendario generato prima di restituirlo: un errore di parsing fa fallire la richiesta con `503`. Sono comunque obbligatori test sul risultato serializzato.

Il renderer deve:

- terminare ogni content line con `CRLF`;
- piegare le righe oltre 75 ottetti senza spezzare una sequenza UTF-8;
- eseguire escaping di backslash, virgola, punto e virgola e newline nei valori `TEXT`;
- eliminare caratteri di controllo non ammessi;
- produrre UTF-8 deterministico;
- validare il calendario generato rileggendolo nei test con un parser indipendente o con una seconda istanza del parser.

## Visibilità e finestra temporale

La query include eventi non cancellati e non esclusi che intersecano la finestra:

```text
event.endDate >= now - pastDays
AND event.startDate <= now + futureMonths
```

I default sono 90 giorni nel passato e 18 mesi nel futuro. Non si limita il feed ai soli eventi che iniziano nella finestra: un evento già iniziato ma ancora in corso deve comparire.

Le regole di stato vengono aggiunte nella stessa query SQL, non applicate dopo aver caricato risultati più ampi. Il repository dedicato non riusa un endpoint amministrativo e non carica disponibilità, presenze o costi.

Il limite tecnico predefinito è 10.000 componenti tra eventi e tombstone per risposta, configurabile. Il server non tronca mai silenziosamente il calendario: se il limite è superato restituisce `503 Service Unavailable` e richiede una finestra più piccola tramite gestione del feed.

## API

### Feed personali autenticati

Base path: `/api/calendar-feeds`.

| Metodo e percorso | Comportamento |
| --- | --- |
| `GET /api/calendar-feeds` | elenca i feed personali del chiamante senza token o URL |
| `POST /api/calendar-feeds` | crea un feed personale e restituisce l'URL una sola volta |
| `POST /api/calendar-feeds/{id}/rotate` | ruota il token del feed posseduto |
| `DELETE /api/calendar-feeds/{id}` | revoca il feed posseduto; idempotente |
| `DELETE /api/calendar-feeds/{id}/record` | cancella logicamente un feed posseduto già revocato; `409 calendarFeed.active` se è attivo |
| `GET /api/calendar-feeds/{id}/audit` | cronologia del feed posseduto: operazione, origine, versione e fingerprint del token, autore, motivo e momento |
| `GET /api/calendar-feeds/events/{uid}` | risolve il `calendar_uid` di un evento nel suo id, con le regole di visibilità del feed; `404` se non esiste o non è visibile |

Esempio di creazione:

```json
{
  "name": "Il mio calendario Taurus",
  "detailLevel": "MINIMAL",
  "pastDays": 90,
  "futureMonths": 18,
  "idempotencyKey": "1f888a38-9b65-41cc-9787-f75dd5897925"
}
```

La rotazione richiede anch'essa una chiave UUID v4 e accetta il corpo `{ "idempotencyKey": "..." }`. Il retry con la stessa chiave restituisce lo stesso URL finché quella versione del token è ancora corrente, altrimenti `409 calendarFeed.idempotency.stale`; il riuso della chiave con un payload diverso restituisce `409 calendarFeed.idempotency.conflict`.

Tutte le API di gestione richiedono la funzionalità tenant `EXTERNAL_CALENDAR_FEED`.

Risposta `201 Created`:

```json
{
  "id": "a630e67b-ef56-46fb-b356-1942fdcd4f02",
  "name": "Il mio calendario Taurus",
  "feedType": "PERSONAL",
  "detailLevel": "MINIMAL",
  "subscriptionUrl": "https://taurus.example/api/calendar-subscriptions/v1/<secret>/calendar.ics",
  "tokenShownOnce": true,
  "createdAt": "2026-09-03T10:15:00Z"
}
```

### Feed tenant autenticati

Base path: `/api/admin/calendar-feeds`.

| Metodo e percorso | Comportamento |
| --- | --- |
| `GET /api/admin/calendar-feeds` | elenca i feed tenant e, per incident response, i metadati dei personali |
| `POST /api/admin/calendar-feeds` | crea un feed `TENANT` |
| `POST /api/admin/calendar-feeds/{id}/rotate` | ruota un feed tenant |
| `DELETE /api/admin/calendar-feeds/{id}` | revoca un feed del tenant |
| `DELETE /api/admin/calendar-feeds/{id}/record` | cancella logicamente un feed revocato del tenant; `409 calendarFeed.active` se è attivo |
| `GET /api/admin/calendar-feeds/{id}/audit` | cronologia di un feed del tenant |

L'amministratore non può ruotare un feed personale perché riceverebbe la nuova credenziale dell'utente; può soltanto revocarlo. L'intera resource richiede `ROLE_ADMIN` o `ROLE_SUPER_ADMIN`.

### Download anonimo autenticato dal token

| Metodo e percorso | Comportamento |
| --- | --- |
| `GET /api/calendar-subscriptions/v1/{token}/calendar.ics` | restituisce il feed o `304` |
| `HEAD /api/calendar-subscriptions/v1/{token}/calendar.ics` | stessi header senza corpo |

Il matcher Spring Security per questo percorso deve essere `permitAll()` e comparire prima della regola generale `/api/**`. L'assenza di JWT è intenzionale; l'autenticazione applicativa avviene esclusivamente tramite digest del token e non conferisce una `Authentication` riutilizzabile da altri endpoint.

## Semantica HTTP

Una risposta valida usa:

```text
Content-Type: text/calendar; charset=utf-8
Content-Disposition: inline; filename="calendar.ics"
Cache-Control: private, max-age=300, must-revalidate
ETag: "<hash-della-rappresentazione>"
Last-Modified: <ultima modifica rilevante>
X-Content-Type-Options: nosniff
```

L'ETag è lo SHA-256 della rappresentazione generata, che riflette configurazione del feed, eventi visibili e tombstone. `If-None-Match` ha precedenza e restituisce `304 Not Modified` senza corpo quando è esattamente uguale all'ETag; elenchi di ETag e `*` non sono interpretati. `If-Modified-Since` è usato solo in assenza di `If-None-Match`.

Il server abilita compressione gzip quando richiesta. Non usa redirect, perché alcuni client non li seguono in modo uniforme e un redirect può copiare il token in log ulteriori.

Esiti:

| Stato | Caso |
| --- | --- |
| `200` | calendario valido, anche vuoto |
| `304` | rappresentazione invariata |
| `404` | token invalido/revocato o soggetto non più autorizzato |
| `429` | limite di richieste superato, con `Retry-After` |
| `503` | generazione temporaneamente impossibile o feed oltre limite |

Non vengono restituite pagine HTML né dettagli di eccezione sull'endpoint ICS.

## Sicurezza e privacy

L'URL è una credenziale a lunga durata. Le seguenti misure sono prerequisiti di rilascio:

- HTTPS obbligatorio e HSTS gestito dall'infrastruttura;
- esclusione completa del path tokenizzato dagli access log di applicazione, reverse proxy, WAF, APM e analytics, oppure sostituzione del segmento con `[REDACTED]`;
- nessun token come tag di metrica, breadcrumb, evento analytics o messaggio d'errore;
- nessuna inclusione dell'URL in email o notifiche automatiche;
- digest confrontati in modo costante dove applicabile;
- rate limit principale per digest, predefinito 120 richieste/ora, e soglia anti-abuso globale/IP molto più alta e configurabile per non penalizzare gli egress condivisi dei provider (`CalendarFeedRateLimiter`, finestre orarie in memoria per istanza); l'IP è l'indirizzo remoto della connessione, perché gli header di forward sono considerati solo nel profilo `dev`;
- `404` uniforme per credenziali non valide;
- blocco dei metodi diversi da `GET` e `HEAD` sul path pubblico;
- nessun cookie di sessione necessario per la lettura;
- validazione stretta di nome, descrizione, luogo e URL per prevenire injection ICS;
- URL pubblico costruito soltanto da una base URL di configurazione fidata, mai dall'header `Host` della richiesta;
- audit storico di creazione, rotazione, revoca ed eliminazione in `calendar_feed_audit`, con operazione, origine, versione e fingerprint del token, autore e motivo, mai il valore segreto; la stessa operazione produce un log `calendar_feed_credential` con l'autore in forma di hash;
- revoca automatica e proattiva quando proprietario o tenant vengono disattivati o cancellati, con il controllo a ogni download come seconda difesa;
- esclusione di `service.calendarfeed` e delle resource dei feed dal `LoggingAspect`, così argomenti e risultati non finiscono nei log DEBUG.

La procedura da seguire quando un link viene esposto — contenimento, revoca, verifica e verbale — è nel [Runbook operativo](runbook-operativo.md#158-revoca-dei-feed-calendario-per-incidente).

Il campo `last_accessed_at` è informativo: un accesso può appartenere al provider e non dimostra che l'utente abbia aperto il calendario. Per evitare una scrittura a ogni polling viene aggiornato al massimo una volta ogni 24 ore, in una transazione propria dopo la generazione del calendario.

## Concorrenza e consistenza

- Rotazione e revoca bloccano la riga della sottoscrizione (`SELECT ... FOR UPDATE`); la creazione prende advisory lock sulla chiave di idempotenza e sul limite.
- Due rotazioni concorrenti vengono serializzate: con chiavi diverse riescono entrambe e resta accettato solo l'ultimo token; con la stessa chiave la seconda riceve la risposta idempotente già registrata.
- Generazione e revoca concorrenti possono completare una risposta già autenticata prima del commit di revoca; ogni nuova richiesta successiva al commit fallisce. Questo è il limite transazionale dichiarato.
- La query legge eventi e tombstone in una singola transazione tenant di sola lettura.
- La modifica di un evento, l'incremento della sequenza e la creazione dei tombstone avvengono nella stessa transazione del dominio.
- L'aggiornamento di `last_accessed_at` sta fuori dalla transazione che genera il calendario ed è best effort: un suo errore viene registrato come `calendar_feed_last_access outcome=failed` e non cambia la risposta, che resta il calendario corretto.

## Componenti backend

I componenti sono separati dalle API calendario amministrative:

- entità e repository globali per `CalendarFeedTokenRegistry`;
- entità e repository tenant per `CalendarFeedSubscription`, `CalendarEventFeedTombstone` e `CalendarFeedIdempotency`;
- `CalendarFeedManagementService` per lifecycle, limiti, idempotenza e autorizzazioni;
- `CalendarFeedTokenService` per generazione, digest e fingerprint del token;
- `CalendarFeedIdempotencyCodec` per digest delle richieste e cifratura AES-GCM del token nelle ricevute;
- `CalendarFeedRateLimiter` per i limiti per token, IP e globali;
- `CalendarFeedTokenResolver` per digest, routing sicuro e proiezione degli eventi;
- `CalendarFeedEventRepository` per la query minimizzata con le regole di visibilità;
- `IcalendarRenderer` per la rappresentazione RFC 5545;
- `CalendarSubscriptionResource` per `GET` e `HEAD` pubblici; `CalendarFeedResource` e `AdminCalendarFeedResource` per la gestione;
- `CalendarFeedEventLifecycle`, servizio di dominio che aggiorna UID, sequenza e tombstone su tutte le mutazioni evento/serie;
- `CalendarFeedOwnerRevocationService` per la revoca dei feed personali quando il proprietario perde i ruoli o viene cancellato;
- `CalendarFeedTenantRevocationService` per la revoca di tutti i feed di un tenant disattivato, cancellato o cancellato per GDPR;
- `CalendarFeedAuditService` e l'entità `CalendarFeedAuditEntry` per la cronologia delle credenziali;
- `CalendarFeedMetrics` per le misure Micrometer a bassa cardinalità;
- `CalendarFeedTombstoneScheduler`, job multi-tenant di retention di tombstone e ricevute, con controllo di coerenza delle righe globali.

Il renderer riceve DTO dedicati e già minimizzati; non deve ricevere `CalendarEventsDTO`, che contiene campi non esportabili. Questo rende più difficile una futura esposizione accidentale di costi o partecipanti.

Configurazione (`application.yml`):

```yaml
application:
  calendar-feed:
    enabled: ${CALENDAR_FEED_ENABLED:true}
    public-base-url: ${TAURUS_PUBLIC_BASE_URL:http://localhost:8080}
    default-past-days: 90
    default-future-months: 18
    max-components: 10000
    tombstone-retention-days: 90
    idempotency-retention-days: 30
    suggested-refresh: PT6H
    rate-limit-per-token-hour: 120
    rate-limit-per-ip-hour: ${CALENDAR_FEED_RATE_LIMIT_PER_IP_HOUR:2000}
    rate-limit-global-hour: ${CALENDAR_FEED_RATE_LIMIT_GLOBAL_HOUR:100000}
```

La chiave facoltativa `application.calendar-feed.cleanup-cron` pianifica la pulizia di tombstone e ricevute e il controllo di coerenza del registro globale (default `0 15 3 * * *`).

Oltre al flag globale, ogni tenant deve avere la funzionalità `EXTERNAL_CALENDAR_FEED` (colonna `public.tenant.external_calendar_feed_enabled`, default `false`). Senza di essa le API di gestione sono negate e il download risponde `404`.

Nessun segreto viene inserito nella configurazione per generare i token: la sicurezza deriva dal CSPRNG e dalla non persistenza in chiaro del valore originale.

## Migrazioni

Le migration Liquibase coordinate sono cinque:

1. `master.xml`, `20260905020000_calendar_feed_registry.xml`: creazione di `public.calendar_feed_token_registry` con indici e FK globali;
2. `tenant-master.xml`, `20260905020001_external_calendar_feed.xml`: estensione `calendar_event`, creazione di `calendar_feed_subscription` e `calendar_event_feed_tombstone`;
3. `tenant-master.xml`, `20260906010000_calendar_feed_idempotency.xml`: creazione di `calendar_feed_idempotency`;
4. `tenant-master.xml`, `20260907000000_calendar_feed_audit.xml`: campi audit standard e colonna `deleted` della sottoscrizione;
5. `tenant-master.xml`, `20261029000000_calendar_feed_audit_history.xml`: creazione di `calendar_feed_audit`, la cronologia delle operazioni sulla credenziale.

Per gli eventi esistenti la migration tenant:

- valorizza `calendar_uid` con UUID casuali distinti;
- imposta `calendar_sequence = 0`;
- valorizza `calendar_feed_modified_at` con `COALESCE(edit_date, insert_date, current_timestamp)`;
- applica `NOT NULL` e indice univoco soltanto dopo il backfill;
- non crea feed né token automaticamente.

Il provisioning di un nuovo tenant riceve direttamente lo schema completo. Il bootstrap applica la migration a ogni tenant esistente secondo il meccanismo già descritto in [Schema PostgreSQL per tenant](postgres-tenant-schemas.md).

Rollback applicativo: disabilitare la feature flag rende il percorso pubblico indistinguibile da un token inesistente e nasconde le UI, mantenendo tabelle e UID per una successiva riattivazione. Le migration non vengono invertite in produzione.

## Osservabilità

`CalendarFeedMetrics` registra le misure Micrometer sotto il prefisso `taurus.calendar_feed.`:

| Misura | Etichette |
| --- | --- |
| `requests`, `request.duration` | `outcome` (`200`, `304`, `404`, `429`, `503`), `type`, `scope` |
| `resolution.duration` | `outcome` della risoluzione del token |
| `generation.duration`, `components` | nessuna |
| `generation.errors` | `kind`: `render` o `limit` |
| `rate.limited` | `scope`: `token`, `ip` o `global` |
| `operations` | `action` (`created`, `rotated`, `revoked`, `removed`), `outcome` |
| `tombstones`, `receipts.deleted` | `operation` della retention |
| `registry.orphans` | `outcome` del controllo di coerenza |

Le etichette sono chiuse e nessuna contiene ID di feed, tenant, utente, UID evento, fingerprint o token; la percentuale di `304` si legge dall'etichetta `outcome` delle richieste, senza un contatore dedicato. Il download registra un log `calendar_feed_download` con correlation ID generato dal server, esito, tipo, ambito e durata, mai il path tokenizzato; le operazioni sulla credenziale registrano `calendar_feed_credential` con l'autore in forma di hash. Un alert segnala aumento di `5xx`, tempi di generazione elevati, superamento del limite componenti o righe globali orfane rilevate dal controllo di coerenza del job notturno.

## Strategia di test

I casi elencati sotto sono coperti da `CalendarFeedTokenServiceTest`, `CalendarFeedIdempotencyCodecTest`, `CalendarFeedManagementServiceTest`, `CalendarFeedTenantIsolationTest`, `CalendarFeedRateLimiterTest`, `CalendarFeedEventLifecycleTest`, `IcalendarRendererTest`, `CalendarFeedTokenResolverTest`, `CalendarFeedMigrationIT` e `CalendarFeedIT`, che esercita con PostgreSQL reale l'intero percorso pubblico. Il file prodotto è sempre rileggibile da un parser iCalendar, perché `IcalendarRenderer` lo rilegge con `ical4j` prima di restituirlo. Due casi non hanno test automatico: la revoca concorrente a un download già autenticato, che è il limite transazionale dichiarato sopra, e la negoziazione gzip, che appartiene al server davanti all'applicazione. Nel frontend li coprono `calendar-feed-panel.component.spec.ts`, `calendar-feed.service.spec.ts` ed `event-by-uid.component.spec.ts`. Restano fuori dalla verifica automatica soltanto il collaudo manuale sui client e le verifiche d'infrastruttura, che sono tra i [Requisiti aperti](#requisiti-aperti).

### Unit test

- token di 256 bit, codifica URL-safe, digest e assenza di persistenza in chiaro;
- regole di visibilità per ogni combinazione di ruolo e stato;
- esclusione assoluta dei `DRAFT`;
- mapping `MINIMAL` e `STANDARD` con campi vietati assenti;
- UID stabile e `SEQUENCE` monotona;
- escaping di `\\`, virgole, punti e virgola, newline, emoji e caratteri accentati;
- folding a 75 ottetti senza corrompere UTF-8 e terminatori `CRLF`;
- conversione UTC attraverso cambio ora legale;
- tombstone per cancellazione e restrizione di stato;
- serie materializzate, eccezioni e occorrenze escluse;
- finestra temporale inclusiva e evento in corso;
- ETag deterministico e precedenza di `If-None-Match`;
- limiti e sanificazione della descrizione.

### Integration test backend

- creazione, elenco, rotazione e revoca con PostgreSQL reale;
- URL restituito una sola volta e assente da query successive;
- lookup globale seguito dal corretto schema tenant;
- token del tenant A impossibile da usare per leggere dati del tenant B;
- ruolo rimosso, utente disattivato, tenant disattivato e schema non `ACTIVE`;
- transazione atomica tra registro `public` e sottoscrizione tenant;
- revoca concorrente con una richiesta di download;
- `200`, calendario vuoto, `304`, `404`, `429` e `503`;
- `GET` e `HEAD`, MIME type, cache header e gzip;
- cancellazione GDPR del tenant senza token globali orfani;
- provisioning di un tenant dopo la migration;
- validazione del file generato tramite parser iCalendar;
- query count senza caricamento di disponibilità, presenze e costi.

### Test frontend

- endpoint personali e amministrativi separati nel servizio;
- colonne di tipo, proprietario e creazione presenti solo nell'elenco amministrativo;
- URL mostrato solo nella risposta di creazione/rotazione;
- avvertenza che invita a ruotare un link esposto e conferma di rotazione/revoca;
- chiave di idempotenza riusata finché una rotazione non riesce, così un retry non produce due link validi;
- assenza del token nello stato persistito del browser;
- cronologia leggibile da screen reader (`role="region"` con etichetta) e messaggi su sottoscrizione, ritardo di aggiornamento e importazione statica;
- risoluzione dell'UID del link e messaggio per un evento non più visibile.

### Compatibilità manuale

Prima del rilascio il medesimo feed di staging viene sottoscritto almeno in:

- Google Calendar web;
- Apple Calendar su macOS e iOS;
- Outlook sul web e desktop supportato.

Per ciascun client si verificano creazione, modifica di nome/data/luogo, cancellazione, cambio `PUBLIC -> COMPLETE`, evento ricorrente materializzato, caratteri italiani e rotazione URL. Il verbale annota il ritardo osservato ma non lo assume come SLA Taurus.

## Criteri di accettazione

1. Un utente `ROLE_USER` attivo può creare un feed e sottoscriverlo usando l'URL restituito.
2. Un utente esclusivamente `ROLE_USER_EXTERNAL` riceve soltanto eventi `PUBLIC`.
3. Nessun feed contiene eventi `DRAFT`.
4. Un utente privo di ruolo partecipante non può creare né continuare a usare un feed personale.
5. Admin e Super Admin possono creare feed tenant `INTERNAL` e `PUBLIC_ONLY`.
6. Il token ha almeno 256 bit di entropia ed è memorizzato soltanto come digest.
7. L'URL originale non è recuperabile dopo la risposta di creazione o rotazione.
8. La rotazione invalida il token precedente dopo il commit.
9. La revoca produce `404` per ogni download successivo.
10. Un token non espone né codifica il tenant e risolve un solo schema attivo.
11. Un test dimostra l'impossibilità di attraversare il confine tra due tenant.
12. Ogni evento mantiene lo stesso `UID` tra feed e aggiornamenti.
13. Una modifica visibile incrementa `SEQUENCE`; una modifica soltanto economica o di disponibilità non la incrementa.
14. Cancellazioni e restrizioni di visibilità producono tombstone per il pubblico corretto.
15. Ogni occorrenza di serie è un `VEVENT` autonomo e le eccezioni conservano il proprio UID.
16. Date e orari sono emessi in UTC e verificati attraverso un cambio di ora legale.
17. Il file rispetta escaping, folding UTF-8 e terminatori `CRLF` di RFC 5545.
18. Nessun feed contiene utenti, disponibilità, presenze, costi, compensi o altri campi esclusi.
19. `MINIMAL` e `STANDARD` producono esattamente i campi previsti.
20. Le richieste condizionali restituiscono `304` quando il calendario non è cambiato.
21. Un feed valido senza eventi restituisce un calendario vuoto valido con `200`.
22. Nessun livello applicativo o infrastrutturale registra il token in chiaro.
23. La cancellazione di tenant elimina o revoca tutte le credenziali globali prima del drop dello schema.
24. I file di staging sono accettati da Google Calendar, Apple Calendar e Outlook.
25. La UI informa che il polling è esterno, l'importazione è statica e la revoca non cancella copie già scaricate.

## Evoluzione futura verso la sincronizzazione bidirezionale

Il feed ICS non viene trasformato direttamente in un canale di scrittura. Una futura sincronizzazione bidirezionale richiederà connettori OAuth distinti per provider, consenso granulare, mapping degli account, gestione conflitti, webhook/polling, idempotenza e audit delle modifiche remote.

Gli elementi riutilizzabili saranno:

- `calendar_uid` e `calendar_sequence`;
- proiezione minimizzata dell'evento;
- regole di visibilità;
- test di interoperabilità;
- gestione centralizzata delle modifiche ICS-visibili.

Token feed, registro pubblico e endpoint anonimo resteranno invece una capacità di pubblicazione in sola lettura e non acquisiranno permessi di scrittura.

## Riferimenti

- [RFC 5545 — Internet Calendaring and Scheduling Core Object Specification](https://www.rfc-editor.org/rfc/rfc5545.html)
- [RFC 7986 — New Properties for iCalendar](https://www.rfc-editor.org/rfc/rfc7986.html)
- [iCal4j — libreria Java per iCalendar](https://www.ical4j.org/)
- [Google Calendar — aggiungere un calendario da URL](https://support.google.com/calendar/answer/37100)
- [Apple Calendar — sottoscrivere calendari su Mac](https://support.apple.com/guide/calendar/subscribe-to-calendars-icl1022/mac)
- [Microsoft Outlook — importare o sottoscrivere un calendario](https://support.microsoft.com/en-us/outlook/import-or-subscribe-to-a-calendar-in-outlook-com-or-outlook-on-the-web)

## Requisiti aperti

- Collaudo manuale su Google Calendar, Apple Calendar e Outlook ([Compatibilità manuale](#compatibilità-manuale)).
- Verifica della redazione del token su reverse proxy, WAF, APM e analytics ([Sicurezza e privacy](#sicurezza-e-privacy)).
- Rilascio progressivo: pilota con feed personali `MINIMAL`, poi feed tenant e livello `STANDARD`, infine abilitazione a tutti i tenant con alert attivi e verifica periodica dell'assenza di token nei log.
