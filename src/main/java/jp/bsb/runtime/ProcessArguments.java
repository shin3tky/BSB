package jp.bsb.runtime;

import java.util.List;
import java.util.Objects;

/** BSBプログラムへ、処理系自身の引数を除いた起動引数を渡す能力です。 */
@FunctionalInterface
public interface ProcessArguments {
  /** 起動引数を順序どおり返します。 */
  List<String> arguments() throws CapabilityException;

  /** 固定した引数列を返す不変能力を作ります。 */
  static ProcessArguments fixed(List<String> values) {
    List<String> copy = List.copyOf(Objects.requireNonNull(values, "values"));
    return () -> copy;
  }
}
