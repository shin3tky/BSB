package jp.bsb.ir;

import java.util.Objects;
import java.util.Optional;
import jp.bsb.diagnostics.SourceSpan;

/** IR呼出しが保持する、宣言へ解決済みの論理接続名と操作です。 */
public record LogicalConnectionReference(
    String name,
    String operation,
    SourceSpan declarationNameSpan,
    SourceSpan argumentSpan,
    Optional<String> httpMethod) {
  /** 論理接続のmethodを持たない参照を作ります。 */
  public LogicalConnectionReference(
      String name, String operation, SourceSpan declarationNameSpan, SourceSpan argumentSpan) {
    this(name, operation, declarationNameSpan, argumentSpan, Optional.empty());
  }

  /** 論理接続で許可する正規名と操作を検証します。 */
  public LogicalConnectionReference {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (!operation.equals("resolve")) {
      throw new IllegalArgumentException(
          "the logical connections logical connection operation must be resolve");
    }
    Objects.requireNonNull(declarationNameSpan, "declarationNameSpan");
    Objects.requireNonNull(argumentSpan, "argumentSpan");
    httpMethod = Objects.requireNonNull(httpMethod, "httpMethod");
  }
}
