# JERG機能グループ: JSON参照・構築の適合性

> [JSON参照・構築](json-access-construction.md)および
> [診断](json-access-construction-diagnostics.md)と一体です。

## 1. 必須観測

- 空pointerによるroot参照と、object・arrayをまたぐ参照
- `~0`・`~1` escape、空token、Unicode key
- 不在member、範囲外・非正規array添字、scalar途中値とJSON nullの区別
- 先頭`/`欠落と不正escapeの診断位置
- 空object、順序保持、子JSON共有、長さ不一致、重複キー
- 成功時と全失敗時のstack・予算原子性
- 導入時の辞書184語と既存182語の接頭辞維持、`JERG` 2語の固定メタデータ

## 2. 完了ゲート

実行時単体テスト、辞書契約、CLIの`check`・`run`・`explain --json`、章末sampleを通し、既存JSON契約、
診断coverage、組み込み語coverage、Spotless、全JUnit、fat JARを回帰します。
