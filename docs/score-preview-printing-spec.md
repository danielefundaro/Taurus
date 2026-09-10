# Anteprima e stampa degli spartiti

## Stato del documento

ID catalogo: `score-preview-printing`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

## 1. Contesto e obiettivo

Taurus consente di raccogliere in una sola anteprima le pagine degli spartiti appartenenti a una traccia oppure alle tracce di un album. Prima della stampa l'utente può limitare il documento agli strumenti necessari, controllare le singole pagine e aprire una presentazione a schermo intero.

Questa specifica definisce il contratto funzionale, visuale e tecnico della route `/preview`. Il suo scopo è rendere prevedibili:

- l'ingresso da tracce e album;
- la selezione delle parti da includere;
- la consultazione e la presentazione delle pagine;
- la stampa tramite il browser;
- il comportamento della spalla filtri a ogni larghezza;
- gli stati vuoti, l'accessibilità e le verifiche necessarie.

La specifica completa il [Workspace per la gestione delle parti di una traccia](track-parts-workspace-spec.md), che lascia esplicitamente fuori ambito il visualizzatore e il flusso di stampa esistenti. Usa inoltre i principi del [Taurus Layout Standard](taurus-layout-standard.md) e il contratto di accesso ai file della [Gestione centralizzata dei media](media-asset-spec.md).

### Terminologia

- **Parte**: raggruppamento logico di pagine associato a zero o più strumenti.
- **Pagina**: singolo media visualizzabile e stampabile.
- **Spalla filtri**: pannello laterale «Parti da includere».
- **Anteprima**: pagina applicativa usata per filtrare, consultare e avviare la stampa.
- **Presentazione**: visualizzazione delle pagine a schermo intero.

## 2. Ambito

### Compreso

- Apertura dell'anteprima da una traccia, da un album completo o da una selezione di tracce di un album.
- Aggregazione ordinata delle parti e delle relative pagine.
- Filtro per strumento, inclusa la voce «Senza strumento».
- Selezione e deselezione globale delle parti.
- Navigazione tra pagine, adattamento e zoom visuale.
- Presentazione a schermo intero.
- Stampa delle sole pagine incluse dal filtro.
- Comportamento responsive della spalla filtri.
- Stati senza contenuto, caricamento ed errore dei media.
- Accessibilità tramite tastiera e tecnologie assistive.

### Fuori ambito

- Modifica di strumenti, parti o immagini dalla pagina di anteprima.
- Riordino, unione, divisione o spostamento delle pagine.
- Elaborazione, ritaglio o correzione dei PDF sorgente.
- Generazione server-side di un nuovo PDF, download ZIP o invio a stampanti proprietarie.
- Persistenza delle preferenze di filtro tra sessioni differenti.
- Annotazioni musicali o grafiche sulle pagine.

## 3. Utenti, autorizzazioni e isolamento tenant

La route è disponibile a super amministratori, amministratori, archivisti, utenti interni e utenti esterni autenticati. L'anteprima non amplia mai la visibilità ricevuta dalla pagina sorgente:

- una traccia o un album può fornire soltanto parti già autorizzate per il ruolo corrente;
- ogni richiesta allo stream di un media applica nuovamente autenticazione, autorizzazione e isolamento tenant lato backend;
- conoscere l'identificativo o l'URL di un media non consente di aggirare i controlli;
- l'anteprima non salva né trasmette una nuova copia dei file;
- titoli, nomi degli strumenti e identificativi dei media non vengono scritti in log o telemetria applicativa.

L'apertura diretta di `/preview` non ricostruisce dati assenti dalla sessione e non effettua ricerche implicite. Se il servizio di stampa non contiene pagine, l'utente viene riportato alla superficie precedente o a una destinazione sicura, senza mostrare una pagina vuota inutilizzabile.

## 4. Origini e preparazione dei dati

### Traccia

L'azione di anteprima riceve la traccia già caricata, conserva l'ordine delle parti e, dentro ogni parte, l'ordine dei media. Il titolo visualizzato deriva dal nome della traccia; in sua assenza usa «Spartiti selezionati». L'etichetta di contesto è «Traccia».

