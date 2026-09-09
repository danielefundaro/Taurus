# Modifica e analisi delle immagini delle parti

## Stato del documento

ID catalogo: `track-page-image-editing`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

Implementazione completata e verificata il **9 settembre 2026**.

## Riepilogo dell'implementazione

| Ambito | Stato realizzato |
| --- | --- |
| Editing post-caricamento | Le immagini già associate alle parti possono essere analizzate, modificate e salvate senza ripetere il caricamento del PDF. |
| Accesso | L'editor è raggiungibile dalle pagine della sezione «Parti» e dalla selezione singola nel workspace. |
| Anteprima pagina | Il comando «Apri anteprima» usa il trigger nativo di `p-image`, risultando compatibile con componenti `OnPush`. |
| Orientamento | Rotazione a sinistra e destra con icone visibili; l'icona destra è specchiata per rappresentare correttamente il verso. |
| Miglioramento | Scala di grigi, contrasto automatico, luminosità, contrasto e soglia bianco e nero; gli slider mostrano il valore numerico sopra il cursore. |
| Multi-crop | Una pagina può contenere fino a otto zone ordinate e produrre da una a otto nuove immagini. |
| Preset A4/A5 | Sono disponibili le divisioni «Sinistra / destra» e «Alto / basso» per creare due metà ordinate. |
| Crop manuale | Le zone possono essere disegnate sul canvas, selezionate dall'immagine o dall'elenco, eliminate e corrette tramite campi percentuali. |
| Ridimensionamento visuale | La zona attiva mostra otto maniglie: quattro angolari e quattro laterali. I bordi possono essere trascinati con cursori direzionali, aggiornamento in tempo reale, limiti dell'immagine e lato minimo del 2%. |
| Layout editor | La spalla strumenti destra usa almeno `28rem`, può arrivare al 32% della viewport e contiene campi fluidi senza scroll orizzontale. |
| Persistenza | Ogni risultato è un nuovo PNG `media_asset`; la pagina sorgente è sostituita dai risultati nello stesso punto e nel loro ordine. |
| Concorrenza e retry | Versione ottimistica della traccia e `Idempotency-Key` UUID v4 con ricevuta persistente e replay della lista dei risultati. |
| Relazioni ordinate | La ricostruzione di `sheet_music_media` avviene in due fasi, con flush dopo la disattivazione dei vecchi collegamenti, evitando collisioni temporanee sugli indici unici durante un multi-crop. |
| Pulizia storage | Il media sorgente viene eliminato soltanto se non è più referenziato; i file creati in operazioni fallite sono compensati dal meccanismo di cleanup. |

## Obiettivo

Consentire a super amministratori, amministratori e archivisti di correggere e migliorare una pagina immagine già associata a una parte di una traccia, senza dover ricaricare e rielaborare il PDF sorgente.

La funzione vive nel workspace «Parti»: l'utente apre una pagina, riceve suggerimenti tecnici sull'immagine, applica trasformazioni con anteprima e salva il risultato sulla sola occorrenza selezionata. Il salvataggio produce un nuovo `media_asset` e sostituisce in modo atomico il riferimento della parte; il file sorgente non viene mai sovrascritto in-place.

La specifica estende [Gestione delle parti di una traccia](track-parts-workspace-spec.md) e rispetta le regole di storage definite in [Gestione centralizzata dei media](media-asset-spec.md). Non modifica il flusso di [Elaborazione asincrona dei PDF delle tracce](track-pdf-processing-spec.md).

## Contesto attuale

Le pagine prodotte dal caricamento PDF sono immagini PNG a 300 DPI registrate in `media_asset` e associate a `sheet_music` tramite `sheet_music_media`. Nel frontend `MediaThumbnailComponent` carica il contenuto tramite `GET /api/media/{id}/stream`; il workspace consente di riordinare, spostare o rimuovere i riferimenti, ma non modifica il contenuto binario.

Il caricamento iniziale offre già ritaglio ed esclusione di pagine nel manipolatore PDF. Quelle annotazioni sono applicate una sola volta dal worker. Dopo la creazione delle parti non esiste un collegamento operativo tra una pagina e il ritaglio del PDF sorgente: l'editing post-caricamento deve quindi lavorare sul `media_asset` immagine corrente.

