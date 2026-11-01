"""Text reading of page images with RapidOCR, run by the service as a separate process.

``python -m taurus_omr.text [--threads N] [--models DIR] FOLDER OUTPUT`` reads the ``page-*.png`` files of the folder
in alphabetical order and writes to OUTPUT, as JSON, the lines read on each page: text, confidence and the rectangle
around the line as fractions of the page size. It applies no threshold and sorts nothing: the service does both.

The models are the files of the image, checked by ``models.sha256``: a missing model ends the reading with an error
and nothing is ever downloaded.
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import sys
from pathlib import Path
from typing import Any

DETECTION = "PP-OCRv6_det_small.onnx"
RECOGNITION = "PP-OCRv6_rec_small.onnx"
ORIENTATION = "ch_ppocr_mobile_v2.0_cls_mobile.onnx"
MODELS = (DETECTION, RECOGNITION, ORIENTATION)
MISSING_MODEL_EXIT = 3


class MissingModel(RuntimeError):
    """A model file is not where the image puts it."""


def default_models() -> Path:
    """The models folder of the installed RapidOCR package, found without importing it."""
    spec = importlib.util.find_spec("rapidocr")
    if spec is None or spec.origin is None:
        raise MissingModel("RapidOCR is not installed")
    return Path(spec.origin).parent / "models"


def model_paths(folder: Path) -> dict[str, Path]:
    paths = {name: folder / name for name in MODELS}
    missing = [name for name, path in paths.items() if not path.is_file()]
    if missing:
        raise MissingModel(f"missing models in {folder}: {', '.join(missing)}")
    return paths


def read(folder: Path, models: Path, threads: int) -> list[dict[str, Any]]:
    paths = model_paths(models)
    engine = _engine(paths, threads)
    from PIL import Image

    pages = []
    for page in sorted(folder.glob("page-*.png")):
        with Image.open(page) as image:
            width, height = image.size
        result = engine(str(page))
        lines = []
        if result.txts:
            for box, text, score in zip(result.boxes, result.txts, result.scores):
                xs = [float(point[0]) for point in box]
                ys = [float(point[1]) for point in box]
                lines.append(
                    {
                        "text": str(text),
                        "confidence": float(score),
                        "x": min(xs) / width,
                        "y": min(ys) / height,
                        "width": (max(xs) - min(xs)) / width,
                        "height": (max(ys) - min(ys)) / height,
                    }
                )
        pages.append({"lines": lines})
    return pages


def _engine(paths: dict[str, Path], threads: int) -> Any:
    from rapidocr import RapidOCR
    from rapidocr.utils.download_file import DownloadFile

    def refuse(*_: object, **__: object) -> None:
        raise MissingModel("RapidOCR tried to download a file: the models of the image are incomplete")

    # Every file is given explicitly; should RapidOCR still look for one, it must fail instead of downloading it.
    DownloadFile.run = staticmethod(refuse)  # type: ignore[method-assign]
    return RapidOCR(
        params={
            # The service applies no threshold: every line goes back with its confidence.
            "Global.text_score": 0.0,
            "Global.log_level": "warning",
            "Det.model_path": str(paths[DETECTION]),
            "Rec.model_path": str(paths[RECOGNITION]),
            "Cls.model_path": str(paths[ORIENTATION]),
            "EngineConfig.onnxruntime.intra_op_num_threads": threads,
            "EngineConfig.onnxruntime.inter_op_num_threads": 1,
        }
    )


def main(arguments: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m taurus_omr.text")
    parser.add_argument("--threads", type=int, default=2)
    parser.add_argument("--models", type=Path)
    parser.add_argument("folder", type=Path)
    parser.add_argument("output", type=Path)
    options = parser.parse_args(arguments)
    try:
        pages = read(options.folder, options.models or default_models(), max(1, options.threads))
    except MissingModel as exception:
        print(exception, file=sys.stderr)
        return MISSING_MODEL_EXIT
    options.output.write_text(json.dumps({"pages": pages}), encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
