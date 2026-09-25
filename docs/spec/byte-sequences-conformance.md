# BYTES機能グループ: `バイト列`の適合データ・テスト計画

> 本文は現行実装の規範契約です。
>
> 規範は[不変バイト列](byte-sequences.md)と
> [診断・失敗境界](byte-sequences-diagnostics.md)です。

## 1. 物理構成と独立性

```text
tests/conformance/byte-sequences/
  catalog.tsv
  vectors/utf8.tsv
  vectors/utf8-failures.tsv
  vectors/base64.tsv
  vectors/base64-failures.tsv
  operations/slices.tsv
  operations/equality.tsv
  resources.tsv
  expected/diagnostics.tsv
  expected/states.tsv
  expected/trace.tsv
  sources/*.bsb
  canonical/*.bsb
  chapter/16-chapter.bsb
  messages.properties
  manifest.tsv
  README.md
```

`catalog.tsv`はBYTES-N001〜012、BYTES-F001〜012、BYTES-R001〜010をこの順で過不足なく登録します。
全詳細行はcatalog IDへ属し、全evidenceは実在し、manifestとREADME・manifest自身の和集合が物理ファイルと
一致しなければなりません。TSVはBOMなしUTF-8、LF、末尾LF、固定header、空欄を空値として扱います。

UTF-8期待はコードポイント列と固定16進列、Base64期待は入力16進列と固定ASCIIを持ちます。
本番Java codec、JDKのdecoder、実行器の出力を期待生成元にしません。境界用の巨大値は物理ファイルへ展開せず、
`repeatByte`等の閉じたレシピと仕様数値からテスト側で構築します。

## 2. 正常例 BYTES-N001〜BYTES-N012

| ID | 必須観測 |
|---|---|
| BYTES-N001 | `バイト列`型、空値、長さ0、保存・合流、固定スタック効果 |
| BYTES-N002 | ASCII、NUL、日本語、補助文字、結合列のUTF-8符号化・復号往復 |
| BYTES-N003 | U+007F/0080/07FF/0800/D7FF/E000/FFFF/10000/10FFFFの最短UTF-8 |
| BYTES-N004 | BOMを通常U+FEFFとして保持し、正規化・改行変換・終端NULを行わない |
| BYTES-N005 | UTF-8の6失敗種類と最初の不正バイト位置を専用語で観測 |
| BYTES-N006 | RFC 4648の空、`f`〜`foobar`、0/1/2 paddingの規範Base64 |
| BYTES-N007 | 0〜255全バイトと基本alphabetのBase64往復 |
| BYTES-N008 | Base64の4失敗種類、文字位置／EOF、空白・URL-safe・pad bit拒否 |
| BYTES-N009 | 長さ、`[0,0)`、`[N,N)`、`[0,N)`、中間の一部取得、元値不変 |
| BYTES-N010 | 同値、先頭・中間・末尾不一致、長さ不一致、任意・結果内の比較と短絡 |
| BYTES-N011 | 11語のformat冪等性、136語のexplain版1、能力・副作用・名前付き要求不変 |
| BYTES-N012 | 章末でUTF-8／Base64成功と両失敗分岐、一部取得、比較、通常trace非開示 |

## 3. 失敗例 BYTES-F001〜BYTES-F012

| ID | 入力・観測 | 期待 |
|---|---|---|
| BYTES-F001 | 変換・長さ・一部取得の入力不足 | `E_STACK_UNDERFLOW` |
| BYTES-F002 | 各固定入力位置の型違い | `E_TYPE_MISMATCH` |
| BYTES-F003 | `バイト列`を表示 | `E_TYPE_MISMATCH`、原因`バイト列` |
| BYTES-F004 | `UTF8復号失敗`を表示 | `E_TYPE_MISMATCH`、原因型を保持 |
| BYTES-F005 | `Base64復号失敗`を表示 | `E_TYPE_MISMATCH`、原因型を保持 |
| BYTES-F006 | UTF-8失敗値を等値比較 | `E_TYPE_MISMATCH`、等値比較不能 |
| BYTES-F007 | Base64失敗値を等値比較 | `E_TYPE_MISMATCH`、等値比較不能 |
| BYTES-F008 | `配列<バイト列>` | `E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED` |
| BYTES-F009 | `配列<UTF8復号失敗>` | 同上 |
| BYTES-F010 | `配列<Base64復号失敗>` | 同上 |
| BYTES-F011 | 一部取得の負数、逆順、終端超過 | `E_BYTE_SEQUENCE_RANGE_OUT_OF_BOUNDS` |
| BYTES-F012 | UTF-8復号・Base64符号化の文字列結果16 MiB超 | `E_STRING_UTF8_LIMIT` |

