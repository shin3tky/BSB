package jp.bsb.runtime;

import java.util.Objects;

/** BSBプログラムの論理名と場所をホスト境界から取得する能力です。 */
@FunctionalInterface
public interface ProgramIdentity {
  /** 1回の原子的な読取りで識別情報を返します。 */
  ProgramMetadata identity() throws CapabilityException;

  /** 固定した識別情報を返す能力を作ります。 */
  static ProgramIdentity fixed(ProgramMetadata metadata) {
    ProgramMetadata value = Objects.requireNonNull(metadata, "metadata");
    return () -> value;
  }
}
