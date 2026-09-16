# BSB 詳細言語仕様

このディレクトリには、現在実装されている言語・CLI・ホスト連携の規範仕様を置きます。文書名と
適合性ケース ID は開発順ではなく機能グループを表します。各 ID の意味とテスト配置は
[機能グループと適合性テスト](../conformance.md)を参照してください。

| ID | 対象 | 仕様入口 | 診断 | 適合性 |
|---|---|---|---|---|
| `CORE` | 基本文法・Unicode・組み込み語 | [文法](language-core-grammar.md) | [診断](language-core-diagnostics.md) | [適合性](language-core-conformance.md) |
| `FLOW` | 分岐・反復・制御移行 | [制御フロー](control-flow.md) | [診断](control-flow-diagnostics.md) | [適合性](control-flow-conformance.md) |
| `BIND` | 定数・変数・スコープ | [束縛](bindings.md) | [診断](bindings-diagnostics.md) | [適合性](bindings-conformance.md) |
| `ARRAY` | 一次元配列 | [配列](arrays.md) | [診断](arrays-diagnostics.md) | [適合性](arrays-conformance.md) |
| `NUM` | 小数・数値演算 | [数値](numerics.md) | [診断](numerics-diagnostics.md) | [適合性](numerics-conformance.md) |
| `TEXT` | 文字列・正規表現 | [文字列](string-operations.md) / [正規表現](regular-expressions.md) | [診断](text-regex-diagnostics.md) | [適合性](text-regex-conformance.md) |
| `IO` | コンソール・実行環境 | [コンソール](host-io-console.md) / [実行環境](host-io-environment.md) | [診断](host-io-diagnostics.md) | [適合性](host-io-conformance.md) |
| `JSON` | 第一級 JSON 値 | [JSON](json-values.md) | [診断](json-diagnostics.md) | [適合性](json-conformance.md) |
| `EXP` | 指数付き数値リテラル | [指数リテラル](exponent-literals.md) | [診断](exponent-literals-diagnostics.md) | [適合性](exponent-literals-conformance.md) |
| `DTXT` | 指数付き小数文字列 | [小数文字列](decimal-text.md) | [診断](decimal-text-diagnostics.md) | [適合性](decimal-text-conformance.md) |
| `OPT` | 任意値 | [任意値](optional-values.md) | [診断](optional-values-diagnostics.md) | [適合性](optional-values-conformance.md) |
| `RESULT` | 結果値 | [結果値](result-values.md) | [診断](result-values-diagnostics.md) | [適合性](result-values-conformance.md) |
| `RJSON` | 回復可能な JSON 解析 | [JSON 解析](recoverable-json-parsing.md) | [診断](recoverable-json-diagnostics.md) | [適合性](recoverable-json-conformance.md) |
| `JSHAPE` | 最小 JSON 形状検証 | [JSON 形状](json-shapes.md) | [診断](json-shapes-diagnostics.md) | [適合性](json-shapes-conformance.md) |
| `CONN` | 論理接続 | [論理接続](logical-connections.md) | [診断](logical-connections-diagnostics.md) | [適合性](logical-connections-conformance.md) |
| `BYTES` | バイト列・UTF-8・Base64 | [バイト列](byte-sequences.md) | [診断](byte-sequences-diagnostics.md) | [適合性](byte-sequences-conformance.md) |
| `HTTPS` | HTTPS クライアント | [HTTPS](https.md) | [診断](https-diagnostics.md) | [適合性](https-conformance.md) |
| `HTTP-REL` | HTTP 信頼性 | [再試行と最終送信記録](http-reliability.md) | [診断](http-reliability-diagnostics.md) | [適合性](http-reliability-conformance.md) |
| `HTTP-API` | HTTP API相互運用 | [form・認証・status・圧縮](http-api-interoperability.md) | [診断](http-api-interoperability-diagnostics.md) | [適合性](http-api-interoperability-conformance.md) |
| `NARRAY` | 二次元配列 | [二次元配列](nested-arrays.md) | [診断](nested-arrays-diagnostics.md) | [適合性](nested-arrays-conformance.md) |
| `WST` | 作業領域・ファイル・CSV/TSV | [統合仕様](workspace-tables.md) | [診断](workspace-tables-diagnostics.md) | [適合性](workspace-tables-conformance.md) |
| `JERG` | JSON参照・構築 | [JSON参照・構築](json-access-construction.md) | [診断](json-access-construction-diagnostics.md) | [適合性](json-access-construction-conformance.md) |

CLI の公開 JSON 契約は [診断 JSON](cli-json.md) と [説明 JSON](explain-json.md)、接続設定は
[CLI 接続設定](cli-connection-config.md)を参照してください。整形規則は各機能グループの
`*-format.md`に分離しています。

`任意`・`結果`の局所伝播と、それらを要素に持つ配列の横断契約は
[任意・結果の合成](optional-result-composition-diagnostics.md)を参照してください。
