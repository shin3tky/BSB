package jp.bsb.cli;

import java.util.Objects;
import java.util.Optional;
import jp.bsb.diagnostics.DiagnosticLocation;
import jp.bsb.diagnostics.FileOffset;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.frontend.SourceText;

/** 診断と説明が共有する元ソース位置を版1 JSON位置へ変換します。 */
final class JsonSourceLocationMapper {
  private JsonSourceLocationMapper() {}

  static JsonDiagnosticLocation diagnostic(
      DiagnosticLocation location, String sourcePath, Optional<SourceText> source) {
    Objects.requireNonNull(location, "location");
    Objects.requireNonNull(sourcePath, "sourcePath");
    Objects.requireNonNull(source, "source");
    return switch (location) {
      case FileOffset offset -> new JsonOffset(offset.utf8Offset());
      case SourcePosition point -> point(point);
      case SourceSpan span -> {
        if (span.start().utf8Offset() == span.end().utf8Offset()) {
          yield point(span.start());
        }
        SourceText snapshot = source.orElseThrow(() -> new IllegalStateException("missing source"));
        if (!snapshot.sourcePath().equals(sourcePath)) {
          throw new IllegalStateException("diagnostic source does not match source snapshot");
        }
        yield span(span, snapshot);
      }
    };
  }

  static JsonSpan span(SourceSpan span, SourceText source) {
    Objects.requireNonNull(span, "span");
    Objects.requireNonNull(source, "source");
    if (span.start().utf8Offset() == span.end().utf8Offset()) {
      throw new IllegalArgumentException("an explanation location must be non-empty");
    }
    SourcePosition inclusive = source.clusterPositionBefore(span.end().utf8Offset());
    return new JsonSpan(
        lineColumn(span.start()),
        lineColumn(inclusive),
        span.start().utf8Offset(),
        span.end().utf8Offset());
  }

  static JsonPoint point(SourcePosition position) {
    Objects.requireNonNull(position, "position");
    return new JsonPoint(position.line(), position.column(), position.utf8Offset());
  }

  private static JsonLineColumn lineColumn(SourcePosition position) {
    return new JsonLineColumn(position.line(), position.column());
  }
}
