package jp.bsb.analyzer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.Severity;
import jp.bsb.frontend.SourceText;

/**
 * 静的検査の診断と、成功時だけ存在するIR生成用プログラムを保持します。
 *
 * <p>【コンピュータ科学の観点：フェーズ間の安全ゲート】 エラーを含むASTから中間表現を作ると、後段が存在しない名前や不正な型を前提に動いてしまいます。 {@link Optional}
 * で成功結果の有無を型として表し、呼出し側の見落としを防ぎます。
 *
 * @param analyzedProgram 成功時の検査済みプログラム。失敗時は空
 * @param diagnostics 規定順に並んだ診断の不変リスト
 * @param source デコードに成功した場合の、解析に使用した不変ソーススナップショット
 */
public record AnalysisResult(
    Optional<AnalyzedProgram> analyzedProgram,
    List<Diagnostic> diagnostics,
    Optional<SourceText> source) {
  /** 結果の各要素を非nullの不変値として保持します。 */
  public AnalysisResult {
    Objects.requireNonNull(analyzedProgram, "analyzedProgram");
    diagnostics = List.copyOf(diagnostics);
    Objects.requireNonNull(source, "source");
    boolean hasErrors =
        diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
    if (analyzedProgram.isPresent() == hasErrors) {
      throw new IllegalArgumentException(
          "an analyzed program must be present exactly when diagnostics contain no errors");
    }
  }

  /**
   * エラーなしで静的検査を完了したかを返します。警告だけなら成功です。
   *
   * @return IR生成へ進める場合はtrue
   */
  public boolean successful() {
    return analyzedProgram.isPresent();
  }

  /**
   * 言語コアの診断契約に対応する終了分類を返します。
   *
   * <p>CLIへの配線とは独立して、UTF-8・字句・構文失敗の9と、静的失敗の8をこの時点で区別しておくことで、UI層が診断コードを再解釈せずに済みます。
   *
   * @return 成功（警告のみを含む）は0、静的失敗は8、構文系失敗は9
   */
  public int exitCode() {
    if (successful()) {
      return 0;
    }
    Diagnostic firstError =
        diagnostics.stream()
            .filter(diagnostic -> diagnostic.severity() == Severity.ERROR)
            .findFirst()
            .orElseThrow();
    return switch (firstError.stage()) {
      case UTF8, LEXICAL, SYNTAX -> 9;
      case NAME, TYPE_AND_STACK, IR -> 8;
      case RUNTIME -> 10;
    };
  }

  /**
   * IR生成へ渡せる検査済みプログラムを返します。
   *
   * @return 検査済みプログラム
   * @throws IllegalStateException 静的検査以前のいずれかでエラーがあった場合
   */
  public AnalyzedProgram programForIrGeneration() {
    return analyzedProgram.orElseThrow(
        () -> new IllegalStateException("a program with static errors cannot enter IR generation"));
  }

  static AnalysisResult success(AnalyzedProgram program, List<Diagnostic> diagnostics) {
    return new AnalysisResult(Optional.of(program), diagnostics, Optional.empty());
  }

  static AnalysisResult failure(List<Diagnostic> diagnostics) {
    return new AnalysisResult(Optional.empty(), diagnostics, Optional.empty());
  }

  AnalysisResult withSource(SourceText value) {
    Objects.requireNonNull(value, "value");
    if (source.isPresent()) {
      throw new IllegalStateException("source snapshot is already attached");
    }
    return new AnalysisResult(analyzedProgram, diagnostics, Optional.of(value));
  }
}
