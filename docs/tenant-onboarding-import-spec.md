# Onboarding guidato e importazione iniziale del tenant

## Stato del documento

ID catalogo: `tenant-onboarding-import`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

## Obiettivo

Ridurre il tempo necessario per rendere operativo un nuovo tenant, permettendo a Super Admin e Admin di:

- scaricare template versionati e già documentati;
- importare strumenti aggiuntivi, utenti, inventario, conti economici, categorie e saldi iniziali;
- validare tutti i dati prima di modificare Taurus o Keycloak;
- visualizzare errori e avvisi per riga e colonna;
- controllare un'anteprima normalizzata;
- applicare l'intero pacchetto in modo idempotente e recuperabile;
- inviare le e-mail di configurazione soltanto dopo il completamento dei dati applicativi;
- riprendere il processo dopo la chiusura del browser o un riavvio del backend.

L'onboarding non sostituisce le normali pagine gestionali. Accelera il caricamento iniziale e gli inserimenti additivi di massa, mentre le correzioni successive continuano ad avvenire nei moduli autorevoli.

## Decisioni principali

| Aspetto | Decisione |
| --- | --- |
| Creazione tenant | Resta separata e continua a usare il provisioning esistente |
| Tenant di destinazione | Sempre quello attivo nella sessione; nessun `tenantId` accettato dal client |
| Ruoli autorizzati | `ROLE_SUPER_ADMIN` e `ROLE_ADMIN` |
| Formato completo | `.xlsx` multi-foglio, versione esplicita |
| Formato semplice | `.csv` per una sola sezione alla volta |
| Modalità prima versione | Additiva, senza aggiornamenti o cancellazioni massive |
| Validazione | Completa prima dell'applicazione; all'avvio dell'applicazione si ricontrollano solo le funzionalità tenant delle sezioni |
| Elaborazione | Asincrona e riprendibile tramite job persistente |
| Dati di staging | Tenant-scoped, temporanei e soggetti a retention |
| PostgreSQL | Commit unico per tutti i dati relazionali selezionati |
| Keycloak | Saga compensabile; non è parte della transazione PostgreSQL |
| Notifiche | Nessuna notifica per riga importata e nessun riepilogo finale |
| E-mail utenti | Solo dopo il commit applicativo; gli errori di invio non annullano l'importazione |
| Riprova | Lo stesso job non applica mai due volte gli stessi dati |

## Separazione tra creazione del tenant e onboarding

La creazione del tenant continua a:

1. salvare l'anagrafica nel catalogo globale;
2. creare e migrare lo schema PostgreSQL;
3. collegare il registro degli schemi;
4. creare il gruppo Keycloak;
5. rendere il tenant attivabile nella sessione.

Solo dopo che `tenant_schema_registry.status = ACTIVE` il wizard può essere aperto nel contesto di quel tenant.

Questa separazione è necessaria perché la sessione autenticata e il tenant context devono essere già validi prima di scrivere nello schema. Il wizard non accetta l'ID della pagina tenant come scorciatoia per cambiare destinazione.

Dal dettaglio globale dell'istanza il pulsante **Configura dati** è mostrato soltanto quando il tenant visualizzato coincide con il tenant attivo. Negli altri casi il pulsante non compare.

### Stato dell'onboarding

Non viene aggiunto un flag globale «onboarding completato», perché inventario e finanza sono moduli facoltativi e un singolo booleano diventerebbe rapidamente ambiguo.

La pagina mostra invece una checklist derivata:

- **Istanza pronta**: schema attivo e anagrafica minima valida;
- **Strumenti disponibili**: almeno uno strumento attivo, normalmente già garantito dal seed Liquibase;
- **Utenti presenti**: almeno un utente attivo nello schema tenant;
- **Inventario configurato**: numero di oggetti presenti, senza considerarlo obbligatorio;
- **Economia configurata**: numero di conti attivi, senza considerarlo obbligatorio;
- **Ultima importazione**: esito e data dell'ultimo job, esposti dal contesto (`lastImport`) ma non mostrati dall'interfaccia.

La checklist informa, non blocca l'utilizzo dei moduli. Un tenant può eseguire più importazioni additive nel tempo.

## Ambito della prima versione

Il workbook completo può contenere le seguenti sezioni, applicate nell'ordine indicato:

1. strumenti;
2. utenti e associazioni agli strumenti;
3. inventario;
4. categorie economiche;
5. conti economici;
6. saldi iniziali.

Ogni sezione è facoltativa. Le dipendenze devono però essere risolvibili: per esempio un utente può riferirsi a uno strumento importato nello stesso job oppure già presente nel tenant.

## Fuori ambito della prima versione

- album, tracce, spartiti e contenuti multimediali;
- fotografie inventario e allegati finanziari;
- assegnazioni e riconsegne inventario;
- eventi, serie ricorrenti, disponibilità e presenze;
- movimenti economici ordinari e trasferimenti;
- importazione di password;
- modifica o cancellazione massiva di dati esistenti;
- sincronizzazione periodica con un file esterno;
- importazione `.xls`, OpenDocument o archivi ZIP caricati dall'utente;
- formule Excel, macro, collegamenti esterni e fogli protetti da password;
- mapping libero di colonne arbitrarie nella prima versione;
- importazioni cross-tenant avviate da un'unica schermata globale.

## Modalità additiva e conflitti

La prima versione non offre un'opzione generica `UPSERT`. Ogni dominio applica una politica deterministica.

### Strumenti

Il provisioning del tenant carica già il catalogo strumentale standard. Il nome normalizzato, senza differenze di maiuscole o spazi esterni, viene quindi usato per individuare strumenti esistenti.

- strumento equivalente esistente: viene riutilizzato e produce un avviso;
- stesso nome con descrizione diversa: viene riutilizzato senza sovrascrivere la descrizione e produce un avviso esplicito;
- nome non presente: viene creato;
- duplicato nel file: errore bloccante.

Il `riferimento` del foglio è una chiave locale al workbook e non viene persistito nel dominio.

### Utenti

L'e-mail normalizzata è la chiave di riconciliazione dell'identità.

- utente assente da Keycloak: viene preparata una nuova identità;
- utente già presente in Keycloak ma non nel tenant: durante l'applicazione viene collegato al gruppo del tenant senza sovrascrivere il profilo globale; la validazione non lo segnala;
- utente già presente nel tenant con stessi nome e cognome normalizzati: viene saltato con avviso `EXISTING_RECORD_WILL_BE_SKIPPED`;
- utente già presente nel tenant con nome o cognome differenti: errore bloccante `CONFLICT_EXISTING_RECORD`;
- `ROLE_SUPER_ADMIN`: sempre vietato nel file;
- numero di nuovi membri oltre `tenant.maxUsers`: errore bloccante.

Per un'identità Keycloak già esistente, nome e cognome importati vengono conservati nella riga `app_user` tenant-scoped; il profilo Keycloak globale non viene modificato automaticamente. La configurazione delle autorità del tenant aggiorna soltanto gruppo e attributo `<tenantCode>_roles`.

### Inventario

- `inventory_number` assente nel tenant: l'oggetto viene creato;
- stesso numero già attivo, anche con dati uguali: errore bloccante;
- numeri duplicati nel file: errore bloccante;
- nessuna assegnazione viene creata automaticamente.

### Categorie economiche

- categoria di sistema con stesso nome e direzione: viene riutilizzata con avviso;
- categoria attiva personalizzata equivalente: viene riutilizzata con avviso;
- stesso nome con direzione incompatibile: errore bloccante;
- categoria nuova: viene creata come non di sistema.

### Conti economici

- conto attivo equivalente per nome normalizzato, tipo e valuta: viene riutilizzato con avviso;
- stesso nome con tipo o valuta diversi: errore bloccante;
- conto nuovo: viene creato;
- l'IBAN non viene usato come chiave di riconciliazione e viene validato soltanto quando il tipo è `BANK`.

