package jp.bsb.runtime;

/** 認可済み登録から全内容をbounded readする能力です。 */
@FunctionalInterface
public interface FileReadCapability {
  /** 1回だけ読み取り、閉じた応答または能力失敗を返します。 */
  FileReadResult read(FileReadRequest request) throws CapabilityException;
}
