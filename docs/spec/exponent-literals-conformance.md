# EXP機能グループ: 指数表記の適合データ

> 本文は現行実装の規範契約です。
>
> 現行契約: DTXT機能グループでEXP-F011を、指数対応後も不正な未完成指数`1e`の回帰へ更新しました。
> 現行の小数文字列指数適合契約は[DTXT機能グループ適合データ](decimal-text-conformance.md)を参照してください。

本仕様は[指数表記](exponent-literals.md)と[診断](exponent-literals-diagnostics.md)を検証する
機械可読データ契約を定めます。

## 1. 場所と形式

```text
tests/conformance/exponent-literals/
  cases.properties
  literals.tsv
  diagnostics.tsv
  resources.tsv
  messages.properties
  generated/resources.properties
  sources/
  canonical/
  chapter/
```

厳密UTF-8、BOMなし、LF、末尾LF、ルートからの相対パスを使います。`literals.tsv`は本番Lexerや
`BigDecimal`から生成しない独立オラクルです。巨大入力は`generated/resources.properties`の記述から
決定的に生成し、生成後の数字数とSHA-256をテスト側で再確認します。

## 2. ケース体系

- 正常例: `EXP-N001`〜`EXP-N012`
- 失敗例: `EXP-F001`〜`EXP-F012`
- 資源・内部境界: `EXP-R001`〜`EXP-R008`

IDは欠番、重複、別名を許しません。`cases.properties`は全32件と全資源を列挙し、未知キー、未使用行、
欠落ファイル、余分なIDを適合データ自身の失敗とします。

## 3. 正常例

| ID | 主対象 |
|---|---|
| EXP-N001 | 小数点なしの`e`・`E`指数 |
| EXP-N002 | 小数点ありの指数 |
| EXP-N003 | 指数符号と指数部先頭0 |
| EXP-N004 | 指数を持つ字句が常に`小数` |
| EXP-N005 | 加減乗、比較、等価性の正確な値 |
| EXP-N006 | 定数、変数、配列、利用者定義語での小数型 |
| EXP-N007 | 既存の整数・固定小数点リテラルが不変 |
| EXP-N008 | JSON数値の指数入力が独立して不変 |
| EXP-N009 | 負の0、末尾0、数学的整数の正規表示 |
| EXP-N010 | `format`が指数字句を保持し冪等 |
| EXP-N011 | 指数リテラル後の正しい区切り |
| EXP-N012 | 章末の単位変換プログラム |

## 4. 失敗例

| ID | 主対象 | コード |
|---|---|---|
| EXP-F001 | 指数数字の欠落`1e`、`1E` | `E_INVALID_NUMBER_LITERAL` |
| EXP-F002 | 指数符号後の数字欠落`1e+`、`1e-` | `E_INVALID_NUMBER_LITERAL` |
| EXP-F003 | 不正仮数`.5e2`、`1.e2` | `E_INVALID_NUMBER_LITERAL` |
| EXP-F004 | リテラル先頭の正符号`+1e2` | `E_INVALID_NUMBER_LITERAL` |
| EXP-F005 | 整数部の先頭0`01e2` | `E_INVALID_NUMBER_LITERAL` |
| EXP-F006 | 指数記号の重複 | `E_INVALID_NUMBER_LITERAL` |
| EXP-F007 | `e2`は数値でなく識別子 | `E_UNDEFINED_WORD` |
| EXP-F008 | 完成した指数後の区切り不足`1e2円` | `E_MISSING_SEPARATOR` |
| EXP-F009 | 未完成指数と識別子`1e円` | `E_INVALID_NUMBER_LITERAL` |
| EXP-F010 | 指数符号の重複`1e--2` | `E_INVALID_NUMBER_LITERAL` |
| EXP-F011 | 小数文字列の未完成指数`1e`を拒否 | `E_DECIMAL_TEXT_INVALID` |
| EXP-F012 | ソース指数のスケール超過 | `E_DECIMAL_SCALE_LIMIT` |

## 5. 資源・内部境界

| ID | 対象 |
|---|---|
| EXP-R001 | ソースの全入力数字4,096/4,097個 |
| EXP-R002 | 指数部配置でも全入力数字4,096/4,097個 |
| EXP-R003 | 構築前の正スケール65,536/65,537 |
| EXP-R004 | 構築前の負スケール-65,536/-65,537 |
| EXP-R005 | 末尾0除去後の負スケール-65,536/-65,537 |
| EXP-R006 | 0でも構築前スケールを検査する境界 |
| EXP-R007 | 4,096数字内の巨大指数を有限時間・有限幅で拒否 |
| EXP-R008 | 境界値の固定小数点表示長と`format`非評価 |

上限ちょうどは成功、1超過は規範コードで失敗します。ソースのスケール超過は`check`と`run`で
静的失敗し、同じ有効字句の`format`は成功します。512 MiBヒープで素のJava例外、部分stdout、
桁数に比例しない巨大確保を許しません。

## 6. 回帰と完了ゲート

1. 全32 ID、全TSV行、全生成指定を中央ハーネスがちょうど1回消費する
2. 旧`1e3`拒否期待をEXP機能グループの受理期待へ置き換え、他の先行する実装済み機能グループ期待は不変とする
3. DTXT機能グループは小数文字列の指数を受理するが、JSON経路は独立させ、不正文法の拒否を維持する
4. `check`、`run`、`format`、`check --json`、`explain --json`、トレースを比較する
5. 全回帰、Spotless、Javadoc、fat JAR、512 MiB境界、`git diff --check`を成功させる
