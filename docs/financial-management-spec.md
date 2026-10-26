# Gestione economica, cassa e conti correnti

## Stato del documento

ID catalogo: `financial-management`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

## Obiettivo

La funzionalità deve consentire di:

- introdurre il ruolo specializzato `ROLE_TREASURER`, mostrato nell'interfaccia come **Tesoriere**;
- gestire una o più casse e uno o più conti correnti per ciascun tenant;
- registrare, correggere ed eliminare liberamente entrate, uscite e trasferimenti;
- distinguere il preventivo economico degli eventi dal consuntivo effettivamente movimentato;
- associare facoltativamente un movimento a un evento;
- gestire allegati facoltativi per ogni movimento;
- produrre saldi, rendiconti ed esportazioni;
- determinare i saldi al 31 dicembre e aprire il successivo esercizio al 1 gennaio riportandoli;
- mantenere tracciabilità, isolamento tenant e coerenza dello storico gestionale.

PostgreSQL è il sistema autorevole. I dati economici sono transazionali e sensibili e si interrogano tramite query relazionali nello schema del tenant.

## Decisioni approvate

| Aspetto | Decisione |
| --- | --- |
| Ruolo tecnico | `ROLE_TREASURER` |
| Etichetta italiana | `Tesoriere` |
| Permessi Admin | Gestione economica completa, senza limitazioni rispetto al Tesoriere |
| Permessi Super Admin | Gestione economica completa |
| Permessi Tesoriere | Gestione economica completa, senza acquisire le altre funzioni amministrative |
| Preventivo eventi | Campi `fee` e costi già presenti negli eventi |
| Consuntivo eventi | Movimenti finanziari effettivamente registrati |
| Conti | Più casse e più conti correnti per tenant |
| Ciclo di vita del conto | Archiviazione reversibile; eliminazione logica ammessa soltanto su un conto archiviato e privo di movimenti attivi |
| Valuta iniziale | EUR, mantenendo un campo valuta ISO 4217 per evoluzioni future |
| Associazione movimento-evento | Facoltativa in generale, automatica quando il movimento nasce dalla pagina dell'evento |
| Stati movimento | Nessun ciclo di vita vincolante; ogni movimento resta modificabile, purché conto e categoria indicati siano attivi |
| Correzione di un movimento | Modifica diretta o eliminazione logica, senza obbligo di storno |
| Saldo iniziale | Movimento tecnico di apertura con `initial_balance = true`, che il riporto annuale non modifica |
| Attivazione | Funzionalità tenant `FINANCE` (vedi [Funzionalità configurabili per tenant](tenant-feature-flags-spec.md)) |
| Categorie | Elenco iniziale predefinito, configurabile da Admin, Tesoriere e Super Admin |
| Allegati | Supportati ma non obbligatori |
| Trasferimenti | Due movimenti collegati e creati atomicamente |
| Rendiconti | Per periodo, conto, categoria ed evento, con esportazione CSV/XLSX e PDF |
| Esercizio | Dal 1 gennaio al 31 dicembre |
| Riporto saldi | Saldo finale al 31/12 riportato come apertura al 01/01 |
| Rettifiche su anni precedenti | Sempre consentite; saldi di apertura successivi ricalcolati automaticamente |
| Notifiche | Per Admin, Super Admin e Tesoriere su ogni operazione economica esplicita, in-app o push secondo le preferenze di notifica; nessuna notifica per letture passive |
| Affidabilità notifiche | Outbox transazionale tenant-scoped, consegna idempotente con retry e deduplicazione per destinatario |

## Terminologia

- **Conto finanziario**: una cassa fisica o un conto corrente bancario.
- **Movimento**: una scrittura che aumenta o diminuisce il saldo di un conto.
- **Preventivo evento**: compenso atteso e costi previsti registrati sull'evento.
- **Consuntivo evento**: entrate e uscite effettive associate all'evento.
- **Riconciliazione**: indicazione facoltativa che un movimento è stato verificato rispetto all'estratto conto o al controllo di cassa; non ne limita la modificabilità.
- **Esercizio**: periodo contabile annuale 01/01-31/12.

## Ruoli e autorizzazioni

### Matrice funzionale

| Operazione | Super Admin | Admin | Tesoriere | Archivista | Utente | Utente esterno |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Visualizzare dashboard economica | sì | sì | sì | no | no | no |
| Consultare conti e saldi | sì | sì | sì | no | no | no |
| Creare e modificare conti | sì | sì | sì | no | no | no |
| Archiviare e riattivare conti | sì | sì | sì | no | no | no |
| Eliminare un conto archiviato e senza movimenti attivi | sì | sì | sì | no | no | no |
| Gestire categorie | sì | sì | sì | no | no | no |
| Creare, modificare ed eliminare movimenti | sì | sì | sì | no | no | no |
| Correggere movimenti di qualsiasi anno | sì | sì | sì | no | no | no |
| Segnare/togliere la riconciliazione | sì | sì | sì | no | no | no |
| Eseguire trasferimenti | sì | sì | sì | no | no | no |
| Gestire allegati | sì | sì | sì | no | no | no |
| Gestire preventivo eventi | sì | sì | sì | no | no | no |
| Consultare consuntivo eventi | sì | sì | sì | no | no | no |
| Gestire e ricalcolare i riporti annuali | sì | sì | sì | no | no | no |
| Esportare rendiconti | sì | sì | sì | no | no | no |
| Modificare dati operativi evento | sì | sì | no | secondo permessi esistenti | no | no |
| Gestire utenti, catalogo e inventario | sì | sì | no | secondo permessi esistenti | no | no |

Admin, Tesoriere e Super Admin hanno pari capacità sul modulo economico. Taurus è uno strumento gestionale di supporto, non un sistema di contabilità formale: qualunque movimento può essere corretto direttamente o eliminato logicamente anche dopo la riconciliazione e anche se appartiene a un anno precedente.

La creazione e la modifica di un movimento richiedono che il conto e la categoria indicati siano attivi. Su un conto o una categoria archiviati `FinanceService.applyMovement` risponde `409` con `finance.account.archived` o `finance.category.archived`. La regola vale anche per i trasferimenti: la creazione richiede entrambi i conti attivi, la modifica il conto indicato. Eliminazione e riconciliazione restano sempre consentite.

### Isolamento del Tesoriere

Il Tesoriere può leggere i dati minimi degli eventi necessari alla gestione economica, ma non deve ricevere accesso al generico aggiornamento dell'evento. Non deve poter modificare:

- nome, descrizione e stato operativo;
- data e ora;
- luogo;
- disponibilità e presenze;
- promemoria;
- utenti o altri cataloghi del tenant.

La modifica di compenso e costi deve quindi passare da un endpoint economico dedicato e non dal generico `PUT /api/calendar-events/{id}`.

## Integrazione Keycloak

### Ruolo client

Creare `ROLE_TREASURER` come client role del client `web_app`.

Il sistema esistente salva i ruoli selezionabili per tenant nell'attributo Keycloak:

```text
{tenantCode}_roles
```

Il custom authenticator consente poi all'utente di scegliere tenant e ruolo; il mapper inserisce nel token i claim:

```text
tenant
role
```

Un utente con più ruoli nello stesso tenant, per esempio Admin e Tesoriere, continuerà a scegliere un solo ruolo per la sessione corrente.

### Realm esistente

Il realm viene importato con strategia `IGNORE_EXISTING`. Modificare soltanto `taurus-realm.json` aggiorna i nuovi ambienti ma non aggiunge il ruolo a un realm già esistente.

Il rilascio deve quindi prevedere entrambe le operazioni:

1. aggiunta di `ROLE_TREASURER` a `taurus-realm.json` per installazioni nuove;
2. provisioning idempotente del client role tramite Keycloak Admin API per installazioni esistenti.

Non si deve cancellare o ricreare il realm per applicare il nuovo ruolo.

### Punti applicativi da aggiornare

- `AuthoritiesConstants`: nuova costante `TREASURER`;
- `RoleEnum`: nuovo valore `ROLE_TREASURER`;
- enum frontend `RoleEnums` e mappa etichette;
- form di creazione e modifica utente;
- menu e route Angular;
- test di estrazione authority dai claim;
- configurazione del client role Keycloak;
- destinatari delle notifiche economiche: `ROLE_ADMIN`, `ROLE_SUPER_ADMIN` e `ROLE_TREASURER`.

Dopo l'assegnazione o la rimozione del ruolo, l'utente deve effettuare un nuovo login oppure ottenere un nuovo ID token che contenga il claim aggiornato.

## Architettura multitenant

Le tabelle economiche appartengono allo schema PostgreSQL del tenant selezionato. Non devono contenere una colonna `tenant_code`, perché l'isolamento è garantito dal multi-tenancy per schema già presente.

