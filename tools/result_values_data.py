#!/usr/bin/env python3
"""Result authoring oracle and materialization auditor.

Only builds explicitly enumerated examples and mathematical boundary recipes.
It never invokes Java, the BSB frontend/formatter/runtime, or a production parser.
--patch emits apply_patch input; --check is read-only. No option writes files.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
DATA = Path("tests/conformance/result-values")
CASE_COLUMNS = "case_id variant kind source canonical stdout stderr commands check_exit run_exit format_exit explain_exit diagnostic_rows state instructions output_bytes error_output_bytes array_construction array_work json_construction json_work target_words".split()
DIAG_COLUMNS = "case_id variant occurrence command code severity stage span fields expected actual fixes related limit_name limit observed".split()
RESOURCE_COLUMNS = "case_id target variant outcome code limit observed generator_key".split()
RUNTIME_COLUMNS = "case_id variant target state result_type payload outcome code limit_name limit observed stack_delta output_bytes array_work json_work trace_policy".split()
SCALARS = ("整数", "真偽", "文字", "文字列", "小数", "丸め方法", "正規表現", "入力結果", "日時", "JSON")
ARRAY_SCALARS = ("整数", "真偽", "文字", "文字列", "小数", "JSON")
NEW_WORDS = ("成功にする", "失敗にする", "結果が成功である", "結果が失敗である", "結果から成功値を取り出す", "結果から失敗値を取り出す", "結果を捨てる")
NORMAL_IDS = ["RESULT-N001", "RESULT-N002"]
FAILURE_IDS = [f"RESULT-F{i:03}" for i in (*range(1, 13), 33, 34, 35)]
RESOURCE_IDS = [f"RESULT-R{i:03}" for i in (1, 2, 3, 4, 13)]
EMPTY_MAIN = "メインとは （--）\nこと。\n"
NEW_MESSAGES = {
    "E_EXPECTED_RESULT_TYPE_ARGUMENT": "結果型の型引数には具体型が必要です。",
    "E_EXPECTED_RESULT_TYPE_SEPARATOR": "成功型と失敗型を区切る「,」が必要です。",
    "E_RESULT_TYPE_ARGUMENT_COUNT": "結果型には成功型と失敗型の2個の型引数が必要です。",
    "E_EXPECTED_RESULT_TYPE_END": "結果型の型引数を閉じる「>」が必要です。",
    "E_RESULT_TYPE_ARGUMENTS_REQUIRED": "結果型とその構築には成功型と失敗型の指定が必要です。",
    "E_TYPE_DEPTH_LIMIT": "推論された型の入れ子が上限を超えています。",
    "E_RESULT_STATE_MISMATCH": "結果値の状態が取り出す側と一致しません。",
    "E_RESULT_PROPAGATION_CONTEXT": "結果の局所伝播は結果値を返す利用者定義単語内でだけ使用できます。",
    "E_RESULT_PROPAGATION_EFFECT_MISMATCH": "結果の局所伝播地点で保持する値または失敗型が単語の宣言出力と一致しません。",
}


def compact(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def pretty(value):
    return json.dumps(value, ensure_ascii=False, indent=2) + "\n"


def sha(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def table(columns, rows):
    return "\n".join("\t".join(str(v) for v in row) for row in [columns, *rows]) + "\n"


def position(source, offset):
    """Independent coordinates for our BMP/ASCII/tab/CRLF fixture alphabet.

    No general grapheme algorithm is claimed: combining marks and ZWJ are not
    generated. Supplementary single code points are supported for oracle tests.
    """
    line, column, index = 1, 1, 0
    while index < offset:
        char = source[index]
        if char == "\r":
            if index + 1 < offset and source[index + 1] == "\n":
                index += 1
            line, column = line + 1, 1
        elif char == "\n":
            line, column = line + 1, 1
        elif char == "\t":
            column += 4 - ((column - 1) % 4)
        else:
            column += 1
        index += 1
    return {"line": line, "column": column, "utf8Offset": len(source[:offset].encode("utf-8"))}


def location(source, start, end=None):
    """Public inclusive span/point plus independently calculated UTF-16 bounds."""
    end = start if end is None else end
    first = position(source, start)
    if start == end:
        result = {"point": first}
    else:
        last = position(source, end - 1)
        result = {"span": {"start": {k: first[k] for k in ("line", "column")},
                           "endInclusive": {k: last[k] for k in ("line", "column")},
                           "utf8Start": first["utf8Offset"],
                           "utf8EndExclusive": len(source[:end].encode("utf-8"))}}
    result["utf16Start"] = len(source[:start].encode("utf-16-le")) // 2
    result["utf16EndExclusive"] = len(source[:end].encode("utf-16-le")) // 2
    return result


def marked(template):
    """Strip fixture-author markers, retaining exact spans without parsing BSB."""
    pieces, marks, cursor, length = [], {}, 0, 0
    for match in re.finditer(r"⟦([A-Za-z0-9_-]+)¦(.*?)⟧", template, re.S):
        prefix = template[cursor:match.start()]
        pieces.extend((prefix, match[2]))
        length += len(prefix)
        assert match[1] not in marks, match[1]
        marks[match[1]] = (length, length + len(match[2]))
        length += len(match[2])
        cursor = match.end()
    pieces.append(template[cursor:])
    source = "".join(pieces)
    if not source.endswith("\n"):
        previous_end = len(source)
        source += "\n"
        marks = {key: (len(source), len(source)) if span == (previous_end, previous_end) else span
                 for key, span in marks.items()}
    return source, marks


def mark(label, text=""):
    return f"⟦{label}¦{text}⟧"


def diag(code, at, *, fields=None, expected=None, actual=None, fixes=(), related=(),
         stage="syntax", point=False, limit=None):
    return dict(code=code, at=at, fields=fields or {}, expected=expected, actual=actual,
                fixes=list(fixes), related=list(related), stage=stage, point=point, limit=limit)


def messages():
    result = {}
    # Normative, checked-in message resources are input, never implementation output.
    groups = (
        "language-core", "control-flow", "bindings", "arrays", "numerics",
        "text-regex", "host-io", "json", "optional-values",
    )
    for group in groups:
        path = ROOT / "tests/conformance" / group / "messages.properties"
        for line in path.read_text().splitlines():
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                result[key] = value
    return result | NEW_MESSAGES


class FixtureSet:
    def __init__(self):
        self.files = {}
        self.cases = []
        self.diagnostics = []
        self.resources = []
        self.hashes = []
        self.groups = {}
        self.message_templates = messages()

    def put(self, path, value, group):
        path = str(path)
        assert path not in self.files, path
        self.files[path] = value
        self.groups[path] = group
        return path

    def resolve(self, specs, source, marks, source_path):
        resolved = []
        for spec in specs:
            start, end = marks[spec["at"]]
            loc = location(source, start, start if spec["point"] else end)
            fields = dict(spec["fields"])
            for key, value in list(fields.items()):
                if isinstance(value, tuple):
                    label, coordinate = value
                    fields[key] = str(position(source, marks[label][0])[coordinate])
            assert all(isinstance(v, str) for v in fields.values())
            related = []
            for value in spec["related"]:
                if isinstance(value, str):
                    related.append({"description": value})
                else:
                    label, description = value
                    related.append({"sourcePath": source_path,
                                    "point": position(source, marks[label][0]),
                                    "description": description})
            formatting = fields | ({"limitName": spec["limit"][0], "limit": str(spec["limit"][1]),
                                    "observed": str(spec["limit"][2])} if spec["limit"] else {})
            message = re.sub(r"\{([A-Za-z][A-Za-z0-9]*)\}",
                             lambda m: formatting[m[1]], self.message_templates[spec["code"]])
            public = {"code": spec["code"], "severity": "error", "stage": spec["stage"],
                      "message": message, "sourcePath": source_path}
            public.update({key: loc[key] for key in ("span", "point") if key in loc})
            public["fields"] = dict(sorted(fields.items()))
            if spec["expected"] is not None:
                public["expected"] = spec["expected"]
            if spec["actual"] is not None:
                public["actual"] = spec["actual"]
            public["relatedLocations"] = related
            public["fixes"] = spec["fixes"]
            if spec["limit"]:
                name, maximum, observed = spec["limit"]
                public["resourceLimit"] = {"name": name, "limit": str(maximum), "observed": str(observed)}
            resolved.append((spec, loc, public))
        return resolved

    @staticmethod
    def human(resolved):
        lines = []
        for spec, loc, public in resolved:
            point = loc.get("point", loc.get("span", {}).get("start"))
            lines.append(f'{public["sourcePath"]}:{point["line"]}:{point["column"]}: エラー[{public["code"]}]: {public["message"]}')
            for key, label in (("expected", "必要"), ("actual", "実際")):
                if key in public:
                    lines.append(f"  {label}: {public[key]}")
            if spec["limit"]:
                name, maximum, observed = spec["limit"]
                lines.extend((f"  上限: {name}={maximum}", f"  観測値: {observed}"))
            for rel in public["relatedLocations"]:
                point = rel.get("point")
                prefix = f'{rel["sourcePath"]}:{point["line"]}:{point["column"]} ' if point else ""
                lines.append(f'  関連位置: {prefix}{rel["description"]}')
            lines.extend(f"  修正候補: {fix}" for fix in public["fixes"])
        return "\n".join(lines) + ("\n" if lines else "")

    def case(self, id_, variant, template, specs=(), canonical=None, targets=(), analysis=None):
        source, marks = marked(template)
        key = f"{id_}-{variant}"
        path = self.put(f"sources/{key}.bsb", source, id_)
        source_path = str(DATA / path)
        resolved = self.resolve(specs, source, marks, source_path)
        exit_code = 9 if any(s["stage"] in ("lexical", "syntax") for s in specs) else 8 if specs else 0
        format_exit = 9 if exit_code == 9 else 0
        canonical_path = "-"
        if format_exit == 0:
            assert canonical is not None, key
            canonical_path = self.put(f"canonical/{key}.bsb", canonical, id_)
        commands = ["check", "checkJson", "run", "format"] + (["explain"] if specs else [])
        public = [r[2] for r in resolved]
        check = {"schemaVersion": 1, "command": "check", "source": source_path,
                 "success": not bool(specs), "exitCode": exit_code, "diagnostics": public}
        stdout = {"check": "", "checkJson": compact(check) + "\n", "run": "",
                  "format": canonical if format_exit == 0 else ""}
        human = self.human(resolved)
        stderr = {"check": human, "checkJson": "", "run": human,
                  "format": human if format_exit else ""}
        if specs:
            explain = dict(check, command="explain")
            explain.update(builtinWords=[], userWords=[], scopes=[], bindings=[])
            stdout["explain"] = compact(explain) + "\n"
            stderr["explain"] = ""
        stdout_path = self.put(f"expected/{key}.stdout.json", pretty(stdout), id_)
        stderr_path = self.put(f"expected/{key}.stderr.json", pretty(stderr), id_)
        state_path = "-"
        if not specs:
            state_path = self.put(f"expected/{key}.state.json", pretty({"dataStack": [], "globals": [],
                                  "programStdout": "", "programStderr": "", "capabilityEvents": []}), id_)
        for command in commands:
            if command == "format" and not format_exit:
                continue
            for occurrence, (spec, loc, public_diag) in enumerate(resolved, 1):
                limits = list(spec["limit"] or ("-", "-", "-"))
                self.diagnostics.append([id_, variant, occurrence, command, spec["code"], "error",
                                         spec["stage"], compact(loc), compact(public_diag["fields"]),
                                         spec["expected"] if spec["expected"] is not None else "-",
                                         spec["actual"] if spec["actual"] is not None else "-",
                                         compact(spec["fixes"]), compact(public_diag["relatedLocations"]), *limits])
        self.cases.append([id_, variant, "failure" if specs else "normal", path, canonical_path,
                           stdout_path, stderr_path, "|".join(commands), exit_code, exit_code, format_exit,
                           exit_code if specs else "-", sum(1 for row in self.diagnostics if row[:2] == [id_, variant]),
                           state_path, *(["-"] * 7 if specs else [1, 0, 0, 0, 0, 0, 0]),
                           "|".join(targets) or "-"])
        if analysis is not None:
            self.put(f"expected/{key}.analysis.json", pretty(analysis), id_)

    def resource(self, id_, variant, recipe, expectation, *, source=None, canonical=None,
                 template=None, specs=(), target="type", outcome="success", code="-", maximum="-", observed="-"):
        key = f"{id_}-{variant}"
        source_path = str(DATA / f"generated/{key}.bsb")
        if template is not None:
            source, marks = marked(template)
            resolved = self.resolve(specs, source, marks, source_path)
            expectation["diagnostics"] = [dict(public, fixtureLocation=loc) for _, loc, public in resolved]
        if source is not None:
            expectation.update(sourceSha256=sha(source), sourceUtf8Bytes=len(source.encode()),
                               sourceUtf16Units=len(source.encode("utf-16-le")) // 2)
        if canonical is not None:
            expectation.update(canonicalSha256=sha(canonical), canonicalUtf8Bytes=len(canonical.encode()))
        expected_path = self.put(f"expected/{key}.json", pretty(expectation), id_)
        recipe = dict(recipe, expected=expected_path)
        recipe_text = "".join(f"{k}={str(v).lower() if isinstance(v, bool) else v}\n" for k, v in recipe.items())
        recipe_path = self.put(f"generated/{key}.properties", recipe_text, id_)
        self.resources.append([id_, target, variant, outcome, code, maximum, observed, key])
        self.hashes.append([key, "source" if source is not None else "recipe",
                            sha(source if source is not None else recipe_text), sha(recipe_text)])
        return recipe_path


def signature(type_text, *, name="受け渡す", output=None):
    output = type_text if output is None else output
    return f"{name}とは （{type_text} -- {output}）\nこと。\n\n" + EMPTY_MAIN


def type_site(owner, arguments, *, eof=False):
    token = mark("owner", owner) + arguments
    if owner == "結果":
        return "調べるとは （" + token + ("" if eof else " --）\nこと。\n\n" + EMPTY_MAIN)
    return "メインとは （--）\n    " + token + ("" if eof else "\nこと。\n")


def result_syntax(owner, kind, actual, *, index=None, count=None):
    fields = {"typeConstructor": "結果", "owner": owner}
    if owner != "結果":
        fields["word"] = owner
    expected, fix = {
        "ARGUMENT": ("具体型", "成功型と失敗型に具体型を指定してください"),
        "SEPARATOR": (",", "成功型と失敗型をASCIIの,で区切ってください"),
        "COUNT": ("2個の型引数", "成功型と失敗型を1個ずつ指定してください"),
        "END": (">", "型引数の末尾へ>を追加してください"),
    }[kind]
    code = {"ARGUMENT": "E_EXPECTED_RESULT_TYPE_ARGUMENT", "SEPARATOR": "E_EXPECTED_RESULT_TYPE_SEPARATOR",
            "COUNT": "E_RESULT_TYPE_ARGUMENT_COUNT", "END": "E_EXPECTED_RESULT_TYPE_END"}[kind]
    related = []
    if index is not None:
        fields["argumentIndex"] = str(index)
    if count is not None:
        fields.update(expectedCount="2", actualCount=str(count))
    if kind == "END":
        fields.update(startLine=("owner", "line"), startColumn=("owner", "column"))
        related = [("owner", owner)]
    return diag(code, "bad", fields=fields, expected=expected, actual=actual, fixes=[fix], related=related, point=True)


def build_normal(data):
    types = [f"結果<{t},真偽>" for t in SCALARS] + [f"結果<整数,{t}>" for t in SCALARS]
    types += [f"結果<配列<{t}>,真偽>" for t in ARRAY_SCALARS]
    types += [f"結果<整数,配列<{t}>>" for t in ARRAY_SCALARS]
    types += ["結果<任意<JSON>,文字列>", "結果<整数,任意<JSON>>", "任意<結果<整数,文字列>>",
              "結果<結果<整数,真偽>,結果<文字列,JSON>>", "結果<任意<結果<整数,真偽>>,任意<文字列>>"]
    text = signature(" ".join(types), name="全型")
    data.case("RESULT-N001", "all-types", text, canonical=text,
              analysis={"word": "全型", "inputs": types, "outputs": types, "returnsNormally": True})
    examples = [
        ("type-whitespace", "受け渡すとは ( 結果 < 整数 ,\n\t文字列 > -- 結果<整数,文字列> )\nこと 。\n\n" + EMPTY_MAIN,
         signature("結果<整数,文字列>"), (), {"types": ["結果<整数,文字列>"]}),
        ("unicode-nested", "受け渡すとは （ 任意 < 結果 < ＪＳＯＮ , 真偽 >> -- 任意<結果<JSON,真偽>> ）\nこと。\n\n" + EMPTY_MAIN,
         signature("任意<結果<JSON,真偽>>"), (), {"types": ["任意<結果<JSON,真偽>>"]}),
        ("success-call", "包むとは （整数 -- 結果<整数,文字列>）\n\t成功にする\n < 整数 , 文字列 >\nこと。\n\n" + EMPTY_MAIN,
         "包むとは （整数 -- 結果<整数,文字列>）\n    成功にする<整数,文字列>\nこと。\n\n" + EMPTY_MAIN,
         ("成功にする",), {"calls": [{"name": "成功にする", "inputs": ["整数"], "outputs": ["結果<整数,文字列>"]}]}),
        ("failure-call", "包むとは （文字列 -- 結果<整数,文字列>）\n失敗にする< 整数,\t文字列>\nこと。\n\n" + EMPTY_MAIN,
         "包むとは （文字列 -- 結果<整数,文字列>）\n    失敗にする<整数,文字列>\nこと。\n\n" + EMPTY_MAIN,
         ("失敗にする",), {"calls": [{"name": "失敗にする", "inputs": ["文字列"], "outputs": ["結果<整数,文字列>"]}]}),
        ("local-initializer", "作るとは （-- 結果<整数,文字列>）\n保存は 定数 42 を 成功にする <整数,文字列>。\n保存\nこと。\n\n" + EMPTY_MAIN,
         "作るとは （-- 結果<整数,文字列>）\n    保存は 定数 42 を 成功にする<整数,文字列>。\n    保存\nこと。\n\n" + EMPTY_MAIN,
         ("成功にする",), {"bindings": [{"name": "保存", "type": "結果<整数,文字列>", "storage": "local"}]}),
        ("nested-call", "包むとは （結果<整数,真偽> -- 結果<結果<整数,真偽>,任意<JSON>>）\n成功にする < 結果<整数,真偽>,任意<ＪＳＯＮ>>\nこと。\n\n" + EMPTY_MAIN,
         "包むとは （結果<整数,真偽> -- 結果<結果<整数,真偽>,任意<JSON>>）\n    成功にする<結果<整数,真偽>,任意<JSON>>\nこと。\n\n" + EMPTY_MAIN,
         ("成功にする",), {"calls": [{"name": "成功にする", "inputs": ["結果<整数,真偽>"], "outputs": ["結果<結果<整数,真偽>,任意<JSON>>"]}]}),
        ("control-block", "選ぶとは （真偽 -- 結果<整数,文字列>）\nならば 42 を 成功にする <整数,文字列>\nさもなければ 「不一致」を 失敗にする <整数,文字列>\nつぎに\nこと。\n\n" + EMPTY_MAIN,
         "選ぶとは （真偽 -- 結果<整数,文字列>）\n    ならば\n        42 を 成功にする<整数,文字列>\n    さもなければ\n        「不一致」 を 失敗にする<整数,文字列>\n    つぎに\nこと。\n\n" + EMPTY_MAIN,
         ("成功にする", "失敗にする"), {"branchJoin": ["結果<整数,文字列>"]}),
        ("array-element", "作るとは （-- 配列<整数>）\n【42 を 成功にする <整数,真偽> 結果から成功値を取り出す】\nこと。\n\n" + EMPTY_MAIN,
         "作るとは （-- 配列<整数>）\n    【42 を 成功にする<整数,真偽> 結果から成功値を取り出す】\nこと。\n\n" + EMPTY_MAIN,
         ("成功にする", "結果から成功値を取り出す"), {"arrayElementTypes": ["整数"]}),
    ]
    for variant, source, canonical, targets, analysis in examples:
        data.case("RESULT-N002", variant, source, canonical=canonical, targets=targets, analysis=analysis)


def build_failures(data):
    source = "調べるとは （" + mark("bad", "結果") + " --）\nこと。\n\n" + EMPTY_MAIN
    data.case("RESULT-F001", "bare-type", source, [diag("E_RESULT_TYPE_ARGUMENTS_REQUIRED", "bad",
              fields={"typeConstructor": "結果", "owner": "結果", "context": "スタック効果"},
              expected="結果<具体型,具体型>", actual="結果", fixes=["結果<整数,文字列>のように成功型と失敗型を指定してください"], stage="typeAndStack")],
              canonical=marked(source)[0])
    shapes = [
        (2, "empty", "<" + mark("bad", ">"), "ARGUMENT", ">", dict(index=1), False),
        (2, "missing-first", "<" + mark("bad", ",") + "文字列>", "ARGUMENT", ",", dict(index=1), False),
        (3, "space", "<整数 " + mark("bad", "文字列") + ">", "SEPARATOR", "文字列", {}, False),
        (3, "ideographic-comma", "<整数、" + mark("bad", "文字列") + ">", "SEPARATOR", "文字列", {}, False),
        (3, "fullwidth-comma", "<整数，" + mark("bad", "文字列") + ">", "SEPARATOR", "文字列", {}, False),
        (4, "one", "<整数" + mark("bad", ">"), "COUNT", "1個の型引数", dict(count=1), False),
        (5, "missing-second", "<整数," + mark("bad", ">"), "ARGUMENT", ">", dict(index=2), False),
        (5, "second-eof", "<整数," + mark("bad"), "ARGUMENT", "EOF", dict(index=2), True),
        (6, "three", "<整数,文字列" + mark("bad", ",") + "真偽>", "COUNT", "3個以上の型引数", dict(count=3), False),
        (6, "trailing-comma", "<整数,文字列" + mark("bad", ",") + ">", "COUNT", "3個以上の型引数", dict(count=3), False),
        (7, "end-eof", "<整数,文字列" + mark("bad"), "END", "EOF", {}, True),
        (7, "one-eof", "<整数" + mark("bad"), "END", "EOF", {}, True),
        (7, "first-eof", "<" + mark("bad"), "ARGUMENT", "EOF", dict(index=1), True),
    ]
    for owner, tag in (("結果", "type"), ("成功にする", "success"), ("失敗にする", "failure")):
        for number, variant, args, kind, actual, extra, eof in shapes:
            data.case(f"RESULT-F{number:03}", f"{tag}-{variant}", type_site(owner, args, eof=eof),
                      [result_syntax(owner, kind, actual, **extra)], targets=() if tag == "type" else (owner,))
        for id_, pairs in [
            (8, [(t, "文字列") for t in ("T", "E", "数値")] + [("整数", t) for t in ("T", "E", "数値")] + [("T", "E"), ("T", "T")]),
            (9, [("未知成功型", "文字列"), ("整数", "未知失敗型"), ("未知成功型", "未知失敗型"), ("未知型", "未知型")]),
            (10, [("JSONオブジェクト", "文字列"), ("整数", "JSONオブジェクト")]),
        ]:
            for n, (left, right) in enumerate(pairs, 1):
                args = "<" + mark("left", left) + "," + mark("right", right) + ">"
                source = type_site(owner, args)
                specs = []
                for label, typ in (("left", left), ("right", right)):
                    if typ in ("整数", "文字列"):
                        continue
                    if id_ == 8:
                        numeric = typ == "数値"
                        specs.append(diag("E_TYPE_CONSTRAINT_NOT_ALLOWED", label, stage="typeAndStack",
                            fields={"type": typ, "context": "利用者定義スタック効果"} if numeric else {},
                            expected="具体型" if numeric else "整数・真偽・文字・文字列", actual=typ,
                            fixes=["整数または小数の具体型を書いてください" if numeric else "具体的な言語コアの型へ変更してください"]))
                    elif id_ == 9:
                        specs.append(diag("E_UNKNOWN_TYPE", label, stage="typeAndStack", fields={"typeName": typ},
                            expected="整数・真偽・文字・文字列", actual=typ, fixes=["言語コアの型へ変更してください"]))
                    else:
                        specs.append(diag("E_FEATURE_NOT_AVAILABLE", label, stage="name",
                            fields={"feature": typ, "plannedFeature": "将来予約"}, expected="言語コアの型", actual=typ,
                            fixes=["現在利用できる機能へ変更してください"]))
                canonical = marked(source)[0]
                data.case(f"RESULT-F{id_:03}", f"{tag}-{n:02}", source, specs, canonical=canonical,
                          targets=() if tag == "type" else (owner,))
    for variant, typ in (
        ("array-annotation", "結果<配列<配列<整数>>,文字列>"),
        ("array-element-success", "結果<配列<配列<文字列>>,文字列>"),
        ("array-element-failure", "結果<整数,配列<配列<文字列>>>")
    ):
        source = "調べるとは （配列<" + mark("bad", typ) + "> --）\nこと。\n\n" + EMPTY_MAIN
        data.case("RESULT-F011", variant, source, [diag("E_NESTED_ARRAY_NOT_AVAILABLE", "bad", stage="typeAndStack",
                  fields={"context": "配列型", "maximumDimensions": "2", "actualDimensions": "3"},
                  expected="最大2次元の配列型", actual=typ,
                  fixes=["配列型の入れ子を2段までにしてください"])], canonical=marked(source)[0])
    for owner, value, tag in (("成功にする", "42", "success"), ("失敗にする", "「失敗」", "failure")):
        for present in (False, True):
            prefix = value + " を " if present else ""
            source = "メインとは （--）\n    " + prefix + mark("bad", owner) + "\nこと。\n"
            data.case("RESULT-F012", f"{tag}-{'input' if present else 'empty'}", source,
                      [diag("E_RESULT_TYPE_ARGUMENTS_REQUIRED", "bad", stage="typeAndStack",
                        fields={"typeConstructor": "結果", "owner": owner, "context": "構築呼出し", "word": owner},
                        expected=owner + "<具体型,具体型>", actual=owner,
                        fixes=[owner + "<整数,文字列>のように成功型と失敗型を指定してください"])],
                      canonical=marked(source)[0], targets=(owner,))
    build_recovery(data)
    build_names(data)


def build_recovery(data):
    for wrapper, code in (("任意", "E_EXPECTED_OPTIONAL_ELEMENT_TYPE"), ("配列", "E_EXPECTED_ARRAY_ELEMENT_TYPE")):
        source = "調べるとは （結果<" + wrapper + "<" + mark("bad", ">") + ",文字列> --）\nこと。\n\n" + EMPTY_MAIN
        spec = diag(code, "bad", point=True, fields={"typeConstructor": "任意"} if wrapper == "任意" else {"allowedTypes": "整数,真偽,文字,文字列"},
                    expected="具体型" if wrapper == "任意" else "要素型", actual=">",
                    fixes=["任意<JSON>のように具体型を追加してください" if wrapper == "任意" else "4種類の要素型から1つ追加してください"])
        data.case("RESULT-F033", f"result-empty-{'optional' if wrapper == '任意' else 'array'}", source, [spec])
    for wrapper, tag in (("任意", "optional"), ("配列", "array")):
        source = "調べるとは （" + wrapper + "<" + mark("owner", "結果") + "<" + mark("bad", ">") + "> --）\nこと。\n\n" + EMPTY_MAIN
        data.case("RESULT-F033", f"{tag}-empty-result", source, [result_syntax("結果", "ARGUMENT", ">", index=1)])
    for order in ("result-optional", "optional-result", "result-result"):
        outer = "任意" if order == "optional-result" else "結果"
        inner = "任意" if order == "result-optional" else "結果"
        source = "調べるとは （" + mark("outer", outer) + ("<" if outer == "任意" else "<整数,")
        source += mark("inner", inner) + ("<文字列" if inner == "任意" else "<整数,文字列") + mark("bad")
        specs = []
        for label, owner in (("inner", inner), ("outer", outer)):
            fields = {"typeConstructor": owner, "startLine": (label, "line"), "startColumn": (label, "column")}
            if owner == "結果":
                fields["owner"] = owner
            specs.append(diag("E_EXPECTED_OPTIONAL_TYPE_END" if owner == "任意" else "E_EXPECTED_RESULT_TYPE_END", "bad",
                              fields=fields, point=True, expected=">", actual="EOF", fixes=["型引数の末尾へ>を追加してください"], related=[(label, owner)]))
        data.case("RESULT-F033", f"missing-ends-{order}", source, specs)
    source = "壊れたとは （" + mark("owner", "結果") + "<" + mark("bad", ">") + " --）\nこと。\n\n"
    source += "後続とは （任意<" + mark("later", ">") + " --）\nこと。\n\n" + EMPTY_MAIN
    data.case("RESULT-F033", "next-definition", source,
              [result_syntax("結果", "ARGUMENT", ">", index=1),
               diag("E_EXPECTED_OPTIONAL_ELEMENT_TYPE", "later", point=True, fields={"typeConstructor": "任意"}, expected="具体型", actual=">", fixes=["任意<JSON>のように具体型を追加してください"])])
    # Comma remains illegal outside a result frame, including a nested single-argument frame.
    for variant, source in (
        ("comma-body", "メインとは （--）\n    " + mark("bad", ",") + "\nこと。\n"),
        ("comma-initializer", "保存は 定数 1 " + mark("bad", ",") + "。\n\n" + EMPTY_MAIN),
        ("comma-type", "調べるとは （整数 " + mark("bad", ",") + " --）\nこと。\n\n" + EMPTY_MAIN),
        ("comma-nested-optional", "調べるとは （結果<任意<整数 " + mark("bad", ",") + "真偽>,文字列> --）\nこと。\n\n" + EMPTY_MAIN),
    ):
        data.case("RESULT-F034", variant, source, [diag("E_UNEXPECTED_CHARACTER", "bad", stage="lexical", fields={"codePoint": "U+002C"},
                  expected="BSBの構文で使用できる文字", actual="U+002C", fixes=["この文字を削除してください"])])
    for owner, tag in (("結果", "type"), ("成功にする", "success"), ("失敗にする", "failure")):
        args = "<整数," + mark("bad", "# 型内") + "\n文字列>"
        data.case("RESULT-F034", f"comment-{tag}", type_site(owner, args),
                  [diag("E_COMMENT_NOT_ALLOWED", "bad", expected="型引数", actual="# 型内", fixes=["コメントを次の行へ移してください"])],
                  targets=() if owner == "結果" else (owner,))
        if owner != "結果":
            source = "メインとは （--）\n    " + owner + " " + mark("bad", "# 構築名の後") + "\n<整数,文字列>\nこと。\n"
            data.case("RESULT-F034", f"comment-before-{tag}", source,
                      [diag("E_COMMENT_NOT_ALLOWED", "bad", expected="型引数開始の<", actual="# 構築名の後", fixes=["コメントを次の行へ移してください"])], targets=(owner,))
    for context in ("body", "initializer"):
        if context == "body":
            source = "メインとは （--）\n    任意にする" + mark("bad", "<") + "整数>\nこと。\n"
            spec = diag("E_EXPECTED_WORD_END", "bad", fields={"word": "メイン"}, expected="こと。", actual="<", fixes=["ここにこと。を追加してください"])
        else:
            source = "保存は 定数 1 任意にする" + mark("bad", "<") + "整数" + mark("end", ">") + "。\n\n" + EMPTY_MAIN
            spec = diag("E_INITIALIZER_ELEMENT_NOT_ALLOWED", "bad", fields={"element": "<"}, expected="初期値で許可された要素", actual="<", fixes=["この要素を宣言の外へ移動してください"])
        specs = [spec]
        if context == "initializer":
            specs.append(diag("E_INITIALIZER_ELEMENT_NOT_ALLOWED", "end", fields={"element": ">"}, expected="初期値で許可された要素", actual=">", fixes=["この要素を宣言の外へ移動してください"]))
        data.case("RESULT-F034", f"unsupported-type-call-{context}", source, specs)


def build_names(data):
    for index, name in enumerate(("結果", "E", *NEW_WORDS), 1):
        for context in ("word", "binding"):
            source = mark("bad", name) + ("とは （--）\nこと。\n\n" if context == "word" else "は 定数 1。\n\n") + EMPTY_MAIN
            related = ["組み込み単語 " + name] if name in NEW_WORDS else []
            data.case("RESULT-F035", f"{context}-{index:02}", source,
                      [diag("E_RESERVED_NAME", "bad", stage="name", expected="利用者定義名", actual=name,
                            fixes=["別の名前に変更してください"], related=related)], canonical=marked(source)[0])
    source = "メインとは （--）\n    " + mark("bad", "結果") + "\nこと。\n"
    data.case("RESULT-F035", "type-not-callable", source,
              [diag("E_NAME_NOT_CALLABLE", "bad", stage="name", fields={"word": "結果", "kind": "型名"},
                    expected="呼び出し可能な単語", actual="型名 結果", fixes=["単語名を指定してください"])], canonical=marked(source)[0])


def chain(depth, shape="success", leaf="整数", marker_depth=None):
    """Construct a known unary path through a binary type, never parse a type."""
    current = leaf
    for level in range(depth, 0, -1):
        optional = shape == "optional" or shape == "mixed" and level % 2 == 0
        name = "任意" if optional else "結果"
        if level == marker_depth:
            name = mark("bad", name)
        if optional:
            current = name + "<" + current + ">"
        elif shape == "failure":
            current = name + "<真偽," + current + ">"
        else:
            current = name + "<" + current + ",真偽>"
    return current


def depth_diagnostic():
    return diag("E_SYNTAX_DEPTH_LIMIT", "bad", expected="256段以下", actual="257段",
                fixes=["型構築子の入れ子を減らしてください"], limit=("syntaxDepth", 256, 257))


def build_resources(data):
    for shape in ("success", "failure", "mixed"):
        for depth in (256, 257):
            typ = chain(depth, shape, marker_depth=257 if depth == 257 else None)
            source = signature(typ, output="")
            # Empty output avoids a second independent occurrence of an invalid deep type.
            if depth == 256:
                source = signature(typ)
            data.resource("RESULT-R001", f"{shape}-{depth}", {"kind": "typeDepthSource", "shape": shape, "depth": depth},
                          {"checkExit": 0 if depth == 256 else 9, "formatExit": 0 if depth == 256 else 9,
                           "maximumPathDepth": depth, "diagnostics": []}, template=source,
                          specs=[] if depth == 256 else [depth_diagnostic()], canonical=source if depth == 256 else None,
                          outcome="success" if depth == 256 else "diagnostic", code="-" if depth == 256 else "E_SYNTAX_DEPTH_LIMIT", maximum=256, observed=depth)
    for owner, tag in (("成功にする", "success"), ("失敗にする", "failure")):
        for side in ("left", "right"):
            for depth in (256, 257):
                inner = chain(depth - 1, "success", marker_depth=256 if depth == 257 else None)
                left, right = (inner, "整数") if side == "left" else ("整数", inner)
                # The selected input type is inferred from a prior declared word only for the
                # accepted form; rejected source needs no valid stack effect.
                selected = left if tag == "success" else right
                prefix = f"包むとは （{selected} -- 結果<{left},{right}>）\n    " if depth == 256 else "メインとは （--）\n    "
                source = prefix + owner + f"<{left},{right}>\nこと。\n" + ("\n" + EMPTY_MAIN if depth == 256 else "")
                data.resource("RESULT-R001", f"construct-{tag}-{side}-{depth}",
                              {"kind": "constructorDepthSource", "owner": owner, "deepSide": side, "depth": depth},
                              {"checkExit": 0 if depth == 256 else 9, "formatExit": 0 if depth == 256 else 9,
                               "maximumPathDepth": depth, "selectedSide": "left" if tag == "success" else "right", "diagnostics": []},
                              template=source, specs=[] if depth == 256 else [depth_diagnostic()], canonical=source if depth == 256 else None,
                              outcome="success" if depth == 256 else "diagnostic", code="-" if depth == 256 else "E_SYNTAX_DEPTH_LIMIT", maximum=256, observed=depth)
    for shape in ("optional", "mixed"):
        for depth in (255, 256):
            typ = chain(depth, shape)
            source = f"包むとは （{typ} --）\n    " + mark("bad", "任意にする") + "\n    任意を捨てる\nこと。\n\n" + EMPTY_MAIN
            specs = [] if depth == 255 else [diag("E_TYPE_DEPTH_LIMIT", "bad", stage="typeAndStack",
                fields={"word": "任意にする", "typeConstructor": "任意"}, expected="型構築子の深さ256以下", actual="型構築子の深さ257",
                fixes=["型の入れ子を浅くしてください"], limit=("typeDepth", 256, 257))]
            data.resource("RESULT-R002", f"{shape}-{depth}", {"kind": "inferredDepthSource", "shape": shape, "inputDepth": depth},
                          {"checkExit": 0 if depth == 255 else 8, "formatExit": 0, "inputDepth": depth, "outputDepth": depth + 1, "diagnostics": []},
                          template=source, specs=specs, canonical=marked(source)[0], outcome="success" if depth == 255 else "diagnostic",
                          code="-" if depth == 255 else "E_TYPE_DEPTH_LIMIT", maximum=256, observed=depth + 1)
    for shape in ("success", "failure", "mixed", "inactive-success", "inactive-failure"):
        if shape.startswith("inactive"):
            deep = chain(255)
            typ = f"結果<整数,{deep}>" if shape == "inactive-success" else f"結果<{deep},整数>"
            state = "成功" if shape == "inactive-success" else "失敗"
            display, trace = f"{state}（42）", f"{state}(42)"
            value_depth = 1
        else:
            typ = chain(256, shape)
            wrappers = ["ある" if shape == "mixed" and i % 2 == 0 else "失敗" if shape == "failure" else "成功" for i in range(1, 257)]
            display = "（".join(wrappers) + "（42" + "）" * 256
            trace = "(".join(wrappers) + "(42" + ")" * 256
            value_depth = 256
        data.resource("RESULT-R003", shape, {"kind": "deepValue", "shape": shape, "typeDepth": 256, "leaf": 42, "differentLeaf": 43},
                      {"typeName": typ, "typeDepth": 256, "valueDepth": value_depth, "display": display, "traceValue": typ + ":" + trace,
                       "sameTypeEquals": True, "sameValueEquals": True, "differentLeafEquals": False, "equalValueHashCodesMatch": True,
                       "displayable": True, "equalityComparable": True, "defaultReveal": True,
                       "inactivePayloadAllocated": False, "arrayConstruction": 0, "arrayWork": 0, "jsonConstruction": 0, "jsonWork": 0,
                       "javaStackDependent": False, "checkExit": 0, "formatExit": 0, "diagnostics": []},
                      source=signature(typ), canonical=signature(typ), target="type-and-value", maximum=256, observed=256)
    for shared in (False, True):
        typ = "整数"
        for _ in range(15):
            typ = f"結果<{typ},{typ}>"
        data.resource("RESULT-R004", "wide-shared" if shared else "wide-tree", {"kind": "wideType", "depth": 15, "shareChildren": shared},
                      {"maximumPathDepth": 15, "expandedConstructors": 32767, "expandedLeaves": 32768,
                       "typeNameSha256": sha(typ), "typeNameUtf8Bytes": len(typ.encode()),
                       "sameTypeEquals": True, "equalTypeHashCodesMatch": True,
                       "publicSourceGeneratorKey": "RESULT-R004-wide-source"},
                      target="type-model", maximum=15, observed=15)
    # One deep-wide annotation, not duplicated in the output, with a typed drop body.
    wide_source = "捨てるとは （" + typ + " --）\n    結果を捨てる\nこと。\n\n" + EMPTY_MAIN
    data.resource("RESULT-R004", "wide-source", {"kind": "wideTypeSource", "depth": 15},
                  {"checkExit": 0, "formatExit": 0, "maximumPathDepth": 15, "constructors": 32767, "leaves": 32768,
                   "typeNameSha256": sha(typ), "typeNameUtf8Bytes": len(typ.encode()), "diagnostics": []},
                  source=wide_source, canonical=wide_source, maximum=250000, observed=163851)
    for total in (250000, 250001):
        source = "メインとは （--）\n" + "    42 成功にする<整数,真偽> 結果を捨てる\n" * 31249
        source += "    改行する\n" * (total - 249999) + "こと" + (mark("bad", "。") if total > 250000 else "。") + "\n"
        specs = [] if total == 250000 else [diag("E_TOKEN_LIMIT", "bad", stage="lexical", expected="250000トークン以下", actual="250001トークン目", limit=("tokens", 250000, 250001))]
        data.resource("RESULT-R004", f"tokens-{total}", {"kind": "tokenBoundarySource", "repetitions": 31249, "newlines": total - 249999},
                      {"lexicalSuccess": total == 250000, "countedTokens": min(total, 250000), "attemptedTokens": total, "commaTokens": 31249, "diagnostics": []},
                      template=source, specs=specs, target="lexer", outcome="success" if total == 250000 else "diagnostic",
                      code="-" if total == 250000 else "E_TOKEN_LIMIT", maximum=250000, observed=total)
    for count in (99, 100, 101):
        pieces, specs = [], []
        for i in range(count):
            label = f"bad{i}"
            pieces.append(f"検査{i}とは （結果<" + mark(label, ">") + " --）\nこと。\n")
            if i < 99:
                spec = result_syntax("結果", "ARGUMENT", ">", index=1)
                spec["at"] = label
                specs.append(spec)
            elif i == 99:
                specs.append(diag("E_DIAGNOSTIC_LIMIT", label, point=True, limit=("diagnostics", 100, 100)))
        source = "\n".join(pieces) + "\n" + EMPTY_MAIN
        data.resource("RESULT-R013", f"diagnostics-{count}", {"kind": "diagnosticLimitSource", "invalidDefinitions": count},
                      {"checkExit": 9, "regularDiagnostics": min(count, 99), "limitDiagnostics": int(count >= 100),
                       "totalDiagnostics": min(count, 100), "followingDefinition": "メイン",
                       "followingDefinitionVisited": count < 100, "diagnostics": []},
                      template=source, specs=specs, target="diagnostic-collector", outcome="diagnostic",
                      code="E_EXPECTED_RESULT_TYPE_ARGUMENT" if count == 99 else "E_DIAGNOSTIC_LIMIT", maximum=100, observed=min(count, 100))
    # Explicit recovery and comma accounting with small, independently countable sources.
    source = "検査とは （結果<任意<" + mark("bad", ">") + ",文字列> --）\nこと。\n\n" + EMPTY_MAIN
    data.resource("RESULT-R013", "recover-comma", {"kind": "recoverySource", "scenario": "empty-optional"},
                  {"checkExit": 9, "commaTokens": 1, "diagnosticCount": 1, "followingDefinition": "メイン", "diagnostics": []},
                  template=source, specs=[diag("E_EXPECTED_OPTIONAL_ELEMENT_TYPE", "bad", point=True, fields={"typeConstructor": "任意"},
                    expected="具体型", actual=">", fixes=["任意<JSON>のように具体型を追加してください"])], target="parser", outcome="diagnostic", code="E_EXPECTED_OPTIONAL_ELEMENT_TYPE")
    source = "検査とは （" + "任意<" * 256 + mark("bad", "結果") + "<>" + ">" * 256 + " --）\nこと。\n\n" + EMPTY_MAIN
    data.resource("RESULT-R013", "depth-before-empty", {"kind": "recoverySource", "scenario": "depth-before-empty"},
                  {"checkExit": 9, "diagnosticCount": 1, "followingDefinition": "メイン", "diagnostics": []},
                  template=source, specs=[depth_diagnostic()], target="parser", outcome="diagnostic", code="E_SYNTAX_DEPTH_LIMIT", maximum=256, observed=257)


def build():
    data = FixtureSet()
    build_normal(data)
    build_failures(data)
    build_resources(data)
    data.cases.sort(key=lambda r: (r[0], r[1]))
    data.diagnostics.sort(key=lambda r: (r[0], r[1], ("check", "checkJson", "run", "format", "explain").index(r[3]), r[2]))
    data.resources.sort(key=lambda r: (r[0], r[2]))
    data.hashes.sort()
    data.put("cases.tsv", table(CASE_COLUMNS, data.cases), "catalog")
    data.put("diagnostics.tsv", table(DIAG_COLUMNS, data.diagnostics), "catalog")
    data.put("resources.tsv", table(RESOURCE_COLUMNS, data.resources), "catalog")
    data.put("generated/resources.tsv", table(["generator_key", "input_kind", "sha256", "recipe_sha256"], data.hashes), "catalog")
    data.put("messages.properties", "".join(f"{k}={v}\n" for k, v in NEW_MESSAGES.items()), "catalog")
    data.put("runtime/runtime-cases.tsv", runtime_cases(), "runtime")
    manifest = {"formatVersion": 1, "scope": "type-and-runtime-data", "baselineCommit": "89f7639",
                "languageImplemented": True, "conformanceExecuted": True,
                "runtimeImplemented": True, "runtimeConformanceExecuted": True,
                "caseIds": NORMAL_IDS + FAILURE_IDS, "resourceIds": RESOURCE_IDS,
                "variants": [{"caseId": row[0], "variant": row[1], "commands": row[7].split("|")} for row in data.cases],
                "resourceVariants": [{"caseId": row[0], "variant": row[2], "generatorKey": row[7]} for row in data.resources],
                "analysisExpectations": [p for p in data.files if p.endswith(".analysis.json")],
                "runtimeCaseIds": [f"RESULT-N{i:03}" for i in range(3, 17)] + ["RESULT-N018", "RESULT-N019", "RESULT-F022", "RESULT-F023", "RESULT-F036"],
                "runtimeResourceIds": [f"RESULT-R{i:03}" for i in range(5, 13)],
                "deferred": {"normalExplain": "RESULT-N017 / central", "languageHarness": "all 70 IDs / central central harness",
                             "remainingCentralIds": "RESULT-N003..020, RESULT-F013..032/036, RESULT-R005..012/014"},
                "files": {p: sha(t) for p, t in sorted(data.files.items())}}
    data.put("manifest.json", pretty(manifest), "catalog")
    return data


def runtime_cases():
    rows = []

    def add(case_id, variant, target, state="-", result_type="-", payload="-", outcome="success",
            code="-", limit_name="-", limit="-", observed="-", stack_delta="-", output_bytes="-",
            array_work="-", json_work="-", trace_policy="-"):
        rows.append([case_id, variant, target, state, result_type, payload, outcome, code,
                     limit_name, limit, observed, stack_delta, output_bytes, array_work,
                     json_work, trace_policy])

    result = "結果<整数,文字列>"
    add("RESULT-N003", "success-integer-string", "construction", "success", result, "integer:42", stack_delta=0)
    add("RESULT-N003", "success-same-types", "construction", "success", "結果<整数,整数>", "integer:42", stack_delta=0)
    add("RESULT-N004", "failure-integer-string", "construction", "failure", result, "string:not-found", stack_delta=0)
    add("RESULT-N004", "failure-json", "construction", "failure", "結果<整数,JSON>", "json:null", stack_delta=0, json_work=0)
    for case_id, target in (("RESULT-N005", "successPredicate"), ("RESULT-N006", "failurePredicate")):
        for state in ("success", "failure"):
            add(case_id, state, target, state, result, "selected", stack_delta=1)
    add("RESULT-N007", "success", "unwrap", "success", result, "integer:42", stack_delta=0)
    add("RESULT-N007", "failure", "unwrap", "failure", result, "string:not-found", stack_delta=0)
    add("RESULT-N008", "success", "drop", "success", result, "opaque", stack_delta=-1, array_work=0, json_work=0)
    add("RESULT-N008", "failure", "drop", "failure", result, "opaque", stack_delta=-1, array_work=0, json_work=0)
    for case_id, target in (("RESULT-N009", "storage"), ("RESULT-N010", "userWord"), ("RESULT-N011", "control")):
        add(case_id, "both-states", target, "both", result, "shared", stack_delta=0)
    for variant, state, payload in (("same", "success", "integer:1"), ("different-state", "both", "integer:1"), ("json", "success", "json:null")):
        add("RESULT-N012", variant, "equality", state, result if variant != "json" else "結果<JSON,JSON>", payload, stack_delta=-1, json_work=2 if variant == "json" else 0)
    add("RESULT-N013", "success-display", "display", "success", result, "integer:1", stack_delta=-1, output_bytes=13)
    add("RESULT-N013", "failure-line", "displayLine", "failure", result, "string:x", stack_delta=-1, output_bytes=14)
    add("RESULT-N014", "opaque-both-sides", "opaquePayload", "both", "結果<正規表現,日時>", "shared", stack_delta=0)
    add("RESULT-N015", "optional-json-four-states", "composition", "four", "結果<任意<JSON>,文字列>", "shared", stack_delta=0)
    add("RESULT-N016", "default-redaction", "trace", "both", result, "secret", trace_policy="result-values")
    add("RESULT-N016", "host-rejection", "trace", "success", "結果<整数,真偽>", "integer:42", trace_policy="reject-selected")
    add("RESULT-N018", "initializer-and-array-expression", "pureContext", "success", result, "integer:42", stack_delta=0)
    add("RESULT-N019", "failure-is-normal", "execution", "failure", result, "string:not-found", outcome="success", code="-")
    add("RESULT-F022", "failure-success-unwrap", "unwrap", "failure", result, "secret", outcome="diagnostic", code="E_RESULT_STATE_MISMATCH", stack_delta=0)
    add("RESULT-F023", "success-failure-unwrap", "unwrap", "success", result, "secret", outcome="diagnostic", code="E_RESULT_STATE_MISMATCH", stack_delta=0)
    add("RESULT-F036", "json-syntax-not-captured", "existingFailure", "-", "-", "invalid-json", outcome="diagnostic", code="E_JSON_SYNTAX")
    add("RESULT-F036", "json-kind-not-captured", "existingFailure", "success", "結果<JSON,文字列>", "json:null", outcome="diagnostic", code="E_JSON_KIND_MISMATCH")

    for predicate in ("successPredicate", "failurePredicate"):
        add("RESULT-R005", predicate + "-exact", "dataStack", "success", result, "integer:1", limit_name="dataStackValues", limit=65536, observed=65536, stack_delta=1)
        add("RESULT-R005", predicate + "-over", "dataStack", "success", result, "integer:1", outcome="diagnostic", code="E_DATA_STACK_LIMIT", limit_name="dataStackValues", limit=65536, observed=65537, stack_delta=0)
    for case_id, target, limit_name, maximum in (("RESULT-R006", "globals", "globalBindings", 10000), ("RESULT-R007", "locals", "localBindingsPerWord", 1024)):
        add(case_id, "exact", target, "both", result, "alternating", limit_name=limit_name, limit=maximum, observed=maximum)
        add(case_id, "over", target, "both", result, "alternating", outcome="diagnostic", code="E_GLOBAL_BINDING_LIMIT" if case_id == "RESULT-R006" else "E_LOCAL_BINDING_LIMIT", limit_name=limit_name, limit=maximum, observed=maximum + 1)
    add("RESULT-R008", "instruction-exact", "instruction", "both", result, "shared", limit_name="executedInstructions", limit=10000000, observed=10000000)
    add("RESULT-R008", "instruction-over", "instruction", "both", result, "shared", outcome="diagnostic", code="E_INSTRUCTION_LIMIT", limit_name="executedInstructions", limit=10000000, observed=10000001)
    add("RESULT-R008", "wrapper-no-construction", "budget", "both", result, "json-or-array", array_work=0, json_work=0)
    for channel, limit_name, code in (("stdout", "outputUtf8Bytes", "E_OUTPUT_LIMIT"),
                                      ("stderr", "errorOutputUtf8Bytes", "E_ERROR_OUTPUT_LIMIT")):
        add("RESULT-R009", channel + "-exact", channel, "success", result, "integer:1", limit_name=limit_name, limit=67108864, observed=67108864, output_bytes=13)
        add("RESULT-R009", channel + "-over", channel, "success", result, "integer:1", outcome="diagnostic", code=code, limit_name=limit_name, limit=67108864, observed=67108865, stack_delta=0, output_bytes=0)
    add("RESULT-R010", "json-selected", "jsonWork", "success", "結果<JSON,文字列>", "json:null", limit_name="jsonWorkUnits", limit=67108864, observed=67108864, json_work=2)
    add("RESULT-R010", "json-over", "jsonWork", "success", "結果<JSON,文字列>", "json:null", outcome="diagnostic", code="E_JSON_WORK_LIMIT", limit_name="jsonWorkUnits", limit=67108864, observed=67108865, stack_delta=0)
    add("RESULT-R010", "different-state", "jsonWork", "both", "結果<JSON,JSON>", "json:null", json_work=0)
    add("RESULT-R010", "array-selected", "arrayWork", "success", "結果<配列<整数>,真偽>", "array:1", limit_name="arrayElementOperationUnits", limit=10000000, observed=10000000, array_work=1)
    add("RESULT-R010", "array-over", "arrayWork", "success", "結果<配列<整数>,真偽>", "array:1", outcome="diagnostic", code="E_ARRAY_ELEMENT_OPERATION_LIMIT", limit_name="arrayElementOperationUnits", limit=10000000, observed=10000001, stack_delta=0)
    for variant, code in (("success-unwrap", "E_RESULT_STATE_MISMATCH"),
                          ("failure-unwrap", "E_RESULT_STATE_MISMATCH"),
                          ("predicate-capacity", "E_DATA_STACK_LIMIT"),
                          ("display-budget", "E_OUTPUT_LIMIT"),
                          ("equality-budget", "E_JSON_WORK_LIMIT")):
        add("RESULT-R011", variant, "atomicity", "both", result, "secret", outcome="diagnostic", code=code, stack_delta=0, output_bytes=0)
    for variant, observed in (("terminal-32", 32), ("terminal-33", 33), ("array-8", 8), ("array-9", 9), ("text-element-16", 16), ("text-element-17", 17)):
        result_type = "結果<配列<整数>,真偽>" if "array" in variant else "結果<文字列,真偽>"
        add("RESULT-R012", variant, "trace", "success", result_type, "preview", limit_name="tracePreview", limit=32 if "terminal" in variant else 8 if "array" in variant else 16, observed=observed, trace_policy="reveal-test")
    add("RESULT-R012", "inactive-secret", "trace", "success", result, "integer:42", trace_policy="result-values-redacted")
    add("RESULT-R012", "host-reject", "trace", "success", "結果<整数,真偽>", "integer:42", trace_policy="reject-selected")
    return table(RUNTIME_COLUMNS, rows)


RECIPE_FIELDS = {
    "typeDepthSource": {"shape", "depth"},
    "constructorDepthSource": {"owner", "deepSide", "depth"},
    "inferredDepthSource": {"shape", "inputDepth"},
    "deepValue": {"shape", "typeDepth", "leaf", "differentLeaf"},
    "wideType": {"depth", "shareChildren"},
    "wideTypeSource": {"depth"},
    "tokenBoundarySource": {"repetitions", "newlines"},
    "diagnosticLimitSource": {"invalidDefinitions"},
    "recoverySource": {"scenario"},
}


def read_recipe(text):
    values = {}
    for line in text.splitlines():
        key, sep, value = line.partition("=")
        if not sep or key in values:
            raise ValueError("invalid or duplicate recipe field: " + key)
        values[key] = value
    kind = values.get("kind")
    if kind not in RECIPE_FIELDS or set(values) != RECIPE_FIELDS[kind] | {"kind", "expected"}:
        raise ValueError("unknown, missing or unused recipe fields")
    expected = Path(values["expected"])
    if expected.is_absolute() or ".." in expected.parts or expected.parts[0] != "expected":
        raise ValueError("expected resource must remain under expected/")
    return values


def generated_input(text):
    """Consume a checked-in recipe, independently of build()'s case dispatch."""
    recipe = read_recipe(text)
    kind = recipe["kind"]
    if kind == "typeDepthSource":
        depth = int(recipe["depth"])
        if recipe["shape"] not in {"success", "failure", "mixed"} or depth not in (256, 257):
            raise ValueError("invalid depth source recipe")
        typ = chain(depth, recipe["shape"])
        return signature(typ, output="" if depth == 257 else typ)
    if kind == "constructorDepthSource":
        depth = int(recipe["depth"])
        if depth not in (256, 257) or recipe["deepSide"] not in {"left", "right"} or recipe["owner"] not in NEW_WORDS[:2]:
            raise ValueError("invalid constructor depth recipe")
        inner = chain(depth - 1)
        left, right = (inner, "整数") if recipe["deepSide"] == "left" else ("整数", inner)
        selected = left if recipe["owner"] == "成功にする" else right
        prefix = f"包むとは （{selected} -- 結果<{left},{right}>）\n    " if depth == 256 else "メインとは （--）\n    "
        return prefix + recipe["owner"] + f"<{left},{right}>\nこと。\n" + ("\n" + EMPTY_MAIN if depth == 256 else "")
    if kind == "inferredDepthSource":
        depth = int(recipe["inputDepth"])
        if depth not in (255, 256) or recipe["shape"] not in {"optional", "mixed"}:
            raise ValueError("invalid inferred depth recipe")
        return f"包むとは （{chain(depth, recipe['shape'])} --）\n    任意にする\n    任意を捨てる\nこと。\n\n" + EMPTY_MAIN
    if kind == "deepValue":
        if (recipe["typeDepth"], recipe["leaf"], recipe["differentLeaf"]) != ("256", "42", "43"):
            raise ValueError("invalid deep value limits")
        shape = recipe["shape"]
        if shape in {"success", "failure", "mixed"}:
            typ = chain(256, shape)
        elif shape == "inactive-success":
            typ = "結果<整数," + chain(255) + ">"
        elif shape == "inactive-failure":
            typ = "結果<" + chain(255) + ",整数>"
        else:
            raise ValueError("invalid deep value shape")
        return signature(typ)
    if kind in {"wideType", "wideTypeSource"}:
        if recipe["depth"] != "15":
            raise ValueError("invalid wide type depth")
        if kind == "wideType":
            if recipe["shareChildren"] not in {"true", "false"}:
                raise ValueError("invalid sharing flag")
            return text
        typ = "整数"
        for _ in range(15):
            typ = f"結果<{typ},{typ}>"
        return "捨てるとは （" + typ + " --）\n    結果を捨てる\nこと。\n\n" + EMPTY_MAIN
    if kind == "tokenBoundarySource":
        if recipe["repetitions"] != "31249" or recipe["newlines"] not in {"1", "2"}:
            raise ValueError("invalid token boundary")
        return "メインとは （--）\n" + "    42 成功にする<整数,真偽> 結果を捨てる\n" * 31249 + "    改行する\n" * int(recipe["newlines"]) + "こと。\n"
    if kind == "diagnosticLimitSource":
        count = int(recipe["invalidDefinitions"])
        if count not in (99, 100, 101):
            raise ValueError("invalid diagnostic boundary")
        return "\n".join(f"検査{i}とは （結果<> --）\nこと。\n" for i in range(count)) + "\n" + EMPTY_MAIN
    if recipe["scenario"] == "empty-optional":
        return "検査とは （結果<任意<>,文字列> --）\nこと。\n\n" + EMPTY_MAIN
    if recipe["scenario"] == "depth-before-empty":
        return "検査とは （" + "任意<" * 256 + "結果<>" + ">" * 256 + " --）\nこと。\n\n" + EMPTY_MAIN
    raise ValueError("unknown recovery scenario")


