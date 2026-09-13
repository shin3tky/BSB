package jp.bsb.runtime;

import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.json.JsonLimits;
import jp.bsb.regex.RegexLimits;
import jp.bsb.stdlib.ArrayLimits;

/** 命令数、単調時間と、各値領域の構築・作業量を原子的に検査する実行予算です。 */
final class ExecutionBudget {
  private final String sourcePath;
  private final MonotonicClock clock;
  private final long startNanos;
  private long executed;
  private long arrayConstructionUnits;
  private long arrayElementOperationUnits;
  private long regexWorkUnits;
  private long jsonConstructionUnits;
  private long jsonWorkUnits;
  private long byteSequenceConstructionBytes;
  private long byteSequenceWorkBytes;
  private long httpMetadataConstructionBytes;
  private long httpSendCalls;
  private long httpRequestAttemptBytes;
  private long httpResponseReceivedBytes;
  private long fileOperations;
  private long fileReadBytes;
  private long fileWriteAttemptBytes;
  private long delimitedTextWorkUnits;
  private long excludedNanos;

  ExecutionBudget(String sourcePath, MonotonicClock clock) {
    this(sourcePath, clock, 0, clock.nanoTime(), 0, 0, 0, 0, 0, 0, 0);
  }

  ExecutionBudget(String sourcePath, MonotonicClock clock, long executed, long startNanos) {
    this(sourcePath, clock, executed, startNanos, 0, 0, 0, 0, 0, 0, 0);
  }

  /** 作業領域・区切り表の境界試験用に、ファイル累積値だけを指定して作ります。 */
  ExecutionBudget(
      String sourcePath,
      MonotonicClock clock,
      long fileOperations,
      long fileReadBytes,
      long fileWriteAttemptBytes) {
    this(sourcePath, clock);
    if (fileOperations < 0
        || fileOperations > FileLimits.MAX_OPERATIONS
        || fileReadBytes < 0
        || fileReadBytes > FileLimits.MAX_READ_BYTES
        || fileWriteAttemptBytes < 0
        || fileWriteAttemptBytes > FileLimits.MAX_WRITE_ATTEMPT_BYTES) {
      throw new IllegalArgumentException("invalid initial file resource count");
    }
    this.fileOperations = fileOperations;
    this.fileReadBytes = fileReadBytes;
    this.fileWriteAttemptBytes = fileWriteAttemptBytes;
  }

  ExecutionBudget(
      String sourcePath,
      MonotonicClock clock,
      long executed,
      long startNanos,
      long arrayConstructionUnits,
      long arrayElementOperationUnits) {
    this(
        sourcePath,
        clock,
        executed,
        startNanos,
        arrayConstructionUnits,
        arrayElementOperationUnits,
        0,
        0,
        0,
        0,
        0);
  }

  ExecutionBudget(
      String sourcePath,
      MonotonicClock clock,
      long executed,
      long startNanos,
      long arrayConstructionUnits,
      long arrayElementOperationUnits,
      long regexWorkUnits) {
    this(
        sourcePath,
        clock,
        executed,
        startNanos,
        arrayConstructionUnits,
        arrayElementOperationUnits,
        regexWorkUnits,
        0,
        0,
        0,
        0);
  }

  ExecutionBudget(
      String sourcePath,
      MonotonicClock clock,
      long executed,
      long startNanos,
      long arrayConstructionUnits,
      long arrayElementOperationUnits,
      long regexWorkUnits,
      long jsonConstructionUnits,
      long jsonWorkUnits) {
    this(
        sourcePath,
        clock,
        executed,
        startNanos,
        arrayConstructionUnits,
        arrayElementOperationUnits,
        regexWorkUnits,
        jsonConstructionUnits,
        jsonWorkUnits,
        0,
        0);
  }

  ExecutionBudget(
      String sourcePath,
      MonotonicClock clock,
      long executed,
      long startNanos,
      long arrayConstructionUnits,
      long arrayElementOperationUnits,
      long regexWorkUnits,
      long jsonConstructionUnits,
      long jsonWorkUnits,
      long byteSequenceConstructionBytes,
      long byteSequenceWorkBytes) {
    this(
        sourcePath,
        clock,
        executed,
        startNanos,
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
        0);
  }

