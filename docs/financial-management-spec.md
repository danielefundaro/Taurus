# Gestione economica, cassa e conti correnti

## Stato del documento

Questa specifica raccoglie le decisioni funzionali e tecniche approvate per introdurre in Taurus la gestione economica del tenant. Il documento è pensato come riferimento autonomo per riprendere lo sviluppo in un momento successivo.

Le decisioni qui riportate sono definitive salvo nuova richiesta esplicita. Dove è indicata una raccomandazione implementativa, essa costituisce il comportamento predefinito da adottare.

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

PostgreSQL è il sistema autorevole. I dati economici non devono essere indicizzati in OpenSearch perché sono transazionali, sensibili e ricercabili efficacemente tramite query relazionali.

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
| Valuta iniziale | EUR, mantenendo un campo valuta ISO 4217 per evoluzioni future |
| Associazione movimento-evento | Facoltativa in generale, automatica quando il movimento nasce dalla pagina dell'evento |
| Stati movimento | Nessun ciclo di vita vincolante; ogni movimento resta modificabile |
| Correzione di un movimento | Modifica diretta o eliminazione logica, senza obbligo di storno |
| Saldo iniziale | Movimento tecnico di apertura |
| Categorie | Elenco iniziale predefinito, configurabile da Admin, Tesoriere e Super Admin |
| Allegati | Supportati ma non obbligatori |
| Trasferimenti | Due movimenti collegati e creati atomicamente |
| Rendiconti | Per periodo, conto, categoria ed evento, con esportazione CSV/XLSX e PDF |
| Esercizio | Dal 1 gennaio al 31 dicembre |
| Riporto saldi | Saldo finale al 31/12 riportato come apertura al 01/01 |
| Rettifiche su anni precedenti | Sempre consentite; saldi di apertura successivi ricalcolati automaticamente |

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
- eventuali destinatari delle notifiche economiche future.

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

È utile una tabella `accounting_year_audit` che registri generazione e ricalcolo dei saldi di apertura, autore e istante, senza introdurre blocchi operativi.

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
- un conto con movimenti non si elimina fisicamente: viene archiviato;
- un conto archiviato rimane visibile nello storico ma non accetta nuovi movimenti ordinari;
- il saldo non è memorizzato come campo modificabile;
- il saldo iniziale è rappresentato da un movimento tecnico di apertura;
- il saldo corrente è calcolato dalle scritture effettive dell'esercizio corrente, inclusa l'apertura.

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

Categorie iniziali suggerite:

- Compensi eventi;
- Quote associative;
- Donazioni;
- Rimborsi;
- Acquisto materiali;
- Trasporti;
- Affitto locali;
- Manutenzione;
- Utenze;
- Spese bancarie;
- Altro incasso;
- Altra spesa.

Le categorie usate non vengono eliminate fisicamente ma archiviate. Il nome può essere modificato; lo storico delle modifiche rimane nei campi di audit.

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
- `request_key UUID`, facoltativa e univoca, per rendere idempotenti le creazioni ripetute dal client.

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

Politica iniziale consigliata:

- formati ammessi: PDF, JPEG e PNG;
- massimo 10 MB per file;
- massimo 20 allegati per movimento;
- verifica server-side del tipo reale del contenuto;
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
- eliminare logicamente il movimento;
- ripristinare un movimento eliminato, se viene prevista la relativa azione nell'interfaccia.

La correzione avviene direttamente sul movimento. Non è obbligatorio e non è previsto come flusso standard creare uno storno o una scrittura opposta.

Restano soltanto le validazioni necessarie al corretto funzionamento del software:

- importo maggiore di zero;
- conto, data e direzione obbligatori;
- valuta coerente con il conto;
- riferimenti appartenenti allo stesso tenant;
- coerenza delle due righe che rappresentano un trasferimento.

Le normali informazioni `edit_by`, `edit_date` ed `entity_version` consentono di sapere chi ha effettuato l'ultima modifica e di evitare sovrascritture concorrenti involontarie, senza impedire la correzione.

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

Non è imposto il blocco del saldo negativo, perché un conto bancario può essere tecnicamente scoperto. L'interfaccia deve tuttavia mostrare un avviso quando il salvataggio di un'uscita porta il conto sotto zero.

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

Stati economici derivati suggeriti:

- `NO_BUDGET`: nessun compenso e nessun costo previsto;
- `NO_MOVEMENTS`: preventivo presente ma nessun consuntivo;
- `OPEN`: residui ancora presenti;
- `PARTIALLY_SETTLED`: almeno un movimento presente, ma posizione non saldata;
- `SETTLED`: residui pari a zero secondo la precisione monetaria;
- `OVERPAID_OR_OVERRUN`: almeno un residuo negativo.

Questi stati non devono essere salvati come booleani sull'evento: vengono calcolati per evitare disallineamenti.

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

Un job idempotente eseguito al cambio anno deve:

- creare l'esercizio successivo se non esiste;
- calcolare i saldi al 31/12;
- creare una sola apertura per coppia esercizio/conto;
- aggiornare l'apertura se il saldo precedente cambia;
- registrare data e risultato dell'ultimo ricalcolo;
- segnalare eventuali trasferimenti incoerenti senza impedire le correzioni.

Lo stato `ROLLED_OVER` indica soltanto che il riporto è stato generato almeno una volta. Non blocca inserimento, modifica o eliminazione dei movimenti.

### Correzioni successive al cambio anno

Non esiste una procedura obbligatoria di riapertura. Se Admin, Tesoriere o Super Admin correggono un movimento di un anno precedente, il backend deve:

1. salvare normalmente la correzione;
2. ricalcolare il saldo finale dell'anno interessato;
3. aggiornare il movimento di apertura dell'anno successivo;
4. propagare il ricalcolo alle aperture degli anni ancora successivi, se presenti;
5. aggiornare dashboard e rendiconti derivati.

Il ricalcolo può essere immediato nella stessa transazione per la prima versione. Se in futuro il volume dei dati lo rendesse costoso, potrà essere eseguito tramite job affidabile, mostrando temporaneamente che i saldi sono in aggiornamento.

### Verifica annuale facoltativa

L'interfaccia può offrire un riepilogo di fine anno con:

- saldi per conto;
- movimenti non riconciliati;
- trasferimenti incoerenti;
- posizioni evento ancora aperte;
- data dell'ultimo ricalcolo.

La verifica non impedisce modifiche successive e non richiede storni o riaperture.

### Calcolo dei saldi tra esercizi

I report annuali usano soltanto:

- movimento di apertura dell'anno;
- movimenti attivi e non cancellati dello stesso anno.

Non devono sommare tutte le aperture di tutti gli anni, altrimenti il riporto verrebbe contato più volte. Per il saldo storico a una data si usa l'apertura dell'esercizio contenente la data più i movimenti successivi dello stesso esercizio.

I compensi e i costi di eventi non ancora saldati non vengono chiusi artificialmente al 31/12: restano posizioni economiche aperte e possono generare movimenti nell'anno successivo.

## API REST

Tutti gli endpoint economici risiedono sotto `/api/finance/**` e devono essere autorizzati esplicitamente prima della regola generica `/api/**`.

Autorità consentite:

```text
ROLE_SUPER_ADMIN
ROLE_ADMIN
ROLE_TREASURER
```

### Conti

- `GET /api/finance/accounts`;
- `POST /api/finance/accounts`;
- `GET /api/finance/accounts/{id}`;
- `PUT /api/finance/accounts/{id}`;
- `DELETE /api/finance/accounts/{id}`, con semantica di archiviazione;
- `GET /api/finance/accounts/{id}/balance?date=YYYY-MM-DD`;
- `GET /api/finance/accounts/{id}/statement`.

### Categorie

- `GET /api/finance/categories`;
- `POST /api/finance/categories`;
- `PUT /api/finance/categories/{id}`;
- `DELETE /api/finance/categories/{id}`, con semantica di archiviazione.

### Movimenti

- `GET /api/finance/movements`, paginato e filtrabile;
- `POST /api/finance/movements`, crea un movimento attivo;
- `GET /api/finance/movements/{id}`;
- `PUT /api/finance/movements/{id}`, corregge qualsiasi movimento;
- `DELETE /api/finance/movements/{id}`, cancellazione logica sempre consentita;
- `POST /api/finance/movements/{id}/restore`, ripristino facoltativo di un movimento eliminato;
- `PATCH /api/finance/movements/{id}/reconciliation`, imposta o rimuove la riconciliazione;
- `POST /api/finance/transfers`.

Filtri minimi:

- intervallo data contabile;
- esercizio;
- conto;
- direzione;
- natura;
- riconciliato/non riconciliato;
- categoria;
- evento;
- controparte;
- testo libero su descrizione e riferimento.

### Allegati

