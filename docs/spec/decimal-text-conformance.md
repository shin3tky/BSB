# DTXT機能グループ: 小数文字列指数表記の適合データ

> 本文は現行実装の規範契約です。

## 1. 適合データの場所

```text
tests/conformance/decimal-text/
  cases.properties
  decimal-text.tsv
  diagnostics.tsv
  resources.tsv
  generated/resources.properties
  sources/
  chapter/
```

厳密UTF-8、BOMなしLF、末尾LF、相対パス、propertiesとTSVのエスケープはEXP機能グループと同じです。
全ID、全行、全参照資源を不足・重複・未使用なしで中央カタログから消費します。

## 2. ケース体系

- 正常例: `DTXT-N001`〜`DTXT-N008`
- 失敗例: `DTXT-F001`〜`DTXT-F012`
- 資源・内部境界: `DTXT-R001`〜`DTXT-R008`

CORE〜EXP機能グループの全回帰を同じ実行で維持します。TEXT機能グループTEXT-F023とEXP機能グループEXP-F011は、
DTXT機能グループによる変更注記を加え、指数対応後も不正な小数文字列を検査するケースへ更新します。

## 3. 正常例

| ID | 主対象 |
|---|---|
| DTXT-N001 | `e`/`E`、指数符号、指数先頭0、小数点あり・なし、負の0の受理 |
| DTXT-N002 | 正確な値と指数を使わない正規固定小数表示 |
| DTXT-N003 | 演算、等価性、配列、束縛、利用者定義語で常に`小数` |
| DTXT-N004 | 固定小数文字列と整数文字列変換の互換性 |
| DTXT-N005 | ソース指数、JSON指数、文字列指数の独立経路 |
| DTXT-N006 | `check`成功と文字列内容を保持する冪等な`format` |
| DTXT-N007 | トレースが入力文字列と変換後の正規小数を区別 |
| DTXT-N008 | 指数付き外部入力を計算へ使う章末プログラム |

## 4. 失敗例

| ID | 主対象 | 診断 |
|---|---|---|
| DTXT-F001 | 指数数字欠落`1e` | `E_DECIMAL_TEXT_INVALID` |
| DTXT-F002 | 指数符号後の数字欠落`1e+` | `E_DECIMAL_TEXT_INVALID` |
| DTXT-F003 | 先頭正符号`+1e2` | `E_DECIMAL_TEXT_INVALID` |
| DTXT-F004 | 仮数の先頭0`01e2` | `E_DECIMAL_TEXT_INVALID` |
| DTXT-F005 | 小数点前の数字欠落`.5e2` | `E_DECIMAL_TEXT_INVALID` |
| DTXT-F006 | 小数点後の数字欠落`1.e2` | `E_DECIMAL_TEXT_INVALID` |
| DTXT-F007 | 指数記号重複`1ee2` | `E_DECIMAL_TEXT_INVALID` |
| DTXT-F008 | 小数点も指数もない`1` | `E_DECIMAL_TEXT_INVALID` |
| DTXT-F009 | 整数文字列変換へ指数`1e2` | `E_INTEGER_TEXT_INVALID` |
| DTXT-F010 | 入力数字4,097個 | `E_NUMERIC_TEXT_DIGIT_LIMIT` |
| DTXT-F011 | 構築前スケール絶対値65,537 | `E_DECIMAL_SCALE_LIMIT`、`metric=rawScale` |
| DTXT-F012 | 正規化後スケール絶対値65,537 | `E_DECIMAL_SCALE_LIMIT`、`metric=normalizedScale` |

全失敗は`check`と`format`が成功し、`run`だけが終了10になります。`run`のstdoutは空で、
入力スタックを消費しません。文法診断の代表形以外は`decimal-text.tsv`の複数variantで覆います。

## 5. 資源・内部境界

| ID | 対象 | 受理側 | 拒否側 |
|---|---|---|---|
| DTXT-R001 | 仮数中心の全入力数字 | 4,096 | 4,097、`E_NUMERIC_TEXT_DIGIT_LIMIT` |
| DTXT-R002 | 指数部中心の全入力数字 | 4,096 | 4,097、`E_NUMERIC_TEXT_DIGIT_LIMIT` |
| DTXT-R003 | 正の構築前スケール | 65,536 | 65,537、`E_DECIMAL_SCALE_LIMIT` |
| DTXT-R004 | 負の構築前スケール | -65,536 | -65,537、`E_DECIMAL_SCALE_LIMIT` |
| DTXT-R005 | 係数末尾0除去後の負スケール | -65,536 | -65,537、`E_DECIMAL_SCALE_LIMIT` |
| DTXT-R006 | 0でも検査する構築前スケール | -65,536 | -65,537、`E_DECIMAL_SCALE_LIMIT` |
| DTXT-R007 | 4,096数字内の巨大指数 | 有限時間で飽和診断 | Java例外名と巨大展開なし |
| DTXT-R008 | 正規固定小数表示と非評価CLI | 上限値の`run`、超過値の`check`/`format` | 部分出力なし |

生成入力はテンプレートから決定的に作り、入力数字数と独立SHA-256を検証します。最大ヒープ
512 MiBで、指数値や固定小数展開の大きさに比例した予期しない中間確保がないことを確認します。

## 6. 公開CLIと章末成果物

全ケースについてコマンド別終了コード、stdout/stderr原子性、`format`二回適用、入力ファイル不変を
比較します。`check --json`と`explain --json`の版1・辞書107語は不変です。

章末プログラムはAPIなどから得た文字列`1.98e3`と`1e-1`を小数へ変換し、正確な演算結果を
固定小数点で表示します。fat JARで`check`、`run`、`format`を実行し、独立資源とバイト一致させます。