Una stessa immagine può essere referenziata da più parti, per esempio dopo una duplicazione. Sovrascrivere il file corrente cambierebbe tutte le occorrenze e invaliderebbe SHA-256, dimensione e semantica immutabile del catalogo media. Per questo l'editing usa sempre una derivazione copy-on-write.

## Decisioni principali

| Tema | Decisione |
| --- | --- |
| Punto di accesso | Azione «Modifica immagine» sulla card pagina e nella barra contestuale quando è selezionata una sola pagina |
| Persistenza | Salvataggio immediato e separato da «Salva traccia» |
| Modello binario | Nuovo `media_asset` per ogni salvataggio; nessuna modifica in-place |
| Ambito della sostituzione | Una relazione sorgente può essere sostituita da una fino a otto immagini, ordinate secondo le zone di crop |
| Anteprima | Rendering locale nel browser; il backend ricalcola autorevolmente il risultato al salvataggio |
| Analisi | Tecnica e deterministica: bordi vuoti, inclinazione, luminosità e contrasto; nessuna analisi del contenuto musicale |
| Concorrenza | Versione attesa della traccia e verifica della relazione corrente |
| Retry | `Idempotency-Key` UUID v4 con ricevuta persistente a durata limitata |
| Formato risultante | PNG lossless nella categoria storage `scores` |
| Autorizzazioni | `SUPER_ADMIN`, `ADMIN`, `ARCHIVIST`; utenti interni ed esterni restano in sola lettura |

## Ambito funzionale

### Strumenti disponibili

La prima versione comprende strumenti adatti a scansioni di spartiti e applicati in un ordine fisso, indipendente dall'ordine dei clic:

1. rotazione a passi di 90 gradi;
2. raddrizzamento fine entro l'intervallo da -5 a +5 gradi;
3. una o più zone di ritaglio rettangolari, manuali, suggerite dall'analisi o create con i preset metà sinistra/destra e alta/bassa;
4. conversione in scala di grigi;
5. regolazione manuale di luminosità e contrasto;
6. contrasto automatico;
7. soglia bianco e nero opzionale, regolabile e reversibile prima del salvataggio.

Il dialogo dispone di «Ripristina tutto». Le trasformazioni restano una bozza locale fino a «Salva immagine».

### Analisi tecnica

L'analisi non usa modelli generativi e non interpreta note, pentagrammi, testi o strumenti. Restituisce esclusivamente misure utili alla correzione visuale:

- dimensioni e rapporto dell'immagine;
- rettangolo normalizzato del contenuto, calcolato stimando i bordi uniformi;
- percentuale di bordo potenzialmente eliminabile;
- inclinazione stimata, limitata all'intervallo supportato;
- punteggio normalizzato di luminosità e contrasto;
- suggerimenti `AUTO_CROP`, `DESKEW`, `AUTO_CONTRAST` e avvisi di qualità.

I suggerimenti non modificano automaticamente la pagina. L'utente li applica singolarmente o con «Applica suggerimenti» e può sempre confrontare prima e dopo.

### Coordinate e ordine delle trasformazioni

Ogni zona di crop usa coordinate normalizzate nell'intervallo `[0, 1]`, riferite all'immagine dopo la rotazione a 90 gradi e il raddrizzamento fine. Il backend accetta al massimo otto zone, ne conserva l'ordine e rifiuta rettangoli fuori limite o inferiori al 2 per cento per lato. In assenza di zone le altre trasformazioni producono una sola immagine completa.

La pipeline autorevole è:

```text
decode -> orientamento -> raddrizzamento -> N crop
       -> contrasto automatico -> contrasto/luminosità
       -> scala di grigi o soglia -> N PNG
```

L'ordine è parte del contratto e deve essere condiviso da preview frontend e renderer backend. Una versione numerica della ricetta (`recipeVersion`) consente di evolvere l'algoritmo senza reinterpretare richieste precedenti.

## Esperienza utente

### Accesso dal workspace Parti

Ogni card modificabile espone un pulsante con icona e nome accessibile «Modifica immagine pagina N». Quando una sola pagina è selezionata, la stessa azione compare nella barra contestuale. L'azione non è mostrata ai ruoli di sola lettura.

Una pagina può essere modificata soltanto quando:

- la traccia e la parte hanno un identificativo persistito;
- la pagina è ancora associata alla parte;
- non esistono modifiche non salvate alla struttura della traccia;
- non è già in corso un salvataggio della traccia o dell'immagine.

Se il workspace contiene una bozza, il comando resta disabilitato con il messaggio «Salva prima le modifiche alla traccia». Questa regola evita che un endpoint immediato aggiorni una relazione che nel browser è già stata spostata, duplicata o rimossa. Una parte appena creata o duplicata diventa modificabile dopo il salvataggio della traccia.

### Dialogo editor

Il dialogo occupa fino al 95 per cento della viewport e usa tre aree:

- barra superiore con nome parte, pagina e confronto «Originale / Modificata»;
- canvas centrale adattivo, con zone numerate, selezionabili e disegnabili tramite trascinamento;
- spalla destra degli strumenti, larga almeno `28rem` e fino al 32 per cento della viewport, con analisi, controlli e azioni finali.

Su viewport inferiori a 768 pixel il layout diventa verticale e il pannello strumenti segue il canvas. Campi e `InputNumber` sono fluidi per non introdurre scorrimento orizzontale. I componenti PrimeNG usati sono `DynamicDialog`, `Button`, `Slider`, `InputNumber`, `SelectButton`, `Message`, `Skeleton`, `Tag`, `Checkbox` e `Tooltip`, con colori derivati dai token semantici del tema.

#### Interazione con le zone di ritaglio

- «Disegna zona» abilita il puntatore a croce e crea una zona trascinando sull'anteprima modificata.
- I preset sostituiscono le zone correnti con due metà ordinate, verticali oppure orizzontali.
- La zona attiva è evidenziata in verde; le altre zone sono evidenziate in blu e tutte mostrano il numero d'ordine.
- Un clic dentro una zona la rende attiva. La stessa selezione è disponibile nell'elenco laterale.
- La zona attiva espone maniglie su angoli e lati con una hit area di 14 pixel CSS.
- Il trascinamento di una maniglia ridimensiona soltanto i lati coinvolti, resta nei limiti `[0, 1]` e non scende sotto il 2 per cento per dimensione.
- Il rendering durante il trascinamento è accodato con `requestAnimationFrame` per non eseguire più ridisegni nello stesso frame.
- Un `pointercancel` interrompe il gesto senza creare una nuova zona involontaria.
- Posizione e dimensione restano modificabili anche con i quattro campi percentuali.

Stati espliciti del dialogo:

| Stato | Comportamento |
| --- | --- |
| Caricamento | Skeleton del canvas; comandi disabilitati |
| Analisi | Immagine visibile; suggerimenti con indicatore non percentuale |
| Pronto | Controlli attivi e «Salva immagine» disabilitato finché la ricetta è vuota |
| Modificato | Confronto prima/dopo e riepilogo trasformazioni |
| Salvataggio | Un solo invio; chiusura e navigazione bloccate |
| Errore recuperabile | Ricetta mantenuta, messaggio e «Riprova» |
| Conflitto | Ricetta mantenuta; richiesta di ricaricare la pagina corrente prima di riprovare |

La chiusura con ricetta non vuota richiede conferma. Dopo il salvataggio il dialogo si chiude e la pagina sorgente viene sostituita da tutte le immagini generate, nello stesso punto e nell'ordine visualizzato; le miniature vengono ricaricate e un toast riporta il numero di immagini create. Il comando generale «Salva traccia» non si attiva per questa operazione già persistita.

### Accessibilità

- Tutti gli strumenti sono raggiungibili da tastiera e hanno nome, valore e unità annunciabili.
- Ogni zona dispone anche di campi numerici per posizione, larghezza e altezza; il trascinamento non è l'unico metodo.
- Rotazione, reset, suggerimenti applicati, errori e salvataggio sono annunciati in una regione `aria-live="polite"`.
- Originale e modificata hanno testo alternativo equivalente e il confronto non dipende dal colore.
- Focus e contrasto seguono i token PrimeNG; i pulsanti a sola icona hanno area attiva minima di 44 per 44 pixel.

## Contratto frontend

Vengono introdotti:

