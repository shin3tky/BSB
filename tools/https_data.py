#!/usr/bin/env python3
"""Read-only integrity and independent-oracle checks for https data."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "tests/conformance/https"
METHODS = ["GET", "HEAD", "POST", "PUT", "PATCH", "DELETE"]
FAILURE_KINDS = [
    "nameResolutionFailure",
    "connectTimeout",
    "connectionFailure",
    "tlsFailure",
    "responseTimeout",
    "responseTooLarge",
    "responseHeadersTooLarge",
    "protocolFailure",
    "transportFailure",
]
RESERVED_HEADERS = {
    "authorization", "connection", "content-length", "expect", "host",
    "proxy-authorization", "te", "trailer", "transfer-encoding", "upgrade",
}
NEW_CODES = {
    "E_EXPECTED_HTTP_ARGUMENT_START",
    "E_EXPECTED_HTTP_CONNECTION_ARGUMENT",
    "E_EXPECTED_HTTP_METHOD_ARGUMENT",
    "E_HTTP_ARGUMENT_COUNT",
    "E_EXPECTED_HTTP_ARGUMENT_END",
    "E_HTTP_METHOD_INVALID",
    "E_HTTP_PATH_INVALID",
    "E_HTTP_PATH_LIMIT",
    "E_HTTP_QUERY_LIMIT",
    "E_HTTP_HEADER_NAME_INVALID",
    "E_HTTP_HEADER_VALUE_INVALID",
    "E_HTTP_HEADER_RESERVED",
    "E_HTTP_HEADER_LIMIT",
    "E_HTTP_METADATA_CONSTRUCTION_LIMIT",
    "E_HTTP_TARGET_URI_LIMIT",
    "E_HTTP_METHOD_NOT_ALLOWED",
    "E_HTTP_BODY_NOT_ALLOWED",
    "E_HTTP_REQUEST_SIZE_LIMIT",
    "E_HTTP_SEND_LIMIT",
    "E_HTTP_REQUEST_TOTAL_LIMIT",
    "E_HTTP_RESPONSE_TOTAL_LIMIT",
    "E_HTTP_AUTHENTICATION_UNSUPPORTED",
    "E_HTTP_CREDENTIAL_NOT_CONFIGURED",
    "E_HTTP_CREDENTIAL_ACCESS_DENIED",
    "E_HTTP_CREDENTIAL_INVALID",
    "E_HTTP_CANCELLED",
}
HEADERS = {
    "catalog.tsv": ["case_id", "kind", "evidence", "scope"],
    "vectors/uri.tsv": [
        "case_id", "variant", "base_uri", "path", "query_json", "expected_uri",
        "outcome", "reason",
    ],
    "vectors/headers.tsv": [
        "case_id", "variant", "initial_json", "name", "value", "outcome",
        "expected_json", "reason",
    ],
    "expected/requests.tsv": [
        "case_id", "variant", "method", "path", "query_count", "header_count",
        "body_kind", "body_bytes", "content_type_implicit", "original_preserved", "result",
    ],
    "expected/responses.tsv": [
        "case_id", "variant", "status", "headers_json", "lookup", "values_json",
        "body_hex", "outcome",
    ],
    "expected/transports.tsv": [
        "case_id", "variant", "resolver_state", "transport_state", "status",
        "failure_kind", "resolver_calls", "transport_calls", "result",
    ],
    "resources.tsv": [
        "case_id", "variant", "metric", "limit", "used", "requested", "observed",
        "outcome", "diagnostic", "counter_after", "state_preserved",
    ],
    "expected/trace.tsv": [
        "case_id", "variant", "type", "expected_trace", "forbidden_markers",
    ],
    "expected/diagnostics.tsv": [
        "case_id", "variant", "code", "stage", "fields_json",
    ],
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


def percent_encode(value: str, preserve_slash: bool = False) -> str:
    """Encode UTF-8 bytes using only the HTTPS feature's unreserved set."""
    unreserved = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    result: list[str] = []
    for byte in value.encode("utf-8"):
        if byte in unreserved or preserve_slash and byte == ord("/"):
            result.append(chr(byte))
        else:
            result.append(f"%{byte:02X}")
    return "".join(result)


