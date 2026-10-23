# Esclusione delle voci dal rendiconto

Il documento progetta la possibilità di escludere singole voci economiche dal rendiconto che il tenant presenta, senza ricorrere alla cancellazione logica. Estende la [Gestione economica, cassa e conti correnti](financial-management-spec.md), di cui eredita modello dati, autorizzazioni e convenzioni; gli errori seguono lo [Standard degli errori applicativi](application-errors-standard.md), le notifiche lo [Stile editoriale delle notifiche interne](notification-editorial-style.md) e l'interfaccia il [Taurus Layout Standard](taurus-layout-standard.md).

## Stato del documento

ID catalogo: `report-exclusion`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

## Problema

Un movimento corretto e realmente avvenuto può non essere significativo nel rendiconto che l'associazione presenta: una partita di giro non registrata come trasferimento, un rimborso che compensa una spesa già esposta, una voce che il consiglio preferisce leggere aggregata.

Oggi l'unico modo per togliere una voce da un rendiconto è la cancellazione logica del movimento, che però:

- la toglie anche dai saldi, che smettono di corrispondere alla cassa reale;
- è irreversibile dal punto di vista applicativo;
- non distingue «questo movimento non esiste» da «questo movimento non va esposto».

Serve quindi un meccanismo distinto, che agisca sulla presentazione e non sull'esistenza del movimento.

## Vincoli da rispettare

Qualunque soluzione deve convivere con i criteri di accettazione già approvati per il modulo economico:

1. **Il saldo di ogni conto resta riproducibile dai movimenti.** È la proprietà che rende i saldi confrontabili con la cassa e con l'estratto bancario.
2. **CSV, XLSX, PDF, API e dashboard mostrano gli stessi totali.** Divergenze fra le viste sono bug, non varianti.
3. **Nessuna immutabilità contabile.** Ogni movimento resta correggibile ed eliminabile; l'esclusione non può introdurre uno stato bloccante.
4. **Ogni operazione economica esplicita genera una notifica** ad Admin, Super Admin e Tesoriere.
5. **I PDF escono con l'intestazione del tenant** — logo, denominazione, codice fiscale, partita IVA — e quindi sembrano documenti ufficiali a chi li riceve.

Il quinto vincolo è quello che pesa di più sulla progettazione: un documento che omette voci senza dichiararlo è un problema di fiducia prima che un problema tecnico.

## Scelte di fondo

### A. Su che cosa agisce l'esclusione

| Opzione | Effetto sui saldi | Effetto sul rendiconto | Conseguenza |
| --- | --- | --- | --- |
| Solo presentazione: la riga sparisce, i totali restano | nessuno | i totali non si spiegano con le righe mostrate | il documento non quadra: da escludere |
| Esclusione piena: la voce sparisce da tutto | il saldo diverge dalla cassa reale | coerente | viola il vincolo 1 |
| **Classificazione più riga di raccordo** | nessuno | i totali analitici sono al netto, l'importo escluso compare aggregato | quadra e non mente |

**Proposta: la terza.** La voce esce dal dettaglio e dalla classificazione, ma il suo importo resta nel documento come riga di raccordo dichiarata, così la somma delle righe mostrate continua a spiegare i totali. È lo stesso principio della quadratura già adottato altrove nel modulo.

### B. Dove agisce, risultato per risultato

Il criterio proposto: *ciò che rappresenta una posizione resta completo; ciò che classifica il risultato dell'esercizio rispetta l'esclusione*.

| Risultato | Voci escluse | Motivo |
| --- | --- | --- |
| Saldo del conto e saldo complessivo | incluse | deve corrispondere alla cassa e all'estratto bancario |
| Estratto conto e saldo progressivo | incluse | è il documento di quadratura del conto |
| Aperture e riporto annuale | incluse | l'anno successivo apre con il saldo effettivo |
| Entrate e uscite ordinarie del rendiconto annuale | escluse, esposte nel raccordo | classifica il risultato dell'esercizio |
| Rendiconto per categoria | escluse, esposte nel raccordo | classifica per causale |
| Dashboard: entrate, uscite e risultato del periodo | escluse, con gli importi esclusi esposti a parte | deve mostrare gli stessi totali dell'esportazione |
| Dashboard: saldi e conteggi | incluse | sono posizioni |
| Registro cassa ed elenchi dei movimenti | incluse e contrassegnate | sono elenchi analitici da verificare riga per riga; il saldo progressivo è solo nell'estratto conto |
| Consuntivo e stato economico dell'evento | incluse e contrassegnate | è una posizione: il residuo da incassare o da pagare deve restare vero |

Gli elenchi analitici restano quindi completi e contrassegnano le voci escluse. È l'unico punto in cui il criterio si piega: un elenco con saldo progressivo a cui manchi una riga non sarebbe verificabile, e l'estratto conto perderebbe l'unica cosa per cui esiste, cioè il confronto con l'estratto bancario.

