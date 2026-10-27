# QR code ed etichette per l'inventario

La funzionalità estende la [gestione inventario tenant](inventory-management-spec.md), riusa il [catalogo centralizzato dei media](media-asset-spec.md) per le fotografie dei guasti e consegna le notifiche tramite il meccanismo descritto in [Generalizzazione della consegna delle notifiche interne](notification-delivery-generalization-spec.md).

## Stato del documento

ID catalogo: `inventory-qr-code`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

## Obiettivo

Associare a ogni oggetto inventariale un QR code stabile e stampabile. La scansione da smartphone apre un percorso mobile dell'applicazione che, dopo l'autenticazione, presenta esclusivamente dati e azioni consentiti all'utente:

- admin e super admin consultano il bene o lotto con stato di conservazione, guasti aperti, scadenze e prese visione mancanti, e da lì assegnano, gestiscono le riconsegne, aggiungono una fotografia, segnalano un guasto, stampano l'etichetta o ruotano il codice;
- l'assegnatario raggiunge rapidamente le proprie consegne relative a quell'oggetto, direttamente quella giusta se è una sola, e da lì completa la presa visione, richiede la riconsegna, aggiunge fotografie e segnala un guasto con le relative fotografie;
- gli altri utenti non ricevono informazioni sull'oggetto.

La funzionalità comprende generazione del codice, stampa di etichette singole o in fogli PDF, risoluzione sicura del collegamento, esperienza mobile e gestione strutturata delle segnalazioni di guasto.

## Fuori scope

- serializzare automaticamente ogni esemplare di un articolo con quantità maggiore di uno;
- sostituire il numero inventariale leggibile dall'uomo;
- rendere pubblica una scheda inventario senza autenticazione;
- usare il QR come credenziale, prova di possesso o autorizzazione;
- introdurre un'app mobile nativa;
- integrare uno scanner interno alla web app;
- supportare operazioni offline o accodare modifiche senza rete;
- stampare direttamente su una stampante termica tramite protocolli proprietari;
- supportare codici a barre lineari, NFC o RFID;
- automatizzare la variazione dello stato di conservazione sulla sola base di una segnalazione utente.

La scansione avviene con l'app fotocamera o con un lettore QR del dispositivo. L'eventuale scanner integrato nel browser è una fase successiva e richiederebbe permessi fotocamera, fallback e test dedicati.

## Decisioni principali

| Aspetto | Decisione |
| --- | --- |
| Entità identificata | un record `inventory_item`, quindi il bene o lotto già gestito da Taurus |
| Identificatore nel QR | UUID casuale dedicato, diverso da ID database e numero inventariale |
| Contenuto | URL HTTPS assoluto e versionato verso il frontend |
| Autenticazione | sempre obbligatoria prima della risoluzione |
| Autorizzazione | tenant e ruolo verificati dal backend; la UI non è un confine di sicurezza |
| Generazione | backend con matrice QR; rendering vettoriale nei PDF |
| Stampa | PDF server-side, layout singolo e foglio A4 ritagliabile |
| Persistenza PDF | nessuna; il documento è rigenerabile e può contenere codici revocati |
| Rotazione | esplicita, amministrativa e immediatamente invalidante per il vecchio codice |
| Guasti | entità dedicata, con stato, severità, quantità interessata, note e fotografie |
| Ricerca | solo PostgreSQL, tramite indice univoco; nessun indice OpenSearch |
| Audit scansioni | solo metriche aggregate; nessuna cronologia nominativa di ogni scansione |

## Bene, lotto e singola unità

`inventory_item` conserva `total_quantity` e rappresenta oggi un bene singolo oppure un lotto omogeneo. Non esiste un'identità persistente per ogni esemplare contenuto nel lotto.

Di conseguenza:

- viene generato un solo QR per `inventory_item`;
- copie della stessa etichetta possono essere applicate a più esemplari del lotto, ma conducono tutte allo stesso oggetto;
- assegnazione, riconsegna e guasto richiedono sempre la quantità interessata;
- Taurus non dichiara quale specifico esemplare fisico sia stato scansionato;
- inventario disponibile e quantità residue continuano a seguire le regole esistenti.

Una futura tracciatura per singolo seriale richiederà un'entità `inventory_unit`, una migrazione esplicita dei lotti e QR distinti. Non deve essere anticipata aggiungendo convenzioni non persistite al testo dell'etichetta.

## Formato e ciclo di vita del QR

### Identificatore pubblico

`qr_public_id` è un UUID versione 4 generato con un generatore crittograficamente sicuro. Ha almeno 122 bit casuali effettivi, non deriva da ID, tenant, numero inventariale o timestamp e non è riutilizzato.

Il valore è un localizzatore opaco, non un segreto. La sua conoscenza non concede accesso e ogni richiesta richiede un token valido. L'uso di un identificatore casuale impedisce comunque l'enumerazione banale degli oggetti.

### URL codificato

Il payload usa questa forma:

```text
https://app.example.org/inventory/scan/v1/550e8400-e29b-41d4-a716-446655440000
```

Regole:

- `v1` versiona il contratto del deep link, non la revisione dell'oggetto;
- il payload non contiene tenant, ID database, numero inventariale, nome, utente o firma;
- l'URL non contiene query string o fragment;
- in produzione lo schema deve essere HTTPS;
- host e prefisso applicativo provengono da configurazione backend attendibile, mai dagli header `Host` o `X-Forwarded-*` della richiesta di stampa;
- il valore configurato non può contenere credenziali, query string o fragment.

Viene introdotta la proprietà:

```yaml
application:
  inventory:
    qr:
      enabled: false
      public-base-url: https://app.example.org
      resolution-limit-per-minute: 60
      label-timeout-seconds: 30
```

In `application.yml` i valori si leggono da `INVENTORY_QR_ENABLED`, `INVENTORY_QR_PUBLIC_BASE_URL`, `INVENTORY_QR_RESOLUTION_LIMIT_PER_MINUTE` e `INVENTORY_QR_LABEL_TIMEOUT_SECONDS`.

L'avvio fallisce con un errore di configurazione chiaro se la generazione etichette è abilitata e l'URL non è valido. HTTP è ammesso soltanto per gli host di sviluppo locali `localhost`, `127.0.0.1` e `[::1]`. Il timeout delle etichette deve stare fra 1 e 300 secondi.

Il profilo `dev` abilita automaticamente la funzionalità e usa `http://localhost:4200` come base pubblica, così il frontend locale espone subito i comandi QR. `application-dev.yml` usa valori letterali: in sviluppo `INVENTORY_QR_ENABLED` e `INVENTORY_QR_PUBLIC_BASE_URL` non vengono lette. Gli altri profili mantengono il valore predefinito disabilitato e richiedono una configurazione esplicita dell'URL pubblico. Oltre al kill switch globale serve il flag per tenant `INVENTORY_QR` (vedi [Migrazione e rilascio](#migrazione-e-rilascio)).

### Emissione

- I nuovi oggetti ricevono il codice nella stessa transazione della creazione.
- La migrazione assegna un codice anche agli oggetti esistenti, inclusi quelli cancellati logicamente, così il vincolo `NOT NULL` rimane uniforme.
- Un oggetto cancellato non è mai risolvibile.
- Il riuso di un numero inventariale dopo una cancellazione crea un nuovo record e un nuovo codice; una vecchia etichetta non deve mai puntare al nuovo bene.
- La ristampa usa lo stesso codice e non cambia revisioni o prese visione.

### Rotazione

Admin e super admin possono ruotare il codice indicando una motivazione obbligatoria, per esempio etichetta smarrita, applicata al bene sbagliato o compromessa.

La rotazione:

1. blocca pessimisticamente l'oggetto;
2. genera un nuovo UUID;
3. incrementa `qr_version`;
4. registra autore, data e motivazione;
5. rende immediatamente non risolvibile il vecchio URL;
6. non modifica numero inventariale, assegnazioni o revisioni di presa visione;
7. invia una notifica amministrativa senza includere l'URL completo.

