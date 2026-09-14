# 機能グループと適合性テスト

BSB の実装、仕様、適合データは次の機能グループ ID で対応付けます。ID は開発順ではなく、
対象機能を表す安定した識別子です。

| ID | 機能 | 適合データ |
|---|---|---|
| `CORE` | 基本文法、単語、診断、整形 | `tests/conformance/language-core` |
| `FLOW` | 条件分岐、反復、制御移行 | `tests/conformance/control-flow` |
| `BIND` | 定数、変数、スコープ、名前解決 | `tests/conformance/bindings` |
| `ARRAY` | 一次元不変配列と配列反復 | `tests/conformance/arrays` |
| `NUM` | 整数・小数の演算、変換、丸め | `tests/conformance/numerics` |
| `TEXT` | Unicode文字列と正規表現 | `tests/conformance/text-regex` |
| `IO` | コンソール、起動情報、時刻、待機 | `tests/conformance/host-io` |
| `JSON` | 第一級JSON値 | `tests/conformance/json` |
| `EXP` | 数値リテラルの指数表記 | `tests/conformance/exponent-literals` |
| `DTXT` | 小数文字列の指数表記 | `tests/conformance/decimal-text` |
| `OPT` | `任意<T>` | `tests/conformance/optional-values` |
| `RESULT` | `結果<T,E>` | `tests/conformance/result-values` |
| `RJSON` | 回復可能なJSON解析 | `tests/conformance/recoverable-json` |
| `JSHAPE` | 第一級JSON形状による構造検証 | `tests/conformance/json-shapes` |
| `CONN` | 論理接続と接続設定 | `tests/conformance/logical-connections` |
| `BYTES` | 不変バイト列、UTF-8、Base64 | `tests/conformance/byte-sequences` |
| `HTTPS` | HTTPS要求・応答・通信失敗 | `tests/conformance/https` |
| `NARRAY` | 二次元配列 | `tests/conformance/nested-arrays` |
| `WST` | 名前付き作業領域、ファイル、CSV/TSV | `tests/conformance/workspace-tables` |

ケース ID は `<GROUP>-<KIND><NUMBER>` の形式です。`KIND` は次を表します。

- `N`: 正常系。値、出力、整形、説明などの通常契約
- `F`: 失敗系。字句・構文・静的・実行時診断と回復可能失敗
- `R`: 資源境界。上限、原子性、予算、非開示

例: `JSON-N001`、`HTTPS-F004`、`BYTES-R003`。

診断 JSON の `stage` は開発順を表す値ではありません。`utf8`、`lexical`、`syntax`、`name`、
`typeAndStack`、`runtime` という処理フェーズを示す公開フィールドなので、この名称は維持します。

`explain --json` の組み込み語には `featureGroup` が含まれ、上表の ID のいずれかを返します。
