# PostgreSQL schema per tenant

## Stato del documento

ID catalogo: `postgres-tenant-schemas`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

Il backend usa il claim JWT `tenant` per selezionare lo schema PostgreSQL delle entità tenant-scoped.
Le entità globali, come `legal_document`, restano nello schema `public`.
Le tabelle degli schemi tenant non contengono `tenant_code`: l'appartenenza è determinata esclusivamente dallo
schema selezionato sulla connessione. `tenant_code` rimane nel registro globale.

## Provisioning

La creazione di un tenant tramite `POST /api/tenants` (`TenantsServiceImpl.save`) esegue, senza riavvio del backend:

1. salvataggio dell'anagrafica in `public.tenant`;
2. generazione di un identificatore sicuro `tenant_<hash>`;
3. registrazione in `public.tenant_schema_registry` con stato `PROVISIONING`;
4. `CREATE SCHEMA IF NOT EXISTS` protetto da advisory lock PostgreSQL;
5. applicazione di `config/liquibase/tenant-master.xml` nello schema;
6. aggiornamento del registro allo stato `ACTIVE`;
7. collegamento del registro all'anagrafica (`linkTenant`, colonna `tenant_id`);
8. creazione del gruppo Keycloak del tenant.

Se schema o migration falliscono, il registro passa allo stato `FAILED` con il messaggio in `last_error`. In caso di
errore in qualunque passo l'anagrafica appena salvata viene eliminata; un errore di provisioning dello schema risponde
`500` con `tenant.schema.provisioningFailed`. Il metodo `qualified()` di `TenantSchemaProvisioningService` non è usato.

Il nome fisico non contiene il codice inserito dall'utente. La relazione tra codice tenant e schema è consultabile con:

```sql
SELECT tenant_code, schema_name, status, last_error, updated_at
FROM public.tenant_schema_registry
ORDER BY tenant_code;
```

All'avvio `TenantSchemaMigrationRunner` legge dal registro i tenant `ACTIVE` e riesegue le migration di ciascuno
schema; un errore viene registrato in `last_error`, senza cambiare lo stato, e interrompe l'avvio. Liquibase conserva
`databasechangelog` e `databasechangeloglock` in ogni schema, quindi ciascun tenant ha una versione verificabile e il
provisioning è ripetibile.

## Routing runtime

`TenantContextInterceptor` legge il tenant dall'identità autenticata. Hibernate ottiene una connessione dal pool,
imposta lo schema deterministico del tenant e la riporta sempre a `public` prima di restituirla al pool. Le richieste
senza claim tenant usano `public`.

Subito dopo, `TenantProvisioningInterceptor` verifica che il tenant della richiesta abbia uno schema `ACTIVE` nel
registro. Se non ce l'ha, ad esempio al primo accesso con il gruppo di bootstrap `temp`, risponde `409` con
`error.tenant.notProvisioned` invece di lasciare fallire la query su relazioni inesistenti. Sono escluse le API annotate
con `@TenantSchemaNotRequired`, che usano solo `public` o Keycloak: gestione tenant, capability delle funzioni, account,
`auth-info`, logout e `GET /api/tenant-status`. Quest'ultima indica al frontend se lavorare in modalità bootstrap. L'esito positivo
della verifica resta in cache per 30 secondi per istanza, quello negativo no: un tenant appena creato è subito
utilizzabile, uno eliminato viene respinto entro 30 secondi.

Scheduler, outbox delle notifiche, retention e notifiche push iterano il registro centrale ed eseguono una transazione
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

La normale cancellazione del tenant (`DELETE /api/tenants/{id}`) elimina logicamente l'anagrafica, porta il registro
allo stato `DELETED` e rimuove il gruppo Keycloak; lo schema resta nel database.

La cancellazione GDPR del tenant pianifica `DROP SCHEMA ... CASCADE` dopo il commit della transazione applicativa,
evitando lock circolari con le query JPA ancora attive. L'operazione è intenzionalmente distruttiva e il registro passa
allo stato `DELETED`.

## Verifica

Test rapidi:

```powershell
.\mvnw.cmd "-Dtest=TenantContextTest,TenantSchemaNameResolverTest,SchemaMultiTenantConnectionProviderTest,TenantProvisioningStatusTest,TenantProvisioningInterceptorTest" test
```

Test di provisioning su PostgreSQL reale:

```powershell
.\mvnw.cmd "-Dtest=TenantSchemaProvisioningServiceIT" test
```

Modalità bootstrap del primo accesso, cioè stato del tenant e risposta `409` alle API dello schema tenant per un tenant
senza schema:

```powershell
.\mvnw.cmd "-Dtest=NoSuchTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dit.test=TenantBootstrapIT" verify
```

Il test di integrazione usa Testcontainers quando Docker è disponibile. Può anche essere puntato a un PostgreSQL
temporaneo tramite `taurus.test.postgres.url`, `taurus.test.postgres.username` e `taurus.test.postgres.password`.
