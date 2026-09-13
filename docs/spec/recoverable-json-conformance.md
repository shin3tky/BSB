# RJSON機能グループ: 回復可能なJSON解析の適合データ・テスト計画

> 本文は現行実装の規範契約です。
>
> 規範は[回復可能なJSON解析](recoverable-json-parsing.md)と
> [診断・失敗境界](recoverable-json-diagnostics.md)です。

## 1. カタログと独立期待

```text
tests/conformance/recoverable-json/
  catalog.tsv
  cases.tsv
  resources.tsv
  manifest.tsv
  sources/*.bsb
  canonical/*.bsb
  expected/states.tsv
  chapter/14-chapter.bsb
  README.md
```

`catalog.tsv`はRJSON-N 10件、RJSON-F 10件、RJSON-R 12件、計32 IDをこの順で過不足なく登録します。
`case_id + variant`を各詳細表の一意キーとし、同じIDに複数条件がある場合はvariantを分けます。

`cases.tsv`は次の15列です。

```text
case_id variant input_utf8_hex outcome payload_utf8_hex failure_kind utf8_offset line column json_work_delta json_construction_delta diagnostic input_preserved target_words evidence
```

- `input_utf8_hex`は解析へ渡す入力のUTF-8バイト列です。空入力は空欄とし、`-`で代用しません。
- `outcome`は`success`、`recoverable`、`diagnostic`です。成功payloadは決定的なコンパクトJSON、
  回復可能失敗は種類と3位置、診断失敗はコードと入力維持を独立期待にします。
- `json_work_delta`と`json_construction_delta`は対象呼出し1回だけの増分です。先行命令は含めません。
- 元入力や処理系の例外本文を期待へ複製しません。入力バイトはテスト入力としてだけ保持し、
  実行結果、失敗値、診断、トレースへ現れないことを別に照合します。

`resources.tsv`は公開sourceだけでは現実的に到達できない境界を含む、決定的な合成入力レシピです。
上限値を本番定数から読み取って期待を作らず、仕様値を表へ直接固定します。

`expected/states.tsv`は代表公開sourceの最終データスタック、保存、stdout、stderr、能力、診断を
JSONで表します。文字列はTSV中のJSON文字列として保持し、部分一致で合格にしません。

`manifest.tsv`はrecoverable-json配下の機械資源をSHA-256とUTF-8バイト数で閉じます。READMEとmanifest自身だけを
自己参照から除外します。読取り専用`tools/recoverable_json_data.py --check`は欠落、余剰、重複、未知列、
不正UTF-8、BOM、CR、末尾LF欠落、ハッシュ差異を拒否します。

## 2. 正常例 RJSON-N001〜RJSON-N010

| ID | 必須観測 |
|---|---|
| RJSON-N001 | 7 JSON値種別の成功結果、既存と同じ正規値、作業・構築課金 |
| RJSON-N002 | 13失敗種類、失敗結果、診断なし、解析後の継続、JSON構築0 |
| RJSON-N003 | 種類・UTF-8バイト位置・行・列の4取出し、ASCII・補助文字・CRLF・EOF位置 |
| RJSON-N004 | 既存結果7語による成功／失敗分岐、両枝の同型合流 |
| RJSON-N005 | 定数、変数、大域・局所保存、利用者語の入出力、再帰・制御合流 |
| RJSON-N006 | `任意<JSON解析失敗>`と`結果<T,JSON解析失敗>`の型・値、推移的非表示・非比較 |
| RJSON-N007 | 純粋初期値、format冪等性、予約名、版1 explainの新型・5語・RJSON機能グループ・能力なし |
| RJSON-N008 | 同じ有効／不正入力に対する既存診断版と新結果版の対照 |
| RJSON-N009 | 失敗結果の判定・取出し後に後続命令が実行され、終了0となる |
| RJSON-N010 | 章末プログラム、公開5コマンド、最終状態、通常・非開示trace |

RJSON-N002は13種類を13 variantsとして登録し、1つの代表だけで種類網羅を主張しません。
RJSON-N003は補助文字のUTF-8バイト数とUnicodeスカラー列、CRLFの1改行扱いを独立variantにします。

## 3. 失敗・警告例 RJSON-F001〜RJSON-F010

