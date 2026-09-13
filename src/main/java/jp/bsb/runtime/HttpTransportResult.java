package jp.bsb.runtime;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** HTTP能力が返す、自由文と部分応答を持たない閉じた結果です。 */
public final class HttpTransportResult {
  /** 能力応答の状態です。 */
  public enum State {
    RESPONSE,
    FAILURE,
    CANCELLED,
    CREDENTIAL_NOT_CONFIGURED,
    CREDENTIAL_DENIED,
    CREDENTIAL_INVALID
  }

  private final String connectionName;
  private final String method;
  private final State state;
  private final OptionalInt status;
  private final List<HttpTransportHeader> headers;
  private final Optional<ByteSequenceValue> body;
  private final Optional<String> failureKind;
  private final Optional<String> credentialInvalidReason;
  private final long receivedBodyBytes;
  private final OptionalLong knownResponseTotalBytes;

  private HttpTransportResult(
      String connectionName,
      String method,
      State state,
      OptionalInt status,
      List<HttpTransportHeader> headers,
      Optional<ByteSequenceValue> body,
      Optional<String> failureKind,
      Optional<String> credentialInvalidReason,
      long receivedBodyBytes,
      OptionalLong knownResponseTotalBytes) {
    this.connectionName = requireText(connectionName, "connectionName");
    this.method = requireText(method, "method");
    this.state = Objects.requireNonNull(state, "state");
    this.status = Objects.requireNonNull(status, "status");
    this.headers = List.copyOf(headers);
    this.body = Objects.requireNonNull(body, "body");
    this.failureKind = Objects.requireNonNull(failureKind, "failureKind");
    this.credentialInvalidReason =
        Objects.requireNonNull(credentialInvalidReason, "credentialInvalidReason");
    if (receivedBodyBytes < 0) {
      throw new IllegalArgumentException("received body bytes must not be negative");
    }
    this.receivedBodyBytes = receivedBodyBytes;
    this.knownResponseTotalBytes =
        Objects.requireNonNull(knownResponseTotalBytes, "knownResponseTotalBytes");
  }

  /** 完全応答を作ります。入力本文は防御コピーされます。 */
  public static HttpTransportResult response(
      String connectionName,
      String method,
      int status,
      List<HttpTransportHeader> headers,
      byte[] body) {
    Objects.requireNonNull(body, "body");
    return new HttpTransportResult(
        connectionName,
        method,
        State.RESPONSE,
        OptionalInt.of(status),
        headers,
        Optional.of(ByteSequenceValue.copyOf(body)),
        Optional.empty(),
        Optional.empty(),
        body.length,
        OptionalLong.empty());
  }

  /** 閉じた通信失敗を作ります。 */
  public static HttpTransportResult failure(
      String connectionName, String method, String failureKind) {
    return failure(connectionName, method, failureKind, 0);
  }

  /** 本文を一部読み取った閉じた通信失敗を作ります。 */
  public static HttpTransportResult failure(
      String connectionName, String method, String failureKind, long receivedBodyBytes) {
    if (!HttpSendFailureValue.KINDS.contains(failureKind)) {
      throw new IllegalArgumentException("unknown HTTP send failure kind");
    }
    return empty(
        connectionName,
        method,
        State.FAILURE,
        Optional.of(failureKind),
        Optional.empty(),
        receivedBodyBytes);
  }

  static HttpTransportResult knownResponseTotalExceeded(
      String connectionName, String method, long declaredBodyBytes) {
    if (declaredBodyBytes < 0) {
      throw new IllegalArgumentException("declared body bytes must not be negative");
    }
    return new HttpTransportResult(
        connectionName,
        method,
        State.FAILURE,
        OptionalInt.empty(),
        List.of(),
        Optional.empty(),
        Optional.of("responseTooLarge"),
        Optional.empty(),
        0,
        OptionalLong.of(declaredBodyBytes));
  }

  /** 取消応答を作ります。 */
  public static HttpTransportResult cancelled(String connectionName, String method) {
    return empty(connectionName, method, State.CANCELLED, Optional.empty(), Optional.empty(), 0);
  }

  /** APIキーが未設定の応答を作ります。 */
  public static HttpTransportResult credentialNotConfigured(String connectionName, String method) {
    return empty(
        connectionName,
        method,
        State.CREDENTIAL_NOT_CONFIGURED,
        Optional.empty(),
        Optional.empty(),
        0);
  }

  /** APIキー利用が拒否された応答を作ります。 */
  public static HttpTransportResult credentialDenied(String connectionName, String method) {
    return empty(
        connectionName, method, State.CREDENTIAL_DENIED, Optional.empty(), Optional.empty(), 0);
  }

  /** APIキー名または値が不正な応答を作ります。 */
  public static HttpTransportResult credentialInvalid(
      String connectionName, String method, String reason) {
    if (!reason.equals("HEADER_NAME_INVALID") && !reason.equals("HEADER_VALUE_INVALID")) {
      throw new IllegalArgumentException("unknown credential invalid reason");
    }
    return empty(
        connectionName, method, State.CREDENTIAL_INVALID, Optional.empty(), Optional.of(reason), 0);
  }

  private static HttpTransportResult empty(
      String connectionName,
      String method,
      State state,
      Optional<String> failureKind,
      Optional<String> credentialReason,
      long receivedBodyBytes) {
    return new HttpTransportResult(
        connectionName,
        method,
        state,
        OptionalInt.empty(),
        List.of(),
        Optional.empty(),
        failureKind,
        credentialReason,
        receivedBodyBytes,
        OptionalLong.empty());
  }

  /**
   * @return 要求の論理接続名
   */
  public String connectionName() {
    return connectionName;
  }

  /**
   * @return 要求のHTTP method
   */
  public String method() {
    return method;
  }

  /**
   * @return 閉じた応答状態
   */
  public State state() {
    return state;
  }

  /**
   * @return 完全応答の状態コード
   */
  public OptionalInt status() {
    return status;
  }

  /**
   * @return 完全応答のheaderリスト
   */
  public List<HttpTransportHeader> headers() {
    return headers;
  }

  Optional<ByteSequenceValue> body() {
    return body;
  }

  /**
   * @return 通信失敗の安定種類
   */
  public Optional<String> failureKind() {
    return failureKind;
  }

  /**
   * @return 資格情報不正の安定理由
   */
  public Optional<String> credentialInvalidReason() {
    return credentialInvalidReason;
  }

  /**
   * @return 実際に読み取った応答本文バイト数
   */
  public long receivedBodyBytes() {
    return receivedBodyBytes;
  }

  OptionalLong knownResponseTotalBytes() {
    return knownResponseTotalBytes;
  }

  /** URI・header・本文・資格情報を漏らさない安全表現だけを返します。 */
  @Override
  public String toString() {
    return "HttpTransportResult[connectionName="
        + connectionName
        + ", method="
        + method
        + ", state="
        + state
        + "]";
  }

  private static String requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
