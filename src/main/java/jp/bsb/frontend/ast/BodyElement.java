package jp.bsb.frontend.ast;

/** 単語本体に現れる実行要素、配列、制御構文、助詞、コメントの共通型です。 */
public sealed interface BodyElement extends AstNode
    permits Assignment,
        ArrayLiteral,
        ArrayLoop,
        Comment,
        ConditionLoop,
        Conditional,
        ControlTransfer,
        CountedLoop,
        Literal,
        Particle,
        Propagation,
        ShortCircuitEvaluation,
        ValueDeclaration,
        ValueReference,
        WordCall {}
