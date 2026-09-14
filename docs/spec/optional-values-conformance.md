# OPT機能グループ: `任意<T>`の適合データ

> 本文は現行実装の規範契約です。

## 1. データ構成と完全性

```text
tests/conformance/optional-values/
  cases.properties
  messages.properties
  diagnostics.tsv
  resources.tsv
  sources/
  canonical/
  explain/
  chapter/
  generated/
```

中央カタログは、全ID、コマンド、対象語、source、canonical、stdout、診断行、最終状態、
生成資源を不足・重複・未使用なしで消費します。正常・失敗の各物理sourceは公開CLIの指定された
`check`、`run`、`format`へ通し、formatはBOMなし、LF、入力不変、2回目の冪等性を比較します。
OPT-F023だけは後述の理由で物理sourceを持たず、対象語、合成実行入力、構造化診断期待へ明示的に
対応付けます。
章末traceの行末にある空の`effect`列は、資源ファイルでは`\\t`と明記し、読込み時にTABへ戻して
実行結果とバイト一致させます。

ケース体系は次のとおりです。

- 正常例: `OPT-N001`〜`OPT-N016`
- 失敗・警告例: `OPT-F001`〜`OPT-F026`
- 資源・内部境界: `OPT-R001`〜`OPT-R010`

## 2. 正常例

| ID | 主対象 |
|---|---|
| OPT-N001 | 全スカラー型、配列型、入れ子任意型の受理と標準形への整形 |
| OPT-N002 | 各代表型の`任意にする`と具体化済みCall効果 |
| OPT-N003 | `任意<任意<文字列>>`の値あり化・型保存・取出し |
| OPT-N004 | 値あり判定が元値を残し、真側で取り出せること |
| OPT-N005 | JSON値ありキーを`ある(JSON)`として取得 |
| OPT-N006 | JSONヌルを`ある(JSONヌル)`として取得 |
| OPT-N007 | 欠落キーを`ない`として取得し、偽側で破棄 |
| OPT-N008 | キーのUnicodeスカラー完全一致と入力JSONの不変性 |
| OPT-N009 | 定数・変数・局所保存と同型再代入 |
| OPT-N010 | 利用者定義語の引数・戻り値、早期復帰、前方参照 |
| OPT-N011 | 条件分岐、回数・条件ループのスタック合流 |
| OPT-N012 | 値あり・値なし・入れ子任意値の等価性 |
| OPT-N013 | 表示可能な任意値の正規表示と出力上限の既存契約 |
| OPT-N014 | 非表示・非比較の任意型を保存・受渡しできること |
| OPT-N015 | `explain --json`の5語、型規則、OPT機能グループ、能力・副作用なし |
| OPT-N016 | 任意設定を読み既定値を選ぶ章末JSONプログラムとtrace |

OPT-N014は正規表現、入力結果、日時を値あり化しますが、表示・比較しません。
OPT-N012の値なしは欠落JSONキーから作り、同じ`任意<JSON>`同士を比較します。

## 3. 失敗・警告例

| ID | 主対象 | 診断 |
|---|---|---|
| OPT-F001 | 裸の`任意` | `E_OPTIONAL_ELEMENT_TYPE_REQUIRED` |
| OPT-F002 | `任意<>` | `E_EXPECTED_OPTIONAL_ELEMENT_TYPE` |
| OPT-F003 | `任意<整数` | `E_EXPECTED_OPTIONAL_TYPE_END` |
| OPT-F004 | `任意<T>`と`任意<数値>` | `E_TYPE_CONSTRAINT_NOT_ALLOWED` |
| OPT-F005 | `任意<未知型>` | `E_UNKNOWN_TYPE` |
| OPT-F006 | `配列<任意<整数>>` | `E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED` |
| OPT-F007 | `任意<整数>`と`整数`の呼出し型不一致 | `E_TYPE_MISMATCH` |
| OPT-F008 | `任意<整数>`と`任意<文字列>`の分岐合流 | `E_BRANCH_STACK_MISMATCH` |
| OPT-F009 | `任意にする`のスタック不足 | `E_STACK_UNDERFLOW` |
| OPT-F010 | `任意に値がある`のスタック不足 | `E_STACK_UNDERFLOW` |
| OPT-F011 | 判定へ非任意値 | `E_TYPE_MISMATCH` |
| OPT-F012 | `任意から値を取り出す`のスタック不足 | `E_STACK_UNDERFLOW` |
| OPT-F013 | 取出しへ非任意値 | `E_TYPE_MISMATCH` |
| OPT-F014 | `任意を捨てる`のスタック不足 | `E_STACK_UNDERFLOW` |
| OPT-F015 | 破棄へ非任意値 | `E_TYPE_MISMATCH` |
| OPT-F016 | JSON非必須取得のスタック不足 | `E_STACK_UNDERFLOW` |
| OPT-F017 | JSON非必須取得の第1入力型不一致 | `E_TYPE_MISMATCH` |
| OPT-F018 | JSON非必須取得の第2入力型不一致 | `E_TYPE_MISMATCH` |
| OPT-F019 | `任意<正規表現>`の表示 | `E_TYPE_MISMATCH` |
| OPT-F020 | `任意<日時>`の等値比較 | `E_TYPE_MISMATCH` |
| OPT-F021 | `ない`からの値取出し | `E_OPTIONAL_VALUE_ABSENT` |
| OPT-F022 | 非オブジェクトへのJSON非必須検索 | `E_JSON_KIND_MISMATCH` |
| OPT-F023 | JSON検索の作業上限 | `E_JSON_WORK_LIMIT` |
| OPT-F024 | 到達不能な空値取出し | `W_UNREACHABLE_CODE`だけ |
| OPT-F025 | `配列<任意<>>`と`任意<配列<>>`の構文回復 | 各内側の`E_EXPECTED_OPTIONAL_ELEMENT_TYPE`／`E_EXPECTED_ARRAY_ELEMENT_TYPE`だけ |
| OPT-F026 | `任意<任意<整数`の複数終端不足 | `E_EXPECTED_OPTIONAL_TYPE_END`を最内側から2件 |

