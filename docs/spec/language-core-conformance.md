# CORE機能グループ: 適合データと成果物

> 本文は現行実装の規範契約です。

## 1. 適合データの場所

CORE機能グループの機械可読な適合データは次に置きます。

```text
tests/conformance/language-core/
  cases.properties       ケースとコマンド別期待値
  diagnostics.tsv        期待する構造化診断
  resources.tsv          資源境界ごとの結果・終了コード・診断データ
  messages.properties    診断メッセージの正規テンプレート
  unicode-data.properties Unicode 16.0.0の公式適合データURL台帳
  sources/               通常のUTF-8 .bsb入力
  canonical/             formatの期待出力
  generated/             バイナリ・巨大境界の決定的生成記述
  chapter/               章末プログラム、出力、命令トレース
```

`.properties` はUTF-8の `Reader` を使って `java.util.Properties.load(Reader)` で読みます。TSVはBOMなしUTF-8、LF、先頭行をヘッダーとし、タブを列区切りにします。フィールド内の改行、タブ、バックスラッシュは `\n`、`\t`、`\\` で表します。

`diagnostics.tsv` の `fields` は、ASCII `;` で区切った `名前=値` の列です。値に含むバックスラッシュ、セミコロン、等号は `\\`、`\;`、`\=` と表し、フィールドがなければ `-` とします。`messages.properties` の `{name}` はこの構造化フィールドの同名値で置換します。未定義のプレースホルダー、重複キー、置換後に残る波括弧はテストハーネス自身の失敗です。

相対パスはすべて `tests/conformance/language-core` を基準にします。

## 2. 既定期待値

`cases.properties` でコマンド固有の値が省略された場合、次を既定とします。

| 分類 | 終了コード | 標準出力 | 標準エラー |
|---|---:|---|---|
| N正常例 | 0 | 空 | 空 |
| CORE-F001〜CORE-F015・CORE-F027〜CORE-F029のUTF-8・字句・構文失敗 | 9 | 空 | `diagnostics.tsv`から生成 |
| CORE-F016〜CORE-F025（枝番を含む）の静的失敗: `check`・`run` | 8 | 空 | `diagnostics.tsv`から生成 |
| CORE-F016〜CORE-F025（枝番を含む）の静的失敗: `format` | 0 | 正規化したソース | 空 |
| CORE-F026・CORE-F030警告: `check` | 0 | 空 | 警告1件 |
| CORE-F026・CORE-F030警告: `run` | 0 | ケース別の指定値 | 警告1件 |
| CORE-F026・CORE-F030警告: `format` | 0 | 正規化したソース | 空 |

`run` は静的検査に失敗した場合、プログラムの出力を1バイトも書きません。

成功するすべての `format` ケースは、指定された標準形へもう一度 `format` を適用して同じバイト列になることも暗黙の期待値とします。

## 3. 正常例

| ID | 主対象 | 入力 | 実行するコマンド |
|---|---|---|---|
| CORE-N001 | 解析から実行までの一周 | `sources/CORE-N001.bsb` | check, run, format |
| CORE-N002 | 前方参照 | `sources/CORE-N002.bsb` | check, run |
| CORE-N003 | 直接再帰の静的検査 | `sources/CORE-N003.bsb` | check |
| CORE-N004 | 相互再帰の静的検査 | `sources/CORE-N004.bsb` | check |
| CORE-N005 | 全角英数字の限定変換 | `sources/CORE-N005.bsb` | check, run, format |
| CORE-N006 | 識別子NFC | `sources/CORE-N006.bsb` | check, run, format |
| CORE-N007 | LF・CR・CRLF | `generated/CORE-N007.properties` | check, format |
| CORE-N008 | 全角空白 | `sources/CORE-N008.bsb` | check, run, format |
| CORE-N009 | `、`・`，`区切り | `sources/CORE-N009.bsb` | check, run, format |
| CORE-N010 | コメント | `sources/CORE-N010.bsb` | check, run, format |
| CORE-N011 | リテラル内の `#`・`。` | `sources/CORE-N011.bsb` | check, run |
| CORE-N012 | ASCII文字列引用符 | `sources/CORE-N012.bsb` | check, run, format |
| CORE-N013 | 全エスケープ | `sources/CORE-N013.bsb` | check, run, format |
| CORE-N014 | 結合濁点の文字 | `sources/CORE-N014.bsb` | check, run, format |
| CORE-N015 | 補助平面の漢字 | `sources/CORE-N015.bsb` | check, run |
| CORE-N016 | 負数と減算順序 | `sources/CORE-N016.bsb` | check, run |
| CORE-N017 | 文字列の厳密等価性 | `sources/CORE-N017.bsb` | check, run, format |
| CORE-N018 | 助詞の非実行性 | `sources/CORE-N018.bsb` | check, run |
| CORE-N019 | 空本体の恒等効果 | `sources/CORE-N019.bsb` | check, run, format |
| CORE-N020 | フォーマッタの標準形・冪等性 | `sources/CORE-N020.bsb` | format |
| CORE-N021 | 先頭BOM | `generated/CORE-N021.properties` | check, format |
| CORE-N022 | 小数の構文整形 | `sources/CORE-N022.bsb` | format |

