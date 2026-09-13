# CLI JSON説明

> 本文は現行実装の規範契約です。

この文書は、ソースプログラムの単語、束縛、必要能力、副作用を機械可読に返す
`bsb explain --json <ソース.bsb>`を定めます。内部の束縛TSV、能力TSV、AST、IR、トレースを
外部互換形式へ昇格するものではありません。

## 1. 範囲とコマンド形式

初版は次のJSON専用形式だけを受理します。

```text
bsb explain --json <ソース.bsb>
```

`--json`は`explain`の直後だけに指定できます。`explain <ソース.bsb>`、
`--json explain ...`、`explain <ソース.bsb> --json`は人間向け使用法エラー2です。
先頭2引数が非nullの`explain`、`--json`である時点でJSONモードを認識し、その後のソース不足、
null、空パス、オプションに見えるパス、不正Unicode、OSで表現できないパス、余分な引数は
JSON使用法問題として返します。

説明対象は次のとおりです。

- 現行処理系の全組み込み語。使用の有無にかかわらず辞書の仕様順で返す
- ソースに宣言された全利用者定義語。`メイン`からの到達性にかかわらずソース順で返す
- 静的解析が作った全字句スコープ。親先行の規範順で返す
- 到達可能な全定数・変数宣言。束縛ID順、すなわちソース順で返す
- `メイン`から静的に到達する語、必要能力、副作用の要約

初版は単語名による絞込み、人間向け`explain`、公開AST、実行トレース、
実行時に実際に選ばれた分岐、能力の利用可否検査を含みません。

## 2. 出力チャネルと終了コード

JSONモードを認識した呼出しは、stdout自体のwrite・flush失敗を除く全終了経路で、stdoutへJSON文書を
ちょうど1個出力します。JSON文書はBOMなしUTF-8、コンパクトな1行、末尾LFちょうど1個です。
正常にJSONを出力した場合、stderrは空です。

| 終了コード | 意味 |
|---:|---|
| 0 | 静的検査成功。警告だけの場合を含む |
| 2 | JSONモード認識後の引数・パス使用法エラー |
| 3 | ソース読取りなどのI/Oエラー |
| 8 | 名前、型、スタック効果、IR生成前検査のエラー |
| 9 | UTF-8、字句、構文エラー |
| 70 | 処理系内部エラー、またはJSON候補が出力上限を超過 |

`success`は`exitCode`が0のときだけtrueです。警告だけなら説明全体と診断を返します。
静的検査にエラーがある場合、解析途中の単語、スコープ、束縛、能力、副作用を部分公開せず、
各説明配列を空にします。CLI問題と内部問題でも説明配列は空です。

JSON文書の構築と上限検査を完了するまでstdoutへ書き始めません。物理書込み開始後のI/O失敗では
部分JSONが残る可能性があり、この場合だけ可能ならstderrへ人間向けI/Oエラーを1件出し、終了コード3を
返します。

## 3. 最上位文書

最上位オブジェクトは次のメンバーを規定順で持ちます。

| 順 | メンバー | JSON型 | 必須 | 意味 |
|---:|---|---|---|---|
| 1 | `schemaVersion` | number | はい | `explain`初版は整数`1` |
| 2 | `command` | string | はい | 常に`explain` |
| 3 | `source` | string | 条件付き | 構文上有効な利用者指定ソース文字列 |
| 4 | `success` | boolean | はい | `exitCode == 0` |
| 5 | `exitCode` | number | はい | 第2節の整数 |
| 6 | `diagnostics` | array | はい | [CLI JSON診断](cli-json.md)版1と同じ診断オブジェクト |
| 7 | `summary` | object | 成功時 | `メイン`から到達する要求の要約 |
| 8 | `builtinWords` | array | はい | 成功時は全組み込み語、失敗時は空 |
| 9 | `userWords` | array | はい | 成功時は全利用者定義語、失敗時は空 |
| 10 | `scopes` | array | はい | 成功時は全字句スコープ、失敗時は空 |
| 11 | `bindings` | array | はい | 成功時は全束縛、失敗時は空 |
| 12 | `problem` | object | 条件付き | CLI、I/O、内部、出力上限の問題 |
| 13 | `parameterizedCapabilities` | object | CONN機能グループ以後の成功時 | 独立版を持つ名前付き資源要求。CONN機能グループ仕様に従う |

