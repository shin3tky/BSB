package jp.bsb.runtime;

/** HTTPSのHTTP要求・応答と、1実行内の累積処理で共有する規範上限です。 */
public final class HttpLimits {
  /** 1応答に許すヘッダー値数です。 */
  public static final int MAX_RESPONSE_HEADER_VALUES = 128;

  /** 1応答に許す正規化済みヘッダーバイト数です。 */
  public static final int MAX_RESPONSE_HEADER_BYTES = 65_536;

  /** 1実行で受理するHTTP送信語の回数です。 */
  public static final long MAX_SEND_CALLS = 1_024;

  /** 1実行で試行できる要求本文の累積バイト数です。 */
  public static final long MAX_REQUEST_ATTEMPT_BYTES = 134_217_728L;

  /** 1実行で受信できる応答本文の累積バイト数です。 */
  public static final long MAX_RESPONSE_RECEIVED_BYTES = 134_217_728L;

  /** 1実行で構築できるHTTP metadataの累積論理バイト数です。 */
  public static final long MAX_METADATA_CONSTRUCTION_BYTES = 16_777_216L;

  private HttpLimits() {}
}
