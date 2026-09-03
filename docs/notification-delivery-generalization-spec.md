# Generalizzazione della consegna delle notifiche interne

## Stato del documento

- **Scopo**: evolutiva implementata nel backend e nel frontend.
- **Ambito**: backend, modello dati tenant, migrazione delle notifiche esistenti, osservabilità e verifiche frontend.
- **Comportamento utente**: invariato. Le notifiche rimangono interne all'applicazione; non vengono introdotti popup, e-mail o push.
- **Riferimento editoriale obbligatorio**: `docs/notification-editorial-style.md`.
- **Specifiche correlate**: `docs/financial-management-spec.md`, `docs/inventory-management-spec.md`, `docs/recurring-calendar-events-spec.md` e `docs/web-push-reminders-spec.md`.

## Obiettivo

Allineare tutte le notifiche interne di Taurus al meccanismo affidabile già utilizzato dall'area economica, sostituendo le consegne sincrone dirette con una outbox generalizzata.

Il risultato deve garantire per ogni ambito applicativo:

- composizione editoriale centralizzata nel backend;
- registrazione transazionale dell'intenzione di notifica;
- consegna asincrona e tenant-scoped;
- risoluzione uniforme dei destinatari;
- retry con attesa esponenziale;
- deduplicazione per evento e destinatario;
- tracciamento dello stato tecnico della consegna;
- mantenimento dell'attuale centro notifiche frontend.

## Problema attuale

Sono presenti due percorsi tecnici differenti.

### Notifiche economiche

```text
Operazione economica
    -> NoticesAspect compone la notifica
    -> NoticesService salva FinanceNotificationOutbox (PENDING)
    -> FinanceNotificationDispatcher risolve gli utenti
    -> viene creata una riga notices per destinatario
    -> evento DELIVERED
```

Questo percorso dispone di lock pessimista, retry, deduplicazione tramite `source_event_key`, isolamento per tenant e pulizia dell'outbox.

### Altre notifiche

```text
Operazione applicativa o scheduler
    -> NoticesAspect compone la notifica
    -> addNoticeToUser/addNotices... risolvono e salvano subito i destinatari
```

Questo secondo percorso è sincrono e non dispone di una outbox comune. Alcune funzionalità, come le scadenze dell'inventario, hanno protezioni idempotenti specifiche, ma non condividono un sistema uniforme di retry e tracciamento.

## Decisioni architetturali

| Tema | Decisione |
| --- | --- |
| Punto editoriale | `NoticesAspect` resta l'unico punto che definisce titolo, descrizione, severità, destinazione e pubblico |
| Logica di dominio | Service, controller e scheduler non compongono né consegnano direttamente notifiche |
| Persistenza iniziale | L'aspect invia un comando strutturato a un publisher che salva nell'outbox |
| Consegna | Un solo `NotificationDispatcher` generalizzato crea le righe `notices` |
| Canale | Solo notifica interna in-app |
| Toast frontend | Rimane una conferma immediata dell'operazione e non fa parte della pipeline di notifica |
| Destinatari | Supportati ruoli, singoli utenti e tutti gli utenti attivi del tenant |
| Autore destinatario | L'autore non viene escluso automaticamente |
| Risoluzione ruoli | Avviene al momento della consegna usando gli utenti attivi in quel momento |
| Deduplicazione | Unicità di `source_event_key` e `user_id` nella tabella `notices` |
| Retry | Esponenziale da 1 minuto, massimo 60 minuti tra i tentativi |
| Numero tentativi | Illimitato per default, per non perdere notifiche; limite configurabile |
| Retention outbox | Eventi consegnati eliminati dopo 30 giorni |
| Multi-tenancy | Outbox e notifiche rimangono nello schema del tenant destinatario |
| Compatibilità | Migrazione incrementale senza duplicare le consegne |

## Terminologia

- **Evento di notifica**: riga tecnica nell'outbox che descrive una notifica ancora da distribuire o già distribuita.
- **Notifica utente**: riga della tabella `notices`, visibile a un singolo destinatario.
- **Pubblico**: insieme di selettori che identifica i destinatari, per ruolo, utente o intero tenant.
- **Publisher**: componente che valida e salva nell'outbox un comando già composto.
- **Dispatcher**: componente che legge l'outbox, risolve i destinatari e crea le notifiche utente.
- **Evento di dominio**: operazione applicativa intercettata dall'aspect. Non coincide con un evento di calendario.

