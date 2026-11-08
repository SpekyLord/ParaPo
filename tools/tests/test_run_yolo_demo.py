import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from run_yolo_demo import normalized_detections, selected_splits, valid_score_threshold, match_signs, ocr_record


class RunYoloDemoTests(unittest.TestCase):
    def test_prepared_pixels_use_rgb_square_padding_and_pixel_center_sampling(self):
        try:
            import numpy as np
        except ImportError:
            self.skipTest("Detector preparation uses the existing research NumPy environment")
        from check_export_parity import prepare_input, source_box
        solid = prepare_input(np.array([[[0, 127, 255]]], dtype=np.uint8))
        self.assertEqual((1, 3, 320, 320), solid.shape)
        self.assertEqual(np.float32(1), solid[0, 0, 0, 0])
        self.assertEqual(np.float32(127)/255, solid[0, 1, 0, 0])
        self.assertEqual(0, solid[0, 2, 0, 0])
        ramp = prepare_input(np.array([[[0, 0, 0], [255, 255, 255]]], dtype=np.uint8))
        self.assertEqual(np.float32(114)/255, ramp[0, 0, 0, 0])
        self.assertEqual(np.float32(127)/255, ramp[0, 0, 100, 159])
        self.assertEqual(np.float32(128)/255, ramp[0, 0, 100, 160])
        self.assertEqual([0, 0, 2, 1], source_box([0, 80, 320, 240], 2, 1))
    def test_duplicate_detections_cannot_share_truth_and_expected_text_cannot_supply_prediction(self):
        sign = {"box": [0, 0, 30, 30], "visible_text": "03", "route_id": "ROUTE-03"}
        detections = [{"box": sign["box"], "score": .9}, {"box": sign["box"], "score": .8}]
        self.assertEqual({0: 0}, match_signs(detections, [sign]))
        row = ocr_record("s", "ground_truth", sign["box"], "", sign, 0)
        self.assertEqual([], row["printed_codes"])
        self.assertIsNone(row["canonical_route_id"])
        self.assertEqual("ILO-03", row["truth_canonical_route_id"])
    def test_detector_crop_gets_transcription_only_when_it_overlaps_a_sign(self):
        signs = [{"box": [10, 10, 30, 30], "visible_text": "CPU UNGKA"}]
        self.assertEqual({0: 0}, match_signs([{"box": [10, 10, 30, 30], "score": .8}], signs))
        self.assertEqual({}, match_signs([{"box": [40, 40, 60, 60], "score": .8}], signs))

    def test_score_threshold_must_be_finite_and_between_zero_and_one(self):
        self.assertTrue(all(valid_score_threshold(value) for value in (0, 0.25, 1)))
        self.assertFalse(any(valid_score_threshold(value) for value in (-0.1, 1.1, float("nan"))))

    def test_zero_area_model_boxes_are_skipped(self):
        class Tensor:
            def __init__(self, values):
                self.values = values

            def tolist(self):
                return self.values

        class Box:
            def __init__(self, coords, score):
                self.xyxy = [Tensor(coords)]
                self.conf = [score]

        boxes = [Box([1, 2, 8, 9], 0.8), Box([0, 9, 4, 9], 0.2)]
        self.assertEqual(1, len(normalized_detections(boxes)))

    def test_validation_only_split_does_not_select_test(self):
        self.assertEqual(("validation",), selected_splits("validation"))
        self.assertEqual(("test",), selected_splits("test"))
        self.assertEqual(("validation", "test"), selected_splits("both"))


if __name__ == "__main__":
    unittest.main()
