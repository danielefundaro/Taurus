# Preferenze notifiche granulari

La funzionalità estende il centro notifiche e i promemoria push esistenti. Si appoggia alla consegna affidabile descritta in [Generalizzazione della consegna delle notifiche interne](notification-delivery-generalization-spec.md), mantiene i testi conformi a [Stile editoriale delle notifiche interne](notification-editorial-style.md) e consolida il comportamento definito in [Promemoria eventi tramite Web Push](web-push-reminders-spec.md).

La gestione operativa degli eventi tecnici `FAILED` resta separata dalle preferenze personali ed è collegata alla [Dashboard operativa trasversale](operational-dashboard-spec.md).

Quando implementata, questa specifica sostituisce soltanto la decisione **Canale: solo notifica interna in-app** della generalizzazione precedente e il percorso push diretto dei promemoria. Composizione editoriale, outbox, destinatari e deduplicazione esistenti restano autorevoli; l'estensione multicanale entra in vigore dalla fase di rilascio che abilita la nuova coda push.

## Stato del documento

Specifica funzionale e tecnica proposta. Il documento definisce il comportamento atteso e le decisioni per una futura implementazione, ma non autorizza ancora lo sviluppo.

## Obiettivo

Consentire a ogni utente di decidere quali aggiornamenti ricevere, attraverso quale canale e in quale momento, senza compromettere affidabilità, isolamento tenant o notifiche obbligatorie.

La prima versione deve permettere di:

- abilitare o disabilitare le notifiche in-app per categoria;
- scegliere per ogni categoria push immediati, riepilogo giornaliero oppure nessun push;
- configurare l'anticipo predefinito dei promemoria calendario;
- definire ore silenziose ricorrenti e una pausa temporanea dei push;
- scegliere quante informazioni mostrare sullo schermo bloccato;
- rinviare una notifica con **Ricordamelo più tardi**;
- conservare una gestione tecnica separata per consegne fallite;
- applicare le preferenze nello schema del tenant corrente.

Le preferenze regolano la consegna, non la produzione degli eventi di dominio: `NoticesAspect` e `notification_outbox` continuano a registrare ogni intenzione editoriale valida.

## Decisioni principali

| Aspetto | Decisione |
| --- | --- |
| Ambito | preferenze distinte per utente e tenant |
| Categorie | derivate da `NotificationSource` |
| Centro in-app | immediato e individuale; non viene trasformato in digest |
| Push per categoria | `OFF`, `IMMEDIATE` o `DAILY_DIGEST` |
| Riepilogo giornaliero | un solo push aggregato; le righe in-app restano individuali |
| Promemoria evento | canale temporale separato, mai inserito nel digest |
| Ore silenziose | sospendono i push, non le notifiche in-app |
| Pausa temporanea | sospende tutti i push fino a un istante scelto |
| Snooze | nasconde temporaneamente una notifica dal conteggio e la ripropone come non letta |
| Notifiche obbligatorie | sempre in-app; non forzano il consenso push |
| Valutazione preferenze | per destinatario durante il fan-out dell'outbox |
| Modello dati | tabelle tipizzate, non nuove chiavi libere in `preferences` |
| Compatibilità iniziale | in-app attivo per tutte le categorie; push generici disattivati |
| Errori tecnici | console amministrativa separata dal profilo utente |

## Ambito della prima versione

### Compreso

- profilo notifiche tipizzato;
- preferenza in-app e modalità push per ogni sorgente;
- push immediati per le notifiche applicative;
- digest push giornaliero;
- fuso orario personale per quiet hours e digest;
- anticipo predefinito dei promemoria evento;
- ripianificazione dei promemoria che ereditano il valore personale;
- pausa push temporanea;
- anteprima push privata o completa;
- snooze e riattivazione anticipata delle notifiche;
- coda affidabile per le consegne push;
- metriche, retention, test e integrazione con la console `FAILED`.

### Fuori ambito

- e-mail, SMS, WhatsApp o altri canali;
- digest in-app che sostituisce o elimina le notifiche individuali;
- riepiloghi settimanali o pianificazioni diverse per giorno della settimana;
- regole per singola operazione, entità o evento oltre alla categoria;
- modelli di testo personalizzati dall'utente;
- priorità push scelta manualmente dall'autore;
- sincronizzazione delle impostazioni del sistema operativo o del browser;
- ricevute di lettura del push;
- garanzia di consegna quando il browser o il sistema operativo la impediscono;
- amministrazione centralizzata delle preferenze di un altro utente;
- modifica delle politiche obbligatorie tramite frontend;
- esposizione degli errori tecnici nella pagina profilo.

## Terminologia

- **Notifica applicativa**: evento composto dal backend e destinato a uno o più utenti.
- **Notifica in-app**: riga `notices` visibile nel centro notifiche Taurus.
- **Push**: messaggio Web Push inviato ai dispositivi registrati dell'utente.
- **Promemoria evento**: push temporale legato a un evento di calendario per cui l'utente è disponibile.
- **Digest**: singolo push che riepiloga più notifiche applicative.
- **Ore silenziose**: intervallo locale giornaliero durante il quale i push non devono comparire.
- **Pausa temporanea**: sospensione globale dei push fino a un istante preciso.
- **Snooze**: rinvio di una singola notifica già consegnata in-app.
- **Politica obbligatoria**: indicazione backend che impedisce di sopprimere la relativa riga in-app.

## Categorie

Le categorie corrispondono ai valori già persistiti in `NotificationSource`, con etichette presentate dal frontend:

| Sorgente | Etichetta | Esempi |
| --- | --- | --- |
| `CALENDAR` | Calendario | eventi, disponibilità, presenze |
| `INVENTORY` | Inventario | assegnazioni, riconsegne, scadenze |
| `FINANCE` | Economia | movimenti, categorie, riconciliazioni |
| `CONTENT` | Contenuti | album, tracce, spartiti, strumenti |
| `IDENTITY` | Utenti e accessi | utenti, ruoli e profili |
| `TENANT` | Organizzazione | configurazione e stato tenant |
| `GENERAL` | Generali | notifiche legacy o trasversali |

