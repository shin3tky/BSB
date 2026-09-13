# RESULT機能グループ: `結果<T,E>`の診断

> 本文は現行実装の規範契約です。
>
> [結果値仕様](result-values.md)と[適合計画](result-values-conformance.md)に従います。

## 1. 型引数構文: stage=syntax、終了9

以下の`owner`は`結果`、`成功にする`、`失敗にする`のいずれかです。
共通必須fieldは`typeConstructor=結果`、`owner`です。主位置は次の表で固定します。
構築呼出しでは追加fieldとして`word=正規語名`を載せます。

| コード | 条件・例 | 主位置 | 追加fields |
|---|---|---|---|
| `E_EXPECTED_RESULT_TYPE_ARGUMENT` | `結果<>`、`結果<,文字列>`、`結果<整数,>`、引数位置の不正トークン・EOF | 空の位置の`,`／`>`／不正トークン／EOF point | `argumentIndex=1`または`2` |
| `E_EXPECTED_RESULT_TYPE_SEPARATOR` | `結果<整数 文字列>`、`結果<整数、文字列>` | 第2型を始めるトークン | なし |
| `E_RESULT_TYPE_ARGUMENT_COUNT` | `結果<整数>`、`結果<整数,文字列,真偽>`、末尾の余分な`,` | 1引数の`>`／2個目の`,` | `expectedCount=2`、`actualCount=1`または`3` |
| `E_EXPECTED_RESULT_TYPE_END` | 必要な2引数の後に`>`がない | 次トークンまたはEOF point | `startLine`、`startColumn` |

空引数は「引数のスロットがあるが中身がない」、個数不一致は「スロット数が違う」と区別します。
2個目の`,`に達した時点で3番目のスロットが始まったとして`actualCount=3`を報告し、
残りの余分な引数を数えて診断値を変えません。

| コードの末尾 | expected | actual | fix（1件） |
|---|---|---|---|
| `TYPE_ARGUMENT` | `具体型` | 該当トークンの原表記または`EOF` | `成功型と失敗型に具体型を指定してください` |
| `TYPE_SEPARATOR` | `,` | 次トークンの原表記 | `成功型と失敗型をASCIIの,で区切ってください` |
| `ARGUMENT_COUNT` | `2個の型引数` | `1個の型引数`または`3個以上の型引数` | `成功型と失敗型を1個ずつ指定してください` |
| `TYPE_END` | `>` | 次トークンの原表記または`EOF` | `型引数の末尾へ>を追加してください` |

終端不足だけはownerの開始位置を`related`へ1件載せ、labelはownerの正規名です。
それ以外の新規構文診断の`related`は空です。型名は原表記とは別に常に正規化します。

### 構文回復と優先順位

- `結果<整数`は第2スロットと閉じ記号を両方推測せず、EOFで終端不足1件とします。
- `結果<`と`結果<整数,`は空引数1件とし、同じ枠の終端不足を重ねません。
- `結果<整数>`は個数不一致1件で、その`>`を消費します。
- `結果<任意<>,文字列>`は内側の既存`E_EXPECTED_OPTIONAL_ELEMENT_TYPE`だけです。
  外側は`,`と第2引数、自分の`>`を回復走査し、派生する引数不足を出しません。
- `任意<結果<>>`は結果の空引数1件だけです。親の`>`を子の回復で消費しません。
- 本当に複数の`>`が欠落した場合は最内側から各枠につき終端不足1件です。
- 引数内のコメントは禁止位置の`E_COMMENT_NOT_ALLOWED`を優先します。引数不足を重ねません。
- 深さ257段目は既存`E_SYNTAX_DEPTH_LIMIT`（`limit=256`、`observed=257`）を優先し、
  その部分木を反復的に読み飛ばします。失敗した部分木由来の型診断を重ねません。
- 宣言終端、定義終端、次の定義開始は回復の境界です。次の独立した定義は検査します。
  100診断の上限と`E_DIAGNOSTIC_LIMIT`を維持します。

単引数の配列・任意の既存診断は改名しません。結果引数枠の外のASCIIカンマ、無関係な語への
`<...>`、コメント禁止、不正な配列要素式は既存の文脈別診断を使い、汎用ジェネリック構文と
解釈しません。成功型または失敗型が未知でも、構文が有効な`format`は成功します。

## 2. 静的診断: stage=typeAndStack、終了8

| コード | 条件 | 主位置・fields |
|---|---|---|
| `E_RESULT_TYPE_ARGUMENTS_REQUIRED` | 型位置の裸の`結果`／型引数なしの構築2語 | owner名のspan。`typeConstructor=結果`、`owner`、`context=スタック効果`または`構築呼出し`。構築時は`word`も必須 |
| `E_TYPE_DEPTH_LIMIT` | `任意にする`等が型推論で深さ257を生成する | 原因の呼出しspan。`word`、`typeConstructor=任意`、既存limit構造に`limitName=typeDepth`、`limit=256`、`observed=257` |

`E_RESULT_TYPE_ARGUMENTS_REQUIRED`は、型位置なら
`expected=結果<具体型,具体型>`、`actual=結果`、
`fix=結果<整数,文字列>のように成功型と失敗型を指定してください`です。
構築時は`expected=成功にする<具体型,具体型>`等、`actual=成功にする`等、
`fix=成功にする<整数,文字列>のように成功型と失敗型を指定してください`等とします。
いずれも`related`は空です。

