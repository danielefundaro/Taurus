# Taurus backend

API Java 17 e Spring Boot di Taurus, generata in origine con JHipster 8.7.3. Persiste i dati su PostgreSQL con uno schema per tenant, autentica gli utenti con Keycloak (realm `taurus`) e usa RabbitMQ per le elaborazioni asincrone, come i PDF delle tracce e il riconoscimento delle parti.

## Struttura

| Percorso | Contenuto |
| --- | --- |
| `src/main/java/com/fundaro/zodiac/taurus` | codice: `web/rest` per le API, `service` per la logica, `domain` e `repository` per la persistenza, `multitenancy` per lo schema del tenant, `rabbitmq` per code e scheduler dei promemoria |
| `src/main/resources/config` | configurazione (`application.yml` e profili `dev`, `prod`, `tls`) |
| `src/main/resources/config/liquibase` | migrazioni: `master.xml` per lo schema `public`, `tenant-master.xml` per ogni schema tenant |
| `src/main/docker` | servizi di sviluppo e realm Keycloak |
| `src/test/java` | test, con la stessa struttura dei package |

Schema per tenant, provisioning e migrazioni sono descritti in [PostgreSQL schema per tenant](../docs/postgres-tenant-schemas.md). Configurazione di produzione, variabili d'ambiente, rilascio e backup sono nel [Runbook operativo](../docs/runbook-operativo.md).

## Sviluppo

Avviare le dipendenze, poi l'applicazione con il profilo `dev`:

```bash
docker compose -f src/main/docker/services.yml up --wait
./mvnw
```

`services.yml` avvia PostgreSQL, Keycloak con il realm `taurus`, RabbitMQ, il servizio OMR e Mailpit, con i valori di sviluppo di `src/main/docker/.env`. Le API rispondono su `http://localhost:8080/api`, Keycloak su `http://localhost:9080`, Mailpit su `http://localhost:8025`. I file gestiti, come pagine degli spartiti, fotografie e allegati, sono salvati sotto `application.base-path`, una cartella per tenant.

Su Windows usare `mvnw.cmd` al posto di `./mvnw`.

## Verifiche

| Comando | Effetto |
| --- | --- |
| `./mvnw test` | test unitari (`*Test`) |
| `./mvnw verify` | test unitari e di integrazione (`*IT`), questi ultimi su PostgreSQL con Testcontainers |

I test di integrazione richiedono Docker, oppure un PostgreSQL temporaneo indicato con `taurus.test.postgres.url`, `taurus.test.postgres.username` e `taurus.test.postgres.password`. La formattazione è applicata da Spotless con Prettier durante la build.

## Immagine

Il `Dockerfile` costruisce l'immagine del backend; il workflow `docker-publish.yml` la pubblica come `taurus-backend` a ogni tag `v*.*.*`, dopo la validazione del catalogo delle funzionalità.
