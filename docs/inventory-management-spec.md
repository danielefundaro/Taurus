# Gestione inventario tenant

## Obiettivo

La funzionalità introduce un inventario separato da album, tracce e dall'anagrafica degli strumenti musicali assegnabile a utenti e tracce. L'inventario rappresenta beni fisici di proprietà del tenant: strumenti musicali, divise, leggii, libretti e altro materiale.

Non è prevista una tassonomia di categorie: il nome e la descrizione identificano il tipo di oggetto. Ogni dato e ogni ricerca sono isolati per tenant.

## Ruoli e autorizzazioni

| Operazione | Utente autenticato | Admin | Super admin |
| --- | ---: | ---: | ---: |
| Visualizzare il proprio materiale | sì | sì | sì |
| Accettare o negare una propria assegnazione | sì | sì | sì |
| Avviare una propria riconsegna | sì | sì | sì |
| Scaricare il proprio proforma | sì | sì | sì |
| Gestire oggetti, foto e assegnazioni | no | sì | sì |
| Consultare inventario di un utente | no | sì | sì |
| Avviare una riconsegna per un utente | no | sì | sì |
| Chiudere una riconsegna | no | sì | sì |
| Gestire richieste GDPR sospese | no | sì | sì |

Gli endpoint personali sono sotto `/api/user/inventory/**`; quelli amministrativi sotto `/api/inventory/**`. Le regole Spring Security autorizzano esplicitamente soltanto le operazioni self-service previste e negano tutte le altre chiamate sul namespace personale. Ogni risorsa personale è inoltre verificata rispetto all'identità ricavata dal token.

## Modello dati relazionale

PostgreSQL è il sistema autorevole. OpenSearch è una proiezione per la ricerca e non contiene lo stato transazionale.

Ogni tabella `inventory_*`, incluse tabelle tecniche, revisioni, decisioni, riconsegne, fotografie ed esportazioni, contiene sempre i campi di audit `deleted`, `insert_date`, `insert_by`, `edit_date` ed `edit_by`.

### Oggetto inventario

Tabella `inventory_item`:

- dati di audit: `deleted`, `insert_date`, `insert_by`, `edit_date`, `edit_by`;
- controllo di concorrenza ottimistico tramite `entity_version`;
- `tenant_code` obbligatorio;
- `inventory_number` obbligatorio e univoco, senza distinzione tra maiuscole e minuscole, tra gli oggetti attivi del tenant; dopo una cancellazione logica il numero può essere riutilizzato, mantenendo invariati i riferimenti storici all'oggetto eliminato;
- `name` obbligatorio;
- `description`;
- `total_quantity`, intero non negativo;
- `estimated_unit_value`, valore stimato unitario;
- `currency`, codice ISO 4217 di tre caratteri, obbligatorio insieme al valore;
- `condition_status`: `NEW`, `EXCELLENT`, `GOOD`, `FAIR`, `TO_REPAIR`, `OUT_OF_SERVICE`;
- `condition_notes`.

La quantità disponibile è calcolata come quantità totale meno la somma delle quantità ancora consegnate. La quantità totale non può essere ridotta sotto il totale già in carico agli utenti.

### Fotografie dell'oggetto

Tabella `inventory_item_photo` con metadati, percorso di storage, digest SHA-256, dimensione e ordine di visualizzazione.

Regole applicate:

- massimo 10 MB per fotografia;
- massimo 20 fotografie per oggetto o singola procedura di riconsegna;
- solo JPEG e PNG;
- WebP non supportato;
- dimensione massima 6000 × 6000 pixel;
- normalizzazione server-side del contenuto;
- soft delete per preservare la verificabilità delle revisioni storiche.

I file sono salvati nello storage configurato dall'applicazione, sotto un percorso separato per tenant. Il database conserva solamente metadati, digest e percorso.

### Assegnazione

Tabella `inventory_assignment`:

- riferimento all'oggetto;
- snapshot identificativo dell'utente: index, Keycloak id, nome e cognome;
- ordine di visualizzazione;
- quantità assegnata e quantità riconsegnata;
- data di assegnazione;
- descrizione;
- stato `ACTIVE`, `PARTIALLY_RETURNED` o `RETURNED`;
- revisione corrente;
- dati di audit e versione ottimistica.

La creazione e l'aggiornamento usano un lock pessimista sull'oggetto per impedire sovra-assegnazioni in richieste concorrenti.

### Revisioni e presa visione

Ogni assegnazione ha revisioni immutabili nella tabella `inventory_assignment_revision`. La revisione contiene:

- numero progressivo;
- causa: assegnazione iniziale, aggiornamento oggetto, aggiornamento assegnazione, modifica foto o riemissione dopo un rifiuto;
- snapshot JSON canonico;
- hash SHA-256 dello snapshot;
- autore e data.

Lo snapshot include numero inventariale, nome, descrizione, valore unitario, valuta, stato e note di conservazione, quantità e descrizione dell'assegnazione, nonché id e digest delle fotografie. Sono esclusi la quantità totale, i dati di audit e gli attributi tecnici non percepibili dall'utente.

La presa visione è per singola assegnazione e singola revisione. L'utente invia l'hash visualizzato insieme alla scelta. Il server rifiuta hash non correnti, utenti non proprietari e seconde decisioni sulla stessa revisione.

La scelta è:

- `ACCEPTED`, senza motivazione obbligatoria;
- `REJECTED`, sempre accompagnata da una motivazione non vuota.

La decisione è memorizzata in `inventory_assignment_decision` con data, identità autenticata e un hash autenticato derivato dalla revisione, dalla scelta e dall'utente. Non rappresenta una firma elettronica: è una presa visione autenticata.

Una modifica ai dati rilevanti dell'oggetto o alle fotografie crea automaticamente una nuova revisione per ogni assegnazione non interamente riconsegnata. La sola modifica della quantità totale non richiede una nuova presa visione.

### Riconsegna

La tabella `inventory_return` consente riconsegne parziali e multiple. Utente, admin o super admin possono aprire una richiesta specificando la quantità e note facoltative. Solo admin e super admin possono chiuderla.

Alla chiusura sono registrati:

- quantità effettivamente riconsegnata;
- data e amministratore;
- stato di conservazione alla riconsegna;
- note;
- fotografie JPEG/PNG con gli stessi limiti di 10 MB.

La chiusura aggiorna atomicamente la quantità riconsegnata e lo stato dell'assegnazione. Non è possibile riconsegnare più della quantità residua.

## PDF proforma

Il PDF viene generato server-side con PDFBox. Può essere richiesto dall'utente per sé oppure da admin/super admin per un utente del tenant.

Filtri disponibili:

- materiale ancora assegnato;
- materiale già riconsegnato;
- inclusione delle fotografie.

Una riconsegna parziale può comparire sia nel gruppo assegnato sia in quello riconsegnato, perché contiene contemporaneamente quantità residua e quantità restituita.

Il documento contiene:

- denominazione e dati fiscali/anagrafici del tenant disponibili;
- nome, cognome, email e data di nascita dell'utente, se presente;
- numero inventariale e tutti i dati descrittivi degli oggetti;
- quantità assegnate, riconsegnate e residue;
- revisioni, hash e prese visione;
- richieste e completamenti di riconsegna;
- stato, note e fotografie allegate quando richieste;
- data di generazione e numerazione delle pagine.

Limiti applicati:

- massimo 100 fotografie per documento;
- massimo 100 MB per il PDF risultante;
- timeout di generazione di 120 secondi.

Il timeout evita che un documento particolarmente pesante occupi indefinitamente thread e memoria del server. Se viene superato, la richiesta termina in modo controllato e può essere ripetuta con filtri più restrittivi, ad esempio senza fotografie.

