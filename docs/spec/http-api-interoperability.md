# HTTP-API機能グループ: HTTP API相互運用

> 本文は第24段階で追加する現行実装の規範契約です。
>
> [HTTPS](https.md)、[HTTP信頼性](http-reliability.md)、
> [診断](http-api-interoperability-diagnostics.md)、
> [適合性](http-api-interoperability-conformance.md)と一体です。

## 1. 境界

HTTP-APIはcurlの挙動互換ではなく、一般的なHTTP APIとの相互運用に必要な最小機能を追加します。
同期送信、完全buffer、論理接続、HTTPS限定という既存境界は変えません。multipart、redirect、proxy、
custom CA、mTLS、OAuth token取得、streamingは対象外です。

## 2. form URL encoding

辞書末尾へ次の純粋語を追加します。

```text
文字列表をフォームURL符号化する
配列<配列<文字列>> -- 文字列
```

外側配列の順序と重複を保持し、各内側配列は正確に`[名前, 値]`の2要素とします。項目は最大256個です。
空配列は空文字列、空名と空値は許可します。ASCII英数字と`*`、`-`、`.`、`_`はそのまま、U+0020は`+`、
その他はUTF-8 byteごとの大文字`%HH`にし、`名前=値`を`&`で連結します。

出力は既存の16 MiB文字列上限に従います。処理系は出力全体を割り当てる前に長さを測り、入力UTF-8 byte数と
出力byte数を既存のbyte列作業予算へ課金します。この語はheaderや本文を暗黙設定しません。

## 3. HTTP statusの明示判定

辞書末尾へform語の後に次の純粋語を追加します。

```text
HTTP応答を成功状態として検査する
HTTP応答 -- 結果<HTTP応答,HTTP応答>
```

200〜299を成功側、300〜599を失敗側へ入れます。どちらも入力と同一の不変`HTTP応答`を保持し、status、header、
本文を失いません。通信失敗や資源診断は変換対象ではなく、`HTTP要求を送信する`の返値と再試行判定も変更しません。

2語は`featureGroup="HTTP-API"`、`typeRule="fixed"`、能力・副作用なしです。辞書総数は189語です。

## 4. Basic・Bearer認証

HTTPS送信は`none`、`apiKey`、`basic`、`bearer`を受理し、`oauth2`は未対応です。資格情報実体はtransport内部で
不透明参照identityに対応付け、方針、BSB値、診断、trace、説明JSONへ保持しません。

Basicは`UTF-8(username + ":" + password)`をRFC 4648 Base64化し、`Authorization: Basic ...`として送信直前に
生成します。usernameとpasswordは空を許しますが、username中の`:`を拒否し、生成header値は8,192 byte以下です。
Bearerは非空の`[A-Za-z0-9\-._~+/]+={0,}`だけを許し、`Authorization: Bearer ...`として送信直前に生成します。
利用者が`authorization`を設定することはできず、資格情報は設定読込み時と送信直前の両方で検証します。

CLI接続設定はschema version 3でBasic・Bearerを追加します。各値は直接値と`*-env`の正確に一方を指定します。
version 1・2の受理項目と意味は変えません。詳細は[CLI接続設定](cli-connection-config.md)に従います。

## 5. gzip・deflate自動展開

transportは全送信へ`Accept-Encoding: gzip, deflate`を追加し、利用者による`accept-encoding`設定を拒否します。
応答の`Content-Encoding`は欠落、`identity`、単一の`gzip`、単一のzlib wrapper付き`deflate`だけを受理します。
未知coding、複数値、多段coding、壊れたstream、gzip CRC不一致は`contentDecodingFailure`です。

`Content-Length`と実読取上限はwire byteへ適用します。展開後本文は接続方針の`maximumResponseBytes`以下、かつ
`65,536 + 100 * wireBytes`以下でなければなりません。上限検査は飽和演算と上限+1読取で行います。展開に成功した
gzip・deflate応答からは`Content-Encoding`と`Content-Length`を除き、展開済み本文を公開します。

`httpResponseReceivedBytes`はwire受信量を数えます。展開後本文の構築量と復号作業量は既存byte列予算へ別途課金し、
再試行で破棄する応答と途中復号失敗も実作業分を戻しません。`contentDecodingFailure`は既定の再試行対象ではありません。

## 6. 非開示と互換性

username、password、token、生成Authorization、form本文、圧縮・展開本文、codec例外は公開面へ出しません。
既存の4xx・5xxを完成応答として返す動作、redirect禁止、retry方針、CLI version 1・2、公開JSON schema version 1は
維持します。
