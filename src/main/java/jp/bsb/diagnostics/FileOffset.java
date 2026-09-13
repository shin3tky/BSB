package jp.bsb.diagnostics;

import java.util.Optional;

/**
 * UTF-8文字列として解釈する前の、元ファイル上のバイト位置（オフセット）を表す位置情報レコードです。
 *
 * <p>【コンピュータ科学の観点】 通常のエラー表示には行番号と列番号（{@link SourcePosition}）を用いますが、
 * ソースファイル全体のサイズ上限（32MiB）を超過した際など、ファイルをテキストとして デコード・パースする前の段階でエラーを報告する場合には、行・列の概念が存在しないため、
 * この単純なバイト位置（オフセット）を使用します。
 *
 * @param utf8Offset 元ファイル先頭からのバイトオフセット（0以上の整数）
 */
public record FileOffset(long utf8Offset) implements DiagnosticLocation {
  /**
   * コンパクトコンストラクタによるバリデーション。
   *
   * @throws IllegalArgumentException {@code utf8Offset} が負数の場合
   */
  public FileOffset {
    if (utf8Offset < 0) {
      throw new IllegalArgumentException("utf8Offset must not be negative");
    }
  }

  /**
   * バイト位置のみであるため、人間向けの行・列位置（{@link SourcePosition}）は存在しません。
   *
   * @return 常に {@link Optional#empty()}
   */
  @Override
  public Optional<SourcePosition> displayPosition() {
    return Optional.empty();
  }
}
