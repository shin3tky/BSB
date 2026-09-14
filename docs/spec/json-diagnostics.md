# JSON機能グループ: JSON診断、原子性、資源境界

> 本文は現行実装の規範契約です。

本仕様は[json-values.md](json-values.md)のJSON語が返す静的・実行時診断、JSON入力内位置、
失敗優先順位、プレビュー、原子性、資源課金を定めます。

## 1. 診断コード

JSON機能グループは次の14コードを`DiagnosticCode`の実行時区画へ追加します。

| コード | 条件 | 主なフィールド |
|---|---|---|
| `E_JSON_SYNTAX` | JSON文字列が規範文法に一致しない | `word`、`reason`、JSON内位置 |
| `E_JSON_DUPLICATE_KEY` | 同じ展開後キーが1オブジェクト内で重複 | `keyPreview`、`firstUtf8Offset`、JSON内位置 |
| `E_JSON_NUMBER_LIMIT` | 入力数字、整数桁、小数有効桁、スケールの上限超過 | `metric`、`limit`、`observed`、JSON内位置 |
| `E_JSON_DEPTH_LIMIT` | JSON容器の入れ子が256段超 | `limit`、`observed`、JSON内位置 |
| `E_JSON_NODE_LIMIT` | 1 JSON値が250,000値ノード超 | `limit`、`observed`、JSON内位置 |
| `E_JSON_ARRAY_LENGTH_LIMIT` | 1 JSON配列が65,536要素超 | `limit`、`observed`、`operation` |
| `E_JSON_OBJECT_MEMBER_LIMIT` | 1 JSONオブジェクトが65,536メンバー超 | `limit`、`observed`、`operation` |
| `E_JSON_KIND_MISMATCH` | JSON値種別が操作の必要種別と異なる | `word`、`expectedKind`、`actualKind` |
| `E_JSON_KEY_NOT_FOUND` | 必須取得のキーが存在しない | `word`、`keyPreview`、`memberCount` |
| `E_JSON_INDEX_OUT_OF_BOUNDS` | JSON配列の添字が`[0,N)`外 | `operation`、`index`、`length`、`validRange` |
| `E_JSON_RANGE_OUT_OF_BOUNDS` | JSON配列の半開区間が不正 | `start`、`end`、`length`、`validCondition` |
| `E_JSON_OUTPUT_LIMIT` | 直列化結果が16,777,216 UTF-8バイト超 | `word`、`limit`、`observed` |
| `E_JSON_CONSTRUCTION_LIMIT` | 1実行のJSON構築単位が1,000,000超 | `operation`、`current`、`requested`、`limit`、`observed` |
| `E_JSON_WORK_LIMIT` | 1実行のJSON作業単位が67,108,864超 | `operation`、`current`、`requested`、`limit`、`observed` |

表は14行です。診断コードの追加数は14個とし、コード名を統合・別名化しません。

BSB静的型の入力不足・不一致、代入不一致、分岐不一致には既存の`E_STACK_UNDERFLOW`、
`E_TYPE_MISMATCH`、`E_ASSIGNMENT_TYPE_MISMATCH`、`E_BRANCH_STACK_MISMATCH`を使います。
`配列<JSON>`のBSB配列操作は既存の配列診断と配列予算を使います。

## 2. JSON入力内位置

JSON解析の主診断位置は、`.bsb`ソース上の`JSONを解析する`呼出しspanです。入力JSONは実行時に
組み立てられる場合があるため、元JSON文字列内の位置は`RelatedLocation`ではなく次の構造化
フィールドで返します。

| フィールド | 意味 |
|---|---|
| `jsonUtf8Offset` | JSON文字列先頭を0とするUTF-8バイト位置 |
| `jsonLine` | 1始まりの物理行 |
| `jsonColumn` | 1始まりのUnicodeスカラー値列 |

CR、LF、CRLFを各1改行として数えます。CRLFのLF位置を単独のエラー位置にせず、次の文字を
次行1列とします。タブはJSON内位置では1 Unicodeスカラー、1列として数えます。BSBソース位置の
書記素クラスタ列・タブ幅4とは別の、実行時入力内の位置です。

入力末尾の欠落はUTF-8長と等しい`jsonUtf8Offset`を持ち、最終文字直後の行・列を返します。
不正トークンはその先頭位置、未終了文字列は開始引用符ではなく入力末尾、孤立低サロゲートは
その`\u`、高サロゲートに低サロゲートが続かない場合は高サロゲートの`\u`を指します。

