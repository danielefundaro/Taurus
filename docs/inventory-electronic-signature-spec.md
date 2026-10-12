# Firma elettronica del foglio di affidamento e della presa visione

La funzionalità consente di firmare in Taurus, senza carta e senza costi per il tenant, il [foglio di consegna e affidamento in comodato](inventory-loan-sheet-spec.md) e la presa visione delle assegnazioni descritta in [Gestione inventario tenant](inventory-management-spec.md#revisioni-e-presa-visione). La firma è una firma elettronica semplice confermata da un codice monouso; chi possiede la Carta d'Identità Elettronica può usare in alternativa CieSign, l'app gratuita del Poligrafico che produce una firma elettronica avanzata.

## Stato del documento

ID catalogo: `inventory-electronic-signature`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

Documenti collegati: [Foglio di consegna e affidamento in comodato](inventory-loan-sheet-spec.md), [Gestione inventario tenant](inventory-management-spec.md), [Gestione centralizzata dei media](media-asset-spec.md), [Standard degli errori applicativi](application-errors-standard.md), [Feed calendario esterno](external-calendar-feed-spec.md) (modello dei token pubblici), [PostgreSQL schema per tenant](postgres-tenant-schemas.md).

## Obiettivo

- Firmare senza carta l'emissione del foglio, le variazioni registrate nel foglio, il verbale di riconsegna e la presa visione delle assegnazioni.
- Non imporre costi al tenant: nessun provider di firma, nessun SMS, nessun certificato o marca temporale a pagamento.
- Lasciare all'utente maggiorenne la scelta del metodo di conferma: codice via e-mail oppure app di autenticazione.
- Coprire l'Affidatario genitore o tutore, che non ha un account Taurus.
- Raccogliere l'approvazione specifica delle clausole ex artt. 1341–1342 c.c. come atto distinto dall'accettazione del contratto.
- Funzionare con qualunque modello del foglio caricato dal tenant, purché dichiari le parti richieste.
- Produrre documenti a prova di manomissione e prove di firma verificabili.
- Mantenere la firma su carta come alternativa, per tenant, per foglio e per singolo atto.

## Fuori scope

- Provider di firma esterni, a pagamento o gratuiti, e invio di codici via SMS.
- Firma elettronica avanzata erogata dal tenant ai sensi del DPCM 22 febbraio 2013: identificazione certa, dichiarazione di accettazione conservata per 20 anni e copertura assicurativa sono oneri sproporzionati per un'associazione.
- Firma elettronica qualificata o digitale erogata da Taurus e firma con SPID.
- Firma grafometrica o immagine della firma autografa.
- Marca temporale qualificata e conservazione a norma presso un conservatore accreditato.
- Promemoria automatici per le firme in attesa oltre alla notifica iniziale.
- Firma di documenti diversi da foglio e presa visione: il meccanismo è riusabile, ma ogni nuovo uso richiede una specifica propria.

## Quadro giuridico

Questa sezione riassume i presupposti del progetto e non sostituisce un parere legale; i punti da verificare sono riportati in [Questioni aperte](#questioni-aperte).

| Tema | Riferimento | Conseguenza per il progetto |
| --- | --- | --- |
| Firma elettronica semplice | art. 3, n. 10, e art. 25 Reg. UE 910/2014; art. 20, c. 1-bis, CAD | la firma con codice è una prova valida, liberamente valutabile in giudizio |
| Firma elettronica avanzata | art. 20, c. 1-bis, CAD | la firma CieSign soddisfa la forma scritta con l'efficacia dell'art. 2702 c.c. |
| Comodato | art. 1803 c.c. | contratto reale a forma libera: la firma serve come prova, non come requisito di validità |
| Clausole vessatorie | artt. 1341–1342 c.c. | richiedono un'approvazione specifica per iscritto, distinta dall'accettazione del contratto |

La giurisprudenza riportata dalle riviste di settore ritiene insufficiente la sola spunta di una casella e ammette una firma elettronica semplice con codice OTP, purché distinta dall'accettazione generale (Cass. n. 9413/2021; ordinanze del 2026 citate da fonti secondarie e da verificare sul testo originale). Per questo l'approvazione delle clausole è un documento separato con una propria conferma.

## Decisioni principali

1. **Firma semplice con codice monouso.** Ogni firma richiede un codice valido per quella sola conferma. È il metodo predefinito per soci maggiorenni, socio minorenne e amministratori.
2. **Due metodi di conferma.** Codice via e-mail, generato da Taurus, oppure codice dell'app di autenticazione, verificato da Keycloak. L'utente sceglie il metodo preferito e può sempre usare l'altro.
3. **App di autenticazione gestita da Keycloak.** Taurus non conserva segreti TOTP. La credenziale è la stessa del login: chi la configura riceve la richiesta del codice anche a ogni accesso, come già previsto dal flusso del realm.
4. **CieSign facoltativo per l'Affidatario.** Il socio maggiorenne o il genitore può firmare i documenti PDF con CieSign e caricarli in Taurus, che verifica firma, certificato e integrità.
5. **Genitore o tutore senza account.** Riceve un link monouso via e-mail e firma con codice via e-mail o con CieSign. La firma su carta resta disponibile.
6. **Documenti congelati.** Ogni documento da firmare è generato una sola volta, salvato e identificato dal suo hash SHA-256. Un documento firmato non viene mai rigenerato.
7. **Atti separati.** Emissione, ogni variazione del registro e verbale di riconsegna sono atti distinti, ciascuno con i propri documenti e firmatari. La presa visione di una revisione è un atto senza PDF.
8. **Approvazione delle clausole come documento a sé.** All'emissione l'Affidatario firma due documenti: il foglio e l'approvazione specifica delle clausole, generata dal blocco che il modello dichiara.
9. **Indipendenza dal modello.** Il modello indica con attributi `data-taurus-act` e `data-taurus-sign` le parti di ogni atto e il blocco delle clausole. Un modello senza questi marcatori resta utilizzabile su carta.
10. **Prove immutabili in catena.** Ogni firma produce una prova immutabile, collegata alla precedente del tenant tramite hash.
11. **Sigillo e attestazione.** A firme complete Taurus appone a ogni documento una firma PAdES con il certificato dell'installazione e genera un'attestazione separata con le prove.
12. **Attivazione per tenant, modalità per foglio.** La firma elettronica si abilita nelle impostazioni del foglio di affidamento ed è disattivata per default. Il foglio sceglie all'emissione la modalità `ELECTRONIC` o `PAPER`; un amministratore può chiudere su carta un singolo atto.
13. **Conservazione degli atti firmati.** Documenti firmati, attestazioni e prove sono conservati per 10 anni dopo la definizione o l'annullamento del foglio, anche dopo la rimozione dei dati personali dalle tabelle e la cancellazione GDPR.
14. **Nessun costo.** L'unico requisito esterno è il server SMTP della casella e-mail già usata dall'associazione.

## Contesto tecnico acquisito

- `InventoryLoanSheetService.generate()` rigenera il PDF a ogni download e salva ogni copia in `inventory_loan_sheet_export`; annullamento e definizione chiamano `deletePdfs()`, la definizione anche `purgePersonalData()`.
- `LoanTemplateValidator` controlla solo `style` e un elenco chiuso di attributi di risorsa: gli attributi `data-*` sono già ammessi. Le segnalazioni sono `Issue(code, args, line, blocking)` e il frontend le traduce come `inventory.loanTemplate.issue.<code>`.
- Il catalogo `LoanSheetVariables` descrive ogni variabile con nome, tipo (`TEXT`, `FLAG`, `LIST`), descrizione ed esempio.
- La presa visione (`InventoryService.decide`) confronta l'hash della revisione con `MessageDigest.isEqual` e salva in `inventory_assignment_decision` l'hash SHA-256 di `snapshotHash|userId|DECISION|reason`. Non esiste un endpoint di decisione multipla.
- `spring-boot-starter-mail` è nel `pom.xml`, ma nessuna classe invia e-mail e nessun profilo configura `spring.mail`.
- Le preferenze utente sono coppie chiave/valore della tabella `preferences`, esposte da `/api/preferences`.
- Il profilo avvia già l'azione Keycloak `CONFIGURE_TOTP` (`profile.component.ts`). Il flusso "Browser with Tenant and Role" chiede l'OTP dopo la selezione di tenant e ruolo solo agli utenti che l'hanno configurato. La policy OTP del realm è TOTP a 6 cifre, periodo 30 secondi, look-ahead 1, codice non riutilizzabile.
- Nessun componente legge i claim `acr`, `amr` o `auth_time`.
- Il frontend inizializza Keycloak con `onLoad: 'login-required'`: oggi nessuna rotta Angular è raggiungibile senza login.
- Il feed calendario risolve il tenant di una richiesta anonima con il registro `public.calendar_feed_token_registry` e limita le richieste con `CalendarFeedRateLimiter`, basato su Caffeine.
- `MediaRepository.isUnreferenced` elenca le tabelle che usano un media; la pulizia rimuove solo i file dei media non più attivi.
- Le notifiche interne si creano con `NotificationOutboxPublisher.enqueue(NotificationCommand)` dentro una transazione.
- BouncyCastle è presente come `bcprov-jdk15on` 1.70, usato dalla libreria Web Push.
- Il modulo `keycloak-authenticator` è compilato con Keycloak 26.6.0, mentre `keycloak.yml` avvia l'immagine 26.5.2.

## Terminologia

| Termine | Significato |
| --- | --- |
| Atto | operazione da firmare: emissione, variazione, riconsegna o presa visione |
| Documento | contenuto congelato di un atto, identificato da ruolo e hash SHA-256 |
| Firmatario | persona che deve firmare un atto, con un ruolo |
| Conferma | richiesta di codice legata a un firmatario, a uno scopo e ai documenti confermati |
| Prova | registrazione immutabile di una firma |
| Sigillo | firma PAdES apposta da Taurus a firme complete |
| Attestazione | PDF separato e sigillato che riepiloga documenti e prove di un atto |

## Atti e firmatari

### Tipi di atto

| Tipo | Origine | Documenti | Firmatari |
| --- | --- | --- | --- |
| `LOAN_SHEET_ISSUE` | emissione di un foglio `ELECTRONIC` | `MAIN`: parti `issue` del modello (sezioni 1–7 e 10 del predefinito); `CLAUSES`: approvazione specifica delle clausole | Affidatario, Associazione; socio minorenne per presa visione con ruolo `GUARDIAN` |
| `LOAN_SHEET_VARIATION` | ogni operazione registrata nella sezione 8 secondo la specifica del foglio: consegna successiva (C), restituzione (R), sostituzione (S), rinnovo (N) | `MAIN`: parti `variation` con le sole righe dell'operazione | Affidatario, Associazione |
| `LOAN_SHEET_RETURN` | chiusura del foglio | `MAIN`: parti `return`, cioè il verbale di riconsegna | Affidatario, Associazione |
| `ASSIGNMENT_ACKNOWLEDGMENT` | conferma di una presa visione | `SNAPSHOT`: hash dello snapshot della revisione, senza file | assegnatario |

### Ruoli dei firmatari

| Ruolo | Chi firma | Obbligatorio | Conferme |
| --- | --- | --- | --- |
| `HOLDER` | socio maggiorenne (`MEMBER`) o genitore/tutore (`GUARDIAN`) | sì | `ACCEPTANCE` su `MAIN`; per l'emissione anche `CLAUSE_APPROVAL` su `CLAUSES` |
| `MINOR` | socio minorenne, dal proprio account | no | `MINOR_ACKNOWLEDGMENT` su `MAIN` dell'emissione |
| `ASSOCIATION` | amministratore designato | sì | `ASSOCIATION_SIGNATURE` su `MAIN` |
| `ASSIGNEE` | assegnatario dell'oggetto | sì | `ASSIGNMENT_DECISION` sullo snapshot |

Il firmatario per l'Associazione dell'emissione è scelto nella finestra di emissione tra gli admin e super admin attivi del tenant, con l'amministratore corrente come valore predefinito; il suo nome è stampato come `foglio.firmatario`. Per variazioni e riconsegna è l'amministratore che esegue l'operazione. La firma del socio minorenne è richiesta e notificata, ma non blocca il completamento dell'atto.

### Stati dell'atto

| Stato | Significato |
| --- | --- |
| `AWAITING_DATA` | solo emissione: in attesa dei dati che l'Affidatario inserisce al posto della compilazione a mano |
| `AWAITING_SIGNATURES` | documenti congelati, mancano firme obbligatorie |
| `SIGNED` | firme obbligatorie raccolte, sigillo e attestazione generati |
| `SIGNED_ON_PAPER` | un amministratore ha dichiarato l'atto firmato su carta |
| `CANCELLED` | atto annullato con il foglio |

Variazioni e riconsegna nascono in `AWAITING_SIGNATURES`. La presa visione nasce direttamente `SIGNED` alla conferma del codice.

Le firme possono arrivare in qualsiasi ordine. Finché nessuno ha firmato, l'Affidatario può correggere i propri dati: Taurus genera nuovi documenti congelati e marca come eliminati i precedenti. Dopo la prima firma i documenti non cambiano più; un errore si corregge annullando il foglio ed emettendone uno nuovo, come per i fogli su carta.

## Modalità di firma

### Attivazione per tenant

Nuovo campo `electronic_signature_enabled` di `inventory_loan_settings`, falso per default, modificabile da admin e super admin. L'attivazione è rifiutata se l'installazione non ha SMTP e certificato del sigillo configurati.

Con la firma elettronica attiva:

- i nuovi fogli propongono la modalità `ELECTRONIC`;
- la presa visione richiede un codice di conferma e non è più accettata dall'endpoint diretto attuale;
- la pagina del modello mostra se il modello in uso è firmabile.

La disattivazione vale per nuovi fogli e nuove prese visione: gli atti già in attesa restano firmabili fino al completamento o all'annullamento.

### Modalità del foglio

Nuovo campo `signature_mode` di `inventory_loan_sheet` (`PAPER`, `ELECTRONIC`), scelto all'emissione e non modificabile. I fogli esistenti sono `PAPER`. L'emissione `ELECTRONIC` richiede:

- firma elettronica attiva nel tenant;
- un modello firmabile (vedi [Indipendenza dal modello](#indipendenza-dal-modello));
- con ruolo `GUARDIAN`, l'e-mail del genitore o tutore.

In un foglio `ELECTRONIC` lo stato del foglio segue le regole esistenti; la firma è uno stato degli atti, mostrato accanto al foglio. Il PDF completo del foglio resta scaricabile come copia di consultazione, con la dicitura `COPIA DI CONSULTAZIONE – NON FIRMATA`; i documenti con valore probatorio sono gli atti firmati.

### Firma su carta di un singolo atto

Un amministratore può dichiarare firmato su carta un atto in `AWAITING_DATA` o `AWAITING_SIGNATURES`, indicando una nota obbligatoria e confermando con il proprio codice. L'atto passa a `SIGNED_ON_PAPER`, la prova registra il metodo `PAPER_DECLARATION` e la copia cartacea resta in custodia al tenant. Serve quando il genitore non può firmare elettronicamente o per problemi tecnici.

## Metodi di conferma

### Codice via e-mail

- Sei cifre generate con `SecureRandom`. Nel database si salva solo l'HMAC-SHA256 del codice con la chiave `application.signature.otp-secret`; il confronto avviene a tempo costante.
- Validità 10 minuti, al massimo 5 tentativi, uso singolo. Un nuovo invio è possibile dopo 60 secondi, fino a 5 invii per conferma; ogni invio sostituisce il codice precedente.
- Il codice è legato al firmatario, allo scopo e all'hash dell'elenco dei documenti confermati: non vale per altri atti.
- Il messaggio indica tenant, tipo di atto, numero del foglio, prime 8 cifre dell'hash del documento e scadenza del codice. Non contiene link.
- L'invio usa `spring.mail.*` e il mittente `jhipster.mail.from`, tramite un nuovo `SignatureMailService` basato su `JavaMailSender`. L'e-mail parte dopo il commit; un invio fallito chiude la conferma con `signature.mail.failed`.
- Destinatario: l'e-mail dell'account Keycloak del firmatario oppure, per il genitore, l'e-mail registrata sul foglio.

### App di autenticazione

- Usa la credenziale OTP di Keycloak, configurata dal profilo con l'azione `CONFIGURE_TOTP` già presente e rimovibile dalla console account di Keycloak.
- Il codice è verificato da un'estensione del modulo `keycloak-authenticator`, un realm resource provider `taurus-otp`:
  - `GET /realms/{realm}/taurus-otp/users/{userId}/status` restituisce `{ "configured": true|false }`;
  - `POST /realms/{realm}/taurus-otp/users/{userId}/verify` con `{ "code": "123456" }` restituisce `204` se valido, `422` altrimenti;
  - accetta solo il bearer token del client confidenziale `taurus-signature`, con service account e ruolo client `otp-verifier`; ogni altro chiamante riceve `403`;
  - verifica con `user.credentialManager().isValid(UserCredentialModel.otp(code))`, rispettando la policy del realm; con `otpPolicyCodeReusable: false` lo stesso codice è rifiutato al secondo uso, anche se già usato per il login;
  - non registra il codice nei log.
- Il backend ottiene il token con client credentials (`application.signature.keycloak.client-id`, segreto da variabile d'ambiente) e passa sempre il `sub` del firmatario autenticato, mai un valore ricevuto dal client.
- I tentativi sono contati anche nella conferma Taurus, con gli stessi limiti del codice via e-mail.
- Se Keycloak non risponde, la conferma fallisce con `signature.method.unavailable` e l'utente può passare al codice via e-mail.

### Preferenza dell'utente

- Chiave `signature.method` nella tabella `preferences`, con valori `EMAIL` (predefinito) e `TOTP`.
- `GET /api/user/signatures/methods` restituisce metodo preferito, stato dell'app ed e-mail mascherata.
- Se la preferenza è `TOTP` ma l'app non è configurata, Taurus usa `EMAIL` e il profilo lo segnala.
- La finestra di firma propone il metodo preferito e offre **Usa l'altro metodo**.

### CieSign

Disponibile per il firmatario `HOLDER` sui documenti PDF degli atti del foglio, non per la presa visione.

1. Il firmatario scarica il documento congelato.
2. Lo firma in formato PAdES con l'app CieSign o con il Software CIE.
3. Carica il file firmato in Taurus, al massimo 25 MB.

Taurus accetta il file solo se:

- inizia con i byte esatti del documento congelato, perché la firma PAdES aggiunge un aggiornamento incrementale;
- contiene una sola firma nuova, che copre il file fino all'ultimo byte;
- la firma è crittograficamente valida;
- il certificato risale alle CA radice e intermedia della CIE del Ministero dell'Interno, configurate come trust anchor in `application.signature.cie.trust-anchors`, ed era valido alla data di caricamento;
- nome e cognome del certificato corrispondono al firmatario atteso (anagrafica del socio o dati inseriti dal genitore), senza distinzione di maiuscole, accenti e spazi;
- il codice fiscale del certificato coincide con quello dichiarato nei dati dell'Affidatario.

La revoca è controllata con la CRL indicata nel certificato, con un timeout di 10 secondi; se la CRL non è raggiungibile la firma è accettata e la prova registra `revocationCheck = UNAVAILABLE`. All'emissione, con CieSign si firmano due file: `MAIN` e `CLAUSES`. Un firmatario può usare CieSign per un documento e il codice per l'altro.

La verifica usa PDFBox e BouncyCastle. Per i messaggi CMS serve `bcpkix`: `bcprov-jdk15on` va sostituito con `bcprov-jdk18on` e `bcpkix-jdk18on`, escludendo la dipendenza transitiva di `web-push` e verificando i test Web Push. Le due famiglie di artefatti non devono convivere nel classpath.

## Firma del genitore o tutore

- Con ruolo `GUARDIAN` e modalità `ELECTRONIC`, l'amministratore inserisce all'emissione l'e-mail del genitore, salvata in `inventory_loan_sheet.holder_email` e rimossa alla definizione con gli altri dati personali.
- Per ogni atto da firmare Taurus invia un link `{public-base-url}/firma/{token}`. La base URL viene dalla configurazione (`application.signature.public-base-url`), mai dall'header `Host`.
- Il token è di 32 byte casuali in base64url. Si salvano solo il digest SHA-256 e un'impronta di 8 caratteri per l'interfaccia; il token non contiene tenant, atto o utente.
- Il tenant è risolto con il nuovo registro `public.signature_link_registry`, come per il feed calendario, accettando solo tenant e schema `ACTIVE`.
- Il link vale 7 giorni e un solo firmatario di un solo atto. Un nuovo invio revoca il precedente; il link è revocato anche a firma completata o atto annullato.
- La pagina pubblica mostra tenant, tipo di atto e numero del foglio, raccoglie i dati dell'emissione e offre codice via e-mail, inviato allo stesso indirizzo, oppure CieSign.
- Limite noto: link e codice arrivano sulla stessa casella. La firma prova il controllo della casella; CieSign offre un'identificazione più forte ed è il metodo consigliato nella pagina.

## Indipendenza dal modello

### Marcatori

| Marcatore | Regola |
| --- | --- |
| `data-taurus-act` | obbligatorio su ogni figlio diretto di `<body>`; uno o più valori separati da spazio tra `issue`, `variation`, `return` e `all` |
| `data-taurus-sign="clauses"` | esattamente un elemento, dentro un blocco `issue`, con testo non vuoto |

Per ogni atto Taurus compila il modello e, prima della conversione in PDF, rimuove con jsoup i figli di `<body>` che non dichiarano il tipo dell'atto o `all`. Intestazione e piè di pagina ricorrenti usano `all`.

Il documento `CLAUSES` usa un modello interno non personalizzabile (`templates/signature/clauses-approval.html`) con denominazione del tenant, numero del foglio, hash del documento `MAIN`, testo del blocco marcato, ricavato come testo semplice con spazi normalizzati, e dichiarazione di approvazione specifica.

### Segnalazioni del validatore

Nuovi codici non bloccanti per l'attivazione del modello. Rendono il modello non firmabile e l'emissione `ELECTRONIC` risponde `inventory.loanSheet.templateNotSignable`.

| Codice | Caso |
| --- | --- |
| `signatureActMissing` | figlio di `<body>` senza `data-taurus-act` |
| `signatureActUnknown` | valore non ammesso in `data-taurus-act` |
| `signatureActEmpty` | nessun blocco per uno dei tipi `issue`, `variation`, `return` |
| `signatureClausesMissing` | nessun elemento `data-taurus-sign="clauses"` |
| `signatureClausesDuplicate` | più elementi `data-taurus-sign="clauses"` |
| `signatureClausesOutsideIssue` | blocco delle clausole fuori da un blocco `issue` |
| `signatureClausesEmpty` | blocco delle clausole senza testo |

La firmabilità è calcolata dal validatore a ogni uso: le versioni dei modelli sono immutabili e non serve una colonna dedicata.

### Variabili

| Variabile | Tipo | Valore |
| --- | --- | --- |
| `firma.elettronica` | FLAG | il documento è un atto da firmare elettronicamente |
| `firma.dicitura` | TEXT | `Firmato elettronicamente – vedi attestazione`, da stampare al posto delle righe per la firma |
| `atto.emissione`, `atto.variazione`, `atto.riconsegna` | FLAG | tipo dell'atto in generazione |
| `affidatario.luogoNascita`, `affidatario.residenza`, `affidatario.cap`, `affidatario.cittaProvincia`, `affidatario.codiceFiscale`, `affidatario.documento`, `affidatario.documentoRilasciatoDa`, `affidatario.documentoRilasciatoIl`, `affidatario.telefono` | TEXT | dati inseriti dall'Affidatario prima della firma; vuoti su carta |
| `minore.luogoNascita` | TEXT | luogo di nascita del socio minorenne, inserito dal genitore |

Con ruolo `GUARDIAN` e modalità `ELECTRONIC`, `affidatario.nomeCognome`, `affidatario.dataNascita` e `affidatario.email` contengono i dati del genitore. Nella variazione `registro` contiene solo le righe dell'atto e `registroRigheVuote` è vuoto; nella riconsegna `riconsegna.righeVuote` è vuoto.

I dati inseriti dall'Affidatario non sono salvati in colonne del database: entrano solo nel documento congelato.

### Modello predefinito

Una nuova versione del predefinito:

- marca ogni blocco con `data-taurus-act` e il paragrafo dell'approvazione specifica con `data-taurus-sign="clauses"`;
- stampa i dati delle sezioni 1 e 2 con le nuove variabili, lasciando la riga vuota quando mancano;
- stampa `firma.dicitura` al posto delle righe per la firma e omette la sigla dell'Affidatario a piè di pagina quando `firma.elettronica` è vero;
- aggiorna la lettera f) dell'informativa per i fogli firmati elettronicamente, indicando la conservazione degli atti firmati.

I fogli già emessi conservano la copia del predefinito con cui sono stati emessi. I tenant con un modello caricato devono aggiungere i marcatori e adeguare la propria informativa: la pagina del modello lo segnala quando si attiva la firma elettronica.

## Documenti, sigillo e attestazione

### Documento congelato

- Generato una volta con `LoanSheetPdfRenderer` e il filtro dei marcatori, salvato con `MediaService.store` nella categoria `signature-acts`, con il limite di 20 MB già previsto.
- L'hash SHA-256 è registrato in `signature_document.frozen_hash` e mostrato al firmatario; ogni conferma invia l'hash visualizzato e il server rifiuta hash diversi.
- I download leggono sempre il file salvato.

### Sigillo

- Quando l'atto passa a `SIGNED`, Taurus aggiunge a ogni documento una firma PAdES-B-B, o B-T se è configurata una TSA, come aggiornamento incrementale. Le firme CieSign già presenti restano valide.
- Il certificato è unico per l'installazione: un keystore PKCS#12 indicato da `application.signature.seal.keystore-path`, con password da variabile d'ambiente. Può essere autofirmato o emesso da una CA interna; il runbook operativo indicherà il comando `keytool` per crearlo. Il keystore non va mai nel repository.
- Nel profilo `dev`, senza keystore, Taurus genera all'avvio un certificato effimero e registra un avviso. In produzione la firma elettronica non si attiva senza keystore.
- `application.signature.seal.tsa-url` è vuoto per default. Una TSA gratuita è un servizio esterno e resta una scelta dell'operatore.
- Acrobat mostra il sigillo come firma di identità sconosciuta finché il certificato non è tra le identità attendibili; l'integrità del file resta comunque verificabile e l'attestazione spiega come controllarla.

### Attestazione

PDF separato, generato dal modello interno `templates/signature/attestation.html` e sigillato. È separato dal documento perché aggiungere pagine dopo una firma CieSign la invaliderebbe. Contiene:

- tenant, tipo di atto, numero del foglio;
- per ogni documento: ruolo, hash congelato, hash dopo il sigillo;
- per ogni firma: ruolo, nome, scopo, metodo, data e ora nel fuso del tenant e in UTC, e-mail mascherata oppure soggetto e codice fiscale del certificato, indirizzo IP, posizione e hash della prova nella catena;
- le istruzioni per verificare hash e sigillo.

## Prove e catena

Ogni firma crea una riga immutabile di `signature_evidence` con:

- atto, documento, firmatario, scopo e metodo;
- hash del documento, testo della dichiarazione e suo hash;
- nome del firmatario, identificativo Keycloak se presente, e-mail di destinazione del codice;
- per CieSign: soggetto, emittente, numero di serie e codice fiscale del certificato, esito del controllo di revoca;
- conferma usata, `sid` del token, indirizzo IP, user agent, data e ora UTC del server;
- numero progressivo nel tenant, hash della prova precedente e hash della prova.

L'hash della prova è lo SHA-256 del JSON canonico della riga, inclusi progressivo e hash precedente. L'inserimento blocca con lock pessimistico la riga di `signature_chain_head`, come il contatore dei fogli. `POST /api/inventory/signatures/verify-chain` ricalcola la catena del tenant e restituisce la prima posizione non coerente.

Alla scadenza della conservazione nome, e-mail, IP, user agent e dati del certificato vengono cancellati e la riga riceve `purged_at`; hash e collegamenti restano, così la continuità della catena resta verificabile.

## Presa visione con codice

Con la firma elettronica attiva:

1. L'utente sceglie accettazione o rifiuto, con motivazione, per una o più assegnazioni, fino a 50.
2. `POST /api/user/signatures/challenges` con scopo `ASSIGNMENT_DECISION` ed elementi `{ assignmentId, revisionHash, decision, rejectionReason }`. Il server applica a ogni elemento i controlli di `InventoryService.decide`: proprietario, hash corrente, decisione non ancora presa, motivazione per il rifiuto.
3. Alla conferma del codice, in una sola transazione, Taurus registra ogni decisione con l'hash autenticato attuale, crea un atto `ASSIGNMENT_ACKNOWLEDGMENT` firmato con documento `SNAPSHOT` e una prova, e collega la decisione alla prova (`inventory_assignment_decision.signature_evidence_id`).
4. Se nel frattempo una revisione è cambiata, l'intera conferma fallisce con `inventory.revision.versionConflict` e viene chiusa.

La dichiarazione registrata è «Ho preso visione dei dati e delle fotografie dell'assegnazione e li accetto» oppure «… e li rifiuto per il seguente motivo: …», con nome dell'oggetto, numero inventariale e numero di revisione.

`POST /api/user/inventory/assignments/{id}/decision` risponde `409 inventory.decision.signatureRequired` quando la firma elettronica è attiva. Le decisioni già registrate non cambiano.

## Ciclo di vita del foglio

| Evento | Effetto sugli atti di un foglio `ELECTRONIC` |
| --- | --- |
| Emissione | atto `LOAN_SHEET_ISSUE` in `AWAITING_DATA`; notifica all'Affidatario oppure link al genitore |
| Consegna successiva, sostituzione, rinnovo, restituzione registrata nel registro | un atto `LOAN_SHEET_VARIATION` per operazione, nella stessa transazione dell'operazione |
| Chiusura | atto `LOAN_SHEET_RETURN` |
| Riconsegna `SIGNED` o `SIGNED_ON_PAPER` con esito `COMPLIANT` | definizione immediata, al posto della conferma manuale della firma del verbale; resta la definizione automatica dopo 30 giorni |
| Definizione | dati personali rimossi dalle tabelle come oggi; gli atti firmati ricevono `retention_until` = definizione + anni di conservazione |
| Annullamento | atti in attesa `CANCELLED`, documenti congelati e link rimossi; atti firmati conservati con `retention_until` = annullamento + anni di conservazione |

`application.signature.retention-years` vale 10 per default. Per una presa visione non collegata a un foglio, `retention_until` parte dalla data in cui l'assegnazione diventa `RETURNED` o `CANCELLED`. `SignatureRetentionScheduler` elimina ogni notte documenti, attestazioni e dati personali delle prove scadute.

## Conservazione e GDPR

- Nei fogli `ELECTRONIC` Taurus conserva i dati che su carta restano sulla copia cartacea: codice fiscale, documento, residenza, telefono e dati del genitore, solo all'interno dei documenti firmati.
- La definizione del foglio e il flusso GDPR esistente (`InventoryErasureService`) rimuovono i dati personali dalle tabelle come oggi, ma non cancellano documenti firmati, attestazioni e prove prima di `retention_until`. La conservazione si fonda sull'art. 17, par. 3, lett. e) GDPR (accertamento, esercizio o difesa di un diritto in sede giudiziaria).
- Documenti firmati e attestazioni sono scaricabili da admin e super admin del tenant e dai firmatari con account; ogni download è registrato.
- L'informativa del modello deve dichiarare conservazione e durata.
- I log non contengono codici, token, e-mail, codici fiscali o contenuto dei documenti.

## Autorizzazioni e isolamento tenant

| Operazione | Firmatario | Admin | Super admin |
| --- | ---: | ---: | ---: |
| Vedere e firmare i propri atti | sì | sì | sì |
| Inserire i dati dell'Affidatario | solo `HOLDER` | no | no |
| Firmare per l'Associazione | solo il designato | sì, se designato | sì, se designato |
| Attivare la firma elettronica del tenant | no | sì | sì |
| Vedere atti, prove e attestazioni del tenant | propri | sì | sì |
| Reinviare o revocare un link | no | sì | sì |
| Dichiarare un atto firmato su carta | no | sì | sì |
| Verificare la catena delle prove | no | sì | sì |

- Gli endpoint personali sono in `/api/user/signatures/**` e vanno aggiunti esplicitamente all'elenco di `SecurityConfiguration`, che mantiene il `denyAll` sugli altri percorsi. Un atto di cui l'utente non è firmatario risponde `404`.
- Gli endpoint pubblici sono solo i percorsi `/api/signature-links/v1/*/…` elencati sotto, con `permitAll()` per metodo e `denyAll()` sul resto, prima della regola generale `/api/**`. L'autenticazione avviene solo tramite digest del token e non crea una `Authentication` riutilizzabile.
- Tutti gli endpoint sono soggetti a `@RequiresTenantFeature(TenantFeature.INVENTORY)`; per il percorso pubblico la feature disattivata è indistinguibile da un token inesistente.
- Ogni identificativo è verificato nel tenant del token JWT o del registro dei link; le tabelle stanno nello schema del tenant, tranne `signature_link_registry`.

## Modello dati e migrazioni

### Schema pubblico

Changelog in `master.xml`:

**`signature_link_registry`**: `token_digest` (PK, SHA-256 esadecimale), `tenant_id`, `link_id`, `expires_at`, `revoked_at`. Nessun dato personale. Creazione, rinnovo e revoca aggiornano registro e tabella tenant nella stessa transazione; la cancellazione del tenant elimina le righe prima del `DROP SCHEMA`.

### Schema del tenant

Changelog in `tenant-master.xml`, con file `changelog/yyyyMMddHHmmss_inventory_electronic_signature.xml`. Tutte le tabelle hanno i campi di audit `deleted`, `insert_date`, `insert_by`, `edit_date`, `edit_by`; quelle modificabili anche `entity_version`.

Modifiche a tabelle esistenti:

| Tabella | Colonna | Tipo | Note |
| --- | --- | --- | --- |
| `inventory_loan_settings` | `electronic_signature_enabled` | `BOOLEAN` | non nullo, predefinito `false` |
| `inventory_loan_sheet` | `signature_mode` | `VARCHAR(16)` | `PAPER` o `ELECTRONIC`, non nullo, predefinito `PAPER` |
| `inventory_loan_sheet` | `holder_email` | `VARCHAR(255)` | solo `GUARDIAN` elettronico; rimossa alla definizione |
| `inventory_assignment_decision` | `signature_evidence_id` | `BIGINT` | FK verso `signature_evidence`, nullo per le decisioni senza firma |

Nuove tabelle:

**`signature_act`**: `id`, `act_type`, `status`, `sheet_id`, `assignment_id`, `renewal_id`, `return_id`, `decision_id` (FK facoltative verso le entità di origine), `template_id`, `template_hash`, `attestation_media_asset_id`, `completed_at`, `paper_note`, `retention_until`, `cancelled_at`, audit, `entity_version`.

**`signature_document`**: `id`, `act_id`, `role` (`MAIN`, `CLAUSES`, `SNAPSHOT`), `frozen_media_asset_id`, `frozen_hash`, `signed_media_asset_id` (file con firma CieSign, se presente), `sealed_media_asset_id`, `sealed_hash`, audit. Vincolo univoco `(act_id, role)` sulle righe non cancellate.

**`signature_act_signer`**: `id`, `act_id`, `signer_role`, `required`, `user_keycloak_id` (nullo per il genitore), `display_name`, `email` (solo genitore), `status` (`PENDING`, `SIGNED`, `SIGNED_ON_PAPER`), `signed_at`, audit, `entity_version`.

**`signature_challenge`**: `id`, `purpose`, `method` (`EMAIL_OTP`, `TOTP`), `user_keycloak_id` oppure `link_id`, `destination_email`, `code_digest`, `items_json`, `items_hash`, `expires_at`, `attempts`, `sends`, `last_sent_at`, `status` (`OPEN`, `CONFIRMED`, `EXPIRED`, `LOCKED`, `CANCELLED`), `confirmed_at`, audit, `entity_version`. Le conferme chiuse da più di 30 giorni sono eliminate.

**`signature_evidence`**: colonne descritte in [Prove e catena](#prove-e-catena), `sequence` univoco, `previous_hash`, `evidence_hash`, `purged_at`, audit. Nessun aggiornamento applicativo, salvo la cancellazione dei dati personali alla scadenza.

**`signature_chain_head`**: riga unica con `last_sequence`, `last_hash`, `entity_version`.

**`signature_link`**: `id`, `signer_id`, `token_fingerprint`, `expires_at`, `revoked_at`, `last_used_at`, audit.

`MediaRepository.isUnreferenced` aggiunge `signature_document` (tre colonne media) e `signature_act.attestation_media_asset_id` ai riferimenti, così la pulizia non rimuove i file degli atti.

## Keycloak

- Nuovo realm resource provider `taurus-otp` in `keycloak-authenticator`, registrato in `META-INF/services`, con le operazioni descritte in [App di autenticazione](#app-di-autenticazione).
- Nuovo client confidenziale `taurus-signature` in `taurus-realm.json`: solo service account, nessun flusso browser, ruolo client `otp-verifier` assegnato al service account. Il segreto di sviluppo segue la convenzione del realm locale; in produzione arriva da variabile d'ambiente e non va nel repository.
- Il flusso "Browser with Tenant and Role" resta invariato: l'OTP condizionale al login è accettato.
- `smtpServer` del realm può usare la stessa casella dell'associazione per recupero password e verifica e-mail; la configurazione è documentata nel runbook, senza credenziali.
- La versione di Keycloak usata per compilare il provider va allineata a quella dell'immagine eseguita.

## API REST

### Firmatario autenticato (`/api/user/signatures`)

| Metodo e percorso | Descrizione |
| --- | --- |
| `GET /pending` | atti in attesa della firma dell'utente, con tipo, foglio, ruolo e stato |
| `GET /acts/{id}` | dettaglio: documenti con hash, firmatari, stato, testo della dichiarazione, campi dati richiesti |
| `PUT /acts/{id}/holder-data` | dati dell'Affidatario; genera i documenti congelati e restituisce gli hash |
| `GET /acts/{id}/documents/{role}` | documento congelato o, ad atto completato, sigillato |
| `GET /acts/{id}/attestation` | attestazione, ad atto completato |
| `POST /acts/{id}/documents/{role}/ciesign` | caricamento multipart del documento firmato con CieSign |
| `GET /methods` | metodo preferito, stato dell'app, e-mail mascherata |
| `POST /challenges` | crea una conferma: `{ purpose, method, items }`; per e-mail invia il codice |
| `POST /challenges/{id}/resend` | nuovo codice via e-mail |
| `POST /challenges/{id}/confirm` | `{ code }`; registra le firme e restituisce l'esito per atto |

Gli elementi di una conferma `ACCEPTANCE` possono riguardare più atti di variazione dello stesso firmatario. Emissione e riconsegna si confermano da sole, e `ACCEPTANCE` e `CLAUSE_APPROVAL` sono sempre conferme distinte.

### Amministrazione (`/api/inventory`)

| Metodo e percorso | Descrizione |
| --- | --- |
| `PUT /loan-template/signature` | `{ enabled, entityVersion }`; attiva o disattiva la firma elettronica |
| `GET /loan-template` | risposta esistente, con requisiti dell'installazione e firmabilità del modello in uso |
| `GET /loan-sheets/{id}/signature-acts` | atti del foglio con firmatari e stato |
| `GET /signature-acts/{id}/evidence` | prove dell'atto |
| `GET /signature-acts/{id}/documents/{role}`, `GET /signature-acts/{id}/attestation` | download per l'amministrazione |
| `POST /signature-acts/{id}/links/{signerId}/resend` | nuovo link al genitore |
| `DELETE /signature-acts/{id}/links/{signerId}` | revoca del link |
| `POST /signature-acts/{id}/paper-declaration` | `{ note }`; crea la conferma `PAPER_DECLARATION` dell'amministratore, da confermare con `/api/user/signatures/challenges/{id}/confirm` |
| `POST /signatures/verify-chain` | verifica della catena del tenant |

La richiesta di emissione esistente (`POST /api/inventory/users/{userIndex}/loan-sheets`) acquisisce `signatureMode`, `associationSignerId` e, per `GUARDIAN` elettronico, `holderEmail`.

### Link pubblici (`/api/signature-links/v1/{token}`)

| Metodo e percorso | Descrizione |
| --- | --- |
| `GET /` | sintesi dell'atto: tenant, tipo, numero del foglio, stato, campi dati richiesti |
| `PUT /holder-data` | dati dell'Affidatario |
| `GET /documents/{role}` | documento congelato o sigillato |
| `POST /documents/{role}/ciesign` | caricamento CieSign |
| `POST /challenges` | conferma con codice via e-mail all'indirizzo del link |
| `POST /challenges/{id}/confirm` | conferma del codice |
| `GET /attestation` | attestazione, finché il link è valido |

Un token inesistente, scaduto o revocato, un tenant non attivo o una feature disattivata rispondono `404` senza corpo applicativo. Le risposte hanno `Cache-Control: no-store` e `Referrer-Policy: no-referrer`. Un limitatore basato su Caffeine, come `CalendarFeedRateLimiter`, limita le richieste per token e per IP.

### Errori

Gli errori seguono lo [Standard degli errori applicativi](application-errors-standard.md): chiave nel campo `message`, dati del caso in `args`, nessun testo.

| Chiave | HTTP | Caso |
| --- | --- | --- |
| `signature.act.notAwaitingData` | 409 | dati inviati per un atto che non li attende o già firmato |
| `signature.act.notAwaitingSignatures` | 409 | atto già completato, annullato o in attesa di dati |
| `signature.act.documentChanged` | 409 | hash inviato diverso dal documento congelato |
| `signature.act.alreadySigned` | 409 | il firmatario ha già firmato |
| `signature.act.batchNotAllowed` | 422 | conferma multipla non ammessa per il tipo di atto o lo scopo |
| `signature.holderData.invalid` | 422 | dati dell'Affidatario mancanti o non validi, campi in `args.fields` |
| `signature.challenge.expired` | 410 | codice scaduto |
| `signature.challenge.invalidCode` | 422 | codice errato, con `args.remainingAttempts` |
| `signature.challenge.locked` | 409 | tentativi esauriti |
| `signature.challenge.resendTooSoon` | 429 | nuovo invio prima del tempo, con `args.retryAfterSeconds` |
| `signature.method.totpNotConfigured` | 422 | app richiesta ma non configurata |
| `signature.method.unavailable` | 503 | Keycloak non raggiungibile |
| `signature.mail.failed` | 503 | invio dell'e-mail non riuscito |
| `signature.cieSign.documentMismatch` | 422 | il file non contiene il documento congelato o è stato modificato dopo la firma |
| `signature.cieSign.invalidSignature` | 422 | firma assente, multipla o non valida |
| `signature.cieSign.untrustedCertificate` | 422 | certificato non riconducibile alla CIE o scaduto |
| `signature.cieSign.signerMismatch` | 422 | nome o codice fiscale diversi dal firmatario atteso |
| `signature.cieSign.tooLarge` | 413 | file oltre 25 MB |
| `signature.settings.requirementsMissing` | 422 | attivazione senza SMTP o sigillo configurati |
| `signature.settings.versionConflict` | 409 | impostazione modificata da un'altra richiesta |
| `inventory.loanSheet.templateNotSignable` | 422 | emissione elettronica con modello senza marcatori validi |
| `inventory.loanSheet.holderEmailRequired` | 422 | genitore senza e-mail in modalità elettronica |
| `inventory.loanSheet.signatureDisabled` | 422 | emissione elettronica con firma non attiva nel tenant |
| `inventory.decision.signatureRequired` | 409 | decisione diretta con firma elettronica attiva |

Un atto o una conferma non accessibili all'utente rispondono `404`, come le risorse di altri soci.

## Interfaccia utente

La vista segue il [Taurus Layout Standard](taurus-layout-standard.md) e la documentazione PrimeNG in `docs/llms-full.md`, in particolare Dialog, Stepper, InputOtp e FileUpload.

- **Profilo**: sezione **Firma elettronica** con il metodo preferito (E-mail o App di autenticazione), lo stato dell'app e il pulsante **Configura app**, che avvia l'azione `CONFIGURE_TOTP` già presente. Un testo avvisa che l'app sarà chiesta anche al login.
- **I miei oggetti**: riquadro **Firme in attesa**, con atti del foglio e prese visione da confermare. Una notifica interna segnala ogni nuovo atto.
- **Finestra di firma**, con passi:
  1. **Dati**, solo all'emissione: campi delle sezioni 1 e 2, con codice fiscale validato nel formato e nel carattere di controllo; tutti obbligatori tranne il telefono.
  2. **Documento**: anteprima e download, hash abbreviato, casella **Ho letto il documento** non preselezionata.
  3. **Accettazione**: dichiarazione e codice.
  4. **Clausole**, solo all'emissione: testo delle clausole, casella **Approvo specificamente le clausole elencate** non preselezionata e un nuovo codice.
  5. **Esito**, con download dei documenti firmati quando l'atto è completo.

  Nei passi 3 e 4 sono disponibili **Usa l'altro metodo** e **Firma con CieSign** (scarica, firma, carica).
- **Presa visione**: le caselle di accettazione e rifiuto restano; **Conferma** apre la richiesta del codice e **Conferma selezionate** gestisce più assegnazioni insieme.
- **Modello del foglio**: sezione **Firma elettronica** con interruttore, requisiti dell'installazione (SMTP, sigillo), firmabilità del modello in uso con le segnalazioni dei marcatori e avviso sull'informativa.
- **Emissione del foglio**: modalità di firma, firmatario per l'Associazione e, per `GUARDIAN` elettronico, e-mail del genitore.
- **Dettaglio del foglio**: scheda **Firme** con atti, stato per firmatario e azioni **Firma per l'Associazione**, **Reinvia link**, **Revoca link**, **Firmato su carta**, **Scarica atto firmato**, **Scarica attestazione** e **Prove**.
- **Pagina pubblica `/firma/:token`**: layout senza menu e senza login, con gli stessi passi della finestra di firma, codice via e-mail o CieSign. `app.config.ts` non avvia il login Keycloak quando il percorso iniziale è `/firma/`; la pagina usa solo gli endpoint pubblici e non invia token.

## Notifiche

Con `NotificationOutboxPublisher.enqueue`, nella transazione dell'operazione:

- all'Affidatario con account e al socio minorenne: nuovo atto da firmare, con percorso verso **Firme in attesa**;
- all'amministratore designato: atto in attesa della firma per l'Associazione;
- ad admin e super admin: atto completato, atto dichiarato su carta, link scaduto senza firma.

Il genitore riceve solo e-mail: link, codici e conferma di completamento con il collegamento alla pagina pubblica, finché il link è valido.

## Osservabilità e sicurezza

- Metriche: `taurus.signature.challenges{method,outcome}`, `taurus.signature.acts{type,outcome}`, `taurus.signature.ciesign{outcome}`, `taurus.signature.mail{outcome}`, senza identificativi di tenant, utente o atto.
- Log con identificativo di atto e tenant ed esito; mai codici, token, e-mail, codici fiscali o contenuto dei documenti.
- Allarmi: invii e-mail falliti consecutivi, verifica della catena non coerente, errori della verifica OTP di Keycloak.
- Codici e token confrontati a tempo costante; segreti (`otp-secret`, password del keystore, segreto del client Keycloak, credenziali SMTP) solo da variabili d'ambiente.
- Il file CieSign caricato è analizzato con i limiti di dimensione e senza eseguirne il contenuto; i caricamenti rifiutati non vengono salvati.

## Strategia di test

### Backend unitari

- Conferme: generazione, HMAC, scadenza, tentativi, uso singolo, limiti di invio, legame con firmatario e documenti.
- Catena delle prove: hash canonico, continuità, concorrenza con lock, verifica dopo la cancellazione dei dati personali.
- Validatore: ogni nuovo codice di segnalazione; firmabilità del predefinito.
- Rendering per atto: filtro dei marcatori, registro con le sole righe della variazione, estrazione del blocco clausole.
- Dati dell'Affidatario: obbligatorietà e codice fiscale.
- Sigillo: firma PAdES valida con PDFBox; firma CieSign ancora valida dopo il sigillo.
- Verifica CieSign con una CA di test configurata come trust anchor: file valido, prefisso diverso, modifica dopo la firma, firma doppia, certificato non attendibile, nome o codice fiscale diversi, CRL irraggiungibile.

### Backend integrazione

- Ciclo completo di emissione, variazioni, riconsegna e definizione immediata.
- Presa visione con firma attiva e disattiva, anche multipla, e conflitto di revisione.
- Isolamento tenant di atti, conferme e link; registro pubblico e tenant non attivi.
- Elenco esplicito di `SecurityConfiguration`: percorsi nuovi ammessi, altri negati.
- Conservazione: definizione, annullamento, cancellazione GDPR e scheduler; media non rimossi dalla pulizia finché referenziati.
- Migrazioni Liquibase su schema pubblico e tenant.

### Keycloak

- Unitari del provider `taurus-otp`: chiamante senza ruolo, codice valido, errato, riutilizzato, utente senza OTP.
- Integrazione con Testcontainers e il realm di sviluppo, se il costo di esecuzione in CI è accettabile.

### Frontend

- Finestra di firma: passi, caselle non preselezionate, InputOtp, cambio metodo, errori per chiave.
- Preferenza nel profilo e stato dell'app.
- Pagina pubblica senza inizializzazione del login.
- Presa visione multipla.

### Verifica manuale

- Firma reale con CieSign su Android e iOS e con il Software CIE su PC, con una CIE reale.
- Verifica in Acrobat di firma CieSign e sigillo.
- Invio delle e-mail con il server SMTP di una casella reale dell'associazione.

## Criteri di accettazione

- [ ] Con la firma elettronica disattivata il comportamento di fogli e prese visione è invariato.
- [ ] Un socio maggiorenne firma l'emissione con codice via e-mail o con l'app, con due conferme distinte per foglio e clausole.
- [ ] Un genitore senza account firma dal link con codice via e-mail o con CieSign.
- [ ] Ogni variazione e la riconsegna producono un atto firmabile; la riconsegna firmata senza addebiti definisce il foglio.
- [ ] Un documento firmato non cambia: download ripetuti restituiscono lo stesso hash e il sigillo resta valido.
- [ ] Un modello caricato senza marcatori resta utilizzabile su carta e la pagina del modello spiega cosa manca.
- [ ] Un file CieSign con documento diverso, firma non valida o firmatario diverso è rifiutato con la chiave corretta.
- [ ] Un codice scaduto, già usato, errato oltre i tentativi o legato a un altro atto è rifiutato.
- [ ] La catena delle prove è verificabile e una modifica manuale di una prova viene rilevata.
- [ ] Definizione e cancellazione GDPR non rimuovono documenti firmati e prove prima della scadenza di conservazione.
- [ ] Atti, conferme e link di un tenant non sono accessibili da un altro tenant.
- [ ] Nessun codice, token, e-mail o codice fiscale compare nei log.

## Piano di implementazione

1. **Fondamenta**: tabelle, catena delle prove, conferme con codice via e-mail, `SignatureMailService`, sigillo e attestazione, presa visione con codice, interruttore del tenant.
2. **Foglio**: marcatori e validatore, nuovo modello predefinito, atti di emissione, variazione e riconsegna, dati dell'Affidatario, firma per l'Associazione, firma su carta del singolo atto, conservazione.
3. **App di autenticazione**: provider `taurus-otp`, client `taurus-signature`, preferenza nel profilo.
4. **Genitore**: link pubblici, registro, pagina `/firma/:token`.
5. **CieSign**: verifica dei file, aggiornamento di BouncyCastle, trust anchor CIE.
6. **Qualificazione**: runbook operativo e di test, verifiche manuali, verifica legale delle [Questioni aperte](#questioni-aperte).

## Compatibilità e adozione

- I fogli esistenti hanno `signature_mode = PAPER` e non cambiano.
- La firma elettronica è disattivata per default in ogni tenant.
- Le decisioni di presa visione già registrate restano valide come prese visione autenticate, senza prova di firma.
- I modelli caricati senza marcatori restano utilizzabili per i fogli su carta.
- Rollback: disattivare la firma elettronica del tenant. Gli atti in attesa restano completabili; tabelle e documenti non vengono rimossi.

Quando l'implementazione inizia, vanno aggiornati anche:

- [Foglio di consegna e affidamento in comodato](inventory-loan-sheet-spec.md): fuori scope, conservazione e GDPR, modello predefinito, emissione, definizione e API;
- [Gestione inventario tenant](inventory-management-spec.md): la frase secondo cui la presa visione non rappresenta una firma elettronica;
- il catalogo dello [Standard degli errori applicativi](application-errors-standard.md), con le nuove chiavi;
- [Runbook operativo](runbook-operativo.md): SMTP, keystore del sigillo, client Keycloak, trust anchor CIE, variabili d'ambiente;
- [Runbook di test](runbook-test.md): verifiche manuali di CieSign, e-mail e sigillo.

## Questioni aperte

1. **Verifica legale**: idoneità della firma semplice con codice per le clausole vessatorie, testo delle dichiarazioni, durata di conservazione di 10 anni e base giuridica dell'art. 17, par. 3, lett. e) GDPR, testo dell'informativa.
2. **Firma del socio minorenne**: proposta non bloccante; da confermare.
3. **Revoca del certificato CIE non verificabile**: proposta non bloccante, con esito registrato; da confermare.
4. **Validità del link del genitore**: proposta di 7 giorni.
5. **PDF/A**: il modello predefinito usa Helvetica non incorporato, incompatibile con PDF/A. Da valutare se serva un formato di archiviazione a lungo termine.
6. **Versione di Keycloak**: allineare provider (26.6.0) e immagine (26.5.2) prima di aggiungere il nuovo provider.
