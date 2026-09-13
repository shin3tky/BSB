# RESULT機能グループ: `結果<T,E>`の適合データ・テスト計画

> 本文は現行実装の規範契約です。
>
> 規範は[結果値](result-values.md)と[診断](result-values-diagnostics.md)です。
> 既存のOPT機能グループ適合資源はそのまま回帰対象にします。

先行データの実体、136通常・失敗variants、33生成境界、639診断行、検証済み／未実行の区別は
[result-values README](../../tests/conformance/result-values/README.md)を参照してください。
以下の全70 ID・全公開コマンドの契約を、中央適合試験で実行・照合します。

## 1. カタログと独立期待

```text
tests/conformance/result-values/
  cases.tsv
  diagnostics.tsv
  resources.tsv
  messages.properties
  sources/
  canonical/
  expected/
  explain/
  chapter/
  generated/resources.tsv
  generated/*.properties
```

既存`OptionalConformanceData`の実体は`cases.tsv`です。OPT機能グループ文書の構成例にある
`cases.properties`は踏襲しません。新しい中央ローダーを`ResultConformanceData`とし、
RESULT-N 20件、RESULT-F 36件、RESULT-R 14件、計70 IDを過不足なく登録します。以下の複数条件を持つIDは
明示的なvariantへ分けます。1 IDを1回走らせて全条件を網羅した扱いにしません。

`cases.tsv`はOPT機能グループの項目を基に、次の22列をこの順で固定します。

```text
case_id variant kind source canonical stdout stderr commands check_exit run_exit format_exit explain_exit diagnostic_rows state instructions output_bytes error_output_bytes array_construction array_work json_construction json_work target_words
```

- 区切りはTAB。各行の一意キーは`case_id + variant`です。物理sourceもvariantごとに指定します。
- `commands`は`check|checkJson|run|format|explain`の部分列です。`checkJson`は`check --json`、
  `explain`は`explain --json`に対応し、`checkJson`の終了は`check_exit`に一致させます。
- stdout／stderrは`expected/`以下のコマンド別期待をまとめた資源への参照です。
  CLI診断stderrと内部環境のプログラムstderrを混同しません。
- `state`は最終スタックと保存値の独立期待です。型、成功／失敗、選択側の値、任意の有無を
  構造化して記録し、個数一致だけでは合格にしません。実行していない行だけ`-`を許します。
- `target_words`は辞書の正規語名の集合です。型付き呼出しも`成功にする`等へ対応させます。
  各語を実際に呼んだか、またはその語の静的診断へ到達したかを別途検証します。
- 予算は対象語へ達する前のリテラル・保存・呼出しを含めた合計です。`0`と未実行の`-`を区別します。
  適合資源に本番の上限定数を読み込んで期待値を作る記法は用意しません。

`diagnostics.tsv`は次の16列で、`case_id + variant + command + occurrence`を一意とします。

```text
case_id variant occurrence command code severity stage span fields expected actual fixes related limit_name limit observed
```

spanは開始・終了の行列、UTF-8／UTF-16範囲、EOF pointの区別を独立JSONで保持します。
fields・fixes・relatedも既存公開JSON契約に従う独立期待です。コードだけ、主位置だけ、
メッセージ内の部分一致だけで合格にしません。構文回復の複数診断は順序も比較します。
共有する人間向けメッセージ原文は`messages.properties`、render済みの期待はコマンド別資源に置きます。

`resources.tsv`は既存の8列
`case_id target variant outcome code limit observed generator_key`を用い、
細かな型形状、予算前後、状態、能力イベントは参照先の生成レシピと独立期待へ分離します。
生成レシピはkind、件数、両型、選択側、深さ、予算初期値、期待の参照等を未使用項目なしで消費します。
公開ソースか合成IR／実行入力かをkindで区別し、入力UTF-8または正規レシピのSHA-256を
`generated/resources.tsv`に固定します。

カタログ試験は欠落、重複、未知variant、未知列、余剰資源、孤立した診断行、未使用レシピ項目を
拒否します。カタログから全試験へdispatchし、手書きのJUnit名だけの証跡にはしません。
期待source・canonical・stdout・型名・診断・予算・traceを本番処理系で自動更新して確定しません。
規範から手計算または小さな独立オラクルで求め、実装出力は比較対象だけに使います。

## 2. 正常例 RESULT-N001〜RESULT-N020

