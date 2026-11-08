import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import experiment
import ocr_probe


class OcrProbeTests(unittest.TestCase):
    def test_expected_labels_never_supply_predictions(self):
        row = ocr_probe.scored_row({"variant_id": "original", "expected_text": "03", "truth_route_id": "ROUTE-03", "raw_text": ""})
        self.assertEqual("unreadable", row["route_status"])
        self.assertIsNone(row["canonical_route_id"])
        self.assertEqual("ILO-03", row["truth_canonical_route_id"])

    def test_preparation_preserves_sources_and_reverses_identical_cases(self):
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("Use the existing research Pillow environment")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source.png"
            Image.new("RGB", (24, 16), (10, 20, 30)).save(source)
            original = source.read_bytes()
            rows = [{"sample_id": f"s-{n}", "sign_index": 0, "crop_file": source.name,
                     "crop_sha256": hashlib.sha256(original).hexdigest(), "expected_text": "03"} for n in range(10)]
            (root / "manifest.json").write_text(json.dumps(rows))
            output = root / "probe"
            self.assertEqual(40, ocr_probe.prepare(root, output)["cases"])
            self.assertEqual(original, source.read_bytes())
            forward = json.loads((output / "manifest-forward.json").read_text())
            reverse = json.loads((output / "manifest-reverse.json").read_text())
            self.assertEqual(forward, list(reversed(reverse)))
            self.assertEqual(40, len({r["crop_file"] for r in forward}))
            with zipfile.ZipFile(output / "fixtures-forward.zip") as archive:
                self.assertEqual(forward, json.loads(archive.read("manifest.json")))
            for row in forward:
                if row["variant_id"] == "original":
                    with Image.open(output / row["crop_file"]) as image:
                        self.assertEqual((24, 16), image.size)
                        self.assertEqual((10, 20, 30), image.getpixel((0, 0)))
            with self.assertRaises(experiment.InvalidInput):
                ocr_probe.prepare(root, output)

    def test_reports_unknown_truth_wrong_supported_route_and_repeated_samples_separately(self):
        with tempfile.TemporaryDirectory() as directory:
            rows = []
            for n in range(10):
                for variant in ocr_probe.VARIANTS:
                    truth = None if n == 9 else "ROUTE-11" if n == 8 else "ROUTE-03"
                    text = "" if truth is None else "11" if truth == "ROUTE-11" else "03"
                    raw = "03" if variant == "padding" and n == 8 else "10" if n == 9 else text
                    rows.append({"sample_id": str(n), "sign_index": 0, "variant_id": variant, "crop_sha256": "a" * 64,
                                 "raw_text": raw, "expected_text": text, "truth_route_id": truth,
                                 "ocr_duration_ms": 100, "error": None, "ocr_version": "ML Kit test", "android_fingerprint": "test"})
            paths = [Path(directory) / f"run-{i}.jsonl" for i in range(2)]
            for path, ordered in zip(paths, [rows, list(reversed(rows))]):
                path.write_text("".join(json.dumps(row) + "\n" for row in ordered))
            report = ocr_probe.report(paths)
            self.assertEqual(10, report["unique_photos"])
            self.assertEqual(2, report["passes"])
            self.assertEqual(18, report["variants"]["original"]["route_code_samples"])
            self.assertEqual(2, report["variants"]["original"]["unknown_text_samples"])
            self.assertEqual(["8", "8"], report["variants"]["padding"]["wrong_supported_predictions"])
            self.assertEqual(["9", "9"], report["variants"]["original"]["supported_predictions_on_unknown_truth"])
            paths[1].write_text(json.dumps(rows[0]) + "\n")
            with self.assertRaises(experiment.InvalidInput):
                ocr_probe.report(paths)

    def test_confirmation_covers_thirty_signs_and_rejects_mixed_reader_policies(self):
        with tempfile.TemporaryDirectory() as directory:
            rows = [{"sample_id": str(n), "sign_index": 0, "variant_id": variant, "crop_sha256": "a" * 64,
                     "raw_text": "03", "expected_text": "03", "truth_route_id": "ROUTE-03",
                     "ocr_duration_ms": 100, "error": None, "ocr_version": "ML Kit test", "android_fingerprint": "test"}
                    for n in range(30) for variant in ("original", "padding")]
            path = Path(directory) / "confirmation.jsonl"
            path.write_text("".join(json.dumps(row) + "\n" for row in rows))
            self.assertEqual(30, ocr_probe.report([path])["unique_crops"])
            rows[0]["reader_input_policy"] = "different"
            path.write_text("".join(json.dumps(row) + "\n" for row in rows))
            with self.assertRaises(experiment.InvalidInput):
                ocr_probe.report([path])
