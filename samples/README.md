# BSB サンプル集

現在実装されている仕様を使ったサンプルです。
コマンドはリポジトリのルートで実行してください。

## 検査と実行

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar check samples/01-hello-world.bsb
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run samples/01-hello-world.bsb
```

標準入力を使うサンプルは、次のように実行できます。

```shell
printf '山田 太郎\n' | java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run samples/11-input-greeting.bsb
```

起動引数を使うサンプルでは、`--` より後ろに引数を指定します。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run samples/12-command-line-arguments.bsb -- 赤 青 緑
```

JSONの解析、配列反復、必須取得、不変更新は次のサンプルで確認できます。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run samples/14-json-processing.bsb
```

`任意<JSON>`による非必須検索と、値あり・JSONヌル・キー不在の区別は次のサンプルで確認できます。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run samples/15-optional-json.bsb
```

`結果<T,E>`による成功・失敗と、成功側に含む任意値の3状態は次のサンプルで確認できます。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar check samples/16-result-values.bsb
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run samples/16-result-values.bsb
```

出力は`configured`、`null`、`default`、`not-object`の4行です。最初の3行は成功値の
値あり・JSONヌル・値なし、最後の1行は明示的な失敗値です。

外部から受け取った文字列をJSONとして解析し、成功値と回復可能な構文失敗を通常分岐で扱う例です。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar check samples/17-recoverable-json.bsb
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run samples/17-recoverable-json.bsb
```

有効なJSONの標準形と、不正入力の安定種類を2行で表示します。入力内位置は同じ失敗値へ
`JSON解析失敗のバイト位置を取り出す`、`JSON解析失敗の行を取り出す`、
`JSON解析失敗の列を取り出す`を適用して取得できます。

必須・任意キー、配列要素、null許容を第一級の`JSON形状`でまとめて検証する例です。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar check samples/json-shape-validation.bsb
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run samples/json-shape-validation.bsb
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar format samples/json-shape-validation.bsb
```

成功時は元のJSONを表示し、失敗時は形状の設定順・配列添字順にJSON Pointer pathを表示します。

RFC 6901 JSON Pointerで入れ子値を1語で任意参照し、キー配列と値配列からJSONオブジェクトを
一括構築する例です。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar check samples/json-pointer-construction.bsb
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run samples/json-pointer-construction.bsb
```

2つの論理接続を宣言し、直接および利用者定義語経由で確認する例です。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar check samples/18-logical-connections.bsb
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar explain --json samples/18-logical-connections.bsb
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run samples/18-logical-connections.bsb
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run --connections samples/logical-connections.toml samples/18-logical-connections.bsb
```

接続TOMLを指定しない最初の`run`は`E_CAPABILITY_UNAVAILABLE`で終了し、最後の`run`は2接続を
解決して終了0になります。どちらもHTTP要求は送信しません。差し替え可能な偽resolverを注入する埋込み例は
[`LogicalConnectionEmbedding.java`](LogicalConnectionEmbedding.java)です。fat JARの後に次のように実行し、
成功、未設定、拒否、設定不正の4状態をネットワークなしで確認できます。

```shell
javac -cp build/libs/bsb-0.1.0-SNAPSHOT-all.jar -d /tmp/bsb-connection-example samples/LogicalConnectionEmbedding.java
java -cp build/libs/bsb-0.1.0-SNAPSHOT-all.jar:/tmp/bsb-connection-example LogicalConnectionEmbedding resolved
java -cp build/libs/bsb-0.1.0-SNAPSHOT-all.jar:/tmp/bsb-connection-example LogicalConnectionEmbedding not-configured
java -cp build/libs/bsb-0.1.0-SNAPSHOT-all.jar:/tmp/bsb-connection-example LogicalConnectionEmbedding denied
java -cp build/libs/bsb-0.1.0-SNAPSHOT-all.jar:/tmp/bsb-connection-example LogicalConnectionEmbedding invalid
```

不変`バイト列`の長さ・slice・等値比較、UTF-8・Base64変換、回復可能な復号失敗を確認する例です。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar check samples/19-byte-sequences.bsb
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run samples/19-byte-sequences.bsb
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar format samples/19-byte-sequences.bsb
```

