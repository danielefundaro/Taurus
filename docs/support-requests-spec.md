# Segnalazioni e richieste di supporto

La funzionalità introduce in Taurus un canale privato tra gli utenti autenticati e gli amministratori del tenant per richieste di supporto, segnalazioni di bug, change request e comunicazioni non classificabili. Non sostituisce le segnalazioni di guasto dell'inventario, che restano governate dal proprio ciclo di vita in [QR code ed etichette per l'inventario](inventory-qr-code-spec.md).

La consegna degli aggiornamenti usa la pipeline descritta in [Generalizzazione della consegna delle notifiche interne](notification-delivery-generalization-spec.md), le preferenze in [Preferenze notifiche granulari](notification-preferences-spec.md), i file gestiti da [Gestione centralizzata dei media](media-asset-spec.md) e le convenzioni UI del [Taurus Layout Standard](taurus-layout-standard.md).

## Stato del documento

ID catalogo: `support-requests`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

## Obiettivo

Consentire a ogni utente autenticato di aprire e seguire una segnalazione privata nel tenant corrente e agli amministratori di rispondere, classificarla e governarne il ciclo di vita senza uscire da Taurus.

La prima versione deve permettere di:

- creare una segnalazione nelle categorie **Supporto**, **Bug**, **Change Request** e **Altro**;
- allegare immagini JPEG/PNG e documenti PDF;
- mostrare all'autore soltanto le proprie segnalazioni;
- mostrare ad amministratori e super amministratori tutte le segnalazioni del tenant corrente;
- mantenere una conversazione cronologica e non modificabile;
- registrare ogni cambio di stato e ogni richiesta di riapertura;
- notificare i soggetti interessati mediante centro notifiche e Web Push, se abilitato;
- filtrare e paginare la coda amministrativa;
- conservare isolamento tenant, audit e controllo di concorrenza.

## Decisioni principali

| Aspetto | Decisione |
| --- | --- |
| Destinatari | `ROLE_ADMIN` e `ROLE_SUPER_ADMIN` del tenant corrente |
| Mittenti | tutti i ruoli autenticati, compreso `ROLE_USER_EXTERNAL` |
| Visibilità | autore e amministratori del tenant; nessun altro utente |
| Canale di risposta | conversazione interna a Taurus |
| Categorie | `SUPPORT`, `BUG`, `CHANGE_REQUEST`, `OTHER` |
| Stati | `OPEN`, `UNDER_REVIEW`, `IN_PROGRESS`, `WAITING_FOR_REQUESTER`, `RESOLVED`, `REJECTED` |
| Cambio di stato | amministratori; unica eccezione è il ritorno automatico da attesa dopo una risposta dell'autore |
| Conversazione | messaggi append-only, non modificabili e non eliminabili dall'interfaccia |
| Allegati | JPEG, PNG e PDF; massimo 10 MB per file |
| Change request | privata, senza voti, adesioni o pubblicazione tra tenant |
| Integrazioni | nessuna integrazione con e-mail, GitHub, Jira o sistemi esterni |
| Amministrazione centrale | assente; un super amministratore opera sempre nel tenant selezionato |
| Feature flag | nessuno nella prima versione: è una capacità trasversale di base |

## Fuori ambito

- portale pubblico o segnalazioni anonime;
- help desk centralizzato tra tenant;
- posta elettronica, chatbot, live chat o messaggistica esterna;
- SLA contrattuali, turni di reperibilità ed escalation automatiche;
- assegnazione a un singolo amministratore;
- votazione o roadmap condivisa delle change request;
- modifica o cancellazione dei messaggi già inviati;
- note interne nascoste all'autore;
- collegamento automatico a errori backend, log, stack trace o dati diagnostici sensibili;
- sostituzione delle segnalazioni di guasto inventario;
- sicurezza e privacy come categoria speciale nella prima versione.

## Terminologia

- **Segnalazione**: contenitore con riferimento stabile, categoria, oggetto, stato e conversazione.
- **Autore**: utente autenticato che crea la segnalazione nel tenant corrente.
- **Gestore**: utente con ruolo `ROLE_ADMIN` o `ROLE_SUPER_ADMIN` nel tenant corrente.
- **Messaggio**: intervento immutabile dell'autore o di un gestore.
- **Evento di stato**: registrazione immutabile di una transizione, distinta dai messaggi.
- **Richiesta di riapertura**: motivazione inviata dall'autore su una segnalazione chiusa; non riapre autonomamente la segnalazione.

## Categorie e dati richiesti

