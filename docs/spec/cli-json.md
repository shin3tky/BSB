# CLI JSON診断

> 本文は現行実装の規範契約です。

この文書は、IO機能グループ完了後続機能グループ横断CLI拡張として、機械可読な静的検査結果を返す
`bsb check --json <ソース.bsb>`を定めます。CORE〜IO機能グループの言語仕様、人間向けCLI出力、終了コードを
変更しません。

## 1. 範囲

次の2形式を受理します。

```text
bsb check <ソース.bsb>
bsb check --json <ソース.bsb>
```

`--json`は`check`の直後にだけ指定できます。`--json check ...`、
`check <ソース.bsb> --json`、`run --json`、`format --json`はJSONモードではなく、従来の
人間向け使用法エラー2です。`run <ソース.bsb> -- --json`の`--json`は引き続きBSBプログラムの
起動引数です。

先頭2引数が非nullの`check`、`--json`である時点でJSONモードを認識します。その後のソース不足、
null、空パス、オプションに見えるパス、余分な引数、OSで表現できないパスはJSONの使用法問題として
返します。公開プロセス引数にはnullを渡せませんが、埋込みAPIも同じ規則を使います。

初回のJSONモードは静的検査だけを対象とします。公開`explain --json`、`ast --json`、`run`の
構造化結果、内部の束縛TSVと21・22列トレースの外部安定化、BSB言語の第一級`JSON`型は範囲外です。

## 2. 出力チャネルと終了コード

JSONモードを認識した呼出しは、stdout自体の書込み・flush失敗を除く全終了経路で、stdoutへJSON文書を
ちょうど1個出力します。JSON文書はBOMなしUTF-8、コンパクトな1行、末尾LFちょうど1個です。JSONを
正常に出力できた場合、stderrは空です。人間向け診断をstderrへ重複出力しません。

終了コードは既存CLIと同じです。

| 終了コード | JSONモードでの意味 |
|---:|---|
| 0 | 静的検査成功。警告だけの場合を含む |
| 2 | JSONモード認識後の引数・パス使用法エラー |
| 3 | ソース読取りなどのI/Oエラー |
| 8 | 名前、型、スタック効果、IR生成前検査のエラー |
| 9 | UTF-8、字句、構文エラー |
| 70 | 処理系内部エラー、またはJSON候補が出力上限を超過 |

`success`は`exitCode`が0のときだけtrueです。警告を含んでいてもtrueです。`check --json`は
プログラムを実行しないため、終了コード10とプログラム指定終了コードを返しません。

JSON文書のシリアライズと上限検査を完了するまでstdoutへ書き始めません。stdoutへの物理書込みが
始まった後のI/O失敗は巻き戻せず、部分JSONが残る可能性があります。この場合だけ、可能ならstderrへ
従来の人間向けI/Oエラーを1件出し、終了コード3を返します。stderrへの報告にも失敗した場合も終了コード3です。

## 3. JSON文書と版管理

最上位は次のメンバーを規定順で持つオブジェクトです。

| 順 | メンバー | JSON型 | 必須 | 意味 |
|---:|---|---|---|---|
| 1 | `schemaVersion` | number | はい | 初版は整数`1` |
| 2 | `command` | string | はい | 常に`check` |
| 3 | `source` | string | 条件つき | 構文上有効なソース引数を特定できた場合の利用者指定文字列 |
| 4 | `success` | boolean | はい | `exitCode == 0` |
| 5 | `exitCode` | number | はい | 第2節の10進整数 |
| 6 | `diagnostics` | array | はい | 規範順の診断。空でも省略しない |
| 7 | `problem` | object | 条件つき | CLI問題がある場合だけ存在 |

`source`は利用者が指定した文字列を保持し、暗黙に絶対パスやURIへ変換しません。空・null・
オプションに見える値、OSでパスに変換できない値は有効なソース引数ではないため省略します。
有効なソース引数を確定した通常結果では必須です。第7節の固定された`internal`・`outputLimit`
フォールバックでは、候補データを再利用せず確実に小さく保つため省略します。

