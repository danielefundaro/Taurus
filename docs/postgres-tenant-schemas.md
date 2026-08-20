# PostgreSQL schema per tenant

Il backend usa il claim JWT `tenant` per selezionare lo schema PostgreSQL delle entità tenant-scoped.
Le entità globali, come `legal_document`, restano nello schema `public`.
Le tabelle degli schemi tenant non contengono `tenant_code`: l'appartenenza è determinata esclusivamente dallo
schema selezionato sulla connessione. `tenant_code` rimane nel registro globale e, durante la fase di transizione,
nelle vecchie tabelle `public` usate come sorgente per attribuire e copiare i dati preesistenti.

## Provisioning

La creazione di un tenant tramite `POST /api/tenants` esegue, senza riavvio del backend:

1. generazione di un identificatore sicuro `tenant_<hash>`;
2. registrazione in `public.tenant_schema_registry` con stato `PROVISIONING`;
3. `CREATE SCHEMA IF NOT EXISTS` protetto da advisory lock PostgreSQL;
4. applicazione di `config/liquibase/tenant-master.xml` nello schema;
5. copia idempotente degli eventuali record legacy del tenant da `public`;
6. aggiornamento del registro allo stato `ACTIVE`.

Il nome fisico non contiene il codice inserito dall'utente. La relazione tra codice tenant e schema è consultabile con:

```sql
SELECT tenant_code, schema_name, status, last_error, updated_at
FROM public.tenant_schema_registry
ORDER BY tenant_code;
```

Al bootstrap vengono rieseguite le migration per i tenant non eliminati già presenti in OpenSearch. Liquibase conserva
`databasechangelog` e `databasechangeloglock` in ogni schema, quindi ciascun tenant ha una versione verificabile e il
provisioning è ripetibile.

## Routing runtime

`TenantContextInterceptor` legge il tenant dall'identità autenticata. Hibernate ottiene una connessione dal pool,
imposta lo schema deterministico del tenant e la riporta sempre a `public` prima di restituirla al pool. Le richieste
senza claim tenant usano `public`.

Scheduler, outbox inventario, retention e notifiche push iterano il registro centrale ed eseguono una transazione
separata per ogni tenant.

## Permessi PostgreSQL

L'utente configurato nel datasource deve avere almeno:

```sql
GRANT CREATE ON DATABASE taurus TO taurus;
GRANT USAGE, CREATE ON SCHEMA public TO taurus;
```

In produzione è preferibile usare un ruolo dedicato al provisioning/migration. Il runtime deve poter usare e
modificare le tabelle tenant, ma non dovrebbe consentire a input o utenti finali di eseguire DDL.

## Cancellazione

La cancellazione GDPR del tenant pianifica `DROP SCHEMA ... CASCADE` dopo il commit della transazione applicativa,
evitando lock circolari con le query JPA ancora attive. L'operazione è intenzionalmente distruttiva e il registro passa
allo stato `DELETED`.

## Verifica

Test rapidi:

```powershell
.\mvnw.cmd "-Dtest=TenantContextTest,TenantSchemaNameResolverTest,SchemaMultiTenantConnectionProviderTest" test
```

Test di provisioning su PostgreSQL reale:

```powershell
.\mvnw.cmd "-Dtest=TenantSchemaProvisioningServiceIT" test
```

Il test di integrazione usa Testcontainers quando Docker è disponibile. Può anche essere puntato a un PostgreSQL
temporaneo tramite `taurus.test.postgres.url`, `taurus.test.postgres.username` e `taurus.test.postgres.password`.
