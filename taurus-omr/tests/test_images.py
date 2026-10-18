from __future__ import annotations

import io
from pathlib import Path

import pytest
from PIL import Image

from taurus_omr import images
from taurus_omr.errors import UnreadableImage

from .conftest import png


def encoded(image: Image.Image, format: str) -> bytes:
    output = io.BytesIO()
    image.save(output, format=format)
    return output.getvalue()


@pytest.mark.parametrize("format", ["PNG", "JPEG", "WEBP"])
def test_decodes_pages_as_grayscale(format: str) -> None:
    page = images.normalize(encoded(Image.new("RGB", (40, 30), "white"), format), 10_000)

    assert page.mode == "L"
    assert page.size == (40, 30)


def test_flattens_transparent_pages_on_white() -> None:
    page = images.normalize(encoded(Image.new("RGBA", (10, 10), (0, 0, 0, 0)), "PNG"), 10_000)

    assert page.getpixel((5, 5)) == 255


@pytest.mark.parametrize(
    "data",
    [b"plain text", encoded(Image.new("RGB", (10, 10)), "GIF"), png(200, 200)],
    ids=["text", "gif", "too-large"],
)
def test_refuses_unreadable_unsupported_or_oversized_pages(data: bytes) -> None:
    with pytest.raises(UnreadableImage):
        images.normalize(data, 10_000)


def test_writes_the_pages_in_reading_order(tmp_path: Path) -> None:
    pages = [Image.new("L", (20, 20), "white") for _ in range(3)]

    paths = images.write_pages(pages, tmp_path / "pages")

    assert [path.name for path in paths] == ["page-001.png", "page-002.png", "page-003.png"]
    assert all(path.is_file() for path in paths)
