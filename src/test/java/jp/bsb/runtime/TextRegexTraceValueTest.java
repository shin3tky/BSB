package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import jp.bsb.regex.Re2RegexCompiler;
import jp.bsb.regex.RegexCompilationResult;
import jp.bsb.stdlib.ScalarType;
import org.junit.jupiter.api.Test;

/** TEXT-R009の32/16コードポイント、8要素、非開示境界を値単体で検証します。 */
class TextRegexTraceValueTest {
  @Test
  void boundsScalarStringAndRegexDisplayAt32CodePoints() {
    String thirtyTwo = "x".repeat(32);
    String thirtyThree = thirtyTwo + "y";
    RegexValue regex32 = regex("a".repeat(26), "");
    RegexValue regex33 = regex("a".repeat(27), "");

    assertEquals("文字列:" + thirtyTwo, visible(new StringValue(thirtyTwo)));
    assertEquals("文字列:" + thirtyTwo + "…", visible(new StringValue(thirtyThree)));
    assertEquals("正規表現:" + regex32.displayText(), visible(regex32));
    assertEquals("正規表現:" + regex33.displayText().substring(0, 32) + "…", visible(regex33));
    assertEquals("正規表現:正規表現「\\\\p{Han}」", visible(regex("\\p{Han}", "")));
  }

  @Test
  void boundsStringArraysAtEightElementsAndEachElementAt16CodePoints() {
    ArrayValue eight = strings(8, "値");
    ArrayValue nine = strings(9, "値");
    String sixteen = "𠮷".repeat(16);
    String seventeen = sixteen + "終";

    assertEquals("配列<文字列>:【「値0」、「値1」、「値2」、「値3」、「値4」、「値5」、「値6」、「値7」】", visible(eight));
    assertEquals("配列<文字列>:【「値0」、「値1」、「値2」、「値3」、「値4」、「値5」、「値6」、「値7」、…(+1)】", visible(nine));
    assertEquals("配列<文字列>:【「" + sixteen + "」】", visible(strings(sixteen)));
    assertEquals("配列<文字列>:【「" + sixteen + "…」】", visible(strings(seventeen)));
    assertEquals("配列<文字列>:【「行\\n列\\t終」】", visible(strings("行\n列\t終")));
  }

  @Test
  void redactionLeaksNoRegexOrArrayShapeAndKeepsThe21ColumnSchema() {
    RegexValue regex = regex("(?<secret>[a-z]+)", "ims");
    ArrayValue array = strings(9, "秘密");
    TraceValuePolicy redactAll = ignored -> false;

    assertEquals("正規表現:<redacted>", TraceValueFormatter.format(regex, redactAll));
    assertEquals("配列<文字列>:<redacted>", TraceValueFormatter.format(array, redactAll));
    assertEquals(
        TraceTsvFormatter.formatNumeric(List.of()), TraceTsvFormatter.formatTextRegex(List.of()));
    assertFalse(TraceValueFormatter.format(regex, redactAll).contains("secret"));
    assertFalse(TraceValueFormatter.format(regex, redactAll).contains("ims"));
    assertFalse(TraceValueFormatter.format(array, redactAll).contains("9"));
  }

  private static String visible(RuntimeValue value) {
    return TraceValueFormatter.format(value, TraceValuePolicy.textRegex());
  }

  private static RegexValue regex(String pattern, String flags) {
    RegexCompilationResult.Success success =
        (RegexCompilationResult.Success) new Re2RegexCompiler().compile(pattern, flags);
    return new RegexValue(pattern, flags, success.program());
  }

  private static ArrayValue strings(int count, String prefix) {
    var elements = new ArrayList<RuntimeValue>(count);
    for (int index = 0; index < count; index++) {
      elements.add(new StringValue(prefix + index));
    }
    return new ArrayValue(ScalarType.STRING, elements);
  }

  private static ArrayValue strings(String value) {
    return new ArrayValue(ScalarType.STRING, List.of(new StringValue(value)));
  }
}
