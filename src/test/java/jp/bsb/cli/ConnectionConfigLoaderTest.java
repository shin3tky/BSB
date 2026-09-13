package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import jp.bsb.runtime.ConnectionResolution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ConnectionConfigLoaderTest {
  @TempDir Path temporaryDirectory;
  private int fileNumber;

  @Test
  void loadsMultipleConnectionsDefaultsAndBothCredentialSources() throws Exception {
    String directSecret = "DIRECT_CONFIG_SECRET";
    String environmentSecret = "ENVIRONMENT_CONFIG_SECRET";
    Path config =
        write(
            "schema-version = 1\n"
                + "\n"
                + "[connections.\"顧客管理API\"]\n"
                + "base-uri = \"https://api.example.test/base/\"\n"
                + "allowed-methods = [\"POST\"]\n"
                + "\n"
                + "[connections.\"顧客管理API\".authentication]\n"
                + "kind = \"api-key\"\n"
                + "value = \""
                + directSecret
                + "\"\n"
                + "\n"
                + "[connections.\"公開.API\"]\n"
                + "base-uri = \"https://public.example.test/\"\n"
                + "allowed-methods = [\"GET\"]\n"
                + "\n"
                + "[connections.\"公開.API\".authentication]\n"
                + "kind = \"api-key\"\n"
                + "header = \"x-public-key\"\n"
                + "value-env = \"PUBLIC_API_KEY\"\n");

    LoadedConnectionConfig loaded =
        new ConnectionConfigLoader(Map.of("PUBLIC_API_KEY", environmentSecret)::get).load(config);
    ConnectionResolution first = loaded.resolver().resolve("顧客管理API", "resolve");
    ConnectionResolution second = loaded.resolver().resolve("公開.API", "resolve");

    assertEquals(ConnectionResolution.State.RESOLVED, first.state());
    assertEquals("https://api.example.test/base/", first.policy().orElseThrow().baseUri());
    assertEquals(5_000, first.policy().orElseThrow().connectTimeoutMilliseconds());
    assertEquals(10_000, first.policy().orElseThrow().responseTimeoutMilliseconds());
    assertEquals(1_048_576, first.policy().orElseThrow().maximumRequestBytes());
    assertEquals("apiKey", first.policy().orElseThrow().authenticationKind());
    assertEquals(ConnectionResolution.State.RESOLVED, second.state());
    assertEquals(
        ConnectionResolution.State.NOT_CONFIGURED,
        loaded.resolver().resolve("未設定", "resolve").state());
    assertFalse(loaded.toString().contains(directSecret));
    assertFalse(loaded.toString().contains(environmentSecret));
    assertFalse(first.toString().contains(directSecret));
  }

  @Test
  void acceptsNoneAuthenticationAndExplicitLimits() throws Exception {
    Path config =
        write(
            "schema-version = 1\n"
                + "[connections.API]\n"
                + "base-uri = \"https://api.example.test/\"\n"
                + "allowed-methods = [\"GET\", \"HEAD\"]\n"
                + "connect-timeout-ms = 1\n"
                + "response-timeout-ms = 2\n"
                + "maximum-request-bytes = 3\n"
                + "maximum-response-bytes = 4\n"
                + "[connections.API.authentication]\n"
                + "kind = \"none\"\n");

    var policy =
        new ConnectionConfigLoader(name -> null)
            .load(config)
            .resolver()
            .resolve("API", "resolve")
            .policy()
            .orElseThrow();

    assertEquals(1, policy.connectTimeoutMilliseconds());
    assertEquals(2, policy.responseTimeoutMilliseconds());
    assertEquals(3, policy.maximumRequestBytes());
    assertEquals(4, policy.maximumResponseBytes());
    assertEquals("none", policy.authenticationKind());
  }

  @Test
  void rejectsParserErrorsWithoutLeakingTheInputLine() throws IOException {
    String secret = "CONFIG_PARSE_SECRET";
    Path syntax = write("schema-version = 1\nsecret = \"" + secret + "\n");
    ConnectionConfigException failure =
        assertThrows(
            ConnectionConfigException.class,
            () -> new ConnectionConfigLoader(name -> null).load(syntax));

    assertFalse(failure.getMessage().contains(secret));
    assertFalse(failure.toString().contains(secret));
  }

  @ParameterizedTest
  @MethodSource("invalidDocuments")
  void rejectsUnknownMissingMistypedAndInvalidPolicyFields(String document) throws IOException {
    Path config = write(document);

    assertThrows(
        ConnectionConfigException.class,
        () -> new ConnectionConfigLoader(name -> null).load(config));
  }

  @Test
  void rejectsMissingEnvironmentAndMutuallyExclusiveCredentialValues() throws IOException {
    Path missingEnvironment =
        writeNamed("missing-env.toml", baseApiKeyConfig() + "value-env = \"MISSING_KEY\"\n");
    Path both =
        writeNamed("both.toml", baseApiKeyConfig() + "value = \"one\"\nvalue-env = \"KEY\"\n");
    Path invalidHeader =
        writeNamed(
            "invalid-header.toml", baseApiKeyConfig() + "header = \"host\"\nvalue = \"secret\"\n");
    Path invalidValue = writeNamed("invalid-value.toml", baseApiKeyConfig() + "value = \"値\"\n");

    for (Path path : List.of(missingEnvironment, both, invalidHeader, invalidValue)) {
      assertThrows(
          ConnectionConfigException.class,
          () -> new ConnectionConfigLoader(name -> null).load(path));
    }
  }

  @Test
  void rejectsBomInvalidUtf8OversizeAndMissingFiles() throws IOException {
    Path bom = temporaryDirectory.resolve("bom.toml");
    Files.write(bom, new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf, 'x'});
    Path invalidUtf8 = temporaryDirectory.resolve("invalid-utf8.toml");
    Files.write(invalidUtf8, new byte[] {(byte) 0xc3, 0x28});
    Path oversize = temporaryDirectory.resolve("oversize.toml");
    Files.write(oversize, new byte[(int) ConnectionConfigLoader.MAX_CONFIG_BYTES + 1]);
    Path missing = temporaryDirectory.resolve("missing.toml");

    for (Path path : List.of(bom, invalidUtf8, oversize, missing)) {
      assertThrows(
          ConnectionConfigException.class,
          () -> new ConnectionConfigLoader(name -> null).load(path));
    }
  }

  private static Stream<Arguments> invalidDocuments() {
    String valid = baseNoneConfig();
    return Stream.of(
        Arguments.of(valid.replace("schema-version = 1", "schema-version = 2")),
        Arguments.of(valid.replace("schema-version = 1", "schema-version = \"1\"")),
        Arguments.of("schema-version = 1\n"),
        Arguments.of("schema-version = 1\n[connections]\n"),
        Arguments.of("schema-version = 1\nconnections = \"not-a-table\"\n"),
        Arguments.of(
            "schema-version = 1\nunexpected = true\n" + valid.substring(valid.indexOf('['))),
        Arguments.of(valid.replace("allowed-methods", "unexpected")),
        Arguments.of(valid.replace("https://api.example.test/", "http://api.example.test/")),
        Arguments.of(valid.replace("[\"GET\"]", "[]")),
        Arguments.of(valid.replace("[\"GET\"]", "[\"get\"]")),
        Arguments.of(valid.replace("kind = \"none\"", "kind = \"basic\"")),
        Arguments.of(valid.replace("kind = \"none\"", "kind = \"none\"\nheader = \"x\"")),
        Arguments.of(valid.replace("kind = \"none\"", "kind = \"api-key\"")),
        Arguments.of(
            valid.replace("kind = \"none\"", "kind = \"api-key\"\nvalue-env = \"NOT-AN-ENV\"")),
        Arguments.of(valid.replace("base-uri =", "connect-timeout-ms = \"5\"\nbase-uri =")),
        Arguments.of(valid.replace("[connections.API.authentication]\n", "")),
        Arguments.of(valid.replace("connections.API", "connections.\"é\"")));
  }

  private static String baseApiKeyConfig() {
    return "schema-version = 1\n"
        + "[connections.API]\n"
        + "base-uri = \"https://api.example.test/\"\n"
        + "allowed-methods = [\"GET\"]\n"
        + "[connections.API.authentication]\n"
        + "kind = \"api-key\"\n";
  }

  private static String baseNoneConfig() {
    return "schema-version = 1\n"
        + "[connections.API]\n"
        + "base-uri = \"https://api.example.test/\"\n"
        + "allowed-methods = [\"GET\"]\n"
        + "[connections.API.authentication]\n"
        + "kind = \"none\"\n";
  }

  private Path write(String text) throws IOException {
    return writeNamed("connections-" + fileNumber++ + ".toml", text);
  }

  private Path writeNamed(String name, String text) throws IOException {
    Path path = temporaryDirectory.resolve(name);
    Files.writeString(path, text, StandardCharsets.UTF_8);
    return path;
  }
}
