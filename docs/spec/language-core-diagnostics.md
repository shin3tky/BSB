# CORE機能グループ: 診断と資源上限

> 本文は現行実装の規範契約です。
>
> EXP機能グループでは[指数診断仕様](exponent-literals-diagnostics.md)が、指数候補のreason、指数部を含む
> 4,096数字の数え方、指数リテラルの静的スケール診断を追加します。

## 1. 共通形式

各診断は次の情報を持ちます。

```text
code
severity
message
span、point、fileOffset のいずれか
word
expected
actual
relatedLocations
fixes
limitName
limit
observed
```

該当しない項目は持ちません。元ソース文字がある問題は `span`、欠落位置は `point` を使います。`E_SOURCE_SIZE_LIMIT` はUTF-8として解釈する前のファイル全体の問題なので、行と列を作らず、最初の超過バイトを示す `fileOffset` だけを使います。行と列は1始まり、UTF-8バイト位置と `fileOffset` はBOMを含む元ファイル先頭を0とします。

人間向けの先頭行は次の形です。

```text
＜path＞:＜line＞:＜column＞: エラー[＜code＞]: ＜message＞
＜path＞:＜line＞:＜column＞: 警告[＜code＞]: ＜message＞
＜path＞: エラー[＜code＞]: ＜message＞
```

補足行は2個のASCII空白で字下げし、`必要`、`実際`、`上限`、`観測値`、`関連位置`、`修正候補`の順に出力します。診断1件の末尾に余分な空行を出力しません。複数件はLFだけで連続させ、標準エラー全体をLFで終えます。

## 2. 順序と抑止

診断は処理段階、元ソースの `utf8Start` または `utf8Offset`、同じ位置では診断コードの昇順に出力します。処理段階の順はUTF-8、字句、構文、名前、型・スタック、IR、実行時です。

UTF-8・字句・構文エラーが1件でもあれば静的検査へ進みません。静的エラーが1件でもあればIRを生成しません。警告は後続処理を止めません。

1回に保持する診断は100件です。100件以上になる場合は、先頭99件を保持し、100件目を `E_DIAGNOSTIC_LIMIT` に置き換えて以降を省略します。終了コードは最初に失敗した段階に従います。

## 3. 終了コード別の診断

### 3.1 UTF-8・字句・構文: 終了コード9

| コード | 条件 | 主な情報 |
|---|---|---|
| `E_SOURCE_SIZE_LIMIT` | 元ファイルが32 MiB超 | 上限、実バイト数 |
| `E_INVALID_UTF8` | 不正なUTF-8 | 最初の不正バイト位置 |
| `E_DISALLOWED_WHITESPACE` | 未許可のUnicode空白 | コードポイント |
| `E_INVISIBLE_CHARACTER` | 禁止不可視文字 | コードポイント、Unicode名 |
| `E_IDENTIFIER_TOO_LONG` | 正規化後128コードポイント超 | 上限、実数 |
| `E_TOKEN_LIMIT` | 250,000トークン超 | 上限、観測値 |
| `E_INVALID_IDENTIFIER` | 識別子候補の文字規則違反 | 入力候補、違反文字 |
| `E_UNEXPECTED_CHARACTER` | どのトークンにも属さない | コードポイント |
| `E_MISSING_SEPARATOR` | 数値直後の区切り欠落 | 入力候補、修正候補 |
| `E_INVALID_NUMBER_LITERAL` | 数値字句文法違反 | 入力候補 |
| `E_NUMBER_LIMIT` | 数値リテラル4,096桁超 | 上限、実桁数 |
| `E_UNTERMINATED_STRING` | 閉じ引用符より先に改行・EOF | 必要な引用符、挿入位置 |
| `E_INVALID_ESCAPE` | 未定義または不完全なエスケープ | 入力エスケープ |
| `E_INVALID_UNICODE_SCALAR` | 範囲外またはサロゲート | 入力値 |
| `E_CHARACTER_LENGTH` | 展開後が1書記素クラスタでない | クラスタ数 |
| `E_STRING_LIMIT` | リテラル値が16 MiB超 | 上限、実UTF-8長 |
| `E_MIXED_STACK_PARENTHESES` | 全角・ASCII括弧混在 | 開始・終了文字 |
| `E_EXPECTED_STACK_SEPARATOR` | スタック効果の `--` 欠落 | 挿入位置 |
| `E_COMMENT_NOT_ALLOWED` | 定義ヘッダーまたはスタック効果内のコメント | コメント位置、移動候補 |
| `E_DEFINITION_ADJACENCY` | 名前と `とは` の間に区切り | 修正候補 |
| `E_EXPECTED_WORD_END` | `こと。` がない | 単語名、挿入位置 |
| `E_EXPECTED_DEFINITION_END_MARK` | `こと` の直後に許されない区切り | `。` の期待位置 |
| `E_UNEXPECTED_TOP_LEVEL` | トップレベルに定義以外 | 入力トークン |
| `E_DEFINITION_LIMIT` | 10,000定義超 | 上限、観測値 |
| `E_DIAGNOSTIC_LIMIT` | 診断100件到達 | 上限 |

