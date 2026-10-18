from __future__ import annotations

import io
import zipfile
from pathlib import Path

import pytest

from taurus_omr import musicxml
from taurus_omr.errors import EngineFailure

from .fakes import scores


def numbers(root) -> list[str]:
    return [measure.get("number") for measure in root.find("part").findall("measure")]


def test_concatenates_documents_and_numbers_the_measures_again() -> None:
    first = musicxml.parse(scores.score(2, implicit_first=True).encode())
    second = musicxml.parse(scores.score(3).encode())

    joined = musicxml.concatenate([first, second])

    assert numbers(joined) == ["0", "1", "2", "3", "4"]
    assert musicxml.measure_count(joined) == 5
    assert numbers(first) == ["1", "2"]


def test_reads_plain_and_compressed_files(tmp_path: Path) -> None:
    plain = tmp_path / "parte.musicxml"
    plain.write_text(scores.score(1), encoding="utf-8")
    packed = tmp_path / "parte.mxl"
    packed.write_bytes(scores.compressed(scores.score(2), root_name="nested/parte.xml"))

    assert musicxml.measure_count(musicxml.read(plain)) == 1
    assert musicxml.measure_count(musicxml.read(packed)) == 2


def test_writes_a_compressed_file_with_its_container() -> None:
    root = musicxml.parse(scores.score(2).encode())

    with zipfile.ZipFile(io.BytesIO(musicxml.to_compressed(root))) as archive:
        assert "score.musicxml" in archive.read("META-INF/container.xml").decode()
        assert musicxml.measure_count(musicxml.parse(archive.read("score.musicxml"))) == 2


def test_removes_titles_read_from_the_image() -> None:
    root = musicxml.parse(scores.score(1, title="Marcia").encode())

    musicxml.remove_titles(root)

    assert root.find("work") is None


@pytest.mark.parametrize("document", [b"<score-timewise/>", b"<score-partwise>", b"<score-partwise/>"])
def test_refuses_unusable_documents(document: bytes) -> None:
    with pytest.raises(EngineFailure):
        musicxml.concatenate([musicxml.parse(document)])
