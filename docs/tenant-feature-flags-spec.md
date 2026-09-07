# Funzionalità configurabili per tenant

## Stato del documento

ID catalogo: `tenant-feature-flags`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

L'evolutiva è implementata per tutti gli otto flag descritti in questo documento, con `notification-delivery-generalization` esplicitamente fuori perimetro.

## Obiettivo

Consentire a un super amministratore di attivare o disattivare dalla pagina di dettaglio del singolo tenant:

- **Economia**;
- **Inventario**;
- **Onboarding e importazione iniziale**;
- **Feed calendario esterno**;
- **QR code inventario**;
- **Preferenze notifiche**;
- **Promemoria eventi Web Push**;
- **Preparazione evento**.

`notification-delivery-generalization` è esclusa da questa evolutiva: è un'infrastruttura trasversale autorevole e non una funzionalità tenant disattivabile.

La configurazione deve:

- essere indipendente per ogni tenant;
- controllare sia la visibilità nel frontend sia l'accesso reale nel backend;
- diventare effettiva per gli utenti già collegati senza richiedere logout e login;
- conservare tutti i dati quando una funzionalità viene disattivata;
- evitare l'introduzione di una WebSocket dedicata.

Ruoli e funzionalità sono verificati insieme:

```text
Accesso consentito = ruolo autorizzato AND funzionalità attiva per il tenant corrente
```

## Decisioni architetturali

1. I flag tenant sono memorizzati nella tabella pubblica `tenant`, non negli schemi dei singoli tenant.
2. I flag non sono inseriti nel token Keycloak, perché altrimenti sarebbe necessario rinnovare il token o ripetere il login per osservare le modifiche.
3. Il backend è sempre la fonte autorevole e verifica la funzionalità prima di eseguire un'operazione protetta.
4. Il frontend mantiene una copia in memoria della configurazione per decidere quali elementi mostrare.
5. Non viene aperto alcun canale WebSocket: il frontend aggiorna periodicamente la configurazione e reagisce comunque agli eventuali rifiuti del backend.
6. Disattivare una funzionalità non cancella, archivia o modifica i dati del relativo modulo.
7. Le proprietà applicative esistenti restano kill switch di installazione. Un flag tenant non può abilitare una capacità disabilitata a livello applicativo.
8. Il valore esposto agli utenti è sempre quello effettivo, calcolato applicando flag globale, flag tenant e dipendenze.

```text
Funzionalità effettiva = capacità applicativa AND flag tenant AND dipendenze effettive
```

La generalizzazione della consegna notifiche resta sempre attiva dopo il relativo rollout. Disabilitare Preferenze notifiche o Web Push non riattiva percorsi sincroni legacy e non crea un secondo dispatcher.

## Modello dati

### Tabella `public.tenant`

Aggiungere tramite Liquibase:

```text
finance_enabled                    BOOLEAN NOT NULL DEFAULT FALSE
inventory_enabled                  BOOLEAN NOT NULL DEFAULT FALSE
onboarding_import_enabled          BOOLEAN NOT NULL DEFAULT FALSE
external_calendar_feed_enabled     BOOLEAN NOT NULL DEFAULT FALSE
inventory_qr_enabled               BOOLEAN NOT NULL DEFAULT FALSE
notification_preferences_enabled   BOOLEAN NOT NULL DEFAULT FALSE
web_push_reminders_enabled         BOOLEAN NOT NULL DEFAULT FALSE
event_preparation_enabled          BOOLEAN NOT NULL DEFAULT FALSE
```

Il valore predefinito è `FALSE`: nessuna funzionalità opzionale viene concessa implicitamente a un nuovo tenant. La creazione richiede quindi un'abilitazione esplicita da parte del super amministratore, oltre alla disponibilità applicativa e alle eventuali dipendenze.

La migration non deve sovrascrivere i valori `finance_enabled` e `inventory_enabled` già persistiti dai tenant esistenti: per queste colonne modifica soltanto il default dello schema. Le sei nuove colonne vengono invece aggiunte con `DEFAULT FALSE`, quindi i tenant esistenti partono con le nuove funzionalità disabilitate finché non vengono inclusi esplicitamente nel rollout.

Non è necessario modificare gli schemi tenant: la configurazione è globale rispetto all'istanza del tenant e risiede nel catalogo pubblico.

### Modello backend

Aggiungere i campi a:

- `Tenants`;
- `TenantsDTO`;
- mapping MapStruct;
- metodi `equals`, `hashCode` e `toString`.

I campi dell'entità devono essere non null e inizializzati a `false`. Durante la creazione il servizio normalizza a `false` eventuali valori null provenienti da vecchi client. Durante un aggiornamento, invece, un campo omesso da un client precedente conserva il valore persistito e non deve disabilitare accidentalmente una funzionalità.

### Modello frontend

Aggiungere a `Tenants`:

```typescript
financeEnabled: boolean = false;
inventoryEnabled: boolean = false;
onboardingImportEnabled: boolean = false;
externalCalendarFeedEnabled: boolean = false;
inventoryQrEnabled: boolean = false;
notificationPreferencesEnabled: boolean = false;
webPushRemindersEnabled: boolean = false;
eventPreparationEnabled: boolean = false;
```