バイト列自体は表示せず、UTF-8復号の成功文字列または規範Base64へ明示変換します。不正な外部入力は
`UTF8復号失敗`・`Base64復号失敗`の安定種類として通常分岐で扱えます。

stdin JSONから顧客登録要求を作り、status、Content-Type、応答JSONを検査する参照例です。
通常はネットワークを使わない偽能力例を実行します。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar check samples/20-minimal-https.bsb
javac -cp build/libs/bsb-0.1.0-SNAPSHOT-all.jar -d /tmp/bsb-https-example samples/MinimalHttpsFakeEmbedding.java
java -cp build/libs/bsb-0.1.0-SNAPSHOT-all.jar:/tmp/bsb-https-example MinimalHttpsFakeEmbedding
```

実JDK HTTPS例は利用者が用意したendpointでのみ実行する任意smokeです。URLと秘密は
ソースやfixtureに保存せず、すべて明示的な環境変数から渡します。endpointは`POST /v1/customers`に
2xx〜5xxの最終応答、`Content-Type: application/json`、UTF-8 JSON本文を返す必要があります。

```shell
javac -cp build/libs/bsb-0.1.0-SNAPSHOT-all.jar -d /tmp/bsb-https-example samples/MinimalHttpsJdkEmbedding.java
export BSB_HTTPS_BASE_URI='https://your-endpoint.example/base/'
export BSB_HTTPS_API_KEY='set-this-in-your-shell'
export BSB_HTTPS_API_KEY_HEADER='x-api-key'
printf '{"name":"山田 太郎"}\n' | java -Djdk.httpclient.disableRetryConnect=true -cp build/libs/bsb-0.1.0-SNAPSHOT-all.jar:/tmp/bsb-https-example MinimalHttpsJdkEmbedding
```

このsmokeは通常のGradleテスト・中央適合の対象外です。リダイレクトと再試行は行わず、
ホスト名検証つきの既定PKIX trust store、TLS 1.2/1.3、無proxyを使います。

公開サービス`https://httpbin.org/ip`から呼出し元のIPアドレス表現を取得し、`origin`だけを表示する例です。
版1 TOMLを明示すれば、標準CLIからそのまま実行できます。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run --connections samples/httpbin-connections.toml samples/21-httpbin-ip.bsb
```

不規則な二次元配列の表示と、外側・内側の二重反復を確認する例です。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar check samples/22-two-dimensional-arrays.bsb
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run samples/22-two-dimensional-arrays.bsb
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar format samples/22-two-dimensional-arrays.bsb
```

二次元配列は`配列<配列<T>>`で、行ごとの列数が異なるragged値も保持できます。CSV/TSV直列化では
作業領域・区切り表の表境界が矩形性を検査し、ragged値を専用診断で拒否します。

名前付き作業領域から2つの論理ファイルを読み、選択した完全バイト列を別の論理ファイルへ書く例です。
OS pathはBSBソースではなく、ホスト側の反復`--file`で有限登録します。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar check samples/23-named-workspaces-files.bsb
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar explain --json samples/23-named-workspaces-files.bsb
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run \
  --file 帳票 入力A.dat read README.md \
  --file 帳票 入力B.dat read docs/README.md \
  --file 帳票 出力.dat write /tmp/bsb-selected.dat \
  samples/23-named-workspaces-files.bsb -- 入力A.dat 入力B.dat 出力.dat
```

同じ登録は、版1 TOMLからも与えられます。`named-workspaces.toml.example`のpathは設定ファイルの親を
基準にするため、この例ではリポジトリ直下の`input-a.dat`、`input-b.dat`、`output.dat`を指します。
利用前にpathを対象ファイルへ変更してください。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run \
  --workspaces samples/named-workspaces.toml.example \
  samples/23-named-workspaces-files.bsb -- 入力A.dat 入力B.dat 出力.dat
```

OSファイルを使わない偽resolver・偽読取・偽書込の埋込み例もあります。

