# tools/

BSBのテスト網羅性を客観的に検証するための補助スクリプト置き場です。プロジェクト本体のビルド（Gradle/JDK）とは独立して動作し、Python 3.9+の標準ライブラリのみに依存します。

## result-values_data.py — 結果型の適合データ生成

承認済み仕様から固定したN/F/R資源の作成オラクルと読取り専用検査です。BSB処理系を起動したり、
ソースを汎用に解析・実行したりはしません。全70 IDのうち指定された22 IDだけを扱います。

```shell
python3 tools/result-values_data.py --check
python3 -m unittest discover -s tools -p 'test_result-values_data.py' -v
python3 tools/result-values_data.py --input RESULT-R001-success-256
```

`--check`は資源の存在・バイト一致・生成ハッシュを検証し、`--input`は保存済みレシピから
入力を標準出力へ返します。どちらもファイルを変更しません。`--patch <ID>`はレビュー用の
apply_patch入力を標準出力へ出すだけで、自動適用しません。
`test_result-values_data.py`はカタログの不足・余剰・重複、構造化診断、位置、境界算術等を検査します。
これらの成功を未実装の結果型の言語適合成功とは扱いません。資源形式と作成済み／未実行範囲は
[result-values README](../tests/conformance/result-values/README.md)を参照してください。

## diagnostic_coverage.py — 診断コード網羅マトリクス

`docs/spec/*diagnostics*.md`・`docs/spec/cli-json.md`・`docs/spec/explain-json.md` が定義する診断コード（`E_*`・`W_*`）1つひとつについて、次のどこで実際に踏まれているかを機械的に集計します。

- 実装（`src/main/java`）に識別子として存在するか
- 適合フィクスチャ（`tests/conformance/**` の `diagnostics.tsv`・`resources.tsv`・`generated/*.properties`・`cli-json`/`explain-json` の `expected/*.json` 等。ただし `messages.properties` はコード網羅とは無関係な全コード分のメッセージ辞書なので除外）で踏まれているか
- 単体テスト（`src/test/java`）で言及されているか、そのうち実際に assert 近傍で検証されていそうか

結果は `CONFORMANCE`（宣言的フィクスチャで検証済み）／`UNIT_ONLY`（単体テストのみ）／`MENTIONED_ONLY`（言及はあるが検証している確証がない）／`UNCOVERED`（一切のテスト証跡なし）の4区分で分類します。新しい機能グループを追加しても、`docs/spec` と `tests/conformance` のディレクトリ構成に従っている限り自動的に拾われます。

### 使い方

```shell
python3 tools/diagnostic_coverage.py                     # テキストサマリーを標準出力へ
python3 tools/diagnostic_coverage.py --format markdown    # Markdown表を標準出力へ（PRやIssueに貼りやすい）
python3 tools/diagnostic_coverage.py --format json        # 機械可読JSON（コーディングエージェント向け）
python3 tools/diagnostic_coverage.py --format markdown --output tools/reports/diagnostic-coverage.md
python3 tools/diagnostic_coverage.py --strict             # UNCOVEREDが1件でもあれば終了コード1（CI向け）
```

`--output` を指定しない場合は常に標準出力へ書きます。`tools/reports/` へ書き出したファイルは実行のたびに内容が変わりうるスナップショットなので、`.gitignore` で追跡除外しています。最新の結果が必要なときは都度実行してください。

詳しい分類基準・既知のヒューリスティックの限界（何を検出でき、何を見逃しうるか）はスクリプト冒頭のdocstringに記載しています。まず `python3 tools/diagnostic_coverage.py --help` と、スクリプト本体の冒頭コメントを読んでから結果を解釈してください。

`--strict`が機械的に終了1とするのは`UNCOVERED`だけです。`UNIT_ONLY`、`MENTIONED_ONLY`、
仕様外コード、警告はJSONの`summary`と`warnings`を別に確認し、プロジェクトの完了基準として
弱い分類0・警告0を要求する場合は呼出し側で判定します。

## builtin_word_coverage.py — 組み込み語 x N/F/R 網羅マトリクス

`jp.bsb.stdlib.BuiltinDictionary`に登録されている全組み込み語（正規名・別名・機能グループ）1つひとつについて、`tests/conformance/**`の宣言的適合フィクスチャ上でN（正常例）・F（失敗例）のどこで実際に呼び出し・言及されているかを機械的に集計します。組み込み語一覧はJavaソースを直接構文解析するのではなく、実際にビルドした処理系の`explain --json`が返す`builtinWords`（使用の有無にかかわらず辞書の仕様順で返る、公開契約上の完全な一覧）を正として取得します。

結果は`FULL`（N・F双方の証跡あり）／`N_ONLY`／`F_ONLY`／`UNIT_ONLY`（適合フィクスチャには無いが`src/test/java`のテストソースには出現）／`UNCOVERED`（一切の証跡なし）の5区分で分類します。加えてR（資源境界）は証跡があるかどうかを参考情報として別掲します(`generated/*.properties`のようなR系フィクスチャは`generator=array-literal`のような抽象的なレシピが大半で、特定の単語名を含まないことが多いため、大多数の語でRの証跡が無いのは正常です)。

