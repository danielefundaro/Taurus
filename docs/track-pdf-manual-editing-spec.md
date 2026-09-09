# Editor manuale delle pagine PDF

## Stato del documento

ID catalogo: `track-pdf-manual-editing`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

Implementazione completata e verificata il **9 settembre 2026**.

Questa specifica estende il flusso descritto in [Elaborazione asincrona dei PDF delle tracce](track-pdf-processing-spec.md) e riutilizza la pipeline definita in [Modifica e analisi delle immagini delle parti](track-page-image-editing-spec.md).

## Obiettivo

Permettere all'utente di preparare le pagine di un PDF prima dell'upload usando le stesse regolazioni manuali disponibili per le immagini delle singole parti, mantenendo le funzioni specifiche del PDF:

- esclusione di una o più pagine;
- inclusione o esclusione massiva;
- applicazione della stessa ricetta a tutte le pagine;
- elaborazione differita nel worker di upload;
- compatibilità con le precedenti annotazioni contenenti soltanto crop.

Il dialogo non salva direttamente immagini o modifiche. Restituisce una ricetta per pagina, allegata al successivo upload del PDF e applicata dal worker durante la conversione.

## Decisioni funzionali

| Tema               | Decisione                                                                                                                  |
| ------------------ | -------------------------------------------------------------------------------------------------------------------------- |
| Regolazioni        | Rotazione, raddrizzamento, crop multiplo, luminosità, contrasto, contrasto automatico, scala di grigi e soglia bianco/nero |
| Raddrizzamento     | Slider da 0° a 360° con passo di 0,1°, tooltip numerico e normalizzazione delle annotazioni negative precedenti             |
| Scala di grigi     | Resta un'opzione esplicita; le pagine PDF vengono renderizzate inizialmente a colori                                       |
| Stato              | Ogni pagina conserva una ricetta indipendente                                                                              |
| Applica a tutte    | Copia l'intera ricetta della pagina corrente su tutte le pagine                                                            |
| Pagine escluse     | Conservano la ricetta e possono essere reincluse senza perdere le regolazioni                                              |
| Tutte escluse      | È consentita la selezione, ma «Applica modifiche» viene disabilitato finché almeno una pagina non viene reinclusa          |
| Crop multiplo      | Le zone vengono unite verticalmente in una sola immagine, preservando una pagina risultante per ogni pagina PDF inclusa    |
| Crop manuale       | Disegno e aggiornamento immediati, stile coerente con l'editor immagini e pointer capture oltre i bordi dell'anteprima      |
| Analisi automatica | Non inclusa: l'editor PDF offre tutte le regolazioni manuali senza invocare l'analisi dei media persistiti                 |
| Persistenza        | Le annotazioni vengono salvate nel job di upload e applicate asincronicamente                                              |

## Esperienza utente

### Elenco pagine

La sidebar sinistra continua a mostrare tutte le pagine del documento. Per ogni pagina sono disponibili:

- selezione e navigazione;
- esclusione o reinclusione;
- apertura dell'editor manuale;
- indicatore del numero di zone di crop;
- evidenziazione delle pagine che possiedono una ricetta di trasformazione.

Il comando nell'intestazione della sidebar alterna «Escludi tutte le pagine» e «Includi tutte le pagine».

### Editor della pagina

L'editor usa un canvas con anteprima immediata e un pannello laterale. Sono disponibili:

1. rotazione a sinistra o a destra in passi di 90 gradi;
2. raddrizzamento tramite slider tra 0 e 360 gradi, con passo di 0,1 gradi e valore visibile sopra il cursore;
3. una o più zone di ritaglio normalizzate;
4. conversione opzionale in scala di grigi;
5. contrasto automatico;
6. luminosità tra -100 e +100;
7. contrasto tra -100 e +100;
8. soglia bianco/nero opzionale tra 0 e 255;
9. ripristino delle sole regolazioni della pagina corrente.