def path_failure(path: str) -> str | None:
    if path.startswith("/"):
        return "absolutePath"
    if "?" in path:
        return "queryDelimiter"
    if "#" in path:
        return "fragmentDelimiter"
    if "\0" in path:
        return "nul"
    if any(segment in {".", ".."} for segment in path.split("/")):
        return "dotSegment"
    return None


def expected_uri(base_uri: str, path: str, query: list[list[str]]) -> str:
    reason = path_failure(path)
    if reason is not None:
        raise ValueError(reason)
    suffix = percent_encode(path, preserve_slash=True)
    if query:
        suffix += "?" + "&".join(
            percent_encode(name) + "=" + percent_encode(value) for name, value in query
        )
    return base_uri + suffix


def decode_fixture(value: str) -> str:
    return value.replace("\\r", "\r").replace("\\n", "\n").replace("\\0", "\0")


def header_failure(name: str, value: str) -> str | None:
    token = b"!#$%&'*+-.^_`|~0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    name_bytes = name.encode("utf-8")
    if not name:
        return "emptyName"
    if len(name_bytes) > 256:
        return "nameTooLong"
    if any(byte not in token for byte in name_bytes):
        return "invalidNameCharacter"
    if name.lower() in RESERVED_HEADERS:
        return "reserved"
    value_bytes = value.encode("utf-8")
    if len(value_bytes) > 8192:
        return "valueTooLong"
    if any(byte != 9 and not 32 <= byte <= 126 for byte in value_bytes):
        return "invalidValueCharacter"
    return None


def check_catalog(available: set[str]) -> set[str]:
    rows = read_rows("catalog.tsv", ("case_id",))
    expected = ([f"HTTPS-N{number:03d}" for number in range(1, 13)]
                + [f"HTTPS-F{number:03d}" for number in range(1, 15)]
                + [f"HTTPS-R{number:03d}" for number in range(1, 11)])
    if [row["case_id"] for row in rows] != expected:
        raise ValueError("catalog IDs are not the exact ordered N17/F17/R17 set")
    for row in rows:
        if row["kind"] not in {"normal", "failure", "resource"}:
            raise ValueError("invalid catalog kind")
        if row["scope"] not in {"public", "internal"}:
            raise ValueError("invalid catalog scope")
        if row["evidence"] not in available:
            raise ValueError("missing catalog evidence: " + row["evidence"])
    return set(expected)


def check_uri(catalog_ids: set[str]) -> None:
    rows = read_rows("vectors/uri.tsv", ("case_id", "variant"))
    reasons = set()
    for row in rows:
        if row["case_id"] not in catalog_ids:
            raise ValueError("URI vector outside catalog")
        query = json.loads(row["query_json"])
        if not isinstance(query, list) or any(
                not isinstance(item, list) or len(item) != 2
                or not all(isinstance(part, str) for part in item) for item in query):
            raise ValueError("query_json must be a list of string pairs")
        reason = path_failure(row["path"])
        if row["outcome"] == "success":
            actual = expected_uri(row["base_uri"], row["path"], query)
            if reason is not None or actual != row["expected_uri"]:
                raise ValueError("URI expectation mismatch: " + row["variant"])
        elif reason != row["reason"]:
            raise ValueError("path failure mismatch: " + row["variant"])
        else:
            reasons.add(reason)
    if reasons != {"absolutePath", "queryDelimiter", "fragmentDelimiter", "dotSegment"}:
        raise ValueError("path vector reasons changed")


