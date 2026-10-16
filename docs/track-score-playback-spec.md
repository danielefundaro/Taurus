# Ascolto e correzione delle parti

La funzionalità consente di ascoltare le parti di una traccia, singolarmente o come partitura, partendo dalle pagine immagine già presenti in Taurus. Le note sono ricavate con il riconoscimento ottico della musica (OMR) oppure da un file MusicXML o MIDI caricato; super amministratori, amministratori e archivisti possono correggere e approvare il risultato. Tutti i componenti sono open source e non comportano costi per il tenant.

## Stato del documento

ID catalogo: `track-score-playback`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

Documenti collegati: [Elaborazione asincrona dei PDF delle tracce](track-pdf-processing-spec.md), [Workspace per la gestione delle parti](track-parts-workspace-spec.md), [Modifica e analisi delle immagini delle parti](track-page-image-editing-spec.md), [Anteprima e stampa degli spartiti](score-preview-printing-spec.md), [Gestione centralizzata dei media](media-asset-spec.md), [Funzionalità configurabili per tenant](tenant-feature-flags-spec.md), [Standard degli errori applicativi](application-errors-standard.md).

## Obiettivo

- Ascoltare una parte o l'intera partitura di una traccia nel browser, con cursore sullo spartito, mixer per parte, tempo variabile, ripetizione di un intervallo di battute ed esecuzione con o senza ritornelli.
- Ricavare le note dalle pagine immagine con due motori OMR, scegliendo automaticamente il risultato più coerente.
- Accettare in alternativa file MusicXML e MIDI.
- Correggere il risultato con un editor affiancato all'immagine originale, oppure con MuseScore tramite scaricamento e nuovo caricamento.
- Approvare le versioni corrette e segnalare gli errori ascoltati.
- Riprodurre in suoni reali le parti scritte per strumenti traspositori.

## Fuori scope

- Arrangiamento, composizione e scrittura di parti da zero: richiedono un editor completo, previsto in un'evolutiva successiva.
- Riconoscimento di manoscritti.
- Esportazione audio (MP3, WAV) e nuova impaginazione PDF delle parti riconosciute.
- Ascolto senza connessione oltre alla cache del service worker già presente.
- Uso di Web MIDI o di strumenti MIDI esterni.
- Riconoscimento del testo cantato.

## Decisioni principali

1. **Riconoscimento su richiesta.** Lo avviano super amministratore, amministratore e archivista, per una parte o per tutte le parti della traccia. Non parte automaticamente al caricamento del PDF.
2. **Ascolto della versione non verificata.** Tutti gli utenti che vedono la parte possono ascoltare anche una versione non verificata, contrassegnata come tale e accompagnata dall'esito dei controlli.
3. **Ascolto non disponibile.** Senza una versione utilizzabile gli utenti senza privilegi vedono l'ascolto disabilitato con il testo «Ascolto non ancora disponibile».
4. **Due motori OMR.** Audiveris e homr vengono eseguiti entrambi; Taurus propone il risultato con i controlli migliori e il revisore può scegliere l'altro.
5. **Servizio OMR separato.** I motori girano in un container dedicato, senza accesso a database o storage, chiamato dal backend via HTTP sulla rete interna.
6. **MusicXML come formato interno.** Ogni versione di una parte è un file MusicXML compresso (`.mxl`). Il MIDI non viene salvato: il browser lo genera al momento.
7. **Ascolto nel browser.** Verovio disegna lo spartito e genera il MIDI; SpessaSynth lo suona con il SoundFont MuseScore General. Il server non produce audio.
8. **Solo correzione.** L'editor modifica note e struttura di una versione esistente; non è uno strumento di scrittura.
9. **Versioni immutabili.** Riconoscimento, caricamento e correzione producono sempre una nuova versione; l'approvazione è uno stato della versione.
10. **Trasposizione e suono dallo strumento.** Ogni strumento dichiara la trasposizione e il suono, come banco e programma del SoundFont; Taurus propone i valori a partire dal nome con un catalogo di riferimento. Uno strumento non configurato si ascolta con un suono neutro e un avviso. La parte può sovrascrivere i valori.
11. **Percussioni incluse.** Le parti di percussione suonano con i suoni di batteria General MIDI dello strumento assegnato.
12. **Formati caricabili.** MusicXML (`.musicxml`, `.xml`, `.mxl`) e MIDI (`.mid`, `.midi`). Il MIDI viene convertito in MusicXML.
13. **Nessun controllo sui diritti.** Il tenant si assume la responsabilità dei file caricati, come già previsto in Taurus: non sono necessari blocchi o avvisi.
14. **Attivazione per tenant.** Nuova funzionalità `SCORE_PLAYBACK`, disattivata per default.

## Prova preliminare

Il 15/10/2026 i due motori sono stati provati su tre parti reali, in container Docker, ricostruendo con Verovio quanto riconosciuto.

| Parte | Immagine | Audiveris 5.11.0 | homr 0.7.0 |
| --- | --- | --- | --- |
| Tuba Sib, 34 battute | grafica digitale pulita | 33/34 battute corrette; legature di valore e dinamiche lette; persi ritornelli, forcelle e indicazione di tempo | 34/34 battute corrette e ritornello finale; legature di valore lette come legature di portamento, nessuna dinamica, metro 8/16 spurio |
| Cl piccolo in Lab, circa 138 battute | pulita ma fitta | note in gran parte corrette; numero di battuta letto come chiave all'ottava, pause multiple contate male, volta estesa a metà brano, accordi inesistenti | inutilizzabile: 69 battute |
| Flic. baritono, circa 113 battute | scansione vecchia e sgranata | inutilizzabile: diviso in quattro movimenti, armature perse, accordi spuri | buona: titolo, armature e cambi di tonalità, terzine, 115 battute; metro C letto 16/16, chiavi spurie, nessuna dinamica |

Tempi: Audiveris 34 secondi per le tre pagine su 12 core; homr circa 30 secondi a pagina su CPU.

