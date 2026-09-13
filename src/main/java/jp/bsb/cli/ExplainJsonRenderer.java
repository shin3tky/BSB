package jp.bsb.cli;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** `explain --json`版1 DTOを規範順のコンパクトなBOMなしUTF-8へ変換します。 */
final class ExplainJsonRenderer {
  static final int MAX_OUTPUT_BYTES = 67_108_864;

  private static final byte[] OUTPUT_LIMIT_FALLBACK =
      ("{\"schemaVersion\":1,\"command\":\"explain\",\"success\":false,\"exitCode\":70,"
              + "\"diagnostics\":[],\"builtinWords\":[],\"userWords\":[],\"scopes\":[],"
              + "\"bindings\":[],\"problem\":{\"kind\":\"outputLimit\","
              + "\"message\":\"JSON説明の出力が上限を超えました。\"}}\n")
          .getBytes(StandardCharsets.UTF_8);
  private static final byte[] INTERNAL_FALLBACK =
      ("{\"schemaVersion\":1,\"command\":\"explain\",\"success\":false,\"exitCode\":70,"
              + "\"diagnostics\":[],\"builtinWords\":[],\"userWords\":[],\"scopes\":[],"
              + "\"bindings\":[],\"problem\":{\"kind\":\"internal\","
              + "\"message\":\"処理系で予期しない問題が発生しました。\"}}\n")
          .getBytes(StandardCharsets.UTF_8);

  private final int maximumBytes;

  ExplainJsonRenderer() {
    this(MAX_OUTPUT_BYTES);
  }

  ExplainJsonRenderer(int maximumBytes) {
    if (maximumBytes < Math.max(OUTPUT_LIMIT_FALLBACK.length, INTERNAL_FALLBACK.length)) {
      throw new IllegalArgumentException("maximumBytes cannot hold fixed fallback documents");
    }
    this.maximumBytes = maximumBytes;
  }

  RenderResult render(ExplainJsonDocument document) {
    Objects.requireNonNull(document, "document");
    try {
      var output = new JsonOutput(maximumBytes);
      writeDocument(output, document);
      output.ascii('\n');
      return new RenderResult(output.toByteArray(), document.exitCode());
    } catch (JsonOutput.JsonOutputLimitException exception) {
      return new RenderResult(OUTPUT_LIMIT_FALLBACK.clone(), BsbCli.EXIT_INTERNAL);
    } catch (RuntimeException exception) {
      return new RenderResult(INTERNAL_FALLBACK.clone(), BsbCli.EXIT_INTERNAL);
    }
  }

