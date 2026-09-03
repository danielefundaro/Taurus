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
| Validazione | Completa prima dell'applicazione, poi ripetuta sui vincoli dinamici |
| Elaborazione | Asincrona e riprendibile tramite job persistente |
| Dati di staging | Tenant-scoped, temporanei e soggetti a retention |
| PostgreSQL | Commit unico per tutti i dati relazionali selezionati |
| Keycloak | Saga compensabile; non è parte della transazione PostgreSQL |
| Notifiche | Un solo riepilogo finale, nessuna notifica per ogni riga importata |
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

Dal dettaglio globale dell'istanza il pulsante **Configura dati** può essere mostrato soltanto quando il tenant visualizzato coincide con il tenant attivo. Negli altri casi il frontend invita a cambiare istanza o ad autenticarsi nel relativo contesto prima di continuare.

### Stato dell'onboarding

Non viene aggiunto un flag globale «onboarding completato», perché inventario e finanza sono moduli facoltativi e un singolo booleano diventerebbe rapidamente ambiguo.

La pagina mostra invece una checklist derivata:

- **Istanza pronta**: schema attivo e anagrafica minima valida;
- **Strumenti disponibili**: almeno uno strumento attivo, normalmente già garantito dal seed Liquibase;
- **Utenti presenti**: almeno un utente attivo nello schema tenant;
- **Inventario configurato**: numero di oggetti presenti, senza considerarlo obbligatorio;
- **Economia configurata**: numero di conti attivi, senza considerarlo obbligatorio;
- **Ultima importazione**: esito e data dell'ultimo job completato.

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
- utente già presente in Keycloak ma non nel tenant: viene collegato al gruppo del tenant senza sovrascrivere il profilo globale;
- utente già presente nel tenant con gli stessi dati normalizzati: viene saltato con avviso;
- utente già presente nel tenant con dati differenti: errore bloccante;
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
- il conto non deve avere movimenti né un movimento `OPENING` già presente;
- l'importo è firmato: positivo per saldo attivo, negativo per saldo passivo, zero consentito ma ignorato;
- la data determina l'esercizio contabile;
- la scrittura creata usa `FinancialMovementNature.OPENING` e la descrizione standard del modulo economico.

## Formati supportati

### Workbook XLSX completo

Il file ufficiale si chiama `taurus-onboarding-v1.xlsx` e contiene:

- un foglio `Istruzioni`, non importato;
- un foglio nascosto `_taurus` con `templateVersion=1` e identificatore del prodotto;
- i fogli dati `Strumenti`, `Utenti`, `Inventario`, `Categorie`, `Conti` e `Saldi iniziali`;
- righe di esempio chiaramente marcate e non incluse nell'importazione;
- elenchi di convalida Excel per enum e valori booleani;
- intestazioni bloccate e filtri attivi.

I nomi e le intestazioni dei fogli devono coincidere esattamente con il template. Fogli sconosciuti producono un avviso; colonne sconosciute producono un errore, per evitare che un errore ortografico venga ignorato silenziosamente.

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

Codici ruolo ammessi: `ADMIN`, `TREASURER`, `ARCHIVIST`, `USER`, `USER_EXTERNAL`. Il template mostra le etichette italiane, ma salva codici stabili indipendenti dalla traduzione.

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

Condizioni ammesse: `NEW`, `EXCELLENT`, `GOOD`, `FAIR`, `TO_REPAIR`, `OUT_OF_SERVICE`. Il foglio contiene anche una legenda italiana.

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
| Righe utenti | minore tra 2.000 e il limite residuo del tenant |
| Colonne per foglio | 64 |
| Lunghezza singola cella | 10.000 caratteri |
| Errori conservati per job | 10.000 |
| Job in applicazione contemporanea per tenant | 1 |
| Job caricati o in validazione contemporanea per utente | 3 |