Il backend restituisce sempre l'enum e i default; il frontend associa etichette e icone senza reinterpretare le regole. Una sorgente sconosciuta introdotta da una versione backend successiva usa i default sicuri: in-app attivo e push `OFF`.

La UI può nascondere categorie non pertinenti ai ruoli correnti, ma le preferenze salvate non vengono cancellate quando un ruolo cambia. Il backend applica comunque autorizzazioni e destinatari prima delle preferenze.

## Canali e regole di consegna

### Centro notifiche in-app

Per ogni categoria l'utente sceglie `inAppEnabled`.

- `true`: la normale riga `notices` viene creata immediatamente dal dispatcher;
- `false`: una notifica configurabile non viene inserita nel centro;
- una notifica con politica `REQUIRED` viene sempre inserita, anche se la categoria è disattivata;
- ore silenziose, pausa push e digest non modificano mai la disponibilità in-app.

Disattivare una categoria vale soltanto per nuovi eventi elaborati dopo il salvataggio. Non elimina né nasconde retroattivamente notifiche già presenti.

### Push applicativi

Per ogni categoria l'utente sceglie `pushMode`:

| Modalità | Comportamento |
| --- | --- |
| `OFF` | non viene pianificato alcun push applicativo |
| `IMMEDIATE` | viene creato un job push appena il fan-out è persistito |
| `DAILY_DIGEST` | la notifica entra nel prossimo riepilogo locale |

La preferenza push è indipendente da `inAppEnabled`: un utente può tenere la cronologia in Taurus senza push, oppure ricevere un push senza conservare la notifica configurabile nel centro.

`pushMode` non abilita tecnicamente il dispositivo. Almeno una sottoscrizione Web Push valida deve esistere; altrimenti il job viene chiuso come `SKIPPED` senza retry. L'interfaccia spiega la differenza tra preferenza dell'account e autorizzazione del singolo browser.

### Politiche obbligatorie

`NotificationCommand` e `notification_outbox` ricevono il campo `preferencePolicy`:

- `CONFIGURABLE`: si applicano entrambe le scelte della categoria;
- `REQUIRED`: la riga in-app viene sempre creata, mentre i push continuano a rispettare modalità scelta, autorizzazione browser, ore silenziose e pausa.

`REQUIRED` non deriva automaticamente da `WARNING` o dalla categoria. Deve essere assegnato esplicitamente dal compositore per operazioni che riguardano sicurezza, accesso o obblighi legali. Tutte le operazioni già presenti al momento della migrazione restano `CONFIGURABLE`: la prima versione non promuove retroattivamente notifiche di calendario, inventario o finanza. L'allowlist iniziale è quindi vuota e potrà ricevere soltanto codici `operation` nuovi o esplicitamente revisionati; un codice non elencato resta `CONFIGURABLE`.

La politica non può essere impostata dal client e non trasforma un push in un canale garantito.

## Default e compatibilità

Quando non esiste ancora un profilo persistito, il backend restituisce questi default:

| Preferenza | Default |
| --- | --- |
| notifiche in-app | attive per tutte le categorie |
| push applicativi | `OFF` per tutte le categorie |
| promemoria evento | attivi |
| anticipo promemoria | 30 minuti |
| ore silenziose | disattivate |
| orario proposto | 22:00–07:00 |
| pausa temporanea | assente |
| orario digest | 08:00 |
| anteprima push | `PRIVATE` |
| timezone | timezone corrente del tenant |

Questi valori preservano il centro notifiche esistente e non iniziano a inviare nuove categorie push senza un consenso esplicito. Il default backend dei promemoria viene portato a 30 minuti per eliminare l'incoerenza attuale con il valore 30 già mostrato dal frontend.

## Promemoria degli eventi

I promemoria calendario rimangono distinti dalle notifiche applicative `CALENDAR`:

- non vengono inclusi nel digest;
- usano `eventRemindersEnabled` come interruttore globale personale;
- richiedono disponibilità `AVAILABLE` per la singola occorrenza;
- rispettano sottoscrizioni push, anteprima, ore silenziose e pausa temporanea;
- conservano la possibilità di impostare zero per disabilitare il singolo evento.

### Precedenza dell'anticipo

L'anticipo effettivo è risolto in questo ordine:

1. valore personale sull'occorrenza in `calendar_event_availability.reminder_minutes`;
2. valore dell'evento in `calendar_event.reminder_minutes`;
3. `defaultCalendarReminderMinutes` del profilo utente;
4. default applicativo, 30 minuti.

Il primo valore presente vince; zero disattiva il promemoria a quel livello. I valori ammessi restano da 0 a 1.440 minuti.

### Ripianificazione

`push_reminders` riceve `reminder_origin` con valori `PERSONAL`, `EVENT`, `PROFILE` o `APPLICATION` e un `schedule_revision` monotono.

Quando cambia il valore predefinito personale, il backend:

1. trova le occorrenze future in cui l'utente è `AVAILABLE`;
2. considera soltanto i promemoria che non hanno override personale o di evento;
3. annulla logicamente i job pendenti con origine `PROFILE` o `APPLICATION`;
4. crea il nuovo job se l'istante calcolato è futuro;
5. non reinvia job già consegnati;
6. esegue l'operazione nella stessa transazione del salvataggio preferenze.

Disabilitare `eventRemindersEnabled` annulla tutti i promemoria personali pendenti del tenant. Riabilitarlo ricalcola quelli futuri ancora utili. Il limite di occorrenze viene applicato con elaborazione a batch per non caricare l'intero calendario in memoria.

### Promemoria durante il silenzioso

Se il promemoria dovrebbe partire durante ore silenziose o pausa temporanea:

- viene rinviato alla fine del silenzioso soltanto se il nuovo istante precede l'inizio dell'evento;
- viene chiuso come `SKIPPED` se la fine del silenzioso coincide con o supera l'inizio;
- non viene consegnato in ritardo dopo l'avvio dell'evento.

La UI avverte che le ore silenziose possono quindi sopprimere promemoria di eventi mattutini.

## Ore silenziose e pausa temporanea

### Timezone

Il profilo salva una timezone IANA, per esempio `Europe/Rome`. Alla prima lettura usa quella del tenant; una successiva modifica della timezone del tenant non sovrascrive un valore personale già salvato.

Tutti i calcoli avvengono backend-side tramite `ZonedDateTime`. Il frontend invia orari locali senza offset e un identificatore IANA separato.

### Intervallo giornaliero

Le ore silenziose richiedono:

- `quietHoursEnabled = true`;
- `quietStart` e `quietEnd` nel formato locale `HH:mm`;
- valori differenti.

Se `quietStart < quietEnd`, l'intervallo appartiene allo stesso giorno. Se `quietStart > quietEnd`, attraversa la mezzanotte. Gli estremi seguono la convenzione `[start, end)`: l'inizio è silenzioso, la fine no.

Durante un cambio di ora legale:

- un orario inesistente viene spostato al primo istante valido successivo;
- un orario ambiguo usa l'offset precedente secondo le regole standard Java;
- ogni comportamento è coperto da test con `Europe/Rome`.

### Pausa temporanea

`pushPausedUntil` sospende tutti i push dell'utente fino a un massimo di 30 giorni. È indipendente dalle ore silenziose e può essere rimossa anticipatamente.

Un push applicativo pendente viene rinviato al più tardi tra fine della pausa e fine delle ore silenziose. Se supera `expiresAt`, viene chiuso `SKIPPED`. I promemoria evento applicano invece la regola più restrittiva legata all'inizio dell'evento.

Le notifiche in-app continuano ad arrivare e il loro conteggio resta aggiornato durante la pausa.

## Riepilogo giornaliero

### Comportamento

L'utente imposta un solo `digestLocalTime` valido per tutte le categorie in modalità `DAILY_DIGEST`. Il default è 08:00. Se l'orario cade dentro le ore silenziose, la UI rifiuta il salvataggio; il backend applica la stessa validazione.

Il digest:

- viene calcolato nella timezone personale;
- raggruppa soltanto job push non ancora elaborati dello stesso tenant e utente;
- contiene gli eventi accumulati dopo il precedente bucket;
- esclude le notifiche in-app già lette o eliminate prima dell'invio;
- include comunque gli eventi configurati come push-only, che non hanno una riga `notices`;
- rivalida la preferenza corrente prima dell'invio;
- produce un solo tentativo logico di riepilogo per bucket giornaliero, salvo il raro duplicato possibile nel confine esterno Web Push.

Esempio:

```text
Titolo: Taurus: riepilogo giornaliero
Testo: Hai 6 aggiornamenti: 3 calendario, 2 inventario e 1 economia.
Destinazione: /dashboard?section=notifications
```

Il digest non include nomi di persone, eventi, oggetti o importi sullo schermo bloccato. Se è presente una sola categoria usa comunque il formato aggregato.

### Bucket e idempotenza

Ogni job in modalità digest salva `digest_local_date`, calcolata al momento del fan-out nella timezone personale. Il vincolo logico del riepilogo è:

```text
(tenant schema, user_id, digest_local_date)
```

Il dispatcher acquisisce un lock per utente e data, seleziona tutti gli elementi eleggibili, invia il riepilogo e li marca insieme. Il normale retry non crea un secondo bucket. Web Push non fornisce idempotenza end-to-end: un crash dopo l'accettazione remota e prima del commit può produrre un duplicato raro, che viene registrato come limite operativo dichiarato.

Se non esistono sottoscrizioni attive, gli elementi vengono marcati `SKIPPED` e non vengono recuperati automaticamente quando un dispositivo viene registrato in seguito.

## Anteprima push e privacy

Il profilo espone `pushPreview`:

| Valore | Titolo e corpo |
| --- | --- |
| `PRIVATE` | titolo generico `Taurus`; corpo `Hai un nuovo aggiornamento` |
| `FULL` | titolo e messaggio editoriali della notifica |

`PRIVATE` è il default. In entrambi i casi il click porta al `targetPath` validato oppure al centro notifiche. Il digest resta sempre aggregato e privato, anche con `FULL`.

Per i promemoria evento:

- `PRIVATE`: `Taurus` / `Hai un promemoria per un evento`;
- `FULL`: `Promemoria evento` / nome e anticipo dell'evento.

La preferenza riduce l'esposizione sul dispositivo ma non sostituisce le impostazioni di privacy del sistema operativo.

## Ricordamelo più tardi

### Regole funzionali

Una notifica in-app non letta può essere rinviata scegliendo un preset oppure un istante:

- tra 5 minuti e 30 giorni nel futuro;
- calcolato e validato dal backend;
- appartenente sempre all'utente autenticato e al tenant corrente.

Lo snooze:

1. valorizza `snoozed_until`;
2. mantiene `read_date = null`;
3. incrementa `snooze_revision`;
4. esclude la riga dal normale elenco attivo e dal conteggio non lette;
5. annulla un eventuale job snooze precedente;
6. crea un job push di tipo `SNOOZE` se esiste almeno una sottoscrizione attiva;
7. alla scadenza rende nuovamente visibile la stessa riga, senza duplicarla.

Il job snooze nasce da un'azione esplicita e non dipende dal `pushMode` della categoria, ma rispetta anteprima, ore silenziose e pausa. Se non esiste un dispositivo push, la notifica ricompare comunque nel centro e nel badge tramite il polling già presente.

Leggere o eliminare una notifica snoozed annulla il job push pendente. **Mostra ora** rimuove `snoozed_until`, incrementa la revisione e la ripropone immediatamente come non letta.

