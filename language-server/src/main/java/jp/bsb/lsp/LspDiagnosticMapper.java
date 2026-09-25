package jp.bsb.lsp;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jp.bsb.analyzer.AnalysisResult;
import jp.bsb.diagnostics.DiagnosticLocation;
import jp.bsb.diagnostics.DiagnosticMessageCatalog;
import jp.bsb.diagnostics.RelatedLocation;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.frontend.SourceText;
import jp.bsb.frontend.Utf16Position;
import jp.bsb.frontend.Utf16Range;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

/** BSBの構造化診断をLSP診断へ写します。 */
final class LspDiagnosticMapper {
  private static final Range DOCUMENT_START = new Range(new Position(0, 0), new Position(0, 0));

  private final DiagnosticMessageCatalog messages;

  LspDiagnosticMapper(DiagnosticMessageCatalog messages) {
    this.messages = Objects.requireNonNull(messages, "messages");
  }

  List<org.eclipse.lsp4j.Diagnostic> map(AnalysisResult result, String documentUri) {
    Objects.requireNonNull(result, "result");
    Objects.requireNonNull(documentUri, "documentUri");
    SourceText source = result.source().orElse(null);
    return result.diagnostics().stream()
        .map(diagnostic -> map(diagnostic, source, documentUri))
        .toList();
  }

  private org.eclipse.lsp4j.Diagnostic map(
      jp.bsb.diagnostics.Diagnostic diagnostic, SourceText source, String documentUri) {
    var mapped = new org.eclipse.lsp4j.Diagnostic();
    mapped.setRange(range(diagnostic.location(), source));
    mapped.setSeverity(severity(diagnostic.severity()));
    mapped.setCode(Either.forLeft(diagnostic.code().name()));
    mapped.setSource("bsb");
    mapped.setMessage(messages.format(diagnostic));

    List<DiagnosticRelatedInformation> related =
        relatedInformation(diagnostic.relatedLocations(), source, documentUri);
    if (!related.isEmpty()) {
      mapped.setRelatedInformation(related);
    }
    return mapped;
  }

  private static Range range(DiagnosticLocation location, SourceText source) {
    if (source == null) {
      return DOCUMENT_START;
    }
    if (location instanceof SourceSpan span) {
      return range(source.utf16RangeOf(span));
    }
    if (location instanceof SourcePosition position) {
      Position point = position(source.utf16PositionAt(position));
      return new Range(point, point);
    }
    return DOCUMENT_START;
  }

  private static List<DiagnosticRelatedInformation> relatedInformation(
      List<RelatedLocation> relatedLocations, SourceText source, String documentUri) {
    if (source == null) {
      return List.of();
    }
    var result = new ArrayList<DiagnosticRelatedInformation>();
    for (RelatedLocation related : relatedLocations) {
      if (related.position() == null || !documentUri.equals(related.sourcePath())) {
        continue;
      }
      Position point = position(source.utf16PositionAt(related.position()));
      result.add(
          new DiagnosticRelatedInformation(
              new Location(documentUri, new Range(point, point)), related.description()));
    }
    return List.copyOf(result);
  }

  private static Range range(Utf16Range range) {
    return new Range(position(range.start()), position(range.end()));
  }

  private static Position position(Utf16Position position) {
    return new Position(position.line(), position.character());
  }

  private static DiagnosticSeverity severity(Severity severity) {
    return switch (severity) {
      case ERROR -> DiagnosticSeverity.Error;
      case WARNING -> DiagnosticSeverity.Warning;
    };
  }
}
