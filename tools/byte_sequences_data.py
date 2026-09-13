#!/usr/bin/env python3
"""Read-only integrity and independent-vector checks for byte sequences data."""

from __future__ import annotations

import argparse
import base64
import csv
import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "tests/conformance/byte-sequences"
UTF8_KINDS = [
    "invalidLeadingByte",
    "invalidContinuationByte",
    "truncatedSequence",
    "overlongEncoding",
    "surrogateCodePoint",
    "codePointOutOfRange",
]
BASE64_KINDS = ["invalidCharacter", "invalidLength", "invalidPadding", "nonZeroPadBits"]
NEW_CODES = {
    "E_BYTE_SEQUENCE_RANGE_OUT_OF_BOUNDS",
    "E_BYTE_SEQUENCE_SIZE_LIMIT",
    "E_BYTE_SEQUENCE_CONSTRUCTION_LIMIT",
    "E_BYTE_SEQUENCE_WORK_LIMIT",
}
HEADERS = {
    "catalog.tsv": ["case_id", "kind", "evidence", "scope"],
    "vectors/utf8.tsv": ["case_id", "vector", "code_points", "utf8_hex", "text_json"],
    "vectors/utf8-failures.tsv": ["case_id", "variant", "input_hex", "kind", "offset"],
    "vectors/base64.tsv": ["case_id", "vector", "input_hex", "encoded"],
    "vectors/base64-failures.tsv": [
        "case_id", "variant", "input_utf8_hex", "kind", "position",
    ],
    "operations/slices.tsv": [
        "case_id", "variant", "input_hex", "start", "end", "outcome", "output_hex",
        "diagnostic", "construction", "work",
    ],
    "operations/equality.tsv": [
        "case_id", "variant", "left_hex", "right_hex", "wrapper", "result",
        "compared_bytes", "construction",
    ],
    "resources.tsv": [
        "case_id", "variant", "metric", "recipe", "limit", "used", "requested",
        "observed", "outcome", "diagnostic", "construction_after", "work_after",
        "state_preserved",
    ],
    "expected/diagnostics.tsv": [
        "case_id", "variant", "code", "stage", "target_lexeme", "fields_json",
        "expected", "actual",
    ],
    "expected/states.tsv": [
        "case_id", "variant", "operation", "input_fixture", "outcome", "result_type",
        "result_state", "kind", "position", "stack_replaced", "stdout_utf8_hex",
        "stderr_utf8_hex", "construction", "work",
    ],
    "expected/trace.tsv": ["case_id", "variant", "type", "expected_trace", "forbidden_markers"],
    "manifest.tsv": ["path", "sha256", "bytes"],
}


def read_rows(relative: str, keys: tuple[str, ...]) -> list[dict[str, str]]:
    path = DATA / relative
    with path.open("r", encoding="utf-8", newline="") as stream:
        raw = list(csv.reader(stream, delimiter="\t", quoting=csv.QUOTE_NONE))
    if not raw or raw[0] != HEADERS[relative]:
        raise ValueError(f"wrong header: {relative}")
    rows: list[dict[str, str]] = []
    seen: set[tuple[str, ...]] = set()
    for values in raw[1:]:
        if len(values) != len(raw[0]):
            raise ValueError(f"wrong column count: {relative}")
        row = dict(zip(raw[0], values))
        key = tuple(row[name] for name in keys)
        if key in seen:
            raise ValueError(f"duplicate row: {relative}: {key}")
        seen.add(key)
        rows.append(row)
    return rows


