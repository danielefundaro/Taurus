# Aspetto unificato delle e-mail

Il documento fissa l'aspetto comune di tutte le e-mail di Taurus: quelle inviate dal backend e quelle inviate da Keycloak. Oggi le prime sono solo testo, le seconde sono HTML con logo e colori di Taurus. Dopo l'adozione ogni e-mail ha la stessa struttura, gli stessi colori e lo stesso logo, e porta sempre anche una versione solo testo.

Riguarda le e-mail della [Firma elettronica dei fogli di affidamento](inventory-electronic-signature-spec.md) e i modelli del tema Keycloak `taurus-theme`. I colori seguono la regola di contrasto del tema di login (`login/resources/css/login.css`); le operazioni e la configurazione SMTP sono nel [Runbook operativo](runbook-operativo.md).

## Stato del documento

ID catalogo: `email-layout-standard`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

Bozza del 2026-11-02, approvata e implementata il 2026-11-03. Le scelte sono fissate in [Decisioni](#decisioni).

## Problema

- Il backend invia tre e-mail della firma elettronica con `SimpleMailMessage`: solo testo, senza logo né colori, con il mittente senza nome. Keycloak invia e-mail HTML con barra verde, logo, pulsante e piè di pagina. Chi riceve le une e le altre non le riconosce come dello stesso servizio.
- Anche le e-mail di Keycloak hanno difetti:
  - il logo è un SVG nel corpo del messaggio, che Gmail e Outlook non mostrano;
  - pulsante e link usano `#22c55e`, che con il testo bianco ha un contrasto di 2,3:1; note e piè di pagina usano `#94a3b8` (2,6:1) e `#cbd5e1` (1,5:1). Il tema di login riserva già `#22c55e` alla decorazione e usa `#15803d` per testo e pulsanti;
  - mancano i modelli solo testo: la parte testuale viene dal tema base di Keycloak, con parole diverse da quelle dell'HTML;
  - gli oggetti vengono dai messaggi del tema base. Con l'internazionalizzazione del realm disattivata Keycloak usa l'inglese, quindi oggetti come «Reset password» accompagnano un corpo in italiano;
  - la scadenza è scritta come «720 minuti» invece di «12 ore»;
  - il piè di pagina dell'e-mail delle azioni richieste dice sempre «è stato creato un account», anche quando chiede solo di verificare l'e-mail o di configurare l'app OTP;
  - le e-mail di Keycloak senza modello Taurus (per esempio la prova SMTP della console) hanno ancora l'aspetto di Keycloak.

## Obiettivo

- Un solo aspetto per tutte le e-mail, descritto in [Struttura](#struttura) e [Colori e caratteri](#colori-e-caratteri), realizzato da un solo modello di impaginazione per modulo.
- Ogni e-mail è `multipart/alternative`, con una parte HTML e una parte solo testo che riportano le stesse informazioni.
- Il logo si vede nei principali programmi di posta (Gmail, Outlook, Apple Mail, Thunderbird).
- Testi, pulsanti e note rispettano il contrasto minimo di 4,5:1.
- Oggetti, contenuti, dati e regole di sicurezza delle e-mail della firma restano quelli di oggi.

## Fuori scope

- Logo o colori del tenant: Taurus usa un solo marchio. Il nome dell'associazione resta nel testo, come oggi.
- E-mail in altre lingue: sono tutte in italiano.
- Un aspetto dedicato alla modalità scura: si dichiara solo lo schema chiaro (vedi [Struttura](#struttura)).
- Nuovi tipi di e-mail, pixel di tracciamento, modelli modificabili dai tenant, server SMTP per tenant.
- L'invio SMTP di Keycloak: resta quello di Keycloak, cambiano solo i modelli del tema.
- La console account e la console di amministrazione di Keycloak.

## Contesto attuale

| E-mail | Modulo | Origine | Formato |
| --- | --- | --- | --- |
| Codice di conferma | backend | `SignatureMailService.sendCode` | testo |
| Firma richiesta (link al genitore) | backend | `SignatureMailService.sendLink`, `signed = false` | testo |
| Atto firmato (link ai documenti) | backend | `SignatureMailService.sendLink`, `signed = true` | testo |
| Reimpostazione della password | Keycloak | `email/html/password-reset.ftl` | HTML; testo del tema base |
| Azioni richieste (benvenuto, verifica e-mail, OTP) | Keycloak | `email/html/executeActions.ftl`, inviata anche da `PUT /api/users/{id}/send-setup-email` | HTML; testo del tema base |
| Altre e-mail di Keycloak | Keycloak | tema base `keycloak` | HTML e testo di Keycloak |

- Il backend ha già `spring-boot-starter-thymeleaf` (usato da `templates/error.html`) e `spring-boot-starter-mail`. I PDF usano Mustache tramite `LoanSheetPdfRenderer`, un ambiente chiuso pensato per i modelli scritti dai tenant, che rifiuta i modelli inclusi.
- Il tema `taurus-theme/email` ha `parent=keycloak`: ogni modello che manca viene dal tema base, che impagina il corpo con la macro `emailLayout` di `html/template.ftl`.
- Il realm dichiara `fromDisplayName: Taurus`; il backend invia da `jhipster.mail.from` senza nome.
- Il tema di login ha un `logo.svg`; il frontend ha un `logo.png` di 1024 × 1024 px e 1,5 MB, troppo pesante per una e-mail.
- `EmailTemplatesTest` del modulo Keycloak rende i due modelli HTML con la configurazione FreeMarker di Keycloak e controlla link, scadenza, entità e variabili.

## Decisioni

### 1. Un modello di impaginazione per modulo

- **Backend**: un layout Thymeleaf `templates/mail/html/layout.html`, usato da ogni e-mail con `th:replace`, le parti del contenuto in `templates/mail/html/parts.html` e un modello solo testo per e-mail in `templates/mail/text/`.
- **Keycloak**: `email/html/template.ftl` del tema ridefinisce la macro `emailLayout`. I modelli Taurus la usano con i loro parametri; i modelli del tema base, che la chiamano senza parametri, ricevono lo stesso aspetto senza modifiche.
- I due layout producono lo stesso HTML a parità di contenuto: stessa struttura, stessi stili in linea, stessi testi fissi. Un cambio d'aspetto si fa in entrambi nella stessa modifica.

Thymeleaf e non Mustache: è già una dipendenza, sfugge i valori per default, compone il layout con i frammenti e ha una modalità testo per la parte alternativa. Il renderer Mustache dei PDF resta dedicato ai modelli dei tenant.

### 2. Logo in PNG

- Un `logo.png` di 144 × 144 px, sotto i 10 KB, ricavato da `login/resources/img/logo.svg` su sfondo trasparente e mostrato a 72 × 72 px. Lo stesso file, con lo stesso SHA-256, sta in:
  - `taurus-be/src/main/resources/templates/mail/logo.png`;
  - `keycloak-authenticator/src/main/resources/theme/taurus-theme/email/resources/img/logo.png`.
- **Backend**: il logo è allegato in linea (`Content-ID: taurus-logo`, `multipart/related`) e citato con `cid:taurus-logo`. Si vede anche quando il programma di posta blocca le immagini remote e non rivela a nessun server l'apertura del messaggio.
- **Keycloak**: cita `${url.resourcesUrl}/img/logo.png`, servito da Keycloak come le risorse del tema di login. Basta il modello: l'invio resta quello di Keycloak. Gmail e Apple Mail mostrano il logo; Outlook e Thunderbird, se bloccano le immagini remote, mostrano il testo alternativo finché il destinatario non le sblocca.
- Allegare il logo anche nelle e-mail di Keycloak richiederebbe di riscrivere nel provider l'invio SMTP di Keycloak, perché la composizione del messaggio in `DefaultEmailSenderProvider` è privata. È escluso: il codice SMTP, TLS e di autenticazione andrebbe riallineato a ogni aggiornamento di Keycloak per una differenza visibile solo con le immagini bloccate.
- In entrambi `alt="Taurus"` e, sotto il logo, la scritta «Taurus» in testo: l'intestazione resta riconoscibile anche senza immagine.
- Il logo non è un link, in nessuna e-mail.

### 3. Mittente

Il backend invia con il nome «Taurus» (`MimeMessageHelper.setFrom(jhipster.mail.from, "Taurus")`), come `fromDisplayName` del realm. L'indirizzo resta quello di `jhipster.mail.from`.

### 4. Parte solo testo

- Ogni e-mail ha la parte testo con le stesse informazioni e lo stesso ordine dell'HTML: titolo, saluto, testo, dati, link o codice, note, piè di pagina.
- Backend: la parte testo è quella di oggi, con titolo in testa e piè di pagina in fondo.
- Keycloak: `email/text/password-reset.ftl` e `email/text/executeActions.ftl` con le parole dei modelli HTML.

### 5. Oggetti

- Backend: invariati («Codice di conferma – {tenant}», «Firma richiesta – {tenant}», «Atto firmato – {tenant}»).
- Keycloak: l'italiano diventa la lingua del realm (`internationalizationEnabled: true`, `supportedLocales: ["it"]`, `defaultLocale: "it"`). Con l'internazionalizzazione disattivata Keycloak sceglie l'inglese; con una sola lingua il selettore non compare.
- `email/messages/messages_it.properties` del tema fissa gli oggetti:
  - `passwordResetSubject=Reimposta la password`;
  - `executeActionsSubject=Completa il tuo account Taurus`;
  - `emailTestSubject=Prova di invio da Taurus`.
- Effetti sulle altre pagine di Keycloak: la pagina di login usa `login/messages/messages_it.properties` invece di quello inglese, che oggi mostra per esempio «Welcome to Taurus!»; la console account e i messaggi del tema base passano all'italiano. I due file del login hanno già le stesse chiavi.

### 6. E-mail di Keycloak con contenuto Taurus

- Hanno testi Taurus in italiano: reimpostazione della password, azioni richieste e prova SMTP (`email-test.ftl`, inviata dal pulsante «Test connection» della console).
- Le altre (verifica dell'indirizzo, eventi di sicurezza, collegamento a provider esterni, inviti alle organizzazioni) non sono previste dalla configurazione del realm: ricevono il layout Taurus tramite la macro e mantengono i testi del tema base, in italiano con la lingua del realm.
- La scadenza si scrive con `linkExpirationFormatter(linkExpiration)`, per esempio «12 ore».
- Il piè di pagina delle azioni richieste dipende dall'azione: «perché è stato creato un account con questo indirizzo» solo per `UPDATE_PASSWORD`, «perché è stata chiesta un'azione sul tuo account Taurus» negli altri casi.

## Struttura

Impaginazione a tabelle con stili in linea, larghezza massima 540 px, senza fogli di stile nella `<head>` né script. Dall'alto:

| Blocco | Contenuto | Obbligatorio |
| --- | --- | --- |
| Anteprima | testo nascosto mostrato nell'elenco dei messaggi, mai un codice o un link | sì |
| Barra | striscia verde di 5 px, angoli superiori arrotondati | sì |
| Intestazione | logo 72 × 72 px e scritta «Taurus», centrati | sì |
| Titolo | `<h1>` con il tema dell'e-mail | sì |
| Saluto | «Ciao {nome},» oppure «Ciao,» senza nome | sì |
| Testo | uno o due paragrafi | sì |
| Dati | tabella etichetta–valore (associazione, atto, socio, riferimento) | no |
| Azione | pulsante con un link, oppure riquadro del codice; il riquadro del codice viene prima dei dati, subito dopo il testo | no |
| Note | scadenza, avvertenze di sicurezza, separate da una linea | sì |
| Link di riserva | «Se il pulsante non funziona, copia questo link nel browser:» e l'indirizzo | solo con il pulsante |
| Piè di pagina | «Taurus» e il motivo per cui si riceve l'e-mail | sì |

- `<html lang="it">`, `charset UTF-8`, `viewport`, `<meta name="color-scheme" content="light">` e `<meta name="supported-color-schemes" content="light">`.
- Un'e-mail con il codice non ha pulsante, link di riserva né altri link, come richiede la [firma elettronica](inventory-electronic-signature-spec.md#codice-via-e-mail).
- Il riquadro del codice mostra le sei cifre in carattere monospaziato a 28 px, con spaziatura tra le cifre, così il codice si legge e si copia senza errori.

## Colori e caratteri

| Uso | Valore | Contrasto |
| --- | --- | --- |
| Sfondo della pagina | `#f1f5f9` | — |
| Scheda | `#ffffff`, bordo `#e2e8f0`, raggio 12 px | — |
| Barra e logo (decorazione) | `#22c55e` | nessun testo sopra |
| Pulsante | sfondo `#15803d`, testo `#ffffff`, raggio 8 px | 5,0:1 |
| Link | `#15803d` | 5,0:1 |
| Titolo e scritta «Taurus» | `#0f172a` | 17,9:1 |
| Testo e dati | `#475569` | 7,6:1 |
| Etichette dei dati, note, link di riserva, piè di pagina | `#64748b` | 4,8:1 |
| Riquadro del codice | sfondo `#f0fdf4`, bordo `#bbf7d0`, cifre `#14532d` | oltre 7:1 |
| Separatori | `#e2e8f0` | — |

- Caratteri: `-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif`; codice: `ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace`.
- Dimensioni: titolo 19 px semibold, testo 15 px con interlinea 1,6, note 13 px, piè di pagina 12 px. Nessun testo sotto i 12 px.

## Contenuto delle e-mail del backend

Testi, dati e regole restano quelli di [`SignatureMailService`](../taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/signature/SignatureMailService.java); cambia solo la disposizione.

| | Codice di conferma | Firma richiesta | Atto firmato |
| --- | --- | --- | --- |
| Anteprima | «Il codice scade alle {ora}.» | «{tenant} ti chiede di firmare un atto.» | «I documenti firmati sono pronti.» |
| Titolo | Codice di conferma | Firma richiesta | Atto firmato |
| Testo | «Usa questo codice per confermare la firma.» | «L'associazione ti chiede di firmare elettronicamente…» (testo attuale) | «Tutte le parti hanno firmato l'atto.» |
| Dati | Associazione, Operazione, Riferimento del documento | Associazione, Atto, Socio minorenne | Associazione, Atto, Socio minorenne |
| Azione | riquadro del codice | pulsante «Leggi e firma» | pulsante «Scarica i documenti» |
| Note | scadenza e uso singolo; «Se non hai chiesto tu questa conferma…» | scadenza; codice in arrivo; «Non inoltrare il messaggio…» | scadenza; «conserva una copia dei documenti…» |
| Motivo nel piè di pagina | «Hai ricevuto questa e-mail perché è stata chiesta una firma con questo indirizzo.» | «Hai ricevuto questa e-mail perché {tenant} ti ha indicato come genitore o tutore di un socio minorenne.» | come «Firma richiesta» |

Il saluto è «Ciao,»: il genitore non ha un nome nel modello del messaggio e il codice non lo richiede.

## Sicurezza e privacy

- Tutti i valori sono sfuggiti (`th:text` nel backend, output HTML di FreeMarker in Keycloak): nome del tenant, nome del socio e riferimenti non possono inserire markup. Nessun `th:utext` sui dati.
- L'anteprima non contiene codici né link, perché compare nelle notifiche del telefono anche a schermo bloccato. Il codice non compare nell'oggetto, come oggi.
- Il backend non carica immagini remote; Keycloak carica solo il logo dal proprio indirizzo pubblico.
- Restano le regole della firma: nessun codice, link o indirizzo nei log; `LinkMessage` e `CodeMessage` non stampano dati personali.

## Modifiche per modulo

### Backend (`taurus-be`)

| Elemento | Modifica |
| --- | --- |
| `TaurusMailSender` (`service/mail`) | ha due motori Thymeleaf dedicati alle e-mail, separati da quello delle pagine web: HTML per `templates/mail/html/` e testo per `templates/mail/text/`, UTF-8, con cache; rende HTML e testo di un modello con il suo modello dati, compone il `MimeMessage` (`multipart/alternative` dentro `multipart/related` con il logo), imposta mittente e nome, invia con `JavaMailSender`; lancia `MailException` come oggi |
| `SignatureMailService` | `sendCode` e `sendLink` costruiscono il modello dati e chiamano `TaurusMailSender`; metriche, eccezioni, `configured()` e oggetti invariati; `text(...)` lascia il posto ai modelli |
| `templates/mail/html/layout.html` | layout comune |
| `templates/mail/html/parts.html` | saluto, paragrafo, dati, codice, pulsante, note e link di riserva: le stesse macro di `template.ftl` |
| `templates/mail/html/signature-code.html`, `signature-link.html`, `signature-signed.html` | contenuto delle tre e-mail |
| `templates/mail/text/…` | le tre parti testo, con lo stesso nome |
| `templates/mail/logo.png` | logo |

Nessuna migrazione e nessuna nuova proprietà di configurazione.

### Keycloak (`keycloak-authenticator`)

| Elemento | Modifica |
| --- | --- |
| `email/html/template.ftl` | macro `emailLayout` con parametri facoltativi (`title`, `preheader`, `reason`), usabile anche senza parametri dai modelli del tema base |
| `email/html/password-reset.ftl`, `executeActions.ftl` | usano la macro; logo PNG; colori della tabella; scadenza formattata; motivo per azione |
| `email/html/email-test.ftl`, `email/text/email-test.ftl` | prova SMTP in italiano |
| `email/text/password-reset.ftl`, `executeActions.ftl` | parti testo |
| `email/messages/messages_it.properties` | oggetti |
| `email/resources/img/logo.png` | logo |

### Realm

- `taurus-be/src/main/docker/realm-config/taurus-realm.json`: `internationalizationEnabled: true`, `supportedLocales: ["it"]`, `defaultLocale: "it"`.
- `--import-realm` non sovrascrive un realm esistente: nelle installazioni già avviate la lingua si imposta a mano da **Realm settings › Localization** (internazionalizzazione attiva, solo italiano, italiano predefinito). Il runbook lo riporta come le altre impostazioni del realm.

### Documenti da allineare

- [Firma elettronica](inventory-electronic-signature-spec.md#codice-via-e-mail): il messaggio segue questo standard.
- [Runbook operativo](runbook-operativo.md): lingua del realm da impostare a mano nei realm esistenti, accanto alla configurazione SMTP di Keycloak.
- [Runbook di test](runbook-test.md): verifica visiva delle e-mail in Mailpit (vedi [Strategia di test](#strategia-di-test)).
- [Catalogo funzionalità](features.md): moduli, evidenze e stato.

## Strategia di test

### Backend

- `TaurusMailSenderTest` con un `JavaMailSender` finto che cattura il `MimeMessage`:
  - struttura `multipart/related` con `multipart/alternative` (testo e HTML) e il logo con `Content-ID: taurus-logo`;
  - mittente con nome «Taurus»;
  - un valore con `<script>` o `&` esce sfuggito nell'HTML e invariato nel testo.
- `SignatureMailServiceTest`:
  - per ogni e-mail oggetto invariato, dati, scadenza nel fuso del tenant e motivo nel piè di pagina, sia nell'HTML sia nel testo;
  - l'e-mail del codice non contiene `<a ` né `http` nell'HTML e nel testo, e l'anteprima non contiene il codice;
  - le e-mail dei link contengono l'indirizzo nel pulsante e nel link di riserva;
  - metriche ed eccezioni come oggi, anche senza server SMTP.
- `TaurusMailSenderTest` verifica anche lo SHA-256 del logo, lo stesso del test Keycloak.

### Keycloak

`EmailTemplatesTest`, con la configurazione FreeMarker di Keycloak e un attributo `url.resourcesUrl` finto:

- i due modelli HTML e i loro modelli testo si rendono per ogni ramo delle azioni richieste, senza entità sfuggite due volte né variabili rimaste;
- il logo è un `<img>` verso `…/img/logo.png` e non c'è più `<svg`;
- la scadenza è formattata («12 ore» per 720 minuti);
- un modello che chiama `<@layout.emailLayout>` senza parametri, come quelli del tema base, si rende con barra, logo e piè di pagina;
- il file dei messaggi email definisce i tre oggetti;
- i due file dei messaggi del login hanno le stesse chiavi;
- il logo ha lo SHA-256 atteso.

`EmailDeliveryIT` avvia Keycloak 26.8.2 con il JAR del provider e Mailpit, chiede l'e-mail di configurazione dell'account dall'API di amministrazione e la legge da Mailpit: oggetto italiano, mittente «Taurus», parte testo, e logo citato con `url.resourcesUrl` che Keycloak serve con `200` e `image/png`. Se un aggiornamento di Keycloak togliesse l'attributo, il modello non mostrerebbe il logo ma solo la scritta «Taurus», e il test fallirebbe.

### Verifica manuale

In Mailpit (`http://localhost:8025`), dopo aver inviato codice, link, atto firmato, reimpostazione della password, configurazione dell'account e prova SMTP:

- le sei e-mail appaiono uguali nella struttura, nei colori e nel logo;
- la scheda **Text** mostra la parte testo e la scheda **HTML Check** di Mailpit non segnala problemi bloccanti per Gmail e Outlook;
- con le immagini remote bloccate il logo del backend resta, quello di Keycloak mostra «Taurus».

## Criteri di accettazione

- Le e-mail del backend e quelle di Keycloak hanno la stessa intestazione, gli stessi colori, lo stesso pulsante e lo stesso piè di pagina.
- Ogni e-mail ha una parte HTML e una parte testo con le stesse informazioni.
- Il logo è un PNG e si vede in Gmail e Outlook; nessuna e-mail contiene SVG.
- Nessun testo ha contrasto sotto 4,5:1 sul suo sfondo.
- Le e-mail della firma conservano oggetti, dati e regole di sicurezza; quella del codice non contiene link.
- Gli oggetti delle e-mail di Keycloak sono in italiano e la scadenza è scritta in ore o minuti secondo la durata.
- Il realm ha l'italiano come unica lingua e la pagina di login mostra i testi italiani del tema.
- I test di backend e Keycloak passano con `./mvnw verify` nei due moduli.

## Piano di adozione e compatibilità

1. Logo PNG nei due moduli e relativi test.
2. Keycloak: macro di layout, modelli HTML e testo, messaggi del tema, test. Si rilascia con il JAR del provider.
3. Realm: lingua italiana nel realm versionato e, a mano, nei realm esistenti, insieme al JAR del passo 2.
4. Backend: motore dei modelli, `TaurusMailSender`, modelli della firma, test.
5. Verifica manuale in Mailpit e aggiornamento dei documenti.

Il passo 4 è indipendente dai passi 2 e 3. Nessuna API, migrazione o variabile di deployment cambia; i link già inviati restano validi. L'unico cambio di configurazione è la lingua del realm.
