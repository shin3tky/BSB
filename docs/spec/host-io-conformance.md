# IO機能グループ: 適合データと章末成果物

> 本文は現行実装の規範契約です。

## 1. 場所と形式

```text
tests/conformance/host-io/
  cases.properties
  diagnostics.tsv
  resources.tsv
  messages.properties
  sources/
  canonical/
  io/
  generated/
  chapter/
```

既存どおり厳密UTF-8、BOMなしLF、末尾LF、相対パスを使います。`io/*.properties`は呼出し順の能力イベントを記述し、実時間、実端末、既定ロケール、既定タイムゾーンへ依存しません。

## 2. ケース体系

- 正常例: `IO-N001`〜`IO-N024`
- 失敗・警告例: `IO-F001`〜`IO-F026`
- 資源・内部境界: `IO-R001`〜`IO-R012`

CORE〜TEXT機能グループ全2,186テストと公開CLIバイト契約を回帰対象とし、CORE〜IO機能グループの全2,258テストを同じクリーン実行で検証します。

## 3. 正常例

| ID | 主対象 |
|---|---|
| IO-N001 | LF、CRLF、CRと終端直前行の正規化 |
| IO-N002 | 行・終端・取消の3状態判定 |
| IO-N003 | `入力行を取り出す`と既存文字列操作 |
| IO-N004 | 入力文字列から整数・小数への明示変換 |
| IO-N005 | stdoutの改行あり・なしとLF固定 |
| IO-N006 | stderrの改行あり・なしとLF固定 |
| IO-N007 | stdoutとstderrの呼出し順・独立予算 |
| IO-N008 | 空文字列行と入力終端を区別 |
| IO-N009 | 結合文字、補助平面、U+FEFF入力 |
| IO-N010 | 入力結果を利用者単語へ渡す |
| IO-N011 | 明示終了0 |
| IO-N012 | 明示終了42と先行両出力保持 |
| IO-N013 | 終了経路を除外する分岐スタック合流 |
| IO-N014 | 到達しない不足能力を呼ばない |
| IO-N015 | 8A章末相当の対話例 |
| IO-N016 | 起動引数の順序、空文字、Unicode |
| IO-N017 | CLIの`--`なしを空引数配列にする |
| IO-N018 | プログラム論理名とfile URI |
| IO-N019 | 0msと偽能力による待機 |
| IO-N020 | 単調ミリ秒の非減少と入力・待機時間の包含 |
| IO-N021 | 固定現在日時と+09:00整形 |
| IO-N022 | UTCの`Z`整形とミリ秒3桁 |
| IO-N023 | 入力・出力・時計の能力説明順 |
| IO-N024 | 8A・8B統合章末プログラム、22列トレース |

## 4. 失敗・警告例

| ID | 段階 | 主対象 | コード |
|---|---|---|---|
| IO-F001 | 静的 | 入力判定へ文字列 | `E_TYPE_MISMATCH` |
| IO-F002 | 静的 | 入力結果を表示 | `E_TYPE_MISMATCH` |
| IO-F003 | 静的 | 日時を表示 | `E_TYPE_MISMATCH` |
| IO-F004 | 静的 | `入力結果`配列 | `E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED` |
| IO-F005 | 静的 | `日時`配列 | `E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED` |
| IO-F006 | 実行時 | 終端から行取出し | `E_INPUT_RESULT_NOT_LINE` |
| IO-F007 | 実行時 | 取消から行取出し | `E_INPUT_RESULT_NOT_LINE` |
| IO-F008 | 実行時 | 入力能力不足 | `E_CAPABILITY_UNAVAILABLE` |
| IO-F009 | 実行時 | stderr能力不足 | `E_CAPABILITY_UNAVAILABLE` |
| IO-F010 | 実行時 | 入力能力失敗 | `E_CAPABILITY_FAILURE` |
| IO-F011 | 実行時 | 不正UTF-8入力 | `E_INPUT_UTF8` |
| IO-F012 | 実行時 | 行16 MiB超 | `E_INPUT_LINE_LIMIT` |
| IO-F013 | 実行時 | 入力累積64 MiB超 | `E_INPUT_TOTAL_LIMIT` |
| IO-F014 | 実行時 | stdout能力失敗 | `E_CAPABILITY_FAILURE` |
| IO-F015 | 実行時 | stderr能力失敗と診断区切り | `E_CAPABILITY_FAILURE` |
| IO-F016 | 実行時 | 終了コード-1 | `E_EXIT_CODE_RANGE` |
| IO-F017 | 実行時 | 終了コード256 | `E_EXIT_CODE_RANGE` |
| IO-F018 | 実行時 | 起動引数能力不足 | `E_CAPABILITY_UNAVAILABLE` |
| IO-F019 | 実行時 | 起動引数個数超過 | `E_ARGUMENT_COUNT_LIMIT` |
| IO-F020 | 実行時 | 引数全体64 MiB超 | `E_ARGUMENT_TOTAL_LIMIT` |
| IO-F021 | 実行時 | 不正なプログラム場所 | `E_PROGRAM_METADATA_INVALID` |
| IO-F022 | 実行時 | 負の待機 | `E_WAIT_DURATION_RANGE` |
| IO-F023 | 実行時 | 1日超の待機 | `E_WAIT_DURATION_RANGE` |
| IO-F024 | 実行時 | 待機取消 | `E_WAIT_CANCELLED` |
| IO-F025 | 実行時 | 日時範囲外 | `E_DATETIME_RANGE` |
| IO-F026 | 警告 | `終了する`後の処理 | `W_UNREACHABLE_CODE` |

