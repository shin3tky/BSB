# HTTP-REL機能グループ: HTTP再試行と送信結果の保存

> 状態: HTTP-REL機能グループの完成仕様。適合データとJava実装へ反映済みです。

## 1. 目的と互換境界

HTTP-RELは、既存の`HTTP要求を送信する<論理接続,method>`を変更せず、論理接続のホスト方針に従う
有限回の自動再試行、待機、`Retry-After`、接続単位の開始間隔制限を追加します。BSBソースへ新しい型、語、
動的URL、要求単位の方針上書きを追加しません。

最終試行の返値はHTTPS機能グループと同じです。200〜599の最終応答は、再試行対象の状態コードであっても
`成功(HTTP応答)`です。通信失敗で試行を使い切った場合だけ`失敗(HTTP送信失敗)`です。したがって、4xx・5xxを
通信成功として扱う既存プログラムと、`HTTP送信失敗`の9種類は変わりません。

「デッドレター相当」は暗黙の外部キューではなく、ホストが任意で受け取る閉じた最終送信記録とします。
BSBのstdout、stderr、値、通常traceへ要求・応答本文や秘密を自動出力しません。

## 2. 接続方針

既存の`retryPolicy=none`を、次の不変な非秘密方針へ拡張します。省略時と`none`は従来どおり1試行です。

| 項目 | 規則 |
|---|---|
| mode | `none`または`bounded` |
| maximumAttempts | 1〜8。初回を含む。`none`では1 |
| retryableFailureKinds | 既存9通信失敗種類の部分集合 |
| retryableStatusCodes | 408、425、429、500、502、503、504の部分集合 |
| initialDelayMilliseconds | 0〜60,000 |
| maximumDelayMilliseconds | 0〜300,000。initial以上 |
| backoffMultiplier | 1または2 |
| respectRetryAfter | 真偽 |
| maximumRetryAfterMilliseconds | 0〜300,000 |
| methodSafety | `safeOnly`、`idempotencyKey`、`apiGuaranteed` |
| idempotencyKeyHeader | `idempotencyKey`のときだけ必須。既存header名文法に適合 |
| minimumStartIntervalMilliseconds | 0〜60,000。接続単位の開始間隔下限 |
| finalFailurePolicy | `disabled`、`optional`、`required` |

`safeOnly`はGETとHEADだけを複数回送ります。`idempotencyKey`はGET・HEADに加え、要求に指定headerが1個あり
空でない場合だけPOST・PUT・PATCH・DELETEを複数回送ります。`apiGuaranteed`は、API側の冪等性保証を確認した
ホストだけが設定でき、6methodを対象にできます。方針が複数回を許さないmethodでは、再試行対象の応答・失敗も
そのまま最終結果にします。

初版はリダイレクトを引き続き禁止し、再試行中も対象URI、method、header、本文を変更しません。冪等性キーを
実行器が生成しません。OAuth、資格情報更新、回路遮断、並行送信、接続プールの公開意味論は含めません。

## 3. 再試行判定

resolverは送信語1回につき最初に1回だけ呼びます。初回の送信予算と要求本文試行予算はHTTPS機能グループ互換で
resolver前に予約し、追加試行は各待機後に予約します。接続単位の開始間隔を検査し、`http.send`を
最大`maximumAttempts`回呼びます。再試行のために
resolverを呼び直さず、途中で方針や資格情報参照を差し替えません。

各結果の判定順は次です。

1. 取消、資格情報問題、能力契約違反、全実行資源診断は直ちに終了する
2. 完全応答の本文・headerと受信予算を、再試行判定より先に検査・課金する
3. methodが複数回送信可能で、残り試行があり、失敗種類またはstatusが方針集合に含まれる場合だけ再試行する
4. それ以外は既存の結果値を返す

接続解決失敗、method不許可、要求サイズ超過、能力不在、取消、資格情報失敗、応答累積超過は再試行しません。
`responseTooLarge`を含む通信失敗は、方針に明示されていても本文を小さくできないため再試行集合へ指定できません。