Il modulo usa una base comune e mostra suggerimenti contestuali senza creare quattro modelli dati divergenti.

### Campi comuni

| Campo | Regola |
| --- | --- |
| categoria | obbligatoria |
| area applicativa | obbligatoria; valori restituiti dal backend |
| oggetto | obbligatorio, da 5 a 160 caratteri dopo trim |
| messaggio iniziale | obbligatorio, da 10 a 5.000 caratteri dopo trim |
| impatto | obbligatorio: `SELF`, `MULTIPLE_USERS`, `TENANT_BLOCKED` |
| allegati | facoltativi; massimo 5 per singolo invio e 20 per segnalazione |
| contesto client | facoltativo e costruito da campi consentiti |

L'utente descrive l'impatto, ma non assegna una priorità tecnica. La coda amministrativa deriva un'evidenza iniziale dall'impatto; il gestore può impostare `LOW`, `NORMAL`, `HIGH` o `URGENT`. Il valore predefinito è `NORMAL` e non viene esposto come promessa di tempo di risposta.

### Aree applicative

La prima versione prevede `GENERAL`, `ACCOUNT`, `USERS`, `CALENDAR`, `CONTENT`, `INVENTORY`, `FINANCE`, `NOTIFICATIONS` e `OTHER`.

Il backend restituisce codici ed etichette ammesse. Il frontend nasconde `FINANCE` e `INVENTORY` quando le rispettive funzionalità tenant sono disabilitate, ma il backend continua a convalidare il valore. Disabilitare successivamente una feature non rende illeggibili le segnalazioni storiche.

### Suggerimenti per categoria

- **Supporto**: cosa si sta cercando di fare e dove ci si è bloccati.
- **Bug**: passaggi per riprodurre, risultato osservato e risultato atteso.
- **Change Request**: risultato desiderato, utenti interessati e valore operativo.
- **Altro**: descrizione libera con gli stessi limiti comuni.

Questi sono suggerimenti editoriali nel modulo, non campi obbligatori separati. Evitano schemi rigidi e permettono di riclassificare una segnalazione senza perdere informazioni.

## Contesto tecnico acquisito

Il client può allegare automaticamente solo:

- rotta Angular corrente, senza query string né fragment;
- versione applicativa pubblica;
- famiglia del browser e sistema operativo ricavate dalla user agent, senza fingerprint;
- timestamp client e fuso orario IANA;
- eventuale correlation ID già restituito da una risposta di errore.

Tenant, autore e data autorevole provengono esclusivamente dal token e dal server. Non vengono acquisiti token, header di autenticazione, contenuto di form, URL completi, indirizzo IP nel corpo applicativo, log del browser o dati di altre entità. Prima dell'invio l'interfaccia mostra all'utente il riepilogo del contesto allegato.

## Ciclo di vita

```text
OPEN ───────────────► UNDER_REVIEW ─────────────► IN_PROGRESS
 │                         │                           │
 │                         ├───────────────► WAITING_FOR_REQUESTER
 │                         │                           │
 ├─────────────────────────┴───────────────────────────┼──► RESOLVED
 └─────────────────────────────────────────────────────┴──► REJECTED

WAITING_FOR_REQUESTER -- risposta autore --> UNDER_REVIEW
RESOLVED / REJECTED -- riapertura accettata da admin --> UNDER_REVIEW
```

Regole:

- la creazione produce sempre `OPEN`;
- solo un gestore sceglie esplicitamente un nuovo stato;
- quando l'autore risponde a `WAITING_FOR_REQUESTER`, il sistema passa atomicamente a `UNDER_REVIEW`;
- `RESOLVED` richiede una motivazione di chiusura visibile all'autore;
- `REJECTED` richiede una motivazione visibile all'autore;
- in stato terminale la normale casella di risposta è disabilitata;
- l'autore può inviare una sola richiesta di riapertura pendente, con motivazione obbligatoria;
- un gestore accetta la richiesta riaprendo in `UNDER_REVIEW`, oppure la respinge con una motivazione senza cambiare lo stato terminale;
- una nuova richiesta di riapertura è ammessa dopo un precedente rifiuto;
- nessuna transizione cancella messaggi, allegati o cronologia.

Transizioni concorrenti usano `entity_version`. Una versione non corrente restituisce `409 Conflict` con lo stato aggiornato, così il client può ricaricare senza sovrascrivere il lavoro di un altro amministratore.

## Autorizzazioni e isolamento tenant

