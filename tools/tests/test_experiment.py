import json
import contextlib
import io
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import experiment


FIXTURE = Path(__file__).resolve().parents[1] / "fixtures" / "m3-synthetic"


class ExperimentTests(unittest.TestCase):
    def setUp(self):
        self.rows = experiment.read_jsonl(FIXTURE / "manifest.jsonl")

    def test_fixture_manifest_and_leakage_audit(self):
        _, samples = experiment.validate_manifest(self.rows)
        experiment.check_leakage(samples)
        self.assertEqual(4, len(samples))

    def test_rejects_cross_split_encounter(self):
        rows = json.loads(json.dumps(self.rows))
        rows[-1]["encounter_id"] = rows[1]["encounter_id"]
        with self.assertRaises(experiment.InvalidInput):
            experiment.validate_manifest(rows)

    def test_owner_links_need_visible_context_and_unique_ids(self):
        rows = json.loads(json.dumps(self.rows))
        rows[1]["signs"][0]["owner_id"] = "owner-1"
        with self.assertRaises(experiment.InvalidInput):
            experiment.validate_manifest(rows)
        rows[1]["vehicles"] = [{"owner_id": "owner-1", "box": [0, 0, 20, 20]}]
        experiment.validate_manifest(rows)
        rows[1]["vehicles"].append(rows[1]["vehicles"][0])
        with self.assertRaises(experiment.InvalidInput):
            experiment.validate_manifest(rows)

    def test_rejects_cross_split_source_derivative(self):
        rows = json.loads(json.dumps(self.rows))
        rows[-1]["derivative_of"] = rows[1]["source_id"]
        with self.assertRaises(experiment.InvalidInput):
            experiment.validate_manifest(rows)

    def test_real_manifest_does_not_require_formal_annotation_review(self):
        rows = json.loads(json.dumps(self.rows))
        rows[0].pop("evidence_kind")
        for row in rows[1:]:
            row.pop("annotation_review", None)
        _, samples = experiment.validate_manifest(rows)
        self.assertEqual(4, len(samples))

    def test_rejects_eval_augmentation_and_unchanged_frozen_input(self):
        rows = json.loads(json.dumps(self.rows))
        rows[2]["augmentation"] = True
        with self.assertRaises(experiment.InvalidInput):
            experiment.validate_manifest(rows)
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "manifest.jsonl"
            manifest.write_text("\n".join(json.dumps(row) for row in self.rows) + "\n", encoding="utf-8")
            frozen = experiment.sha256_file(manifest)
            manifest.write_text(manifest.read_text(encoding="utf-8") + "\n", encoding="utf-8")
            self.assertNotEqual(frozen, experiment.sha256_file(manifest))

    def test_encounter_green_denominators(self):
        decisions = experiment.read_jsonl(FIXTURE / "decisions.jsonl")
        metrics = experiment.encounter_metrics(decisions)
        self.assertEqual({"count": 1, "denominator": 1, "fraction": 1.0, "wilson_95": [0.206549, 1]}, metrics["wrong_green"])
        self.assertEqual({"count": 0, "denominator": 2, "fraction": 0.0, "wilson_95": [0, 0.65762]}, metrics["matching_recall"])
        none = experiment.encounter_metrics([{"encounter_id": "n", "eligible_matching": False, "green_pattern_ids": [], "definitive": False}])
        self.assertEqual("not estimable", none["wrong_green"]["fraction"])

    def test_ocr_route_accuracy_skips_unlabeled_codes(self):
        rows = [
            {"crop_source": "ground_truth", "truth_text": "JARO", "predicted_text": "JARO",
             "truth_route_code": None, "predicted_route_code": None},
            {"crop_source": "ground_truth", "truth_text": "ROUTE 3", "predicted_text": "ROUTE 3",
             "truth_route_code": "ILO-03", "predicted_route_code": None},
        ]
        metrics = experiment.ocr_metrics(rows, "ground_truth")
        self.assertEqual(0, metrics["route_code_samples"])
        self.assertEqual(2, metrics["extraction_not_run"])
        self.assertIsNone(metrics["exact_route_code_accuracy"])
        self.assertIsNone(experiment.ocr_metrics(rows[:1], "ground_truth")["exact_route_code_accuracy"])

    def test_route_accuracy_does_not_count_unknown_labels_as_correct(self):
        samples = [{"sample_id": "s1", "direction": "unknown", "signs": [
            {"box": [0, 0, 10, 10], "pattern_id": None, "direction": "unknown"}]}]
        predictions = [{"sample_id": "s1", "detections": [
            {"box": [0, 0, 10, 10], "score": 0.9, "pattern_id": None, "direction": None}]}]
        metrics = experiment.route_metrics(samples, predictions)
        self.assertEqual(1, metrics["signs"])
        self.assertEqual(0, metrics["pattern_evaluable_signs"])
        self.assertIsNone(metrics["exact_pattern_accuracy"])
        self.assertEqual(0, metrics["direction_evaluable_signs"])
        self.assertIsNone(metrics["exact_direction_accuracy"])

    def test_synthetic_smoke_report_is_explicitly_labeled(self):
        with contextlib.redirect_stdout(io.StringIO()) as output:
            result = experiment.main(["smoke", "--fixture", str(FIXTURE)])
        self.assertEqual(0, result)
        report = json.loads(output.getvalue())
        self.assertEqual("synthetic", report["evidence_kind"])
        self.assertEqual("synthetic", report["report"]["evidence_kind"])
        self.assertEqual("not estimable", report["no_green_case"]["wrong_green"]["fraction"])

    def test_held_out_report_uses_frozen_split_and_validation_tuning_without_signoff(self):
        with tempfile.TemporaryDirectory() as directory:
            split_path = Path(directory) / "split.json"
            split_path.write_text(json.dumps({"schema_version": 1, "dataset_version": "synthetic-m3-v1",
                                              "manifest_sha256": experiment.sha256_file(FIXTURE / "manifest.jsonl")}),
                                  encoding="utf-8")
            run_path = Path(directory) / "run.json"
            run_path.write_text(json.dumps({"schema_version": 1, "detector": "YOLOv8n", "variant": "synthetic", "outcome": "completed",
                                            "checkpoint_sha256": "synthetic", "dataset_version": "synthetic-m3-v1",
                                            "split_sha256": experiment.sha256_file(FIXTURE / "manifest.jsonl"),
                                            "evaluation_split": "test", "ocr_engine": "synthetic", "route_catalog_version": "synthetic",
                                            "aliases_version": "synthetic", "compatibility_rules_version": "synthetic", "crop_policy": "synthetic",
                                            "seed": 0, "training_budget": "synthetic", "input_resolution": "synthetic", "precision": "synthetic",
                                            "tuning_budget": "synthetic",
                                            "preprocessing": "synthetic", "decoding": "synthetic", "postprocessing": "synthetic", "runtime": "synthetic",
                                            "thresholds": {"score": 0.5, "iou": 0.5}, "dependencies": {"synthetic": "fixture"},
                                            "decision_rules": {"synthetic": "fixed"}, "threshold_tuning_split": "validation",
                                            "decision_rules_tuning_split": "validation",
                                            "configuration_sha256": "synthetic", "evidence_kind": "synthetic"}), encoding="utf-8")
            run = json.loads(run_path.read_text(encoding="utf-8"))
            predictions_path = Path(directory) / "predictions.jsonl"
            predictions_path.write_text(json.dumps({"sample_id": "syn-test-1", "detections": [
                {"box": [5, 5, 25, 25], "score": 0.8, "pattern_id": "P2", "direction": "unknown"}]}) + "\n", encoding="utf-8")
            report_path = Path(directory) / "report.json"
            args = ["report", "--manifest", str(FIXTURE / "manifest.jsonl"), "--split-record", str(split_path),
                    "--split", "test", "--predictions", str(predictions_path), "--run-record", str(run_path),
                    "--output", str(report_path)]
            self.assertEqual(0, experiment.main(args))
            self.assertEqual("synthetic", experiment.read_json(report_path)["evidence_kind"])
            run["threshold_tuning_split"] = "test"
            run_path.write_text(json.dumps(run), encoding="utf-8")
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(2, experiment.main(args))

    def test_export_parity_uses_identical_samples_and_tolerance(self):
        reference = [{"sample_id": "s1", "detections": [{"box": [0, 0, 10, 10], "score": 0.8,
                                                            "pattern_id": "P1", "direction": "outbound"}]}]
        exported = [{"sample_id": "s1", "detections": [{"box": [0.1, 0, 10, 10], "score": 0.79,
                                                            "pattern_id": "P1", "direction": "outbound"}]}]
        self.assertEqual(1, experiment.validate_parity(reference, exported, 0.2, 0.02))
        with self.assertRaises(experiment.InvalidInput):
            experiment.validate_parity(reference, exported, 0.05, 0.02)

    def test_parity_matches_geometry_when_scores_reorder_but_rejects_missing_boxes(self):
        a = {"box": [0, 0, 10, 10], "score": .80}
        b = {"box": [30, 30, 40, 40], "score": .81}
        reference = [{"sample_id": "s", "detections": [a, b]}]
        reordered = [{"sample_id": "s", "detections": [{**b, "score": .80}, {**a, "score": .81}]}]
        self.assertEqual(2, experiment.validate_parity(reference, reordered, 1.5, .02))
        with self.assertRaises(experiment.InvalidInput):
            experiment.validate_parity(reference, [{"sample_id": "s", "detections": [a]}], 1.5, .02)

    def test_unknown_ocr_truth_and_false_crops_do_not_pollute_cer(self):
        rows = [{"crop_source": "detector", "truth_text": "03", "truth_text_known": True,
                 "predicted_text": "3", "extraction_status": "completed", "truth_printed_codes": ["03"],
                 "printed_codes": ["3"], "truth_canonical_route_id": "ILO-03", "canonical_route_id": "ILO-03"},
                {"crop_source": "detector", "truth_text": "", "truth_text_known": False,
                 "predicted_text": "nonsense", "truth_match_status": "unmatched", "extraction_status": "completed"}]
        metrics = experiment.ocr_metrics(rows, "detector")
        self.assertEqual(.5, metrics["character_error_rate"])
        self.assertEqual(0, metrics["exact_route_code_accuracy"])
        self.assertEqual(1, metrics["normalized_code_accuracy"])
        self.assertEqual(1, metrics["canonical_route_accuracy"])
        self.assertEqual(1, metrics["unknown_text_samples"])
        self.assertEqual(1, metrics["unmatched_crops_with_text"])

    def test_same_route_wrong_owner_and_unknown_owner_are_not_correct_matches(self):
        def row(identity, ownership):
            return {"encounter_id": identity, "eligible_matching": True, "matching_pattern_id": "P1",
                    "definitive": True, "direction_status": "resolved", "evidence_type": "recorded_replay",
                    "readable_at_ms": 100, "useful_window_end_ms": 1000,
                    "green_events": [{"pattern_id": "P1", "owner_correctness": ownership, "stable": True, "at_ms": 400}]}
        metrics = experiment.encounter_metrics([row("a", "incorrect"), row("b", "unknown"), row("c", "correct")])
        self.assertEqual(1, metrics["wrong_green"]["count"])
        self.assertEqual(3, metrics["wrong_green"]["denominator"])
        self.assertFalse(metrics["wrong_green_fully_verified"])
        self.assertEqual(1, metrics["matching_recall"]["count"])
        self.assertEqual(300, metrics["first_stable_correct_ms_median"])
        self.assertEqual(1, metrics["correct_before_window_closed"]["count"])

    def test_green_on_ineligible_encounter_is_wrong_even_with_same_pattern_label(self):
        row = {"encounter_id": "wrong-journey", "eligible_matching": False, "matching_pattern_id": "P1",
               "definitive": True, "direction_status": "resolved",
               "green_events": [{"pattern_id": "P1", "owner_correctness": "correct"}]}
        self.assertEqual(1, experiment.encounter_metrics([row])["wrong_green"]["count"])

    def test_shared_route_cases(self):
        from route_text import interpret
        for case in json.loads((FIXTURE.parent / "route-text-cases.json").read_text()):
            result = interpret(case["text"])
            self.assertEqual(case["status"], result["route_status"], case["text"])
            self.assertEqual(case.get("route_id"), result["canonical_route_id"], case["text"])

    def test_existing_evidence_is_not_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "evidence.json"
            output.write_text("original", encoding="utf-8")
            with self.assertRaises(experiment.InvalidInput):
                experiment.atomic_write(output, "replacement", exclusive=True)
            self.assertEqual("original", output.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
