# 1. 最初のプログラム

この章では BSB のプログラムを1つ実行し、ソースファイルの基本形と4つの CLI コマンドを覚えます。

## 準備

BSB のビルドには JDK 25 を使います。リポジトリのルートで次を実行してください。

```shell
./gradlew shadowJar
```

Windows では `./gradlew` を `.\gradlew.bat` に置き換えます。以降の例で使う JAR は
`build/libs/bsb-0.1.0-SNAPSHOT-all.jar` です。

## Hello, World!

最初のプログラムは [`samples/01-hello-world.bsb`](../../samples/01-hello-world.bsb) にあります。

```text
メインとは （--）
    「Hello, World!」 を 一行表示する
こと。
```

実行します。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run samples/01-hello-world.bsb
```

`Hello, World!` と表示されれば成功です。

## プログラムの形

BSB は `メイン` から実行を始めます。

```text
メインとは （--）
    # ここへ処理を書く
こと。
```

`とは` で定義を始め、`こと。` で閉じます。`（--）` はメインが値を受け取らず、値を返さないことを
表します。`#` から行末まではコメントです。

文字列は `「` と `」` で囲みます。`一行表示する` は値を1個受け取り、表示して改行します。
助詞の `を` はコードを日本語として読みやすくしますが、処理結果には影響しません。

## 実行前に検査する

`check` はプログラムを実行せずに、構文、名前、型、スタック上の値の流れを検査します。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar check samples/01-hello-world.bsb
```

問題がなければ何も表示せず、終了コード0で終わります。ネットワークへ送信したりファイルへ書き込んだりする
プログラムを、実際に動かす前に検査したいときに便利です。

## 整形と説明

`format` はソースを標準形に整え、その結果を標準出力へ表示します。元のファイルは上書きしません。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar format samples/01-hello-world.bsb
```

`explain --json` は、プログラムが使う単語や型、実行に必要な能力（ファイル操作や HTTPS 通信など、実行環境が
提供する機能）を、ツールで処理しやすい JSON 形式で出力します。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar explain --json samples/01-hello-world.bsb
```

普段は `check` と `run`、書式を確認するときは `format`、ツールから解析結果を利用するときは
`explain --json` と覚えておけば十分です。

## 試してみよう

サンプルを別のファイルへコピーし、表示する文字列を自分の言葉へ変えて実行してみましょう。
次の章では、値がプログラム内をどのように流れるかを説明します。

<details>
<summary>回答例</summary>

たとえば `hello.bsb` を次の内容で作ります。

```text
メインとは （--）
    「BSBへようこそ！」 を 一行表示する
こと。
```

リポジトリのルートから検査し、実行します。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar check hello.bsb
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run hello.bsb
```

```text
BSBへようこそ！
```

表示する文字列は自由です。`check` が何も表示せず終了し、`run` で書いた文字列が表示されれば正解です。

</details>

[次へ: 値の流れと単語](02-stack-and-words.md)
