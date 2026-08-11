# Taurus Info

Sito-vetrina statico del prodotto Taurus, realizzato con Astro, TypeScript e Tailwind CSS e distribuito tramite Nginx.

## Requisiti

- Node.js 22
- pnpm 11
- Docker e Docker Compose, per l'esecuzione containerizzata

## Sviluppo locale

```bash
pnpm install
pnpm dev
```

Il sito è disponibile su `http://localhost:4321`.

## Configurazione

Copiare `.env.example` in `.env` e valorizzare:

| Variabile | Descrizione |
| --- | --- |
| `SITE_URL` | URL canonico del sito, usato da sitemap e metadata SEO |
| `PUBLIC_APP_URL` | URL della web app Taurus aperto dai pulsanti “Accedi” |
| `PUBLIC_CONTACT_EMAIL` | Indirizzo pubblico per le richieste informative |

Le variabili vengono incorporate nel sito durante la build: per modificarle è necessario ricostruire gli asset.

## Controlli e build

```bash
pnpm check
pnpm build
pnpm preview
```

L'output statico viene scritto in `dist/`.

## Docker

```bash
docker compose up --build
```

Il sito è disponibile su `http://localhost:8088`; l'health check risponde su `/health`.

Per una build esplicita:

```bash
docker build \
  --build-arg SITE_URL=https://www.taurus.it \
  --build-arg PUBLIC_APP_URL=https://app.taurus.it \
  --build-arg PUBLIC_CONTACT_EMAIL=admin@taurus.it \
  -t taurus-info:latest .
```

## Pubblicazione

Nginx applica:

- cache immutabile agli asset Astro versionati;
- HTML senza cache persistente;
- compressione gzip;
- fallback 404 statico;
- header di sicurezza e Content Security Policy.

Il TLS deve essere terminato dal reverse proxy o dall'ingress dell'ambiente di destinazione.

## Verifiche prima della produzione

Le pagine Privacy e Termini contengono una base editoriale coerente con il sito statico. Prima della pubblicazione devono essere revisionate dal referente legale e integrate con i dati identificativi completi del titolare del servizio.
