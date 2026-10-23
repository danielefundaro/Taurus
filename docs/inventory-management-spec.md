# Gestione inventario tenant

## Stato del documento

ID catalogo: `inventory-management`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

La consegna delle notifiche interne descritte in questa specifica segue `docs/notification-delivery-generalization-spec.md`.

## Obiettivo

La funzionalità introduce un inventario separato da album, tracce e dall'anagrafica degli strumenti musicali assegnabile a utenti e tracce. L'inventario rappresenta beni fisici di proprietà del tenant: strumenti musicali, divise, leggii, libretti e altro materiale.

Non è prevista una tassonomia di categorie: il nome e la descrizione identificano il tipo di oggetto. L'unica qualificazione è il regime di comodato (`loan_type`), richiesto dal [foglio di consegna e affidamento](inventory-loan-sheet-spec.md): non classifica gli oggetti, ma indica se il bene è concesso con scadenza o in comodato precario. Ogni dato e ogni ricerca sono isolati per tenant.

## Ruoli e autorizzazioni

| Operazione | Utente autenticato | Admin | Super admin |
| --- | ---: | ---: | ---: |
| Visualizzare il proprio materiale | sì | sì | sì |
| Accettare o negare una propria assegnazione | sì | sì | sì |
| Avviare una propria riconsegna | sì | sì | sì |
| Scaricare i propri fogli di affidamento | sì | sì | sì |
| Gestire oggetti, foto e assegnazioni | no | sì | sì |
| Consultare inventario di un utente | no | sì | sì |
| Avviare una riconsegna per un utente | no | sì | sì |
| Chiudere una riconsegna | no | sì | sì |
| Gestire richieste GDPR sospese | no | sì | sì |

Gli endpoint personali sono sotto `/api/user/inventory/**`; quelli amministrativi sotto `/api/inventory/**`. Le regole Spring Security autorizzano esplicitamente soltanto le operazioni self-service previste e negano tutte le altre chiamate sul namespace personale. Ogni risorsa personale è inoltre verificata rispetto all'identità ricavata dal token.

L'inventario è una funzionalità per tenant: flag `INVENTORY`, colonna `tenants.inventory_enabled`. Le resource amministrative e personali sono annotate con `@RequiresTenantFeature(TenantFeature.INVENTORY)`: con il flag spento rispondono `403 tenantFeature.inventory.disabled`. Nel frontend la rotta `/inventory` usa `tenantFeatureGuard` e la voce di menu è nascosta. QR code, foglio di affidamento e firma elettronica hanno flag propri (`INVENTORY_QR`, `INVENTORY_LOAN_SHEET`, `INVENTORY_SIGNATURE`), attivi solo con `INVENTORY` acceso; la firma richiede anche il foglio.

## Modello dati relazionale

PostgreSQL è l'unico archivio dell'inventario. Non esiste una proiezione OpenSearch: la ricerca è una query JPQL su PostgreSQL (`InventoryItemRepository`).

Ogni tabella `inventory_*`, incluse tabelle tecniche, revisioni, decisioni, riconsegne, fotografie ed esportazioni, contiene sempre i campi di audit `deleted`, `insert_date`, `insert_by`, `edit_date` ed `edit_by`.

### Oggetto inventario

Tabella `inventory_item`:

- dati di audit: `deleted`, `insert_date`, `insert_by`, `edit_date`, `edit_by`;
- controllo di concorrenza ottimistico tramite `entity_version`;
- nessuna colonna `tenant_code`: l'isolamento è garantito dallo schema del tenant (`20260820000002_remove_tenant_columns.xml`);
- `inventory_number` obbligatorio e univoco, senza distinzione tra maiuscole e minuscole, tra gli oggetti attivi (indice parziale `uq_inventory_item_number` su `lower(inventory_number)` con `deleted = false`, `20260821000000_allow_inventory_number_reuse.xml`); dopo una cancellazione logica il numero può essere riutilizzato, mantenendo invariati i riferimenti storici all'oggetto eliminato;
- `name` obbligatorio;
- `description`;
- `total_quantity`, intero non negativo;
- `estimated_unit_value`, valore stimato unitario;
- `currency`, codice ISO 4217 di tre caratteri, obbligatorio insieme al valore;
- `condition_status`: `NEW`, `EXCELLENT`, `GOOD`, `FAIR`, `TO_REPAIR`, `OUT_OF_SERVICE`;
- `condition_notes`;
- `brand_model` e `serial_number` (matricola), facoltativi;
- `loan_type`: `FIXED_TERM` (strumento musicale o accessorio, comodato con scadenza) o `PRECARIOUS` (divisa, accessorio o altro bene, comodato precario); facoltativo, obbligatorio per inserire il bene in un foglio di affidamento e non modificabile finché il bene è in un foglio emesso.

