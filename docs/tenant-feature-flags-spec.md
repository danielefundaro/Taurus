# Funzionalità Economia e Inventario configurabili per tenant

## Obiettivo

Consentire a un super amministratore di attivare o disattivare le funzionalità **Economia** e **Inventario** dalla pagina di dettaglio del singolo tenant.

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

1. I flag sono memorizzati nella tabella pubblica `tenant`, non negli schemi dei singoli tenant.
2. I flag non sono inseriti nel token Keycloak, perché altrimenti sarebbe necessario rinnovare il token o ripetere il login per osservare le modifiche.
3. Il backend è sempre la fonte autorevole e verifica la funzionalità prima di eseguire un'operazione protetta.
4. Il frontend mantiene una copia in memoria della configurazione per decidere quali elementi mostrare.
5. Non viene aperto alcun canale WebSocket: il frontend aggiorna periodicamente la configurazione e reagisce comunque agli eventuali rifiuti del backend.
6. Disattivare una funzionalità non cancella, archivia o modifica i dati del relativo modulo.

## Modello dati

### Tabella `public.tenant`

Aggiungere tramite Liquibase:

```text
finance_enabled   BOOLEAN NOT NULL DEFAULT TRUE
inventory_enabled BOOLEAN NOT NULL DEFAULT TRUE
```

Il valore predefinito `TRUE` garantisce la compatibilità con i tenant e con i client già esistenti.

Non è necessario modificare gli schemi tenant: la configurazione è globale rispetto all'istanza del tenant e risiede nel catalogo pubblico.

### Modello backend

Aggiungere i campi a:

- `Tenants`;
- `TenantsDTO`;
- mapping MapStruct;
- metodi `equals`, `hashCode` e `toString`.

I campi dell'entità devono essere non null e inizializzati a `true`. Anche il servizio deve normalizzare eventuali valori null provenienti da vecchi client, soprattutto durante la creazione di un tenant.

### Modello frontend

Aggiungere a `Tenants`:

```typescript
financeEnabled: boolean = true;
inventoryEnabled: boolean = true;
```

I nuovi tenant devono quindi nascere con entrambe le funzionalità abilitate, salvo una futura diversa scelta commerciale.

## Gestione nella pagina di dettaglio tenant

Nella pagina di dettaglio aggiungere una sezione **Funzionalità disponibili** contenente due checkbox PrimeNG binarie:

- Economia;
- Inventario.

Ogni checkbox deve avere un `inputId`, una label associata e una breve descrizione. Esempio:

> Disabilitando Economia verranno nascosti conti, movimenti, rendiconti e informazioni economiche degli eventi. I dati saranno conservati.

Il cambiamento viene salvato insieme agli altri dati del tenant. Se una funzionalità passa da attiva a disattiva, prima del salvataggio viene mostrata una conferma che elenca gli effetti. La riattivazione non richiede conferma.

