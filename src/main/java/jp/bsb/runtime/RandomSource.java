package jp.bsb.runtime;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * 整数・小数乱数の範囲変換から分離した、差し替え可能な乱数バイト源です。
 *
 * <p>公平で独立なビットを供給する源を想定した範囲変換に使用します。標準実装は暗号用途を契約に含めない疑似乱数です。
 * 実行環境ごとに1個を作り、同じ実行では状態を継続します。同時呼出しへの対応は要求しません。
 */
@FunctionalInterface
public interface RandomSource {
  /** 指定配列の全要素を乱数バイトで上書きします。配列を保持しません。 */
  void nextBytes(byte[] bytes) throws CapabilityException;

  /** 自動シードのL64X128MixRandomを使う通常実行用の源を作ります。 */
  static RandomSource standard() {
    RandomGenerator generator = RandomGenerator.of("L64X128MixRandom");
    return generator::nextBytes;
  }

  /** 指定シードのL64X128MixRandomを使う、埋込み・再現試験用の源を作ります。 同じJDK実装、シード、呼出し順では同じバイト列を生成します。 */
  static RandomSource seeded(long seed) {
    RandomGenerator generator = RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    return generator::nextBytes;
  }
}
