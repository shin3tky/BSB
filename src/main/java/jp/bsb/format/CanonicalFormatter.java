package jp.bsb.format;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import jp.bsb.frontend.UnicodeRules;
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
import jp.bsb.frontend.ast.Literal;
import jp.bsb.frontend.ast.LogicalConnectionDeclaration;
import jp.bsb.frontend.ast.Particle;
import jp.bsb.frontend.ast.Program;
import jp.bsb.frontend.ast.StackEffect;
import jp.bsb.frontend.ast.TopLevelElement;
import jp.bsb.frontend.ast.TypeReference;
import jp.bsb.frontend.ast.ValueDeclaration;
import jp.bsb.frontend.ast.ValueReference;
import jp.bsb.frontend.ast.WordCall;
import jp.bsb.frontend.ast.WordDefinition;
import jp.bsb.frontend.ast.WorkspaceDeclaration;

/**
 * 構文的に有効なASTを、ホスト入出力までで一意に定められた正規表記へ変換します。
 *
 * <p>【コンピュータ科学の観点：正規形（Canonical Form）】 同じ構造を表す空白、改行、括弧、引用符の違いを1種類へそろえると、差分レビューが安定し、
 * フォーマッタを繰り返しても結果が変わらない冪等性を実現できます。このクラスは名前解決や型検査を行わず、ASTに保存された構文情報だけを使用します。
 */
public final class CanonicalFormatter {
  private static final String INDENT = "    ";

  /** 状態を持たない正規フォーマッタを生成します。 */
  public CanonicalFormatter() {}

  /**
   * プログラム全体をBOMなし・LF改行の正規表記へ変換します。
   *
   * @param program 構文解析に成功したプログラムAST
   * @return 正規表記。トークンもコメントもない場合は空文字列、それ以外はLFちょうど1個で終了
   */
  public String format(Program program) {
    Objects.requireNonNull(program, "program");
    var lines = new ArrayList<RenderedLine>();
    TopLevelElement previousStructuralElement = null;

    for (TopLevelElement element : program.elements()) {
      if (element instanceof Comment comment) {
        appendComment(lines, comment, "");
        continue;
      }

      if (needsTopLevelBlankLine(previousStructuralElement, element)
          && !lines.isEmpty()
          && !lines.getLast().text().isEmpty()) {
        lines.add(new RenderedLine("", -1));
      }
      switch (element) {
        case LogicalConnectionDeclaration declaration ->
            lines.addAll(formatLogicalConnectionDeclaration(declaration));
        case WorkspaceDeclaration declaration ->
            lines.addAll(
                formatStaticResourceDeclaration(
                    declaration.name(),
                    "作業領域",
                    declaration.kindSpan(),
                    declaration.comments(),
                    declaration.endSpan()));
        case ValueDeclaration declaration -> lines.addAll(formatDeclaration(declaration, 0));
        case WordDefinition definition -> lines.addAll(formatDefinition(definition));
        case Comment ignored -> throw new AssertionError("comments are handled before this switch");
      }
      previousStructuralElement = element;
    }

    if (lines.isEmpty()) {
      return "";
    }
    return String.join("\n", lines.stream().map(RenderedLine::text).toList()) + '\n';
  }

  /** 連続する大域宣言以外のトップレベル要素間に空行が必要かを返します。 */
  private static boolean needsTopLevelBlankLine(TopLevelElement previous, TopLevelElement current) {
    return previous != null && !(isDeclaration(previous) && isDeclaration(current));
  }

  private static boolean isDeclaration(TopLevelElement element) {
    return element instanceof ValueDeclaration
        || element instanceof LogicalConnectionDeclaration
        || element instanceof WorkspaceDeclaration;
  }

