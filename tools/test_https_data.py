"""Tests for the read-only https data auditor."""

import unittest

import https_data


class HttpsDataTest(unittest.TestCase):
    def test_checked_repository_data(self):
        https_data.check()

    def test_percent_encoder_uses_utf8_and_never_form_encoding(self):
        self.assertEqual("%E6%9D%B1%E4%BA%AC%20%E9%A7%85", https_data.percent_encode("東京 駅"))
        self.assertEqual("a/%25/b", https_data.percent_encode("a/%/b", preserve_slash=True))
        self.assertEqual(
            "https://example.test/root/search?q=a%20b&q=%25",
            https_data.expected_uri(
                "https://example.test/root/", "search", [["q", "a b"], ["q", "%"]]
            ),
        )

    def test_path_and_header_oracles_use_normative_priority(self):
        self.assertEqual("absolutePath", https_data.path_failure("/x?y#z"))
        self.assertEqual("queryDelimiter", https_data.path_failure("x?y#z"))
        self.assertEqual("dotSegment", https_data.path_failure("x/../y"))
        self.assertEqual("emptyName", https_data.header_failure("", "bad\r\n"))
        self.assertEqual("reserved", https_data.header_failure("Authorization", "ok"))
        self.assertEqual("invalidValueCharacter", https_data.header_failure("X-Test", "bad\r\n"))

    def test_closed_method_and_failure_sets_remain_ordered(self):
        self.assertEqual(["GET", "HEAD", "POST", "PUT", "PATCH", "DELETE"], https_data.METHODS)
        self.assertEqual("nameResolutionFailure", https_data.FAILURE_KINDS[0])
        self.assertEqual("transportFailure", https_data.FAILURE_KINDS[-1])
        self.assertEqual(10, len(https_data.FAILURE_KINDS))


if __name__ == "__main__":
    unittest.main()
