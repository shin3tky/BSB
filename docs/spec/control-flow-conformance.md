# FLOW機能グループ: 適合データと章末成果物

> 本文は現行実装の規範契約です。

## 1. 適合データの場所

FLOW機能グループ固有の機械可読データを次に置きます。

```text
tests/conformance/control-flow/
  cases.properties
  diagnostics.tsv
  resources.tsv
  messages.properties
  sources/
  canonical/
  generated/
  chapter/
```

文字コード、改行、propertiesとTSVのエスケープ、構造化フィールドの形式は[language-core-conformance.md](language-core-conformance.md)と同じです。相対パスは`tests/conformance/control-flow`を基準にします。

FLOW機能グループのメッセージカタログはCORE機能グループのカタログへ追加して読み込みます。両方に同じキーがあればハーネス自身の失敗です。

## 2. ケース体系

- 正常例: `FLOW-N001`〜`FLOW-N020`
- 失敗・警告例: `FLOW-F001`〜`FLOW-F026`
- 資源・内部境界: `FLOW-R001`〜`FLOW-R004`

CORE機能グループの全ケースも同じテスト実行で維持します。FLOW機能グループの追加によって、CORE機能グループのcanonical、標準出力、診断位置を変更しません。

成功するすべてのformatケースは、期待出力をもう一度formatして同じバイト列になることを暗黙に要求します。

## 3. 正常例

| ID | 主対象 | コマンド |
|---|---|---|
| `FLOW-N001` | elseなし・真 | check, run, format |
| `FLOW-N002` | elseなし・偽 | check, run |
| `FLOW-N003` | elseあり・真 | check, run |
| `FLOW-N004` | elseあり・偽 | check, run, format |
| `FLOW-N005` | 入れ子条件分岐 | check, run |
| `FLOW-N006` | 分岐が同じ型の値を返す | check, run |
| `FLOW-N007` | 0回ループ | check, run |
| `FLOW-N008` | 1回ループ | check, run |
| `FLOW-N009` | 複数回ループ | check, run, format |
| `FLOW-N010` | 計算した反復回数 | check, run |
| `FLOW-N011` | 最初から偽の条件ループ | check, run |
| `FLOW-N012` | 条件ループからの脱出 | check, run, format |
| `FLOW-N013` | 回数ループの継続 | check, run |
| `FLOW-N014` | 回数ループの脱出 | check, run |
| `FLOW-N015` | 単語末尾の早期復帰 | check, run |
| `FLOW-N016` | 分岐片側からの早期復帰 | check, run |
| `FLOW-N017` | 小さい比較が真 | check, run |
| `FLOW-N018` | 小さい比較が偽・同値 | check, run |
| `FLOW-N019` | 分岐と回数ループの入れ子 | check, run |
| `FLOW-N020` | 空本体・コメント・正規字下げ | format |

正常例の`check`と`format`は標準エラーが空です。`run`の標準出力は`cases.properties`に固定します。

## 4. 失敗例と警告例

| ID | 段階 | 主対象 | 診断 |
|---|---|---|---|
| `FLOW-F001` | 構文 | 対応しないelse | `E_UNEXPECTED_ELSE` |
| `FLOW-F002` | 構文 | 重複else | `E_DUPLICATE_ELSE` |
| `FLOW-F003` | 構文 | if終端不足 | `E_EXPECTED_IF_END` |
| `FLOW-F004` | 構文 | 過剰なif終端 | `E_UNEXPECTED_BLOCK_END` |
| `FLOW-F005` | 構文 | 対応しないループ終端 | `E_UNEXPECTED_LOOP_END` |
| `FLOW-F006` | 構文 | 回数ループ終端不足 | `E_EXPECTED_LOOP_END` |
| `FLOW-F007` | 構文 | 条件区切り不足 | `E_EXPECTED_LOOP_CONDITION_SEPARATOR` |
| `FLOW-F008` | 構文 | 重複条件区切り | `E_UNEXPECTED_LOOP_SEPARATOR` |
| `FLOW-F009` | 構文 | 257段目 | `E_SYNTAX_DEPTH_LIMIT` |
| `FLOW-F010` | 静的 | if条件不足 | `E_CONDITION_STACK_UNDERFLOW` |
| `FLOW-F011` | 静的 | if条件型 | `E_CONDITION_TYPE_MISMATCH` |
| `FLOW-F012` | 静的 | elseあり値数不一致 | `E_BRANCH_STACK_MISMATCH` |
| `FLOW-F013` | 静的 | elseあり型不一致 | `E_BRANCH_STACK_MISMATCH` |
| `FLOW-F014` | 静的 | elseなし出口不一致 | `E_BRANCH_STACK_MISMATCH` |
| `FLOW-F015` | 静的 | 回数不足 | `E_REPEAT_COUNT_UNDERFLOW` |
| `FLOW-F016` | 静的 | 回数型 | `E_REPEAT_COUNT_TYPE_MISMATCH` |
| `FLOW-F017` | 静的 | 回数ループ本体不一致 | `E_LOOP_STACK_MISMATCH` |
| `FLOW-F018` | 静的 | 条件ループの真偽不足 | `E_LOOP_CONDITION_MISMATCH` |
| `FLOW-F019` | 静的 | 条件ループの余分な値 | `E_LOOP_CONDITION_MISMATCH` |
| `FLOW-F020` | 静的 | ループ外脱出 | `E_BREAK_OUTSIDE_LOOP` |
| `FLOW-F021` | 静的 | ループ外継続 | `E_CONTINUE_OUTSIDE_LOOP` |
| `FLOW-F022` | 静的 | 早期復帰の効果 | `E_RETURN_EFFECT_MISMATCH` |
| `FLOW-F023` | 静的 | 比較の入力不足 | `E_STACK_UNDERFLOW` |
| `FLOW-F024` | 静的 | 比較の入力型 | `E_TYPE_MISMATCH` |
| `FLOW-F025` | 実行時 | 負の反復回数 | `E_NEGATIVE_REPEAT_COUNT` |
| `FLOW-F026` | 警告 | 到達不能コード | `W_UNREACHABLE_CODE` |

