package jp.bsb.runtime;

import java.util.Objects;
import java.util.Optional;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourceSpan;

/** 能力確認、独立した64 MiB上限、1呼出し単位の原子的委譲を適用します。 */
final class BoundedOutput {
  private final String sourcePath;
  private final Optional<ConsoleOutput> output;
  private final RuntimeCapability capability;
  private final DiagnosticCode limitCode;
  private long writtenBytes;

  BoundedOutput(String sourcePath, OutputSink sink) {
    this(sourcePath, sink, 0);
  }

  BoundedOutput(String sourcePath, OutputSink sink, long initialBytes) {
    this(
        sourcePath,
        Optional.of(ConsoleOutput.fromOutputSink(Objects.requireNonNull(sink, "sink"))),
        RuntimeCapability.CONSOLE_OUTPUT,
        DiagnosticCode.E_OUTPUT_LIMIT,
        initialBytes);
  }

  static BoundedOutput standard(String sourcePath, ExecutionEnvironment environment) {
    return new BoundedOutput(
        sourcePath,
        environment.consoleOutput(),
        RuntimeCapability.CONSOLE_OUTPUT,
        DiagnosticCode.E_OUTPUT_LIMIT,
        0);
  }

  static BoundedOutput error(String sourcePath, ExecutionEnvironment environment) {
    return new BoundedOutput(
        sourcePath,
        environment.consoleError(),
        RuntimeCapability.CONSOLE_ERROR,
        DiagnosticCode.E_ERROR_OUTPUT_LIMIT,
        0);
  }

  static BoundedOutput error(String sourcePath, ConsoleOutput output, long initialBytes) {
    return new BoundedOutput(
        sourcePath,
        Optional.of(Objects.requireNonNull(output, "output")),
        RuntimeCapability.CONSOLE_ERROR,
        DiagnosticCode.E_ERROR_OUTPUT_LIMIT,
        initialBytes);
  }

  private BoundedOutput(
      String sourcePath,
      Optional<ConsoleOutput> output,
      RuntimeCapability capability,
      DiagnosticCode limitCode,
      long initialBytes) {
    this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
    this.output = Objects.requireNonNull(output, "output");
    this.capability = Objects.requireNonNull(capability, "capability");
    this.limitCode = Objects.requireNonNull(limitCode, "limitCode");
    if (initialBytes < 0 || initialBytes > RuntimeLimits.OUTPUT_UTF8_BYTES) {
      throw new IllegalArgumentException("invalid initial output byte count");
    }
    writtenBytes = initialBytes;
  }

  void write(byte[] bytes, SourceSpan span) throws RuntimeFailure {
    write(bytes, capability == RuntimeCapability.CONSOLE_ERROR ? "エラー表示する" : "表示する", span);
  }

  void write(byte[] bytes, String word, SourceSpan span) throws RuntimeFailure {
    Objects.requireNonNull(bytes, "bytes");
    ConsoleOutput target = output.orElseThrow(() -> unavailable(word, span));
    long observed = requireWithinLimit(bytes.length, word, span);
    try {
      target.write(bytes.clone());
    } catch (CapabilityException failure) {
      if (failure.kind() != CapabilityException.Kind.FAILURE
          || failure.capability() != capability) {
        throw new IllegalStateException("output capability violated its failure contract");
      }
      throw capabilityFailure(word, failure.operation(), span);
    } catch (RuntimeException failure) {
      throw capabilityFailure(word, "write", span);
    }
    writtenBytes = observed;
  }

  /** 候補出力長だけを検査し、能力呼出しと累積値の更新は行いません。 */
  long requireWithinLimit(long additionalBytes, String word, SourceSpan span)
      throws RuntimeFailure {
    if (additionalBytes < 0) {
      throw new IllegalArgumentException("additional output bytes must not be negative");
    }
    long observed = addForLimit(writtenBytes, additionalBytes);
    if (observed > RuntimeLimits.OUTPUT_UTF8_BYTES) {
      throw limit(word, additionalBytes, observed, span);
    }
    return observed;
  }

  long writtenBytes() {
    return writtenBytes;
  }

  private RuntimeFailure unavailable(String word, SourceSpan span) {
    String target = capability == RuntimeCapability.CONSOLE_ERROR ? "標準エラー" : "標準出力";
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_CAPABILITY_UNAVAILABLE,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word)
            .field("capability", capability.sourceName())
            .expected("利用可能な" + capability.sourceName())
            .actual("能力なし")
            .fix(target + "能力を持つ実行環境で実行してください")
            .build());
  }

  private RuntimeFailure capabilityFailure(String word, String operation, SourceSpan span) {
    boolean error = capability == RuntimeCapability.CONSOLE_ERROR;
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_CAPABILITY_FAILURE,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word)
            .field("capability", capability.sourceName())
            .field("operation", operation)
            .expected(error ? "成功する標準エラー能力" : "成功する出力能力")
            .actual("能力失敗")
            .fix(error ? "標準エラー先を確認してください" : "標準出力先を確認してください")
            .build());
  }

  private RuntimeFailure limit(String word, long additionalBytes, long observed, SourceSpan span) {
    boolean error = capability == RuntimeCapability.CONSOLE_ERROR;
    return new RuntimeFailure(
        Diagnostic.builder(limitCode, Severity.ERROR, DiagnosticStage.RUNTIME, sourcePath, span)
            .field("word", word)
            .field("additionalBytes", Long.toString(additionalBytes))
            .field("currentBytes", Long.toString(writtenBytes))
            .limit(
                error ? "errorOutputUtf8Bytes" : "outputUtf8Bytes",
                RuntimeLimits.OUTPUT_UTF8_BYTES,
                observed)
            .expected(RuntimeLimits.OUTPUT_UTF8_BYTES + "バイト以下")
            .actual(observed + "バイト")
            .fix(error ? "標準エラーへ出力する量を減らしてください。" : "出力する量を減らしてください。")
            .build());
  }

  private static long addForLimit(long used, long requested) {
    return requested > Long.MAX_VALUE - used ? Long.MAX_VALUE : used + requested;
  }
}
