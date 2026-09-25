# BSB Language Support for VS Code

BSB (`.bsb`) の構文ハイライトとLanguage Serverによるリアルタイム診断を提供します。

## 必要環境

- Visual Studio Code 1.91以降
- Java 25以降

JavaがPATHにない場合は、machine設定 `bsb.java.path` にJava実行ファイルの絶対パスを指定してください。

## 開発

```shell
cd editors/vscode
npm install
npm run generate:icon
npm test
npm run package:vsix
```

`package:vsix` はLanguage ServerをGradleでビルドしてVSIXへ同梱します。
アイコンの再生成にはPython 3とPillowが必要です。生成元は
`../../tools/gen-icon.py` です。
