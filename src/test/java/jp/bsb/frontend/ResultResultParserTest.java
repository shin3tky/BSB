package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import jp.bsb.frontend.ast.ArrayLiteral;
import jp.bsb.frontend.ast.TypeReference;
import jp.bsb.frontend.ast.WordCall;
import org.junit.jupiter.api.Test;

class ResultResultParserTest {
  @Test
  void retainsBothTypeArgumentsOnTypesAndConstructorCallsInEveryExpressionContext() {
    var parsed =
        ParserTestSupport.parseText(
            "result.bsb",
            "構築とは （整数 -- 結果<整数,任意<文字列>>）\n"
                + "    成功にする < 整数 , 任意 < 文字列 > >\n"
                + "こと。\n\n"
                + "配列内とは （-- 配列<整数>）\n"
                + "    【「失敗」を 失敗にする<整数,文字列>】\n"
                + "こと。\n\n"
                + "メインとは （--）\nこと。\n");

    assertTrue(parsed.parseResult().successful(), parsed.diagnostics().toString());
    var definitions = parsed.parseResult().programForAnalysis().definitions();
    TypeReference resultType = definitions.getFirst().stackEffect().outputTypes().getFirst();
    assertTrue(resultType.isResult());
    assertEquals("整数", resultType.typeArgument().orElseThrow().name());
    assertEquals("任意<文字列>", resultType.secondTypeArgument().orElseThrow().name());

    WordCall bodyCall = (WordCall) definitions.getFirst().body().getLast();
    assertEquals(
        List.of("整数", "任意<文字列>"),
        bodyCall.explicitTypeArguments().stream().map(TypeReference::name).toList());
    assertEquals("成功にする < 整数 , 任意 < 文字列 > >", sourceSlice(parsed.source().text(), bodyCall));

    ArrayLiteral array = (ArrayLiteral) definitions.get(1).body().getFirst();
    WordCall arrayCall = (WordCall) array.elements().getFirst().body().getLast();
    assertEquals(
        List.of("整数", "文字列"),
        arrayCall.explicitTypeArguments().stream().map(TypeReference::name).toList());
  }

  private static String sourceSlice(String source, WordCall call) {
    byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
    return new String(
        Arrays.copyOfRange(
            bytes,
            Math.toIntExact(call.span().start().utf8Offset()),
            Math.toIntExact(call.span().end().utf8Offset())),
        StandardCharsets.UTF_8);
  }
}
