package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import jp.bsb.stdlib.ArrayLimits;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class WrapperArrayRuntimeTest {
  @Test
  void buildsDisplaysComparesAppendsAndIteratesWrapperElements() {
    String source =
        "メインとは （--）\n"
            + "    【1 を 任意にする、2 を 任意にする】 一行表示する\n"
            + "    【1 を 成功にする<整数,文字列>、「bad」を 失敗にする<整数,文字列>】 一行表示する\n"
            + "    【【1、2】 を 任意にする、【3】 を 任意にする】 一行表示する\n"
            + "    【1 を 任意にする】 と 2 を 任意にする を 配列の末尾へ追加する 一行表示する\n"
            + "    【1 を 任意にする、2 を 任意にする】\n"
            + "    各要素について\n"
            + "        任意から値を取り出す 一行表示する\n"
            + "    繰り返す\n"
            + "    【1 を 任意にする】 と 【1 を 任意にする】 を 等しい 一行表示する\n"
            + "こと。\n";
    var output = new MemoryOutputSink();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "wrapper-array.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(
        "【ある（1）、ある（2）】\n"
            + "【成功（1）、失敗（bad）】\n"
            + "【ある（【1、2】）、ある（【3】）】\n"
            + "【ある（1）、ある（2）】\n"
            + "1\n2\nはい\n",
        output.utf8Text());
    assertTrue(result.finalDataStack().isEmpty());
  }

  @Test
  void countsSelectedNestedPayloadsThroughWrappers() {
    ArrayValue row =
        new ArrayValue(
            ValueType.INTEGER, java.util.List.of(new IntegerValue(java.math.BigInteger.ONE)));
    OptionalValue present = OptionalValue.present(row);
    OptionalValue absent = OptionalValue.absent(row.type());
    ArrayValue value = new ArrayValue(present.type(), java.util.List.of(present, absent, present));

    assertEquals(2, value.logicalLeafCount());
    assertEquals(2, ValueType.arrayConstructorDepth(value.type()));
    assertTrue(value.logicalLeafCount() < ArrayLimits.MAX_NESTED_LEAF_ELEMENTS);
  }
}
