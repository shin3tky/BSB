package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.Arrays;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class ByteSequenceValueTest {
  @Test
  void copiesMutableInputAndComparesLogicalRangesByContent() {
    byte[] input = {0, 1, (byte) 0xff, 3};
    ByteSequenceValue value = ByteSequenceValue.copyOf(input);
    input[1] = 99;

    assertEquals(4, value.length());
    assertEquals(ByteSequenceValue.copyOf(new byte[] {0, 1, (byte) 0xff, 3}), value);
    assertEquals(value.hashCode(), value.slice(0, 4).hashCode());
    assertNotEquals(ByteSequenceValue.copyOf(new byte[] {0, 1, (byte) 0xfe, 3}), value);
    assertNotEquals(ByteSequenceValue.copyOf(new byte[] {0, 1, (byte) 0xff}), value);
  }

  @Test
  void slicesShareImmutableStorageWithoutExposingIt() throws Exception {
    ByteSequenceValue value = ByteSequenceValue.copyOf(new byte[] {10, 20, 30, 40});
    ByteSequenceValue slice = value.slice(1, 3);
    var storage = ByteSequenceValue.class.getDeclaredField("storage");
    storage.setAccessible(true);

    assertSame(storage.get(value), storage.get(slice));
    assertEquals(ByteSequenceValue.copyOf(new byte[] {20, 30}), slice);
    assertSame(value, value.slice(0, value.length()));
    assertSame(ByteSequenceValue.empty(), value.slice(2, 2));
    assertThrows(IndexOutOfBoundsException.class, () -> value.slice(-1, 2));
    assertThrows(IndexOutOfBoundsException.class, () -> value.slice(3, 2));
    assertThrows(IndexOutOfBoundsException.class, () -> value.slice(0, 5));
  }

  @Test
  void publicSurfaceHasNoMutableViewOrContentBearingString() {
    assertTrue(
        Arrays.stream(ByteSequenceValue.class.getDeclaredConstructors())
            .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())));
    assertTrue(
        Arrays.stream(ByteSequenceValue.class.getMethods())
            .noneMatch(
                method ->
                    method.getReturnType() == byte[].class
                        || method.getReturnType() == ByteBuffer.class));
    assertEquals(ValueType.BYTE_SEQUENCE, ByteSequenceValue.empty().type());
    assertEquals("<redacted>", ByteSequenceValue.empty().displayText());
    assertEquals("バイト列:<redacted>", ByteSequenceValue.empty().toString());
    assertFalse(ByteSequenceValue.empty().toString().contains("0"));
  }
}
