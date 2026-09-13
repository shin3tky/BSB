package jp.bsb.runtime;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.ir.IrGenerator;

/**
 * UTF-8入力から静的検査、IR生成、実行までを順番に接続する {@code run} 用パイプラインです。
 *
 * <p>上流でエラーが出た場合は後段へ進まないため、不正なASTや部分IRが実行器へ渡ることはありません。CLI層はこの結果を解釈し、標準出力と標準エラーへ分離します。
 */
public final class ProgramRunner {
  private final SourceChecker checker = new SourceChecker();
  private final IrGenerator irGenerator = new IrGenerator();
  private final Interpreter interpreter = new Interpreter();

  /** 標準の各処理段階を接続した実行パイプラインを作ります。 */
  public ProgramRunner() {}

  /**
   * ファイルを読み込み、指定環境で実行します。
   *
   * @param path 入力ファイル
   * @param context 実行環境
   * @return 全段階の終了結果
   * @throws IOException ファイルの物理的なI/Oエラーが発生した場合
   */
  public ProgramRunResult run(Path path, ExecutionContext context) throws IOException {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(context, "context");
    return runAnalyzed(checker.check(path), context);
  }

  /**
   * 生の入力バイト列を厳密なUTF-8として読み、指定環境で実行します。
   *
   * @param sourcePath 診断表示用のソース識別パス
   * @param bytes BOMを含み得る元バイト列
   * @param context 実行環境
   * @return 全段階の終了結果
   */
  public ProgramRunResult run(String sourcePath, byte[] bytes, ExecutionContext context) {
    Objects.requireNonNull(sourcePath, "sourcePath");
    Objects.requireNonNull(bytes, "bytes");
    Objects.requireNonNull(context, "context");

    return runAnalyzed(checker.check(sourcePath, bytes), context);
  }

  private ProgramRunResult runAnalyzed(
      jp.bsb.analyzer.AnalysisResult analysis, ExecutionContext context) {
    if (!analysis.successful()) {
      return new ProgramRunResult(analysis.exitCode(), analysis.diagnostics(), List.of(), 0, 0);
    }

    var ir = irGenerator.generate(analysis.programForIrGeneration());
    if (!ir.successful()) {
      var diagnostics = new ArrayList<>(analysis.diagnostics());
      diagnostics.addAll(ir.diagnostics());
      return new ProgramRunResult(8, diagnostics, List.of(), 0, 0);
    }

    var execution = interpreter.execute(ir.programForExecution(), context);
    var diagnostics = new ArrayList<>(analysis.diagnostics());
    execution.diagnostic().ifPresent(diagnostics::add);
    return new ProgramRunResult(
        execution.exitCode(),
        diagnostics,
        execution.finalDataStack(),
        execution.executedInstructions(),
        execution.outputBytes(),
        execution.finalGlobalValues(),
        execution.arrayConstructionUnits(),
        execution.arrayElementOperationUnits(),
        execution.regexWorkUnits(),
        execution.jsonConstructionUnits(),
        execution.jsonWorkUnits(),
        execution.byteSequenceConstructionBytes(),
        execution.byteSequenceWorkBytes(),
        execution.httpMetadataConstructionBytes(),
        execution.httpSendCalls(),
        execution.httpRequestAttemptBytes(),
        execution.httpResponseReceivedBytes(),
        execution.fileOperations(),
        execution.fileReadBytes(),
        execution.fileWriteAttemptBytes(),
        execution.delimitedTextWorkUnits(),
        execution.errorOutputBytes(),
        execution.termination());
  }
}