La migration economica deve essere inclusa in:

```text
taurus-be/src/main/resources/config/liquibase/tenant-master.xml
```

In questo modo:

- tutti gli schemi tenant attivi vengono migrati all'avvio dal migration runner;
- i nuovi tenant ricevono automaticamente le tabelle;
- le foreign key verso `calendar_event` e le altre entità tenant rimangono nello stesso schema;
- nessuna query economica può attraversare involontariamente i tenant.

Le tabelle pubbliche di identità e registry non devono contenere movimenti o saldi.

## Modello dati

Tutti gli importi monetari devono usare `BigDecimal` in Java e `NUMERIC(19,4)` in PostgreSQL. Il frontend può rappresentarli come `number`, ma il backend rimane autorevole per arrotondamenti, vincoli e calcoli.

Le date contabili sono `DATE`; gli istanti di audit sono `TIMESTAMPTZ`. La visualizzazione usa il fuso del tenant/applicazione, inizialmente `Europe/Rome`.

### Esercizio contabile: `accounting_year`

Campi proposti:

- `id BIGINT`;
- `year INTEGER`, univoco;
- `start_date DATE`, sempre 1 gennaio;
- `end_date DATE`, sempre 31 dicembre;
- `status VARCHAR(16)`: `OPEN` oppure `ROLLED_OVER`, con valore puramente organizzativo;
- `rolled_over_at TIMESTAMPTZ` e `rolled_over_by VARCHAR(255)`;
- `last_recalculated_at TIMESTAMPTZ`;
- campi di audit e `entity_version`.

Vincoli:

- `start_date` e `end_date` devono appartenere allo stesso anno;
- una sola riga per anno;
- la generazione o il ricalcolo dei riporti deve essere atomico e idempotente;
- lo stato annuale non impedisce mai la modifica dei movimenti.

Non esiste una tabella di audit dedicata: generazione e ricalcolo dei riporti aggiornano sull'esercizio `rolled_over_at`, `rolled_over_by`, `last_recalculated_at` e i campi di audit, senza introdurre blocchi operativi.

### Conto finanziario: `financial_account`

Campi proposti:

- `id BIGINT`;
- campi di audit e `entity_version`;
- `name VARCHAR(255)` obbligatorio;
- `description TEXT`;
- `account_type VARCHAR(16)`: `CASH` oppure `BANK`;
- `currency CHAR(3)`, inizialmente `EUR`;
- `iban VARCHAR(34)`, facoltativo e ammesso soltanto per `BANK`;
- `bank_name VARCHAR(255)`, facoltativo;
- `active BOOLEAN`;
- `display_order INTEGER`.

Regole:

- nome univoco tra i conti attivi, senza distinzione tra maiuscole e minuscole;
- un conto a cui è collegato almeno un movimento attivo non si elimina: viene archiviato;
- un conto archiviato rimane visibile nello storico ma non accetta nuovi movimenti ordinari;
- un conto archiviato può essere riattivato: torna `active = true` e accetta di nuovo movimenti;
- la riattivazione è rifiutata se nel frattempo un altro conto attivo ha assunto lo stesso nome;
- un conto archiviato e senza movimenti attivi può essere eliminato, perché non esiste storico consultabile da preservare;
- l'eliminazione passa sempre per l'archiviazione: un conto attivo non è eliminabile finché resta operativo;
- il saldo non è memorizzato come campo modificabile;
- il saldo iniziale è un movimento tecnico di apertura (`OPENING`) con `initial_balance = true`, creato con il conto o dall'onboarding;
- il saldo corrente è calcolato dalle scritture effettive dell'esercizio corrente, inclusa l'apertura.

#### Archiviazione, riattivazione ed eliminazione

Il ciclo di vita di un conto prevede tre transizioni distinte, che non vanno confuse tra loro.

| Transizione | Effetto | Quando è ammessa |
| --- | --- | --- |
| Archiviazione | `active = false`; il conto resta consultabile, compare nei rendiconti storici e non accetta nuovi movimenti ordinari | sempre |
| Riattivazione | `active = true`; il conto torna selezionabile per nuovi movimenti | se nessun altro conto attivo ha lo stesso nome |
| Eliminazione | cancellazione logica (`deleted = true`); il conto scompare da elenchi, filtri e rendiconti | soltanto su un conto già archiviato e senza movimenti attivi |

L'eliminazione conta i soli movimenti **attivi**: le scritture già eliminate logicamente non vincolano il conto, perché non contribuiscono a saldi e rendiconti e restano nel database soltanto per tracciabilità. Un conto svuotato eliminando i suoi movimenti torna quindi eliminabile, ed è eliminabile anche un conto creato indicando un saldo iniziale, dopo che il relativo movimento tecnico di apertura è stato eliminato. In pratica l'eliminazione è riservata ai conti creati per errore o mai entrati in uso. L'archiviazione preventiva è obbligatoria: rende l'eliminazione un secondo passo deliberato su un conto già fuori uso, invece di un'azione immediata su un conto operativo.

I movimenti eliminati continuano a referenziare il conto eliminato: la riga di tracciabilità resta integra nel database, ma il conto a cui punta non è più consultabile dall'interfaccia.

Ognuna delle tre transizioni è un'operazione economica esplicita e genera quindi la relativa notifica interna: `ACCOUNT_ARCHIVED`, `ACCOUNT_REACTIVATED` e `ACCOUNT_REMOVED`.

Come per i movimenti, l'eliminazione è definitiva dal punto di vista applicativo: il conto non è ripristinabile dall'interfaccia e i dati restano nel database esclusivamente per tracciabilità. L'interfaccia deve quindi richiedere una conferma esplicita. Il nome di un conto eliminato torna disponibile per un nuovo conto.

### Categoria: `financial_category`

Campi proposti:

- `id BIGINT`;
- campi di audit e `entity_version`;
- `name VARCHAR(255)` obbligatorio;
- `description TEXT`;
- `direction VARCHAR(16)`: `INCOME`, `EXPENSE` oppure `BOTH`;
- `active BOOLEAN`;
- `system_defined BOOLEAN`;
- `display_order INTEGER`.

Categorie iniziali e relativa direzione:

| Categoria | Direzione | Utilizzo |
| --- | --- | --- |
| Compensi eventi | `INCOME` | Incassi dei compensi associati agli eventi |
| Quote associative | `INCOME` | Quote versate dai soci |
| Donazioni | `INCOME` | Somme ricevute senza controprestazione |
| Rimborsi ricevuti | `INCOME` | Restituzioni da fornitori o recuperi di spese anticipate |
| Rimborsi erogati | `EXPENSE` | Somme restituite a soci, collaboratori o clienti |
| Acquisto materiali | `EXPENSE` | Acquisto di beni e materiali di consumo |
| Trasporti | `EXPENSE` | Viaggi, carburante, noleggi e spedizioni |
| Affitto locali | `EXPENSE` | Affitto di sale, depositi o sedi |
| Manutenzione | `EXPENSE` | Riparazione e manutenzione di beni |
| Utenze | `EXPENSE` | Energia, telefono, Internet e servizi analoghi |
| Spese bancarie | `EXPENSE` | Commissioni e costi dei conti correnti |
| Altro incasso | `INCOME` | Entrate occasionali non classificabili diversamente |
| Altra spesa | `EXPENSE` | Uscite occasionali non classificabili diversamente |

Nessuna categoria iniziale usa `BOTH`. Admin, Tesoriere e Super Admin possono comunque creare categorie bidirezionali quando la stessa causale genera realmente sia entrate sia uscite, per esempio cauzioni o anticipi. Per i rimborsi si mantengono due categorie distinte per rendere più chiari filtri e rendiconti.

Le categorie usate non vengono eliminate fisicamente ma archiviate. Il nome può essere modificato; lo storico delle modifiche rimane nei campi di audit. Il nome è univoco tra le categorie attive, senza distinzione tra maiuscole e minuscole: un duplicato risponde `409` con `finance.category.nameExists`.

### Movimento: `financial_movement`

Campi proposti:

- `id BIGINT`;
- campi di audit e `entity_version`;
- `accounting_year_id BIGINT` obbligatorio;
- `account_id BIGINT` obbligatorio;
- `category_id BIGINT`, facoltativo per aperture e trasferimenti;
- `event_id BIGINT`, facoltativo;
- `event_name_snapshot VARCHAR(255)`, aggiornato quando viene associato o corretto l'evento;
- `direction VARCHAR(16)`: `INCOME` o `EXPENSE`;
- `nature VARCHAR(16)`: `ORDINARY`, `OPENING`, `TRANSFER`;
- `booking_date DATE` obbligatoria;
- `value_date DATE`, facoltativa;
- `amount NUMERIC(19,4)` obbligatorio e strettamente maggiore di zero;
- `currency CHAR(3)` obbligatoria;
- `description TEXT` obbligatoria;
- `counterparty VARCHAR(500)`;
- `document_reference VARCHAR(255)`;
- `notes TEXT`;
- `transfer_group UUID`, facoltativo;
- `reconciled BOOLEAN` con default `FALSE`;
- `reconciled_at TIMESTAMPTZ` e `reconciled_by VARCHAR(255)`;
- `reconciliation_reference VARCHAR(255)`;
- `request_key UUID`, facoltativa e univoca: un `POST` di movimento con una chiave già usata restituisce il movimento esistente; `POST /api/finance/transfers` riceve la chiave ma non la usa, e il frontend non la invia;
- `initial_balance BOOLEAN` con default `FALSE`: vale `TRUE` per il saldo iniziale di un conto, che il riporto annuale non ricalcola.

Vincoli e indici principali:

- `amount > 0`;
- valuta del movimento uguale alla valuta del conto nella prima versione;
- `booking_date` inclusa nell'esercizio associato;
- foreign key verso conto, categoria, esercizio ed evento;
- `event_id` con `ON DELETE RESTRICT`; gli eventi applicativi sono comunque cancellati logicamente;
- indice su `(account_id, booking_date, id)`;
- indice su `(event_id, booking_date)`;
- indice su `(reconciled, booking_date)`;
- indice su `(category_id, booking_date)`;
- indice univoco su `request_key` quando valorizzata;
- indice su `transfer_group`;
- indice univoco parziale `uq_financial_opening` su `(accounting_year_id, account_id)` per le aperture non eliminate.

Il campo `event_name_snapshot` preserva la denominazione mostrata nei rendiconti anche se l'evento viene rinominato o cancellato logicamente in seguito.

### Allegato: `financial_movement_attachment`

Campi proposti:

- `id BIGINT`;
- `movement_id BIGINT` obbligatorio;
- `media_asset_id BIGINT` obbligatorio, foreign key verso il catalogo tenant-scoped `media_asset` definito nella [specifica centralizzata dei media](media-asset-spec.md);
- `description VARCHAR(1000)`;
- `active BOOLEAN`;
- campi di audit.

Nome originale, MIME type, dimensione, digest SHA-256 e chiave relativa dello storage sono proprietà del record `media_asset` e non vengono duplicati nell'allegato finanziario.

Gli allegati sono sempre facoltativi e possono essere più di uno per movimento.

Politica applicata da `FinanceService.addAttachment`:

- formati ammessi: PDF, JPEG e PNG, verificati prima sul tipo MIME dichiarato dal client (altrimenti `415` `finance.attachment.unsupported`) e poi sulla firma dei primi byte del file: se il contenuto non corrisponde al tipo dichiarato l'upload è rifiutato con `415` `finance.attachment.contentMismatch` e nulla entra nello storage del tenant;
- massimo 10 MB per file (`413` `finance.attachment.tooLarge`);
- massimo 20 allegati per movimento (`409` `finance.attachment.limit`);
- digest SHA-256 obbligatorio;
- storage filesystem separato per tenant e gestito tramite `media_asset`;
- nessun contenuto binario nel database;
- download esclusivamente tramite endpoint autenticato che verifica tenant e ruolo.

Gli allegati possono essere aggiunti, sostituiti o rimossi in qualunque momento. La rimozione usa la cancellazione logica del collegamento e le normali informazioni di audit, così un errore può essere corretto senza blocchi operativi.

### Indici e query aggregate

Non è necessario memorizzare il saldo corrente. Il backend calcola:

```text
saldo conto = aperture + entrate effettive - uscite effettive
```

Contribuiscono ai saldi tutti i movimenti attivi e non cancellati logicamente. La riconciliazione è soltanto un'informazione di supporto e non cambia il contributo al saldo.

Quando un movimento viene modificato o eliminato, saldi, riepiloghi evento e riporti annuali interessati vengono ricalcolati. Non viene generata automaticamente una scrittura di storno.

Le aperture contribuiscono al saldo del conto ma non ai ricavi/costi dell'anno. I trasferimenti contribuiscono ai singoli saldi dei conti ma sono esclusi dalle entrate/uscite consolidate del tenant.

## Gestione e correzione dei movimenti

Non è previsto un ciclo di vita contabile vincolante. Dal momento del salvataggio, un movimento attivo contribuisce ai saldi e ai consuntivi e rimane sempre modificabile dagli utenti autorizzati.

Admin, Tesoriere e Super Admin possono in qualunque momento:

- correggere conto, direzione, importo, data, categoria, evento, descrizione e note;
- aggiungere, sostituire o rimuovere allegati;
- segnare o togliere la riconciliazione;
- eliminare logicamente il movimento.

Il soft-delete è definitivo dal punto di vista applicativo: un movimento eliminato non può essere ripristinato e non contribuisce più a saldi e rendiconti. I dati rimangono nel database esclusivamente per tracciabilità. Se l'eliminazione è avvenuta per errore, l'utente deve creare un nuovo movimento; l'interfaccia deve quindi richiedere una conferma esplicita prima di eliminare.

La correzione avviene direttamente sul movimento. Non è obbligatorio e non è previsto come flusso standard creare uno storno o una scrittura opposta.

Restano soltanto le validazioni necessarie al corretto funzionamento del software:

- importo maggiore di zero;
- conto, data e direzione obbligatori;
- valuta coerente con il conto;
- riferimenti appartenenti allo stesso tenant;
- coerenza delle due righe che rappresentano un trasferimento.

Le normali informazioni `edit_by` ed `edit_date` indicano chi ha effettuato l'ultima modifica. `entity_version` è gestito con `@Version` ed esposto nei DTO come `version`. `MovementRequest` accetta la versione letta dal client: se è presente e non coincide con quella corrente la modifica è rifiutata con `409` `finance.movement.versionConflict`, così due correzioni concorrenti non si sovrascrivono. Il campo resta facoltativo, perché un'integrazione che non lo invia conserva il comportamento precedente.

## Trasferimenti tra conti

Un trasferimento non è un ricavo né un costo per il tenant. Deve creare atomicamente:

1. un movimento `EXPENSE`/`TRANSFER` sul conto di origine;
2. un movimento `INCOME`/`TRANSFER` sul conto di destinazione.

Entrambi condividono lo stesso `transfer_group`, importo, valuta e data. Se una delle due scritture fallisce, l'intera transazione viene annullata.

Regole:

- conto di origine e destinazione differenti;
- entrambi attivi;
- stessa valuta nella prima versione;
- importi identici;
- una modifica del trasferimento aggiorna atomicamente entrambe le scritture;
- l'eliminazione del trasferimento elimina logicamente entrambe le scritture;
- il trasferimento non compare nei totali consolidati entrate/uscite, ma compare nei registri dei singoli conti.

Non è imposto il blocco del saldo negativo, perché un conto bancario può essere tecnicamente scoperto. L'interfaccia evidenzia saldi e risultati negativi e, prima di registrare una nuova uscita che porterebbe il conto sotto zero, chiede conferma indicando il saldo che risulterebbe: il movimento si registra comunque, ma spesso l'avviso rivela un conto sbagliato o un importo di troppo.

## Preventivo e consuntivo degli eventi

### Preventivo

Il modello esistente rimane autorevole per il preventivo:

- `calendar_event.fee`: compenso previsto;
- `calendar_event_cost`: singole voci di costo previsto.

Non è richiesta una migrazione distruttiva dei dati esistenti.

### Consuntivo

Il consuntivo deriva da tutti i movimenti attivi e non cancellati logicamente associati all'evento, indipendentemente dall'eventuale riconciliazione.

Per ogni evento il backend espone almeno:

- compenso previsto;
- costi previsti;
- margine previsto;
- totale incassato;
- totale pagato;
- risultato effettivo;
- residuo da incassare;
- residuo da pagare;
- stato economico derivato.

Formule iniziali:

```text
costi previsti = somma(calendar_event_cost.amount)
margine previsto = fee - costi previsti
incassato = somma movimenti effettivi INCOME associati all'evento
pagato = somma movimenti effettivi EXPENSE associati all'evento
risultato effettivo = incassato - pagato
residuo da incassare = fee - incassato
residuo da pagare = costi previsti - pagato
```

I residui possono essere negativi e in tal caso rappresentano un incasso o una spesa superiore al preventivo.

Stati economici derivati approvati:

- `NO_BUDGET`: nessun compenso, nessun costo previsto e nessun movimento;
- `UNPLANNED_MOVEMENTS`: nessun compenso e nessun costo previsto, ma almeno un movimento associato;
- `NO_MOVEMENTS`: preventivo presente ma nessun movimento associato;
- `OVERPAID_OR_OVERRUN`: almeno un residuo negativo;
- `SETTLED`: tutti i residui sono pari a zero secondo la precisione monetaria;
- `PARTIALLY_SETTLED`: almeno un movimento presente e posizione non saldata, senza superamento del preventivo.

La valutazione deve seguire esattamente l'ordine sopra indicato e fermarsi al primo stato applicabile. In particolare, il superamento del preventivo ha priorità rispetto alla posizione parzialmente saldata. Lo stato generico `OPEN` non viene utilizzato perché si sovrapporrebbe a `NO_MOVEMENTS` e `PARTIALLY_SETTLED`.

Questi stati non devono essere salvati sull'evento: vengono calcolati per evitare disallineamenti.

### Evoluzione futura facoltativa

Se diventa necessario associare pagamenti parziali a una specifica voce di costo o gestire più compensi previsti sullo stesso evento, si potrà introdurre `event_economic_item` con tipo `INCOME`/`EXPENSE` e collegare i movimenti alle singole voci. Questa normalizzazione non è necessaria per la prima versione approvata.

## Esercizi e riporto annuale

### Regola temporale

Ogni esercizio va dal 1 gennaio al 31 dicembre. I movimenti con data nell'anno appartengono al relativo esercizio ai fini di filtri e rendiconti.

Il cambio anno ha funzione organizzativa e non rende immutabili i dati:

1. al 31/12 viene calcolato il saldo finale di ogni conto;
2. al 01/01 viene creato il nuovo esercizio;
3. per ogni conto viene creato o aggiornato un movimento tecnico `OPENING` pari al saldo finale dell'anno precedente;
4. entrate e uscite del nuovo anno ripartono da zero nei rendiconti, mentre la disponibilità di cassa e banca viene riportata;
5. i movimenti dell'anno precedente restano sempre modificabili o eliminabili dagli utenti autorizzati.

L'apertura contribuisce al saldo del conto ma non è conteggiata come ricavo o costo del nuovo anno.

### Automazione del cambio anno

`FinanceRolloverScheduler` esegue ogni giorno (cron `application.finance.rollover-cron`, default `0 10 0 * * *`, zona `application.finance.rollover-zone`, default `Europe/Rome`) il riporto dell'anno precedente sui tenant con la funzionalità `FINANCE` attiva (`findFinanceEnabledTenantCodes`). Il riporto (`FinanceService.rolloverForActor`):

- crea l'esercizio successivo se non esiste;
- calcola i saldi al 31/12;
- crea o aggiorna una sola apertura per coppia esercizio/conto, eliminando logicamente l'apertura di un saldo nullo;
- lascia invariato il saldo iniziale di un conto aperto nell'anno successivo (`initial_balance = true`);
- registra `rolled_over_at`, `rolled_over_by` e `last_recalculated_at`.

Su un esercizio già `ROLLED_OVER` il riporto non fa nulla: le aperture restano allineate grazie al ricalcolo eseguito a ogni modifica (vedi sotto). Un errore su un tenant viene registrato nel log senza fermare gli altri.

Lo stato `ROLLED_OVER` indica soltanto che il riporto è stato generato almeno una volta. Non blocca inserimento, modifica o eliminazione dei movimenti.

### Correzioni successive al cambio anno

Non esiste una procedura obbligatoria di riapertura. Se Admin, Tesoriere o Super Admin correggono un movimento di un anno precedente, il backend deve:

1. salvare normalmente la correzione;
2. ricalcolare il saldo finale dell'anno interessato;
3. aggiornare il movimento di apertura dell'anno successivo;
4. propagare il ricalcolo alle aperture degli anni ancora successivi, se presenti;
5. aggiornare dashboard e rendiconti derivati.

Il ricalcolo è immediato, nella stessa transazione della modifica: creazione, modifica ed eliminazione di movimenti e trasferimenti e creazione di un conto con saldo iniziale ricalcolano le aperture di tutti gli esercizi successivi già presenti. Le aperture con `initial_balance = true` non vengono toccate. Se il volume dei dati rendesse costoso il ricalcolo, potrà essere eseguito tramite job affidabile, mostrando temporaneamente che i saldi sono in aggiornamento.

### Verifica annuale facoltativa

Il riepilogo annuale (`GET /api/finance/years/{year}/summary`, scheda Esercizi) mostra:

- saldi per conto;
- movimenti non riconciliati, con numero e importo;
- posizioni evento ancora aperte;
- segnalazioni dell'esercizio;
- data dell'ultimo ricalcolo.

Le segnalazioni sono informative per scelta: la contabilità di un'associazione ha casi irregolari legittimi, e bloccarli costringerebbe a scritture finte. I codici emessi sono `TRANSFER_INCOMPLETE` (un trasferimento con un numero di scritture diverso da due), `TRANSFER_AMOUNT_MISMATCH`, `TRANSFER_DATE_MISMATCH`, `TRANSFER_CURRENCY_MISMATCH`, `TRANSFER_DIRECTION_MISMATCH`, `TRANSFER_SAME_ACCOUNT`, `ACCOUNT_NEGATIVE_CLOSING` (un conto che chiude l'esercizio in negativo) e `MOVEMENTS_WITHOUT_ATTACHMENT` (quanti movimenti ordinari sono senza giustificativo). Compaiono nel riepilogo della scheda Esercizi e nel rendiconto annuale.

La verifica non impedisce modifiche successive e non richiede storni o riaperture.

### Calcolo dei saldi tra esercizi

I report annuali usano soltanto:

- movimento di apertura dell'anno;
- movimenti attivi e non cancellati dello stesso anno.

Non devono sommare tutte le aperture di tutti gli anni, altrimenti il riporto verrebbe contato più volte. Per il saldo storico a una data si usa l'apertura dell'esercizio contenente la data più i movimenti successivi dello stesso esercizio.

Estratto conto e rendiconto annuale calcolano il saldo d'apertura del periodo come saldo al giorno precedente più i saldi iniziali (`initial_balance = true`) dei conti aperti nel periodo. Le righe del periodo non includono le aperture.

I compensi e i costi di eventi non ancora saldati non vengono chiusi artificialmente al 31/12: restano posizioni economiche aperte e possono generare movimenti nell'anno successivo.

## API REST

Tutti gli endpoint economici risiedono sotto `/api/finance/**` e devono essere autorizzati esplicitamente prima della regola generica `/api/**`.

Autorità consentite:

```text
ROLE_SUPER_ADMIN
ROLE_ADMIN
ROLE_TREASURER
```

`FinanceResource` è annotata con `@RequiresTenantFeature(TenantFeature.FINANCE)`: con la funzionalità spenta per il tenant tutte le API economiche rispondono `403`.

### Conti

- `GET /api/finance/accounts`, con `includeArchived=true` per includere i conti archiviati (default `false`);
- `POST /api/finance/accounts`;
- `GET /api/finance/accounts/{id}`;
- `PUT /api/finance/accounts/{id}`;
- `PATCH /api/finance/accounts/{id}/archive`, imposta `active = false`; il conto resta consultabile;
- `PATCH /api/finance/accounts/{id}/restore`, imposta `active = true` e restituisce il conto riattivato; risponde `409` con codice `finance.account.alreadyActive` se il conto è già attivo e `finance.account.nameExists` se il nome è nel frattempo occupato da un altro conto attivo. Anche un `PUT` riattiva il conto salvato, ma il `PATCH` è la via esplicita usata dall'interfaccia;
- `DELETE /api/finance/accounts/{id}`, cancellazione logica consentita soltanto su un conto archiviato e privo di movimenti attivi; risponde `409` con codice `finance.account.notArchived` se il conto è ancora attivo e `finance.account.hasMovements` se esistono movimenti attivi;
- `GET /api/finance/accounts/{id}/balance?date=YYYY-MM-DD`;
- `GET /api/finance/accounts/{id}/statement`.

La rappresentazione del conto espone `movementCount`, il numero di movimenti attivi collegati, esclusi quelli eliminati logicamente. A zero il conto è eliminabile una volta archiviato: l'interfaccia combina questo valore con `active` per decidere se proporre l'azione di eliminazione.

L'ordine di conti e categorie si cambia con `PATCH /api/finance/accounts/order` e `PATCH /api/finance/categories/order`, che ricevono l'elenco completo degli identificativi nell'ordine scelto e restituiscono l'elenco aggiornato. Gli identificativi assenti conservano la posizione che avevano.

### Categorie

- `GET /api/finance/categories`, con `includeArchived=true` per includere le categorie archiviate (default `false`);
- `POST /api/finance/categories`;
- `PUT /api/finance/categories/{id}`;
- `PATCH /api/finance/categories/{id}/archive`, imposta `active = false`; la categoria resta consultabile e viene riattivata salvandola di nuovo con `PUT`.

