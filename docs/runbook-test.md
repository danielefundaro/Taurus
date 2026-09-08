# Runbook di test e qualificazione Taurus

## 1. Scopo e stato

ID catalogo: `test-runbook`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

Questo runbook definisce la strategia, le suite, i dati, le procedure e le evidenze necessarie per verificare la qualità dell'intero progetto Taurus. Copre backend, frontend, PostgreSQL multi-tenant, Keycloak, RabbitMQ, storage media, sito informativo e processi operativi.

Il documento è una checklist eseguibile e una matrice di copertura. Non sostituisce i test automatici: un caso marcato come manuale o non ancora automatizzato resta un rischio esplicito. Nessuna quantità di test garantisce l'assenza assoluta di difetti; il criterio adottato è produrre evidenze ripetibili, proporzionate al rischio, sufficienti per una decisione di rilascio consapevole.

Stato rilevato all'8 settembre 2026:

- backend: 109 file Java di test e supporto sotto `src/test`, con unit test, test Spring e integrazioni PostgreSQL/Testcontainers;
- frontend: 18 specifiche Jasmine/Karma;
- provider Keycloak: 1 classe con 2 test di packaging/metadata;
- sito informativo: type-check e build, senza suite funzionale dedicata;
- nessun framework E2E browser configurato;
- JaCoCo produce report backend, ma non applica una soglia minima;
- la CI esegue test/build dei moduli modificati, ma non test full-stack, performance, sicurezza dinamica o restore.

Il runbook resta `in-progress` finché i gap indicati nella sezione 19 non sono risolti o formalmente accettati.

## 2. Obiettivi di qualità

Ogni release deve dimostrare:

1. correttezza delle funzioni previste dal catalogo;
2. isolamento tra tenant in API, database, file e processi asincroni;
3. autorizzazioni coerenti tra backend e interfaccia;
4. compatibilità tra frontend, backend, realm e provider Keycloak;
5. migrazioni ripetibili su database vuoto e aggiornato;
6. integrità e recuperabilità di database e media;
7. comportamento controllato in caso di dipendenze indisponibili;
8. prestazioni compatibili con SLO e volumi attesi;
9. accessibilità, usabilità e compatibilità browser concordate;
10. assenza di vulnerabilità critiche note non accettate;
11. osservabilità sufficiente a diagnosticare un guasto;
12. possibilità di rollback o forward fix documentata.

## 3. Classificazione dei test

| Livello | Scopo | Esecuzione minima |
| --- | --- | --- |
| L0 — statico | Formattazione, compilazione, catalogo, dipendenze e configurazione | Ogni PR |
| L1 — unità | Regole di dominio, mapping, validazione e utility | Ogni PR |
| L2 — componente | Controller, service, componenti Angular e contratti HTTP isolati | Ogni PR |
| L3 — integrazione | PostgreSQL, Liquibase, Keycloak, RabbitMQ e filesystem reali | Ogni PR per aree coinvolte; suite completa nightly |
| L4 — E2E | Flussi utente reali da browser attraverso tutto lo stack | Release candidate |
| L5 — non funzionale | Sicurezza, accessibilità, performance, resilienza, backup/restore | Nightly o release secondo costo/rischio |
| L6 — produzione | Smoke test non distruttivi e osservazione post-deploy | Ogni deploy |

### 3.1 Priorità dei casi

- `P0`: sicurezza, tenant isolation, autenticazione, perdita dati, migrazioni, avvio e funzioni core. Sempre bloccante.
- `P1`: funzione primaria o ruolo importante degradato. Bloccante salvo accettazione formale.
- `P2`: caso secondario, UX o compatibilità non critica. Pianificabile con workaround documentato.

### 3.2 Esiti ammessi

- `PASS`: risultato atteso dimostrato e prova allegata;
- `FAIL`: risultato diverso dall'atteso;
- `BLOCKED`: prerequisito indisponibile, con ticket e owner;
- `NOT RUN`: non eseguito, mai equivalente a `PASS`;
- `N/A`: non applicabile con motivazione approvata.

## 4. Quality gate proposto

I valori seguenti costituiscono la baseline raccomandata; devono essere approvati dagli owner prima di diventare gate automatici.

| Area | Gate di release |
| --- | --- |
| Build | Tutti i moduli interessati compilano in modalità produzione |
| Automatici | 100% test obbligatori verdi; nessun test P0 saltato |
| Difetti | 0 difetti aperti P0; 0 P1 senza accettazione esplicita |
| Tenant isolation | Tutta la matrice negativa P0 superata con almeno due tenant |
| Migrazioni | Fresh install, upgrade e riavvio idempotente superati |
| Sicurezza | 0 vulnerabilità Critical/High sfruttabili non accettate |
| Copertura | Soglie da approvare; proposta iniziale: 80% linee e 70% branch nei moduli modificati, 90% per logica P0 nuova |
| Accessibilità | Nessuna violazione critica automatica; flussi principali utilizzabili da tastiera |
| Performance | SLO p95/p99, throughput ed error rate approvati e rispettati |
| Resilienza | Recupero dimostrato per riavvio backend e indisponibilità temporanea delle dipendenze |
| Restore | Ultima prova entro la finestra di policy e RTO/RPO misurati |
| Evidenze | Report, log, versione e configurazione non segreta archiviati |