| ID | 入力・観測 | 期待 |
|---|---|---|
| RJSON-F001 | 結果返却解析の入力不足 | `E_STACK_UNDERFLOW` |
| RJSON-F002 | 結果返却解析へ非文字列 | `E_TYPE_MISMATCH` |
| RJSON-F003 | 4取出し語の入力不足 | 各`E_STACK_UNDERFLOW` |
| RJSON-F004 | 4取出し語へ別型 | 各`E_TYPE_MISMATCH` |
| RJSON-F005 | 失敗型と、それを含む任意・結果を表示 | `E_TYPE_MISMATCH`、原因型を保持 |
| RJSON-F006 | 失敗型と、それを含む任意・結果を等値比較 | `E_TYPE_MISMATCH` |
| RJSON-F007 | `配列<JSON解析失敗>`の注釈・要素式 | `E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED` |
| RJSON-F008 | 新型1名・新語5名の語／束縛再定義、型名呼出し | `E_RESERVED_NAME`／`E_NAME_NOT_CALLABLE` |
| RJSON-F009 | 既存解析語へ構文不正・重複キー | 既存`E_JSON_SYNTAX`／`E_JSON_DUPLICATE_KEY`、終了10 |
| RJSON-F010 | `戻る`後の新5語の不足・型不一致 | `W_UNREACHABLE_CODE`だけ |

静的診断はコードだけでなくstage、span、全fields、expected、actual、fixes、related、終了値、
stdout／stderrを既存公開契約と比較します。新しい診断コードはありません。

## 4. 資源・内部境界 RJSON-R001〜RJSON-R012

| ID | 固定する境界・対照 |
|---|---|
| RJSON-R001 | ASCII、3バイト文字、4バイト文字、LF、CRLF、EOFの位置計算 |
| RJSON-R002 | JSON数値4,096／4,097桁、入力16,777,216／1超過。超過は診断 |
| RJSON-R003 | JSON容器深さ256／257。超過は`E_JSON_DEPTH_LIMIT` |
| RJSON-R004 | JSON値ノード250,000／250,001。超過は`E_JSON_NODE_LIMIT` |
| RJSON-R005 | 1 JSON配列65,536／65,537要素。超過は`E_JSON_ARRAY_LENGTH_LIMIT` |
| RJSON-R006 | 1 JSONオブジェクト65,536／65,537メンバー。超過は`E_JSON_OBJECT_MEMBER_LIMIT` |
| RJSON-R007 | JSON作業67,108,864ちょうど／1超過。拒否予約は0追加、入力不変 |
| RJSON-R008 | JSON構築1,000,000ちょうど／1超過。先行入力作業は維持、入力不変 |
| RJSON-R009 | 命令10,000,000／1超過、実行時間30秒／1 ns超過。結果へ変換しない |
| RJSON-R010 | データスタック65,536値での1対1置換、65,537値目の既存上限との対照 |
| RJSON-R011 | 固有入力の失敗値・診断・`dataStack`・通常trace・保存trace非開示 |
| RJSON-R012 | 最大ヒープ512 MiBで深さ・幅・共有JSON・13失敗値・traceを統合実行 |

上限超過が文法不正と重なる入力では、JSON機能グループの解析優先順位どおり最初の結果または診断を固定します。
OOM、StackOverflow、Java例外名、部分結果を期待終了にしません。ホストの実時間で合否を決めず、
偽時計と明示予算を使用します。

## 5. 章末成果物 RJSON-N010

章末プログラムは有効JSON、不正JSON、重複キーの3入力を結果版で解析し、成功なら正規JSON、
失敗なら種類と`行:列`を表示します。期待stdoutは次です。

```text
{"mode":"ok"}
expectedObjectKey@1:2
duplicateKey@1:8
```

全呼出しは外部能力を持たず、章末全体の能力は`console.output`だけです。最終スタックは空、
保存値はありません。正確な命令数、出力バイト、JSON作業・構築単位は物理source確定後に
`expected/states.tsv`へ独立計算で固定します。

## 6. 受入ゲート

1. データ検査: `tools/recoverable_json_data.py --check`とその単体試験。
2. 型・静的検査: 新型、5語、保存・合流・入れ子、非表示・非比較・配列拒否。
3. runtime: 7成功、13失敗、4取出し、既存解析版対照、予算・原子性。
4. 公開CLI: check、check JSON、run、format、explain JSONの終了と全バイト。
5. 非開示: 結果、保存、trace、診断全表現、共通`dataStack`。
6. 全回帰: 先行する実装済み機能グループ、182診断、既存119語、公開JSON版1、最大ヒープ512 MiB。

期待を本番実装の出力から自動更新しません。実装と期待が食い違った場合は、規範を再確認してどちらを
直すか記録し、単にgoldenを再生成して合格させません。
