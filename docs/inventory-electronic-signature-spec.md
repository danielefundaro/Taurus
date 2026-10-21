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
- Consegnare al tenant un fascicolo autoverificabile degli atti firmati, da conservare a sua cura.
- Mantenere la firma su carta come alternativa, per tenant, per foglio e per singolo atto.

## Fuori scope

- Provider di firma esterni, a pagamento o gratuiti, e invio di codici via SMS.
- Firma elettronica avanzata erogata dal tenant ai sensi del DPCM 22 febbraio 2013: identificazione certa, dichiarazione di accettazione conservata per 20 anni e copertura assicurativa sono oneri sproporzionati per un'associazione.
- Firma elettronica qualificata o digitale erogata da Taurus e firma con SPID.
- Firma grafometrica o immagine della firma autografa.
- Marca temporale qualificata e conservazione a norma presso un conservatore accreditato.
- Conservazione degli atti firmati per i termini di legge: è responsabilità del tenant, che scarica il fascicolo del foglio. Taurus elimina documenti e prove al termine del ciclo di vita del foglio.
- Formato PDF/A: richiederebbe di incorporare i font, mentre il modello predefinito usa i font standard PDF non incorporati.
- Promemoria automatici per le firme in attesa oltre alla notifica iniziale.
- Firma di documenti diversi da foglio e presa visione: il meccanismo è riusabile, ma ogni nuovo uso richiede una specifica propria.

## Quadro giuridico

Questa sezione riassume i presupposti del progetto e non sostituisce un parere legale; i punti da verificare prima dell'uso in un tenant reale sono riportati in [Verifica legale](#verifica-legale).

| Tema | Riferimento | Conseguenza per il progetto |
| --- | --- | --- |
| Firma elettronica semplice | art. 3, n. 10, e art. 25 Reg. UE 910/2014; art. 20, c. 1-bis, CAD | la firma con codice è una prova valida, liberamente valutabile in giudizio |
| Firma elettronica avanzata | art. 20, c. 1-bis, CAD | la firma CieSign soddisfa la forma scritta con l'efficacia dell'art. 2702 c.c. |
| Comodato | art. 1803 c.c. | contratto reale a forma libera: la firma serve come prova, non come requisito di validità |
| Clausole vessatorie | artt. 1341–1342 c.c. | richiedono un'approvazione specifica per iscritto, distinta dall'accettazione del contratto |

La giurisprudenza ritiene insufficiente la sola spunta di una casella e ammette una firma elettronica semplice con codice OTP, purché l'approvazione sia specifica e distinta dall'accettazione generale: Cass. civ., sez. III, ord. 20 giugno 2026, n. 20945, letta sul testo originale, richiede per un contratto telematico tra professionisti un passaggio dedicato in cui firmare specificamente le clausole, di regola con un codice OTP inviato via SMS o e-mail, e richiama Cass. n. 9413/2021 per la nozione di firma elettronica e Cass. n. 12708/2014 per la specificità dell'approvazione. Per questo l'approvazione delle clausole è un documento separato con una propria conferma.

## Decisioni principali

1. **Firma semplice con codice monouso.** Ogni firma richiede un codice valido per quella sola conferma. È il metodo predefinito per soci maggiorenni, socio minorenne e amministratori.
2. **Due metodi di conferma.** Codice via e-mail, generato da Taurus, oppure codice dell'app di autenticazione, verificato da Keycloak. L'utente sceglie il metodo preferito e può sempre usare l'altro.
3. **App di autenticazione gestita da Keycloak.** Taurus non conserva segreti TOTP. La credenziale è la stessa del login: chi la configura riceve la richiesta del codice anche a ogni accesso, come già previsto dal flusso del realm.
4. **CieSign facoltativo per l'Affidatario.** Il socio maggiorenne o il genitore può firmare i documenti PDF con CieSign e caricarli in Taurus, che verifica firma, certificato e integrità.
5. **Genitore o tutore senza account.** Riceve un link monouso via e-mail e firma con codice via e-mail o con CieSign. La firma su carta resta disponibile.
6. **Documenti congelati.** Ogni documento da firmare è generato una sola volta, salvato e identificato dal suo hash SHA-256. Un documento firmato non viene mai rigenerato.
7. **Atti separati.** Emissione, ogni variazione del registro e verbale di riconsegna sono atti distinti, ciascuno con i propri documenti e firmatari. La presa visione di una revisione è un atto senza PDF, senza sigillo e senza attestazione.
8. **Approvazione delle clausole come documento a sé.** All'emissione l'Affidatario firma due documenti: il foglio e l'approvazione specifica delle clausole, generata dal blocco che il modello dichiara.
9. **Indipendenza dal modello.** Il modello indica con attributi `data-taurus-act` e `data-taurus-sign` le parti di ogni atto e il blocco delle clausole. Un modello senza questi marcatori resta utilizzabile su carta.
10. **Prove immutabili in catena.** Ogni firma produce una prova immutabile, collegata alla precedente del tenant tramite hash.
11. **Sigillo e attestazione.** A firme complete Taurus appone a ogni documento una firma PAdES con il certificato dell'installazione e genera un'attestazione separata e sigillata con le prove. L'attestazione basta da sola a verificare l'atto, anche quando Taurus non ne conserva più i dati.
12. **Attivazione per tenant, modalità per foglio.** La firma elettronica si abilita nelle impostazioni del foglio di affidamento ed è disattivata per default. Il foglio sceglie all'emissione la modalità `ELECTRONIC` o `PAPER`; un amministratore può chiudere su carta un singolo atto.
13. **Conservazione a cura del tenant.** Taurus conserva documenti firmati, attestazioni e dati personali delle prove solo durante il ciclo di vita del foglio. Dopo la definizione o l'annullamento restano scaricabili nel fascicolo del foglio per un periodo di grazia di 30 giorni, poi sono eliminati. La cancellazione GDPR li elimina subito. La conservazione per i termini di legge spetta al tenant.
14. **Firmatario per l'Associazione distinto dall'Affidatario.** Chi firma per l'Associazione non può essere l'Affidatario dello stesso foglio. Finché nessuno ha firmato l'atto, un amministratore può designare un altro firmatario per l'Associazione.
15. **Nessun costo.** L'unico requisito esterno è un server SMTP, per esempio quello della casella e-mail già usata dall'associazione.

## Contesto tecnico acquisito

- `InventoryLoanSheetService.generate()` rigenera il PDF a ogni download e salva ogni copia in `inventory_loan_sheet_export`; annullamento e definizione chiamano `deletePdfs()`, la definizione anche `purgePersonalData()`.
- `LoanTemplateValidator` controlla solo `style` e un elenco chiuso di attributi di risorsa: gli attributi `data-*` sono già ammessi. Le segnalazioni sono `Issue(code, args, line, blocking)` e il frontend le traduce come `inventory.loanTemplate.issue.<code>`.
- Il catalogo `LoanSheetVariables` descrive ogni variabile con nome, tipo (`TEXT`, `FLAG`, `LIST`), descrizione ed esempio.
- La presa visione (`InventoryService.decide`) confronta l'hash della revisione con `MessageDigest.isEqual` e salva in `inventory_assignment_decision` l'hash SHA-256 di `snapshotHash|userId|DECISION|reason`. Non esiste un endpoint di decisione multipla.
- `spring-boot-starter-mail` è nel `pom.xml`, ma nessuna classe invia e-mail e nessun profilo configura `spring.mail`. `jhipster.mail.from` vale `taurus@localhost` e l'health check della posta è disattivato. L'ambiente di sviluppo non ha un server SMTP.
- Le preferenze utente sono coppie chiave/valore della tabella `preferences`, esposte da `/api/preferences`.
- Il profilo avvia già l'azione Keycloak `CONFIGURE_TOTP` (`profile.component.ts`). Il flusso "Browser with Tenant and Role" chiede l'OTP dopo la selezione di tenant e ruolo solo agli utenti che l'hanno configurato. La policy OTP del realm è TOTP a 6 cifre, periodo 30 secondi, look-ahead 1, codice non riutilizzabile.
- Nessun componente legge i claim `acr`, `amr` o `auth_time`.
- Il frontend inizializza Keycloak con `onLoad: 'login-required'` in `taurus-fe/src/app.config.ts`: oggi nessuna rotta Angular è raggiungibile senza login.
- L'annullamento di un foglio (`InventoryLoanSheetService.cancel`) rimuove i PDF ma non i dati personali. La definizione automatica dei fogli `COMPLIANT` avviene 30 giorni dopo la chiusura, con `InventoryLoanSheetRetentionScheduler`. La cancellazione GDPR (`InventoryErasureService.complete`) è completata da un amministratore.
- Il feed calendario risolve il tenant di una richiesta anonima con il registro `public.calendar_feed_token_registry` e limita le richieste con `CalendarFeedRateLimiter`, basato su Caffeine.
- `MediaRepository.isUnreferenced` elenca le tabelle che usano un media; la pulizia rimuove solo i file dei media non più attivi.
- Le notifiche interne si creano con `NotificationOutboxPublisher.enqueue(NotificationCommand)` dentro una transazione.
- BouncyCastle è presente solo come `bcprov-jdk15on` 1.70, usato dalla libreria Web Push. Manca `bcpkix`, necessario per generare e verificare firme CMS.
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
| Fascicolo | archivio ZIP di un foglio con documenti sigillati e attestazioni degli atti firmati, che il tenant scarica e conserva |
| Periodo di grazia | giorni dopo la definizione o l'annullamento del foglio in cui il fascicolo resta scaricabile prima dell'eliminazione |

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

### Firmatario per l'Associazione

- Non può essere l'Affidatario del foglio. All'emissione l'elenco esclude il socio quando il ruolo è `MEMBER` e la richiesta con il socio come firmatario risponde `422 signature.associationSigner.sameAsHolder`. Se l'amministratore che registra una variazione o chiude il foglio ne è l'Affidatario, firma per l'Associazione il firmatario designato all'emissione.
- Firma solo se, al momento della conferma, è ancora admin o super admin attivo del tenant. Altrimenti la conferma risponde `422 signature.associationSigner.notAdmin` e la scheda **Firme** del foglio segnala che serve un nuovo firmatario.
- Finché nessun firmatario dell'atto ha firmato, un amministratore può designare un altro firmatario per l'Associazione, con le stesse regole e con il controllo di `entity_version` dell'atto (`409 signature.act.versionConflict`). Il nuovo firmatario riceve la notifica. Il nome è stampato solo nell'emissione: se i suoi documenti sono già congelati, Taurus li marca come eliminati e l'atto torna in `AWAITING_DATA`, perché i dati dell'Affidatario esistono solo dentro i documenti; l'Affidatario riceve la notifica e li inserisce di nuovo. Variazioni e verbale di riconsegna non stampano il nome e non cambiano.
- Dopo la prima firma il firmatario non cambia più (`409 signature.associationSigner.locked`): se non può firmare, un amministratore dichiara l'atto firmato su carta.

### Stati dell'atto

| Stato | Significato |
| --- | --- |
| `AWAITING_DATA` | solo emissione: in attesa dei dati che l'Affidatario inserisce al posto della compilazione a mano |
| `AWAITING_SIGNATURES` | documenti congelati, mancano firme obbligatorie |
| `SIGNED` | firme obbligatorie raccolte, sigillo e attestazione generati |
| `SIGNED_ON_PAPER` | un amministratore ha dichiarato l'atto firmato su carta |
| `CANCELLED` | atto annullato con il foglio |

