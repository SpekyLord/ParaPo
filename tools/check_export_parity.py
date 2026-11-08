#!/usr/bin/env python3
"""Same-tensor CPU export probe and local Android parity fixtures (validation only)."""

import argparse
import importlib.metadata
import json
import shutil
import zipfile
import math
from pathlib import Path

import experiment


def prepare_input(bgr):
    """Pixel-center bilinear RGB sampling, with explicit rounding matching Android."""
    import numpy as np
    height, width = bgr.shape[:2]
    scale = min(320 / width, 320 / height)
    rw, rh = math.floor(width * scale + .5), math.floor(height * scale + .5)
    x = (np.arange(rw, dtype=np.float64) + .5) * width / rw - .5
    y = (np.arange(rh, dtype=np.float64) + .5) * height / rh - .5
    x0, y0 = np.floor(x).astype(int), np.floor(y).astype(int)
    dx, dy = (x - x0)[None, :, None], (y - y0)[:, None, None]
    rgb = bgr[:, :, ::-1].astype(np.float64)
    a = rgb[np.clip(y0, 0, height-1)[:, None], np.clip(x0, 0, width-1)[None, :]]
    b = rgb[np.clip(y0, 0, height-1)[:, None], np.clip(x0+1, 0, width-1)[None, :]]
    c = rgb[np.clip(y0+1, 0, height-1)[:, None], np.clip(x0, 0, width-1)[None, :]]
    d = rgb[np.clip(y0+1, 0, height-1)[:, None], np.clip(x0+1, 0, width-1)[None, :]]
    upper, lower = a * (1-dx) + b * dx, c * (1-dx) + d * dx
    resized = np.floor(upper * (1-dy) + lower * dy + .5).clip(0, 255).astype(np.uint8)
    pixels = np.full((320, 320, 3), 114, dtype=np.uint8)
    top, left = (320-rh)//2, (320-rw)//2
    pixels[top:top+rh, left:left+rw] = resized
    return np.ascontiguousarray(pixels.transpose(2, 0, 1)[None], dtype=np.float32) / np.float32(255)


def source_box(box, width, height):
    scale = min(320 / width, 320 / height)
    left, top = (320-math.floor(width*scale+.5))//2, (320-math.floor(height*scale+.5))//2
    x1, y1, x2, y2 = box
    return [min(width, max(0., (x1-left)/scale)), min(height, max(0., (y1-top)/scale)),
            min(width, max(0., (x2-left)/scale)), min(height, max(0., (y2-top)/scale))]


def decode(raw, width, height, threshold=.25):
    """Mirror the deployed single-class decoder, independently of Ultralytics prediction."""
    import numpy as np
    values = raw[0].T
    candidates = []
    for cx, cy, w, h, score in values:
        if not np.isfinite([cx, cy, w, h, score]).all() or w <= 0 or h <= 0 or score < threshold:
            continue
        candidates.append({"box": [float(cx-w/2), float(cy-h/2), float(cx+w/2), float(cy+h/2)], "score": float(score)})
    kept = []
    for detection in sorted(candidates, key=lambda item: -item["score"]):
        if all(experiment.box_iou(detection["box"], other["box"]) <= .7 for other in kept):
            kept.append(detection)
        if len(kept) == 300:
            break
    for detection in kept:
        detection["box"] = source_box(detection["box"], width, height)
    return [d for d in kept if experiment.validate_box(d["box"])]


