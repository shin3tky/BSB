package jp.bsb.ir;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jp.bsb.diagnostics.Diagnostic;

/**
 * IR生成の成功値または構造化診断を保持する安全ゲートです。
 *
 * @param program 成功時だけ存在するIR
 * @param diagnostics 失敗時のIR診断
 */
public record IrGenerationResult(Optional<IrProgram> program, List<Diagnostic> diagnostics) {
  /** 成功値と診断を検証し、不変値として保持します。 */
  public IrGenerationResult {
    Objects.requireNonNull(program, "program");
    diagnostics = List.copyOf(diagnostics);
    if (program.isPresent() == !diagnostics.isEmpty()) {
      throw new IllegalArgumentException("a program is present exactly when diagnostics are empty");
    }
  }

  /**
   * 生成に成功したかを返します。
   *
   * @return IRが存在すればtrue
   */
  public boolean successful() {
    return program.isPresent();
  }

  /**
   * 成功したIRを返します。
   *
   * @return 実行可能なIR
   * @throws IllegalStateException IR生成に失敗している場合
   */
  public IrProgram programForExecution() {
    return program.orElseThrow(() -> new IllegalStateException("IR generation failed"));
  }
}
