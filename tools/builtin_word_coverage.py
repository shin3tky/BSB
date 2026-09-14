#!/usr/bin/env python3
"""BSB 組み込み語 x N/F/R 網羅マトリクス生成ツール。

目的
----
`jp.bsb.stdlib.BuiltinDictionary`(全組み込み語の単一の信頼できる情報源)に登録されている
組み込み語1つひとつについて、次のどこで実際に踏まれているかを機械的に集計し、人間および
コーディングエージェントが客観的に参照できるレポートを出力する。姉妹ツール
`tools/diagnostic_coverage.py`（診断コード側の網羅マトリクス）と対になる、単語側の網羅マトリクス。

  - N(正常例): `tests/conformance/**/sources|canonical|central` 配下の `N*.bsb` や `chapter/*.bsb` の
    ような、成功する例で実際に呼び出されているか
  - F(失敗例): 誤った使い方をする失敗例で踏まれているか。`diagnostics.tsv` の `fields`／
    `fields_json`列の`word`、または同じ意味を持つ`target_lexeme`列で特定される場合(structured)と、`F*.bsb`
    ソース中に単語として出現するだけの場合(text)を区別する
  - R(資源境界): 資源境界の生成記述・資源オラクル関連ファイルに出現するか（多くの語には該当する
    専用境界が存在しないため、他の2つに比べて出現しないのが正常であることに注意）
  - unit(単体テスト): 上記いずれの適合フィクスチャにも出現しないが、`src/test/java` のJUnit
    テストソース中に単語として出現するか（宣言的フィクスチャより弱い証跡として区別する）

組み込み語一覧の取得方法（重要）
--------------------------------
組み込み語の正規名・別名・機能グループは、Javaソース（`BuiltinDictionary.java`）を直接構文解析するのでは
なく、実際にビルドした処理系の `explain --json` が返す `builtinWords` を正とする。理由は、
`BuiltinDictionary.java` の記述が `numeric(...)`, `display(...)` のようなファクトリメソッド経由の箇所
と `new BuiltinWord(...)` を直接呼ぶ箇所が混在しており、`featureGroup` 等をテキスト上で正確に
再構成するのは信頼性に欠けるため。`explain --json` は「現行処理系の全組み込み語を、使用の有無に
かかわらず辞書の仕様順で返す」契約になっており（`docs/spec/explain-json.md`）、常に完全な一覧が
得られる。

入手方法は2通り:
  1. `--jar` で指定した（または `build/libs/*.jar` から自動検出した）fat jar を `--java`
     （既定は PATH 上の `java`）で実行し、最小の合格プログラムに対して `explain --json` を実行して
     `builtinWords` を取り出す。**プロジェクトの要求どおりJDK 25で実行できる`java`が必要。**
  2. `--words-json` で、あらかじめ保存しておいた `explain --json` の出力全体（または
     `builtinWords` 配列そのもの）を指すJSONファイルを渡す。ローカルに互換jarが無い環境
     （例: JDK 25を用意できないサンドボックス）でも、他所で1回取得しておいた結果を使い回せる。

使い方
------
    python3 tools/builtin_word_coverage.py                                  # jarを自動検出して実行
    python3 tools/builtin_word_coverage.py --jar build/libs/foo-all.jar
    python3 tools/builtin_word_coverage.py --words-json tools/reports/words.json
    python3 tools/builtin_word_coverage.py --format markdown --output tools/reports/builtin-word-coverage.md
    python3 tools/builtin_word_coverage.py --strict     # UNCOVEREDが1件でもあればexit 1

分類（status）
--------------
  FULL       適合フィクスチャでN・Fいずれの証跡もある
  N_ONLY     適合フィクスチャでNの証跡はあるが、Fの証跡が無い
  F_ONLY     適合フィクスチャでFの証跡はあるが、Nの証跡が無い（珍しいが理論上あり得る）
  UNIT_ONLY  適合フィクスチャ(N・F)には出現しないが、単体テストソースには出現する
  UNCOVERED  適合フィクスチャにも単体テストソースにも一切出現しない。最重要ギャップ。

Rは上記statusには含めない別軸として報告する。理由は次のとおり: `tests/conformance/*/resources.tsv`・
`generated/*.properties` は「データスタック上限」「文字列UTF-8上限」のような構造的資源境界を対象と
しており、生成記述は `generator=array-literal` のような抽象的なレシピであって特定の単語名を
literalに含まないことが大半である。したがって「R証跡が無い」ことは大多数の語にとって正常であり、
欠陥のシグナルではない。R証跡は「テキスト中に単語名がたまたま出現したか」という緩い一致に基づく
参考情報として別掲する。

証跡の粒度（F）
----------------
  structured: `diagnostics.tsv` の`fields`／`fields_json`列の`word`、または`target_lexeme`列から取れる証跡。
              最も精度が高い（どの診断コードでその語が問題になったかまで分かる）。
  text       : `sources/F*.bsb`・`canonical/F*.bsb`・`central/F*.bsb` に語のトークンとして
               出現するだけの証跡。
              構造化証跡より弱いが、実際の失敗例プログラム内で使われている事実は示す。

ヒューリスティックの限界（重要）
--------------------------------
  - BSBソース(`tests/conformance/**`)の走査は簡易トークナイザ（コメント`#`・文字列「...」/"..."・
    文字'...'をおおまかに読み飛ばし、空白・全角空白・改行・読点・句点・括弧・山括弧で区切る）によるもので、
    BSB処理系本体の字句解析器と完全には一致しない。トークンの完全一致で判定するため、「表示する」と
    「一行表示する」のような部分文字列関係にある語を取り違えることはない。
  - `src/test/java` の走査は簡易トークナイザではなく、Unicode対応の`\b`(単語境界)付き正規表現に
    よる直接検索。Pythonの`\b`は連続した漢字・かな文字を1つの「語」とみなすため、「一行表示する」の
    内部にある「表示する」を誤って拾うことは無い（実測で確認済み）。ただしJavaのコメントや
    文字列リテラルの境界は判定しないため、コメント中の言及も「出現」として拾う可能性がある
    （unit tierが宣言的フィクスチャより弱い証跡として区別されているのはこのため）。
  - ファイル名の先頭が`N`または`F`で始まるかどうかでカテゴリを判定する（例: `ARRAY-N001.bsb`,
    `EXP-F-source-scale.bsb`）。`cli-json`・`explain-json`配下の`CJ-*`・`EJ-*`はこの規則に一致しない
    ため、意図的にN/Fどちらにも算入しない（診断コード側の網羅は`diagnostic_coverage.py`が担当）。
    同様に`explain-json`の`expected/*.json`は「全組み込み語のメタデータ一覧」を含むため、これを
    証跡として使うと全語が実質的に常にヒットしてしまう。したがって`expected/*.json`は本ツールの
    走査対象に含めていない。
  - `messages.properties` は前ツールと同様に常に除外する（語の使用有無とは無関係なため）。
  - 本ツールは「テストが存在するか」を測るものであり、「そのテストが語の意味論を十分に検証して
    いるか」（テストの質）までは保証しない。
"""

