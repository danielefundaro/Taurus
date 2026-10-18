from __future__ import annotations

import pytest

from taurus_omr.config import MEGABYTE, ConfigurationError, Settings


def test_reads_the_environment() -> None:
    settings = Settings.from_env(
        {
            "TAURUS_OMR_API_KEY": "k" * 32,
            "TAURUS_OMR_ENGINE_TIMEOUT_SECONDS": "120",
            "TAURUS_OMR_MAX_REQUEST_MB": "5",
            "TAURUS_OMR_HOMR_COMMAND": "python -m homr",
        }
    )

    assert settings.engine_timeout_seconds == 120
    assert settings.max_request_bytes == 5 * MEGABYTE
    assert settings.homr_command == ("python", "-m", "homr")
    assert settings.max_pages == 30


@pytest.mark.parametrize(
    "environ",
    [{}, {"TAURUS_OMR_API_KEY": "short"}, {"TAURUS_OMR_API_KEY": "k" * 32, "TAURUS_OMR_MAX_PAGES": "0"}, {"TAURUS_OMR_API_KEY": "k" * 32, "TAURUS_OMR_CONCURRENCY": "two"}],
)
def test_refuses_to_start_without_a_valid_configuration(environ: dict[str, str]) -> None:
    with pytest.raises(ConfigurationError):
        Settings.from_env(environ)