La quantità disponibile è calcolata come quantità totale meno la somma delle quantità ancora consegnate. La quantità totale non può essere ridotta sotto il totale già in carico agli utenti.

### Fotografie dell'oggetto

Tabella `inventory_item_photo` con riferimento al media (`media_asset_id`), ordine di visualizzazione (`display_order`) e indicatore di anteprima (`preview`). Nome, tipo, dimensione, digest SHA-256 e chiave di storage sono nel record `media_asset`. La prima fotografia caricata diventa l'anteprima; se l'anteprima viene rimossa, passa alla prima fotografia rimasta. Riordino e scelta dell'anteprima creano una nuova revisione come le altre modifiche alle foto.

Regole applicate:

- massimo 10 MB per fotografia;
- massimo 20 fotografie per oggetto o singola procedura di riconsegna;
- solo JPEG e PNG;
- WebP non supportato;
- dimensione massima 6000 × 6000 pixel;
- normalizzazione server-side del contenuto;
- soft delete per preservare la verificabilità delle revisioni storiche.

I file sono salvati nello storage filesystem configurato dall'applicazione, sotto un percorso separato per tenant. Fotografie degli oggetti e delle riconsegne (`inventory_return_photo.media_asset_id`) usano la tabella tenant-scoped `media_asset`, descritta nella [specifica centralizzata dei media](media-asset-spec.md), che conserva nel database soltanto metadati e chiave relativa dello storage; nessun contenuto binario viene salvato nel database.

### Assegnazione

Tabella `inventory_assignment`:

- riferimento all'oggetto;
- snapshot identificativo dell'utente: index, Keycloak id, nome e cognome;
- ordine di visualizzazione;
- quantità assegnata e quantità riconsegnata;
- data di assegnazione;
- descrizione;
- data di scadenza, specifica della singola assegnazione e quindi differenziabile per utente: facoltativa, salvo per gli oggetti in comodato con scadenza, per i quali è obbligatoria e deve rispettare le regole di durata fin dall'assegnazione (vedi [Scadenza all'assegnazione](inventory-loan-sheet-spec.md#scadenza-allassegnazione));
- stato `ACTIVE`, `PARTIALLY_RETURNED` o `RETURNED`;
- revisione corrente;
- accessori o taglia (`accessories`), facoltativi; il valore concordato del foglio di affidamento è il valore unitario stimato dell'oggetto e non un dato dell'assegnazione;
- foglio di affidamento collegato (`loan_sheet_id`) e assegnazione sostituita (`replaces_assignment_id`), gestiti dal foglio;
- dati di audit e versione ottimistica.

Per un'assegnazione collegata a un foglio non annullato la scadenza si modifica solo con un rinnovo, la quantità non è modificabile e l'assegnazione non può essere eliminata. Se il socio ha un foglio emesso, una nuova assegnazione vi viene collegata come consegna successiva e deve rispettarne le regole di durata. Il dettaglio è nella [specifica del foglio](inventory-loan-sheet-spec.md).

La creazione e l'aggiornamento usano un lock pessimista sull'oggetto per impedire sovra-assegnazioni in richieste concorrenti.

L'interfaccia evidenzia la scadenza dell'assegnazione con un badge rosso dal giorno successivo alla data indicata, giallo nei 30 giorni precedenti (data compresa) e neutro per le scadenze più lontane. Il badge è informativo e non modifica stato o disponibilità dell'oggetto.

Un processo giornaliero crea notifiche interne per l'assegnatario e per gli utenti attivi con ruolo admin o super admin: 30 giorni prima, 7 giorni prima, il giorno della scadenza e una sola volta dopo il superamento. Sono considerate soltanto assegnazioni attive o parzialmente riconsegnate con quantità residua. La tabella `inventory_expiration_notice` registra assegnazione, data e soglia per impedire duplicati; una variazione della data avvia un nuovo ciclo di promemoria.

### Revisioni e presa visione

Ogni assegnazione ha revisioni immutabili nella tabella `inventory_assignment_revision`. La revisione contiene:

- numero progressivo;
- causa: assegnazione iniziale, aggiornamento oggetto, aggiornamento assegnazione, modifica foto, riemissione dopo un rifiuto o rinnovo del comodato (`RENEWAL`);
- snapshot JSON canonico;
- hash SHA-256 dello snapshot;
- autore e data.

Lo snapshot include la versione dello schema (`schemaVersion`), numero inventariale, nome, descrizione, valore unitario, valuta, stato e note di conservazione, data di scadenza, quantità e descrizione dell'assegnazione, nonché id, digest, ordine (`displayOrder`) e indicatore di anteprima (`preview`) delle fotografie. Marca e modello, matricola, regime di comodato e accessori sono inclusi solo se valorizzati, così gli hash delle revisioni precedenti restano riproducibili. Sono esclusi la quantità totale, i dati di audit e gli attributi tecnici non percepibili dall'utente.

La presa visione è per singola assegnazione e singola revisione. L'utente invia l'hash visualizzato insieme alla scelta. Il server rifiuta hash non correnti, utenti non proprietari e seconde decisioni sulla stessa revisione.

La scelta è:

- `ACCEPTED`, senza motivazione obbligatoria;
- `REJECTED`, sempre accompagnata da una motivazione non vuota.

La decisione è memorizzata in `inventory_assignment_decision` con data, identità autenticata e un hash autenticato derivato dalla revisione, dalla scelta e dall'utente. Con la firma elettronica disattivata non rappresenta una firma elettronica: è una presa visione autenticata. Quando il tenant attiva la [firma elettronica](inventory-electronic-signature-spec.md#presa-visione-con-codice), la scelta si conferma con un codice inviato via e-mail, anche per più assegnazioni insieme, e `signature_evidence_id` collega la decisione alla prova della firma; l'endpoint diretto risponde `409 inventory.decision.signatureRequired`.

Una modifica ai dati rilevanti dell'oggetto o alle fotografie crea automaticamente una nuova revisione per ogni assegnazione non interamente riconsegnata. La sola modifica della quantità totale non richiede una nuova presa visione.

### Riconsegna

La tabella `inventory_return` consente riconsegne parziali e multiple. Utente, admin o super admin possono aprire una richiesta specificando la quantità, lo stato di conservazione e note facoltative. La somma delle richieste aperte non può superare la quantità residua. Solo admin e super admin possono chiuderla.

Lo stato della riconsegna vale `REQUESTED`, `COMPLETED` o `CANCELLED`. Admin e super admin possono annullare una richiesta aperta (`CANCELLED`) o eliminarla. L'eliminazione di una riconsegna completata ripristina la quantità in carico e ricalcola lo stato dell'assegnazione; è rifiutata con `409 inventory.return.materialReassigned` se la quantità è già stata riassegnata. L'eliminazione di un'assegnazione elimina logicamente anche le sue riconsegne.

Alla chiusura sono registrati:

- quantità effettivamente riconsegnata;
- data e amministratore;
- stato di conservazione alla riconsegna;
- note;
- fotografie JPEG/PNG con gli stessi limiti di 10 MB.

La chiusura aggiorna atomicamente la quantità riconsegnata e lo stato dell'assegnazione. Non è possibile riconsegnare più della quantità residua.

Il motivo della riconsegna (`reason`) vale `STANDARD`, oppure `SUBSTITUTION` quando la riconsegna è generata dalla sostituzione di un bene in un foglio di affidamento. Una riconsegna completata di un bene appartenente a un foglio chiuso o definito non può essere eliminata.

## Documenti PDF

Il proforma PDF dell'inventario è stato rimosso: il documento da consegnare e far firmare al socio è il [foglio di consegna e affidamento in comodato](inventory-loan-sheet-spec.md), emesso dalla pagina utente e scaricabile dal socio nella propria lista dell'inventario.

La tabella `inventory_report_export`, che registrava i proforma generati, è eliminata dal changeset `20261010000000_drop_inventory_report_export.xml`. Lo stesso changeset marca come eliminati i media dei PDF già generati, se nessun'altra tabella li usa: la pulizia periodica dei media ne rimuove poi i file.

L'intestazione comune dei PDF generati da Taurus è resa tramite `TenantPdfHeaderService` e `PdfPageWriter`. Logo, denominazione, sede, paese, codice fiscale, partita IVA, data di generazione e stile non devono essere reimplementati nei singoli servizi di reportistica. Fa eccezione il foglio di affidamento: è un contratto da stampare e firmare che riproduce il modello dell'associazione, senza logo né data di generazione, e usa un proprio template HTML convertito con OpenHTMLtoPDF. Legge comunque i dati del tenant tramite `TenantsService`.

## Ricerca

La ricerca amministrativa filtra gli oggetti attivi per nome o numero inventariale, senza distinzione tra maiuscole e minuscole, con query JPQL su PostgreSQL (`InventoryItemRepository`). Il parametro facoltativo `attention` restringe l'elenco agli oggetti che richiedono un'azione: `pending-decisions`, `pending-returns`, `expiring`, `issues-unsafe`, `issues-limiting`. L'elenco personale accetta `pending-decisions` ed `expiring` sul materiale in possesso. I filtri sono usati dai collegamenti della dashboard.

La proiezione OpenSearch `{tenant}-inventory-items` e l'outbox `inventory_search_outbox` sono state eliminate: la tabella è rimossa dal changeset `20260824000002_finalize_relational_inventory.xml`.

## Notifiche

Sono inviate notifiche applicative mirate nei seguenti casi:

- nuova assegnazione o nuova revisione da prendere in visione;
- accettazione o rifiuto motivato della presa visione, verso admin e super admin;
- creazione, modifica e rimozione di un oggetto, verso admin e super admin;
- aggiunta, rimozione, riordino e cambio dell'anteprima delle fotografie dell'oggetto, verso admin e super admin;
- creazione, modifica, rimozione o riemissione di un'assegnazione, verso admin e super admin;
- richiesta di riconsegna, verso gli amministratori;
- riconsegna completata, verso l'utente assegnatario, gli admin e i super admin.

Le notifiche amministrative riportano l'autore dell'operazione e gli estremi dell'oggetto; per assegnazioni e riconsegne includono anche assegnatario e quantità quando applicabile. La notifica di richiesta di riconsegna indica sempre l'assegnatario come richiedente, anche quando la richiesta è aperta da un amministratore. Se un'operazione coinvolge più utenti, ogni assegnazione produce il proprio evento, mantenendo identificabile il destinatario interessato.

Il servizio notifiche supporta destinatari espliciti, evitando comunicazioni indiscriminate ad altri tenant o utenti.

## Cancellazione GDPR

L'eliminazione di un utente con materiale ancora consegnato non cancella immediatamente l'identità necessaria alla riconsegna.

Il flusso è:

1. eliminazione immediata dei dati non necessari già gestiti dal processo GDPR;
2. disabilitazione dell'account Keycloak;
3. creazione di una richiesta `inventory_erasure_request` in stato sospeso;
4. permanenza dei soli dati minimi necessari a identificare il materiale da recuperare;
5. completamento di tutte le riconsegne da parte di un amministratore;
6. pseudonimizzazione di assegnazioni e segnalazioni di guasto, eliminazione di fogli di affidamento e atti firmati, cancellazione dei dati utente e dell'account Keycloak (o della sola appartenenza al gruppo del tenant, se l'utente appartiene ad altri tenant);
7. chiusura della richiesta GDPR.