Le zone di crop possono essere disegnate, ridimensionate, selezionate, aggiornate ed eliminate. Il disegno segue la stessa interazione dell'editor immagini: la zona viene salvata automaticamente al termine del trascinamento, senza un passaggio di conferma, e la selezione attiva usa bordo verde e otto handle circolari. Le zone inattive sono mostrate in blu. Il puntatore viene acquisito durante il trascinamento, quindi la selezione continua anche fuori dai bordi dell'anteprima e termina soltanto al rilascio. Il pannello mostra l'elenco ordinato delle zone, consente di modificarne numericamente posizione e dimensioni percentuali e include due divisioni rapide: sinistra/destra e alto/basso. L'ordine visualizzato è anche l'ordine usato per comporre verticalmente il risultato. Una pagina può contenere al massimo otto zone; ogni zona deve avere larghezza e altezza superiori o uguali al 2 per cento dell'immagine.

### Applica a tutte

«Applica a tutte» copia su ogni pagina:

- orientamento;
- raddrizzamento;
- zone e ordine dei crop;
- scala di grigi;
- luminosità e contrasto;
- contrasto automatico;
- soglia bianco/nero.

Ogni pagina riceve una copia indipendente della ricetta. Modificare successivamente una pagina non altera le altre. L'insieme delle pagine escluse non viene modificato.

### Blocco con tutte le pagine escluse

Quando tutte le pagine sono escluse:

- la selezione rimane valida e può essere annullata con «Includi tutte» o reincludendo una singola pagina;
- viene mostrato il messaggio «Includi almeno una pagina»;
- il pulsante «Applica modifiche» è disabilitato;
- il metodo di conferma contiene anche una guardia applicativa e non restituisce annotazioni.

## Modello frontend

Le annotazioni PDF comprendono:

```typescript
interface PdfAnnotations {
  excludedPages: number[];
  cropRegions: PdfCropRegion[];
  pageTransforms: PdfPageTransform[];
}

interface PdfPageTransform {
  page: number;
  recipeVersion: 1;
  rotationQuarterTurns: number;
  deskewDegrees: number;
  crops: ImageCrop[];
  grayscale: boolean;
  brightness: number;
  contrast: number;
  autoContrast: boolean;
  threshold: number | null;
}
```

`cropRegions` è mantenuto come rappresentazione legacy. Quando è presente una ricetta in `pageTransforms`, questa è autorevole e il backend non applica nuovamente i crop legacy della stessa pagina.

Il dialogo riceve anche le annotazioni già presenti. Chiudere e riaprire il manipolatore non azzera quindi esclusioni, crop o regolazioni.

## Pipeline di elaborazione

Frontend e backend applicano le regolazioni nello stesso ordine:

```text
render pagina PDF a colori
    -> rotazione a 90 gradi
    -> raddrizzamento fine
    -> N crop
    -> contrasto automatico
    -> contrasto e luminosità
    -> scala di grigi o soglia
    -> unione verticale degli N risultati
    -> PNG della pagina
```

Se non esistono crop, l'intera pagina continua nella pipeline. Se una pagina è esclusa, la sua posizione nell'elenco intermedio viene conservata con un valore nullo; il raggruppamento delle parti può così continuare a riferirsi agli indici del PDF originale.

## Riuso del motore

`ImageTransformationService` espone una pipeline per immagini già decodificate. La stessa implementazione viene usata da:

- editor delle immagini persistite, con input PNG/JPEG e un risultato per ogni crop;
- conversione PDF, con input `BufferedImage` prodotto da PDFBox e unione verticale dei risultati.

Il riuso comprende validazione, rotazione, raddrizzamento, crop e regolazioni tonali. Il dialogo PDF mantiene separatamente la gestione multipagina, l'esclusione e l'applicazione massiva.

## Validazione e sicurezza

Il backend valida anche le ricette provenienti dalle annotazioni del PDF, che non attraversano la validazione HTTP dell'endpoint dell'editor immagini:

- `recipeVersion` uguale a 1;
- rotazione compresa tra -3 e +3 quarti di giro;
- raddrizzamento compreso tra 0 e 360 gradi; le annotazioni legacy comprese tra -5 e 0 gradi restano accettate e vengono normalizzate nell'angolo equivalente;
- massimo otto crop;
- coordinate normalizzate e dimensione minima del 2 per cento;
- luminosità e contrasto tra -100 e +100;
- soglia nulla oppure compresa tra 0 e 255.

Annotazioni legacy senza `pageTransforms` continuano a essere elaborate usando esclusioni e `cropRegions`.

## Componenti modificati

### Frontend