```shell
javac -cp build/libs/bsb-0.1.0-SNAPSHOT-all.jar -d /tmp/bsb-workspace-example samples/NamedWorkspaceFakeEmbedding.java
java -cp build/libs/bsb-0.1.0-SNAPSHOT-all.jar:/tmp/bsb-workspace-example NamedWorkspaceFakeEmbedding
```

出力は選択した3バイト、終了0、resolver 3回、read 2回、write 1回を示します。

名前付き作業領域からCSVとTSVを読み、厳密UTF-8で解析し、TSVの先頭行を追加したCSVを別登録へ
原子的に書く縦断例です。入力にはBOM、quoted改行、空field、日本語を含みます。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar check samples/24-csv-tsv-files.bsb
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run \
  --file 入力 商品.csv read products.csv \
  --file 入力 注記.tsv read notes.tsv \
  --file 出力 集計.csv write summary.csv \
  samples/24-csv-tsv-files.bsb -- 商品.csv 注記.tsv 集計.csv
```

TOMLでは`delimited-tables-workspaces.toml.example`の相対pathを実ファイルへ合わせてから実行します。
OSファイルを使わない偽能力例は次です。

```shell
javac -cp build/libs/bsb-0.1.0-SNAPSHOT-all.jar -d /tmp/bsb-delimited-example samples/DelimitedTablesFakeEmbedding.java
java -cp build/libs/bsb-0.1.0-SNAPSHOT-all.jar:/tmp/bsb-delimited-example DelimitedTablesFakeEmbedding
```

偽例は55 bytesのBOMなしCRLF CSV、終了0、resolver 3回、read 2回、write 1回、区切り作業198単位を確認します。

固定されたHTTPS接続方針を注入する従来のJDK埋込み例も利用できます。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar check samples/21-httpbin-ip.bsb
javac -cp build/libs/bsb-0.1.0-SNAPSHOT-all.jar -d /tmp/bsb-httpbin-example samples/HttpbinIpJdkEmbedding.java
java -Djdk.httpclient.disableRetryConnect=true -cp build/libs/bsb-0.1.0-SNAPSHOT-all.jar:/tmp/bsb-httpbin-example HttpbinIpJdkEmbedding
```

表示される値は実行環境からhttpbinに見える送信元です。通常テストでは`203.0.113.10`を返す偽transportを使い、
httpbinの稼働状況や実行環境のネットワークには依存しません。

同じプログラムへ有限再試行を設定する版2例は`http-reliability-connections.toml.example`です。
GETだけを最大3試行し、`Retry-After`と接続単位100ミリ秒の開始間隔を尊重します。実endpointを使用する
任意smokeは次の形です。

```shell
java -Djdk.httpclient.disableRetryConnect=true -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run \
  --connections samples/http-reliability-connections.toml.example samples/21-httpbin-ip.bsb
```

APIキー付きの参照例を標準CLIで実行する場合は、
`samples/minimal-https-connections.toml.example`の`base-uri`を対象endpointへ変更し、秘密だけを
環境変数から渡せます。

```shell
export BSB_HTTPS_API_KEY='set-this-in-your-shell'
printf '{"name":"山田 太郎"}\n' | java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run --connections samples/minimal-https-connections.toml.example samples/20-minimal-https.bsb
```

秘密をTOMLの`value`へ直接記述することもできます。そのファイルは版管理へ追加せず、例えば
`local.connections.local.toml`のような無視対象名を使ってください。完全な形式は
[`docs/spec/cli-connection-config.md`](../docs/spec/cli-connection-config.md)にあります。

form URL encoding、Basic認証、status判定を組み合わせる第24段階の例は次です。設定例にはBearer認証の
別接続も含みます。`base-uri`を利用するAPIへ変更し、秘密はshell環境だけから渡してください。gzip・deflate応答は
transportが自動展開します。

```shell
export BSB_API_USERNAME='set-this-in-your-shell'
export BSB_API_PASSWORD='set-this-in-your-shell'
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run \
  --connections samples/http-api-connections.toml.example \
  samples/26-http-api-interoperability.bsb
```

