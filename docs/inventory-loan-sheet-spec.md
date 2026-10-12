# Foglio di consegna e affidamento in comodato

## Stato del documento

ID catalogo: `inventory-loan-sheet`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

Documenti collegati: [Gestione inventario tenant](inventory-management-spec.md), [Gestione centralizzata dei media](media-asset-spec.md), [PostgreSQL schema per tenant](postgres-tenant-schemas.md).

## Obiettivo

Taurus deve generare il **foglio di consegna e affidamento in comodato di beni associativi**: il documento cartaceo che il tenant stampa, fa firmare all'Affidatario e conserva come contratto di comodato. Il modello predefinito riproduce forma e struttura del modello `Template foglio inventario.docx` fornito dall'associazione (versione del 28/09/2026), composto da dieci sezioni:

1. Affidatario;
2. Socio utilizzatore minorenne;
3. Beni consegnati;
4. Durata dell'affidamento;
5. Dichiarazioni dell'Affidatario;
6. Condizioni di affidamento e responsabilità (artt. 1–17);
7. Sottoscrizione, con approvazione specifica delle clausole ex artt. 1341–1342 c.c.;
8. Registro delle variazioni;
9. Verbale di riconsegna;
10. Informativa sul trattamento dei dati personali.

Obiettivi specifici:

- riprodurre fedelmente, con il modello predefinito, impaginazione, tabelle e testi del modello Word;
- consentire a ogni tenant di caricare il proprio modello completo di sezioni, tabelle, intestazioni e testi, e di scaricare sia il proprio modello sia quello predefinito;
- spiegare nella pagina di caricamento tutte le variabili disponibili;
- precompilare soltanto i dati che Taurus già gestisce, lasciando in bianco quelli da compilare a mano;
- rendere le regole di durata del comodato parametri del tenant, coerenti tra testo contrattuale e controlli applicativi;
- tracciare il ciclo di vita del foglio: emissione, consegne successive, sostituzioni, rinnovi, restituzioni, chiusura e definizione.

## Fuori scope

- firma elettronica o firma grafometrica: il foglio si firma su carta;
- raccolta e conservazione in Taurus di dati anagrafici sensibili dell'Affidatario (codice fiscale, documento di identità, residenza, telefono, luogo di nascita) e dei dati del genitore o tutore;
- modifica del modello nel browser: il modello si scarica, si modifica con un editor esterno e si ricarica;
- modelli in formato Word o in formati diversi da HTML;
- caricamento della copia firmata scansionata;
- gestione di smarrimenti, furti e sinistri assicurativi oltre a quanto già previsto dall'inventario;
- notifiche all'utente sull'emissione del foglio;
- archiviazione della copia cartacea, che resta responsabilità del tenant.

## Decisioni principali

1. **Unico documento dell'inventario.** Il foglio di affidamento ha finalità contrattuale e sostituisce il proforma PDF dell'inventario, che è stato rimosso: lato amministrazione si emette dalla pagina utente, lato socio si scarica dalla propria lista dell'inventario.
2. **Dati anagrafici sensibili lasciati in bianco.** Taurus precompila nome, cognome, data di nascita ed e-mail del socio, già presenti nell'anagrafica. Tutti gli altri campi delle sezioni 1 e 2 sono stampati vuoti e compilati a mano.
3. **Scadenza per assegnazione.** Resta valido il modello attuale: la data di scadenza appartiene alla singola assegnazione. Il foglio gestisce il caso di più strumenti con scadenze diverse (vedi sezione 4).
4. **Modello caricabile per tenant.** Il foglio è generato da un unico file HTML con CSS che contiene sezioni, tabelle, intestazioni, piè di pagina e testi; non sono ammessi modelli Word. Ogni tenant può caricare il proprio modello; senza un modello caricato si usa il modello predefinito di Taurus (`loan-sheet.html`). Entrambi sono scaricabili.
5. **Variabili senza logica.** I dati entrano nel modello solo tramite variabili con sintassi Mustache, sempre con escape. Il modello non può contenere espressioni, codice o accessi a risorse esterne: per questo non si usa Thymeleaf, le cui espressioni possono eseguire codice sul server.
6. **Attivazione in due passi, senza bozza.** Il file scelto viene verificato subito e se ne può vedere l'anteprima; diventa una versione solo con **Attiva modello**.
7. **Versioni immutabili del modello.** Ogni modello attivato diventa una versione immutabile. Ogni foglio registra la versione e l'hash SHA-256 del modello usato e viene sempre rigenerato con quella versione.
8. **Regole di durata per tenant.** Le regole di durata del comodato (durata minima e massima, limite del rinnovo, durata complessiva massima; art. 4 del modello predefinito) sono un'impostazione del tenant, usata dai controlli applicativi e disponibile nel modello come variabili. All'emissione vengono copiate sul foglio, che le conserva per le consegne successive e i rinnovi.
9. **Predefinito aggiornato automaticamente.** Un tenant che usa il modello predefinito riceve, per i nuovi fogli, il predefinito aggiornato dalle release successive; i fogli già emessi conservano la copia con cui sono stati emessi.
10. **Font standard PDF e immagini incorporate.** Il modello predefinito usa Helvetica (codifica WinAnsi), senza incorporare font. I caratteri `’ « » € – —` devono essere stampati correttamente. Il carattere `☐` non esiste in Helvetica ed è disegnato come casella con bordi CSS. I modelli caricati possono usare solo i font standard PDF e immagini incorporate come `data:` URI; il logo del tenant è disponibile come variabile.
11. **Regime del comodato sull'oggetto.** Ogni oggetto dichiara il regime di comodato: *con scadenza* (strumenti musicali e relativi accessori) oppure *precario* (divise, relativi accessori e altri beni). Non si tratta di una tassonomia di categorie, ma della qualificazione giuridica richiesta dalle sezioni 4 e 6.
12. **Un solo foglio aperto per socio.** Le consegne successive all'emissione si collegano automaticamente al foglio aperto e compaiono nel registro delle variazioni, come previsto dall'art. 15.
13. **Numerazione annuale.** Il numero del foglio ha formato `AAAA/NNN`, con progressivo per tenant che riparte ogni anno solare.
14. **Rinnovo solo prima della scadenza.** Un comodato a scadenza si rinnova soltanto prima della scadenza in corso, come previsto dall'art. 4 predefinito. La regola resta applicata anche se il modello del tenant formula diversamente l'articolo.
15. **Definizione automatica senza addebiti.** Un foglio chiuso senza addebiti viene definito automaticamente 30 giorni dopo la chiusura, se l'amministratore non lo ha già fatto.
16. **Nessuna scadenza per il comodato precario.** Un bene in comodato precario collegato a un foglio non può avere data di scadenza.
17. **Download personale.** Il socio può scaricare il PDF dei propri fogli emessi o chiusi.

## Struttura del modello predefinito e fonte dei dati

