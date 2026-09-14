import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("http_reliability_data.py")
SPEC = importlib.util.spec_from_file_location("http_reliability_data", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class HttpReliabilityDataTest(unittest.TestCase):
    def test_catalog_and_timing_oracle(self):
        MODULE.check()

    def test_backoff_is_bounded(self):
        self.assertEqual(500, MODULE.backoff(100, 500, 2, 8))


if __name__ == "__main__":
    unittest.main()
