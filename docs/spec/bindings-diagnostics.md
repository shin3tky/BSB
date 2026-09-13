# BIND機能グループ: 束縛診断と資源境界

> 本文は現行実装の規範契約です。
>
> [language-core-diagnostics.md](language-core-diagnostics.md)と[control-flow-diagnostics.md](control-flow-diagnostics.md)の共通形式、終了コード、100件上限を引き継ぎます。

## 1. 処理段階と順序

BIND機能グループの診断順は次のとおりです。

```text
UTF-8
字句
構文
名前・スコープ・初期化順序
型・スタック・制御フロー
IR
実行時
```

同じ段階では元ソースのUTF-8開始位置、同じ位置では診断コードの昇順です。構文エラーが1件でもあれば名前以降へ進まず終了コード9、静的エラーが1件でもあればIRを生成せず終了コード8です。

到達不能警告はFLOW機能グループと同じ位置と抑止規則を使います。警告だけなら終了コード0です。

## 2. 構文診断: 終了コード9

| コード | 条件 | 位置 | 主な情報 |
|---|---|---|---|
| `E_DECLARATION_ADJACENCY` | 宣言名と`は`の間に区切りがある | `は`のspan | 入力名、期待表記、実表記 |
| `E_EXPECTED_DECLARATION_KIND` | `名前は`の後が`定数`・`変数`でない | 欠落pointまたは実トークンspan | 名前、期待種別、実トークン |
| `E_DECLARATION_HEADER_COMMENT_NOT_ALLOWED` | `は`と宣言種別の間にコメントがある | コメントのspan | 文脈、移動候補 |
| `E_EXPECTED_DECLARATION_END` | 初期値の`。`より先に単語終端またはEOF | 欠落point | 名前、宣言位置、挿入候補 |
| `E_UNEXPECTED_DECLARATION_END` | 宣言外に単独の`。`がある | `。`のspan | 実際の語 |
| `E_EXPECTED_ASSIGNMENT_VALUE_PARTICLE` | `＜名前＞ に 入れる`の前に`を`がない | 対象名のspan | 期待語、対象名 |
| `E_EXPECTED_ASSIGNMENT_TARGET` | `を`の後に対象識別子がない | 欠落pointまたは実トークンspan | 期待対象、実トークン |
| `E_EXPECTED_ASSIGNMENT_TARGET_PARTICLE` | 対象名の後に`に`がない | 欠落pointまたは実トークンspan | 対象名、期待語、実トークン |
| `E_INITIALIZER_ELEMENT_NOT_ALLOWED` | 初期値に制御構文、宣言、代入がある | 最初の禁止要素のspan | 要素名 |

`は`と宣言種別の間にコメントがある場合は`E_DECLARATION_HEADER_COMMENT_NOT_ALLOWED`を使用し、`context=宣言ヘッダー`を持たせます。代入の4要素の間にコメントがある場合は`E_INITIALIZER_ELEMENT_NOT_ALLOWED`ではなく、代入構文専用の不足診断を最初の不一致位置へ報告します。

宣言終端の回復は、現在の`。`、`こと。`、または次の有効なトップレベル開始を同期点にします。代入の回復は、現在の本体行、制御中間・終了語、宣言終端、単語終端を越えません。

## 3. 名前・スコープ・初期化診断: 終了コード8

| コード | 条件 | 位置 | 主な情報 |
|---|---|---|---|
| `E_REFERENCE_BEFORE_INITIALIZATION` | 宣言の初期値から自分または後方の大域値を読む | 参照span | 名前、宣言位置、初期化順 |
| `E_REFERENCE_BEFORE_DECLARATION` | 同じ局所スコープの宣言より前から読む・書く | 参照または対象span | 名前、後方の宣言位置 |
| `E_BINDING_OUT_OF_SCOPE` | 終了した子・兄弟スコープの局所名を読む・書く | 参照または対象span | 名前、宣言位置、スコープ |
| `E_NAME_SHADOWING` | 局所宣言が可視な外側名を隠す | 宣言名span | 名前、外側の種類と位置 |
| `E_ASSIGN_TO_CONSTANT` | 定数を代入先にする | 対象名span | 名前、型、宣言位置 |
| `E_ASSIGNMENT_TARGET_NOT_VARIABLE` | 単語または組み込み単語を代入先にする | 対象名span | 名前、解決種別 |
| `E_UNDEFINED_ASSIGNMENT_TARGET` | 代入先がどこにも定義されていない | 対象名span | 名前、候補 |

同じスコープの重複、正規化後の重複、予約名との衝突には既存の`E_DUPLICATE_NAME`と`E_RESERVED_NAME`を使います。通常の読み出し位置で未知名なら既存の`E_UNDEFINED_WORD`です。

初期値の自己参照は`E_REFERENCE_BEFORE_INITIALIZATION`であり、`E_REFERENCE_BEFORE_DECLARATION`ではありません。大域宣言は名前登録済みでも、初期化順を満たすまでは値を読めません。

## 4. 型・スタック診断: 終了コード8