### C. Unità escludibile

**Proposta: il singolo movimento.** Righe di categoria, evento o conto non si escludono direttamente: riflettono l'esclusione dei movimenti che le compongono. Escludere una riga aggregata significherebbe doverne decidere l'effetto su tutte le altre sezioni che la toccano, con incoerenze fra sezioni dello stesso PDF.

### D. Persistenza

| Opzione | Pro | Contro |
| --- | --- | --- |
| **Flag sul movimento** | auditabile, notificabile, riproducibile: lo stesso periodo dà sempre lo stesso documento | una colonna in più sulla tabella dei movimenti e una migration |
| Selezione al momento dell'esportazione | nessun impatto sul modello dati | due esportazioni dello stesso periodo possono differire senza lasciare traccia |

**Proposta: il flag**, perché la riproducibilità di un rendiconto conta più del costo di una colonna.

### E. Rapporto con la cancellazione logica

Sono due cose diverse e vanno tenute distinte anche nei testi dell'interfaccia:

| | Cancellazione logica | Esclusione |
| --- | --- | --- |
| Significato | il movimento non esiste più | il movimento non va esposto nel dettaglio |
| Saldi | non contribuisce | contribuisce |
| Reversibile | no | sì |
| Motivo | non richiesto | obbligatorio |

Il rischio da evitare è che l'esclusione diventi una cancellazione morbida usata per nascondere ciò che è scomodo, senza le conferme e la tracciabilità previste.

## Decisioni prese

Le scelte discusse e fissate, con il motivo per cui l'alternativa è stata scartata.

### 1. Registri con elenco di movimenti

Le voci escluse **restano nel registro cassa e nell'estratto conto, contrassegnate** da una colonna dedicata. Toglierle avrebbe spezzato il saldo progressivo, rendendo una riga inspiegabile a partire dalla precedente e privando l'estratto conto del confronto con l'estratto bancario.

Resta da sanare un disallineamento preesistente: questa specifica descrive il registro cassa con saldo iniziale e progressivo, mentre il rendiconto prodotto è un elenco semplice. Va riportato a una sola definizione nell'intervento.

### 2. Che cosa è escludibile e che cosa viene contato

Sono escludibili **soltanto le scritture ordinarie**. I trasferimenti non lo sono: non compongono la classificazione, quindi escluderne uno avrebbe avuto come unico effetto un contrassegno nei registri. La scelta porta con sé tre semplificazioni: «voce esclusa» ha un solo significato in tutto il modulo, il numero dichiarato in intestazione coincide sempre con le righe contrassegnate e con la riga di raccordo, e cade la regola di propagazione alla coppia di scritture.

### 3. Vista di verifica interna

**Non è prevista**, e con essa cade il parametro che l'avrebbe abilitata sugli endpoint di rendiconto. Dopo la decisione 1 il registro cassa è già il documento completo, esportabile nei tre formati, e in interfaccia restano il filtro sulle voci escluse e l'elenco dei motivi. Un secondo documento con l'intestazione del tenant e totali diversi da quelli presentati sarebbe circolato senza contesto.

### 4. Dashboard

I totali di periodo della dashboard sono al netto delle voci escluse e la pagina lo dichiara con **una card dedicata, visibile soltanto quando il periodo contiene voci escluse**, come la riga di raccordo che compare solo quando serve. Lasciare la dashboard non filtrata avrebbe fatto divergere i suoi totali da quelli del rendiconto annuale dello stesso periodo.

### 5. Consuntivo dell'evento

