package jp.bsb.runtime;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 論理接続の論理接続方針を、規範順で検査します。 */
final class ConnectionPolicyValidator {
  static final String BASE_URI_INVALID = "BASE_URI_INVALID";
  static final String ORIGIN_POLICY_INVALID = "ORIGIN_POLICY_INVALID";
  static final String METHOD_POLICY_INVALID = "METHOD_POLICY_INVALID";
  static final String AUTHENTICATION_POLICY_INVALID = "AUTHENTICATION_POLICY_INVALID";
  static final String CONNECT_TIMEOUT_INVALID = "CONNECT_TIMEOUT_INVALID";
  static final String RESPONSE_TIMEOUT_INVALID = "RESPONSE_TIMEOUT_INVALID";
  static final String REQUEST_LIMIT_INVALID = "REQUEST_LIMIT_INVALID";
  static final String RESPONSE_LIMIT_INVALID = "RESPONSE_LIMIT_INVALID";
  static final String REDIRECT_POLICY_INVALID = "REDIRECT_POLICY_INVALID";
  static final String RETRY_POLICY_INVALID = "RETRY_POLICY_INVALID";

  private static final int URI_BYTES = 8_192;
  private static final int ORIGIN_COUNT = 32;
  private static final int ORIGIN_TOTAL_BYTES = 262_144;
  private static final long TIMEOUT_MILLISECONDS = 30_000;
  private static final long BODY_BYTES = 67_108_864;
  private static final Set<String> METHODS =
      Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE");
  private static final Set<String> AUTHENTICATION = Set.of("none", "basic", "apiKey", "oauth2");
  private static final Pattern DNS_LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

  private ConnectionPolicyValidator() {}

  static String validate(ConnectionPolicy policy) {
    URI base = parseBase(policy.baseUri());
    if (base == null) {
      return BASE_URI_INVALID;
    }
    if (!validOrigins(policy, base)) {
      return ORIGIN_POLICY_INVALID;
    }
    if (policy.allowedMethods().isEmpty()
        || !METHODS.containsAll(policy.allowedMethods())
        || new HashSet<>(policy.allowedMethods()).size() != policy.allowedMethods().size()) {
      return METHOD_POLICY_INVALID;
    }
    boolean knownAuthentication = AUTHENTICATION.contains(policy.authenticationKind());
    boolean referenceExpected = !policy.authenticationKind().equals("none");
    if (!knownAuthentication || referenceExpected != policy.credentialReference().isPresent()) {
      return AUTHENTICATION_POLICY_INVALID;
    }
    if (!within(policy.connectTimeoutMilliseconds(), 1, TIMEOUT_MILLISECONDS)) {
      return CONNECT_TIMEOUT_INVALID;
    }
    if (!within(policy.responseTimeoutMilliseconds(), 1, TIMEOUT_MILLISECONDS)) {
      return RESPONSE_TIMEOUT_INVALID;
    }
    if (!within(policy.maximumRequestBytes(), 1, BODY_BYTES)) {
      return REQUEST_LIMIT_INVALID;
    }
    if (!within(policy.maximumResponseBytes(), 1, BODY_BYTES)) {
      return RESPONSE_LIMIT_INVALID;
    }
    if (!policy.redirectPolicy().equals("deny")) {
      return REDIRECT_POLICY_INVALID;
    }
    if (!policy.retryPolicy().equals("none")) {
      return RETRY_POLICY_INVALID;
    }
    return null;
  }

  static boolean validReason(String reason) {
    return switch (reason) {
      case BASE_URI_INVALID,
          ORIGIN_POLICY_INVALID,
          METHOD_POLICY_INVALID,
          AUTHENTICATION_POLICY_INVALID,
          CONNECT_TIMEOUT_INVALID,
          RESPONSE_TIMEOUT_INVALID,
          REQUEST_LIMIT_INVALID,
          RESPONSE_LIMIT_INVALID,
          REDIRECT_POLICY_INVALID,
          RETRY_POLICY_INVALID ->
          true;
      default -> false;
    };
  }

  static boolean allowsTarget(ConnectionPolicy policy, URI target) {
    URI base = parseBase(policy.baseUri());
    return base != null
        && target.getRawUserInfo() == null
        && target.getRawFragment() == null
        && origin(base).equals(origin(target))
        && policy.allowedOrigins().stream()
            .map(ConnectionPolicyValidator::parseAsciiHttps)
            .filter(java.util.Objects::nonNull)
            .map(ConnectionPolicyValidator::origin)
            .anyMatch(origin(target)::equals);
  }

  private static URI parseBase(String text) {
    URI uri = parseAsciiHttps(text);
    if (uri == null
        || text.getBytes(StandardCharsets.UTF_8).length > URI_BYTES
        || uri.getRawUserInfo() != null
        || uri.getRawQuery() != null
        || uri.getRawFragment() != null
        || uri.getRawPath() == null
        || !(uri.getRawPath().equals("/") || uri.getRawPath().endsWith("/"))
        || !uri.normalize().equals(uri)) {
      return null;
    }
    return uri;
  }

  private static boolean validOrigins(ConnectionPolicy policy, URI base) {
    if (policy.allowedOrigins().isEmpty() || policy.allowedOrigins().size() > ORIGIN_COUNT) {
      return false;
    }
    var normalized = new HashSet<String>();
    long total = 0;
    for (String text : policy.allowedOrigins()) {
      URI origin = parseAsciiHttps(text);
      if (origin == null
          || origin.getRawUserInfo() != null
          || origin.getRawQuery() != null
          || origin.getRawFragment() != null
          || (origin.getRawPath() != null && !origin.getRawPath().isEmpty())) {
        return false;
      }
      int bytes = text.getBytes(StandardCharsets.UTF_8).length;
      total += bytes;
      if (bytes > URI_BYTES || total > ORIGIN_TOTAL_BYTES || !normalized.add(origin(origin))) {
        return false;
      }
    }
    return normalized.contains(origin(base));
  }

  private static URI parseAsciiHttps(String text) {
    if (text.isEmpty() || !StandardCharsets.US_ASCII.newEncoder().canEncode(text)) {
      return null;
    }
    try {
      URI uri = new URI(text);
      String host = uri.getHost();
      if (!uri.isAbsolute()
          || !"https".equals(uri.getScheme())
          || host == null
          || !host.equals(host.toLowerCase(Locale.ROOT))
          || !isDnsName(host)
          || uri.getPort() == 0
          || uri.getPort() > 65_535) {
        return null;
      }
      return uri;
    } catch (URISyntaxException failure) {
      return null;
    }
  }

  private static boolean isDnsName(String host) {
    if (host.indexOf(':') >= 0 || host.matches("[0-9.]+") || host.length() > 253) {
      return false;
    }
    String candidate = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    if (candidate.isEmpty()) {
      return false;
    }
    for (String label : candidate.split("\\.", -1)) {
      if (!DNS_LABEL.matcher(label).matches()) {
        return false;
      }
    }
    return true;
  }

  private static String origin(URI uri) {
    int port = uri.getPort();
    return "https://" + uri.getHost() + (port == -1 || port == 443 ? "" : ":" + port);
  }

  private static boolean within(long value, long minimum, long maximum) {
    return value >= minimum && value <= maximum;
  }
}