from __future__ import annotations

import argparse
import csv
import datetime
import json
import re
import subprocess
import sys
import tempfile
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

STATUS_ORDER = {"UNCOVERED": 0, "UNIT_ONLY": 1, "F_ONLY": 2, "N_ONLY": 3, "FULL": 4}

PROBE_PROGRAM = "メインとは （--）\nこと。\n"

# BSBの区切り文字: 空白、改行、読点、句点、丸括弧、静的引数の山括弧。
DELIMITERS = set(" \t　\n\r、，。（）()<>")


def find_repo_root(explicit: str | None) -> Path:
    if explicit:
        root = Path(explicit).resolve()
    else:
        root = Path(__file__).resolve().parent.parent
    if (
        not (root / "docs" / "spec").is_dir()
        or not (root / "tests" / "conformance").is_dir()
    ):
        raise SystemExit(
            f"リポジトリルートを特定できませんでした: {root}\n"
            "docs/spec と tests/conformance が見つかりません。--root で明示してください。"
        )
    return root


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        try:
            return path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            return ""


# ---------------------------------------------------------------------------
# 組み込み語カタログの取得
# ---------------------------------------------------------------------------


def find_default_jar(root: Path) -> Path | None:
    libs = root / "build" / "libs"
    if not libs.is_dir():
        return None
    candidates = sorted(libs.glob("*.jar"))
    all_jars = [p for p in candidates if "-all" in p.name]
    if all_jars:
        return all_jars[0]
    return candidates[0] if candidates else None