- `PdfManipulatorDialogComponent`: stato per pagina, canvas, regolazioni, applicazione massiva e blocco della conferma;
- `styles.scss`: tooltip numerici e indicatori degli estremi condivisi con l'editor immagini;
- `PdfAnnotations` e `PdfPageTransform`: nuovo contratto delle annotazioni;
- `AddFilesDialogComponent` e dettaglio traccia: passaggio delle annotazioni esistenti e upload del nuovo payload.

### Backend

- `PdfPageTransform`: deserializzazione e conversione nella ricetta condivisa;
- `PdfAnnotations`: supporto di `pageTransforms`;
- `ImageTransformationService`: trasformazione condivisa di immagini già renderizzate;
- `Converter`: rendering RGB, applicazione della ricetta e unione verticale dei crop;
- `Receiver`: iniezione e utilizzo del motore condiviso.

## Strategia di test

### Frontend

Sono coperti:

- disabilitazione logica della conferma con tutte le pagine escluse;
- copia di tutte le regolazioni su ogni pagina;
- indipendenza delle copie delle ricette;
- conservazione delle esclusioni durante «Applica a tutte»;
- ripristino di ricette esistenti;
- compatibilità con i crop legacy;
- creazione del canvas condizionale prima del primo rendering dell'anteprima;
- creazione rapida delle zone verticali e orizzontali;
- aggiornamento di una zona senza duplicarla né modificarne l'ordine;
- salvataggio automatico della zona al termine del trascinamento;
- mantenimento del trascinamento oltre i bordi tramite pointer capture;
- blocco dell'aggiunta oltre il limite di otto zone;
- posizionamento del tooltip numerico di luminosità, contrasto e soglia lungo lo slider;
- slider di raddrizzamento 0°–360° e normalizzazione delle annotazioni negative precedenti.

### Backend

Sono coperti:

- trasformazione di una pagina PDF già renderizzata;
- rotazione e scala di grigi tramite la pipeline condivisa;
- conversione reale di un PDF di prova;
- mantenimento della posizione nulla di una pagina esclusa;
- comportamento precedente del receiver dopo l'iniezione del motore.

## Evidenze di verifica

| Verifica                           | Esito       |
| ---------------------------------- | ----------- |
| Test frontend Karma/Jasmine        | 100 superati |
| Test backend mirati                | 9 superati  |
| Build Angular di produzione        | superata    |
| Compilazione Java                  | superata    |
| Formattazione e `git diff --check` | superati    |

La build Angular segnala un warning non bloccante: lo stylesheet del manipolatore PDF supera il budget `anyComponentStyle` di 719 byte.

## Criteri di accettazione

1. Ogni pagina PDF può avere una ricetta manuale indipendente.
2. Tutte le regolazioni previste dall'editor sono visibili nell'anteprima e applicate dal backend.
3. La conversione a scala di grigi avviene soltanto quando richiesta.
4. «Applica a tutte» copia l'intera ricetta senza modificare le esclusioni.
5. Escludere tutte le pagine disabilita «Applica modifiche» e non chiude il dialogo con annotazioni invalide.
6. Reincludere almeno una pagina riabilita la conferma.
7. Riaprire il dialogo conserva le annotazioni già definite.
8. Le vecchie annotazioni con soli crop continuano a funzionare.
9. Una pagina con crop multipli produce ancora una singola immagine composta verticalmente.
10. Lo slider di raddrizzamento copre l'intervallo 0°–360° con passo di 0,1°; 360° è equivalente a 0° e le annotazioni negative precedenti vengono normalizzate.
11. Una zona di ritaglio viene salvata al rilascio senza un pulsante di conferma, usa gli stessi colori e handle circolari dell'editor immagini e mantiene il trascinamento oltre i bordi.
12. Test frontend e backend coprono esclusioni, applicazione massiva, interazioni di crop, slider e pipeline condivisa.

## Fuori scope

- analisi automatica delle pagine PDF prima dell'upload;
- suggerimenti automatici `AUTO_CROP`, `DESKEW` o `AUTO_CONTRAST` nel dialogo PDF;
- produzione di più pagine risultanti da crop multipli del PDF;
- modifica del PDF sorgente;
- cronologia o undo dopo l'avvio dell'upload;
- modifica simultanea di ricette diverse su una selezione parziale di pagine.
