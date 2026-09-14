# ARRAY機能グループ: 適合データと章末成果物

> 本文は現行実装の規範契約です。
>
> NARRAY機能グループ注記: [NARRAY機能グループの中央適合](nested-arrays-conformance.md)により、二次元配列を拒否していた歴史caseは
> 二次元の受理caseと三次元の拒否caseへ置換されました。その他のARRAY機能グループcaseは引き続き有効です。

## 1. 適合データの場所

```text
tests/conformance/arrays/
  cases.properties
  diagnostics.tsv
  resources.tsv
  messages.properties
  sources/
  canonical/
  generated/
  chapter/
```

文字コード、改行、propertiesとTSVのエスケープ、構造化フィールドは[language-core-conformance.md](language-core-conformance.md)と同じです。相対パスは`tests/conformance/arrays`を基準にします。ARRAY機能グループのメッセージキーが既存機能グループと重複した場合はハーネス自身の失敗です。

## 2. ケース体系

- 正常例: `ARRAY-N001`〜`ARRAY-N022`
- 失敗・警告例: `ARRAY-F001`〜`ARRAY-F033`
- 資源・内部境界: `ARRAY-R001`〜`ARRAY-R006`

CORE〜BIND機能グループの全1287テストも同じテスト実行で維持します。成功する全formatケースは、期待出力を再度formatして同じバイト列になることを暗黙に要求します。

## 3. 正常例

| ID | 主対象 | コマンド |
|---|---|---|
| `ARRAY-N001` | 整数配列リテラルと表示 | check, run, format |
| `ARRAY-N002` | 4スカラー要素型の推論 | check, run |
| `ARRAY-N003` | 4個の型付き空配列値 | check, run |
| `ARRAY-N004` | 純粋な要素式と名前参照 | check, run |
| `ARRAY-N005` | 配列の長さ | check, run |
| `ARRAY-N006` | 先頭・末尾要素の参照 | check, run |
| `ARRAY-N007` | 空・中間・全体の半開区間 | check, run |
| `ARRAY-N008` | 要素置換と元配列の不変性 | check, run |
| `ARRAY-N009` | 末尾追加と元配列の不変性 | check, run |
| `ARRAY-N010` | 配列の構造的等価性 | check, run |
| `ARRAY-N011` | 配列変数を新しい値へ置換 | check, run |
| `ARRAY-N012` | 利用者単語の具体的配列型 | check, run, format |
| `ARRAY-N013` | 非空配列の順次反復 | check, run, format |
| `ARRAY-N014` | 空配列の0回反復 | check, run |
| `ARRAY-N015` | 配列反復の`続ける` | check, run |
| `ARRAY-N016` | 配列反復の`打ち切る` | check, run |
| `ARRAY-N017` | 条件分岐と配列反復の入れ子 | check, run |
| `ARRAY-N018` | コメントつき配列の標準形 | check, run, format |
| `ARRAY-N019` | 全角コンマから標準の読点 | check, run, format |
| `ARRAY-N020` | 文字・文字列要素の表示エスケープ | check, run |
| `ARRAY-N021` | 9要素以上のトレース省略 | check, run + 内部trace |
| `ARRAY-N022` | 合計・件数・各点数の章末統合 | check, run, format + 内部trace |

正常例のcheckとformatは標準エラーが空です。runの標準出力は`cases.properties`、ARRAY-N021とARRAY-N022のトレースは`trace.source`で指定したTSVへ固定します。

## 4. 失敗例と警告例