Conclusioni applicate a questa specifica:

- l'OMR produce una bozza da correggere, non una conversione definitiva;
- i due motori sono complementari e conviene eseguirli entrambi;
- ritornelli, volte, pause multiple e tempo sono il punto debole comune, e sono proprio ciò che determina un ascolto corretto;
- la trasposizione non si ricava dall'immagine;
- Audiveris richiede GTK e un display virtuale anche in modalità batch, e i dati legacy di Tesseract per leggere il testo; il pacchetto `.deb` va estratto senza eseguire gli script di installazione.

Le pagine generate da Taurus a partire dai PDF sono a 300 DPI ([Elaborazione asincrona dei PDF](track-pdf-processing-spec.md)), più adatte delle immagini usate nella prova.

## Contesto tecnico acquisito

- Una parte è `SheetsMusic` (tabella `sheet_music`), con le pagine ordinate in `sheet_music_media` e gli strumenti in `sheet_music_instrument`; tutte le tabelle sono nello schema del tenant.
- `sheet_music.needs_review` indica strumenti suggeriti automaticamente dal PDF e viene sovrascritto da ogni salvataggio della traccia: non va riusato per la revisione delle note.
- `Tracks.tempo` e `Tracks.tone` sono testo libero.
- `instrument` contiene solo nome e descrizione; i 43 strumenti iniziali vengono da `data/instruments.csv`. Il tenant può aggiungerne altri dalla pagina degli strumenti o con l'[importazione iniziale](tenant-onboarding-import-spec.md), che riconosce gli strumenti esistenti dal nome normalizzato.
- L'elaborazione dei PDF usa la coda RabbitMQ `upload.files` con messaggi Java serializzati, consumata dal backend; il frontend legge lo stato una volta, senza aggiornamenti, e non c'è recupero dei job bloccati.
- I file stanno sul filesystem locale (`application.base-path`); non esistono volumi condivisi né API interne di download.
- Super amministratore, amministratore e archivista gestiscono tracce, strumenti e immagini delle pagine; `ROLE_USER` vede le tracce `COMPLETE` e `PUBLIC` con le sole parti dei propri strumenti; `ROLE_USER_EXTERNAL` vede solo le tracce `PUBLIC`.
- Il dettaglio della traccia è lo stesso componente per tutti i ruoli, con schede Dettagli, Parti e, per chi gestisce i PDF, PDF.
- L'intestazione `Permissions-Policy` disabilita Web MIDI, che non serve: l'audio è generato con Web Audio.
- Il service worker mette in cache in modo differito i file sotto `/assets/**`.

## Flussi

### Riconoscimento di una parte