Un gate non ancora misurabile è un punto aperto, non un successo implicito.

## 5. Ambienti di test

### 5.1 Locale

Usare per sviluppo rapido e diagnosi. Il compose in `taurus-be/src/main/docker` è esclusivamente di sviluppo e il database non ha un volume persistente attivo.

### 5.2 CI effimera

Caratteristiche richieste:

- checkout pulito;
- versioni Java/Node/pnpm fissate;
- Docker disponibile per Testcontainers;
- database e container nuovi per ogni job;
- nessun segreto di produzione;
- report e log pubblicati anche in caso di fallimento.

### 5.3 Integrazione full-stack

Deve contenere PostgreSQL 17, Keycloak 26.5.2 con provider e temi, RabbitMQ 4, backend, frontend, storage persistente temporaneo e browser controllato. Gli URL devono replicare lo schema pubblico HTTPS previsto, non gli hostname interni della configurazione attuale.

### 5.4 Staging production-like

Deve replicare:

- manifest, reverse proxy, TLS e policy di rete;
- immagini per digest della release candidate;
- stessa strategia di replica e scheduler;
- storage e backup equivalenti;
- dati sintetici, anonimizzati o specificamente autorizzati;
- osservabilità e alert reali.

Non usare dati personali di produzione per comodità.

## 6. Dati e identità di prova

### 6.1 Tenant minimi

| Tenant | Configurazione | Scopo |
| --- | --- | --- |
| `TENANT_A` | Tutte le feature abilitate | Happy path completo |
| `TENANT_B` | Tutte le feature abilitate | Verifiche di isolamento incrociato |
| `TENANT_MIN` | Tutte le feature opzionali disabilitate | Kill switch, menu e API negate |
| `TENANT_MIX` | Combinazioni e dipendenze parziali | Feature flag e capability |

Per ogni tenant creare dati con ID potenzialmente coincidenti, nomi riconoscibili e file differenti. I test non devono affidarsi all'unicità globale degli ID tenant-scoped.

### 6.2 Ruoli

Preparare almeno un'identità per:

- `ROLE_SUPER_ADMIN`;
- `ROLE_ADMIN`;
- `ROLE_TREASURER`;
- `ROLE_ARCHIVIST`;
- `ROLE_USER`;
- `ROLE_USER_EXTERNAL`;
- utente autenticato privo del ruolo richiesto;
- token senza tenant, tenant sconosciuto, tenant eliminato e token scaduto.

### 6.3 Dati limite

Includere:

- valori minimi, massimi, null, stringhe vuote e Unicode;
- date a cambio anno, mese, ora legale e fuso diverso;
- pagine vuote, una voce, ultima pagina e pagina oltre il limite;
- upload validi, corrotti, MIME falso, nome lungo, duplicato e dimensione al limite;
- CSV onboarding vuoto, 1 riga, 5.000 righe, colonne extra/mancanti e celle al limite;
- ricorrenze fino al limite di 500 occorrenze;
- importi positivi, negativi, zero, arrotondamenti e cambio esercizio;
- subscription push valide, scadute e respinte dal provider.

Ogni suite deve creare e ripulire i propri dati. La pulizia non deve mascherare un fallimento.

## 7. Preparazione e raccolta evidenze

Prima dell'esecuzione registrare:

```text
Test run ID:
Commit SHA:
Tag/digest immagini:
Ambiente:
Browser e versione:
Java/Node/pnpm/Docker:
Schema/migration version:
Configurazione feature non segreta:
Dataset/versione fixture:
Esecutore:
Ora inizio UTC:
```

Conservare:

- report JUnit, Karma e coverage;
- log applicativi e container relativi al run;
- screenshot/video solo per fallimenti UI o evidenze richieste;
- risultati scanner e performance;
- query di verifica prive di segreti/dati personali;
- elenco test saltati con motivazione;
- ticket per ogni fallimento non risolto.

## 8. Comandi delle suite esistenti

### 8.1 Governance documentale

```powershell
node scripts/docs/validate-feature-catalog.mjs
node --test scripts/docs/feature-catalog.test.mjs
node scripts/docs/generate-feature-index.mjs
git diff --exit-code -- docs/features.md
```

### 8.2 Backend

Suite unit/component:

```powershell
Set-Location taurus-be
.\mvnw.cmd test
```

Suite completa, incluse classi `*IT*` e `*IntTest*` tramite Failsafe:

```powershell
Set-Location taurus-be
.\mvnw.cmd verify
```

Build e test con profilo produzione:

```powershell
.\mvnw.cmd -Pprod clean verify
```

Test mirati tenant:

```powershell
.\mvnw.cmd "-Dtest=TenantContextTest,TenantSchemaNameResolverTest,SchemaMultiTenantConnectionProviderTest" test
.\mvnw.cmd "-Dit.test=TenantSchemaProvisioningServiceIT" failsafe:integration-test failsafe:verify
```

Verificare che Docker sia realmente disponibile: `TenantSchemaProvisioningServiceIT` usa un'assumption e può risultare saltato se Docker non è raggiungibile. Un test P0 saltato rende il run incompleto.