  ExecutionBudget(
      String sourcePath,
      MonotonicClock clock,
      long executed,
      long startNanos,
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
      long httpResponseReceivedBytes) {
    this.sourcePath = sourcePath;
    this.clock = clock;
    this.executed = executed;
    this.startNanos = startNanos;
    if (arrayConstructionUnits < 0
        || arrayConstructionUnits > ArrayLimits.MAX_CONSTRUCTION_UNITS
        || arrayElementOperationUnits < 0
        || arrayElementOperationUnits > ArrayLimits.MAX_ELEMENT_OPERATION_UNITS
        || regexWorkUnits < 0
        || regexWorkUnits > RegexLimits.MAX_WORK_UNITS_PER_EXECUTION
        || jsonConstructionUnits < 0
        || jsonConstructionUnits > JsonLimits.CONSTRUCTION_UNITS
        || jsonWorkUnits < 0
        || jsonWorkUnits > JsonLimits.WORK_UNITS
        || byteSequenceConstructionBytes < 0
        || byteSequenceConstructionBytes > ByteSequenceLimits.MAX_CONSTRUCTION_BYTES
        || byteSequenceWorkBytes < 0
        || byteSequenceWorkBytes > ByteSequenceLimits.MAX_WORK_BYTES
        || httpMetadataConstructionBytes < 0
        || httpMetadataConstructionBytes > HttpLimits.MAX_METADATA_CONSTRUCTION_BYTES
        || httpSendCalls < 0
        || httpSendCalls > HttpLimits.MAX_SEND_CALLS
        || httpRequestAttemptBytes < 0
        || httpRequestAttemptBytes > HttpLimits.MAX_REQUEST_ATTEMPT_BYTES
        || httpResponseReceivedBytes < 0
        || httpResponseReceivedBytes > HttpLimits.MAX_RESPONSE_RECEIVED_BYTES) {
      throw new IllegalArgumentException("invalid initial resource count");
    }
    this.arrayConstructionUnits = arrayConstructionUnits;
    this.arrayElementOperationUnits = arrayElementOperationUnits;
    this.regexWorkUnits = regexWorkUnits;
    this.jsonConstructionUnits = jsonConstructionUnits;
    this.jsonWorkUnits = jsonWorkUnits;
    this.byteSequenceConstructionBytes = byteSequenceConstructionBytes;
    this.byteSequenceWorkBytes = byteSequenceWorkBytes;
    this.httpMetadataConstructionBytes = httpMetadataConstructionBytes;
    this.httpSendCalls = httpSendCalls;
    this.httpRequestAttemptBytes = httpRequestAttemptBytes;
    this.httpResponseReceivedBytes = httpResponseReceivedBytes;
  }

  void beforeInstruction(SourceSpan span) throws RuntimeFailure {
    if (executed >= RuntimeLimits.EXECUTED_INSTRUCTIONS) {
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_INSTRUCTION_LIMIT,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .limit("executedInstructions", RuntimeLimits.EXECUTED_INSTRUCTIONS, executed + 1)
              .expected(RuntimeLimits.EXECUTED_INSTRUCTIONS + "命令以下")
              .actual((executed + 1) + "命令目")
              .fix("無限ループまたは過大な処理を見直してください。")
              .build());
    }

