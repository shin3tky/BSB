package jp.bsb.explain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import jp.bsb.analyzer.SourceChecker;
import org.junit.jupiter.api.Test;

class ByteSequenceByteSequenceExplainTest {
  @Test
  void publishesAllElevenWordsWithStableVersionOneMetadata() {
    var analysis =
        new SourceChecker()
            .check(
                "ByteSequence-explain.bsb",
                ("変換とは （文字列 -- 結果<バイト列,Base64復号失敗>）\n"
                        + "    Base64文字列をバイト列に変換して結果を返す\n"
                        + "こと。\n\n"
                        + "メインとは （--）\nこと。\n")
                    .getBytes(StandardCharsets.UTF_8));
    assertTrue(analysis.successful(), analysis.diagnostics().toString());

    ProgramExplanation explanation =
        new ProgramExplainer().explain(analysis.programForIrGeneration());
    List<ProgramExplanation.BuiltinWordEntry> ByteSequence =
        explanation.builtinWords().stream()
            .filter(word -> word.featureGroup().equals("BYTES"))
            .toList();
    assertEquals(189, explanation.builtinWords().size());
    assertEquals(11, ByteSequence.size());
    assertEquals("空のバイト列", ByteSequence.getFirst().name());
    assertEquals("Base64復号失敗の文字位置を取り出す", ByteSequence.getLast().name());
    assertTrue(ByteSequence.stream().allMatch(word -> word.typeRule().equals("fixed")));
    assertTrue(ByteSequence.stream().allMatch(word -> word.capabilities().isEmpty()));
    assertTrue(ByteSequence.stream().allMatch(word -> word.effects().isEmpty()));
  }
}
