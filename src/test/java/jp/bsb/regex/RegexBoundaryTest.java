package jp.bsb.regex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import jp.bsb.diagnostics.DiagnosticCode;
import org.junit.jupiter.api.Test;

class RegexBoundaryTest {
  @Test
  void exposesOnlyBsbOwnedTypesAtTheCompilerBoundary() {
    List<Class<?>> boundaryTypes =
        List.of(
            RegexCompiler.class,
            RegexProgram.class,
            RegexMatch.class,
            RegexMatchCursor.class,
            RegexCompilationResult.class,
            RegexCompilationResult.Success.class,
            RegexCompilationResult.Failure.class);

    boolean leaksEngineType =
        boundaryTypes.stream()
            .flatMap(type -> Stream.of(type.getMethods()))
            .flatMap(RegexBoundaryTest::signatureTypes)
            .map(Class::getName)
            .anyMatch(name -> name.startsWith("com.google.re2j."));

    assertFalse(leaksEngineType);
  }

  @Test
  void acceptsOnlyRegexCompilationDiagnostics() {
    var failure =
        new RegexCompilationResult.Failure(
            DiagnosticCode.E_REGEX_SYNTAX, 3, Map.of("reason", "quantifierLimit"));

    assertEquals(DiagnosticCode.E_REGEX_SYNTAX, failure.code());
    assertEquals(3, failure.patternOffset());
    assertEquals(Map.of("reason", "quantifierLimit"), failure.fields());
    assertThrows(
        IllegalArgumentException.class,
        () -> new RegexCompilationResult.Failure(DiagnosticCode.E_TYPE_MISMATCH, 0, Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new RegexCompilationResult.Failure(DiagnosticCode.E_REGEX_SYNTAX, -1, Map.of()));
  }

  private static Stream<Class<?>> signatureTypes(Method method) {
    return Stream.concat(Stream.of(method.getReturnType()), Stream.of(method.getParameterTypes()));
  }
}
