# JERG機能グループ: JSON参照・構築の簡潔化

> 本文は現行実装の規範契約です。
>
> ここで明示する追加以外は、先行する機能グループの契約を維持します。
> [診断](json-access-construction-diagnostics.md)および
> [適合性](json-access-construction-conformance.md)と一体です。

## 1. 目的と機能境界

HTTP APIやAI APIのJSONを扱う際に反復していた、object・arrayの段階的な参照と、空objectへの
連続設定を短くします。RFC 6901 JSON Pointerによる決定的な任意参照と、キー列・値列からの
不変object一括構築だけを追加します。JSONリテラル、JSONPath、URI fragment表現、動的な式評価、
objectの可変更新は追加しません。

## 2. 公開語

既存182語の後ろへ次の2語を表の順で追加し、辞書は184語になります。両語とも純粋で、能力・副作用は空、
`featureGroup="JERG"`、`returnsNormally=true`、型規則`fixed`です。

| 正規語 | スタック効果 | 意味 |
|---|---|---|
| `JSONをポインターで任意参照する` | `JSON 文字列 -- 任意<JSON>` | RFC 6901 pointerで参照し、不在なら値なしを返す |
| `JSONオブジェクトを構築する` | `配列<文字列> 配列<JSON> -- JSON` | 対応するキー・値から不変objectを構築する |

別名はありません。既存JSON群33語の順序、識別値、意味は変えません。

## 3. JSON Pointer参照

pointerはRFC 6901の文字列表現です。空文字列は入力JSON自身を参照し、それ以外は`/`で始まります。
各token内の`~1`を`/`、`~0`を`~`へ復号します。不正な`~` escapeまたは先頭`/`欠落は
`E_JSON_POINTER_SYNTAX`です。offsetは入力文字列先頭からの0始まりUnicodeスカラー位置です。

object tokenは復号後キーの完全一致でたどります。array tokenは`0`または先頭0のないASCII十進整数です。
member・要素がない、array tokenが`-`・範囲外・非正規整数、途中値が容器でない場合は値なしです。
参照先がJSON nullの場合は、JSON nullを内包する値ありです。

構文検証は参照より先にpointer全体へ行うため、途中の値が不在でも後続tokenの不正escapeを診断します。
成功時は入力JSONを変更せず参照先を共有でき、新しいJSON構築単位を消費しません。

## 4. JSONオブジェクトの一括構築

キーと値を同じ添字で対応づけ、入力配列順を保持した新しい不変objectを返します。両配列の長さが違えば
`E_JSON_OBJECT_BUILD_LENGTH_MISMATCH`、同じキーが複数あれば既存の`E_JSON_DUPLICATE_KEY`です。
空の2配列からは空objectを構築します。子JSONは共有でき、構築単位は新しいobject 1とmember参照数です。

## 5. 資源と原子性

pointer参照はpointerのUTF-8 byte数とtoken数を既存JSON作業量へ課金します。一括構築はキーのUTF-8
byte数、member数、object 1を既存JSON作業量へ課金し、object 1とmember参照数を既存JSON構築量へ
課金します。既存のJSON深さ・ノード数・object member数上限を迂回しません。

捕捉不能診断では入力stack、保存値、出力、JSON作業量、JSON構築量を変更しません。成功した参照と構築は
既存JSONの通常trace非開示規則に従います。

## 6. 説明JSONとの境界

2語の追加は`explain --json`版1の加算的変更です。`schemaVersion`を増やさず、`featureGroup`へ未知でも
保持可能な文字列`JERG`を追加します。既存182語は同じ順序の接頭辞として残ります。
