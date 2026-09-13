# CORE機能グループ: Unicode適合仕様

> 本文は現行実装の規範契約です。

## 1. 基準版

CORE機能グループのUnicode基準版はUnicode 16.0.0とします。識別子カテゴリー、NFC、拡張書記素クラスタ、不可視文字、紛らわしい名前の判定は、すべてこの版のデータへ固定します。

- 基準: [Unicode 16.0.0](https://www.unicode.org/versions/Unicode16.0.0/)
- 文字カテゴリー: Unicode Character Database 16.0.0
- 正規化: Unicode 16.0.0版UAX #15
- 書記素分割: Unicode 16.0.0版UAX #29のExtended Grapheme Cluster
- 紛らわしい名前: Unicode 16.0.0版UTS #39のconfusable skeleton

JDK 25の `Character` はUnicode 16.0の文字情報に基づくため、識別子カテゴリーの実装に利用できます。ただし、Java APIの挙動自体をBSB仕様にはしません。書記素分割と紛らわしい名前の判定は、Unicode 16.0の公式適合データを通過する実装を使用します。

### 1.1 固定する公式データ

実装と適合試験が参照するファイルを次に固定します。機械可読なURL台帳は `tests/conformance/language-core/unicode-data.properties` です。

| 用途 | Unicode 16.0.0の公式ファイル |
|---|---|
| 一般カテゴリー | [UnicodeData.txt](https://www.unicode.org/Public/16.0.0/ucd/UnicodeData.txt) |
| Default Ignorable | [DerivedCoreProperties.txt](https://www.unicode.org/Public/16.0.0/ucd/DerivedCoreProperties.txt) |
| Bidi Control・Variation Selector | [PropList.txt](https://www.unicode.org/Public/16.0.0/ucd/PropList.txt) |
| NFC適合 | [NormalizationTest.txt](https://www.unicode.org/Public/16.0.0/ucd/NormalizationTest.txt) |
| 書記素プロパティ | [GraphemeBreakProperty.txt](https://www.unicode.org/Public/16.0.0/ucd/auxiliary/GraphemeBreakProperty.txt) |
| 書記素分割適合 | [GraphemeBreakTest.txt](https://www.unicode.org/Public/16.0.0/ucd/auxiliary/GraphemeBreakTest.txt) |
| Extended Pictographic | [emoji-data.txt](https://www.unicode.org/Public/16.0.0/ucd/emoji/emoji-data.txt) |
| confusable skeleton | [confusables.txt](https://www.unicode.org/Public/security/16.0.0/confusables.txt) |

`latest`を指すURLや実行環境内蔵版への暗黙フォールバックは使いません。テストをオフライン実行する場合は、上記URLの内容を改変せずテスト資源へ固定します。

## 2. Unicodeスカラー値

BSBの文字列と文字は、U+0000〜U+10FFFFからサロゲート領域U+D800〜U+DFFFを除いたUnicodeスカラー値の列です。不正なUTF-8、孤立サロゲート、範囲外の `\u{...}` を値へ変換しません。

JVM内部ではUTF-16を使用できますが、診断、添字、長さ、文字判定へUTF-16コード単位を露出しません。

## 3. 識別子カテゴリー

識別子の `Letter`、`Mark`、`Number` はUnicode 16.0のGeneral_Categoryを次のように展開します。

| 仕様名 | General_Category |
|---|---|
| `Letter` | Lu, Ll, Lt, Lm, Lo |
| `Mark` | Mn, Mc, Me |
| `Number` | Nd, Nl, No |

カテゴリー判定はコードポイント単位です。全角英数字の限定変換を先に行い、その結果へNFCを適用してから、識別子構文と128コードポイント上限を検査します。

## 4. 識別子で禁止するコードポイント

カテゴリー上は識別子継続に使える場合でも、Unicode 16.0の次のプロパティまたは集合に属するコードポイントを禁止します。

- `Default_Ignorable_Code_Point`
- `Bidi_Control`
- `Variation_Selector`
- General_Category `Cc`、`Cf`、`Cs`
- U+200C ZERO WIDTH NON-JOINER
- U+200D ZERO WIDTH JOINER

これらが識別子に現れた場合は `E_INVISIBLE_CHARACTER` です。文字列と文字では、Unicodeスカラー値として有効であれば保持できますが、フォーマッタは直接表記を禁止するものを `\u{X}` へ変換します。

## 5. 書記素クラスタ

`文字` の個数検査と人間向け診断列は、Unicode 16.0版UAX #29のExtended Grapheme Cluster規則を使います。

次はそれぞれ1クラスタです。

- `が` U+304C
- `か` U+304B + U+3099
- `𠮷` U+20BB7
- 絵文字のZWJ列

識別子ではZWJを禁止しますが、文字列と文字の値では許可します。端末上のセル幅は診断列へ使用しません。

## 6. NFC

識別子だけにUnicode 16.0版NFCを適用します。文字列、文字、コメントは正規化しません。

NFCの前に行う互換変換は、全角英字A-Z、a-zと全角数字0-9からASCII相当への変換だけです。その他のNFKC互換文字を変換しません。

## 7. 紛らわしい名前

正規化後の異なる2つの名前について、Unicode 16.0版UTS #39のconfusable skeletonが一致した場合は、後から現れた名前へ `W_CONFUSABLE_IDENTIFIER` を報告します。

比較対象は次の集合です。

- 同じソース内の利用者定義名
- CORE機能グループで利用可能な組み込み単語、組み込み値、型名
- 予約された将来機能の名前

この警告で名前を統合せず、名前解決結果も変更しません。診断には両方の入力名、正規化後の名前、skeleton、先に現れた名前の位置を含めます。警告だけなら終了コード0です。

同じ正規化後の名前はconfusable警告ではなく、既存の重複または予約名衝突エラーです。

## 8. Unicode版の更新

Unicode基準版の更新は、識別子の有効性、クラスタ境界、診断列、警告結果を変え得るため、BSB言語バージョンの明示的な仕様変更として扱います。JDK更新だけを理由に自動変更しません。

処理系は内部定数として `16.0.0` を保持し、適合テストでUnicode 16.0のデータ版と一致することを確認します。
