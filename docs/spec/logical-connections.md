# CONN機能グループ: 論理接続とパラメータ付き能力

> 本文は現行実装の規範契約です。

## 1. 目的と範囲

CONN機能グループは、BSBソースが参照する論理接続名と、ホストが保持する接続定義を分離します。
接続名を通常の文字列値にせず、ソース上で宣言し、能力を使う呼出しへ静的引数として渡します。
これにより、実URL、資格情報、接続方針をBSB値へ入れずに、実行前の静的説明と実行時の認可を
同じ名前へ結び付けます。

この機能グループで追加する実行操作は、ホストへ接続定義の存在と利用可否を問い合わせる
`論理接続を確認する`だけです。DNS、ソケット、TLS、HTTP要求、資格情報の取得・適用は行いません。
確認成功は、後続のHTTP送信が成功することや、同じ定義が将来も維持されることを保証しません。

次はこの機能グループに含めません。

- 動的に計算した接続名、文字列から接続参照への変換
- 任意URL、任意オリジン、接続定義の列挙
- HTTP要求・応答、DNS、TLS、認証情報の取得・適用
- リダイレクト、再試行、レート制限の実行
- 接続参照を値、型、変数、配列、任意値、結果値として保持する機能
- BSBソースからホスト接続定義を作成・変更・削除する機能
- 汎用的な名前付き資源フレームワーク

## 2. ソース構文

### 2.1 論理接続宣言

論理接続はトップレベルで次のように宣言します。

```text
顧客管理APIは 論理接続。
```

文法への追加は次です。

```text
program                       := (wordDefinition | globalDeclaration
                                  | logicalConnectionDeclaration | trivia)* EOF
logicalConnectionDeclaration  := identifier immediately-followed-by "は"
                                  separator+ "論理接続" separator* "。"
```

宣言は初期値を持ちません。`論理接続`と`。`の間へコメント以外のトークンがある場合は
`E_LOGICAL_CONNECTION_DECLARATION_VALUE`です。宣言名と`は`の隣接、識別子の正規化、長さ、
confusable警告は値宣言と同じ規則です。

論理接続宣言はトップレベルだけに置けます。単語本体または制御ブロック内の宣言は
`E_LOGICAL_CONNECTION_DECLARATION_SCOPE`です。大域宣言のソース位置にかかわらず、全宣言を
本体検査前に収集するため、前方参照を許します。実行時の初期化順と保存スロットは持ちません。

1ソースの論理接続宣言は10,000個までです。10,001個目を登録する前に
`E_LOGICAL_CONNECTION_LIMIT`を1件出し、それ以後の接続宣言・参照から派生する名前診断を抑止します。
この上限は大域値束縛10,000個の既存上限とは別に数え、論理接続をbinding総数や保存領域へ加えません。

論理接続名は、利用者定義語、大域の定数・変数、他の論理接続と同じ大域名前空間を共有します。
同名は既存`E_DUPLICATE_NAME`、予約名との衝突は既存`E_RESERVED_NAME`です。局所宣言で論理接続名を
隠すことはできず、既存`E_NAME_SHADOWING`を使います。

### 2.2 静的な接続引数

接続確認は次の構文です。

```text
論理接続を確認する<顧客管理API>
```

```text
logicalConnectionCheck := "論理接続を確認する" "<" identifier ">"
```

`<`は正規語の直後に空白なしで置きます。`<`と名前、名前と`>`の間には通常の区切りを許しますが、
コメントは許しません。フォーマッタの正規形は空白を入れない
`論理接続を確認する<顧客管理API>`です。

引数は論理接続宣言へ静的に解決します。文字列、定数、変数、利用者定義語、型名は指定できません。
宣言がない名前は`E_UNDECLARED_LOGICAL_CONNECTION`です。0個、2個以上、入れ子の引数は受理しません。
結果型の型引数と同じ記号を使いますが、この位置の名前は型ではなく論理接続名前空間で解決します。

論理接続名を通常の本体名、値の初期値、代入元、配列要素、型位置へ書いても接続値にはなりません。
通常の本体名として参照した場合は`E_LOGICAL_CONNECTION_REFERENCE_NOT_ALLOWED`です。

### 2.3 正規語と効果

| 正規語 | 静的引数 | スタック効果 | 能力 | 副作用 |
|---|---|---|---|---|
| `論理接続を確認する` | 論理接続1個 | `--` | `connection.resolve` | `connection.resolve` |

別名はありません。`typeRule`は`fixed`、`returnsNormally`は`true`、`featureGroup`は`"CONN"`です。
この語はデータスタックを読み書きせず、成功時にも接続参照や設定値を積みません。

`論理接続`と`論理接続を確認する`を予約名へ追加します。宣言された個々の論理接続名は、宣言の
有効範囲における利用者名であり、言語全体の予約名には追加しません。

## 3. パラメータ付き能力

