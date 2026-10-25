# Suite end to end

Guida un browser reale contro un'istanza completa di Taurus: frontend, backend, Keycloak,
PostgreSQL e RabbitMQ. È la suite chiesta dal [Runbook di test](../../docs/runbook-test.md) per i
percorsi con ruoli e tenant multipli, e non sostituisce i test di componente di `npm test`.

## Prerequisiti

1. Dipendenze avviate: `docker compose -f ../taurus-be/src/main/docker/services.yml up --wait`.
2. Backend in esecuzione: `cd ../taurus-be && ./mvnw`.
3. Frontend in esecuzione: `npm start` (serve su `http://localhost:4200`).
4. Browser di Playwright installati una volta sola: `npm run e2e:install`.
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

I ruoli ammessi sono `SUPERADMIN`, `ADMIN`, `ARCHIVIST`, `USER`, `EXTERNAL` e `TREASURER`.

## Esecuzione

```bash
npm run e2e            # entrambi i profili, desktop e mobile
npm run e2e -- --project=desktop
npm run e2e -- --headed --project=desktop
```

Tracce, video e report delle prove fallite restano in `target/e2e` e `target/e2e-report`.

## Prima esecuzione

I selettori delle prove usano ruoli ed etichette accessibili, non classi CSS, ma non sono ancora
stati eseguiti contro un'istanza reale: la prima esecuzione va fatta a mano, correggendo le
etichette che l'ambiente mostra in modo diverso (in particolare la pagina di accesso di Keycloak e
la scelta del ruolo). Finché quella calibrazione non è registrata, la suite non è un gate di
rilascio.