  /** 論理接続宣言を初期値のない正規形へ変換し、宣言内コメントの相対順を保ちます。 */
  private static List<RenderedLine> formatLogicalConnectionDeclaration(
      LogicalConnectionDeclaration declaration) {
    return formatStaticResourceDeclaration(
        declaration.name(),
        "論理接続",
        declaration.kindSpan(),
        declaration.comments(),
        declaration.endSpan());
  }

  /** 値を持たない静的資源宣言を共通の正規形へ変換します。 */
  private static List<RenderedLine> formatStaticResourceDeclaration(
      String name,
      String kind,
      jp.bsb.diagnostics.SourceSpan kindSpan,
      List<Comment> comments,
      jp.bsb.diagnostics.SourceSpan endSpan) {
    String header = name + "は " + kind;
    if (comments.isEmpty()) {
      return List.of(new RenderedLine(header + "。", endSpan.end().line()));
    }
    var lines = new ArrayList<RenderedLine>();
    for (Comment comment : comments) {
      if (lines.isEmpty() && comment.span().start().line() == kindSpan.end().line()) {
        lines.add(new RenderedLine(header + " " + comment.text(), comment.span().end().line()));
      } else {
        if (lines.isEmpty()) {
          lines.add(new RenderedLine(header, kindSpan.end().line()));
        }
        lines.add(new RenderedLine(comment.text(), comment.span().end().line()));
      }
    }
    lines.add(new RenderedLine("。", endSpan.end().line()));
    return List.copyOf(lines);
  }

  /** 単語定義をヘッダー、本体、終端の正規行へ変換します。 */
  private static List<RenderedLine> formatDefinition(WordDefinition definition) {
    var lines = new ArrayList<RenderedLine>();
    lines.add(
        new RenderedLine(
            definition.name() + "とは " + formatStackEffect(definition.stackEffect()),
            definition.stackEffect().span().end().line()));

    formatBody(lines, definition.body(), 1);

    lines.add(new RenderedLine("こと。", definition.endSpan().end().line()));
    return lines;
  }

  /**
   * 1個の本体要素列を指定された字下げ深さで再帰的に整形します。
   *
   * <p>【コンピュータ科学の観点：構文木の走査】ASTの親子関係を深さ優先でたどると、開始語の後で深さを増やし、終了語の前で元へ戻す規則を自然に表現できます。
   */
  private static void formatBody(List<RenderedLine> lines, List<BodyElement> body, int depth) {
    String indent = INDENT.repeat(depth);
    var pending = new PendingBodyLine();

    for (int index = 0; index < body.size(); index++) {
      BodyElement element = body.get(index);
      switch (element) {
        case Assignment assignment -> {
          pending.add("を", assignment.valueParticleSpan().end().line());
          pending.add(assignment.targetName(), assignment.targetSpan().end().line());
          pending.add("に", assignment.targetParticleSpan().end().line());
          pending.add("入れる", assignment.keywordSpan().end().line());
          pending.flushInto(lines, indent);
        }
        case ArrayLiteral array -> {
          if (hasComment(array)) {
            formatArrayLiteral(lines, pending, array, depth);
          } else {
            pending.add(formatInlineArray(array), array.span().end().line());
          }
        }
        case ArrayLoop loop -> formatArrayLoop(lines, pending, loop, depth, indent);
        case ValueDeclaration declaration -> {
          pending.flushInto(lines, indent);
          lines.addAll(formatDeclaration(declaration, depth));
        }
        case ValueReference reference ->
            pending.add(reference.name(), reference.span().end().line());
        case Comment comment -> formatComment(lines, pending, comment, indent);
        case Literal literal -> pending.add(formatLiteral(literal), literal.span().end().line());
        case Particle particle -> pending.add(particle.name(), particle.span().end().line());
        case WordCall wordCall -> {
          pending.add(formatWordCall(wordCall), wordCall.span().end().line());
          if (!nextElementCanShareCurrentLine(body, index)) {
            pending.flushInto(lines, indent);
          }
        }
        case Conditional conditional ->
            formatConditional(lines, pending, conditional, depth, indent);
        case CountedLoop countedLoop ->
            formatCountedLoop(lines, pending, countedLoop, depth, indent);
        case ConditionLoop conditionLoop ->
            formatConditionLoop(lines, pending, conditionLoop, depth, indent);
        case ControlTransfer transfer -> {
          pending.flushInto(lines, indent);
          appendControlLine(lines, indent, transferText(transfer), transfer.span().end().line());
        }
      }
    }
    pending.flushInto(lines, indent);
  }