平坦な能力名は「どの種類のホストサービスが必要か」を表し、パラメータ付き能力要求は
「どの論理資源へ何を行い得るか」を表します。CONN機能グループでは次の1種類だけです。

```text
capability: connection.resolve
resourceKind: logicalConnection
resourceName: ソースで宣言された正規化後の名前
operations: [resolve]
```

呼出し1個は`connection.resolve`の平坦な能力と、対象名に対する`resolve`要求を1個持ちます。
同じ利用者定義語内の重複要求は名前ごとに統合し、操作を重複させません。利用者定義語呼出しを通る
推移要求は、既存の能力・副作用と同じ最小固定点で求めます。条件値や反復回数を予測せず、構造的に
到達可能な全呼出しを上限集合へ含めます。構造的に到達不能と診断された呼出しは含めません。

資源の規範順は、資源種別順、論理接続宣言のソース順、操作の仕様順です。CONN機能グループの資源種別は
`logicalConnection`、操作順は`resolve`だけです。後続版の読取側は未知の資源種別と操作を文字列として
保持できなければなりません。

## 4. ホスト側の接続定義

### 4.1 解決能力

実行環境は、任意で`connection.resolve`能力を1個提供できます。能力は次を入力に取ります。

- NFC正規化済みの論理接続名
- 安定ASCII操作名`resolve`

ソース本文、値スタック、保存値、呼出し元のローカル名、資格情報は渡しません。実行主体やテナントなどの
認可文脈はホストが能力の閉包として保持し、BSB値から指定させません。

能力の応答は、`RESOLVED`、`NOT_CONFIGURED`、`DENIED`、`INVALID`の閉じた4状態です。
`DENIED`と`INVALID`はホストの自由文を持てません。能力自体の例外または失敗は既存
`E_CAPABILITY_FAILURE`へ写し、例外型・本文・スタックトレースを公開しません。null、未知状態、
名前不一致など能力契約外の応答は利用者診断へ推測変換せず内部エラー70です。

### 4.2 `RESOLVED`が返す非秘密方針

`RESOLVED`は、次の不変な接続方針と、不透明な資格情報参照をホスト内部だけに返します。
CONN機能グループの語は妥当性を検査するだけで、どの項目もBSB値へ変換しません。

| 項目 | 規則 |
|---|---|
| 基底URI | ASCII直列化で1〜8,192バイトの絶対`https` URI。小文字化したDNSホストを持ち、userinfo、query、fragmentなし。pathは`/`または`/`終端 |
| 許可オリジン | 1〜32個の相異なる絶対HTTPS origin。各ASCII直列化は8,192バイト以下、合計262,144バイト以下。path、query、fragment、userinfoなし。基底URIのoriginを含む |
| 許可メソッド | `GET`、`HEAD`、`POST`、`PUT`、`PATCH`、`DELETE`の空でない部分集合 |
| 認証方式 | `none`、`basic`、`apiKey`、`oauth2`のいずれか |
| 資格情報参照 | `none`では不在。それ以外ではホスト内の不透明参照が必須。秘密値そのものは禁止 |
| 接続タイムアウト | 1〜30,000ミリ秒 |
| 応答タイムアウト | 1〜30,000ミリ秒 |
| 要求サイズ上限 | 1〜67,108,864バイト |
| 応答サイズ上限 | 1〜67,108,864バイト |
| リダイレクト方針 | CONN機能グループでは`deny`だけ |
| 再試行方針 | CONN機能グループでは`none`だけ |

URIのoriginはscheme、ASCII化・小文字化したhost、明示または既定portからなり、既定443は同一とします。
IPv4・IPv6リテラル、国際化ドメイン名のUnicode入力、相対URI、`http`、TLS設定の上書きは
CONN機能グループの定義では不正です。国際化ドメイン名を使うホストは、設定を渡す前にIDNAのASCII表記へ
変換します。

認証方式の列挙は接続方針の静的分類だけを固定します。Basic、APIキー、OAuth 2.0の資格情報形式、
挿入位置、トークン取得、更新はHTTPS機能グループで別途規範化します。CONN機能グループは資格情報参照を解決・呼出しせず、
存在確認も行いません。

不正理由は次の安定ASCII値のいずれかです。複数不正がある場合は表の順で最初の1個だけを報告します。

```text
BASE_URI_INVALID
ORIGIN_POLICY_INVALID
METHOD_POLICY_INVALID
AUTHENTICATION_POLICY_INVALID
CONNECT_TIMEOUT_INVALID
RESPONSE_TIMEOUT_INVALID
REQUEST_LIMIT_INVALID
RESPONSE_LIMIT_INVALID
REDIRECT_POLICY_INVALID
RETRY_POLICY_INVALID
```

ホストが`INVALID`を返した場合もこの理由だけを受理します。自由文、URI、資格情報参照は診断へ移しません。

## 5. 実行意味論と原子性