def check_headers(catalog_ids: set[str]) -> None:
    rows = read_rows("vectors/headers.tsv", ("case_id", "variant"))
    reserved = set()
    for row in rows:
        if row["case_id"] not in catalog_ids:
            raise ValueError("header vector outside catalog")
        initial = json.loads(row["initial_json"])
        name, value = row["name"], decode_fixture(row["value"])
        reason = header_failure(name, value)
        if row["outcome"] == "success":
            if reason is not None:
                raise ValueError("valid header rejected: " + row["variant"])
            normalized = name.lower()
            actual = [[key, old] if key != normalized else [key, value] for key, old in initial]
            if not any(key == normalized for key, _ in initial):
                actual.append([normalized, value])
            if actual != json.loads(row["expected_json"]):
                raise ValueError("header replacement mismatch: " + row["variant"])
        elif reason != row["reason"]:
            raise ValueError("header failure mismatch: " + row["variant"])
        if reason == "reserved":
            reserved.add(name.lower())
    if reserved != RESERVED_HEADERS:
        raise ValueError("reserved headers are not the exact closed set")


def check_requests_and_responses(catalog_ids: set[str]) -> None:
    requests = read_rows("expected/requests.tsv", ("case_id", "variant"))
    if {row["method"] for row in requests if row["method"] != "-"} != set(METHODS):
        raise ValueError("request methods are not the exact six methods")
    if not all(row["case_id"] in catalog_ids and row["original_preserved"] == "true"
               and row["content_type_implicit"] == "false" for row in requests):
        raise ValueError("request immutability or implicit content type changed")
    if {row["body_kind"] for row in requests} != {"absent", "json", "string", "bytes"}:
        raise ValueError("request body variants changed")
    responses = read_rows("expected/responses.tsv", ("case_id", "variant"))
    for row in responses:
        if row["case_id"] not in catalog_ids:
            raise ValueError("response outside catalog")
        status = int(row["status"])
        expected_outcome = "success" if 200 <= status <= 599 else "failure"
        if row["outcome"] != expected_outcome:
            raise ValueError("response status boundary mismatch: " + row["variant"])
        headers = json.loads(row["headers_json"])
        values = json.loads(row["values_json"])
        if row["lookup"] != "-":
            actual = [value for name, value in headers if name.lower() == row["lookup"].lower()]
            if actual != values:
                raise ValueError("response header order mismatch: " + row["variant"])
        bytes.fromhex(row["body_hex"])


def check_transports(catalog_ids: set[str]) -> None:
    rows = read_rows("expected/transports.tsv", ("case_id", "variant"))
    kinds = [row["failure_kind"] for row in rows if row["case_id"] == "HTTPS-N008"]
    if kinds != FAILURE_KINDS:
        raise ValueError("transport failure kinds are not the exact closed ordered set")
    for row in rows:
        if row["case_id"] not in catalog_ids:
            raise ValueError("transport row outside catalog")
        resolver_calls, transport_calls = int(row["resolver_calls"]), int(row["transport_calls"])
        if resolver_calls not in {0, 1} or transport_calls not in {0, 1}:
            raise ValueError("capability called more than once")
        if transport_calls > resolver_calls:
            raise ValueError("transport called without resolution")
        if row["result"] == "success" and not 200 <= int(row["status"]) <= 599:
            raise ValueError("successful transport without final response")
    required = {
        "not-configured", "denied", "invalid", "basic", "oauth2",
        "credential-missing", "credential-denied", "credential-name-invalid",
        "credential-value-invalid", "resolver-capability-absent",
        "transport-capability-absent", "resolver-failure", "transport-failure", "cancelled",
    }
    if not required <= {row["variant"] for row in rows}:
        raise ValueError("transport boundary variants are incomplete")


