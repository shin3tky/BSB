package jp.bsb.cli;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** `check --json`の版1最上位文書です。 */
record CliJsonDocument(
    Optional<String> source,
    int exitCode,
    List<DiagnosticJson> diagnostics,
    Optional<CliJsonProblem> problem) {
  static final int SCHEMA_VERSION = 1;

  CliJsonDocument {
    Objects.requireNonNull(source, "source");
    diagnostics = List.copyOf(diagnostics);
    Objects.requireNonNull(problem, "problem");
    if (problem.isPresent() && !diagnostics.isEmpty()) {
      throw new IllegalArgumentException("problem and diagnostics are mutually exclusive");
    }
    boolean problemExit = exitCode == 2 || exitCode == 3 || exitCode == 70;
    if (problem.isPresent() != problemExit) {
      throw new IllegalArgumentException("problem presence does not match exit code");
    }
    if (!problemExit && exitCode != 0 && exitCode != 8 && exitCode != 9) {
      throw new IllegalArgumentException("unsupported check exit code");
    }
  }

  boolean successful() {
    return exitCode == 0;
  }

  static CliJsonDocument analysis(String source, int exitCode, List<DiagnosticJson> diagnostics) {
    return new CliJsonDocument(Optional.of(source), exitCode, diagnostics, Optional.empty());
  }

  static CliJsonDocument problem(
      Optional<String> source, int exitCode, CliJsonProblemKind kind, String message) {
    return new CliJsonDocument(
        source, exitCode, List.of(), Optional.of(new CliJsonProblem(kind.jsonName(), message)));
  }
}

/** CLI問題の安定した版1識別値です。 */
enum CliJsonProblemKind {
  USAGE("usage"),
  IO("io"),
  INTERNAL("internal"),
  OUTPUT_LIMIT("outputLimit");

  private final String jsonName;

  CliJsonProblemKind(String jsonName) {
    this.jsonName = jsonName;
  }

  String jsonName() {
    return jsonName;
  }
}

/** 言語診断と区別されるCLI問題です。 */
record CliJsonProblem(String kind, String message) {
  CliJsonProblem {
    if (kind == null || kind.isBlank() || message == null || message.isBlank()) {
      throw new IllegalArgumentException("problem kind and message must not be blank");
    }
  }
}