### Saldi iniziali

- massimo una riga per conto nel file;
- il conto può essere creato nello stesso job o riutilizzato se equivalente;
- il conto non deve avere movimenti attivi; la validazione non lo verifica: un conto esistente con movimenti fa fallire il commit (`OPENING_BALANCE_NOT_ALLOWED`), il job passa in compensazione e termina `FAILED`;
- l'importo è firmato: positivo per saldo attivo, negativo per saldo passivo, zero consentito ma ignorato;
- la data determina l'esercizio contabile, creato se assente;
- la scrittura creata usa `FinancialMovementNature.OPENING`, `initial_balance = true` e la descrizione "Saldo iniziale", come il saldo iniziale inserito alla creazione di un conto: il riporto annuale non la modifica.

## Formati supportati

### Workbook XLSX completo

Il file ufficiale si chiama `taurus-onboarding-v1.xlsx` e contiene:

- un foglio `Istruzioni`, non importato;
- un foglio nascosto `_taurus` con `templateVersion=1` e identificatore del prodotto;
- i fogli dati `Strumenti`, `Utenti`, `Inventario`, `Categorie`, `Conti` e `Saldi iniziali`, limitati alle sezioni disponibili per il tenant;
- elenchi di convalida Excel con i codici di `attivo`, `condizione`, `direzione` e `tipo`;
- intestazioni bloccate e filtri attivi.

Il template non contiene righe di esempio. Il CSV di sezione contiene la sola riga di intestazione.

I nomi e le intestazioni dei fogli devono coincidere esattamente con il template; fanno eccezione le colonne facoltative aggiunte in coda dopo la prima versione, che un file preparato prima può omettere tutte insieme. Fogli sconosciuti producono un avviso; colonne sconosciute producono un errore, per evitare che un errore ortografico venga ignorato silenziosamente.

### CSV per singola sezione

Il CSV:

- contiene una sola sezione selezionata prima del caricamento;
- usa codifica UTF-8, con BOM facoltativo;
- accetta separatore virgola oppure punto e virgola, rilevato dalla riga di intestazione;
- usa virgolette secondo RFC 4180;
- usa intestazioni identiche al corrispondente foglio XLSX;
- non permette dipendenze verso righe presenti in un altro file non ancora applicato.

Se un CSV utenti fa riferimento a strumenti, questi devono già esistere nel tenant. Per importare strumenti e utenti insieme si usa il workbook XLSX.

## Colonne dei template

### `Strumenti`

| Colonna | Obbligatoria | Regola |
| --- | ---: | --- |
| `riferimento` | sì | chiave locale, univoca nel workbook, massimo 64 caratteri |
| `nome` | sì | massimo 255 caratteri |
| `descrizione` | no | massimo 4000 caratteri |
| `suono` | no | programma General MIDI da 0 a 127, oppure `batteria:<nota>` con nota da 27 a 87 |
| `trasposizione` | no | semitoni interi tra −36 e +36 dalla nota scritta al suono reale; non ammessa con `batteria:` |
| `trasposizione_chiave_basso` | no | come `trasposizione`, per le parti in chiave di basso se diversa |

Le ultime tre colonne servono all'[ascolto delle parti](track-score-playback-spec.md) e sono state aggiunte dopo la prima versione del template: un foglio `Strumenti` con le sole prime tre colonne resta valido. Se le tre colonne sono vuote, Taurus applica la proposta calcolata dal nome; se il tenant usa l'ascolto delle parti, il rapporto la segnala con `PLAYBACK_PROPOSED`, oppure con `PLAYBACK_NOT_CONFIGURED` quando il nome non ha una proposta o i valori indicati non bastano a configurare lo strumento. Per uno strumento esistente, che viene riutilizzato, i valori indicati non vengono applicati (`PLAYBACK_NOT_APPLIED`).

### `Utenti`

| Colonna | Obbligatoria | Regola |
| --- | ---: | --- |
| `riferimento` | sì | chiave locale, univoca nel foglio |
| `nome` | sì | valore non vuoto |
| `cognome` | sì | valore non vuoto |
| `email` | sì | indirizzo valido, normalizzato in minuscolo |
| `data_nascita` | no | data ISO `YYYY-MM-DD` |
| `ruoli` | sì | uno o più codici separati da `|` |
| `strumenti` | no | riferimenti separati da `|`, ordine conservato |
| `attivo` | no | `SI` o `NO`, predefinito `SI` |

Codici ruolo ammessi: `ADMIN`, `TREASURER`, `ARCHIVIST`, `USER`, `USER_EXTERNAL`. Il template usa direttamente i codici stabili, senza etichette italiane.

### `Inventario`

| Colonna | Obbligatoria | Regola |
| --- | ---: | --- |
| `numero_inventario` | sì | massimo 128 caratteri, univoco nel tenant |
| `nome` | sì | massimo 255 caratteri |
| `descrizione` | no | testo libero nei limiti applicativi |
| `quantita_totale` | sì | intero maggiore o uguale a zero |
| `valore_unitario_stimato` | no | decimale non negativo, separatore `.` nel CSV |
| `valuta` | condizionale | codice ISO 4217 di tre lettere se è presente un valore |
| `condizione` | sì | enum previsto dal dominio |
| `note_condizione` | no | massimo 2000 caratteri |

Condizioni ammesse: `NEW`, `EXCELLENT`, `GOOD`, `FAIR`, `TO_REPAIR`, `OUT_OF_SERVICE`. Il foglio non contiene una legenda italiana.

### `Categorie`

| Colonna | Obbligatoria | Regola |
| --- | ---: | --- |
| `nome` | sì | massimo 255 caratteri |
| `descrizione` | no | testo libero nei limiti applicativi |
| `direzione` | sì | `INCOME`, `EXPENSE` oppure `BOTH` |
| `ordine` | no | intero; se assente viene assegnato dopo quelli esistenti |

### `Conti`

| Colonna | Obbligatoria | Regola |
| --- | ---: | --- |
| `riferimento` | sì | chiave locale usata dal foglio saldi |
| `nome` | sì | massimo 255 caratteri |
| `descrizione` | no | testo libero nei limiti applicativi |
| `tipo` | sì | `CASH` oppure `BANK` |
| `valuta` | sì | codice ISO 4217 di tre lettere |
| `iban` | no | facoltativo, massimo 34 caratteri, normalizzato senza spazi e ammesso soltanto per `BANK` |
| `banca` | no | massimo 255 caratteri, vietata per `CASH` |
| `ordine` | no | intero; se assente viene assegnato automaticamente |

### `Saldi iniziali`

| Colonna | Obbligatoria | Regola |
| --- | ---: | --- |
| `conto` | sì | riferimento del foglio `Conti` oppure nome univoco di un conto esistente |
| `data` | sì | data ISO `YYYY-MM-DD` |
| `importo` | sì | numero firmato con massimo quattro decimali |

I file CSV non accettano date localizzate o separatori delle migliaia. Nel foglio XLSX vengono accettate celle data reali e celle testuali ISO; la normalizzazione mostrata in anteprima è sempre ISO.

## Limiti

Valori predefiniti:

| Limite | Valore |
| --- | ---: |
| Dimensione file | 10 MB |
| Righe dati complessive | 5.000 |
| Righe utenti | 2.000 nel file (`TOO_MANY_USER_ROWS`); le nuove membership oltre il limite residuo del tenant producono `TENANT_USER_LIMIT_EXCEEDED` |
| Colonne per foglio | 64 |
| Lunghezza singola cella | 10.000 caratteri, verificata dopo la lettura della cella |
| Job in applicazione contemporanea per tenant | 1 |

Il limite applicativo da 10 MB è indipendente dal limite multipart globale, attualmente molto più ampio. Il backend rifiuta il file prima di memorizzarlo quando la dimensione dichiarata supera il limite (`413` `onboarding.file.tooLarge`).