Questa sezione descrive il modello predefinito. Un modello caricato dal tenant può organizzare liberamente sezioni e testi; i dati disponibili sono quelli elencati in [Variabili](#variabili).

### Intestazione della prima pagina

Il blocco iniziale, centrato, non contiene logo né data di generazione:

| Riga | Contenuto | Stile |
| --- | --- | --- |
| 1 | denominazione del tenant | 24 pt |
| 2 | indirizzo, CAP, città e provincia | 14 pt |
| 3 | recapiti – P. IVA – C.F., omettendo le parti mancanti | 9 pt |
| 4 | `FOGLIO DI CONSEGNA E AFFIDAMENTO IN COMODATO DI BENI ASSOCIATIVI` | 12 pt grassetto, filetto inferiore 1 pt `#404040` |

Segue il preambolo.

I recapiti del tenant sono e-mail, telefono e PEC. Telefono e PEC sono nuovi campi facoltativi di `Tenants`.

Il foglio non usa `TenantPdfHeaderService`: il modello comune dei PDF Taurus prevede logo e data di generazione, che il modello predefinito non riporta. I dati del tenant sono comunque letti tramite `TenantsService`, e il logo tramite `TenantLogoLoader`. La deroga è riportata nella specifica dell'inventario, che impone l'intestazione comune a tutti gli altri PDF inventario.

### Intestazione e piè di pagina ricorrenti

Su ogni pagina:

- in alto a destra, corsivo 8 pt: `<denominazione tenant> – Foglio di affidamento n. <numero>`;
- in basso a sinistra, 8 pt: `Pagina X di Y`;
- in basso a destra, 8 pt: `Sigla dell’Affidatario ____________`.

Il modello Word contiene un punto superfluo dopo il nome del tenant nell'intestazione ricorrente: il modello predefinito non lo riporta.

### Sezione 1 – Affidatario

Tabella a quattro colonne con celle etichetta grigie. Righe: nome e cognome (valore su tre colonne); nato/a a – il; residente in (valore su tre colonne); CAP – città e provincia; codice fiscale – documento; rilasciato da – il; telefono – e-mail.

Segue la riga `In qualità di: ☐ socio maggiorenne ☐ genitore / tutore del socio minorenne indicato di seguito`.

Precompilazione in base al ruolo dell'Affidatario registrato sul foglio:

| Ruolo | Sezione 1 | Casella spuntata | Sezione 2 |
| --- | --- | --- | --- |
| `MEMBER` (socio maggiorenne) | nome e cognome, data di nascita ed e-mail del socio; resto in bianco | socio maggiorenne | in bianco |
| `GUARDIAN` (genitore o tutore) | interamente in bianco | genitore / tutore | nome e cognome e data di nascita del socio; luogo di nascita in bianco |

Il ruolo è proposto calcolando l'età del socio alla data del foglio e può essere modificato dall'amministratore. Se la data di nascita manca, l'amministratore deve sceglierlo esplicitamente.

### Sezione 2 – Socio utilizzatore minorenne

Tabella a quattro colonne con due righe: nome e cognome; nato/a a – il. Segue una nota in corsivo. La sezione è sempre stampata, come nel modello Word.

### Sezione 3 – Beni consegnati

Inizia a pagina nuova. Una nota in corsivo precede la tabella a otto colonne:

| Colonna | Fonte |
| --- | --- |
| N. | numero progressivo della riga |
| Nome | nome dell'oggetto |
| Marca / modello | nuovo campo `brandModel` dell'oggetto |
| Matricola / cod. inventario | `serialNumber / inventoryNumber`; solo il numero inventariale se la matricola manca |
| Accessori / taglia | nuovo campo `accessories` dell'assegnazione |
| Q.tà | quantità in carico alla data di emissione |
| Valore concordato (€) | valore unitario stimato dell'oggetto (`estimatedUnitValue`), solo se la valuta è EUR, copiato sulla riga all'emissione; con quantità maggiore di 1 si stampa il totale seguito dal valore unitario, per esempio `150,00 (75,00 cad.)`. Con un'altra valuta la cella resta vuota, da compilare a mano |
| Stato alla consegna / note | stato e note di conservazione letti dallo snapshot della revisione iniziale dell'assegnazione, non dallo stato corrente dell'oggetto |

Le righe della sezione 3 sono congelate all'emissione: modifiche successive dell'oggetto o dell'assegnazione non alterano il foglio già firmato.

La tabella ha sempre almeno 14 righe numerate. Le righe non valorizzate restano vuote. Oltre la quattordicesima riga la tabella prosegue ripetendo l'intestazione.

### Sezione 4 – Durata dell'affidamento

Due opzioni con casella:

- comodato con scadenza: casella spuntata se il foglio contiene almeno un bene con comodato a scadenza;
- comodato precario: casella spuntata se il foglio contiene almeno un bene in comodato precario.

La variabile `{{durata.scadenzaStrumenti}}` assume questi valori:

| Situazione | Testo stampato |
| --- | --- |
| nessuno strumento | `del ____ / ____ / ________` |
| tutti gli strumenti hanno la stessa scadenza | `del 30/09/2027` |
| scadenze diverse | `indicata per ciascuno strumento nella sezione 3` |

Nel terzo caso la colonna «Stato alla consegna / note» di ogni riga a scadenza termina con `Scadenza: gg/mm/aaaa`, composto nel modello con i campi `statoENote` e `scadenza` e la variabile `durata.scadenzeDiverse`.

### Sezioni 5 e 6 – Dichiarazioni e condizioni

Dichiarazioni dell'Affidatario e condizioni di affidamento (artt. 1–17), testo giustificato. La sezione 6 inizia a pagina nuova. L'art. 4 riporta le regole di durata tramite le variabili `durata.*`.

### Sezione 7 – Sottoscrizione

Inizia a pagina nuova e contiene:

- `Luogo e data` precompilato con luogo e data del foglio;
- firma dell'Affidatario e firma del socio minorenne per presa visione, su tabella senza bordi a due colonne;
- `Per l’Associazione – consegna autorizzata con delibera / provvedimento n. ___ del ___`, con estremi precompilati se indicati all'emissione;
- nome e cognome del firmatario per l'Associazione, se indicato, e spazio per la firma;
- il testo di approvazione specifica delle clausole, preceduto dal titolo `Approvazione specifica delle clausole` e seguito dalla firma dell'Affidatario.

### Sezione 8 – Registro delle variazioni

Inizia a pagina nuova. Una nota in corsivo precede la tabella: data, bene, marca/modello e matricola, operazione, scadenza strumento, valore concordato, firma dell'Affidatario, firma per l'Associazione.

Le righe sono ricavate in ordine cronologico:

| Operazione | Origine | Contenuto |
| --- | --- | --- |
| `C` | assegnazione collegata al foglio dopo l'emissione | data di assegnazione, bene e quantità, scadenza se a termine, valore concordato (valore stimato corrente dell'oggetto, se in euro) |
| `S` | assegnazione che sostituisce un'altra assegnazione del foglio | come `C`, con indicazione del bene sostituito |
| `R` | riconsegna parziale completata | data di completamento, bene e quantità restituita |
| `N` | rinnovo di un comodato a scadenza | data del rinnovo, nuova scadenza; nella colonna «Firma per l’Associazione» sono stampati gli estremi della delibera, lasciando lo spazio per la firma |

Le riconsegne generate da una sostituzione non producono una riga `R`: sono rappresentate dalla riga `S`.

Una riconsegna è **finale** quando il foglio è chiuso e la data di completamento coincide con la data dell'ultima riconsegna completata del foglio, nel fuso orario del tenant. Tutte le altre riconsegne completate sono **parziali**. Le riconsegne finali compaiono nella sezione 9, quelle parziali nella sezione 8.

La tabella ha almeno 21 righe e prosegue oltre, se necessario, ripetendo l'intestazione. Le colonne firma restano sempre vuote.

### Sezione 9 – Verbale di riconsegna

Inizia a pagina nuova. Contiene la riga `Riconsegna ☐ totale ☐ parziale in data ____`, la tabella a sei colonne (N., bene, matricola o codice inventario, restituito sì/no, stato alla riconsegna, note), le due opzioni di esito (nessun addebito, addebito con importo e termine) e le firme.

- Con foglio **emesso**: la sezione è stampata in bianco, con 14 righe numerate e caselle vuote.
- Con foglio **chiuso**: casella `totale` spuntata, data della riconsegna finale, una riga per ogni bene restituito nella riconsegna finale, con stato e note della riconsegna. `Restituito` vale `Sì` se è rientrata l'intera quantità, altrimenti `No` con l'indicazione della quantità mancante. È spuntato l'esito registrato alla chiusura, con importo e termine se previsti. Il nome di chi riceve i beni per l'Associazione è precompilato.

### Sezione 10 – Informativa

Inizia a pagina nuova. Titolo `10. INFORMATIVA SUL TRATTAMENTO DEI DATI PERSONALI (art. 13 Reg. UE 2016/679 – GDPR)`, seguito dal testo dell'informativa. La lettera f) descrive il comportamento di conservazione di Taurus (vedi [Conservazione e GDPR](#conservazione-e-gdpr)).

## Modello del foglio

### Modello predefinito

Il modello predefinito è il file [`loan-sheet.html`](../taurus-be/src/main/resources/templates/inventory/loan-sheet.html), con un'etichetta di versione (per esempio `2026-10-09`) nel file `loan-sheet.properties` accanto al modello, che contiene anche le regole di durata iniziali dei tenant. Contiene impaginazione, testi del modello Word del 28/09/2026 e variabili, ed è scritto con la stessa sintassi ammessa per i modelli caricati: il file scaricato è quindi un punto di partenza funzionante per un modello del tenant. Ogni modifica del file richiede di aggiornare l'etichetta.

### Formato del file

- un solo file `.html` in UTF-8, al massimo 2 MB, immagini incorporate comprese;
- HTML5 o XHTML: il file è letto con un parser tollerante, quindi non deve essere XML ben formato;
- CSS in elementi `<style>` o attributi `style`; non sono ammessi fogli di stile esterni;
- impaginazione con il CSS paged media supportato da OpenHTMLtoPDF: `@page` per formato, margini e riquadri di pagina, `counter(page)` e `counter(pages)` per la numerazione, `position: running(...)` con `element(...)` per intestazioni e piè di pagina ricorrenti, `page-break-before` per i salti pagina, `-fs-table-paginate: paginate` per ripetere l'intestazione delle tabelle su più pagine. Il modello predefinito è l'esempio di riferimento di tutte queste tecniche;
- immagini solo come `data:` URI PNG o JPEG; il logo del tenant è disponibile come variabile;
- font: solo i font standard PDF, `Helvetica` (o `sans-serif`), `Times` (o `serif`) e `Courier` (o `monospace`), con i caratteri della codifica WinAnsi;
- nessun JavaScript: gli script non sono eseguiti.

### Sintassi

| Costrutto | Significato |
| --- | --- |
| `{{nome}}` | inserisce il valore con escape HTML; un valore assente è una stringa vuota |
| `{{#nome}}…{{/nome}}` | con una variabile sì/no mostra il contenuto se vale sì; con un testo, se non è vuoto; con un elenco, ripete il contenuto per ogni elemento |
| `{{^nome}}…{{/nome}}` | mostra il contenuto se la variabile vale no, è vuota o è un elenco vuoto |
| `{{! commento }}` | commento, non stampato |

Dentro una sezione su un elenco i campi dell'elemento si indicano con il solo nome, per esempio `{{#beni}}<tr><td>{{numero}}</td><td>{{nome}}</td></tr>{{/beni}}`.

Per stampare una linea da compilare a mano quando un dato manca si combinano le due sezioni: `{{#foglio.delibera}}{{foglio.delibera}}{{/foglio.delibera}}{{^foglio.delibera}}________{{/foglio.delibera}}`.

Non sono ammessi: inserimento senza escape (`{{{nome}}}`, `{{&nome}}`), inclusione di altri modelli (`{{> nome}}`) e cambio dei delimitatori (`{{=<% %>=}}`).

