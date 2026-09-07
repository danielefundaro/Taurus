# Runbook operativo Taurus

## 1. Scopo e stato del documento

Questo runbook descrive come installare, configurare, avviare, verificare, rilasciare, monitorare e ripristinare l'intero applicativo Taurus. È rivolto a sviluppo, operations, supporto applicativo e responsabili di release.

ID catalogo: `operational-runbook`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

Il documento riflette lo stato del repository alla data **7 settembre 2026**. Le procedure marcate **baseline da definire** non sono ancora automatizzate o completamente rappresentate nel repository e devono essere validate nell'ambiente di destinazione prima di essere usate in produzione.

Principi operativi:

- non usare i compose presenti come manifest di produzione;
- non inserire segreti nei file versionati o nella riga di comando condivisa;
- eseguire backup verificati prima di ogni variazione irreversibile;
- distribuire immagini identificate da versione o digest, mai affidandosi al solo tag `latest`;
- trattare le migration Liquibase come append-only dopo il rilascio;
- verificare sempre isolamento tenant, autenticazione e storage dei media dopo un deploy;
- registrare nell'incidente o nella change ogni comando distruttivo e relativo esito.

### 1.1 Dati da completare prima dell'uso in produzione

| Campo | Valore |
| --- | --- |
| Owner applicativo | `TBD` |
| Owner infrastruttura | `TBD` |
| Reperibilità/escalation | `TBD` |
| Canale incidenti | `TBD` |
| URL produzione web app | `TBD` |
| URL produzione sito informativo | `TBD` |
| URL pubblico Keycloak | `TBD` |
| Registro immagini autorizzato | Docker Hub e/o GHCR, scelta `TBD` |
| RPO database | `TBD` |
| RTO applicativo | `TBD` |
| Retention backup | `TBD` |
| Regione e sito DR | `TBD` |

Finché questi campi non sono valorizzati e approvati, il runbook è utilizzabile per sviluppo, collaudo e preparazione operativa, ma non costituisce da solo una baseline di produzione.

## 2. Inventario dell'applicativo

| Componente | Tecnologia | Responsabilità | Porta locale | Stato persistente |
| --- | --- | --- | --- | --- |
| `taurus-fe` | Angular 19, Nginx | SPA utente/amministrazione, PWA e Web Push | `4200` in sviluppo, `80` nel container | Nessuno; configurazione incorporata in build |
| `taurus-be` | Java 17, Spring Boot 3.3, JHipster | API, processi asincroni, scheduler, migrazioni e accesso ai dati | `8080` | Media su filesystem; dati in PostgreSQL |
| PostgreSQL | PostgreSQL 17 nel compose locale | Schema globale `public`, schemi tenant e database Keycloak | `5432` | Critico |
| Keycloak | Keycloak 26.5.2 nel compose locale | OIDC, utenti, ruoli, tenant claim, login e-mail e temi | `9080`, `9443` locali | Database `taurus_keycloak` e provider custom |
| RabbitMQ | RabbitMQ 4.0.7 management nel compose locale | Coda di elaborazione file/PDF `upload.files` | `5672`, console `15672` | Code e messaggi in transito |
| `keycloak-authenticator` | Java 17, provider Keycloak | Selezione tenant/ruolo e protocol mapper | n/a | JAR e temi distribuiti in Keycloak |
| `taurus-info` | Astro, Nginx | Sito informativo statico | `4321` dev, `8088` container locale | Nessuno; variabili incorporate in build |
| Prometheus/Grafana | Compose opzionale | Metriche JVM/HTTP e dashboard | `9090`, `3000` | Configurazione e dashboard locali |

### 2.1 Flusso principale

```text
Browser
  |-- sito informativo --> taurus-info/Nginx
  |-- autenticazione ---> Keycloak ----> PostgreSQL/taurus_keycloak
  `-- SPA Angular -------> Taurus API --> PostgreSQL/public
                                    |--> PostgreSQL/tenant_<hash>
                                    |--> filesystem media per tenant
                                    `--> RabbitMQ/upload.files --> worker nello stesso backend
```

Il claim JWT `tenant` determina lo schema PostgreSQL. Le entità globali restano in `public`; le entità operative risiedono in uno schema fisico `tenant_<hash>`. Una richiesta senza un tenant valido non deve poter leggere uno schema di un altro tenant.

### 2.2 Ordine delle dipendenze

L'ordine di avvio raccomandato è:

1. PostgreSQL;
2. Keycloak e relativo provider;
3. RabbitMQ;
4. backend, incluse migration globali e tenant;
5. frontend;
6. sito informativo;
7. monitoraggio.

L'ordine di arresto è inverso. Durante un arresto pianificato, fermare prima l'ingresso di nuovo traffico e attendere la conclusione delle richieste e dei job in corso.

## 3. Prerequisiti e accessi

### 3.1 Sviluppo locale

- Git;
- Java 17;
- Node.js 22 e npm;
- pnpm 11 per `taurus-info`;
- Docker Engine con Docker Compose;
- Chrome/Chromium per i test Karma;
- facoltativo: Tesseract OCR con dati `ita+eng`.