  /** 宣言を、初期値内コメントの有無に応じた正規行へ変換します。 */
  private static List<RenderedLine> formatDeclaration(ValueDeclaration declaration, int depth) {
    String indent = INDENT.repeat(depth);
    String header =
        indent
            + declaration.name()
            + "は "
            + switch (declaration.kind()) {
              case CONSTANT -> "定数";
              case VARIABLE -> "変数";
            };
    boolean hasComment =
        declaration.initializer().stream().anyMatch(CanonicalFormatter::hasComment);
    if (!hasComment) {
      String initializer = formatInlineInitializer(declaration.initializer());
      String text = header + (initializer.isEmpty() ? "" : " " + initializer) + "。";
      return List.of(new RenderedLine(text, declaration.endSpan().end().line()));
    }

    var lines = new ArrayList<RenderedLine>();
    lines.add(new RenderedLine(header, declaration.kindSpan().end().line()));
    formatCommentedInitializer(lines, declaration.initializer(), depth + 1);
    lines.add(new RenderedLine(indent + "。", declaration.endSpan().end().line()));
    return lines;
  }

  /** コメントを含まない初期値を1行用のトークン列へ変換します。 */
  private static String formatInlineInitializer(List<BodyElement> initializer) {
    return String.join(" ", initializer.stream().map(CanonicalFormatter::initializerText).toList());
  }

  /** コメント境界を越えずに、初期値を宣言より1段深い行へ整形します。 */
  private static void formatCommentedInitializer(
      List<RenderedLine> lines, List<BodyElement> initializer, int depth) {
    String indent = INDENT.repeat(depth);
    var pending = new PendingBodyLine();
    for (BodyElement element : initializer) {
      if (element instanceof Comment comment) {
        formatInitializerComment(lines, pending, comment, indent);
      } else if (element instanceof ArrayLiteral array && hasComment(array)) {
        formatArrayLiteral(lines, pending, array, depth);
      } else {
        pending.add(initializerText(element), element.span().end().line());
      }
    }
    pending.flushInto(lines, indent);
  }

  /** 初期値のコメントを、宣言ヘッダーではなく初期値行にだけ結合します。 */
  private static void formatInitializerComment(
      List<RenderedLine> lines,
      PendingBodyLine pending,
      Comment comment,
      String initializerIndent) {
    if (!pending.isEmpty() && comment.span().start().line() == pending.sourceLine()) {
      lines.add(
          new RenderedLine(
              initializerIndent + pending.text() + ' ' + comment.text(),
              comment.span().end().line()));
      pending.clear();
      return;
    }
    pending.flushInto(lines, initializerIndent);
    lines.add(new RenderedLine(initializerIndent + comment.text(), comment.span().end().line()));
  }

  /** 初期値に許可される1要素の正規表記を返します。 */
  private static String initializerText(BodyElement element) {
    return switch (element) {
      case ArrayLiteral array -> formatInlineArray(array);
      case Literal literal -> formatLiteral(literal);
      case Particle particle -> particle.name();
      case ValueReference reference -> reference.name();
      case WordCall wordCall -> formatWordCall(wordCall);
      case Comment ignored ->
          throw new IllegalArgumentException(
              "a comment cannot be rendered as inline initializer text");
      default ->
          throw new IllegalArgumentException(
              "initializer contains an element prohibited by the arrays grammar");
    };
  }

