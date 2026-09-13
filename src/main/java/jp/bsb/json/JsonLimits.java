package jp.bsb.json;

/** JSONJSON値とコーデックの規範上限です。 */
public final class JsonLimits {
  public static final long INPUT_UTF8_BYTES = 16_777_216L;
  public static final long OUTPUT_UTF8_BYTES = 16_777_216L;
  public static final int DEPTH = 256;
  public static final int ARRAY_LENGTH = 65_536;
  public static final int OBJECT_MEMBERS = 65_536;
  public static final long VALUE_NODES = 250_000L;
  public static final int INPUT_NUMBER_DIGITS = 4_096;
  public static final int INTEGER_DIGITS = 65_536;
  public static final int DECIMAL_PRECISION = 65_536;
  public static final int DECIMAL_ABSOLUTE_SCALE = 65_536;
  public static final long CONSTRUCTION_UNITS = 1_000_000L;
  public static final long WORK_UNITS = 67_108_864L;

  private JsonLimits() {}
}
