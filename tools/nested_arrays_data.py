#!/usr/bin/env python3
"""Read-only integrity checks for nested arrays conformance data."""

from __future__ import annotations

import argparse
import csv
import hashlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "tests/conformance/nested-arrays"
HEADERS = {
    "catalog.tsv": ["case_id", "kind", "evidence", "scope"],
    "expected/states.tsv": [
        "case_id", "variant", "value_type", "display", "stdout",
        "construction_units", "operation_units", "original_preserved",
    ],
    "expected/diagnostics.tsv": [
        "case_id", "variant", "code", "stage", "line", "column", "fields",
    ],
    "expected/trace.tsv": [
        "case_id", "variant", "value_type", "expected_preview",
        "forbidden_markers", "budget_delta",
    ],
    "resources.tsv": [
        "case_id", "variant", "metric", "recipe", "limit", "used", "requested",
        "observed", "outcome", "diagnostic", "construction_after",
        "operation_after", "state_preserved",
    ],
    "manifest.tsv": ["path", "sha256", "bytes"],
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
        row = dict(zip(raw[0], values))
        key = (values[0], values[1] if len(values) > 1 else "")
        if key in seen:
            raise ValueError(f"duplicate row: {relative}: {key}")
        seen.add(key)
        rows.append(row)
    return rows


def check() -> None:
    catalog = read_rows("catalog.tsv")
    expected = ([f"NARRAY-N{number:03d}" for number in range(1, 13)]
                + [f"NARRAY-F{number:03d}" for number in range(1, 13)]
                + [f"NARRAY-R{number:03d}" for number in range(1, 11)])
    if [row["case_id"] for row in catalog] != expected:
        raise ValueError("catalog IDs are not the exact ordered N18/F18/R18 set")
    for row in catalog:
        if row["kind"] not in {"normal", "failure", "resource"}:
            raise ValueError("invalid catalog kind")
        if row["scope"] not in {"public", "internal"}:
            raise ValueError("invalid catalog scope")
        if not (DATA / row["evidence"]).is_file():
            raise ValueError("missing evidence: " + row["evidence"])

    states = read_rows("expected/states.tsv")
    if not all(row["original_preserved"] == "true" for row in states):
        raise ValueError("state expectation is not immutable")
    diagnostics = read_rows("expected/diagnostics.tsv")
    if len(diagnostics) != 12:
        raise ValueError("diagnostic rows changed")
    traces = read_rows("expected/trace.tsv")
    if not all(row["budget_delta"] == "0" for row in traces):
        raise ValueError("trace must not consume array budget")
    resources = read_rows("resources.tsv")
    if not all(row["state_preserved"] == "true" for row in resources):
        raise ValueError("resource atomicity changed")
    catalog_ids = set(expected)
    for rows in (states, diagnostics, traces, resources):
        if any(row["case_id"] not in catalog_ids for row in rows):
            raise ValueError("table row is outside the catalog")

    manifest = read_rows("manifest.tsv")
    expected_paths = {
        path.relative_to(DATA).as_posix()
        for path in DATA.rglob("*")
        if path.is_file() and path.name not in {"README.md", "manifest.tsv"}
    }
    actual_paths = {row["path"] for row in manifest}
    if actual_paths != expected_paths:
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