## 3. JSON構文理由識別値

`E_JSON_SYNTAX.fields.reason`は次の安定ASCII値のいずれかです。

```text
emptyInput
unexpectedToken
trailingContent
expectedObjectKey
expectedColon
expectedCommaOrEnd
unterminatedString
unescapedControl
invalidEscape
invalidUnicodeEscape
isolatedSurrogate
invalidNumber
```

読取側は将来の未知理由を文字列として保持できます。処理系は未知の内部理由を推測して公開せず、
内部契約違反として扱います。

## 4. 値種別識別値

診断の`expectedKind`と`actualKind`は次の安定値を使います。

```text
null
boolean
integer
decimal
string
array
object
```

複数種別を期待する操作はJSON機能グループにありません。BSBの静的型名`JSON`、`整数`などをこの
フィールドへ入れません。

## 5. 診断プレビュー

JSON入力全体を診断へ複製しません。キーと不正トークンのプレビューは、エスケープ展開後の
Unicodeスカラー値列を最大64コードポイントで表します。

- 64以下は全体
- 65以上は先頭32、`…(+省略数)`、末尾16
- LF、CR、HTAB、バックスラッシュ、引用符、U+0000〜U+001FはBSB文字列の標準エスケープ
- 孤立サロゲートをプレビューへ入れない

JSON値本体、オブジェクト全体、配列全体は診断へ表示しません。`actual`には値種別名、範囲、
件数など問題の判定に必要な形だけを入れます。キー不在では利用者が指定したキーの限定プレビューを
`keyPreview`へ入れますが、利用可能キー一覧と既存値は公開しません。

## 6. 解析時の失敗優先順位

`JSONを解析する`は次の順で処理します。

1. 既存の静的スタック・型検査
2. 入力`文字列`の既存Unicodeスカラー・16 MiB不変条件
3. 入力UTF-8バイト数のJSON作業単位を一括予約
4. JSONを先頭から解析し、各位置で構文を先に検査
5. 容器へ入る直前の入れ子上限
6. 65,537個目の配列要素またはオブジェクトメンバー上限
7. 250,001個目の値ノード上限
8. 数値トークン全体を走査した後、数値上限
9. オブジェクトのコロンを受理した後、値を読む前の重複キー検査
10. 全JSONを受理した後、必要なJSON構築単位を一括予約
11. 検査済みJSON値を利用者スタックへ公開

同じ入力位置で構文と資源の両方が不正なら構文を優先します。例えば65,537個目の配列要素位置が
閉じ括弧でも値でもない場合は`E_JSON_SYNTAX`です。

JSON作業単位は手順3を通った時点で消費済みです。その後に構文、重複キー、数値、深さ、件数で
失敗しても戻しません。JSON構築単位は完全な値を検査できた手順10でだけ予約するため、解析失敗では
消費しません。構築上限予約に失敗した場合も加算しません。

## 7. 操作時の失敗優先順位

検査済みJSON値への1呼出しは次の順で最初の1件だけを返します。

1. 既存の静的スタック・型検査
2. JSON値種別
3. キー存在、添字、半開区間
4. 1容器の要素・メンバー上限
5. 直列化結果の16 MiB上限
6. JSON構築単位の累積上限
7. JSON作業単位の累積上限
8. 新しい不変値または文字列の構築とスタック更新

値種別判定語は失敗せず、1 JSON作業単位を予約してから真偽を積みます。スカラー変換は種別を
検査した後、値の桁・文字列不変条件が内部モデルと一致することを確認し、1作業単位を予約します。

JSON直列化はキャッシュ済みまたは文字列を作らない計測値で手順5を判定し、出力上限を通った後に
「直列化UTF-8バイト数 + 値ノード数 + 全直接要素・メンバー数」を作業単位として予約します。

## 8. JSON構築・作業単位

### 8.1 構築単位

新しい論理JSON容器に保持する参照も、メモリ・複製量を表す単位として数えます。

| 操作 | JSON構築単位 |
|---|---:|
| JSONヌル・空JSON配列・空JSONオブジェクト | 1 |
| BSB真偽・整数・小数からJSON | 1 |
| BSB文字列からJSON | 1 |
| JSON解析 | 値ノード数 + 全配列要素数 + 全オブジェクトメンバー数 |
| `配列<JSON>`からJSON配列 | `N + 1` |
| JSON配列の一部取得 | 結果長`M + 1` |
| JSON配列の要素置換 | 入力長`N + 1` |
| JSON配列の末尾追加 | 入力長`N + 2` |
| JSONオブジェクト設定 | 結果メンバー数`M + 1` |
| JSONオブジェクト削除 | 結果メンバー数`M + 1` |

