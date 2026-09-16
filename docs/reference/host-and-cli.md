# 実行環境と CLI

## CLI の基本形

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar version
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar check [--json] SOURCE
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar explain --json SOURCE
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar format SOURCE
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run [RUN_OPTIONS] SOURCE [-- PROGRAM_ARGS...]
```

| コマンド | 動作 |
|---|---|
| `version` | 処理系の版とビルド時のコミットハッシュを標準出力へ表示する |
| `check` | ソースを実行せず、字句・構文・名前・型・スタック効果を検査する |
| `run` | 検査に成功したプログラムを実行する |
| `format` | 標準形へ整形したソースを標準出力へ表示する。元ファイルは変更しない |
| `explain --json` | 単語、束縛、能力、副作用などの静的説明を JSON で出力する |

`check --json` は診断を機械可読な1行 JSON で返します。プログラムへ渡す起動引数はソースファイルの後ろの
`--` で分離します。

`run` のオプションは `--connections FILE`、`--workspaces FILE`、反復可能な
`--file WORKSPACE LOGICAL_NAME ACCESS OS_PATH` です。すべて `SOURCE` より前へ置きます。
`ACCESS` は `read`、`write`、`read-write` のいずれかです。`--file` と `--workspaces` は相互排他で、
どちらも `--connections` とは併用できます。`check`、`explain`、`format` にはこれらの実行時設定を
指定できません。`--json` は `check` または `explain` の直後にだけ置けます。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run samples/12-command-line-arguments.bsb -- 赤 青 緑
```

## コンソール

標準出力は `表示する`、`一行表示する`、`改行する`、標準エラーは対応する `エラー...` 語を使います。
一行入力は `入力結果` を返し、入力行、終端、取消を判定してから取出しまたは破棄します。

```text
一行を入力する
入力行である ならば
    入力行を取り出す
    一行表示する
さもなければ
    入力結果を捨てる
つぎに
```

## 起動情報と時刻

`起動引数を得る` は `配列<文字列>`、`プログラム名を得る` と `プログラムの場所を得る` は文字列を返します。
場所は OS パスではなく URI 文字列です。

`待つ` は非負のミリ秒を受け取ります。`単調ミリ秒を得る` は経過時間の計測、`現在日時を得る` は壁時計の
取得に使います。実行環境が対応能力を提供しない場合、実行時診断になります。

## 論理接続と HTTPS

外部接続は、BSB ソースに URI や認証情報を書かず、静的な論理名で宣言します。実行時にホストが論理名を
有限な設定へ解決します。標準 CLI では `--connections` で TOML を明示します。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run \
  --connections samples/httpbin-connections.toml \
  samples/21-httpbin-ip.bsb
```

対応する method は GET、POST、PUT、PATCH、DELETE、HEAD です。要求は不変値として組み立て、送信結果は
`結果<HTTP応答,HTTP送信失敗>` で受け取ります。接続設定では有限再試行や開始間隔を指定できます。

### formとHTTP status

`文字列表をフォームURL符号化する`は、名前・値の2要素を持つ文字列二次元配列から
`application/x-www-form-urlencoded`本文を作ります。順序と重複を保持し、spaceは`+`、その他の予約文字と
Unicode文字はUTF-8 byte単位の`%HH`にします。Content-Typeと本文は要求へ明示的に設定します。

`HTTP要求を送信する`は4xx・5xxも完成した`HTTP応答`として成功側へ返します。
`HTTP応答を成功状態として検査する`を使うと、200〜299を成功側、300〜599を失敗側に分類できます。
返値は`結果<HTTP応答,HTTP応答>`で、どちらの側にも元の応答がそのまま保持されます。

### Basic・Bearer認証

標準CLIではschema version 3が`none`、`api-key`、`basic`、`bearer`を受理します。Basicはusernameとpassword、
Bearerはtokenを、直接値または環境変数名の正確に一方で指定します。秘密を版管理対象ファイルへ保存しないため、
通常は環境変数形を使います。

```toml
schema-version = 3

[connections."Basic API"]
base-uri = "https://api.example.com/"
allowed-methods = ["POST"]

[connections."Basic API".authentication]
kind = "basic"
username-env = "BSB_API_USERNAME"
password-env = "BSB_API_PASSWORD"

[connections."Bearer API"]
base-uri = "https://api.example.com/"
allowed-methods = ["GET"]

[connections."Bearer API".authentication]
kind = "bearer"
token-env = "BSB_API_BEARER_TOKEN"
```

Basicのusernameには`:`を含められません。Bearer tokenは非空の規範ASCII文字列です。資格情報と生成した
Authorization headerはBSB値、診断、trace、説明JSONへ公開されません。利用者が要求へ`Authorization`を
設定することもできません。schema version 1・2ではBasic・Bearerと版3専用keyを拒否します。

### gzip・deflate応答

transportは`Accept-Encoding: gzip, deflate`を送信時に追加し、gzipまたはzlib形式deflateの応答を上限付きで
自動展開します。公開される本文は展開済みで、展開した応答からは`Content-Encoding`と`Content-Length`が
除かれます。未知・多段coding、壊れたstream、展開上限超過は`contentDecodingFailure`です。この失敗は既定の
再試行対象ではありません。利用者が`Accept-Encoding`を設定することはできません。

## 名前付き作業領域とファイル

ファイルも静的な作業領域名と論理ファイル名を使います。OS パスと read/write 権限はホスト側で登録します。
CLI では `--workspaces` の TOML、または反復可能な `--file` を明示します。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run \
  --file 帳票 入力.dat read input.dat \
  --file 帳票 出力.dat write output.dat \
  program.bsb
```

読取りはファイル全体のバイト列、書込みは完全バイト列の原子的置換です。任意の OS パス操作、ディレクトリ列挙、
暗黙の設定探索は行いません。

## CSV と TSV

CSV/TSV は文字列と `配列<配列<文字列>>` の間で変換します。入力は CR、LF、CRLF と先頭 BOM を扱い、
出力は BOM なし CRLF です。解析時の文法失敗は結果値、入力サイズや行列数などの上限超過は診断になります。

## 診断と終了

診断はコード、severity、処理段階、ソース位置、構造化 field、期待値と実際値、関連位置、修正案を持ちます。
CLI は成功、使用法、I/O、静的検査、字句・構文、実行時、内部失敗を異なる終了コードで区別します。

外部能力を使うプログラムでも、`check` はホスト設定やネットワークへ接続せずに静的検査できます。
`run` の前に `check` を使い、必要な論理接続・作業領域は `explain --json` で確認できます。

## 詳細仕様

- [コンソール](../spec/host-io-console.md) / [実行環境](../spec/host-io-environment.md)
- [CLI 診断 JSON](../spec/cli-json.md) / [説明 JSON](../spec/explain-json.md)
- [CLI 接続設定](../spec/cli-connection-config.md)
- [論理接続](../spec/logical-connections.md)
- [HTTPS](../spec/https.md) / [HTTP 信頼性](../spec/http-reliability.md)
- [HTTP API相互運用](../spec/http-api-interoperability.md)
- [作業領域とファイル](../spec/workspace-files.md)
- [CSV/TSV](../spec/delimited-tables.md)
- [作業領域・ファイル・表の統合仕様](../spec/workspace-tables.md)
