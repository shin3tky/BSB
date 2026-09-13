package jp.bsb.runtime;

/** time.wall能力が返す、未検証のエポックミリ秒とUTCオフセット分です。 */
public record WallTimeReading(long epochMilliseconds, int offsetMinutes) {}