## Architettura di destinazione

```text
                     stessa transazione del dominio
                                  |
Operazione -> NoticesAspect -> NotificationOutboxPublisher
                                  |
                                  v
                         notification_outbox
                                  |
                    scheduler tenant-scoped
                                  |
                                  v
                       NotificationDispatcher
                         /                 \
              RecipientResolver       NoticesService
                         \                 /
                          -> notices per utente
```

### `NoticesAspect`

Responsabilità:

- intercettare esclusivamente operazioni completate con successo;
- acquisire gli snapshot necessari prima di eliminazioni o cambi di stato;
- applicare `docs/notification-editorial-style.md`;
- costruire un `NotificationCommand` completo;
- generare o fornire una chiave evento idempotente;
- inviare il comando al publisher.

Non deve:

- interrogare Keycloak per risolvere i destinatari;
- iterare sugli utenti;
- scrivere direttamente nella tabella `notices`;
- implementare retry o aggiornare lo stato dell'outbox.

L'aspect può essere suddiviso internamente in compositori per ambito, per esempio `FinanceNoticeComposer`, `InventoryNoticeComposer` e `CalendarNoticeComposer`, purché `NoticesAspect` rimanga il punto applicativo unico che attiva la composizione e nessun testo venga spostato nei service di dominio.

### `NotificationOutboxPublisher`

Responsabilità:

- validare il comando;
- normalizzare titolo, messaggio, percorso, autore e pubblico;
- salvare evento e selettori del pubblico;
- partecipare obbligatoriamente alla transazione corrente per le operazioni nello stesso tenant;
- rifiutare comandi privi di destinatari o contenuto valido.

Metodo previsto:

```java
void enqueue(NotificationCommand command);
```

La propagazione transazionale deve essere `MANDATORY`. L'ordine degli advisor Spring deve garantire che l'interceptor transazionale avvolga `NoticesAspect`. Deve essere aggiunto un test di integrazione che verifichi `TransactionSynchronizationManager.isActualTransactionActive()` durante l'enqueue e che un rollback del dominio elimini anche la riga di outbox.

### `NotificationRecipientResolver`

Responsabilità:

- risolvere tutti i selettori del pubblico;
- considerare soltanto utenti tenant attivi per `ROLE` e `ALL_ACTIVE_USERS`;
- includere i Super Admin ottenuti da Keycloak quando è richiesto `ROLE_SUPER_ADMIN`;
- accettare un destinatario diretto `USER` anche quando non deriva da una query per ruolo, preservando le notifiche personali dell'inventario;
- unire i risultati in un `LinkedHashSet`, eliminando duplicati tra ruoli e selettori;
- scartare identificativi nulli o vuoti.

La risoluzione rimane dinamica: un ruolo assegnato o rimosso prima della consegna influenza i destinatari effettivi. Questa scelta evita chiamate a Keycloak nella transazione di dominio e conserva il comportamento della finanza.

### `NotificationDispatcher`

Responsabilità:

- estrarre fino a `batch-size` eventi pronti per tenant;
- acquisire un lock pessimista sul singolo evento;
- ignorare eventi non più `PENDING` o non ancora pronti;
- risolvere il pubblico;
- creare una notifica per ogni destinatario;
- segnare l'evento `DELIVERED` soltanto dopo il completamento dell'intero fan-out;
- registrare tentativi ed errore sanificato in caso di fallimento;
- eliminare gli eventi consegnati oltre la retention.

Il dispatcher non modifica titolo, messaggio, severità, destinazione o pubblico.

### `NoticesService`

Responsabilità di destinazione:

- gestione CRUD delle notifiche dell'utente;
- inserimento idempotente di una notifica già composta per uno specifico utente;
- conteggio, lettura e cancellazione logica delle notifiche.

Metodo tecnico previsto:

```java
void addNoticeToUser(NotificationDelivery delivery);
```

I metodi sincroni `addNoticesSuperAdmins`, `addNoticesAdmins`, `addNoticesExcludeRoleUsers`, `addNoticeWholeTenant`, `addNoticeOnlyRoleUsers` e i due overload legacy di `addNoticeToUser` devono essere deprecati nella prima fase e rimossi dopo la migrazione di tutti i chiamanti.

## Modello del comando

