package jp.bsb.runtime;

/** 検証済みHTTP要求を1回だけ同期送信する、差し替え可能な能力です。 */
@FunctionalInterface
public interface HttpTransport {
  /**
   * 要求を1回送信し、自由文を含まない閉じた結果を返します。
   *
   * @throws CapabilityException 能力が呼出し自体を完了できない場合
   */
  HttpTransportResult send(HttpTransportRequest request) throws CapabilityException;
}
