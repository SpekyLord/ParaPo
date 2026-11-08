#!/usr/bin/env python3
"""Offline dataset and evaluation helpers for ParaPo M3.

The commands validate research records; they do not collect data, train models,
or turn synthetic fixtures into candidate measurements.
"""

import argparse
import contextlib
import hashlib
import io
import json
import math
import re
import shutil
import sys
import tempfile
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path


FORMAT_VERSION = 1
SPLITS = {"train", "validation", "test"}
DETECTORS = {"YOLOv8n", "SSD MobileNetV2", "EfficientDet-Lite0"}
OCR_SOURCES = {"ground_truth", "detector"}


def is_sha256(value):
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value) is not None


class InvalidInput(ValueError):
    pass


def read_jsonl(path):
    try:
        with Path(path).open(encoding="utf-8") as stream:
            rows = []
            for line_number, line in enumerate(stream, 1):
                if not line.strip():
                    continue
                try:
                    row = json.loads(line)
                except json.JSONDecodeError as exc:
                    raise InvalidInput(f"invalid JSONL at line {line_number}") from exc
                if not isinstance(row, dict):
                    raise InvalidInput(f"invalid JSONL record at line {line_number}")
                rows.append(row)
            return rows
    except OSError as exc:
        raise InvalidInput("cannot read input file") from exc


def read_json(path):
    try:
        value = json.loads(Path(path).read_text(encoding="utf-8"))
        if not isinstance(value, dict):
            raise InvalidInput("JSON record must be an object")
        return value
    except InvalidInput:
        raise
    except (OSError, json.JSONDecodeError) as exc:
        raise InvalidInput("cannot read JSON input") from exc


def sha256_file(path):
    digest = hashlib.sha256()
    try:
        with Path(path).open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
    except OSError as exc:
        raise InvalidInput("cannot hash input file") from exc
    return digest.hexdigest()


def validate_box(box):
    return (
        isinstance(box, list) and len(box) == 4
        and all(isinstance(value, (int, float)) and math.isfinite(value) for value in box)
        and box[0] < box[2] and box[1] < box[3]
    )


def validate_manifest(rows):
    if not rows or rows[0].get("record_type") != "manifest":
        raise InvalidInput("manifest header is missing")
    header = rows[0]
    if header.get("schema_version") != FORMAT_VERSION or not header.get("dataset_version"):
        raise InvalidInput("unsupported or incomplete manifest header")
    samples = rows[1:]
    if not samples:
        raise InvalidInput("manifest has no samples")
    ids = set()
    encounters, sources, sessions = {}, {}, {}
    required = ("sample_id", "encounter_id", "session_id", "source_id", "ground_truth_ref", "permission_ref", "split")
    for row in samples:
        if row.get("record_type", "sample") != "sample" or any(not isinstance(row.get(key), str) or not row[key] for key in required):
            raise InvalidInput("sample is missing a required field")
        sample_id = row["sample_id"]
        if sample_id in ids:
            raise InvalidInput("duplicate sample identifier")
        ids.add(sample_id)
        if row["split"] not in SPLITS:
            raise InvalidInput("invalid split")
        if not isinstance(row.get("augmentation", False), bool):
            raise InvalidInput("augmentation must be boolean")
        if not isinstance(row.get("conditions", []), list) or any(not isinstance(c, str) or not re.fullmatch(r"[a-z0-9_-]{1,40}", c) for c in row.get("conditions", [])):
            raise InvalidInput("conditions must be safe categorical labels")
        if row.get("augmentation", False) and row["split"] != "train":
            raise InvalidInput("augmentation is only allowed in training")
        for key, registry in (("encounter_id", encounters), ("session_id", sessions)):
            prior = registry.setdefault(row[key], row["split"])
            if prior != row["split"]:
                raise InvalidInput(f"{key} crosses splits")
        source = row.get("derivative_of") or row["source_id"]
        prior = sources.setdefault(source, row["split"])
        if prior != row["split"]:
            raise InvalidInput("source derivatives cross splits")
        if row.get("vehicle_split_exception") and not isinstance(row["vehicle_split_exception"], str):
            raise InvalidInput("vehicle split exception must document its reason")
        if row.get("direction") not in (None, "unknown") and not isinstance(row.get("direction"), str):
            raise InvalidInput("direction must be text, unknown, or null")
        signs = row.get("signs", [])
        if not isinstance(signs, list):
            raise InvalidInput("signs must be a list")
        vehicles = row.get("vehicles", [])
        if not isinstance(vehicles, list) or any(not isinstance(vehicle, dict) or not validate_box(vehicle.get("box"))
                                                or not isinstance(vehicle.get("owner_id"), str) or not vehicle["owner_id"] for vehicle in vehicles):
            raise InvalidInput("owner context needs valid vehicle boxes and IDs")
        owner_ids = {vehicle["owner_id"] for vehicle in vehicles}
        if len(owner_ids) != len(vehicles):
            raise InvalidInput("duplicate owner ID within photo")
        review = row.get("annotation_review")
        if review is not None and not isinstance(review, dict):
            raise InvalidInput("annotation review note must be an object")
        if isinstance(review, dict) and review.get("disagreement") and not review.get("resolution_ref"):
            raise InvalidInput("annotation disagreement needs a resolution reference")
        for sign in signs:
            if not isinstance(sign, dict):
                raise InvalidInput("sign annotations must be objects")
            if sign.get("owner_id") is not None and sign["owner_id"] not in owner_ids:
                raise InvalidInput("sign owner reference is unknown")
            if not validate_box(sign.get("box")):
                raise InvalidInput("invalid sign box")
            if not isinstance(sign.get("visible_text", ""), str):
                raise InvalidInput("visible transcription must be text")
            if sign.get("pattern_id") is not None and not isinstance(sign["pattern_id"], str):
                raise InvalidInput("sign pattern label must be text or null")
    # Check vehicle grouping after all rows, without retaining internal fields.
    vehicle_splits = defaultdict(set)
    for row in samples:
        if row.get("vehicle_id"):
            vehicle_splits[row["vehicle_id"]].add(row["split"])
    for row in samples:
        if row.get("vehicle_id") and len(vehicle_splits[row["vehicle_id"]]) > 1 and not row.get("vehicle_split_exception"):
            raise InvalidInput("vehicle crosses splits without a documented exception")
    return header, samples


