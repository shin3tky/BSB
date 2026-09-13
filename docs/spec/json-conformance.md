# JSON機能グループ: JSON適合データと章末成果物

> 本文は現行実装の規範契約です。

本仕様は[JSON値仕様](json-values.md)と[JSON診断仕様](json-diagnostics.md)を、
機械可読データ、Java単体テスト、CLI章末成果物で検証する契約を定めます。

## 1. 場所と形式

```text
tests/conformance/json/
  cases.properties
  diagnostics.tsv
  normal-corpus.tsv
  failure-corpus.tsv
  resources.tsv
  messages.properties
  sources/
  canonical/
  json/
  generated/
  chapter/
```

既存機能グループと同じく、厳密UTF-8、BOMなし、LF、末尾LF、ルートからの相対パスを使います。
`json/`は入力JSONと期待する正規JSONを保持し、パーサー自身の出力を期待値の作成に使いません。
`sources/`はケース固有の`.bsb`と正常実行の期待`.stdout`、`canonical/`はformatの期待値を
保持します。巨大入力は`generated/*.properties`から決定的に生成します。

`cases.properties`の`schema.version`は`2`です。`case.ids`はN/F 58 IDを仕様順で列挙し、
`normal.codec.ids`、`normal.cli.ids`、`normal.format.ids`、`normal.explain.ids`、
`normal.chapter.ids`、`failure.codec.ids`、`failure.cli.ids`へ重複なく分割します。R 18 IDは
`resources.tsv`と`generated/resources.properties`で別に管理します。

## 2. ケース体系

- 正常例: `JSON-N001`〜`JSON-N030`
- 失敗例: `JSON-F001`〜`JSON-F028`
- 資源・内部境界: `JSON-R001`〜`JSON-R018`

IDは欠番や別名を許しません。中央ハーネスはN/F 58 IDとR 18 IDの合計76件を照合し、未知キー、
重複ID、分類間重複、重複source/stdout、未使用資源、欠落資源を適合データ自体の失敗とします。

JSON-N010〜JSON-N027はそれぞれ固有の`source`、`commands=check,run,format`、終了コード、
run stdout、canonical、空の最終スタック、JSON構築・作業予算、観測目的、対象語を宣言します。
JSON-F017〜JSON-F028はそれぞれ固有の`source`、`commands=check,run`、終了コード、診断ID、
JSON構築・作業予算、最終大域値、対象語を宣言します。共有プログラムへ複数IDを対応させません。

`diagnostics.tsv`は次の14列をこの順で持ちます。

```text
case_id kind command severity code stage line column fields expected actual fix related reason
```

実ファイルではタブ区切りです。codecケースの複数の安定理由は`reason`列、CLI実行時診断の
対象語は`fields`内の`word=<正規名>`として表し、両者を同じ意味の列として扱いません。

## 3. 正常例

| ID | 主対象 |
|---|---|
| JSON-N001 | 7値種別のトップレベル解析 |
| JSON-N002 | SP、HTAB、LF、CRだけをJSON空白とする |
| JSON-N003 | 全エスケープとサロゲートペア |
| JSON-N004 | キーと文字列のUnicodeスカラー保持 |
| JSON-N005 | 整数と小数の値種別保持 |
| JSON-N006 | `-0`、`-0.0`、`1.2300`、`1e2`の正規出力 |
| JSON-N007 | 空配列と空オブジェクト |
| JSON-N008 | 入力順のオブジェクト直列化 |
| JSON-N009 | `parse(serialize(value))`の値種別と構造的等価性 |
| JSON-N010 | BSB真偽からJSONへの変換と取出し |
| JSON-N011 | BSB整数からJSONへの変換と取出し |
| JSON-N012 | BSB小数からJSONへの変換と取出し |
| JSON-N013 | BSB文字列からJSONへの変換と取出し |
| JSON-N014 | 7値種別判定が元JSON値を保持する |
| JSON-N015 | JSON配列の長さと要素取得 |
| JSON-N016 | JSON配列の空・中間・全半開区間 |
| JSON-N017 | JSON配列の置換と追加の不変性 |
| JSON-N018 | JSON配列と`配列<JSON>`の往復 |
| JSON-N019 | `各要素について`で`配列<JSON>`を反復 |
| JSON-N020 | JSONオブジェクトの要素数とキー一覧 |
| JSON-N021 | キー存在検査で欠落とJSON `null`を区別 |
| JSON-N022 | 必須取得でJSON `null`と非`null`を保持 |
| JSON-N023 | 既存キー更新の位置保持 |
| JSON-N024 | 新規キー追加とキー削除の順序契約 |
| JSON-N025 | JSON配列の要素順依存等価性 |
| JSON-N026 | JSONオブジェクトのキー順非依存等価性 |
| JSON-N027 | JSON値と`配列<JSON>`の表示 |
| JSON-N028 | フォーマットでJSON型名と正規語を保持 |
| JSON-N029 | `explain --json`の33語、固定型規則、機能グループ`9` |
| JSON-N030 | 章末JSON変換プログラムと非開示トレース |

