# Elaborazione asincrona dei PDF delle tracce

## Stato del documento

ID catalogo: `track-pdf-processing`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

## Obiettivo

Il caricamento di un PDF dalla pagina di dettaglio di una traccia deve essere osservabile anche dopo la conclusione della richiesta HTTP iniziale. L'utente deve poter distinguere un file in coda, un file in elaborazione, un'elaborazione conclusa e un errore, anche dopo un refresh o la riapertura della pagina.

Il processo resta asincrono: il caricamento registra un job persistente e pubblica il lavoro su RabbitMQ; la creazione delle parti avviene nel worker e non dipende dal salvataggio manuale dei dati della traccia.

## Ambito

La specifica riguarda:

- il caricamento PDF su una traccia esistente;
- il caricamento PDF dall'elenco delle tracce, che crea una nuova traccia;
- il ciclo di vita persistente di `upload_job`;
- il contratto HTTP per avvio, consultazione e nuovo tentativo;
- la lettura iniziale e la presentazione dello stato nel dettaglio della traccia;
- il riallineamento automatico delle parti al completamento;
- sicurezza tenant, diagnosi operativa e test.

La gestione fisica del file sorgente e dei media prodotti resta disciplinata dalla [Gestione centralizzata dei media](media-asset-spec.md).

## Modello e ciclo di vita

Ogni caricamento crea un record tenant-scoped in `upload_job`, associato alla traccia e al `media_asset` sorgente. Il job è la fonte autorevole dello stato del processo; RabbitMQ trasporta il comando ma non sostituisce la persistenza applicativa.

Gli stati esposti sono:

| Stato | Significato | Stato terminale |
|---|---|---|
| `TO_PROCESS` | File acquisito e lavoro in coda | No |
| `IN_PROGRESS` | Worker in elaborazione | No |
| `DONE` | Parti e media creati correttamente | Sì |
| `ERROR` | Elaborazione non conclusa; è possibile riprovare | Sì |

L'enum `UploadFileStatusEnum` contiene anche `NOT_FOUND`. Il backend non lo assegna mai; il frontend lo mostra come «File sorgente non disponibile».

Transizioni ammesse:

```text
TO_PROCESS -> IN_PROGRESS -> DONE
                    `-----> ERROR
TO_PROCESS ----------------> ERROR
ERROR -> TO_PROCESS
```

Il worker imposta `IN_PROGRESS` con una transizione condizionata da `TO_PROCESS` prima delle operazioni costose e registra `DONE` soltanto dopo la persistenza completa delle parti e dei media. Un'eccezione gestita porta il job a `ERROR`. Se la pubblicazione del messaggio dopo il commit fallisce, il job passa da `TO_PROCESS` a `ERROR` in una transazione propria (`QueueUploadFilesService.markUnpublished`) ed è ritentabile.

Il nuovo tentativo riutilizza il job e il file sorgente già acquisito, imposta nuovamente `TO_PROCESS` e ripubblica il comando. La richiesta è rifiutata se il job non appartiene alla traccia e al tenant correnti o non è in `ERROR`.

## Contratto HTTP

Tutte le rotte richiedono autenticazione e i ruoli già autorizzati a modificare le tracce.

### Avvio

```http
POST /api/tracks/{trackId}/stream
Content-Type: multipart/form-data

