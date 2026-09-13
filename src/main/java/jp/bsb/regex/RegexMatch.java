package jp.bsb.regex;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * エンジン位置を公開せず、一致全体と捕捉文字列だけを保持する不変なBSB照合結果です。
 *
 * @param entire 一致全体
 * @param numberedCaptures 1番から順の番号付き捕捉。不参加は空
 * @param namedCaptures 名前から捕捉値への対応。不参加は空
 */
public record RegexMatch(
    String entire,
    List<Optional<String>> numberedCaptures,
    Map<String, Optional<String>> namedCaptures) {
  /** 一致文字列と参加・不参加を区別した捕捉値を不変化します。 */
  public RegexMatch {
    Objects.requireNonNull(entire, "entire");
    numberedCaptures = List.copyOf(numberedCaptures);
    namedCaptures = Map.copyOf(namedCaptures);
    if (numberedCaptures.stream().anyMatch(Objects::isNull)
        || namedCaptures.entrySet().stream()
            .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
      throw new IllegalArgumentException("regex captures must not contain null containers");
    }
  }
}
