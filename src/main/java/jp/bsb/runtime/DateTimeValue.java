package jp.bsb.runtime;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import jp.bsb.stdlib.ValueType;

/** 能力取得時のエポックミリ秒とUTCオフセットを保持する不変な日時値です。 */
public record DateTimeValue(long epochMilliseconds, int offsetMinutes) implements RuntimeValue {
  /** 規範範囲のオフセットと年だけを受理します。 */
  public DateTimeValue {
    OffsetDateTime value = checkedOffsetDateTime(epochMilliseconds, offsetMinutes);
    if (value.getYear() < 0 || value.getYear() > 9_999) {
      throw new IllegalArgumentException("date-time year must be between 0000 and 9999");
    }
  }

  @Override
  public ValueType type() {
    return ValueType.DATE_TIME;
  }

  @Override
  public String displayText() {
    OffsetDateTime value = checkedOffsetDateTime(epochMilliseconds, offsetMinutes);
    String offset =
        offsetMinutes == 0
            ? "Z"
            : String.format(
                Locale.ROOT,
                "%c%02d:%02d",
                offsetMinutes < 0 ? '-' : '+',
                Math.abs(offsetMinutes) / 60,
                Math.abs(offsetMinutes) % 60);
    return String.format(
        Locale.ROOT,
        "%04d-%02d-%02dT%02d:%02d:%02d.%03d%s",
        value.getYear(),
        value.getMonthValue(),
        value.getDayOfMonth(),
        value.getHour(),
        value.getMinute(),
        value.getSecond(),
        value.getNano() / 1_000_000,
        offset);
  }

  static OffsetDateTime checkedOffsetDateTime(long epochMilliseconds, int offsetMinutes) {
    if (offsetMinutes < -1_080 || offsetMinutes > 1_080) {
      throw new IllegalArgumentException("UTC offset must be between -18:00 and +18:00");
    }
    try {
      return Instant.ofEpochMilli(epochMilliseconds)
          .atOffset(ZoneOffset.ofTotalSeconds(Math.multiplyExact(offsetMinutes, 60)));
    } catch (DateTimeException | ArithmeticException failure) {
      throw new IllegalArgumentException(
          "date-time is outside the supported representation", failure);
    }
  }
}