| ID | 必須観測 |
|---|---|
| RESULT-N001 | 全10スカラー型をT／Eの双方へ置く型参照、許可された全配列要素型の配列、任意・結果との混在を受理 |
| RESULT-N002 | 型参照と構築呼出しの空白・改行・既存Unicode正規化、隣接`>>`、正規ASCIIカンマ、format冪等性 |
| RESULT-N003 | 成功構築、`T=E`を含む複数の型組、非選択側の値を生成しない、具体化Callの出力型 |
| RESULT-N004 | 失敗構築、Eが文字列以外（整数・JSON・任意・結果）の場合、非選択側を保持した具体型 |
| RESULT-N005 | 成功判定のsuccess／failure両状態、元値保持、真偽の位置と値 |
| RESULT-N006 | 失敗判定のsuccess／failure両状態、元値保持、成功判定との相補性 |
| RESULT-N007 | 成功値／失敗値の正しい取出し、ペイロードの型・値・不変参照共有 |
| RESULT-N008 | 成功／失敗両状態の破棄、非表示・非比較ペイロードでも許可、追加作業0 |
| RESULT-N009 | 定数、変数、局所保存、同型変数の成功⇔失敗更新、元値の不変性 |
| RESULT-N010 | 利用者語の入力・出力、前方参照、再帰、早期復帰、型引数順序の保持 |
| RESULT-N011 | 分岐、回数・条件ループ、打切り・継続の同型合流、状態による静的分岐削除なし |
| RESULT-N012 | 同状態同値／異値・異状態の等価性、`T=E`、小数・JSON・配列・混在任意の既存比較規則 |
| RESULT-N013 | 成功／失敗の正規表示、stdout／stderr、一行表示、空文字・制御文字・Unicode・入れ子 |
| RESULT-N014 | T／E各側の正規表現・入力結果・日時を保持・受渡し・取出しできる（表示・比較しない） |
| RESULT-N015 | `結果<任意<JSON>,文字列>`の値あり・ヌル・欠落・失敗の4状態と`任意<結果<T,E>>`との区別 |
| RESULT-N016 | 既定／開示試験用／拒否ホストポリシーのtrace、非選択側の非開示型、保存前後、任意との交互入れ子 |
| RESULT-N017 | 公開辞書119語、7語の順序・例・型規則・RESULT機能グループ、具体型の利用者語／束縛、JSON版1 |
| RESULT-N018 | 構築2語を含む純粋初期値、型付き呼出しを含む配列要素式が許可型へ復帰するケース、外部能力なし |
| RESULT-N019 | 明示的な失敗値は診断や自動終了を起こさず、判定・取出し・破棄後に後続命令へ進める |
| RESULT-N020 | 第5節の章末プログラム、全公開コマンド、独立の通常・非開示trace |

RESULT-N001の型受理だけで実行網羅としません。RESULT-N003〜004・014で生成可能な各スカラーと
配列の構築・分解を実行します。入力結果・日時は偽能力で決定的に生成し、実端末・実時間・
ロケールへ依存しません。型に存在するだけの非選択側は能力を呼びません。
各7語に正常sourceと静的失敗sourceの両方を割り当て、最終監査で全7語の`FULL`を要求します。

## 3. 失敗・警告例 RESULT-F001〜RESULT-F036

複数形を併記した行は各形を独立variantとします。構文4種は型位置と構築2語の計3 ownerで
同じ検査規則を共有することを確認し、型位置だけの試験で構築経路を代用しません。

