#!/usr/bin/env python3
"""Read-only integrity checks for logical connections logical-connection conformance data."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "tests/conformance/logical-connections"
REASONS = [
    "BASE_URI_INVALID",
    "ORIGIN_POLICY_INVALID",
    "METHOD_POLICY_INVALID",
    "AUTHENTICATION_POLICY_INVALID",
    "CONNECT_TIMEOUT_INVALID",
    "RESPONSE_TIMEOUT_INVALID",
    "REQUEST_LIMIT_INVALID",
    "RESPONSE_LIMIT_INVALID",
    "REDIRECT_POLICY_INVALID",
    "RETRY_POLICY_INVALID",
]
NEW_CODES = {
    "E_LOGICAL_CONNECTION_DECLARATION_VALUE",
    "E_LOGICAL_CONNECTION_DECLARATION_SCOPE",
    "E_LOGICAL_CONNECTION_LIMIT",
    "E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT_START",
    "E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT",
    "E_LOGICAL_CONNECTION_ARGUMENT_COUNT",
    "E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT_END",
    "E_UNDECLARED_LOGICAL_CONNECTION",
    "E_LOGICAL_CONNECTION_REFERENCE_NOT_ALLOWED",
    "E_LOGICAL_CONNECTION_NOT_CONFIGURED",
    "E_LOGICAL_CONNECTION_ACCESS_DENIED",
    "E_LOGICAL_CONNECTION_CONFIGURATION_INVALID",
}
HEADERS = {
    "catalog.tsv": ["case_id", "kind", "evidence", "scope"],
    "cases.tsv": [
        "case_id", "variant", "resolver_present", "resolver_outcome", "policy_id",
        "invalid_reason", "expected_outcome", "diagnostic", "resolver_calls",
        "connection", "operation", "state_preserved", "event",
    ],
    "policies.tsv": [
        "policy_id", "base_uri", "origins", "methods", "auth_kind",
        "credential_reference", "connect_timeout_ms", "response_timeout_ms",
        "max_request_bytes", "max_response_bytes", "redirect_policy", "retry_policy",
        "expected_reason", "secret_marker",
    ],
    "resolver.tsv": [
        "fixture", "connection", "operation", "outcome", "policy_id", "invalid_reason",
        "host_failure_marker", "expected_calls",
    ],
    "resources.tsv": [
        "case_id", "variant", "metric", "input_shape", "limit", "observed", "outcome",
        "diagnostic", "reason", "resolver_calls", "state_preserved",
    ],
    "expected/diagnostics.tsv": [
        "case_id", "variant", "occurrence", "code", "stage", "target_lexeme",
        "fields_json", "expected", "actual",
    ],
    "expected/states.tsv": [
        "source", "resolver_fixture", "exit", "data_stack", "globals", "stdout_utf8_hex",
        "stderr_utf8_hex", "capabilities", "diagnostic", "resolver_calls", "event",
    ],
    "expected/trace.tsv": [
        "variant", "outcome", "effect", "forbidden_markers", "data_stack", "globals",
        "diagnostic", "resolver_calls",
    ],
    "manifest.tsv": ["path", "sha256", "bytes"],
}


def read_rows(relative: str, keys: tuple[str, ...]) -> list[dict[str, str]]:
    path = DATA / relative
    with path.open("r", encoding="utf-8", newline="") as stream:
        raw = list(csv.reader(stream, delimiter="\t"))
    if not raw or raw[0] != HEADERS[relative]:
        raise ValueError(f"wrong header: {relative}")
    result: list[dict[str, str]] = []
    seen: set[tuple[str, ...]] = set()
    for values in raw[1:]:
        if len(values) != len(raw[0]):
            raise ValueError(f"wrong column count: {relative}")
        row = dict(zip(raw[0], values))
        key = tuple(row[name] for name in keys)
        if key in seen:
            raise ValueError(f"duplicate row: {relative}: {key}")
        seen.add(key)
        result.append(row)
    return result


def require_json(text: str, label: str) -> object:
    try:
        return json.loads(text)
    except json.JSONDecodeError as error:
        raise ValueError(f"invalid JSON: {label}") from error


def check_catalog(available: set[str]) -> tuple[list[dict[str, str]], set[str]]:
    rows = read_rows("catalog.tsv", ("case_id",))
    expected = ([f"CONN-N{number:03d}" for number in range(1, 11)]
                + [f"CONN-F{number:03d}" for number in range(1, 13)]
                + [f"CONN-R{number:03d}" for number in range(1, 11)])
    if [row["case_id"] for row in rows] != expected:
        raise ValueError("catalog IDs are not the exact ordered N15/F15/R15 set")
    for row in rows:
        if row["kind"] not in {"normal", "failure", "warning", "resource"}:
            raise ValueError("invalid catalog kind")
        if row["scope"] not in {"public", "internal"}:
            raise ValueError("invalid catalog scope")
        if row["evidence"] not in available:
            raise ValueError("missing catalog evidence: " + row["evidence"])
    return rows, set(expected)


def check_cases(catalog_ids: set[str], policy_ids: set[str]) -> None:
    rows = read_rows("cases.tsv", ("case_id", "variant"))
    for row in rows:
        if row["case_id"] not in catalog_ids:
            raise ValueError("case outside catalog")
        if row["resolver_present"] not in {"true", "false"}:
            raise ValueError("invalid resolver_present")
        if row["expected_outcome"] not in {"success", "diagnostic", "internal"}:
            raise ValueError("invalid expected outcome")
        if row["operation"] != "resolve" or row["state_preserved"] != "true":
            raise ValueError("invalid operation or atomicity")
        calls = int(row["resolver_calls"])
        if row["resolver_present"] == "false" and calls != 0:
            raise ValueError("absent resolver must not be called")
        if row["resolver_present"] == "true" and calls != 1:
            raise ValueError("present resolver must be called exactly once")
        if row["policy_id"] != "-" and row["policy_id"] not in policy_ids:
            raise ValueError("unknown case policy")
        if row["invalid_reason"] != "-" and row["invalid_reason"] not in REASONS:
            raise ValueError("unknown case reason")


def check_policies() -> tuple[list[dict[str, str]], set[str]]:
    rows = read_rows("policies.tsv", ("policy_id",))
    reasons = {row["expected_reason"] for row in rows if row["expected_reason"] != "-"}
    if reasons != set(REASONS):
        raise ValueError("policy reasons do not cover the exact normative set")
    if len(rows) != 12 or rows[0]["policy_id"] != "valid":
        raise ValueError("policy fixture count or order changed")
    if rows[-1]["policy_id"] != "first-invalid" or rows[-1]["expected_reason"] != REASONS[0]:
        raise ValueError("first-invalid does not fix validation priority")
    for row in rows:
        if not row["secret_marker"].startswith("SECRET_"):
            raise ValueError("missing unique policy secret marker")
    return rows, {row["policy_id"] for row in rows}


def check_resolvers(policy_ids: set[str]) -> set[str]:
    rows = read_rows("resolver.tsv", ("fixture", "connection"))
    outcomes = {"RESOLVED", "NOT_CONFIGURED", "DENIED", "INVALID", "FAILURE", "CONTRACT_NULL"}
    for row in rows:
        if row["operation"] != "resolve" or row["outcome"] not in outcomes:
            raise ValueError("invalid resolver route")
        if row["policy_id"] != "-" and row["policy_id"] not in policy_ids:
            raise ValueError("unknown resolver policy")
        if row["invalid_reason"] != "-" and row["invalid_reason"] not in REASONS:
            raise ValueError("unknown resolver reason")
        if int(row["expected_calls"]) < 1:
            raise ValueError("resolver fixture must be called")
    return {row["fixture"] for row in rows}


def check_resources(catalog_ids: set[str]) -> None:
    rows = read_rows("resources.tsv", ("case_id", "variant"))
    if {row["case_id"] for row in rows if row["case_id"].startswith("CONN-R")} != {
            f"CONN-R{number:03d}" for number in range(1, 11)}:
        raise ValueError("resource rows do not cover CONN-R001..010")
    for row in rows:
        if row["case_id"] not in catalog_ids or row["state_preserved"] != "true":
            raise ValueError("resource ownership or atomicity")
        if row["reason"] != "-" and row["reason"] not in REASONS and row["reason"] != "outputLimit":
            raise ValueError("unknown resource reason")
        int(row["resolver_calls"])
    exact = {(row["metric"], row["limit"], row["observed"]) for row in rows}
    for boundary in {
        ("logicalConnections", "10000", "10001"),
        ("baseUriAsciiBytes", "8192", "8193"),
        ("originTotalAsciiBytes", "262144", "262145"),
        ("connectTimeoutMillis", "30000", "30001"),
        ("maxRequestBytes", "67108864", "67108865"),
        ("executedInstructions", "10000000", "10000001"),
        ("elapsedNanos", "30000000000", "30000000001"),
        ("explainOutputBytes", "67108864", "67108865"),
    }:
        if boundary not in exact:
            raise ValueError(f"missing normative boundary: {boundary}")


def check_diagnostics(catalog_ids: set[str]) -> None:
    rows = read_rows("expected/diagnostics.tsv", ("case_id", "variant", "occurrence"))
    if {row["code"] for row in rows} != NEW_CODES:
        raise ValueError("diagnostics do not cover the exact 12 new codes")
    if {row["case_id"] for row in rows} != {f"CONN-F{number:03d}" for number in range(1, 13)}:
        raise ValueError("diagnostics do not cover CONN-F001..012")
    reasons: list[str] = []
    for row in rows:
        if row["case_id"] not in catalog_ids:
            raise ValueError("diagnostic outside catalog")
        fields = require_json(row["fields_json"], row["case_id"])
        if not isinstance(fields, dict) or not fields:
            raise ValueError("diagnostic fields must be a nonempty object")
        if row["code"] == "E_LOGICAL_CONNECTION_CONFIGURATION_INVALID":
            reasons.append(str(fields.get("reason")))
    if reasons != REASONS:
        raise ValueError("diagnostic reasons differ from normative order")


def check_states(available: set[str], resolver_fixtures: set[str]) -> None:
    rows = read_rows("expected/states.tsv", ("source", "resolver_fixture"))
    for row in rows:
        if row["source"] not in available:
            raise ValueError("missing state source")
        if row["resolver_fixture"] not in resolver_fixtures | {"absent"}:
            raise ValueError("unknown state resolver fixture")
        for field in ("data_stack", "globals", "capabilities"):
            require_json(row[field], "state " + field)
        bytes.fromhex(row["stdout_utf8_hex"]).decode("utf-8")
        bytes.fromhex(row["stderr_utf8_hex"]).decode("utf-8")
        if row["data_stack"] != "[]" or row["globals"] != "{}":
            raise ValueError("state atomicity expectation changed")


def check_traces() -> None:
    rows = read_rows("expected/trace.tsv", ("variant",))
    for row in rows:
        if row["data_stack"] != "[]" or row["globals"] != "{}":
            raise ValueError("trace state must be empty")
        effect = row["effect"]
        for marker in row["forbidden_markers"].split("|"):
            if marker in effect or marker in row["diagnostic"]:
                raise ValueError("forbidden marker appears in expected public trace")


def check_parameterized_json() -> None:
    path = DATA / "expected/parameterized-capabilities.json"
    value = require_json(path.read_text(encoding="utf-8"), path.name)
    if not isinstance(value, dict) or list(value) != [
            "schemaVersion", "declarations", "summaryRequirements", "userWords"]:
        raise ValueError("parameterized capability member order changed")
    if value["schemaVersion"] != 1 or len(value["declarations"]) != 1:
        raise ValueError("parameterized capability version or declarations")
    declaration = value["declarations"][0]
    if declaration["name"] != "接続A" or declaration["uses"][0]["operation"] != "resolve":
        raise ValueError("parameterized capability declaration changed")
    source = (DATA / "sources/CONN-N-explain.bsb").read_bytes()
    declaration_bytes = "接続A".encode()
    call_bytes = "論理接続を確認する<接続A>".encode()
    if source[0:7] != declaration_bytes or source[56:92] != call_bytes:
        raise ValueError("source spans no longer match the independent JSON expectation")


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
    _, catalog_ids = check_catalog(available)
    _, policy_ids = check_policies()
    resolver_fixtures = check_resolvers(policy_ids)
    check_cases(catalog_ids, policy_ids)
    check_resources(catalog_ids)
    check_diagnostics(catalog_ids)
    check_states(available, resolver_fixtures)
    check_traces()
    check_parameterized_json()
    check_manifest(available)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", required=True)
    parser.parse_args()
    check()
    print("logical-connections data: ok")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