Una notifica `REQUIRED` può essere rinviata: l'obbligatorietà impedisce la soppressione, non lo snooze personale. Eventuali flussi realmente bloccanti, come l'accettazione legale, continuano a essere governati dai relativi guard e non dal centro notifiche.

### Query del centro notifiche

Il conteggio predefinito usa:

```text
deleted = false
AND read_date IS NULL
AND (snoozed_until IS NULL OR snoozed_until <= now)
```

L'elenco normale applica la stessa condizione. Una vista **Posticipate** mostra invece le righe con `snoozed_until > now`, ordinate per riattivazione.

**Segna tutte come lette** agisce soltanto sulle notifiche attive attualmente visibili. **Elimina tutte** mantiene il comportamento esplicito corrente e include anche quelle posticipate, dopo conferma.

## Modello dati

### `notification_profile`

Nuova tabella tenant con una riga al massimo per utente:

| Campo | Tipo indicativo | Regola |
| --- | --- | --- |
| `id` | `BIGINT` | PK |
| `user_id` | `BIGINT` | FK a `app_user`, univoca tra righe attive |
| `time_zone` | `VARCHAR(64)` | timezone IANA valida |
| `event_reminders_enabled` | `BOOLEAN` | default `true` |
| `default_calendar_reminder_minutes` | `INTEGER` | 0–1.440, default 30 |
| `quiet_hours_enabled` | `BOOLEAN` | default `false` |
| `quiet_start` | `TIME` | default `22:00` |
| `quiet_end` | `TIME` | default `07:00` |
| `push_paused_until` | `TIMESTAMPTZ` | nullo o massimo 30 giorni nel futuro |
| `digest_local_time` | `TIME` | default `08:00` |
| `push_preview` | `VARCHAR(16)` | `PRIVATE` o `FULL` |
| audit/versione | campi standard tenant | include soft delete e lock ottimistico |

La FK usa l'ID relazionale dell'utente; il backend ricava sempre tale ID dal subject autenticato. Nessun endpoint accetta un `user_id` scelto dal client.

L'assenza della riga equivale ai default e una `GET` non scrive nel database. La prima `PUT` materializza il profilo completo.

### `notification_category_preference`

| Campo | Tipo indicativo | Regola |
| --- | --- | --- |
| `id` | `BIGINT` | PK |
| `profile_id` | `BIGINT` | FK con `ON DELETE CASCADE` |
| `source` | `VARCHAR(32)` | valore `NotificationSource` |
| `in_app_enabled` | `BOOLEAN` | default `true` |
| `push_mode` | `VARCHAR(20)` | `OFF`, `IMMEDIATE`, `DAILY_DIGEST` |
| audit/versione | campi standard tenant | modifiche tracciate |

Vincolo univoco sulle righe attive `(profile_id, source)`. Il salvataggio dell'aggregato contiene tutte le sorgenti note e sostituisce atomicamente le preferenze della versione precedente.

### Estensione di `notification_outbox`

| Campo | Tipo indicativo | Regola |
| --- | --- | --- |
| `preference_policy` | `VARCHAR(20)` | `CONFIGURABLE` o `REQUIRED`, default `CONFIGURABLE` |

Il campo entra in `NotificationCommand`; testo, pubblico, sorgente e severità restano invariati. Gli eventi già esistenti vengono migrati come `CONFIGURABLE`.

### Estensione di `notices`

| Campo | Tipo indicativo | Regola |
| --- | --- | --- |
| `snoozed_until` | `TIMESTAMPTZ` | facoltativo |
| `snooze_revision` | `INTEGER` | non nullo, default 0 |

Non viene creata una seconda notifica alla scadenza. Lo stesso ID torna attivo quando `snoozed_until <= now`, preservando lettura, eliminazione, deduplicazione e collegamento all'evento sorgente.

Indici candidati:

- `notices(user_id, read_date, snoozed_until)` limitato alle righe non cancellate;
- `notices(user_id, snoozed_until)` per la vista posticipate;
- verifica con `EXPLAIN ANALYZE` su cardinalità realistiche prima di renderli definitivi.

### Estensione di `push_reminders`

| Campo | Tipo indicativo | Regola |
| --- | --- | --- |
| `status` | `VARCHAR(16)` | `PENDING`, `DELIVERED`, `SKIPPED`, `FAILED` |
| `reminder_origin` | `VARCHAR(16)` | `PERSONAL`, `EVENT`, `PROFILE`, `APPLICATION` |
| `schedule_revision` | `INTEGER` | versione monotona della pianificazione |
| `attempts` | `INTEGER` | default 0 |
| `next_attempt_at` | `TIMESTAMPTZ` | retry ammesso |
| `delivered_at` | `TIMESTAMPTZ` | facoltativo |
| `skip_reason` | `VARCHAR(32)` | codice tecnico facoltativo |
| `last_error` | `VARCHAR(1000)` | sanificato |

Il booleano `sent` viene mantenuto durante la fase compatibile e poi rimosso: `true` migra a `DELIVERED`, `false` a `PENDING`. I job pendenti ricevono origine ricostruita quando possibile; in caso ambiguo usano `APPLICATION` e vengono ricalcolati alla successiva modifica rilevante.

### `notification_push_delivery`

Nuova tabella tenant per push applicativi, digest e snooze:

| Campo | Tipo indicativo | Regola |
| --- | --- | --- |
| `id` | `BIGINT` | PK |
| `source_event_key` | `VARCHAR(160)` | chiave dell'evento originario |
| `user_id` | `VARCHAR(255)` | subject Keycloak destinatario |
| `source` | `VARCHAR(32)` | categoria |
| `delivery_type` | `VARCHAR(16)` | `IMMEDIATE`, `DIGEST_ITEM`, `SNOOZE` |
| `title` | `VARCHAR(255)` | snapshot editoriale |
| `message` | `VARCHAR(255)` | snapshot editoriale |
| `target_path` | `VARCHAR(500)` | percorso interno validato |
| `notice_id` | `BIGINT` | FK facoltativa a `notices` |
| `snooze_revision` | `INTEGER` | solo per `SNOOZE` |
| `digest_local_date` | `DATE` | solo per `DIGEST_ITEM` |
| `scheduled_at` | `TIMESTAMPTZ` | primo istante previsto |
| `expires_at` | `TIMESTAMPTZ` | oltre il quale viene saltato |
| `status` | `VARCHAR(16)` | `PENDING`, `DELIVERED`, `SKIPPED`, `FAILED` |
| `attempts` | `INTEGER` | default 0 |
| `next_attempt_at` | `TIMESTAMPTZ` | retry ammesso |
| `delivered_at` | `TIMESTAMPTZ` | facoltativo |
| `skip_reason` | `VARCHAR(32)` | nessun dispositivo, scaduto, letto, preferenza cambiata |
| `last_error` | `VARCHAR(1000)` | sanificato |
| audit/versione | campi standard tenant | soft delete e lock |

Vincoli:

- indice univoco parziale `(source_event_key, user_id, delivery_type)` per `IMMEDIATE` e `DIGEST_ITEM`;
- indice univoco parziale `(notice_id, snooze_revision)` per `SNOOZE`;
- `DIGEST_ITEM` richiede `digest_local_date`;
- `SNOOZE` richiede `notice_id` e `snooze_revision`;
- indici su `(status, next_attempt_at, id)` e `(user_id, digest_local_date, status)`;
- titolo e messaggio rispettano gli stessi limiti editoriali dell'outbox.

La tabella conserva il payload minimo necessario al retry. Non memorizza endpoint, chiavi push o elenco dei dispositivi.

## Flusso di fan-out

Il flusso esistente diventa:

```text
Operazione di dominio
    -> NoticesAspect
    -> notification_outbox
    -> NotificationDispatcher
         -> NotificationRecipientResolver
         -> NotificationPreferenceResolver (lettura bulk)
              -> notices, se in-app consentito o REQUIRED
              -> notification_push_delivery, se push richiesto
              -> sola decisione soppressa, se entrambi disattivati
    -> notification_outbox DELIVERED
```

`NotificationPreferenceResolver` riceve sorgente, politica e insieme destinatari. Recupera profili e override di categoria con un numero costante di query, non una query per utente.

`DELIVERED` sull'outbox significa che il fan-out e le decisioni di canale sono stati persistiti. Non significa che un provider Web Push abbia già accettato il messaggio: tale stato appartiene a `notification_push_delivery` o `push_reminders`.

Se tutti i destinatari hanno soppresso una notifica configurabile, l'outbox passa comunque a `DELIVERED`. Una metrica incrementa il conteggio `suppressed`, senza salvare una riga personale di sola telemetria.

Le righe `notices` e `notification_push_delivery` vengono create nella stessa transazione del dispatch dell'outbox. I vincoli univoci rendono il retry idempotente.

## Dispatcher push

Un unico orchestratore multi-tenant elabora `notification_push_delivery` e `push_reminders`, mantenendo servizi di dominio distinti per i due repository.

Per ogni job:

1. acquisisce lock con strategia batch compatibile con più istanze;
2. verifica utente attivo, preferenze correnti, scadenza, pausa e ore silenziose;
3. carica le sottoscrizioni attive soltanto al momento dell'invio;
4. applica la modalità di anteprima;
5. invia a tutti i dispositivi correnti;
6. elimina logicamente sottoscrizioni che rispondono `404` o `410`;
7. marca `DELIVERED` se almeno un endpoint accetta il messaggio;
8. marca `SKIPPED` se non esistono endpoint validi o il messaggio non è più pertinente;
9. applica retry per errori temporanei e `FAILED` dopo il limite configurato.

`PushService` non deve più nascondere l'esito in un metodo `@Async void`: restituisce un risultato strutturato per endpoint al dispatcher. Gli errori di un dispositivo non impediscono il tentativo sugli altri.

Politica retry proposta: 1, 2, 4, 8, 16, 32 e 60 minuti, massimo 8 tentativi. `429` e `5xx` sono temporanei; errori permanenti di payload o configurazione portano direttamente a `FAILED`. Gli endpoint e le chiavi di sottoscrizione non compaiono mai nei log.

La consegna esterna è almeno una volta. Il backend evita duplicati ordinari, ma non può sapere con certezza se un push accettato sia stato visualizzato.

## API

### Profilo notifiche

```http
GET /api/notification-preferences
PUT /api/notification-preferences
```

`GET` restituisce sempre un aggregato completo, materializzato o derivato dai default:

```json
{
  "version": 3,
  "timeZone": "Europe/Rome",
  "eventRemindersEnabled": true,
  "defaultCalendarReminderMinutes": 30,
  "quietHours": {
    "enabled": true,
    "start": "22:00",
    "end": "07:00"
  },
  "pushPausedUntil": null,
  "digestLocalTime": "08:00",
  "pushPreview": "PRIVATE",
  "categories": [
    {
      "source": "CALENDAR",
      "inAppEnabled": true,
      "pushMode": "IMMEDIATE"
    },
    {
      "source": "INVENTORY",
      "inAppEnabled": true,
      "pushMode": "DAILY_DIGEST"
    }
  ]
}
```

`PUT` sostituisce atomicamente l'intero aggregato. La `version` è obbligatoria dopo la prima persistenza; un conflitto restituisce `409 Conflict` con codice stabile. Il backend:

- ricava utente e tenant dall'autenticazione;
- rifiuta sorgenti duplicate, mancanti o sconosciute;
- valida timezone, orari, intervalli e anticipo;
- pianifica nella stessa transazione le modifiche ai promemoria ereditati;
- non riceve né modifica sottoscrizioni browser.