def check_leakage(samples):
    by_encounter, by_session, by_source, by_vehicle = (defaultdict(set) for _ in range(4))
    for row in samples:
        split = row["split"]
        by_encounter[row["encounter_id"]].add(split)
        by_session[row["session_id"]].add(split)
        by_source[row.get("derivative_of") or row["source_id"]].add(split)
        if row.get("vehicle_id") and not row.get("vehicle_split_exception"):
            by_vehicle[row["vehicle_id"]].add(split)
    if any(len(splits) > 1 for groups in (by_encounter, by_session, by_source, by_vehicle) for splits in groups.values()):
        raise InvalidInput("split leakage detected")


def wilson(successes, total):
    if total == 0:
        return None
    z = 1.959963984540054
    p = successes / total
    denominator = 1 + z * z / total
    center = (p + z * z / (2 * total)) / denominator
    margin = z * math.sqrt(p * (1 - p) / total + z * z / (4 * total * total)) / denominator
    return [round(max(0, center - margin), 6), round(min(1, center + margin), 6)]


def levenshtein(left, right):
    previous = list(range(len(right) + 1))
    for i, a in enumerate(left, 1):
        current = [i]
        for j, b in enumerate(right, 1):
            current.append(min(current[-1] + 1, previous[j] + 1, previous[j - 1] + (a != b)))
        previous = current
    return previous[-1]


def box_iou(a, b):
    ix1, iy1 = max(a[0], b[0]), max(a[1], b[1])
    ix2, iy2 = min(a[2], b[2]), min(a[3], b[3])
    intersection = max(0, ix2 - ix1) * max(0, iy2 - iy1)
    area_a = (a[2] - a[0]) * (a[3] - a[1])
    area_b = (b[2] - b[0]) * (b[3] - b[1])
    return intersection / (area_a + area_b - intersection) if area_a + area_b > intersection else 0.0


def detection_metrics(samples, predictions, score_threshold=0.5, iou_threshold=0.5):
    truth = {row["sample_id"]: [sign["box"] for sign in row.get("signs", [])] for row in samples}
    pred_by_sample = defaultdict(list)
    for row in predictions:
        if row.get("sample_id") not in truth:
            raise InvalidInput("prediction references an unknown sample")
        for detection in row.get("detections", []):
            box, score = detection.get("box"), detection.get("score")
            if not validate_box(box) or not isinstance(score, (int, float)) or not 0 <= score <= 1:
                raise InvalidInput("invalid detection")
            pred_by_sample[row["sample_id"]].append((score, box))
    total_gt = sum(map(len, truth.values()))
    selected = [(sample, score, box) for sample, detections in pred_by_sample.items() for score, box in detections]
    selected.sort(key=lambda item: item[1], reverse=True)
    tp = fp = 0
    used = defaultdict(set)
    for sample, score, box in selected:
        if score < score_threshold:
            continue
        choices = [(box_iou(box, gt), index) for index, gt in enumerate(truth[sample]) if index not in used[sample]]
        overlap, index = max(choices, default=(0, None))
        if index is not None and overlap >= iou_threshold:
            tp += 1
            used[sample].add(index)
        else:
            fp += 1
    recall = tp / total_gt if total_gt else None
    precision = tp / (tp + fp) if tp + fp else None
    aps = []
    if total_gt:
        for threshold_step in range(50, 100, 5):
            threshold = threshold_step / 100
            used = defaultdict(set)
            ranked = []
            for sample, score, box in selected:
                choices = [(box_iou(box, gt), index) for index, gt in enumerate(truth[sample]) if index not in used[sample]]
                overlap, index = max(choices, default=(0, None))
                if index is not None and overlap >= threshold:
                    used[sample].add(index)
                    ranked.append(1)
                else:
                    ranked.append(0)
            running_tp = 0
            points = []
            for rank, correct in enumerate(ranked, 1):
                running_tp += correct
                points.append((running_tp / total_gt, running_tp / rank))
            aps.append(sum(max((p for r, p in points if r >= recall_level), default=0) for recall_level in [i / 100 for i in range(101)]) / 101)
    return {
        "ground_truth_signs": total_gt,
        "score_threshold": score_threshold,
        "iou_threshold": iou_threshold,
        "detections_at_score_threshold": tp + fp,
        "true_positives_at_iou_threshold": tp,
        "precision_at_thresholds": precision,
        "recall_at_thresholds": recall,
        "AP50": round(aps[0], 6) if aps else None,
        "AP50_95": round(sum(aps) / len(aps), 6) if aps else None,
    }