Report JaCoCo attesi sotto `taurus-be/target/site/jacoco` e `taurus-be/target/site/jacoco-it`.

### 8.3 Frontend

```powershell
Set-Location taurus-fe
npm ci
npm test -- --watch=false --browsers=ChromeHeadless --code-coverage
npm run build -- --configuration production
```

Controllare sia l'exit code sia il report coverage. Attualmente non esiste una soglia configurata: il report non costituisce da solo un gate.

### 8.4 Provider Keycloak

```powershell
Set-Location keycloak-authenticator
.\mvnw.cmd clean verify
```

La suite esistente verifica solo metadata del factory e service descriptor. La qualificazione richiede anche i casi reali della sezione 12.

### 8.5 Sito informativo

```powershell
Set-Location taurus-info
pnpm install --frozen-lockfile
pnpm check
pnpm build
docker build -t taurus-info:test .
docker run --rm -p 127.0.0.1:8088:80 taurus-info:test
```

In un secondo terminale verificare `http://localhost:8088/health` e le pagine principali. Non è presente una suite automatica dedicata.

## 9. Selezione delle suite per modifica

| Modifica | Suite minima aggiuntiva |
| --- | --- |
| Entità/repository/migration | Backend completo, fresh DB, upgrade DB, due tenant, soft delete e backup/restore se distruttiva |
| Security/ruoli/OIDC | Matrice ruoli, token negativi, due tenant, login/logout/refresh E2E |
| Feature flag | Tutte le combinazioni dipendenti, API negate, menu/route, cambio senza nuovo login |
| Scheduler/outbox | Idempotenza, retry, concorrenza, clock/timezone, multi-replica |
| Media/PDF/OCR | Tipi file, path traversal, limiti, rollback DB/file, cleanup e malware/content handling |
| RabbitMQ | Pubblicazione, consumo, redelivery, poison message, outage e recupero |
| Frontend condiviso/layout | Tutti i ruoli, responsive, tastiera, temi, build e smoke browser |
| Keycloak provider/theme | Build JAR, avvio Keycloak reale, login, selezione tenant/ruolo e rendering temi |
| Configurazione/manifest | Deploy staging, probe, secret, rete, restart, rollback e smoke |
| Sito informativo | Link, metadata, sitemap, 404, cache, CSP, responsive e health |
| Dipendenze | Suite completa del modulo, vulnerability scan e compatibility smoke |

## 10. Matrice trasversale P0

Eseguire per ogni risorsa tenant-scoped significativa:

| ID | Caso | Risultato atteso |
| --- | --- | --- |
| `MT-001` | Utente A legge record A | Consentito secondo ruolo |
| `MT-002` | Utente A usa ID di record B | `404` o rifiuto senza rivelare l'esistenza |
| `MT-003` | Utente A crea/aggiorna/elimina indicando tenant B nel payload | Tenant da token; nessuna scrittura in B |
| `MT-004` | Token senza claim tenant su endpoint tenant | Rifiuto controllato |
| `MT-005` | Claim tenant sconosciuto/eliminato | Rifiuto fail-closed |
| `MT-006` | Due richieste concorrenti A/B sul pool JDBC | Nessuna contaminazione di schema |
| `MT-007` | Eccezione durante richiesta A e riuso connessione da B | Schema ripristinato, nessun leak |
| `MT-008` | Scheduler attraversa A e B, A fallisce | B prosegue; contesto sempre corretto |
| `MT-009` | Media key/path di B richiesto da A | Accesso negato, nessun path leak |
| `MT-010` | Messaggio Rabbit di A elaborato dopo attività B | Contesto ricavato dal messaggio e isolato |
| `MT-011` | Feed/QR revocato o di altro tenant | Nessun dato esposto |
| `MT-012` | Export/report A | Nessun record o allegato B |

Ripetere CRUD, ricerca, paginazione, export, upload e cancellazione per almeno due tenant.

## 11. Autenticazione, autorizzazione e sessione

| ID | Caso | Priorità | Risultato atteso |
| --- | --- | --- | --- |
| `AUTH-001` | Login per ogni ruolo | P0 | Ruolo e tenant corretti; landing page consentita |
| `AUTH-002` | Password errata/utente disabilitato | P0 | Accesso negato senza dettaglio sensibile |
| `AUTH-003` | Token scaduto, firma errata o issuer diverso | P0 | `401`, nessuna elaborazione |
| `AUTH-004` | Token valido ma ruolo insufficiente | P0 | `403`; UI non sostituisce il controllo BE |
| `AUTH-005` | Accesso diretto a route FE non consentita | P1 | Redirect/rifiuto coerente |
| `AUTH-006` | Refresh token | P0 | Sessione rinnovata senza perdere tenant/ruolo |
| `AUTH-007` | Logout | P0 | Sessione locale e IdP chiuse come previsto |
| `AUTH-008` | Cambio tenant/ruolo nel provider | P0 | Nuovo contesto coerente, nessun dato precedente in cache |
| `AUTH-009` | Redirect URI e origin non autorizzati | P0 | Keycloak rifiuta |
| `AUTH-010` | Endpoint Actuator/API docs | P0 | Solo accessi previsti; health pubblico con dettagli limitati |
| `AUTH-011` | Super admin creato tramite API ordinaria | P0 | Operazione impossibile |
| `AUTH-012` | Clock skew e token vicino alla scadenza | P1 | Comportamento deterministico e osservabile |

