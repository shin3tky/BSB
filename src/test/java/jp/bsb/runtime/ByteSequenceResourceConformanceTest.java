package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jp.bsb.conformance.ByteSequenceConformanceData;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.BuiltinDictionary;
import org.junit.jupiter.api.Test;

/** BYTES-R001〜010の巨大候補を512 MiB内で構築前検査または共有viewにより検証します。 */
class ByteSequenceResourceConformanceTest {
  private static final String SOURCE_PATH = "ByteSequence-resource.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void independentRowsUseEveryFixedLimitAndTheConfiguredHeap() throws Exception {
    var rows = ByteSequenceConformanceData.loadResources();

    assertEquals(20, rows.size());
    assertTrue(hasLimit(rows, "byteSequenceBytes", ByteSequenceLimits.MAX_VALUE_BYTES));
    assertTrue(
        hasLimit(rows, "byteSequenceConstructionBytes", ByteSequenceLimits.MAX_CONSTRUCTION_BYTES));
    assertTrue(hasLimit(rows, "byteSequenceWorkBytes", ByteSequenceLimits.MAX_WORK_BYTES));
    assertTrue(hasLimit(rows, "stringUtf8Bytes", StringLimits.MAX_UTF8_BYTES));
    assertTrue(Runtime.getRuntime().maxMemory() <= 512L * 1024 * 1024);
  }

  @Test
  void oneValueAcceptsExactly64MiBAndRejectsOneMoreAsAnInternalInvariant() {
    ByteSequenceValue maximum =
        ByteSequenceValue.takeOwnership(new byte[ByteSequenceLimits.MAX_VALUE_BYTES]);
    assertEquals(ByteSequenceLimits.MAX_VALUE_BYTES, maximum.length());

    byte[] excessive = new byte[ByteSequenceLimits.MAX_VALUE_BYTES + 1];
    assertThrows(IllegalArgumentException.class, () -> ByteSequenceValue.takeOwnership(excessive));
  }

  @Test
  void constructionAndWorkAcceptTheirExactCumulativeBoundariesAndRejectOneMoreAtomically()
      throws Exception {
    var exact = budget(0, 0);
    exact.beforeByteSequenceWork(
        ByteSequenceLimits.MAX_CONSTRUCTION_BYTES, ByteSequenceLimits.MAX_WORK_BYTES, SPAN, "境界語");
    assertEquals(ByteSequenceLimits.MAX_CONSTRUCTION_BYTES, exact.byteSequenceConstructionBytes());
    assertEquals(ByteSequenceLimits.MAX_WORK_BYTES, exact.byteSequenceWorkBytes());

    RuntimeFailure construction =
        assertThrows(
            RuntimeFailure.class,
            () -> exact.beforeByteSequenceWork(1, 0, SPAN, "文字列をUTF8バイト列に変換する"));
    assertResourceDiagnostic(
        construction.diagnostic(),
        DiagnosticCode.E_BYTE_SEQUENCE_CONSTRUCTION_LIMIT,
        "byteSequenceConstructionBytes",
        ByteSequenceLimits.MAX_CONSTRUCTION_BYTES,
        1,
        ByteSequenceLimits.MAX_CONSTRUCTION_BYTES + 1,
        "文字列をUTF8バイト列に変換する");

    RuntimeFailure work =
        assertThrows(
            RuntimeFailure.class, () -> exact.beforeByteSequenceWork(0, 1, SPAN, "バイト列の長さ"));
    assertResourceDiagnostic(
        work.diagnostic(),
        DiagnosticCode.E_BYTE_SEQUENCE_WORK_LIMIT,
        "byteSequenceWorkBytes",
        ByteSequenceLimits.MAX_WORK_BYTES,
        1,
        ByteSequenceLimits.MAX_WORK_BYTES + 1,
        "バイト列の長さ");

    var atomic =
        budget(ByteSequenceLimits.MAX_CONSTRUCTION_BYTES - 1, ByteSequenceLimits.MAX_WORK_BYTES);
    assertThrows(RuntimeFailure.class, () -> atomic.beforeByteSequenceWork(1, 1, SPAN, "原子的予約"));
    assertEquals(
        ByteSequenceLimits.MAX_CONSTRUCTION_BYTES - 1, atomic.byteSequenceConstructionBytes());
    assertEquals(ByteSequenceLimits.MAX_WORK_BYTES, atomic.byteSequenceWorkBytes());
  }

  @Test
  void utf8EncodeRejectsA64MiBPlusOneResultBeforeAllocationAndKeepsState() {
    StringValue input = new StringValue("a".repeat(ByteSequenceLimits.MAX_VALUE_BYTES + 1));
    var values = stack(input);
    var operationBudget = budget(0, 0);

    RuntimeFailure failure =
        assertThrows(
            RuntimeFailure.class, () -> execute("文字列をUTF8バイト列に変換する", values, operationBudget));

    Diagnostic diagnostic = failure.diagnostic();
    assertEquals(DiagnosticCode.E_BYTE_SEQUENCE_SIZE_LIMIT, diagnostic.code());
    assertEquals(
        Map.of("word", "文字列をUTF8バイト列に変換する", "operation", "utf8Encode"), diagnostic.fields());
    assertEquals("byteSequenceBytes", diagnostic.limitName().orElseThrow());
    assertEquals("67108864", diagnostic.limit().orElseThrow());
    assertEquals("67108865", diagnostic.observed().orElseThrow());
    assertSame(input, values.getFirst());
    assertEquals(0, operationBudget.byteSequenceConstructionBytes());
    assertEquals(0, operationBudget.byteSequenceWorkBytes());
  }

  @Test
  void base64OutputAcceptsExactly16MiBAndRejectsTheNextQuartetBeforeWork() throws Exception {
    int exactInputLength = 12_582_912;
    var exactBudget = budget(0, 0);
    var exact = stack(ByteSequenceValue.takeOwnership(new byte[exactInputLength]));

    execute("バイト列をBase64文字列に変換する", exact, exactBudget);

    assertEquals(StringLimits.MAX_UTF8_BYTES, ((StringValue) exact.getFirst()).value().length());
    assertEquals(29_360_128, exactBudget.byteSequenceWorkBytes());
    assertEquals(0, exactBudget.byteSequenceConstructionBytes());

    ByteSequenceValue overInput = ByteSequenceValue.takeOwnership(new byte[exactInputLength + 1]);
    var over = stack(overInput);
    var overBudget = budget(0, 0);
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("バイト列をBase64文字列に変換する", over, overBudget));
    assertEquals(DiagnosticCode.E_STRING_UTF8_LIMIT, failure.diagnostic().code());
    assertEquals("16777220", failure.diagnostic().observed().orElseThrow());
    assertSame(overInput, over.getFirst());
    assertEquals(0, overBudget.byteSequenceWorkBytes());
  }

  @Test
  void utf8OutputAcceptsExactly16MiBAndOverLimitKeepsInputAfterChargingScan() throws Exception {
    int exactLength = StringLimits.MAX_UTF8_BYTES;
    var exactBudget = budget(0, 0);
    var exact = stack(asciiBytes(exactLength));
    execute("バイト列をUTF8文字列に変換して結果を返す", exact, exactBudget);
    ResultValue result = (ResultValue) exact.getFirst();
    assertTrue(result.isSuccess());
    assertEquals(exactLength, ((StringValue) result.value()).value().length());
    assertEquals(exactLength, exactBudget.byteSequenceWorkBytes());

    ByteSequenceValue overInput = asciiBytes(exactLength + 1);
    var over = stack(overInput);
    var overBudget = budget(0, 0);
    RuntimeFailure failure =
        assertThrows(
            RuntimeFailure.class, () -> execute("バイト列をUTF8文字列に変換して結果を返す", over, overBudget));
    assertEquals(DiagnosticCode.E_STRING_UTF8_LIMIT, failure.diagnostic().code());
    assertSame(overInput, over.getFirst());
    assertEquals(exactLength + 1L, overBudget.byteSequenceWorkBytes());
  }

  @Test
  void full64MiBSliceAndEqualityUseViewsAndExactWorkWithoutExtraConstruction() throws Exception {
    ByteSequenceValue maximum =
        ByteSequenceValue.takeOwnership(new byte[ByteSequenceLimits.MAX_VALUE_BYTES]);

    var sliceBudget = budget(0, 0);
    var slice =
        stack(
            maximum,
            new IntegerValue(java.math.BigInteger.ZERO),
            new IntegerValue(java.math.BigInteger.valueOf(ByteSequenceLimits.MAX_VALUE_BYTES)));
    execute("バイト列の一部を取り出す", slice, sliceBudget);
    assertSame(maximum, slice.getFirst());
    assertEquals(1, sliceBudget.byteSequenceWorkBytes());

    var equalBudget = budget(0, 0);
    var equal = stack(maximum, maximum);
    execute("等しい", equal, equalBudget);
    assertEquals(List.of(new BooleanValue(true)), equal);
    assertEquals(ByteSequenceLimits.MAX_VALUE_BYTES, equalBudget.byteSequenceWorkBytes());

    var differentLengthBudget = budget(0, 0);
    var differentLength = stack(maximum, maximum.slice(0, maximum.length() - 1));
    execute("等しい", differentLength, differentLengthBudget);
    assertEquals(List.of(new BooleanValue(false)), differentLength);
    assertEquals(0, differentLengthBudget.byteSequenceWorkBytes());
    assertEquals(0, equalBudget.byteSequenceConstructionBytes());
  }

  @Test
  void invalidUtf8AndBase64At16MiBChargeTheWholeInputAndReturnFailure() throws Exception {
    byte[] invalidUtf8Bytes = new byte[StringLimits.MAX_UTF8_BYTES];
    java.util.Arrays.fill(invalidUtf8Bytes, (byte) 'a');
    invalidUtf8Bytes[invalidUtf8Bytes.length - 1] = (byte) 0x80;
    var utf8Budget = budget(0, 0);
    var utf8 = stack(ByteSequenceValue.takeOwnership(invalidUtf8Bytes));
    execute("バイト列をUTF8文字列に変換して結果を返す", utf8, utf8Budget);
    assertTrue(((ResultValue) utf8.getFirst()).isFailure());
    assertEquals(StringLimits.MAX_UTF8_BYTES, utf8Budget.byteSequenceWorkBytes());

    String invalidBase64 = "A".repeat(StringLimits.MAX_UTF8_BYTES - 1) + " ";
    var base64Budget = budget(0, 0);
    var base64 = stack(new StringValue(invalidBase64));
    execute("Base64文字列をバイト列に変換して結果を返す", base64, base64Budget);
    assertTrue(((ResultValue) base64.getFirst()).isFailure());
    assertEquals(StringLimits.MAX_UTF8_BYTES, base64Budget.byteSequenceWorkBytes());
  }

  @Test
  void base64SecondReservationRejectsConstructionOrWorkWithoutPartialCommit() throws Exception {
    var constructionBudget = budget(ByteSequenceLimits.MAX_CONSTRUCTION_BYTES - 1, 0);
    StringValue input = new StringValue("Zm9v");
    var construction = stack(input);
    RuntimeFailure constructionFailure =
        assertThrows(
            RuntimeFailure.class,
            () -> execute("Base64文字列をバイト列に変換して結果を返す", construction, constructionBudget));
    assertEquals(
        DiagnosticCode.E_BYTE_SEQUENCE_CONSTRUCTION_LIMIT, constructionFailure.diagnostic().code());
    assertSame(input, construction.getFirst());
    assertEquals(
        ByteSequenceLimits.MAX_CONSTRUCTION_BYTES - 1,
        constructionBudget.byteSequenceConstructionBytes());
    assertEquals(4, constructionBudget.byteSequenceWorkBytes());

    var workBudget = budget(0, ByteSequenceLimits.MAX_WORK_BYTES - 6);
    var work = stack(input);
    RuntimeFailure workFailure =
        assertThrows(
            RuntimeFailure.class, () -> execute("Base64文字列をバイト列に変換して結果を返す", work, workBudget));
    assertEquals(DiagnosticCode.E_BYTE_SEQUENCE_WORK_LIMIT, workFailure.diagnostic().code());
    assertSame(input, work.getFirst());
    assertEquals(0, workBudget.byteSequenceConstructionBytes());
    assertEquals(ByteSequenceLimits.MAX_WORK_BYTES - 2, workBudget.byteSequenceWorkBytes());
  }

  private static void assertResourceDiagnostic(
      Diagnostic diagnostic,
      DiagnosticCode code,
      String limitName,
      long used,
      long requested,
      long observed,
      String word) {
    assertEquals(code, diagnostic.code());
    assertEquals(
        Map.of("word", word, "used", Long.toString(used), "requested", Long.toString(requested)),
        diagnostic.fields());
    assertEquals(limitName, diagnostic.limitName().orElseThrow());
    assertEquals(Long.toString(observed), diagnostic.observed().orElseThrow());
    assertEquals("累積" + observed + "バイト", diagnostic.actual().orElseThrow());
  }

  private static boolean hasLimit(
      List<ByteSequenceConformanceData.ResourceSpec> rows, String metric, long limit) {
    return rows.stream()
        .anyMatch(
            row ->
                row.fields().get("metric").equals(metric)
                    && Long.parseLong(row.fields().get("limit")) == limit);
  }

  private static ByteSequenceValue asciiBytes(int length) {
    byte[] bytes = new byte[length];
    java.util.Arrays.fill(bytes, (byte) 'a');
    return ByteSequenceValue.takeOwnership(bytes);
  }

  private static ExecutionBudget budget(long construction, long work) {
    return new ExecutionBudget(SOURCE_PATH, () -> 0L, 0, 0, 0, 0, 0, 0, 0, construction, work);
  }

  private static ArrayList<RuntimeValue> stack(RuntimeValue... values) {
    return new ArrayList<>(List.of(values));
  }

  private static void execute(
      String name, ArrayList<RuntimeValue> stack, ExecutionBudget operationBudget)
      throws RuntimeFailure {
    new BuiltinExecutor(
            SOURCE_PATH, new BoundedOutput(SOURCE_PATH, new MemoryOutputSink()), operationBudget)
        .execute(BuiltinDictionary.find(name).orElseThrow(), stack, SPAN);
  }
}
