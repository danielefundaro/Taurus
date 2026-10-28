# Suite end to end

Guida un browser reale contro un'istanza completa di Taurus: frontend, backend, Keycloak,
PostgreSQL e RabbitMQ. È la suite chiesta dal [Runbook di test](../../docs/runbook-test.md) per i
percorsi con ruoli e tenant multipli e per il gate di accessibilità, e non sostituisce i test di
componente di `npm test`.

Le prove sono in `specs`:

- `roles-and-tenants.spec.ts`: menu per ruolo, nome del tenant nella topbar e indirizzo di una
  traccia non raggiungibile dall'altro tenant;
- `accessibility.spec.ts`: axe-core con le regole WCAG 2.1 A e AA sulla pagina di accesso e sulle
  pagine principali di un amministratore. Lo scanner trova le violazioni automatiche, non tutte: la
  verifica a mano con tastiera e lettore di schermo resta nel runbook;
- `track-parts-workspace.spec.ts`: il workspace delle parti con un archivista — frecce e Maiusc
  sull'elenco, trascinamento di una pagina sulla parte di destinazione, divisore da tastiera,
  conferma di uscita con bozza modificata e dettaglio della parte sotto 768 px. Le prove si saltano
  se la traccia aperta non ha almeno due parti e una pagina, perché il workspace non crea pagine:
  nascono dall'elaborazione di un PDF;
- `operational-dashboard.spec.ts`: la sezione «Da fare» per ogni ruolo configurato — voci
  restituite dal server dentro la matrice del ruolo, risposta `no-store`, una riga per voce o lo
  stato vuoto —, ogni azione di un amministratore che apre la pagina già filtrata, **Aggiorna** da
  tastiera con l'annuncio dell'esito, axe-core sulla sezione per amministratore e utente, nessuno
  scorrimento orizzontale a 320, 768 e 1280 pixel con azioni di riga alte almeno 44 pixel. I dati
  dei tenant di prova non sono fissati: le prove verificano invarianti, non conteggi, e quella dei
  collegamenti si salta se l'amministratore non ha attività. Risultato parziale, isolamento fra
  tenant con dati omonimi e obiettivi di prestazione sono in `OperationalDashboardIT` del backend;
- `inventory-qr.spec.ts`: la scansione dei QR inventario — deep link senza sessione che passa dal
  login e torna alla scansione, stesso stato «Codice non disponibile» con `404` per un codice
  sconosciuto o di un altro tenant, per amministratore e utente, pulsanti amministrativi presenti
  solo se compaiono in `allowedActions`, collegamento **Assegna** che apre e mette a fuoco la
  sezione della scheda, apertura diretta della consegna quando il socio ne ha una sola, axe-core e
  nessuno scorrimento orizzontale a 320 pixel con azioni alte almeno 44 pixel. La rotazione non è
  provata perché invaliderebbe le etichette dell'ambiente: resta nelle verifiche fisiche. Le prove
  sull'oggetto si saltano senza `E2E_INVENTORY_QR_CODE`.
- `event-preparation.spec.ts`: la preparazione di un evento per ruolo — stato, avanzamento e
  criticità dell'amministratore con risposta `no-store`, collegamento di una criticità che aggiorna
  il fragment e mette a fuoco la sezione, archivista che legge soltanto la vista di catalogo e
  riordina il programma da tastiera con l'annuncio nella live region e la guardia che elenca
  l'unità «programma», socio che legge il programma senza stato né strumenti di modifica, tesoriere
  che chiama soltanto `/api/finance/**`, axe-core e nessuno scorrimento orizzontale a 320 pixel. Le
  prove non salvano nulla e si saltano senza `E2E_PREPARATION_EVENT_ID`; isolamento fra tenant,
  limiti, conflitti di versione e prestazioni sono in `EventPreparationIT` del backend.

## Prerequisiti

1. Dipendenze avviate: `docker compose -f ../taurus-be/src/main/docker/services.yml up --wait`.
2. Backend in esecuzione: `cd ../taurus-be && ./mvnw`.
3. Frontend in esecuzione: `npm start` (serve su `http://localhost:4200`).
4. Browser di Playwright installati una volta sola: `npm run e2e:install` (Chromium, Firefox e WebKit).
5. Due tenant di prova in Keycloak, con utenti per i ruoli usati dalle prove.

## Variabili d'ambiente

Nessuna credenziale vive nel repository: la suite legge utenti e password dall'ambiente e si salta
quando non sono configurati.

| Variabile | Significato |
| --- | --- |
| `E2E_BASE_URL` | Indirizzo del frontend; senza di essa `http://localhost:4200` |
| `E2E_FIRST_TENANT`, `E2E_SECOND_TENANT` | Codici dei due tenant di prova |
| `E2E_FIRST_ADMIN_USERNAME`, `E2E_FIRST_ADMIN_PASSWORD` | Amministratore del primo tenant |
| `E2E_FIRST_<RUOLO>_USERNAME`, `E2E_FIRST_<RUOLO>_PASSWORD` | Altri ruoli del primo tenant |
| `E2E_SECOND_<RUOLO>_USERNAME`, `E2E_SECOND_<RUOLO>_PASSWORD` | Ruoli del secondo tenant |
| `E2E_INVENTORY_QR_CODE` | Identificatore pubblico del QR di un oggetto del primo tenant con `INVENTORY_QR` attivo, letto dall'etichetta stampata; senza di esso le prove sull'oggetto si saltano |
| `E2E_SECOND_INVENTORY_QR_CODE` | Identificatore di un oggetto del secondo tenant, per provare che dal primo risponde come un codice sconosciuto; facoltativo |
| `E2E_PREPARATION_EVENT_ID` | Evento del primo tenant con `EVENT_PREPARATION` attivo, piano configurato e programma richiesto, possibilmente con almeno due brani e una criticità; senza di esso le prove della preparazione si saltano |
| `E2E_LARGE_TRACK_ID` | Traccia del primo tenant con 200 parti e 1.000 pagine, per il gate prestazionale del workspace; senza di essa quella prova si salta |

I ruoli ammessi sono `SUPERADMIN`, `ADMIN`, `ARCHIVIST`, `USER`, `EXTERNAL` e `TREASURER`.

## Esecuzione

```bash
npm run e2e                      # il gate: profili desktop e mobile
npm run e2e:matrix               # tutta la matrice: desktop, mobile, tablet, Firefox, WebKit
npm run e2e -- --project=desktop
npm run e2e -- --headed --project=desktop
```

I profili sono dichiarati in `playwright.config.ts`. Il gate resta su desktop e mobile perché
Firefox e WebKit raddoppiano la durata del run: la matrice completa va eseguita nei cicli di
release, come dice il runbook.

Tracce, video e report delle prove fallite restano in `target/e2e` e `target/e2e-report`.

## Prima esecuzione

I selettori delle prove usano ruoli ed etichette accessibili, non classi CSS, ma non sono ancora
stati eseguiti contro un'istanza reale: la prima esecuzione va fatta a mano, correggendo le
etichette che l'ambiente mostra in modo diverso (in particolare la pagina di accesso di Keycloak e
la scelta del ruolo). Finché quella calibrazione non è registrata, la suite non è un gate di
rilascio.