Controllare la matrice ruolo × funzione almeno per super admin, admin, tesoriere, archivista, utente interno ed esterno. Verificare sia API sia menu/route.

## 12. Keycloak e provider custom

| ID | Caso | Risultato atteso |
| --- | --- | --- |
| `KC-001` | Installazione JAR su Keycloak 26.5.2 pulito | Provider rilevato senza errori SPI |
| `KC-002` | Import realm Taurus | Client, ruoli, flow e temi presenti |
| `KC-003` | Utente con un tenant/un ruolo | Selezione automatica o flow previsto |
| `KC-004` | Utente con più tenant/ruoli | Selettore completo, valori non manipolabili |
| `KC-005` | Tenant disabilitato/eliminato | Non selezionabile |
| `KC-006` | Parametro tenant/ruolo manomesso | Nessun claim non autorizzato |
| `KC-007` | Mapper OIDC | Claim tenant e ruoli corretti in ID/access token |
| `KC-008` | Login, reset password, TOTP ed e-mail | Tema renderizzato e flussi completabili |
| `KC-009` | Upgrade/restart Keycloak | Provider continua a caricarsi |
| `KC-010` | Database Keycloak temporaneamente indisponibile | Fail-closed e recupero dopo ritorno DB |

Automatizzare questi casi con Keycloak reale; i soli unit test attuali non bastano per qualificare il provider.

## 13. Database, Liquibase e lifecycle tenant

| ID | Caso | Priorità | Risultato atteso |
| --- | --- | --- | --- |
| `DB-001` | Database completamente vuoto | P0 | `master.xml` completa; backend ready |
| `DB-002` | Creazione primo tenant | P0 | Registry `ACTIVE`, schema e changelog completi |
| `DB-003` | Creazione concorrente stesso tenant | P0 | Una sola registrazione/schema; risultato idempotente |
| `DB-004` | Avvio con più tenant esistenti | P0 | Tutti migrati; nessun tenant saltato |
| `DB-005` | Riavvio senza nuove migration | P0 | Nessun cambiamento o errore |
| `DB-006` | Upgrade dalla versione precedente supportata | P0 | Dati preservati e nuovi vincoli validi |
| `DB-007` | Errore migration di un tenant | P0 | Avvio/stato coerente, errore diagnosticabile, nessuna falsa readiness |
| `DB-008` | Lock Liquibase concorrente | P0 | Un solo migrator; nessuna corruzione |
| `DB-009` | Permessi runtime insufficienti per DDL | P1 | Runtime opera; provisioning/migration usa ruolo previsto |
| `DB-010` | Soft delete e cascade | P0 | Record nascosti/cascati secondo specifica |
| `DB-011` | Cancellazione GDPR tenant | P0 | Commit, drop schema e registry `DELETED` coerenti |
| `DB-012` | Restore DB più media | P0 | Referenze e file coerenti allo stesso punto |

Per ogni changeset verificare fresh install e upgrade. Dopo il rilascio non alterare changeset applicati: aggiungere un nuovo changeset correttivo.

## 14. Casi funzionali

### 14.1 Tenant, utenti e preferenze

- `TEN-001 P0`: CRUD tenant, validazione duplicati e campi obbligatori.
- `TEN-002 P0`: provisioning ruoli e associazioni Keycloak.
- `TEN-003 P0`: aggiornamento concorrente e controllo versione.
- `TEN-004 P0`: disattivazione tenant e accesso successivo negato.
- `USR-001 P0`: creazione, modifica ruoli e appartenenza tenant.
- `USR-002 P0`: utente globale con profili distinti in due tenant.
- `USR-003 P0`: cancellazione `/me`, GDPR e amministrativa.
- `USR-004 P1`: preferenze e ultime ricerche isolate e persistenti.
- `USR-005 P1`: accettazioni legali versionate e non riutilizzate impropriamente.

### 14.2 Catalogo musicale

- `CAT-001 P1`: CRUD strumenti, album e tracce con validazioni.
- `CAT-002 P1`: associazioni ordinate album–tracce e parti/strumenti.
- `CAT-003 P0`: utente esterno vede solo contenuti pubblici.
- `CAT-004 P1`: paginazione, ordinamento, ricerca e filtri PostgreSQL.
- `CAT-005 P1`: soft delete, ripristino previsto e riuso dei riferimenti.
- `CAT-006 P1`: media di spartiti accessibili solo ai ruoli autorizzati.

### 14.3 Media, PDF e OCR