JSON文字列の文字数、数値の桁数はJSON作業単位で数え、構築単位へ重複加算しません。
既存JSON配列から`配列<JSON>`への変換とキー一覧は、JSON構築単位を使わず、結果BSB配列の
要素数を既存配列構築単位へ課金します。

### 8.2 作業単位

| 操作 | JSON作業単位 |
|---|---:|
| JSON解析 | 入力UTF-8バイト数 |
| JSON直列化・表示 | 出力UTF-8バイト数 + 値ノード数 + 全要素・メンバー数 |
| BSB文字列とJSON文字列の相互変換 | `1 + UTF-8バイト数` |
| その他のスカラー相互変換 | 1 |
| 値種別判定 | 1 |
| JSON配列の長さ・1要素取得 | 1 |
| JSON配列と`配列<JSON>`の変換 | `N + 1` |
| JSON配列の一部取得 | `M + 1` |
| JSON配列の置換・追加 | 結果長`M + 1` |
| JSONオブジェクトの要素数・キー存在・必須取得 | 1 |
| JSONオブジェクトのキー一覧 | `N + 1 + 全キーUTF-8バイト数` |
| JSONオブジェクトの設定・削除 | 結果メンバー数`M + 1` |
| JSON等価比較 | 両入力の値ノード数 + 全要素・メンバー数 |

加算・乗算で`long`があふれる場合は上限超過として飽和させ、負値へ折り返しません。

### 8.3 予算の原子性

構築単位と作業単位の両方を使う操作は、構築、作業の順に両上限を検査し、両方を受理できる場合だけ
同時に加算します。どちらかの予約に失敗した場合は両方とも変えません。解析だけは第6節のとおり、
入力走査作業を先に消費し、成功値の構築単位を最後に予約します。

## 9. 容器操作の原子性

- JSON配列の添字・範囲失敗では入力スタックと両JSON予算を変えない
- JSON配列の長さ65,536へ追加する場合は`E_JSON_ARRAY_LENGTH_LIMIT`を予算より先に返す
- JSONオブジェクトのメンバー65,536へ新規キーを設定する場合は`E_JSON_OBJECT_MEMBER_LIMIT`
- 同じキーの更新はメンバー上限を超えず、規定の複製単位だけを予約する
- 存在しないキーの削除は成功し、入力と等価な結果を返して規定単位を消費する
- キー不在の必須取得はJSON値・キーの両入力をスタックに残す
- すべての失敗でstdout、stderr、保存領域、元JSON値を変えない

## 10. 直列化と表示の原子性

`JSONを文字列に変換する`は候補UTF-8長を文字列構築前に検査します。`表示する`と
`一行表示する`は次の全上限を満たす場合だけ、JSON全体と任意のLFを1回の出力呼出しで書きます。

1. JSON直列化結果16 MiB
2. JSON作業累積
3. stdout累積64 MiB

いずれかに失敗した場合、JSON入力を消費せずstdoutへ1バイトも追加しません。`エラー表示する`と
`エラー一行表示する`もstderrについて同じ規則です。

## 11. トレースと非開示

既定`TraceValuePolicy`は`JSON`と`配列<JSON>`の値部分を常に非開示にします。

```text
JSON:<redacted>
配列<JSON>:<redacted>
```

JSONヌル、空容器、値種別、長さ、キー名も例外なく非開示です。トレースは型名だけから表記を作り、
JSON直列化、走査、配列走査を行いません。このためトレースはJSON予算、配列予算、実行結果、診断を
変えません。

JSON機能グループの章末トレースは、解析直前のBSB文字列からJSON本文やキーが漏れないよう、`formatJson`
で文字列と`配列<文字列>`も保守的に非開示にします。IO機能グループ以前のトレース方針と22列スキーマは
変更しません。

## 12. 到達不能コードと診断順

構造的に到達不能なJSON呼出しには既存`W_UNREACHABLE_CODE`だけを報告し、派生する型診断を
抑止します。実行されないためJSON実行時診断と予算消費もありません。

静的診断は既存の段階・元ソース位置・コード順、実行時診断は最初に実行された失敗呼出し1件です。
`check --json`はJSONの静的型診断を人間向け`check`と同じ構造情報から返します。JSON実行時診断は
人間向け`run`で返し、将来の構造化`run`も同じコードとフィールドを再利用できる設計にします。
