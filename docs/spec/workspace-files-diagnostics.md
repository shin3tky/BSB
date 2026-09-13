# WST-FILE機能グループ: 名前付き作業領域とファイルの診断

> 本文は現行実装の規範契約です。
>
> [名前付き作業領域と複数ファイル](workspace-files.md)の診断、判定順、原子性を定めます。
> 統合草案[WST機能グループの診断](workspace-tables-diagnostics.md)と異なる場合、前半については本仕様を優先します。

## 1. 新規診断

すべて重要度`error`です。`syntax`は終了9、`name`は終了8、実行時は終了10です。WST-FILE機能グループでは
次の19コードを追加します。

### 1.1 宣言と静的引数

| コード | 段階 | 条件 | 必須fields |
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

同名衝突、予約名、隠蔽は既存`E_DUPLICATE_NAME`、`E_RESERVED_NAME`、`E_NAME_SHADOWING`です。

### 1.2 実行境界

| コード | 条件 | 必須fields |
|---|---|---|
| `E_LOGICAL_FILE_NAME_INVALID` | 論理名のNFC・長さ・禁止文字規則に違反 | `word`、`reason` |
| `E_WORKSPACE_NOT_CONFIGURED` | 宣言名にホスト定義がない | `word`、`workspace`、`operation` |
| `E_WORKSPACE_ACCESS_DENIED` | ホストが作業領域の操作を拒否 | `word`、`workspace`、`operation` |
| `E_WORKSPACE_CONFIGURATION_INVALID` | 埋込みホスト定義が能力契約を満たさない | `word`、`workspace`、`reason` |
| `E_WORKSPACE_FILE_NOT_MAPPED` | 論理名が有限登録にない | `word`、`workspace`、`operation` |
| `E_WORKSPACE_FILE_ACCESS_DENIED` | 登録はあるが操作を許可しない | `word`、`workspace`、`operation` |
| `E_FILE_OPERATION_LIMIT` | 受理済み操作が4,096回を超える | `word`、`limitName`、`limit`、`observed` |
| `E_FILE_READ_TOTAL_LIMIT` | 読取成功候補の累積が128 MiBを超える | `word`、`limitName`、`limit`、`used`、`requested` |
| `E_FILE_WRITE_TOTAL_LIMIT` | 書込試行累積が128 MiBを超える | `word`、`limitName`、`limit`、`used`、`requested` |
| `E_FILE_CANCELLED` | 読書き中に全実行取消 | `word`、`workspace`、`operation` |

`operation`は`read|write`です。`limitName`はそれぞれ`fileOperations`、`fileReadBytes`、
`fileWriteAttemptBytes`です。`reason`は自由文にしません。

`E_LOGICAL_FILE_NAME_INVALID.reason`は次の優先順です。

```text
notNfc, empty, tooLong, separator, controlCharacter, dotName
```

`E_WORKSPACE_CONFIGURATION_INVALID.reason`は次の閉じた値です。

```text
invalidReference, invalidPolicy, inconsistentWorkspace, inconsistentMapping
```

上限診断の数値は決定的にします。宣言上限と操作上限の`observed`はそれぞれ10,001と4,097です。
読取累積超過の`used`は直前までの成功累積、`requested`は超過を確定した最小値である`残量 + 1`です。
書込累積超過の`used`は直前までの受理済み試行累積、`requested`は今回の本文全バイト数です。拒否された
予約によって`used`を変更しません。

標準CLIの設定不正はBSB実行時診断にせず、実行前の`作業領域設定エラー`と終了78にします。能力不在・
能力契約違反は既存`E_CAPABILITY_UNAVAILABLE`・`E_CAPABILITY_FAILURE`です。型、stack、命令、能動時間、
データstack、バイト列構築上限も既存診断を使います。

## 2. 回復可能な失敗

結果値へ変換する外部状態は次だけです。

| 操作 | 失敗型 | 種類 |
|---|---|---|
| 読取 | `ファイル読取失敗` | `notFound`、`notRegularFile`、`tooLarge`、`ioFailure` |
| 書込 | `ファイル書込失敗` | `parentNotFound`、`targetNotRegularFile`、`tooLarge`、`atomicReplacementUnavailable`、`ioFailure` |

JDK能力は既知の型付き状態だけを細分類し、安全に確定しない例外を`ioFailure`へ閉じます。OS例外本文、errno、
pathで分類しません。登録のread/write不許可はホスト認可診断にし、認可済み登録に対するOSの
`AccessDeniedException`等は安全に再分類せず`ioFailure`にします。