| Operazione | Autore | Altro utente | Admin tenant | Super admin nel tenant |
| --- | --- | --- | --- | --- |
| creare | sì | sì, come autore di una nuova segnalazione | sì | sì |
| leggere la segnalazione | se proprietario | no | sì | sì |
| inviare un messaggio | se proprietario e non terminale | no | sì, se non terminale | sì, se non terminale |
| cambiare stato/priorità/categoria | no | no | sì | sì |
| chiedere riapertura | se proprietario e terminale | no | no | no |
| decidere la riapertura | no | no | sì | sì |
| scaricare un allegato | se proprietario | no | sì | sì |

Ogni richiesta richiede un tenant autenticato. Le tabelle risiedono nello schema del tenant; repository e servizi applicano comunque il controllo sull'autore. Un `public_id` inesistente, appartenente a un altro tenant o non visibile restituisce lo stesso `404`, senza distinguere le cause.

Il ruolo super amministratore non abilita query trasversali: deve essere attivo un tenant valido e vengono interrogate soltanto le sue tabelle. Non esistono endpoint globali né ricerca federata.

I permessi sono verificati nel service su ogni comando e lettura; la sola presenza della rotta nel frontend o del ruolo nel token non sostituisce i controlli di proprietà e tenant.

## Modello dati

Le nuove tabelle sono tenant-scoped e usano timestamp con timezone, audit applicativo e soft delete solo per le operazioni di retention. Le righe della conversazione non estendono entità legacy OpenSearch.

### `support_request`

| Campo | Tipo | Regola |
| --- | --- | --- |
| `id` | `BIGINT` | chiave primaria interna |
| `public_id` | `UUID` | obbligatorio, casuale e univoco nello schema |
| `reference_code` | `VARCHAR(32)` | obbligatorio e univoco, es. `SUP-000123` |
| `author_user_id` | `VARCHAR(255)` | ID Keycloak ricavato dal token |
| `client_request_id` | `UUID` | idempotenza della creazione per autore |
| `category` | `VARCHAR(32)` | enum supportata |
| `application_area` | `VARCHAR(32)` | area supportata |
| `subject` | `VARCHAR(160)` | obbligatorio |
| `impact` | `VARCHAR(32)` | impatto dichiarato dall'autore |
| `priority` | `VARCHAR(32)` | priorità amministrativa |
| `status` | `VARCHAR(32)` | stato corrente |
| `client_context` | `JSONB` | oggetto con allowlist e limite 4 KB |
| `last_activity_at` | `TIMESTAMPTZ` | aggiornato da messaggi, transizioni e riaperture |
| `closed_at/by` | timestamp, stringa | valorizzati negli stati terminali |
| `entity_version` | `BIGINT` | concorrenza ottimistica |
| audit comuni | vari | creazione, modifica e soft delete |

`reference_code` è leggibile e non è un segreto; le rotte usano `public_id`. Il codice usa una sequenza PostgreSQL dedicata nello schema tenant, con padding minimo di sei cifre, e non viene mai accettato dal client. I salti dovuti a rollback sono normali e il numero non esprime priorità o ordine assoluto tra tenant.

### `support_request_message`

| Campo | Tipo | Regola |
| --- | --- | --- |
| `id` | `BIGINT` | chiave primaria |
| `request_id` | `BIGINT` | FK `RESTRICT` |
| `author_user_id` | `VARCHAR(255)` | autore del messaggio |
| `kind` | `VARCHAR(32)` | `MESSAGE` o `REOPEN_REQUEST` |
| `body` | `VARCHAR(5000)` | obbligatorio dopo trim |
| `client_message_id` | `UUID` | idempotenza del singolo autore |
| `created_at` | `TIMESTAMPTZ` | assegnato dal backend |

Vincolo univoco `(request_id, author_user_id, client_message_id)`. Il messaggio iniziale viene creato nella stessa transazione della segnalazione con `kind = MESSAGE`.

### `support_request_status_event`

| Campo | Tipo | Regola |
| --- | --- | --- |
| `id` | `BIGINT` | chiave primaria |
| `request_id` | `BIGINT` | FK `RESTRICT` |
| `from_status` | `VARCHAR(32)` | nullo soltanto alla creazione |
| `to_status` | `VARCHAR(32)` | obbligatorio |
| `actor_user_id` | `VARCHAR(255)` | utente o attore di sistema |
| `reason` | `VARCHAR(2000)` | obbligatorio per chiusura e rifiuto riapertura |
| `event_type` | `VARCHAR(32)` | `CREATED`, `STATUS_CHANGED`, `AUTO_RESUMED`, `REOPEN_ACCEPTED`, `REOPEN_REJECTED` |
| `created_at` | `TIMESTAMPTZ` | assegnato dal backend |