  private static void writeDocument(JsonOutput output, ExplainJsonDocument document) {
    output.ascii('{');
    member(output, "schemaVersion", ExplainJsonDocument.SCHEMA_VERSION);
    commaMember(output, "command", "explain");
    document.source().ifPresent(source -> commaMember(output, "source", source));
    commaMember(output, "success", document.successful());
    commaMember(output, "exitCode", document.exitCode());
    output.ascii(',');
    name(output, "diagnostics");
    writeDiagnostics(output, document.diagnostics());
    document
        .explanation()
        .ifPresent(
            explanation -> {
              output.ascii(',');
              name(output, "summary");
              writeSummary(output, explanation.summary());
            });
    output.ascii(',');
    name(output, "builtinWords");
    writeBuiltinWords(
        output, document.explanation().map(ExplainJson::builtinWords).orElseGet(List::of));
    output.ascii(',');
    name(output, "userWords");
    writeUserWords(output, document.explanation().map(ExplainJson::userWords).orElseGet(List::of));
    output.ascii(',');
    name(output, "scopes");
    writeScopes(output, document.explanation().map(ExplainJson::scopes).orElseGet(List::of));
    output.ascii(',');
    name(output, "bindings");
    writeBindings(output, document.explanation().map(ExplainJson::bindings).orElseGet(List::of));
    document
        .explanation()
        .ifPresent(
            explanation -> {
              output.ascii(',');
              name(output, "parameterizedCapabilities");
              writeParameterizedCapabilities(output, explanation.parameterizedCapabilities());
            });
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

  private static void writeSummary(JsonOutput output, ExplainSummaryJson summary) {
    output.ascii('{');
    member(output, "entryPoint", summary.entryPoint());
    output.ascii(',');
    name(output, "reachableUserWords");
    writeStrings(output, summary.reachableUserWords());
    output.ascii(',');
    name(output, "reachableBuiltinWords");
    writeStrings(output, summary.reachableBuiltinWords());
    output.ascii(',');
    name(output, "capabilities");
    writeStrings(output, summary.capabilities());
    output.ascii(',');
    name(output, "effects");
    writeStrings(output, summary.effects());
    output.ascii('}');
  }

  private static void writeBuiltinWords(JsonOutput output, List<ExplainBuiltinWordJson> words) {
    output.ascii('[');
    for (int index = 0; index < words.size(); index++) {
      if (index > 0) output.ascii(',');
      ExplainBuiltinWordJson word = words.get(index);
      output.ascii('{');
      member(output, "name", word.name());
      output.ascii(',');
      name(output, "aliases");
      writeStrings(output, word.aliases());
      commaMember(output, "description", word.description());
      output.ascii(',');
      name(output, "stackEffect");
      writeStackEffect(output, word.stackEffect());
      commaMember(output, "typeRule", word.typeRule());
      output.ascii(',');
      name(output, "capabilities");
      writeStrings(output, word.capabilities());
      output.ascii(',');
      name(output, "effects");
      writeStrings(output, word.effects());
      commaMember(output, "featureGroup", word.featureGroup());
      commaMember(output, "example", word.example());
      output.ascii('}');
    }
    output.ascii(']');
  }

  private static void writeUserWords(JsonOutput output, List<ExplainUserWordJson> words) {
    output.ascii('[');
    for (int index = 0; index < words.size(); index++) {
      if (index > 0) output.ascii(',');
      ExplainUserWordJson word = words.get(index);
      output.ascii('{');
      member(output, "name", word.name());
      commaMember(output, "spelling", word.spelling());
      output.ascii(',');
      name(output, "declaration");
      writeLocatedSpan(output, word.declaration());
      output.ascii(',');
      name(output, "stackEffect");
      writeStackEffect(output, word.stackEffect());
      commaMember(output, "reachableFromMain", word.reachableFromMain());
      output.ascii(',');
      name(output, "directCapabilities");
      writeStrings(output, word.directCapabilities());
      output.ascii(',');
      name(output, "capabilities");
      writeStrings(output, word.capabilities());
      output.ascii(',');
      name(output, "directEffects");
      writeStrings(output, word.directEffects());
      output.ascii(',');
      name(output, "effects");
      writeStrings(output, word.effects());
      output.ascii('}');
    }
    output.ascii(']');
  }

  private static void writeStackEffect(JsonOutput output, ExplainStackEffectJson effect) {
    output.ascii('{');
    name(output, "inputs");
    writeStrings(output, effect.inputs());
    output.ascii(',');
    name(output, "outputs");
    writeStrings(output, effect.outputs());
    commaMember(output, "returnsNormally", effect.returnsNormally());
    output.ascii('}');
  }

  private static void writeScopes(JsonOutput output, List<ExplainScopeJson> scopes) {
    output.ascii('[');
    for (int index = 0; index < scopes.size(); index++) {
      if (index > 0) output.ascii(',');
      ExplainScopeJson scope = scopes.get(index);
      output.ascii('{');
      member(output, "id", scope.id());
      commaMember(output, "kind", scope.kind());
      scope.parentId().ifPresent(value -> commaMember(output, "parentId", value));
      scope.ownerWord().ifPresent(value -> commaMember(output, "ownerWord", value));
      output.ascii(',');
      name(output, "location");
      writeLocatedSpan(output, scope.location());
      output.ascii('}');
    }
    output.ascii(']');
  }

  private static void writeBindings(JsonOutput output, List<ExplainBindingJson> bindings) {
    output.ascii('[');
    for (int index = 0; index < bindings.size(); index++) {
      if (index > 0) output.ascii(',');
      ExplainBindingJson binding = bindings.get(index);
      output.ascii('{');
      member(output, "id", binding.id());
      commaMember(output, "name", binding.name());
      commaMember(output, "spelling", binding.spelling());
      commaMember(output, "kind", binding.kind());
      commaMember(output, "storage", binding.storage());
      commaMember(output, "scopeId", binding.scopeId());
      commaMember(output, "type", binding.type());
      binding
          .initializationOrder()
          .ifPresent(value -> commaMember(output, "initializationOrder", value));
      output.ascii(',');
      name(output, "declaration");
      writeLocatedSpan(output, binding.declaration());
      output.ascii(',');
      name(output, "uses");
      writeBindingUses(output, binding.uses());
      output.ascii('}');
    }
    output.ascii(']');
  }

  private static void writeBindingUses(JsonOutput output, List<ExplainBindingUseJson> uses) {
    output.ascii('[');
    for (int index = 0; index < uses.size(); index++) {
      if (index > 0) output.ascii(',');
      ExplainBindingUseJson use = uses.get(index);
      output.ascii('{');
      member(output, "spelling", use.spelling());
      commaMember(output, "kind", use.kind());
      output.ascii(',');
      name(output, "location");
      writeLocatedSpan(output, use.location());
      output.ascii('}');
    }
    output.ascii(']');
  }

  private static void writeParameterizedCapabilities(
      JsonOutput output, ExplainParameterizedCapabilitiesJson value) {
    output.ascii('{');
    member(output, "schemaVersion", value.schemaVersion());
    output.ascii(',');
    name(output, "declarations");
    output.ascii('[');
    for (int index = 0; index < value.declarations().size(); index++) {
      if (index > 0) output.ascii(',');
      ExplainConnectionDeclarationJson declaration = value.declarations().get(index);
      output.ascii('{');
      member(output, "name", declaration.name());
      commaMember(output, "spelling", declaration.spelling());
      output.ascii(',');
      name(output, "declaration");
      writeLocatedSpan(output, declaration.declaration());
      output.ascii(',');
      name(output, "uses");
      output.ascii('[');
      for (int useIndex = 0; useIndex < declaration.uses().size(); useIndex++) {
        if (useIndex > 0) output.ascii(',');
        ExplainConnectionUseJson use = declaration.uses().get(useIndex);
        output.ascii('{');
        member(output, "ownerWord", use.ownerWord());
        commaMember(output, "operation", use.operation());
        commaMember(output, "reachableFromMain", use.reachableFromMain());
        output.ascii(',');
        name(output, "location");
        writeLocatedSpan(output, use.location());
        output.ascii('}');
      }
      output.ascii(']');
      output.ascii('}');
    }
    output.ascii(']');
    output.ascii(',');
    name(output, "workspaceDeclarations");
    output.ascii('[');
    for (int index = 0; index < value.workspaceDeclarations().size(); index++) {
      if (index > 0) output.ascii(',');
      ExplainConnectionDeclarationJson declaration = value.workspaceDeclarations().get(index);
      output.ascii('{');
      member(output, "name", declaration.name());
      commaMember(output, "spelling", declaration.spelling());
      output.ascii(',');
      name(output, "declaration");
      writeLocatedSpan(output, declaration.declaration());
      output.ascii(',');
      name(output, "uses");
      output.ascii('[');
      for (int useIndex = 0; useIndex < declaration.uses().size(); useIndex++) {
        if (useIndex > 0) output.ascii(',');
        ExplainConnectionUseJson use = declaration.uses().get(useIndex);
        output.ascii('{');
        member(output, "ownerWord", use.ownerWord());
        commaMember(output, "operation", use.operation());
        commaMember(output, "reachableFromMain", use.reachableFromMain());
        output.ascii(',');
        name(output, "location");
        writeLocatedSpan(output, use.location());
        output.ascii('}');
      }
      output.ascii(']');
      output.ascii('}');
    }
    output.ascii(']');
    output.ascii(',');
    name(output, "summaryRequirements");
    writeRequirements(output, value.summaryRequirements());
    output.ascii(',');
    name(output, "userWords");
    output.ascii('[');
    for (int index = 0; index < value.userWords().size(); index++) {
      if (index > 0) output.ascii(',');
      ExplainParameterizedUserWordJson word = value.userWords().get(index);
      output.ascii('{');
      member(output, "name", word.name());
      output.ascii(',');
      name(output, "directRequirements");
      writeRequirements(output, word.directRequirements());
      output.ascii(',');
      name(output, "requirements");
      writeRequirements(output, word.requirements());
      output.ascii('}');
    }
    output.ascii(']');
    output.ascii('}');
  }

  private static void writeRequirements(
      JsonOutput output, List<ExplainResourceRequirementJson> values) {
    output.ascii('[');
    for (int index = 0; index < values.size(); index++) {
      if (index > 0) output.ascii(',');
      ExplainResourceRequirementJson value = values.get(index);
      output.ascii('{');
      member(output, "kind", value.kind());
      commaMember(output, "name", value.name());
      output.ascii(',');
      name(output, "operations");
      writeStrings(output, value.operations());
      output.ascii('}');
    }
    output.ascii(']');
  }

  private static void writeLocatedSpan(JsonOutput output, JsonSpan span) {
    output.ascii('{');
    name(output, "span");
    writeSpan(output, span);
    output.ascii('}');
  }

  private static void writeDiagnostics(JsonOutput output, List<DiagnosticJson> values) {
    output.ascii('[');
    for (int index = 0; index < values.size(); index++) {
      if (index > 0) output.ascii(',');
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
        output.ascii('{');
        member(output, "utf8Offset", offset.utf8Offset());
        output.ascii('}');
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
              output.ascii('{');
              member(output, "name", limit.name());
              commaMember(output, "limit", limit.limit());
              commaMember(output, "observed", limit.observed());
              output.ascii('}');
            });
    output.ascii('}');
  }