def load_words_via_jar(java_bin: str, jar_path: Path) -> list[dict]:
    with tempfile.TemporaryDirectory() as tmp:
        probe = Path(tmp) / "probe.bsb"
        probe.write_text(PROBE_PROGRAM, encoding="utf-8")
        try:
            proc = subprocess.run(
                [java_bin, "-jar", str(jar_path), "explain", "--json", str(probe)],
                capture_output=True,
                text=True,
                timeout=60,
            )
        except FileNotFoundError as exc:
            raise SystemExit(
                f"java実行ファイルを起動できませんでした: {java_bin}\n"
                "--java でJDK 25対応のjavaを指定してください。"
            ) from exc
        except subprocess.TimeoutExpired as exc:
            raise SystemExit(
                "explain --json の実行がタイムアウトしました（60秒）。"
            ) from exc

        stderr = proc.stderr or ""
        if "UnsupportedClassVersionError" in stderr:
            raise SystemExit(
                "指定したjavaのバージョンがjarのクラスファイルバージョンより古いです。\n"
                "本プロジェクトはJDK 25でビルドされています。--java でJDK 25対応のjavaを指定するか、\n"
                "--words-json で別途取得済みのbuiltinWords JSONを渡してください。\n"
                f"stderr: {stderr.strip()[:500]}"
            )
        if proc.returncode != 0 or not proc.stdout.strip():
            raise SystemExit(
                f"explain --json の実行に失敗しました（exit={proc.returncode}）。\n"
                f"stdout: {proc.stdout.strip()[:500]}\nstderr: {stderr.strip()[:500]}"
            )
        try:
            payload = json.loads(proc.stdout)
        except json.JSONDecodeError as exc:
            raise SystemExit(
                f"explain --json の出力をJSONとして解釈できませんでした: {exc}"
            ) from exc

        if not payload.get("success"):
            raise SystemExit(
                "最小プローブプログラムの静的検査が失敗しました（本来失敗しないはずです）。\n"
                f"diagnostics: {json.dumps(payload.get('diagnostics'), ensure_ascii=False)[:1000]}"
            )
        words = payload.get("builtinWords")
        if not words:
            raise SystemExit(
                "explain --json の出力に builtinWords がありませんでした。"
            )
        return words


def load_words_from_file(path: Path) -> list[dict]:
    if not path.is_file():
        raise SystemExit(f"--words-json で指定したファイルが見つかりません: {path}")
    try:
        payload = json.loads(read_text(path))
    except json.JSONDecodeError as exc:
        raise SystemExit(f"{path} をJSONとして解釈できませんでした: {exc}") from exc
    if isinstance(payload, list):
        return payload
    if isinstance(payload, dict) and "builtinWords" in payload:
        return payload["builtinWords"]
    raise SystemExit(
        f"{path} から builtinWords を取り出せませんでした。"
        "explain --jsonの出力全体、またはbuiltinWords配列そのものを渡してください。"
    )


@dataclass
class WordEntry:
    canonical_name: str
    aliases: list[str]
    feature_group: str
    description: str
    example: str

    @property
    def name_variants(self) -> list[str]:
        return [self.canonical_name] + list(self.aliases)