`source`は利用者が指定した文字列を保持し、絶対化やURI化をしません。無効なソース引数と固定された
`internal`・`outputLimit`フォールバックでは省略します。

`problem`は`kind`、`message`の順で持ちます。`kind`は`usage`、`io`、`internal`、
`outputLimit`のいずれかです。`problem`がある場合は`diagnostics`と全説明配列が空で、`summary`は
ありません。言語診断がある場合は`problem`を作りません。ホスト例外の型、本文、スタックトレースを
`message`へ含めません。

### 3.1 版管理

- `explain`の`schemaVersion`は`check`の版と独立して管理する
- 読取側は未知の版を版1として推測せず拒否する
- 読取側は版1オブジェクトの未知メンバーを無視する
- 必須メンバーの削除・改名・型変更・意味変更、既存識別値の意味変更では版を増やす
- 省略可能メンバー、新しい組み込み語、能力、副作用、スコープ種別の追加では版を増やさない
- 版1へメンバーを追加する場合は既存メンバーより後へ追加する
- 説明・例・診断メッセージの文面変更だけでは版を増やさない
- 読取側は未知の能力、副作用、型規則、機能グループ、スコープ種別を文字列として保持できるものとする

## 4. プログラム要約

`summary`は次のメンバーを規定順で持ちます。

| 順 | メンバー | JSON型 | 意味 |
|---:|---|---|---|
| 1 | `entryPoint` | string | 常に正規名`メイン` |
| 2 | `reachableUserWords` | array | `メイン`を含む到達利用者定義語名 |
| 3 | `reachableBuiltinWords` | array | 到達組み込み語名 |
| 4 | `capabilities` | array | 到達語が推移的に要求する能力 |
| 5 | `effects` | array | 到達語が推移的に持つ副作用 |

利用者定義語はソース順、組み込み語は辞書順、能力と副作用は第7節の規範順です。
到達性は名前解決済みの静的呼出し辺から求めます。実行時の条件値、ループ回数、配列長を予測せず、
静的に到達可能な分岐・ループ本体をすべて含めます。構造的に到達不能と診断された要素は含めません。

## 5. 組み込み語

各`builtinWords`要素は次のメンバーを規定順で持ちます。

| 順 | メンバー | JSON型 | 意味 |
|---:|---|---|---|
| 1 | `name` | string | 正規名 |
| 2 | `aliases` | array | 別名。初版の現行語では空 |
| 3 | `description` | string | 一文説明 |
| 4 | `stackEffect` | object | 第5.1節 |
| 5 | `typeRule` | string | 第5.2節の安定識別値 |
| 6 | `capabilities` | array | 呼出し時に必要な能力 |
| 7 | `effects` | array | 呼出し自体の副作用 |
| 8 | `featureGroup` | string | 組み込み語を分類する安定した機能グループ ID |
| 9 | `example` | string | 1個の使用例 |

全組み込み語を`BuiltinDictionary`の仕様順で返します。`BuiltinOperation`名やJava列挙型の宣言順は
公開しません。別名、能力、副作用はそれぞれの規範順を保持します。

### 5.1 スタック効果

`stackEffect`は次のメンバーを規定順で持ちます。

| 順 | メンバー | JSON型 | 意味 |
|---:|---|---|---|
| 1 | `inputs` | array | スタックの下側から上側への入力型・制約名 |
| 2 | `outputs` | array | スタックの下側から上側への出力型・制約名 |
| 3 | `returnsNormally` | boolean | 呼出元の次処理へ戻り得るか |

通常復帰しない語も`outputs`は辞書上の空配列とし、`⊥`という架空の型を入れません。
`returnsNormally=false`が非通常出口を表します。

### 5.2 型規則

