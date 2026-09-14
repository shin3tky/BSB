# CONN機能グループ: 論理接続の適合データ・テスト計画

> 本文は現行実装の規範契約です。

規範は[論理接続とパラメータ付き能力](logical-connections.md)と
[診断・ホスト境界](logical-connections-diagnostics.md)です。本書と`tests/conformance/logical-connections`は
本番実装より先に期待を固定し、処理系出力を期待値の生成元にしません。

## 1. 物理構成とデータ契約

```text
tests/conformance/logical-connections/
  catalog.tsv
  cases.tsv
  policies.tsv
  resolver.tsv
  resources.tsv
  manifest.tsv
  sources/*.bsb
  canonical/*.bsb
  expected/diagnostics.tsv
  expected/states.tsv
  expected/trace.tsv
  expected/parameterized-capabilities.json
  chapter/15-chapter.bsb
  README.md
```

`catalog.tsv`はCONN-N001〜010、CONN-F001〜012、CONN-R001〜010をこの順で過不足なく登録します。
`case_id + variant`と各fixture IDは表ごとに一意です。catalogの全evidenceは実在し、全詳細行は
catalog IDへ所属し、物理ファイルはmanifestとREADME・manifest自身の和集合に一致しなければなりません。

`cases.tsv`はresolverの1回の呼出しについて、能力有無、閉じた応答、設定不正理由、終了分類、診断、
呼出し回数、状態不変、能力イベントを独立期待にします。`policies.tsv`は有効方針と10不正理由を、
`resolver.tsv`は偽resolverの決定的な応答列を定義します。自由文による失敗は持たせません。

`resources.tsv`は宣言数、URI・origin長、命令・時間、説明出力、最大ヒープの境界を仕様数値で直接固定します。
`expected/diagnostics.tsv`は新12診断のstage、対象、必須fieldsをJSONで保持します。
`expected/states.tsv`と`expected/trace.tsv`は成功・失敗時の原子性、能力回数、非開示を完全一致で照合します。

`expected/parameterized-capabilities.json`は、全組み込み辞書を含む外側文書ではなく、独立版1の
`parameterizedCapabilities`値そのものの完全な期待です。位置を省略せず、宣言、uses、summary、
userWordsの規範順を固定し、外側`explain --json`の全バイトgoldenにも組み込みます。

## 2. 正常例 CONN-N001〜CONN-N010

| ID | 必須観測 |
|---|---|
| CONN-N001 | 単一のトップレベル宣言と確認、空スタック効果、確認成功 |
| CONN-N002 | 複数宣言、宣言より前の参照、宣言ソース順、同名重複要求の統合 |
| CONN-N003 | 利用者語を通る直接・推移要求、自己・相互再帰の最小固定点 |
| CONN-N004 | 条件、反復、構造的到達不能と、要求の保守的上限集合 |
| CONN-N005 | NFC、全角英数の正規化、元spellingと正規nameの分離 |
| CONN-N006 | `RESOLVED`と有効方針、resolver 1回、状態・既存予算不変 |
| CONN-N007 | 外側版1、平坦能力順、内側版1の宣言・利用・直接／推移要求 |
| CONN-N008 | 宣言・静的引数・コメントの標準形と2回目の冪等性 |
| CONN-N009 | 未使用宣言と`メイン`から到達しない語を説明へ保持し、summaryから除外 |
| CONN-N010 | 章末プログラム、2接続、公開5コマンド、通常・非開示trace |

## 3. 失敗例 CONN-F001〜CONN-F012

