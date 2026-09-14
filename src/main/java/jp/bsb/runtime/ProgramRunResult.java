package jp.bsb.runtime;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jp.bsb.diagnostics.Diagnostic;

/**
 * UTF-8読取りから実行までの終了コード、診断、最終スタックをまとめた結果です。
 *
 * @param exitCode 0、8、9、10の終了分類
 * @param diagnostics 警告または停止原因
 * @param finalDataStack 実行へ到達した場合の終了スタック
 * @param executedInstructions 実行へ到達した場合の命令数
 * @param outputBytes 正常に書かれたUTF-8バイト数
 * @param finalGlobalValues 実行へ到達した場合の最終大域値
 * @param arrayConstructionUnits 実行へ到達した場合の配列要素構築単位
 * @param arrayElementOperationUnits 実行へ到達した場合の配列要素処理単位
 * @param regexWorkUnits 実行へ到達した場合の正規表現累積照合単位
 * @param jsonConstructionUnits 実行へ到達した場合のJSON構築単位
 * @param jsonWorkUnits 実行へ到達した場合のJSON作業単位
 * @param byteSequenceConstructionBytes 実行へ到達した場合のバイト列構築バイト数
 * @param byteSequenceWorkBytes 実行へ到達した場合のバイト列作業バイト数
 * @param httpMetadataConstructionBytes 実行へ到達した場合のHTTP metadata構築バイト数
 * @param httpSendCalls 実行へ到達した場合のHTTP送信回数
 * @param httpRequestAttemptBytes 実行へ到達した場合のHTTP要求本文試行バイト数
 * @param httpResponseReceivedBytes 実行へ到達した場合のHTTP応答本文受信バイト数
 * @param fileOperations 実行へ到達した場合の受理済みファイル操作数
 * @param fileReadBytes 実行へ到達した場合のファイル読取成功バイト数
 * @param fileWriteAttemptBytes 実行へ到達した場合のファイル書込試行バイト数
 * @param delimitedTextWorkUnits 実行へ到達した場合の区切りテキスト作業単位
 * @param errorOutputBytes プログラム標準エラーへ正常に書かれたUTF-8バイト数
 * @param termination 埋込みホスト向けの実行終了原因
 * @param httpReliabilityWaitMilliseconds HTTP信頼性機構が要求した待機ミリ秒数
 * @param httpRetryAttempts 初回送信を除くHTTP物理再試行回数
 * @param httpFinalFailureRecords 予約済みHTTP最終失敗記録数
 * @param jsonShapeWorkUnits 実行へ到達した場合のJSON形状検証作業単位
 */