La timeline dettaglio unisce messaggi ed eventi ordinandoli per `created_at` e `id`, con un discriminatore esplicito. Gli eventi non sono rappresentati come messaggi artificiali.

### `support_request_attachment`

| Campo | Tipo | Regola |
| --- | --- | --- |
| `id` | `BIGINT` | chiave primaria |
| `message_id` | `BIGINT` | FK `RESTRICT` |
| `media_asset_id` | `BIGINT` | FK `RESTRICT` verso `media_asset` |
| `display_order` | `INTEGER` | da 0, univoco nel messaggio |
| `created_by/at` | stringa, timestamp | audit minimo |

La categoria storage è `support-request-attachments`. Sono ammessi `image/jpeg`, `image/png` e `application/pdf`, determinati dal contenuto. Ogni file può occupare al massimo 10 MB; ogni invio contiene al massimo 5 file e una segnalazione al massimo 20. Il nome è sanificato dal servizio media.

Le immagini possono essere mostrate inline; i PDF vengono scaricati con `Content-Disposition: attachment`. Nessun allegato è accessibile tramite il generico download media: il download dedicato risolve prima relazione, proprietario, ruolo e tenant. L'implementazione deve quindi classificare la relazione supporto come riservata e fare restituire `404` da `GET /api/media/**` anche ai ruoli normalmente privilegiati; non deve affidarsi al solo ID del media asset.

### Richieste di riapertura pendenti

La richiesta pendente è identificata dall'ultimo messaggio `REOPEN_REQUEST` privo di un successivo evento `REOPEN_ACCEPTED` o `REOPEN_REJECTED`. Questa regola è sufficiente nella prima versione e viene protetta con lock sulla segnalazione. Non serve una quinta tabella.

### Indici

- univoco su `support_request.public_id`;
- univoco su `support_request.reference_code`;
- univoco su `(author_user_id, client_request_id)`;
- `(author_user_id, last_activity_at DESC)` per “Le mie segnalazioni”;
- `(status, priority, last_activity_at DESC)` per la coda amministrativa;
- `(category, application_area, last_activity_at DESC)` per i filtri;
- `(request_id, created_at, id)` su messaggi ed eventi;
- `(message_id, display_order)` univoco sugli allegati;
- indici sulle foreign key verso richiesta, messaggio e media asset.

## Migrazione Liquibase

Una nuova migration inclusa in `tenant-master.xml`:

1. crea la sequenza dei riferimenti, le cinque tabelle e i relativi vincoli;
2. aggiunge check constraint per enum, lunghezze e ordini;
3. aggiunge gli indici previsti;
4. registra `support_request_attachment` tra le relazioni considerate dalla verifica e pulizia dei media asset;
5. estende gli eventuali vincoli di `NotificationSource` con `SUPPORT`;
6. non esegue backfill, perché non esiste un dato legacy equivalente.

La migration deve essere verificata su PostgreSQL reale tramite Testcontainers e applicata anche durante il provisioning di un nuovo tenant.

## API REST

Gli endpoint usano `publicId` nelle rotte. Tutte le liste sono paginate lato server, con ordinamento allowlisted e limite massimo di 100 righe per pagina.

### Metadati e operazioni comuni

```text
GET  /api/support-requests/metadata
POST /api/support-requests
GET  /api/support-requests/mine
GET  /api/support-requests/{publicId}
POST /api/support-requests/{publicId}/messages
POST /api/support-requests/{publicId}/reopen-requests
GET  /api/support-requests/{publicId}/attachments/{attachmentId}
```

`POST /api/support-requests` e l'invio dei messaggi usano `multipart/form-data`: una parte JSON `request` e parti `files`. `clientRequestId` e `clientMessageId`, UUID generati dal client, rendono idempotenti i retry. La creazione usa un vincolo univoco su `(author_user_id, client_request_id)`; `client_request_id` è quindi presente anche nella tabella principale, benché omesso dalla vista utente.

### Operazioni amministrative

```text
GET   /api/admin/support-requests
PATCH /api/admin/support-requests/{publicId}
POST  /api/admin/support-requests/{publicId}/messages
POST  /api/admin/support-requests/{publicId}/reopen-requests/{messageId}/accept
POST  /api/admin/support-requests/{publicId}/reopen-requests/{messageId}/reject
```