def check(data):
    """Strict byte-level materialization check; deliberately not language conformance."""
    problems = []
    directory = ROOT / DATA
    actual = {p.relative_to(directory).as_posix() for p in directory.rglob("*") if p.is_file()}
    # Large public CLI and trace artifacts are recorded by independent SHA-256
    # instead of being embedded in this authoring oracle.
    central_hashes = {}
    central_catalog = directory / "central/file-hashes.tsv"
    if central_catalog.exists():
        lines = central_catalog.read_text(encoding="utf-8").splitlines()
        if not lines or lines[0] != "path\tsha256\tbytes":
            problems.append("invalid: central/file-hashes.tsv header")
        for line in lines[1:]:
            fields = line.split("\t")
            if len(fields) != 3 or fields[0] in central_hashes:
                problems.append("invalid: central/file-hashes.tsv row")
                continue
            central_hashes[fields[0]] = (fields[1], fields[2])
        for path, (expected_hash, expected_bytes) in central_hashes.items():
            file = directory / path
            if not file.is_file():
                problems.append(f"missing: {path}")
            elif sha(file.read_text(encoding="utf-8")) != expected_hash:
                problems.append(f"mismatch: {path}")
            elif len(file.read_bytes()) != int(expected_bytes):
                problems.append(f"size mismatch: {path}")
    # README and the hash catalog are deliberately not generated here.
    allowed = set(data.files) | set(central_hashes) | {"README.md", "central/file-hashes.tsv"}
    extra = actual - allowed
    problems.extend(f"unused: {p}" for p in sorted(extra))
    for path, expected in data.files.items():
        file = directory / path
        if not file.exists():
            problems.append(f"missing: {path}")
        elif file.read_bytes() != expected.encode("utf-8"):
            problems.append(f"mismatch: {path}")
    ids = {r[0] for r in data.cases}
    assert ids == set(NORMAL_IDS + FAILURE_IDS)
    assert len({tuple(r[:2]) for r in data.cases}) == len(data.cases)
    assert len({(r[0], r[2]) for r in data.resources}) == len(data.resources)
    assert {r[0] for r in data.resources} == set(RESOURCE_IDS)
    assert all(len(r) == 22 for r in data.cases)
    assert all(len(r) == 16 for r in data.diagnostics)
    assert all(len(r) == 8 for r in data.resources)
    for key, kind, expected_hash, recipe_hash in data.hashes:
        recipe_text = data.files[f"generated/{key}.properties"]
        assert sha(recipe_text) == recipe_hash, key
        assert sha(generated_input(recipe_text)) == expected_hash, key
    print(pretty({"dataValid": not problems, "languageConformanceExecuted": True,
                  "caseIds": len(ids), "variants": len(data.cases), "diagnosticRows": len(data.diagnostics),
                  "resourceIds": len(RESOURCE_IDS), "resourceVariants": len(data.resources),
                  "plannedCliInvocations": sum(len(r[7].split("|")) for r in data.cases),
                  "files": len(data.files), "problems": problems}), end="")
    return int(bool(problems))


