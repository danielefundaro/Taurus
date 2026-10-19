# Editor di scrittura musicale

L'editor consente di scrivere parti e partiture direttamente in Taurus: una parte nuova da zero, una parte ricavata da un'altra per uno strumento diverso, un arrangiamento completo per l'organico della banda da cui estrarre le parti. Le parti scritte si ascoltano, si correggono, si approvano, si impaginano e si stampano come quelle ricavate dai PDF. Estende l'[editor di correzione](track-score-playback-spec.md#editor-di-correzione) dell'ascolto delle parti, che corregge una versione esistente ma non serve a scrivere.

## Stato del documento

ID catalogo: `music-writing-editor`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

Approvato il 18/10/2026, dopo la revisione delle dodici questioni aperte della prima bozza, decise e raccolte in [Decisioni della revisione](#decisioni-della-revisione).

Documenti collegati: [Ascolto e correzione delle parti](track-score-playback-spec.md), [Workspace per la gestione delle parti](track-parts-workspace-spec.md), [Anteprima e stampa degli spartiti](score-preview-printing-spec.md), [Gestione centralizzata dei media](media-asset-spec.md), [Funzionalità configurabili per tenant](tenant-feature-flags-spec.md), [Standard degli errori applicativi](application-errors-standard.md).

## Contesto

- Ogni parte di una traccia (`sheet_music`) ha pagine immagine ricavate dai PDF e, con l'ascolto, versioni MusicXML immutabili (`score_version`) prodotte da riconoscimento, caricamento o correzione. Anteprima, presentazione e stampa usano solo le pagine immagine.
- L'editor di correzione lavora sul DOM MusicXML di **una sola parte** (`src/app/score-editor/`), disegnata da Verovio, che conserva gli `id` di battute e note. Corregge altezze, durate, accidenti, legature, gruppi irregolari, metro, armatura, chiave, battute, ritornelli, volte, salti, tempo, dinamiche e articolazioni, con controllo della durata delle battute, annulla e ripristina su istantanee del documento e ascolto del passaggio.
- Lo strumento dichiara trasposizione e suono (`instrument.transposition_semitones`, `bass_clef_transposition_semitones`, `sound_program`): Taurus sa già quanto traspone ogni strumento.
- Il lettore costruisce l'ascolto da più MusicXML e sa leggere una parte qualsiasi di un file con più parti (`parseMusicXml(xml, partIndex)`).
- Il backend invia `Permissions-Policy` con `midi=()` (`SecurityConfiguration`); la pagina dell'applicazione è servita da nginx, che oggi non imposta intestazioni di sicurezza.
- Misure della fase 4 dell'ascolto: Verovio ridisegna una parte di 300 battute in circa 0,2 secondi su un computer di sviluppo e in 1–3 secondi su uno lento; l'editor riunisce già le modifiche rapide in un solo disegno per fotogramma.

## Obiettivo

- Scrivere una parte da zero con la tastiera, il mouse o una tastiera di pianoforte a schermo, in pochi minuti per una parte da banda di una pagina.
- Ricavare da una parte esistente la parte di un altro strumento, con la trasposizione calcolata dagli strumenti.
- Scrivere o arrangiare una partitura completa e estrarne le parti, ciascuna nella trasposizione del proprio strumento.
- Impaginare le parti scritte in pagine e PDF, così da usarle in anteprima, presentazione, stampa e preparazione degli eventi come le parti ricavate dai PDF.
- Non perdere lavoro: bozze salvate in automatico, un solo autore alla volta per parte o partitura, versioni immutabili.

## Fuori scope

- Incisione professionale: posizionamento manuale fine di ogni simbolo, collisioni risolte a mano, stili tipografici personalizzati. Si accetta l'impaginazione automatica di Verovio con poche opzioni.
- Modifica contemporanea della stessa parte da più persone in tempo reale.
- Testo cantato, sigle degli accordi, intavolature, notazione antica.
- Scrittura a mano libera, riconoscimento della voce o del canto.
- Uscita MIDI verso strumenti esterni e registrazione in tempo reale con quantizzazione: la tastiera MIDI serve all'inserimento nota per nota.
- Esportazione audio, armonizzazione o orchestrazione automatiche, suggerimenti generati.
- Scrittura sul telefono: sul telefono le parti scritte si consultano e si ascoltano.

## Casi d'uso

1. **Parte perduta o illeggibile**: l'archivista riscrive la parte guardando una fotocopia o ascoltando una registrazione.
2. **Strumento mancante**: la banda ha un sax contralto in Mib ma il brano ha solo la parte di clarinetto in Sib; si ricava la parte trasposta, con controllo dell'estensione.
3. **Parte semplificata**: per un allievo si parte dalla parte esistente e si semplificano ritmi e passaggi.
4. **Arrangiamento**: il maestro arrangia un brano per l'organico della banda in partitura, poi estrae le parti.
5. **Composizione**: un brano nuovo, scritto direttamente in partitura.

## Decisioni principali

1. **Stesso motore dell'editor di correzione.** Il documento resta MusicXML modificato nel DOM del browser e disegnato da Verovio; i moduli di `src/app/score-editor/` vengono estesi, non sostituiti. Il formato è già quello di versioni, ascolto e scaricamento per MuseScore, e la fase 3 dell'ascolto ha mostrato che Verovio conserva gli `id` necessari alla selezione. Le alternative sono nella [tabella delle alternative](#alternative-valutate).
2. **Due livelli: parte e partitura.** L'editor di parte (un rigo, fino a quattro voci) arriva per primo e copre i casi 1–3. L'editor di partitura (più parti in sistemi) arriva dopo e copre i casi 4 e 5. Il codice è lo stesso: la partitura è un documento con più `part`.
3. **Battute sempre complete durante la scrittura.** Nella scrittura una nota più lunga dello spazio rimasto si divide e si lega oltre la stanghetta, le note tolte diventano pause e ogni battuta resta della durata del metro, come in MuseScore. La correzione continua ad accettare battute sbagliate, perché deve poter mostrare gli errori del riconoscimento.
4. **Inserimento con la tastiera del computer e con una tastiera MIDI.** Una modalità di inserimento con cursore, le scorciatoie già usate dall'editor di correzione (durate 1–7, punto, pausa, alterazioni, legatura, grado e ottava) più quelle di MuseScore per l'inserimento (`N`, tasti A–G, accordo con `Maiusc`), e il mouse per la selezione. Una tastiera MIDI collegata al computer scrive le note suonate fin dalla prima fase (vedi [Tastiera MIDI](#tastiera-midi)). Per tablet e per chi non conosce le scorciatoie c'è una tastiera di pianoforte a schermo e una palette.
5. **Bozze sul server, versioni solo al salvataggio.** L'editor salva una bozza automaticamente, pochi secondi dopo l'ultima modifica; la bozza non è una versione e non si ascolta dal lettore. **Salva** produce una versione non verificata, **Salva e approva** una versione approvata, come nella correzione: chi scrive approva il proprio lavoro, senza una seconda persona. Le versioni restano immutabili.
6. **Un solo autore alla volta.** Aprire l'editor prende un blocco sulla parte o sulla partitura, rinnovato finché l'editor è aperto e scaduto dopo due minuti senza segnali. Gli altri vedono chi sta scrivendo e aprono in sola lettura; chi può gestire la traccia può prendere il controllo, e la bozza dell'altro resta.
7. **La partitura è la fonte delle parti che ne derivano.** Estrarre le parti crea, per ogni parte della partitura, una nuova versione della parte corrispondente della traccia (origine `ARRANGEMENT`), trasposta per il suo strumento. Una parte estratta può essere corretta da sola; la partitura segnala che la parte non coincide più con l'estrazione e lascia scegliere se estrarla di nuovo.
8. **Trasposizione dagli strumenti.** La partitura si scrive e si mostra in suoni reali o trasposta, con un comando; le parti estratte usano la trasposizione dello strumento assegnato alla parte, compresa quella in chiave di basso. Uno strumento non configurato blocca l'estrazione della sua parte con un messaggio, invece di indovinare.
9. **Impaginazione con Verovio, pagine come quelle dei PDF.** Una versione si impagina in pagine PNG a 300 DPI e in un PDF, con formato, dimensione del rigo e intestazione scelti dall'utente. Le pagine generate diventano pagine della parte, così anteprima, presentazione, stampa e preparazione degli eventi funzionano senza modifiche. La generazione avviene nel servizio `taurus-omr`, che diventa il servizio musicale interno e mantiene il nome del modulo.
10. **Ruoli e attivazione.** Scrivono super amministratore, amministratore e archivista, come per correzione e approvazione. Nuova funzionalità per tenant `SCORE_WRITING`, disattivata per default e dipendente da `SCORE_PLAYBACK`.
11. **Computer e tablet.** L'editor è progettato per computer e utilizzabile su tablet in orizzontale con la tastiera di pianoforte a schermo; sul telefono le azioni di scrittura non compaiono.
12. **Disegno per pagina.** Con parti lunghe e partiture l'editor non ridisegna più tutto a ogni modifica: disegna la pagina visibile, con Verovio in un Web Worker, e ridisegna il resto in background. Obiettivo: meno di 150 ms tra tasto e nota disegnata su una partitura di 30 parti.

### Alternative valutate

| Alternativa | Motivo per cui non è la scelta |
| --- | --- |
| Editor online commerciali (Flat, Noteflight) | a pagamento per uso di gruppo e con i dati fuori da Taurus; contrari al vincolo di costo zero |
| MuseScore | applicazione desktop GPL, non incorporabile nel browser; resta il percorso alternativo con scaricamento e nuovo caricamento, già disponibile |
| OpenSheetMusicDisplay, VexFlow | disegnano lo spartito ma non lo modificano: andrebbero costruiti sopra modello, impaginazione ed editing, che Verovio e l'editor di correzione già forniscono |
| abcjs e notazione ABC | scrittura testuale comoda per melodie semplici, ma ABC non rappresenta tutto ciò che serve a una parte da banda e richiederebbe conversioni a ogni passaggio |
| Editor basati su MEI (Verovio editor, mei-friend) | MEI è più ricco di MusicXML ma richiederebbe di convertire ogni versione e di cambiare il formato già usato da ascolto e scaricamento; da riconsiderare solo se MusicXML diventasse un limite |

## Notazione supportata

Nella prima versione dell'editor di parte, oltre a ciò che la correzione già gestisce:

- inserimento di note, accordi e pause con cursore, fino a quattro voci per rigo;
- selezione di intervalli di note e battute, copia, taglia, incolla e ripetizione del passaggio selezionato;
- trasposizione di un passaggio o di tutta la parte, per intervallo o di ottava;
- legature di portamento, forcelle di crescendo e diminuendo, corona, respiro, abbellimenti (acciaccatura e appoggiatura);
- lettere di prova e numeri di riferimento, testo di espressione (per esempio «dolce», «rit.», «a tempo»), cambi di tempo con testo;
- interruzioni di riga e di pagina, numero di battute per riga suggerito;
- chiave di percussione con le note mappate sui suoni di batteria dello strumento, per le parti di percussione.

Più avanti: battute in levare con metro diverso, metri composti misti (per esempio 2+3/8), parti per strumenti a due righi (glockenspiel, arpa, tastiere), note di richiamo (cue), ottava sopra e sotto (8va, 8vb), tremoli.

## Flussi

### Nuova parte da zero

1. Nel workspace Parti della traccia l'archivista sceglie **Nuova parte scritta**.
2. Indica descrizione e strumenti della parte, poi chiave, armatura, metro, tempo, numero di battute iniziali e battuta in levare. Chiave e trasposizione propongono i valori dello strumento; armatura e tempo propongono quelli delle altre parti della traccia, se esistono.
3. Taurus crea la parte senza pagine e apre l'editor con le battute di pausa.
4. L'archivista scrive; la bozza si salva da sola.
5. **Salva** crea la versione 1 con origine `WRITING`, non verificata; **Salva e approva** la crea approvata.
6. **Impagina** genera pagine e PDF (vedi [Impaginazione](#impaginazione)); da quel momento la parte compare in anteprima e stampa.

### Parte ricavata da un'altra

1. Dalla versione corrente di una parte l'archivista sceglie **Crea una parte da questa**.
2. Sceglie lo strumento di destinazione. Taurus calcola l'intervallo dalla trasposizione dei due strumenti, propone l'ottava che porta le note più vicine all'estensione dello strumento, mostra l'armatura risultante e segnala le note fuori estensione, se l'estensione è configurata.
3. Si apre l'editor sulla parte trasposta; la parte di origine è indicata nella versione (`parent_version_id`) e nella nuova parte.
4. Il resto segue la nuova parte da zero, dal punto 4.

### Partitura e arrangiamento

1. Nella nuova scheda **Partitura** della traccia il maestro sceglie **Nuova partitura**, da un organico (gli strumenti del tenant, nell'ordine tipico della banda) o dalle parti esistenti della traccia.
2. Dalle parti esistenti Taurus unisce le versioni correnti allineando le battute; se le parti hanno numero di battute o ritornelli diversi, lo segnala e lascia scegliere la parte di riferimento, come nella partitura del lettore.
3. L'editor mostra i sistemi con tutte le parti; si possono nascondere le parti, comprimere i righi vuoti e passare tra suoni reali e trasposizione.
4. **Salva** crea una versione della partitura, **Salva e approva** la crea approvata.
5. **Estrai le parti** mostra, per ogni parte della partitura, la parte della traccia che riceverà la nuova versione o una parte nuova da creare, e lo stato: nuova, aggiornata, modificata a mano dopo l'ultima estrazione. Dopo la conferma Taurus crea le versioni con origine `ARRANGEMENT`, trasposte per strumento, approvate se lo è la versione della partitura, e offre di impaginarle tutte. Quando le parti estratte sono approvate, i soci che suonano quegli strumenti ricevono una notifica.

### Bozza e ripresa

- La bozza si salva pochi secondi dopo l'ultima modifica e comunque ogni 30 secondi durante la scrittura; una copia resta nel browser se la rete manca, e viene inviata al ritorno della connessione.
- Riaprendo l'editor con una bozza, Taurus chiede se riprenderla, con data e ora, o scartarla.
- Se nel frattempo è stata salvata un'altra versione della stessa parte, la ripresa lo segnala: salvando, la bozza diventa comunque la versione corrente, dopo una conferma esplicita. Non c'è unione automatica.

### Blocco e presa di controllo

- Chi apre l'editor prende il blocco; un altro utente vede «In modifica da Mario Rossi dalle 10:32» e apre l'editor in sola lettura.
- **Prendi il controllo**, dopo una conferma, passa il blocco: l'editor dell'altro diventa di sola lettura e lo avvisa; la sua bozza resta disponibile a chi riprende.
- Il blocco scade da solo dopo due minuti senza segnali, per esempio se il browser si chiude.

### Impaginazione

1. Da una versione l'archivista sceglie **Impagina**: formato (A4 verticale, A4 orizzontale, formato marcia di 18 × 13 cm in orizzontale), dimensione del rigo, margini, intestazione (titolo della traccia, compositore, arrangiatore, nome della parte) e numerazione delle pagine.
2. Il browser mostra subito un'anteprima con Verovio.
3. **Genera pagine** avvia un job: il servizio musicale produce le pagine PNG a 300 DPI e il PDF.
4. Al termine Taurus chiede se **sostituire le pagine** della parte o **aggiungerle in una parte nuova**; per una parte scritta da zero, senza pagine, le aggiunge direttamente. Le pagine sostituite restano come media della traccia secondo le regole di pulizia esistenti.
5. Pagine e PDF ricordano la versione da cui derivano: l'editor segnala quando la versione corrente è più recente delle pagine e propone di impaginare di nuovo.

### Scaricamento

MusicXML compresso, MIDI e PDF di una versione, come oggi il MusicXML per MuseScore.

## Interfaccia utente

L'editor si apre a schermo intero, come quello di correzione.

- **Barra superiore**: modalità **Selezione** e **Inserimento**, durata e punti correnti, voce attiva da 1 a 4, annulla e ripristina, ascolto dal punto selezionato, stato della bozza («Salvata alle 10:41»), **Tastiera MIDI**, **Salva**, **Salva e approva**, **Impagina**.
- **Palette laterale** a schede: Note, Battuta (metro, armatura, chiave, battute), Struttura (ritornelli, volte, salti, lettere di prova), Espressione (dinamiche, forcelle, articolazioni, legature, testo), Impaginazione (interruzioni di riga e di pagina). Ogni comando indica la sua scorciatoia.
- **Spartito** al centro, con il cursore di inserimento visibile come una barra verticale colorata nella posizione dove andrà la prossima nota.
- **Barra di stato**: battuta, tempo nella battuta, voce, nota selezionata letta per esteso («Battuta 12 · tempo 3 · Sib4 · croma puntata»), già annunciata a chi usa uno screen reader; controlli in tempo reale.
- **Tastiera di pianoforte a schermo**, mostrata su tablet o a richiesta: un tocco inserisce la nota alla durata corrente.
- **Partitura**: elenco delle parti con mostra e nascondi, righi vuoti compressi, interruttore tra suoni reali e trasposizione, scelta della parte da ascoltare in evidenza.
- **Ricavata da**: per le parti estratte o trasposte, un'etichetta con la parte o la partitura di origine e, se divergono, un avviso.

### Scorciatoie principali

| Tasto | Azione |
| --- | --- |
| `N` | entra ed esce dalla modalità di inserimento |
| `A`–`G` | inserisce la nota all'ottava più vicina alla precedente |
| `1`–`7` | durata da semibiscroma a semibreve, come in MuseScore |
| `.` | punto di valore |
| `0` | pausa della durata corrente |
| `Maiusc` + `A`–`G` | aggiunge la nota all'accordo |
| `↑` `↓` | un grado; con `Ctrl` un'ottava |
| `+` `-` `=` | diesis, bemolle, bequadro |
| `T` | legatura di valore con la nota seguente |
| `Canc` | elimina la nota, che diventa pausa nella scrittura |
| `S` | legatura di portamento sulla selezione |
| `Ctrl` + `3` | terzina (con `5`, `6` gli altri gruppi irregolari) |
| `Ctrl` + `1`–`4` | voce attiva |
| `Ctrl` + `C`, `X`, `V` | copia, taglia, incolla; `R` ripete la selezione |
| `Ctrl` + `Z`, `Y` | annulla, ripristina |
| `Spazio` | ascolta dalla selezione o ferma |

Le scorciatoie della tabella che esistono già nell'editor di correzione mantengono lo stesso significato.

### Tastiera MIDI

- **Collegamento**: **Tastiera MIDI** nella barra superiore chiede al browser, la prima volta, il permesso di usare i dispositivi MIDI, poi elenca le tastiere collegate e usa quella scelta, ricordandola nel browser. Una tastiera collegata con l'editor già aperto compare da sola nell'elenco.
- **Inserimento**: in modalità di inserimento ogni nota suonata si scrive al cursore con la durata corrente e il cursore avanza; le note premute insieme diventano un accordo. La durata si cambia con i tasti 1–7 del computer o con la palette, come in MuseScore.
- **Correzione**: fuori dalla modalità di inserimento una nota suonata sostituisce l'altezza della nota selezionata.
- **Grafia**: i tasti neri si scrivono secondo l'armatura in vigore, con diesis nelle armature in diesis e bemolli in quelle in bemolle; `+`, `-` e `=` correggono la grafia.
- **Altezza**: la nota suonata è quella che si vede. Nella partitura in suoni reali è il suono reale; nelle parti e nella partitura trasposta è la nota scritta dello strumento.
- **Ascolto**: la nota suonata si sente subito con il suono della parte, attraverso il sintetizzatore del lettore.
- **Messaggi ignorati**: intensità, pedale e controlli; Taurus non chiede l'accesso ai messaggi di sistema (sysex).
- **Browser**: Chrome, Edge e Opera su computer e Android; Firefox dalla versione 108, dopo che l'utente accetta il permesso del sito che il browser propone. Safari e iPad non supportano Web MIDI: lì il comando non compare e restano la tastiera del computer e il pianoforte a schermo.
- **Pagina**: Web MIDI richiede HTTPS e una `Permissions-Policy` che lo consenta. Il backend passa da `midi=()` a `midi=(self)`, e la stessa regola entra nelle intestazioni del frontend quando verranno definite.

## Architettura

### Frontend

- **Documento**: `ScoreDocument` passa da una parte a più parti e più righi, con voci da 1 a 4; i metodi esistenti restano validi per la parte singola. Le regole di accidenti, travature, divisioni e ordine degli elementi MusicXML della correzione restano le stesse.
- **Cursore**: posizione come parte, rigo, voce, battuta e istante nella battuta in frazioni esatte; le operazioni di inserimento lavorano sul cursore, quelle di correzione sulla selezione.
- **Operazioni**: le funzioni di `score-edits.ts` si affiancano a operazioni di scrittura (inserisci al cursore, riempi con pause, dividi e lega oltre la stanghetta, copia e incolla di frammenti MusicXML, trasponi). Ogni operazione dichiara le battute che cambia.
- **Cronologia**: per la parte singola restano le istantanee del documento; per la partitura la cronologia registra solo le battute cambiate, prima e dopo, per non tenere in memoria cento copie di un file di diversi megabyte.
- **Disegno**: Verovio in un Web Worker, con la selezione di un intervallo di battute per disegnare solo la pagina visibile e le battute vicine; il resto si aggiorna in background. La mappa tra `id` e elementi disegnati resta quella della correzione.
- **Ascolto**: il lettore esistente, che già legge una parte qualsiasi di un file a più parti e applica trasposizioni e suoni.
- **MIDI**: un servizio legge i dispositivi con Web MIDI, riunisce in un accordo le note premute insieme, sceglie la grafia dall'armatura e passa le note alle stesse operazioni di inserimento della tastiera del computer.
- **Bozze e blocco**: un servizio che salva la bozza con ritardo, ne conserva una copia in IndexedDB e rinnova il blocco.

### Backend

- **Versioni delle parti**: `score_version` esistente, con le nuove origini `WRITING` e `ARRANGEMENT`. Il salvataggio usa la stessa richiesta JSON della correzione, con controlli e limiti invariati.
- **Partiture**: nuove entità per la partitura della traccia e le sue versioni, con lo stesso modello immutabile delle parti.
- **Estrazione**: il backend divide la versione della partitura in parti, applica la trasposizione dello strumento e crea le versioni delle parti in una transazione; i controlli MusicXML esistenti (`MusicXmlInspector`) valgono anche qui.
- **Impaginazione**: job su una nuova coda RabbitMQ, con lo stesso schema del riconoscimento (tabella dei job come fonte di verità, recupero pianificato, notifica al termine).

### Servizio musicale

`taurus-omr` aggiunge `POST /v1/engravings`: riceve un MusicXML e le opzioni di impaginazione e restituisce il PDF e le pagine PNG. Usa Verovio per Python e Cairo, gli stessi strumenti con cui il test di accuratezza disegna il proprio corpus, già provati con il carattere musicale incorporato. Il servizio resta interno, senza stato e senza accesso a database o storage.

## Modello dati e migrazioni

Changelog nello schema del tenant. Tutte le nuove tabelle hanno i campi di audit e, se modificabili, `entity_version`.

| Tabella | Contenuto |
| --- | --- |
| `score_version` (esistente) | nuove origini `WRITING` e `ARRANGEMENT`; nuova colonna facoltativa `arrangement_version_id` per le versioni estratte |
| `track_arrangement` | `id`, `track_id`, `name`, `current_version_id`, `concert_pitch` (scrittura in suoni reali) |
| `arrangement_version` | `id`, `arrangement_id`, `version_number`, `status`, `media_asset_id` (MusicXML compresso a più parti), `parent_version_id`, `part_map_json` (parte della partitura → parte della traccia), `measure_count`, `checks_json`, approvazione; univoco `(arrangement_id, version_number)` |
| `score_draft` | `id`, tipo e identificativo del bersaglio (parte o partitura), `base_version_id`, `content` (MusicXML compresso, al massimo 10 MB), `saved_by`, `saved_at`, `entity_version`; una bozza per bersaglio |
| `score_edit_lock` | tipo e identificativo del bersaglio, `owner`, `owner_name`, `acquired_at`, `heartbeat_at`, `expires_at`; univoco per bersaglio |
| `score_engraving_job` | `id`, `sheet_music_id`, `score_version_id`, `options_json`, `status`, `attempts`, `error_code`, `pdf_media_asset_id`, `page_count`, audit |
| `sheet_music` (esistente) | `engraved_from_version_id` facoltativo: versione da cui derivano le pagine generate |
| `instrument` (esistente) | `lowest_written_pitch`, `highest_written_pitch`: estensione scritta come numeri di nota MIDI, facoltativi, proposti dal catalogo di riferimento come suono e trasposizione |
| `track` (esistente) | `arranger` facoltativo, come `composer`: mostrato nel dettaglio della traccia e nell'intestazione delle pagine |

Pagine e PDF generati sono media delle categorie esistenti; `MediaRepository.isUnreferenced` aggiunge i riferimenti dei job e delle partiture. La funzionalità `SCORE_WRITING` richiede una colonna del flag nel registro dei tenant, come le altre funzionalità.

## API REST

Sotto `/api/tracks/{trackId}`, per super amministratore, amministratore e archivista con la funzionalità attiva:

| Metodo e percorso | Descrizione |
| --- | --- |
| `POST /scores/{scoreId}/score-versions` (esistente) | salvataggio di una versione scritta, con `origin: WRITING` |
| `GET`, `PUT`, `DELETE /scores/{scoreId}/score-draft` | bozza della parte; `PUT` con `entityVersion` per evitare sovrascritture |
| `POST`, `PUT`, `DELETE /scores/{scoreId}/edit-lock` | prende, rinnova e rilascia il blocco; `POST ?takeOver=true` prende il controllo |
| `POST /scores/{scoreId}/derive` | crea una parte nuova da una versione, con strumento di destinazione e ottava |
| `GET`, `POST /arrangements`, `GET /arrangements/{id}/versions` | partiture della traccia e loro versioni |
| `POST /arrangements/{id}/versions/{versionId}/extract` | estrazione delle parti, con la corrispondenza confermata |
| `GET`, `PUT`, `DELETE /arrangements/{id}/draft`, `.../edit-lock` | bozza e blocco della partitura |
| `POST /scores/{scoreId}/score-versions/{versionId}/engravings` | avvia l'impaginazione; `GET` ne legge lo stato |
| `GET /scores/{scoreId}/score-versions/{versionId}/file` (esistente) | scaricamento del MusicXML; nuovo parametro `format=midi\|pdf` |

Errori nuovi, secondo lo [standard](application-errors-standard.md): `scoreWriting.locked` (409, con nome e ora di chi scrive), `scoreWriting.draftOutdated` (409), `scoreWriting.instrumentNotConfigured` (422, per l'estrazione), `scoreWriting.engravingUnavailable` (503).

## Autorizzazioni e isolamento tenant

- Scrittura, bozze, blocchi, estrazione e impaginazione sono riservati a super amministratore, amministratore e archivista, sui percorsi di gestione; utenti ed esterni continuano a vedere solo le versioni, con le regole di visibilità dell'ascolto.
- Bozze e blocchi stanno nello schema del tenant; il blocco riporta il nome di chi scrive solo agli utenti dello stesso tenant.
- Le bozze di un utente cancellato restano finché il bersaglio esiste, perché il contenuto è musica dell'archivio e non un dato personale; il nome dell'autore nel blocco scompare alla scadenza.

## Osservabilità e sicurezza

- Metriche: `taurus.score.writing.saves{origin}`, `taurus.score.writing.drafts{outcome}`, `taurus.score.writing.locks{outcome}` (`acquired`, `takenOver`, `expired`), `taurus.score.engravings{outcome}` e la durata dell'impaginazione.
- Log con identificativi di parte, partitura, versione e job; mai il contenuto MusicXML.
- Gli stessi limiti di sicurezza dei file MusicXML caricati (entità e DTD disattivate, limiti di dimensione); bozze al massimo 10 MB compressi e un salvataggio ogni due secondi per utente.
- Il servizio musicale resta sulla rete interna con la chiave condivisa; l'impaginazione ha un tempo massimo come il riconoscimento.
- La tastiera MIDI si usa solo dopo il permesso del browser e senza messaggi di sistema (sysex); `midi=(self)` consente il MIDI solo all'origine dell'applicazione, non a contenuti di altre origini.

## Prestazioni

| Misura | Obiettivo |
| --- | --- |
| Tasto → nota disegnata, parte di 300 battute | meno di 100 ms su un computer recente |
| Tasto → nota disegnata, partitura di 30 parti e 150 battute | meno di 150 ms, con il disegno della sola pagina visibile |
| Apertura di una partitura di 30 parti | meno di 3 secondi |
| Salvataggio di una bozza | non blocca la scrittura |
| Impaginazione di una parte di due pagine | meno di 15 secondi |

Le misure seguono il metodo della [fase 4 dell'ascolto](track-score-playback-spec.md#copertura-della-fase-4), anche con il processore rallentato quattro volte.

## Strategia di test

- **Unità (frontend)**: ogni operazione di scrittura su documenti minimi, come le specifiche di `score-edits`: inserimento al cursore, battute sempre complete, divisione e legatura alla stanghetta, voci, copia e incolla, trasposizione con accidenti e armatura, partitura e cronologia per battute; inserimento da MIDI con un dispositivo simulato (accordi, grafia, vista trasposta e suoni reali).
- **Validità**: ogni documento prodotto dai test viene validato contro lo schema XSD di MusicXML 4.0; un campione si apre in MuseScore durante la verifica manuale.
- **Andata e ritorno**: scrivere, salvare, riaprire e scaricare produce lo stesso MusicXML, senza attributi di lavoro.
- **Backend**: bozze con concorrenza ottimistica, blocchi con scadenza e presa di controllo, estrazione con trasposizione e strumenti non configurati, job di impaginazione con il servizio simulato, autorizzazioni e isolamento tenant.
- **Servizio musicale**: impaginazione su MusicXML noti, con conteggio delle pagine e confronto delle immagini entro una tolleranza.
- **Prestazioni**: misure ripetibili del disegno su parti e partiture grandi.
- **Manuale**: prove con archivisti su parti reali, tempo per scrivere una parte di una pagina, uso su tablet, tastiera MIDI reale su Chrome, Edge e Firefox.

## Criteri di accettazione

- [ ] Con `SCORE_WRITING` disattivata nessuna azione di scrittura è visibile e le API rispondono `403`.
- [ ] Un archivista scrive da zero una parte di una pagina con la sola tastiera; le battute restano complete e la parte si ascolta.
- [ ] Con una tastiera MIDI collegata, in Chrome ed Edge, le note suonate si scrivono al cursore e quelle premute insieme diventano accordi; dove Web MIDI non esiste il comando non compare.
- [ ] **Salva e approva** approva la versione senza l'intervento di una seconda persona e chiude le segnalazioni aperte della parte.
- [ ] Una parte di clarinetto in Sib diventa la parte di sax contralto in Mib con l'intervallo e l'armatura corretti, e le note fuori estensione sono segnalate.
- [ ] Chiudendo il browser durante la scrittura e riaprendo l'editor si riprende la bozza.
- [ ] Un secondo utente vede chi sta scrivendo e apre in sola lettura; la presa di controllo conserva la bozza del primo.
- [ ] Una partitura di 30 parti si scrive senza ritardi percepibili e si estrae in parti trasposte per strumento.
- [ ] Una parte scritta si impagina in A4 e nel formato marcia, compare in anteprima e si stampa come le parti dei PDF.
- [ ] Il MusicXML scaricato si apre in MuseScore senza avvisi.
- [ ] Nessun dato di un tenant è accessibile da un altro tenant.

## Piano di implementazione

1. **Scrittura di una parte**: modalità di inserimento con cursore e voci, battute sempre complete, selezione di intervalli, copia, incolla e trasposizione, nuova notazione (legature di portamento, forcelle, lettere di prova, testo, corona, respiro, abbellimenti), inserimento da tastiera MIDI con `midi=(self)`, bozze e blocco, nuova parte da zero, approvazione da parte di chi scrive, funzionalità `SCORE_WRITING`.
2. **Parti ricavate**: derivazione per un altro strumento, estensione degli strumenti con proposta dal catalogo e avvisi, scaricamento MIDI.
3. **Impaginazione e stampa**: servizio di impaginazione, job e coda, pagine e PDF della parte, formati A4 e marcia, arrangiatore della traccia, indicazione delle pagine non aggiornate.
4. **Partitura**: documento a più parti, disegno per pagina in un Web Worker, suoni reali e trasposizione, partitura dalle parti esistenti, estrazione e divergenze, cronologia per battute, notifica ai soci delle parti approvate.
5. **Qualificazione**: misure su partiture grandi, prove con archivisti e su tablet, runbook.

## Compatibilità e adozione

- La funzionalità è disattivata per default e richiede l'ascolto delle parti; senza servizio musicale si scrive e si ascolta, ma non si impagina.
- Versioni, ascolto, correzione, segnalazioni e scaricamento esistenti non cambiano: le versioni scritte sono versioni come le altre.
- Le nuove origini di versione sono valori aggiuntivi: il frontend precedente mostra un'origine sconosciuta come «Altro» finché non viene aggiornato.
- Rollback: disattivare la funzionalità; partiture, bozze e versioni restano per una riattivazione.

## Decisioni della revisione

Le questioni aperte della prima bozza, decise il 18/10/2026, con la numerazione originale:

1. **Chi può scrivere**: super amministratore, amministratore e archivista, come per correzione e approvazione. Nessun nuovo ruolo né delega per traccia.
2. **Dove si impagina**: nel servizio musicale, per pagine identiche a ogni generazione e indipendenti dai caratteri del browser. Il modulo mantiene il nome `taurus-omr`, così immagine, compose e workflow non cambiano.
3. **Sostituire o affiancare le pagine**: Taurus chiede ogni volta se sostituire le pagine della parte o aggiungere quelle impaginate in una parte nuova; una parte scritta da zero, senza pagine, le riceve direttamente.
4. **Formato marcia**: 18 × 13 cm in orizzontale, oltre ad A4 verticale e orizzontale; altri formati si aggiungeranno su richiesta.
5. **Estensione degli strumenti**: due nuovi campi facoltativi dello strumento, nota scritta più grave e più acuta, proposti dal catalogo di riferimento. Non c'è un'estensione separata per gli allievi.
6. **Tastiera MIDI**: abilitata dalla prima fase (vedi [Tastiera MIDI](#tastiera-midi)).
7. **Approvazione**: chi scrive approva il proprio lavoro con **Salva e approva**, come nella correzione; non serve l'approvazione di una seconda persona e non c'è un'impostazione del tenant per richiederla.
8. **Unione delle bozze**: nessuna unione automatica; un solo autore alla volta e conferma esplicita in caso di conflitto.
9. **Partitura e parti modificate a mano**: l'estrazione segnala le parti corrette dopo l'ultima estrazione e lascia scegliere, parte per parte, se sostituirle.
10. **Crediti e diritti**: intestazione e metadati riportano compositore e arrangiatore; l'arrangiatore è un nuovo campo facoltativo della traccia, mostrato nel dettaglio, senza un filtro di ricerca dedicato. La responsabilità dei diritti resta del tenant, come per i caricamenti.
11. **Notifiche**: quando le parti estratte sono approvate, i soci che suonano quegli strumenti ricevono una notifica, con il sistema di notifiche e le preferenze esistenti.
12. **Formato interno**: MusicXML. MEI si rivaluterà solo se servisse notazione che MusicXML non rappresenta.

## Proposte

- **Organico della banda per tenant**: un modello con gli strumenti e l'ordine in partitura, usato per le nuove partiture e per ordinare le parti in stampa.
- **Confronto tra versioni**: una vista che evidenzia le battute diverse tra due versioni, riusando il confronto per battute già scritto per l'[accuratezza dei motori](runbook-operativo.md#46-accuratezza-dei-motori); utile per rivedere correzioni, estrazioni e approvazioni.
- **Parti di studio**: ascolto della propria parte con le altre attenuate e tempo rallentato, già possibile con il lettore, più la stampa con le battute numerate a ogni rigo.
- **Frammenti riutilizzabili**: introduzioni, marce d'ordinanza, segnali e rulli di tamburo salvati una volta e inseriti nelle parti.
- **Impaginazione per l'evento**: dalla preparazione di un evento, la stampa di tutte le parti di una scaletta già impaginate per strumento, nel formato marcia.
- **Raccolta dai riconoscimenti**: le battute che gli archivisti correggono più spesso indicano i difetti dei motori e possono diventare nuove parti del corpus di accuratezza.