La `PATCH` amministrativa accetta `expectedVersion` e almeno uno tra stato, categoria, area e priorità. Una transizione a `RESOLVED` o `REJECTED` richiede `reason`. Il backend ignora qualsiasi autore, tenant, data o codice riferimento inviato dal client.

### Risposta di dettaglio esemplificativa

```json
{
  "publicId": "8b785cbf-06ca-4b58-88c1-338bc211d4ef",
  "referenceCode": "SUP-000123",
  "category": "BUG",
  "applicationArea": "CALENDAR",
  "subject": "Aggiornamento incompleto della serie",
  "impact": "MULTIPLE_USERS",
  "priority": "HIGH",
  "status": "UNDER_REVIEW",
  "author": { "id": "...", "displayName": "Mario Rossi" },
  "lastActivityAt": "2026-09-09T10:15:00Z",
  "entityVersion": 3,
  "permissions": {
    "canReply": true,
    "canManage": false,
    "canRequestReopen": false
  },
  "timeline": [
    {
      "type": "MESSAGE",
      "id": 10,
      "author": { "id": "...", "displayName": "Mario Rossi" },
      "body": "Modificando la serie viene aggiornata una sola occorrenza.",
      "createdAt": "2026-09-09T09:40:00Z",
      "attachments": []
    },
    {
      "type": "STATUS_EVENT",
      "id": 4,
      "fromStatus": "OPEN",
      "toStatus": "UNDER_REVIEW",
      "createdAt": "2026-09-09T10:15:00Z"
    }
  ]
}
```

Gli ID utente vengono restituiti solo quando utili al client e non sono usati per autorizzare azioni. Se l'identità non è più disponibile, `displayName` diventa “Utente non disponibile”; non si conserva una copia permanente di nome o e-mail nella conversazione.

### Errori

Gli errori seguono RFC 7807:

- `400` per enum, testo, allegato o transizione non validi;
- `401` per identità o tenant mancanti;
- `403` per un'azione amministrativa priva del ruolo necessario;
- `404` per risorsa inesistente o non visibile;
- `409` per versione concorrente, idempotency key riutilizzata con payload diverso o stato non compatibile;
- `413` per file o richiesta multipart oltre limite;
- `415` per MIME type non ammesso;
- `429` per superamento del rate limit.

## Interfaccia utente

### Accesso e rotte

Un'azione con icona `pi-question-circle` e tooltip “Assistenza e segnalazioni” è visibile nella topbar a tutti gli autenticati e porta a `/support`. La stessa destinazione può comparire nella sezione Home del menu per essere trovabile anche da tastiera e su mobile.

Rotte:

```text
/support                  elenco personale
/support/new              nuova segnalazione
/support/:publicId        dettaglio e conversazione
/admin/support            coda del tenant, solo admin/super admin
```

La funzionalità non usa un dialogo per il modulo principale: testo, allegati e aiuti contestuali richiedono una pagina completa, navigabile e protetta dall'`UnsavedChangesGuard`.

### Elenco personale

Segue il guscio standard delle pagine elenco:

- intestazione “Le mie segnalazioni”, conteggio e azione primaria “Nuova segnalazione”;
- ricerca per riferimento e oggetto;
- filtri categoria, area e stato;
- lista paginata ordinata per ultima attività discendente;
- riga con riferimento, oggetto, categoria, stato, ultima attività e indicatore di aggiornamenti non letti;
- stato vuoto con spiegazione e azione di creazione;
- skeleton durante il caricamento e filtri preservati nella query string.

### Coda amministrativa

La coda mostra tutte le segnalazioni del tenant e aggiunge filtri per autore, impatto e priorità. Il default include gli stati non terminali; un filtro esplicito mostra risolte e respinte. Non sono previste azioni massive nella prima versione.

L'evidenza visuale distingue priorità e stato senza affidarsi soltanto al colore. `p-tag` presenta sempre un'etichetta testuale. L'ordinamento predefinito è priorità discendente e poi ultima attività discendente, ma una segnalazione `TENANT_BLOCKED` non viene automaticamente classificata `URGENT`.

### Nuova segnalazione

La pagina presenta:

1. categoria tramite `p-select`;
2. area applicativa tramite `p-select`;
3. oggetto con contatore caratteri;
4. impatto con opzioni descritte in linguaggio non tecnico;
5. `p-textarea` con suggerimento dipendente dalla categoria;
6. `p-fileupload` in modalità avanzata con selezione multipla e riepilogo limiti;
7. sezione comprimibile “Informazioni tecniche incluse”;
8. azioni “Annulla” e “Invia segnalazione”.

