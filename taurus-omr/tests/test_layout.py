from __future__ import annotations

from pathlib import Path

from taurus_omr import musicxml
from taurus_omr.layout import audiveris_layout

from .fakes import scores


def test_places_each_measure_on_its_page_as_fractions_of_the_page(tmp_path: Path) -> None:
    project = scores.project(tmp_path / "book.omr", [[2, 1], [1]])
    score = musicxml.parse(scores.score(4).encode())

    boxes = audiveris_layout(project, score)

    assert [(box.measure, box.page) for box in boxes] == [(1, 1), (2, 1), (3, 1), (4, 2)]
    first = boxes[0]
    assert (first.x, first.width) == (0.1, 0.4)
    assert first.y == 0.08
    assert round(first.height, 5) == 0.08
    assert boxes[2].y == 0.28
    assert not any(box.approximate for box in boxes)


def test_measures_grouped_in_a_multiple_rest_share_its_box(tmp_path: Path) -> None:
    project = scores.project(tmp_path / "book.omr", [[3]])
    score = musicxml.parse(scores.score(5, multiple_rest={1: 3}).encode())

    boxes = audiveris_layout(project, score)

    assert [box.x for box in boxes] == [boxes[0].x, boxes[1].x, boxes[1].x, boxes[1].x, boxes[4].x]
    assert boxes[1].x != boxes[0].x != boxes[4].x
    assert not any(box.approximate for box in boxes)


def test_marks_the_boxes_approximate_when_the_counts_differ(tmp_path: Path) -> None:
    project = scores.project(tmp_path / "book.omr", [[2]])
    score = musicxml.parse(scores.score(3).encode())

    boxes = audiveris_layout(project, score)

    assert len(boxes) == 3
    assert all(box.approximate for box in boxes)
