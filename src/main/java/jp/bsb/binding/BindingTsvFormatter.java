package jp.bsb.binding;

import java.util.Objects;
import jp.bsb.analyzer.AnalyzedProgram;

/** 束縛の内部名前解決説明を、規範の11列TSVへ変換します。 */
public final class BindingTsvFormatter {
  private static final String HEADER =
      "line\tcolumn\tspelling\tuse\tbindingId\tbindingName\tbindingKind\tscope\ttype\t"
          + "declarationLine\tdeclarationColumn\n";

  private BindingTsvFormatter() {}

  /**
   * 検査済みプログラムからヘッダーと末尾LFを持つTSVを返します。
   *
   * @param program 型推論まで成功したプログラム
   * @return 元ソース位置順の束縛説明TSV
   */
  public static String format(AnalyzedProgram program) {
    return format(BindingUseReport.from(Objects.requireNonNull(program, "program")));
  }

  /**
   * 説明モデルをヘッダーと末尾LFを持つTSVへ変換します。
   *
   * @param report 静的な束縛利用説明
   * @return 規範の11列TSV
   */
  public static String format(BindingUseReport report) {
    Objects.requireNonNull(report, "report");
    var result = new StringBuilder(HEADER);
    for (BindingUseReport.Entry entry : report.entries()) {
      result
          .append(entry.usePosition().line())
          .append('\t')
          .append(entry.usePosition().column())
          .append('\t')
          .append(entry.spelling())
          .append('\t')
          .append(entry.useKind().reportName())
          .append('\t')
          .append(entry.bindingId().displayName())
          .append('\t')
          .append(entry.bindingName())
          .append('\t')
          .append(entry.bindingKind().reportName())
          .append('\t')
          .append(entry.scope())
          .append('\t')
          .append(entry.type().sourceName())
          .append('\t')
          .append(entry.declarationPosition().line())
          .append('\t')
          .append(entry.declarationPosition().column())
          .append('\n');
    }
    return result.toString();
  }
}
