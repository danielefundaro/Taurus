# Taurus frontend

Applicazione Angular 19 di Taurus per utenti e amministratori, con componenti PrimeNG 19 e autenticazione Keycloak (`keycloak-angular`). Il progetto nasce dal template Sakai.

## Struttura

| Percorso | Contenuto |
| --- | --- |
| `src/app/pages` | pagine, una cartella per funzionalità |
| `src/app/components` | componenti condivisi del [Taurus Layout Standard](../docs/taurus-layout-standard.md) |
| `src/app/dialogs` | dialoghi |
| `src/app/service` | accesso alle API del backend |
| `src/app/constants` | messaggi d'errore e comportamenti dello [Standard degli errori applicativi](../docs/application-errors-standard.md) |
| `src/app/score-player`, `src/app/score-editor` | lettore ed editor dell'[ascolto delle parti](../docs/track-score-playback-spec.md) |
| `src/assets` | risorse statiche, compresi Verovio, SpessaSynth e il SoundFont |
| `src/environments` | indirizzi di backend e Keycloak: `environment.ts` per lo sviluppo, `environment.prod.ts` per la build di produzione |

## Sviluppo

Con Node.js 22 e i servizi del backend avviati (vedi il [README principale](../README.md)):

```bash
npm ci
npm start
```

L'applicazione è su `http://localhost:4200` e chiama il backend su `http://localhost:8080/api` e Keycloak su `http://localhost:9080`.

`npm start` e `npm run build` eseguono prima `scripts/fetch-soundfont.mjs`, che scarica il SoundFont MuseScore General (circa 38 MB, non versionato) e ne verifica lo SHA-256. `TAURUS_SKIP_SOUNDFONT=1` salta il download, per esempio nei job che non usano il lettore.

## Comandi

| Comando | Effetto |
| --- | --- |
| `npm start` | server di sviluppo |
| `npm run build` | build di produzione in `dist/sakai-ng/browser` |
| `npm test -- --watch=false --browsers=ChromeHeadless` | test Karma/Jasmine, una sola esecuzione |
| `npm run format` | formattazione Prettier |

I test stanno accanto ai file che verificano, come `*.spec.ts`.

## Convenzioni

TypeScript strict, indentazione a quattro spazi, apici singoli e punto e virgola, formattazione con Prettier. I file seguono il kebab case con il suffisso del ruolo, per esempio `inventory.service.ts`. Per i componenti PrimeNG si consulta `docs/llms-full.md`, limitandosi alle sezioni del componente usato.

## Immagine

Il `Dockerfile` costruisce l'applicazione con Node.js 22 e la serve con nginx. Il workflow `docker-publish.yml` pubblica l'immagine come `taurus-frontend` a ogni tag `v*.*.*`.
