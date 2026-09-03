# Spec: Web Push Notifications + RabbitMQ Delayed Reminders

## Stato del documento

ID catalogo: `web-push-reminders`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

## Obiettivo

Aggiungere al calendario eventi:
1. **Notifiche push Web** (anche fuori dall'app, via Service Worker) per eventi real-time già gestiti da `NoticesAspect` (disponibilità, presenze)
2. **Promemoria automatici** dell'inizio evento, inviati X minuti prima tramite **RabbitMQ delayed exchange**, solo agli utenti con disponibilità confermata

---

## Decisioni architetturali già prese

| Aspetto | Scelta |
|---|---|
| Destinatari promemoria | Solo utenti con `availability = true` per quell'evento |
| Preferenza reminder | Preference utente globale (default) + override per singolo evento + opzione "nessun avviso" |
| Tecnologia delay | RabbitMQ `x-delayed-message-exchange` plugin |
| Notifiche immediate | `NoticesAspect` → `PushService` (stesso canale delle notifiche in-app) |
| Frontend push | Service Worker NGSW + Web Push API + VAPID |

---

## Stack e vincoli

- **Backend**: Spring Boot 3.3.5, Spring WebFlux (reactive), R2DBC, Java 17
- **Frontend**: Angular 19 standalone, PrimeNG 19, Keycloak Angular
- **DB**: PostgreSQL via R2DBC + Liquibase per migrazioni
- **Auth**: OAuth2/OIDC Keycloak, JWT in `SecurityContext`
- **Multi-tenancy**: campo `tenant_code` su ogni entità, filtraggio nei service
- **Pattern service**: `CommonService` / `CommonOpenSearchService` con `CommonResource`
- **Naming package**: `com.fundaro.zodiac.taurus.*`

---

## Dipendenza Maven da aggiungere (pom.xml)

```xml
<!-- Web Push VAPID -->
<dependency>
    <groupId>nl.martijndwars</groupId>
    <artifactId>web-push</artifactId>
    <version>5.1.1</version>
</dependency>
<!-- Bouncy Castle (richiesto da web-push per VAPID) -->
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk15on</artifactId>
    <version>1.70</version>
</dependency>
```

---

## Step 1 — RabbitMQ: abilitare il plugin delayed

Nel file Docker Compose di RabbitMQ (probabilmente `taurus-be/src/main/docker/rabbitmq.yml`), aggiornare l'immagine:

```yaml
image: rabbitmq:3-management
```
→
```yaml
image: rabbitmq:3-management
# oppure usa un'immagine con plugin pre-installato:
# image: heidiks/rabbitmq-delayed-message-exchange:3.9.13-management
```

In alternativa, aggiungere un `Dockerfile` custom per RabbitMQ che esegue:
```
rabbitmq-plugins enable rabbitmq_delayed_message_exchange
```

---

## Step 2 — Database: migrazioni Liquibase

### 2a. Tabella `push_subscriptions`

File: `taurus-be/src/main/resources/config/liquibase/changelog/YYYYMMDDHHMMSS_add_push_subscriptions.xml`

```xml
<createTable tableName="push_subscriptions">
    <column name="id" type="bigint" autoIncrement="true"><constraints primaryKey="true"/></column>
    <column name="user_id" type="varchar(255)"><constraints nullable="false"/></column>
    <column name="tenant_code" type="varchar(255)"><constraints nullable="false"/></column>
    <column name="endpoint" type="varchar(2048)"><constraints nullable="false"/></column>
    <column name="p256dh" type="varchar(512)"><constraints nullable="false"/></column>
    <column name="auth" type="varchar(255)"><constraints nullable="false"/></column>
    <column name="created_date" type="timestamp"/>
    <column name="last_modified_date" type="timestamp"/>
</createTable>
<addUniqueConstraint tableName="push_subscriptions" columnNames="user_id,endpoint"/>
```

### 2b. Campo `reminder_minutes` su `calendar_events`

File: `taurus-be/src/main/resources/config/liquibase/changelog/YYYYMMDDHHMMSS_add_reminder_minutes_to_calendar_events.xml`

```xml
<addColumn tableName="calendar_events">
    <column name="reminder_minutes" type="integer" defaultValue="-1">
        <!-- -1 = usa preferenza utente, 0 = nessun avviso, >0 = minuti prima -->
        <constraints nullable="true"/>
    </column>
</addColumn>
```

### 2c. Campo `reminder_minutes` su `preferences` (o tabella separata se già strutturata)

Verificare la struttura attuale di `Preferences` e aggiungere:
```xml
<addColumn tableName="preferences">
    <column name="default_reminder_minutes" type="integer" defaultValue="30">
        <!-- 0 = nessun avviso, N = minuti prima -->
        <constraints nullable="true"/>
    </column>
</addColumn>
```

---

## Step 3 — Backend: entità `PushSubscription`

### Domain entity

`taurus-be/src/main/java/com/fundaro/zodiac/taurus/domain/PushSubscription.java`

- Estende `CommonFields` (che ha `id`, `tenantCode`, `createdDate`, ecc.)
- Campi: `userId` (String), `endpoint` (String), `p256dh` (String), `auth` (String)
- Annotazione `@Table("push_subscriptions")`

### Repository

`...repository/PushSubscriptionRepository.java`

```java
public interface PushSubscriptionRepository extends ReactiveCrudRepository<PushSubscription, Long> {
    Flux<PushSubscription> findByUserId(String userId);
    Mono<Void> deleteByUserIdAndEndpoint(String userId, String endpoint);
    Flux<PushSubscription> findByUserIdIn(List<String> userIds);
}
```

### DTO

`...service/dto/PushSubscriptionDTO.java`
- Campi: `id`, `userId`, `endpoint`, `p256dh`, `auth`

### Mapper

`...service/mapper/PushSubscriptionMapper.java`
- Estende `EntityMapper<PushSubscriptionDTO, PushSubscription>`

### Service Interface

`...service/PushSubscriptionService.java`
- `Mono<PushSubscriptionDTO> save(PushSubscriptionDTO dto, AbstractAuthenticationToken token)`
- `Mono<Void> delete(String endpoint, AbstractAuthenticationToken token)`

### Service Implementation

`...service/impl/PushSubscriptionServiceImpl.java`
- Implementa `PushSubscriptionService`
- `save()`: ricava `userId` dal token JWT, salva abbonamento
- `delete()`: rimuove per userId + endpoint

### REST Resource

`...web/rest/PushSubscriptionResource.java`

```java
@RestController
@RequestMapping("/api/push-subscriptions")
public class PushSubscriptionResource {
    @PostMapping
    public Mono<ResponseEntity<PushSubscriptionDTO>> subscribe(@RequestBody PushSubscriptionDTO dto, AbstractAuthenticationToken token) { ... }
    
    @DeleteMapping
    public Mono<ResponseEntity<Void>> unsubscribe(@RequestParam String endpoint, AbstractAuthenticationToken token) { ... }
}
```

---

## Step 4 — Backend: `PushService` (VAPID)

`...service/impl/PushServiceImpl.java`

```java
@Service
public class PushServiceImpl {
    // Iniettare da application.yml:
    // application.vapid.public-key
    // application.vapid.private-key
    // application.vapid.subject (es. mailto:admin@taurus.it)
    
    private final nl.martijndwars.webpush.PushService pushService;
    
    public Mono<Void> sendToUser(String userId, String title, String body) {
        // 1. Caricare tutte le PushSubscription per userId
        // 2. Per ognuna, costruire Notification e chiamare pushService.send()
        // 3. Gestire errori 410 Gone (rimuovere subscription scaduta)
    }
    
    public Mono<Void> sendToUsers(List<String> userIds, String title, String body) {
        // Iterare su sendToUser per ogni userId
    }
}
```

### Payload JSON per la notifica push

```json
{
  "title": "Promemoria evento",
  "body": "L'evento 'Nome Evento' inizia tra 30 minuti",
  "icon": "/assets/icon-192x192.png",
  "badge": "/assets/badge-72x72.png",
  "tag": "event-reminder-{eventId}",
  "data": {
    "url": "/calendar-events/{eventId}"
  }
}
```

### Configurazione application.yml

```yaml
application:
  vapid:
    public-key: <VAPID_PUBLIC_KEY_BASE64_URL>
    private-key: <VAPID_PRIVATE_KEY_BASE64_URL>
    subject: mailto:admin@taurus.it
```

> **Nota**: generare le chiavi VAPID con:
> ```
> npx web-push generate-vapid-keys
> ```

---

## Step 5 — Backend: RabbitMQ delayed exchange config

`...rabbitmq/EventReminderRabbitConfig.java`

```java
@Configuration
public class EventReminderRabbitConfig {
    public static final String REMINDER_EXCHANGE = "event.reminder.delayed";
    public static final String REMINDER_QUEUE    = "event.reminder.queue";
    public static final String REMINDER_KEY      = "event.reminder";

    @Bean
    public CustomExchange reminderDelayedExchange() {
        return new CustomExchange(REMINDER_EXCHANGE, "x-delayed-message", true, false,
            Map.of("x-delayed-type", "direct"));
    }

    @Bean
    public Queue reminderQueue() {
        return new Queue(REMINDER_QUEUE, true);
    }

    @Bean
    public Binding reminderBinding() {
        return BindingBuilder.bind(reminderQueue())
            .to(reminderDelayedExchange())
            .with(REMINDER_KEY)
            .noargs();
    }
}
```

---

## Step 6 — Backend: `EventReminderMessage`

`...rabbitmq/EventReminderMessage.java`

```java
public record EventReminderMessage(
    String eventId,
    String eventName,
    List<String> userIds,  // utenti da notificare
    long scheduledAt       // epoch millis di quando mandare (per deduplication)
) implements Serializable {}
```

---

## Step 7 — Backend: `EventReminderProducer`

`...rabbitmq/EventReminderProducer.java`

```java
@Service
public class EventReminderProducer {
    private final RabbitTemplate rabbitTemplate;
    
    public void scheduleReminder(CalendarEventsDTO event, List<String> userIds, int minutesBefore) {
        long delayMs = computeDelay(event.getStartDate(), minutesBefore);
        if (delayMs <= 0) return; // evento già passato o troppo vicino
        
        EventReminderMessage msg = new EventReminderMessage(
            event.getId(), event.getName(), userIds, 
            Instant.now().toEpochMilli() + delayMs
        );
        
        rabbitTemplate.convertAndSend(
            EventReminderRabbitConfig.REMINDER_EXCHANGE,
            EventReminderRabbitConfig.REMINDER_KEY,
            msg,
            m -> { m.getMessageProperties().setDelay((int) delayMs); return m; }
        );
    }
    
    public void cancelReminder(String eventId) {
        // RabbitMQ delayed non supporta cancellazione diretta.
        // Soluzione: nel Consumer, verificare se l'utente ha ancora availability=true.
        // Se no, saltare silenziosamente. Non è necessario fare nulla qui.
    }
}
```

---

## Step 8 — Backend: `EventReminderConsumer`

`...rabbitmq/EventReminderConsumer.java`

```java
@Component
public class EventReminderConsumer {
    private final PushServiceImpl pushService;
    private final CalendarEventsService calendarEventsService; // per verificare availability attuale
    
    @RabbitListener(queues = EventReminderRabbitConfig.REMINDER_QUEUE)
    public void handleReminder(EventReminderMessage message) {
        // 1. Per ogni userId nel messaggio, verificare che abbia ancora availability=true
        // 2. Filtrare gli userId ancora disponibili
        // 3. Chiamare pushService.sendToUsers(filteredUserIds, title, body)
    }
}
```

---

## Step 9 — Backend: hook in `CalendarEventsServiceImpl`

Nei metodi esistenti (o in `NoticesAspect`), dopo `setAvailability(true)`:

```java
// Dopo aver confermato la disponibilità di un utente:
// 1. Calcolare reminderMinutes effettivi:
//    - Se event.reminderMinutes == -1 → leggere preferenza utente (defaultReminderMinutes)
//    - Se event.reminderMinutes == 0 → skip
//    - Se event.reminderMinutes > 0 → usare quello
// 2. Se minuti > 0 → eventReminderProducer.scheduleReminder(event, List.of(userId), minutesBefore)

// Dopo cancelAvailability o setAvailability(false):
// → Non fare nulla (il consumer verifica la disponibilità al momento dell'invio)
```

---

## Step 10 — Backend: hook in `NoticesAspect`

Nel `NoticesAspect` esistente, nei metodi `onSetAvailability`, `onCancelAvailability`, `onSetPresentUsers`, aggiungere dopo la creazione della notifica in-app:

```java
// Notifica push immediata agli admin (o all'utente stesso, secondo logica business)
pushService.sendToUsers(adminUserIds, title, body).subscribe();
```

---

## Step 11 — Frontend: Service Worker

### Configurazione Angular Service Worker

`taurus-fe/angular.json` — aggiungere nel progetto:
```json
"serviceWorker": true,
"ngswConfigPath": "ngsw-config.json"
```

`taurus-fe/ngsw-config.json`:
```json
{
  "index": "/index.html",
  "assetGroups": [
    {
      "name": "app",
      "installMode": "prefetch",
      "resources": { "files": ["/favicon.ico", "/index.html", "/*.css", "/*.js"] }
    }
  ]
}
```

Aggiungere `@angular/service-worker` all'`app.config.ts`:
```typescript
provideServiceWorker('ngsw-worker.js', {
  enabled: !isDevMode(),
  registrationStrategy: 'registerWhenStable:30000'
})
```

### Custom push handler nel Service Worker

Creare `taurus-fe/src/custom-sw.js` (se serve gestione personalizzata dei click):
```javascript
self.addEventListener('push', event => {
  const data = event.data?.json() ?? {};
  event.waitUntil(
    self.registration.showNotification(data.title, {
      body: data.body, icon: data.icon, badge: data.badge,
      tag: data.tag, data: data.data
    })
  );
});
self.addEventListener('notificationclick', event => {
  event.notification.close();
  const url = event.notification.data?.url;
  if (url) event.waitUntil(clients.openWindow(url));
});
```

---

## Step 12 — Frontend: `PushNotificationService`

`taurus-fe/src/app/services/push-notification.service.ts`

```typescript
@Injectable({ providedIn: 'root' })
export class PushNotificationService {
  private readonly VAPID_PUBLIC_KEY = environment.vapidPublicKey;

  async requestPermissionAndSubscribe(): Promise<void> {
    if (!('serviceWorker' in navigator) || !('PushManager' in window)) return;
    
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') return;
    
    const sw = await navigator.serviceWorker.ready;
    const subscription = await sw.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: this.urlB64ToUint8Array(this.VAPID_PUBLIC_KEY)
    });
    
    // Inviare la subscription al backend
    await this.pushSubscriptionHttpService.save(subscription).toPromise();
  }

  async unsubscribe(): Promise<void> {
    const sw = await navigator.serviceWorker.ready;
    const subscription = await sw.pushManager.getSubscription();
    if (subscription) {
      await this.pushSubscriptionHttpService.delete(subscription.endpoint).toPromise();
      await subscription.unsubscribe();
    }
  }
  
  private urlB64ToUint8Array(base64String: string): Uint8Array { /* ... */ }
}
```

### `PushSubscriptionHttpService`

`taurus-fe/src/app/services/push-subscription-http.service.ts`

```typescript
@Injectable({ providedIn: 'root' })
export class PushSubscriptionHttpService {
  save(subscription: PushSubscription): Observable<any> {
    return this.http.post('/api/push-subscriptions', {
      endpoint: subscription.endpoint,
      p256dh: btoa(String.fromCharCode(...new Uint8Array(subscription.getKey('p256dh')!))),
      auth: btoa(String.fromCharCode(...new Uint8Array(subscription.getKey('auth')!)))
    });
  }
  delete(endpoint: string): Observable<void> {
    return this.http.delete<void>('/api/push-subscriptions', { params: { endpoint } });
  }
}
```

---

## Step 13 — Frontend: aggiungere VAPID public key all'environment

`taurus-fe/src/environments/environment.ts`:
```typescript
export const environment = {
  production: false,
  vapidPublicKey: '<VAPID_PUBLIC_KEY>'
};
```

---

## Step 14 — Frontend: Preferences page

Aggiungere alla pagina preferenze utente un campo:

```html
<p-floatlabel variant="on">
  <p-inputnumber id="defaultReminderMinutes" [(ngModel)]="preferences.defaultReminderMinutes"
    [min]="0" [max]="1440" suffix=" min" />
  <label for="defaultReminderMinutes">Promemoria evento (minuti prima, 0 = nessuno)</label>
</p-floatlabel>

<!-- Toggle notifiche push -->
<div class="flex items-center gap-3">
  <p-togglebutton [(ngModel)]="pushEnabled" onLabel="Notifiche push attive" offLabel="Notifiche push disattivate"
    (onChange)="onPushToggle($event)" />
</div>
```

Logica nel TS della pagina preferences:
- Al toggle ON → `pushNotificationService.requestPermissionAndSubscribe()`
- Al toggle OFF → `pushNotificationService.unsubscribe()`
- Al caricamento → verificare `Notification.permission` e stato subscription

---

## Step 15 — Frontend: Calendar Events detail — campo `reminderMinutes`

In `detail.component.html`, nel card "Evento" (solo per utenti che hanno dato la disponibilità), aggiungere:

```html
<!-- Visibile solo se l'utente corrente ha disponibilità confermata -->
@if (currentUserAvailability === true) {
  <p-floatlabel variant="on">
    <p-inputnumber id="reminderMinutes" [(ngModel)]="event.reminderMinutes"
      [min]="-1" [max]="1440" suffix=" min"
      [placeholder]="'Default (' + (userDefaultReminder ?? 30) + ' min)'"
      (ngModelChange)="isDirty = true" />
    <label for="reminderMinutes">Promemoria personale (-1 = usa default, 0 = nessuno)</label>
  </p-floatlabel>
}
```

> Nota: `reminderMinutes` viene salvato insieme all'availability tramite il PATCH esistente, oppure con un endpoint separato `PATCH /api/user/calendar-events/{id}/reminder`.

---

## Step 16 — Frontend: richiedere permission push dopo login

In `app.component.ts`, dopo l'inizializzazione di Keycloak, controllare se l'utente ha già dato il consenso alle notifiche e, se no, chiedere in modo non invasivo (es. toast o banner):

```typescript
// Dopo keycloak.init()
if ('Notification' in window && Notification.permission === 'default') {
  // Mostrare un banner/toast non intrusivo
  // "Vuoi ricevere notifiche push per i promemoria degli eventi?"
}
```

---

## Riepilogo file da creare/modificare

### Backend — nuovi file
| File | Tipo |
|---|---|
| `domain/PushSubscription.java` | Entity |
| `repository/PushSubscriptionRepository.java` | Repository |
| `service/dto/PushSubscriptionDTO.java` | DTO |
| `service/mapper/PushSubscriptionMapper.java` | Mapper |
| `service/PushSubscriptionService.java` | Interface |
| `service/impl/PushSubscriptionServiceImpl.java` | Service |
| `service/impl/PushServiceImpl.java` | Push VAPID |
| `web/rest/PushSubscriptionResource.java` | REST |
| `rabbitmq/EventReminderRabbitConfig.java` | Config |
| `rabbitmq/EventReminderMessage.java` | DTO |
| `rabbitmq/EventReminderProducer.java` | Producer |
| `rabbitmq/EventReminderConsumer.java` | Consumer |
| `changelog/..._add_push_subscriptions.xml` | Liquibase |
| `changelog/..._add_reminder_minutes.xml` | Liquibase |

### Backend — file modificati
| File | Modifica |
|---|---|
| `pom.xml` | Aggiungere web-push + bouncy castle |
| `domain/CalendarEvents.java` | Aggiungere `reminderMinutes` |
| `service/dto/CalendarEventsDTO.java` | Aggiungere `reminderMinutes` |
| `domain/Preferences.java` | Aggiungere `defaultReminderMinutes` |
| `service/dto/PreferencesDTO.java` | Aggiungere `defaultReminderMinutes` |
| `service/impl/CalendarEventsServiceImpl.java` | Hook reminder al setAvailability |
| `aop/notices/NoticesAspect.java` | Hook push immediata |
| `config/application.yml` | Aggiungere `application.vapid.*` |
| `docker/rabbitmq.yml` | Immagine con plugin delayed |

### Frontend — nuovi file
| File | Tipo |
|---|---|
| `services/push-notification.service.ts` | Service |
| `services/push-subscription-http.service.ts` | HTTP Service |
| `ngsw-config.json` | SW config |
| `src/custom-sw.js` (opzionale) | SW custom handler |

### Frontend — file modificati
| File | Modifica |
|---|---|
| `environments/environment.ts` | `vapidPublicKey` |
| `environments/environment.prod.ts` | `vapidPublicKey` |
| `angular.json` | `serviceWorker: true` |
| `app.config.ts` | `provideServiceWorker(...)` |
| `app.component.ts` | Banner consenso push |
| `pages/calendar-events/detail/detail.component.ts` | Campo reminderMinutes + logica |
| `pages/calendar-events/detail/detail.component.html` | UI reminderMinutes |
| `pages/preferences/...` | Toggle push + defaultReminderMinutes |

---

## Note operative

1. **VAPID keys**: prima di avviare, generare con `npx web-push generate-vapid-keys` e inserire in `application.yml` e `environment.ts`
2. **HTTPS obbligatorio per Service Worker**: in development usare `ng serve --ssl` oppure `localhost` (browser lo permette)
3. **Plugin RabbitMQ**: verificare che il container RabbitMQ carichi il plugin prima di avviare il backend
4. **Cancellazione reminder**: il consumer ri-verifica la disponibilità al momento della consegna, quindi `cancelAvailability` non richiede logica esplicita di annullamento nella coda
5. **Errore 410 Gone**: quando Web Push ritorna 410, la subscription è scaduta → rimuoverla dal DB automaticamente nel `PushServiceImpl`