- `MED-001 P0`: upload, persistenza atomica, download e metadati.
- `MED-002 P0`: rollback DB elimina il file parziale; errore file non crea record.
- `MED-003 P0`: path traversal assoluto/relativo e separatori misti rifiutati.
- `MED-004 P0`: contenuto oltre 500 MB rifiutato anche dal proxy.
- `MED-005 P0`: estensione/MIME discordante e file corrotto gestiti.
- `MED-006 P1`: conversione PDF, pagine escluse e crop corretti.
- `MED-007 P1`: OCR spento/attivo, lingua o data path mancanti.
- `MED-008 P0`: cleanup elimina solo temporanei/orfani oltre soglia.
- `MED-009 P0`: file referenziato non viene cancellato.
- `MED-010 P1`: concorrenza upload/delete/download sullo stesso asset.
- `MED-011 P0`: upload PDF traccia restituisce `202` con job persistente in `TO_PROCESS`.
- `MED-012 P0`: worker e persistenza coprono `TO_PROCESS -> IN_PROGRESS -> DONE/ERROR` senza parti parziali visibili.
- `MED-013 P0`: consultazione e retry rispettano traccia, tenant, ruolo e idempotenza della pubblicazione.
- `MED-014 P1`: il dettaglio consulta i job una sola volta in `ngOnInit` e mostra lo stato persistente dopo refresh.
- `MED-015 P1`: `ERROR` presenta un'azione di retry che aggiorna la UI a «In coda» senza avviare polling.
- `MED-016 P0`: arresto del consumer, redelivery e retry non duplicano parti o media.

### 14.4 Calendario e ricorrenze

- `CAL-001 P1`: CRUD evento, timezone, visibilità e partecipanti.
- `CAL-002 P0`: utenti esterni vedono solo eventi pubblici.
- `CAL-003 P1`: disponibilità/presenza univoca e aggiornabile.
- `CAL-004 P1`: reminder default, override, disabilitazione e cambio orario.
- `REC-001 P0`: generazione serie entro 500 occorrenze.
- `REC-002 P1`: modifica intera serie, singola occorrenza e future.
- `REC-003 P1`: cancellazione e ripristino eccezione.
- `REC-004 P0`: DST, fine mese/anno e ricorrenza senza data valida.
- `REC-005 P0`: retry/idempotenza non duplica occorrenze o reminder.

### 14.5 Preparazione evento

- `PREP-001 P1`: configurazione, programma e materiali per admin.
- `PREP-002 P1`: archivista gestisce solo la parte catalogo autorizzata.
- `PREP-003 P1`: utente interno/esterno vede la vista minimizzata corretta.
- `PREP-004 P1`: tesoriere conferma budget o assenza movimenti.
- `PREP-005 P0`: scadenze generano una sola notifica per destinatario.
- `PREP-006 P0`: feature disabilitata blocca endpoint e scheduler.

### 14.6 Inventario e QR

- `INV-001 P0`: quantità totali, assegnate, restituite e residue coerenti.
- `INV-002 P0`: assegnazione oltre disponibilità rifiutata anche in concorrenza.
- `INV-003 P1`: revisioni, decisioni e audit immutabili.
- `INV-004 P1`: reso parziale/totale e foto associate.
- `INV-005 P0`: cancellazione/erasure non rompe la tracciabilità prevista.
- `INV-006 P1`: report/export rispetta filtri, ruoli e tenant.
- `INV-007 P1`: scadenze e notifiche una volta per finestra.
- `QR-001 P0`: risoluzione public ID restituisce solo dati pubblici previsti.
- `QR-002 P0`: ID inesistente, ruotato o tenant/feature disabilitati non espongono dati.
- `QR-003 P1`: rate limit per risoluzione applicato e recupera dopo finestra.
- `QR-004 P1`: etichetta/QR leggibile ai formati e dimensioni supportati.
- `QR-005 P0`: segnalazione problema e foto isolate per tenant.

### 14.7 Economia

- `FIN-001 P0`: conti, movimenti, categorie e saldi coerenti.
- `FIN-002 P0`: autorizzazione limitata a super admin/admin/tesoriere.
- `FIN-003 P0`: importi zero, negativi, decimali e arrotondamenti.
- `FIN-004 P1`: allegati seguono lifecycle media e permessi.
- `FIN-005 P0`: rollover annuale idempotente e timezone corretto.
- `FIN-006 P1`: rendiconto e totali riconciliabili con i movimenti.
- `FIN-007 P0`: cancellazione/soft delete non altera il saldo in modo incoerente.
- `FIN-008 P0`: feature disabilitata nasconde UI e nega API senza cancellare dati.

### 14.8 Notifiche, preferenze e Web Push

- `NOT-001 P0`: pubblicazione outbox nello stesso commit del dominio.
- `NOT-002 P0`: rollback dominio non lascia notifica.
- `NOT-003 P0`: dispatcher idempotente non duplica destinatari.
- `NOT-004 P1`: retry esponenziale, errore sanitizzato e chiusura manuale.
- `NOT-005 P1`: retention elimina solo stati e date eleggibili.
- `PREF-001 P1`: default tenant/applicativo e override utente.
- `PREF-002 P1`: pausa, snooze, digest e quiet hours ai limiti.
- `PREF-003 P0`: notification REQUIRED ignora opt-out di categoria previsto.
- `PUSH-001 P0`: registrazione e revoca subscription.
- `PUSH-002 P0`: VAPID errata produce failure osservabile e retry controllato.
- `PUSH-003 P1`: subscription invalida rimossa senza bloccare le altre.
- `PUSH-004 P0`: reminder non viene inviato dopo l'inizio evento.
- `PUSH-005 P1`: anteprima privata non espone titolo/contenuto sensibile.