Il limite applicativo da 10 MB è indipendente dal limite multipart globale, attualmente molto più ampio. Il backend rifiuta il file prima di memorizzarlo quando la dimensione dichiarata o quella effettiva supera il limite.

Se gli errori superano il massimo, la validazione si interrompe con un problema globale `TOO_MANY_ISSUES`; non viene presentato un file apparentemente quasi valido.

## Flusso utente

### Passo 1 - Prepara

Il wizard mostra:

- nome e codice del tenant attivo;
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
- elenco di problemi con foglio, riga, colonna, codice, messaggio e suggerimento;
- download del rapporto errori XLSX.

Le celle non sono modificabili nel browser nella prima versione. L'utente corregge il proprio file e crea un nuovo job; questo evita che l'anteprima diverga dal documento conservato e rende riproducibile ogni applicazione.

### Passo 4 - Conferma

Il comando **Avvia importazione** è disponibile soltanto se:

- il job è `READY`;
- non esistono errori;
- gli avvisi sono stati esplicitamente accettati;
- il tenant del job coincide con quello della sessione corrente;
- nessun altro job è in applicazione nello stesso tenant.

Prima della conferma vengono mostrati i numeri esatti di record nuovi, riutilizzati e saltati. L'opzione **Invia e-mail di configurazione ai nuovi utenti** è selezionata per impostazione predefinita; non riguarda identità Keycloak già esistenti.

### Passo 5 - Esito

Durante l'applicazione la pagina resta consultabile ma non modificabile. È possibile chiuderla e tornare in seguito tramite `/onboarding/imports/{jobId}`.

Al completamento mostra:

- dati creati, riutilizzati e saltati per sezione;
- utenti Keycloak creati o collegati;
- e-mail inviate o da ritentare;
- durata totale;
- collegamenti ai moduli interessati;
- eventuale rapporto finale scaricabile.

## Validazione

La validazione è divisa in livelli e non produce scritture di dominio.

### Livello 1 - File

- estensione e MIME rilevato compatibili;
- dimensione e numero di righe entro i limiti;
- workbook non cifrato e non protetto da password;
- assenza di macro, formule, collegamenti e relazioni esterne;
- nessuna struttura ZIP anomala o rapporto di compressione sospetto;
- template e versione riconosciuti;
- fogli e intestazioni validi;
- CSV decodificabile integralmente come UTF-8.

### Livello 2 - Celle

- obbligatorietà;
- lunghezza;
- tipo e formato;
- enum ammessi;
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
- presenza di movimenti sui conti che ricevono un saldo iniziale;
- strumenti già esistenti;
- stato attivo del tenant.

### Livello 5 - Preflight prima dell'applicazione

Subito prima di applicare, il backend ripete tutte le verifiche dipendenti dallo stato corrente. Se nel frattempo un altro utente ha creato un record in conflitto, il job torna `INVALID` con problemi nuovi e non avvia alcuna modifica esterna.

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
- `OPENING_BALANCE_NOT_ALLOWED`;
- `KEYCLOAK_IDENTITY_WILL_BE_LINKED`;
- `EXISTING_RECORD_WILL_BE_REUSED`.

Gli `ERROR` bloccano l'applicazione. I `WARNING` richiedono una conferma unica nel passo finale e restano nel rapporto di audit.

Il rapporto XLSX riproduce i fogli caricati aggiungendo colonne finali `esito`, `codici_problema` e `messaggi`. Qualunque testo che inizi con `=`, `+`, `-` o `@` viene neutralizzato nell'export per impedire formula injection.

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

- riferimento alla riga utente;
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

Un errore infrastrutturale durante la validazione produce `FAILED` senza operazioni identità. Un job `FAILED` può essere validato nuovamente soltanto quando il journal dimostra che non esistono effetti esterni pendenti.

`COMPENSATION_REQUIRED` richiede l'intervento di un Super Admin o Admin tramite il comando **Ritenta ripristino**. Non è possibile applicare o cancellare il job finché la compensazione non termina.

