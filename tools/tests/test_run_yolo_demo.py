import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from run_yolo_demo import matched_truth_text, valid_score_threshold


class RunYoloDemoTests(unittest.TestCase):
    def test_detector_crop_gets_transcription_only_when_it_overlaps_a_sign(self):
        signs = [{"box": [10, 10, 30, 30], "visible_text": "CPU UNGKA"}]
        self.assertEqual("CPU UNGKA", matched_truth_text([10, 10, 30, 30], signs))
        self.assertEqual("", matched_truth_text([40, 40, 60, 60], signs))

    def test_score_threshold_must_be_finite_and_between_zero_and_one(self):
        self.assertTrue(all(valid_score_threshold(value) for value in (0, 0.25, 1)))
        self.assertFalse(any(valid_score_threshold(value) for value in (-0.1, 1.1, float("nan"))))


if __name__ == "__main__":
    unittest.main()
