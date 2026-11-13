# Ripartizione dei compensi ai presenti

La funzionalità calcola quanto spetta a ciascun socio che ha presenziato a un evento, a partire dal compenso dell'evento, dalle spese, dagli eventuali pagamenti fissi e dalla quota trattenuta per la cassa dell'associazione, e consente di liquidare le quote generando i movimenti economici corrispondenti.

Si appoggia interamente alle entità già esistenti: le presenze sono quelle registrate sull'evento (`calendar_event_presence`), il compenso e le spese sono il preventivo descritto in [Gestione economica, cassa e conti correnti](financial-management-spec.md), e la liquidazione produce un normale movimento di uscita dello stesso modulo. Le convenzioni di interfaccia seguono il [Taurus Layout Standard](taurus-layout-standard.md), gli errori lo [Standard degli errori applicativi](application-errors-standard.md) le notifiche lo [Stile editoriale delle notifiche interne](notification-editorial-style.md) e l'attivazione per tenant le regole di [Funzionalità configurabili per tenant](tenant-feature-flags-spec.md).

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
- stabilire per un singolo presente un pagamento fisso motivato al posto della quota, sottratto dalla base dell'evento e ammesso anche su un evento senza compenso;
- liquidare a un socio il totale delle quote maturate, o una parte, con un solo movimento di uscita;
- mantenere tracciabilità, isolamento tenant e coerenza con i saldi del modulo economico;
- attivare o disattivare la funzionalità per singolo tenant, indipendentemente dalla gestione economica.

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
| Liquidabilità | la quota reale, cioè quella calcolata sul consuntivo, e il pagamento fisso, che non attende l'incasso |
| Quota per la cassa | percentuale della base dell'evento, sottratta prima della divisione; si riduce al margine, fino a zero, quando la base non copre spese, pagamenti fissi e quota di cassa |
| Arrotondamento | per difetto, a un passo uguale per tutti, applicato alla quota del singolo |
| Destinazione del residuo | la cassa dell'associazione, che incassa anche ciò che l'arrotondamento non distribuisce |
| Parametri | percentuale e passo versionati per data di decorrenza, modificabili anche nel passato |
| Parametri applicabili | la versione con decorrenza più recente non successiva alla data dell'evento |
| Fattore per presente | moltiplicatore fra `0` e `1`, con motivo obbligatorio, valido per un solo evento |
| Effetto del fattore | riduce soltanto la quota di quel presente; le altre non cambiano |
| Destinazione della riduzione | la cassa, insieme al residuo di arrotondamento |
| Pagamento fisso per presente | importo stabilito al posto della quota, con motivo obbligatorio, valido per un solo evento e alternativo al fattore |
| Effetto del pagamento fisso | si sottrae dalla base insieme alle spese, senza cambiare la base su cui si calcola la quota di cassa; il presente non partecipa alla divisione del netto |
| Evento senza compenso | il pagamento fisso è ammesso; ciò che la base non copre è un disavanzo a carico della cassa |
| Simulazione | calcolata dal backend e ricalcolata a ogni modifica, senza salvare nulla |
| Liquidazione | un solo movimento di uscita per operazione, con il dettaglio per evento nelle righe |
| Liquidazione parziale | ammessa: ogni riga può essere liquidata per un importo inferiore al dovuto |
| Ruoli | gli stessi del modulo economico: Super Admin, Admin, Direttivo e Tesoriere |
| Flag tenant | `EVENT_PAYOUTS`, dedicato e spento per impostazione predefinita, effettivo soltanto con `FINANCE` e `CALENDAR` attivi |