Variazioni e riconsegna nascono in `AWAITING_SIGNATURES`. La presa visione nasce direttamente `SIGNED` alla conferma del codice.

Lo stato non cambia quando Taurus elimina documenti e prove al termine del periodo di grazia: l'atto riceve `purged_at` e i download rispondono `410 signature.act.purged`.

Le firme possono arrivare in qualsiasi ordine, con un vincolo per l'Affidatario: firma gli atti del foglio (accettazione e approvazione delle clausole, con il codice o con CieSign) solo quando il socio ha preso visione di tutte le assegnazioni del foglio non ancora riconsegnate, cioè quando nessuna ha la revisione corrente senza decisione. Altrimenti la firma, e prima ancora l'invio dei dati dell'emissione, risponde `409 signature.act.acknowledgmentsPending` con il numero delle prese visione in attesa (`args.count`); la vista dell'atto riporta lo stesso numero in `pendingAcknowledgments`. Una presa visione rifiutata non blocca la firma. Il vincolo non vale per la firma per l'Associazione, la presa visione del minore e la dichiarazione di firma su carta.

Finché nessuno ha firmato, l'Affidatario può correggere i propri dati: Taurus genera nuovi documenti congelati e marca come eliminati i precedenti. Dopo la prima firma i documenti non cambiano più; un errore si corregge annullando il foglio ed emettendone uno nuovo, come per i fogli su carta.

## Modalità di firma

### Attivazione per tenant

