package jp.bsb.frontend.ast;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/**
 * {@code を 変数名 に 入れる}という後置代入構文です。保存する値は直前までの本体要素がデータスタックへ作ります。
 *
 * @param targetName Unicode正規化済みの代入先名
 * @param targetLexeme 元ソースに書かれた代入先表記
 * @param valueParticleSpan 「を」の範囲
 * @param targetSpan 代入先名の範囲
 * @param targetParticleSpan 「に」の範囲
 * @param keywordSpan 「入れる」の範囲
 * @param span 「を」から「入れる」直後までの範囲
 */
public record Assignment(
    String targetName,
    String targetLexeme,
    SourceSpan valueParticleSpan,
    SourceSpan targetSpan,
    SourceSpan targetParticleSpan,
    SourceSpan keywordSpan,
    SourceSpan span)
    implements BodyElement {
  /** 必須値を検証します。 */
  public Assignment {
    targetName = requireText(targetName, "targetName");
    targetLexeme = requireText(targetLexeme, "targetLexeme");
    Objects.requireNonNull(valueParticleSpan, "valueParticleSpan");
    Objects.requireNonNull(targetSpan, "targetSpan");
    Objects.requireNonNull(targetParticleSpan, "targetParticleSpan");
    Objects.requireNonNull(keywordSpan, "keywordSpan");
    Objects.requireNonNull(span, "span");
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value;
  }
}
