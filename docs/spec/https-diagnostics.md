# HTTPS機能グループ: 最小HTTPSの診断と失敗境界

> 本文は現行実装の規範契約です。
>
> [最小HTTPS仕様](https.md)と[適合性](https-conformance.md)に従います。

## 1. 新規診断

HTTPS機能グループでは次の26診断を追加します。すべて重要度error、終了コード10です。通信相手または通信路に由来する
回復可能な失敗は診断ではなく`HTTP送信失敗`です。

### 1.1 静的引数

| コード | 段階 | 条件 | 必須fields |
|---|---|---|---|
| `E_EXPECTED_HTTP_ARGUMENT_START` | syntax | 送信語の直後に`<`がない | `word` |
| `E_EXPECTED_HTTP_CONNECTION_ARGUMENT` | syntax | 第1引数が接続名でない | `word`、`argumentIndex=1` |
| `E_EXPECTED_HTTP_METHOD_ARGUMENT` | syntax | 第2引数がmethodでない | `word`、`argumentIndex=2` |
| `E_HTTP_ARGUMENT_COUNT` | syntax | 静的引数が2個でない | `word`、`expectedCount=2`、`actualCount` |
| `E_EXPECTED_HTTP_ARGUMENT_END` | syntax | 第2引数の後に`>`がない | `word`、`connection`、`method` |
| `E_HTTP_METHOD_INVALID` | name | methodが大文字ASCIIの許可6値でない | `word`、`method`、`allowedMethods` |

未宣言接続は既存`E_UNDECLARED_LOGICAL_CONNECTION`を再利用します。接続名を通常値として使う場合も既存
`E_LOGICAL_CONNECTION_REFERENCE_NOT_ALLOWED`です。

### 1.2 要求構築

| コード | 条件 | 必須fields |
|---|---|---|
| `E_HTTP_PATH_INVALID` | 相対経路が先頭slash、query・fragment、NUL、dot segmentを含む | `word`、`reason` |
| `E_HTTP_PATH_LIMIT` | 生経路が8,192 UTF-8バイトを超える | `word`、`limitName=httpPathBytes`、`limit`、`observed` |
| `E_HTTP_QUERY_LIMIT` | queryが256項目または65,536符号化バイトを超える | `word`、`limitName`、`limit`、`observed` |
| `E_HTTP_HEADER_NAME_INVALID` | header名が1〜256文字のASCII tokenでない | `word`、`reason` |
| `E_HTTP_HEADER_VALUE_INVALID` | header値が許可ASCIIまたは8,192バイト境界を満たさない | `word`、`reason`。`valueTooLong`では`limit`、`observed`も必須 |
| `E_HTTP_HEADER_RESERVED` | 利用者が予約headerを設定した | `word`、`header` |
| `E_HTTP_HEADER_LIMIT` | 要求headerが128値または65,536バイトを超える | `word`、`limitName`、`limit`、`observed` |
| `E_HTTP_METADATA_CONSTRUCTION_LIMIT` | 1実行のmetadata構築が16,777,216バイトを超える | `word`、`limitName=httpMetadataConstructionBytes`、`limit`、`used`、`requested`、`observed` |

`reason`は次の安定ASCII値だけです。

```text
absolutePath
queryDelimiter
fragmentDelimiter
nul
dotSegment
emptyName
nameTooLong
invalidNameCharacter
valueTooLong
invalidValueCharacter
```

`E_HTTP_HEADER_RESERVED`の`header`は利用者がソースまたは値で明示したASCII小文字化名です。APIキー用の
ホストheader名、認証header名、接続方針は診断へ含めません。

### 1.3 送信前・能力境界・全実行資源

| コード | 条件 | 必須fields |
|---|---|---|
| `E_HTTP_TARGET_URI_LIMIT` | 最終絶対URIが65,536 ASCIIバイトを超える | `word`、`limitName=httpTargetUriBytes`、`limit`、`observed` |
| `E_HTTP_METHOD_NOT_ALLOWED` | 静的methodが解決済み接続方針で許可されない | `word`、`connection`、`method` |
| `E_HTTP_BODY_NOT_ALLOWED` | GETまたはHEAD要求に長さ0を含む本文がある | `word`、`method` |
| `E_HTTP_REQUEST_SIZE_LIMIT` | 要求本文が接続方針の上限を超える | `word`、`connection`、`method`、`limitName=requestBytes`、`limit`、`observed` |
| `E_HTTP_SEND_LIMIT` | 1実行の送信が1,024回を超える | `word`、`limitName=httpSendCalls`、`limit`、`observed` |
| `E_HTTP_REQUEST_TOTAL_LIMIT` | 要求本文試行累積が134,217,728バイトを超える | `word`、`limitName=httpRequestAttemptBytes`、`limit`、`used`、`requested`、`observed` |
| `E_HTTP_RESPONSE_TOTAL_LIMIT` | 応答本文受信累積が134,217,728バイトを超える | `word`、`limitName=httpResponseReceivedBytes`、`limit`、`used`、`requested`、`observed` |
| `E_HTTP_AUTHENTICATION_UNSUPPORTED` | 接続方針が`basic`または`oauth2` | `word`、`connection`、`authenticationKind` |
| `E_HTTP_CREDENTIAL_NOT_CONFIGURED` | APIキー参照に資格情報が設定されていない | `word`、`connection`、`authenticationKind=apiKey` |
| `E_HTTP_CREDENTIAL_ACCESS_DENIED` | ホストがAPIキー利用を拒否した | `word`、`connection`、`authenticationKind=apiKey` |
| `E_HTTP_CREDENTIAL_INVALID` | APIキーheader名・値が規範を満たさない | `word`、`connection`、`authenticationKind=apiKey`、`reason` |
| `E_HTTP_CANCELLED` | 実行全体が送信中に取り消された | `word`、`connection`、`method` |