CORE-N003とCORE-N004は再帰単語を実行しません。再帰実行の上限はCORE-R011で検査します。

## 4. 失敗例

CORE-F005とCORE-F010は英小文字の枝番だけを持ち、CORE-F012、CORE-F020、CORE-F025には基本ケースに加えて枝番ケースがあります。
CORE-F001〜CORE-F026の既存IDは維持し、CORE-F027〜CORE-F030は既存診断の公開CLI証跡を補う安定IDです。

| ID | 主対象 | 診断 |
|---|---|---|
| CORE-F001 | 不正UTF-8 | `E_INVALID_UTF8` |
| CORE-F002 | NBSP | `E_DISALLOWED_WHITESPACE` |
| CORE-F003 | 双方向制御 | `E_INVISIBLE_CHARACTER` |
| CORE-F004 | 数値後の区切り | `E_MISSING_SEPARATOR` |
| CORE-F005a | 先頭0 | `E_INVALID_NUMBER_LITERAL` |
| CORE-F005b | 整数部なし小数 | `E_INVALID_NUMBER_LITERAL` |
| CORE-F005c | 小数部なし小数 | `E_INVALID_NUMBER_LITERAL` |
| CORE-F006 | 4,097桁 | `E_NUMBER_LIMIT` |
| CORE-F007 | 文字列閉じ忘れ | `E_UNTERMINATED_STRING` |
| CORE-F008 | 不正エスケープ | `E_INVALID_ESCAPE` |
| CORE-F009 | サロゲート指定 | `E_INVALID_UNICODE_SCALAR` |
| CORE-F010a | 空文字リテラル | `E_CHARACTER_LENGTH` |
| CORE-F010b | 2クラスタ文字 | `E_CHARACTER_LENGTH` |
| CORE-F011 | 括弧混在 | `E_MIXED_STACK_PARENTHESES` |
| CORE-F012 | `--`欠落 | `E_EXPECTED_STACK_SEPARATOR` |
| CORE-F012c | 定義ヘッダー内のコメント | `E_COMMENT_NOT_ALLOWED` |
| CORE-F013 | `とは`の隣接違反 | `E_DEFINITION_ADJACENCY` |
| CORE-F014 | `こと。`欠落 | `E_EXPECTED_WORD_END` |
| CORE-F015 | トップレベル実行語 | `E_UNEXPECTED_TOP_LEVEL` |
| CORE-F016 | 予約名定義 | `E_RESERVED_NAME` |
| CORE-F017 | 正規化後重複 | `E_DUPLICATE_NAME` |
| CORE-F018 | `メイン`欠落 | `E_MISSING_MAIN` |
| CORE-F019 | `メイン`効果違反 | `E_INVALID_MAIN_EFFECT` |
| CORE-F020 | 未定義単語 | `E_UNDEFINED_WORD` |
| CORE-F020b | 型名を呼び出し位置で使用 | `E_NAME_NOT_CALLABLE` |
| CORE-F021 | スタック不足 | `E_STACK_UNDERFLOW` |
| CORE-F022 | 型不一致 | `E_TYPE_MISMATCH` |
| CORE-F023 | 宣言効果不一致 | `E_WORD_EFFECT_MISMATCH` |
| CORE-F024 | `メイン`の残存値 | `E_MAIN_STACK_NOT_EMPTY` |
| CORE-F025 | 小数の機能境界 | `E_FEATURE_NOT_AVAILABLE` |
| CORE-F025b | 未知の型名 | `E_UNKNOWN_TYPE` |
| CORE-F026 | 助詞位置 | `W_PARTICLE_POSITION` |
| CORE-F027 | 識別子候補内の禁止記号 | `E_INVALID_IDENTIFIER` |
| CORE-F028 | トークンに属さないASCIIカンマ | `E_UNEXPECTED_CHARACTER` |
| CORE-F029 | `こと`と`。`の改行分離 | `E_EXPECTED_DEFINITION_END_MARK` |
| CORE-F030 | UTS #39 skeletonが一致する別名 | `W_CONFUSABLE_IDENTIFIER` |

