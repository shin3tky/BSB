package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;
import jp.bsb.diagnostics.DiagnosticCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ArrayParserDiagnosticTest {
  @ParameterizedTest
  @MethodSource("syntaxFailures")
  void matchesEveryArraySyntaxFailure(
      String sourceName,
      DiagnosticCode code,
      int line,
      int column,
      Map<String, String> fields,
      String expected,
      String actual,
      String fix)
      throws IOException {
    var parsed = ParserTestSupport.parseArrayResource(sourceName);

    assertFalse(parsed.parseResult().successful(), sourceName);
    assertEquals(1, parsed.diagnostics().diagnostics().size(), sourceName);
    var diagnostic = parsed.diagnostics().diagnostics().getFirst();
    assertEquals(code, diagnostic.code(), sourceName);
    assertEquals(line, diagnostic.location().displayPosition().orElseThrow().line(), sourceName);
    assertEquals(
        column, diagnostic.location().displayPosition().orElseThrow().column(), sourceName);
    assertEquals(fields, diagnostic.fields(), sourceName);
    assertEquals(expected, diagnostic.expected().orElseThrow(), sourceName);
    assertEquals(actual, diagnostic.actual().orElseThrow(), sourceName);
    assertEquals(java.util.List.of(fix), diagnostic.fixes(), sourceName);
    assertThrows(IllegalStateException.class, parsed.parseResult()::programForAnalysis);
  }

  @Test
  void recoversAfterAMissingArrayEndAtTheNextDefinition() {
    var parsed =
        ParserTestSupport.parseText("配列回復.bsb", "壊れたとは （--）\n    【1、2\nこと。\n\n次とは （--）\nこと。\n");

    assertFalse(parsed.parseResult().successful());
    assertEquals(
        java.util.List.of("壊れた", "次"),
        parsed.parseResult().partialProgram().definitions().stream()
            .map(word -> word.name())
            .toList());
    assertEquals(
        java.util.List.of(DiagnosticCode.E_EXPECTED_ARRAY_END),
        parsed.diagnostics().diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
  }

  @Test
  void sharesTheSyntaxDepthLimitAcrossArraysAndControls() {
    String accepted = "メインとは （--）\n" + "    " + "【".repeat(256) + "1" + "】".repeat(256) + "\nこと。\n";
    String rejected =
        accepted
            .replace("【".repeat(256), "【".repeat(257))
            .replace("】".repeat(256), "】".repeat(257));

    var acceptedResult = ParserTestSupport.parseText("深さ256.bsb", accepted);
    var rejectedResult = ParserTestSupport.parseText("深さ257.bsb", rejected);

    assertEquals(0, acceptedResult.diagnostics().diagnostics().size());
    assertEquals(
        java.util.List.of(DiagnosticCode.E_SYNTAX_DEPTH_LIMIT),
        rejectedResult.diagnostics().diagnostics().stream()
            .map(diagnostic -> diagnostic.code())
            .toList());

    String mixedAccepted = nestedConditionalWithArray(255, 1);
    String mixedRejected = nestedConditionalWithArray(255, 2);
    assertEquals(
        java.util.List.of(),
        ParserTestSupport.parseText("混合深さ256.bsb", mixedAccepted).diagnostics().diagnostics());
    assertEquals(
        java.util.List.of(DiagnosticCode.E_SYNTAX_DEPTH_LIMIT),
        ParserTestSupport.parseText("混合深さ257.bsb", mixedRejected)
            .diagnostics()
            .diagnostics()
            .stream()
            .map(diagnostic -> diagnostic.code())
            .toList());
  }

  private static String nestedConditionalWithArray(int conditionDepth, int arrayDepth) {
    return "メインとは （--）\n"
        + "はい ならば\n".repeat(conditionDepth)
        + "【".repeat(arrayDepth)
        + "1"
        + "】".repeat(arrayDepth)
        + "\nつぎに\n".repeat(conditionDepth)
        + "こと。\n";
  }

  private static Stream<Arguments> syntaxFailures() {
    return Stream.of(
        Arguments.of(
            "ARRAY-F001.bsb",
            DiagnosticCode.E_EXPECTED_ARRAY_END,
            3,
            1,
            Map.of("startLine", "2", "startColumn", "5"),
            "】",
            "こと。",
            "ここに「】」を追加してください"),
        Arguments.of(
            "ARRAY-F002.bsb",
            DiagnosticCode.E_UNEXPECTED_ARRAY_END,
            2,
            5,
            Map.of("actual", "】"),
            "配列の内側",
            "】",
            "この記号を削除してください"),
        Arguments.of(
            "ARRAY-F003.bsb",
            DiagnosticCode.E_EXPECTED_ARRAY_ELEMENT,
            2,
            6,
            Map.of("elementIndex", "1", "startLine", "2", "startColumn", "5"),
            "配列要素",
            "、",
            "区切りの前に要素を追加してください"),
        Arguments.of(
            "ARRAY-F004.bsb",
            DiagnosticCode.E_EXPECTED_ARRAY_ELEMENT,
            2,
            8,
            Map.of("elementIndex", "2", "startLine", "2", "startColumn", "5"),
            "配列要素",
            "、",
            "重複する区切りを削除してください"),
        Arguments.of(
            "ARRAY-F005.bsb",
            DiagnosticCode.E_EXPECTED_ARRAY_ELEMENT,
            2,
            8,
            Map.of("elementIndex", "2", "startLine", "2", "startColumn", "5"),
            "配列要素",
            "】",
            "末尾の区切りを削除してください"),
        Arguments.of(
            "ARRAY-F006.bsb",
            DiagnosticCode.E_EXPECTED_ARRAY_ELEMENT_TYPE,
            1,
            10,
            Map.of("allowedTypes", "整数,真偽,文字,文字列"),
            "要素型",
            ">",
            "4種類の要素型から1つ追加してください"),
        Arguments.of(
            "ARRAY-F007.bsb",
            DiagnosticCode.E_EXPECTED_ARRAY_TYPE_END,
            1,
            13,
            Map.of("startLine", "1", "startColumn", "7"),
            ">",
            "--",
            "「整数」の後に「>」を追加してください"),
        Arguments.of(
            "ARRAY-F008.bsb",
            DiagnosticCode.E_EXPECTED_LOOP_END,
            4,
            1,
            Map.of("loopKind", "配列", "startLine", "2", "startColumn", "10"),
            "繰り返す",
            "こと。",
            "ここに「繰り返す」を追加してください"),
        Arguments.of(
            "ARRAY-F016.bsb",
            DiagnosticCode.E_ARRAY_ELEMENT_NOT_ALLOWED,
            2,
            9,
            Map.of("elementIndex", "1", "element", "ならば"),
            "配列要素式で許可された要素",
            "ならば",
            "条件分岐を配列リテラルの外へ移動してください"));
  }
}
