# NUM機能グループ: 適合データと章末成果物

> 本文は現行実装の規範契約です。

## 1. 適合データの場所

```text
tests/conformance/numerics/
  cases.properties
  diagnostics.tsv
  resources.tsv
  messages.properties
  sources/
  canonical/
  generated/
  chapter/
```

文字コード、改行、相対パス、propertiesとTSVのエスケープはARRAY機能グループと同じです。成功する全formatケースは2回目の冪等性と入力非変更を暗黙に要求します。

## 2. ケース体系

- 正常例: `NUM-N001`〜`NUM-N020`
- 失敗・警告例: `NUM-F001`〜`NUM-F020`
- 資源・内部境界: `NUM-R001`〜`NUM-R006`

CORE〜ARRAY機能グループの全1,738テストを同じ実行で維持します。

## 3. 正常例

| ID | 主対象 |
|---|---|
| `NUM-N001` | 小数リテラル実行、負の0と末尾0の値表示 |
| `NUM-N002` | 0.1を含む正確な小数加減乗 |
| `NUM-N003` | 小数の数値的等価性と順序比較 |
| `NUM-N004` | 4符号組合せの整数床除算 |
| `NUM-N005` | 4符号組合せの剰余 |
| `NUM-N006` | 商と剰余の同時取得とスタック順 |
| `NUM-N007` | 既定28桁の小数除算と整数・小数の混在入力 |
| `NUM-N008` | 明示精度と最近接偶数・四捨五入 |
| `NUM-N009` | 0方向・正方向・負方向を含む整数丸め |
| `NUM-N010` | 整数・小数の絶対値 |
| `NUM-N011` | 同型数値の最小値・最大値 |
| `NUM-N012` | 整数から小数、整数値小数から整数への正確変換 |
| `NUM-N013` | 丸め方法値の等価性と表示 |
| `NUM-N014` | 小数配列リテラル、型付き空配列、表示 |
| `NUM-N015` | 小数配列の参照・置換・追加・反復 |
| `NUM-N016` | 小数の定数・変数・利用者単語効果 |
| `NUM-N017` | 配列要素式内の純粋な数値演算と明示変換 |
| `NUM-N018` | 小数字句を保持するformatと実行表示の分離 |
| `NUM-N019` | 小数と小数配列の21列トレース表現 |
| `NUM-N020` | 税込価格、平均、整数丸めの章末統合 |

## 4. 失敗・警告例

| ID | 段階 | 主対象 | 診断 |
|---|---|---|---|
| `NUM-F001` | 静的 | 整数と小数を混在して加算 | `E_TYPE_MISMATCH` |
| `NUM-F002` | 静的 | 小数へ整数の商を適用 | `E_TYPE_MISMATCH` |
| `NUM-F003` | 静的 | 整数と小数の比較 | `E_TYPE_MISMATCH` |
| `NUM-F004` | 静的 | 丸め方法入力の型違い | `E_TYPE_MISMATCH` |
| `NUM-F005` | 静的 | 整数・小数の混在配列 | `E_ARRAY_ELEMENT_TYPE_MISMATCH` |
| `NUM-F006` | 静的 | `配列<丸め方法>` | `E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED` |
| `NUM-F007` | 静的 | 整数変数へ小数を代入 | `E_ASSIGNMENT_TYPE_MISMATCH` |
| `NUM-F008` | 静的 | 変換元型の違い | `E_TYPE_MISMATCH` |
| `NUM-F009` | 静的 | 利用者効果中の型制約`数値` | `E_TYPE_CONSTRAINT_NOT_ALLOWED` |
| `NUM-F010` | 実行時 | `割った商`のゼロ除算 | `E_DIVISION_BY_ZERO` |
| `NUM-F011` | 実行時 | `割った剰余`のゼロ除算 | `E_DIVISION_BY_ZERO` |
| `NUM-F012` | 実行時 | `割った商と剰余`のゼロ除算 | `E_DIVISION_BY_ZERO` |
| `NUM-F013` | 実行時 | 小数の負の0による除算 | `E_DIVISION_BY_ZERO` |
| `NUM-F014` | 実行時 | ゼロ除算と精度不正の優先順位 | `E_DIVISION_BY_ZERO` |
| `NUM-F015` | 実行時 | 明示精度0 | `E_DIVISION_PRECISION_OUT_OF_RANGE` |
| `NUM-F016` | 実行時 | 明示精度4,097 | `E_DIVISION_PRECISION_OUT_OF_RANGE` |
| `NUM-F017` | 実行時 | 小数部を持つ値の正確な整数変換 | `E_DECIMAL_NOT_INTEGER` |
| `NUM-F018` | 実行時 | 小数スケール結果上限 | `E_DECIMAL_SCALE_LIMIT` |
| `NUM-F019` | 実行時 | 失敗前stdoutとスタック原子性 | `E_DIVISION_BY_ZERO` |
| `NUM-F020` | 警告 | 到達不能数値処理の派生診断抑止 | `W_UNREACHABLE_CODE` |