### Album

L'azione di anteprima usa le tracce selezionate quando la selezione non è vuota; altrimenti usa tutte le tracce dell'album. Le tracce vengono risolte prima della navigazione, mantenendo l'ordine dell'album o della selezione. Il titolo deriva dal nome dell'album e l'etichetta di contesto è «Album».

Un errore durante il caricamento di una o più tracce impedisce l'apertura di un'anteprima parziale silenziosa. La pagina sorgente conserva la selezione e mostra un errore recuperabile con azione «Riprova».

### Durata dello stato

I dati preparati vivono nella sessione applicativa necessaria alla navigazione verso `/preview`. Uscendo dall'anteprima, lo stato viene liberato. Un refresh o un collegamento diretto non è tenuto a ricostruire la selezione: questa limitazione deve produrre un ritorno sicuro, non un errore JavaScript.

Non sono richiesti nuovi modelli persistenti, endpoint o migrazioni Liquibase.

## 5. Composizione dell'interfaccia

La pagina usa quattro zone, nell'ordine seguente:

1. intestazione con ritorno, contesto, titolo, metadati e azioni;
2. spalla «Parti da includere»;
3. barra strumenti della pagina corrente;
4. canvas del documento oppure stato vuoto.

### Intestazione

L'intestazione contiene:

- pulsante «Torna indietro»;
- etichetta «Traccia» o «Album»;
- titolo «Anteprima spartiti»;
- nome della sorgente e numero di pagine incluse;
- pulsante «Filtri»;
- azione secondaria «Presentazione»;
- azione primaria «Stampa n pagine».

Il conteggio e l'etichetta singolare/plurale si aggiornano immediatamente dopo ogni variazione dei filtri. «Presentazione» e «Stampa» sono disabilitati quando non è inclusa alcuna pagina.

### Spalla filtri

La spalla mostra:

- titolo «Parti da includere»;
- conteggio degli strumenti selezionati sul totale;
- pulsante di chiusura;
- selezione globale «Seleziona tutto»;
- una riga per strumento con nome e numero di pagine;
- voce «Senza strumento» per le parti prive di associazioni;
- nota persistente che chiarisce l'aggiornamento automatico e l'effetto sulla stampa.

Ogni strumento compare una sola volta. Il numero mostrato sulla sua riga è la somma delle pagine delle parti associate. Se una parte è associata a più strumenti selezionati, le sue pagine vengono incluse una sola volta.

All'apertura iniziale tutti gli strumenti sono selezionati. «Seleziona tutto» è attivo soltanto quando lo sono tutte le righe; la selezione di una singola riga aggiorna immediatamente conteggi, pagine e stato globale.

### Apertura e chiusura dei filtri

- Da oltre 960 px la spalla è aperta al primo ingresso e occupa una colonna del workspace.
- Fino a 960 px la spalla è chiusa al primo ingresso e si apre sopra il contenuto con una maschera.
- «Filtri» è sempre raggiungibile nell'intestazione e apre la spalla.
- Il pulsante con la X, `Escape` e la maschera mobile chiudono la spalla.
- Chiudendo la spalla, il focus torna a «Filtri».
- Aprendo la spalla da «Filtri» o dallo stato vuoto, il focus passa al suo primo controllo utile.
- «Apri filtri» nello stato vuoto è visibile soltanto quando la spalla è chiusa.
- Una scelta esplicita di apertura o chiusura resta valida durante i successivi cambi di larghezza nella stessa visita.

La chiusura su desktop restituisce al canvas tutta la larghezza disponibile. Su mobile la maschera impedisce l'interazione con il contenuto sottostante finché la spalla è aperta.

## 6. Filtro e stati del contenuto

Il filtro opera sulle parti: una parte è inclusa quando non ha strumenti e la voce «Senza strumento» è selezionata, oppure quando possiede almeno uno strumento selezionato. Le pagine risultanti mantengono l'ordine della sorgente.