Ogni esportazione riuscita è tracciata in `inventory_report_export`: tenant, utente oggetto del report, richiedente autenticato, filtri, data, dimensione e digest SHA-256 del PDF. Il PDF non viene duplicato nel database.

## OpenSearch

L'indice logico `InventoryItems` viene risolto secondo la convenzione esistente nel nome fisico `{tenant}-inventory-items`.

La proiezione include esclusivamente dati ricercabili dell'oggetto e quantità aggregate. Non include nomi, email, Keycloak id o altri dati degli assegnatari.

La sincronizzazione usa la transactional outbox `inventory_search_outbox`:

1. la transazione PostgreSQL modifica l'oggetto e inserisce un evento di indicizzazione;
2. un job pianificato elabora gli eventi pendenti;
3. l'evento viene marcato come completato solo dopo la risposta positiva di OpenSearch;
4. in caso di errore viene riprovato con backoff esponenziale.

Il primo retry avviene dopo circa 30 secondi, cresce progressivamente fino a un massimo di 30 minuti e si arresta dopo 10 tentativi marcando l'evento `FAILED`. I retry assorbono indisponibilità temporanee di rete o OpenSearch senza perdere modifiche già confermate nel database. Gli eventi falliti restano disponibili per diagnosi e riprocessamento operativo.

Il client OpenSearch applica un timeout di 30 secondi, così un nodo non raggiungibile non blocca indefinitamente né le richieste né il job di proiezione.

## Notifiche

Sono inviate notifiche applicative mirate nei seguenti casi:

- nuova assegnazione o nuova revisione da prendere in visione;
- negazione motivata, verso gli amministratori del tenant;
- richiesta di riconsegna, verso gli amministratori;
- riconsegna completata, verso l'utente.

Il servizio notifiche supporta destinatari espliciti, evitando comunicazioni indiscriminate ad altri tenant o utenti.

## Cancellazione GDPR

L'eliminazione di un utente con materiale ancora consegnato non cancella immediatamente l'identità necessaria alla riconsegna.

Il flusso è:

1. eliminazione immediata dei dati non necessari già gestiti dal processo GDPR;
2. disabilitazione dell'account Keycloak;
3. creazione di una richiesta `inventory_erasure_request` in stato sospeso;
4. permanenza dei soli dati minimi necessari a identificare il materiale da recuperare;
5. completamento di tutte le riconsegne da parte di un amministratore;
6. pseudonimizzazione dello storico inventario e rimozione definitiva dell'identità da OpenSearch e Keycloak;
7. chiusura della richiesta GDPR.

L'interfaccia amministrativa mostra le richieste sospese e consente di completarle solo quando non rimangono quantità in carico.

## API REST

Endpoint amministrativi principali:

- `GET/POST /api/inventory/items`;
- `GET/PUT/DELETE /api/inventory/items/{id}`;
- `POST /api/inventory/items/{id}/photos`;
- `DELETE /api/inventory/photos/{id}`;
- `POST /api/inventory/items/{id}/assignments`;
- `PUT /api/inventory/assignments/{id}`;
- `POST /api/inventory/assignments/{id}/reissue`;
- `GET /api/inventory/users/{userIndex}/assignments`;
- `POST /api/inventory/assignments/{id}/returns`;
- `POST /api/inventory/returns/{id}/complete`;
- `POST /api/inventory/returns/{id}/photos`;
- `GET /api/inventory/users/{userIndex}/report`;
- `GET /api/inventory/erasure-requests`;
- `POST /api/inventory/erasure-requests/{id}/complete`.

Endpoint personali principali:

- `GET /api/user/inventory/assignments`, paginato e filtrabile per materiale in possesso o riconsegnato;
- `GET /api/user/inventory/assignments/{id}`;
- `POST /api/user/inventory/assignments/{id}/decision`;
- `POST /api/user/inventory/assignments/{id}/returns`;
- `POST /api/user/inventory/returns/{id}/photos`;
- `GET /api/user/inventory/photos/{id}`;
- `GET /api/user/inventory/return-photos/{id}`;
- `GET /api/user/inventory/report`.

Tutti gli identificativi sono verificati insieme al tenant ricavato dal token. Gli endpoint personali verificano inoltre la proprietà dell'assegnazione.

## Interfaccia utente

La voce `Inventario` è visibile nel menu solo ad admin e super admin. La pagina consente:

- visualizzazione paginata di tutti gli oggetti in modalità lista o griglia;
- ricerca per nome o numero inventariale e ordinamento;
- apertura del dettaglio amministrativo tramite `/inventory/items/{id}`;
- ricerca, creazione, modifica e cancellazione logica degli oggetti;
- controllo quantità totale/disponibile/assegnata;
- caricamento e rimozione fotografie;
- assegnazione agli utenti del tenant;
- consultazione delle assegnazioni e delle prese visione;
- riemissione di una revisione dopo una negazione;
- gestione delle riconsegne e delle richieste GDPR sospese.

La creazione parte dal pulsante `Nuovo` dell'elenco e utilizza un modale, coerente con Album e Tracce, per raccogliere numero inventariale, nome, quantità, valore e valuta, stato e note di conservazione e descrizione. Dopo il salvataggio, fotografie, assegnazioni, prese visione e riconsegne sono gestite nella pagina di dettaglio `/inventory/items/{id}`, con protezione dalle modifiche non salvate.

La voce laterale `Inventario` è disponibile a tutti i ruoli. Admin e super admin possono alternare `Inventario tenant` e `I miei oggetti`; gli altri ruoli accedono direttamente alla propria lista paginata, suddivisa tra materiale in possesso e riconsegnato. Ogni consegna apre `/inventory/assignments/{id}`, da cui l'utente può prendere visione, motivare un rifiuto, avviare una riconsegna, allegare fotografie e scaricare il proforma. La sezione non è più mostrata nel profilo personale.

La pagina utente consultata da un amministratore mantiene il riepilogo delle assegnazioni e delle decisioni dell'utente.

La vista personale presenta checkbox mutuamente esclusive per accettazione e negazione, richiede una motivazione per la negazione e rimuove i controlli dopo la conferma. La vista amministrativa è in sola lettura sulla decisione dell'utente e abilita la chiusura delle riconsegne.

## Migrazione e rilascio

La migration Liquibase `20260818090000_add_inventory.xml` crea tutte le tabelle, gli indici, i vincoli referenziali e i vincoli sulle quantità. È inclusa nel master changelog.

Prima del rilascio:

1. eseguire backup PostgreSQL e verificare spazio sullo storage fotografie;
2. applicare Liquibase in staging;
3. configurare permessi di lettura/scrittura dello storage per l'applicazione;
4. verificare la raggiungibilità di OpenSearch e la creazione dell'indice tenant;
5. eseguire uno smoke test con admin e utente ordinario;
6. verificare notifiche e generazione PDF con e senza fotografie;
7. monitorare eventi outbox `FAILED`, tempi di generazione PDF e spazio occupato dalle immagini.

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
- il PDF rispetta filtri, limite di 100 MB e timeout;
- OpenSearch non contiene PII degli assegnatari e recupera dagli errori temporanei tramite retry;
- una cancellazione GDPR con materiale residuo resta sospesa fino alla riconsegna completa.

## Verifiche automatiche presenti

I test backend coprono almeno:

- blocco della riduzione della quantità totale sotto il materiale assegnato;
- obbligatorietà della motivazione di negazione;
- generazione e apertura di un PDF valido.

La migration è validata offline dal plugin Liquibase per PostgreSQL. La build Angular verifica il type-checking e il template checking della nuova pagina e del componente condiviso.
