package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import jp.bsb.diagnostics.DiagnosticCode;
import org.junit.jupiter.api.Test;

class HostIoDateTimeRuntimeTest {
  private static final byte[] SOURCE =
      ("メインとは （--）\n" + "    現在日時を得る\n" + "    日時を文字列に変換する\n" + "    一行表示する\n" + "こと。\n")
          .getBytes(StandardCharsets.UTF_8);

  @Test
  void formatsYearAndOffsetBoundariesWithFixedAsciiFields() {
    DateTimeValue minimum = value(0, 1, 1, 0, 0, 0, 0, 1_080);
    DateTimeValue maximum = value(9_999, 12, 31, 23, 59, 59, 999, -1_080);
    DateTimeValue utc = value(2026, 8, 29, 5, 5, 6, 7, 0);

    assertEquals("0000-01-01T00:00:00.000+18:00", minimum.displayText());
    assertEquals("9999-12-31T23:59:59.999-18:00", maximum.displayText());
    assertEquals("2026-08-29T05:05:06.007Z", utc.displayText());
    assertThrows(
        IllegalArgumentException.class,
        () -> new DateTimeValue(minimum.epochMilliseconds(), 1_081));
  }

  @Test
  void obtainsWallTimeOnceAndFormattingNeverCallsTheCapabilityAgain() {
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    DateTimeValue expected = value(2026, 8, 29, 14, 5, 6, 123, 540);
    Run run =
        run(
            () -> {
              calls.incrementAndGet();
              return new WallTimeReading(expected.epochMilliseconds(), expected.offsetMinutes());
            },
            true);

    assertEquals(0, run.result().exitCode());
    assertEquals(1, calls.get());
    assertEquals("2026-08-29T14:05:06.123+09:00\n", run.output().utf8Text());
    TraceEvent wall =
        run.events().stream()
            .filter(event -> event.opcode().equals("Call:現在日時を得る"))
            .findFirst()
            .orElseThrow();
    assertEquals("time.wall", wall.effect());
    assertEquals("日時:2026-08-29T14:05:06.123+09:00", wall.dataAfter().getLast().traceText());
  }

  @Test
  void rejectsOffsetAndYearOutsideTheNormativeRangeWithoutLeakingHostDetails() {
    DateTimeValue year9999 = value(9_999, 12, 31, 23, 59, 59, 999, 0);
    Run offset = run(() -> new WallTimeReading(year9999.epochMilliseconds(), 1_081), false);
    long year10000 = LocalDateTime.of(10_000, 1, 1, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
    Run year = run(() -> new WallTimeReading(year10000, 0), false);
    Run failure =
        run(
            () -> {
              throw CapabilityException.failure(RuntimeCapability.TIME_WALL, "secret-host-error");
            },
            false);

    assertEquals(DiagnosticCode.E_DATETIME_RANGE, offset.result().diagnostics().getFirst().code());
    assertEquals(DiagnosticCode.E_DATETIME_RANGE, year.result().diagnostics().getFirst().code());
    assertEquals("10000", year.result().diagnostics().getFirst().fields().get("year"));
    assertEquals(
        DiagnosticCode.E_CAPABILITY_FAILURE, failure.result().diagnostics().getFirst().code());
    assertFalse(failure.result().diagnostics().toString().contains("secret-host-error"));
  }

  @Test
  void dateTimeIsConcreteButNotDisplayableComparableOrArrayEligible() {
    assertFalse(jp.bsb.stdlib.ValueType.DATE_TIME.isArrayElementType());
    assertThrows(
        IllegalArgumentException.class,
        () -> jp.bsb.stdlib.ValueType.arrayOf(jp.bsb.stdlib.ValueType.DATE_TIME));

    byte[] display =
        ("メインとは （--）\n" + "    現在日時を得る を 一行表示する\n" + "こと。\n").getBytes(StandardCharsets.UTF_8);
    byte[] equality =
        ("メインとは （--）\n" + "    現在日時を得る\n" + "    現在日時を得る\n" + "    等しい を 一行表示する\n" + "こと。\n")
            .getBytes(StandardCharsets.UTF_8);

    assertEquals(8, check(display).exitCode());
    assertEquals(8, check(equality).exitCode());
  }

  private static ProgramRunResult check(byte[] source) {
    return new ProgramRunner()
        .run(
            "datetime-static.bsb",
            source,
            new ExecutionContext(new MemoryOutputSink(), () -> 0L, TraceSink.none()));
  }

  private static Run run(WallTime wallTime, boolean tracing) {
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(() -> 0L)
            .consoleOutput(output::write)
            .wallTime(wallTime)
            .build();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "datetime.bsb",
                SOURCE,
                new ExecutionContext(
                    output, () -> 0L, tracing ? events::add : TraceSink.none(), environment));
    return new Run(result, output, List.copyOf(events));
  }

  private static DateTimeValue value(
      int year,
      int month,
      int day,
      int hour,
      int minute,
      int second,
      int millisecond,
      int offsetMinutes) {
    ZoneOffset offset = ZoneOffset.ofTotalSeconds(offsetMinutes * 60);
    long epoch =
        LocalDateTime.of(year, month, day, hour, minute, second, millisecond * 1_000_000)
            .toInstant(offset)
            .toEpochMilli();
    return new DateTimeValue(epoch, offsetMinutes);
  }

  private record Run(ProgramRunResult result, MemoryOutputSink output, List<TraceEvent> events) {}
}