### Movimenti

- `GET /api/finance/movements`, paginato e filtrabile;
- `POST /api/finance/movements`, crea un movimento attivo;
- `GET /api/finance/movements/{id}`;
- `PUT /api/finance/movements/{id}`, corregge qualsiasi movimento con conto e categoria attivi; su un trasferimento aggiorna entrambe le scritture;
- `DELETE /api/finance/movements/{id}`, cancellazione logica sempre consentita;
- `PATCH /api/finance/movements/{id}/reconciliation`, imposta o rimuove la riconciliazione;
- `POST /api/finance/transfers`.

Creazione e modifica usano lo stesso `MovementRequest`.

Filtri disponibili su `GET /api/finance/movements`:

- intervallo data contabile (`from`, `to`);
- esercizio contabile (`year`), che non coincide con l'intervallo di date: un movimento può essere registrato in un esercizio e pagato in un altro;
- conto (`accountId`);
- categoria (`categoryId`);
- evento (`eventId`);
- direzione (`direction`);
- natura (`nature`), per isolare ordinari, trasferimenti e aperture;
- controparte (`counterparty`), per corrispondenza parziale;
- riconciliato/non riconciliato (`reconciled`);
- testo libero (`query`) su descrizione, controparte e riferimento documento.

L'interfaccia espone tutti questi filtri. `GET /api/finance/reports/cashbook` accetta gli stessi parametri, così l'esportazione contiene esattamente il risultato filtrato e non la sola pagina a video.

### Allegati

- `POST /api/finance/movements/{id}/attachments`, multipart;
- `GET /api/finance/movements/{id}/attachments`;
- `GET /api/finance/attachments/{attachmentId}`;
- `DELETE /api/finance/attachments/{attachmentId}`, cancellazione logica sempre consentita.

L'upload deve fallire atomicamente: un file non valido non deve lasciare metadati orfani o contenuti temporanei non rimossi.

### Eventi economici

- `GET /api/finance/events`, paginato e ordinabile, su tutti gli eventi non eliminati, filtrabile per periodo (`from`, `to`), stato economico (`status`) e nome (`query`). Lo stato economico è calcolato dai movimenti e non è una colonna: periodo, testo e stato si applicano quindi in memoria sull'elenco degli eventi, che per un'associazione resta dell'ordine delle centinaia;
- `GET /api/finance/events/{id}`, riepilogo economico con l'elenco dei movimenti;
- `GET /api/finance/events/{id}/statement`, preventivo, consuntivo e scostamenti dell'evento;
- `PATCH /api/finance/events/{id}/budget`, modifica soltanto compenso e costi previsti;
- `GET /api/finance/events/{id}/movements`;
- `POST /api/finance/events/{id}/movements`, crea un movimento con evento già associato.

Il DTO economico dell'evento deve esporre solo i dati operativi necessari alla lettura e i dati economici. Non deve accettare campi come stato, date, presenze o disponibilità nell'endpoint di aggiornamento budget.

### Esercizi

- `GET /api/finance/years`;
- `GET /api/finance/years/{year}`;
- `POST /api/finance/years/{year}/rollover`, genera il riporto all'anno successivo; su un esercizio già `ROLLED_OVER` il comando resta efficace e riscrive le aperture dell'anno successivo sui saldi attuali, perché dopo una correzione tardiva è l'unico modo di riallineare i saldi. La notifica automatica del riporto scatta soltanto la prima volta;
- `POST /api/finance/years/{year}/recalculate`, ricalcola le aperture degli esercizi successivi e aggiorna `last_recalculated_at`;
- `GET /api/finance/years/{year}/summary`.

### Dashboard e rendiconti

- `GET /api/finance/dashboard?from=&to=`;
- `GET /api/finance/reports/cashbook`;
- `GET /api/finance/reports/account-statement`;
- `GET /api/finance/reports/events`;
- `GET /api/finance/reports/categories`;
- `GET /api/finance/reports/annual`;
- `GET /api/finance/reports/event-statement?eventId=`.

Gli endpoint di report accettano `format=csv`, `format=xlsx` o `format=pdf` quando applicabile.

## Sicurezza backend

La regola Spring Security deve comparire prima del catch-all autenticato:

```java
.requestMatchers("/api/finance/**")
.hasAnyAuthority(
    AuthoritiesConstants.SUPER_ADMIN,
    AuthoritiesConstants.ADMIN,
    AuthoritiesConstants.TREASURER
)
```

La sicurezza Angular nasconde pagine e controlli, ma non sostituisce mai l'autorizzazione backend.

Ogni service economico deve essere eseguito nel tenant ricavato dal token. Gli identificativi ricevuti dal client devono essere risolti nello schema corrente e non accettare riferimenti provenienti da altri tenant.

I download degli allegati verificano tenant e autorità e non espongono il percorso fisico dello storage. L'allegato è cercato per identificativo tra quelli attivi e il movimento proprietario viene ricontrollato: l'allegato di un movimento eliminato logicamente risponde `404` `finance.attachment.notFound`, perché un collegamento rimasto in giro non deve diventare una porta aperta.

Le operazioni che modificano più dati devono essere transazionali:

- creazione, modifica o eliminazione di un trasferimento;
- modifica di un movimento che richiede il ricalcolo dei riporti;
- generazione o aggiornamento delle aperture annuali;
- associazione o rimozione coordinata degli allegati.

## Backend: struttura

### Domain ed enum

Classi in `domain/finance`:

```text
domain/finance/AccountingYear.java
domain/finance/AccountingYearStatus.java
domain/finance/FinancialAccount.java
domain/finance/FinancialAccountType.java
domain/finance/FinancialCategory.java
domain/finance/FinancialCategoryDirection.java
domain/finance/FinancialDirection.java
domain/finance/FinancialMovement.java
domain/finance/FinancialMovementAttachment.java
domain/finance/FinancialMovementNature.java
```

Le entità estendono `TenantAuditedEntity`, che fornisce i campi audit comuni e `@Version`. Il servizio offre un CRUD esplicito per applicare validazioni, ricalcoli e isolamento tenant senza introdurre transizioni di stato vincolanti.

### Repository

Repository distinti per ogni aggregato in `repository/finance` (`AccountingYearRepository`, `FinancialAccountRepository`, `FinancialCategoryRepository`, `FinancialMovementRepository`, `FinancialMovementAttachmentRepository`), con query dedicate per:

- saldi per conto e data;
- somme per evento;
- somme per categoria;
- conteggi e totali per dashboard;
- ricerca movimenti con `Specification`;
- ricalcolo dei saldi e dei riporti annuali;
- verifica trasferimenti e aperture;
- conteggio dei movimenti attivi collegati a un conto, per stabilire se il conto è eliminabile;
- riepilogo dei movimenti non riconciliati per la Home (`FinanceUnreconciledProjection`).

Gli allegati sono recuperati per identificativo (`findByIdAndDeletedFalseAndActiveTrue`) o per movimento.

### Service

Servizi in `service/impl`:

```text
FinanceService               conti, categorie, movimenti, trasferimenti, allegati, eventi, esercizi e dashboard
FinanceReportService         esportazioni CSV, XLSX e PDF
FinanceRolloverScheduler     riporto annuale automatico
FinanceNoticeDataService     snapshot per le notifiche
```

`FinanceService` espone normali operazioni CRUD con validazione. La riconciliazione è un aggiornamento esplicito ma reversibile; non esistono transizioni che rendono il movimento immutabile.

In Home, `FinanceOperationProvider` (`service/dashboard`) mostra ad Admin, Super Admin e Tesoriere l'operazione `FINANCE_MOVEMENTS_UNRECONCILED`: numero e importo dei movimenti ordinari non riconciliati dell'anno corrente, con severità `WARNING` quando il più vecchio supera `application.dashboard.finance-unreconciled-warning-days`, e link a `/finance?section=movements&reconciled=false`.

### DTO

I DTO sono record in `service/dto/finance/FinanceDtos.java`. I DTO di comando sono separati da quelli di lettura:

- comando: `AccountRequest`, `CategoryRequest`, `MovementRequest` (unico per creazione e modifica), `ReconciliationRequest`, `TransferRequest`, `EventBudgetRequest`;
- lettura: `AccountDTO`, `CategoryDTO`, `MovementDTO`, `TransferDTO`, `AttachmentDTO`, `EventSummaryDTO`, `YearDTO`, `YearSummaryDTO`, `AccountStatementDTO`, `DashboardDTO`.

La separazione impedisce overposting e distingue la normale correzione del movimento dall'aggiornamento rapido della riconciliazione.

### Validazione e concorrenza