Verifica rapida:

```powershell
java -version
node --version
npm --version
pnpm --version
docker version
docker compose version
```

### 3.2 Produzione

Prima di operare servono almeno:

- accesso al runtime/container orchestrator;
- accesso in sola lettura a log, metriche e dashboard;
- accesso controllato al registry immagini;
- credenziali separate per runtime DB, migration/provisioning DB e amministrazione Keycloak;
- accesso al secret manager;
- permesso di leggere e ripristinare i backup;
- procedura di change e rollback approvata;
- sincronizzazione oraria affidabile su tutti i nodi.

Gli accessi amministrativi devono essere personali, sottoposti a MFA e registrati. Non usare gli account e le password di sviluppo contenuti nei file locali.

## 4. Configurazione

### 4.1 Regole

- Usare secret manager o secret nativi dell'orchestratore per password DB, client secret OIDC, credenziali amministrative Keycloak e chiave privata VAPID.
- Usare configurazione non segreta per URL, feature kill switch, limiti e pianificazioni.
- Separare rigorosamente sviluppo, collaudo e produzione.
- Conservare una matrice delle configurazioni per ambiente senza valori segreti.
- Ricostruire `taurus-fe` e `taurus-info` quando cambia una variabile incorporata durante la build.
- Montare `application.base-path` su storage persistente e sottoposto a backup.

Spring Boot accetta override tramite variabili d'ambiente con relaxed binding. La baseline minima di produzione deve includere valori equivalenti ai seguenti:

| Impostazione | Variabile consigliata | Note |
| --- | --- | --- |
| Profilo | `SPRING_PROFILES_ACTIVE=prod` | Aggiungere `api-docs` solo se esplicitamente autorizzato |
| JDBC | `SPRING_DATASOURCE_URL` | URL PostgreSQL raggiungibile dal backend |
| Utente DB | `SPRING_DATASOURCE_USERNAME` | Non usare credenziali predefinite |
| Password DB | `SPRING_DATASOURCE_PASSWORD` | Segreto |
| URL Liquibase | `SPRING_LIQUIBASE_URL` | JDBC, non R2DBC |
| Utente Liquibase | `SPRING_LIQUIBASE_USER` | Preferibilmente ruolo dedicato |
| Password Liquibase | `SPRING_LIQUIBASE_PASSWORD` | Segreto |
| Issuer OIDC | `SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI` | Deve coincidere con l'issuer pubblico dei token |
| Client ID OIDC | `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_ID` | Deve coincidere con frontend e realm |
| Client secret OIDC | `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_SECRET` | Segreto |
| Storage | `APPLICATION_BASE_PATH` | Directory/volume persistente, scrivibile solo dal backend |
| Keycloak master | `APPLICATION_KEYCLOAK_MASTER_URI` | URL amministrativo previsto dall'integrazione |
| Keycloak admin issuer | `APPLICATION_KEYCLOAK_ADMIN_ISSUER_URI` | Validare contro l'ambiente reale |
| Keycloak admin user | `APPLICATION_KEYCLOAK_ADMIN_USERNAME` | Preferire service account con privilegi minimi |
| Keycloak admin password | `APPLICATION_KEYCLOAK_ADMIN_PASSWORD` | Segreto |
| Chiave VAPID privata | `VAPID_PRIVATE_KEY` | Segreto; deve corrispondere alla chiave pubblica FE |
| URL pubblico feed | `TAURUS_PUBLIC_BASE_URL` | HTTPS pubblico del backend/feed |
| URL pubblico QR | `INVENTORY_QR_PUBLIC_BASE_URL` | HTTPS pubblico della SPA |
| Kill switch QR | `INVENTORY_QR_ENABLED` | Un flag tenant non supera un kill switch globale spento |
| Kill switch feed | `CALENDAR_FEED_ENABLED` | Come sopra |

Le proprietà RabbitMQ standard Spring, per esempio `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_PORT`, `SPRING_RABBITMQ_USERNAME` e `SPRING_RABBITMQ_PASSWORD`, devono essere impostate nell'ambiente distribuito. La coda applicativa è `upload.files` e l'exchange è `taurus.rabbitmq`.

### 4.2 Frontend Angular

La configurazione è in:

- `taurus-fe/src/environments/environment.ts` per sviluppo;
- `taurus-fe/src/environments/environment.prod.ts` per produzione.

Prima della build di produzione verificare:

- `baseUrl` sia un URL HTTPS raggiungibile **dal browser**, non un hostname solo Docker;
- `keycloak.baseurl` sia l'URL HTTPS pubblico di Keycloak;
- realm e client ID coincidano esattamente con il realm importato;
- la chiave VAPID pubblica corrisponda alla privata del backend;
- redirect URI, web origin e post-logout URI siano autorizzati in Keycloak;
- il service worker possa essere servito via HTTPS.

Stato attuale da correggere prima della produzione: `environment.prod.ts` usa `http://backend:8080/api`, `http://keycloak:8081` e client `web-app`, mentre lo sviluppo e il backend usano il client `web_app`. Questi valori non costituiscono una configurazione di produzione valida per un browser esterno.

