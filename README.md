# Taurus

Taurus è una piattaforma multi-tenant per la gestione operativa di organizzazioni musicali. Il repository riunisce API, client web, servizio di riconoscimento musicale, integrazione Keycloak e sito informativo; ogni modulo mantiene build e dipendenze indipendenti.

## Moduli

| Modulo | Scopo | Documentazione |
| --- | --- | --- |
| `taurus-be` | API Java/Spring Boot, persistenza PostgreSQL, migrazioni e processi asincroni | [README backend](taurus-be/README.md) |
| `taurus-fe` | Applicazione Angular per utenti e amministratori | [README frontend](taurus-fe/README.md) |
| `taurus-omr` | Servizio Python/FastAPI interno per il riconoscimento ottico della musica (Audiveris, homr) e la conversione MIDI, chiamato solo dal backend | [README OMR](taurus-omr/README.md) |
| `keycloak-authenticator` | Provider Keycloak e temi di autenticazione Taurus | [README autenticazione](keycloak-authenticator/README.md) |
| `taurus-info` | Sito informativo Astro distribuito con Nginx | [README sito](taurus-info/README.md) |

## Prerequisiti

- Java 17;
- Node.js 22 e npm;
- pnpm 11 per `taurus-info`;
- Python 3.12 per i test di `taurus-omr`;
- Docker con Docker Compose per PostgreSQL, Keycloak, RabbitMQ, il servizio OMR e Mailpit.

Non inserire credenziali o configurazioni di produzione nei file versionati. Per il sito informativo partire da `taurus-info/.env.example`.

## Avvio rapido

Avviare prima i servizi condivisi (PostgreSQL, Keycloak, RabbitMQ, servizio OMR e Mailpit, con i valori di sviluppo di `taurus-be/src/main/docker/.env`):

```bash
docker compose -f taurus-be/src/main/docker/services.yml up --wait
```

In due terminali separati:

```bash
cd taurus-be
./mvnw
```

```bash
cd taurus-fe
npm ci
npm start
```

Il backend usa il profilo di sviluppo configurato nel modulo; il frontend Angular è normalmente disponibile su `http://localhost:4200`. `npm start` e `npm run build` scaricano prima il SoundFont dell'ascolto delle parti (circa 38 MB); `TAURUS_SKIP_SOUNDFONT=1` salta il download.

Su un ambiente vuoto o appena resettato non esiste ancora nessun tenant: il primo accesso richiede di preparare il super amministratore in Keycloak e di creare il primo tenant, come descritto nel [runbook operativo, §5.2](docs/runbook-operativo.md#52-primo-accesso-su-ambiente-vuoto).

## Verifiche

| Ambito | Comandi |
| --- | --- |
| Governance documentale | `node scripts/docs/validate-feature-catalog.mjs`; `node --test scripts/docs/feature-catalog.test.mjs`; `node scripts/docs/generate-feature-index.mjs`; `node scripts/docs/check-error-catalog.mjs`; `node --test scripts/docs/error-catalog.test.mjs` |
| Backend | `cd taurus-be && ./mvnw verify` |
| Frontend | `cd taurus-fe && npm ci && npm test -- --watch=false --browsers=ChromeHeadless && npm run build` |
| Servizio OMR | `cd taurus-omr && pip install -r requirements-test.txt && python -m pytest`; `docker build -t taurus-omr .` per l'immagine completa |
| Provider Keycloak | `cd keycloak-authenticator && ./mvnw verify` |
| Sito informativo | `cd taurus-info && pnpm install --frozen-lockfile && pnpm check && pnpm build` |

Su Windows usare `mvnw.cmd` al posto di `./mvnw`.

## Documentazione

- [Runbook operativo](docs/runbook-operativo.md): avvio, configurazione, release, deploy, monitoraggio, backup/restore, incident response e disaster recovery;
- [Runbook di test](docs/runbook-test.md): strategia di qualità, matrici funzionali e di rischio, casi multi-tenant, sicurezza, performance, resilienza e criteri go/no-go;
- [Catalogo funzionalità](docs/features.md): stato corrente, moduli ed evidenze di ogni iniziativa;
- [Governance documentazione/implementazione](docs/documentation-implementation-alignment-spec.md): formato del catalogo e flusso di aggiornamento;
- [Schema PostgreSQL multi-tenant](docs/postgres-tenant-schemas.md) e [migrazione da OpenSearch](docs/migrazione-opensearch-postgresql.md): architettura dei dati;
- le altre specifiche funzionali sono raggiungibili dal catalogo.

`docs/features.json` è la fonte autorevole degli stati; `docs/features.md` è generato e non va modificato manualmente. Quando una modifica cambia un contratto o un'evidenza registrata, aggiornare specifica, catalogo e matrice nella stessa pull request.

## Pull request e migrazioni

Le pull request devono indicare i Feature ID interessati, impatti di configurazione o compatibilità e comandi di verifica eseguiti. Le migration Liquibase sono append-only dopo il rilascio, devono essere incluse nel master changelog corretto e accompagnate da un test di integrazione o da una motivazione nel catalogo.

Modifiche a autenticazione, isolamento tenant, keystore e dati economici richiedono una revisione di sicurezza dedicata. Non includere token, segreti, hostname privati o `.env` di produzione.

## Release

I tag seguono `vMAJOR.MINOR.PATCH`. Il workflow Docker valida il catalogo prima di costruire e pubblicare le immagini backend, frontend e OMR. Una voce passa a `released` nella preparazione della release, indicando come prima release lo stesso tag e la relativa data; correzioni successive restano nelle note di release e non cambiano quel riferimento.
