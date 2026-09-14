#!/usr/bin/env python3
"""Validate independent JSHAPE IDs, boundaries, paths, order, and work counts."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from dataclasses import dataclass
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "tests" / "conformance" / "json-shapes"
KINDS = {"null", "boolean", "integer", "decimal", "string", "array", "object"}


def rows(name: str) -> list[dict[str, str]]:
    with (DATA / name).open(encoding="utf-8", newline="") as source:
        return list(csv.DictReader(source, delimiter="\t"))


def kind(value: object) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, int):
        return "integer"
    if isinstance(value, Decimal):
        return "decimal"
    if isinstance(value, str):
        return "string"
    if isinstance(value, list):
        return "array"
    if isinstance(value, dict):
        return "object"
    raise TypeError(type(value))


def pointer(path: str, token: str) -> str:
    return path + "/" + token.replace("~", "~0").replace("/", "~1")


@dataclass(frozen=True)
class OracleResult:
    failures: list[dict[str, str]]
    work: int


def validate(instance: object, shape: object) -> OracleResult:
    failures: list[dict[str, str]] = []
    work = 0
    pending: list[tuple[object, object, str, bool, bool]] = [
        (instance, shape, "", False, False)
    ]
    while pending and len(failures) < 256:
        value, expected, path, missing, required = pending.pop()
        work += 1
        nullable = isinstance(expected, dict) and "nullable" in expected
        base = expected["nullable"] if nullable else expected
        expected_kind = (
            base if isinstance(base, str)
            else base["kind"] if "kind" in base
            else "array" if "array" in base else "object"
        )
        if missing:
            if not required:
                continue
            failures.append({"kind": "missingRequiredKey", "path": path,
                             "expectedKind": expected_kind, "actualKind": "missing"})
            continue
        actual_kind = kind(value)
        if actual_kind == "null":
            if nullable or expected_kind == "null":
                continue
            failures.append({"kind": "nullNotAllowed", "path": path,
                             "expectedKind": expected_kind, "actualKind": "null"})
            continue
        if actual_kind != expected_kind:
            failures.append({"kind": "kindMismatch", "path": path,
                             "expectedKind": expected_kind, "actualKind": actual_kind})
            continue
        if expected_kind == "array":
            child = base["array"]
            for index in range(len(value) - 1, -1, -1):
                pending.append((value[index], child, path + f"/{index}", False, False))
        elif expected_kind == "object":
            for member in reversed(base["object"]):
                name = member["name"]
                if name in value:
                    pending.append(
                        (value[name], member["shape"], pointer(path, name), False, False)
                    )
                else:
                    pending.append(
                        (None, member["shape"], pointer(path, name), True,
                         member["required"])
                    )
    return OracleResult(failures, work)


def check() -> None:
    manifest = rows("manifest.tsv")
    expected_paths = [
        "README.md",
        "catalog.tsv",
        "diagnostics.tsv",
        "messages.properties",
        "resources.tsv",
        "vectors.tsv",
        "chapter/json-shapes-chapter.bsb",
        "chapter/json-shapes-chapter.stdout",
    ]
    if [row["path"] for row in manifest] != expected_paths:
        raise ValueError("JSHAPE manifest paths are incomplete or out of order")
    for row in manifest:
        payload = (DATA / row["path"]).read_bytes()
        if hashlib.sha256(payload).hexdigest() != row["sha256"]:
            raise ValueError(f"manifest hash mismatch: {row['path']}")
        if len(payload) != int(row["bytes"]):
            raise ValueError(f"manifest size mismatch: {row['path']}")

    expected_ids = (
        [f"JSHAPE-N{i:03d}" for i in range(1, 17)]
        + [f"JSHAPE-F{i:03d}" for i in range(1, 13)]
        + [f"JSHAPE-R{i:03d}" for i in range(1, 13)]
    )
    actual_ids = [row["id"] for row in rows("catalog.tsv")]
    if actual_ids != expected_ids or len(set(actual_ids)) != 40:
        raise ValueError("JSHAPE catalog must contain the ordered 40 IDs exactly once")

    for row in rows("vectors.tsv"):
        instance = json.loads(row["input_json"], parse_float=Decimal)
        shape = json.loads(row["shape_json"])
        expected = json.loads(row["expected_failures"])
        actual = validate(instance, shape)
        if actual.failures != expected or actual.work != int(row["work"]):
            raise ValueError(f"vector mismatch: {row['case']}")

    resources = rows("resources.tsv")
    expected_resources = {
        "shapeDepth": (256, 257, "diagnostic"),
        "shapeNodes": (65536, 65537, "diagnostic"),
        "validationWork": (67108864, 67108865, "diagnostic"),
        "pathUtf8Bytes": (65536, 65537, "diagnostic"),
        "failureCount": (256, 257, "cap"),
    }
    actual_resources = {
        row["resource"]: (int(row["exact"]), int(row["over"]), row["behavior"])
        for row in resources
    }
    if actual_resources != expected_resources:
        raise ValueError("JSHAPE resource boundaries changed")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if not args.check:
        parser.error("--check is required")
    check()
    print("JSHAPE conformance data: manifest, 40 IDs, vectors, resources OK")


if __name__ == "__main__":
    main()