- Bean Validation per campi obbligatori e formati;
- vincoli PostgreSQL come seconda linea di difesa;
- `@Version` sulle entità, con la versione inviata facoltativamente dal client nelle modifiche dei movimenti;
- ricalcolo degli esercizi senza bloccare la correzione dello storico;
- indice univoco `uq_financial_opening` contro le aperture duplicate;
- `request_key` contro le doppie creazioni di movimenti e di trasferimenti dovute al retry HTTP: sul trasferimento la chiave sta sulla sola uscita, perché identifica il trasferimento e non la singola scrittura, e una richiesta ripetuta restituisce il trasferimento già creato; la chiave è generata dal frontend al salvataggio;
- calcoli monetari esclusivamente con `BigDecimal` e arrotondamento esplicito.

## Frontend

### Ruolo e navigazione

Aggiungere `RoleEnums.TREASURER = 'ROLE_TREASURER'` e l'etichetta `Tesoriere`.

Per il Tesoriere sono visibili:

- Home/Dashboard;
- Economia;
- Profilo.

Non sono visibili calendario, gestione utenti, tenant, catalogo o inventario amministrativo, salvo eventuali permessi derivanti da un diverso ruolo selezionato in una nuova sessione.

Il Tesoriere non vede l'elenco del calendario né la relativa voce di menu. La rotta padre `calendar` lo ammette soltanto per aprire il dettaglio evento `/calendar/:id`, per esempio da una notifica. Il dettaglio carica i dati tramite `GET /api/finance/events/{id}` e non usa gli endpoint amministrativi del calendario.

### Sezione Economia

Voce menu principale `Economia`, con un riepilogo in testa, le azioni `Trasferimento` e `Nuovo movimento` e le schede Movimenti, Conti, Categorie, Eventi ed Esercizi. I rendiconti si esportano dalle schede che li riguardano.

1. **Riepilogo**
   - scelta del periodo, perché la sezione non è vincolata all'anno in corso;
   - disponibilità complessiva dei soli conti attivi;
   - entrate, uscite e risultato del periodo;
   - numero dei movimenti da verificare (non riconciliati);
   - posizioni evento ancora da incassare e da pagare, con l'elenco degli eventi aperti;
   - anno e data dell'ultimo riporto generato, o la sua assenza.

2. **Movimenti**
   - tabella paginata e ordinabile;
   - filtri per periodo, esercizio, conto, direzione, natura, categoria, evento, controparte, riconciliazione e testo libero;
   - creazione, correzione, cancellazione e riconciliazione facoltativa;
   - gestione allegati, con l'indicazione `Senza giustificativo` sui movimenti ordinari privi di allegati;
   - esportazione del registro cassa con i filtri correnti.

3. **Conti**
   - elenco casse e conti correnti;
   - saldo attuale;
   - estratto movimenti;
   - creazione, modifica e archiviazione, con modifica proposta soltanto sui conti attivi;
   - ordine modificabile dalla riga con `Sposta su` e `Sposta giù`;
   - riattivazione dei conti archiviati, con azione dedicata sulla riga del conto;
   - eliminazione dei soli conti archiviati e senza movimenti attivi, con conferma esplicita.

4. **Categorie**
   - elenco configurabile;
   - direzione consentita;
   - archiviazione;
   - esportazione del riepilogo per categoria.

   L'ordine segue `display_order` e il nome e si cambia dalla riga con le azioni `Sposta su` e `Sposta giù`, che salvano l'ordine completo.

5. **Eventi economici**
   - elenco eventi paginato con preventivo, consuntivo, residui e stato economico;
   - filtri per periodo, stato economico e nome;
   - azioni `Registra incasso` e `Registra spesa` sulla riga, che aprono il form del movimento con l'evento e la direzione già impostati;
   - pagina dell'evento con compenso e costi previsti, incassato e pagato, margine previsto, risultato effettivo, scostamenti, voci di costo, elenco dei movimenti collegati e azione per vederli nell'elenco movimenti;
   - modifica del preventivo;
   - esportazione del rendiconto per evento e del rendiconto del singolo evento.

6. **Rendiconti**, esportati dalle schede:
   - registro cassa (Movimenti);
   - estratto conto interno (Conti);
   - rendiconto annuale (Esercizi);
   - risultato per evento (Eventi);
   - riepilogo per categoria (Categorie).

7. **Esercizi**
   - riepilogo annuale, con le segnalazioni dell'esercizio;
   - azione `Aggiorna riporto all’anno corrente`, ricalcolo dei riporti per esercizio e, sulla riga, `Genera riporto` o `Rigenera riporto` con conferma;
   - data dell'ultimo aggiornamento delle aperture.

### Pagina evento

Con la funzionalità `FINANCE` attiva, per Admin, Tesoriere e Super Admin il dettaglio evento mostra la sezione **Consuntivo economico** con:

- incassato e pagato;
- residui da incassare e da pagare;
- azione `Registra movimento`, che apre Economia con l'evento preimpostato.

Nella scheda Eventi di Economia la pagina dell'evento mostra preventivo, consuntivo e scostamento di compenso, costi e risultato, le voci di costo previste, i movimenti collegati con l'indicazione dei giustificativi mancanti e le azioni rapide `Registra incasso`, `Registra spesa` e `Vedi nei movimenti`.

L'Admin continua ad avere tutti i normali controlli dell'evento. Il Tesoriere usa componenti e chiamate economiche dedicate e non riceve controlli operativi editabili.

### Form movimento

Campi minimi:

- conto;
- entrata/uscita;
- importo;
- data contabile;
- data valuta facoltativa;
- categoria;
- descrizione;
- controparte;
- riferimento documento;
- evento facoltativo;
- note;
- allegati facoltativi.

Quando il form viene aperto da un evento, l'evento è preimpostato. Se è stato aperto da `Registra incasso` o `Registra spesa`, il form propone anche la direzione corrispondente e, quando con quella direzione esiste una sola categoria utilizzabile, la preseleziona; con più categorie possibili la scelta resta all'utente.

### Esperienza utente nella gestione

- pulsante `Salva` con normale modifica del movimento;
- sul movimento, azioni `Modifica` ed `Elimina` sempre disponibili agli utenti autorizzati;
- conferma esplicita prima dell'eliminazione, indicando che il movimento non potrà essere ripristinato;
- conferma prima della cancellazione logica;
- controllo facoltativo `Riconciliato` che può essere attivato o rimosso;
- sulla riga del conto l'azione di archiviazione è sostituita da `Riattiva` quando il conto è archiviato;
- su un conto archiviato l'azione `Modifica` non è proposta: i dati di un conto non operativo si aggiornano dopo averlo riattivato;
- conferma prima di registrare un'uscita che porterebbe il conto sotto zero, con il saldo che risulterebbe;
- dopo il salvataggio di un movimento in un esercizio passato, avviso che i saldi di apertura degli esercizi successivi sono stati ricalcolati;
- indicazione `Senza giustificativo` sui movimenti ordinari senza allegati, nell'elenco movimenti e nella pagina dell'evento;
- l'azione `Elimina` compare soltanto su un conto archiviato e senza movimenti attivi, con conferma esplicita che indica l'irreversibilità; su un conto attivo non è proposta, perché l'eliminazione richiede prima l'archiviazione.

## Rendiconti ed esportazioni

### Registro cassa

Intero risultato dei filtri dell'elenco movimenti, con una sezione per conto: saldo iniziale, righe con data, direzione, natura, categoria, evento, descrizione, controparte, riferimento documento, entrate, uscite, valuta e riconciliazione, saldo progressivo riga per riga e saldo finale. Il saldo iniziale è quello reale del conto alla vigilia del periodo, mentre il progressivo segue le sole righe esportate: con un filtro per categoria o direzione la colonna racconta quindi l'effetto di quelle righe, non il conto nel suo insieme. Con il solo filtro per esercizio il periodo non è applicabile e il progressivo parte da zero.

### Estratto conto

Per uno specifico conto e intervallo:

- saldo iniziale del periodo, comprensivo del saldo iniziale di un conto aperto nel periodo;
- movimenti ordinati per data e identificativo, senza aperture;
- entrate, uscite e saldo progressivo;
- saldo finale.

### Rendiconto per evento

Una riga per evento del periodo o con movimenti nel periodo:

- evento e data;
- compenso e costi previsti, margine previsto;
- incassato, pagato e risultato effettivo;
- residui;
- stato economico.

### Rendiconto del singolo evento

Per un evento: compenso e costi previsti, incassato e pagato, margine e risultato con i rispettivi scostamenti, voci di costo previste ed elenco dei movimenti collegati.

### Rendiconto annuale