def ocr_metrics(rows, source):
    chosen = [row for row in rows if row.get("crop_source") == source]
    edits = total_chars = exact_text = text_count = 0
    printed_pairs, normalized_pairs, route_pairs = [], [], []
    not_run = errors = unknown_text = 0
    for row in chosen:
        truth, predicted = row.get("truth_text"), row.get("predicted_text")
        if not isinstance(truth, str) or not isinstance(predicted, str):
            raise InvalidInput("OCR text fields must be strings")
        if "truth_text_known" in row and not isinstance(row["truth_text_known"], bool):
            raise InvalidInput("OCR truth readability must be boolean")
        if row.get("truth_match_status", "matched") not in {"matched", "unmatched"}:
            raise InvalidInput("invalid OCR crop match status")
        for key in ("truth_canonical_route_id", "canonical_route_id"):
            if row.get(key) is not None and not isinstance(row[key], str):
                raise InvalidInput("canonical route IDs must be text or null")
        if row.get("truth_text_known", bool(truth.strip())) and row.get("truth_match_status") != "unmatched":
            edits += levenshtein(truth, predicted)
            total_chars += len(truth)
            text_count += 1
            exact_text += int(truth == predicted)
        else:
            unknown_text += 1
        status = row.get("extraction_status", "completed" if row.get("predicted_route_code") is not None else "not_run")
        if status not in {"completed", "not_run", "error"}:
            raise InvalidInput("invalid code extraction status")
        not_run += status == "not_run"
        errors += status == "error"
        if status != "completed":
            continue
        truth_codes = row.get("truth_printed_codes")
        codes = row.get("printed_codes", [])
        if truth_codes is not None:
            if not isinstance(truth_codes, list) or not all(isinstance(c, str) and re.fullmatch(r"[0-9]+", c) for c in truth_codes):
                raise InvalidInput("printed truth codes must be numeric strings")
            if not isinstance(codes, list) or not all(isinstance(c, str) and re.fullmatch(r"[0-9]+", c) for c in codes):
                raise InvalidInput("printed predictions must be numeric strings")
            printed_pairs.append((set(truth_codes), set(codes)))
            normalized_pairs.append(({c.lstrip("0") or "0" for c in truth_codes}, {c.lstrip("0") or "0" for c in codes}))
        route_id = row.get("truth_canonical_route_id")
        if route_id:
            route_pairs.append((route_id, row.get("canonical_route_id")))
    def accuracy(pairs):
        return sum(a == b for a, b in pairs) / len(pairs) if pairs else None
    return {
        "samples": len(chosen),
        "text_samples": text_count, "unknown_text_samples": unknown_text,
        "exact_text_accuracy": exact_text / text_count if text_count else None,
        "character_error_rate": edits / total_chars if total_chars else None,
        "route_code_samples": len(printed_pairs), "exact_route_code_accuracy": accuracy(printed_pairs),
        "normalized_code_accuracy": accuracy(normalized_pairs),
        "canonical_route_samples": len(route_pairs), "canonical_route_accuracy": accuracy(route_pairs),
        "unknown_canonical_route_samples": len(chosen) - sum(bool(r.get("truth_canonical_route_id")) for r in chosen),
        "extraction_not_run": not_run, "extraction_errors": errors,
        "unmatched_detector_crops": sum(r.get("truth_match_status") == "unmatched" for r in chosen),
        "unmatched_crops_with_text": sum(r.get("truth_match_status") == "unmatched" and bool(r["predicted_text"].strip()) for r in chosen),
    }


def route_metrics(samples, predictions, score_threshold=0.5):
    truth = {row["sample_id"]: row.get("signs", []) for row in samples}
    sample_rows = {row["sample_id"]: row for row in samples}
    pred_by_sample = defaultdict(list)
    for row in predictions:
        if row.get("sample_id") not in truth:
            raise InvalidInput("prediction references an unknown sample")
        pred_by_sample[row["sample_id"]].extend(d for d in row.get("detections", []) if d.get("score", 0) >= score_threshold)
    matrix, direction_matrix = Counter(), Counter()
    correct = count = direction_correct = direction_count = 0
    for sample_id, signs in truth.items():
        used = set()
        detections = sorted(pred_by_sample[sample_id], key=lambda value: value["score"], reverse=True)
        for sign in signs:
            choices = [(box_iou(sign["box"], detection["box"]), index) for index, detection in enumerate(detections) if index not in used]
            overlap, index = max(choices, default=(0, None))
            predicted = "UNKNOWN"
            if index is not None and overlap >= 0.5:
                used.add(index)
                predicted = detections[index].get("pattern_id") or "UNKNOWN"
            expected = sign.get("pattern_id") or "UNKNOWN"
            if expected.casefold() == "unknown":
                expected = "UNKNOWN"
            matrix[f"{expected}=>{predicted}"] += 1
            if expected != "UNKNOWN":
                count += 1
                correct += int(expected == predicted)
            expected_direction = sign.get("direction") or sample_rows[sample_id].get("direction") or "UNKNOWN"
            predicted_direction = (detections[index].get("direction") or "UNKNOWN") if index is not None and overlap >= 0.5 else "UNKNOWN"
            if expected_direction.casefold() == "unknown":
                expected_direction = "UNKNOWN"
            if predicted_direction.casefold() == "unknown":
                predicted_direction = "UNKNOWN"
            direction_matrix[f"{expected_direction}=>{predicted_direction}"] += 1
            if expected_direction != "UNKNOWN":
                direction_count += 1
                direction_correct += int(expected_direction == predicted_direction)
    return {"signs": sum(len(row.get("signs", [])) for row in samples),
            "pattern_evaluable_signs": count, "exact_pattern_accuracy": correct / count if count else None,
            "confusion": dict(sorted(matrix.items())),
            "direction_evaluable_signs": direction_count,
            "exact_direction_accuracy": direction_correct / direction_count if direction_count else None,
            "direction_confusion": dict(sorted(direction_matrix.items()))}


