package jp.bsb.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExternalLibraryBoundaryTest {
  private static final Path PRODUCTION_SOURCES = Path.of("src/main/java");
  private static final Map<String, Path> ALLOWED_BOUNDARIES =
      Map.of(
          "com.ibm.icu.", Path.of("jp/bsb/adapter/IcuUnicodeAdapter.java"),
          "org.tomlj.", Path.of("jp/bsb/adapter/TomlAdapter.java"),
          "com.google.re2j.", Path.of("jp/bsb/regex/Re2RegexCompiler.java"));

  @Test
  void externalLibraryReferencesStayInsideTheirDeclaredBoundaries() throws IOException {
    try (var paths = Files.walk(PRODUCTION_SOURCES)) {
      for (Path source : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
        String text = Files.readString(source);
        Path relative = PRODUCTION_SOURCES.relativize(source);
        for (Map.Entry<String, Path> boundary : ALLOWED_BOUNDARIES.entrySet()) {
          if (text.contains(boundary.getKey())) {
            assertEquals(boundary.getValue(), relative, source.toString());
          }
        }
      }
    }
  }
}
