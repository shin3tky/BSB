# CLI接続設定TOML 版1

## 1. 目的と境界

標準CLIから論理接続とHTTPS送信能力を明示的に注入し、Java埋込みコードを作らずにAPI連携スクリプトを
実行できるようにします。BSB言語の値、語、静的説明、論理接続の送信時再解決は変更しません。

設定は`run`へ指定した1ファイルからだけ読みます。自動探索、複数ファイルのマージ、汎用環境変数読取り、
TOML文字列展開、Basic・OAuth、redirect、retryは版1に含めません。

## 2. CLI構文

```text
bsb run --connections <設定.toml> <ソース.bsb>
bsb run --connections <設定.toml> <ソース.bsb> -- [引数...]
```

`--connections`は`run`の直後に1回だけ指定できます。`check`、`explain`、`format`では使用できません。
設定を指定しない従来の`run`はresolverとHTTP transportを持たないままです。

引数またはTOML形状の不正、設定ファイルの読取り失敗、資格情報環境変数の未設定、JDK HTTPS安全設定の
競合はプログラム実行前に停止します。TOML接続設定の失敗はstdoutを変更せず、秘密を含まない
`接続設定エラー`をstderrへ書き、終了コード78を返します。

## 3. 物理形式と資源

- TOML 1.0、BOMなしの厳密UTF-8
- 1ファイル1,048,576バイト以下
- `connections`は1〜256項目
- 接続名は空でないNFC文字列で、BSBソースの論理接続名と完全一致
- 未知項目、重複項目、型違い、未対応schema版は拒否

TOML parserの自由文や入力行を公開エラーへ転記しません。構文エラーは行・列だけを公開します。

## 4. スキーマ

```toml
schema-version = 1

[connections."顧客管理API"]
base-uri = "https://api.example.com/base/"
allowed-methods = ["POST"]
connect-timeout-ms = 5000
response-timeout-ms = 10000
maximum-request-bytes = 1048576
maximum-response-bytes = 1048576

[connections."顧客管理API".authentication]
kind = "api-key"
header = "x-api-key"
value-env = "CRM_API_KEY"
```

`base-uri`、`allowed-methods`、`authentication`は必須です。4個の数値項目は省略でき、上記の値を
既定値とします。許可originは`base-uri`のoriginだけから導出し、TOMLから追加できません。
redirectは`deny`、retryは`none`に固定します。構築した方針は既存`ConnectionPolicy`の規範検証を
読込み時と送信時の両方で通します。

認証なしは次の形です。

```toml
[connections."公開API".authentication]
kind = "none"
```

この場合、認証用の追加項目は指定できません。

## 5. APIキー

`kind="api-key"`では`header`を省略でき、既定値は`x-api-key`です。秘密値は次のどちらか一方だけを
指定します。

```toml
value = "直接保持する秘密"
```

```toml
value-env = "CRM_API_KEY"
```

`value-env`はASCIIの環境変数名で、CLIホストだけが実行前に読みます。BSBプログラムへ環境変数読取り能力を
与えません。空または未設定なら設定エラーです。`${CRM_API_KEY}`のような文字列展開は行いません。

直接値を含むファイルは平文の秘密保管物として扱い、版管理へ追加せず所有者だけが読める権限にする必要が
あります。いずれの方式でも値、TOML入力行、parser例外、資格情報参照を診断、trace、通常出力へ載せません。

## 6. 実行環境への注入

CLIは接続ごとに不透明`CredentialReference`と`ConnectionPolicy`を作り、名前完全一致の
`ConnectionResolver`と1個の`JdkHttpsTransport`を`ExecutionEnvironment`へ追加します。未設定名は既存の
`NOT_CONFIGURED`です。送信時には既存どおり方針、method、origin、認証、資源を再検証します。

CLIはJVM全体を所有するため、JDKの接続再試行抑止propertyが未設定ならHTTP client構築前に`true`へ固定します。
既存の値やhostname検証・HTTP logging等の競合設定は上書きせず、安全preflightで設定エラーにします。

## 7. 版1の対象外

- 設定ファイルの自動探索と暗黙読込み
- 複数ファイル、include、profile、上書き、文字列展開
- secret専用ファイル、OS keychain、外部secret manager
- Basic、OAuth 2.0、redirect、retry、proxy、独自trust store
- 解決済み接続計画の公開、`run --json`
