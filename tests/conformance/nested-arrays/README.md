# nested arrays conformance data

多次元配列のN18 12件、F18 12件、R18 10件、計34 IDを本番実装前に固定し、中央適合まで完了しました。規範は
`docs/spec/nested-arrays.md`、診断は`docs/spec/nested-arrays-diagnostics.md`、
全IDは`docs/spec/nested-arrays-conformance.md`です。

`catalog.tsv`は全IDを順序付きで列挙します。巨大境界は物理展開せず`resources.tsv`の閉じたrecipeで表し、
`manifest.tsv`はREADMEとmanifest自身を除く全物理資源をSHA-256とバイト数で閉じます。

検査は次を実行します。

```shell
python3 tools/nested-arrays_data.py --check
PYTHONPYCACHEPREFIX=/private/tmp/wbsb-pycache python3 -m unittest discover -s tools -p 'test_nested-arrays_data.py' -v
./gradlew test --tests jp.bsb.conformance.NestedArrayConformanceDataTest
```