def normalize_catalog(raw_words: list[dict]) -> list[WordEntry]:
    entries = []
    for w in raw_words:
        entries.append(
            WordEntry(
                canonical_name=w["name"],
                aliases=list(w.get("aliases", [])),
                feature_group=str(w.get("featureGroup", "?")),
                description=w.get("description", ""),
                example=w.get("example", ""),
            )
        )
    return entries


# ---------------------------------------------------------------------------
# BSBソースの簡易トークナイザ（tests/conformance用）
# ---------------------------------------------------------------------------


def tokenize_bsb(text: str) -> set[str]:
    """BSBソース(またはそれに準じるテキスト)を大まかにトークン化する。

    コメント(`#`から行末)と文字列・文字リテラル(「」/""/'')の中身は読み飛ばし、
    それ以外の非空白連続を1トークンとして返す。BSB処理系本体の字句解析器の代用ではなく、
    「この語が呼び出し位置に literal に出現するか」を判定するための近似実装。
    """
    tokens: set[str] = set()
    buf: list[str] = []
    i = 0
    n = len(text)

    def flush():
        if buf:
            tokens.add("".join(buf))
            buf.clear()

    while i < n:
        ch = text[i]
        if ch == "#":
            nl = text.find("\n", i)
            i = n if nl == -1 else nl
            continue
        if ch in ("「", '"', "'"):
            flush()
            closer = "」" if ch == "「" else ch
            j = i + 1
            while j < n:
                if text[j] == "\\" and j + 1 < n:
                    j += 2
                    continue
                if text[j] == closer:
                    j += 1
                    break
                j += 1
            i = j
            continue
        if ch in DELIMITERS:
            flush()
            i += 1
            continue
        buf.append(ch)
        i += 1
    flush()
    return tokens


# ---------------------------------------------------------------------------
# tests/conformance の走査
# ---------------------------------------------------------------------------

CATEGORY_PREFIX = re.compile(r"^(?:[A-Za-z]+-)?([NF])")


def classify_bsb_prefix(stem: str) -> str | None:
    m = CATEGORY_PREFIX.match(stem)
    if not m:
        return None
    prefix = m.group(1)
    if prefix == "N":
        return "N"
    if prefix == "F":
        return "F"
    return None


def parse_diagnostics_fields(raw: str) -> dict[str, str]:
    """`name=value;name=value` 形式（\\, \\;, \\= でエスケープ）を辞書へ変換する。"""
    if raw in ("", "-"):
        return {}
    result: dict[str, str] = {}
    for part in split_unescaped(raw, ";"):
        if not part:
            continue
        name, sep, value = part.partition("=")
        if not sep:
            continue
        result[unescape_field(name)] = unescape_field(value)
    return result


def split_unescaped(text: str, sep: str) -> list[str]:
    parts: list[str] = []
    buf: list[str] = []
    i = 0
    n = len(text)
    while i < n:
        ch = text[i]
        if ch == "\\" and i + 1 < n:
            buf.append(text[i : i + 2])
            i += 2
            continue
        if ch == sep:
            parts.append("".join(buf))
            buf = []
            i += 1
            continue
        buf.append(ch)
        i += 1
    parts.append("".join(buf))
    return parts


def unescape_field(value: str) -> str:
    return value.replace("\\;", ";").replace("\\=", "=").replace("\\\\", "\\")


@dataclass
class ScanResult:
    n_tokens: dict[str, set[str]] = field(default_factory=lambda: defaultdict(set))
    f_text_tokens: dict[str, set[str]] = field(default_factory=lambda: defaultdict(set))
    f_structured: dict[str, set[str]] = field(default_factory=lambda: defaultdict(set))
    r_tokens: dict[str, set[str]] = field(default_factory=lambda: defaultdict(set))
    warnings: list[str] = field(default_factory=list)