| ID | 入力・観測 | 期待診断 |
|---|---|---|
| RESULT-F001 | 型位置の裸の`結果` | `E_RESULT_TYPE_ARGUMENTS_REQUIRED` |
| RESULT-F002 | `結果<>`、`結果<,文字列>` | `E_EXPECTED_RESULT_TYPE_ARGUMENT`、index=1 |
| RESULT-F003 | `結果<整数 文字列>`、`、`／`，`による代用 | `E_EXPECTED_RESULT_TYPE_SEPARATOR` |
| RESULT-F004 | `結果<整数>` | `E_RESULT_TYPE_ARGUMENT_COUNT`、actualCount=1 |
| RESULT-F005 | `結果<整数,>`、`結果<整数,` | `E_EXPECTED_RESULT_TYPE_ARGUMENT`、index=2 |
| RESULT-F006 | 3引数、2引数後の末尾カンマ | `E_RESULT_TYPE_ARGUMENT_COUNT`、actualCount=3 |
| RESULT-F007 | 2引数後の未終端、`結果<整数`、`結果<` | 終端不足／最後の形だけ空引数。RESULT機能グループの回復規則どおり |
| RESULT-F008 | T側・E側の`T`、`E`、`数値`、両側同時の型制約 | `E_TYPE_CONSTRAINT_NOT_ALLOWED` |
| RESULT-F009 | T側・E側の未知型、両側同時の未知型 | `E_UNKNOWN_TYPE`、内側span、左から右 |
| RESULT-F010 | T側・E側の予約済み`JSONオブジェクト` | `E_FEATURE_NOT_AVAILABLE` |
| RESULT-F011 | 注釈および要素式で`配列<結果<整数,文字列>>`を作る | `E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED` |
| RESULT-F012 | 型引数なしの成功／失敗構築、入力有／無 | `E_RESULT_TYPE_ARGUMENTS_REQUIRED`だけ |
| RESULT-F013 | 成功構築へTと違うペイロード | `E_TYPE_MISMATCH` |
| RESULT-F014 | 失敗構築へEと違うペイロード | `E_TYPE_MISMATCH` |
| RESULT-F015 | 型付き成功／失敗構築の入力不足 | `E_STACK_UNDERFLOW` |
| RESULT-F016 | 成功／失敗判定の入力不足 | `E_STACK_UNDERFLOW` |
| RESULT-F017 | 成功／失敗判定へ非結果値（整数、任意値） | `E_TYPE_MISMATCH` |
| RESULT-F018 | 成功／失敗取出しの入力不足 | `E_STACK_UNDERFLOW` |
| RESULT-F019 | 成功／失敗取出しへ非結果値 | `E_TYPE_MISMATCH` |
| RESULT-F020 | 結果破棄の入力不足 | `E_STACK_UNDERFLOW` |
| RESULT-F021 | 結果破棄へ非結果値 | `E_TYPE_MISMATCH` |
| RESULT-F022 | 失敗結果から成功値を取り出す | `E_RESULT_STATE_MISMATCH`、expectedState=success |
| RESULT-F023 | 成功結果から失敗値を取り出す | `E_RESULT_STATE_MISMATCH`、expectedState=failure |
| RESULT-F024 | 非選択側も含む非表示型（正規表現・入力結果・日時、T／E双方） | `E_TYPE_MISMATCH`、結果型全体 |
| RESULT-F025 | 非選択側も含む非比較型（同上） | `E_TYPE_MISMATCH`、結果型全体 |
| RESULT-F026 | 成功型だけが違う結果を比較／引数へ渡す | `E_TYPE_MISMATCH` |
| RESULT-F027 | 失敗型だけが違う結果を比較／引数へ渡す | `E_TYPE_MISMATCH` |
| RESULT-F028 | 分岐でTだけ／Eだけが異なる | `E_BRANCH_STACK_MISMATCH` |
| RESULT-F029 | 再代入でTだけ／Eだけが異なる | `E_ASSIGNMENT_TYPE_MISMATCH` |
| RESULT-F030 | 回数・条件ループで結果型が変わる | `E_LOOP_STACK_MISMATCH` |
| RESULT-F031 | 通常出口／早期復帰の結果型が宣言と異なる | `E_WORD_EFFECT_MISMATCH`／`E_RETURN_EFFECT_MISMATCH` |
| RESULT-F032 | `戻る`後の状態不一致取出し／入力不足、別variantの壊れた型構文 | 前2者は`W_UNREACHABLE_CODE`だけ、構文は抑止しない |
| RESULT-F033 | 任意／配列との混在空引数、複数終端不足、回復後の別定義 | 内側の規定診断だけ、真の終端不足だけ内側から複数 |
| RESULT-F034 | 枠外のASCIIカンマ、型引数内コメント、構築名と`<`の間のコメント、他語への型引数 | 既存`E_UNEXPECTED_CHARACTER`／`E_COMMENT_NOT_ALLOWED`／`E_EXPECTED_WORD_END`等の文脈診断 |
| RESULT-F035 | 新予約名9個の再定義、裸の型名`結果`の呼出し | `E_RESERVED_NAME`／`E_NAME_NOT_CALLABLE` |
| RESULT-F036 | 不正JSON解析の後に成功構築を置く／JSONヌルを成功構築・取出ししてから文字列取出しを行う | 既存`E_JSON_SYNTAX`／`E_JSON_KIND_MISMATCH`。結果へ捕捉されない |

