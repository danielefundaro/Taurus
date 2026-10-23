# Gestione centralizzata dei media

## Stato del documento

ID catalogo: `media-asset`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

## Obiettivo

Taurus usa un catalogo tecnico centralizzato dei file gestiti dall'applicazione. Il contenuto binario resta sul filesystem; il database conserva esclusivamente metadati, stato e chiave relativa dello storage.

Il catalogo ha sostituito le informazioni tecniche prima duplicate tra `media`, `upload_job`, `inventory_item_photo` e `inventory_return_photo`. Lo usano tutti i file gestiti: pagine degli spartiti e versioni delle parti, PDF caricati, fotografie dell'inventario, fogli di affidamento, documenti degli atti di firma, allegati economici e file dell'onboarding.

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
- i PDF dei fogli di affidamento generati con successo vengono conservati sul filesystem e referenziati tramite `media_asset`.

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
| `name` | `VARCHAR(255)` | Obbligatorio, ereditato dalla tabella `media`: contiene il nome del file sanificato |
| `description` | `TEXT` | Facoltativa, ereditata dalla tabella `media` e non valorizzata dal caricamento |
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

Categorie usate dai servizi (la categoria deve rispettare `[a-z0-9][a-z0-9-]{0,63}`, altrimenti `400 media.category.invalid`):