```java
record NotificationCommand(
    String eventKey,
    NotificationSource source,
    String aggregateType,
    String aggregateId,
    String operation,
    String title,
    String message,
    NotificationSeverity severity,
    String targetPath,
    String actorId,
    String actorDisplayName,
    Set<NotificationAudience> audiences,
    String targetTenantCode
) {}
```

`targetTenantCode` è normalmente assente e indica il tenant corrente. È valorizzato soltanto per le notifiche relative alla gestione dei tenant, che oggi usano `addNoticesSuperAdminsForTenant`.

```java
record NotificationAudience(
    NotificationAudienceType type,
    String value
) {}
```

Valori di `NotificationAudienceType`:

- `ROLE`: `value` contiene un valore valido di `RoleEnum`;
- `USER`: `value` contiene il Keycloak user ID;
- `ALL_ACTIVE_USERS`: `value` è `*`.

Non viene introdotto un selettore di esclusione. I pubblici esistenti sono tutti esprimibili come unione di inclusioni.

### Sorgenti

Enum iniziale `NotificationSource`:

- `GENERAL`, riservato a notifiche legacy non ancora classificate;
- `CONTENT`, per album, tracce e strumenti;
- `CALENDAR`, per eventi, disponibilità e presenze;
- `IDENTITY`, per utenti;
- `TENANT`, per operazioni sui tenant;
- `INVENTORY`, per oggetti, assegnazioni, fotografie, riconsegne e scadenze;
- `FINANCE`, per l'area economica.

L'enum sostituisce le stringhe libere nel backend. Nel database resta memorizzato come stringa.

### Severità

`FinanceNotificationSeverity` deve diventare `NotificationSeverity` e spostarsi in un package non finanziario. Valori iniziali:

- `INFO`;
- `SUCCESS`;
- `WARNING`.

La migrazione non cambia i valori presenti nel database né la resa frontend.

### Chiave evento

`eventKey` identifica una singola intenzione editoriale e deve essere unica all'interno del tenant.

Regole:

- ogni retry conserva la stessa chiave;
- pubblici differenti con testi differenti generano eventi e chiavi differenti;
- operazioni già dotate di `requestKey`, revisione o identificatore schedulato usano una chiave deterministica;
- aggiornamenti ripetibili includono la versione dell'aggregato o un identificatore dell'operazione;
- quando non esiste una base idempotente stabile viene generato un UUID;
- la chiave non contiene dati personali o testo della notifica;
- la lunghezza massima rimane 160 caratteri; le chiavi composte troppo lunghe usano un hash SHA-256.

Esempi:

```text
finance:movement:42:created:request-8f...
inventory:assignment:91:revision:4:user
inventory:assignment:91:revision:4:admins
calendar:event:120:availability:user-uuid:accepted
```

La deduplicazione dell'outbox evita la pubblicazione ripetuta dello stesso evento; l'indice su `notices(source_event_key, user_id)` evita una seconda consegna allo stesso utente.

## Modello dati

### Tabella `notification_outbox`

La tabella generalizza `finance_notification_outbox` e mantiene i campi esistenti:

| Colonna | Tipo | Note |
| --- | --- | --- |
| `id` | `BIGSERIAL` | PK |
| `event_key` | `VARCHAR(160)` | Univoca per tenant |
| `source` | `VARCHAR(32)` | Nuova; `FINANCE` per i record esistenti |
| `aggregate_type` | `VARCHAR(64)` | Tipo logico dell'entità |
| `aggregate_id` | `VARCHAR(160)` | Generalizzato da `BIGINT` per supportare UUID e codici |
| `operation` | `VARCHAR(64)` | Codice stabile dell'operazione |
| `title` | `VARCHAR(255)` | Testo già composto |
| `message` | `VARCHAR(255)` | Testo già composto |
| `severity` | `VARCHAR(16)` | `INFO`, `SUCCESS`, `WARNING` |
| `target_path` | `VARCHAR(500)` | Percorso interno opzionale |
| `actor_id` | `VARCHAR(255)` | ID tecnico autore o `system` |
| `actor_display_name` | `VARCHAR(255)` | Nome leggibile |
| `occurred_at` | `TIMESTAMPTZ` | Istante dell'operazione |
| `status` | `VARCHAR(16)` | `PENDING`, `DELIVERED`, `FAILED` |
| `attempts` | `INTEGER` | Numero dei tentativi falliti |
| `next_attempt_at` | `TIMESTAMPTZ` | Prossima consegna ammessa |
| `delivered_at` | `TIMESTAMPTZ` | Valorizzato alla consegna completa |
| `last_error` | `VARCHAR(1000)` | Errore sanificato |
| campi audit | esistenti | Come `TenantAuditedEntity` |

