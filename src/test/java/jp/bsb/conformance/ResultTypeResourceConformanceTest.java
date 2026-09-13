package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import jp.bsb.analyzer.AnalysisResult;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.format.SourceFormatter;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class ResultTypeResourceConformanceTest {
  private static final String EMPTY_MAIN = "メインとは （--）\nこと。\n";

  @Test
  void enforcesEveryResultAndConstructorDepthBoundaryFromResultR001() {
    for (String shape : List.of("success", "failure", "mixed")) {
      for (int depth : List.of(256, 257)) {
        String type = chain(depth, shape);
        String source = signature(type, depth == 256);
        assertBoundary("RESULT-R001-" + shape + "-" + depth, source, depth == 256);
      }
    }

    for (String owner : List.of("成功にする", "失敗にする")) {
      for (String side : List.of("left", "right")) {
        for (int depth : List.of(256, 257)) {
          String inner = chain(depth - 1, "success");
          String left = side.equals("left") ? inner : "整数";
          String right = side.equals("right") ? inner : "整数";
          String selected = owner.equals("成功にする") ? left : right;
          String source =
              depth == 256
                  ? "包むとは （"
                      + selected
                      + " -- 結果<"
                      + left
                      + ","
                      + right
                      + ">）\n    "
                      + owner
                      + "<"
                      + left
                      + ","
                      + right
                      + ">\nこと。\n\n"
                      + EMPTY_MAIN
                  : "メインとは （--）\n    " + owner + "<" + left + "," + right + ">\nこと。\n";
          assertBoundary(
              "RESULT-R001-construct-" + owner + "-" + side + "-" + depth, source, depth == 256);
        }
      }
    }
  }

  @Test
  void enforcesInferredOptionalDepthAcrossPureAndMixedPathsFromResultR002() {
    for (String shape : List.of("optional", "mixed")) {
      for (int inputDepth : List.of(255, 256)) {
        String source =
            "包むとは （"
                + chain(inputDepth, shape)
                + " --）\n"
                + "    任意にする\n"
                + "    任意を捨てる\n"
                + "こと。\n\n"
                + EMPTY_MAIN;
        AnalysisResult result = check("RESULT-R002-" + shape + "-" + inputDepth, source);
        if (inputDepth == 255) {
          assertTrue(result.successful(), result.diagnostics().toString());
        } else {
          assertEquals(8, result.exitCode());
          assertEquals(
              List.of(DiagnosticCode.E_TYPE_DEPTH_LIMIT),
              result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
          assertEquals("256", result.diagnostics().getFirst().limit().orElseThrow());
          assertEquals("257", result.diagnostics().getFirst().observed().orElseThrow());
        }
        assertTrue(
            new SourceFormatter()
                .format(path("RESULT-R002-" + shape + "-" + inputDepth), bytes(source))
                .successful());
      }
    }
  }

  @Test
  void handlesDeepValuesAndWideSharedOrExpandedTypesFromResultR003AndResultR004() {
    for (String shape : List.of("success", "failure", "mixed")) {
      ValueType type = ValueType.fromSourceName(chain(256, shape)).orElseThrow();
      assertEquals(256, ValueType.constructorDepth(type));
    }
    ValueType inactiveFailure =
        ValueType.fromSourceName("結果<整数," + chain(255, "success") + ">").orElseThrow();
    ValueType inactiveSuccess =
        ValueType.fromSourceName("結果<" + chain(255, "success") + ",整数>").orElseThrow();
    assertEquals(256, ValueType.constructorDepth(inactiveFailure));
    assertEquals(256, ValueType.constructorDepth(inactiveSuccess));

    String wide = "整数";
    for (int depth = 0; depth < 15; depth++) {
      wide = "結果<" + wide + "," + wide + ">";
    }
    ValueType expanded = ValueType.fromSourceName(wide).orElseThrow();
    ValueType shared = ValueType.INTEGER;
    for (int depth = 0; depth < 15; depth++) {
      shared = ValueType.resultOf(shared, shared);
    }
    assertEquals(expanded, shared);
    assertEquals(expanded.hashCode(), shared.hashCode());
    assertEquals(15, ValueType.constructorDepth(expanded));

    String source = "捨てるとは （" + wide + " --）\n    結果を捨てる\nこと。\n\n" + EMPTY_MAIN;
    AnalysisResult checked = check("RESULT-R004-wide-source", source);
    assertTrue(checked.successful(), checked.diagnostics().toString());
    assertEquals(
        source,
        new SourceFormatter()
            .format(path("RESULT-R004-wide-source"), bytes(source))
            .outputForStandardOutput());
  }

  @Test
  void enforcesTokenDiagnosticAndRecoveryBoundariesFromResultR004AndResultR013() {
    AnalysisResult tokenLimit = check("RESULT-R004-tokens-250001", tokenBoundarySource(2));
    assertEquals(9, tokenLimit.exitCode());
    assertEquals(
        List.of(DiagnosticCode.E_TOKEN_LIMIT),
        tokenLimit.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
    assertEquals("250000", tokenLimit.diagnostics().getFirst().limit().orElseThrow());
    assertEquals("250001", tokenLimit.diagnostics().getFirst().observed().orElseThrow());

    AnalysisResult acceptedTokenBoundary =
        check("RESULT-R004-tokens-250000", tokenBoundarySource(1));
    assertTrue(acceptedTokenBoundary.successful(), acceptedTokenBoundary.diagnostics().toString());

    for (int count : List.of(99, 100, 101)) {
      AnalysisResult result =
          check("RESULT-R013-diagnostics-" + count, diagnosticLimitSource(count));
      assertEquals(9, result.exitCode());
      assertEquals(Math.min(count, 100), result.diagnostics().size());
      long regular =
          result.diagnostics().stream()
              .filter(
                  diagnostic -> diagnostic.code() == DiagnosticCode.E_EXPECTED_RESULT_TYPE_ARGUMENT)
              .count();
      long limit =
          result.diagnostics().stream()
              .filter(diagnostic -> diagnostic.code() == DiagnosticCode.E_DIAGNOSTIC_LIMIT)
              .count();
      assertEquals(Math.min(count, 99), regular);
      assertEquals(count >= 100 ? 1 : 0, limit);
    }

    AnalysisResult commaRecovery =
        check("RESULT-R013-recover-comma", "検査とは （結果<任意<>,文字列> --）\nこと。\n\n" + EMPTY_MAIN);
    assertEquals(
        List.of(DiagnosticCode.E_EXPECTED_OPTIONAL_ELEMENT_TYPE),
        commaRecovery.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());

    String depthBeforeEmpty =
        "検査とは （" + "任意<".repeat(256) + "結果<>" + ">".repeat(256) + " --）\nこと。\n\n" + EMPTY_MAIN;
    AnalysisResult depthRecovery = check("RESULT-R013-depth-before-empty", depthBeforeEmpty);
    assertEquals(
        List.of(DiagnosticCode.E_SYNTAX_DEPTH_LIMIT),
        depthRecovery.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
  }

  private static void assertBoundary(String key, String source, boolean accepted) {
    AnalysisResult result = check(key, source);
    if (accepted) {
      assertTrue(result.successful(), result.diagnostics().toString());
      assertEquals(
          source, new SourceFormatter().format(path(key), bytes(source)).outputForStandardOutput());
    } else {
      assertFalse(result.successful());
      assertEquals(9, result.exitCode());
      assertEquals(
          List.of(DiagnosticCode.E_SYNTAX_DEPTH_LIMIT),
          result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
      assertEquals("256", result.diagnostics().getFirst().limit().orElseThrow());
      assertEquals("257", result.diagnostics().getFirst().observed().orElseThrow());
    }
  }

  private static AnalysisResult check(String key, String source) {
    return new SourceChecker().check(path(key), bytes(source));
  }

  private static String path(String key) {
    return "tests/conformance/result-values/generated/" + key + ".bsb";
  }

  private static byte[] bytes(String source) {
    return source.getBytes(StandardCharsets.UTF_8);
  }

  private static String signature(String type, boolean includeOutput) {
    return "保持とは （" + type + " --" + (includeOutput ? " " + type : "") + "）\nこと。\n\n" + EMPTY_MAIN;
  }

  private static String chain(int depth, String shape) {
    String current = "整数";
    for (int level = depth; level > 0; level--) {
      boolean optional = shape.equals("optional") || shape.equals("mixed") && level % 2 == 0;
      if (optional) {
        current = "任意<" + current + ">";
      } else if (shape.equals("failure")) {
        current = "結果<真偽," + current + ">";
      } else {
        current = "結果<" + current + ",真偽>";
      }
    }
    return current;
  }

  private static String tokenBoundarySource(int newlines) {
    return "メインとは （--）\n"
        + "    42 成功にする<整数,真偽> 結果を捨てる\n".repeat(31_249)
        + "    改行する\n".repeat(newlines)
        + "こと。\n";
  }

  private static String diagnosticLimitSource(int count) {
    var source = new StringBuilder();
    for (int index = 0; index < count; index++) {
      source.append("検査").append(index).append("とは （結果<> --）\nこと。\n");
    }
    return source.append('\n').append(EMPTY_MAIN).toString();
  }
}
