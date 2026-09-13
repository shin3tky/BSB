package jp.bsb.runtime;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

/** 可搬な単一論理ファイル名の規範検査です。 */
public final class LogicalFileName {
  private LogicalFileName() {}

  /** 不正理由を規範優先順で返し、正常ならnullを返します。 */
  public static String problem(String value) {
    if (!Normalizer.isNormalized(value, Normalizer.Form.NFC)) return "notNfc";
    if (value.isEmpty()) return "empty";
    if (value.getBytes(StandardCharsets.UTF_8).length > 255) return "tooLong";
    if (value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) return "separator";
    if (value.codePoints().anyMatch(codePoint -> codePoint <= 0x1f || codePoint == 0x7f)) {
      return "controlCharacter";
    }
    if (value.equals(".") || value.equals("..")) return "dotName";
    return null;
  }
}