| 公開値 | 意味 |
|---|---|
| `fixed` | 記載した具体型をそのまま使う |
| `sameTypePair` | 2入力が同じ許可型 |
| `displayable` | 1入力が表示可能型 |
| `sameNumericType` | 数値入力が同じ整数または小数型 |
| `independentNumericInputs` | 各数値入力が独立に整数または小数型 |
| `arrayLength` | `配列<T> -- 整数` |
| `arrayGet` | `配列<T> 整数 -- T` |
| `arraySlice` | `配列<T> 整数 整数 -- 配列<T>` |
| `arrayReplace` | `配列<T> 整数 T -- 配列<T>` |
| `arrayAppend` | `配列<T> T -- 配列<T>` |

## 6. 利用者定義語

各`userWords`要素は次のメンバーを規定順で持ちます。

| 順 | メンバー | JSON型 | 意味 |
|---:|---|---|---|
| 1 | `name` | string | NFC正規名 |
| 2 | `spelling` | string | 元ソース表記 |
| 3 | `declaration` | object | 宣言名の`span`位置 |
| 4 | `stackEffect` | object | 具体型列と通常復帰 |
| 5 | `reachableFromMain` | boolean | `メイン`から静的に到達するか |
| 6 | `directCapabilities` | array | 本体が直接呼ぶ組み込み語の能力 |
| 7 | `capabilities` | array | 利用者定義語呼出しも含む推移能力 |
| 8 | `directEffects` | array | 本体が直接呼ぶ組み込み語の副作用 |
| 9 | `effects` | array | 利用者定義語呼出しも含む推移副作用 |

`declaration`の値は`{"span":...}`です。位置オブジェクトは第9節に従います。
利用者定義語のスタック効果は検査済みの具体型を使います。型制約は利用者定義語へ許されないため
`typeRule`を持ちません。

直接集合は、その語の静的に到達可能な本体要素が直接呼ぶ組み込み語からだけ集めます。推移集合は
直接集合と、直接呼ぶ利用者定義語の推移集合の和集合です。自己再帰・相互再帰を含む呼出しグラフで
最小固定点を求めます。解析順や再帰深さによって結果を変えません。

## 7. 能力と副作用

能力は、語を実行するためにホストが提供する必要があるサービスです。副作用は、語が外部状態を
読み取り、変更し、待機し、またはプログラム制御を終了させる観測可能な作用です。版1の現行辞書では
能力を持つ語の能力集合と副作用集合は同じですが、別概念・別配列として公開し、将来も同一とは仮定しません。

現行値の規範順は次です。

```text
console.input
console.output
console.error
process.exit
process.arguments
program.identity
time.sleep
time.monotonic
time.wall
connection.resolve
```

集合は重複を除き、この順で配列化します。未知の内部値を推測で末尾へ出さず、処理系契約違反として
内部問題70にします。仕様へ新しい値を追加した版1処理系の読取側は、未知文字列を保持できます。

## 8. スコープと束縛

### 8.1 スコープ

各`scopes`要素は次のメンバーを規定順で持ちます。

| 順 | メンバー | JSON型 | 必須 | 意味 |
|---:|---|---|---|---|
| 1 | `id` | string | はい | 同一文書内の`S1`、`S2`、… |
| 2 | `kind` | string | はい | 下表のスコープ種別 |
| 3 | `parentId` | string | 条件付き | 大域以外の親スコープID |
| 4 | `ownerWord` | string | 条件付き | 大域以外の所有利用者定義語正規名 |
| 5 | `location` | object | はい | スコープ全体の`span`位置 |

スコープ種別は`global`、`wordBody`、`conditionalTrue`、`conditionalFalse`、
`countedLoopBody`、`conditionLoopCondition`、`conditionLoopBody`、`arrayLoopBody`です。
配列は親先行の解析規範順です。IDは同じソーススナップショットと処理系版の文書内でだけ安定し、
異なるソースや編集後も同じ意味を持つ永続IDではありません。

### 8.2 束縛

各`bindings`要素は次のメンバーを規定順で持ちます。

