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
| Persistenza del token | solo digest SHA-256; il valore originale è mostrato alla creazione o rotazione |
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

Questa regola evita che un URL di lunga durata trasformi l'accesso amministrativo in una pubblicazione permanente. Il feed non cambia silenziosamente significato: se viene meno il ruolo richiesto dal suo ambito, viene revocato e dalla richiesta successiva restituisce `404`. L'utente può creare un nuovo feed con l'ambito consentito. La revoca è eseguita in modo proattivo durante la modifica dell'utente e verificata nuovamente a ogni download come difesa.

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

Il valore predefinito è `MINIMAL`. Il collegamento al dettaglio non concede accesso: l'apertura continua a richiedere autenticazione e ruolo compatibile.

Non vengono mai esportati, indipendentemente dal livello:

- ID numerici del database;
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
6. ruotare l'URL oppure revocare il feed.

Prima di mostrare l'URL, la UI avverte: **Chiunque possieda questo link può leggere gli eventi inclusi. Non inoltrarlo e ruotalo se pensi che sia stato esposto.**

La rotazione richiede conferma perché interrompe la sottoscrizione esistente. Dopo la rotazione il nuovo URL è mostrato una sola volta e il precedente smette di funzionare.

### Gestione tenant

Nell'amministrazione del tenant compare **Feed calendario** con:

- nome riconoscibile;
- ambito `INTERNAL` o `PUBLIC_ONLY`;
- livello di dettaglio;
- finestra temporale;
- creatore, data di creazione, stato e ultimo accesso approssimativo;
- azioni di rotazione e revoca.

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
| audit/versione | campi standard tenant | include concorrenza ottimistica |

Vincoli e indici:

- FK `owner_user_id -> app_user.id` senza cancellazione a cascata distruttiva;
- indice su `(owner_user_id, status)`;
- indice su `(feed_type, status)`;
- massimo 3 feed `PERSONAL` attivi per proprietario e 10 feed `TENANT` attivi, verificati sotto lock transazionale;
- un feed personale revocato resta nell'audit ma non è riattivabile: si crea un nuovo token.

Il digest completo non viene duplicato nello schema tenant. `token_fingerprint` non consente autenticazione e serve soltanto a distinguere feed in UI e log amministrativi.

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

Il token è opaco: non contiene tenant, feed, utente, timestamp o firma decodificabile. Prima della persistenza il backend calcola SHA-256 sui byte originali e memorizza soltanto il digest.

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

La generazione usa la libreria Java `ical4j`, selezionando in implementazione una versione stabile compatibile con Java 17, invece di costruire stringhe manualmente. Sono comunque obbligatori test sul risultato serializzato.

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

Il limite tecnico predefinito è 10.000 componenti tra eventi e tombstone per risposta, configurabile. Il server non tronca mai silenziosamente il calendario: se il limite è superato restituisce `503 Service Unavailable`, registra una metrica e richiede una finestra più piccola tramite gestione del feed.

## API

### Feed personali autenticati

Base path: `/api/calendar-feeds`.

| Metodo e percorso | Comportamento |
| --- | --- |
| `GET /api/calendar-feeds` | elenca i feed personali del chiamante senza token o URL |
| `POST /api/calendar-feeds` | crea un feed personale e restituisce l'URL una sola volta |
| `POST /api/calendar-feeds/{id}/rotate` | ruota il token del feed posseduto |
| `DELETE /api/calendar-feeds/{id}` | revoca il feed posseduto; idempotente |

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

L'amministratore non può ruotare un feed personale perché riceverebbe la nuova credenziale dell'utente; può soltanto revocarlo.

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

L'ETag è calcolato sulla rappresentazione canonica oppure su un revision hash equivalente che includa configurazione del feed, eventi visibili e tombstone. `If-None-Match` ha precedenza e restituisce `304 Not Modified` senza corpo quando coincide. `If-Modified-Since` è supportato come fallback.

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
- rate limit principale per digest, predefinito 120 richieste/ora, e soglia anti-abuso globale/IP molto più alta e configurabile per non penalizzare gli egress condivisi dei provider;
- `404` uniforme per credenziali non valide;
- blocco dei metodi diversi da `GET` e `HEAD` sul path pubblico;
- nessun cookie di sessione necessario per la lettura;
- validazione stretta di nome, descrizione, luogo e URL per prevenire injection ICS;
- URL pubblico costruito soltanto da una base URL di configurazione fidata, mai dall'header `Host` della richiesta;
- audit di creazione, rotazione e revoca senza valore segreto;
- revoca automatica quando proprietario o tenant vengono disattivati o cancellati.

Il campo `last_accessed_at` è informativo: un accesso può appartenere al provider e non dimostra che l'utente abbia aperto il calendario. Per evitare una scrittura a ogni polling viene aggiornato best effort, in modo asincrono o condizionale, al massimo una volta ogni 24 ore e non partecipa alla correttezza del feed.

## Concorrenza e consistenza

- Creazione, rotazione e revoca sono serializzate sulla sottoscrizione e rispettano `entity_version`.
- Due rotazioni concorrenti producono al massimo un nuovo token accettato; la richiesta perdente riceve `409 Conflict` o la risposta idempotente già registrata.
- Generazione e revoca concorrenti possono completare una risposta già autenticata prima del commit di revoca; ogni nuova richiesta successiva al commit fallisce. Questo è il limite transazionale dichiarato.
- La query legge eventi e tombstone in una singola transazione read-only per produrre una fotografia coerente.
- La modifica di un evento, l'incremento della sequenza e la creazione dei tombstone avvengono nella stessa transazione del dominio.
- Il fallimento dell'aggiornamento best effort di `last_accessed_at` non invalida una risposta ICS corretta.

