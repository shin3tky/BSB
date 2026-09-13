package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import jp.bsb.conformance.ByteSequenceConformanceData;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ResultType;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class ByteSequenceByteSequenceRuntimeTest {
  private static final String SOURCE_PATH = "ByteSequence-byte-runtime.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));
  private static final HexFormat HEX = HexFormat.of();

  @Test
  void lengthAndEveryIndependentSliceUseOneWorkByteAndPreserveTheSource() throws Exception {
    var lengthBudget = budget();
    var length = stack(ByteSequenceValue.copyOf(new byte[] {0, 1, 2, (byte) 0xff}));
    execute("バイト列の長さ", length, lengthBudget);
    assertEquals(List.of(new IntegerValue(BigInteger.valueOf(4))), length);
    assertEquals(1, lengthBudget.byteSequenceWorkBytes());

    for (var spec : ByteSequenceConformanceData.loadSlices()) {
      var fields = spec.fields();
      ByteSequenceValue original = ByteSequenceValue.copyOf(HEX.parseHex(fields.get("input_hex")));
      var values =
          stack(
              original,
              new IntegerValue(new BigInteger(fields.get("start"))),
              new IntegerValue(new BigInteger(fields.get("end"))));
      List<RuntimeValue> before = List.copyOf(values);
      var operationBudget = budget();

      if (fields.get("outcome").equals("success")) {
        execute("バイト列の一部を取り出す", values, operationBudget);
        assertEquals(1, values.size(), fields.get("variant"));
        assertArrayEquals(
            HEX.parseHex(fields.get("output_hex")),
            ((ByteSequenceValue) values.getFirst()).copyBytes(),
            fields.get("variant"));
        assertEquals(Long.parseLong(fields.get("work")), operationBudget.byteSequenceWorkBytes());
        assertEquals(ByteSequenceValue.copyOf(HEX.parseHex(fields.get("input_hex"))), original);
      } else {
        RuntimeFailure failure =
            assertThrows(
                RuntimeFailure.class,
                () -> execute("バイト列の一部を取り出す", values, operationBudget),
                fields.get("variant"));
        assertEquals(
            DiagnosticCode.E_BYTE_SEQUENCE_RANGE_OUT_OF_BOUNDS, failure.diagnostic().code());
        assertEquals(before, values, fields.get("variant"));
        assertEquals(0, operationBudget.byteSequenceWorkBytes());
      }
    }
  }

  @Test
  void rangeDiagnosticMatchesTheIndependentFieldsAndDisclosesNoBytes() {
    ByteSequenceValue bytes = ByteSequenceValue.copyOf(new byte[] {0, 1, 2, (byte) 0xff});
    var values =
        stack(
            bytes,
            new IntegerValue(BigInteger.valueOf(-1)),
            new IntegerValue(BigInteger.valueOf(2)));

    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("バイト列の一部を取り出す", values, budget()));

    var diagnostic = failure.diagnostic();
    assertEquals(
        java.util.Map.of(
            "word",
            "バイト列の一部を取り出す",
            "unit",
            "byte",
            "start",
            "-1",
            "end",
            "2",
            "length",
            "4",
            "validRange",
            "0 <= start <= end <= length"),
        diagnostic.fields());
    assertEquals("0 <= 開始 <= 終了 <= 4", diagnostic.expected().orElseThrow());
    assertEquals("開始-1、終了2", diagnostic.actual().orElseThrow());
    assertFalse(diagnostic.toString().contains("000102"));
    assertSame(bytes, values.getFirst());
  }

  @Test
  void everyIndependentEqualityCaseChargesOnlyComparedBytes() throws Exception {
    for (var spec : ByteSequenceConformanceData.loadEquality()) {
      var fields = spec.fields();
      RuntimeValue left = ByteSequenceValue.copyOf(HEX.parseHex(fields.get("left_hex")));
      RuntimeValue right = ByteSequenceValue.copyOf(HEX.parseHex(fields.get("right_hex")));
      if (fields.get("wrapper").equals("present")) {
        left = OptionalValue.present(left);
        right = OptionalValue.present(right);
      } else if (fields.get("wrapper").equals("result-state")) {
        ResultType type = ValueType.resultOf(ValueType.BYTE_SEQUENCE, ValueType.BYTE_SEQUENCE);
        left = ResultValue.success(type, left);
        right = ResultValue.failure(type, right);
      }
      var values = stack(left, right);
      var operationBudget = budget();

      execute("等しい", values, operationBudget);

      assertEquals(
          List.of(new BooleanValue(Boolean.parseBoolean(fields.get("result")))),
          values,
          fields.get("variant"));
      assertEquals(
          Long.parseLong(fields.get("compared_bytes")),
          operationBudget.byteSequenceWorkBytes(),
          fields.get("variant"));
      assertEquals(0, operationBudget.byteSequenceConstructionBytes());
    }
  }

  @Test
  void rejectedIncrementalEqualityKeepsBothInputsAndAcceptedWork() {
    ByteSequenceValue first = ByteSequenceValue.copyOf(new byte[] {1, 2});
    ByteSequenceValue second = ByteSequenceValue.copyOf(new byte[] {1, 2});
    var values = stack(first, second);
    var operationBudget = budget(0, ByteSequenceLimits.MAX_WORK_BYTES - 1);

    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("等しい", values, operationBudget));

    assertEquals(DiagnosticCode.E_BYTE_SEQUENCE_WORK_LIMIT, failure.diagnostic().code());
    assertEquals(List.of(first, second), values);
    assertEquals(ByteSequenceLimits.MAX_WORK_BYTES, operationBudget.byteSequenceWorkBytes());
  }

  @Test
  void conversionBudgetsFollowTheNormativeTableAndFailuresStillConsumeInputScan() throws Exception {
    var utf8EncodeBudget = budget();
    var utf8Encoded = stack(new StringValue("日本語"));
    execute("文字列をUTF8バイト列に変換する", utf8Encoded, utf8EncodeBudget);
    assertEquals(9, utf8EncodeBudget.byteSequenceConstructionBytes());
    assertEquals(9, utf8EncodeBudget.byteSequenceWorkBytes());

    var utf8DecodeBudget = budget();
    var utf8Decoded = stack(utf8Encoded.getFirst());
    execute("バイト列をUTF8文字列に変換して結果を返す", utf8Decoded, utf8DecodeBudget);
    assertTrue(((ResultValue) utf8Decoded.getFirst()).isSuccess());
    assertEquals(0, utf8DecodeBudget.byteSequenceConstructionBytes());
    assertEquals(9, utf8DecodeBudget.byteSequenceWorkBytes());

    var invalidUtf8Budget = budget();
    var invalidUtf8 = stack(ByteSequenceValue.copyOf(new byte[] {(byte) 0x80}));
    execute("バイト列をUTF8文字列に変換して結果を返す", invalidUtf8, invalidUtf8Budget);
    assertTrue(((ResultValue) invalidUtf8.getFirst()).isFailure());
    assertEquals(1, invalidUtf8Budget.byteSequenceWorkBytes());

    var base64EncodeBudget = budget();
    var base64Encoded =
        stack(ByteSequenceValue.copyOf("foobar".getBytes(StandardCharsets.US_ASCII)));
    execute("バイト列をBase64文字列に変換する", base64Encoded, base64EncodeBudget);
    assertEquals(List.of(new StringValue("Zm9vYmFy")), base64Encoded);
    assertEquals(0, base64EncodeBudget.byteSequenceConstructionBytes());
    assertEquals(14, base64EncodeBudget.byteSequenceWorkBytes());

    var base64DecodeBudget = budget();
    var base64Decoded = stack(new StringValue("Zm9vYmFy"));
    execute("Base64文字列をバイト列に変換して結果を返す", base64Decoded, base64DecodeBudget);
    assertTrue(((ResultValue) base64Decoded.getFirst()).isSuccess());
    assertEquals(6, base64DecodeBudget.byteSequenceConstructionBytes());
    assertEquals(14, base64DecodeBudget.byteSequenceWorkBytes());

    var invalidBase64Budget = budget();
    var invalidBase64 = stack(new StringValue("Z g=="));
    execute("Base64文字列をバイト列に変換して結果を返す", invalidBase64, invalidBase64Budget);
    assertTrue(((ResultValue) invalidBase64.getFirst()).isFailure());
    assertEquals(5, invalidBase64Budget.byteSequenceWorkBytes());
    assertEquals(0, invalidBase64Budget.byteSequenceConstructionBytes());
  }

  @Test
  void tracingChangesNeitherResultsNorBudgetsAndAlwaysRedactsByteSequenceValues() {
    String source =
        "メインとは （--）\n" + "    「SECRET_BYTES」を 文字列をUTF8バイト列に変換する " + "バイト列の長さ 一行表示する\n" + "こと。\n";
    Run normal = run(source, false);
    Run traced = run(source, true);

    assertTrue(normal.result().successful(), normal.result().diagnostics().toString());
    assertTrue(traced.result().successful(), traced.result().diagnostics().toString());
    assertEquals(normal.output().utf8Text(), traced.output().utf8Text());
    assertEquals(normal.result().finalDataStack(), traced.result().finalDataStack());
    assertEquals(normal.result().executedInstructions(), traced.result().executedInstructions());
    assertEquals(
        normal.result().byteSequenceConstructionBytes(),
        traced.result().byteSequenceConstructionBytes());
    assertEquals(normal.result().byteSequenceWorkBytes(), traced.result().byteSequenceWorkBytes());
    String trace = TraceTsvFormatter.formatByteSequence(traced.events());
    assertTrue(trace.contains("バイト列:<redacted>"));
    assertFalse(trace.contains("SECRET_BYTES"));
  }

  @Test
  void diagnosticsRedactByteSequenceValuesAndSensitiveConversionInputs() {
    Diagnostic diagnostic =
        Diagnostic.builder(
                DiagnosticCode.E_BYTE_SEQUENCE_CONSTRUCTION_LIMIT,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                SOURCE_PATH,
                SPAN)
            .field("word", "Base64文字列をバイト列に変換して結果を返す")
            .build();
    assertEquals(
        "[文字列:<redacted>]",
        Interpreter.safeDataStack(diagnostic, List.of(new StringValue("SECRET_BASE64_INPUT"))));

    ByteSequenceValue bytes = ByteSequenceValue.copyOf(new byte[] {0, 1, 2, (byte) 0xff});
    String safeValues =
        Interpreter.safeDataStack(
            diagnostic,
            List.of(
                bytes,
                new Utf8DecodeFailureValue("invalidLeadingByte", 0),
                OptionalValue.present(bytes)));
    assertEquals("[バイト列:<redacted>, UTF8復号失敗:<redacted>, 任意<バイト列>:<redacted>]", safeValues);
    assertFalse(safeValues.contains("000102"));
    assertFalse(safeValues.contains("invalidLeadingByte"));
  }

  private static Run run(String source, boolean tracing) {
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                SOURCE_PATH,
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, tracing ? events::add : TraceSink.none()));
    return new Run(result, output, List.copyOf(events));
  }

  private static ExecutionBudget budget() {
    return budget(0, 0);
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

  private record Run(ProgramRunResult result, MemoryOutputSink output, List<TraceEvent> events) {}
}
