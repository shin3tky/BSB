package jp.bsb.cli;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import jp.bsb.analyzer.AnalysisResult;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticMessageCatalog;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.RelatedLocation;
import jp.bsb.diagnostics.Severity;
import jp.bsb.frontend.SourceText;

/** 内部診断を版1の安定した識別値と位置表現へ写します。 */
final class DiagnosticJsonMapper {
  private static final Comparator<String> UNICODE_SCALAR_ORDER =
      DiagnosticJsonMapper::compareUnicodeScalars;

  private final DiagnosticMessageCatalog messages;

  DiagnosticJsonMapper(DiagnosticMessageCatalog messages) {
    this.messages = Objects.requireNonNull(messages, "messages");
  }

  List<DiagnosticJson> map(AnalysisResult result) {
    Objects.requireNonNull(result, "result");
    var ordered = new ArrayList<>(result.diagnostics());
    ordered.sort(Diagnostic.ORDERING);
    return ordered.stream().map(diagnostic -> map(diagnostic, result.source())).toList();
  }

  private DiagnosticJson map(Diagnostic diagnostic, Optional<SourceText> source) {
    SortedMap<String, String> fields = new TreeMap<>(UNICODE_SCALAR_ORDER);
    fields.putAll(diagnostic.fields());
    return new DiagnosticJson(
        diagnostic.code().name(),
        severity(diagnostic.severity()),
        stage(diagnostic.stage()),
        messages.format(diagnostic),
        diagnostic.sourcePath(),
        JsonSourceLocationMapper.diagnostic(diagnostic.location(), diagnostic.sourcePath(), source),
        Collections.unmodifiableSortedMap(fields),
        diagnostic.expected(),
        diagnostic.actual(),
        diagnostic.relatedLocations().stream().map(DiagnosticJsonMapper::related).toList(),
        diagnostic.fixes(),
        resourceLimit(diagnostic));
  }

  private static JsonRelatedLocation related(RelatedLocation related) {
    if (related.position() == null) {
      return new JsonRelatedLocation(Optional.empty(), Optional.empty(), related.description());
    }
    return new JsonRelatedLocation(
        Optional.of(related.sourcePath()),
        Optional.of(JsonSourceLocationMapper.point(related.position())),
        related.description());
  }

  private static Optional<JsonResourceLimit> resourceLimit(Diagnostic diagnostic) {
    int present =
        (diagnostic.limitName().isPresent() ? 1 : 0)
            + (diagnostic.limit().isPresent() ? 1 : 0)
            + (diagnostic.observed().isPresent() ? 1 : 0);
    if (present == 0) {
      return Optional.empty();
    }
    if (present != 3) {
      throw new IllegalStateException("incomplete diagnostic resource limit");
    }
    return Optional.of(
        new JsonResourceLimit(
            diagnostic.limitName().orElseThrow(),
            diagnostic.limit().orElseThrow(),
            diagnostic.observed().orElseThrow()));
  }

  private static String severity(Severity severity) {
    return switch (severity) {
      case ERROR -> "error";
      case WARNING -> "warning";
    };
  }

  private static String stage(DiagnosticStage stage) {
    return switch (stage) {
      case UTF8 -> "utf8";
      case LEXICAL -> "lexical";
      case SYNTAX -> "syntax";
      case NAME -> "name";
      case TYPE_AND_STACK -> "typeAndStack";
      case IR -> "ir";
      case RUNTIME -> "runtime";
    };
  }

  private static int compareUnicodeScalars(String left, String right) {
    int leftIndex = 0;
    int rightIndex = 0;
    while (leftIndex < left.length() && rightIndex < right.length()) {
      int leftCodePoint = left.codePointAt(leftIndex);
      int rightCodePoint = right.codePointAt(rightIndex);
      int comparison = Integer.compare(leftCodePoint, rightCodePoint);
      if (comparison != 0) {
        return comparison;
      }
      leftIndex += Character.charCount(leftCodePoint);
      rightIndex += Character.charCount(rightCodePoint);
    }
    return Integer.compare(left.length() - leftIndex, right.length() - rightIndex);
  }
}