L'interfaccia amministrativa mostra le richieste sospese con il pulsante `Completa cancellazione` sempre attivo. Il vincolo è applicato dal backend: con quantità ancora in carico la richiesta risponde `409 inventory.erasure.outstanding`.

Senza materiale in carico la richiesta non serve: fogli di affidamento e atti firmati elettronicamente del socio vengono eliminati subito, nella stessa operazione, come al completamento di una richiesta.

## API REST

Endpoint amministrativi principali:

- `GET/POST /api/inventory/items`, la lettura con i parametri facoltativi `query` e `attention`;
- `GET /api/inventory/summary`, riepilogo per la dashboard;
- `GET/PUT/DELETE /api/inventory/items/{id}`; l'eliminazione di un oggetto con quantità in carico risponde `409 inventory.item.assigned`;
- `POST /api/inventory/items/{id}/photos`;
- `PUT /api/inventory/items/{id}/photos/order`;
- `PUT /api/inventory/items/{itemId}/photos/{photoId}/preview`;
- `GET/DELETE /api/inventory/photos/{id}`;
- `POST /api/inventory/items/{id}/assignments`;
- `PUT/DELETE /api/inventory/assignments/{id}`;
- `POST /api/inventory/assignments/{id}/reissue`;
- `GET /api/inventory/users/{userIndex}/assignments`;
- `POST /api/inventory/assignments/{id}/returns`;
- `POST /api/inventory/returns/{id}/complete`;
- `POST /api/inventory/returns/{id}/cancel`;
- `DELETE /api/inventory/returns/{id}`;
- `POST /api/inventory/returns/{id}/photos`;
- `GET /api/inventory/return-photos/{id}`;
- `GET /api/inventory/erasure-requests`;
- `POST /api/inventory/erasure-requests/{id}/complete`.

