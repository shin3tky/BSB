#!/usr/bin/env python3
"""Read-only integrity checks for workspace tables file and delimited-table conformance data."""

from __future__ import annotations

import argparse
import csv
import hashlib
from pathlib import Path
from typing import Optional
import unicodedata


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "tests/conformance/workspace-tables"
HEADERS = {
    "catalog.tsv": ["case_id", "kind", "evidence", "scope", "part"],
    "vectors/logical-names.tsv": [
        "case_id", "variant", "utf8_hex", "utf8_bytes", "valid", "reason", "match_key",
    ],
    "expected/operations.tsv": [
        "case_id", "variant", "operation", "workspace", "resolver_state", "file_state",
        "failure_kind", "result", "resolver_calls", "file_calls", "retry_calls",
        "operation_after", "read_after", "write_after", "target_state",
    ],
    "expected/diagnostics.tsv": [
        "case_id", "variant", "code", "stage", "fields", "stack_preserved",
    ],
    "expected/cli.tsv": [
        "case_id", "variant", "mode", "entries", "base", "outcome", "exit",
        "stdout_bytes", "category",
    ],
    "expected/explain.tsv": [
        "case_id", "variant", "workspace_declarations", "summary_requirements",
        "word_requirements", "forbidden",
    ],
    "expected/trace.tsv": [
        "case_id", "variant", "type", "expected_trace", "forbidden_markers", "budget_delta",
    ],
    "resources.tsv": [
        "case_id", "variant", "metric", "recipe", "limit", "used", "requested", "observed",
        "outcome", "diagnostic", "operation_after", "read_after", "write_after",
        "state_preserved",
    ],
    "vectors/delimited-parse.tsv": [
        "case_id", "variant", "format", "input_hex", "outcome", "table",
        "failure_kind", "byte_position", "line", "column",
    ],
    "expected/delimited-write.tsv": [
        "case_id", "variant", "format", "table", "output_hex", "roundtrip",
    ],
    "expected/delimited-diagnostics.tsv": [
        "case_id", "variant", "word", "code", "fields", "stack_preserved",
        "forbidden_markers",
    ],
    "expected/delimited-trace.tsv": [
        "case_id", "variant", "type", "expected_trace", "forbidden_markers", "budget_delta",
    ],
    "delimited-resources.tsv": [
        "case_id", "variant", "metric", "recipe", "limit", "used", "requested",
        "observed", "outcome", "diagnostic", "array_construction_after",
        "delimited_work_after", "state_preserved",
    ],
    "manifest.tsv": ["path", "sha256", "bytes"],
}

NEW_DIAGNOSTICS = {
    "E_WORKSPACE_DECLARATION_VALUE",
    "E_WORKSPACE_DECLARATION_SCOPE",
    "E_WORKSPACE_DECLARATION_LIMIT",
    "E_EXPECTED_WORKSPACE_ARGUMENT_START",
    "E_EXPECTED_WORKSPACE_ARGUMENT",
    "E_WORKSPACE_ARGUMENT_COUNT",
    "E_EXPECTED_WORKSPACE_ARGUMENT_END",
    "E_UNDECLARED_WORKSPACE",
    "E_WORKSPACE_REFERENCE_NOT_ALLOWED",
    "E_LOGICAL_FILE_NAME_INVALID",
    "E_WORKSPACE_NOT_CONFIGURED",
    "E_WORKSPACE_ACCESS_DENIED",
    "E_WORKSPACE_CONFIGURATION_INVALID",
    "E_WORKSPACE_FILE_NOT_MAPPED",
    "E_WORKSPACE_FILE_ACCESS_DENIED",
    "E_FILE_OPERATION_LIMIT",
    "E_FILE_READ_TOTAL_LIMIT",
    "E_FILE_WRITE_TOTAL_LIMIT",
    "E_FILE_CANCELLED",
}
READ_FAILURES = {"notFound", "notRegularFile", "tooLarge", "ioFailure"}
WRITE_FAILURES = {
    "parentNotFound", "targetNotRegularFile", "tooLarge",
    "atomicReplacementUnavailable", "ioFailure",
}
LOGICAL_REASONS = {"notNfc", "empty", "tooLong", "separator", "controlCharacter", "dotName"}
DELIMITED_DIAGNOSTICS = {
    "E_DELIMITED_TEXT_EMPTY_ROW",
    "E_DELIMITED_TEXT_COLUMN_COUNT_MISMATCH",
    "E_DELIMITED_TEXT_CELL_INVALID",
    "E_DELIMITED_TEXT_OUTPUT_LIMIT",
    "E_DELIMITED_TEXT_WORK_LIMIT",
}
DELIMITED_FAILURES = {
    "unexpectedQuote",
    "unexpectedCharacterAfterQuote",
    "unterminatedQuotedField",
    "nulCharacter",
    "columnCountMismatch",
}


