package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.format.SourceFormatter;
import jp.bsb.runtime.CapabilityException;
import jp.bsb.runtime.ConsoleInput;
import jp.bsb.runtime.ConsoleOutput;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.ExecutionEnvironment;
import jp.bsb.runtime.ExecutionTermination;
import jp.bsb.runtime.InputEvent;
import jp.bsb.runtime.MemoryConsoleInput;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramMetadata;
import jp.bsb.runtime.ProgramRunResult;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.RuntimeCapability;
import jp.bsb.runtime.SleepCapability;
import jp.bsb.runtime.StreamConsoleInput;
import jp.bsb.runtime.TraceSink;
import jp.bsb.runtime.WallTimeReading;
import org.junit.jupiter.api.Test;

class HostIoConformanceTest {
  @Test
  void everyNormalCaseMatchesBothOutputChannelsAndTermination() throws Exception {
    var catalog = HostIoConformanceData.loadCases();
    for (var spec : catalog.cases()) {
      if (!spec.id().matches("IO-N00[1-9]|IO-N01[0-9]|IO-N02[0-4]")) {
        continue;
      }
      Run run = run(spec);
      Map<String, String> attributes = spec.attributes();
      assertEquals(integer(attributes, "run.exit", 0), run.result().exitCode(), spec.id());
      assertEquals(expectedTermination(attributes), run.result().termination().kind(), spec.id());
      assertEquals(expectedText(attributes, "run.stdout", ""), run.stdout().utf8Text(), spec.id());
      assertEquals(
          expectedText(attributes, "run.program.stderr", ""), run.stderr().utf8Text(), spec.id());
    }
  }