La conferma UI chiarisce che tutte le etichette precedenti smetteranno di funzionare. Non è previsto annullamento automatico: per tornare operativo occorre stampare il nuovo codice.

## Qualità grafica del QR

Il backend usa ZXing Core per produrre una `BitMatrix`; PDFBox disegna moduli vettoriali neri su fondo bianco. Non viene inserita nel PDF un'immagine raster ridimensionata.

Parametri:

- livello di correzione errore `Q`;
- quiet zone minima di quattro moduli su ogni lato;
- nessun logo o grafica sovrapposta al codice;
- contrasto nero su bianco;
- lato fisico minimo 24 mm;
- URL codificato in UTF-8;
- nessuna trasformazione o accorciamento tramite servizi esterni.

L'endpoint PNG amministrativo produce soltanto dimensioni predefinite di 256 o 512 pixel, senza parametro libero. La stampa raccomandata è al 100% senza “adatta alla pagina”.

## Contenuto dell'etichetta

Ogni etichetta mostra:

- denominazione breve del tenant;
- QR code;
- numero inventariale;
- nome dell'oggetto, troncato su due righe;
- suffisso di otto caratteri dell'UUID come riferimento diagnostico;
- dicitura “Scansiona con Taurus”.

Non mostra valore economico, quantità, condizione, assegnatario, indirizzo, dati fiscali o altre informazioni personali. Il numero inventariale resta leggibile se il codice è danneggiato.

Il renderer usa la stessa fonte autorevole dei dati tenant già impiegata dai report, ma non riusa l'intestazione completa di `TenantPdfHeaderService`: un'etichetta non è un report e lo spazio ridotto richiede il solo nome breve.

Il suffisso non è accettato come identificatore per le API e non costituisce un codice manuale. La ricerca manuale continua a usare il numero inventariale.

## Layout di stampa

### Etichetta singola

Il layout `SINGLE_62X40` genera una pagina da 62 × 40 mm, adatta al download e all'invio a un normale driver di stampa. Il PDF non contiene comandi specifici per stampanti termiche.

### Foglio A4

Il layout `A4_GRID_3X8` genera 24 celle ritagliabili su pagina A4 verticale:

- 3 colonne e 8 righe;
- margini esterni di 5 mm;
- spazi orizzontali di 3 mm e verticali di 3 mm;
- contenuto centrato in una safe area interna;
- indicatori di taglio facoltativi, disabilitati per impostazione predefinita.

La richiesta accetta `startCell` da 0 a 23 per riutilizzare un foglio parzialmente occupato. Le celle precedenti restano vuote soltanto sulla prima pagina; le pagine successive partono dalla prima cella.

Questo preset è pensato per fogli da ritagliare. La compatibilità con formati adesivi commerciali richiede preset separati e verificati fisicamente, non semplici variazioni CSS.

### Limiti

- massimo 100 oggetti diversi per richiesta; ogni oggetto compare una sola volta, altrimenti `400 inventory.labels.duplicate`;
- da 1 a 20 copie per oggetto;
- massimo `240 - startCell` etichette per `A4_GRID_3X8`, così il documento non supera 10 pagine;
- massimo 20 etichette, quindi 20 pagine, per `SINGLE_62X40`, che richiede `startCell` 0;
- massimo 10 pagine A4;
- ordinamento uguale a quello inviato dal client;
- nome file `etichette-inventario-YYYYMMDD-HHmm.pdf` nel `Content-Disposition`; il frontend salva comunque il download come `etichette-inventario.pdf`.

Il PDF viene generato in memoria e restituito come `application/pdf` con `Content-Disposition: attachment`. Non viene salvato in `media_asset`: non è un documento storico, è rigenerabile e una copia conservata potrebbe contenere codici successivamente ruotati.

La generazione ha un tempo massimo di 30 secondi, configurabile con `application.inventory.qr.label-timeout-seconds` (variabile `INVENTORY_QR_LABEL_TIMEOUT_SECONDS`, da 1 a 300). Gli oggetti sono letti in una sola query e trasformati in una proiezione immutabile: numero inventariale, nome, riferimento diagnostico e URL. Il rendering avviene poi fuori da ogni transazione, su un pool dedicato di thread `inventory-label-render-N`: un thread per CPU, almeno due, senza coda.

- Oltre il tempo massimo la richiesta riceve `503 inventory.labels.timeout` e il lavoro abbandonato termina senza effetti.
- Se tutti i renderer sono occupati, `503 inventory.labels.busy` arriva subito.

In entrambi i casi nessuno stato è cambiato e il client può riprovare.

## Flusso di stampa

### Da un singolo oggetto

1. L'amministratore apre la scheda inventario.
2. Seleziona “Stampa etichetta”.
3. Sceglie layout e numero di copie.
4. Taurus mostra un riepilogo con numero inventariale e nome.
5. Il backend genera il PDF usando il codice corrente.
6. Il browser scarica il documento.

### Stampa multipla

1. La lista inventario abilita selezione multipla.
2. Il comando “Genera etichette” apre il dialogo di configurazione.
3. Per ogni riga si può impostare il numero di copie.
4. Per A4 si può indicare la prima cella libera.
5. Il backend rilegge tutti gli oggetti nel tenant corrente; non accetta nomi o codici forniti dal client.
6. Oggetti inesistenti, cancellati o appartenenti a un altro tenant fanno fallire l'intera richiesta con RFC 7807.

La generazione è atomica: non restituisce un PDF parziale quando una voce non è valida.

## Flusso di scansione

### Ingresso e autenticazione

La rotta frontend pubblicamente raggiungibile è:

```text
/inventory/scan/v1/:publicId
```

“Pubblicamente raggiungibile” significa che il router può ricevere il deep link; il contenuto applicativo resta protetto. Se la sessione non è presente, il normale flusso Keycloak autentica l'utente e riporta alla rotta originale. Il parametro di ritorno è validato come percorso locale e non può diventare un open redirect.

Dopo il login il frontend invoca il resolver backend. Durante la risoluzione mostra uno stato di caricamento mobile e non visualizza il codice completo.

### Risoluzione per ruolo

| Profilo | Risultato |
| --- | --- |
| Admin o super admin | riepilogo dell'oggetto, quantità e azioni amministrative |
| Utente con una o più assegnazioni pertinenti | le proprie assegnazioni sull'oggetto; con una sola il frontend apre direttamente il dettaglio della consegna |
| Utente senza assegnazioni pertinenti | risposta generica non disponibile |
| Tenant errato, codice ignoto, ruotato o oggetto cancellato | stessa risposta generica non disponibile |
| Non autenticato | autenticazione, senza anticipare l'esistenza del codice |

Sono pertinenti le assegnazioni attive, parzialmente riconsegnate oppure storiche visibili nel normale inventario personale. Il backend calcola `allowedActions` ripetendo le condizioni dei comandi che il frontend apre:

| Profilo | Azione | Condizione |
| --- | --- | --- |
| amministratore | `ASSIGN` | quantità disponibile e nessun guasto `UNSAFE` aperto o preso in carico |
| amministratore | `RETURN` | quantità assegnata maggiore di zero |
| amministratore | `ADD_ITEM_PHOTO`, `REPORT_ISSUE`, `PRINT_LABEL`, `ROTATE_CODE` | sempre |
| assegnatario | `VIEW` | sempre |
| assegnatario | `COMPLETE_DECISION` | assegnazione aperta senza decisione sulla revisione corrente |
| assegnatario | `REQUEST_RETURN` | quantità residua non già coperta da richieste di riconsegna in attesa |
| assegnatario | `ADD_RETURN_PHOTO` | almeno una richiesta di riconsegna in attesa |
| assegnatario | `REPORT_ISSUE` | quantità residua maggiore di zero |
| assegnatario | `ADD_ISSUE_PHOTO` | almeno una segnalazione dell'assegnazione aperta o presa in carico |

La risposta amministrativa riporta anche:

