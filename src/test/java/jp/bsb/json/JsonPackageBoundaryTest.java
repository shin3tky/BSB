package jp.bsb.json;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonPackageBoundaryTest {
  @Test
  void productionCodecHasNoCliRuntimeOrTestParserDependency() throws IOException {
    Path packageDirectory = Path.of("src/main/java/jp/bsb/json");
    List<Path> sources;
    try (var paths = Files.list(packageDirectory)) {
      sources = paths.filter(path -> path.toString().endsWith(".java")).toList();
    }

    assertFalse(sources.isEmpty());
    for (Path source : sources) {
      String text = Files.readString(source);
      assertFalse(text.contains("jp.bsb.cli"), source.toString());
      assertFalse(text.contains("jp.bsb.runtime"), source.toString());
      assertFalse(text.contains("StrictJsonParser"), source.toString());
      assertTrue(text.startsWith("package jp.bsb.json;"), source.toString());
    }
  }
}
