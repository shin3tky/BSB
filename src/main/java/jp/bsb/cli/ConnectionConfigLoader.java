package jp.bsb.cli;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import jp.bsb.runtime.ConnectionPolicy;
import jp.bsb.runtime.ConnectionResolution;
import jp.bsb.runtime.CredentialReference;
import jp.bsb.runtime.JdkHttpsTransport;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** 公開CLIの版付きTOML接続設定を、既存の実行環境能力へ変換します。 */
final class ConnectionConfigLoader {
  static final long MAX_CONFIG_BYTES = 1_048_576;
  static final int MAX_CONNECTIONS = 256;

  private static final long DEFAULT_CONNECT_TIMEOUT_MILLISECONDS = 5_000;
  private static final long DEFAULT_RESPONSE_TIMEOUT_MILLISECONDS = 10_000;
  private static final long DEFAULT_MAXIMUM_REQUEST_BYTES = 1_048_576;
  private static final long DEFAULT_MAXIMUM_RESPONSE_BYTES = 1_048_576;
  private static final Set<String> ROOT_KEYS = Set.of("schema-version", "connections");
  private static final Set<String> CONNECTION_KEYS =
      Set.of(
          "base-uri",
          "allowed-methods",
          "connect-timeout-ms",
          "response-timeout-ms",
          "maximum-request-bytes",
          "maximum-response-bytes",
          "authentication");
  private static final Set<String> AUTHENTICATION_KEYS =
      Set.of("kind", "header", "value", "value-env");
  private static final Pattern ENVIRONMENT_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

  private final Function<String, String> environment;

  ConnectionConfigLoader(Function<String, String> environment) {
    this.environment = java.util.Objects.requireNonNull(environment, "environment");
  }

  static ConnectionConfigLoader systemEnvironment() {
    return new ConnectionConfigLoader(System::getenv);
  }

  LoadedConnectionConfig load(Path path) throws ConnectionConfigException {
    byte[] bytes = read(path);
    String text = decode(bytes);
    TomlParseResult document = Toml.parse(text);
    if (document.hasErrors()) {
      var position = document.errors().getFirst().position();
      throw problem("TOML構文が正しくありません（行" + position.line() + "、列" + position.column() + "）。");
    }
    rejectUnknownKeys(document, ROOT_KEYS, "ルート");
    requireLong(document, "schema-version", "schema-version");
    if (document.getLong("schema-version") != 1L) {
      throw problem("schema-version は1でなければなりません。");
    }
    TomlTable connections = requireTable(document, "connections", "connections");
    if (connections.isEmpty()) {
      throw problem("connectionsには1個以上の接続を指定してください。");
    }
    if (connections.size() > MAX_CONNECTIONS) {
      throw problem("connectionsは" + MAX_CONNECTIONS + "個以下でなければなりません。");
    }

    var policies = new LinkedHashMap<String, ConnectionPolicy>();
    var transport = JdkHttpsTransport.builder();
    for (String name : connections.keySet()) {
      validateConnectionName(name);
      Object raw = connections.get(List.of(name));
      if (!(raw instanceof TomlTable table)) {
        throw problem("connectionsの各項目はテーブルでなければなりません。");
      }
      rejectUnknownKeys(table, CONNECTION_KEYS, "接続");
      policies.put(name, parseConnection(table, transport));
    }

    JdkHttpsTransport builtTransport;
    try {
      builtTransport = transport.buildForOwnedProcess();
    } catch (IllegalStateException failure) {
      throw problem("JDK HTTPSの安全設定を構築できません。");
    }
    Map<String, ConnectionPolicy> immutablePolicies = Map.copyOf(policies);
    return new LoadedConnectionConfig(
        (name, operation) -> {
          ConnectionPolicy policy = immutablePolicies.get(name);
          return policy == null
              ? ConnectionResolution.notConfigured(name)
              : ConnectionResolution.resolved(name, policy);
        },
        builtTransport);
  }

  private ConnectionPolicy parseConnection(TomlTable table, JdkHttpsTransport.Builder transport)
      throws ConnectionConfigException {
    String baseUri = requireString(table, "base-uri", "base-uri");
    String origin = origin(baseUri);
    List<String> methods = requireStringArray(table, "allowed-methods", "allowed-methods");
    long connectTimeout =
        optionalLong(table, "connect-timeout-ms", DEFAULT_CONNECT_TIMEOUT_MILLISECONDS);
    long responseTimeout =
        optionalLong(table, "response-timeout-ms", DEFAULT_RESPONSE_TIMEOUT_MILLISECONDS);
    long maximumRequest =
        optionalLong(table, "maximum-request-bytes", DEFAULT_MAXIMUM_REQUEST_BYTES);
    long maximumResponse =
        optionalLong(table, "maximum-response-bytes", DEFAULT_MAXIMUM_RESPONSE_BYTES);
    TomlTable authentication = requireTable(table, "authentication", "authentication");
    rejectUnknownKeys(authentication, AUTHENTICATION_KEYS, "authentication");
    String kind = requireString(authentication, "kind", "authentication.kind");

    Optional<CredentialReference> reference;
    if (kind.equals("none")) {
      if (!authentication.keySet().equals(Set.of("kind"))) {
        throw problem("authentication.kindがnoneの場合、認証用の追加項目は指定できません。");
      }
      reference = Optional.empty();
    } else if (kind.equals("api-key")) {
      String header = optionalString(authentication, "header", "x-api-key");
      boolean direct = authentication.contains("value");
      boolean fromEnvironment = authentication.contains("value-env");
      if (direct == fromEnvironment) {
        throw problem("api-key認証ではvalueまたはvalue-envのどちらか一方を指定してください。");
      }
      String value;
      if (direct) {
        value = requireString(authentication, "value", "authentication.value");
      } else {
        String variable = requireString(authentication, "value-env", "authentication.value-env");
        if (!ENVIRONMENT_NAME.matcher(variable).matches()) {
          throw problem("authentication.value-envの環境変数名が正しくありません。");
        }
        value = environment.apply(variable);
        if (value == null || value.isEmpty()) {
          throw problem("authentication.value-envで指定した環境変数が設定されていません。");
        }
      }
      Optional<String> credentialProblem = JdkHttpsTransport.apiKeyValidationProblem(header, value);
      if (credentialProblem.isPresent()) {
        throw problem("APIキー認証設定が正しくありません（" + credentialProblem.orElseThrow() + "）。");
      }
      var credential = CredentialReference.opaque();
      transport.apiKey(credential, header, value);
      reference = Optional.of(credential);
      kind = "apiKey";
    } else {
      throw problem("authentication.kindはnoneまたはapi-keyでなければなりません。");
    }

    var policy =
        new ConnectionPolicy(
            baseUri,
            List.of(origin),
            methods,
            kind,
            reference,
            connectTimeout,
            responseTimeout,
            maximumRequest,
            maximumResponse,
            "deny",
            "none");
    Optional<String> problem = policy.validationProblem();
    if (problem.isPresent()) {
      throw problem("接続方針が正しくありません（" + problem.orElseThrow() + "）。");
    }
    return policy;
  }