Il pulsante di invio resta disabilitato durante l'upload. Errori di campo sono associati tramite `aria-describedby`; il focus va al primo errore. Dopo il successo la pagina naviga al dettaglio e mostra “Segnalazione SUP-000123 inviata”. Un errore conserva il testo e gli allegati ancora validi.

### Dettaglio e conversazione

L'intestazione mostra oggetto e riferimento, con tag per categoria, area, stato e priorità amministrativa. Una colonna laterale o una sezione iniziale riassume autore, impatto, creazione e ultima attività.

Messaggi ed eventi sono presentati in una timeline verticale cronologica, non alternata su mobile. Ogni messaggio mostra autore, ruolo nel contesto della conversazione, data, testo e allegati. Gli eventi di stato sono più compatti dei messaggi e includono la motivazione quando prevista.

Il composer è in fondo alla timeline. Nei terminali viene sostituito da “Richiedi riapertura” per l'autore e dalle azioni di gestione per gli amministratori. Prima di abbandonare testo o file non inviati interviene `UnsavedChangesGuard`.

Gli amministratori possono modificare categoria, area, priorità e stato in una card “Gestione”, con conferma obbligatoria per gli stati terminali. Le etichette UI sono:

| Codice | Etichetta |
| --- | --- |
| `OPEN` | Aperta |
| `UNDER_REVIEW` | In valutazione |
| `IN_PROGRESS` | In lavorazione |
| `WAITING_FOR_REQUESTER` | In attesa dell'utente |
| `RESOLVED` | Risolta |
| `REJECTED` | Respinta |

## Notifiche

Si aggiunge `SUPPORT` a `NotificationSource`, con etichetta “Assistenza” e icona `pi-question-circle`. Le preferenze esistenti ricevono il default sicuro: in-app attivo e push `OFF` finché l'utente non abilita il canale, coerentemente con le nuove categorie introdotte dal backend.

| Evento | Destinatari | Operazione |
| --- | --- | --- |
| nuova segnalazione | admin e super admin attivi del tenant, escluso l'attore | `SUPPORT_REQUEST_CREATED` |
| nuovo messaggio dell'autore | admin e super admin, escluso l'attore | `SUPPORT_REQUEST_REPLIED` |
| risposta amministrativa | autore, escluso l'attore | `SUPPORT_REQUEST_REPLIED` |
| cambio di stato o riclassificazione | autore, escluso l'attore | `SUPPORT_REQUEST_UPDATED` |
| richiesta di riapertura | admin e super admin, escluso l'attore | `SUPPORT_REOPEN_REQUESTED` |
| decisione sulla riapertura | autore, escluso l'attore | `SUPPORT_REOPEN_DECIDED` |

Le notifiche sono `CONFIGURABLE`, con target `/support/{publicId}` per l'autore e `/admin/support/{publicId}` per i gestori. `eventKey` include richiesta, evento persistito e destinatario, così retry e fan-out non duplicano la consegna. Titolo e anteprima non includono corpo del messaggio, nome di file o contesto tecnico.

Se un amministratore apre una segnalazione personale, gli altri amministratori vengono notificati; l'attore non riceve una notifica per la propria operazione. Se non esistono altri gestori attivi, la segnalazione resta comunque visibile nella coda.

## Lettura e indicatori

Per offrire un indicatore affidabile a ogni partecipante non basta un timestamp sulla segnalazione: nella coda amministrativa lo stato di lettura è distinto per gestore. Viene quindi introdotta la tabella minimale:

### `support_request_read_state`

| Campo | Tipo | Regola |
| --- | --- | --- |
| `request_id` | `BIGINT` | parte della chiave e FK `CASCADE` |
| `user_id` | `VARCHAR(255)` | parte della chiave |
| `last_read_at` | `TIMESTAMPTZ` | ultimo dettaglio letto |

Una riga viene creata o aggiornata quando l'utente apre il dettaglio. La lista calcola `hasUnreadUpdates` confrontando `last_activity_at` con `last_read_at`; l'assenza della riga equivale a non letto. Non vengono prodotte ricevute di lettura visibili agli interlocutori.

## Sicurezza e privacy

