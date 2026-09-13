# WST機能グループ: 作業領域・ファイル・CSV/TSVの診断と失敗境界

> 本文は現行実装の規範契約です。
>
> [名前付き作業領域、ファイル、CSV/TSV](workspace-tables.md)に従います。ファイル操作は
> [確定診断仕様](workspace-files-diagnostics.md)を優先します。後半は
> [CSV/TSV診断仕様](delimited-tables-diagnostics.md)を優先します。

## 1. 診断

診断コード名と必須fieldsを次に定めます。すべて重要度errorです。
`syntax`は終了9、`name`は終了8、実行時診断は終了10とする既存境界を維持します。

### 1.1 宣言と静的引数

| コード | 段階 | 条件 | 主なfields |
|---|---|---|---|
| `E_WORKSPACE_DECLARATION_VALUE` | syntax | 作業領域宣言が値を持つ | `name` |
| `E_WORKSPACE_DECLARATION_SCOPE` | syntax | トップレベル以外に宣言 | `name` |
| `E_WORKSPACE_DECLARATION_LIMIT` | name | 10,001個目 | `limit`、`observed` |
| `E_EXPECTED_WORKSPACE_ARGUMENT_START` | syntax | ファイル語直後に`<`がない | `word` |
| `E_EXPECTED_WORKSPACE_ARGUMENT` | syntax | 引数が作業領域名でない | `word` |
| `E_WORKSPACE_ARGUMENT_COUNT` | syntax | 引数が1個でない | `word`、`expectedCount`、`actualCount` |
| `E_EXPECTED_WORKSPACE_ARGUMENT_END` | syntax | `>`がない | `word`、`workspace` |
| `E_UNDECLARED_WORKSPACE` | name | 宣言のない作業領域名 | `word`、`workspace` |
| `E_WORKSPACE_REFERENCE_NOT_ALLOWED` | name | 作業領域名を通常値として使用 | `workspace` |

同名衝突、予約名、隠蔽は既存`E_DUPLICATE_NAME`、`E_RESERVED_NAME`、`E_NAME_SHADOWING`を使います。

### 1.2 ホスト境界と論理ファイル名

| コード | 条件 | 主なfields |
|---|---|---|
| `E_LOGICAL_FILE_NAME_INVALID` | 論理名のNFC・長さ・禁止文字規則に違反 | `word`、`reason` |
| `E_WORKSPACE_NOT_CONFIGURED` | 宣言名にホスト定義がない | `word`、`workspace`、`operation` |
| `E_WORKSPACE_ACCESS_DENIED` | ホストが作業領域の操作を拒否 | `word`、`workspace`、`operation` |
| `E_WORKSPACE_CONFIGURATION_INVALID` | ホスト定義が不正 | `word`、`workspace`、`reason` |
| `E_WORKSPACE_FILE_NOT_MAPPED` | 論理名が有限登録にない | `word`、`workspace`、`operation` |
| `E_WORKSPACE_FILE_ACCESS_DENIED` | 登録はあるが操作を許可しない | `word`、`workspace`、`operation` |
| `E_FILE_OPERATION_LIMIT` | 受理済み操作が4,096回を超える | `word`、`limitName`、`limit`、`observed` |
| `E_FILE_READ_TOTAL_LIMIT` | 読取累積128 MiB超 | `word`、`limitName`、`limit`、`used`、`requested` |
| `E_FILE_WRITE_TOTAL_LIMIT` | 書込試行累積128 MiB超 | `word`、`limitName`、`limit`、`used`、`requested` |
| `E_FILE_CANCELLED` | 読書き中に全実行取消 | `word`、`workspace`、`operation` |

論理名の`reason`は`notNfc`、`empty`、`tooLong`、`separator`、`controlCharacter`、`dotName`です。
作業領域設定不正の`reason`は、自由文やOSパスを含まない閉じたASCII値にします。

能力不在・能力自体の失敗は既存`E_CAPABILITY_UNAVAILABLE`・`E_CAPABILITY_FAILURE`です。型、スタック、
命令、能動時間、データスタック、バイト列構築上限も既存診断です。

### 1.3 区切りテキストと直列化

| コード | 条件 | 主なfields |
|---|---|---|
| `E_DELIMITED_TEXT_EMPTY_ROW` | 非空二次元配列の直列化入力に0セル行がある | `word`、`row` |
| `E_DELIMITED_TEXT_COLUMN_COUNT_MISMATCH` | 直列化入力の行列がragged | `word`、`row`、`expectedCount`、`actualCount` |
| `E_DELIMITED_TEXT_WORK_LIMIT` | 1実行の区切りテキスト作業が134,217,728を超える | `word`、`limitName`、`limit`、`used`、`requested` |
| `E_DELIMITED_TEXT_OUTPUT_LIMIT` | 直列化結果が16 MiB超 | `word`、`limit`、`observed` |

CSV/TSVの文法不正と解析時の列数不一致は、これらの診断ではなく`区切りテキスト解析失敗`です。
行数とセル数はNARRAY機能グループの`E_ARRAY_LENGTH_LIMIT`、`E_ARRAY_NESTED_ELEMENT_LIMIT`、
`E_ARRAY_CONSTRUCTION_LIMIT`を使います。文法失敗と資源超過が同じ走査位置で確定する場合は、文法失敗を
先に分類します。

## 2. 回復可能な失敗

回復可能な値は次だけです。

| 操作 | 失敗型 | 種類 |
|---|---|---|
| ファイル読取 | `ファイル読取失敗` | `notFound`、`notRegularFile`、`tooLarge`、`ioFailure` |
| ファイル書込 | `ファイル書込失敗` | `parentNotFound`、`targetNotRegularFile`、`tooLarge`、`atomicReplacementUnavailable`、`ioFailure` |
| CSV/TSV解析 | `区切りテキスト解析失敗` | 仕様本文第7.2節の5種類 |

