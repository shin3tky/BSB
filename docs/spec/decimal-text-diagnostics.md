# DTXT機能グループ: 小数文字列指数表記の診断

> 本文は現行実装の規範契約です。
>
> NUM、TEXT、EXP機能グループの診断コード、位置、プレビュー、原子性を再利用し、新しいコードは追加しません。

## 1. 実行時診断

| コード | 条件 | 必須フィールド |
|---|---|---|
| `E_DECIMAL_TEXT_INVALID` | 入力全体がDTXT機能グループの`decimalText`に一致しない | `word`、`inputPreview`、`inputCodePoints` |
| `E_NUMERIC_TEXT_DIGIT_LIMIT` | 仮数と指数部のASCII数字合計が4,096個を超える | `word`、`numericType=小数`、`inputCodePoints`、`limit=4096`、`observed` |
| `E_DECIMAL_SCALE_LIMIT` | 構築前または正規化後スケール絶対値が65,536を超える | `word`、`metric`、`inputPreview`、`inputCodePoints`、`limit=65536`、`observed` |

`metric`は`rawScale`または`normalizedScale`です。`observed`は絶対値で、巨大指数では
上限の1超過である`65537`へ飽和できます。Java例外名や巨大な指数全文を公開しません。

## 2. メッセージ

`E_DECIMAL_TEXT_INVALID`の期待形式は「小数点または指数部を持つASCII小数表記」とします。
`actual`は空文字列なら「空文字列」、それ以外は入力プレビューです。修正候補は入力形に応じて
指数数字、小数点前後の数字、先頭0、先頭`+`の修正を示せますが、公開契約では少なくとも
有効例`1.0`または`1e0`へ誘導します。

`E_NUMERIC_TEXT_DIGIT_LIMIT`はTEXT機能グループの既存メッセージを維持します。
`E_DECIMAL_SCALE_LIMIT`の期待値は「スケール絶対値65536以下」、`actual`は飽和後の絶対値、
修正候補は指数の絶対値または小数桁を減らす内容とします。

入力プレビューはTEXT機能グループどおり、64 Unicodeコードポイントを超えると先頭32、
`…(+省略数)`、末尾16へ制限します。`inputCodePoints`は省略前の長さです。

## 3. 優先順位

同じ呼出しで複数条件が成立し得る場合、最初の1件だけを次の順で報告します。

1. `E_DECIMAL_TEXT_INVALID`
2. `E_NUMERIC_TEXT_DIGIT_LIMIT`
3. 構築前スケールの`E_DECIMAL_SCALE_LIMIT`（`metric=rawScale`）
4. 正規化後スケールの`E_DECIMAL_SCALE_LIMIT`（`metric=normalizedScale`）
5. 検査済み値の構築

文法不一致の入力に4,097個を超える数字が含まれても、文法診断を優先します。値0でも構築前
スケール検査を省略しません。構築前と正規化後の両方が超過する場合は`rawScale`を報告します。

## 4. 位置、終了、原子性

3診断はいずれも`文字列を小数に変換する`呼出しのspanを主位置とし、段階は`runtime`、
終了コードは10です。失敗時の入力スタック、保存値、stdoutは呼出し前のままです。
通常の実行時コンテキストフィールドとコールスタック表示は既存契約を維持します。
