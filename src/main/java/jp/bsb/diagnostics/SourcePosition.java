package jp.bsb.diagnostics;

import java.util.Optional;

/**
 * ソースコード上の具体的な「1点」を表す位置情報レコードです。
 *
 * <p>【コンピュータ科学の観点：文字と書記素クラスタ】 現代の多言語環境（Unicode）において、「1文字」の定義は多重のレイヤを持ちます：
 *
 * <ul>
 *   <li><b>UTF-8バイト位置（{@code utf8Offset}）</b>: ファイル上の物理的なバイト位置（0始まり）。
 *   <li><b>行番号（{@code line}）</b>: 改行で区切られた物理行（1始まり）。
 *   <li><b>列番号（{@code column}）</b>: 拡張書記素クラスタ（Extended Grapheme Cluster）単位の論理位置（1始まり）。 例えば「か」+
 *       U+3099 COMBINING KATAKANA-HIRAGANA VOICED SOUND MARK や国旗絵文字のように、
 *       複数のUnicodeコードポイントから構成される並びも1クラスタとして数えます。端末やフォントの表示セル幅は列番号に使用しません。
 * </ul>
 *
 * @param utf8Offset BOMを含む元ファイル先頭からのUTF-8バイト位置（0始まり）
 * @param line 1始まりの行番号
 * @param column 1始まりの書記素クラスタ単位の列番号
 */
public record SourcePosition(long utf8Offset, int line, int column) implements DiagnosticLocation {
  /**
   * コンパクトコンストラクタによるバリデーション。
   *
   * @throws IllegalArgumentException {@code utf8Offset} が負数、または {@code line}/{@code column} が 1
   *     未満の場合
   */
  public SourcePosition {
    if (utf8Offset < 0) {
      throw new IllegalArgumentException("utf8Offset must not be negative");
    }
    if (line < 1) {
      throw new IllegalArgumentException("line must be at least 1");
    }
    if (column < 1) {
      throw new IllegalArgumentException("column must be at least 1");
    }
  }

  /**
   * 自身が人間向けの行・列位置情報を持っているため、自身のインスタンスを返します。
   *
   * @return {@code Optional.of(this)}
   */
  @Override
  public Optional<SourcePosition> displayPosition() {
    return Optional.of(this);
  }
}
