# RNG機能グループ: 一様整数乱数

> 本文は現行実装の規範契約です。

## 1. 単語と範囲

```text
整数乱数を得る    （整数 整数 -- 整数）
```

入力順は下限 `a`、上限 `b` です。両端を含む `a..b` から整数を1個返します。
負数を含め、既存の整数値上限内の任意精度整数を扱います。小数の暗黙変換はありません。
下限と上限が同じなら、その値を返し乱数源を呼びません。

```text
メインとは （--）
    1 と 6 から 整数乱数を得る 一行表示する
こと。
```

この例は1〜6のいずれかを表示します。短い試行で出現回数が揃うことは要求しません。
小数乱数、分布の指定、BSBソースからのシード操作、暗号用乱数はこの機能グループに含めません。

## 2. 一様性と棄却法

候補数を `n = b - a + 1` とします。`n > 1` なら `k = bitLength(n - 1)` とし、
乱数バイト源から `ceil(k / 8)` バイトを取得します。先頭バイトの余分な上位ビットを捨て、
符号なしbig-endian整数 `x` を作ります。

- `0 <= x < n` なら `a + x` を返します。
- `x >= n` なら、新しいバイト列から候補を作り直します。

範囲への変換で剰余、浮動小数、絶対値を使いません。範囲幅と候補は任意精度整数で計算します。
合法な下限・上限から作る範囲幅は65,537桁になる場合がありますが、これは内部計算として許可します。
返す値は必ず入力範囲内なので、既存の整数値上限を超えません。

公平で独立な乱数ビットを仮定すると、各候補は `1 / 2^k` の確率です。
`r = 1 - n / 2^k` とすると、試行上限 `M` までに特定の値を返す確率は
`sum(j=0..M-1, r^j / 2^k) = (1 - r^M) / n` です。
したがって成功を条件にした各整数の確率は正確に `1 / n` であり、失敗確率は `r^M` です。
`k` が最小なので `r < 1/2`、平均候補数は上限なしの場合でも2未満です。
これは範囲変換の保証であり、有限状態の疑似乱数源そのものの完全な独立性を保証しません。

## 3. 実行環境能力

必要能力と副作用はともに `random.bytes` です。実行環境は
`RandomSource.nextBytes(byte[])` で全要素を乱数バイトに上書きし、渡された配列を保持しません。
公平で独立なビット源を想定した範囲変換を行いますが、源の統計的品質を実行時に判定しません。

CLIと `ExecutionContext.standard` は、実行環境ごとに自動シードの `L64X128MixRandom` を1個作ります。
呼出しごとに作り直さず、同じ実行の間は状態を継続します。暗号用途の保証はしません。
埋込みホストは `ExecutionEnvironment.builder(clock).randomSource(source)` で源を注入できます。
`RandomSource.seeded(long)` は同じJDK実装、シード、呼出し順での再現試験に利用できます。
JDKやアルゴリズム変更をまたぐ乱数列の一致は公開契約に含めません。
従来の3引数 `ExecutionContext` には乱数能力を暗黙に追加しません。

Javaの乱数源の仕様は [java.util.random](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/random/package-summary.html)
と [RandomGeneratorFactory](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/random/RandomGeneratorFactory.html) を参照してください。

## 4. 診断・資源・原子性

静的検査で入力が整数でなければ `E_TYPE_MISMATCH`、入力が足りなければ `E_STACK_UNDERFLOW` です。
実行時は次の順に判定します。

1. `a > b` は既存の `E_NUMERIC_RANGE_INVALID`。`word`、`lowerPreview`、`upperPreview` と
   必要な省略前の長さを数値範囲操作と同じ形で返します。乱数能力を参照しません。
2. 乱数能力がなければ `E_CAPABILITY_UNAVAILABLE`。`a = b` の場合も宣言された能力を要求します。
3. `a = b` なら乱数源を呼ばず、その値を返します。
4. 通常の採用または下記の失敗まで候補生成を続けます。

1回の取得の候補数上限は `RuntimeLimits.RANDOM_ATTEMPTS = 1,024` です。
最後の候補も採用可能です。すべて棄却された場合は `E_CAPABILITY_FAILURE`、
`capability=random.bytes`、`operation=sample` とし、代替値を返しません。
能力側の失敗やホスト実行時例外は `E_CAPABILITY_FAILURE` にし、ホスト例外詳細を開示しません。
標準のバイト取得失敗の操作名は `nextBytes` です。

候補生成の前後で既存の能動実行時間上限を検査し、超過なら `E_EXECUTION_TIMEOUT` です。
候補生成や棄却をIR命令数へ追加しません。失敗時には入力スタックを変更しません。
乱数源の状態は巻き戻しません。

## 5. 説明・整形・トレース

`check`、`format`、`explain` は乱数源を呼びません。既存の整数型と通常の単語呼出しの整形を使います。
辞書の機能グループは `RNG`、型規則は `fixed`、能力と副作用は `random.bytes` です。
成功した呼出しのトレース効果は `random.bytes` とし、候補・シード・棄却回数は記録しません。
トレースの有無で乱数源の呼出し回数や生成列を変更しません。

## 6. 適合性

`tests/conformance/integer-random/sources` の宣言的ソースは
`IntegerRandomConformanceTest` が実行します。正常例はサイコロ範囲、同じ負の両端、
64ビットを超える範囲、失敗例は逆順の両端、小数入力、入力不足です。

`IntegerRandomRuntimeTest` は候補列を列挙し、各値が同じ回数だけ採用されること、
棄却とビットマスク、符号なし変換、最大合法範囲の両端、能力不足・失敗・候補上限、
実行時間超過、シード再現性とトレース不変性を検証します。
`IntegerRandomCliTest` は通常の乱数源で `check`、`run`、`format`、`explain` を検証します。
統計的な頻度の閾値に依存する不安定なテストは使用しません。
