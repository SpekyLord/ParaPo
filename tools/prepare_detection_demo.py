#!/usr/bin/env python3
"""Prepare the local M3 manifest as a one-class YOLO dataset."""

import argparse
import json
import shutil
import struct
from pathlib import Path

import experiment


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
    args = parser.parse_args(argv)
    try:
        print(json.dumps({"status": "prepared", **prepare(args.manifest, args.output)}, sort_keys=True))
        return 0
    except experiment.InvalidInput as exc:
        parser.error(str(exc))


if __name__ == "__main__":
    raise SystemExit(main())