IO-F001〜IO-F005はcheck/runで8、formatで0です。IO-F006〜IO-F025はcheck/formatで0、runで10です。IO-F026はcheck/runで警告を出して0、formatでは警告を出しません。

## 5. 資源・内部境界

| ID | 対象 |
|---|---|
| IO-R001 | 入力行16,777,216/16,777,217 UTF-8バイト |
| IO-R002 | 入力累積67,108,864/67,108,865生バイト |
| IO-R003 | stdout 67,108,864/67,108,865 UTF-8バイト |
| IO-R004 | stderr 67,108,864/67,108,865 UTF-8バイト |
| IO-R005 | 起動引数65,536/65,537要素 |
| IO-R006 | 引数合計67,108,864/67,108,865 UTF-8バイト |
| IO-R007 | 待機1呼出し86,400,000/86,400,001ms |
| IO-R008 | 待機累積604,800,000/604,800,001ms |
| IO-R009 | 入力・待機を除く能動時間30秒境界 |
| IO-R010 | `effect`列、入力非開示、stdout/stderr区別 |
| IO-R011 | `入力結果`・`日時`・終了移行の合成IR検証 |
| IO-R012 | 能力不足と能力失敗でのスタック・保存値・両出力原子性 |

生成定義、期待終了、診断コード、`limit`、`observed`は`generated/*.properties`と`resources.tsv`を正解とします。

## 6. 能力イベントスキーマ

`io/*.properties`は次のキーを使います。

```text
capabilities=console.input,console.output,console.error,process.exit,...
input.events=line:utf8:<escaped>,end,cancel,failure:<kind>
input.raw.hex=<UPPER_HEX>
arguments.values=<properties escaped list>
program.name=sample.bsb
program.location=file:///work/sample.bsb
sleep.results=completed:1000,cancel
monotonic.millis=0,1000,1000
wall.values=2026-08-29T14:05:06.123+09:00
```

`input.events`は意味イベントを直接与えます。行終端と厳密UTF-8デコーダ自体を試すケースは、代わりに`input.raw.hex`で標準アダプタへ生バイト列を渡します。同じ能力へ両方を指定してはなりません。

リストの区切りと値中のカンマを混同しないよう、実装ローダーはpropertiesのバックスラッシュエスケープを展開した後、``をリスト区切りとして扱います。イベント数の不足、余り、未使用キー、未知能力、未知イベントは適合データ自体の失敗です。

## 7. 章末成果物

`IO-N024`は偽入力`山田 太郎`、固定プログラム名`08-chapter.bsb`、固定日時`2026-08-29T14:05:06.123+09:00`を使います。stdoutはプロンプト、挨拶、日時を順に出し、stderrへ実行名を1行出し、`0 終了する`で終わります。

```text
stdout: お名前は？こんにちは、山田 太郎さん\n2026-08-29T14:05:06.123+09:00\n
stderr: 実行: 08-chapter.bsb\n
exit: 0 (programExit)
```

`check`、`run`、`format`の公開CLI期待をバイト単位で比較します。formatは入力非変更と2回目冪等性を要求します。トレースは内部`formatHostIo`の22列で比較し、入力本文を`effect`へ含めません。

## 8. 同値性

通常実行とトレース実行で、終了種別、終了コード、最終スタック、保存値、stdout、プログラムstderr、能力呼出し列、実行命令数、能動時間、入力・出力・配列・正規表現・待機予算が一致しなければなりません。