RESULT-F022〜023はペイロードに固有の秘密検出用文字列を入れ、診断の全公開表現と既定traceへ
現れないことを検査します。異状態、同型のままの入力スタックと保存値を独立期待へ一致させます。
RESULT-R011では、別の語で失敗した際の残存結果値・結果を含む任意値も診断`dataStack`で伏せられる
ことを検証します。新規診断の明示fieldsだけを見て、共通コンテキストからの漏れを見逃しません。
RESULT-F034の「等」は未定の新診断を許す意味ではなく、初期値なら既存
`E_INITIALIZER_ELEMENT_NOT_ALLOWED`となる文脈差です。body、initializer、型ヘッダーを
variant名で明示し、対応する既存診断の全fieldsを期待へ固定します。

## 4. 資源・内部境界 RESULT-R001〜RESULT-R014

| ID | 対象 | 固定する境界・対照 |
|---|---|---|
| RESULT-R001 | 明示型の最大パス深さ | 256成功／257構文失敗。T側、E側、交互、構築呼出しの出力型 |
| RESULT-R002 | 推論型深さ | 深さ255を任意で包み256成功／256を包み257で`E_TYPE_DEPTH_LIMIT`。結果混在と任意だけの回帰 |
| RESULT-R003 | 深い値と型の全操作 | 深さ256の型名、equals/hash、表示、比較、trace。選択側／非選択側を交換、任意・結果の交互入れ子 |
| RESULT-R004 | 幅のある型と共有型 | 深さ15の完全二分結果型（構築子32,767、葉32,768）、共有／非共有で同じ型名・同一性。既存トークン上限250,000／250,001も別variant |
| RESULT-R005 | スタック値数と判定の1値追加 | 65,536成功／65,537で`E_DATA_STACK_LIMIT`。両判定は実行前65,535成功／65,536で原子的失敗 |
| RESULT-R006 | 大域保存数 | 10,000／10,001、成功値・失敗値を交互保存 |
| RESULT-R007 | 1語の局所保存数 | 1,024／1,025、再帰フレームごとの独立性も検証 |
| RESULT-R008 | 命令・既存構築予算 | 10,000,000／10,000,001命令。結果包装は各1命令でJSON／配列構築追加0。既存構築上限1,000,000到達後も包装可 |
| RESULT-R009 | 出力のUTF-8バイト数 | stdout／stderr各67,108,864／1超過。成功・失敗の括弧とLFを含め、拒否時に部分出力しない |
| RESULT-R010 | JSON作業と配列複合課金 | JSON作業67,108,864／1超過、配列作業10,000,000／1超過。成功・失敗の選択側、異状態比較0、JSON構築追加0 |
| RESULT-R011 | 実行時失敗の原子性 | 両取出し、両判定、表示・比較の拒否予約。スタック・保存・両出力不変、先行成功課金は維持 |
| RESULT-R012 | trace上限と非開示 | 末端32／33文字、配列8／9要素・文字要素16／17、非選択側の秘密型、強いホスト拒否、実行・予算・能力列不変 |
| RESULT-R013 | 構文回復の資源上限 | 診断100／101、型枠回復、後続定義、トークン枠のカンマ課金、深さ・空引数の派生診断抑止 |
| RESULT-R014 | 最大ヒープ512 MiBの統合 | 深さ256、スタック幅、共有ペイロード、JSON比較・表示を組み合わせた決定的レシピ。通常／traceの一致 |

RESULT-R001〜002、006〜007、013は決定的な公開sourceを優先します。RESULT-R003〜004は型モデルの
直接試験と公開型参照を両方使います。RESULT-R005は型付き構築を65,536個並べるとトークン上限が
先に来るため合成IRを使用し、通常幅の判定・構築はN/F公開sourceでも実行します。

RESULT-R008〜012の厳密な残予算境界は、偽時計・偽出力と明示した`ExecutionBudget`初期状態への
対象語1回適用を許します。特に大きなJSON作業上限を細かな操作だけで消費すると命令上限が
先に来るため、内部境界と明記し公開CLIで到達したと主張しません。RESULT-R014は合成IRとし、
入力形状・命令数・共有の有無を独立レシピで検証します。非選択側のダミー値を用意しません。

