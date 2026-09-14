# HTTP-REL機能グループ: 適合データ・テスト

> 状態: HTTP-REL機能グループの完成仕様。全31 ID、独立timing oracle、manifestを固定済みです。

## 1. 原則

実DNS、socket、TLS、実時間待機、外部キューを中央適合へ含めません。偽resolver、列応答を返す偽transport、
偽単調時計、偽壁時計、偽待機、偽最終送信記録を使い、各物理試行と待機を決定的に観測します。
期待値を本番executor、JDK HTTP client、実時計から生成しません。

## 2. 正常系 HTTPREL-N001〜N012

| ID | 必須観測 |
|---|---|
| HTTPREL-N001 | `none`と版1設定が従来のresolver 1回・transport 1回を維持 |
| HTTPREL-N002 | 通信失敗から成功、状態応答から成功への有限再試行 |
| HTTPREL-N003 | 最終4xx・5xxは成功応答、最終通信失敗は失敗値 |
| HTTPREL-N004 | GET・HEAD、冪等性キー、API保証のmethod安全性 |
| HTTPREL-N005 | 固定・指数backoffと上限 |
| HTTPREL-N006 | delta-secondsとIMF-fixdateの`Retry-After` |
| HTTPREL-N007 | 不正・複数・過去`Retry-After`の決定的扱い |
| HTTPREL-N008 | 接続単位開始間隔とbackoffを最大時刻へ統合 |
| HTTPREL-N009 | 物理試行ごとの送信・要求・応答・バイト列課金 |
| HTTPREL-N010 | 任意・必須の閉じた最終送信記録 |
| HTTPREL-N011 | 版2 TOMLの既定値、明示値、版1互換 |
| HTTPREL-N012 | trace、実行結果、通常実行の最終結果同値 |

## 3. 失敗系 HTTPREL-F001〜F011

| ID | 必須観測 |
|---|---|
| HTTPREL-F001 | 方針11理由の検証順 |
| HTTPREL-F002 | 試行上限、送信上限、要求試行上限 |
| HTTPREL-F003 | 待機上限の丁度・1超過 |
| HTTPREL-F004 | 待機能力の不在・失敗・取消 |
| HTTPREL-F005 | HTTP-date用壁時計の不在・失敗 |
| HTTPREL-F006 | transport能力例外・契約違反は再試行しない |
| HTTPREL-F007 | resolver・資格情報・全実行取消は再試行しない |
| HTTPREL-F008 | 応答累積・byte予算超過時の部分状態 |
| HTTPREL-F009 | 最終記録能力の不在・失敗・取消・上限 |
| HTTPREL-F010 | TOML版2の未知項目、型、範囲、集合、矛盾 |
| HTTPREL-F011 | JDK内部retryが引き続き無効で1能力呼出し=1物理試行 |

## 4. 資源・非開示 HTTPREL-R001〜R008

| ID | 必須観測 |
|---|---|
| HTTPREL-R001 | 8試行、1,024物理送信、要求本文累積の境界 |
| HTTPREL-R002 | 破棄応答を含む応答受信・バイト列構築の境界 |
| HTTPREL-R003 | 3,600,000 ms待機要求累積の境界 |
| HTTPREL-R004 | 待機を能動30秒から除外し、例外でも区間を閉じる |
| HTTPREL-R005 | 同一接続と異なる接続の開始間隔状態を分離 |
| HTTPREL-R006 | 記録1,024件境界と失敗原子性 |
| HTTPREL-R007 | URI、秘密、本文、header、Retry-After、例外markerの全公開面非漏えい |
| HTTPREL-R008 | 最大ヒープ512 MiBで最大応答を再試行しても逐次解放可能 |

## 5. 受入ゲート

全31 IDを中央カタログから過不足なく消費し、manifestで全物理資源を固定します。独立oracleはbackoff、
`Retry-After`、開始時刻、試行列、予算遷移を再計算します。新規診断をすべて`CONFORMANCE`にし、既存249診断と
167語の分類を低下させません。版1/2 TOML、偽埋込み、JDK adapter、fat JARを各2回実行し、通常・traceで
最終結果と予算を一致させます。
