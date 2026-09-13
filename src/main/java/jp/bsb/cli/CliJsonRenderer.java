package jp.bsb.cli;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/** 版1 DTOだけを規範順のコンパクトなBOMなしUTF-8 JSONへ変換します。 */
final class CliJsonRenderer {
  static final int MAX_OUTPUT_BYTES = 67_108_864;

  private static final byte[] OUTPUT_LIMIT_FALLBACK =
      ("{\"schemaVersion\":1,\"command\":\"check\",\"success\":false,\"exitCode\":70,"
              + "\"diagnostics\":[],\"problem\":{\"kind\":\"outputLimit\","
              + "\"message\":\"JSON診断の出力が上限を超えました。\"}}\n")
          .getBytes(StandardCharsets.UTF_8);
  private static final byte[] INTERNAL_FALLBACK =
      ("{\"schemaVersion\":1,\"command\":\"check\",\"success\":false,\"exitCode\":70,"
              + "\"diagnostics\":[],\"problem\":{\"kind\":\"internal\","
              + "\"message\":\"処理系で予期しない問題が発生しました。\"}}\n")
          .getBytes(StandardCharsets.UTF_8);

  private final int maximumBytes;

  CliJsonRenderer() {
    this(MAX_OUTPUT_BYTES);
  }

  CliJsonRenderer(int maximumBytes) {
    if (maximumBytes < Math.max(OUTPUT_LIMIT_FALLBACK.length, INTERNAL_FALLBACK.length)) {
      throw new IllegalArgumentException("maximumBytes cannot hold fixed fallback documents");
    }
    this.maximumBytes = maximumBytes;
  }

  JsonRenderResult render(CliJsonDocument document) {
    Objects.requireNonNull(document, "document");
    try {
      var output = new JsonOutput(maximumBytes);
      writeDocument(output, document);
      output.ascii('\n');
      return new JsonRenderResult(output.toByteArray(), document.exitCode());
    } catch (JsonOutput.JsonOutputLimitException exception) {
      return new JsonRenderResult(OUTPUT_LIMIT_FALLBACK.clone(), BsbCli.EXIT_INTERNAL);
    } catch (RuntimeException exception) {
      return new JsonRenderResult(INTERNAL_FALLBACK.clone(), BsbCli.EXIT_INTERNAL);
    }
  }

  private static void writeDocument(JsonOutput output, CliJsonDocument document) {
    output.ascii('{');
    member(output, "schemaVersion", CliJsonDocument.SCHEMA_VERSION);
    commaMember(output, "command", "check");
    document.source().ifPresent(source -> commaMember(output, "source", source));
    commaMember(output, "success", document.successful());
    commaMember(output, "exitCode", document.exitCode());
    output.ascii(',');
    name(output, "diagnostics");
    writeDiagnostics(output, document.diagnostics());
    document
        .problem()
        .ifPresent(
            problem -> {
              output.ascii(',');
              name(output, "problem");
              writeProblem(output, problem);
            });
    output.ascii('}');
  }

  private static void writeDiagnostics(JsonOutput output, java.util.List<DiagnosticJson> values) {
    output.ascii('[');
    for (int index = 0; index < values.size(); index++) {
      if (index > 0) {
        output.ascii(',');
      }
      writeDiagnostic(output, values.get(index));
    }
    output.ascii(']');
  }

  private static void writeDiagnostic(JsonOutput output, DiagnosticJson diagnostic) {
    output.ascii('{');
    member(output, "code", diagnostic.code());
    commaMember(output, "severity", diagnostic.severity());
    commaMember(output, "stage", diagnostic.stage());
    commaMember(output, "message", diagnostic.message());
    commaMember(output, "sourcePath", diagnostic.sourcePath());
    output.ascii(',');
    switch (diagnostic.location()) {
      case JsonSpan span -> {
        name(output, "span");
        writeSpan(output, span);
      }
      case JsonPoint point -> {
        name(output, "point");
        writePoint(output, point);
      }
      case JsonOffset offset -> {
        name(output, "offset");
        writeOffset(output, offset);
      }
    }
    output.ascii(',');
    name(output, "fields");
    writeFields(output, diagnostic.fields());
    diagnostic.expected().ifPresent(value -> commaMember(output, "expected", value));
    diagnostic.actual().ifPresent(value -> commaMember(output, "actual", value));
    output.ascii(',');
    name(output, "relatedLocations");
    writeRelatedLocations(output, diagnostic.relatedLocations());
    output.ascii(',');
    name(output, "fixes");
    writeStrings(output, diagnostic.fixes());
    diagnostic
        .resourceLimit()
        .ifPresent(
            limit -> {
              output.ascii(',');
              name(output, "resourceLimit");
              writeResourceLimit(output, limit);
            });
    output.ascii('}');
  }

