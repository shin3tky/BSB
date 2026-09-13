package jp.bsb.frontend;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jp.bsb.diagnostics.DiagnosticCollector;

final class ParserTestSupport {
  private ParserTestSupport() {}

  static ParsedSource parseText(String path, String text) {
    var diagnostics = new DiagnosticCollector();
    var source = new SourceText(path, text, 0);
    var lexResult = new Lexer().lex(source, diagnostics);
    var parseResult = new Parser().parse(source, lexResult, diagnostics);
    return new ParsedSource(source, lexResult, parseResult, diagnostics);
  }

  static ParsedSource parseResource(String name) throws IOException {
    return parseResource("language-core", name);
  }

  static ParsedSource parseControlFlowResource(String name) throws IOException {
    return parseResource("control-flow", name);
  }

  static ParsedSource parseBindingResource(String name) throws IOException {
    return parseResource("bindings", name);
  }

  static ParsedSource parseArrayResource(String name) throws IOException {
    return parseResource("arrays", name);
  }

  static ParsedSource parseTextRegexResource(String name) throws IOException {
    return parseResource("text-regex", name);
  }

  static ParsedSource parseArrayChapter(String name) throws IOException {
    return parseChapter("arrays", name);
  }

  static ParsedSource parseBindingChapter(String name) throws IOException {
    return parseChapter("bindings", name);
  }

  private static ParsedSource parseChapter(String featureGroup, String name) throws IOException {
    String resource = "/conformance/" + featureGroup + "/chapter/" + name;
    byte[] bytes;
    try (var input = ParserTestSupport.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      bytes = input.readAllBytes();
    }
    return parseBytes(name, bytes);
  }

  private static ParsedSource parseResource(String featureGroup, String name) throws IOException {
    String resource = "/conformance/" + featureGroup + "/sources/" + name;
    byte[] bytes;
    try (var input = ParserTestSupport.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      bytes = input.readAllBytes();
    }
    return parseBytes(name, bytes);
  }

  static ParsedSource parseBytes(String path, byte[] bytes) {
    var diagnostics = new DiagnosticCollector();
    SourceText source = new Utf8SourceReader().read(path, bytes, diagnostics).orElseThrow();
    LexResult lexResult = new Lexer().lex(source, diagnostics);
    ParseResult parseResult = new Parser().parse(source, lexResult, diagnostics);
    return new ParsedSource(source, lexResult, parseResult, diagnostics);
  }

  static String resourceText(String name) throws IOException {
    String resource = "/conformance/language-core/sources/" + name;
    try (var input = ParserTestSupport.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  record ParsedSource(
      SourceText source,
      LexResult lexResult,
      ParseResult parseResult,
      DiagnosticCollector diagnostics) {}
}
