# WST機能グループ: 作業領域・複数ファイル・厳密CSV/TSVの適合データ

> 本文は現行実装の規範契約です。

規範は[名前付き作業領域と複数ファイル](workspace-files.md)、
[厳密CSV/TSV](delimited-tables.md)と各診断仕様です。本番Java、TOML parser、JDK filesystem、
本番CSV/TSV parser/writer、外部CSV libraryから期待値を生成しません。

## 1. 構成と件数

WST機能グループは正常22件、失敗30件、資源18件の計70 IDです。前半は正常12件、失敗18件、資源10件の40 ID、
後半は正常10件、失敗12件、資源8件の30 IDです。物理データは
`tests/conformance/workspace-tables`に置き、`catalog.tsv`の順序と`manifest.tsv`の完全inventoryをPythonとJavaの
独立loaderで検査します。前半は`part=files`、後半は`part=delimited`とし、既存IDを変更しません。

- `WST-N001`〜`WST-N012`: 宣言、説明、複数file、読書き、失敗値、wrapper、trace、章末
- `WST-F001`〜`WST-F018`: 構文・名前19診断、論理名、解決・認可、能力、取消、CLI設定
- `WST-R001`〜`WST-R010`: 操作・累積・方針・構築・stack・時間・非開示・非transaction・heap
- `WST-N013`〜`WST-N022`: CSV/TSV解析、quote、BOM、直列化、往復、型、trace、章末
- `WST-F019`〜`WST-F030`: 5解析失敗、直列化5診断、型・stack境界
- `WST-R011`〜`WST-R018`: 入出力、区切り作業、行・外側・論理セル、構築、trace・原子性

## 2. 正常ID

| ID | 主対象 | 必須観測 |
|---|---|---|
| `WST-N001` | 宣言と前方参照 | 複数宣言、前方参照、保存slotなし、format冪等 |
| `WST-N002` | 静的説明 | workspace宣言、直接・推移`resolve/read/write`、決定的順序 |
| `WST-N003` | 空file読取 | 長さ0の成功バイト列、resolver/read各1回 |
| `WST-N004` | 複数file読取 | 2領域4登録、完全内容、ソース順、cacheなし |
| `WST-N005` | 読取失敗 | 4種類を失敗結果にし、種類を取出す |
| `WST-N006` | 新規file書込 | 全内容公開、成功整数、resolver/write各1回 |
| `WST-N007` | 既存file置換 | 公開前は旧内容、公開後は新内容全体 |
| `WST-N008` | 書込失敗 | 5種類、公開前失敗で旧target不変 |
| `WST-N009` | 失敗取出し | 読取4・書込5のASCII種類を完全一致で返す |
| `WST-N010` | 保存・分岐・wrapper | 2失敗型を保存・合流・`任意`・`結果`に使用 |
| `WST-N011` | trace | 失敗値とbytesを伏字にし、能力eventに秘密なし |
| `WST-N012` | 章末 | 複数入力を読み、別登録へ原子的に書く公開経路 |
| `WST-N013` | CSV基本解析 | 空文書、空field、CR/LF/CRLF、末尾改行 |
| `WST-N014` | TSV基本解析 | tab区切りとcomma通常データ、複数行 |
| `WST-N015` | quoted field | delimiter、引用符二重化、quoted CR/LF/CRLF |
| `WST-N016` | BOMとUnicode | 先頭BOM除去、内部BOM、日本語、補助平面 |
| `WST-N017` | CSV直列化 | 空表、空セル、最小quote、formula非変更、CRLF終端 |
| `WST-N018` | TSV直列化 | tabだけquote、commaは通常データ |
| `WST-N019` | 往復 | 先頭U+FEFF特別quote、改行・引用符を含む表の完全往復 |
| `WST-N020` | 型と失敗取出し | 8語のstack効果、5種類、4位置取出し |
| `WST-N021` | trace | 解析失敗を結果内でも伏字、予算差0 |
| `WST-N022` | 章末縦断 | file bytesからCSV/TSVを解析し、別登録へCSVを書込 |

## 3. 失敗ID