`E_MISSING_SEPARATOR` は、数値として有効な先頭部分の直後に識別子文字が連続する場合に使います。候補全体が数値文法自体に合わない場合は `E_INVALID_NUMBER_LITERAL` です。

### 3.2 静的検査・IR: 終了コード8

| コード | 条件 | 主な情報 |
|---|---|---|
| `E_RESERVED_NAME` | 予約名を定義 | 入力名、正規名、分類 |
| `E_DUPLICATE_NAME` | 正規化後の名前が重複 | 両方の名前と位置 |
| `E_MISSING_MAIN` | `メイン` がない | 挿入例 |
| `E_INVALID_MAIN_EFFECT` | `メイン` が `（--）` でない | 宣言効果、必要効果 |
| `E_UNDEFINED_WORD` | 辞書にも利用者定義にもない | 入力名、候補 |
| `E_NAME_NOT_CALLABLE` | 型・型制約・構文・`メイン`を呼び出し位置で使用 | 入力名、分類 |
| `E_FEATURE_NOT_AVAILABLE` | 予約済みの後続機能グループ機能を使用 | 名前、予定段階 |
| `E_TYPE_CONSTRAINT_NOT_ALLOWED` | 利用者宣言で `T`・`表示可能` | 入力名、具体型の候補 |
| `E_UNKNOWN_TYPE` | 型位置の名前が型辞書にない | 入力名、利用可能な型 |
| `E_STACK_UNDERFLOW` | 呼び出しに必要な値が不足 | 必要型列、実スタック |
| `E_TYPE_MISMATCH` | 入力位置の型が違う | 単語、第何入力、必要型、実型 |
| `E_WORD_EFFECT_MISMATCH` | 本体終了スタックが宣言と違う | 単語、必要型列、実型列 |
| `E_MAIN_STACK_NOT_EMPTY` | `メイン` が値を残す | 実型列 |
| `E_IR_LIMIT` | 全定義のIRが250,000命令超 | 上限、観測値、原因定義 |
| `E_DIAGNOSTIC_LIMIT` | 診断100件到達 | 上限 |

未定義語の修正候補は、Unicode拡張書記素クラスタ列に対するLevenshtein距離を使います。距離2以下の正規名が1件だけ最小なら提示し、同率または距離3以上なら提示しません。予約された後続機能グループ機能は候補ではなく `E_FEATURE_NOT_AVAILABLE` とします。

### 3.3 実行時: 終了コード10

