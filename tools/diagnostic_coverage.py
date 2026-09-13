#!/usr/bin/env python3
"""BSB 診断コード網羅マトリクス生成ツール。

目的
----
`docs/spec/*diagnostics*.md`・`docs/spec/cli-json.md`・`docs/spec/explain-json.md` が定義する
診断コード（`E_*` / `W_*`）それぞれについて、次の3種類のソースのどこで実際に踏まれているかを
機械的に集計し、人間およびコーディングエージェントが客観的に参照できるレポートを出力する。

  1. 実装      : src/main/java 配下に識別子として出現するか
  2. 適合フィクスチャ: tests/conformance 配下（diagnostics.tsv, resources.tsv,
                  cli-json/explain-json の expected/*, generated/*.properties 等）に
                  出現するか（＝CLI経由のエンドツーエンドな宣言的テストで踏まれているか）
  3. 単体テスト  : src/test/java 配下に識別子として出現するか。さらに、出現行の前後5行以内に
                  assert 系の呼び出し（assertEquals / assertTrue / Assertions.assertThat 等、
                  いずれも部分文字列 "assert" を含む）があるかどうかで
                  「asserted（実際に検証していそう）」と「mentioned のみ（switch文や
                  マッピングテーブルなど、検証を伴わない言及）」を区別する。

使い方
------
    python3 tools/diagnostic_coverage.py                         # テキストサマリーを標準出力へ
    python3 tools/diagnostic_coverage.py --format markdown       # Markdown表を標準出力へ
    python3 tools/diagnostic_coverage.py --format json           # 機械可読JSONを標準出力へ
    python3 tools/diagnostic_coverage.py --format markdown \
        --output tools/reports/diagnostic-coverage.md            # ファイルへ書き出し
    python3 tools/diagnostic_coverage.py --strict                # UNCOVEREDが1件でもあれば exit 1

依存関係は Python 3.9+ の標準ライブラリのみ。プロジェクトのビルド（Gradle/JDK）は不要。

分類（status）
--------------
  CONFORMANCE      適合フィクスチャ（宣言的データ）で踏まれている。最も信頼できる証跡。
  UNIT_ONLY        適合フィクスチャでは踏まれていないが、単体テストの assert 近傍で踏まれている。
  MENTIONED_ONLY   コード識別子はテストソース中に出現するが、assert 近傍ヒューリスティックに
                   一致しない（switch文・マッピングテーブルなど検証を伴わない言及の可能性が高い。
                   要目視確認）。
  UNCOVERED        実装・適合フィクスチャ・テストソースのどこにも出現しない。仕様上は定義済みだが
                   一切テストされていない可能性が高い最重要ギャップ。

このほか、仕様外の付帯情報として次も検出する。
  UNDOCUMENTED_IN_SPEC  実装またはテストに出現するが、対象spec docsのどれにも見つからないコード
                        （表記揺れ・spec更新漏れ・命名変更の可能性）。
  NOT_IMPLEMENTED       spec上は定義されているが、src/main/java のどこにも見つからないコード
                        （実装がまだ追いついていない可能性。設計上「将来機能の予約」も含み得るので
                        単純な不具合とは限らない）。

ヒューリスティックの限界（重要）
--------------------------------
  - "asserted" 判定は前後5行以内に "assert" という字面があるかどうかのテキストマッチであり、
    実際にそのコードを検証する assert かどうかまでは判定していない（過大評価・過小評価どちらも
    起こり得る）。
  - コメントアウトされたコードや文字列リテラル中の言及も「出現」として数える。
  - tests/conformance 配下は拡張子を問わず全ファイルを対象にしており、TSVの列構成が機能グループごとに
    異なっていても頑健に拾えるようにしている（トレードオフとして、コードと無関係な文脈での
    偶然の一致を拾う可能性はゼロではない）。ただし messages.properties は「機能グループで定義済みの
    全コードに対するメッセージテンプレート辞書」であり、実際のテストケースの有無とは無関係に
    ほぼ全コードのキーを持つため、常に除外している（除外しないと大半のコードが実際には
    テストされていなくても CONFORMANCE と誤判定されてしまう）。
  - 本ツールは「テストが存在するか」を測るものであり、「そのテストが正しく仕様を検証しているか」
    （テストの質）までは保証しない。UNCOVERED / MENTIONED_ONLY は必ず目視で確認すること。
"""

