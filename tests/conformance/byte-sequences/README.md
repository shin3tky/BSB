# byte sequences conformance data

バイト列のN16 12件、F16 12件、R16 10件、計34 IDをJava実装前に固定し、中央適合から
過不足なく消費します。章末と利用者サンプルの公開5コマンドは各2回、固定バイト・SHA-256で検証します。
規範は`docs/spec/byte-sequences.md`、診断境界は
`docs/spec/byte-sequences-diagnostics.md`、全IDは`docs/spec/byte-sequences-conformance.md`です。

検査は次を実行します。

```shell
python3 tools/byte-sequences_data.py --check
PYTHONPYCACHEPREFIX=/private/tmp/wbsb-pycache python3 -m unittest discover -s tools -p 'test_byte-sequences_data.py' -v
./gradlew test --tests jp.bsb.conformance.ByteSequenceConformanceDataTest
```

UTF-8とBase64の期待値は本番Java codecから生成しません。巨大境界は物理展開せず、`resources.tsv`の
閉じたレシピで表します。`manifest.tsv`はREADMEとmanifest自身以外の全物理資源をSHA-256とバイト数で
閉じ、余剰・欠落・BOM・CR・末尾LF欠落を拒否します。
Base64正常ベクトルでは、空バイト列と空文字列を末尾空欄と区別するため`-`で表します。