def condition_metrics(samples, predictions, score_threshold=0.5, iou_threshold=0.5):
    result = {}
    for condition in sorted({c for row in samples for c in row.get("conditions", [])}):
        subset = [row for row in samples if condition in row.get("conditions", [])]
        ids = {row["sample_id"] for row in subset}
        subset_predictions = [row for row in predictions if row.get("sample_id") in ids]
        result[condition] = {"detection": detection_metrics(subset, subset_predictions, score_threshold, iou_threshold),
                             "route_pattern": route_metrics(subset, subset_predictions, score_threshold)}
    return result


def sign_size_metrics(samples, predictions, score_threshold=0.5, iou_threshold=0.5):
    result = {}
    categories = sorted({sign["size_category"] for row in samples for sign in row.get("signs", []) if sign.get("size_category")})
    for category in categories:
        subset = []
        for row in samples:
            signs = [sign for sign in row.get("signs", []) if sign.get("size_category") == category]
            if signs:
                subset.append({**row, "signs": signs})
        ids = {row["sample_id"] for row in subset}
        result[category] = detection_metrics(subset, [row for row in predictions if row.get("sample_id") in ids],
                                             score_threshold, iou_threshold)
    return result


def encounter_metrics(rows):
    kinds = {row.get("evidence_type", "legacy_unknown") for row in rows}
    if not kinds <= {"legacy_unknown", "still_photo", "recorded_replay", "live_encounter", "synthetic"}:
        raise InvalidInput("invalid encounter evidence type")
    if len(kinds) > 1:
        return {"by_evidence_type": {kind: encounter_metrics([r for r in rows if r.get("evidence_type", "legacy_unknown") == kind])
                                     for kind in sorted(kinds)}}
    if len({row.get("encounter_id") for row in rows}) != len(rows):
        raise InvalidInput("encounter decisions must have one row per encounter")
    wrong_green = green_encounters = matching_total = matching_success = 0
    abstained = 0
    late = 0
    latencies = []
    useful_window = 0
    observed_timing = on_time = 0
    failure_stages = Counter()
    owner_counts = Counter()
    unknown_green = unresolved = 0
    stable_times = []
    for row in rows:
        events = row.get("green_events")
        if events is not None and "green_pattern_ids" in row:
            raise InvalidInput("use green events or legacy pattern IDs, not both")
        if events is None:
            events = [{"pattern_id": pattern, "owner_correctness": "unknown"} for pattern in row.get("green_pattern_ids", [])]
        if not isinstance(events, list) or any(not isinstance(event, dict) for event in events):
            raise InvalidInput("green_events must be a list of observations")
        for event in events:
            if event.get("owner_correctness", "unknown") not in {"correct", "incorrect", "unknown"}:
                raise InvalidInput("invalid green ownership result")
            if "at_ms" in event and (type(event["at_ms"]) not in (int, float) or not math.isfinite(event["at_ms"]) or event["at_ms"] < 0):
                raise InvalidInput("event time must be finite and non-negative")
            if "stable" in event and not isinstance(event["stable"], bool):
                raise InvalidInput("event stability must be boolean")
        greens = [event.get("pattern_id") for event in events]
        if not isinstance(greens, list):
            raise InvalidInput("green_pattern_ids must be a list")
        if any(not isinstance(pattern, str) or not pattern for pattern in greens):
            raise InvalidInput("green pattern IDs must be non-empty text")
        if not isinstance(row.get("definitive"), bool):
            raise InvalidInput("encounter decision must record whether the result is definitive")
        if greens and not row["definitive"]:
            raise InvalidInput("green decisions must be definitive")
        if not isinstance(row.get("eligible_matching"), bool):
            raise InvalidInput("encounter eligibility must be explicit")
        if row["eligible_matching"] and not row.get("matching_pattern_id"):
            raise InvalidInput("eligible matching encounter needs its independent pattern label")
        correct_id = row.get("matching_pattern_id")
        incorrect = bool(events) and (not row["eligible_matching"] or any(
            event["pattern_id"] != correct_id or event.get("owner_correctness") == "incorrect" for event in events))
        owner_unknown = any(event.get("owner_correctness", "unknown") == "unknown" for event in events)
        unknown_green += int(bool(events) and owner_unknown)
        assignments = row.get("owner_assignments", [event.get("owner_correctness", "unknown") for event in events])
        if not isinstance(assignments, list) or any(value not in {"correct", "incorrect", "unknown"} for value in assignments):
            raise InvalidInput("owner assignments must be correct, incorrect, or unknown")
        owner_counts.update(assignments)
        direction = row.get("direction_status", "unknown")
        if direction not in {"resolved", "unresolved", "unknown"}:
            raise InvalidInput("invalid direction status")
        unresolved += direction != "resolved"
        if greens:
            green_encounters += 1
            wrong_green += int(incorrect)
        if not row["definitive"]:
            abstained += 1
        late += int(row.get("correct_before_window_closed") is False)
        if row.get("timing_boundary") == "capture_to_display" and isinstance(row.get("latency_ms"), (int, float)) and row["latency_ms"] >= 0:
            latencies.append(row["latency_ms"])
        if isinstance(row.get("useful_window_ms"), (int, float)) and row["useful_window_ms"] >= 0:
            useful_window += 1
        if isinstance(row.get("correct_before_window_closed"), bool):
            observed_timing += 1
            on_time += int(row["correct_before_window_closed"] and bool(events) and not incorrect and not owner_unknown and direction == "resolved")
        stages = row.get("failure_stages", [])
        allowed_stages = {"localization", "crop_transformation", "ocr", "route_alias", "direction", "unsupported_sign", "timing", "ownership", "other"}
        if not isinstance(stages, list) or any(stage not in allowed_stages for stage in stages):
            raise InvalidInput("failure stages must use the documented categories")
        failure_stages.update(stages)
        for key in ("latency_ms", "useful_window_ms", "readable_at_ms", "useful_window_end_ms"):
            if key in row and (type(row[key]) not in (int, float) or not math.isfinite(row[key]) or row[key] < 0):
                raise InvalidInput("decision timing values must be finite non-negative numbers")
        correct_stable = [event["at_ms"] for event in events if row["eligible_matching"] and direction == "resolved" and event.get("pattern_id") == correct_id
                          and event.get("owner_correctness") == "correct" and event.get("stable") is True and "at_ms" in event]
        if correct_stable and "readable_at_ms" in row:
            first = min(correct_stable)
            if first < row["readable_at_ms"]:
                raise InvalidInput("stable result precedes annotated readable start")
            stable_times.append(first - row["readable_at_ms"])
        if "useful_window_end_ms" in row:
            useful_window += 1
            if "readable_at_ms" in row and row["useful_window_end_ms"] < row["readable_at_ms"]:
                raise InvalidInput("useful window ends before readable start")
            if "correct_before_window_closed" in row:
                raise InvalidInput("use timed events or a legacy window summary, not both")
            observed_timing += 1
            timely = bool(correct_stable and min(correct_stable) <= row["useful_window_end_ms"] and not incorrect and not owner_unknown)
            on_time += timely
            late += not timely
        if row.get("eligible_matching") is True:
            matching_total += 1
            matching_success += int(bool(greens) and correct_id in greens and not incorrect and not owner_unknown and direction == "resolved")
    return {
        "encounters": len(rows),
        "evidence_type": next(iter(kinds), None),
        "owner_assignments": {"attempted": sum(owner_counts.values()), "correct": owner_counts["correct"],
                              "incorrect": owner_counts["incorrect"], "unknown": owner_counts["unknown"]},
        "green_encounters_with_unknown_ownership": unknown_green,
        "wrong_green_fully_verified": unknown_green == 0,
        "unresolved_or_unknown_direction_encounters": unresolved,
        "first_stable_correct_ms_median": percentile(stable_times, 0.5),
        "first_stable_correct_ms_p95": percentile(stable_times, 0.95),
        "first_stable_correct_timed_encounters": len(stable_times),
        "wrong_green": {"count": wrong_green, "denominator": green_encounters,
                        "fraction": wrong_green / green_encounters if green_encounters else "not estimable",
                        "wilson_95": wilson(wrong_green, green_encounters)},
        "matching_recall": {"count": matching_success, "denominator": matching_total,
                            "fraction": matching_success / matching_total if matching_total else "not estimable",
                            "wilson_95": wilson(matching_success, matching_total)},
        "abstention_fraction": abstained / len(rows) if rows else None,
        "decision_coverage": sum(row["definitive"] for row in rows) / len(rows) if rows else None,
        "late_decisions": late,
        "latency_ms_median": percentile(latencies, 0.5),
        "latency_ms_p95": percentile(latencies, 0.95),
        "encounters_with_useful_window": useful_window,
        "correct_before_window_closed": {"count": on_time, "denominator": observed_timing,
                                          "fraction": on_time / observed_timing if observed_timing else "not estimable"},
        "failure_stages": dict(sorted(failure_stages.items())),
    }