診断の完全なフィールドは `diagnostics.tsv`、人間向けメッセージは `messages.properties` と [language-core-diagnostics.md](language-core-diagnostics.md) の表示規則から生成します。同じ入力に対する `run` は `check` と同じ検査診断を返します。CORE-F027〜CORE-F030は`check --json`の独立期待も`tests/conformance/cli-json`に持ち、span、stage、fields、expected、actual、fix、relatedを版1文書としてバイト一致させます。

中央ハーネスは`cases.properties`のID集合と`source`、`canonical`、`diagnostics.tsv`の行を
相互照合します。CORE-F027〜CORE-F030を含め、未知ID、重複ID、欠落資源、未使用source/canonical、
未使用診断行を適合データ自体の失敗として拒否します。警告ケースは警告ID集合にも必ず属し、
診断171件の宣言的証跡を集計するときにメッセージ辞書だけを証跡とは数えません。

## 5. 資源境界

CORE-R001〜CORE-R014は `generated/*.properties` の生成記述を使います。全境界の結果、終了コード、診断コード、`limit`、`observed` は `resources.tsv` を正解データとします。`-` は該当フィールドなしです。

| ID | 上限 | 対象境界 |
|---|---|---|
| CORE-R001 | ソースバイト | 32 MiB / +1バイト |
| CORE-R002 | 識別子 | 128 / 129コードポイント |
| CORE-R003 | トークン | 250,000 / 250,001 |
| CORE-R004 | 定義 | 10,000 / 10,001 |
| CORE-R005 | 診断 | 99件 + 上限通知 |
| CORE-R006 | IR | 250,000 / 250,001命令 |
| CORE-R007 | 文字列 | 16 MiB / +1バイト |
| CORE-R008 | 数値字句 | 4,096 / 4,097桁 |
| CORE-R009 | 整数結果 | 65,536 / 65,537桁 |
| CORE-R010 | データスタック | 65,536 / 65,537値 |
| CORE-R011 | 呼出スタック | 1,024 / 1,025フレーム |
| CORE-R012 | 実行命令 | 10,000,000 / +1命令 |
| CORE-R013 | 実行時間 | 30秒 / +1ナノ秒 |
| CORE-R014 | 標準出力 | 64 MiB / +1バイト |

相互に独立した上限を先に踏まないよう、CORE-R003、CORE-R006、CORE-R010、CORE-R011、CORE-R012、CORE-R014は対象コンポーネントへ合成入力を直接渡します。これは公開CLIの経路を省略するためではなく、防御層ごとの境界を分離して試験するためです。CORE-R011はJava再帰を使わない合成IR呼出鎖でフレーム境界だけを検査します。CORE-N001が全経路の統合試験を担います。

## 6. 生成記述

生成器は同じ入力から同じバイト列または内部命令列を作らなければなりません。ロケール、乱数、現在時刻、OS改行を参照しません。

生成器名と必須キーは各 `.properties` に記録します。未認識の生成器、必須キーの欠落、生成結果が宣言した大きさと違う場合はテストハーネス自身の失敗とし、BSB診断として扱いません。

## 7. 章末プログラムとトレース

章末プログラムは `chapter/02-chapter.bsb` です。期待標準出力は `chapter/02-chapter.stdout`、命令単位トレースは `chapter/02-chapter.trace.tsv` です。

トレースは公開CLIへ追加しません。インタープリタへ内部 `TraceSink` を注入し、通常実行と同じIR命令を実行したときのイベントを受け取ります。

各イベントは次を持ちます。

```text
sequence
word
opcode
sourceLine
sourceColumn
dataBefore
dataAfter
callDepthBefore
callDepthAfter
outputHex
particles
```

`Call` が利用者定義単語を呼ぶ場合はデータスタックをその場で変更せず、呼出深さだけを増やします。組み込み単語への `Call` は同じイベント内でスタックと出力を変更します。助詞は独立イベントにせず、関連する `Call` の `particles` へ位置つきで記録します。

通常実行とトレース実行について、終了コード、標準出力、最終データスタックが同一でなければなりません。