OS例外の自由文、errno、Java例外型、OSパスで分類しません。JDK能力は既知の型付き状態だけを細分類し、
安全に確定しないものを`ioFailure`へ閉じます。アクセス拒否をI/O失敗へ偽装せず、ホスト認可診断とします。

## 3. 捕捉不能境界

次は結果値へ変換しません。

- 作業領域の未宣言、未設定、利用拒否、設定不正
- 論理ファイルの未登録、操作不許可、不正な論理ファイル名
- 能力不在・失敗、能力契約違反、全実行取消
- ファイル操作回数、累積読取・書込、区切りテキスト、文字列、配列、バイト列、命令、時間、スタック上限
- CSV/TSV直列化入力の列数不一致、0セル行
- null、名前・操作・不透明参照不一致、部分成功値等の処理系内部契約違反

物理ファイルの不在や通常ファイルでない状態は、登録・認可後に外部状態が変わり得るため回復可能です。
一方、論理名の未登録はホストが与えた境界外を指すため捕捉不能です。

## 4. 構文回復と静的検査順

静的引数は`<`、作業領域名、引数個数、`>`、名前解決の順です。構文失敗した呼出しへ型、能力要求、
未宣言名を派生させません。構造的に到達不能なファイル呼出しは既存`W_UNREACHABLE_CODE`だけを出し、
作業領域要求へ含めません。

作業領域宣言は構文、宣言上限、正規化、大域同名、予約名の順に検査します。上限到達後は派生する名前診断を
抑止します。

## 5. ファイル読取の判定順

1. 命令数と能動実行時間
2. 入力型、論理ファイル名、静的作業領域参照の内部不変条件
3. `workspace.resolve`と`file.read`能力の存在
4. ファイル操作回数を検査して1回分を予約
5. 作業領域を`read`で1回解決し、未設定・拒否・設定不正を分類
6. 論理ファイル登録とread認可
7. 読取残量と方針上限の小さい方を能力へ渡して1回読取
8. 能力契約、取消、回復可能失敗を分類
9. ファイル読取累積とバイト列構築予算を検査
10. 入力名を成功または失敗結果へ置換

未登録・拒否でも予約済み操作回数は戻さず、file能力は呼びません。能力が上限+1を観測した場合、方針上限が小さければ`tooLarge`、
全実行残量が小さいか同値なら`E_FILE_READ_TOTAL_LIMIT`を優先します。

## 6. ファイル書込の判定順

1. 命令数と能動実行時間
2. 入力型、論理ファイル名、バイト列、静的作業領域参照の内部不変条件
3. `workspace.resolve`と`file.write`能力の存在
4. ファイル操作回数と書込試行累積を一括予約
5. 作業領域を`write`で1回解決し、未設定・拒否・設定不正を分類
6. 論理ファイル登録とwrite認可、方針上限
7. 待機除外中にfile能力を1回呼び、必ず待機除外を終了
8. 能力契約、取消、回復可能失敗を分類
9. 成功整数と結果用スタック上限を検査
10. 入力2値を結果1値へ置換

書込試行累積は両能力存在後、resolver前に予約し、未設定・拒否・物理失敗でも戻しません。方針上限違反は
能力へ渡す前に`tooLarge`結果へできますが、試行累積は消費済みです。原子的公開後の結果用スタック上限失敗では
物理書込みを戻しませんが、入力スタックは診断原子性のため保持します。

## 7. CSV/TSV解析の判定順

1. 命令数、入力型、文字列不変条件
2. `入力UTF-8バイト数 + 入力Unicodeスカラー数`の区切りテキスト作業を一括予約
3. BOM、引用、区切り、改行を1回走査
4. 同じ位置の文法失敗を閉じた失敗値へ変換
5. NARRAY機能グループの外側長、各行長、論理葉上限を順に検査
6. 内側・外側配列の構築単位と結果用スタック上限を予約
7. 入力を成功二次元文字列配列または解析失敗結果へ置換

列数不一致は、異なることが確定した行の最初の余分なfield、または不足行のrecord終端位置を指します。
位置は元入力のBOMを含むUTF-8列を基準とします。

## 8. 二次元配列入力と直列化の判定順

行・セルの取出し、置換、追加はNARRAY機能グループの通常配列操作であり、WST機能グループに専用診断を追加しません。
CSV/TSV直列化は、入力が`配列<配列<文字列>>`であること、0セル行、先頭行との列数一致、候補UTF-8長、
16 MiB上限、`全セルの入力UTF-8バイト数 + 候補出力UTF-8バイト数`の区切りテキスト作業、文字列構築、
スタック置換の順に検査します。0セル行と列数不一致では、
外側の先頭から最初に見つかった行を報告します。

条件をすべて通るまで入力二次元配列と両出力を変更せず、一部文字列を返したりstdoutへ書いたりしません。

## 9. 非開示

次を診断のmessage、fields、expected、actual、fixes、relatedLocations、`dataStack`へ含めません。

- OSパス、実パス、URI、親ディレクトリ、一時ファイル名
- ファイル・セル・行・二次元配列の内容、接頭辞、hash
- 未登録または登録済みの論理ファイル名
- ホスト例外型・本文・stack trace、errno、権限・所有者・時刻

公開できるのはソース上の作業領域名、`read|write`、規範上限、安定reason、専用語で取り出した失敗情報です。
適合試験は各非開示面に異なる検出文字列を置き、人間診断、JSON診断、両出力、trace、能力イベント、
説明JSON、最終状態、CLI設定エラーで0件を要求します。