Dopo ogni filtro:

- la pagina corrente resta invariata se ancora valida;
- se supera il nuovo totale viene limitata all'ultima pagina;
- se non restano pagine, il riferimento interno torna a 1 senza tentare di caricare un media inesistente.

### Stati visuali

| Stato | Presentazione | Azione |
| --- | --- | --- |
| Contenuto disponibile | Pagina corrente nel canvas, navigatore e comandi attivi. | Consulta, presenta o stampa. |
| Nessuna parte selezionata | Icona, titolo, causa esplicita e testo di supporto. | «Apri filtri» quando la spalla è chiusa. |
| Media in caricamento | Segnaposto con proporzione stabile, senza salto di layout. | Nessuna. |
| Media non disponibile | Segnaposto con numero pagina e messaggio recuperabile. | «Riprova». |
| Sorgente senza pagine | Nessuna navigazione verso una preview inutilizzabile. | Ritorno sicuro con messaggio sulla pagina sorgente. |
| Caricamento album fallito | Nessuna anteprima parziale non dichiarata. | «Riprova» dalla pagina sorgente. |

## 7. Navigazione, zoom e presentazione

### Navigazione

Quando esiste almeno una pagina, la barra mostra «Pagina x di n», un campo numerico e i pulsanti precedente e successivo. I pulsanti sono disabilitati ai rispettivi estremi. Un valore digitato viene limitato all'intervallo da 1 a n.

Il cambio pagina aggiorna il testo alternativo e porta la nuova pagina nella porzione visibile del canvas. Il navigatore resta distinto dai controlli di zoom.

### Zoom

- Valore iniziale: 100 per cento.
- Passo: 10 punti percentuali.
- Minimo: 60 per cento.
- Massimo: 140 per cento.
- «Adatta» ripristina il 100 per cento.

Lo zoom modifica soltanto il canvas e non la dimensione stampata. Il valore corrente è sempre visibile; i comandi non applicabili sono disabilitati.

### Presentazione

«Presentazione» apre una galleria a schermo intero delle sole pagine incluse. La galleria parte dalla pagina corrente dell'anteprima, consente navigazione avanti e indietro e si chiude con pulsante esplicito o `Escape`. Chiudendola, la pagina corrente resta sincronizzata con l'ultima pagina mostrata.

## 8. Stampa

«Stampa n pagine» invoca il dialogo di stampa del browser. Il documento stampato contiene esclusivamente le pagine risultanti dal filtro, nello stesso ordine dell'anteprima.

Regole di composizione:

- una pagina dello spartito per ogni pagina di stampa;
- larghezza pari all'area stampabile e altezza proporzionale;
- interruzione pagina dopo ogni immagine tranne l'ultima;
- esclusione di intestazione, navigazione, filtri, maschere e componenti applicativi;
- nessuna dipendenza dallo zoom del canvas;
- sfondo neutro e nessuna ombra decorativa;
- nessuna pagina vuota iniziale o finale introdotta dal layout.

Taurus non forza formato carta, margini, orientamento o scala del driver. Tali valori restano sotto il controllo del browser e del sistema operativo.

## 9. Responsive

| Viewport | Comportamento |
| --- | --- |
| Oltre 960 px | Workspace a due colonne; spalla aperta inizialmente e richiudibile; canvas elastico. |
| Da 641 a 960 px | Workspace a colonna singola; spalla overlay chiusa inizialmente; maschera alla sua apertura. |
| Fino a 640 px | Come sopra; testi secondari ridotti, azioni compatte e pulsanti essenziali ancora riconoscibili. |

Il ridimensionamento non deve troncare il canvas, rendere irraggiungibili i comandi o lasciare la maschera visibile dopo la chiusura. La spalla mobile non supera la viewport e conserva uno spazio laterale che renda percepibile il contesto sottostante.

## 10. Accessibilità