### 4.3 Sito informativo

Variabili build-time:

| Variabile | Scopo |
| --- | --- |
| `SITE_URL` | URL canonico, sitemap e metadata |
| `PUBLIC_APP_URL` | Destinazione dei pulsanti di accesso |
| `PUBLIC_CONTACT_EMAIL` | Contatto pubblico |
| `TAURUS_INFO_PORT` | Solo compose locale |

Per cambiarle occorre ricostruire l'immagine. Il TLS termina sul reverse proxy o ingress.

### 4.4 Feature flag

Otto funzioni sono configurabili per tenant: economia, inventario, onboarding/import, feed calendario esterno, QR inventario, preferenze notifiche, Web Push e preparazione evento.

```text
funzione effettiva = capacità installazione AND flag tenant AND dipendenze effettive
```

Disabilitare un flag nasconde e blocca la funzione, ma non cancella i dati. Dopo una modifica verificare `GET /api/tenant-features/current` come utente del tenant e, come super amministratore, le capability esposte da `GET /api/tenant-features/capabilities`.

## 5. Avvio e arresto locale

### 5.1 Primo avvio

Dalla root del repository:

```powershell
docker compose -f taurus-be/src/main/docker/services.yml up --wait
```

In terminali separati:

```powershell
Set-Location taurus-be
.\mvnw.cmd
```

```powershell
Set-Location taurus-fe
npm ci
npm start
```

Facoltativamente:

```powershell
Set-Location taurus-info
pnpm install --frozen-lockfile
pnpm dev
```

Endpoint locali attesi:

| Servizio | URL |
| --- | --- |
| Frontend | `http://localhost:4200` |
| Backend health | `http://localhost:8080/management/health` |
| Keycloak | `http://localhost:9080` |
| RabbitMQ management | `http://localhost:15672` |
| Sito Astro dev | `http://localhost:4321` |

Il backend abilita Spring Docker Compose e può tentare di avviare `services.yml` automaticamente. Per evitare due owner dello stesso stack, scegliere una sola modalità: avvio manuale del compose oppure lifecycle Spring.

### 5.2 Controllo stato

```powershell
docker compose -f taurus-be/src/main/docker/services.yml ps
Invoke-RestMethod http://localhost:8080/management/health
Test-NetConnection localhost -Port 5432
Test-NetConnection localhost -Port 5672
Test-NetConnection localhost -Port 9080
```

Health atteso del backend: HTTP `200` e stato `UP`. I dettagli completi richiedono autorizzazione amministrativa.

### 5.3 Arresto

Arrestare backend e frontend con `Ctrl+C`, quindi:

```powershell
docker compose -f taurus-be/src/main/docker/services.yml down
```

Avvertenza: nel compose corrente il volume dati PostgreSQL è commentato. La ricreazione del container può quindi eliminare dati locali. Non usare `down -v` e non rimuovere il container finché i dati necessari non sono stati esportati.

## 6. Build e verifiche

Eseguire dalla root:

```powershell
node scripts/docs/validate-feature-catalog.mjs
node --test scripts/docs/feature-catalog.test.mjs
node scripts/docs/generate-feature-index.mjs
git diff --exit-code -- docs/features.md
```

Backend:

```powershell
Set-Location taurus-be
.\mvnw.cmd verify
.\mvnw.cmd -Pprod clean verify
```

Frontend:

```powershell
Set-Location taurus-fe
npm ci
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
```

Provider Keycloak:

```powershell
Set-Location keycloak-authenticator
.\mvnw.cmd verify
```

Sito informativo:

```powershell
Set-Location taurus-info
pnpm install --frozen-lockfile
pnpm check
pnpm build
```

Per una modifica PrimeNG consultare solo le sezioni pertinenti di `docs/llms-full.md`. Per una migration tenant eseguire anche:

```powershell
Set-Location taurus-be
.\mvnw.cmd "-Dtest=TenantContextTest,TenantSchemaNameResolverTest,SchemaMultiTenantConnectionProviderTest" test
.\mvnw.cmd "-Dtest=TenantSchemaProvisioningServiceIT" test
```

## 7. Release e artefatti

### 7.1 Versionamento

I tag seguono `vMAJOR.MINOR.PATCH`. Un push di tag conforme avvia `.github/workflows/docker-publish.yml`, valida il catalogo funzionale e pubblica le immagini backend e frontend su Docker Hub e GHCR, firmandole con Cosign.

Il workflow attuale **non** pubblica `taurus-info` né il provider Keycloak. Questi due artefatti richiedono una pipeline o procedura controllata aggiuntiva prima di poter dichiarare completa una release.

### 7.2 Checklist pre-release