from __future__ import annotations

import argparse
import csv
import datetime
import json
import re
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

# `E_XXX` / `W_XXX` 形式の診断コード。バッククォートで囲まれた形（spec docs用）と、
# 通常の識別子境界だけの形（Java/フィクスチャ用）の2種類を使う。
# セグメント（アンダースコア区切りの各語）は英大文字・数字1文字以上とし、末尾がアンダースコアで
# 終わる文字列（例: "E_EXPECTED_" のようなprefix比較用リテラル）を診断コードとして誤検出しない
# ようにしている。
CODE_SEGMENT = r"[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*"
CODE_IN_BACKTICKS = re.compile(rf"`([EW]_{CODE_SEGMENT})`")
CODE_BARE = re.compile(rf"\b([EW]_{CODE_SEGMENT})\b")
ASSERT_HINT = re.compile(r"assert", re.IGNORECASE)

STATUS_ORDER = {
    "UNCOVERED": 0,
    "MENTIONED_ONLY": 1,
    "UNIT_ONLY": 2,
    "CONFORMANCE": 3,
}


def find_repo_root(explicit: str | None) -> Path:
    if explicit:
        root = Path(explicit).resolve()
    else:
        # このスクリプトは <repo>/tools/diagnostic_coverage.py に置く前提。
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


@dataclass
class Evidence:
    """コードごとの証跡: コード -> 出現ファイルの相対パス集合。"""

    files: dict[str, set[str]] = field(default_factory=lambda: defaultdict(set))

    def add(self, code: str, relpath: str) -> None:
        self.files[code].add(relpath)

    def has(self, code: str) -> bool:
        return code in self.files

    def get(self, code: str) -> list[str]:
        return sorted(self.files.get(code, ()))


def collect_spec_codes(root: Path) -> tuple[Evidence, list[str]]:
    """docs/spec の診断系ドキュメントからバッククォート付きコードを抽出する。"""
    warnings: list[str] = []
    ev = Evidence()
    spec_dir = root / "docs" / "spec"
    candidates = sorted(spec_dir.glob("*diagnostics*.md"))
    for extra in ("cli-json.md", "explain-json.md"):
        p = spec_dir / extra
        if p.is_file():
            candidates.append(p)
        else:
            warnings.append(f"spec対象ファイルが見つかりません: {p.relative_to(root)}")
    if not candidates:
        warnings.append("docs/spec に *diagnostics*.md が1件も見つかりませんでした。")
    for path in candidates:
        text = read_text(path)
        rel = str(path.relative_to(root))
        for m in CODE_IN_BACKTICKS.finditer(text):
            ev.add(m.group(1), rel)
    return ev, warnings


def collect_bare_codes_in_tree(
    root: Path,
    subdir: str,
    patterns: list[str],
    exclude_names: frozenset[str] = frozenset(),
) -> Evidence:
    """root/subdir 配下を再帰的に走査し、生の識別子としてコードを拾う。

    exclude_names に列挙したファイル名（例: messages.properties）は走査対象から除外する。
    messages.properties は「その機能グループで定義済みの全コードに対する人間向けメッセージ
    テンプレート辞書」であり、実際にどのテストケースがそのコードを踏んだかとは無関係に
    ほぼ全コードのキーを持つ。そのまま証跡に含めると、テストが1件も無いコードまで
    「CONFORMANCE」と誤判定してしまうため、conformance証跡の収集からは常に除外する。
    """
    ev = Evidence()
    base = root / subdir
    if not base.is_dir():
        return ev
    seen: set[Path] = set()
    for pattern in patterns:
        for path in base.rglob(pattern):
            if not path.is_file() or path in seen:
                continue
            if path.name in exclude_names:
                continue
            seen.add(path)
            text = read_text(path)
            rel = str(path.relative_to(root))
            for m in CODE_BARE.finditer(text):
                ev.add(m.group(1), rel)
    return ev


