package jp.bsb.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 1回のコンパイル・解析処理の中で発生した複数の診断（エラー・警告）を収集・管理するコレクタークラスです。
 *
 * <p>【コンピュータ科学の観点：診断の上限管理と派生エラーの影響制限（Error Cascading Protection）】
 * コンパイラが構文解析などで最初の1つのエラーに遭遇した際、後続のコードを誤って解釈し続け、 大量（数百〜数千件）の派生エラー（カスケードエラー）を画面に出力してしまうことがあります。
 * 派生エラーそのものの発生は解析側の回復処理で抑えます。本クラスは、残った派生エラーによる表示量と処理負荷を制限するため、収集する診断数を最大100件に制限します：
 *
 * <ul>
 *   <li>通算99件目までは通常通りリストへ蓄積します。
 *   <li>100件目に到達した際、100件目の診断を {@link DiagnosticCode#E_DIAGNOSTIC_LIMIT}（上限到達エラー）へ差し替え、
 *       それ以降の診断の収集を安全に打ち切ります。
 * </ul>
 */
public final class DiagnosticCollector {
  /** 1回の処理で報告を許可する診断の最大総数（100件） */
  public static final int MAX_DIAGNOSTICS = 100;

  /** 上限通知エラーの枠を1件分確保するための、通常診断の最大件数（99件） */
  private static final int MAX_REGULAR_DIAGNOSTICS = MAX_DIAGNOSTICS - 1;

  /** 収集された診断の内部リスト */
  private final List<Diagnostic> diagnostics = new ArrayList<>();

  /** 上限に到達したかどうかのフラグ */
  private boolean limitReached;

  /** 診断がまだない空の収集器を作ります。 */
  public DiagnosticCollector() {}

  /**
   * 診断をコレクターに追加します。
   *
   * @param diagnostic 追加する診断オブジェクト
   * @return 元の診断がそのまま蓄積された場合は true、 上限到達により上限通知エラーに差し替えられたか、または以降が無視された場合は false
   */
  public boolean add(Diagnostic diagnostic) {
    Objects.requireNonNull(diagnostic, "diagnostic");
    if (limitReached) {
      return false;
    }
    if (diagnostics.size() < MAX_REGULAR_DIAGNOSTICS) {
      diagnostics.add(diagnostic);
      return true;
    }

    // 100件目に到達した場合、上限通知エラーを生成して追加し、以降の受付を打ち切る
    diagnostics.add(createLimitDiagnostic(diagnostic));
    limitReached = true;
    return false;
  }

  /**
   * 収集されたすべての診断を、仕様規定の順序（{@link Diagnostic#ORDERING}）でソートした不変リストとして返します。
   *
   * @return ソート済みの診断リスト
   */
  public List<Diagnostic> diagnostics() {
    var ordered = new ArrayList<>(diagnostics);
    ordered.sort(Diagnostic.ORDERING);
    return Collections.unmodifiableList(ordered);
  }

  /**
   * 現在収集されている診断の件数を返します。
   *
   * @return 診断件数
   */
  public int size() {
    return diagnostics.size();
  }

  /**
   * 収集された診断の中に、重要度が {@link Severity#ERROR} であるものが1件でも含まれるかを返します。
   *
   * @return エラーが存在する場合は true、警告のみまたは診断なしの場合は false
   */
  public boolean hasErrors() {
    return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
  }

  /**
   * 診断件数が上限（100件）に到達したかを返します。
   *
   * @return 上限に達した場合は true
   */
  public boolean limitReached() {
    return limitReached;
  }

  /** 100件目の通常診断を上限通知エラー（E_DIAGNOSTIC_LIMIT）に変換します。 */
  private static Diagnostic createLimitDiagnostic(Diagnostic replaced) {
    return Diagnostic.builder(
            DiagnosticCode.E_DIAGNOSTIC_LIMIT,
            Severity.ERROR,
            replaced.stage(),
            replaced.sourcePath(),
            replaced.location())
        .limit("diagnostics", MAX_DIAGNOSTICS, MAX_DIAGNOSTICS)
        .build();
  }
}
