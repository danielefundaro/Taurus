# Gestione centralizzata dei media

## Stato del documento

Specifica approvata e implementata per i flussi applicativi esistenti. La migrazione mantiene la compatibilità con i file storici tramite uno stato tecnico transitorio, descritto di seguito.

Il documento definisce il modello adottato per i file gestiti da Taurus.

## Obiettivo

Taurus deve adottare un catalogo tecnico centralizzato dei file gestiti dall'applicazione. Il contenuto binario resta sul filesystem; il database conserva esclusivamente metadati, stato e chiave relativa dello storage.

Il catalogo sostituirà progressivamente le informazioni tecniche duplicate tra `media`, `upload_job`, `inventory_item_photo` e `inventory_return_photo` e sarà riutilizzato dagli allegati finanziari e dai PDF inventario conservati.

## Decisioni approvate

- la tabella centrale si chiama `media_asset`;
- `media_asset` è tenant-scoped e risiede nello schema del tenant;
- nessun contenuto binario viene salvato nel database;
- il filesystem resta l'unico provider di storage supportato;
- non viene introdotto alcun campo `storage_provider`;
- non viene introdotto un campo generico `metadata` o `metadata JSONB`;
- nel database viene salvata una chiave relativa, non il path fisico assoluto;
- `mime_type` è l'unico campo per il tipo MIME: non viene mantenuto un duplicato `content_type`;
- SHA-256 è usato per integrità e diagnostica, non per imporre deduplicazione;
- file identici possono essere caricati più volte, sia nello stesso tenant sia in tenant differenti;
- i PDF inventario generati con successo vengono conservati sul filesystem e referenziati tramite `media_asset`.

## Isolamento tenant

Ogni record e ogni file appartengono esclusivamente al tenant nel cui contesto sono stati creati.

- La tabella `media_asset` risiede nello schema del tenant.
- Il filesystem mantiene una directory radice distinta per tenant.
- Ogni operazione di creazione, lettura, aggiornamento o cancellazione richiede un `TenantContext` valido.
- Non sono consentite ricerche globali per SHA-256 o per nome del file.
- Non è prevista deduplicazione fisica o logica tra tenant.
- Un tenant non può rilevare, referenziare o riutilizzare un file appartenente a un altro tenant.
- Il tenant non viene inserito nella chiave relativa perché è già determinato dal contesto applicativo e dalla directory radice dello storage.

## Tabella `media_asset`

Campi previsti:

| Campo | Tipo indicativo | Vincoli e significato |
|---|---|---|
| `id` | `BIGINT` | Chiave primaria |
| `storage_key` | `VARCHAR(2048)` | Obbligatoria e univoca nello schema tenant |
| `original_filename` | `VARCHAR(500)` | Nome ricevuto dal client o assegnato al file generato |
| `mime_type` | `VARCHAR(255)` | Tipo verificato o determinato dal server |
| `file_extension` | `VARCHAR(32)` | Estensione normalizzata e determinata dal server |
| `file_size` | `BIGINT` | Dimensione effettiva del contenuto salvato |
| `sha256` | `CHAR(64)` | Digest del contenuto definitivo |
| `status` | `VARCHAR(32)` | Stato tecnico del file |
| `deleted` | `BOOLEAN` | Cancellazione logica |
| `insert_by` | `VARCHAR(255)` | Utente o processo che ha creato il record |
| `insert_date` | `TIMESTAMP WITH TIME ZONE` | Data di creazione |
| `edit_by` | `VARCHAR(255)` | Ultimo autore della modifica |
| `edit_date` | `TIMESTAMP WITH TIME ZONE` | Data dell'ultima modifica |
| `entity_version` | `BIGINT` | Versione per optimistic locking |

Vincoli minimi:

- `storage_key`, `original_filename`, `mime_type`, `file_size`, `sha256` e `status` sono obbligatori;
- `file_size` non può essere negativo;
- `sha256` deve contenere esattamente 64 caratteri esadecimali minuscoli;
- `storage_key` è univoca soltanto nello schema tenant;
- non deve esistere un vincolo univoco su `sha256`.

## Naming convention dello storage

Il path fisico viene composto esclusivamente lato server:

```text
<application.base-path>/<tenant-normalizzato>/<storage-key>
```

La chiave relativa segue la convenzione:

```text
<categoria>/<media-uuid>/<sha256>.<estensione>
```

Esempio:

```text
inventory/550e8400-e29b-41d4-a716-446655440000/a84c70f1c2eac12d9c832a55e42f7804d2f748a04f70ce54c9f945f888a3c892.jpg
```

Con `application.base-path=D:\data` il path fisico diventa:

```text
D:\data\<tenant>\inventory\550e8400-e29b-41d4-a716-446655440000\a84c70f1c2eac12d9c832a55e42f7804d2f748a04f70ce54c9f945f888a3c892.jpg
```

Categorie iniziali:

- `uploads` per i file sorgente in attesa di elaborazione;
- `scores` per le pagine e i media degli spartiti;
- `inventory` per le fotografie degli oggetti;
- `inventory-returns` per le fotografie delle riconsegne;
- `inventory-reports` per i PDF inventario generati;
- `financial-attachments` per gli allegati dei movimenti finanziari.

Il nome originale non viene usato per determinare directory o nome fisico. L'estensione deriva dal contenuto riconosciuto dal server. La chiave non può essere fornita o modificata dal client e deve essere normalizzata e verificata prima di ogni accesso al filesystem.

## Relazioni con le tabelle di dominio

