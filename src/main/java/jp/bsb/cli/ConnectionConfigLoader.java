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
import jp.bsb.adapter.TomlAdapter;
import jp.bsb.adapter.TomlAdapter.Array;
import jp.bsb.adapter.TomlAdapter.Table;
import jp.bsb.runtime.ConnectionPolicy;
import jp.bsb.runtime.ConnectionResolution;
import jp.bsb.runtime.CredentialReference;
import jp.bsb.runtime.HttpRetryPolicy;
import jp.bsb.runtime.JdkHttpsTransport;

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
  private static final Set<String> CONNECTION_KEYS_V2 =
      Set.of(
          "base-uri",
          "allowed-methods",
          "connect-timeout-ms",
          "response-timeout-ms",
          "maximum-request-bytes",
          "maximum-response-bytes",
          "authentication",
          "reliability");
  private static final Set<String> RELIABILITY_KEYS =
      Set.of(
          "maximum-attempts",
          "retryable-failure-kinds",
          "retryable-status-codes",
          "initial-delay-ms",
          "maximum-delay-ms",
          "backoff-multiplier",
          "respect-retry-after",
          "maximum-retry-after-ms",
          "method-safety",
          "idempotency-key-header",
          "minimum-start-interval-ms",
          "final-failure-policy");
  private static final Set<String> AUTHENTICATION_KEYS =
      Set.of(
          "kind",
          "header",
          "value",
          "value-env",
          "username",
          "username-env",
          "password",
          "password-env",
          "token",
          "token-env");
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
    TomlAdapter.ParseResult parsed = TomlAdapter.parse(text);
    if (parsed.error().isPresent()) {
      var position = parsed.error().orElseThrow();
      throw problem("TOML構文が正しくありません（行" + position.line() + "、列" + position.column() + "）。");
    }
    Table document = parsed.document();
    rejectUnknownKeys(document, ROOT_KEYS, "ルート");
    long schemaVersion = requireLong(document, "schema-version", "schema-version");
    if (schemaVersion != 1L && schemaVersion != 2L && schemaVersion != 3L) {
      throw problem("schema-version は1、2、3のいずれかでなければなりません。");
    }
    Table connections = requireTable(document, "connections", "connections");
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
      Object raw = connections.get(name);
      if (!(raw instanceof Table table)) {
        throw problem("connectionsの各項目はテーブルでなければなりません。");
      }
      rejectUnknownKeys(table, schemaVersion == 1 ? CONNECTION_KEYS : CONNECTION_KEYS_V2, "接続");
      policies.put(name, parseConnection(table, transport, schemaVersion));
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

  private ConnectionPolicy parseConnection(
      Table table, JdkHttpsTransport.Builder transport, long schemaVersion)
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
    Table authentication = requireTable(table, "authentication", "authentication");
    rejectUnknownKeys(authentication, AUTHENTICATION_KEYS, "authentication");
    String kind = requireString(authentication, "kind", "authentication.kind");

    Optional<CredentialReference> reference;
    if (kind.equals("none")) {
      if (!authentication.keySet().equals(Set.of("kind"))) {
        throw problem("authentication.kindがnoneの場合、認証用の追加項目は指定できません。");
      }
      reference = Optional.empty();
    } else if (kind.equals("api-key")) {
      rejectUnknownKeys(
          authentication, Set.of("kind", "header", "value", "value-env"), "authentication");
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
    } else if (schemaVersion == 3 && kind.equals("basic")) {
      rejectUnknownKeys(
          authentication,
          Set.of("kind", "username", "username-env", "password", "password-env"),
          "authentication");
      String username =
          credentialValue(authentication, "username", "username-env", "Basic username");
      String password =
          credentialValue(authentication, "password", "password-env", "Basic password");
      Optional<String> credentialProblem =
          JdkHttpsTransport.basicValidationProblem(username, password);
      if (credentialProblem.isPresent()) {
        throw problem("Basic認証設定が正しくありません（" + credentialProblem.orElseThrow() + "）。");
      }
      var credential = CredentialReference.opaque();
      transport.basic(credential, username, password);
      reference = Optional.of(credential);
    } else if (schemaVersion == 3 && kind.equals("bearer")) {
      rejectUnknownKeys(authentication, Set.of("kind", "token", "token-env"), "authentication");
      String token = credentialValue(authentication, "token", "token-env", "Bearer token");
      Optional<String> credentialProblem = JdkHttpsTransport.bearerValidationProblem(token);
      if (credentialProblem.isPresent()) {
        throw problem("Bearer認証設定が正しくありません（" + credentialProblem.orElseThrow() + "）。");
      }
      var credential = CredentialReference.opaque();
      transport.bearer(credential, token);
      reference = Optional.of(credential);
    } else {
      throw problem(
          schemaVersion == 3
              ? "authentication.kindはnone、api-key、basic、bearerのいずれかでなければなりません。"
              : "authentication.kindはnoneまたはapi-keyでなければなりません。");
    }

    HttpRetryPolicy retry =
        schemaVersion >= 2 && table.contains("reliability")
            ? parseReliability(requireTable(table, "reliability", "reliability"))
            : HttpRetryPolicy.none();
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
            retry.mode(),
            retry);
    Optional<String> problem = policy.validationProblem();
    if (problem.isPresent()) {
      throw problem("接続方針が正しくありません（" + problem.orElseThrow() + "）。");
    }
    return policy;
  }

  private String credentialValue(
      Table authentication, String directKey, String environmentKey, String label)
      throws ConnectionConfigException {
    boolean direct = authentication.contains(directKey);
    boolean fromEnvironment = authentication.contains(environmentKey);
    if (direct == fromEnvironment) {
      throw problem(label + "では" + directKey + "または" + environmentKey + "のどちらか一方を指定してください。");
    }
    if (direct) return requireString(authentication, directKey, "authentication." + directKey);
    String variable =
        requireString(authentication, environmentKey, "authentication." + environmentKey);
    if (!ENVIRONMENT_NAME.matcher(variable).matches()) {
      throw problem("authentication." + environmentKey + "の環境変数名が正しくありません。");
    }
    String value = environment.apply(variable);
    if (value == null) {
      throw problem("authentication." + environmentKey + "で指定した環境変数が設定されていません。");
    }
    return value;
  }

  private static HttpRetryPolicy parseReliability(Table table) throws ConnectionConfigException {
    rejectUnknownKeys(table, RELIABILITY_KEYS, "reliability");
    int attempts = optionalInt(table, "maximum-attempts", 3);
    List<String> failures =
        optionalStringArray(
            table,
            "retryable-failure-kinds",
            List.of("connectTimeout", "connectionFailure", "responseTimeout", "transportFailure"));
    List<Integer> statuses =
        optionalIntegerArray(table, "retryable-status-codes", List.of(429, 502, 503, 504));
    long initial = optionalLong(table, "initial-delay-ms", 200);
    long maximum = optionalLong(table, "maximum-delay-ms", 5_000);
    int multiplier = optionalInt(table, "backoff-multiplier", 2);
    boolean respect = optionalBoolean(table, "respect-retry-after", true);
    long retryAfter = optionalLong(table, "maximum-retry-after-ms", 60_000);
    String safety = optionalString(table, "method-safety", "safeOnly");
    Optional<String> idempotencyHeader =
        table.contains("idempotency-key-header")
            ? Optional.of(requireString(table, "idempotency-key-header", "idempotency-key-header"))
            : Optional.empty();
    long interval = optionalLong(table, "minimum-start-interval-ms", 0);
    String finalFailure = optionalString(table, "final-failure-policy", "disabled");
    if (!finalFailure.equals("disabled")) {
      throw problem("CLI版2のfinal-failure-policyはdisabledでなければなりません。");
    }
    var policy =
        new HttpRetryPolicy(
            "bounded",
            attempts,
            failures,
            statuses,
            initial,
            maximum,
            multiplier,
            respect,
            retryAfter,
            safety,
            idempotencyHeader,
            interval,
            finalFailure);
    if (jp.bsb.runtime.HttpRetryPolicies.validationProblem(policy).isPresent()) {
      throw problem(
          "reliabilityが正しくありません（"
              + jp.bsb.runtime.HttpRetryPolicies.validationProblem(policy).orElseThrow()
              + "）。");
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

  private static void rejectUnknownKeys(Table table, Set<String> allowed, String scope)
      throws ConnectionConfigException {
    var unknown = new HashSet<>(table.keySet());
    unknown.removeAll(allowed);
    if (!unknown.isEmpty()) {
      throw problem(scope + "に未対応の項目があります。");
    }
  }

  private static String requireString(Table table, String key, String display)
      throws ConnectionConfigException {
    Object value = table.get(key);
    if (!(value instanceof String text) || text.isEmpty()) {
      throw problem(display + "には空でない文字列を指定してください。");
    }
    return text;
  }

  private static String optionalString(Table table, String key, String defaultValue)
      throws ConnectionConfigException {
    if (!table.contains(key)) return defaultValue;
    return requireString(table, key, key);
  }

  private static long requireLong(Table table, String key, String display)
      throws ConnectionConfigException {
    Object value = table.get(key);
    if (!(value instanceof Long number)) {
      throw problem(display + "には整数を指定してください。");
    }
    return number;
  }

  private static long optionalLong(Table table, String key, long defaultValue)
      throws ConnectionConfigException {
    if (!table.contains(key)) return defaultValue;
    return requireLong(table, key, key);
  }

  private static int optionalInt(Table table, String key, int defaultValue)
      throws ConnectionConfigException {
    long value = optionalLong(table, key, defaultValue);
    try {
      return Math.toIntExact(value);
    } catch (ArithmeticException overflow) {
      throw problem(key + "の整数が範囲外です。");
    }
  }

  private static boolean optionalBoolean(Table table, String key, boolean defaultValue)
      throws ConnectionConfigException {
    if (!table.contains(key)) return defaultValue;
    Object value = table.get(key);
    if (!(value instanceof Boolean flag)) throw problem(key + "には真偽値を指定してください。");
    return flag;
  }

  private static List<String> optionalStringArray(
      Table table, String key, List<String> defaultValue) throws ConnectionConfigException {
    if (!table.contains(key)) return defaultValue;
    Object value = table.get(key);
    if (!(value instanceof Array array)) throw problem(key + "には文字列配列を指定してください。");
    var result = new ArrayList<String>();
    for (int index = 0; index < array.size(); index++) {
      if (!(array.get(index) instanceof String text) || text.isEmpty())
        throw problem(key + "には空でない文字列だけを指定してください。");
      result.add(text);
    }
    return List.copyOf(result);
  }

  private static List<Integer> optionalIntegerArray(
      Table table, String key, List<Integer> defaultValue) throws ConnectionConfigException {
    if (!table.contains(key)) return defaultValue;
    Object value = table.get(key);
    if (!(value instanceof Array array)) throw problem(key + "には整数配列を指定してください。");
    var result = new ArrayList<Integer>();
    for (int index = 0; index < array.size(); index++) {
      if (!(array.get(index) instanceof Long number)) throw problem(key + "には整数だけを指定してください。");
      try {
        result.add(Math.toIntExact(number));
      } catch (ArithmeticException overflow) {
        throw problem(key + "の整数が範囲外です。");
      }
    }
    return List.copyOf(result);
  }

  private static Table requireTable(Table table, String key, String display)
      throws ConnectionConfigException {
    Object value = table.get(key);
    if (!(value instanceof Table nested)) {
      throw problem(display + "にはテーブルを指定してください。");
    }
    return nested;
  }

  private static List<String> requireStringArray(Table table, String key, String display)
      throws ConnectionConfigException {
    Object value = table.get(key);
    if (!(value instanceof Array array) || array.isEmpty()) {
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
      boolean https = "https".equals(parsed.getScheme());
      boolean localHttp =
          "http".equals(parsed.getScheme())
              && "localhost".equals(parsed.getHost())
              && parsed.getPort() >= 1;
      if ((!https && !localHttp)
          || parsed.getHost() == null
          || parsed.getRawUserInfo() != null
          || parsed.getPort() == 0
          || parsed.getPort() > 65_535) {
        throw problem("base-uriは有効なHTTPS基底URIまたは明示port付きlocalhost HTTP URIでなければなりません。");
      }
      int port = parsed.getPort();
      boolean defaultHttpsPort = https && (port == -1 || port == 443);
      return parsed.getScheme() + "://" + parsed.getHost() + (defaultHttpsPort ? "" : ":" + port);
    } catch (URISyntaxException failure) {
      throw problem("base-uriは有効なHTTPS基底URIまたは明示port付きlocalhost HTTP URIでなければなりません。");
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
