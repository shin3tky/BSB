package jp.bsb.frontend.ast;

import java.util.List;
import java.util.Optional;
import jp.bsb.diagnostics.SourceSpan;

/**
 * 単語本体に現れる名前を、呼出し候補として保持します。実在する単語か、予約名や未定義名かの判定は後続の名前解決が担当します。
 *
 * @param name Unicode正規化後の名前
 * @param lexeme 元ソース上の表記
 * @param span 名前と明示型引数を含むソース範囲
 * @param explicitTypeArguments 結果構築呼出しに明記された成功型・失敗型
 * @param logicalConnectionArgument 接続確認語に明記された静的な論理接続引数
 */
public record WordCall(
    String name,
    String lexeme,
    SourceSpan span,
    List<TypeReference> explicitTypeArguments,
    Optional<LogicalConnectionArgument> logicalConnectionArgument,
    Optional<WorkspaceArgument> workspaceArgument)
    implements BodyElement {
  /** 型引数を持たない従来の呼出しを作ります。 */
  public WordCall(String name, String lexeme, SourceSpan span) {
    this(name, lexeme, span, List.of(), Optional.empty(), Optional.empty());
  }

  /** 結果構築語の明示型引数を持つ呼出しを作ります。 */
  public WordCall(
      String name, String lexeme, SourceSpan span, List<TypeReference> explicitTypeArguments) {
    this(name, lexeme, span, explicitTypeArguments, Optional.empty(), Optional.empty());
  }

  /** 静的な論理接続引数を持つ呼出しを作ります。 */
  public WordCall(
      String name,
      String lexeme,
      SourceSpan span,
      LogicalConnectionArgument logicalConnectionArgument) {
    this(name, lexeme, span, List.of(), Optional.of(logicalConnectionArgument), Optional.empty());
  }

  /** 静的な作業領域引数を持つ呼出しを作ります。 */
  public WordCall(
      String name, String lexeme, SourceSpan span, WorkspaceArgument workspaceArgument) {
    this(name, lexeme, span, List.of(), Optional.empty(), Optional.of(workspaceArgument));
  }

  /** 呼出し候補名、原表記、ソース範囲を検証します。 */
  public WordCall {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (lexeme == null || lexeme.isBlank()) {
      throw new IllegalArgumentException("lexeme must not be blank");
    }
    if (span == null) {
      throw new NullPointerException("span");
    }
    explicitTypeArguments = List.copyOf(explicitTypeArguments);
    logicalConnectionArgument =
        java.util.Objects.requireNonNull(logicalConnectionArgument, "logicalConnectionArgument");
    workspaceArgument = java.util.Objects.requireNonNull(workspaceArgument, "workspaceArgument");
    if (!explicitTypeArguments.isEmpty() && explicitTypeArguments.size() != 2) {
      throw new IllegalArgumentException("a typed result constructor must have two type arguments");
    }
    if (!explicitTypeArguments.isEmpty()
        && (logicalConnectionArgument.isPresent() || workspaceArgument.isPresent())) {
      throw new IllegalArgumentException("a call cannot have type and static resource arguments");
    }
    if (logicalConnectionArgument.isPresent() && workspaceArgument.isPresent()) {
      throw new IllegalArgumentException("a call cannot have two static resource arguments");
    }
  }
}
