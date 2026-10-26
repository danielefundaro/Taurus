# Specifica: promemoria eventi tramite Web Push

## Stato del documento

ID catalogo: `web-push-reminders`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

Questa specifica descrive l'architettura effettivamente adottata. Sostituisce il progetto iniziale basato su WebFlux/R2DBC e RabbitMQ delayed exchange, non coerente con lo stack corrente di Taurus.

## Obiettivo

Taurus invia promemoria Web Push prima dell'inizio di un evento agli utenti che hanno confermato la propria disponibilità. L'utente può abilitare il dispositivo, configurare il comportamento globale e scegliere un anticipo personale per il singolo evento.

Le notifiche applicative immediate continuano a passare dalla pipeline generalizzata delle notifiche; il promemoria temporizzato usa invece un job persistente dedicato.

## Decisioni architetturali

| Aspetto | Decisione implementata |
| --- | --- |
| Backend | Spring Boot MVC, Spring Data JPA, Java 17 |
| Persistenza | PostgreSQL e schema separato per tenant |
| Pianificazione | righe persistenti in `push_reminders`, elaborate ogni minuto |
| Consegna | Web Push con VAPID tramite `nl.martijndwars:web-push` |
| Frontend | Angular Service Worker e `SwPush` |
| Destinatari | utente autenticato con disponibilità confermata sull'evento |
| Multi-tenancy | enumerazione tenant attivi e transazione nel relativo schema |
| Preferenze | profilo notifiche tipizzato, con fallback temporaneo alla preferenza legacy |
| Navigazione | rotta applicativa `/calendar/{eventId}` |
| Funzionalità tenant | sottoscrizioni e preferenze dietro `NOTIFICATION_PREFERENCES`; promemoria dietro `WEB_PUSH_REMINDERS`, che richiede la prima e chiavi VAPID configurate |

RabbitMQ resta presente per altri flussi del progetto, ma non gestisce il ritardo dei promemoria calendario. La persistenza PostgreSQL evita di perdere le pianificazioni al riavvio e rende gli esiti consultabili dalla console amministrativa.

## Modello dei dati

### Sottoscrizioni push

La tabella `push_subscriptions` conserva, per utente e tenant, endpoint, chiave `p256dh` e segreto `auth`. Le sottoscrizioni scadute vengono eliminate logicamente quando il provider risponde `404` o `410`.

File principali:

- `domain/PushSubscription.java`;
- `repository/PushSubscriptionRepository.java`;
- `service/impl/PushSubscriptionServiceImpl.java`;
- migration `20260708000001_add_entity_PushSubscription.xml`.

### Pianificazioni

La tabella `push_reminders` registra almeno evento, destinatario, istante di invio ed esito. Le estensioni della pipeline notifiche aggiungono stato, origine della preferenza, revisione, tentativi, prossimo tentativo, consegna, motivo di salto ed errore.

`status` è il solo campo autorevole: il booleano `sent` è stato rimosso con la fase contract, perché due indicatori dello stesso fatto potevano contraddirsi.

File principali:

- `domain/PushReminder.java`;
- `repository/PushReminderRepository.java`;
- migration `20260710000001_add_entity_PushReminder.xml`;
- migration `20260905000000_notification_preferences.xml`.

### Anticipo del promemoria

L'anticipo effettivo applica questa precedenza:

1. valore personale sulla disponibilità dell'utente;
2. valore configurato sull'evento;
3. valore del profilo notifiche dell'utente;
4. preferenza legacy `defaultReminderMinutes`, se l'utente non ha un profilo notifiche;
5. default applicativo di 30 minuti.

Il valore `0` disabilita il promemoria al livello in cui è configurato. I valori validi sono compresi tra 0 e 1440 minuti.

`EventReminderProducer` legge la preferenza legacy direttamente dalle righe del destinatario, non dal contesto della richiesta: quando un amministratore aggiorna un evento e i promemoria vengono ripianificati, a ogni disponibile senza profilo si applica il proprio `defaultReminderMinutes`. Un valore non numerico equivale all'assenza della preferenza e porta al default applicativo.

## Flussi backend

