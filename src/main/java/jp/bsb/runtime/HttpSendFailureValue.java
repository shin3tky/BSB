package jp.bsb.runtime;

import java.util.Set;
import jp.bsb.stdlib.ValueType;

/** URIや例外を保持せず、10種類の安定名だけを持つ不変HTTP送信失敗です。 */
public final class HttpSendFailureValue implements RuntimeValue {
  static final Set<String> KINDS =
      Set.of(
          "nameResolutionFailure",
          "connectTimeout",
          "connectionFailure",
          "tlsFailure",
          "responseTimeout",
          "responseTooLarge",
          "responseHeadersTooLarge",
          "contentDecodingFailure",
          "protocolFailure",
          "transportFailure");

  private final String kind;

  HttpSendFailureValue(String kind) {
    if (!KINDS.contains(kind)) {
      throw new IllegalArgumentException("unknown HTTP send failure kind");
    }
    this.kind = kind;
  }

  String kind() {
    return kind;
  }

  @Override
  public ValueType type() {
    return ValueType.HTTP_SEND_FAILURE;
  }

  @Override
  public String displayText() {
    return "<redacted>";
  }

  @Override
  public String toString() {
    return traceText();
  }
}
