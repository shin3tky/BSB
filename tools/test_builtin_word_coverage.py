"""Tests for the builtin word coverage scanner."""

import unittest

import builtin_word_coverage


class BuiltinWordCoverageTest(unittest.TestCase):
    def test_classifies_group_prefixed_and_legacy_case_names(self):
        self.assertEqual("N", builtin_word_coverage.classify_bsb_prefix("TEXT-N025"))
        self.assertEqual("F", builtin_word_coverage.classify_bsb_prefix("RESULT-F-type"))
        self.assertEqual("N", builtin_word_coverage.classify_bsb_prefix("N001"))
        self.assertEqual("F", builtin_word_coverage.classify_bsb_prefix("F001"))
        self.assertIsNone(builtin_word_coverage.classify_bsb_prefix("chapter"))

    def test_static_arguments_are_separate_from_the_called_word(self):
        tokens = builtin_word_coverage.tokenize_bsb(
            """
論理接続を確認する<顧客管理API>
成功にする<整数,文字列>
"""
        )

        self.assertIn("論理接続を確認する", tokens)
        self.assertIn("成功にする", tokens)
        self.assertNotIn("論理接続を確認する<顧客管理API>", tokens)


if __name__ == "__main__":
    unittest.main()
