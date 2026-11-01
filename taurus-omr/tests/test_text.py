from __future__ import annotations

import os
import sys
import threading
import time
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from taurus_omr import text
from taurus_omr.app import API_KEY_HEADER, create_app
from taurus_omr.reading import TextLine, reading_order

from .conftest import png, settings

SOURCES = Path(__file__).parent.parent / "src"


def pages(*blank: bool) -> list[tuple[str, tuple[str, bytes, str]]]:
    return [("pages", (f"page-{index}.png", png() if empty else png(noise=True), "image/png")) for index, empty in enumerate(blank)]


def test_health_reports_the_text_engine(client: TestClient) -> None:
    engine = client.get("/v1/health").json()["textEngine"]

    assert engine["name"] == "RAPIDOCR"
    assert engine["version"]


def test_reads_the_lines_of_each_page_in_reading_order(client: TestClient, headers: dict[str, str]) -> None:
    response = client.post("/v1/text-recognitions", files=pages(False, True), headers=headers)

    assert response.status_code == 200
    body = response.json()
    assert body["engine"] == "RAPIDOCR"
    assert body["durationMillis"] >= 0
    assert [page["page"] for page in body["pages"]] == [1, 2]
    lines = body["pages"][0]["lines"]
    assert [line["text"] for line in lines] == ["Corale e marcia", "Clarinetto 1° in Sib", "= 92", "1", "Ed. Musicali"]
    assert lines[1] == {"text": "Clarinetto 1° in Sib", "confidence": 0.94, "x": 0.05, "y": 0.1, "width": 0.22, "height": 0.02}
    clipped = lines[-1]
    assert clipped["confidence"] == 1.0
    assert clipped["x"] + clipped["width"] == pytest.approx(1.0)
    assert clipped["y"] + clipped["height"] == pytest.approx(1.0)
    assert body["pages"][1]["lines"] == []


def test_keeps_nothing_after_the_request(tmp_path: Path, headers: dict[str, str]) -> None:
    client = TestClient(create_app(settings(tmp_path)))

    client.post("/v1/text-recognitions", files=pages(False), headers=headers)

    assert list(tmp_path.iterdir()) == []


@pytest.mark.parametrize("key", [None, "wrong-key-0123456789"])
def test_requires_the_service_key(client: TestClient, key: str | None) -> None:
    response = client.post("/v1/text-recognitions", files=pages(False), headers={} if key is None else {API_KEY_HEADER: key})

    assert response.status_code == 401
    assert response.json()["code"] == "unauthorized"


def test_applies_the_limits_of_the_service(tmp_path: Path, headers: dict[str, str]) -> None:
    few_pages = TestClient(create_app(settings(tmp_path, max_pages=1)))
    small = TestClient(create_app(settings(tmp_path, max_request_bytes=1)))
    few_pixels = TestClient(create_app(settings(tmp_path, max_image_pixels=100)))

    assert few_pages.post("/v1/text-recognitions", files=pages(False, False), headers=headers).json()["code"] == "tooManyPages"
    assert small.post("/v1/text-recognitions", files=pages(False), headers=headers).status_code == 413
    assert few_pixels.post("/v1/text-recognitions", files=pages(False), headers=headers).json()["code"] == "unreadableImage"


def test_refuses_files_that_are_not_images(client: TestClient, headers: dict[str, str]) -> None:
    response = client.post("/v1/text-recognitions", files=[("pages", ("page.png", b"not an image", "image/png"))], headers=headers)

    assert response.status_code == 422
    assert response.json()["code"] == "unreadableImage"


@pytest.mark.parametrize("mode", ["fail", "garbage", "short"])
def test_answers_500_when_the_engine_fails(client: TestClient, headers: dict[str, str], monkeypatch: pytest.MonkeyPatch, mode: str) -> None:
    monkeypatch.setenv("FAKE_TEXT", mode)

    response = client.post("/v1/text-recognitions", files=pages(False, False), headers=headers)

    assert response.status_code == 500
    assert response.json()["code"] == "engineFailed"


def test_stops_a_reading_that_exceeds_the_timeout(tmp_path: Path, headers: dict[str, str], monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("FAKE_TEXT", "sleep")
    client = TestClient(create_app(settings(tmp_path, text_timeout_seconds=1)))
    started = time.monotonic()

    response = client.post("/v1/text-recognitions", files=pages(False), headers=headers)

    assert response.status_code == 500
    assert response.json()["code"] == "engineTimeout"
    assert time.monotonic() - started < 15


def test_a_reading_does_not_wait_for_a_running_recognition(tmp_path: Path, headers: dict[str, str], monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("FAKE_AUDIVERIS", "sleep")
    client = TestClient(create_app(settings(tmp_path, engine_timeout_seconds=8, concurrency=1)))
    recognition = threading.Thread(
        target=lambda: client.post("/v1/recognitions", files=pages(False), data={"engines": ["AUDIVERIS"]}, headers=headers)
    )
    recognition.start()
    time.sleep(1)
    try:
        started = time.monotonic()
        response = client.post("/v1/text-recognitions", files=pages(False), headers=headers)
        elapsed = time.monotonic() - started
        assert recognition.is_alive()
    finally:
        recognition.join()

    assert response.status_code == 200
    assert elapsed < 6


def test_a_missing_model_fails_without_downloading(tmp_path: Path, headers: dict[str, str], monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("PYTHONPATH", os.pathsep.join([str(SOURCES), os.environ.get("PYTHONPATH", "")]))
    command = (sys.executable, "-m", "taurus_omr.text", "--models", str(tmp_path / "no-models"))
    client = TestClient(create_app(settings(tmp_path, text_command=command)))

    response = client.post("/v1/text-recognitions", files=pages(False), headers=headers)

    assert response.status_code == 500
    assert response.json()["code"] == "engineFailed"


def test_the_text_module_checks_every_model_before_reading(tmp_path: Path, capsys: pytest.CaptureFixture[str]) -> None:
    models = tmp_path / "models"
    models.mkdir()
    (models / text.DETECTION).write_bytes(b"")
    output = tmp_path / "lines.json"

    code = text.main(["--models", str(models), str(tmp_path), str(output)])

    assert code == text.MISSING_MODEL_EXIT
    assert not output.exists()
    assert text.RECOGNITION in capsys.readouterr().err


def test_reading_order_joins_the_lines_of_a_row() -> None:
    def line(name: str, x: float, y: float, height: float = 0.02) -> TextLine:
        return TextLine(name, 1.0, x, y, 0.1, height)

    ordered = reading_order([line("c", 0.1, 0.5), line("b", 0.6, 0.208, 0.03), line("a", 0.2, 0.2), line("top", 0.9, 0.01)])

    assert [item.text for item in ordered] == ["top", "a", "b", "c"]