Il salvataggio aggiorna atomicamente entrambi i flag e sfrutta la versione dell'entità tenant per impedire sovrascritture concorrenti.

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
  "inventoryEnabled": false
}
```

Requisiti:

- il codice tenant è ricavato esclusivamente dall'autenticazione e dal `TenantContext`;
- il client non può indicare il tenant tramite parametro o header applicativo;
- la risposta non espone il resto dell'anagrafica del tenant;
- tenant inesistente, eliminato o non valido produce un errore e non abilita funzionalità per impostazione predefinita;
- la risposta può essere mantenuta in memoria dal frontend, ma non deve essere incorporata nel token.

DTO suggerito:

```java
public record TenantFeaturesDTO(
    String tenantCode,
    Long version,
    boolean financeEnabled,
    boolean inventoryEnabled
) {}
```

## Controllo centralizzato nel backend

Introdurre:

```java
public enum TenantFeature {
    FINANCE,
    INVENTORY
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
4. leggere esclusivamente il flag richiesto;
5. negare l'accesso in assenza di un contesto tenant valido.

Non deve utilizzare uno stato statico o una configurazione globale condivisa tra tenant.

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
```

In una prima versione non è consigliata una cache backend: la lettura riguarda due booleani su una riga individuata da un codice univoco, mentre l'assenza di cache rende la modifica effettiva dalla richiesta successiva anche in un'installazione con più istanze applicative.

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

## Modifica mentre l'utente è collegato

Non è richiesto logout/login.

Se un utente si trova già in una funzionalità appena disabilitata:

1. al successivo aggiornamento periodico il frontend aggiorna il menu e lo reindirizza alla dashboard;
2. se prima dell'aggiornamento prova a eseguire un'operazione, il backend risponde immediatamente `403`;
3. l'interceptor HTTP riconosce il codice `tenantFeature.*.disabled`, forza il refresh dei flag, mostra un messaggio specifico e torna alla dashboard.

Messaggio suggerito:

> La funzionalità Inventario non è disponibile per questa istanza.

Il normale `403` dovuto a un ruolo insufficiente continua invece a produrre il messaggio **Permesso negato**.

Una richiesta già iniziata prima del commit della modifica potrebbe concludersi. Tutte le nuove richieste effettuate dopo il commit vengono bloccate.

## Notifiche

Il dominio possiede già le sorgenti `FINANCE` e `INVENTORY`, che devono essere utilizzate per il filtro.

Quando una funzionalità è disabilitata:

- la lista delle notifiche esclude la sorgente corrispondente;
- il conteggio delle notifiche non lette applica lo stesso filtro;
- le notifiche esistenti restano conservate nello schema tenant;
- gli eventi non ancora consegnati presenti nell'outbox vengono marcati come soppressi e non inviati;
- nessun nuovo evento applicativo del modulo dovrebbe essere generato, perché le relative operazioni sono già bloccate.

Alla riattivazione possono ricomparire le notifiche già consegnate e conservate, mentre quelle esplicitamente soppresse nell'outbox non devono essere inviate in ritardo.

## Processi schedulati

`TenantSchemaRegistry` oggi restituisce tutti i tenant attivi. Per evitare query dinamiche sui nomi delle colonne, aggiungere metodi espliciti equivalenti a:

```java
List<String> findFinanceEnabledTenantCodes();
List<String> findInventoryEnabledTenantCodes();
```

Utilizzo:

- `FinanceRolloverScheduler` usa soltanto i tenant con Economia attiva;
- `InventoryExpirationNotificationScheduler` usa soltanto i tenant con Inventario attivo;
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

Non devono essere registrati token o altri dati sensibili. I normali campi di audit del tenant continuano a indicare autore e data dell'ultima modifica.

## Piano di test

### Backend

- migrazione con entrambi i flag attivi per i tenant esistenti;
- creazione di un tenant con valori omessi da un vecchio client;
- lettura delle funzionalità del tenant corrente;
- rifiuto in assenza di un tenant valido;
- `403` su ogni endpoint Economia e Inventario disabilitato;
- accesso invariato quando la funzionalità è attiva;
- test con tenant A disabilitato e tenant B abilitato nella stessa esecuzione;
- conservazione di `fee` e `costs` durante l'aggiornamento di un evento;
- conservazione dei dati nelle serie ricorrenti;
- protezione di allegati, fotografie e accesso media generico;
- esclusione del tenant dai job specifici;
- filtro coerente tra lista notifiche e conteggio non letti;
- esecuzione invariata di GDPR, retention e pulizia media.

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
- conferma prima della disattivazione dal dettaglio tenant.

### Test di accettazione multi-tenant

1. Configurare A con Inventario disattivato e B con Inventario attivato.
2. Accedere contemporaneamente ai due tenant.
3. Verificare che A non mostri menu, card e pagine Inventario.
4. Verificare che una chiamata manuale di A riceva `403`.
5. Verificare che B continui a visualizzare e modificare i propri dati.
6. Riattivare Inventario per A e verificare la ricomparsa dei dati precedenti senza nuovo login.
7. Ripetere la matrice per Economia, includendo i dati economici del Calendario.

## Strategia di rilascio

Ordine consigliato:

1. aggiungere la migrazione pubblica con flag predefiniti a `TRUE`;
2. distribuire endpoint e controlli backend;
3. proteggere Calendario, file, notifiche e scheduler;
4. distribuire servizio, guard e condizioni frontend;
5. eseguire i test multi-tenant;
6. disattivare manualmente le funzionalità soltanto dopo il completamento del rollout.

Questo ordine mantiene compatibile il frontend precedente durante il rilascio. In caso di problemi operativi, riattivare entrambi i flag ripristina immediatamente l'accesso senza dover recuperare o migrare dati.

## Criteri di accettazione

La funzionalità è completata quando:

- i due flag sono modificabili dalla pagina di dettaglio tenant;
- menu, route, card e azioni collegate rispettano i flag;
- il backend impedisce ogni accesso diretto ai moduli disabilitati;
- il Calendario resta utilizzabile senza mostrare o alterare dati economici nascosti;
- la dashboard e il dettaglio utente non interrogano Inventario quando è disabilitato;
- i processi schedulati e le notifiche rispettano la configurazione;
- una modifica diventa visibile agli utenti collegati entro 60 secondi, senza logout;
- i dati tornano disponibili alla riattivazione;
- la configurazione di un tenant non produce alcun effetto sugli altri tenant.