Indici e vincoli:

- unique su `event_key`;
- indice parziale su `(next_attempt_at, id)` per record `PENDING` non eliminati;
- check su severità, stato e `attempts >= 0`;
- `title`, `message`, `source`, `operation`, autore e data obbligatori.

### Tabella `notification_outbox_audience`

Nuova tabella normalizzata:

| Colonna | Tipo | Note |
| --- | --- | --- |
| `id` | `BIGSERIAL` | PK |
| `notification_event_id` | `BIGINT` | FK con `ON DELETE CASCADE` |
| `audience_type` | `VARCHAR(32)` | `ROLE`, `USER`, `ALL_ACTIVE_USERS` |
| `audience_value` | `VARCHAR(255)` | Ruolo, user ID o `*` |

Vincoli:

- unique su `(notification_event_id, audience_type, audience_value)`;
- check sui tipi ammessi;
- valore sempre non nullo e non vuoto.

La normalizzazione è preferita a CSV o JSON perché consente validazione, migrazione controllata e assenza di parsing fragile. La vecchia colonna `recipient_roles` viene rimossa soltanto nella fase di contract.

### Tabella `notices`

Si conservano:

- `source`;
- `severity`;
- `target_path`;
- `source_event_key`;
- indice univoco parziale su `(source_event_key, user_id)`.

Tutte le nuove notifiche, non soltanto quelle finanziarie, devono valorizzare questi campi. Le righe legacy con `source = GENERAL` restano valide e non richiedono backfill editoriale.

## Mappatura dei destinatari legacy

| Metodo attuale | Pubblico generalizzato |
| --- | --- |
| `addNoticesSuperAdmins` | `ROLE_SUPER_ADMIN` |
| `addNoticesSuperAdminsForTenant` | `ROLE_SUPER_ADMIN` con `targetTenantCode` |
| `addNoticesAdmins` | `ROLE_ADMIN`, `ROLE_SUPER_ADMIN` |
| `addNoticesExcludeRoleUsers` | `ROLE_ADMIN`, `ROLE_SUPER_ADMIN`, `ROLE_ARCHIVIST` |
| `addNoticeWholeTenant` | `ALL_ACTIVE_USERS`, `ROLE_SUPER_ADMIN` |
| `addNoticeOnlyRoleUsers` | `ROLE_USER`, `ROLE_USER_EXTERNAL` |
| `addNoticeToUser(userId, ...)` | `USER(userId)` |
| Finanza | `ROLE_ADMIN`, `ROLE_SUPER_ADMIN`, `ROLE_TREASURER` |

`addNoticesExcludeRoleUsers` ha un nome fuorviante: durante la migrazione non deve essere riprodotto come esclusione, ma come l'insieme positivo dei tre ruoli attualmente utilizzati.

Quando un'unica operazione produce testi diversi per pubblici diversi, vengono creati eventi distinti. Esempi:

- pubblicazione di album, traccia o evento: un evento per utenti e uno per ruoli amministrativi se i testi o i percorsi divergono;
- completamento riconsegna: una notifica personale all'assegnatario e una amministrativa;
- revisione inventario: evento personale distinto dall'eventuale evento amministrativo.

## Ambiti da migrare

### Contenuti

- creazione, aggiornamento, pubblicazione, rimozione dalla pubblicazione ed eliminazione di album;
- creazione, aggiornamento, upload, pubblicazione, rimozione dalla pubblicazione ed eliminazione di tracce;
- creazione, aggiornamento ed eliminazione di strumenti.

Sorgente: `CONTENT`.

### Calendario

- creazione, aggiornamento, pubblicazione, rimozione dalla pubblicazione ed eliminazione di eventi;
- disponibilità confermata, rifiutata o annullata;
- aggiornamento delle presenze.

Sorgente: `CALENDAR`.

### Identità e tenant

- creazione, aggiornamento ed eliminazione di utenti;
- creazione, aggiornamento ed eliminazione di tenant.

Sorgenti: `IDENTITY` e `TENANT`.

Le operazioni tenant sono il solo caso cross-tenant. L'evento deve essere salvato nello schema del tenant indicato da `targetTenantCode`, dopo che lo schema è disponibile. Questa enqueue non può essere resa atomicamente dipendente da una transazione appartenente a uno schema differente: deve usare `TenantTransactionExecutor`, essere testata separatamente e registrare chiaramente eventuali errori. Non si deve dichiarare una garanzia atomica cross-tenant inesistente.