- `ImageEditorDialogComponent`, responsabile di canvas, ricetta e stato del dialogo;
- rendering locale nel canvas dell'editor per applicare la pipeline a una bitmap ridotta e disegnare zone e maniglie;
- modelli `TrackPageAnalysis`, `TrackPageEditResult`, `ImageCrop` e `ImageTransformRecipe`;
- metodi dedicati in `TracksService` per analisi e salvataggio;
- evento `mediaEdited` da `ScoreWorkspaceComponent` verso `DetailComponent` e aggiornamento immutabile della pagina nella bozza corrente.

Il frontend conserva la ricetta, non i PNG definitivi. La preview può usare una bitmap ridotta per fluidità, mentre il backend lavora sempre sui byte originali. Il risultato restituito sostituisce il `ChildrenEntities.index` della pagina sorgente con una lista ordinata e aggiorna `track.version`; ordine e nomi visuali sono quelli restituiti dal server.

Le URL `blob:` vengono revocate alla sostituzione, alla chiusura e alla distruzione del componente. Il nuovo `mediaId` provoca il reset naturale di `MediaThumbnailComponent`, evitando cache stale senza aggiungere parametri fittizi all'URL.

## Contratto HTTP

Le rotte sono collocate sotto `/api/tracks/**`, così ereditano la policy amministrativa del catalogo. Il client non invia tenant, path o storage key.

### Analisi

```http
GET /api/tracks/{trackId}/scores/{scoreId}/media/{mediaId}/analysis
```

Risposta `200 OK` indicativa:

```json
{
  "mediaId": 42,
  "width": 2480,
  "height": 3508,
  "contentBounds": { "x": 0.04, "y": 0.03, "width": 0.92, "height": 0.94 },
  "blankBorderRatio": 0.12,
  "estimatedSkewDegrees": -0.8,
  "brightnessScore": 0.84,
  "contrastScore": 0.41,
  "suggestions": ["AUTO_CROP", "DESKEW", "AUTO_CONTRAST"],
  "warnings": []
}
```

L'endpoint verifica che la catena traccia-parte-media sia attiva nel tenant corrente. Il risultato può essere memorizzato in cache applicativa per `(mediaId, sha256, analyzerVersion)`; non è necessario persisterlo nella prima versione.

### Salvataggio

```http
POST /api/tracks/{trackId}/scores/{scoreId}/media/{mediaId}/edits
Idempotency-Key: <UUID v4>
Content-Type: application/json
```

Body indicativo:

```json
{
  "expectedTrackVersion": 7,
  "recipeVersion": 1,
  "rotationQuarterTurns": 1,
  "deskewDegrees": -0.8,
  "crops": [
    { "x": 0.0, "y": 0.0, "width": 0.5, "height": 1.0 },
    { "x": 0.5, "y": 0.0, "width": 0.5, "height": 1.0 }
  ],
  "grayscale": true,
  "brightness": 0,
  "contrast": 12,
  "autoContrast": true,
  "threshold": null
}
```

Risposta `200 OK` indicativa:

```json
{
  "trackId": 10,
  "trackVersion": 8,
  "scoreId": 21,
  "replacedMediaId": 42,
  "media": [
    { "index": 73, "name": "Titolo-3-edited-1.png", "order": 3 },
    { "index": 74, "name": "Titolo-3-edited-2.png", "order": 4 }
  ]
}
```

La risposta a una ripetizione con la stessa chiave e lo stesso fingerprint è identica finché il risultato è ancora la pagina corrente. La stessa chiave con payload diverso restituisce `409 Conflict`. Una ricevuta scaduta o riferita a un risultato non più corrente restituisce `409`, senza rieseguire implicitamente la modifica.

### Errori

| Codice | Caso |
| --- | --- |
| `400` | ricetta, coordinate, versione ricetta o chiave idempotente non valide |
| `404` | traccia, parte o media non visibili oppure relazione non esistente nel tenant |
| `409` | versione traccia superata, pagina già sostituita o conflitto idempotente |
| `413` | file o immagine decodificata oltre i limiti |
| `415` | media corrente non supportato come immagine |
| `422` | ricetta valida ma risultato non producibile, per esempio crop vuoto |
| `503` | elaborazione temporaneamente non disponibile o timeout controllato |

Il frontend non distingue un oggetto inesistente da uno appartenente a un altro tenant.