public record ProgramRunResult(
    int exitCode,
    List<Diagnostic> diagnostics,
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
    long errorOutputBytes,
    ExecutionTermination termination,
    long httpReliabilityWaitMilliseconds,
    long httpRetryAttempts,
    long httpFinalFailureRecords,
    long jsonShapeWorkUnits) {
  /** JSON形状検証資源を追加する前の全資源量を指定する互換コンストラクタです。 */
  public ProgramRunResult(
      int exitCode,
      List<Diagnostic> diagnostics,
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
      long errorOutputBytes,
      ExecutionTermination termination,
      long httpReliabilityWaitMilliseconds,
      long httpRetryAttempts,
      long httpFinalFailureRecords) {
    this(
        exitCode,
        diagnostics,
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
        errorOutputBytes,
        termination,
        httpReliabilityWaitMilliseconds,
        httpRetryAttempts,
        httpFinalFailureRecords,
        0);
  }

  /** HTTP信頼性資源を追加する前の全資源量を指定する互換コンストラクタです。 */
  public ProgramRunResult(
      int exitCode,
      List<Diagnostic> diagnostics,
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
      long errorOutputBytes,
      ExecutionTermination termination) {
    this(
        exitCode,
        diagnostics,
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
        errorOutputBytes,
        termination,
        0,
        0,
        0);
  }

  /** 作業領域・区切り表までの全資源量を指定する正規コンストラクタとの互換形です。 */
  public ProgramRunResult(
      int exitCode,
      List<Diagnostic> diagnostics,
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
      long errorOutputBytes,
      ExecutionTermination termination) {
    this(
        exitCode,
        diagnostics,
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
        errorOutputBytes,
        termination);
  }

  /** 多次元配列までの全資源量を指定する正規コンストラクタとの互換形です。 */
  public ProgramRunResult(
      int exitCode,
      List<Diagnostic> diagnostics,
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
      long errorOutputBytes,
      ExecutionTermination termination) {
    this(
        exitCode,
        diagnostics,
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
        errorOutputBytes,
        termination);
  }

  /** バイト列までの全資源量を指定する正規コンストラクタとの互換形です。 */
  public ProgramRunResult(
      int exitCode,
      List<Diagnostic> diagnostics,
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
      long errorOutputBytes,
      ExecutionTermination termination) {
    this(
        exitCode,
        diagnostics,
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
        errorOutputBytes,
        termination);
  }

  /** 論理接続までの全資源量を指定する正規コンストラクタとの互換形です。 */
  public ProgramRunResult(
      int exitCode,
      List<Diagnostic> diagnostics,
      List<RuntimeValue> finalDataStack,
      long executedInstructions,
      long outputBytes,
      List<Optional<RuntimeValue>> finalGlobalValues,
      long arrayConstructionUnits,
      long arrayElementOperationUnits,
      long regexWorkUnits,
      long jsonConstructionUnits,
      long jsonWorkUnits,
      long errorOutputBytes,
      ExecutionTermination termination) {
    this(
        exitCode,
        diagnostics,
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
        errorOutputBytes,
        termination);
  }

  /** ホスト入出力までの全資源量を指定する互換コンストラクタです。 */
  public ProgramRunResult(
      int exitCode,
      List<Diagnostic> diagnostics,
      List<RuntimeValue> finalDataStack,
      long executedInstructions,
      long outputBytes,
      List<Optional<RuntimeValue>> finalGlobalValues,
      long arrayConstructionUnits,
      long arrayElementOperationUnits,
      long regexWorkUnits,
      long errorOutputBytes,
      ExecutionTermination termination) {
    this(
        exitCode,
        diagnostics,
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
        errorOutputBytes,
        termination);
  }

  /**
   * 保存領域を持たない従来パイプライン結果を作ります。
   *
   * @param exitCode 0、8、9、10の終了分類
   * @param diagnostics 警告または停止原因
   * @param finalDataStack 実行へ到達した場合の終了スタック
   * @param executedInstructions 実行へ到達した場合の命令数
   * @param outputBytes 正常に書かれたUTF-8バイト数
   */
  public ProgramRunResult(
      int exitCode,
      List<Diagnostic> diagnostics,
      List<RuntimeValue> finalDataStack,
      long executedInstructions,
      long outputBytes) {
    this(
        exitCode,
        diagnostics,
        finalDataStack,
        executedInstructions,
        outputBytes,
        List.of(),
        0,
        0,
        0);
  }

  /**
   * 配列予算を使わない束縛までの保存領域つき結果を作ります。
   *
   * @param exitCode 0、8、9、10の終了分類
   * @param diagnostics 警告または停止原因
   * @param finalDataStack 実行へ到達した場合の終了スタック
   * @param executedInstructions 実行へ到達した場合の命令数
   * @param outputBytes 正常に書かれたUTF-8バイト数
   * @param finalGlobalValues 実行へ到達した場合の最終大域値
   */
  public ProgramRunResult(
      int exitCode,
      List<Diagnostic> diagnostics,
      List<RuntimeValue> finalDataStack,
      long executedInstructions,
      long outputBytes,
      List<Optional<RuntimeValue>> finalGlobalValues) {
    this(
        exitCode,
        diagnostics,
        finalDataStack,
        executedInstructions,
        outputBytes,
        finalGlobalValues,
        0,
        0,
        0);
  }

  /**
   * 数値演算の配列予算までを指定する互換コンストラクタです。
   *
   * @param exitCode 0、8、9、10の終了分類
   * @param diagnostics 警告または停止原因
   * @param finalDataStack 実行へ到達した場合の終了スタック
   * @param executedInstructions 実行へ到達した場合の命令数
   * @param outputBytes 正常に書かれたUTF-8バイト数
   * @param finalGlobalValues 実行へ到達した場合の最終大域値
   * @param arrayConstructionUnits 実行へ到達した場合の配列要素構築単位
   * @param arrayElementOperationUnits 実行へ到達した場合の配列要素処理単位
   */
  public ProgramRunResult(
      int exitCode,
      List<Diagnostic> diagnostics,
      List<RuntimeValue> finalDataStack,
      long executedInstructions,
      long outputBytes,
      List<Optional<RuntimeValue>> finalGlobalValues,
      long arrayConstructionUnits,
      long arrayElementOperationUnits) {
    this(
        exitCode,
        diagnostics,
        finalDataStack,
        executedInstructions,
        outputBytes,
        finalGlobalValues,
        arrayConstructionUnits,
        arrayElementOperationUnits,
        0);
  }

  /** 文字列・正規表現までの全資源量を指定する互換コンストラクタです。 */
  public ProgramRunResult(
      int exitCode,
      List<Diagnostic> diagnostics,
      List<RuntimeValue> finalDataStack,
      long executedInstructions,
      long outputBytes,
      List<Optional<RuntimeValue>> finalGlobalValues,
      long arrayConstructionUnits,
      long arrayElementOperationUnits,
      long regexWorkUnits) {
    this(
        exitCode,
        diagnostics,
        finalDataStack,
        executedInstructions,
        outputBytes,
        finalGlobalValues,
        arrayConstructionUnits,
        arrayElementOperationUnits,
        regexWorkUnits,
        0,
        exitCode == 0
            ? ExecutionTermination.completed()
            : ExecutionTermination.diagnosticFailure());
  }

  /** 結果を不変値として保持します。 */
  public ProgramRunResult {
    if (exitCode < 0) {
      throw new IllegalArgumentException("exitCode must not be negative");
    }
    diagnostics = List.copyOf(diagnostics);
    finalDataStack = List.copyOf(finalDataStack);
    finalGlobalValues = List.copyOf(finalGlobalValues);
    Objects.requireNonNull(diagnostics, "diagnostics");
    Objects.requireNonNull(termination, "termination");
    if (arrayConstructionUnits < 0
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
        || httpFinalFailureRecords < 0
        || jsonShapeWorkUnits < 0) {
      throw new IllegalArgumentException("resource counts must not be negative");
    }
  }

  /**
   * パイプラインをエラーなしで完了したかを返します。
   *
   * @return 終了コードが0ならtrue
   */
  public boolean successful() {
    return exitCode == 0;
  }
}
