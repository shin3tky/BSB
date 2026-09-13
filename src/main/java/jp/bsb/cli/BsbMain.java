package jp.bsb.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * BSB言語処理系（インタープリタ／フォーマッタ）のメインエントリポイントとなるクラスです。
 *
 * <p>【コンピュータ科学の観点】 コマンドラインインターフェース（CLI: Command Line Interface）は、ユーザーがターミナル（端末）から
 * コマンドや引数を与えてプログラムを実行するための窓口です。
 *
 * <p>実際の引数検査とサブコマンド処理は、テスト可能な {@link BsbCli} へ委譲します。このクラスはJVMの入口と終了コードの反映だけを担当します。
 *
 * <ul>
 *   <li>{@code check}: ソースコードの字句解析・構文解析・静的型検査のみを行い、誤りがないか確認する
 *   <li>{@code run}: 静的検査に合格した後、実際にプログラムを実行する
 *   <li>{@code format}: ソースコードを言語仕様で定められた一意な「正規表記」に自動整形する
 * </ul>
 */
public final class BsbMain {
  /** ユーティリティクラスのため、インスタンス化を禁止します。 */
  private BsbMain() {}

  /**
   * BSB処理系の起動メインメソッドです。
   *
   * @param args コマンドライン引数の配列（サブコマンド名やソースファイルパスなど）
   */
  public static void main(String[] args) {
    System.exit(execute(args, System.in, System.out, System.err));
  }

  static int execute(String[] args, OutputStream standardOutput, OutputStream standardError) {
    return execute(args, InputStream.nullInputStream(), standardOutput, standardError);
  }

  static int execute(
      String[] args,
      InputStream standardInput,
      OutputStream standardOutput,
      OutputStream standardError) {
    try {
      return new BsbCli().run(args, standardInput, standardOutput, standardError);
    } catch (RuntimeException exception) {
      try {
        standardError.write("内部エラー: 処理系を初期化できませんでした。\n".getBytes(StandardCharsets.UTF_8));
        standardError.flush();
      } catch (IOException ignored) {
        // CLI初期化と標準エラーの両方が失敗した場合は、終了コードだけを返します。
      }
      return BsbCli.EXIT_INTERNAL;
    }
  }
}