Il numero di problemi conservati per job non è limitato: `max-issues` è configurabile ma non viene applicato. Non esiste un limite di job caricati o in validazione per utente.

## Flusso utente

### Passo 1 - Prepara

La pagina usa il titolo **Configurazione iniziale** e il sottotitolo **Importa in modo controllato i dati iniziali**.

Il wizard mostra:

- nome dell'organizzazione, senza etichette tecniche né codice del tenant;
- checklist delle sezioni disponibili;
- conteggi attuali del tenant;
- pulsante **Scarica modello completo XLSX**;
- download CSV distinto per ogni sezione;
- limiti e comportamento additivo.

La selezione delle sezioni serve a generare aspettative chiare, ma la presenza effettiva delle righe viene verificata dal backend.

### Passo 2 - Carica

L'utente seleziona un singolo `.xlsx` o `.csv`. Per il CSV sceglie prima il tipo di dati.

Il caricamento non parte automaticamente. Il frontend mostra nome, dimensione e formato e richiede il comando **Carica e verifica**. Al termine il server restituisce l'ID del job e avvia la validazione asincrona.

### Passo 3 - Controlla

La pagina mostra:

- stato e avanzamento della validazione;
- riepilogo per sezione: totali, validi, avvisi, errori, riutilizzati e nuovi;
- tabella paginata delle righe normalizzate;
- filtri per sezione e stato;
- elenco di problemi con foglio, riga, colonna quando nota, etichetta italiana, messaggio e suggerimento; il codice tecnico stabile resta disponibile nell'API e nel rapporto;
- download del rapporto errori XLSX.

Le celle non sono modificabili nel browser nella prima versione. L'utente corregge il proprio file e crea un nuovo job; questo evita che l'anteprima diverga dal documento conservato e rende riproducibile ogni applicazione.

### Passo 4 - Conferma

Il comando **Avvia importazione** è disponibile soltanto se:

- il job è `READY`;
- non esistono errori;
- gli avvisi sono stati esplicitamente accettati;
- il tenant del job coincide con quello della sessione corrente;
- nessun altro job è in applicazione nello stesso tenant.

Prima della conferma vengono mostrati il totale delle righe verificate e il numero di avvisi. Il riepilogo usa colori semantici del tema, con sfondo, bordo e testo leggibili sia in modalità chiara sia scura. L'opzione **Invia e-mail di configurazione ai nuovi utenti** è selezionata per impostazione predefinita; non riguarda identità Keycloak già esistenti.

### Passo 5 - Esito

Durante l'applicazione la pagina resta consultabile ma non modificabile. È possibile chiuderla e tornare in seguito tramite `/onboarding/imports/{jobId}`.

Al completamento mostra:

- stato e fase del job;
- l'avviso **Ripristino incompleto** con **Ritenta ripristino** per `COMPENSATION_REQUIRED`;
- le e-mail da ritentare, con **Ritenta e-mail**;
- collegamenti a Utenti, Inventario ed Economia, mostrati anche quando il modulo è disattivato.

Il rapporto resta scaricabile dal pulsante **Rapporto** nell'intestazione della pagina.

## Validazione

La validazione è divisa in livelli e non produce scritture di dominio.

### Livello 1 - File

- per XLSX, firma ZIP (`PK`) iniziale; il MIME del contenuto non viene rilevato;
- dimensione e numero di righe entro i limiti;
- workbook leggibile: un file cifrato o danneggiato produce `FILE_XLSX_INVALID`;
- assenza di macro (`MACRO_NOT_ALLOWED`), celle formula (`FORMULA_NOT_ALLOWED`) e collegamenti esterni (`FILE_EXTERNAL_LINKS`);
- limiti `ZipSecureFile` su rapporto di compressione, dimensione delle voci e testo;
- template e versione riconosciuti;
- fogli e intestazioni validi;
- CSV decodificabile integralmente come UTF-8.

Un errore di questo livello porta il job in `INVALID` con un unico problema globale.

### Livello 2 - Celle

- obbligatorietà;
- lunghezza;
- tipo e formato: un numero, una data o un enum di dominio non validi producono `VALUE_INVALID_FORMAT` senza colonna;
- enum ammessi: `VALUE_INVALID_ENUM`, con colonna, soltanto per `ruoli` e `attivo`;
- date valide;
- importi rappresentabili con `BigDecimal` e massimo quattro decimali;
- e-mail sintatticamente valida;
- riferimenti locali ben formati.

Gli spazi esterni vengono rimossi. Gli spazi interni significativi non vengono compressi nei campi descrittivi. Valute e codici enum vengono convertiti in maiuscolo. Gli indirizzi e-mail vengono normalizzati in minuscolo con `Locale.ROOT` senza alterare la parte locale oltre questa regola.

### Livello 3 - Relazioni nel file

- riferimenti univoci;
- strumenti citati dagli utenti esistenti nel file o nel tenant;
- conti citati dai saldi esistenti nel file o nel tenant;
- nessun saldo duplicato per conto;
- assenza di dipendenze non risolvibili;
- ruoli compatibili con le regole di autorizzazione.

### Livello 4 - Conflitti con il tenant

- e-mail e membership utenti;
- limite massimo utenti;
- numero inventario;
- nome e direzione categorie;
- nome, tipo e valuta conti;
- strumenti già esistenti;
- stato attivo del tenant.

La presenza di movimenti sui conti che ricevono un saldo iniziale è verificata soltanto al commit.

### Livello 5 - Controllo all'avvio dell'applicazione

Al comando di applicazione il backend ricontrolla soltanto che le sezioni del job siano ancora disponibili per le funzionalità tenant. Se non lo sono, il job torna `INVALID` con fase e codice `FEATURE_DEPENDENCY_DISABLED` e non avvia alcuna modifica esterna. I conflitti con record creati dopo la validazione emergono al commit: la transazione fallisce, le identità vengono compensate e il job termina `FAILED`.

## Errori e avvisi

Ogni problema ha:

```text
severity: ERROR | WARNING
code: codice stabile e documentato
section: USERS | INSTRUMENTS | INVENTORY | CATEGORIES | ACCOUNTS | OPENING_BALANCES
rowNumber: numero riga del file, se applicabile
columnName: intestazione, se applicabile
message: testo italiano leggibile
suggestion: correzione proposta, facoltativa
```

Esempi di codici:

- `FILE_UNSUPPORTED_FORMAT`;
- `TEMPLATE_VERSION_UNSUPPORTED`;
- `SHEET_REQUIRED`;
- `COLUMN_UNKNOWN`;
- `VALUE_REQUIRED`;
- `VALUE_INVALID_ENUM`;
- `REFERENCE_NOT_FOUND`;
- `DUPLICATE_IN_FILE`;
- `CONFLICT_EXISTING_RECORD`;
- `TENANT_USER_LIMIT_EXCEEDED`;
- `EXISTING_RECORD_WILL_BE_REUSED`;
- `EXISTING_RECORD_WILL_BE_SKIPPED`;
- `PLAYBACK_PROPOSED`, `PLAYBACK_NOT_CONFIGURED`, `PLAYBACK_NOT_APPLIED`.

`OPENING_BALANCE_NOT_ALLOWED` non è un problema di validazione: è il motivo del fallimento del commit descritto in Saldi iniziali.

Gli `ERROR` bloccano l'applicazione. I `WARNING` richiedono una conferma unica nel passo finale e restano nel rapporto di audit. Nell'interfaccia questi valori e i codici problema sono presentati tramite etichette italiane; i codici originali non vengono modificati nel contratto API.

Il rapporto XLSX riproduce i fogli caricati aggiungendo colonne finali `esito`, `codici_problema` e `messaggi`. La colonna `esito` usa etichette italiane; `codici_problema` conserva i codici tecnici stabili per diagnosi e assistenza. Qualunque testo che inizi con `=`, `+`, `-` o `@` viene neutralizzato nell'export per impedire formula injection.

