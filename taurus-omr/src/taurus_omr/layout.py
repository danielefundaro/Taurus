"""Position of each measure on the page images, read from the Audiveris project (.omr)."""

from __future__ import annotations

import re
import zipfile
from dataclasses import asdict, dataclass
from pathlib import Path
from xml.etree import ElementTree

_SHEET = re.compile(r"sheet#(\d+)/sheet#\1\.xml$")


@dataclass(frozen=True)
class MeasureBox:
    """A measure on a page: page number from 1, coordinates as fractions of the page size."""

    measure: int
    page: int
    x: float
    y: float
    width: float
    height: float
    approximate: bool

    def to_json(self) -> dict[str, object]:
        return asdict(self)


@dataclass(frozen=True)
class _Stack:
    page: int
    x: float
    y: float
    width: float
    height: float


def audiveris_layout(project: Path, score: ElementTree.Element, page: int | None = None, first_measure: int = 1) -> list[MeasureBox]:
    """Matches the measures of the exported score with the measure stacks of the project, page by page.

    A multi-measure rest is a single stack on the page but several measures in MusicXML: the measures it groups share
    its box. When the counts do not match, the boxes are still returned in order but marked approximate. With ``page``
    the project holds a single page, numbered as given; measures are numbered from ``first_measure``.
    """
    stacks = _stacks(project)
    if page is not None:
        stacks = [_Stack(page, stack.x, stack.y, stack.width, stack.height) for stack in stacks]
    part = score.find("part")
    measures = [] if part is None else part.findall("measure")
    boxes: list[MeasureBox] = []
    next_stack = 0
    rest_left = 0
    current: _Stack | None = None
    exact = True
    for index, measure in enumerate(measures, start=first_measure):
        if rest_left > 0 and current is not None:
            rest_left -= 1
        elif next_stack < len(stacks):
            current = stacks[next_stack]
            next_stack += 1
            rest_left = _multiple_rest(measure) - 1
        else:
            exact = False
        if current is None:
            continue
        boxes.append(MeasureBox(index, current.page, current.x, current.y, current.width, current.height, False))
    exact = exact and next_stack == len(stacks)
    return boxes if exact else [MeasureBox(**{**box.to_json(), "approximate": True}) for box in boxes]


def _stacks(project: Path) -> list[_Stack]:
    with zipfile.ZipFile(project) as archive:
        sheets = sorted(
            ((int(match.group(1)), name) for name in archive.namelist() if (match := _SHEET.search(name))),
            key=lambda item: item[0],
        )
        result: list[_Stack] = []
        for number, name in sheets:
            result.extend(_sheet_stacks(number, ElementTree.fromstring(archive.read(name))))
        return result


def _sheet_stacks(page: int, sheet: ElementTree.Element) -> list[_Stack]:
    picture = sheet.find("picture")
    if picture is None:
        return []
    width = float(picture.get("width", "0"))
    height = float(picture.get("height", "0"))
    if width <= 0 or height <= 0:
        return []
    interline = sheet.find("scale/interline")
    margin = 2 * float(interline.get("main", "0")) if interline is not None else 0.0
    result: list[_Stack] = []
    for system in sheet.iter("system"):
        top, bottom = _staff_extent(system)
        if top is None or bottom is None:
            continue
        y0 = max(0.0, top - margin)
        y1 = min(height, bottom + margin)
        for stack in system.findall("stack"):
            left = float(stack.get("left", "0"))
            right = float(stack.get("right", "0"))
            if right <= left:
                continue
            result.append(_Stack(page, _ratio(left, width), _ratio(y0, height), _ratio(right - left, width), _ratio(y1 - y0, height)))
    return result


def _staff_extent(system: ElementTree.Element) -> tuple[float | None, float | None]:
    top: float | None = None
    bottom: float | None = None
    for staff in system.iter("staff"):
        lines = staff.findall("lines/line")
        if not lines:
            continue
        first = [float(point.get("y", "0")) for point in lines[0].findall("point")]
        last = [float(point.get("y", "0")) for point in lines[-1].findall("point")]
        if first:
            top = min(first) if top is None else min(top, min(first))
        if last:
            bottom = max(last) if bottom is None else max(bottom, max(last))
    return top, bottom


def _multiple_rest(measure: ElementTree.Element) -> int:
    element = measure.find("attributes/measure-style/multiple-rest")
    try:
        return max(1, int((element.text or "1").strip())) if element is not None else 1
    except ValueError:
        return 1


def _ratio(value: float, total: float) -> float:
    return round(min(1.0, max(0.0, value / total)), 5)
