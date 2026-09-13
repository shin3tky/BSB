"""Read-only tests of Result fixture integrity, not tests of the Result implementation."""

import csv
import hashlib
import io
import json
from pathlib import Path
import re
import unittest

import result_values_data as author

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "tests/conformance/result-values"


def rows(text, columns, keys):
    raw = list(csv.reader(io.StringIO(text), delimiter="\t"))
    if not raw or raw[0] != columns:
        raise ValueError("wrong header")
    result, seen = [], set()
    for values in raw[1:]:
        if len(values) != len(columns):
            raise ValueError("wrong column count")
        row = dict(zip(columns, values))
        key = tuple(row[k] for k in keys)
        if key in seen:
            raise ValueError("duplicate row")
        seen.add(key)
        result.append(row)
    return result


def audit_files(files):
    manifest = json.loads(files["manifest.json"])
    fixed = rows(files["central/file-hashes.tsv"].decode(),
                 ["path", "sha256", "bytes"], ["path"])
    expected = set(manifest["files"]) | {"manifest.json", "central/file-hashes.tsv"}
    expected |= {row["path"] for row in fixed}
    if set(files) - {"README.md"} != expected:
        raise ValueError("missing or unused file")
    for path, digest in manifest["files"].items():
        if hashlib.sha256(files[path]).hexdigest() != digest:
            raise ValueError("hash mismatch: " + path)
    for row in fixed:
        value = files[row["path"]]
        if len(value) != int(row["bytes"]):
            raise ValueError("byte count mismatch: " + row["path"])
        if hashlib.sha256(value).hexdigest() != row["sha256"]:
            raise ValueError("hash mismatch: " + row["path"])


def small_alphabet_token_count(source):
    """Independent counter only for the boundary recipe's known token alphabet."""
    tokens = re.findall(r"[^\s<>,（）。【】]+|[<>,（）。【】]", source)
    return sum(2 if token.endswith("とは") else 1 for token in tokens)


class ResultDataTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.files = {p.relative_to(DATA).as_posix(): p.read_bytes() for p in DATA.rglob("*") if p.is_file()}
        cls.cases = rows(cls.files["cases.tsv"].decode(), author.CASE_COLUMNS, ["case_id", "variant"])
        cls.diagnostics = rows(cls.files["diagnostics.tsv"].decode(), author.DIAG_COLUMNS,
                               ["case_id", "variant", "command", "occurrence"])
        cls.resources = rows(cls.files["resources.tsv"].decode(), author.RESOURCE_COLUMNS,
                             ["case_id", "variant"])
        cls.runtime = rows(cls.files["runtime/runtime-cases.tsv"].decode(), author.RUNTIME_COLUMNS,
                         ["case_id", "variant"])
        cls.central = rows(cls.files["central/catalog.tsv"].decode(),
                           ["case_id", "kind", "evidence", "primary_code", "commands"],
                           ["case_id"])
        cls.command_hashes = rows(cls.files["central/command-hashes.tsv"].decode(),
                                  ["source", "command", "exit", "stdout_sha256", "stderr_sha256",
                                   "stdout_bytes", "stderr_bytes"],
                                  ["source", "command"])
        cls.file_hashes = rows(cls.files["central/file-hashes.tsv"].decode(),
                               ["path", "sha256", "bytes"], ["path"])

    def test_exact_type_scope_and_counts(self):
        self.assertEqual(set(author.NORMAL_IDS + author.FAILURE_IDS), {r["case_id"] for r in self.cases})
        self.assertEqual(set(author.RESOURCE_IDS), {r["case_id"] for r in self.resources})
        self.assertEqual(136, len(self.cases))
        self.assertEqual(639, len(self.diagnostics))
        self.assertEqual(33, len(self.resources))
        self.assertEqual(9, sum(r["kind"] == "normal" for r in self.cases))
        self.assertEqual(671, sum(len(r["commands"].split("|")) for r in self.cases))
        manifest = json.loads(self.files["manifest.json"])
        self.assertTrue(manifest["languageImplemented"])
        self.assertTrue(manifest["conformanceExecuted"])
        self.assertTrue(manifest["runtimeImplemented"])
        self.assertTrue(manifest["runtimeConformanceExecuted"])

    def test_runtime_cases_cover_planned_ids_states_and_boundaries(self):
        manifest = json.loads(self.files["manifest.json"])
        self.assertEqual(set(manifest["runtimeCaseIds"]),
                         {r["case_id"] for r in self.runtime
                          if r["case_id"].startswith(("RESULT-N", "RESULT-F"))})
        self.assertEqual(set(manifest["runtimeResourceIds"]),
                         {r["case_id"] for r in self.runtime
                          if r["case_id"].startswith("RESULT-R")})
        self.assertEqual({"success", "failure", "both", "four", "-"},
                         {r["state"] for r in self.runtime})
        self.assertEqual({"E_RESULT_STATE_MISMATCH"},
                         {r["code"] for r in self.runtime
                          if r["case_id"] in {"RESULT-F022", "RESULT-F023"}})
        boundaries = {(r["case_id"], r["limit_name"], r["limit"], r["observed"])
                      for r in self.runtime if r["limit_name"] != "-"}
        self.assertIn(("RESULT-R005", "dataStackValues", "65536", "65537"), boundaries)
        self.assertIn(("RESULT-R009", "outputUtf8Bytes", "67108864", "67108865"), boundaries)
        self.assertIn(("RESULT-R010", "jsonWorkUnits", "67108864", "67108865"), boundaries)
        for row in self.runtime:
            self.assertIn(row["outcome"], {"success", "diagnostic"})
            if row["outcome"] == "diagnostic":
                self.assertNotEqual("-", row["code"])

    def test_central_catalog_covers_exactly_all_seventy_ids(self):
        expected = ([f"RESULT-N{number:03d}" for number in range(1, 21)]
                    + [f"RESULT-F{number:03d}" for number in range(1, 37)]
                    + [f"RESULT-R{number:03d}" for number in range(1, 15)])
        self.assertEqual(expected, [row["case_id"] for row in self.central])
        self.assertEqual(20, sum(row["case_id"].startswith("RESULT-N") for row in self.central))
        self.assertEqual(36, sum(row["case_id"].startswith("RESULT-F") for row in self.central))
        self.assertEqual(14, sum(row["case_id"].startswith("RESULT-R") for row in self.central))
        self.assertEqual(["RESULT-F032"],
                         [row["case_id"] for row in self.central if row["kind"] == "warning"])
        for row in self.central:
            self.assertIn(row["evidence"], self.files)
            self.assertIn(row["commands"], {"existing", "public", "internal"})

    def test_central_public_sources_have_all_five_fixed_cli_observations(self):
        public_sources = {row["evidence"] for row in self.central if row["commands"] == "public"}
        self.assertEqual(24, len(public_sources))
        self.assertEqual(120, len(self.command_hashes))
        self.assertEqual(public_sources, {row["source"] for row in self.command_hashes})
        expected_commands = {"check", "checkJson", "run", "format", "explain"}
        for source in public_sources:
            observed = {row["command"] for row in self.command_hashes if row["source"] == source}
            self.assertEqual(expected_commands, observed)
        for row in self.command_hashes:
            self.assertRegex(row["stdout_sha256"], r"^[0-9a-f]{64}$")
            self.assertRegex(row["stderr_sha256"], r"^[0-9a-f]{64}$")
            self.assertGreaterEqual(int(row["stdout_bytes"]), 0)
            self.assertGreaterEqual(int(row["stderr_bytes"]), 0)

    def test_central_fixed_assets_and_integrated_recipe_are_closed(self):
        self.assertEqual(34, len(self.file_hashes))
        for row in self.file_hashes:
            value = self.files[row["path"]]
            self.assertEqual(int(row["bytes"]), len(value), row["path"])
            self.assertEqual(row["sha256"], hashlib.sha256(value).hexdigest(), row["path"])
        recipe = dict(line.split("=", 1) for line in
                      self.files["central/RESULT-R014.properties"].decode().splitlines())
        self.assertEqual({"kind", "depth", "stackWidth", "sharedPayload", "jsonComparisons",
                          "trace", "heapMiB"}, set(recipe))
        self.assertEqual("integratedResult", recipe["kind"])
        self.assertEqual("256", recipe["depth"])
        self.assertEqual("65535", recipe["stackWidth"])
        self.assertEqual("512", recipe["heapMiB"])

    def test_file_inventory_and_sha256(self):
        audit_files(self.files)
        for path, value in self.files.items():
            self.assertFalse(value.startswith(b"\xef\xbb\xbf"), path)
            self.assertNotIn(b"\r", value, path)
            self.assertTrue(value.endswith(b"\n"), path)
            if path.endswith(".json"):
                json.loads(value)

    def test_missing_extra_and_mutated_files_are_rejected(self):
        first = self.cases[0]["source"]
        for bad in (dict(self.files, unwanted=b"x\n"),
                    {k: v for k, v in self.files.items() if k != first},
                    dict(self.files, **{first: self.files[first] + b"\n"})):
            with self.assertRaises(ValueError):
                audit_files(bad)

    def test_duplicate_and_malformed_catalog_rows_are_rejected(self):
        text = self.files["cases.tsv"].decode()
        duplicate = text + text.splitlines()[1] + "\n"
        missing_column = text.replace("\tvariant\t", "\t", 1)
        short_row = text + "RESULT-F001\n"
        for bad in (duplicate, missing_column, short_row):
            with self.assertRaises(ValueError):
                rows(bad, author.CASE_COLUMNS, ["case_id", "variant"])

    def test_case_references_commands_and_outputs(self):
        used_diagnostics = set()
        for case in self.cases:
            with self.subTest(case=case["case_id"], variant=case["variant"]):
                self.assertIn(case["source"], self.files)
                stdout = json.loads(self.files[case["stdout"]])
                stderr = json.loads(self.files[case["stderr"]])
                commands = case["commands"].split("|")
                self.assertEqual(set(commands), set(stdout))
                self.assertEqual(set(commands), set(stderr))
                if case["format_exit"] == "0":
                    self.assertEqual(self.files[case["canonical"]].decode(), stdout["format"])
                    self.assertEqual("", stderr["format"])
                else:
                    self.assertEqual("-", case["canonical"])
                    self.assertEqual("", stdout["format"])
                relevant = [r for r in self.diagnostics if (r["case_id"], r["variant"]) == (case["case_id"], case["variant"])]
                self.assertEqual(int(case["diagnostic_rows"]), len(relevant))
                for command in commands:
                    expected = [r for r in relevant if r["command"] == command]
                    self.assertEqual(list(range(1, len(expected) + 1)), [int(r["occurrence"]) for r in expected])
                    used_diagnostics.update((r["case_id"], r["variant"], r["command"], r["occurrence"]) for r in expected)
                    if command in {"checkJson", "explain"}:
                        self.assertEqual("", stderr[command])
                        document = json.loads(stdout[command])
                        self.assertEqual(1, document["schemaVersion"])
                        self.assertEqual(str(author.DATA / case["source"]), document["source"])
                        self.assertEqual(int(case["check_exit"]), document["exitCode"])
                        self.assertEqual(len(expected), len(document["diagnostics"]))
                        for row, actual in zip(expected, document["diagnostics"]):
                            for key in ("code", "severity", "stage"):
                                self.assertEqual(row[key], actual[key])
                            loc = json.loads(row["span"])
                            for key in ("span", "point"):
                                if key in loc:
                                    self.assertEqual(loc[key], actual[key])
                            self.assertEqual(json.loads(row["fields"]), actual["fields"])
                            self.assertEqual(json.loads(row["fixes"]), actual["fixes"])
                            self.assertEqual(json.loads(row["related"]), actual["relatedLocations"])
                            for key in ("expected", "actual"):
                                self.assertEqual(None if row[key] == "-" else row[key], actual.get(key))
                    elif expected:
                        self.assertTrue(stderr[command])
                        for row in expected:
                            self.assertIn("エラー[" + row["code"] + "]", stderr[command])
                if case["kind"] == "normal":
                    self.assertEqual([], relevant)
                    state = json.loads(self.files[case["state"]])
                    self.assertEqual([], state["dataStack"])
                    self.assertEqual([], state["globals"])
                    self.assertEqual([], state["capabilityEvents"])
                    self.assertEqual("1", case["instructions"])
                    for key in ("output_bytes", "error_output_bytes", "array_construction", "array_work", "json_construction", "json_work"):
                        self.assertEqual("0", case[key])
                else:
                    self.assertEqual("-", case["state"])
        self.assertEqual(len(self.diagnostics), len(used_diagnostics))

    def test_all_type_slots_and_normalized_call_expectations(self):
        all_types = json.loads(self.files["expected/RESULT-N001-all-types.analysis.json"])
        self.assertEqual(all_types["inputs"], all_types["outputs"])
        self.assertEqual(37, len(all_types["inputs"]))
        for typ in author.SCALARS:
            self.assertIn(f"結果<{typ},真偽>", all_types["inputs"])
            self.assertIn(f"結果<整数,{typ}>", all_types["inputs"])
        for typ in author.ARRAY_SCALARS:
            self.assertIn(f"結果<配列<{typ}>,真偽>", all_types["inputs"])
            self.assertIn(f"結果<整数,配列<{typ}>>", all_types["inputs"])
        normalized = self.files["canonical/RESULT-N002-unicode-nested.bsb"].decode()
        self.assertNotIn("ＪＳＯＮ", normalized)
        self.assertIn("任意<結果<JSON,真偽>>", normalized)

    def test_span_utf8_and_utf16_bounds_are_independent(self):
        by_case = {(r["case_id"], r["variant"]): r for r in self.cases}
        for row in self.diagnostics:
            source_bytes = self.files[by_case[(row["case_id"], row["variant"])]["source"]]
            loc = json.loads(row["span"])
            start = loc["point"]["utf8Offset"] if "point" in loc else loc["span"]["utf8Start"]
            end = start if "point" in loc else loc["span"]["utf8EndExclusive"]
            self.assertLessEqual(start, end)
            self.assertLessEqual(end, len(source_bytes))
            self.assertEqual(len(source_bytes[:start].decode().encode("utf-16-le")) // 2, loc["utf16Start"])
            self.assertEqual(len(source_bytes[:end].decode().encode("utf-16-le")) // 2, loc["utf16EndExclusive"])
            prefix = source_bytes[:start].decode()
            line = prefix.count("\n") + 1
            column = 1
            for char in prefix.rsplit("\n", 1)[-1]:
                column += 4 - (column - 1) % 4 if char == "\t" else 1
            observed = loc.get("point", loc.get("span", {}).get("start"))
            self.assertEqual((line, column), (observed["line"], observed["column"]))

    def test_position_oracle_tabs_crlf_supplementary_and_eof(self):
        source = "A\t日\r\n😀X\n"
        self.assertEqual({"line": 1, "column": 5, "utf8Offset": 2}, author.position(source, 2))
        self.assertEqual({"line": 2, "column": 1, "utf8Offset": 7}, author.position(source, 5))
        span = author.location(source, 5, 6)
        self.assertEqual(5, span["utf16Start"])
        self.assertEqual(7, span["utf16EndExclusive"])
        self.assertEqual(11, span["span"]["utf8EndExclusive"])
        self.assertEqual({"line": 3, "column": 1, "utf8Offset": 13}, author.position(source, len(source)))

    def test_generated_recipe_schema_hashes_and_sources(self):
        hashes = rows(self.files["generated/resources.tsv"].decode(),
                      ["generator_key", "input_kind", "sha256", "recipe_sha256"], ["generator_key"])
        self.assertEqual({r["generator_key"] for r in self.resources}, {r["generator_key"] for r in hashes})
        for row in hashes:
            raw = self.files[f'generated/{row["generator_key"]}.properties'].decode()
            recipe = author.read_recipe(raw)
            self.assertIn(recipe["expected"], self.files)
            self.assertEqual(hashlib.sha256(raw.encode()).hexdigest(), row["recipe_sha256"])
            generated = author.generated_input(raw)
            self.assertEqual(hashlib.sha256(generated.encode()).hexdigest(), row["sha256"])
            expected = json.loads(self.files[recipe["expected"]])
            if row["input_kind"] == "source":
                self.assertEqual(row["sha256"], expected["sourceSha256"])
                self.assertEqual(len(generated.encode()), expected["sourceUtf8Bytes"])
                self.assertEqual(len(generated.encode("utf-16-le")) // 2, expected["sourceUtf16Units"])
                if "canonicalSha256" in expected:
                    self.assertEqual(row["sha256"], expected["canonicalSha256"])
            else:
                self.assertEqual("wideType", recipe["kind"])

    def test_invalid_duplicate_unused_and_unsafe_recipe_fields_are_rejected(self):
        original = self.files["generated/RESULT-R001-success-256.properties"].decode()
        for bad in (original + "unused=true\n", original + "depth=256\n",
                    original.replace("depth=256\n", ""), original.replace("kind=typeDepthSource", "kind=unknown"),
                    original.replace("expected=expected/", "expected=../")):
            with self.assertRaises(ValueError):
                author.read_recipe(bad)
        with self.assertRaises(ValueError):
            author.generated_input(original.replace("depth=256", "depth=1000000000"))

    def test_wide_and_token_boundary_arithmetic(self):
        wide = author.generated_input(self.files["generated/RESULT-R004-wide-source.properties"].decode())
        self.assertEqual(2**15 - 1, wide.count("結果<"))
        self.assertEqual(2**15, wide.count("整数"))
        self.assertEqual(163851, small_alphabet_token_count(wide))
        for attempted in (250000, 250001):
            raw = self.files[f"generated/RESULT-R004-tokens-{attempted}.properties"].decode()
            generated = author.generated_input(raw)
            self.assertEqual(attempted, small_alphabet_token_count(generated))
            self.assertEqual(31249, generated.count(","))
        for variant in ("wide-tree", "wide-shared"):
            expected = json.loads(self.files[f"expected/RESULT-R004-{variant}.json"])
            self.assertEqual(32767, expected["expandedConstructors"])
            self.assertEqual(32768, expected["expandedLeaves"])

    def test_both_type_paths_and_inferred_depth_limits(self):
        for resource in self.resources:
            if resource["case_id"] not in {"RESULT-R001", "RESULT-R002"}:
                continue
            raw = self.files[f'generated/{resource["generator_key"]}.properties'].decode()
            recipe = author.read_recipe(raw)
            source = author.generated_input(raw)
            depth, maximum = 0, 0
            for char in source:
                if char == "<":
                    depth += 1
                    maximum = max(maximum, depth)
                elif char == ">":
                    depth -= 1
            self.assertEqual(0, depth)
            expected = json.loads(self.files[recipe["expected"]])
            if resource["case_id"] == "RESULT-R001":
                self.assertEqual(int(resource["observed"]), maximum)
                self.assertEqual(1 if maximum > 256 else 0, len(expected["diagnostics"]))
            else:
                self.assertEqual(expected["inputDepth"], maximum)
                self.assertEqual(maximum + 1, expected["outputDepth"])
                self.assertEqual(0, expected["formatExit"])

    def test_deep_value_payload_and_hash_relations(self):
        for shape in ("success", "failure", "mixed", "inactive-success", "inactive-failure"):
            data = json.loads(self.files[f"expected/RESULT-R003-{shape}.json"])
            self.assertEqual(256, data["typeDepth"])
            depth = 1 if shape.startswith("inactive") else 256
            self.assertEqual(depth, data["display"].count("（"))
            self.assertEqual(depth, data["display"].count("）"))
            self.assertEqual(1, data["display"].count("42"))
            self.assertTrue(data["sameValueEquals"])
            self.assertFalse(data["differentLeafEquals"])
            self.assertTrue(data["equalValueHashCodesMatch"])
            self.assertFalse(data["inactivePayloadAllocated"])
            self.assertFalse(data["javaStackDependent"])

    def test_recovery_order_and_diagnostic_cap(self):
        mixed = [r for r in self.diagnostics if r["case_id"] == "RESULT-F033" and
                 r["variant"] == "missing-ends-optional-result" and r["command"] == "check"]
        self.assertEqual(["E_EXPECTED_RESULT_TYPE_END", "E_EXPECTED_OPTIONAL_TYPE_END"], [r["code"] for r in mixed])
        for count in (99, 100, 101):
            expected = json.loads(self.files[f"expected/RESULT-R013-diagnostics-{count}.json"])
            self.assertEqual(min(count, 100), len(expected["diagnostics"]))
            self.assertEqual(min(count, 99), expected["regularDiagnostics"])
            self.assertEqual(count < 100, expected["followingDefinitionVisited"])
            if count >= 100:
                last = expected["diagnostics"][-1]
                self.assertEqual("E_DIAGNOSTIC_LIMIT", last["code"])
                self.assertEqual("100", last["resourceLimit"]["observed"])


if __name__ == "__main__":
    unittest.main()