def strict_utf8_failure(data: bytes) -> tuple[str, int] | None:
    index = 0
    while index < len(data):
        first = data[index]
        if first <= 0x7f:
            index += 1
            continue
        if 0xc2 <= first <= 0xdf:
            length, minimum, code_point = 2, 0x80, first & 0x1f
        elif 0xe0 <= first <= 0xef:
            length, minimum, code_point = 3, 0x800, first & 0x0f
        elif 0xf0 <= first <= 0xf4:
            length, minimum, code_point = 4, 0x10000, first & 0x07
        else:
            return "invalidLeadingByte", index
        for continuation in range(1, length):
            position = index + continuation
            if position >= len(data):
                return "truncatedSequence", index
            following = data[position]
            if following & 0xc0 != 0x80:
                return "invalidContinuationByte", position
            code_point = code_point << 6 | following & 0x3f
        if code_point < minimum:
            return "overlongEncoding", index
        if 0xd800 <= code_point <= 0xdfff:
            return "surrogateCodePoint", index
        if code_point > 0x10ffff:
            return "codePointOutOfRange", index
        index += length
    return None


def strict_base64_failure(text: str) -> tuple[str, int] | None:
    alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    sextet = {character: index for index, character in enumerate(alphabet)}
    for position, character in enumerate(text):
        if character not in sextet and character != "=":
            return "invalidCharacter", position
    if len(text) % 4:
        return "invalidLength", len(text)
    for group_start in range(0, len(text), 4):
        group = text[group_start:group_start + 4]
        final = group_start + 4 == len(text)
        if not final:
            for offset, character in enumerate(group):
                if character == "=":
                    return "invalidPadding", group_start + offset
            continue
        forms = (
            all(character in sextet for character in group),
            all(character in sextet for character in group[:3]) and group[3:] == "=",
            all(character in sextet for character in group[:2]) and group[2:] == "==",
        )
        if not any(forms):
            patterns = ("AAAA", "AAA=", "AA==")
            for offset, character in enumerate(group):
                prefix = group[:offset + 1]
                if not any(all(
                        actual == expected or expected == "A" and actual in sextet
                        for actual, expected in zip(prefix, pattern[:offset + 1]))
                           for pattern in patterns):
                    return "invalidPadding", group_start + offset
            raise ValueError("padding oracle could not locate an invalid prefix")
        if group.endswith("==") and sextet[group[1]] & 0x0f:
            return "nonZeroPadBits", group_start + 1
        if group.endswith("=") and not group.endswith("==") and sextet[group[2]] & 0x03:
            return "nonZeroPadBits", group_start + 2
    return None


def bytes_fixture(value: str) -> bytes:
    if value == "-":
        return b""
    if value == "recipe:bytes-00-ff":
        return bytes(range(256))
    return bytes.fromhex(value)


def check_catalog(available: set[str]) -> set[str]:
    rows = read_rows("catalog.tsv", ("case_id",))
    expected = ([f"BYTES-N{number:03d}" for number in range(1, 13)]
                + [f"BYTES-F{number:03d}" for number in range(1, 13)]
                + [f"BYTES-R{number:03d}" for number in range(1, 11)])
    if [row["case_id"] for row in rows] != expected:
        raise ValueError("catalog IDs are not the exact ordered N16/F16/R16 set")
    for row in rows:
        if row["kind"] not in {"normal", "failure", "warning", "resource"}:
            raise ValueError("invalid catalog kind")
        if row["scope"] not in {"public", "internal"}:
            raise ValueError("invalid catalog scope")
        if row["evidence"] not in available:
            raise ValueError("missing catalog evidence: " + row["evidence"])
    return set(expected)


def check_utf8(catalog_ids: set[str]) -> None:
    rows = read_rows("vectors/utf8.tsv", ("case_id", "vector"))
    if len(rows) != 19:
        raise ValueError("UTF-8 vector count changed")
    for row in rows:
        if row["case_id"] not in catalog_ids:
            raise ValueError("UTF-8 vector outside catalog")
        text = json.loads(row["text_json"])
        if not isinstance(text, str):
            raise ValueError("UTF-8 text_json must be a string")
        expected = bytes.fromhex(row["utf8_hex"])
        if text.encode("utf-8") != expected or strict_utf8_failure(expected) is not None:
            raise ValueError("UTF-8 vector mismatch: " + row["vector"])
        points = "-" if not text else " ".join(f"U+{ord(character):04X}" for character in text)
        if points != row["code_points"]:
            raise ValueError("code point list mismatch: " + row["vector"])
    failures = read_rows("vectors/utf8-failures.tsv", ("case_id", "variant"))
    if set(row["kind"] for row in failures) != set(UTF8_KINDS):
        raise ValueError("UTF-8 failure kinds are not the exact closed set")
    for row in failures:
        actual = strict_utf8_failure(bytes.fromhex(row["input_hex"]))
        expected = row["kind"], int(row["offset"])
        if actual != expected:
            raise ValueError(f"UTF-8 failure mismatch: {row['variant']}: {actual}")


