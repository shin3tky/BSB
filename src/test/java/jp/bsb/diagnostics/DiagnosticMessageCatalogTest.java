package jp.bsb.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DiagnosticMessageCatalogTest {
  @Test
  void loadsUtf8MessagesAndReplacesStructuredFields() throws IOException {
    var catalog = DiagnosticTestSupport.loadMessages();
    var diagnostic =
        Diagnostic.builder(
                DiagnosticCode.E_IDENTIFIER_TOO_LONG,
                Severity.ERROR,
                DiagnosticStage.LEXICAL,
                "入力.bsb",
                new SourcePosition(0, 1, 1))
            .limit("identifierCodePoints", 128, 129)
            .build();

    assertEquals("正規化後の識別子は128コードポイントまでですが、129コードポイントあります。", catalog.format(diagnostic));
  }

  @Test
  void rejectsDuplicateMessageKeys() throws IOException {
    String messages;
    try (var input =
        DiagnosticMessageCatalogTest.class.getResourceAsStream(
            "/conformance/language-core/messages.properties")) {
      messages = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }

    String duplicate = messages + "\nE_INVALID_UTF8=重複\n";
    assertThrows(
        IllegalArgumentException.class,
        () -> DiagnosticMessageCatalog.load(new StringReader(duplicate)));
  }

  @Test
  void rejectsDuplicateMessageKeysAcrossFeatureFiles() throws IOException {
    String Core = resourceText("/conformance/language-core/messages.properties");
    String ControlFlow =
        resourceText("/conformance/control-flow/messages.properties") + "\nE_INVALID_UTF8=重複\n";

    assertThrows(
        IllegalArgumentException.class,
        () -> DiagnosticMessageCatalog.load(new StringReader(Core), new StringReader(ControlFlow)));
  }

  @Test
  void formatsEveryControlFlowCodeFromStructuredDiagnostics() throws IOException {
    var catalog = DiagnosticTestSupport.loadMessages();

    for (DiagnosticCode code : ControlFlowCodes()) {
      Severity severity =
          code == DiagnosticCode.W_UNREACHABLE_CODE ? Severity.WARNING : Severity.ERROR;
      Diagnostic diagnostic =
          Diagnostic.builder(code, severity, stageFor(code), "入力.bsb", new SourcePosition(0, 1, 1))
              .field("loopKind", "条件")
              .field("actualType", "整数")
              .field("pathKind", "継続経路")
              .field("word", "試す")
              .field("count", "-1")
              .field("cause", "戻る")
              .limit("syntaxDepth", 256, 257)
              .build();

      String message = catalog.format(diagnostic);

      assertFalse(message.isBlank(), code.name());
      assertFalse(message.contains("{"), code.name());
      assertFalse(message.contains("}"), code.name());
    }
  }

  @Test
  void formatsEveryBindingCodeFromStructuredDiagnostics() throws IOException {
    var catalog = DiagnosticTestSupport.loadMessages();

    for (DiagnosticCode code : bindingsCodes()) {
      Diagnostic diagnostic =
          Diagnostic.builder(
                  code, Severity.ERROR, stageFor(code), "入力.bsb", new SourcePosition(0, 1, 1))
              .field("name", "合計")
              .field("target", "合計")
              .field("element", "ならば")
              .field("outerKind", "大域変数")
              .field("targetKind", "単語")
              .field("actualCount", "2")
              .field("callKind", "利用者定義単語")
              .field("word", "計算する")
              .field("expectedType", "整数")
              .field("actualType", "文字列")
              .limit("bindings", 10_000, 10_001)
              .build();

      String message = catalog.format(diagnostic);

      assertFalse(message.isBlank(), code.name());
      assertFalse(message.contains("{"), code.name());
      assertFalse(message.contains("}"), code.name());
    }
  }

  @Test
  void formatsEveryTextRegexCodeFromStructuredDiagnostics() throws IOException {
    var catalog = DiagnosticTestSupport.loadMessages();

    for (DiagnosticCode code : TextRegexCodes()) {
      Diagnostic diagnostic =
          Diagnostic.builder(
                  code,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  "入力.bsb",
                  new SourcePosition(0, 1, 1))
              .field("flag", "x")
              .field("patternOffset", "3")
              .field("token", "\\1")
              .field("length", "2")
              .field("index", "3")
              .field("start", "1")
              .field("end", "3")
              .field("word", "正規表現を含む")
              .field("inputPreview", "「入力」")
              .field("numericType", "整数")
              .field("group", "name")
              .field("reference", "$99")
              .limit("TextRegexUnits", 64, 65)
              .build();

      String message = catalog.format(diagnostic);

      assertFalse(message.isBlank(), code.name());
      assertFalse(message.contains("{"), code.name());
      assertFalse(message.contains("}"), code.name());
    }
  }

  @Test
  void rejectsMissingTemplateFields() throws IOException {
    var catalog = DiagnosticTestSupport.loadMessages();
    var diagnostic =
        Diagnostic.builder(
                DiagnosticCode.E_EXPECTED_WORD_END,
                Severity.ERROR,
                DiagnosticStage.SYNTAX,
                "入力.bsb",
                new SourcePosition(0, 1, 1))
            .build();

    assertThrows(IllegalArgumentException.class, () -> catalog.format(diagnostic));
  }

  @Test
  void rejectsBracesLeftAfterTemplateExpansion() throws IOException {
    String messages;
    try (var input =
        DiagnosticMessageCatalogTest.class.getResourceAsStream(
            "/conformance/language-core/messages.properties")) {
      messages = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
    messages =
        messages.replace("E_MISSING_MAIN=開始点となる「メイン」がありません。", "E_MISSING_MAIN=開始点となる「メイン」がありません。{");
    String ControlFlowMessages;
    try (var input =
        DiagnosticMessageCatalogTest.class.getResourceAsStream(
            "/conformance/control-flow/messages.properties")) {
      ControlFlowMessages = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
    String bindingsMessages = resourceText("/conformance/bindings/messages.properties");
    String arraysMessages = resourceText("/conformance/arrays/messages.properties");
    String numericsMessages = resourceText("/conformance/numerics/messages.properties");
    String TextRegexMessages = resourceText("/conformance/text-regex/messages.properties");
    String HostIoMessages = resourceText("/conformance/host-io/messages.properties");
    String jsonMessages = resourceText("/conformance/json/messages.properties");
    String OptionalMessages = resourceText("/conformance/optional-values/messages.properties");
    String ResultMessages = resourceText("/conformance/result-values/messages.properties");
    String ConnectionMessages =
        resourceText("/conformance/logical-connections/messages.properties");
    String ByteSequenceMessages = resourceText("/conformance/byte-sequences/messages.properties");
    String httpsMessages = resourceText("/conformance/https/messages.properties");
    String NestedArrayMessages = resourceText("/conformance/nested-arrays/messages.properties");
    String WorkspaceTableMessages =
        resourceText("/conformance/workspace-tables/messages.properties");
    var catalog =
        DiagnosticMessageCatalog.load(
            new StringReader(messages),
            new StringReader(ControlFlowMessages),
            new StringReader(bindingsMessages),
            new StringReader(arraysMessages),
            new StringReader(numericsMessages),
            new StringReader(TextRegexMessages),
            new StringReader(HostIoMessages),
            new StringReader(jsonMessages),
            new StringReader(OptionalMessages),
            new StringReader(ResultMessages),
            new StringReader(ConnectionMessages),
            new StringReader(ByteSequenceMessages),
            new StringReader(httpsMessages),
            new StringReader(NestedArrayMessages),
            new StringReader(WorkspaceTableMessages));
    var diagnostic =
        Diagnostic.builder(
                DiagnosticCode.E_MISSING_MAIN,
                Severity.ERROR,
                DiagnosticStage.NAME,
                "入力.bsb",
                new SourcePosition(0, 1, 1))
            .build();

    assertThrows(IllegalArgumentException.class, () -> catalog.format(diagnostic));
  }

  @Test
  void formatsEveryConnectionCodeFromStructuredDiagnostics() throws IOException {
    var catalog = DiagnosticTestSupport.loadMessages();
    for (DiagnosticCode code : ConnectionCodes()) {
      Diagnostic diagnostic =
          Diagnostic.builder(
                  code, Severity.ERROR, stageFor(code), "入力.bsb", new SourcePosition(0, 1, 1))
              .field("connection", "顧客管理API")
              .field("word", "論理接続を確認する")
              .field("argumentIndex", "1")
              .field("reason", "BASE_URI_INVALID")
              .field("limit", "10000")
              .build();
      String message = catalog.format(diagnostic);
      assertFalse(message.isBlank(), code.name());
      assertFalse(message.contains("{"), code.name());
      assertFalse(message.contains("}"), code.name());
    }
  }

  @Test
  void formatsEveryByteSequenceCodeFromStructuredDiagnostics() throws IOException {
    var catalog = DiagnosticTestSupport.loadMessages();
    for (DiagnosticCode code : ByteSequenceCodes()) {
      Diagnostic diagnostic =
          Diagnostic.builder(
                  code,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  "入力.bsb",
                  new SourcePosition(0, 1, 1))
              .field("word", "バイト列の一部を取り出す")
              .field("start", "-1")
              .field("end", "2")
              .field("length", "4")
              .limit("byteSequenceBytes", 67_108_864, 67_108_865)
              .build();
      String message = catalog.format(diagnostic);
      assertFalse(message.isBlank(), code.name());
      assertFalse(message.contains("{"), code.name());
      assertFalse(message.contains("}"), code.name());
    }
  }

  private static Set<DiagnosticCode> ByteSequenceCodes() {
    return EnumSet.of(
        DiagnosticCode.E_BYTE_SEQUENCE_RANGE_OUT_OF_BOUNDS,
        DiagnosticCode.E_BYTE_SEQUENCE_SIZE_LIMIT,
        DiagnosticCode.E_BYTE_SEQUENCE_CONSTRUCTION_LIMIT,
        DiagnosticCode.E_BYTE_SEQUENCE_WORK_LIMIT);
  }

  private static Set<DiagnosticCode> ConnectionCodes() {
    return EnumSet.of(
        DiagnosticCode.E_LOGICAL_CONNECTION_DECLARATION_VALUE,
        DiagnosticCode.E_LOGICAL_CONNECTION_DECLARATION_SCOPE,
        DiagnosticCode.E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT_START,
        DiagnosticCode.E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT,
        DiagnosticCode.E_LOGICAL_CONNECTION_ARGUMENT_COUNT,
        DiagnosticCode.E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT_END,
        DiagnosticCode.E_LOGICAL_CONNECTION_LIMIT,
        DiagnosticCode.E_UNDECLARED_LOGICAL_CONNECTION,
        DiagnosticCode.E_LOGICAL_CONNECTION_REFERENCE_NOT_ALLOWED,
        DiagnosticCode.E_LOGICAL_CONNECTION_NOT_CONFIGURED,
        DiagnosticCode.E_LOGICAL_CONNECTION_ACCESS_DENIED,
        DiagnosticCode.E_LOGICAL_CONNECTION_CONFIGURATION_INVALID);
  }

  private static Set<DiagnosticCode> ControlFlowCodes() {
    return EnumSet.of(
        DiagnosticCode.E_UNEXPECTED_ELSE,
        DiagnosticCode.E_DUPLICATE_ELSE,
        DiagnosticCode.E_EXPECTED_IF_END,
        DiagnosticCode.E_UNEXPECTED_BLOCK_END,
        DiagnosticCode.E_UNEXPECTED_LOOP_END,
        DiagnosticCode.E_EXPECTED_LOOP_END,
        DiagnosticCode.E_EXPECTED_LOOP_CONDITION_SEPARATOR,
        DiagnosticCode.E_UNEXPECTED_LOOP_SEPARATOR,
        DiagnosticCode.E_SYNTAX_DEPTH_LIMIT,
        DiagnosticCode.E_CONDITION_STACK_UNDERFLOW,
        DiagnosticCode.E_CONDITION_TYPE_MISMATCH,
        DiagnosticCode.E_BRANCH_STACK_MISMATCH,
        DiagnosticCode.E_REPEAT_COUNT_UNDERFLOW,
        DiagnosticCode.E_REPEAT_COUNT_TYPE_MISMATCH,
        DiagnosticCode.E_LOOP_CONDITION_MISMATCH,
        DiagnosticCode.E_LOOP_STACK_MISMATCH,
        DiagnosticCode.E_BREAK_OUTSIDE_LOOP,
        DiagnosticCode.E_CONTINUE_OUTSIDE_LOOP,
        DiagnosticCode.E_RETURN_EFFECT_MISMATCH,
        DiagnosticCode.E_NEGATIVE_REPEAT_COUNT,
        DiagnosticCode.W_UNREACHABLE_CODE);
  }

  private static Set<DiagnosticCode> bindingsCodes() {
    return EnumSet.of(
        DiagnosticCode.E_DECLARATION_ADJACENCY,
        DiagnosticCode.E_EXPECTED_DECLARATION_KIND,
        DiagnosticCode.E_DECLARATION_HEADER_COMMENT_NOT_ALLOWED,
        DiagnosticCode.E_EXPECTED_DECLARATION_END,
        DiagnosticCode.E_UNEXPECTED_DECLARATION_END,
        DiagnosticCode.E_EXPECTED_ASSIGNMENT_VALUE_PARTICLE,
        DiagnosticCode.E_EXPECTED_ASSIGNMENT_TARGET,
        DiagnosticCode.E_EXPECTED_ASSIGNMENT_TARGET_PARTICLE,
        DiagnosticCode.E_INITIALIZER_ELEMENT_NOT_ALLOWED,
        DiagnosticCode.E_REFERENCE_BEFORE_INITIALIZATION,
        DiagnosticCode.E_REFERENCE_BEFORE_DECLARATION,
        DiagnosticCode.E_BINDING_OUT_OF_SCOPE,
        DiagnosticCode.E_NAME_SHADOWING,
        DiagnosticCode.E_ASSIGN_TO_CONSTANT,
        DiagnosticCode.E_ASSIGNMENT_TARGET_NOT_VARIABLE,
        DiagnosticCode.E_UNDEFINED_ASSIGNMENT_TARGET,
        DiagnosticCode.E_INITIALIZER_VALUE_MISSING,
        DiagnosticCode.E_INITIALIZER_VALUE_COUNT,
        DiagnosticCode.E_INITIALIZER_CALL_NOT_ALLOWED,
        DiagnosticCode.E_ASSIGNMENT_STACK_UNDERFLOW,
        DiagnosticCode.E_ASSIGNMENT_TYPE_MISMATCH,
        DiagnosticCode.E_GLOBAL_BINDING_LIMIT,
        DiagnosticCode.E_LOCAL_BINDING_LIMIT,
        DiagnosticCode.E_BINDING_LIMIT);
  }

  private static Set<DiagnosticCode> TextRegexCodes() {
    return EnumSet.of(
        DiagnosticCode.E_UNTERMINATED_REGEX_LITERAL,
        DiagnosticCode.E_NEWLINE_IN_REGEX_LITERAL,
        DiagnosticCode.E_REGEX_FLAG,
        DiagnosticCode.E_REGEX_SYNTAX,
        DiagnosticCode.E_REGEX_UNSUPPORTED_CONSTRUCT,
        DiagnosticCode.E_REGEX_PATTERN_LIMIT,
        DiagnosticCode.E_REGEX_CAPTURE_LIMIT,
        DiagnosticCode.E_REGEX_PROGRAM_LIMIT,
        DiagnosticCode.E_STRING_INDEX_OUT_OF_BOUNDS,
        DiagnosticCode.E_STRING_RANGE_OUT_OF_BOUNDS,
        DiagnosticCode.E_CODE_POINT_INDEX_OUT_OF_BOUNDS,
        DiagnosticCode.E_CODE_POINT_RANGE_OUT_OF_BOUNDS,
        DiagnosticCode.E_EMPTY_SEARCH_TEXT,
        DiagnosticCode.E_EMPTY_DELIMITER,
        DiagnosticCode.E_INTEGER_TEXT_INVALID,
        DiagnosticCode.E_DECIMAL_TEXT_INVALID,
        DiagnosticCode.E_NUMERIC_TEXT_DIGIT_LIMIT,
        DiagnosticCode.E_STRING_UTF8_LIMIT,
        DiagnosticCode.E_REGEX_NO_MATCH,
        DiagnosticCode.E_REGEX_GROUP_NOT_FOUND,
        DiagnosticCode.E_REGEX_GROUP_UNMATCHED,
        DiagnosticCode.E_REGEX_REPLACEMENT_TEMPLATE,
        DiagnosticCode.E_REGEX_WORK_LIMIT,
        DiagnosticCode.E_REGEX_TOTAL_WORK_LIMIT);
  }

  private static DiagnosticStage stageFor(DiagnosticCode code) {
    if (code == DiagnosticCode.E_NEGATIVE_REPEAT_COUNT
        || code == DiagnosticCode.E_LOGICAL_CONNECTION_NOT_CONFIGURED
        || code == DiagnosticCode.E_LOGICAL_CONNECTION_ACCESS_DENIED
        || code == DiagnosticCode.E_LOGICAL_CONNECTION_CONFIGURATION_INVALID) {
      return DiagnosticStage.RUNTIME;
    }
    if (code.name().startsWith("E_UNEXPECTED_")
        || code == DiagnosticCode.E_DUPLICATE_ELSE
        || code.name().startsWith("E_EXPECTED_")
        || code == DiagnosticCode.E_SYNTAX_DEPTH_LIMIT
        || code == DiagnosticCode.E_DECLARATION_ADJACENCY
        || code == DiagnosticCode.E_DECLARATION_HEADER_COMMENT_NOT_ALLOWED
        || code == DiagnosticCode.E_INITIALIZER_ELEMENT_NOT_ALLOWED
        || code == DiagnosticCode.E_LOGICAL_CONNECTION_DECLARATION_VALUE
        || code == DiagnosticCode.E_LOGICAL_CONNECTION_DECLARATION_SCOPE
        || code == DiagnosticCode.E_LOGICAL_CONNECTION_ARGUMENT_COUNT) {
      return DiagnosticStage.SYNTAX;
    }
    if (code.name().startsWith("E_REFERENCE_")
        || code == DiagnosticCode.E_BINDING_OUT_OF_SCOPE
        || code == DiagnosticCode.E_NAME_SHADOWING
        || code == DiagnosticCode.E_ASSIGN_TO_CONSTANT
        || code == DiagnosticCode.E_ASSIGNMENT_TARGET_NOT_VARIABLE
        || code == DiagnosticCode.E_UNDEFINED_ASSIGNMENT_TARGET
        || code.name().endsWith("BINDING_LIMIT")
        || code == DiagnosticCode.E_LOGICAL_CONNECTION_LIMIT
        || code == DiagnosticCode.E_UNDECLARED_LOGICAL_CONNECTION
        || code == DiagnosticCode.E_LOGICAL_CONNECTION_REFERENCE_NOT_ALLOWED) {
      return DiagnosticStage.NAME;
    }
    return DiagnosticStage.TYPE_AND_STACK;
  }

  private static String resourceText(String path) throws IOException {
    try (var input = DiagnosticMessageCatalogTest.class.getResourceAsStream(path)) {
      if (input == null) {
        throw new IllegalStateException("missing test resource: " + path);
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