Le API CRUD generiche `/api/preferences` non vengono usate dalla nuova pagina. Restano disponibili per preferenze non migrate finché necessario.

### Snooze

```http
PATCH /api/notices/{id}/snooze
PATCH /api/notices/{id}/unsnooze
```

Richiesta snooze:

```json
{
  "until": "2026-09-04T09:00:00+02:00"
}
```

La risposta restituisce la notifica aggiornata. `404` copre ID inesistente, cancellato o appartenente a un altro utente; `409` indica notifica già letta o versione concorrente; `400` indica istante fuori dai limiti.

`PATCH /api/notices/{id}/read` cancella automaticamente lo snooze e il job relativo. L'API elenco aggiunge filtri tipizzati:

```http
GET /api/notices?view=ACTIVE&source=CALENDAR&unread=true
GET /api/notices?view=SNOOZED
```

Il default è `ACTIVE`. Il client non può passare un altro user ID.

### Stato push del dispositivo

La registrazione continua a usare `/api/push-subscriptions`. La pagina distingue:

- supporto e permesso del browser;
- sottoscrizione del dispositivo corrente;
- impostazioni dell'account nel tenant.

La cancellazione per endpoint resta autorizzata al solo proprietario, ma l'endpoint non deve più essere scritto nei log neppure a livello debug.

## Frontend

### Pagina profilo

La sezione **Notifiche** diventa un'unità di modifica autonoma con un solo comando **Salva preferenze**. Contiene, nell'ordine:

1. stato push del dispositivo corrente e relativo consenso;
2. promemoria eventi con toggle e anticipo predefinito;
3. tabella categorie con colonna **Nel centro notifiche** e scelta **Push**;
4. timezone e orario del riepilogo, visibile quando almeno una categoria usa il digest;
5. ore silenziose;
6. pausa temporanea e relativo comando di riattivazione;
7. anteprima push `Privata` o `Completa`.

I controlli seguono [Standard di layout Taurus](taurus-layout-standard.md). In implementazione devono essere consultate soltanto le sezioni PrimeNG pertinenti di `docs/llms-full.md`.

La UI mostra i default ricevuti dal backend e non li ricostruisce. Il toggle browser rimane un'operazione immediata separata dal salvataggio dell'account, perché apre un prompt esterno e riguarda soltanto quel dispositivo.

Stati da comunicare chiaramente:

- browser non supportato;
- permesso non ancora richiesto;
- permesso negato, modificabile soltanto nelle impostazioni del browser;
- dispositivo registrato;
- account configurato per push ma nessun dispositivo registrato;
- preferenze modificate ma non ancora salvate;
- conflitto di versione con possibilità di ricaricare.

### Centro notifiche

Ogni riga non letta aggiunge al menu contestuale:

- **Ricordamelo tra un'ora**;
- **Domani mattina** nella timezone personale;
- **Scegli data e ora**;
- **Disattiva questa categoria**, soltanto per notifiche configurabili.

La pagina distingue le viste **Attive** e **Posticipate**. Una riga posticipata mostra l'istante di ritorno e le azioni **Mostra ora**, **Segna come letta** ed **Elimina**.

Il badge globale usa soltanto notifiche attive non lette. Il polling ogni 30 secondi già presente è sufficiente a far ricomparire una riga scaduta; non viene introdotto WebSocket nella prima versione.

Tutte le azioni espongono label accessibili, focus visibile e conferma solo quando l'effetto è distruttivo. Lo snooze non richiede conferma e mostra un toast con l'istante risultante.

## Gestione degli eventi tecnici `FAILED`

Le preferenze utente non mostrano, modificano o riprocessano stati tecnici. La pagina amministrativa `/admin/notification-delivery?status=FAILED`, già prevista dalla dashboard operativa, rimane il punto unico per Super Admin e Admin.

La console distingue due origini:

| Origine | Tabella | Azioni |
| --- | --- | --- |
| fan-out in-app | `notification_outbox` | retry idempotente |
| consegna push | `notification_push_delivery`, `push_reminders` | retry o chiusura tecnica motivata |

La dashboard aggrega il numero totale ma la console filtra per origine, sorgente, operazione e intervallo. Non espone testo completo, destinatario, endpoint push, chiavi o stack trace. Mostra soltanto ID tecnico, hash della chiave evento, categoria, tipo consegna, tentativi, istanti e classe d'errore sanificata.

`SKIPPED` e le soppressioni dovute a preferenze non sono errori e non compaiono tra i `FAILED`. Un retry amministrativo rivalida utente, preferenze e sottoscrizioni; non forza l'invio contro una preferenza corrente.

## Sicurezza e privacy

- Tutti gli endpoint richiedono autenticazione e usano il tenant risolto dal contesto.
- L'utente destinatario deriva sempre dal subject; non è accettato dal body o dalla query.
- Le preferenze di un tenant non influenzano lo stesso account in un altro tenant.
- Timezone, enum, orari, date e limiti vengono validati nel backend.
- `targetPath` continua a usare l'allowlist e deve iniziare con una sola `/`.
- `pushPreview = PRIVATE` è il default per ridurre dati sullo schermo bloccato.
- Endpoint, `p256dh` e `auth` sono segreti operativi: non entrano in DTO di elenco, log, metriche o console.
- I payload non contengono HTML e rispettano i limiti editoriali esistenti.
- Un utente disattivato o cancellato non riceve nuove consegne; job e sottoscrizioni pendenti vengono eliminati o chiusi.
- La cancellazione GDPR comprende profilo, preferenze categorie, notifiche, job push, reminder e sottoscrizioni.
- La cancellazione del tenant elimina tutto con lo schema, senza record globali aggiuntivi.
- Le metriche non usano tenant, user ID, notification ID, evento o endpoint come tag.

## Concorrenza e consistenza

