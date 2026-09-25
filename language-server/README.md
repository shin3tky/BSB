# BSB Language Server

BSB処理系をLanguage Server Protocolへ公開するJavaサブプロジェクトです。
現在はLSP 3.17のUTF-16位置表現、文書のopen/close、全文同期、静的診断に対応します。

開発時はGradleから標準入出力サーバーを起動できます。

```shell
./gradlew :language-server:run
```

単独実行JARを作る場合は次を実行します。

```shell
./gradlew :language-server:shadowJar
java -jar language-server/build/libs/language-server-all.jar
```

標準出力はJSON-RPC専用です。ログを追加する場合は標準エラーまたはファイルへ出力してください。
