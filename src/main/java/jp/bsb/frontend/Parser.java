package jp.bsb.frontend;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import jp.bsb.binding.BindingKind;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticCollector;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.RelatedLocation;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.frontend.ast.ArrayElement;
import jp.bsb.frontend.ast.ArrayLiteral;
import jp.bsb.frontend.ast.ArrayLoop;
import jp.bsb.frontend.ast.Assignment;
import jp.bsb.frontend.ast.BodyElement;
import jp.bsb.frontend.ast.Comment;
import jp.bsb.frontend.ast.ConditionLoop;
import jp.bsb.frontend.ast.Conditional;
import jp.bsb.frontend.ast.ControlTransfer;
import jp.bsb.frontend.ast.CountedLoop;
import jp.bsb.frontend.ast.HttpMethodArgument;
import jp.bsb.frontend.ast.Literal;
import jp.bsb.frontend.ast.LiteralKind;
import jp.bsb.frontend.ast.LogicalConnectionArgument;
import jp.bsb.frontend.ast.LogicalConnectionDeclaration;
import jp.bsb.frontend.ast.Particle;
import jp.bsb.frontend.ast.Program;
import jp.bsb.frontend.ast.ShortCircuitEvaluation;
import jp.bsb.frontend.ast.ShortCircuitOperator;
import jp.bsb.frontend.ast.StackEffect;
import jp.bsb.frontend.ast.TopLevelElement;
import jp.bsb.frontend.ast.TypeReference;
import jp.bsb.frontend.ast.ValueDeclaration;
import jp.bsb.frontend.ast.ValueReference;
import jp.bsb.frontend.ast.WordCall;
import jp.bsb.frontend.ast.WordDefinition;
import jp.bsb.frontend.ast.WorkspaceArgument;
import jp.bsb.frontend.ast.WorkspaceDeclaration;

/**
 * 字句解析済みのトークン列を、ホスト入出力までの文法に従ってASTへ組み立てる再帰下降構文解析器です。
 *
 * <p>【コンピュータ科学の観点：構文解析と責務の分離】 Lexerは個々のトークンを分類しますが、「識別子、とは、スタック効果、本体、こと。」という並びが
 * 1個の単語定義を作るかは判断しません。Parserはトークン列の並びを文法と照合し、意味を持つ階層構造へまとめます。名前が定義済みか、型が一致するかは構文ではないため、
 * 後続の静的検査へ委ねます。
 *
 * <p>構文エラー後は、次の定義開始または現在の定義終端までだけ読み飛ばします。この同期点（synchronization point）を設けることで、壊れた入力を際限なく解釈して
 * 派生診断を増やすことを避けます。
 */
public final class Parser {
  /** 1ソースファイルで受理するトップレベル単語定義の最大数です。 */
  public static final int MAX_DEFINITIONS = 10_000;

  /** 制御フロー以降の再帰的な構文でも共通して使用する構文入れ子上限です。 */
  public static final int MAX_SYNTAX_DEPTH = SyntaxDepthGuard.MAX_DEPTH;

  /** 状態を持たない構文解析器を生成します。解析ごとの状態は {@link #parse} 内部で作られます。 */
  public Parser() {}

  /**
   * 字句解析に成功したソースとトークン列を構文解析します。
   *
   * @param source 元のデコード済みソース。区切りの隣接関係を検査するために使用
   * @param lexResult 字句解析結果
   * @param diagnostics 構文診断を追加する収集器
   * @return 完全またはエラー回復中の部分ASTと成功可否
   * @throws IllegalStateException 字句解析が失敗した結果を渡した場合
   */
  public ParseResult parse(
      SourceText source, LexResult lexResult, DiagnosticCollector diagnostics) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(lexResult, "lexResult");
    Objects.requireNonNull(diagnostics, "diagnostics");