def emit_patch(data, group):
    print("*** Begin Patch")
    for path, value in data.files.items():
        if data.groups[path] != group:
            continue
        target = ROOT / DATA / path
        if target.exists():
            previous = target.read_bytes().decode("utf-8")
            if previous == value:
                continue
            print(f"*** Update File: {target}\n@@")
            for line in previous.splitlines():
                print("-" + line)
        else:
            print(f"*** Add File: {target}")
        for line in value.splitlines():
            print("+" + line)
        if value and not value.endswith("\n"):
            # apply_patch creates LF-terminated text files. EOF fixtures deliberately
            # omit a word terminator, but can still end in LF; author them that way.
            raise ValueError(f"fixture must end in LF: {path}")
    print("*** End Patch")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true")
    mode.add_argument(
        "--patch", choices=NORMAL_IDS + FAILURE_IDS + RESOURCE_IDS + ["runtime", "catalog"])
    mode.add_argument("--input", metavar="GENERATOR_KEY", help="Read a materialized recipe and print its source/model recipe")
    args = parser.parse_args()
    if args.input:
        if not re.fullmatch(r"RESULT-R\d{3}-[a-z0-9-]+", args.input):
            parser.error("invalid generator key")
        sys.stdout.write(generated_input((ROOT / DATA / f"generated/{args.input}.properties").read_text()))
        return 0
    data = build()
    if args.check:
        return check(data)
    emit_patch(data, args.patch)
    return 0


if __name__ == "__main__":
    sys.exit(main())