2人用の三目並べは、1から9のマス番号を交互に入力して遊べます。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run samples/131-tic-tac-toe.bsb
```

ANSIエスケープシーケンスに対応した端末では、盤面を一度だけ描画し、カーソル移動で
選んだマスだけを上書きする版も実行できます。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run samples/132-fixed-tic-tac-toe.bsb
```

喜・怒・哀・楽の4状態が色付きで発生・遷移・衰退する「喜怒哀楽ライフゲーム」は、
各世代を同じ端末座標へ上書きします。盤外は「無」として扱い、初期配置は毎回同じになる
決定的な模様です。既定値は10×10、30世代、1000ミリ秒間隔です。実行時間と出力量を調整する場合は、
ソース先頭の4変数を変更できます。盤面か世代数を大きくすると、現行処理系の命令数上限へ
到達する場合があります。初期配置は、各感情のX座標配列とY座標配列へ0始まりの座標を
対応する順番で追加すると変更できます。盤外の座標は無視されます。

```shell
java -jar build/libs/bsb-0.1.0-SNAPSHOT-all.jar run samples/133-emotional-life-game.bsb
```

## 一覧

| ファイル                           | 内容                             | 主な機能                                     |
|------------------------------------|----------------------------------|----------------------------------------------|
| `01-hello-world.bsb`               | Hello, World                     | 文字列、標準出力                             |
| `02-arithmetic.bsb`                | 四則演算の一部と剰余             | 単語定義、整数演算                           |
| `03-fizzbuzz.bsb`                  | 1から100までのFizzBuzz           | 変数、回数ループ、条件分岐                   |
| `04-factorial-loop.bsb`            | 10の階乗（ループ版）             | 引数、局所変数、条件ループ                   |
| `04-factorial-recursive.bsb`       | 10の階乗（再帰版）               | 引数、局所変数、直接再帰                     |
| `05-fibonacci.bsb`                 | 先頭15個のフィボナッチ数         | 複数の局所変数、条件ループ                   |
| `06-countdown.bsb`                 | 10から1までのカウントダウン      | 大域変数、条件ループ                         |
| `07-array-aggregation.bsb`         | 点数の合計・件数・平均           | 配列、各要素ループ、小数除算                 |
| `08-string-processing.bsb`         | 文字列の整形・分割・置換         | Unicode文字列操作、配列                      |
| `09-regular-expression.bsb`        | 日付文字列の検査と部分抽出       | 正規表現、名前付き部分                       |
| `10-decimal-calculation.bsb`       | 税込み価格の計算                 | 文字列指数変換、正確な小数演算、固定小数表示 |
| `11-input-greeting.bsb`            | 入力された名前への挨拶           | 一行入力、終端判定                           |
| `12-command-line-arguments.bsb`    | 起動引数の列挙                   | 実行環境、文字列配列                         |
| `13-decimal-division-rounding.bsb` | 小数除算の丸め方法を比較         | 有効桁数、5種類の丸め方法                    |
| `14-json-processing.bsb`           | 注文JSONの検査と変換            | JSON解析、必須取得、配列反復、不変更新 |
| `15-optional-json.bsb`             | 任意設定の3状態                  | 任意値、JSON非必須検索、JSONヌル       |
| `16-result-values.bsb`             | 設定検証の成功・失敗             | 結果値、任意値、JSON検証               |
| `17-recoverable-json.bsb`          | 受信JSONの回復可能な解析         | 結果値、JSON解析失敗、入力内位置        |
| `json-shape-validation.bsb`        | 外部JSONの構造検証               | 必須・任意キー、配列形状、null許容      |
| `18-logical-connections.bsb`       | 2つの論理接続の確認              | 接続宣言、静的引数、直接・推移要求       |
| `19-byte-sequences.bsb`            | 不変バイト列の変換と検査         | UTF-8、Base64、slice、等値、復号失敗     |
| `20-minimal-https.bsb`              | stdin JSONからHTTPS API連携       | 不変要求、論理接続、status、header、JSON応答 |
| `21-httpbin-ip.bsb`                 | httpbinから送信元IPを取得         | HTTPS GET、応答JSON、文字列取出し             |
| `http-reliability-connections.toml.example` | HTTP信頼性のCLI設定例 | 有限再試行、Retry-After、開始間隔 |
| `22-two-dimensional-arrays.bsb`     | raggedな二次元配列                | 二重反復、構造表示、配列内配列                 |
| `23-named-workspaces-files.bsb`     | 複数ファイルの読取・選択・書込   | 名前付き作業領域、完全バイト列、原子的置換     |
| `24-csv-tsv-files.bsb`              | CSV/TSVの読取・行追加・CSV書込   | 厳密UTF-8、quoted改行、二次元配列、決定的CSV   |
| `25-optional-result-composition.bsb` | 任意・結果の局所伝播と配列       | prefix保持、失敗伝播、wrapper配列              |
| `26-http-api-interoperability.bsb` | form POSTとstatus判定 | form URL encoding、Basic、gzip/deflate自動展開 |
| `http-api-connections.toml.example` | HTTP API用CLI設定の雛形 | schema version 3、Basic、Bearer |
| `named-workspaces.toml.example`     | CLI作業領域設定の雛形             | 複数登録、read/write許可、相対path             |
| `delimited-tables-workspaces.toml.example` | CSV/TSV用CLI設定の雛形       | 2入力領域、出力領域、有限ファイル登録           |
| `HttpbinIpJdkEmbedding.java`        | httpbin接続の実TLS埋込み          | 固定origin、JDK HTTPS、timeout、PKIX           |
| `LogicalConnectionEmbedding.java` | 論理接続resolverの埋込み          | 成功、未設定、拒否、設定不正                   |
| `MinimalHttpsFakeEmbedding.java`  | 参照HTTPSの偽能力埋込み       | APIキー参照、要求検査、固定応答              |
| `MinimalHttpsJdkEmbedding.java`   | 利用者明示設定の実TLS smoke   | JDK HTTPS、APIキー、timeout、PKIX              |
| `NamedWorkspaceFakeEmbedding.java` | 作業領域の偽能力埋込み            | resolver、完全読取、原子的公開、能力回数       |
| `DelimitedTablesFakeEmbedding.java` | CSV/TSV縦断の偽能力埋込み         | 厳密変換、混在入力、決定的出力、能力回数         |
| `121-four-seasons.bsb`             | めぐる自然を紡ぐ                 | 配列、回数ループ、文字列連結                 |
| `122-iroha.bsb`                    | いろは                           | 配列、各要素ループ                           |
| `123-circle-of-transmigration.bsb` | 生々流転                         | 単語定義、前方参照                           |
| `124-3n-plus-1.bsb`                | コラッツ予想 3n+1問題            | 整数演算、条件分岐                           |
| `125-Euclidean-algorithm.bsb`      | ユークリッドの互除法             | 整数演算、条件分岐                           |
| `129-prime-sieve.bsb`              | エラトステネスの篩による素数列挙 | 単語定義、配列操作、条件ループ               |
| `130-rpn-calculator.bsb`           | 逆ポーランド記法（RPN）電卓      | 文字列分割、配列によるスタック操作、四則演算 |
| `131-tic-tac-toe.bsb`              | 2人用の対話式三目並べ             | 一行入力、配列更新、勝敗判定、条件ループ       |
| `132-fixed-tic-tac-toe.bsb`        | 盤面が流れない三目並べ           | ANSIカーソル移動、行消去、部分上書き         |
| `133-emotional-life-game.bsb`      | 喜怒哀楽ライフゲーム               | 色付き固定盤面、8近傍、状態遷移、待機     |

`13-decimal-division-rounding.bsb` の精度指定は「小数点以下の桁数」ではなく「有効桁数」です。`1.0 ÷ 3`、`1.0 ÷ 8.0`、`-1.0 ÷ 8.0`を使い、丸め方向による結果の違いを表示します。

`10-decimal-calculation.bsb`は`「1.98e3」`と`「1e-1」`を文字列から`小数`へ変換し、計算結果を`2178.0`のような指数を使わない固定小数点で表示します。