### Inventario

- oggetti, assegnazioni, revisioni e prese visione;
- richieste e completamenti di riconsegna;
- fotografie, ordine e anteprima;
- notifiche di scadenza.

Sorgente: `INVENTORY`.

La tabella `inventory_expiration_notice` continua a rappresentare la regola di business “questa scadenza è stata elaborata”. La consegna in-app viene però affidata all'outbox. Il salvataggio del marker e dell'evento di outbox deve essere atomico nello stesso tenant.

### Finanza

Tutto il comportamento e il contenuto esistenti devono rimanere invariati. Cambiano soltanto nomi e componenti tecnici generali:

- `FinanceNotificationOutbox` -> `NotificationOutbox`;
- `FinanceNotificationStatus` -> `NotificationStatus`;
- `FinanceNotificationSeverity` -> `NotificationSeverity`;
- `FinanceNotificationRecipientService` -> `NotificationRecipientResolver`;
- `FinanceNotificationDispatcher` -> `NotificationDispatcher`;
- `FinanceNotificationScheduler` -> `NotificationScheduler`;
- `FinanceNoticeCommand` -> `NotificationCommand`.

## Transazioni e consistenza

### Operazioni nello stesso tenant

L'operazione di dominio e l'inserimento nell'outbox devono appartenere alla stessa transazione:

- se il dominio va in rollback, non resta alcun evento;
- se l'enqueue fallisce, anche l'operazione di dominio va in rollback;
- il dispatcher non vede l'evento prima del commit.

Non è sufficiente affidarsi implicitamente all'ordine degli aspect. Occorre configurare e testare l'ordine tra advisor transazionale e `NoticesAspect`, e usare `Propagation.MANDATORY` nel publisher.

### Consegna

La consegna di un evento avviene in una transazione tenant dedicata:

1. lock dell'evento;
2. verifica stato e `next_attempt_at`;
3. risoluzione del pubblico;
4. inserimento idempotente delle notifiche;
5. aggiornamento a `DELIVERED`.

Se un errore causa il rollback, le righe create nello stesso tentativo non restano parzialmente persistite. L'indice univoco protegge comunque dai duplicati in caso di retry, concorrenza o confini transazionali inattesi.

## Retry e stati

### `PENDING`

Stato iniziale. L'evento è pronto quando `next_attempt_at <= now`.

### `DELIVERED`

Tutti i destinatari risolti hanno una notifica persistita. Vengono valorizzati `delivered_at` e audit; `last_error` viene azzerato.

### `FAILED`

Usato soltanto quando:

- è configurato un numero massimo di tentativi e viene superato;
- un evento legacy risulta strutturalmente non valido e non può essere corretto dal dispatcher.

La validazione del publisher deve impedire la creazione di nuovi eventi strutturalmente non validi.

### Politica di retry

```text
1, 2, 4, 8, 16, 32, 60, 60, ... minuti
```

Configurazione proposta:

```yaml
application:
  notifications:
    dispatch-delay: 5000
    batch-size: 100
    retry:
      initial-delay-minutes: 1
      max-delay-minutes: 60
      max-attempts: 0 # 0 = illimitato
    cleanup-cron: "0 30 3 * * *"
    outbox-retention-days: 30
```

L'errore memorizzato contiene classe e messaggio, è limitato a 1000 caratteri e non contiene stack trace. I log non devono riportare il testo della notifica o altri dati personali non necessari.

Se non esistono destinatari, l'evento rimane `PENDING`: un utente che riceve successivamente uno dei ruoli richiesti può ancora ricevere la notifica. Si applicano retry e limite configurato.

## Validazione

Il publisher deve verificare:

- `source`, `operation`, titolo, messaggio, autore e pubblico obbligatori;
- titolo e messaggio in testo semplice e nei limiti di 255 caratteri;
- `targetPath` assente oppure percorso interno che inizia con `/`;
- nessun URL esterno o schema come `javascript:`;
- ruoli appartenenti a `RoleEnum`;
- user ID non vuoti;
- almeno un selettore di pubblico;
- chiave evento non vuota e non superiore a 160 caratteri dopo la normalizzazione.

## Multi-tenancy

