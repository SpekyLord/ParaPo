#!/usr/bin/env python3
"""Bounded local ML Kit crop probe; prepared pixels and expected text remain separate."""

import argparse
from collections import defaultdict
import json
import math
from pathlib import Path
import random
import zipfile

import experiment
from route_text import interpret, canonical_truth

VARIANTS = ("original", "padding", "contrast", "wide_2x")


def prepare(fixtures, output):
    from PIL import Image, ImageOps
    fixtures, output = Path(fixtures).resolve(), Path(output)
    samples = json.loads((fixtures / "manifest.json").read_text(encoding="utf-8"))
    if not isinstance(samples, list) or len(samples) != 10 or not all(isinstance(row, dict) for row in samples):
        raise experiment.InvalidInput("this probe uses the existing ten reviewed crops")
    if len({(row["sample_id"], row["sign_index"]) for row in samples}) != 10:
        raise experiment.InvalidInput("reviewed crop identities must be unique")
    if output.exists():
        raise experiment.InvalidInput("choose a fresh probe directory")
    prepared = []
    for sample in samples:
        source = (fixtures / sample["crop_file"]).resolve()
        if source.parent != fixtures or experiment.sha256_file(source) != sample["crop_sha256"]:
            raise experiment.InvalidInput("fixture path or hash differs")
        with Image.open(source) as image:
            original = image.convert("RGB")
            border = max(16, round(original.height * .2))
            images = (original.copy(), ImageOps.expand(original, border, (114, 114, 114)),
                      ImageOps.autocontrast(ImageOps.grayscale(original), cutoff=1).convert("RGB"),
                      original.resize((original.width * 2, original.height), Image.Resampling.BICUBIC))
            for variant, pixels in zip(VARIANTS, images):
                record = {**sample, "variant_id": variant, "source_crop_sha256": sample["crop_sha256"],
                          "crop_file": f"{sample['sample_id']}-{sample['sign_index']}-{variant}.png",
                          "preprocessing": variant, "evidence_kind": "development_still_crop_probe"}
                prepared.append((record, pixels))
    output.mkdir(parents=True)
    records = []
    for record, pixels in prepared:
        pixels.save(output / record["crop_file"])
        record["crop_sha256"] = experiment.sha256_file(output / record["crop_file"])
        records.append(record)
    random.Random(42).shuffle(records)
    # Reverse the second pass to expose order/cold-start effects without more variants.
    for order, ordered in (("forward", records), ("reverse", list(reversed(records)))):
        (output / f"manifest-{order}.json").write_text(json.dumps(ordered, indent=2), encoding="utf-8")
        with zipfile.ZipFile(output / f"fixtures-{order}.zip", "x", zipfile.ZIP_DEFLATED) as archive:
            archive.write(output / f"manifest-{order}.json", "manifest.json")
            for record in ordered:
                archive.write(output / record["crop_file"], record["crop_file"])
    return {"cases": len(records), "unique_photos": len(samples), "passes": 2, "variants": VARIANTS}


def scored_row(record):
    if not isinstance(record.get("raw_text"), str) or record.get("variant_id") not in VARIANTS:
        raise experiment.InvalidInput("invalid probe observation")
    prediction = interpret(record["raw_text"])
    known = bool(record.get("expected_text", "").strip()) and record.get("transcription_status") != "uncertain"
    truth_codes = interpret(record.get("expected_text", ""))["printed_codes"] if known and record.get("truth_route_id") else None
    return {"crop_source": "ground_truth", "truth_text": record.get("expected_text", ""),
            "truth_text_known": known, "predicted_text": record["raw_text"],
            "truth_printed_codes": truth_codes,
            "truth_canonical_route_id": canonical_truth(record.get("truth_route_id")),
            **prediction, "extraction_status": "error" if record.get("error") else "completed"}


