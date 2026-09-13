package jp.bsb.frontend;

import java.io.IOException;
import jp.bsb.diagnostics.DiagnosticCollector;

final class LexerTestSupport {
  private LexerTestSupport() {}

  static LexedSource lexText(String path, String text) {
    var diagnostics = new DiagnosticCollector();
    var source = new SourceText(path, text, 0);
    var result = new Lexer().lex(source, diagnostics);
    return new LexedSource(source, result, diagnostics);
  }

  static LexedSource lexResource(String name) throws IOException {
    return lexResource("language-core", name);
  }

  static LexedSource lexControlFlowResource(String name) throws IOException {
    return lexResource("control-flow", name);
  }

  static LexedSource lexBindingResource(String name) throws IOException {
    return lexResource("bindings", name);
  }

  static LexedSource lexArrayResource(String name) throws IOException {
    return lexResource("arrays", name);
  }

  static LexedSource lexTextRegexResource(String name) throws IOException {
    return lexResource("text-regex", name);
  }

  private static LexedSource lexResource(String featureGroup, String name) throws IOException {
    String resource = "/conformance/" + featureGroup + "/sources/" + name;
    byte[] bytes;
    try (var input = LexerTestSupport.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      bytes = input.readAllBytes();
    }
    var diagnostics = new DiagnosticCollector();
    SourceText source = new Utf8SourceReader().read(name, bytes, diagnostics).orElseThrow();
    LexResult result = new Lexer().lex(source, diagnostics);
    return new LexedSource(source, result, diagnostics);
  }

  record LexedSource(SourceText source, LexResult result, DiagnosticCollector diagnostics) {}
}