## 4. 失敗例

| ID | 主対象 | コード |
|---|---|---|
| JSON-F001 | 空入力 | `E_JSON_SYNTAX` |
| JSON-F002 | BOM、全角空白、コメント | `E_JSON_SYNTAX` |
| JSON-F003 | 末尾の余分値 | `E_JSON_SYNTAX` |
| JSON-F004 | 末尾コンマ | `E_JSON_SYNTAX` |
| JSON-F005 | 未エスケープ制御文字 | `E_JSON_SYNTAX` |
| JSON-F006 | 未知エスケープと不正`U+XXXX` | `E_JSON_SYNTAX` |
| JSON-F007 | 孤立サロゲート | `E_JSON_SYNTAX` |
| JSON-F008 | 先頭ゼロ、小数部・指数部欠落 | `E_JSON_SYNTAX` |
| JSON-F009 | 同一キーの重複 | `E_JSON_DUPLICATE_KEY` |
| JSON-F010 | エスケープ後に同一となるキー | `E_JSON_DUPLICATE_KEY` |
| JSON-F011 | 数値入力数字4,096桁超 | `E_JSON_NUMBER_LIMIT` |
| JSON-F012 | 数値の精度またはスケール超過 | `E_JSON_NUMBER_LIMIT` |
| JSON-F013 | 入れ子256段超 | `E_JSON_DEPTH_LIMIT` |
| JSON-F014 | 総ノード250,000超 | `E_JSON_NODE_LIMIT` |
| JSON-F015 | 1配列65,536要素超 | `E_JSON_ARRAY_LENGTH_LIMIT` |
| JSON-F016 | 1オブジェクト65,536メンバー超 | `E_JSON_OBJECT_MEMBER_LIMIT` |
| JSON-F017 | 真偽取出しの値種別不一致 | `E_JSON_KIND_MISMATCH` |
| JSON-F018 | 整数取出しの値種別不一致 | `E_JSON_KIND_MISMATCH` |
| JSON-F019 | 小数取出しの値種別不一致 | `E_JSON_KIND_MISMATCH` |
| JSON-F020 | 文字列取出しの値種別不一致 | `E_JSON_KIND_MISMATCH` |
| JSON-F021 | 配列操作の値種別不一致 | `E_JSON_KIND_MISMATCH` |
| JSON-F022 | オブジェクト操作の値種別不一致 | `E_JSON_KIND_MISMATCH` |
| JSON-F023 | 必須キーの欠落 | `E_JSON_KEY_NOT_FOUND` |
| JSON-F024 | 配列の負または長さ以上の添字 | `E_JSON_INDEX_OUT_OF_BOUNDS` |
| JSON-F025 | 半開区間の負、逆転、長さ超過 | `E_JSON_RANGE_OUT_OF_BOUNDS` |
| JSON-F026 | 直列化UTF-8上限超 | `E_JSON_OUTPUT_LIMIT` |
| JSON-F027 | JSON構築累積上限超 | `E_JSON_CONSTRUCTION_LIMIT` |
| JSON-F028 | JSON作業累積上限超 | `E_JSON_WORK_LIMIT` |