- [ ] Feature ID e stato release aggiornati in `docs/features.json`.
- [ ] `docs/features.md` rigenerato e senza diff residuo.
- [ ] Tutti i job del workflow `Verify` verdi.
- [ ] Migration globali incluse in `master.xml`.
- [ ] Migration tenant incluse in `tenant-master.xml`.
- [ ] Compatibilità di rollback valutata.
- [ ] Configurazioni FE, Keycloak e URL pubblici coerenti.
- [ ] Immagine info e JAR provider prodotti con versione tracciabile.
- [ ] Backup completato e restore recentemente provato.
- [ ] Spazio DB e storage sufficiente per migrazione e rollback.
- [ ] Change window e responsabile del go/no-go identificati.
- [ ] Smoke test e osservazione post-deploy assegnati.

### 7.3 Produzione degli artefatti mancanti

```powershell
docker build -t <registry>/taurus-info:<version> taurus-info
Set-Location keycloak-authenticator
.\mvnw.cmd clean verify
```

Registrare digest dell'immagine e checksum del JAR. Non sostituire il provider su un nodo Keycloak attivo senza una procedura rolling compatibile con la topologia scelta.

## 8. Deploy di produzione

### 8.1 Gate obbligatori

Non procedere se uno dei seguenti punti è vero:

- backup non disponibile o restore mai provato;
- configurazione Angular contiene hostname interni/non pubblici;
- manifest usa `start-dev` per Keycloak;
- PostgreSQL o storage media non sono persistenti;
- credenziali di esempio sono ancora attive;
- non è definito il routing HTTPS tra browser, frontend, API e Keycloak;
- non sono definiti readiness/liveness e limiti di risorse;
- le immagini non sono fissate per digest/versione;
- manca una strategia per eseguire una sola volta le migration in presenza di più repliche.

Il file `taurus-be/src/main/docker/app.yml` non è una baseline di produzione: usa riferimenti JHipster obsoleti, configura R2DBC mentre l'applicazione corrente usa JDBC/JPA, non include RabbitMQ e non monta lo storage media. Va sostituito da un manifest validato.

### 8.2 Sequenza standard

1. Aprire la change e registrare versione corrente, versione target e digest.
2. Verificare health, tasso errori, code RabbitMQ, spazio disco e stato backup.
3. Sospendere modifiche amministrative ai tenant durante la finestra.
4. Eseguire backup coerente di PostgreSQL e storage media.
5. Distribuire l'eventuale provider Keycloak compatibile; riavviare e verificare Keycloak.
6. Avviare un'unica istanza backend target per applicare Liquibase.
7. Verificare `public.databasechangelog` e il changelog di ogni schema attivo.
8. Se le migration sono riuscite, distribuire tutte le repliche backend.
9. Distribuire frontend e invalidare solo la cache HTML/service worker necessaria.
10. Distribuire il sito informativo se incluso nella release.
11. Eseguire gli smoke test.
12. Osservare metriche e log per almeno una finestra operativa concordata.
13. Chiudere la change riportando esito, anomalie e versione effettiva.

### 8.3 Verifica migration tenant

Elencare il registro globale:

```sql
SELECT tenant_code, schema_name, status, last_error, updated_at
FROM public.tenant_schema_registry
ORDER BY tenant_code;
```

Condizione di successo:

- nessun tenant attivo in stato di errore;
- nessun lock Liquibase persistente;
- lo stesso set di changeset atteso è applicato agli schemi attivi;
- il backend raggiunge readiness.

Non modificare manualmente `databasechangelog` o `databasechangeloglock` senza diagnosi e approvazione. Un lock può indicare una migration ancora in corso o interrotta.

### 8.4 Smoke test post-deploy

- [ ] `GET /management/health` restituisce `200/UP`.
- [ ] `GET /management/info` risponde e identifica la build prevista.
- [ ] Login e logout funzionano con un utente non amministratore.
- [ ] Il token contiene ruoli e tenant attesi.
- [ ] Un utente vede solo i dati del proprio tenant.
- [ ] Un secondo tenant non può accedere allo stesso record/ID.
- [ ] Lettura e scrittura di una funzione core riuscite.
- [ ] Upload e download di un piccolo file riusciti.
- [ ] La coda `upload.files` viene consumata senza crescita anomala.
- [ ] Le feature abilitate/disabilitate coincidono con la configurazione tenant.
- [ ] Il feed calendario e il QR usano URL pubblici HTTPS corretti, se abilitati.
- [ ] Registrazione Web Push e invio di prova riusciti, se abilitati.
- [ ] Il frontend non presenta errori di CORS, mixed content o service worker.
- [ ] `/health` del sito informativo restituisce `200`, se rilasciato.

Usare dati di collaudo dedicati ed eliminarli secondo le regole applicative, evitando cancellazioni SQL dirette.

## 9. Rollback

### 9.1 Solo applicazione, senza migration incompatibili

1. Fermare il rollout.
2. Riportare frontend e backend ai digest precedenti.
3. Ripristinare l'eventuale provider Keycloak precedente.
4. Verificare health, login, tenant isolation, coda e storage.
5. Lasciare aperto l'incidente e conservare log e artefatti.

### 9.2 Con migration database

Le migration Liquibase non vanno annullate automaticamente. Preferire, in ordine:

1. forward fix compatibile;
2. rollback dell'app solo se il vecchio codice è compatibile con lo schema nuovo;
3. restore di database **e storage media** allo stesso punto temporale, se la modifica è incompatibile o distruttiva.