出力境界は、たとえば`成功（x）`の13バイト（一行なら14）に対して残予算を設定し、
失敗側にも同じ検査を適用します。配列比較は先行要素で成功した課金と失敗予約を区別します。
上限ちょうど、最小超過の両方を確認し、OOM、StackOverflow、Java例外名、部分stdoutを
合格の終了方法と認めません。ホスト環境由来の経過時間でテストの成否を決めません。

## 5. 章末成果物 RESULT-N020

JSONの検証結果を`結果<任意<JSON>,文字列>`で返し、成功の中の値あり・ヌル・欠落と、
明示的な失敗を分けます。これは検証関数が状態判定して作る失敗であり、JSON例外捕捉ではありません。
章末sourceの意味を次に固定します。

```text
設定を検証するとは （JSON -- 結果<任意<JSON>,文字列>）
    JSONオブジェクトである ならば
        「mode」 を JSONオブジェクトから任意値を取り出す
        成功にする<任意<JSON>,文字列>
    さもなければ
        任意にする 任意を捨てる
        「not-object」 を 失敗にする<任意<JSON>,文字列>
    つぎに
こと。

設定結果を読むとは （結果<任意<JSON>,文字列> -- 文字列）
    結果が成功である ならば
        結果から成功値を取り出す
        任意に値がある ならば
            任意から値を取り出す
            JSON文字列である ならば
                JSONから文字列を取り出す
            さもなければ
                JSONを文字列に変換する
            つぎに
        さもなければ
            任意を捨てる
            「default」
        つぎに
    さもなければ
        結果から失敗値を取り出す
    つぎに
こと。

メインとは （--）
    「{"mode":"configured"}」 を JSONを解析する
    設定を検証する 設定結果を読む 一行表示する
    「{"mode":null}」 を JSONを解析する
    設定を検証する 設定結果を読む 一行表示する
    空のJSONオブジェクト
    設定を検証する 設定結果を読む 一行表示する
    「[]」 を JSONを解析する
    設定を検証する 設定結果を読む 一行表示する
こと。
```

非オブジェクト枝の`任意にする 任意を捨てる`は、元JSONを純粋に消費する既存の語列です。
新たな汎用drop語を追加する意図はありません。

期待stdoutは次の35 UTF-8バイト（各行LF、末尾LF）です。終了0、stderr空、最終スタック空、
大域保存なし、JSON／配列の構築・作業と命令数は規範から別計算して資源化します。

```text
configured
null
default
not-object
```

`chapter/13-chapter.bsb`、canonical、stdout、`explain --json`、開示用と既定非開示の
22列trace、state／budgetsを登録します。型・Call出力・状態・予算を含む独立期待を比較し、
traceの末尾空effect列は既存資源の`\t`復元規約を使います。公開CLIにtraceコマンドを足さず、
内部のtrace実行を公開runと同じ入力・偽環境で比較します。

## 6. テスト層と受入ゲート

1. frontend: 型引数区切りの文脈、型付き構築の全配置、span、回復、format。
2. analyzer／stdlib: 両型の解決、型深さ、7語の具体化、保存・合流・初期値、表示／比較条件。
3. IR: Callの入力・出力・状態側と辞書語の整合。出力型欠落、T/E逆転、誤ったペイロード型を
   持つ不正IRを検証段階で拒否し、BuiltinExecutorのcast失敗へ流さない。
4. runtime: 不変値、参照共有、2状態、表示・比較、予算、原子性、非開示、ホストポリシー。
5. conformance／CLI: 正常sourceは原則5コマンド全て。失敗sourceもcheck、checkJson、run、
   format、explainを実行し、構文／型／実行時失敗のコマンド差を固定する。
   実行時失敗ケースのcheck／explainは成功、formatも構文が有効なら成功する。
6. distribution: fat JARで章末全コマンドとRESULT-F022／023を実行。stdout・stderr・終了コード・
   入力不変とハッシュを確認し、JAR内の新規メッセージ資源不足を検出する。

新規7診断は全て`CONFORMANCE`、新規7語は全て`FULL`、既存175仕様診断と112語は
分類を低下させません。coverageツールのヒューリスティックだけでは合格とせず、
型付き語の認識、全行の実行、期待比較のassert、JaCoCoの関連分岐を確認します。
全機能グループの実ファイル数、variant数、CLI実行数、各予算、ハッシュは全資源実体化後に測定します。
今回作成した先行データの件数と、処理系で実行済みの件数はresult-values READMEで別々に記録します。
