package jp.bsb.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 内部の構造化診断（{@link Diagnostic}）オブジェクトのリストを、 人間がターミナル（標準エラー出力）で読みやすいテキスト表現へと整形（レンダリング）するクラスです。
 *
 * <p>【コンピュータ科学の観点：コンパイラのエラーフォーマット】 多くのコマンドラインツールで使われる慣例に合わせ、次の形式で出力します：
 *
 * <pre>{@code
 * ファイル名:行番号:列番号: 重要度[エラーコード]: メッセージ
 *   必要: 期待値
 *   実際: 観測された値
 *   関連位置: 関連ファイル:行:列 関連理由
 *   修正候補: 修正の提案
 * }</pre>
 *
 * という階層構造で出力されます。これにより、開発者が問題を直感的に把握できるとともに、
 * ターミナルやCI（継続的インテグレーション）で位置を見つけやすくなります。IDEなどとの連携では、この人間向け文字列を解析するのではなく、{@link Diagnostic}
 * の構造化フィールドを使用します。
 */
public final class DiagnosticRenderer {
  /** メッセージの生成に使用するメッセージカタログ */
  private final DiagnosticMessageCatalog messages;

  /**
   * 指定されたメッセージカタログを使用してレンダラーを構築します。
   *
   * @param messages メッセージカタログ
   */
  public DiagnosticRenderer(DiagnosticMessageCatalog messages) {
    this.messages = Objects.requireNonNull(messages, "messages");
  }

  /**
   * 複数の診断を仕様で定められた順序に並べ替え、標準エラー出力用のフォーマット済み文字列を生成します。
   *
   * @param diagnostics レンダリング対象の診断リスト
   * @return フォーマットされた文字列（末尾にLFを含む）。診断が空の場合は空文字列
   */
  public String render(List<Diagnostic> diagnostics) {
    if (diagnostics.isEmpty()) {
      return "";
    }

    // 仕様で定められた規定順（フェーズ順 → バイト位置順 → コード名順）にソート
    var ordered = new ArrayList<>(diagnostics);
    ordered.sort(Diagnostic.ORDERING);
    var output = new StringBuilder();
    for (Diagnostic diagnostic : ordered) {
      appendDiagnostic(output, diagnostic);
    }
    return output.toString();
  }

  /** 単一の診断オブジェクトをフォーマットして {@link StringBuilder} に追記します。 */
  private void appendDiagnostic(StringBuilder output, Diagnostic diagnostic) {
    // 1行目: <パス>:<行>:<列>: <重要度>[<コード>]: <メッセージ>
    output.append(diagnostic.sourcePath());
    diagnostic
        .location()
        .displayPosition()
        .ifPresent(
            position -> {
              output.append(':').append(position.line()).append(':').append(position.column());
            });
    output
        .append(": ")
        .append(diagnostic.severity().displayName())
        .append('[')
        .append(diagnostic.code().name())
        .append("]: ")
        .append(messages.format(diagnostic))
        .append('\n');

    // 2行目以降: 詳細情報（期待値、実際値、上限、観測値、関連位置、修正候補）をインデントして追記
    diagnostic.expected().ifPresent(value -> appendDetail(output, "必要", value));
    diagnostic.actual().ifPresent(value -> appendDetail(output, "実際", value));
    if (diagnostic.limit().isPresent()) {
      String value = diagnostic.limit().orElseThrow();
      if (diagnostic.limitName().isPresent()) {
        value = diagnostic.limitName().orElseThrow() + '=' + value;
      }
      appendDetail(output, "上限", value);
    }
    diagnostic.observed().ifPresent(value -> appendDetail(output, "観測値", value));
    for (RelatedLocation related : diagnostic.relatedLocations()) {
      String value;
      if (related.position() == null) {
        value = related.description();
      } else {
        value =
            related.sourcePath()
                + ':'
                + related.position().line()
                + ':'
                + related.position().column()
                + ' '
                + related.description();
      }
      appendDetail(output, "関連位置", value);
    }
    for (String fix : diagnostic.fixes()) {
      appendDetail(output, "修正候補", fix);
    }
  }

  private static void appendDetail(StringBuilder output, String label, String value) {
    output.append("  ").append(label).append(": ").append(value).append('\n');
  }
}