Un restore del solo database può lasciare record che puntano a file assenti o file orfani. Durante un restore completo bloccare le scritture, ripristinare entrambi gli insiemi e poi riavviare i consumer.

### 9.3 Criteri di rollback immediato

- isolamento tenant violato o non dimostrabile;
- autenticazione indisponibile senza workaround controllato;
- errori di migration su uno o più tenant;
- corruzione/perdita dati o storage non montato;
- aumento sostenuto di errori 5xx oltre la soglia approvata;
- backlog RabbitMQ in crescita senza consumo;
- incompatibilità frontend/backend bloccante.

## 10. Monitoraggio e osservabilità

### 10.1 Endpoint backend

| Endpoint | Uso | Accesso |
| --- | --- | --- |
| `/management/health` | Stato generale | Pubblico, dettagli limitati |
| `/management/health/liveness` | Processo vivo | Pubblico |
| `/management/health/readiness` | Pronto al traffico | Pubblico |
| `/management/info` | Informazioni build | Pubblico |
| `/management/prometheus` | Metriche Prometheus | Authority prevista dalla security; non esporre su Internet |
| `/management/loggers` | Livelli log runtime | Amministrativo |
| `/management/liquibase` | Changeset | Amministrativo |
| `/management/threaddump` | Diagnosi thread | Amministrativo e sensibile |

Il repository espone anche `env`, `configprops` e `logfile` nell'Actuator. In produzione limitarli alla rete amministrativa e verificare la sanitizzazione, perché possono rivelare configurazione sensibile.

### 10.2 Segnali minimi e allarmi

Definire soglie specifiche per ambiente e almeno i seguenti alert:

- readiness non `UP`;
- tasso 5xx e latenza p95/p99;
- esaurimento pool JDBC;
- connessioni o storage PostgreSQL prossimi al limite;
- spazio/inode del filesystem media;
- backlog e messaggi non confermati sulla coda `upload.files`;
- consumer RabbitMQ assenti;
- Keycloak non raggiungibile o incremento errori OIDC;
- tenant in stato diverso da `ACTIVE` con `last_error` valorizzato;
- errori ripetuti degli scheduler;
- crescita di notification outbox/push fallite;
- certificati TLS e chiavi in scadenza;
- backup fallito o restore test scaduto.

### 10.3 Log

Il profilo `prod` usa livello `INFO`; i log non sono JSON per impostazione predefinita e Logstash è disabilitato. La piattaforma di produzione deve acquisire `stdout/stderr`, aggiungere timestamp e identità del pod/container e applicare retention controllata.

Non registrare token, password, contenuto dei file, chiavi VAPID o dati personali completi. Un aumento temporaneo del livello log va annotato e ripristinato al termine della diagnosi.

## 11. Job pianificati

Gli orari cron senza `zone` esplicita usano il timezone del processo JVM. Impostare e documentare il timezone del container oppure configurare una zona esplicita.

| Job | Frequenza predefinita | Scopo |
| --- | --- | --- |
| Promemoria eventi Web Push | ogni minuto | Invia reminder dovuti per tenant |
| Dispatch notification outbox | ogni 5 s | Materializza notifiche pendenti |
| Dispatch push | ogni 5 s | Invia consegne push con retry |
| Recovery onboarding | ogni 2 s | Riprende import caricati/interrotti |
| Retention dati | 03:00 giornaliero | Elimina dati oltre retention per tenant |
| Cleanup media | 03:30 giornaliero | Elimina temporanei e orfani per tenant |
| Cleanup outbox | 03:30 giornaliero | Elimina eventi consegnati scaduti |
| Cleanup tombstone feed | 03:15 giornaliero | Elimina tombstone calendario scadute |
| Avvisi scadenza inventario | 08:00 Europe/Rome | Genera notifiche scadenze |
| Rollover economia | 00:10 Europe/Rome | Aggiorna saldi/aperture annuali |
| Scadenze preparazione evento | ogni minuto Europe/Rome | Genera notifiche operative |

In una distribuzione con più repliche, verificare il comportamento concorrente di ogni scheduler. Il repository non mostra un coordinatore distribuito generale: prima di scalare il backend oltre una replica, validare idempotenza e locking o assegnare i job a una sola replica.

## 12. Backup e restore

### 12.1 Perimetro obbligatorio

Il backup completo comprende:

- database PostgreSQL `taurus`, inclusi `public` e tutti gli schemi tenant;
- database Keycloak `taurus_keycloak`;
- filesystem configurato da `APPLICATION_BASE_PATH`;
- realm/configurazione Keycloak esportabile, provider JAR e temi;
- manifest e configurazioni non segrete della release;
- riferimenti ai segreti e alle relative versioni, non i segreti in chiaro nel repository;
- digest delle immagini distribuite.

RabbitMQ non deve essere usato come archivio. Per ottenere un punto coerente, fermare temporaneamente producer/consumer oppure usare una strategia applicativa validata che gestisca i messaggi in volo.

### 12.2 Esempio backup PostgreSQL

