package jp.bsb.runtime;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jp.bsb.diagnostics.Diagnostic;

/**
 * インタープリタの終了状態、最終データスタック、実行量を保持します。
 *
 * @param diagnostic 失敗時だけ存在する実行時診断
 * @param finalDataStack 終了または失敗時のデータスタック
 * @param executedInstructions 検査を通過した命令数
 * @param outputBytes 正常に書かれたUTF-8バイト数
 * @param finalGlobalValues 終了または失敗時の大域スロット値。未初期化なら空
 * @param arrayConstructionUnits 正常に受理した配列要素構築単位
 * @param arrayElementOperationUnits 正常に受理した配列要素処理単位
 * @param regexWorkUnits 照合開始前に正常に受理した正規表現累積照合単位
 * @param jsonConstructionUnits 正常に受理したJSON構築単位
 * @param jsonWorkUnits 正常に受理したJSON作業単位
 * @param byteSequenceConstructionBytes 正常に受理したバイト列構築バイト数
 * @param byteSequenceWorkBytes 正常に受理したバイト列作業バイト数
 * @param httpMetadataConstructionBytes 正常に受理したHTTP metadata構築バイト数
 * @param httpSendCalls 正常に受理したHTTP送信回数
 * @param httpRequestAttemptBytes 正常に受理したHTTP要求本文試行バイト数
 * @param httpResponseReceivedBytes 実際に受信したHTTP応答本文バイト数
 * @param fileOperations 正常に受理したファイル操作数
 * @param fileReadBytes 正常に受理したファイル読取成功バイト数
 * @param fileWriteAttemptBytes 正常に受理したファイル書込試行バイト数
 * @param delimitedTextWorkUnits 正常に受理した区切りテキスト作業単位
 * @param termination 埋込みホスト向けの終了原因
 * @param errorOutputBytes プログラム標準エラーへ正常に書かれたUTF-8バイト数
 * @param httpReliabilityWaitMilliseconds HTTP信頼性機構が要求した待機ミリ秒数
 * @param httpRetryAttempts 初回送信を除くHTTP物理再試行回数
 * @param httpFinalFailureRecords 予約済みHTTP最終失敗記録数
 */