def percentile(values, fraction):
    if not values:
        return None
    ordered = sorted(values)
    return ordered[max(0, math.ceil(fraction * len(ordered)) - 1)]


def validate_run(record):
    if record.get("schema_version") != FORMAT_VERSION or record.get("detector") not in DETECTORS | {"OCR-only"}:
        raise InvalidInput("run record has unsupported version or detector")
    required = ("variant", "dataset_version", "split_sha256", "evaluation_split", "ocr_engine",
                "route_catalog_version", "aliases_version", "compatibility_rules_version", "crop_policy",
                "seed", "training_budget", "tuning_budget", "input_resolution", "precision", "preprocessing",
                "decoding", "postprocessing", "runtime", "thresholds", "decision_rules",
                "threshold_tuning_split", "decision_rules_tuning_split", "dependencies", "configuration_sha256", "outcome")
    if any(record.get(key) in (None, "") for key in required):
        raise InvalidInput("run record is incomplete")
    if record["outcome"] not in {"completed", "failed"}:
        raise InvalidInput("run outcome must be completed or failed")
    if record["threshold_tuning_split"] != "validation" or record["decision_rules_tuning_split"] != "validation":
        raise InvalidInput("thresholds and decision rules must be tuned on validation only")
    if record["outcome"] == "failed" and (not record.get("failure_reason") or not record.get("evidence_ref")):
        raise InvalidInput("failed run needs its reason and retained evidence reference")
    if record["outcome"] == "completed" and record["detector"] != "OCR-only" and not record.get("checkpoint_sha256"):
        raise InvalidInput("detector checkpoint hash is required")
    if record.get("evidence_kind") != "synthetic":
        digests = [record["split_sha256"], record["configuration_sha256"]]
        if record["outcome"] == "completed" and record["detector"] != "OCR-only":
            digests.append(record.get("checkpoint_sha256"))
        if not all(is_sha256(value) for value in digests):
            raise InvalidInput("run record contains an invalid SHA-256 value")
        if not isinstance(record["dependencies"], dict) or not record["dependencies"]:
            raise InvalidInput("pinned dependency versions are required")
    thresholds = record["thresholds"]
    if not isinstance(thresholds, dict) or not all(isinstance(thresholds.get(key), (int, float)) and 0 <= thresholds[key] <= 1
                                                   for key in ("score", "iou")):
        raise InvalidInput("score and IoU thresholds must be pinned between 0 and 1")
    return record