- lo stato di conservazione;
- le assegnazioni scadute (`overdueAssignments`);
- le assegnazioni in scadenza entro `expiringWithinDays` giorni (`expiringAssignments`); la soglia è di 30 giorni, la stessa del badge delle scadenze;
- gli assegnatari con presa visione da completare (`pendingDecisions`).

Ogni consegna personale riporta stato di conservazione, data di scadenza e `decisionPending`.

Con il flag `INVENTORY_QR` spento per il tenant il resolver risponde `404` senza corpo.

Per evitare un oracolo di esistenza, `404` usa lo stesso problema e lo stesso messaggio per codice sconosciuto, tenant errato, vecchio codice, oggetto cancellato o utente senza diritto di visione. Il backend non restituisce un target `NO_ACCESS` contenente dettagli dell'oggetto.

### Schermata mobile

`InventoryScanComponent` usa una singola colonna.

Per admin e super admin presenta:

1. codice del tenant e numero inventariale;
2. nome, stato di conservazione e foto di anteprima, se presente;
3. quantità disponibili, assegnate e totali;
4. avviso di nuove assegnazioni bloccate da un guasto `UNSAFE`, oppure del numero di segnalazioni aperte;
5. avvisi di assegnazioni scadute, in scadenza e di prese visione da completare, quando presenti;
6. `Apri scheda completa` e un comando per ogni azione di `allowedActions`.

| Azione | Comando | Effetto |
| --- | --- | --- |
| `ASSIGN` | `Assegna` | apre la scheda sulla sezione `#assegna` |
| `RETURN` | `Gestisci riconsegna` | apre la scheda sulla sezione `#assegnazioni` |
| `ADD_ITEM_PHOTO` | `Aggiungi fotografia` | acquisisce una foto con la fotocamera o dalla galleria e la carica sull'oggetto |
| `REPORT_ISSUE` | `Segnala guasto` | apre il dialogo del guasto |
| `PRINT_LABEL` | `Stampa etichetta` | apre il dialogo di stampa con l'oggetto già selezionato |
| `ROTATE_CODE` | `Ruota codice` | apre il dialogo di rotazione con motivazione e conferma esplicita |

Nella scheda completa le sezioni `#codice-qr`, `#fotografie`, `#assegna`, `#assegnazioni` e `#segnalazioni` ricevono scroll e focus appena la pagina è caricata.

Dopo una rotazione la schermata resta aperta e non risolve di nuovo il vecchio codice, che risponderebbe `404`:

- un avviso spiega che il codice appena scansionato non funziona più;
- `Stampa etichetta` diventa `Stampa nuova etichetta`;
- `Ruota codice` scompare.

Per un assegnatario con una sola consegna pertinente il frontend apre direttamente `/inventory/assignments/{id}`, sostituendo la voce di cronologia. Con più consegne presenta il codice del tenant e l'elenco delle proprie consegne sull'oggetto. Ogni consegna mostra foto di anteprima, numero inventariale, nome, quantità in possesso, stato di conservazione, badge di scadenza ed evidenza della presa visione da completare. Offre `Apri consegna` e un comando per azione:

| Azione | Comando | Destinazione |
| --- | --- | --- |
| `COMPLETE_DECISION` | `Completa presa visione` | dettaglio della consegna, sezione `#presa-visione` |
| `REQUEST_RETURN` | `Richiedi riconsegna` | dettaglio della consegna, sezione `#riconsegna` |
| `ADD_RETURN_PHOTO` | `Foto di riconsegna` | dettaglio della consegna, sezione `#riconsegna` |
| `ADD_ISSUE_PHOTO` | `Foto della segnalazione` | dettaglio della consegna, sezione `#segnalazioni` |
| `REPORT_ISSUE` | `Segnala guasto` | dialogo del guasto sulla consegna |

Presa visione, richiesta di riconsegna e fotografie si completano nel dettaglio della consegna, con i form e le conferme già esistenti.

La scansione non crea o completa operazioni senza un'azione esplicita: la scelta di una fotografia ne avvia il caricamento, gli altri comandi aprono un dialogo o una sezione con le proprie conferme. Form, vincoli e messaggi riusano quelli dei flussi inventario esistenti.

## Segnalazione guasti

### Perché serve un'entità dedicata

Le note dell'oggetto e della riconsegna descrivono stati diversi e non rappresentano una segnalazione con ciclo di vita. Un guasto richiede presa in carico, risoluzione, notifiche, fotografie e audit; viene quindi introdotto `inventory_issue_report`.

### Modello dati

Tabella `inventory_issue_report`:

| Campo | Tipo | Regola |
| --- | --- | --- |
| `id` | `BIGINT` | chiave primaria |
| `item_id` | `BIGINT` | oggetto interessato, obbligatorio |
| `assignment_id` | `BIGINT` | obbligatorio per segnalazioni personali, facoltativo per admin |
| `reported_quantity` | `INTEGER` | maggiore di zero |
| `severity` | `VARCHAR(32)` | `MINOR`, `LIMITING`, `UNSAFE` |
| `description` | `VARCHAR(2000)` | obbligatoria dopo trim |
| `status` | `VARCHAR(32)` | `OPEN`, `ACKNOWLEDGED`, `RESOLVED`, `DISMISSED` |
| `resolution_notes` | `VARCHAR(2000)` | obbligatorie negli stati terminali |
| `acknowledged_at/by` | timestamp, stringa | valorizzati alla presa in carico |
| `resolved_at/by` | timestamp, stringa | valorizzati per stato terminale |
| `entity_version` | `BIGINT` | concorrenza ottimistica |
| campi audit comuni | vari | inclusi soft delete e autore |

Vincoli:

- `reported_quantity > 0`;
- per una segnalazione personale la quantità non supera il residuo della relativa assegnazione al momento della creazione; per una segnalazione amministrativa non supera la quantità totale dell'oggetto (`400 inventory.issue.quantityExceeded`);
- `assignment_id`, quando presente, deve appartenere allo stesso `item_id`;
- la tabella non ha `tenant_code`: l'isolamento è dato dallo schema del tenant;
- `ACKNOWLEDGED` richiede autore e data di presa in carico;
- `RESOLVED` e `DISMISSED` richiedono note, autore e data di chiusura;
- una segnalazione terminale non è modificabile né riapribile.

La tabella `inventory_issue_photo` collega la segnalazione a `media_asset` e conserva ordine e campi di audit. Categoria storage: `inventory-issue-photos`. Si applicano le stesse convalide delle altre fotografie inventario: JPEG o PNG, massimo 10 MB, 6000 × 6000 pixel e normalizzazione server-side. Il limite è 20 fotografie per segnalazione.

### Severità

| Severità | Significato | Effetto |
| --- | --- | --- |
| `MINOR` | difetto estetico o non bloccante | evidenza nella scheda |
| `LIMITING` | utilizzo compromesso o riparazione necessaria | priorità amministrativa alta |
| `UNSAFE` | possibile rischio per persone o bene | blocco prudenziale di nuove assegnazioni |

Un guasto `UNSAFE` aperto o preso in carico impedisce nuove assegnazioni dell'intero `inventory_item`. La scelta è conservativa perché Taurus non distingue le singole unità di un lotto. Non modifica automaticamente `condition_status`, non annulla le assegnazioni esistenti e non altera la quantità disponibile.

Il blocco termina soltanto quando non esistono altre segnalazioni `UNSAFE` non terminali. L'amministratore deve comunque valutare se aggiornare lo stato dell'oggetto a `TO_REPAIR` o `OUT_OF_SERVICE`.

### Transizioni

```text
OPEN ───────────────► ACKNOWLEDGED ─────► RESOLVED
  │                         │
  ├─────────────────────────┴───────────► DISMISSED
  └────────────────────────────────────► RESOLVED
```

Solo admin e super admin eseguono transizioni. Il passaggio a `ACKNOWLEDGED` registra chi ha preso in carico la segnalazione. La chiusura può includere un nuovo `condition_status`; se presente, aggiornamento del bene e chiusura del guasto avvengono nella stessa transazione e continuano ad applicarsi le regole sulle revisioni delle assegnazioni.

