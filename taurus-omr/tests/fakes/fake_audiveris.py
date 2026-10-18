"""Stands in for Audiveris: exports two measures for each page, as one score or, for the second page, as two movements.

The behavior is chosen with the FAKE_AUDIVERIS environment variable: ok, movements, fail, empty, sleep.
"""

import os
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from PIL import Image  # noqa: E402

import scores  # noqa: E402

arguments = sys.argv[1:]
output = Path(arguments[arguments.index("-output") + 1])
books = [Path(argument) for argument in arguments[arguments.index("--") + 1 :]]
mode = os.environ.get("FAKE_AUDIVERIS", "ok")
if mode == "sleep":
    time.sleep(60)
if mode == "fail":
    sys.exit(3)
if mode == "empty":
    sys.exit(0)

for index, book in enumerate(books):
    with Image.open(book):
        pass
    if mode == "movements" and index == 1:
        (output / f"{book.stem}.mvt1.mxl").write_bytes(scores.compressed(scores.score(1)))
        (output / f"{book.stem}.mvt2.mxl").write_bytes(scores.compressed(scores.score(1)))
    else:
        (output / f"{book.stem}.mxl").write_bytes(scores.compressed(scores.score(2, title="Marcia")))
    scores.project(output / f"{book.stem}.omr", [[2]])