## Modello persistente di staging

Le tabelle risiedono nello schema del tenant e non contengono `tenant_code`.

### `onboarding_import_job`

| Campo | Scopo |
| --- | --- |
| `id` | identificatore del job |
| `source_media_asset_id` | file originale nel catalogo media |
| `file_name` | nome originale sanificato |
| `file_sha256` | copia immutabile del digest verificato |
| `format` | `XLSX` o `CSV` |
| `csv_section` | sezione selezionata per il CSV |
| `template_version` | versione del contratto |
| `status` | stato complessivo |
| `stage` | fase dettagliata visualizzata nel progresso |
| `progress_percentage` | valore intero 0-100 |
| `send_setup_emails` | opzione scelta prima dell'applicazione |
| `warnings_accepted_at` | conferma degli avvisi |
| `total_rows` | righe complessive |
| `valid_rows` | righe valide |
| `warning_rows` | righe con avvisi |
| `error_rows` | righe con errori |
| `started_at`, `completed_at` | istanti operativi |
| `last_error_code` | codice tecnico sanificato |
| `source_removed_at` | istante in cui la retention ha rimosso file, righe e problemi |
| campi audit | autore e date standard Taurus |

### `onboarding_import_section`

Una riga per sezione con conteggi `total`, `valid`, `warning`, `error`, `create`, `reuse`, `skip` e `applied`.

### `onboarding_import_row`

Contiene `job_id`, sezione, numero riga, stato e `normalized_payload JSONB` conforme alla versione del template.

L'uso di JSONB è limitato allo staging temporaneo: consente anteprima paginata e applicazione riproducibile senza creare sei modelli temporanei paralleli. Il payload non è mai interrogato come fonte di dominio e viene eliminato dalla retention. Le righe diventano immutabili quando il job entra in `READY`.

### `onboarding_import_issue`

Contiene il riferimento alla riga, severità, codice, colonna, messaggio e suggerimento. Non salva una seconda copia del valore originale della cella.

### `onboarding_identity_operation`

Journal per le operazioni Keycloak:

- riferimento alla riga utente (`row_id`), facoltativo con `ON DELETE SET NULL` e azzerato dalla retention;
- tipo `CREATE` o `LINK_EXISTING`;
- ID Keycloak restituito;
- indicatore `created_by_job`;
- presenza precedente nel gruppo;
- snapshot del solo attributo ruoli del tenant;
- stato `PLANNED`, `APPLIED`, `COMPENSATED` o `COMPENSATION_FAILED`;
- ultimo codice errore sanificato.

Non vengono conservati token, password o risposte HTTP complete di Keycloak.

## Stati e transizioni

```text
UPLOADED -> VALIDATING -> INVALID
                       -> READY -> APPLYING -> COMPLETED
                                  APPLYING -> COMPENSATING -> FAILED
                                                          -> COMPENSATION_REQUIRED
UPLOADED | INVALID | READY -> CANCELLED
```

Un errore infrastrutturale durante la validazione produce `FAILED` senza operazioni identità. `retry-validation` è ammesso sui job `INVALID` e `FAILED`, senza consultare il journal identità; negli altri stati risponde `409` `onboarding.job.validationNotRetryable`.

`COMPENSATION_REQUIRED` richiede l'intervento di un Super Admin o Admin tramite il comando **Ritenta ripristino**. Non è possibile applicare o cancellare il job finché la compensazione non termina.

Un job `COMPLETED` è terminale. Chiamate duplicate al comando di applicazione restituiscono lo stesso riepilogo senza eseguire nuove scritture.

Un job `INVALID`, `FAILED` o `COMPLETED` la cui sorgente è stata rimossa dalla retention non accetta una nuova validazione né un nuovo invio delle e-mail: risponde `409` `onboarding.job.sourceRemoved`.

## Atomicità tra PostgreSQL e Keycloak

PostgreSQL e Keycloak non condividono una transazione distribuita. La funzionalità usa quindi una saga esplicita e compensabile.

### Fase A - Avvio

Il comando `apply`, in una transazione:

1. acquisisce il lock pessimista sul job;
2. verifica tenant attivo, stato `READY`, chiave di applicazione e accettazione degli avvisi;
3. rifiuta l'avvio se un altro job è `APPLYING` (`existsByStatus`, `409` `onboarding.tenant.busy`); l'indice unico parziale `uq_onboarding_single_apply` ammette un solo job `APPLYING` o `COMPENSATING`;
4. ricontrolla le funzionalità tenant delle sezioni;
5. porta il job in `APPLYING` e affida l'applicazione a `OnboardingWorker` dopo il commit.

Nessuna chiamata Keycloak avviene se questi controlli falliscono.

### Fase B - Preparazione identità

Per ogni utente, in ordine stabile di riga:

1. cercare l'identità per e-mail normalizzata;
2. se assente, creare l'utente senza inviare e-mail e registrare immediatamente l'ID nel journal;
3. se presente, non modificare nome, cognome, e-mail, stato o credenziali globali;
4. aggiungere il gruppo del tenant se mancante;
5. impostare soltanto l'attributo `<tenantCode>_roles`;
6. registrare l'operazione `APPLIED` prima di procedere alla riga successiva.

Le password non vengono generate né restituite a Taurus. I nuovi utenti ricevono le azioni Keycloak `UPDATE_PASSWORD` e `VERIFY_EMAIL` soltanto nel passaggio finale.

### Fase C - Commit applicativo

Una singola transazione PostgreSQL crea o collega:

1. strumenti;
2. `user_identity` e `tenant_user_membership` nel catalogo pubblico;
3. `app_user`, ruoli e strumenti nello schema tenant;
4. inventario;
5. categorie;
6. conti;
7. esercizi e movimenti `OPENING`.

Le chiamate pubbliche dei normali servizi non vengono concatenate riga per riga. `OnboardingDomainApplicationService` scrive direttamente tramite i repository in una transazione `REQUIRES_NEW`; non passando dai servizi di dominio, non genera notifiche granulari.

Se la transazione fallisce, nessun dato PostgreSQL del pacchetto resta applicato e inizia la compensazione Keycloak.

### Fase D - Finalizzazione

Dopo il commit `OnboardingWorker`:

1. invia le e-mail di configurazione ai soli utenti creati dal job, se richiesto, registrando separatamente ogni esito;
2. marca il job `COMPLETED` con il numero di e-mail fallite (`OnboardingJobStateService.complete`).

Non viene pubblicata alcuna notifica di riepilogo.

Un errore di e-mail non annulla dati validi. Il risultato resta `COMPLETED` con `setupEmailFailures > 0` e offre **Ritenta e-mail** per i destinatari falliti.

### Compensazione

La compensazione opera in ordine inverso:

- identità creata dal job e non presente in altri tenant: eliminazione Keycloak;
- identità creata dal job ma nel frattempo collegata altrove: disabilitazione e segnalazione manuale, senza cancellazione distruttiva;
- identità esistente: ripristino del precedente attributo ruoli del tenant e rimozione dal gruppo soltanto se il job l'aveva aggiunta;
- nessuna modifica ai profili globali preesistenti.

Ogni passo è idempotente. Un riavvio riprende dalle operazioni del journal non ancora compensate.

## Concorrenza e idempotenza

