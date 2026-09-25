# NARRAY機能グループ: 二次元配列の適合データ

> 本文は現行実装の規範契約です。

## 1. 全体構成

NARRAY機能グループは正常12件、失敗12件、資源10件の計34 IDを持ちます。物理データは
`tests/conformance/nested-arrays`に置き、`catalog.tsv`の順序と`manifest.tsv`の完全な物理inventoryを中央適合で
検査します。

- `NARRAY-N001`〜`NARRAY-N012`: 型、値、操作、反復、ラッパー、表示、等価性、trace、章末
- `NARRAY-F001`〜`NARRAY-F012`: 三次元、禁止葉、型なし空値、型不一致、合成内部境界
- `NARRAY-R001`〜`NARRAY-R010`: 各階層の直接長、論理葉、累積予算、stack、trace、共有、heap

期待値は本番の型parser、`ArrayValue`、表示器、trace実装から生成しません。数値境界は展開せず、閉じたrecipeと
固定値で表します。

## 2. 正常ID

| ID | 主対象 | 必須観測 |
|---|---|---|
| `NARRAY-N001` | 6葉型 | 二次元型をcheckできる |
| `NARRAY-N002` | 6空外側値 | 長さ0、辞書順、型 |
| `NARRAY-N003` | ragged・空行 | `【【1、2】、【3】、【】】` |
| `NARRAY-N004` | 外側length/get | 行数3、取出し行`【3】` |
| `NARRAY-N005` | 外側配列の一部取得 | 半開区間と元値不変 |
| `NARRAY-N006` | 外側replace/append | 新値と元値の不変性 |
| `NARRAY-N007` | 二重loop | 葉を行順に`1,2,3`と処理 |
| `NARRAY-N008` | 保存・分岐・ラッパー | 二次元型を型引数として保持 |
| `NARRAY-N009` | 等価性 | 形と葉を含む構造比較 |
| `NARRAY-N010` | 表示 | 入れ子literalの正規表示 |
| `NARRAY-N011` | trace | 各階層8要素、JSON葉の推移的伏字 |
| `NARRAY-N012` | 章末 | 操作と二重loopを公開CLIで実行 |

## 3. 失敗ID

| ID | 主対象 | 期待 |
|---|---|---|
| `NARRAY-F001` | 三次元literal | `E_NESTED_ARRAY_NOT_AVAILABLE` |
| `NARRAY-F002` | 三次元型 | `E_NESTED_ARRAY_NOT_AVAILABLE` |
| `NARRAY-F003` | 行の葉型違い | `E_ARRAY_ELEMENT_TYPE_MISMATCH` |
| `NARRAY-F004` | 一次元・二次元違い | `E_TYPE_MISMATCH` |
| `NARRAY-F005` | `任意`越しの3次元型 | `E_NESTED_ARRAY_NOT_AVAILABLE` |
| `NARRAY-F006` | `結果`越しの3次元型 | `E_NESTED_ARRAY_NOT_AVAILABLE` |
| `NARRAY-F007` | 型なし空外側 | `E_EMPTY_ARRAY_TYPE_REQUIRED` |
| `NARRAY-F008` | 型なし空内側 | `E_EMPTY_ARRAY_TYPE_REQUIRED` |
| `NARRAY-F009` | 行置換型違い | `E_TYPE_MISMATCH` |
| `NARRAY-F010` | 行追加型違い | `E_TYPE_MISMATCH` |
| `NARRAY-F011` | 内側型終端欠落 | `E_EXPECTED_ARRAY_TYPE_END` |
| `NARRAY-F012` | 合成不正型・値・IR | 終了70、公開診断へ変換しない |

ARRAY機能グループ`ARRAY-F013`と`ARRAY-F018`は二次元の正常観測へ置換します。診断コード自体は`NARRAY-F001`と`NARRAY-F002`で
三次元以上の拒否として継続します。

## 4. 資源ID

| ID | 境界 |
|---|---|
| `NARRAY-R001` | 外側直接長65,536／65,537 |
| `NARRAY-R002` | 内側直接長65,536／65,537 |
| `NARRAY-R003` | 論理葉1,000,000／1,000,001 |
| `NARRAY-R004` | 構築累積1,000,000／1,000,001 |
| `NARRAY-R005` | 比較候補処理10,000,000／10,000,001 |
| `NARRAY-R006` | 表示候補処理10,000,000／10,000,001 |
| `NARRAY-R007` | traceの各階層8／9 |
| `NARRAY-R008` | 共有行を出現位置ごとに論理計数 |
| `NARRAY-R009` | 結果stack 65,536／65,537 |
| `NARRAY-R010` | 512 MiBで最大境界を決定的に処理 |

拒否された構築・処理予約は加算せず、入力stack、元配列、保存値、両出力を維持します。

## 5. 公開経路

`NARRAY-N003`、`NARRAY-N006`、`NARRAY-N007`、`NARRAY-N012`はcheck、run、formatを各2回実行します。
`NARRAY-N001`と`NARRAY-N008`はexplain JSONも2回実行し、155組み込み項目と具体的な二次元型を固定します。
全成功formatは2回目が同じUTF-8バイト列でなければなりません。