La firma è la funzionalità per tenant `INVENTORY_SIGNATURE`, figlia dei Fogli di affidamento (`INVENTORY_LOAN_SHEET`), che il super amministratore concede dalla pagina del tenant (vedi [Funzionalità configurabili per tenant](tenant-feature-flags-spec.md#fogli-di-affidamento-e-firma-elettronica)). Senza la funzionalità l'interruttore seguente resta salvato ma non ha effetto, la sezione della firma non compare nel modello e gli endpoint della firma rispondono `403 tenantFeature.inventorySignature.disabled`.

Nuovo campo `electronic_signature_enabled` di `inventory_loan_settings`, falso per default, modificabile da admin e super admin. L'attivazione è rifiutata con `signature.settings.requirementsMissing` se l'installazione non ha server SMTP, chiave dei codici (`application.signature.otp-secret`) e certificato del sigillo configurati; `args.missing` elenca i requisiti mancanti (`mail`, `codes`, `seal`).

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

Un amministratore può dichiarare firmato su carta un atto in `AWAITING_DATA` o `AWAITING_SIGNATURES`, indicando una nota obbligatoria e confermando con il proprio codice. L'atto passa a `SIGNED_ON_PAPER`, i firmatari in attesa a `SIGNED_ON_PAPER`, la prova registra scopo e metodo `PAPER_DECLARATION` e la copia cartacea resta in custodia al tenant. La prova non ha un firmatario dell'atto, perché chi dichiara può non esserlo, e non ha un documento quando l'atto attendeva ancora i dati; i documenti preparati per la firma sono eliminati. Serve quando una parte non può firmare elettronicamente o per problemi tecnici.

## Metodi di conferma

### Codice via e-mail

- Sei cifre generate con `SecureRandom`. Nel database si salva solo l'HMAC-SHA256 del codice con la chiave `application.signature.otp-secret`; il confronto avviene a tempo costante.
- Validità 10 minuti, al massimo 5 tentativi, uso singolo. Un nuovo invio è possibile dopo 60 secondi, fino a 5 invii per conferma; ogni invio sostituisce il codice precedente.
- Il codice è legato al firmatario, allo scopo e all'hash dell'elenco dei documenti confermati: non vale per altri atti.
- Il messaggio indica tenant, tipo di atto, numero del foglio, prime 8 cifre dell'hash del documento e scadenza del codice. Non contiene link.
- L'invio usa `spring.mail.*` e il mittente `jhipster.mail.from`, tramite un nuovo `SignatureMailService` basato su `JavaMailSender`. L'e-mail parte dopo il commit; se il server SMTP non la accetta la conferma è annullata e la richiesta risponde `502 signature.mail.sendFailed`. Un firmatario senza e-mail riceve `signature.mail.addressMissing`.
- Ogni utente può aprire al massimo 20 conferme in un'ora (`application.signature.max-challenges-per-hour`), per proteggere la casella dell'associazione dai limiti di invio del provider; oltre il limite la richiesta risponde `429 signature.challenge.resendTooSoon` con il tempo d'attesa.
- Le conferme chiuse da più di 30 giorni (`application.signature.challenge-retention-days`) sono eliminate ogni notte da `SignatureChallengeCleanupScheduler`; le prove non dipendono dalle conferme.
- La configurazione SMTP è unica per l'installazione: tutti i tenant inviano dallo stesso mittente e ogni messaggio riporta la denominazione del tenant. Un SMTP diverso per tenant è fuori scope.
- Nello sviluppo `services.yml` avvia Mailpit, un server SMTP locale e open source che raccoglie i messaggi senza consegnarli e li mostra in una pagina web; il profilo `dev` vi punta `spring.mail`.
- Destinatario: l'e-mail dell'account Keycloak del firmatario oppure, per il genitore, l'e-mail registrata sul foglio.

### App di autenticazione

- Usa la credenziale OTP di Keycloak, configurata dal profilo con l'azione `CONFIGURE_TOTP` già presente e rimovibile dalla console account di Keycloak.
- Il codice è verificato da un'estensione del modulo `keycloak-authenticator`, un realm resource provider `taurus-otp`:
  - `GET /realms/{realm}/taurus-otp/users/{userId}/status` restituisce `{ "configured": true|false }`;
  - `POST /realms/{realm}/taurus-otp/users/{userId}/verify` con `{ "code": "123456" }` restituisce `204` se valido, `422` altrimenti;
  - un utente inesistente, disabilitato o service account riceve `404`; le risposte di errore hanno un corpo JSON `{ "error": ... }`, perché Keycloak rifiuta gli errori senza tipo di contenuto;
  - accetta solo il bearer token del client confidenziale `taurus-signature`, con service account e ruolo client `otp-verifier`; ogni altro chiamante, anche senza token, riceve `403`;
  - verifica il codice su ogni credenziale OTP dell'utente con `user.credentialManager().isValid(...)` e l'identificativo della credenziale, che il provider OTP di Keycloak richiede; la policy del realm vale anche qui e con `otpPolicyCodeReusable: false` lo stesso codice è rifiutato al secondo uso, anche se già usato per il login;
  - non registra il codice nei log.
- Il backend (`KeycloakOtpClient`) ottiene il token con client credentials (`application.signature.keycloak.client-id`, segreto `SIGNATURE_KEYCLOAK_CLIENT_SECRET`, realm `application.signature.keycloak.realm-uri`, per default l'issuer OIDC), lo riusa fino a 30 secondi prima della scadenza e ne chiede uno nuovo una sola volta se il provider risponde `403`. Passa sempre il `sub` del firmatario autenticato, mai un valore ricevuto dal client. Ogni chiamata ha un timeout di 5 secondi (`application.signature.keycloak.timeout`).
- Senza segreto del client l'installazione non offre l'app e la richiesta di una conferma con l'app risponde `422 signature.method.totpNotConfigured`, come per un utente che non l'ha configurata. L'app non è un requisito per attivare la firma elettronica.
- La conferma con l'app non invia e-mail e non ammette nuovi invii (`400 signature.challenge.invalid`). I tentativi sono contati nella conferma Taurus, con gli stessi limiti del codice via e-mail, e il limite orario delle conferme vale per entrambi i metodi.
- Se Keycloak non risponde, la richiesta o la conferma falliscono con `502 signature.method.unavailable` senza consumare tentativi; la conferma resta aperta e l'utente può riprovare o passare al codice via e-mail. Il log registra `signature_totp_unavailable`.

### Preferenza dell'utente

- Chiave `signature.method` nella tabella `preferences`, con valori `EMAIL` (predefinito) e `TOTP`, salvata con `PUT /api/user/signatures/methods` e `{ "preference": "EMAIL_OTP" | "TOTP" }`. L'app si può preferire prima di configurarla.
- `GET /api/user/signatures/methods` restituisce firma attiva nel tenant (`enabled`), metodo preferito (`preference`), metodo proposto dalla finestra di firma (`proposed`), app verificabile in questo momento (`totpAvailable`), app configurata (`totpConfigured`) ed e-mail mascherata. Keycloak è interrogato solo se il tenant usa la firma elettronica, perché le pagine dell'inventario leggono i metodi per saperlo.
- Se la preferenza è `TOTP` ma l'app non è configurata o non è verificabile, Taurus propone il codice via e-mail e il profilo lo segnala.
- La finestra di firma propone il metodo `proposed` e offre l'altro: **Ricevi il codice via e-mail** con l'app, **Usa l'app di autenticazione** con l'e-mail se l'app è configurata e verificabile. La conferma aperta con il primo metodo scade da sola.

### CieSign

Disponibile per il firmatario `HOLDER` sui documenti PDF degli atti del foglio, non per la presa visione: il socio maggiorenne dall'applicazione, il genitore o tutore dalla pagina pubblica.

1. Il firmatario scarica il documento congelato.
2. Lo firma in formato PAdES con l'app CieSign o con il Software CIE.
3. Carica il file firmato in Taurus, al massimo 25 MB.

Taurus accetta il file solo se:

- inizia con i byte esatti del documento congelato, perché la firma PAdES aggiunge un aggiornamento incrementale;
- contiene una sola firma nuova, `ETSI.CAdES.detached` o `adbe.pkcs7.detached`, che copre il file fino all'ultimo byte;
- l'aggiornamento incrementale aggiunge solo il campo della firma e il suo widget: catalogo, pagine, risorse, contenuti e annotazioni esistenti restano identici al documento congelato, salvo le voci del catalogo che la firma può aggiungere (modulo, permessi, dati di validazione, metadati, albero della struttura); un widget con azioni o un modulo XFA non sono ammessi;
- la firma è crittograficamente valida, con un solo firmatario e senza SHA-1 o MD5;
- il certificato risale alle CA della CIE del Ministero dell'Interno, configurate come trust anchor in `application.signature.cie.trust-anchors`, era valido alla data di caricamento e può firmare (uso della chiave `digitalSignature` o `nonRepudiation`);
- nome e cognome del certificato (`givenName` e `surname`, o in mancanza il nome comune) corrispondono al firmatario atteso, cioè all'anagrafica del socio o ai dati inseriti dal genitore e salvati sul foglio, senza distinzione di maiuscole, accenti, spazi, apostrofi, trattini e ordine tra nome e cognome;
- il codice fiscale del certificato, letto dal numero di serie del soggetto (per esempio `TINIT-…`), dal nome comune o dal qualificatore, coincide con quello dichiarato nei dati dell'Affidatario e salvato sul foglio.

CieSign non è disponibile per un Affidatario che ha dichiarato di non avere un codice fiscale italiano (`422 signature.cieSign.notAvailable`): la CIE riporta sempre il codice fiscale del titolare. Senza trust anchor l'installazione non offre CieSign (`422 signature.cieSign.notConfigured`); CieSign non è un requisito per attivare la firma elettronica.

La revoca è controllata con la CRL indicata nel certificato, scaricata con un timeout di 10 secondi (`application.signature.cie.revocation-timeout`), al massimo 20 MB, verificata con la chiave dell'emittente e tenuta in memoria fino al successivo aggiornamento, al massimo per un giorno. Un certificato revocato è rifiutato con `signature.cieSign.untrustedCertificate`; se la CRL non è raggiungibile, è troppo grande, non è firmata dall'emittente o è scaduta, la firma è accettata e la prova registra `revocationCheck = UNAVAILABLE`. All'emissione, con CieSign si firmano due file: `MAIN` per l'accettazione e `CLAUSES` per l'approvazione delle clausole. Un firmatario può usare CieSign per un documento e il codice per l'altro.

- La verifica avviene fuori dalle transazioni, perché la CRL può richiedere secondi; poi Taurus blocca l'atto, ricontrolla firmatario, stato e hash del documento congelato e registra la firma. I file rifiutati non vengono salvati.
- Il file accettato è salvato in `signature_document.signed_media_asset_id`; a firme complete il sigillo si aggiunge a questo file, così la firma CieSign continua a coprire gli stessi byte.
- La prova ha metodo `CIESIGN`, nessuna conferma e nessuna e-mail di destinazione, la stessa dichiarazione della firma con codice e i dati del certificato: soggetto ed emittente con i nomi degli attributi, numero di serie esadecimale, codice fiscale ed esito del controllo di revoca.

La verifica usa PDFBox e `bcpkix`, introdotto con il sigillo (vedi [Sigillo](#sigillo)).

## Firma del genitore o tutore

- Con ruolo `GUARDIAN` e modalità `ELECTRONIC`, l'amministratore inserisce all'emissione l'e-mail del genitore, salvata in `inventory_loan_sheet.holder_email` e rimossa con gli altri dati dell'Affidatario (vedi [Dati dell'Affidatario](#dati-dellaffidatario)). Senza e-mail l'emissione risponde `422 inventory.loanSheet.holderEmailRequired`.
- Il firmatario `HOLDER` degli atti del foglio non ha account: `user_keycloak_id` è vuoto ed `email` contiene l'indirizzo del genitore. Finché i dati non sono inseriti compare come «Genitore o tutore di» seguito dal nome del socio, poi con il nome inserito dal genitore.
- L'emissione ha anche il firmatario `MINOR`, non obbligatorio: il socio minorenne prende visione del documento `MAIN` dal proprio account, con lo scopo `MINOR_ACKNOWLEDGMENT`, dopo che il genitore ha inserito i dati, e riceve la notifica in quel momento. Se l'atto è firmato da tutte le parti prima, la presa visione non si può più dare.
- Per ogni atto da firmare (emissione, variazione, riconsegna) Taurus invia un link `{public-base-url}/firma/{token}`. La base URL viene dalla configurazione (`application.signature.public-base-url`), mai dall'header `Host`. L'e-mail parte dopo il commit dell'operazione: se il server SMTP la rifiuta l'operazione resta valida, il link risulta non inviato nella sezione **Firme** e un amministratore lo reinvia.
- Il token è di 32 byte casuali in base64url. Si salvano solo il digest SHA-256, nel registro pubblico, e un'impronta di 8 caratteri per l'interfaccia, nel tenant; il token non contiene tenant, atto o utente.
- Il tenant è risolto con il nuovo registro `public.signature_link_registry`, come per il feed calendario, accettando solo tenant e schema `ACTIVE` con l'inventario attivo.
- Il link vale 7 giorni (`application.signature.link-validity`) e un solo firmatario di un solo atto. Un nuovo link revoca il precedente; il link è revocato anche quando l'atto è firmato da tutte le parti, dichiarato firmato su carta o annullato, e un amministratore può revocarlo.
- A firme complete il genitore riceve la conferma di completamento con un nuovo link di 7 giorni, che apre la pagina con i documenti sigillati e l'attestazione. Un amministratore può reinviarlo finché i documenti sono in Taurus.
- La pagina pubblica mostra tenant, tipo di atto, numero del foglio, socio minorenne e scadenza del link, raccoglie i dati dell'emissione e registra le firme con il codice via e-mail, inviato allo stesso indirizzo, o con CieSign. Le conferme dal link hanno gli stessi limiti di quelle con account e il limite orario vale per ciascun link.
- Con i dati delle sezioni 1 e 2 il genitore inserisce nome, cognome e data di nascita propri, da maggiorenne, e il luogo di nascita del socio minorenne. Se cambia chi firma per l'Associazione quando i documenti dell'emissione sono già congelati, il genitore riceve un nuovo link per inserire di nuovo i dati.
- Le prove delle firme dal link non hanno identificativo Keycloak: registrano il nome inserito dal genitore, l'indirizzo del link e, come sessione, `link:` seguito dall'impronta del link.
- `SignatureLinkExpiryScheduler` gira ogni ora (`application.signature.link-expiry-cron`): notifica una volta ad admin e super admin i link scaduti senza la firma del genitore, con l'atto ancora in attesa, e rimuove dal registro pubblico le righe dei link revocati o scaduti.
- Limite noto: link e codice arrivano sulla stessa casella. La firma prova il controllo della casella; CieSign offre un'identificazione più forte ed è indicato nella pagina come metodo consigliato, quando il genitore ha dichiarato un codice fiscale italiano.

## Dati dell'Affidatario

All'emissione di un foglio `ELECTRONIC` l'Affidatario inserisce, al posto della compilazione a mano, i dati delle sezioni 1 e 2 elencati in [Variabili](#variabili).

- Sono tutti obbligatori tranne il telefono.
- La data di nascita del socio, come nome e cognome, viene dal profilo dell'utente, dove il socio la inserisce e la modifica dalla pagina **Profilo**. È obbligatoria: finché manca, la finestra dei dati non mostra i campi e rimanda al profilo, o per il genitore all'associazione, e il salvataggio dei dati risponde `422 signature.holderData.birthDateMissing`. Al salvataggio Taurus la copia sul foglio (`member_birth_date`), così i documenti congelati riportano la data in vigore in quel momento anche se all'emissione mancava.
- Il codice fiscale è validato nel formato e nel carattere di controllo. Chi non ha un codice fiscale italiano lo dichiara con una casella esplicita: il campo resta vuoto nel documento e CieSign non è disponibile.
- I dati entrano nel documento congelato dell'emissione. Sul foglio Taurus salva solo ciò che serve a verificare CieSign negli atti successivi: il codice fiscale (`holder_tax_code`) e, per il ruolo `GUARDIAN`, nome e cognome del genitore (`holder_name`), oltre all'e-mail del genitore (`holder_email`).
- Questi campi sono rimossi alla definizione, all'annullamento e alla cancellazione GDPR, anche se la regola generale dell'annullamento non rimuove i dati personali del foglio.

## Indipendenza dal modello

### Marcatori

| Marcatore | Regola |
| --- | --- |
| `data-taurus-act` | obbligatorio su ogni figlio diretto di `<body>`; uno o più valori separati da spazio tra `issue`, `variation`, `return` e `all` |
| `data-taurus-sign="clauses"` | esattamente un elemento, dentro un blocco `issue`, con testo non vuoto |

Per ogni atto Taurus compila il modello e, prima della conversione in PDF, rimuove con jsoup i figli di `<body>` che non dichiarano il tipo dell'atto o `all`. Intestazione e piè di pagina ricorrenti usano `all`.

Il documento `CLAUSES` usa un modello interno non personalizzabile (`templates/signature/clauses-approval.html`) con denominazione del tenant, numero del foglio, nome dell'Affidatario, hash del documento `MAIN`, testo del blocco marcato, ricavato come testo semplice con spazi normalizzati, e dichiarazione di approvazione specifica.

Nel documento di una variazione e nel verbale di riconsegna il modello predefinito non interrompe la pagina prima della sezione: la classe `new-page` è condizionata a `firma.elettronica`, perché la sezione è l'unico contenuto del documento oltre a intestazione e piè di pagina.

### Segnalazioni del validatore

Nuovi codici non bloccanti per l'attivazione del modello, prodotti da `LoanTemplateValidator.signatureIssues`. Rendono il modello non firmabile e l'emissione `ELECTRONIC` risponde `inventory.loanSheet.templateNotSignable`. La verifica di un modello caricato li mostra solo ai tenant con la firma elettronica attiva; la pagina del modello mostra sempre quelli del modello in uso nella sezione **Firma elettronica**.

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
| `firma.giorniDisponibilita` | TEXT | giorni del periodo di grazia dopo la definizione o l'annullamento, per esempio `30` |
| `atto.emissione`, `atto.variazione`, `atto.riconsegna` | FLAG | tipo dell'atto in generazione |
| `affidatario.luogoNascita`, `affidatario.residenza`, `affidatario.cap`, `affidatario.cittaProvincia`, `affidatario.codiceFiscale`, `affidatario.documento`, `affidatario.documentoRilasciatoDa`, `affidatario.documentoRilasciatoIl`, `affidatario.telefono` | TEXT | dati inseriti dall'Affidatario prima della firma; vuoti su carta |
| `minore.luogoNascita` | TEXT | luogo di nascita del socio minorenne, inserito dal genitore |

Con ruolo `GUARDIAN` e modalità `ELECTRONIC`, `affidatario.nomeCognome`, `affidatario.dataNascita` e `affidatario.email` contengono i dati del genitore, inseriti nella pagina pubblica e stampati solo nei documenti dell'emissione. Nella variazione `registro` contiene solo le righe dell'atto e `registroRigheVuote` è vuoto; nella riconsegna `riconsegna.righeVuote` è vuoto.

Le variabili dei dati inseriti dall'Affidatario sono valorizzate solo nei documenti dell'emissione; nelle variazioni e nella riconsegna sono vuote. Quali dati Taurus salva sul foglio è descritto in [Dati dell'Affidatario](#dati-dellaffidatario).

### Modello predefinito

Una nuova versione del predefinito:

- marca ogni blocco con `data-taurus-act` e il paragrafo dell'approvazione specifica con `data-taurus-sign="clauses"`;
- stampa i dati delle sezioni 1 e 2 con le nuove variabili, lasciando la riga vuota quando mancano;
- stampa `firma.dicitura` al posto delle righe per la firma e omette la sigla dell'Affidatario a piè di pagina quando `firma.elettronica` è vero;
- quando `firma.elettronica` è vero, aggiunge alla lettera f) dell'informativa un paragrafo sugli atti firmati elettronicamente: l'Associazione li conserva nei propri archivi, fuori dalla piattaforma, per il tempo necessario all'accertamento, all'esercizio o alla difesa dei propri diritti; la piattaforma li elimina `firma.giorniDisponibilita` giorni dopo la definizione o l'annullamento del foglio. Il testo è soggetto alla [Verifica legale](#verifica-legale).

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
- La firma CMS del sigillo richiede `bcpkix`. `bcprov-jdk15on` va sostituito con `bcprov-jdk18on` e `bcpkix-jdk18on`, escludendo la dipendenza transitiva di `web-push` e verificando i test Web Push. Le due famiglie di artefatti non devono convivere nel classpath.

### Attestazione

PDF separato, generato dal modello interno `templates/signature/attestation.html` e sigillato. È separato dal documento perché aggiungere pagine dopo una firma CieSign la invaliderebbe. È la prova che il tenant conserva insieme ai documenti: riporta tutte le informazioni delle prove, così l'atto resta verificabile anche dopo l'eliminazione da Taurus. Contiene:

- tenant, tipo di atto, numero del foglio;
- per ogni documento: ruolo, hash congelato, hash dopo il sigillo;
- per ogni firma: ruolo, nome, scopo, metodo, testo della dichiarazione, data e ora nel fuso del tenant e in UTC, e-mail mascherata oppure soggetto, emittente, numero di serie e codice fiscale del certificato con l'esito del controllo di revoca, indirizzo IP, user agent, posizione e hash della prova nella catena;
- l'impronta SHA-256 del certificato del sigillo;
- le istruzioni per verificare hash e sigillo senza Taurus.

La presa visione non ha attestazione: le sue prove sono consultabili nell'applicazione.

### Fascicolo del foglio

- `GET /api/inventory/loan-sheets/{id}/signature-dossier` restituisce uno ZIP con una cartella per ogni atto `SIGNED` del foglio, contenente i documenti sigillati e l'attestazione, e un indice `INDICE.txt` con atti, file e hash SHA-256.
- Gli atti `SIGNED_ON_PAPER` compaiono nell'indice con la nota della dichiarazione: la copia cartacea è già in custodia al tenant.
- Il fascicolo è disponibile ad admin e super admin dal primo atto firmato fino all'eliminazione. L'Affidatario con account scarica i singoli documenti e le attestazioni dei propri atti.
- Il fascicolo è generato a ogni richiesta dai file salvati, che non cambiano; ogni download è registrato.

## Prove e catena

Ogni firma crea una riga immutabile di `signature_evidence` con:

- atto, documento, firmatario, scopo e metodo;
- hash del documento, testo della dichiarazione e suo hash;
- nome del firmatario, identificativo Keycloak se presente, e-mail di destinazione del codice;
- per CieSign: soggetto, emittente, numero di serie e codice fiscale del certificato, esito del controllo di revoca;
- conferma usata, `sid` del token, indirizzo IP, user agent, data e ora UTC del server;
- numero progressivo nel tenant, hash della prova precedente e hash della prova.

I dati personali della prova (nome, identificativo Keycloak, e-mail, dati del certificato, indirizzo IP, user agent e testo della dichiarazione) hanno un proprio digest, `personal_data_hash`: lo SHA-256 del loro JSON canonico. L'hash della prova è lo SHA-256 del JSON canonico degli altri campi, incluso `personal_data_hash`, il progressivo e l'hash precedente. L'inserimento blocca con lock pessimistico la riga di `signature_chain_head`, come il contatore dei fogli. `POST /api/inventory/signatures/verify-chain` ricalcola la catena del tenant e restituisce la prima posizione non coerente.

All'eliminazione dell'atto, al termine del periodo di grazia o con la cancellazione GDPR, i dati personali della prova vengono cancellati e la riga riceve `purged_at`. Restano `personal_data_hash`, gli altri campi e i collegamenti: la verifica ricalcola ancora l'hash di ogni prova e la continuità della catena, e controlla `personal_data_hash` solo sulle righe non eliminate.

## Presa visione con codice

Con la firma elettronica attiva:

1. L'utente sceglie accettazione o rifiuto, con motivazione, per una o più assegnazioni, fino a 50.
2. `POST /api/user/signatures/challenges` con scopo `ASSIGNMENT_DECISION` ed elementi `{ assignmentId, revisionHash, decision, rejectionReason }`. Il server applica a ogni elemento i controlli di `InventoryService.decide`: proprietario, hash corrente, decisione non ancora presa, motivazione per il rifiuto.
3. Alla conferma del codice, in una sola transazione, Taurus registra ogni decisione con l'hash autenticato attuale, crea un atto `ASSIGNMENT_ACKNOWLEDGMENT` firmato con documento `SNAPSHOT` e una prova, e collega la decisione alla prova (`inventory_assignment_decision.signature_evidence_id`).
4. Se nel frattempo una revisione è cambiata, l'intera conferma fallisce con `inventory.revision.versionConflict` e viene chiusa.

La dichiarazione registrata è «Ho preso visione dei dati e delle fotografie dell'assegnazione e li accetto» oppure «… e li rifiuto per il seguente motivo: …», con nome dell'oggetto, numero inventariale e numero di revisione.

`POST /api/user/inventory/assignments/{id}/decision` risponde `409 inventory.decision.signatureRequired` quando la firma elettronica è attiva. Le decisioni già registrate non cambiano.

La presa visione non genera sigillo né attestazione e non ha periodo di grazia: i dati personali delle sue prove restano finché resta la decisione e sono cancellati dal flusso GDPR.

## Ciclo di vita del foglio

| Evento | Effetto sugli atti di un foglio `ELECTRONIC` |
| --- | --- |
| Emissione | atto `LOAN_SHEET_ISSUE` in `AWAITING_DATA`; notifica all'Affidatario oppure link al genitore |
| Consegna successiva, sostituzione, rinnovo, restituzione registrata nel registro | un atto `LOAN_SHEET_VARIATION` per operazione, nella stessa transazione dell'operazione: `InventoryService` e `InventoryLoanSheetService` pubblicano l'evento `LoanSheetVariationRecorded`, che crea l'atto con le sole righe dell'operazione. Una restituzione che lascia il foglio senza beni in carico non crea un atto: la documenta il verbale di riconsegna |
| Chiusura | atto `LOAN_SHEET_RETURN` |
| Riconsegna `SIGNED` o `SIGNED_ON_PAPER` con esito `COMPLIANT` | definizione immediata con l'evento `LoanSheetReturnSigned`, al posto della conferma manuale della firma del verbale; resta la definizione automatica dopo 30 giorni |
| Definizione | dati personali rimossi dalle tabelle come oggi, compresi i [dati dell'Affidatario](#dati-dellaffidatario); gli atti ancora in attesa diventano `CANCELLED`; gli atti firmati ricevono `purge_after` = definizione + periodo di grazia; notifica agli amministratori con la data di eliminazione del fascicolo |
| Annullamento | atti in attesa `CANCELLED`, documenti congelati e link rimossi, dati dell'Affidatario rimossi; gli atti firmati ricevono `purge_after` = annullamento + periodo di grazia; notifica agli amministratori con la data di eliminazione del fascicolo |
| Cancellazione GDPR | atti in attesa `CANCELLED`; documenti, attestazioni e dati personali delle prove di tutti gli atti del socio eliminati, comprese le prese visione: subito se il socio non ha materiale in carico, altrimenti al completamento della pratica dopo la riconsegna |
| Fine del periodo di grazia | documenti sigillati, file CieSign, attestazioni e dati personali delle prove eliminati; l'atto riceve `purged_at` |

Il periodo di grazia è `application.signature.download-grace-days`, 30 giorni per default. `SignaturePurgeScheduler` gira ogni notte ed elimina gli atti con `purge_after` passato: azzera i riferimenti ai media, invoca `MediaService.deleteIfUnreferenced` e cancella i dati personali delle prove. La disattivazione della firma elettronica non cambia le date di eliminazione già fissate.

## Conservazione e GDPR

- La conservazione degli atti firmati per i termini di legge è responsabilità del tenant, titolare del trattamento. Taurus mette a disposizione il fascicolo del foglio e non trattiene documenti o dati personali oltre il periodo di grazia.
- Nei fogli `ELECTRONIC`, fino all'eliminazione, Taurus conserva i dati che su carta restano sulla copia cartacea (codice fiscale, documento, residenza, telefono e dati del genitore) solo all'interno dei documenti firmati, più i [dati dell'Affidatario](#dati-dellaffidatario) salvati sul foglio per la verifica CieSign.
- La definizione e l'annullamento rimuovono subito i dati dell'Affidatario dal foglio; documenti, attestazioni e dati personali delle prove restano scaricabili per il periodo di grazia e poi sono eliminati.
- Il flusso GDPR esistente elimina anche documenti, attestazioni e dati personali delle prove del socio: subito, se non ha materiale in carico (evento `UserErasureCompleted` di `DataErasureService`, gestito da `InventoryErasureService`), altrimenti al completamento della pratica dopo la riconsegna (`InventoryErasureService.complete`). La finestra di conferma avvisa l'amministratore di scaricare prima i fascicoli dei fogli coinvolti.
- Documenti firmati, attestazioni e fascicolo sono scaricabili da admin e super admin del tenant; documenti e attestazioni anche dai firmatari con account. Ogni download è registrato.
- L'informativa del modello deve indicare chi conserva gli atti firmati e per quanto tempo.
- I log non contengono codici, token, e-mail, codici fiscali o contenuto dei documenti.

## Autorizzazioni e isolamento tenant

| Operazione | Firmatario | Admin | Super admin |
| --- | ---: | ---: | ---: |
| Vedere e firmare i propri atti | sì | sì | sì |
| Inserire i dati dell'Affidatario | solo `HOLDER` | no | no |
| Firmare per l'Associazione | solo il designato | sì, se designato | sì, se designato |
| Attivare la firma elettronica del tenant | no | sì | sì |
| Vedere atti, prove e attestazioni del tenant | propri | sì | sì |
| Scaricare il fascicolo del foglio | no | sì | sì |
| Designare un altro firmatario per l'Associazione | no | sì | sì |
| Reinviare o revocare un link | no | sì | sì |
| Dichiarare un atto firmato su carta | no | sì | sì |
| Verificare la catena delle prove | no | sì | sì |

- Gli endpoint personali sono in `/api/user/signatures/**` e vanno aggiunti esplicitamente all'elenco di `SecurityConfiguration`, che mantiene il `denyAll` sugli altri percorsi. Un atto di cui l'utente non è firmatario risponde `404`.
- Gli endpoint pubblici sono solo i percorsi `/api/signature-links/v1/*/…` elencati sotto, con `permitAll()` per metodo e `denyAll()` sul resto, prima della regola generale `/api/**`. L'autenticazione avviene solo tramite digest del token e non crea una `Authentication` riutilizzabile.
- Tutti gli endpoint sono soggetti a `@RequiresTenantFeature(TenantFeature.INVENTORY)`; per il percorso pubblico la feature disattivata è indistinguibile da un token inesistente.
- Ogni identificativo è verificato nel tenant del token JWT o del registro dei link; le tabelle stanno nello schema del tenant, tranne `signature_link_registry`.

## Modello dati e migrazioni

### Schema pubblico

Changelog in `master.xml`: `20261020100000_signature_link_registry.xml` (fase 4).

**`signature_link_registry`**: `token_digest` (PK, SHA-256 esadecimale), `tenant_id` (FK verso `tenant`, eliminata a cascata), `link_id`, `expires_at`, `revoked_at`. Nessun dato personale. Creazione, rinnovo e revoca aggiornano registro e tabella tenant nella stessa transazione; la cancellazione del tenant elimina le righe prima del `DROP SCHEMA`; le righe dei link revocati o scaduti sono rimosse ogni ora.

### Schema del tenant

Changelog in `tenant-master.xml`: `20261018100000_inventory_electronic_signature.xml` (fase 1: tabelle della firma e interruttore), `20261019100000_inventory_loan_sheet_signature.xml` (fase 2: colonne del foglio e prove delle dichiarazioni su carta) e `20261020100001_inventory_signature_links.xml` (fase 4: link dei genitori e conferme dal link). Tutte le tabelle hanno i campi di audit `deleted`, `insert_date`, `insert_by`, `edit_date`, `edit_by`; quelle modificabili anche `entity_version`.

Modifiche a tabelle esistenti:

| Tabella | Colonna | Tipo | Note |
| --- | --- | --- | --- |
| `inventory_loan_settings` | `electronic_signature_enabled` | `BOOLEAN` | non nullo, predefinito `false` |
| `inventory_loan_sheet` | `signature_mode` | `VARCHAR(16)` | `PAPER` o `ELECTRONIC`, non nullo, predefinito `PAPER` |
| `inventory_loan_sheet` | `holder_email` | `VARCHAR(255)` | solo `GUARDIAN` elettronico; rimossa alla definizione, all'annullamento e con la cancellazione GDPR |
| `inventory_loan_sheet` | `holder_name` | `VARCHAR(255)` | nome e cognome del genitore, solo `GUARDIAN` elettronico; rimossi come `holder_email` |
| `inventory_loan_sheet` | `holder_tax_code` | `VARCHAR(16)` | codice fiscale dell'Affidatario, solo `ELECTRONIC`; nullo se dichiarato assente; rimosso come `holder_email` |
| `inventory_loan_sheet` | `holder_no_tax_code` | `BOOLEAN` | l'Affidatario ha dichiarato di non avere un codice fiscale italiano; non nullo, predefinito `false` |
| `inventory_assignment_decision` | `signature_evidence_id` | `BIGINT` | FK verso `signature_evidence`, nullo per le decisioni senza firma |

Nuove tabelle:

**`signature_act`**: `id`, `act_type`, `status`, `sheet_id`, `assignment_id`, `renewal_id`, `return_id`, `decision_id` (FK facoltative verso le entità di origine), `template_id`, `template_hash`, `attestation_media_asset_id`, `completed_at`, `paper_note`, `purge_after`, `purged_at`, `cancelled_at`, audit, `entity_version`. Indice su `purge_after` per le righe con `purged_at` nullo.

**`signature_document`**: `id`, `act_id`, `role` (`MAIN`, `CLAUSES`, `SNAPSHOT`), `frozen_media_asset_id`, `frozen_hash`, `signed_media_asset_id` (file con firma CieSign, se presente), `sealed_media_asset_id`, `sealed_hash`, audit. Vincolo univoco `(act_id, role)` sulle righe non cancellate.

**`signature_act_signer`**: `id`, `act_id`, `signer_role`, `required`, `user_keycloak_id` (nullo per il genitore), `display_name`, `email` (solo genitore), `status` (`PENDING`, `SIGNED`, `SIGNED_ON_PAPER`), `signed_at`, audit, `entity_version`.

**`signature_challenge`**: `id`, `purpose`, `method` (`EMAIL_OTP`, `TOTP`), `user_keycloak_id` oppure `link_id`, `destination_email`, `code_digest`, `items_json`, `items_hash`, `expires_at`, `attempts`, `sends`, `last_sent_at`, `status` (`OPEN`, `CONFIRMED`, `EXPIRED`, `LOCKED`, `CANCELLED`), `confirmed_at`, audit, `entity_version`. Le conferme chiuse da più di 30 giorni sono eliminate.

**`signature_evidence`**: colonne descritte in [Prove e catena](#prove-e-catena), `personal_data_hash`, `sequence` univoco, `previous_hash`, `evidence_hash`, `purged_at`, audit. `document_id`, `document_hash` e `signer_id` sono nulli solo nelle dichiarazioni di firma su carta senza documenti o fatte da un amministratore che non firma l'atto. Nessun aggiornamento applicativo, salvo la cancellazione dei dati personali all'eliminazione dell'atto o con la cancellazione GDPR.

**`signature_chain_head`**: riga unica con `last_sequence`, `last_hash`, `entity_version`.

**`signature_link`**: `id`, `signer_id`, `token_fingerprint`, `expires_at`, `revoked_at`, `sent_at` (accettazione dell'e-mail da parte del server SMTP), `last_used_at`, `expiry_notified_at`, audit, `entity_version`. `signature_challenge.link_id` (FK) collega le conferme dal link; ogni conferma ha `user_keycloak_id` oppure `link_id`.

`MediaRepository.isUnreferenced` aggiunge `signature_document` (tre colonne media) e `signature_act.attestation_media_asset_id` ai riferimenti, così la pulizia non rimuove i file degli atti.

## Keycloak

- Nuovo realm resource provider `taurus-otp` in `keycloak-authenticator`, registrato in `META-INF/services`, con le operazioni descritte in [App di autenticazione](#app-di-autenticazione). La logica sta in `TaurusOtp`, separata dallo strato JAX-RS di `TaurusOtpResource`. Keycloak avvisa all'avvio che l'SPI `realm-restapi-extension` è interna: va riverificata a ogni aggiornamento di versione, come quelle dell'autenticatore e del mapper già presenti.
- Nuovo client confidenziale `taurus-signature` in `taurus-realm.json`: solo service account, nessun flusso browser, solo lo scope `basic`, ruolo client `otp-verifier` assegnato al service account `service-account-taurus-signature`. Il segreto di sviluppo segue la convenzione del realm locale (`taurus-signature`, come `web_app`); in produzione arriva da variabile d'ambiente e non va nel repository. Un realm di sviluppo già importato non riceve il client: va reimportato o configurato a mano come indicato nel [Runbook operativo](runbook-operativo.md).
- Il flusso "Browser with Tenant and Role" resta invariato: l'OTP condizionale al login è accettato.
- `smtpServer` del realm può usare la stessa casella dell'associazione per recupero password e verifica e-mail; la configurazione è documentata nel runbook, senza credenziali.
- Provider e immagine eseguita devono usare la stessa versione di Keycloak. Come primo passo dell'implementazione, `keycloak.yml`, il runbook e `keycloak.version` del modulo vengono portati sulla stessa versione 26.6.x.

## API REST

### Firmatario autenticato (`/api/user/signatures`)

| Metodo e percorso | Descrizione |
| --- | --- |
| `GET /acts` | atti dei fogli in cui l'utente firma, non annullati e non eliminati, dal più recente: stato, documenti con hash, firmatari, conferme ancora attese dall'utente (`pendingPurposes`), richiesta dei dati (`dataRequired`) e disponibilità di CieSign per l'Affidatario con codice fiscale italiano (`cieSignAvailable`) |
| `GET /acts/{id}` | dettaglio di un atto dell'utente, con gli stessi campi |
| `PUT /acts/{id}/holder-data` | dati dell'Affidatario; genera i documenti congelati e restituisce gli hash |
| `GET /acts/{id}/documents/{role}` | documento congelato o, ad atto completato, sigillato |
| `GET /acts/{id}/attestation` | attestazione, ad atto completato |
| `POST /acts/{id}/documents/{role}/ciesign` | caricamento multipart (`file`) del documento `MAIN` o `CLAUSES` firmato con CieSign dall'Affidatario; registra la firma e restituisce l'esito per l'atto |
| `GET /methods` | firma elettronica attiva nel tenant (`enabled`), metodo preferito e proposto, stato dell'app, e-mail mascherata |
| `PUT /methods` | `{ preference }`; salva il metodo preferito e restituisce i metodi |
| `POST /challenges` | crea una conferma: `{ purpose, method, items }`; per e-mail invia il codice |
| `POST /challenges/{id}/resend` | nuovo codice via e-mail |
| `POST /challenges/{id}/confirm` | `{ code }`; registra le firme e restituisce l'esito per atto |

Gli elementi di una conferma `ACCEPTANCE` possono riguardare più atti di variazione dello stesso firmatario. Emissione e riconsegna si confermano da sole, e `ACCEPTANCE` e `CLAUSE_APPROVAL` sono sempre conferme distinte.

### Amministrazione (`/api/inventory`)

| Metodo e percorso | Descrizione |
| --- | --- |
| `PUT /loan-template/signature` | `{ enabled, entityVersion }`; attiva o disattiva la firma elettronica |
| `GET /loan-template` | risposta esistente, con requisiti dell'installazione e segnalazioni dei marcatori del modello in uso (`signature.templateIssues`) |
| `GET /users/{userIndex}/association-signers` | amministratori attivi che possono firmare per l'Associazione un foglio del socio, senza il socio |
| `GET /loan-sheets/{id}/signature-acts` | atti del foglio con firmatari, stato e data di eliminazione |
| `GET /loan-sheets/{id}/signature-dossier` | fascicolo ZIP del foglio |
| `GET /signature-acts/{id}/evidence` | prove dell'atto, con i dati del certificato delle firme CieSign |
| `GET /signature-acts/{id}/documents/{role}`, `GET /signature-acts/{id}/attestation` | download per l'amministrazione |
| `PUT /signature-acts/{id}/association-signer` | `{ userId, entityVersion }`; designa un altro firmatario per l'Associazione, finché nessuno ha firmato |
| `POST /signature-acts/{id}/links/{signerId}/resend` | nuovo link al genitore, che revoca il precedente: per firmare un atto in attesa o, ad atto firmato, per scaricarne i documenti; se il server SMTP rifiuta l'e-mail risponde `502 signature.link.sendFailed` |
| `DELETE /signature-acts/{id}/links/{signerId}` | revoca del link |
| `POST /signature-acts/{id}/paper-declaration` | `{ note, method }`; crea la conferma `PAPER_DECLARATION` dell'amministratore con il metodo indicato, via e-mail se assente, da confermare con `/api/user/signatures/challenges/{id}/confirm`. L'interfaccia apre la stessa conferma con `POST /api/user/signatures/challenges` |
| `POST /signatures/verify-chain` | verifica della catena del tenant |

La richiesta di emissione esistente (`POST /api/inventory/users/{userIndex}/loan-sheets`) acquisisce `signatureMode`, `associationSignerId` (identificativo Keycloak dell'amministratore) e, per `GUARDIAN` elettronico, `holderEmail`. Gli atti mostrati agli amministratori riportano il ruolo dell'Affidatario (`holderRole`) e, per il firmatario senza account, l'e-mail mascherata e l'ultimo link: impronta, invio, scadenza, ultimo accesso e validità. La proposta di emissione restituisce `signature`: firma attiva, firmabilità del modello in uso, amministratori ammessi e firmatario predefinito, cioè l'amministratore corrente se ammesso. Il riepilogo dei fogli espone `signatureMode`. Il PDF di un foglio `ELECTRONIC` porta su ogni pagina la filigrana `COPIA DI CONSULTAZIONE – NON FIRMATA`.

### Link pubblici (`/api/signature-links/v1/{token}`)

| Metodo e percorso | Descrizione |
| --- | --- |
| `GET /` | sintesi: tenant, socio minorenne, e-mail mascherata, scadenza del link e atto con stato, documenti, firmatari senza identificativi, conferme attese e richiesta dei dati (`dataRequired`) |
| `PUT /holder-data` | dati dell'Affidatario, con nome, cognome e data di nascita del genitore e luogo di nascita del minore |
| `GET /documents/{role}` | documento congelato o sigillato |
| `POST /documents/{role}/ciesign` | caricamento multipart (`file`) del documento dell'atto del link firmato con CieSign |
| `POST /challenges` | `{ purpose, documentHash }`: conferma `ACCEPTANCE` o `CLAUSE_APPROVAL` del documento dell'atto del link, con codice via e-mail all'indirizzo del link |
| `POST /challenges/{id}/resend` | nuovo codice allo stesso indirizzo |
| `POST /challenges/{id}/confirm` | conferma del codice |
| `GET /attestation` | attestazione, finché il link è valido |

Un token inesistente, scaduto o revocato, un tenant non attivo o una feature disattivata rispondono `404` senza corpo applicativo. Le risposte hanno `Cache-Control: no-store` e `Referrer-Policy: no-referrer`. Un limitatore basato su Caffeine, come `CalendarFeedRateLimiter`, limita le richieste per token (`application.signature.link-rate-limit-per-token-hour`, 120) e per IP (`application.signature.link-rate-limit-per-ip-hour`, 600): oltre risponde `429` con `Retry-After`. Gli errori applicativi, per esempio un codice errato, seguono lo standard come negli altri endpoint.

### Errori

Gli errori seguono lo [Standard degli errori applicativi](application-errors-standard.md): chiave nel campo `message`, dati del caso in `args`, nessun testo.

| Chiave | HTTP | Caso |
| --- | --- | --- |
| `signature.act.notAwaitingData` | 409 | dati inviati per un atto che non li attende o già firmato |
| `signature.act.notAwaitingSignatures` | 409 | atto già completato, annullato o in attesa di dati |
| `signature.act.documentChanged` | 409 | hash inviato diverso dal documento congelato |
| `signature.act.alreadySigned` | 409 | il firmatario ha già firmato |
| `signature.act.purged` | 410 | documenti, attestazioni o fascicolo già eliminati al termine del periodo di grazia o con la cancellazione GDPR |
| `signature.act.batchNotAllowed` | 422 | conferma multipla non ammessa per il tipo di atto o lo scopo |
| `signature.holderData.invalid` | 422 | dati dell'Affidatario mancanti o non validi, campi in `args.fields` |
| `signature.act.acknowledgmentsPending` | 409 | firma o invio dei dati dell'Affidatario con prese visione in attesa sulle assegnazioni del foglio, numero in `args.count` |
| `signature.holderData.birthDateMissing` | 422 | dati dell'Affidatario inviati senza la data di nascita del socio nel suo profilo; `args.holderRole` distingue il socio maggiorenne dal genitore |
| `signature.disabled` | 422 | conferma di una presa visione con la firma elettronica non attiva nel tenant |
| `signature.challenge.invalid` | 400 | elementi mancanti, duplicati, oltre il massimo o scopo non supportato |
| `signature.challenge.notFound` | 404 | conferma inesistente o di un altro utente |
| `signature.challenge.closed` | 409 | codice o nuovo invio per una conferma già confermata o annullata |
| `signature.challenge.expired` | 410 | codice scaduto |
| `signature.challenge.invalidCode` | 422 | codice errato, con `args.remainingAttempts` |
| `signature.challenge.locked` | 409 | tentativi esauriti |
| `signature.challenge.limit` | 409 | nuovo invio oltre il numero massimo di codici, con `args.maxSends` |
| `signature.challenge.resendTooSoon` | 429 | nuovo invio prima del tempo o troppe conferme nell'ultima ora, con `args.retryAfterSeconds` |
| `signature.method.totpNotConfigured` | 422 | app richiesta ma non configurata |
| `signature.method.unavailable` | 502 | Keycloak non raggiungibile |
| `signature.mail.addressMissing` | 422 | firmatario senza indirizzo e-mail |
| `signature.mail.sendFailed` | 502 | il server SMTP non ha accettato l'e-mail del codice; la conferma è annullata |
| `signature.link.sendFailed` | 502 | il server SMTP non ha accettato l'e-mail del nuovo link al genitore; il link è creato ma non inviato |
| `signature.cieSign.documentMismatch` | 422 | il file non contiene il documento congelato, l'aggiornamento cambia il documento o il file è stato modificato dopo la firma |
| `signature.cieSign.invalidSignature` | 422 | file vuoto o illeggibile, firma assente, multipla o non valida |
| `signature.cieSign.untrustedCertificate` | 422 | certificato non riconducibile alla CIE, scaduto, revocato o non adatto alla firma |
| `signature.cieSign.signerMismatch` | 422 | nome o codice fiscale diversi dal firmatario atteso |
| `signature.cieSign.tooLarge` | 413 | file oltre 25 MB |
| `signature.cieSign.notAvailable` | 422 | CieSign richiesto da un Affidatario senza codice fiscale italiano |
| `signature.cieSign.notConfigured` | 422 | CieSign richiesto in un'installazione senza i certificati delle CA della CIE |
| `signature.associationSigner.sameAsHolder` | 422 | firmatario per l'Associazione coincidente con l'Affidatario |
| `signature.associationSigner.notAdmin` | 422 | firmatario per l'Associazione non più admin o super admin attivo |
| `signature.associationSigner.locked` | 409 | cambio del firmatario per l'Associazione dopo la prima firma dell'atto |
| `signature.settings.requirementsMissing` | 422 | attivazione senza SMTP, chiave dei codici o sigillo configurati, con `args.missing` |
| `signature.settings.versionConflict` | 409 | impostazione modificata da un'altra richiesta |
| `inventory.loanSheet.templateNotSignable` | 422 | emissione elettronica con modello senza marcatori validi |
| `inventory.loanSheet.holderEmailRequired` | 422 | Affidatario senza e-mail in modalità elettronica: socio maggiorenne senza e-mail o genitore senza e-mail inserita all'emissione |
| `inventory.loanSheet.signatureDisabled` | 422 | emissione elettronica con firma non attiva nel tenant |
| `signature.act.notFound` | 404 | atto inesistente o di cui l'utente non è firmatario |
| `signature.act.versionConflict` | 409 | cambio del firmatario per l'Associazione su un atto modificato da un'altra richiesta |
| `inventory.decision.signatureRequired` | 409 | decisione diretta con firma elettronica attiva |

Un atto o una conferma non accessibili all'utente rispondono `404`, come le risorse di altri soci.

## Interfaccia utente

La vista segue il [Taurus Layout Standard](taurus-layout-standard.md) e la documentazione PrimeNG in `docs/llms-full.md`, in particolare Dialog, Stepper, InputOtp e FileUpload.

- **Profilo**: sezione **Firma elettronica**, solo con l'inventario attivo e la firma elettronica attiva nel tenant, con il metodo preferito (**Codice via e-mail** o **App di autenticazione**, salvato alla scelta), lo stato dell'app (configurata, non configurata o non disponibile) e il pulsante **Configura app**, che avvia l'azione `CONFIGURE_TOTP` già presente. Un avviso segnala quando l'app è preferita ma le firme useranno ancora il codice via e-mail; un testo avvisa che l'app sarà chiesta anche al login.
- **I miei oggetti**: riquadro **Firme**, prima dei fogli di affidamento, con gli atti in attesa della firma dell'utente, come Affidatario, come socio minorenne che prende visione (**Prendi visione**) o per l'Associazione, gli atti che l'utente ha già firmato e che attendono le altre parti, con i firmatari obbligatori mancanti, e gli atti firmati con i download di documenti e attestazione e, dopo la definizione o l'annullamento del foglio, la data fino a cui restano scaricabili. Le variazioni che attendono la stessa firma si selezionano e si firmano insieme con un solo codice, tranne quelle con prese visione in attesa. Un atto che l'Affidatario non può ancora firmare per le prese visione in attesa lo indica sotto l'operazione. Una notifica interna segnala ogni nuovo atto e la dashboard mostra in «Da fare» la voce **Firma richiesta** con il numero di atti in attesa dell'utente (vedi la [dashboard operativa](operational-dashboard-spec.md#firma-personale-inventory_signature_required)). Le prese visione restano nell'elenco delle consegne.
- **Finestra di firma**, ripetuta per ogni firma attesa dall'utente:
  1. **Dati**, solo all'emissione: campi delle sezioni 1 e 2, con codice fiscale validato nel formato e nel carattere di controllo e casella **Non ho un codice fiscale italiano**; tutti obbligatori tranne il telefono. La data di nascita del socio compare in sola lettura, presa dal profilo; se manca, al posto dei campi un avviso chiede al socio di aggiungerla nel **Profilo**, con il pulsante **Vai al profilo**, o al genitore di chiederla all'associazione. Il genitore o tutore inserisce anche nome, cognome e data di nascita propri e il luogo di nascita del socio minorenne.
  2. **Documento**: apertura del PDF, impronta abbreviata, casella non preselezionata **Ho letto il documento**, **Approvo specificamente le clausole elencate nel documento** per le clausole o **Ho letto il documento e firmo per l'Associazione**.
  3. **Firma con codice**: la finestra del codice con la dichiarazione.

  Con prese visione in attesa sulle assegnazioni del foglio, la finestra dell'Affidatario mostra, al posto dei dati e del documento, l'avviso di prendere prima visione delle consegne, con «Accetta e firma» nell'elenco delle consegne, e il pulsante **Chiudi**; al genitore chiede di attendere la presa visione del socio minorenne.

  Dopo l'accettazione del foglio la finestra si riapre per l'approvazione delle clausole, con un nuovo codice; a firme completate un avviso indica se l'atto è firmato da tutte le parti. La finestra del codice offre l'altro metodo (vedi [Preferenza dell'utente](#preferenza-dellutente)).

  **CieSign**: per l'Affidatario con codice fiscale italiano, nell'installazione con i trust anchor, il passo **Documento** chiede **Come firmi** tra **Codice via e-mail** e **CieSign**, indicato come consigliato al genitore. Con CieSign la finestra elenca i tre passi (**Scarica il PDF**, che salva il documento senza aprirlo; firma con l'app o con il Software CIE senza modificarlo; caricamento del file firmato, al massimo 25 MB) e **Carica e firma** registra la firma senza la finestra del codice. La casella di lettura resta obbligatoria. Un file rifiutato lascia la finestra aperta con il motivo, per caricare un altro file o passare al codice; ogni altro errore chiude la finestra e ricarica i dati.
- **Presa visione**: nel dettaglio dell'assegnazione le caselle di accettazione e rifiuto restano e **Firma la scelta** apre la richiesta del codice. Nell'elenco **I miei oggetti**, tra le consegne in possesso, quelle senza presa visione si selezionano e **Accetta e firma** le accetta con un solo codice; il rifiuto, che richiede la motivazione, si conferma dal dettaglio.
- **Finestra del codice**: legge i metodi e usa quello proposto. Con l'e-mail mostra e-mail mascherata, scadenza, riferimento stampato nell'e-mail e **Invia un nuovo codice** con il conto alla rovescia; con l'app chiede il codice mostrato dall'app e l'ora entro cui confermare. In entrambi i casi mostra le dichiarazioni confermate, l'InputOtp a 6 cifre e il pulsante per passare all'altro metodo. Gli errori della conferma e del metodo compaiono nella finestra; dopo scadenza, blocco, chiusura o limite degli invii la finestra propone **Richiedi un nuovo codice** o, con l'app, **Ricomincia la firma**. Se l'app non è configurata o Keycloak non risponde la finestra lo spiega e propone il codice via e-mail. Ogni altro errore chiude la finestra e ricarica i dati.
- **Modello del foglio**: sezione **Firma elettronica** con interruttore, conferma prima di ogni cambio, requisiti dell'installazione (server di posta, chiave dei codici, certificato del sigillo), firmabilità del modello in uso con le segnalazioni dei marcatori e avviso sull'informativa. Con requisiti mancanti l'interruttore non attiva la firma e un avviso invita a rivolgersi a chi gestisce l'installazione.
- **Emissione del foglio**: modalità di firma, proposta come elettronica quando la firma è attiva e il modello è firmabile, altrimenti bloccata su carta con il motivo; firmatario per l'Associazione tra gli amministratori ammessi; per `GUARDIAN` elettronico, e-mail obbligatoria del genitore, che riceve il link.
- **Dettaglio del foglio**: per i fogli `ELECTRONIC`, sezione **Firme** con gli atti, lo stato per firmatario e le azioni **Firma per l'Associazione**, **Cambia firmatario**, **Firmato su carta**, **Atto firmato**, **Clausole firmate**, **Attestazione** e **Prove**, che per le firme CieSign mostrano numero di serie ed emittente del certificato ed esito del controllo di revoca; per il genitore, stato del link (inviato, non inviato, non più valido, ultimo accesso) con **Reinvia link** e **Revoca link**. In testa, **Scarica fascicolo** e, dopo la definizione o l'annullamento, la data di eliminazione con l'avviso che la conservazione spetta all'Associazione. L'elenco dei fogli segna quelli con firma elettronica e il PDF diventa **Copia**.
- **Definizione, annullamento e cancellazione GDPR**: le finestre di conferma di un foglio `ELECTRONIC` con atti firmati ricordano di scaricare il fascicolo e indicano quando sarà eliminato, o che sarà eliminato subito nel caso della cancellazione GDPR.
- **Pagina pubblica `/firma/:token`**: layout senza menu e senza login, con gli stessi passi e le stesse finestre della firma dall'applicazione (dati, documento, codice via e-mail o CieSign), i documenti da scaricare e i firmatari con il loro stato. `app.config.ts` non avvia il login Keycloak quando il percorso iniziale è `/firma/`, l'applicazione non legge i dati del tenant e l'intercettore chiama gli endpoint pubblici senza token; la pagina imposta il referrer a `no-referrer`. Un link non valido mostra che è scaduto, sostituito o non più necessario; quando la firma completa l'atto la pagina spiega che il nuovo collegamento ai documenti arriva via e-mail.

## Notifiche

Composte da `NoticesAspect` e registrate con `NotificationOutboxPublisher.enqueue` nella transazione dell'operazione:

- `Inventario: firma richiesta`, obbligatoria, con percorso verso **I miei oggetti**: all'Affidatario per l'emissione da completare con i dati, per ogni variazione e per il verbale; all'amministratore designato quando i documenti dell'emissione sono pronti, per variazioni e verbale e quando subentra a un altro; all'Affidatario quando deve reinserire i dati dopo il cambio del firmatario;
- `Inventario: atto firmato`, all'Affidatario e ad admin e super admin, per un atto firmato da tutte le parti o dichiarato su carta;
- `Inventario: fascicolo da conservare`, ad admin e super admin, alla definizione o all'annullamento di un foglio con atti firmati, con la data di eliminazione;
- `Inventario: link di firma scaduto`, ad admin e super admin, una volta per ogni link scaduto senza la firma del genitore mentre l'atto è in attesa;
- `Inventario: firma richiesta` anche al socio minorenne, quando il genitore ha inserito i dati dell'emissione, per la presa visione.

Le nuove notifiche seguono lo [Stile editoriale delle notifiche interne](notification-editorial-style.md) e le [Preferenze notifiche granulari](notification-preferences-spec.md).

Il genitore riceve solo e-mail: link, codici e conferma di completamento con il collegamento alla pagina pubblica, finché il link è valido.

## Osservabilità e sicurezza

- Metriche: `taurus.signature.challenges{method,outcome}`, `taurus.signature.acts{type,outcome}`, `taurus.signature.ciesign{outcome}` (`accepted`, `acceptedRevocationUnavailable` e l'ultima parte della chiave di ogni rifiuto, per esempio `signerMismatch`), `taurus.signature.mail{outcome}`, `taurus.signature.links{outcome}` (`issued`, `issuedSigned`, `revoked`, `expired`, `mailFailed`), `taurus.signature.purge{reason,outcome}`, senza identificativi di tenant, utente o atto.
- Log con identificativo di atto e tenant ed esito; mai codici, token, e-mail, codici fiscali o contenuto dei documenti.
- Allarmi: invii e-mail falliti consecutivi, verifica della catena non coerente, errori della verifica OTP di Keycloak (`signature_totp_unavailable` nel log, esito `unavailable` della metrica delle conferme).
- Codici e token confrontati a tempo costante; segreti (`otp-secret`, password del keystore, segreto del client Keycloak, credenziali SMTP) solo da variabili d'ambiente.
- Il file CieSign caricato è analizzato con i limiti di dimensione e senza eseguirne il contenuto; i caricamenti rifiutati non vengono salvati. Il log registra `signature_ciesign_accepted` e `signature_ciesign_rejected` con atto, documento ed esito, mai nome, codice fiscale o dati del certificato.

## Strategia di test

### Backend unitari

- Conferme: generazione, HMAC, scadenza, tentativi, uso singolo, limiti di invio, legame con firmatario e documenti.
- Catena delle prove: hash canonico, digest dei dati personali, continuità, concorrenza con lock, verifica completa dopo la cancellazione dei dati personali.
- Validatore: ogni nuovo codice di segnalazione; firmabilità del predefinito.
- Rendering per atto: filtro dei marcatori, registro con le sole righe della variazione, estrazione del blocco clausole.
- Dati dell'Affidatario: obbligatorietà, codice fiscale, dichiarazione di assenza del codice fiscale.
- Firmatario per l'Associazione: esclusione dell'Affidatario, ripiego sul firmatario dell'emissione, cambio prima e dopo la prima firma, firmatario non più amministratore.
- Sigillo: firma PAdES valida con PDFBox; firma CieSign ancora valida dopo il sigillo; attestazione verificabile da sola.
- Fascicolo: contenuto, indice con hash, atti su carta, atto eliminato.
- Web Push dopo la sostituzione di BouncyCastle.
- Link dei genitori: token e impronta, limitatore, risoluzione con tenant, inventario e link validi, revoca a ogni nuovo link, invio dopo il commit e invio rifiutato, conferme dal link legate al link, dati del genitore maggiorenne, firmatario `MINOR`.
- Dichiarazioni (`LoanSheetSigningTest`): testo per scopo e tipo di atto, compresa la presa visione del minore. Il codice non compare nella rappresentazione testuale della richiesta di conferma (`SignatureCodesTest`).
- Verifica CieSign con una CA di test configurata come trust anchor (`CieSignVerifierTest`): file valido, confronto dei nomi senza maiuscole, accenti, spazi, apostrofi e ordine, prefisso diverso, documento scaricato e caricato senza firma, modifica dopo la firma, pagine o catalogo cambiati nell'aggiornamento della firma, firma doppia o alterata, certificato non attendibile o scaduto, nome o codice fiscale diversi, CRL valida, con il certificato revocato o irraggiungibile, trust anchor da file e installazione senza trust anchor. `CieSignServiceTest`: registrazione dopo la verifica, nessuna registrazione per un file rifiutato, metriche, limite di 25 MB anche sul file caricato, contato tra i rifiuti.

### Backend integrazione

- Ciclo completo di emissione, variazioni, riconsegna e definizione immediata.
- Presa visione con firma attiva e disattiva, anche multipla, e conflitto di revisione.
- Isolamento tenant di atti, conferme e link; registro pubblico e tenant non attivi.
- Firma del genitore dal link su PostgreSQL: e-mail del link, dati, firme del foglio e delle clausole, presa visione del minore, firma per l'Associazione, link ai documenti firmati, reinvio, variazione, inventario disattivato e annullamento del foglio; CieSign rifiutato al genitore senza codice fiscale italiano.
- CieSign su PostgreSQL: il socio firma il foglio con CieSign e le clausole con il codice; caricamento di un amministratore, firma ripetuta, firmatario diverso e documento diverso rifiutati senza salvare il file; sigillo aggiunto al file CieSign, prova con i dati del certificato e catena verificabile.
- Elenco esplicito di `SecurityConfiguration`: percorsi nuovi ammessi, altri negati.
- Eliminazione: periodo di grazia dopo definizione e annullamento, `SignaturePurgeScheduler`, cancellazione GDPR immediata di un socio senza materiale in carico con `DataErasureService`, rimozione dei dati dell'Affidatario dal foglio; media non rimossi dalla pulizia finché referenziati.
- Invio dei codici con un server SMTP di test.
- Migrazioni Liquibase su schema pubblico e tenant.

### Keycloak

- Unitari del provider `taurus-otp` (`TaurusOtpTest`): chiamante senza token, senza ruolo o di un altro client, client disabilitato, utente inesistente, disabilitato o service account, codice valido su una qualsiasi credenziale, errato, riutilizzato, malformato, utente senza OTP.
- Integrazione (`TaurusOtpResourceIT`, eseguito da `mvnw verify` dopo il pacchetto): Keycloak 26.6.4 in Testcontainers con il JAR del provider e un realm di prova con policy che rifiuta i codici riutilizzati; stato degli utenti, codice valido accettato una sola volta, codice errato, utente senza app, chiamante di un altro client e senza token. Costo in CI: un container Keycloak per circa un minuto, come `OnboardingKeycloakIT` del backend.
- Backend: `KeycloakOtpClientTest` con un server HTTP locale (token, stato, verifica, rinnovo del token, Keycloak irraggiungibile) e i casi dell'app in `SignatureChallengeServiceTest`.

### Frontend

- Finestra di firma: passi, caselle non preselezionate, InputOtp, cambio metodo, errori per chiave; scelta di CieSign solo per l'Affidatario con codice fiscale, sempre con lo stesso elenco di scelte, file obbligatorio fino a 25 MB, caricamento, rifiuto mostrato nella finestra e passaggio al codice; il flusso prosegue senza la finestra del codice.
- Preferenza nel profilo e stato dell'app.
- Pagina pubblica senza inizializzazione del login.
- Presa visione multipla.
- Riquadro **Firme** e sezione **Firme** del foglio: firma passo dopo passo, variazioni firmate insieme, fascicolo, data di eliminazione, cambio del firmatario, dichiarazione su carta.

### Verifica manuale

- Firma reale con CieSign su Android e iOS e con il Software CIE su PC, con una CIE reale.
- Verifica in Acrobat di firma CieSign e sigillo.
- Verifica di un fascicolo scaricato senza Taurus, seguendo le istruzioni dell'attestazione.
- Invio delle e-mail con Mailpit nello sviluppo e con il server SMTP di una casella reale dell'associazione.
- Stampa A4 della nuova versione del modello predefinito, a confronto con il modello dell'associazione.

Il 2026-10-20 i casi SIG-001…035 del [Runbook di test](runbook-test.md#1414-firma-elettronica) sono stati eseguiti su uno stack locale, con le e-mail in Mailpit e le firme CieSign prodotte da una CA di prova configurata come trust anchor; anche il fascicolo è stato verificato senza Taurus. Restano la firma con una CIE reale e la verifica in Acrobat (SIG-031–033), l'invio da una casella reale e la stampa A4.

## Criteri di accettazione

- [x] Con la firma elettronica disattivata il comportamento di fogli e prese visione è invariato.
- [x] Un socio maggiorenne firma l'emissione con codice via e-mail o con l'app, con due conferme distinte per foglio e clausole.
- [x] Un genitore senza account firma dal link con codice via e-mail o con CieSign.
- [x] Ogni variazione e la riconsegna producono un atto firmabile; la riconsegna firmata senza addebiti definisce il foglio.
- [x] Un documento firmato non cambia: download ripetuti restituiscono lo stesso hash e il sigillo resta valido.
- [x] Un modello caricato senza marcatori resta utilizzabile su carta e la pagina del modello spiega cosa manca.
- [x] Un file CieSign con documento diverso, firma non valida o firmatario diverso è rifiutato con la chiave corretta.
- [x] Un codice scaduto, già usato, errato oltre i tentativi o legato a un altro atto è rifiutato.
- [x] La catena delle prove è verificabile e una modifica manuale di una prova viene rilevata.
- [x] Dopo la definizione o l'annullamento il fascicolo resta scaricabile per il periodo di grazia, poi documenti, attestazioni e dati personali delle prove sono eliminati; la cancellazione GDPR li elimina subito.
- [x] Il fascicolo scaricato contiene documenti sigillati e attestazioni verificabili senza Taurus.
- [x] Chi firma per l'Associazione non è mai l'Affidatario e può essere sostituito solo prima della prima firma dell'atto.
- [x] Atti, conferme e link di un tenant non sono accessibili da un altro tenant.
- [x] Nessun codice, token, e-mail o codice fiscale compare nei log.

I criteri sono verificati il 2026-10-20 dai test automatici e dall'esecuzione dei casi SIG-001…035; per CieSign con una CA di prova al posto di quelle della CIE, mentre la prova con una carta reale resta tra le verifiche manuali.

## Piano di implementazione

1. **Fondamenta**: allineamento della versione di Keycloak, sostituzione di BouncyCastle, Mailpit nei servizi di sviluppo, tabelle, catena delle prove, conferme con codice via e-mail, `SignatureMailService`, sigillo e attestazione, presa visione con codice, interruttore del tenant.
2. **Foglio**: marcatori e validatore, nuovo modello predefinito, atti di emissione, variazione e riconsegna, dati dell'Affidatario, firma per l'Associazione e suo cambio, firma su carta del singolo atto, fascicolo, eliminazione dopo il periodo di grazia e con la cancellazione GDPR.
3. **App di autenticazione**: provider `taurus-otp`, client `taurus-signature`, preferenza nel profilo.
4. **Genitore**: link pubblici, registro, pagina `/firma/:token`.
5. **CieSign**: verifica dei file, trust anchor CIE.
6. **Qualificazione**: runbook operativo e di test, verifiche manuali, [Verifica legale](#verifica-legale).

Le fasi 1 e 2 sono implementate (ottobre 2026) e formano la prima consegna, utilizzabile da sola: soci maggiorenni e amministratori firmano con codice via e-mail fogli e prese visione. La fase 3 è implementata il 2026-10-19: chi ha configurato l'app di autenticazione può confermare con il suo codice, scegliendo il metodo preferito nel profilo. La fase 4 è implementata il 2026-10-19: il genitore o tutore senza account firma dal link ricevuto via e-mail e il socio minorenne prende visione del foglio dal proprio account. La fase 5 è implementata il 2026-10-19: l'Affidatario con codice fiscale italiano, socio maggiorenne o genitore, può firmare i documenti con CieSign, nelle installazioni che configurano i certificati delle CA della CIE. La fase 6 è eseguita il 2026-10-20: verifiche manuali su uno stack locale, correzione dei difetti trovati (vedi le decisioni della fase 6) e lettura del testo originale della pronuncia citata. Prima dell'uso in un tenant reale restano la firma con una CIE reale e in Acrobat, l'invio da una casella reale, la stampa A4 e la [Verifica legale](#verifica-legale) da parte di un legale.

## Compatibilità e adozione

- I fogli esistenti hanno `signature_mode = PAPER` e non cambiano.
- La firma elettronica è disattivata per default in ogni tenant.
- Le decisioni di presa visione già registrate restano valide come prese visione autenticate, senza prova di firma.
- I modelli caricati senza marcatori restano utilizzabili per i fogli su carta.
- Rollback: disattivare la firma elettronica del tenant. Gli atti in attesa restano completabili e le date di eliminazione già fissate restano valide; tabelle e documenti non vengono rimossi.

Quando l'implementazione inizia, vanno aggiornati anche:

- [Foglio di consegna e affidamento in comodato](inventory-loan-sheet-spec.md): fuori scope, conservazione e GDPR, modello predefinito, emissione, definizione, annullamento e API;
- [Gestione inventario tenant](inventory-management-spec.md): la frase secondo cui la presa visione non rappresenta una firma elettronica;
- [Gestione centralizzata dei media](media-asset-spec.md): la categoria `signature-acts` e i nuovi riferimenti di `MediaRepository`;
- il catalogo dello [Standard degli errori applicativi](application-errors-standard.md), con le nuove chiavi;
- [Runbook operativo](runbook-operativo.md): SMTP, Mailpit nello sviluppo, keystore del sigillo, versione di Keycloak, client Keycloak, trust anchor CIE, variabili d'ambiente, responsabilità del tenant sulla conservazione dei fascicoli;
- [Runbook di test](runbook-test.md): verifiche manuali di CieSign, e-mail, sigillo e fascicolo.

## Decisioni della revisione

Revisione del 2026-10-18:

- la firma del socio minorenne è richiesta e notificata, ma non blocca il completamento dell'atto;
- se la CRL della CIE non è raggiungibile la firma CieSign è accettata e la prova registra `revocationCheck = UNAVAILABLE`;
- il link del genitore vale 7 giorni;
- il formato PDF/A è fuori scope;
- provider e immagine di Keycloak vengono allineati sulla stessa versione 26.6.x prima di aggiungere il nuovo provider;
- la conservazione degli atti firmati spetta al tenant: Taurus offre il fascicolo e lo elimina dopo un periodo di grazia di 30 giorni dalla definizione o dall'annullamento, subito con la cancellazione GDPR;
- `bcpkix` arriva con il sigillo, nella fase 1;
- nome e codice fiscale dell'Affidatario sono salvati sul foglio fino alla definizione o all'annullamento, per verificare CieSign anche negli atti successivi all'emissione;
- chi firma per l'Associazione non è mai l'Affidatario e può essere sostituito solo prima della prima firma dell'atto;
- la presa visione non ha sigillo né attestazione;
- il server SMTP è unico per l'installazione; nello sviluppo si usa Mailpit;
- il codice fiscale è obbligatorio salvo dichiarazione esplicita di non averne uno italiano, che esclude CieSign.

Decisioni della fase 4, 2026-10-19:

- l'amministratore inserisce all'emissione solo l'e-mail del genitore; nome, cognome e data di nascita li inserisce il genitore nella pagina pubblica, con gli altri dati;
- a firme complete il link di firma è revocato e un nuovo link di 7 giorni, nella conferma di completamento, apre i documenti firmati e l'attestazione; un amministratore può reinviarlo;
- la pagina pubblica può chiedere un nuovo codice, come la finestra del codice dell'applicazione;
- la conferma dal link indica scopo e impronta del documento: l'atto è sempre quello del link;
- un'e-mail del link rifiutata dal server SMTP non annulla l'operazione che l'ha generata: la sezione **Firme** mostra il link non inviato;
- i link scaduti senza firma sono notificati ogni ora, una sola volta per link;
- il socio minorenne prende visione solo dopo l'inserimento dei dati del genitore.

Decisioni della fase 5, 2026-10-19:

- l'Affidatario sceglie tra codice e CieSign nel passo **Documento**, prima che parta un codice; per il genitore CieSign è indicato come consigliato;
- l'aggiornamento incrementale della firma può aggiungere solo il campo della firma e il suo widget: un file che cambia pagine, risorse, annotazioni o azioni è rifiutato anche se la firma è valida, perché mostrerebbe un documento diverso da quello congelato;
- tutti i certificati configurati, radice e intermedi, sono trust anchor; il certificato deve essere valido alla data di caricamento, mentre la data di firma dichiarata nel file non è considerata;
- la CRL della CIE può essere grande: si scarica al massimo 20 MB in 10 secondi e si tiene in memoria fino al suo aggiornamento; oltre, la firma è accettata con `revocationCheck = UNAVAILABLE`;
- il confronto del nome ignora anche apostrofi, trattini e l'ordine tra nome e cognome, perché il nome del genitore è salvato come unica stringa;
- senza trust anchor l'installazione non offre CieSign, con la nuova chiave `signature.cieSign.notConfigured`.

Decisioni della fase 6, 2026-10-20:

- la cancellazione GDPR di un socio senza materiale in carico elimina subito fogli, documenti, attestazioni e dati personali delle prove: `DataErasureService` pubblica `UserErasureCompleted` e `InventoryErasureService` esegue la stessa pulizia del completamento di una pratica, mentre prima restavano fino al termine del periodo di grazia. È corretto anche l'errore che faceva fallire ogni cancellazione GDPR di un utente del tenant, perché ruoli e strumenti venivano sostituiti con raccolte immutabili;
- il documento scaricato e ricaricato senza firma risponde `signature.cieSign.invalidSignature`, non `documentMismatch`: il file è quello giusto, manca la firma;
- un file CieSign vuoto o oltre 25 MB è contato nella metrica e registrato nel log come gli altri rifiuti;
- la dichiarazione della presa visione del minore usa la preposizione articolata («Ho preso visione del foglio…», «della variazione…»);
- le scelte «Come firmi» della finestra di firma sono create una sola volta: ricreate a ogni controllo delle modifiche, bloccavano la finestra nella pagina pubblica;
- la richiesta di conferma non riporta il codice nella propria rappresentazione testuale, che i log di debug del profilo di sviluppo stampano;
- i testi dell'interfaccia che citavano solo il codice via e-mail indicano anche l'app di autenticazione e CieSign.

## Verifica legale

Non blocca lo sviluppo, perché la firma elettronica è disattivata per default, ma va completata prima di attivarla in un tenant reale:

1. idoneità della firma elettronica semplice con codice per l'approvazione specifica delle clausole vessatorie, verificando sul testo originale le pronunce citate;
2. testo delle dichiarazioni di accettazione, approvazione delle clausole, presa visione e firma su carta;
3. testo della lettera f) dell'informativa del modello predefinito sulla conservazione degli atti firmati da parte dell'Associazione.

Esito della ricerca del 2026-10-20, da confermare da parte di un legale:

- punto 1: Cass. civ., sez. III, ord. 20 giugno 2026, n. 20945 (Pres. Frasca, Rel. Saija), letta sul testo pubblicato da Diritto Bancario, conferma l'impostazione: per i contratti non soggetti alla forma dell'art. 1350 c.c. basta una firma elettronica semplice, di regola un codice OTP via SMS o e-mail, purché l'approvazione sia specifica e raccolta in un passaggio dedicato, e la sola spunta non basta. Il caso riguardava però un contratto telematico tra professionisti (art. 13 d.lgs. 70/2003): va valutata l'applicazione al comodato tra associazione e socio o genitore, anche rispetto al Codice del consumo. Cass. n. 9413/2021 è richiamata solo per la nozione di firma elettronica, in un altro ambito contrattuale;
- punti 2 e 3: da verificare.
