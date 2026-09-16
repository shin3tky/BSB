# HTTPS機能グループ: 最小HTTPSの適合データ・テスト計画

> 本文は現行実装の規範契約です。

規範は[最小HTTPS](https.md)と
[診断・失敗境界](https-diagnostics.md)です。本番Java、JDK HTTPクライアント、実通信の出力を
期待生成元にしません。

## 1. 物理構成と独立性

`tests/conformance/https`はcatalog、URI vector、transport期待、資源境界、26新規診断、trace、
公開BSB source/canonical/chapter、message、manifestからなります。全詳細行はcatalog IDへ属し、
manifestはREADMEとmanifest自身を除く全物理ファイルのSHA-256とバイト数を固定します。

URI期待は仕様のUTF-8 percent encodingを独立Python oracleで再計算します。transport期待は完全応答、
9失敗種類、取消、資格情報3状態、resolver・transport回数を閉じた表として固定します。実DNS、socket、TLS、
時計待ちを使いません。巨大境界は物理展開せず閉じたrecipeと仕様数値で表します。

## 2. 正常例 HTTPS-N001〜012

| ID | 必須観測 |
|---|---|
| HTTPS-N001 | HTTP 3型、空要求、不変設定、保存・合流・結果型利用 |
| HTTPS-N002 | 6methodの静的引数、format、直接・推移能力要求 |
| HTTPS-N003 | pathのUTF-8 percent encoding、基底path保持、origin不変 |
| HTTPS-N004 | queryの空名値、重複、順序、space `%20`、Unicode |
| HTTPS-N005 | header名大小文字、同名置換、応答同名値順序、欠落空配列 |
| HTTPS-N006 | JSON・文字列・バイト列本文、本文不在・長さ0、Content-Type非自動 |
| HTTPS-N007 | 200、204、3xx、4xx、5xxを完全応答として成功にする |
| HTTPS-N008 | 10通信失敗を失敗結果にし、種類だけ取り出す |
| HTTPS-N009 | `none`とAPIキー注入、利用者同名headerを秘密値で置換 |
| HTTPS-N010 | resolver 1回、transport 1回、redirect 0、retry 0、2 timeout |
| HTTPS-N011 | HTTP 3型とラッパーの完全trace非開示、静的説明の非開示 |
| HTTPS-N012 | stdin JSONから要求・送信・status/header/body・応答JSONまでの章末縦断 |

## 3. 失敗例 HTTPS-F001〜014

| ID | 必須観測 |
|---|---|
| HTTPS-F001 | 静的引数の開始、接続名、method、個数、終端、method値の6診断 |
| HTTPS-F002 | 未宣言接続、通常値参照、到達不能抑止 |
| HTTPS-F003 | pathの5理由と8,192／8,193境界 |
| HTTPS-F004 | query 256／257、65,536／65,537境界 |
| HTTPS-F005 | header名・値文法、予約11名、個数・総量境界 |
| HTTPS-F006 | method不許可、GET・HEAD本文あり、要求本文方針超過 |
| HTTPS-F007 | 接続未設定・拒否・10設定不正理由 |
| HTTPS-F008 | OAuth未対応 |
| HTTPS-F009 | APIキー未設定・拒否・名不正・値不正 |
| HTTPS-F010 | 能力不在、resolver例外、transport例外 |
| HTTPS-F011 | 取消は捕捉不能で失敗結果にしない |
| HTTPS-F012 | 1xx・101・不正status/headerをprotocol失敗にする |
| HTTPS-F013 | 入力不足、型違い、3型の表示・比較・配列要素禁止 |
| HTTPS-F014 | 対象URI65,536／65,537境界 |

## 4. 資源・内部境界 HTTPS-R001〜010

| ID | 必須観測 |
|---|---|
| HTTPS-R001 | send 1,024／1,025、拒否予約0追加 |
| HTTPS-R002 | 要求試行134,217,728／1超過、resolver前加算 |
| HTTPS-R003 | 応答受信134,217,728／1超過、越境バイト非加算 |
| HTTPS-R004 | metadata構築16,777,216／1超過、共有metadata再加算なし |
| HTTPS-R005 | 接続方針本文67,108,864／1超過とContent-Length早期判定 |
| HTTPS-R006 | 応答header 128／129、65,536／65,537 |
| HTTPS-R007 | HTTP受信とバイト列構築の二重課金、後段診断の原子性 |
| HTTPS-R008 | timeout待機を能動30秒から除外し、例外時も除外区間を閉じる |
| HTTPS-R009 | URI、APIキー、header、本文、TLS、例外markerの全公開面非漏えい |
| HTTPS-R010 | 64 MiB本文、128 MiB累積、traceを最大ヒープ512 MiBで統合 |

## 5. 中央適合と受入ゲート

1. `python3 tools/https_data.py --check`が全表、独立URI oracle、閉じた状態、資源、manifestを検査する。
2. Python単体試験がUTF-8 percent encoding、失敗集合、改ざん検出を本番Javaなしで確認する。
3. Java classpath loaderが同じ物理資源をBOMなしUTF-8、LF、固定headerで厳格に読む。
4. 全36 IDを中央カタログから過不足なく消費する。
5. 最終的に新13語を`FULL`、新26診断を`CONFORMANCE`にし、既存分類を低下させない。
6. 公開5コマンドを章末・利用者sampleで各2回、通常・traceの能力回数と結果を一致させる。

期待と実装が食い違った場合は規範を再確認し、理由を記録せずgoldenを再生成しません。
