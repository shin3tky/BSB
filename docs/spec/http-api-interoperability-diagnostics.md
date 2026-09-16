# HTTP-API機能グループ: 診断と失敗境界

## 1. form診断

次の診断は重要度error、runtime段階、終了コード10です。いずれも入力stackを変更しません。

| コード | 条件 | 必須fields |
|---|---|---|
| `E_HTTP_FORM_ROW_WIDTH` | 内側配列の要素数が2でない | `word`、1始まりの`row`、`expectedColumns=2`、`actualColumns` |
| `E_HTTP_FORM_ITEM_LIMIT` | 外側配列が256項目を超える | `word`、`limitName=httpFormItems`、`limit=256`、`observed` |

出力文字列または作業量の超過は既存の文字列・byte列作業診断を使います。診断へ名前、値、符号化済み本文を
含めません。

## 2. 資格情報診断

既存の`E_HTTP_CREDENTIAL_NOT_CONFIGURED`、`E_HTTP_CREDENTIAL_ACCESS_DENIED`、
`E_HTTP_CREDENTIAL_INVALID`を`apiKey`、`basic`、`bearer`へ共通化します。`authenticationKind`は実際の分類です。
不正理由は次の閉じた値だけです。

```text
HEADER_NAME_INVALID
HEADER_VALUE_INVALID
BASIC_USERNAME_INVALID
BEARER_TOKEN_INVALID
```

`E_HTTP_AUTHENTICATION_UNSUPPORTED`は`oauth2`にだけ用います。資格情報、環境変数値、Authorization、参照identity、
検証対象文字列はmessage、field、expected、actual、fix、trace、能力イベントへ含めません。

## 3. content decoding

次の外部データ由来の問題は診断ではなく`失敗(HTTP送信失敗)`へ変換します。

| 条件 | 種類 |
|---|---|
| 未知・複数・多段`Content-Encoding` | `contentDecodingFailure` |
| gzipまたはzlib deflateの破損・途中終了 | `contentDecodingFailure` |
| 展開後本文上限または展開率上限の超過 | `contentDecodingFailure` |

wire本文上限超過は従来どおり`responseTooLarge`です。wire受信累積超過とbyte列作業超過は既存の捕捉不能資源診断です。
部分展開本文、codec例外、応答本文は失敗値と診断へ保持しません。

## 4. 判定順

送信前の方針・認証検査、wire headerと`Content-Length`検査、wire読取、coding検査、上限付き展開、wire受信課金、
復号作業課金、結果stack置換の順です。再試行判定は完全応答または閉じた送信失敗を検査・課金した後に行います。
