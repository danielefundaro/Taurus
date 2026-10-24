# Strumenti da aggiungere dai PDF delle tracce

Quando l'elaborazione di un PDF legge sulla prima pagina di una parte il nome di uno strumento che il tenant non ha, oggi quel nome va perso. Il documento progetta gli **strumenti da aggiungere**: la parte viene creata senza quello strumento e Taurus propone di aggiungerlo al tenant. Chi gestisce l'archivio sceglie quali strumenti creare o associare a strumenti esistenti, e salva: le parti che li riportavano ricevono lo strumento in un solo passaggio.

Estende l'[Elaborazione asincrona dei PDF delle tracce](track-pdf-processing-spec.md) e il [Workspace per la gestione delle parti](track-parts-workspace-spec.md), e usa il catalogo di riferimento descritto in [Ascolto e correzione delle parti](track-score-playback-spec.md#catalogo-di-riferimento). Il testo delle pagine scansionate arriva dalla [Lettura del testo nel servizio OMR](omr-text-recognition-spec.md). Gli errori seguono lo [Standard degli errori applicativi](application-errors-standard.md), le notifiche lo [Stile editoriale delle notifiche interne](notification-editorial-style.md) e l'interfaccia il [Taurus Layout Standard](taurus-layout-standard.md).

## Stato del documento

ID catalogo: `track-instrument-suggestions`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

