# CONN機能グループ: 論理接続の診断とホスト境界

> 本文は現行実装の規範契約です。

## 1. 診断一覧

CONN機能グループは次の12診断を追加します。既存182診断と公開`check --json`版1を維持します。

| コード | 段階 | 条件 | 必須fields |
|---|---|---|---|
| `E_LOGICAL_CONNECTION_DECLARATION_VALUE` | syntax | 論理接続宣言に初期値相当のトークンがある | `connection`、`actual` |
| `E_LOGICAL_CONNECTION_DECLARATION_SCOPE` | syntax | 論理接続宣言がトップレベル以外にある | `connection`、`scope` |
| `E_LOGICAL_CONNECTION_LIMIT` | name | 論理接続宣言が10,000個を超えた | `limit`、`observed` |
| `E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT_START` | syntax | 確認語の直後に`<`がない | `word` |
| `E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT` | syntax | `<`の後に接続名がない、または名前でない | `word`、`argumentIndex` |
| `E_LOGICAL_CONNECTION_ARGUMENT_COUNT` | syntax | 静的接続引数が1個でない | `word`、`expectedCount`、`actualCount` |
| `E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT_END` | syntax | 接続名の後に`>`がない | `word`、`connection` |
| `E_UNDECLARED_LOGICAL_CONNECTION` | name | 静的引数名に対応する宣言がない | `word`、`connection` |
| `E_LOGICAL_CONNECTION_REFERENCE_NOT_ALLOWED` | name | 宣言名を静的引数以外から値・語として参照する | `connection`、`context` |
| `E_LOGICAL_CONNECTION_NOT_CONFIGURED` | runtime | ホストに対象名の定義がない | `word`、`capability`、`connection`、`operation` |
| `E_LOGICAL_CONNECTION_ACCESS_DENIED` | runtime | ホストが対象名・操作の利用を拒否した | `word`、`capability`、`connection`、`operation` |
| `E_LOGICAL_CONNECTION_CONFIGURATION_INVALID` | runtime | ホスト定義が規範を満たさない | `word`、`capability`、`connection`、`operation`、`reason` |

新しい診断のmessage、expected、actual、fixesに基底URI、origin、資格情報参照、秘密値、ホスト自由文を
含めません。`connection`はソース上のNFC正規名、`capability`は`connection.resolve`、`operation`は
`resolve`です。`reason`は[論理接続仕様](logical-connections.md)第4.2節の安定ASCII値だけです。

能力全体がない場合は既存`E_CAPABILITY_UNAVAILABLE`、能力呼出し自体の失敗は既存
`E_CAPABILITY_FAILURE`です。命令・時間上限は既存診断、能力契約違反は内部エラー70であり、
新しい3実行時診断へ変換しません。

## 2. 構文回復と優先順位

論理接続宣言は宣言終端`。`までを回復単位とします。初期値相当が複数あっても
`E_LOGICAL_CONNECTION_DECLARATION_VALUE`は宣言ごとに1件です。単語内に置かれた宣言はscope診断を
1件出し、初期値、名前解決、実行診断を派生させません。

静的接続引数は対応する`>`、定義終端、宣言終端、またはEOFまでを回復単位とします。
診断優先順位は次です。

1. 引数開始`<`
2. 第1引数の名前
3. 引数個数
4. 終端`>`
5. 宣言への名前解決

同じ欠落位置から外側の定義終端不足を派生させません。EOFで`>`と`こと。`の両方がない場合は、
最も内側の`E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT_END`だけを出します。構文エラーがある呼出しへ
未宣言、能力、型、IR診断を出しません。

宣言数上限は、構文として有効なトップレベル宣言をソース順に数え、10,001個目の登録前に検査します。
上限到達後も字句・構文回復は続けますが、未登録となった宣言との重複・参照から名前診断を派生させません。

`論理接続を確認する`は常に静的引数を必要とします。引数なしの通常語呼出しとして回復させません。
`論理接続を確認する<整数>`では構文成功後に`E_UNDECLARED_LOGICAL_CONNECTION`です。
同名の値・語があっても論理接続宣言として代用しません。

## 3. 静的検査と到達不能

全論理接続宣言を収集して名前衝突を確定した後、利用者定義語の本体を検査します。接続確認は
データスタックを変更しないため、前後のスタック形をそのまま維持します。利用者定義語の効果推論と
IRには、正規化済み接続名と操作を失わず保持します。

構造的に到達不能な接続確認は、既存`W_UNREACHABLE_CODE`だけを出し、未宣言、引数の名前解決、
パラメータ付き能力要求を派生させません。構文診断は到達不能でも抑止しません。

`check`と`explain --json`はホスト解決能力を呼びません。静的に有効なら、ホストが能力を持つか、
対象名が設定済みかにかかわらず成功します。`run`だけが到達した確認語で実行時境界を検査します。

## 4. 実行時の判定順

到達した確認語では次の順を固定します。

1. 既存の命令数上限
2. 既存の能動実行時間上限
3. `connection.resolve`能力の存在
4. 能力の1回の呼出し
5. 能力失敗または応答契約違反
6. `NOT_CONFIGURED`
7. `DENIED`
8. `INVALID`の理由値
9. `RESOLVED`の接続方針検証
10. 正常継続

能力がない場合は呼出しイベントを作りません。能力が呼ばれた場合は、成功・診断・内部失敗のいずれでも
1イベントだけを残します。`RESOLVED`の方針に複数の不正がある場合は仕様表順の最初の理由を使います。
診断の構築またはtraceのために能力を再呼出ししません。

`NOT_CONFIGURED`は名前が認可対象かを推測しません。ホストが情報秘匿のため未設定と拒否を同じ状態へ
畳み込むことは許しますが、処理系が一方を推測して他方へ変換してはなりません。

## 5. 原子性

呼出し前の状態を`S`とすると、成功時も診断時もデータスタックは`S`です。保存値、stdout、
プログラムstderr、既存の入出力・配列・正規表現・JSON・待機予算を変更しません。命令数と、
能力呼出しまでに消費した能動実行時間は巻き戻しません。能力を呼んだ場合の能力イベントも保持します。

設定不正の検査は方針全体をホスト側の一時値として検証してから終了し、部分的に検証済みのURI、method、
認証方式を実行状態へ登録しません。CONN機能グループでは解決結果を呼出し間でキャッシュしません。

## 6. 非開示と安全表現

実行時診断の共通`dataStack`は呼出し前のBSB値だけから作ります。接続定義、資格情報参照、能力応答を
追加しません。能力失敗の共通文脈にもホスト例外本文を含めません。

次の各面で秘密検出用文字列が0件でなければ不適合です。

- 人間向け診断と`check --json`診断の全メンバー
- CLI stdout、処理系stderr、プログラムstderr
- 通常trace、非開示trace、能力イベント
- `explain --json`全体
- 最終データスタック、保存値、内部適合用状態TSV
- Java例外を公開CLIへ写したフォールバック文面

論理接続名自体はソース情報なので既定では公開できます。ホストの強いtrace非開示方針で名前を伏せる場合も、
診断fieldsの規範値や`explain --json`の静的要求は変更しません。

## 7. 捕捉不能境界

未設定、拒否、設定不正、能力不足、能力失敗、命令・時間上限、内部失敗はすべて捕捉不能診断または
内部終了です。`結果<T,E>`へ変換せず、既存の結果自動伝播や汎用診断捕捉も追加しません。

この分類は、通信確立失敗や応答タイムアウトを後続の`HTTP送信失敗`へ入れる決定と矛盾しません。
CONN機能グループの失敗は通信操作の結果ではなく、ホストが強制する能力・設定境界だからです。