def validate_phone(record):
    required = ("candidate", "artifact_sha256", "runtime", "acceleration", "device_model", "android_build",
                "model_size_bytes", "peak_memory_bytes", "processed_fps", "median_latency_ms", "p95_latency_ms",
                "measurement_type", "measurement_boundary", "samples_sha256")
    if any(record.get(key) in (None, "") for key in required):
        raise InvalidInput("phone record is incomplete")
    if record["measurement_type"] not in {"detector_only", "offline_replay", "end_to_end"}:
        raise InvalidInput("invalid phone measurement type")
    if record.get("evidence_kind") == "synthetic":
        raise InvalidInput("synthetic records cannot be used as phone measurements")
    if not is_sha256(record["artifact_sha256"]) or not is_sha256(record["samples_sha256"]):
        raise InvalidInput("phone record contains an invalid SHA-256 value")
    if not all(isinstance(record[key], (int, float)) and record[key] >= 0 for key in
               ("model_size_bytes", "peak_memory_bytes", "processed_fps", "median_latency_ms", "p95_latency_ms")):
        raise InvalidInput("phone measurements must be non-negative numbers")
    return record


def validate_parity(reference_rows, exported_rows, box_tolerance, score_tolerance):
    reference = {row.get("sample_id"): row.get("detections", []) for row in reference_rows}
    exported = {row.get("sample_id"): row.get("detections", []) for row in exported_rows}
    if (not reference or len(reference) != len(reference_rows) or len(exported) != len(exported_rows)
            or reference.keys() != exported.keys()):
        raise InvalidInput("parity outputs must cover identical samples")
    if not math.isfinite(box_tolerance) or not math.isfinite(score_tolerance) or box_tolerance < 0 or score_tolerance < 0:
        raise InvalidInput("parity tolerances must be finite non-negative numbers")
    compared = 0
    for sample_id in reference:
        left, right = reference[sample_id], exported[sample_id]
        if len(left) != len(right):
            raise InvalidInput("export parity detection counts differ")
        for detection in left + right:
            if not validate_box(detection.get("box")):
                raise InvalidInput("parity output contains an invalid box")
            if not isinstance(detection.get("score"), (int, float)) or not math.isfinite(detection["score"]) or not 0 <= detection["score"] <= 1:
                raise InvalidInput("parity output contains an invalid score")
        # Find a complete one-to-one correspondence; score reordering is not a box mismatch.
        candidates = [[j for j, b in sorted(enumerate(right), key=lambda pair: (-box_iou(a["box"], pair[1]["box"]), pair[0]))
                       if all(abs(x - y) <= box_tolerance for x, y in zip(a["box"], b["box"]))
                       and abs(a["score"] - b["score"]) <= score_tolerance
                       and (a.get("pattern_id"), a.get("direction")) == (b.get("pattern_id"), b.get("direction"))]
                      for a in left]
        owners = {}
        def assign(index, seen):
            for candidate in candidates[index]:
                if candidate in seen:
                    continue
                seen.add(candidate)
                if candidate not in owners or assign(owners[candidate], seen):
                    owners[candidate] = index
                    return True
            return False
        if not all(assign(index, set()) for index in range(len(left))):
            raise InvalidInput("export parity has no one-to-one box/score/label match within tolerance")
        compared += len(left)
    return compared


def atomic_write(path, contents, exclusive=False):
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    mode = "x" if exclusive else "w"
    try:
        with target.open(mode, encoding="utf-8", newline="\n") as stream:
            stream.write(contents)
    except FileExistsError as exc:
        raise InvalidInput("output already exists; research evidence is never overwritten") from exc
    except OSError as exc:
        raise InvalidInput("cannot write output file") from exc


def command_validate(args):
    rows = read_jsonl(args.manifest)
    header, samples = validate_manifest(rows)
    check_leakage(samples)
    print(json.dumps({"status": "valid", "dataset_version": header["dataset_version"],
                      "samples": len(samples), "encounters": len({r["encounter_id"] for r in samples}),
                      "frames_by_split": dict(Counter(r["split"] for r in samples))}, sort_keys=True))


