package jp.bsb.runtime;

/** 認可済み登録へ全内容を原子的に公開する能力です。 */
@FunctionalInterface
public interface FileWriteCapability {
  /** 1回だけ書き込み、閉じた応答または能力失敗を返します。 */
  FileWriteResult write(FileWriteRequest request) throws CapabilityException;
}
