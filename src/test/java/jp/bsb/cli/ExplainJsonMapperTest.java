package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.explain.ProgramExplainer;
import org.junit.jupiter.api.Test;

class ExplainJsonMapperTest {
  @Test
  void mapsExplanationLocationsFromTheAnalysisSnapshot() {
    String sourceText = "値は 定数 1。\r\n" + "メインとは （--）\r\n" + "\t値 を 一行表示する\r\n" + "こと。\r\n";
    var analysis = new SourceChecker().check("位置.bsb", sourceText.getBytes(StandardCharsets.UTF_8));
    assertTrue(analysis.successful(), analysis.diagnostics().toString());
    var explanation = new ProgramExplainer().explain(analysis.programForIrGeneration());

    ExplainJson mapped = new ExplainJsonMapper().map(explanation, analysis.source().orElseThrow());

    assertEquals(213, mapped.builtinWords().size());
    assertEquals(2, mapped.userWords().getFirst().declaration().start().line());
    assertEquals(1, mapped.bindings().getFirst().declaration().start().line());
    assertEquals(3, mapped.bindings().getFirst().uses().getFirst().location().start().line());
    assertEquals(5, mapped.bindings().getFirst().uses().getFirst().location().start().column());
  }

  @Test
  void mapsLogicalConnectionLocationsIntoTheIndependentInnerSchema() {
    String sourceText = "接続Aは 論理接続。\nメインとは （--）\n    論理接続を確認する<接続A>\nこと。\n";
    var analysis = new SourceChecker().check("接続.bsb", sourceText.getBytes(StandardCharsets.UTF_8));
    assertTrue(analysis.successful(), analysis.diagnostics().toString());

    ExplainJson mapped =
        new ExplainJsonMapper()
            .map(
                new ProgramExplainer().explain(analysis.programForIrGeneration()),
                analysis.source().orElseThrow());

    var parameterized = mapped.parameterizedCapabilities();
    assertEquals(1, parameterized.schemaVersion());
    assertEquals(1, parameterized.declarations().size());
    assertEquals(1, parameterized.declarations().getFirst().declaration().start().line());
    assertEquals(
        3, parameterized.declarations().getFirst().uses().getFirst().location().start().line());
    assertEquals("logicalConnection", parameterized.summaryRequirements().getFirst().kind());
  }
}
