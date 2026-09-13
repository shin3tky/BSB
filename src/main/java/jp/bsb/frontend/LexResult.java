package jp.bsb.frontend;

import java.util.List;

/**
 * 字句解析（{@link Lexer}）の実行結果をカプセル化するレコードです。
 *
 * <p>【コンピュータ科学の観点：安全なパイプライン制御（Safety Gates）】 コンパイラのパイプライン設計では、「前のフェーズでエラーが発生した場合は後続のフェーズへ進まない」
 * という原則（Fail-Fast / Error Cascade Prevention）が極めて重要です。
 * 字句解析でエラーが発生して不完全なトークン列しか得られなかった場合、それを構文解析（Parser）に渡してしまうと 予期せぬパニックや意味不明な構文エラーが多発してしまいます。
 *
 * <p>{@link #tokensForParsing()} メソッドは、字句解析が完全に成功した場合（{@code successful == true}）にのみ
 * トークン列の取得を許可する安全装置（ゲート）として機能します。
 *
 * @param tokens 切り出されたすべてのトークン（コメントを含む）のリスト
 * @param countedTokenCount 資源制限（250,000上限）のカウント対象となったトークンの総数
 * @param successful 字句エラーが1件も発生せずに完了したかどうか
 */
public record LexResult(List<Token> tokens, int countedTokenCount, boolean successful) {
  /** コンパクトコンストラクタによる不変リスト化とバリデーション。 */
  public LexResult {
    tokens = List.copyOf(tokens);
    if (countedTokenCount < 0) {
      throw new IllegalArgumentException("countedTokenCount must not be negative");
    }
  }

  /**
   * 構文解析（Parser）に渡すためのトークン列を取得します。
   *
   * @return トークンの不変リスト
   * @throws IllegalStateException 字句解析が失敗している（{@code successful == false}）場合
   */
  public List<Token> tokensForParsing() {
    if (!successful) {
      throw new IllegalStateException("tokens from a failed lexical analysis cannot be parsed");
    }
    return tokens;
  }
}
