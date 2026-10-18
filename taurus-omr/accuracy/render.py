"""Renders the reference parts as A4 pages at 300 DPI with Verovio, as Taurus extracts them from the PDFs.

Requires the packages of ``requirements-accuracy.txt``, the Cairo library and a Times-compatible font; the pages are
committed, so the checks always read the same images. A part marked as a scan looks like a worn photocopy.
"""

from __future__ import annotations

import base64
import io
import os
import random
import re
import tempfile
from pathlib import Path

import verovio
from fontTools.ttLib import TTFont
from PIL import Image, ImageChops, ImageDraw

from . import corpus

CORPUS = Path(__file__).parent / "corpus"
# A4 at 300 DPI.
PAGE_PIXELS = (2480, 3508)
# Verovio units are tenths of a millimetre: an A4 page with 15 mm margins and a 7.2 mm staff, common in band parts.
OPTIONS = {
    "pageWidth": 2100,
    "pageHeight": 2970,
    "pageMarginTop": 150,
    "pageMarginBottom": 150,
    "pageMarginLeft": 150,
    "pageMarginRight": 150,
    "scale": 100,
    "header": "auto",
    "footer": "none",
    "breaks": "auto",
    "adjustPageHeight": False,
}


def render(output: Path = CORPUS) -> list[Path]:
    """Writes the reference MusicXML and the pages of every part; returns the written files."""
    fonts = tempfile.TemporaryDirectory(prefix="verovio-fonts-")
    _use_music_font(Path(fonts.name))
    output.mkdir(parents=True, exist_ok=True)
    written = []
    for piece in corpus.PIECES:
        document = corpus.musicxml(piece)
        reference = output / f"{piece.name}.musicxml"
        reference.write_text(document, encoding="utf-8", newline="\n")
        written.append(reference)
        for stale in output.glob(f"{piece.name}-*.png"):
            stale.unlink()
        for number, page in enumerate(_pages(document), start=1):
            image = _scan(page, seed=number) if piece.scan else page
            path = output / f"{piece.name}-{number}.png"
            image.save(path, dpi=(300, 300), optimize=True)
            written.append(path)
    return written


def _use_music_font(folder: Path) -> None:
    """Makes the music font of Verovio, embedded in its CSS for browsers, available to Cairo through fontconfig.

    Verovio writes the glyphs of tempo marks as text in that font; without it they would be drawn as empty boxes.
    """
    css = (Path(verovio.__file__).parent / "data" / "Leipzig.css").read_text(encoding="utf-8")
    match = re.search(r"base64,([A-Za-z0-9+/=]+)", css)
    if match is None:
        raise ValueError("music font not found in the Verovio data")
    font = TTFont(io.BytesIO(base64.b64decode(match.group(1))))
    font.flavor = None
    font.save(folder / "Leipzig.otf")
    config = folder / "fonts.conf"
    config.write_text(
        f'<?xml version="1.0"?><fontconfig><include ignore_missing="yes">/etc/fonts/fonts.conf</include><dir>{folder}</dir></fontconfig>',
        encoding="utf-8",
    )
    os.environ["FONTCONFIG_FILE"] = str(config)


def _pages(document: str) -> list[Image.Image]:
    import cairosvg  # after the font configuration, which fontconfig reads once

    toolkit = verovio.toolkit()
    toolkit.setOptions(OPTIONS)
    if not toolkit.loadData(document):
        raise ValueError("Verovio could not read the reference part")
    pages = []
    for number in range(1, toolkit.getPageCount() + 1):
        png = cairosvg.svg2png(
            bytestring=toolkit.renderToSVG(number).encode("utf-8"),
            output_width=PAGE_PIXELS[0],
            output_height=PAGE_PIXELS[1],
            background_color="white",
        )
        pages.append(Image.open(io.BytesIO(png)).convert("L"))
    return pages


def _scan(page: Image.Image, seed: int) -> Image.Image:
    """A worn photocopy, deterministic for a seed: copied at low resolution and slightly rotated, with grey toner, paper
    darker towards the bottom and specks of dust. Grey levels are reduced to keep the file small."""
    rng = random.Random(seed)
    rotated = page.rotate(0.7, resample=Image.Resampling.BICUBIC, fillcolor=255)
    low = rotated.resize((page.width * 2 // 5, page.height * 2 // 5), Image.Resampling.BILINEAR)
    copied = low.resize(page.size, Image.Resampling.BILINEAR).point(lambda value: 60 + value * 195 // 255)
    shade = Image.linear_gradient("L").resize(page.size).point(lambda value: 255 - value // 7)
    paper = ImageChops.multiply(copied, shade)
    draw = ImageDraw.Draw(paper)
    for _ in range(3000):
        x, y, size = rng.randrange(page.width), rng.randrange(page.height), rng.choice((1, 1, 2, 3, 4))
        draw.ellipse((x, y, x + size, y + size), fill=rng.randrange(40, 170))
    return paper.point(lambda value: value // 16 * 16)
