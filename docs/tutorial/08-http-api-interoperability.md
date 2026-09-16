# 8. HTTP APIを呼び出す

BSBでは接続先と認証情報をソースへ書かず、論理接続としてホスト側から与えます。この章ではform本文を作り、
HTTPSで送信し、通信結果とHTTP statusを別々に判定します。Basic・Bearer認証と圧縮応答は接続設定とtransportが
扱うため、BSBプログラムが資格情報や圧縮形式を操作する必要はありません。

## form本文を作る

`文字列表をフォームURL符号化する`は、名前と値の組を持つ二次元文字列配列を
`application/x-www-form-urlencoded`形式へ変換します。

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

外側配列の順序と同名項目の重複は保たれます。各内側配列は正確に名前と値の2要素にします。spaceは`+`、
それ以外の予約文字やUnicode文字はUTF-8 byteごとの`%HH`になります。この語は本文や`Content-Type`を
自動設定しないため、要求へ明示的に設定します。

## 要求を送り、2種類の結果を判定する

HTTP APIでは、通信できたかどうかとstatusが成功範囲かどうかを分けて扱います。

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
    「v1/forms」を HTTP要求に経路を設定する
    「Content-Type」と 「application/x-www-form-urlencoded」を HTTP要求にヘッダーを設定する
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

`HTTP要求を送信する`の結果は通信の成否です。接続失敗やtimeoutなら失敗側ですが、HTTP 404や500は
通信が完了しているため成功側の`HTTP応答`です。その応答を`HTTP応答を成功状態として検査する`へ渡すと、
200〜299は成功側、300〜599は失敗側になります。status失敗側にも元の応答がそのまま残るため、status、header、
本文を調べられます。

## Basic・Bearer認証を設定する

標準CLIではschema version 3の接続設定を使います。次はBasic認証の例です。

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

Bearer認証では認証部分を次のようにします。

```toml
[connections."Bearer API".authentication]
kind = "bearer"
token-env = "BSB_API_BEARER_TOKEN"
```

秘密はソースや版管理対象のTOMLへ直接書かず、実行プロセスの環境変数から渡します。

```shell
export BSB_API_USERNAME='set-this-in-your-shell'
export BSB_API_PASSWORD='set-this-in-your-shell'
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run \
  --connections samples/http-api-connections.toml.example \
  samples/26-http-api-interoperability.bsb
```

認証headerはtransportが送信直前に生成します。BSBソースから`Authorization`や`Accept-Encoding`を設定することは
できません。

## 圧縮応答を受け取る

transportは`gzip`とzlib形式の`deflate`を要求し、受信時に上限付きで自動展開します。
`HTTP応答から本文を取り出す`が返すのは展開済みの完全本文です。展開した応答では、内容と一致しなくなる
`Content-Encoding`と`Content-Length`は公開headerから除かれます。

壊れた圧縮stream、未知または多段のcontent coding、展開上限超過は正常なHTTP応答として公開されません。
codecの失敗は`contentDecodingFailure`という通信失敗になり、既定では再試行されません。

現在は同期送信と完全bufferだけに対応しています。multipart、redirect、proxy、custom CA、mTLS、OAuth token取得、
streamingはこの機能の対象外です。

実行できる全体例は
[`samples/26-http-api-interoperability.bsb`](../../samples/26-http-api-interoperability.bsb)と
[`samples/http-api-connections.toml.example`](../../samples/http-api-connections.toml.example)にあります。
正確な上限と失敗条件は[HTTP API相互運用仕様](../spec/http-api-interoperability.md)を参照してください。
