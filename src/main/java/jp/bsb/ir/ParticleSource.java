package jp.bsb.ir;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/**
 * 呼出しの直前に書かれた助詞のソース情報です。
 *
 * <p>助詞は実行命令ではありませんが、トレースや将来の診断が元の表記を示せるよう、次の {@link Call} のメタデータとして保存します。
 *
 * @param name 正規化済みの助詞名
 * @param span 元ソース上の範囲
 */
public record ParticleSource(String name, SourceSpan span) {
  /** 名前と範囲を検証します。 */
  public ParticleSource {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("particle name must not be blank");
    }
    Objects.requireNonNull(span, "span");
  }
}
