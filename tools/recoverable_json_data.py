#!/usr/bin/env python3
"""Read-only integrity checks for recoverable json independent conformance data."""

from __future__ import annotations

import argparse
import csv
import hashlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "tests/conformance/recoverable-json"
KINDS = (
    "emptyInput", "unexpectedToken", "trailingContent", "expectedObjectKey",
    "expectedColon", "expectedCommaOrEnd", "unterminatedString",
    "unescapedControl", "invalidEscape", "invalidUnicodeEscape",
    "isolatedSurrogate", "invalidNumber", "duplicateKey",
)
CATALOG_COLUMNS = ["case_id", "kind", "evidence", "scope"]
CASE_COLUMNS = [
    "case_id", "variant", "input_utf8_hex", "outcome", "payload_utf8_hex",
    "failure_kind", "utf8_offset", "line", "column", "json_work_delta",
    "json_construction_delta", "diagnostic", "input_preserved", "target_words",
    "evidence",
]
RESOURCE_COLUMNS = [
    "case_id", "variant", "metric", "input_shape", "limit", "observed",
    "outcome", "diagnostic", "json_work_delta", "json_construction_delta",
    "input_preserved",
]
STATE_COLUMNS = [
    "source", "exit", "data_stack", "globals", "stdout_utf8_hex", "stderr_utf8_hex",
    "capabilities", "diagnostic",
]
DIAGNOSTIC_COLUMNS = [
    "case_id", "variant", "occurrence", "code", "stage", "target_lexeme",
    "expected_type", "actual_type", "required_count", "actual_count",
    "input_preserved",
]
MANIFEST_COLUMNS = ["path", "sha256", "bytes"]


def read_rows(path: Path, columns: list[str], keys: tuple[str, ...]) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8", newline="") as stream:
        raw = list(csv.reader(stream, delimiter="\t"))
    if not raw or raw[0] != columns:
        raise ValueError(f"wrong header: {path}")
    result: list[dict[str, str]] = []
    seen: set[tuple[str, ...]] = set()
    for values in raw[1:]:
        if len(values) != len(columns):
            raise ValueError(f"wrong column count: {path}")
        row = dict(zip(columns, values))
        key = tuple(row[name] for name in keys)
        if key in seen:
            raise ValueError(f"duplicate row: {path}: {key}")
        seen.add(key)
        result.append(row)
    return result


def position(data: bytes, offset: int) -> tuple[int, int]:
    prefix = data[:offset].decode("utf-8")
    line = 1
    column = 1
    index = 0
    while index < len(prefix):
        if prefix[index] == "\r":
            index += 1
            if index < len(prefix) and prefix[index] == "\n":
                index += 1
            line += 1
            column = 1
        elif prefix[index] == "\n":
            index += 1
            line += 1
            column = 1
        else:
            index += 1
            column += 1
    return line, column


