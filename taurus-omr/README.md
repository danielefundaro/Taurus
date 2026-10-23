# Taurus OMR

Servizio interno di riconoscimento ottico della musica per l'[ascolto delle parti](../docs/track-score-playback-spec.md). Riceve le pagine di una parte, le fa leggere ad Audiveris e a homr e restituisce per ogni motore un MusicXML compresso, con le posizioni delle battute quando il motore le fornisce. Converte anche file MIDI in MusicXML con music21.

Il servizio non conserva nulla, non conosce tenant, tracce o utenti e va esposto solo sulla rete interna, con una chiave condivisa.

## API

| Metodo e percorso | Descrizione |
| --- | --- |
| `POST /v1/recognitions` | multipart con le pagine (`pages`, PNG, JPEG o WebP, in ordine di lettura) e i motori (`engines`, predefiniti `AUDIVERIS` e `HOMR`); risponde al termine con un risultato per motore |
| `POST /v1/midi-conversions` | multipart con il file MIDI (`file`); risponde con il MusicXML, una parte per traccia |
| `GET /v1/health` | stato e versioni; non richiede la chiave |

Le richieste protette portano la chiave nell'intestazione `X-Taurus-Omr-Key`. Gli errori rispondono con `{ "code", "message" }`: `unauthorized` (401), `lengthRequired` (411), `tooLarge` (413), `tooManyPages`, `unreadableImage`, `unknownEngine`, `unreadableMidi` (422), `busy` (503). L'esito di un motore fallito è nel risultato, con `errorCode` `engineFailed` o `engineTimeout`.

## Configurazione

| Variabile | Predefinito | Significato |
| --- | --- | --- |
| `TAURUS_OMR_API_KEY` | obbligatoria | chiave condivisa con il backend, almeno 16 caratteri |
| `TAURUS_OMR_ENGINE_TIMEOUT_SECONDS` | `300` | tempo massimo di ogni motore |
| `TAURUS_OMR_MAX_PAGES` | `30` | pagine per richiesta |
| `TAURUS_OMR_MAX_REQUEST_MB` | `25` | dimensione massima delle pagine |
| `TAURUS_OMR_MAX_MIDI_MB` | `10` | dimensione massima di un file MIDI |
| `TAURUS_OMR_MAX_IMAGE_PIXELS` | `60000000` | pixel massimi di una pagina, controllati prima della decodifica (`unreadableImage`) |
| `TAURUS_OMR_CONCURRENCY` | `1` | richieste elaborate insieme; le altre attendono |

L'immagine imposta anche i comandi dei motori, la versione di Audiveris e la cartella di lavoro (`TAURUS_OMR_AUDIVERIS_COMMAND`, `TAURUS_OMR_AUDIVERIS_VERSION`, `TAURUS_OMR_HOMR_COMMAND`, `TAURUS_OMR_WORK_DIR`): servono ai test e non vanno cambiati nell'immagine.

## Sviluppo

I test sostituiscono i motori con programmi finti e non richiedono Audiveris né homr. Con Python 3.12:

```bash
python -m venv .venv
.venv/bin/pip install -r requirements-test.txt
.venv/bin/python -m pytest
```

## Accuratezza

La cartella `accuracy` misura quanto leggono bene i motori reali. Il corpus (`accuracy/corpus`) contiene sei parti scritte per il test, simili a parti per banda (tromba, corno con divisi e pause di più battute, tuba in chiave di basso con D.C. al Fine, clarinetto in 6/8, flauto con sedicesimi e terzine, e la stessa marcia come fotocopia consumata), con il MusicXML di riferimento e le pagine A4 a 300 DPI disegnate con Verovio. Il confronto segue le regole del lettore di Taurus: una battuta è corretta se si ascolta uguale.

```bash
# con il servizio in esecuzione, per esempio quello di services.yml
.venv/bin/python -m accuracy run --url http://localhost:8090 --key <chiave> --save /tmp/letture
# una parte reale: versione approvata e versione di un motore, scaricate da Taurus
.venv/bin/python -m accuracy compare approvata.mxl audiveris.mxl
```

`tests/test_accuracy.py` fissa la quota minima di battute corrette per motore e si esegue solo con `TAURUS_OMR_ACCURACY_URL` e `TAURUS_OMR_ACCURACY_KEY`; la CI lo esegue sull'immagine appena costruita. Per aggiungere parti al corpus si modifica `accuracy/corpus.py` e si ridisegnano le pagine con `python -m accuracy render`, che richiede `requirements-accuracy.txt`, la libreria Cairo e un carattere compatibile con Times (si veda il [Runbook operativo](../docs/runbook-operativo.md)).

## Immagine

L'immagine completa si costruisce con `docker build -t taurus-omr .`; il file `taurus-be/src/main/docker/omr.yml` la avvia con le restrizioni previste (file system di sola lettura con `/tmp` in memoria, nessun privilegio aggiuntivo, limiti di CPU e memoria, porta pubblicata solo su `127.0.0.1`) ed è incluso in `services.yml`, che avvia tutte le dipendenze del backend. Il workflow `docker-publish.yml` pubblica l'immagine su Docker Hub come `fundanie/taurus-omr` a ogni tag `v*.*.*`.

Licenze e sorgenti dei componenti: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
