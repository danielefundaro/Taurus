# Taurus Info

Sito-vetrina statico del gestionale Taurus per organizzazioni musicali, realizzato con Astro, TypeScript e Tailwind CSS e distribuito tramite Nginx.

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
  --build-arg SITE_URL=https://info-taurus.it \
  --build-arg PUBLIC_APP_URL=https://app-taurus.it \
  --build-arg PUBLIC_CONTACT_EMAIL=ing.daniele.fundaro@gmail.com \
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

## Documenti legali dell'applicativo

Il sito pubblica separatamente i documenti destinati agli utenti della web app:

- `/privacy-applicativo/` per l'informativa privacy, da registrare in Taurus come tipo `PRIVACY` e azione `ACKNOWLEDGE`;
- `/termini-applicativo/` per i termini di utilizzo, da registrare come tipo `TERMS` e azione `ACCEPT`.

Le versioni pubblicate nel sito sono attualmente la **1.1 del 3 settembre 2026**.

Dopo la pubblicazione del sito, un super amministratore deve aprire **Documenti legali** nella web app e creare le due versioni indicando gli URL pubblici completi, la versione, la data di pubblicazione e lo stato attivo. I record non devono essere inseriti manualmente nel database.

## Verifiche prima della produzione

Le pagine Privacy e Termini costituiscono una base editoriale coerente con le informazioni disponibili. Prima della pubblicazione è comunque opportuna una revisione professionale, soprattutto per i ruoli privacy dei tenant, il trattamento dei dati dei minori e l'ubicazione effettiva dei server Contabo.
