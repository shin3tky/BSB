# JSHAPE機能グループ: 最小JSON形状検証

> 本文は現行実装の規範契約です。
>
> [診断](json-shapes-diagnostics.md)および[適合性](json-shapes-conformance.md)と一体です。

## 1. 範囲と設計選択

この機能グループは、外部から得た`JSON`が期待する必須・任意メンバー、値種別、入れ子構造、
一様な配列要素を持つかを、副作用なしに検証します。成功時は原JSONを返し、不一致時は原文や値を
含まない閉じた失敗値を返します。JSONの変換、参照、構築の短縮は後続機能です。

初版はJSON Schema文書を実行時に解釈せず、第一級の不変`JSON形状`をBSBの構築語で作ります。

| 観点 | 第一級`JSON形状` | JSON Schema部分集合 |
|---|---|---|
| HTTP応答例の表現 | 11構築呼出し、補助JSON文書なし | 1スキーマJSONだが`type`、`properties`、`required`が重複 |
| 不正な形状定義 | 型検査済み構築語だけで閉じる | 未知語彙、重複、schema版の実行時検査が必要 |
| 失敗位置 | 検証対象だけのパス | instance pathとschema pathの2種類が必要 |
| 版管理 | JSHAPE機能グループとして固定 | draftまたは独自dialectの固定が必要 |
| 実装範囲 | 7種別、nullable、配列、object | meta-schema、keyword評価規則が追加で必要 |

JSON Schemaとの交換が具体的に必要になるまで、暗黙変換、`$ref`、組合せ、数値範囲、文字列pattern、
追加member拒否は導入しません。

## 2. 型と値

`JSON形状`は不変の閉じた実行時型です。次のノードだけを持ちます。

- `null`、真偽、整数、小数、文字列の各葉
- 1個の要素形状を持つ配列
- 必須member列と任意member列を持つobject
- null以外の形状1個を包むnullable

objectの未記載memberは許可し、検査しません。member名はUnicodeスカラー列の完全一致で扱います。
同じ名前を再設定した場合は、元の位置を保ったまま子形状と必須性を置き換えます。したがって動的な
member名にも捕捉不能な「重複定義」診断は発生しません。

`JSON形状失敗`は1件の不一致を表す不変の閉じた型です。保持する公開情報は`kind`、`path`、
`expectedKind`、`actualKind`だけで、原JSON、member名の別コピー、値、文字列、本文、形状を保持しません。
`expectedKind`と`actualKind`は第5節の閉じたASCII識別値です。

検証結果型は`結果<JSON,配列<JSON形状失敗>>`です。成功値は入力と同じ不変JSON値、失敗配列は
1件以上、最大256件です。失敗配列は通常の`配列<JSON形状失敗>`なので、既存の配列長・取得・反復を
使います。`JSON形状`と`JSON形状失敗`は表示・等値比較の対象外です。

## 3. 公開組み込み語

辞書の既存語の後ろへ次の15語をこの順で追加します。全語は能力・副作用なし、
`featureGroup="JSHAPE"`、`returnsNormally=true`です。

| 正規語 | スタック効果 | 意味 |
|---|---|---|
| `JSONヌルの形状` | `-- JSON形状` | nullだけを受理する葉 |
| `JSON真偽の形状` | `-- JSON形状` | 真偽だけを受理する葉 |
| `JSON整数の形状` | `-- JSON形状` | 整数だけを受理する葉 |
| `JSON小数の形状` | `-- JSON形状` | 小数だけを受理する葉 |
| `JSON文字列の形状` | `-- JSON形状` | 文字列だけを受理する葉 |
| `JSON配列の形状にする` | `JSON形状 -- JSON形状` | 子を全要素へ適用する配列形状 |
| `空のJSONオブジェクト形状` | `-- JSON形状` | member制約を持たないobject形状 |
| `JSON形状に必須キーを設定する` | `JSON形状 文字列 JSON形状 -- JSON形状` | objectへ必須memberを設定 |
| `JSON形状に任意キーを設定する` | `JSON形状 文字列 JSON形状 -- JSON形状` | objectへ任意memberを設定 |
| `JSON形状をヌル許容にする` | `JSON形状 -- JSON形状` | nullまたは元形状を受理 |
| `JSONの形状を検証する` | `JSON JSON形状 -- 結果<JSON,配列<JSON形状失敗>>` | 全不一致を規範順に収集 |
| `JSON形状失敗の種類を取り出す` | `JSON形状失敗 -- 文字列` | `kind`を返す |
| `JSON形状失敗のパスを取り出す` | `JSON形状失敗 -- 文字列` | `path`を返す |
| `JSON形状失敗の期待種類を取り出す` | `JSON形状失敗 -- 文字列` | `expectedKind`を返す |
| `JSON形状失敗の実際種類を取り出す` | `JSON形状失敗 -- 文字列` | `actualKind`を返す |