  @Test
  void everyStaticAndRuntimeDiagnosticMatchesItsNormativeCodeAndPosition() throws Exception {
    var catalog = HostIoConformanceData.loadCases();
    var diagnostics =
        HostIoConformanceData.loadDiagnostics().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    HostIoConformanceData.DiagnosticSpec::id, value -> value));
    List<String> ids =
        java.util.stream.IntStream.rangeClosed(1, 25)
            .mapToObj(index -> "IO-F%03d".formatted(index))
            .toList();
    for (String id : ids) {
      var spec =
          catalog.cases().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
      Run run = run(spec);
      var expected = diagnostics.get(id);
      var actual =
          run.result().diagnostics().stream()
              .filter(value -> value.code().name().equals(expected.code()))
              .findFirst()
              .orElseThrow(() -> new AssertionError(id + ": " + run.result().diagnostics()));
      assertEquals(expected.code(), actual.code().name(), id);
      assertEquals(expected.line(), actual.location().displayPosition().orElseThrow().line(), id);
      assertEquals(
          expected.column(), actual.location().displayPosition().orElseThrow().column(), id);
      assertEquals(
          id.startsWith("IO-F00") && Integer.parseInt(id.substring(4)) <= 5 ? 8 : 10,
          run.result().exitCode(),
          id);
    }
  }

  @Test
  void everyCaseMatchesItsCheckAndFormatCommandContract() throws Exception {
    var checker = new SourceChecker();
    var formatter = new SourceFormatter();
    var catalog = HostIoConformanceData.loadCases();
    for (var spec : catalog.cases()) {
      byte[] source = HostIoConformanceData.resourceBytes(spec.attributes().get("source"));
      int expectedCheck = catalog.staticFailureIds().contains(spec.id()) ? 8 : 0;
      assertEquals(expectedCheck, checker.check(spec.id() + ".bsb", source).exitCode(), spec.id());

      if (!spec.commands().contains("format")) {
        continue;
      }
      var formatted = formatter.format(spec.id() + ".bsb", source);
      assertTrue(formatted.successful(), spec.id() + ": " + formatted.diagnostics());
      String expectedFormat =
          new String(
              HostIoConformanceData.resourceBytes(spec.attributes().get("format.stdout.source")),
              StandardCharsets.UTF_8);
      assertEquals(expectedFormat, formatted.outputForStandardOutput(), spec.id());
    }
  }

  @Test
  void tracingPreservesEveryNormalResultBudgetAndCapabilityCallSequence() throws Exception {
    for (var spec : HostIoConformanceData.loadCases().cases()) {
      if (!spec.id().startsWith("IO-N")) {
        continue;
      }
      Run normal = run(spec, false);
      Run traced = run(spec, true);
      assertEquals(normal.result(), traced.result(), spec.id());
      assertEquals(normal.stdout().utf8Text(), traced.stdout().utf8Text(), spec.id());
      assertEquals(normal.stderr().utf8Text(), traced.stderr().utf8Text(), spec.id());
      assertEquals(normal.capabilityCalls(), traced.capabilityCalls(), spec.id());
    }
  }

  @Test
  void integratedEffectsMatchTheNormativeTsvWithoutDisclosingValues() throws Exception {
    var spec =
        HostIoConformanceData.loadCases().cases().stream()
            .filter(value -> value.id().equals("IO-N023"))
            .findFirst()
            .orElseThrow();
    Run run = run(spec, true);
    var actual = new StringBuilder("sequence\teffect\n");
    int sequence = 0;
    for (var event : run.traceEvents()) {
      if (!event.effect().isEmpty()) {
        actual.append(++sequence).append('\t').append(event.effect()).append('\n');
      }
    }

    assertEquals(
        new String(
            HostIoConformanceData.resourceBytes(spec.attributes().get("effects.source")),
            StandardCharsets.UTF_8),
        actual.toString());
  }

  @Test
  void unreachableWarningCaseStillPerformsProgramExitWithoutOutput() throws Exception {
    var spec =
        HostIoConformanceData.loadCases().cases().stream()
            .filter(value -> value.id().equals("IO-F026"))
            .findFirst()
            .orElseThrow();
    Run run = run(spec);

    assertEquals(0, run.result().exitCode());
    assertEquals(ExecutionTermination.Kind.PROGRAM_EXIT, run.result().termination().kind());
    assertEquals("W_UNREACHABLE_CODE", run.result().diagnostics().getFirst().code().name());
    assertEquals("", run.stdout().utf8Text());
  }

  @Test
  void everySourceFormatsWithoutChangingInputAndIsIdempotent() throws Exception {
    var formatter = new SourceFormatter();
    for (var spec : HostIoConformanceData.loadCases().cases()) {
      if (!(spec.id().matches("IO-N00[1-9]|IO-N01[0-9]|IO-N02[0-4]")
          || spec.id().matches("IO-F00[1-9]|IO-F01[0-9]|IO-F02[0-6]"))) {
        continue;
      }
      byte[] source = HostIoConformanceData.resourceBytes(spec.attributes().get("source"));
      byte[] original = source.clone();
      var first = formatter.format(spec.id() + ".bsb", source);
      assertTrue(first.successful(), spec.id() + ": " + first.diagnostics());
      var second =
          formatter.format(
              spec.id() + ".bsb", first.outputForStandardOutput().getBytes(StandardCharsets.UTF_8));
      assertTrue(second.successful(), spec.id() + ": " + second.diagnostics());
      assertEquals(first.outputForStandardOutput(), second.outputForStandardOutput(), spec.id());
      assertTrue(java.util.Arrays.equals(original, source), spec.id() + ": input changed");
    }
  }

  private static Run run(HostIoConformanceData.CaseSpec spec) throws Exception {
    return run(spec, false);
  }

  private static Run run(HostIoConformanceData.CaseSpec spec, boolean tracing) throws Exception {
    byte[] source = HostIoConformanceData.resourceBytes(spec.attributes().get("source"));
    HostIoConformanceData.IoSpec io =
        spec.attributes().containsKey("io")
            ? HostIoConformanceData.loadIo(spec.attributes().get("io"))
            : new HostIoConformanceData.IoSpec(Set.of(), List.of(), Map.of());
    var stdout = new MemoryOutputSink();
    var stderr = new MemoryOutputSink();
    var calls = new ArrayList<String>();
    var traceEvents = new ArrayList<jp.bsb.runtime.TraceEvent>();
    var exhaustionChecks = new ArrayList<Runnable>();
    ExecutionEnvironment.Builder builder = ExecutionEnvironment.builder(() -> 0L);
    if (io.capabilities().contains("console.input")) {
      builder.consoleInput(input(io, calls, exhaustionChecks));
    }
    if (io.capabilities().contains("console.output")) {
      builder.consoleOutput(
          output(
              io.attributes().get("console.output.result"),
              stdout,
              RuntimeCapability.CONSOLE_OUTPUT,
              calls));
    }
    if (io.capabilities().contains("console.error")) {
      builder.consoleError(
          output(
              io.attributes().get("console.error.result"),
              stderr,
              RuntimeCapability.CONSOLE_ERROR,
              calls));
    }
    if (io.capabilities().contains("process.exit")) {
      builder.programControl(
          code -> {
            calls.add("process.exit:" + code);
          });
    }
    if (io.capabilities().contains("process.arguments")) {
      List<String> values = arguments(io);
      builder.processArguments(
          () -> {
            calls.add("process.arguments");
            return values;
          });
    }
    if (io.capabilities().contains("program.identity")) {
      String name = io.attributes().get("program.name");
      String location = io.attributes().get("program.location");
      ProgramMetadata metadata =
          location == null
              ? ProgramMetadata.unlocated(name)
              : ProgramMetadata.located(name, location);
      builder.programIdentity(
          () -> {
            calls.add("program.identity");
            return metadata;
          });
    }
    if (io.capabilities().contains("time.sleep")) {
      List<String> results = splitEvents(io.attributes().get("sleep.results"));
      var queue = new HostIoConformanceData.EventQueue<>(results);
      exhaustionChecks.add(queue::assertExhausted);
      builder.sleepCapability(
          milliseconds -> {
            calls.add("time.sleep:" + milliseconds);
            String event = queue.take();
            if (event.equals("cancel")) {
              return SleepCapability.Result.CANCELLED;
            }
            assertEquals("completed:" + milliseconds, event);
            return SleepCapability.Result.COMPLETED;
          });
    }
    if (io.capabilities().contains("time.monotonic")) {
      List<Long> values =
          splitEvents(io.attributes().get("monotonic.millis")).stream()
              .map(Long::parseLong)
              .toList();
      var queue = new HostIoConformanceData.EventQueue<>(values);
      exhaustionChecks.add(queue::assertExhausted);
      builder.monotonicTime(
          () -> {
            calls.add("time.monotonic");
            return queue.take();
          });
    }
    if (io.capabilities().contains("time.wall")) {
      List<WallTimeReading> values =
          splitEvents(io.attributes().get("wall.values")).stream()
              .map(HostIoConformanceTest::wallTime)
              .toList();
      var queue = new HostIoConformanceData.EventQueue<>(values);
      exhaustionChecks.add(queue::assertExhausted);
      builder.wallTime(
          () -> {
            calls.add("time.wall");
            return queue.take();
          });
    }
    ExecutionEnvironment environment = builder.build();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                spec.id() + ".bsb",
                source,
                new ExecutionContext(
                    stdout, () -> 0L, tracing ? traceEvents::add : TraceSink.none(), environment));
    exhaustionChecks.forEach(Runnable::run);
    return new Run(result, stdout, stderr, List.copyOf(calls), List.copyOf(traceEvents));
  }

  private static List<String> splitEvents(String value) {
    return value == null || value.isEmpty() ? List.of() : List.of(value.split("\u001F", -1));
  }

  private static WallTimeReading wallTime(String value) {
    var matcher =
        java.util.regex.Pattern.compile(
                "([0-9]{4,})-([0-9]{2})-([0-9]{2})T([0-9]{2}):([0-9]{2}):([0-9]{2})\\.([0-9]{3})(Z|[+-][0-9]{2}:[0-9]{2})")
            .matcher(value);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("invalid wall time test value: " + value);
    }
    int offsetMinutes = 0;
    String offsetText = matcher.group(8);
    if (!offsetText.equals("Z")) {
      int sign = offsetText.charAt(0) == '-' ? -1 : 1;
      offsetMinutes =
          sign
              * (Integer.parseInt(offsetText.substring(1, 3)) * 60
                  + Integer.parseInt(offsetText.substring(4, 6)));
    }
    var local =
        java.time.LocalDateTime.of(
            Integer.parseInt(matcher.group(1)),
            Integer.parseInt(matcher.group(2)),
            Integer.parseInt(matcher.group(3)),
            Integer.parseInt(matcher.group(4)),
            Integer.parseInt(matcher.group(5)),
            Integer.parseInt(matcher.group(6)),
            Integer.parseInt(matcher.group(7)) * 1_000_000);
    long epoch =
        local.toInstant(java.time.ZoneOffset.ofTotalSeconds(offsetMinutes * 60)).toEpochMilli();
    return new WallTimeReading(epoch, offsetMinutes);
  }

  private static List<String> arguments(HostIoConformanceData.IoSpec io) throws Exception {
    String values = io.attributes().get("arguments.values");
    if (values != null) {
      return values.isEmpty() ? List.of() : List.of(values.split("\u001F", -1));
    }
    String generated = io.attributes().get("arguments.generated");
    if (generated == null) {
      return List.of();
    }
    String[] parts = generated.split(":", -1);
    int variant = Integer.parseInt(parts[1]);
    return switch (parts[0]) {
      case "IO-R005" -> java.util.Collections.nCopies(variant, "");
      case "IO-R006" -> {
        String maximum = "a".repeat(16_777_216);
        yield List.of(maximum, maximum, maximum, maximum, "a".repeat(variant - 67_108_864));
      }
      default -> throw new IllegalArgumentException("unknown argument generator: " + generated);
    };
  }

  private static ConsoleInput input(
      HostIoConformanceData.IoSpec io, List<String> calls, List<Runnable> exhaustionChecks) {
    String raw = io.attributes().get("input.raw.hex");
    if (raw != null) {
      ConsoleInput stream =
          new StreamConsoleInput(new java.io.ByteArrayInputStream(HexFormat.of().parseHex(raw)));
      return () -> {
        calls.add("console.input");
        return stream.readLine();
      };
    }
    String generated = io.attributes().get("input.generated");
    if (generated != null) {
      String[] parts = generated.split(":", -1);
      int variant = Integer.parseInt(parts[1]);
      InputEvent.Line event =
          switch (parts[0]) {
            case "IO-R001" -> new InputEvent.Line(filledBytes(variant), variant);
            case "IO-R002" -> new InputEvent.Line(new byte[] {'a'}, variant);
            default -> throw new IllegalArgumentException("unknown input generator: " + generated);
          };
      var queue = new HostIoConformanceData.EventQueue<InputEvent>(List.of(event));
      exhaustionChecks.add(queue::assertExhausted);
      return () -> {
        calls.add("console.input");
        return queue.take();
      };
    }
    var directives = new HostIoConformanceData.EventQueue<>(io.inputEvents());
    exhaustionChecks.add(directives::assertExhausted);
    return () -> {
      calls.add("console.input");
      var directive = directives.take();
      return switch (directive.kind()) {
        case "line" ->
            MemoryConsoleInput.rawLine(directive.value().getBytes(StandardCharsets.UTF_8), 0);
        case "end" -> new InputEvent.End();
        case "cancel" -> new InputEvent.Cancel();
        case "failure" ->
            throw CapabilityException.failure(RuntimeCapability.CONSOLE_INPUT, "readLine");
        default -> throw new IllegalStateException("unknown parsed input event");
      };
    };
  }

  private static byte[] filledBytes(int length) {
    byte[] bytes = new byte[length];
    java.util.Arrays.fill(bytes, (byte) 'a');
    return bytes;
  }

  private static ConsoleOutput output(
      String result, MemoryOutputSink sink, RuntimeCapability capability, List<String> calls) {
    if ("failure:io".equals(result)) {
      return bytes -> {
        calls.add(capability.sourceName());
        throw CapabilityException.failure(capability, "write");
      };
    }
    return bytes -> {
      calls.add(capability.sourceName());
      sink.write(bytes);
    };
  }

  private static String expectedText(
      Map<String, String> attributes, String prefix, String defaultValue) throws Exception {
    if (Boolean.parseBoolean(attributes.get(prefix + ".empty"))) {
      return "";
    }
    String value = attributes.get(prefix + ".text");
    if (value == null && attributes.containsKey(prefix + ".source")) {
      value =
          new String(
              HostIoConformanceData.resourceBytes(attributes.get(prefix + ".source")),
              StandardCharsets.UTF_8);
    }
    if (value == null) {
      value = defaultValue;
    }
    if (Boolean.parseBoolean(attributes.get(prefix + ".trailingLf"))) {
      value += "\n";
    }
    return value;
  }

  private static int integer(Map<String, String> attributes, String key, int defaultValue) {
    return attributes.containsKey(key) ? Integer.parseInt(attributes.get(key)) : defaultValue;
  }

  private static ExecutionTermination.Kind expectedTermination(Map<String, String> attributes) {
    return switch (attributes.getOrDefault("run.termination", "completed")) {
      case "completed" -> ExecutionTermination.Kind.COMPLETED;
      case "programExit" -> ExecutionTermination.Kind.PROGRAM_EXIT;
      case "diagnosticFailure" -> ExecutionTermination.Kind.DIAGNOSTIC_FAILURE;
      default -> throw new IllegalArgumentException("unknown termination");
    };
  }

  private record Run(
      ProgramRunResult result,
      MemoryOutputSink stdout,
      MemoryOutputSink stderr,
      List<String> capabilityCalls,
      List<jp.bsb.runtime.TraceEvent> traceEvents) {}
}
