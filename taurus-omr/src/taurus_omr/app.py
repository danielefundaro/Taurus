"""HTTP API of the service, reachable only on the internal network and protected by a shared key."""

from __future__ import annotations

import base64
import hmac
import logging
import tempfile
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from importlib import metadata
from pathlib import Path
from typing import Annotated

from fastapi import Depends, FastAPI, File, Form, Request, UploadFile
from fastapi.responses import JSONResponse, Response
from PIL import Image

from . import __version__, images, midi, musicxml
from .config import Settings
from .engines import AUDIVERIS, HOMR, Engine, default_engines
from .errors import Busy, EngineFailure, LengthRequired, OmrError, RequestTooLarge, TooManyPages, Unauthorized, UnknownEngine

log = logging.getLogger("taurus_omr")
API_KEY_HEADER = "X-Taurus-Omr-Key"
MUSICXML_MEDIA_TYPE = "application/vnd.recordare.musicxml+xml"
# Room for the multipart boundaries and headers around the files.
_MULTIPART_OVERHEAD = 64 * 1024


def create_app(settings: Settings | None = None, engines: dict[str, Engine] | None = None) -> FastAPI:
    settings = settings or Settings.from_env()
    _configure_logging()
    engines = engines if engines is not None else default_engines(settings)
    slots = threading.BoundedSemaphore(settings.concurrency)
    app = FastAPI(title="Taurus OMR", version=__version__, docs_url=None, redoc_url=None, openapi_url=None)

    @app.exception_handler(OmrError)
    async def refused(_: Request, error: OmrError) -> JSONResponse:
        return JSONResponse(status_code=error.status, content={"code": error.code, "message": error.message})

    def authenticated(request: Request) -> None:
        provided = request.headers.get(API_KEY_HEADER, "")
        if not hmac.compare_digest(provided.encode(), settings.api_key.encode()):
            raise Unauthorized("missing or wrong service key")

    def limited(limit: int):
        def check(request: Request) -> None:
            length = request.headers.get("content-length")
            if length is None:
                raise LengthRequired("Content-Length is required")
            if not length.isdigit() or int(length) > limit + _MULTIPART_OVERHEAD:
                raise RequestTooLarge(f"request larger than {limit} bytes")

        return check

    @app.get("/v1/health")
    def health() -> dict[str, object]:
        return {
            "status": "UP",
            "service": __version__,
            "engines": {name: engine.version for name, engine in engines.items()},
            "music21": _version("music21"),
        }

    @app.post(
        "/v1/recognitions",
        dependencies=[Depends(authenticated), Depends(limited(settings.max_request_bytes))],
    )
    def recognitions(
        pages: Annotated[list[UploadFile], File(description="Page images in reading order")],
        requested: Annotated[list[str] | None, Form(alias="engines")] = None,
    ) -> dict[str, object]:
        names = requested or [AUDIVERIS, HOMR]
        unknown = [name for name in names if name not in engines]
        if unknown:
            raise UnknownEngine(f"unknown engines {unknown}")
        if len(pages) > settings.max_pages:
            raise TooManyPages(f"at most {settings.max_pages} pages")
        contents = [page.file.read() for page in pages]
        if sum(len(content) for content in contents) > settings.max_request_bytes:
            raise RequestTooLarge(f"pages larger than {settings.max_request_bytes} bytes")
        decoded = [images.normalize(content, settings.max_image_pixels) for content in contents]
        with _slot(slots, settings.engine_timeout_seconds), tempfile.TemporaryDirectory(dir=settings.work_dir, prefix="omr-") as work:
            with ThreadPoolExecutor(max_workers=len(names)) as pool:
                futures = [pool.submit(_recognize, engines[name], decoded, Path(work) / name.lower()) for name in dict.fromkeys(names)]
                results = [future.result() for future in futures]
        log.info("recognition of %d pages: %s", len(pages), ", ".join(f"{r['engine']} {r['status']} {r['durationMillis']} ms" for r in results))
        return {"results": results}

    @app.post(
        "/v1/midi-conversions",
        dependencies=[Depends(authenticated), Depends(limited(settings.max_midi_bytes))],
    )
    def midi_conversions(file: Annotated[UploadFile, File(description="Standard MIDI file")]) -> Response:
        data = file.file.read(settings.max_midi_bytes + 1)
        if len(data) > settings.max_midi_bytes:
            raise RequestTooLarge(f"MIDI file larger than {settings.max_midi_bytes} bytes")
        with _slot(slots, settings.engine_timeout_seconds):
            converted = midi.to_musicxml(data)
        return Response(content=converted, media_type=MUSICXML_MEDIA_TYPE)

    return app


def _recognize(engine: Engine, pages: list[Image.Image], work: Path) -> dict[str, object]:
    work.mkdir(parents=True)
    started = time.monotonic()
    result: dict[str, object] = {"engine": engine.name, "version": engine.version}
    try:
        recognition = engine.recognize(pages, work)
        result |= {
            "status": "DONE",
            "errorCode": None,
            "musicXml": base64.b64encode(musicxml.to_compressed(recognition.score)).decode("ascii"),
            "measureCount": musicxml.measure_count(recognition.score),
            "layout": None if recognition.layout is None else [box.to_json() for box in recognition.layout],
        }
    except EngineFailure as failure:
        log.warning("engine %s failed: %s", engine.name, failure)
        result |= _failed(failure.code)
    except Exception:  # an unexpected engine output must not cancel the result of the other engine
        log.exception("engine %s failed unexpectedly", engine.name)
        result |= _failed(EngineFailure.code)
    result["durationMillis"] = int((time.monotonic() - started) * 1000)
    return result


def _failed(code: str) -> dict[str, object]:
    return {"status": "FAILED", "errorCode": code, "musicXml": None, "measureCount": None, "layout": None}


class _slot:
    """Waits for a free engine slot: recognitions are CPU heavy and run at most ``concurrency`` at a time."""

    def __init__(self, slots: threading.BoundedSemaphore, timeout_seconds: int) -> None:
        self._slots = slots
        self._timeout = timeout_seconds

    def __enter__(self) -> None:
        if not self._slots.acquire(timeout=self._timeout):
            raise Busy("all engine slots are busy")

    def __exit__(self, *_: object) -> None:
        self._slots.release()


def _configure_logging() -> None:
    if not log.handlers:
        handler = logging.StreamHandler()
        handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(name)s %(message)s"))
        log.addHandler(handler)
        log.setLevel(logging.INFO)


def _version(package: str) -> str:
    try:
        return metadata.version(package)
    except metadata.PackageNotFoundError:
        return "unknown"