def command_freeze(args):
    rows = read_jsonl(args.manifest)
    header, samples = validate_manifest(rows)
    check_leakage(samples)
    frozen = {
        "schema_version": FORMAT_VERSION,
        "dataset_version": header["dataset_version"],
        "manifest_sha256": sha256_file(args.manifest),
        "counts": {
            split: {"frames": sum(r["split"] == split for r in samples),
                   "encounters": len({r["encounter_id"] for r in samples if r["split"] == split})}
            for split in ("train", "validation", "test")
        },
        "frozen_at_utc": datetime.now(timezone.utc).isoformat(),
        "evidence_kind": header.get("evidence_kind", "real"),
    }
    atomic_write(args.output, json.dumps(frozen, indent=2, sort_keys=True) + "\n", exclusive=True)
    print(json.dumps({"status": "frozen", "dataset_version": header["dataset_version"],
                      "manifest_sha256": frozen["manifest_sha256"]}))


def command_verify(args):
    frozen = read_json(args.split_record)
    actual = sha256_file(args.manifest)
    if frozen.get("schema_version") != FORMAT_VERSION or frozen.get("manifest_sha256") != actual:
        raise InvalidInput("frozen manifest verification failed")
    rows = read_jsonl(args.manifest)
    header, samples = validate_manifest(rows)
    check_leakage(samples)
    if header["dataset_version"] != frozen.get("dataset_version"):
        raise InvalidInput("dataset version differs from frozen record")
    print(json.dumps({"status": "verified", "dataset_version": header["dataset_version"],
                      "manifest_sha256": actual, "evidence_kind": frozen.get("evidence_kind", "real")}))


def command_report(args):
    rows = read_jsonl(args.manifest)
    header, samples = validate_manifest(rows)
    check_leakage(samples)
    frozen = read_json(args.split_record)
    if (frozen.get("schema_version") != FORMAT_VERSION or frozen.get("manifest_sha256") != sha256_file(args.manifest)
            or frozen.get("dataset_version") != header["dataset_version"]):
        raise InvalidInput("manifest differs from frozen record")
    run = validate_run(read_json(args.run_record))
    if run["outcome"] != "completed":
        raise InvalidInput("failed runs are retained as records but cannot produce prediction metrics")
    if run["dataset_version"] != frozen.get("dataset_version") or run["split_sha256"] != frozen.get("manifest_sha256") or run["evaluation_split"] != args.split:
        raise InvalidInput("run configuration does not match the frozen dataset and selected split")
    split = args.split
    selected = [row for row in samples if row["split"] == split]
    if not selected:
        raise InvalidInput("selected split has no samples")
    sample_ids = {row["sample_id"] for row in selected}
    predictions = read_jsonl(args.predictions)
    if any(row.get("sample_id") not in sample_ids for row in predictions):
        raise InvalidInput("prediction input contains samples outside the selected split")
    ocr = read_jsonl(args.ocr) if args.ocr else []
    if any(row.get("sample_id") not in sample_ids or row.get("crop_source") not in OCR_SOURCES for row in ocr):
        raise InvalidInput("OCR input contains samples outside the selected split or invalid crop source")
    encounter_ids = {row["encounter_id"] for row in selected}
    decisions = read_jsonl(args.decisions) if args.decisions else []
    if any(row.get("encounter_id") not in encounter_ids for row in decisions):
        raise InvalidInput("decision input contains encounters outside the selected split")
    if decisions and {row["encounter_id"] for row in decisions} != encounter_ids:
        raise InvalidInput("decision input must include every selected encounter")
    report = {
        "evidence_kind": header.get("evidence_kind", "real"),
        "dataset_version": header["dataset_version"],
        "split": split,
        "split_sha256": frozen["manifest_sha256"],
        "run_configuration_sha256": run["configuration_sha256"],
        "candidate": run["detector"],
        "encounters": len({row["encounter_id"] for row in selected}),
        "frames": len(selected),
        "detection": detection_metrics(selected, predictions, run["thresholds"]["score"], run["thresholds"]["iou"]),
        "route_pattern": route_metrics(selected, predictions, run["thresholds"]["score"]),
        "by_condition": condition_metrics(selected, predictions, run["thresholds"]["score"], run["thresholds"]["iou"]),
        "by_sign_size": sign_size_metrics(selected, predictions, run["thresholds"]["score"], run["thresholds"]["iou"]),
        "unresolved_directions": sum(row.get("direction") in (None, "unknown") for row in selected),
        "ocr_ground_truth_crops": ocr_metrics(ocr, "ground_truth"),
        "ocr_detector_crops": ocr_metrics(ocr, "detector"),
        "encounter_decisions": encounter_metrics(decisions) if decisions else None,
    }
    rendered = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if args.output:
        atomic_write(args.output, rendered, exclusive=True)
    else:
        print(rendered, end="")


def command_record(args):
    record = read_json(args.input)
    (validate_run if args.kind == "run" else validate_phone)(record)
    print(json.dumps({"status": "valid", "kind": args.kind, "evidence_kind": record.get("evidence_kind", "real")}))


def command_parity(args):
    reference, exported = read_jsonl(args.reference), read_jsonl(args.exported)
    compared = validate_parity(reference, exported, args.box_tolerance, args.score_tolerance)
    print(json.dumps({"status": "passed", "samples": len(reference), "detections_compared": compared,
                      "box_tolerance": args.box_tolerance, "score_tolerance": args.score_tolerance}))