- scheduler e cleanup iterano esclusivamente sui tenant attivi;
- ricerca outbox, lock, risoluzione utenti e inserimento `notices` avvengono nello stesso contesto tenant;
- nessun ID destinatario ottenuto da un tenant viene riutilizzato nel ciclo di un altro tenant;
- cache eventuali del resolver devono includere il codice tenant nella chiave e avere durata limitata;
- i Super Admin Keycloak possono comparire in più tenant, ma ricevono una notifica distinta nello schema di ciascun tenant interessato;
- i test devono dimostrare che la stessa `eventKey` può esistere in tenant differenti senza collisioni cross-tenant.

## Frontend

L'API e il comportamento base del centro notifiche restano compatibili:

- polling del contatore ogni 30 secondi e al focus della finestra;
- elenco in dashboard;
- stato letto/non letto;
- navigazione tramite `targetPath` dopo la marcatura come letta;
- nessun popup automatico.

Adeguamenti raccomandati:

- sostituire il controllo speciale `source === 'FINANCE'` con una mappa centralizzata sorgente -> icona;
- usare un'icona supportata da PrimeIcons per ogni sorgente;
- mantenere un'icona generica per sorgenti sconosciute o legacy;
- non ricostruire titolo o descrizione nel frontend;
- conservare i toast di conferma delle operazioni, evitando di presentarli come notifiche distribuite.

Mappa iniziale proposta:

| Sorgente | PrimeIcon |
| --- | --- |
| `GENERAL` | `pi pi-bell` |
| `CONTENT` | `pi pi-file` |
| `CALENDAR` | `pi pi-calendar` |
| `IDENTITY` | `pi pi-users` |
| `TENANT` | `pi pi-building` |
| `INVENTORY` | `pi pi-box` |
| `FINANCE` | `pi pi-wallet` |

La disponibilità effettiva delle icone deve essere verificata contro la versione `primeicons` installata prima dell'implementazione.

## Migrazione Liquibase

La migrazione deve essere applicata tramite `tenant-master.xml` a ogni schema tenant.

### Fase expand

Il changeset `20260903000001-1-expand`, applicato per default, esegue questi passaggi:

1. aggiunge `source`, con default `FINANCE` per i record esistenti, generalizza `aggregate_id` e rinomina la tabella fisica in `notification_outbox`;
2. crea `notification_outbox_audience` e converte ogni valore CSV di `recipient_roles` in una riga `ROLE`;
3. conserva temporaneamente `recipient_roles` e pubblica la vista aggiornabile `finance_notification_outbox` per le istanze precedenti;
4. installa trigger bidirezionali che mantengono allineati i ruoli CSV legacy e le audience normalizzate.

La compatibilità è quindi valida in entrambe le direzioni durante un rolling deployment: il nuovo codice può scrivere audience normalizzate mentre il vecchio dispatcher continua a leggere e scrivere la vista finanziaria.

Durante questa fase deve esistere un solo scheduler di consegna. `FinanceNotificationScheduler` e `NotificationScheduler` non possono essere attivi contemporaneamente.

### Migrazione dei produttori

Migrare un ambito alla volta:

1. finanza, mantenendo invariati contenuti e destinatari;
2. inventario;
3. calendario;
4. contenuti;
5. identità e tenant.

Per ogni pointcut deve essere attivo un solo percorso: enqueue oppure consegna diretta. Non effettuare dual-write verso outbox e `notices`, perché produrrebbe notifiche duplicate con chiavi diverse.

### Fase contract

Dopo che tutte le istanze eseguono il codice generalizzato, applicare esplicitamente il changeset `20260903000001-2-contract` attivando il contesto Liquibase `notification-contract`. Il changeset rimuove vista, trigger e funzioni di compatibilità e infine la colonna `recipient_roles`. Il contesto non deve essere attivato finché può essere avviata un'istanza della versione precedente.

Prima del contract verificare il backfill e con `rg` l'assenza di riferimenti alle classi e ai metodi finanziari sostituiti.

Se il deployment non supporta una migrazione rolling, expand e contract possono essere accorpate durante una finestra di manutenzione, ma il backfill e la preservazione degli eventi `PENDING` restano obbligatori.

### Rollback

