package jp.bsb.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import jp.bsb.binding.DeclaredNameKind;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.frontend.ast.Program;
import jp.bsb.frontend.ast.StackEffect;
import jp.bsb.frontend.ast.WordCall;
import jp.bsb.frontend.ast.WordDefinition;
import org.junit.jupiter.api.Test;

class AnalyzedProgramTest {
  @Test
  void compatibilityConstructorRegistersWordsAndKeepsUnreachableIdentity() {
    WordCall unreachable = new WordCall("改行する", "改行する", span(40, 55));
    WordCall equalButDistinct = new WordCall("改行する", "改行する", span(40, 55));
    WordDefinition main =
        new WordDefinition(
            "メイン",
            "メイン",
            span(0, 9),
            new StackEffect(List.of(), List.of(), span(10, 14)),
            List.of(unreachable),
            span(56, 65),
            span(0, 65));
    Program syntax = new Program("入力.bsb", List.of(main), span(0, 65));

    var analyzed =
        new AnalyzedProgram(
            syntax, Map.of("メイン", new WordSignature(List.of(), List.of())), Set.of(unreachable));

    var declaredMain = analyzed.nameResolution().findDeclaration("メイン").orElseThrow();
    assertEquals(DeclaredNameKind.WORD, declaredMain.declarationKind());
    assertSame(main.nameSpan(), declaredMain.nameSpan());
    assertFalse(analyzed.isReachable(unreachable));
    assertTrue(analyzed.isReachable(equalButDistinct));
    assertTrue(analyzed.nameResolution().bindings().isEmpty());
  }

  private static SourceSpan span(long start, long end) {
    return new SourceSpan(
        new SourcePosition(start, 1, Math.toIntExact(start + 1)),
        new SourcePosition(end, 1, Math.toIntExact(end + 1)));
  }
}
