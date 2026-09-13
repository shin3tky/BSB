package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import jp.bsb.conformance.ByteSequenceConformanceData;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.json.JsonCodec;
import jp.bsb.json.JsonString;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class ByteSequenceCodecRuntimeTest {
  private static final String SOURCE_PATH = "ByteSequence-codec-runtime.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));
  private static final HexFormat HEX = HexFormat.of();

  @Test
  void everyIndependentUtf8VectorRoundTripsWithoutBomRemovalOrNormalization() throws Exception {
    for (var vector : ByteSequenceConformanceData.loadUtf8Vectors()) {
      String text = ((JsonString) JsonCodec.parse(vector.textJson())).value();
      byte[] expectedBytes = HEX.parseHex(vector.utf8Hex());

      var encoded = stack(new StringValue(text));
      execute("文字列をUTF8バイト列に変換する", encoded);
      assertArrayEquals(
          expectedBytes, ((ByteSequenceValue) encoded.getFirst()).copyBytes(), vector.vector());

      var decoded = stack(ByteSequenceValue.copyOf(expectedBytes));
      execute("バイト列をUTF8文字列に変換して結果を返す", decoded);
      ResultValue result = (ResultValue) decoded.getFirst();
      assertTrue(result.isSuccess(), vector.vector());
      assertEquals(
          ValueType.resultOf(ValueType.STRING, ValueType.UTF8_DECODE_FAILURE), result.type());
      assertEquals(new StringValue(text), result.value(), vector.vector());
    }
  }

  @Test
  void everyIndependentUtf8FailureUsesTheFirstNormativeKindAndByteOffset() throws Exception {
    for (var expected : ByteSequenceConformanceData.loadUtf8Failures()) {
      byte[] input = HEX.parseHex(expected.inputHex());
      var stack = stack(ByteSequenceValue.copyOf(input));
      execute("バイト列をUTF8文字列に変換して結果を返す", stack);

      ResultValue result = (ResultValue) stack.getFirst();
      assertTrue(result.isFailure(), expected.variant());
      var failure = (Utf8DecodeFailureValue) result.value();
      assertEquals(expected.kind(), failure.kind(), expected.variant());
      assertEquals(expected.offset(), failure.byteOffset(), expected.variant());
      assertEquals(expected.offset(), StrictUtf8.firstInvalidOffset(input).orElseThrow());
      assertUtf8Accessors(failure);
    }
  }

  @Test
  void everyIndependentBase64VectorUsesCanonicalPaddingAndAlphabet() throws Exception {
    for (var vector : ByteSequenceConformanceData.loadBase64Vectors()) {
      byte[] bytes = bytes(vector.inputHex());
      String encodedText = vector.encoded().equals("-") ? "" : vector.encoded();

      var encoded = stack(ByteSequenceValue.copyOf(bytes));
      execute("バイト列をBase64文字列に変換する", encoded);
      assertEquals(new StringValue(encodedText), encoded.getFirst(), vector.vector());

      var decoded = stack(new StringValue(encodedText));
      execute("Base64文字列をバイト列に変換して結果を返す", decoded);
      ResultValue result = (ResultValue) decoded.getFirst();
      assertTrue(result.isSuccess(), vector.vector());
      assertEquals(
          ValueType.resultOf(ValueType.BYTE_SEQUENCE, ValueType.BASE64_DECODE_FAILURE),
          result.type());
      assertArrayEquals(bytes, ((ByteSequenceValue) result.value()).copyBytes(), vector.vector());
    }
  }

  @Test
  void everyIndependentBase64FailureUsesScalarPositionsAndNormativePriority() throws Exception {
    for (var expected : ByteSequenceConformanceData.loadBase64Failures()) {
      String text = new String(HEX.parseHex(expected.inputUtf8Hex()), StandardCharsets.UTF_8);
      var stack = stack(new StringValue(text));
      execute("Base64文字列をバイト列に変換して結果を返す", stack);

      ResultValue result = (ResultValue) stack.getFirst();
      assertTrue(result.isFailure(), expected.variant());
      var failure = (Base64DecodeFailureValue) result.value();
      assertEquals(expected.kind(), failure.kind(), expected.variant());
      assertEquals(expected.position(), failure.characterOffset(), expected.variant());
      assertBase64Accessors(failure);
    }
  }

  @Test
  void publicWordsComposeAsOneToOneReplacementsInAProgram() {
    String source =
        "メインとは （--）\n"
            + "    「日本語」を 文字列をUTF8バイト列に変換する "
            + "バイト列をUTF8文字列に変換して結果を返す "
            + "結果から成功値を取り出す 一行表示する\n"
            + "    「Z g==」を Base64文字列をバイト列に変換して結果を返す "
            + "結果から失敗値を取り出す Base64復号失敗の種類を取り出す 一行表示する\n"
            + "    「Z g==」を Base64文字列をバイト列に変換して結果を返す "
            + "結果から失敗値を取り出す Base64復号失敗の文字位置を取り出す 一行表示する\n"
            + "    空のバイト列 バイト列をBase64文字列に変換する 一行表示する\n"
            + "こと。\n";
    var output = new MemoryOutputSink();

    ProgramRunResult result =
        new ProgramRunner()
            .run(
                SOURCE_PATH,
                source.getBytes(StandardCharsets.UTF_8),
                new ExecutionContext(output, () -> 0L, TraceSink.none()));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals("日本語\ninvalidCharacter\n1\n\n", output.utf8Text());
    assertTrue(result.finalDataStack().isEmpty());
  }

  @Test
  void validatorKindsAndFailurePayloadFieldsRemainClosedAndInputFree() {
    assertEquals(
        Utf8DecodeFailureValue.KINDS,
        Arrays.stream(StrictUtf8.FailureKind.values())
            .map(StrictUtf8.FailureKind::stableName)
            .toList());
    assertEquals(
        Base64DecodeFailureValue.KINDS,
        Arrays.stream(StrictBase64.FailureKind.values())
            .map(StrictBase64.FailureKind::stableName)
            .toList());
    assertEquals(Set.of("kind", "byteOffset"), instanceFieldNames(Utf8DecodeFailureValue.class));
    assertEquals(
        Set.of("kind", "characterOffset"), instanceFieldNames(Base64DecodeFailureValue.class));
  }

  private static void assertUtf8Accessors(Utf8DecodeFailureValue failure) throws RuntimeFailure {
    var kind = stack(failure);
    execute("UTF8復号失敗の種類を取り出す", kind);
    assertEquals(List.of(new StringValue(failure.kind())), kind);

    var position = stack(failure);
    execute("UTF8復号失敗のバイト位置を取り出す", position);
    assertEquals(List.of(new IntegerValue(BigInteger.valueOf(failure.byteOffset()))), position);
  }

  private static void assertBase64Accessors(Base64DecodeFailureValue failure)
      throws RuntimeFailure {
    var kind = stack(failure);
    execute("Base64復号失敗の種類を取り出す", kind);
    assertEquals(List.of(new StringValue(failure.kind())), kind);

    var position = stack(failure);
    execute("Base64復号失敗の文字位置を取り出す", position);
    assertEquals(
        List.of(new IntegerValue(BigInteger.valueOf(failure.characterOffset()))), position);
  }

  private static byte[] bytes(String hex) {
    if (hex.equals("-")) {
      return new byte[0];
    }
    if (hex.equals("recipe:bytes-00-ff")) {
      byte[] bytes = new byte[256];
      for (int index = 0; index < bytes.length; index++) {
        bytes[index] = (byte) index;
      }
      return bytes;
    }
    return HEX.parseHex(hex);
  }

  private static Set<String> instanceFieldNames(Class<?> type) {
    return Arrays.stream(type.getDeclaredFields())
        .filter(field -> !Modifier.isStatic(field.getModifiers()))
        .map(java.lang.reflect.Field::getName)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static ArrayList<RuntimeValue> stack(RuntimeValue value) {
    return new ArrayList<>(List.of(value));
  }

  private static void execute(String name, ArrayList<RuntimeValue> stack) throws RuntimeFailure {
    new BuiltinExecutor(
            SOURCE_PATH,
            new BoundedOutput(SOURCE_PATH, new MemoryOutputSink()),
            new ExecutionBudget(SOURCE_PATH, () -> 0L))
        .execute(BuiltinDictionary.find(name).orElseThrow(), stack, SPAN);
  }
}
