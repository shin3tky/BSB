#!/usr/bin/env python3
"""Validate independent HTTP-REL IDs and timing expectations."""

from __future__ import annotations

import argparse
import csv
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "tests" / "conformance" / "http-reliability"


def rows(name: str) -> list[dict[str, str]]:
    with (DATA / name).open(encoding="utf-8", newline="") as source:
        return list(csv.DictReader(source, delimiter="\t"))


def backoff(initial: int, maximum: int, multiplier: int, attempt: int) -> int:
    value = initial
    for _ in range(2, attempt):
        value = min(maximum, value * multiplier)
    return value


def check() -> None:
    manifest = rows("manifest.tsv")
    manifest_paths = [row["path"] for row in manifest]
    expected_paths = ["README.md", "catalog.tsv", "diagnostics.tsv", "timing.tsv"]
    if manifest_paths != expected_paths:
        raise ValueError("HTTP-REL manifest paths are incomplete or out of order")
    for row in manifest:
        payload = (DATA / row["path"]).read_bytes()
        if hashlib.sha256(payload).hexdigest() != row["sha256"]:
            raise ValueError(f"manifest hash mismatch: {row['path']}")
        if len(payload) != int(row["bytes"]):
            raise ValueError(f"manifest size mismatch: {row['path']}")
    catalog = rows("catalog.tsv")
    expected = (
        [f"HTTPREL-N{i:03d}" for i in range(1, 13)]
        + [f"HTTPREL-F{i:03d}" for i in range(1, 12)]
        + [f"HTTPREL-R{i:03d}" for i in range(1, 9)]
    )
    actual = [row["id"] for row in catalog]
    if actual != expected or len(set(actual)) != 31:
        raise ValueError("HTTP-REL catalog must contain the ordered 31 IDs exactly once")
    for row in rows("timing.tsv"):
        retry = backoff(
            int(row["initial_ms"]), int(row["maximum_ms"]),
            int(row["multiplier"]), int(row["attempt"])
        )
        observed = max(retry, int(row["retry_after_ms"]), int(row["interval_remaining_ms"]))
        if observed != int(row["expected_wait_ms"]):
            raise ValueError(f"timing mismatch: {row['case']}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if not args.check:
        parser.error("--check is required")
    check()
    print("HTTP-REL conformance data: manifest, 31 IDs, timing oracle OK")


if __name__ == "__main__":
    main()