## Architettura backend

### Componenti

- `TrackPageImageResource`: espone esclusivamente analisi e salvataggio nel contesto traccia-parte-media.
- `TrackPageImageService`: autorizza l'operazione, orchestra lettura, trasformazione e sostituzione.
- `ImageTransformationService`: calcola analisi e suggerimenti, quindi decodifica, valida e applica la pipeline deterministica con Java2D/ImageIO.
- `TrackPageEditReceipt`: ricevuta tenant-scoped per idempotenza e replay sicuro.

Il renderer resta separato da `Converter`, che continua a occuparsi della conversione PDF. Questo evita di accrescere una utility statica e rende analisi e trasformazioni testabili senza filesystem o HTTP.

### Sequenza di salvataggio

```text
Frontend        API/orchestratore       Renderer        DB + storage
   | POST edit          |                  |                  |
   |------------------->| valida contesto  |                  |
   |                    | legge sorgente -------------------->|
   |                    |----------------->| render PNG       |
   |                    |<-----------------| byte + metadati   |
   |                    | transazione breve:                  |
   |                    | - ricontrolla versione/relazione    |
   |                    | - crea uno o più media_asset         |
   |                    | - sostituisce la relazione sorgente  |
   |                    | - salva ricevuta e incrementa track |
   |<-------------------| nuovo mediaId/versione               |
```

Il rendering costoso avviene fuori dalla transazione database. Prima del commit una transazione breve blocca o ricontrolla la traccia, confronta `expectedTrackVersion` e verifica nuovamente che `mediaId` occupi ancora la posizione attesa nella parte. La scrittura del nuovo asset usa `MediaService.store`; se la transazione fallisce, la compensazione già prevista elimina il file appena scritto.

Nella stessa transazione, dopo il flush della relazione, il media sostituito viene affidato a `deleteIfUnreferenced`; la cancellazione fisica è eseguita soltanto dopo il commit. Se l'asset è ancora usato da un'altra parte, traccia o dominio resta disponibile. Non viene offerto undo dopo il salvataggio nella prima versione; il reset opera soltanto sulla bozza del dialogo.

### Idempotenza

Le migration tenant creano `track_page_edit_receipt` e ne evolvono il risultato singolo nella lista JSON usata dal multi-crop:

| Campo | Vincolo |
| --- | --- |
| `id` | PK identity |
| `request_key`, `requested_by` | coppia univoca che identifica il tentativo per attore |
| `request_fingerprint` | SHA-256 di route, sorgente, versione attesa e ricetta canonica |
| `track_id`, `sheet_music_id` | riferimenti al contesto della richiesta |
| `source_media_id` | identificativo diagnostico, senza path |
| `result_media_json` | riferimenti ordinati ai risultati, senza path o contenuto immagine |
| `result_track_version` | versione restituita al primo completamento |
| `requested_by`, `created_at` | audit minimo |

La ricevuta non è uno storico editoriale e viene eliminata dopo una finestra configurabile, inizialmente sette giorni. Non conserva il contenuto dell'immagine né valori liberi non necessari.

### Limiti e qualità

Valori iniziali configurabili:

- input massimo 50 MB;
- massimo 40 milioni di pixel decodificati;
- lato massimo 20 000 pixel;
- output massimo 50 MB;
- massimo otto zone e otto immagini risultanti per operazione;
- protezione persistente da richieste concorrenti tramite versione e idempotenza.

Il formato viene riconosciuto dai byte. Sono accettati PNG e JPEG come sorgenti; il risultato è sempre PNG. Prima dell'allocazione completa vengono lette, quando possibile, le dimensioni dall'header per contrastare decompression bomb. Il backend elimina metadati EXIF e profili non necessari, conserva l'orientamento già applicato e non incorpora path o dati utente nel file.

## Modello dati e compatibilità

La relazione attiva continua a essere `sheet_music_media`: non vengono aggiunte colonne di editing a `media_asset` e il payload generale `TracksDTO` continua a contenere soltanto riferimenti ordinati. È necessaria soltanto la tabella di ricevute idempotenti; non è necessario migrare i media esistenti.

I client precedenti continuano a leggere il nuovo asset come una normale pagina. Anteprima, stampa e download non richiedono fallback. La modifica è disponibile soltanto per parti e media già persistiti; le bozze create localmente seguono il flusso esistente di «Salva traccia» prima di usare il nuovo endpoint.

