"""Stands in for homr: writes a two-measure score next to each page image of the folder.

The behavior is chosen with the FAKE_HOMR environment variable: ok, missing-page, fail.
"""

import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import scores  # noqa: E402

folder = Path(sys.argv[1])
mode = os.environ.get("FAKE_HOMR", "ok")
if mode == "fail":
    sys.exit(1)
pages = sorted(folder.glob("*.png"))
for index, page in enumerate(pages):
    if mode == "missing-page" and index == len(pages) - 1:
        continue
    page.with_suffix(".musicxml").write_text(scores.score(2, title="Lento 50"), encoding="utf-8")
