# TEXT機能グループ: 適合データと章末成果物

> 本文は現行実装の規範契約です。
>
> 現行契約: DTXT機能グループで旧指数拒否ケースTEXT-F023の入力を、指数対応後も不正な未完成指数`1e`へ更新しました。
> 現行の小数文字列指数適合契約は[DTXT機能グループ適合データ](decimal-text-conformance.md)を参照してください。

## 1. 適合データの場所

```text
tests/conformance/text-regex/
  cases.properties
  diagnostics.tsv
  resources.tsv
  messages.properties
  sources/
  canonical/
  generated/
  chapter/
```

厳密UTF-8、BOMなしLF、末尾LF、相対パス、propertiesとTSVのエスケープはNUM機能グループと同じです。成功する全formatケースは2回目の冪等性と入力非変更を要求します。

## 2. ケース体系

- 正常例: `TEXT-N001`〜`TEXT-N024`
- 失敗・警告例: `TEXT-F001`〜`TEXT-F030`
- 資源・内部境界: `TEXT-R001`〜`TEXT-R010`

CORE〜NUM機能グループの全1,942テストを同じ実行で維持します。

## 3. 正常例

| ID | 主対象 |
|---|---|
| TEXT-N001 | 結合濁点、異体字セレクタ、補助平面を含む書記素長とコードポイント数 |
| TEXT-N002 | 書記素とコードポイントの参照 |
| TEXT-N003 | 両単位の空・中間・全体半開区間 |
| TEXT-N004 | 両単位の検索位置、見つからない`-1`、空検索0 |
| TEXT-N005 | 連結とスカラー値辞書式比較 |
| TEXT-N006 | Unicode White_Spaceの前後除去 |
| TEXT-N007 | 書記素境界の全置換と元値の不変性 |
| TEXT-N008 | 空欄を保持する通常分割 |
| TEXT-N009 | 整数・小数から正規文字列への変換 |
| TEXT-N010 | 文字列から整数・小数への正確変換 |
| TEXT-N011 | 文字列配列の操作・反復・表示 |
| TEXT-N012 | raw正規表現リテラルと`ims`フラグ |
| TEXT-N013 | 正規表現の完全一致と検索 |
| TEXT-N014 | 最初の一致全体の抽出 |
| TEXT-N015 | ASCII名の名前付きキャプチャ抽出 |
| TEXT-N016 | 番号・名前・`$$`を使う全置換 |
| TEXT-N017 | 末尾空欄とゼロ幅を保持する正規表現分割 |
| TEXT-N018 | Unicode 16.0カテゴリ・ScriptとU+2EBF0 |
| TEXT-N019 | 正規表現の定数・変数・利用者単語効果 |
| TEXT-N020 | 正規表現フラグ順のcanonical |
| TEXT-N021 | 文字列の定数・変数・利用者単語効果 |
| TEXT-N022 | 文字列・正規表現・文字列配列の21列トレース |
| TEXT-N023 | 正規等価な文字列を暗黙に同一視しないこと |
| TEXT-N024 | 氏名整形と請求番号抽出の章末統合 |

## 4. 失敗・警告例

