# Gestione economica, cassa e conti correnti

## Stato del documento

Questa specifica raccoglie le decisioni funzionali e tecniche approvate per introdurre in Taurus la gestione economica del tenant. Il documento è pensato come riferimento autonomo per riprendere lo sviluppo in un momento successivo.

Le decisioni qui riportate sono definitive salvo nuova richiesta esplicita. Dove è indicata una raccomandazione implementativa, essa costituisce il comportamento predefinito da adottare.

## Obiettivo

La funzionalità deve consentire di:

- introdurre il ruolo specializzato `ROLE_TREASURER`, mostrato nell'interfaccia come **Tesoriere**;
- gestire una o più casse e uno o più conti correnti per ciascun tenant;
- registrare entrate, uscite, trasferimenti, storni e riconciliazioni;
- distinguere il preventivo economico degli eventi dal consuntivo effettivamente movimentato;
- associare facoltativamente un movimento a un evento;
- gestire allegati facoltativi per ogni movimento;
- produrre saldi, rendiconti ed esportazioni;
- chiudere ogni esercizio al 31 dicembre e aprire il successivo al 1 gennaio riportando i saldi;
- mantenere tracciabilità, isolamento tenant e integrità dello storico contabile.

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
| Consuntivo eventi | Movimenti finanziari realmente contabilizzati |
| Conti | Più casse e più conti correnti per tenant |
| Valuta iniziale | EUR, mantenendo un campo valuta ISO 4217 per evoluzioni future |
| Associazione movimento-evento | Facoltativa in generale, automatica quando il movimento nasce dalla pagina dell'evento |
| Stati movimento | `DRAFT`, `POSTED`, `RECONCILED`, `REVERSED` |
| Correzione di un movimento contabilizzato | Storno tracciato, senza cancellazione dello storico |
| Saldo iniziale | Movimento tecnico di apertura |
| Categorie | Elenco iniziale predefinito, configurabile da Admin, Tesoriere e Super Admin |
| Allegati | Supportati ma non obbligatori |
| Trasferimenti | Due movimenti collegati e creati atomicamente |
| Rendiconti | Per periodo, conto, categoria ed evento, con esportazione CSV/XLSX e PDF |
| Esercizio | Dal 1 gennaio al 31 dicembre |
| Riporto saldi | Saldo finale al 31/12 riportato come apertura al 01/01 |
| Rettifiche su esercizio chiuso | Riapertura esplicita, motivata e tracciata |

## Terminologia

- **Conto finanziario**: una cassa fisica o un conto corrente bancario.
- **Movimento**: una scrittura che aumenta o diminuisce il saldo di un conto.
- **Preventivo evento**: compenso atteso e costi previsti registrati sull'evento.
- **Consuntivo evento**: entrate e uscite effettive associate all'evento.
- **Contabilizzazione**: conferma di una bozza che rende il movimento rilevante per i saldi.
- **Riconciliazione**: verifica di un movimento rispetto all'estratto conto o al controllo di cassa.
- **Storno**: nuova scrittura opposta che neutralizza un movimento già contabilizzato.
- **Esercizio**: periodo contabile annuale 01/01-31/12.

## Ruoli e autorizzazioni

### Matrice funzionale

| Operazione | Super Admin | Admin | Tesoriere | Archivista | Utente | Utente esterno |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Visualizzare dashboard economica | sì | sì | sì | no | no | no |
| Consultare conti e saldi | sì | sì | sì | no | no | no |
| Creare e modificare conti | sì | sì | sì | no | no | no |
| Gestire categorie | sì | sì | sì | no | no | no |
| Creare/modificare/eliminare bozze | sì | sì | sì | no | no | no |
| Contabilizzare movimenti | sì | sì | sì | no | no | no |
| Riconciliare movimenti | sì | sì | sì | no | no | no |
| Stornare movimenti | sì | sì | sì | no | no | no |
| Eseguire trasferimenti | sì | sì | sì | no | no | no |
| Gestire allegati | sì | sì | sì | no | no | no |
| Gestire preventivo eventi | sì | sì | sì | no | no | no |
| Consultare consuntivo eventi | sì | sì | sì | no | no | no |
| Chiudere/riaprire esercizi | sì | sì | sì | no | no | no |
| Esportare rendiconti | sì | sì | sì | no | no | no |
| Modificare dati operativi evento | sì | sì | no | secondo permessi esistenti | no | no |
| Gestire utenti, catalogo e inventario | sì | sì | no | secondo permessi esistenti | no | no |

