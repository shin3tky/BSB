package jp.bsb.json;

/** CLI DTOやBSB実行スタックに依存しない、不変なJSON値です。 */
public sealed interface JsonValue
    permits JsonNull, JsonBoolean, JsonInteger, JsonDecimal, JsonString, JsonArray, JsonObject {
  /** 実行時のJSON値種別を返します。 */
  JsonKind kind();

  /** 構築時に検証済みの計測値を返します。 */
  JsonMetrics metrics();
}
