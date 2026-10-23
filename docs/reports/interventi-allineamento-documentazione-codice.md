# Interventi di allineamento tra documentazione e codice

23 ottobre 2026

Questo report spiega ogni intervento della revisione che ha confrontato la documentazione di Taurus con il codice di `main`. Per ogni intervento indica in quale direzione è andato e perché:

- **codice aggiornato in base al documento**: il documento descriveva il comportamento voluto e il codice non lo rispettava, quindi il difetto era nel codice;
- **documento aggiornato in base al codice**: il codice era corretto, o era una scelta voluta, e il documento era superato, incompleto o descriveva un progetto mai realizzato.

Il report non è una specifica e non fa parte del catalogo `docs/features.json` né della matrice `docs/features.md`. Nulla è stato committato: tutte le modifiche sono nella working tree.

Percorsi abbreviati: `be/` = `taurus-be/src/main/java/com/fundaro/zodiac/taurus/`, `fe/` = `taurus-fe/src/app/`.

## Criteri

Tre regole hanno guidato la direzione degli interventi:

- **Requisiti non realizzati.** Quando un documento descrive qualcosa che il codice non fa e che non è un difetto, il requisito resta nella specifica, spostato nella sezione «Requisiti aperti». Le voci del catalogo che ne hanno sono passate a `in-progress`.
- **Standard violati dal codice.** Se il codice ha superato una regola di proposito, la regola è stata aggiornata; altrimenti la violazione è registrata in «Scostamenti noti», senza correggere ora il codice.
- **Frasi temporali di stato.** Frasi come «implementato il…», «in corso» o «previsto» sono state tolte dalle specifiche: lo stato vive solo nel catalogo.

Il codice è stato modificato solo per i 16 difetti della sezione seguente, ognuno con un test che riproduce il caso, e per i messaggi delle due chiavi d'errore che quelle correzioni hanno introdotto (1.17).

## 1. Codice aggiornato in base alla documentazione

### 1.1 Promemoria personale negato a soci ed esterni

Ho aggiornato il codice `be/config/SecurityConfiguration.java` in base al documento [web-push-reminders-spec.md](../web-push-reminders-spec.md) perché la specifica prevede che soci (`PATCH /api/user/calendar-events/{id}/reminder`), utenti esterni e ruoli amministrativi impostino il proprio anticipo del promemoria, ma le regole di sicurezza non elencavano quei percorsi: la richiesta finiva nel `denyAll` e rispondeva 403 a tutti, archivista compreso. Ho aggiunto le tre regole esplicite. Test: `SecurityConfigurationTest`, 7 casi nuovi.

### 1.2 Saldo iniziale cancellato dal riporto annuale

Ho aggiornato il codice `be/service/impl/FinanceService.java` e `be/domain/finance/FinancialMovement.java`, con la migrazione `20261022000000_financial_initial_balance.xml`, in base al documento [financial-management-spec.md](../financial-management-spec.md) perché la specifica tratta il saldo iniziale di un conto come un dato inserito dall'utente, che si corregge solo con il normale flusso dei movimenti. Il riporto notturno e il comando «Ricalcola» invece lo sovrascrivevano con il saldo dell'anno precedente, o lo eliminavano se quel saldo era zero, perché esiste una sola apertura per esercizio e conto. Ora l'apertura inserita a mano porta `initial_balance = true` e il riporto non la tocca; la creazione di un conto con saldo iniziale ricalcola gli esercizi successivi. Lo stesso contrassegno vale per i saldi importati dall'onboarding (`OnboardingDomainApplicationService`). Test: `FinanceServiceTest`.

I saldi iniziali già danneggiati prima della correzione non si ripristinano da soli: vanno controllati a mano.

### 1.3 Saldo iniziale assente da estratto conto e rendiconto annuale

Ho aggiornato il codice `be/service/impl/FinanceService.java` in base al documento [financial-management-spec.md](../financial-management-spec.md) perché la specifica chiede che l'estratto conto riporti il saldo iniziale del periodo e che il rendiconto annuale mostri i saldi iniziali per conto, ma un conto aperto nel periodo partiva da zero: il saldo d'apertura del periodo considerava solo il giorno precedente. Ora vi entra anche il saldo iniziale dei conti aperti nel periodo. Test: `FinanceServiceTest`, 2 casi nuovi.

### 1.4 Dashboard: il dominio Inventario spariva con un guasto aperto

Ho aggiornato il codice `be/service/OperationalDashboardService.java` in base al documento [inventory-qr-code-spec.md](../inventory-qr-code-spec.md) perché la specifica prevede in dashboard le segnalazioni `UNSAFE` e `LIMITING` con il collegamento all'elenco filtrato. Il provider dell'inventario produceva le due voci, ma i loro percorsi non erano nell'elenco dei percorsi ammessi: la validazione falliva e l'intero dominio Inventario risultava «non disponibile». Ho aggiunto i due percorsi. Test: `OperationalDashboardServiceTest`, 2 casi nuovi, di cui uno controlla che ogni tipo abbia un percorso ammesso.

### 1.5 Conservazione sospesa nei tenant senza Inventario

Ho aggiornato il codice `InventoryLoanSheetRetentionScheduler`, `SignaturePurgeScheduler`, `SignatureLinkExpiryScheduler` e `SignatureChallengeCleanupScheduler` in base al documento [tenant-feature-flags-spec.md](../tenant-feature-flags-spec.md) perché la specifica stabilisce che conservazione dei fogli, eliminazione degli atti al termine del periodo di grazia, scadenza dei link e pulizia delle conferme continuano su tutti i tenant attivi. I quattro job giravano solo sui tenant con Inventario attivo, quindi spegnere l'Inventario lasciava dati personali e documenti oltre il termine. Ora girano su tutti i tenant attivi.