| 順 | メンバー | JSON型 | 必須 | 意味 |
|---:|---|---|---|---|
| 1 | `id` | string | はい | 同一文書内の`B1`、`B2`、… |
| 2 | `name` | string | はい | NFC正規名 |
| 3 | `spelling` | string | はい | 宣言の元ソース表記 |
| 4 | `kind` | string | はい | `constant`または`variable` |
| 5 | `storage` | string | はい | `global`または`local` |
| 6 | `scopeId` | string | はい | 宣言を直接含むスコープ |
| 7 | `type` | string | はい | 推論済み具体型 |
| 8 | `initializationOrder` | number | 条件付き | 大域束縛の1始まり初期化順 |
| 9 | `declaration` | object | はい | 宣言名の`span`位置 |
| 10 | `uses` | array | はい | この束縛のread/write利用 |

未使用束縛も空の`uses`を持って出力します。束縛IDの安定範囲はスコープIDと同じです。

各`uses`要素は`spelling`、`kind`、`location`の順で持ちます。`kind`は`read`または`write`、
`location`は利用名だけの`span`位置です。利用は元ソース位置順です。内部TSVの開始点だけの位置や
`local:<owner>`文字列を公開JSONへコピーしません。

## 9. 診断と元ソース位置

`diagnostics`のコード、重要度、処理段階、メッセージ、位置、フィールド、期待値、実値、関連位置、
修正候補、資源上限、規範順は[CLI JSON診断](cli-json.md)版1と同一です。

説明内の`declaration`と`location`は、同仕様の位置オブジェクトを1個だけ包みます。
現行の宣言・スコープ・利用は非空の元ソース範囲を持つため`span`を使います。

```json
{"span":{"start":{"line":2,"column":1},"endInclusive":{"line":2,"column":3},"utf8Start":10,"utf8EndExclusive":19}}
```

行・列は1始まりのUnicode 16.0拡張書記素クラスタ、タブ幅は4、CR・LF・CRLFは各1改行です。
UTF-8位置はBOMを含む元ファイル先頭から0始まりです。位置は解析に使った同一の不変`SourceText`
スナップショットから求め、ファイルを読み直しません。内部UTF-16インデックスを公開しません。

## 10. 決定性とJSONエンコード

- 最上位と全入れ子オブジェクトは、この文書の表のキー順で出力する
- 数値はASCIIの10進整数とし、不要な先頭0、正符号、小数点、指数を使わない
- 配列順は各節の規範順に従う
- `"`、`\\`、U+0000〜U+001FをJSON規則でエスケープする
- その他のUnicodeスカラー値はU+2028、U+2029を含めUTF-8で直接出力する
- 孤立サロゲートを出力せず、公開候補にあれば内部問題へ置き換える
- 既定ロケール、既定タイムゾーン、OS改行、ハッシュ反復順へ依存しない

同じソーススナップショット、組み込み辞書、メッセージカタログ、処理系版からはバイト単位で同じ
JSON文書を生成します。

## 11. 出力上限と固定フォールバック

末尾LFを含む文書は67,108,864 UTF-8バイト以下です。候補を完成する前にstdoutへ書きません。
上限を超えた場合は元候補を破棄し、次の固定文書と終了コード70を返します。

```json
{"schemaVersion":1,"command":"explain","success":false,"exitCode":70,"diagnostics":[],"builtinWords":[],"userWords":[],"scopes":[],"bindings":[],"problem":{"kind":"outputLimit","message":"JSON説明の出力が上限を超えました。"}}
```

公開DTOの不変条件違反、未知の内部識別値、JSON文字列の孤立サロゲート、マッピング・レンダリング中の
予期しない失敗では次の固定文書を返します。

```json
{"schemaVersion":1,"command":"explain","success":false,"exitCode":70,"diagnostics":[],"builtinWords":[],"userWords":[],"scopes":[],"bindings":[],"problem":{"kind":"internal","message":"処理系で予期しない問題が発生しました。"}}
```

両例の実出力は末尾LFを1個持ちます。固定文書自体が上限内であることを起動時不変条件とします。

## 12. 完全な最小例

次のソースを例にします。

```text
挨拶するとは （文字列 --）
    一行表示する
こと。

メインとは （--）
    「こんにちは」を 挨拶する
こと。
```

