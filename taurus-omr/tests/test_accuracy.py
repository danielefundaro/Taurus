"""Accuracy of the real engines on the reference corpus, against a running service.

Runs only with ``TAURUS_OMR_ACCURACY_URL`` and ``TAURUS_OMR_ACCURACY_KEY``; the CI starts the image it has just built.
The minimum share of correct measures of each engine is fixed here: an engine or model update that reads worse fails,
one that reads better allows raising the minimum.
"""

from __future__ import annotations

import os

import pytest

from accuracy import run

URL = os.environ.get("TAURUS_OMR_ACCURACY_URL")
KEY = os.environ.get("TAURUS_OMR_ACCURACY_KEY")
# Measured on 18/10/2026 with Audiveris 5.11.0 and homr 0.7.0 (measures 132/151 and 124/151, structure marks 15/26
# and 6/26), lowered by a margin of a few measures and marks for small differences between processors.
MINIMUM_MEASURES = {"AUDIVERIS": 0.84, "HOMR": 0.79}
MINIMUM_MARKS = {"AUDIVERIS": 0.5, "HOMR": 0.15}

pytestmark = pytest.mark.skipif(not URL or not KEY, reason="no OMR service to check: set TAURUS_OMR_ACCURACY_URL and TAURUS_OMR_ACCURACY_KEY")


@pytest.fixture(scope="module")
def results() -> list[run.PieceResult]:
    results = run.evaluate(URL or "", KEY or "")
    table = run.report(results)
    print(f"\n{table}")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as output:
            output.write(f"### Accuratezza dei motori OMR\n\n{table}\n")
    return results


@pytest.mark.parametrize("engine", run.ENGINES)
def test_engine_reads_at_least_the_expected_share_of_measures(results: list[run.PieceResult], engine: str) -> None:
    correct, measures, _, _ = run.totals(results, engine)

    assert correct / measures >= MINIMUM_MEASURES[engine], f"{engine}: {correct}/{measures} correct measures"


@pytest.mark.parametrize("engine", run.ENGINES)
def test_engine_reads_at_least_the_expected_share_of_structure_marks(results: list[run.PieceResult], engine: str) -> None:
    _, _, found, marks = run.totals(results, engine)

    assert found / marks >= MINIMUM_MARKS[engine], f"{engine}: {found}/{marks} structure marks"


def test_every_piece_is_read_by_at_least_one_engine(results: list[run.PieceResult]) -> None:
    unread = [result.piece for result in results if all(comparison is None or comparison.correct_measures == 0 for comparison in result.comparisons.values())]

    assert unread == []