L'utente non può eliminare o correggere retroattivamente una segnalazione inviata. Un errore viene chiuso come `DISMISSED` con motivazione, preservando l'audit.

### Diritti sulla segnalazione

- Admin e super admin possono creare segnalazioni per qualsiasi oggetto del tenant, consultarle, aggiungere foto e cambiarne stato.
- Un utente può creare una segnalazione soltanto da una propria assegnazione visibile e può leggere le segnalazioni delle proprie assegnazioni.
- Archivist e user external seguono le stesse regole personali degli altri utenti; il ruolo non concede gestione globale.
- L'assegnatario allega fino a cinque foto già nel dialogo di creazione. Consulta poi le segnalazioni nel dettaglio della consegna e vi aggiunge foto finché la segnalazione è `OPEN` o `ACKNOWLEDGED`.
- Solo admin e super admin rimuovono una fotografia, con la procedura descritta in [GDPR](#gdpr).
- Nessun utente può leggere segnalazioni o foto riferite ad assegnazioni altrui.

## Modifiche al modello dell'oggetto

`inventory_item` riceve:

| Campo | Tipo | Regola |
| --- | --- | --- |
| `qr_public_id` | `UUID` | non nullo e univoco nello schema tenant |
| `qr_version` | `INTEGER` | non nullo, iniziale 1, maggiore di zero |
| `qr_issued_at` | `TIMESTAMPTZ` | non nullo |
| `qr_issued_by` | `VARCHAR(255)` | autore iniziale o della rotazione |

Le rotazioni sono conservate nella tabella immutabile `inventory_qr_rotation` con item, versione precedente e successiva, digest SHA-256 dei due codici, motivazione, autore e data. Non vengono conservati i vecchi UUID in chiaro e nessuna riga della cronologia può essere usata dal resolver.

Il resolver usa `InventoryItemRepository.findByQrPublicIdAndDeletedFalse`. Le tabelle non hanno `tenant_code`: l'isolamento è garantito dallo schema del tenant, come nel resto dell'inventario.

Il codice non viene inserito in `InventoryItemDTO` destinato alle liste e non viene accettato da `InventoryItemRequest`. La generazione e la rotazione sono esclusivamente server-side.

## Migrazione Liquibase

La migration tenant `20260906000000_inventory_qr_code.xml`, successiva a `20260818090000_add_inventory.xml`, esegue:

1. aggiunta temporaneamente nullable delle colonne QR;
2. backfill con UUID casuali per tutti gli oggetti esistenti;
3. valorizzazione versione e data di emissione;
4. applicazione dei vincoli `NOT NULL` e `qr_version > 0`;
5. indice univoco su `qr_public_id`;
6. creazione di `inventory_qr_rotation`, `inventory_issue_report` e `inventory_issue_photo`;
7. indici per oggetto, assegnazione, stato/severità e righe attive;
8. foreign key `RESTRICT` verso oggetto, assegnazione e `media_asset`;
9. vincolo univoco su oggetto e versione successiva della rotazione;
10. inclusione nel `tenant-master.xml`.

Il backfill usa una funzione UUID disponibile e verificata nella versione PostgreSQL supportata da Taurus. Se l'ambiente target non la espone, la migration usa un custom change Java testato; non sono ammessi UUID derivati da `random()` o hash prevedibili.

La migration tenant `20261027000000_notification_audience_excluded_user.xml` aggiunge `EXCLUDED_USER` ai valori ammessi dal vincolo `ck_notification_audience_type` di `notification_outbox_audience`, usato per escludere l'autore dalle notifiche. Il rollback elimina le righe con quel tipo e ripristina il vincolo precedente.

Non viene creata una migration OpenSearch. Il QR viene risolto esclusivamente sul database relazionale.

## API REST

### Risoluzione condivisa

```text
GET /api/inventory-scan/v1/{publicId}
```

L'endpoint è autenticato per tutti i ruoli ammessi all'inventario e viene dichiarato esplicitamente in `SecurityConfiguration` prima delle regole amministrative `/api/inventory/**` e del `denyAll` personale.

Risposta amministrativa esemplificativa:

```json
{
  "target": "ADMIN_ITEM",
  "itemId": 42,
  "inventoryNumber": "LEG-0012",
  "name": "Leggio pieghevole",
  "conditionStatus": "GOOD",
  "totalQuantity": 10,
  "assignedQuantity": 6,
  "availableQuantity": 4,
  "previewPhotoId": 15,
  "openIssueCount": 1,
  "unsafe": false,
  "overdueAssignments": 1,
  "expiringAssignments": 2,
  "expiringWithinDays": 30,
  "pendingDecisions": 1,
  "allowedActions": ["ASSIGN", "RETURN", "ADD_ITEM_PHOTO", "REPORT_ISSUE", "PRINT_LABEL", "ROTATE_CODE"],
  "assignments": []
}
```

Risposta personale esemplificativa:

```json
{
  "target": "OWN_ASSIGNMENTS",
  "allowedActions": [],
  "assignments": [
    {
      "assignmentId": 91,
      "inventoryNumber": "LEG-0012",
      "itemName": "Leggio pieghevole",
      "conditionStatus": "GOOD",
      "outstandingQuantity": 1,
      "status": "ACTIVE",
      "expirationDate": "2026-11-15",
      "decisionPending": true,
      "previewPhotoId": 15,
      "allowedActions": ["VIEW", "COMPLETE_DECISION", "REQUEST_RETURN", "REPORT_ISSUE"]
    }
  ]
}
```

I campi nulli sono omessi. `allowedActions` è calcolato dal backend e serve alla presentazione; ogni endpoint di comando ripete comunque tutte le autorizzazioni e le invarianti.

La risposta applica `Cache-Control: no-store` e `Referrer-Policy: no-referrer`. UUID malformato e codice non accessibile producono lo stesso `404` RFC 7807.

### Etichette e rotazione

```text
GET  /api/inventory/items/{id}/qr-code.png?size=256
POST /api/inventory/labels
POST /api/inventory/items/{id}/qr-code/rotate
```

Richiesta batch:

```json
{
  "layout": "A4_GRID_3X8",
  "startCell": 5,
  "showCutMarks": false,
  "entries": [
    { "itemId": 42, "copies": 2 },
    { "itemId": 77, "copies": 1 }
  ]
}
```

Rotazione:

```json
{
  "reason": "Etichetta applicata al bene sbagliato"
}
```

La risposta di rotazione contiene soltanto nuova versione e data; il client aggiorna l'anteprima richiedendo nuovamente il PNG.

### Guasti amministrativi

```text
GET  /api/inventory/items/{itemId}/issues
POST /api/inventory/items/{itemId}/issues
GET  /api/inventory/issues/{issueId}
PATCH /api/inventory/issues/{issueId}/status
POST /api/inventory/issues/{issueId}/photos
GET  /api/inventory/issue-photos/{photoId}
DELETE /api/inventory/issue-photos/{photoId}
```

`DELETE` rimuove una fotografia con la procedura descritta in [GDPR](#gdpr) e risponde `204`; una fotografia inesistente o già rimossa risponde `404`.

### Guasti personali

```text
GET  /api/user/inventory/assignments/{assignmentId}/issues
POST /api/user/inventory/assignments/{assignmentId}/issues
GET  /api/user/inventory/issues/{issueId}
POST /api/user/inventory/issues/{issueId}/photos
GET  /api/user/inventory/issue-photos/{photoId}
```

Gli endpoint personali vengono aggiunti puntualmente agli allowlist per metodo HTTP e percorso prima della regola finale `denyAll` su `/api/user/inventory/**`. L'elenco delle segnalazioni di un'assegnazione, dalla più recente, risponde `404` se l'assegnazione non appartiene al richiedente.

Gli endpoint di PNG, etichette, rotazione e guasti, amministrativi e personali, sono annotati con `@RequiresTenantFeature(TenantFeature.INVENTORY_QR)`: con il flag spento rispondono `403 tenantFeature.inventoryQr.disabled`.

### Contratti guasto

Creazione:

```json
{
  "reportedQuantity": 1,
  "severity": "LIMITING",
  "description": "La vite di bloccaggio non mantiene l'altezza."
}
```

Transizione amministrativa:

```json
{
  "status": "RESOLVED",
  "resolutionNotes": "Vite sostituita e serraggio verificato.",
  "itemConditionStatus": "GOOD",
  "version": 3
}
```

Tutte le validazioni producono RFC 7807. I conflitti di versione restituiscono `409 Conflict`; quantità o transizione non valide restituiscono `400 Bad Request`; risorse non visibili restituiscono `404 Not Found`.

## Backend

Componenti:

- `InventoryQrCodeService`: emissione, rotazione, URL e matrice QR;
- `InventoryScanRateLimiter`: limite di risoluzioni per utente e IP;
- `InventoryLabelService`: layout e generazione PDF/PNG, con pool di rendering dedicato e timeout;
- `InventoryScanService`: risoluzione tenant/ruolo e calcolo azioni;
- `InventoryIssueService`: segnalazioni, foto e loro rimozione, transizioni, notifiche e blocco sicurezza;
- `InventoryQrMetrics`: contatori e timer Micrometer a etichette chiuse di resolver, stampa e guasti;
- `InventoryIssueReportRepository` e `InventoryIssuePhotoRepository`;
- DTO separati per scan, stampa e guasti;
- estensione mirata di `InventoryService` per impedire assegnazioni con guasti `UNSAFE` aperti.

La generazione del QR non viene collocata nei controller. I renderer ricevono dati già autorizzati e non interrogano direttamente repository o token.

La transazione di rotazione usa lock sull'oggetto. La creazione o chiusura di un guasto `UNSAFE` usa lock sull'oggetto e controllo delle altre segnalazioni aperte per evitare finestre concorrenti nel blocco assegnazioni.

## Frontend Angular

Componenti:

- `InventoryScanComponent`, lazy-loaded e mobile-first;
- `InventoryLabelDialogComponent` per singola e multipla selezione;
- `InventoryQrRotateDialogComponent` per la rotazione con motivazione, aperto dalla scheda amministrativa;
- `InventoryIssueDialogComponent` per quantità, severità, descrizione e fino a cinque fotografie; è aperto dalla scansione, dalla scheda amministrativa e dal dettaglio della consegna;
- `InventoryIssueListComponent`:
  - nella scheda amministrativa offre, nella card di ogni segnalazione, caricamento e rimozione delle foto, presa in carico, note di chiusura, nuovo stato del bene, risoluzione e respingimento;
  - nel dettaglio della consegna (`assignmentId`) usa gli endpoint personali, mostra le segnalazioni dell'assegnazione e permette soltanto il caricamento delle foto;
- `focusInventoryFragment`, che porta scroll e focus sulla sezione indicata dal fragment nella scheda e nel dettaglio della consegna;
- metodi dedicati in `InventoryService` e `UserInventoryService` e un `InventoryScanService` frontend per il resolver.

La rotta di scansione accetta tutti i ruoli già ammessi alla sezione inventario; il resolver decide il contenuto. Le rotte amministrative esistenti restano protette da admin e super admin.

Nella scheda amministrativa `InventoryIssueListComponent` partecipa al ritmo verticale standard delle sezioni con una separazione di `2rem`, mantenendo la card “Segnalazioni guasto” visivamente distinta dalla “Zona pericolosa” conclusiva secondo il [Taurus Layout Standard](taurus-layout-standard.md).

### Esperienza mobile

- target tattili di almeno 44 × 44 CSS pixel;
- azione primaria sempre visibile senza menu contestuale;
- caricamento e upload con stato esplicito;
- acquisizione foto tramite `<input type="file" accept="image/jpeg,image/png" capture="environment">`, lasciando disponibile la scelta dalla galleria. Il file viene caricato appena selezionato nella scheda di scansione, per la foto dell'oggetto, e nelle liste delle segnalazioni, amministrativa e personale. Nel dialogo del guasto viene invece allegato prima dell'invio;
- nel dialogo del guasto:
  - le foto, al massimo cinque, sono verificate subito (JPEG o PNG, massimo 10 MB), mostrate in anteprima e rimovibili prima dell'invio;
  - dopo la creazione vengono caricate una alla volta;
  - se un caricamento fallisce, la segnalazione resta registrata e un avviso invita ad aggiungere le foto mancanti dalla segnalazione;
- nessuna dipendenza dalla fotocamera per aprire il QR;
- messaggi brevi che non espongono dettagli di autorizzazione.

### Accessibilità

- il QR mostrato nella scheda ha testo alternativo con numero inventariale, non con UUID;
- ogni azione ha etichetta testuale oltre all'icona;
- severità e stato non sono comunicati soltanto dal colore;
- dialoghi e messaggi sono navigabili da tastiera;
- dopo login o risoluzione il focus viene spostato sul titolo del risultato;
- errori di campo sono associati programmaticamente ai controlli;
- il PDF mantiene testo reale per tenant, numero e nome, mentre il QR è contenuto grafico.

## Notifiche

Gli eventi sono pubblicati tramite l'outbox generalizzata nella stessa transazione del cambiamento applicativo. Ogni gruppo di destinatari ha un evento distinto con il proprio `eventKey`, così la deduplicazione riguarda la coppia evento-destinatario. L'autore dell'azione non riceve mai la propria notifica.

| Evento | Destinatari | `eventKey` | Collegamento |
| --- | --- | --- | --- |
| guasto creato, da utente o da admin | admin e super admin, autore escluso | `inventory-issue:{id}:created:admins` | `/inventory/items/{itemId}` |
| guasto preso in carico | il segnalante, se non è l'autore della transizione | `inventory-issue:{id}:ACKNOWLEDGED:user:{segnalante}` | consegna per le segnalazioni personali, scheda per quelle amministrative |
| guasto risolto o respinto | admin e super admin, autore escluso | `inventory-issue:{id}:{stato}:admins` | `/inventory/items/{itemId}` |
| guasto personale risolto o respinto | l'assegnatario segnalante, se non è l'autore | `inventory-issue:{id}:{stato}:user:{segnalante}` | `/inventory/assignments/{assignmentId}` |
| codice QR ruotato | admin e super admin, autore escluso | `inventory-qr:{itemId}:version:{versione}:admins` | `/inventory/items/{itemId}` |

| Evento | Titolo | Messaggio |
| --- | --- | --- |
| creazione | “Nuova segnalazione inventario” | numero inventariale · severità · quantità |
| transizione | “Segnalazione inventario aggiornata” | numero inventariale · stato precedente → nuovo stato |
| rotazione | “Codice QR inventario ruotato” | numero inventariale · versione · motivazione |

Il segnalante è l'assegnatario per una segnalazione personale e l'amministratore che l'ha creata per una amministrativa. Quando una segnalazione amministrativa viene risolta o respinta, il segnalante è già tra i destinatari dell'evento agli amministratori.

L'esclusione dell'autore usa l'audience `EXCLUDED_USER` della [consegna generalizzata](notification-delivery-generalization-spec.md). L'utente viene tolto dai destinatari risolti dalle altre audience dello stesso evento. Se non resta nessuno, l'evento risulta consegnato senza destinatari e non viene ritentato: succede, per esempio, quando l'unico amministratore chiude una segnalazione.

Livelli e politiche:

- la creazione di una segnalazione `UNSAFE` usa `WARNING`, la severità più alta disponibile;
- le altre creazioni e tutte le transizioni usano `INFO` con politica `CONFIGURABLE`;
- la rotazione usa `WARNING` con politica `REQUIRED`.

La motivazione della rotazione è testo libero e nel messaggio viene ripulita: i caratteri `<` e `>` vengono rimossi, caratteri di controllo e a capo diventano spazi, il testo è troncato a 255 caratteri con `…` per rispettare i vincoli dell'outbox. Il messaggio non include fotografie, UUID completo o dati di altri assegnatari.

## Dashboard operativa

Il provider inventario della [dashboard operativa](operational-dashboard-spec.md) (`InventoryOperationProvider`) mostra ad admin e super admin, con QR attivo:

- `INVENTORY_UNSAFE_ISSUES`: numero di segnalazioni `UNSAFE` aperte o prese in carico, collegamento `/inventory?attention=issues-unsafe`;
- `INVENTORY_LIMITING_ISSUES`: numero di segnalazioni `LIMITING` aperte o prese in carico, collegamento `/inventory?attention=issues-limiting`.

I collegamenti aprono l'elenco inventario filtrato sugli oggetti con quei guasti. Le voci dei guasti precedono scadenze e altre attività inventario.

## Sicurezza e privacy

### Autorizzazione

- Ogni risoluzione parte dal tenant del token, non da dati nel QR.
- Il repository filtra il soft delete; il tenant è determinato dallo schema dedicato.
- Le API personali verificano `user_keycloak_id` dell'assegnazione contro il subject autenticato.
- L'azione dichiarata dal resolver non sostituisce i controlli del comando.
- I dati economici dell'oggetto non compaiono nella risposta personale o nell'etichetta.
- Le fotografie sono sempre scaricate tramite endpoint autorizzati, mai tramite path filesystem o URL statici.

### Abusi e logging

- Il formato UUID rende impraticabile l'enumerazione sequenziale.
- Il resolver applica un limite configurabile per utente e IP, inizialmente 60 richieste al minuto, con risposta `429` e `Retry-After`.
- Resolver, stampa, rotazione e guasti scrivono log strutturati a chiavi fisse, elencati in [Osservabilità](#osservabilità).
- L'autore compare solo come `actorHash` e il codice scansionato solo come `codeHash`: i primi 12 caratteri esadecimali dello SHA-256, che bastano a correlare le righe ma non a risalire al codice o all'utente.
- Motivazioni e descrizioni libere non entrano nei log. `LoggingAspect` esclude `InventoryScanResource`, quindi l'UUID completo non compare neppure nei log DEBUG del profilo `dev`.
- Le metriche hanno etichette chiuse (esito, gruppo di ruolo, layout, severità, stato): nessun tag con UUID, item ID, tenant o subject.
- Il frontend imposta `Referrer-Policy: no-referrer` sulla rotta di scansione.
- Analitiche e strumenti di tracciamento non ricevono il path completo della scansione.

Il rate limit è difesa aggiuntiva e non autorizza l'uso del QR come segreto.

### GDPR

Il codice è legato all'oggetto e non contiene dati personali. Le segnalazioni personali possono contenere autore e descrizioni libere; seguono quindi il processo di cancellazione inventario esistente:

- una richiesta GDPR con materiale residuo resta sospesa;
- dopo la completa riconsegna, l'identità del segnalante viene pseudonimizzata insieme allo storico inventario;
- descrizione tecnica, stato e fotografie del bene sono conservati per la gestione patrimoniale.

Una fotografia di guasto che ritrae persone o altri dati personali si rimuove dalla card della segnalazione nella scheda amministrativa, con il pulsante accanto alla foto e una conferma distruttiva, oppure con `DELETE /api/inventory/issue-photos/{photoId}`:

1. la riga `inventory_issue_photo` viene marcata come cancellata, con autore e data della modifica, e resta come traccia;
2. dopo il commit il file lascia lo storage se nessun'altra entità usa lo stesso `media_asset`;
3. segnalazione, descrizione e altre fotografie non cambiano;
4. l'operazione incrementa `taurus.inventory.issue.photo.erased` e scrive `inventory_issue_photo_erased` con identificativi tecnici e `actorHash`.

Solo admin e super admin rimuovono le foto: l'assegnatario che ne chiede la rimozione si rivolge a un amministratore. L'endpoint richiede il flag `INVENTORY_QR`. Se il tenant lo ha spento, l'amministratore lo riattiva temporaneamente per eseguire la rimozione.

## Osservabilità

`InventoryQrMetrics` registra metriche Micrometer con prefisso `taurus.inventory.` ed etichette chiuse, con valori in minuscolo. I nomi Prometheus seguono la conversione standard.

| Metrica | Tipo | Etichette | Nome Prometheus |
| --- | --- | --- | --- |
| `taurus.inventory.qr.resolution` | contatore | `outcome` (`resolved`, `not_found`, `rate_limited`, `disabled`), `role_group` (`admin`, `member`) | `taurus_inventory_qr_resolution_total` |
| `taurus.inventory.qr.resolution.duration` | timer | `outcome` | `taurus_inventory_qr_resolution_duration_seconds` |
| `taurus.inventory.label.generation` | contatore | `layout`, `outcome` (`done`, `rejected`, `timeout`, `busy`, `failed`) | `taurus_inventory_label_generation_total` |
| `taurus.inventory.label.generation.duration` | timer | `layout` | `taurus_inventory_label_generation_duration_seconds` |
| `taurus.inventory.label.count` | distribuzione con istogramma, solo per richieste con etichette | `layout` | `taurus_inventory_label_count` |
| `taurus.inventory.issue.created` | contatore | `severity` | `taurus_inventory_issue_created_total` |
| `taurus.inventory.issue.transition` | contatore | `from`, `to` | `taurus_inventory_issue_transition_total` |
| `taurus.inventory.issue.photo.erased` | contatore | nessuna | `taurus_inventory_issue_photo_erased_total` |

Log strutturati:

| Evento | Livello | Campi |
| --- | --- | --- |
| `inventory_qr_resolution_failed` | INFO | `outcome`, `roleGroup`, `codeHash` |
| `inventory_labels` | INFO per `done` e `rejected`, WARN per `timeout`, `busy` e `failed` | `layout`, `outcome`, `labels`, `durationMs`, `actorHash` |
| `inventory_qr_rotated` | INFO | `itemId`, `version`, `actorHash` |
| `inventory_issue_created` | INFO | `issueId`, `itemId`, `severity`, `personal`, `actorHash` |
| `inventory_issue_transition` | INFO | `issueId`, `itemId`, `severity`, `from`, `status`, `actorHash` |
| `inventory_issue_photo_erased` | INFO | `issueId`, `photoId`, `actorHash` |

Le risoluzioni riuscite non producono log: il contatore basta, e una riga per scansione diventerebbe una cronologia comportamentale. Il flag tenant spento e il rate limit sono contati nel resolver senza log.

Segnali da sorvegliare:

- crescita di `timeout` e `busy` in `taurus_inventory_label_generation_total`;
- picchi di `rate_limited` in `taurus_inventory_qr_resolution_total`;
- lo stesso `codeHash` ripetuto in `inventory_qr_resolution_failed`, segno di una vecchia etichetta ancora in uso.

Il conteggio corrente delle segnalazioni aperte resta una query tenant-scoped per UI e dashboard e non viene registrato come gauge globale, evitando enumerazione periodica degli schemi e tag ad alta cardinalità.

Non viene conservato un audit persistente di ogni scansione: produrrebbe dati comportamentali senza necessità funzionale.

## Prestazioni e affidabilità

- lookup QR tramite indice univoco, obiettivo p95 inferiore a 300 ms escluso login;
- risposta resolver compatta e senza caricamento dei binari delle fotografie;
- anteprima foto caricata separatamente e in modo lazy;
- generazione di 240 etichette entro 10 secondi su ambiente di staging di riferimento; il limite rigido è il timeout configurabile di 30 secondi;
- memoria e CPU controllate: limiti verificati prima di leggere gli oggetti, un solo rendering per thread del pool dedicato e `503` immediato a pool pieno;
- nessuna chiamata OpenSearch nel percorso di scansione;
- nessuna chiamata Keycloak remota per la normale risoluzione oltre la validazione token già prevista;
- nessuna transazione durante il rendering: il PDF viene generato dopo aver letto una proiezione immutabile dei dati autorizzati.

Se il PDF fallisce, nessuno stato dell'oggetto cambia. Se la notifica di un guasto non è immediatamente consegnabile, l'outbox conserva l'intenzione senza annullare la segnalazione.

## Gestione errori

| Caso | Risposta |
| --- | --- |
| QR ignoto, ruotato, non visibile o tenant errato | `404` generico |
| sessione assente | redirect al login, poi ritorno locale |
| rate limit superato | `429` con `Retry-After` |
| layout, copie o cella iniziale non validi | `400` RFC 7807 |
| uno degli oggetti batch non valido | `404`, nessun PDF parziale |
| oggetto ripetuto nel batch | `400 inventory.labels.duplicate` |
| generazione etichette oltre il tempo massimo | `503 inventory.labels.timeout`, nessun PDF |
| tutti i renderer delle etichette occupati | `503 inventory.labels.busy`, da riprovare |
| foto di guasto inesistente o già rimossa | `404` |
| guasto personale oltre quantità residua o amministrativo oltre la quantità totale | `400` |
| nuova assegnazione con guasto `UNSAFE` aperto | `409` con messaggio operativo |
| transizione guasto illegale | `400` |
| versione concorrente superata | `409` |
| foto non valida | `400` per immagine illeggibile o oltre 6000 × 6000, `413` oltre 10 MB, `415` per tipo non JPEG/PNG |
| flag `INVENTORY_QR` spento | resolver `404` senza corpo; altri endpoint `403 tenantFeature.inventoryQr.disabled` |

Il messaggio mobile offre sempre una via d'uscita: riprova, torna all'inventario personale o contatta un amministratore. Non invita l'utente a cambiare tenant rivelando quale sarebbe quello corretto.

## Test backend

La strategia è coperta da questi test:

| Test | Copertura |
| --- | --- |
| `InventoryQrCodeServiceTest` | UUID versione 4 opaco, rotazione con soli digest e vecchio codice invalido, messaggio di rotazione su una riga entro 255 caratteri, codici nascosti a funzionalità spenta |
| `InventoryScanServiceTest` | risposta amministrativa con quantità, avvisi e azioni; `ASSIGN` assente con `UNSAFE` aperto o quantità esaurita; azioni personali secondo lo stato; stesso `404` contato per ogni esito negativo; impronta breve stabile e non reversibile |
| `InventoryScanResourceTest` | flag spento con `404` senza corpo, `Cache-Control: no-store` e `Referrer-Policy: no-referrer`, `429` con `Retry-After` per utente e per indirizzo |
| `InventoryQrSecurityConfigurationTest` | regole per metodo e percorso di resolver, endpoint amministrativi e personali |
| `InventoryQrPropertiesValidationTest` | URL pubblico HTTPS o host locale; rifiuto di credenziali, query, fragment e HTTP remoto; intervallo del timeout delle etichette |
| `InventoryLabelServiceTest` | dimensioni fisiche, testo e assenza di dati sensibili, QR vettoriale renderizzato dal PDF e decodificato nell'URL atteso, `startCell` e paginazione, dieci pagine A4, limiti verificati prima della lettura, nessun PDF parziale, timeout, pool occupato, errori di rendering |
| `InventoryIssueServiceTest` | notifiche con quantità, destinatari ed esclusione dell'autore; quantità massima; assegnazioni e segnalazioni altrui come `404`; foto solo su segnalazioni proprie aperte; elenco personale; transizioni con note, versione e nuovo stato del bene; rimozione GDPR delle foto |
| `InventoryServiceTest` | emissione del QR prima del salvataggio di un nuovo oggetto, blocco delle assegnazioni con `UNSAFE` aperto |
| `InventoryErasureServiceTest` | pseudonimizzazione del segnalante con lo stesso pseudonimo delle assegnazioni, pratica sospesa con materiale residuo |
| `NotificationRecipientResolverTest`, `NotificationDispatcherTest`, `NotificationOutboxPublisherTest`, `NotificationOutboxPublisherIT` | audience `EXCLUDED_USER`, consegna senza destinatari e senza nuovi tentativi, rifiuto di un evento con sole esclusioni, persistenza dell'esclusione |
| `TenantSchemaProvisioningServiceIT` | colonne QR non nulle e tabelle di rotazione e guasti nello schema tenant |
| `TenantFeatureServiceTest` | combinazione del kill switch globale e del flag tenant |

Gli elenchi seguenti sono la checklist della strategia.

### QR e tenant

- generazione UUID alla creazione e backfill univoco;
- risoluzione amministrativa nel tenant corretto;
- risoluzione personale limitata alle proprie assegnazioni;
- stesso `404` per codice ignoto, ruotato, cancellato, tenant errato e non autorizzato;
- nessuna esposizione di valore economico nella risposta personale;
- vecchio codice invalido dopo rotazione e nuovo codice valido;
- cronologia immutabile della rotazione con motivazione e soli digest dei codici;
- ristampa senza rotazione;
- oggetto nuovo con numero inventariale riusato ma QR diverso;
- rate limit e assenza del codice completo nei log.

### Etichette

- payload URL esatto e versionato;
- quiet zone e correzione errore configurate;
- PDF apribile con PDFBox;
- dimensioni fisiche dei layout e numero celle;
- `startCell` sulla prima pagina;
- copie, ordine e paginazione;
- testo presente e dati sensibili assenti;
- generazione vettoriale del QR;
- limiti batch, timeout e atomicità;
- rifiuto di base URL non attendibile.

`InventoryLabelServiceTest` renderizza a 300 dpi la pagina del PDF, decodifica il QR con ZXing e verifica che l'URL risultante sia quello atteso. Il PDF di prova contiene solo dati fittizi e non viene conservato come artifact: la verifica è ripetuta a ogni build.

### Guasti

- creazione personale soltanto su propria assegnazione;
- quantità valida rispetto al residuo;
- creazione amministrativa senza assegnazione;
- transizioni consentite e terminalità;
- note di risoluzione obbligatorie;
- concorrenza ottimistica;
- upload e autorizzazione foto;
- blocco nuove assegnazioni con `UNSAFE` aperto;
- sblocco solo dopo la chiusura dell'ultimo `UNSAFE`;
- aggiornamento atomico opzionale di `condition_status`;
- notifiche outbox e deduplicazione;
- pseudonimizzazione GDPR.

## Test frontend

Le spec Karma/Jasmine affiancano i componenti:

| Spec | Copertura |
| --- | --- |
| `inventory-scan.component.spec.ts` | schermata amministrativa con le sole azioni ammesse, stato non disponibile senza dati, apertura diretta di una sola consegna, collegamenti alle sezioni, foto dell'oggetto, stato dopo la rotazione, guasto personale, stampa |
| `inventory-label-dialog.component.spec.ts` | copie, layout, limiti distinti, `startCell` e indicatori di taglio |
| `inventory-qr-rotate-dialog.component.spec.ts` | rotazione solo con motivazione e conferma esplicita |
| `inventory-issue-dialog.component.spec.ts` | quantità e descrizione, verifica, anteprima e rimozione delle foto, caricamento dopo la creazione, segnalazione conservata se una foto fallisce |
| `inventory-issue-list.component.spec.ts` | endpoint amministrativi o personali, caricamento delle foto, rimozione con conferma, transizioni con versione e note |
| `assignment-detail.component.spec.ts` | focus sulla sezione indicata dalla scansione, aggiornamento delle segnalazioni dopo una nuova segnalazione |
| `inventory-fragment.spec.ts` | scroll e focus sulla sezione del fragment |

La suite Playwright `e2e/specs/inventory-qr.spec.ts`, descritta in `e2e/README.md`, prova su un'istanza completa:

- ritorno alla scansione dopo il login;
- stesso stato non disponibile per un codice sconosciuto o di un altro tenant, con entrambi i profili;
- azioni amministrative secondo `allowedActions` e focus sulla sezione `#assegna`;
- apertura della consegna o dell'elenco delle consegne;
- uso a 320 px senza scroll orizzontale, target di 44 px e controllo axe WCAG 2.1 AA.

La rotazione resta fuori dalla suite perché invaliderebbe le etichette stampate dell'ambiente.

La checklist della strategia:

- ritorno alla rotta di scansione dopo autenticazione;
- schermata admin e schermata personale per i diversi resolver target;
- nessuna informazione mostrata nello stato non disponibile;
- una o più assegnazioni personali;
- azioni nascoste o disabilitate secondo `allowedActions` e stato locale;
- validazione copie, layout e `startCell`;
- download PDF e gestione errori;
- conferma forte della rotazione;
- creazione guasto e acquisizione foto da smartphone;
- gestione `409` per guasto concorrente o blocco sicurezza;
- navigazione tastiera, focus e annunci screen reader;
- viewport mobili da 320 px senza scroll orizzontale.

## Verifiche fisiche

Prima del rilascio vengono stampati campioni reali:

1. etichetta singola e foglio A4 al 100%;
2. carta comune e almeno un supporto adesivo compatibile con il preset;
3. scansione con almeno un dispositivo Android e uno iOS;
4. scansione a 30 cm, con luce ordinaria e con etichetta leggermente graffiata;
5. verifica del ritorno dopo login con sessione assente;
6. rotazione e prova che la vecchia etichetta non risolva più;
7. controllo che nessun testo venga tagliato con nomi lunghi.

Un layout non supera il collaudo soltanto perché il QR digitale è decodificabile: deve funzionare dopo stampa reale.

## Migrazione e rilascio

La funzionalità ha due interruttori, combinati da `TenantFeatureService`:

- il kill switch globale `application.inventory.qr.enabled`, `false` per impostazione predefinita, che richiede anche un URL pubblico valido;
- il flag per tenant `INVENTORY_QR` (colonna `tenants.inventory_qr_enabled`), attivo solo con `INVENTORY` acceso.

Il flag protegge gli endpoint di etichette, rotazione e guasti (`@RequiresTenantFeature`) e il resolver (`InventoryScanResource`); il frontend mostra i comandi QR solo con il flag attivo.

Prima di abilitarla:

1. eseguire backup PostgreSQL;
2. applicare Liquibase a tutti gli schemi tenant;
3. verificare unicità e completezza del backfill;
4. configurare e validare l'URL pubblico frontend ed eventualmente `INVENTORY_QR_LABEL_TIMEOUT_SECONDS`;
5. provare login e ritorno dal deep link dietro il reverse proxy reale;
6. stampare e scansionare un campione fisico;
7. verificare autorizzazioni con admin, user, user external e tenant errato;
8. verificare storage e download delle foto guasto;
9. monitorare errori resolver, tempi PDF e outbox notifiche con le metriche e i log di [Osservabilità](#osservabilità);
10. attivare il flag `INVENTORY_QR` sui tenant interessati.

La migrazione si applica comunque a tutti gli schemi tenant, anche a quelli con il flag spento.

### Stampa e sostituzione delle etichette

Ristampa o rotazione:

- un'etichetta rovinata, staccata o da duplicare si ristampa: il codice non cambia e le altre copie restano valide;
- un'etichetta smarrita, applicata al bene sbagliato o fotografata da chi non doveva si ruota: tutte le copie precedenti smettono di funzionare.

Stampa:

1. aprire `Stampa etichetta` dalla scheda, dalla scansione o dalla selezione multipla della lista;
2. scegliere `SINGLE_62X40` per una stampante di etichette o `A4_GRID_3X8` per un foglio, indicando la prima cella libera se il foglio è già usato;
3. stampare al 100%, senza “adatta alla pagina”, su supporto bianco opaco;
4. scansionare l'etichetta prima di applicarla e verificare che apra l'oggetto atteso.

Applicazione:

- applicare l'etichetta su una superficie piana, pulita e visibile, lontano da pieghe e zone di attrito;
- dopo una rotazione, rimuovere o coprire subito le etichette vecchie: la scansione di una vecchia etichetta mostra solo “Codice non disponibile”;
- se un `503 inventory.labels.busy` o `inventory.labels.timeout` si ripete, dividere la stampa in richieste più piccole e controllare i log `inventory_labels`.

## Rollback

Il rollback applicativo disabilita la feature flag e nasconde comandi e resolver. Le colonne QR e le tabelle guasti restano nel database: non vengono eliminate automaticamente e nessun codice viene riutilizzato.

Le etichette già stampate diventano temporaneamente non utilizzabili ma non espongono dati. Al ripristino della versione, gli stessi codici tornano validi salvo rotazioni effettuate nel frattempo.

Un rollback distruttivo delle tabelle guasto richiede esportazione e conservazione dello storico e non fa parte del deploy ordinario.

## Criteri di accettazione

1. Ogni oggetto, esistente o nuovo, possiede un UUID QR univoco e non prevedibile.
2. Il QR contiene soltanto un URL HTTPS versionato e nessun dato di tenant, oggetto o persona.
3. Una scansione anonima non rivela alcuna informazione prima del login.
4. Admin e super admin risolvono esclusivamente oggetti del tenant corrente.
5. Gli altri ruoli vedono soltanto le proprie assegnazioni pertinenti.
6. Codice ignoto, ruotato, cancellato, di altro tenant o non autorizzato produce lo stesso `404`.
7. Le azioni veloci rispettano le autorizzazioni degli endpoint esistenti e richiedono conferma.
8. La ristampa non cambia il codice; la rotazione invalida immediatamente tutte le vecchie etichette e lascia una cronologia immutabile.
9. Una nuova entità con numero inventariale riusato non eredita il vecchio QR.
10. Il PDF singolo e il foglio A4 rispettano dimensioni, quiet zone, limiti e ordinamento.
11. Almeno un QR estratto da un PDF di test viene decodificato automaticamente nell'URL atteso.
12. Le etichette non contengono valore, quantità, condizione o dati personali.
13. Il batch è atomico e rispetta i limiti distinti di 10 pagine A4 o 20 etichette singole.
14. Un utente può segnalare un guasto solo su una propria assegnazione e per una quantità valida.
15. Le fotografie dei guasti rispettano formato, dimensioni, tenant e proprietà.
16. Solo admin e super admin possono prendere in carico, risolvere o respingere un guasto.
17. Una segnalazione `UNSAFE` aperta blocca nuove assegnazioni fino alla chiusura dell'ultima segnalazione unsafe.
18. La chiusura opzionale con variazione della condizione è atomica e crea le revisioni richieste dalle regole inventario.
19. Notifiche e metriche non includono UUID completi o contenuti fotografici.
20. Il resolver non dipende da OpenSearch e usa un indice PostgreSQL.
21. La rotta mobile è utilizzabile a 320 px, da tastiera e con screen reader.
22. Il deep link ritorna correttamente dopo autenticazione senza introdurre open redirect.
23. Il rate limit non usa tag metrici ad alta cardinalità e restituisce `429` in modo controllato.
24. Il processo GDPR pseudonimizza l'autore senza perdere lo storico tecnico necessario.
25. Kill switch globale, flag per tenant, rollback e vecchie etichette hanno il comportamento documentato.

## Decisioni rinviate

- entità e QR distinti per singola unità serializzata;
- preset per fogli adesivi commerciali specifici;
- scanner QR integrato nell'applicazione;
- stampa diretta ZPL, EPL o AirPrint;
- supporto NFC/RFID;
- modalità offline con sincronizzazione differita;
- etichette pubbliche con informazioni di contatto in caso di ritrovamento;
- portale manutenzioni con fornitori, costi, ricambi e scadenze.

## Requisiti aperti

Nessuno: la specifica è coperta dal codice e dai test. La suite end-to-end richiede un'istanza completa con le variabili descritte in `e2e/README.md`; le [verifiche fisiche](#verifiche-fisiche) di stampa e scansione restano un collaudo manuale prima di ogni attivazione del flag su un tenant.
