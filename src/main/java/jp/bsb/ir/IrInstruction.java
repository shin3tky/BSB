package jp.bsb.ir;

import jp.bsb.diagnostics.SourceSpan;

/**
 * IR命令に共通する型です。
 *
 * <p>【コンピュータ科学の観点：中間表現】ASTの多様な構文を少数の命令へ縮約すると、実行器は字句、コメント、構文の入れ子を意識せずに済みます。
 */
public sealed interface IrInstruction
    permits ArrayLoopNext,
        ArrayLoopStart,
        BranchIfFalse,
        BuildArray,
        Call,
        CountedLoopNext,
        CountedLoopStart,
        Jump,
        PropagateOrReturn,
        PushConst,
        Return,
        StorageInstruction {
  /**
   * 元ソース上の位置を返します。
   *
   * @return 命令のソース範囲
   */
  SourceSpan span();

  /**
   * トレースへ出す命令名を返します。
   *
   * @return 安定した命令名
   */
  String opcode();
}