復号入力不正はF系でも診断にせず、BYTES-N005・008の正常な失敗結果として扱います。到達不能コードは
`W_UNREACHABLE_CODE`だけを返し、新語由来の派生型診断を抑止します。

## 4. 資源・内部境界 BYTES-R001〜BYTES-R010

| ID | 固定する境界・対照 |
|---|---|
| BYTES-R001 | 1値67,108,864／67,108,865バイト |
| BYTES-R002 | 構築累積134,217,728／134,217,729バイト、拒否予約0追加 |
| BYTES-R003 | 作業累積268,435,456／268,435,457バイト、拒否予約0追加 |
| BYTES-R004 | Base64出力入力12,582,912／12,582,913、UTF-8文字列16,777,216／1超過 |
| BYTES-R005 | 全体の一部取得を64 MiBで構築0・作業1、共有範囲の元値不変 |
| BYTES-R006 | 64 MiB比較の同値・末尾不一致、長さ違い0、途中の作業拒否原子性 |
| BYTES-R007 | 不正UTF-8・Base64が入力全長作業、構築0、失敗結果1値になる |
| BYTES-R008 | Base64復号の先行走査後、構築・追加作業の同時予約が片側だけ増えない |
| BYTES-R009 | 内容・長さ・状態・接頭辞・ハッシュ・例外markerが全公開面にない |
| BYTES-R010 | 最大型深さ、最大値、一部取得の共有表現、変換、traceを最大ヒープ512 MiBで統合 |

OOM、StackOverflow、Java例外名は期待終了にしません。内部不変条件違反は利用者診断へ推測変換せず
内部終了70とします。

## 5. データ表の意味

`vectors/utf8.tsv`はUnicodeコードポイント列、UTF-8 16進列、JSON文字列表現を同じvectorへ固定します。
`vectors/utf8-failures.tsv`は入力16進列、6種類、0始まりバイト位置を持ちます。
`vectors/base64.tsv`は入力16進列と規範ASCIIを、`vectors/base64-failures.tsv`は入力UTF-8 16進列、
4種類、0始まりUnicodeスカラー位置を持ちます。

`operations/slices.tsv`と`operations/equality.tsv`は結果だけでなく構築・作業量を固定します。
`resources.tsv`は上限、使用済み、要求、観測、診断、スタック・予約原子性を固定します。
`expected/states.tsv`は成功・失敗結果の型・状態・公開fieldだけを記録し、元内容を結果表現へ複写しません。
`expected/trace.tsv`は固定`<redacted>`と禁止markerを完全一致で検査します。

## 6. 章末成果物 BYTES-N012

章末は`「こんにちは🌍」`をUTF-8バイト列へ変換し、長さ、規範Base64、UTF-8復号成功を表示します。
先頭15バイトを取り出して`「こんにちは」`の符号化結果と比較し、`はい`を表示します。
`/w==`から作った`FF`はUTF-8復号で`invalidLeadingByte`位置0、`Zg`はBase64復号で
`invalidLength`位置2になります。失敗値自体とバイト列を直接表示しません。

通常runのstdoutは独立固定し、stderrは空、最終スタック・保存は空です。traceでは入力・中間バイト列、
失敗値、それらを含む結果を長さごと伏せ、trace有無でstdout、終了、課金を変えません。

## 7. 受入ゲート

1. `python3 tools/byte_sequences_data.py --check`が全表、ベクトル、参照、manifest、非開示を検査する。
2. Python単体試験がUTF-8境界、Base64 pad bit、位置、改ざん検出を本番Javaなしで確認する。
3. `ByteSequenceConformanceDataTest`が同じ物理資源をclasspathから独立に厳格読込みする。
4. データ検査だけでなく、対象の組み込み語を`run`で検証する。
5. 全34 IDを中央カタログから過不足なく消費する。
6. 最終的に新11語を`FULL`、新4診断を`CONFORMANCE`にし、既存分類を低下させない。

期待と実装が食い違った場合は規範を再確認し、理由を記録せずgoldenを再生成しません。
