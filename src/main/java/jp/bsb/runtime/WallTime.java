package jp.bsb.runtime;

/** 現在時刻と明示的なUTCオフセットを取得する能力です。 */
@FunctionalInterface
public interface WallTime {
  /** 呼出し時点の時刻情報を返します。 */
  WallTimeReading now() throws CapabilityException;

  /** 既定タイムゾーンを参照せずUTCを選ぶ公開CLI用能力を作ります。 */
  static WallTime systemUtc() {
    return () -> new WallTimeReading(System.currentTimeMillis(), 0);
  }
}