  /** コメントを含まない配列を、要素内だけ空白を入れた1行正規形へ変換します。 */
  private static String formatInlineArray(ArrayLiteral array) {
    String elements =
        String.join(
            "、",
            array.elements().stream()
                .map(ArrayElement::body)
                .map(CanonicalFormatter::formatInlineArrayElement)
                .toList());
    return "【" + elements + "】";
  }

  /** コメントなし配列の1要素を、通常の本体改行規則を使わず1個の式として連結します。 */
  private static String formatInlineArrayElement(List<BodyElement> body) {
    return String.join(" ", body.stream().map(CanonicalFormatter::arrayElementText).toList());
  }

  /** 配列要素内で1行表示できる構文要素の正規表記を返します。 */
  private static String arrayElementText(BodyElement element) {
    return switch (element) {
      case ArrayLiteral array -> formatInlineArray(array);
      case Literal literal -> formatLiteral(literal);
      case Particle particle -> particle.name();
      case ValueReference reference -> reference.name();
      case WordCall wordCall -> formatWordCall(wordCall);
      case Comment ignored ->
          throw new IllegalArgumentException("a comment cannot be rendered as inline array text");
      default ->
          throw new IllegalArgumentException(
              "array element contains an element prohibited by the arrays grammar");
    };
  }

  /** 呼出し名と、結果構築語にだけ許される2個の明示型引数を正規表記へ変換します。 */
  private static String formatWordCall(WordCall wordCall) {
    if (wordCall.logicalConnectionArgument().isPresent()) {
      var argument = wordCall.logicalConnectionArgument().orElseThrow();
      return wordCall.name()
          + "<"
          + argument.name()
          + argument.httpMethod().map(method -> "," + method.value()).orElse("")
          + ">";
    }
    if (wordCall.workspaceArgument().isPresent()) {
      return wordCall.name() + "<" + wordCall.workspaceArgument().orElseThrow().name() + ">";
    }
    if (wordCall.explicitTypeArguments().isEmpty()) {
      return wordCall.name();
    }
    return wordCall.name()
        + "<"
        + wordCall.explicitTypeArguments().get(0).name()
        + ","
        + wordCall.explicitTypeArguments().get(1).name()
        + ">";
  }

  /** 配列またはその入れ子要素にコメントが含まれるかを構文情報だけから判定します。 */
  private static boolean hasComment(BodyElement element) {
    if (element instanceof Comment) {
      return true;
    }
    if (element instanceof ArrayLiteral array) {
      return hasComment(array);
    }
    return false;
  }

  private static boolean hasComment(ArrayLiteral array) {
    return array.elements().stream()
        .flatMap(element -> element.body().stream())
        .anyMatch(CanonicalFormatter::hasComment);
  }

  /** コメント付き配列を、開始・要素・終了の字下げを保った複数行正規形へ変換します。 */
  private static void formatArrayLiteral(
      List<RenderedLine> lines, PendingBodyLine pending, ArrayLiteral array, int depth) {
    String indent = INDENT.repeat(depth);
    pending.flushInto(lines, indent);
    lines.add(new RenderedLine(indent + "【", array.openingSpan().end().line()));

    for (int index = 0; index < array.elements().size(); index++) {
      ArrayElement element = array.elements().get(index);
      var elementLines = new ArrayList<RenderedLine>();
      formatArrayElementBody(elementLines, element.body(), depth + 1);
      moveBoundaryCommentToPreviousElement(lines, elementLines, array.elements(), index);
      if (index + 1 < array.elements().size()) {
        appendArraySeparator(elementLines, element, depth + 1);
      }
      lines.addAll(elementLines);
    }

    // 終了記号は未確定行に残し、直後の助詞や単語呼出しと同じ行へ結合できるようにします。
    pending.add("】", array.endSpan().end().line());
  }