### 1.6 Il tesoriere non apriva il dettaglio evento

Ho aggiornato il codice `taurus-fe/src/app.routes.ts` in base al documento [event-preparation-spec.md](../event-preparation-spec.md) perché la specifica ammette `ROLE_TREASURER` sul dettaglio `/calendar/:id`, aperto dalle notifiche di chiusura economica, ma la rotta padre `calendar` non includeva il ruolo e il dettaglio era irraggiungibile. La rotta padre ora ammette il tesoriere; l'elenco del calendario resta escluso dalla rotta figlia. Test: `app.routes.spec.ts`.

La specifica economica prevedeva anche il «Calendario in lettura» per il tesoriere: è stata allineata alla scelta più ristretta (vedi [2.2](#22-economia-tenant-e-piattaforma)).

### 1.7 Onboarding senza conservazione dei dati

Ho aggiornato il codice con i nuovi `OnboardingRetentionService` e `OnboardingCleanupScheduler`, con modifiche a `be/repository/MediaRepository.java`, `OnboardingImportService` e alla migrazione `20261022000001_onboarding_retention.xml`, in base al documento [tenant-onboarding-import-spec.md](../tenant-onboarding-import-spec.md) perché la specifica prevede che file, righe e problemi degli import conclusi siano eliminati dopo 30 giorni e i metadati dopo 365, con un job notturno `OnboardingCleanupScheduler`. Il job non esisteva: file con e-mail e date di nascita restavano senza scadenza. Ora il job riduce il journal identità agli esiti, libera il file e risponde `409 onboarding.job.sourceRemoved` a una nuova validazione o a un nuovo invio delle e-mail dopo la pulizia. Test: `OnboardingRetentionServiceTest`, 3 casi.

Il primo passaggio dopo il rilascio elimina subito i dati degli import conclusi da oltre 30 giorni.

### 1.8 Serie ricorrenti: spostamento raddoppiato

Ho aggiornato il codice `be/service/impl/CalendarEventSeriesServiceImpl.java` in base al documento [recurring-calendar-events-spec.md](../recurring-calendar-events-spec.md) perché la specifica definisce la data originaria (`original_start_date`) come la posizione dell'occorrenza nella regola, e il frontend calcola il nuovo inizio della serie dallo scarto rispetto a quella data. Dopo «Salva da questo evento in poi» la data originaria restava quella vecchia, quindi una seconda modifica sommava di nuovo lo spostamento. Ora dalla sorgente in poi la data originaria segue la nuova regola; se coincide con quella di un'altra occorrenza la risposta è `409 calendar.series.occurrence.conflict`, prima era un errore 500. Test: `CalendarEventSeriesServiceImplTest`, 1 caso nuovo.

### 1.9 Valutazione della preparazione esposta ai soci

Ho aggiornato il codice `be/service/eventpreparation/EventPreparationService.java`, `fe/module/event-preparation.module.ts` e il dettaglio evento `fe/pages/calendar-events/detail/` in base al documento [event-preparation-spec.md](../event-preparation-spec.md) perché la specifica esclude il riepilogo «Preparazione operativa» dalle sessioni `ROLE_USER` e `ROLE_USER_EXTERNAL`, e vieta di caricare dati non autorizzati per poi nasconderli. Il frontend nascondeva la sezione, ma le viste personali dell'API restituivano comunque stato, percentuale e criticità, che possono rivelare problemi economici o di materiale. Ora le viste personali non contengono la valutazione e il frontend mostra la sezione solo quando c'è. Test: `EventPreparationServiceTest`.

### 1.10 Promemoria inviati a evento iniziato

Ho aggiornato il codice `be/rabbitmq/PushReminderScheduler.java` in base al documento [web-push-reminders-spec.md](../web-push-reminders-spec.md) perché la specifica stabilisce che un promemoria viene saltato se l'evento è già iniziato. Lo scheduler non lo controllava: dopo un fermo del backend inviava promemoria in ritardo per eventi in corso. Ora li chiude `SKIPPED` con motivo `EVENT_STARTED`. Test: `PushReminderSchedulerTest`, 1 caso nuovo.

### 1.11 Job PDF bloccato in coda

Ho aggiornato il codice `be/service/impl/TracksServiceImpl.java` e `QueueUploadFilesService(Impl)` in base al documento [track-pdf-processing-spec.md](../track-pdf-processing-spec.md) perché la specifica chiede che ogni errore definitivo porti il job a `ERROR`, da cui si può riprovare. Se la pubblicazione su RabbitMQ dopo il commit falliva, il codice registrava solo l'errore nel log: il job restava `TO_PROCESS` per sempre e non era ritentabile, perché il nuovo tentativo parte solo da `ERROR`. Ora il job passa a `ERROR` in una transazione propria (`markUnpublished`). Test: `TracksServiceImplTest`, 1 caso nuovo.

### 1.12 Limiti dei feed superabili con richieste concorrenti

Ho aggiornato il codice `be/service/calendarfeed/CalendarFeedManagementService.java` in base al documento [external-calendar-feed-spec.md](../external-calendar-feed-spec.md) perché la specifica fissa al massimo 3 feed personali attivi per utente e 10 feed del tenant, verificati sotto lock transazionale. Il lock copriva solo la chiave di idempotenza: due creazioni contemporanee con chiavi diverse superavano insieme il conteggio. Ora un advisory lock sul limite precede il conteggio. Test: `CalendarFeedManagementServiceTest`.

### 1.13 Segreti nei log di sviluppo

Ho aggiornato il codice `be/aop/logging/LoggingAspect.java` in base ai documenti [external-calendar-feed-spec.md](../external-calendar-feed-spec.md), [inventory-electronic-signature-spec.md](../inventory-electronic-signature-spec.md) e [inventory-qr-code-spec.md](../inventory-qr-code-spec.md) perché le specifiche vietano token, codici di conferma, e-mail, codici fiscali e codici delle etichette nei log, e la specifica della firma cita esplicitamente i log di debug del profilo di sviluppo. L'aspect generato da JHipster scrive in DEBUG argomenti e risultati di ogni bean: token dei feed, codici e dati della firma e UUID dei QR finivano nel log del profilo `dev`. Ora l'aspect esclude i package e le resource che li trattano.

### 1.14 Immagine decodificata prima del controllo delle dimensioni

Ho aggiornato il codice `be/service/impl/ImageTransformationService.java` in base al documento [track-page-image-editing-spec.md](../track-page-image-editing-spec.md) perché la specifica chiede di leggere le dimensioni dall'header prima di allocare l'immagine, per contrastare le decompression bomb. Il codice decodificava l'intera immagine con `ImageIO.read` e solo dopo controllava lato e pixel. Ora legge le dimensioni con `ImageReader` e rifiuta l'immagine prima di decodificarla. Test: `ImageTransformationServiceTest`, 1 caso nuovo.

### 1.15 Notifica «Tenant: rimosso» persa

Ho aggiornato il codice `be/aop/notices/NoticesAspect.java` in base al documento [notification-delivery-generalization-spec.md](../notification-delivery-generalization-spec.md) perché la specifica chiede di salvare una notifica tra tenant nello schema del tenant di destinazione «dopo che lo schema è disponibile». Per la rimozione di un tenant la destinazione era proprio il tenant rimosso, il cui schema non viene più elaborato: la notifica non arrivava mai. Ora va nel tenant in cui lavora il super amministratore; se rimuove proprio quel tenant, la notifica non viene emessa. Test: `NoticesAspectTest`, 1 caso nuovo.

### 1.16 Promemoria fallito ripreso ogni minuto

Ho aggiornato il codice `be/rabbitmq/PushReminderScheduler.java` in base ai documenti [web-push-reminders-spec.md](../web-push-reminders-spec.md) e [operational-dashboard-spec.md](../operational-dashboard-spec.md) perché le specifiche trattano `FAILED` come esito finale, che torna in coda solo con il «Riprova» della console delle consegne. Un fallimento permanente impostava lo stato `FAILED` ma lasciava `sent = false`, il criterio con cui lo scheduler sceglie i promemoria da inviare: il promemoria veniva ripreso ogni minuto. Ora un fallimento definitivo imposta anche `sent = true`. Test: `PushReminderSchedulerTest`, 1 caso nuovo.

### 1.17 Messaggi del frontend per le chiavi nuove

Ho aggiornato il codice `fe/constants/api-error-messages.ts` in base al documento [application-errors-standard.md](../application-errors-standard.md) perché lo standard chiede che ogni chiave d'errore lanciata dal backend abbia un messaggio nel catalogo del frontend, e le correzioni 1.7 e 1.8 hanno introdotto `onboarding.job.sourceRemoved` e `calendar.series.occurrence.conflict`. Ho aggiunto i due messaggi; `check-error-catalog.mjs` lo verifica.

## 2. Documentazione aggiornata in base al codice

### 2.1 Calendario, preparazione eventi e dashboard

**[recurring-calendar-events-spec.md](../recurring-calendar-events-spec.md).** Ho aggiornato il documento in base al codice `be/service/impl/CalendarEventSeriesServiceImpl.java`, `be/service/RecurringEventGenerator.java`, `be/service/impl/CalendarEventsServiceImpl.java` e `be/config/SecurityConfiguration.java` perché descriveva come unica modifica di serie l'ambito «tutte le future» e rinviava «questa e le successive», mentre l'interfaccia offre solo «Salva da questo evento in poi» (`sourceOccurrenceId`) e l'ambito delle future esiste solo via API. Ho descritto abbinamento per posizione, aggiornamento anche delle occorrenze passate, riallineamento della data originaria e risposta 409; intervallo da 1 a 366, promemoria da 0 a 1440, ripiego sul fuso predefinito, compenso e costi scritti solo con `FINANCE`; disponibilità bulk senza conferma preventiva e aperta anche all'archivista; la colonna del tesoriere, che usa solo l'API economica. Restano 11 requisiti aperti, tra cui il budget del tesoriere che non marca l'occorrenza come eccezione (vedi [sezione 4](#4-rilievi-registrati-e-non-corretti)).

**[external-calendar-feed-spec.md](../external-calendar-feed-spec.md).** Ho aggiornato il documento in base al codice `be/service/calendarfeed/` (`CalendarFeedManagementService`, `CalendarFeedIdempotencyCodec`, `CalendarFeedTokenResolver`, `IcalendarRenderer`) e alle quattro migrazioni dei feed perché diceva che il token è conservato solo come digest e che il calendario è generato con ical4j. In realtà il token è conservato anche cifrato con AES-GCM nelle ricevute di idempotenza (`calendar_feed_idempotency`, tabella assente dal documento) e il renderer scrive le righe da sé, usando ical4j solo per validare. Ho aggiunto l'eliminazione dall'elenco dei feed revocati, la funzionalità tenant `EXTERNAL_CALENDAR_FEED`, la configurazione reale, l'aggiornamento giornaliero di `last_accessed_at` e l'avviso effettivo della UI; ho tolto il piano di rilascio per fasi. Restano 13 requisiti aperti, tra cui metriche, collaudo con i client e test di integrazione.

**[event-preparation-spec.md](../event-preparation-spec.md).** Ho aggiornato il documento in base al codice `be/service/eventpreparation/EventPreparationService.java`, `EventPreparationNotificationScheduler`, alle quattro resource della preparazione e al dettaglio evento perché descriveva componenti e regole mai realizzati: valutatori separati per area, criticità con titolo, `relatedId` e collegamento, snapshot della traccia e indicatore di bis, limite di 100 voci, hash su JSON canonico, contratto `unavailableAreas`, dettaglio Angular scomposto per area, notifica della conferma materiale invalidata. Ho descritto il servizio unico, la criticità unica per area con messaggio e ancora fissa, i requisiti tolti quando Inventario o Finanza sono spenti, la versione solo sulla configurazione, le notifiche e la dashboard reali, la vista dell'archivista con valutazione ristretta e conteggi aggregati. Restano 19 requisiti aperti.

**[operational-dashboard-spec.md](../operational-dashboard-spec.md).** Ho aggiornato il documento in base al codice `be/service/OperationalDashboardService.java`, ai provider di `be/service/dashboard/`, a `DashboardMetrics` e a `NotificationDeliveryAdminService` perché mancavano le voci aggiunte dopo la stesura (preparazione eventi, guasti inventario, firme richieste), le consegne fallite contavano solo l'outbox mentre il provider unisce outbox, push e promemoria, e la console delle consegne ha origini, filtri e chiusura tecnica non descritti. Ho anche corretto la regola dei movimenti da riconciliare (natura `ORDINARY`, totali distinti per valuta), i tag delle metriche e il default `enabled: false`. Restano 6 requisiti aperti, tra cui il filtro del calendario per i collegamenti della preparazione, che oggi aprono l'elenco senza filtro.

### 2.2 Economia, tenant e piattaforma

**[financial-management-spec.md](../financial-management-spec.md).** Ho aggiornato il documento in base al codice `be/service/impl/FinanceService.java`, `FinanceResource`, `FinanceRolloverScheduler`, `FinanceReportService`, `be/domain/finance/` e alle pagine `fe/pages/finance/` perché proponeva una struttura non realizzata (servizi separati per area, tabella `accounting_year_audit`, entità in `domain/`, dashboard con posizioni evento e filtri per stato economico) e prevedeva il calendario in lettura per il tesoriere, che apre invece solo il dettaglio evento. Ho descritto il servizio unico, i conti e le categorie archiviati che rifiutano nuovi movimenti con 409, il ricalcolo immediato dei riporti, il riporto giornaliero, la politica reale degli allegati, la versione non inviata dal client e il saldo iniziale con `initial_balance` della correzione 1.2. Restano 15 requisiti aperti, tra cui verifica del tipo reale degli allegati e idempotenza dei trasferimenti.

**[event-payout-shares-spec.md](../event-payout-shares-spec.md).** Ho aggiornato il documento in base al codice `be/domain/Users.java` (`@Table(name = "app_user")`) e `be/web/rest/FinanceResource.java` (`@RequiresTenantFeature`) perché citava una tabella `users` che si chiama `app_user`, dava il flag `FINANCE` come applicato al percorso mentre si applica al controller, e trattava come esistenti la riga di raccordo dei rendiconti e l'esclusione di un movimento, che sono introdotte da [report-exclusion-spec.md](../report-exclusion-spec.md) e non ancora realizzate.

**[report-exclusion-spec.md](../report-exclusion-spec.md).** Ho aggiornato il documento in base al codice `FinanceService`, `FinanceReportService` e alle pagine economiche perché giustificava le voci escluse nei registri con un saldo progressivo che esiste solo nell'estratto conto, dava per visibile nell'interfaccia l'elenco dei movimenti dell'evento (esposto solo dall'API) e citava un controllo di concorrenza dal client che non c'è (solo `@Version` sull'entità).

**[tenant-feature-flags-spec.md](../tenant-feature-flags-spec.md).** Ho aggiornato il documento in base al codice `be/domain/enumeration/TenantFeature.java`, `be/service/TenantFeatureService.java`, `TenantSchemaRegistry`, `be/service/impl/MediaServiceImpl.java`, `TenantsServiceImpl` e al servizio delle funzionalità del frontend perché descriveva otto flag mentre il codice ne ha undici (ascolto delle parti, fogli di affidamento, firma elettronica), metodi del registro mai creati, checkbox raggruppate che sono in un'unica griglia, la rimozione della sola sezione invece della navigazione a `/`, un contratto `unavailableAreas` che non esiste e la protezione di `/api/media` come cosa «da verificare», mentre `MediaServiceImpl` la applica già. Ho anche aggiunto che il controllo richiede un tenant attivo. Restano 4 requisiti aperti.

**[tenant-onboarding-import-spec.md](../tenant-onboarding-import-spec.md).** Ho aggiornato il documento in base al codice `be/service/onboarding/` (`OnboardingImportService`, `OnboardingValidationService`, `OnboardingWorker`, `OnboardingDomainApplicationService`, `OnboardingFileInspectionService`, `OnboardingRetentionService`) perché descriveva un preflight completo prima dell'applicazione, la lettura XLSX in streaming, la verifica dell'hash del file, il tipo MIME rilevato, un limite di 3 job per utente e di 10.000 problemi, una notifica di riepilogo e un advisory lock, assenti dal codice. Ho descritto i controlli reali all'avvio, l'indice univoco che ammette un solo job in applicazione, la lettura in memoria entro i limiti di `ZipSecureFile`, la scrittura diretta tramite repository e la conservazione della correzione 1.7, precisando che i job non conclusi non vengono puliti. Restano 24 requisiti aperti, compresa la scadenza dei job abbandonati.

**[postgres-tenant-schemas.md](../postgres-tenant-schemas.md).** Ho aggiornato il documento in base al codice `TenantsServiceImpl.save`, `TenantSchemaProvisioningService`, `TenantSchemaMigrationRunner` e `TenantProvisioningInterceptor` perché parlava di copia dei dati legacy da `public` e di migrazione all'avvio dei tenant presenti in OpenSearch. Il provisioning salva l'anagrafica, crea schema, registro e gruppo Keycloak ed elimina l'anagrafica in caso di errore; all'avvio le migrazioni ripartono dal registro dei tenant `ACTIVE`. Ho aggiunto la modalità bootstrap del primo accesso e la cancellazione normale.

**[migrazione-opensearch-postgresql.md](../migrazione-opensearch-postgresql.md).** Ho aggiornato il documento in base al codice `TenantIndexAspect`, `JpaPageableUtils` e `CommonFieldsOpenSearch` e alle migrazioni `20260829000002_enable_relational_soft_delete.xml` e `20261010000000_drop_inventory_report_export.xml` perché presentava lo schema E/R come completo e includeva `inventory_report_export`, poi eliminata, e descriveva come vincoli `UNIQUE` quelli che sono indici univoci parziali sulle sole righe attive. Ho segnalato i nomi e il codice inerte ereditati da OpenSearch e ricomposto la tabella delle aree, spezzata da un paragrafo inserito a metà.

### 2.3 Inventario e firma elettronica

**[inventory-management-spec.md](../inventory-management-spec.md).** Ho aggiornato il documento in base al codice `be/repository/inventory/InventoryItemRepository.java`, `InventoryService` e alla migrazione `20260824000002_finalize_relational_inventory.xml` perché descriveva la proiezione OpenSearch, l'outbox `inventory_search_outbox` e la generazione del PDF, tutti rimossi. Ho descritto la ricerca JPQL con il parametro `attention`, il flag `INVENTORY`, le fotografie su `media_asset` con anteprima, gli stati delle riconsegne, la cancellazione GDPR estesa ai fogli e agli atti e i ruoli che vedono il menu.

**[inventory-qr-code-spec.md](../inventory-qr-code-spec.md).** Ho aggiornato il documento in base al codice `InventoryScanResource`, `InventoryQrCodeService`, `InventoryIssueService` e `InventoryOperationProvider` perché prevedeva una colonna `tenant_code`, un flag solo globale, una dashboard ancora da realizzare e notifiche, metriche e log mai realizzati. Ho descritto il flag per tenant `INVENTORY_QR`, la schermata di scansione reale con `allowedActions`, notifiche e severità effettive, gli errori reali e la dashboard dei guasti. Restano 18 requisiti aperti.

**[inventory-loan-sheet-spec.md](../inventory-loan-sheet-spec.md).** Ho aggiornato il documento in base al codice `InventoryLoanSheetResource`, `InventoryLoanTemplateResource` e `UserInventoryLoanSheetResource` (annotati `INVENTORY_LOAN_SHEET`) e al contatore `inventory_loan_sheet_counter` perché attribuiva gli endpoint al flag `INVENTORY`, non considerava la modalità elettronica (atti annullati alla definizione, dati dell'Affidatario conservati) e conteneva il piano di implementazione. Restano 2 requisiti aperti.

**[inventory-electronic-signature-spec.md](../inventory-electronic-signature-spec.md).** Ho aggiornato il documento in base al codice `be/service/signature/` (scheduler della conservazione, `LoanSheetSignatureService`) perché conteneva lo stato per fasi con date e il piano di implementazione. Ho aggiornato la conservazione su tutti i tenant attivi (correzione 1.5), le conferme multiple anche per la firma dell'Associazione e i casi dell'errore `signature.settings.requirementsMissing`. Restano 6 requisiti aperti, tra cui la prova con una CIE reale e la verifica legale.

**[keycloak-authenticator/README.md](../../keycloak-authenticator/README.md).** Ho aggiornato il documento in base al realm `taurus-realm.json` e a `taurus-be/src/main/docker/keycloak.yml` perché indicava come temi del realm quelli del modulo, mentre il realm usa `taurus-theme`, montato da `taurus-be/src/main/docker/realm-config/taurus-theme`; la copia nel modulo non viene caricata. Ho anche precisato i nomi configurabili dei claim del mapper.

### 2.4 Notifiche e promemoria

**[notification-delivery-generalization-spec.md](../notification-delivery-generalization-spec.md).** Ho aggiornato il documento in base al codice `NotificationOutboxPublisher`, `CrossTenantNotificationPublisher`, `NotificationDispatcher`, `NotificationFeaturePolicy` e ai sei servizi che compongono notifiche proprie perché presentava come «problema attuale» il doppio percorso già eliminato, `NoticesAspect` come unico produttore, i metodi sincroni come da deprecare (sono già rimossi) e non conosceva lo stato `SUPPRESSED`. Ho aggiunto la sezione «Produttori fuori dall'aspect» e la regola della correzione 1.15.

**[notification-preferences-spec.md](../notification-preferences-spec.md).** Ho aggiornato il documento in base al codice `EventReminderProducer`, `PushReminderScheduler`, `NotificationPushScheduler`, `DataErasureService` e `PushSubscriptionResource` perché legava il profilo a `user_id` (è per `user_keycloak_id`, così vale anche per un super amministratore senza `app_user`), descriveva un orchestratore unico (sono due scheduler), una ripianificazione che annulla logicamente i job (li elimina fisicamente), un `schedule_revision` crescente (resta 0), proprietà di conservazione lette (i valori sono fissi) e nessun endpoint nei log (c'è nel debug della cancellazione). Restano 16 requisiti aperti.

**[notification-editorial-style.md](../notification-editorial-style.md).** Ho aggiornato il documento in base al codice dei sei produttori fuori dall'aspect (`EventPreparationService`, `EventPreparationNotificationScheduler`, `InventoryIssueService`, `InventoryQrCodeService`, `ScoreRecognitionNotifier`, `ScoreErrorReportService`) perché imponeva `NoticesAspect` come unico punto di composizione, mentre quei servizi compongono di proposito le notifiche che l'aspect non può intercettare. Ho aggiornato la regola e registrato in «Scostamenti noti» i 10 punti (20 occorrenze) in cui i testi violano lo stile, senza correggerli.

**[web-push-reminders-spec.md](../web-push-reminders-spec.md).** Ho aggiornato il documento in base al codice `EventReminderProducer`, `PushReminderScheduler` e `SecurityConfiguration` perché non descriveva le funzionalità tenant richieste, i percorsi per esterni e amministratori, l'eliminazione fisica dei job, l'esito `FAILED` definitivo e i testi con anteprima privata. L'analisi ha fatto emergere che la preferenza legacy `defaultReminderMinutes` è letta dal token di chi esegue l'operazione e non del destinatario: è registrata tra i 2 requisiti aperti.

### 2.5 Tracce, anteprima e media

**[media-asset-spec.md](../media-asset-spec.md).** Ho aggiornato il documento in base al codice `be/service/impl/MediaServiceImpl.java`, `MediaRepository.isUnreferenced`, `MediaStorageCleanupService`, `TenantStorageService` e alla migrazione `20260831000000_media_asset.xml` perché era scritto come progetto futuro e in parte non corrispondeva al codice:

- indicava la categoria `financial-attachments`, mentre gli allegati usano `finance`, e non elencava `score-versions`, `inventory-issue-photos` e `onboarding-imports`;
- mancavano cinque relazioni verso `media_asset`: `inventory_issue_photo`, `score_version`, `signature_document`, `signature_act` e `onboarding_import_job`;
- prevedeva una garbage collection dei record, mentre i record si cancellano solo logicamente e la pulizia elimina i file;
- ometteva che i file diversi da PNG, JPEG e PDF sono salvati come `application/octet-stream` e che tre stati (`UPLOADING`, `PROCESSING`, `FAILED`) non sono mai assegnati.

Ho riscritto al presente obiettivo, flusso di caricamento, pulizia e migrazione.

**[track-pdf-processing-spec.md](../track-pdf-processing-spec.md).** Ho aggiornato il documento in base al codice `TracksResource` (`POST /api/tracks/stream`), `TracksServiceImpl`, `Receiver` e al dettaglio della traccia perché non citava il caricamento dall'elenco delle tracce, che crea una traccia nuova, lo stato `NOT_FOUND` e il limite dei cinque job mostrati, e prevedeva un recupero dei job bloccati e metriche che non esistono. Restano 6 requisiti aperti.

**[track-pdf-manual-editing-spec.md](../track-pdf-manual-editing-spec.md).** Ho aggiornato il documento in base al codice `PdfManipulatorDialogComponent`, `ImageTransformationService.transformRendered` e `Receiver` perché affermava che frontend e backend applicano le regolazioni nello stesso ordine e che le annotazioni sono validate al caricamento. L'anteprima calcola i toni sulla pagina intera e il backend per zona; le annotazioni sono validate dal worker, in modo asincrono. Ho tolto la data di completamento e la tabella delle verifiche. Restano 2 requisiti aperti.

**[track-page-image-editing-spec.md](../track-page-image-editing-spec.md).** Ho aggiornato il documento in base al codice `ImageTransformationService`, `TrackPageImageService`, `MediaStorageCleanupService` e al dialogo di modifica perché dichiarava una data di completamento, un'azione «Riprova» con la stessa chiave di idempotenza, un timeout con risposta 503 e limiti configurabili. Il frontend genera una chiave nuova a ogni invio, il rendering non ha timeout e i limiti sono costanti; ho riportato gli errori reali `image.*` e la lettura dall'header della correzione 1.14. Restano 6 requisiti aperti.

**[track-parts-workspace-spec.md](../track-parts-workspace-spec.md).** Ho aggiornato il documento in base al workspace delle parti nel dettaglio della traccia (`fe/pages/tracks/detail/score-workspace/`) perché descriveva il trascinamento di pagine tra parti, il banner «Nuove parti disponibili», un divisore ridimensionabile e una tastiera più estesa, che non esistono, e presentava come «da confermare» decisioni già prese (identità della parte, versione della traccia). Restano 12 requisiti aperti.

**[score-preview-printing-spec.md](../score-preview-printing-spec.md).** Ho aggiornato il documento in base al codice `PrinterService` e alla pagina `/preview` perché conteneva lo stato al 12 settembre e un piano di adozione, e prometteva per una sorgente senza pagine e per il refresh un ritorno alla pagina precedente con messaggio, mentre `/preview` reindirizza alla home senza messaggio. Restano 4 requisiti aperti.

### 2.6 Musica e OMR

**[track-score-playback-spec.md](../track-score-playback-spec.md).** Ho aggiornato il documento in base al catalogo `docs/features.json` e a [documentation-implementation-alignment-spec.md](../documentation-implementation-alignment-spec.md) perché conteneva lo stato per fasi con date («realizzata il…», «in corso»), che le regole di governance tengono solo nel catalogo. Ho trasformato la sezione in «Scelte di sviluppo» e tolto gli stati dal piano. In base al codice `fe/dialogs/score-player-dialog/` ho corretto le etichette del mixer («Solo le mie parti», «Tutto tranne le mie») e ho indicato che gli strumenti esistenti sono configurati da una migrazione Liquibase, non da un'attività di avvio. Le prove manuali ancora da fare (`PLY-009`, `PLY-018`) e la verifica del catalogo degli strumenti sono i 3 requisiti aperti.

**[music-writing-editor-spec.md](../music-writing-editor-spec.md).** Ho aggiornato il documento in base al catalogo `docs/features.json` perché riportava la data di approvazione come frase di stato; lo stato della progettazione è nel catalogo.

**[taurus-omr/README.md](../../taurus-omr/README.md).** Ho aggiornato il documento in base al codice `taurus-omr/src/taurus_omr/config.py` e a `taurus-be/src/main/docker/omr.yml` perché mancava la variabile `TAURUS_OMR_MAX_IMAGE_PIXELS` (60 milioni di pixel per pagina) e descriveva una «rete interna» che `omr.yml` non configura: il file pubblica la porta solo su `127.0.0.1`.

### 2.7 Standard e richieste di supporto

**[application-errors-standard.md](../application-errors-standard.md).** Ho aggiornato il documento in base al codice `be/web/rest/errors/ApiErrorResponses.java`, `fe/constants/api-error-behaviors.ts`, l'interceptor HTTP e i dialoghi della firma elettronica perché era scritto come piano di adozione («viene eliminato», «diventano», «chiavi attuali») mentre l'adozione è completa, e l'elenco delle aree non comprendeva `scorePlayback`. Ho riscritto quei passaggi al presente, rinominato le colonne in «Chiave precedente», aggiunto i dialoghi della firma tra gli usi di `HANDLED_ERRORS` e registrato nel catalogo le due chiavi delle correzioni 1.7 e 1.8.

**[taurus-layout-standard.md](../taurus-layout-standard.md).** Ho aggiornato il documento in base ai componenti di `fe/components/`, ai dialoghi di `fe/dialogs/`, alle pagine di errore e a `taurus-fe/angular.json` perché presentava il censimento iniziale come «stato attuale», i componenti condivisi come «da introdurre» (esistono tutti) e la correzione delle pagine di errore come «prevista» (è fatta). Ho registrato in «Scostamenti noti» l'avviso del budget degli stili a 7 kB contro i 2 kB della regola, tre dialoghi a piena finestra senza `dialog-shell` e due usi diretti di `p-message`.

**[support-requests-spec.md](../support-requests-spec.md).** Ho aggiornato il documento in base al documento [application-errors-standard.md](../application-errors-standard.md) e al codice `ApiErrorResponses` perché prevedeva un 401 per il tenant mancante e un «correlation ID», mentre lo standard usa 403 per l'utente senza tenant e l'identificativo `errorId`. La funzionalità non è pianificata: ho allineato solo il contratto degli errori.

### 2.8 Runbook, README e governance

**[runbook-operativo.md](../runbook-operativo.md).** Ho aggiornato il documento in base agli scheduler `InventoryLoanSheetRetentionScheduler` e `OnboardingCleanupScheduler` e a `.github/workflows/verify.yml` perché la tabella dei job pianificati non elencava i due job di conservazione, mancavano i comandi del catalogo errori e il documento si dichiarava allineato a una data fissa.

**[runbook-test.md](../runbook-test.md).** Ho aggiornato il documento in base al contenuto del repository e alle correzioni della sezione 1 perché i conteggi delle suite erano superati (ora 170 file di test backend, 68 specifiche frontend, 3 classi di test Keycloak) e mancavano i casi delle regressioni corrette: ho aggiunto `FIN-012` (saldo iniziale) e `REC-006` (serie ricorrenti) e precisato `ONB-010` (conservazione dell'onboarding).

**[README.md](../../README.md).** Ho aggiornato il documento in base al modulo `taurus-omr`, a `taurus-be/src/main/docker/services.yml` e `.env`, a `taurus-fe/scripts/fetch-soundfont.mjs` e ai workflow perché non citava il modulo OMR, elencava tre servizi Docker invece di cinque (mancavano OMR e Mailpit), non spiegava il download del SoundFont e ometteva i controlli del catalogo errori e la pubblicazione dell'immagine OMR.

**[taurus-be/README.md](../../taurus-be/README.md).** Ho riscritto il documento in base a `taurus-be/pom.xml`, `application.yml`, `services.yml` e al `Dockerfile` perché era il README generato da JHipster, con istruzioni per Okta, Auth0 e un realm `jhipster` che non valgono per Taurus.

**[taurus-fe/README.md](../../taurus-fe/README.md).** Ho riscritto il documento in base a `taurus-fe/package.json`, `angular.json`, `src/environments/` e allo script del SoundFont perché era il README del template Sakai e di Angular CLI (`ng serve`, `ng e2e`), senza i comandi e le convenzioni reali del modulo.

**[taurus-info/README.md](../../taurus-info/README.md).** Ho aggiornato il documento in base alla pagina `taurus-info/src/pages/termini-applicativo.astro` perché indicava per i termini di utilizzo la versione 1.1 del 3 settembre, mentre la pagina pubblica la 1.2 del 7 settembre.

**[documentation-implementation-alignment-spec.md](../documentation-implementation-alignment-spec.md).** Ho aggiornato il documento in base a `scripts/docs/feature-catalog-lib.mjs`, agli altri script di `scripts/docs/` e a `.github/workflows/verify.yml` perché non ammetteva il modulo `taurus-omr`, che il validatore accetta, non citava il job `omr` della CI né gli script `affected-modules.mjs`, `check-feature-drift.mjs` e `check-error-catalog.mjs`, e descriveva come «oggi» la situazione precedente all'adozione del catalogo.

### 2.9 Documenti verificati senza modifiche

Ho confrontato con il codice anche [omr-text-recognition-spec.md](../omr-text-recognition-spec.md), [track-instrument-suggestions-spec.md](../track-instrument-suggestions-spec.md), `AGENTS.md`, `taurus-omr/THIRD_PARTY_NOTICES.md` e `.github/pull_request_template.md`: il contesto tecnico e le versioni citate corrispondono al repository, quindi non li ho modificati.

## 3. Catalogo e matrice

Ho aggiornato `docs/features.json` in base alle specifiche allineate e al repository perché il catalogo deve riflettere lo stato reale:

- 13 voci passano da `implemented` a `in-progress` perché le loro specifiche hanno requisiti aperti: `event-preparation`, `financial-management`, `inventory-qr-code`, `notification-preferences`, `operational-dashboard`, `recurring-calendar-events`, `score-preview-printing`, `tenant-feature-flags`, `tenant-onboarding-import`, `track-page-image-editing`, `track-pdf-manual-editing`, `track-pdf-processing`, `web-push-reminders`;
- note superate corrette: «undici flag» al posto di otto e nove, nessun merge ancora da fare per i fogli di affidamento, regressione di `RelationalSoftDeleteIT` già risolta nel test;
- evidenze aggiunte: le due migrazioni del 22 ottobre, quella del 21 ottobre sui flag, l'audit dei feed, il fuso del tenant, i provider della dashboard, servizio e scheduler della conservazione dell'onboarding con il relativo test;
- `lastVerifiedOn` al 2026-10-23 per tutte le 35 voci.

Ho poi rigenerato `docs/features.md` con `generate-feature-index.mjs`.

## 4. Rilievi registrati e non corretti

Questi punti sono emersi dal confronto ma richiedono una decisione: sono registrati nei documenti e il codice non è stato toccato.

| Rilievo | Dove è registrato |
| --- | --- |
| `PATCH /api/finance/events/{id}/budget` non marca l'occorrenza di una serie come eccezione: una modifica successiva della serie sovrascrive compenso e costi decisi dal tesoriere | requisiti aperti di [recurring-calendar-events-spec.md](../recurring-calendar-events-spec.md) |
| `angular.json` porta l'avviso del budget degli stili a 7 kB contro i 2 kB dello standard; `preview.component.scss` è a circa 200 byte dalla soglia d'errore | scostamenti noti di [taurus-layout-standard.md](../taurus-layout-standard.md) |
| `image-editor-dialog`, `score-editor-dialog` e `score-player-dialog` non usano `dialog-shell`; due dialoghi usano `p-message` direttamente | scostamenti noti di [taurus-layout-standard.md](../taurus-layout-standard.md) |
| I job di onboarding `UPLOADED` e `READY` abbandonati conservano file e dati personali senza scadenza | requisiti aperti di [tenant-onboarding-import-spec.md](../tenant-onboarding-import-spec.md) |
| La preferenza legacy dell'anticipo dei promemoria è letta dal token di chi esegue l'operazione, non del destinatario | requisiti aperti di [web-push-reminders-spec.md](../web-push-reminders-spec.md) |
| Dieci testi di notifica violano lo stile editoriale | scostamenti noti di [notification-editorial-style.md](../notification-editorial-style.md) |

## 5. Verifiche eseguite

| Verifica | Esito |
| --- | --- |
| `node scripts/docs/validate-feature-catalog.mjs` | catalogo valido, 35 voci |
| `node scripts/docs/generate-feature-index.mjs` | matrice rigenerata |
| `node --test scripts/docs/feature-catalog.test.mjs` ed `error-catalog.test.mjs` | 24 test superati |
| `node scripts/docs/check-error-catalog.mjs` | 0 errori, 12 avvisi già noti sui parametri non verificabili staticamente |
| `npm test -- --watch=false --browsers=ChromeHeadless` | 338 test frontend superati |
| `npm run build` | build di produzione riuscita, con gli avvisi di budget già noti |
| `./mvnw verify` | 659 test unitari e 146 di integrazione superati; le due migrazioni nuove eseguite su PostgreSQL |

Questo report sta in `docs/reports/`, insieme al [documento di revisione](Revisione%20allineamento%20documentazione%20e%20codice%20Taurus.md) che riassume lo stato del lavoro: il validatore controlla solo i file al primo livello di `docs/`, quindi nessuno dei due richiede una voce nel catalogo né compare in `features.md`.