1. Un utente con privilegi sceglie **Riconosci** su una parte, oppure **Riconosci tutte le parti** sulla traccia.
2. Il backend crea un job per ogni parte e lo pubblica sulla nuova coda `score.recognition`.
3. Il worker del backend legge le pagine della parte nell'ordine corrente e chiama il servizio OMR con le immagini.
4. Il servizio esegue Audiveris e homr, unisce gli eventuali movimenti separati da Audiveris, calcola i controlli e restituisce per ogni motore MusicXML, posizioni delle battute sulle pagine ed esito.
5. Il backend salva una versione per ogni motore riuscito, sceglie la proposta (vedi [Scelta della versione proposta](#scelta-della-versione-proposta)) e la rende corrente, salvo che la parte abbia già una versione approvata.
6. Chi ha avviato il riconoscimento riceve una notifica di completamento o di errore; per **Riconosci tutte le parti** la notifica è unica.

L'avanzamento è visibile a chi ha privilegi: la pagina interroga lo stato ogni 5 secondi finché esistono job in coda o in corso.

### Caricamento di un file

1. Un utente con privilegi carica un file MusicXML o MIDI su una parte.
2. Se il file contiene più parti o tracce MIDI, sceglie quale importare.
3. Il MIDI viene convertito in MusicXML dal servizio OMR con music21, quantizzando le durate.
4. Il backend salva una nuova versione non verificata, oppure approvata se l'utente sceglie **Carica come approvata**, e la rende corrente.

### Correzione e approvazione

1. Un utente con privilegi apre l'editor sulla versione corrente, con la pagina originale affiancata e la battuta selezionata evidenziata sull'immagine.
2. Corregge note e struttura; i controlli si aggiornano a ogni modifica.
3. **Salva** crea una nuova versione non verificata; **Salva e approva** la crea già approvata. In entrambi i casi diventa la versione corrente.
4. In alternativa scarica il MusicXML, lo corregge in MuseScore e lo ricarica.
5. **Approva** su una versione esistente la marca come approvata senza modificarla.

### Ascolto

1. L'utente sceglie **Ascolta** sulla parte oppure **Ascolta la partitura** sulla traccia.
2. Il browser scarica il MusicXML delle versioni correnti, applica trasposizioni e svolgimento dei ritornelli, genera il MIDI con Verovio e lo suona con SpessaSynth.
3. Una versione non verificata mostra l'etichetta **Non verificata** e il riepilogo dei controlli.
4. Chi ascolta può usare **Segnala un errore**, indicando facoltativamente la battuta.

### Segnalazione di un errore

La segnalazione è salvata sulla parte e notificata a super amministratori, amministratori e archivisti. Resta aperta finché un utente con privilegi la chiude oppure approva una nuova versione della parte.

## Disponibilità dell'ascolto

Una versione è **utilizzabile** se è approvata, oppure se è non verificata e ricavata dalle pagine attuali della parte. L'impronta delle pagine è lo SHA-256 dell'elenco ordinato degli SHA-256 delle immagini; una versione caricata o corretta eredita l'impronta della versione da cui deriva.

| Situazione della parte | Super amministratore, amministratore, archivista | Altri utenti |
| --- | --- | --- |
| Nessuna versione | **Ascolta** avvia il riconoscimento; disponibile anche **Riconosci** | disabilitato, «Ascolto non ancora disponibile» |
| Riconoscimento in coda o in corso | avanzamento | disabilitato, «Ascolto non ancora disponibile» |
| Riconoscimento non riuscito | errore e **Riprova** | disabilitato, «Ascolto non ancora disponibile» |
| Versione non verificata utilizzabile | ascolto, etichetta e controlli | ascolto, etichetta e controlli |
| Versione non verificata con pagine cambiate | ascolto con avviso e **Riconosci di nuovo** | disabilitato, «Ascolto non ancora disponibile» |
| Versione approvata | ascolto | ascolto |
| Versione approvata con pagine cambiate | ascolto con avviso «Pagine modificate dopo l'approvazione» | ascolto |

Una versione approvata resta utilizzabile dopo una modifica delle pagine perché una correzione di contrasto o raddrizzamento non cambia la musica; l'avviso permette a chi gestisce la traccia di verificarla.

La partitura comprende le parti con una versione utilizzabile per l'utente; le parti mancanti sono elencate nel mixer come non disponibili.

## Servizio OMR

### Modulo

Nuovo modulo `taurus-omr/`: servizio HTTP in Python, immagine Docker propria, test con pytest. Contiene:

- Audiveris 5.11.0, estratto dal pacchetto `.deb` ufficiale, con GTK 3, Xvfb e Tesseract con i dati legacy di `tesseract-ocr/tessdata` per inglese e italiano;
- homr 0.7.0 in versione CPU, con i modelli scaricati durante la build dell'immagine e verificati con checksum; il riconoscimento del titolo di homr (RapidOCR) è disattivato con `--no-title`;
- music21 per la conversione MIDI → MusicXML e per i controlli.

Le versioni dei motori e dei modelli sono fissate nel Dockerfile; un aggiornamento passa da una nuova immagine e dai test di accuratezza.

### API interna

| Metodo e percorso | Descrizione |
| --- | --- |
| `POST /v1/recognitions` | multipart con le pagine PNG o JPEG in ordine e i motori richiesti; risponde, al termine, con un risultato per motore |
| `POST /v1/midi-conversions` | multipart con il file MIDI e l'indice della traccia da importare; risponde con il MusicXML |
| `POST /v1/inspections` | multipart con un file MusicXML; restituisce parti contenute e controlli |
| `GET /v1/health` | stato e versioni di motori e modelli |

Il risultato di un motore contiene stato, MusicXML compresso, posizioni delle battute (`{ measure, page, x, y, width, height, approximate }`), controlli, durata ed eventuale codice di errore.

- Le posizioni di Audiveris sono ricavate dal progetto `.omr` e sono esatte. homr scrive per ogni nota una posizione approssimata come commento nel MusicXML: le posizioni delle battute sono calcolate da queste e marcate `approximate`.
- Il servizio non conserva nulla: i file temporanei sono eliminati al termine della richiesta.
- Autenticazione con chiave condivisa nell'intestazione `X-Taurus-Omr-Key`, da variabile d'ambiente; il servizio è esposto solo sulla rete interna.
- Limiti: 30 pagine e 25 MB per richiesta, timeout di 5 minuti per motore.

### Configurazione del backend

| Proprietà | Predefinito | Significato |
| --- | --- | --- |
| `application.score-playback.enabled` | `false` | abilitazione a livello di installazione |
| `application.score-playback.omr.url` | vuoto | indirizzo del servizio; vuoto nasconde le azioni di riconoscimento |
| `application.score-playback.omr.api-key` | da variabile d'ambiente | chiave condivisa |
| `application.score-playback.omr.timeout` | `PT12M` | attesa massima della risposta |
| `application.score-playback.omr.concurrency` | `1` | job eseguiti in parallelo |
| `application.score-playback.omr.stale-after` | `PT30M` | dopo questo tempo un job in corso torna in coda, al massimo due volte |

Il servizio OMR è un nuovo file `taurus-be/src/main/docker/omr.yml` e una nuova immagine pubblicata dal workflow `docker-publish.yml`.

### Controlli

Calcolati per ogni versione e salvati con essa:

| Controllo | Segnala |
| --- | --- |
| Durata delle battute | battute la cui durata non corrisponde al metro, escluse anacrusi e battute marcate implicite |
| Numero di battute | totale e differenza con l'altro motore |
| Chiavi | cambi di chiave, chiavi all'ottava, chiavi di percussione in parti non di percussione |
| Armature e metro | cambi di armatura e di metro, metri insoliti come 8/16 o 16/16 |
| Struttura | ritornelli, volte, segno, coda, D.C., D.S. e Fine trovati; ritornelli aperti e non chiusi |
| Tempo | indicazione metronomica trovata |
| Contenuto | battute vuote, accordi in parti monofoniche |

### Scelta della versione proposta

1. Si escludono i motori falliti.
2. Si preferisce la versione con la percentuale più alta di battute di durata corretta.
3. A parità entro due punti percentuali, si preferisce quella con meno cambi di chiave sospetti.
4. A ulteriore parità si preferisce Audiveris, che conserva dinamiche e legature di valore.

Il revisore può rendere corrente l'altra versione con **Usa questa versione**.

## Trasposizione e suoni

### Strumento

Nuovi campi dello strumento, tutti facoltativi:

| Campo | Significato |
| --- | --- |
| `transpositionSemitones` | intervallo in semitoni tra nota scritta e suono reale |
| `bassClefTranspositionSemitones` | intervallo per le parti in chiave di basso, se diverso; vuoto = uguale al precedente |
| `soundBank` | banco del suono nel SoundFont; 0 per i suoni General MIDI |
| `soundProgram` | programma del suono nel banco, da 0 a 127 |
| `percussionKey` | nota di batteria General MIDI; se presente lo strumento è una percussione |

Il suono è salvato come banco e programma, non come solo programma General MIDI: se in futuro si adotta un SoundFont gratuito con suoni specifici per la banda, come flicorni ed euphonium che il General MIDI non prevede, basterà sceglierli senza cambiare il modello dati.

Uno strumento è **configurato per l'ascolto** se ha `percussionKey`, oppure se ha sia `soundProgram` sia `transpositionSemitones`.

### Strumento non configurato

- La parte si ascolta comunque, con il pianoforte (banco 0, programma 0) e senza trasposizione; una parte in chiave di percussione usa il rullante (nota 38).
- Lettore e workspace mostrano «Suono e trasposizione non configurati per *nome dello strumento*»; chi ha privilegi vede anche il collegamento al dettaglio dello strumento.
- L'elenco degli strumenti offre il filtro **Da configurare per l'ascolto**.

### Catalogo di riferimento

Taurus contiene un catalogo di riferimento, usato per proporre i valori degli strumenti. Valori da verificare con un archivista prima dell'implementazione:

| Parole chiave | Tonalità di riferimento | Trasposizione | Chiave di basso | Suono (banco 0) |
| --- | --- | ---: | ---: | --- |
| ottavino | Do | +12 | — | 72 Piccolo |
| flauto | Do | 0 | — | 73 Flute |
| oboe | Do | 0 | — | 68 Oboe |
| fagotto | Do | 0 | 0 | 70 Bassoon |
| clarinetto piccolo | Mib | +3 | — | 71 Clarinet |
| clarinetto contralto | Mib | −9 | — | 71 Clarinet |
| clarinetto basso | Sib | −14 | −2 | 71 Clarinet |
| clarinetto | Sib | −2 | — | 71 Clarinet |
| sax soprano | Sib | −2 | — | 64 Soprano Sax |
| sax contralto | Mib | −9 | — | 65 Alto Sax |
| sax tenore | Sib | −14 | — | 66 Tenor Sax |
| sax baritono | Mib | −21 | — | 67 Baritone Sax |
| tromba, cornetta | Sib | −2 | — | 56 Trumpet |
| flicorno sopranino | Mib | +3 | — | 56 Trumpet |
| flicorno soprano | Sib | −2 | — | 56 Trumpet |
| flicorno contralto | Mib | −9 | — | 60 French Horn |
| flicorno tenore | Sib | −14 | 0 | 57 Trombone |
| flicorno baritono, euphonium, bombardino | Sib | −14 | 0 | 57 Trombone |
| flicorno basso | Sib | −14 | 0 | 58 Tuba |
| trombone | Do | 0 | 0 | 57 Trombone |
| corno | Fa | −7 | — | 60 French Horn |
| basso, tuba, bassotuba | Do | 0 | 0 | 58 Tuba |
| glockenspiel, campanelli | Do | +24 | — | 9 Glockenspiel |
| xilofono | Do | +12 | — | 13 Xylophone |
| timpani | Do | 0 | 0 | 47 Timpani |
| tamburo, rullante | — | — | — | batteria, nota 38 |
| gran cassa | — | — | — | batteria, nota 36 |
| piatti | — | — | — | batteria, nota 49 |
| triangolo | — | — | — | batteria, nota 81 |
| tamburello | — | — | — | batteria, nota 54 |

### Proposta di configurazione

Quando si crea o si rinomina uno strumento, Taurus calcola una proposta dal nome:

1. Il nome viene normalizzato: minuscole, senza accenti, senza punteggiatura, senza numeri d'ordine (1°, 2°, I, II) e senza le parole «solo», «primo», «secondo», «terzo».
2. Le parole chiave del catalogo si cercano come parole intere; vince la più lunga, così «clarinetto basso» prevale su «clarinetto» e «contrabbasso» non corrisponde a «basso».
3. Una tonalità nel nome vale se segue la parola «in» oppure se è una forma con alterazione a sé stante (`sib`, `mib`, `lab`, `reb`, `solb`, `bb`, `eb`, `ab`). Se è diversa dalla tonalità di riferimento, la trasposizione diventa quella della stessa nota più vicina al valore di riferimento; la trasposizione in chiave di basso resta quella del catalogo. Esempi: corno in Mib −9, clarinetto piccolo in Lab +8, sax tenore in Do −12.
4. Senza parole chiave non c'è proposta e lo strumento resta non configurato.

Nel dettaglio dello strumento la proposta precompila i campi, con la dicitura «Proposta da verificare»; diventa definitiva solo quando l'utente salva. Fanno eccezione due casi:

- **Strumenti esistenti al rilascio**: un'attività di avvio per tenant, idempotente, compila con la proposta i campi vuoti degli strumenti esistenti, compresi i 43 predefiniti. Non sovrascrive mai un valore già impostato; gli strumenti senza proposta restano non configurati.
- **Importazione iniziale del tenant**: il foglio `Strumenti` accetta le colonne facoltative `suono` (programma General MIDI da 0 a 127, oppure `batteria:<nota>`), `trasposizione` e `trasposizione_chiave_basso`. Se mancano, si applica la proposta e il report di importazione la segnala come avviso; senza proposta lo strumento resta non configurato, con un avviso.

### Scelta del suono

Nel dettaglio dello strumento il suono si sceglie per nome, raggruppato per famiglia (legni, ottoni, percussioni intonate, batteria), dall'elenco dei suoni del SoundFont in uso, letto con SpessaSynth quando si apre il selettore; banco e programma restano visibili come dettaglio. **Prova** suona una scala di un'ottava scritta in Do e trasposta con i valori correnti, oppure il colpo di batteria scelto, così l'archivista verifica a orecchio suono e trasposizione prima di salvare.

### Parte

La parte può sovrascrivere trasposizione e suono (banco e programma). Se gli strumenti assegnati alla parte hanno valori diversi e non c'è una sovrascrittura, l'ascolto usa il primo strumento e il workspace mostra un avviso.

La trasposizione si applica inserendo nel MusicXML l'elemento `<transpose>` con l'intervallo della chiave in uso. Le parti di percussione suonano sul canale 10: con uno strumento ogni nota usa la sua nota di batteria; con due strumenti la prima voce usa il primo e la seconda voce il secondo.

## Ascolto

### Componenti

- **Verovio** (npm `verovio`, LGPL-3.0 o successiva): disegno dello spartito, generazione del MIDI e mappa temporale per il cursore. È servito come file separato, non incluso nel bundle, con la nota di licenza.
- **SpessaSynth** (`spessasynth_lib`, Apache-2.0): sintetizzatore SoundFont e sequencer, con volume per canale, velocità di riproduzione e posizionamento.
- **SoundFont MuseScore General** in formato `.sf3` (circa 38 MB, licenza MIT), in `/assets/soundfonts/`, messo in cache dal service worker al primo ascolto. Le note di copyright sono riportate nella pagina delle licenze.

### Svolgimento dei ritornelli

Un servizio del frontend calcola l'ordine di esecuzione delle battute da ritornelli, volte, `segno`, `coda`, `dacapo`, `dalsegno`, `tocoda` e `fine` del MusicXML, quindi produce una copia "svolta" da cui Verovio genera il MIDI. Le regole:

- **Con ritornelli**: ogni ritornello è eseguito come scritto; dopo un D.C. o un D.S. i ritornelli non si ripetono, salvo diversa indicazione.
- **Senza ritornelli**: ogni sezione è eseguita una volta, saltando le prime volte e prendendo l'ultima; i salti D.C., D.S. e Coda restano.

La copia svolta conserva il numero di battuta originale, così il cursore resta allineato allo spartito disegnato.

### Partitura

Il frontend unisce le versioni correnti delle parti disponibili in un unico documento con una parte MusicXML per ogni parte di Taurus:

- le pause multiple sono espanse prima dell'unione;
- la struttura (ritornelli e salti) è quella della parte di riferimento, cioè la prima parte in ordine di visualizzazione con versione approvata o, in mancanza, con versione utilizzabile; un utente con privilegi può sceglierla nella traccia;
- se le parti hanno lunghezze diverse, le più corte sono completate con pause e il mixer mostra «Le parti hanno lunghezze diverse».

Ogni parte usa un proprio canale MIDI, così il mixer agisce sulla singola parte.

### Controlli dell'interfaccia

- riproduci, pausa, posizionamento su una battuta;
- tempo in battiti al minuto, partendo dall'indicazione del brano;
- intervallo di battute da ripetere;
- metronomo e una battuta di attacco facoltativa;
- con o senza ritornelli;
- nella partitura: volume, muto e solo per ogni parte, più le scorciatoie **Solo la mia parte** e **Tutto tranne la mia parte**, basate sugli strumenti dell'utente.

Il tempo iniziale è, in ordine: il nuovo campo numerico `tempoBpm` della traccia, l'indicazione metronomica della versione, 100.

## Editor di correzione

Disponibile a super amministratore, amministratore e archivista.

- **Layout**: spartito disegnato da Verovio a sinistra, pagina originale a destra con la battuta selezionata evidenziata; con posizioni approssimate l'evidenziazione è un riquadro più ampio.
- **Note e pause**: selezione con mouse o tastiera; modifica di altezza, ottava, alterazione, durata, punto; trasformazione tra nota e pausa; inserimento ed eliminazione.
- **Legature e terzine**: legature di valore, gruppi irregolari.
- **Battuta**: metro, armatura, chiave; inserimento ed eliminazione di battute; pause di più battute.
- **Struttura**: ritornelli, volte, segno, coda, D.C., D.S., Fine; indicazione metronomica.
- **Espressione**: dinamiche e accenti più comuni.
- **Controlli in tempo reale**: la battuta con durata errata è evidenziata.
- **Annulla e ripristina** nella sessione.
- **Ascolto** della battuta o dell'intervallo selezionato durante la correzione.

Le modifiche agiscono sul DOM MusicXML della versione e producono a ogni salvataggio una nuova versione. La corrispondenza tra elementi disegnati da Verovio e nodi MusicXML è il principale rischio tecnico (vedi [Questioni aperte](#questioni-aperte)).

## Modello dati e migrazioni

### Schema del tenant

Changelog in `tenant-master.xml`. Tutte le nuove tabelle hanno i campi di audit `deleted`, `insert_date`, `insert_by`, `edit_date`, `edit_by`; quelle modificabili anche `entity_version`.

Modifiche a tabelle esistenti:

| Tabella | Colonna | Tipo | Note |
| --- | --- | --- | --- |
| `instrument` | `transposition_semitones` | `SMALLINT` | facoltativo, tra −36 e +36; vuoto = non configurato |
| `instrument` | `bass_clef_transposition_semitones` | `SMALLINT` | facoltativo, tra −36 e +36 |
| `instrument` | `sound_bank` | `SMALLINT` | facoltativo, tra 0 e 16383; richiede `sound_program` |
| `instrument` | `sound_program` | `SMALLINT` | facoltativo, tra 0 e 127 |
| `instrument` | `percussion_key` | `SMALLINT` | facoltativo, tra 27 e 87 |
| `track` | `tempo_bpm` | `SMALLINT` | facoltativo, tra 20 e 300 |
| `track` | `reference_sheet_music_id` | `BIGINT` | facoltativo, parte di riferimento della partitura |
| `sheet_music` | `current_score_version_id` | `BIGINT` | FK verso `score_version` |
| `sheet_music` | `transposition_override` | `SMALLINT` | facoltativo |
| `sheet_music` | `sound_bank_override` | `SMALLINT` | facoltativo; richiede `sound_program_override` |
| `sheet_music` | `sound_program_override` | `SMALLINT` | facoltativo |

Nuove tabelle:

**`score_version`**: `id`, `sheet_music_id`, `version_number` (progressivo per parte), `origin` (`OMR_AUDIVERIS`, `OMR_HOMR`, `UPLOAD_MUSICXML`, `UPLOAD_MIDI`, `CORRECTION`), `status` (`UNVERIFIED`, `APPROVED`), `media_asset_id` (file `.mxl`), `pages_fingerprint`, `recognition_job_id`, `parent_version_id`, `checks_json`, `layout_json`, `measure_count`, `approved_by`, `approved_at`, audit, `entity_version`. Vincolo univoco `(sheet_music_id, version_number)` sulle righe non cancellate.

**`score_recognition_job`**: `id`, `sheet_music_id`, `track_id`, `batch_id` (per **Riconosci tutte le parti**), `pages_fingerprint`, `status` (`QUEUED`, `RUNNING`, `DONE`, `FAILED`), `attempts`, `requested_by`, `started_at`, `finished_at`, `error_code`, `engines_json` (esito, durata e versione per motore), audit, `entity_version`. Al massimo un job `QUEUED` o `RUNNING` per parte, garantito da un indice univoco parziale.

**`score_error_report`**: `id`, `sheet_music_id`, `score_version_id`, `measure_number`, `message` (massimo 1000 caratteri), `reported_by`, `status` (`OPEN`, `CLOSED`), `closed_by`, `closed_at`, audit, `entity_version`.

`MediaRepository.isUnreferenced` aggiunge `score_version.media_asset_id` ai riferimenti. Le versioni sono categoria media `score-versions`.

All'eliminazione di una parte le sue versioni, i job e le segnalazioni seguono la cancellazione logica esistente. Le versioni non correnti e non approvate più vecchie delle ultime 20 per parte vengono eliminate da un job notturno.

### Schema pubblico

Nuova colonna `tenant.score_playback_enabled` in `master.xml`, come per le altre funzionalità per tenant.

### Coda

Nuova coda `score.recognition` sullo scambio `taurus.rabbitmq`, con messaggi JSON `{ tenant, jobId }`. Il consumer usa la concorrenza configurata e passa il job da `QUEUED` a `RUNNING` con un aggiornamento condizionale, come l'elaborazione dei PDF. Un job `RUNNING` oltre `stale-after` torna `QUEUED`; dopo tre tentativi diventa `FAILED`.

## API REST

### Gestione (super amministratore, amministratore, archivista)

Prefisso `/api/tracks/{trackId}`:

| Metodo e percorso | Descrizione |
| --- | --- |
| `POST /recognitions` | riconoscimento di tutte le parti con pagine; risponde `202` con i job |
| `POST /scores/{scoreId}/recognitions` | riconoscimento di una parte; `409` se c'è già un job attivo |
| `GET /recognitions` | job attivi e ultimo esito per parte |
| `GET /scores/{scoreId}/score-versions` | versioni con origine, stato, controlli e impronta |
| `GET /scores/{scoreId}/score-versions/{versionId}/file` | MusicXML della versione |
| `POST /scores/{scoreId}/score-versions/inspect` | contenuto di un file prima del caricamento: parti o tracce MIDI |
| `POST /scores/{scoreId}/score-versions` | caricamento (`file`, `partIndex`, `approve`) oppure correzione (`parentVersionId`, `content`, `approve`) |
| `POST /scores/{scoreId}/score-versions/{versionId}/approve` | approvazione |
| `POST /scores/{scoreId}/score-versions/{versionId}/current` | rende corrente la versione |
| `PUT /scores/{scoreId}/playback-settings` | sovrascritture di trasposizione e suono (banco e programma) |
| `PUT /playback-settings` | `tempoBpm` e parte di riferimento |
| `GET /error-reports` | segnalazioni della traccia |
| `POST /error-reports/{reportId}/close` | chiusura di una segnalazione |

Le operazioni di modifica richiedono `expectedTrackVersion` o `entityVersion`, coerentemente con il controllo di concorrenza già usato sulle tracce.

Strumenti, con il prefisso `/api/instruments`:

- le operazioni esistenti di creazione, modifica e lettura includono i nuovi campi e lo stato **configurato per l'ascolto**;
- l'elenco accetta il filtro `playbackConfigured`;
- `GET /playback-suggestion?name=…` restituisce la proposta calcolata dal nome, con la parola chiave riconosciuta e l'eventuale tonalità, oppure nessuna proposta.

### Ascolto (tutti i ruoli che vedono la traccia)

Per `ROLE_USER` il prefisso è `/api/user/tracks/{trackId}`, per `ROLE_USER_EXTERNAL` `/api/external/tracks/{trackId}`, per i ruoli con privilegi `/api/tracks/{trackId}`.

| Metodo e percorso | Descrizione |
| --- | --- |
| `GET /playback` | parti con disponibilità, stato della versione corrente, riepilogo dei controlli, trasposizione, suono, tempo e parte di riferimento |
| `GET /scores/{scoreId}/playback-file` | MusicXML della versione corrente, se utilizzabile per l'utente |
| `POST /scores/{scoreId}/error-reports` | segnalazione di un errore |

Il servizio applica le stesse regole di visibilità del dettaglio della traccia; una parte non visibile o senza versione utilizzabile risponde `404`. `SecurityConfiguration` aggiunge in modo esplicito il `POST` delle segnalazioni per `ROLE_USER` e `ROLE_USER_EXTERNAL`, oggi limitati alle letture.

### Errori

Secondo lo [Standard degli errori applicativi](application-errors-standard.md):

| Chiave | HTTP | Caso |
| --- | --- | --- |
| `scorePlayback.recognition.alreadyRunning` | 409 | job già in coda o in corso per la parte |
| `scorePlayback.recognition.noPages` | 422 | parte senza pagine |
| `scorePlayback.recognition.unavailable` | 503 | servizio OMR non configurato o non raggiungibile |
| `scorePlayback.upload.unsupportedFormat` | 415 | file diverso da MusicXML o MIDI |
| `scorePlayback.upload.invalid` | 422 | file non leggibile, con `args.reason` |
| `scorePlayback.upload.partRequired` | 422 | file con più parti senza indicazione di quella da importare |
| `scorePlayback.upload.tooLarge` | 413 | file oltre 10 MB |
| `scorePlayback.version.notFound` | 404 | versione inesistente |
| `scorePlayback.version.conflict` | 409 | parte modificata da un'altra richiesta |
| `scorePlayback.settings.invalid` | 422 | trasposizione, suono o tempo fuori intervallo, anche nel dettaglio dello strumento; banco senza programma |

I codici di errore dei job (`engineTimeout`, `engineFailed`, `tooManyPages`, `unreadableImage`) sono restituiti come dati e tradotti dal frontend.

## Interfaccia utente

La vista segue il [Taurus Layout Standard](taurus-layout-standard.md) e la documentazione PrimeNG in `docs/llms-full.md`.

- **Dettaglio della traccia**: **Ascolta la partitura** accanto ad **Anteprima**; per chi ha privilegi anche **Riconosci tutte le parti** e **Impostazioni di ascolto** (tempo, parte di riferimento).
- **Workspace Parti**: per ogni parte stato del riconoscimento, etichetta della versione (**Non verificata**, **Approvata**), avvisi, **Ascolta**, e per chi ha privilegi **Riconosci**, **Correggi**, **Carica MusicXML o MIDI**, **Scarica MusicXML**, **Versioni**, **Approva**.
- **Lettore della parte**: spartito con cursore, controlli di ascolto, etichetta e riepilogo dei controlli, **Segnala un errore**.
- **Lettore della partitura**: spartito della parte selezionata, mixer, controlli di ascolto.
- **Versioni**: elenco con origine, data, autore, stato, controlli, e azioni **Usa questa versione**, **Approva**, **Scarica**.
- **Editor di correzione**: vista a schermo intero descritta in [Editor di correzione](#editor-di-correzione).
- **Strumenti**: nel dettaglio, trasposizione e suono con la proposta calcolata dal nome, il selettore del suono per famiglia e **Prova**; nell'elenco, l'indicazione e il filtro **Da configurare per l'ascolto**.
- **Avviso di strumento non configurato**: nel lettore e nel workspace, con collegamento al dettaglio dello strumento per chi ha privilegi.
- **Utenti senza privilegi**: **Ascolta** disabilitato con il testo «Ascolto non ancora disponibile» quando la parte non ha una versione utilizzabile.

Le azioni sono nascoste se la funzionalità del tenant è disattivata; le azioni di riconoscimento sono nascoste anche quando il servizio OMR non è configurato.

## Autorizzazioni e isolamento tenant

| Operazione | Utente, esterno | Archivista | Admin | Super admin |
| --- | ---: | ---: | ---: | ---: |
| Ascoltare parti e partitura visibili | sì | sì | sì | sì |
| Segnalare un errore | sì | sì | sì | sì |
| Avviare il riconoscimento | no | sì | sì | sì |
| Caricare, correggere, approvare, scegliere la versione | no | sì | sì | sì |
| Configurare trasposizione, suono e tempo | no | sì | sì | sì |
| Chiudere le segnalazioni | no | sì | sì | sì |

- Le tabelle sono nello schema del tenant; il job porta il tenant e il worker imposta il contesto del tenant prima di leggere pagine e salvare versioni.
- Il servizio OMR non riceve identificativi di tenant, tracce o utenti: solo immagini o file.
- Tutti gli endpoint sono protetti da `@RequiresTenantFeature(TenantFeature.SCORE_PLAYBACK)`.

## Osservabilità e sicurezza

- Metriche: `taurus.score.recognition{engine,outcome}`, durata per motore, job in coda, `taurus.score.upload{format,outcome}`, `taurus.score.error_reports`.
- Log con identificativi di job, parte e tenant ed esito; mai contenuto dei file o testo delle segnalazioni.
- MusicXML: parsing con entità esterne e DTD disabilitate; `.mxl`: al massimo 50 MB decompressi e 100 voci nello zip; MIDI: al massimo 10 MB.
- Servizio OMR: utente non privilegiato nel container, file system di sola lettura salvo la directory temporanea, limiti di CPU e memoria, nessun accesso a Internet in esecuzione.

## Licenze

| Componente | Licenza | Uso |
| --- | --- | --- |
| Audiveris | AGPL-3.0 | processo separato nel servizio OMR, non modificato |
| homr | AGPL-3.0 | processo separato nel servizio OMR, non modificato |
| Pesi dei modelli di homr | AGPL-3.0, con dati di addestramento sotto licenze diverse, tra cui Camera-PrIMuS (CC BY-NC 4.0 nella copia su Hugging Face) | ammesso perché Taurus non è commerciale; va rivalutato se Taurus diventasse commerciale |
| Tesseract e dati linguistici | Apache-2.0 | servizio OMR |
| music21 | BSD-3-Clause | servizio OMR |
| Verovio | LGPL-3.0 o successiva | frontend, file separato non modificato |
| SpessaSynth | Apache-2.0 | frontend |
| SoundFont MuseScore General | MIT | frontend, con le note di copyright |

Il nuovo modulo `taurus-omr` include i testi delle licenze e un riferimento ai sorgenti dei motori usati.

## Strategia di test

### Servizio OMR

- Test di accuratezza: un insieme di MusicXML di riferimento viene disegnato con Verovio in immagini a 300 DPI, riconosciuto e confrontato battuta per battuta; il risultato minimo per motore è fissato nel test, così un aggiornamento che peggiora fallisce.
- Unione dei movimenti di Audiveris, posizioni delle battute, controlli, conversione MIDI.
- Limiti di dimensione, timeout, chiave condivisa.

### Backend

- Ciclo del job: coda, esecuzione, completamento, errore, recupero dei job bloccati, unicità per parte.
- Scelta della versione proposta e rispetto della versione approvata.
- Regole di disponibilità per ruolo, incluse pagine cambiate.
- Caricamenti: formati, file con più parti, file non validi, protezione XML.
- Autorizzazioni, isolamento tenant e visibilità delle parti per `ROLE_USER` e `ROLE_USER_EXTERNAL`.
- Migrazioni Liquibase e media non rimossi dalla pulizia finché referenziati.
- Proposta per gli strumenti: normalizzazione del nome, parola chiave più lunga, parole intere, tonalità nel nome (corno in Mib −9, clarinetto piccolo in Lab +8, sax tenore in Do −12), assenza di proposta senza parole chiave.
- Attività di avvio: compila solo i campi vuoti, non sovrascrive, è ripetibile senza effetti.
- Importazione iniziale con e senza le colonne di suono e trasposizione.

### Frontend

- Svolgimento dei ritornelli: ritornelli semplici e annidati, prime e seconde volte, D.C. al Fine, D.S. al Coda, opzione senza ritornelli.
- Unione delle parti, pause multiple, lunghezze diverse.
- Trasposizione, chiave di basso, percussioni, strumento non configurato.
- Selettore del suono e **Prova**.
- Mixer, tempo, intervallo, metronomo.
- Disponibilità e testi per ruolo.
- Editor: ogni operazione, controllo di durata, salvataggio.

### Verifica manuale

- Parti reali della banda: digitali, scansioni e foto.
- Ascolto su telefono di fascia media, con misura della memoria usata dal SoundFont.

## Criteri di accettazione

- [ ] Con la funzionalità disattivata nessuna azione di ascolto o riconoscimento è visibile.
- [ ] Un archivista avvia il riconoscimento di una parte e, al termine, ascolta la versione proposta con controlli ed etichetta.
- [ ] **Riconosci tutte le parti** produce un job per parte e una sola notifica finale.
- [ ] Un utente senza privilegi ascolta una versione non verificata e vede «Ascolto non ancora disponibile» sulle parti senza versione utilizzabile.
- [ ] Il caricamento di un MusicXML con più parti chiede quale importare; un MIDI viene convertito e ascoltato.
- [ ] Una correzione salvata crea una nuova versione; l'approvazione la marca come approvata senza modificarla.
- [ ] La partitura suona tutte le parti disponibili, con mixer, **Solo la mia parte** e **Tutto tranne la mia parte**.
- [ ] Ritornelli, volte e salti sono eseguiti correttamente, e l'opzione senza ritornelli li salta.
- [ ] Le parti di strumenti traspositori suonano in suoni reali, anche in chiave di basso.
- [ ] Creando lo strumento «Corno in Mib» Taurus propone suono French Horn e trasposizione −9, che l'archivista può provare e confermare.
- [ ] Uno strumento senza proposta resta non configurato: le sue parti si ascoltano con l'avviso e lo strumento compare nel filtro **Da configurare per l'ascolto**.
- [ ] Una segnalazione d'errore notifica archivisti e amministratori e si chiude con l'approvazione di una nuova versione.
- [ ] Nessun dato di un tenant è accessibile da un altro tenant.

## Piano di implementazione

1. **Ascolto da file caricati**: modello dati, funzionalità per tenant, caricamento MusicXML e MIDI (conversione MIDI nel servizio OMR), campi di strumento e traccia con catalogo di riferimento, proposta e attività di avvio, lettore della parte e della partitura con mixer, tempo, intervallo e ritornelli.
2. **Riconoscimento**: modulo `taurus-omr`, coda e job, controlli, scelta della versione, notifiche, versioni e approvazione, scaricamento per MuseScore.
3. **Correzione**: editor con pagina affiancata, segnalazioni d'errore.
4. **Qualificazione**: test di accuratezza, prove con parti reali, prove su telefono, runbook.

## Compatibilità e adozione

- La funzionalità è disattivata per default; senza servizio OMR configurato restano disponibili caricamento e ascolto.
- Le colonne nuove sono facoltative o hanno un predefinito: tracce e parti esistenti non cambiano.
- Al rilascio l'attività di avvio compila con la proposta i campi vuoti degli strumenti esistenti; quelli senza proposta restano non configurati e compaiono nel filtro **Da configurare per l'ascolto**.
- Rollback: disattivare la funzionalità; tabelle, versioni e file restano per una riattivazione.

Quando l'implementazione inizia, vanno aggiornati anche:

- `AGENTS.md`, con il nuovo modulo `taurus-omr` e i suoi comandi;
- `scripts/docs/feature-catalog-lib.mjs`, aggiungendo `taurus-omr` ai moduli ammessi dal catalogo;
- i workflow `verify.yml` e `docker-publish.yml`, con test e pubblicazione dell'immagine OMR;
- [Onboarding guidato e importazione iniziale del tenant](tenant-onboarding-import-spec.md), con le nuove colonne facoltative del foglio `Strumenti`;
- [Runbook operativo](runbook-operativo.md) e [Runbook di test](runbook-test.md).

## Questioni aperte

1. **Partitura per gli utenti senza privilegi**: oggi `ROLE_USER` vede solo le parti dei propri strumenti. Proposta: nella partitura si sentono tutte le parti disponibili, ma si vede lo spartito solo delle parti visibili all'utente.
2. **Utenti esterni**: proposta di applicare le stesse regole di ascolto alle tracce `PUBLIC` che già vedono.
3. **Catalogo di riferimento**: valori e parole chiave da verificare con un archivista, in particolare corno in Fa o in Mib, parti in chiave di basso, percussioni intonate e nomi locali degli strumenti.
4. **SoundFont specifico per banda**: valutare in futuro un SoundFont gratuito con suoni di flicorni ed euphonium; il salvataggio del suono come banco e programma lo consente senza modifiche al modello dati.
5. **Prototipi tecnici** da completare prima della fase indicata:
   - corrispondenza tra elementi di Verovio e nodi MusicXML per l'editor (fase 3); in alternativa si valuta MEI come formato di lavoro dell'editor;
   - applicazione di `<transpose>` nel MIDI generato da Verovio (fase 1);
   - più di 16 canali in SpessaSynth per partiture con molte parti (fase 1);
   - tempi di generazione del MIDI per una partitura di 30 parti (fase 1);
   - memoria del SoundFont `.sf3` su telefono, con alternativa più leggera se necessario (fase 1).
6. **Licenza dei modelli di homr**: l'uso resta ammesso finché Taurus non è commerciale.
