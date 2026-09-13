# 実装・配布ガイド

この文書は、BSB 処理系のビルド、内部実行方式、テスト、資源制限、CLI の現行基準をまとめます。
言語の意味論は[最小言語仕様の概説](language-overview.md)と[詳細仕様](spec/README.md)を正本とします。

## Java 実行環境とビルド

- Java Toolchain とコンパイル対象は JDK 25
- ビルド定義は Gradle Kotlin DSL の `build.gradle.kts`
- Gradle Wrapper は 9.7.1
- 基底パッケージは `jp.bsb`、成果物座標は `jp.bsb:bsb`
- CLI のメインクラスは `jp.bsb.cli.BsbMain`
- `application` と `com.gradleup.shadow` 9.6.1 で実行可能な fat JAR を生成
- Java ソースは Google Java Format 1.36.0 を使う Spotless で検査
- カバレッジレポートは JaCoCo 0.8.14 で生成

依存ライブラリは ICU4J 76.1、RE2/J 1.8、TOML 解析用の tomlj 1.1.1 です。依存ライブラリの型や
例外は、言語仕様と公開 JSON へ直接露出させません。

```shell
./gradlew clean spotlessCheck test jacocoTestReport javadoc shadowJar
```

生成した JAR は次の形で起動できます。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar check samples/01-hello-world.bsb
```

## テスト

テストには JUnit 6.1.3 の Jupiter を使い、JUnit Vintage は使いません。

- クラス単位の振る舞いは単体テストで検査する
- Unicode、数値、診断位置、資源上限はパラメータ化された境界値で検査する
- `.bsb`、標準出力、標準エラー、終了コードを適合性データとして分離する
- フォーマッタは二度適用して結果が変わらないことを検査する
- I/O、時刻、HTTPS、ファイルは差し替え可能な偽能力で再現する
- 通常実行と同じ IR に `TraceSink` を注入し、命令単位の状態を検査する
- 巨大境界ケースを含む全テストを最大ヒープ 512 MiB で実行する

適合性データの配置とケース ID は[機能グループと適合性テスト](conformance.md)に従います。

診断と組み込み語の網羅性は次の監査コマンドで確認します。

```shell
python3 tools/diagnostic_coverage.py --format json --strict
python3 tools/builtin_word_coverage.py --format json --strict
```

## IR と実行

処理は次の順序で行います。

```text
UTF-8 ソース
  → トークン
  → 構文木
  → 名前・型・スタック効果の検査
  → 実行用 IR
  → JVM 上のインタープリタ
```

検査に失敗した構文木から IR を生成しません。IR は定数配置、呼び出し、分岐、反復、保存領域操作、
復帰などの小さな命令で構成します。各命令は元ソース位置を持ちますが、助詞そのものは実行命令に
しません。IR の直列化形式と互換性は公開 API ではありません。

## 基本資源上限

上限値そのものを受理し、一単位でも超える操作は結果を確保または公開する前に拒否します。

| 対象 | 上限 |
|---|---:|
| 一つのソースファイル | 32 MiB |
| 正規化後の識別子 | 128 Unicode コードポイント |
| 一つのソースのトークン | 250,000 |
| トップレベル定義 | 10,000 |
| 構文・型の入れ子 | 256 |
| 一度に保持する診断 | 100 |
| 一つのプログラムの IR 命令 | 250,000 |
| 一つの文字列 | 16 MiB（UTF-8） |
| 数値リテラル | 4,096 桁 |
| 整数・小数結果の有効桁 | 65,536 桁 |
| 小数スケールの絶対値 | 65,536 |
| データスタック | 65,536 値 |
| 呼出スタック | 1,024 フレーム |
| 一回の実行命令 | 10,000,000 |
| 能動実行時間 | 30 秒 |
| stdout / stderr | それぞれ 64 MiB |
| 入力行 | 16 MiB |
| 一回の実行で受け取る生入力 | 64 MiB |
| 起動引数 | 65,536 個、合計 64 MiB |

文字列、正規表現、配列、JSON、結果値、バイト列、HTTPS、ファイル、CSV/TSV は、これに加えて
機能固有の構築量、走査量、要素数、本文量などの上限を持ちます。正確な数え方、診断コード、失敗時の
原子性は各[診断仕様](spec/README.md)に従います。

資源超過を切り詰めて成功扱いにせず、上限名、上限値、観測値、可能な場合はソース位置を診断へ
含めます。通常の資源超過を `OutOfMemoryError` や `StackOverflowError` 任せにしません。

## CLI

公開コマンドは次のとおりです。

```text
bsb check <ソース.bsb>
bsb check --json <ソース.bsb>
bsb explain --json <ソース.bsb>
bsb run <ソース.bsb> [-- <引数>...]
bsb run --connections <設定.toml> <ソース.bsb> [-- <引数>...]
bsb run --workspaces <設定.toml> <ソース.bsb> [-- <引数>...]
bsb run --file <作業領域名> <論理名> <read|write|read-write> <OSパス> [--file ...] <ソース.bsb>
bsb format <ソース.bsb>
```

`check` は実行前検査、`run` は検査後の実行、`format` は入力を上書きしない正規整形です。
`explain --json` は静的説明を返します。人間向け診断と CLI 自身のエラーは stderr、プログラム出力と
整形結果は stdout へ分離します。

終了コードは次のとおりです。

| コード | 意味 |
|---:|---|
| 0 | 成功。警告だけの場合も成功 |
| 2 | コマンドまたは引数の使用法エラー |
| 3 | ソースや出力の I/O エラー |
| 8 | 名前、型、スタック効果などの静的エラー |
| 9 | UTF-8、字句、構文のエラー |
| 10 | プログラムの実行時エラーまたは実行時資源超過 |
| 70 | 処理系内部エラー |
| 78 | 明示した接続・作業領域設定のエラー |

構文系の検査に失敗した場合は静的検査へ進まず、静的検査に失敗した場合は実行しません。診断 JSON の
完全な契約は [CLI JSON 診断](spec/cli-json.md)、接続設定は
[CLI 接続設定](spec/cli-connection-config.md)に従います。