資格情報不正の`reason`は`HEADER_NAME_INVALID`または`HEADER_VALUE_INVALID`だけです。header名、値、参照、
プロバイダー名、自由文は公開しません。

接続未設定・拒否・設定不正はCONN機能グループの3診断を、能力不在・能力自体の失敗は既存
`E_CAPABILITY_UNAVAILABLE`・`E_CAPABILITY_FAILURE`を使います。1値・累積バイト列予算、JSON予算、
配列予算、データスタック、命令、能動実行時間は既存診断です。

## 2. 回復可能な失敗

次だけを`失敗(HTTP送信失敗)`へ変換します。

| 条件 | 種類 |
|---|---|
| DNSまたは同等の名前解決失敗 | `nameResolutionFailure` |
| 接続timeout | `connectTimeout` |
| TCP接続拒否・切断等 | `connectionFailure` |
| TLS handshake・証明書・hostname失敗 | `tlsFailure` |
| 完全応答までのtimeout | `responseTimeout` |
| 接続方針の応答本文上限超過 | `responseTooLarge` |
| 応答header上限超過 | `responseHeadersTooLarge` |
| HTTP応答契約違反 | `protocolFailure` |
| 安全に細分類できない通信I/O失敗 | `transportFailure` |

失敗結果を返して後続が正常終了したrunは終了0、CLI診断なしです。4xx・5xx、空本文、未知の
Content-Type、未知のContent-Encodingは失敗にしません。応答JSON解析失敗はHTTP送信失敗ではなく、本文取出し・
UTF-8復号・JSON解析の後続結果です。

分類は原因チェーンの秘密文面ではなく型付き能力応答から決めます。JDK実装は既知の例外型・状態だけを
細分類し、安全に確定しない場合は`transportFailure`にします。自由文の部分一致で分類しません。

## 3. 捕捉不能境界

次は結果値へ変換しません。

| 条件 | 診断または終了 | 理由 |
|---|---|---|
| 静的引数、path、query、header、method/bodyの誤り | 新規診断 | プログラム契約違反 |
| 要求本文の接続方針超過 | `E_HTTP_REQUEST_SIZE_LIMIT` | 送信前に確定する契約違反 |
| method不許可 | `E_HTTP_METHOD_NOT_ALLOWED` | ホスト認可境界 |
| 接続・資格情報の未設定、拒否、不正、未対応 | CONN・HTTPS機能グループ診断 | 能力・設定境界 |
| 送信回数、要求・応答累積、metadata累積 | 新規資源診断 | 全実行の資源境界 |
| 全実行取消 | `E_HTTP_CANCELLED` | 外側の実行制御 |
| 能力不在・能力契約失敗 | 既存能力診断 | ホスト境界 |
| バイト列、JSON、配列、スタック、命令、時間上限 | 既存診断 | 全実行または値の境界 |
| null、名前・method不一致、上限超過成功応答、壊れた内部値 | 内部失敗70 | 処理系・能力契約違反 |

応答が接続方針の上限を超えた場合は外部データ由来の回復可能失敗ですが、全実行の応答受信累積を超えた場合は
捕捉不能診断です。`Content-Length`が両方を超える場合は接続方針の`responseTooLarge`を先に分類します。
streaming中の実読取で両方へ同時に到達する場合は、小さい上限を先に適用し、同値なら全実行累積を優先します。

## 4. 構文回復と静的検査順

静的引数の回復単位は対応する`>`、定義終端、宣言終端、またはEOFです。優先順位は次です。

1. 引数開始`<`
2. 第1引数の接続名
3. comma
4. 第2引数のmethod
5. 引数個数
6. 終端`>`
7. 接続名解決
8. method許可値

