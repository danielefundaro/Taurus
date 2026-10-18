"""Validation and normalization of the page images received from the backend."""

from __future__ import annotations

import io
import warnings
from collections.abc import Sequence
from pathlib import Path

from PIL import Image

from .errors import UnreadableImage

ACCEPTED_FORMATS = frozenset({"PNG", "JPEG", "WEBP"})


def normalize(data: bytes, max_pixels: int) -> Image.Image:
    """Decodes a PNG, JPEG or WebP page as an 8-bit grayscale image.

    Images larger than ``max_pixels`` are refused before decoding, so that a crafted file cannot exhaust memory.
    """
    previous = Image.MAX_IMAGE_PIXELS
    try:
        Image.MAX_IMAGE_PIXELS = max_pixels
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(io.BytesIO(data)) as image:
                if image.format not in ACCEPTED_FORMATS:
                    raise UnreadableImage(f"unsupported image format {image.format}")
                if image.width * image.height > max_pixels:
                    raise UnreadableImage("image too large")
                image.load()
                if image.mode in ("RGBA", "LA", "P"):
                    image = _flatten(image)
                return image.convert("L")
    except UnreadableImage:
        raise
    except (OSError, ValueError, Image.DecompressionBombError, Image.DecompressionBombWarning) as exception:
        raise UnreadableImage("unreadable image") from exception
    finally:
        Image.MAX_IMAGE_PIXELS = previous


def write_pages(images: Sequence[Image.Image], directory: Path) -> list[Path]:
    """Writes the pages as numbered PNG files, so that the alphabetical order is the page order."""
    directory.mkdir(parents=True, exist_ok=True)
    paths = []
    for index, image in enumerate(images, start=1):
        path = directory / f"page-{index:03d}.png"
        image.save(path, format="PNG")
        paths.append(path)
    return paths


def _flatten(image: Image.Image) -> Image.Image:
    rgba = image.convert("RGBA")
    background = Image.new("RGBA", rgba.size, (255, 255, 255, 255))
    return Image.alpha_composite(background, rgba)