def read_rows(relative: str) -> list[dict[str, str]]:
    path = DATA / relative
    raw_bytes = path.read_bytes()
    if raw_bytes.startswith(b"\xef\xbb\xbf") or not raw_bytes.endswith(b"\n") or b"\r" in raw_bytes:
        raise ValueError(f"invalid text envelope: {relative}")
    with path.open("r", encoding="utf-8", newline="") as stream:
        raw = list(csv.reader(stream, delimiter="\t", quoting=csv.QUOTE_NONE))
    if not raw or raw[0] != HEADERS[relative]:
        raise ValueError(f"wrong header: {relative}")
    rows: list[dict[str, str]] = []
    seen: set[tuple[str, str]] = set()
    for values in raw[1:]:
        if len(values) != len(raw[0]):
            raise ValueError(f"wrong column count: {relative}")
        key = (values[0], values[1] if len(values) > 1 else "")
        if key in seen:
            raise ValueError(f"duplicate row: {relative}: {key}")
        seen.add(key)
        rows.append(dict(zip(raw[0], values)))
    return rows


def expanded_bytes(encoded: str) -> bytes:
    if encoded == "61x255":
        return b"a" * 255
    if encoded == "61x256":
        return b"a" * 256
    return bytes.fromhex(encoded)


def fixed_bytes(encoded: str) -> bytes:
    return b"" if encoded == "-" else bytes.fromhex(encoded)


def decode_table(encoded: str) -> list[list[str]]:
    if encoded == "[]":
        return []
    result: list[list[str]] = []
    for encoded_row in encoded.split(";"):
        row = []
        for encoded_cell in encoded_row.split(","):
            payload = b"" if encoded_cell == "_" else bytes.fromhex(encoded_cell)
            row.append(payload.decode("utf-8"))
        result.append(row)
    return result


def encode_table(table: list[list[str]]) -> str:
    if not table:
        return "[]"
    return ";".join(
        ",".join("_" if cell == "" else cell.encode("utf-8").hex() for cell in row)
        for row in table
    )


def parse_delimited(payload: bytes, format_name: str) -> tuple[str, object]:
    """Independent scalar/byte-position oracle; it shares no production Java code."""
    text = payload.decode("utf-8")
    delimiter = {"csv": ",", "tsv": "\t"}[format_name]
    index = 0
    byte_position = 0
    line = 1
    column = 1

    def position() -> tuple[int, int, int]:
        return byte_position, line, column

    def advance_scalar() -> str:
        nonlocal index, byte_position, line, column
        value = text[index]
        index += 1
        byte_position += len(value.encode("utf-8"))
        column += 1
        return value

    def advance_newline() -> str:
        nonlocal index, byte_position, line, column
        if text[index] == "\r" and index + 1 < len(text) and text[index + 1] == "\n":
            value = "\r\n"
            index += 2
            byte_position += 2
        else:
            value = text[index]
            index += 1
            byte_position += 1
        line += 1
        column = 1
        return value

    def failure(kind: str, at: Optional[tuple[int, int, int]] = None) -> tuple[str, object]:
        return "failure", (kind, *(at or position()))

    if text.startswith("\ufeff"):
        advance_scalar()
    if index == len(text):
        return "success", []

    rows: list[list[str]] = []
    expected_columns: Optional[int] = None
    first_mismatch: Optional[tuple[int, int, int]] = None
    while index < len(text):
        row: list[str] = []
        field_starts: list[tuple[int, int, int]] = []
        ended_with_break = False
        terminator_position: Optional[tuple[int, int, int]] = None
        while True:
            field_starts.append(position())
            cell: list[str] = []
            if index < len(text) and text[index] == '"':
                advance_scalar()
                while True:
                    if index == len(text):
                        return failure("unterminatedQuotedField")
                    if text[index] == "\x00":
                        return failure("nulCharacter")
                    if text[index] == '"':
                        if index + 1 < len(text) and text[index + 1] == '"':
                            advance_scalar()
                            advance_scalar()
                            cell.append('"')
                            continue
                        advance_scalar()
                        break
                    if text[index] in "\r\n":
                        cell.append(advance_newline())
                    else:
                        cell.append(advance_scalar())
                if index < len(text) and text[index] not in {delimiter, "\r", "\n"}:
                    return failure("unexpectedCharacterAfterQuote")
            else:
                while index < len(text) and text[index] not in {delimiter, "\r", "\n"}:
                    if text[index] == "\x00":
                        return failure("nulCharacter")
                    if text[index] == '"':
                        return failure("unexpectedQuote")
                    cell.append(advance_scalar())
            row.append("".join(cell))
            if index < len(text) and text[index] == delimiter:
                advance_scalar()
                continue
            if index < len(text) and text[index] in "\r\n":
                terminator_position = position()
                advance_newline()
                ended_with_break = True
            break

        if expected_columns is None:
            expected_columns = len(row)
        elif len(row) != expected_columns and first_mismatch is None:
            first_mismatch = (
                field_starts[expected_columns]
                if len(row) > expected_columns
                else (terminator_position or position())
            )
        rows.append(row)
        if not ended_with_break or index == len(text):
            break

    if first_mismatch is not None:
        return failure("columnCountMismatch", first_mismatch)
    return "success", rows


