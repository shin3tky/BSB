# BSB

BSB（Block Sentence Builder）は、日本語の文に近い表記で記述する、実験的な静的型付きスタック言語です。
Java で実装した処理系は、字句・構文解析、静的検査、整形、実行、機械可読な診断と静的説明を一体で提供します。

```text
メイン とは （ -- ）
    「こんにちは、世界！」を 一行表示する
こと。
```

現在の実装には、正確な整数・10進小数、Unicode文字列、正規表現、不変配列、条件分岐と反復、
定数・変数、コンソール／時刻／プロセス情報、JSON、JSON形状検証、任意値、結果値、不変バイト列、論理接続、
HTTPS、form URL encoding、Basic・Bearer認証、gzip・deflate自動展開、名前付き作業領域、複数ファイル、
CSV/TSV が含まれます。実装済み範囲と非対応範囲は
[最小言語仕様の概説](docs/language-overview.md)にまとめています。

## 必要な環境

- JDK 25
- 初回実行時に Gradle と依存関係を取得するためのネットワーク接続

Gradle Wrapper をリポジトリに同梱しているため、Gradle の別途インストールは不要です。

## クイックスタート

macOS / Linux では、clone 後に次のコマンドだけでサンプルを実行できます。

```shell
git clone https://github.com/shin3tky/BSB.git
cd BSB
./gradlew run --args="run samples/01-hello-world.bsb"
```

Windows（PowerShell / コマンドプロンプト）では、最後のコマンドを次のように置き換えてください。

```powershell
.\gradlew.bat run --args="run samples/01-hello-world.bsb"
```

初回のみ Gradle 本体と依存関係を自動取得するため、完了まで時間がかかることがあります。
`Hello, World!` と表示されれば実行成功です。

## ビルドと CLI の実行

テストを実行し、単独で起動できる JAR を作成します。

```shell
./gradlew clean test shadowJar
```

作成した JAR では、次の CLI 操作を試せます。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar check samples/01-hello-world.bsb
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar version
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run samples/01-hello-world.bsb
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar format samples/01-hello-world.bsb
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar explain --json samples/01-hello-world.bsb
```

`version` は処理系の版とビルド時のコミットハッシュを表示、`check` は静的検査、`run` は検査後の実行、`format` はソースを標準形へ整形して出力、`explain --json` は
語・束縛・能力・副作用の静的な説明を行います。`format` は入力ファイルを上書きしません。

公開前の一式を検証するコマンドは次のとおりです。

```shell
./gradlew clean spotlessCheck test jacocoTestReport javadoc shadowJar
python3 tools/diagnostic_coverage.py --format json --strict
python3 tools/builtin_word_coverage.py --format json --strict
```

## ドキュメント

- [ドキュメント案内](docs/README.md)
- [言語チュートリアル](docs/tutorial/README.md)
- [言語リファレンスマニュアル](docs/reference/README.md)
- [目的と設計原則](docs/vision.md)
- [実行基盤の構成](docs/runtime-architecture.md)
- [実装・配布ガイド](docs/implementation-guide.md)
- [言語仕様の概説](docs/language-overview.md)
- [詳細言語仕様](docs/spec/README.md)
- [機能グループと適合性テスト](docs/conformance.md)
- [サンプル](samples/README.md)
- [網羅性監査ツール](tools/README.md)

`docs` は公開ドキュメント、`docs_internal` は旧リポジトリから保持する設計・実装記録です。
公開仕様では機能グループ ID を使って実装と適合性テストを対応付けます。

## リポジトリ構成

```text
src/main/java/       処理系本体
src/test/java/       単体・統合・適合性テスト
tests/conformance/   機能グループ別の宣言的な適合データ
samples/             独立して実行できる BSB プログラムと埋込み例
docs/                公開ドキュメントと詳細言語仕様
tools/               診断・組み込み語・適合データの監査ツール
```

## プロジェクトの位置づけ

BSB は実験段階の処理系です。言語と CLI の挙動は適合性テストで固定していますが、安定版 API や
後方互換性はまだ保証しません。現時点の制約は[概説の「非対応範囲」](docs/language-overview.md#非対応範囲)を参照してください。

## 謝辞

BSB は、[スクリプツ・ラボ有限会社](https://www.scripts-lab.co.jp/)が開発・提供する
日本語プログラミング言語 Mind から強い影響を受けています。日本語によるプログラム表現と
スタック型言語の可能性を長年にわたり示してきた Mind と、その開発に携わった皆様に敬意を表し、
感謝いたします。

詳しくは[謝辞](ACKNOWLEDGEMENTS.md)をご覧ください。
