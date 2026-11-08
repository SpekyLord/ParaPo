import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from run_yolo_demo import matched_truth_text, normalized_detections, selected_splits, valid_score_threshold


class RunYoloDemoTests(unittest.TestCase):
    def test_detector_crop_gets_transcription_only_when_it_overlaps_a_sign(self):
        signs = [{"box": [10, 10, 30, 30], "visible_text": "CPU UNGKA"}]
        self.assertEqual("CPU UNGKA", matched_truth_text([10, 10, 30, 30], signs))
        self.assertEqual("", matched_truth_text([40, 40, 60, 60], signs))

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
