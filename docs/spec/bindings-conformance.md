# BIND機能グループ: 適合データと章末成果物

> 本文は現行実装の規範契約です。

## 1. 適合データの場所

BIND機能グループ固有の機械可読データを次に置きます。

```text
tests/conformance/bindings/
  cases.properties
  diagnostics.tsv
  resources.tsv
  messages.properties
  sources/
  canonical/
  generated/
  chapter/
```

文字コード、改行、propertiesとTSVのエスケープ、構造化フィールドの形式は[language-core-conformance.md](language-core-conformance.md)と同じです。相対パスは`tests/conformance/bindings`を基準にします。

BIND機能グループのメッセージカタログはCORE・FLOWのカタログへ追加します。複数の機能グループに同じキーがあればハーネス自身の失敗です。

## 2. ケース体系

- 正常例: `BIND-N001`〜`BIND-N020`
- 失敗・警告例: `BIND-F001`〜`BIND-F030`
- 資源・内部境界: `BIND-R001`〜`BIND-R005`

CORE・FLOW機能グループの全862テストも同じテスト実行で維持します。BIND機能グループの追加によって、既存のcanonical、標準出力、診断位置、トレースを変更しません。

成功するすべてのformatケースは、期待出力をもう一度formatして同じバイト列になることを暗黙に要求します。

## 3. 正常例

| ID | 主対象 | コマンド |
|---|---|---|
| `BIND-N001` | 大域整数定数の参照 | check, run, format |
| `BIND-N002` | 大域整数変数への代入 | check, run, format |
| `BIND-N003` | 先行大域値を使う純粋初期値 | check, run |
| `BIND-N004` | 局所定数 | check, run |
| `BIND-N005` | 局所変数への代入 | check, run |
| `BIND-N006` | 4既存型の推論 | check, run |
| `BIND-N007` | 大域変数の呼出し間保持 | check, run |
| `BIND-N008` | 局所変数の呼出しごとの初期化 | check, run |
| `BIND-N009` | 再帰フレーム間の局所値分離 | check, run |
| `BIND-N010` | 条件分岐の子スコープ | check, run |
| `BIND-N011` | 兄弟スコープの同名局所 | check, run |
| `BIND-N012` | 子スコープから外側局所を読む | check, run |
| `BIND-N013` | 子スコープから外側変数へ代入 | check, run |
| `BIND-N014` | ループ再到達時の局所再初期化 | check, run |
| `BIND-N015` | 早期復帰と到達した局所値 | check, run |
| `BIND-N016` | 全角英数字・NFCの名前参照 | check, run, format |
| `BIND-N017` | コメントを含む複数行初期値 | check, run, format |
| `BIND-N018` | 代替区切りから代入の標準形 | check, run, format |
| `BIND-N019` | 等価・比較による真偽型推論 | check, run |
| `BIND-N020` | 大域・局所・制御・説明の統合 | check, run, format + 内部束縛表 |

正常例の`check`と`format`は標準エラーが空です。`run`の標準出力は`cases.properties`、BIND-N020の内部束縛表は`bindingReport.source`で指定したTSVに固定します。内部束縛表は公開CLIコマンドではありません。

## 4. 失敗例と警告例

| ID | 段階 | 主対象 | 診断 |
|---|---|---|---|
| `BIND-F001` | 構文 | 宣言名と`は`の隣接違反 | `E_DECLARATION_ADJACENCY` |
| `BIND-F002` | 構文 | 宣言種別不足 | `E_EXPECTED_DECLARATION_KIND` |
| `BIND-F003` | 構文 | 宣言ヘッダー内コメント | `E_DECLARATION_HEADER_COMMENT_NOT_ALLOWED` |
| `BIND-F004` | 構文 | 宣言終端不足 | `E_EXPECTED_DECLARATION_END` |
| `BIND-F005` | 構文 | 単独の宣言終端 | `E_UNEXPECTED_DECLARATION_END` |
| `BIND-F006` | 構文 | 代入の`を`不足 | `E_EXPECTED_ASSIGNMENT_VALUE_PARTICLE` |
| `BIND-F007` | 構文 | 代入先不足 | `E_EXPECTED_ASSIGNMENT_TARGET` |
| `BIND-F008` | 構文 | 代入先後の`に`不足 | `E_EXPECTED_ASSIGNMENT_TARGET_PARTICLE` |
| `BIND-F009` | 静的 | 初期値なし | `E_INITIALIZER_VALUE_MISSING` |
| `BIND-F010` | 静的 | 初期値が2値 | `E_INITIALIZER_VALUE_COUNT` |
| `BIND-F011` | 静的 | 初期値から利用者単語を呼ぶ | `E_INITIALIZER_CALL_NOT_ALLOWED` |
| `BIND-F012` | 構文 | 初期値内の制御構文 | `E_INITIALIZER_ELEMENT_NOT_ALLOWED` |
| `BIND-F013` | 静的 | 初期値の自己参照 | `E_REFERENCE_BEFORE_INITIALIZATION` |
| `BIND-F014` | 静的 | 後方の大域値を初期値から参照 | `E_REFERENCE_BEFORE_INITIALIZATION` |
| `BIND-F015` | 静的 | 局所宣言前の参照 | `E_REFERENCE_BEFORE_DECLARATION` |
| `BIND-F016` | 静的 | 子スコープ終了後の参照 | `E_BINDING_OUT_OF_SCOPE` |
| `BIND-F017` | 静的 | 大域名重複 | `E_DUPLICATE_NAME` |
| `BIND-F018` | 静的 | 同じ局所スコープの重複 | `E_DUPLICATE_NAME` |
| `BIND-F019` | 静的 | 正規化後の宣言名重複 | `E_DUPLICATE_NAME` |
| `BIND-F020` | 静的 | 局所名による大域値の隠蔽 | `E_NAME_SHADOWING` |
| `BIND-F021` | 静的 | 局所名による大域単語の隠蔽 | `E_NAME_SHADOWING` |
| `BIND-F022` | 静的 | 内側局所名による外側局所の隠蔽 | `E_NAME_SHADOWING` |
| `BIND-F023` | 静的 | 定数への代入 | `E_ASSIGN_TO_CONSTANT` |
| `BIND-F024` | 静的 | 単語を代入先にする | `E_ASSIGNMENT_TARGET_NOT_VARIABLE` |
| `BIND-F025` | 静的 | 未定義の代入先 | `E_UNDEFINED_ASSIGNMENT_TARGET` |
| `BIND-F026` | 静的 | 代入値不足 | `E_ASSIGNMENT_STACK_UNDERFLOW` |
| `BIND-F027` | 静的 | 代入型不一致 | `E_ASSIGNMENT_TYPE_MISMATCH` |
| `BIND-F028` | 静的 | 局所宣言前の代入 | `E_REFERENCE_BEFORE_DECLARATION` |
| `BIND-F029` | 静的 | 純粋初期値内の入力不足 | `E_STACK_UNDERFLOW` |
| `BIND-F030` | 警告 | 到達不能な宣言と派生診断抑止 | `W_UNREACHABLE_CODE` |

