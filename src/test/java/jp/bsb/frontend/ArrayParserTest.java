package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import jp.bsb.frontend.ast.ArrayLiteral;
import jp.bsb.frontend.ast.ArrayLoop;
import jp.bsb.frontend.ast.Comment;
import jp.bsb.frontend.ast.TypeReference;
import jp.bsb.frontend.ast.ValueDeclaration;
import jp.bsb.frontend.ast.ValueReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ArrayParserTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "ARRAY-N001.bsb", "ARRAY-N002.bsb", "ARRAY-N003.bsb", "ARRAY-N004.bsb", "ARRAY-N005.bsb",
        "ARRAY-N006.bsb", "ARRAY-N007.bsb", "ARRAY-N008.bsb", "ARRAY-N009.bsb", "ARRAY-N010.bsb",
        "ARRAY-N011.bsb", "ARRAY-N012.bsb", "ARRAY-N013.bsb", "ARRAY-N014.bsb", "ARRAY-N015.bsb",
        "ARRAY-N016.bsb", "ARRAY-N017.bsb", "ARRAY-N018.bsb", "ARRAY-N019.bsb", "ARRAY-N020.bsb",
        "ARRAY-N021.bsb", "ARRAY-F009.bsb", "ARRAY-F010.bsb", "ARRAY-F011.bsb", "ARRAY-F012.bsb",
        "ARRAY-F013.bsb", "ARRAY-F014.bsb", "ARRAY-F015.bsb", "ARRAY-F017.bsb", "ARRAY-F018.bsb",
        "ARRAY-F019.bsb", "ARRAY-F020.bsb", "ARRAY-F021.bsb", "ARRAY-F022.bsb", "ARRAY-F023.bsb",
        "ARRAY-F024.bsb", "ARRAY-F025.bsb", "ARRAY-F026.bsb", "ARRAY-F027.bsb", "ARRAY-F028.bsb",
        "ARRAY-F029.bsb", "ARRAY-F030.bsb", "ARRAY-F031.bsb", "ARRAY-F032.bsb", "ARRAY-F033.bsb"
      })
  void parsesEveryArraySourceWhoseFailureIsNotSyntactic(String sourceName) throws IOException {
    var parsed = ParserTestSupport.parseArrayResource(sourceName);

    assertTrue(parsed.parseResult().successful(), sourceName + ": " + parsed.diagnostics());
    assertTrue(parsed.diagnostics().diagnostics().isEmpty(), sourceName);
  }

  @Test
  void parsesTheNormativeArrayChapter() throws IOException {
    var parsed = ParserTestSupport.parseArrayChapter("arrays-chapter.bsb");

    assertTrue(parsed.parseResult().successful(), parsed.diagnostics().toString());
    assertEquals(3, parsed.parseResult().programForAnalysis().declarations().size());
    assertEquals(
        2,
        parsed.parseResult().programForAnalysis().definitions().getFirst().body().stream()
            .filter(ArrayLoop.class::isInstance)
            .count());
  }

  @Test
  void preservesArrayElementBodiesCommentsAndSpans() throws IOException {
    var parsed = ParserTestSupport.parseArrayResource("ARRAY-N004.bsb");
    var program = parsed.parseResult().programForAnalysis();
    ValueDeclaration points = assertInstanceOf(ValueDeclaration.class, program.elements().get(1));
    ArrayLiteral array = assertInstanceOf(ArrayLiteral.class, points.initializer().getFirst());

    assertEquals(2, array.elements().size());
    assertInstanceOf(ValueReference.class, array.elements().getFirst().body().getFirst());
    assertEquals(5, array.elements().get(1).body().size());
    assertEquals(2, array.openingSpan().start().line());
    assertEquals(2, array.endSpan().end().line());
    assertEquals(array.openingSpan().start(), array.span().start());
    assertEquals(array.endSpan().end(), array.span().end());

    var commented = ParserTestSupport.parseArrayResource("ARRAY-N018.bsb");
    ValueDeclaration declaration =
        assertInstanceOf(
            ValueDeclaration.class,
            commented.parseResult().programForAnalysis().elements().getFirst());
    ArrayLiteral commentedArray =
        assertInstanceOf(ArrayLiteral.class, declaration.initializer().getFirst());
    assertInstanceOf(Comment.class, commentedArray.elements().get(1).body().getFirst());
    assertInstanceOf(Comment.class, commentedArray.elements().get(2).body().getFirst());
  }

  @Test
  void associatesTheOptionalInputParticleWithTheArrayLoop() throws IOException {
    var parsed = ParserTestSupport.parseArrayResource("ARRAY-N013.bsb");
    var body = parsed.parseResult().programForAnalysis().definitions().getFirst().body();

    assertEquals(2, body.size());
    assertInstanceOf(ArrayLiteral.class, body.getFirst());
    ArrayLoop loop = assertInstanceOf(ArrayLoop.class, body.getLast());
    assertEquals("を", loop.inputParticle().orElseThrow().name());
    assertEquals(1, loop.body().size());

    var withoutParticle = ParserTestSupport.parseArrayResource("ARRAY-F024.bsb");
    ArrayLoop bareLoop =
        assertInstanceOf(
            ArrayLoop.class,
            withoutParticle
                .parseResult()
                .programForAnalysis()
                .definitions()
                .getFirst()
                .body()
                .getFirst());
    assertTrue(bareLoop.inputParticle().isEmpty());
  }

  @Test
  void buildsRecursiveArrayTypeReferencesWithoutTreatingConstraintsAsTypes() throws IOException {
    var concrete = ParserTestSupport.parseArrayResource("ARRAY-N012.bsb");
    TypeReference arrayType =
        concrete
            .parseResult()
            .programForAnalysis()
            .definitions()
            .getFirst()
            .stackEffect()
            .inputTypes()
            .getFirst();

    assertTrue(arrayType.isArray());
    assertEquals("配列<整数>", arrayType.name());
    assertEquals("配列 < 整数 >", arrayType.lexeme());
    assertEquals("整数", arrayType.elementType().orElseThrow().name());

    var nested = ParserTestSupport.parseArrayResource("ARRAY-F018.bsb");
    TypeReference nestedType =
        nested
            .parseResult()
            .programForAnalysis()
            .definitions()
            .getFirst()
            .stackEffect()
            .inputTypes()
            .getFirst();
    assertEquals("配列<配列<配列<整数>>>", nestedType.name());
    assertTrue(nestedType.elementType().orElseThrow().isArray());
  }
}