### 14.9 Feed calendario esterno

- `FEED-001 P0`: creazione, rotazione e revoca token.
- `FEED-002 P0`: token non appare in log, metriche, referrer o errori.
- `FEED-003 P0`: scope interno/pubblico coerente con ruolo proprietario.
- `FEED-004 P1`: GET e HEAD, ETag/cache e contenuto iCalendar valido.
- `FEED-005 P1`: tombstone `CANCELLED` e relativa retention.
- `FEED-006 P0`: rate limit token/IP/globale e risposta non informativa.
- `FEED-007 P0`: idempotency key non duplica operazioni e non attraversa tenant.
- `FEED-008 P1`: collaudo reale con Google Calendar, Apple Calendar e Outlook.

Lo stato della feature è `in-progress`; un rilascio che la abilita deve includere i collaudi client e la verifica della redazione del token su proxy/WAF/APM.

### 14.10 Onboarding e importazione

- `ONB-001 P0`: template e import valido end-to-end.
- `ONB-002 P0`: validazione limiti file, 5.000 righe, 2.000 utenti, 64 colonne e cella da 10.000 caratteri.
- `ONB-003 P0`: CSV injection, encoding, delimitatori, quote e newline.
- `ONB-004 P1`: preview/report coerente con applicazione effettiva.
- `ONB-005 P0`: errore Keycloak attiva compensazione senza identità orfane.
- `ONB-006 P0`: crash in ogni fase e recovery idempotente.
- `ONB-007 P0`: retry e-mail non reinvia quelle già riuscite.
- `ONB-008 P1`: cancellazione job prima/durante applicazione secondo stato.
- `ONB-009 P0`: sezioni Economia/Inventario filtrate dai feature flag.
- `ONB-010 P1`: retention sorgente e audit applicata correttamente.

### 14.11 Dashboard e feature flag

- `DASH-001 P1`: operazioni aggregate per ruolo senza duplicati.
- `DASH-002 P1`: blocker, warning e follow-up ordinati e conteggiati.
- `DASH-003 P0`: nessun dato di moduli/tenant non autorizzati.
- `FLAG-001 P0`: tutti gli otto flag default `false` per nuovo tenant.
- `FLAG-002 P0`: capacità globale spenta prevale sul flag tenant acceso.
- `FLAG-003 P0`: dipendenza spenta rende inefficace l'estensione.
- `FLAG-004 P0`: cambio flag diventa effettivo senza nuovo login.
- `FLAG-005 P0`: UI, route, API, scheduler e notifiche reagiscono coerentemente.
- `FLAG-006 P0`: disabilitare/riabilitare conserva i dati.
- `FLAG-007 P1`: update concorrente dei flag non perde modifiche.

## 15. Frontend, UX e accessibilità

Eseguire i flussi principali almeno su Chrome, Edge, Firefox e Safari nelle versioni supportate definite dal prodotto; includere viewport mobile, tablet e desktop.

| ID | Caso | Risultato atteso |
| --- | --- | --- |
| `UI-001` | Navigazione da tastiera | Ordine focus logico, focus visibile, nessun trap |
| `UI-002` | Dialoghi | Titolo/label associati, Escape e ripristino focus corretti |
| `UI-003` | Icon-only button | Nome accessibile presente |
| `UI-004` | Validazione form | Errore associato al campo e annunciabile |
| `UI-005` | Loading/empty/error/success | Stato comprensibile e senza layout shift grave |
| `UI-006` | Tema chiaro/scuro e contrasto | Testo e controlli leggibili |
| `UI-007` | Zoom 200% e reflow | Nessuna perdita di funzione/contenuto |
| `UI-008` | Responsive | Tabelle, menu e dialoghi utilizzabili |
| `UI-009` | Refresh/deep link | Route Angular caricata senza 404 |
| `UI-010` | Update service worker | Nuova versione applicata senza bundle incompatibili |
| `UI-011` | Offline/rete lenta | Errore controllato; nessuna falsa conferma di salvataggio |
| `UI-012` | Doppio click/reinvio | Nessuna duplicazione di operazioni non idempotenti |

Integrare uno scanner automatico di accessibilità e conservare un controllo manuale per tastiera, screen reader e contenuti dinamici.

## 16. Sito informativo

- `INFO-001 P1`: tutte le pagine costruiscono senza errori.
- `INFO-002 P1`: `SITE_URL`, app URL e contatto corretti nell'artefatto.
- `INFO-003 P1`: link interni/esterni e CTA non rotti.
- `INFO-004 P1`: metadata, canonical, sitemap e robots coerenti.
- `INFO-005 P1`: `/health` restituisce `200`, route inesistente usa 404.
- `INFO-006 P1`: cache immutabile per `_astro`, HTML no-cache.
- `INFO-007 P0`: CSP e security header presenti e senza risorse bloccate.
- `INFO-008 P1`: responsive, accessibilità e performance Lighthouse.
- `INFO-009 P1`: nessun segreto o URL interno nel bundle statico.

