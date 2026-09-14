# JSHAPE機能グループ: 最小JSON形状検証の診断

> [形状検証](json-shapes.md)および[適合性](json-shapes-conformance.md)と一体です。

## 1. 診断一覧

| コード | 条件 | 必須フィールド |
|---|---|---|
| `E_JSON_SHAPE_OBJECT_REQUIRED` | 必須・任意setterの第1入力がobject形状でない | `word`、`actualShapeKind` |
| `E_JSON_SHAPE_NODE_LIMIT` | 生成する形状が65,536ノードを超える | `word`、`limit=65536`、`observed=65537` |
| `E_JSON_SHAPE_DEPTH_LIMIT` | 生成する形状が深さ256を超える | `word`、`limit=256`、`observed=257` |
| `E_JSON_SHAPE_WORK_LIMIT` | 1検証の訪問が67,108,864を超える | `word=JSONの形状を検証する`、`limit=67108864`、`observed=67108865` |
| `E_JSON_SHAPE_PATH_LIMIT` | 生成する失敗pathがUTF-8で65,536 bytesを超える | `word=JSONの形状を検証する`、`limit=65536`、`observed` |

全て実行時、error、終了コード10です。既存のスタック不足・型不一致は`E_STACK_UNDERFLOW`と
`E_TYPE_MISMATCH`を使い、新しい診断を重ねません。検証対象の種類不一致、null不許可、必須member欠落は
診断ではなく`JSON形状失敗`です。

## 2. 優先順位

1 Callでは次の順に1件だけ診断します。

1. 実行前IR保証。通常実装では到達しないstack不足・静的型不一致
2. setterのobject形状要件
3. 生成形状の深さ上限
4. 生成形状のノード上限
5. 検証時の累積作業上限
6. 現在の失敗path上限

検証の種類不一致を見つけても、後続訪問で作業またはpath上限へ達した場合はCall全体を捕捉不能診断で
終了し、途中の失敗配列をstackへ置きません。

## 3. メッセージと非開示

メッセージは「JSON形状にはオブジェクト形状が必要です」「JSON形状のノード数が上限を超えました」
「JSON形状の深さが上限を超えました」「JSON形状検証の作業量が上限を超えました」
「JSON形状失敗のパス長が上限を超えました」を基礎文とします。

診断は入力JSON、実値、member名、path、形状全体、HTTP要求・応答本文、秘密を含めません。
`actualShapeKind`は`null|boolean|integer|decimal|string|array|object|nullable`の閉じた値です。