| ID | 段階 | 主対象 | 診断 |
|---|---|---|---|
| `ARRAY-F001` | 構文 | 配列終端不足 | `E_EXPECTED_ARRAY_END` |
| `ARRAY-F002` | 構文 | 対応しない配列終端 | `E_UNEXPECTED_ARRAY_END` |
| `ARRAY-F003` | 構文 | 先頭の要素区切り | `E_EXPECTED_ARRAY_ELEMENT` |
| `ARRAY-F004` | 構文 | 連続する要素区切り | `E_EXPECTED_ARRAY_ELEMENT` |
| `ARRAY-F005` | 構文 | 末尾の要素区切り | `E_EXPECTED_ARRAY_ELEMENT` |
| `ARRAY-F006` | 構文 | 配列型の要素型不足 | `E_EXPECTED_ARRAY_ELEMENT_TYPE` |
| `ARRAY-F007` | 構文 | 配列型終端不足 | `E_EXPECTED_ARRAY_TYPE_END` |
| `ARRAY-F008` | 構文 | 配列反復終端不足 | `E_EXPECTED_LOOP_END` |
| `ARRAY-F009` | 静的 | 型なし空配列 | `E_EMPTY_ARRAY_TYPE_REQUIRED` |
| `ARRAY-F010` | 静的 | 要素式が0値 | `E_ARRAY_ELEMENT_VALUE_MISSING` |
| `ARRAY-F011` | 静的 | 要素式が2値 | `E_ARRAY_ELEMENT_VALUE_COUNT` |
| `ARRAY-F012` | 静的 | 要素型混在 | `E_ARRAY_ELEMENT_TYPE_MISMATCH` |
| `ARRAY-F013` | 静的 | 入れ子配列値 | `E_NESTED_ARRAY_NOT_AVAILABLE` |
| `ARRAY-F014` | 静的 | 要素式から利用者単語呼出し | `E_ARRAY_ELEMENT_CALL_NOT_ALLOWED` |
| `ARRAY-F015` | 静的 | 要素式から副作用単語呼出し | `E_ARRAY_ELEMENT_CALL_NOT_ALLOWED` |
| `ARRAY-F016` | 構文 | 要素式内の制御構文 | `E_ARRAY_ELEMENT_NOT_ALLOWED` |
| `ARRAY-F017` | 静的 | bare配列型 | `E_ARRAY_ELEMENT_TYPE_REQUIRED` |
| `ARRAY-F018` | 静的 | 多次元配列型 | `E_NESTED_ARRAY_NOT_AVAILABLE` |
| `ARRAY-F019` | 静的 | 異なる配列型の代入 | `E_ASSIGNMENT_TYPE_MISMATCH` |
| `ARRAY-F020` | 静的 | 配列操作の入力不足 | `E_STACK_UNDERFLOW` |
| `ARRAY-F021` | 静的 | 長さ操作へ非配列 | `E_TYPE_MISMATCH` |
| `ARRAY-F022` | 静的 | 置換要素型不一致 | `E_TYPE_MISMATCH` |
| `ARRAY-F023` | 静的 | 追加要素型不一致 | `E_TYPE_MISMATCH` |
| `ARRAY-F024` | 静的 | 配列反復の入力不足 | `E_ARRAY_LOOP_INPUT_UNDERFLOW` |
| `ARRAY-F025` | 静的 | 配列反復へ非配列 | `E_ARRAY_LOOP_INPUT_TYPE_MISMATCH` |
| `ARRAY-F026` | 静的 | 反復本体が要素を残す | `E_LOOP_STACK_MISMATCH` |
| `ARRAY-F027` | 静的 | `続ける`地点が要素を残す | `E_LOOP_STACK_MISMATCH` |
| `ARRAY-F028` | 実行時 | 負の添字 | `E_ARRAY_INDEX_OUT_OF_BOUNDS` |
| `ARRAY-F029` | 実行時 | 長さと同じ添字 | `E_ARRAY_INDEX_OUT_OF_BOUNDS` |
| `ARRAY-F030` | 実行時 | 置換の範囲外添字 | `E_ARRAY_INDEX_OUT_OF_BOUNDS` |
| `ARRAY-F031` | 実行時 | 半開区間の負開始・超過終了 | `E_ARRAY_RANGE_OUT_OF_BOUNDS` |
| `ARRAY-F032` | 実行時 | 開始が終了より後 | `E_ARRAY_RANGE_OUT_OF_BOUNDS` |
| `ARRAY-F033` | 警告 | 到達不能配列の派生診断抑止 | `W_UNREACHABLE_CODE` |

ARRAY-F001〜ARRAY-F008とARRAY-F016は全コマンドで終了コード9です。ARRAY-F009〜ARRAY-F015とARRAY-F017〜ARRAY-F027はcheckとrunで終了コード8、formatで成功します。ARRAY-F028〜ARRAY-F032はcheckとformatで成功し、runだけ終了コード10です。ARRAY-F033は警告だけで終了コード0です。

## 5. 資源・内部境界

| ID | 対象 | 受理側 | 拒否・省略側 |
|---|---|---|---|
| `ARRAY-R001` | 配列リテラル長 | 65,536要素 | 65,537要素、`E_ARRAY_LENGTH_LIMIT` |
| `ARRAY-R002` | 動的な配列長 | 65,536要素 | 65,537要素、`E_ARRAY_LENGTH_LIMIT` |
| `ARRAY-R003` | 要素構築単位 | 1,000,000 | 1,000,001、`E_ARRAY_CONSTRUCTION_LIMIT` |
| `ARRAY-R004` | 要素処理単位 | 10,000,000 | 10,000,001、`E_ARRAY_ELEMENT_OPERATION_LIMIT` |
| `ARRAY-R005` | 配列IRを含む総IR命令 | 250,000 | 250,001、`E_IR_LIMIT` |
| `ARRAY-R006` | トレース表示 | 8要素・16コードポイント | 9要素目以降・17文字目以降を省略、非開示時は全体を伏せる |

ARRAY-R001〜ARRAY-R005は`generated/*.properties`から決定的に生成します。ARRAY-R002〜ARRAY-R005は他のソース、トークン、命令上限へ先に達しないよう合成済み値・IR・実行文脈を対象コンポーネントへ直接渡せます。

## 6. 生成器

```text
array-literal        指定数の整数要素を持つ配列リテラルを生成する
array-append         指定初期長の配列へ1要素追加する合成実行を作る
array-construction   指定構築単位直前の実行文脈と構築操作を作る
array-operations     指定処理単位直前の実行文脈と処理操作を作る
array-ir             指定数のBuildArray系命令を持つ合成IRを作る
array-trace-value    指定要素数・文字数・開示方針の配列値を整形する
```

生成器はロケール、時刻、乱数、OS改行、マップ反復順へ依存しません。

## 7. 章末プログラム

`chapter/05-chapter.bsb`は5件の整数点数について次を示します。

- 非空の整数配列リテラルと具体的な宣言型
- 配列反復で要素を順に表示しながら合計する
- 配列の長さを件数として表示する
- 配列を不変値として保持し、変数には整数の累計だけを使う
- `ArrayLoopStart`・`ArrayLoopNext`と配列値を含むトレース

期待標準出力は合計`400`、件数`5`、続いて`70`、`80`、`90`、`75`、`85`を各LFつきで出力します。平均は計算しません。

## 8. ARRAY機能グループのトレース

列はBIND機能グループの21列をそのまま使います。配列反復の内部状態は`controlBefore`・`controlAfter`へ`array:現在添字/長さ`として記録します。

ARRAY-N021は9要素配列をLoadして、8要素と`…(+1)`だけが現れることを固定します。ARRAY-R006は要素数、長い文字列要素、非開示ポリシーを内部フォーマッタ境界として検証します。

通常実行とトレース実行で、終了コード、標準出力、最終データスタック、大域保存値、配列資源予算の成否が同一でなければなりません。