  /** 配列要素を1個の式として整形し、コメント境界または入れ子配列でだけ改行します。 */
  private static void formatArrayElementBody(
      List<RenderedLine> lines, List<BodyElement> body, int depth) {
    String indent = INDENT.repeat(depth);
    var pending = new PendingBodyLine();
    for (BodyElement element : body) {
      if (element instanceof Comment comment) {
        formatComment(lines, pending, comment, indent);
      } else if (element instanceof ArrayLiteral array && hasComment(array)) {
        formatArrayLiteral(lines, pending, array, depth);
      } else {
        pending.add(arrayElementText(element), element.span().end().line());
      }
    }
    pending.flushInto(lines, indent);
  }

  /** 区切り直後の行末コメントを、構文上の次要素から元の直前要素へ戻します。 */
  private static void moveBoundaryCommentToPreviousElement(
      List<RenderedLine> lines,
      List<RenderedLine> elementLines,
      List<ArrayElement> elements,
      int index) {
    if (index == 0 || elementLines.size() < 2 || lines.isEmpty()) {
      return;
    }
    RenderedLine first = elementLines.getFirst();
    int previousElementEndLine = elements.get(index - 1).span().end().line();
    if (!first.text().stripLeading().startsWith("#")
        || first.sourceLine() != previousElementEndLine) {
      return;
    }

    RenderedLine previous = lines.removeLast();
    lines.add(
        new RenderedLine(previous.text() + ' ' + first.text().stripLeading(), first.sourceLine()));
    elementLines.removeFirst();
  }

  /** 最終値行の行末コメントより前へ、配列要素の正規区切りを追加します。 */
  private static void appendArraySeparator(
      List<RenderedLine> elementLines, ArrayElement element, int depth) {
    Comment inlineComment = trailingInlineComment(element);
    for (int index = elementLines.size() - 1; index >= 0; index--) {
      RenderedLine line = elementLines.get(index);
      if (line.text().stripLeading().startsWith("#")) {
        continue;
      }
      int commentStart =
          inlineComment == null ? -1 : line.text().length() - inlineComment.text().length() - 1;
      String text =
          commentStart < 0
              ? line.text() + "、"
              : line.text().substring(0, commentStart) + "、" + line.text().substring(commentStart);
      elementLines.set(index, new RenderedLine(text, line.sourceLine()));
      return;
    }
    elementLines.add(new RenderedLine(INDENT.repeat(depth) + "、", element.span().end().line()));
  }

  /** 末尾コメントが直前の構文要素と同じ物理行にある場合だけ、そのコメントを返します。 */
  private static Comment trailingInlineComment(ArrayElement element) {
    List<BodyElement> body = element.body();
    if (body.size() < 2 || !(body.getLast() instanceof Comment comment)) {
      return null;
    }
    BodyElement previous = body.get(body.size() - 2);
    return previous.span().end().line() == comment.span().start().line() ? comment : null;
  }

  /** 配列反復を開始行、本体、終了行へ整形します。 */
  private static void formatArrayLoop(
      List<RenderedLine> lines, PendingBodyLine pending, ArrayLoop loop, int depth, String indent) {
    loop.inputParticle()
        .ifPresent(particle -> pending.add(particle.name(), particle.span().end().line()));
    appendShareableControlStart(lines, pending, indent, "各要素について", loop.openingSpan().end().line());
    formatBody(lines, loop.body(), depth + 1);
    appendControlLine(lines, indent, "繰り返す", loop.endSpan().end().line());
  }

  /** 条件分岐の開始行、真偽両側、終了行を正規順に出力します。 */
  private static void formatConditional(
      List<RenderedLine> lines,
      PendingBodyLine pending,
      Conditional conditional,
      int depth,
      String indent) {
    appendShareableControlStart(
        lines, pending, indent, "ならば", conditional.openingSpan().end().line());
    formatBody(lines, conditional.trueBody(), depth + 1);
    if (conditional.hasElse()) {
      var elseSpan = conditional.elseSpan().orElseThrow();
      appendControlLine(lines, indent, "さもなければ", elseSpan.end().line());
      formatBody(lines, conditional.falseBody(), depth + 1);
    }
    appendControlLine(lines, indent, "つぎに", conditional.endSpan().end().line());
  }