def scan_conformance(root: Path) -> ScanResult:
    result = ScanResult()
    base = root / "tests" / "conformance"
    if not base.is_dir():
        result.warnings.append(f"{base} が見つかりません。")
        return result

    for path in base.rglob("*"):
        if not path.is_file():
            continue
        if path.name == "messages.properties":
            continue
        rel = str(path.relative_to(root))
        parts = path.parts

        if path.suffix == ".bsb" and "chapter" in parts:
            for tok in tokenize_bsb(read_text(path)):
                result.n_tokens[tok].add(rel)
            continue

        if path.suffix == ".bsb" and (
            "sources" in parts or "canonical" in parts or "central" in parts
        ):
            category = classify_bsb_prefix(path.stem)
            if category == "N":
                for tok in tokenize_bsb(read_text(path)):
                    result.n_tokens[tok].add(rel)
            elif category == "F":
                for tok in tokenize_bsb(read_text(path)):
                    result.f_text_tokens[tok].add(rel)
            continue

        if path.name == "diagnostics.tsv":
            group = parts[2] if len(parts) > 2 and parts[1] == "conformance" else "?"
            try:
                with path.open(encoding="utf-8", newline="") as f:
                    reader = csv.DictReader(f, delimiter="\t")
                    if reader.fieldnames is None:
                        result.warnings.append(f"{rel}: ヘッダーが見つかりません。")
                        continue
                    fields_col = "fields" if "fields" in reader.fieldnames else None
                    fields_json_col = (
                        "fields_json" if "fields_json" in reader.fieldnames else None
                    )
                    target_col = (
                        "target_lexeme" if "target_lexeme" in reader.fieldnames else None
                    )
                    if fields_col is None and fields_json_col is None and target_col is None:
                        result.warnings.append(
                            f"{rel}: 'fields'、'fields_json'または'target_lexeme'列が見つかりません。"
                        )
                        continue
                    code_col = "code" if "code" in reader.fieldnames else None
                    for row in reader:
                        if fields_col is not None:
                            fields = parse_diagnostics_fields(row.get(fields_col, ""))
                        elif fields_json_col is not None:
                            try:
                                parsed_fields = json.loads(row.get(fields_json_col, ""))
                                fields = parsed_fields if isinstance(parsed_fields, dict) else {}
                            except json.JSONDecodeError:
                                fields = {}
                        else:
                            fields = {}
                        word = fields.get("word") or (
                            row.get(target_col, "") if target_col is not None else ""
                        )
                        if word:
                            code = row.get(code_col, "?") if code_col else "?"
                            result.f_structured[word].add(f"{group}:{code} ({rel})")
            except (OSError, csv.Error) as exc:
                result.warnings.append(f"{rel}: 読み込みに失敗しました ({exc})")
            continue

        if path.name == "resources.tsv" or "generated" in parts:
            for tok in tokenize_bsb(read_text(path)):
                result.r_tokens[tok].add(rel)
            continue

        # cases.properties, unicode-data.properties, literals.tsv, decimal-text.tsv,
        # cli-json/explain-json 配下の expected/*.json（全語のメタデータを含むため除外）、
        # chapter/*.stdout, chapter/*.trace.tsv 等は対象外。

    return result


# ---------------------------------------------------------------------------
# src/test/java の走査（単体テスト証跡）
# ---------------------------------------------------------------------------


def build_word_pattern(catalog: list[WordEntry]) -> tuple[re.Pattern, dict[str, str]]:
    """全語の正規名・別名から、単語境界`\\b`付きの単一の選択正規表現を組み立てる。

    Pythonの`\\b`は連続する漢字・かな文字を1つの「語」とみなすため（デフォルトでUnicode対応）、
    「一行表示する」の内部にある「表示する」を誤って1件としてカウントすることはない。
    """
    variant_to_canonical: dict[str, str] = {}
    for entry in catalog:
        for variant in entry.name_variants:
            variant_to_canonical[variant] = entry.canonical_name
    ordered = sorted(variant_to_canonical, key=len, reverse=True)
    pattern = re.compile(r"\b(?:" + "|".join(re.escape(v) for v in ordered) + r")\b")
    return pattern, variant_to_canonical