`論理接続を確認する<名前>`は次の順で実行します。

1. 命令数と能動実行時間の既存上限を確認する
2. `connection.resolve`能力の有無を確認する
3. 正規化済みの名前と`resolve`を能力へ1回だけ渡す
4. 応答状態または能力失敗を分類する
5. `RESOLVED`なら第4.2節の非秘密方針を順に検証する
6. 成功として次の命令へ進む

成功、未設定、拒否、設定不正のいずれでも、データスタック、保存値、stdout、プログラムstderrを
変更しません。能力呼出しの事実だけは能力イベントへ残ります。診断の物理stderr出力はプログラムstderrと
別です。診断後の再開、結果値への変換、別名へのフォールバックは行いません。

確認は1命令として既存命令予算を消費します。接続専用の言語ヒープ、文字列、配列、JSON、待機、
入出力予算は追加しません。ホスト定義の個数と構築費は埋込みホストの責任であり、BSB実行予算を
迂回してBSB値を作れません。

## 6. `explain --json`の公開項目

既存`explain --json`の`schemaVersion`は1を維持し、既存の平坦な`capabilities`と`effects`へ
`connection.resolve`を加算的に追加します。さらに、成功文書の既存メンバーより後へ
`parameterizedCapabilities`を追加します。静的検査失敗、CLI問題、内部問題ではこのメンバーを省略し、
途中の宣言や要求を公開しません。

`parameterizedCapabilities`は独立した版を持ちます。

```json
{
  "schemaVersion": 1,
  "declarations": [
    {
      "name": "顧客管理API",
      "spelling": "顧客管理API",
      "declaration": {"span": {}},
      "uses": [
        {
          "ownerWord": "メイン",
          "operation": "resolve",
          "reachableFromMain": true,
          "location": {"span": {}}
        }
      ]
    }
  ],
  "summaryRequirements": [
    {"kind": "logicalConnection", "name": "顧客管理API", "operations": ["resolve"]}
  ],
  "userWords": [
    {
      "name": "メイン",
      "directRequirements": [
        {"kind": "logicalConnection", "name": "顧客管理API", "operations": ["resolve"]}
      ],
      "requirements": [
        {"kind": "logicalConnection", "name": "顧客管理API", "operations": ["resolve"]}
      ]
    }
  ]
}
```

位置オブジェクトは既存`explain --json`のspan形式です。`declarations`は未使用宣言も含むソース順、
`uses`はその宣言を直接参照する構造的に到達可能な呼出しのソース順です。`userWords`は全利用者定義語を
ソース順で含み、`directRequirements`と`requirements`は第3節の順序です。
`summaryRequirements`は`メイン`から到達する推移要求だけを含みます。

この項目に基底URI、origin、認証方式、資格情報参照、タイムアウト、サイズ上限、解決成否を含めません。
環境を含む解決済み計画の公開CLIは、HTTP実行設定の入力方法と一緒に後続機能グループで定めます。

`parameterizedCapabilities.schemaVersion`は外側の版から独立します。必須メンバーの削除・改名・型変更、
既存識別値の意味変更ではこの内側の版を増やします。未知メンバー、未知資源種別、未知操作は無視または
文字列として保持できるものとします。

## 7. 非開示

資格情報の値、資格情報プロバイダー、認証ヘッダー候補、ホスト例外、接続定義オブジェクトの
`toString()`を、BSB値、結果値、診断のmessage・fields・expected・actual・relatedLocations・fixes、
通常トレース、能力イベント、`explain --json`へ出しません。

公開できるのは、ソースに存在する論理接続名、安定能力名、安定操作名、成功か失敗分類、設定不正の
安定理由だけです。既定traceの能力イベントは`connection.resolve`、論理接続名、
`resolved|notConfigured|denied|invalid|failure`だけを保持します。より厳しいホスト方針は論理接続名も
伏せられますが、実行結果、診断コード、能力呼出し回数を変更してはなりません。

適合試験では、URI、資格情報参照、秘密値、ホスト例外本文に互いに異なる検出用文字列を置き、
全公開診断表現、両出力、通常trace、説明JSON、最終状態から検出されないことを確認します。

## 8. 後続互換性

CONN機能グループの宣言構文、静的接続引数、`connection.resolve`、資源種別`logicalConnection`、操作`resolve`を
後続HTTPS機能グループでも維持します。HTTP語は同じ宣言名を静的引数に取り、平坦なネットワーク能力と、
論理接続名・HTTPメソッドを持つパラメータ付き要求を追加できます。

CONN機能グループの接続確認語をHTTP送信の暗黙前提にはしません。HTTP送信は呼出し時に改めて解決・認可し、
確認を先に書いたことを理由に検査を省略しません。ファイル作業領域やSQLデータソースへ名前付き資源を
展開するときも、論理接続のJava型や診断をそのまま汎用化しません。