Un job `COMPLETED` è terminale. Chiamate duplicate al comando di applicazione restituiscono lo stesso riepilogo senza eseguire nuove scritture.

## Atomicità tra PostgreSQL e Keycloak

PostgreSQL e Keycloak non condividono una transazione distribuita. La funzionalità usa quindi una saga esplicita e compensabile.

### Fase A - Preflight

1. acquisire il lock pessimista sul job;
2. acquisire un advisory lock PostgreSQL per il tenant;
3. verificare stato `READY`, hash del file, tenant attivo e autorità;
4. ripetere i controlli dinamici;
5. creare il piano immutabile delle operazioni identità.

Nessuna chiamata Keycloak avviene se il preflight fallisce.

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

Le chiamate pubbliche dei normali servizi non vengono concatenate riga per riga. Un servizio di importazione dedicato riusa le stesse funzioni di validazione e mapping, ma controlla esplicitamente transazione, audit e soppressione delle notifiche granulari.

Se la transazione fallisce, nessun dato PostgreSQL del pacchetto resta applicato e inizia la compensazione Keycloak.

### Fase D - Finalizzazione

Dopo il commit:

1. marcare il job `COMPLETED`;
2. pubblicare una sola notifica di riepilogo all'autore e agli amministratori previsti;
3. inviare le e-mail di configurazione ai soli utenti creati dal job, se richiesto;
4. registrare separatamente ogni esito e-mail.

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
- La chiave di upload è univoca per tenant e autore e restituisce lo stesso job in caso di retry HTTP.
- La chiave di applicazione è salvata sul job; una seconda chiave dopo l'avvio produce `409 Conflict`.
- Il job viene bloccato con `PESSIMISTIC_WRITE` durante ogni transizione.
- Le query di applicazione verificano nuovamente i vincoli naturali sotto transazione.
- I worker selezionano job con `FOR UPDATE SKIP LOCKED` per supportare più istanze backend.
- Nessun job può essere cancellato dopo l'ingresso in `APPLYING`.

## API REST

Tutti gli endpoint sono tenant-scoped e riservati a Super Admin e Admin.

### Template e contesto

```http
GET /api/onboarding/context
GET /api/onboarding/templates/xlsx
GET /api/onboarding/templates/csv?section=USERS
```

`context` restituisce identità del tenant attivo, stato dello schema, limite e conteggio utenti, conteggi per dominio e versioni template supportate.

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

`DELETE` annulla soltanto job `UPLOADED`, `INVALID` o `READY`; è idempotente e non elimina immediatamente l'audit minimo.

### Anteprima e rapporti

```http
GET /api/onboarding/imports/{id}/sections
GET /api/onboarding/imports/{id}/rows?section=USERS&status=WARNING&page=0&size=50
GET /api/onboarding/imports/{id}/issues?severity=ERROR&section=USERS&page=0&size=50
GET /api/onboarding/imports/{id}/validation-report
GET /api/onboarding/imports/{id}/final-report
```

Le pagine hanno dimensione massima 100. Rapporti e anteprime usano `Cache-Control: no-store`. Il backend non restituisce il payload JSONB grezzo, ma DTO espliciti per sezione che escludono campi interni.

### DTO job indicativo

```json
{
  "id": 42,
  "fileName": "avvio-banda.xlsx",
  "format": "XLSX",
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
  "setupEmailFailures": 0
}
```

Lo stato del job è ottenuto con polling adattivo: ogni due secondi per i primi trenta secondi, poi ogni cinque secondi finché la pagina è visibile. La risposta supporta `ETag` o `If-Modified-Since` per ridurre payload invariati.

## Architettura backend

### Componenti

```text
OnboardingResource
        |
OnboardingImportService
        +-- OnboardingTemplateService
        +-- OnboardingFileInspectionService
        +-- OnboardingValidationService
        +-- OnboardingApplicationService
        +-- OnboardingIdentitySagaService
        +-- OnboardingReportService
        +-- OnboardingCleanupScheduler
```