- `POST /api/finance/movements/{id}/attachments`, multipart;
- `GET /api/finance/movements/{id}/attachments`;
- `GET /api/finance/attachments/{attachmentId}`;
- `DELETE /api/finance/attachments/{attachmentId}`, cancellazione logica sempre consentita.

L'upload deve fallire atomicamente: un file non valido non deve lasciare metadati orfani o contenuti temporanei non rimossi.

### Eventi economici

- `GET /api/finance/events`, con filtri economici;
- `GET /api/finance/events/{id}`;
- `PATCH /api/finance/events/{id}/budget`, modifica soltanto compenso e costi previsti;
- `GET /api/finance/events/{id}/movements`;
- `POST /api/finance/events/{id}/movements`, crea un movimento con evento già associato.

Il DTO economico dell'evento deve esporre solo i dati operativi necessari alla lettura e i dati economici. Non deve accettare campi come stato, date, presenze o disponibilità nell'endpoint di aggiornamento budget.

### Esercizi

- `GET /api/finance/years`;
- `GET /api/finance/years/{year}`;
- `POST /api/finance/years/{year}/rollover`, genera o aggiorna il riporto all'anno successivo;
- `POST /api/finance/years/{year}/recalculate`, ricalcola saldi e aperture successive;
- `GET /api/finance/years/{year}/summary`.

### Dashboard e rendiconti

- `GET /api/finance/dashboard?from=&to=`;
- `GET /api/finance/reports/cashbook`;
- `GET /api/finance/reports/account-statement`;
- `GET /api/finance/reports/events`;
- `GET /api/finance/reports/categories`;
- `GET /api/finance/reports/annual`.

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

I download degli allegati devono verificare nuovamente movimento, tenant e autorità. Non devono esporre direttamente il percorso fisico dello storage.

Le operazioni che modificano più dati devono essere transazionali:

- creazione, modifica o eliminazione di un trasferimento;
- modifica di un movimento che richiede il ricalcolo dei riporti;
- generazione o aggiornamento delle aperture annuali;
- associazione o rimozione coordinata degli allegati.

## Backend: struttura consigliata

### Domain ed enum

Nuove classi suggerite:

```text
domain/AccountingYear.java
domain/AccountingYearAudit.java
domain/FinancialAccount.java
domain/FinancialCategory.java
domain/FinancialMovement.java
domain/FinancialMovementAttachment.java
domain/enumeration/AccountingYearStatus.java
domain/enumeration/FinancialAccountType.java
domain/enumeration/FinancialDirection.java
domain/enumeration/FinancialMovementNature.java
```

Le entità possono riutilizzare i campi audit comuni. Il servizio movimenti offre un CRUD esplicito per applicare validazioni, ricalcoli e isolamento tenant senza introdurre transizioni di stato vincolanti.

### Repository

Repository distinti per ogni aggregato, con query dedicate per:

- saldi per conto e data;
- somme per evento;
- somme per categoria;
- conteggi e totali per dashboard;
- ricerca movimenti con `Specification`;
- ricalcolo dei saldi e dei riporti annuali;
- verifica trasferimenti e aperture;
- recupero allegati sempre attraverso il movimento proprietario.

### Service

Servizi suggeriti:

```text
AccountingYearService
FinancialAccountService
FinancialCategoryService
FinancialMovementService
FinancialTransferService
FinancialAttachmentService
FinancialEventService
FinancialReportService
```

Il servizio movimenti espone normali operazioni CRUD con validazione. La riconciliazione è un aggiornamento esplicito ma reversibile; non esistono transizioni che rendono il movimento immutabile.

### DTO

Separare i DTO di comando dai DTO di lettura:

- `CreateFinancialMovementDTO`;
- `UpdateFinancialMovementDTO`;
- `UpdateMovementReconciliationDTO`;
- `CreateTransferDTO`;
- `FinancialMovementDTO`;
- `FinancialAccountDTO`;
- `FinancialEventSummaryDTO`;
- `RolloverAccountingYearDTO`;
- `RecalculateAccountingYearDTO`.

La separazione impedisce overposting e distingue la normale correzione del movimento dall'aggiornamento rapido della riconciliazione.

### Validazione e concorrenza

- Bean Validation per campi obbligatori e formati;
- vincoli PostgreSQL come seconda linea di difesa;
- `@Version` per modifiche concorrenti di movimenti e conti;
- ricalcolo idempotente degli esercizi senza bloccare la correzione dello storico;
- lock o vincolo idempotente durante trasferimenti e aperture;
- `request_key` per evitare doppie scritture dovute al retry HTTP;
- calcoli monetari esclusivamente con `BigDecimal` e arrotondamento esplicito.