`problem`が存在する場合、`diagnostics`は空です。言語パイプラインが診断を返した場合は`problem`を
省略します。言語エラーをCLI問題へ変換せず、CLI問題を架空の言語診断へ変換しません。

`problem`は次のメンバーを規定順で持ちます。

| 順 | メンバー | JSON型 | 意味 |
|---:|---|---|---|
| 1 | `kind` | string | `usage`、`io`、`internal`、`outputLimit`のいずれか |
| 2 | `message` | string | 秘匿済みの日本語説明 |

利用者が分岐に使う値は`schemaVersion`、`exitCode`、`problem.kind`、診断コードです。`message`は
人間向けであり、文面の完全一致を機械的な分岐条件にしません。I/Oと内部問題には、ホスト例外の型、
本文、スタックトレースを含めません。

### 3.1 互換性

- 読取側は未知の`schemaVersion`を、この版として推測せず拒否する
- 読取側は版1オブジェクトの未知メンバーを無視する
- 必須メンバーの削除・改名・型変更・意味変更、既存値の意味変更では版を増やす
- 省略可能なメンバーの追加、新しい診断コード・処理段階・`problem.kind`の追加では版を増やさない
- 版1へメンバーを追加する場合は、そのオブジェクトの既存メンバーより後へ追加する
- 診断・問題の日本語`message`変更だけでは版を増やさない
- キー順はJSONの意味ではないが、BSBが生成するバイト列の決定性のため規定順を維持する
- 読取側は未知の診断コード、処理段階、問題種別を文字列として保持できるものとする

版1を選ぶCLIオプションは設けません。

## 4. 診断オブジェクト

各診断は次のメンバーを規定順で持ちます。

| 順 | メンバー | JSON型 | 必須 | 意味 |
|---:|---|---|---|---|
| 1 | `code` | string | はい | `E_...`または`W_...`の診断コード |
| 2 | `severity` | string | はい | `error`または`warning` |
| 3 | `stage` | string | はい | 第4.1節の処理段階 |
| 4 | `message` | string | はい | 共通メッセージカタログから生成した日本語本文 |
| 5 | `sourcePath` | string | はい | 診断が保持するソース識別パス |
| 6 | `span`、`point`、`offset` | object | はい | 位置表現をちょうど1個 |
| 7 | `fields` | object | はい | 診断固有の文字列フィールド。空でも省略しない |
| 8 | `expected` | string | いいえ | 構造化診断の期待値 |
| 9 | `actual` | string | いいえ | 構造化診断の実値 |
| 10 | `relatedLocations` | array | はい | 関連位置。空でも省略しない |
| 11 | `fixes` | array | はい | 人間向け修正説明。空でも省略しない |
| 12 | `resourceLimit` | object | いいえ | 資源名、上限、観測値 |

診断配列は処理段階、`utf8Start`または`utf8Offset`、診断コードの順で並べます。これは既存の
`Diagnostic.ORDERING`と同じです。レンダラーは入力リストを変更しません。

`fields`のキーと値、`expected`、`actual`、`fixes`は文字列です。カンマや括弧を解析してJSON配列や
数値へ推測変換しません。`fields`のキーはUnicodeスカラー値列の辞書順で出力します。現行キーは
ASCIIなので、ASCII辞書順と一致します。固定メンバーとの衝突を避けるため、診断固有フィールドを
診断オブジェクト直下へ平坦化しません。

`resourceLimit`は`name`、`limit`、`observed`の順に3個の文字列メンバーを持ち、3個すべてがある場合だけ
存在します。内部診断が一部だけを持つ状態は契約違反であり、黙って欠損JSONを作らず内部問題70にします。

`relatedLocations`と`fixes`は内部診断の順を維持します。省略可能メンバーは不在時に省略し、`null`を
出力しません。

### 4.1 安定した識別値

重要度は次の2値です。