- Possono esistere più job `READY`, ma soltanto uno può entrare in `APPLYING` per tenant.
- Upload e applicazione richiedono un header `Idempotency-Key` UUID.
- La chiave di upload è univoca per autore nel tenant (`uq_onboarding_upload_actor`) e restituisce lo stesso job in caso di retry HTTP.
- La chiave di applicazione è salvata sul job. Durante `APPLYING` la stessa chiave restituisce il job e una chiave diversa produce `409` `onboarding.job.notReady`; su un job `COMPLETED` qualunque chiave restituisce il job.
- Il job viene bloccato con `PESSIMISTIC_WRITE` (`findForUpdate`) durante i comandi.
- Validazione e applicazione partono dopo il commit come eventi asincroni (`OnboardingWorker`). `OnboardingRecoveryScheduler`, ogni `worker-delay` millisecondi e su tutti i tenant attivi, riprende fino a 5 job per tenant in `UPLOADED`, `VALIDATING`, `APPLYING` o `COMPENSATING`, senza lock e senza controllarne l'anzianità: un job ancora in lavorazione può essere ripreso una seconda volta. Con Onboarding disattivato riprende soltanto applicazioni e compensazioni. Lo scheduler si disattiva con `recovery-enabled: false`.
- Nessun job può essere cancellato dopo l'ingresso in `APPLYING`.

## API REST

Tutti gli endpoint sono tenant-scoped e riservati a Super Admin e Admin.

### Template e contesto

```http
GET /api/onboarding/context
GET /api/onboarding/templates/xlsx
GET /api/onboarding/templates/csv?section=USERS
```

`context` restituisce identità del tenant attivo, stato dello schema, limite e conteggio utenti, conteggi per dominio, versioni template supportate, sezioni disponibili, ultimo job e limiti. Contesto e template richiedono la funzionalità `ONBOARDING_IMPORT`.

I download impostano `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff` e non includono dati del tenant.

### Job

```http
POST   /api/onboarding/imports
GET    /api/onboarding/imports?page=0&size=20&sort=insertDate,desc
GET    /api/onboarding/imports/{id}
DELETE /api/onboarding/imports/{id}
POST   /api/onboarding/imports/{id}/retry-validation
POST   /api/onboarding/imports/{id}/apply
POST   /api/onboarding/imports/{id}/retry-compensation
POST   /api/onboarding/imports/{id}/retry-setup-emails
```

L'upload usa `multipart/form-data` con:

- `file` obbligatorio;
- `format` obbligatorio;
- `csvSection` obbligatorio soltanto per CSV;
- `selectedSections` obbligatorio per XLSX;
- header `Idempotency-Key` obbligatorio.

Restituisce `202 Accepted`, il DTO del job e `Location: /api/onboarding/imports/{id}`.

Il comando `apply` usa un secondo `Idempotency-Key` e un body:

```json
{
  "warningsAccepted": true,
  "sendSetupEmails": true
}
```

`DELETE` annulla soltanto job `UPLOADED`, `INVALID` o `READY` e non elimina immediatamente l'audit minimo. Su ogni altro stato, compreso un job già `CANCELLED`, risponde `409` `onboarding.job.notCancellable`.

### Anteprima e rapporti

```http
GET /api/onboarding/imports/{id}/sections
GET /api/onboarding/imports/{id}/rows?section=USERS&status=WARNING&page=0&size=50
GET /api/onboarding/imports/{id}/issues?severity=ERROR&section=USERS&page=0&size=50
GET /api/onboarding/imports/{id}/validation-report
GET /api/onboarding/imports/{id}/final-report
```

Le pagine hanno dimensione massima 100. Rapporti e anteprime usano `Cache-Control: no-store`. Il DTO di riga (`OnboardingDtos.Row`) espone sezione, numero, stato, azione e la mappa `values` dei valori normalizzati.

`validation-report` e `final-report` restituiscono lo stesso rapporto XLSX, generato al momento della richiesta da righe e problemi di staging: dopo la retention non contiene più righe.

### DTO job

```json
{
  "id": 42,
  "fileName": "avvio-banda.xlsx",
  "format": "XLSX",
  "csvSection": null,
  "templateVersion": 1,
  "status": "READY",
  "stage": "VALIDATION_COMPLETED",
  "progressPercentage": 100,
  "counts": {
    "total": 187,
    "valid": 181,
    "warnings": 6,
    "errors": 0
  },
  "createdAt": "2026-09-03T18:00:00+02:00",
  "completedAt": null,
  "setupEmailFailures": 0,
  "lastErrorCode": null,
  "sourceRemovedAt": null
}
```

Lo stato del job è ottenuto con polling ogni due secondi, che si ferma sugli stati finali. La risposta non supporta `ETag` né `If-Modified-Since`.

## Architettura backend

### Componenti

```text
OnboardingResource
        +-- OnboardingTemplateService
        +-- OnboardingReportService
        +-- OnboardingImportService
OnboardingValidationService
        +-- OnboardingFileInspectionService
        +-- OnboardingInstrumentPlayback
OnboardingWorker
        +-- OnboardingValidationService
        +-- OnboardingIdentitySagaService
        +-- OnboardingDomainApplicationService
        +-- OnboardingJobStateService
OnboardingRecoveryScheduler
OnboardingCleanupScheduler
        +-- OnboardingRetentionService
```

Responsabilità:

- `OnboardingResource`: contratto HTTP, autorizzazioni dichiarative e pagination;
- `OnboardingImportService`: contesto, upload, comandi sul job, anteprima e problemi;
- `OnboardingTemplateService`: genera template XLSX e CSV dalla versione supportata;
- `OnboardingFileInspectionService`: controlli di sicurezza e lettura del file;
- `OnboardingValidationService`: normalizzazione, problemi e staging;
- `OnboardingInstrumentPlayback`: suono e trasposizione degli strumenti;
- `OnboardingWorker`: esecuzione asincrona di validazione, applicazione e compensazione;
- `OnboardingIdentitySagaService`: Keycloak, journal, e-mail e compensazione;
- `OnboardingDomainApplicationService`: transazione PostgreSQL dei dati applicativi;
- `OnboardingJobStateService`: transizioni finali del job in transazioni proprie;
- `OnboardingReportService`: rapporto XLSX;
- `OnboardingRecoveryScheduler`: ripresa dei job interrotti;
- `OnboardingCleanupScheduler` e `OnboardingRetentionService`: retention di file, staging e job.

Non si riusa `upload_job`, perché quel modello descrive la lavorazione di PDF musicali e ha semantica, stati e relazioni incompatibili.

### Parsing

Il backend possiede già Apache POI. Il file XLSX viene caricato interamente in memoria con `XSSFWorkbook`, entro i limiti `ZipSecureFile` su rapporto di compressione (0,01), dimensione delle voci (20 MB) e testo (10 MB). Non esistono limiti sul numero di celle; la lunghezza di una cella è verificata dopo la lettura.

Per CSV si introduce Apache Commons CSV con versione fissata nel `pom.xml`. Non si implementa un parser artigianale basato su `split`, perché virgolette, ritorni a capo e delimitatori nei valori renderebbero il formato fragile.

Il parser produce record intermedi tipizzati e non entità JPA. Le formule vengono rifiutate anche se possiedono un valore calcolato memorizzato.

### Storage del file

Il file originale viene salvato tramite il catalogo `media_asset` nella categoria `onboarding-imports`, con MIME dichiarato dal client, dimensione e SHA-256. La chiave di storage è tenant-scoped e non contiene nome del tenant, e-mail o nome file originale.

Il job conserva il digest del file, che non viene riverificato prima della validazione o dell'applicazione.

### Riutilizzo della logica di dominio

`OnboardingDomainApplicationService` non condivide la logica di `FinanceService`: crea direttamente esercizi, categorie, conti e aperture tramite i repository. Le aperture importate hanno `initial_balance = true`, come i saldi iniziali inseriti alla creazione di un conto, ma l'importazione non ricalcola le aperture degli esercizi successivi.

Le scritture massive non invocano i controller e non aprono una transazione per ogni riga.

### Audit e notifiche

Tutte le entità create hanno come autore il subject che ha avviato l'applicazione, non il thread tecnico del worker. Il job conserva sia `requestedBy` sia `executedBy`; coincidono, salvo ripresa amministrativa di una compensazione.

