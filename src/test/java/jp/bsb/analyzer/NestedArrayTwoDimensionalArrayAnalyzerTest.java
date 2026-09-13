package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 多次元配列の2次元型、literal、汎用配列操作、入れ子loopの静的契約を検証します。 */
class NestedArrayTwoDimensionalArrayAnalyzerTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "NARRAY-N-types.bsb",
        "NARRAY-N-ragged.bsb",
        "NARRAY-N-operations.bsb",
        "NARRAY-N-loops.bsb",
        "NARRAY-N-wrappers.bsb"
      })
  void acceptsEveryPublicNormalSource(String sourceName) throws IOException {
    AnalysisResult result = check(sourceName);

    assertTrue(result.successful(), sourceName + result.diagnostics());
    assertTrue(result.diagnostics().isEmpty(), sourceName + result.diagnostics());
    assertTrue(result.programForIrGeneration().findUserWord("メイン").isPresent());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "NARRAY-F-three-dimensional-literal.bsb:E_NESTED_ARRAY_NOT_AVAILABLE",
        "NARRAY-F-three-dimensional-type.bsb:E_NESTED_ARRAY_NOT_AVAILABLE",
        "NARRAY-F-mixed-leaves.bsb:E_ARRAY_ELEMENT_TYPE_MISMATCH",
        "NARRAY-F-one-two-dimensional-mismatch.bsb:E_TYPE_MISMATCH",
        "NARRAY-F-optional-element.bsb:E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED",
        "NARRAY-F-result-element.bsb:E_ARRAY_ELEMENT_TYPE_NOT_ALLOWED",
        "NARRAY-F-empty-outer.bsb:E_EMPTY_ARRAY_TYPE_REQUIRED",
        "NARRAY-F-empty-inner.bsb:E_EMPTY_ARRAY_TYPE_REQUIRED",
        "NARRAY-F-replace-row-type.bsb:E_TYPE_MISMATCH",
        "NARRAY-F-append-row-type.bsb:E_TYPE_MISMATCH",
        "NARRAY-F-missing-inner-end.bsb:E_EXPECTED_ARRAY_TYPE_END"
      })
  void selectsTheSpecifiedPrimaryDiagnostic(String specification) throws IOException {
    String[] parts = specification.split(":", 2);
    AnalysisResult result = check(parts[0]);

    assertFalse(result.successful(), parts[0]);
    assertEquals(1, result.diagnostics().size(), parts[0] + result.diagnostics());
    assertEquals(DiagnosticCode.valueOf(parts[1]), result.diagnostics().getFirst().code());
  }

  @ParameterizedTest
  @ValueSource(strings = {"整数", "真偽", "文字", "文字列", "小数", "JSON"})
  void resolvesAllSixLeafFamilies(String leafName) {
    ValueType expected =
        ValueType.arrayOf(ValueType.arrayOf(ValueType.fromSourceName(leafName).orElseThrow()));
    AnalysisResult result =
        new SourceChecker()
            .check(
                "leaf.bsb",
                ("保つとは （配列<配列<"
                        + leafName
                        + ">> -- 配列<配列<"
                        + leafName
                        + ">>）\nこと。\n\nメインとは （--）\nこと。\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));

    assertEquals(
        new WordSignature(java.util.List.of(expected), java.util.List.of(expected)),
        result.programForIrGeneration().findUserWord("保つ").orElseThrow());
  }

  private static AnalysisResult check(String sourceName) throws IOException {
    String resource = "/conformance/nested-arrays/sources/" + sourceName;
    try (var input =
        NestedArrayTwoDimensionalArrayAnalyzerTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("missing test resource: " + resource);
      }
      return new SourceChecker().check(sourceName, input.readAllBytes());
    }
  }
}
