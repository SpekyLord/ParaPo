#!/usr/bin/env python3
"""Run the local M3 detector/OCR demo; optional Ultralytics and EasyOCR packages are needed."""

import argparse
import json
import math
import sys
from pathlib import Path

import experiment
from route_text import interpret, canonical_truth


def match_signs(detections, signs, iou_threshold=0.5):
    """Assign each labeled sign at most once; extra detector crops retain no truth."""
    used, matches = set(), {}
    for index in sorted(range(len(detections)), key=lambda i: (-detections[i]["score"], i)):
        best = max(((experiment.box_iou(detections[index]["box"], sign["box"]), j)
                    for j, sign in enumerate(signs) if j not in used), default=(0, -1))
        if best[0] >= iou_threshold:
            matches[index] = best[1]
            used.add(best[1])
    return matches


def ocr_record(sample_id, source, box, text, sign=None, sign_index=None):
    prediction = interpret(text)
    truth_text = sign.get("visible_text", "") if sign else ""
    known = bool(sign and truth_text.strip() and sign.get("transcription_status") != "uncertain")
    truth_codes = interpret(truth_text)["printed_codes"] if known and sign.get("route_id") else None
    return {"schema_version": 2, "sample_id": sample_id, "crop_source": source, "crop_box": box,
            "matched_sign_index": sign_index, "truth_match_status": "matched" if sign else "unmatched",
            "truth_text": truth_text, "truth_text_known": known, "predicted_text": text,
            "truth_printed_codes": truth_codes,
            "truth_canonical_route_id": canonical_truth(sign.get("route_id")) if sign else None,
            "truth_route_label": sign.get("route_id") if sign else None,
            **prediction}


def valid_score_threshold(value):
    return math.isfinite(value) and 0 <= value <= 1


def normalized_detections(boxes):
    detections = []
    for box in boxes or []:
        coords = [round(float(value), 3) for value in box.xyxy[0].tolist()]
        score = round(float(box.conf[0]), 6)
        if not all(math.isfinite(value) for value in coords + [score]):
            raise experiment.InvalidInput("model returned a non-finite prediction")
        if not experiment.validate_box(coords):
            continue
        detections.append({"box": coords, "score": score, "pattern_id": None, "direction": None})
    return detections


def selected_splits(value):
    return ("validation", "test") if value == "both" else (value,)


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
    splits = selected_splits(args.split)
    ocr_split = "test" if "test" in splits else splits[0]
    targets = [output / f"predictions-{split}.jsonl" for split in splits]
    targets.append(output / f"ocr-{ocr_split}.jsonl")
    if any(path.exists() for path in targets):
        raise experiment.InvalidInput("demo output already exists; choose a new folder")
    if not Path(args.checkpoint).is_file():
        raise experiment.InvalidInput("checkpoint file is missing")

    try:
        import cv2
        import easyocr
        import torch
        from ultralytics import YOLO
        from check_export_parity import prepare_input, source_box
    except ImportError as exc:
        raise experiment.InvalidInput("install the pinned Ultralytics, EasyOCR, OpenCV, and PyTorch packages") from exc

    root = Path(args.manifest).resolve().parent
    model = YOLO(str(Path(args.checkpoint).resolve()))
    predictions = {}
    for split in splits:
        split_rows = []
        for sample in (row for row in samples if row["split"] == split):
            image = (root / sample.get("image_ref", "")).resolve()
            if not image.is_relative_to(root) or not image.is_file():
                raise experiment.InvalidInput("sample image reference is invalid")
            pixels = cv2.imread(str(image))
            if pixels is None:
                raise experiment.InvalidInput("cannot decode detector sample")
            tensor = torch.from_numpy(prepare_input(pixels))
            result = model.predict(source=tensor, imgsz=320, rect=False, conf=0.001, iou=0.7,
                                  max_det=300, device=args.device, verbose=False)[0]
            detections = normalized_detections(result.boxes)
            for detection in detections:
                detection["box"] = source_box(detection["box"], pixels.shape[1], pixels.shape[0])
            detections = [d for d in detections if experiment.validate_box(d["box"])]
            split_rows.append({"sample_id": sample["sample_id"], "detections": detections})
        predictions[split] = split_rows

    reader = easyocr.Reader(["en"], gpu=args.device != "cpu" and torch.cuda.is_available(), verbose=False)
    ocr_rows_for_split = [row for row in samples if row["split"] == ocr_split]
    detection_by_id = {row["sample_id"]: row["detections"] for row in predictions[ocr_split]}
    ocr_rows = []
    for sample in ocr_rows_for_split:
        image_path = (root / sample["image_ref"]).resolve()
        image = cv2.imread(str(image_path))
        if image is None:
            raise experiment.InvalidInput("cannot read sample image for OCR")
        for sign_index, sign in enumerate(sample.get("signs", [])):
            crop_source, box = "ground_truth", sign["box"]
            x1, y1, x2, y2 = (int(round(value)) for value in box)
            crop = image[max(0, y1):min(image.shape[0], y2), max(0, x1):min(image.shape[1], x2)]
            if crop.size == 0:
                raise experiment.InvalidInput("ground-truth sign crop is empty")
            crop = cv2.resize(crop, None, fx=3, fy=3, interpolation=cv2.INTER_CUBIC)
            text = " ".join(reader.readtext(crop, detail=0, paragraph=True))
            ocr_rows.append(ocr_record(sample["sample_id"], crop_source, box, text, sign, sign_index))
        selected = [d for d in detection_by_id[sample["sample_id"]] if d["score"] >= args.score_threshold]
        matched = match_signs(selected, sample.get("signs", []))
        for detection_index, detection in enumerate(selected):
            box = detection["box"]
            x1, y1, x2, y2 = (int(round(value)) for value in box)
            crop = image[max(0, y1):min(image.shape[0], y2), max(0, x1):min(image.shape[1], x2)]
            if crop.size == 0:
                raise experiment.InvalidInput("detector sign crop is empty")
            crop = cv2.resize(crop, None, fx=3, fy=3, interpolation=cv2.INTER_CUBIC)
            text = " ".join(reader.readtext(crop, detail=0, paragraph=True))
            sign_index = matched.get(detection_index)
            sign = sample["signs"][sign_index] if sign_index is not None else None
            ocr_rows.append(ocr_record(sample["sample_id"], "detector", box, text, sign, sign_index))

    output.mkdir(parents=True, exist_ok=True)
    for split in splits:
        text = "".join(json.dumps(row, separators=(",", ":")) + "\n" for row in predictions[split])
        experiment.atomic_write(output / f"predictions-{split}.jsonl", text, exclusive=True)
    text = "".join(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n" for row in ocr_rows)
    experiment.atomic_write(output / f"ocr-{ocr_split}.jsonl", text, exclusive=True)
    print(json.dumps({"status": "completed", "evidence_kind": header.get("evidence_kind", "real"),
                      "validation_samples": len(predictions.get("validation", [])),
                      "test_samples": len(predictions.get("test", [])),
                      "ocr_split": ocr_split, "ocr_crops": len(ocr_rows)}, sort_keys=True))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--split-record", required=True)
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--device", default="0", help="Ultralytics device; default is CUDA device 0")
    parser.add_argument("--split", choices=("both", "validation", "test"), required=True,
                        help="split(s) to run; use validation to avoid reading held-out test samples")
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