### Variabili

Le date hanno formato `gg/mm/aaaa`, gli importi formato `1.250,00` senza simbolo di valuta. Il catalogo delle variabili è definito una sola volta nel backend (`LoanSheetVariables`) ed è usato sia dalla validazione sia dalla guida della pagina del modello.

**Associazione**

| Variabile | Tipo | Contenuto | Esempio |
| --- | --- | --- | --- |
| `associazione.nome` | testo | denominazione del tenant | `Associazione Musicale Esempio APS` |
| `associazione.indirizzo` | testo | indirizzo, CAP, città e provincia | `Via Roma 10, 00100 Roma (RM)` |
| `associazione.email` | testo | e-mail del tenant | `info@example.org` |
| `associazione.telefono` | testo | telefono del tenant | `06 1234567` |
| `associazione.pec` | testo | PEC del tenant | `associazione@pec.example.org` |
| `associazione.recapiti` | testo | e-mail, telefono e PEC presenti, separati da virgola | `info@example.org, tel. 06 1234567, PEC associazione@pec.example.org` |
| `associazione.partitaIva` | testo | partita IVA | `01234567890` |
| `associazione.codiceFiscale` | testo | codice fiscale | `91234567890` |
| `associazione.datiFiscali` | testo | `P. IVA …, C.F. …`, omettendo le parti mancanti | `P. IVA 01234567890, C.F. 91234567890` |
| `associazione.logo` | testo | logo del tenant come `data:` URI, da usare nell'attributo `src` di un'immagine; vuoto se il tenant non ha logo | `data:image/png;base64,…` |

**Foglio**

| Variabile | Tipo | Contenuto | Esempio |
| --- | --- | --- | --- |
| `foglio.numero` | testo | numero del foglio; vuoto nelle anteprime | `2026/007` |
| `foglio.data` | testo | data del foglio | `28/09/2026` |
| `foglio.luogo` | testo | luogo del foglio | `Roma` |
| `foglio.luogoEData` | testo | luogo e data separati da virgola | `Roma, 28/09/2026` |
| `foglio.delibera` | testo | numero della delibera o del provvedimento di consegna | `7` |
| `foglio.dataDelibera` | testo | data della delibera | `25/09/2026` |
| `foglio.firmatario` | testo | nome e cognome di chi firma per l'Associazione | `Mario Bianchi` |
| `foglio.anteprima` | sì/no | vale sì nelle anteprime | |

**Socio, Affidatario e minore**

| Variabile | Tipo | Contenuto | Esempio |
| --- | --- | --- | --- |
| `socio.nome`, `socio.cognome`, `socio.nomeCognome` | testo | dati del socio, indipendentemente dal ruolo dell'Affidatario | `Giulia Rossi` |
| `socio.dataNascita` | testo | data di nascita del socio | `14/03/2010` |
| `socio.email` | testo | e-mail del socio | `giulia.rossi@example.org` |
| `affidatario.socioMaggiorenne` | sì/no | l'Affidatario è il socio maggiorenne | |
| `affidatario.genitoreTutore` | sì/no | l'Affidatario è il genitore o tutore del socio minorenne | |
| `affidatario.nomeCognome`, `affidatario.dataNascita`, `affidatario.email` | testo | dati del socio se è l'Affidatario; vuoti se l'Affidatario è il genitore o tutore | `Giulia Rossi` |
| `minore.nomeCognome`, `minore.dataNascita` | testo | dati del socio se l'Affidatario è il genitore o tutore; vuoti altrimenti | `Luca Rossi` |

**Beni consegnati** – elenco `beni`, una voce per riga della sezione 3:

| Campo | Tipo | Contenuto | Esempio |
| --- | --- | --- | --- |
| `numero` | testo | numero progressivo della riga | `1` |
| `nome` | testo | nome dell'oggetto | `Clarinetto in Si bemolle` |
| `marcaModello` | testo | marca e modello | `Yamaha YCL-255` |
| `matricola` | testo | matricola | `SN-004512` |
| `numeroInventario` | testo | numero inventariale | `INV-0001` |
| `matricolaInventario` | testo | `matricola / numero inventariale`, oppure il solo numero inventariale | `SN-004512 / INV-0001` |
| `accessori` | testo | accessori o taglia | `Custodia, bocchino, legatura` |
| `quantita` | testo | quantità in carico all'emissione | `1` |
| `valoreUnitario` | testo | valore unitario stimato dell'oggetto all'emissione, se espresso in euro | `75,00` |
| `valoreConcordato` | testo | valore concordato: valore stimato dell'oggetto in euro, totale seguito dall'unitario se la quantità è maggiore di 1 | `150,00 (75,00 cad.)` |
| `stato` | testo | stato di conservazione alla consegna | `Buono` |
| `note` | testo | note di conservazione alla consegna | `Graffio sul barilotto` |
| `aScadenza` | sì/no | bene in comodato con scadenza | |
| `precario` | sì/no | bene in comodato precario | |
| `scadenza` | testo | data di scadenza, solo per il comodato a scadenza | `30/09/2027` |
| `statoENote` | testo | stato e note di conservazione separati da trattino | `Buono – Graffio sul barilotto` |

L'elenco `beniRigheVuote` contiene le righe vuote necessarie per arrivare a 14 righe totali, ciascuna con il solo campo `numero`.

**Durata**

| Variabile | Tipo | Contenuto | Esempio |
| --- | --- | --- | --- |
| `durata.aScadenza` | sì/no | il foglio contiene almeno un bene in comodato a scadenza | |
| `durata.precario` | sì/no | il foglio contiene almeno un bene in comodato precario | |
| `durata.scadenzaStrumenti` | testo | vedi la tabella della [sezione 4](#sezione-4--durata-dellaffidamento) | `del 30/09/2027` |
| `durata.scadenzaUnica` | testo | scadenza comune a tutti gli strumenti; vuota se non ci sono strumenti o se le scadenze sono diverse | `30/09/2027` |
| `durata.scadenzeDiverse` | sì/no | gli strumenti hanno scadenze diverse | |
| `durata.mesiMinimi` | testo | durata minima del comodato a scadenza, in mesi | `12` |
| `durata.mesiMassimi` | testo | durata massima del comodato a scadenza | `24` |
| `durata.mesiRinnovoMassimi` | testo | estensione massima di un rinnovo | `24` |
| `durata.mesiComplessiviMassimi` | testo | durata complessiva massima per strumento | `36` |

Le variabili `durata.mesi*` riportano le regole registrate sul foglio; nelle anteprime del modello, le regole correnti del tenant.

**Registro delle variazioni** – elenco `registro`, una voce per riga della sezione 8:

| Campo | Tipo | Contenuto | Esempio |
| --- | --- | --- | --- |
| `data` | testo | data della variazione | `15/11/2026` |
| `bene` | testo | bene e quantità; per le sostituzioni, anche il bene sostituito | `Leggio pieghevole` |
| `marcaModelloMatricola` | testo | marca, modello e matricola o numero inventariale | `INV-0340` |
| `operazione` | testo | `C`, `S`, `R` o `N` | `C` |
| `scadenza` | testo | scadenza dello strumento | `30/09/2027` |
| `valoreConcordato` | testo | valore concordato: valore stimato dell'oggetto in euro | `25,00` |
| `delibera` | testo | per i rinnovi, estremi della delibera su due righe (`Delibera n. …` e `del …`); da stampare con `white-space: pre-line` | `Delibera n. 12` |

L'elenco `registroRigheVuote` contiene le righe vuote necessarie per arrivare a 21 righe totali, senza campi.

**Riconsegna**

| Variabile | Tipo | Contenuto | Esempio |
| --- | --- | --- | --- |
| `riconsegna.registrata` | sì/no | il foglio è chiuso e la riconsegna finale è registrata | |
| `riconsegna.data` | testo | data della riconsegna finale | `10/09/2027` |
| `riconsegna.beni` | elenco | beni restituiti nella riconsegna finale, con i campi `numero`, `bene`, `matricolaInventario`, `restituito` (`Sì`, oppure `No` con la quantità mancante), `stato` e `note` | |
| `riconsegna.righeVuote` | elenco | righe vuote necessarie per arrivare a 14 righe totali, con il solo campo `numero` | |
| `riconsegna.senzaAddebiti` | sì/no | esito senza addebiti | |
| `riconsegna.conAddebiti` | sì/no | esito con addebito | |
| `riconsegna.importo` | testo | importo dell'addebito | `1.250,00` |
| `riconsegna.termine` | testo | termine di pagamento dell'addebito | `30/09/2027` |
| `riconsegna.ricevutoDa` | testo | nome e cognome di chi riceve i beni per l'Associazione | `Mario Bianchi` |

I dati provenienti da altre funzionalità (nomi, note, dati del tenant) passano dalla normalizzazione dei caratteri: i caratteri non rappresentabili con i font standard PDF sono sostituiti con `?`.

### Validazione

Il file è verificato quando viene scelto nella pagina, prima dell'anteprima e prima dell'attivazione. La verifica restituisce le segnalazioni con il numero di riga, quando applicabile.

Segnalazioni bloccanti, che impediscono anteprima e attivazione (`inventory.loanTemplate.invalid`, HTTP 422):

- file vuoto, non UTF-8 o oltre 2 MB (oltre il limite: `inventory.loanTemplate.tooLarge`, HTTP 413);
- sintassi Mustache non valida, per esempio una sezione non chiusa o chiusa con un nome diverso;
- variabili sconosciute, o campi di un elenco usati fuori dalla sezione dell'elenco;
- un elenco usato come semplice variabile invece che come sezione;
- costrutti non ammessi: inserimento senza escape, inclusioni, cambio dei delimitatori;
- risorse esterne: attributi `src`, `href` e simili e `url(...)` del CSS che non siano `data:` URI, ancore interne (`#…`) o soltanto variabili, `@import` e `@font-face`. I collegamenti `<a href>` verso `http`, `https` e `mailto` sono ammessi, perché nel PDF diventano link e non caricano nulla;
- immagini incorporate in formati diversi da PNG, JPEG o GIF;
- elementi `<script>`, `<iframe>`, `<frame>`, `<object>`, `<embed>`, `<applet>`, `<link>` e `<base>`;
- generazione del PDF con i dati di esempio non riuscita, oppure PDF oltre 20 MB.

Avvisi non bloccanti:

- caratteri del testo non rappresentabili con i font standard PDF, con riga e carattere, perché verrebbero stampati in modo errato;
- font diversi da Helvetica, Times e Courier, che vengono sostituiti da un font standard;
- il modello non contiene `foglio.numero` oppure l'elenco `beni`;
- il modello non contiene nessuna delle variabili `durata.mesi*`: il testo sulla durata del comodato potrebbe non corrispondere alle regole di durata applicate dai controlli.

La pagina ricorda che la coerenza del contratto è responsabilità del tenant: rimandi tra articoli, elenco delle clausole approvate e testo della lettera f) dell'informativa, che descrive come Taurus conserva e rimuove i dati del foglio.