  /** 回数ループの開始行、本体、終了行を正規順に出力します。 */
  private static void formatCountedLoop(
      List<RenderedLine> lines,
      PendingBodyLine pending,
      CountedLoop loop,
      int depth,
      String indent) {
    appendShareableControlStart(lines, pending, indent, "回だけ", loop.openingSpan().end().line());
    formatBody(lines, loop.body(), depth + 1);
    appendControlLine(lines, indent, "繰り返す", loop.endSpan().end().line());
  }

  /** 条件ループの3個のマーカーと、条件計算部・本体を正規順に出力します。 */
  private static void formatConditionLoop(
      List<RenderedLine> lines,
      PendingBodyLine pending,
      ConditionLoop loop,
      int depth,
      String indent) {
    pending.flushInto(lines, indent);
    appendControlLine(lines, indent, "ここから", loop.openingSpan().end().line());
    formatBody(lines, loop.conditionBody(), depth + 1);
    appendControlLine(lines, indent, "続く間", loop.separatorSpan().end().line());
    formatBody(lines, loop.body(), depth + 1);
    appendControlLine(lines, indent, "繰り返す", loop.endSpan().end().line());
  }

  /** 未確定行があれば開始語をその末尾へ付け、なければ開始語だけの行を作ります。 */
  private static void appendShareableControlStart(
      List<RenderedLine> lines,
      PendingBodyLine pending,
      String indent,
      String keyword,
      int sourceLine) {
    if (pending.isEmpty()) {
      appendControlLine(lines, indent, keyword, sourceLine);
      return;
    }
    pending.add(keyword, sourceLine);
    pending.flushInto(lines, indent);
  }

  /** 制御語だけからなる独立行を追加します。 */
  private static void appendControlLine(
      List<RenderedLine> lines, String indent, String keyword, int sourceLine) {
    lines.add(new RenderedLine(indent + keyword, sourceLine));
  }

  /** 単語呼出しの直後に開始語または代入が続くなら、同じ正規行へ付けるため行確定を遅らせます。 */
  private static boolean nextElementCanShareCurrentLine(List<BodyElement> body, int index) {
    if (index + 1 >= body.size()) {
      return false;
    }
    BodyElement next = body.get(index + 1);
    return next instanceof Assignment
        || next instanceof ArrayLoop
        || next instanceof Conditional
        || next instanceof CountedLoop;
  }

  /** 未確定行とコメントの元物理行を比較し、行末またはコメント専用行へ配置します。 */
  private static void formatComment(
      List<RenderedLine> lines, PendingBodyLine pending, Comment comment, String standaloneIndent) {
    if (pending.isEmpty()) {
      appendComment(lines, comment, standaloneIndent);
      return;
    }

    if (comment.span().start().line() == pending.sourceLine()) {
      lines.add(
          new RenderedLine(
              standaloneIndent + pending.text() + ' ' + comment.text(),
              comment.span().end().line()));
      pending.clear();
      return;
    }

    pending.flushInto(lines, standaloneIndent);
    appendComment(lines, comment, standaloneIndent);
  }

  /** コメントが直前の実行要素と同じ物理行ならその行へ付け、それ以外はコメント専用行を作ります。 */
  private static void appendComment(
      List<RenderedLine> lines, Comment comment, String standaloneIndent) {
    if (!lines.isEmpty()
        && !lines.getLast().text().isEmpty()
        && lines.getLast().sourceLine() == comment.span().start().line()) {
      RenderedLine previous = lines.removeLast();
      lines.add(
          new RenderedLine(previous.text() + ' ' + comment.text(), comment.span().end().line()));
      return;
    }
    lines.add(new RenderedLine(standaloneIndent + comment.text(), comment.span().end().line()));
  }

