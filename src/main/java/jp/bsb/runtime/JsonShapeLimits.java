package jp.bsb.runtime;

/** JSON形状の構築、検証、公開失敗位置に適用する有限上限です。 */
public final class JsonShapeLimits {
  public static final long MAX_NODES = 65_536L;
  public static final int MAX_DEPTH = 256;
  public static final long MAX_WORK_UNITS = 67_108_864L;
  public static final int MAX_FAILURES = 256;
  public static final long MAX_PATH_UTF8_BYTES = 65_536L;

  private JsonShapeLimits() {}
}
