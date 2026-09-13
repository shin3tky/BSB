# 回復可能JSON・先行適合データ

回復可能JSONのJava実装より先に固定した、回復可能なJSON解析の独立期待です。規範は
`docs/spec/recoverable-json-parsing.md`、`docs/spec/recoverable-json-diagnostics.md`、
`docs/spec/recoverable-json-conformance.md`です。

- `catalog.tsv`: N14 10、F14 10、R14 12の全32 ID
- `cases.tsv`: 7成功、13回復可能失敗、位置対照、既存診断版対照
- `resources.tsv`: 捕捉不能な上限と原子性の境界レシピ
- `expected/diagnostics.tsv`: F14全10 IDの診断コード・段階・対象語・型・原子性期待
- `expected/states.tsv`: 代表公開sourceの独立した終了状態
- `sources`、`canonical`、`chapter`: 公開CLIへ通す物理sourceと正規形
- `manifest.tsv`: READMEとmanifest自身を除く全資源のSHA-256とバイト数

仕様、独立期待、実行系、中央カタログからの全件実行適合まで完成しています。次でデータ整合と
RecoverableJsonの対象テストを再確認できます。

```shell
python3 tools/recoverable-json_data.py --check
python3 -m unittest discover -s tools -p 'test_recoverable-json_data.py' -v
./gradlew test --tests '*RecoverableJson*'
```

検査は読取り専用で、期待ファイルを更新しません。