L'assenza di limitazioni per `ROLE_ADMIN` riguarda la parità di autorizzazione con Tesoriere e Super Admin sul modulo economico. I vincoli di integrità contabile, come l'impossibilità di cancellare una scrittura già contabilizzata, si applicano a tutti i ruoli e non costituiscono una limitazione di ruolo.

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
- `status VARCHAR(16)`: `OPEN`, `CLOSING`, `CLOSED`;
- `closed_at TIMESTAMPTZ` e `closed_by VARCHAR(255)`;
- `reopened_at TIMESTAMPTZ` e `reopened_by VARCHAR(255)`;
- `reopen_reason TEXT`;
- campi di audit e `entity_version`.

Vincoli:

- `start_date` e `end_date` devono appartenere allo stesso anno;
- una sola riga per anno;
- la chiusura deve essere serializzata tramite lock pessimista o advisory lock PostgreSQL;
- la riapertura richiede sempre una motivazione non vuota.

È utile una tabella immutabile `accounting_year_audit` che registri ogni passaggio di stato, autore, istante e motivazione.

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
- `category_id BIGINT`, facoltativo per aperture, trasferimenti e storni;
- `event_id BIGINT`, facoltativo;
- `event_name_snapshot VARCHAR(255)`, valorizzato alla contabilizzazione se associato a un evento;
- `direction VARCHAR(16)`: `INCOME` o `EXPENSE`;
- `nature VARCHAR(16)`: `ORDINARY`, `OPENING`, `TRANSFER`, `REVERSAL`;
- `status VARCHAR(16)`: `DRAFT`, `POSTED`, `RECONCILED`, `REVERSED`;
- `booking_date DATE` obbligatoria;
- `value_date DATE`, facoltativa;
- `amount NUMERIC(19,4)` obbligatorio e strettamente maggiore di zero;
- `currency CHAR(3)` obbligatoria;
- `description TEXT` obbligatoria;
- `counterparty VARCHAR(500)`;
- `document_reference VARCHAR(255)`;
- `notes TEXT`;
- `transfer_group UUID`, facoltativo;
- `reversed_movement_id BIGINT`, facoltativo e autoreferenziale;
- `posted_at TIMESTAMPTZ` e `posted_by VARCHAR(255)`;
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
- indice su `(status, booking_date)`;
- indice su `(category_id, booking_date)`;
- indice univoco su `request_key` quando valorizzata;
- indice su `transfer_group`;
- indice su `reversed_movement_id`.

Il campo `event_name_snapshot` preserva la denominazione mostrata nei rendiconti anche se l'evento viene rinominato o cancellato logicamente in seguito.

### Allegato: `financial_movement_attachment`

Campi proposti:

- `id BIGINT`;
- `movement_id BIGINT` obbligatorio;
- `original_filename VARCHAR(500)`;
- `storage_path VARCHAR(2048)`;
- `media_type VARCHAR(255)`;
- `file_size BIGINT`;
- `sha256 CHAR(64)`;
- `description VARCHAR(1000)`;
- `active BOOLEAN`;
- campi di audit.

Gli allegati sono sempre facoltativi e possono essere più di uno per movimento.

Politica iniziale consigliata:

- formati ammessi: PDF, JPEG e PNG;
- massimo 10 MB per file;
- massimo 20 allegati per movimento;
- verifica server-side del tipo reale del contenuto;
- digest SHA-256 obbligatorio;
- storage separato per tenant e movimento;
- nessun contenuto binario nel database;
- download esclusivamente tramite endpoint autenticato che verifica tenant e ruolo.