`FLOW-F001`〜`FLOW-F009`は全コマンドで終了コード9です。`FLOW-F010`〜`FLOW-F024`はcheckとrunで終了コード8、formatで成功します。`FLOW-F025`はcheckとformatで成功し、runだけ終了コード10です。`FLOW-F026`はcheckとrunで警告を出し終了コード0、formatでは警告を出しません。

構造化診断の完全な期待値は`diagnostics.tsv`、人間向け文は`messages.properties`に固定します。

## 5. 資源・内部境界

| ID | 対象 | 受理側 | 拒否側 |
|---|---|---|---|
| `FLOW-R001` | 構文深さ | 256段 | 257段、`E_SYNTAX_DEPTH_LIMIT` |
| `FLOW-R002` | 制御IR命令数 | 250,000命令 | 250,001命令、`E_IR_LIMIT` |
| `FLOW-R003` | 後退辺の実行命令数 | 10,000,000命令 | 10,000,001命令、`E_INSTRUCTION_LIMIT` |
| `FLOW-R004` | 回数の符号境界 | 0回 | -1回、`E_NEGATIVE_REPEAT_COUNT` |

巨大境界は`generated/*.properties`から決定的に生成します。FLOW-R002とFLOW-R003は、他のソース・トークン・IR上限へ先に達しないよう対象コンポーネントへ合成入力または合成IRを直接渡します。

## 6. 生成記述

FLOW機能グループで使用する生成器名は次です。

```text
nested-control       指定深さのelseなし条件分岐を生成する
control-ir           指定個数の制御命令を持つ検査済み合成IRを生成する
loop-execution       指定個数のJump系命令を実行する合成IRを生成する
counted-loop-source  指定した10進回数を持つ最小ソースを生成する
```

生成器はロケール、乱数、現在時刻、OS改行へ依存しません。生成結果の個数が宣言値と違う場合はハーネス自身の失敗です。

## 7. 章末プログラム

FLOW機能グループの章末プログラムは`chapter/03-chapter.bsb`です。BIND機能グループの変数とNUM機能グループの剰余演算を先取りせず、次を示します。

- elseあり条件分岐
- 回数ループ
- 利用者定義単語の呼出し
- 分岐選択と内部反復状態のトレース

期待標準出力は`chapter/03-chapter.stdout`、期待命令トレースは`chapter/03-chapter.trace.tsv`です。

## 8. FLOW機能グループのトレース

CORE機能グループの列へ、次の4列を末尾追加します。

```text
controlBefore
controlAfter
target
branchTaken
```

`controlBefore`と`controlAfter`は、回数ループの内部状態を外側から内側の順に`[count:残り/初期値]`で表します。残り回数は、現在実行中の反復を含みます。内部状態がなければ`[]`です。利用者データスタックにはこの値を含めません。呼出先の単語を実行中も、停止中の呼出元が持つループ状態を外側の状態として表示します。

`target`は制御移動先を`単語名:1始まり命令番号`で表し、飛び先のない命令では空です。`branchTaken`は制御命令だけ`true`または`false`、その他では空です。

`CountedLoopStart`は、正数なら内部状態を作って次命令へ進むため`branchTaken=false`、0なら本体後へ移動するため`true`とします。`CountedLoopNext`は次反復へ戻る場合`true`、終了して次命令へ進む場合`false`です。

通常実行とトレース実行で、終了コード、標準出力、最終データスタックが同一でなければなりません。
