"""Tests for the read-only nested arrays data auditor."""

import unittest

import nested_arrays_data


class NestedArrayDataTest(unittest.TestCase):
    def test_checked_repository_data(self):
        nested_arrays_data.check()

    def test_fixed_identifier_sets(self):
        rows = nested_arrays_data.read_rows("catalog.tsv")
        self.assertEqual("NARRAY-N001", rows[0]["case_id"])
        self.assertEqual("NARRAY-R010", rows[-1]["case_id"])
        self.assertEqual(34, len(rows))


if __name__ == "__main__":
    unittest.main()
