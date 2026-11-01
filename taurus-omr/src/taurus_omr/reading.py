"""Text reading: RapidOCR runs as a separate program on the pages; the lines it finds are put in reading order."""

from __future__ import annotations

import json
from dataclasses import dataclass
from importlib import metadata
from pathlib import Path

from PIL import Image

from . import images, process
from .config import Settings
from .errors import EngineFailure

RAPIDOCR = "RAPIDOCR"


@dataclass(frozen=True)
class TextLine:
    """A line read on a page: position and size as fractions of the page, confidence of the engine from 0 to 1."""

    text: str
    confidence: float
    x: float
    y: float
    width: float
    height: float

    def to_json(self) -> dict[str, object]:
        return {
            "text": self.text,
            "confidence": round(self.confidence, 3),
            "x": round(self.x, 4),
            "y": round(self.y, 4),
            "width": round(self.width, 4),
            "height": round(self.height, 4),
        }


class TextReader:
    name = RAPIDOCR

    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    @property
    def version(self) -> str:
        try:
            return metadata.version("rapidocr")
        except metadata.PackageNotFoundError:
            return "unknown"

    def read(self, pages: list[Image.Image], work: Path, timeout_seconds: int) -> list[list[TextLine]]:
        """The lines of each page, in the order of the pages; the whole reading stops at the timeout."""
        folder = work / "pages"
        images.write_pages(pages, folder)
        output = work / "lines.json"
        process.run(
            [*self._settings.text_command, "--threads", str(self._settings.text_threads), str(folder), str(output)],
            cwd=work,
            timeout_seconds=timeout_seconds,
            environment={"HOME": str(work), "OMP_NUM_THREADS": str(self._settings.text_threads)},
        )
        try:
            parsed = json.loads(output.read_text(encoding="utf-8"))["pages"]
            result = [reading_order([_line(line) for line in page["lines"]]) for page in parsed]
        except (OSError, ValueError, KeyError, TypeError) as exception:
            raise EngineFailure("unreadable result of the text engine") from exception
        if len(result) != len(pages):
            raise EngineFailure(f"the text engine read {len(result)} of {len(pages)} pages")
        return result


def reading_order(lines: list[TextLine]) -> list[TextLine]:
    """Top to bottom, then left to right: lines whose centers are within half a line height are on the same row."""
    rows: list[tuple[float, float, list[TextLine]]] = []
    for line in sorted(lines, key=lambda candidate: candidate.y + candidate.height / 2):
        center = line.y + line.height / 2
        if rows and abs(center - rows[-1][0]) <= max(line.height, rows[-1][1]) / 2:
            rows[-1][2].append(line)
        else:
            rows.append((center, line.height, [line]))
    return [line for _, _, row in rows for line in sorted(row, key=lambda candidate: candidate.x)]


def _line(value: dict[str, object]) -> TextLine:
    x = _fraction(value["x"])
    y = _fraction(value["y"])
    return TextLine(
        text=str(value["text"]),
        confidence=_fraction(value["confidence"]),
        x=x,
        y=y,
        width=min(_fraction(value["width"]), 1.0 - x),
        height=min(_fraction(value["height"]), 1.0 - y),
    )


def _fraction(value: object) -> float:
    number = float(value)  # type: ignore[arg-type]
    if number != number:  # NaN
        raise ValueError("not a number")
    return min(max(number, 0.0), 1.0)