Responsabilità:

- `OnboardingResource`: contratto HTTP, autorizzazioni dichiarative e pagination;
- `OnboardingTemplateService`: genera template XLSX e CSV dalla versione supportata;
- `OnboardingFileInspectionService`: controlli di sicurezza e parsing streaming;
- `OnboardingValidationService`: normalizzazione, problemi e staging;
- `OnboardingApplicationService`: preflight e transazione PostgreSQL;
- `OnboardingIdentitySagaService`: Keycloak, journal e compensazione;
- `OnboardingReportService`: rapporto errori e riepilogo finale;
- `OnboardingCleanupScheduler`: retention di file e staging.

La validazione e l'applicazione vengono eseguite da un worker persistente che legge i job pronti con lock. Non si riusa `upload_job`, perché quel modello descrive la lavorazione di PDF musicali e ha semantica, stati e relazioni incompatibili.

### Parsing

Il backend possiede già Apache POI. Per XLSX si usa l'API SAX/event model (`XSSFReader`) invece di caricare l'intero workbook in memoria. Devono essere configurati limiti espliciti per ZIP bomb, dimensione dei record condivisi e numero di celle.

Per CSV si introduce Apache Commons CSV con versione fissata nel `pom.xml`. Non si implementa un parser artigianale basato su `split`, perché virgolette, ritorni a capo e delimitatori nei valori renderebbero il formato fragile.

Il parser produce record intermedi tipizzati e non entità JPA. Le formule vengono rifiutate anche se possiedono un valore calcolato memorizzato.

### Storage del file

Il file originale viene salvato tramite il catalogo `media_asset` nella categoria `onboarding-imports`, con MIME rilevato, dimensione e SHA-256. La chiave di storage è tenant-scoped e non contiene nome del tenant, e-mail o nome file originale.

Il job conserva il digest atteso. Prima della validazione e dell'applicazione il contenuto viene verificato; una discrepanza porta il job in `FAILED` con codice `SOURCE_INTEGRITY_FAILURE`.

### Riutilizzo della logica di dominio

Le regole condivise devono essere estratte in validatori o factory riutilizzabili. L'importatore non deve duplicare in SQL le decisioni del servizio economico su esercizi e saldi iniziali né quelle dell'inventario su valuta e condizione.

Le scritture massive non devono però invocare i controller o aprire una transazione per ogni riga. Il livello applicativo prepara le entità, usa `saveAll` a blocchi e scarica periodicamente il persistence context senza spezzare il commit logico.

### Audit e notifiche

Tutte le entità create hanno come autore il subject che ha avviato l'applicazione, non il thread tecnico del worker. Il job conserva sia `requestedBy` sia `executedBy`; nella prima versione coincidono, salvo ripresa amministrativa di una compensazione.

Durante il commit è attivo un contesto `BulkOperationContext` che impedisce la generazione di notifiche per ogni inserimento. Al completamento viene pubblicato un solo comando nell'outbox generalizzata con:

- sorgente amministrativa;
- chiave evento basata sul job;
- riepilogo numerico senza dati personali;
- collegamento `/onboarding/imports/{id}`.

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

- non fidarsi di estensione, MIME dichiarato o nome originale;
- impedire path traversal e non estrarre il contenuto XLSX sul filesystem applicativo;
- limiti POI contro ZIP bomb e decompressione eccessiva;
- rifiutare macro, formule, external relationship, DDE e collegamenti remoti;
- non risolvere URL contenuti nelle celle;
- neutralizzare formula injection nei rapporti esportati;
- non scrivere valori delle celle nei log;
- eseguire il parsing senza accesso di rete;
- usare nomi di file generati e storage tenant-scoped.

### Dati personali

I file possono contenere e-mail e date di nascita. Di conseguenza:

- anteprima e rapporti sono `no-store`;
- log e metriche non contengono valori importati;
- i problemi indicano posizione e regola senza duplicare il valore grezzo;
- lo staging non è indicizzato in OpenSearch;
- l'accesso usa sempre il tenant context;
- file, righe e problemi vengono rimossi dalla retention;
- il rapporto scaricato è responsabilità dell'utente e la UI lo ricorda.

## Retention

Valori predefiniti configurabili:

- job `INVALID`, `CANCELLED` o `FAILED` senza compensazione: file e staging eliminati dopo 30 giorni;
- job `COMPLETED`: file, righe e problemi eliminati dopo 30 giorni;
- job `COMPENSATION_REQUIRED`: nessuna pulizia automatica finché non è risolto;
- metadati minimi del job completato: conservati 365 giorni;
- journal identità: ridotto ai soli esiti e identificatori tecnici dopo 30 giorni, eliminato dopo 365;
- rapporti generati: eliminati insieme al file sorgente.

Dopo la pulizia il job resta consultabile con conteggi, hash, versione template, autore, esito e date, ma anteprima e download non sono più disponibili.

La cancellazione usa `MediaService` e lo scheduler esistente per rimuovere in sicurezza asset non più referenziati.

## Configurazione

```yaml
application:
  onboarding:
    enabled: true
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
```

Le proprietà sono validate all'avvio. `max-user-rows` non può superare `max-total-rows`; retention e batch size devono essere positivi.

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

I passi completati possono essere riaperti finché il job non entra in `APPLYING`. Dopo l'avvio, navigazione e selezioni sono bloccate e resta disponibile soltanto la vista di stato.

Il file usa `p-fileupload` con `customUpload`, `multiple=false`, caricamento non automatico e limite locale coerente con il backend. Il componente mantiene il proprio input nativo accessibile; l'area drag-and-drop non è l'unico modo per selezionare il file.

### Anteprima

- `p-table` paginata lato server;
- filtri per sezione ed esito;
- colonne variabili ma definite dal DTO della sezione;
- riga e cella con errore collegate tramite `aria-describedby` al messaggio;
- `p-tag` con testo `Valida`, `Avviso`, `Errore`, `Riutilizzata`;
- nessun editor di cella;
- vista compatta a schede sotto 768 pixel per evitare tabelle orizzontali ingestibili.

### Progresso e stati

`p-progressbar` mostra percentuale e descrizione testuale della fase. Il contenitore espone `aria-busy`; gli avanzamenti non vengono annunciati a ogni punto percentuale, ma soltanto al cambio fase tramite live region `polite`.

Stati vuoti distinti:

- nessun job: invito a scaricare un template;
- nessuna riga nella sezione: spiegazione che il foglio non contiene dati;
- nessun problema: conferma **Nessun errore rilevato**;
- filtro senza risultati: azione **Azzera filtri**.

### Uscita e ripresa

Prima dell'upload il normale guard delle modifiche non salvate avvisa se è stato selezionato un file. Dopo che il server ha creato il job, la navigazione è libera perché lo stato è persistito.

Durante `APPLYING` la chiusura della pagina non interrompe il worker. Al ritorno, il frontend carica lo stato tramite ID e riprende il polling.

### Errori frontend

- `413`: file troppo grande con limite leggibile;
- `415`: formato non supportato;
- `409`: conflitto di stato, tenant cambiato o altro job in applicazione;
- `422`: file leggibile ma non conforme al template;
- errore di rete dopo upload: recupero tramite stessa `Idempotency-Key`;
- risultato `COMPENSATION_REQUIRED`: banner `danger`, istruzione operativa e comando di retry autorizzato.

## Osservabilità

Metriche Micrometer:

- counter `taurus.onboarding.jobs.created` con tag `format`;
- counter `taurus.onboarding.jobs.completed`;
- counter `taurus.onboarding.jobs.failed` con tag `stage`;
- counter `taurus.onboarding.compensations` con tag `result`;
- counter `taurus.onboarding.setup_emails` con tag `result`;
- timer `taurus.onboarding.validation.duration`;
- timer `taurus.onboarding.application.duration`;
- summary `taurus.onboarding.rows` con tag `section`.