## 17. Test non funzionali

### 17.1 Performance

Definire prima SLO, dataset e capacità attesa. Profili minimi:

- smoke: carico minimo, verifica script;
- load: traffico nominale sostenuto;
- stress: crescita fino al limite e degradazione;
- spike: aumento improvviso;
- soak: traffico nominale prolungato per leak e accumuli;
- capacity: tenant, utenti, media e job ai volumi futuri previsti.

Misurare p50/p95/p99, throughput, errori, CPU, memoria, GC, pool JDBC, query lente, I/O storage, backlog RabbitMQ e durata scheduler. Testare separatamente login, dashboard, liste paginated, upload/download, feed anonimo e notification dispatch.

I limiti di accettazione sono `TBD` finché SLO e volumi non vengono approvati.

### 17.2 Concorrenza

Verificare almeno:

- aggiornamenti concorrenti dello stesso tenant e feature flag;
- assegnazione inventario concorrente;
- rotazione/revoca concorrente di feed e QR;
- dispatch multiplo della stessa outbox;
- provisioning tenant simultaneo;
- più repliche backend con scheduler attivi;
- upload/delete simultaneo dello stesso media.

### 17.3 Resilienza

In staging, introdurre una dipendenza alla volta:

- PostgreSQL non raggiungibile e lento;
- Keycloak non raggiungibile o certificato invalido;
- RabbitMQ interrotto durante publish/consume;
- filesystem read-only, pieno o smontato;
- provider push con timeout/4xx/5xx;
- kill/restart backend durante onboarding o migration controllata;
- DNS e latenza di rete degradati.

Per ogni prova verificare timeout, retry limitato, assenza di perdita/duplicazione, readiness, alert e recupero automatico. Non eseguire fault injection in produzione senza autorizzazione specifica.

### 17.4 Sicurezza

La suite deve includere:

- SAST e dependency/container scan;
- secret scan della history e degli artefatti;
- DAST autenticato e non autenticato in staging;
- OWASP Top 10 web/API;
- IDOR/BOLA su tutti gli ID tenant-scoped;
- mass assignment, over-posting e campi audit/tenant ignorati;
- SQL injection, path traversal, XSS persistente/riflesso e CSV injection;
- CORS, CSP, cookie, redirect e header;
- rate limit feed/QR e abuso upload;
- token leak in URL/log/APM;
- PDF/immagini malevoli e decompression/resource exhaustion;
- esposizione Actuator e messaggi di errore;
- verifica firma e provenienza immagini.

Ogni finding deve avere severità, exploitability, componente, versione, owner e decisione. Una semplice assenza di finding automatizzati non sostituisce i test di autorizzazione.

### 17.5 Backup, restore e DR

Usare la procedura del [runbook operativo](runbook-operativo.md). La prova deve dimostrare:

- restore di database Taurus e Keycloak;
- restore dello storage media allo stesso punto;
- login e token validi;
- registry e schemi tenant coerenti;
- download dei media campionati;
- RPO/RTO effettivi;
- assenza di split brain durante failover/failback.

## 18. Cicli di esecuzione

### 18.1 Pull request

- L0, L1 e L2 dei moduli toccati;
- L3 mirati per DB, Keycloak o integrazioni modificate;
- test di regressione del difetto corretto;
- catalogo/spec aggiornati nella stessa PR;
- build produzione dei moduli toccati.

### 18.2 Nightly

- backend `verify` completo con Docker disponibile;
- frontend completo con coverage;
- avvio full-stack e E2E;
- scanner sicurezza;
- migrazione da snapshot della versione supportata;
- browser matrix distribuita;
- controllo leak/flaky e test saltati.

### 18.3 Release candidate

1. Congelare commit, dipendenze e digest.
2. Creare staging pulito production-like.
3. Eseguire fresh install e upgrade.
4. Eseguire tutte le P0 e P1.
5. Eseguire E2E per tutti i ruoli.
6. Eseguire sicurezza, accessibilità e performance.
7. Eseguire backup/restore e rollback applicativo.
8. Correggere o accettare formalmente ogni eccezione.
9. Firmare il report go/no-go.

### 18.4 Post-deploy

Eseguire solo test sintetici non distruttivi:

- health/info e asset frontend;
- login/logout con account sintetico;
- lettura isolata su due tenant sintetici;
- query core in sola lettura;
- presenza consumer RabbitMQ e assenza backlog anomalo;
- controllo error rate, latency e scheduler;
- health e link principale del sito informativo.

## 19. Gap di copertura da chiudere