def report(paths):
    by_variant = defaultdict(list)
    cases = []
    expected_cases = None
    unique_photos, unique_crops = set(), set()
    versions, phones, policies = set(), set(), set()
    for path in paths:
        rows = experiment.read_jsonl(path)
        identities = {(row["sample_id"], row["sign_index"], row["variant_id"], row["crop_sha256"]) for row in rows}
        if len(rows) not in (40, 60) or len(identities) != len(rows) or expected_cases is not None and identities != expected_cases:
            raise experiment.InvalidInput("each pass must cover the same bounded screening or confirmation crops")
        families = VARIANTS if len(rows) == 40 else ("original", "padding")
        if {row["variant_id"] for row in rows} != set(families):
            raise experiment.InvalidInput("incorrect variants for this probe stage")
        by_crop = defaultdict(set)
        for row in rows:
            by_crop[(row["sample_id"], row["sign_index"])].add(row["variant_id"])
        if any(values != set(families) for values in by_crop.values()):
            raise experiment.InvalidInput("each sign needs all comparison variants")
        expected_cases = identities
        for row in rows:
            versions.add(row.get("ocr_version"))
            phones.add(row.get("android_fingerprint"))
            policies.add(row.get("reader_input_policy", "raw-v1"))
            unique_photos.add(row["sample_id"])
            unique_crops.add((row["sample_id"], row["sign_index"]))
            scored = scored_row(row)
            by_variant[row["variant_id"]].append((row, scored))
            cases.append({"run": Path(path).name, "sample_id": row["sample_id"], "sign_index": row["sign_index"], "variant": row["variant_id"],
                          "raw_text": row["raw_text"], "error": row.get("error"),
                          "text_block_count": row.get("text_block_count"), "route_status": scored["route_status"],
                          "canonical_route_id": scored["canonical_route_id"],
                          "truth_route_id": row.get("truth_route_id")})
    if len(versions) != 1 or len(phones) != 1 or len(policies) != 1 or not all(versions) or not all(phones):
        raise experiment.InvalidInput("keep OCR, input policy, and phone versions fixed")
    summaries = {}
    for variant, observations in by_variant.items():
        rows = [scored for _, scored in observations]
        wrong, unknown, durations = [], [], []
        for source, scored in observations:
            truth = source.get("truth_route_id")
            if scored["canonical_route_id"]:
                if truth and truth != "unknown" and scored["canonical_route_id"] != canonical_truth(truth):
                    wrong.append(source["sample_id"])
                elif not truth or truth == "unknown":
                    unknown.append(source["sample_id"])
            duration = source.get("ocr_duration_ms")
            if not isinstance(duration, (int, float)) or not math.isfinite(duration) or duration < 0:
                raise experiment.InvalidInput("OCR task duration must be finite and non-negative")
            durations.append(duration)
        summaries[variant] = {**experiment.ocr_metrics(rows, "ground_truth"),
                              "empty_observations": sum(not source["raw_text"].strip() and not source.get("error") for source, _ in observations),
                              "wrong_supported_predictions": wrong, "supported_predictions_on_unknown_truth": unknown,
                              "ocr_task_median_ms": experiment.percentile(durations, .5),
                              "ocr_task_p95_ms": experiment.percentile(durations, .95)}
    return {"evidence_kind": "development_still_crop_probe", "unique_photos": len(unique_photos), "unique_crops": len(unique_crops), "passes": len(paths),
            "limits": "Repeated observations are not independent encounters; variant preprocessing was performed on PC; timing covers the reader call, including minimum-size padding if enabled, and excludes capture/decode/display.",
            "ocr_version": next(iter(versions)), "reader_input_policy": next(iter(policies)), "phone": next(iter(phones)), "variants": summaries, "cases": cases}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    prep = commands.add_parser("prepare")
    prep.add_argument("--fixtures", required=True)
    prep.add_argument("--output", required=True)
    scoring = commands.add_parser("report")
    scoring.add_argument("--results", nargs="+", required=True)
    scoring.add_argument("--output", required=True)
    args = parser.parse_args()
    try:
        if args.command == "prepare":
            print(json.dumps(prepare(args.fixtures, args.output)))
        else:
            experiment.atomic_write(args.output, json.dumps(report(args.results), indent=2) + "\n", exclusive=True)
    except experiment.InvalidInput as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
