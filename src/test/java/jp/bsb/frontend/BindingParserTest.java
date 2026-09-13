package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import jp.bsb.binding.BindingKind;
import jp.bsb.frontend.ast.Assignment;
import jp.bsb.frontend.ast.Comment;
import jp.bsb.frontend.ast.Conditional;
import jp.bsb.frontend.ast.ValueDeclaration;
import jp.bsb.frontend.ast.ValueReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BindingParserTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "BIND-N001.bsb",
        "BIND-N002.bsb",
        "BIND-N003.bsb",
        "BIND-N004.bsb",
        "BIND-N005.bsb",
        "BIND-N006.bsb",
        "BIND-N007.bsb",
        "BIND-N008.bsb",
        "BIND-N009.bsb",
        "BIND-N010.bsb",
        "BIND-N011.bsb",
        "BIND-N012.bsb",
        "BIND-N013.bsb",
        "BIND-N014.bsb",
        "BIND-N015.bsb",
        "BIND-N016.bsb",
        "BIND-N017.bsb",
        "BIND-N018.bsb",
        "BIND-N019.bsb",
        "BIND-F009.bsb",
        "BIND-F010.bsb",
        "BIND-F011.bsb",
        "BIND-F013.bsb",
        "BIND-F014.bsb",
        "BIND-F015.bsb",
        "BIND-F016.bsb",
        "BIND-F017.bsb",
        "BIND-F018.bsb",
        "BIND-F019.bsb",
        "BIND-F020.bsb",
        "BIND-F021.bsb",
        "BIND-F022.bsb",
        "BIND-F023.bsb",
        "BIND-F024.bsb",
        "BIND-F025.bsb",
        "BIND-F026.bsb",
        "BIND-F027.bsb",
        "BIND-F028.bsb",
        "BIND-F029.bsb",
        "BIND-F030.bsb"
      })
  void parsesEveryBindingSourceWhoseFailureIsNotSyntactic(String sourceName) throws IOException {
    var parsed = ParserTestSupport.parseBindingResource(sourceName);

    assertTrue(parsed.parseResult().successful(), sourceName);
    assertTrue(parsed.diagnostics().diagnostics().isEmpty(), sourceName);
  }

  @Test
  void preservesGlobalLocalControlInitializerReferencesAndAssignment() {
    String source =
        "大域は 変数 1。\n\n"
            + "メインとは （--）\n"
            + "    局所は 定数 大域 # 初期値\n"
            + "    。\n"
            + "    はい ならば\n"
            + "        2 を 大域 に 入れる\n"
            + "        局所\n"
            + "    つぎに\n"
            + "こと。\n";
    var parsed = ParserTestSupport.parseText("AST.bsb", source);

    assertTrue(parsed.parseResult().successful());
    var program = parsed.parseResult().programForAnalysis();
    ValueDeclaration global = assertInstanceOf(ValueDeclaration.class, program.elements().get(0));
    assertEquals(BindingKind.VARIABLE, global.kind());
    var body = program.definitions().getFirst().body();
    ValueDeclaration local = assertInstanceOf(ValueDeclaration.class, body.get(0));
    assertEquals(BindingKind.CONSTANT, local.kind());
    assertInstanceOf(ValueReference.class, local.initializer().get(0));
    assertInstanceOf(Comment.class, local.initializer().get(1));
    Conditional conditional = assertInstanceOf(Conditional.class, body.get(2));
    Assignment assignment = assertInstanceOf(Assignment.class, conditional.trueBody().get(1));
    assertEquals("大域", assignment.targetName());
    assertInstanceOf(ValueReference.class, conditional.trueBody().get(2));
    assertEquals(1, global.nameSpan().start().line());
    assertEquals(5, local.endSpan().start().line());
  }

  @Test
  void turnsForwardAndOutOfScopeValueNamesIntoReferences() {
    String source =
        "メインとは （--）\n"
            + "    後\n"
            + "    はい ならば\n"
            + "        内は 定数 1。\n"
            + "    つぎに\n"
            + "    内\n"
            + "    後は 変数 0。\n"
            + "こと。\n";
    var parsed = ParserTestSupport.parseText("可視性.bsb", source);

    assertTrue(parsed.parseResult().successful());
    var body = parsed.parseResult().programForAnalysis().definitions().getFirst().body();
    assertInstanceOf(ValueReference.class, body.get(0));
    assertInstanceOf(ValueReference.class, body.get(3));
  }

  @Test
  void parsesTheNormativeBindingChapterProgram() throws IOException {
    var parsed = ParserTestSupport.parseBindingChapter("bindings-chapter.bsb");

    assertTrue(parsed.parseResult().successful());
    var program = parsed.parseResult().programForAnalysis();
    assertEquals(3, program.declarations().size());
    assertEquals(2, program.definitions().size());
  }
}