### Anteprima

L'anteprima genera il PDF con i dati di esempio di `LoanSheetSamples` e le regole di durata correnti del tenant. È disponibile per il file appena scelto, per il modello in uso, per il modello predefinito e per ogni versione dello storico, e non viene salvata.

In ogni anteprima, compresa quella della finestra di emissione con i dati reali del socio, Taurus sovrappone a tutte le pagine la filigrana `ANTEPRIMA` dopo la generazione del PDF, indipendentemente dal modello. `foglio.numero` è vuoto e `foglio.anteprima` vale sì.

### Versioni e attivazione

- **Attiva modello** crea una nuova versione con numero progressivo, nome del file, contenuto, hash SHA-256, autore e data, registrati nei campi di audit, e la rende il modello in uso per i nuovi fogli. La versione non è più modificabile né cancellabile.
- Un file identico, per hash, a una versione esistente non crea una nuova versione: la versione esistente torna in uso.
- Dallo storico si può rimettere in uso una versione precedente, scaricarla e vederne l'anteprima.
- **Torna al modello predefinito** fa usare ai nuovi fogli il modello predefinito, senza cancellare lo storico.
- Senza un modello caricato in uso, i nuovi fogli usano il modello predefinito corrente. All'emissione Taurus ne salva una copia immutabile, con origine `DEFAULT` ed etichetta, riusando la copia esistente se l'hash coincide: ogni foglio resta così rigenerabile anche dopo un aggiornamento del predefinito.
- Un cambio di modello non modifica i fogli già emessi: il loro PDF è sempre generato con la versione registrata sul foglio.

### Regole di durata

| Parametro | Predefinito | Vincoli |
| --- | ---: | --- |
| `minMonths` | 12 | intero, 1 ≤ valore ≤ `maxMonths` |
| `maxMonths` | 24 | intero, ≤ `totalMaxMonths` |
| `renewalMaxMonths` | 24 | intero, 1 ≤ valore ≤ `totalMaxMonths` |
| `totalMaxMonths` | 36 | intero, ≤ 120 |

Le regole si modificano nella pagina del modello, con un salvataggio proprio e controllo di concorrenza. Valgono per i fogli emessi da quel momento: all'emissione vengono copiate sul foglio, e le consegne successive e i rinnovi di quel foglio usano la copia.

### Scadenza all'assegnazione

Un oggetto in comodato con scadenza segue le regole di durata fin dall'assegnazione, anche se il socio non ha ancora un foglio emesso. Così l'emissione non trova scadenze da correggere.

- **Nuova assegnazione:** la data di scadenza è obbligatoria e deve cadere in `[assegnazione + minMonths, assegnazione + maxMonths]`. Si usano le regole del foglio emesso del socio, se esiste, altrimenti le regole correnti del tenant.
- **Modifica di un'assegnazione non collegata a un foglio:** una scadenza diversa da quella registrata segue la stessa regola, contando i mesi dal giorno dell'assegnazione. Una scadenza invariata non viene ricontrollata, così si possono modificare gli altri campi anche quando le regole sono cambiate dopo l'assegnazione.
- **Comodato precario e regime non indicato:** la scadenza resta facoltativa e libera fino all'emissione.

Le violazioni senza foglio emesso restituiscono `422` con chiave `inventory.loanDuration.<violazione>` e, per una data fuori intervallo, i parametri `minimum`, `maximum`, `minMonths`, `maxMonths` e `deliveredOn`. Il frontend ne ricava il messaggio, per esempio «La data di scadenza deve essere compresa tra il 09/10/2027 e il 09/10/2028, cioè tra 12 e 24 mesi dalla consegna del 09/10/2026». Con un foglio emesso la chiave è `inventory.loanSheet.<violazione>`, con il parametro aggiuntivo `sheetNumber`, e il messaggio aggiunge «(regole del foglio n. …)». Il frontend non limita il calendario: chiede a `GET /api/inventory/users/{userIndex}/loan-expiration-range` l'intervallo ammesso solo per indicarlo sotto il campo, e se il backend rifiuta la data mostra il messaggio sotto il campo, senza toast, così da poterne scegliere un'altra.

## Ciclo di vita del foglio

### Stati

| Stato | Significato |
| --- | --- |
| `ISSUED` | foglio emesso; accoglie consegne successive, sostituzioni, rinnovi e riconsegne |
| `CLOSED` | tutti i beni sono rientrati; esito della riconsegna registrato |
| `SETTLED` | rapporto definito; dati personali del foglio rimossi |
| `CANCELLED` | foglio annullato per errore; il numero non viene riutilizzato |

Per ogni socio può esistere al massimo un foglio `ISSUED`.

### Numerazione

Il numero è progressivo per tenant e anno solare, nel formato `AAAA/NNN` (per esempio `2026/007`). È assegnato all'emissione tramite un contatore per anno con lock pessimista ed è univoco. I numeri dei fogli annullati non vengono riassegnati.

### Emissione

L'amministratore apre dalla pagina utente la finestra **Emetti foglio di affidamento**, che propone:

- tutte le assegnazioni del socio con quantità in carico e non collegate a un foglio, selezionate per default e deselezionabili;
- data del foglio, predefinita a oggi;
- luogo, predefinito alla città del tenant;
- ruolo dell'Affidatario, predefinito in base all'età;
- estremi della delibera o provvedimento (numero e data), facoltativi;
- nome e cognome del firmatario per l'Associazione, facoltativo, proposto con l'ultimo valore usato nel tenant.

L'emissione è rifiutata se:

- il socio ha già un foglio `ISSUED`;
- nessuna assegnazione è selezionata;
- un oggetto selezionato non ha il regime di comodato;
- un'assegnazione a scadenza non ha data di scadenza o ha una scadenza fuori dall'intervallo `[consegna + minMonths, consegna + maxMonths]` delle regole di durata correnti, dove la consegna è la data di assegnazione;
- un'assegnazione in comodato precario ha una data di scadenza.

Gli errori sono restituiti tutti insieme, con riferimento alla singola assegnazione, così che l'amministratore possa correggerli prima di riprovare.

All'emissione il foglio registra: numero, data, luogo, ruolo, delibera, firmatario, versione e hash del modello in uso, copia delle regole di durata, snapshot del socio (nome, cognome, data di nascita, e-mail) e le righe congelate della sezione 3. Le assegnazioni selezionate vengono collegate al foglio.

Prima dell'emissione è disponibile un'anteprima PDF senza numero, con filigrana `ANTEPRIMA`, che non viene salvata.

### Consegne successive

Una nuova assegnazione a un socio con foglio `ISSUED` viene collegata automaticamente a quel foglio come variazione `C`. Si applicano le stesse regole di scadenza dell'emissione, con le regole di durata registrate sul foglio. L'amministratore può collegare come variazione anche un'assegnazione esistente esclusa all'emissione.

### Rinnovo

Azione disponibile sulle assegnazioni a scadenza, con quantità in carico, di un foglio `ISSUED`. Richiede nuova data di scadenza, numero e data della delibera del Consiglio e data del rinnovo (predefinita a oggi).

Regole, con le regole di durata registrate sul foglio:

- la data del rinnovo non è successiva alla scadenza in corso, perché l'art. 4 esclude il rinnovo dopo la scadenza;
- la data della delibera non è successiva alla data del rinnovo;
- la nuova scadenza è successiva alla scadenza in corso e non supera `scadenza in corso + renewalMaxMonths`;
- la nuova scadenza non supera `prima consegna + totalMaxMonths`, dove la prima consegna è la data di assegnazione più remota tra le assegnazioni del foglio con la stessa matricola (confronto senza distinzione tra maiuscole e minuscole e senza spazi esterni) oppure, senza matricola, la data di assegnazione corrente.

