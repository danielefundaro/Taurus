from __future__ import annotations

import base64
import io
import time
import zipfile
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from music21 import midi, note, stream

from taurus_omr.app import API_KEY_HEADER, create_app

from .conftest import API_KEY, png, settings


def pages(count: int) -> list[tuple[str, tuple[str, bytes, str]]]:
    return [("pages", (f"page-{index}.png", png(), "image/png")) for index in range(count)]


def test_health_reports_versions_without_key(client: TestClient) -> None:
    response = client.get("/v1/health")

    assert response.status_code == 200
    assert response.json()["status"] == "UP"
    assert set(response.json()["engines"]) == {"AUDIVERIS", "HOMR"}


@pytest.mark.parametrize("key", [None, "wrong-key-0123456789"])
def test_recognitions_require_the_service_key(client: TestClient, key: str | None) -> None:
    headers = {} if key is None else {API_KEY_HEADER: key}

    response = client.post("/v1/recognitions", files=pages(1), headers=headers)

    assert response.status_code == 401
    assert response.json()["code"] == "unauthorized"


def test_runs_both_engines_and_joins_the_pages(client: TestClient, headers: dict[str, str]) -> None:
    response = client.post("/v1/recognitions", files=pages(2), headers=headers)

    assert response.status_code == 200
    results = {result["engine"]: result for result in response.json()["results"]}
    audiveris = results["AUDIVERIS"]
    assert audiveris["status"] == "DONE"
    assert audiveris["errorCode"] is None
    assert audiveris["measureCount"] == 4
    assert [box["page"] for box in audiveris["layout"]] == [1, 1, 2, 2]
    assert not any(box["approximate"] for box in audiveris["layout"])
    homr = results["HOMR"]
    assert homr["status"] == "DONE"
    assert homr["measureCount"] == 4
    assert homr["layout"] is None
    with zipfile.ZipFile(io.BytesIO(base64.b64decode(homr["musicXml"]))) as archive:
        document = archive.read("score.musicxml").decode()
    assert "Lento 50" not in document
    assert '<measure number="4">' in document


def test_runs_only_the_requested_engines(client: TestClient, headers: dict[str, str]) -> None:
    response = client.post("/v1/recognitions", files=pages(1), data={"engines": ["HOMR"]}, headers=headers)

    assert [result["engine"] for result in response.json()["results"]] == ["HOMR"]


def test_joins_the_movements_exported_by_audiveris(client: TestClient, headers: dict[str, str], monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("FAKE_AUDIVERIS", "movements")

    response = client.post("/v1/recognitions", files=pages(3), data={"engines": ["AUDIVERIS"]}, headers=headers)

    result = response.json()["results"][0]
    assert result["measureCount"] == 6
    assert len(result["layout"]) == 6


@pytest.mark.parametrize(("mode", "code"), [("fail", "engineFailed"), ("empty", "engineFailed")])
def test_reports_a_failed_engine_without_losing_the_other(
    client: TestClient, headers: dict[str, str], monkeypatch: pytest.MonkeyPatch, mode: str, code: str
) -> None:
    monkeypatch.setenv("FAKE_AUDIVERIS", mode)

    response = client.post("/v1/recognitions", files=pages(1), headers=headers)

    results = {result["engine"]: result for result in response.json()["results"]}
    assert results["AUDIVERIS"]["status"] == "FAILED"
    assert results["AUDIVERIS"]["errorCode"] == code
    assert results["AUDIVERIS"]["musicXml"] is None
    assert results["HOMR"]["status"] == "DONE"


def test_fails_homr_when_a_page_has_no_result(client: TestClient, headers: dict[str, str], monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("FAKE_HOMR", "missing-page")

    response = client.post("/v1/recognitions", files=pages(2), data={"engines": ["HOMR"]}, headers=headers)

    assert response.json()["results"][0]["errorCode"] == "engineFailed"


def test_stops_an_engine_that_exceeds_the_timeout(tmp_path: Path, headers: dict[str, str], monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("FAKE_AUDIVERIS", "sleep")
    client = TestClient(create_app(settings(tmp_path, engine_timeout_seconds=1)))
    started = time.monotonic()

    response = client.post("/v1/recognitions", files=pages(1), data={"engines": ["AUDIVERIS"]}, headers=headers)

    assert response.json()["results"][0]["errorCode"] == "engineTimeout"
    assert time.monotonic() - started < 15


def test_refuses_too_many_pages(tmp_path: Path, headers: dict[str, str]) -> None:
    client = TestClient(create_app(settings(tmp_path, max_pages=2)))

    response = client.post("/v1/recognitions", files=pages(3), headers=headers)

    assert response.status_code == 422
    assert response.json()["code"] == "tooManyPages"


def test_refuses_requests_over_the_size_limit(tmp_path: Path, headers: dict[str, str]) -> None:
    client = TestClient(create_app(settings(tmp_path, max_request_bytes=1)))
    large = [("pages", ("page.png", png(400, 400, noise=True), "image/png"))]

    response = client.post("/v1/recognitions", files=large, headers=headers)

    assert response.status_code == 413
    assert response.json()["code"] == "tooLarge"


def test_refuses_files_that_are_not_images(client: TestClient, headers: dict[str, str]) -> None:
    response = client.post("/v1/recognitions", files=[("pages", ("page.png", b"not an image", "image/png"))], headers=headers)

    assert response.status_code == 422
    assert response.json()["code"] == "unreadableImage"


def test_refuses_unknown_engines(client: TestClient, headers: dict[str, str]) -> None:
    response = client.post("/v1/recognitions", files=pages(1), data={"engines": ["MUSESCORE"]}, headers=headers)

    assert response.status_code == 422
    assert response.json()["code"] == "unknownEngine"


def test_converts_midi_files_into_musicxml(client: TestClient, headers: dict[str, str]) -> None:
    part = stream.Part([note.Note("C4", quarterLength=1), note.Note("E4", quarterLength=1), note.Note("G4", quarterLength=2)])
    midi_file = midi.translate.streamToMidiFile(stream.Score([part]))

    response = client.post("/v1/midi-conversions", files={"file": ("brano.mid", midi_file.writestr(), "audio/midi")}, headers=headers)

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("application/vnd.recordare.musicxml+xml")
    assert b"<score-partwise" in response.content
    assert response.content.count(b"<note") >= 3
    assert b"Music21 Fragment" not in response.content


def test_refuses_files_that_are_not_midi(client: TestClient, headers: dict[str, str]) -> None:
    response = client.post("/v1/midi-conversions", files={"file": ("brano.mid", b"RIFF....", "audio/midi")}, headers=headers)

    assert response.status_code == 422
    assert response.json()["code"] == "unreadableMidi"


def test_midi_conversions_require_the_service_key(client: TestClient) -> None:
    response = client.post("/v1/midi-conversions", files={"file": ("brano.mid", b"MThd", "audio/midi")})

    assert response.status_code == 401


def test_the_key_must_be_long_enough(tmp_path: Path) -> None:
    with pytest.raises(Exception, match="at least"):
        settings(tmp_path, api_key="short")
    assert API_KEY
