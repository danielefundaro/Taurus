# Componenti di terze parti

Il servizio esegue i motori come programmi separati e non modificati. I sorgenti delle versioni incluse nell'immagine sono disponibili agli indirizzi indicati.

| Componente | Versione | Licenza | Sorgenti | Uso |
| --- | --- | --- | --- | --- |
| Audiveris | 5.11.0 | AGPL-3.0 | https://github.com/Audiveris/audiveris/tree/5.11.0 | riconoscimento, processo separato |
| homr | 0.7.0 | AGPL-3.0 | https://github.com/liebharc/homr (pacchetto `homr` 0.7.0 su PyPI) | riconoscimento, processo separato |
| Modelli di homr (`onnx_checkpoints`) | segnet 308, transformer 396 | AGPL-3.0; dati di addestramento con licenze diverse, tra cui Camera-PrIMuS (CC BY-NC 4.0) | https://github.com/liebharc/homr/releases/tag/onnx_checkpoints | ammessi perché Taurus non è commerciale |
| RapidOCR e modelli PP-OCR | 3.10.0 | Apache-2.0 | https://github.com/RapidAI/RapidOCR | lettura del titolo in homr, non usata da Taurus |
| Dati Tesseract (`eng`, `ita`) | 4.1.0 | Apache-2.0 | https://github.com/tesseract-ocr/tessdata/tree/4.1.0 | lettura dei testi in Audiveris |
| music21 | 9.7.1 | BSD-3-Clause | https://github.com/cuthbertLab/music21 | conversione MIDI → MusicXML |
| FastAPI, Starlette, Uvicorn | vedi `requirements.txt` | MIT, BSD-3-Clause | https://github.com/fastapi/fastapi | API HTTP |
| Pillow | 12.3.0 | MIT-CMU | https://github.com/python-pillow/Pillow | lettura delle immagini |

Le versioni esatte di tutte le dipendenze Python sono in `requirements.txt`; le impronte SHA-256 di modelli e dati sono in `models.sha256` e quella del pacchetto Audiveris nel `Dockerfile`.
