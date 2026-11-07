#!/usr/bin/env python3
"""Run the local M3 detector/OCR demo; optional Ultralytics and EasyOCR packages are needed."""

import argparse
import json
import math
import sys
from pathlib import Path

import experiment


def matched_truth_text(box, signs, iou_threshold=0.5):
    best = max(((experiment.box_iou(box, sign["box"]), sign) for sign in signs),
               default=(0, None), key=lambda item: item[0])
    return best[1].get("visible_text", "") if best[1] is not None and best[0] >= iou_threshold else ""


def valid_score_threshold(value):
    return math.isfinite(value) and 0 <= value <= 1


def run(args):
    if not valid_score_threshold(args.score_threshold):
        raise experiment.InvalidInput("score threshold must be between 0 and 1")
    rows = experiment.read_jsonl(args.manifest)
    header, samples = experiment.validate_manifest(rows)
    experiment.check_leakage(samples)
    frozen = experiment.read_json(args.split_record)
    if (frozen.get("schema_version") != experiment.FORMAT_VERSION
            or frozen.get("dataset_version") != header["dataset_version"]
            or frozen.get("manifest_sha256") != experiment.sha256_file(args.manifest)):
        raise experiment.InvalidInput("manifest differs from frozen split")

    output = Path(args.output_dir)
    targets = [output / f"predictions-{split}.jsonl" for split in ("validation", "test")]
    targets.append(output / "ocr-test.jsonl")
    if any(path.exists() for path in targets):
        raise experiment.InvalidInput("demo output already exists; choose a new folder")
    if not Path(args.checkpoint).is_file():
        raise experiment.InvalidInput("checkpoint file is missing")

    try:
        import cv2
        import easyocr
        import torch
        from ultralytics import YOLO
    except ImportError as exc:
        raise experiment.InvalidInput("install the pinned Ultralytics, EasyOCR, OpenCV, and PyTorch packages") from exc

    root = Path(args.manifest).resolve().parent
    model = YOLO(str(Path(args.checkpoint).resolve()))
    predictions = {}
    for split in ("validation", "test"):
        split_rows = []
        for sample in (row for row in samples if row["split"] == split):
            image = (root / sample.get("image_ref", "")).resolve()
            if not image.is_relative_to(root) or not image.is_file():
                raise experiment.InvalidInput("sample image reference is invalid")
            result = model.predict(source=str(image), imgsz=320, conf=0.001, iou=0.7,
                                  max_det=300, device=args.device, verbose=False)[0]
            detections = []
            if result.boxes is not None:
                for box in result.boxes:
                    coords = [round(float(value), 3) for value in box.xyxy[0].tolist()]
                    score = round(float(box.conf[0]), 6)
                    if not all(math.isfinite(value) for value in coords + [score]):
                        raise experiment.InvalidInput("model returned a non-finite prediction")
                    detections.append({"box": coords, "score": score, "pattern_id": None, "direction": None})
            split_rows.append({"sample_id": sample["sample_id"], "detections": detections})
        predictions[split] = split_rows

    reader = easyocr.Reader(["en"], gpu=args.device != "cpu" and torch.cuda.is_available(), verbose=False)
    test_rows = [row for row in samples if row["split"] == "test"]
    detection_by_id = {row["sample_id"]: row["detections"] for row in predictions["test"]}
    ocr_rows = []
    for sample in test_rows:
        image_path = (root / sample["image_ref"]).resolve()
        image = cv2.imread(str(image_path))
        if image is None:
            raise experiment.InvalidInput("cannot read sample image for OCR")
        for sign in sample.get("signs", []):
            crop_source, box = "ground_truth", sign["box"]
            x1, y1, x2, y2 = (int(round(value)) for value in box)
            crop = image[max(0, y1):min(image.shape[0], y2), max(0, x1):min(image.shape[1], x2)]
            if crop.size == 0:
                raise experiment.InvalidInput("ground-truth sign crop is empty")
            crop = cv2.resize(crop, None, fx=3, fy=3, interpolation=cv2.INTER_CUBIC)
            text = " ".join(reader.readtext(crop, detail=0, paragraph=True)).strip()
            ocr_rows.append({"sample_id": sample["sample_id"], "crop_source": crop_source,
                             "truth_text": sign.get("visible_text", ""), "predicted_text": text,
                             "truth_route_code": sign.get("route_id"), "predicted_route_code": None})
        for detection in detection_by_id[sample["sample_id"]]:
            if detection["score"] < args.score_threshold:
                continue
            box = detection["box"]
            x1, y1, x2, y2 = (int(round(value)) for value in box)
            crop = image[max(0, y1):min(image.shape[0], y2), max(0, x1):min(image.shape[1], x2)]
            if crop.size == 0:
                raise experiment.InvalidInput("detector sign crop is empty")
            crop = cv2.resize(crop, None, fx=3, fy=3, interpolation=cv2.INTER_CUBIC)
            text = " ".join(reader.readtext(crop, detail=0, paragraph=True)).strip()
            ocr_rows.append({"sample_id": sample["sample_id"], "crop_source": "detector",
                             "truth_text": matched_truth_text(box, sample.get("signs", [])),
                             "predicted_text": text, "truth_route_code": None, "predicted_route_code": None})

    output.mkdir(parents=True, exist_ok=True)
    for split in ("validation", "test"):
        text = "".join(json.dumps(row, separators=(",", ":")) + "\n" for row in predictions[split])
        experiment.atomic_write(output / f"predictions-{split}.jsonl", text, exclusive=True)
    text = "".join(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n" for row in ocr_rows)
    experiment.atomic_write(output / "ocr-test.jsonl", text, exclusive=True)
    print(json.dumps({"status": "completed", "evidence_kind": header.get("evidence_kind", "real"),
                      "validation_samples": len(predictions["validation"]), "test_samples": len(predictions["test"]),
                      "test_ocr_crops": len(ocr_rows)}, sort_keys=True))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--split-record", required=True)
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--device", default="0", help="Ultralytics device; default is CUDA device 0")
    parser.add_argument("--score-threshold", type=float, default=0.25,
                        help="validation-selected cutoff for evaluation and detector OCR crops")
    args = parser.parse_args(argv)
    try:
        run(args)
    except experiment.InvalidInput as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