Eseguire da un host protetto, usando `.pgpass` o secret injection e una directory specifica già verificata:

```bash
pg_dump --format=custom --no-owner --file=taurus_<UTC_TIMESTAMP>.dump "$TAURUS_DATABASE_URL"
pg_dump --format=custom --no-owner --file=taurus_keycloak_<UTC_TIMESTAMP>.dump "$KEYCLOAK_DATABASE_URL"
```

Calcolare checksum, cifrare lato storage, copiare fuori dal dominio di guasto e registrare dimensione, durata ed esito. Non dichiarare riuscito un backup solo perché il comando ha creato un file.

### 12.3 Backup storage media

Usare snapshot consistente o copia incrementale del volume `APPLICATION_BASE_PATH`. Coordinare il punto temporale con il database. Escludere solo temporanei documentati; non dedurre gli orfani durante il backup.

### 12.4 Restore di prova

Almeno con la frequenza prevista dalla policy:

1. creare un ambiente isolato;
2. ripristinare i due database su istanze vuote;
3. ripristinare lo storage media allo stesso punto;
4. distribuire la versione applicativa associata al backup;
5. avviare Keycloak e poi il backend;
6. verificare schema registry, Liquibase e health;
7. eseguire login, lettura tenant, download media e consumo di un job controllato;
8. misurare RPO/RTO effettivi;
9. eliminare in sicurezza l'ambiente di prova.

Esempio su database vuoto:

```bash
pg_restore --exit-on-error --no-owner --dbname="$RESTORE_TAURUS_DATABASE_URL" taurus_<UTC_TIMESTAMP>.dump
pg_restore --exit-on-error --no-owner --dbname="$RESTORE_KEYCLOAK_DATABASE_URL" taurus_keycloak_<UTC_TIMESTAMP>.dump
```

Non usare questi comandi contro un database esistente senza un piano esplicito di pulizia e approvazione.

## 13. Operazioni tenant

### 13.1 Provisioning

La creazione tramite `POST /api/tenants`:

1. registra il tenant in `public`;
2. genera uno schema fisico sicuro;
3. applica `tenant-master.xml`;
4. copia eventuali dati legacy in modo idempotente;
5. porta il registro a `ACTIVE`.

Verificare registro, log e presenza delle tabelle Liquibase nello schema. Il ruolo DB deve avere `CREATE` sul database e `USAGE, CREATE` su `public`; in produzione separare per quanto possibile privilegi di migration e runtime.

### 13.2 Provisioning fallito

1. Non creare manualmente un secondo schema.
2. Leggere `status` e `last_error` nel registro.
3. Correlare l'errore con i log Liquibase/backend.
4. Correggere la causa: permessi, lock, spazio o changeset.
5. Riavviare/ripetere solo il flusso idempotente previsto dall'applicazione.
6. Verificare che il tenant diventi `ACTIVE` e sia isolato.

### 13.3 Cancellazione GDPR

La cancellazione tenant pianifica `DROP SCHEMA ... CASCADE` dopo il commit e porta il registro a `DELETED`. È distruttiva.

Prima dell'azione:

- validare identità e autorizzazione del richiedente;
- identificare esattamente tenant e impatto;
- applicare retention/hold legale;
- creare l'eventuale backup autorizzato;
- verificare che non si stia operando sul tenant errato.

Dopo l'azione verificare stato `DELETED`, assenza dello schema e rimozione/anonimizzazione dei file previsti. Non ricreare manualmente lo stesso tenant per simulare un restore.

## 14. Procedure di incidente

### 14.1 Metodo comune

1. Dichiarare severità e incident commander.
2. Annotare in UTC inizio, versione, ambiente e sintomo.
3. Contenere l'impatto senza cancellare prove.
4. Verificare cambi recenti prima di riavviare indiscriminatamente.
5. Conservare log, metriche, eventi orchestrator e query diagnostiche.
6. Applicare workaround/rollback approvato.
7. Confermare recupero con smoke test e metriche.
8. Comunicare stato e impatto con cadenza concordata.
9. Chiudere con timeline, causa radice e azioni correttive.

### 14.2 Backend non disponibile

Diagnosi:

- interrogare liveness e readiness separatamente;
- controllare eventi di restart/OOM e log di startup;
- verificare PostgreSQL, Keycloak, RabbitMQ e mount dello storage;
- cercare errori Liquibase o pool JDBC.

Azione:

- se liveness fallisce, riavviare una sola istanza dopo aver raccolto le prove;
- se readiness fallisce per dipendenza, ripristinare prima la dipendenza;
- se il problema coincide con un deploy, applicare i criteri di rollback;
- non scalare repliche se il DB è saturo o se gli scheduler non sono coordinati.

### 14.3 PostgreSQL indisponibile o saturo

- verificare reachability, connessioni, spazio, I/O, replica e lock;
- bloccare deploy e operazioni tenant;
- ridurre traffico solo tramite meccanismi previsti;
- non terminare query o sessioni senza identificare owner e transazione;
- dopo il recupero verificare Liquibase, registro tenant e integrità funzionale.