| 内部概念 | JSON値 |
|---|---|
| エラー | `error` |
| 警告 | `warning` |

処理段階は次の値です。Java列挙型名や宣言順をそのまま公開契約にしません。

| 処理段階 | JSON値 |
|---|---|
| UTF-8読取り | `utf8` |
| 字句解析 | `lexical` |
| 構文解析 | `syntax` |
| 名前解決 | `name` |
| 型・スタック効果検査 | `typeAndStack` |
| IR生成 | `ir` |
| 実行時 | `runtime` |

`check --json`では通常`runtime`を生成しませんが、公開診断表現の対応を完全にするため値を予約します。

## 5. ソース位置

行と列は1始まりです。列は正規化前の元ソースをUnicode 16.0の拡張書記素クラスタに分けて数えます。
タブ幅は4で、タブ後は列1、5、9、13、…の次の位置へ進みます。CR、LF、CRLFはそれぞれ1改行で、
CRLFは行を1回だけ進めます。表示セル幅、正規化後の文字列、JVMのUTF-16位置を使いません。

UTF-8位置はBOMを含む元ソースファイルの先頭を0とします。ファイル先頭のBOMを受理しても、最初の
実トークンのUTF-8位置は3です。

### 5.1 span

元ソースに1バイト以上の対象がある診断は`span`を使います。

```json
{"span":{"start":{"line":2,"column":1},"endInclusive":{"line":2,"column":2},"utf8Start":3,"utf8EndExclusive":13}}
```

- `start`と`endInclusive`の行・列は両端を含む
- `utf8Start`は開始バイトを含み、`utf8EndExclusive`は終了バイトを含まない
- `endInclusive`は`utf8EndExclusive`直前のバイトを含む拡張書記素クラスタの開始位置
- 対象が改行クラスタを含んで終わる場合、その改行クラスタが始まる直前行の位置を`endInclusive`とする
- 対象が書記素クラスタの一部だけでも、行・列はそのクラスタの位置、UTF-8範囲は正確なバイト範囲とする

`endInclusive`は内部の終端排他的`SourceSpan.end`から列を単純に1減らして作りません。解析に使った
同一の不変ソーススナップショットと同じUnicode 16.0書記素境界から求めます。ソースファイルを解析後に
読み直す方法は、競合する変更によって位置と診断が食い違うため適合しません。

### 5.2 point

欠落トークン、挿入位置、EOF、内部の開始・終了が等しいゼロ幅`SourceSpan`、単独の`SourcePosition`は
`point`を使います。

```json
{"point":{"line":3,"column":1,"utf8Offset":14}}
```

`line`と`column`は1始まり、`utf8Offset`は0始まりです。ゼロ幅範囲を空の`span`にしません。

### 5.3 offset

ソース全体のサイズ超過やデコード前の位置など、行・列を確定できない`FileOffset`は`offset`を使います。

```json
{"offset":{"utf8Offset":33554432}}
```

行・列を推測して追加しません。

### 5.4 関連位置

ソース内の関連位置は`sourcePath`、`point`、`description`の順に持ちます。

```json
{"sourcePath":"input.bsb","point":{"line":1,"column":1,"utf8Offset":0},"description":"先の定義"}
```

組み込み辞書などソース外の関連情報は`description`だけを持ちます。存在しないパスや位置を作りません。

```json
{"description":"組み込み辞書の定義"}
```

### 5.5 位置境界例

BOMなしUTF-8ソース`A\r\nが𠮷\n`を例にします。`が`はU+304B U+3099からなる1クラスタです。

| 対象または位置 | 行 | 列 | UTF-8範囲または位置 |
|---|---:|---:|---|
| `A` | 1 | 1 | `[0,1)` |
| CRLFクラスタ | 1 | 2 | `[1,3)` |
| `が` | 2 | 1 | `[3,9)` |
| `𠮷` | 2 | 2 | `[9,13)` |
| LFクラスタ | 2 | 3 | `[13,14)` |
| EOF point | 3 | 1 | `14` |

