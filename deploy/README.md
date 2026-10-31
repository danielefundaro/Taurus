# Manifest di produzione di Taurus

Lo stack completo su un host singolo con Docker Compose: PostgreSQL, Keycloak, RabbitMQ, il servizio
OMR, il backend e il frontend, più il sito informativo come profilo facoltativo. È la forma adatta a
un'associazione senza infrastruttura: un server, nessun servizio a pagamento, niente orchestratore.

Le procedure operative — gate di deploy, sequenza, verifica delle migrazioni, smoke test, rollback,
backup, incidenti — stanno nel [Runbook operativo](../docs/runbook-operativo.md). Qui ci sono i file
e le scelte che li rendono eseguibili.

## Com'è fatto

- **Una sola origine pubblica.** Il container del frontend serve l'applicazione e inoltra `/api` al
  backend e `/auth` a Keycloak. Il browser parla con un indirizzo solo: niente CORS, una
  Content-Security-Policy semplice e nessun nome della rete interna dentro il bundle.
- **TLS sull'host.** L'unica porta pubblicata è quella del frontend, legata al loopback. Davanti sta
  il reverse proxy dell'host, che termina TLS e dichiara HSTS: l'esempio è in
  [`reverse-proxy.example.conf`](reverse-proxy.example.conf), e il certificato è di Let's Encrypt.
- **Rete interna.** PostgreSQL, Keycloak, RabbitMQ e il servizio OMR non pubblicano porte: parlano
  solo con gli altri container.
- **Configurazione a runtime.** L'immagine del frontend è costruita una volta e configurata
  all'avvio: scrive `assets/config.json` dalle variabili d'ambiente, quindi gli indirizzi dell'API e
  di Keycloak non sono fissati nel bundle.
- **Dati su volumi.** `taurus-db`, `taurus-rabbit` e `taurus-media` sopravvivono a un `down`. Il
  backend gira senza privilegi e lo storage dei media appartiene al suo utente.
- **Versioni fissate.** Le immagini arrivano da `TAURUS_*_VERSION`; per fissare anche il digest si
  usa la forma `1.2.3@sha256:…`.
- **Segreti fuori dal repository.** Tutto passa da `.env`, che non è versionato: nel repository vive
  solo [`.env.example`](.env.example).

## Primo insediamento

1. **Preparare i file.**

   ```bash
   cd deploy
   cp .env.example .env && chmod 600 .env
   cp /percorso/keycloak-authenticator-*.jar keycloak/providers/
   cp /percorso/taurus-realm.json keycloak/realm/
   ```

   Il JAR del provider porta con sé anche il tema `taurus-theme`, che non va montato a parte.

2. **Compilare `.env`.** Le variabili senza valore predefinito sono obbligatorie: Compose si ferma
   dicendo quale manca. Le chiavi VAPID si generano con `npx web-push generate-vapid-keys`.

3. **Importare il realm, una volta sola.**

   ```bash
   docker compose --env-file .env -f compose.yml run --rm keycloak start --import-realm
   ```

   Il realm esportato dallo sviluppo contiene indirizzi e client di sviluppo: prima dell'import va
   adattato all'installazione — `loginTheme` ed `emailTheme` su `taurus-theme`, redirect URI e web
   origins sull'indirizzo pubblico, il segreto del client `web_app` uguale a
   `TAURUS_OIDC_CLIENT_SECRET`.

4. **Avviare lo stack.**

   ```bash
   docker compose --env-file .env -f compose.yml up -d
   ```

   Il backend applica Liquibase all'avvio: una replica sola, come fa questo manifest.

5. **Verificare.** Dall'host:

   ```bash
   ../scripts/ops/smoke-test.sh https://taurus.esempio.it
   ```

   Lo script non scrive nulla: controlla salute, pagine, intestazioni e metadati del realm.

## Aggiornamento

```bash
cd deploy
# nuova versione in .env
docker compose --env-file .env -f compose.yml pull
docker compose --env-file .env -f compose.yml up -d
../scripts/ops/smoke-test.sh https://taurus.esempio.it
```

Il backup del database e dello storage media va fatto prima, come dice il runbook. Se la release
cambia gli argomenti delle code RabbitMQ, il runbook descrive la cancellazione delle code di lavoro
prima dell'avvio.

## Quello che questo manifest non risolve

- **Validazione su un host reale.** Il manifest non è ancora stato eseguito su un server di
  produzione: finché non lo è, il gap resta aperto nel runbook operativo.
- **Backup e ripristino.** `scripts/ops/restore-test.sh` prova il ripristino del database, ma la
  programmazione dei backup, la loro conservazione e la prova su Keycloak e sui media appartengono
  all'host.
- **Più repliche del backend.** Questo manifest ne avvia una. Gli scheduler sono sicuri con più
  repliche — ogni coda a tempo ha la sua presa in carico condizionata — ma le migrazioni vanno
  applicate da un'istanza per volta.
- **Monitoraggio.** Le metriche sono su `/management/prometheus`, raggiungibile solo dalla rete
  interna: la raccolta e gli allarmi appartengono all'host.
