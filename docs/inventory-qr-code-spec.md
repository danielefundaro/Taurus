# QR code ed etichette per l'inventario

La funzionalità estende la [gestione inventario tenant](inventory-management-spec.md), riusa il [catalogo centralizzato dei media](media-asset-spec.md) per le fotografie dei guasti e consegna le notifiche tramite il meccanismo descritto in [Generalizzazione della consegna delle notifiche interne](notification-delivery-generalization-spec.md).

## Stato del documento

ID catalogo: `inventory-qr-code`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

## Obiettivo

Associare a ogni oggetto inventariale un QR code stabile e stampabile. La scansione da smartphone apre un percorso mobile dell'applicazione che, dopo l'autenticazione, presenta esclusivamente dati e azioni consentiti all'utente:

- admin e super admin possono consultare il bene o lotto, assegnarlo, gestire una riconsegna, aggiungere fotografie e registrare un guasto;
- l'assegnatario può raggiungere rapidamente le proprie consegne relative a quell'oggetto, richiedere una riconsegna e segnalare un guasto;
- gli altri utenti non ricevono informazioni sull'oggetto.

La funzionalità comprende generazione del codice, stampa di etichette singole o in fogli PDF, risoluzione sicura del collegamento, esperienza mobile e gestione strutturata delle segnalazioni di guasto.

## Fuori scope