同じ欠落位置から外側終端診断を派生させません。構文失敗した呼出しへ型、能力要求、未宣言名、method診断を
追加しません。未宣言接続と不正methodが共存する構文成功呼出しでは、接続名を先に1件報告します。

送信語の静的スタック検査は通常の固定効果として行います。接続方針や能力は`check`・`explain --json`で
呼びません。到達不能送信は既存`W_UNREACHABLE_CODE`だけを出し、接続名解決、method、能力要求を派生させません。

## 5. 要求設定の判定順と原子性

要求設定語は共通して、型・スタック、入力固有の文法、1項目上限、要求全体上限、metadataまたは既存変換予算、
出力スタック上限の順に検査します。全条件を通るまで元要求と引数をスタックから除きません。

- 経路: NUL、先頭slash、`?`、`#`、dot segmentの順。その後に生UTF-8長
- query: 現在項目数、新しい符号化長、全query長、metadata累積
- header: 空名、名長、名文字、予約名、値長、値文字、個数、全header長、metadata累積
- JSON本文: JSON直列化上限・作業、UTF-8結果長、バイト列構築・作業、要求置換
- 文字列本文: UTF-8結果長、バイト列構築・作業、要求置換
- バイト列本文: 値の内部妥当性、要求置換。本文ストレージの再構築課金は0

失敗した設定では、入力スタック、元要求、保存値、両出力、能力イベントを変更しません。先に正常予約された
JSON走査等の段階予算は各既存仕様に従い、後段失敗時に戻さない場合があります。拒否された予約は加算しません。

## 6. 送信時の判定順

到達した送信では次の順を固定します。

1. 既存命令数、能動実行時間
2. 入力要求と静的引数の内部不変条件
3. `connection.resolve`能力の存在
4. `http.send`能力の存在
5. 送信回数上限
6. 要求本文試行累積上限
7. resolverを1回呼び、能力失敗・契約違反を分類
8. `NOT_CONFIGURED`、`DENIED`、`INVALID`、`RESOLVED`方針不正
9. method許可、GET・HEAD本文、path・query・header・対象URI、要求本文方針上限
10. 認証方式`basic`・`oauth2`の未対応
11. HTTP待機除外開始、`http.send`を1回呼出し、finally相当で待機除外終了
12. 能力例外・契約違反
13. `CANCELLED`、資格情報3状態
14. 通信失敗種類
15. 応答status、header、本文の能力契約
16. 応答受信累積上限
17. バイト列構築予算、結果用スタック上限
18. 入力要求を結果1個へ置換

能力不在ではresolverもtransportも呼びません。送信回数と要求本文試行は、両能力が存在し両予算を受理した後に
同時加算します。resolverが未設定・拒否・不正を返しても加算済み試行は戻しませんが、`http.send`は呼びません。

`http.send`を呼んだ場合は成功、失敗、取消、資格情報診断、能力失敗、内部失敗のいずれでも能力イベントを
1件だけ保持します。診断やtraceのために再送信・再解決しません。自動retryは0回です。

## 7. 応答受信、予算、部分状態

能力は応答本文を、接続方針上限と全実行残量の小さい方に対して最大「上限+1」まで読みます。
成功応答は完全本文と実読取バイト数が一致しなければなりません。`Content-Length`だけで方針超過を確定した場合、
本文実読取0として`responseTooLarge`を返します。

回復可能失敗でも実際に読んだ本文バイトを応答受信累積へ加算します。累積境界を越えた場合は、境界直前までを
加算して`E_HTTP_RESPONSE_TOTAL_LIMIT`とし、越えた1バイトで累積値を上限超過させません。
`HTTP送信失敗`は積みません。headerを受信していても、失敗結果はheader、status、本文接頭辞を保持しません。

完全応答を受けた後のバイト列構築・スタック上限診断では、物理通信と受信累積は戻しませんが、入力要求を
スタックに保ち、部分`HTTP応答`を積みません。

## 8. 非開示

次を診断のmessage、fields、expected、actual、fixes、relatedLocations、`dataStack`へ含めません。

- 基底URI、最終URI、host、port、path、queryとその長さ
- 利用者・認証のheader値、ホストAPIキーheader名、資格情報参照、秘密
- 要求・応答本文、接頭辞、hash、Content-Length以外から得た本文長
- TLS証明書、subject、issuer、fingerprint、プロトコル、暗号suite
- ホスト例外型・本文・stack trace、HTTP理由句、部分応答

公開してよいのは、ソース上の論理接続名、静的method、規範数値上限、利用者が明示した予約header名、
安定診断reason、専用取出し後の通常値だけです。資格情報・URI・例外に異なる検出用文字列を置き、
人間診断、JSON診断、両出力、通常trace、能力イベント、説明JSON、最終状態、公開CLIフォールバックの
全体で0件を要求します。