- prima della fase contract, il rollback applicativo può riattivare il dispatcher finanziario tramite la vista e `recipient_roles`;
- il rollback del contract ricrea la colonna, esegue il backfill inverso dalle audience e ripristina vista e trigger;
- il rollback dell'expand si interrompe esplicitamente se trova eventi non finanziari, audience non rappresentabili o identificativi non numerici, evitando perdita silenziosa di dati;
- non eliminare eventi `PENDING` durante upgrade o rollback;
- gli eventi `DELIVERED` possono seguire la retention ordinaria.

## Piano implementativo

### Backend — modello e infrastruttura

1. Creare package generale `domain.notification`.
2. Introdurre enum, entity, audience e command generali.
3. Implementare `NotificationOutboxPublisher` con validazione e transazione obbligatoria.
4. Implementare `NotificationRecipientResolver` generalizzando la logica finanziaria.
5. Implementare `NotificationDispatcher` e `NotificationScheduler`.
6. Generalizzare il metodo idempotente di `NoticesService`.
7. Spostare le proprietà da `application.finance.notification-*` a `application.notifications.*`, mantenendo alias temporanei se necessari.
8. Aggiungere la migrazione Liquibase expand.

### Backend — produttori

1. Sostituire `FinanceNoticeCommand` senza cambiare la semantica finanziaria.
2. Convertire le notifiche inventario, incluse quelle personali e schedulate.
3. Convertire disponibilità, presenze e ciclo editoriale degli eventi.
4. Convertire album, tracce e strumenti.
5. Convertire utenti e operazioni cross-tenant.
6. Deprecare e infine rimuovere le API sincrone di fan-out.

### Frontend

1. Centralizzare la mappa delle icone per sorgente.
2. Verificare destinazioni di navigazione per ogni ambito.
3. Conservare compatibilità con notifiche `GENERAL` già persistite.
4. Non aggiungere popup o richieste di permesso browser.

### Documentazione

1. Aggiornare `docs/notification-editorial-style.md` con il comando generalizzato.
2. Sostituire i riferimenti `FinanceNotification*` in `docs/financial-management-spec.md`.
3. Collegare questa specifica dalle specifiche di inventario, calendario e contenuti interessate.
4. Tenere separata la progettazione Web Push: un eventuale canale push futuro deve consumare la notifica canonica senza duplicare la composizione editoriale.

## Strategia di test

### Unit test

- validazione completa di `NotificationCommand`;
- normalizzazione e chiavi evento;
- mapping di ogni helper legacy verso il pubblico generalizzato;
- unione e deduplicazione di utenti con più ruoli;
- risoluzione `ROLE_SUPER_ADMIN` locale e Keycloak;
- destinatario `USER` diretto;
- calcolo retry e limite massimo;
- sanificazione e troncamento errori;
- transizioni `PENDING` -> `DELIVERED` e `PENDING` -> `FAILED` quando configurato.

### Test di integrazione backend

- commit del dominio crea una riga outbox;
- rollback del dominio non lascia alcuna riga outbox;
- publisher fuori transazione fallisce;
- dispatcher crea una sola notifica per destinatario;
- retry dopo consegna parziale non duplica notifiche;
- due dispatcher concorrenti non elaborano due volte lo stesso evento;
- eventi non ancora pronti non vengono estratti;
- nessun destinatario mantiene l'evento pendente;
- separazione completa tra due tenant;
- cleanup elimina solo outbox `DELIVERED` oltre soglia;
- retention delle normali notifiche rimane a 365 giorni;
- migrazione preserva eventi finanziari pendenti e consegnati;
- backfill CSV dei ruoli produce le audience corrette.

Usare PostgreSQL reale o Testcontainers per lock pessimista, indici parziali e vincoli; H2 non è sufficiente per queste verifiche.

### Test dell'aspect

Per ogni gruppo è sufficiente almeno un caso rappresentativo di creazione, aggiornamento e rimozione, oltre ai casi con destinatario personale:

- contenuti pubblici e non pubblici;
- disponibilità calendario;
- utente e tenant;
- assegnazione, revisione, riconsegna e scadenza inventario;
- movimento, trasferimento, allegato e riporto finanza.

Verificare titolo, messaggio, source, operation, targetPath, attore, audience ed eventKey. I test non devono limitarsi a verificare che `enqueue()` sia stato chiamato.

### Frontend

- icona corretta per ogni sorgente e fallback per valori sconosciuti;
- conteggio non lette invariato;
- click: marcatura come letta prima della navigazione;
- target finanziari e non finanziari;
- build Angular di produzione e test dei componenti coinvolti.

## Osservabilità