L'importazione non genera notifiche: né per inserimento né di riepilogo.

## Sicurezza

### Autorizzazioni

In `SecurityConfiguration`:

```java
.requestMatchers("/api/onboarding/**")
.hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN)
```

La regola HTTP non sostituisce le verifiche applicative su tenant attivo, proprietario del job e stato. Un Admin può leggere tutti i job del proprio tenant per consentire continuità operativa, ma non quelli di altri schemi.

`ROLE_SUPER_ADMIN` non è mai importabile. Gli altri ruoli seguono le stesse regole già applicate dalla gestione utenti; il wizard non introduce escalation aggiuntive.

### File ostili

- non fidarsi di estensione, MIME dichiarato o nome originale: il formato è riconosciuto dal parser;
- impedire path traversal e non estrarre il contenuto XLSX sul filesystem applicativo;
- limiti POI contro ZIP bomb e decompressione eccessiva;
- rifiutare macro, celle formula e collegamenti esterni del workbook;
- non risolvere URL contenuti nelle celle;
- neutralizzare formula injection nei rapporti esportati;
- non scrivere valori delle celle nei log;
- eseguire il parsing senza accesso di rete;
- usare nomi di file generati e storage tenant-scoped.

### Dati personali

I file possono contenere e-mail e date di nascita. Di conseguenza:

- anteprima e rapporti sono `no-store`;
- log e metriche non contengono valori importati;
- i problemi indicano posizione e regola; solo alcuni messaggi riportano il valore errato, come un ruolo o un riferimento a uno strumento;
- l'accesso usa sempre il tenant context;
- file, righe e problemi vengono rimossi dalla retention;
- il rapporto scaricato è responsabilità dell'utente e la UI lo ricorda.

## Retention

`OnboardingCleanupScheduler` esegue ogni notte la retention (cron `application.onboarding.cleanup-cron`, default `0 30 4 * * *`, zona `Europe/Rome`) su tutti i tenant attivi, anche con Onboarding disattivato. `OnboardingRetentionService` applica due soglie, calcolate da `completed_at`:

- dopo `source-retention-days` (default 30), per i job `INVALID`, `CANCELLED`, `FAILED` e `COMPLETED`: elimina righe di staging e problemi, riduce il journal identità (`row_id` e `previous_roles` azzerati; restano tipo, ID Keycloak ed esiti), imposta `source_removed_at` e libera il file sorgente tramite `MediaService.deleteIfUnreferenced`;
- dopo `audit-retention-days` (default 365), per i job con `source_removed_at` valorizzato: elimina il job, con sezioni e journal in cascata;
- job `COMPENSATION_REQUIRED`: nessuna pulizia automatica;
- job non conclusi (`UPLOADED`, `VALIDATING`, `READY`, `APPLYING`, `COMPENSATING`): nessuna pulizia automatica, anche se abbandonati.

`MediaRepository.isUnreferenced` considera referenziato il file di un job finché `source_removed_at` è vuoto. I rapporti non sono conservati: vengono generati al momento dalle righe di staging, quindi dopo la pulizia non contengono più righe.

Dopo la pulizia il job resta consultabile con conteggi, hash, versione template, autore, esito e date, ma l'anteprima non è più disponibile. Una nuova validazione o un nuovo invio delle e-mail rispondono `409` `onboarding.job.sourceRemoved`.

La migrazione tenant `20261022000001_onboarding_retention.xml` aggiunge `source_removed_at` e rende `onboarding_identity_operation.row_id` facoltativo con `ON DELETE SET NULL`.

## Configurazione

```yaml
application:
  onboarding:
    enabled: true
    recovery-enabled: true
    worker-delay: 2000
    max-file-size: 10MB
    max-total-rows: 5000
    max-user-rows: 2000
    max-columns: 64
    max-cell-length: 10000
    max-issues: 10000
    source-retention-days: 30
    audit-retention-days: 365
    worker-batch-size: 5
    cleanup-cron: "0 30 4 * * *"
```

Le proprietà sono validate all'avvio con vincoli di minimo (`worker-delay` almeno 250, limiti, retention e batch size positivi, `max-columns` al massimo 256). `recovery-enabled` attiva `OnboardingRecoveryScheduler`. `max-issues` e `worker-batch-size` non sono usati: lo scheduler di ripresa prende al massimo 5 job per tenant a ogni giro.

## Frontend

### Navigazione

Nuova route:

```text
/onboarding
/onboarding/imports/:id
```

Il menu mostra **Configurazione iniziale** a Super Admin e Admin quando il tenant attivo è valido. Il dettaglio tenant globale può mostrare **Configura dati** soltanto nel contesto sicuro descritto in precedenza.

La pagina principale mostra anche gli ultimi job, così un amministratore può riprendere una validazione o un'applicazione avviata da un collega.

### Wizard

Il wizard usa `p-stepper` lineare con cinque passi:

1. **Prepara**;
2. **Carica**;
3. **Controlla**;
4. **Conferma**;
5. **Esito**.

I passi completati possono essere riaperti finché il job non entra in `APPLYING`. Il valore attivo dello stepper è sincronizzato in entrambe le direzioni con lo stato del componente, così i comandi **Vai alla conferma** e **Indietro** restano funzionanti anche dopo la navigazione tramite le intestazioni dei passi. Dopo l'avvio, navigazione e selezioni sono bloccate e resta disponibile soltanto la vista di stato.

Il file usa `p-fileupload` con `customUpload`, `multiple=false`, caricamento non automatico e limite locale coerente con il backend. Il componente mantiene il proprio input nativo accessibile; l'area drag-and-drop non è l'unico modo per selezionare il file.

### Anteprima

- `p-table` paginata lato server;
- filtri per sezione ed esito;
- valori della riga letti dalla mappa `values` del DTO;
- riga e cella con errore non collegate al messaggio tramite `aria-describedby`;
- `p-tag` con stati italiani `Valida`, `Avviso`, `Errore`, `Applicata`, `Saltata`;
- azioni delle righe localizzate come `Crea`, `Riutilizza`, `Ignora`;
- nessun editor di cella;
- elenco dei problemi senza paginazione;
- nessuna vista dedicata sotto 768 pixel.

### Progresso e stati

`p-progressbar` mostra percentuale e descrizione testuale della fase. Il contenitore espone `aria-busy`; gli avanzamenti non vengono annunciati a ogni punto percentuale, ma soltanto al cambio fase tramite live region `polite`.

Stati del job, fasi, severità, azioni e titoli dei problemi sono sempre mostrati in italiano, comprese le importazioni recenti e la vista **Esito**. I valori enum e i codici tecnici continuano a essere scambiati in forma stabile con il backend, ma non vengono presentati direttamente all'utente. Per codici futuri non ancora mappati viene mostrata un'etichetta italiana neutra.

Stati vuoti distinti:

- nessun job: invito a scaricare un template;
- nessuna riga nella sezione: spiegazione che il foglio non contiene dati;
- nessun problema: conferma **Nessun errore rilevato**;
- filtro senza risultati: azione **Azzera filtri**.

### Uscita e ripresa

Prima dell'upload il normale guard delle modifiche non salvate avvisa se è stato selezionato un file. Dopo che il server ha creato il job, la navigazione è libera perché lo stato è persistito.

Durante `APPLYING` la chiusura della pagina non interrompe il worker. Al ritorno, il frontend carica lo stato tramite ID e riprende il polling.

### Errori frontend

Gli errori del backend mostrano i testi del catalogo `api-error-messages.ts` (chiavi `onboarding.*`):

