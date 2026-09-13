package jp.bsb.ir;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jp.bsb.diagnostics.SourceSpan;

/**
 * 名前解決済みの単語を呼び出すIR命令です。
 *
 * @param symbolId 呼出先の内部識別子
 * @param targetName トレース・診断用の正規名
 * @param particles この呼出しへ関連づけられた助詞
 * @param stackEffect 型変数を具体化した検証用スタック効果。旧来の合成IRでは空
 * @param span 呼出し名のソース範囲
 * @param returnsNormally 呼出元の次命令へ戻り得る場合はtrue
 * @param logicalConnection 解決済みの静的論理接続引数。通常呼出しでは空
 */
public record Call(
    SymbolId symbolId,
    String targetName,
    List<ParticleSource> particles,
    Optional<IrStackEffect> stackEffect,
    SourceSpan span,
    boolean returnsNormally,
    Optional<LogicalConnectionReference> logicalConnection,
    Optional<WorkspaceReference> workspace)
    implements IrInstruction {
  /**
   * 具体的なスタック効果を持たない、束縛までの合成IR呼出しを作ります。
   *
   * @param symbolId 呼出先の内部識別子
   * @param targetName トレース・診断用の正規名
   * @param particles 呼出しへ関連づけられた助詞
   * @param span 呼出し名のソース範囲
   */
  public Call(
      SymbolId symbolId, String targetName, List<ParticleSource> particles, SourceSpan span) {
    this(
        symbolId,
        targetName,
        particles,
        Optional.empty(),
        span,
        true,
        Optional.empty(),
        Optional.empty());
  }

  /**
   * 解析済みの具体的なスタック効果を持つ呼出しを作ります。
   *
   * @param symbolId 呼出先の内部識別子
   * @param targetName トレース・診断用の正規名
   * @param particles 呼出しへ関連づけられた助詞
   * @param stackEffect 型変数を具体化した入力・出力型
   * @param span 呼出し名のソース範囲
   */
  public Call(
      SymbolId symbolId,
      String targetName,
      List<ParticleSource> particles,
      IrStackEffect stackEffect,
      SourceSpan span) {
    this(
        symbolId,
        targetName,
        particles,
        Optional.of(stackEffect),
        span,
        true,
        Optional.empty(),
        Optional.empty());
  }

  /** 解析済みの具体効果と通常復帰性を持つ呼出しを作ります。 */
  public Call(
      SymbolId symbolId,
      String targetName,
      List<ParticleSource> particles,
      IrStackEffect stackEffect,
      SourceSpan span,
      boolean returnsNormally) {
    this(
        symbolId,
        targetName,
        particles,
        Optional.of(stackEffect),
        span,
        returnsNormally,
        Optional.empty(),
        Optional.empty());
  }

  /** 回復可能JSONまでの完全な呼出し表現を作る互換コンストラクタです。 */
  public Call(
      SymbolId symbolId,
      String targetName,
      List<ParticleSource> particles,
      Optional<IrStackEffect> stackEffect,
      SourceSpan span,
      boolean returnsNormally) {
    this(
        symbolId,
        targetName,
        particles,
        stackEffect,
        span,
        returnsNormally,
        Optional.empty(),
        Optional.empty());
  }

  /** 多次元配列までの完全な呼出し表現を作る互換コンストラクタです。 */
  public Call(
      SymbolId symbolId,
      String targetName,
      List<ParticleSource> particles,
      Optional<IrStackEffect> stackEffect,
      SourceSpan span,
      boolean returnsNormally,
      Optional<LogicalConnectionReference> logicalConnection) {
    this(
        symbolId,
        targetName,
        particles,
        stackEffect,
        span,
        returnsNormally,
        logicalConnection,
        Optional.empty());
  }

  /** 呼出先と助詞情報を不変値として保持します。 */
  public Call {
    Objects.requireNonNull(symbolId, "symbolId");
    if (targetName == null || targetName.isBlank()) {
      throw new IllegalArgumentException("targetName must not be blank");
    }
    particles = List.copyOf(particles);
    stackEffect = Objects.requireNonNull(stackEffect, "stackEffect");
    Objects.requireNonNull(span, "span");
    logicalConnection = Objects.requireNonNull(logicalConnection, "logicalConnection");
    workspace = Objects.requireNonNull(workspace, "workspace");
    if (logicalConnection.isPresent() && workspace.isPresent()) {
      throw new IllegalArgumentException("a call cannot carry two static resource references");
    }
  }

  @Override
  public String opcode() {
    return "Call:" + targetName;
  }
}