setter 2語の第1入力はobject形状でなければなりません。nullable objectを暗黙に剥がさず、配列・葉も
受理しません。この誤用は外部JSONの不一致ではなくプログラムの形状構築誤りなので、捕捉不能な
`E_JSON_SHAPE_OBJECT_REQUIRED`です。nullableをnullableにする場合は同じ値を返します。

## 4. 必須、任意、null

object memberは次のように判定します。

| 宣言 | 入力状態 | 結果 |
|---|---|---|
| 必須 | 欠落 | `missingRequiredKey` |
| 必須 | JSON null、子が非nullable | `nullNotAllowed` |
| 必須 | JSON null、子がnull葉またはnullable | 成功 |
| 任意 | 欠落 | 成功 |
| 任意 | JSON null、子が非nullable | `nullNotAllowed` |
| 任意 | JSON null、子がnull葉またはnullable | 成功 |
| 必須・任意 | 値あり | 子形状で検査 |

任意は「欠落を許す」であり、null許容を意味しません。これにより欠落、JSON null、値ありの3状態を
暗黙に潰しません。

## 5. 種類、パス、判定順

JSON種類識別値は`null|boolean|integer|decimal|string|array|object`です。整数と小数は既存の
`JsonInteger`と`JsonDecimal`の区別を維持します。

失敗種類は次の閉じた3値です。

| `kind` | `expectedKind` | `actualKind` |
|---|---|---|
| `missingRequiredKey` | 子形状の種類。nullableなら非null側 | `missing` |
| `nullNotAllowed` | 子形状の種類 | `null` |
| `kindMismatch` | 期待する種類 | 実際のJSON種類 |

pathはRFC 6901 JSON Pointerの文字列表現に固定します。rootは空文字列、object memberは`/`を足し、
member名中の`~`を`~0`、`/`を`~1`へこの順でescapeします。配列添字は先頭0なしの10進数です。
この機能グループはpathによる参照語や逆解析を提供しません。

検証は反復的なdepth-first pre-orderです。objectは形状へ最初に設定されたmember順、配列は添字昇順で
進みます。種類不一致またはnull不許可のノードでは子へ降りません。object自身の種類が一致した後、
必須・任意を混ぜた設定順で各memberを検査します。失敗が256件に達したら走査を正常に打ち切り、
その256件を返します。したがって同じ入力と形状から同じ失敗列を返します。

## 6. 資源上限と原子性

| 資源 | 上限 | 超過時 |
|---|---:|---|
| 1形状のノード数 | 65,536 | `E_JSON_SHAPE_NODE_LIMIT` |
| 形状の入れ子深さ | 256 | `E_JSON_SHAPE_DEPTH_LIMIT` |
| 1検証の訪問ノード数 | 67,108,864 | `E_JSON_SHAPE_WORK_LIMIT` |
| 1検証の失敗件数 | 256 | 256件で正常打切り |
| 1失敗pathのUTF-8長 | 65,536 bytes | `E_JSON_SHAPE_PATH_LIMIT` |

葉と空objectのノード数・深さは1です。配列・nullableは`1 + child`、objectは自身1と各子の合計、
深さは根から葉までのノード数です。setterは置換後の形状全体、wrapperは生成後の形状全体を事前検査し、
上限超過なら入力スタックを変更しません。

検証の訪問1ノードを作業1とし、欠落memberも1と数えます。種類不一致で降りなかった子、未記載object
member、256件到達後の残りは数えません。既存JSON作業量とは別に累積し、上限超過時は捕捉不能です。

形状構築失敗、検証作業・path上限では、入力スタック、保存値、stdout、stderr、公開出力、拒否された
予算予約を変更しません。入力JSON由来の不一致だけを結果の失敗値にします。型・スタック誤り、資源上限、
能力不在、取消、内部失敗を結果へ変換しません。

## 7. 非開示、trace、公開互換性

`JSON形状`と`JSON形状失敗`、それらを含む配列・任意・結果は通常traceで値全体を`<redacted>`とします。
型名、結果の成功・失敗状態、失敗件数も通常traceへ出しません。失敗値、診断、fields、expected、actual、
fix、例外messageへ、入力JSON、入力文字列、objectの全キー列、HTTP本文、秘密を複写しません。

利用者が4 accessorの戻す閉じた識別値またはpathを明示的に出力することは許します。pathは形状へ明示した
member名を含み得るため、通常traceが自動で開示しないことと、利用者の明示出力を区別します。

`check --json`と`explain --json`は外側`schemaVersion=1`を維持します。新しい型、15語、型規則、
JSHAPE分類、5診断を加算し、追加前の組み込み語と仕様対象診断の意味・順序を変更しません。