- Il profilo usa lock ottimistico; due salvataggi concorrenti non si sovrascrivono silenziosamente.
- Profilo, categorie e ripianificazione dei reminder vengono salvati nella stessa transazione tenant.
- Il fan-out legge una fotografia coerente delle preferenze e crea notice/job nella transazione dell'outbox.
- Una modifica preferenze concorrente può precedere o seguire atomicamente il fan-out; non esiste uno stato intermedio parziale.
- Il dispatcher push rivalida le preferenze prima dell'effetto esterno, quindi una disattivazione successiva al fan-out impedisce un job non ancora inviato.
- Il salvataggio `read`, `snooze`, `unsnooze` o `delete` incrementa la versione della notifica e annulla atomicamente i job snooze superati.
- I job usano lock pessimista o `SKIP LOCKED` e vincoli univoci per supportare più istanze backend.
- Il passaggio a `DELIVERED` avviene dopo la risposta del provider; un crash nel confine esterno può produrre un raro duplicato, mai una doppia riga in-app.

## Migrazione

### Fase expand

Una migration in `tenant-master.xml`:

- crea `notification_profile` e `notification_category_preference`;
- aggiunge `preference_policy` a `notification_outbox` con default `CONFIGURABLE`;
- aggiunge i campi snooze a `notices`;
- estende `push_reminders` mantenendo temporaneamente `sent`;
- crea `notification_push_delivery` e relativi indici;
- aggiorna la cascata di soft delete dell'utente;
- aggiorna retention e cancellazione GDPR.

### Migrazione del promemoria legacy

La preferenza chiave-valore `defaultReminderMinutes` viene migrata così:

1. join tra `preferences.user_id` e `app_user.keycloak_id` nello stesso schema;
2. in presenza di duplicati, scelta deterministica dell'ultima riga attiva per `COALESCE(edit_date, insert_date)` e poi `id`;
3. parsing dei soli interi da 0 a 1.440;
4. creazione di `notification_profile` per i valori validi;
5. valore logico 30 per dati assenti o invalidi, senza creare righe inutili;
6. doppia lettura temporanea: prima tabella tipizzata, poi chiave legacy;
7. confronto tramite metriche e test;
8. soft delete di tutte le copie della chiave legacy soltanto nella fase contract.

Non vengono creati push generici durante la migrazione. Le categorie mancanti continuano a usare in-app `true` e push `OFF`.

### Migrazione reminder

- `sent = true` diventa `status = DELIVERED`;
- `sent = false` diventa `status = PENDING`;
- job pendenti scaduti vengono marcati `SKIPPED` anziché inviati in massa;
- dopo almeno un rilascio compatibile, il codice smette di leggere `sent` e una migration successiva lo rimuove.

## Configurazione

Valori indicativi:

```yaml
application:
  notification-preferences:
    enabled: false
    default-calendar-reminder-minutes: 30
    default-time-zone: Europe/Rome
    default-digest-local-time: "08:00"
    max-pause-days: 30
    min-snooze-minutes: 5
    max-snooze-days: 30
  notification-push-delivery:
    batch-size: 100
    poll-delay: 5000
    max-attempts: 8
    retry-initial-minutes: 1
    retry-max-minutes: 60
    default-expiration-hours: 24
    delivered-retention-days: 30
    skipped-retention-days: 30
    failed-retention-days: 90
```

I default di prodotto sono restituiti dal backend nel DTO. Il frontend non legge direttamente questa configurazione.

## Osservabilità

Metriche a bassa cardinalità:

- decisioni di fan-out per sorgente, canale e risultato `DELIVERED`, `SUPPRESSED` o `REQUIRED_OVERRIDE`;
- job push per tipo e stato;
- durata e dimensione dei batch;
- numero elementi per digest;
- ritardo tra `scheduled_at` e tentativo;
- retry e `FAILED` per classe di errore normalizzata;
- reminder rinviati o saltati per ore silenziose;
- sottoscrizioni eliminate per `404`/`410`;
- conflitti ottimistici sul profilo;
- righe legacy del promemoria ancora lette durante la migrazione.

I log non includono titolo, messaggio, utente, endpoint o chiavi push. Per correlazione usano ID tecnico del job e hash della `source_event_key`. Alert:

- crescita di job `FAILED`;
- backlog `PENDING` oltre la soglia temporale;
- digest non elaborati dopo l'orario previsto;
- aumento anomalo di errori provider;
- scheduler multi-tenant incompleto;
- job orfani rispetto a notifiche o utenti.

## Strategia di test

### Unit test backend

- default completi quando il profilo non esiste;
- mapping di ogni `NotificationSource`;
- preferenze `inAppEnabled` e tre modalità push;
- override `REQUIRED` soltanto sul canale in-app;
- fallback sicuro per sorgente sconosciuta;
- precedenza `PERSONAL > EVENT > PROFILE > APPLICATION` e semantica dello zero;
- intervalli silenziosi nello stesso giorno e oltre mezzanotte;
- timezone invalida e transizioni DST `Europe/Rome`;
- pausa massima e calcolo del prossimo istante utile;
- digest bucket per timezone e data locale;
- composizione aggregata senza dati sensibili;
- anteprima `PRIVATE` e `FULL`;
- snooze minimo, massimo, revisione e ritorno nell'elenco attivo;
- esclusione delle righe snoozed dal conteggio;
- skip di reminder che diventerebbe successivo all'evento;
- classificazione errori push temporanei e permanenti.

### Integration test backend

- `GET` senza riga persistita e `PUT` atomica dell'aggregato;
- conflitto di versione;
- impossibilità di leggere o modificare preferenze e notice di altri utenti;
- isolamento tra due schemi tenant per lo stesso subject;
- fan-out con combinazioni in-app/push/digest/off;
- evento `REQUIRED` con categoria in-app disabilitata;
- outbox `DELIVERED` quando tutti i canali sono soppressi;
- deduplicazione di notice e job durante retry;
- cambio preferenza dopo il fan-out ma prima del push;
- digest che esclude notice lette o cancellate e include push-only;
- un solo digest per utente, tenant e data locale;
- assenza di sottoscrizioni e stato `SKIPPED`;
- eliminazione `404`/`410` e retry `429`/`5xx`;
- snooze, unsnooze, read e delete concorrenti;
- ripianificazione dei reminder ereditati senza toccare override personali o evento;
- disattivazione utente e cancellazione GDPR;
- scheduler che ripristina sempre il tenant context;
- migrazione della chiave legacy valida, assente e corrotta;
- console `FAILED` autorizzata solo ad Admin e Super Admin.