  private static String formatStackEffect(StackEffect effect) {
    String inputs = joinTypes(effect.inputTypes());
    String outputs = joinTypes(effect.outputTypes());
    var result = new StringBuilder("（");
    if (!inputs.isEmpty()) {
      result.append(inputs).append(' ');
    }
    result.append("--");
    if (!outputs.isEmpty()) {
      result.append(' ').append(outputs);
    }
    return result.append('）').toString();
  }

  private static String joinTypes(List<TypeReference> types) {
    return String.join(" ", types.stream().map(TypeReference::name).toList());
  }

  private static String transferText(ControlTransfer transfer) {
    return switch (transfer.kind()) {
      case BREAK -> "打ち切る";
      case CONTINUE -> "続ける";
      case RETURN -> "戻る";
    };
  }

  private static String formatLiteral(Literal literal) {
    return switch (literal.kind()) {
      case INTEGER -> literal.value().equals("-0") ? "0" : literal.value();
      case DECIMAL -> literal.lexeme();
      case BOOLEAN -> literal.value();
      case CHARACTER -> '\'' + escapeLiteralValue(literal.value(), true) + '\'';
      case STRING -> '「' + escapeLiteralValue(literal.value(), false) + '」';
      case REGEX ->
          "正規表現「" + literal.value() + "」" + literal.regexMetadata().orElseThrow().canonicalFlags();
    };
  }

  /** 解釈済みの値を走査し、正規表記で直接書けないコードポイントだけをエスケープします。 */
  private static String escapeLiteralValue(String value, boolean characterLiteral) {
    var result = new StringBuilder(value.length());
    value
        .codePoints()
        .forEach(
            codePoint -> {
              switch (codePoint) {
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                case '\\' -> result.append("\\\\");
                case '\'' -> {
                  if (characterLiteral) {
                    result.append("\\'");
                  } else {
                    result.append('\'');
                  }
                }
                case '「' -> result.append(characterLiteral ? "「" : "\\「");
                case '」' -> result.append(characterLiteral ? "」" : "\\」");
                default -> {
                  if (mustUseUnicodeEscape(codePoint)) {
                    result
                        .append("\\u{")
                        .append(Integer.toHexString(codePoint).toUpperCase(Locale.ROOT))
                        .append('}');
                  } else {
                    result.appendCodePoint(codePoint);
                  }
                }
              }
            });
    return result.toString();
  }

  private static boolean mustUseUnicodeEscape(int codePoint) {
    return (codePoint >= 0x00 && codePoint <= 0x1F)
        || (codePoint >= 0x7F && codePoint <= 0x9F)
        || UnicodeRules.isForbiddenIdentifierCodePoint(codePoint);
  }

  /** 整形後の1行と、その末尾要素が元ソースで属していた物理行を保持します。 */
  private record RenderedLine(String text, int sourceLine) {}

  /**
   * リテラル、助詞、呼出しなど、まだ改行を確定していない1行分の部品を保持します。
   *
   * <p>部品を即座に出力しないことで、直後の {@code ならば} や {@code 回だけ} を同じ行へ安全に結合できます。
   */
  private static final class PendingBodyLine {
    private final List<String> parts = new ArrayList<>();
    private int sourceLine = -1;

    private void add(String part, int partSourceLine) {
      parts.add(part);
      sourceLine = partSourceLine;
    }

    private boolean isEmpty() {
      return parts.isEmpty();
    }

    private int sourceLine() {
      return sourceLine;
    }

    private String text() {
      return String.join(" ", parts);
    }

    private void flushInto(List<RenderedLine> lines, String indent) {
      if (isEmpty()) {
        return;
      }
      lines.add(new RenderedLine(indent + text(), sourceLine));
      clear();
    }

    private void clear() {
      parts.clear();
      sourceLine = -1;
    }
  }
}