def collect_test_codes(root: Path) -> tuple[Evidence, Evidence]:
    """src/test/java を走査し、(mentioned, asserted) の2種類の証跡を返す。

    mentioned: コード識別子が出現する全ファイル。
    asserted : 出現行の前後5行以内に "assert" を含む字面がある出現のみ。
    """
    mentioned = Evidence()
    asserted = Evidence()
    base = root / "src" / "test" / "java"
    if not base.is_dir():
        return mentioned, asserted
    for path in base.rglob("*.java"):
        if not path.is_file():
            continue
        rel = str(path.relative_to(root))
        lines = read_text(path).splitlines()
        for i, line in enumerate(lines):
            for m in CODE_BARE.finditer(line):
                code = m.group(1)
                mentioned.add(code, rel)
                window = lines[max(0, i - 5) : i + 6]
                if any(ASSERT_HINT.search(w) for w in window):
                    asserted.add(code, rel)
    return mentioned, asserted


@dataclass
class CodeRecord:
    code: str
    severity: str
    documented: bool
    spec_files: list[str]
    implemented: bool
    main_files: list[str]
    conformance: bool
    conformance_files: list[str]
    unit_asserted: bool
    unit_asserted_files: list[str]
    unit_mentioned: bool
    unit_mentioned_files: list[str]
    status: str


def build_report(root: Path) -> tuple[list[CodeRecord], dict, list[str]]:
    spec_ev, warnings = collect_spec_codes(root)
    main_ev = collect_bare_codes_in_tree(root, "src/main/java", ["*.java"])
    conformance_ev = collect_bare_codes_in_tree(
        root,
        "tests/conformance",
        ["*"],
        exclude_names=frozenset({"messages.properties"}),
    )
    test_mentioned_ev, test_asserted_ev = collect_test_codes(root)

    all_codes = (
        set(spec_ev.files)
        | set(main_ev.files)
        | set(conformance_ev.files)
        | set(test_mentioned_ev.files)
    )

    records: list[CodeRecord] = []
    for code in sorted(all_codes):
        documented = spec_ev.has(code)
        implemented = main_ev.has(code)
        conformance = conformance_ev.has(code)
        unit_asserted = test_asserted_ev.has(code)
        unit_mentioned = test_mentioned_ev.has(code)

        if not documented:
            status = "UNDOCUMENTED_IN_SPEC"
        elif conformance:
            status = "CONFORMANCE"
        elif unit_asserted:
            status = "UNIT_ONLY"
        elif unit_mentioned:
            status = "MENTIONED_ONLY"
        else:
            status = "UNCOVERED"

        records.append(
            CodeRecord(
                code=code,
                severity="error" if code.startswith("E_") else "warning",
                documented=documented,
                spec_files=spec_ev.get(code),
                implemented=implemented,
                main_files=main_ev.get(code),
                conformance=conformance,
                conformance_files=conformance_ev.get(code),
                unit_asserted=unit_asserted,
                unit_asserted_files=test_asserted_ev.get(code),
                unit_mentioned=unit_mentioned,
                unit_mentioned_files=test_mentioned_ev.get(code),
                status=status,
            )
        )

    summary = {
        "totalCodes": len(records),
        "documented": sum(1 for r in records if r.documented),
        "byStatus": {
            s: sum(1 for r in records if r.status == s)
            for s in (
                "CONFORMANCE",
                "UNIT_ONLY",
                "MENTIONED_ONLY",
                "UNCOVERED",
                "UNDOCUMENTED_IN_SPEC",
            )
        },
        "notImplemented": sorted(
            r.code for r in records if r.documented and not r.implemented
        ),
    }
    return records, summary, warnings