def check_resources(catalog_ids: set[str]) -> None:
    rows = read_rows("resources.tsv", ("case_id", "variant"))
    for row in rows:
        if row["case_id"] not in catalog_ids or row["state_preserved"] != "true":
            raise ValueError("resource ownership or atomicity mismatch")
        values = [int(row[name]) for name in ("limit", "used", "requested", "observed", "counter_after")]
        if any(value < 0 for value in values):
            raise ValueError("negative resource value")
        if row["outcome"] == "diagnostic" and row["diagnostic"] == "-":
            raise ValueError("diagnostic resource row without code")
    exact = {(row["metric"], int(row["limit"]), int(row["observed"])) for row in rows}
    for boundary in {
        ("httpPathBytes", 8192, 8193), ("httpQueryItems", 256, 257),
        ("httpQueryBytes", 65536, 65537), ("httpRequestHeaderValues", 128, 129),
        ("httpRequestHeaderBytes", 65536, 65537), ("httpTargetUriBytes", 65536, 65537),
        ("httpSendCalls", 1024, 1025), ("httpRequestAttemptBytes", 134217728, 134217729),
        ("httpResponseReceivedBytes", 134217728, 134217729),
        ("httpMetadataConstructionBytes", 16777216, 16777217),
        ("requestBytes", 67108864, 67108865), ("httpResponseHeaderValues", 128, 129),
        ("httpResponseHeaderBytes", 65536, 65537),
    }:
        if boundary not in exact:
            raise ValueError("missing normative resource boundary: " + repr(boundary))


def check_expected(catalog_ids: set[str]) -> None:
    diagnostics = read_rows("expected/diagnostics.tsv", ("case_id", "variant"))
    if len(diagnostics) != 26 or {row["code"] for row in diagnostics} != NEW_CODES:
        raise ValueError("diagnostics do not cover the exact 26 new codes")
    for row in diagnostics:
        if row["case_id"] not in catalog_ids or row["stage"] not in {"syntax", "name", "runtime"}:
            raise ValueError("diagnostic ownership or stage")
        fields = json.loads(row["fields_json"])
        if not isinstance(fields, dict) or not fields:
            raise ValueError("diagnostic fields must be a nonempty object")
    traces = read_rows("expected/trace.tsv", ("case_id", "variant"))
    if len(traces) != 6:
        raise ValueError("trace variant count changed")
    for row in traces:
        redacted = "<redacted>" in row["expected_trace"]
        closed_event = row["type"] == "http.send" and row["expected_trace"] == (
            "http.send:顧客管理API:POST:response"
        )
        if row["case_id"] not in catalog_ids or not (redacted or closed_event):
            raise ValueError("trace is not redacted")
        if any(marker in row["expected_trace"] for marker in row["forbidden_markers"].split("|")):
            raise ValueError("forbidden marker in expected trace")
    messages = {}
    for line in (DATA / "messages.properties").read_text(encoding="utf-8").splitlines():
        key, separator, value = line.partition("=")
        if not separator or not value or key in messages:
            raise ValueError("invalid messages.properties")
        messages[key] = value
    if set(messages) != NEW_CODES:
        raise ValueError("messages do not cover the exact 26 new codes")


def check_sources() -> None:
    methods = (DATA / "sources/HTTPS-N-methods.bsb").read_text(encoding="utf-8")
    for method in METHODS:
        if f"HTTP要求を送信する<顧客管理API,{method}>" not in methods:
            raise ValueError("missing static method source: " + method)
    if methods.index("HTTP要求を送信する") > methods.index("顧客管理APIは 論理接続"):
        raise ValueError("forward logical-connection reference was lost")
    chapter = (DATA / "chapter/https-chapter.bsb").read_text(encoding="utf-8")
    canonical = (DATA / "canonical/https-chapter.bsb").read_text(encoding="utf-8")
    if chapter == canonical or "<顧客管理API,POST>" not in canonical:
        raise ValueError("chapter formatting expectation is not fixed")


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
    check_uri(catalog_ids)
    check_headers(catalog_ids)
    check_requests_and_responses(catalog_ids)
    check_transports(catalog_ids)
    check_resources(catalog_ids)
    check_expected(catalog_ids)
    check_sources()
    check_manifest(available)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", required=True)
    parser.parse_args()
    check()
    print("https data: ok")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