Endpoint personali principali:

- `GET /api/user/inventory/assignments`, paginato, filtrabile per materiale in possesso o riconsegnato (`scope`), testo (`query`) e `attention`;
- `GET /api/user/inventory/summary`, riepilogo personale per la dashboard;
- `GET /api/user/inventory/assignments/{id}`;
- `POST /api/user/inventory/assignments/{id}/decision`;
- `POST /api/user/inventory/assignments/{id}/returns`;
- `POST /api/user/inventory/returns/{id}/photos`;
- `GET /api/user/inventory/photos/{id}`;
- `GET /api/user/inventory/return-photos/{id}`.

Gli endpoint di QR code, etichette e guasti sono descritti nella [specifica del QR code](inventory-qr-code-spec.md). Gli endpoint dei fogli di affidamento e del relativo modello (`/api/inventory/loan-sheets/**`, `/api/inventory/users/{userIndex}/loan-sheets/**`, `/api/inventory/loan-template/**`, `/api/inventory/assignments/{id}/renewals`, `/api/inventory/assignments/{id}/substitution`, `/api/user/inventory/loan-sheets/**`) sono descritti nella [specifica del foglio](inventory-loan-sheet-spec.md#api-rest).

Tutti gli identificativi sono verificati insieme al tenant ricavato dal token. Gli endpoint personali verificano inoltre la proprietà dell'assegnazione.

## Interfaccia utente

La voce `Inventario` è visibile nel menu, con il flag `INVENTORY` attivo, ai ruoli SUPER_ADMIN, ADMIN, ARCHIVIST, USER e USER_EXTERNAL; il tesoriere (TREASURER) non la vede e la rotta `/inventory` lo esclude. Per admin e super admin la pagina consente:

- visualizzazione paginata di tutti gli oggetti in modalità lista o griglia;
- ricerca per nome o numero inventariale e ordinamento;
- apertura del dettaglio amministrativo tramite `/inventory/items/{id}`;
- ricerca, creazione, modifica e cancellazione logica degli oggetti;
- controllo quantità totale/disponibile/assegnata;
- caricamento, rimozione, riordino e scelta dell'anteprima delle fotografie;
- assegnazione agli utenti del tenant;
- consultazione delle assegnazioni e delle prese visione;
- riemissione di una revisione dopo una negazione;
- gestione delle riconsegne e delle richieste GDPR sospese.

La creazione parte dal pulsante `Nuovo oggetto` dell'elenco e utilizza un modale, coerente con Album e Tracce, per raccogliere numero inventariale, nome, quantità, stato di conservazione, valore e valuta, marca e modello, matricola, regime di comodato, descrizione e note di conservazione. Dopo il salvataggio, fotografie, assegnazioni, prese visione e riconsegne sono gestite nella pagina di dettaglio `/inventory/items/{id}`, con protezione dalle modifiche non salvate.

Admin e super admin possono alternare `Inventario` e `I miei oggetti`; gli altri ruoli ammessi accedono direttamente alla propria lista paginata, suddivisa tra materiale in possesso e riconsegnato. Ogni consegna apre `/inventory/assignments/{id}`, da cui l'utente può prendere visione, motivare un rifiuto, avviare una riconsegna e allegare fotografie. I fogli di affidamento del socio si scaricano dalla stessa lista. La sezione non è più mostrata nel profilo personale.

La pagina utente consultata da un amministratore mantiene il riepilogo delle assegnazioni e delle decisioni dell'utente.

La vista personale presenta checkbox mutuamente esclusive per accettazione e negazione, richiede una motivazione per la negazione e rimuove i controlli dopo la conferma. La vista amministrativa è in sola lettura sulla decisione dell'utente e abilita la chiusura delle riconsegne.

## Migrazione e rilascio

La migration Liquibase `20260818090000_add_inventory.xml` crea le tabelle di base (oggetti, foto, assegnazioni, revisioni, decisioni, riconsegne, richieste GDPR), gli indici, i vincoli referenziali e i vincoli sulle quantità. I changeset successivi del changelog tenant (`tenant-master.xml`) rimuovono `tenant_code`, rendono il numero riutilizzabile, eliminano outbox ed esportazioni, aggiungono anteprima delle foto, scadenza sull'assegnazione e `inventory_expiration_notice`, oltre alle tabelle di QR code, foglio di affidamento e firma elettronica.

Prima del rilascio:

1. eseguire backup PostgreSQL e verificare spazio sullo storage fotografie;
2. applicare Liquibase in staging;
3. configurare permessi di lettura/scrittura dello storage per l'applicazione;
4. attivare il flag `INVENTORY` sui tenant interessati;
5. eseguire uno smoke test con admin e utente ordinario;
6. verificare le notifiche con e senza fotografie;
7. monitorare lo spazio occupato dalle immagini.

Il rollback applicativo deve preservare le nuove tabelle: non vanno eliminate in automatico perché contengono storico di consegna e presa visione. Un rollback distruttivo richiede una procedura esplicita di esportazione e conservazione.

## Criteri di accettazione

- un utente senza ruolo amministrativo non può accedere alla gestione globale;
- nessuna API restituisce dati appartenenti a un altro tenant;
- non si può assegnare più della quantità disponibile;
- la quantità totale non può scendere sotto la quantità ancora consegnata;
- la modifica della sola quantità totale non crea una revisione;
- ogni altra modifica concordata crea una nuova revisione da confermare;
- una decisione già salvata è immutabile per la relativa revisione;
- la negazione senza motivazione viene rifiutata;
- l'hash non corrente viene rifiutato;
- utente e amministratore possono avviare una riconsegna, ma solo admin/super admin possono chiuderla;
- JPEG e PNG entro 10 MB sono accettati, WebP e file eccedenti sono rifiutati;
- con il flag `INVENTORY` spento le API rispondono 403 e la voce di menu è nascosta;
- una cancellazione GDPR con materiale residuo resta sospesa fino alla riconsegna completa.

## Verifiche automatiche presenti

I test backend coprono almeno:

- blocco della riduzione della quantità totale sotto il materiale assegnato;
- obbligatorietà della motivazione di negazione;
- riordino e anteprima delle fotografie;
- eliminazione di assegnazioni e riconsegne, con ripristino della quantità o rifiuto se il materiale è riassegnato;
- presenza dei campi di audit in ogni tabella `inventory_*` (`InventoryAuditSchemaTest`).

La migration è validata offline dal plugin Liquibase per PostgreSQL. La build Angular verifica il type-checking e il template checking della nuova pagina e del componente condiviso.