全実行時失敗は終了10、プログラムstdout/stderrと保存値は呼出し前のままです。
診断TSVはcode、severity、stage、span、fields、expected、actual、fix、relatedを固定します。
OPT-F021〜022は`ProgramRunner`の最終スタックとJSON予算も独立期待へ一致させます。
OPT-F023のJSON作業上限67,108,864は実行命令上限10,000,000より大きく、1検索1作業単位の
公開プログラムでは先に命令上限へ達します。このケースは上限直前の`ExecutionBudget`を持つ
`BuiltinExecutor`へ対象語を1回適用し、入力スタック2値、stdout/stderr、使用済み作業量が
不変であることを検証します。公開CLIの当該語はOPT-N005〜008、OPT-N012〜013、OPT-N015〜016と
OPT-F016〜018、OPT-F021〜024で別途実行・診断到達させます。

## 4. 資源・内部境界

| ID | 対象 | 上限側／超過側 |
|---|---|---|
| OPT-R001 | 型構築子の入れ子深さ | 256 / 257 |
| OPT-R002 | 入れ子任意値の型名・表示・等価性・trace | 深さ256で非再帰停止 / Javaスタック非依存 |
| OPT-R003 | データスタック上の任意値 | 65,536 / 65,537 |
| OPT-R004 | 大域任意値保存 | 10,000 / 10,001 |
| OPT-R005 | 1単語の局所任意値保存 | 1,024 / 1,025 |
| OPT-R006 | JSON非必須検索の作業量 | 上限ちょうど / 1超過 |
| OPT-R007 | 欠落検索も成功時に作業量を消費 | 存在／欠落の同一課金 |
| OPT-R008 | 種別・検索作業・内包値の表示／等価作業・空値取出しの原子性 | スタック・保存・両出力・拒否予算不変 |
| OPT-R009 | trace値上限と推移的非開示 | 非開示型は状態も伏せ、実行結果・予算不変 |
| OPT-R010 | 最大ヒープ512 MiBの統合境界 | 深さ・幅・JSON検索の同時境界 |

生成入力は決定的なBSB sourceまたは合成IRレシピと独立SHA-256で同一性を確認します。
公開sourceでは字句トークン上限がデータスタック65,536値より先に発火するため、OPT-R003と
OPT-R010の幅境界は既存資源試験と同じ合成IRで検証し、レシピの件数・検索有無・型深さを
未使用フィールドなしで消費します。上限ちょうどは成功、1超過は
対応する既存またはOPT機能グループの安定診断です。`StackOverflowError`、`OutOfMemoryError`、
Java例外名、部分stdoutを許しません。

## 5. 公開面と章末成果物

追加5語は少なくとも1つのN/F適合sourceから実際に実行し、カバレッジ集計で
`UNIT_ONLY`・`F_ONLY`・`UNCOVERED`にしません。失敗しない包装・判定・破棄へ人工的な
実行時失敗を作らず、正常証跡を必須にします。

章末OPT-N016は、JSONの`mode`が文字列、JSONヌル、欠落の3入力を処理し、値あり側と既定値側を
明示的に分岐します。`check`、`run`、`format`、`explain --json`、通常・非開示traceを
独立期待へバイト一致させます。既存必須取得とJSON機能グループの全JSON契約も回帰実行します。