| Priorità | Gap attuale | Azione |
| --- | --- | --- |
| Bloccante | Nessuna suite E2E browser full-stack | Introdurre Playwright/Cypress equivalente con ruoli e tenant multipli |
| Bloccante | Nessun test automatico completo del provider Keycloak | Eseguire flow e mapper contro Keycloak reale |
| Bloccante | Tenant isolation API non coperta sistematicamente per tutte le risorse | Creare test parametrizzati CRUD/IDOR per resource |
| Alta | Nessuna integrazione RabbitMQ reale/poison-message | Aggiungere Testcontainers RabbitMQ e casi redelivery/outage |
| Alta | Nessuna soglia coverage | Definire baseline, ratchet e gate per changed code |
| Alta | Nessun test performance/SLO | Definire carichi e integrare uno strumento ripetibile |
| Alta | Nessun security scan completo in CI | Aggiungere SAST, SCA, secret e container scan; DAST su staging |
| Alta | Nessuna automazione backup/restore | Programmare restore test e misurare RPO/RTO |
| Alta | Scheduler multi-replica non qualificati | Testare concorrenza o introdurre locking/worker dedicato |
| Media | Solo 18 specifiche frontend | Estendere componenti, form, pagine e casi errore/ruolo |
| Media | Sito informativo senza test | Aggiungere link/header/SEO/accessibilità e smoke container |
| Media | Nessuna browser/device matrix automatica | Definire browser supportati e pipeline |
| Media | Nessun gate accessibilità | Integrare scanner più verifica manuale periodica |
| Media | Test Docker-required potenzialmente skipped | Far fallire la pipeline P0 quando Docker non è disponibile |
| Media | Nessun controllo sistematico dei test flaky | Raccogliere storico, quarantena con scadenza e owner |

## 20. Gestione difetti e flaky test

| Severità | Definizione | Decisione |
| --- | --- | --- |
| P0/Critical | Violazione tenant/sicurezza, perdita dati, impossibilità di avvio/login/migrazione | Stop release immediato |
| P1/High | Funzione primaria indisponibile o risultato errato senza workaround sicuro | Stop, salvo accettazione formale eccezionale |
| P2/Medium | Funzione secondaria degradata con workaround | Valutazione owner prodotto/tecnico |
| P3/Low | Difetto cosmetico o miglioramento | Pianificabile |

Un test flaky non va semplicemente rilanciato fino al verde. Registrare frequenza, evidenza e ticket; se messo in quarantena, assegnare owner e scadenza. Un flaky P0 rende il gate non affidabile e va risolto prima della release.

## 21. Report di esecuzione

```text
Test run ID:
Release candidate / commit / digest:
Ambiente e configurazione:
Data e durata UTC:
Suite previste:
PASS / FAIL / BLOCKED / NOT RUN / N/A:
Test P0 saltati:
Coverage BE line/branch:
Coverage FE line/branch:
Vulnerabilità per severità:
Risultati performance vs SLO:
Risultato accessibilità:
Risultato restore e RPO/RTO:
Difetti aperti e accettazioni:
Rischi residui:
Decisione: GO | NO-GO | CONDITIONAL GO
Approvazioni:
Link artefatti/evidenze:
```

## 22. Checklist go/no-go

- [ ] Commit e digest sono immutabili e registrati.
- [ ] Catalogo documentale valido e specifiche allineate.
- [ ] Nessuna suite obbligatoria è `BLOCKED` o `NOT RUN`.
- [ ] Tutte le P0 e P1 previste sono passate.
- [ ] Nessun test Docker-required è stato saltato.
- [ ] Fresh install e upgrade hanno lo stesso esito funzionale.
- [ ] Tenant isolation verificata su API, DB, media, feed, QR e job.
- [ ] Matrice ruoli verificata lato BE e FE.
- [ ] Login/logout/refresh e provider Keycloak funzionano.
- [ ] RabbitMQ e processi asincroni recuperano dopo un'interruzione.
- [ ] Coverage raggiunge la soglia approvata.
- [ ] Sicurezza, accessibilità e performance rispettano i gate.
- [ ] Backup/restore è valido entro la finestra prevista.
- [ ] Rollback o forward fix è provato e documentato.
- [ ] Difetti e rischi residui hanno owner e accettazione.
- [ ] Report firmato da responsabile tecnico e prodotto/operazioni.

## 23. Manutenzione del runbook

Per ogni nuova funzionalità o regressione:

1. aggiornare la specifica e `docs/features.json`;
2. aggiungere casi positivi, negativi, limite, autorizzazione e tenant isolation;
3. automatizzare almeno i casi P0/P1 ripetibili;
4. indicare il test di regressione nel catalogo;
5. aggiornare dati, ambienti e comandi se cambiano;
6. rieseguire la qualificazione proporzionata al rischio.

Revisionare il runbook almeno a ogni release maggiore e dopo incidenti, restore o cambi infrastrutturali. La fonte autorevole delle funzionalità rimane `docs/features.json`; questo documento deve seguirne l'evoluzione.

## 24. Riferimenti

- [Runbook operativo](runbook-operativo.md);
- [Catalogo funzionalità](features.md);
- [Governance documentazione](documentation-implementation-alignment-spec.md);
- [Schema PostgreSQL multi-tenant](postgres-tenant-schemas.md);
- [Feature flag tenant](tenant-feature-flags-spec.md);
- [Onboarding/import](tenant-onboarding-import-spec.md);
- [Notifiche](notification-delivery-generalization-spec.md);
- [Media](media-asset-spec.md);
- [Elaborazione PDF tracce](track-pdf-processing-spec.md);
- [Feed calendario](external-calendar-feed-spec.md);
- `.github/workflows/verify.yml` per la CI corrente.