I nuovi tenant nascono con tutti i flag configurati a `false`. La pagina di creazione può consentire al super amministratore di abilitarli esplicitamente prima del salvataggio; in assenza di scelta rimangono disabilitati.

## Gestione nella pagina di dettaglio tenant

Nella pagina di dettaglio aggiungere una sezione **Funzionalità disponibili** contenente checkbox PrimeNG binarie per tutti i flag tenant. Le checkbox sono raggruppate in:

- **Moduli principali**: Economia, Inventario, Onboarding e importazione iniziale, Feed calendario esterno e Preparazione evento;
- **Estensioni**: QR code inventario, Preferenze notifiche e Promemoria eventi Web Push.

Ogni checkbox usa `[binary]="true"`, un `inputId` stabile e una `label` semanticamente associata. Accanto al controllo viene mostrato lo stato della capacità applicativa: **Disponibile**, **Non disponibile nell'installazione** oppure **Richiede un'altra funzionalità**.

I controlli sono:

- Economia;
- Inventario;
- Onboarding e importazione iniziale;
- Feed calendario esterno;
- QR code inventario;
- Preferenze notifiche;
- Promemoria eventi Web Push;
- Preparazione evento.

Ogni checkbox deve avere un `inputId`, una label associata e una breve descrizione. Esempio:

> Disabilitando Economia verranno nascosti conti, movimenti, rendiconti e informazioni economiche degli eventi. I dati saranno conservati.

Il cambiamento viene salvato insieme agli altri dati del tenant. Se una o più funzionalità passano da attive a disattive, prima del salvataggio viene mostrata un'unica conferma che elenca per nome gli effetti e le dipendenze che diventeranno inefficaci. La riattivazione non richiede conferma.

Il valore tenant rimane modificabile anche se il kill switch globale è spento, permettendo di preparare un rollout. In questo caso la UI chiarisce che il flag salvato è un'abilitazione pianificata e che la funzionalità effettiva resta indisponibile.

Il salvataggio aggiorna atomicamente tutti i flag e sfrutta la versione dell'entità tenant per impedire sovrascritture concorrenti.

## API delle funzionalità correnti

Esporre un endpoint minimale per tutti gli utenti autenticati:

```http
GET /api/tenant-features/current
```

Esempio di risposta:

```json
{
  "tenantCode": "ORCHESTRA_A",
  "version": 12,
  "financeEnabled": true,
  "inventoryEnabled": true,
  "onboardingImportEnabled": true,
  "externalCalendarFeedEnabled": false,
  "inventoryQrEnabled": true,
  "notificationPreferencesEnabled": true,
  "webPushRemindersEnabled": false,
  "eventPreparationEnabled": true
}
```

Requisiti:

- il codice tenant è ricavato esclusivamente dall'autenticazione e dal `TenantContext`;
- il client non può indicare il tenant tramite parametro o header applicativo;
- la risposta non espone il resto dell'anagrafica del tenant;
- tenant inesistente, eliminato o non valido produce un errore e non abilita funzionalità per impostazione predefinita;
- la risposta può essere mantenuta in memoria dal frontend, ma non deve essere incorporata nel token.

I booleani di questa risposta rappresentano lo stato **effettivo**, non il solo valore persistito. Il dettaglio amministrativo del tenant continua invece a restituire i valori configurati, necessari per modificarli e per conservare un'abilitazione pianificata mentre il kill switch globale è spento.

### Capacità dell'installazione

La pagina amministrativa interroga inoltre:

```http
GET /api/tenant-features/capabilities
```

L'endpoint è riservato al super amministratore e restituisce, per ogni funzionalità opzionale, soltanto:

- `available`, calcolato dalla configurazione applicativa e dalla presenza dei prerequisiti tecnici;
- `reasonCode`, valorizzato quando non disponibile;
- l'elenco stabile delle dipendenze tenant.

Non vengono mai restituiti URL interni, chiavi VAPID, segreti, nomi di bean o altri dettagli di configurazione. Il frontend usa questi metadati solo per spiegare lo stato; il backend ricalcola comunque capacità e dipendenze su ogni richiesta protetta.

DTO suggerito:

```java
public record TenantFeaturesDTO(
    String tenantCode,
    Long version,
    boolean financeEnabled,
    boolean inventoryEnabled,
    boolean onboardingImportEnabled,
    boolean externalCalendarFeedEnabled,
    boolean inventoryQrEnabled,
    boolean notificationPreferencesEnabled,
    boolean webPushRemindersEnabled,
    boolean eventPreparationEnabled
) {}
```

## Controllo centralizzato nel backend

Introdurre:

```java
public enum TenantFeature {
    FINANCE,
    INVENTORY,
    ONBOARDING_IMPORT,
    EXTERNAL_CALENDAR_FEED,
    INVENTORY_QR,
    NOTIFICATION_PREFERENCES,
    WEB_PUSH_REMINDERS,
    EVENT_PREPARATION
}
```

e un servizio con operazioni equivalenti a:

```java
boolean isEnabled(TenantFeature feature);
void requireEnabled(TenantFeature feature);
```

`TenantFeatureService` deve:

1. ottenere il codice dal `TenantContext`;
2. cercare esattamente quel codice in `public.tenant`;
3. verificare che il tenant non sia eliminato;
4. leggere esclusivamente i flag necessari per la funzionalità richiesta e le sue dipendenze;
5. negare l'accesso in assenza di un contesto tenant valido.