### 使い方

```shell
python3 tools/builtin_word_coverage.py                                    # build/libsのjarを自動検出して実行
python3 tools/builtin_word_coverage.py --jar build/libs/foo-all.jar        # jarを明示
python3 tools/builtin_word_coverage.py --words-json tools/reports/words.json  # jarを使わず既存のexplain --json出力を利用
python3 tools/builtin_word_coverage.py --format markdown --output tools/reports/builtin-word-coverage.md
python3 tools/builtin_word_coverage.py --strict     # UNCOVEREDが1件でもあれば終了コード1（CI向け）
```

**前提条件**: 既定の実行方法（`--jar`）は、本プロジェクトが要求するJDK 25で実行できる`java`と、事前にビルド済みのfat jar（`./gradlew shadowJar`）を必要とします。JDK 25を用意できない環境では、`--words-json`で他所で取得済みの`explain --json`出力（または`builtinWords`配列そのもの）を渡すことで代替できます。

詳しい分類基準・証跡の粒度（Fのstructured/text）・既知のヒューリスティックの限界はスクリプト冒頭のdocstringに記載しています。まず `python3 tools/builtin_word_coverage.py --help` と、スクリプト本体の冒頭コメントを読んでから結果を解釈してください。

`diagnostics.tsv`を走査するときは、`fields`列の`word=<組み込み語の正規名>`、または同じ意味を
持つ`target_lexeme`列をstructured F証跡として読みます。JSONの14列形式では末尾の
`reason`はcodec理由専用であり、組み込み語の証跡には使いません。こちらの`--strict`も
機械的に終了1とするのは`UNCOVERED`だけなので、`UNIT_ONLY`、`F_ONLY`、警告は別に確認します。

## 今後のテスト工程での利用

JaCoCoとこの2ツールを、今後の機能グループ、公開CLI、診断、組み込み語、適合フィクスチャを変更する
工程の標準チェックに含めます。対象テストを通した後と、各ステップのコミット前に関係する検査を
実行し、最終完了ゲートでは全回帰、JaCoCoレポート、fat JAR生成に続けて両ツールを実行します。

```shell
./gradlew clean spotlessCheck test jacocoTestReport javadoc shadowJar --no-build-cache --rerun-tasks
python3 tools/diagnostic_coverage.py --format json --strict
python3 tools/builtin_word_coverage.py --format json --strict
```

JaCoCoのHTML・XMLレポートは`build/reports/jacoco`以下に生成します。全体の行カバレッジ率だけで
完了を判定せず、変更したクラスとfrontend・analyzer・runtime・cliなどのパッケージごとに、
未到達行と分岐、変更前からの低下を確認します。JaCoCoはコードを通過した事実を測るもので、
宣言的フィクスチャの期待値やassertの強さまでは保証しません。

`builtin_word_coverage.py`は生成済みfat JARの`explain --json`から組み込み語一覧を取得するため、
必ず`shadowJar`より後に実行します。互換JDKまたはfat JARを利用できない環境だけ、由来を確認できる
既存出力を`--words-json`へ渡します。

確認項目は次のとおりです。

- JaCoCoレポートが生成され、変更箇所に説明できない未到達行・分岐やカバレッジ低下がない
- 両Pythonコマンドが正常終了し、JSONとして解釈できる
- `--strict`が終了0、`UNCOVERED`が0である
- `warnings`を目視し、入力形式変更や走査漏れでないことを確認する
- 診断コードに新規`MENTIONED_ONLY`・`UNIT_ONLY`がなく、既存`CONFORMANCE`が弱い分類へ低下していない
- 組み込み語に新規`UNIT_ONLY`がなく、既存`FULL`・`N_ONLY`が弱い分類へ低下していない
- 仕様変更による意図した差分や既知の誤検出は、実装計画または引き継ぎ文書へ根拠を記録する

2026-08-30の適合カバレッジ強化完了基準は、仕様記載の診断171件がすべて`CONFORMANCE`、
組み込み語107件が`FULL` 59・`N_ONLY` 48で、`UNIT_ONLY`・`F_ONLY`・`UNCOVERED`と両ツールの
警告が0件です。仕様外のテスト用`E_TEST`は診断総数に含まれますが、仕様記載171件の分母からは
除外します。組み込み語のR証跡は抽象的な生成レシピでは語名を含まないことが多いため、0件でも
完了失敗とはしません。両ツールは証跡の存在を測る補助検査であり、テスト内容の正しさを保証する
ものではないため、JaCoCoの未到達箇所、弱い分類、警告は必ず該当ファイルを目視確認します。

任意値完了後の2026-08-31現在は、診断176件のうち既存のテスト専用`E_TEST`を除く175件が
`CONFORMANCE`です。組み込み語112件は`FULL` 65・`N_ONLY` 47で、弱い分類と警告は0件です。