Le voci escluse **continuano a contare** in incassato, pagato, residui e stato economico, e sono **contrassegnate** nell'elenco dei movimenti dell'evento. Oggi il backend espone questo elenco (`GET /api/finance/events/{id}/movements` e il riepilogo dell'evento), ma l'interfaccia non lo mostra. Escluderle avrebbe prodotto residui fantasma ed eventi perennemente «aperti» nei rendiconti annuali successivi.

### 6. Motivo

**Testo libero obbligatorio, fino a 255 caratteri**, mostrato ovunque compaia la voce esclusa. Un elenco chiuso di causali si progetta bene soltanto dopo aver visto i motivi reali; introdurlo in seguito resta possibile, costruendolo su ciò che è stato davvero scritto.

### 7. Autorizzazione

**Gli stessi ruoli del modulo economico**, senza permessi dedicati. Il controllo non è la restrizione dell'accesso ma la visibilità: ogni esclusione notifica tutti gli amministratori e l'elenco delle voci escluse è davanti a chi esporta.

## Regole

- sono escludibili soltanto le scritture ordinarie: le aperture no, perché senza di loro il saldo iniziale del rendiconto non è più determinabile, e i trasferimenti nemmeno, perché non compongono la classificazione;
- l'esclusione è reversibile; il reintegro azzera motivo, autore e istante;
- l'eliminazione logica di un movimento escluso lo toglie da tutto, raccordo compreso: i due meccanismi non si sommano;
- esclusione e riconciliazione sono indipendenti;
- l'operazione usa il controllo di concorrenza dei movimenti: oggi esiste soltanto `@Version` sull'entità, senza versione inviata dal client, quindi due modifiche concorrenti si sovrascrivono;
- esclusione e reintegro sono operazioni economiche esplicite e generano notifica.

## Riga di raccordo

Ogni rendiconto che applica l'esclusione espone nella propria sezione dei totali: entrate escluse dal dettaglio, uscite escluse dal dettaglio e numero delle voci. Nel rendiconto annuale la quadratura resta verificabile a vista:

```text
saldo finale complessivo = saldo iniziale complessivo
                         + entrate ordinarie - uscite ordinarie
                         + entrate escluse - uscite escluse
```

I trasferimenti restano nella propria riga, perché non sono entrate né uscite del tenant. Un rendiconto senza voci escluse non mostra righe di raccordo ed è identico a quelli prodotti finora.

## Punti di innesto

Dove la funzionalità tocca il codice esistente, utile per stimare l'intervento prima di autorizzarlo.

| Area | Intervento |
| --- | --- |
| Modello dati | quattro colonne su `financial_movement` — flag, motivo, istante, autore — due vincoli di coerenza e un indice parziale, in una migration inclusa in `tenant-master.xml` |
| Calcoli | totali ordinari, riepilogo per categoria, riepilogo annuale e dashboard passano da «tutte le scritture ordinarie» a «ordinarie non escluse», più il calcolo degli importi esclusi |
| Rendiconti | righe di raccordo nelle sezioni dei totali, colonna di contrassegno nei registri, conteggio nell'intestazione, identico nei tre formati |
| API | un endpoint di aggiornamento dedicato sul movimento e un filtro di ricerca |
| Notifiche | due nuovi eventi con i testi nello stile editoriale |
| Interfaccia | dialogo con motivo obbligatorio, contrassegno e motivo sulla riga, filtro dedicato, contrassegno nei movimenti dell'evento (elenco da aggiungere alla pagina evento o alla scheda Eventi), raccordo nel riepilogo dell'esercizio, card in dashboard visibile solo quando serve, avviso prima dell'esportazione |
| Errori | due chiavi nel catalogo con i testi corrispondenti nel frontend |

## Strategia di test

- esclusione che lascia invariati saldo del conto, estratto conto, aperture e consuntivo evento;
- totali annuali e per categoria al netto, con riga di raccordo che riporta la quadratura;
- somma delle righe mostrate uguale ai totali del documento, nei tre formati;
- rifiuto dell'esclusione senza motivo, su un movimento di apertura e su una scrittura di trasferimento;
- consuntivo e stato economico dell'evento invariati quando una sua voce viene esclusa;
- card della dashboard presente soltanto quando il periodo contiene voci escluse;
- voce esclusa poi eliminata logicamente che non compare più nemmeno nel raccordo;
- vincoli verificati su PostgreSQL, non solo nel servizio;
- interfaccia: motivo obbligatorio, reintegro, azione assente su aperture e trasferimenti, filtro che ritrova le voci escluse.

## Criteri di accettazione

- una voce esclusa non compare nei totali annuali, nel riepilogo per categoria e nei totali della dashboard;
- la stessa voce continua a contribuire a saldi, estratto conto, aperture, riporti e consuntivo dell'evento;
- i totali di periodo della dashboard coincidono con quelli del rendiconto annuale dello stesso periodo;
- ogni rendiconto con voci escluse espone la riga di raccordo e ne dichiara il numero nell'intestazione, in CSV, XLSX e PDF;
- la somma delle righe mostrate spiega sempre i totali del documento;
- l'esclusione è reversibile, conserva il motivo ed è ammessa soltanto sulle scritture ordinarie;
- l'esclusione non altera importo, data, conto, categoria o evento del movimento;
- ogni esclusione e ogni reintegro generano una sola notifica per destinatario;
- un rendiconto senza voci escluse è identico a quelli prodotti prima della funzionalità.

## Rischi

- **Documento fuorviante.** Un rendiconto con l'intestazione del tenant che omette voci senza dichiararlo può essere presentato come ufficiale. La riga di raccordo e il conteggio in intestazione sono la mitigazione, e non sono facoltativi.
- **Uso improprio.** L'esclusione può diventare il modo comodo per far sparire ciò che è scomodo. Mitigazioni: motivo obbligatorio, notifica a tutti gli amministratori, elenco delle voci escluse consultabile prima di esportare.
- **Divergenza fra viste.** Se la dashboard applica l'esclusione e una vista no, i numeri non tornano. Mitigazione: un solo punto di calcolo nel backend e test che confrontano le viste.
- **Confusione con la cancellazione.** Mitigazione: testi dell'interfaccia espliciti e azioni separate.