def scan_unit_tests(root: Path, catalog: list[WordEntry]) -> dict[str, set[str]]:
    result: dict[str, set[str]] = defaultdict(set)
    base = root / "src" / "test" / "java"
    if not base.is_dir() or not catalog:
        return result
    pattern, variant_to_canonical = build_word_pattern(catalog)
    for path in base.rglob("*.java"):
        if not path.is_file():
            continue
        rel = str(path.relative_to(root))
        text = read_text(path)
        seen_here: set[str] = set()
        for m in pattern.finditer(text):
            canonical = variant_to_canonical[m.group(0)]
            if canonical not in seen_here:
                seen_here.add(canonical)
                result[canonical].add(rel)
    return result


# ---------------------------------------------------------------------------
# レポート生成
# ---------------------------------------------------------------------------


@dataclass
class WordReport:
    entry: WordEntry
    n_files: list[str]
    f_structured_evidence: list[str]
    f_text_files: list[str]
    r_files: list[str]
    unit_files: list[str]
    status: str


def build_report(
    catalog: list[WordEntry], scan: ScanResult, unit: dict[str, set[str]]
) -> list[WordReport]:
    reports = []
    for entry in catalog:
        variants = entry.name_variants
        n_files = sorted(set().union(*(scan.n_tokens.get(v, set()) for v in variants)))
        f_struct = sorted(
            set().union(*(scan.f_structured.get(v, set()) for v in variants))
        )
        f_text = sorted(
            set().union(*(scan.f_text_tokens.get(v, set()) for v in variants))
        )
        r_files = sorted(set().union(*(scan.r_tokens.get(v, set()) for v in variants)))
        unit_files = sorted(unit.get(entry.canonical_name, set()))

        n_ok = bool(n_files)
        f_ok = bool(f_struct) or bool(f_text)
        unit_ok = bool(unit_files)

        if n_ok and f_ok:
            status = "FULL"
        elif n_ok:
            status = "N_ONLY"
        elif f_ok:
            status = "F_ONLY"
        elif unit_ok:
            status = "UNIT_ONLY"
        else:
            status = "UNCOVERED"

        reports.append(
            WordReport(
                entry=entry,
                n_files=n_files,
                f_structured_evidence=f_struct,
                f_text_files=f_text,
                r_files=r_files,
                unit_files=unit_files,
                status=status,
            )
        )
    return reports


def sort_key(r: WordReport):
    return (STATUS_ORDER.get(r.status, 99), r.entry.canonical_name)


def summarize(reports: list[WordReport]) -> dict:
    by_status = {s: sum(1 for r in reports if r.status == s) for s in STATUS_ORDER}
    with_r = sum(1 for r in reports if r.r_files)
    return {
        "totalWords": len(reports),
        "byStatus": by_status,
        "withResourceEvidence": with_r,
        "withoutResourceEvidence": len(reports) - with_r,
    }