各ケースは主`.bsb` span、必須診断フィールド、スタック・保存値・stdout・stderrの原子性を比較します。
JSON-F001〜JSON-F016はJSON入力内位置と安定`reason`も比較します。JSON-F017〜JSON-F028は公開CLIの
人間向け診断と構造化診断を同じ宣言行から組み立て、終了10、呼出しspan、stage、fields、
expected、actual、fix、relatedを一致させます。`ProgramRunner`では最終スタック、大域値、
JSON構築・作業予算を独立期待と比較し、失敗した操作が入力と保存先を変更しないことを検証します。

## 5. 資源・内部境界

| ID | 対象 |
|---|---|
| JSON-R001 | JSON入力16,777,216/16,777,217 UTF-8バイト |
| JSON-R002 | JSON出力16,777,216/16,777,217 UTF-8バイト |
| JSON-R003 | 入れ子256/257段と非再帰走査 |
| JSON-R004 | 1配列65,536/65,537要素 |
| JSON-R005 | 1オブジェクト65,536/65,537メンバー |
| JSON-R006 | 総ノード250,000/250,001 |
| JSON-R007 | 数値入力数字4,096/4,097桁 |
| JSON-R008 | 整数桁65,536/65,537 |
| JSON-R009 | 小数精度65,536/65,537 |
| JSON-R010 | 小数絶対スケール65,536/65,537 |
| JSON-R011 | JSON構築累積1,000,000/1,000,001 |
| JSON-R012 | JSON作業累積67,108,864/67,108,865 |
| JSON-R013 | 解析失敗時の作業消費と構築非消費 |
| JSON-R014 | 更新失敗時の両予算非消費 |
| JSON-R015 | 直列化失敗時の作業消費とstdout原子性 |
| JSON-R016 | 等価性と幅広キーリストの作業予算 |
| JSON-R017 | 値全文の既定トレース非開示と予算不変 |
| JSON-R018 | 深さ、幅、数値を同時に含む境界近傍値の512 MiB検証 |

上限ちょうどは成功、1超過は対応する安定コードで失敗させます。`StackOverflowError`、
`OutOfMemoryError`、素のJava例外、部分stdoutは許しません。

## 6. 独立コーデックオラクル

少なくとも次を本番`jp.bsb.json`実装と独立に比較します。

- RFC 8259の正常・不正コーパスの受理可否表
- 値種別つきの期待値を手作業で構築するJavaフィクスチャ
- 規範JSONバイト列を固定した`json/`リソース
- 制限つき生成器で作る往復値。生成器は数値表記とキー順を別に記録する

CLI出力用`JsonOutput`とテスト用`StrictJsonParser`を本番解析のオラクルにしません。

## 7. CLIと`explain --json`

- 全組み込み語数は74から107となり、追加33語は辞書のJSON機能グループ区画に仕様順で並ぶ
- `explain --json`の`schemaVersion` 1は維持し、追加語の`typeRule`は`fixed`、
  `featureGroup`は文字列`JSON`、能力と副作用は空配列とする
- `check --json`と`explain --json`の公開JSONはCLI DTOであり、JSON機能グループのBSB `JSON`値に自動変換しない
- `check`、`run`、`format`、`explain --json`の既存終了コードとstderr/stdout分離を維持する

## 8. 章末成果物

`JSON-N030`はJSON文字列を解析し、キー存在を検査し、必須値と配列要素を取得し、不変更新後のJSONを
正規直列化します。`check`、`run`、`format`、`explain --json`の出力と終了コードをバイト単位で
比較します。

通常実行とトレース実行で、終了種別、終了コード、最終スタック、保存値、stdout、stderr、命令数、
既存予算、JSON構築予算、JSON作業予算が一致しなければなりません。トレースはJSON全文、キー、
文字列値を出力せず、型と許可された形状情報だけを返します。

## 9. 完了ゲート

1. 76適合ケースとJava単体・結合テストが512 MiBで成功する
2. 全回帰テストと先行する実装済み機能グループのCLIバイト契約に回帰がない
3. 上限ちょうどと1超過、同時失敗の512 MiB検証が成功する
4. 本番コーデックのCLI、テストパーサー、期待JSONへの依存が0件である
5. 章末成果物と`explain --json`の独立期待リソースが一致する
6. N/F 58 IDとR 18 IDが中央カタログから消費され、未使用・欠落・重複資源が0件である