### Test frontend

- default provenienti dal backend;
- matrice categorie e visibilità condizionata per ruolo;
- separazione tra toggle dispositivo e salvataggio account;
- campi digest mostrati soltanto quando necessari;
- validazione digest dentro le ore silenziose;
- timezone, pausa e anteprima;
- dirty state unico e conflitto di versione;
- browser unsupported/default/granted/denied;
- viste attive e posticipate;
- preset e data personalizzata dello snooze;
- badge che esclude e poi reinserisce una notifica snoozed;
- navigazione sicura verso `targetPath`;
- responsive layout, tastiera, focus e annunci screen reader.

### Test operativi

- push su almeno Chrome/Edge desktop e Android supportato;
- installazione PWA e applicazione chiusa;
- più dispositivi per lo stesso utente;
- revoca del permesso dal browser;
- device offline durante retry;
- cambio ora legale e cambio timezone personale;
- riavvio backend durante invio immediato e digest;
- verifica che payload, endpoint e credenziali non compaiano nei log.

## Criteri di accettazione

1. Ogni utente autenticato riceve un profilo completo anche senza righe persistite.
2. Le preferenze appartengono al tenant corrente e non attraversano gli schemi.
3. Ogni sorgente permette in-app attivo/disattivo e push `OFF`, `IMMEDIATE` o `DAILY_DIGEST`.
4. I default conservano tutte le notifiche in-app e non abilitano nuovi push generici.
5. Una notifica `REQUIRED` resta in-app anche con categoria disabilitata.
6. `REQUIRED` non forza né il permesso browser né un push disabilitato.
7. Il fan-out legge le preferenze in bulk e non esegue una query per destinatario.
8. Un evento completamente soppresso non resta bloccato nell'outbox.
9. Notice e job push non vengono duplicati dai retry.
10. Il digest persiste un solo bucket logico per utente, tenant e data locale e documenta il limite at-least-once del provider.
11. Il digest non ritarda né raggruppa le righe del centro notifiche.
12. Una notifica letta o eliminata prima del digest ne viene esclusa.
13. Il digest non espone nomi, entità o importi nel payload aggregato.
14. Le ore silenziose e la pausa temporanea non alterano il conteggio in-app.
15. Un promemoria evento non viene mai inviato dopo l'inizio dell'evento.
16. L'anticipo segue la precedenza definita e zero disabilita il livello selezionato.
17. Cambiare il default ripianifica soltanto i reminder che lo ereditano.
18. Il default di anticipo è coerente a 30 minuti tra backend e frontend.
19. Lo snooze esclude immediatamente la riga dal badge e la ripropone allo scadere.
20. Snooze, lettura ed eliminazione appartengono sempre all'utente autenticato.
21. La modalità privata non inserisce titolo o descrizione originali nel payload push.
22. Un dispositivo senza sottoscrizione non causa retry indefiniti.
23. Risposte push `404` e `410` eliminano la sottoscrizione non più valida.
24. Endpoint e chiavi push non compaiono in log, metriche o console.
25. Stati `FAILED` sono gestiti soltanto dalla console amministrativa separata.
26. `SKIPPED` e soppressioni personali non vengono presentati come guasti.
27. La modifica di una preferenza prima dell'invio impedisce un push non più consentito.
28. La cancellazione GDPR rimuove tutti i nuovi dati personali e i job pendenti.
29. Cambio di timezone e ora legale sono coperti da test deterministici.
30. Il frontend è utilizzabile da tastiera e comunica stato, errori e orari in forma testuale.

## Piano di rilascio

### Fase 1 — Modello tipizzato e compatibilità

- migration expand;
- API profilo dietro feature flag;
- doppia lettura del promemoria legacy;
- default backend/frontend unificato a 30 minuti;
- nessuna modifica al fan-out visibile.

### Fase 2 — Categorie in-app e snooze

- `NotificationPreferenceResolver`;
- politica `REQUIRED` con allowlist revisionata;
- filtri attive/posticipate e conteggio corretto;
- UI profilo e centro notifiche;
- metriche di soppressione.

### Fase 3 — Push immediati affidabili

- `notification_push_delivery`;
- risultato strutturato di `PushService`;
- retry, cleanup sottoscrizioni e anteprima privata;
- pilota con categorie push opt-in.

### Fase 4 — Silenzioso e digest

- timezone personale, ore silenziose e pausa;
- bucket giornalieri e riepilogo aggregato;
- comportamento dei reminder mattutini;
- test DST e multi-dispositivo.

### Fase 5 — Console e contract

- estensione della console `FAILED` ai job push;
- runbook operativo e alert;
- rimozione lettura `defaultReminderMinutes` legacy;
- rimozione del booleano `push_reminders.sent` dopo un rilascio compatibile;
- verifica finale GDPR, retention e assenza di segreti nei log.

## Evoluzioni successive

Il modello consente in futuro, senza cambiare il significato della prima versione:

- digest settimanali;
- orari diversi per giorno;
- canale e-mail con una propria coda di consegna;
- preferenze per singolo tipo `operation`;
- soglie o priorità personali;
- gestione dei dispositivi registrati con nome e ultima attività;
- template localizzati lato backend;
- aggiornamento in tempo reale del badge.

Ogni nuovo canale deve avere stato, retry, retention e consenso autonomi. Non deve essere aggiunto come effetto collaterale sincrono del `NotificationDispatcher`.
