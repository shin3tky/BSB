# HTTP-REL機能グループ: 診断、原子性、非開示

> 状態: HTTP-REL機能グループの完成仕様。診断、適合データ、Java実装へ反映済みです。

## 1. 新規診断

| コード | 条件 | 必須fields |
|---|---|---|
| `E_HTTP_RETRY_POLICY_INVALID` | 解決済み信頼性方針が不正 | `word`、`connection`、`reason` |
| `E_HTTP_RETRY_WAIT_LIMIT` | 信頼性待機要求合計が上限を超える | `word`、`limitName=httpReliabilityWaitMilliseconds`、`limit`、`used`、`requested`、`observed` |
| `E_HTTP_RETRY_WAIT_UNAVAILABLE` | 必要な待機能力がない | `word`、`connection`、`method` |
| `E_HTTP_RETRY_WAIT_FAILURE` | 待機能力が失敗した | `word`、`connection`、`method` |
| `E_HTTP_RETRY_WAIT_CANCELLED` | 待機が取り消された | `word`、`connection`、`method` |
| `E_HTTP_RETRY_CLOCK_UNAVAILABLE` | HTTP-date解釈に必要な壁時計能力がない | `word`、`connection`、`method` |
| `E_HTTP_RETRY_CLOCK_FAILURE` | 壁時計能力が失敗した | `word`、`connection`、`method` |
| `E_HTTP_FINAL_FAILURE_UNAVAILABLE` | 必須の最終送信記録能力がない | `word`、`connection`、`method` |
| `E_HTTP_FINAL_FAILURE_FAILURE` | 最終送信記録能力が失敗した | `word`、`connection`、`method` |
| `E_HTTP_FINAL_FAILURE_CANCELLED` | 最終送信記録が取り消された | `word`、`connection`、`method` |
| `E_HTTP_FINAL_FAILURE_LIMIT` | 1実行の記録数上限を超える | `word`、`limitName=httpFinalFailureRecords`、`limit`、`observed` |

すべてruntime、error、終了10です。方針不正の`reason`は次の閉じたASCII値です。

```text
MODE_INVALID
ATTEMPT_LIMIT_INVALID
FAILURE_KIND_INVALID
STATUS_CODE_INVALID
DELAY_INVALID
BACKOFF_INVALID
RETRY_AFTER_INVALID
METHOD_SAFETY_INVALID
IDEMPOTENCY_HEADER_INVALID
RATE_LIMIT_INVALID
FINAL_FAILURE_POLICY_INVALID
```

既存接続方針の`RETRY_POLICY_INVALID`は、方針コンテナの不在・型不一致にだけ維持し、上記の詳細不正は
`E_HTTP_RETRY_POLICY_INVALID`へ写します。

## 2. 判定順と原子性

送信語は、既存の送信前検査後に信頼性方針、method安全性、全実行試行予算を検査します。各追加試行では、
待機予算、待機、送信・要求試行予算、transportの順です。応答を受けた試行は、再試行する場合でもheader、本文、
応答受信、バイト列予算を先に確定します。

捕捉不能診断では入力`HTTP要求`、保存値、stdout、プログラムstderrを維持し、部分`HTTP応答`や
`HTTP送信失敗`を積みません。すでに行った物理送信、受信、待機、能力イベント、受理済み予算は戻しません。
拒否された次試行の送信回数・要求本文試行予算は加算しません。

最終送信記録が必須のときは、最終結果用のstack容量を先に検査し、記録予算を予約し、能力を1回呼び、成功後にだけ
結果値へ置換します。記録能力の失敗時も最終応答本文を公開面へ残しません。

## 3. 非開示

既存HTTPSの非開示に加え、`Retry-After`原文とHTTP-date、冪等性キー値、rate-limit関連header原文、
破棄応答のstatus以外、最終送信記録能力の宛先・例外・内部識別子を公開面へ含めません。

公開してよいのは論理接続名、静的method、試行番号、待機ミリ秒、安定status、閉じた失敗種類、規範上限、
上記の安定reasonだけです。より厳しいホストでは論理接続名を既存方針どおり伏せられます。
