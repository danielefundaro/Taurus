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
| docker/build-push-action, login, metadata, setup-buildx | v5, v3, v5, v3 | v7, v4, v6, v4 | un riferimento a login era alla v1.9.0, trovato nella fase 1 |
| sigstore/cosign-installer | v3.5.0 | v4.x | con la v4 si installa cosign 3 |
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
| opencv-python-headless del servizio OMR | 4.14.0.94 | 5.0.0.93 | homr 0.7.0 richiede `<5`: decisione 9 |
| musicxml del servizio OMR | 1.4 | 1.6.3 | homr 0.7.0 richiede `==1.4`: decisione 9 |
| Java del provider Keycloak | 21, quella dell'immagine di Keycloak 26.8.2 | 25 | il provider gira nella JVM di Keycloak |
| Prometheus | 3.13 LTS | 3.15 | la 3.15 esce dal supporto il 06/11/2026; la LTS è supportata fino a luglio 2027 |
| Karma | 6.4 | — | deprecato senza successore diretto: decisione 6 |

## Fasi

Ogni fase è una pull request separata, di un solo modulo, come chiede `AGENTS.md`. L'ordine è quello consigliato. Le fasi 2–6 sono indipendenti fra loro e possono procedere in parallelo, anche in sessioni diverse.

| Fase | Contenuto | Moduli | Vulnerabilità risolte |
| --- | --- | --- | --- |
| 0 | Sblocco della CI (decisione 13) | tutti | patch e minor, Dockerfile |
| 1 | Azioni GitHub alle nuove versioni maggiori, già fissate per SHA dalla fase 0; Trivy, gitleaks e semgrep aggiornati, Dependabot | repository | — |
| 2 | Servizio OMR: librerie Python | `taurus-omr` | 29 |
| 3 | Provider e immagine Keycloak 26.8 | `keycloak-authenticator`, compose | 3, emerse nella fase 1 |
| 4 | Backend: Java 25, Spring Boot 3.5 → 4.0 → 4.1, JHipster 9, librerie e plugin | `taurus-be` | 9, più 75 emerse nella fase 1 |
| 5 | Frontend: Node 26, Angular e PrimeNG 20 → 21 → 22, Tailwind 4, librerie | `taurus-fe` | 38 |
| 6 | Sito informativo: Node 26, pnpm 12, Astro 6 → 7 | `taurus-info` | 14 |
| 7 | Servizi: PostgreSQL 18, RabbitMQ 4.4, nginx senza root, monitoraggio | compose, `deploy/`, Dockerfile | 4 configurazioni |
| 8 | Documentazione e chiusura: runbook, `AGENTS.md`, README, `.trivyignore.yaml` vuoto | repository | — |

### Esito della fase 0