| ID | 入力・観測 | 期待 |
|---|---|---|
| CONN-F001 | 宣言に初期値 | `E_LOGICAL_CONNECTION_DECLARATION_VALUE` |
| CONN-F002 | 単語本体内の宣言 | `E_LOGICAL_CONNECTION_DECLARATION_SCOPE` |
| CONN-F003 | 宣言10,001個 | `E_LOGICAL_CONNECTION_LIMIT`、上限後の派生抑止 |
| CONN-F004 | 確認語の`<`欠落 | `E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT_START` |
| CONN-F005 | 空または非名前の第1引数 | `E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT` |
| CONN-F006 | 0個、2個、入れ子の引数 | `E_LOGICAL_CONNECTION_ARGUMENT_COUNT` |
| CONN-F007 | `>`欠落とEOF回復 | `E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT_END`だけ |
| CONN-F008 | 宣言されていない静的名前 | `E_UNDECLARED_LOGICAL_CONNECTION` |
| CONN-F009 | 接続名を通常の値・語として参照 | `E_LOGICAL_CONNECTION_REFERENCE_NOT_ALLOWED` |
| CONN-F010 | resolverの`NOT_CONFIGURED` | `E_LOGICAL_CONNECTION_NOT_CONFIGURED` |
| CONN-F011 | resolverの`DENIED` | `E_LOGICAL_CONNECTION_ACCESS_DENIED` |
| CONN-F012 | 明示`INVALID`と不正な`RESOLVED` | `E_LOGICAL_CONNECTION_CONFIGURATION_INVALID` |

既存`E_DUPLICATE_NAME`、`E_RESERVED_NAME`、`E_NAME_SHADOWING`、confusable警告、
`E_CAPABILITY_UNAVAILABLE`、`E_CAPABILITY_FAILURE`も対照に含めます。新診断の必須fields以外の全公開表現と
終了値はfixtureへ固定し、URI、資格情報参照、ホスト自由文を期待へ複写しません。

## 4. 資源・内部境界 CONN-R001〜CONN-R010

| ID | 固定する境界・対照 |
|---|---|
| CONN-R001 | 論理接続宣言10,000／10,001、値binding上限との独立性 |
| CONN-R002 | 基底URI 8,192／8,193バイト、scheme、userinfo、query、fragment、path、IP literal |
| CONN-R003 | origin 1／0、32／33、各8,192／8,193、合計262,144／1超過、重複、基底origin不足 |
| CONN-R004 | methodの全許可値、空、未知値。auth 4分類、不透明参照の有無 |
| CONN-R005 | 接続・応答timeout 1／30,000、0／30,001ミリ秒 |
| CONN-R006 | 要求・応答size 1／67,108,864、0／67,108,865バイト |
| CONN-R007 | redirect `deny`／未知、retry `none`／未知、複数不正の最初の理由 |
| CONN-R008 | 命令10,000,000／1超過、時間30秒／1 ns超過、能力なし・能力失敗・契約違反 |
| CONN-R009 | URI、origin、資格情報参照、秘密、例外本文の全公開面からの非漏えい |
| CONN-R010 | 64 MiB説明上限、深い呼出しグラフ、10,000宣言、traceを512 MiBで統合 |

境界は本番定数を読んで期待を変えません。能力なしは呼出し0回、能力を呼んだ全経路は1回です。
能力契約違反を利用者診断へ変換せず、内部終了70にします。OOM、StackOverflow、Java例外名を期待終了に
しません。

## 5. 章末成果物 CONN-N010

章末プログラムは`顧客管理API`と`通知API`を宣言し、前者を利用者語経由、後者を`メイン`から直接確認します。
両方の偽resolver応答が`RESOLVED`ならstdoutとプログラムstderrは空、最終スタックと保存値も空です。
能力イベントは宣言順ではなく実行順の2件です。

静的説明は両接続を宣言順で持ち、利用者語の直接要求、`メイン`の直接要求と推移要求、summaryの2要求を
区別します。標準CLIの`run`はresolverを持たないため能力不足になる一方、check、check JSON、format、
explain JSONはホストを呼ばず成功する対照を固定します。

## 6. 受入ゲート

1. `python3 tools/logical_connections_data.py --check`が全表、JSON、manifest、参照、非開示規則を検査する。
2. `ConnectionConformanceDataTest`が同じ物理資源をclasspathから独立に厳格読込みする。
3. データ検査だけを実行し、新構文が未実装であることを成功扱いしない。
4. 全32 IDを中央カタログから消費し、未使用・欠落・重複を許さない。
5. 最終的に新1語を`FULL`、新12診断を規範的証跡へ接続し、既存分類を低下させない。

期待と実装が食い違った場合は規範を再確認し、理由を記録せずにgoldenを再生成しません。