  private static void writeSpan(JsonOutput output, JsonSpan span) {
    output.ascii('{');
    name(output, "start");
    writeLineColumn(output, span.start());
    output.ascii(',');
    name(output, "endInclusive");
    writeLineColumn(output, span.endInclusive());
    commaMember(output, "utf8Start", span.utf8Start());
    commaMember(output, "utf8EndExclusive", span.utf8EndExclusive());
    output.ascii('}');
  }

  private static void writeLineColumn(JsonOutput output, JsonLineColumn position) {
    output.ascii('{');
    member(output, "line", position.line());
    commaMember(output, "column", position.column());
    output.ascii('}');
  }

  private static void writePoint(JsonOutput output, JsonPoint point) {
    output.ascii('{');
    member(output, "line", point.line());
    commaMember(output, "column", point.column());
    commaMember(output, "utf8Offset", point.utf8Offset());
    output.ascii('}');
  }

  private static void writeOffset(JsonOutput output, JsonOffset offset) {
    output.ascii('{');
    member(output, "utf8Offset", offset.utf8Offset());
    output.ascii('}');
  }

  private static void writeFields(JsonOutput output, Map<String, String> fields) {
    output.ascii('{');
    int index = 0;
    for (Map.Entry<String, String> field : fields.entrySet()) {
      if (index++ > 0) {
        output.ascii(',');
      }
      name(output, field.getKey());
      output.string(field.getValue());
    }
    output.ascii('}');
  }

  private static void writeRelatedLocations(
      JsonOutput output, java.util.List<JsonRelatedLocation> values) {
    output.ascii('[');
    for (int index = 0; index < values.size(); index++) {
      if (index > 0) {
        output.ascii(',');
      }
      JsonRelatedLocation related = values.get(index);
      output.ascii('{');
      if (related.sourcePath().isPresent()) {
        member(output, "sourcePath", related.sourcePath().orElseThrow());
        output.ascii(',');
        name(output, "point");
        writePoint(output, related.point().orElseThrow());
        commaMember(output, "description", related.description());
      } else {
        member(output, "description", related.description());
      }
      output.ascii('}');
    }
    output.ascii(']');
  }

  private static void writeStrings(JsonOutput output, java.util.List<String> values) {
    output.ascii('[');
    for (int index = 0; index < values.size(); index++) {
      if (index > 0) {
        output.ascii(',');
      }
      output.string(values.get(index));
    }
    output.ascii(']');
  }

  private static void writeResourceLimit(JsonOutput output, JsonResourceLimit limit) {
    output.ascii('{');
    member(output, "name", limit.name());
    commaMember(output, "limit", limit.limit());
    commaMember(output, "observed", limit.observed());
    output.ascii('}');
  }

  private static void writeProblem(JsonOutput output, CliJsonProblem problem) {
    output.ascii('{');
    member(output, "kind", problem.kind());
    commaMember(output, "message", problem.message());
    output.ascii('}');
  }

  private static void member(JsonOutput output, String name, String value) {
    name(output, name);
    output.string(value);
  }

  private static void member(JsonOutput output, String name, long value) {
    name(output, name);
    output.number(value);
  }

  private static void commaMember(JsonOutput output, String name, String value) {
    output.ascii(',');
    member(output, name, value);
  }

  private static void commaMember(JsonOutput output, String name, long value) {
    output.ascii(',');
    member(output, name, value);
  }

  private static void commaMember(JsonOutput output, String name, boolean value) {
    output.ascii(',');
    name(output, name);
    output.ascii(value ? "true" : "false");
  }

  private static void name(JsonOutput output, String name) {
    output.string(name);
    output.ascii(':');
  }

  /** 完成済み文書と、上限・内部フォールバックを反映した終了コードです。 */
  record JsonRenderResult(byte[] bytes, int exitCode) {
    JsonRenderResult {
      Objects.requireNonNull(bytes, "bytes");
    }
  }
}
