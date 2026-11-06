# Documenti legali nell'applicativo

Il documento progetta la pubblicazione dei **termini di utilizzo** e dell'**informativa privacy** dentro Taurus. Il testo è salvato nel database e scritto dal super amministratore in **Documenti legali**. Ogni versione diventa immutabile quando viene pubblicata, e chiunque può leggerla su una pagina pubblica dell'applicativo, anche senza login. Le pagine `termini-applicativo` e `privacy-applicativo` del sito informativo rimandano a quelle dell'applicativo.

La pagina pubblica senza login riprende lo schema della pagina di firma descritta nella [Firma elettronica dei fogli di affidamento](inventory-electronic-signature-spec.md). Gli errori seguono lo [Standard degli errori applicativi](application-errors-standard.md), l'interfaccia il [Taurus Layout Standard](taurus-layout-standard.md) e le tappe nuove la [Guida introduttiva per ruolo](guided-tour-spec.md).

## Stato del documento

ID catalogo: `legal-documents`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

Le scelte sono fissate in [Decisioni prese](#decisioni-prese).

## Problema

- Taurus non conserva il testo dei documenti, solo un URL scritto a mano. Il testo vive nel sito informativo: per pubblicare una nuova versione servono un rilascio del sito e un record creato a parte, coordinati a mano.
- L'URL non è versionato. `/termini-applicativo/` mostra sempre l'ultimo testo: quando esce la 1.3, il record della 1.2 punta al testo della 1.3. Non resta prova di cosa l'utente ha accettato.
- Nessun controllo lega l'URL alla versione e alla data dichiarate nel record.
- Per leggere i documenti la pagina di accettazione manda l'utente su un altro sito, in una nuova scheda.
- Dentro l'applicativo un utente non trova più i documenti dopo averli accettati.

## Obiettivo

- Il super amministratore scrive il testo in **Documenti legali**, con anteprima, e lo pubblica senza rilasci.
- Dalla pubblicazione ogni versione è immutabile. Il suo testo ha un'impronta SHA-256, registrata anche in ogni accettazione.
- Pagine pubbliche senza login: `/legal/terms` e `/legal/privacy` per la versione in vigore, `/legal/terms/<versione>` e `/legal/privacy/<versione>` come indirizzi permanenti di ogni versione pubblicata, con l'elenco delle versioni precedenti.
- Nella pagina di accettazione l'utente legge il testo senza uscire dall'applicativo e vede cosa è cambiato rispetto alla versione che aveva confermato.
- Il piè di pagina dell'applicativo collega i due documenti.
- Il sito informativo rimanda alle pagine dell'applicativo.

## Fuori scope

- Più lingue: i documenti restano in italiano.
- Pubblicazione programmata a una data futura.
- Confronto automatico tra due versioni.
- Documenti propri di un tenant, come il regolamento dell'associazione: i documenti restano della piattaforma.
- Tipi di documento oltre `TERMS` e `PRIVACY`.
- PDF scaricabile: la pagina pubblica si stampa dal browser.
- Collegamenti ai documenti nella pagina di login di Keycloak e nelle e-mail.
- Avviso in dashboard al super amministratore quando manca una versione in vigore.
- Le pagine `privacy` e `termini` del sito informativo: riguardano il sito stesso e restano lì.
- L'informativa della sezione 10 del foglio di affidamento (`loan-sheet.html`), che è dell'associazione.
- Portare in inglese il percorso `/firma/` della pagina pubblica di firma, l'unico oggi in italiano.

## Contesto attuale

- `public.legal_document` sta nello schema pubblico, condiviso fra i tenant. Campi: tipo (`TERMS`, `PRIVACY`), versione, titolo, URL obbligatorio, modalità di conferma (`ACCEPT`, `ACKNOWLEDGE`), data di pubblicazione scelta a mano, attivo, obbligatorio, campi di audit con il trigger `taurus_touch_audit`.
  - Vincoli: tipo e versione univoci (`uq_legal_document_type_version`) e una sola versione attiva per tipo (`uq_legal_document_active_type`).
- `user_legal_acceptance` sta nello schema di ogni tenant e punta a `public.legal_document` con `ON DELETE RESTRICT`. Un utente conferma una versione una volta sola (`uq_user_legal_document`). `DataErasureService` cancella le accettazioni con l'utente.
- `LegalServiceImpl`:
  - attivare una versione disattiva le altre dello stesso tipo (`activateExclusively`);
  - un documento già accettato da almeno un utente non si modifica (`legal.document.accepted.immutable`), tranne lo stato attivo;
  - `accept` registra solo i documenti attivi e chiede tutti quelli obbligatori ancora da confermare.
- API: `GET /api/legal/status` e `POST /api/legal/acceptances` per ogni utente autenticato. `/api/legal/documents/**` solo per il super amministratore. Il resto di `/api/legal/**` è negato.
- Frontend:
  - `legalDocumentsGuard` protegge tutte le pagine del layout e porta a `/legal/accept` finché mancano conferme;
  - `legal-acceptance` mostra per ogni documento il link «Apri il documento» verso l'URL;
  - `legal-documents` elenca le versioni e le modifica nel dialogo `legal-document-dialog`, con il campo «URL pubblico del documento»;
  - il piè di pagina del layout contiene solo la scritta «TAURUS».
- L'applicativo parte con `onLoad: 'login-required'`. Fa eccezione la pagina pubblica di firma `/firma/:token`:
  - `publicSignaturePage()` evita l'avvio di Keycloak in `app.config.ts` e la lettura dei dati del tenant in `app.component.ts`;
  - l'interceptor HTTP manda le chiamate a `PUBLIC_SIGNATURE_API` senza token e senza logout.
- Il backend usa già jsoup 1.23.2 per i modelli del foglio di affidamento. Il frontend non ha librerie Markdown.
- Il sito informativo pubblica `termini-applicativo` (1.2 del 7 settembre 2026) e `privacy-applicativo` (1.1 del 3 settembre 2026). Li collegano il piè di pagina, `termini.astro`, `privacy.astro` e la sezione «Documenti legali dell'applicativo» del README.

## Decisioni prese

Le scelte discusse e fissate il 06/11/2026.

### 1. Testo in Markdown, convertito in HTML dal backend

Il testo si scrive in Markdown (CommonMark) con le tabelle.

| Ammesso | Escluso |
| --- | --- |
| Titoli, paragrafi, grassetto, corsivo, elenchi puntati e numerati, citazioni, linee di separazione, tabelle, codice in linea | HTML scritto a mano, che viene mostrato come testo |
| Link `https:` e `mailto:` | Altri protocolli, compreso `javascript:`: il link viene tolto e resta il testo |
| | Immagini e ogni risorsa esterna |

Il backend converte il testo con `org.commonmark:commonmark` e `commonmark-ext-gfm-tables` (licenza BSD-2-Clause), con `escapeHtml(true)` e `sanitizeUrls(true)`. Poi jsoup ripulisce il risultato con una `Safelist` propria:
- elementi `h2`, `h3`, `h4`, `p`, `br`, `hr`, `strong`, `em`, `ul`, `ol`, `li`, `blockquote`, `code`, `table`, `thead`, `tbody`, `tr`, `th`, `td`, `a`;
- `href` solo con `https` e `mailto`, `id` solo sui titoli.

I titoli si adattano alla pagina, dove il titolo del documento è l'unico `h1`. Il livello più alto usato nel testo diventa `h2`, i successivi `h3` e `h4`, e i livelli oltre il quarto restano `h4`. Ogni titolo riceve un `id` ricavato dal testo, così una sezione si può collegare (`/legal/terms#5-utilizzi-vietati`). I link esterni ricevono `rel="noopener noreferrer"` e `target="_blank"`.

Un unico convertitore serve l'anteprima, la pagina di accettazione e la pagina pubblica: il super amministratore vede quello che vedranno gli utenti. L'HTML non si salva, si ricava a ogni lettura in pochi millisecondi. Un aggiornamento della libreria può cambiare l'aspetto, non le parole: il testo che vale è il Markdown, ed è quello che l'impronta certifica.

Un editor visuale come quello di PrimeNG (Quill) produrrebbe HTML da ripulire, aggiungerebbe una dipendenza al frontend e darebbe un testo meno leggibile da confrontare tra versioni.

### 2. Bozza, in vigore, ritirata

| Stato | Condizione | Cosa si può fare | Chi la vede |
| --- | --- | --- | --- |
| Bozza | `published_at` vuoto | modificare tutto, eliminare, pubblicare | solo il super amministratore |
| In vigore | `active = true` | ritirare | tutti, anche senza login |
| Ritirata | `published_at` valorizzato, `active = false` | riattivare | tutti, come versione precedente |

- **Pubblicare** è la prima attivazione. Il server scrive `published_at` con l'istante della pubblicazione, che la pagina mostra come «in vigore dal». Il super amministratore non sceglie più la data a mano.
- Pubblicare una versione ritira quella in vigore dello stesso tipo, come oggi.
- Dalla pubblicazione non cambiano più tipo, versione, titolo, testo, sintesi delle modifiche, modalità di conferma e obbligatorietà. Cambia solo lo stato. La regola passa da «accettato da almeno un utente» a «pubblicato»: un indirizzo permanente non deve cambiare contenuto anche se nessuno l'ha ancora confermato, e chi legge la pagina pubblica prima del login non lascia traccia.
- **Riattivare** una versione ritirata è permesso, per tornare indietro. Chi l'aveva già confermata resta in regola; chi aveva confermato solo la versione ritirata nel frattempo deve confermare di nuovo.
- **Ritirare** senza pubblicare un'altra versione è permesso, con un avviso: nessuno deve più confermare quel documento e la sua pagina pubblica risponde «Documento non disponibile».
- Si elimina solo una bozza.

### 3. Versione e impronta

- **Versione**: da 1 a 32 caratteri tra lettere, cifre, punto, trattino e trattino basso, iniziando con una lettera o una cifra (`1.3`, `2026-1`). Così è sicura dentro un indirizzo. Resta univoca per tipo.
- L'ordine delle versioni segue `published_at`, non il testo della versione.
- **Testo**: prima del salvataggio i ritorni a capo diventano `\n` e si tolgono gli spazi in fondo al testo. Il testo non può essere vuoto e non supera 100 000 caratteri; le pagine attuali ne contano circa 6 000.
- **Impronta** (`content_sha256`): SHA-256 in esadecimale del testo normalizzato in UTF-8, calcolata a ogni salvataggio della bozza. La pagina pubblica la mostra intera; l'elenco di **Documenti legali** la mostra abbreviata con `shortHash`.
- **Accettazione**: `user_legal_acceptance` registra l'impronta del testo confermato. La prova resta completa nello schema del tenant, anche senza leggere lo schema pubblico.

### 4. Sintesi delle modifiche

Campo facoltativo di testo semplice, al massimo 1 000 caratteri. Dice in poche righe cosa cambia rispetto alla versione precedente.

- Nella pagina di accettazione compare sopra il testo, con il titolo «Cosa cambia», solo per chi aveva confermato un'altra versione dello stesso documento. A chi entra per la prima volta non serve.
- Nella pagina pubblica compare sotto il titolo.

### 5. Pagine pubbliche nell'applicativo

Quattro percorsi fuori dal layout, senza guardie e senza login:

| Percorso | Contenuto |
| --- | --- |
| `/legal/terms` | termini di utilizzo in vigore |
| `/legal/terms/:version` | una versione pubblicata dei termini |
| `/legal/privacy` | informativa privacy in vigore |
| `/legal/privacy/:version` | una versione pubblicata dell'informativa |

I percorsi sono in inglese come gli altri dell'applicativo e stanno sotto `/legal/`, accanto a `/legal/accept`. `publicSignaturePage()` diventa `publicPage()`, con l'elenco dei percorsi pubblici in una costante. Un percorso è pubblico quando coincide con `/legal/terms` o `/legal/privacy` o inizia con `/legal/terms/`, `/legal/privacy/` o `/firma/`. Non lo sono `/legal/accept`, che richiede il login, né `/legal/termsX`. Per i percorsi pubblici l'applicativo non avvia Keycloak e non legge i dati del tenant.

La pagina contiene:
- in alto il marchio Taurus, senza menu;
- il titolo come `h1` e la riga «Versione 1.3 · in vigore dal 12 novembre 2026»;
- per una versione ritirata, un avviso: «Questa versione non è più in vigore» con il link alla versione in vigore;
- la sintesi delle modifiche, se c'è, e il testo, in una colonna di lettura di circa 48rem;
- in fondo l'impronta del testo, le altre versioni pubblicate con la loro data e il pulsante «Stampa».

Per la stampa il foglio di stile nasconde intestazione, pulsanti ed elenco delle versioni.

Il titolo della scheda è il titolo del documento. Una versione ritirata ha `<meta name="robots" content="noindex">`, perché i motori di ricerca mostrino quella in vigore.

Esiti senza documento:
- nessuna versione in vigore: «Documento non disponibile»;
- versione inesistente o in bozza: «Versione non trovata», con il link alla versione in vigore.

Un utente con la sessione aperta apre queste pagine in una nuova scheda (`target="_blank"`): la pagina parte senza Keycloak e non tocca la sessione della scheda di partenza.

### 6. Lettura dentro l'applicativo

- **Pagina di accettazione**:
  - per ogni documento da confermare mostra titolo, versione, «Cosa cambia» quando serve e il testo, in un riquadro scorrevole alto al massimo metà schermo;
  - il testo arriva dall'API pubblica;
  - il link «Apri in una nuova scheda» porta all'indirizzo permanente della versione;
  - la casella di conferma resta com'è. Non si chiede di scorrere fino in fondo: non prova la lettura e ostacola chi usa la tastiera o un lettore di schermo.
- **Piè di pagina** del layout: i link «Termini di utilizzo» e «Informativa privacy» verso `/legal/terms` e `/legal/privacy`, in una nuova scheda.
- **Stato dei documenti** (`GET /api/legal/status`): ogni documento riporta `contentSha256`, `changeSummary` e `previousVersionAccepted` (vero se l'utente ha confermato, nel tenant, un'altra versione dello stesso tipo). `url` resta solo per i documenti esistenti senza testo, vedi la [decisione 8](#8-documenti-esistenti).

### 7. Amministrazione in «Documenti legali»

Il dialogo diventa una pagina di dettaglio: il testo ha bisogno di spazio e di un'anteprima accanto. Percorsi `/legal-documents/new` e `/legal-documents/:id`, solo per il super amministratore, con la protezione delle modifiche non salvate (`canDeactivateUnsavedChanges`).

**Elenco**
- Colonne: documento (titolo e tipo), versione, stato (Bozza, In vigore, Ritirata), in vigore dal, conferma, obbligatorio, impronta.
- Azioni: «Nuovo documento» e apertura del dettaglio.
- L'avviso «Versioni immutabili» diventa: «Dalla pubblicazione una versione non si modifica più. Per cambiare un testo crea una nuova versione a partire da quella in vigore.»

**Dettaglio di una bozza**
- Campi: tipo, versione, titolo, modalità di conferma (proposta dal tipo, come oggi), obbligatorio, sintesi delle modifiche, testo.
- Il testo si scrive in un'area di testo a larghezza fissa. Accanto, su schermi larghi, c'è l'anteprima; su schermi stretti testo e anteprima stanno in due schede.
- L'anteprima chiama `POST /api/legal/documents/preview` 500 ms dopo l'ultima modifica e mostra il testo come la pagina pubblica.
- Azioni:
  - «Salva bozza»;
  - «Pubblica», che salva e pubblica dopo una conferma: «La versione 1.3 sostituisce la 1.2. Da ora il testo non si può più modificare e gli utenti dovranno confermarlo al prossimo accesso.» Per un documento facoltativo la seconda frase diventa «Sarà visibile a tutti.»;
  - «Elimina bozza», con conferma di gravità alta.

**Dettaglio di una versione pubblicata**
- Campi in sola lettura e anteprima del testo.
- Azioni:
  - «Ritira» per la versione in vigore, con l'avviso della [decisione 2](#2-bozza-in-vigore-ritirata);
  - «Riattiva» per una versione ritirata;
  - «Nuova versione da questa», che apre `/legal-documents/new` con tipo, titolo, modalità di conferma, obbligatorietà e testo già compilati, e versione e sintesi vuote;
  - «Apri pagina pubblica».

**Guida introduttiva**
- La tappa `page.legalDocuments.versions` prende il testo del nuovo avviso.
- La pagina di dettaglio riceve le sue tappe: testo e anteprima, pubblicazione, nuova versione. Valgono le regole di `introducedIn` e della revisione del catalogo della guida: le tappe nuove hanno `introducedIn: 3` e `GUIDED_TOUR_CATALOG_REVISION` passa a 3.

### 8. Documenti esistenti

- La migrazione rende facoltativo `url` e considera pubblicate tutte le righe esistenti, che hanno già `published_at`. Restano immutabili.
- Un documento esistente senza testo e con l'URL è un documento **da completare**: la pagina pubblica e quella di accettazione mostrano il link all'URL, come oggi.
- **Completamento una tantum.** Il super amministratore può inserire **una sola volta** il testo di un documento da completare. Lo copia dalla pagina del sito informativo della stessa versione, cioè dal file `.astro` al commit che l'ha pubblicata.
  - Dopo l'inserimento il testo è immutabile e l'URL resta come dato storico.
  - Gli utenti non confermano di nuovo, perché la versione è la stessa.
  - La responsabilità che il testo sia identico è del super amministratore; la cronologia Git di `taurus-info` permette di verificarlo.
  - Le accettazioni registrate prima della migrazione restano senza impronta.
- **In alternativa** si pubblica una nuova versione con lo stesso testo (per esempio termini 1.3 e privacy 1.2), e tutti gli utenti confermano di nuovo. Si sceglie questa strada se il testo cambia o se il testo originale non è certo.
- Un documento nuovo non ha URL: il campo sparisce dall'interfaccia e l'API lo ignora.

### 9. Sito informativo

- `termini-applicativo.astro` e `privacy-applicativo.astro` diventano pagine di rimando a `${appUrl}/legal/terms` e `${appUrl}/legal/privacy`, con `meta refresh` immediato, `link rel="canonical"`, un link visibile e `noindex`, ed escono dalla sitemap. I vecchi indirizzi, nei segnalibri e nelle e-mail, continuano a funzionare.
- Il piè di pagina, `termini.astro` e `privacy.astro` collegano direttamente le pagine dell'applicativo.
- La sezione «Documenti legali dell'applicativo» del README spiega che i testi si gestiscono in Taurus.
- Il sito passa ai rimandi solo dopo che i testi sono in vigore nell'applicativo, vedi il [piano di adozione](#piano-di-adozione-e-compatibilità).

## Autorizzazioni e isolamento tenant

- I documenti sono della piattaforma: stanno nello schema pubblico e solo il super amministratore li gestisce, come oggi.
- L'API pubblica restituisce solo versioni pubblicate, in vigore o ritirate. Una bozza risponde `404` come una versione inesistente, così non si scopre che esiste.
- L'API pubblica non espone dati personali, dati dei tenant né l'autore della versione.
- Le accettazioni restano per tenant, nello schema del tenant, ora con l'impronta. Con l'[account condiviso fra tenant](shared-user-account-spec.md), la conferma data in un tenant non vale per gli altri, come oggi.

## Modello dati e migrazioni

**Schema pubblico** (`master.xml`), nuova migrazione `…_legal_document_content.xml`:

| Colonna | Tipo | Note |
| --- | --- | --- |
| `content` | `TEXT NULL` | testo Markdown normalizzato |
| `content_sha256` | `CHAR(64) NULL` | impronta del testo |
| `change_summary` | `VARCHAR(1000) NULL` | sintesi delle modifiche |
| `url` | diventa `NULL` | solo documenti esistenti |
| `published_at` | diventa `NULL` | vuoto per le bozze |

Vincoli nuovi:
- `active = FALSE OR published_at IS NOT NULL`: una versione in vigore è pubblicata;
- `content IS NOT NULL OR url IS NOT NULL`: ogni documento ha un testo o, se esistente, un URL;
- `(content IS NULL) = (content_sha256 IS NULL)`: testo e impronta vanno insieme.

Le righe esistenti soddisfano già i vincoli: nessuna migrazione dei dati. Gli indici attuali bastano: la lettura pubblica per tipo e versione usa `uq_legal_document_type_version`.

**Schema dei tenant** (`tenant-master.xml`), nuova migrazione `…_user_legal_acceptance_content_hash.xml`: colonna `content_sha256 CHAR(64) NULL` in `user_legal_acceptance`.

**Entità**
- `LegalDocument`: `content`, `contentSha256`, `changeSummary`; `@PrePersist` non scrive più `publishedAt`.
- `UserLegalAcceptance`: `contentSha256`.

Le versioni pubblicate si conservano senza scadenza: le accettazioni le referenziano con `ON DELETE RESTRICT`.

## API

| Metodo e percorso | Chi | Esito |
| --- | --- | --- |
| `GET /api/legal/status` | autenticato | come oggi, con i campi della [decisione 6](#6-lettura-dentro-lapplicativo) |
| `POST /api/legal/acceptances` | autenticato | come oggi; registra anche l'impronta |
| `GET /api/legal/documents` | super amministratore | elenco senza testo, con stato e impronta |
| `GET /api/legal/documents/{id}` | super amministratore | dettaglio con testo |
| `POST /api/legal/documents` | super amministratore | crea una bozza |
| `PUT /api/legal/documents/{id}` | super amministratore | modifica una bozza, o completa una volta un documento esistente; altrimenti `409` |
| `DELETE /api/legal/documents/{id}` | super amministratore | elimina una bozza; altrimenti `409` |
| `POST /api/legal/documents/{id}/activation` | super amministratore | pubblica una bozza o riattiva una versione ritirata, e ritira l'altra dello stesso tipo |
| `DELETE /api/legal/documents/{id}/activation` | super amministratore | ritira la versione |
| `POST /api/legal/documents/preview` | super amministratore | `{ content }` → `{ html }`, con i controlli del testo |
| `GET /api/legal-documents/v1/{type}` | chiunque | versione in vigore; `404` se manca |
| `GET /api/legal-documents/v1/{type}/{version}` | chiunque | versione pubblicata; `404` per bozze e versioni inesistenti |

- `{type}` vale `terms` o `privacy`.
- In `LegalDocumentDTO` stato, `publishedAt`, `active`, `contentSha256` e `url` diventano di sola lettura. L'attivazione passa dagli endpoint dedicati, non più dal campo `active` di `PUT`.
- La risposta pubblica contiene tipo, versione, titolo, `publishedAt`, `active`, sintesi, `html`, `contentSha256`, `url` per i documenti da completare, e `versions`: l'elenco delle versioni pubblicate dello stesso tipo con data e stato. Una sola chiamata basta alla pagina.
- La risposta pubblica ha `Cache-Control: public, max-age=300` e un `ETag` ricavato da impronta e stato.
- `SecurityConfiguration`: `GET /api/legal-documents/v1/**` aperto a tutti, il resto di `/api/legal-documents/**` negato. Gli endpoint nuovi di amministrazione ricadono nella regola esistente di `/api/legal/documents/**`.

## Errori

Nuove chiavi nel catalogo dello [Standard degli errori applicativi](application-errors-standard.md) e in `api-error-messages.ts`:

| Chiave | Stato | Quando | Messaggio |
| --- | --- | --- | --- |
| `legal.document.published.immutable` | 409 | Modifica o eliminazione di una versione già pubblicata, o secondo completamento di un documento esistente | «Una versione pubblicata non si modifica: crearne una nuova» |
| `legal.document.content.required` | 400 | Testo vuoto | «Scrivere il testo del documento» |
| `legal.document.content.tooLong` | 400 | Testo oltre 100 000 caratteri | «Il testo supera i 100.000 caratteri» |
| `legal.document.version.invalid` | 400 | Versione con caratteri non ammessi | «Usare per la versione solo lettere, numeri, punto, trattino e trattino basso» |

- `legal.document.published.immutable` sostituisce `legal.document.accepted.immutable`, che esce dal catalogo: la regola ora vale dalla pubblicazione. Il frontend è l'unico cliente dell'API, quindi il cambio non rompe altri contratti.
- `legal.document.notFound` vale anche per l'API pubblica.

## Sicurezza e osservabilità

- **Iniezione di HTML.** Tre barriere:
  1. CommonMark con `escapeHtml` e `sanitizeUrls`;
  2. la `Safelist` di jsoup;
  3. la pulizia di Angular su `[innerHTML]`, mai aggirata con `bypassSecurityTrustHtml`.

  Il testo lo scrive solo il super amministratore, ma la pagina è pubblica: le barriere valgono comunque.
- **Nessuna risorsa esterna.** Senza immagini né elementi incorporati, la Content Security Policy non cambia.
- **API pubblica senza limitatore.** Restituisce testi pubblici, pochi e piccoli, e non accetta segreti da indovinare, al contrario dei link di firma e dei feed calendario. La cache HTTP riduce le letture ripetute.
- **Tracce.** Pubblicazione, ritiro, riattivazione, eliminazione di una bozza e completamento di un documento esistente scrivono una riga di log `INFO` con id, tipo, versione, impronta e utente. I campi di audit di `legal_document` restano quelli di oggi.
- **Prova dell'accettazione.** Impronta nel documento e nell'accettazione, versione immutabile, indirizzo permanente.
- Nessuna metrica nuova.

## Strategia di test

### Backend

- `LegalServiceImplTest`:
  - ciclo di vita: creazione e modifica di una bozza, pubblicazione con `published_at` scritto dal server, ritiro della versione precedente, riattivazione, ritiro senza sostituzione, eliminazione permessa solo alle bozze;
  - dopo la pubblicazione ogni campo è immutabile (`legal.document.published.immutable`);
  - completamento di un documento esistente riuscito una volta e rifiutato la seconda;
  - normalizzazione del testo, impronta, limiti di testo e versione;
  - l'accettazione registra l'impronta; `previousVersionAccepted` vero solo con una conferma precedente dello stesso tipo nel tenant.
- `LegalMarkdownRendererTest`: costrutti ammessi; HTML scritto a mano mostrato come testo; link `javascript:` e `data:` tolti; immagini tolte; adattamento dei livelli dei titoli e `id`; attributi dei link esterni; tabelle.
- `LegalResourceIT`, come le altre IT delle risorse, sul contesto Spring con i container di prova. Non chiama `DockerRequirement.required()`, perché il contesto si avvia prima dei metodi `@BeforeAll`; senza Docker la classe fallisce invece di saltarsi:
  - API pubblica senza token: versione in vigore, versione ritirata, bozza e versione inesistente in `404`, tipo sconosciuto, intestazioni di cache;
  - API di amministrazione in `403` per tutti i ruoli tranne il super amministratore;
  - migrazioni applicate a schema pubblico e schema tenant, vincoli nuovi rispettati.

### Frontend

- `publicPage()`: `/legal/terms`, `/legal/terms/1.3`, `/legal/privacy` e `/firma/<token>` sono pubblici; `/legal/accept`, `/legal-documents` e `/legal/termsX` no.
- Pagina pubblica: versione in vigore, versione ritirata con avviso e `noindex`, documento non disponibile, versione non trovata, documento da completare con il link, stampa.
- Interceptor: le chiamate all'API pubblica partono senza token e senza logout.
- Pagina di accettazione: riquadro del testo, «Cosa cambia» solo con `previousVersionAccepted`, link alla nuova scheda.
- Amministrazione: bozza modificabile, versione pubblicata in sola lettura, conferma di pubblicazione, «Nuova versione da questa», anteprima ritardata, protezione delle modifiche non salvate.
- Piè di pagina: i due link.
- Catalogo della guida: tappe nuove con le ancore presenti nella pagina.

### End-to-end

Nuovo scenario `legal-documents.spec.ts`:
1. il super amministratore pubblica una nuova versione dei termini;
2. al passaggio successivo un utente arriva alla pagina di accettazione, legge il testo e conferma;
3. un visitatore senza sessione apre `/legal/terms` e la versione precedente.

Una versione pubblicata non si elimina, quindi lo scenario si abilita con `E2E_LEGAL_PUBLISH=true`, gira solo sul profilo desktop e alla fine rimette in vigore la versione di prima. Le prove di sola lettura (pagina pubblica, versione inesistente, accessibilità a 320 pixel, link del piè di pagina) girano sempre.

### Sito informativo

`pnpm test` controlla le due pagine di rimando e i link aggiornati.

Il [Runbook dei test](runbook-test.md) aggiorna `USR-005` e aggiunge le prove della pagina pubblica. Le baseline di copertura si alzano se la copertura cresce.

## Criteri di accettazione

1. Il super amministratore scrive il testo con l'anteprima, salva una bozza e la pubblica. Da quel momento la versione non si modifica: l'API risponde `legal.document.published.immutable`.
2. Pubblicare una versione ritira la precedente dello stesso tipo. Al passaggio successivo gli utenti devono confermarla, leggono il testo nella pagina di accettazione e vedono «Cosa cambia» se avevano confermato la versione precedente.
3. Ogni nuova accettazione registra l'impronta del testo confermato.
4. `/legal/terms` e `/legal/privacy` mostrano a chiunque, senza login, la versione in vigore. `/legal/terms/<versione>` e `/legal/privacy/<versione>` mostrano ogni versione pubblicata. Bozze e versioni inesistenti risultano «Versione non trovata».
5. HTML scritto a mano, link `javascript:` e immagini nel testo non arrivano mai alla pagina.
6. Il piè di pagina dell'applicativo collega i due documenti.
7. Un documento esistente riceve il testo una sola volta, e chi l'aveva confermato non deve confermare di nuovo.
8. I vecchi indirizzi del sito informativo portano alle pagine dell'applicativo.
9. Le verifiche di `taurus-be`, `taurus-fe` e `taurus-info` passano.

## Piano di adozione e compatibilità

1. Rilascio insieme di backend e frontend, perché cambia lo stato dei documenti. Le migrazioni girano sullo schema pubblico e su ogni schema tenant. I documenti esistenti continuano a funzionare con il loro URL.
2. Il super amministratore completa il testo delle versioni in vigore ([decisione 8](#8-documenti-esistenti)) oppure pubblica nuove versioni.
3. Verifica di `/legal/terms` e `/legal/privacy`.
4. Rilascio del sito informativo con le pagine di rimando.

Per i tenant non cambia nulla. Con il completamento una tantum nessun utente deve confermare di nuovo.

Le migrazioni aggiungono colonne e tolgono due `NOT NULL`. Si può tornare alla release precedente finché ogni documento ha un URL: dopo la prima versione pubblicata senza URL, il ritorno indietro non è supportato.
