# Taurus Info

Sito-vetrina statico del gestionale Taurus per organizzazioni musicali, realizzato con Astro, TypeScript e Tailwind CSS e distribuito tramite Nginx.

## Requisiti

- Node.js 26
- pnpm 12, alla versione di `packageManager` in `package.json`: Node.js 26 non include più corepack, e pnpm si installa con `npm install --global pnpm@<versione>`
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

Il sito è disponibile su `http://localhost:8088`; l'health check risponde su `/health`. Nel container nginx gira senza privilegi, con l'utente `nginx` (uid 101), e ascolta sulla porta `8080`: un `docker run` a mano pubblica quella, per esempio con `-p 127.0.0.1:8088:8080`.

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

I termini di utilizzo e l'informativa privacy dell'applicativo si scrivono, si versionano e si pubblicano in Taurus: un super amministratore li gestisce in **Documenti legali**, e ogni versione pubblicata ha una pagina pubblica nell'applicativo, leggibile senza login.

Il sito non contiene più quei testi:

- `/termini-applicativo/` e `/privacy-applicativo/` rimandano subito a `${PUBLIC_APP_URL}/legal/terms` e `${PUBLIC_APP_URL}/legal/privacy`, con il canonical verso l'applicativo, un collegamento visibile e `noindex`; restano fuori dalla sitemap, così i vecchi indirizzi nei segnalibri e nelle e-mail continuano a funzionare;
- il piè di pagina, `termini.astro` e `privacy.astro` collegano direttamente le pagine dell'applicativo.

Il sito con i rimandi si pubblica solo dopo che i testi sono in vigore nell'applicativo. Le ultime versioni pubblicate nel sito, la **1.1 del 3 settembre 2026** per l'informativa privacy e la **1.2 del 7 settembre 2026** per i termini, restano nella cronologia Git di questo modulo: da lì il super amministratore copia il testo di una versione registrata in Taurus con il solo indirizzo, che riceve il testo una sola volta. Il procedimento è descritto in `docs/legal-documents-spec.md`.

## Verifiche prima della produzione

Le pagine Privacy e Termini costituiscono una base editoriale coerente con le informazioni disponibili. Prima della pubblicazione è comunque opportuna una revisione professionale, soprattutto per i ruoli privacy dei tenant, il trattamento dei dati dei minori e l'ubicazione effettiva dei server Contabo.
