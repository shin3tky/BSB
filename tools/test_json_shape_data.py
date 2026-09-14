import unittest

import json_shape_data


class JsonShapeDataTest(unittest.TestCase):
    def test_checked_repository_data(self) -> None:
        json_shape_data.check()

    def test_optional_missing_member_is_visited_without_failure(self) -> None:
        shape = {
            "object": [
                {"name": "next", "required": False, "shape": {"nullable": "string"}}
            ]
        }
        result = json_shape_data.validate({}, shape)
        self.assertEqual([], result.failures)
        self.assertEqual(2, result.work)


if __name__ == "__main__":
    unittest.main()
