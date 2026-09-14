# JERG機能グループ: JSON参照・構築の診断

> [JSON参照・構築](json-access-construction.md)および
> [適合性](json-access-construction-conformance.md)と一体です。

## 1. 診断一覧

| コード | 条件 | 必須フィールド |
|---|---|---|
| `E_JSON_POINTER_SYNTAX` | pointerがRFC 6901文字列表現でない | `word`、`reason`、`offset` |
| `E_JSON_OBJECT_BUILD_LENGTH_MISMATCH` | キー数と値数が異なる | `word`、`keyCount`、`valueCount` |

両方とも実行時、error、終了コード10です。一括構築の重複キーは既存`E_JSON_DUPLICATE_KEY`、既存上限は
既存JSON診断を使います。stack不足と静的型不一致には既存診断を使います。

## 2. 優先順位と非開示

pointerは構文を全体検証してから参照し、最初の不正escapeを診断します。一括構築は長さ不一致、member数、
重複キー、JSON構造上限、構築量、作業量の順で1件だけ診断します。

診断は入力JSON、参照値、キー列、値列を含めません。重複時だけ既存規則に従う短い`keyPreview`を許可し、
pointer構文診断の`reason`は閉じたASCII識別値、`offset`は0始まりUnicodeスカラー位置です。
