# WST-TABLE機能グループ: CSV/TSVの診断と失敗境界

> 本文は現行実装の規範契約です。
>
> [厳密CSV/TSVと二次元文字列配列](delimited-tables.md)の診断、判定順、原子性を定めます。
> 統合草案[WST機能グループの診断](workspace-tables-diagnostics.md)と異なる場合、後半については本仕様を優先します。

## 1. 新規診断

すべて重要度`error`、段階`runtime`、終了10です。

| コード | 条件 | 必須fields |
|---|---|---|
| `E_DELIMITED_TEXT_EMPTY_ROW` | 非空二次元配列の直列化入力に0セル行がある | `word`、`row` |
| `E_DELIMITED_TEXT_COLUMN_COUNT_MISMATCH` | 直列化入力がragged | `word`、`row`、`expectedCount`、`actualCount` |
| `E_DELIMITED_TEXT_CELL_INVALID` | 直列化セルにNULがある | `word`、`row`、`column`、`reason` |
| `E_DELIMITED_TEXT_OUTPUT_LIMIT` | 候補出力が16 MiBを超える | `word`、`limitName`、`limit`、`observed` |
| `E_DELIMITED_TEXT_WORK_LIMIT` | 累積区切りテキスト作業が134,217,728を超える | `word`、`limitName`、`limit`、`used`、`requested` |

`row`と`column`は1始まりです。`E_DELIMITED_TEXT_CELL_INVALID`の`reason`は`nulCharacter`だけです。
message、fields、expected、actual、fixes、relatedLocationsへセル内容を含めません。

解析時の列数不一致は診断ではなく、種類`columnCountMismatch`の回復可能な解析失敗です。行数・行長・論理セル・
配列構築・文字列構築・stack・命令・時間は既存診断を使います。

## 2. 回復可能な解析失敗

次の5種類だけを`区切りテキスト解析失敗`として返します。

```text
unexpectedQuote
unexpectedCharacterAfterQuote
unterminatedQuotedField
nulCharacter
columnCountMismatch
```

各失敗値は種類、0始まりUTF-8バイト位置、1始まり物理行、1始まりUnicodeスカラー列だけを保持します。
入力、セル、期待・実際の列数、delimiter、例外を保持しません。失敗値は通常の値であり、結果分岐と専用4取出し語で
処理できます。

## 3. 捕捉不能境界

次は結果値へ変換しません。

- 入力型、stack効果、処理系内部不変条件の違反
- 入力・出力文字列、外側長、行長、論理セル、配列構築、区切り作業、stack、命令、時間上限
- 直列化入力の0セル行、ragged、NULセル
- null、未知の失敗種類、位置不変条件違反等の内部契約違反

文字列はすでにBSBの有効なUnicode値なので、UTF-8復号失敗はこの層では起きません。ファイルbytesは既存の
`UTF8バイト列を文字列に変換して結果を返す`を先に通します。

## 4. 解析の判定順

1. 命令数、能動時間、入力型、文字列不変条件
2. 既存文字列入力上限
3. `入力UTF-8 byte数 + Unicodeスカラー数`の区切りテキスト作業を一括予約
4. BOM、quote、delimiter、CRLFを1回走査し、位置と飽和した構造計数を得る
5. 最初の文法失敗を閉じた失敗値へ分類
6. 外側長、各行長、論理セル上限を順に検査
7. `セル数 + 行数`の配列構築単位と結果stackを一括予約
8. 入力文字列を成功または失敗結果1値へ置換

入力・区切り作業上限は走査前に確定するため文法失敗より優先します。走査が始まった後は、文法失敗を構造資源超過より
優先します。文法失敗時も区切り作業予約は戻しませんが、配列構築は予約しません。

同一位置の失敗分類は`nulCharacter`、`unexpectedQuote`、`unexpectedCharacterAfterQuote`、
`unterminatedQuotedField`、`columnCountMismatch`の順です。列数超過は最初の余分なfield開始、不足はrecord終端、
quoted未終端は入力末尾を指します。

## 5. 直列化の判定順

1. 命令数、能動時間、入力型、二次元配列不変条件
2. 外側先頭から最初の0セル行を`E_DELIMITED_TEXT_EMPTY_ROW`にする
3. 外側先頭から最初の列数不一致行を`E_DELIMITED_TEXT_COLUMN_COUNT_MISMATCH`にする
4. 行・列順で最初のNULセルを`E_DELIMITED_TEXT_CELL_INVALID`にする
5. 全セル入力UTF-8長と、引用符二重化・delimiter・CRLFを含む候補出力長を有限幅で測る
6. 候補出力が16 MiB超なら`E_DELIMITED_TEXT_OUTPUT_LIMIT`にする
7. `全セル入力UTF-8 byte数 + 候補出力UTF-8 byte数`の区切り作業を一括予約
8. 既存文字列構築予算と結果stackを予約
9. 出力文字列を1回構築し、入力配列を置換

空の外側配列は構造診断なしで候補長0、作業0の空文字列になります。条件をすべて通るまで完全な候補文字列を割り当てず、
入力stack、保存値、stdout、stderrを変更しません。受理済みの累積予算は後続失敗でも戻しません。

## 6. 非開示とtrace

次を人間診断、JSON診断、`dataStack`、通常trace、説明JSON、両出力へ含めません。

- 入力CSV/TSV、セル、行、表の内容・接頭辞・hash
- delimiterと、入力がCSVかTSVかを失敗値から推測させる追加情報
- ファイル由来の場合の論理名、OS path、一時名、例外

通常traceでは`区切りテキスト解析失敗:<redacted>`を使い、`結果`内でも推移的に伏せます。専用語で取り出した
安定種類と位置、診断の語名、1始まりrow/column、件数、規範上限だけは公開できます。

## 7. 原子性の必須観測

捕捉不能診断では、入力stackの同一値、全保存値、stdout、stderrを維持します。解析失敗結果は入力文字列を1結果へ、
解析成功は入力文字列を1結果へ、直列化成功は入力配列を1文字列へ原子的に置換します。traceの有無で終了、診断、stack、
保存、配列・文字列・区切り作業予算、両出力が変化してはいけません。
