# workspace tables conformance data

作業領域・区切り表のN19 12件、F19 18件、R19 10件と、後半のN19 10件、F19 12件、R19 8件の
全70 IDを中央適合へ接続済みです。後半期待は本番CSV/TSV実装より先に固定しました。規範は
`docs/spec/workspace-files.md`と`docs/spec/delimited-tables.md`、全70 IDは
`docs/spec/workspace-tables-conformance.md`です。

`catalog.tsv`は前半を`part=files`、後半を`part=delimited`で順序付き列挙します。CSV/TSVの表は、`[]`を空表、
`;`を行境界、`,`をセル境界、`_`を空セル、それ以外をセルUTF-8 hexとして表します。入力・出力もUTF-8 hexで、
`-`だけを空bytesに使います。巨大境界は展開せず2つのresources表の閉じたrecipeで表し、
`manifest.tsv`はREADMEとmanifest自身を除く全物理資源のSHA-256とバイト数を閉じます。

```shell
python3 tools/workspace-tables_data.py --check
PYTHONPYCACHEPREFIX=/private/tmp/wbsb-pycache python3 -m unittest discover -s tools -p 'test_workspace-tables_data.py' -v
./gradlew test --tests jp.bsb.conformance.WorkspaceTableConformanceDataTest
```
