package jp.bsb.cli;

import java.io.IOException;
import java.nio.file.Path;
import jp.bsb.analyzer.AnalysisResult;
import jp.bsb.format.FormatResult;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.ExecutionLimits;
import jp.bsb.runtime.ProgramRunResult;

/** CLIから言語処理パイプラインを分離し、実I/Oを使わないCLI試験を可能にする内部境界です。 */
interface CliBackend {
  /** 静的検査を実行します。 */
  AnalysisResult check(Path path) throws IOException;

  /** 静的説明の入力となる検査済みプログラムを取得します。 */
  default AnalysisResult explain(Path path) throws IOException {
    return check(path);
  }

  /** プログラムを実行します。 */
  ProgramRunResult run(Path path, ExecutionContext context) throws IOException;

  /** 指定された実行上限でプログラムを実行します。 */
  default ProgramRunResult run(Path path, ExecutionContext context, ExecutionLimits limits)
      throws IOException {
    return run(path, context);
  }

  /** 正規フォーマットを実行します。 */
  FormatResult format(Path path) throws IOException;
}