Una bozza può perdere definitivamente un allegato. Dopo la contabilizzazione, un allegato non viene eliminato fisicamente: può essere marcato come sostituito o non valido, con motivazione e tracciamento. È consentito aggiungere successivamente un giustificativo a un movimento contabilizzato o riconciliato.

### Indici e query aggregate

Non è necessario memorizzare il saldo corrente. Il backend calcola:

```text
saldo conto = aperture + entrate effettive - uscite effettive
```

Contribuiscono ai saldi i movimenti contabilizzati, riconciliati e le coppie originale/storno. Le bozze non contribuiscono.

Quando un movimento viene stornato:

- il movimento originale assume stato `REVERSED` ma rimane nel calcolo storico;
- viene creata una nuova scrittura opposta con natura `REVERSAL` e stato `POSTED`;
- la somma delle due scritture è zero;
- i report non devono escludere l'originale `REVERSED`, altrimenti lo storno produrrebbe un saldo errato.

Le aperture contribuiscono al saldo del conto ma non ai ricavi/costi dell'anno. I trasferimenti contribuiscono ai singoli saldi dei conti ma sono esclusi dalle entrate/uscite consolidate del tenant.

## Ciclo di vita dei movimenti

```text
DRAFT -> POSTED -> RECONCILED
             \\-> REVERSED
```

### `DRAFT`

- non contribuisce ai saldi;
- può essere modificato;
- può essere eliminato logicamente;
- conto, importo, data, evento e categoria possono cambiare;
- gli allegati possono essere aggiunti o rimossi.

### `POSTED`

- contribuisce ai saldi e ai consuntivi;
- non può essere modificato nei dati economici;
- non può essere eliminato;
- può ricevere allegati successivi;
- può essere riconciliato oppure stornato.

La contabilizzazione deve validare conto attivo, esercizio, importo, valuta, categoria, evento e coerenza dell'eventuale trasferimento.

### `RECONCILED`

- è stato verificato rispetto all'estratto conto o al controllo di cassa;
- continua a contribuire ai saldi;
- non può essere modificato o eliminato;
- può ricevere allegati;
- può essere stornato, mantenendo la riconciliazione nello storico.

### `REVERSED`

- indica che il movimento originale è stato neutralizzato;
- conserva tutti i propri dati e allegati;
- è collegato alla scrittura opposta di storno;
- non può essere nuovamente stornato;
- non può essere modificato o eliminato.

Lo storno richiede data, motivo e conto. Di norma usa lo stesso conto, la stessa valuta e l'importo dell'originale con direzione opposta.

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
- stato aggiornato in modo coerente per entrambe le scritture;
- uno storno del trasferimento genera una nuova coppia inversa;
- il trasferimento non compare nei totali consolidati entrate/uscite, ma compare nei registri dei singoli conti.

Non è imposto il blocco del saldo negativo, perché un conto bancario può essere tecnicamente scoperto. L'interfaccia deve tuttavia mostrare un avviso prima di contabilizzare un'uscita che porta il conto sotto zero.

## Preventivo e consuntivo degli eventi

### Preventivo

Il modello esistente rimane autorevole per il preventivo:

- `calendar_event.fee`: compenso previsto;
- `calendar_event_cost`: singole voci di costo previsto.

Non è richiesta una migrazione distruttiva dei dati esistenti.

### Consuntivo

Il consuntivo deriva dai movimenti `POSTED`, `RECONCILED` e dalle relative scritture di storno associati all'evento.

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

## Esercizi, chiusura e riapertura annuale

### Regola temporale

Ogni esercizio va dal 1 gennaio al 31 dicembre. I movimenti con data contabile nell'anno appartengono al relativo esercizio.

Il processo annuale è:

1. al 31/12 l'esercizio entra nella procedura di chiusura;
2. vengono verificati movimenti, saldi, trasferimenti e riconciliazioni;
3. Admin, Tesoriere o Super Admin confermano la chiusura;
4. l'esercizio diventa `CLOSED` ed è bloccato;
5. al 01/01 viene aperto il nuovo esercizio;
6. per ogni conto viene creato un movimento tecnico `OPENING` pari al saldo finale dell'anno precedente.

L'apertura non è conteggiata come ricavo o costo del nuovo anno.

