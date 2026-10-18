"""Running an engine as a separate process, with a timeout that also stops the processes it starts."""

from __future__ import annotations

import logging
import os
import signal
import subprocess
from collections.abc import Mapping, Sequence
from pathlib import Path

from .errors import EngineFailure, EngineTimeout

log = logging.getLogger(__name__)
_OUTPUT_TAIL = 2000


def run(command: Sequence[str], cwd: Path, timeout_seconds: int, environment: Mapping[str, str] | None = None) -> None:
    """Runs the command in its own process group and kills the whole group on timeout.

    Engines such as ``xvfb-run`` start child processes that a plain timeout would leave running.
    """
    env = {**os.environ, **(environment or {})}
    try:
        process = subprocess.Popen(  # noqa: S603 - the command comes from the service configuration
            list(command),
            cwd=cwd,
            env=env,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
    except OSError as exception:
        raise EngineFailure(f"cannot start {command[0]}") from exception
    try:
        output, _ = process.communicate(timeout=timeout_seconds)
    except subprocess.TimeoutExpired as exception:
        _kill_group(process)
        process.communicate()
        raise EngineTimeout(f"{command[0]} exceeded {timeout_seconds} seconds") from exception
    if process.returncode != 0:
        tail = output.decode("utf-8", "replace")[-_OUTPUT_TAIL:]
        log.warning("engine %s exited with %s: %s", command[0], process.returncode, tail)
        raise EngineFailure(f"{command[0]} exited with {process.returncode}")


def _kill_group(process: subprocess.Popen[bytes]) -> None:
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except (ProcessLookupError, PermissionError, AttributeError):
        process.kill()