## Sicurezza e isolamento tenant

- L'autorizzazione è verificata sia in `SecurityConfiguration` sia nel servizio applicativo.
- Ogni lookup parte dalla traccia visibile nel tenant corrente e attraversa parte e media; non si cercano separatamente ID forniti dal client per poi comporli.
- Nessun endpoint accetta tenant code, filename definitivo, MIME type risultante, SHA-256, path o storage key.
- Il decoder tratta l'immagine come input non fidato e applica limiti prima e dopo la decompressione.
- I messaggi client non espongono esistenza cross-tenant, stack trace, path fisici o dettagli del decoder.
- Log e metriche non contengono byte immagine, ricetta completa o nomi musicali; possono contenere ID, durata, dimensioni, operazioni aggregate ed esito.

## Osservabilità

Metriche minime:

- numero e durata di analisi e trasformazioni per esito;
- pixel e byte in ingresso/uscita in bucket;
- frequenza delle operazioni applicate;
- conflitti di versione e replay idempotenti;
- errori di decode, limiti superati e compensazioni storage;
- media derivati rimasti senza riferimento dopo errore.

I log correlano tenant in forma tecnica, `trackId`, `scoreId`, `sourceMediaId`, `resultMediaId`, idempotency digest ed esito. Non registrano il contenuto della ricetta se include coordinate puntuali; per diagnosi sono sufficienti i tipi di trasformazione.

## Strategia di test

### Backend unitario

- pipeline e ordine delle trasformazioni con fixture piccole e digest atteso;
- coordinate normalizzate dopo rotazione e raddrizzamento;
- auto-crop su bordo bianco, pagina quasi vuota e contenuto a contatto col bordo;
- stima inclinazione, luminosità e contrasto entro tolleranze dichiarate;
- validazione di limiti, ricetta, formato e dimensioni;
- nessuna mutazione dei byte sorgente.

### Backend integrazione

- analisi e salvataggio autorizzati sulla catena corretta;
- `404` per associazione errata e accesso cross-tenant;
- `409` per versione superata, media già sostituito e chiave riusata con payload differente;
- replay identico senza secondo asset;
- uno o più nuovi asset, sostituzione atomica della relazione sorgente e incremento versione nello stesso commit;
- ricostruzione in due fasi della lista ordinata: disattivazione e flush dei collegamenti correnti, quindi inserimento del nuovo ordine senza violare `uq_sheet_music_media_active_relation` o `uq_sheet_music_media_active_order`;
- asset sorgente condiviso non cancellato; asset non più referenziato eliminato dopo commit;
- errore DB dopo scrittura con cancellazione compensativa;
- input malevolo, decompression bomb simulata e timeout controllato;
- anteprima e stampa della traccia usano il nuovo media.

### Frontend

- visibilità dell'azione per ruolo e disabilitazione con bozza della traccia;
- apertura da card e selezione singola;
- applicazione/reset strumenti, confronto e riepilogo ricetta;
- parità della preview rispetto a fixture del backend entro tolleranza visuale;
- chiusura con conferma, retry che conserva la ricetta e gestione `409`;
- aggiornamento immutabile della pagina sorgente con tutti i risultati, della versione traccia e delle miniature;
- apertura effettiva dell'overlay PrimeNG dal comando «Apri anteprima» anche con change detection `OnPush`;
- icone di rotazione presenti e verso dell'azione «Destra» rappresentato correttamente;
- valori numerici degli slider posizionati sopra il relativo cursore;
- preset di divisione, selezione delle zone, otto maniglie e ridimensionamento di lati e angoli entro i limiti;
- annullamento del gesto di disegno senza creazione accidentale di una zona;
- spalla strumenti ampliata e assenza di overflow orizzontale nei campi del crop;
- revoca delle URL `blob:` e assenza di richieste duplicate;
- tastiera, focus trap, nomi accessibili, campi alternativi al drag e viewport mobile.

### Prestazioni

- analisi e salvataggio di una pagina A4 a 300 DPI entro le soglie concordate sull'ambiente di riferimento;
- memoria stabile dopo almeno 30 aperture consecutive del dialogo;
- nessuna transazione database mantenuta durante il rendering;
- due salvataggi concorrenti sulla stessa pagina producono un solo vincitore e un `409`.