- saldi iniziali e finali per conto, con i saldi iniziali dei conti aperti nell'anno;
- totale entrate e uscite ordinarie;
- trasferimenti separati;
- correzioni ed eliminazioni già riflesse nei totali correnti;
- totali per categoria;
- eventi economicamente aperti;
- movimenti non riconciliati;
- segnalazioni dell'esercizio;
- data dell'ultimo ricalcolo dei riporti.

### Formati

- CSV per interoperabilità semplice;
- XLSX per analisi e filtri;
- PDF per stampa e archiviazione.

I report devono essere generati dal backend per evitare divergenze nei calcoli. Ogni esportazione deve riportare tenant, periodo, filtri, data di generazione e utente richiedente. Tutti i PDF usano la stessa intestazione del prospetto inventario tramite `TenantPdfHeaderService`: logo, denominazione completa, sede comprensiva di paese, codice fiscale e partita IVA del tenant. Se il logo non è disponibile, il documento viene generato comunque; se l'anagrafica tenant non è risolvibile, viene mostrato come fallback il codice tenant.

## Migrazione dei dati

### Dati evento esistenti

I valori `fee` e `calendar_event_cost` restano invariati e diventano automaticamente il preventivo degli eventi. Non vengono creati movimenti retroattivi, perché non è possibile dedurre se un importo previsto sia stato realmente pagato o incassato.

Gli eventi esistenti con compenso o costi compariranno subito nell'elenco economico con stato `NO_MOVEMENTS` fino alla registrazione del consuntivo.

### Saldi iniziali

Alla creazione di un conto l'utente può indicare un saldo iniziale; il frontend invia anche la data di riferimento (`initialBalanceDate`, default la data odierna), una descrizione propria (`initialBalanceDescription`) e, se vuole, il documento che lo giustifica.

Con un saldo diverso da zero il backend crea un movimento tecnico `OPENING` con `initial_balance = true` e la descrizione scelta dall'utente, o l'etichetta "Saldo iniziale" quando non è indicata, poi ricalcola le aperture degli anni successivi. `AccountDTO` espone `initialBalanceMovementId`: il frontend vi allega il documento appena il conto è salvato, perché l'apertura è un movimento come gli altri e non un campo del conto. Lo stesso contrassegno vale per i saldi d'apertura importati dall'onboarding. Il riporto annuale (`recalculateOpening`) non modifica queste aperture. Il saldo iniziale può essere corretto o eliminato come gli altri movimenti. Non esiste un campo saldo iniziale separato sul conto.

La migrazione `20261022000000_financial_initial_balance.xml` aggiunge la colonna e la valorizza per le aperture esistenti con descrizione esattamente "Saldo iniziale".

### Rollback

Un rollback applicativo deve preservare tabelle, movimenti e allegati. Non devono essere eseguiti drop automatici delle strutture economiche. Un rollback distruttivo richiede esportazione e conservazione esplicita dei dati.

## Notifiche economiche

### Destinatari e canale

Ogni operazione economica esplicita genera una notifica interna destinata agli utenti attivi con uno dei ruoli:

- `ROLE_ADMIN`;
- `ROLE_SUPER_ADMIN`;
- `ROLE_TREASURER`.

Il destinatario non viene escluso quando coincide con l'autore dell'operazione. Un utente che possiede più ruoli riceve una sola notifica. Le notifiche economiche (`publishFinance`, sorgente `FINANCE`) seguono le preferenze di notifica: `NotificationPreferenceResolver` decide per ciascun destinatario se creare la notifica in-app e se inviarla in push, e `NotificationDispatcher` applica la decisione. Non generano e-mail.

Sono notificate creazione, modifica, riattivazione e archiviazione di conti e categorie, oltre all'eliminazione dei conti, che le categorie non prevedono; registrazione, modifica, rimozione e riconciliazione dei movimenti; registrazione, modifica e rimozione dei trasferimenti; aggiunta, download e rimozione degli allegati; aggiornamento del preventivo evento tramite `PATCH /api/finance/events/{id}/budget` (`EVENT_BUDGET_UPDATED`; la modifica di compenso e costi dal generico `PUT` dell'evento non la genera); riporto e ricalcolo annuale; esportazione dei rendiconti.

Le consultazioni passive, i calcoli automatici eseguiti come conseguenza di un'altra operazione e le richieste già elaborate tramite chiave idempotente non generano notifiche aggiuntive. Un trasferimento produce un solo evento informativo, non uno per ciascuna scrittura collegata.

### Composizione editoriale

Si applica integralmente `docs/notification-editorial-style.md`:

- `NoticesAspect` è l'unico punto che definisce titolo, descrizione e ruoli destinatari;
- i service economici eseguono esclusivamente la logica di dominio;
- `FinanceNoticeDataService` fornisce all'aspect snapshot di supporto privi di testo editoriale;
- `NotificationOutboxPublisher` persiste nell'outbox la notifica già composta;
- `NotificationDispatcher` risolve gli utenti e crea le notifiche applicative tramite `NoticesService`.

I titoli seguono il formato `Economia: <evento>`. Le rimozioni usano il verbo `rimosso`; le operazioni automatiche iniziano dall'entità interessata e non dalla formula `Il sistema ha…`. Se il nome dell'autore non è disponibile si usa una formulazione leggibile e impersonale, senza mostrare UUID o identificativi tecnici.

### Outbox e consegna

Le tabelle tenant `notification_outbox` e `notification_outbox_audience` conservano evento, aggregato, operazione, contenuto già composto, severità, percorso di navigazione, autore e pubblico. La scrittura dell'evento partecipa alla stessa transazione dell'operazione economica: un rollback del dominio annulla anche la notifica.

Il dispatcher elabora periodicamente gli eventi `PENDING`, applica un lock pessimista e crea una riga `notices` per ciascun destinatario. La coppia `source_event_key`/`user_id` è univoca, quindi retry e istanze concorrenti non producono duplicati. In caso di errore l'evento resta pendente e viene riprovato con attesa esponenziale fino a 60 minuti. Gli eventi consegnati dell'outbox vengono eliminati dopo 30 giorni; le normali notifiche dell'utente seguono invece la retention applicativa esistente.

### Esperienza frontend

Il conteggio delle notifiche non lette viene aggiornato all'apertura del layout, ogni 30 secondi e quando la finestra torna in primo piano. Il click su una notifica economica la segna come letta e, soltanto dopo il completamento della richiesta, apre la sezione, il movimento o l'evento indicato da `targetPath`.

## Audit, conservazione e cancellazioni

- tutti i movimenti possono essere modificati;
- tutti i movimenti possono essere cancellati logicamente, senza possibilità di ripristino applicativo;
- conti e categorie con scritture attive collegate: archiviati, non cancellati;
- un conto archiviato e senza movimenti attivi può essere cancellato logicamente, senza possibilità di ripristino applicativo;
- l'archiviazione di un conto è reversibile: la riattivazione lo riporta tra i conti selezionabili;
- tutti gli allegati possono essere aggiunti, sostituiti o rimossi logicamente;
- ogni modifica conserva almeno ultimo autore, data e versione;
- non sono richiesti storni, motivazioni di rettifica o procedure di riapertura;
- i riferimenti all'identità autenticata sono conservati secondo la policy applicativa e di retention del tenant;
- l'eliminazione GDPR di un utente non deve alterare involontariamente importi o saldi; gli identificativi personali possono essere pseudonimizzati senza rimuovere il movimento.

Taurus è uno strumento gestionale di supporto e non sostituisce la contabilità fiscale. La cancellazione logica è raccomandata per consentire recupero e diagnosi, ma non introduce immutabilità contabile.

## Osservabilità

`FinanceMetrics` registra le metriche del modulo con tag a cardinalità nota, senza tenant, identificativi, IBAN o descrizioni:

- `taurus.finance.operations` con `entity` (`ACCOUNT`, `CATEGORY`, `MOVEMENT`, `TRANSFER`, `ATTACHMENT`, `YEAR`) e `operation` (creazione, modifica, eliminazione, archiviazione, riattivazione, riconciliazione, riordino, replay idempotente, riporto, ricalcolo, download);
- `taurus.finance.failures` con `reason`: conflitto di versione, contenuto non corrispondente, tipo non ammesso, allegato troppo grande, troppi allegati, upload fallito, esercizio chiuso, trasferimento sullo stesso conto, valuta incoerente, accesso negato, ricalcolo fallito;
- `taurus.finance.attachment.bytes`, lo spazio occupato dagli allegati caricati;
- `taurus.finance.year.anomalies` per codice di segnalazione;
- `taurus.finance.report.duration` con `report` e `format`.

