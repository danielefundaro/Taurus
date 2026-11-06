# Aggiornamento di librerie, framework e servizi

Il documento pianifica l'aggiornamento di tutto ciò da cui Taurus dipende all'ultima versione stabile: runtime, framework, librerie, servizi, immagini Docker, strumenti di build e azioni della CI, in tutti i moduli. Fissa anche le regole per restare aggiornati dopo, così che il ritardo non si accumuli di nuovo.

L'occasione è il job `security` di `.github/workflows/verify.yml`: tornato a funzionare il 02/11/2026, Trivy ha trovato 90 vulnerabilità `HIGH` e `CRITICAL` nelle dipendenze e 4 problemi nei Dockerfile, e blocca la pipeline. Molte vulnerabilità non hanno una correzione nelle versioni maggiori in uso: Angular 19, Spring Boot 3.3, Keycloak 26.6 e nginx 1.29 sono fuori supporto.

Le verifiche di ogni modulo restano quelle di `AGENTS.md` e del [Runbook di test](runbook-test.md); le procedure di aggiornamento in produzione vanno nel [Runbook operativo](runbook-operativo.md).

## Stato del documento

ID catalogo: `platform-upgrade`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

Bozza del 2026-11-02, approvata il 2026-11-06 con tutte le scelte di [Decisioni](#decisioni). Le versioni obiettivo sono le ultime stabili a quella data, lette dai registri ufficiali (Maven Central, npm, PyPI, GitHub, endoflife.date).

## Problema

- **CI bloccata.** Trivy segnala 9 vulnerabilità nel backend, 38 nel frontend, 14 nel sito informativo e 29 nel servizio OMR, di cui 8 `CRITICAL`. Fra queste: un'esecuzione di codice remoto in Astro, un aggiramento dell'autenticazione degli endpoint Actuator di Spring Boot, vulnerabilità XSS nel compilatore di Angular e certificati compromessi in `certifi`.
- **Versioni fuori supporto.** Angular 19 è fuori supporto dal 19/05/2026, Spring Boot 3.3 dal 2025, Keycloak 26.6 dal 09/07/2026, nginx 1.29 dal 13/05/2026, JHipster 8 dall'11/03/2026. Per alcune vulnerabilità di Angular la correzione esiste solo dalla 20 in su.
- **Ritardo accumulato.** Il frontend è fermo alla prima patch di Angular 19 (19.0.5), il backend a Spring Boot 3.3.5 di fine 2024, la CI usa azioni di due versioni maggiori fa. Il job `security` è rimasto rotto per mesi senza che nessuno se ne accorgesse.
- **Nessun aggiornamento automatico.** Il repository non ha Dependabot né altri strumenti che propongano gli aggiornamenti: ogni versione resta ferma finché qualcuno non la cambia a mano.

## Obiettivo

- Ogni componente all'ultima versione stabile compatibile con gli altri, con le eccezioni motivate in [Eccezioni](#eccezioni-alla-versione-più-recente).
- Il job `security` verde senza eccezioni permanenti: ogni vulnerabilità ignorata ha un motivo e una scadenza.
- Nessuna regressione: le verifiche di ogni modulo, le soglie di copertura e la suite end-to-end passano come prima.
- Aggiornamenti futuri proposti in automatico, piccoli e frequenti, e non più un grande salto ogni due anni.
- Nessun costo: solo strumenti gratuiti, come chiede il vincolo del progetto.

## Fuori scope

- Nuove funzionalità o cambi di comportamento visibili all'utente, salvo quelli imposti da un aggiornamento e descritti qui.
- Il passaggio di Angular alla rilevazione delle modifiche senza zone.js (zoneless), che è un cambio di architettura del frontend: da valutare a parte.
- La migrazione dei test del frontend da Karma e Jasmine a Vitest (decisione 6).
- Il riordino del codice generato da JHipster che non serve più, salvo quando l'aggiornamento lo rende inutilizzabile.
- Il passaggio del servizio OMR a Ubuntu 26.04 finché Audiveris non pubblica un pacchetto per quella versione (decisione 9).
- La riscrittura della cronologia git o la rotazione dei segreti: la ricerca dei segreti è già verde.

## Contesto attuale

Inventario del 02/11/2026. «Salto» indica il tipo di aggiornamento: patch e minor non dovrebbero rompere nulla, una maggiore richiede modifiche al codice.

### Runtime e strumenti

| Componente | Attuale | Obiettivo | Salto | Note |
| --- | --- | --- | --- | --- |
| Java (backend) | 17 | 25 LTS | maggiore | supporto fino al 2031 |
| Java (provider Keycloak) | 17 | quella del runtime di Keycloak | — | decisione 3 |
| Node.js | 22 | 26 LTS | maggiore | LTS dal 28/10/2026; Angular 22 e Astro 7 lo supportano |
| pnpm | 11.16 | 12.x | maggiore | sito informativo |
| Python (OMR) | 3.12 | 3.12 | — | eccezione: decisione 9 |
| TypeScript | 5.6 (FE), 5.9 (info) | 6.0 | maggiore | eccezione: la 7 non è supportata da Angular |
| Maven | 3.9.6 (immagine), wrapper | 3.10.x | minor | |

### Backend (`taurus-be`)

| Componente | Attuale | Obiettivo | Salto | Note |
| --- | --- | --- | --- | --- |
| Spring Boot | 3.3.5 | 4.1.x | maggiore | Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3 |
| JHipster framework | 8.7.2 | 9.4.x | maggiore | costruito su Spring Boot 4.1 |
| Spring Cloud (BOM) | 2023.0.3 | rimosso | — | importato ma non usato da nessuna classe |
| springdoc-openapi | 2.6.0 | 3.x | maggiore | linea per Spring Boot 4 |
| Liquibase e liquibase-hibernate | 4.x | 5.x, `liquibase-hibernate7` | maggiore | licenza: vedi [Licenze](#licenze) |
| Testcontainers | 1.21.4 | 2.x | maggiore | nuovi nomi di artefatti e package |
| PostgreSQL JDBC | 42.7.4 | 42.7.x | patch | 3 CVE |
| RabbitMQ Java client | 5.25.0 | 5.37.x | minor | 4 CVE |
| commons-beanutils | 1.9.4 | 1.11.x | minor | 1 CVE |
| Apache POI | 5.3.0 | 6.x | maggiore | |
| PDFBox, pdfbox-tools, jbig2 | 3.0.3 | 3.0.x | patch | |
| openhtmltopdf | 1.1.44 | 1.1.x | patch | |
| ical4j | 4.2.4 | 4.4.x | minor | |
| MapStruct, ArchUnit, ZXing, commons-csv, commons-compress, web-push | varie | ultime | minor/patch | |
| Plugin Maven (compiler, surefire, failsafe, enforcer, jar, javadoc…) | varie | ultime | minor | JaCoCo almeno 0.8.14 per Java 25 |
| Spotless, Checkstyle, Jib, git-commit-id, modernizer, Sonar | varie | ultime | maggiore per alcuni | |

### Frontend (`taurus-fe`)

| Componente | Attuale | Obiettivo | Salto | Note |
| --- | --- | --- | --- | --- |
| Angular (core, CLI, CDK) | 19.0.5 | 22.2.x | tre maggiori | 22 CVE, alcune corrette solo dalla 20 |
| Builder | `@angular-devkit/build-angular` | `@angular/build` | — | migrazione di `ng update` |
| PrimeNG | 19.0.10 | 22.x | tre maggiori | supporta Angular 22 |
| Temi PrimeNG | `@primeng/themes` 19 | `@primeuix/themes` 3.x | pacchetto nuovo | il vecchio pacchetto è deprecato |
| PrimeIcons | 7.0.0 | 8.x | maggiore | |
| keycloak-angular / keycloak-js | 19.0.2 / 26.2.2 | 22.x / 26.2.x | maggiore / patch | |
| ngx-extended-pdf-viewer | 28.1.2 | 31.x | maggiore | la 28 include un pdf.js vulnerabile |
| Tailwind CSS e tailwindcss-primeui | 3.4 / 0.5 | 4.3 / 0.6 | maggiore | configurazione CSS al posto di `tailwind.config.js` |
| zone.js, rxjs, tslib | 0.15, 7.8.1 | 0.16, 7.8.x | minor | zone.js resta: [Fuori scope](#fuori-scope) |
| chart.js, verovio, spessasynth_lib | 4.4.2, 6.3.0, 4.3.14 | 4.6, 6.4, 4.4 | minor | lettore e rendering degli spartiti da ricontrollare |
| ESLint, Prettier, Playwright, axe-core | 9, 3.4, 1.49, 4.10 | 10, 3.9, 1.64, 4.14 | maggiore per ESLint | |
| Karma, Jasmine | 6.4, 5.4 | 6.4, ultima compatibile | — | decisione 6 |
| Dipendenze transitive (form-data, glob, minimatch, nanoid, postcss, tmp…) | varie | corrette | — | si allineano con il nuovo lockfile |

### Sito informativo (`taurus-info`)

| Componente | Attuale | Obiettivo | Salto | Note |
| --- | --- | --- | --- | --- |
| Astro | 5.18.2 | 7.4.x | due maggiori | esecuzione di codice remoto (`CRITICAL`) corretta nella 7 |
| @astrojs/sitemap, @astrojs/check | 3.7.3, 0.9.10 | ultime | patch | |
| Tailwind CSS e @tailwindcss/vite | 4.3.3 | 4.3.x | — | già aggiornati |
| Dipendenze transitive (sharp, vite, devalue, svgo, js-yaml) | varie | corrette | — | arrivano con Astro 7 |

### Servizio OMR (`taurus-omr`)

| Componente | Attuale | Obiettivo | Salto | Note |
| --- | --- | --- | --- | --- |
| Pillow | 12.3.0 | 13.0.x | maggiore | 28 CVE, 5 `CRITICAL`; homr 0.7.0 richiede `Pillow<13`: [Eccezioni](#eccezioni-alla-versione-più-recente) |
| certifi | 2026.7.22 | 2026.10.x | — | radici emSign compromesse (`CRITICAL`) |
| FastAPI, Starlette, Pydantic, python-multipart, ONNX Runtime | varie | ultime | minor | |
| opencv-python-headless | 4.14 | 5.0 | maggiore | solo se homr lo accetta: decisione 9 |
| music21 | 9.7.1 | 10.x | maggiore | solo se homr lo accetta: decisione 9 |
| homr, RapidOCR, Audiveris | 0.7.0, 3.10.0, 5.11.0 | invariati | — | già alle ultime versioni |
| Immagine di base | Ubuntu 24.04 | Ubuntu 24.04 | — | eccezione: decisione 9 |

### Provider Keycloak (`keycloak-authenticator`)

| Componente | Attuale | Obiettivo | Salto | Note |
| --- | --- | --- | --- | --- |
| Keycloak (SPI e immagine) | 26.6.4 | 26.8.x | minor | la 26.6 è fuori supporto |
| FreeMarker | 2.3.32 | quella di Keycloak 26.8 | patch | resta allineata al runtime |
| Spring Boot (parent del modulo) | 4.0.2 | 4.1.x | minor | |

### Servizi e immagini Docker

| Componente | Attuale | Obiettivo | Salto | Note |
| --- | --- | --- | --- | --- |
| PostgreSQL | 17.0 | 18.x | maggiore | migrazione dei dati: decisione 10 |
| RabbitMQ | 4.0.7 | 4.4.x | minor | percorso di aggiornamento: decisione 11 |
| nginx (frontend e sito) | 1.29-alpine | 1.30 stabile, senza root | minor | decisione 8 |
| eclipse-temurin (backend) | 17-jre | 25-jre | maggiore | anche l'immagine di Jib, oggi `17-jre-focal` |
| maven (build del backend) | 3.9.6-eclipse-temurin-17 | 3.10.x con Temurin 25 | — | |
| node (build di frontend e sito) | 22-alpine | 26-alpine | maggiore | |
| Prometheus | 2.55.0 | 3.13 LTS | maggiore | solo sviluppo |
| Grafana | 11.3.0 | 13.3.x | maggiore | solo sviluppo |
| SonarQube | 10.7.0-community | Community Build 26.x | maggiore | solo sviluppo |
| Mailpit | 1.31.3 | 1.33.x | minor | solo sviluppo |
| JHipster Control Center | v0.5.0 (2021) | rimosso | — | abbandonato dal 2021: decisione 12 |
| Dev container | `vscode/devcontainers/java:0-17` | immagine `devcontainers/java` per Java 25 | maggiore | utente non root |

### CI e strumenti di sicurezza

| Componente | Attuale | Obiettivo | Note |
| --- | --- | --- | --- |
| actions/checkout | v4 | v7 | |
| actions/setup-node, setup-java, setup-python | v4, v4, v5 | v7, v6, v7 | |
| actions/upload-artifact, attest-build-provenance | v4, v1 | v7, v4 | |
| docker/build-push-action, login, metadata, setup-buildx | v5, v3, v5, v3 | v7, v4, v6, v4 | |
| sigstore/cosign-installer | v3.5.0 | v4.x | |
| pnpm/action-setup | v4 | v6 | |
| aquasecurity/trivy-action | v0.36.0, Trivy 0.70.0 | ultima azione, Trivy 0.76.x | la versione di Trivy diventa esplicita |
| gitleaks | 8.21.2 | 8.30.x | |
| semgrep | 1.97.0 | 1.183.x | |
| Trivy nel runbook di test | 0.56.2 | uguale alla CI | |

## Decisioni

Proposte del 02/11/2026, approvate il 06/11/2026.

### 1. Ultima versione stabile, non l'ultima pubblicata

Si usa l'ultima versione **stabile**: niente milestone, release candidate, beta o versioni `next`. Se un componente non supporta ancora la versione più recente di un altro, si sceglie la più recente compatibile e la si elenca in [Eccezioni](#eccezioni-alla-versione-più-recente). Al momento di ogni fase si rileggono le patch: le versioni di questo documento sono il minimo, non un tetto.

### 2. Versioni maggiori un passo alla volta

Dove il produttore fornisce migrazioni automatiche, si sale una maggiore per volta: Angular e PrimeNG da 19 a 20, 21 e 22 con `ng update`; Astro da 5 a 6 e poi a 7; Spring Boot prima a 3.5, togliendo le deprecazioni, poi a 4.0 e a 4.1. Ogni passo compila e supera i test prima del successivo. Saltare direttamente all'ultima versione perderebbe le migrazioni automatiche intermedie.

### 3. Java 25 per il backend, la versione di Keycloak per il provider

Il backend passa a Java 25 LTS: build, immagini, Jib, CI e `java.version`. Il provider Keycloak gira dentro l'immagine di Keycloak e deve essere compilato per la versione di Java di quell'immagine, con `--release`. Un provider compilato per una versione più recente non verrebbe caricato. La versione si legge dall'immagine di Keycloak 26.8 al momento dell'implementazione.

### 4. Spring Boot 4.1 con JHipster framework 9.4

JHipster framework 9.4 è costruito su Spring Boot 4.1: le due versioni salgono insieme. Fanno parte della migrazione:

- Jackson 3: i package `com.fasterxml.jackson.core` e `databind` diventano `tools.jackson`, mentre le annotazioni restano in `com.fasterxml.jackson.annotation`. Riguarda 88 file. Non si usa il modulo di compatibilità con Jackson 2, per non dover fare la migrazione due volte.
- Hibernate 7 e Jakarta Persistence 3.2, nei 14 file che importano Hibernate.
- I nuovi nomi degli starter, per esempio `spring-boot-starter-aspectj` al posto di `spring-boot-starter-aop`.
- La rimozione di Undertow, non più supportato: oggi compare solo nel profilo Maven `eclipse`.
- Testcontainers 2 nei 10 file di test che lo usano.
- La rimozione del BOM di Spring Cloud, importato ma non usato da nessuna classe.

### 5. Angular 22 con PrimeNG 22 e TypeScript 6.0

Angular 22 richiede TypeScript 6.0. PrimeNG 22 è la prima versione che dichiara il supporto ad Angular 22. Il tema passa a `@primeuix/themes`. `docs/llms-full.md` va rigenerato dalla documentazione di PrimeNG 22, che `AGENTS.md` indica come riferimento. Tailwind passa alla 4 nello stesso passo, perché `tailwindcss-primeui` 0.6 accompagna i temi nuovi. L'impatto riguarda 129 file che importano PrimeNG, 81 dei quali per `DynamicDialog`.

### 6. Karma e Jasmine restano per ora

Karma è deprecato, ma `@angular/build` 22 lo supporta ancora. Migrare 99 file di test a Vitest nello stesso momento renderebbe impossibile capire se un test fallisce per l'aggiornamento o per la migrazione. La migrazione a Vitest è un lavoro a parte, da fare dopo questo. Diventa prioritaria se Karma riceve vulnerabilità senza correzione.

### 7. Dependabot con aggiornamenti raggruppati

Si aggiunge `.github/dependabot.yml`, gratuito, per Maven, npm, pnpm, pip, Docker e GitHub Actions in tutte le cartelle dei moduli. Le pull request sono settimanali e raggruppate per modulo: un gruppo per patch e minor, una pull request separata per ogni maggiore. Le azioni GitHub restano fissate per SHA del commit con la versione in commento, come in `docker-publish.yml` e come imposto dopo la manomissione dei tag di `trivy-action` del marzo 2026. Dependabot aggiorna sia lo SHA sia il commento.

### 8. nginx stabile e senza root

Frontend e sito informativo usano la linea stabile di nginx (le versioni pari, oggi la 1.30) nella variante senza privilegi `nginxinc/nginx-unprivileged`. Il container ascolta sulla porta 8080 e non sulla 80. Questo risolve DS-0002 di Trivy e cambia la porta interna nei compose, nei controlli di salute e negli esempi del reverse proxy. Il backend aggiunge `--no-install-recommends` all'installazione di `curl` (DS-0029).

### 9. Servizio OMR su Ubuntu 24.04 e Python 3.12

Audiveris 5.11.0, ultima versione, pubblica pacchetti solo per Ubuntu 22.04 e 24.04. Il servizio resta quindi su Ubuntu 24.04 e sul suo Python 3.12, supportato per la sicurezza fino al 2028. Tutte le librerie Python salgono all'ultima versione che homr 0.7.0 accetta. Se homr limita una maggiore, come OpenCV 5 o music21 10, resta la versione più recente ammessa e la si elenca in [Eccezioni](#eccezioni-alla-versione-più-recente). Il passaggio a Ubuntu 26.04 si fa quando esce il pacchetto di Audiveris. Prima del rilascio servono i test di accuratezza sul corpus in `accuracy/`.

### 10. PostgreSQL 18 con dump e ripristino

Il passaggio da 17 a 18 è una maggiore: i file dei dati non sono compatibili. La procedura è un `pg_dumpall` dal 17 e un ripristino nel 18, con il backend e Keycloak fermi. È più lenta di `pg_upgrade`, ma è la stessa per ogni installazione e non richiede entrambe le versioni nello stesso container. L'immagine `postgres:18` cambia anche il punto di montaggio del volume, da `/var/lib/postgresql/data` a `/var/lib/postgresql`: il compose va adattato. La procedura e il ritorno indietro vanno nel runbook operativo. Gli ambienti di sviluppo possono ricreare il volume.

### 11. RabbitMQ 4.4 dopo aver svuotato le code

Prima dell'aggiornamento si abilitano tutti i feature flag stabili e si verifica nella documentazione di RabbitMQ se il salto da 4.0 a 4.4 è diretto. Se non lo è, le code di Taurus contengono solo lavori ripetibili, come l'elaborazione dei PDF. Si può quindi lasciarle svuotare, fermare il nodo e ricrearlo con la 4.4 su un volume nuovo, invece di passare per ogni versione minore intermedia.

### 12. Rimozione di JHipster Control Center

`jhipster-control-center` è fermo alla v0.5.0 del 2021 e non riceve correzioni. Il suo compose di sviluppo viene rimosso. Prometheus e Grafana restano lo strumento di monitoraggio in sviluppo.

### 13. Sblocco immediato della CI

Le fasi maggiori richiedono settimane. Nel frattempo la fase 0 sblocca la pipeline senza spegnere il controllo:

- si applicano subito gli aggiornamenti patch e minor che correggono vulnerabilità senza cambiare API: pgjdbc, amqp-client, commons-beanutils, certifi, Pillow (dopo i test di accuratezza), le dipendenze transitive dei lockfile e i Dockerfile;
- le vulnerabilità che richiedono una maggiore vanno in `.trivyignore.yaml`, una per voce, con il motivo, la fase che la risolve e una scadenza `expired_at` di 60 giorni;
- alla scadenza la voce torna a far fallire la CI: o la fase è arrivata, o la scadenza si rinnova con una nuova motivazione nella pull request.

Le voci di `.trivyignore.yaml` vengono tolte dalla fase che risolve la vulnerabilità.

## Eccezioni alla versione più recente

| Componente | Versione scelta | Più recente | Motivo |
| --- | --- | --- | --- |
| TypeScript | 6.0 | 7.0 | Angular 22 richiede `>=6.0 <6.1`; `@astrojs/check` supporta fino alla 6 |
| Python e Ubuntu del servizio OMR | 3.12 / 24.04 | 3.15 / 26.04 | Audiveris non ha pacchetti per Ubuntu 26.04 |
| Pillow del servizio OMR | 12.3.0 | 13.0.0 | homr 0.7.0, ultima versione, richiede `Pillow<13`; le 28 CVE corrette solo dalla 13 restano in `.trivyignore.yaml` finché homr non accetta la 13 |
| Java del provider Keycloak | quella dell'immagine Keycloak | 25 | il provider gira nella JVM di Keycloak |
| Prometheus | 3.13 LTS | 3.15 | la 3.15 esce dal supporto il 06/11/2026; la LTS è supportata fino a luglio 2027 |
| Karma | 6.4 | — | deprecato senza successore diretto: decisione 6 |

## Fasi

Ogni fase è una pull request separata, di un solo modulo, come chiede `AGENTS.md`. L'ordine è quello consigliato. Le fasi 2–6 sono indipendenti fra loro e possono procedere in parallelo, anche in sessioni diverse.

| Fase | Contenuto | Moduli | Vulnerabilità risolte |
| --- | --- | --- | --- |
| 0 | Sblocco della CI (decisione 13) | tutti | patch e minor, Dockerfile |
| 1 | Azioni GitHub fissate per SHA, Trivy, gitleaks e semgrep aggiornati, Dependabot | repository | — |
| 2 | Servizio OMR: librerie Python | `taurus-omr` | 29 |
| 3 | Provider e immagine Keycloak 26.8 | `keycloak-authenticator`, compose | — |
| 4 | Backend: Java 25, Spring Boot 3.5 → 4.0 → 4.1, JHipster 9, librerie e plugin | `taurus-be` | 9 |
| 5 | Frontend: Node 26, Angular e PrimeNG 20 → 21 → 22, Tailwind 4, librerie | `taurus-fe` | 38 |
| 6 | Sito informativo: Node 26, pnpm 12, Astro 6 → 7 | `taurus-info` | 14 |
| 7 | Servizi: PostgreSQL 18, RabbitMQ 4.4, nginx senza root, monitoraggio | compose, `deploy/`, Dockerfile | 4 configurazioni |
| 8 | Documentazione e chiusura: runbook, `AGENTS.md`, README, `.trivyignore.yaml` vuoto | repository | — |

### Esito della fase 0

Applicata e verificata il 2026-11-06 sul ramo `update-versions`; i commit per modulo sono descritti in [Stato dei lavori](#stato-dei-lavori). Trivy 0.70.0, la versione della CI, trovava 90 vulnerabilità e 4 configurazioni: ne restano 45 e 3, tutte in `.trivyignore.yaml` con scadenza 2027-01-05. Il job `security` legge il file con l'input `trivyignores` di `trivy-action`, e il comando del [Runbook di test](runbook-test.md) usa la stessa versione di Trivy e lo stesso file.

- Backend: pgjdbc 42.7.14, amqp-client 5.37.1, commons-beanutils 1.11.0 e `--no-install-recommends` nel Dockerfile. Resta la CVE di Actuator, corretta da Spring Boot 3.5.12 (fase 4). Nei test, la cache dei contesti di Spring è limitata a 8: senza limite gli IT esaurivano l'heap, un difetto già presente prima della fase 0 (vedi [Memoria esaurita negli IT del backend](#memoria-esaurita-negli-it-del-backend)).
- Frontend: Angular 19.2.25 e strumenti di build 19.2.27, ultime della 19, che correggono 9 delle 18 segnalazioni di Angular; PrimeNG 19.1.4; postcss 8.5 e le dipendenze transitive del lockfile. Restano le altre 9 di Angular, corrette solo dalla 20.3, e quella di `ngx-extended-pdf-viewer` (fase 5).
- Sito informativo: devalue, js-yaml, source-map-js, svgo e vite alle versioni corrette. Restano Astro e sharp 0.34, l'ultima linea che Astro 5 accetta (fase 6).
- Servizio OMR: certifi 2026.10.23. Pillow resta alla 12.3.0 per il vincolo di homr (vedi [Eccezioni](#eccezioni-alla-versione-più-recente)).
- Dockerfile: restano le immagini nginx e il dev container che girano come root (fase 7).

Trivy risolve le dipendenze del `pom.xml` scaricando i POM da Maven Central e non sempre ci riesce: due esecuzioni sullo stesso file possono dare risultati diversi. Il confronto vale sul report completo, quello che elenca anche Spring Boot e il driver PostgreSQL.

### Stato dei lavori

Fase 0 verificata e committata il 2026-11-06 sul ramo `update-versions`, che parte da `84ae3b8`. I commit sono sei, uno per modulo e due per il backend:

- `Limit the cached test contexts`: il limite alla cache dei contesti di test del backend;
- `Update vulnerable backend dependencies`: `pom.xml` e `Dockerfile` del backend;
- `Update Angular and PrimeNG to their last 19.x releases`: `package.json` e lockfile del frontend;
- `Update vulnerable info site dependencies`: lockfile del sito informativo;
- `Update certifi in the OMR service`: i requisiti del servizio OMR;
- `Add expiring Trivy exceptions for the platform upgrade`: `.trivyignore.yaml`, il job `security` e la documentazione.

Il ramo non è ancora pubblicato.

#### Verifiche eseguite

| Modulo | Comando | Esito |
| --- | --- | --- |
| Repository | Trivy 0.70.0 come nella CI, con `--ignorefile .trivyignore.yaml` | nessuna segnalazione, codice di uscita 0 |
| Repository | `validate-feature-catalog.mjs`, `feature-catalog.test.mjs`, `check-feature-drift.mjs` | superati |
| Frontend | `npm run build` | riuscita; due avvisi di budget CSS: `pdf-manipulator-dialog.component.scss` 7,72 kB e `preview.component.scss` 7,95 kB, sul limite di 7 kB |
| Frontend | `npm run test:coverage` | 736 test su 736; copertura sopra la baseline (statements 64,46%, branches 54,29%, lines 66,53%) |
| Sito informativo | `pnpm check`, `pnpm build`, `pnpm test`, `pnpm test:image` | superati |
| Servizio OMR | `python -m pytest` in `python:3.12-slim` con `requirements-test.txt` | 71 superati, 6 saltati: i test di accuratezza, che chiedono un servizio con i motori reali |
| Backend | `./mvnw verify -Dtaurus.test.requireDocker=true`, con RabbitMQ irraggiungibile come in CI | superata in 26 minuti: 1225 test unitari e 279 di integrazione; copertura unita 75,59% delle linee e 60,53% dei branch, sopra le soglie |
| Servizio OMR | `docker build` e `tests/test_accuracy.py` sull'immagine costruita, come il job `omr` della CI | immagine costruita con certifi 2026.10.23; 6 test su 6 in 3 minuti: battute lette 87% con Audiveris e 82% con homr, nomi delle parti 6 su 6 |

Non eseguiti: `npm run e2e` e la verifica visiva di Angular 19.2 e PrimeNG 19.1.4. Entrambi chiedono di entrare con gli utenti dei tenant di prova, che il realm di sviluppo non contiene e che la suite legge dalle variabili `E2E_*`.

#### Memoria esaurita negli IT del backend

Risolto il 2026-11-06.

- **Sintomo.** Dopo `SignatureIT` il contesto di `AccountResourceIT` non partiva: l'heap di 1 GB del fork era pieno e, dopo 17-19 minuti di garbage collection continua, il fork moriva con codice 99. Gli IT successivi non giravano.
- **Causa.** Spring tiene in cache un contesto per ogni configurazione di test, fino a 32, e nel fork degli IT ne nascono 18 prima di `AccountResourceIT`, intorno ai 50 MB l'uno. Nell'istogramma dell'heap dominano le cache di ogni contesto: le corrispondenze dei pointcut AspectJ (un milione di `ShadowMatchImpl`), gli oggetti `Method` della riflessione, i `Lazy` di Spring Data. Le centinaia di thread dei listener RabbitMQ che riprovano a collegarsi sono un effetto dei contesti vivi, non la causa.
- **Non è una regressione della fase 0.** La stessa prova su `84ae3b8`, con RabbitMQ irraggiungibile come in CI, si è fermata nello stesso punto: heap pieno al 100%, 578 thread, contesto di `AccountResourceIT` non avviato dopo 10 minuti. La suite era già al limite: il 2026-11-06 alle 01:18, prima del commit `e6eff8d`, `AccountResourceIT` aveva impiegato 146 secondi.
- **Correzione.** `spring.test.context.cache.maxSize=8` in `taurus-be/src/test/resources/spring.properties`. Oltre il limite Spring chiude il contesto usato meno di recente, con i suoi pool e listener; il container PostgreSQL condiviso, registrato senza callback di distruzione, resta acceso. Con il limite ogni contesto parte in 10-13 secondi e `AccountResourceIT` dura 14 secondi. Il [Runbook di test](runbook-test.md) spiega il limite e come riconoscere un heap di nuovo al limite.

#### Passi per chiudere la fase 0

1. Eseguire `npm run e2e` su un'istanza completa con i tenant di prova e la verifica visiva sulle pagine del [Taurus Layout Standard](taurus-layout-standard.md).
2. Pubblicare il ramo e aprire la pull request: la CI verifica i moduli con Java 17, mentre in locale il backend è stato provato con Java 21.
3. Aggiornare questa sezione e la nota della voce in `docs/features.json`, poi passare alla fase 1.

#### Note sull'ambiente locale

- Il backend è stato verificato con Java 21.0.2; la CI usa Java 17. Con i servizi di sviluppo avviati, `SPRING_RABBITMQ_PORT=5679` punta i test su una porta chiusa, come in CI dove nessun broker ascolta su `localhost:5672`.
- Il frontend è stato verificato con Node 22.11.0, come la CI che usa Node 22. pnpm 11.16 non parte su Node 22.11.0 e il corepack di quella versione rifiuta le chiavi di firma di pnpm: per il sito è stato usato Node 24.21.0.
- Il lockfile del frontend è stato rigenerato così: `npm audit fix`, minimi del `package.json` alzati a mano, poi rimozione dal lock delle sole voci con nome esatto (`@angular/*`, `@angular-devkit/*`, `@ngtools/*`, `@schematics/angular`, `primeng`, `postcss`) e `npm install`. Una rimozione per prefisso toglie anche `postcss-modules-*`, che npm non ripristina: la build passa, ma Karma non trova `postcss-modules-values`. `npm ls --all` deve uscire senza errori.
- Il `node_modules` del sito era collegato allo store pnpm `D:\Daniele\Zodiac\Taurus\.pnpm-store`, che non esisteva più. Lo store è stato ricreato per l'aggiornamento e poi cancellato: se pnpm segnala uno store inatteso, `pnpm install` ricollega le dipendenze.
- L'enforcer del backend segnala due divergenze di dipendenze, solo come avvisi: amqp-client, 5.21.0 da `spring-rabbit` contro quella dichiarata, già presente con la 5.25.0, e `checker-qual`, 3.55.1 da pgjdbc 42.7.14 contro 3.37.0 da Caffeine, nuova.

## Modifiche per modulo

### Repository

- `.github/workflows/*.yml`: azioni alle nuove versioni maggiori, fissate per SHA; Node 26 e Java 25 nei job; Trivy con `version` esplicita; gitleaks e semgrep aggiornati.
- `.github/dependabot.yml`: nuovo (decisione 7).
- `.trivyignore.yaml`: nuovo nella fase 0, vuoto alla fine della fase 8.
- `AGENTS.md` e `README.md`: Java 25, Node.js 26, pnpm 12, Angular 22.

### Backend

- `pom.xml`: versioni del parent, delle proprietà e dei plugin; `java.version` 25; rimozione di Spring Cloud e Undertow; starter rinominati; immagine di Jib.
- Codice: Jackson 3, Hibernate 7, Spring Security 7, Testcontainers 2 e le API rimosse in Spring Boot 4, guidate dalle note di migrazione ufficiali e da OpenRewrite dove disponibile.
- `Dockerfile`: immagini Temurin 25 e `--no-install-recommends`.
- Configurazione: proprietà rinominate da Spring Boot 4, segnalate all'avvio da `spring-boot-properties-migrator`, che si toglie a fine fase.

### Frontend

- `package.json` e lockfile dopo ogni `ng update`; builder `@angular/build`; `@primeuix/themes`; Tailwind 4 con lo strumento di migrazione ufficiale.
- Componenti: le modifiche richieste da PrimeNG 20–22, verificate pagina per pagina sulle schermate del [Taurus Layout Standard](taurus-layout-standard.md).
- `Dockerfile`: `node:26-alpine` e nginx senza root sulla porta 8080.
- `docs/llms-full.md` rigenerato per PrimeNG 22.

### Sito informativo

- `package.json`, `packageManager` pnpm 12, lockfile; adeguamenti di Astro 6 e 7; `Dockerfile` come il frontend.

### Servizio OMR

- `requirements.txt`, `requirements-test.txt`, `requirements-accuracy.txt` aggiornati insieme, come chiede l'intestazione del file; `models.sha256` invariato salvo nuovi modelli di homr.

### Provider Keycloak

- `pom.xml`: Keycloak 26.8, FreeMarker del runtime, `--release` della JVM di Keycloak; temi verificati sulle pagine di login, registrazione ed e-mail.

### Servizi

- `deploy/compose.yml`, `taurus-be/src/main/docker/*.yml`: nuove immagini, volume di PostgreSQL, porte di nginx; rimozione di `jhipster-control-center.yml`.
- `deploy/reverse-proxy.example.conf`: la porta pubblicata resta la stessa, cambia solo quella interna del container.

## Autorizzazioni e isolamento tenant

Nessun cambio di regole. Il passaggio a Spring Security 7 e a Hibernate 7 tocca però i punti in cui le regole sono applicate: la configurazione della sicurezza e il cambio di schema per tenant di [PostgreSQL schema per tenant](postgres-tenant-schemas.md). I test di autorizzazione e di isolamento tenant devono passare senza essere modificati. Se un test va cambiato, la modifica si motiva nella pull request.

## Osservabilità e sicurezza

- Actuator: la correzione di CVE-2026-22733 arriva con Spring Boot 4.1. Gli endpoint esposti si ricontrollano con la configurazione di produzione.
- Le metriche Prometheus e i nomi esposti da Micrometer possono cambiare con Spring Boot 4: i pannelli Grafana di `taurus-be/src/main/docker/grafana` si verificano.
- Il job `security` resta bloccante per vulnerabilità `HIGH` e `CRITICAL` corrette, configurazioni e segreti.
- Ogni immagine Docker pubblicata viene ricontrollata con Trivy dopo la fase che la cambia.

## Licenze

| Componente | Licenza | Nota |
| --- | --- | --- |
| Liquibase 5 Community | Functional Source License | dalla 5 non è più Apache-2.0: l'uso interno è gratuito, ma va verificato con il vincolo di costo zero e annotato in `THIRD_PARTY_NOTICES.md`. Se non è accettabile, si resta sull'ultima 4.x, fuori supporto dal 30/09/2025 |
| Tutti gli altri componenti | invariata | da ricontrollare a ogni maggiore in `THIRD_PARTY_NOTICES.md` |

## Strategia di test

Ogni fase esegue le verifiche complete del suo modulo, come in `AGENTS.md`:

- backend: `./mvnw verify -Dtaurus.test.requireDocker=true` con le soglie JaCoCo attuali, che non si abbassano;
- frontend: `npm run build`, `npm run test:coverage` e `npm run e2e` su un'istanza completa;
- sito informativo: `pnpm check`, `pnpm build`, `pnpm test` e `pnpm test:image`;
- servizio OMR: `python -m pytest` e i test di accuratezza sull'immagine nuova;
- provider Keycloak: `./mvnw verify` e il login sull'immagine di Keycloak 26.8;
- servizi: avvio di `services.yml` e del compose di produzione con `scripts/ops/validate-deploy-manifest.sh`.

### Verifica manuale

- Frontend: le pagine principali in chiaro e scuro, desktop e mobile; dialoghi, tabelle, calendario, lettore PDF e lettore degli spartiti.
- Aggiornamento di un database di prova da PostgreSQL 17 a 18 con la procedura del runbook, e ritorno indietro.
- Login, registrazione, recupero della password ed e-mail di Keycloak con il tema Taurus.
- Riconoscimento di una parte e lettura del testo con il servizio OMR aggiornato.

## Criteri di accettazione

- Ogni componente dell'inventario è all'ultima versione stabile del momento dell'implementazione o è elencato in [Eccezioni](#eccezioni-alla-versione-più-recente).
- Nessun componente in uso è fuori supporto.
- Il job `security` passa con `.trivyignore.yaml` vuoto.
- Le verifiche di tutti i moduli e la suite end-to-end passano; le soglie di copertura non scendono.
- Tutte le azioni GitHub sono fissate per SHA del commit e Dependabot apre le pull request settimanali.
- Frontend e sito informativo girano come utente non root.
- Il runbook operativo descrive l'aggiornamento di PostgreSQL, RabbitMQ e Keycloak e il ritorno indietro.

## Piano di adozione e compatibilità

- Ogni fase si rilascia da sola. Per la fase 7 le installazioni esistenti devono seguire la procedura del runbook: PostgreSQL non si aggiorna cambiando solo il tag dell'immagine.
- Backend e frontend non cambiano API fra loro: possono essere rilasciati in fasi diverse.
- Il frontend aggiornato richiede browser recenti: Angular 22 supporta le ultime due versioni dei browser principali.
- Le variabili d'ambiente del compose restano le stesse. Cambiano solo i valori predefiniti delle versioni (`TAURUS_POSTGRES_VERSION`, `TAURUS_KEYCLOAK_VERSION`, `TAURUS_RABBITMQ_VERSION`). Chi le ha fissate nel proprio `.env` le aggiorna seguendo il runbook.
- Nessuna migrazione Liquibase prevista. Se Hibernate 7 cambia il DDL atteso, la differenza si corregge con una nuova migrazione e non modificando quelle esistenti.

## Rischi

- **Volume del lavoro nel frontend.** Tre maggiori di Angular e PrimeNG su 117 componenti. Mitigazione: un passo alla volta con `ng update`, test a ogni passo e verifica visiva con la suite end-to-end.
- **Jackson 3 nel backend.** Serializzazione di date, enum e valori nulli può cambiare in modo silenzioso. Mitigazione: test delle API REST e controllo delle risposte JSON prima e dopo.
- **Hibernate 7 e schema per tenant.** Il cambio di schema dipende da API interne di Hibernate. Mitigazione: test di isolamento tenant e prova con più tenant reali.
- **Dati di PostgreSQL.** Un errore nella migrazione dei dati è il rischio più grave. Mitigazione: backup verificato prima, prova su una copia, procedura di ritorno indietro.
- **Licenza di Liquibase 5.** Mitigazione: verifica prima della fase 4, con l'ultima 4.x come ripiego.
- **Eccezioni che diventano permanenti.** Mitigazione: scadenza obbligatoria in `.trivyignore.yaml`.
- **Modifiche in corso su altri rami.** Una fase lunga entra in conflitto con le funzionalità sviluppate nel frattempo. Mitigazione: fasi piccole, rilasciate appena pronte, e Dependabot per non accumulare di nuovo ritardo.
