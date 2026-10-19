# Lettura del testo nel servizio OMR

Il documento progetta lo spostamento dell'OCR delle pagine dal backend al servizio `taurus-omr`, che lo offre con una nuova API interna. Oggi l'OCR serve a suggerire gli strumenti delle parti ricavate da un PDF scansionato e usa Tesseract tramite tess4j, che richiede la libreria nativa sulla macchina del backend. Il servizio OMR contiene già un motore OCR open source, RapidOCR, installato insieme a homr: la lettura del testo diventa una funzione del servizio e il backend non ha più dipendenze native.

Il documento non cambia l'uso del testo letto. Il suggerimento degli strumenti che il tenant non ha, con le nuove regole di confronto, è progettato a parte in [Strumenti da aggiungere dai PDF delle tracce](track-instrument-suggestions-spec.md).

Estende l'[Elaborazione asincrona dei PDF delle tracce](track-pdf-processing-spec.md) e il servizio descritto in [Ascolto e correzione delle parti](track-score-playback-spec.md#servizio-omr); gli errori seguono lo [Standard degli errori applicativi](application-errors-standard.md).

## Stato del documento

ID catalogo: `omr-text-recognition`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

Le scelte sono fissate in [Decisioni prese](#decisioni-prese).

## Problema

- `PdfProcessingService` chiama Tesseract con tess4j quando il PDF non contiene testo selezionabile, per leggere il nome dello strumento sulla prima pagina di ogni parte.
- tess4j carica `libtesseract` dal sistema. In locale va installato Tesseract con i dati `ita` ed `eng` e indicato il loro percorso in `application.tesseract.data-path`, che per default punta a `C:\Program Files\Tesseract-OCR\tessdata`.
- L'immagine Docker del backend (`eclipse-temurin:17-jre`) non installa Tesseract. Attivando l'OCR in produzione la libreria non viene trovata, l'errore finisce nel log e la parte resta senza suggerimenti: l'OCR è spento di default e di fatto inutilizzabile in produzione.
- L'immagine `taurus-omr` contiene già i dati Tesseract, usati da Audiveris, e RapidOCR con i modelli PP-OCR verificati in `models.sha256`, usato da homr per il titolo. Installare Tesseract anche nel backend significherebbe mantenere due motori OCR in due immagini.

## Obiettivo

- Un solo posto per l'elaborazione delle immagini con motori esterni: il servizio `taurus-omr`, interrogato tramite API.
- Nessuna dipendenza nativa nel backend: build, test e avvio richiedono solo Java.
- Suggerimento degli strumenti funzionante anche in produzione, con lo stesso servizio già usato dal riconoscimento delle parti.
- Nessun costo e nessun accesso a Internet durante l'esecuzione: i modelli stanno nell'immagine e sono verificati con SHA-256.

## Fuori scope

- Le regole di confronto tra testo letto e strumenti (`matchInstruments` e `normalize`) e il suggerimento degli strumenti che il tenant non ha: progettati in [Strumenti da aggiungere dai PDF delle tracce](track-instrument-suggestions-spec.md).
- Il raggruppamento delle pagine in parti dai segnalibri del PDF e la lettura del testo selezionabile (livello 1).
- Altri usi del testo letto, come proporre la descrizione della parte quando il PDF non ha segnalibri: da valutare dopo aver usato questa funzione.
- L'OCR di altri documenti, come ricevute, fogli di affidamento o PDF da rendere ricercabili.
- La lettura di testo scritto a mano.
- La lettura del testo durante il riconoscimento delle parti: Audiveris continua a usare i propri dati Tesseract.
- Motori che richiedono una GPU e modelli visione-linguaggio.

## Contesto attuale

Il flusso parte da `Receiver`, consumatore della coda `upload.files`, e prosegue in `PdfProcessingService.buildSheets`:

1. il PDF viene convertito in immagini PNG a 300 DPI, applicando le pagine escluse e i ritagli delle annotazioni;
2. le pagine sono raggruppate in parti dai segnalibri del PDF; senza segnalibri l'intero PDF è una parte sola;
3. per ogni parte si legge il testo selezionabile nel 20% sinistro della prima pagina (livello 1);
4. solo se il livello 1 non dà righe utili e `application.tesseract.enabled` è attivo, Tesseract legge l'intera prima pagina non esclusa della parte;
5. le righe utili (almeno 3 caratteri, non solo numeri o numeri romani) vengono confrontate con i nomi degli strumenti del tenant, ignorando maiuscole, accenti, numeri finali e un «in …» finale; gli strumenti trovati vengono suggeriti e la parte viene marcata `needsReview`.

Un errore dell'OCR non interrompe l'elaborazione: la parte resta senza suggerimenti.

Indirizzo e chiave del servizio OMR stanno oggi sotto `application.score-playback.omr`, nelle variabili `SCORE_PLAYBACK_OMR_URL` e `SCORE_PLAYBACK_OMR_API_KEY`, perché finora il servizio serviva solo l'ascolto delle parti.

## Scelte di fondo

### A. Dove gira l'OCR

| Opzione | Pro | Contro |
| --- | --- | --- |
| Tesseract nel backend con tess4j (situazione attuale) | nessun servizio coinvolto | libreria nativa da installare in locale e nell'immagine; due motori OCR in due immagini |
| Tesseract nel backend con le librerie native nelle dipendenze Maven (JavaCPP) | niente da installare | decine di MB in più nel JAR, dati delle lingue da scaricare e verificare, immagini elaborate in due posti |
| Nuovo servizio OCR dedicato | isolamento completo | un container, una chiave, un rilascio e un runbook in più per una funzione piccola |
| **Servizio `taurus-omr`** | motore e modelli già nell'immagine; autenticazione, limiti, test e pubblicazione già esistenti | il suggerimento degli strumenti dipende dal servizio OMR |

**Scelta: il servizio `taurus-omr`.** La dipendenza dal servizio è accettabile: il suggerimento è facoltativo, va sempre rivisto e senza servizio l'utente assegna gli strumenti a mano, come fa già oggi in produzione.

### B. Motore

| Motore | Licenza | Peso nell'immagine | Note |
| --- | --- | --- | --- |
| **RapidOCR 3.10.0 con modelli PP-OCR** | Apache-2.0 | nessuno: è già installato con homr | gira su CPU con ONNX Runtime; prima trova le zone di testo e poi le legge, in genere più adatto a pagine piene di pentagrammi e note |
| Tesseract, programma e libreria | Apache-2.0 | pochi MB; i dati `ita` ed `eng` 4.1.0 ci sono già | legge la pagina come un documento di testo: su uno spartito in genere produce molto rumore |
| PaddleOCR | Apache-2.0 | alcune centinaia di MB | gli stessi modelli di RapidOCR, con un framework più pesante |
| EasyOCR, docTR | Apache-2.0 | 1–2 GB (PyTorch) | sproporzionati per leggere il nome di uno strumento |
| Surya | codice GPL-3.0, modelli con limiti d'uso commerciale | pesante | escluso per la licenza |
| Modelli visione-linguaggio (olmOCR, PaddleOCR-VL e simili) | varie | molto lenti senza GPU | contrari al vincolo di costo zero |

**Scelta: RapidOCR**, con i modelli già presenti nell'immagine (decisione 3).

### C. Divisione dei compiti

**Il servizio legge, il backend interpreta.** Il servizio restituisce tutte le righe lette, con posizione e affidabilità, senza sapere nulla di strumenti. Il backend sceglie le righe utili per zona e affidabilità e le confronta con gli strumenti del tenant. L'API resta così riusabile per altri usi, e le regole di dominio restano nel backend, dove sono oggi.

## Decisioni prese

Le scelte discusse e fissate il 20/10/2026, con il motivo per cui l'alternativa è stata scartata. Le misure della zona e la soglia sono valori iniziali: la [prova preliminare](#piano-di-adozione-e-compatibilità) può correggerli prima dell'implementazione.

### 1. Zona letta

Una riga conta se il centro del suo rettangolo cade **nel 20% sinistro della pagina oppure nel 15% superiore**. Il solo margine sinistro, come il livello 1, non vede i nomi scritti in alto al centro o a destra; l'intera pagina, come oggi, porta dentro titoli, indicazioni di tempo e diciture dell'editore, quindi più suggerimenti sbagliati.

### 2. Quando leggere

**Resta la regola attuale**: la lettura parte solo se il livello 1 non dà righe utili. Su un PDF digitale l'OCR rileggerebbe lo stesso testo già estratto, con un costo e nessun vantaggio; il caso di un nome disegnato come grafica accanto ad altro testo selezionabile è raro.

### 3. Modello di lettura

**I modelli già presenti nell'immagine**: rilevamento e lettura PP-OCRv6 small e classificatore dell'orientamento. Il modello PP-OCR per le lingue latine si aggiunge solo se la prova preliminare mostra errori che la normalizzazione non assorbe; richiederebbe un file nuovo nell'immagine, una riga in `models.sha256` e un nuovo test di accuratezza. Gli accenti pesano poco, perché `normalize()` li toglie prima del confronto.

### 4. Esecuzione del motore

**In un processo separato** (`python -m taurus_omr.text`), come Audiveris e homr. Nel processo del servizio una lettura bloccata non si potrebbe interrompere e un errore del motore coinvolgerebbe l'intero servizio. Il costo è il caricamento dei modelli a ogni richiesta: la prova preliminare lo misura e, oltre 5 secondi, la decisione va rivista.

### 5. Slot

**Slot propri per le letture**, separati da quelli dei riconoscimenti. Con slot condivisi e la concorrenza predefinita di 1, una lettura di pochi secondi aspetterebbe un riconoscimento che può durare minuti, e l'elaborazione del PDF con lei.

### 6. Soglia di affidabilità

**0,5 come valore iniziale** di `application.pdf-processing.ocr.min-confidence`, da calibrare con la prova preliminare e con il corpus di accuratezza. È una proprietà, così si corregge senza una nuova versione.

### 7. Nome della configurazione

**Indirizzo, chiave e attesa della connessione passano a nomi neutri**: `application.omr.url`, `application.omr.api-key` e `application.omr.connect-timeout`, con le variabili `TAURUS_OMR_URL` e `TAURUS_OMR_API_KEY`. Il servizio serve ormai due funzionalità e il nome legato all'ascolto non lo descriverebbe più. Il cambio costa poco adesso: l'ascolto non è ancora in una release ([catalogo](features.md)), quindi le variabili `SCORE_PLAYBACK_OMR_*` esistono solo negli ambienti di sviluppo. Più avanti avrebbe richiesto un periodo di compatibilità con i due nomi.

La chiave usa lo stesso nome nel backend e nel servizio: nel compose una sola variabile `TAURUS_OMR_API_KEY` configura entrambi. Le impostazioni dei job di riconoscimento (`timeout`, `concurrency`, `stale-after`, `max-attempts`, `recovery-enabled`) restano sotto `application.score-playback.omr`, perché riguardano solo l'ascolto.

### 8. Attivazione predefinita

**Attiva** (`application.pdf-processing.ocr.enabled: true`): basta configurare il servizio OMR. I suggerimenti vanno sempre rivisti e la lettura costa pochi secondi in un'elaborazione già asincrona; tenerla spenta avrebbe lasciato la funzione inutilizzata come oggi.

### 9. Attivazione per tenant

**Nessuna funzionalità per tenant**: resta un'impostazione dell'installazione, come oggi. La lettura non espone dati a nessuno, non ha costi per il tenant e produce solo suggerimenti da rivedere.

### 10. Nuovi tentativi

**Nessun nuovo tentativo** quando il servizio è occupato, lento o non risponde: le parti restano senza suggerimenti. Ripetere la richiesta allungherebbe l'elaborazione del PDF per un risultato facoltativo, e con gli slot separati il servizio occupato è un caso raro.

## Servizio OMR

### API interna

| Metodo e percorso | Descrizione |
| --- | --- |
| `POST /v1/text-recognitions` | multipart con le immagini PNG, JPEG o WebP (`pages`); risponde con le righe lette in ogni immagine |

```json
{
  "engine": "RAPIDOCR",
  "version": "3.10.0",
  "durationMillis": 840,
  "pages": [
    {
      "page": 1,
      "lines": [
        { "text": "Clarinetto 1° in Sib", "confidence": 0.94, "x": 0.05, "y": 0.03, "width": 0.22, "height": 0.02 }
      ]
    }
  ]
}
```

- `page` parte da 1 e segue l'ordine delle immagini inviate; un'immagine senza testo ha `lines` vuoto.
- Posizione e dimensioni sono frazioni della pagina, come le posizioni delle battute: descrivono il rettangolo che contiene il quadrilatero trovato dal motore.
- Le righe sono ordinate dall'alto in basso e da sinistra a destra.
- `confidence` è l'affidabilità del motore, fra 0 e 1. Il servizio non applica soglie.
- Il servizio non conserva nulla: immagini e file temporanei vengono eliminati al termine della richiesta.
- `GET /v1/health` riporta anche il motore di lettura e la sua versione.

### Regole

- La chiave `X-Taurus-Omr-Key` e i limiti sono gli stessi degli altri endpoint: 30 immagini e 25 MB per richiesta, 60 milioni di pixel per immagine. Le immagini passano dalla stessa normalizzazione del riconoscimento.
- Errori: `unauthorized` (401), `lengthRequired` (411), `tooLarge` (413), `tooManyPages` e `unreadableImage` (422), `busy` (503). Un errore del motore risponde 500 con `engineFailed` o `engineTimeout` per l'intera richiesta.
- Il motore gira in un processo separato, con il tempo massimo applicato dal servizio (decisione 4).
- Le letture usano slot propri (decisione 5). ONNX Runtime usa un numero limitato di thread, per non togliere CPU ai motori OMR.
- RapidOCR viene configurato in modo esplicito con i file già verificati da `models.sha256`. Il servizio non scarica nulla: se un modello manca, la lettura fallisce con `engineFailed` invece di tentare un download.
- `rapidocr` resta fissato in `requirements.txt`. Oggi arriva con homr: un aggiornamento di homr non deve cambiare la lettura del testo senza passare dai test di accuratezza.

### Configurazione del servizio

| Variabile | Predefinito | Significato |
| --- | --- | --- |
| `TAURUS_OMR_TEXT_CONCURRENCY` | `1` | letture eseguite in parallelo |
| `TAURUS_OMR_TEXT_THREADS` | `2` | thread di ONNX Runtime per lettura |
| `TAURUS_OMR_TEXT_TIMEOUT_SECONDS` | `60` | tempo massimo di una richiesta |

## Backend

### Flusso

1. `PdfProcessingService` applica il livello 1 a tutte le parti, come oggi.
2. Raccoglie la prima pagina non esclusa delle parti per cui il livello 1 non ha dato righe utili (decisione 2).
3. Se l'OCR è attivo e il servizio è configurato, invia le pagine al servizio in una o più richieste che rispettano i limiti: al massimo 30 pagine e 20 MB, con un margine sotto i 25 MB del servizio.
4. Di ogni pagina tiene le righe nella zona letta (decisione 1) con affidabilità almeno pari a `min-confidence`, e le passa a `parseCandidateLines` e `matchInstruments` come oggi.
5. Se il servizio non risponde, rifiuta la richiesta o supera il tempo massimo, le parti di quella richiesta restano senza suggerimenti, senza nuovi tentativi (decisione 10). L'elaborazione del PDF prosegue e termina `DONE`.

Il backend invia al servizio solo le immagini, con nomi generici (`page-1.png`, `page-2.png`…), senza codice del tenant, nome del file originale o dati dell'utente.

### Client

`OmrClient` riceve un metodo `readText(List<PageImage>)`. Usa un client HTTP con un'attesa propria (`application.pdf-processing.ocr.timeout`), perché quella dei riconoscimenti è di 12 minuti. Le risposte di errore seguono le regole attuali: un 401 viene registrato come errore di configurazione della chiave, ogni altro rifiuto viene trattato come servizio non disponibile. Un servizio di una versione precedente, senza il nuovo endpoint, risponde 404 e rientra nello stesso caso.

### Configurazione

Collegamento al servizio, condiviso da ascolto e lettura del testo (decisione 7):

| Proprietà | Predefinito | Significato |
| --- | --- | --- |
| `application.omr.url` | vuoto (`TAURUS_OMR_URL`) | indirizzo del servizio; vuoto: niente riconoscimento, caricamento MIDI e lettura del testo |
| `application.omr.api-key` | vuoto (`TAURUS_OMR_API_KEY`) | chiave condivisa, la stessa del servizio |
| `application.omr.connect-timeout` | `PT5S` | attesa massima della connessione |

Lettura del testo:

| Proprietà | Predefinito | Significato |
| --- | --- | --- |
| `application.pdf-processing.ocr.enabled` | `true` | lettura del testo delle pagine senza testo selezionabile; senza servizio configurato non ha effetto |
| `application.pdf-processing.ocr.timeout` | `PT2M` | attesa massima di una richiesta al servizio |
| `application.pdf-processing.ocr.min-confidence` | `0.5` | affidabilità minima di una riga |

Il profilo `dev` imposta `http://localhost:8090` e la chiave di sviluppo, come oggi. Le impostazioni dei job di riconoscimento restano sotto `application.score-playback.omr`.

### Rimozione di Tesseract

- la dipendenza `net.sourceforge.tess4j:tess4j` dal `pom.xml`;
- `TesseractProperties` e `application.tesseract` da `ApplicationProperties` e da `application.yml`;
- il messaggio di log sulla libreria nativa mancante.

Prima della rimozione si confronta l'albero delle dipendenze (`./mvnw dependency:tree`), per assicurarsi che nessuna libreria arrivata con tess4j venga usata altrove, per esempio i lettori ImageIO di `jai-imageio`.

`ApplicationProperties` rifiuta le proprietà sconosciute (`ignoreUnknownFields = false`). Un'installazione che imposta ancora `application.tesseract.*`, nel file di configurazione o con variabili d'ambiente, non si avvia finché non le toglie: va scritto nelle note di rilascio e nel runbook operativo.

## Casistiche

### Contenuto del PDF

| Situazione | Comportamento |
| --- | --- |
| PDF digitale con testo nel margine sinistro | livello 1, nessuna chiamata al servizio (invariato) |
| PDF digitale con testo nel margine ma nessuno strumento riconosciuto | nessuna chiamata al servizio: il livello 1 ha dato righe (decisione 2) |
| PDF scansionato, nome dello strumento nella zona letta | strumento suggerito, parte marcata `needsReview` |
| PDF scansionato, nome dello strumento fuori dalla zona letta | nessun suggerimento |
| Testo letto, ma nessuno strumento del tenant corrisponde | parte senza strumenti e senza `needsReview` (invariato; gli strumenti da aggiungere sono progettati [a parte](track-instrument-suggestions-spec.md)) |
| Nome con accenti o simboli («Sib», «1°», «♭») | stessa normalizzazione di oggi; la lettura dei simboli va verificata nella prova preliminare |
| Titolo che contiene il nome di uno strumento («Il flauto magico») | possibile suggerimento sbagliato, da correggere nella revisione; zona e soglia lo limitano |
| Prima pagina della parte esclusa dall'utente | viene letta la prima pagina non esclusa (invariato) |
| Tutte le pagine della parte escluse | la parte non viene creata e non c'è lettura (invariato) |
| Nome dello strumento tolto dal ritaglio delle annotazioni | nessun suggerimento: il servizio legge l'immagine ritagliata |
| PDF senza segnalibri con più parti | una sola parte, letta sulla prima pagina (invariato, fuori scope) |
| Pagina capovolta | il classificatore dell'orientamento la legge comunque |
| Pagina ruotata di 90° | nessun testo utile, nessun suggerimento |
| Pagina bianca o senza testo | `lines` vuoto, nessun suggerimento |
| Nome scritto a mano | probabilmente non letto, nessun suggerimento |

### Servizio e configurazione

| Situazione | Comportamento |
| --- | --- |
| OCR disattivato | nessuna chiamata, nessun suggerimento dalle pagine scansionate |
| Indirizzo del servizio vuoto | nessuna chiamata; un solo messaggio informativo all'avvio |
| Ambiente con le sole variabili `SCORE_PLAYBACK_OMR_*` | le variabili vengono ignorate e il servizio risulta non configurato: vanno rinominate in `TAURUS_OMR_*` |
| Servizio fermo o non raggiungibile | attesa al massimo pari a `connect-timeout`, avviso nel log, job `DONE` senza suggerimenti |
| Chiave rifiutata (401) | errore di configurazione nel log, job `DONE` senza suggerimenti |
| Servizio di una versione senza l'endpoint (404) | come il servizio non disponibile |
| Slot di lettura occupati oltre l'attesa (503 `busy`) | nessun suggerimento per quella richiesta, nessun nuovo tentativo |
| Lettura oltre il tempo massimo | il servizio interrompe il processo e risponde `engineTimeout`; nessun suggerimento |
| Immagine rifiutata (413, 422) | avviso nel log, nessun suggerimento per quella richiesta; non dovrebbe accadere, perché il backend rispetta i limiti |
| Riconoscimento OMR in corso | la lettura usa i propri slot e non lo aspetta |
| Job PDF ripreso con un nuovo tentativo | la lettura viene rifatta: non c'è stato salvato |

### Volumi

| Situazione | Comportamento |
| --- | --- |
| PDF con più di 30 parti | più richieste da al massimo 30 pagine |
| Prime pagine che superano insieme 20 MB | più richieste, divise per dimensione |
| PDF di più tenant elaborati insieme | richieste indipendenti, in coda sugli slot del servizio; nessun dato condiviso |

## Autorizzazioni e isolamento tenant

- Nessuna nuova API pubblica: la lettura resta interna all'elaborazione asincrona del PDF, avviata da chi può già caricare PDF.
- Il servizio riceve solo immagini con nomi generici, non conserva nulla e non accede a database o storage.
- Gli strumenti confrontati sono quelli del tenant del job, letti nel suo contesto come oggi.
- Nessuna funzionalità per tenant (decisione 9).

## Osservabilità e sicurezza

- Servizio: una riga di log per richiesta con numero di pagine, righe lette e durata, senza il testo.
- Backend: un avviso con il motivo quando la lettura non è disponibile, al massimo uno per job; il testo letto compare solo a livello debug, come oggi.
- Il testo letto è un dato non fidato: serve solo al confronto con i nomi degli strumenti e non viene interpretato in altro modo.
- Il servizio resta raggiungibile solo dalla rete interna, con la stessa chiave degli altri endpoint.

## Licenze

| Componente | Licenza | Modulo | Nota |
| --- | --- | --- | --- |
| RapidOCR 3.10.0 e modelli PP-OCR | Apache-2.0 | servizio OMR | già presente in `THIRD_PARTY_NOTICES.md` come «non usata da Taurus»: la riga va aggiornata |
| ONNX Runtime | MIT | servizio OMR | già presente |
| tess4j | Apache-2.0 | backend | rimosso |

## Strategia di test

### Servizio OMR

- motore finto, come per Audiveris e homr: righe nell'ordine atteso, posizioni come frazioni della pagina, pagina senza testo, motore fallito, tempo massimo superato;
- chiave obbligatoria; limiti di pagine, dimensione e pixel; immagine non leggibile;
- slot separati: una lettura non aspetta un riconoscimento in corso;
- modello mancante: `engineFailed`, senza tentativi di download;
- accuratezza: il nome atteso della parte su ogni pagina del corpus, comprese le pagine che imitano una scansione, con un minimo di nomi letti correttamente; il test gira anche nella CI sull'immagine appena costruita.

### Backend

- `PdfProcessingService` con un client finto: suggerimento da una riga nel margine sinistro e da una nella fascia in alto; righe fuori zona o sotto soglia ignorate; livello 1 che evita la chiamata; pagine escluse; più di 30 parti divise in più richieste;
- OCR disattivato, servizio non configurato, non raggiungibile, 401, 404, 503 e tempo scaduto: job `DONE` senza suggerimenti;
- `OmrClient.readText` contro `FakeOmrServer`;
- `OmrClientTest` e `ScoreRecognitionIT` con le nuove proprietà `application.omr.*`;
- avvio dell'applicazione senza Tesseract installato e senza `application.tesseract`.

### Verifica manuale

- servizio in Docker e un PDF scansionato di parti reali con segnalibri: strumenti suggeriti e parti marcate da rivedere nel workspace Parti;
- stesso PDF con il servizio fermo: elaborazione completata, parti senza suggerimenti;
- riconoscimento e caricamento MIDI ancora disponibili con le sole variabili `TAURUS_OMR_URL` e `TAURUS_OMR_API_KEY`.

## Criteri di accettazione

- `./mvnw verify` e l'avvio del backend non richiedono Tesseract né altre librerie native;
- il backend non contiene più tess4j né le proprietà `application.tesseract`;
- con il servizio OMR in esecuzione, una parte scansionata con il nome dello strumento nella zona letta riceve lo strumento suggerito ed è marcata da rivedere, anche con l'immagine Docker di produzione del backend;
- con OCR disattivato, servizio non configurato o non raggiungibile, l'elaborazione del PDF termina `DONE` con le parti senza suggerimenti, entro l'attesa di connessione o di risposta configurata;
- una lettura del testo non aspetta un riconoscimento OMR in corso;
- il servizio non scarica modelli durante l'esecuzione e `GET /v1/health` riporta la versione del motore di lettura;
- ascolto e lettura del testo usano lo stesso collegamento `application.omr.*`, e nel compose una sola variabile `TAURUS_OMR_API_KEY` configura servizio e backend;
- i suggerimenti per i PDF con testo selezionabile sono identici a prima.

## Piano di adozione e compatibilità

0. **Prova preliminare.** RapidOCR e Tesseract a confronto sulle prime pagine scansionate di 10–20 parti reali: strumenti suggeriti correttamente, suggerimenti sbagliati, tempi e caricamento dei modelli. La prova conferma o corregge le misure della zona, la soglia, il modello e l'esecuzione in un processo separato (decisioni 1, 3, 4 e 6). I nomi letti servono anche al vocabolario degli [strumenti da aggiungere](track-instrument-suggestions-spec.md).
1. **Servizio.** Endpoint, motore in un processo separato, slot, test e accuratezza; nuova immagine pubblicata con il tag di release.
2. **Backend.** Client, nuovo flusso, configurazione `application.omr.*` e `application.pdf-processing.ocr.*`, rimozione di tess4j e test.
3. **Configurazione di sviluppo.** `TAURUS_OMR_API_KEY` al posto di `SCORE_PLAYBACK_OMR_API_KEY` in `omr.yml` e nel file `.env` del compose; profilo `dev` aggiornato.
4. **Documentazione.** Nel [Runbook operativo](runbook-operativo.md): prerequisiti senza Tesseract, variabili `TAURUS_OMR_URL` e `TAURUS_OMR_API_KEY`, rotazione della chiave e note di aggiornamento. Poi la prova `MED-007` del [Runbook di test](runbook-test.md), la configurazione e l'API in [Ascolto e correzione delle parti](track-score-playback-spec.md#configurazione-del-backend), la descrizione del modulo in `AGENTS.md` e nel README di `taurus-omr`, `THIRD_PARTY_NOTICES.md`.

Compatibilità:

- backend nuovo con servizio vecchio: l'endpoint risponde 404 e le parti restano senza suggerimenti; per averli va aggiornato anche il servizio;
- backend vecchio con servizio nuovo: nessun effetto, il nuovo endpoint resta inutilizzato;
- un ambiente con le variabili `SCORE_PLAYBACK_OMR_URL` e `SCORE_PLAYBACK_OMR_API_KEY` deve rinominarle in `TAURUS_OMR_URL` e `TAURUS_OMR_API_KEY`: i nomi vecchi vengono ignorati e riconoscimento, caricamento MIDI e lettura del testo non vengono offerti;
- un'installazione con `application.tesseract.*` impostato deve togliere quelle proprietà prima dell'aggiornamento, altrimenti il backend non si avvia;
- nessuna migrazione del database.

## Rischi

- **Qualità in italiano.** Il modello di lettura predefinito potrebbe sbagliare accenti e simboli. Mitigazione: prova preliminare, test di accuratezza e, se serve, modello PP-OCR per le lingue latine.
- **Suggerimenti sbagliati.** Titoli e indicazioni di tempo possono contenere nomi di strumenti. Mitigazione: zona letta, soglia di affidabilità e revisione obbligatoria delle parti marcate.
- **Dipendenza dal servizio OMR.** Senza servizio niente suggerimenti dalle pagine scansionate. Mitigazione: il suggerimento è facoltativo e l'elaborazione prosegue comunque.
- **CPU condivisa con i riconoscimenti.** Mitigazione: thread limitati, slot separati e limiti del container.
- **Aggiornamento di homr.** Una nuova versione può portare un'altra versione di RapidOCR o altri modelli. Mitigazione: versione fissata in `requirements.txt`, modelli in `models.sha256` e test di accuratezza nella CI.
- **Variabili rinominate.** Un ambiente di sviluppo con le vecchie variabili perde il collegamento al servizio senza errori evidenti. Mitigazione: messaggio informativo all'avvio quando il servizio non è configurato e note di aggiornamento nel runbook.
