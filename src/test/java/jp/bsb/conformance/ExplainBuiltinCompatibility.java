package jp.bsb.conformance;

import java.nio.charset.StandardCharsets;

/** explain固定値から、後方互換なバイト列機能の末尾追加だけを除いて比較します。 */
public final class ExplainBuiltinCompatibility {
  private static final String FIRST_BYTES_WORD = ",{\"name\":\"空のバイト列\"";
  private static final String FOLLOWING_MEMBER = "],\"userWords\"";
  private static final String BYTES = "\"featureGroup\":\"BYTES\"";

  private ExplainBuiltinCompatibility() {}

  /** バイト列11語がある成功explainだけを、既存125語の文書へ正規化します。 */
  public static byte[] withoutByteSequenceWords(byte[] output) {
    String json = new String(output, StandardCharsets.UTF_8);
    int count = occurrences(json, BYTES);
    if (count == 0) {
      return output.clone();
    }
    if (count != 11) {
      throw new IllegalArgumentException(
          "an explain document must contain exactly 11 byte sequences words");
    }
    int start = json.indexOf(FIRST_BYTES_WORD);
    int end = json.indexOf(FOLLOWING_MEMBER, start);
    if (start < 0 || end < 0) {
      throw new IllegalArgumentException("byte sequences builtin suffix is not contiguous");
    }
    String normalized = json.substring(0, start) + json.substring(end);
    normalized = normalized.replace(",\"workspaceDeclarations\":[]", "");
    return normalized.getBytes(StandardCharsets.UTF_8);
  }

  private static int occurrences(String text, String needle) {
    int count = 0;
    for (int index = text.indexOf(needle); index >= 0; index = text.indexOf(needle, index + 1)) {
      count++;
    }
    return count;
  }
}