- serializzare automaticamente ogni esemplare di un articolo con quantità maggiore di uno;
- sostituire il numero inventariale leggibile dall'uomo;
- rendere pubblica una scheda inventario senza autenticazione;
- usare il QR come credenziale, prova di possesso o autorizzazione;
- introdurre un'app mobile nativa;
- integrare nella prima versione uno scanner interno alla web app;
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
| Ricerca | PostgreSQL autorevole; il codice non viene indicizzato in OpenSearch |
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
```

L'avvio fallisce con un errore di configurazione chiaro se la generazione etichette è abilitata e l'URL non è valido. HTTP è ammesso soltanto per host di sviluppo locali.

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

- massimo 100 oggetti diversi per richiesta;
- da 1 a 20 copie per oggetto;
- massimo `240 - startCell` etichette per `A4_GRID_3X8`, così il documento non supera 10 pagine;
- massimo 20 etichette, quindi 20 pagine, per `SINGLE_62X40`;
- timeout di generazione 30 secondi;
- massimo 10 pagine A4;
- ordinamento uguale a quello inviato dal client;
- nome file `etichette-inventario-YYYYMMDD-HHmm.pdf`.

Il PDF viene generato in memoria entro un limite controllato e restituito come `application/pdf` con `Content-Disposition: attachment`. Non viene salvato in `media_asset` né in `inventory_report_export`: non è un report storico, è rigenerabile e una copia conservata potrebbe contenere codici successivamente ruotati.

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
| Utente con una propria assegnazione pertinente | scheda dell'assegnazione e azioni personali |
| Utente con più assegnazioni pertinenti | elenco da cui scegliere l'assegnazione |
| Utente senza assegnazioni pertinenti | risposta generica non disponibile |
| Tenant errato, codice ignoto, ruotato o oggetto cancellato | stessa risposta generica non disponibile |
| Non autenticato | autenticazione, senza anticipare l'esistenza del codice |

Sono pertinenti le assegnazioni attive, parzialmente riconsegnate oppure storiche visibili nel normale inventario personale. Le azioni operative sono abilitate solo quando quantità residua e stato le consentono.

Per evitare un oracolo di esistenza, `404` usa lo stesso problema e lo stesso messaggio per codice sconosciuto, tenant errato, vecchio codice, oggetto cancellato o utente senza diritto di visione. Il backend non restituisce un target `NO_ACCESS` contenente dettagli dell'oggetto.

### Schermata mobile

`InventoryScanComponent` usa una singola colonna e presenta:

1. tenant e numero inventariale;
2. foto di anteprima, solo se autorizzata;
3. nome e stato di conservazione;
4. quantità pertinenti al ruolo;
5. avvisi per scadenza, presa visione o guasti aperti;
6. azioni rapide consentite;
7. collegamento alla scheda completa.

Per admin e super admin le azioni sono:

- assegna;
- gestisci riconsegna;
- aggiungi fotografia dell'oggetto;
- segnala guasto;
- apri scheda completa;
- stampa o ruota il QR.

Per un assegnatario le azioni sono:

- apri o completa la presa visione;
- richiedi riconsegna;
- segnala guasto;
- aggiungi fotografie a una riconsegna o segnalazione esistente;
- apri dettaglio dell'assegnazione.

La scansione non crea o completa operazioni senza un'ulteriore conferma. Form, vincoli e messaggi riusano quelli dei flussi inventario esistenti.

## Segnalazione guasti

### Perché serve un'entità dedicata

Le note dell'oggetto e della riconsegna descrivono stati diversi e non rappresentano una segnalazione con ciclo di vita. Un guasto richiede presa in carico, risoluzione, notifiche, fotografie e audit; viene quindi introdotto `inventory_issue_report`.

### Modello dati

Tabella `inventory_issue_report`:

| Campo | Tipo | Regola |
| --- | --- | --- |
| `id` | `BIGINT` | chiave primaria |
| `tenant_code` | `VARCHAR(255)` | obbligatorio e sempre filtrato |
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
- per una segnalazione personale la quantità non supera il residuo della relativa assegnazione al momento della creazione;
- `assignment_id`, quando presente, deve appartenere allo stesso `item_id` e tenant;
- `ACKNOWLEDGED` richiede autore e data di presa in carico;
- `RESOLVED` e `DISMISSED` richiedono note, autore e data di chiusura;
- una segnalazione terminale non è modificabile né riapribile nella prima versione.

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
- Un utente può creare una segnalazione soltanto da una propria assegnazione visibile e può leggere la segnalazione così creata.
- Archivist e user external seguono le stesse regole personali degli altri utenti; il ruolo non concede gestione globale.
- L'assegnatario può aggiungere foto finché la segnalazione è `OPEN` o `ACKNOWLEDGED`.
- Nessun utente può leggere segnalazioni o foto riferite ad assegnazioni altrui.

## Modifiche al modello dell'oggetto

`inventory_item` riceve:

| Campo | Tipo | Regola |
| --- | --- | --- |
| `qr_public_id` | `UUID` | non nullo e univoco nello schema tenant |
| `qr_version` | `INTEGER` | non nullo, iniziale 1, maggiore di zero |
| `qr_issued_at` | `TIMESTAMPTZ` | non nullo |
| `qr_issued_by` | `VARCHAR(255)` | autore iniziale o della rotazione |

Le rotazioni sono conservate nella tabella immutabile `inventory_qr_rotation` con item, tenant, versione precedente e successiva, digest SHA-256 dei due codici, motivazione, autore e data. Non vengono conservati i vecchi UUID in chiaro e nessuna riga della cronologia può essere usata dal resolver.

Il repository espone una ricerca dedicata equivalente a `findByQrPublicIdAndTenantCodeAndDeletedFalse`. Anche se ogni tenant usa uno schema separato, il filtro `tenant_code` resta difesa in profondità coerente con l'inventario attuale.

Il codice non viene inserito in `InventoryItemDTO` destinato alle liste e non viene accettato da `InventoryItemRequest`. La generazione e la rotazione sono esclusivamente server-side.

## Migrazione Liquibase

Una nuova migration tenant, successiva a `20260818090000_add_inventory.xml`, esegue:

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
  "openIssueCount": 1,
  "unsafe": false,
  "allowedActions": ["ASSIGN", "RETURN", "ADD_ITEM_PHOTO", "REPORT_ISSUE", "PRINT_LABEL", "ROTATE_CODE"]
}
```

Risposta personale esemplificativa:

```json
{
  "target": "OWN_ASSIGNMENTS",
  "assignments": [
    {
      "assignmentId": 91,
      "inventoryNumber": "LEG-0012",
      "itemName": "Leggio pieghevole",
      "outstandingQuantity": 1,
      "status": "ACTIVE",
      "allowedActions": ["VIEW", "REQUEST_RETURN", "REPORT_ISSUE"]
    }
  ]
}
```