public record ExecutionResult(
    Optional<Diagnostic> diagnostic,
    List<RuntimeValue> finalDataStack,
    long executedInstructions,
    long outputBytes,
    List<Optional<RuntimeValue>> finalGlobalValues,
    long arrayConstructionUnits,
    long arrayElementOperationUnits,
    long regexWorkUnits,
    long jsonConstructionUnits,
    long jsonWorkUnits,
    long byteSequenceConstructionBytes,
    long byteSequenceWorkBytes,
    long httpMetadataConstructionBytes,
    long httpSendCalls,
    long httpRequestAttemptBytes,
    long httpResponseReceivedBytes,
    long fileOperations,
    long fileReadBytes,
    long fileWriteAttemptBytes,
    long delimitedTextWorkUnits,
    ExecutionTermination termination,
    long errorOutputBytes,
    long httpReliabilityWaitMilliseconds,
    long httpRetryAttempts,
    long httpFinalFailureRecords) {
  /** HTTP信頼性資源を追加する前の全資源量を指定する互換コンストラクタです。 */
  public ExecutionResult(
      Optional<Diagnostic> diagnostic,
      List<RuntimeValue> finalDataStack,
      long executedInstructions,
      long outputBytes,
      List<Optional<RuntimeValue>> finalGlobalValues,
      long arrayConstructionUnits,
      long arrayElementOperationUnits,
      long regexWorkUnits,
      long jsonConstructionUnits,
      long jsonWorkUnits,
      long byteSequenceConstructionBytes,
      long byteSequenceWorkBytes,
      long httpMetadataConstructionBytes,
      long httpSendCalls,
      long httpRequestAttemptBytes,
      long httpResponseReceivedBytes,
      long fileOperations,
      long fileReadBytes,
      long fileWriteAttemptBytes,
      long delimitedTextWorkUnits,
      ExecutionTermination termination,
      long errorOutputBytes) {
    this(
        diagnostic,
        finalDataStack,
        executedInstructions,
        outputBytes,
        finalGlobalValues,
        arrayConstructionUnits,
        arrayElementOperationUnits,
        regexWorkUnits,
        jsonConstructionUnits,
        jsonWorkUnits,
        byteSequenceConstructionBytes,
        byteSequenceWorkBytes,
        httpMetadataConstructionBytes,
        httpSendCalls,
        httpRequestAttemptBytes,
        httpResponseReceivedBytes,
        fileOperations,
        fileReadBytes,
        fileWriteAttemptBytes,
        delimitedTextWorkUnits,
        termination,
        errorOutputBytes,
        0,
        0,
        0);
  }

  /** 作業領域・区切り表までの全資源量を指定する正規コンストラクタとの互換形です。 */
  public ExecutionResult(
      Optional<Diagnostic> diagnostic,
      List<RuntimeValue> finalDataStack,
      long executedInstructions,
      long outputBytes,
      List<Optional<RuntimeValue>> finalGlobalValues,
      long arrayConstructionUnits,
      long arrayElementOperationUnits,
      long regexWorkUnits,
      long jsonConstructionUnits,
      long jsonWorkUnits,
      long byteSequenceConstructionBytes,
      long byteSequenceWorkBytes,
      long httpMetadataConstructionBytes,
      long httpSendCalls,
      long httpRequestAttemptBytes,
      long httpResponseReceivedBytes,
      long fileOperations,
      long fileReadBytes,
      long fileWriteAttemptBytes,
      ExecutionTermination termination,
      long errorOutputBytes) {
    this(
        diagnostic,
        finalDataStack,
        executedInstructions,
        outputBytes,
        finalGlobalValues,
        arrayConstructionUnits,
        arrayElementOperationUnits,
        regexWorkUnits,
        jsonConstructionUnits,
        jsonWorkUnits,
        byteSequenceConstructionBytes,
        byteSequenceWorkBytes,
        httpMetadataConstructionBytes,
        httpSendCalls,
        httpRequestAttemptBytes,
        httpResponseReceivedBytes,
        fileOperations,
        fileReadBytes,
        fileWriteAttemptBytes,
        0,
        termination,
        errorOutputBytes);
  }

  /** 多次元配列までの全資源量を指定する正規コンストラクタとの互換形です。 */
  public ExecutionResult(
      Optional<Diagnostic> diagnostic,
      List<RuntimeValue> finalDataStack,
      long executedInstructions,
      long outputBytes,
      List<Optional<RuntimeValue>> finalGlobalValues,
      long arrayConstructionUnits,
      long arrayElementOperationUnits,
      long regexWorkUnits,
      long jsonConstructionUnits,
      long jsonWorkUnits,
      long byteSequenceConstructionBytes,
      long byteSequenceWorkBytes,
      long httpMetadataConstructionBytes,
      long httpSendCalls,
      long httpRequestAttemptBytes,
      long httpResponseReceivedBytes,
      ExecutionTermination termination,
      long errorOutputBytes) {
    this(
        diagnostic,
        finalDataStack,
        executedInstructions,
        outputBytes,
        finalGlobalValues,
        arrayConstructionUnits,
        arrayElementOperationUnits,
        regexWorkUnits,
        jsonConstructionUnits,
        jsonWorkUnits,
        byteSequenceConstructionBytes,
        byteSequenceWorkBytes,
        httpMetadataConstructionBytes,
        httpSendCalls,
        httpRequestAttemptBytes,
        httpResponseReceivedBytes,
        0,
        0,
        0,
        termination,
        errorOutputBytes);
  }

  /** バイト列までの全資源量を指定する正規コンストラクタとの互換形です。 */
  public ExecutionResult(
      Optional<Diagnostic> diagnostic,
      List<RuntimeValue> finalDataStack,
      long executedInstructions,
      long outputBytes,
      List<Optional<RuntimeValue>> finalGlobalValues,
      long arrayConstructionUnits,
      long arrayElementOperationUnits,
      long regexWorkUnits,
      long jsonConstructionUnits,
      long jsonWorkUnits,
      long byteSequenceConstructionBytes,
      long byteSequenceWorkBytes,
      ExecutionTermination termination,
      long errorOutputBytes) {
    this(
        diagnostic,
        finalDataStack,
        executedInstructions,
        outputBytes,
        finalGlobalValues,
        arrayConstructionUnits,
        arrayElementOperationUnits,
        regexWorkUnits,
        jsonConstructionUnits,
        jsonWorkUnits,
        byteSequenceConstructionBytes,
        byteSequenceWorkBytes,
        0,
        0,
        0,
        0,
        termination,
        errorOutputBytes);
  }

  /** 論理接続までの全資源量を指定する正規コンストラクタとの互換形です。 */
  public ExecutionResult(
      Optional<Diagnostic> diagnostic,
      List<RuntimeValue> finalDataStack,
      long executedInstructions,
      long outputBytes,
      List<Optional<RuntimeValue>> finalGlobalValues,
      long arrayConstructionUnits,
      long arrayElementOperationUnits,
      long regexWorkUnits,
      long jsonConstructionUnits,
      long jsonWorkUnits,
      ExecutionTermination termination,
      long errorOutputBytes) {
    this(
        diagnostic,
        finalDataStack,
        executedInstructions,
        outputBytes,
        finalGlobalValues,
        arrayConstructionUnits,
        arrayElementOperationUnits,
        regexWorkUnits,
        jsonConstructionUnits,
        jsonWorkUnits,
        0,
        0,
        termination,
        errorOutputBytes);
  }

  /** ホスト入出力までの全資源量を指定する互換コンストラクタです。 */
  public ExecutionResult(
      Optional<Diagnostic> diagnostic,
      List<RuntimeValue> finalDataStack,
      long executedInstructions,
      long outputBytes,
      List<Optional<RuntimeValue>> finalGlobalValues,
      long arrayConstructionUnits,
      long arrayElementOperationUnits,
      long regexWorkUnits,
      ExecutionTermination termination,
      long errorOutputBytes) {
    this(
        diagnostic,
        finalDataStack,
        executedInstructions,
        outputBytes,
        finalGlobalValues,
        arrayConstructionUnits,
        arrayElementOperationUnits,
        regexWorkUnits,
        0,
        0,
        0,
        0,
        termination,
        errorOutputBytes);
  }

  /**
   * 保存領域を持たない従来IRの終了結果を作ります。
   *
   * @param diagnostic 失敗時だけ存在する実行時診断
   * @param finalDataStack 終了または失敗時のデータスタック
   * @param executedInstructions 検査を通過した命令数
   * @param outputBytes 正常に書かれたUTF-8バイト数
   */
  public ExecutionResult(
      Optional<Diagnostic> diagnostic,
      List<RuntimeValue> finalDataStack,
      long executedInstructions,
      long outputBytes) {
    this(
        diagnostic,
        finalDataStack,
        executedInstructions,
        outputBytes,
        List.of(),
        0,
        0,
        0,
        defaultTermination(diagnostic),
        0);
  }

  /**
   * 配列予算を使わない束縛までの保存領域つき結果を作ります。
   *
   * @param diagnostic 失敗時だけ存在する実行時診断
   * @param finalDataStack 終了または失敗時のデータスタック
   * @param executedInstructions 検査を通過した命令数
   * @param outputBytes 正常に書かれたUTF-8バイト数
   * @param finalGlobalValues 終了または失敗時の大域スロット値
   */
  public ExecutionResult(
      Optional<Diagnostic> diagnostic,
      List<RuntimeValue> finalDataStack,
      long executedInstructions,
      long outputBytes,
      List<Optional<RuntimeValue>> finalGlobalValues) {
    this(
        diagnostic,
        finalDataStack,
        executedInstructions,
        outputBytes,
        finalGlobalValues,
        0,
        0,
        0,
        defaultTermination(diagnostic),
        0);
  }

  /**
   * 第5・数値演算の配列予算までを指定する互換コンストラクタです。
   *
   * @param diagnostic 失敗時だけ存在する実行時診断
   * @param finalDataStack 終了または失敗時のデータスタック
   * @param executedInstructions 検査を通過した命令数
   * @param outputBytes 正常に書かれたUTF-8バイト数
   * @param finalGlobalValues 終了または失敗時の大域スロット値
   * @param arrayConstructionUnits 正常に受理した配列要素構築単位
   * @param arrayElementOperationUnits 正常に受理した配列要素処理単位
   */
  public ExecutionResult(
      Optional<Diagnostic> diagnostic,
      List<RuntimeValue> finalDataStack,
      long executedInstructions,
      long outputBytes,
      List<Optional<RuntimeValue>> finalGlobalValues,
      long arrayConstructionUnits,
      long arrayElementOperationUnits) {
    this(
        diagnostic,
        finalDataStack,
        executedInstructions,
        outputBytes,
        finalGlobalValues,
        arrayConstructionUnits,
        arrayElementOperationUnits,
        0,
        defaultTermination(diagnostic),
        0);
  }

  /** 文字列・正規表現までの全資源量を指定する互換コンストラクタです。 */
  public ExecutionResult(
      Optional<Diagnostic> diagnostic,
      List<RuntimeValue> finalDataStack,
      long executedInstructions,
      long outputBytes,
      List<Optional<RuntimeValue>> finalGlobalValues,
      long arrayConstructionUnits,
      long arrayElementOperationUnits,
      long regexWorkUnits) {
    this(
        diagnostic,
        finalDataStack,
        executedInstructions,
        outputBytes,
        finalGlobalValues,
        arrayConstructionUnits,
        arrayElementOperationUnits,
        regexWorkUnits,
        defaultTermination(diagnostic),
        0);
  }

  /** ホスト入出力より前の終了原因つき正規コンストラクタとの互換形です。 */
  public ExecutionResult(
      Optional<Diagnostic> diagnostic,
      List<RuntimeValue> finalDataStack,
      long executedInstructions,
      long outputBytes,
      List<Optional<RuntimeValue>> finalGlobalValues,
      long arrayConstructionUnits,
      long arrayElementOperationUnits,
      long regexWorkUnits,
      ExecutionTermination termination) {
    this(
        diagnostic,
        finalDataStack,
        executedInstructions,
        outputBytes,
        finalGlobalValues,
        arrayConstructionUnits,
        arrayElementOperationUnits,
        regexWorkUnits,
        termination,
        0);
  }

  /** 結果を不変値として保持します。 */
  public ExecutionResult {
    Objects.requireNonNull(diagnostic, "diagnostic");
    Objects.requireNonNull(termination, "termination");
    finalDataStack = List.copyOf(finalDataStack);
    finalGlobalValues = List.copyOf(finalGlobalValues);
    if (executedInstructions < 0
        || outputBytes < 0
        || arrayConstructionUnits < 0
        || arrayElementOperationUnits < 0
        || regexWorkUnits < 0
        || jsonConstructionUnits < 0
        || jsonWorkUnits < 0
        || byteSequenceConstructionBytes < 0
        || byteSequenceWorkBytes < 0
        || httpMetadataConstructionBytes < 0
        || httpSendCalls < 0
        || httpRequestAttemptBytes < 0
        || httpResponseReceivedBytes < 0
        || fileOperations < 0
        || fileReadBytes < 0
        || fileWriteAttemptBytes < 0
        || delimitedTextWorkUnits < 0
        || errorOutputBytes < 0
        || httpReliabilityWaitMilliseconds < 0
        || httpRetryAttempts < 0
        || httpFinalFailureRecords < 0) {
      throw new IllegalArgumentException("resource counts must not be negative");
    }
    if (diagnostic.isPresent()
        != (termination.kind() == ExecutionTermination.Kind.DIAGNOSTIC_FAILURE)) {
      throw new IllegalArgumentException("diagnostic and termination kind must agree");
    }
  }

  /**
   * 実行時エラーなしで終了したかを返します。
   *
   * @return 診断がなければtrue
   */
  public boolean successful() {
    return diagnostic.isEmpty();
  }

  /**
   * 実行終了コードを返します。
   *
   * @return 成功は0、実行時失敗は10
   */
  public int exitCode() {
    return switch (termination.kind()) {
      case COMPLETED -> 0;
      case PROGRAM_EXIT -> termination.programExitCode().orElseThrow();
      case DIAGNOSTIC_FAILURE -> 10;
    };
  }

  private static ExecutionTermination defaultTermination(Optional<Diagnostic> diagnostic) {
    Objects.requireNonNull(diagnostic, "diagnostic");
    return diagnostic.isPresent()
        ? ExecutionTermination.diagnosticFailure()
        : ExecutionTermination.completed();
  }
}
