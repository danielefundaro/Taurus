"""The recognition engines: Audiveris and homr, each run as a separate, unmodified program."""

from __future__ import annotations

import logging
import re
import zipfile
from dataclasses import dataclass
from importlib import metadata
from pathlib import Path
from typing import Protocol
from xml.etree import ElementTree

from PIL import Image

from . import images, musicxml, process
from .config import Settings
from .errors import EngineFailure
from .layout import MeasureBox, audiveris_layout

log = logging.getLogger(__name__)

AUDIVERIS = "AUDIVERIS"
HOMR = "HOMR"
_MOVEMENT = re.compile(r"\.mvt(\d+)\.mxl$")


@dataclass(frozen=True)
class Recognition:
    """What an engine read from the pages: the score and, when known, the position of each measure."""

    score: ElementTree.Element
    layout: list[MeasureBox] | None


class Engine(Protocol):
    name: str

    @property
    def version(self) -> str: ...

    def recognize(self, pages: list[Image.Image], work: Path) -> Recognition: ...


class Audiveris:
    """Audiveris reads each page as a book; pages and the movements it detects are joined back into a single score."""

    name = AUDIVERIS

    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    @property
    def version(self) -> str:
        return self._settings.audiveris_version

    def recognize(self, pages: list[Image.Image], work: Path) -> Recognition:
        paths = images.write_pages(pages, work / "pages")
        output = work / "out"
        output.mkdir()
        process.run(
            [*self._settings.audiveris_command, "-batch", "-export", "-output", str(output), "--", *(str(path) for path in paths)],
            cwd=work,
            timeout_seconds=self._settings.engine_timeout_seconds,
            environment={"HOME": str(work)},
        )
        scores = []
        layout: list[MeasureBox] | None = []
        first_measure = 1
        for number, path in enumerate(paths, start=1):
            exported = _movements(output, path.stem)
            if not exported:
                raise EngineFailure(f"Audiveris exported no score for page {number}")
            page = musicxml.concatenate([musicxml.read(file) for file in exported])
            scores.append(page)
            layout = _page_layout(layout, output / f"{path.stem}.omr", page, number, first_measure)
            first_measure += musicxml.measure_count(page)
        score = musicxml.concatenate(scores)
        musicxml.remove_titles(score)
        return Recognition(score, layout)


class Homr:
    """homr reads one page at a time; the pages are joined in order. It does not report measure positions."""

    name = HOMR

    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    @property
    def version(self) -> str:
        try:
            return metadata.version("homr")
        except metadata.PackageNotFoundError:
            return "unknown"

    def recognize(self, pages: list[Image.Image], work: Path) -> Recognition:
        folder = work / "pages"
        paths = images.write_pages(pages, folder)
        process.run(
            [*self._settings.homr_command, str(folder)],
            cwd=work,
            timeout_seconds=self._settings.engine_timeout_seconds,
            environment={"HOME": str(work)},
        )
        documents = []
        for path in paths:
            result = path.with_suffix(".musicxml")
            if not result.is_file():
                raise EngineFailure(f"homr produced no score for {path.name}")
            documents.append(musicxml.read(result))
        score = musicxml.concatenate(documents)
        musicxml.remove_titles(score)
        return Recognition(score, None)


def default_engines(settings: Settings) -> dict[str, Engine]:
    return {AUDIVERIS: Audiveris(settings), HOMR: Homr(settings)}


def _movements(output: Path, book: str) -> list[Path]:
    single = output / f"{book}.mxl"
    if single.is_file():
        return [single]
    movements = [(int(match.group(1)), path) for path in output.glob(f"{book}.mvt*.mxl") if (match := _MOVEMENT.search(path.name))]
    return [path for _, path in sorted(movements)]


def _page_layout(
    layout: list[MeasureBox] | None, project: Path, page: ElementTree.Element, number: int, first_measure: int
) -> list[MeasureBox] | None:
    """Adds the positions of a page; without a readable project the positions of the whole score are omitted."""
    if layout is None or not project.is_file():
        return None
    try:
        return layout + audiveris_layout(project, page, page=number, first_measure=first_measure)
    except (OSError, ElementTree.ParseError, ValueError, zipfile.BadZipFile):
        log.warning("Audiveris project of page %s not readable: measure positions omitted", number)
        return None