Le scelte alternative valutate e scartate, con il motivo, sono raccolte in [Decisioni chiuse](#decisioni-chiuse).

## Terminologia

- **Base dell'evento**: l'importo da cui parte la ripartizione, cioè il compenso dell'evento: quello previsto per la quota simulata, le entrate effettive per la quota reale.
- **Pagamento fisso**: l'importo stabilito per un presente al posto della quota, sottratto dalla base insieme alle spese.
- **Margine**: ciò che resta della base dopo spese e pagamenti fissi, prima della quota di cassa.
- **Quota di cassa**: la parte della base trattenuta dall'associazione, espressa in percentuale e ridotta quando il margine non basta a coprirla.
- **Netto distribuibile**: ciò che resta da dividere fra i presenti a quota dopo spese, pagamenti fissi e quota di cassa.
- **Presenti a quota**: i presenti senza pagamento fisso, fra i quali si divide il netto distribuibile.
- **Quota base**: il netto distribuibile diviso per il numero dei presenti a quota, prima di fattore e arrotondamento.
- **Quota teorica**: la quota base ridotta dal fattore del presente, prima dell'arrotondamento.
- **Quota**: l'importo dovuto al presente, arrotondato per difetto al passo.
- **Quota simulata**: la quota calcolata sul preventivo dell'evento.
- **Quota reale**: la quota calcolata sul consuntivo, l'unica quota liquidabile; il pagamento fisso si liquida a parte.
- **Residuo di arrotondamento**: la differenza fra netto distribuibile e somma delle quote.
- **Disavanzo**: quanto spese e pagamenti fissi superano la base; resta a carico della cassa.
- **Fattore**: il moltiplicatore che pesa la quota di un presente in un singolo evento.
- **Liquidazione**: il movimento di uscita che paga a un socio una o più quote o pagamenti fissi.

## Calcolo

### Formula

```text
pagamenti fissi     = somma dei pagamenti fissi dei presenti
margine             = base evento − spese − pagamenti fissi
quota di cassa      = base evento × percentuale, ridotta al margine quando il margine è inferiore, 0 quando il margine non è positivo
netto distribuibile = margine − quota di cassa, 0 quando il margine non è positivo
quota base          = netto distribuibile / numero dei presenti a quota
quota teorica       = quota base × fattore del presente, 1 quando non è indicato
quota               = ⌊quota teorica / passo⌋ × passo
residuo             = netto distribuibile − somma delle quote
disavanzo           = − margine quando il margine è negativo, altrimenti 0
cassa effettiva     = quota di cassa + residuo − disavanzo
```

La quota di cassa si calcola sempre sull'intera base e si riduce soltanto quando la base non copre spese, pagamenti fissi e quota di cassa insieme. Con `B` base, `S` spese, `P` pagamenti fissi e `Q` quota di cassa, quando `B < B × percentuale + S + P`:

1. `Q = B − S − P` se `B − S − P > 0`;
2. `Q = 0` se `B − S − P ≤ 0`.

L'ordine di precedenza è quindi: spese e pagamenti fissi, poi la quota di cassa, poi le quote dei presenti. La cassa è la prima a rinunciare, e il netto distribuibile non è mai negativo.

Quando nessun presente ha un fattore o un pagamento fisso e la base copre spese e quota di cassa, la quota è la quota base arrotondata: è la formula di partenza. Il fattore può soltanto ridurre, mai aumentare, quindi la somma delle quote non supera mai il netto distribuibile. Soltanto i pagamenti fissi possono portare l'associazione a pagare ai presenti più di quanto l'evento ha prodotto. Quando spese e pagamenti fissi superano la base, la quota di cassa è zero e la differenza è il disavanzo, che resta a carico della cassa ed è sempre mostrato in chiaro.

La somma di spese, pagamenti fissi, cassa effettiva e quote è sempre pari alla base dell'evento, e l'interfaccia la mostra esplicitamente. La cassa effettiva è negativa soltanto quando c'è un disavanzo.

Tutti gli importi sono `BigDecimal` con quattro decimali; la quota di cassa è arrotondata a due decimali con `HALF_UP` prima di essere sottratta, le quote con la regola del passo, mentre il pagamento fisso resta l'importo stabilito e non è arrotondato al passo. Il backend resta autorevole sui calcoli, come per i rendiconti: l'interfaccia non ricalcola in proprio.

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

### Esempio con pagamento fisso

Lo stesso evento, senza fattori, con un presente a cui è stabilito un pagamento fisso di 180,00 €.

```text
pagamenti fissi     = 180,00
margine             = 1.000,00 − 120,00 − 180,00 = 700,00
quota di cassa      = 1.000,00 × 20% = 200,00, coperta dal margine
netto distribuibile = 700,00 − 200,00 = 500,00
quota base          = 500,00 / 4 = 125,00
quote               = 125,00 × 4 = 500,00
residuo             = 500,00 − 500,00 = 0,00
cassa effettiva     = 200,00 + 0,00 = 200,00
```

Il pagamento fisso si sottrae dalla base insieme alle spese, mentre la quota di cassa resta calcolata sull'intera base: il suo costo ricade sui quattro presenti a quota, che dividono un netto più piccolo. A differenza del fattore, quindi, il pagamento fisso cambia la quota degli altri. Quadratura: 120,00 + 180,00 + 200,00 + 500,00 = 1.000,00.

### Esempio di quota di cassa ridotta

Evento da 500,00 €, spese 150,00 €, percentuale di cassa 20%, un presente con pagamento fisso di 300,00 € e altri tre presenti.

```text
pagamenti fissi     = 300,00
margine             = 500,00 − 150,00 − 300,00 = 50,00
quota di cassa      = 500,00 × 20% = 100,00, ridotta al margine: 50,00
netto distribuibile = 50,00 − 50,00 = 0,00
quote               = 0,00 per i tre presenti a quota
cassa effettiva     = 50,00
```

Il margine non basta a coprire l'intera quota di cassa, che scende da 100,00 € a 50,00 €; ai presenti a quota non resta nulla. Quadratura: 150,00 + 300,00 + 50,00 + 0,00 = 500,00.

### Esempio di evento senza compenso

Un evento gratuito, con spese per 40,00 €, un presente con pagamento fisso di 100,00 € e altri tre presenti.

```text
pagamenti fissi     = 100,00
margine             = 0,00 − 40,00 − 100,00 = −140,00
quota di cassa      = 0,00, perché il margine non è positivo
netto distribuibile = 0,00
quote               = 0,00 per i tre presenti a quota
disavanzo           = 140,00
cassa effettiva     = 0,00 + 0,00 − 140,00 = −140,00
```

Il presente con pagamento fisso riceve comunque 100,00 €, liquidabili subito; gli altri non ricevono nulla, e la cassa copre con fondi propri spese e pagamento. Quadratura: 40,00 + 100,00 − 140,00 + 0,00 = 0,00.

### Casi limite

| Situazione | Comportamento |
| --- | --- |
| Nessun presente registrato | nessuna quota; la simulazione lo dichiara e non propone la liquidazione |
| Margine inferiore alla quota di cassa | quota di cassa ridotta al margine e nessuna quota; la simulazione mostra la quota di cassa piena accanto a quella applicata |
| Margine nullo o negativo | quota di cassa pari a zero e nessuna quota; quando è negativo la simulazione espone il disavanzo, cioè quanto spese e pagamenti fissi superano la base |
| Quota teorica inferiore al passo | quota pari a zero, mostrata in chiaro: il presente non riceve nulla e il suo importo resta nel residuo |
| Consuntivo assente o inferiore alle spese | quota reale pari a zero per i presenti a quota, e la simulazione lo dichiara; i pagamenti fissi restano dovuti e liquidabili |
| Evento senza compenso | nessuna quota; i pagamenti fissi sono dovuti per intero e, insieme alle spese, formano il disavanzo |
| Passo non valorizzato o pari a zero | nessun arrotondamento, quote a due decimali |
| Tutti i presenti con fattore zero | nessuna quota: il netto resta interamente alla cassa |
| Tutti i presenti con pagamento fisso | nessuna quota: il netto, se positivo, resta interamente alla cassa |
| Parametri non configurati per la data dell'evento | simulazione bloccata con un messaggio esplicito, nessun calcolo implicito |

Arrotondamento per difetto e fattore non superiore a uno garantiscono insieme che il residuo non sia mai negativo; il netto non lo è mai, perché un margine insufficiente riduce prima la quota di cassa e soltanto dopo produce un disavanzo.

### Quota simulata e quota reale

Lo stesso evento produce due quote, con la stessa formula applicata al preventivo e al consuntivo.

| | Quota simulata | Quota reale |
| --- | --- | --- |
| Base dell'evento | compenso previsto dell'evento | entrate effettive collegate all'evento |
| Pagamenti fissi | quelli stabiliti per l'evento | gli stessi |
| Spese | costi previsti | uscite effettive collegate all'evento, esclusi i compensi ai presenti |
| A che cosa serve | concordare la ripartizione prima, quando l'incasso non c'è ancora | sapere quanto si può pagare davvero |
| Liquidabile | no | sì |

Si liquida soltanto la quota reale: finché l'incasso non è registrato è pari a zero, e la simulazione lo dichiara invece di proporre un pagamento che la cassa non può sostenere. La quota simulata resta accanto a quella reale per rendere visibile lo scarto fra ciò che era stato concordato e ciò che l'evento ha davvero prodotto. Il pagamento fisso fa eccezione: è lo stesso importo su preventivo e consuntivo, non dipende dall'incasso ed è liquidabile appena stabilito.

L'esclusione dei compensi dalle spese del consuntivo non è un dettaglio di comodo. Se le liquidazioni già registrate contassero fra le spese dell'evento, ogni pagamento ridurrebbe la base, e quindi la quota, e quindi ciò che resta da pagare: il calcolo inseguirebbe se stesso e non convergerebbe mai. Le uscite collegate a una riga di liquidazione sono perciò escluse dalle spese, comprese quelle che saldano un pagamento fisso, che nel calcolo conta già per l'importo stabilito; tutte le altre no.

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

## Pagamento fisso del presente

Il pagamento fisso sostituisce la quota di un singolo presente in un singolo evento con un importo stabilito: il professionista ingaggiato per un compenso concordato, il servizio pagato a forfait, la partecipazione a un evento gratuito o di beneficenza. Vale soltanto per quell'evento e non si propaga agli altri.

- l'importo è maggiore di zero, ha al massimo quindici cifre intere e due decimali, come gli altri importi, e non è arrotondato al passo;
- è alternativo al fattore: un presente ha un fattore, un pagamento fisso o nessuno dei due;
- il motivo è obbligatorio, come per il fattore;
- l'importo si sottrae dalla base insieme alle spese, mentre la quota di cassa resta calcolata sull'intera base: il suo costo ricade sui presenti a quota, che dividono un netto più piccolo, e tocca la cassa soltanto quando il margine non basta a coprirne la quota;
- il presente con pagamento fisso non partecipa alla divisione del netto: la quota base si calcola sui soli presenti a quota;
- è ammesso anche su un evento senza compenso, e quando spese e pagamenti fissi superano la base: la quota di cassa scende a zero e la parte non coperta è un disavanzo a carico della cassa, che la simulazione espone accanto al campo;
- è lo stesso importo sulla quota simulata e su quella reale, ed è liquidabile appena stabilito, senza attendere l'incasso.

A differenza del fattore, il pagamento fisso cambia la quota degli altri presenti. Assegnarlo, modificarlo o rimuoverlo su un evento già liquidato non riscrive nulla: la differenza fra le nuove quote e quanto già pagato è esposta come scostamento, senza conguagli automatici.

## Ruoli e autorizzazioni

| Operazione | Super Admin | Admin | Direttivo | Tesoriere | Archivista | Utente | Utente esterno |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Consultare la simulazione di un evento | sì | sì | sì | sì | no | no | no |
| Gestire i parametri datati | sì | sì | sì | sì | no | no | no |
| Assegnare o rimuovere un fattore o un pagamento fisso | sì | sì | sì | sì | no | no | no |
| Consultare le quote dovute a un socio | sì | sì | sì | sì | no | no | no |
| Generare la liquidazione | sì | sì | sì | sì | no | no | no |
| Consultare le proprie quote | sì | sì | sì | sì | no | no | no |

Le quote di un socio sono dati economici del tenant: restano visibili soltanto a chi amministra la cassa. La consultazione delle proprie quote da parte del socio è un'evoluzione facoltativa, non inclusa nella prima versione.

I ruoli non bastano: ogni operazione richiede anche la funzionalità effettiva per il tenant, come descritto nella sezione seguente.

## Attivazione per tenant

La funzionalità è configurabile per tenant con un flag dedicato, secondo le regole di [Funzionalità configurabili per tenant](tenant-feature-flags-spec.md): il super amministratore la attiva o la disattiva dalla pagina di dettaglio del tenant. Un tenant può quindi usare la gestione economica senza la ripartizione dei compensi.

| Aspetto | Valore |
| --- | --- |
| Funzionalità | `EVENT_PAYOUTS`, checkbox **Compensi ai presenti** nel gruppo **Estensioni** |
| Descrizione della checkbox | «Ripartizione del compenso degli eventi fra i presenti, pagamenti fissi e liquidazioni.» |
| Colonna | `public.tenant.event_payouts_enabled BOOLEAN NOT NULL DEFAULT FALSE` |
| Capacità applicativa | sempre disponibile dopo il deploy |
| Dipendenze tenant | `FINANCE` e `CALENDAR` |
| Stato effettivo | `event_payouts_enabled` AND `finance_enabled` AND `calendar_enabled` |
| Campo nelle funzionalità correnti | `eventPayoutsEnabled` in `GET /api/tenant-features/current` |
| Codice d'errore | `error.tenantFeature.eventPayouts.disabled` |

Servono entrambe le dipendenze: le quote nascono dalle presenze e dal preventivo e consuntivo degli eventi, che appartengono al Calendario, e la liquidazione è un movimento di Economia. La migration aggiunge la colonna con `DEFAULT FALSE` e non attiva la funzionalità per nessun tenant esistente: prima del rilascio nessuno la usa, quindi non c'è un comportamento da preservare.

Quando la funzionalità non è effettiva, per il flag spento o per una dipendenza spenta:

- la scheda `Compensi` della pagina Economia, la sua tappa nella guida di pagina e il pulsante `Ripartisci compenso` del dettaglio evento non compaiono; la scheda è incorporata nella pagina condivisa di Economia, quindi la disattivazione la fa sparire senza riportare l'utente alla dashboard;
- gli endpoint `/api/finance/payouts/**` rispondono `403 tenantFeature.eventPayouts.disabled`;
- le notifiche `PAYOUT_*` non vengono prodotte né consegnate, perché `NotificationFeaturePolicy` le associa a `EVENT_PAYOUTS`;
- parametri, fattori, pagamenti fissi e righe di liquidazione restano nello schema del tenant;
- i movimenti generati dalle liquidazioni restano normali movimenti di Economia, visibili e modificabili; la loro eliminazione logica continua a eliminare le righe di liquidazione collegate, perché la regola appartiene al modulo economico e non dipende dal flag;
- la categoria di sistema `Compensi ai presenti`, creata in ogni schema tenant come le altre categorie di sistema, resta utilizzabile per i movimenti registrati a mano.

Alla riattivazione le quote vengono ricalcolate sui dati correnti — presenze, preventivo, consuntivo, parametri, fattori e pagamenti fissi — e confrontate con quanto già liquidato, esponendo gli eventuali scostamenti; le notifiche non prodotte durante la disattivazione non vengono recuperate.

La [specifica dei flag tenant](tenant-feature-flags-spec.md) riporta il flag in elenco, colonne, modelli, risposta delle funzionalità correnti, enum e dipendenze, codici d'errore, checkbox, notifiche e piano di test.

## Modello dati

Le tabelle appartengono allo schema del tenant e non contengono `tenant_code`, come il resto del modulo economico. La migration va inclusa in `tenant-master.xml`.

### Parametri: `payout_setting`

- `id BIGINT`;
- `valid_from DATE` obbligatoria e univoca fra le righe non eliminate;
- `association_share_percent NUMERIC(5,2)` obbligatoria, fra `0` e `100`;
- `rounding_step NUMERIC(19,4)` obbligatoria, maggiore o uguale a zero;
- campi di audit ed `entity_version`.

### Fattore e pagamento fisso: `event_payout_adjustment`

- `id BIGINT`;
- `event_id BIGINT` obbligatorio, foreign key verso `calendar_event`;
- `user_id BIGINT` obbligatorio, foreign key verso `app_user`;
- `factor NUMERIC(6,3)`, fra `0` e `1`, valorizzato per un fattore;
- `fixed_amount NUMERIC(19,4)`, maggiore di zero, valorizzato per un pagamento fisso;
- vincolo di controllo che richiede esattamente uno fra `factor` e `fixed_amount`;
- `reason VARCHAR(255)` obbligatoria;
- campi di audit ed `entity_version`;
- indice univoco su `(event_id, user_id)` limitato alle righe non eliminate.

### Liquidazione: `event_payout_settlement`

- `id BIGINT`;
- `movement_id BIGINT` obbligatorio, foreign key verso `financial_movement`;
- `event_id BIGINT` obbligatorio, foreign key verso `calendar_event`;
- `user_id BIGINT` obbligatorio, foreign key verso `app_user`;
- `amount NUMERIC(19,4)` obbligatorio, maggiore di zero;
- `event_name_snapshot VARCHAR(255)`, per conservare la denominazione mostrata;
- campi di audit ed `entity_version`;
- indici su `(user_id, event_id)` e su `movement_id`.

Le quote non vengono memorizzate: sono sempre ricalcolate da presenze, preventivo o consuntivo, parametri e fattori. Viene memorizzato soltanto ciò che è stato liquidato, perché è un fatto e non un calcolo. L'importo ancora dovuto a un socio per un evento è la quota calcolata, o il pagamento fisso, meno la somma delle liquidazioni attive collegate.

L'eliminazione logica del movimento di liquidazione elimina anche le righe collegate: le quote tornano dovute, coerentemente con il modulo economico, dove un movimento eliminato non contribuisce più a nulla.

## API REST

Gli endpoint vivono sotto `/api/finance/payouts/**`, già coperti dalla regola di sicurezza del modulo economico su `/api/finance/**`. Il flag tenant non si applica per percorso ma per controller: il nuovo controller porta `@RequiresTenantFeature(TenantFeature.EVENT_PAYOUTS)`, che include già `FINANCE` e `CALENDAR` fra le dipendenze, come fa `WEB_PUSH_REMINDERS` con le proprie.

### Parametri

- `GET /api/finance/payouts/settings`, elenco delle versioni in ordine di decorrenza;
- `POST /api/finance/payouts/settings`, nuova versione; risponde `409` con codice `payout.setting.dateExists` se la decorrenza è già occupata;
- `PUT /api/finance/payouts/settings/{id}`, con gli stessi controlli della creazione; risponde `404` con codice `payout.setting.notFound` se la versione non esiste e `409` con codice `payout.setting.inUse` se spostare la decorrenza in avanti lascerebbe senza parametri eventi con presenti;
- `DELETE /api/finance/payouts/settings/{id}`, cancellazione logica consentita soltanto se esiste un'altra versione applicabile agli eventi interessati; altrimenti risponde `409` con codice `payout.setting.inUse`;
- `GET /api/finance/payouts/settings/affected-events?validFrom=…&settingId=…`, gli eventi già liquidati che una versione con quella decorrenza ricalcolerebbe, con nome, data e importo liquidato; `settingId` esclude la versione in modifica. Il frontend lo interroga prima di salvare, per l'avviso sulle decorrenze retroattive.

### Simulazione

- `GET /api/finance/payouts/events/{eventId}`, restituisce la simulazione completa su preventivo e consuntivo: per ciascuno, base dell'evento, spese, pagamenti fissi, margine, quota di cassa piena e applicata, netto, totale delle quote, residuo e disavanzo; per ogni presente, fattore o pagamento fisso, quota simulata, quota reale, già liquidato e residuo dovuto;
- la richiesta accetta, per la sola anteprima, `sharePercent` e `roundingStep`: valori che non vengono salvati e servono a rispondere alla domanda «e se cambiassi il passo?»; la risposta li dichiara con `preview` e riporta accanto i valori salvati; valori fuori intervallo sono rifiutati con `400` e codice `payout.preview.invalid`;
- risponde `409` con codice `payout.setting.notConfigured` quando nessuna versione è applicabile alla data dell'evento.

### Fattori e pagamenti fissi

- `PUT /api/finance/payouts/events/{eventId}/adjustments/{userId}`, imposta il motivo e, in alternativa, il fattore o il pagamento fisso, e risponde `204`; un fattore pari a `1` non viene salvato e rimuove l'eventuale fattore esistente. Risponde `400` con codice `payout.adjustment.reasonRequired` se manca il motivo, `400` con codice `payout.adjustment.valueRequired` se non indica esattamente uno fra fattore e pagamento fisso, `400` con codice `payout.adjustment.factorOutOfRange` se il fattore non è compreso fra zero e uno, `400` con codice `payout.adjustment.fixedAmountNotPositive` se il pagamento fisso non è maggiore di zero, `400` con codice `validation` e il dettaglio in `fieldErrors` se ha più di quindici cifre intere o più di due decimali, come le righe di liquidazione, `404` con codice `finance.event.notFound` o `payout.user.notFound` se evento o utente non esistono, e `409` con codice `payout.adjustment.userNotPresent` se l'utente non risulta presente all'evento;
- `DELETE /api/finance/payouts/events/{eventId}/adjustments/{userId}`, riporta il presente alla quota base, senza fattore né pagamento fisso, e risponde `204` anche quando non c'era nulla da rimuovere.

### Quote e liquidazione

- `GET /api/finance/payouts/users`, riepilogo per socio nel periodo indicato: eventi con presenza, dovuto, liquidato e scostamento;
- `GET /api/finance/payouts/users/{userId}`, elenco delle quote reali e dei pagamenti fissi del socio con evento, data, tipo, importo, liquidato e residuo, filtrabile per periodo (`from`, `to`) e per stato (`DUE`, `SETTLED`);
- `POST /api/finance/payouts/settlements`, riceve conto, data, categoria, descrizione e note facoltative, una chiave di idempotenza `requestKey` e le righe `{eventId, userId, amount}`; crea un solo movimento di uscita e le righe di liquidazione in un'unica transazione e risponde `201`. Una richiesta ripetuta con la stessa chiave restituisce la liquidazione già registrata invece di crearne una seconda. Risponde `400` con codice `payout.settlement.amountExceedsDue` se una riga supera il residuo dovuto sulla quota reale o sul pagamento fisso e `409` con codice `payout.settlement.empty` se non contiene righe.

La liquidazione riguarda un solo socio per volta: righe di utenti diversi nella stessa richiesta sono rifiutate con `payout.settlement.multipleUsers`, perché un movimento ha una sola controparte.

## Integrazione con la gestione economica

Il movimento creato dalla liquidazione è un normale movimento del modulo economico:

- direzione `EXPENSE`, natura `ORDINARY`, conto scelto dall'operatore;
- categoria predefinita `Compensi ai presenti`, nuova categoria di sistema con direzione `EXPENSE`;
- controparte valorizzata con il nome del socio;
- descrizione composta dall'evento quando le righe appartengono a un solo evento, altrimenti dal numero di eventi liquidati;
- `event_id` valorizzato soltanto quando tutte le righe appartengono allo stesso evento.

Ne discende un limite da conoscere: una liquidazione che copre più eventi contribuisce ai totali del tenant ma non al consuntivo del singolo evento, che continua a leggere `financial_movement.event_id`. Il dettaglio per evento resta nelle righe di liquidazione e nella pagina delle quote. Attribuire al singolo evento anche i pagamenti cumulativi richiede la normalizzazione `event_economic_item` già prevista come evoluzione facoltativa dalla [specifica economica](financial-management-spec.md); fino ad allora, chi vuole il consuntivo per evento liquida un evento per volta.

Il movimento resta modificabile ed eliminabile come ogni altro, con un solo vincolo: finché ha righe di liquidazione attive, importo e direzione non cambiano, e la modifica è rifiutata con `409` e codice `finance.movement.payoutSettlement`. Le righe dicono quanto è stato pagato per ciascun evento: un importo diverso le renderebbe false. Per correggere un pagamento si elimina il movimento, che riporta le quote fra quelle dovute, e si genera una nuova liquidazione. Data, conto, categoria, descrizione, note, allegati e riconciliazione restano modificabili.

## Notifiche

Ogni operazione esplicita genera una notifica interna per Super Admin, Admin, Direttivo e Tesoriere, con i titoli nel formato `Economia: <operazione>` delle altre notifiche economiche, per esempio «Economia: compensi liquidati»; il messaggio nomina l'evento e la sua data:

- `PAYOUT_SETTING_CREATED` e `PAYOUT_SETTING_UPDATED`, che riportano decorrenza, percentuale e passo, e `PAYOUT_SETTING_REMOVED`, con gravità di avviso;
- `PAYOUT_ADJUSTMENT_SET` e `PAYOUT_ADJUSTMENT_REMOVED`, che riportano socio, evento, fattore o pagamento fisso e motivo;
- `PAYOUT_SETTLED`, che riporta socio, totale e numero di eventi liquidati.

La simulazione non genera notifiche: è una lettura.

## Frontend

### Collocazione

Una nuova scheda `Compensi` nella sezione Economia, con tre viste: **Simulazione** di un evento, **Quote per socio** e **Parametri**. Dalla pagina dell'evento, un pulsante `Ripartisci compenso` apre direttamente la simulazione di quell'evento. Scheda e pulsante compaiono soltanto quando `eventPayoutsEnabled` è vero nel `TenantFeatureService` del frontend, e senza la funzionalità effettiva il frontend non chiama gli endpoint dei compensi.

La [guida di pagina](guided-tour-spec.md) di Economia presenta la scheda con la tappa `page.finance.payouts`, ancorata all'intestazione della scheda (`finance.tabPayouts`), per Gestori e Tesoriere: dice che cosa vi si fa e ricorda di fissare per prima cosa i parametri. Come la scheda, la tappa richiede `EVENT_PAYOUTS` effettiva.

### Simulazione in tempo reale

La simulazione è il cuore della funzionalità: mostra in ogni istante dove finiscono i soldi dell'evento e quanto spetta a ciascuno. Ogni modifica — fattore, pagamento fisso, passo, percentuale di anteprima — richiama l'endpoint di simulazione con un ritardo di 300 ms e aggiorna la vista con il risultato del backend. Nessun importo viene calcolato nel client: le cifre che l'operatore legge sono quelle che il backend liquiderebbe.

### Rappresentazione grafica

Tre elementi, dal generale al particolare.

**1. Barre di ripartizione, previsto sopra ed effettivo sotto.** Due barre orizzontali impilate, allineate sulla stessa scala, ciascuna divisa in cinque segmenti affiancati: `Spese`, `Pagamenti fissi`, `Quota di cassa`, `Totale quote`, `Residuo`. La prima rappresenta il preventivo, la seconda il consuntivo; la seconda è più corta finché l'incasso non è completo, e quella differenza di lunghezza è esattamente ciò che manca all'appello. Ogni segmento porta etichetta, importo e percentuale in legenda. Le barre rispondono alla domanda «dove vanno i soldi di questo evento» in un colpo d'occhio e, poiché i segmenti sommano esattamente alla rispettiva base, rendono visibile la quadratura invece di chiedere di fidarsi. Quando la quota di cassa è ridotta, il segmento riporta accanto all'importo applicato quello pieno. Quando c'è un disavanzo, spese e pagamenti fissi superano la base: la barra oltrepassa una linea che segna la base, e la parte eccedente è marcata come `Disavanzo a carico della cassa`. Sono preferibili a una torta per due motivi: la lunghezza si confronta meglio dell'angolo, e il residuo è spesso una fetta minima che in una torta diventa illeggibile proprio quando serve vederla.

**2. Tabella dei presenti.** Una riga per presente, con `Nome`, `Fattore o fisso` modificabile in linea, dove si sceglie fra un fattore e un pagamento fisso, `Quota simulata`, `Quota reale`, `Già liquidato` e `Residuo`. La quota liquidabile è quella reale ed è la colonna in evidenza; la simulata le sta accanto in tono secondario, come termine di paragone. Per chi ha un pagamento fisso, le due colonne riportano lo stesso importo, contrassegnato come fisso. Il motivo del fattore o del pagamento fisso compare sotto il nome, leggibile senza aprire nulla. Il piè di tabella riporta i totali delle quote e dei pagamenti fissi, che coincidono con i segmenti omonimi delle barre. L'ordinamento predefinito è alfabetico, non per importo: l'elenco serve a trovare una persona, non a stilare una classifica.

**3. Pannello dei parametri.** Una riga di indicatori compatti sopra le barre: percentuale di cassa, passo di arrotondamento, decorrenza della versione applicata. Il passo è modificabile come anteprima con un selettore a valori discreti (1, 2, 5, 10, 20 €): spostarlo ridisegna barre e tabella all'istante e mostra quanto cresce il residuo. L'anteprima è dichiarata come tale finché non viene salvata in una nuova versione dei parametri.

Gli stati particolari non sono messaggi d'errore ma informazioni di prima classe: evento senza presenti, netto non sufficiente, quota di cassa ridotta perché il margine non la copre, incasso non ancora registrato e quindi nulla da liquidare oltre ai pagamenti fissi, evento senza compenso con pagamenti fissi a carico della cassa, quote azzerate perché inferiori al passo, evento già liquidato con parametri o pagamenti fissi cambiati dopo. Ciascuno sostituisce la barra con un riquadro che spiega la situazione e propone l'azione sensata.

### Quote per socio

Selezionato un socio, l'elenco delle sue quote reali e dei suoi pagamenti fissi per evento con data, importo, già liquidato e residuo, e un totale in testa. Gli eventi il cui incasso non è ancora registrato compaiono con quota zero e restano visibili: sono crediti attesi, non dimenticanze. I pagamenti fissi compaiono con il loro importo anche senza incasso. Le righe sono selezionabili; su ciascuna l'importo da liquidare è modificabile e proposto pari al residuo. Il pulsante `Genera movimento` apre un dialogo con conto, data e descrizione, mostra il totale che sta per essere registrato e dichiara quanti eventi copre. Dopo il salvataggio le righe liquidate restano visibili con il loro stato.

### Parametri

Elenco delle versioni in ordine di decorrenza, con percentuale, passo, autore e data di creazione. L'autore è il nome e cognome del socio che ha creato la versione, come nelle notifiche, e mai l'identificativo di Keycloak registrato nei campi di audit; se quell'identità non corrisponde a un socio del tenant la cella resta vuota. La creazione di una versione con decorrenza anteriore a oggi avvisa esplicitamente quali eventi già liquidati ricadrebbero nel nuovo calcolo, prima di salvare.

## Sicurezza e isolamento

Valgono le regole del modulo economico: autorizzazione esplicita prima della catena generica, tenant ricavato dal token, identificativi risolti nello schema corrente. Dopo il ruolo, l'interceptor dei flag verifica che `EVENT_PAYOUTS` sia effettiva per il tenant della richiesta. Il Tesoriere legge dell'evento soltanto i dati necessari alla ripartizione e non acquisisce il generico aggiornamento dell'evento; l'assegnazione di un fattore o di un pagamento fisso non passa dall'endpoint operativo dell'evento.

Le operazioni che toccano più dati sono transazionali: la creazione della liquidazione scrive movimento e righe in un'unica transazione e, se una riga è invalida, non lascia il movimento orfano.

## Osservabilità

Log applicativi strutturati per: versione dei parametri creata o modificata, con decorrenza; fattore o pagamento fisso assegnato o rimosso, con evento, socio e motivo; liquidazione creata, con socio, totale e numero di righe; simulazione rifiutata per parametri non configurati. I log non contengono importi riferibili a persone oltre a quanto necessario alla diagnosi.

Metriche utili: eventi con quote non ancora liquidate, totale dovuto per tenant, residuo di arrotondamento accumulato per esercizio, disavanzo coperto dalla cassa per esercizio, numero di fattori e di pagamenti fissi attivi.

## Strategia di test

### Unit test backend

- formula completa con e senza fattori, confrontata con l'esempio di questa specifica;
- fattore maggiore di uno rifiutato;
- riduzione di un fattore che non altera le quote degli altri presenti e aumenta il residuo;
- quota reale calcolata sul consuntivo, con le uscite di liquidazione escluse dalle spese: registrare un pagamento non cambia la quota reale dell'evento;
- quota reale nulla finché l'incasso non è registrato, e quota simulata comunque esposta;
- scostamento quando la quota reale scende sotto quanto già liquidato;
- arrotondamento per difetto al passo e residuo sempre non negativo;
- quadratura: spese più pagamenti fissi più cassa effettiva più quote uguale base dell'evento, anche in presenza di disavanzo;
- quota di cassa ridotta al margine quando il margine è inferiore, e a zero quando il margine non è positivo, anche senza pagamenti fissi; netto mai negativo;
- quota teorica inferiore al passo che produce quota zero;
- netto nullo o negativo, nessun presente, tutti i presenti con fattore zero, tutti i presenti con pagamento fisso;
- pagamento fisso sottratto dalla base insieme alle spese, con la quota di cassa calcolata sull'intera base, confrontato con gli esempi di questa specifica;
- presente con pagamento fisso escluso dalla divisione del netto, e pagamento fisso non arrotondato al passo;
- pagamento fisso su un evento senza compenso: importo dovuto per intero, nessuna quota, disavanzo pari a spese più pagamenti fissi;
- pagamento fisso uguale su preventivo e consuntivo, e liquidabile prima dell'incasso;
- pagamento fisso assegnato su un evento già liquidato, che espone lo scostamento delle quote degli altri presenti;
- selezione della versione dei parametri per data dell'evento, inclusa la decorrenza retroattiva;
- rifiuto del fattore senza motivo, fuori intervallo o su un utente non presente;
- rifiuto del pagamento fisso senza motivo o non positivo, e della richiesta con fattore e pagamento fisso insieme o con nessuno dei due;
- rifiuto, come dato non valido, del pagamento fisso con più di quindici cifre intere o più di due decimali;
- liquidazione che supera il residuo dovuto, liquidazione vuota, liquidazione con più soci;
- liquidazione parziale che lascia il residuo corretto;
- eliminazione del movimento che riporta le quote fra quelle dovute;
- stato effettivo di `EVENT_PAYOUTS` falso con il flag spento o con Economia o Calendario spenti;
- notifiche `PAYOUT_*` soppresse quando la funzionalità non è effettiva.

### Integration test PostgreSQL

- vincoli e indici univoci delle tre tabelle, incluso il vincolo che alterna fattore e pagamento fisso;
- isolamento per schema tenant;
- `403 tenantFeature.eventPayouts.disabled` su ogni endpoint dei compensi con la funzionalità non effettiva, con il tenant A spento e il tenant B acceso nella stessa esecuzione;
- migration che aggiunge `event_payouts_enabled` spento per tutti i tenant esistenti;
- dati conservati durante la disattivazione e quote ricalcolate alla riattivazione;
- eliminazione di un movimento di liquidazione a funzionalità spenta, che elimina comunque le righe collegate;
- atomicità della liquidazione;
- coerenza fra righe di liquidazione e movimento eliminato logicamente.

### Frontend test

- la simulazione richiama il backend a ogni modifica e non calcola importi in proprio;
- ciascuna barra di ripartizione somma alla propria base, con l'eventuale disavanzo oltre la linea della base;
- i totali della tabella coincidono con i segmenti omonimi delle barre;
- il campo del fattore rifiuta i valori superiori a uno;
- il campo del pagamento fisso rifiuta i valori non positivi e quelli con più di quindici cifre intere, e un presente non può avere insieme fattore e pagamento fisso;
- la guida di pagina di Economia ha la tappa della scheda `Compensi` per Gestori e Tesoriere soltanto con la funzionalità effettiva;
- stati particolari resi come riquadri informativi;
- l'anteprima del passo non salva nulla;
- scheda `Compensi` e pulsante `Ripartisci compenso` assenti, senza chiamate agli endpoint dei compensi, quando la funzionalità non è effettiva;
- disattivazione mentre la scheda è aperta: la scheda sparisce e l'utente resta nella pagina Economia;
- checkbox **Compensi ai presenti** nel gruppo Estensioni del dettaglio tenant, che richiede Economia e Calendario.

## Criteri di accettazione

- la quota di ogni presente è riproducibile a mano con la formula di questa specifica;
- nessuna quota ha decimali quando il passo è valorizzato;
- la somma di spese, pagamenti fissi, cassa effettiva e quote è sempre pari alla base dell'evento;
- il residuo di arrotondamento non è mai negativo;
- percentuale e passo applicati a un evento sono quelli vigenti alla sua data, anche dopo una modifica retroattiva;
- un cambio di parametri non altera ciò che è già stato liquidato e rende visibile lo scostamento;
- un fattore senza motivo non è salvabile, e un fattore superiore a uno è rifiutato;
- ridurre il fattore di un presente non cambia la quota degli altri e aumenta il residuo che resta alla cassa;
- un pagamento fisso senza motivo o non positivo non è salvabile, e un presente non può avere insieme fattore e pagamento fisso;
- un pagamento fisso si sottrae dalla base insieme alle spese, non cambia la base su cui si calcola la quota di cassa ed esclude il presente dalla divisione del netto;
- quando il margine non copre la quota di cassa, questa si riduce al margine e, se il margine non è positivo, a zero;
- un pagamento fisso è dovuto e liquidabile anche su un evento senza compenso, e ciò che la base non copre è un disavanzo a carico della cassa, mostrato in chiaro;
- la somma delle quote non supera mai il netto distribuibile;
- si liquidano soltanto la quota reale e il pagamento fisso, e registrare una liquidazione non li modifica;
- la liquidazione genera un solo movimento di uscita, con il dettaglio per evento consultabile;
- una liquidazione parziale lascia dovuto il residuo;
- l'eliminazione del movimento riporta le quote fra quelle dovute;
- nessun dato attraversa il confine dello schema tenant;
- la simulazione non salva nulla finché non si liquida;
- la funzionalità è spenta per impostazione predefinita, si attiva per singolo tenant ed è effettiva soltanto con Economia e Calendario attivi;
- disattivarla nasconde scheda e pulsante, blocca gli endpoint con `403` e conserva tutti i dati, che tornano disponibili alla riattivazione.

## Decisioni chiuse

1. **Effetto del fattore.** Il fattore moltiplica la quota base del singolo presente e non può superare `1`. Scartata l'ipotesi del peso sulla divisione, in cui ridurre una quota avrebbe aumentato quelle degli altri: qui la riduzione resta alla cassa e nessuna quota dipende dai fattori assegnati agli altri. Il limite a uno è ciò che rende impossibile distribuire più del netto.
2. **Preventivo e consuntivo.** Entrambi: il preventivo produce la quota simulata, il consuntivo la quota reale, e si liquida soltanto la seconda. Le uscite già liquidate restano fuori dalle spese del consuntivo, altrimenti il calcolo inseguirebbe se stesso.
3. **Liquidazione cumulativa.** Confermato il movimento unico per socio, con il limite dichiarato: una liquidazione che copre più eventi non è attribuita ai singoli eventi nel consuntivo.
4. **Destinazione del residuo.** Confermata la cassa dell'associazione, insieme a quanto non distribuito per effetto dei fattori.
5. **Visibilità per il socio.** Il socio non vede le proprie quote in questa versione. Resta un'evoluzione possibile, da decidere insieme a che cosa mostrare: il solo importo o anche il calcolo.
6. **Pagamento fisso.** Aggiunto il 2026-11-12: un presente può ricevere un importo stabilito al posto della quota, alternativo al fattore. L'importo si sottrae dalla base insieme alle spese, e il presente esce dalla divisione del netto. È ammesso anche su un evento senza compenso: ciò che la base non copre resta a carico della cassa come disavanzo, dichiarato e mai implicito. Diversamente dal fattore, cambia la quota degli altri presenti.
7. **Riduzione della quota di cassa.** Decisa il 2026-11-12: la quota di cassa si calcola sempre sull'intera base, non sulla base al netto dei pagamenti fissi. Quando la base non copre spese, pagamenti fissi e quota di cassa, la cassa rinuncia per prima: la quota si riduce a `B − S − P` se positivo, altrimenti a zero. La regola vale anche senza pagamenti fissi, quando le sole spese erodono la quota di cassa.
8. **Attivazione per tenant.** Decisa il 2026-11-12: la funzionalità ha un flag tenant dedicato, `EVENT_PAYOUTS`, spento per impostazione predefinita e dipendente da `FINANCE` e `CALENDAR`. Sostituisce la scelta iniziale di legarla al solo flag `FINANCE`, con cui ogni tenant con la gestione economica avrebbe ricevuto anche la ripartizione dei compensi.