In caso di perdita dati attivare la procedura DR e ripristinare database e media allo stesso punto.

### 14.4 Lock o errore Liquibase

- assicurarsi che nessuna migration sia ancora attiva;
- identificare istanza, changeset e schema interessato;
- acquisire dump e log;
- non modificare checksum o lock a mano durante un'esecuzione attiva;
- correggere il changeset con una nuova migration se già rilasciato;
- rimuovere un lock orfano solo con approvazione DB e prova che il processo originario è terminato.

### 14.5 Login/Keycloak non funzionante

- verificare health e certificato di Keycloak;
- confrontare issuer del token, URL pubblico, realm e client ID;
- verificare redirect URI e web origins;
- controllare connessione al database `taurus_keycloak`;
- verificare caricamento e compatibilità del provider custom;
- escludere clock skew tra browser, backend e Keycloak.

Non bypassare OIDC e non distribuire client secret nel frontend.

### 14.6 RabbitMQ o upload bloccati

- verificare connessione del backend e presenza di consumer su `upload.files`;
- osservare ready/unacked, publish rate e redelivery;
- verificare spazio/memoria e allarmi RabbitMQ;
- controllare nei log `queueId` e tenant senza esporre contenuto sensibile;
- non eliminare o requeue massivamente i messaggi senza valutarne l'idempotenza;
- sospendere nuovi upload se il backlog continua a crescere.

### 14.7 Storage media pieno o non disponibile

- mettere in pausa upload e operazioni che scrivono file;
- verificare mount, permessi, spazio e inode;
- non lanciare manualmente una cancellazione ricorsiva;
- controllare esito del cleanup pianificato e riferimenti DB;
- espandere/ripristinare il volume;
- validare upload, download e isolamento del path tenant.

### 14.8 Notifiche o Web Push degradati

- verificare feature flag globali e tenant;
- controllare corrispondenza della coppia VAPID;
- osservare outbox, consegne `FAILED/SKIPPED`, retry e subscription invalide;
- verificare HTTPS e service worker del frontend;
- usare la console amministrativa di delivery per retry/chiusura controllati;
- evitare retry massivi finché la causa esterna persiste.

### 14.9 Sospetta violazione tenant o sicurezza

Classificare come incidente critico:

1. contenere l'accesso e fermare il rollout;
2. preservare audit log, token metadata e query coinvolte;
3. non cancellare tenant, schemi o utenti come prima risposta;
4. coinvolgere owner sicurezza e privacy;
5. verificare claim `tenant`, `TenantContext` e schema effettivo;
6. ruotare credenziali/chiavi solo con piano che preservi le prove;
7. seguire gli obblighi di notifica applicabili.

## 15. Sicurezza e rotazioni

### 15.1 Controlli minimi

- TLS pubblico obbligatorio per SPA, API, Keycloak e feed;
- rete privata per PostgreSQL, RabbitMQ e Actuator amministrativo;
- nessun `start-dev` Keycloak in produzione;
- password e account predefiniti rimossi;
- backup cifrati e accesso tracciato;
- immagini scansionate e firme Cosign verificate;
- privilegi DB e Keycloak minimi;
- CSP e header del sito informativo verificati a ogni modifica;
- limite upload da 500 MB rivalutato a livello reverse proxy e capacità;
- dati OCR/PDF trattati come contenuto non fidato.

### 15.2 Rotazione client secret OIDC

1. Creare una seconda credenziale se supportato.
2. Aggiornare il secret manager.
3. Distribuire il backend e verificare login/refresh/logout.
4. Revocare la credenziale precedente.
5. Registrare versione e data di rotazione.

### 15.3 Rotazione VAPID

La chiave pubblica è incorporata nel frontend. La rotazione richiede coordinamento tra backend e nuova build FE e può rendere inutilizzabili le subscription esistenti.

1. Valutare migrazione/ri-registrazione delle subscription.
2. Generare la nuova coppia in ambiente sicuro.
3. Aggiornare privata backend e pubblica frontend nella stessa release coordinata.
4. Distribuire, verificare una nuova subscription e un invio.
5. Eliminare la vecchia privata secondo policy.

### 15.4 Rotazione certificati TLS

Monitorare scadenza con anticipo, aggiornare reverse proxy/ingress, verificare catena completa e hostname, quindi provare login OIDC, API, service worker e feed calendario da una rete esterna.

## 16. Manutenzione ricorrente

### Giornaliera

- health e alert aperti;
- esito backup;
- spazio DB/storage;
- backlog RabbitMQ;
- errori scheduler, notification delivery e provisioning tenant.

### Settimanale

- trend di latenza/errori e capacità;
- tenant non `ACTIVE`;
- dipendenze/certificati prossimi a scadenza;
- crescita media e outbox;
- verifica che i cleanup abbiano completato.

### Mensile

- restore di prova secondo policy;
- revisione accessi privilegiati;
- patching immagini base e dipendenze;
- revisione RPO/RTO misurati;
- audit configurazione e drift dei manifest;
- prova della procedura di rollback in collaudo.

## 17. Disaster recovery