Non deve utilizzare uno stato statico condiviso tra tenant. Può leggere la configurazione applicativa immutabile per applicare il kill switch globale, ma questa non sostituisce il flag del tenant.

### Dipendenze e stato effettivo

| Funzionalità | Capacità applicativa | Dipendenze tenant | Stato effettivo |
| --- | --- | --- | --- |
| `FINANCE` | sempre disponibile dopo il deploy | nessuna | `finance_enabled` |
| `INVENTORY` | sempre disponibile dopo il deploy | nessuna | `inventory_enabled` |
| `ONBOARDING_IMPORT` | `application.onboarding.enabled` | nessuna per il wizard; Economia e Inventario per le relative sezioni | globale AND `onboarding_import_enabled` |
| `EXTERNAL_CALENDAR_FEED` | `application.calendar-feed.enabled` | Calendario base | globale AND `external_calendar_feed_enabled` |
| `INVENTORY_QR` | `application.inventory.qr.enabled` e URL pubblico valido | `INVENTORY` | globale AND `inventory_qr_enabled` AND `inventory_enabled` |
| `NOTIFICATION_PREFERENCES` | `application.notification-preferences.enabled` | outbox generalizzata disponibile | globale AND `notification_preferences_enabled` |
| `WEB_PUSH_REMINDERS` | VAPID valido, Service Worker e dispatcher push disponibili | `NOTIFICATION_PREFERENCES` | capacità push AND `web_push_reminders_enabled` AND preferenze effettive |
| `EVENT_PREPARATION` | `application.event-preparation.enabled` | Calendario base | globale AND `event_preparation_enabled` |

Economia e Inventario non sono prerequisiti dell'intera Preparazione evento. Quando sono disabilitati, soltanto le aree dipendenti risultano non disponibili secondo il contratto `unavailableAreas` della specifica di preparazione.

Il backend calcola la matrice in un solo punto. Controller, scheduler, dispatcher e frontend non devono ricostruire autonomamente le dipendenze.

### Annotazione e interceptor

Per gli endpoint interamente appartenenti a un modulo è consigliata un'annotazione:

```java
@RequiresTenantFeature(TenantFeature.FINANCE)
```

Un interceptor MVC la verifica dopo `TenantContextInterceptor`. I normali controlli Spring Security sui ruoli restano invariati e vengono eseguiti prima dei controller.

Applicazioni:

| Endpoint | Funzionalità richiesta |
| --- | --- |
| `/api/finance/**` | `FINANCE` |
| `/api/inventory/**` | `INVENTORY` |
| `/api/user/inventory/**` | `INVENTORY` |
| creazione, validazione, applicazione e retry sotto `/api/onboarding/**` | `ONBOARDING_IMPORT` |
| `/api/calendar-feeds/**` | `EXTERNAL_CALENDAR_FEED` |
| `/api/admin/calendar-feeds/**` | `EXTERNAL_CALENDAR_FEED` |
| endpoint QR, etichette, scansione e guasti | `INVENTORY_QR` |
| `/api/notification-preferences/**` | `NOTIFICATION_PREFERENCES` |
| configurazione e pianificazione promemoria calendario | `WEB_PUSH_REMINDERS` |
| endpoint di preparazione amministrativi, personali, esterni ed economici | `EVENT_PREPARATION` |

I requisiti sono cumulativi: gli endpoint economici di Preparazione richiedono `EVENT_PREPARATION` e `FINANCE`; quelli QR richiedono `INVENTORY_QR`, che incorpora già la dipendenza da `INVENTORY`. Consultazione di esiti e rapporti Onboarding già esistenti, compensazione, retention e cleanup usano regole dedicate descritte più avanti e non vengono bloccati dall'annotazione generale.

Le operazioni interne non raggiunte tramite controller, in particolare job schedulati e dispatcher, devono chiamare esplicitamente `isEnabled` o `requireEnabled`.

### Risposta in caso di funzionalità disabilitata

Restituire `403 Forbidden` con un codice applicativo distinguibile dal normale errore di ruolo:

```json
{
  "message": "error.tenantFeature.inventory.disabled"
}
```

Codici previsti:

```text
error.tenantFeature.finance.disabled
error.tenantFeature.inventory.disabled
error.tenantFeature.onboardingImport.disabled
error.tenantFeature.externalCalendarFeed.disabled
error.tenantFeature.inventoryQr.disabled
error.tenantFeature.notificationPreferences.disabled
error.tenantFeature.webPushReminders.disabled
error.tenantFeature.eventPreparation.disabled
```

Il resolver anonimo del feed calendario e gli identificatori QR non restituiscono `403`: per non rivelare l'esistenza di token, tenant o oggetti rispondono `404` con lo stesso corpo usato per una credenziale inesistente. Gli endpoint autenticati di amministrazione e profilo usano invece il `403` applicativo distinguibile.

In una prima versione non è consigliata una cache backend: la lettura riguarda pochi booleani su una riga individuata da un codice univoco, mentre l'assenza di cache rende la modifica effettiva dalla richiesta successiva anche in un'installazione con più istanze applicative.

## Comportamento della funzionalità Economia

Quando `financeEnabled` è `false`:

- la voce **Economia** non compare nel menu;
- le rotte `/finance` e figlie non sono attivabili;
- il frontend non esegue chiamate tramite `FinanceService`;
- tutti gli endpoint `/api/finance/**` restituiscono `403`;
- conti, categorie, movimenti, trasferimenti, allegati, esercizi e rendiconti non sono utilizzabili;
- il job di riporto annuale salta il tenant;
- le notifiche con sorgente `FINANCE` non vengono mostrate o consegnate.

### Integrazione con il Calendario

Il Calendario rimane disponibile. Devono invece essere nascosti:

- il campo **Compenso** (`fee`);
- la sezione **Costi** (`costs`);
- la sezione **Consuntivo economico**;
- il pulsante **Registra movimento**;
- gli stessi campi nei dialog di creazione evento e nella gestione delle serie ricorrenti.

Il frontend non deve richiedere il riepilogo economico dell'evento quando Economia è disabilitata.

### Conservazione dei dati economici del Calendario

La sola omissione dei campi nel frontend non è sufficiente. Attualmente l'aggiornamento di un evento sostituisce la collezione dei costi; una richiesta priva dei campi economici potrebbe quindi cancellare involontariamente dati esistenti.

Quando Economia è disabilitata, il backend deve applicare queste regole:

- nella lettura, restituire `fee = null` e `costs = []`;
- nell'aggiornamento di un evento, ignorare `fee` e `costs` ricevuti e conservare i valori persistiti;
- nella creazione di un evento, forzare `fee = null` e `costs = []`;
- applicare le stesse regole ai template e alle occorrenze delle serie ricorrenti;
- non bloccare le modifiche non economiche, come data, luogo, descrizione, disponibilità e promemoria.

Alla riattivazione di Economia, i valori precedenti tornano visibili.

## Comportamento della funzionalità Inventario

Quando `inventoryEnabled` è `false`:

- la voce **Inventario** non compare nel menu;
- tutte le rotte `/inventory/**` sono bloccate;
- la card Inventario non compare nella dashboard personale o amministrativa;
- la sezione delle assegnazioni non compare nel dettaglio utente;
- il frontend non invoca `InventoryService` o `UserInventoryService`;
- gli endpoint amministrativi e personali dell'Inventario restituiscono `403`;
- il job delle scadenze inventariali salta il tenant;
- le notifiche con sorgente `INVENTORY` non vengono mostrate o consegnate.

Alla riattivazione tornano disponibili oggetti, assegnazioni, fotografie, riconsegne e report esistenti.

## Comportamento di Onboarding e importazione iniziale

Quando `onboardingImportEnabled` è `false`:

- la voce **Configurazione iniziale** e il collegamento dal dettaglio tenant non compaiono;
- non è possibile creare, caricare, validare, applicare o ritentare un job;
- gli endpoint di mutazione sotto `/api/onboarding/**` restituiscono `403`;
- i worker non acquisiscono nuovi job del tenant negli stati precedenti ad `APPLYING`;
- file, staging, rapporti e audit già presenti vengono conservati secondo la normale retention.

La disattivazione non deve interrompere un job che ha già iniziato `APPLYING`, la finalizzazione Keycloak o una compensazione: queste operazioni raggiungono uno stato terminale per evitare identità o dati parziali. Restano inoltre consentiti, con le normali autorizzazioni, la consultazione dell'esito, il download del rapporto e le attività tecniche di compensazione e cleanup dei job esistenti. Queste eccezioni non consentono di avviare una nuova importazione.

Le sezioni **Inventario**, **Categorie**, **Conti** e **Saldi iniziali** hanno anche dipendenze proprie:

- con Inventario disabilitato, template, contesto e validazione non propongono né accettano la sezione Inventario;
- con Economia disabilitata, non propongono né accettano Categorie, Conti e Saldi iniziali;
- Strumenti e Utenti restano importabili se Onboarding è effettivamente attivo.

Il preflight ripete il controllo subito prima dell'applicazione. Se una dipendenza viene disabilitata dopo la validazione, il job torna `INVALID` senza iniziare modifiche esterne. Alla riattivazione i job non terminali possono essere rivalidati esplicitamente; non ripartono automaticamente.

## Comportamento del Feed calendario esterno

Quando `externalCalendarFeedEnabled` è `false`:

- le UI personali e amministrative per creare, ruotare, revocare o elencare feed sono nascoste;
- gli endpoint autenticati di gestione restituiscono `403`;
- il download anonimo di qualsiasi token del tenant restituisce `404` come un token inesistente;
- token, sottoscrizioni, UID, sequence e tombstone restano conservati;
- nessun feed viene revocato e nessun segreto viene ruotato automaticamente.

Il lifecycle che aggiorna UID, sequence e tombstone degli eventi continua anche durante la disattivazione, così la prima lettura successiva alla riattivazione rappresenta lo stato corrente senza perdere cancellazioni. Cleanup, retention, revoche dovute a cancellazione utente o tenant e procedure GDPR continuano a operare.

Il resolver pubblico legge il tenant dal registro globale del digest e verifica il flag prima di aprire lo schema tenant. Il risultato negativo non deve distinguere tra token inesistente, tenant inattivo e funzionalità disabilitata.

## Comportamento dei QR code inventario

`inventoryQrEnabled` è una funzionalità figlia di Inventario. Quando il flag QR oppure Inventario è `false`:

- stampa etichette, scansione, resolver, rotazione, azioni rapide e gestione guasti QR non sono disponibili;
- pulsanti, selezione multipla, pagina mobile e criticità QR della dashboard non compaiono;
- gli endpoint autenticati restituiscono `403`, mentre il resolver basato su codice restituisce `404` uniforme;
- i codici pubblici, le rotazioni, le segnalazioni e il relativo storico restano persistiti;
- le notifiche originate esclusivamente dai guasti QR non vengono prodotte o consegnate.

La disattivazione non rende disponibile per riuso alcun codice e non altera le normali operazioni Inventario. Alla riattivazione le vecchie etichette valide tornano risolvibili; i codici revocati rimangono revocati.

Il kill switch applicativo continua a proteggere l'intera installazione e la validazione dell'URL pubblico. Un tenant non può superare una configurazione applicativa QR non valida o disabilitata.

## Comportamento delle Preferenze notifiche

Quando `notificationPreferencesEnabled` è `false`:

- la sezione di configurazione notifiche nel profilo e le relative azioni non compaiono;
- gli endpoint `/api/notification-preferences/**` restituiscono `403`;
- profili, preferenze di categoria, ore silenziose, pause e digest persistiti non vengono cancellati;
- il fan-out continua a usare l'outbox generalizzata, senza riattivare la consegna sincrona legacy;
- le notifiche `REQUIRED` e la normale cronologia in-app continuano a essere consegnate.

Il resolver ignora temporaneamente le preferenze personalizzate e applica il profilo di compatibilità: notifiche in-app abilitate, nessun nuovo push generico configurabile, nessun digest e nessuna ora silenziosa. In questo modo disabilitare la pagina delle preferenze non equivale a spegnere il sistema di notifiche.

Alla riattivazione tornano effettive le preferenze precedentemente salvate soltanto per nuovi fan-out. Le consegne già decise non vengono ricostruite e i digest scaduti non vengono inviati in ritardo.

## Comportamento dei Promemoria eventi Web Push

`webPushRemindersEnabled` controlla esclusivamente i promemoria push legati agli eventi; non spegne il trasporto Web Push condiviso usato da altre categorie.

Quando il flag è `false` o Preferenze notifiche non è effettivamente disponibile:

- profilo e dettaglio evento non mostrano impostazioni relative ai promemoria push;
- creazione, aggiornamento e disponibilità degli eventi ignorano i campi push ricevuti, conservando i valori persistiti sulle entità esistenti;
- non vengono pianificati nuovi promemoria per il tenant;
- i job pendenti vengono marcati `SKIPPED` con motivo tecnico `FEATURE_DISABLED`, non mantenuti in backlog per una consegna tardiva;
- sottoscrizioni browser e credenziali del dispositivo restano conservate perché possono servire ad altri push;
- cleanup degli endpoint scaduti, retention e GDPR continuano a essere eseguiti.

La riattivazione ricalcola soltanto i promemoria futuri ancora utili e idempotenti, senza recuperare finestre già trascorse. La capacità effettiva resta falsa se VAPID, Service Worker o dispatcher push non sono disponibili nell'installazione.

## Comportamento della Preparazione evento

Quando `eventPreparationEnabled` è `false`:

- il workspace, le sezioni, gli indicatori e le azioni di preparazione scompaiono dal dettaglio evento;
- gli endpoint amministrativi, personali, esterni ed economici di preparazione restituiscono `403`;
- la dashboard operativa non interroga il provider di preparazione;
- lo scheduler non produce promemoria o follow-up di preparazione per il tenant;
- programma, configurazione, materiali e conferme restano nelle relative tabelle.

Calendario, disponibilità, presenze, tracce, spartiti, Inventario ed Economia continuano a funzionare secondo i rispettivi flag. Se Preparazione è attiva ma Economia o Inventario sono disabilitati, l'aggregatore usa `unavailableAreas`: un'area obbligatoria diventa `UNKNOWN`, mentre le altre aree restano utilizzabili. Non vengono eseguite chiamate ai moduli disabilitati.

Alla riattivazione gli hash di conferma vengono rivalutati sui dati correnti. Taurus non forza una conferma precedente a tornare valida e non genera retroattivamente notifiche la cui scadenza è trascorsa.

## Protezione dei file

Gli endpoint specifici di fotografie e allegati sono coperti dal controllo del rispettivo modulo. Va tuttavia verificato anche l'accesso generico a `/api/media/**`, che oggi consente agli utenti privilegiati di leggere i media del tenant.

Prima di restituire un file, il backend deve determinare se il media è collegato a entità Economia o Inventario:

- se è collegato esclusivamente a un modulo disabilitato, l'accesso viene negato;
- se è collegato anche a un modulo ancora accessibile, valgono le normali autorizzazioni di quel modulo;
- non bisogna affidarsi soltanto al prefisso dello `storageKey`: la relazione nel database è la fonte autorevole.

Le procedure GDPR, la pulizia degli orfani e le attività di conservazione devono continuare a elaborare i media anche quando il relativo modulo è disabilitato.

## Sincronizzazione del frontend

Introdurre un `TenantFeatureService` singleton, preferibilmente basato su signal Angular, che esponga:

```typescript
financeEnabled: Signal<boolean>;
inventoryEnabled: Signal<boolean>;
onboardingImportEnabled: Signal<boolean>;
externalCalendarFeedEnabled: Signal<boolean>;
inventoryQrEnabled: Signal<boolean>;
notificationPreferencesEnabled: Signal<boolean>;
webPushRemindersEnabled: Signal<boolean>;
eventPreparationEnabled: Signal<boolean>;
loaded: Signal<boolean>;
refresh(force?: boolean): Observable<TenantFeatures>;
```

Comportamento:

- caricamento subito dopo l'autenticazione e prima di mostrare le parti condizionali del layout;
- aggiornamento ogni 60 secondi, solo quando la scheda è visibile;
- aggiornamento quando la scheda torna in primo piano, se lo stato è scaduto;
- aggiornamento durante la navigazione, se sono trascorsi almeno 60 secondi dall'ultima lettura;
- una sola richiesta concorrente condivisa tra menu, guard e componenti;
- aggiornamento immediato dopo il salvataggio del tenant se il tenant modificato coincide con quello della sessione corrente.

Durante il caricamento iniziale, gli elementi soggetti a feature devono rimanere nascosti per evitare che appaiano brevemente e poi scompaiano.

Non è necessario usare `localStorage`: una nuova scheda può leggere nuovamente la configurazione, mentre una copia persistente rischierebbe di mostrare dati obsoleti o appartenenti a un tenant selezionato in precedenza.

## Route guard e componenti frontend

Creare un `tenantFeatureGuard` che legga dalla route:

```typescript
data: { feature: TenantFeature.INVENTORY }
```

Usarlo sulle route lazy-loaded `/inventory` e `/finance`, insieme al guard dei ruoli. Il guard deve attendere il primo caricamento della configurazione e reindirizzare alla dashboard quando la feature non è disponibile.

Principali punti di applicazione:

| Area frontend | Regola |
| --- | --- |
| Menu | Filtrare Economia e Inventario prima di costruire il modello |
| Dashboard | Non creare la card Inventario e non avviare le sue richieste |
| Dettaglio utente | Non creare `InventoryAssignmentsComponent` |
| Dettaglio evento | Nascondere compenso, costi e consuntivo |
| Dialog nuovo evento | Nascondere il compenso |
| Route Economia/Inventario | Impedire anche l'accesso diretto tramite URL |
| Menu e dettaglio tenant | Nascondere collegamenti Onboarding quando non effettivo |
| Profilo e amministrazione feed | Nascondere gestione e URL del feed calendario |
| Inventario | Nascondere etichette, scansione, rotazione, azioni rapide e guasti QR |
| Profilo notifiche | Nascondere preferenze, digest, silenzioso e pausa |
| Profilo e dettaglio evento | Nascondere configurazione dei promemoria Web Push |
| Dettaglio evento e dashboard | Non creare workspace o provider di Preparazione evento |

## Modifica mentre l'utente è collegato

Non è richiesto logout/login.

Se un utente si trova già in una funzionalità appena disabilitata:

1. al successivo aggiornamento periodico il frontend aggiorna il menu e lo reindirizza alla dashboard;
2. se prima dell'aggiornamento prova a eseguire un'operazione, il backend risponde immediatamente `403`;
3. l'interceptor HTTP riconosce il codice `tenantFeature.*.disabled`, forza il refresh dei flag, mostra un messaggio specifico e torna alla dashboard.

Messaggio suggerito:

> La funzionalità Inventario non è disponibile per questa istanza.

Il normale `403` dovuto a un ruolo insufficiente continua invece a produrre il messaggio **Permesso negato**.

Per le funzionalità incorporate in una pagina condivisa, come Preparazione evento, Preferenze notifiche o QR inventario, il frontend rimuove soltanto la sezione interessata e annulla polling e richieste future. Reindirizza alla dashboard solo quando l'intera route è dedicata alla funzionalità, come Onboarding.

Una disattivazione dipendente produce lo stesso aggiornamento. Per esempio, spegnere Inventario rende immediatamente inefficace anche QR inventario pur conservando `inventoryQrEnabled = true` come configurazione pianificata.

Una richiesta già iniziata prima del commit della modifica potrebbe concludersi. Tutte le nuove richieste effettuate dopo il commit vengono bloccate.

## Notifiche

Il dominio possiede già le sorgenti `FINANCE` e `INVENTORY`, che devono essere utilizzate per il filtro.

Quando una funzionalità è disabilitata:

- la lista delle notifiche esclude la sorgente corrispondente;
- il conteggio delle notifiche non lette applica lo stesso filtro;
- le notifiche esistenti restano conservate nello schema tenant;
- gli eventi non ancora consegnati presenti nell'outbox vengono marcati come soppressi e non inviati;
- nessun nuovo evento applicativo del modulo dovrebbe essere generato, perché le relative operazioni sono già bloccate.

Per i nuovi flag valgono inoltre queste regole:

- le notifiche di Onboarding, QR inventario e Preparazione evento sono soppresse quando la funzionalità origine non è effettiva;
- disabilitare Preferenze notifiche non sopprime le notifiche: applica il profilo di compatibilità e conserva quelle obbligatorie;
- disabilitare Promemoria Web Push chiude come `SKIPPED` soltanto i job reminder interessati;
- la generalizzazione della consegna e il relativo scheduler restano attivi per tutti i tenant.