- `uploads` per i PDF sorgente in attesa di elaborazione;
- `scores` per le pagine degli spartiti, generate dall'elaborazione o dalla modifica delle immagini;
- `score-versions` per le versioni MusicXML delle parti dell'[ascolto delle parti](track-score-playback-spec.md);
- `inventory` per le fotografie degli oggetti;
- `inventory-returns` per le fotografie delle riconsegne;
- `inventory-issue-photos` per le fotografie dei guasti segnalati con il QR;
- `inventory-reports` per i PDF del proforma inventario, rimosso: i media esistenti sono marcati come eliminati;
- `inventory-loan-sheets` per i fogli di affidamento generati;
- `signature-acts` per i documenti degli atti firmati elettronicamente (congelati, sigillati, firmati con CieSign) e le loro attestazioni, referenziati da `signature_document` e `signature_act`; restano finché il foglio non è definito o annullato e poi per il periodo di grazia della [firma elettronica](inventory-electronic-signature-spec.md#ciclo-di-vita-del-foglio);
- `finance` per gli allegati dei movimenti finanziari;
- `onboarding-imports` per i file XLSX e CSV dell'[onboarding](tenant-onboarding-import-spec.md), rimossi dalla sua retention.

Il nome originale non viene usato per determinare directory o nome fisico. L'estensione deriva dal tipo riconosciuto dal server per PNG, JPEG e PDF; per gli altri file è quella del nome originale, se composta da lettere minuscole e cifre, altrimenti `bin`. La chiave non può essere fornita o modificata dal client e deve essere normalizzata e verificata prima di ogni accesso al filesystem.

## Relazioni con le tabelle di dominio

`media_asset` conserva soltanto dati tecnici. Proprietà funzionali come ordine, anteprima, descrizione, validità e relazione con l'entità proprietaria rimangono nelle rispettive tabelle.

Relazioni:

| Tabella | Relazione verso `media_asset` | Informazioni che restano nel dominio |
|---|---|---|
| `inventory_item_photo` | `media_asset_id` obbligatorio | `item_id`, ordine e indicatore di anteprima |
| `inventory_return_photo` | `media_asset_id` obbligatorio | `return_id` e informazioni della riconsegna |
| `inventory_issue_photo` | `media_asset_id` obbligatorio | segnalazione di guasto |
| `sheet_music_media` | `media_asset_id` obbligatorio | `sheet_music_id` e ordine |
| `upload_job` | `source_media_asset_id` | stato e informazioni dell'elaborazione |
| `score_version` | `media_asset_id` obbligatorio | parte (`sheet_music_id`), numero, origine e stato della versione |
| `inventory_loan_sheet_export` | `media_asset_id`, azzerato quando il PDF viene rimosso | foglio di affidamento, richiedente e data |
| `signature_document` | `frozen_media_asset_id`, `signed_media_asset_id`, `sealed_media_asset_id` | documento dell'atto nelle sue versioni |
| `signature_act` | `attestation_media_asset_id` | attestazione dell'atto |
| `financial_movement_attachment` | `media_asset_id` obbligatorio | movimento, descrizione, validità e audit funzionale |
| `onboarding_import_job` | `source_media_asset_id` | job di importazione; il file resta referenziato finché `source_removed_at` è vuoto |

`MediaRepository.isUnreferenced` considera queste relazioni, escluse le righe cancellate logicamente, per decidere se un file è ancora in uso.

Non deve essere introdotta una relazione polimorfica `owner_type` più `owner_id`, perché non garantirebbe l'integrità referenziale. Ogni dominio usa una foreign key o una tabella di relazione specifica.

## Conservazione dei PDF inventario

Il proforma PDF dell'inventario è stato rimosso e sostituito dal foglio di affidamento, i cui PDF sono registrati in `inventory_loan_sheet_export`. Il changeset `20261010000000_drop_inventory_report_export.xml` elimina la tabella `inventory_report_export` e marca come eliminati i media `inventory-reports` non usati da altre tabelle: la pulizia dei file orfani li rimuove dal filesystem dopo il periodo di attesa configurato.

## Stati tecnici

Stati ammessi dal vincolo della tabella:

- `READY`: contenuto disponibile e verificato; è lo stato assegnato a ogni file salvato;
- `INVALID`: contenuto rifiutato o non valido, per esempio un file storico che non si riesce a leggere;
- `DELETED`: contenuto cancellato logicamente;
- `MIGRATION_PENDING`: record storico migrato, i cui metadati di integrità vengono verificati e completati alla prima lettura;
- `UPLOADING`, `PROCESSING` e `FAILED`: previsti per scritture ed elaborazioni non ancora concluse, ma non assegnati dal codice, perché il file viene salvato solo a contenuto completo.

Lo stato tecnico del file non sostituisce lo stato delle entità applicative. Ad esempio, lo stato di `upload_job` continua a descrivere il processo di importazione dello spartito.

Il contratto HTTP, le transizioni persistenti del job e la relativa esperienza nel dettaglio della traccia sono definiti in [Elaborazione asincrona dei PDF delle tracce](track-pdf-processing-spec.md).

## Processo di caricamento o generazione

Il servizio di dominio verifica autorizzazioni, dimensione e formato ammesso ed esegue l'eventuale normalizzazione, per esempio la conversione delle fotografie. Poi `MediaService.store`:

1. ricava il tenant dal contesto autenticato, oppure lo riceve esplicitamente da un processo di sistema insieme all'autore;
2. rifiuta un contenuto vuoto (`400 media.file.empty`) e una categoria non valida;
3. determina il MIME type dai primi byte del contenuto: PNG, JPEG e PDF sono riconosciuti, ogni altro file è salvato come `application/octet-stream` anche se il client dichiara un tipo diverso;
4. calcola dimensione e SHA-256 sul contenuto definitivo;
5. genera UUID, estensione e `storage_key` lato server;
6. scrive il file accanto alla destinazione con un nome temporaneo e lo sposta con un `ATOMIC_MOVE`, ripiegando su uno spostamento ordinario dove il filesystem non lo consente;
7. salva `media_asset` con stato `READY`; la relazione di dominio viene salvata dal chiamante nella stessa transazione.

Filesystem e database non condividono la stessa transazione. Se il salvataggio del record fallisce, il file viene cancellato subito; se la transazione del chiamante non va a buon fine, il file viene cancellato al termine della transazione. Un processo periodico elimina i file temporanei e i file orfani rimasti dopo errori non recuperabili (vedi [Cancellazione e pulizia](#cancellazione-e-pulizia)).

## Download e sicurezza

- Il download avviene tramite l'identificativo applicativo, mai tramite un path fornito dal client.
- Prima della lettura vengono verificati tenant, relazione con l'entità proprietaria e autorizzazioni.
- Tramite `/api/media` super admin, admin e archivista leggono ogni media del tenant. Gli altri ruoli leggono solo le pagine delle parti che vedono nel dettaglio della traccia: `ROLE_USER` quelle delle tracce `COMPLETE` e `PUBLIC`, `ROLE_USER_EXTERNAL` quelle delle tracce `PUBLIC`, in entrambi i casi solo per le parti associate a uno dei propri strumenti. Ogni altro media risponde `404`; allegati economici, fotografie e PDF dell'inventario si scaricano dagli endpoint del proprio dominio.
- `storage_key` e path fisico non sono URL pubblici.
- Il path risolto deve rimanere all'interno della directory del tenant.
- Il nome originale viene sanificato prima dell'uso nell'header `Content-Disposition`.
- I log non devono contenere il contenuto del file né esporre inutilmente il path fisico completo.

## Cancellazione e pulizia

- La rimozione di una relazione di dominio non determina automaticamente la cancellazione fisica.
- Il dominio che rimuove una relazione chiama `MediaService.deleteIfUnreferenced`: se nessuna relazione attiva usa più il media, il record passa a `DELETED` e il file viene cancellato dopo il commit.
- La cancellazione del record è sempre logica: nessun processo elimina fisicamente le righe di `media_asset`.
- `MediaStorageCleanupScheduler` (`application.media.cleanup-cron`, default `0 30 3 * * *`, disattivabile con `application.media.cleanup-enabled`) esegue ogni giorno su ogni tenant attivo `MediaStorageCleanupService`, che elimina i file temporanei più vecchi di `temporary-file-hours` (24), i file più vecchi di `orphan-file-hours` (168) che non corrispondono a un media non cancellato e le ricevute di modifica delle pagine più vecchie di `track-page-edit-receipt-days` (7).
- Tutte le operazioni di pulizia restano confinate alla directory del tenant.

## Migrazione

La migrazione `20260831000000_media_asset.xml`:

1. rinomina la tabella `media` in `media_asset` e la colonna `media_id` delle relazioni in `media_asset_id`;
2. aggiunge `storage_key` e i metadati tecnici, convertendo i path assoluti in chiavi relative; un path esterno alla directory del tenant riceve una chiave `legacy-unresolved/…`, che non corrisponde a un file leggibile;
3. crea i record `media_asset` per le fotografie di oggetti e riconsegne e collega `upload_job` al media sorgente;
4. marca i record storici `MIGRATION_PENDING`: esistenza, dimensione, tipo e SHA-256 del file vengono verificati e completati alla prima lettura, oppure il record passa a `INVALID`;
5. rende obbligatori metadati e foreign key dopo il backfill.

I PDF del proforma inventario, collegati in un primo tempo tramite `inventory_report_export`, sono stati poi rimossi (vedi [Conservazione dei PDF inventario](#conservazione-dei-pdf-inventario)).

## Fuori scope della specifica

- salvataggio di BLOB o altri contenuti binari nel database;
- object storage o provider diversi dal filesystem;
- deduplicazione dei file;
- condivisione di file tra tenant;
- URL pubblici diretti verso lo storage;
- campo generico per metadati arbitrari.

