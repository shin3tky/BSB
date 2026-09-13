"""Tests for the read-only byte sequences data auditor."""

import unittest

import byte_sequences_data


class ByteSequenceDataTest(unittest.TestCase):
    def test_checked_repository_data(self):
        byte_sequences_data.check()

    def test_utf8_failure_oracle_uses_normative_priority(self):
        self.assertEqual(("invalidLeadingByte", 0), byte_sequences_data.strict_utf8_failure(b"\x80\xff"))
        self.assertEqual(
            ("invalidContinuationByte", 2), byte_sequences_data.strict_utf8_failure(b"A\xe2(B")
        )
        self.assertEqual(("truncatedSequence", 0), byte_sequences_data.strict_utf8_failure(b"\xe2\x82"))
        self.assertEqual(("overlongEncoding", 0), byte_sequences_data.strict_utf8_failure(b"\xe0\x80\x80"))

    def test_base64_failure_oracle_checks_padding_and_pad_bits(self):
        self.assertEqual(("invalidCharacter", 0), byte_sequences_data.strict_base64_failure("-AAA"))
        self.assertEqual(("invalidLength", 2), byte_sequences_data.strict_base64_failure("Zg"))
        self.assertEqual(("invalidPadding", 3), byte_sequences_data.strict_base64_failure("AA=A"))
        self.assertEqual(("nonZeroPadBits", 1), byte_sequences_data.strict_base64_failure("Zh=="))
        self.assertIsNone(byte_sequences_data.strict_base64_failure("Zg=="))

    def test_failure_kind_sets_are_closed_and_ordered(self):
        self.assertEqual("invalidLeadingByte", byte_sequences_data.UTF8_KINDS[0])
        self.assertEqual("codePointOutOfRange", byte_sequences_data.UTF8_KINDS[-1])
        self.assertEqual(
            ["invalidCharacter", "invalidLength", "invalidPadding", "nonZeroPadBits"],
            byte_sequences_data.BASE64_KINDS,
        )


if __name__ == "__main__":
    unittest.main()