- La pagina usa un solo `h1`; lo stato vuoto usa un `h2`.
- La spalla è un landmark con nome programmatico «Parti da includere».
- «Filtri» e «Apri filtri» espongono `aria-controls` e lo stato `aria-expanded` coerente con la spalla realmente visibile.
- La spalla chiusa non contiene elementi raggiungibili dal focus.
- Pulsanti a sola icona possiedono nome accessibile, tooltip quando utile e area attiva minima di 44 × 44 px.
- Apertura, chiusura e passaggio del focus funzionano da tastiera; `Escape` chiude prima la presentazione o la spalla attiva.
- La maschera mobile non sostituisce il pulsante di chiusura ed è identificata come «Chiudi filtri».
- Il cambio del numero di pagine incluse è annunciato tramite una regione `aria-live="polite"` senza interrompere la lettura.
- Ogni immagine usa «Pagina x di n» come minimo; quando disponibile include anche sorgente o parte.
- Focus, stato attivo e disabilitazione non dipendono dal solo colore e rispettano il contrasto del tema.
- Le transizioni rispettano `prefers-reduced-motion`.

## 11. Decisioni tecniche

### Componenti frontend

| Elemento | Responsabilità |
| --- | --- |
| `PrinterService` | Prepara titolo, sorgente e parti; risolve le tracce dell'album; naviga verso `/preview`; libera lo stato all'uscita. |
| `PreviewComponent` | Deriva strumenti e media, governa filtri, pagina, zoom, presentazione e stampa. |
| `MediaService` e `SecurePipe` | Costruiscono e risolvono gli stream autenticati senza esporre credenziali. |
| PrimeNG Checkbox | Selezione binaria globale e per strumento. |
| PrimeNG Galleria | Presentazione a schermo intero. |
| CSS di stampa | Separa la superficie interattiva dalle pagine stampabili. |

Il componente usa `ChangeDetectionStrategy.OnPush`. Lo stato di apertura della spalla deve avere una fonte coerente anche al primo rendering responsive; la soluzione può usare un breakpoint osservabile oppure uno stato iniziale neutro, purché DOM, attributi ARIA e resa visuale non divergano.

### API e dati

La funzionalità riusa gli stream media esistenti e le letture delle tracce necessarie all'album. Non introduce endpoint dedicati. Il client non concatena byte, non genera file autorevoli e non memorizza URL con credenziali.

### Compatibilità

- Gli ingressi esistenti da elenco/dettaglio Tracce e Album restano invariati.
- L'ordine dei DTO esistenti resta autorevole.
- Le pagine prodotte dall'editor immagini sono normali media e appaiono senza fallback dedicati.
- Browser che non supportano le transizioni usano comunque apertura e chiusura immediate.
- La stampa dipende dalle API standard `window.print` e `@media print`.

## 12. Sicurezza, privacy e osservabilità

- Ogni stream resta protetto dal backend e non viene trasformato in URL pubblico.
- La route non accetta dal client autorizzazioni, tenant o percorsi filesystem.
- Le pagine non vengono inviate a servizi esterni per anteprima o stampa.
- La cache segue le regole dello stream media esistente; la preview non aggiunge persistenza offline.
- Eventuali metriche registrano soltanto sorgente generica, numero di pagine, apertura presentazione, avvio stampa ed esito tecnico di caricamento.
- La telemetria non registra nomi di tracce, album, strumenti, file o identificativi media.
- Gli errori distinguono caricamento della sorgente e caricamento di una pagina senza mostrare dettagli tecnici o URL interni.

## 13. Strategia di test

| Livello | Copertura minima |
| --- | --- |
| Unità | Aggregazione traccia/album, ordine, deduplicazione delle parti multi-strumento, «Senza strumento», selezione globale, limiti pagina e zoom, pulizia dello stato. |
| Componente | Apertura e chiusura da tutti i comandi, classi responsive, maschera, stato vuoto, attributi ARIA, focus, `Escape`, azioni disabilitate e sincronizzazione della presentazione. |
| Integrazione frontend | Stream autenticati, errore di una traccia album, errore media e ritorno sicuro dopo refresh diretto. |
| End to end | Ingressi da traccia e album, filtro, stampa intercettata, presentazione, tastiera, viewport desktop/tablet/mobile e ruoli consentiti. |
| Visuale | Tema chiaro/scuro, pagina verticale/orizzontale, nomi lunghi, 1 e molte pagine, spalla aperta/chiusa e anteprima senza selezioni. |
| Stampa | Ordine, numero di fogli, assenza di UI, nessun foglio vuoto e indipendenza dallo zoom. |