def check_base64(catalog_ids: set[str]) -> None:
    rows = read_rows("vectors/base64.tsv", ("case_id", "vector"))
    if len(rows) != 9:
        raise ValueError("Base64 vector count changed")
    for row in rows:
        if row["case_id"] not in catalog_ids:
            raise ValueError("Base64 vector outside catalog")
        data = bytes_fixture(row["input_hex"])
        encoded = base64.b64encode(data).decode("ascii")
        expected_encoded = "" if row["encoded"] == "-" else row["encoded"]
        if encoded != expected_encoded or strict_base64_failure(encoded) is not None:
            raise ValueError("Base64 vector mismatch: " + row["vector"])
        if base64.b64decode(encoded, validate=True) != data:
            raise ValueError("Base64 reverse mismatch: " + row["vector"])
    failures = read_rows("vectors/base64-failures.tsv", ("case_id", "variant"))
    if set(row["kind"] for row in failures) != set(BASE64_KINDS):
        raise ValueError("Base64 failure kinds are not the exact closed set")
    for row in failures:
        text = bytes.fromhex(row["input_utf8_hex"]).decode("utf-8")
        actual = strict_base64_failure(text)
        expected = row["kind"], int(row["position"])
        if actual != expected:
            raise ValueError(f"Base64 failure mismatch: {row['variant']}: {actual}")


def check_operations(catalog_ids: set[str]) -> None:
    slices = read_rows("operations/slices.tsv", ("case_id", "variant"))
    for row in slices:
        if row["case_id"] not in catalog_ids:
            raise ValueError("slice outside catalog")
        data = bytes.fromhex(row["input_hex"])
        start, end = int(row["start"]), int(row["end"])
        valid = 0 <= start <= end <= len(data)
        if valid != (row["outcome"] == "success"):
            raise ValueError("slice outcome mismatch: " + row["variant"])
        if valid and data[start:end].hex() != row["output_hex"]:
            raise ValueError("slice bytes mismatch: " + row["variant"])
        if int(row["construction"]) != 0 or int(row["work"]) != (1 if valid else 0):
            raise ValueError("slice accounting mismatch")
    equality = read_rows("operations/equality.tsv", ("case_id", "variant"))
    for row in equality:
        left, right = bytes.fromhex(row["left_hex"]), bytes.fromhex(row["right_hex"])
        expected_result = row["result"] == "true"
        if row["wrapper"] == "result-state":
            if expected_result or int(row["compared_bytes"]) != 0:
                raise ValueError("result state must short-circuit")
            continue
        compared = 0
        actual_result = len(left) == len(right)
        if actual_result:
            for first, second in zip(left, right):
                compared += 1
                if first != second:
                    actual_result = False
                    break
        if actual_result != expected_result or compared != int(row["compared_bytes"]):
            raise ValueError("equality expectation mismatch: " + row["variant"])


