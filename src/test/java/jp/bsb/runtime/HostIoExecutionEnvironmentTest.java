package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HostIoExecutionEnvironmentTest {
  @Test
  void capabilitiesAreExplicitAndInvocableInMemory() throws Exception {
    var output = new ArrayList<byte[]>();
    ConsoleInput input = () -> new InputEvent.Line("受付".getBytes(StandardCharsets.UTF_8), 6);
    ConsoleOutput error = bytes -> output.add(bytes.clone());
    ProgramControl control = code -> output.add(new byte[] {(byte) code});

    ExecutionEnvironment environment =
        ExecutionEnvironment.builder(() -> 17L)
            .consoleInput(input)
            .consoleError(error)
            .programControl(control)
            .build();

    assertEquals(
        List.of(
            RuntimeCapability.CONSOLE_INPUT,
            RuntimeCapability.CONSOLE_ERROR,
            RuntimeCapability.PROCESS_EXIT),
        List.copyOf(environment.capabilities()));
    assertTrue(environment.consoleOutput().isEmpty());
    InputEvent.Line line = (InputEvent.Line) environment.consoleInput().orElseThrow().readLine();
    assertArrayEquals("受付".getBytes(StandardCharsets.UTF_8), line.bytes());
    environment.consoleError().orElseThrow().write(new byte[] {1, 2});
    environment.programControl().orElseThrow().exit(255);
    assertArrayEquals(new byte[] {1, 2}, output.get(0));
    assertArrayEquals(new byte[] {(byte) 255}, output.get(1));
  }

  @Test
  void absenceAndFailureNeverUseNullOrLeakHostDetails() {
    ExecutionEnvironment environment = ExecutionEnvironment.builder(() -> 0L).build();
    assertTrue(environment.capabilities().isEmpty());
    assertTrue(environment.consoleInput().isEmpty());

    var failure = CapabilityException.failure(RuntimeCapability.CONSOLE_INPUT, "readLine");
    assertEquals(CapabilityException.Kind.FAILURE, failure.kind());
    assertEquals(RuntimeCapability.CONSOLE_INPUT, failure.capability());
    assertEquals("readLine", failure.operation());
    assertNull(failure.getMessage());
    assertNull(failure.getCause());
    assertEquals(0, failure.getStackTrace().length);
  }

  @Test
  void legacyContextBuildsSafeCompatibilityEnvironment() {
    var output = new MemoryOutputSink();
    MonotonicClock clock = () -> 23L;
    ExecutionContext context = new ExecutionContext(output, clock, TraceSink.none());

    assertSame(output, context.output());
    assertSame(clock, context.clock());
    assertEquals(23L, context.environment().resourceClock().nanoTime());
    assertTrue(context.environment().consoleOutput().isPresent());
    assertTrue(context.environment().programControl().isPresent());
    assertFalse(context.environment().consoleInput().isPresent());
  }
}
