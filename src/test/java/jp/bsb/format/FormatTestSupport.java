package jp.bsb.format;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class FormatTestSupport {
  private FormatTestSupport() {}

  static byte[] resourceBytes(String relativePath) throws IOException {
    return resourceBytes("language-core", relativePath);
  }

  static byte[] ControlFlowResourceBytes(String relativePath) throws IOException {
    return resourceBytes("control-flow", relativePath);
  }

  static byte[] bindingsResourceBytes(String relativePath) throws IOException {
    return resourceBytes("bindings", relativePath);
  }

  static byte[] arraysResourceBytes(String relativePath) throws IOException {
    return resourceBytes("arrays", relativePath);
  }

  static byte[] numericsResourceBytes(String relativePath) throws IOException {
    return resourceBytes("numerics", relativePath);
  }

  static byte[] TextRegexResourceBytes(String relativePath) throws IOException {
    return resourceBytes("text-regex", relativePath);
  }

  static byte[] ExponentResourceBytes(String relativePath) throws IOException {
    return resourceBytes("exponent-literals", relativePath);
  }

  private static byte[] resourceBytes(String featureGroup, String relativePath) throws IOException {
    String resource = "/conformance/" + featureGroup + '/' + relativePath;
    try (var input = FormatTestSupport.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }

  static String resourceText(String relativePath) throws IOException {
    return new String(resourceBytes(relativePath), StandardCharsets.UTF_8);
  }

  static String ControlFlowResourceText(String relativePath) throws IOException {
    return new String(ControlFlowResourceBytes(relativePath), StandardCharsets.UTF_8);
  }

  static String bindingsResourceText(String relativePath) throws IOException {
    return new String(bindingsResourceBytes(relativePath), StandardCharsets.UTF_8);
  }

  static String arraysResourceText(String relativePath) throws IOException {
    return new String(arraysResourceBytes(relativePath), StandardCharsets.UTF_8);
  }

  static String numericsResourceText(String relativePath) throws IOException {
    return new String(numericsResourceBytes(relativePath), StandardCharsets.UTF_8);
  }

  static String TextRegexResourceText(String relativePath) throws IOException {
    return new String(TextRegexResourceBytes(relativePath), StandardCharsets.UTF_8);
  }

  static FormatResult formatResource(String sourceName) throws IOException {
    return new SourceFormatter().format(sourceName, resourceBytes("sources/" + sourceName));
  }

  static FormatResult formatControlFlowResource(String sourceName) throws IOException {
    return new SourceFormatter()
        .format(sourceName, ControlFlowResourceBytes("sources/" + sourceName));
  }

  static FormatResult formatBindingResource(String sourceName) throws IOException {
    return new SourceFormatter().format(sourceName, bindingsResourceBytes("sources/" + sourceName));
  }

  static FormatResult formatArrayResource(String sourceName) throws IOException {
    return new SourceFormatter().format(sourceName, arraysResourceBytes("sources/" + sourceName));
  }
}