`media_asset` conserva soltanto dati tecnici. Proprietà funzionali come ordine, anteprima, descrizione, validità e relazione con l'entità proprietaria rimangono nelle rispettive tabelle.

Relazioni target:

| Tabella | Relazione verso `media_asset` | Informazioni che restano nel dominio |
|---|---|---|
| `inventory_item_photo` | `media_asset_id` obbligatorio | `item_id`, ordine e indicatore di anteprima |
| `inventory_return_photo` | `media_asset_id` obbligatorio | `return_id` e informazioni della riconsegna |
| `sheet_music_media` | `media_asset_id` obbligatorio | `sheet_music_id` e ordine |
| `upload_job` | `source_media_asset_id` | stato e informazioni dell'elaborazione |
| `inventory_report_export` | `media_asset_id` obbligatorio per un'esportazione riuscita | utente richiesto, filtri, richiedente e data |
| `financial_movement_attachment` | `media_asset_id` obbligatorio | movimento, descrizione, validità e audit funzionale |

Non deve essere introdotta una relazione polimorfica `owner_type` più `owner_id`, perché non garantirebbe l'integrità referenziale. Ogni dominio usa una foreign key o una tabella di relazione specifica.

## Conservazione dei PDF inventario

Ogni PDF inventario generato con successo viene salvato nel filesystem nella categoria `inventory-reports` e registrato in `media_asset`.

`inventory_report_export` conserva il riferimento `media_asset_id` e i dati di audit della generazione. Dimensione e SHA-256 sono proprietà del relativo `media_asset` e non devono essere duplicati nella tabella di export una volta completata la migrazione.

Un PDF non deve essere registrato come esportazione riuscita se la scrittura del file o la creazione del relativo `media_asset` non è completata correttamente.

## Stati tecnici

Stati iniziali:

- `UPLOADING`: caricamento o scrittura non ancora completati;
- `PROCESSING`: elaborazione tecnica in corso;
- `READY`: contenuto disponibile e verificato;
- `INVALID`: contenuto rifiutato o non valido;
- `FAILED`: scrittura o elaborazione fallita;
- `DELETED`: contenuto cancellato logicamente.
- `MIGRATION_PENDING`: record storico migrato, i cui metadati di integrità vengono verificati e completati alla prima lettura.

Lo stato tecnico del file non sostituisce lo stato delle entità applicative. Ad esempio, lo stato di `upload_job` continua a descrivere il processo di importazione dello spartito.

## Processo di caricamento o generazione

1. Recuperare il tenant esclusivamente dal contesto autenticato.
2. Verificare autorizzazioni, dimensione e formato ammesso.
3. Scrivere il contenuto in un'area temporanea interna alla directory del tenant.
4. Determinare il MIME type dal contenuto senza fidarsi del solo valore inviato dal client.
5. Eseguire l'eventuale normalizzazione prevista dal dominio.
6. Calcolare dimensione e SHA-256 sul contenuto definitivo.
7. Generare UUID, estensione e `storage_key` lato server.
8. Spostare atomicamente il file nella posizione definitiva quando il filesystem lo consente.
9. Salvare `media_asset` e la relazione di dominio.
10. Impostare lo stato `READY` solo al completamento dell'intera operazione.

Filesystem e database non condividono la stessa transazione. Se la persistenza nel database fallisce dopo la scrittura, il servizio deve tentare la cancellazione compensativa del file. Un processo periodico deve eliminare file temporanei e file orfani rimasti dopo errori non recuperabili.

## Download e sicurezza

- Il download avviene tramite l'identificativo applicativo, mai tramite un path fornito dal client.
- Prima della lettura vengono verificati tenant, relazione con l'entità proprietaria e autorizzazioni.
- `storage_key` e path fisico non sono URL pubblici.
- Il path risolto deve rimanere all'interno della directory del tenant.
- Il nome originale viene sanificato prima dell'uso nell'header `Content-Disposition`.
- I log non devono contenere il contenuto del file né esporre inutilmente il path fisico completo.

## Cancellazione e pulizia

- La rimozione di una relazione di dominio non determina automaticamente la cancellazione fisica.
- Un file può essere eliminato soltanto quando non è più referenziato da alcuna tabella di dominio.
- La cancellazione applicativa è inizialmente logica.
- Un processo di garbage collection elimina successivamente file e record non più utilizzati secondo le regole di conservazione del dominio.
- Tutte le operazioni di pulizia devono rimanere confinate alla directory del tenant.

## Strategia di migrazione prevista

1. Rinominare ed estendere l'attuale tabella `media` come `media_asset`.
2. Aggiungere i nuovi campi e la colonna `storage_key`.
3. Convertire i path assoluti esistenti in chiavi relative, rifiutando quelli esterni alla directory gestita del tenant.
4. Creare record `media_asset` per i file referenziati da `inventory_item_photo` e `inventory_return_photo`.
5. Conservare i PDF inventario generati e collegare `inventory_report_export` tramite `media_asset_id`.
6. Collegare `upload_job` al media sorgente.
7. Aggiornare upload, elaborazione, download, cancellazione e cancellazione GDPR.
8. Verificare esistenza, dimensione e SHA-256 di tutti i file migrati.
9. Rendere obbligatorie le nuove foreign key dopo il backfill.
10. Rimuovere path e metadati duplicati soltanto al termine della validazione.

## Fuori scope della specifica

- salvataggio di BLOB o altri contenuti binari nel database;
- object storage o provider diversi dal filesystem;
- deduplicazione dei file;
- condivisione di file tra tenant;
- URL pubblici diretti verso lo storage;
- campo generico per metadati arbitrari.