### Registrazione del dispositivo

- `POST /api/push-subscriptions` registra o riallinea una sottoscrizione del dispositivo autenticato.
- `DELETE /api/push-subscriptions?endpoint=...` disattiva la sottoscrizione dello stesso utente.
- Identità utente e tenant derivano dal token e dal contesto autenticato, non dal payload client.

### Preferenze

- `GET /api/notification-preferences` restituisce il profilo corrente.
- `PUT /api/notification-preferences` aggiorna consenso ai reminder, anticipo predefinito, modalità push, quiet hours, digest e livello di anteprima.
- Le preferenze granulari della sorgente `CALENDAR` vengono risolte dalla pipeline condivisa.

### Disponibilità e promemoria personale

Quando un utente conferma la disponibilità, `CalendarEventsServiceImpl` crea o aggiorna la risposta e richiama `EventReminderProducer`. Una risposta negativa o annullata elimina il job pendente.

Per gli utenti standard:

- `PATCH /api/user/calendar-events/{id}/availability?available=true|false` aggiorna la disponibilità;
- `PATCH /api/user/calendar-events/{id}/availability/cancel` annulla la risposta;
- `GET /api/user/calendar-events/{id}/reminder` legge l'anticipo personale;
- `PATCH /api/user/calendar-events/{id}/reminder?minutes=...` lo imposta;
- l'assenza di `minutes` ripristina il valore ereditato dall'evento.

Gli utenti esterni usano gli stessi endpoint sotto `/api/external/calendar-events/{id}/reminder`. Super Admin, Admin e Archivista usano `/api/calendar-events/{id}/reminder`. Gli endpoint del promemoria richiedono la funzionalità `WEB_PUSH_REMINDERS`.

Il promemoria personale è modificabile solo con disponibilità confermata.

### Pianificazione e consegna

`EventReminderProducer` calcola `sendAt`, elimina fisicamente gli eventuali job `PENDING` per la stessa coppia evento/utente, ne crea uno nuovo e non crea job già scaduti. Con la funzionalità `WEB_PUSH_REMINDERS` disattivata non crea job. Una ripianificazione dovuta al valore predefinito del profilo tocca solo i job che lo ereditano (vedi [Preferenze notifiche granulari](notification-preferences-spec.md)).

`NotificationPushScheduler` è l'unico orchestratore delle code push e tratta anche `push_reminders`; la consegna del singolo promemoria vive in `PushReminderDeliveryService`. A ogni giro:

1. enumera i tenant attivi;
2. per ciascuno chiede fino a `batch-size` ID pronti, cioè i reminder `PENDING` con `sendAt <= now`;
3. tratta ogni ID in una transazione propria, con lock pessimista sulla riga: più repliche del backend non si rubano lo stesso promemoria e il fallimento di uno non trascina gli altri;
4. rivaluta funzionalità tenant, inizio dell'evento, preferenze, pausa e quiet hours;
5. invia il payload con target `/calendar/{eventId}`;
6. registra `DELIVERED`, `SKIPPED` o `FAILED`: lo stato è l'unica verità del job, e un reminder `FAILED` torna in coda solo con il retry della console amministrativa;
7. applica backoff ai fallimenti temporanei (1, 2, 4… fino a 60 minuti, massimo 8 tentativi) senza superare l'inizio dell'evento.

Un reminder viene chiuso `SKIPPED` se la funzionalità è disattivata, i promemoria sono disabilitati, l'evento è già iniziato (anche dopo un fermo del backend) o non esistono sottoscrizioni valide.

## Payload e sicurezza

`PushServiceImpl` genera un payload compatibile con Angular Service Worker. La navigazione è affidata a `navigateLastFocusedOrOpen` e usa esclusivamente un percorso interno:

```json
{
  "notification": {
    "title": "Promemoria evento",
    "body": "L'evento \"Prova\" sta per iniziare",
    "icon": "/icons/icon-192x192.png",
    "data": {
      "onActionClick": {
        "default": {
          "operation": "navigateLastFocusedOrOpen",
          "url": "/calendar/42"
        }
      }
    }
  }
}
```

