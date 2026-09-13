package jp.bsb.diagnostics;

import java.util.Optional;

/**
 * 診断（エラーや警告）が発生したソースファイル上の位置情報を表す共通インターフェースです。
 *
 * <p>【コンピュータ科学の観点】 エラー報告において「どこで問題が起きたか」を正確に示すことは極めて重要です。
 * 本インターフェースは、以下の3つの具象表現を統一的に扱うための封印インターフェース（sealed interface）です：
 *
 * <ul>
 *   <li>{@link FileOffset}: デコード前のファイル先頭からのバイト位置（文字として復元できない場合など）
 *   <li>{@link SourcePosition}: 1つの点（行番号・列番号・バイト位置）
 *   <li>{@link SourceSpan}: 開始位置から終了位置までの範囲
 * </ul>
 */
public sealed interface DiagnosticLocation permits FileOffset, SourcePosition, SourceSpan {
  /**
   * BOM（Byte Order Mark）を含む元ファイルの先頭を 0 とする UTF-8 バイト位置（オフセット）を返します。
   *
   * @return ファイル先頭からのバイト数（0以上の整数）
   */
  long utf8Offset();

  /**
   * 人間向けの行番号・列番号として表現可能な位置情報を持つ場合、その先頭位置を返します。
   *
   * @return 行・列位置を含む {@link SourcePosition}。バイト位置のみの場合は {@link Optional#empty()}
   */
  Optional<SourcePosition> displayPosition();
}