## Frontend

### Ruolo e navigazione

Aggiungere `RoleEnums.TREASURER = 'ROLE_TREASURER'` e l'etichetta `Tesoriere`.

Per il Tesoriere sono visibili almeno:

- Home/Dashboard;
- Calendario in lettura;
- Economia;
- Profilo.

Non sono visibili gestione utenti, tenant, catalogo o inventario amministrativo, salvo eventuali permessi derivanti da un diverso ruolo selezionato in una nuova sessione.

Il servizio calendario esistente instrada i ruoli non riconosciuti verso gli endpoint amministrativi. Prima di aggiungere il ruolo alle route del calendario è quindi necessario introdurre un percorso esplicito di sola lettura/economico per il Tesoriere; non si deve lasciare che `ROLE_TREASURER` ricada nel ramo amministrativo predefinito.

### Sezione Economia

Voce menu principale `Economia` con pagine:

1. **Dashboard**
   - saldo totale;
   - saldo per cassa e conto corrente;
   - entrate e uscite del periodo;
   - posizioni evento da incassare/pagare;
   - movimenti da riconciliare;
   - stato dell'ultimo riporto annuale.

2. **Movimenti**
   - tabella paginata;
   - filtri completi;
   - creazione movimento;
   - creazione, correzione, cancellazione e riconciliazione facoltativa;
   - gestione allegati;
   - esportazione del risultato filtrato.

3. **Conti**
   - elenco casse e conti correnti;
   - saldo attuale;
   - estratto movimenti;
   - creazione, modifica e archiviazione.

4. **Categorie**
   - elenco configurabile;
   - direzione consentita;
   - ordinamento e archiviazione.

5. **Eventi economici**
   - elenco eventi con preventivo, consuntivo e residui;
   - filtri per stato economico;
   - dettaglio movimenti;
   - azioni `Registra incasso` e `Registra spesa`.

6. **Rendiconti**
   - registro cassa;
   - estratto conto interno;
   - rendiconto mensile/annuale;
   - risultato per evento;
   - riepilogo per categoria.

7. **Esercizi**
   - riepilogo annuale;
   - anomalie informative;
   - generazione e ricalcolo dei riporti;
   - data dell'ultimo aggiornamento delle aperture.

### Pagina evento

Per Admin, Tesoriere e Super Admin mostrare una scheda economica con:

- compenso previsto;
- costi previsti;
- margine previsto;
- incassato e pagato;
- residui;
- risultato effettivo;
- elenco movimenti;
- azioni rapide per incasso e spesa.

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

Quando il form viene aperto da un evento, l'evento è preimpostato. Il sistema propone direzione e categoria coerenti con l'azione scelta.

### Esperienza utente nella gestione

- pulsante `Salva` con normale modifica del movimento;
- azioni `Modifica`, `Elimina` e, se prevista, `Ripristina` sempre disponibili agli utenti autorizzati;
- conferma prima della cancellazione logica;
- controllo facoltativo `Riconciliato` che può essere attivato o rimosso;
- warning per saldo negativo;
- indicazione quando una modifica storica comporta il ricalcolo delle aperture successive;
- indicatori per allegati mancanti senza considerarli errori.

## Rendiconti ed esportazioni

### Registro cassa

Per uno specifico conto e intervallo:

- saldo iniziale;
- movimenti ordinati per data e identificativo;
- entrate, uscite e saldo progressivo;
- saldo finale;
- riferimenti a evento, categoria e documento.

### Rendiconto per evento

- dati identificativi evento;
- preventivo entrate e costi;
- movimenti effettivi;
- residui;
- risultato previsto ed effettivo;
- eventuali scostamenti.

### Rendiconto annuale

- saldi iniziali e finali per conto;
- totale entrate e uscite ordinarie;
- trasferimenti separati;
- correzioni ed eliminazioni già riflesse nei totali correnti;
- totali per categoria;
- eventi economicamente aperti;
- movimenti non riconciliati;
- data dell'ultimo ricalcolo dei riporti.

### Formati

- CSV per interoperabilità semplice;
- XLSX per analisi e filtri;
- PDF per stampa e archiviazione.

I report devono essere generati dal backend per evitare divergenze nei calcoli. Ogni esportazione deve riportare tenant, periodo, filtri, data di generazione e utente richiedente.