Il rinnovo aggiorna la scadenza dell'assegnazione, crea una revisione con il nuovo motivo `RENEWAL`, che richiede una nuova presa visione, e avvia un nuovo ciclo dei promemoria di scadenza secondo le regole già esistenti.

Per le assegnazioni collegate a un foglio emesso la scadenza non è modificabile tramite l'aggiornamento ordinario dell'assegnazione, ma solo tramite rinnovo. Il regime di comodato di un oggetto non è modificabile se l'oggetto è in un foglio `ISSUED`.

### Sostituzione

Azione disponibile sulle assegnazioni di un foglio `ISSUED`. In un'unica transazione:

1. completa la riconsegna dell'assegnazione sostituita per l'intera quantità in carico, con stato e note di conservazione indicati dall'amministratore e motivo `SUBSTITUTION`;
2. crea una nuova assegnazione del bene sostitutivo, collegata al foglio, con riferimento all'assegnazione sostituita.

Per il bene sostitutivo decorrono nuovi termini (art. 4): la scadenza segue le regole della consegna.

### Riconsegne

Restano quelle esistenti: apertura da utente o amministratore, chiusura solo da amministratore. Le riconsegne completate compaiono nelle sezioni 8 e 9 secondo la regola sulle riconsegne finali.

### Chiusura

Disponibile quando tutte le assegnazioni del foglio sono `RETURNED` o `CANCELLED`. L'amministratore registra:

- esito: `COMPLIANT` (nessun addebito) oppure `CHARGES` (danni o mancanze);
- con esito `CHARGES`: importo in euro, maggiore di zero, e termine di pagamento;
- nome e cognome di chi riceve i beni per l'Associazione, predefinito all'amministratore corrente.

Il foglio passa a `CLOSED` e la sezione 9 viene precompilata.

### Definizione

Il foglio passa a `SETTLED`:

- con esito `COMPLIANT`, quando l'amministratore conferma la firma del verbale di riconsegna oppure, in assenza di conferma, automaticamente 30 giorni dopo la chiusura (valore configurabile con la proprietà applicativa `application.inventory.loan-sheet.auto-settle-days`; il job gira ogni notte secondo `application.inventory.loan-sheet.settle-cron`, predefinito `0 15 3 * * *`);
- con esito `CHARGES`, quando l'amministratore conferma che l'addebito è stato definito. Non è prevista la definizione automatica.