| コード | 条件 | 位置 | 主な情報 |
|---|---|---|---|
| `E_INITIALIZER_VALUE_MISSING` | 初期値の出口が空 | 宣言終端のspan | 名前、実スタック |
| `E_INITIALIZER_VALUE_COUNT` | 初期値の出口に2個以上の値 | 宣言終端のspan | 名前、必要個数、実個数、実スタック |
| `E_INITIALIZER_CALL_NOT_ALLOWED` | 初期値から利用者単語または副作用単語を呼ぶ | 呼出しspan | 名前、呼出し種類・効果 |
| `E_ASSIGNMENT_STACK_UNDERFLOW` | `入れる`時点に値がない | `入れる`のspan | 対象名、必要型、実スタック |
| `E_ASSIGNMENT_TYPE_MISMATCH` | 代入値が変数の宣言型と異なる | `入れる`のspan | 対象名、必要型、実型、宣言位置 |

初期値に許されない制御構文、宣言、代入は構文段階の`E_INITIALIZER_ELEMENT_NOT_ALLOWED`です。位置は最初の許可されない開始語または宣言名で、`element`フィールドを持ちます。

初期値内の純粋組み込み単語の入力不足と型不一致には、既存の`E_STACK_UNDERFLOW`と`E_TYPE_MISMATCH`を使います。小数リテラルには既存の`E_FEATURE_NOT_AVAILABLE`を使います。

名前・スコープ・初期化段階で宣言または参照を検査できなかった場合、その要素から派生する初期値・代入型診断を抑止します。他の宣言と単語の検査は続けます。

## 5. 警告

到達不能な宣言・参照・代入には既存の`W_UNREACHABLE_CODE`だけを報告し、その範囲のBIND機能グループ診断を抑止します。助詞配置には既存の`W_PARTICLE_POSITION`を使います。

## 6. 実行時と内部エラー

正常に検査・生成されたBIND機能グループIRは、未初期化Load、定数へのStore、範囲外スロットを実行しません。これらは利用者入力で到達できる実行時エラーとして定義せず、合成不正IRを検出した場合は内部エラー終了コード70です。

`LoadGlobal`と`LoadLocal`がデータスタック上限を超える場合は、既存の`E_DATA_STACK_LIMIT`、終了コード10です。実行済みの初期化、Load、Storeは既存の実行命令数と時間上限へ各1命令として数えます。

## 7. 束縛IDと説明順序

到達可能な大域宣言と局所宣言を元ソースのUTF-8開始位置順に数え、1始まりの`B1`、`B2`、…を割り当てます。到達不能宣言は数えません。IDは診断順序へ影響しません。

束縛説明の参照行は参照位置順に並べ、同じ位置は`read`、`write`の順とします。マップの反復順、JVMのオブジェクトID、実行順に依存しません。

## 8. 資源上限

BIND機能グループで次を追加または拡張します。上限値を含み、1単位超過を処理前に拒否します。

| 対象 | 上限 | 判定時点 | 超過診断 |
|---|---:|---|---|
| 大域束縛 | 10,000個 | 10,001個目の大域束縛登録前 | `E_GLOBAL_BINDING_LIMIT` |
| 1単語の局所束縛 | 1,024個 | 1,025個目の束縛登録前 | `E_LOCAL_BINDING_LIMIT` |
| 1プログラムの全束縛 | 65,536個 | 65,537個目の束縛登録前 | `E_BINDING_LIMIT` |

CORE機能グループのトップレベル単語定義10,000個上限は変更しません。大域束縛10,000個は独立して数えます。したがって、単語定義だけの既存10,000/10,001境界と診断文は変わりません。

全束縛には到達可能な大域定数・変数と局所定数・変数を数え、単語、組み込み名、到達不能宣言は数えません。兄弟スコープで同名でも別々に数えます。

## 9. IR命令数

次を各1命令として既存の250,000命令上限へ数えます。

```text
InitializeGlobal
InitializeLocal
LoadGlobal
LoadLocal
StoreGlobal
StoreLocal
```

束縛表、スロット表、大域・局所値配列、初期化済みビット、束縛説明行はIR命令として数えません。大域初期化用の末尾`Return`は既存どおり1命令です。到達不能宣言とその初期値は数えません。

250,000命令を受理し、250,001個目の命令オブジェクトを確保する前に既存の`E_IR_LIMIT`とします。

## 10. トレース値

BIND機能グループトレースは、FLOW機能グループの列へ次を末尾追加します。

```text
bindingId
bindingName
bindingKind
storageScope
valueBefore
valueAfter
```

Initialize、Load、Store以外の追加6列はすべて`-`です。`bindingKind`は`constant`または`variable`、`storageScope`は`global`または`local:＜所有単語名＞`です。未初期化値は`<uninitialized>`、初期化済み値は既存の`型:表示値`で表します。

Loadでは`valueBefore`と`valueAfter`が同じ保存値、Initializeでは前が`<uninitialized>`、Storeでは更新前後の値です。文字列などの表示は既存トレースの安全なエスケープと上限を適用します。