def sort_key(r: CodeRecord):
    return (STATUS_ORDER.get(r.status, 99), r.code)


def render_text(records: list[CodeRecord], summary: dict, warnings: list[str]) -> str:
    lines: list[str] = []
    lines.append("BSB 診断コード網羅マトリクス")
    lines.append("=" * 32)
    lines.append(f"生成日時: {datetime.datetime.now().isoformat(timespec='seconds')}")
    lines.append("")
    lines.append(f"spec上の診断コード総数: {summary['documented']}")
    for status in ("CONFORMANCE", "UNIT_ONLY", "MENTIONED_ONLY", "UNCOVERED"):
        lines.append(f"  {status:<15}: {summary['byStatus'][status]}")
    if summary["byStatus"]["UNDOCUMENTED_IN_SPEC"]:
        lines.append(
            f"  (参考) spec未記載だが実装/テストに出現: {summary['byStatus']['UNDOCUMENTED_IN_SPEC']}"
        )
    if summary["notImplemented"]:
        lines.append("")
        lines.append(
            f"spec上は定義済みだが src/main/java に見つからないコード ({len(summary['notImplemented'])}件):"
        )
        for c in summary["notImplemented"]:
            lines.append(f"  - {c}")
    lines.append("")
    lines.append("-" * 32)
    lines.append(
        "コード別詳細（優先度順: UNCOVERED > MENTIONED_ONLY > UNIT_ONLY > CONFORMANCE）"
    )
    lines.append("-" * 32)
    for r in sorted(records, key=sort_key):
        if r.status == "UNDOCUMENTED_IN_SPEC":
            continue
        lines.append(f"[{r.status}] {r.code} ({r.severity})")
        if r.conformance_files:
            lines.append(
                f"    conformance: {', '.join(r.conformance_files[:3])}"
                + (
                    f" ...ほか{len(r.conformance_files) - 3}件"
                    if len(r.conformance_files) > 3
                    else ""
                )
            )
        if r.unit_asserted_files:
            lines.append(f"    unit(asserted): {', '.join(r.unit_asserted_files)}")
        if r.status == "MENTIONED_ONLY" and r.unit_mentioned_files:
            lines.append(
                f"    unit(mentioned only): {', '.join(r.unit_mentioned_files)}"
            )
        if not r.implemented:
            lines.append("    ※ src/main/java に未出現（未実装の可能性）")
    if summary["byStatus"]["UNDOCUMENTED_IN_SPEC"]:
        lines.append("")
        lines.append("-" * 32)
        lines.append(
            "参考: spec未記載コード（実装/テストにのみ出現。表記揺れ・spec更新漏れの可能性）"
        )
        lines.append("-" * 32)
        for r in sorted(records, key=lambda r: r.code):
            if r.status == "UNDOCUMENTED_IN_SPEC":
                lines.append(
                    f"  - {r.code} (main={bool(r.main_files)}, conformance={bool(r.conformance_files)}, test={bool(r.unit_mentioned_files)})"
                )
    if warnings:
        lines.append("")
        lines.append("-" * 32)
        lines.append("警告")
        lines.append("-" * 32)
        for w in warnings:
            lines.append(f"  - {w}")
    return "\n".join(lines) + "\n"


