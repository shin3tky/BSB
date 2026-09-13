package jp.bsb.json;

/** コーデック固有の解析失敗分類です。 */
public enum JsonParseErrorKind {
  SYNTAX,
  DUPLICATE_KEY,
  NUMBER_LIMIT,
  DEPTH_LIMIT,
  NODE_LIMIT,
  ARRAY_LENGTH_LIMIT,
  OBJECT_MEMBER_LIMIT,
  INPUT_LIMIT
}
