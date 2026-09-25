package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.explain.ProgramExplainer;
import jp.bsb.format.SourceFormatter;
import org.junit.jupiter.api.Test;

class JsonPublishedContractConformanceTest {
  @Test
  void jsonN028KeepsJsonTypeNamesAndCanonicalWordsIdempotent() throws Exception {
    byte[] expected = JsonConformanceData.resourceBytes("canonical/JSON-N028.bsb");
    var formatter = new SourceFormatter();
    var first = formatter.format("JSON-N028.bsb", expected);
    var second =
        formatter.format(
            "JSON-N028-second.bsb",
            first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));

    assertTrue(first.successful(), first.diagnostics().toString());
    assertTrue(second.successful(), second.diagnostics().toString());
    assertArrayEquals(expected, first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));
    assertEquals(first.outputForStandardOutput(), second.outputForStandardOutput());
  }

  @Test
  void jsonN029PublishesExactlyThirtyThreeFixedPureJsonFeatureWords() throws Exception {
    byte[] source = JsonConformanceData.resourceBytes("canonical/JSON-N028.bsb");
    var analysis = new SourceChecker().check("JSON-N029.bsb", source);
    assertTrue(analysis.successful(), analysis.diagnostics().toString());
    var explanation = new ProgramExplainer().explain(analysis.programForIrGeneration());
    var jsonFeature =
        explanation.builtinWords().stream()
            .filter(word -> word.featureGroup().equals("JSON"))
            .toList();

    assertEquals(217, explanation.builtinWords().size());
    assertEquals(33, jsonFeature.size());
    assertTrue(jsonFeature.stream().allMatch(word -> word.typeRule().equals("fixed")));
    assertTrue(jsonFeature.stream().allMatch(word -> word.capabilities().isEmpty()));
    assertTrue(jsonFeature.stream().allMatch(word -> word.effects().isEmpty()));
  }
}