    long now = clock.nanoTime();
    if (now < startNanos) {
      throw new IllegalStateException("resource clock moved backwards");
    }
    long rawElapsed;
    try {
      rawElapsed = Math.subtractExact(now, startNanos);
    } catch (ArithmeticException overflow) {
      throw new IllegalStateException("resource elapsed time overflow", overflow);
    }
    if (excludedNanos > rawElapsed) {
      throw new IllegalStateException("excluded time exceeds elapsed time");
    }
    long elapsed = rawElapsed - excludedNanos;
    if (elapsed > RuntimeLimits.ELAPSED_NANOS) {
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_EXECUTION_TIMEOUT,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .limit("elapsedNanos", RuntimeLimits.ELAPSED_NANOS, elapsed)
              .expected(RuntimeLimits.ELAPSED_NANOS + "ナノ秒以下")
              .actual(elapsed + "ナノ秒")
              .fix("終了しない処理または過大な処理を見直してください。")
              .build());
    }
    executed++;
  }

  long executed() {
    return executed;
  }

  long beginBlocking() {
    return clock.nanoTime();
  }

  void endBlocking(long startedAt) {
    long endedAt = clock.nanoTime();
    if (endedAt < startedAt) {
      throw new IllegalStateException("resource clock moved backwards during blocking operation");
    }
    try {
      long duration = Math.subtractExact(endedAt, startedAt);
      excludedNanos = Math.addExact(excludedNanos, duration);
    } catch (ArithmeticException overflow) {
      throw new IllegalStateException("excluded time overflow", overflow);
    }
  }

  /** 両方の配列予算を先に検査し、操作全体を受理できる場合だけ同時に加算します。 */
  void beforeArrayWork(
      long constructionRequested,
      long elementOperationsRequested,
      SourceSpan span,
      String operation)
      throws RuntimeFailure {
    beforeArrayAndJsonWork(
        constructionRequested, elementOperationsRequested, 0, 0, span, operation);
  }

  long arrayConstructionUnits() {
    return arrayConstructionUnits;
  }

  long arrayElementOperationUnits() {
    return arrayElementOperationUnits;
  }

  /**
   * 1呼出しと累積の正規表現照合予算を先に検査し、両方を受理できる場合だけ累積へ加算します。
   *
   * @param instructionCount BSBコンパイル命令数
   * @param inputCodePoints 入力文字列のUnicodeコードポイント数
   * @param span 呼出し位置
   * @param operation 操作語
   * @throws RuntimeFailure 1呼出しまたは累積上限を超える場合
   */
  void beforeRegexWork(
      long instructionCount, long inputCodePoints, SourceSpan span, String operation)
      throws RuntimeFailure {
    if (instructionCount < 1
        || instructionCount > RegexLimits.MAX_PROGRAM_INSTRUCTIONS
        || inputCodePoints < 0) {
      throw new IllegalArgumentException("regex work factors are outside their valid range");
    }
    long inputBoundaries = addForLimit(inputCodePoints, 1);
    long requested = multiplyForLimit(instructionCount, inputBoundaries);
    if (requested > RegexLimits.MAX_WORK_UNITS_PER_CALL) {
      throw regexLimit(
          DiagnosticCode.E_REGEX_WORK_LIMIT,
          "regexWorkUnitsPerCall",
          RegexLimits.MAX_WORK_UNITS_PER_CALL,
          requested,
          instructionCount,
          inputCodePoints,
          operation,
          span,
          "入力または正規表現の複雑さを減らしてください。");
    }
    long observed = addForLimit(regexWorkUnits, requested);
    if (observed > RegexLimits.MAX_WORK_UNITS_PER_EXECUTION) {
      throw regexLimit(
          DiagnosticCode.E_REGEX_TOTAL_WORK_LIMIT,
          "regexTotalWorkUnits",
          RegexLimits.MAX_WORK_UNITS_PER_EXECUTION,
          observed,
          instructionCount,
          inputCodePoints,
          operation,
          span,
          "1回の実行で行う正規表現照合を減らしてください。");
    }
    regexWorkUnits = observed;
  }

  long regexWorkUnits() {
    return regexWorkUnits;
  }

  /** JSON構築・作業予算を先に検査し、両方を受理できる場合だけ同時に加算します。 */
  void beforeJsonWork(
      long constructionRequested, long workRequested, SourceSpan span, String operation)
      throws RuntimeFailure {
    beforeArrayAndJsonWork(0, 0, constructionRequested, workRequested, span, operation);
  }

  /** JSON構築・作業予算を検査し、上限診断へ呼出し語を含めます。 */
  void beforeJsonWork(
      long constructionRequested,
      long workRequested,
      SourceSpan span,
      String operation,
      String word)
      throws RuntimeFailure {
    beforeArrayAndJsonWork(0, 0, constructionRequested, workRequested, span, operation, word);
  }

  /** 配列とJSONの4予算を事前検査し、複合変換を1つの原子的な予約として加算します。 */
  void beforeArrayAndJsonWork(
      long arrayConstructionRequested,
      long arrayOperationsRequested,
      long jsonConstructionRequested,
      long jsonWorkRequested,
      SourceSpan span,
      String operation)
      throws RuntimeFailure {
    beforeArrayAndJsonWork(
        arrayConstructionRequested,
        arrayOperationsRequested,
        jsonConstructionRequested,
        jsonWorkRequested,
        span,
        operation,
        null);
  }

  private void beforeArrayAndJsonWork(
      long arrayConstructionRequested,
      long arrayOperationsRequested,
      long jsonConstructionRequested,
      long jsonWorkRequested,
      SourceSpan span,
      String operation,
      String word)
      throws RuntimeFailure {
    if (arrayConstructionRequested < 0
        || arrayOperationsRequested < 0
        || jsonConstructionRequested < 0
        || jsonWorkRequested < 0) {
      throw new IllegalArgumentException("resource requests must not be negative");
    }
    long arrayConstructionObserved =
        addForLimit(arrayConstructionUnits, arrayConstructionRequested);
    if (arrayConstructionObserved > ArrayLimits.MAX_CONSTRUCTION_UNITS) {
      throw arrayLimit(
          DiagnosticCode.E_ARRAY_CONSTRUCTION_LIMIT,
          "arrayConstructionUnits",
          ArrayLimits.MAX_CONSTRUCTION_UNITS,
          arrayConstructionObserved,
          arrayConstructionRequested,
          arrayConstructionUnits,
          operation,
          span,
          "新しく配列へ格納する要素数を減らしてください。");
    }
    long arrayOperationsObserved =
        addForLimit(arrayElementOperationUnits, arrayOperationsRequested);
    if (arrayOperationsObserved > ArrayLimits.MAX_ELEMENT_OPERATION_UNITS) {
      throw arrayLimit(
          DiagnosticCode.E_ARRAY_ELEMENT_OPERATION_LIMIT,
          "arrayElementOperations",
          ArrayLimits.MAX_ELEMENT_OPERATION_UNITS,
          arrayOperationsObserved,
          arrayOperationsRequested,
          arrayElementOperationUnits,
          operation,
          span,
          "配列要素を処理する回数または要素数を減らしてください。");
    }
    long constructionObserved = addForLimit(jsonConstructionUnits, jsonConstructionRequested);
    if (constructionObserved > JsonLimits.CONSTRUCTION_UNITS) {
      throw jsonLimit(
          DiagnosticCode.E_JSON_CONSTRUCTION_LIMIT,
          "jsonConstructionUnits",
          JsonLimits.CONSTRUCTION_UNITS,
          constructionObserved,
          jsonConstructionRequested,
          jsonConstructionUnits,
          operation,
          span,
          word,
          "1回の実行で構築するJSON値を減らしてください。");
    }
    long workObserved = addForLimit(jsonWorkUnits, jsonWorkRequested);
    if (workObserved > JsonLimits.WORK_UNITS) {
      throw jsonLimit(
          DiagnosticCode.E_JSON_WORK_LIMIT,
          "jsonWorkUnits",
          JsonLimits.WORK_UNITS,
          workObserved,
          jsonWorkRequested,
          jsonWorkUnits,
          operation,
          span,
          word,
          "1回の実行で処理するJSONの量を減らしてください。");
    }
    arrayConstructionUnits = arrayConstructionObserved;
    arrayElementOperationUnits = arrayOperationsObserved;
    jsonConstructionUnits = constructionObserved;
    jsonWorkUnits = workObserved;
  }

  long jsonConstructionUnits() {
    return jsonConstructionUnits;
  }

  long jsonWorkUnits() {
    return jsonWorkUnits;
  }

  /** バイト列構築・作業予算を先に検査し、両方を受理できる場合だけ同時に加算します。 */
  void beforeByteSequenceWork(
      long constructionRequested, long workRequested, SourceSpan span, String word)
      throws RuntimeFailure {
    if (constructionRequested < 0 || workRequested < 0) {
      throw new IllegalArgumentException("byte sequence resource requests must not be negative");
    }
    long constructionObserved = addForLimit(byteSequenceConstructionBytes, constructionRequested);
    if (constructionObserved > ByteSequenceLimits.MAX_CONSTRUCTION_BYTES) {
      throw byteSequenceLimit(
          DiagnosticCode.E_BYTE_SEQUENCE_CONSTRUCTION_LIMIT,
          "byteSequenceConstructionBytes",
          ByteSequenceLimits.MAX_CONSTRUCTION_BYTES,
          byteSequenceConstructionBytes,
          constructionRequested,
          constructionObserved,
          span,
          word,
          "1回の実行で構築するバイト列を減らしてください");
    }
    long workObserved = addForLimit(byteSequenceWorkBytes, workRequested);
    if (workObserved > ByteSequenceLimits.MAX_WORK_BYTES) {
      throw byteSequenceLimit(
          DiagnosticCode.E_BYTE_SEQUENCE_WORK_LIMIT,
          "byteSequenceWorkBytes",
          ByteSequenceLimits.MAX_WORK_BYTES,
          byteSequenceWorkBytes,
          workRequested,
          workObserved,
          span,
          word,
          "1回の実行で行うバイト列変換または比較を減らしてください");
    }
    byteSequenceConstructionBytes = constructionObserved;
    byteSequenceWorkBytes = workObserved;
  }

  long byteSequenceConstructionBytes() {
    return byteSequenceConstructionBytes;
  }

  long byteSequenceWorkBytes() {
    return byteSequenceWorkBytes;
  }

  /** HTTP metadata構築予算を検査し、受理できる場合だけ加算します。 */
  void beforeHttpMetadata(long requested, SourceSpan span, String word) throws RuntimeFailure {
    if (requested < 0) {
      throw new IllegalArgumentException("HTTP metadata request must not be negative");
    }
    long observed = addForLimit(httpMetadataConstructionBytes, requested);
    if (observed > HttpLimits.MAX_METADATA_CONSTRUCTION_BYTES) {
      throw httpLimit(
          DiagnosticCode.E_HTTP_METADATA_CONSTRUCTION_LIMIT,
          "httpMetadataConstructionBytes",
          HttpLimits.MAX_METADATA_CONSTRUCTION_BYTES,
          httpMetadataConstructionBytes,
          requested,
          observed,
          span,
          word,
          "1回の実行で構築するHTTP metadataを減らしてください");
    }
    httpMetadataConstructionBytes = observed;
  }

  /** 送信回数と要求本文試行を先に検査し、両方を受理できる場合だけ同時に加算します。 */
  void beforeHttpSend(long requestBodyBytes, SourceSpan span, String word) throws RuntimeFailure {
    if (requestBodyBytes < 0) {
      throw new IllegalArgumentException("HTTP request body bytes must not be negative");
    }
    long callsObserved = addForLimit(httpSendCalls, 1);
    if (callsObserved > HttpLimits.MAX_SEND_CALLS) {
      throw httpLimit(
          DiagnosticCode.E_HTTP_SEND_LIMIT,
          "httpSendCalls",
          HttpLimits.MAX_SEND_CALLS,
          httpSendCalls,
          1,
          callsObserved,
          span,
          word,
          "1回の実行で行うHTTP送信を減らしてください");
    }
    long requestObserved = addForLimit(httpRequestAttemptBytes, requestBodyBytes);
    if (requestObserved > HttpLimits.MAX_REQUEST_ATTEMPT_BYTES) {
      throw httpLimit(
          DiagnosticCode.E_HTTP_REQUEST_TOTAL_LIMIT,
          "httpRequestAttemptBytes",
          HttpLimits.MAX_REQUEST_ATTEMPT_BYTES,
          httpRequestAttemptBytes,
          requestBodyBytes,
          requestObserved,
          span,
          word,
          "1回の実行で試行するHTTP要求本文を減らしてください");
    }
    httpSendCalls = callsObserved;
    httpRequestAttemptBytes = requestObserved;
  }

  /** 実際の応答本文読取りを加算し、超過時は累積値を上限で止めて診断にします。 */
  void afterHttpResponseBytes(long received, SourceSpan span, String word) throws RuntimeFailure {
    if (received < 0) {
      throw new IllegalArgumentException("HTTP received bytes must not be negative");
    }
    long observed = addForLimit(httpResponseReceivedBytes, received);
    if (observed > HttpLimits.MAX_RESPONSE_RECEIVED_BYTES) {
      long used = httpResponseReceivedBytes;
      httpResponseReceivedBytes = HttpLimits.MAX_RESPONSE_RECEIVED_BYTES;
      throw httpLimit(
          DiagnosticCode.E_HTTP_RESPONSE_TOTAL_LIMIT,
          "httpResponseReceivedBytes",
          HttpLimits.MAX_RESPONSE_RECEIVED_BYTES,
          used,
          received,
          observed,
          span,
          word,
          "1回の実行で受信するHTTP応答本文を減らしてください");
    }
    httpResponseReceivedBytes = observed;
  }

  /** Content-Lengthだけで全実行残量超過が確定した場合、受信量を加算せず診断にします。 */
  void rejectKnownHttpResponseBytes(long requested, SourceSpan span, String word)
      throws RuntimeFailure {
    if (requested < 0) {
      throw new IllegalArgumentException("HTTP known response bytes must not be negative");
    }
    long observed = addForLimit(httpResponseReceivedBytes, requested);
    if (observed <= HttpLimits.MAX_RESPONSE_RECEIVED_BYTES) {
      throw new IllegalStateException("known response bytes do not exceed the execution limit");
    }
    throw httpLimit(
        DiagnosticCode.E_HTTP_RESPONSE_TOTAL_LIMIT,
        "httpResponseReceivedBytes",
        HttpLimits.MAX_RESPONSE_RECEIVED_BYTES,
        httpResponseReceivedBytes,
        requested,
        observed,
        span,
        word,
        "1回の実行で受信するHTTP応答本文を減らしてください");
  }

  long httpMetadataConstructionBytes() {
    return httpMetadataConstructionBytes;
  }

  long httpSendCalls() {
    return httpSendCalls;
  }

  long httpRequestAttemptBytes() {
    return httpRequestAttemptBytes;
  }

  long httpResponseReceivedBytes() {
    return httpResponseReceivedBytes;
  }

  long httpResponseRemainingBytes() {
    return HttpLimits.MAX_RESPONSE_RECEIVED_BYTES - httpResponseReceivedBytes;
  }

  /** 読取操作数を検査し、受理できる場合だけ1回分を加算します。 */
  void beforeFileRead(SourceSpan span, String word) throws RuntimeFailure {
    long observed = addForLimit(fileOperations, 1);
    if (observed > FileLimits.MAX_OPERATIONS) {
      throw fileLimit(
          DiagnosticCode.E_FILE_OPERATION_LIMIT,
          "fileOperations",
          FileLimits.MAX_OPERATIONS,
          fileOperations,
          1,
          observed,
          span,
          word,
          "1回の実行で行うファイル操作を減らしてください");
    }
    fileOperations = observed;
  }

  /** 書込操作数と本文試行バイト数を検査し、受理できる場合だけ同時に加算します。 */
  void beforeFileWrite(long bodyBytes, SourceSpan span, String word) throws RuntimeFailure {
    if (bodyBytes < 0) throw new IllegalArgumentException("file body bytes must not be negative");
    long operationObserved = addForLimit(fileOperations, 1);
    if (operationObserved > FileLimits.MAX_OPERATIONS) {
      throw fileLimit(
          DiagnosticCode.E_FILE_OPERATION_LIMIT,
          "fileOperations",
          FileLimits.MAX_OPERATIONS,
          fileOperations,
          1,
          operationObserved,
          span,
          word,
          "1回の実行で行うファイル操作を減らしてください");
    }
    long bytesObserved = addForLimit(fileWriteAttemptBytes, bodyBytes);
    if (bytesObserved > FileLimits.MAX_WRITE_ATTEMPT_BYTES) {
      throw fileLimit(
          DiagnosticCode.E_FILE_WRITE_TOTAL_LIMIT,
          "fileWriteAttemptBytes",
          FileLimits.MAX_WRITE_ATTEMPT_BYTES,
          fileWriteAttemptBytes,
          bodyBytes,
          bytesObserved,
          span,
          word,
          "1回の実行で試行するファイル書込を減らしてください");
    }
    fileOperations = operationObserved;
    fileWriteAttemptBytes = bytesObserved;
  }

  /** 読取成功累積とバイト列構築を検査し、受理できる場合だけ同時に加算します。 */
  void afterFileRead(long bytes, SourceSpan span, String word) throws RuntimeFailure {
    if (bytes < 0) throw new IllegalArgumentException("file read bytes must not be negative");
    long readObserved = addForLimit(fileReadBytes, bytes);
    if (readObserved > FileLimits.MAX_READ_BYTES) {
      throw fileLimit(
          DiagnosticCode.E_FILE_READ_TOTAL_LIMIT,
          "fileReadBytes",
          FileLimits.MAX_READ_BYTES,
          fileReadBytes,
          bytes,
          readObserved,
          span,
          word,
          "1回の実行で読み取るファイル内容を減らしてください");
    }
    long constructionObserved = addForLimit(byteSequenceConstructionBytes, bytes);
    if (constructionObserved > ByteSequenceLimits.MAX_CONSTRUCTION_BYTES) {
      throw byteSequenceLimit(
          DiagnosticCode.E_BYTE_SEQUENCE_CONSTRUCTION_LIMIT,
          "byteSequenceConstructionBytes",
          ByteSequenceLimits.MAX_CONSTRUCTION_BYTES,
          byteSequenceConstructionBytes,
          bytes,
          constructionObserved,
          span,
          word,
          "1回の実行で構築するバイト列を減らしてください");
    }
    fileReadBytes = readObserved;
    byteSequenceConstructionBytes = constructionObserved;
  }

  long fileOperations() {
    return fileOperations;
  }

  long fileReadBytes() {
    return fileReadBytes;
  }

  long fileWriteAttemptBytes() {
    return fileWriteAttemptBytes;
  }

  /** 区切りテキスト作業予算を検査し、受理できる場合だけ加算します。 */
  void beforeDelimitedTextWork(long requested, SourceSpan span, String word) throws RuntimeFailure {
    if (requested < 0) {
      throw new IllegalArgumentException("delimited text work request must not be negative");
    }
    long observed = addForLimit(delimitedTextWorkUnits, requested);
    if (observed > DelimitedTextLimits.MAX_WORK_UNITS) {
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_DELIMITED_TEXT_WORK_LIMIT,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word)
              .field("used", Long.toString(delimitedTextWorkUnits))
              .field("requested", Long.toString(requested))
              .limit("delimitedTextWorkUnits", DelimitedTextLimits.MAX_WORK_UNITS, observed)
              .expected("累積" + DelimitedTextLimits.MAX_WORK_UNITS + "単位以下")
              .actual("累積" + observed + "単位")
              .fix("1回の実行で解析または直列化する区切りテキストを減らしてください")
              .build());
    }
    delimitedTextWorkUnits = observed;
  }

  long delimitedTextWorkUnits() {
    return delimitedTextWorkUnits;
  }

  /** 区切りテキスト資源境界試験用に、配列構築と区切り作業の累積値を指定します。 */
  static ExecutionBudget withDelimitedTextWork(
      String sourcePath,
      MonotonicClock clock,
      long arrayConstructionUnits,
      long delimitedTextWorkUnits) {
    if (delimitedTextWorkUnits < 0 || delimitedTextWorkUnits > DelimitedTextLimits.MAX_WORK_UNITS) {
      throw new IllegalArgumentException("invalid initial delimited text work count");
    }
    var result =
        new ExecutionBudget(sourcePath, clock, 0, clock.nanoTime(), arrayConstructionUnits, 0);
    result.delimitedTextWorkUnits = delimitedTextWorkUnits;
    return result;
  }

  long fileReadRemainingBytes() {
    return FileLimits.MAX_READ_BYTES - fileReadBytes;
  }

  private static long addForLimit(long used, long requested) {
    return requested > Long.MAX_VALUE - used ? Long.MAX_VALUE : used + requested;
  }

  private static long multiplyForLimit(long left, long right) {
    return left != 0 && right > Long.MAX_VALUE / left ? Long.MAX_VALUE : left * right;
  }

  private RuntimeFailure arrayLimit(
      DiagnosticCode code,
      String limitName,
      long limit,
      long observed,
      long requested,
      long usedBefore,
      String operation,
      SourceSpan span,
      String fix) {
    return new RuntimeFailure(
        Diagnostic.builder(code, Severity.ERROR, DiagnosticStage.RUNTIME, sourcePath, span)
            .field("operation", operation)
            .field("usedBefore", Long.toString(usedBefore))
            .field("requested", Long.toString(requested))
            .limit(limitName, limit, observed)
            .expected(limit + "単位以下")
            .actual(observed + "単位")
            .fix(fix)
            .build());
  }

  private RuntimeFailure regexLimit(
      DiagnosticCode code,
      String limitName,
      long limit,
      long observed,
      long instructionCount,
      long inputCodePoints,
      String operation,
      SourceSpan span,
      String fix) {
    return new RuntimeFailure(
        Diagnostic.builder(code, Severity.ERROR, DiagnosticStage.RUNTIME, sourcePath, span)
            .field("operation", operation)
            .field("instructionCount", Long.toString(instructionCount))
            .field("inputCodePoints", Long.toString(inputCodePoints))
            .field("usedBefore", Long.toString(regexWorkUnits))
            .limit(limitName, limit, observed)
            .expected(limit + "単位以下")
            .actual(observed + "単位")
            .fix(fix)
            .build());
  }

  private RuntimeFailure jsonLimit(
      DiagnosticCode code,
      String limitName,
      long limit,
      long observed,
      long requested,
      long current,
      String operation,
      SourceSpan span,
      String word,
      String fix) {
    var builder =
        Diagnostic.builder(code, Severity.ERROR, DiagnosticStage.RUNTIME, sourcePath, span)
            .field("operation", operation)
            .field("current", Long.toString(current))
            .field("requested", Long.toString(requested));
    if (word != null) {
      builder.field("word", word);
    }
    return new RuntimeFailure(
        builder
            .limit(limitName, limit, observed)
            .expected(limit + "単位以下")
            .actual(observed + "単位")
            .fix(fix)
            .build());
  }

  private RuntimeFailure byteSequenceLimit(
      DiagnosticCode code,
      String limitName,
      long limit,
      long used,
      long requested,
      long observed,
      SourceSpan span,
      String word,
      String fix) {
    return new RuntimeFailure(
        Diagnostic.builder(code, Severity.ERROR, DiagnosticStage.RUNTIME, sourcePath, span)
            .field("word", word)
            .field("used", Long.toString(used))
            .field("requested", Long.toString(requested))
            .limit(limitName, limit, observed)
            .expected("累積" + limit + "バイト以下")
            .actual("累積" + observed + "バイト")
            .fix(fix)
            .build());
  }

  private RuntimeFailure httpLimit(
      DiagnosticCode code,
      String limitName,
      long limit,
      long used,
      long requested,
      long observed,
      SourceSpan span,
      String word,
      String fix) {
    return new RuntimeFailure(
        Diagnostic.builder(code, Severity.ERROR, DiagnosticStage.RUNTIME, sourcePath, span)
            .field("word", word)
            .field("limitName", limitName)
            .field("used", Long.toString(used))
            .field("requested", Long.toString(requested))
            .field("limit", Long.toString(limit))
            .field("observed", Long.toString(observed))
            .expected("累積" + limit + "以下")
            .actual("累積" + observed)
            .fix(fix)
            .build());
  }

  private RuntimeFailure fileLimit(
      DiagnosticCode code,
      String limitName,
      long limit,
      long used,
      long requested,
      long observed,
      SourceSpan span,
      String word,
      String fix) {
    var builder =
        Diagnostic.builder(code, Severity.ERROR, DiagnosticStage.RUNTIME, sourcePath, span)
            .field("word", word)
            .field("limitName", limitName)
            .field("limit", Long.toString(limit));
    if (code == DiagnosticCode.E_FILE_OPERATION_LIMIT) {
      builder.field("observed", Long.toString(observed));
    } else {
      builder.field("used", Long.toString(used)).field("requested", Long.toString(requested));
    }
    return new RuntimeFailure(
        builder
            .limit(limitName, limit, observed)
            .expected("累積" + limit + "以下")
            .actual("累積" + observed)
            .fix(fix)
            .build());
  }
}