`allowedActions` è calcolato dal backend e serve alla presentazione; ogni endpoint di comando ripete comunque tutte le autorizzazioni e le invarianti.

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
```

### Guasti personali

```text
POST /api/user/inventory/assignments/{assignmentId}/issues
GET  /api/user/inventory/issues/{issueId}
POST /api/user/inventory/issues/{issueId}/photos
GET  /api/user/inventory/issue-photos/{photoId}
```

Gli endpoint personali vengono aggiunti puntualmente agli allowlist per metodo HTTP e percorso prima della regola finale `denyAll` su `/api/user/inventory/**`.

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

Componenti previsti:

- `InventoryQrCodeService`: emissione, rotazione, URL e matrice QR;
- `InventoryLabelService`: layout e generazione PDF/PNG;
- `InventoryScanService`: risoluzione tenant/ruolo e calcolo azioni;
- `InventoryIssueService`: segnalazioni, foto, transizioni e blocco sicurezza;
- `InventoryIssueReportRepository` e `InventoryIssuePhotoRepository`;
- DTO separati per scan, stampa e guasti;
- estensione mirata di `InventoryService` per impedire assegnazioni con guasti `UNSAFE` aperti.

La generazione del QR non viene collocata nei controller. I renderer ricevono dati già autorizzati e non interrogano direttamente repository o token.

La transazione di rotazione usa lock sull'oggetto. La creazione o chiusura di un guasto `UNSAFE` usa lock sull'oggetto e controllo delle altre segnalazioni aperte per evitare finestre concorrenti nel blocco assegnazioni.

## Frontend Angular

Componenti previsti:

- `InventoryScanComponent`, lazy-loaded e mobile-first;
- `InventoryLabelDialogComponent` per singola e multipla selezione;
- `InventoryIssueDialogComponent` per quantità, severità, descrizione e foto successive;
- `InventoryIssueListComponent` nella scheda amministrativa;
- `InventoryIssueStatusDialogComponent` per presa in carico e chiusura;
- metodi dedicati in `InventoryService` e `UserInventoryService` oppure un piccolo `InventoryScanService` frontend condiviso.

La rotta di scansione accetta tutti i ruoli già ammessi alla sezione inventario; il resolver decide il contenuto. Le rotte amministrative esistenti restano protette da admin e super admin.

### Esperienza mobile

- target tattili di almeno 44 × 44 CSS pixel;
- azione primaria sempre visibile senza menu contestuale;
- caricamento e upload con stato esplicito;
- acquisizione foto tramite `<input type="file" accept="image/jpeg,image/png" capture="environment">`, lasciando disponibile la scelta dalla galleria;
- anteprima e possibilità di rimuovere il file prima dell'invio;
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

Gli eventi sono pubblicati tramite l'outbox generalizzata nella stessa transazione del cambiamento applicativo.

| Evento | Destinatari | Contenuto minimo |
| --- | --- | --- |
| guasto creato da utente | admin e super admin | oggetto, quantità, severità, autore |
| guasto amministrativo creato | admin e super admin escluso l'attore | oggetto e severità |
| guasto preso in carico | segnalante, se ancora disponibile | oggetto e amministratore |
| guasto risolto o respinto | segnalante, admin e super admin | esito e collegamento alla segnalazione |
| codice QR ruotato | admin e super admin escluso l'attore | oggetto, versione e motivazione |

Una segnalazione `UNSAFE` usa severità notifica alta. Il messaggio non include fotografie, UUID completo o dati di altri assegnatari. Gli `eventKey` includono ID segnalazione, transizione e destinatario per garantire deduplicazione.

## Dashboard operativa

Quando sarà implementata la dashboard descritta in `operational-dashboard-spec.md`, il provider inventario includerà:

- numero di segnalazioni `UNSAFE` aperte o prese in carico;
- numero di segnalazioni `LIMITING` aperte;
- deep link alla lista guasti filtrata.

Le segnalazioni `UNSAFE` precedono scadenze e altre attività inventario. Questa integrazione non è prerequisito per rilasciare QR ed etichette, ma il contratto della dashboard deve essere aggiornato nello stesso ciclo se la dashboard esiste già al momento dello sviluppo.

## Sicurezza e privacy

### Autorizzazione

- Ogni risoluzione parte dal tenant del token, non da dati nel QR.
- Il repository filtra tenant e soft delete anche in presenza di schema tenant dedicato.
- Le API personali verificano `user_keycloak_id` dell'assegnazione contro il subject autenticato.
- L'azione dichiarata dal resolver non sostituisce i controlli del comando.
- I dati economici dell'oggetto non compaiono nella risposta personale o nell'etichetta.
- Le fotografie sono sempre scaricate tramite endpoint autorizzati, mai tramite path filesystem o URL statici.

### Abusi e logging

- Il formato UUID rende impraticabile l'enumerazione sequenziale.
- Il resolver applica un limite configurabile per utente e IP, inizialmente 60 richieste al minuto, con risposta `429` e `Retry-After`.
- I log applicativi non registrano URL o UUID completi; usano un hash troncato soltanto per correlazione tecnica temporanea.
- Le metriche non usano UUID, item ID, tenant o subject come tag.
- Il frontend imposta `Referrer-Policy: no-referrer` sulla rotta di scansione.
- Analitiche e strumenti di tracciamento non ricevono il path completo della scansione.

Il rate limit è difesa aggiuntiva e non autorizza l'uso del QR come segreto.

### GDPR

Il codice è legato all'oggetto e non contiene dati personali. Le segnalazioni personali possono contenere autore e descrizioni libere; seguono quindi il processo di cancellazione inventario esistente:

- una richiesta GDPR con materiale residuo resta sospesa;
- dopo la completa riconsegna, l'identità del segnalante viene pseudonimizzata insieme allo storico inventario;
- descrizione tecnica, stato e fotografie del bene possono essere conservati se necessari alla gestione patrimoniale, dopo verifica che non contengano dati personali non necessari;
- le foto possono essere rimosse o oscurate da un amministratore tramite la procedura di erasure quando contengono persone o altri dati personali.

## Osservabilità

Metriche Micrometer a cardinalità limitata:

- `taurus_inventory_qr_resolution_total{outcome,role_group}`;
- `taurus_inventory_qr_resolution_seconds{outcome}`;
- `taurus_inventory_label_generation_total{layout,outcome}`;
- `taurus_inventory_label_generation_seconds{layout}`;
- `taurus_inventory_label_count{layout}` come distribution summary;
- `taurus_inventory_issue_created_total{severity}`;
- `taurus_inventory_issue_transition_total{from,to}`;

Il conteggio corrente delle segnalazioni aperte resta una query tenant-scoped per UI e dashboard; nella prima versione non viene registrato come gauge globale, evitando enumerazione periodica degli schemi e tag ad alta cardinalità.

Log strutturati:

- successo o fallimento di stampa con conteggio, layout, durata e attore;
- rotazione con item ID interno, versione e attore, mai UUID completo o testo libero della motivazione;
- creazione e transizione guasto con issue ID, item ID, severità e stato;
- fallimento di risoluzione con outcome generico e hash troncato.

Non viene conservato un audit persistente di ogni scansione: produrrebbe dati comportamentali senza necessità funzionale.

## Prestazioni e affidabilità

- lookup QR tramite indice univoco, obiettivo p95 inferiore a 300 ms escluso login;
- risposta resolver compatta e senza caricamento dei binari delle fotografie;
- anteprima foto caricata separatamente e in modo lazy;
- generazione di 240 etichette entro 10 secondi su ambiente di staging di riferimento;
- memoria massima controllata e rifiuto preventivo delle richieste oltre limite;
- nessuna chiamata OpenSearch nel percorso di scansione;
- nessuna chiamata Keycloak remota per la normale risoluzione oltre la validazione token già prevista;
- transazioni brevi: il PDF viene generato dopo aver letto una proiezione immutabile dei dati autorizzati.

Se il PDF fallisce, nessuno stato dell'oggetto cambia. Se la notifica di un guasto non è immediatamente consegnabile, l'outbox conserva l'intenzione senza annullare la segnalazione.

## Gestione errori

| Caso | Risposta |
| --- | --- |
| QR ignoto, ruotato, non visibile o tenant errato | `404` generico |
| sessione assente | redirect al login, poi ritorno locale |
| rate limit superato | `429` con `Retry-After` |
| layout, copie o cella iniziale non validi | `400` RFC 7807 |
| uno degli oggetti batch non valido | `404`, nessun PDF parziale |
| guasto personale oltre quantità residua | `400` |
| nuova assegnazione con guasto `UNSAFE` aperto | `409` con messaggio operativo |
| transizione guasto illegale | `400` |
| versione concorrente superata | `409` |
| foto non valida | `400` o `413` secondo il limite violato |
| timeout di generazione PDF | `503` controllato, nessun file persistito |

Il messaggio mobile offre sempre una via d'uscita: riprova, torna all'inventario personale o contatta un amministratore. Non invita l'utente a cambiare tenant rivelando quale sarebbe quello corretto.

## Test backend

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

Un test di integrazione decodifica almeno un QR renderizzato dal PDF e verifica che l'URL risultante sia quello atteso. La pipeline conserva come artifact un PDF campione soltanto nei test, privo di dati reali.

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

## Piano di implementazione

### Fase 1 — Identità e resolver

1. Migration delle colonne QR e backfill.
2. Configurazione e validazione del public base URL.
3. Servizio QR e lookup tenant-scoped.
4. Endpoint resolver, regole Security e test di isolamento.
5. Rotta mobile con stati di caricamento e non disponibile.

### Fase 2 — Stampa

1. Dipendenza ZXing e renderer vettoriale.
2. PNG amministrativo.
3. PDF singolo e `A4_GRID_3X8`.
4. Dialogo per stampa singola e multipla.
5. Limiti, test PDF e collaudo fisico.

### Fase 3 — Azioni rapide

1. Collegamento ai dialoghi esistenti di assegnazione e riconsegna.
2. Upload fotografia amministrativa.
3. Calcolo backend delle azioni consentite.
4. Gestione aggiornata dei dati dopo ogni comando.

### Fase 4 — Segnalazioni guasto

1. Tabelle, service, API e autorizzazioni.
2. Form mobile e fotografie via `media_asset`.
3. Transizioni amministrative e blocco `UNSAFE`.
4. Notifiche outbox, metriche e GDPR.

### Fase 5 — Rotazione e consolidamento

1. Rotazione con motivazione e audit.
2. Integrazione opzionale con dashboard operativa.
3. Test end-to-end e di sicurezza.
4. Documentazione operativa per stampa e sostituzione etichette.
5. Abilitazione progressiva per tenant.

## Migrazione e rilascio

La funzionalità è protetta dalla proprietà `application.inventory.qr.enabled`, inizialmente `false` negli ambienti esistenti.

Prima di abilitarla:

1. eseguire backup PostgreSQL;
2. applicare Liquibase a tutti gli schemi tenant;
3. verificare unicità e completezza del backfill;
4. configurare e validare l'URL pubblico frontend;
5. provare login e ritorno dal deep link dietro il reverse proxy reale;
6. stampare e scansionare un campione fisico;
7. verificare autorizzazioni con admin, user, user external e tenant errato;
8. verificare storage e download delle foto guasto;
9. monitorare errori resolver, tempi PDF e outbox notifiche.

La prima versione usa un flag applicativo globale. L'abilitazione per singolo tenant è rinviata: tutti gli schemi devono quindi essere migrati e verificati prima di attivare la funzionalità in produzione.

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
25. Feature flag globale, rollback e vecchie etichette hanno il comportamento documentato.

## Decisioni rinviate

- entità e QR distinti per singola unità serializzata;
- preset per fogli adesivi commerciali specifici;
- scanner QR integrato nell'applicazione;
- stampa diretta ZPL, EPL o AirPrint;
- supporto NFC/RFID;
- modalità offline con sincronizzazione differita;
- etichette pubbliche con informazioni di contatto in caso di ritrovamento;
- portale manutenzioni con fornitori, costi, ricambi e scadenze.