## 4. 待機と`Retry-After`

方針待機は、試行番号2の前を`initialDelayMilliseconds`とし、その後は倍率を掛けて
`maximumDelayMilliseconds`で打ち止める決定的backoffです。jitterは初版に含めません。

再試行対象の完全応答に大小文字非区別で`Retry-After`がちょうど1値ある場合、`respectRetryAfter=true`なら
次を解釈します。

- 10進のdelta-seconds。上限超過を避けてミリ秒へ変換する
- RFC 9110のIMF-fixdate。ホストのUTC現在時刻との差を0以上へ切り上げる

複数値、不正値、過去日時は0として扱い、秘密を含む診断にしません。採用待機はbackoffと`Retry-After`の大きい方で、
`maximumRetryAfterMilliseconds`および300,000ミリ秒を超えません。再試行直前の応答に解釈可能なHTTP-dateが
実際に現れた場合だけ壁時計能力を要求し、正の待機が実際に必要な場合だけ待機能力を要求します。
能力不足・失敗・取消は捕捉不能診断です。

接続単位の`minimumStartIntervalMilliseconds`は、同じ実行内で直前の物理試行開始から次の開始までの不足分を
待機します。backoff、`Retry-After`、開始間隔の待機は重ねて足さず、次の許可開始時刻の最大値まで1回待ちます。
待機区間は既存の30秒能動実行時間から除外します。

## 5. 資源と観測

既存のHTTP送信1,024回は送信語ではなく物理試行を数え、要求本文試行累積も物理試行ごとに加算します。
応答本文受信累積とバイト列構築は、破棄して再試行する完全応答・通信失敗の実読取分も加算します。

| 資源 | 上限 |
|---|---:|
| 1送信語の物理試行 | 8 |
| 1実行のHTTP信頼性待機要求合計 | 3,600,000 ms |
| 1実行の最終送信記録 | 1,024 |

`ExecutionResult`と`ProgramRunResult`へ物理試行数、再試行回数、信頼性待機要求ミリ秒、最終送信記録件数を
加算的に公開します。現行traceは送信語1命令につき最終試行の閉じた結果を1件保持し、物理試行数と再試行回数は
実行結果の集計値で観測します。URI、header、本文、`Retry-After`原文、待機理由の外部入力は公開しません。

## 6. 最終送信記録

実行環境は任意で`http.final-failure`能力を1個提供できます。記録は正規化済み論理接続名、静的method、
物理試行回数、最終分類`transportFailure|httpStatus`、通信失敗種類またはHTTP statusだけを持ちます。
要求、応答header、本文、URI、資格情報、例外、`Retry-After`原文は渡しません。

方針で記録を必須にした場合、再試行対象を使い切った最終結果をBSBへ返す前に1回だけ記録します。
能力不在・失敗・取消は診断であり、結果値へ変換しません。`optional`では能力がなくても従来どおり返します。
成功応答、再試行対象外の応答・失敗は記録しません。

## 7. CLI設定

CLI接続設定は`schema-version=2`でだけ信頼性方針を受理します。版1は意味を変えず、常に`none`です。
版2は接続テーブルへ`reliability`を1個追加し、未知項目、集合外status・失敗種類、矛盾するmethodSafety、
範囲外数値を実行前の秘密なし設定エラー78にします。

標準CLIはシステム待機・UTC壁時計を注入します。最終送信記録は、明示した名前付き作業領域内の登録済み論理
ファイルへ書く後続統合を待ち、版2初版では`disabled`だけを受理します。任意OS path、自動stderr出力、
暗黙のネットワーク送信は行いません。

## 8. 対象外

- jitter、適応的backoff、回路遮断、並行要求、公平な全接続スケジューラ
- リダイレクト、proxy、圧縮、streaming、接続プールの公開契約
- OAuth・Basic認証、資格情報の自動更新
- BSB値からのretry方針変更、動的status集合、任意header生成
- 永続デッドレターキュー、再投入、管理CLI
