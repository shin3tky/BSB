package jp.bsb.ir;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/** IR呼出しが保持する、宣言へ解決済みの作業領域名と操作です。 */
public record WorkspaceReference(
    String name, String operation, SourceSpan declarationNameSpan, SourceSpan argumentSpan) {
  public WorkspaceReference {
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("name must not be blank");
    if (!operation.equals("read") && !operation.equals("write")) {
      throw new IllegalArgumentException("workspace operation must be read or write");
    }
    Objects.requireNonNull(declarationNameSpan, "declarationNameSpan");
    Objects.requireNonNull(argumentSpan, "argumentSpan");
  }
}
