# https conformance data

HTTPSのN17 12件、F17 14件、R17 10件、計36 IDを本番実装前に固定し、中央適合で消費済みです。
規範は`docs/spec/https.md`、診断境界は
`docs/spec/https-diagnostics.md`、全IDは`docs/spec/https-conformance.md`です。

検査は次を実行します。

```shell
python3 tools/https_data.py --check
PYTHONPYCACHEPREFIX=/private/tmp/wbsb-pycache python3 -m unittest discover -s tools -p 'test_https_data.py' -v
./gradlew test --tests jp.bsb.conformance.HttpsConformanceDataTest
```

URI期待は本番Java、`java.net.URI`、JDK HTTPクライアントを使わない独立Python oracleで再計算します。
巨大境界は物理展開せず`resources.tsv`の数値レシピで表します。`manifest.tsv`はREADMEとmanifest自身を
除く全物理資源をSHA-256とバイト数で閉じ、余剰・欠落・BOM・CR・末尾LF欠落を拒否します。