def check() -> None:
    catalog = read_rows(DATA / "catalog.tsv", CATALOG_COLUMNS, ("case_id",))
    cases = read_rows(DATA / "cases.tsv", CASE_COLUMNS, ("case_id", "variant"))
    resources = read_rows(DATA / "resources.tsv", RESOURCE_COLUMNS, ("case_id", "variant"))
    states = read_rows(DATA / "expected/states.tsv", STATE_COLUMNS, ("source",))
    diagnostics = read_rows(DATA / "expected/diagnostics.tsv", DIAGNOSTIC_COLUMNS,
                            ("case_id", "variant", "occurrence"))
    manifest = read_rows(DATA / "manifest.tsv", MANIFEST_COLUMNS, ("path",))

    expected_ids = ([f"RJSON-N{number:03d}" for number in range(1, 11)]
                    + [f"RJSON-F{number:03d}" for number in range(1, 11)]
                    + [f"RJSON-R{number:03d}" for number in range(1, 13)])
    if [row["case_id"] for row in catalog] != expected_ids:
        raise ValueError("catalog IDs are not the exact ordered N14/F14/R14 set")
    available = {path.relative_to(DATA).as_posix() for path in DATA.rglob("*") if path.is_file()}
    for row in catalog:
        if row["evidence"] not in available:
            raise ValueError("missing catalog evidence: " + row["evidence"])

    recoverable = [row for row in cases if row["outcome"] == "recoverable"
                   and row["case_id"] == "RJSON-N002"]
    if [row["failure_kind"] for row in recoverable] != list(KINDS):
        raise ValueError("recoverable kind coverage differs from the normative order")
    for row in cases:
        data = bytes.fromhex(row["input_utf8_hex"])
        data.decode("utf-8")
        if row["outcome"] == "success":
            bytes.fromhex(row["payload_utf8_hex"]).decode("utf-8")
            if row["diagnostic"] != "-" or row["input_preserved"] != "false":
                raise ValueError("invalid success expectation")
        elif row["outcome"] == "recoverable":
            if row["failure_kind"] not in KINDS or row["diagnostic"] != "-":
                raise ValueError("invalid recoverable expectation")
            offset = int(row["utf8_offset"])
            if position(data, offset) != (int(row["line"]), int(row["column"])):
                raise ValueError("independent position mismatch: " + row["variant"])
        elif row["outcome"] == "diagnostic":
            if not row["diagnostic"].startswith("E_") or row["input_preserved"] != "true":
                raise ValueError("invalid diagnostic expectation")
        else:
            raise ValueError("unknown case outcome")

    resource_ids = {row["case_id"] for row in resources}
    if resource_ids != {f"RJSON-R{number:03d}" for number in range(1, 13)}:
        raise ValueError("resource rows do not cover RJSON-R001..012")
    for row in resources:
        if row["outcome"] == "diagnostic":
            if not row["diagnostic"].startswith("E_") or row["input_preserved"] != "true":
                raise ValueError("resource diagnostics must preserve input")
        elif row["diagnostic"] != "-":
            raise ValueError("non-diagnostic resource row has a diagnostic")

    if {row["case_id"] for row in diagnostics} != {f"RJSON-F{number:03d}" for number in range(1, 11)}:
        raise ValueError("diagnostic expectations do not cover RJSON-F001..010")
    for row in diagnostics:
        if not (row["code"].startswith("E_") or row["code"].startswith("W_")):
            raise ValueError("invalid diagnostic code")
        if row["target_lexeme"] not in (DATA / next(
                item["evidence"] for item in catalog if item["case_id"] == row["case_id"]
        )).read_text(encoding="utf-8") and row["case_id"] != "RJSON-F009":
            raise ValueError("diagnostic target is absent from its evidence")

    for row in states:
        if row["source"] not in available:
            raise ValueError("missing state source: " + row["source"])
        for field in ("data_stack", "globals", "capabilities"):
            __import__("json").loads(row[field])
        bytes.fromhex(row["stdout_utf8_hex"]).decode("utf-8")
        bytes.fromhex(row["stderr_utf8_hex"]).decode("utf-8")

    expected_files = {row["path"] for row in manifest} | {"README.md", "manifest.tsv"}
    if available != expected_files:
        raise ValueError("manifest inventory mismatch")
    for row in manifest:
        content = (DATA / row["path"]).read_bytes()
        if content.startswith(b"\xef\xbb\xbf") or b"\r" in content or not content.endswith(b"\n"):
            raise ValueError("invalid text encoding or line ending: " + row["path"])
        content.decode("utf-8")
        if len(content) != int(row["bytes"]):
            raise ValueError("byte count mismatch: " + row["path"])
        if hashlib.sha256(content).hexdigest() != row["sha256"]:
            raise ValueError("hash mismatch: " + row["path"])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", required=True)
    parser.parse_args()
    check()
    print("recoverable-json data: ok")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
