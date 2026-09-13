package jp.bsb.frontend.ast;

import java.util.List;
import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/**
 * 1個のBSBソースファイル全体を表すASTの根（root）です。
 *
 * <p>トップレベル要素を元ソースの順番で保持するため、定義間にあるコメントを失いません。名前解決など、定義だけが必要な処理は {@link #definitions()} を利用できます。
 *
 * @param sourcePath 元ソースの識別パス
 * @param elements 単語定義、値宣言、トップレベルコメントを元ソース順に並べた不変リスト
 * @param span ファイル内容全体の範囲
 */
public record Program(String sourcePath, List<TopLevelElement> elements, SourceSpan span)
    implements AstNode {
  /** パスと範囲を検証し、トップレベル要素を不変リストへコピーします。 */
  public Program {
    if (sourcePath == null || sourcePath.isBlank()) {
      throw new IllegalArgumentException("sourcePath must not be blank");
    }
    elements = List.copyOf(elements);
    Objects.requireNonNull(span, "span");
  }

  /**
   * トップレベル要素から単語定義だけを元ソース順に取り出します。
   *
   * @return 単語定義の不変リスト
   */
  public List<WordDefinition> definitions() {
    return elements.stream()
        .filter(WordDefinition.class::isInstance)
        .map(WordDefinition.class::cast)
        .toList();
  }

  /**
   * トップレベル要素から大域定数・変数宣言だけを元ソース順に取り出します。
   *
   * @return 大域宣言の不変リスト
   */
  public List<ValueDeclaration> declarations() {
    return elements.stream()
        .filter(ValueDeclaration.class::isInstance)
        .map(ValueDeclaration.class::cast)
        .toList();
  }

  /**
   * トップレベル要素から論理接続宣言だけを元ソース順に取り出します。
   *
   * @return 論理接続宣言の不変リスト
   */
  public List<LogicalConnectionDeclaration> logicalConnections() {
    return elements.stream()
        .filter(LogicalConnectionDeclaration.class::isInstance)
        .map(LogicalConnectionDeclaration.class::cast)
        .toList();
  }

  /** トップレベル要素から作業領域宣言だけを元ソース順に取り出します。 */
  public List<WorkspaceDeclaration> workspaces() {
    return elements.stream()
        .filter(WorkspaceDeclaration.class::isInstance)
        .map(WorkspaceDeclaration.class::cast)
        .toList();
  }
}