Alla definizione vengono rimossi i dati personali del foglio (vedi [Conservazione e GDPR](#conservazione-e-gdpr)). Il PDF non è più scaricabile.

### Annullamento

Un foglio `ISSUED` può essere annullato da un amministratore indicando una motivazione obbligatoria. Le assegnazioni vengono scollegate e tornano disponibili per un nuovo foglio; rinnovi e revisioni già registrati restano nello storico dell'assegnazione. I PDF già generati del foglio annullato vengono cancellati.

## Modello dati e migrazioni

### Schema pubblico

Changelog in `master.xml`:

- `tenants.phone` (`VARCHAR(64)`, facoltativo);
- `tenants.pec` (`VARCHAR(255)`, facoltativo).

I due campi sono gestiti nella pagina di dettaglio del tenant, con le stesse autorizzazioni degli altri dati anagrafici del tenant.

### Schema del tenant

Changelog in `tenant-master.xml`. Tutte le nuove tabelle `inventory_*` contengono i campi di audit `deleted`, `insert_date`, `insert_by`, `edit_date`, `edit_by`. Le tabelle modificabili hanno anche `entity_version`. Le entità estendono `AuditedEntity` o `TenantAuditedEntity` come le altre entità inventario.

Modifiche a tabelle esistenti:

| Tabella | Colonna | Tipo | Note |
| --- | --- | --- | --- |
| `inventory_item` | `brand_model` | `VARCHAR(255)` | facoltativo |
| `inventory_item` | `serial_number` | `VARCHAR(128)` | facoltativo, non univoco |
| `inventory_item` | `loan_type` | `VARCHAR(32)` | `FIXED_TERM` o `PRECARIOUS`; nullo per gli oggetti esistenti, obbligatorio per l'inclusione in un foglio |
| `inventory_assignment` | `accessories` | `VARCHAR(500)` | facoltativo |
| `inventory_assignment` | `loan_sheet_id` | `BIGINT` | FK verso `inventory_loan_sheet`, nullo se non collegata |
| `inventory_assignment` | `replaces_assignment_id` | `BIGINT` | FK verso `inventory_assignment`, per le sostituzioni |
| `inventory_return` | `reason` | `VARCHAR(32)` | `STANDARD` (predefinito) o `SUBSTITUTION` |

Il valore concordato non è un dato dell'assegnazione: è il valore unitario stimato dell'oggetto, usato solo se la valuta è EUR. Per questo `inventory_assignment` non ha una colonna propria; il valore è copiato solo sulle righe congelate del foglio (`inventory_loan_sheet_line.agreed_unit_value`).

Nuove tabelle:

**`inventory_loan_template`**: versioni immutabili del modello. `id`, `version_number` (progressivo dei modelli caricati, nullo per le copie del predefinito), `origin` (`DEFAULT`, `CUSTOM`), `default_label` (etichetta del predefinito, solo per `DEFAULT`), `file_name`, `content` (`TEXT`), `content_hash` (SHA-256 esadecimale del contenuto), audit: `insert_by` e `insert_date` sono autore e data dell'attivazione. Indici univoci parziali su `(origin, content_hash)` e su `version_number` per le righe non cancellate; un vincolo `CHECK` richiede il numero di versione per i modelli caricati e l'etichetta, senza numero, per le copie del predefinito.

**`inventory_loan_settings`**: impostazioni del foglio, una sola riga non cancellata per tenant, garantita da un indice univoco parziale. È creata al primo uso con le regole di durata iniziali. `active_template_id` (FK verso `inventory_loan_template`, nullo quando si usa il modello predefinito), `min_months`, `max_months`, `renewal_max_months`, `total_max_months`, audit ed `entity_version`. Vincoli `CHECK` sulle regole di durata.

**`inventory_loan_sheet`**:

- identificazione: `id`, `sheet_year`, `sheet_sequence`, `sheet_number` (univoco), `status`;
- socio: `user_index`, `user_keycloak_id`, `member_name`, `member_last_name`, `member_birth_date`, `member_email`, `holder_role` (`MEMBER`, `GUARDIAN`);
- emissione: `issued_on`, `place`, `authorization_reference`, `authorization_date`, `association_signatory_name`, `template_id` (FK), `template_hash`;
- regole di durata copiate all'emissione: `min_months`, `max_months`, `renewal_max_months`, `total_max_months`;
- chiusura: `closed_at`, `closed_by`, `return_outcome` (`COMPLIANT`, `CHARGES`), `charge_amount`, `charge_due_date`, `received_by_name`;
- definizione e annullamento: `settled_at`, `settled_by`, `personal_data_purged_at`, `cancelled_at`, `cancelled_by`, `cancellation_reason`;
- audit ed `entity_version`.

Indice univoco parziale su `user_index` per `status = 'ISSUED'` e `deleted = false`. Vincoli `CHECK` che richiedono importo e termine quando l'esito è `CHARGES` e regole di durata coerenti.

**`inventory_loan_sheet_line`**: righe congelate della sezione 3: `id`, `sheet_id`, `line_number`, `assignment_id`, `item_name`, `brand_model`, `serial_number`, `inventory_number`, `accessories`, `quantity`, `agreed_unit_value` (valore stimato dell'oggetto all'emissione, se in euro), `condition_status`, `condition_notes`, `loan_type`, `expiration_date`, audit. Vincolo univoco `(sheet_id, line_number)`.

**`inventory_loan_sheet_counter`**: `sheet_year` (PK), `last_sequence`, audit.

**`inventory_assignment_renewal`**: `id`, `assignment_id`, `sheet_id`, `renewed_on`, `previous_expiration_date`, `new_expiration_date`, `authorization_reference`, `authorization_date`, audit.

**`inventory_loan_sheet_export`**: `id`, `sheet_id`, `media_asset_id` (nullo dopo la rimozione del file), `generated_by`, `generated_at`, audit. Traccia ogni PDF generato e salvato.

### Revisioni e presa visione

Lo snapshot canonico della revisione include `brandModel`, `serialNumber` e `loanType` dell'oggetto e `accessories` dell'assegnazione, **solo se valorizzati**. Il valore concordato coincide con il valore stimato dell'oggetto, già presente nello snapshot. Così gli hash delle revisioni esistenti restano riproducibili. La modifica di uno di questi campi crea una nuova revisione secondo la regola già esistente per i dati concordati.

`InventoryRevisionReason` acquisisce il valore `RENEWAL`.

### Compatibilità dei dati esistenti

- Gli oggetti esistenti non hanno regime di comodato: l'emissione di un foglio che li include è rifiutata finché non viene impostato.
- Le assegnazioni esistenti non sono validate retroattivamente: le regole di scadenza si applicano quando entrano in un foglio o quando la loro scadenza viene modificata.
- La tabella `inventory_report_export` del proforma rimosso è eliminata e i media dei PDF già generati sono marcati come eliminati, così la pulizia dei media ne rimuove i file.

## API REST

Tutti gli endpoint sono protetti da `@RequiresTenantFeature(TenantFeature.INVENTORY)`. Gli identificativi sono sempre verificati nel contesto del tenant ricavato dal token.

### Modello del foglio (admin e super admin)

- `GET /api/inventory/loan-template`: modello in uso, etichetta del predefinito, regole di durata, storico dei modelli caricati, catalogo delle variabili e limiti del file;
- `PUT /api/inventory/loan-template/durations`: aggiorna le regole di durata, con `entityVersion` per il controllo di concorrenza;
- `POST /api/inventory/loan-template/check`: verifica un file senza salvarlo e restituisce le segnalazioni;
- `POST /api/inventory/loan-template/preview`: PDF di anteprima di un file non salvato;
- `POST /api/inventory/loan-template`: verifica il file, crea la versione e la rende il modello in uso;
- `POST /api/inventory/loan-template/versions/{id}/activate`: rimette in uso una versione dello storico;
- `POST /api/inventory/loan-template/use-default`: torna al modello predefinito;
- `GET /api/inventory/loan-template/default/file` e `GET /api/inventory/loan-template/default/preview`: download e anteprima del modello predefinito;
- `GET /api/inventory/loan-template/versions/{id}/file` e `GET /api/inventory/loan-template/versions/{id}/preview`: download e anteprima di una versione.

Il file viaggia nel corpo JSON come `{ "fileName": "…", "content": "…" }`: il browser legge il file scelto e ne invia il testo. I download rispondono con `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff` e `Content-Security-Policy: sandbox`, in modo che un modello caricato non venga mai visualizzato come pagina nell'origine di Taurus.

### Fogli (admin e super admin)

- `GET /api/inventory/users/{userIndex}/loan-sheets`: fogli del socio;
- `GET /api/inventory/users/{userIndex}/loan-sheets/proposal`: assegnazioni proponibili, valori predefiniti e problemi bloccanti;
- `GET /api/inventory/users/{userIndex}/loan-expiration-range?deliveredOn=`: scadenze ammesse per un comodato a scadenza consegnato in `deliveredOn`, oggi se assente, con il numero del foglio emesso le cui regole si applicano, nullo per le regole correnti del tenant;
- `POST /api/inventory/users/{userIndex}/loan-sheets/preview`: anteprima PDF senza persistenza;
- `POST /api/inventory/users/{userIndex}/loan-sheets`: emissione;
- `GET /api/inventory/loan-sheets/{id}`: dettaglio con righe, variazioni e stato;
- `GET /api/inventory/loan-sheets/{id}/pdf`: generazione, salvataggio e download del PDF corrente;
- `POST /api/inventory/loan-sheets/{id}/assignments/{assignmentId}`: collegamento di un'assegnazione come variazione;
- `POST /api/inventory/assignments/{id}/renewals`: rinnovo;
- `POST /api/inventory/assignments/{id}/substitution`: sostituzione;
- `POST /api/inventory/loan-sheets/{id}/close`: chiusura;
- `POST /api/inventory/loan-sheets/{id}/settle`: definizione;
- `POST /api/inventory/loan-sheets/{id}/cancel`: annullamento.

### Fogli personali

- `GET /api/user/inventory/loan-sheets`: fogli propri non annullati;
- `GET /api/user/inventory/loan-sheets/{id}/pdf`: PDF del proprio foglio, solo se `ISSUED` o `CLOSED`.

`SecurityConfiguration` autorizza esplicitamente soltanto questi due percorsi `GET` nel namespace personale, mantenendo il `denyAll` sugli altri. Il servizio verifica che il foglio appartenga all'utente del token.

### Errori

Gli errori seguono lo [Standard degli errori applicativi](application-errors-standard.md): il backend restituisce la chiave nel campo `message` e i dati del caso nel campo `args` (numero del foglio, nome dell'oggetto, date e mesi ammessi, problemi del modello), mai un testo; il frontend costruisce il messaggio dal proprio catalogo. Le chiavi sono `inventory.loanSheet.*`, `inventory.loanTemplate.*`, `inventory.loanSettings.*`, `inventory.loanDuration.*` e `inventory.renewal.*`; l'elenco completo, con parametri e messaggi, è nel catalogo dello standard. Per esempio:

| Chiave | HTTP | Caso |
| --- | --- | --- |
| `inventory.loanSheet.alreadyIssued` | 409 | il socio ha già un foglio emesso |
| `inventory.loanSheet.validation` | 422 | problemi di emissione, elencati tutti in `args.issues` con bene e codice della regola |
| `inventory.loanSheet.roleRequired` | 422 | data di nascita assente e ruolo dell'Affidatario non indicato |
| `inventory.loanSheet.<violazione>` | 422 | consegna successiva o collegamento che violano le regole del foglio (`missingLoanType`, `missingExpiration`, `expirationOutOfRange`, `precariousExpiration`) |
| `inventory.loanDuration.missingExpiration`, `expirationOutOfRange` | 422 | assegnazione, o modifica della scadenza, di un oggetto a scadenza senza foglio emesso che viola le regole di durata correnti del tenant |
| `inventory.loanSheet.expirationLocked`, `quantityLocked`, `loanTypeLocked`, `assignmentLocked`, `returnLocked` | 409 | modifica ordinaria di un bene collegato a un foglio |
| `inventory.loanSheet.notClosable` | 409 | beni ancora in carico |
| `inventory.loanSheet.chargesRequired` | 422 | esito con addebito senza importo o termine |
| `inventory.loanSheet.notClosed`, `notIssued`, `notLinked` | 409 | operazione non ammessa nello stato del foglio o per un bene senza foglio |
| `inventory.loanSheet.purged` | 410 | dati personali rimossi, PDF non disponibile |
| `inventory.loanSheet.tooLarge` | 413 | PDF oltre 20 MB |
| `inventory.loanTemplate.invalid` | 422 | anteprima o attivazione di un file con segnalazioni bloccanti, fino a cinque in `args.issues` con codice e riga |
| `inventory.loanTemplate.tooLarge` | 413 | file oltre 2 MB |
| `inventory.loanTemplate.notFound` | 404 | versione inesistente |
| `inventory.loanSettings.invalid` | 422 | regole di durata incoerenti |
| `inventory.loanSettings.versionConflict` | 409 | regole di durata modificate da un'altra richiesta |
| `inventory.renewal.afterExpiration` | 422 | rinnovo richiesto dopo la scadenza |
| `inventory.renewal.renewalLimitExceeded`, `totalLimitExceeded` | 422 | superato il limite del rinnovo o quello di durata complessiva |

Le verifiche del modello e l'anteprima di emissione restituiscono i problemi come dati, con la stessa forma: `code` e `args`, senza testo. Il frontend li descrive con lo stesso catalogo dei messaggi degli errori.

## Backend

Componenti principali, nel package `service.loansheet`:

- `InventoryLoanTemplateService`: modello in uso, verifica, anteprima, attivazione, storico, ritorno al predefinito, copia immutabile del predefinito all'emissione, regole di durata;
- `LoanTemplateDefaults`: caricamento del modello predefinito e della sua etichetta dalle risorse;
- `LoanSheetVariables`: catalogo delle variabili con tipo, descrizione ed esempio, usato da validazione, costruzione dei dati e guida della pagina;
- `LoanTemplateValidator`: segnalazioni bloccanti e avvisi del file;
- `LoanSheetModelFactory`: costruzione dei dati del modello a partire dal contenuto del foglio, con formattazione di date e importi e normalizzazione dei caratteri;
- `LoanDurationPolicy`: regole di scadenza all'emissione, alla consegna successiva e al rinnovo;
- `InventoryLoanSheetPolicy`: regole che le operazioni ordinarie dell'inventario rispettano per i beni collegati a un foglio (collegamento delle consegne successive, blocco di scadenza, quantità, regime, eliminazioni);
- `InventoryLoanSheetService`: emissione, collegamento, sostituzione, rinnovo, chiusura, definizione, annullamento, PDF;
- `LoanSheetRegisterBuilder`: righe delle sezioni 8 e 9;
- `LoanSheetPdfRenderer`: compilazione del modello con i dati, conversione in PDF e filigrana delle anteprime;
- `LoanSheetSamples`: dati fittizi per le anteprime del modello;
- `InventoryLoanSheetRetentionScheduler`: definizione automatica dei fogli `COMPLIANT`.
- `LoanSheetTenantData`: dati del tenant stampati sul foglio; il logo viene scaricato con `TenantLogoLoader` solo se il modello usa `associazione.logo`;
- `LoanSheetText`: formattazione di date e importi e normalizzazione dei caratteri.

`InventoryService` delega a `InventoryLoanSheetPolicy` i controlli sulle assegnazioni collegate ed espone le operazioni atomiche di rinnovo (`applyRenewal`) e sostituzione (`substitute`), orchestrate da `InventoryLoanSheetService`. `InventoryErasureService` invoca `InventoryLoanSheetService.purgeForUser` nel completamento della cancellazione GDPR.

Le date sono calcolate nel fuso orario del tenant (`TenantTimeZoneService`). L'aggiunta dei mesi usa `LocalDate.plusMonths`, che porta al 28/29 febbraio le consegne del 31 gennaio.

Il foglio non contiene fotografie e si genera in pochi secondi: non è previsto un timeout dedicato. Un PDF oltre 20 MB viene rifiutato.

### Generazione PDF

1. Il modello è compilato con JMustache (`com.samskivert:jmustache`, versione gestita da Spring Boot) e i dati di `LoanSheetModelFactory`, che sono solo mappe, elenchi, testi e valori sì/no.
2. L'HTML risultante è letto con jsoup (`org.jsoup:jsoup`) e convertito in DOM W3C.
3. Il DOM è convertito in PDF con OpenHTMLtoPDF (`io.github.openhtmltopdf:openhtmltopdf-pdfbox`). Si usa la versione 1.1.44, l'ultima basata su PDFBox 3.0.3 come le dipendenze `pdfbox` e `pdfbox-tools` del backend: un aggiornamento di OpenHTMLtoPDF richiede di allinearle.
4. Nelle anteprime PDFBox sovrappone la filigrana `ANTEPRIMA` a ogni pagina.

Il CSS paged media copre in modo nativo intestazione e piè di pagina ricorrenti con `counter(page)` e `counter(pages)`, testo giustificato, tabelle a larghezza fissa con celle unite, intestazioni ripetute e righe non spezzate, salti pagina forzati e titoli tenuti insieme al contenuto successivo, caselle disegnate con bordi CSS.

Vincoli di sicurezza, necessari perché il modello è caricato dal tenant:

- JMustache usa un collettore limitato a mappe ed elenchi, senza riflessione: il modello non può invocare metodi Java né raggiungere oggetti diversi dai dati preparati;
- tutti i valori sono inseriti con escape HTML; l'inserimento senza escape, le inclusioni e il cambio dei delimitatori sono rifiutati dalla validazione;
- il resolver delle risorse di OpenHTMLtoPDF rifiuta qualsiasi URI che non sia un `data:` URI incorporato, in modo che nessun modello possa provocare accessi di rete o al filesystem;
- JavaScript non è supportato;
- dimensione del file e del PDF sono limitate; solo admin e super admin possono caricare un modello;
- il modello risiede nello schema del tenant e non è mai usato per un altro tenant;
- il file scaricato non viene mai visualizzato nell'origine di Taurus (vedi [API REST](#modello-del-foglio-admin-e-super-admin)).

Questi vincoli sono verificati dai test del generatore: valori con markup inseriti come testo, nessun accesso a metodi Java dal modello, risorse esterne ignorate e `data:` URI caricati.

Thymeleaf non è più usato per il foglio. `PdfPageWriter`, usato dagli altri report, resta invariato.

### Impaginazione del modello predefinito

- A4, margini di 2 cm, intestazione e piè di pagina a 1 cm dal bordo;
- corpo 10 pt, interlinea 1,1, Helvetica;
- titoli di sezione 11 pt grassetto con filetto inferiore 0,75 pt `#404040`;
- tabelle con bordo singolo 0,5 pt; celle etichetta e intestazioni di colonna con sfondo `#EDEDED`, testo nero, intestazioni 8 pt grassetto centrate, contenuto 9 pt; nessuna alternanza di colore tra le righe;
- tabelle firma senza bordi;
- pagina nuova prima delle sezioni 3, 6, 7, 8, 9 e 10;
- le date dei dati non vanno a capo.

Larghezze delle colonne, ricavate dalla griglia del modello Word (larghezza utile 17 cm):

| Tabella | Colonne (%) | Altezza riga |
| --- | --- | --- |
| Sezioni 1 e 2 | 21,8 · 28,2 · 19,7 · 30,3 | 21 pt |
| Sezione 3 | 4,2 · 15,6 · 15,6 · 15,6 · 15,6 · 6,2 · 11,4 · 16,0 | 20 pt |
| Sezione 8 | 9,9 · 11,4 · 13,5 · 12,5 · 11,9 · 12,5 · 13,5 · 14,9 | 24 pt |
| Sezione 9 | 5,2 · 17,6 · 15,6 · 10,4 · 35,7 · 15,6 | 20 pt |
| Firme | 50 · 50 | libera |

Le altezze sono minime: una riga con testo più lungo cresce in altezza.

## Frontend

- **Oggetto**: nuovi campi marca/modello, matricola e regime di comodato nel modale di creazione e nel dettaglio. Il regime mostra le due opzioni con la descrizione giuridica, senza preselezione.
- **Assegnazione**: nuovo campo accessori/taglia. Il valore concordato non si inserisce nell'assegnazione: è il valore unitario stimato dell'oggetto, e il relativo campo lo indica. Per gli oggetti a scadenza la data è obbligatoria; il calendario accetta qualsiasi data e sotto il campo indica l'intervallo ammesso con le regole che si applicano, quelle del foglio emesso del socio o quelle di durata del comodato. Se il backend rifiuta la data, il suo messaggio, con l'intervallo corretto, sostituisce l'indicazione finché la data non cambia. Le assegnazioni a scadenza non ancora collegate a un foglio si comportano allo stesso modo nella tabella. La scadenza non è disponibile per gli oggetti in comodato precario collegati a un foglio.
- **Pagina utente (amministrazione)**: sezione **Fogli di affidamento** (`app-inventory-loan-sheets`) con elenco, stato, numero e azioni: emetti (con anteprima PDF nella finestra di emissione), dettaglio con beni e registro, scarica PDF, chiudi, definisci, annulla. Se il socio ha un foglio emesso e beni in carico non collegati, la sezione li elenca con l'azione **Aggiungi al foglio**.
- **Dettaglio oggetto, assegnazioni**: per le assegnazioni collegate a un foglio mostra il numero del foglio, blocca quantità e scadenza e nasconde l'eliminazione; offre **Rinnova** (comodato a scadenza con beni in carico) e **Sostituisci** (beni in carico senza riconsegne aperte), con le regole di durata registrate sul foglio mostrate nella finestra.
- **Modello del foglio**: pagina `/inventory/loan-template`, raggiungibile dal pulsante **Modello del foglio di affidamento** dell'inventario, per admin e super admin. Contiene:
  - **Modello in uso**: predefinito, con etichetta, oppure versione caricata con nome del file e data di attivazione; azioni **Scarica il modello in uso**, **Anteprima PDF**, **Scarica il modello predefinito** e, con un modello caricato in uso, **Torna al modello predefinito**;
  - **Carica un modello**: scelta del file `.html`, esito della verifica con errori e avvisi per riga, **Anteprima PDF** del file e **Attiva modello**, disabilitati in presenza di errori;
  - **Regole di durata del comodato**: i quattro parametri con salvataggio proprio. Il titolo non cita un articolo, perché la numerazione dipende dal modello in uso;
  - **Guida alle variabili**: sintassi, regole del file e tabelle delle variabili per gruppo con tipo, descrizione ed esempio, lette dal catalogo del backend;
  - **Storico dei modelli caricati**: versione, data di attivazione, nome del file e stato, con **Scarica**, **Anteprima PDF** e **Usa questa versione**.
  La vista segue il [Taurus Layout Standard](taurus-layout-standard.md) e la documentazione PrimeNG in `docs/llms-full.md`.
- **I miei oggetti**: riquadro **Fogli di affidamento** con i propri fogli e il download del PDF finché il foglio conserva i dati personali.
- **Tenant**: campi telefono e PEC nella pagina di dettaglio.

## Autorizzazioni e isolamento tenant

| Operazione | Utente autenticato | Admin | Super admin |
| --- | ---: | ---: | ---: |
| Scaricare il PDF dei propri fogli | sì | sì | sì |
| Gestire il modello del foglio e le regole di durata | no | sì | sì |
| Emettere, chiudere, definire e annullare fogli | no | sì | sì |
| Rinnovare e sostituire assegnazioni | no | sì | sì |

Modelli, impostazioni, fogli, contatori ed esportazioni risiedono nello schema del tenant: nessuna query può restituire dati di un altro tenant. Il tenant è ricavato esclusivamente dal token.

## Conservazione e GDPR

Il foglio salva in Taurus soltanto dati già presenti nell'anagrafica del socio (nome, cognome, data di nascita, e-mail). I dati da compilare a mano restano sulla copia cartacea.

In coerenza con la lettera f) dell'informativa del modello predefinito:

- alla **definizione** del foglio nome e cognome del socio sono sostituiti da «Dati personali rimossi», data di nascita ed e-mail vengono cancellate e tutti i PDF del foglio vengono rimossi: per ogni esportazione si azzera `media_asset_id` e si invoca `MediaService.deleteIfUnreferenced`, che marca il media `DELETED` e cancella il file dallo storage dopo il commit. `MediaRepository` considera `inventory_loan_sheet_export` tra i riferimenti inventario, così la pulizia dei media non rimuove i PDF ancora referenziati;
- restano numero, date, righe dei beni, esito e versione del modello, senza dati personali dell'Affidatario;
- il flusso GDPR esistente (`InventoryErasureService.complete`) rimuove i dati personali di tutti i fogli del socio, sostituisce l'identificativo Keycloak con lo pseudonimo già usato per le assegnazioni e ne rimuove i PDF;
- all'annullamento di un foglio i relativi PDF vengono rimossi.

Il modello caricato non contiene dati personali dei soci, che vengono inseriti solo al momento della generazione del PDF.

I log non contengono nomi, e-mail o contenuto dei modelli: solo identificativi di foglio, versione del modello e tenant.

## Osservabilità

- log strutturati (`inventory_loan_sheet_*`, `inventory_loan_template_*`) per emissione, chiusura, definizione, annullamento, attivazione del modello, ritorno al predefinito, copia del predefinito, modifica delle regole di durata, rinnovo, sostituzione e generazione del PDF, con la dimensione del file;
- le verifiche respinte registrano il numero di segnalazioni bloccanti, senza il contenuto del file;
- i messaggi di diagnostica di OpenHTMLtoPDF sono registrati a livello debug;
- l'esecuzione del job di conservazione registra il numero di fogli definiti per tenant e gli errori per tenant senza interrompere gli altri.

## Test

### Backend – unitari

- `LoanTemplateValidatorTest`: modello predefinito senza segnalazioni, variabili sconosciute e campi di un elenco fuori dalla sezione con numero di riga, sezioni non bilanciate, elenchi usati come testo, inserimento senza escape, inclusioni e cambio dei delimitatori, risorse esterne in `src`, `href`, `url(...)`, `@import` e `@font-face`, `data:` URI e link ammessi, elementi non ammessi con numero di riga, file vuoto o non UTF-8, avvisi su font, caratteri non stampabili e variabili consigliate assenti;
- `LoanSheetModelFactoryTest`: precompilazione per socio maggiorenne e per genitore o tutore, i tre casi di `durata.scadenzaStrumenti`, righe vuote degli elenchi, recapiti e dati fiscali, verbale con esito e addebito, valori assenti come stringhe vuote, corrispondenza tra dati prodotti e catalogo delle variabili;
- `LoanSheetPdfRendererTest`: modello predefinito con dati di esempio e con un foglio di 18 righe, escape dei valori con markup, nessun accesso a metodi o oggetti fuori dai dati, risorse esterne non caricate, `data:` URI caricati, filigrana solo nelle anteprime e su ogni pagina;
- `LoanDurationPolicyTest`: regime e scadenza obbligatori, estremi dell'intervallo, 31 gennaio + 1 mese, rinnovo dopo la scadenza, delibera successiva al rinnovo, limite del rinnovo e durata complessiva;
- `LoanSheetRegisterBuilderTest`: ordinamento cronologico di `C`, `R`, `S` e `N`, esclusione delle riconsegne `SUBSTITUTION`, distinzione tra riconsegne parziali e finali.

### Backend – servizi

- `InventoryLoanSheetServiceTest`: emissione con numerazione annuale, collegamento delle assegnazioni, versione del modello e regole di durata registrate sul foglio, secondo foglio emesso rifiutato, emissione rifiutata per regime mancante o scadenza fuori intervallo, ruolo obbligatorio senza data di nascita, ruolo proposto al compimento dei 18 anni, chiusura solo con tutti i beni rientrati, addebito con importo e termine, definizione manuale e automatica con rimozione dei dati e dei PDF, PDF rifiutato dopo la definizione, PDF di un foglio generato con la propria versione dopo un cambio di modello, annullamento, rinnovo entro le regole del foglio dopo una modifica delle regole del tenant, rinnovo di un comodato precario rifiutato, cancellazione GDPR;
- `InventoryLoanTemplateServiceTest`: creazione delle impostazioni con le regole iniziali, attivazione con nuova versione e nome del file ripulito, file identico che riattiva la versione esistente, attivazione e anteprima rifiutate con segnalazioni bloccanti, file oltre 2 MB, ritorno al predefinito, copia del predefinito creata o riusata a parità di hash, copia del predefinito non attivabile come versione, regole di durata incoerenti e conflitto di versione;
- `InventoryLoanSheetPolicyTest`: collegamento delle consegne successive con le regole del foglio, scadenza degli oggetti a scadenza controllata con le regole correnti del tenant senza foglio emesso, scadenza libera per comodato precario e regime non indicato, modifica della scadenza di un'assegnazione non collegata contata dal giorno dell'assegnazione, intervallo ammesso con le regole del foglio o del tenant, blocco di scadenza, quantità, regime ed eliminazioni, fogli annullati che non vincolano;
- `InventoryServiceTest`: collegamento automatico al foglio emesso, nuovi campi nello snapshot di revisione e snapshot invariato per le assegnazioni senza dati di comodato;
- `LoanSheetSecurityConfigurationTest`: un utente accede soltanto all'elenco e al PDF dei propri fogli; gli altri percorsi personali e tutti gli endpoint amministrativi, compresi quelli del modello e dell'intervallo di scadenza, gli sono negati.

### Backend – PDF e integrazione

- il test del modello predefinito genera il PDF ed estrae il testo con PDFBox: intestazione e piè di pagina su ogni pagina con `Pagina X di Y` coerente, sezioni 3 e 6 in testa alla rispettiva pagina, caratteri `’ « » €` senza sostituzioni, variabili risolte, righe oltre il minimo della tabella. I PDF di prova sono salvati in `target/loan-sheet/`;
- `InventoryLoanSheetMigrationIT` applica le migrazioni su PostgreSQL 17 (Testcontainers) e verifica colonne, tabelle, valore predefinito del motivo della riconsegna, indici univoci parziali (un foglio emesso per socio, versioni del modello), vincoli di coerenza su durate e addebiti, ed eliminazione del registro del proforma con i media dei suoi PDF marcati come eliminati, salvo quelli usati da altre tabelle;
- `TenantSchemaProvisioningServiceIT` continua a verificare il provisioning dello schema tenant con i nuovi changelog.

La concorrenza di emissioni e attivazioni simultanee è serializzata da lock advisory per schema e garantita dagli indici univoci verificati dall'integrazione; non è coperta da un test concorrente dedicato.

### Frontend

- `inventory-loan-sheet.service.spec.ts`: endpoint di fogli, modello, intervallo di scadenza e area personale, con download come blob;
- `loan-template.component.spec.ts`: lettura e verifica del file scelto, anteprima e attivazione bloccate con errori, attivazione confermata, file troppo grande non inviato, salvataggio delle regole di durata, nomi dei file scaricati, sintassi delle variabili mostrate;
- `inventory-loan-sheet-issue-dialog.component.spec.ts`: problemi per assegnazione e ruolo obbligatorio senza data di nascita;
- `inventory-renewal-dialog.component.spec.ts`: limiti del rinnovo, delibera precedente al rinnovo, rinnovo dopo la scadenza;
- `local-date.spec.ts`: date locali, somma dei mesi come nel backend ed etichette del foglio.

### Verifica manuale

Stampa A4 del foglio generato con il modello predefinito e confronto con la stampa del modello Word, sovrapponendo le pagine. Il confronto a video usa i PDF di prova in `target/loan-sheet/` e l'esportazione PDF del modello. Va inoltre caricato un modello modificato a partire dal predefinito scaricato, verificandone anteprima, attivazione ed emissione di un foglio.

## Criteri di accettazione

- il PDF generato con il modello predefinito riproduce le dieci sezioni del modello Word con la stessa sequenza, gli stessi salti pagina, le stesse tabelle e gli stessi testi;
- nessun carattere del modello predefinito viene stampato in modo errato;
- i campi anagrafici non gestiti da Taurus sono stampati in bianco;
- ogni tenant può scaricare il modello predefinito e il proprio, caricare un modello, verificarlo, vederne l'anteprima e attivarlo senza influenzare gli altri tenant;
- la pagina del modello spiega la sintassi ed elenca tutte le variabili disponibili con tipo, descrizione ed esempio;
- un modello con sintassi non valida, variabili sconosciute, costrutti non ammessi o risorse esterne non può essere attivato;
- un modello non può eseguire codice né accedere a rete, filesystem o dati diversi dalle variabili;
- una versione attivata non è modificabile, e ogni foglio indica la versione e l'hash del modello usato e viene sempre generato con quella versione;
- l'emissione è rifiutata per oggetti senza regime, scadenze mancanti o fuori intervallo, scadenze su beni in comodato precario;
- le consegne successive compaiono come `C`, le sostituzioni come `S`, i rinnovi come `N` con gli estremi della delibera, le riconsegne parziali come `R`;
- un rinnovo oltre le regole di durata registrate sul foglio è rifiutato;
- la sezione 9 è precompilata solo per un foglio chiuso;
- alla definizione i dati personali del foglio e i relativi PDF sono rimossi;
- un utente non amministratore scarica solo i propri fogli;
- il proforma PDF non è più disponibile, né per l'amministratore né per il socio.

## Piano di implementazione

1. **Fase 0 – Verifiche preliminari**: compatibilità di OpenHTMLtoPDF con PDFBox 3.0.3; JMustache con collettore senza riflessione; lettura con jsoup; blocco delle risorse con l'eccezione dei `data:` URI.
2. **Fase 1 – Modello dati**: migrazioni, nuovi campi di oggetto, assegnazione, riconsegna e tenant, aggiornamento dello snapshot di revisione, form del frontend.
3. **Fase 2 – Modello del foglio**: modello predefinito, catalogo delle variabili, validazione, versioni, regole di durata, API e pagina del modello.
4. **Fase 3 – Emissione e PDF**: emissione, anteprima con filigrana, generazione, download amministrativo e personale.
5. **Fase 4 – Variazioni**: collegamento automatico, rinnovo, sostituzione, registro delle variazioni.
6. **Fase 5 – Chiusura e conservazione**: chiusura, verbale, definizione manuale e automatica, estensione del flusso GDPR.
7. **Fase 6 – Documentazione**: aggiornamento di [Gestione inventario tenant](inventory-management-spec.md) (regime di comodato accanto all'assenza di tassonomia, deroga all'intestazione comune, nuovi endpoint), della categoria media `inventory-loan-sheets` in [Gestione centralizzata dei media](media-asset-spec.md), del [Runbook di test](runbook-test.md) e del catalogo.

Il rollback applicativo preserva le nuove tabelle, perché contengono lo storico contrattuale. Le colonne aggiunte sono tutte facoltative e compatibili con la versione precedente del backend.