def command_smoke(args):
    fixture = Path(args.fixture)
    manifest = fixture / "manifest.jsonl"
    rows = read_jsonl(manifest)
    header, samples = validate_manifest(rows)
    check_leakage(samples)
    bad_rows = [dict(row) for row in rows]
    bad_rows[-1]["derivative_of"] = bad_rows[1]["source_id"]
    try:
        validate_manifest(bad_rows)
        check_leakage(bad_rows[1:])
    except InvalidInput:
        pass
    else:
        raise InvalidInput("synthetic leakage case was not rejected")
    decisions = encounter_metrics(read_jsonl(fixture / "decisions.jsonl"))
    if decisions["wrong_green"]["count"] != 1 or decisions["wrong_green"]["denominator"] != 1:
        raise InvalidInput("mixed green decision case failed")
    no_green = encounter_metrics([{"encounter_id": "no-green", "eligible_matching": True, "matching_pattern_id": "P1", "green_pattern_ids": [], "definitive": False}])
    if no_green["wrong_green"]["fraction"] != "not estimable" or no_green["matching_recall"]["count"] != 0:
        raise InvalidInput("no-green denominator case failed")
    with tempfile.TemporaryDirectory() as directory:
        local_manifest = Path(directory) / "manifest.jsonl"
        split_path, run_path, report_path = (Path(directory) / name for name in ("split.json", "run.json", "report.json"))
        shutil.copyfile(manifest, local_manifest)
        with contextlib.redirect_stdout(io.StringIO()):
            command_freeze(argparse.Namespace(manifest=local_manifest, output=split_path))
            command_verify(argparse.Namespace(manifest=local_manifest, split_record=split_path))
        frozen = read_json(split_path)
        if frozen["manifest_sha256"] != sha256_file(manifest):
            raise InvalidInput("synthetic frozen manifest verification failed")
        run = {"schema_version": 1, "detector": "YOLOv8n", "variant": "synthetic", "outcome": "completed", "checkpoint_sha256": "synthetic",
               "dataset_version": header["dataset_version"], "split_sha256": frozen["manifest_sha256"],
               "evaluation_split": "validation", "ocr_engine": "synthetic", "route_catalog_version": "synthetic",
               "aliases_version": "synthetic", "compatibility_rules_version": "synthetic", "crop_policy": "synthetic",
               "seed": 0, "training_budget": "synthetic", "input_resolution": "synthetic", "precision": "synthetic",
               "tuning_budget": "synthetic", "preprocessing": "synthetic", "decoding": "synthetic", "postprocessing": "synthetic", "runtime": "synthetic",
               "thresholds": {"score": 0.5, "iou": 0.5}, "decision_rules": {"synthetic": "fixed"},
               "threshold_tuning_split": "validation", "decision_rules_tuning_split": "validation", "dependencies": {"synthetic": "fixture"},
               "configuration_sha256": "synthetic", "evidence_kind": "synthetic"}
        atomic_write(run_path, json.dumps(run))
        command_report(argparse.Namespace(manifest=manifest, split_record=split_path, split="validation",
                                          run_record=run_path, predictions=fixture / "predictions.jsonl", ocr=fixture / "ocr.jsonl",
                                          decisions=fixture / "decisions.jsonl", output=report_path))
        report = read_json(report_path)
        with local_manifest.open("a", encoding="utf-8") as stream:
            stream.write("\n")
        try:
            command_verify(argparse.Namespace(manifest=local_manifest, split_record=split_path))
        except InvalidInput:
            pass
        else:
            raise InvalidInput("changed frozen manifest was not rejected")
    if report.get("evidence_kind") != "synthetic" or report["encounter_decisions"]["wrong_green"]["count"] != 1:
        raise InvalidInput("synthetic report was not labeled or mixed green case missing")
    result = {"evidence_kind": "synthetic", "status": "passed", "manifest_samples": len(samples),
              "split_audit": "cross-split source rejected", "report": report,
              "no_green_case": no_green}
    print(json.dumps(result, indent=2, sort_keys=True))


def parser():
    root = argparse.ArgumentParser(description=__doc__)
    commands = root.add_subparsers(dest="command", required=True)
    for name, handler in (("validate", command_validate), ("freeze", command_freeze), ("verify", command_verify)):
        sub = commands.add_parser(name)
        sub.add_argument("--manifest", required=True)
        if name == "freeze":
            sub.add_argument("--output", required=True)
        if name == "verify":
            sub.add_argument("--split-record", required=True)
        sub.set_defaults(handler=handler)
    report = commands.add_parser("report")
    report.add_argument("--manifest", required=True)
    report.add_argument("--split-record", required=True)
    report.add_argument("--split", choices=sorted(SPLITS), required=True)
    report.add_argument("--predictions", required=True)
    report.add_argument("--ocr")
    report.add_argument("--decisions")
    report.add_argument("--run-record", required=True)
    report.add_argument("--output")
    report.set_defaults(handler=command_report)
    record = commands.add_parser("validate-record")
    record.add_argument("--kind", choices=("run", "phone"), required=True)
    record.add_argument("--input", required=True)
    record.set_defaults(handler=command_record)
    parity = commands.add_parser("parity")
    parity.add_argument("--reference", required=True)
    parity.add_argument("--exported", required=True)
    parity.add_argument("--box-tolerance", type=float, required=True)
    parity.add_argument("--score-tolerance", type=float, required=True)
    parity.set_defaults(handler=command_parity)
    smoke = commands.add_parser("smoke")
    smoke.add_argument("--fixture", required=True)
    smoke.set_defaults(handler=command_smoke)
    return root


def main(argv=None):
    args = parser().parse_args(argv)
    try:
        args.handler(args)
    except InvalidInput as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