`BIND-F001`〜`BIND-F008`と`BIND-F012`は全コマンドで終了コード9です。`BIND-F009`〜`BIND-F011`と`BIND-F013`〜`BIND-F029`はcheckとrunで終了コード8、formatで成功します。`BIND-F011`は利用者定義単語と副作用組み込み単語の2診断を持ちます。`BIND-F030`はcheckとrunで警告を出して終了コード0、formatでは警告を出しません。

構造化診断の完全な期待値は`diagnostics.tsv`、人間向け文は`messages.properties`に固定します。

## 5. 資源・内部境界

| ID | 対象 | 受理側 | 拒否側 |
|---|---|---|---|
| `BIND-R001` | 大域束縛 | 10,000個 | 10,001個、`E_GLOBAL_BINDING_LIMIT` |
| `BIND-R002` | 1単語の局所束縛 | 1,024個 | 1,025個、`E_LOCAL_BINDING_LIMIT` |
| `BIND-R003` | プログラム全体の束縛 | 65,536個 | 65,537個、`E_BINDING_LIMIT` |
| `BIND-R004` | 保存領域IRを含む総IR命令数 | 250,000命令 | 250,001命令、`E_IR_LIMIT` |
| `BIND-R005` | Loadによるデータスタック | 65,536値 | 65,537値、`E_DATA_STACK_LIMIT` |

巨大境界は`generated/*.properties`から決定的に生成します。BIND-R003〜BIND-R005は他のソース、トークン、トップレベル、IR上限へ先に達しないよう、対象コンポーネントへ合成AST、検査済みプログラム、合成IRを直接渡します。

## 6. 生成記述

BIND機能グループで使用する生成器名は次です。

```text
top-level-bindings     指定数の大域宣言と必須メインを生成する
local-bindings         1単語へ指定数の局所宣言を持つ合成ASTを生成する
program-bindings       指定総数の束縛を持つ検査用合成ASTを生成する
storage-ir             指定個数のLoad/Store系命令を持つ合成IRを生成する
load-stack             指定個数のLoadを実行する合成IRを生成する
```

生成器はロケール、乱数、現在時刻、OS改行へ依存しません。生成結果の個数が宣言値と違う場合はハーネス自身の失敗です。

## 7. 章末プログラム

BIND機能グループの章末プログラムは`chapter/04-chapter.bsb`です。次を示します。

- 大域定数`単価`と`個数`
- 呼出しごとに初期化する局所定数`小計`
- 大域変数`累計`への代入
- 回数ループをまたぐ大域値の保持
- 束縛IDによる参照先説明
- Initialize、Load、Storeを含む命令トレース

期待標準出力は`chapter/04-chapter.stdout`、束縛説明は`chapter/04-chapter.bindings.tsv`、命令トレースは`chapter/04-chapter.trace.tsv`です。

## 8. 束縛説明

束縛説明は[bindings.md](bindings.md)の列をヘッダー順に持つBOMなしUTF-8、LFのTSVです。行は値参照・代入先の元ソース位置順です。宣言そのものと単語呼出しは行にしません。

同じ束縛を複数回実行しても、説明行は静的な参照位置ごとに1行です。ループ反復回数や再帰深さによって増えません。

## 9. BIND機能グループのトレース

FLOW機能グループの15列へ、[bindings-diagnostics.md](bindings-diagnostics.md)の6列を末尾追加します。Initialize、Load、Store以外では追加列を`-`にします。

大域初期化は`<大域初期化>`という内部単語名で先に現れ、最後の大域宣言終端位置を持つ`Return`で終わります。その後、呼出深さ1で`メイン`を開始します。sequenceは両者を通じて連続します。

通常実行とトレース実行で、終了コード、標準出力、最終データスタック、大域保存値が同一でなければなりません。