## Migrazione dei dati

### Dati evento esistenti

I valori `fee` e `calendar_event_cost` restano invariati e diventano automaticamente il preventivo degli eventi. Non vengono creati movimenti retroattivi, perché non è possibile dedurre se un importo previsto sia stato realmente pagato o incassato.

Gli eventi esistenti con compenso o costi compariranno subito nell'elenco economico con stato `NO_MOVEMENTS` fino alla registrazione del consuntivo.

### Saldi iniziali

Al primo avvio del modulo, per ciascun conto creato l'utente inserisce:

- data di riferimento;
- saldo iniziale;
- eventuale descrizione e allegato.

Il backend crea un movimento tecnico `OPENING`. Anche questo valore può essere corretto attraverso il normale flusso gestionale, pur mantenendo il ricalcolo automatico come modalità consigliata. Non deve esistere un campo saldo iniziale separato sul conto.

### Rollback

Un rollback applicativo deve preservare tabelle, movimenti e allegati. Non devono essere eseguiti drop automatici delle strutture economiche. Un rollback distruttivo richiede esportazione e conservazione esplicita dei dati.

## Audit, conservazione e cancellazioni

- tutti i movimenti possono essere modificati;
- tutti i movimenti possono essere cancellati logicamente e, se previsto, ripristinati;
- conti e categorie usati: archiviati, non cancellati;
- tutti gli allegati possono essere aggiunti, sostituiti o rimossi logicamente;
- ogni modifica conserva almeno ultimo autore, data e versione;
- non sono richiesti storni, motivazioni di rettifica o procedure di riapertura;
- i riferimenti all'identità autenticata sono conservati secondo la policy applicativa e di retention del tenant;
- l'eliminazione GDPR di un utente non deve alterare involontariamente importi o saldi; gli identificativi personali possono essere pseudonimizzati senza rimuovere il movimento.

Taurus è uno strumento gestionale di supporto e non sostituisce la contabilità fiscale. La cancellazione logica è raccomandata per consentire recupero e diagnosi, ma non introduce immutabilità contabile.

## Osservabilità

Registrare log applicativi strutturati per:

- movimento creato, modificato, eliminato, ripristinato o riconciliato;
- trasferimento creato o fallito;
- allegato caricato o invalidato;
- esercizio creato o riportato all'anno successivo;
- apertura annuale generata o ricalcolata;
- errore di quadratura;
- tentativo di accesso non autorizzato.

I log non devono contenere file allegati, token, IBAN completi o note potenzialmente sensibili. Per l'IBAN usare una versione mascherata.

Metriche utili:

- durata dei report;
- numero di movimenti attivi, eliminati e riconciliati;
- fallimenti upload;
- fallimenti nel ricalcolo dei riporti;
- movimenti non riconciliati;
- spazio storage allegati per tenant.

## Piano di implementazione

### Fase 1 - Ruolo e sicurezza

- aggiungere `ROLE_TREASURER` a backend, frontend e Keycloak;
- provisioning idempotente sui realm esistenti;
- aggiornare gestione utenti e selezione ruolo;
- proteggere esplicitamente `/api/finance/**`;
- aggiungere test autorizzativi per tutti i ruoli.

### Fase 2 - Schema e servizi fondamentali

- migration Liquibase tenant;
- esercizi, conti e categorie;
- CRUD completo dei movimenti;
- correzione ed eliminazione logica con ricalcolo saldi;
- audit e concorrenza ottimistica.

### Fase 3 - Operazioni gestionali

- riconciliazione facoltativa e reversibile;
- trasferimenti atomici;
- idempotenza;
- gestione allegati.

### Fase 4 - Integrazione eventi

- endpoint economici dedicati;
- riepilogo preventivo/consuntivo;
- filtri economici;
- azioni rapide incasso/spesa;
- aggiornamento pagina evento per Admin, Tesoriere e Super Admin.

### Fase 5 - Frontend Economia

- menu e route;
- dashboard;
- conti, categorie e movimenti;
- eventi economici;
- esercizi e riporti annuali;
- gestione allegati.

### Fase 6 - Rendiconti

- registro cassa e conto;
- rendiconto eventi e categorie;
- rendiconto annuale;
- esportazioni CSV/XLSX/PDF.

### Fase 7 - Riporto annuale

