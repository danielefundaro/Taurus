"""Recognizes the corpus with a running service and compares each engine with the reference parts."""

from __future__ import annotations

import base64
import re
from dataclasses import dataclass
from pathlib import Path

import httpx

from . import compare, corpus

CORPUS = Path(__file__).parent / "corpus"
API_KEY_HEADER = "X-Taurus-Omr-Key"
ENGINES = ("AUDIVERIS", "HOMR")


@dataclass(frozen=True)
class PieceResult:
    piece: str
    seconds: float
    comparisons: dict[str, compare.Comparison | None]


def pages(name: str, folder: Path = CORPUS) -> list[Path]:
    numbered = (re.fullmatch(rf"{re.escape(name)}-(\d+)\.png", path.name) for path in folder.iterdir())
    found = [folder / match.group(0) for match in sorted((m for m in numbered if m), key=lambda m: int(m.group(1)))]
    if not found:
        raise FileNotFoundError(f"no pages for {name} in {folder}")
    return found


def recognize(url: str, key: str, files: list[Path], timeout_seconds: float = 900) -> tuple[dict[str, bytes | None], float]:
    """The compressed MusicXML of each engine, ``None`` for a failed engine, and the duration of the request."""
    response = httpx.post(
        f"{url.rstrip('/')}/v1/recognitions",
        headers={API_KEY_HEADER: key},
        files=[("pages", (path.name, path.read_bytes(), "image/png")) for path in files],
        timeout=timeout_seconds,
    )
    response.raise_for_status()
    results = {
        result["engine"]: base64.b64decode(result["musicXml"]) if result["status"] == "DONE" else None for result in response.json()["results"]
    }
    return results, response.elapsed.total_seconds()


def evaluate(url: str, key: str, names: list[str] | None = None, folder: Path = CORPUS, save: Path | None = None) -> list[PieceResult]:
    """Recognizes the pieces; ``save`` keeps what each engine read, to look at the differences with ``compare``."""
    results = []
    for piece in corpus.PIECES:
        if names and piece.name not in names:
            continue
        reference = compare.load_file(folder / f"{piece.name}.musicxml")
        recognized, seconds = recognize(url, key, pages(piece.name, folder))
        if save is not None:
            save.mkdir(parents=True, exist_ok=True)
            for engine, data in recognized.items():
                if data is not None:
                    (save / f"{piece.name}-{engine.lower()}.mxl").write_bytes(data)
        comparisons = {
            engine: None if recognized.get(engine) is None else compare.compare(reference, compare.load(recognized[engine]))  # type: ignore[arg-type]
            for engine in ENGINES
        }
        results.append(PieceResult(piece.name, seconds, comparisons))
    return results


def totals(results: list[PieceResult], engine: str) -> tuple[int, int, int, int]:
    """Correct and reference measures, found and reference structure marks of an engine; failures count as zero."""
    correct = measures = found = marks = 0
    for result in results:
        reference = compare.load_file(CORPUS / f"{result.piece}.musicxml")
        comparison = result.comparisons[engine]
        measures += len(reference)
        marks += sum(len(measure.marks) for measure in reference)
        if comparison is not None:
            correct += comparison.correct_measures
            found += comparison.found_marks
    return correct, measures, found, marks


def report(results: list[PieceResult]) -> str:
    """A Markdown table: correct measures and structure marks per engine, and the time of each recognition."""
    lines = [
        "| Brano | Battute | Audiveris | homr | Struttura Audiveris | Struttura homr | Secondi |",
        "| --- | --- | --- | --- | --- | --- | --- |",
    ]
    for result in results:
        cells = []
        for engine in ENGINES:
            comparison = result.comparisons[engine]
            cells.append("fallito" if comparison is None else f"{comparison.correct_measures} ({comparison.candidate_measures} lette)")
        marks = []
        for engine in ENGINES:
            comparison = result.comparisons[engine]
            marks.append("fallito" if comparison is None else f"{comparison.found_marks}/{comparison.reference_marks}")
        measures = len(compare.load_file(CORPUS / f"{result.piece}.musicxml"))
        lines.append(f"| {result.piece} | {measures} | {cells[0]} | {cells[1]} | {marks[0]} | {marks[1]} | {result.seconds:.0f} |")
    summary = []
    for engine in ENGINES:
        correct, measures, found, marks = totals(results, engine)
        summary.append(f"{correct}/{measures} ({correct / measures:.0%}), struttura {found}/{marks}")
    lines.append(f"| **Totale** | | {summary[0]} | {summary[1]} | | | {sum(r.seconds for r in results):.0f} |")
    return "\n".join(lines)