I log del modulo sono su `FinanceService`: a livello informativo la creazione e la modifica di un conto, il trasferimento registrato e il riporto generato o rigenerato; a livello di avviso l'allegato rifiutato per contenuto non corrispondente e l'accesso negato all'allegato di un movimento eliminato; a livello di debug il movimento registrato con il suo esercizio. `FinanceRolloverScheduler` registra l'errore del riporto automatico per tenant.

I log non devono contenere file allegati, token, IBAN completi o note potenzialmente sensibili. Per l'IBAN usare una versione mascherata.

## Strategia di test

Le verifiche elencate qui sono coperte da: `FinanceServiceTest` e `FinanceServiceGuardsTest` (unit backend, comprese le difese su allegati, concorrenza, idempotenza, segnalazioni, riporto rigenerabile, filtri della scheda eventi e ordinamento), `FinanceReportServiceTest`, `FinanceRolloverSchedulerTest`, `FinanceIT` (PostgreSQL: isolamento fra tenant, retry di un trasferimento, concorrenza ottimistica, filtri tradotti in SQL, riporto rigenerato dopo una correzione tardiva, rifiuto di un'identità senza tenant) e, sul frontend, `finance.service.spec.ts`, `finance.component.spec.ts`, `finance-account-dialog.component.spec.ts` e `finance-movement-dialog.component.spec.ts`.

### Unit test backend

- modifica libera dei movimenti;
- esclusione dei movimenti cancellati logicamente dai saldi;
- ricalcolo dopo correzione o eliminazione;
- esclusione trasferimenti dai totali consolidati;
- esclusione aperture da ricavi/costi annuali;
- formule preventivo/consuntivo evento;
- assegnazione univoca dello stato economico secondo la precedenza approvata;
- validazione della direzione consentita dalla categoria;
- validazione valuta e importi;
- riconciliazione attivabile e rimovibile senza bloccare la modifica;
- aggiornamento diretto senza generazione di storni;
- riattivazione di un conto archiviato e rifiuto della riattivazione in caso di nome già occupato;
- eliminazione di un conto archiviato i cui movimenti sono tutti eliminati logicamente, rifiuto dell'eliminazione in presenza di movimenti attivi e rifiuto dell'eliminazione di un conto ancora attivo;
- pubblicazione delle notifiche `ACCOUNT_REACTIVATED` e `ACCOUNT_REMOVED` a valle delle due operazioni.

### Integration test PostgreSQL

- vincoli e foreign key;
- isolamento per schema tenant;
- trasferimento atomico;
- ricalcolo atomico dei riporti;
- idempotenza `request_key`;
- apertura annuale non duplicata;
- ricalcolo apertura dopo modifica di un anno precedente;
- query saldo progressivo;
- gestione concorrente di due modifiche.

### Security test

- Admin, Tesoriere e Super Admin accedono a tutte le API economiche;
- Archivista, Utente e Utente esterno ricevono `403`;
- il Tesoriere non può modificare endpoint operativi dell'evento;
- nessun ruolo può accedere a dati di un altro tenant;
- un percorso storage non può essere usato per leggere direttamente un allegato.

### Frontend test

- route guard e menu per ogni ruolo;
- Admin e Tesoriere vedono gli stessi controlli economici;
- il Tesoriere non vede i controlli operativi amministrativi;
- form di modifica disponibile per qualsiasi movimento;
- conferma di eliminazione senza azione di ripristino;
- caricamento allegati facoltativo;
- riepiloghi evento coerenti con le risposte API;
- warning saldo negativo e ricalcolo degli anni successivi.

### Test di riporto annuale

- calcolo saldo finale al 31/12;
- apertura al 01/01;
- riporto esatto di saldi positivi, nulli e negativi;
- nessun doppio conteggio delle aperture;
- segnalazione senza blocco dei trasferimenti incoerenti;
- modifica libera dei movimenti dell'anno precedente;
- ricalcolo automatico del saldo iniziale successivo;
- permanenza delle posizioni evento non saldate.

## Criteri di accettazione

- `ROLE_TREASURER` è assegnabile per tenant e selezionabile al login;
- Admin, Tesoriere e Super Admin hanno accesso economico completo;
- il Tesoriere non acquisisce gestione utenti, catalogo, inventario o modifica operativa degli eventi;
- nessuna API economica è accessibile agli altri ruoli;
- ogni conto mostra un saldo riproducibile dai movimenti;
- il saldo iniziale di un conto non viene modificato dal riporto annuale e rientra nel saldo d'apertura di estratto conto e rendiconto annuale;
- ogni movimento attivo influenza immediatamente i saldi;
- ogni movimento può essere modificato o cancellato logicamente;
- un movimento cancellato logicamente non può essere ripristinato dall'applicazione e non partecipa ai calcoli;
- una correzione aggiorna direttamente il movimento senza richiedere uno storno;
- un trasferimento genera sempre due scritture coerenti oppure nessuna;
- gli allegati sono facoltativi, protetti per tenant e verificati server-side su tipo dichiarato, dimensione e numero;
- preventivo e consuntivo evento rimangono separati;
- ogni evento espone un solo stato economico, calcolato secondo la precedenza approvata;
- un movimento creato dall'evento conserva automaticamente il collegamento;
- gli eventi esistenti con compensi/costi compaiono senza migrazione distruttiva;
- i report non contano aperture come ricavi né trasferimenti come entrate consolidate;
- il saldo viene determinato al 31/12 e il successivo esercizio apre al 01/01 con saldo riportato;
- gli eventi non saldati attraversano il cambio anno senza perdere il residuo;
- una modifica storica ricalcola automaticamente tutti i riporti successivi interessati;
- nessun dato economico attraversa il confine dello schema tenant;
- CSV/XLSX e PDF restituiscono gli stessi totali delle API e della dashboard.
- ogni operazione economica esplicita genera una sola notifica per ciascun Admin, Super Admin e Tesoriere, in-app o push secondo le sue preferenze;
- letture passive e retry idempotenti non generano notifiche duplicate;
- un errore temporaneo di consegna non perde l'evento e attiva il retry;
- titoli e descrizioni rispettano `docs/notification-editorial-style.md`;
- il click su una notifica la segna come letta e apre la destinazione economica corretta.

## File e aree

### Backend modificati

```text
taurus-be/src/main/java/com/fundaro/zodiac/taurus/security/AuthoritiesConstants.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/domain/enumeration/RoleEnum.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/config/SecurityConfiguration.java
taurus-be/src/main/docker/realm-config/taurus-realm.json
taurus-be/src/main/resources/config/liquibase/tenant-master.xml
```

### Backend del modulo

```text
taurus-be/src/main/java/com/fundaro/zodiac/taurus/domain/finance/*.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/repository/finance/*.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/impl/Finance*.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/dto/finance/FinanceDtos.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/dashboard/FinanceOperationProvider.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/web/rest/FinanceResource.java
taurus-be/src/main/resources/config/liquibase/changelog/20260901000002_add_finance.xml
taurus-be/src/main/resources/config/liquibase/changelog/20260901000003_finance_notifications.xml
taurus-be/src/main/resources/config/liquibase/changelog/20261022000000_financial_initial_balance.xml
```

### Frontend modificati

```text
taurus-fe/src/app/constants/role.enums.ts
taurus-fe/src/app.routes.ts
taurus-fe/src/app/components/menu/menu.component.ts
taurus-fe/src/app/service/keycloak.service.ts
taurus-fe/src/app/pages/calendar-events/**
taurus-fe/src/app/dialogs/add-users-dialog/**
taurus-fe/src/app/pages/users/detail/**
```

### Frontend del modulo

```text
taurus-fe/src/app/pages/finance/**
taurus-fe/src/app/dialogs/finance-*-dialog/**
taurus-fe/src/app/service/finance.service.ts
taurus-fe/src/app/module/finance.module.ts
```

## Checklist prima del rilascio

1. backup PostgreSQL e Keycloak;
2. verifica spazio e permessi dello storage allegati;
3. applicazione migration su tutti gli schemi tenant in staging;
4. provisioning idempotente `ROLE_TREASURER` sul realm esistente;
5. smoke test con Admin, Tesoriere, Utente e Utente esterno;
6. test di caricamento e download PDF/JPEG/PNG;
7. prova completa creazione-modifica-riconciliazione-eliminazione e verifica dell'impossibilità di ripristino;
8. prova trasferimento cassa-conto corrente;
9. prova evento con preventivo, incasso parziale, costo e saldo;
10. simulazione cambio anno e riporto saldi;
11. verifica esportazioni CSV/XLSX/PDF;
12. verifica che i dati economici restino nello schema del tenant;
13. monitoraggio errori di quadratura, upload e report dopo il rilascio.
14. verifica notifiche con Admin, Super Admin e Tesoriere, inclusi retry, deduplicazione e link di destinazione.

## Requisiti aperti

Nessuno: la specifica è coperta dal codice e dai test.
