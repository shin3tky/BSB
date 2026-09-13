package jp.bsb.frontend.ast;

/** ソースファイルのトップレベルへ置ける、単語定義、値宣言、コメントを表します。 */
public sealed interface TopLevelElement extends AstNode
    permits Comment,
        LogicalConnectionDeclaration,
        ValueDeclaration,
        WordDefinition,
        WorkspaceDeclaration {}
