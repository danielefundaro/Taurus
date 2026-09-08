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

Transizioni ammesse:

```text
TO_PROCESS -> IN_PROGRESS -> DONE
                    `-----> ERROR
TO_PROCESS ----------------> ERROR
ERROR -> TO_PROCESS
```

Il worker deve impostare `IN_PROGRESS` prima delle operazioni costose e registrare `DONE` soltanto dopo la persistenza completa delle parti e dei media. Qualsiasi errore definitivo deve portare il job a `ERROR`; non deve rimanere indefinitamente `IN_PROGRESS` dopo un'eccezione gestita.

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

Il `202` conferma l'acquisizione e l'accodamento, non il completamento dell'elaborazione. Errori di validazione o acquisizione sincrona continuano a usare una risposta `4xx/5xx` e non devono essere presentati come job avviati.

### Consultazione

```http
GET /api/tracks/{trackId}/upload-jobs
```

Restituisce i job visibili della traccia come elenco di `QueueUploadFilesDTO`, compresi quelli non terminali e quelli in `ERROR` necessari a mostrare lo stato e l'azione di riprova. L'ordinamento è stabile, dal più recente al meno recente.

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

La sezione «File pdf» nel dettaglio della traccia mostra una riga o card persistente per ogni job rilevante, con nome del file e stato localizzato:

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
- La lavorazione e il retry devono essere idempotenti rispetto allo stesso job, così una redelivery non duplica parti o media.
- `DONE` implica che la successiva lettura della traccia osservi le parti create.
- I job non terminali rimasti oltre la soglia operativa devono essere rilevabili; una procedura di recupero può riportarli in coda o chiuderli in `ERROR` secondo una policy esplicita.
- Il file sorgente non viene eliminato mentre il job può essere riprovato.

## Osservabilità e gestione operativa

Log e metriche devono permettere di correlare `jobId`, `trackId`, tenant, stato e durata senza registrare il contenuto del PDF. Devono essere monitorati almeno:

- numero e anzianità dei job `TO_PROCESS` e `IN_PROGRESS`;
- tasso e cause aggregate dei job `ERROR`;
- tempo da accettazione a `DONE`;
- backlog, consumer e redelivery della coda `upload.files`;
- spazio disponibile nello storage media.

Un job in `TO_PROCESS` con coda ferma indica tipicamente indisponibilità di RabbitMQ o assenza di consumer. Un job bloccato in `IN_PROGRESS` richiede verifica del worker, delle risorse PDF e dello storage. Prima di un nuovo tentativo manuale va controllato che non sia ancora attivo un consumer sullo stesso job.

## Test richiesti

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