## Componenti backend previsti

La futura implementazione introduce componenti separati dalle API calendario amministrative:

- entità e repository globali per `CalendarFeedTokenRegistry`;
- entità e repository tenant per `CalendarFeedSubscription` e `CalendarEventFeedTombstone`;
- `CalendarFeedManagementService` per lifecycle e autorizzazioni;
- `CalendarFeedTokenResolver` per digest e routing sicuro;
- `CalendarFeedProjectionService` per visibilità e query minimizzata;
- `IcalendarRenderer` per la rappresentazione RFC 5545;
- `CalendarSubscriptionResource` per `GET` e `HEAD` pubblici;
- listener o servizio di dominio unico che aggiorni UID, sequenza e tombstone su tutte le mutazioni evento/serie;
- job multi-tenant di retention dei tombstone.

Il renderer riceve DTO dedicati e già minimizzati; non deve ricevere `CalendarEventsDTO`, che contiene campi non esportabili. Questo rende più difficile una futura esposizione accidentale di costi o partecipanti.

Configurazione indicativa:

```yaml
application:
  calendar-feed:
    enabled: false
    public-base-url: https://taurus.example
    default-past-days: 90
    default-future-months: 18
    max-components: 10000
    tombstone-retention-days: 90
    suggested-refresh: PT6H
    rate-limit-per-token-hour: 120
```

Nessun segreto viene inserito nella configurazione per generare i token: la sicurezza deriva dal CSPRNG e dalla non persistenza del valore originale.

## Migrazioni

Sono necessarie due migration Liquibase coordinate:

1. `master.xml`: creazione di `public.calendar_feed_token_registry` con indici e FK globali;
2. `tenant-master.xml`: estensione `calendar_event`, creazione di `calendar_feed_subscription` e `calendar_event_feed_tombstone`.

Per gli eventi esistenti la migration tenant:

- valorizza `calendar_uid` con UUID casuali distinti;
- imposta `calendar_sequence = 0`;
- valorizza `calendar_feed_modified_at` con `COALESCE(edit_date, insert_date, current_timestamp)`;
- applica `NOT NULL` e indice univoco soltanto dopo il backfill;
- non crea feed né token automaticamente.

Il provisioning di un nuovo tenant riceve direttamente lo schema completo. Il bootstrap applica la migration a ogni tenant esistente secondo il meccanismo già descritto in [Schema PostgreSQL per tenant](postgres-tenant-schemas.md).

Rollback applicativo: disabilitare la feature flag rende il percorso pubblico indistinguibile da un token inesistente e nasconde le UI, mantenendo tabelle e UID per una successiva riattivazione. Le migration non vengono invertite in produzione.

## Osservabilità

Metriche a bassa cardinalità:

- richieste feed per esito, `feedType` e ambito;
- durata risoluzione token;
- durata generazione ICS;
- numero componenti per risposta;
- percentuale risposte `304`;
- errori di rendering;
- rate limit applicati;
- tombstone creati ed eliminati;
- righe globali orfane rilevate dal controllo di coerenza.

Nessuna metrica contiene ID di feed, tenant, utente, UID evento, fingerprint o token. I log applicativi usano un correlation ID generato dal server e registrano soltanto esito e classe dell'errore. Un alert segnala aumento di `5xx`, tempi di generazione elevati, superamento del limite componenti o incoerenze tra registro globale e schema tenant.

## Strategia di test

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

- visibilità della sezione per ruoli partecipanti;
- gestione personale e amministrativa separata;
- URL mostrato solo nella risposta di creazione/rotazione;
- copy-to-clipboard, avvertenza e conferma di rotazione/revoca;
- assenza del token nello stato persistito del browser e nei messaggi analytics;
- messaggi su sottoscrizione, ritardo di aggiornamento e importazione statica;
- gestione accessibile da tastiera e screen reader.

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

## Piano di rilascio

### Fase 1 — Fondazioni disabilitate

- migration globali e tenant;
- UID e sequenze eventi;
- registro token e lifecycle;
- feature flag disabilitata;
- redazione verificata su proxy, WAF, APM e log.

### Fase 2 — Backend e collaudo interno

- endpoint personali e download ICS;
- renderer, tombstone, cache e rate limit;
- test automatici e feed di staging;
- verifica manuale sui tre ecosistemi.

### Fase 3 — Pilota personale

- abilitazione a uno o pochi tenant;
- solo feed personali `MINIMAL` inizialmente;
- osservazione di errori, dimensioni, frequenze di polling e compatibilità.

### Fase 4 — Feed tenant e dettaglio standard

- gestione amministrativa;
- ambiti `INTERNAL` e `PUBLIC_ONLY`;
- livello `STANDARD`;
- documentazione utente definitiva.

### Fase 5 — General availability

- abilitazione controllata a tutti i tenant;
- alert e runbook di revoca per incidente;
- verifica periodica dell'assenza di token nei log;
- retention automatica dei tombstone e controllo righe orfane.

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
