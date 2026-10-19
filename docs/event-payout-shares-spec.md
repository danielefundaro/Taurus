# Ripartizione dei compensi ai presenti

La funzionalità calcola quanto spetta a ciascun socio che ha presenziato a un evento, a partire dal compenso dell'evento, dalle spese e dalla quota trattenuta per la cassa dell'associazione, e consente di liquidare le quote generando i movimenti economici corrispondenti.

Si appoggia interamente alle entità già esistenti: le presenze sono quelle registrate sull'evento (`calendar_event_presence`), il compenso e le spese sono il preventivo descritto in [Gestione economica, cassa e conti correnti](financial-management-spec.md), e la liquidazione produce un normale movimento di uscita dello stesso modulo. Le convenzioni di interfaccia seguono il [Taurus Layout Standard](taurus-layout-standard.md), gli errori lo [Standard degli errori applicativi](application-errors-standard.md) e le notifiche lo [Stile editoriale delle notifiche interne](notification-editorial-style.md).

## Stato del documento

ID catalogo: `event-payout-shares`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

## Obiettivo

Dare a chi amministra la cassa uno strumento che risponda a due domande: *quanto spetta a ciascun presente di questo evento* e *quanto devo ancora a questa persona in tutto*.

La prima versione deve permettere di:

- calcolare la quota di ogni presente a partire da compenso, spese e percentuale di cassa;
- arrotondare per difetto tutte le quote a un passo configurabile, uguale per tutti;
- conservare la percentuale e il passo come parametri datati, modificabili anche con decorrenza retroattiva;
- distinguere la quota simulata sul preventivo dalla quota reale sul consuntivo, e liquidare soltanto la seconda;
- simulare in tempo reale l'effetto di ogni modifica prima di liquidare qualunque cosa;
- ridurre la quota di un singolo presente con un fattore motivato, valido per quell'evento;
- liquidare a un socio il totale delle quote maturate, o una parte, con un solo movimento di uscita;
- mantenere tracciabilità, isolamento tenant e coerenza con i saldi del modulo economico.

## Fuori scope

- la contribuzione fiscale o previdenziale sui compensi: Taurus resta uno strumento gestionale, non un sostituto della contabilità o del sostituto d'imposta;
- il pagamento effettivo: il movimento registra l'uscita, non esegue bonifici;
- la ripartizione di eventi senza presenze registrate, che resta un'operazione manuale;
- la distribuzione di quote a soggetti che non sono utenti del tenant.

## Decisioni approvate

| Aspetto | Decisione |
| --- | --- |
| Perimetro dei beneficiari | gli utenti presenti all'evento secondo `calendar_event_presence` |
| Base di calcolo | due: il preventivo produce la quota simulata, il consuntivo la quota reale |
| Liquidabilità | soltanto la quota reale, cioè quella calcolata sul consuntivo |
| Quota per la cassa | percentuale del compenso dell'evento, sottratta prima della divisione |
| Arrotondamento | per difetto, a un passo uguale per tutti, applicato alla quota del singolo |
| Destinazione del residuo | la cassa dell'associazione, che incassa anche ciò che l'arrotondamento non distribuisce |
| Parametri | percentuale e passo versionati per data di decorrenza, modificabili anche nel passato |
| Parametri applicabili | la versione con decorrenza più recente non successiva alla data dell'evento |
| Fattore per presente | moltiplicatore fra `0` e `1`, con motivo obbligatorio, valido per un solo evento |
| Effetto del fattore | riduce soltanto la quota di quel presente; le altre non cambiano |
| Destinazione della riduzione | la cassa, insieme al residuo di arrotondamento |
| Simulazione | calcolata dal backend e ricalcolata a ogni modifica, senza salvare nulla |
| Liquidazione | un solo movimento di uscita per operazione, con il dettaglio per evento nelle righe |
| Liquidazione parziale | ammessa: ogni riga può essere liquidata per un importo inferiore al dovuto |
| Ruoli | gli stessi del modulo economico: Super Admin, Admin e Tesoriere |
| Flag tenant | `FINANCE`, perché la funzionalità vive dentro la gestione economica |