def run(args):
    import cv2
    import numpy as np
    import onnxruntime as ort
    import torch
    from ultralytics import YOLO
    from ultralytics.data.augment import LetterBox
    root = Path(args.manifest).resolve().parent
    header, samples = experiment.validate_manifest(experiment.read_jsonl(args.manifest))
    experiment.check_leakage(samples)
    frozen = experiment.read_json(args.split_record)
    if frozen["manifest_sha256"] != experiment.sha256_file(args.manifest):
        raise experiment.InvalidInput("manifest differs from frozen record")
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=False)
    torch.set_num_threads(2)
    network = YOLO(args.checkpoint).model.cpu().float().eval()
    session = ort.InferenceSession(args.onnx, providers=["CPUExecutionProvider"])
    reference, exported, fixtures, details = [], [], [], []
    for sample in (row for row in samples if row["split"] == "validation"):
        source = (root / sample["image_ref"]).resolve()
        if not source.is_relative_to(root):
            raise experiment.InvalidInput("invalid source reference")
        bgr = cv2.imread(str(source))
        if bgr is None:
            raise experiment.InvalidInput("cannot decode parity source")
        height, width = bgr.shape[:2]
        tensor = prepare_input(bgr)
        with torch.inference_mode():
            raw_pt = network(torch.from_numpy(tensor))[0].numpy()
        raw_ort = session.run(None, {session.get_inputs()[0].name: tensor})[0]
        name = sample["sample_id"]
        cv2.imwrite(str(output / f"{name}.png"), bgr)
        tensor.astype("<f4").tofile(output / f"{name}-input.f32")
        raw_pt.astype("<f4").tofile(output / f"{name}-pytorch.f32")
        raw_ort.astype("<f4").tofile(output / f"{name}-onnx.f32")
        ref = {"sample_id": name, "detections": decode(raw_pt, width, height)}
        exp = {"sample_id": name, "detections": decode(raw_ort, width, height)}
        reference.append(ref)
        exported.append(exp)
        fixtures.append({"sample_id": name, "width": width, "height": height,
                         "image": f"{name}.png", "input": f"{name}-input.f32", "output": f"{name}-onnx.f32",
                         "input_sha256": experiment.sha256_file(output / f"{name}-input.f32"),
                         "image_sha256": experiment.sha256_file(output / f"{name}.png"),
                         "output_sha256": experiment.sha256_file(output / f"{name}-onnx.f32"),
                         "output_shape": list(raw_ort.shape), "expected_detections": exp["detections"]})
        # Record the former rectangular-input boundary; it is different input, not export drift.
        rect = LetterBox((320, 320), auto=True, stride=32)(image=bgr)
        details.append({"sample_id": name, "source_sha256": experiment.sha256_file(source),
                        "upright_rgb_sha256": __import__("hashlib").sha256(bgr[:, :, ::-1].tobytes()).hexdigest(),
                        "previous_rectangular_shape": [1, 3, *rect.shape[:2]], "input_shape": list(tensor.shape),
                        "raw_max_absolute_difference": float(np.max(np.abs(raw_pt-raw_ort))),
                        "raw_score_max_absolute_difference": float(np.max(np.abs(raw_pt[:,4]-raw_ort[:,4]))),
                        "pytorch_count": len(ref["detections"]), "onnx_count": len(exp["detections"])})
    status, reason = "passed", None
    try:
        experiment.validate_parity(reference, exported, 1.5, .02)
    except experiment.InvalidInput as error:
        status, reason = "failed", str(error)
    for name, rows in [("pytorch.jsonl", reference), ("onnx.jsonl", exported)]:
        experiment.atomic_write(output / name, "".join(json.dumps(row) + "\n" for row in rows), exclusive=True)
    model_hash = experiment.sha256_file(args.onnx)
    fixture = {"schema_version": 1, "model_sha256": model_hash, "samples": fixtures}
    (output / "manifest.json").write_text(json.dumps(fixture, indent=2), encoding="utf-8")
    shutil.copyfile(args.onnx, output / "model.onnx")
    with zipfile.ZipFile(output / "android-fixtures.zip", "x", zipfile.ZIP_DEFLATED) as archive:
        for path in output.iterdir():
            if path.suffix in {".png", ".f32", ".onnx"} or path.name == "manifest.json":
                archive.write(path, path.name)
    report = {"status": status, "reason": reason, "boundary": "identical square FP32 RGB NCHW tensor to raw outputs and Android-equivalent decoder",
              "checkpoint_sha256": experiment.sha256_file(args.checkpoint), "onnx_sha256": model_hash,
              "manifest_sha256": experiment.sha256_file(args.manifest), "dataset_version": header["dataset_version"],
              "split": "validation", "preprocessing": "pixel-center bilinear, round half up RGB, square 114 padding; shared with Android",
              "box_tolerance_pixels": 1.5, "score_tolerance": .02,
              "versions": {name: importlib.metadata.version(name) for name in ("torch", "ultralytics", "onnxruntime", "numpy", "opencv-python")},
              "samples": details, "android_status": "not measured"}
    experiment.atomic_write(output / "report.json", json.dumps(report, indent=2), exclusive=True)
    print(json.dumps(report))
    return 0 if status == "passed" else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("manifest", "split-record", "checkpoint", "onnx", "output"):
        parser.add_argument("--" + name, required=True)
    return run(parser.parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