- job cambio anno idempotente;
- riepilogo di fine anno;
- segnalazioni informative di incoerenza;
- generazione e aggiornamento delle aperture;
- propagazione automatica delle correzioni storiche.

### Fase 8 - Hardening e rilascio

- test end-to-end;
- test multi-tenant;
- test concorrenza e retry;
- verifica storage;
- backup e smoke test staging;
- monitoraggio post-rilascio.

## Strategia di test

### Unit test backend

- modifica libera dei movimenti;
- esclusione dei movimenti cancellati logicamente dai saldi;
- ricalcolo dopo correzione o eliminazione;
- esclusione trasferimenti dai totali consolidati;
- esclusione aperture da ricavi/costi annuali;
- formule preventivo/consuntivo evento;
- validazione valuta e importi;
- riconciliazione attivabile e rimovibile senza bloccare la modifica;
- aggiornamento diretto senza generazione di storni.

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
- ogni movimento attivo influenza immediatamente i saldi;
- ogni movimento può essere modificato o cancellato logicamente;
- una correzione aggiorna direttamente il movimento senza richiedere uno storno;
- un trasferimento genera sempre due scritture coerenti oppure nessuna;
- gli allegati sono facoltativi, protetti per tenant e verificati server-side;
- preventivo e consuntivo evento rimangono separati;
- un movimento creato dall'evento conserva automaticamente il collegamento;
- gli eventi esistenti con compensi/costi compaiono senza migrazione distruttiva;
- i report non contano aperture come ricavi né trasferimenti come entrate consolidate;
- il saldo viene determinato al 31/12 e il successivo esercizio apre al 01/01 con saldo riportato;
- gli eventi non saldati attraversano il cambio anno senza perdere il residuo;
- una modifica storica ricalcola automaticamente tutti i riporti successivi interessati;
- nessun dato economico attraversa il confine dello schema tenant;
- CSV/XLSX e PDF restituiscono gli stessi totali delle API e della dashboard.

## File e aree previste

### Backend da modificare

```text
taurus-be/src/main/java/com/fundaro/zodiac/taurus/security/AuthoritiesConstants.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/domain/enumeration/RoleEnum.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/config/SecurityConfiguration.java
taurus-be/src/main/docker/realm-config/taurus-realm.json
taurus-be/src/main/resources/config/liquibase/tenant-master.xml
```

### Backend da creare

```text
taurus-be/src/main/java/com/fundaro/zodiac/taurus/domain/*Financial*.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/domain/AccountingYear*.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/repository/*Financial*.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/*Financial*.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/dto/*Financial*.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/mapper/*Financial*.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/web/rest/finance/*.java
taurus-be/src/main/resources/config/liquibase/changelog/*_add_financial_management.xml
```

### Frontend da modificare

```text
taurus-fe/src/app/constants/role.enums.ts
taurus-fe/src/app/app.routes.ts o taurus-fe/src/app.routes.ts secondo struttura corrente
taurus-fe/src/app/components/menu/menu.component.ts
taurus-fe/src/app/service/keycloak.service.ts
taurus-fe/src/app/pages/calendar-events/**
taurus-fe/src/app/dialogs/add-users-dialog/**
taurus-fe/src/app/pages/users/detail/**
```

### Frontend da creare

```text
taurus-fe/src/app/pages/finance/**
taurus-fe/src/app/service/financial-*.service.ts
taurus-fe/src/app/module/financial-*.module.ts
taurus-fe/src/app/module/criteria/financial-*.ts
```

I nomi precisi possono essere adattati alle convenzioni applicative osservate durante l'implementazione, mantenendo invariati confini, autorizzazioni e comportamenti descritti in questa specifica.

## Checklist prima del rilascio

1. backup PostgreSQL e Keycloak;
2. verifica spazio e permessi dello storage allegati;
3. applicazione migration su tutti gli schemi tenant in staging;
4. provisioning idempotente `ROLE_TREASURER` sul realm esistente;
5. smoke test con Admin, Tesoriere, Utente e Utente esterno;
6. test di caricamento e download PDF/JPEG/PNG;
7. prova completa creazione-modifica-riconciliazione-eliminazione-ripristino;
8. prova trasferimento cassa-conto corrente;
9. prova evento con preventivo, incasso parziale, costo e saldo;
10. simulazione cambio anno e riporto saldi;
11. verifica esportazioni CSV/XLSX/PDF;
12. verifica che OpenSearch non riceva dati economici;
13. monitoraggio errori di quadratura, upload e report dopo il rilascio.
