package jp.bsb.json;

/** JSON直列化候補が規範出力上限を超えたことを表します。 */
public final class JsonWriteException extends Exception {
  private final long limit;
  private final long observed;

  JsonWriteException(long limit, long observed) {
    super("JSON output exceeds the normative limit");
    this.limit = limit;
    this.observed = observed;
  }

  public long limit() {
    return limit;
  }

  public long observed() {
    return observed;
  }
}