| ID | 段階 | 主対象 | 診断 |
|---|---|---|---|
| TEXT-F001 | 字句 | 正規表現終端不足 | `E_UNTERMINATED_REGEX_LITERAL` |
| TEXT-F002 | 字句 | rawパターン内の生改行 | `E_NEWLINE_IN_REGEX_LITERAL` |
| TEXT-F003 | 字句 | 不明なフラグ | `E_REGEX_FLAG` |
| TEXT-F004 | 字句 | 重複フラグ | `E_REGEX_FLAG` |
| TEXT-F005 | 静的 | 連結の型違い | `E_TYPE_MISMATCH` |
| TEXT-F006 | 静的 | コードポイント操作の型違い | `E_TYPE_MISMATCH` |
| TEXT-F007 | 静的 | 正規表現操作へ文字列パターン | `E_TYPE_MISMATCH` |
| TEXT-F008 | 静的 | `配列<正規表現>` | `E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED` |
| TEXT-F009 | 静的 | 後方参照 | `E_REGEX_UNSUPPORTED_CONSTRUCT` |
| TEXT-F010 | 静的 | 先読み | `E_REGEX_UNSUPPORTED_CONSTRUCT` |
| TEXT-F011 | 静的 | 不正な量指定 | `E_REGEX_SYNTAX` |
| TEXT-F012 | 静的 | 重複キャプチャ名 | `E_REGEX_SYNTAX` |
| TEXT-F013 | 静的 | 日本語キャプチャ名 | `E_REGEX_SYNTAX` |
| TEXT-F014 | 静的 | 正規表現を表示 | `E_TYPE_MISMATCH` |
| TEXT-F015 | 実行時 | 負の書記素位置 | `E_STRING_INDEX_OUT_OF_BOUNDS` |
| TEXT-F016 | 実行時 | 長さと同じ書記素位置 | `E_STRING_INDEX_OUT_OF_BOUNDS` |
| TEXT-F017 | 実行時 | 逆転した書記素範囲 | `E_STRING_RANGE_OUT_OF_BOUNDS` |
| TEXT-F018 | 実行時 | 範囲外コードポイント位置 | `E_CODE_POINT_INDEX_OUT_OF_BOUNDS` |
| TEXT-F019 | 実行時 | 範囲外コードポイント範囲 | `E_CODE_POINT_RANGE_OUT_OF_BOUNDS` |
| TEXT-F020 | 実行時 | 空検索文字列による通常置換 | `E_EMPTY_SEARCH_TEXT` |
| TEXT-F021 | 実行時 | 空区切りによる通常分割 | `E_EMPTY_DELIMITER` |
| TEXT-F022 | 実行時 | 不正な整数文字列 | `E_INTEGER_TEXT_INVALID` |
| TEXT-F023 | 実行時 | 不正な小数文字列、未完成指数`1e` | `E_DECIMAL_TEXT_INVALID` |
| TEXT-F024 | 実行時 | 数値文字列4,097桁 | `E_NUMERIC_TEXT_DIGIT_LIMIT` |
| TEXT-F025 | 実行時 | 抽出時の一致なし | `E_REGEX_NO_MATCH` |
| TEXT-F026 | 実行時 | 存在しない名前付き群 | `E_REGEX_GROUP_NOT_FOUND` |
| TEXT-F027 | 実行時 | 選択枝で不参加の名前付き群 | `E_REGEX_GROUP_UNMATCHED` |
| TEXT-F028 | 実行時 | 不正な置換テンプレート | `E_REGEX_REPLACEMENT_TEMPLATE` |
| TEXT-F029 | 実行時 | 失敗前stdout・スタックの原子性 | `E_REGEX_NO_MATCH` |
| TEXT-F030 | 警告 | 到達不能操作の派生診断抑止 | `W_UNREACHABLE_CODE` |

## 5. 資源・内部境界

| ID | 対象 | 受理側 | 拒否・省略側 |
|---|---|---|---|
| TEXT-R001 | 連結結果UTF-8長 | 16,777,216 | 16,777,217、`E_STRING_UTF8_LIMIT` |
| TEXT-R002 | 置換結果UTF-8長 | 16,777,216 | 16,777,217、`E_STRING_UTF8_LIMIT` |
| TEXT-R003 | 分割結果要素数 | 65,536 | 65,537、`E_ARRAY_LENGTH_LIMIT` |
| TEXT-R004 | rawパターンUTF-8長 | 65,536 | 65,537、`E_REGEX_PATTERN_LIMIT` |
| TEXT-R005 | コンパイル命令数 | 65,536 | 65,537、`E_REGEX_PROGRAM_LIMIT` |
| TEXT-R006 | キャプチャ数 | 64 | 65、`E_REGEX_CAPTURE_LIMIT` |
| TEXT-R007 | 1呼出し照合単位 | 1,000,000,000 | 1,000,000,001、`E_REGEX_WORK_LIMIT` |
| TEXT-R008 | 1実行累積照合単位 | 10,000,000,000 | 10,000,000,001、`E_REGEX_TOTAL_WORK_LIMIT` |
| TEXT-R009 | トレース値 | 32/16コードポイント・8要素 | 超過省略と非開示 |
| TEXT-R010 | 文字列・正規表現型つき合成IR | 全具体効果一致 | 型違いは内部エラー70 |

## 6. 診断TSVと生成器

`diagnostics.tsv`はNUM機能グループと同じ11列です。`resources.tsv`の全行は`generated/TEXT-R-*.properties`から決定的に再現します。巨大入力は対象外のソース、トークン、配列、命令、時間上限へ先に達しないよう、合成済み値・パターン・IR・実行文脈を対象コンポーネントへ直接渡せます。

生成器は時刻、乱数、既定ロケール、OS改行、マップの未規定反復順へ依存しません。

## 7. 章末プログラム

`chapter/07-chapter.bsb`は、氏名の前後の全角空白を除去して姓と名へ分割し、挨拶を連結します。さらに`請求番号: INV-2026-0042`から`year`と`number`を名前付きキャプチャで抽出します。

期待stdoutは次の4行で、末尾もLFです。

```text
山田 太郎さん、こんにちは
【「山田」、「太郎」】
2026
0042
```

## 8. トレース

TEXT-N022と章末例は、文字列・正規表現の`PushConst`、`つなぐ`、分割、正規表現Call、`配列<文字列>`を21列へ固定します。正規表現エンジン内部の状態遷移とキャプチャ作業領域はトレースしません。

通常実行とトレース実行で、終了コード、stdout、最終スタック、保存値、実行命令数、配列予算、正規表現累積照合単位が同一でなければなりません。
