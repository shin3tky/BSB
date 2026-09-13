package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourcePosition;
import org.junit.jupiter.api.Test;

class ExecutionTerminationTest {
  @Test
  void embeddedResultDistinguishesAllThreeTerminationKinds() {
    ExecutionResult completed = new ExecutionResult(Optional.empty(), List.of(), 0, 0);
    ExecutionResult programExit =
        new ExecutionResult(
            Optional.empty(),
            List.of(),
            1,
            0,
            List.of(),
            0,
            0,
            0,
            ExecutionTermination.programExit(10));
    Diagnostic diagnostic =
        Diagnostic.builder(
                DiagnosticCode.E_CAPABILITY_FAILURE,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                "test.bsb",
                new SourcePosition(0, 1, 1))
            .build();
    ExecutionResult failed = new ExecutionResult(Optional.of(diagnostic), List.of(), 1, 0);

    assertEquals(ExecutionTermination.Kind.COMPLETED, completed.termination().kind());
    assertEquals(0, completed.exitCode());
    assertEquals(ExecutionTermination.Kind.PROGRAM_EXIT, programExit.termination().kind());
    assertEquals(10, programExit.exitCode());
    assertTrue(programExit.successful());
    assertEquals(ExecutionTermination.Kind.DIAGNOSTIC_FAILURE, failed.termination().kind());
    assertEquals(10, failed.exitCode());
    assertFalse(failed.successful());
  }

  @Test
  void exitCodeAndDiagnosticInvariantsAreChecked() {
    assertThrows(IllegalArgumentException.class, () -> ExecutionTermination.programExit(-1));
    assertThrows(IllegalArgumentException.class, () -> ExecutionTermination.programExit(256));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExecutionTermination(ExecutionTermination.Kind.COMPLETED, OptionalInt.of(0)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ExecutionResult(
                Optional.empty(),
                List.of(),
                0,
                0,
                List.of(),
                0,
                0,
                0,
                ExecutionTermination.diagnosticFailure()));
  }
}