`NotificationSource` non è abbastanza granulare per applicare questi controlli: QR inventario condivide `INVENTORY`, mentre Preparazione evento condivide aree calendario e finanza. Introdurre quindi una `NotificationFeaturePolicy` centralizzata che associ i tipi di evento notificabili a un eventuale `TenantFeature`. Publisher e dispatcher la consultano rispettivamente prima dell'enqueue e prima della consegna. Gli eventi senza associazione continuano normalmente; non si aggiungono dispatcher alternativi e non si deduce la funzionalità analizzando testo o prefissi liberi.

Per gli eventi già `PENDING` al momento della disattivazione, il dispatcher applica nuovamente la policy e li marca con lo stato terminale di soppressione già previsto. La riattivazione non li rimette in coda.

Alla riattivazione possono ricomparire le notifiche già consegnate e conservate, mentre quelle esplicitamente soppresse nell'outbox non devono essere inviate in ritardo.

## Processi schedulati

`TenantSchemaRegistry` oggi restituisce tutti i tenant attivi. Per evitare query dinamiche sui nomi delle colonne, aggiungere metodi espliciti equivalenti a:

```java
List<String> findFinanceEnabledTenantCodes();
List<String> findInventoryEnabledTenantCodes();
List<String> findOnboardingImportEnabledTenantCodes();
List<String> findExternalCalendarFeedEnabledTenantCodes();
List<String> findInventoryQrEnabledTenantCodes();
List<String> findNotificationPreferencesEnabledTenantCodes();
List<String> findWebPushRemindersEnabledTenantCodes();
List<String> findEventPreparationEnabledTenantCodes();
```

Utilizzo:

- `FinanceRolloverScheduler` usa soltanto i tenant con Economia attiva;
- `InventoryExpirationNotificationScheduler` usa soltanto i tenant con Inventario attivo;
- i worker Onboarding acquisiscono nuovi job soltanto per tenant con Onboarding effettivo, fermo restando il completamento sicuro di applicazioni e compensazioni già iniziate;
- il resolver del feed applica il flag prima di aprire lo schema, mentre lifecycle, tombstone cleanup e revoche di sicurezza continuano su tutti i tenant attivi;
- dashboard e notifiche QR elaborano soltanto tenant con Inventario e QR effettivi;
- dispatcher di preferenze e digest applicano il profilo di compatibilità ai tenant con Preferenze disabilitate;
- lo scheduler Web Push non pianifica o consegna reminder evento per tenant con il flag disabilitato;
- lo scheduler Preparazione evento usa soltanto i tenant con Preparazione effettiva;
- scheduler generici, GDPR, retention e pulizia media continuano a usare tutti i tenant attivi.

## Isolamento tra tenant

L'isolamento è garantito da quattro regole:

1. il codice tenant viene ricavato dall'identità autenticata;
2. il controllo legge una sola riga di `public.tenant` tramite il codice esatto;
3. ogni operazione sui dati continua a usare lo schema selezionato dal `TenantContext`;
4. scheduler e processi trasversali iterano liste di tenant filtrate per funzionalità.

Esempio:

```text
Tenant A: Economia ON,  Inventario OFF
Tenant B: Economia ON,  Inventario ON
```

Una richiesta Inventario di A riceve `403`; la stessa richiesta autenticata sul tenant B continua a usare esclusivamente lo schema di B.

## Logging e audit

Ogni modifica dei flag deve produrre un log strutturato contenente:

- ID e codice del tenant modificato;
- valore precedente e nuovo valore di ciascun flag;
- identificativo dell'amministratore;
- data e ora;
- versione dell'entità.

Il log include anche l'elenco delle funzionalità figlie il cui stato effettivo cambia per dipendenza, senza fingere che il relativo valore persistito sia stato modificato.

Non devono essere registrati token o altri dati sensibili. I normali campi di audit del tenant continuano a indicare autore e data dell'ultima modifica.

## Piano di test

### Backend

- migrazione che conserva Economia e Inventario dei tenant esistenti, imposta a `false` le sei nuove colonne e modifica a `FALSE` tutti i default dello schema;
- creazione di un tenant con valori omessi da un vecchio client, verificando tutti i flag a `false`;
- aggiornamento da un vecchio client con campi omessi, verificando la conservazione dei valori persistiti;
- lettura delle funzionalità del tenant corrente;
- calcolo centralizzato tra kill switch globale, flag tenant e dipendenze;
- rifiuto in assenza di un tenant valido;
- `403` su ogni endpoint Economia e Inventario disabilitato;
- accesso invariato quando la funzionalità è attiva;
- test con tenant A disabilitato e tenant B abilitato nella stessa esecuzione;
- conservazione di `fee` e `costs` durante l'aggiornamento di un evento;
- conservazione dei dati nelle serie ricorrenti;
- protezione di allegati, fotografie e accesso media generico;
- esclusione del tenant dai job specifici;
- filtro coerente tra lista notifiche e conteggio non letti;
- esecuzione invariata di GDPR, retention e pulizia media;
- blocco di nuovi job Onboarding e completamento sicuro di `APPLYING` o compensazione;
- rifiuto delle sezioni Onboarding Economia/Inventario quando il modulo padre è disabilitato;
- `404` uniforme del feed pubblico disabilitato e conservazione di token, sequence e tombstone;
- QR inefficace quando Inventario è disabilitato e riutilizzo impossibile dei codici esistenti;
- profilo di compatibilità quando Preferenze notifiche è disabilitata;
- reminder Web Push pendenti marcati `SKIPPED` senza consegna tardiva;
- Preparazione evento assente, dati conservati e aree Economia/Inventario degradate indipendentemente;
- verifica che l'outbox generalizzata rimanga l'unico percorso con ogni combinazione di flag.