- tutti i testi vengono renderizzati come testo, senza HTML interpretato;
- il backend applica trim, lunghezze massime e rifiuto dei caratteri di controllo non ammessi;
- MIME type ed estensione sono determinati e normalizzati server-side;
- SVG, HTML, archivi e documenti Office non sono accettati;
- il download usa `X-Content-Type-Options: nosniff`, `Cache-Control: private, no-store` e una CSP restrittiva dove applicabile;
- le immagini non incorporano contenuto remoto; i metadati non necessari vengono rimossi durante la normalizzazione;
- i PDF non vengono mostrati inline e devono essere sottoposti al controllo antimalware disponibile nell'ambiente; in assenza del controllo, la distribuzione richiede un'accettazione esplicita del rischio operativo;
- rate limit iniziale: 10 creazioni/ora per autore e 60 messaggi/ora per autore, con soglia anti-abuso per tenant;
- log e metriche non contengono oggetto, corpo, nome file, display name o `public_id` completo;
- il contesto client è un JSON costruito da allowlist, con chiavi e dimensione convalidate;
- nessun endpoint espone conteggi o riferimenti di un altro tenant.

La cancellazione GDPR dell'utente non elimina automaticamente una conversazione necessaria alla continuità amministrativa: rimuove l'associazione identificativa o la rende non risolvibile e la UI mostra “Utente non disponibile”. Le regole definitive di retention devono essere configurabili; il default progettuale elimina fisicamente allegati e contenuti delle segnalazioni terminali dopo 24 mesi, conservando soltanto metriche aggregate non personali. Segnalazioni aperte non vengono eliminate dal job di retention.

Messaggi ed eventi sono immutabili nell'uso ordinario, ma retention, obblighi legali e cancellazione amministrativa controllata possono applicare eliminazione o anonimizzazione tramite servizi dedicati e auditati.

## Consistenza e transazioni

- creazione di richiesta, primo messaggio, relazioni degli allegati ed evento `CREATED` sono atomici sul database;
- i file già scritti vengono rimossi per compensazione se la transazione fallisce;
- una risposta, l'eventuale transizione automatica da attesa e l'aggiornamento di `last_activity_at` sono atomici;
- una transizione crea sempre il relativo evento nella stessa transazione;
- l'intenzione di notifica viene inserita nell'outbox nella stessa transazione del cambiamento di dominio;
- una richiesta duplicata con lo stesso client UUID e lo stesso payload restituisce l'esito precedente; con payload diverso restituisce `409`;
- allegati non referenziati vengono eliminati dal normale processo di pulizia media.

## Osservabilità

Metriche senza tag ad alta cardinalità:

- segnalazioni create per categoria e impatto;
- segnalazioni aperte per stato e priorità;
- tempo tra creazione e prima presa in carico;
- tempo trascorso in `WAITING_FOR_REQUESTER` separato dal tempo di lavorazione;
- segnalazioni risolte, respinte, riaperte e richieste di riapertura rifiutate;
- errori di upload/download per MIME ed esito;
- rifiuti di autorizzazione e rate limit;
- notifiche prodotte e fallite tramite le metriche outbox esistenti.

Tenant, utente, riferimento e `public_id` non sono label metriche. I log usano un hash corto del riferimento tecnico e il correlation ID HTTP.

## Strategia di test

### Backend unitario

- validazione categorie, aree, impatto, priorità, testi e allegati;
- matrice completa delle transizioni;
- risposta autore che riporta automaticamente l'attesa in valutazione;
- richiesta, accettazione e rifiuto della riapertura;
- idempotenza di creazione e messaggi;
- esclusione dell'attore e risoluzione destinatari notifiche;
- calcolo degli aggiornamenti non letti;
- sanitizzazione del contesto client.

### Backend integrazione

- migrazione e provisioning di un nuovo schema tenant su PostgreSQL reale;
- isolamento tra due tenant con gli stessi ID interni;
- proprietario, altro utente, admin e super admin su ogni endpoint;
- utente esterno autenticato che crea, legge e risponde alla propria segnalazione;
- accesso indistinguibile `404` per risorsa altrui o inesistente;
- multipart valido, limiti `413`, MIME falsificato e PDF non ammesso dal controllo;
- download negato tramite endpoint media generico e consentito tramite relazione di supporto;
- concorrenza tra due cambi di stato;
- rollback del file e della relazione in caso di errore;
- outbox persistita insieme al cambiamento di dominio;
- retention e anonimizzazione dell'autore.

### Frontend

- visibilità delle rotte per tutti i ruoli autenticati;
- coda amministrativa visibile solo ad admin e super admin;
- campi, suggerimenti e validazione del modulo dinamico;
- gestione upload, errori parziali e limite allegati;
- elenco personale e filtri amministrativi paginati;
- permessi restituiti dal backend applicati a composer e azioni;
- timeline di messaggi ed eventi ordinata stabilmente;
- protezione delle modifiche non salvate;
- conflitto `409` con ricaricamento esplicito;
- accessibilità da tastiera, focus degli errori e annunci degli aggiornamenti;
- layout responsive e temi chiaro/scuro.

