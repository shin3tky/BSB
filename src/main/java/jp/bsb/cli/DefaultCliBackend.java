package jp.bsb.cli;

import java.io.IOException;
import java.nio.file.Path;
import jp.bsb.analyzer.AnalysisResult;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.format.FormatResult;
import jp.bsb.format.SourceFormatter;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.ProgramRunResult;
import jp.bsb.runtime.ProgramRunner;

/** 既存のcheck・run・formatパイプラインを公開CLIへ接続する標準バックエンドです。 */
final class DefaultCliBackend implements CliBackend {
  private final SourceChecker checker = new SourceChecker();
  private final ProgramRunner runner = new ProgramRunner();
  private final SourceFormatter formatter = new SourceFormatter();

  @Override
  public AnalysisResult check(Path path) throws IOException {
    return checker.check(path);
  }

  @Override
  public AnalysisResult explain(Path path) throws IOException {
    return checker.check(path);
  }

  @Override
  public ProgramRunResult run(Path path, ExecutionContext context) throws IOException {
    return runner.run(path, context);
  }

  @Override
  public FormatResult format(Path path) throws IOException {
    return formatter.format(path);
  }
}
