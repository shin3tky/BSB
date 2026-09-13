package jp.bsb.cli;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** `explain --json`版1の最上位文書です。 */
record ExplainJsonDocument(
    Optional<String> source,
    int exitCode,
    List<DiagnosticJson> diagnostics,
    Optional<ExplainJson> explanation,
    Optional<CliJsonProblem> problem) {
  static final int SCHEMA_VERSION = 1;

  ExplainJsonDocument {
    Objects.requireNonNull(source, "source");
    diagnostics = List.copyOf(diagnostics);
    Objects.requireNonNull(explanation, "explanation");
    Objects.requireNonNull(problem, "problem");
    boolean successful = exitCode == 0;
    boolean problemExit = exitCode == 2 || exitCode == 3 || exitCode == 70;
    if (explanation.isPresent() != successful) {
      throw new IllegalArgumentException("only a successful document has an explanation");
    }
    if (problem.isPresent() != problemExit) {
      throw new IllegalArgumentException("problem presence does not match exit code");
    }
    if (problem.isPresent() && !diagnostics.isEmpty()) {
      throw new IllegalArgumentException("problem and diagnostics are mutually exclusive");
    }
    if (!successful && !problemExit && exitCode != 8 && exitCode != 9) {
      throw new IllegalArgumentException("unsupported explain exit code");
    }
  }

  boolean successful() {
    return exitCode == 0;
  }

  static ExplainJsonDocument success(
      String source, List<DiagnosticJson> diagnostics, ExplainJson explanation) {
    return new ExplainJsonDocument(
        Optional.of(source), 0, diagnostics, Optional.of(explanation), Optional.empty());
  }

  static ExplainJsonDocument diagnostics(
      String source, int exitCode, List<DiagnosticJson> diagnostics) {
    return new ExplainJsonDocument(
        Optional.of(source), exitCode, diagnostics, Optional.empty(), Optional.empty());
  }

  static ExplainJsonDocument problem(
      Optional<String> source, int exitCode, CliJsonProblemKind kind, String message) {
    return new ExplainJsonDocument(
        source,
        exitCode,
        List.of(),
        Optional.empty(),
        Optional.of(new CliJsonProblem(kind.jsonName(), message)));
  }
}