Le metriche Micrometer implementate, taggate per tenant e sorgente e, per gli eventi, anche per operazione, sono:

- `taurus.notifications.pending` e `taurus.notifications.oldest.pending.seconds`;
- `taurus.notifications.delivered`, `taurus.notifications.failed` e `taurus.notifications.retry.scheduled`;
- `taurus.notifications.attempts`;
- `taurus.notifications.delivery.latency`;
- `taurus.notifications.recipients`;
- `taurus.notifications.scheduler.duration`.

Log strutturati minimi:

- tenant;
- outbox event ID;
- event key o sua versione hashata;
- source e operation;
- tentativo e prossimo retry;
- classe dell'errore.

Non registrare messaggio completo, token, claim JWT o dati degli allegati.

Soglie operative iniziali:

- warning se il più vecchio `PENDING` supera 15 minuti;
- alert se supera 60 minuti;
- alert immediato per eventi `FAILED`;
- warning se un tenant non può essere enumerato o aperto dallo scheduler.

## Sicurezza e privacy

- il dispatcher opera soltanto su tenant ottenuti da `TenantSchemaRegistry`;
- `targetPath` ammette soltanto rotte interne;
- il contenuto resta testo semplice, senza HTML o Markdown;
- i ruoli sono enum validati e non input arbitrario del frontend;
- gli endpoint utente continuano a filtrare le notifiche tramite il `userId` autenticato;
- `last_error` non contiene stack trace o credenziali;
- l'outbox segue una retention più breve delle notifiche utente;
- nessuna informazione economica viene copiata in OpenSearch o in uno schema diverso.

## Criteri di accettazione

La generalizzazione è completa quando:

- esiste un solo dispatcher e un solo scheduler per tutte le notifiche interne;
- nessun pointcut applicativo esegue fan-out diretto verso `notices`;
- tutte le nuove notifiche valorizzano `source_event_key`, `source`, `severity` e, quando utile, `target_path`;
- retry e deduplicazione sono verificati con PostgreSQL;
- un rollback applicativo annulla anche l'evento outbox nello stesso tenant;
- un errore temporaneo di Keycloak non annulla un'operazione già registrata e non perde la notifica;
- utenti con più ruoli ricevono una sola notifica per evento;
- il comportamento finanziario non cambia;
- notifiche legacy restano leggibili;
- il frontend continua a mostrare elenco, badge e navigazione senza popup automatici;
- documentazione e nomi tecnici non contengono più responsabilità generali sotto il prefisso `FinanceNotification`.

## File e aree previste

### Da generalizzare o sostituire

```text
taurus-be/src/main/java/com/fundaro/zodiac/taurus/domain/finance/FinanceNotificationOutbox.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/domain/finance/FinanceNotificationStatus.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/domain/finance/FinanceNotificationSeverity.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/repository/finance/FinanceNotificationOutboxRepository.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/impl/FinanceNotificationRecipientService.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/impl/FinanceNotificationDispatcher.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/impl/FinanceNotificationScheduler.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/NoticesService.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/impl/NoticesServiceImpl.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/aop/notices/NoticesAspect.java
```

### Nuove aree suggerite

```text
taurus-be/src/main/java/com/fundaro/zodiac/taurus/domain/notification/
taurus-be/src/main/java/com/fundaro/zodiac/taurus/repository/notification/
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/notification/
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/impl/NotificationOutboxPublisher.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/impl/NotificationRecipientResolver.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/impl/NotificationDispatcher.java
taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/impl/NotificationScheduler.java
taurus-be/src/main/resources/config/liquibase/changelog/*_generalize_notification_outbox.xml
taurus-fe/src/app/service/notification-presentation.service.ts
```

## Ordine raccomandato di esecuzione

1. test di caratterizzazione del comportamento esistente;
2. migrazione Liquibase expand;
3. modello, publisher, resolver e dispatcher generali;
4. migrazione della finanza sul dispatcher generale;
5. verifica in staging di pending, retry e deduplicazione;
6. migrazione inventario;
7. migrazione calendario;
8. migrazione contenuti;
9. migrazione identità e tenant;
10. aggiornamento frontend delle icone;
11. rimozione del fan-out sincrono;
12. migrazione Liquibase contract;
13. aggiornamento delle specifiche collegate e smoke test multi-tenant.

Non devono essere migrate più sorgenti nello stesso passaggio senza aver prima verificato metriche, duplicati e navigazione della sorgente precedente.
