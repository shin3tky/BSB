package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class ByteSequenceFailureValueTest {
  @Test
  void utf8FailureKeepsOnlyClosedKindAndNonNegativePosition() {
    assertEquals(
        List.of(
            "invalidLeadingByte",
            "invalidContinuationByte",
            "truncatedSequence",
            "overlongEncoding",
            "surrogateCodePoint",
            "codePointOutOfRange"),
        Utf8DecodeFailureValue.KINDS);
    var value = new Utf8DecodeFailureValue("invalidContinuationByte", 7);
    assertEquals(ValueType.UTF8_DECODE_FAILURE, value.type());
    assertEquals("invalidContinuationByte", value.kind());
    assertEquals(7, value.byteOffset());
    assertEquals("UTF8復号失敗:<redacted>", value.toString());
    assertFalse(value.toString().contains("invalid"));
    assertThrows(IllegalArgumentException.class, () -> new Utf8DecodeFailureValue("other", 0));
    assertThrows(
        IllegalArgumentException.class, () -> new Utf8DecodeFailureValue("invalidLeadingByte", -1));
  }

  @Test
  void base64FailureKeepsOnlyClosedKindAndNonNegativePosition() {
    assertEquals(
        List.of("invalidCharacter", "invalidLength", "invalidPadding", "nonZeroPadBits"),
        Base64DecodeFailureValue.KINDS);
    var value = new Base64DecodeFailureValue("nonZeroPadBits", 2);
    assertEquals(ValueType.BASE64_DECODE_FAILURE, value.type());
    assertEquals("nonZeroPadBits", value.kind());
    assertEquals(2, value.characterOffset());
    assertEquals("Base64復号失敗:<redacted>", value.toString());
    assertFalse(value.toString().contains("nonZero"));
    assertThrows(IllegalArgumentException.class, () -> new Base64DecodeFailureValue("other", 0));
    assertThrows(
        IllegalArgumentException.class, () -> new Base64DecodeFailureValue("invalidCharacter", -1));
  }

  @Test
  void failureValuesDoNotDefineLanguageStyleValueEquality() {
    assertNotEquals(
        new Utf8DecodeFailureValue("invalidLeadingByte", 0),
        new Utf8DecodeFailureValue("invalidLeadingByte", 0));
    assertNotEquals(
        new Base64DecodeFailureValue("invalidLength", 2),
        new Base64DecodeFailureValue("invalidLength", 2));
  }

  @Test
  void ByteSequenceTraceAlwaysRedactsValuesAndWrapperState() {
    var bytes = ByteSequenceValue.copyOf(new byte[] {1, 2, 3});
    var failure = new Utf8DecodeFailureValue("invalidLeadingByte", 0);
    var wrapped =
        ResultValue.failure(
            jp.bsb.stdlib.ValueType.resultOf(ValueType.STRING, ValueType.UTF8_DECODE_FAILURE),
            failure);

    assertFalse(TraceValuePolicy.byteSequence().mayReveal(bytes));
    assertFalse(TraceValuePolicy.byteSequence().mayReveal(failure));
    assertFalse(TraceValuePolicy.byteSequence().mayReveal(wrapped));
    assertEquals(
        "バイト列:<redacted>", TraceValueFormatter.format(bytes, TraceValuePolicy.byteSequence()));
    assertEquals(
        "結果<文字列,UTF8復号失敗>:<redacted>",
        TraceValueFormatter.format(wrapped, TraceValuePolicy.byteSequence()));
    assertEquals("<redacted>", wrapped.displayText());
    assertEquals("<redacted>", wrapped.toString());

    var optionalBytes = OptionalValue.present(bytes);
    assertEquals(
        "任意<バイト列>:<redacted>",
        TraceValueFormatter.format(optionalBytes, TraceValuePolicy.byteSequence()));
    assertEquals("<redacted>", optionalBytes.toString());
  }
}