  private static void writeRelatedLocations(JsonOutput output, List<JsonRelatedLocation> values) {
    output.ascii('[');
    for (int index = 0; index < values.size(); index++) {
      if (index > 0) output.ascii(',');
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

  private static void writeFields(JsonOutput output, Map<String, String> fields) {
    output.ascii('{');
    int index = 0;
    for (Map.Entry<String, String> field : fields.entrySet()) {
      if (index++ > 0) output.ascii(',');
      name(output, field.getKey());
      output.string(field.getValue());
    }
    output.ascii('}');
  }

  private static void writeStrings(JsonOutput output, List<String> values) {
    output.ascii('[');
    for (int index = 0; index < values.size(); index++) {
      if (index > 0) output.ascii(',');
      output.string(values.get(index));
    }
    output.ascii(']');
  }

  private static void writeProblem(JsonOutput output, CliJsonProblem problem) {
    output.ascii('{');
    member(output, "kind", problem.kind());
    commaMember(output, "message", problem.message());
    output.ascii('}');
  }

  private static void member(JsonOutput output, String memberName, String value) {
    name(output, memberName);
    output.string(value);
  }

  private static void member(JsonOutput output, String memberName, long value) {
    name(output, memberName);
    output.number(value);
  }

  private static void commaMember(JsonOutput output, String memberName, String value) {
    output.ascii(',');
    member(output, memberName, value);
  }

  private static void commaMember(JsonOutput output, String memberName, long value) {
    output.ascii(',');
    member(output, memberName, value);
  }

  private static void commaMember(JsonOutput output, String memberName, boolean value) {
    output.ascii(',');
    name(output, memberName);
    output.ascii(value ? "true" : "false");
  }

  private static void name(JsonOutput output, String value) {
    output.string(value);
    output.ascii(':');
  }

  record RenderResult(byte[] bytes, int exitCode) {
    RenderResult {
      Objects.requireNonNull(bytes, "bytes");
    }
  }
}
