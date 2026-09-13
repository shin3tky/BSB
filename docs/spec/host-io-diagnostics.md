# IO機能グループ: 入出力・実行環境診断

> 本文は現行実装の規範契約です。

CORE〜TEXT機能グループの診断形式、ソース位置、100件上限、終了コード、プレビュー、原子性を引き継ぎます。

## 1. 静的診断

新しい固定型効果には既存の`E_STACK_UNDERFLOW`、`E_TYPE_MISMATCH`、`E_WORD_EFFECT_MISMATCH`を使います。`入力結果`と`日時`を配列要素、`表示可能`、`等しい`へ渡した場合も、既存の具体型診断を使います。

`終了する`は全プログラム終了の制御移行です。到達可能な通常出口だけをスタック合流へ使い、後続範囲には既存の`W_UNREACHABLE_CODE`を1件だけ出します。到達不能な入出力・能力呼出しから派生する型・能力・資源診断は出しません。

## 2. 実行時診断

すべて終了コード10です。

| コード | 条件 | 主なフィールド |
|---|---|---|
| `E_CAPABILITY_UNAVAILABLE` | 到達した単語が要求する能力がない | `word`, `capability` |
| `E_CAPABILITY_FAILURE` | 入力・出力・時計能力が失敗した | `word`, `capability`, `operation`。ホスト例外本文は含めない |
| `E_INPUT_UTF8` | 完成した入力行が厳密UTF-8でない | 行内の最初の不正バイト位置、最大4バイトの16進プレビュー |
| `E_INPUT_LINE_LIMIT` | 入力行の値が16 MiB超 | `limit`, `observed` |
| `E_INPUT_TOTAL_LIMIT` | 生入力累積が64 MiB超 | 現在値、追加値、`limit`, `observed` |
| `E_INPUT_RESULT_NOT_LINE` | 終端・取消から行を取り出した | `state` |
| `E_ERROR_OUTPUT_LIMIT` | プログラムstderrが64 MiB超 | 現在値、追加値、`limit`, `observed` |
| `E_EXIT_CODE_RANGE` | 終了コードが0..255外 | 入力値、有効範囲 |
| `E_ARGUMENT_COUNT_LIMIT` | 起動引数が65,536個超 | `limit`, `observed` |
| `E_ARGUMENT_TOTAL_LIMIT` | 引数全体が64 MiB超 | `limit`, `observed` |
| `E_PROGRAM_METADATA_INVALID` | 論理名・場所URIが契約外 | 項目名、理由。値は64コードポイントプレビュー |
| `E_WAIT_DURATION_RANGE` | 待機値が0..86,400,000外 | 入力値、有効範囲 |
| `E_WAIT_TOTAL_LIMIT` | 累積待機要求が604,800,000ms超 | 現在値、追加値、`limit`, `observed` |
| `E_WAIT_CANCELLED` | 実行環境が待機を取り消した | `word=待つ` |
| `E_DATETIME_RANGE` | 日時の年またはUTCオフセットが表現範囲外 | 年、オフセット、有効範囲 |

入力引数1個またはプログラム識別文字列の16 MiB超は既存の`E_STRING_UTF8_LIMIT`、stdoutは既存の`E_OUTPUT_LIMIT`です。

## 3. 判定順序

### 入力

1. `console.input`能力の有無
2. 能力が返した失敗、取消、終端、完成行の区別
3. 生入力累積64 MiB
4. 厳密UTF-8
5. 行文字列16 MiB
6. `入力結果`を積むデータスタック上限

取消と終端は正常値なので3〜5を検査しません。完成行を受け取った後の3〜6で失敗しても物理入力は戻しませんが、スタック、保存値、両出力へ部分結果を残しません。

### 出力

1. 入力型と表示文字列生成上限
2. 対象能力の有無
3. 対象チャネルの累積上限
4. 能力への1回の書込み
5. 成功後に入力値をスタックから除く

能力委譲後の失敗は`E_CAPABILITY_FAILURE`です。処理系が診断自体をstderrへ書けない場合だけ、CLI入出力エラー3が最終結果を上書きします。

### 終了、引数、待機、日時

- `終了する`: 整数型、0..255、`process.exit`能力、終了確定の順
- 起動引数: 能力、要素数、各文字列、合計UTF-8長、配列構築予算、スタック上限の順
- `待つ`: 能力、入力範囲、累積上限、能力結果、成功時の累積加算の順
- 日時: 能力、エポックミリ秒の変換可能性、UTCオフセット、文字列結果の順

## 4. 能動実行時間

30秒上限は、入力待ちと`待つ`の能力委譲区間を除いた単調時間です。能力呼出しの直前と直後を必ず読み、差分を除外量へ加えます。時計の減少、加算オーバーフロー、能力から契約外の値が返る場合は利用者診断へ推測変換せず内部エラー70です。

## 5. トレースと非開示

IO機能グループ内部トレースは既存21列を先頭に保ち、末尾へ`effect`列を1つ追加します。値は次です。

```text
console.output:<UPPER_HEX>
console.error:<UPPER_HEX>
console.input:line
console.input:end
console.input:cancel
process.exit:<0..255>
process.arguments:<count>
program.identity:name
program.identity:location
time.sleep:<milliseconds>
time.monotonic
time.wall
```

入力本文、起動引数、プログラム場所、日時値を`effect`へ出しません。`入力結果`の行内容は既定ポリシーで`入力結果:<redacted>`とし、終端・取消は状態だけを表示できます。より厳しいホストポリシーは抽出後の文字列を含む全値を非開示にできます。トレースのために能力を追加呼出しせず、資源予算も消費しません。

通常実行とトレース実行の能力呼出し列が一致しなければなりません。
