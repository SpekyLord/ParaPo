#!/usr/bin/env python3
"""Prepare the local M3 manifest as a one-class YOLO dataset."""

import argparse
import json
import shutil
import struct
import hashlib
import zipfile
from pathlib import Path

import experiment


def prepare_ocr_crops(manifest_path, output_path):
    """Local lossless OCR fixtures, with labels stored separately from recognition pixels."""
    from PIL import Image, ImageOps
    manifest_path, output_path = Path(manifest_path), Path(output_path)
    _, samples = experiment.validate_manifest(experiment.read_jsonl(manifest_path))
    wanted = {f"m3-image-{n}" for n in (18, 21, 24, 27, 28, 31, 32, 34, 38, 39)}
    if output_path.exists():
        raise experiment.InvalidInput("OCR output already exists; choose a fresh folder")
    root = manifest_path.resolve().parent
    prepared = []
    for sample in samples:
        if sample["sample_id"] not in wanted:
            continue
        source = (root / sample["image_ref"]).resolve()
        if not source.is_relative_to(root) or not source.is_file():
            raise experiment.InvalidInput("invalid OCR source image")
        with Image.open(source) as original:
            image = ImageOps.exif_transpose(original).convert("RGB")
            for index, sign in enumerate(sample["signs"]):
                box = [round(value) for value in sign["box"]]
                x1, y1, x2, y2 = box
                if not (0 <= x1 < x2 <= image.width and 0 <= y1 < y2 <= image.height):
                    raise experiment.InvalidInput("OCR crop is outside the upright image")
                prepared.append((sample, sign, source, box, image.crop(box), index))
    if {sample["sample_id"] for sample, *_ in prepared} != wanted:
        raise experiment.InvalidInput("OCR fixture samples are missing")
    output_path.mkdir(parents=True)
    records = []
    for sample, sign, source, box, crop, index in prepared:
        name = f"{sample['sample_id']}-{index}.png"
        crop.save(output_path / name)
        records.append({"sample_id": sample["sample_id"], "sign_index": index, "crop_file": name,
                        "image_ref": sample["image_ref"], "source_sha256": experiment.sha256_file(source),
                        "crop_sha256": experiment.sha256_file(output_path / name), "box": box,
                        "expected_text": sign.get("visible_text", ""),
                        "transcription_status": sign.get("transcription_status", "reviewed-manifest"),
                        "truth_route_id": sign.get("route_id"), "conditions": sample.get("conditions", [])})
    (output_path / "manifest.json").write_text(json.dumps(records, indent=2, ensure_ascii=False), encoding="utf-8")
    with zipfile.ZipFile(output_path / "fixtures.zip", "x", zipfile.ZIP_DEFLATED) as archive:
        for path in [output_path / "manifest.json", *(output_path / row["crop_file"] for row in records)]:
            archive.write(path, path.name)
    return {"crops": len(records), "manifest_sha256": hashlib.sha256(manifest_path.read_bytes()).hexdigest()}


def image_size(path):
    try:
        data = Path(path).read_bytes()
    except OSError as exc:
        raise experiment.InvalidInput("cannot read sample image") from exc
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        if len(data) < 24 or data[12:16] != b"IHDR":
            raise experiment.InvalidInput("invalid PNG sample")
        return struct.unpack(">II", data[16:24])
    if not data.startswith(b"\xff\xd8"):
        raise experiment.InvalidInput("demo images must be PNG or JPEG")
    offset = 2
    frame_markers = {0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF}
    while offset < len(data):
        if data[offset] != 0xFF:
            raise experiment.InvalidInput("invalid JPEG sample")
        while offset < len(data) and data[offset] == 0xFF:
            offset += 1
        if offset >= len(data):
            break
        marker = data[offset]
        offset += 1
        if marker in {0x01, 0xD8, 0xD9, *range(0xD0, 0xD8)}:
            continue
        if offset + 2 > len(data):
            break
        length = struct.unpack(">H", data[offset:offset + 2])[0]
        if length < 2 or offset + length > len(data):
            break
        if marker in frame_markers:
            if length < 7:
                break
            height, width = struct.unpack(">HH", data[offset + 3:offset + 7])
            if width and height:
                return width, height
            break
        if marker == 0xDA:
            break
        offset += length
    raise experiment.InvalidInput("cannot read sample image dimensions")


