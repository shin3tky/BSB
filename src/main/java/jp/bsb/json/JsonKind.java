package jp.bsb.json;

/** JSON値の7つの実行時種別です。 */
public enum JsonKind {
  NULL("null"),
  BOOLEAN("boolean"),
  INTEGER("integer"),
  DECIMAL("decimal"),
  STRING("string"),
  ARRAY("array"),
  OBJECT("object");

  private final String diagnosticName;

  JsonKind(String diagnosticName) {
    this.diagnosticName = diagnosticName;
  }

  /** 診断で用いる安定ASCII名を返します。 */
  public String diagnosticName() {
    return diagnosticName;
  }
}