`が𠮷`全体のspanは`start=(2,1)`、`endInclusive=(2,2)`、UTF-8範囲`[3,13)`です。
CRLF全体のspanは`start=(1,2)`、`endInclusive=(1,2)`、UTF-8範囲`[1,3)`です。先頭へBOMを
付けた場合、行・列は同じで全UTF-8位置だけが3増えます。CRとLFを単独で使う場合も各1改行です。

`A\tB`では`A`が列1、タブが列2、`B`が次のタブ位置である列5です。結合文字列、異体字セレクタを
含むクラスタ、ZWJ絵文字、補助平面文字はそれぞれ1クラスタにつき1列進めます。

## 6. JSONエンコードと決定性

実出力は例示用の整形を行わず、メンバー間・コロン前後へ空白を入れないコンパクトな1行です。
数値はASCIIの10進整数で、不要な先頭0、正符号、小数点、指数を使いません。

文字列はUnicodeスカラー値列でなければなりません。CLI引数の孤立サロゲートは使用法問題、内部の
診断・メッセージに孤立サロゲートがある場合は内部問題です。文字列は次の規則でエンコードします。

- `"`と`\\`をそれぞれ`\"`と`\\\\`へエスケープする
- U+0008、U+0009、U+000A、U+000C、U+000Dは`\b`、`\t`、`\n`、`\f`、`\r`を使う
- その他のU+0000〜U+001Fは大文字16進4桁の`\u00XX`を使う
- `/`はエスケープしない
- その他のUnicodeスカラー値は、U+2028とU+2029を含めUTF-8で直接出力する
- HTML向けの追加エスケープを行わない

診断配列、関連位置、修正候補の順は第4節に従います。最上位、診断、位置、関連位置、資源上限、問題の
各オブジェクトはこの文書の表に示す順でメンバーを出力します。同じ入力スナップショット、メッセージ
カタログ、処理系版からはバイト単位で同じJSONを生成します。既定ロケール、既定タイムゾーン、OS改行、
ハッシュマップの未規定反復順へ依存しません。

## 7. JSON出力上限と失敗

末尾LFを含むJSON文書は67,108,864 UTF-8バイト以下とします。上限値を含めて受理し、候補が
67,108,865バイト以上なら候補を一切stdoutへ書かず破棄します。その後、`source`と元の診断を
含まず、空の`diagnostics`を持つ固定の小さい文書を出し、終了コード70を返します。

```json
{"schemaVersion":1,"command":"check","success":false,"exitCode":70,"diagnostics":[],"problem":{"kind":"outputLimit","message":"JSON診断の出力が上限を超えました。"}}
```

このフォールバック自体の出力に失敗した場合は第2節のstdout I/O失敗です。上限判定のために候補を
無制限に保持せず、上限を1バイト超えた時点で構築を止めます。上限超過は言語診断ではなくCLI表現の
失敗なので、元の診断終了コードを70で置き換えます。

シリアライズ、位置変換、メッセージ生成、内部契約の予期しない失敗は、ホスト情報を漏らさない次の
形に変換します。構築途中の候補は出力しません。

```json
{"schemaVersion":1,"command":"check","success":false,"exitCode":70,"diagnostics":[],"problem":{"kind":"internal","message":"処理系で予期しない問題が発生しました。"}}
```

## 8. 出力例

以下のコードブロックは、末尾LFを除いて実際のコンパクト出力です。

### 8.1 成功

```json
{"schemaVersion":1,"command":"check","source":"ok.bsb","success":true,"exitCode":0,"diagnostics":[]}
```

### 8.2 警告だけの成功

```json
{"schemaVersion":1,"command":"check","source":"warning.bsb","success":true,"exitCode":0,"diagnostics":[{"code":"W_PARTICLE_POSITION","severity":"warning","stage":"typeAndStack","message":"助詞の位置が不自然です。","sourcePath":"warning.bsb","span":{"start":{"line":2,"column":5},"endInclusive":{"line":2,"column":5},"utf8Start":30,"utf8EndExclusive":33},"fields":{},"expected":"本体先頭以外","actual":"を","relatedLocations":[],"fixes":["先頭のをを削除してください"]}]}
```

