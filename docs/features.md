<!-- File generato: non modificare manualmente. -->
# Catalogo funzionalità Taurus

Fonte: [`docs/features.json`](features.json). Rigenerare con `node scripts/docs/generate-feature-index.mjs`.

| ID | Funzionalità | Tipo | Progettazione | Consegna | Moduli | Migrazioni | Test | Release | Verificata |
| --- | --- | --- | --- | --- | --- | --- | ---: | --- | --- |
| `documentation-implementation-alignment` | [Governance e allineamento tra documentazione e implementazione](documentation-implementation-alignment-spec.md) | standard | approved | implemented | Repository | 0 | [1](#documentation-implementation-alignment-evidenze) | non rilasciata | 2026-09-04 |
| `event-preparation` | [Preparazione operativa dell'evento](event-preparation-spec.md) | feature | draft | not-planned | BE, FE | 0 | 0 | non rilasciata | 2026-09-04 |
| `external-calendar-feed` | [Feed calendario esterno in sola lettura](external-calendar-feed-spec.md) | feature | draft | not-planned | BE, FE | 0 | 0 | non rilasciata | 2026-09-04 |
| `financial-management` | [Gestione economica, cassa e conti correnti](financial-management-spec.md) | feature | approved | implemented | BE, FE | [1](#financial-management-evidenze) | [1](#financial-management-evidenze) | non rilasciata | 2026-09-04 |
| `inventory-management` | [Gestione inventario tenant](inventory-management-spec.md) | feature | approved | implemented | BE, FE | [1](#inventory-management-evidenze) | [1](#inventory-management-evidenze) | non rilasciata | 2026-09-04 |
| `inventory-qr-code` | [QR code ed etichette per l'inventario](inventory-qr-code-spec.md) | feature | draft | not-planned | BE, FE | 0 | 0 | non rilasciata | 2026-09-04 |
| `media-asset` | [Gestione centralizzata dei media](media-asset-spec.md) | platform | approved | implemented | BE, FE | [1](#media-asset-evidenze) | [1](#media-asset-evidenze) | non rilasciata | 2026-09-04 |
| `notification-delivery-generalization` | [Generalizzazione della consegna delle notifiche interne](notification-delivery-generalization-spec.md) | platform | approved | implemented | BE, FE | [1](#notification-delivery-generalization-evidenze) | [1](#notification-delivery-generalization-evidenze) | non rilasciata | 2026-09-04 |
| `notification-editorial-style` | [Stile editoriale delle notifiche interne](notification-editorial-style.md) | standard | approved | implemented | BE, FE | 0 | [2](#notification-editorial-style-evidenze) | non rilasciata | 2026-09-04 |
| `notification-preferences` | [Preferenze notifiche granulari](notification-preferences-spec.md) | feature | draft | not-planned | BE, FE | 0 | 0 | non rilasciata | 2026-09-04 |
| `opensearch-postgresql-migration` | [Migrazione strutturale da OpenSearch a PostgreSQL](migrazione-opensearch-postgresql.md) | migration | approved | implemented | BE | [1](#opensearch-postgresql-migration-evidenze) | [1](#opensearch-postgresql-migration-evidenze) | non rilasciata | 2026-09-04 |
| `operational-dashboard` | [Dashboard operativa trasversale](operational-dashboard-spec.md) | feature | approved | implemented | BE, FE | 0 | [3](#operational-dashboard-evidenze) | non rilasciata | 2026-09-04 |
| `postgres-tenant-schemas` | [PostgreSQL schema per tenant](postgres-tenant-schemas.md) | platform | approved | implemented | BE | [1](#postgres-tenant-schemas-evidenze) | [1](#postgres-tenant-schemas-evidenze) | non rilasciata | 2026-09-04 |
| `recurring-calendar-events` | [Eventi ricorrenti del calendario](recurring-calendar-events-spec.md) | feature | approved | implemented | BE, FE | [1](#recurring-calendar-events-evidenze) | [1](#recurring-calendar-events-evidenze) | non rilasciata | 2026-09-04 |
| `taurus-layout-standard` | [Taurus Layout Standard](taurus-layout-standard.md) | standard | approved | implemented | FE | 0 | [1](#taurus-layout-standard-evidenze) | non rilasciata | 2026-09-04 |
| `tenant-feature-flags` | [Funzionalità Economia e Inventario configurabili per tenant](tenant-feature-flags-spec.md) | feature | approved | implemented | BE, FE | [2](#tenant-feature-flags-evidenze) | [9](#tenant-feature-flags-evidenze) | non rilasciata | 2026-09-04 |
| `tenant-onboarding-import` | [Onboarding guidato e importazione iniziale del tenant](tenant-onboarding-import-spec.md) | feature | draft | not-planned | BE, FE | 0 | 0 | non rilasciata | 2026-09-04 |
| `web-push-reminders` | [Promemoria eventi tramite Web Push](web-push-reminders-spec.md) | feature | approved | implemented | BE, FE | [2](#web-push-reminders-evidenze) | [2](#web-push-reminders-evidenze) | non rilasciata | 2026-09-04 |

## Evidenze

<a id="documentation-implementation-alignment-evidenze"></a>
### Governance e allineamento tra documentazione e implementazione

- Implementazione: [`scripts/docs/feature-catalog-lib.mjs`](../scripts/docs/feature-catalog-lib.mjs), [`scripts/docs/validate-feature-catalog.mjs`](../scripts/docs/validate-feature-catalog.mjs), [`scripts/docs/generate-feature-index.mjs`](../scripts/docs/generate-feature-index.mjs)
- Migrazioni: nessuna
- Test: [`scripts/docs/feature-catalog.test.mjs`](../scripts/docs/feature-catalog.test.mjs)

<a id="event-preparation-evidenze"></a>
### Preparazione operativa dell'evento

- Implementazione: nessuna
- Migrazioni: nessuna
- Test: nessuna

<a id="external-calendar-feed-evidenze"></a>
### Feed calendario esterno in sola lettura

- Implementazione: nessuna
- Migrazioni: nessuna
- Test: nessuna

<a id="financial-management-evidenze"></a>
### Gestione economica, cassa e conti correnti

- Implementazione: [`taurus-be/src/main/java/com/fundaro/zodiac/taurus/web/rest/FinanceResource.java`](../taurus-be/src/main/java/com/fundaro/zodiac/taurus/web/rest/FinanceResource.java), [`taurus-fe/src/app/service/finance.service.ts`](../taurus-fe/src/app/service/finance.service.ts)
- Migrazioni: [`taurus-be/src/main/resources/config/liquibase/changelog/20260901000002_add_finance.xml`](../taurus-be/src/main/resources/config/liquibase/changelog/20260901000002_add_finance.xml)
- Test: [`taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/impl/FinanceServiceTest.java`](../taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/impl/FinanceServiceTest.java)
- Note: La migration è verificata dal plugin Liquibase; i test di servizio coprono il contratto economico principale.

<a id="inventory-management-evidenze"></a>
### Gestione inventario tenant

- Implementazione: [`taurus-be/src/main/java/com/fundaro/zodiac/taurus/web/rest/InventoryResource.java`](../taurus-be/src/main/java/com/fundaro/zodiac/taurus/web/rest/InventoryResource.java), [`taurus-fe/src/app/service/inventory.service.ts`](../taurus-fe/src/app/service/inventory.service.ts)
- Migrazioni: [`taurus-be/src/main/resources/config/liquibase/changelog/20260818090000_add_inventory.xml`](../taurus-be/src/main/resources/config/liquibase/changelog/20260818090000_add_inventory.xml)
- Test: [`taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/impl/InventoryServiceTest.java`](../taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/impl/InventoryServiceTest.java)
- Note: La migration è verificata dal plugin Liquibase; i test di servizio coprono vincoli di quantità, revisioni e riconsegne.

<a id="inventory-qr-code-evidenze"></a>
### QR code ed etichette per l'inventario

- Implementazione: nessuna
- Migrazioni: nessuna
- Test: nessuna

<a id="media-asset-evidenze"></a>
### Gestione centralizzata dei media

- Implementazione: [`taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/impl/MediaServiceImpl.java`](../taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/impl/MediaServiceImpl.java), [`taurus-fe/src/app/service/media.service.ts`](../taurus-fe/src/app/service/media.service.ts)
- Migrazioni: [`taurus-be/src/main/resources/config/liquibase/changelog/20260831000000_media_asset.xml`](../taurus-be/src/main/resources/config/liquibase/changelog/20260831000000_media_asset.xml)
- Test: [`taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/impl/MediaServiceImplTest.java`](../taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/impl/MediaServiceImplTest.java)
- Note: La migration è verificata dal plugin Liquibase e il test del servizio copre il flusso di compatibilità dei media.

<a id="notification-delivery-generalization-evidenze"></a>
### Generalizzazione della consegna delle notifiche interne

- Implementazione: [`taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/impl/NotificationOutboxPublisher.java`](../taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/impl/NotificationOutboxPublisher.java), [`taurus-fe/src/app/service/notification-center.service.ts`](../taurus-fe/src/app/service/notification-center.service.ts)
- Migrazioni: [`taurus-be/src/main/resources/config/liquibase/changelog/20260903000001_generalize_notification_outbox.xml`](../taurus-be/src/main/resources/config/liquibase/changelog/20260903000001_generalize_notification_outbox.xml)
- Test: [`taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/impl/NotificationDeliveryIT.java`](../taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/impl/NotificationDeliveryIT.java)

<a id="notification-editorial-style-evidenze"></a>
### Stile editoriale delle notifiche interne

- Implementazione: [`taurus-be/src/main/java/com/fundaro/zodiac/taurus/aop/notices/NoticesAspect.java`](../taurus-be/src/main/java/com/fundaro/zodiac/taurus/aop/notices/NoticesAspect.java), [`taurus-fe/src/app/service/notification-presentation.service.ts`](../taurus-fe/src/app/service/notification-presentation.service.ts)
- Migrazioni: nessuna
- Test: [`taurus-be/src/test/java/com/fundaro/zodiac/taurus/aop/notices/NoticesAspectTest.java`](../taurus-be/src/test/java/com/fundaro/zodiac/taurus/aop/notices/NoticesAspectTest.java), [`taurus-fe/src/app/service/notification-presentation.service.spec.ts`](../taurus-fe/src/app/service/notification-presentation.service.spec.ts)

<a id="notification-preferences-evidenze"></a>
### Preferenze notifiche granulari

- Implementazione: nessuna
- Migrazioni: nessuna
- Test: nessuna

<a id="opensearch-postgresql-migration-evidenze"></a>
### Migrazione strutturale da OpenSearch a PostgreSQL

- Implementazione: [`taurus-be/src/main/java/com/fundaro/zodiac/taurus/repository/CatalogRepository.java`](../taurus-be/src/main/java/com/fundaro/zodiac/taurus/repository/CatalogRepository.java)
- Migrazioni: [`taurus-be/src/main/resources/config/liquibase/changelog/20260824000001_add_relational_catalog.xml`](../taurus-be/src/main/resources/config/liquibase/changelog/20260824000001_add_relational_catalog.xml)
- Test: [`taurus-be/src/test/java/com/fundaro/zodiac/taurus/multitenancy/TenantSchemaProvisioningServiceIT.java`](../taurus-be/src/test/java/com/fundaro/zodiac/taurus/multitenancy/TenantSchemaProvisioningServiceIT.java)

<a id="operational-dashboard-evidenze"></a>
### Dashboard operativa trasversale

- Implementazione: [`taurus-be/src/main/java/com/fundaro/zodiac/taurus/web/rest/OperationalDashboardResource.java`](../taurus-be/src/main/java/com/fundaro/zodiac/taurus/web/rest/OperationalDashboardResource.java), [`taurus-be/src/main/java/com/fundaro/zodiac/taurus/web/rest/NotificationDeliveryAdminResource.java`](../taurus-be/src/main/java/com/fundaro/zodiac/taurus/web/rest/NotificationDeliveryAdminResource.java), [`taurus-fe/src/app/pages/dashboard/components/operations-widget/operations-widget.component.ts`](../taurus-fe/src/app/pages/dashboard/components/operations-widget/operations-widget.component.ts), [`taurus-fe/src/app/pages/admin/notification-delivery/notification-delivery.component.ts`](../taurus-fe/src/app/pages/admin/notification-delivery/notification-delivery.component.ts)
- Migrazioni: nessuna
- Test: [`taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/OperationalDashboardServiceTest.java`](../taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/OperationalDashboardServiceTest.java), [`taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/NotificationDeliveryAdminServiceTest.java`](../taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/NotificationDeliveryAdminServiceTest.java), [`taurus-fe/src/app/pages/dashboard/components/operations-widget/operations-widget.component.spec.ts`](../taurus-fe/src/app/pages/dashboard/components/operations-widget/operations-widget.component.spec.ts)

<a id="postgres-tenant-schemas-evidenze"></a>
### PostgreSQL schema per tenant

- Implementazione: [`taurus-be/src/main/java/com/fundaro/zodiac/taurus/multitenancy/TenantSchemaProvisioningService.java`](../taurus-be/src/main/java/com/fundaro/zodiac/taurus/multitenancy/TenantSchemaProvisioningService.java)
- Migrazioni: [`taurus-be/src/main/resources/config/liquibase/changelog/20260820000000_add_tenant_schema_registry.xml`](../taurus-be/src/main/resources/config/liquibase/changelog/20260820000000_add_tenant_schema_registry.xml)
- Test: [`taurus-be/src/test/java/com/fundaro/zodiac/taurus/multitenancy/TenantSchemaProvisioningServiceIT.java`](../taurus-be/src/test/java/com/fundaro/zodiac/taurus/multitenancy/TenantSchemaProvisioningServiceIT.java)

<a id="recurring-calendar-events-evidenze"></a>
### Eventi ricorrenti del calendario

- Implementazione: [`taurus-be/src/main/java/com/fundaro/zodiac/taurus/web/rest/CalendarEventSeriesResource.java`](../taurus-be/src/main/java/com/fundaro/zodiac/taurus/web/rest/CalendarEventSeriesResource.java), [`taurus-fe/src/app/service/calendar-event-series.service.ts`](../taurus-fe/src/app/service/calendar-event-series.service.ts)
- Migrazioni: [`taurus-be/src/main/resources/config/liquibase/changelog/20260831000003_recurring_calendar_events.xml`](../taurus-be/src/main/resources/config/liquibase/changelog/20260831000003_recurring_calendar_events.xml)
- Test: [`taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/impl/CalendarEventSeriesServiceImplTest.java`](../taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/impl/CalendarEventSeriesServiceImplTest.java)
- Note: La migration è verificata dal plugin Liquibase; il test del servizio copre generazione, aggiornamento e rimozione delle occorrenze.

<a id="taurus-layout-standard-evidenze"></a>
### Taurus Layout Standard

- Implementazione: [`taurus-fe/src/app/components/page-header/page-header.component.ts`](../taurus-fe/src/app/components/page-header/page-header.component.ts), [`taurus-fe/src/app/components/dialog-shell/dialog-shell.component.ts`](../taurus-fe/src/app/components/dialog-shell/dialog-shell.component.ts)
- Migrazioni: nessuna
- Test: [`taurus-fe/src/app/service/list-layout.service.spec.ts`](../taurus-fe/src/app/service/list-layout.service.spec.ts)

<a id="tenant-feature-flags-evidenze"></a>
### Funzionalità Economia e Inventario configurabili per tenant

- Implementazione: [`taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/TenantFeatureService.java`](../taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/TenantFeatureService.java), [`taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/impl/CalendarEventsServiceImpl.java`](../taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/impl/CalendarEventsServiceImpl.java), [`taurus-fe/src/app/service/tenant-feature.service.ts`](../taurus-fe/src/app/service/tenant-feature.service.ts), [`taurus-fe/src/app/guard/tenant-feature.guard.ts`](../taurus-fe/src/app/guard/tenant-feature.guard.ts)
- Migrazioni: [`taurus-be/src/main/resources/config/liquibase/changelog/20260904000000_tenant_feature_flags.xml`](../taurus-be/src/main/resources/config/liquibase/changelog/20260904000000_tenant_feature_flags.xml), [`taurus-be/src/main/resources/config/liquibase/changelog/20260904000001_tenant_feature_notification_status.xml`](../taurus-be/src/main/resources/config/liquibase/changelog/20260904000001_tenant_feature_notification_status.xml)
- Test: [`taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/TenantFeatureServiceTest.java`](../taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/TenantFeatureServiceTest.java), [`taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/impl/CalendarEventSeriesServiceImplTest.java`](../taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/impl/CalendarEventSeriesServiceImplTest.java), [`taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/impl/FinanceRolloverSchedulerTest.java`](../taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/impl/FinanceRolloverSchedulerTest.java), [`taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/impl/InventoryExpirationNotificationSchedulerTest.java`](../taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/impl/InventoryExpirationNotificationSchedulerTest.java), [`taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/impl/NotificationDispatcherTest.java`](../taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/impl/NotificationDispatcherTest.java), [`taurus-fe/src/app.routes.spec.ts`](../taurus-fe/src/app.routes.spec.ts), [`taurus-fe/src/app/service/tenant-feature.service.spec.ts`](../taurus-fe/src/app/service/tenant-feature.service.spec.ts), [`taurus-fe/src/app/guard/tenant-feature.guard.spec.ts`](../taurus-fe/src/app/guard/tenant-feature.guard.spec.ts), [`taurus-fe/src/app/components/menu/menu.component.spec.ts`](../taurus-fe/src/app/components/menu/menu.component.spec.ts)
- Note: Le migration sono verificate con PostgreSQL/Testcontainers; i test coprono isolamento, payload minimale, rifiuto dei moduli disabilitati, conservazione dei dati economici, soppressione notifiche, menu e route guard.

<a id="tenant-onboarding-import-evidenze"></a>
### Onboarding guidato e importazione iniziale del tenant

- Implementazione: nessuna
- Migrazioni: nessuna
- Test: nessuna

<a id="web-push-reminders-evidenze"></a>
### Promemoria eventi tramite Web Push

- Implementazione: [`taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/impl/PushServiceImpl.java`](../taurus-be/src/main/java/com/fundaro/zodiac/taurus/service/impl/PushServiceImpl.java), [`taurus-fe/src/app/service/push-notification.service.ts`](../taurus-fe/src/app/service/push-notification.service.ts)
- Migrazioni: [`taurus-be/src/main/resources/config/liquibase/changelog/20260708000001_add_entity_PushSubscription.xml`](../taurus-be/src/main/resources/config/liquibase/changelog/20260708000001_add_entity_PushSubscription.xml), [`taurus-be/src/main/resources/config/liquibase/changelog/20260710000001_add_entity_PushReminder.xml`](../taurus-be/src/main/resources/config/liquibase/changelog/20260710000001_add_entity_PushReminder.xml)
- Test: [`taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/impl/CalendarEventsServiceImplTest.java`](../taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/impl/CalendarEventsServiceImplTest.java), [`taurus-be/src/test/java/com/fundaro/zodiac/taurus/rabbitmq/ReceiverTest.java`](../taurus-be/src/test/java/com/fundaro/zodiac/taurus/rabbitmq/ReceiverTest.java)
- Note: Le migration sono verificate dal plugin Liquibase e i test coprono pianificazione e consumo dei promemoria. La descrizione storica dello stack è integrata dalle decisioni più recenti su PostgreSQL e consegna delle notifiche.