def check_resources(catalog_ids: set[str]) -> None:
    rows = read_rows("resources.tsv", ("case_id", "variant"))
    expected_ids = {f"BYTES-R{number:03d}" for number in range(1, 11)} | {"BYTES-F012"}
    if {row["case_id"] for row in rows} != expected_ids:
        raise ValueError("resource rows do not cover the exact resource IDs")
    for row in rows:
        if row["case_id"] not in catalog_ids or row["state_preserved"] != "true":
            raise ValueError("resource ownership or atomicity mismatch")
        for field in ("limit", "used", "requested", "observed", "construction_after", "work_after"):
            if int(row[field]) < 0:
                raise ValueError("negative resource count")
        if row["outcome"] == "diagnostic" and row["diagnostic"] == "-":
            raise ValueError("diagnostic resource row without code")
    exact = {(row["metric"], row["limit"], row["observed"]) for row in rows}
    for boundary in {
        ("byteSequenceBytes", "67108864", "67108865"),
        ("byteSequenceConstructionBytes", "134217728", "134217729"),
        ("byteSequenceWorkBytes", "268435456", "268435457"),
        ("stringUtf8Bytes", "16777216", "16777220"),
    }:
        if boundary not in exact:
            raise ValueError("missing normative resource boundary: " + repr(boundary))
    work_rejected = next(row for row in rows if row["variant"] == "second-reservation-work-rejected")
    if work_rejected["construction_after"] != "0" or work_rejected["work_after"] != "268435454":
        raise ValueError("second reservation must not partially update")


def check_expected(catalog_ids: set[str]) -> None:
    diagnostics = read_rows("expected/diagnostics.tsv", ("case_id", "variant"))
    if {row["code"] for row in diagnostics} != NEW_CODES:
        raise ValueError("diagnostics do not cover the exact four new codes")
    for row in diagnostics:
        if row["case_id"] not in catalog_ids or row["stage"] != "runtime":
            raise ValueError("diagnostic ownership or stage")
        fields = json.loads(row["fields_json"])
        if not isinstance(fields, dict) or not fields:
            raise ValueError("diagnostic fields must be a nonempty object")
    states = read_rows("expected/states.tsv", ("case_id", "variant"))
    for row in states:
        if row["case_id"] not in catalog_ids:
            raise ValueError("state outside catalog")
        if row["stack_replaced"] not in {"true", "false"}:
            raise ValueError("invalid state boolean")
        bytes.fromhex(row["stdout_utf8_hex"]).decode("utf-8")
        bytes.fromhex(row["stderr_utf8_hex"]).decode("utf-8")
        int(row["construction"])
        int(row["work"])
    traces = read_rows("expected/trace.tsv", ("case_id", "variant"))
    if len(traces) != 6:
        raise ValueError("trace variant count changed")
    for row in traces:
        if row["expected_trace"] != row["type"] + ":<redacted>":
            raise ValueError("trace is not fully redacted")
        for marker in row["forbidden_markers"].split("|"):
            if marker in row["expected_trace"]:
                raise ValueError("forbidden marker in expected trace")


def check_manifest(available: set[str]) -> None:
    rows = read_rows("manifest.tsv", ("path",))
    expected_files = {row["path"] for row in rows} | {"README.md", "manifest.tsv"}
    if available != expected_files:
        raise ValueError("manifest inventory mismatch")
    for row in rows:
        content = (DATA / row["path"]).read_bytes()
        if content.startswith(b"\xef\xbb\xbf") or b"\r" in content or not content.endswith(b"\n"):
            raise ValueError("invalid text encoding or line ending: " + row["path"])
        content.decode("utf-8")
        if len(content) != int(row["bytes"]):
            raise ValueError("byte count mismatch: " + row["path"])
        if hashlib.sha256(content).hexdigest() != row["sha256"]:
            raise ValueError("hash mismatch: " + row["path"])


def check() -> None:
    available = {path.relative_to(DATA).as_posix() for path in DATA.rglob("*") if path.is_file()}
    catalog_ids = check_catalog(available)
    check_utf8(catalog_ids)
    check_base64(catalog_ids)
    check_operations(catalog_ids)
    check_resources(catalog_ids)
    check_expected(catalog_ids)
    check_manifest(available)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", required=True)
    parser.parse_args()
    check()
    print("byte-sequences data: ok")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
