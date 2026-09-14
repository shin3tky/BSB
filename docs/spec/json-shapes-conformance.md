# JSHAPE機能グループ: 最小JSON形状検証の適合データ

> [形状検証](json-shapes.md)および[診断](json-shapes-diagnostics.md)と一体です。

## 1. データ構成と独立性

物理データは`tests/conformance/json-shapes/`へ置き、manifest、case表、source、canonical、expected、
generated境界指定を厳格loaderで対応付けます。期待値は本番実装から生成せず、JSON Pointer escape、
種類判定、訪問数、失敗順、境界値を独立oracleでも検算します。

全ケースIDは`JSHAPE-Nnnn`、`JSHAPE-Fnnn`、`JSHAPE-Rnnn`です。

## 2. 正常例

| ID | 主対象 |
|---|---|
| JSHAPE-N001 | 5葉、配列、object、nullableの構築と15語の静的説明 |
| JSHAPE-N002 | HTTP応答の`data[].id`必須整数、`name`必須文字列、`next`任意nullable文字列 |
| JSHAPE-N003 | AI構造化出力の`summary`必須文字列、`items[].label`必須文字列、`score`任意小数 |
| JSHAPE-N004 | 必須memberの値ありと、未記載追加memberの許可 |
| JSHAPE-N005 | 任意memberの欠落、null、値ありをnullableとの組合せで区別 |
| JSHAPE-N006 | JSON整数と小数を相互に受理しない |
| JSHAPE-N007 | 配列要素を添字昇順で一様に検査 |
| JSHAPE-N008 | objectの設定順と再設定時の位置保持・必須性移動 |
| JSHAPE-N009 | root、`~`、`/`、空member名、Unicodeを含むJSON Pointer path |
| JSHAPE-N010 | 複数不一致のdepth-first pre-orderと不一致ノードでの短絡 |
| JSHAPE-N011 | 256件での正常打切りと1件以上の失敗配列 |
| JSHAPE-N012 | 成功時に入力と構造的に同じJSONを返し、入力・形状を変更しない |
| JSHAPE-N013 | 4 accessorの閉じた値と既存配列反復による失敗処理 |
| JSHAPE-N014 | format冪等性、保存・引数・戻り値・分岐合流 |
| JSHAPE-N015 | 通常traceの推移的非開示と、明示accessor出力 |
| JSHAPE-N016 | HTTP/AIの2参照例で既存語列より判定・分岐を削減する章末成果物 |

## 3. 失敗例

| ID | 主対象 | 診断または失敗種類 |
|---|---|---|
| JSHAPE-F001 | 必須member欠落 | `missingRequiredKey` |
| JSHAPE-F002 | 任意member以外のnull | `nullNotAllowed` |
| JSHAPE-F003 | 葉種類不一致 | `kindMismatch` |
| JSHAPE-F004 | 配列を期待してobject | `kindMismatch` |
| JSHAPE-F005 | objectを期待して配列 | `kindMismatch` |
| JSHAPE-F006 | 入れ子配列の複数不一致とpath | 閉じた失敗列 |
| JSHAPE-F007 | 葉へ必須setter | `E_JSON_SHAPE_OBJECT_REQUIRED` |
| JSHAPE-F008 | 配列へ任意setter | `E_JSON_SHAPE_OBJECT_REQUIRED` |
| JSHAPE-F009 | nullable objectへsetter | `E_JSON_SHAPE_OBJECT_REQUIRED` |
| JSHAPE-F010 | 構築・検証・accessorのstack不足 | `E_STACK_UNDERFLOW` |
| JSHAPE-F011 | 各入力の静的型不一致 | `E_TYPE_MISMATCH` |
| JSHAPE-F012 | 既存必須取得・種類判定・解析語の診断が不変 | 既存コード |

## 4. 資源・非開示・原子性

| ID | 対象 | 境界 |
|---|---|---|
| JSHAPE-R001 | 形状深さ | 256 / 257 |
| JSHAPE-R002 | 形状ノード数 | 65,536 / 65,537 |
| JSHAPE-R003 | 検証訪問数 | 67,108,864 / 67,108,865 |
| JSHAPE-R004 | path UTF-8長 | 65,536 / 65,537 bytes |
| JSHAPE-R005 | 失敗件数 | 255 / 256 / 257候補を256で打切り |
| JSHAPE-R006 | setter置換でノード数が減る場合と増える場合 | 置換後全体で判定 |
| JSHAPE-R007 | 種類不一致時の子短絡と作業量 | 未訪問を課金しない |
| JSHAPE-R008 | 5捕捉不能診断のstack・保存・出力・予算原子性 | 全観測不変 |
| JSHAPE-R009 | marker入りJSON・key・本文・秘密の全公開面非開示 | marker検出0 |
| JSHAPE-R010 | 通常実行とtrace実行 | 結果・出力・予算同値 |
| JSHAPE-R011 | 最大JSON、深い形状、256失敗の複合 | 最大ヒープ512 MiB |
| JSHAPE-R012 | 既存JSON作業予算との独立累積 | 相互迂回なし |

## 5. 完了ゲート

- 全40 IDと全variantを中央カタログが過不足なく1回以上消費する
- 新規5診断を`CONFORMANCE`とし、既存の仕様対象診断と組み込み語を回帰する
- 新規15語を組み込み語coverageへ接続し、`UNCOVERED`を0に保つ
- `check`、`check --json`、`run`、`format`、`explain --json`とfat JARを各2回比較する
- clean、Spotless、全JUnit、JaCoCo、Javadoc、Python、監査を最大ヒープ512 MiBで通す
