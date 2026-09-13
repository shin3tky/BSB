package jp.bsb.binding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jp.bsb.analyzer.AnalyzedProgram;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.frontend.ast.Assignment;
import jp.bsb.stdlib.ValueType;

/**
 * 検査済みプログラムの各値参照・代入先を、静的な宣言へ対応づけた内部説明モデルです。
 *
 * <p>行は元ソース位置順で、ループ反復数や再帰深さに依存しません。宣言位置と利用位置は書記素クラスタ列を保持するため、利用者向け表示と同じ位置契約を共有します。
 *
 * @param entries 元ソース位置順の値参照・代入先
 */
public record BindingUseReport(List<Entry> entries) {
  /** 行を不変コピーとして保持します。 */
  public BindingUseReport {
    entries = List.copyOf(entries);
    long previousOffset = -1;
    for (Entry entry : entries) {
      Objects.requireNonNull(entry, "entry");
      if (entry.usePosition().utf8Offset() < previousOffset) {
        throw new IllegalArgumentException("binding report entries must be in source order");
      }
      previousOffset = entry.usePosition().utf8Offset();
    }
  }

  /**
   * 検査済みの名前解決結果から決定的な説明を作ります。
   *
   * @param program 型推論まで成功したプログラム
   * @return 静的な利用位置ごとの説明
   */
  public static BindingUseReport from(AnalyzedProgram program) {
    Objects.requireNonNull(program, "program");
    NameResolution resolution = program.nameResolution();
    var entries = new ArrayList<Entry>(resolution.uses().size());
    for (ResolvedBindingUse use : resolution.uses()) {
      Binding binding =
          resolution
              .findBinding(use.bindingId())
              .orElseThrow(
                  () -> new IllegalStateException("unknown binding use: " + use.bindingId()));
      ValueType type =
          binding
              .typeState()
              .type()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "an explained binding has no inferred type: " + binding.id()));
      String scope = scopeText(resolution, binding);
      SourcePosition usePosition =
          use.node() instanceof Assignment assignment
              ? assignment.targetSpan().start()
              : use.node().span().start();
      entries.add(
          new Entry(
              usePosition,
              use.spelling(),
              use.kind(),
              binding.id(),
              binding.name(),
              binding.kind(),
              scope,
              type,
              binding.nameSpan().start()));
    }
    return new BindingUseReport(entries);
  }

  private static String scopeText(NameResolution resolution, Binding binding) {
    if (binding.storage() == BindingStorage.GLOBAL) {
      return BindingStorage.GLOBAL.reportName();
    }
    LexicalScope scope =
        resolution
            .findScope(binding.scopeId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "an explained binding has no lexical scope: " + binding.id()));
    return BindingStorage.LOCAL.reportName() + ":" + scope.ownerWord().orElseThrow();
  }

  /**
   * 1個の値参照または代入先と、その静的な解決先です。
   *
   * @param usePosition 利用名の開始位置
   * @param spelling 元ソースに書かれた表記
   * @param useKind 読み出しまたは書き込み
   * @param bindingId 解決先の安定ID
   * @param bindingName 解決先の正規化済み名
   * @param bindingKind 定数または変数
   * @param scope 大域または所有単語つき局所スコープ
   * @param type 初期値から推論した型
   * @param declarationPosition 宣言名の開始位置
   */
  public record Entry(
      SourcePosition usePosition,
      String spelling,
      BindingUseKind useKind,
      BindingId bindingId,
      String bindingName,
      BindingKind bindingKind,
      String scope,
      ValueType type,
      SourcePosition declarationPosition) {
    /** 必須値とTSVを壊さない表示文字列を検証します。 */
    public Entry {
      Objects.requireNonNull(usePosition, "usePosition");
      spelling = requireTsvText(spelling, "spelling");
      Objects.requireNonNull(useKind, "useKind");
      Objects.requireNonNull(bindingId, "bindingId");
      bindingName = requireTsvText(bindingName, "bindingName");
      Objects.requireNonNull(bindingKind, "bindingKind");
      scope = requireTsvText(scope, "scope");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(declarationPosition, "declarationPosition");
    }

    private static String requireTsvText(String value, String label) {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException(label + " must not be blank");
      }
      if (value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
        throw new IllegalArgumentException(label + " must fit in one TSV field");
      }
      return value;
    }
  }
}
