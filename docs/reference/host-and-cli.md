# 実行環境と CLI

## CLI の基本形

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar COMMAND [OPTIONS] SOURCE [-- PROGRAM_ARGS...]
```

| コマンド | 動作 |
|---|---|
| `check` | ソースを実行せず、字句・構文・名前・型・スタック効果を検査する |
| `run` | 検査に成功したプログラムを実行する |
| `format` | 標準形へ整形したソースを標準出力へ表示する。元ファイルは変更しない |
| `explain --json` | 単語、束縛、能力、副作用などの静的説明を JSON で出力する |

`check --json` は診断を機械可読な1行 JSON で返します。プログラムへ渡す起動引数はソースファイルの後ろの
`--` で分離します。

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
- [作業領域とファイル](../spec/workspace-files.md)
- [CSV/TSV](../spec/delimited-tables.md)