def prepare(manifest_path, output_path):
    manifest_path, output_path = Path(manifest_path), Path(output_path)
    rows = experiment.read_jsonl(manifest_path)
    header, samples = experiment.validate_manifest(rows)
    experiment.check_leakage(samples)
    if output_path.exists():
        raise experiment.InvalidInput("output already exists; choose a new dataset folder")

    root = manifest_path.parent.resolve()
    prepared = []
    for index, row in enumerate(samples, 1):
        image_ref = row.get("image_ref")
        if not isinstance(image_ref, str) or not image_ref:
            raise experiment.InvalidInput("sample image reference is missing")
        source = (root / image_ref).resolve()
        if not source.is_relative_to(root) or not source.is_file():
            raise experiment.InvalidInput("sample image reference is invalid")
        width, height = image_size(source)
        for vehicle in row.get("vehicles", []):
            x1, y1, x2, y2 = vehicle["box"]
            if not (0 <= x1 < x2 <= width and 0 <= y1 < y2 <= height):
                raise experiment.InvalidInput("vehicle context box is outside its image")
        labels = []
        for sign in row.get("signs", []):
            x1, y1, x2, y2 = sign["box"]
            if not (0 <= x1 < x2 <= width and 0 <= y1 < y2 <= height):
                raise experiment.InvalidInput("sign box is outside its image")
            labels.append(f"0 {(x1 + x2) / (2 * width):.8f} {(y1 + y2) / (2 * height):.8f} {(x2 - x1) / width:.8f} {(y2 - y1) / height:.8f}")
        prepared.append((index, row, source, labels))

    output_path.mkdir(parents=True)
    for split in ("train", "validation", "test"):
        (output_path / "images" / split).mkdir(parents=True)
        (output_path / "labels" / split).mkdir(parents=True)
    sample_map = []
    for index, row, source, labels in prepared:
        split = row["split"]
        suffix = source.suffix.lower()
        if suffix not in {".png", ".jpg", ".jpeg"}:
            raise experiment.InvalidInput("demo images must use PNG or JPEG files")
        filename = f"sample-{index:04d}{suffix}"
        shutil.copyfile(source, output_path / "images" / split / filename)
        (output_path / "labels" / split / f"sample-{index:04d}.txt").write_text("".join(f"{label}\n" for label in labels), encoding="utf-8")
        sample_map.append({"sample_id": row["sample_id"], "split": split, "image": filename})

    dataset_root = output_path.resolve().as_posix()
    (output_path / "data.yaml").write_text(
        f"path: {json.dumps(dataset_root)}\ntrain: images/train\nval: images/validation\ntest: images/test\nnc: 1\nnames: [route_sign]\n",
        encoding="utf-8",
    )
    (output_path / "sample-map.json").write_text(json.dumps(sample_map, indent=2) + "\n", encoding="utf-8")
    (output_path / "manifest-sha256.txt").write_text(experiment.sha256_file(manifest_path) + "\n", encoding="ascii")
    return {"samples": len(samples), "splits": {split: sum(row["split"] == split for row in samples) for split in ("train", "validation", "test")}, "dataset_version": header["dataset_version"]}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--ocr-crops", action="store_true", help="prepare the ten local phone OCR fixtures instead of YOLO labels")
    args = parser.parse_args(argv)
    try:
        action = prepare_ocr_crops if args.ocr_crops else prepare
        print(json.dumps({"status": "prepared", **action(args.manifest, args.output)}, sort_keys=True))
        return 0
    except experiment.InvalidInput as exc:
        parser.error(str(exc))


if __name__ == "__main__":
    raise SystemExit(main())