Con anteprima `FULL` il testo è quello dell'esempio, senza l'anticipo. Con anteprima `PRIVATE` titolo e testo sono `Taurus` e `Hai un promemoria per un evento`, senza il nome dell'evento. Le chiavi VAPID arrivano dalla configurazione di deploy; `CHANGE_ME` disabilita esplicitamente il canale e non deve essere usato in produzione.

## Frontend

Angular registra `ngsw-worker.js` tramite `provideServiceWorker`, in ogni ambiente. La registrazione era in origine condizionata a `!isDevMode()`, ma la configurazione `development` di `angular.json` costruisce e serve comunque il worker: il risultato era un worker disponibile e mai registrato, mentre una registrazione creata una volta sola — da una build di produzione o da una prova delle push in locale — sopravviveva indefinitamente continuando a servire dalla cache un bundle vecchio. Registrarlo sempre allinea lo sviluppo alla produzione e rende provabili le push in locale.

`AppUpdateService` fa avanzare le versioni, che altrimenti non avanzerebbero da sole: il service worker scarica la versione nuova ma tiene ogni scheda ancorata a quella con cui è partita. Il servizio ascolta `versionUpdates`, e su `VERSION_READY` in produzione propone un toast persistente con l'azione di ricarica — mai una ricarica non annunciata, che butterebbe via un modulo compilato a metà — mentre in sviluppo attiva subito, perché ogni ricompilazione è una versione nuova e un consenso ogni volta sarebbe solo rumore. Su `unrecoverable` ricarica senza chiedere: il worker non trova più i file della versione che sta servendo e la pagina è già rotta. Il controllo esplicito parte alla prima stabilità dell'applicazione e si ripete ogni sei ore.

`PushNotificationService`:

- non mostra richieste di consenso automatiche all'avvio;
- chiede il permesso soltanto dopo un gesto esplicito dell'utente;
- usa `SwPush` con la chiave VAPID pubblica;
- riallinea al backend una sottoscrizione browser già concessa;
- consente la revoca dal profilo.

La pagina profilo espone abilitazione del dispositivo, promemoria eventi e anticipo predefinito. Il dettaglio evento consente agli amministratori di definire il default dell'evento e agli utenti disponibili di scegliere il proprio anticipo.

## Osservabilità e gestione operativa

Metriche e console amministrativa distinguono consegne, salti e fallimenti. I fallimenti temporanei possono essere ritentati automaticamente o dalla console; le sottoscrizioni invalide vengono conteggiate e disattivate.

La retention elimina i reminder conclusi secondo `application.retention.sent-push-reminders-days`.

## Criteri di accettazione

- La sottoscrizione appartiene sempre all'utente e al tenant autenticati.
- Un utente non disponibile non mantiene reminder pendenti.
- La precedenza personale → evento → profilo → fallback è rispettata.
- Quiet hours e pausa push rinviano la consegna senza oltrepassare l'inizio dell'evento.
- Gli endpoint scaduti vengono disattivati.
- Il click sul reminder apre `/calendar/{eventId}`.
- Stato ed esito sono disponibili alla console amministrativa.
- La pianificazione sopravvive al riavvio dell'applicazione.

## Verifica

La copertura automatica rilevante include:

- `CalendarEventsServiceImplTest`: disponibilità, annullamento e reminder personale;
- `PushReminderDeliveryServiceTest`: consegna, rotta del dettaglio evento, lock per job e testo del promemoria;
- `NotificationPreferencesServiceTest` e `NotificationTimingTest`: preferenze e finestre temporali;
- `NotificationPushDeliveryServiceTest`: consegna push generalizzata;
- `NotificationPreferencesIT`: persistenza PostgreSQL e isolamento tenant;
- `EventReminderProducerTest`: precedenza dell'anticipo, interruttore globale del profilo e ripianificazione dei soli job ereditati;
- `notification-presentation.service.spec.ts`: presentazione delle notifiche nel frontend;
- `push-notification.service.spec.ts`: supporto, consenso, registrazione e disattivazione del dispositivo;
- `app-update.service.spec.ts`: attivazione della versione scaricata, stato non recuperabile e controlli periodici.
