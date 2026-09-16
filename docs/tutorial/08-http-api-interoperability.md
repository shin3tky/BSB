# 8. HTTP API を呼び出す

BSB では、接続先の URL や認証情報をソースに書きません。これらは論理接続として、実行時にホスト側（標準 CLI では
接続設定ファイル）から与えます。この章ではフォーム形式の本文を作って HTTPS で送信し、通信の成否と HTTP の
ステータスコードを別々に判定します。Basic・Bearer 認証と圧縮応答の展開は、接続設定と、実行環境の HTTP 送受信
処理（以下、transport）が扱います。そのため、BSB プログラムで資格情報や圧縮形式を操作する必要はありません。

## フォーム形式の本文を作る

`文字列表をフォームURL符号化する` は、名前と値の組を並べた二次元の文字列配列を
`application/x-www-form-urlencoded` 形式へ変換します。

```text
メインとは （--）
    【【「name」、「山田 太郎」】、【「tag」、「a/b」】、【「tag」、「二つ目」】】
    文字列表をフォームURL符号化する
    一行表示する
こと。
```

結果は次の文字列です。

```text
name=%E5%B1%B1%E7%94%B0+%E5%A4%AA%E9%83%8E&tag=a%2Fb&tag=%E4%BA%8C%E3%81%A4%E7%9B%AE
```

外側の配列の順序はそのまま保たれ、同じ名前の項目が複数あってもまとめられません。内側の各配列は、名前と値の
ちょうど2要素にします（それ以外は実行時エラー `E_HTTP_FORM_ROW_WIDTH`）。空白は `+` に、英数字と `*`、`-`、
`.`、`_` 以外の文字は UTF-8 のバイトごとに `%HH` 形式になります。この単語は文字列を返すだけで、要求の本文や
`Content-Type` ヘッダーは設定しません。次の節のように、要求へ明示的に設定してください。

## 要求を送り、2種類の結果を判定する

HTTP API の呼出しでは、「通信が完了したか」と「ステータスコードが成功を表すか」を分けて判定します。

```text
フォームAPIは 論理接続。
本文は 変数 「」。

応答状態を表示するとは （HTTP応答 --）
    HTTP応答を成功状態として検査する
    結果が成功である ならば
        結果から成功値を取り出す
        HTTP応答から状態コードを取り出す
        一行表示する
    さもなければ
        結果から失敗値を取り出す
        HTTP応答から状態コードを取り出す
        一行表示する
    つぎに
こと。

メインとは （--）
    【【「name」、「山田 太郎」】、【「tag」、「a/b」】】
    文字列表をフォームURL符号化する
    を 本文 に 入れる

    空のHTTP要求
    「v1/forms」 を HTTP要求に経路を設定する
    「Content-Type」 と 「application/x-www-form-urlencoded」 を HTTP要求にヘッダーを設定する
    本文 を HTTP要求に文字列本文を設定する
    HTTP要求を送信する<フォームAPI,POST>
    結果が成功である ならば
        結果から成功値を取り出す
        応答状態を表示する
    さもなければ
        結果から失敗値を取り出す
        HTTP送信失敗の種類を取り出す
        一行表示する
    つぎに
こと。
```

`HTTP要求を送信する` が返す `結果` は、通信の成否を表します。接続失敗やタイムアウトは失敗側になりますが、
HTTP 404 や 500 の応答は通信自体は完了しているため、成功側の `HTTP応答` になります。その応答を
`HTTP応答を成功状態として検査する` へ渡すと、ステータスコードが 200〜299 なら成功側、300〜599 なら失敗側の
`結果` になります。どちらの側にも元の応答がそのまま入っているため、失敗側でもステータスコード、ヘッダー、
本文を調べられます。

## Basic・Bearer 認証を設定する

標準 CLI では、TOML 形式の接続設定ファイルを `--connections` で指定します。Basic・Bearer 認証を使うには、
`schema-version = 3` の形式で書きます。次は、ソース中の論理接続 `フォームAPI` に Basic 認証を設定する例です。

```toml
schema-version = 3

[connections."フォームAPI"]
base-uri = "https://api.example.com/"
allowed-methods = ["POST"]

[connections."フォームAPI".authentication]
kind = "basic"
username-env = "BSB_API_USERNAME"
password-env = "BSB_API_PASSWORD"
```

Bearer 認証を使う接続では、`authentication` の表を次のように書きます（サンプルの設定ファイルでは、別の接続
`Bearer API` として定義しています）。

```toml
[connections."Bearer API".authentication]
kind = "bearer"
token-env = "BSB_API_BEARER_TOKEN"
```

ユーザー名、パスワード、トークンなどの秘密情報は、ソースやバージョン管理する TOML ファイルへ直接書かず、
環境変数で渡します。設定ファイルの `username-env` などには、値そのものではなく環境変数の名前を書きます。
次のコマンドの `set-this-in-your-shell` は、実際の値に置き換えてください。

```shell
export BSB_API_USERNAME='set-this-in-your-shell'
export BSB_API_PASSWORD='set-this-in-your-shell'
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run \
  --connections samples/http-api-connections.toml.example \
  samples/26-http-api-interoperability.bsb
```

`Authorization` ヘッダーは、transport が送信直前に生成します。BSB のソースから `Authorization` や
`Accept-Encoding` ヘッダーを設定することはできません。

## 圧縮応答を受け取る

transport はすべての要求に `Accept-Encoding: gzip, deflate` を付けます。`gzip` または zlib 形式の `deflate` で
圧縮された応答を受信すると、展開後の大きさの上限を確認しながら自動で展開します。`HTTP応答から本文を取り出す` は、
展開済みの本文全体を返します。展開した応答では、展開後の本文と合わなくなる `Content-Encoding` と
`Content-Length` ヘッダーが、BSB から参照できるヘッダーから除かれます。

次の場合は HTTP 応答として扱われず、`HTTP要求を送信する` の失敗側で、種類が `contentDecodingFailure` の
通信失敗になります。

- 圧縮データが壊れている、または途中で終わっている
- 未対応の圧縮形式が指定されている、または複数の圧縮形式が重ねて指定されている
- 展開後の本文が上限を超える

この失敗は、既定では再試行されません。

現在は、送信の完了を待つ同期送信と、応答本文全体をまとめて受け取る方式だけに対応しています。multipart 形式の
本文、リダイレクトの追跡、プロキシ、独自の CA 証明書、mTLS、OAuth のトークン取得、ストリーミングには
対応していません。

この章の内容をまとめた実行可能な例は
[`samples/26-http-api-interoperability.bsb`](../../samples/26-http-api-interoperability.bsb)と
[`samples/http-api-connections.toml.example`](../../samples/http-api-connections.toml.example)にあります。
正確な上限と失敗条件は[HTTP API 相互運用仕様](../spec/http-api-interoperability.md)を参照してください。

[次へ: CSV/TSV を扱う](09-csv-tsv.md)