| ID | 主対象 | 必須観測 |
|---|---|---|
| `WST-F001` | 宣言形 | `E_WORKSPACE_DECLARATION_VALUE`、`E_WORKSPACE_DECLARATION_SCOPE` |
| `WST-F002` | 宣言上限・名前 | 10,001、既存重複・予約・隠蔽診断 |
| `WST-F003` | 静的引数 | start、argument、count、endの4診断と派生抑止 |
| `WST-F004` | 名前解決 | `E_UNDECLARED_WORKSPACE`、`E_WORKSPACE_REFERENCE_NOT_ALLOWED`、到達不能抑止 |
| `WST-F005` | 論理名 | 6 reason、255/256 UTF-8バイト、完全一致 |
| `WST-F006` | 未設定 | `E_WORKSPACE_NOT_CONFIGURED`、file能力0回 |
| `WST-F007` | 領域拒否 | `E_WORKSPACE_ACCESS_DENIED`、file能力0回 |
| `WST-F008` | 埋込み設定不正 | 4 closed reason、file能力0回 |
| `WST-F009` | 未登録 | `E_WORKSPACE_FILE_NOT_MAPPED`、論理名非開示 |
| `WST-F010` | 登録操作不許可 | `E_WORKSPACE_FILE_ACCESS_DENIED` |
| `WST-F011` | 能力境界 | resolver/read/writeの不在・例外を既存能力診断へ |
| `WST-F012` | 取消 | `E_FILE_CANCELLED`、失敗結果へ変換しない |
| `WST-F013` | stack・型 | 入力不足、順序、論理名・bytes型違い |
| `WST-F014` | 失敗型制限 | 表示・比較・配列要素禁止 |
| `WST-F015` | 読取契約違反 | null、未知、参照不一致、部分・上限超過成功は終了70 |
| `WST-F016` | 書込契約違反 | null、未知、参照不一致、成功長不一致は終了70 |
| `WST-F017` | direct CLI不正 | 引数不足、access、重複、件数、相互排他、終了78 |
| `WST-F018` | TOML不正 | envelope、schema、未知・重複・型・上限・path、終了78 |
| `WST-F019` | unexpected quote | CSV/TSV、BOM・補助平面後のbyte/scalar位置 |
| `WST-F020` | quote後文字 | `unexpectedCharacterAfterQuote`と位置 |
| `WST-F021` | 未終端quote | EOF、quoted改行後の物理行・列 |
| `WST-F022` | NUL | quoted/unquotedとも`nulCharacter`、内容非保持 |
| `WST-F023` | 解析列数 | extra/missing、LF/CRLF/EOFの規範位置 |
| `WST-F024` | 0セル行 | `E_DELIMITED_TEXT_EMPTY_ROW`、1始まりrow |
| `WST-F025` | ragged直列化 | `E_DELIMITED_TEXT_COLUMN_COUNT_MISMATCH`と件数 |
| `WST-F026` | NULセル | `E_DELIMITED_TEXT_CELL_INVALID`、row/column/reason |
| `WST-F027` | 出力上限 | `E_DELIMITED_TEXT_OUTPUT_LIMIT`、候補を部分構築しない |
| `WST-F028` | 作業上限 | `E_DELIMITED_TEXT_WORK_LIMIT`、予約失敗非加算 |
| `WST-F029` | parser型・stack | 入力不足、文字列以外、結果型不一致 |
| `WST-F030` | writer型・stack | 一次元・非文字列葉・入力不足を既存型診断へ |

## 4. 資源ID

| ID | 境界 |
|---|---|
| `WST-R001` | file操作4,096／4,097、拒否予約非加算 |
| `WST-R002` | 読取成功累積134,217,728／1超過、probe非加算 |
| `WST-R003` | 書込試行累積134,217,728／1超過、resolver前予約 |
| `WST-R004` | 方針1／67,108,864と1超過、累積との優先順 |
| `WST-R005` | 読取後のバイト列構築上限、入力stack原子性 |
| `WST-R006` | 公開後の結果stack 65,536／65,537、物理書込非rollback |
| `WST-R007` | I/O待機を能動30秒から除外し、全経路で区間終了 |
| `WST-R008` | 論理名・path・内容・temp・例外markerの全公開面非漏えい |
| `WST-R009` | 先行file成功後の後続失敗、先行内容をrollbackしない |
| `WST-R010` | 64 MiB file、128 MiB累積を最大ヒープ512 MiBで処理 |
| `WST-R011` | 文字列入力16,777,216 bytes丁度／1超過 |
| `WST-R012` | 区切り作業134,217,728丁度／1超過、失敗予約非加算 |
| `WST-R013` | 1行65,536セル／65,537セル |
| `WST-R014` | 外側65,536行／65,537行 |
| `WST-R015` | 論理セル1,000,000／1,000,001 |
| `WST-R016` | 配列構築累積1,000,000／1超過、解析失敗は非加算 |
| `WST-R017` | 直列化出力16,777,216 bytes丁度／1超過、割当前測定 |
| `WST-R018` | 通常/traceの全予算同値、診断時stack・保存・両出力原子性 |

## 5. 独立表と中央適合

`vectors/logical-names.tsv`はUTF-8 hexと閉じたreasonを固定し、OS名比較を使いません。
`expected/operations.tsv`はresolver/file状態、呼出し回数、結果、予算、target状態を固定し、実fileを開きません。
`expected/cli.tsv`はdirectとTOMLの設定解釈を固定し、TOML parserの診断本文をoracleにしません。
`resources.tsv`は巨大bytesを展開せず、閉じたrecipeと規範数値で表します。
`vectors/delimited-parse.tsv`は入力UTF-8 hexと表のセル別hex、失敗位置を固定します。
`expected/delimited-write.tsv`は表のセル別hexと規範出力hexを固定し、独立oracleで往復を検査します。
`delimited-resources.tsv`は巨大表や文字列を展開せず、閉じたrecipeと飽和計数を使います。

中央適合は次を満たします。

1. Python監査とJava loaderがBOM、CR、最終LF、header、列数、重複、ID外行、manifestを拒否する。
2. 全70 IDと全variantを中央カタログから過不足なく消費する。
3. WST機能グループの新24診断を`CONFORMANCE`、新12語を`FULL`にし、既存分類を低下させない。
4. 偽能力を言語適合oracleにし、JDK能力は一時directory統合に限定する。
5. 章末と利用者sampleの公開5コマンド、direct/TOML、通常/traceを各2回実行する。

期待と実装が食い違った場合は規範を再確認し、理由を記録せずgoldenを再生成しません。
