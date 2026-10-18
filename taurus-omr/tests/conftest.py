from __future__ import annotations

import io
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from taurus_omr.app import API_KEY_HEADER, create_app
from taurus_omr.config import Settings

FAKES = Path(__file__).parent / "fakes"
API_KEY = "test-key-0123456789"
sys.path.insert(0, str(FAKES))


def settings(tmp_path: Path, **overrides: object) -> Settings:
    values: dict[str, object] = {
        "api_key": API_KEY,
        "audiveris_command": (sys.executable, str(FAKES / "fake_audiveris.py")),
        "homr_command": (sys.executable, str(FAKES / "fake_homr.py")),
        "engine_timeout_seconds": 20,
        "work_dir": str(tmp_path),
    }
    values.update(overrides)
    return Settings(**values)  # type: ignore[arg-type]


def png(width: int = 120, height: int = 80, mode: str = "L", noise: bool = False) -> bytes:
    image = Image.effect_noise((width, height), 64).convert(mode) if noise else Image.new(mode, (width, height), "white")
    output = io.BytesIO()
    image.save(output, format="PNG")
    return output.getvalue()


@pytest.fixture
def client(tmp_path: Path) -> TestClient:
    return TestClient(create_app(settings(tmp_path)))


@pytest.fixture
def headers() -> dict[str, str]:
    return {API_KEY_HEADER: API_KEY}