def render_markdown(
    records: list[CodeRecord], summary: dict, warnings: list[str]
) -> str:
    lines: list[str] = []
    lines.append("# BSB 診断コード網羅マトリクス")
    lines.append("")
    lines.append(f"生成日時: {datetime.datetime.now().isoformat(timespec='seconds')}")
    lines.append("")
    lines.append("## サマリー")
    lines.append("")
    lines.append("| 分類 | 件数 |")
    lines.append("|---|---:|")
    lines.append(f"| spec上の診断コード総数 | {summary['documented']} |")
    for status in ("CONFORMANCE", "UNIT_ONLY", "MENTIONED_ONLY", "UNCOVERED"):
        lines.append(f"| {status} | {summary['byStatus'][status]} |")
    if summary["byStatus"]["UNDOCUMENTED_IN_SPEC"]:
        lines.append(
            f"| (参考) spec未記載だが実装/テストに出現 | {summary['byStatus']['UNDOCUMENTED_IN_SPEC']} |"
        )
    lines.append("")
    if summary["notImplemented"]:
        lines.append("## spec上は定義済みだが未実装の可能性があるコード")
        lines.append("")
        for c in summary["notImplemented"]:
            lines.append(f"- `{c}`")
        lines.append("")
    lines.append("## コード別詳細")
    lines.append("")
    lines.append(
        "| status | code | severity | conformance証跡 | unit(asserted)証跡 | unit(mentioned only)証跡 | 実装 |"
    )
    lines.append("|---|---|---|---|---|---|---|")
    for r in sorted(records, key=sort_key):
        if r.status == "UNDOCUMENTED_IN_SPEC":
            continue
        conf = (
            "、".join(r.conformance_files[:2])
            + (" ..." if len(r.conformance_files) > 2 else "")
            if r.conformance_files
            else "-"
        )
        asserted = "、".join(r.unit_asserted_files) if r.unit_asserted_files else "-"
        mentioned = (
            "、".join(r.unit_mentioned_files)
            if (r.status == "MENTIONED_ONLY" and r.unit_mentioned_files)
            else "-"
        )
        impl = "○" if r.implemented else "×"
        lines.append(
            f"| {r.status} | `{r.code}` | {r.severity} | {conf} | {asserted} | {mentioned} | {impl} |"
        )
    if summary["byStatus"]["UNDOCUMENTED_IN_SPEC"]:
        lines.append("")
        lines.append("## 参考: spec未記載コード")
        lines.append("")
        lines.append(
            "実装またはテストに出現するが、対象spec docsに見つからないコード。表記揺れやspec更新漏れの可能性があるため確認を推奨する。"
        )
        lines.append("")
        lines.append("| code | main出現 | conformance出現 | test出現 |")
        lines.append("|---|---|---|---|")
        for r in sorted(records, key=lambda r: r.code):
            if r.status == "UNDOCUMENTED_IN_SPEC":
                lines.append(
                    f"| `{r.code}` | {'○' if r.main_files else '×'} | {'○' if r.conformance_files else '×'} | {'○' if r.unit_mentioned_files else '×'} |"
                )
    if warnings:
        lines.append("")
        lines.append("## 警告")
        lines.append("")
        for w in warnings:
            lines.append(f"- {w}")
    return "\n".join(lines) + "\n"


def render_json(records: list[CodeRecord], summary: dict, warnings: list[str]) -> str:
    payload = {
        "generatedAt": datetime.datetime.now().isoformat(timespec="seconds"),
        "summary": summary,
        "warnings": warnings,
        "codes": [
            {
                "code": r.code,
                "severity": r.severity,
                "status": r.status,
                "documented": r.documented,
                "specFiles": r.spec_files,
                "implemented": r.implemented,
                "mainFiles": r.main_files,
                "conformance": r.conformance,
                "conformanceFiles": r.conformance_files,
                "unitAsserted": r.unit_asserted,
                "unitAssertedFiles": r.unit_asserted_files,
                "unitMentioned": r.unit_mentioned,
                "unitMentionedFiles": r.unit_mentioned_files,
            }
            for r in sorted(records, key=lambda r: r.code)
        ],
    }
    return json.dumps(payload, ensure_ascii=False, indent=2) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="BSB 診断コード網羅マトリクス生成ツール"
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
        "--strict",
        action="store_true",
        help="UNCOVERED（実装・テストのどこにも出現しないspec上のコード）が1件でもあれば終了コード1",
    )
    args = parser.parse_args(argv)

    root = find_repo_root(args.root)
    records, summary, warnings = build_report(root)

    if args.format == "markdown":
        output = render_markdown(records, summary, warnings)
    elif args.format == "json":
        output = render_json(records, summary, warnings)
    else:
        output = render_text(records, summary, warnings)

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
