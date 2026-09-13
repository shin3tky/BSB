package jp.bsb.regex;

/** RE2/J位置を公開せず、重ならないleftmost-first一致を順に読む1回限りのカーソルです。 */
public interface RegexMatchCursor {
  /**
   * 次の一致へ進みます。
   *
   * @return 次の一致があればtrue
   */
  boolean advance();

  /**
   * 直前の一致末尾または入力先頭から、現在の一致直前までを返します。
   *
   * @return 一致前の未照合部分
   */
  String textBeforeMatch();

  /**
   * 現在の一致と捕捉を返します。
   *
   * @return 不変な現在一致
   */
  RegexMatch match();

  /**
   * 全一致を読み終えた後の入力末尾部分を返します。
   *
   * @return 最後の一致末尾から入力末尾まで
   */
  String textAfterMatches();
}
