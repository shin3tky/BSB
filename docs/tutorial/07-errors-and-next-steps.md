# 7. エラーを読み、次へ進む

BSB は構文だけでなく、名前、型、スタックの形、実行環境の能力まで検査します。診断を上から順に読むと、
多くの問題は実行前に修正できます。

## まず `check` を使う

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar check path/to/program.bsb
```

診断には安定したコード、問題の位置、期待したもの、実際のもの、修正案が含まれます。先頭のエラーを直すと、
後続の派生エラーも消えることがあります。

よくある問題は次の5つです。

- 値が足りない: 呼び出す単語のスタック効果で入力数を確認する
- 型が違う: 暗黙変換はないため、変換単語を使うか値の作り方を直す
- 分岐・反復の出口が合わない: すべての経路で同じ数・型の値を残す
- 局所伝播の出力が合わない: 宣言出力末尾のラッパー、保持するprefix、`結果`の失敗型を確認する
- 名前が見つからない: 綴り、宣言位置、局所スコープ、予約名を確認する

ツールから扱う場合は `check --json` を使うと、同じ診断を1行 JSON で取得できます。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar check --json path/to/program.bsb
```

## 整形して構造を確かめる

入れ子や区切りが分かりにくいときは `format` の出力を確認します。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar format path/to/program.bsb
```

元ファイルは上書きしないため、出力を見ながら安全に比較できます。

## 次に読むもの

ここまでで BSB の基本的なプログラムを読み書きできます。次は目的に応じて進んでください。

- 書き方や型を調べる: [言語リファレンスマニュアル](../reference/README.md)
- 動くプログラムから学ぶ: [サンプル集](../../samples/README.md)
- HTTP APIを呼び出す: [HTTP APIを呼び出す](08-http-api-interoperability.md)
- HTTPS やファイルの規則を調べる: [外部との接続](../reference/host-and-cli.md)
- 厳密な境界や診断を確認する: [詳細言語仕様](../spec/README.md)

チュートリアルは基本概念を優先して一部の機能を省略しています。現在実装済みの範囲と非対応範囲は
[言語仕様の概説](../language-overview.md)で確認できます。