def render_text(reports: list[WordReport], summary: dict, warnings: list[str]) -> str:
    lines = []
    lines.append("BSB 組み込み語 x N/F/R 網羅マトリクス")
    lines.append("=" * 38)
    lines.append(f"生成日時: {datetime.datetime.now().isoformat(timespec='seconds')}")
    lines.append("")
    lines.append(f"組み込み語総数: {summary['totalWords']}")
    for status in ("UNCOVERED", "UNIT_ONLY", "F_ONLY", "N_ONLY", "FULL"):
        lines.append(f"  {status:<10}: {summary['byStatus'][status]}")
    lines.append(
        f"  (参考) R(資源境界)証跡あり: {summary['withResourceEvidence']} / "
        f"証跡なし: {summary['withoutResourceEvidence']}"
        "  ※大半の語にRが無いのは想定どおり(下記docstring参照)"
    )
    lines.append("")
    lines.append("-" * 38)
    lines.append("語別詳細（優先度順: UNCOVERED > UNIT_ONLY > F_ONLY > N_ONLY > FULL）")
    lines.append("-" * 38)
    for r in sorted(reports, key=sort_key):
        e = r.entry
        alias_note = f" (別名: {', '.join(e.aliases)})" if e.aliases else ""
        lines.append(
            f"[{r.status}] {e.canonical_name}{alias_note} (機能グループ: {e.feature_group})"
        )
        if r.n_files:
            shown = ", ".join(r.n_files[:2]) + (" ..." if len(r.n_files) > 2 else "")
            lines.append(f"    N: {shown}")
        if r.f_structured_evidence:
            lines.append(f"    F(structured): {', '.join(r.f_structured_evidence[:3])}")
        if r.f_text_files:
            shown = ", ".join(r.f_text_files[:2]) + (
                " ..." if len(r.f_text_files) > 2 else ""
            )
            lines.append(f"    F(text only): {shown}")
        if r.status == "UNIT_ONLY" and r.unit_files:
            shown = ", ".join(r.unit_files[:3]) + (
                " ..." if len(r.unit_files) > 3 else ""
            )
            lines.append(f"    unit: {shown}")
        if r.r_files:
            shown = ", ".join(r.r_files[:2]) + (" ..." if len(r.r_files) > 2 else "")
            lines.append(f"    R: {shown}")
    if warnings:
        lines.append("")
        lines.append("-" * 38)
        lines.append("警告")
        lines.append("-" * 38)
        for w in warnings:
            lines.append(f"  - {w}")
    return "\n".join(lines) + "\n"


def render_markdown(
    reports: list[WordReport], summary: dict, warnings: list[str]
) -> str:
    lines = []
    lines.append("# BSB 組み込み語 x N/F/R 網羅マトリクス")
    lines.append("")
    lines.append(f"生成日時: {datetime.datetime.now().isoformat(timespec='seconds')}")
    lines.append("")
    lines.append("## サマリー")
    lines.append("")
    lines.append("| 分類 | 件数 |")
    lines.append("|---|---:|")
    lines.append(f"| 組み込み語総数 | {summary['totalWords']} |")
    for status in ("UNCOVERED", "UNIT_ONLY", "F_ONLY", "N_ONLY", "FULL"):
        lines.append(f"| {status} | {summary['byStatus'][status]} |")
    lines.append(f"| (参考) R証跡あり | {summary['withResourceEvidence']} |")
    lines.append(f"| (参考) R証跡なし | {summary['withoutResourceEvidence']} |")
    lines.append("")
    lines.append(
        "> Rは大半の語にとって証跡が無いのが正常です。理由はスクリプト冒頭のdocstringを参照してください。"
    )
    lines.append("")
    lines.append("## 語別詳細")
    lines.append("")
    lines.append(
        "| status | 語 | 別名 | 機能グループ | N証跡 | F(structured) | F(text only) | unit証跡 | R証跡 |"
    )
    lines.append("|---|---|---|---|---|---|---|---|---|")
    for r in sorted(reports, key=sort_key):
        e = r.entry
        aliases = "、".join(e.aliases) if e.aliases else "-"

        def joined(items: list[str], limit: int = 2) -> str:
            if not items:
                return "-"
            shown = "、".join(items[:limit])
            return shown + (" ..." if len(items) > limit else "")

        n = joined(r.n_files)
        fs = joined(r.f_structured_evidence)
        ft = joined(r.f_text_files)
        unit = (
            joined(r.unit_files)
            if r.status == "UNIT_ONLY"
            else ("-" if not r.unit_files else "(参考)")
        )
        rr = joined(r.r_files)
        lines.append(
            f"| {r.status} | `{e.canonical_name}` | {aliases} | {e.feature_group} | {n} | {fs} | {ft} | {unit} | {rr} |"
        )
    if warnings:
        lines.append("")
        lines.append("## 警告")
        lines.append("")
        for w in warnings:
            lines.append(f"- {w}")
    return "\n".join(lines) + "\n"