### 8.3 静的エラー8と関連位置

```json
{"schemaVersion":1,"command":"check","source":"stack.bsb","success":false,"exitCode":8,"diagnostics":[{"code":"E_STACK_UNDERFLOW","severity":"error","stage":"typeAndStack","message":"「足す」には2個の値が必要ですが、利用できる値は1個です。","sourcePath":"stack.bsb","span":{"start":{"line":2,"column":9},"endInclusive":{"line":2,"column":10},"utf8Start":42,"utf8EndExclusive":48},"fields":{"actualCount":"1","requiredCount":"2","word":"足す"},"expected":"[整数, 整数]","actual":"[整数]","relatedLocations":[{"sourcePath":"stack.bsb","point":{"line":1,"column":1,"utf8Offset":0},"description":"単語定義の開始"},{"description":"組み込み辞書の定義"}],"fixes":["整数をもう1つ置いてください"]}]}
```

### 8.4 構文系エラー9のpoint

```json
{"schemaVersion":1,"command":"check","source":"missing.bsb","success":false,"exitCode":9,"diagnostics":[{"code":"E_EXPECTED_STACK_SEPARATOR","severity":"error","stage":"syntax","message":"スタック効果には入力と出力を分ける -- が必要です。","sourcePath":"missing.bsb","point":{"line":3,"column":1,"utf8Offset":42},"fields":{},"expected":"-- と閉じ括弧","actual":"EOF","relatedLocations":[],"fixes":["スタック効果を閉じてください"]}]}
```

### 8.5 行・列を持たないUTF-8段階エラー9

```json
{"schemaVersion":1,"command":"check","source":"large.bsb","success":false,"exitCode":9,"diagnostics":[{"code":"E_SOURCE_SIZE_LIMIT","severity":"error","stage":"utf8","message":"ソースファイルが上限を超えています。","sourcePath":"large.bsb","offset":{"utf8Offset":33554432},"fields":{},"relatedLocations":[],"fixes":[],"resourceLimit":{"name":"sourceBytes","limit":"33554432","observed":"33554433"}}]}
```

### 8.6 使用法エラー2

```json
{"schemaVersion":1,"command":"check","success":false,"exitCode":2,"diagnostics":[],"problem":{"kind":"usage","message":"ソースファイルを1つ指定してください。"}}
```

### 8.7 I/Oエラー3

```json
{"schemaVersion":1,"command":"check","source":"missing.bsb","success":false,"exitCode":3,"diagnostics":[],"problem":{"kind":"io","message":"ソースファイルの読取りに失敗しました。"}}
```

内部エラー70と出力上限70は第7節の固定例に従います。

## 9. 適合条件

実装は少なくとも次を自動検証します。

- 成功、警告、終了コード2・3・8・9・70の文書が有効なJSONである
- schemaVersion、必須・省略メンバー、型、値、排他条件がこの文書と一致する
- span、point、offset、ソース内・ソース外関連位置、資源上限を網羅する
- CR、LF、CRLF、タブ、BOM、結合文字、補助平面文字、異体字セレクタ、ZWJ絵文字の位置を検証する
- 引用符、逆斜線、全制御文字、日本語、U+2028、U+2029のエンコードを検証する
- 複数診断、関連位置、修正候補、`fields`の順を検証する
- JSON候補の67,108,864/67,108,865バイト境界を実時間・巨大な重複確保なしで検証する
- 通常の`check`、`run`、`format`のstdout、stderr、終了コードが既存適合資源とバイト一致する
- fat JAR経路と埋込み`BsbCli`経路が同じJSONを返す

JSON適合資源は言語のJSON機能グループへ混在させず、`tests/conformance/cli-json`へ置きます。期待資源の不足、
重複、未使用をハーネス自身の失敗にします。