  private static byte[] read(Path path) throws ConnectionConfigException {
    try {
      long size = Files.size(path);
      if (size > MAX_CONFIG_BYTES) {
        throw problem("接続設定ファイルが1 MiBを超えています。");
      }
      try (var input = Files.newInputStream(path)) {
        byte[] bytes = input.readNBytes(Math.toIntExact(MAX_CONFIG_BYTES + 1));
        if (bytes.length > MAX_CONFIG_BYTES) {
          throw problem("接続設定ファイルが1 MiBを超えています。");
        }
        return bytes;
      }
    } catch (IOException failure) {
      throw problem("接続設定ファイルを読み取れません。");
    }
  }

  private static String decode(byte[] bytes) throws ConnectionConfigException {
    if (bytes.length >= 3
        && bytes[0] == (byte) 0xef
        && bytes[1] == (byte) 0xbb
        && bytes[2] == (byte) 0xbf) {
      throw problem("接続設定ファイルにUTF-8 BOMは使用できません。");
    }
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException failure) {
      throw problem("接続設定ファイルが正しいUTF-8ではありません。");
    }
  }

  private static void rejectUnknownKeys(TomlTable table, Set<String> allowed, String scope)
      throws ConnectionConfigException {
    var unknown = new HashSet<>(table.keySet());
    unknown.removeAll(allowed);
    if (!unknown.isEmpty()) {
      throw problem(scope + "に未対応の項目があります。");
    }
  }

  private static String requireString(TomlTable table, String key, String display)
      throws ConnectionConfigException {
    Object value = table.get(List.of(key));
    if (!(value instanceof String text) || text.isEmpty()) {
      throw problem(display + "には空でない文字列を指定してください。");
    }
    return text;
  }

  private static String optionalString(TomlTable table, String key, String defaultValue)
      throws ConnectionConfigException {
    if (!table.contains(List.of(key))) return defaultValue;
    return requireString(table, key, key);
  }

  private static long requireLong(TomlTable table, String key, String display)
      throws ConnectionConfigException {
    Object value = table.get(List.of(key));
    if (!(value instanceof Long number)) {
      throw problem(display + "には整数を指定してください。");
    }
    return number;
  }

  private static long optionalLong(TomlTable table, String key, long defaultValue)
      throws ConnectionConfigException {
    if (!table.contains(List.of(key))) return defaultValue;
    return requireLong(table, key, key);
  }

  private static TomlTable requireTable(TomlTable table, String key, String display)
      throws ConnectionConfigException {
    Object value = table.get(List.of(key));
    if (!(value instanceof TomlTable nested)) {
      throw problem(display + "にはテーブルを指定してください。");
    }
    return nested;
  }

  private static List<String> requireStringArray(TomlTable table, String key, String display)
      throws ConnectionConfigException {
    Object value = table.get(List.of(key));
    if (!(value instanceof TomlArray array) || array.isEmpty()) {
      throw problem(display + "には1個以上の文字列配列を指定してください。");
    }
    var result = new ArrayList<String>();
    for (int index = 0; index < array.size(); index++) {
      Object element = array.get(index);
      if (!(element instanceof String text) || text.isEmpty()) {
        throw problem(display + "には空でない文字列だけを指定してください。");
      }
      result.add(text);
    }
    return List.copyOf(result);
  }

  private static String origin(String baseUri) throws ConnectionConfigException {
    try {
      URI parsed = new URI(baseUri);
      if (!"https".equals(parsed.getScheme())
          || parsed.getHost() == null
          || parsed.getRawUserInfo() != null
          || parsed.getPort() == 0
          || parsed.getPort() > 65_535) {
        throw problem("base-uriは有効なHTTPS基底URIでなければなりません。");
      }
      int port = parsed.getPort();
      return "https://" + parsed.getHost() + (port == -1 || port == 443 ? "" : ":" + port);
    } catch (URISyntaxException failure) {
      throw problem("base-uriは有効なHTTPS基底URIでなければなりません。");
    }
  }

  private static void validateConnectionName(String name) throws ConnectionConfigException {
    if (name.isBlank() || !Normalizer.isNormalized(name, Normalizer.Form.NFC)) {
      throw problem("接続名は空でないNFC文字列でなければなりません。");
    }
  }

  private static ConnectionConfigException problem(String message) {
    return new ConnectionConfigException(message);
  }
}