def write_delimited(table: list[list[str]], format_name: str) -> bytes:
    delimiter = {"csv": ",", "tsv": "\t"}[format_name]
    if not table:
        return b""
    records: list[str] = []
    for row_index, row in enumerate(table):
        cells: list[str] = []
        for column_index, cell in enumerate(row):
            if "\x00" in cell:
                raise ValueError("NUL is not serializable")
            quoted = any(character in cell for character in (delimiter, '"', "\r", "\n"))
            quoted = quoted or (row_index == 0 and column_index == 0 and cell.startswith("\ufeff"))
            cells.append('"' + cell.replace('"', '""') + '"' if quoted else cell)
        records.append(delimiter.join(cells))
    return ("\r\n".join(records) + "\r\n").encode("utf-8")


def check() -> None:
    catalog = read_rows("catalog.tsv")
    expected = ([f"WST-N{number:03d}" for number in range(1, 23)]
                + [f"WST-F{number:03d}" for number in range(1, 31)]
                + [f"WST-R{number:03d}" for number in range(1, 19)])
    if [row["case_id"] for row in catalog] != expected:
        raise ValueError("catalog IDs are not the exact ordered WST-N/WST-F/WST-R set")
    for row in catalog:
        if row["kind"] not in {"normal", "failure", "resource"}:
            raise ValueError("invalid catalog kind")
        category_and_number = row["case_id"].split("-")[1]
        expected_part = "files" if int(category_and_number[1:]) <= {
            "N": 12, "F": 18, "R": 10,
        }[category_and_number[0]] else "delimited"
        if row["scope"] not in {"public", "internal"} or row["part"] != expected_part:
            raise ValueError("invalid catalog scope or part")
        if not (DATA / row["evidence"]).is_file():
            raise ValueError("missing evidence: " + row["evidence"])

    catalog_ids = set(expected)
    logical_names = read_rows("vectors/logical-names.tsv")
    for row in logical_names:
        payload = expanded_bytes(row["utf8_hex"])
        if len(payload) != int(row["utf8_bytes"]):
            raise ValueError("logical-name byte mismatch: " + row["variant"])
        text = payload.decode("utf-8")
        valid = row["valid"] == "true"
        reason = row["reason"]
        if valid and (reason != "-" or unicodedata.normalize("NFC", text) != text):
            raise ValueError("invalid valid logical-name vector")
        if not valid and reason not in LOGICAL_REASONS:
            raise ValueError("unknown logical-name reason")

    operations = read_rows("expected/operations.tsv")
    if any(row["retry_calls"] != "0" for row in operations):
        raise ValueError("file operations must never retry")
    read_failures = {
        row["failure_kind"] for row in operations
        if row["case_id"] == "WST-N005"
    }
    write_failures = {
        row["failure_kind"] for row in operations
        if row["case_id"] == "WST-N008"
    }
    if read_failures != READ_FAILURES or write_failures != WRITE_FAILURES:
        raise ValueError("closed file failure set changed")

    diagnostics = read_rows("expected/diagnostics.tsv")
    if {row["code"] for row in diagnostics} != NEW_DIAGNOSTICS:
        raise ValueError("front-half diagnostic set changed")
    if not all(row["stack_preserved"] == "true" for row in diagnostics):
        raise ValueError("diagnostic stack atomicity changed")

    cli = read_rows("expected/cli.tsv")
    for row in cli:
        if row["outcome"] == "configuration-error":
            if row["exit"] != "78" or row["stdout_bytes"] != "0":
                raise ValueError("CLI configuration failure boundary changed")

    traces = read_rows("expected/trace.tsv")
    if not all(row["budget_delta"] == "0" and row["forbidden_markers"] for row in traces):
        raise ValueError("trace redaction or budget expectation changed")

    resources = read_rows("resources.tsv")
    if not all(row["state_preserved"] == "true" for row in resources):
        raise ValueError("resource atomicity changed")
    for rows in (
        logical_names, operations, diagnostics, cli, read_rows("expected/explain.tsv"), traces,
        resources,
    ):
        if any(row["case_id"] not in catalog_ids for row in rows):
            raise ValueError("table row is outside the catalog")

    parse_rows = read_rows("vectors/delimited-parse.tsv")
    for row in parse_rows:
        actual_outcome, actual = parse_delimited(fixed_bytes(row["input_hex"]), row["format"])
        if actual_outcome != row["outcome"]:
            raise ValueError("delimited outcome mismatch: " + row["variant"])
        if actual_outcome == "success":
            if encode_table(actual) != row["table"] or row["failure_kind"] != "-":
                raise ValueError("delimited table mismatch: " + row["variant"])
        else:
            kind, byte_offset, physical_line, scalar_column = actual
            expected_failure = (
                row["failure_kind"], int(row["byte_position"]),
                int(row["line"]), int(row["column"]),
            )
            if (kind, byte_offset, physical_line, scalar_column) != expected_failure:
                raise ValueError("delimited failure mismatch: " + row["variant"])
    if {row["failure_kind"] for row in parse_rows if row["outcome"] == "failure"} != DELIMITED_FAILURES:
        raise ValueError("closed delimited failure set changed")

    write_rows = read_rows("expected/delimited-write.tsv")
    for row in write_rows:
        table = decode_table(row["table"])
        output = write_delimited(table, row["format"])
        if output != fixed_bytes(row["output_hex"]):
            raise ValueError("delimited output mismatch: " + row["variant"])
        if row["roundtrip"] != "true" or parse_delimited(output, row["format"]) != ("success", table):
            raise ValueError("delimited roundtrip mismatch: " + row["variant"])

    delimited_diagnostics = read_rows("expected/delimited-diagnostics.tsv")
    if {row["code"] for row in delimited_diagnostics} != DELIMITED_DIAGNOSTICS:
        raise ValueError("delimited diagnostic set changed")
    if not all(row["stack_preserved"] == "true" and row["forbidden_markers"]
               for row in delimited_diagnostics):
        raise ValueError("delimited diagnostic atomicity or redaction changed")

    delimited_traces = read_rows("expected/delimited-trace.tsv")
    if not all(row["budget_delta"] == "0" and row["forbidden_markers"]
               for row in delimited_traces):
        raise ValueError("delimited trace expectation changed")

    delimited_resources = read_rows("delimited-resources.tsv")
    if not all(row["state_preserved"] == "true" for row in delimited_resources):
        raise ValueError("delimited resource atomicity changed")
    for rows in (parse_rows, write_rows, delimited_diagnostics, delimited_traces,
                 delimited_resources):
        if any(row["case_id"] not in catalog_ids for row in rows):
            raise ValueError("delimited table row is outside the catalog")

    required_boundaries = {
        ("fileOperations", "4096", "4097"),
        ("fileReadBytes", "134217728", "134217729"),
        ("fileWriteAttemptBytes", "134217728", "134217729"),
        ("fileReadPolicy", "67108864", "67108865"),
        ("dataStackItems", "65536", "65537"),
    }
    actual_boundaries = {(r["metric"], r["limit"], r["observed"]) for r in resources}
    if not required_boundaries <= actual_boundaries:
        raise ValueError("required resource boundary missing")
    required_delimited_boundaries = {
        ("stringUtf8Bytes", "16777216", "16777217"),
        ("delimitedTextWorkUnits", "134217728", "134217729"),
        ("arrayDirectElements", "65536", "65537"),
        ("arrayNestedElements", "1000000", "1000001"),
        ("arrayConstructionUnits", "1000000", "1000001"),
        ("delimitedTextOutputUtf8Bytes", "16777216", "16777217"),
    }
    actual_delimited_boundaries = {
        (row["metric"], row["limit"], row["observed"]) for row in delimited_resources
    }
    if not required_delimited_boundaries <= actual_delimited_boundaries:
        raise ValueError("required delimited resource boundary missing")

    manifest = read_rows("manifest.tsv")
    expected_paths = {
        path.relative_to(DATA).as_posix()
        for path in DATA.rglob("*")
        if path.is_file() and path.name not in {"README.md", "manifest.tsv"}
    }
    if {row["path"] for row in manifest} != expected_paths:
        raise ValueError("manifest inventory mismatch")
    for row in manifest:
        payload = (DATA / row["path"]).read_bytes()
        if len(payload) != int(row["bytes"]):
            raise ValueError("manifest byte mismatch: " + row["path"])
        if hashlib.sha256(payload).hexdigest() != row["sha256"]:
            raise ValueError("manifest hash mismatch: " + row["path"])


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", required=True)
    parser.parse_args()
    check()


if __name__ == "__main__":
    main()
