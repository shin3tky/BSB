"""Tests for the read-only recoverable json data auditor."""

import unittest

import recoverable_json_data


class RecoverableJsonDataTest(unittest.TestCase):
    def test_checked_repository_data(self):
        recoverable_json_data.check()

    def test_normative_failure_kinds_are_closed_and_ordered(self):
        self.assertEqual(13, len(recoverable_json_data.KINDS))
        self.assertEqual("emptyInput", recoverable_json_data.KINDS[0])
        self.assertEqual("duplicateKey", recoverable_json_data.KINDS[-1])

    def test_position_oracle_counts_utf8_scalars_and_crlf(self):
        self.assertEqual((1, 6), recoverable_json_data.position('["😀",x]'.encode(), 8))
        self.assertEqual((2, 1), recoverable_json_data.position(b"[\r\nx]", 3))


if __name__ == "__main__":
    unittest.main()