Tenant, job ID, e-mail e autore non sono tag metrici. I log strutturati possono contenere tenant e job ID, oltre a stato, fase, durata, conteggi e codice errore; non contengono valori delle righe.

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

Un nuovo changelog tenant crea:

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

La garanzia di un solo job `APPLYING` viene ottenuta con advisory lock, perché il lock deve coordinare più stati tecnici della saga e non soltanto un valore di colonna.

## Piano di implementazione

### Fase 1 - Contratto e staging

1. aggiungere changelog, enum, DTO e proprietà validate;
2. creare resource, repository e transizioni di stato;
3. integrare `media_asset` e retention;
4. implementare idempotency key e isolamento tenant;
5. coprire il modello con integration test PostgreSQL.

### Fase 2 - Template e validazione

1. generare template XLSX e CSV versionati;
2. introdurre parsing streaming e controlli file;
3. implementare normalizzazione e validatori per sezione;
4. salvare righe e problemi di staging;
5. produrre rapporto errori sicuro.

### Fase 3 - Applicazione dei domini PostgreSQL

1. strumenti e riferimenti locali;
2. inventario;
3. categorie e conti;
4. esercizi e saldi iniziali;
5. transazione unica, audit e soppressione notifiche granulari;
6. preflight concorrente e prove di rollback.

### Fase 4 - Identità Keycloak

1. piano e journal delle operazioni;
2. creazione e collegamento utenti;
3. membership e dati `app_user` nel commit PostgreSQL;
4. compensazione idempotente e ripresa;
5. e-mail post-commit e retry selettivo;
6. test con Keycloak reale in container.

### Fase 5 - Wizard Angular

1. route e menu;
2. stepper, template e upload;
3. polling e riepilogo sezioni;
4. anteprima, problemi e rapporti;
5. conferma, avanzamento, esito e ripresa;
6. responsive e accessibilità.

### Fase 6 - Hardening e rilascio

1. test end-to-end completi;
2. file ostili e limiti di memoria;
3. test di carico con 5.000 righe;
4. simulazione di riavvio in ogni fase della saga;
5. metriche, dashboard e alert;
6. attivazione tramite feature flag prima in staging e poi in produzione.

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
30. il job da 5.000 righe rispetta i limiti di memoria e tempo approvati in staging.

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

## File e aree previste

### Backend da creare

```text
taurus-be/src/main/java/com/fundaro/zodiac/taurus/domain/onboarding/
taurus-be/src/main/java/com/fundaro/zodiac/taurus/repository/onboarding/
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/onboarding/
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/dto/onboarding/
taurus-be/src/main/java/com/fundaro/zodiac/taurus/web/rest/OnboardingResource.java
taurus-be/src/main/resources/config/liquibase/changelog/<timestamp>_tenant_onboarding_import.xml
```

### Backend da modificare

```text
taurus-be/pom.xml
taurus-be/src/main/java/com/fundaro/zodiac/taurus/config/ApplicationProperties.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/config/SecurityConfiguration.java
taurus-be/src/main/resources/config/application.yml
taurus-be/src/main/resources/config/liquibase/tenant-master.xml
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/impl/FinanceService.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/impl/InventoryService.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/impl/UsersServiceImpl.java
```

Le modifiche ai servizi esistenti devono estrarre validatori e factory condivisi; non devono aggiungere un ramo di importazione ai normali metodi REST riga per riga.

### Frontend da creare

```text
taurus-fe/src/app/module/onboarding/
taurus-fe/src/app/service/onboarding.service.ts
taurus-fe/src/app/pages/onboarding/onboarding.routes.ts
taurus-fe/src/app/pages/onboarding/onboarding.component.*
taurus-fe/src/app/pages/onboarding/import-detail/
taurus-fe/src/app/pages/onboarding/components/
```

### Frontend da modificare

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