La verifica minima del modulo esegue:

```text
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
```

## 14. Criteri di accettazione

1. L'anteprima si apre da traccia, album e selezione di tracce mantenendo titolo, contesto e ordine.
2. Tutte le parti sono incluse al primo ingresso e il conteggio usa singolare e plurale corretti.
3. Il filtro per strumento aggiorna immediatamente anteprima, presentazione e stampa senza duplicare parti multi-strumento.
4. Le parti senza strumenti sono controllabili tramite «Senza strumento».
5. «Seleziona tutto» riflette sempre lo stato reale delle righe.
6. Da oltre 960 px la spalla parte aperta; fino a 960 px parte chiusa e si comporta come overlay.
7. «Filtri», X, maschera, «Apri filtri» ed `Escape` producono sempre l'esito dichiarato.
8. Chiudere e riaprire la spalla aggiorna larghezza del canvas, focus e attributi ARIA senza lasciare controlli invisibili raggiungibili.
9. «Apri filtri» appare soltanto quando la spalla è chiusa e apre effettivamente il pannello.
10. Quando il filtro produce zero pagine, lo stato vuoto spiega la causa e presentazione/stampa sono disabilitate.
11. Navigatore e campo numerico non possono portare fuori dall'intervallo valido.
12. Lo zoom resta tra 60 e 140 per cento, «Adatta» torna a 100 e la stampa non ne è influenzata.
13. La presentazione contiene le sole pagine incluse, parte dalla pagina corrente e mantiene la sincronizzazione alla chiusura.
14. La stampa contiene una pagina per media, nell'ordine corrente, senza UI né fogli vuoti aggiuntivi.
15. Un media in caricamento o errore mantiene stabile il layout e offre un esito comprensibile.
16. Un errore durante la preparazione di un album non apre silenziosamente un documento parziale.
17. Un refresh o accesso diretto senza stato torna in sicurezza e non genera errori non gestiti.
18. Tutti i ruoli ammessi vedono soltanto media già autorizzati e gli stream rispettano l'isolamento tenant.
19. Il flusso completo è utilizzabile con tastiera e i pulsanti a sola icona hanno area attiva minima di 44 × 44 px.
20. Test di componente, integrazione e viewport coprono le regressioni della spalla filtri.

## 15. Adozione e decisioni da confermare

### Piano di adozione

1. Formalizzare e approvare comportamento responsive, accessibilità e sincronizzazione della presentazione.
2. Allineare la pagina al contratto approvato senza cambiare gli ingressi esistenti.
3. Aggiungere i test automatici mancanti e le fixture con parti multi-strumento e senza strumento.
4. Verificare stampa reale da Chrome ed Edge con almeno un documento verticale e uno orizzontale.
5. Eseguire collaudo da tastiera, tema scuro e viewport fino a 320 px.

### Decisioni da confermare

| Decisione | Proposta |
| --- | --- |
| Breakpoint della spalla | Conservare 960 px per compatibilità con la pagina attuale. |
| Stato iniziale desktop/mobile | Aperta su desktop, chiusa su tablet e mobile. |
| Persistenza filtri | Limitata alla singola visita; nessun salvataggio utente. |
| Presentazione | Apertura sulla pagina corrente e sincronizzazione bidirezionale. |
| Refresh diretto | Ritorno sicuro alla pagina precedente o al catalogo, senza ricostruzione implicita. |
| Stampa | Affidata al browser; nessuna generazione PDF server-side in questa funzionalità. |