Applicata e verificata il 2026-11-06 sul ramo `update-versions`; i commit per modulo sono descritti in [Stato dei lavori](#stato-dei-lavori). Trivy 0.70.0, la versione della CI, trovava 90 vulnerabilità e 4 configurazioni: ne restano 45 e 3, tutte in `.trivyignore.yaml` con scadenza 2027-01-05. Il job `security` legge il file con l'input `trivyignores` di `trivy-action`, e il comando del [Runbook di test](runbook-test.md) usa la stessa versione di Trivy e lo stesso file.

- Backend: pgjdbc 42.7.14, amqp-client 5.37.1, commons-beanutils 1.11.0 e `--no-install-recommends` nel Dockerfile. Resta la CVE di Actuator, corretta da Spring Boot 3.5.12 (fase 4). Nei test, la cache dei contesti di Spring è limitata a 8: senza limite gli IT esaurivano l'heap, un difetto già presente prima della fase 0 (vedi [Memoria esaurita negli IT del backend](#memoria-esaurita-negli-it-del-backend)).
- Frontend: Angular 19.2.25 e strumenti di build 19.2.27, ultime della 19, che correggono 9 delle 18 segnalazioni di Angular; PrimeNG e `@primeng/themes` 19.1.4; postcss 8.5 e le dipendenze transitive del lockfile. Restano le altre 9 di Angular, corrette solo dalla 20.3, e quella di `ngx-extended-pdf-viewer` (fase 5). Il lockfile porta anche Playwright 1.65 ed ESLint 9.39, dentro gli intervalli del `package.json`: i browser di Playwright vanno reinstallati. In PrimeNG 19.1 ToggleButton ha perso il ruolo di pulsante, che `ToggleButtonRoleDirective` gli restituisce (vedi [Suite end to end](#suite-end-to-end)).
- Sito informativo: devalue, js-yaml, source-map-js, svgo e vite alle versioni corrette. Restano Astro e sharp 0.34, l'ultima linea che Astro 5 accetta (fase 6).
- Servizio OMR: certifi 2026.10.23. Pillow resta alla 12.3.0 per il vincolo di homr (vedi [Eccezioni](#eccezioni-alla-versione-più-recente)).
- Dockerfile: restano le immagini nginx e il dev container che girano come root (fase 7).
- CI: le sette azioni GitHub che usavano un tag (`actions/checkout`, `setup-node`, `setup-java`, `setup-python`, `upload-artifact`, `attest-build-provenance` e `pnpm/action-setup`, 29 riferimenti) sono fissate per SHA del commit a cui portava il loro tag maggiore, con la versione in commento: nessuna versione cambia. Le segnalava semgrep (vedi [Stato dei lavori](#stato-dei-lavori)).

Trivy risolve le dipendenze del `pom.xml` scaricando i POM da Maven Central e non sempre ci riesce: due esecuzioni sullo stesso file possono dare risultati diversi. Il confronto vale sul report completo, quello che elenca anche Spring Boot e il driver PostgreSQL. Nemmeno quel report era completo: vedi [Esito della fase 1](#esito-della-fase-1).

### Esito della fase 1

Applicata e verificata il 2026-11-07 sul ramo `upgrade-phase-1`, che parte da `6cb19fd`.

- **Azioni GitHub.** Ogni azione è all'ultima release della sua versione maggiore più recente, fissata per SHA del commit con la versione in commento: `actions/checkout` 7.0.1, `setup-node` 7.1.0, `setup-java` 6.2.0, `setup-python` 7.1.0, `upload-artifact` 7.0.2, `attest-build-provenance` 4.2.2, `pnpm/action-setup` 6.1.1, `docker/setup-buildx-action` 4.4.1, `login-action` 4.8.0, `metadata-action` 6.2.0, `build-push-action` 7.4.0 e `sigstore/cosign-installer` 4.1.3. `trivy-action` era già all'ultima, la 0.36.0. Gli SHA vengono da `git ls-remote` sui tag, con i tag annotati risolti al loro commit. In `docker-publish.yml` un riferimento a `login-action` era ancora alla 1.9.0, senza commento.
- **Cambi di comportamento.** Le note di rilascio non chiedono modifiche ai workflow. Le azioni girano su Node 24, che chiede un runner 2.327.1 o successivo, come quelli di GitHub. `setup-node` dalla v5 attiva da sé la cache quando `package.json` dichiara `packageManager`, e dalla v6 solo per npm: i job del frontend e del sito la configurano già, gli altri due non hanno un `package.json`. `checkout` 7 rifiuta il checkout delle pull request da fork negli eventi `pull_request_target`, che nessun workflow usa. `attest-build-provenance` 4 si appoggia ad `actions/attest` e accetta lo stesso `subject-path`.
- **Firme delle immagini.** `cosign-installer` 4 installa cosign 3, oggi la 3.1.4. `docker-publish.yml` non fissa più `cosign-release: v2.2.4`, così cosign segue l'installer, che Dependabot aggiorna. Il comando di firma, `cosign sign --yes` sull'immagine per digest, non cambia; cosign 3 però salva la firma come referrer OCI 1.1, nel formato bundle nuovo, e chi verifica le immagini deve usare cosign 3. Nessuna procedura del progetto le verifica. `docker-publish.yml` gira solo ai tag `v*.*.*`, e `cie-trust-anchors.yml` una volta al mese o a mano: le loro prime esecuzioni con le azioni nuove saranno il prossimo rilascio e il prossimo aggiornamento delle ancore. Finora li ha controllati solo actionlint.
- **Strumenti di sicurezza.** Trivy 0.76.2, ora esplicita nell'input `version` di `trivy-action`; gitleaks 8.30.1 e semgrep 1.183.0, fissati per tag e digest. Per semgrep è la versione del tag `latest` su Docker Hub: la 1.184.0 è ancora solo nel canale canary. Il [Runbook di test](runbook-test.md), sezione 8.7, usa le stesse versioni. Dependabot non vede le immagini dentro `run` né l'input `version`: le tre versioni si aggiornano a mano, insieme al runbook.
- **Maven nel job `security`.** Dalla 0.71 Trivy si ferma con un errore quando Maven Central risponde 429, come è successo nelle prove locali; prima saltava in silenzio le dipendenze che non riusciva a risolvere. Trivy cerca i POM prima nel repository Maven locale, quindi il job installa Java 17 con la cache Maven del job `backend` ed esegue `dependency:resolve` nei due moduli Java prima della scansione.
- **Vulnerabilità emerse.** Con tutte le dipendenze risolte Trivy trova 75 vulnerabilità nel `pom.xml` del backend, con 72 identificativi, e 3 in quello del provider. Trivy 0.70.0 sullo stesso repository locale trova le stesse: nella fase 0 le dipendenze transitive degli starter di Spring Boot restavano fuori dalla scansione senza errori. Nel backend sono Tomcat 10.1.31 (24), Jackson 2.17.2, Netty 4.1.114, Spring Framework 6.1.14, Spring Security 6.3.4, Spring Boot e Spring Data 3.3.5, Micrometer 1.13.6 e Thymeleaf 3.1.2, corrette da Spring Boot 3.5 e 4 (fase 4); json-smart 2.5.1, che arriva solo nei test con `spring-boot-starter-test`; async-http-client 2.10.4 e jose4j 0.7.0, che arrivano con `nl.martijndwars:web-push` 5.1.1, ultima versione della libreria: la fase 4 deve forzarle a una versione corretta o sostituire la libreria. Nel provider sono Spring Boot 4.0.2 e Spring Framework 7.0.3, da `spring-boot-starter` 4.0.2 del suo `pom.xml` (fase 3). Sono tutte in `.trivyignore.yaml`, una voce per identificativo con la fase che la risolve e scadenza 2027-01-06, come chiede la decisione 13.
- **gitleaks 8.30.** La regola `generic-api-key` dà 5 falsi positivi in due test del backend: prende per valore la parola dopo un identificatore che contiene «key», come nel record `Issued(PrivateKey key, …)` di `CieSignTestSupport` e nei filtri `"key.contains=" + DEFAULT_KEY` di `PreferencesResourceIT`. I due file sono nell'allowlist di `.gitleaks.toml`.
- **Dependabot.** `.github/dependabot.yml` ha 13 blocchi: le azioni GitHub; Maven, npm e i Dockerfile dell'immagine e del dev container del backend; Maven del provider; npm e Dockerfile del frontend; npm, che legge anche `pnpm-lock.yaml`, Dockerfile e compose del sito; pip e Dockerfile del servizio OMR; il compose di `deploy/`. Ogni blocco gira il lunedì alle 6 (Europe/Rome) e raggruppa patch e minor in una pull request; ogni maggiore arriva in una pull request a sé. `cooldown` aspetta 7 giorni dalla pubblicazione di una versione: è il minimo della regola `dependabot-missing-cooldown` del set `p/ci` di semgrep, che con 3 giorni dava 13 segnalazioni, una per blocco. Gli aggiornamenti di sicurezza non aspettano. I compose di sviluppo del backend, in `src/main/docker`, hanno nomi che Dependabot non riconosce come compose: le loro immagini restano alla fase 7 e agli aggiornamenti a mano.
- **Prima esecuzione di Dependabot.** Al merge Dependabot ha girato subito in tutti i blocchi e ha aperto 26 pull request, dalla [#121](https://github.com/danielefundaro/Taurus/pull/121) alla [#146](https://github.com/danielefundaro/Taurus/pull/146): quasi tutte sono le maggiori delle fasi 2–6. Cinque proponevano versioni che la specifica esclude: Node 25, che non è LTS, nel frontend e nel sito ([#125](https://github.com/danielefundaro/Taurus/pull/125), [#124](https://github.com/danielefundaro/Taurus/pull/124)); Maven con Temurin 26 nel backend ([#121](https://github.com/danielefundaro/Taurus/pull/121)); Ubuntu 26.04 nel servizio OMR ([#122](https://github.com/danielefundaro/Taurus/pull/122)); Pillow 13 ([#145](https://github.com/danielefundaro/Taurus/pull/145)). Il gruppo di patch e minor dell'OMR ([#138](https://github.com/danielefundaro/Taurus/pull/138)) porta anche musicxml dalla 1.4 alla 1.6.3, che homr 0.7.0 fissa a `==1.4`. Le cause sono due. Per le immagini Docker `cooldown` conta dall'ultimo push del tag su Docker Hub: `node:26-alpine`, ripubblicato il 5 novembre, era ancora in attesa, mentre `node:25-alpine`, fermo da aprile, è passato. Nel tag `maven:3.9.6-eclipse-temurin-17` Dependabot legge il numero di Java come parte della versione, quindi sceglie sempre la Java più alta. La [#123](https://github.com/danielefundaro/Taurus/pull/123), Temurin 25 per l'immagine del backend, è corretta ma appartiene alla fase 4. music21 non dipende da homr: la 10 ([#140](https://github.com/danielefundaro/Taurus/pull/140)) resta un obiettivo della fase 2.
- **Esclusioni di Dependabot.** Dopo la prima esecuzione, il ramo `fix-dependabot-rules` aggiunge a `.github/dependabot.yml` regole `ignore` per le versioni che la specifica esclude. Nel backend: solo Java LTS per `eclipse-temurin`, dalla 26 alla 28 escluse, e nessun aggiornamento automatico dell'immagine `maven`. Nel frontend e nel sito: Node 25; le minor dispari di nginx, cioè la linea mainline, anche per `nginxinc/nginx-unprivileged` della decisione 8; TypeScript dalla 6.1 nel frontend, perché Angular 22 chiede `<6.1`, e dalla 7 nel sito. Nel servizio OMR: Ubuntu dopo la 24.04, comprese le versioni intermedie non LTS; i limiti che homr 0.7.0 dichiara su nove librerie fissate nei requisiti (Pillow, opencv-python-headless, musicxml, numpy, onnxruntime, rapidocr, requests, typing-extensions, types-Pillow), che Dependabot non ricava da solo. Nel frontend un gruppo raccoglie in una pull request le maggiori di Angular, dei suoi strumenti, di PrimeNG e di keycloak-angular, che cambiano maggiore insieme con `ng update`: la prima esecuzione ne aveva aperte quattro, una per pacchetto Angular ([#133](https://github.com/danielefundaro/Taurus/pull/133)–[#136](https://github.com/danielefundaro/Taurus/pull/136)). Secondo `ignore_condition.rb` di dependabot-core, un'esclusione con le versioni vale anche per gli aggiornamenti di sicurezza; quella di `maven`, senza versioni, solo per gli aggiornamenti di versione.
- **Seconda esecuzione di Dependabot.** Il merge `cf20bf0` della [#147](https://github.com/danielefundaro/Taurus/pull/147) ha fatto girare di nuovo Dependabot, che ha chiuso da solo le sei pull request escluse: #121, #122, #124, #125, #145 e #138, sostituita dalla [#150](https://github.com/danielefundaro/Taurus/pull/150) senza musicxml né Pillow. Restavano tre problemi. Il primo è Node 23 nel frontend e nel sito ([#149](https://github.com/danielefundaro/Taurus/pull/149), [#148](https://github.com/danielefundaro/Taurus/pull/148)): `node:23-alpine` è fermo dal 2025-05-31 e supera `cooldown`, mentre `24-alpine` e `26-alpine`, ripubblicati il 2026-11-05, sono ancora in attesa. Il secondo è il gruppo delle maggiori di Angular, che non ha aperto nessuna pull request: le quattro singole ([#133](https://github.com/danielefundaro/Taurus/pull/133)–[#136](https://github.com/danielefundaro/Taurus/pull/136)) restavano aperte, come Astro 7 nel sito ([#131](https://github.com/danielefundaro/Taurus/pull/131)). Sono salti di più maggiori, che la decisione 2 esclude. Il terzo sono le versioni che funzionano solo con la maggiore successiva del framework: zone.js 0.16 nel gruppo del frontend ([#132](https://github.com/danielefundaro/Taurus/pull/132)), con cui `npm ci` si ferma perché Angular 19 chiede `~0.15.0`, e springdoc-openapi 3 nel backend ([#139](https://github.com/danielefundaro/Taurus/pull/139)), la linea per Spring Boot 4. Entrambe hanno la CI rossa.
- **Maggiori a mano.** Il ramo `fix-dependabot-majors` toglie il gruppo delle maggiori di Angular. Come chiede la decisione 2, ignora le maggiori di Angular, dei suoi strumenti, di PrimeNG e di keycloak-angular nel frontend, di Astro e delle integrazioni `@astrojs/*` nel sito e di Spring Boot nel backend; minor e patch restano nei gruppi. Ignora anche le maggiori di springdoc-openapi e, nel frontend, le minor e le maggiori di TypeScript, zone.js e tailwindcss-primeui, che seguono Angular e PrimeNG: TypeScript non segue semver e nelle versioni 0.x la minor è una maggiore. La regola su TypeScript sostituisce quella dalla 6.1. Per Node esclude dalla 23 alla 25 e la 27: restano le LTS pari dalla 26. Secondo `ignore_condition.rb`, le regole con `update-types` valgono solo per gli aggiornamenti di versione, non per quelli di sicurezza. La [#137](https://github.com/danielefundaro/Taurus/pull/137), il gruppo Maven del backend, si ferma in `./mvnw verify`: il log richiede l'accesso al repository, e il suo contenuto, Maven 3.10 e i plugin, appartiene alla fase 4.
- **Terza esecuzione di Dependabot.** Il merge `9db617b` della [#151](https://github.com/danielefundaro/Taurus/pull/151) ha fatto chiudere a Dependabot #131, #132, #133–#136, #139, #148 e #149. Ha chiuso anche la #137, sostituita dalla [#157](https://github.com/danielefundaro/Taurus/pull/157): con le maggiori di Spring Boot ignorate, il gruppo Maven del backend contiene ora Spring Boot 3.5.16, il primo passo della decisione 2, e springdoc-openapi 2.10.0. La sua CI si ferma in `./mvnw verify` e nella scansione di Trivy: è lavoro della fase 4. Nel frontend sono arrivate cinque pull request. Il gruppo di patch e minor ([#152](https://github.com/danielefundaro/Taurus/pull/152)), con zone.js 0.15.1, supera `npm ci` e si ferma in `npm run test:coverage`. Tailwind 4 ([#153](https://github.com/danielefundaro/Taurus/pull/153)) si ferma allo stesso passo ed è una maggiore della fase 5. eslint-config-prettier 10 ([#154](https://github.com/danielefundaro/Taurus/pull/154)) e primeicons 8 ([#155](https://github.com/danielefundaro/Taurus/pull/155)) hanno la CI verde. jasmine-core 7 ([#156](https://github.com/danielefundaro/Taurus/pull/156)) si ferma in `npm ci`, perché `karma-jasmine-html-reporter` 2.1 accetta Jasmine fino alla 5; la 2.3 della #152 arriva alla 7. Probabilmente queste maggiori non comparivano prima perché il frontend era già al limite predefinito di cinque pull request aperte per blocco, con la #132 e le quattro di Angular.

### Esito della fase 2

Applicata e verificata il 2026-11-07 sul ramo `upgrade-phase-2`, che parte da `9db617b`.

- **Motori.** homr 0.7.0, RapidOCR 3.10.0 e Audiveris 5.11.0 sono ancora le ultime versioni, quindi valgono gli stessi limiti di homr. `homr --init` scarica gli stessi modelli e `models.sha256` non cambia.
- **Versioni.** I tre file dei requisiti sono rigenerati insieme in Python 3.12 da `pip freeze`, partendo dalle dipendenze di `pyproject.toml`, da homr 0.7.0, da pytest e httpx per i test e dagli strumenti di rendering del corpus. Ogni libreria è all'ultima versione che homr accetta e che è pubblicata da almeno sette giorni, come chiede `cooldown` per Dependabot (decisione 7); l'opzione `--uploaded-prior-to 2026-10-31T18:00:00Z` di pip esclude le più recenti. Nel servizio: music21 da 9.7.1 a 10.5.2, FastAPI da 0.143.3 a 0.162.0, Starlette da 1.8.0 a 1.10.0, Pydantic 2.14.2 con pydantic-core 2.50.2, python-multipart 0.0.33, ONNX Runtime 1.31.1, python-dateutil 3.0.0 con la nuova dipendenza `tzdata` 2026.6, chardet 7.7.0, fonttools 4.68.0 e opentelemetry-api 1.45.2. Nei test, pytest 9.2.0; nel rendering del corpus, verovio 6.4.0, brotli 1.2.1 e pycparser 3.12. setuptools sale alla 84.2.1 in `pyproject.toml`. Sono le versioni delle pull request di Dependabot [#150](https://github.com/danielefundaro/Taurus/pull/150), [#140](https://github.com/danielefundaro/Taurus/pull/140), [#142](https://github.com/danielefundaro/Taurus/pull/142) e [#144](https://github.com/danielefundaro/Taurus/pull/144). pip, che Dependabot non vede dentro `RUN`, sale alla 26.2.2 nel Dockerfile. Restano fuori, perché pubblicate da meno di sette giorni, FastAPI 0.162.2, Pydantic 2.14.3, python-dateutil 4.0.0, anyio 4.16.0, click 8.5.1, typing-inspection 0.4.5, verovio 6.4.1 e pip 26.3: arriveranno con Dependabot.
- **Limiti di homr.** Pillow resta alla 12.3.0, opencv-python-headless alla 4.14.0.94 e musicxml alla 1.4, tutte elencate in [Eccezioni](#eccezioni-alla-versione-più-recente). Delle 29 vulnerabilità del servizio, certifi è stata corretta nella fase 0, mentre le 28 di Pillow restano in `.trivyignore.yaml` finché homr non accetta Pillow 13: la fase 2 non ne risolve altre. music21 non è una dipendenza di homr, quindi sale alla 10. La conversione MIDI e i suoi test non cambiano, e `/v1/health` riporta music21 10.5.2.
- **Accuratezza.** Sull'immagine nuova, con le restrizioni di `omr.yml`, i test di accuratezza danno gli stessi risultati di prima: 132 battute su 151 corrette con Audiveris (87%) e 124 con homr (82%), segni di struttura 15 e 6 su 26, nomi delle parti 6 su 6.

### Esito della fase 3

Applicata e verificata il 2026-11-07 sul ramo `upgrade-phase-3`, che parte da `bb1c2f4`.

- **Versioni.** Keycloak 26.8.2 nelle SPI del provider, nei compose di sviluppo e di produzione, in `deploy/.env.example` e nei Testcontainers dei tre IT del provider e dei due IT del backend che usano Keycloak. È uscita il 2026-11-05, quindi da meno di sette giorni, ma è un rilascio di sicurezza, che per la decisione 7 non aspetta: corregge, fra le altre, una XSS memorizzata nella pagina di accesso e un furto di account. Nel provider salgono anche il parent Spring Boot alla 4.1.2, FreeMarker alla 2.3.35, la versione che Keycloak 26.8.2 porta a runtime e che `EmailTemplatesTest` usa per rendere i modelli, il wrapper Maven alla 3.10.0 e i plugin compiler 3.16.0, jar 3.5.1 e resources 3.5.0. Sono le versioni della [#129](https://github.com/danielefundaro/Taurus/pull/129) di Dependabot, che proponeva Keycloak 26.8.1.
- **Java.** L'immagine di Keycloak 26.8.2 gira su OpenJDK 21.0.13: il provider si compila con `release` 21 al posto di `source` e `target` 17, come chiede la decisione 3, e le classi del JAR sono nella versione 65. Serve un JDK 21, quindi i job `keycloak` di `verify.yml` e `publish-provider` di `docker-publish.yml` installano Java 21; `backend` e `security` restano su Java 17, che basta a `dependency:resolve`. Il compilatore di Maven non ricompila quando cambia solo `release`: dopo l'aggiornamento serve `clean verify`, come già indica il [Runbook di test](runbook-test.md).
- **Vulnerabilità.** Spring Boot 4.1.2 corregge le tre vulnerabilità del provider emerse nella fase 1, CVE-2026-41850 di spring-expression 7.0.3 e CVE-2026-40973 e CVE-2026-40976 di spring-boot 4.0.2: le loro voci escono da `.trivyignore.yaml`. Le voci con gli stessi identificativi per il backend restano alla fase 4.
- **Tema.** I modelli `keycloak.v2` e `base` di 26.6.4 e 26.8.2, estratti dalle immagini, cambiano poco per il tema Taurus. La macro `registrationLayout` ha gli stessi parametri e le stesse sezioni del nostro `template.ftl`. Nei modelli che il tema sostituisce cambia solo la macro `buttons`, che i nostri non usano. `login-update-profile.ftl` e `login-verify-email.ftl` sono nuovi in `keycloak.v2`, prima arrivavano da `base`, e funzionano con il nostro layout. Fra i messaggi italiani c'è una chiave nuova; i modelli delle e-mail che Taurus usa sono identici. La versione nuova non chiede modifiche al tema; quelle della fase correggono difetti già presenti.
- **Prova a mano.** Su un Keycloak 26.8.2 usa e getta, con il JAR della build, il realm di Taurus e Mailpit, nel browser integrato: accesso con nome utente e con e-mail; scelta di tenant e ruolo con due tenant e due ruoli, scelta automatica con un tenant e un ruolo; configurazione dell'app di autenticazione e accesso con il codice, con l'errore su un codice sbagliato; recupero della password dall'e-mail fino alla password nuova, con l'errore su due password diverse; invito con l'e-mail di configurazione dell'account e scelta della password; pagine di aggiornamento del profilo e di verifica dell'e-mail; prova SMTP. Le e-mail hanno oggetto italiano, mittente «Taurus», logo e colori dello [standard delle e-mail](email-layout-standard.md). La registrazione, spenta nel realm, è stata accesa solo nel realm di prova per vedere la pagina. Nel log di Keycloak nessun errore dei modelli; gli avvisi `KC-SERVICES0047` sulle SPI interne compaiono anche nel Keycloak 26.6.4 di sviluppo.
- **Difetti già presenti, corretti.** La prova a mano li ha trovati, ma i modelli e i messaggi coinvolti sono identici in 26.6.4: non sono regressioni. Nella configurazione dell'app di autenticazione i nomi delle app comparivano come chiavi, `totpAppGoogleName` e le altre, perché `login-config-totp.ftl` stampava `${app}` dove il tema base usa `${msg(app)}`. Nella stessa pagina «Non riesci a scansionare il codice QR?» compariva due volte, come testo e come link, e senza script il link riapriva la pagina del codice QR. Ora compare una volta e porta a `totp.manualUrl`: la stessa pagina in modo manuale, con il codice da inserire già in vista e il modo conservato nel form. Nel recupero della password il ritorno all'accesso si leggeva «← « Torna al Login», perché il tema aggiungeva una freccia al messaggio, che ha già «: la freccia del tema è tolta. Le pagine che vengono da `base` e `keycloak.v2`, cioè conferma dell'uscita, informazione, errore, pagina scaduta, aggiornamento del profilo, verifica dell'e-mail e registrazione, usano classi di PatternFly 5 che `login.css` non definiva: campi e pulsanti avevano lo stile predefinito del browser e il titolo era testo semplice. Ora `login.css` dà a titolo, testi, campi, messaggi di errore e pulsanti di quelle pagine la forma delle pagine Taurus, senza caricare PatternFly; il pulsante per mostrare la password delle pagine `keycloak.v2`, che dipende da uno script che il tema non carica, è nascosto. Nei messaggi italiani del tema tornano gli accenti di «È necessario» e «non è valido». Due contrasti, visti nella stessa prova, restavano sotto il 4,5:1 di WCAG 2.1 AA: il testo degli avvisi gialli, 3,1:1 sul loro fondo, e il rosso dei messaggi di errore e degli asterischi, 3,8:1 sul bianco. Passano a 4,8:1 entrambi, e il testo degli avvisi di errore da 4,4:1 a 5,9:1. Resta com'è il recupero della password che toglie anche l'app di autenticazione, da configurare di nuovo al primo accesso: il flow di reimpostazione del realm ha `reset-otp` senza configurazione, come quello predefinito di Keycloak.
- **Runbook.** Il [Runbook operativo](runbook-operativo.md) descrive l'aggiornamento di Keycloak e il ritorno indietro (§8.5): la versione nuova aggiorna lo schema di `taurus_keycloak` all'avvio e non si torna indietro senza il backup preso prima. Il [README del deploy](../deploy/README.md) ci rimanda. Anche il Keycloak di sviluppo aggiorna il proprio database al primo avvio con `keycloak.yml`, quindi il JAR in `realm-config` va ricompilato e copiato insieme.

### Stato dei lavori

Fase 0 verificata e committata il 2026-11-06 sul ramo `update-versions`, che parte da `84ae3b8`. I commit sono sei, uno per modulo e due per il backend:

- `Limit the cached test contexts`: il limite alla cache dei contesti di test del backend;
- `Update vulnerable backend dependencies`: `pom.xml` e `Dockerfile` del backend;
- `Update Angular and PrimeNG to their last 19.x releases`: `package.json` e lockfile del frontend;
- `Update vulnerable info site dependencies`: lockfile del sito informativo;
- `Update certifi in the OMR service`: i requisiti del servizio OMR;
- `Add expiring Trivy exceptions for the platform upgrade`: `.trivyignore.yaml`, il job `security` e la documentazione.

La verifica visiva ha aggiunto un settimo commit il 2026-11-07, `Align PrimeNG themes with PrimeNG 19.1`: `@primeng/themes` 19.1.4 nel `package.json` e nel lockfile del frontend, allineato a PrimeNG 19.1 (vedi [Verifica visiva del frontend](#verifica-visiva-del-frontend)). La suite end to end ha aggiunto un ottavo commit lo stesso giorno, `Restore the button role of PrimeNG toggle buttons`: `ToggleButtonRoleDirective` nel frontend, con il suo test e una nota nel README della suite (vedi [Suite end to end](#suite-end-to-end)). Sempre il 2026-11-07 il merge `16e5044` ha unito il ramo `fix-a11y-defects`, che parte da `main` e ne porta anche `6c36409`, `Mark track page image editing as implemented`. I suoi tre commit correggono due difetti di accessibilità già presenti, trovati dalla suite end to end: il nome del campo data del rinvio nel widget delle notifiche e il contrasto di `p-message` e `p-toast`, insieme al preset accessibile di `app.config.ts`, che non arrivava a `providePrimeNG`.

Il ramo è pubblicato e la pull request è la [#118](https://github.com/danielefundaro/Taurus/pull/118). Nella prima esecuzione della CI, il 2026-11-07, sono passati tutti i job tranne `security`, compreso il backend con Java 17. Trivy passava; si fermava semgrep, con 29 segnalazioni della regola `github-actions-mutable-action-tag`, che il set `p/ci` ha aggiunto da poco. semgrep scarica le regole a ogni esecuzione, e su `main` non era mai arrivato a girare perché il job si fermava prima, su Trivy. Il commit `Pin GitHub Actions to commit SHAs` fissa le sette azioni allo SHA del commit a cui portava il loro tag maggiore, con la versione in commento, e anticipa così la parte della fase 1 che le fissa per SHA. Per `pnpm/action-setup` il tag `v4` porta alla 4.3.0: la 4.4.0 è lo stesso commit della 5.0.0. Con quel commit la CI della pull request è passata in tutti gli otto job, lo stesso giorno: il backend in 11 minuti con Java 17, il servizio OMR in 5, gli altri in meno di 2.

Dopo il commit `d2f0285`, che registrava la CI verde, il job `frontend` è fallito una volta, su `GuidedTourService invites the members present at the release instead of opening the tour`. Il test dipendeva dall'ordine casuale di Jasmine. La spec di `MediaThumbnailComponent` chiudeva l'anteprima di PrimeNG con `closePreview()` e distruggeva subito il componente: `Image` è `OnPush`, la vista non si aggiornava e la maschera, spostata nel `body`, restava nel documento. Il servizio della guida non si apre con una maschera aperta, quindi falliva il primo test della guida eseguito dopo, se si aspettava la guida aperta. Il commit `bc8a2fe`, `Fix order-dependent guided tour test`, chiude l'anteprima dal pulsante e attende la fine dell'animazione; la spec della guida toglie le maschere anche prima di ogni test. Con quel commit la CI è passata di nuovo in tutti gli otto job. La pull request è stata unita su `main` il 2026-11-07 con il merge `6cb19fd`: la fase 0 è chiusa.

Fase 1 verificata il 2026-11-07 sul ramo `upgrade-phase-1`, che parte da `6cb19fd` (vedi [Esito della fase 1](#esito-della-fase-1)). I commit sono quattro:

- `Update GitHub Actions to their latest releases`: le azioni dei tre workflow;
- `Update the CI security scanners`: il job `security`, `.gitleaks.toml`, `.trivyignore.yaml` e il Runbook di test;
- `Add Dependabot configuration`: `.github/dependabot.yml`;
- `Record phase 1 of the platform upgrade`: questa specifica e il catalogo.

La pull request è la [#120](https://github.com/danielefundaro/Taurus/pull/120): la CI è passata in tutti gli otto job alla prima esecuzione. È stata unita su `main` il 2026-11-07 con il merge `71c1260`, e anche lì la CI è passata in tutti gli otto job: la fase 1 è chiusa. Gli aggiornamenti di sicurezza di Dependabot erano già attivi nelle impostazioni del repository: dopo il merge della fase 0 hanno aperto la [#119](https://github.com/danielefundaro/Taurus/pull/119), che porta `http-proxy-middleware` alla 3.0.7 e con lui `@angular-devkit/build-angular` dalla 19.2.27 alla 21.2.29. È un salto di due maggiori, che appartiene alla fase 5, e nel job `frontend` `npm ci` fallisce. Altri tre aggiornamenti di sicurezza, di npm nel backend e nel frontend, non sono arrivati a una pull request; dopo il merge della fase 1 è fallito anche quello di esbuild nel frontend, il cui log richiede l'accesso al repository.

Con il merge della fase 1 gli aggiornamenti di versione hanno aperto 26 pull request, e cinque contraddicevano la specifica (vedi [Esito della fase 1](#esito-della-fase-1)). Le esclusioni sono arrivate con la [#147](https://github.com/danielefundaro/Taurus/pull/147), dal ramo `fix-dependabot-rules`: la CI è passata in tutti gli otto job, e anche su `main` dopo il merge `cf20bf0` del 2026-11-07. Alla seconda esecuzione Dependabot ha chiuso da solo le pull request escluse, ma ne ha aperte altre che la specifica non vuole. Le regole sulle maggiori sono arrivate con la [#151](https://github.com/danielefundaro/Taurus/pull/151), dal ramo `fix-dependabot-majors`, unita su `main` il 2026-11-07 con il merge `9db617b`: la CI di `main` è passata in tutti gli otto job. Alla terza esecuzione le pull request aperte rispettano la specifica.

Fase 2 verificata il 2026-11-07 sul ramo `upgrade-phase-2`, che parte da `9db617b` (vedi [Esito della fase 2](#esito-della-fase-2)). I commit sono due:

- `Update the OMR service libraries`: requisiti, `pyproject.toml`, Dockerfile e `THIRD_PARTY_NOTICES.md` del servizio OMR;
- `Record phase 2 of the platform upgrade`: questa specifica e il catalogo.

La pull request è la [#158](https://github.com/danielefundaro/Taurus/pull/158), unita su `main` il 2026-11-07 con il merge `bb1c2f4`: la CI di `main` è passata in tutti i job. Subito dopo Dependabot ha chiuso da solo [#140](https://github.com/danielefundaro/Taurus/pull/140), [#142](https://github.com/danielefundaro/Taurus/pull/142), [#144](https://github.com/danielefundaro/Taurus/pull/144) e [#150](https://github.com/danielefundaro/Taurus/pull/150), che la fase conteneva. Uno dei cinque job di Dependabot sul merge è fallito, e il suo log richiede l'accesso al repository. La fase 2 è chiusa.

Fase 3 verificata il 2026-11-07 sul ramo `upgrade-phase-3`, che parte da `bb1c2f4` (vedi [Esito della fase 3](#esito-della-fase-3)). I commit sono sei:

- `Update the Keycloak provider to Keycloak 26.8.2`: `pom.xml`, wrapper Maven, IT e README del provider;
- `Fix the Taurus theme on Keycloak pages`: modelli, messaggi italiani e `login.css` del tema, con le prove nuove di `TenantRoleFlowIT` e il suo realm (vedi [Esito della fase 3](#esito-della-fase-3));
- `Build the Keycloak provider with Java 21 in CI`: i job `keycloak` e `publish-provider` dei workflow;
- `Use Keycloak 26.8.2 in the backend development services`: `keycloak.yml` e i due IT del backend su Keycloak;
- `Use Keycloak 26.8.2 in the deployment manifest`: `compose.yml`, `.env.example` e README del deploy;
- `Record phase 3 of the platform upgrade`: `.trivyignore.yaml`, `AGENTS.md`, `README.md`, i runbook, lo standard delle e-mail, questa specifica e il catalogo.

Con il merge, Dependabot dovrebbe chiudere da solo la [#129](https://github.com/danielefundaro/Taurus/pull/129), che la fase contiene con Keycloak 26.8.2 al posto della 26.8.1.

#### Verifiche eseguite

| Modulo | Comando | Esito |
| --- | --- | --- |
| Repository | Trivy 0.70.0 come nella CI, con `--ignorefile .trivyignore.yaml` | nessuna segnalazione, codice di uscita 0 |
| Repository | `validate-feature-catalog.mjs`, `feature-catalog.test.mjs`, `check-feature-drift.mjs` | superati |
| Frontend | `npm run build` | riuscita; due avvisi di budget CSS: `pdf-manipulator-dialog.component.scss` 7,72 kB e `preview.component.scss` 7,95 kB, sul limite di 7 kB |
| Frontend | `npm run test:coverage` | 736 test su 736; copertura sopra la baseline (statements 64,46%, branches 54,29%, lines 66,53%) |
| Frontend | `npm run build` e `npm run test:coverage` con `@primeng/themes` 19.1.4 | build riuscita con gli stessi due avvisi di budget; 736 test su 736, copertura sopra la baseline (statements 64,48%, branches 54,31%, lines 66,54%) |
| Frontend | verifica visiva nel browser integrato | vedi [Verifica visiva del frontend](#verifica-visiva-del-frontend) |
| Frontend | `npm run e2e`, profili desktop e mobile, con i tenant di prova `E2E1` ed `E2E2` | 40 superate, 38 saltate, 16 fallite in 19 minuti: vedi [Suite end to end](#suite-end-to-end) |
| Frontend | `inventory-qr.spec.ts` e `onboarding-import.spec.ts` con le funzionalità accese su `E2E1` | 14 superate, 6 saltate: le prove sull'oggetto, senza `E2E_INVENTORY_QR_CODE` |
| Frontend | `npm run build` e `npm run test:coverage` con `ToggleButtonRoleDirective` | build riuscita con gli stessi due avvisi di budget; 741 test su 741, copertura sopra la baseline (statements 64,54%, branches 54,36%, lines 66,59%) |
| Frontend | `accessibility.spec.ts` dopo la correzione, e axe su tracce, album e calendario | pagina di accesso senza violazioni; la dashboard dell'amministratore resta con `label` sul campo data delle notifiche, difetto già presente; tracce, album e calendario senza violazioni |
| Frontend | login in Google Chrome 156 con il service worker attivo | riuscito: vedi [Suite end to end](#suite-end-to-end) |
| Frontend | `npm run build` e `npm run test:coverage` dopo il merge di `fix-a11y-defects` | build riuscita con gli stessi due avvisi di budget; 752 test su 752, copertura sopra la baseline (statements 64,59%, branches 54,37%, lines 66,64%); le misure di contrasto di `accessible-theme.spec.ts` passano sui token di `@primeng/themes` 19.1.4 |
| Repository | `validate-feature-catalog.mjs` e `check-feature-drift.mjs` dopo il merge | superati |
| Tutti | CI della pull request [#118](https://github.com/danielefundaro/Taurus/pull/118), prima esecuzione | superati tutti i job tranne `security`: 29 segnalazioni di semgrep sulle azioni non fissate per SHA |
| Repository | semgrep 1.97.0 con `p/ci`, come nel job `security`, sui file versionati con le azioni fissate per SHA | 0 segnalazioni su 1798 file, codice di uscita 0; sui workflow di prima, 29 segnalazioni come in CI |
| Tutti | CI della pull request [#118](https://github.com/danielefundaro/Taurus/pull/118) sul commit `Pin GitHub Actions to commit SHAs` | otto job su otto superati, fra cui `security` e `backend` con Java 17 |
| Frontend | `npm run test:coverage` dopo `Fix order-dependent guided tour test`, e la spec della guida con una maschera inserita prima di ogni test | 752 test su 752, copertura sopra la baseline (statements 64,66%, branches 54,48%, lines 66,68%); con la maschera la spec precedente falliva 3 test, quella corretta nessuno |
| Tutti | CI della pull request [#118](https://github.com/danielefundaro/Taurus/pull/118) sul commit `Fix order-dependent guided tour test` | otto job su otto superati |
| Tutti | CI di `main` sul merge `6cb19fd` | superata |
| Repository | actionlint 1.7.12 sui tre workflow della fase 1 | gli stessi 6 avvisi di shellcheck di `main`, nessuno nuovo |
| Repository | `./mvnw -B -q dependency:resolve` nel backend e nel provider, come nel job `security` | riuscito in entrambi: meno di mezzo minuto ciascuno con il repository Maven locale già pieno, circa 2 minuti ciascuno, 307 MB in tutto, partendo da un repository vuoto |
| Repository | Trivy 0.76.2 come nella CI, con il solo repository Maven creato da `dependency:resolve` | nessun errore di Maven Central, codice di uscita 0; soppresse 76 voci nel backend e 3 nel provider, cioè tutte: il passo basta anche senza la cache del job `backend` |
| Repository | Trivy 0.76.2 come nella CI, senza repository Maven locale | errore 429 di Maven Central sul `pom.xml`, codice di uscita 1 |
| Repository | Trivy 0.76.2 e 0.70.0 con il repository Maven locale e il `.trivyignore.yaml` della fase 0 | 75 vulnerabilità nel `pom.xml` del backend e 3 in quello del provider, le stesse con le due versioni |
| Repository | Trivy 0.76.2 come nella CI, con il repository Maven locale e le voci della fase 1 | nessuna segnalazione, codice di uscita 0 |
| Repository | gitleaks 8.30.1 come nella CI, su tutta la storia | nessuna fuga su 612 commit; senza le due voci nuove dell'allowlist, 5 falsi positivi |
| Repository | semgrep 1.183.0 con `p/ci`, come nel job `security` | 0 segnalazioni con 88 regole su 1785 file, codice di uscita 0; con `cooldown` di 3 giorni, 13 segnalazioni |
| Repository | `.github/dependabot.yml` contro lo schema di SchemaStore, con `ajv-cli` 5.0.0 | valido |
| Repository | `validate-feature-catalog.mjs`, `feature-catalog.test.mjs` e `check-feature-drift.mjs` dopo la fase 1 | superati |
| Tutti | CI della pull request [#120](https://github.com/danielefundaro/Taurus/pull/120) | otto job su otto superati alla prima esecuzione |
| Tutti | CI di `main` sul merge `71c1260` | otto job su otto superati |
| Repository | tag su Docker Hub delle immagini proposte da Dependabot, e `ignore_condition.rb` e `requirement.rb` di dependabot-core | ultimo push di `node:26-alpine` il 2026-11-05 e di `node:25-alpine` il 2026-04-15; `nginx:1.31-alpine`, mainline, ripubblicata il 2026-11-06 come `alpine`; gli intervalli separati da virgola valgono come «e» per Docker |
| Repository | `requires_dist` di homr 0.7.0 su PyPI | limiti superiori su nove librerie fissate nei requisiti, nessuno su music21 |
| Repository | `.github/dependabot.yml` con le esclusioni, contro lo schema di SchemaStore con ajv 8.18.0 | valido |
| Repository | semgrep 1.183.0 con `p/ci`, come nel job `security`, con le esclusioni | 0 segnalazioni con 88 regole su 1785 file, codice di uscita 0 |
| Repository | `validate-feature-catalog.mjs`, `feature-catalog.test.mjs` e `check-feature-drift.mjs` con le esclusioni | superati |
| Tutti | CI della pull request [#147](https://github.com/danielefundaro/Taurus/pull/147) e di `main` sul merge `cf20bf0` | otto job su otto superati in entrambe |
| Repository | seconda esecuzione di Dependabot e CI delle sue pull request | 20 job di Dependabot superati; chiuse da sole #121, #122, #124, #125, #138 e #145; CI rossa su #132 (`npm ci`), #137 (`./mvnw verify`), #133–#136 e #139 |
| Frontend | `npm ci --ignore-scripts --dry-run` con `package.json` e lockfile della #132, Node 22.11.0 | `ERESOLVE`: zone.js 0.16.3 contro `~0.15.0` di `@angular/core` 19.2.25 |
| Repository | `node:23-alpine` su Docker Hub e `version.rb` di dependabot-core | ultimo push il 2025-05-31; con `update-types` l'intervallo parte dalla versione installata: `>= 20.a` per le maggiori di Angular 19, `>= 0.16.a, < 1` e `>= 1.a` per minor e maggiori di zone.js 0.15 |
| Repository | `.github/dependabot.yml` con le regole sulle maggiori, contro lo schema di SchemaStore con ajv 8.18.0 | valido |
| Repository | semgrep 1.183.0 con `p/ci`, come nel job `security`, con le regole sulle maggiori | 0 segnalazioni con 88 regole su 1785 file, codice di uscita 0 |
| Repository | i controlli del job `documentation` con le regole sulle maggiori | superati: catalogo valido, 11 e 13 test superati, catalogo degli errori senza errori, `features.md` uguale a quello generato, nessun disallineamento |
| Tutti | CI di `main` sul merge `9db617b` e terza esecuzione di Dependabot | otto job su otto superati e 19 job di Dependabot superati; chiuse da sole #131–#137, #139, #148 e #149 |
| Servizio OMR | in `python:3.12-slim`, `pip install` con `--uploaded-prior-to 2026-10-31T18:00:00Z` delle dipendenze di `pyproject.toml` con homr 0.7.0, di quelle dei test e del rendering del corpus, poi `pip freeze` e `pip check` | nessun conflitto nei tre ambienti; senza il limite di data, otto versioni più recenti |
| Servizio OMR | `python -m pytest` in `python:3.12-slim` con i requisiti nuovi | 71 superati, 6 saltati: i test di accuratezza |
| Servizio OMR | `docker build` dell'immagine | riuscita; le otto impronte di `models.sha256` corrispondono |
| Servizio OMR | `tests/test_accuracy.py` sull'immagine nuova, con le restrizioni di `omr.yml` | 6 test su 6 in 3 minuti: 132 battute su 151 con Audiveris (87%) e 124 con homr (82%), segni di struttura 15 e 6 su 26, nomi delle parti 6 su 6 |
| Servizio OMR | `/v1/midi-conversions` dell'immagine nuova con un file MIDI di due parti creato con music21 | risposta 200 con 2 parti, 4 battute e 16 note; `/v1/health` riporta music21 10.5.2 |
| Repository | Trivy 0.76.2 come nella CI sul servizio OMR | con `.trivyignore.yaml` nessuna segnalazione; senza, le 28 vulnerabilità di Pillow 12.3.0 e nessun'altra |
| Repository | i controlli del job `documentation` con la fase 2 | superati: catalogo valido, 11 e 13 test superati, catalogo degli errori senza errori, `features.md` uguale a quello generato, nessun disallineamento |
| Sito informativo | `pnpm check`, `pnpm build`, `pnpm test`, `pnpm test:image` | superati |
| Servizio OMR | `python -m pytest` in `python:3.12-slim` con `requirements-test.txt` | 71 superati, 6 saltati: i test di accuratezza, che chiedono un servizio con i motori reali |
| Backend | `./mvnw verify -Dtaurus.test.requireDocker=true`, con RabbitMQ irraggiungibile come in CI | superata in 26 minuti: 1225 test unitari e 279 di integrazione; copertura unita 75,59% delle linee e 60,53% dei branch, sopra le soglie |
| Servizio OMR | `docker build` e `tests/test_accuracy.py` sull'immagine costruita, come il job `omr` della CI | immagine costruita con certifi 2026.10.23; 6 test su 6 in 3 minuti: battute lette 87% con Audiveris e 82% con homr, nomi delle parti 6 su 6 |
| Tutti | CI di `main` sul merge `bb1c2f4` della fase 2 e quarta esecuzione di Dependabot | tutti i job superati; chiuse da sole #140, #142, #144 e #150; un job di Dependabot su cinque fallito |
| Provider Keycloak | `./mvnw clean verify` con JDK 21 e Maven 3.10.0 | superata in 2 minuti e 15 secondi: 32 test unitari e 10 di integrazione su Keycloak 26.8.2, cioè `TenantRoleFlowIT` 6, `TaurusOtpResourceIT` 3 ed `EmailDeliveryIT` 1; con `javap` le classi sono nella versione 65, Java 21, in `target/classes` e nel JAR |
| Provider Keycloak | `java -version` nell'immagine `quay.io/keycloak/keycloak:26.8.2` | OpenJDK 21.0.13 |
| Backend | `SharedUserAccountKeycloakIT` e `OnboardingKeycloakIT` con Keycloak 26.8.2 e `-Dtaurus.test.requireDocker=true` | 9 test su 9, 7 e 2 |
| Repository | Trivy 0.76.2 come nella CI sul provider, con i POM del repository Maven locale dopo `dependency:resolve` | nessuna segnalazione, con e senza `.trivyignore.yaml`; senza il repository locale Maven Central risponde 429, come nella fase 1 |
| Provider Keycloak | modelli e messaggi di `keycloak.v2` e `base` estratti dalle immagini 26.6.4 e 26.8.2 | `registrationLayout` e sezioni invariati; nuovi `login-update-profile.ftl` e `login-verify-email.ftl`; una chiave italiana nuova; modelli delle e-mail usati da Taurus identici |
| Provider Keycloak | prova a mano su Keycloak 26.8.2 con il JAR della build, il realm di Taurus e Mailpit, nel browser integrato | flussi riusciti, e-mail con il tema Taurus: vedi [Esito della fase 3](#esito-della-fase-3) |
| Repository | i controlli del job `documentation` con la fase 3 | superati: catalogo valido, 11 e 13 test superati, catalogo degli errori senza errori, `features.md` uguale a quello generato, nessun disallineamento |
| Provider Keycloak | `./mvnw clean verify` con JDK 21 dopo la correzione del tema | superata: 32 test unitari e 13 di integrazione, cioè `TenantRoleFlowIT` 9, con le tre prove nuove sulle pagine del tema, `TaurusOtpResourceIT` 3 ed `EmailDeliveryIT` 1 |
| Provider Keycloak | prova a mano del tema corretto su Keycloak 26.8.2 usa e getta, con il JAR della build, il realm di Taurus e Mailpit, nel browser integrato a 375×812 | in stile Taurus conferma dell'uscita, errore, aggiornamento del profilo con l'errore su un campo vuoto, verifica dell'e-mail, informazione dell'invito, account aggiornato e pagina scaduta; app di autenticazione con i nomi delle app, un solo link al codice manuale, codice in vista con lo script, aprendo il link e dopo un codice sbagliato, configurazione conclusa; ritorno all'accesso con una sola «; «È necessario selezionare un tenant» con l'accento; nessun errore dei modelli nel log |
| Repository | i controlli del job `documentation` dopo la correzione del tema | superati: catalogo valido, 11 e 13 test superati, catalogo degli errori senza errori, `features.md` uguale a quello generato, nessun disallineamento |

Non ripetuta dopo la correzione: l'intera `npm run e2e`. La direttiva tocca solo attributi ARIA, e la suite di accessibilità è stata ripetuta. Dopo il merge di `fix-a11y-defects` non sono stati ripetuti né axe né la suite end to end: le due correzioni sono state verificate con axe sul loro ramo, con PrimeNG 19.0.10, e sul ramo unito le coprono i test unitari.

#### Verifica visiva del frontend

Eseguita il 2026-11-06 nel browser integrato dell'app desktop di Claude, con `npm start`, il backend locale e il superadmin di sviluppo sul tenant della banda. Sono state viste le pagine del [Taurus Layout Standard](taurus-layout-standard.md) in tema scuro e chiaro, a 1280×800 e a 375×812: Utenti, Strumenti in lista e griglia, Tenant, Inventario, Economia, Calendario in lista e mese, Documenti legali con l'editor, Profilo, Configurazione iniziale, Consegne notifiche, Feed calendario, la pagina 404. Anche i dialoghi Nuovo utente e Nuovo evento con il selettore di data e ora, la conferma di eliminazione, il menu laterale su mobile, il dettaglio di una traccia con parti, ascolto e file PDF, l'anteprima degli spartiti nel lettore PDF e il lettore della partitura con Verovio.

- **Difetto trovato e corretto.** PrimeNG 19.1 usa token di tema che `@primeng/themes` 19.0.5 non definisce, fra cui `togglebutton.content.padding`, `button.sm.icon.only.width` e `progressspinner.color.one`. Le variabili CSS mancanti azzeravano le proprietà: il contenuto di SelectButton e ToggleButton misurava 14×14 pixel, senza padding né arrotondamento. Con `@primeng/themes` 19.1.4 le variabili sono definite e il contenuto misura 35×21, con padding e angoli di 6 pixel.
- **Service worker nel browser integrato.** Dopo il riavvio di `npm start` il worker, in stato degradato per l'hash cambiato di un chunk, rispondeva 504 a ogni richiesta verso Keycloak e il backend: la sua `fetch` falliva con `TypeError: Failed to fetch` e l'app tornava alla pagina di logout. Non dipende dall'aggiornamento: in `ngsw-worker.js` il codice che inoltra le richieste di altre origini è identico fra la 19.0.5 e la 19.2.25, che cambiano solo le richieste di cache-busting degli asset e lo scope degli script dei worker. Per la verifica il worker è stato disattivato a mano e la modifica è stata tolta subito dopo. Il login in Chrome con il worker attivo, il 2026-11-07, è riuscito (vedi [Suite end to end](#suite-end-to-end)).
- **Già presenti, non regressioni.** In Inventario il cestino va sotto «Modifica» perché la pagina è dentro `<p-fluid>`, e in Button la regola che rende il pulsante fluido è identica fra PrimeNG 19.0.10 e 19.1.4. Nel dialogo Nuovo utente il campo Strumenti resta stretto. Su mobile gli step di Configurazione iniziale scorrono in orizzontale dentro la loro riga, senza allargare la pagina. In console restano i 401 delle pagine pubbliche senza sessione e due NG0100 della sola modalità di sviluppo: `p-inputtext-fluid` nel dettaglio della traccia, da InputText, identico fra le due versioni di PrimeNG, e `LoadingSpinnerComponent`.

#### Suite end to end

Eseguita il 2026-11-07 con `npm start`, il backend locale e i profili desktop e mobile.

- **Ambiente.** Le password dei sei utenti `e2e-*` del realm di sviluppo sono state reimpostate nel Keycloak locale, e le variabili `E2E_*` le passano alla suite. `E2E2` era stato cancellato del tutto: riga in `public.tenant`, registro degli schemi, schema e gruppo di Keycloak. È stato ricreato come chiede il [README della suite](../taurus-fe/e2e/README.md): il backend crea lo schema subito dopo l'avvio, poi si aggiungono `app_user` e ruoli dei due utenti. Il lockfile della fase 0 porta Playwright dalla 1.49.1 alla 1.65.0: al primo giro ogni prova falliva in pochi millisecondi perché mancava `chromium_headless_shell-1254`, poi installato con `npx playwright install --only-shell chromium`.
- **Regressione trovata e corretta.** In PrimeNG 19.1 ToggleButton non disegna più un `<button>`: `aria-pressed`, `tabindex` e la tastiera stanno sull'elemento `p-togglebutton`, che non ha ruolo. axe segnala `aria-allowed-attr` (critical) sui selettori lista e griglia di tracce, album e calendario, e un lettore di schermo non annuncia un pulsante. Il difetto tocca ogni SelectButton e ToggleButton. La 19.1.4 è l'ultima 19 gratuita e nessuna vulnerabilità la richiede: si tiene e si corregge nell'applicazione. `ToggleButtonRoleDirective`, in `taurus-fe/src/app/directive`, sta nel modulo condiviso `ImportsModule` e nei tre componenti che importano PrimeNG da sé. Aggiunge `role="button"` e `aria-disabled`, e porta sull'elemento l'`ariaLabel` di ToggleButton, che nessuna delle due versioni usa. I ToggleButton creati da SelectButton li cerca sotto l'host e li segue con un `MutationObserver`. Dopo la correzione tracce, album e calendario sono senza violazioni, e il selettore lista e griglia risponde al clic e a Invio.
- **Effetti dell'ambiente.** Le prove del QR inventario e della configurazione iniziale, 7 per profilo, andavano in timeout: su `E2E1` inventario, QR e importazione iniziale sono spenti dal 2026-11-04. Con la funzionalità spenta il frontend non apre la pagina e la risposta che la prova aspetta non arriva, mentre la prova si salta solo davanti a un 403 del backend. Accese per la prova e poi rimesse com'erano, le funzionalità fanno passare tutte le prove che non chiedono un codice QR.
- **Già presenti, non regressioni.** Sulla dashboard il campo data per posticipare una notifica riceve l'etichetta con `[attr.aria-label]`, che resta sull'host di `p-datepicker`: axe segnala `label` (critical) per ogni notifica. Il 2026-10-31 non si vedeva perché l'amministratore non aveva notifiche. Su `/legal/privacy`, senza un documento in vigore, l'avviso `warn` ha il testo a 2,84:1 sul fondo, con token e CSS di Message identici fra le due versioni. Nel profilo il titolo della zona pericolosa e i pulsanti `danger` restano sotto 4,5:1, il gap già annotato in `accessible-theme.ts`. I primi due sono stati corretti il 2026-11-07 nel ramo `fix-a11y-defects`, unito a questo: il campo usa l'ingresso `ariaLabel`, che arriva all'input, e il testo di Message e Toast passa alla tinta 700 in modalità chiara, alla 800 per `warn`, e alla 400 in modalità scura. L'avviso di `/legal/privacy` arriva così a 6,63:1 in chiaro e a 8,43:1 in scuro. Il terzo resta aperto.
- **Login con il service worker.** L'estensione Claude in Chrome non era collegata: Playwright ha guidato il Google Chrome 156 installato, con un profilo pulito e i worker ammessi. Primo accesso, ricarica con la pagina controllata dal worker e nuova scheda sono riusciti. Le richieste del token passano dal worker con risposta 200, e nessuna chiamata verso Keycloak o le API risponde 5xx o fallisce.

#### Memoria esaurita negli IT del backend

Risolto il 2026-11-06.

- **Sintomo.** Dopo `SignatureIT` il contesto di `AccountResourceIT` non partiva: l'heap di 1 GB del fork era pieno e, dopo 17-19 minuti di garbage collection continua, il fork moriva con codice 99. Gli IT successivi non giravano.
- **Causa.** Spring tiene in cache un contesto per ogni configurazione di test, fino a 32, e nel fork degli IT ne nascono 18 prima di `AccountResourceIT`, intorno ai 50 MB l'uno. Nell'istogramma dell'heap dominano le cache di ogni contesto: le corrispondenze dei pointcut AspectJ (un milione di `ShadowMatchImpl`), gli oggetti `Method` della riflessione, i `Lazy` di Spring Data. Le centinaia di thread dei listener RabbitMQ che riprovano a collegarsi sono un effetto dei contesti vivi, non la causa.
- **Non è una regressione della fase 0.** La stessa prova su `84ae3b8`, con RabbitMQ irraggiungibile come in CI, si è fermata nello stesso punto: heap pieno al 100%, 578 thread, contesto di `AccountResourceIT` non avviato dopo 10 minuti. La suite era già al limite: il 2026-11-06 alle 01:18, prima del commit `e6eff8d`, `AccountResourceIT` aveva impiegato 146 secondi.
- **Correzione.** `spring.test.context.cache.maxSize=8` in `taurus-be/src/test/resources/spring.properties`. Oltre il limite Spring chiude il contesto usato meno di recente, con i suoi pool e listener; il container PostgreSQL condiviso, registrato senza callback di distruzione, resta acceso. Con il limite ogni contesto parte in 10-13 secondi e `AccountResourceIT` dura 14 secondi. Il [Runbook di test](runbook-test.md) spiega il limite e come riconoscere un heap di nuovo al limite.

#### Note sull'ambiente locale

- Il backend è stato verificato con Java 21.0.2; la CI usa Java 17. Con i servizi di sviluppo avviati, `SPRING_RABBITMQ_PORT=5679` punta i test su una porta chiusa, come in CI dove nessun broker ascolta su `localhost:5672`.
- Il frontend è stato verificato con Node 22.11.0, come la CI che usa Node 22. pnpm 11.16 non parte su Node 22.11.0 e il corepack di quella versione rifiuta le chiavi di firma di pnpm: per il sito è stato usato Node 24.21.0.
- Il lockfile del frontend è stato rigenerato così: `npm audit fix`, minimi del `package.json` alzati a mano, poi rimozione dal lock delle sole voci con nome esatto (`@angular/*`, `@angular-devkit/*`, `@ngtools/*`, `@schematics/angular`, `primeng`, `postcss`) e `npm install`. Una rimozione per prefisso toglie anche `postcss-modules-*`, che npm non ripristina: la build passa, ma Karma non trova `postcss-modules-values`. `npm ls --all` deve uscire senza errori.
- Il `node_modules` del sito era collegato allo store pnpm `D:\Daniele\Zodiac\Taurus\.pnpm-store`, che non esisteva più. Lo store è stato ricreato per l'aggiornamento e poi cancellato: se pnpm segnala uno store inatteso, `pnpm install` ricollega le dipendenze.
- La coda `upload.files` del RabbitMQ locale è precedente alla dead-letter exchange introdotta il 2026-10-31 con `ad15f670`: all'avvio il backend registra `PRECONDITION_FAILED - inequivalent arg 'x-dead-letter-exchange'`. Non dipende dalla fase 0: cancellata la coda dalla console di RabbitMQ, il backend la dichiara di nuovo.
- L'enforcer del backend segnala due divergenze di dipendenze, solo come avvisi: amqp-client, 5.21.0 da `spring-rabbit` contro quella dichiarata, già presente con la 5.25.0, e `checker-qual`, 3.55.1 da pgjdbc 42.7.14 contro 3.37.0 da Caffeine, nuova.

## Modifiche per modulo

### Repository

- `.github/workflows/*.yml`: azioni alle nuove versioni maggiori, fissate per SHA come già dalla fase 0; Trivy con `version` esplicita e le dipendenze Maven risolte prima della scansione; gitleaks e semgrep aggiornati. Fatto nella fase 1. Node 26 e Java 25 nei job arrivano con le fasi 4–6.
- `.github/dependabot.yml`: nuovo (decisione 7), nella fase 1.
- `.gitleaks.toml`: due test del backend nell'allowlist, falsi positivi di gitleaks 8.30 (fase 1).
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
- `ToggleButtonRoleDirective`, aggiunta nella fase 0: da togliere se PrimeNG 22 dà a ToggleButton il ruolo di pulsante, verificandolo con la suite di accessibilità.
- `Dockerfile`: `node:26-alpine` e nginx senza root sulla porta 8080.
- `docs/llms-full.md` rigenerato per PrimeNG 22.

### Sito informativo

- `package.json`, `packageManager` pnpm 12, lockfile; adeguamenti di Astro 6 e 7; `Dockerfile` come il frontend.

### Servizio OMR

- `requirements.txt`, `requirements-test.txt`, `requirements-accuracy.txt` aggiornati insieme, come chiede l'intestazione del file; `models.sha256` invariato salvo nuovi modelli di homr.

### Provider Keycloak

- `pom.xml`: Keycloak 26.8, FreeMarker del runtime, `--release` della JVM di Keycloak; temi verificati sulle pagine di login, registrazione ed e-mail. Fatto nella fase 3, con Keycloak 26.8.2 e Java 21.

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
