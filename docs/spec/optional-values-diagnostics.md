# OPT機能グループ: `任意<T>`の診断

> 本文は現行実装の規範契約です。

## 1. 構文診断: 終了コード9

配列の既存診断との後方互換を保ち、`任意`だけに次を追加します。

| コード | 条件 | 主位置 | 必須フィールド |
|---|---|---|---|
| `E_EXPECTED_OPTIONAL_ELEMENT_TYPE` | `任意<>`で型引数がない | `>` | `typeConstructor=任意` |
| `E_EXPECTED_OPTIONAL_TYPE_END` | `任意<型`の`>`欠落 | 欠落pointまたは次トークン | `typeConstructor=任意`、`startLine`、`startColumn` |

- 要素欠落: `expected=具体型`、`actual=>`、`fix=任意<JSON>のように具体型を追加してください`
- 終端欠落: `expected=>`、`actual=次のトークンまたはEOF`、`fix=型引数の末尾へ>を追加してください`

終端欠落では構築子開始位置を`related`へ`任意`として1件載せます。

配列の`E_EXPECTED_ARRAY_ELEMENT_TYPE`・`E_EXPECTED_ARRAY_TYPE_END`は改名しません。
型入れ子257段目は既存`E_SYNTAX_DEPTH_LIMIT`で、`limit=256`、`observed=257`を返します。

空の型引数では、その構築子自身の`>`を消費して無効な型参照として回復します。外側の構築子は
自分の`>`を通常どおり消費し、同じ空要素から終端不足を派生させません。混在時は空にした構築子の
種類を使うため、`配列<任意<>>`は任意型診断だけ、`任意<配列<>>`は既存の配列型診断だけです。
実際に複数の`>`が欠落する場合は、欠落した構築子ごとに最内側から1件ずつ終端不足を返します。
全体の100診断上限と`E_DIAGNOSTIC_LIMIT`は既存契約を維持します。

## 2. 静的型診断: 終了コード8

| 条件 | コード |
|---|---|
| 裸の`任意`を具体型として使う | `E_OPTIONAL_ELEMENT_TYPE_REQUIRED` |
| `任意<T>`の`T`が辞書専用型制約 | `E_TYPE_CONSTRAINT_NOT_ALLOWED` |
| `T`が未知型 | `E_UNKNOWN_TYPE` |
| `T`が予約済み後続型 | `E_FEATURE_NOT_AVAILABLE` |
| `配列<任意<T>>` | 既存`E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED` |
| 任意型の具体型不一致 | 文脈に応じた既存の型・合流診断 |
| 内包型が非表示・非比較型 | 既存`E_TYPE_MISMATCH`。`actualType`は任意型全体 |

`E_OPTIONAL_ELEMENT_TYPE_REQUIRED`の主位置は`任意`のspan、段階は`typeAndStack`、
必須フィールドは`typeConstructor=任意`、`context=スタック効果`、
`expected=任意<具体型>`、`actual=任意`、
`fix=任意<JSON>のように具体型を追加してください`です。型制約・未知型診断の主位置は
内側の型参照です。型名を含む`actual`、スタック、フィールドは
常に`任意<整数>`のような標準形を使います。外側の任意型について派生診断を重ねません。

## 3. 実行時診断

| コード | 条件 | 必須フィールド |
|---|---|---|
| `E_OPTIONAL_VALUE_ABSENT` | `ない`へ`任意から値を取り出す`を適用 | `word=任意から値を取り出す`、`optionalType`、`state=absent` |

主位置は呼出しspan、段階は`runtime`、終了コードは10です。`expected=値がある任意値`、
`actual=ない`、`fix=任意に値があるで分岐してから取り出してください`とします。
内包値やJSONキーはフィールド・メッセージへ載せません。

JSON非必須検索の非オブジェクト値は既存`E_JSON_KIND_MISMATCH`、作業超過は既存
`E_JSON_WORK_LIMIT`です。後者の`operation`は`objectGetOptional`、要求量は1です。
キー不在は正常な`ない`であり、診断を出しません。任意値内のJSONを表示・等価比較して
作業上限へ達した場合は、既存の`operation=display`・`operation=equals`を維持します。

## 4. 人間向けメッセージ

追加コードの日本語メッセージを次に固定します。可変情報は既存どおり構造化フィールド、
期待値、実値、修正候補へ分離します。

```properties
E_EXPECTED_OPTIONAL_ELEMENT_TYPE=任意型には型引数が必要です。
E_EXPECTED_OPTIONAL_TYPE_END=任意型を閉じる「>」が必要です。
E_OPTIONAL_ELEMENT_TYPE_REQUIRED=「任意」は型引数を省略できません。
E_OPTIONAL_VALUE_ABSENT=「任意から値を取り出す」に渡された任意値には値がありません。
```

## 5. 優先順位と原子性

1. 型引数構文と型深さ
2. 裸型、型制約、後段型、未知型、配列要素型制限
3. スタック不足と具体型不一致
4. JSON値種別
5. JSON作業量
6. `E_OPTIONAL_VALUE_ABSENT`

同一呼出しでは最初の該当1件だけを返します。実行時失敗は入力スタック、保存値、
プログラムstdout/stderrと、失敗した操作が予約しようとした配列・JSON予算を変更しません。
先行する成功操作で消費済みの予算は戻しません。通常の診断出力はプログラムstderrとは別に
既存CLI契約どおり処理します。