説明文書は全112組み込み語を`builtinWords`へ持つため、ここでは該当部分を`...`で省略した説明用例を
示します。`...`は実JSONへ出力しません。

```json
{
  "schemaVersion": 1,
  "command": "explain",
  "source": "hello.bsb",
  "success": true,
  "exitCode": 0,
  "diagnostics": [],
  "summary": {
    "entryPoint": "メイン",
    "reachableUserWords": ["挨拶する", "メイン"],
    "reachableBuiltinWords": ["一行表示する"],
    "capabilities": ["console.output"],
    "effects": ["console.output"]
  },
  "builtinWords": ["..."],
  "userWords": [
    {
      "name": "挨拶する",
      "spelling": "挨拶する",
      "declaration": {"span": {"...": "..."}},
      "stackEffect": {"inputs": ["文字列"], "outputs": [], "returnsNormally": true},
      "reachableFromMain": true,
      "directCapabilities": ["console.output"],
      "capabilities": ["console.output"],
      "directEffects": ["console.output"],
      "effects": ["console.output"]
    },
    {
      "name": "メイン",
      "spelling": "メイン",
      "declaration": {"span": {"...": "..."}},
      "stackEffect": {"inputs": [], "outputs": [], "returnsNormally": true},
      "reachableFromMain": true,
      "directCapabilities": [],
      "capabilities": ["console.output"],
      "directEffects": [],
      "effects": ["console.output"]
    }
  ],
  "scopes": ["..."],
  "bindings": []
}
```

この整形例は可読性のため空白と改行を含みます。実出力は第10節のコンパクトな1行です。

### 12.1 失敗経路の最上位例

ソース不足は次です。

```json
{"schemaVersion":1,"command":"explain","success":false,"exitCode":2,"diagnostics":[],"builtinWords":[],"userWords":[],"scopes":[],"bindings":[],"problem":{"kind":"usage","message":"ソースファイルを1つ指定してください。"}}
```

有効な利用者指定パス`missing.bsb`の読取り失敗は次です。

```json
{"schemaVersion":1,"command":"explain","source":"missing.bsb","success":false,"exitCode":3,"diagnostics":[],"builtinWords":[],"userWords":[],"scopes":[],"bindings":[],"problem":{"kind":"io","message":"ソースファイルの読取りに失敗しました。"}}
```

言語診断では`problem`を持たず、診断配列だけを埋めます。次は先頭で不正UTF-8を検出した例です。
診断本文は共通メッセージカタログに従います。

```json
{"schemaVersion":1,"command":"explain","source":"invalid.bsb","success":false,"exitCode":9,"diagnostics":[{"code":"E_INVALID_UTF8","severity":"error","stage":"utf8","message":"UTF-8として解釈できないバイト列があります。","sourcePath":"invalid.bsb","point":{"line":1,"column":1,"utf8Offset":0},"fields":{},"expected":"UTF-8","actual":"C3 28","relatedLocations":[],"fixes":["UTF-8で保存し直してください"]}],"builtinWords":[],"userWords":[],"scopes":[],"bindings":[]}
```

警告だけの場合は第12節の成功文書と同じ説明メンバーをすべて持ち、`success=true`、`exitCode=0`のまま
`diagnostics`へ警告を規範順で入れます。内部・出力上限の完全な固定文書は第11節に示します。

## 13. 非目標と分離境界

- 内部`BindingUseReport`の11列TSVとIO機能グループ能力TSVは既存適合形式のまま維持する
- 内部AST、IR命令、Javaクラス名、列挙型名を説明JSONへ出さない
- `check --json`の版1文書へ説明メンバーを追加しない
- `explain`の版更新によって`check`の版更新を要求しない
- 第一級`JSON`型の値モデルや将来のHTTP応答モデルとして説明DTOを再利用しない
- `run`の実出力、実際に選ばれた分岐、能力イベント、資格情報、入力値、保存値を含めない
- CONN機能グループの`parameterizedCapabilities`は[論理接続仕様](logical-connections.md)第6節に従い、
  外側の`schemaVersion`から独立した版、宣言、利用位置、直接・推移要求を持つ