### Automazione del cambio anno

Un job idempotente eseguito al cambio anno deve:

- creare l'esercizio successivo se non esiste;
- portare il precedente in `CLOSING` se non è già chiuso;
- preparare aperture provvisorie per i conti attivi;
- notificare nell'applicazione Admin e Tesorieri che la chiusura deve essere verificata;
- impedire duplicati tramite vincoli univoci su esercizio, conto e natura di apertura.

La conferma della chiusura ricalcola e rende definitive le aperture. Se la chiusura viene completata dopo il 1 gennaio, il nuovo esercizio può ricevere movimenti, ma il saldo di apertura resta indicato come provvisorio fino alla conferma.

### Condizioni di chiusura

La chiusura deve essere bloccata quando:

- esistono bozze datate nell'esercizio;
- esistono trasferimenti incompleti o incoerenti;
- manca un'apertura o la quadratura di un conto;
- il calcolo dei saldi non coincide con lo snapshot di chiusura.

Per i conti bancari è raccomandata la riconciliazione fino al 31/12. L'interfaccia deve evidenziare eventuali movimenti non riconciliati e richiedere una conferma esplicita se la policy applicativa consente comunque di chiudere.

### Riapertura

Admin, Tesoriere e Super Admin possono riaprire un esercizio chiuso, ma devono:

- indicare una motivazione obbligatoria;
- ricevere un avviso sugli esercizi successivi già movimentati;
- produrre una voce immutabile nell'audit dell'esercizio.

Dopo le rettifiche, una nuova chiusura ricalcola il saldo finale e aggiorna in modo tracciato i movimenti tecnici di apertura dell'anno successivo. Le aperture precedenti non devono scomparire senza traccia: la revisione o sostituzione deve essere registrata nell'audit.

### Calcolo dei saldi tra esercizi

I report annuali usano soltanto:

- movimento di apertura dell'anno;
- movimenti effettivi dello stesso anno.

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
- `POST /api/finance/movements`, crea una bozza;
- `GET /api/finance/movements/{id}`;
- `PUT /api/finance/movements/{id}`, solo per bozze;
- `DELETE /api/finance/movements/{id}`, solo per bozze;
- `POST /api/finance/movements/{id}/post`;
- `POST /api/finance/movements/{id}/reconcile`;
- `POST /api/finance/movements/{id}/reverse`;
- `POST /api/finance/transfers`.

Filtri minimi:

- intervallo data contabile;
- esercizio;
- conto;
- direzione;
- natura;
- stato;
- categoria;
- evento;
- controparte;
- testo libero su descrizione e riferimento.

### Allegati

- `POST /api/finance/movements/{id}/attachments`, multipart;
- `GET /api/finance/movements/{id}/attachments`;
- `GET /api/finance/attachments/{attachmentId}`;
- `DELETE /api/finance/attachments/{attachmentId}`, eliminazione reale solo se il movimento è bozza, altrimenti invalidazione tracciata.

L'upload deve fallire atomicamente: un file non valido non deve lasciare metadati orfani o contenuti temporanei non rimossi.

### Eventi economici

- `GET /api/finance/events`, con filtri economici;
- `GET /api/finance/events/{id}`;
- `PATCH /api/finance/events/{id}/budget`, modifica soltanto compenso e costi previsti;
- `GET /api/finance/events/{id}/movements`;
- `POST /api/finance/events/{id}/movements`, crea una bozza con evento già associato.

Il DTO economico dell'evento deve esporre solo i dati operativi necessari alla lettura e i dati economici. Non deve accettare campi come stato, date, presenze o disponibilità nell'endpoint di aggiornamento budget.

### Esercizi

- `GET /api/finance/years`;
- `GET /api/finance/years/{year}`;
- `POST /api/finance/years/{year}/close`;
- `POST /api/finance/years/{year}/reopen`;
- `GET /api/finance/years/{year}/closing-preview`.

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

Le operazioni critiche devono essere transazionali:

- contabilizzazione;
- riconciliazione;
- storno;
- trasferimento;
- chiusura e riapertura esercizio;
- generazione o aggiornamento aperture.

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
domain/enumeration/FinancialMovementStatus.java
```

Le entità possono riutilizzare i campi audit comuni, ma i servizi economici devono applicare regole specifiche e non affidarsi al CRUD generico per transizioni di stato.

### Repository

Repository distinti per ogni aggregato, con query dedicate per:

- saldi per conto e data;
- somme per evento;
- somme per categoria;
- conteggi e totali per dashboard;
- ricerca movimenti con `Specification`;
- lock dell'esercizio;
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

Le transizioni `post`, `reconcile`, `reverse`, `close` e `reopen` devono essere metodi espliciti. Non devono essere ottenute modificando direttamente il campo `status` tramite un DTO generico.

### DTO

Separare i DTO di comando dai DTO di lettura:

- `CreateFinancialMovementDTO`;
- `UpdateFinancialMovementDraftDTO`;
- `PostMovementDTO`;
- `ReconcileMovementDTO`;
- `ReverseMovementDTO`;
- `CreateTransferDTO`;
- `FinancialMovementDTO`;
- `FinancialAccountDTO`;
- `FinancialEventSummaryDTO`;
- `CloseAccountingYearDTO`;
- `ReopenAccountingYearDTO`.

La separazione impedisce overposting e rende evidenti i campi modificabili in ogni fase.

### Validazione e concorrenza

- Bean Validation per campi obbligatori e formati;
- vincoli PostgreSQL come seconda linea di difesa;
- `@Version` per modifiche concorrenti di bozze e conti;
- lock sull'esercizio durante chiusura/riapertura;
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
   - stato della chiusura annuale.

2. **Movimenti**
   - tabella paginata;
   - filtri completi;
   - creazione bozza;
   - contabilizzazione, riconciliazione e storno;
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
   - anteprima chiusura;
   - anomalie bloccanti;
   - conferma chiusura;
   - storico aperture e riaperture.

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

### Esperienza utente sugli stati

- badge di stato chiaramente visibile;
- pulsante `Salva bozza`;
- conferma esplicita prima di `Contabilizza`;
- spiegazione che un movimento contabilizzato si corregge tramite storno;
- motivo obbligatorio nello storno;
- warning per saldo negativo;
- warning per esercizio in chiusura o chiuso;
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
- storni evidenziati;
- totali per categoria;
- eventi economicamente aperti;
- movimenti non riconciliati;
- dati della chiusura e autore.

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

Il backend crea un movimento `OPENING` contabilizzato. Non deve esistere un campo saldo iniziale modificabile direttamente sul conto.

### Rollback

Un rollback applicativo deve preservare tabelle, movimenti e allegati. Non devono essere eseguiti drop automatici delle strutture economiche. Un rollback distruttivo richiede esportazione e conservazione esplicita dei dati.

## Audit, conservazione e cancellazioni

- bozze: cancellazione logica consentita;
- movimenti contabilizzati: mai cancellati;
- conti e categorie usati: archiviati, non cancellati;
- allegati di scritture contabilizzate: invalidati/sostituiti, non rimossi senza traccia;
- ogni transizione conserva autore e istante;
- riaperture esercizio e storni richiedono motivazione;
- i riferimenti all'identità autenticata sono conservati secondo la policy applicativa e di retention del tenant;
- l'eliminazione GDPR di un utente non deve alterare importi o quadrature contabili; gli identificativi personali possono essere pseudonimizzati senza cancellare la scrittura.

La durata legale di conservazione e gli obblighi fiscali specifici dipendono dal contesto del tenant e devono essere configurati o verificati separatamente; Taurus deve comunque evitare cancellazioni irreversibili dello storico contabilizzato.

## Osservabilità

Registrare log applicativi strutturati per:

- movimento creato, contabilizzato, riconciliato e stornato;
- trasferimento creato o fallito;
- allegato caricato o invalidato;
- esercizio aperto, chiuso o riaperto;
- apertura annuale generata o ricalcolata;
- errore di quadratura;
- tentativo di accesso non autorizzato.

I log non devono contenere file allegati, token, IBAN completi o note potenzialmente sensibili. Per l'IBAN usare una versione mascherata.

Metriche utili:

- durata dei report;
- numero di movimenti per stato;
- fallimenti upload;
- fallimenti chiusura;
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
- movimenti in bozza;
- contabilizzazione e calcolo saldi;
- audit e concorrenza ottimistica.

### Fase 3 - Operazioni contabili

- riconciliazione;
- storni;
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
- esercizi e chiusura;
- gestione allegati.

### Fase 6 - Rendiconti

- registro cassa e conto;
- rendiconto eventi e categorie;
- rendiconto annuale;
- esportazioni CSV/XLSX/PDF.

### Fase 7 - Chiusura annuale

- job cambio anno idempotente;
- anteprima chiusura;
- controlli di quadratura;
- aperture provvisorie e definitive;
- riapertura motivata e ricalcolo tracciato.

### Fase 8 - Hardening e rilascio

- test end-to-end;
- test multi-tenant;
- test concorrenza e retry;
- verifica storage;
- backup e smoke test staging;
- monitoraggio post-rilascio.

## Strategia di test

### Unit test backend

- transizioni di stato valide e non valide;
- esclusione bozze dai saldi;
- inclusione corretta di originali e storni;
- esclusione trasferimenti dai totali consolidati;
- esclusione aperture da ricavi/costi annuali;
- formule preventivo/consuntivo evento;
- validazione valuta e importi;
- blocco modifica di un movimento contabilizzato;
- motivo obbligatorio per storno e riapertura.

### Integration test PostgreSQL

- vincoli e foreign key;
- isolamento per schema tenant;
- trasferimento atomico;
- lock durante chiusura;
- idempotenza `request_key`;
- apertura annuale non duplicata;
- ricalcolo apertura dopo riapertura;
- query saldo progressivo;
- gestione concorrente di due contabilizzazioni.

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
- form e conferme rispettano gli stati;
- caricamento allegati facoltativo;
- riepiloghi evento coerenti con le risposte API;
- warning chiusura, saldo negativo e storno.

### Test di chiusura annuale

- chiusura al 31/12;
- apertura al 01/01;
- riporto esatto di saldi positivi, nulli e negativi;
- nessun doppio conteggio delle aperture;
- blocco con bozze o trasferimenti incompleti;
- riapertura motivata;
- ricalcolo tracciato del saldo iniziale successivo;
- permanenza delle posizioni evento non saldate.

## Criteri di accettazione

- `ROLE_TREASURER` è assegnabile per tenant e selezionabile al login;
- Admin, Tesoriere e Super Admin hanno accesso economico completo;
- il Tesoriere non acquisisce gestione utenti, catalogo, inventario o modifica operativa degli eventi;
- nessuna API economica è accessibile agli altri ruoli;
- ogni conto mostra un saldo riproducibile dai movimenti;
- le bozze non influenzano i saldi;
- un movimento contabilizzato non può essere modificato o cancellato;
- uno storno mantiene originale, scrittura opposta e motivazione;
- un trasferimento genera sempre due scritture coerenti oppure nessuna;
- gli allegati sono facoltativi, protetti per tenant e verificati server-side;
- preventivo e consuntivo evento rimangono separati;
- un movimento creato dall'evento conserva automaticamente il collegamento;
- gli eventi esistenti con compensi/costi compaiono senza migrazione distruttiva;
- i report non contano aperture come ricavi né trasferimenti come entrate consolidate;
- l'esercizio chiude al 31/12 e il successivo apre al 01/01 con saldo riportato;
- gli eventi non saldati attraversano il cambio anno senza perdere il residuo;
- una riapertura è sempre motivata e auditata;
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
7. prova completa bozza-contabilizzazione-riconciliazione-storno;
8. prova trasferimento cassa-conto corrente;
9. prova evento con preventivo, incasso parziale, costo e saldo;
10. simulazione cambio anno e riporto saldi;
11. verifica esportazioni CSV/XLSX/PDF;
12. verifica che OpenSearch non riceva dati economici;
13. monitoraggio errori di quadratura, upload e report dopo il rilascio.
