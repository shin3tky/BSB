package jp.bsb.runtime;

/**
 * ホスト内の資格情報を指す、不透明な識別子です。
 *
 * <p>参照先や秘密値を取り出すAPIを意図的に持ちません。将来の通信能力は、このオブジェクトの同一性を ホスト側の安全な保管庫へ照合できます。
 */
public final class CredentialReference {
  private CredentialReference() {}

  /** 新しい不透明参照を作ります。 */
  public static CredentialReference opaque() {
    return new CredentialReference();
  }

  /** 秘密やホスト識別子を含まない安全表現だけを返します。 */
  @Override
  public String toString() {
    return "<credential-reference>";
  }
}