`E_TYPE_DEPTH_LIMIT`は`expected=型構築子の深さ256以下`、`actual=型構築子の深さ257`、
`fix=型の入れ子を浅くしてください`、`related`は空です。これは構文深さ診断の改名ではなく、
ソースに257段の型注釈がなくても推論で超過する経路の診断です。

| 条件 | 再利用する診断 |
|---|---|
| `T`、`E`、`数値`等を具体型に使用 | `E_TYPE_CONSTRAINT_NOT_ALLOWED` |
| 未知型／後続予約型 | `E_UNKNOWN_TYPE`／`E_FEATURE_NOT_AVAILABLE` |
| 配列要素に結果型 | `E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED` |
| 構築するペイロードが宣言した選択側の型と違う | `E_TYPE_MISMATCH` |
| 7語の入力不足／結果を要求する5語へ非結果値 | `E_STACK_UNDERFLOW`／`E_TYPE_MISMATCH` |
| 両型の一方でも非表示／非比較 | `E_TYPE_MISMATCH`。`actualType`は結果型全体 |
| 成功型だけ／失敗型だけが異なる結果同士 | 文脈の`E_TYPE_MISMATCH`／保存・合流・復帰診断 |
| 新予約名の利用者定義 | `E_RESERVED_NAME` |

型引数の検査は左（成功型）から右（失敗型）の順です。独立した両引数のエラーは両方報告し、
同じ不正部分木から外側の未知型・スタック不一致を派生させません。型引数を確定できた後に
スタック不足、入力型不一致、出力型深さの順で検査します。構築の型引数欠落と入力不足が
同時なら型引数欠落だけです。型名に状態を付けず、非選択側が異なる場合も無視しません。

構造的な到達不能コードは既存の`W_UNREACHABLE_CODE`規則に従い、呼出しの型・スタック診断を
抑止します。字句・構文診断は到達不能でも抑止しません。成功／失敗の予測で到達不能としません。

## 3. 実行時診断: stage=runtime、終了10

| コード | 条件 | 必須fields |
|---|---|---|
| `E_RESULT_STATE_MISMATCH` | 失敗結果から成功値を、または成功結果から失敗値を取り出す | `word`、`resultType`、`expectedState=success`または`failure`、`actualState=failure`または`success` |

主位置は型引数を持たない取出し語の呼出しspanです。成功取出しなら
`expected=成功の結果値`、`actual=失敗の結果値`、
`fix=結果が成功であるで分岐してから取り出してください`です。失敗取出しは成功と失敗を
入れ替えます。`related`は空、`resultType`は正規型名です。
処理系が付与する呼出し履歴等の共通実行時コンテキストは既存契約を維持します。ただし診断の
`dataStack`へ結果値を載せる場合は、型名に続けて常に`<redacted>`とし、ペイロードと状態を
そこへ複写しません。結果型を含む任意値にも推移的に適用します。状態は上記の専用fieldsだけに
載せます。既存の非結果値の診断表現は変更しません。

内包値、エラー本文、JSONキー、入力原文、Java例外名を追加診断へ含めません。
成功値取出しの失敗と失敗値取出しの失敗は同じコードで、上記2フィールドにより区別します。
判定と破棄は状態による実行時失敗を持ちません。

データスタック、出力、配列作業、JSON作業等の上限には既存診断を使います。
JSON内包値の比較・表示は既存の`operation=equals`／`display`を維持し、結果専用名へ変えません。
型・スタックの静的検査を通った後、取出しでは状態検査だけ、判定では追加スタック枠、
表示・比較では既存の能力・予算検査順に従います。無関係なJSON検査を取出しの前に行いません。

失敗した語は入力スタック、保存値、プログラム両出力、拒否予約を変更しません。
診断をCLIがstderrへ書くことと、プログラムstderrへの副作用は区別します。
先行する成功操作の課金は戻さず、失敗を自動的な結果値にも変えません。

## 4. 新規日本語メッセージ

```properties
E_EXPECTED_RESULT_TYPE_ARGUMENT=結果型の型引数には具体型が必要です。
E_EXPECTED_RESULT_TYPE_SEPARATOR=成功型と失敗型を区切る「,」が必要です。
E_RESULT_TYPE_ARGUMENT_COUNT=結果型には成功型と失敗型の2個の型引数が必要です。
E_EXPECTED_RESULT_TYPE_END=結果型の型引数を閉じる「>」が必要です。
E_RESULT_TYPE_ARGUMENTS_REQUIRED=結果型とその構築には成功型と失敗型の指定が必要です。
E_TYPE_DEPTH_LIMIT=推論された型の入れ子が上限を超えています。
E_RESULT_STATE_MISMATCH=結果値の状態が取り出す側と一致しません。
```

メッセージ資源は`tests/conformance/result-values/messages.properties`、`DiagnosticCode`、
`DiagnosticMessageCatalog`、Gradleの配布資源へ接続済みです。新7件は実装欠落0で、
状態不一致を含む仕様182件すべてが診断監査上`CONFORMANCE`です。全70 IDの中央ハーネスによる
章全体の実行適合も中央適合試験で検証します。