Le scelte alternative valutate e scartate, con il motivo, sono raccolte in [Decisioni chiuse](#decisioni-chiuse).

## Terminologia

- **Base dell'evento**: l'importo da cui parte la ripartizione, cioè il compenso dell'evento.
- **Quota di cassa**: la parte della base trattenuta dall'associazione, espressa in percentuale.
- **Netto distribuibile**: ciò che resta da dividere fra i presenti dopo spese e quota di cassa.
- **Quota base**: il netto distribuibile diviso per il numero dei presenti, prima di fattore e arrotondamento.
- **Quota teorica**: la quota base ridotta dal fattore del presente, prima dell'arrotondamento.
- **Quota**: l'importo dovuto al presente, arrotondato per difetto al passo.
- **Quota simulata**: la quota calcolata sul preventivo dell'evento.
- **Quota reale**: la quota calcolata sul consuntivo, l'unica liquidabile.
- **Residuo di arrotondamento**: la differenza fra netto distribuibile e somma delle quote.
- **Fattore**: il moltiplicatore che pesa la quota di un presente in un singolo evento.
- **Liquidazione**: il movimento di uscita che paga una o più quote a un socio.

## Calcolo

### Formula

```text
quota di cassa      = base evento × percentuale
netto distribuibile = base evento − spese − quota di cassa
quota base          = netto distribuibile / numero dei presenti
quota teorica       = quota base × fattore del presente, 1 quando non è indicato
quota               = ⌊quota teorica / passo⌋ × passo
residuo             = netto distribuibile − somma delle quote
cassa effettiva     = quota di cassa + residuo
```

Quando nessun presente ha un fattore, la quota è la quota base arrotondata: è la formula di partenza. Il fattore può soltanto ridurre, mai aumentare, quindi la somma delle quote non supera mai il netto distribuibile e l'associazione non può trovarsi a distribuire più di quanto ha guadagnato.

Tutti gli importi sono `BigDecimal` con quattro decimali; la quota di cassa è arrotondata a due decimali con `HALF_UP` prima di essere sottratta, le quote con la regola del passo. Il backend resta autorevole sui calcoli, come per i rendiconti: l'interfaccia non ricalcola in proprio.

### Esempio

Evento da 1.000,00 €, spese 120,00 €, percentuale di cassa 20%, passo 5,00 €, cinque presenti di cui uno con fattore 0,5 perché presente a metà serata.

```text
quota di cassa      = 1.000,00 × 20% = 200,00
netto distribuibile = 1.000,00 − 120,00 − 200,00 = 680,00
quota base          = 680,00 / 5 = 136,00
quota teorica       = 136,00 (fattore 0,5: 68,00)
quote               = 135,00 × 4 + 65,00 = 605,00
residuo             = 680,00 − 605,00 = 75,00
cassa effettiva     = 200,00 + 75,00 = 275,00
```

I 68,00 € non attribuiti al presente a mezzo servizio restano alla cassa: ridurre la quota di uno non aumenta quella degli altri, che continuano a ricevere la stessa quota base.

La somma di spese, cassa effettiva e quote è sempre pari alla base dell'evento: è la stessa quadratura che i rendiconti garantiscono con la riga di raccordo, e l'interfaccia la mostra esplicitamente.

### Casi limite

| Situazione | Comportamento |
| --- | --- |
| Nessun presente registrato | nessuna quota; la simulazione lo dichiara e non propone la liquidazione |
| Netto distribuibile nullo o negativo | nessuna quota; la simulazione segnala che l'evento non copre spese e cassa |
| Quota teorica inferiore al passo | quota pari a zero, mostrata in chiaro: il presente non riceve nulla e il suo importo resta nel residuo |
| Consuntivo assente o inferiore alle spese | quota reale pari a zero: non c'è nulla da liquidare e la simulazione lo dichiara |
| Passo non valorizzato o pari a zero | nessun arrotondamento, quote a due decimali |
| Tutti i presenti con fattore zero | nessuna quota: il netto resta interamente alla cassa |
| Parametri non configurati per la data dell'evento | simulazione bloccata con un messaggio esplicito, nessun calcolo implicito |

Arrotondamento per difetto e fattore non superiore a uno garantiscono insieme che il residuo non sia mai negativo.

### Quota simulata e quota reale

Lo stesso evento produce due quote, con la stessa formula applicata a due basi diverse.

| | Quota simulata | Quota reale |
| --- | --- | --- |
| Base dell'evento | compenso previsto dell'evento | entrate effettive collegate all'evento |
| Spese | costi previsti | uscite effettive collegate all'evento, esclusi i compensi ai presenti |
| A che cosa serve | concordare la ripartizione prima, quando l'incasso non c'è ancora | sapere quanto si può pagare davvero |
| Liquidabile | no | sì |

Si liquida soltanto la quota reale: finché l'incasso non è registrato è pari a zero, e la simulazione lo dichiara invece di proporre un pagamento che la cassa non può sostenere. La quota simulata resta accanto a quella reale per rendere visibile lo scarto fra ciò che era stato concordato e ciò che l'evento ha davvero prodotto.

L'esclusione dei compensi dalle spese del consuntivo non è un dettaglio di comodo. Se le liquidazioni già registrate contassero fra le spese dell'evento, ogni pagamento ridurrebbe la base, e quindi la quota, e quindi ciò che resta da pagare: il calcolo inseguirebbe se stesso e non convergerebbe mai. Le uscite collegate a una riga di liquidazione sono perciò escluse dalle spese; tutte le altre no.

Quando la quota reale scende sotto quanto già liquidato — un incasso stornato, una spesa registrata dopo il pagamento — la differenza è esposta come scostamento. Nessun conguaglio è automatico: la decisione di chiedere la restituzione o di lasciare le cose come stanno è di chi amministra, non del software.

## Parametri datati

Percentuale e passo valgono dalla data in cui sono fissati e restano validi finché non subentra una versione successiva. Una nuova versione può avere decorrenza anche anteriore alla data odierna, così da correggere un periodo già trascorso.

La versione applicabile a un evento è quella con la decorrenza più recente fra quelle non successive alla data dell'evento. Un evento la cui data precede la prima versione non è calcolabile: va creata una versione con decorrenza anteriore.

Il cambio di parametri non riscrive nulla di ciò che è già stato liquidato. Le quote già pagate restano quelle pagate; la simulazione dell'evento mostra il nuovo calcolo e, quando differisce da quanto liquidato, espone la differenza come scostamento, lasciando all'operatore la decisione di integrare con una nuova liquidazione o di lasciare le cose come stanno. Nessun conguaglio è automatico.

## Fattore del presente

Il fattore riduce la quota di un singolo presente in un singolo evento: chi ha suonato metà serata, chi è arrivato alla fine, chi ha già ricevuto un rimborso a parte. Vale soltanto per quell'evento e non si propaga agli altri.

- il fattore è un numero con tre decimali compreso fra `0` e `1`;
- `1` è il valore predefinito e non viene salvato: la quota è quella base;
- `0` esclude il presente dalla ripartizione pur lasciandolo fra i presenti dell'evento;
- valori superiori a `1` sono rifiutati: il fattore non può premiare nessuno oltre la quota base, e così la somma delle quote non supera mai il netto distribuibile;
- il motivo è obbligatorio ed è mostrato ovunque compaia il fattore, perché un importo diverso dagli altri deve restare spiegabile a distanza di mesi;
- la riduzione non viene redistribuita: resta alla cassa insieme al residuo di arrotondamento. L'interfaccia lo dichiara accanto al campo, perché è la domanda che si pone chiunque riduca una quota per la prima volta.

## Ruoli e autorizzazioni

| Operazione | Super Admin | Admin | Tesoriere | Archivista | Utente | Utente esterno |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Consultare la simulazione di un evento | sì | sì | sì | no | no | no |
| Gestire i parametri datati | sì | sì | sì | no | no | no |
| Assegnare o rimuovere un fattore | sì | sì | sì | no | no | no |
| Consultare le quote dovute a un socio | sì | sì | sì | no | no | no |
| Generare la liquidazione | sì | sì | sì | no | no | no |
| Consultare le proprie quote | sì | sì | sì | no | no | no |

Le quote di un socio sono dati economici del tenant: restano visibili soltanto a chi amministra la cassa. La consultazione delle proprie quote da parte del socio è un'evoluzione facoltativa, non inclusa nella prima versione.

## Modello dati

Le tabelle appartengono allo schema del tenant e non contengono `tenant_code`, come il resto del modulo economico. La migration va inclusa in `tenant-master.xml`.

### Parametri: `payout_setting`

- `id BIGINT`;
- `valid_from DATE` obbligatoria e univoca fra le righe non eliminate;
- `association_share_percent NUMERIC(5,2)` obbligatoria, fra `0` e `100`;
- `rounding_step NUMERIC(19,4)` obbligatoria, maggiore o uguale a zero;
- campi di audit ed `entity_version`.

### Fattore: `event_payout_adjustment`

- `id BIGINT`;
- `event_id BIGINT` obbligatorio, foreign key verso `calendar_event`;
- `user_id BIGINT` obbligatorio, foreign key verso `users`;
- `factor NUMERIC(6,3)` obbligatorio, fra `0` e `1`;
- `reason VARCHAR(255)` obbligatoria;
- campi di audit ed `entity_version`;
- indice univoco su `(event_id, user_id)` limitato alle righe non eliminate.

### Liquidazione: `event_payout_settlement`

- `id BIGINT`;
- `movement_id BIGINT` obbligatorio, foreign key verso `financial_movement`;
- `event_id BIGINT` obbligatorio, foreign key verso `calendar_event`;
- `user_id BIGINT` obbligatorio, foreign key verso `users`;
- `amount NUMERIC(19,4)` obbligatorio, maggiore di zero;
- `event_name_snapshot VARCHAR(255)`, per conservare la denominazione mostrata;
- campi di audit ed `entity_version`;
- indici su `(user_id, event_id)` e su `movement_id`.

Le quote non vengono memorizzate: sono sempre ricalcolate da presenze, preventivo o consuntivo, parametri e fattori. Viene memorizzato soltanto ciò che è stato liquidato, perché è un fatto e non un calcolo. L'importo ancora dovuto a un socio per un evento è la quota calcolata meno la somma delle liquidazioni attive collegate.

L'eliminazione logica del movimento di liquidazione elimina anche le righe collegate: le quote tornano dovute, coerentemente con il modulo economico, dove un movimento eliminato non contribuisce più a nulla.

## API REST

Gli endpoint vivono sotto `/api/finance/payouts/**`, già coperti dalla regola di sicurezza del modulo economico e dal flag tenant `FINANCE`.

### Parametri

- `GET /api/finance/payouts/settings`, elenco delle versioni in ordine di decorrenza;
- `POST /api/finance/payouts/settings`, nuova versione; risponde `409` con codice `payout.setting.dateExists` se la decorrenza è già occupata;
- `PUT /api/finance/payouts/settings/{id}`;
- `DELETE /api/finance/payouts/settings/{id}`, cancellazione logica consentita soltanto se esiste un'altra versione applicabile agli eventi interessati.

### Simulazione

- `GET /api/finance/payouts/events/{eventId}`, restituisce la simulazione completa su entrambe le basi: per ciascuna, base dell'evento, spese, quota di cassa, netto, totale delle quote e residuo; per ogni presente, fattore, quota simulata, quota reale, già liquidato e residuo dovuto;
- la richiesta accetta, per la sola anteprima, `sharePercent` e `roundingStep`: valori che non vengono salvati e servono a rispondere alla domanda «e se cambiassi il passo?»;
- risponde `409` con codice `payout.setting.notConfigured` quando nessuna versione è applicabile alla data dell'evento.

### Fattori

- `PUT /api/finance/payouts/events/{eventId}/adjustments/{userId}`, imposta fattore e motivo; risponde `400` con codice `payout.adjustment.reasonRequired` se manca il motivo, `400` con codice `payout.adjustment.factorOutOfRange` se il fattore non è compreso fra zero e uno, e `409` con codice `payout.adjustment.userNotPresent` se l'utente non risulta presente all'evento;
- `DELETE /api/finance/payouts/events/{eventId}/adjustments/{userId}`, riporta il presente alla quota base.

### Quote e liquidazione

- `GET /api/finance/payouts/users/{userId}`, elenco delle quote reali del socio con evento, data, quota, liquidato e residuo, filtrabile per periodo e per stato;
- `POST /api/finance/payouts/settlements`, riceve conto, data, descrizione facoltativa e le righe `{eventId, userId, amount}`; crea un solo movimento di uscita e le righe di liquidazione in un'unica transazione. Risponde `400` con codice `payout.settlement.amountExceedsDue` se una riga supera il residuo dovuto sulla quota reale e `409` con codice `payout.settlement.empty` se non contiene righe.

La liquidazione riguarda un solo socio per volta: righe di utenti diversi nella stessa richiesta sono rifiutate con `payout.settlement.multipleUsers`, perché un movimento ha una sola controparte.

## Integrazione con la gestione economica

Il movimento creato dalla liquidazione è un normale movimento del modulo economico:

- direzione `EXPENSE`, natura `ORDINARY`, conto scelto dall'operatore;
- categoria predefinita `Compensi ai presenti`, nuova categoria di sistema con direzione `EXPENSE`;
- controparte valorizzata con il nome del socio;
- descrizione composta dall'evento quando le righe appartengono a un solo evento, altrimenti dal numero di eventi liquidati;
- `event_id` valorizzato soltanto quando tutte le righe appartengono allo stesso evento.

Ne discende un limite da conoscere: una liquidazione che copre più eventi contribuisce ai totali del tenant ma non al consuntivo del singolo evento, che continua a leggere `financial_movement.event_id`. Il dettaglio per evento resta nelle righe di liquidazione e nella pagina delle quote. Attribuire al singolo evento anche i pagamenti cumulativi richiede la normalizzazione `event_economic_item` già prevista come evoluzione facoltativa dalla [specifica economica](financial-management-spec.md); fino ad allora, chi vuole il consuntivo per evento liquida un evento per volta.

Il movimento resta modificabile ed eliminabile come ogni altro. Può essere escluso dal rendiconto con il flusso ordinario: l'esclusione agisce sulla classificazione, non sulle quote.

## Notifiche

Ogni operazione esplicita genera una notifica interna per Admin, Super Admin e Tesoriere, con i titoli nel formato `Economia: <evento>`:

- `PAYOUT_SETTING_CREATED` e `PAYOUT_SETTING_UPDATED`, che riportano decorrenza, percentuale e passo;
- `PAYOUT_ADJUSTMENT_SET` e `PAYOUT_ADJUSTMENT_REMOVED`, che riportano socio, evento, fattore e motivo;
- `PAYOUT_SETTLED`, che riporta socio, totale e numero di eventi liquidati.

La simulazione non genera notifiche: è una lettura.

## Frontend

### Collocazione

Una nuova scheda `Compensi` nella sezione Economia, con tre viste: **Simulazione** di un evento, **Quote per socio** e **Parametri**. Dalla pagina dell'evento, un pulsante `Ripartisci compenso` apre direttamente la simulazione di quell'evento.

### Simulazione in tempo reale

La simulazione è il cuore della funzionalità: mostra in ogni istante dove finiscono i soldi dell'evento e quanto spetta a ciascuno. Ogni modifica — fattore, passo, percentuale di anteprima — richiama l'endpoint di simulazione con un ritardo di 300 ms e aggiorna la vista con il risultato del backend. Nessun importo viene calcolato nel client: le cifre che l'operatore legge sono quelle che il backend liquiderebbe.

### Rappresentazione grafica

Tre elementi, dal generale al particolare.

**1. Barre di ripartizione, previsto sopra ed effettivo sotto.** Due barre orizzontali impilate, allineate sulla stessa scala, ciascuna divisa in quattro segmenti affiancati: `Spese`, `Quota di cassa`, `Totale quote`, `Residuo`. La prima rappresenta il preventivo, la seconda il consuntivo; la seconda è più corta finché l'incasso non è completo, e quella differenza di lunghezza è esattamente ciò che manca all'appello. Ogni segmento porta etichetta, importo e percentuale in legenda. Le barre rispondono alla domanda «dove vanno i soldi di questo evento» in un colpo d'occhio e, poiché i segmenti sommano esattamente alla rispettiva base, rendono visibile la quadratura invece di chiedere di fidarsi. Sono preferibili a una torta per due motivi: la lunghezza si confronta meglio dell'angolo, e il residuo è spesso una fetta minima che in una torta diventa illeggibile proprio quando serve vederla.

**2. Tabella dei presenti.** Una riga per presente, con `Nome`, `Fattore` modificabile in linea, `Quota simulata`, `Quota reale`, `Già liquidato` e `Residuo`. La quota liquidabile è quella reale ed è la colonna in evidenza; la simulata le sta accanto in tono secondario, come termine di paragone. Il motivo del fattore compare sotto il nome, leggibile senza aprire nulla, con lo stesso criterio adottato per le voci escluse dal rendiconto. Il piè di tabella riporta i due totali, che coincidono con i segmenti omonimi delle barre. L'ordinamento predefinito è alfabetico, non per importo: l'elenco serve a trovare una persona, non a stilare una classifica.

**3. Pannello dei parametri.** Una riga di indicatori compatti sopra le barre: percentuale di cassa, passo di arrotondamento, decorrenza della versione applicata. Il passo è modificabile come anteprima con un selettore a valori discreti (1, 2, 5, 10, 20 €): spostarlo ridisegna barre e tabella all'istante e mostra quanto cresce il residuo. L'anteprima è dichiarata come tale finché non viene salvata in una nuova versione dei parametri.

Gli stati particolari non sono messaggi d'errore ma informazioni di prima classe: evento senza presenti, netto non sufficiente, incasso non ancora registrato e quindi nulla da liquidare, quote azzerate perché inferiori al passo, evento già liquidato con parametri cambiati dopo. Ciascuno sostituisce la barra con un riquadro che spiega la situazione e propone l'azione sensata.

### Quote per socio

Selezionato un socio, l'elenco delle sue quote reali per evento con data, importo, già liquidato e residuo, e un totale in testa. Gli eventi il cui incasso non è ancora registrato compaiono con quota zero e restano visibili: sono crediti attesi, non dimenticanze. Le righe sono selezionabili; su ciascuna l'importo da liquidare è modificabile e proposto pari al residuo. Il pulsante `Genera movimento` apre un dialogo con conto, data e descrizione, mostra il totale che sta per essere registrato e dichiara quanti eventi copre. Dopo il salvataggio le righe liquidate restano visibili con il loro stato.

### Parametri

Elenco delle versioni in ordine di decorrenza, con percentuale, passo, autore e data di creazione. La creazione di una versione con decorrenza anteriore a oggi avvisa esplicitamente quali eventi già liquidati ricadrebbero nel nuovo calcolo, prima di salvare.

## Sicurezza e isolamento

Valgono le regole del modulo economico: autorizzazione esplicita prima della catena generica, tenant ricavato dal token, identificativi risolti nello schema corrente. Il Tesoriere legge dell'evento soltanto i dati necessari alla ripartizione e non acquisisce il generico aggiornamento dell'evento; l'assegnazione di un fattore non passa dall'endpoint operativo dell'evento.

Le operazioni che toccano più dati sono transazionali: la creazione della liquidazione scrive movimento e righe in un'unica transazione e, se una riga è invalida, non lascia il movimento orfano.

## Osservabilità

Log applicativi strutturati per: versione dei parametri creata o modificata, con decorrenza; fattore assegnato o rimosso, con evento, socio e motivo; liquidazione creata, con socio, totale e numero di righe; simulazione rifiutata per parametri non configurati. I log non contengono importi riferibili a persone oltre a quanto necessario alla diagnosi.

Metriche utili: eventi con quote non ancora liquidate, totale dovuto per tenant, residuo di arrotondamento accumulato per esercizio, numero di fattori attivi.

## Strategia di test

### Unit test backend

- formula completa con e senza fattori, confrontata con l'esempio di questa specifica;
- fattore maggiore di uno rifiutato;
- riduzione di un fattore che non altera le quote degli altri presenti e aumenta il residuo;
- quota reale calcolata sul consuntivo, con le uscite di liquidazione escluse dalle spese: registrare un pagamento non cambia la quota reale dell'evento;
- quota reale nulla finché l'incasso non è registrato, e quota simulata comunque esposta;
- scostamento quando la quota reale scende sotto quanto già liquidato;
- arrotondamento per difetto al passo e residuo sempre non negativo;
- quadratura: spese più cassa effettiva più quote uguale base dell'evento;
- quota teorica inferiore al passo che produce quota zero;
- netto nullo o negativo, nessun presente, tutti i presenti con fattore zero;
- selezione della versione dei parametri per data dell'evento, inclusa la decorrenza retroattiva;
- rifiuto del fattore senza motivo, fuori intervallo o su un utente non presente;
- liquidazione che supera il residuo dovuto, liquidazione vuota, liquidazione con più soci;
- liquidazione parziale che lascia il residuo corretto;
- eliminazione del movimento che riporta le quote fra quelle dovute.

### Integration test PostgreSQL

- vincoli e indici univoci delle tre tabelle;
- isolamento per schema tenant;
- atomicità della liquidazione;
- coerenza fra righe di liquidazione e movimento eliminato logicamente.

### Frontend test

- la simulazione richiama il backend a ogni modifica e non calcola importi in proprio;
- ciascuna barra di ripartizione somma alla propria base;
- i totali della tabella coincidono con i segmenti omonimi delle barre;
- il campo del fattore rifiuta i valori superiori a uno;
- stati particolari resi come riquadri informativi;
- l'anteprima del passo non salva nulla.

## Criteri di accettazione

- la quota di ogni presente è riproducibile a mano con la formula di questa specifica;
- nessuna quota ha decimali quando il passo è valorizzato;
- la somma di spese, cassa effettiva e quote è sempre pari alla base dell'evento;
- il residuo di arrotondamento non è mai negativo;
- percentuale e passo applicati a un evento sono quelli vigenti alla sua data, anche dopo una modifica retroattiva;
- un cambio di parametri non altera ciò che è già stato liquidato e rende visibile lo scostamento;
- un fattore senza motivo non è salvabile, e un fattore superiore a uno è rifiutato;
- ridurre il fattore di un presente non cambia la quota degli altri e aumenta il residuo che resta alla cassa;
- la somma delle quote non supera mai il netto distribuibile;
- si liquida soltanto la quota reale, e registrare una liquidazione non la modifica;
- la liquidazione genera un solo movimento di uscita, con il dettaglio per evento consultabile;
- una liquidazione parziale lascia dovuto il residuo;
- l'eliminazione del movimento riporta le quote fra quelle dovute;
- nessun dato attraversa il confine dello schema tenant;
- la simulazione non salva nulla finché non si liquida.

## Decisioni chiuse

1. **Effetto del fattore.** Il fattore moltiplica la quota base del singolo presente e non può superare `1`. Scartata l'ipotesi del peso sulla divisione, in cui ridurre una quota avrebbe aumentato quelle degli altri: qui la riduzione resta alla cassa e nessuna quota dipende dalle correzioni fatte sugli altri. Il limite a uno è ciò che rende impossibile distribuire più del netto.
2. **Preventivo e consuntivo.** Entrambi: il preventivo produce la quota simulata, il consuntivo la quota reale, e si liquida soltanto la seconda. Le uscite già liquidate restano fuori dalle spese del consuntivo, altrimenti il calcolo inseguirebbe se stesso.
3. **Liquidazione cumulativa.** Confermato il movimento unico per socio, con il limite dichiarato: una liquidazione che copre più eventi non è attribuita ai singoli eventi nel consuntivo.
4. **Destinazione del residuo.** Confermata la cassa dell'associazione, insieme a quanto non distribuito per effetto dei fattori.
5. **Visibilità per il socio.** Il socio non vede le proprie quote in questa versione. Resta un'evoluzione possibile, da decidere insieme a che cosa mostrare: il solo importo o anche il calcolo.