## Stato dell'adozione

1. Modelli, renderer, analisi tecnica, limiti e fixture backend sono implementati.
2. API protette, ricevute idempotenti e migration per risultato singolo e multi-crop sono implementate.
3. Dialogo, canvas di preview, controlli tonali, multi-crop e maniglie sono integrati nel frontend.
4. Il workspace «Parti» aggiorna in modo immutabile media e versione della traccia dopo il salvataggio.
5. I test automatici coprono trasformazioni, servizio, persistenza ordinata PostgreSQL, dialogo, workspace, miniature e client HTTP.
6. Rimane attività operativa il collaudo prestazionale su un insieme rappresentativo di scansioni reali anonimizzate.

In caso di rollback applicativo, i nuovi `media_asset` restano normali immagini compatibili con il codice precedente. La tabella ricevute può rimanere inutilizzata senza influire sulla lettura delle tracce.

## Criteri di accettazione

1. Un ruolo autorizzato può aprire l'editor da una pagina persistita nella scheda Parti.
2. L'editor offre rotazione, raddrizzamento, crop e miglioramenti tonali con preview e reset.
3. La zona attiva mostra otto maniglie trascinabili e può essere regolata anche tramite campi percentuali.
4. L'analisi propone correzioni tecniche senza applicarle automaticamente né interpretare contenuto musicale.
5. «Salva immagine» crea un asset per ogni zona e sostituisce soltanto l'occorrenza selezionata con i risultati ordinati.
6. Una pagina condivisa da due parti cambia soltanto nella parte esplicitamente modificata.
7. Miniatura, anteprima e stampa usano il nuovo asset subito dopo il salvataggio.
8. Il salvataggio è atomico, idempotente e protetto da versione; errori e conflitti non perdono la ricetta locale.
9. La sostituzione multipla non viola gli indici unici della relazione ordinata `sheet_music_media`.
10. Le bozze strutturali della traccia devono essere salvate prima dell'editing immediato dell'immagine.
11. Utenti e utenti esterni non vedono azioni di modifica e non possono invocare le API.
12. Tenant, path e contenuti restano isolati; input sovradimensionati o non validi sono rifiutati in modo controllato.
13. Tutti gli strumenti sono utilizzabili da tastiera e su viewport mobile.
14. Test automatici coprono trasformazioni, concorrenza, idempotenza, compensazione storage, ruoli e tenant isolation.

## Evidenze di verifica del thread

| Verifica | Esito |
| --- | --- |
| Test unitari backend eseguiti durante l'implementazione complessiva | 298 superati |
| Test mirati `ImageTransformationServiceTest`, `TrackPageImageServiceTest`, `MediaStorageCleanupServiceTest` | 9 superati |
| Provisioning tenant e applicazione delle migration con PostgreSQL 17 | superato |
| Regressione PostgreSQL per sostituzione di una pagina con più risultati ordinati | superata |
| Test frontend mirati di editor, dettaglio, miniature, workspace e servizio HTTP | 17 superati dopo il multi-crop |
| Test specifici dell'editor dopo l'introduzione delle maniglie | 4 superati |
| Build Angular di produzione | superata |
| Formattazione Prettier, Checkstyle, parsing XML e `git diff --check` | superati |

Durante la verifica completa di `RelationalSoftDeleteIT` è emersa una failure preesistente e indipendente dall'editing immagini: il test inventario inserisce una riga senza il campo obbligatorio `qr_public_id`. Il metodo di regressione dedicato a `sheet_music_media` è stato eseguito isolatamente con esito positivo.

## Fuori scope

- modifica del PDF sorgente o rigenerazione automatica del PDF caricato;
- OCR, riconoscimento di note, rimozione di annotazioni manoscritte o ricostruzione generativa;
- editing vettoriale o modifica semantica dello spartito;
- applicazione massiva della stessa ricetta a più pagine;
- cronologia e ripristino di revisioni già salvate;
- editing per utenti standard o esterni;
- nuovi provider di storage, CDN o URL pubblici;
- elaborazione asincrona: la prima versione opera sincronicamente su una pagina entro limiti controllati.
