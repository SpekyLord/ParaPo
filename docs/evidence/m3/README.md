# M3 research tooling and collection protocol

This directory documents the tooling. No model outputs, model weights, or phone-recognition measurements have been produced for M3. Sixteen chat-supplied images are staged locally under ignored `datasets/m3-samples/`; 14 vehicle scenes have first-pass boxes/text in `manifest.jsonl`, and two poster graphics are excluded. The manifest is frozen at 10 train / 2 validation / 2 test, but two held-out images are far too few for a reliable comparison. The checked-in fixture is synthetic and must not be used to claim candidate performance.

## Reproduce the tooling check

From the repository root, with Python 3.12 or later:

```powershell
python -m unittest discover -s tools/tests
python tools/experiment.py smoke --fixture tools/fixtures/m3-synthetic/
python tools/experiment.py validate --manifest tools/fixtures/m3-synthetic/manifest.jsonl
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest
```

The Android command is a manual-planner regression check, not an M3 detector measurement. On 2026-11-06 it passed the 11 JVM tests and 10 connected instrumentation tests on TECNO KJ8 / Android 14. The first Gradle attempt could not locate the SDK; setting these process environment variables for the rerun resolved it without writing `local.properties`.

To create and verify a local frozen record (the fixture is synthetic):

```powershell
python tools/experiment.py freeze --manifest tools/fixtures/m3-synthetic/manifest.jsonl --output $env:TEMP/parapo-synthetic-split.json
python tools/experiment.py verify --manifest tools/fixtures/m3-synthetic/manifest.jsonl --split-record $env:TEMP/parapo-synthetic-split.json
```

Do not use the synthetic split record for a real experiment. `freeze` refuses to replace an existing split record. `verify` fails if even one manifest byte changes. Keep the demo's raw images, private manifests, credentials, and weights local and out of Git.

## Data and experiment inputs

The manifest is UTF-8 JSONL. Its first line is a header with `record_type: "manifest"`, `schema_version: 1`, `dataset_version`, and optional `evidence_kind`. Each following sample line uses `record_type: "sample"` and includes:

- `sample_id`, `encounter_id`, `session_id`, optional `vehicle_id`, `source_id`, optional `derivative_of`, safe `ground_truth_ref` and `permission_ref` values, and `split` (`train`, `validation`, or `test`).
- `augmentation` (true only for training); `conditions`; `route_id`, `pattern_id`, and `direction` (null or `unknown` when unclear); and `signs` (empty for genuine no-sign scenes).
- Each sign contains an `[x1,y1,x2,y2]` box, optional `size_category`, and `visible_text` separate from route/direction labels. A second look at labels is helpful but not required by the tool; disagreements recorded in `annotation_review` need a `resolution_ref`. An optional `vehicle_split_exception` must explain why vehicle grouping could not be kept intact.

Encounter, session, and source lineage may not cross splits. Vehicle grouping is enforced when IDs are supplied; an explicit reason is required for an exception. No augmented validation/test examples are allowed. Freeze records include the raw manifest SHA-256 and independent encounter and frame counts per split.

Normalized prediction JSONL rows have `sample_id` and `detections`; each detection has `box`, confidence `score`, `pattern_id` or `UNKNOWN`, and `direction` or `unknown`. OCR JSONL rows have `sample_id`, `crop_source` (`ground_truth` or `detector`), `truth_text`, `predicted_text`, `truth_route_code`, and `predicted_route_code`. Encounter-decision JSONL rows have one `encounter_id`, `eligible_matching`, `matching_pattern_id`, all `green_pattern_ids`, and an explicit `definitive` boolean (so a definitive red is coverage rather than abstention); optional timing fields are `latency_ms`, `useful_window_ms`, and `correct_before_window_closed`.

Each run record must pin the detector/variant, outcome, checkpoint and configuration hashes, dataset and split hashes, evaluation split, OCR engine, route catalog, aliases and compatibility-rule versions, crop policy, seed, training/tuning budgets, input resolution, precision, preprocessing, decoding, postprocessing, dependency versions, runtime, score/IoU thresholds and decision rules. `threshold_tuning_split` and `decision_rules_tuning_split` must both be `validation`. Failed runs retain a reason and evidence reference; they cannot produce prediction metrics. Phone records must pin the artifact hash, runtime, acceleration, device and Android build, model size, peak memory, FPS, median/p95 latency, measurement boundary, and sample hash. Export parity is checked on identical normalized samples with explicit box and score tolerances.

Validate records with `python tools/experiment.py validate-record --kind run --input <run.json>` or `--kind phone`. Compare export outputs with `python tools/experiment.py parity --reference <runtime.jsonl> --exported <phone.jsonl> --box-tolerance <pixels> --score-tolerance <fraction>`.

## Simple class-project use

Anyone can add images, label them, or run checks; no lead roles or formal approval forms are needed unless your course specifically requires them. For the images already supplied, a short source note and keeping them local is enough for this prototype. If you add more, mark unclear route/direction as unknown, avoid spending time on faces or plates, and keep images out of Git. Do not plan a public release.

The image inventory is a first pass. Confirm route/sign boxes where useful; do not pretend unclear signs are labeled. For a small demo, put related or possibly duplicated photos in the same training group and use different new images for a basic held-out check. More images from unrelated web pictures do not automatically make a reliable comparison.

## Evaluation gates and honest reporting

`report --split test` requires a frozen manifest, a completed run record, and thresholds/decision rules tuned on validation only. That keeps test results out of tuning without adding a sign-off form. If you change the model or rules after seeing test results, treat that test set as used and get a fresh held-out set for a new comparison.

Example validation report command:

```powershell
python tools/experiment.py report --manifest <manifest.jsonl> --split-record <frozen-split.json> --split validation --run-record <pinned-run.json> --predictions <validation-predictions.jsonl> --ocr <validation-ocr.jsonl> --decisions <validation-decisions.jsonl> --output <report.json>
```

The report emits detection precision/recall at the pinned score/IoU, AP50 and AP50–95, pattern/direction confusion with unknowns, OCR character error/exact code accuracy by crop source, condition/sign-size breakdowns, wrong-green and matching-recall counts with Wilson 95% intervals, abstention, coverage, late decisions, and latency summaries when supplied. It reports zero denominators as `not estimable`. Do not pool repeated frames as independent encounters. Missing crop or timing inputs are not measured; record them as unavailable rather than treating them as zero.

There is no training/export adapter in this repository, and this tool does not install model runtimes or select a candidate. Resolve the M1 feasibility paths and exact SSD MobileNetV2 variant before configuring actual runs. Do not substitute another detector without the approved versioned experiment-plan change.

## Current state (2026-11-06)

All M3 acceptance items remain open. Manifest, freeze/verify, metric, parity, and synthetic smoke tooling are implementation evidence only. A 14-image first-pass manifest now validates, freezes, and verifies locally; its labels need a quick correction pass, and its two-image validation/test splits are only a starter. The original image source details, exact SSD variant, feasible training/export path, pinned OCR/runtime, and actual candidate phone evidence are still missing. No candidate was run or selected. Anyone can handle the next steps; no formal role or sign-off is needed unless the course requires it.
