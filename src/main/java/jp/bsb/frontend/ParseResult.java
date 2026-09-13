package jp.bsb.frontend;

import java.util.Objects;
import jp.bsb.frontend.ast.Program;

/**
 * 構文解析で得たASTと、後続処理へ進めるかをまとめた結果です。
 *
 * <p>【コンピュータ科学の観点：部分ASTと安全ゲート】 構文エラーから回復すると、解析器は後続の定義も検査するために部分的なASTを組み立てることがあります。
 * その部分ASTを名前解決や型検査へ渡すと派生エラーの原因になるため、後続処理は必ず {@link #programForAnalysis()} を通して成功済みASTを取得します。
 *
 * @param partialProgram エラー回復中に得られた要素を含むAST。成功時は完全なAST
 * @param successful 構文エラーがなく解析を完了したか
 */
public record ParseResult(Program partialProgram, boolean successful) {
  /** 部分ASTの非null条件を検証します。 */
  public ParseResult {
    Objects.requireNonNull(partialProgram, "partialProgram");
  }

  /**
   * 名前解決やフォーマッタなど、構文解析の後段へ渡せる完全なASTを返します。
   *
   * @return 構文的に有効なプログラムAST
   * @throws IllegalStateException 構文解析が失敗している場合
   */
  public Program programForAnalysis() {
    if (!successful) {
      throw new IllegalStateException(
          "a program with syntax errors cannot enter the next processing phase");
    }
    return partialProgram;
  }
}
