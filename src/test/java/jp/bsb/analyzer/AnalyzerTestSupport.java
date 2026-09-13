package jp.bsb.analyzer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class AnalyzerTestSupport {
  private AnalyzerTestSupport() {}

  static AnalysisResult checkResource(String name) throws IOException {
    return checkResource("language-core", name);
  }

  static AnalysisResult checkControlFlowResource(String name) throws IOException {
    return checkResource("control-flow", name);
  }

  static AnalysisResult checkBindingResource(String name) throws IOException {
    return checkResource("bindings", name);
  }

  static AnalysisResult checkArrayResource(String name) throws IOException {
    return checkResource("arrays", name);
  }

  static AnalysisResult checkTextRegexResource(String name) throws IOException {
    return checkResource("text-regex", name);
  }

  static AnalysisResult checkHostIoResource(String name) throws IOException {
    return checkResource("host-io", name);
  }

  static AnalysisResult checkBindingChapterResource() throws IOException {
    String resource = "/conformance/bindings/chapter/bindings-chapter.bsb";
    try (var input = AnalyzerTestSupport.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return new SourceChecker().check("bindings-chapter.bsb", input.readAllBytes());
    }
  }

  static AnalysisResult checkArrayChapterResource() throws IOException {
    String resource = "/conformance/arrays/chapter/arrays-chapter.bsb";
    try (var input = AnalyzerTestSupport.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return new SourceChecker().check("arrays-chapter.bsb", input.readAllBytes());
    }
  }

  private static AnalysisResult checkResource(String featureGroup, String name) throws IOException {
    String resource = "/conformance/" + featureGroup + "/sources/" + name;
    try (var input = AnalyzerTestSupport.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalArgumentException("missing test resource: " + resource);
      }
      return new SourceChecker().check(name, input.readAllBytes());
    }
  }

  static AnalysisResult checkText(String text) {
    return new SourceChecker().check("test.bsb", text.getBytes(StandardCharsets.UTF_8));
  }
}
