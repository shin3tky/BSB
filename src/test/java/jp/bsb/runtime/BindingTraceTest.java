package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 束縛の保存領域トレース6列と安全な値表示を検証します。 */
class BindingTraceTest {
  @Test
  void formatsTheChapterTraceByteForByteWithoutChangingExecution() throws IOException {
    byte[] source = resourceBytes("chapter/bindings-chapter.bsb");
    String expectedTrace = resourceText("chapter/bindings-chapter.trace.tsv");
    var normalOutput = new MemoryOutputSink();
    var tracedOutput = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    var runner = new ProgramRunner();

    ProgramRunResult normal =
        runner.run(
            "bindings-chapter.bsb",
            source,
            new ExecutionContext(normalOutput, () -> 0L, TraceSink.none()));
    ProgramRunResult traced =
        runner.run(
            "bindings-chapter.bsb",
            source,
            new ExecutionContext(tracedOutput, () -> 0L, events::add));

    assertTrue(normal.successful(), normal.diagnostics().toString());
    assertTrue(traced.successful(), traced.diagnostics().toString());
    assertEquals(normal.exitCode(), traced.exitCode());
    assertEquals(normal.finalDataStack(), traced.finalDataStack());
    assertEquals(normal.finalGlobalValues(), traced.finalGlobalValues());
    assertArrayEquals(normalOutput.bytes(), tracedOutput.bytes());
    assertEquals(expectedTrace, TraceTsvFormatter.formatBinding(events));
  }

  @Test
  void distinguishesInitializeLoadAndStoreValues() {
    String source =
        "値は 変数 1。\n\n" + "メインとは （--）\n" + "    値 を 一行表示する\n" + "    2 を 値 に 入れる\n" + "こと。\n";
    var events = new ArrayList<TraceEvent>();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "storage-trace.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(new MemoryOutputSink(), () -> 0L, events::add));

    assertTrue(result.successful(), result.diagnostics().toString());
    List<StorageTraceState> storage =
        events.stream().flatMap(event -> event.storage().stream()).toList();
    assertEquals(3, storage.size());
    assertEquals(Optional.empty(), storage.get(0).valueBefore());
    assertEquals(storage.get(1).valueBefore(), storage.get(1).valueAfter());
    assertEquals(
        new IntegerValue(java.math.BigInteger.ONE), storage.get(2).valueBefore().orElseThrow());
    assertEquals(
        new IntegerValue(java.math.BigInteger.TWO), storage.get(2).valueAfter().orElseThrow());
  }

  @Test
  void boundsEscapesAndCanRedactTraceValues() {
    String raw = "01234567890123456789012345678901\tsecret";
    var value = new StringValue(raw);
    var event =
        new TraceEvent(
            1,
            "メイン",
            "PushConst",
            1,
            1,
            List.of(value),
            List.of(value),
            1,
            1,
            new byte[0],
            List.of());

    String visible = TraceTsvFormatter.formatBinding(List.of(event));
    String redacted = TraceTsvFormatter.formatBinding(List.of(event), ignored -> false);

    assertTrue(visible.contains("文字列:01234567890123456789012345678901…"));
    assertTrue(!visible.contains("secret"));
    assertTrue(redacted.contains("[文字列:<redacted>]"));
    assertTrue(!redacted.contains(raw));
  }

  private static byte[] resourceBytes(String relativePath) throws IOException {
    String path = "/conformance/bindings/" + relativePath;
    try (var input = BindingTraceTest.class.getResourceAsStream(path)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + path);
      }
      return input.readAllBytes();
    }
  }

  private static String resourceText(String relativePath) throws IOException {
    return new String(resourceBytes(relativePath), StandardCharsets.UTF_8);
  }
}
