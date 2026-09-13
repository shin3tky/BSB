package jp.bsb.regex;

import java.util.List;
import java.util.Optional;

/**
 * Unicode 16.0適合層で検査・コンパイル済みの正規表現です。
 *
 * <p>RE2/Jの{@code Pattern}や{@code Matcher}をAST、IR、実行時値へ公開しないための境界です。照合APIは文字列・正規表現の後続ステップで、
 * BSBの意味論と資源検査を保つ形でこの境界へ追加します。
 */
public interface RegexProgram {
  /**
   * BSBが照合量へ使うコンパイル命令数を返します。
   *
   * @return 1以上{@link RegexLimits#MAX_PROGRAM_INSTRUCTIONS}以下の命令数
   */
  int instructionCount();

  /**
   * 名前なしを含むキャプチャ数を返します。
   *
   * @return 0以上{@link RegexLimits#MAX_CAPTURES}以下のキャプチャ数
   */
  int captureCount();

  /**
   * ソース出現順の名前付きキャプチャ名を返します。
   *
   * @return 重複のない不変の名前一覧
   */
  List<String> namedCaptures();

  /**
   * 入力全体が一致するかを、新しい照合状態で判定します。
   *
   * @param input Unicodeスカラー値列
   * @return 全体一致ならtrue
   */
  boolean matchesEntire(String input);

  /**
   * 入力中に一致があるかを、新しい照合状態で判定します。
   *
   * @param input Unicodeスカラー値列
   * @return 一致が1個以上あればtrue
   */
  boolean containsMatch(String input);

  /**
   * leftmost-firstの最初の一致を不変なBSB値へコピーします。
   *
   * @param input Unicodeスカラー値列
   * @return 一致があれば捕捉を含む結果、なければ空
   */
  Optional<RegexMatch> firstMatch(String input);

  /**
   * 重ならない全一致を読む新しいカーソルを作ります。
   *
   * @param input Unicodeスカラー値列
   * @return この呼出しだけが所有する一致カーソル
   */
  RegexMatchCursor matchCursor(String input);
}