### End-to-end

1. un utente esterno crea un bug con screenshot;
2. un amministratore lo porta in valutazione e risponde;
3. l'autore riceve la notifica e aggiunge informazioni;
4. l'amministratore imposta attesa dell'utente;
5. una risposta dell'autore riporta la segnalazione in valutazione;
6. l'amministratore risolve con motivazione;
7. l'autore chiede la riapertura e un super amministratore la accetta;
8. un utente dello stesso tenant e un utente di un altro tenant non riescono a leggerla.

## Criteri di accettazione

1. Ogni ruolo autenticato può creare una segnalazione nel tenant corrente.
2. La creazione restituisce un riferimento leggibile e un `public_id` non prevedibile.
3. Un utente vede soltanto le segnalazioni di cui è autore.
4. Admin e super admin vedono e gestiscono tutte le segnalazioni del tenant corrente.
5. Nessun ruolo, compreso super admin, esegue ricerche trasversali senza un tenant attivo.
6. Categoria, area, impatto, oggetto e messaggio iniziale sono convalidati dal backend.
7. JPEG, PNG e PDF fino a 10 MB sono accettati; altri contenuti sono rifiutati in base ai byte reali.
8. Gli allegati sono scaricabili soltanto tramite una segnalazione visibile.
9. Messaggi ed eventi già inviati non possono essere modificati o eliminati dall'interfaccia.
10. Solo admin e super admin selezionano stato, categoria, area e priorità.
11. La risposta dell'autore durante l'attesa produce atomicamente `UNDER_REVIEW`.
12. Risoluzione e rifiuto richiedono una motivazione visibile all'autore.
13. L'autore può chiedere la riapertura, ma soltanto un gestore può accettarla.
14. Versioni concorrenti non sovrascrivono aggiornamenti amministrativi.
15. Creazione, messaggi e notifiche sono idempotenti e transazionalmente coerenti.
16. Le notifiche raggiungono i destinatari previsti senza notificare l'attore.
17. Liste personali e amministrative sono paginate e filtrate lato server.
18. Il contesto tecnico non contiene credenziali, query string o dati di form.
19. Test automatici dimostrano isolamento tra tenant e tra utenti dello stesso tenant.
20. La UI è utilizzabile da mobile, tastiera e con entrambi i temi Taurus.

## Piano di implementazione

### Fase 1 — Contratto e persistenza

- enum, entità, repository e migration tenant;
- DTO, validazione, idempotenza e matrice delle transizioni;
- test PostgreSQL, tenant e ruoli.

### Fase 2 — Allegati

- upload applicativo dedicato e relazioni con `media_asset`;
- validazione immagini/PDF, download autorizzato e pulizia compensativa;
- test di contenuto, limiti e autorizzazioni.

### Fase 3 — API e notifiche

- endpoint personali e amministrativi;
- `NotificationSource.SUPPORT`, composizione outbox e preferenze;
- metriche e rate limit.

### Fase 4 — Interfaccia

- ingresso topbar/menu, elenco personale, creazione e dettaglio;
- coda amministrativa e card di gestione;
- upload, timeline, stati vuoti, loading, errori e conflitti.

### Fase 5 — Qualificazione e adozione

- test end-to-end e accessibilità;
- verifica responsive e temi;
- collaudo antimalware PDF e retention nell'ambiente target;
- documentazione operativa e aggiornamento del catalogo delle funzionalità.

## Compatibilità e rilascio

Le nuove tabelle sono additive e non modificano i dati esistenti. L'aggiunta di `SUPPORT` a `NotificationSource` richiede backend e frontend compatibili nello stesso rilascio, perché il profilo preferenze enumera tutte le categorie. Durante un rollout non atomico, il frontend deve usare un fallback di etichetta e icona per sorgenti sconosciute.

Non viene aggiunto un feature flag nella prima versione. Se fosse necessario un rilascio progressivo, il flag dovrà proteggere insieme rotte, menu, API e produzione delle notifiche, senza rendere inaccessibili dati già creati dopo una successiva disabilitazione.

Prima del rilascio devono essere verificati: migration PostgreSQL, controllo PDF, limiti del reverse proxy per multipart, job di retention, header di download, preferenze della nuova sorgente e comportamento con tenant privo di altri amministratori attivi.
