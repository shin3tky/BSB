# HTTP-API機能グループ: 適合性

## 1. 必須観測

実装は次を独立した期待値で検査します。

| 区分 | 観測 |
|---|---|
| form | ASCII、space、Unicode、予約文字、空、順序、重複、行幅、256／257項目、原子性 |
| status | 200・299と300・599の境界、成功／失敗側、元応答identity、本文・header保持 |
| Basic | UTF-8、空値、username colon拒否、Base64、header上限、秘密非開示 |
| Bearer | 許可文字とpadding、空・空白・CRLF・非ASCII・上限拒否、秘密非開示 |
| CLI | version 1・2回帰、version 3直接値／環境変数、排他、未設定、未知key |
| compression | gzip、zlib deflate、identity、header正規化、未知・多段・破損、展開率、作業課金 |
| retry | 破棄応答のwire・復号課金、`contentDecodingFailure`の既定非再試行 |

## 2. 実行環境

通常の適合試験は差替えtransportまたはJDK client adapterを使い、実network、実資格情報、実URLへ依存しません。
圧縮fixtureは本番decoderの出力を期待値生成に使わず、展開後byte列を独立に固定します。全公開面へ検出markerを置き、
秘密、Authorization、URI、本文、codec例外が0件であることを確認します。

中央カタログは`tests/conformance/http-api-interoperability/catalog.tsv`です。`HTTPAPI-N001`はformの公開実行、
`HTTPAPI-N002`はHTTP応答を同型結果へ変える静的型経路を固定します。transport・認証・圧縮境界はHTTPSの中央データと
差替えJDK adapter試験で固定します。

## 3. 回帰条件

既存HTTPSの13語と返値、HTTP-RELの有限再試行、CLI schema version 1・2、`explain --json`版1を維持します。
新しい2語を含む組み込み辞書は189語で、末尾順は`文字列表をフォームURL符号化する`、
`HTTP応答を成功状態として検査する`です。