file=<pdf>
annotations=<json opzionale>
```

Una richiesta accettata restituisce `202 Accepted` e il `QueueUploadFilesDTO` persistito nel body. Almeno `id`, `trackId`, `name` e `status` devono essere disponibili al frontend; lo stato iniziale è `TO_PROCESS`.

```http
POST /api/tracks/stream
Content-Type: multipart/form-data
```

La variante senza id accetta gli stessi campi, crea una traccia con il nome del file senza estensione e accoda il job su di essa. La usa `AddFilesDialogComponent` dall'elenco delle tracce, che poi apre il dettaglio della nuova traccia.

Il `202` conferma l'acquisizione e l'accodamento, non il completamento dell'elaborazione. Errori di validazione o acquisizione sincrona continuano a usare una risposta `4xx/5xx` e non devono essere presentati come job avviati.

### Consultazione

```http
GET /api/tracks/{trackId}/upload-jobs
```

Restituisce i job visibili della traccia come elenco di `QueueUploadFilesDTO`, compresi quelli non terminali e quelli in `ERROR` necessari a mostrare lo stato e l'azione di riprova. L'ordinamento è per `insert_date` decrescente, senza criterio di spareggio.

### Nuovo tentativo

```http
POST /api/tracks/{trackId}/upload-jobs/{jobId}/retry
```

Per un job in `ERROR`, restituisce `202 Accepted` con il job aggiornato a `TO_PROCESS`. La risposta significa che il nuovo tentativo è stato accodato. La rotta deve evitare l'accodamento duplicato causato da doppio clic o richieste concorrenti.

### Errori e isolamento

- `404 Not Found` quando traccia o job non sono visibili nel tenant corrente, senza rivelare l'esistenza di dati di altri tenant;
- `409 Conflict` quando il retry non è compatibile con lo stato corrente;
- `400 Bad Request` o `415 Unsupported Media Type` per input non valido;
- nessun endpoint accetta tenant, path fisico o chiave di storage dal client.

I messaggi restituiti al frontend devono essere sicuri e comprensibili. Stack trace, path e dettagli infrastrutturali restano nei log correlati al job.

## Esperienza nel frontend

La sezione «File PDF» nel dettaglio della traccia mostra una riga persistente per ciascuno dei cinque job più recenti, con nome del file e stato localizzato:

| Stato API | Etichetta italiana | Presentazione |
|---|---|---|
| `TO_PROCESS` | In coda | indicatore indeterminato |
| `IN_PROGRESS` | Elaborazione in corso | indicatore indeterminato |
| `DONE` | Completato | conferma non animata |
| `ERROR` | Elaborazione non riuscita | messaggio di errore e pulsante «Riprova» |

Non viene mostrata una percentuale, perché il backend non misura un avanzamento reale. Lo stato non è affidato soltanto a toast o variabili locali: all'apertura della pagina il frontend legge i job persistiti e ricostruisce la visualizzazione.

Dopo un upload accettato, il job restituito viene mostrato immediatamente. `GET /api/tracks/{trackId}/upload-jobs` viene invocato una sola volta durante `ngOnInit`; il frontend non esegue polling né altre richieste automatiche per aggiornare lo stato.

Gli avanzamenti completati dal worker diventano visibili alla successiva apertura o al refresh della pagina. Quando il caricamento iniziale restituisce un job in `ERROR`, l'utente può scegliere «Riprova»; la risposta del retry aggiorna immediatamente la riga locale a «In coda» senza avviare interrogazioni periodiche.

Il pulsante di caricamento impedisce invii duplicati durante la sola richiesta HTTP iniziale. Un altro upload può essere limitato mentre esiste un job non terminale per la stessa traccia, se necessario per evitare risultati concorrenti non deterministici.

## Coerenza, concorrenza e recupero

- La pubblicazione RabbitMQ avviene solo dopo il commit che rende leggibili job e file sorgente.
- Il consumer ricava il tenant dal messaggio validato e ripristina il contesto al termine.
- La lavorazione e il retry sono idempotenti rispetto allo stesso job: il worker ignora un messaggio il cui job non è più `TO_PROCESS`, e il retry usa la transizione condizionata `ERROR -> TO_PROCESS`, quindi una richiesta concorrente riceve `409`.
- `DONE` implica che la successiva lettura della traccia osservi le parti create.
- I job fermi vengono rilevati e chiusi. `UploadJobRecovery` gira a intervalli regolari su tutti i tenant attivi e chiude in `ERROR` i job fermi nello stato corrente da più di `application.upload.stale-after`: un `IN_PROGRESS` oltre soglia è un worker arrestato, un `TO_PROCESS` oltre soglia è una coda ferma o senza consumer. Chiuso il job, l'interfaccia mostra «Elaborazione non riuscita» con «Riprova».
- La chiusura è preferita al riaccodamento automatico perché il messaggio porta il token di chi ha caricato il file: riaccodare dal recupero significherebbe fabbricare un'identità di sistema con i permessi di scrittura di un utente. Il nuovo tentativo riparte invece dalla richiesta dell'utente, con il suo token. Un messaggio ancora in coda per un job chiuso è innocuo: il worker lo scarta, perché la presa in carico è condizionata a `TO_PROCESS`.
- La soglia deve restare più larga della conversione più lenta plausibile. Un job chiuso mentre il worker sta ancora lavorando produce comunque le sue parti ma non raggiunge `DONE`: il worker lo registra come conclusione tardiva (log di avviso e contatore `LATE`) e un nuovo tentativo dell'utente duplicherebbe le parti. È il motivo del valore predefinito di trenta minuti.
- Il file sorgente non viene eliminato mentre il job può essere riprovato.

Configurazione (`application.upload`):

| Proprietà | Valore predefinito | Significato |
|---|---|---|
| `recovery-enabled` | `true` | Attiva il recupero pianificato. |
| `stale-after` | `PT30M` | Da quanto un job deve essere fermo nello stato corrente per considerarlo interrotto. |
| `recovery-delay` | `PT5M` | Intervallo fra due giri di recupero. |
| `max-recovered-per-run` | `100` | Job chiusi al massimo in un giro per tenant. |

## Osservabilità e gestione operativa

I log del worker riportano `jobId`, tenant e media sorgente senza il contenuto del PDF; gli errori includono l'eccezione. `UploadJobMetrics` espone le metriche del modulo, con tag a cardinalità nota e senza tenant, identificativi o nomi di file:

- `taurus.upload.jobs` con `operation`: `QUEUED`, `STARTED`, `COMPLETED`, `FAILED`, `RETRIED`, `CLOSED_STALE` (chiuso dal recupero), `IGNORED` (messaggio scartato perché il job non era più in coda) e `LATE` (elaborazione conclusa su un job già chiuso);
- `taurus.upload.failures` con `reason`: `PUBLISH_FAILED`, `PROCESSING_FAILED`, `STALE_WORKER`, `STALE_QUEUE`, `MISSING_JOB`, `MISSING_TENANT`, `NOT_RETRYABLE`, `STATE_CHANGED`;
- `taurus.upload.jobs.pending` per stato (`TO_PROCESS`, `IN_PROGRESS`) e `taurus.upload.jobs.oldest.age` in secondi, scritte dal giro di recupero sommando tutti i tenant: sono il numero e l'anzianità dei job non terminali;
- `taurus.upload.job.duration` con `outcome`, la durata dell'elaborazione nel worker. Misura il lavoro, non l'attesa: il tempo passato in coda si legge sull'anzianità, perché il messaggio non porta la data di accettazione del job;
- `taurus.upload.queue.messages`, i messaggi ancora nella coda `upload.files`, a `-1` quando il broker non risponde — valore che distingue «coda vuota» da «broker non raggiungibile».

Il monitoraggio previsto riguarda inoltre il backlog, i consumer e la redelivery della coda `upload.files` e lo spazio disponibile nello storage media.

La coda `upload.files` ha due consumatori nello stesso contesto applicativo: quello dichiarato in `RabbitMQConfig` e quello generato dall'annotazione `@RabbitListener` sul `Receiver`. Non è un problema di correttezza, perché un messaggio va a un solo consumatore, ma chi conta i consumer sul broker ne vede due per replica.

Un job in `TO_PROCESS` con coda ferma indica tipicamente indisponibilità di RabbitMQ o assenza di consumer. Un job bloccato in `IN_PROGRESS` richiede verifica del worker, delle risorse PDF e dello storage. Prima di un nuovo tentativo manuale va controllato che non sia ancora attivo un consumer sullo stesso job.

## Test richiesti

Le verifiche elencate qui sono coperte da: `TracksServiceImplTest` (pubblicazione dopo il commit, pubblicazione fallita, retry di un job `ERROR`, retry rifiutato su uno stato non compatibile e job di un'altra traccia nascosto), `ReceiverTest` (contesto tenant, percorso completo fino a `DONE` su un PDF vero, passaggio a `ERROR`, redelivery ignorata, conclusione tardiva e messaggio senza tenant), `UploadJobRecoveryTest` (chiusura dei job fermi, misure dei job non terminali, tenant in errore che non blocca gli altri), `UploadJobConcurrencyIT` (più repliche sullo stesso job), `UploadJobPipelineIT` (PostgreSQL e RabbitMQ reali: percorso completo, consumatore fermato e riavviato, nuovo tentativo, recupero di un job in elaborazione e isolamento fra tenant) e, sul frontend, `detail.component.spec.ts` (lettura unica in `ngOnInit`, etichette dei cinque stati, job del `202` mostrato subito, «Riprova» e limite di cinque righe).

### Backend

- l'upload valido restituisce `202`, body del job e stato `TO_PROCESS`;
- job, media sorgente e pubblicazione dopo commit restano coerenti anche in caso di errore;
- il worker esegue le transizioni `TO_PROCESS -> IN_PROGRESS -> DONE`;
- un'eccezione porta a `ERROR` senza lasciare risultati parziali visibili;
- la consultazione restituisce solo i job della traccia e del tenant autorizzati;
- il retry di `ERROR` restituisce `202`, passa a `TO_PROCESS` e pubblica una sola volta;
- retry di stato non compatibile e accesso cross-tenant sono rifiutati;
- redelivery dello stesso messaggio non duplica parti o media.

### Frontend

- il job del `202` compare subito con etichetta italiana;
- la consultazione dei job viene effettuata una sola volta durante `ngOnInit`;
- nessun timer o polling genera ulteriori richieste di consultazione;
- `ERROR` mostra «Riprova» e il retry riporta la UI a «In coda»;
- refresh e riapertura ricostruiscono lo stato dai job persistiti;
- spinner, testo e azioni sono accessibili da tastiera e comprensibili senza affidarsi al solo colore.

### Integrazione

- test con PostgreSQL e RabbitMQ reali del percorso upload, consumo e completamento;
- arresto e riavvio del consumer con job in coda;
- errore di conversione PDF seguito da retry riuscito;
- isolamento tenant del messaggio, del job, del file e delle parti prodotte.

## Criteri di accettazione

La funzionalità è completa quando:

1. la richiesta di upload risponde `202` con un job persistente;
2. tutti e quattro gli stati vengono aggiornati dal processo reale ed esposti dalle API;
3. lo stato resta visibile dopo refresh;
4. il frontend consulta i job una sola volta in `ngOnInit` e mostra gli aggiornamenti al successivo refresh o accesso;
5. un errore offre un retry controllato;
6. redelivery e retry non generano duplicati;
7. test backend e frontend coprono contratto, transizioni e regressioni principali;
8. metriche, log e runbook permettono di diagnosticare job fermi o falliti.

## Fuori scope

- avanzamento percentuale per pagina;
- polling, WebSocket o Server-Sent Events per l'aggiornamento automatico dello stato;
- modifica manuale dello stato da parte del client;
- conservazione illimitata dei job terminali;
- modifica delle regole generali di conversione, riconoscimento strumenti o manipolazione PDF.

## Requisiti aperti

Nessuno: la specifica è coperta dal codice e dai test.