def render_json(reports: list[WordReport], summary: dict, warnings: list[str]) -> str:
    payload = {
        "generatedAt": datetime.datetime.now().isoformat(timespec="seconds"),
        "summary": summary,
        "warnings": warnings,
        "words": [
            {
                "canonicalName": r.entry.canonical_name,
                "aliases": r.entry.aliases,
                "featureGroup": r.entry.feature_group,
                "status": r.status,
                "n": {"covered": bool(r.n_files), "files": r.n_files},
                "fStructured": {
                    "covered": bool(r.f_structured_evidence),
                    "evidence": r.f_structured_evidence,
                },
                "fText": {"covered": bool(r.f_text_files), "files": r.f_text_files},
                "unit": {"covered": bool(r.unit_files), "files": r.unit_files},
                "r": {"covered": bool(r.r_files), "files": r.r_files},
            }
            for r in sorted(reports, key=lambda r: r.entry.canonical_name)
        ],
    }
    return json.dumps(payload, ensure_ascii=False, indent=2) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="BSB 組み込み語 x N/F/R 網羅マトリクス生成ツール"
    )
    parser.add_argument(
        "--format", choices=["text", "markdown", "json"], default="text"
    )
    parser.add_argument(
        "--output", type=str, default=None, help="出力先ファイル（省略時は標準出力）"
    )
    parser.add_argument(
        "--root", type=str, default=None, help="リポジトリルート（省略時は自動検出）"
    )
    parser.add_argument(
        "--jar",
        type=str,
        default=None,
        help="実行するfat jar（省略時はbuild/libsから自動検出）",
    )
    parser.add_argument(
        "--java", type=str, default="java", help="javaコマンド（既定: PATH上のjava）"
    )
    parser.add_argument(
        "--words-json",
        type=str,
        default=None,
        help="jarを実行する代わりに、あらかじめ取得したexplain --json出力(またはbuiltinWords配列)を読む",
    )
    parser.add_argument(
        "--strict",
        action="store_true",
        help="UNCOVERED（適合フィクスチャにも単体テストにも一切出現しない語）が1件でもあれば終了コード1",
    )
    args = parser.parse_args(argv)

    root = find_repo_root(args.root)

    if args.words_json:
        words_path = Path(args.words_json)
        if not words_path.is_absolute():
            words_path = root / words_path
        raw_words = load_words_from_file(words_path)
    else:
        jar_path = Path(args.jar) if args.jar else find_default_jar(root)
        if jar_path is None or not jar_path.is_file():
            raise SystemExit(
                "実行可能なjarが見つかりませんでした。\n"
                "先に `./gradlew shadowJar` を実行するか、--jar でjarのパスを指定してください。\n"
                "javaが無い/バージョンが合わない環境では --words-json で代替できます。"
            )
        raw_words = load_words_via_jar(args.java, jar_path)

    catalog = normalize_catalog(raw_words)
    scan = scan_conformance(root)
    unit = scan_unit_tests(root, catalog)
    reports = build_report(catalog, scan, unit)
    summary = summarize(reports)

    if args.format == "markdown":
        output = render_markdown(reports, summary, scan.warnings)
    elif args.format == "json":
        output = render_json(reports, summary, scan.warnings)
    else:
        output = render_text(reports, summary, scan.warnings)

    if args.output:
        out_path = Path(args.output)
        if not out_path.is_absolute():
            out_path = root / out_path
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(output, encoding="utf-8")
        print(f"書き出しました: {out_path}", file=sys.stderr)
    else:
        sys.stdout.write(output)

    if args.strict and summary["byStatus"]["UNCOVERED"] > 0:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
