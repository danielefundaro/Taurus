"""Stands in for the text reading module: same arguments, lines out of reading order on every page that is not blank.

The behavior is chosen with the FAKE_TEXT environment variable: ok, fail, sleep, garbage, short.
"""

import argparse
import json
import os
import sys
import time
from pathlib import Path

from PIL import Image

parser = argparse.ArgumentParser()
parser.add_argument("--threads", type=int, required=True)
parser.add_argument("folder", type=Path)
parser.add_argument("output", type=Path)
options = parser.parse_args()
mode = os.environ.get("FAKE_TEXT", "ok")
if mode == "sleep":
    time.sleep(60)
if mode == "fail":
    sys.exit(1)
if mode == "garbage":
    options.output.write_text("not json", encoding="utf-8")
    sys.exit(0)

# Written bottom to top and right to left; the last line overflows the page and must be clipped.
LINES = [
    {"text": "1", "confidence": 0.99, "x": 0.07, "y": 0.155, "width": 0.01, "height": 0.01},
    {"text": "= 92", "confidence": 0.93, "x": 0.2, "y": 0.0995, "width": 0.05, "height": 0.02},
    {"text": "Clarinetto 1° in Sib", "confidence": 0.94, "x": 0.05, "y": 0.1, "width": 0.22, "height": 0.02},
    {"text": "Corale e marcia", "confidence": 0.98, "x": 0.4, "y": 0.05, "width": 0.19, "height": 0.02},
    {"text": "Ed. Musicali", "confidence": 1.2, "x": 0.9, "y": 0.97, "width": 0.3, "height": 0.05},
]
pages = []
for page in sorted(options.folder.glob("page-*.png")):
    with Image.open(page) as image:
        blank = image.convert("L").getextrema() == (255, 255)
    pages.append({"lines": [] if blank else LINES})
if mode == "short":
    pages = pages[:-1]
options.output.write_text(json.dumps({"pages": pages}), encoding="utf-8")