| コード | 条件 | 主な情報 |
|---|---|---|
| `E_INTEGER_RESULT_LIMIT` | 整数結果が65,536桁超 | 演算名、上限、予測または実桁数 |
| `E_DATA_STACK_LIMIT` | 65,536値を超えて積む | 上限、現在値数 |
| `E_CALL_STACK_LIMIT` | 1,024フレームを超えて呼ぶ | 上限、呼出先、BSB呼出履歴 |
| `E_INSTRUCTION_LIMIT` | 10,000,000命令を超えて実行 | 上限、次の命令位置 |
| `E_EXECUTION_TIMEOUT` | `メイン` 実行が30秒超 | 上限、経過時間、命令位置 |
| `E_OUTPUT_LIMIT` | 標準出力が64 MiB超 | 上限、現在バイト、追加予定バイト |
| `E_STRING_LIMIT` | 実行時生成文字列が16 MiB超 | 上限、予測UTF-8長 |

実行時エラーには、失敗したIR命令のソース位置、現在の単語、型付きデータスタック、BSB呼出スタックを含めます。値の全文は表示せず、型と安全な短縮表現だけを使います。

## 4. 警告

| コード | 条件 | 情報 |
|---|---|---|
| `W_PARTICLE_POSITION` | CORE機能グループの構造的な助詞配置違反 | 助詞、違反種別、修正候補 |
| `W_CONFUSABLE_IDENTIFIER` | UTS #39 skeletonが別名と一致 | 両方の名前、skeleton、関連位置 |

1つの助詞が複数規則へ違反しても、同じ位置には `W_PARTICLE_POSITION` を1件だけ出します。連続する助詞は、2個目以降の各助詞へ1件ずつ出します。

警告は標準エラーへ出しますが、警告だけなら終了コード0です。`format` は名前・型・助詞配置検査を行わないため、これらの警告を出しません。

## 5. 資源量の数え方

### ソース、識別子、トークン、定義

- ソースサイズはBOMを含む元ファイルのバイト数
- 識別子長は限定変換とNFC後のUnicodeコードポイント数
- トークン数は [language-core-grammar.md](language-core-grammar.md) の実行・構文トークン数。区切り、コメント、BOM、EOFは除く
- 定義数は構文解析が認識したトップレベル単語定義数

### IR命令

次を各1命令として、到達可能性にかかわらず全利用者定義の合計を数えます。

```text
PushConst
Call
Return
```

空本体も `Return` 1命令です。組み込み単語の内部処理はIR命令数に含めません。250,001個目を生成しようとする前に `E_IR_LIMIT` にします。

### 整数結果

整数の桁数は、符号を除いた正規10進表記のASCII数字数です。0は1桁です。加算・減算・乗算で安全に上限超過を予測できる場合は、大きな結果領域を確保する前に停止します。

### データスタックと呼出スタック

データスタックは値を積む直前に検査し、上限ちょうどの65,536値を許可します。

呼出スタックは `メイン` のフレームを1つ目として数えます。1,024フレームを許可し、1,025個目を積む前に停止します。Javaメソッドの再帰は使用しません。

### 実行命令と時間

実行命令数は、実際に実行した `PushConst`、`Call`、`Return` を各1命令として数えます。組み込み単語の処理は、その呼び出し元の `Call` 1命令に含めます。10,000,000命令を許可し、次の命令を実行する前に停止します。

時間は `メイン` の最初の命令直前から単調増加時計で測り、各命令の直前に検査します。30秒ちょうどは許可し、それを超えた場合に停止します。適合テストでは差し替え可能な単調増加時計を使い、実時間を30秒待ちません。

### 標準出力

標準出力は実際に書かれるUTF-8バイト数で数えます。64 MiBちょうどを許可します。1回の組み込み単語の出力全体を事前計算し、超える呼び出しは1バイトも追加せず停止します。

## 6. 境界適合データ

各上限には、上限ちょうどを受理するケースと、1単位超えて規定コードになるケースを用意します。巨大な入力はリポジトリへ実体を保存せず、[language-core-conformance.md](language-core-conformance.md) の決定的生成記述からテスト時に作ります。
