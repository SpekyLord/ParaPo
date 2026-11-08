import json
import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import experiment
import prepare_detection_demo


class PrepareDetectionDemoTests(unittest.TestCase):
    def test_ocr_crops_are_lossless_local_and_do_not_replace_originals(self):
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("Local crop preparation uses the existing research environment's Pillow")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.write_fixture(root)
            Image.new("RGB", (10, 20), (10, 20, 30)).save(root / "images/frame.png")
            original = (root / "images/frame.png").read_bytes()
            header, sample = [json.loads(line) for line in manifest.read_text().splitlines()]
            rows = [header]
            for number in (18, 21, 24, 27, 28, 31, 32, 34, 38, 39):
                rows.append({**sample, "sample_id": f"m3-image-{number}"})
            manifest.write_text("".join(json.dumps(row) + "\n" for row in rows))
            output = root / "ocr"
            self.assertEqual(10, prepare_detection_demo.prepare_ocr_crops(manifest, output)["crops"])
            self.assertEqual(original, (root / "images/frame.png").read_bytes())
            with Image.open(output / "m3-image-18-0.png") as crop:
                self.assertEqual((4, 4), crop.size)
                self.assertEqual((10, 20, 30), crop.getpixel((0, 0)))
            with self.assertRaises(experiment.InvalidInput):
                prepare_detection_demo.prepare_ocr_crops(manifest, output)
    def write_fixture(self, root, box=(1, 1, 5, 5)):
        image_dir = root / "images"
        image_dir.mkdir()
        image = image_dir / "frame.png"
        image.write_bytes(b"\x89PNG\r\n\x1a\n" + struct.pack(">I", 13) + b"IHDR" + struct.pack(">II", 10, 20))
        rows = [
            {"record_type": "manifest", "schema_version": 1, "dataset_version": "test-v1"},
            {"record_type": "sample", "sample_id": "frame-1", "image_ref": "images/frame.png",
             "encounter_id": "enc-1", "session_id": "session-1", "source_id": "source-1",
             "ground_truth_ref": "label-1", "permission_ref": "local-test", "split": "train",
             "signs": [{"box": list(box), "visible_text": "sign"}]},
        ]
        manifest = root / "manifest.jsonl"
        manifest.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")
        return manifest

    def test_exports_bounded_boxes_into_yolo_format(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.write_fixture(root)
            output = root / "yolo"
            result = prepare_detection_demo.prepare(manifest, output)
            self.assertEqual({"train": 1, "validation": 0, "test": 0}, result["splits"])
            label = (output / "labels" / "train" / "sample-0001.txt").read_text(encoding="utf-8")
            self.assertEqual("0 0.30000000 0.15000000 0.40000000 0.20000000\n", label)
            with self.assertRaises(experiment.InvalidInput):
                prepare_detection_demo.prepare(manifest, output)

    def test_rejects_boxes_outside_image(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.write_fixture(root, box=(1, 1, 11, 5))
            with self.assertRaises(experiment.InvalidInput):
                prepare_detection_demo.prepare(manifest, root / "yolo")
            self.assertFalse((root / "yolo").exists())

    def test_prepares_jpeg_without_renaming_its_extension(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            image_dir = root / "images"
            image_dir.mkdir()
            # Minimal JPEG header with a baseline frame declaring 10x20 pixels.
            frame = b"\xff\xc0\x00\x0b\x08" + struct.pack(">HH", 20, 10) + b"\x01\x01\x11\x00"
            (image_dir / "frame.jpg").write_bytes(b"\xff\xd8\xff\xe0\x00\x04AB" + frame + b"\xff\xd9")
            rows = [
                {"record_type": "manifest", "schema_version": 1, "dataset_version": "jpeg-v1"},
                {"record_type": "sample", "sample_id": "frame-1", "image_ref": "images/frame.jpg",
                 "encounter_id": "enc-1", "session_id": "session-1", "source_id": "source-1",
                 "ground_truth_ref": "label-1", "permission_ref": "local-test", "split": "train",
                 "signs": [{"box": [1, 1, 5, 5], "visible_text": "sign"}]},
            ]
            manifest = root / "manifest.jsonl"
            manifest.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")
            output = root / "yolo"
            prepare_detection_demo.prepare(manifest, output)
            self.assertTrue((output / "images" / "train" / "sample-0001.jpg").is_file())


if __name__ == "__main__":
    unittest.main()
