#!/usr/bin/env python3
"""`explain --json` から利用者向けの全組み込み語一覧を生成する。"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


GROUPS = {
    "CORE": "基本",
    "FLOW": "制御フロー",
    "ARRAY": "一次元配列",
    "NUM": "数値",
    "TEXT": "文字列と正規表現",
    "IO": "コンソールと実行情報",
    "JSON": "JSON",
    "OPT": "任意値",
    "RESULT": "結果値",
    "RJSON": "回復可能なJSON解析",
    "JSHAPE": "JSON形状検証",
    "CONN": "論理接続",
    "BYTES": "バイト列",
    "HTTPS": "HTTPS",
    "HTTP-API": "HTTP API相互運用",
    "NARRAY": "二次元配列",
    "WST": "作業領域、ファイル、表",
    "JERG": "JSON参照・構築",
}


def effect(word: dict[str, object]) -> str:
    stack = word["stackEffect"]
    assert isinstance(stack, dict)
    inputs = " ".join(stack["inputs"])
    outputs = " ".join(stack["outputs"])
    left = f"{inputs} " if inputs else ""
    right = f" {outputs}" if outputs else ""
    return f"（{left}--{right}）"


def generate(document: dict[str, object]) -> str:
    words = document.get("builtinWords")
    if not isinstance(words, list):
        raise ValueError("入力JSONに builtinWords 配列がありません")

    unknown = sorted({word["featureGroup"] for word in words} - GROUPS.keys())
    if unknown:
        raise ValueError(f"未知の機能グループです: {', '.join(unknown)}")

    lines = [
        "# 全組み込み語一覧",
        "",
        "このページは、現在の処理系が公開する組み込み語を機能グループ別に網羅した索引です。",
        "スタック効果では左側が入力、右側が出力です。`T`、`E`、`N`などは辞書内の型変数・型制約であり、",
        "利用者定義単語の宣言には書けません。失敗条件、資源上限、具体化規則は",
        "[カテゴリ別の説明](builtins.md)と、そこからリンクされた詳細仕様を参照してください。",
        "",
        "<!-- この表は tools/generate_builtin_reference.py で生成します。直接編集しないでください。 -->",
    ]
    for group, title in GROUPS.items():
        selected = [word for word in words if word["featureGroup"] == group]
        if not selected:
            continue
        lines.extend(
            [
                "",
                f"## {title}（`{group}`）",
                "",
                "| 単語 | スタック効果 | 説明 |",
                "|---|---|---|",
            ]
        )
        for word in selected:
            description = str(word["description"]).replace("|", "\\|")
            lines.append(f"| `{word['name']}` | `{effect(word)}` | {description} |")
    lines.extend(
        [
            "",
            "## 更新方法",
            "",
            "処理系の辞書を変更したときは、有効なソースに対する `explain --json` の出力を保存し、次を実行します。",
            "",
            "```shell",
            "python3 tools/generate_builtin_reference.py explain.json docs/reference/builtin-catalog.md",
            "```",
            "",
            "生成元となる `builtinWords` は、未使用の語も含む辞書順の完全一覧です。",
        ]
    )
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path, help="explain --json の出力")
    parser.add_argument("output", type=Path, help="生成するMarkdown")
    args = parser.parse_args()
    document = json.loads(args.input.read_text(encoding="utf-8"))
    args.output.write_text(generate(document), encoding="utf-8")


if __name__ == "__main__":
    main()
