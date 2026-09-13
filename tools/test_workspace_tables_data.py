"""Tests for the read-only workspace tables data auditor."""

import unittest

import workspace_tables_data


class WorkspaceTableDataTest(unittest.TestCase):
    def test_checked_repository_data(self):
        workspace_tables_data.check()

    def test_fixed_identifier_sets(self):
        rows = workspace_tables_data.read_rows("catalog.tsv")
        self.assertEqual("WST-N001", rows[0]["case_id"])
        self.assertEqual("WST-R018", rows[-1]["case_id"])
        self.assertEqual(70, len(rows))

    def test_logical_name_oracle_is_independent(self):
        rows = workspace_tables_data.read_rows("vectors/logical-names.tsv")
        self.assertEqual(255, len(workspace_tables_data.expanded_bytes(rows[-4]["utf8_hex"])))
        self.assertEqual(workspace_tables_data.LOGICAL_REASONS,
                         {row["reason"] for row in rows if row["valid"] == "false"})

    def test_delimited_oracle_is_independent_and_closed(self):
        rows = workspace_tables_data.read_rows("vectors/delimited-parse.tsv")
        self.assertEqual(workspace_tables_data.DELIMITED_FAILURES,
                         {row["failure_kind"] for row in rows if row["outcome"] == "failure"})
        outcome, value = workspace_tables_data.parse_delimited("\ufeff😀\"x".encode(), "csv")
        self.assertEqual("failure", outcome)
        self.assertEqual(("unexpectedQuote", 7, 1, 3), value)

    def test_delimited_writer_quotes_leading_bom_for_roundtrip(self):
        table = [["\ufeffx", "y"]]
        output = workspace_tables_data.write_delimited(table, "csv")
        self.assertTrue(output.startswith(b'"'))
        self.assertEqual(("success", table), workspace_tables_data.parse_delimited(output, "csv"))


if __name__ == "__main__":
    unittest.main()
