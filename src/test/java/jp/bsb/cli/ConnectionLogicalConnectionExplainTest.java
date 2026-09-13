package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jp.bsb.conformance.ConnectionConformanceData;
import org.junit.jupiter.api.Test;

class ConnectionLogicalConnectionExplainTest {
  @Test
  void rendersTheIndependentParameterizedCapabilityDocumentExactly() throws Exception {
    Invocation invocation =
        invoke("tests/conformance/logical-connections/sources/CONN-N-explain.bsb");

    assertEquals(0, invocation.exitCode());
    assertEquals(0, invocation.stderr().length);
    Map<String, Object> document = object(StrictJsonParser.parse(invocation.stdout()));
    Map<String, Object> historicalExpected =
        object(
            StrictJsonParser.parse(
                ConnectionConformanceData.expectedParameterizedCapabilities()
                    .getBytes(StandardCharsets.UTF_8)));
    Map<String, Object> expected = new LinkedHashMap<>();
    historicalExpected.forEach(
        (key, value) -> {
          expected.put(key, value);
          if (key.equals("declarations")) expected.put("workspaceDeclarations", List.of());
        });
    assertEquals(expected, object(document.get("parameterizedCapabilities")));
    assertTrue(
        object(document.get("summary"))
            .get("capabilities")
            .toString()
            .contains("connection.resolve"));
  }

  @Test
  void omitsPartialParameterizedDataOnStaticFailure() {
    Invocation invocation =
        invoke("tests/conformance/logical-connections/sources/CONN-F-undeclared.bsb");

    assertEquals(8, invocation.exitCode());
    Map<String, Object> document = object(StrictJsonParser.parse(invocation.stdout()));
    assertFalse(document.containsKey("parameterizedCapabilities"));
    assertEquals(java.util.List.of(), document.get("builtinWords"));
    assertEquals(java.util.List.of(), document.get("userWords"));
  }

  private static Invocation invoke(String source) {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    int exit = new BsbCli().run(new String[] {"explain", "--json", source}, stdout, stderr);
    return new Invocation(exit, stdout.toByteArray(), stderr.toByteArray());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> object(Object value) {
    return (Map<String, Object>) value;
  }

  private record Invocation(int exitCode, byte[] stdout, byte[] stderr) {}
}
