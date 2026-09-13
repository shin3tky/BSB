package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import jp.bsb.frontend.ast.Comment;
import jp.bsb.frontend.ast.Literal;
import jp.bsb.frontend.ast.Particle;
import jp.bsb.frontend.ast.WordCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ParserTest {
  @Test
  void representsAnEmptyWordBodyAsAnEmptyList() throws IOException {
    var parsed = ParserTestSupport.parseResource("CORE-N019.bsb");

    assertTrue(parsed.parseResult().successful());
    assertTrue(parsed.parseResult().programForAnalysis().definitions().getFirst().body().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"CORE-N002.bsb", "CORE-N003.bsb", "CORE-N004.bsb"})
  void acceptsForwardReferencesAndRecursionAsSyntax(String sourceName) throws IOException {
    var parsed = ParserTestSupport.parseResource(sourceName);

    assertTrue(parsed.parseResult().successful(), sourceName);
    assertTrue(parsed.diagnostics().diagnostics().isEmpty(), sourceName);
  }

  @Test
  void mapsFullwidthAndAsciiParenthesesToTheSameStackEffectStructure() {
    var fullwidth = ParserTestSupport.parseText("全角.bsb", "メインとは （整数 -- 文字列）\nこと。\n");
    var ascii = ParserTestSupport.parseText("ASCII.bsb", "メインとは (整数 -- 文字列)\nこと。\n");

    var fullwidthEffect =
        fullwidth.parseResult().programForAnalysis().definitions().getFirst().stackEffect();
    var asciiEffect =
        ascii.parseResult().programForAnalysis().definitions().getFirst().stackEffect();
    assertEquals(
        fullwidthEffect.inputTypes().stream().map(type -> type.name()).toList(),
        asciiEffect.inputTypes().stream().map(type -> type.name()).toList());
    assertEquals(
        fullwidthEffect.outputTypes().stream().map(type -> type.name()).toList(),
        asciiEffect.outputTypes().stream().map(type -> type.name()).toList());
  }

  @Test
  void keepsLiteralCallParticleAndCommentsForLaterFeatures() throws IOException {
    var parsed = ParserTestSupport.parseResource("CORE-N010.bsb");
    var program = parsed.parseResult().programForAnalysis();
    var body = program.definitions().getFirst().body();

    assertInstanceOf(Comment.class, program.elements().getFirst());
    assertInstanceOf(Comment.class, body.get(0));
    assertInstanceOf(Literal.class, body.get(1));
    assertInstanceOf(Particle.class, body.get(2));
    assertInstanceOf(WordCall.class, body.get(3));
    assertInstanceOf(Comment.class, body.get(4));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "CORE-N001.bsb",
        "CORE-N002.bsb",
        "CORE-N003.bsb",
        "CORE-N004.bsb",
        "CORE-N005.bsb",
        "CORE-N006.bsb",
        "CORE-N008.bsb",
        "CORE-N009.bsb",
        "CORE-N010.bsb",
        "CORE-N011.bsb",
        "CORE-N012.bsb",
        "CORE-N013.bsb",
        "CORE-N014.bsb",
        "CORE-N015.bsb",
        "CORE-N016.bsb",
        "CORE-N017.bsb",
        "CORE-N018.bsb",
        "CORE-N019.bsb",
        "CORE-N020.bsb",
        "CORE-N022.bsb"
      })
  void parsesEveryTextBackedNormalConformanceCase(String sourceName) throws IOException {
    var parsed = ParserTestSupport.parseResource(sourceName);

    assertTrue(parsed.parseResult().successful(), sourceName);
    assertTrue(parsed.diagnostics().diagnostics().isEmpty(), sourceName);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "CORE-F016.bsb",
        "CORE-F017.bsb",
        "CORE-F018.bsb",
        "CORE-F019.bsb",
        "CORE-F020.bsb",
        "CORE-F020b.bsb",
        "CORE-F021.bsb",
        "CORE-F022.bsb",
        "CORE-F023.bsb",
        "CORE-F024.bsb",
        "CORE-F025.bsb",
        "CORE-F025b.bsb",
        "CORE-F026.bsb"
      })
  void leavesStaticFailuresForLaterFeatures(String sourceName) throws IOException {
    var parsed = ParserTestSupport.parseResource(sourceName);

    assertTrue(parsed.parseResult().successful(), sourceName);
    assertTrue(parsed.diagnostics().diagnostics().isEmpty(), sourceName);
  }

  @Test
  void parsesEverySpecifiedPhysicalLineEnding() throws IOException {
    String lf = ParserTestSupport.resourceText("CORE-N001.bsb");
    for (String lineEnding : List.of("\n", "\r", "\r\n")) {
      var parsed = ParserTestSupport.parseText("CORE-N007.bsb", lf.replace("\n", lineEnding));
      assertTrue(parsed.parseResult().successful(), escape(lineEnding));
    }
  }

  @Test
  void parsesASourceWithALeadingBom() throws IOException {
    byte[] source =
        ParserTestSupport.resourceText("CORE-N001.bsb")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] bomSource = new byte[source.length + 3];
    bomSource[0] = (byte) 0xEF;
    bomSource[1] = (byte) 0xBB;
    bomSource[2] = (byte) 0xBF;
    System.arraycopy(source, 0, bomSource, 3, source.length);

    var parsed = ParserTestSupport.parseBytes("CORE-N021.bsb", bomSource);

    assertTrue(parsed.parseResult().successful());
    assertEquals(3, parsed.parseResult().programForAnalysis().span().start().utf8Offset());
  }

  @Test
  void preventsASyntaxErrorAstFromEnteringTheNextPhase() throws IOException {
    var parsed = ParserTestSupport.parseResource("CORE-F011.bsb");

    assertThrows(IllegalStateException.class, parsed.parseResult()::programForAnalysis);
  }

  private static String escape(String value) {
    return value.replace("\r", "\\r").replace("\n", "\\n");
  }
}