    // tokensForParsing() が字句エラー後のパイプライン進行をここで遮断する。
    List<Token> tokens = lexResult.tokensForParsing();
    var state = new State(source, locateTokens(source, tokens), diagnostics);
    Program program = state.parseProgram();
    return new ParseResult(program, !state.syntaxError);
  }

  /**
   * 区切りはトークンとして残らないため、各トークンのUTF-16範囲を元テキスト上で復元します。
   *
   * <p>探索位置は単調に前進します。文字列やコメント内に別トークンと同じ文字列があっても、そのリテラルまたはコメント全体を先に消費するため、後続トークンと混同しません。
   */
  private static List<LocatedToken> locateTokens(SourceText source, List<Token> tokens) {
    var located = new ArrayList<LocatedToken>(tokens.size());
    int cursor = 0;
    for (Token token : tokens) {
      int start = source.text().indexOf(token.lexeme(), cursor);
      if (start < 0) {
        throw new IllegalStateException("token lexeme cannot be located in its source");
      }
      int end = start + token.lexeme().length();
      SourceSpan computed = source.span(start, end);
      if (computed.start().utf8Offset() != token.span().start().utf8Offset()
          || computed.end().utf8Offset() != token.span().end().utf8Offset()) {
        throw new IllegalStateException("token span does not match its source lexeme");
      }
      located.add(new LocatedToken(token, start, end));
      cursor = end;
    }
    return List.copyOf(located);
  }

  /** 1回の構文解析で変化するカーソル、診断状態、定義数を保持します。 */
  private static final class State {
    private final SourceText source;
    private final List<LocatedToken> tokens;
    private final DiagnosticCollector diagnostics;
    private final SyntaxDepthGuard depthGuard = new SyntaxDepthGuard();
    private int index;
    private int definitionCount;
    private boolean syntaxError;
    private boolean definitionLimitReached;
    private boolean abortCurrentDefinition;
    private boolean suppressEnclosingEofDiagnostics;
    private String currentWordName;
    private final Set<String> globalValueNames = new LinkedHashSet<>();
    private final Map<Integer, Set<String>> localValueNamesByDefinitionStart =
        new LinkedHashMap<>();
    private Set<String> currentValueNames = Set.of();

    private State(SourceText source, List<LocatedToken> tokens, DiagnosticCollector diagnostics) {
      this.source = source;
      this.tokens = tokens;
      this.diagnostics = diagnostics;
      collectDeclaredValueNames();
    }

    /** ファイル全体をトップレベル要素の列として解析します。 */
    private Program parseProgram() {
      var elements = new ArrayList<TopLevelElement>();
      while (!atEnd() && !definitionLimitReached && !diagnostics.limitReached()) {
        if (check(TokenKind.COMMENT)) {
          elements.add(comment(advance().token()));
          continue;
        }
        if (isDefinitionPair(index)) {
          WordDefinition definition = parseWordDefinition();
          if (definition != null) {
            elements.add(definition);
          }
          continue;
        }
        if (isDeclarationPair(index)) {
          if (workspaceDeclarationFollows(index)) {
            WorkspaceDeclaration declaration = parseWorkspaceDeclaration(true);
            if (declaration != null) {
              elements.add(declaration);
            }
          } else if (logicalConnectionDeclarationFollows(index)) {
            LogicalConnectionDeclaration declaration = parseLogicalConnectionDeclaration(true);
            if (declaration != null) {
              elements.add(declaration);
            }
          } else {
            ValueDeclaration declaration = parseDeclaration(globalValueNames);
            if (declaration != null) {
              elements.add(declaration);
            }
          }
          continue;
        }
        if (check(TokenKind.DECLARATION_END)) {
          reportUnexpectedDeclarationEnd(advance().token());
          continue;
        }

        // トップレベルの実行語などを1件報告し、次の定義開始までを1回復単位として読み飛ばす。
        reportUnexpectedTopLevel(current());
        synchronizeAtTopLevel();
      }

      return new Program(source.sourcePath(), elements, source.span(0, source.text().length()));
    }

    /** 全大域宣言名と、各単語に字句上現れる全局所宣言名を構文解析前に収集します。 */
    private void collectDeclaredValueNames() {
      boolean insideWord = false;
      int definitionStart = -1;
      for (int cursor = 0; cursor < tokens.size(); cursor++) {
        if (isDefinitionPair(cursor)) {
          insideWord = true;
          definitionStart = cursor;
          localValueNamesByDefinitionStart.putIfAbsent(cursor, new LinkedHashSet<>());
          cursor++;
          continue;
        }
        if (isDeclarationPair(cursor)) {
          if (logicalConnectionDeclarationFollows(cursor) || workspaceDeclarationFollows(cursor)) {
            cursor++;
            continue;
          }
          String name = tokens.get(cursor).token().value();
          if (insideWord) {
            localValueNamesByDefinitionStart
                .computeIfAbsent(definitionStart, ignored -> new LinkedHashSet<>())
                .add(name);
          } else {
            globalValueNames.add(name);
          }
          cursor++;
          continue;
        }
        if (insideWord
            && isReservedAt(cursor, "こと")
            && tokenKindAt(cursor + 1) == TokenKind.DEFINITION_END) {
          insideWord = false;
          definitionStart = -1;
          cursor++;
        }
      }
      localValueNamesByDefinitionStart.replaceAll((ignored, names) -> Set.copyOf(names));
    }

    /** 論理接続宣言を解析し、局所位置では宣言全体を1回復単位として破棄します。 */
    private LogicalConnectionDeclaration parseLogicalConnectionDeclaration(boolean topLevel) {
      LocatedToken name = advance();
      LocatedToken marker = advance();
      if (!topLevel) {
        reportLogicalConnectionDeclarationScope(name.token(), "wordBody");
        synchronizeDeclaration();
        return null;
      }

      if (name.end() != marker.start()) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_DECLARATION_ADJACENCY,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    marker.token().span())
                .field("name", name.token().value())
                .expected(name.token().lexeme() + "は")
                .actual(
                    name.token().lexeme()
                        + source.text().substring(name.end(), marker.start())
                        + marker.token().lexeme())
                .fix("空白を削除してください")
                .build());
        syntaxError = true;
      }

      while (check(TokenKind.COMMENT)) {
        reportDeclarationHeaderComment(advance().token());
      }
      if (!check(TokenKind.LOGICAL_CONNECTION_DECLARATION)) {
        throw new IllegalStateException("logical connection declaration kind is missing");
      }
      Token kind = advance().token();
      var comments = new ArrayList<Comment>();
      Token firstValue = null;
      while (!atEnd()
          && !check(TokenKind.DECLARATION_END)
          && !isDefinitionPair(index)
          && !checkReserved("こと")
          && !diagnostics.limitReached()) {
        if (check(TokenKind.COMMENT)) {
          comments.add(comment(advance().token()));
        } else {
          if (firstValue == null) {
            firstValue = current().token();
          }
          advance();
        }
      }
      if (firstValue != null) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_LOGICAL_CONNECTION_DECLARATION_VALUE,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    kind.span())
                .field("connection", name.token().value())
                .field("actual", logicalConnectionValueKind(firstValue))
                .expected("初期値なし")
                .actual(logicalConnectionValueKind(firstValue))
                .fix("論理接続宣言から初期値を削除してください")
                .build());
        syntaxError = true;
      }
      if (!check(TokenKind.DECLARATION_END)) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_EXPECTED_DECLARATION_END,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    currentPosition())
                .field("name", name.token().value())
                .expected("。")
                .actual(currentText())
                .fix("ここに「。」を追加してください")
                .relatedLocation(
                    new RelatedLocation(
                        source.sourcePath(), name.token().span().start(), name.token().lexeme()))
                .build());
        syntaxError = true;
        return null;
      }
      Token end = advance().token();
      if (firstValue != null) {
        return null;
      }
      return new LogicalConnectionDeclaration(
          name.token().value(),
          name.token().lexeme(),
          name.token().span(),
          marker.token().span(),
          kind.span(),
          comments,
          end.span(),
          span(name.token().span(), end.span()));
    }

    private void reportDeclarationHeaderComment(Token comment) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_DECLARATION_HEADER_COMMENT_NOT_ALLOWED,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  comment.span())
              .field("context", "宣言ヘッダー")
              .expected("通常の区切り")
              .actual("コメント")
              .fix("コメントを宣言終端の後へ移動してください")
              .build());
      syntaxError = true;
    }

    private void reportLogicalConnectionDeclarationScope(Token name, String scope) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_LOGICAL_CONNECTION_DECLARATION_SCOPE,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  name.span())
              .field("connection", name.value())
              .field("scope", scope)
              .expected("トップレベル")
              .actual(scope)
              .fix("論理接続宣言をトップレベルへ移動してください")
              .build());
      syntaxError = true;
    }

    private static String logicalConnectionValueKind(Token token) {
      return switch (token.kind()) {
        case STRING_LITERAL -> "文字列";
        case INTEGER_LITERAL -> "整数";
        case DECIMAL_LITERAL -> "小数";
        case BOOLEAN_LITERAL -> "真偽";
        case CHARACTER_LITERAL -> "文字";
        case REGEX_LITERAL -> "正規表現";
        default -> token.lexeme();
      };
    }

    /** 作業領域宣言を解析し、局所位置では宣言全体を1回復単位として破棄します。 */
    private WorkspaceDeclaration parseWorkspaceDeclaration(boolean topLevel) {
      LocatedToken name = advance();
      LocatedToken marker = advance();
      if (!topLevel) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_WORKSPACE_DECLARATION_SCOPE,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    name.token().span())
                .field("name", name.token().value())
                .expected("トップレベル")
                .actual("wordBody")
                .fix("作業領域宣言をトップレベルへ移動してください")
                .build());
        syntaxError = true;
        synchronizeDeclaration();
        return null;
      }
      if (name.end() != marker.start()) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_DECLARATION_ADJACENCY,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    marker.token().span())
                .field("name", name.token().value())
                .expected(name.token().lexeme() + "は")
                .actual(
                    name.token().lexeme()
                        + source.text().substring(name.end(), marker.start())
                        + marker.token().lexeme())
                .fix("空白を削除してください")
                .build());
        syntaxError = true;
      }
      while (check(TokenKind.COMMENT)) {
        reportDeclarationHeaderComment(advance().token());
      }
      if (!check(TokenKind.WORKSPACE_DECLARATION)) {
        throw new IllegalStateException("workspace declaration kind is missing");
      }
      Token kind = advance().token();
      var comments = new ArrayList<Comment>();
      Token firstValue = null;
      while (!atEnd()
          && !check(TokenKind.DECLARATION_END)
          && !isDefinitionPair(index)
          && !checkReserved("こと")
          && !diagnostics.limitReached()) {
        if (check(TokenKind.COMMENT)) {
          comments.add(comment(advance().token()));
        } else {
          if (firstValue == null) firstValue = current().token();
          advance();
        }
      }
      if (firstValue != null) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_WORKSPACE_DECLARATION_VALUE,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    kind.span())
                .field("name", name.token().value())
                .expected("初期値なし")
                .actual(logicalConnectionValueKind(firstValue))
                .fix("作業領域宣言から初期値を削除してください")
                .build());
        syntaxError = true;
      }
      if (!check(TokenKind.DECLARATION_END)) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_EXPECTED_DECLARATION_END,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    currentPosition())
                .field("name", name.token().value())
                .expected("。")
                .actual(currentText())
                .fix("ここに「。」を追加してください")
                .relatedLocation(
                    new RelatedLocation(
                        source.sourcePath(), name.token().span().start(), name.token().lexeme()))
                .build());
        syntaxError = true;
        return null;
      }
      Token end = advance().token();
      if (firstValue != null) return null;
      return new WorkspaceDeclaration(
          name.token().value(),
          name.token().lexeme(),
          name.token().span(),
          marker.token().span(),
          kind.span(),
          comments,
          end.span(),
          span(name.token().span(), end.span()));
    }

    /** 宣言ヘッダー、初期値、宣言終端を1個のASTノードへまとめます。 */
    private ValueDeclaration parseDeclaration(Set<String> valueNames) {
      LocatedToken name = advance();
      LocatedToken marker = advance();
      if (name.end() != marker.start()) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_DECLARATION_ADJACENCY,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    marker.token().span())
                .field("name", name.token().value())
                .expected(name.token().lexeme() + "は")
                .actual(
                    name.token().lexeme()
                        + source.text().substring(name.end(), marker.start())
                        + marker.token().lexeme())
                .fix("空白を削除してください")
                .build());
        syntaxError = true;
      }

      while (check(TokenKind.COMMENT)) {
        Token comment = advance().token();
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_DECLARATION_HEADER_COMMENT_NOT_ALLOWED,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    comment.span())
                .field("context", "宣言ヘッダー")
                .expected("通常の区切り")
                .actual("コメント")
                .fix("コメントを宣言終端の後へ移動してください")
                .build());
        syntaxError = true;
      }

      if (!check(TokenKind.CONSTANT_DECLARATION) && !check(TokenKind.VARIABLE_DECLARATION)) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_EXPECTED_DECLARATION_KIND,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    currentPosition())
                .field("name", name.token().value())
                .expected("定数または変数")
                .actual(currentText())
                .fix("「定数」または「変数」を追加してください")
                .build());
        syntaxError = true;
        synchronizeDeclaration();
        return null;
      }

      Token kindToken = advance().token();
      BindingKind kind =
          kindToken.kind() == TokenKind.CONSTANT_DECLARATION
              ? BindingKind.CONSTANT
              : BindingKind.VARIABLE;
      var initializer = new ArrayList<BodyElement>();
      while (!atEnd()
          && !check(TokenKind.DECLARATION_END)
          && !isDefinitionPair(index)
          && !checkReserved("こと")
          && !diagnostics.limitReached()) {
        parseInitializerElement(initializer, valueNames);
      }

      if (!check(TokenKind.DECLARATION_END)) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_EXPECTED_DECLARATION_END,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    currentPosition())
                .field("name", name.token().value())
                .expected("。")
                .actual(currentText())
                .fix("ここに「。」を追加してください")
                .relatedLocation(
                    new RelatedLocation(
                        source.sourcePath(), name.token().span().start(), name.token().lexeme()))
                .build());
        syntaxError = true;
        return null;
      }

      Token end = advance().token();
      return new ValueDeclaration(
          name.token().value(),
          name.token().lexeme(),
          name.token().span(),
          marker.token().span(),
          kind,
          kindToken.span(),
          initializer,
          end.span(),
          span(name.token().span(), end.span()));
    }

    /** 初期値で許可された単純要素を保持し、制御・宣言・代入を専用診断で除外します。 */
    private void parseInitializerElement(List<BodyElement> initializer, Set<String> valueNames) {
      TokenKind kind = current().token().kind();
      switch (kind) {
        case COMMENT -> initializer.add(comment(advance().token()));
        case INTEGER_LITERAL,
            DECIMAL_LITERAL,
            BOOLEAN_LITERAL,
            CHARACTER_LITERAL,
            STRING_LITERAL,
            REGEX_LITERAL ->
            initializer.add(literal(advance().token()));
        case ARRAY_OPEN -> {
          if (!tryEnterControl()) {
            return;
          }
          try {
            ArrayLiteral array = parseArrayLiteral(valueNames);
            if (array != null) {
              initializer.add(array);
            }
          } finally {
            depthGuard.exit();
          }
        }
        case PARTICLE -> {
          if (isAssignmentStartingAtParticle()) {
            reportInitializerElementNotAllowed(current().token(), "入れる");
            consumeAssignmentCandidate();
          } else {
            Token particle = advance().token();
            initializer.add(new Particle(particle.value(), particle.lexeme(), particle.span()));
          }
        }
        case IDENTIFIER -> {
          if (isDeclarationPair(index)) {
            if (workspaceDeclarationFollows(index)) {
              parseWorkspaceDeclaration(false);
            } else if (logicalConnectionDeclarationFollows(index)) {
              parseLogicalConnectionDeclaration(false);
            } else {
              reportInitializerElementNotAllowed(current().token(), "宣言");
              advance();
              advance();
            }
          } else if (isAssignmentMissingValueParticle()) {
            reportInitializerElementNotAllowed(current().token(), "入れる");
            consumeAssignmentCandidate();
          } else {
            initializer.add(nameUse(advance(), valueNames));
          }
        }
        case RESERVED_SYNTAX -> {
          Token word = advance().token();
          initializer.add(new WordCall(word.value(), word.lexeme(), word.span()));
        }
        case CONDITIONAL_START,
            SHORT_CIRCUIT_OR,
            SHORT_CIRCUIT_AND,
            COUNTED_LOOP_START,
            CONDITION_LOOP_START,
            ARRAY_LOOP_START -> {
          Token forbidden = advance().token();
          reportInitializerElementNotAllowed(forbidden, forbidden.lexeme());
          skipForbiddenInitializerControl();
        }
        case CONDITIONAL_ELSE,
            CONDITIONAL_END,
            LOOP_CONDITION_SEPARATOR,
            LOOP_END,
            BREAK,
            CONTINUE,
            RETURN -> {
          Token forbidden = advance().token();
          reportInitializerElementNotAllowed(forbidden, forbidden.lexeme());
        }
        case ARRAY_CLOSE -> reportUnexpectedArrayEnd(advance().token());
        case ASSIGNMENT -> {
          Token forbidden = advance().token();
          reportInitializerElementNotAllowed(forbidden, forbidden.lexeme());
        }
        default -> {
          Token forbidden = advance().token();
          reportInitializerElementNotAllowed(forbidden, forbidden.lexeme());
        }
      }
    }

    /** 初期値内に誤って置かれた制御構文を、対応する終了語まで1回復単位として読み飛ばします。 */
    private void skipForbiddenInitializerControl() {
      int depth = 1;
      while (!atEnd() && !check(TokenKind.DECLARATION_END) && depth > 0) {
        TokenKind kind = current().token().kind();
        if (kind == TokenKind.CONDITIONAL_START
            || kind == TokenKind.SHORT_CIRCUIT_OR
            || kind == TokenKind.SHORT_CIRCUIT_AND
            || kind == TokenKind.COUNTED_LOOP_START
            || kind == TokenKind.CONDITION_LOOP_START) {
          depth++;
        } else if (kind == TokenKind.CONDITIONAL_END || kind == TokenKind.LOOP_END) {
          depth--;
        }
        advance();
      }
    }

    private void reportInitializerElementNotAllowed(Token token, String element) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_INITIALIZER_ELEMENT_NOT_ALLOWED,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  token.span())
              .field("element", element)
              .expected("初期値で許可された要素")
              .actual(token.lexeme())
              .fix(element.equals("ならば") ? "条件分岐を宣言の外へ移動してください" : "この要素を宣言の外へ移動してください")
              .build());
      syntaxError = true;
    }

    private void synchronizeDeclaration() {
      while (!atEnd() && !isDefinitionPair(index) && !checkReserved("こと")) {
        if (check(TokenKind.DECLARATION_END)) {
          advance();
          return;
        }
        advance();
      }
    }

    /** 名前から終端の「こと。」までを1個の単語定義へまとめます。 */
    private WordDefinition parseWordDefinition() {
      abortCurrentDefinition = false;
      int definitionStart = index;
      LocatedToken name = advance();
      LocatedToken marker = advance();
      currentWordName = name.token().value();
      var names = new LinkedHashSet<>(globalValueNames);
      names.addAll(localValueNamesByDefinitionStart.getOrDefault(definitionStart, Set.of()));
      currentValueNames = Set.copyOf(names);
      definitionCount++;
      if (definitionCount > MAX_DEFINITIONS) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_DEFINITION_LIMIT,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    name.token().span())
                .limit("definitions", MAX_DEFINITIONS, definitionCount)
                .build());
        syntaxError = true;
        definitionLimitReached = true;
        return null;
      }

      if (name.end() != marker.start()) {
        String actual =
            name.token().lexeme()
                + source.text().substring(name.end(), marker.start())
                + marker.token().lexeme();
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_DEFINITION_ADJACENCY,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    marker.token().span())
                .expected(name.token().lexeme() + "とは")
                .actual(actual)
                .fix("空白を削除してください")
                .build());
        syntaxError = true;
      }

      // 定義開始とスタック効果の間のコメントは、ヘッダーを安全に整形できなくするため禁止する。
      boolean headerHadComment = false;
      while (check(TokenKind.COMMENT)) {
        headerHadComment = true;
        reportCommentNotAllowed(advance().token(), "スタック効果の後のコメント");
      }

      if (atEnd() || !isStackOpen(current().token().kind())) {
        reportExpectedWordEnd(name.token().value(), currentPosition(), currentText());
        synchronizeAtTopLevel();
        return null;
      }

      LocatedToken opening = current();
      String headerGap = source.text().substring(marker.end(), opening.start());
      if (!headerHadComment && !isSeparatorGap(headerGap)) {
        reportUnexpectedTopLevel(opening);
      }

      StackEffect stackEffect = parseStackEffect();
      List<BodyElement> body = parseBody(Set.of());
      if (abortCurrentDefinition) {
        synchronizeAtTopLevel();
        return null;
      }

      if (checkReserved("こと")) {
        LocatedToken wordEnd = current();
        WordDefinition definition = finishDefinition(name.token(), stackEffect, body, wordEnd);
        if (definition != null) {
          return definition;
        }
        synchronizeAtTopLevel();
        return null;
      }

      if (suppressEnclosingEofDiagnostics) {
        return null;
      }

      reportExpectedWordEnd(name.token().value(), eofPosition(), "EOF");
      return null;
    }

    /**
     * 現在の字句位置から、呼出し側が指定した終了語または単語終端までを本体要素列へ変換します。
     *
     * <p>【コンピュータ科学の観点：再帰下降構文解析】制御構文を見つけると、その内側の本体を同じメソッドで解析します。各呼出しが自分に有効な終了語だけを渡すため、
     * 内側のループ終了語が外側の条件分岐を誤って閉じることはありません。
     */
    private List<BodyElement> parseBody(Set<TokenKind> terminators) {
      var body = new ArrayList<BodyElement>();
      while (!atEnd()
          && !diagnostics.limitReached()
          && !abortCurrentDefinition
          && !checkReserved("こと")) {
        TokenKind kind = current().token().kind();
        if (terminators.contains(kind)) {
          break;
        }

        switch (kind) {
          case COMMENT -> body.add(comment(advance().token()));
          case INTEGER_LITERAL,
              DECIMAL_LITERAL,
              BOOLEAN_LITERAL,
              CHARACTER_LITERAL,
              STRING_LITERAL,
              REGEX_LITERAL ->
              body.add(literal(advance().token()));
          case ARRAY_OPEN -> {
            if (!tryEnterControl()) {
              break;
            }
            try {
              ArrayLiteral array = parseArrayLiteral(currentValueNames);
              if (array != null) {
                body.add(array);
              }
            } finally {
              depthGuard.exit();
            }
          }
          case PARTICLE -> {
            if (isAssignmentStartingAtParticle()) {
              Assignment assignment = parseAssignmentFromParticle();
              if (assignment != null) {
                body.add(assignment);
              }
            } else {
              Token particle = advance().token();
              body.add(new Particle(particle.value(), particle.lexeme(), particle.span()));
            }
          }
          case IDENTIFIER -> {
            if (isDeclarationPair(index)) {
              if (workspaceDeclarationFollows(index)) {
                parseWorkspaceDeclaration(false);
              } else if (logicalConnectionDeclarationFollows(index)) {
                parseLogicalConnectionDeclaration(false);
              } else {
                ValueDeclaration declaration = parseDeclaration(currentValueNames);
                if (declaration != null) {
                  body.add(declaration);
                }
              }
            } else if (isAssignmentMissingValueParticle()) {
              reportAssignmentMissingValueParticle();
            } else {
              body.add(nameUse(advance(), currentValueNames));
            }
          }
          case RESERVED_SYNTAX -> {
            Token word = advance().token();
            body.add(new WordCall(word.value(), word.lexeme(), word.span()));
          }
          case CONDITIONAL_START,
              SHORT_CIRCUIT_OR,
              SHORT_CIRCUIT_AND,
              COUNTED_LOOP_START,
              CONDITION_LOOP_START -> {
            if (!tryEnterControl()) {
              break;
            }
            try {
              BodyElement control = parseControl(kind);
              if (control != null) {
                body.add(control);
              }
            } finally {
              depthGuard.exit();
            }
          }
          case ARRAY_LOOP_START -> {
            if (!tryEnterControl()) {
              break;
            }
            Optional<Particle> inputParticle = removeArrayLoopInputParticle(body);
            try {
              ArrayLoop loop = parseArrayLoop(inputParticle);
              if (loop != null) {
                body.add(loop);
              }
            } finally {
              depthGuard.exit();
            }
          }
          case BREAK, CONTINUE, RETURN -> body.add(controlTransfer(advance().token()));
          case ARRAY_CLOSE -> reportUnexpectedArrayEnd(advance().token());
          case ASSIGNMENT -> reportAssignmentWithoutPrefix(advance().token());
          case DECLARATION_END -> reportUnexpectedDeclarationEnd(advance().token());
          case CONSTANT_DECLARATION,
              VARIABLE_DECLARATION,
              LOGICAL_CONNECTION_DECLARATION,
              WORKSPACE_DECLARATION,
              DECLARATION_MARKER -> {
            LocatedToken token = current();
            reportExpectedWordEnd(currentWordName, token.token().span(), token.token().lexeme());
            abortCurrentDefinition = true;
          }
          case CONDITIONAL_ELSE, CONDITIONAL_END, LOOP_CONDITION_SEPARATOR, LOOP_END -> {
            reportUnexpectedControlWord(advance().token());
          }
          default -> {
            if (isDeclarationPair(index)) {
              ValueDeclaration declaration = parseDeclaration(currentValueNames);
              if (declaration != null) {
                body.add(declaration);
              }
            } else {
              LocatedToken token = current();
              reportExpectedWordEnd(currentWordName, token.token().span(), token.token().lexeme());
              abortCurrentDefinition = true;
            }
          }
        }
      }
      return List.copyOf(body);
    }

    /** 「を」から始まる正常または助詞不足の代入候補かを判定します。 */
    private boolean isAssignmentStartingAtParticle() {
      if (!checkParticleAt(index, "を")) {
        return false;
      }
      if (tokenKindAt(index + 1) == TokenKind.ASSIGNMENT) {
        return true;
      }
      if (checkParticleAt(index + 1, "に")) {
        return tokenKindAt(index + 2) == TokenKind.ASSIGNMENT;
      }
      if (tokenKindAt(index + 1) != TokenKind.IDENTIFIER) {
        return false;
      }
      return tokenKindAt(index + 2) == TokenKind.ASSIGNMENT
          || checkParticleAt(index + 2, "に") && tokenKindAt(index + 3) == TokenKind.ASSIGNMENT;
    }

    /** 代入先から始まり「を」だけが欠けた候補かを判定します。 */
    private boolean isAssignmentMissingValueParticle() {
      return tokenKindAt(index) == TokenKind.IDENTIFIER
          && checkParticleAt(index + 1, "に")
          && tokenKindAt(index + 2) == TokenKind.ASSIGNMENT;
    }

    /** 正常な代入をAST化し、代入先または「に」の欠落を規定位置で報告します。 */
    private Assignment parseAssignmentFromParticle() {
      Token valueParticle = advance().token();
      if (!check(TokenKind.IDENTIFIER)) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_EXPECTED_ASSIGNMENT_TARGET,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    currentPosition())
                .expected("変数名")
                .actual(currentText())
                .fix("「を」と「に」の間に変数名を追加してください")
                .build());
        syntaxError = true;
        if (checkParticleAt(index, "に")) {
          advance();
        }
        if (check(TokenKind.ASSIGNMENT)) {
          advance();
        }
        return null;
      }

      Token target = advance().token();
      if (!checkParticleAt(index, "に")) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_EXPECTED_ASSIGNMENT_TARGET_PARTICLE,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    currentPosition())
                .field("target", target.value())
                .expected("に")
                .actual(currentText())
                .fix("「" + target.lexeme() + "」の後に「に」を追加してください")
                .build());
        syntaxError = true;
        if (check(TokenKind.ASSIGNMENT)) {
          advance();
        }
        return null;
      }

      Token targetParticle = advance().token();
      if (!check(TokenKind.ASSIGNMENT)) {
        reportAssignmentWithoutPrefix(targetParticle);
        return null;
      }
      Token keyword = advance().token();
      return new Assignment(
          target.value(),
          target.lexeme(),
          valueParticle.span(),
          target.span(),
          targetParticle.span(),
          keyword.span(),
          span(valueParticle.span(), keyword.span()));
    }

    /** 「を」だけがない代入候補を1診断として消費します。 */
    private void reportAssignmentMissingValueParticle() {
      Token target = advance().token();
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_EXPECTED_ASSIGNMENT_VALUE_PARTICLE,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  target.span())
              .field("target", target.value())
              .expected("を " + target.lexeme() + " に 入れる")
              .actual(target.lexeme() + " に 入れる")
              .fix("「" + target.lexeme() + "」の前に「を」を追加してください")
              .build());
      syntaxError = true;
      advance();
      advance();
    }

    /** 接頭要素がない「入れる」を有限に処理します。 */
    private void reportAssignmentWithoutPrefix(Token keyword) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_EXPECTED_ASSIGNMENT_TARGET,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  keyword.span())
              .expected("を 変数名 に 入れる")
              .actual(keyword.lexeme())
              .fix("代入先を追加してください")
              .build());
      syntaxError = true;
    }

    /** 初期値で見つけた代入候補を、最大4トークンだけ消費します。 */
    private void consumeAssignmentCandidate() {
      int consumed = 0;
      while (!atEnd() && consumed < 4) {
        TokenKind kind = current().token().kind();
        advance();
        consumed++;
        if (kind == TokenKind.ASSIGNMENT) {
          return;
        }
      }
    }

    /** 深さ上限を検査済みの制御開始語を、対応するASTノードへ変換します。 */
    private BodyElement parseControl(TokenKind kind) {
      return switch (kind) {
        case CONDITIONAL_START -> parseConditional();
        case SHORT_CIRCUIT_OR, SHORT_CIRCUIT_AND -> parseShortCircuit();
        case COUNTED_LOOP_START -> parseCountedLoop();
        case CONDITION_LOOP_START -> parseConditionLoop();
        default -> throw new IllegalArgumentException("token is not a control opener: " + kind);
      };
    }

    /** {@code または} または {@code かつ} から対応する {@code つぎに} までを解析します。 */
    private ShortCircuitEvaluation parseShortCircuit() {
      Token opening = advance().token();
      List<BodyElement> rightBody = parseBody(Set.of(TokenKind.CONDITIONAL_END));
      if (abortCurrentDefinition) {
        return null;
      }
      if (!check(TokenKind.CONDITIONAL_END)) {
        reportExpectedShortCircuitEnd(opening);
        return null;
      }
      Token end = advance().token();
      ShortCircuitOperator operator =
          opening.kind() == TokenKind.SHORT_CIRCUIT_OR
              ? ShortCircuitOperator.OR
              : ShortCircuitOperator.AND;
      return new ShortCircuitEvaluation(
          operator, opening.span(), rightBody, end.span(), span(opening.span(), end.span()));
    }

    /** {@code ならば} から対応する {@code つぎに} までを解析します。 */
    private Conditional parseConditional() {
      Token opening = advance().token();
      List<BodyElement> trueBody =
          parseBody(Set.of(TokenKind.CONDITIONAL_ELSE, TokenKind.CONDITIONAL_END));
      if (abortCurrentDefinition) {
        return null;
      }

      SourceSpan elseSpan = null;
      var falseBody = new ArrayList<BodyElement>();
      if (check(TokenKind.CONDITIONAL_ELSE)) {
        elseSpan = advance().token().span();
        falseBody.addAll(parseBody(Set.of(TokenKind.CONDITIONAL_ELSE, TokenKind.CONDITIONAL_END)));
        while (!abortCurrentDefinition && check(TokenKind.CONDITIONAL_ELSE)) {
          Token duplicate = advance().token();
          reportDuplicateElse(duplicate, elseSpan);
          falseBody.addAll(
              parseBody(Set.of(TokenKind.CONDITIONAL_ELSE, TokenKind.CONDITIONAL_END)));
        }
      }
      if (abortCurrentDefinition) {
        return null;
      }

      if (!check(TokenKind.CONDITIONAL_END)) {
        reportExpectedIfEnd(opening);
        return null;
      }
      Token end = advance().token();
      return new Conditional(
          opening.span(),
          trueBody,
          Optional.ofNullable(elseSpan),
          falseBody,
          end.span(),
          span(opening.span(), end.span()));
    }

    /** {@code 回だけ} から対応する {@code 繰り返す} までを解析します。 */
    private CountedLoop parseCountedLoop() {
      Token opening = advance().token();
      List<BodyElement> body = parseBody(Set.of(TokenKind.LOOP_END));
      if (abortCurrentDefinition) {
        return null;
      }
      if (!check(TokenKind.LOOP_END)) {
        reportExpectedLoopEnd(opening, "回数");
        return null;
      }
      Token end = advance().token();
      return new CountedLoop(opening.span(), body, end.span(), span(opening.span(), end.span()));
    }

    /** {@code ここから}、{@code 続く間}、{@code 繰り返す} の3区間を解析します。 */
    private ConditionLoop parseConditionLoop() {
      Token opening = advance().token();
      List<BodyElement> conditionBody =
          parseBody(Set.of(TokenKind.LOOP_CONDITION_SEPARATOR, TokenKind.LOOP_END));
      if (abortCurrentDefinition) {
        return null;
      }
      if (!check(TokenKind.LOOP_CONDITION_SEPARATOR)) {
        reportExpectedLoopConditionSeparator(opening);
        if (check(TokenKind.LOOP_END)) {
          advance();
        }
        return null;
      }

      Token separator = advance().token();
      var body = new ArrayList<BodyElement>();
      body.addAll(parseBody(Set.of(TokenKind.LOOP_CONDITION_SEPARATOR, TokenKind.LOOP_END)));
      while (!abortCurrentDefinition && check(TokenKind.LOOP_CONDITION_SEPARATOR)) {
        Token duplicate = advance().token();
        reportDuplicateLoopSeparator(duplicate, separator.span());
        body.addAll(parseBody(Set.of(TokenKind.LOOP_CONDITION_SEPARATOR, TokenKind.LOOP_END)));
      }
      if (abortCurrentDefinition) {
        return null;
      }
      if (!check(TokenKind.LOOP_END)) {
        reportExpectedLoopEnd(opening, "条件");
        return null;
      }

      Token end = advance().token();
      return new ConditionLoop(
          opening.span(),
          conditionBody,
          separator.span(),
          body,
          end.span(),
          span(opening.span(), end.span()));
    }

    /** 配列反復の直前にある「を」を通常助詞列から取り除き、反復ノードへ関連づけます。 */
    private Optional<Particle> removeArrayLoopInputParticle(List<BodyElement> body) {
      if (body.isEmpty() || !(body.getLast() instanceof Particle particle)) {
        return Optional.empty();
      }
      if (!particle.name().equals("を")) {
        return Optional.empty();
      }
      body.removeLast();
      return Optional.of(particle);
    }

    /** {@code 各要素について}から対応する{@code 繰り返す}までを解析します。 */
    private ArrayLoop parseArrayLoop(Optional<Particle> inputParticle) {
      Token opening = advance().token();
      List<BodyElement> body = parseBody(Set.of(TokenKind.LOOP_END));
      if (abortCurrentDefinition) {
        return null;
      }
      if (!check(TokenKind.LOOP_END)) {
        reportExpectedArrayLoopEnd(opening);
        return null;
      }
      Token end = advance().token();
      return new ArrayLoop(
          inputParticle, opening.span(), body, end.span(), span(opening.span(), end.span()));
    }

    /** {@code 【}から対応する{@code 】}までを、区切られた要素式の一覧へ変換します。 */
    private ArrayLiteral parseArrayLiteral(Set<String> valueNames) {
      Token opening = advance().token();
      var elements = new ArrayList<ArrayElement>();
      int elementIndex = 1;
      boolean elementRequired = false;

      while (!atEnd() && !diagnostics.limitReached() && !abortCurrentDefinition) {
        if (check(TokenKind.ARRAY_CLOSE)) {
          Token end = advance().token();
          if (elementRequired) {
            reportExpectedArrayElement(opening, end, elementIndex, ArrayElementGap.TRAILING);
          }
          return new ArrayLiteral(
              opening.span(), elements, end.span(), span(opening.span(), end.span()));
        }
        if (isArrayRecoveryBoundary()) {
          reportExpectedArrayEnd(opening);
          return null;
        }
        if (check(TokenKind.ARRAY_SEPARATOR)) {
          Token separator = advance().token();
          reportExpectedArrayElement(
              opening,
              separator,
              elementIndex,
              elements.isEmpty() && !elementRequired
                  ? ArrayElementGap.LEADING
                  : ArrayElementGap.CONSECUTIVE);
          elementRequired = true;
          continue;
        }

        ArrayElement element = parseArrayElement(opening, elementIndex, valueNames);
        if (element != null) {
          elements.add(element);
        }
        elementRequired = false;
        if (check(TokenKind.ARRAY_SEPARATOR)) {
          advance();
          elementIndex++;
          elementRequired = true;
        }
      }

      if (!diagnostics.limitReached() && !abortCurrentDefinition) {
        reportExpectedArrayEnd(opening);
      }
      return null;
    }

    /** 現在位置から次の配列区切りまたは終端までを1個の要素式として解析します。 */
    private ArrayElement parseArrayElement(
        Token opening, int elementIndex, Set<String> valueNames) {
      SourceSpan firstSpan = current().token().span();
      SourceSpan lastSpan = firstSpan;
      var body = new ArrayList<BodyElement>();
      while (!atEnd()
          && !check(TokenKind.ARRAY_SEPARATOR)
          && !check(TokenKind.ARRAY_CLOSE)
          && !isArrayRecoveryBoundary()
          && !abortCurrentDefinition
          && !diagnostics.limitReached()) {
        TokenKind kind = current().token().kind();
        switch (kind) {
          case COMMENT -> {
            Token token = advance().token();
            body.add(comment(token));
            lastSpan = token.span();
          }
          case INTEGER_LITERAL,
              DECIMAL_LITERAL,
              BOOLEAN_LITERAL,
              CHARACTER_LITERAL,
              STRING_LITERAL,
              REGEX_LITERAL -> {
            Token token = advance().token();
            body.add(literal(token));
            lastSpan = token.span();
          }
          case ARRAY_OPEN -> {
            if (!tryEnterControl()) {
              return null;
            }
            try {
              ArrayLiteral nested = parseArrayLiteral(valueNames);
              if (nested != null) {
                body.add(nested);
                lastSpan = nested.span();
              }
            } finally {
              depthGuard.exit();
            }
          }
          case PARTICLE -> {
            if (isAssignmentStartingAtParticle()) {
              Token forbidden = current().token();
              reportArrayElementNotAllowed(opening, forbidden, elementIndex, "入れる");
              skipArrayElementRemainder();
              return null;
            }
            Token particle = advance().token();
            body.add(new Particle(particle.value(), particle.lexeme(), particle.span()));
            lastSpan = particle.span();
          }
          case IDENTIFIER -> {
            if (isDeclarationPair(index)) {
              if (workspaceDeclarationFollows(index)) {
                parseWorkspaceDeclaration(false);
              } else if (logicalConnectionDeclarationFollows(index)) {
                parseLogicalConnectionDeclaration(false);
              } else {
                Token forbidden = current().token();
                reportArrayElementNotAllowed(opening, forbidden, elementIndex, "宣言");
                skipArrayElementRemainder();
              }
              return null;
            }
            if (isAssignmentMissingValueParticle()) {
              Token forbidden = current().token();
              reportArrayElementNotAllowed(opening, forbidden, elementIndex, "入れる");
              skipArrayElementRemainder();
              return null;
            }
            LocatedToken name = advance();
            BodyElement use = nameUse(name, valueNames);
            body.add(use);
            lastSpan = use.span();
          }
          case RESERVED_SYNTAX -> {
            Token word = advance().token();
            body.add(new WordCall(word.value(), word.lexeme(), word.span()));
            lastSpan = word.span();
          }
          case CONDITIONAL_START,
              SHORT_CIRCUIT_OR,
              SHORT_CIRCUIT_AND,
              COUNTED_LOOP_START,
              CONDITION_LOOP_START,
              ARRAY_LOOP_START -> {
            Token forbidden = advance().token();
            reportArrayElementNotAllowed(opening, forbidden, elementIndex, forbidden.lexeme());
            skipForbiddenArrayControl();
            skipArrayElementRemainder();
            return null;
          }
          case CONDITIONAL_ELSE,
              CONDITIONAL_END,
              LOOP_CONDITION_SEPARATOR,
              LOOP_END,
              BREAK,
              CONTINUE,
              RETURN,
              CONSTANT_DECLARATION,
              VARIABLE_DECLARATION,
              LOGICAL_CONNECTION_DECLARATION,
              WORKSPACE_DECLARATION,
              DECLARATION_MARKER,
              ASSIGNMENT -> {
            Token forbidden = advance().token();
            reportArrayElementNotAllowed(opening, forbidden, elementIndex, forbidden.lexeme());
            skipArrayElementRemainder();
            return null;
          }
          default -> {
            Token forbidden = advance().token();
            reportArrayElementNotAllowed(opening, forbidden, elementIndex, forbidden.lexeme());
            skipArrayElementRemainder();
            return null;
          }
        }
      }
      if (body.isEmpty()) {
        return null;
      }
      return new ArrayElement(body, span(firstSpan, lastSpan));
    }

    /** 禁止要素を1件報告した後、同じ要素式の残りだけを有限に読み飛ばします。 */
    private void skipArrayElementRemainder() {
      int nestedArrays = 0;
      while (!atEnd() && !isArrayRecoveryBoundary() && !abortCurrentDefinition) {
        if (check(TokenKind.ARRAY_OPEN)) {
          nestedArrays++;
          advance();
          continue;
        }
        if (check(TokenKind.ARRAY_CLOSE)) {
          if (nestedArrays == 0) {
            return;
          }
          nestedArrays--;
          advance();
          continue;
        }
        if (nestedArrays == 0 && check(TokenKind.ARRAY_SEPARATOR)) {
          return;
        }
        advance();
      }
    }

    /** 禁止された制御開始から対応する終了語までを、内側の制御と配列を数えながら読み飛ばします。 */
    private void skipForbiddenArrayControl() {
      int controlDepth = 1;
      int nestedArrays = 0;
      while (!atEnd() && !abortCurrentDefinition && !isArrayHardRecoveryBoundary()) {
        TokenKind kind = current().token().kind();
        if (kind == TokenKind.ARRAY_OPEN) {
          nestedArrays++;
          advance();
          continue;
        }
        if (kind == TokenKind.ARRAY_CLOSE) {
          if (nestedArrays == 0) {
            return;
          }
          nestedArrays--;
          advance();
          continue;
        }
        if (nestedArrays == 0 && kind == TokenKind.ARRAY_SEPARATOR) {
          return;
        }
        if (nestedArrays == 0
            && (kind == TokenKind.CONDITIONAL_START
                || kind == TokenKind.COUNTED_LOOP_START
                || kind == TokenKind.CONDITION_LOOP_START
                || kind == TokenKind.ARRAY_LOOP_START)) {
          controlDepth++;
        } else if (nestedArrays == 0
            && (kind == TokenKind.CONDITIONAL_END || kind == TokenKind.LOOP_END)) {
          controlDepth--;
        }
        advance();
        if (controlDepth == 0) {
          return;
        }
      }
    }

    /** 配列終端不足時に、現在の外側構文へ制御を返す同期点かを判定します。 */
    private boolean isArrayRecoveryBoundary() {
      if (atEnd() || checkReserved("こと") || check(TokenKind.DECLARATION_END)) {
        return true;
      }
      return switch (current().token().kind()) {
        case CONDITIONAL_ELSE,
            CONDITIONAL_END,
            LOOP_CONDITION_SEPARATOR,
            LOOP_END,
            DEFINITION_END ->
            true;
        default -> isDefinitionPair(index);
      };
    }

    /** 制御回復中でも越えてはならない宣言・単語・ファイルの境界かを返します。 */
    private boolean isArrayHardRecoveryBoundary() {
      return atEnd()
          || checkReserved("こと")
          || check(TokenKind.DECLARATION_END)
          || check(TokenKind.DEFINITION_END)
          || isDefinitionPair(index);
    }

    /** スタック効果の単純型または型構築子を解析します。 */
    private TypeReference parseTypeReference() {
      return parseTypeReference(0);
    }

    /** 257段目へ再帰する前に拒否しながら、型構築子を内側へ解析します。 */
    private TypeReference parseTypeReference(int constructorDepth) {
      LocatedToken name = advance();
      boolean array = name.token().value().equals("配列");
      boolean optional = name.token().value().equals("任意");
      boolean result = name.token().value().equals("結果");
      if ((!array && !optional && !result) || !check(TokenKind.ARRAY_TYPE_OPEN)) {
        return new TypeReference(name.token().value(), name.token().lexeme(), name.token().span());
      }

      if (constructorDepth >= MAX_SYNTAX_DEPTH) {
        reportTypeDepthLimit(name.token());
        skipTypeReferenceTail();
        return null;
      }
      advance();
      if (result) {
        return parseResultTypeReference(name, constructorDepth);
      }
      skipTypeArgumentComments();
      if (atEnd() || check(TokenKind.ARRAY_TYPE_CLOSE)) {
        if (array) {
          reportExpectedArrayElementType();
        } else {
          reportExpectedOptionalElementType();
        }
        if (check(TokenKind.ARRAY_TYPE_CLOSE)) {
          advance();
        }
        return null;
      }
      if (!check(TokenKind.IDENTIFIER)) {
        reportExpectedArrayElementType();
        return null;
      }

      TypeReference typeArgument = parseTypeReference(constructorDepth + 1);
      if (typeArgument == null) {
        if (check(TokenKind.ARRAY_TYPE_CLOSE)) {
          advance();
        }
        return null;
      }
      SourceSpan typeSpan = span(name.token().span(), typeArgument.span());
      int typeEnd = utf16EndOf(typeArgument.span());
      if (!check(TokenKind.ARRAY_TYPE_CLOSE)) {
        if (array) {
          reportExpectedArrayTypeEnd(name.token(), typeArgument);
        } else {
          reportExpectedOptionalTypeEnd(name.token(), typeArgument);
        }
        return new TypeReference(
            name.token().value() + "<" + typeArgument.name() + ">",
            source.text().substring(name.start(), typeEnd),
            typeSpan,
            Optional.of(typeArgument));
      }

      LocatedToken end = advance();
      typeSpan = span(name.token().span(), end.token().span());
      return new TypeReference(
          name.token().value() + "<" + typeArgument.name() + ">",
          source.text().substring(name.start(), end.end()),
          typeSpan,
          Optional.of(typeArgument));
    }

    /** 開き山括弧を消費した結果型の成功型・失敗型と終端を解析します。 */
    private TypeReference parseResultTypeReference(LocatedToken owner, int constructorDepth) {
      skipTypeArgumentComments();
      if (atEnd() || check(TokenKind.ARRAY_TYPE_CLOSE) || check(TokenKind.RESULT_TYPE_SEPARATOR)) {
        reportResultSyntax(owner.token(), ResultSyntax.ARGUMENT, 1, null);
        finishResultEofIfNeeded();
        if (check(TokenKind.ARRAY_TYPE_CLOSE)) {
          advance();
        } else if (!atEnd()) {
          recoverResultFrame();
        }
        return null;
      }
      if (!check(TokenKind.IDENTIFIER)) {
        reportResultSyntax(owner.token(), ResultSyntax.ARGUMENT, 1, null);
        return null;
      }
      TypeReference successType = parseTypeReference(constructorDepth + 1);
      skipTypeArgumentComments();
      if (successType == null) {
        finishResultEofIfNeeded();
        if (!atEnd()) {
          recoverResultFrame();
        }
        return null;
      }
      if (!check(TokenKind.RESULT_TYPE_SEPARATOR)) {
        if (check(TokenKind.ARRAY_TYPE_CLOSE)) {
          reportResultSyntax(owner.token(), ResultSyntax.COUNT, 0, 1);
        } else if (atEnd()) {
          reportResultSyntax(owner.token(), ResultSyntax.END, 0, null);
        } else {
          reportResultSyntax(owner.token(), ResultSyntax.SEPARATOR, 0, null);
        }
        finishResultEofIfNeeded();
        if (check(TokenKind.ARRAY_TYPE_CLOSE)) {
          advance();
        } else if (!atEnd()) {
          recoverResultFrame();
        }
        return null;
      }
      advance();
      skipTypeArgumentComments();
      if (atEnd() || check(TokenKind.ARRAY_TYPE_CLOSE)) {
        reportResultSyntax(owner.token(), ResultSyntax.ARGUMENT, 2, null);
        finishResultEofIfNeeded();
        if (check(TokenKind.ARRAY_TYPE_CLOSE)) {
          advance();
        } else if (!atEnd()) {
          recoverResultFrame();
        }
        return null;
      }
      if (!check(TokenKind.IDENTIFIER)) {
        reportResultSyntax(owner.token(), ResultSyntax.ARGUMENT, 2, null);
        return null;
      }
      TypeReference failureType = parseTypeReference(constructorDepth + 1);
      skipTypeArgumentComments();
      if (failureType == null) {
        finishResultEofIfNeeded();
        if (!atEnd()) {
          recoverResultFrame();
        }
        return null;
      }
      if (check(TokenKind.RESULT_TYPE_SEPARATOR)) {
        reportResultSyntax(owner.token(), ResultSyntax.COUNT, 0, 3);
        recoverResultFrame();
        return null;
      }

      SourceSpan typeSpan = span(owner.token().span(), failureType.span());
      int typeEnd = utf16EndOf(failureType.span());
      if (!check(TokenKind.ARRAY_TYPE_CLOSE)) {
        reportResultSyntax(owner.token(), ResultSyntax.END, 0, null);
        finishResultEofIfNeeded();
        return new TypeReference(
            "結果<" + successType.name() + "," + failureType.name() + ">",
            source.text().substring(owner.start(), typeEnd),
            typeSpan,
            Optional.of(successType),
            Optional.of(failureType));
      }
      LocatedToken end = advance();
      return new TypeReference(
          "結果<" + successType.name() + "," + failureType.name() + ">",
          source.text().substring(owner.start(), end.end()),
          span(owner.token().span(), end.token().span()),
          Optional.of(successType),
          Optional.of(failureType));
    }

    private void skipTypeArgumentComments() {
      while (check(TokenKind.COMMENT)) {
        reportCommentNotAllowed(advance().token(), "型引数");
      }
    }

    private void finishResultEofIfNeeded() {
      if (atEnd()) {
        suppressEnclosingEofDiagnostics = true;
        abortCurrentDefinition = true;
      }
    }

    /** 壊れた結果型の現在の引数枠だけを閉じ、外側の宣言・定義へ回復します。 */
    private void recoverResultFrame() {
      int nested = 0;
      while (!atEnd() && !isArrayHardRecoveryBoundary()) {
        if (check(TokenKind.ARRAY_TYPE_OPEN)) {
          nested++;
          advance();
        } else if (check(TokenKind.ARRAY_TYPE_CLOSE)) {
          advance();
          if (nested == 0) {
            return;
          }
          nested--;
        } else {
          advance();
        }
      }
    }

    private void reportResultSyntax(
        Token owner, ResultSyntax kind, int argumentIndex, Integer actualCount) {
      var builder =
          Diagnostic.builder(
                  kind.code,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  currentPosition())
              .field("typeConstructor", "結果")
              .field("owner", owner.value());
      if (!owner.value().equals("結果")) {
        builder.field("word", owner.value());
      }
      switch (kind) {
        case ARGUMENT ->
            builder
                .field("argumentIndex", Integer.toString(argumentIndex))
                .expected("具体型")
                .actual(currentText())
                .fix("成功型と失敗型に具体型を指定してください");
        case SEPARATOR ->
            builder.expected(",").actual(currentText()).fix("成功型と失敗型をASCIIの,で区切ってください");
        case COUNT ->
            builder
                .field("expectedCount", "2")
                .field("actualCount", Integer.toString(actualCount))
                .expected("2個の型引数")
                .actual(actualCount == 1 ? "1個の型引数" : "3個以上の型引数")
                .fix("成功型と失敗型を1個ずつ指定してください");
        case END -> {
          SourcePosition start = owner.span().start();
          builder
              .field("startLine", Integer.toString(start.line()))
              .field("startColumn", Integer.toString(start.column()))
              .expected(">")
              .actual(currentText())
              .fix("型引数の末尾へ>を追加してください")
              .relatedLocation(new RelatedLocation(source.sourcePath(), start, owner.lexeme()));
        }
      }
      diagnostics.add(builder.build());
      syntaxError = true;
    }

    private enum ResultSyntax {
      ARGUMENT(DiagnosticCode.E_EXPECTED_RESULT_TYPE_ARGUMENT),
      SEPARATOR(DiagnosticCode.E_EXPECTED_RESULT_TYPE_SEPARATOR),
      COUNT(DiagnosticCode.E_RESULT_TYPE_ARGUMENT_COUNT),
      END(DiagnosticCode.E_EXPECTED_RESULT_TYPE_END);

      private final DiagnosticCode code;

      ResultSyntax(DiagnosticCode code) {
        this.code = code;
      }
    }

    /** 深さ超過した1個の型部分木だけを、Java再帰を使わず読み飛ばします。 */
    private void skipTypeReferenceTail() {
      int opens = 0;
      while (!atEnd()) {
        if (check(TokenKind.ARRAY_TYPE_OPEN)) {
          opens++;
          advance();
          continue;
        }
        if (check(TokenKind.ARRAY_TYPE_CLOSE)) {
          advance();
          if (opens == 0) {
            return;
          }
          opens--;
          if (opens == 0) {
            return;
          }
          continue;
        }
        if (opens == 0 && !check(TokenKind.IDENTIFIER)) {
          return;
        }
        advance();
      }
    }

    /** span終端のUTF-16位置を、単調なトークン表から取得します。 */
    private int utf16EndOf(SourceSpan target) {
      for (int cursor = index - 1; cursor >= 0; cursor--) {
        LocatedToken token = tokens.get(cursor);
        if (token.token().span().end().equals(target.end())) {
          return token.end();
        }
      }
      throw new IllegalStateException("a parsed type span has no source token");
    }

    /** 257段目なら、再帰メソッドやASTノードを作る前に現在の定義を打ち切ります。 */
    private boolean tryEnterControl() {
      if (depthGuard.tryEnter()) {
        return true;
      }
      Token opener = current().token();
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_SYNTAX_DEPTH_LIMIT,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  opener.span())
              .limit("syntaxDepth", MAX_SYNTAX_DEPTH, MAX_SYNTAX_DEPTH + 1)
              .expected(MAX_SYNTAX_DEPTH + "段以下")
              .actual((MAX_SYNTAX_DEPTH + 1) + "段")
              .fix("入れ子を減らしてください")
              .build());
      syntaxError = true;
      abortCurrentDefinition = true;
      return false;
    }

    /** スタック効果の括弧内を、区切り記号の前後にある型名リストへ分割します。 */
    private StackEffect parseStackEffect() {
      if (!depthGuard.tryEnter()) {
        throw new IllegalStateException("stack effect unexpectedly exceeded its nesting limit");
      }
      try {
        LocatedToken opening = advance();
        var inputs = new ArrayList<TypeReference>();
        var outputs = new ArrayList<TypeReference>();
        boolean separatorSeen = false;

        while (!atEnd()) {
          LocatedToken current = current();
          Token token = current.token();
          if (token.kind() == TokenKind.COMMENT) {
            reportCommentNotAllowed(token, "スタック効果の外のコメント");
            advance();
            continue;
          }
          if (token.kind() == TokenKind.STACK_SEPARATOR) {
            separatorSeen = true;
            advance();
            continue;
          }
          if (isStackClose(token.kind())) {
            LocatedToken closing = advance();
            if (!separatorSeen) {
              diagnostics.add(
                  Diagnostic.builder(
                          DiagnosticCode.E_EXPECTED_STACK_SEPARATOR,
                          Severity.ERROR,
                          DiagnosticStage.SYNTAX,
                          source.sourcePath(),
                          closing.token().span())
                      .expected("--")
                      .actual(closing.token().lexeme())
                      .fix("ここに -- を追加してください")
                      .build());
              syntaxError = true;
            }
            reportMismatchedParenthesis(opening.token(), closing.token());
            return new StackEffect(
                inputs, outputs, span(opening.token().span(), closing.token().span()));
          }
          if (token.kind() == TokenKind.IDENTIFIER) {
            TypeReference type = parseTypeReference();
            if (type != null) {
              (separatorSeen ? outputs : inputs).add(type);
            }
            continue;
          }

          // 言語コアのスタック効果に現れ得ないトークンでは、現在位置を挿入候補として診断する。
          diagnostics.add(
              Diagnostic.builder(
                      DiagnosticCode.E_EXPECTED_STACK_SEPARATOR,
                      Severity.ERROR,
                      DiagnosticStage.SYNTAX,
                      source.sourcePath(),
                      token.span())
                  .expected("--")
                  .actual(token.lexeme())
                  .fix("スタック効果を修正してください")
                  .build());
          syntaxError = true;
          advance();
        }

        if (suppressEnclosingEofDiagnostics) {
          return new StackEffect(inputs, outputs, span(opening.token().span(), eofSpan()));
        }
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_EXPECTED_STACK_SEPARATOR,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    eofPosition())
                .expected("-- と閉じ括弧")
                .actual("EOF")
                .fix("スタック効果を閉じてください")
                .build());
        syntaxError = true;
        SourceSpan empty = span(opening.token().span(), eofSpan());
        return new StackEffect(inputs, outputs, empty);
      } finally {
        depthGuard.exit();
      }
    }

    /** 「こと」と句点の隣接条件を確認し、完成した単語定義を返します。 */
    private WordDefinition finishDefinition(
        Token name, StackEffect stackEffect, List<BodyElement> body, LocatedToken wordEnd) {
      LocatedToken mark = peek(1);
      if (mark != null && mark.token().kind() == TokenKind.DEFINITION_END) {
        String gap = source.text().substring(wordEnd.end(), mark.start());
        if (isHorizontalSpaceGap(gap)) {
          advance();
          advance();
          SourceSpan endSpan = span(wordEnd.token().span(), mark.token().span());
          return new WordDefinition(
              name.value(),
              name.lexeme(),
              name.span(),
              stackEffect,
              body,
              endSpan,
              span(name.span(), mark.token().span()));
        }
      }

      String actual = mark == null ? "EOF" : mark.token().lexeme();
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_EXPECTED_DEFINITION_END_MARK,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  wordEnd.token().span())
              .expected("。")
              .actual(actual)
              .fix("ことの直後に。を置いてください")
              .build());
      syntaxError = true;
      advance();
      return null;
    }

    /** 対応する開始語がない中間語・終了語を、その語の位置で報告します。 */
    private void reportUnexpectedControlWord(Token token) {
      var builder =
          switch (token.kind()) {
            case CONDITIONAL_ELSE ->
                Diagnostic.builder(
                        DiagnosticCode.E_UNEXPECTED_ELSE,
                        Severity.ERROR,
                        DiagnosticStage.SYNTAX,
                        source.sourcePath(),
                        token.span())
                    .expected("ならばの内側")
                    .actual(token.lexeme())
                    .fix("この語を削除してください");
            case CONDITIONAL_END ->
                Diagnostic.builder(
                        DiagnosticCode.E_UNEXPECTED_BLOCK_END,
                        Severity.ERROR,
                        DiagnosticStage.SYNTAX,
                        source.sourcePath(),
                        token.span())
                    .expected("ならばの内側")
                    .actual(token.lexeme())
                    .fix("この語を削除してください");
            case LOOP_END ->
                Diagnostic.builder(
                        DiagnosticCode.E_UNEXPECTED_LOOP_END,
                        Severity.ERROR,
                        DiagnosticStage.SYNTAX,
                        source.sourcePath(),
                        token.span())
                    .expected("ループの内側")
                    .actual(token.lexeme())
                    .fix("この語を削除してください");
            case LOOP_CONDITION_SEPARATOR ->
                Diagnostic.builder(
                        DiagnosticCode.E_UNEXPECTED_LOOP_SEPARATOR,
                        Severity.ERROR,
                        DiagnosticStage.SYNTAX,
                        source.sourcePath(),
                        token.span())
                    .expected("条件ループの内側")
                    .actual(token.lexeme())
                    .fix("この語を削除してください");
            default ->
                throw new IllegalArgumentException(
                    "token is not a control boundary: " + token.kind());
          };
      diagnostics.add(builder.build());
      syntaxError = true;
    }

    /** 同じ条件分岐に現れた2個目以降の {@code さもなければ} を報告します。 */
    private void reportDuplicateElse(Token duplicate, SourceSpan first) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_DUPLICATE_ELSE,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  duplicate.span())
              .field("firstLine", Integer.toString(first.start().line()))
              .field("firstColumn", Integer.toString(first.start().column()))
              .expected("さもなければは1個まで")
              .actual("2個目のさもなければ")
              .fix("この語を削除してください")
              .relatedLocation(new RelatedLocation(source.sourcePath(), first.start(), "最初のさもなければ"))
              .build());
      syntaxError = true;
    }

    /** 同じ条件ループに現れた2個目以降の {@code 続く間} を報告します。 */
    private void reportDuplicateLoopSeparator(Token duplicate, SourceSpan first) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_UNEXPECTED_LOOP_SEPARATOR,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  duplicate.span())
              .field("firstLine", Integer.toString(first.start().line()))
              .field("firstColumn", Integer.toString(first.start().column()))
              .expected("続く間は1個")
              .actual("2個目の続く間")
              .fix("この語を削除してください")
              .relatedLocation(new RelatedLocation(source.sourcePath(), first.start(), "最初の続く間"))
              .build());
      syntaxError = true;
    }

    /** 条件分岐の終了語が欠けた場合に、開始位置を伴う構造化診断を作ります。 */
    private void reportExpectedIfEnd(Token opening) {
      SourcePosition position = opening.span().start();
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_EXPECTED_IF_END,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  currentPosition())
              .field("openingLine", Integer.toString(position.line()))
              .field("openingColumn", Integer.toString(position.column()))
              .expected("つぎに")
              .actual(currentText())
              .fix("ここにつぎにを追加してください")
              .relatedLocation(new RelatedLocation(source.sourcePath(), position, opening.lexeme()))
              .build());
      syntaxError = true;
    }

    /** 短絡評価ブロックの終了語不足を開始位置と演算子つきで報告します。 */
    private void reportExpectedShortCircuitEnd(Token opening) {
      SourcePosition position = opening.span().start();
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_EXPECTED_SHORT_CIRCUIT_END,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  currentPosition())
              .field("operator", opening.lexeme())
              .field("openingLine", Integer.toString(position.line()))
              .field("openingColumn", Integer.toString(position.column()))
              .expected("つぎに")
              .actual(currentText())
              .fix("ここにつぎにを追加してください")
              .relatedLocation(new RelatedLocation(source.sourcePath(), position, opening.lexeme()))
              .build());
      syntaxError = true;
    }

    /** ループの終了語が欠けた場合に、種類と開始位置を伴う構造化診断を作ります。 */
    private void reportExpectedLoopEnd(Token opening, String loopKind) {
      SourcePosition position = opening.span().start();
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_EXPECTED_LOOP_END,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  currentPosition())
              .field("loopKind", loopKind)
              .field("openingLine", Integer.toString(position.line()))
              .field("openingColumn", Integer.toString(position.column()))
              .expected("繰り返す")
              .actual(currentText())
              .fix("ここに繰り返すを追加してください")
              .relatedLocation(new RelatedLocation(source.sourcePath(), position, opening.lexeme()))
              .build());
      syntaxError = true;
    }

    /** 配列反復の終了語不足を、配列の構造化フィールド名で報告します。 */
    private void reportExpectedArrayLoopEnd(Token opening) {
      SourcePosition position = opening.span().start();
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_EXPECTED_LOOP_END,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  currentPosition())
              .field("loopKind", "配列")
              .field("startLine", Integer.toString(position.line()))
              .field("startColumn", Integer.toString(position.column()))
              .expected("繰り返す")
              .actual(arrayBoundaryText())
              .fix("ここに「繰り返す」を追加してください")
              .relatedLocation(new RelatedLocation(source.sourcePath(), position, opening.lexeme()))
              .build());
      syntaxError = true;
    }

    /** 空の配列要素を、先頭・連続・末尾に応じた修正候補とともに報告します。 */
    private void reportExpectedArrayElement(
        Token opening, Token actual, int elementIndex, ArrayElementGap gap) {
      SourcePosition start = opening.span().start();
      String fix =
          switch (gap) {
            case LEADING -> "区切りの前に要素を追加してください";
            case CONSECUTIVE -> "重複する区切りを削除してください";
            case TRAILING -> "末尾の区切りを削除してください";
          };
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_EXPECTED_ARRAY_ELEMENT,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  actual.span())
              .field("elementIndex", Integer.toString(elementIndex))
              .field("startLine", Integer.toString(start.line()))
              .field("startColumn", Integer.toString(start.column()))
              .expected("配列要素")
              .actual(actual.lexeme())
              .fix(fix)
              .relatedLocation(new RelatedLocation(source.sourcePath(), start, opening.lexeme()))
              .build());
      syntaxError = true;
    }

    /** 配列リテラルの終了記号不足を現在の外側構文境界で報告します。 */
    private void reportExpectedArrayEnd(Token opening) {
      SourcePosition start = opening.span().start();
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_EXPECTED_ARRAY_END,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  currentPosition())
              .field("startLine", Integer.toString(start.line()))
              .field("startColumn", Integer.toString(start.column()))
              .expected("】")
              .actual(arrayBoundaryText())
              .fix("ここに「】」を追加してください")
              .relatedLocation(new RelatedLocation(source.sourcePath(), start, opening.lexeme()))
              .build());
      syntaxError = true;
    }

    /** 対応する開始記号がない配列終了記号を報告します。 */
    private void reportUnexpectedArrayEnd(Token end) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_UNEXPECTED_ARRAY_END,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  end.span())
              .field("actual", end.lexeme())
              .expected("配列の内側")
              .actual(end.lexeme())
              .fix("この記号を削除してください")
              .build());
      syntaxError = true;
    }

    /** 配列型の空または不正な型引数位置を報告します。 */
    private void reportExpectedArrayElementType() {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_EXPECTED_ARRAY_ELEMENT_TYPE,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  currentPosition())
              .field("allowedTypes", "整数,真偽,文字,文字列")
              .expected("要素型")
              .actual(currentText())
              .fix("4種類の要素型から1つ追加してください")
              .build());
      syntaxError = true;
    }

    /** 任意型の空または不正な型引数位置を報告します。 */
    private void reportExpectedOptionalElementType() {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_EXPECTED_OPTIONAL_ELEMENT_TYPE,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  currentPosition())
              .field("typeConstructor", "任意")
              .expected("具体型")
              .actual(currentText())
              .fix("任意<JSON>のように具体型を追加してください")
              .build());
      syntaxError = true;
    }

    /** 配列型の閉じ記号不足を、配列名と要素型の位置を保持して報告します。 */
    private void reportExpectedArrayTypeEnd(Token arrayName, TypeReference elementType) {
      SourcePosition start = arrayName.span().start();
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_EXPECTED_ARRAY_TYPE_END,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  currentPosition())
              .field("startLine", Integer.toString(start.line()))
              .field("startColumn", Integer.toString(start.column()))
              .expected(">")
              .actual(currentText())
              .fix("「" + elementType.lexeme() + "」の後に「>」を追加してください")
              .relatedLocation(new RelatedLocation(source.sourcePath(), start, arrayName.lexeme()))
              .build());
      syntaxError = true;
    }

    /** 任意型の閉じ記号不足を、構築子開始位置とともに報告します。 */
    private void reportExpectedOptionalTypeEnd(Token optionalName, TypeReference elementType) {
      SourcePosition start = optionalName.span().start();
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_EXPECTED_OPTIONAL_TYPE_END,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  currentPosition())
              .field("typeConstructor", "任意")
              .field("startLine", Integer.toString(start.line()))
              .field("startColumn", Integer.toString(start.column()))
              .expected(">")
              .actual(currentText())
              .fix("型引数の末尾へ>を追加してください")
              .relatedLocation(
                  new RelatedLocation(source.sourcePath(), start, optionalName.lexeme()))
              .build());
      syntaxError = true;
    }

    /** 型構築子の257段目を、ASTへ入る前の開始名位置で報告します。 */
    private void reportTypeDepthLimit(Token constructor) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_SYNTAX_DEPTH_LIMIT,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  constructor.span())
              .limit("syntaxDepth", MAX_SYNTAX_DEPTH, MAX_SYNTAX_DEPTH + 1)
              .expected(MAX_SYNTAX_DEPTH + "段以下")
              .actual((MAX_SYNTAX_DEPTH + 1) + "段")
              .fix("型構築子の入れ子を減らしてください")
              .build());
      syntaxError = true;
    }

    /** 配列要素式で禁止された最初の構文要素を報告します。 */
    private void reportArrayElementNotAllowed(
        Token opening, Token forbidden, int elementIndex, String element) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_ARRAY_ELEMENT_NOT_ALLOWED,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  forbidden.span())
              .field("elementIndex", Integer.toString(elementIndex))
              .field("element", element)
              .expected("配列要素式で許可された要素")
              .actual(forbidden.lexeme())
              .fix(element.equals("ならば") ? "条件分岐を配列リテラルの外へ移動してください" : "この要素を配列リテラルの外へ移動してください")
              .build());
      syntaxError = true;
    }

    /** 「こと。」だけは2トークンを利用者向けに1個の外側境界として表示します。 */
    private String arrayBoundaryText() {
      if (checkReserved("こと") && tokenKindAt(index + 1) == TokenKind.DEFINITION_END) {
        return "こと。";
      }
      return currentText();
    }

    /** 条件ループの中間語が欠けた場合は、派生する終了語不足を生成せず1件だけ報告します。 */
    private void reportExpectedLoopConditionSeparator(Token opening) {
      SourcePosition position = opening.span().start();
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_EXPECTED_LOOP_CONDITION_SEPARATOR,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  currentPosition())
              .field("openingLine", Integer.toString(position.line()))
              .field("openingColumn", Integer.toString(position.column()))
              .expected("続く間")
              .actual(currentText())
              .fix("ここに続く間を追加してください")
              .relatedLocation(new RelatedLocation(source.sourcePath(), position, "ここから"))
              .build());
      syntaxError = true;
    }

    private void reportMismatchedParenthesis(Token opening, Token closing) {
      TokenKind expected =
          opening.kind() == TokenKind.STACK_OPEN_FULLWIDTH
              ? TokenKind.STACK_CLOSE_FULLWIDTH
              : TokenKind.STACK_CLOSE_ASCII;
      if (closing.kind() == expected) {
        return;
      }
      String expectedText = expected == TokenKind.STACK_CLOSE_FULLWIDTH ? "）" : ")";
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_MIXED_STACK_PARENTHESES,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  closing.span())
              .expected(expectedText)
              .actual(closing.lexeme())
              .fix("括弧の種類をそろえてください")
              .build());
      syntaxError = true;
    }

    private void reportCommentNotAllowed(Token comment, String expected) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_COMMENT_NOT_ALLOWED,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  comment.span())
              .expected(expected)
              .actual(comment.lexeme())
              .fix("コメントを次の行へ移してください")
              .build());
      syntaxError = true;
    }

    private void reportExpectedWordEnd(
        String word, jp.bsb.diagnostics.DiagnosticLocation location, String actual) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_EXPECTED_WORD_END,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  location)
              .field("word", word)
              .expected("こと。")
              .actual(actual)
              .fix("ここにこと。を追加してください")
              .build());
      syntaxError = true;
    }

    private void reportUnexpectedTopLevel(LocatedToken token) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_UNEXPECTED_TOP_LEVEL,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  token.token().span())
              .expected("単語定義")
              .actual(token.token().lexeme())
              .fix("単語定義の本体へ移してください")
              .build());
      syntaxError = true;
    }

    /** 対応する宣言がない句点を専用診断として報告します。 */
    private void reportUnexpectedDeclarationEnd(Token token) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_UNEXPECTED_DECLARATION_END,
                  Severity.ERROR,
                  DiagnosticStage.SYNTAX,
                  source.sourcePath(),
                  token.span())
              .expected("宣言の内側")
              .actual(token.lexeme())
              .fix("この記号を削除してください")
              .build());
      syntaxError = true;
    }

    /** 次の定義開始か、現在の定義終端を越えた直後までカーソルを進めます。 */
    private void synchronizeAtTopLevel() {
      while (!atEnd()) {
        if (isDefinitionPair(index) || isDeclarationPair(index)) {
          return;
        }
        if (check(TokenKind.DECLARATION_END)) {
          advance();
          return;
        }
        if (checkReserved("こと")) {
          advance();
          if (check(TokenKind.DEFINITION_END)) {
            advance();
          }
          return;
        }
        advance();
      }
    }

    private boolean isDefinitionPair(int candidateIndex) {
      LocatedToken name = tokenAt(candidateIndex);
      LocatedToken marker = tokenAt(candidateIndex + 1);
      return name != null
          && marker != null
          && name.token().kind() == TokenKind.IDENTIFIER
          && marker.token().kind() == TokenKind.RESERVED_SYNTAX
          && marker.token().value().equals("とは");
    }

    private boolean isDeclarationPair(int candidateIndex) {
      LocatedToken name = tokenAt(candidateIndex);
      LocatedToken marker = tokenAt(candidateIndex + 1);
      return name != null
          && marker != null
          && name.token().kind() == TokenKind.IDENTIFIER
          && marker.token().kind() == TokenKind.DECLARATION_MARKER;
    }

    private boolean logicalConnectionDeclarationFollows(int candidateIndex) {
      int cursor = candidateIndex + 2;
      while (tokenKindAt(cursor) == TokenKind.COMMENT) {
        cursor++;
      }
      return tokenKindAt(cursor) == TokenKind.LOGICAL_CONNECTION_DECLARATION;
    }

    private boolean workspaceDeclarationFollows(int candidateIndex) {
      int cursor = candidateIndex + 2;
      while (tokenKindAt(cursor) == TokenKind.COMMENT) cursor++;
      return tokenKindAt(cursor) == TokenKind.WORKSPACE_DECLARATION;
    }

    private boolean isReservedAt(int candidateIndex, String value) {
      LocatedToken token = tokenAt(candidateIndex);
      return token != null
          && token.token().kind() == TokenKind.RESERVED_SYNTAX
          && token.token().value().equals(value);
    }

    private TokenKind tokenKindAt(int candidateIndex) {
      LocatedToken token = tokenAt(candidateIndex);
      return token == null ? null : token.token().kind();
    }

    private boolean checkParticleAt(int candidateIndex, String value) {
      LocatedToken token = tokenAt(candidateIndex);
      return token != null
          && token.token().kind() == TokenKind.PARTICLE
          && token.token().value().equals(value);
    }

    private boolean checkReserved(String value) {
      return !atEnd()
          && current().token().kind() == TokenKind.RESERVED_SYNTAX
          && current().token().value().equals(value);
    }

    private boolean check(TokenKind kind) {
      return !atEnd() && current().token().kind() == kind;
    }

    private LocatedToken advance() {
      LocatedToken current = current();
      index++;
      return current;
    }

    private LocatedToken current() {
      return tokens.get(index);
    }

    private LocatedToken peek(int distance) {
      return tokenAt(index + distance);
    }

    private LocatedToken tokenAt(int requestedIndex) {
      if (requestedIndex < 0 || requestedIndex >= tokens.size()) {
        return null;
      }
      return tokens.get(requestedIndex);
    }

    private boolean atEnd() {
      return index >= tokens.size();
    }

    private SourcePosition currentPosition() {
      return atEnd() ? eofPosition() : current().token().span().start();
    }

    private String currentText() {
      return atEnd() ? "EOF" : current().token().lexeme();
    }

    private SourcePosition eofPosition() {
      return source.positionAt(source.text().length());
    }

    private SourceSpan eofSpan() {
      SourcePosition eof = eofPosition();
      return new SourceSpan(eof, eof);
    }

    /** 値参照または呼出しを作り、結果構築型引数と論理接続引数を同時に解析します。 */
    private BodyElement nameUse(LocatedToken located, Set<String> valueNames) {
      Token token = located.token();
      if (valueNames.contains(token.value())) {
        return new ValueReference(token.value(), token.lexeme(), token.span());
      }
      if (token.value().equals("成功にする") || token.value().equals("失敗にする")) {
        while (check(TokenKind.COMMENT)) {
          reportCommentNotAllowed(advance().token(), "型引数開始の<");
        }
        if (check(TokenKind.ARRAY_TYPE_OPEN)) {
          advance();
          TypeReference result = parseResultTypeReference(located, 0);
          if (result != null) {
            return new WordCall(
                token.value(),
                token.lexeme(),
                span(token.span(), result.span()),
                List.of(
                    result.typeArgument().orElseThrow(),
                    result.secondTypeArgument().orElseThrow()));
          }
        }
      }
      if (token.value().equals("論理接続を確認する")) {
        return parseLogicalConnectionCall(located);
      }
      if (token.value().equals("HTTP要求を送信する")) {
        return parseHttpSendCall(located);
      }
      if (token.value().equals("ファイルを読む") || token.value().equals("ファイルへ書く")) {
        return parseWorkspaceCall(located);
      }
      return new WordCall(token.value(), token.lexeme(), token.span());
    }

    /** ファイル語に必須の静的作業領域引数1個を解析します。 */
    private WordCall parseWorkspaceCall(LocatedToken word) {
      Token token = word.token();
      if (atEnd() || !check(TokenKind.ARRAY_TYPE_OPEN) || word.end() != current().start()) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_EXPECTED_WORKSPACE_ARGUMENT_START,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    token.span())
                .field("word", token.value())
                .expected("<")
                .actual(currentText())
                .fix("単語の直後に「<作業領域名>」を追加してください")
                .build());
        syntaxError = true;
        if (check(TokenKind.ARRAY_TYPE_OPEN)) synchronizeLogicalConnectionArgument();
        return new WordCall(token.value(), token.lexeme(), token.span());
      }
      advance();
      if (atEnd() || !check(TokenKind.IDENTIFIER)) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_EXPECTED_WORKSPACE_ARGUMENT,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    currentPosition())
                .field("word", token.value())
                .expected("作業領域名")
                .actual(currentText())
                .fix("第1引数に作業領域名を追加してください")
                .build());
        syntaxError = true;
        synchronizeLogicalConnectionArgument();
        return new WordCall(token.value(), token.lexeme(), token.span());
      }
      Token workspace = advance().token();
      int count = 1;
      while (check(TokenKind.RESULT_TYPE_SEPARATOR)) {
        advance();
        if (check(TokenKind.IDENTIFIER)) {
          count++;
          advance();
        } else break;
      }
      if (count != 1) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_WORKSPACE_ARGUMENT_COUNT,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    token.span())
                .field("word", token.value())
                .field("expectedCount", "1")
                .field("actualCount", Integer.toString(count))
                .expected("1個の作業領域引数")
                .actual(count + "個の作業領域引数")
                .fix("作業領域引数を1個にしてください")
                .build());
        syntaxError = true;
        synchronizeLogicalConnectionArgument();
        return new WordCall(token.value(), token.lexeme(), token.span());
      }
      if (!check(TokenKind.ARRAY_TYPE_CLOSE)) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_EXPECTED_WORKSPACE_ARGUMENT_END,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    token.span())
                .field("word", token.value())
                .field("workspace", workspace.value())
                .expected(">")
                .actual(currentText())
                .fix("作業領域引数の末尾に「>」を追加してください")
                .build());
        syntaxError = true;
        if (atEnd()) suppressEnclosingEofDiagnostics = true;
        synchronizeLogicalConnectionArgument();
        return new WordCall(token.value(), token.lexeme(), token.span());
      }
      Token end = advance().token();
      return new WordCall(
          token.value(),
          token.lexeme(),
          span(token.span(), end.span()),
          new WorkspaceArgument(workspace.value(), workspace.lexeme(), workspace.span()));
    }

    /** HTTP送信語に必須の静的論理接続名とmethodを解析します。 */
    private WordCall parseHttpSendCall(LocatedToken word) {
      Token token = word.token();
      if (atEnd() || !check(TokenKind.ARRAY_TYPE_OPEN) || word.end() != current().start()) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_EXPECTED_HTTP_ARGUMENT_START,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    token.span())
                .field("word", token.value())
                .expected("<")
                .actual(currentText())
                .fix("単語の直後に「<論理接続名,method>」を追加してください")
                .build());
        syntaxError = true;
        if (check(TokenKind.ARRAY_TYPE_OPEN)) synchronizeLogicalConnectionArgument();
        return new WordCall(token.value(), token.lexeme(), token.span());
      }
      advance();
      if (atEnd() || !check(TokenKind.IDENTIFIER)) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_EXPECTED_HTTP_CONNECTION_ARGUMENT,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    currentPosition())
                .field("word", token.value())
                .field("argumentIndex", "1")
                .expected("論理接続名")
                .actual(currentText())
                .fix("第1引数に論理接続名を追加してください")
                .build());
        syntaxError = true;
        synchronizeLogicalConnectionArgument();
        return new WordCall(token.value(), token.lexeme(), token.span());
      }
      Token connection = advance().token();
      if (!check(TokenKind.RESULT_TYPE_SEPARATOR)) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_EXPECTED_HTTP_METHOD_ARGUMENT,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    currentPosition())
                .field("word", token.value())
                .field("argumentIndex", "2")
                .expected("ASCIIカンマに続くHTTP method")
                .actual(currentText())
                .fix("第2引数にmethodを追加してください")
                .build());
        syntaxError = true;
        synchronizeLogicalConnectionArgument();
        return new WordCall(token.value(), token.lexeme(), token.span());
      }
      advance();
      if (atEnd() || !check(TokenKind.IDENTIFIER)) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_EXPECTED_HTTP_METHOD_ARGUMENT,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    currentPosition())
                .field("word", token.value())
                .field("argumentIndex", "2")
                .expected("HTTP method")
                .actual(currentText())
                .fix("第2引数にmethodを追加してください")
                .build());
        syntaxError = true;
        synchronizeLogicalConnectionArgument();
        return new WordCall(token.value(), token.lexeme(), token.span());
      }
      Token method = advance().token();
      int count = 2;
      while (check(TokenKind.RESULT_TYPE_SEPARATOR)) {
        advance();
        if (check(TokenKind.IDENTIFIER)) {
          count++;
          advance();
        } else {
          break;
        }
      }
      if (count != 2) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_HTTP_ARGUMENT_COUNT,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    token.span())
                .field("word", token.value())
                .field("expectedCount", "2")
                .field("actualCount", Integer.toString(count))
                .expected("2個の静的引数")
                .actual(count + "個の静的引数")
                .fix("静的引数を接続名とmethodの2個にしてください")
                .build());
        syntaxError = true;
        synchronizeLogicalConnectionArgument();
        return new WordCall(token.value(), token.lexeme(), token.span());
      }
      if (!check(TokenKind.ARRAY_TYPE_CLOSE)) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_EXPECTED_HTTP_ARGUMENT_END,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    token.span())
                .field("word", token.value())
                .field("connection", connection.value())
                .field("method", method.value())
                .expected(">")
                .actual(currentText())
                .fix("静的引数の末尾に「>」を追加してください")
                .build());
        syntaxError = true;
        if (atEnd()) suppressEnclosingEofDiagnostics = true;
        synchronizeLogicalConnectionArgument();
        return new WordCall(token.value(), token.lexeme(), token.span());
      }
      Token end = advance().token();
      if (!Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE").contains(method.value())) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_HTTP_METHOD_INVALID,
                    Severity.ERROR,
                    DiagnosticStage.NAME,
                    source.sourcePath(),
                    method.span())
                .field("word", token.value())
                .field("method", method.value())
                .field("allowedMethods", "GET,HEAD,POST,PUT,PATCH,DELETE")
                .expected("GET, HEAD, POST, PUT, PATCH, DELETE")
                .actual(method.lexeme())
                .fix("methodを許可されたASCII大文字へ変更してください")
                .build());
        syntaxError = true;
        return new WordCall(token.value(), token.lexeme(), span(token.span(), end.span()));
      }
      return new WordCall(
          token.value(),
          token.lexeme(),
          span(token.span(), end.span()),
          new LogicalConnectionArgument(
              connection.value(),
              connection.lexeme(),
              connection.span(),
              new HttpMethodArgument(method.value(), method.lexeme(), method.span())));
    }

    /** 確認語に必須の静的論理接続引数1個を解析します。 */
    private WordCall parseLogicalConnectionCall(LocatedToken word) {
      Token token = word.token();
      if (atEnd() || !check(TokenKind.ARRAY_TYPE_OPEN) || word.end() != current().start()) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT_START,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    token.span())
                .field("word", token.value())
                .expected("<")
                .actual(currentText())
                .fix("単語の直後に「<論理接続名>」を追加してください")
                .build());
        syntaxError = true;
        if (check(TokenKind.ARRAY_TYPE_OPEN)) {
          synchronizeLogicalConnectionArgument();
        }
        return new WordCall(token.value(), token.lexeme(), token.span());
      }
      advance();
      if (atEnd() || !check(TokenKind.IDENTIFIER)) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    currentPosition())
                .field("word", token.value())
                .field("argumentIndex", "1")
                .expected("論理接続名")
                .actual(currentText())
                .fix("第1引数に論理接続名を追加してください")
                .build());
        syntaxError = true;
        synchronizeLogicalConnectionArgument();
        return new WordCall(token.value(), token.lexeme(), token.span());
      }
      Token connection = advance().token();
      int count = 1;
      while (check(TokenKind.RESULT_TYPE_SEPARATOR)) {
        advance();
        if (check(TokenKind.IDENTIFIER)) {
          count++;
          advance();
        } else {
          break;
        }
      }
      if (count != 1) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_LOGICAL_CONNECTION_ARGUMENT_COUNT,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    token.span())
                .field("word", token.value())
                .field("expectedCount", "1")
                .field("actualCount", Integer.toString(count))
                .expected("1個の論理接続引数")
                .actual(count + "個の論理接続引数")
                .fix("論理接続引数を1個にしてください")
                .build());
        syntaxError = true;
        synchronizeLogicalConnectionArgument();
        return new WordCall(token.value(), token.lexeme(), token.span());
      }
      if (!check(TokenKind.ARRAY_TYPE_CLOSE)) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_EXPECTED_LOGICAL_CONNECTION_ARGUMENT_END,
                    Severity.ERROR,
                    DiagnosticStage.SYNTAX,
                    source.sourcePath(),
                    token.span())
                .field("word", token.value())
                .field("connection", connection.value())
                .expected(">")
                .actual(currentText())
                .fix("論理接続引数の末尾に「>」を追加してください")
                .build());
        syntaxError = true;
        if (atEnd()) {
          suppressEnclosingEofDiagnostics = true;
        }
        synchronizeLogicalConnectionArgument();
        return new WordCall(token.value(), token.lexeme(), token.span());
      }
      Token end = advance().token();
      return new WordCall(
          token.value(),
          token.lexeme(),
          span(token.span(), end.span()),
          new LogicalConnectionArgument(
              connection.value(), connection.lexeme(), connection.span()));
    }

    private void synchronizeLogicalConnectionArgument() {
      int nested = 0;
      while (!atEnd()) {
        if (check(TokenKind.ARRAY_TYPE_OPEN)) {
          nested++;
          advance();
        } else if (check(TokenKind.ARRAY_TYPE_CLOSE)) {
          advance();
          if (nested == 0) {
            return;
          }
          nested--;
        } else if (nested == 0
            && (check(TokenKind.DECLARATION_END)
                || check(TokenKind.DEFINITION_END)
                || checkReserved("こと"))) {
          return;
        } else {
          advance();
        }
      }
    }

    /** 空配列要素が現れた区切り位置です。 */
    private enum ArrayElementGap {
      LEADING,
      CONSECUTIVE,
      TRAILING
    }
  }

  private static boolean isStackOpen(TokenKind kind) {
    return kind == TokenKind.STACK_OPEN_FULLWIDTH || kind == TokenKind.STACK_OPEN_ASCII;
  }

  private static boolean isStackClose(TokenKind kind) {
    return kind == TokenKind.STACK_CLOSE_FULLWIDTH || kind == TokenKind.STACK_CLOSE_ASCII;
  }

  private static boolean isSeparatorGap(String gap) {
    if (gap.isEmpty()) {
      return false;
    }
    return gap.codePoints()
        .allMatch(
            codePoint ->
                codePoint == ' '
                    || codePoint == '\t'
                    || codePoint == '\r'
                    || codePoint == '\n'
                    || codePoint == 0x3000
                    || codePoint == '、'
                    || codePoint == '，');
  }

  private static boolean isHorizontalSpaceGap(String gap) {
    return gap.codePoints()
        .allMatch(codePoint -> codePoint == ' ' || codePoint == '\t' || codePoint == 0x3000);
  }

  private static Comment comment(Token token) {
    return new Comment(token.value(), token.span());
  }

  private static Literal literal(Token token) {
    LiteralKind kind =
        switch (token.kind()) {
          case INTEGER_LITERAL -> LiteralKind.INTEGER;
          case DECIMAL_LITERAL -> LiteralKind.DECIMAL;
          case BOOLEAN_LITERAL -> LiteralKind.BOOLEAN;
          case CHARACTER_LITERAL -> LiteralKind.CHARACTER;
          case STRING_LITERAL -> LiteralKind.STRING;
          case REGEX_LITERAL -> LiteralKind.REGEX;
          default -> throw new IllegalArgumentException("token is not a literal: " + token.kind());
        };
    return new Literal(kind, token.lexeme(), token.value(), token.span(), token.regexMetadata());
  }

  /** 制御移行用トークンを、通常の単語呼出しではなく専用ASTノードへ変換します。 */
  private static ControlTransfer controlTransfer(Token token) {
    ControlTransfer.Kind kind =
        switch (token.kind()) {
          case BREAK -> ControlTransfer.Kind.BREAK;
          case CONTINUE -> ControlTransfer.Kind.CONTINUE;
          case RETURN -> ControlTransfer.Kind.RETURN;
          default ->
              throw new IllegalArgumentException(
                  "token is not a control transfer: " + token.kind());
        };
    return new ControlTransfer(kind, token.lexeme(), token.span());
  }

  private static SourceSpan span(SourceSpan first, SourceSpan last) {
    return new SourceSpan(first.start(), last.end());
  }

  /** トークンと元ソース上のUTF-16半開区間を組にした、構文解析中だけの内部表現です。 */
  private record LocatedToken(Token token, int start, int end) {}
}