## 3. 捕捉不能境界

次は失敗値へ変換しません。

- 作業領域の未宣言、未設定、利用拒否、設定不正
- 論理ファイルの未登録、操作不許可、不正な論理名
- 能力不在・失敗、能力契約違反、全実行取消
- 操作回数、累積読取・書込、バイト列構築、命令、時間、stack上限
- null、未知状態、名前・操作・不透明参照不一致、部分成功等の内部契約違反

物理ファイルの不在や非通常ファイルは、登録・認可後にも外部状態が変化し得るため回復可能です。未登録名は
ホストが与えた境界外なので捕捉不能です。

## 4. 構文回復と静的検査順

静的引数は`<`、作業領域名、引数個数、`>`、名前解決の順です。構文失敗した呼出しへ型、能力要求、
未宣言診断を派生させません。構造的に到達不能なファイル呼出しは既存`W_UNREACHABLE_CODE`だけを出し、
作業領域要求へ含めません。

作業領域宣言は構文、宣言上限、正規化、大域同名、予約名の順に検査します。上限到達後はその宣言から派生する
名前診断を抑止します。

## 5. 読取の判定順

1. 命令数と能動実行時間
2. 入力型、論理名規則、静的作業領域参照の内部不変条件
3. `workspace.resolve`と`file.read`能力の存在
4. 操作回数を検査して1回分を予約
5. 作業領域を`read`で1回解決し、未設定・拒否・設定不正を分類
6. 論理名登録とread認可
7. 方針上限と読取累積残量の小さい値で、file能力を1回だけbounded read
8. 能力契約、取消、回復可能失敗、上限probeを分類
9. 完全成功内容の読取累積とバイト列構築予算を原子的に予約
10. 入力名を成功または失敗結果へ置換

未登録・拒否でも予約済み操作回数は戻さず、file能力は呼びません。上限probeでは累積残量が方針上限以下なら
`E_FILE_READ_TOTAL_LIMIT`を優先し、それ以外は`tooLarge`です。probeで読んだ部分、失敗した一括予約は
読取累積へ加算しません。

## 6. 書込の判定順

1. 命令数と能動実行時間
2. 入力型、論理名規則、バイト列、静的作業領域参照の内部不変条件
3. `workspace.resolve`と`file.write`能力の存在
4. 操作回数1回と本文全量の書込試行累積を原子的に予約
5. 作業領域を`write`で1回解決し、未設定・拒否・設定不正を分類
6. 論理名登録、write認可、方針上限を分類
7. 待機除外中にfile能力を1回呼び、全経路で待機除外を終了
8. 能力契約、取消、回復可能失敗を分類
9. 成功整数と結果用stack上限を検査
10. 入力2値を結果1値へ置換

予約済み操作回数と書込試行累積は、resolver・認可・方針・物理失敗でも戻しません。方針超過は能力呼出し前に
`tooLarge`結果へできます。原子的公開後の結果用stack上限失敗では物理書込みを戻しませんが、入力stackは
診断原子性のため保持します。

## 7. 診断原子性と待機

捕捉不能診断では入力stack、保存値、stdout、stderrを変更しません。結果として返す回復可能失敗は通常成功
終了であり、入力を失敗結果へ置換します。能力委譲後の外部観測と、既に公開済みの単一ファイル書込みは
巻き戻しません。別ファイルの内容を変更しません。

ファイルI/O待機は既存のHTTPS待機と同じく能動時間から除外し、成功、閉じた失敗、取消、例外の全経路で
除外区間を終了します。命令、操作回数、累積バイト上限は待機中も省略しません。

## 8. 非開示

診断のmessage、fields、expected、actual、fixes、relatedLocations、`dataStack`へ次を含めません。

- OSパス、実path、親directory、一時ファイル名
- 登録済み・未登録の論理ファイル名
- 内容、部分内容、接頭辞、hash
- ホスト例外型・本文・stack trace、errno、metadata

公開できるのはソース上の作業領域名、`read|write`、規範上限、閉じたreason、失敗取出し語が返す種類です。
適合試験は各非開示面に異なる検出文字列を置き、人間診断、JSON診断、stdout、stderr、trace、能力event、
説明JSON、最終stack、CLI設定エラーで0件を要求します。
