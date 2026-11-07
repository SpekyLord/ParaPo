# M3 research tooling and collection protocol

This directory documents the tooling and local demo runs. Twenty user-supplied images are staged under ignored `datasets/m3-samples/`; 18 vehicle scenes are labeled, and two poster graphics are excluded. The original v4 manifest freezes 10 train / 2 validation / 2 test. A v5 manifest adds four photos to training and freezes 14 train / 2 validation / 2 test. YOLOv8n was retrained and evaluated on v5, but it still missed all three test signs; its ONNX export also failed host parity. The phone smoke test ran the existing provisional model on a synthetic image only, not the v5 export. SSD MobileNetV2 and EfficientDet-Lite0 still have no comparable runs. The test split has only two photos and has been previewed, so the measurements are exploratory. See [M3 demo results](results-2026-11-07.md). The checked-in synthetic fixture remains separate and cannot be used to claim candidate performance.

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

The v4 image inventory and boxes were visually checked; boxes in images 03, 13, 15, and 16 were corrected. Route IDs not printed on a sign stay unknown, and unreadable text remains blank. More independent photos would be needed for a useful comparison.

## Evaluation gates and honest reporting

`report --split test` requires a frozen manifest, a completed run record, and thresholds/decision rules set from validation. The two-photo test result here is exploratory because its predictions were previewed during the label review. Get a fresh held-out set before using results to choose a model.

Example validation report command:

```powershell
python tools/experiment.py report --manifest <manifest.jsonl> --split-record <frozen-split.json> --split validation --run-record <pinned-run.json> --predictions <validation-predictions.jsonl> --ocr <validation-ocr.jsonl> --decisions <validation-decisions.jsonl> --output <report.json>
```

The report emits detection precision/recall at the pinned score/IoU, AP50 and AP50–95, pattern/direction confusion with unknowns, OCR character error/exact code accuracy by crop source, condition/sign-size breakdowns, wrong-green and matching-recall counts with Wilson 95% intervals, abstention, coverage, late decisions, and latency summaries when supplied. It reports zero denominators as `not estimable`. Do not pool repeated frames as independent encounters. Missing crop or timing inputs are not measured; record them as unavailable rather than treating them as zero.

`tools/run_yolo_demo.py` runs an existing YOLO checkpoint and EasyOCR on the frozen validation/test split; it does not train a model. Ultralytics, PyTorch, OpenCV, and EasyOCR are optional packages installed in a local virtual environment. There is no SSD MobileNetV2 or EfficientDet-Lite0 training/inference adapter yet, and no candidate has been selected.

## Reproduce the real YOLO demo

From PowerShell at the repository root, with the optional model packages installed in the local environment:

```powershell
$mlPy = Join-Path $env:LOCALAPPDATA 'ParaPo\m3-yolo-venv\Scripts\python.exe'
& $mlPy tools/run_yolo_demo.py --manifest datasets/m3-samples/manifest.jsonl --split-record datasets/m3-samples/split-v4.json --checkpoint datasets/m3-samples/runs/yolov8n-reviewed-20ep/weights/best.pt --output-dir datasets/m3-samples/runs/demo-replay-v4 --device 0
python tools/experiment.py validate-record --kind run --input datasets/m3-samples/runs/demo-v4/run-record.json
```

The run helper refuses to overwrite outputs. Choose a new output directory for each replay. Training and model weights are optional local artifacts and remain out of Git. The exact training arguments and dependency versions are recorded in `datasets/m3-samples/runs/demo-v4/run-config.json` and `run-record.json`.

## Reproduce v5 retraining

The preparation tool accepts PNG and JPEG samples. From PowerShell at the repository root, rebuild the frozen v5 training folders and repeat YOLO training with the pinned settings:

```powershell
python tools/prepare_detection_demo.py --manifest datasets/m3-samples/manifest-v5.jsonl --output datasets/m3-samples/yolo-v5-replay
$mlPy = Join-Path $env:LOCALAPPDATA 'ParaPo\m3-yolo-venv\Scripts\python.exe'
@'
from pathlib import Path
from ultralytics import YOLO
root = Path.cwd()
YOLO('yolov8n.pt').train(data=str(root / 'datasets/m3-samples/yolo-v5-replay/data.yaml'), epochs=20, patience=5, batch=2, imgsz=320, device=0, workers=0, seed=42, deterministic=True, pretrained=True, optimizer='auto', project=str(root / 'datasets/m3-samples/runs'), name='yolov8n-v5-replay', exist_ok=False, cls_remap=True, amp=True, verbose=False, plots=False)
'@ | & $mlPy -
```

Only the 14 `train` examples are used for gradient updates; the two validation examples control early stopping. The two test examples are not read during training. Use a fresh output folder/name for each rerun. The original local v5 records remain in `datasets/m3-samples/runs/demo-v5/` and `yolov8n-v5-20ep/`.

## Current state (2026-11-07)

M3-01–04 are complete for the current local set. The v5 YOLO run used its 14 training images, selected a threshold from validation, and reported the two-photo test set separately. M3-05–09 remain partial: the two other detectors have no comparable runs, v5 missed all three test signs, and its ONNX export failed parity. The Tecno and emulator each passed the one-test bundled-model smoke check, but only on a synthetic image with the old provisional asset; no v5 phone measurement was made. M3-10 remains undecided; there is no winner. No Android source or scanner UI changed. Anyone can take the remaining tasks; no role assignments or formal sign-offs are needed for this class demo.