NUM-F001〜NUM-F009はcheckとrunが8、formatが0です。NUM-F010〜NUM-F019はcheckが0、runが10、formatが0です。NUM-F020は警告だけで全コマンド0です。

## 5. 資源・内部境界

| ID | 対象 | 受理側 | 拒否・省略側 |
|---|---|---|---|
| `NUM-R001` | 小数結果の有効桁 | 65,536 | 65,537、`E_DECIMAL_RESULT_PRECISION_LIMIT` |
| `NUM-R002` | 小数結果のスケール絶対値 | 65,536 | 65,537、`E_DECIMAL_SCALE_LIMIT` |
| `NUM-R003` | 明示除算精度 | 1と4,096 | 0と4,097、`E_DIVISION_PRECISION_OUT_OF_RANGE` |
| `NUM-R004` | 小数から整数への結果桁数 | 65,536 | 65,537、`E_INTEGER_RESULT_LIMIT` |
| `NUM-R005` | 小数トレース値 | 32コードポイント | 33以降を省略、非開示時は全体を伏せる |
| `NUM-R006` | 数値型つき合成IR | 全具体効果一致 | 型違い定数・効果・丸め値を内部エラー |

NUM-Rは`generated/*.properties`から合成値または合成IRを作り、巨大なソースや中間値を不要に確保しません。生成器は時刻、乱数、ロケール、OS改行、マップ反復順に依存しません。

## 6. 診断TSV

ARRAY機能グループと同じ11列です。

```text
case_id command severity code line column fields expected actual fix related
```

数値入力フィールドは64コードポイント以下なら正規値表記、それを超える場合は[numerics-diagnostics.md](numerics-diagnostics.md)の決定的プレビューを使います。`E_DIVISION_BY_ZERO`は`word`、`dividendPreview`、`divisorPreview`を必須とし、結果上限は`limitName`、`limit`、`observed`、`word`、利用可能な全入力プレビューを持ちます。

## 7. 章末プログラム

`chapter/06-chapter.bsb`は3件の税抜価格を小数配列に保持し、次を示します。

- 配列反復による正確な合計
- 税率0.1の乗算と税込合計
- 件数による既定28桁の平均
- `四捨五入`による平均の整数丸め
- 小数値、小数配列、丸め方法を含む21列トレース

期待stdoutは次をLF区切りで出し、末尾もLFです。

```text
2500.0
2750.0
833.3333333333333333333333333
833
```

## 8. トレース

列はARRAY機能グループの21列をそのまま使います。`NUM-N019`と章末例で、小数定数のPush、同型演算、数値入力を具体化したCall、小数配列反復、整数丸めを固定します。

通常実行とトレース実行で、終了コード、stdout、最終データスタック、大域・局所保存値、実行命令数、配列予算が同一でなければなりません。