Le scelte sono fissate in [Decisioni prese](#decisioni-prese).

## Problema

- Le righe lette sulla prima pagina di una parte vengono confrontate solo con gli strumenti del tenant. Un nome che non corrisponde a nessuno di essi va perso: la parte resta senza strumento e nulla segnala che il tenant potrebbe aggiungerlo.
- Il confronto attuale accetta che una riga contenga il nome dello strumento o il contrario, e si ferma alla prima corrispondenza. Produce così assegnazioni sbagliate: «Clarinetto basso» va a «Clarinetto» se il tenant non ha «Clarinetto basso», e «Clarinetto in Sib» va a «Clarinetto in Mib».
- Chi carica un PDF con strumenti nuovi deve crearli a mano, uno per uno, e poi riaprire ogni parte per assegnarli.

## Obiettivo

- Riconoscere nelle righe lette i nomi degli strumenti, anche di quelli che il tenant non ha.
- Proporre di aggiungere al tenant gli strumenti trovati, senza crearne nessuno senza una scelta esplicita.
- Assegnare lo strumento creato o associato a tutte le parti che lo riportano, in un'unica operazione.
- Evitare strumenti doppi nel tenant.

## Fuori scope

- La lettura del testo delle pagine: livello 1 dal PDF e OCR restano come sono, o come li porta la [Lettura del testo nel servizio OMR](omr-text-recognition-spec.md).
- La creazione di strumenti senza conferma di chi gestisce l'archivio.
- L'assegnazione degli strumenti ai soci.
- Il raggruppamento delle pagine in parti dai segnalibri del PDF.
- Altri usi del testo letto, come proporre la descrizione della parte.
- I suggerimenti per le parti create prima di questa funzionalità.

## Contesto attuale

Il flusso parte da `Receiver`, consumatore della coda `upload.files`, e prosegue in `PdfProcessingService.buildSheets`:

1. il PDF viene convertito in immagini PNG a 300 DPI, applicando le pagine escluse e i ritagli delle annotazioni;
2. le pagine sono raggruppate in parti dai segnalibri del PDF; senza segnalibri l'intero PDF è una parte sola;
3. per ogni parte si legge il testo della prima pagina: il testo selezionabile del PDF (livello 1) oppure, se manca, l'OCR;
4. le righe utili (almeno 3 caratteri, non solo numeri o numeri romani) vengono confrontate con i nomi degli strumenti del tenant, ignorando maiuscole, accenti, numeri finali e un «in …» finale; gli strumenti trovati vengono assegnati e la parte viene marcata `needsReview`;
5. `TracksService.appendScores` aggiunge le parti alla traccia; nessuna notifica segnala la fine dell'elaborazione.

Altri elementi utili al progetto:

- `InstrumentPlaybackCatalog`, il catalogo di riferimento dell'ascolto, riconosce gli strumenti dal nome con parole chiave cercate come parole intere: vince la più lunga, così «clarinetto basso» prevale su «clarinetto». Riconosce anche la tonalità nel nome e propone suono e trasposizione ([Proposta di configurazione](track-score-playback-spec.md#proposta-di-configurazione)).
- Le parti hanno un id stabile. Il salvataggio della traccia ricostruisce l'elenco delle parti: quelle assenti vengono eliminate, e `needs_review` viene riscritto con il valore inviato dal frontend.
- Nel workspace Parti l'unione di più parti crea una parte nuova, senza id; la divisione lascia l'id al primo pezzo.
- Tracce e strumenti sono gestiti dagli stessi ruoli: super amministratore, amministratore e archivista.
- Il nome dello strumento non ha un vincolo di unicità nel database.
- Un socio vede solo le parti dei propri strumenti.
- Nella [preparazione dell'evento](event-preparation-spec.md#spartiti) una parte senza strumenti produce un blocco, perché non è distribuibile ai soci.

## Scelte di fondo

### Riconoscere uno strumento che il tenant non ha

Una riga letta può essere il nome di uno strumento, un titolo, un'indicazione di tempo o il nome del compositore. Per gli strumenti del tenant basta il confronto con i loro nomi; per quelli che il tenant non ha serve un vocabolario, altrimenti ogni titolo diventerebbe uno strumento da creare.

**Scelta: il catalogo di riferimento `InstrumentPlaybackCatalog`.** Conosce già i nomi degli strumenti della banda, con le regole di parola intera e di parola più lunga, ed è lo stesso che propone suono e trasposizione di uno strumento nuovo. Una riga senza parole chiave del catalogo viene scartata. Il prezzo è che uno strumento assente dal catalogo non viene mai proposto: il catalogo va arricchito (decisione 1).

## Decisioni prese

Le scelte discusse e fissate il 20/10/2026, con il motivo per cui l'alternativa è stata scartata. L'elenco delle voci da aggiungere al catalogo di riferimento è un valore iniziale, da completare con i nomi letti su parti reali.

### 1. Vocabolario dei nomi

**Un solo catalogo**: `InstrumentPlaybackCatalog`, arricchito con abbreviazioni («Cl.», «Fl.», «Sax A.»), sinonimi e strumenti che oggi non conosce, come arpa, contrabbasso, chitarra e pianoforte. Le voci senza proposta di suono sono ammesse. Le abbreviazioni ambigue, come «Tr.» per tromba e trombone, restano fuori. L'elenco nasce dai nomi letti su parti reali, compresi quelli raccolti dalla prova preliminare della [lettura del testo](omr-text-recognition-spec.md#piano-di-adozione-e-compatibilità). Un secondo vocabolario, solo per il riconoscimento, avrebbe dovuto restare allineato a mano con quello dell'ascolto.

### 2. Regole di confronto con gli strumenti del tenant

**Nome esatto, poi parola chiave più lunga**, con la regola attuale del contenimento solo per gli strumenti del tenant che il catalogo non conosce ([Riconoscimento dei nomi](#riconoscimento-dei-nomi)). Uno strumento con una tonalità diversa da quella della riga non corrisponde mai. La regola attuale da sola assegna strumenti sbagliati, come «Clarinetto basso» a «Clarinetto» o «Clarinetto in Sib» a «Clarinetto in Mib», e non distingue uno strumento sconosciuto da un testo qualsiasi.

### 3. Corrispondenza ambigua

**Si chiede all'utente**: quando più strumenti del tenant condividono la parola chiave della riga, la parte riceve un suggerimento **Associa** con la scelta fra quegli strumenti. Assegnare il primo dipenderebbe dall'ordine degli strumenti; assegnarli tutti darebbe alla parte strumenti che non la suonano.

### 4. Nome proposto

**La riga letta senza numeri d'ordine (1°, I, II) e senza punteggiatura finale, con la tonalità e l'iniziale maiuscola**: «Clarinetto 1° in Sib» diventa «Clarinetto in Sib». Il nome resta sempre modificabile. La riga intera avrebbe creato uno strumento per ogni numero d'ordine; il solo nome del catalogo avrebbe perso la tonalità, che distingue strumenti diversi come il clarinetto in Sib e quello in Mib.

### 5. Unicità del nome dello strumento

**Un controllo nel servizio, sotto un lock transazionale del tenant**: nessuno strumento può avere lo stesso nome di un altro, confrontando i nomi in minuscolo, senza accenti, punteggiatura e spazi ripetuti. Numeri d'ordine e tonalità contano, quindi «Clarinetto in Sib» e «Clarinetto in Mib» restano distinti. Il controllo vale anche per la creazione e la rinomina manuali. Un indice univoco nel database resta da valutare a parte: i tenant esistenti potrebbero avere già nomi doppi, e la migrazione fallirebbe.

### 6. Unione di parti con suggerimenti aperti

**Conferma prima dell'unione, poi i suggerimenti delle parti unite vengono eliminati.** Trasferirli alla parte nuova richiederebbe di portarli nel salvataggio della traccia, che oggi non li conosce; bloccare l'unione sarebbe sproporzionato per un caso raro.

### 7. Parti con strumenti già assegnati

**Non vengono preselezionate**; se l'utente le seleziona, il nuovo strumento si aggiunge a quelli che hanno. Preselezionarle avrebbe dato un secondo strumento a una parte già sistemata a mano, senza che l'utente se ne accorgesse.

### 8. Suono e trasposizione dello strumento creato

**Si applica la proposta del catalogo**, come fa l'importazione iniziale; resta correggibile dal dettaglio dello strumento. Chi crea lo strumento dal pannello non passa dal suo dettaglio: lasciarlo da configurare avrebbe richiesto un secondo passaggio per ogni strumento creato.

### 9. Soci del nuovo strumento

**Un avviso nel pannello**, senza assegnazioni automatiche: quali soci suonano lo strumento lo sa l'associazione, non il PDF.

### 10. Partiture

**Oltre 4 nomi sulla stessa parte, nessuna preselezione** ed etichetta «Possibile partitura». Preselezionarli avrebbe assegnato alla partitura del direttore tutti gli strumenti della banda, rendendola visibile a tutti i soci.

### 11. Notifica

**A chi ha caricato il PDF, solo quando ci sono strumenti da aggiungere**, con il riepilogo dell'elaborazione e il collegamento alla scheda Parti della traccia, nello [stile editoriale delle notifiche](notification-editorial-style.md). Notificare ogni elaborazione, o tutti quelli che gestiscono l'archivio, avrebbe prodotto notifiche senza un'azione da compiere.

### 12. Durata dei suggerimenti aperti

**Nessuna scadenza**: restano finché esiste la parte o finché qualcuno decide. Una scadenza farebbe sparire suggerimenti ancora utili senza che nessuno li abbia visti; l'etichetta sulle parti li tiene in vista.

### 13. Nomi ignorati

**Valgono solo per quelle parti**, senza un elenco di nomi da non proporre più nel tenant. Un elenco del genere sarebbe un'impostazione in più da gestire, e un nome ignorato per errore non verrebbe più proposto.

### 14. Parti senza strumenti con suggerimenti aperti

**`needsReview` resta falso** finché la parte non riceve strumenti; l'etichetta **Strumento da aggiungere** viene dalla tabella dei suggerimenti. Nella preparazione dell'evento una parte senza strumenti produce già un blocco: marcarla da rivedere avrebbe aggiunto un avviso allo stesso problema.

## Termini

- **Strumento suggerito**: uno strumento del tenant assegnato automaticamente alla parte, che viene marcata `needsReview`, come oggi. Il flag si azzera con Segna come verificata nel [workspace Parti](track-parts-workspace-spec.md#verificare-una-parte).
- **Strumento da aggiungere**: un nome letto sulla parte che corrisponde al catalogo di riferimento ma a nessuno strumento del tenant. Viene salvato come suggerimento sulla parte, che resta senza quello strumento finché qualcuno non decide.

## Flusso

1. Il backend legge il testo della prima pagina di ogni parte: dal PDF (livello 1) oppure con l'OCR.
2. Ogni riga utile segue le regole di [Riconoscimento dei nomi](#riconoscimento-dei-nomi).
3. Le parti si dividono in tre gruppi:
   - **con strumenti del tenant**: assegnati, parte marcata `needsReview`;
   - **con strumenti da aggiungere**: parte creata senza quegli strumenti, con un suggerimento salvato per ciascun nome;
   - **senza strumenti**: nessuna riga riconosciuta, per esempio perché la pagina non ha testo leggibile.
4. Al termine del job chi ha caricato il PDF riceve una notifica con il riepilogo, quando ci sono strumenti da aggiungere (decisione 11).
5. Nel workspace Parti il pannello **Strumenti da aggiungere** elenca i nomi trovati, ciascuno con le parti che lo riportano. Per ogni nome si sceglie:
   - **Crea**, con il nome proposto modificabile;
   - **Associa** a uno strumento esistente;
   - **Ignora**.
6. Al salvataggio, in un'unica transazione: gli strumenti vengono creati, gli strumenti creati o associati vengono assegnati alle parti selezionate, le parti vengono marcate `needsReview` e i suggerimenti vengono chiusi.

## Riconoscimento dei nomi

Per ogni riga utile, nell'ordine (decisione 2):

1. **Nome esatto.** Se la riga, normalizzata come oggi, coincide con il nome normalizzato di uno strumento del tenant, lo strumento viene assegnato.
2. **Parola chiave.** Altrimenti si cerca la parola chiave più lunga del catalogo di riferimento contenuta nella riga:
   - uno solo strumento del tenant ha la stessa parola chiave: viene assegnato;
   - più strumenti del tenant la hanno, per esempio «Clarinetto 1°» e «Clarinetto 2°» per la riga «Clarinetto in Sib»: nessuna assegnazione, ma un suggerimento **Associa** con la scelta fra quegli strumenti (decisione 3);
   - nessuno strumento del tenant la ha: suggerimento **Crea**, con il nome proposto (decisione 4).
3. **Strumenti del tenant fuori catalogo.** Una riga senza parole chiave si confronta ancora, con la regola attuale, con i soli strumenti del tenant il cui nome non contiene parole chiave del catalogo: un tenant può avere strumenti che il catalogo non conosce.
4. Le righe che non soddisfano nessuna regola vengono scartate.

In tutti i passi uno strumento che indica una tonalità diversa da quella della riga non corrisponde, così «Clarinetto in Sib» non viene assegnato a «Clarinetto in Mib». La tonalità si riconosce come nella [Proposta di configurazione](track-score-playback-spec.md#proposta-di-configurazione): dopo la parola «in», oppure come forma con alterazione a sé stante (`sib`, `mib`…). Uno strumento senza tonalità nel nome corrisponde a qualunque tonalità.

Le regole valgono per le righe del livello 1 come per quelle dell'OCR. Lo stesso nome trovato su più parti della traccia produce un solo elemento nel pannello, con tutte le parti coinvolte.

## Modello dati e migrazioni

Nuova tabella dello schema del tenant `sheet_music_instrument_suggestion`, in una migrazione inclusa in `tenant-master.xml`:

| Colonna | Contenuto |
| --- | --- |
| `id` | identificativo |
| `sheet_music_id` | parte; il suggerimento viene eliminato con la parte |
| `read_text` | riga letta, fino a 255 caratteri |
| `proposed_name` | nome proposto per la creazione |
| `normalized_name` | nome proposto nella forma del controllo di unicità (decisione 5), per raggruppare e confrontare |
| `catalog_keyword` | parola chiave del catalogo trovata |
| `source` | `PDF_TEXT` oppure `OCR` |
| `status` | `OPEN`, `ACCEPTED` oppure `IGNORED` |
| `instrument_id` | strumento creato o associato, valorizzato con `ACCEPTED` |
| `resolved_by`, `resolved_at` | autore e istante della decisione |

Più colonne di audit e cancellazione logica, come le altre tabelle del tenant. Un indice univoco parziale impedisce due suggerimenti `OPEN` con lo stesso `normalized_name` sulla stessa parte.

I suggerimenti stanno in una tabella propria e non nel DTO della parte: il salvataggio della traccia, che riscrive `needs_review` e l'elenco delle parti, non li tocca, salvo eliminarli insieme alle parti eliminate.

I suggerimenti vengono salvati nella stessa transazione di `appendScores`, quando le parti hanno già il loro id.

## API REST

| Metodo e percorso | Descrizione |
| --- | --- |
| `GET /api/tracks/{id}/instrument-suggestions` | suggerimenti aperti della traccia raggruppati per nome, con le parti coinvolte e l'azione proposta |
| `POST /api/tracks/{id}/instrument-suggestions/apply` | applica le decisioni e restituisce la traccia aggiornata |

La lettura rivaluta ogni nome con gli strumenti attuali del tenant: un nome per cui nel frattempo esiste uno strumento, creato da un'altra traccia o a mano, viene proposto come **Associa** invece che come **Crea**.

```json
{
  "trackVersion": 12,
  "decisions": [
    { "normalizedName": "clarinetto basso", "action": "CREATE", "name": "Clarinetto basso", "sheetMusicIds": [101, 102] },
    { "normalizedName": "flicorno baritono", "action": "ASSOCIATE", "instrumentId": 7, "sheetMusicIds": [103] },
    { "normalizedName": "tromba", "action": "IGNORE" }
  ]
}
```

- Le parti non selezionate di un nome accettato vengono chiuse come `IGNORED`; i nomi non presenti fra le decisioni restano aperti.
- Il percorso ricade sotto `/api/tracks/**`: lo usano gli stessi ruoli che gestiscono tracce e strumenti.
- Errori:
  - `version.conflict` (409): la traccia è cambiata dopo la lettura;
  - `track.score.notFound` (400): una parte non appartiene alla traccia;
  - nuove chiavi `instrument.name.exists` (409, un nome da creare esiste già) e `instrumentSuggestion.notOpen` (409, suggerimento già chiuso), da aggiungere al catalogo degli errori con i testi del frontend.

## Applicazione

- Tutto in una transazione, con la traccia bloccata come in `appendScores` e il controllo della versione. In caso di errore non viene creato nulla.
- Prima di creare uno strumento si controlla, sotto il lock del tenant, che nessuno strumento abbia lo stesso nome (decisione 5).
- Lo strumento creato riceve la proposta di suono e trasposizione del catalogo (decisione 8) e produce gli stessi effetti della creazione manuale, audit e notifiche compresi.
- Lo strumento viene aggiunto in coda agli strumenti di ogni parte selezionata, senza togliere quelli già assegnati, e la parte viene marcata `needsReview`.
- La traccia riceve una nuova versione, così un workspace aperto altrove se ne accorge al salvataggio.

## Interfaccia utente

- Il pannello **Strumenti da aggiungere** compare nel workspace Parti solo con suggerimenti aperti e solo ai ruoli che gestiscono tracce e strumenti.
- Ogni nome mostra la riga letta, la scelta Crea, Associa o Ignora, il nome proposto modificabile e le parti coinvolte con una casella di selezione.
- Sono preselezionate le parti ancora senza strumenti (decisione 7) e nessuna parte con molti nomi (decisione 10).
- Uno strumento nuovo mostra l'avviso che nessun socio lo suona ancora: le parti assegnate restano invisibili ai soci finché lo strumento non viene assegnato alle persone (decisione 9).
- Il pannello è disabilitato finché il workspace ha modifiche non salvate, come le azioni dell'ascolto.
- Le parti con suggerimenti aperti mostrano l'etichetta **Strumento da aggiungere**.
- Unire parti con suggerimenti aperti chiede conferma (decisione 6).
- Il riepilogo dell'ultima elaborazione indica quante parti hanno strumenti assegnati, quante strumenti da aggiungere e quante nessuno strumento.

## Ciclo di vita dei suggerimenti

| Evento | Effetto sui suggerimenti |
| --- | --- |
| Parte eliminata | eliminati con la parte |
| Parti unite | la parte unita è nuova: i suggerimenti delle parti di partenza vengono eliminati, dopo la conferma |
| Parte divisa | restano al pezzo che conserva l'id, il primo |
| Strumento assegnato a mano alla parte | restano aperti; la parte non viene preselezionata |
| Strumento creato a mano o da un'altra traccia | il nome viene proposto come **Associa** |
| Nuovo PDF sulla stessa traccia | le parti nuove ricevono i propri suggerimenti; quelli esistenti non cambiano |
| Traccia eliminata | eliminati con le parti |

## Casistiche

### Righe lette

| Situazione | Comportamento |
| --- | --- |
| Nome di uno strumento del tenant | strumento assegnato, parte marcata `needsReview` |
| Nome con parola chiave del catalogo, assente dal tenant | parte senza quello strumento, suggerimento **Crea** |
| Nome con parola chiave condivisa da più strumenti del tenant | nessuna assegnazione, suggerimento **Associa** con la scelta |
| Nome senza parola chiave e assente dal tenant («Arpa», se il catalogo non la conosce) | nessun suggerimento |
| Testo senza nomi di strumenti (titolo, tempo, compositore) | righe scartate, parte senza strumenti |
| Nome abbreviato («Cl.», «Sax A.») | riconosciuto solo se il catalogo ha l'abbreviazione (decisione 1) |
| Abbreviazione ambigua («Tr.») | nessun suggerimento |
| «Clarinetto basso» con il tenant che ha solo «Clarinetto» | suggerimento **Crea** per «Clarinetto basso», non più assegnazione a «Clarinetto» |
| «Clarinetto in Sib» con il tenant che ha solo «Clarinetto in Mib» | nessuna corrispondenza per la tonalità diversa: suggerimento **Crea** per «Clarinetto in Sib» |
| «Clarinetto 1° in Sib» con il tenant che ha «Clarinetto 1°» | strumento assegnato: il nome del tenant non indica una tonalità |
| Due nomi sulla stessa parte («Clarinetto basso e Fagotto») | un esito per ciascun nome |
| Partitura con molti nomi nel margine | un esito per ciascun nome; nel pannello nessuna parte preselezionata |
| Titolo che contiene un nome di strumento («Concerto per tromba») | possibile suggerimento sbagliato, da ignorare nel pannello |
| Pagina senza testo leggibile | parte senza strumenti e senza suggerimenti |

### Pannello e applicazione

| Situazione | Comportamento |
| --- | --- |
| Stesso nome su più parti della traccia | un solo elemento nel pannello, con tutte le parti |
| Stesso nome su più tracce | ogni traccia ha i propri suggerimenti; dopo la creazione in una, nelle altre il nome diventa **Associa** |
| Nome proposto modificato prima della creazione | lo strumento viene creato con il nome scritto dall'utente |
| Nome da creare già esistente nel tenant | errore `instrument.name.exists`, nessuna modifica; il pannello si ricarica e propone **Associa** |
| Creazione o rinomina manuale con un nome già presente | errore `instrument.name.exists` (decisione 5) |
| Due persone applicano i suggerimenti insieme | la seconda riceve `version.conflict` e ricarica il pannello |
| Workspace con modifiche non salvate | pannello disabilitato |
| Parte con uno strumento assegnato a mano | non preselezionata; se selezionata, riceve lo strumento in aggiunta |
| Parti deselezionate per un nome accettato | suggerimento chiuso come ignorato per quelle parti |
| Tutti i nomi ignorati | nessuno strumento creato, il pannello sparisce |
| Strumento nuovo senza soci | avviso nel pannello; le parti restano visibili solo a chi gestisce l'archivio |
| Utente senza privilegi | pannello assente, API con 403 |
| Nessuna decisione presa | i suggerimenti restano aperti (decisione 12) |

## Autorizzazioni e isolamento tenant

- Le API dei suggerimenti stanno sotto `/api/tracks/**` e sono riservate a super amministratore, amministratore e archivista, gli stessi ruoli che gestiscono tracce e strumenti.
- Strumenti, parti e suggerimenti sono quelli del tenant del job o della richiesta, nel suo schema. L'applicazione verifica che ogni parte appartenga alla traccia e ogni strumento associato al tenant.
- Nessuna funzionalità per tenant: i suggerimenti seguono l'elaborazione dei PDF, già disponibile a chi gestisce l'archivio.

## Osservabilità e sicurezza

- Al termine del job, una riga di log con i conteggi: parti con strumenti assegnati, con strumenti da aggiungere e senza strumenti, senza il testo letto.
- Il testo letto è un dato non fidato: serve solo al confronto con i nomi degli strumenti e come nome proposto. Il nome proposto è sempre visibile e modificabile prima della creazione, ed è soggetto alle stesse validazioni del nome inserito a mano.
- Creazioni e associazioni lasciano autore e istante sul suggerimento, oltre all'audit dello strumento creato.

## Strategia di test

### Backend

- riconoscimento dei nomi: nome esatto, parola chiave con uno, più o nessuno strumento del tenant, strumento del tenant fuori catalogo, riga senza parole chiave, «Clarinetto basso» con il solo «Clarinetto», tonalità diverse e strumento senza tonalità, più nomi sulla stessa parte, righe dal livello 1 e dall'OCR;
- suggerimenti salvati con le parti nella stessa transazione; eliminati con la parte; conservati dal salvataggio della traccia; rimasti al primo pezzo di una divisione;
- applicazione: creazione, associazione e rifiuto; parti deselezionate; nome già esistente; versione cambiata; parte di un'altra traccia; transazione annullata senza strumenti creati; proposta del catalogo applicata allo strumento creato;
- unicità: creazione e rinomina manuali con un nome già presente rifiutate; due creazioni concorrenti dello stesso nome con una sola riuscita; nomi con tonalità diverse accettati;
- notifica solo con strumenti da aggiungere, a chi ha caricato il PDF;
- autorizzazioni: 403 per i ruoli senza privilegi; isolamento fra tenant;
- migrazione su PostgreSQL, con l'indice univoco parziale.

### Frontend

- pannello presente solo con suggerimenti aperti e solo per i ruoli con privilegi;
- preselezione delle parti senza strumenti e di nessuna parte con molti nomi;
- nome proposto modificabile, scelta fra Crea, Associa e Ignora;
- pannello disabilitato con modifiche non salvate; conferma prima di unire parti con suggerimenti;
- errori `version.conflict` e `instrument.name.exists` con ricaricamento del pannello.

### Verifica manuale

- un PDF di parti reali con segnalibri, digitale o scansionato: strumenti assegnati, strumenti da aggiungere e parti senza strumenti, poi creazione e associazione dal pannello e verifica dell'assegnazione;
- notifica ricevuta da chi ha caricato il PDF, con il collegamento alla scheda Parti.

## Criteri di accettazione

- una parte che riporta uno strumento del catalogo assente dal tenant viene creata senza quello strumento e il nome compare nel pannello **Strumenti da aggiungere**;
- creando o associando lo strumento dal pannello, tutte le parti selezionate lo ricevono in un'unica operazione e vengono marcate da rivedere;
- nessuno strumento viene creato senza una scelta esplicita, e nessuno strumento nuovo o rinominato, dal pannello o a mano, può avere il nome di uno strumento già presente;
- una riga non viene mai assegnata a uno strumento con una tonalità diversa;
- i suggerimenti sopravvivono al salvataggio della traccia e spariscono con le parti eliminate;
- gli strumenti assegnati automaticamente seguono le regole di riconoscimento dei nomi, per i PDF digitali come per quelli scansionati.

## Piano di adozione e compatibilità

1. **Vocabolario.** Catalogo di riferimento arricchito con abbreviazioni, sinonimi e strumenti mancanti, a partire dai nomi letti su parti reali.
2. **Backend.** Riconoscimento dei nomi, tabella e migrazione, controllo di unicità, API, notifica e test.
3. **Frontend.** Pannello nel workspace Parti, etichetta sulle parti, conferma prima dell'unione e test.
4. **Documentazione.** Nuove prove nel [Runbook di test](runbook-test.md) e il pannello nel [Workspace per la gestione delle parti](track-parts-workspace-spec.md).

La funzionalità non dipende dalla [Lettura del testo nel servizio OMR](omr-text-recognition-spec.md): con i PDF digitali usa il testo del PDF, con quelli scansionati usa l'OCR disponibile, quello attuale o quello del servizio. Può quindi essere sviluppata prima o dopo.

Compatibilità:

- una migrazione dello schema del tenant aggiunge la tabella dei suggerimenti, senza toccare i dati esistenti; le parti già presenti non ricevono suggerimenti;
- la creazione e la rinomina manuali di uno strumento rifiutano un nome già presente nel tenant; i nomi doppi già esistenti restano come sono;
- le nuove regole di riconoscimento dei nomi cambiano alcune assegnazioni rispetto a oggi, come «Clarinetto basso» non più assegnato a «Clarinetto»; valgono solo per i PDF elaborati dopo l'aggiornamento.

## Rischi

- **Suggerimenti sbagliati.** Titoli e indicazioni di tempo possono contenere nomi di strumenti. Mitigazione: vocabolario del catalogo, revisione obbligatoria delle parti marcate e nessuna creazione senza scelta esplicita.
- **Strumenti doppi.** Nomi diversi per lo stesso strumento («Sax contralto» e «Sassofono contralto») possono portare a crearlo due volte. Mitigazione: confronto per parola chiave, proposta **Associa**, controllo sul nome e nome sempre modificabile.
- **Suggerimenti dimenticati.** Suggerimenti mai decisi restano aperti senza che nessuno li guardi. Mitigazione: notifica a fine job ed etichetta sulle parti.
- **Vocabolario incompleto.** Uno strumento che il catalogo non conosce non viene mai proposto. Mitigazione: elenco costruito sulle parti reali e aggiornabile senza modifiche al modello dati.