- `413` `onboarding.file.tooLarge`: file troppo grande, con il limite in MB;
- `400`: file, sezione CSV o sezioni mancanti, file illeggibile, sezione che richiede una funzionalità non disponibile;
- `409`: job non pronto, non annullabile, validazione non ripetibile, ripristino non necessario, sorgente rimossa, avvisi non accettati, altra importazione in corso, chiave di applicazione differente, e-mail da ritentare assenti;
- `404` `onboarding.disabled` e `onboarding.job.notFound`;
- un file non conforme al template non produce un errore HTTP: il job termina `INVALID` con il problema globale;
- risultato `COMPENSATION_REQUIRED`: avviso **Ripristino incompleto** con il comando **Ritenta ripristino**.

Il riepilogo dell'errore porta il titolo **Caricamento non riuscito**, **Importazione non avviata** oppure **Onboarding non disponibile** per il contesto. Un nuovo tentativo dopo un errore di rete usa una nuova `Idempotency-Key`.

## Osservabilità

Il modulo non registra metriche Micrometer. I log contengono tenant e job ID, il riepilogo della retention (`onboarding_retention`) e gli errori di ripresa e pulizia; non contengono valori delle righe.

Alert raccomandati:

- qualunque job `COMPENSATION_REQUIRED`;
- job `APPLYING` senza avanzamento per più di 15 minuti;
- tasso di job tecnicamente falliti superiore al 5% in un'ora;
- errore ripetuto della pulizia retention.

## Strategia di test

### Unit test parsing

- XLSX e CSV validi per ogni sezione;
- BOM, virgola, punto e virgola, virgolette e ritorni a capo CSV;
- date reali Excel e date ISO testuali;
- decimali, valori negativi e quattro cifre decimali;
- fogli, colonne ed enum sconosciuti;
- formule, macro, external relationship e file cifrati rifiutati;
- ZIP bomb e limiti di celle, righe, colonne e dimensione;
- normalizzazione deterministica di e-mail, valuta, IBAN e spazi;
- neutralizzazione formula injection nel rapporto.

### Unit test validazione

- dipendenze tra strumenti e utenti;
- dipendenze tra conti e saldi;
- duplicati nel file;
- riuso di strumenti e categorie di sistema;
- collisioni inventario e conti;
- identità globale esistente e membership tenant;
- `ROLE_SUPER_ADMIN` rifiutato;
- rispetto di `maxUsers` contando solo nuovi membri;
- conto con movimenti incompatibile con il saldo iniziale;
- errori bloccanti e avvisi coerenti.

### Integration test PostgreSQL

- staging creato nello schema corretto;
- anteprima paginata e isolata per tenant;
- un solo job `APPLYING` per tenant;
- preflight rileva conflitti introdotti dopo la validazione;
- commit unico di tutte le sezioni;
- rollback completo su errore dell'ultima sezione;
- audit usa l'autore del job;
- saldi iniziali creano esercizio e movimento `OPENING` corretti;
- chiamata duplicata con stessa chiave è idempotente;
- pulizia elimina staging e media ma conserva audit minimo.

### Test Keycloak e compensazione

- creazione di un'identità nuova;
- collegamento di identità esistente senza modifica del profilo globale;
- gruppo già presente non viene rimosso in compensazione;
- attributo ruoli precedente viene ripristinato;
- fallimento alla riga N compensa le prime N-1;
- riavvio durante compensazione riprende dal journal;
- identità collegata nel frattempo a un altro tenant non viene eliminata;
- e-mail inviate soltanto dopo il commit;
- errore e-mail non annulla l'importazione e può essere ritentato.

I test Keycloak devono usare un container o un ambiente isolato, non mockare l'intera saga nei soli test di integrazione.

### Security test

- utente anonimo, Tesoriere, Archivista, Utente ed Esterno rifiutati;
- Admin confinato al tenant attivo;
- nessun parametro può scegliere un altro tenant;
- job ID di un altro schema non trovato;
- file ostile non produce chiamate esterne;
- risposta e log non espongono valori sensibili o eccezioni complete;
- ruolo Super Admin nel file sempre rifiutato;
- report e anteprima hanno `no-store`;
- cambio tenant invalida job e polling precedenti.

### Frontend test

- tutti i passi e i relativi vincoli di navigazione;
- selezione file accessibile da tastiera;
- upload non automatico;
- stati `VALIDATING`, `INVALID`, `READY`, `APPLYING`, `COMPLETED`, `FAILED` e `COMPENSATION_REQUIRED`;
- tabella problemi, filtri e anteprima mobile;
- conferma obbligatoria degli avvisi;
- retry HTTP con stessa chiave;
- uscita e ripresa del job;
- polling sospeso a pagina nascosta e terminato sugli stati finali;
- annunci accessibili soltanto al cambio fase;
- layout a 320, 768 e 1280 pixel.

### End-to-end principali

1. Un Admin importa strumenti, utenti e inventario validi e ritrova tutti i record nei moduli.
2. Un utente già presente in Keycloak viene collegato senza modifica del profilo globale.
3. Un file con errori mostra riga e colonna, genera il rapporto e non modifica alcun sistema.
4. Un conflitto creato dopo la validazione viene intercettato dal preflight.
5. Un errore PostgreSQL dopo la preparazione identità attiva e completa la compensazione.
6. Un errore di compensazione porta a `COMPENSATION_REQUIRED` e viene risolto dopo riavvio.
7. Un doppio clic su **Avvia importazione** produce una sola applicazione.
8. Le e-mail fallite vengono ritentate senza reinviare quelle già riuscite.
9. Due tenant importano file omonimi senza condividere file, staging o dati.

## Migrazione Liquibase

Il changelog tenant `20260905030000_tenant_onboarding_import.xml` crea:

```text
onboarding_import_job
onboarding_import_section
onboarding_import_row
onboarding_import_issue
onboarding_identity_operation
```

Vincoli minimi:

- FK in cascata da sezione, riga, problema e journal al job;
- FK del job verso `media_asset` con cancellazione `RESTRICT` finché il riferimento è attivo;
- univocità della chiave upload per autore nel tenant;
- univocità `(job_id, section, row_number)`;
- univocità dell'operazione identità per riga;
- check constraint per stati, formato, severità e percentuale 0-100;
- indice sui job per `(status, edit_date)`;
- indice sui problemi per `(job_id, severity, section, row_number)`;
- indice sulle righe per `(job_id, section, status, row_number)`.

La garanzia di un solo job in applicazione è data dall'indice unico parziale `uq_onboarding_single_apply`, che ammette una sola riga `APPLYING` o `COMPENSATING` non eliminata, e dal controllo `existsByStatus` al comando `apply`. Non si usa un advisory lock.

`20260906020000_onboarding_recovery.xml` aggiunge al journal lo stato di consegna delle e-mail (`setup_email_status`), così il nuovo invio resta selettivo dopo un riavvio. `20261022000001_onboarding_retention.xml` introduce `source_removed_at` e il `row_id` facoltativo del journal.

## Criteri di accettazione

La funzionalità è completa quando:

1. il wizard opera esclusivamente sul tenant attivo e `ACTIVE`;
2. solo Super Admin e Admin possono accedere a endpoint e pagine;
3. template XLSX e CSV sono versionati e generati in modo deterministico;
4. tutte le colonne documentate hanno validazione coerente con i DTO di dominio;
5. file non supportati, cifrati, con macro, formule o relazioni esterne vengono rifiutati;
6. nessuna scrittura di dominio avviene durante la validazione;
7. errori e avvisi indicano sezione, riga, colonna e codice stabile;
8. l'anteprima mostra i valori normalizzati che saranno applicati;
9. le righe di staging diventano immutabili in `READY`;
10. i conflitti dinamici vengono ricontrollati nel preflight;
11. l'importazione è additiva e non modifica o elimina record esistenti;
12. strumenti, categorie e conti equivalenti vengono riutilizzati secondo le regole;
13. inventario e saldi in conflitto bloccano l'intero job;
14. il limite utenti considera soltanto nuove membership;
15. `ROLE_SUPER_ADMIN` non può essere importato;
16. identità Keycloak esistenti non subiscono modifiche al profilo globale;
17. i dati PostgreSQL delle sezioni vengono applicati in un unico commit;
18. un errore di commit attiva la compensazione delle identità;
19. la compensazione è idempotente e riprendibile dopo riavvio;
20. una compensazione incompleta produce uno stato operativo visibile e allertato;
21. chiamate HTTP duplicate non applicano due volte il job;
22. e-mail e notifiche granulari non partono prima del commit;
23. un errore e-mail non annulla dati validi e può essere ritentato selettivamente;
24. file, staging e rapporti rispettano la retention;
25. log e metriche non contengono dati personali importati;
26. anteprima e rapporti usano `Cache-Control: no-store`;
27. la chiusura del browser non interrompe validazione o applicazione;
28. il wizard è utilizzabile da tastiera e su schermi mobili;
29. i test PostgreSQL e Keycloak dimostrano isolamento e recupero;
30. il job da 5.000 righe rispetta i limiti di memoria e tempo approvati in staging;
31. l'interfaccia e la colonna `esito` del rapporto mostrano etichette italiane senza esporre direttamente stati, fasi, azioni o severità tecniche.

## Prestazioni attese

Obiettivi iniziali in staging:

- upload da 10 MB accettato senza conservare l'intero file due volte in memoria;
- validazione di 5.000 righe entro 60 secondi al `p95`;
- applicazione dei soli dati PostgreSQL entro 30 secondi al `p95`;
- utenti Keycloak elaborati con concorrenza limitata e configurabile, predefinita 4;
- memoria aggiuntiva del parser inferiore a 128 MB per job;
- anteprima paginata entro 500 ms al `p95`;
- worker rispettoso del pool JDBC e arrestabile in modo ordinato.

Il frontend non mostra una stima temporale non affidabile. Mostra fase, righe elaborate e percentuale calcolata dal backend.

## File e aree

### Backend del modulo

```text
taurus-be/src/main/java/com/fundaro/zodiac/taurus/domain/onboarding/
taurus-be/src/main/java/com/fundaro/zodiac/taurus/repository/onboarding/
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/onboarding/
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/dto/onboarding/OnboardingDtos.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/web/rest/OnboardingResource.java
taurus-be/src/main/resources/config/liquibase/changelog/20260905030000_tenant_onboarding_import.xml
taurus-be/src/main/resources/config/liquibase/changelog/20260906020000_onboarding_recovery.xml
taurus-be/src/main/resources/config/liquibase/changelog/20261022000001_onboarding_retention.xml
```

### Backend modificati

```text
taurus-be/pom.xml
taurus-be/src/main/java/com/fundaro/zodiac/taurus/config/ApplicationProperties.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/config/SecurityConfiguration.java
taurus-be/src/main/resources/config/application.yml
taurus-be/src/main/resources/config/liquibase/tenant-master.xml
```

I servizi di dominio esistenti non hanno un ramo di importazione: `OnboardingDomainApplicationService` scrive tramite i repository.

### Frontend del modulo

```text
taurus-fe/src/app/module/onboarding.module.ts
taurus-fe/src/app/service/onboarding.service.ts
taurus-fe/src/app/pages/onboarding/onboarding.routes.ts
taurus-fe/src/app/pages/onboarding/onboarding.component.*
```

### Frontend modificati

```text
taurus-fe/src/app/components/menu/menu.component.ts
taurus-fe/src/app/pages/tenants/detail/detail.component.ts
taurus-fe/src/app/pages/tenants/detail/detail.component.html
taurus-fe/src/app/service/index.ts
taurus-fe/src/app/module/index.ts
```

## Evoluzioni successive compatibili

- modalità di aggiornamento con confronto campo per campo e approvazione esplicita;
- mapping guidato di colonne provenienti da gestionali esterni;
- importazione di movimenti economici con chiavi di idempotenza;
- importazione di assegnazioni inventario dopo utenti e beni;
- pacchetto album, tracce e spartiti con upload ZIP controllato;
- API machine-to-machine per migrazioni assistite;
- profili di importazione salvati per fornitori noti;
- esportazione completa dello stesso formato per migrazioni tra ambienti;
- checklist di onboarding nella dashboard operativa trasversale.

Ogni evoluzione che modifica dati esistenti deve introdurre una modalità distinta dall'importazione additiva, con anteprima delle differenze e strategia di rollback propria.

## Requisiti aperti

- Preflight completo all'avvio dell'applicazione, con verifica dell'hash del file e ripetizione dei controlli dinamici: un conflitto riporta il job in `INVALID` senza modifiche esterne (Validazione, Livello 5; Fase A; Criteri di accettazione 10).
- `OPENING_BALANCE_NOT_ALLOWED` segnalato in validazione per i conti con movimenti (Saldi iniziali; Livello 4).
- Limite dei problemi conservati con `max-issues` e problema globale `TOO_MANY_ISSUES` (Limiti).
- Limite di 3 job caricati o in validazione per utente (Limiti).
- Notifica unica di riepilogo nell'outbox con `BulkOperationContext` (Decisioni principali; Fase D; Audit e notifiche).
- Metriche Micrometer `taurus.onboarding.*` e alert su `COMPENSATION_REQUIRED`, job bloccati e fallimenti (Osservabilità).
- Lettura XLSX in streaming SAX (`XSSFReader`) con limiti per cella e sul numero di celle (Parsing; Limiti).
- Rilevamento del MIME reale e rifiuto di relazioni esterne e DDE, oltre a macro, formule e collegamenti (Validazione, Livello 1; File ostili).
- Selezione dei job con `FOR UPDATE SKIP LOCKED` e ripresa dei soli job fermi da un tempo minimo, per escludere la doppia applicazione (Concorrenza e idempotenza; Architettura backend).
- Coordinamento dell'applicazione con advisory lock PostgreSQL (Fase A; Migrazione Liquibase).
- Codice `SOURCE_INTEGRITY_FAILURE` per un file che non corrisponde al digest (Storage del file).
- Colonna valorizzata per ogni problema di formato e `VALUE_INVALID_ENUM` per tutti gli enum di dominio (Validazione, Livello 2; Errori e avvisi).
- Avviso `KEYCLOAK_IDENTITY_WILL_BE_LINKED` in validazione per identità Keycloak esistenti (Utenti; Errori e avvisi).
- Template con righe di esempio, legenda ed etichette italiane di ruoli e condizioni (Workbook XLSX completo; Colonne dei template).
- Invito a cambiare istanza quando il tenant visualizzato non è quello attivo (Separazione tra creazione del tenant e onboarding).
- **Ultima importazione** nella checklist (Stato dell'onboarding).
- Esito con dati creati, riutilizzati e saltati per sezione, utenti Keycloak creati o collegati e durata; collegamenti a Inventario ed Economia solo con il modulo attivo (Passo 5 - Esito).
- Elenco problemi paginato, collegamento `aria-describedby` tra cella e messaggio e vista compatta sotto 768 pixel (Anteprima).
- Polling adattivo con pausa a pagina nascosta e supporto `ETag` o `If-Modified-Since` (DTO job; Frontend test).
- Riuso della stessa `Idempotency-Key` nei tentativi dopo un errore di rete (Errori frontend).
- Elaborazione delle identità Keycloak con concorrenza configurabile, predefinita 4 (Prestazioni attese).
- Decidere una scadenza per i job `UPLOADED` e `READY` abbandonati, che oggi conservano file e dati personali senza limite (Retention).
- Test di sicurezza, frontend ed end-to-end non coperti dai test esistenti (`OnboardingImportIT`, `OnboardingKeycloakIT` e unit test dei servizi), carico da 5.000 righe e simulazione di riavvio in ogni fase della saga (Strategia di test; Prestazioni attese).