### 17.1 Dichiarazione DR

Attivare il DR quando l'ambiente primario non può rispettare l'RTO o quando il ripristino in-place aumenta il rischio di perdita/corruzione dati. L'incident commander autorizza il failover e congela le scritture sul primario se ancora raggiungibile.

### 17.2 Sequenza di recupero

1. Identificare l'ultimo punto coerente disponibile.
2. Preparare rete, DNS, TLS, secret references e storage nel sito DR.
3. Ripristinare PostgreSQL Taurus e Keycloak.
4. Ripristinare media e provider/temi Keycloak.
5. Avviare Keycloak.
6. Avviare una sola istanza backend e verificare migration/schema.
7. Avviare RabbitMQ vuoto o ripristinato secondo la policy approvata.
8. Avviare backend completo, frontend e sito.
9. Eseguire smoke test multi-tenant e media.
10. Aprire il traffico gradualmente e monitorare.
11. Comunicare perdita dati massima effettiva e tempo di ripristino.

Prima del failback impedire split brain e decidere quale sito è la fonte autorevole.

## 18. Gap noti e piano di completamento produzione

I seguenti punti emergono direttamente dal repository e devono diventare attività tracciate:

| Priorità | Gap | Azione richiesta |
| --- | --- | --- |
| Bloccante | Nessun manifest di produzione validato | Creare deployment/orchestrazione con reti, secret, probe, risorse, volumi e rolling strategy |
| Bloccante | PostgreSQL compose senza volume dati attivo | Definire storage persistente e backup; mantenere il compose attuale solo per dev effimero |
| Bloccante | `environment.prod.ts` non utilizzabile da browser esterno | Introdurre valori pubblici corretti o configurazione runtime |
| Bloccante | Client OIDC `web-app`/`web_app` incoerente | Uniformare realm, FE e BE |
| Bloccante | Keycloak avviato con `start-dev` | Creare configurazione production mode con hostname, proxy, TLS e tuning DB |
| Alta | `app.yml` obsoleto/incompleto | Non usarlo; sostituirlo e aggiungere RabbitMQ/storage/JDBC corretti |
| Alta | Pipeline non pubblica info/provider | Estendere CI/CD, firma e provenienza artefatti |
| Alta | Scheduler multi-replica non governati globalmente | Validare locking/idempotenza o separare worker scheduler |
| Alta | Nessun RPO/RTO/retention approvato | Definire e provare backup/DR |
| Media | Logging prod non strutturato | Definire formato, correlazione, raccolta e retention |
| Media | Actuator espone endpoint sensibili | Restringere rete e autorizzazioni, verificare sanitizzazione |
| Media | Compose RabbitMQ espone porte su tutte le interfacce | Limitare al loopback in dev e usare rete privata/TLS/credenziali in prod |
| Media | Nessuna procedura automatica di smoke test | Creare test sintetici non distruttivi per release |

## 19. Scheda rapida di change

```text
Change ID:
Ambiente:
Versione attuale / digest:
Versione target / digest:
Feature ID interessati:
Migration globali:
Migration tenant:
Modifiche Keycloak:
Modifiche configurazione/segreti:
Backup ID e timestamp UTC:
Restore test più recente:
Owner go/no-go:
Ora inizio UTC:
Esito pre-check:
Esito migration:
Esito smoke test:
Decisione: GO | ROLLBACK | HOLD
Ora fine UTC:
Note/link incidente:
```

## 20. Riferimenti nel repository

- `README.md`: panoramica e comandi comuni;
- `docs/features.md`: catalogo funzionalità generato;
- `docs/postgres-tenant-schemas.md`: isolamento e provisioning tenant;
- `docs/migrazione-opensearch-postgresql.md`: modello dati relazionale e strategia migration;
- `docs/tenant-feature-flags-spec.md`: feature flag e capability;
- `docs/media-asset-spec.md`: storage e lifecycle media;
- `docs/notification-delivery-generalization-spec.md`: outbox e consegna;
- `docs/web-push-reminders-spec.md`: Web Push e VAPID;
- `docs/tenant-onboarding-import-spec.md`: import e recovery;
- `taurus-be/src/main/resources/config/application*.yml`: configurazione backend;
- `taurus-be/src/main/docker/*.yml`: stack esclusivamente locale;
- `.github/workflows/verify.yml`: verifiche CI;
- `.github/workflows/docker-publish.yml`: pubblicazione immagini BE/FE.

## 21. Criterio di “servizio ripristinato”

Un componente che risponde al solo health check non basta. Taurus è ripristinato quando:

- health e readiness sono stabili;
- login, refresh e logout OIDC funzionano;
- almeno due tenant di verifica risultano isolati;
- DB globale e schemi tenant hanno migration coerenti;
- lettura/scrittura core e media funzionano;
- RabbitMQ consuma i job senza backlog crescente;
- scheduler e notification delivery non producono errori ripetuti;
- frontend, QR, feed e Web Push usano URL e certificati corretti;
- metriche restano entro le soglie concordate per la finestra di osservazione;
- l'incident commander o il change owner registra formalmente il recupero.