### Frontend

- caricamento e aggiornamento di `TenantFeatureService`;
- deduplicazione delle richieste concorrenti;
- menu con tutte le combinazioni dei flag;
- blocco delle route dirette;
- assenza della card Inventario e delle relative chiamate API;
- assenza dell'Inventario nel dettaglio utente;
- assenza dei campi economici nel Calendario e nei dialog;
- mancata chiamata a `FinanceService` quando Economia è disabilitata;
- aggiornamento periodico e al ritorno in primo piano;
- gestione specifica del `403` per feature disabilitata;
- conferma prima della disattivazione dal dettaglio tenant;
- rendering delle otto checkbox, stato della capacità applicativa e dipendenze;
- Onboarding nascosto e polling interrotto dopo la disattivazione;
- gestione feed nascosta senza esporre URL o token conservati;
- assenza di comandi QR e chiamate resolver quando QR o Inventario sono disabilitati;
- sezione Preferenze notifiche nascosta senza influire sul centro notifiche obbligatorio;
- impostazioni reminder nascoste senza disabilitare push di altre categorie;
- workspace Preparazione e relativo provider dashboard non creati quando disabilitati;
- combinazioni padre/figlio, compreso il ripristino automatico dell'efficacia del figlio quando il padre viene riattivato.

### Test di accettazione multi-tenant

1. Configurare A con Inventario disattivato e B con Inventario attivato.
2. Accedere contemporaneamente ai due tenant.
3. Verificare che A non mostri menu, card e pagine Inventario.
4. Verificare che una chiamata manuale di A riceva `403`.
5. Verificare che B continui a visualizzare e modificare i propri dati.
6. Riattivare Inventario per A e verificare la ricomparsa dei dati precedenti senza nuovo login.
7. Ripetere la matrice per Economia, includendo i dati economici del Calendario.
8. Ripetere attivazione, disattivazione e riattivazione per ciascuno dei sei nuovi flag, verificando che i dati precedenti ricompaiano.
9. Verificare `INVENTORY_QR = true` con Inventario disabilitato e `WEB_PUSH_REMINDERS = true` con Preferenze notifiche disabilitata.
10. Disabilitare Onboarding durante validazione e durante applicazione, verificando rispettivamente arresto sicuro e completamento atomico.
11. Verificare che un token feed del tenant disabilitato restituisca `404`, mentre lo stesso endpoint continui a funzionare per un altro tenant.
12. Verificare che la consegna generalizzata delle notifiche degli altri moduli non venga mai disattivata.

## Strategia di rilascio

Ordine consigliato:

1. aggiungere la migrazione pubblica con tutti i default a `FALSE`, preservando i valori Economia e Inventario già persistiti;
2. mantenere spenti i kill switch delle capacità non ancora rilasciate e configurare i tenant pilota;
3. distribuire il calcolo centralizzato delle dipendenze, gli endpoint e i controlli backend;
4. proteggere Calendario, file, notifiche, resolver pubblici, worker e scheduler;
5. distribuire servizio, guard e condizioni frontend;
6. eseguire i test multi-tenant e padre/figlio;
7. abilitare i kill switch globali e poi i tenant secondo il piano di rollout;
8. disattivare manualmente una funzionalità soltanto dopo aver verificato le regole per attività in corso e dati conservati.

Questo ordine mantiene compatibile il frontend precedente durante il rilascio. In caso di problemi operativi, riattivare i flag interessati ripristina immediatamente l'accesso senza dover recuperare o migrare dati.

## Criteri di accettazione

La funzionalità è completata quando:

- tutti gli otto flag sono modificabili dalla pagina di dettaglio tenant;
- menu, route, card e azioni collegate rispettano i flag;
- il backend impedisce ogni accesso diretto ai moduli disabilitati;
- il Calendario resta utilizzabile senza mostrare o alterare dati economici nascosti;
- la dashboard e il dettaglio utente non interrogano Inventario quando è disabilitato;
- i processi schedulati e le notifiche rispettano la configurazione;
- una modifica diventa visibile agli utenti collegati entro 60 secondi, senza logout;
- i dati tornano disponibili alla riattivazione;
- la configurazione di un tenant non produce alcun effetto sugli altri tenant;
- kill switch globale, flag tenant e dipendenze producono uno stato effettivo unico e coerente tra backend e frontend;
- Onboarding non lascia job o identità in stato parziale quando viene disabilitato;
- Feed calendario e QR usano una risposta non enumerabile sui resolver basati su token o codice;
- disabilitare Preferenze notifiche non disabilita l'outbox né le notifiche obbligatorie;
- disabilitare Web Push reminder non spegne i push di altre categorie e non causa invii tardivi;
- Preparazione evento conserva dati e conferme, degradando separatamente le aree Economia e Inventario;
- `notification-delivery-generalization` non compare tra i controlli tenant e resta l'unico meccanismo di consegna.
