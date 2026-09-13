package jp.bsb.cli;

import java.util.Objects;
import jp.bsb.runtime.ConnectionResolver;
import jp.bsb.runtime.HttpTransport;

/** TOMLから構築済みの論理接続解決能力とHTTPS送信能力です。 */
record LoadedConnectionConfig(ConnectionResolver resolver, HttpTransport transport) {
  LoadedConnectionConfig {
    Objects.requireNonNull(resolver, "resolver");
    Objects.requireNonNull(transport, "transport");
  }

  @Override
  public String toString() {
    return "<loaded-connection-config>";
  }
}
