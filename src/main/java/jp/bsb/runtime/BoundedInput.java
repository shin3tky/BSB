package jp.bsb.runtime;

import java.util.HexFormat;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourceSpan;

/** 入力能力、厳密UTF-8、行・累積上限を規定順に適用します。 */
final class BoundedInput {
  private static final HexFormat HEX = HexFormat.of().withUpperCase();

  private final String sourcePath;
  private final ExecutionEnvironment environment;
  private final ExecutionBudget budget;
  private long consumedBytes;

  BoundedInput(String sourcePath, ExecutionEnvironment environment, ExecutionBudget budget) {
    this.sourcePath = sourcePath;
    this.environment = environment;
    this.budget = budget;
  }

  InputResultValue readLine(String word, SourceSpan span) throws RuntimeFailure {
    ConsoleInput input =
        environment.consoleInput().orElseThrow(() -> capabilityUnavailable(word, span));
    InputEvent event;
    long blockedAt = budget.beginBlocking();
    try {
      event = input.readLine();
    } catch (CapabilityException failure) {
      throw capabilityFailure(word, span, failure);
    } catch (RuntimeException failure) {
      throw capabilityFailure(
          word, span, CapabilityException.failure(RuntimeCapability.CONSOLE_INPUT, "readLine"));
    } finally {
      budget.endBlocking(blockedAt);
    }
    if (event == null) {
      throw new IllegalStateException("console.input returned null");
    }
    if (event instanceof InputEvent.End) {
      return InputResultValue.end();
    }
    if (event instanceof InputEvent.Cancel) {
      return InputResultValue.cancel();
    }
    InputEvent.Line line = (InputEvent.Line) event;
    byte[] bytes = line.ownedBytes();
    long observed = saturatedAdd(consumedBytes, line.consumedBytes());
    if (observed > RuntimeLimits.INPUT_TOTAL_BYTES) {
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_INPUT_TOTAL_LIMIT,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word)
              .field("currentBytes", Long.toString(consumedBytes))
              .field("additionalBytes", Integer.toString(line.consumedBytes()))
              .limit("inputBytes", RuntimeLimits.INPUT_TOTAL_BYTES, observed)
              .expected(RuntimeLimits.INPUT_TOTAL_BYTES + "バイト以下")
              .actual(observed + "バイト")
              .fix("入力量を減らしてください")
              .build());
    }
    var invalid = StrictUtf8.firstInvalidOffset(bytes);
    if (invalid.isPresent()) {
      int offset = invalid.getAsInt();
      int end = Math.min(bytes.length, offset + 4);
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_INPUT_UTF8,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word)
              .field("byteOffset", Integer.toString(offset))
              .field("bytes", HEX.formatHex(bytes, offset, end))
              .expected("正しいUTF-8")
              .actual(HEX.formatHex(bytes, offset, end))
              .fix("入力をUTF-8で送ってください")
              .build());
    }
    if (bytes.length > RuntimeLimits.INPUT_LINE_UTF8_BYTES) {
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_INPUT_LINE_LIMIT,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word)
              .limit("inputLineUtf8Bytes", RuntimeLimits.INPUT_LINE_UTF8_BYTES, bytes.length)
              .expected(RuntimeLimits.INPUT_LINE_UTF8_BYTES + "バイト以下")
              .actual(bytes.length + "バイト")
              .fix("入力行を短くしてください")
              .build());
    }
    consumedBytes = observed;
    return InputResultValue.line(StrictUtf8.decodeValidated(bytes));
  }

  long consumedBytes() {
    return consumedBytes;
  }

  private RuntimeFailure capabilityUnavailable(String word, SourceSpan span) {
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_CAPABILITY_UNAVAILABLE,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word)
            .field("capability", RuntimeCapability.CONSOLE_INPUT.sourceName())
            .expected("利用可能なconsole.input")
            .actual("能力なし")
            .fix("入力能力を持つ実行環境で実行してください")
            .build());
  }

  private RuntimeFailure capabilityFailure(
      String word, SourceSpan span, CapabilityException failure) {
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_CAPABILITY_FAILURE,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word)
            .field("capability", RuntimeCapability.CONSOLE_INPUT.sourceName())
            .field("operation", failure.operation())
            .expected("成功する入力能力")
            .actual("能力失敗")
            .fix("入力元を確認してください")
            .build());
  }

  private static long saturatedAdd(long left, int right) {
    return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
  }
}
