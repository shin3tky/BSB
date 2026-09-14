package jp.bsb.frontend;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticCollector;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.RelatedLocation;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.frontend.ast.RegexLiteralMetadata;
import jp.bsb.numeric.DecimalLexemeAnalyzer;
import jp.bsb.numeric.DecimalLexemeAnalyzer.Invalid;
import jp.bsb.numeric.DecimalLexemeAnalyzer.Valid;

/**
 * ソーステキストを先頭から走査し、トークン列へと変換する字句解析器（Lexer / Tokenizer / Scanner）です。
 *
 * <p>【コンピュータ科学の観点：字句解析の基礎とアルゴリズム】
 *
 * <ul>
 *   <li><b>字句解析（Lexical Analysis）とは</b>: 文字コードの検証に続くフロントエンド前半の段階であり、
 *       ソースコードの文字列ストリームを一文字ずつ（Unicodeコードポイント単位で）読み進めながら、
 *       区切りを読み飛ばし、キーワード・識別子・リテラル・記号などを「トークン」の列へ変換します。BSBではフォーマッタが必要とするため、
 *       コメントも位置と本文を持つ非実行トークンとして保持します。
 *   <li><b>最長一致の原則（Maximal Munch / Longest Match）</b>: 例えば {@code --}（スタック区切り記号）は {@code -}
 *       が2つ続いた表記ですが、2個の別々な文字として処理せず、1つの記号トークンとして切り出します。
 *   <li><b>コンテキスト依存のトークン化</b>: BSB言語では、トップレベルで現れる {@code 名前とは} の末尾 {@code とは} を単語定義の開始マーカーとして認識し、
 *       名前と {@code とは} の2つのトークンに分割します。一方、単語定義の本体中では通常の識別子として扱います。 また、{@code こと} の直後に {@code 。}
 *       が続く場合は定義終端として認識します。
 *   <li><b>資源制限によるDoS防止</b>: トークン総数（最大250,000個）、数値の桁数（最大4,096桁）、文字列リテラルのバイトサイズ（最大16MiB）を厳密に検査し、
 *       悪意ある巨大な入力によるメモリやCPUの過剰消費を一定範囲へ制限します。
 * </ul>
 */
public final class Lexer {
  /** 1ソースファイルあたりに許可される最大トークン数（250,000個） */
  public static final int MAX_TOKENS = 250_000;

  /** 数値リテラルの最大許容桁数（4,096桁） */
  public static final int MAX_NUMBER_DIGITS = 4096;

  /** 文字列リテラルの最大許容UTF-8バイトサイズ（16 MiB） */
  public static final int MAX_STRING_UTF8_BYTES = 16 * 1024 * 1024;

  /** 助詞として分類される予約語集合 */
  private static final Set<String> PARTICLES = Set.of("を", "に", "と", "から", "で");

  /** 真偽値リテラルとして分類される予約語集合 */
  private static final Set<String> BOOLEANS = Set.of("はい", "いいえ");

  /** 構文キーワードとして分類される予約語集合 */
  private static final Set<String> SYNTAX_NAMES = Set.of("とは", "こと");

  /** 宣言・代入予約語と専用トークン種別の対応です。 */
  private static final Map<String, TokenKind> BINDING_NAMES =
      Map.ofEntries(
          Map.entry("は", TokenKind.DECLARATION_MARKER),
          Map.entry("定数", TokenKind.CONSTANT_DECLARATION),
          Map.entry("変数", TokenKind.VARIABLE_DECLARATION),
          Map.entry("論理接続", TokenKind.LOGICAL_CONNECTION_DECLARATION),
          Map.entry("作業領域", TokenKind.WORKSPACE_DECLARATION),
          Map.entry("入れる", TokenKind.ASSIGNMENT));

  /**
   * 制御フローの制御予約語と専用トークン種別の対応です。
   *
   * <p>【コンピュータ科学の観点：予約語の早期分類】制御語を通常の識別子と区別しておくと、Parserは文字列比較ではなくトークン種別で文法を記述できます。
   * 誤って単語呼出しとしてASTへ入ることも防げます。
   */
  private static final Map<String, TokenKind> CONTROL_NAMES =
      Map.ofEntries(
          Map.entry("ならば", TokenKind.CONDITIONAL_START),
          Map.entry("さもなければ", TokenKind.CONDITIONAL_ELSE),
          Map.entry("つぎに", TokenKind.CONDITIONAL_END),
          Map.entry("または", TokenKind.SHORT_CIRCUIT_OR),
          Map.entry("かつ", TokenKind.SHORT_CIRCUIT_AND),
          Map.entry("回だけ", TokenKind.COUNTED_LOOP_START),
          Map.entry("ここから", TokenKind.CONDITION_LOOP_START),
          Map.entry("続く間", TokenKind.LOOP_CONDITION_SEPARATOR),
          Map.entry("繰り返す", TokenKind.LOOP_END),
          Map.entry("打ち切る", TokenKind.BREAK),
          Map.entry("続ける", TokenKind.CONTINUE),
          Map.entry("戻る", TokenKind.RETURN));

  /** 識別子の通常規則では数字から始まる、数値演算用の予約済み組み込み値です。 */
  private static final String NUMERIC_LEADING_RESERVED_NAME = "0方向へ丸め";

  /** raw正規表現リテラルを通常識別子と文字列より先に認識する連続接頭辞です。 */
  private static final String REGEX_LITERAL_PREFIX = "正規表現「";

  /** 識別子の検証器 */
  private final IdentifierValidator identifierValidator = new IdentifierValidator();

  /** 指数リテラルまでの字句規則を使う解析器を作ります。 */
  public Lexer() {}

  /**
   * 指定されたソーステキスト全体を字句解析し、トークン列と解析結果を生成します。
   *
   * @param source 解析対象のソーステキスト
   * @param diagnostics 診断（エラー・警告）を収集するコレクター
   * @return 解析結果（トークン列、トークン数、成功可否を含む {@link LexResult}）
   */
  public LexResult lex(SourceText source, DiagnosticCollector diagnostics) {
    var state = new State(source, diagnostics);
    String text = source.text();

    // メインループ：ソースコードの先頭から末尾まで1文字（コードポイント）ずつディスパッチ
    while (state.index < text.length() && !state.tokenLimitReached) {
      int codePoint = text.codePointAt(state.index);
      int width = Character.charCount(codePoint);

      // raw正規表現リテラルは「正規表現」と通常文字列の2トークンへ分けない。
      if (text.startsWith(REGEX_LITERAL_PREFIX, state.index)) {
        state.scanRegexLiteral();
        continue;
      }

      // 1. 許可された区切り文字のスキップ。読点は配列内だけ構造トークンとして次の規則へ渡す。
      if (isAllowedSeparator(codePoint)
          && !(isArrayElementSeparator(codePoint) && state.insideArray())) {
        state.consumeSeparator(codePoint, width);
        continue;
      }
      // 2. 許可されていないUnicode空白（ノーブレークスペース等）の検出・エラー報告
      if (UnicodeRules.isUnicodeWhitespace(codePoint)) {
        state.reportDisallowedWhitespace(codePoint, width);
        continue;
      }
      // 3. 行コメント（# から行末まで）の切り出し
      if (codePoint == '#') {
        state.scanComment();
        continue;
      }
      // 4. 文字列リテラル（「...」や "..."）または文字リテラル（'...'）の切り出し
      if (codePoint == '「' || codePoint == '"' || codePoint == '\'') {
        state.scanLiteral(codePoint);
        continue;
      }
      // 5. 構文記号（括弧、句点「。」、スタック区切り「--」）の切り出し
      if (state.scanPunctuation(codePoint, width)) {
        continue;
      }
      // 将来予約名「0方向へ丸め」は数値0と識別子へ分割せず、静的段階で機能境界を報告する。
      if (state.scanNumericLeadingReservedName()) {
        continue;
      }
      // 6. 数値リテラル（数字または符号付き数字で始まる候補）の切り出し
      if (isNumberCandidateStart(text, state.index, codePoint)) {
        state.scanNumber();
        continue;
      }
      // 7. 識別子・キーワード・助詞の切り出し
      if (looksLikeIdentifierStart(codePoint)) {
        state.scanIdentifier();
        continue;
      }

      // 8. いずれの規則にも当てはまらない予期しない文字のエラー報告
      state.reportUnexpectedCharacter(codePoint, width);
    }
    return new LexResult(state.tokens, state.countedTokenCount, !diagnostics.hasErrors());
  }

  /** BSBで許可されている区切り文字（空白、タブ、CR、LF、全角空白、読点）であるかを判定します。 */
  private static boolean isAllowedSeparator(int codePoint) {
    return codePoint == ' '
        || codePoint == '\t'
        || codePoint == '\r'
        || codePoint == '\n'
        || codePoint == 0x3000
        || codePoint == '、'
        || codePoint == '，';
  }

  /** 配列内で要素区切りとなる2種類の全角記号かを返します。 */
  private static boolean isArrayElementSeparator(int codePoint) {
    return codePoint == '、' || codePoint == '，';
  }

  /** 水平方向の空白（スペース、タブ、全角空白）であるかを判定します。 */
  private static boolean isHorizontalSpace(int codePoint) {
    return codePoint == ' ' || codePoint == '\t' || codePoint == 0x3000;
  }

  /** 単語やリテラル候補の境界となり得る文字（区切り、記号、引用符等）であるかを判定します。 */
  private static boolean isCandidateBoundary(int codePoint) {
    return isAllowedSeparator(codePoint)
        || codePoint == '。'
        || codePoint == '#'
        || codePoint == '（'
        || codePoint == '）'
        || codePoint == '('
        || codePoint == ')'
        || codePoint == '【'
        || codePoint == '】'
        || codePoint == '<'
        || codePoint == '>'
        || codePoint == '「'
        || codePoint == '」'
        || codePoint == '"'
        || codePoint == '\'';
  }

  /** 識別子の開始文字になり得る文字かを判定します。 */
  private static boolean looksLikeIdentifierStart(int codePoint) {
    return UnicodeRules.isIdentifierContinue(codePoint)
        || UnicodeRules.isForbiddenIdentifierCodePoint(codePoint);
  }

  /** 数値リテラル候補の開始であるか（数字、または直後に数字が続く + / - / .）を判定します。 */
  private static boolean isNumberCandidateStart(String text, int index, int codePoint) {
    if (codePoint >= '0' && codePoint <= '9') {
      return true;
    }
    if ((codePoint == '-' || codePoint == '+' || codePoint == '.') && index + 1 < text.length()) {
      char next = text.charAt(index + 1);
      return next >= '0' && next <= '9';
    }
    return false;
  }

  /** 字句解析の走査状態（現在のインデックス、切り出したトークン列、コンテキストフラグなど）を保持する内部クラスです。 */
  private final class State {
    private final SourceText source;
    private final DiagnosticCollector diagnostics;
    private final List<Token> tokens = new ArrayList<>();
    private final SourceText.PositionCursor positionCursor;
    private int index;
    private int countedTokenCount;
    private boolean tokenLimitReached;

    /** 現在、単語定義の本体（本体要素列）の内部を走査中であるか */
    private boolean insideDefinition;

    /** 直前に「こと」が出現し、次の「。」で定義終了になり得るか */
    private boolean possibleDefinitionEnd;

    /** 宣言開始を読み、次の「。」を宣言終端として扱う状態か */
    private boolean insideDeclaration;

    /** 字句上まだ閉じられていない配列リテラルの個数です。 */
    private int arrayDepth;

    /** 山括弧ごとに、最内側が結果型／結果構築の2引数枠かを保持します。 */
    private final ArrayDeque<Boolean> typeArgumentFrames = new ArrayDeque<>();

    private State(SourceText source, DiagnosticCollector diagnostics) {
      this.source = source;
      this.diagnostics = diagnostics;
      positionCursor = source.positionCursor();
    }

    /** 許可された区切り文字を消費してUTF-16インデックスを進めます（CRLFはまとめて2コード単位進める）。 */
    private void consumeSeparator(int codePoint, int width) {
      if (!isHorizontalSpace(codePoint)) {
        possibleDefinitionEnd = false;
      }
      if (codePoint == '\r'
          && index + 1 < source.text().length()
          && source.text().charAt(index + 1) == '\n') {
        index += 2;
      } else {
        index += width;
      }
    }

    /** 許可されていないUnicode空白（ノーブレークスペース等）のエラーを報告します。 */
    private void reportDisallowedWhitespace(int codePoint, int width) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_DISALLOWED_WHITESPACE,
                  Severity.ERROR,
                  DiagnosticStage.LEXICAL,
                  source.sourcePath(),
                  source.span(index, index + width))
              .field("codePoint", UnicodeRules.codePointNotation(codePoint))
              .expected("許可された区切り")
              .actual(UnicodeRules.codePointNotation(codePoint))
              .fix("ASCII空白へ置き換えてください")
              .build());
      possibleDefinitionEnd = false;
      index += width;
    }

    /** '#' から行末（CRまたはLF）までの行コメントを走査してトークン化します。 */
    private void scanComment() {
      int start = index;
      while (index < source.text().length()) {
        char current = source.text().charAt(index);
        if (current == '\r' || current == '\n') {
          break;
        }
        index++;
      }
      addToken(TokenKind.COMMENT, start, index, source.text().substring(start, index));
      possibleDefinitionEnd = false;
    }

    /** 連続した「正規表現「pattern」flags」をrawの1トークンとして走査します。 */
    private void scanRegexLiteral() {
      int start = index;
      int patternStart = start + REGEX_LITERAL_PREFIX.length();
      int close = source.text().indexOf('」', patternStart);
      SourcePosition opened = source.positionAt(start);
      if (close < 0) {
        index = source.text().length();
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_UNTERMINATED_REGEX_LITERAL,
                    Severity.ERROR,
                    DiagnosticStage.LEXICAL,
                    source.sourcePath(),
                    source.span(start, index))
                .field("openedLine", Integer.toString(opened.line()))
                .field("openedColumn", Integer.toString(opened.column()))
                .expected("終わりかぎ括弧」")
                .actual("入力末尾")
                .fix("正規表現リテラルを」で閉じてください")
                .build());
        possibleDefinitionEnd = false;
        return;
      }

      int flagEnd = scanAsciiLowercaseEnd(close + 1);
      int newline = firstNewline(patternStart, close);
      if (newline >= 0) {
        index = flagEnd;
        String actual = source.text().charAt(newline) == '\r' ? "CR" : "LF";
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_NEWLINE_IN_REGEX_LITERAL,
                    Severity.ERROR,
                    DiagnosticStage.LEXICAL,
                    source.sourcePath(),
                    source.span(newline, newline + 1))
                .field("openedLine", Integer.toString(opened.line()))
                .field("openedColumn", Integer.toString(opened.column()))
                .expected("改行を含まないrawパターン")
                .actual(actual)
                .fix("改行を\\nとして表すかパターンを1行にしてください")
                .relatedLocation(new RelatedLocation(source.sourcePath(), opened, "正規表現"))
                .build());
        possibleDefinitionEnd = false;
        return;
      }

      String flags = source.text().substring(close + 1, flagEnd);
      if (!validateRegexFlags(flags, close + 1)) {
        index = flagEnd;
        possibleDefinitionEnd = false;
        return;
      }

      index = flagEnd;
      String pattern = source.text().substring(patternStart, close);
      var metadata = new RegexLiteralMetadata(flags, regexPatternPositions(patternStart, close));
      addToken(TokenKind.REGEX_LITERAL, start, index, pattern, metadata);
      possibleDefinitionEnd = false;
    }

    private int scanAsciiLowercaseEnd(int start) {
      int end = start;
      while (end < source.text().length()) {
        char character = source.text().charAt(end);
        if (character < 'a' || character > 'z') {
          break;
        }
        end++;
      }
      return end;
    }

    private int firstNewline(int start, int end) {
      for (int cursor = start; cursor < end; cursor++) {
        char character = source.text().charAt(cursor);
        if (character == '\r' || character == '\n') {
          return cursor;
        }
      }
      return -1;
    }

    private boolean validateRegexFlags(String flags, int flagsStart) {
      int[] first = {-1, -1, -1};
      for (int offset = 0; offset < flags.length(); offset++) {
        char flag = flags.charAt(offset);
        int flagIndex = "ims".indexOf(flag);
        int absolute = flagsStart + offset;
        if (flagIndex < 0) {
          diagnostics.add(
              Diagnostic.builder(
                      DiagnosticCode.E_REGEX_FLAG,
                      Severity.ERROR,
                      DiagnosticStage.LEXICAL,
                      source.sourcePath(),
                      source.span(absolute, absolute + 1))
                  .field("flag", Character.toString(flag))
                  .field("allowed", "i,m,s")
                  .expected("フラグi,m,s")
                  .actual(Character.toString(flag))
                  .fix(flag + "を削除してください")
                  .build());
          return false;
        }
        if (first[flagIndex] >= 0) {
          diagnostics.add(
              Diagnostic.builder(
                      DiagnosticCode.E_REGEX_FLAG,
                      Severity.ERROR,
                      DiagnosticStage.LEXICAL,
                      source.sourcePath(),
                      source.span(absolute, absolute + 1))
                  .field("flag", Character.toString(flag))
                  .field("reason", "duplicate")
                  .expected("重複しないフラグ")
                  .actual(Character.toString(flag))
                  .fix((offset + 1) + "個目の" + flag + "を削除してください")
                  .relatedLocation(
                      new RelatedLocation(
                          source.sourcePath(),
                          source.positionAt(first[flagIndex]),
                          Character.toString(flag)))
                  .build());
          return false;
        }
        first[flagIndex] = absolute;
      }
      return true;
    }

    private List<SourcePosition> regexPatternPositions(int start, int end) {
      var result = new ArrayList<SourcePosition>();
      SourceText.PositionCursor cursor = source.positionCursor();
      int offset = start;
      while (offset < end) {
        result.add(cursor.positionAt(offset));
        offset += Character.charCount(source.text().codePointAt(offset));
      }
      result.add(cursor.positionAt(end));
      return List.copyOf(result);
    }

    private boolean insideArray() {
      return arrayDepth > 0;
    }

    /** 構文記号（括弧、配列、型引数、句点、スタック区切り記号）を判定してトークン化します。 */
    private boolean scanPunctuation(int codePoint, int width) {
      TokenKind kind =
          switch (codePoint) {
            case '（' -> TokenKind.STACK_OPEN_FULLWIDTH;
            case '）' -> TokenKind.STACK_CLOSE_FULLWIDTH;
            case '(' -> TokenKind.STACK_OPEN_ASCII;
            case ')' -> TokenKind.STACK_CLOSE_ASCII;
            case '【' -> TokenKind.ARRAY_OPEN;
            case '】' -> TokenKind.ARRAY_CLOSE;
            case '、', '，' -> insideArray() ? TokenKind.ARRAY_SEPARATOR : null;
            case '<' -> TokenKind.ARRAY_TYPE_OPEN;
            case '>' -> TokenKind.ARRAY_TYPE_CLOSE;
            case ',' ->
                !typeArgumentFrames.isEmpty() && typeArgumentFrames.peek()
                    ? TokenKind.RESULT_TYPE_SEPARATOR
                    : null;
            case '。' ->
                possibleDefinitionEnd ? TokenKind.DEFINITION_END : TokenKind.DECLARATION_END;
            default -> null;
          };
      if (kind != null) {
        boolean resultFrame = kind == TokenKind.ARRAY_TYPE_OPEN && previousTokenStartsResultFrame();
        int start = index;
        index += width;
        addToken(kind, start, index, source.text().substring(start, index));
        if (kind == TokenKind.ARRAY_TYPE_OPEN) {
          typeArgumentFrames.push(resultFrame);
        } else if (kind == TokenKind.ARRAY_TYPE_CLOSE && !typeArgumentFrames.isEmpty()) {
          typeArgumentFrames.pop();
        } else if (kind == TokenKind.ARRAY_OPEN) {
          arrayDepth++;
        } else if (kind == TokenKind.ARRAY_CLOSE && arrayDepth > 0) {
          arrayDepth--;
        } else if (kind == TokenKind.DEFINITION_END) {
          insideDefinition = false;
          insideDeclaration = false;
          arrayDepth = 0;
          typeArgumentFrames.clear();
        } else if (kind == TokenKind.DECLARATION_END) {
          insideDeclaration = false;
          arrayDepth = 0;
          typeArgumentFrames.clear();
        }
        possibleDefinitionEnd = false;
        return true;
      }

      // 2文字のスタック区切り記号「--」の判定
      if (codePoint == '-'
          && index + 1 < source.text().length()
          && source.text().charAt(index + 1) == '-') {
        int start = index;
        index += 2;
        addToken(TokenKind.STACK_SEPARATOR, start, index, "--");
        possibleDefinitionEnd = false;
        return true;
      }
      return false;
    }

    private boolean previousTokenStartsResultFrame() {
      for (int cursor = tokens.size() - 1; cursor >= 0; cursor--) {
        Token token = tokens.get(cursor);
        if (token.kind() == TokenKind.COMMENT) {
          continue;
        }
        return token.kind() == TokenKind.IDENTIFIER
            && Set.of("結果", "成功にする", "失敗にする", "論理接続を確認する", "HTTP要求を送信する", "ファイルを読む", "ファイルへ書く")
                .contains(token.value());
      }
      return false;
    }

    /** 識別子候補を走査し、定義開始「〜とは」の分割や予約語の分類を行います。 */
    private void scanIdentifier() {
      int start = index;
      index = scanCandidateEnd(index);
      String candidate = source.text().substring(start, index);

      // 宣言候補だけ、末尾の「は」を名前から分離する。「母は強い」のような通常名は分割しない。
      if (candidate.endsWith("は")
          && candidate.length() > "は".length()
          && declarationKindFollows(index)) {
        int nameEnd = index - "は".length();
        identifierValidator
            .validate(source, start, nameEnd, diagnostics)
            .ifPresent(value -> addToken(TokenKind.IDENTIFIER, start, nameEnd, value));
        addToken(TokenKind.DECLARATION_MARKER, nameEnd, index, "は");
        insideDeclaration = true;
        possibleDefinitionEnd = false;
        return;
      }

      // トップレベルかつ末尾が「とは」で終わる場合、定義開始（名前 + 「とは」）として分割
      if (!insideDefinition && candidate.endsWith("とは") && candidate.length() > "とは".length()) {
        int nameEnd = index - "とは".length();
        identifierValidator
            .validate(source, start, nameEnd, diagnostics)
            .ifPresent(value -> addToken(TokenKind.IDENTIFIER, start, nameEnd, value));
        addToken(TokenKind.RESERVED_SYNTAX, nameEnd, index, "とは");
        insideDefinition = true;
        insideDeclaration = false;
        possibleDefinitionEnd = false;
        return;
      }

      // 通常の識別子・予約語・助詞の検証と分類
      identifierValidator
          .validate(source, start, index, diagnostics)
          .ifPresent(value -> addClassifiedName(start, index, value));
    }

    /** 数字から始まる唯一の将来予約名を、数値候補より先に1トークンとして認識します。 */
    private boolean scanNumericLeadingReservedName() {
      if (!source.text().startsWith(NUMERIC_LEADING_RESERVED_NAME, index)) {
        return false;
      }
      int start = index;
      int nameEnd = start + NUMERIC_LEADING_RESERVED_NAME.length();
      boolean definitionStart = !insideDefinition && source.text().startsWith("とは", nameEnd);
      int candidateEnd = definitionStart ? nameEnd + "とは".length() : nameEnd;
      if (candidateEnd < source.text().length()
          && !isCandidateBoundary(source.text().codePointAt(candidateEnd))
          && !UnicodeRules.isUnicodeWhitespace(source.text().codePointAt(candidateEnd))) {
        return false;
      }

      index = nameEnd;
      addToken(TokenKind.IDENTIFIER, start, nameEnd, NUMERIC_LEADING_RESERVED_NAME);
      if (definitionStart) {
        index = candidateEnd;
        addToken(TokenKind.RESERVED_SYNTAX, nameEnd, candidateEnd, "とは");
        insideDefinition = true;
      }
      possibleDefinitionEnd = false;
      return true;
    }

    /** 正規化された識別子を、真偽値、助詞、構文語、通常識別子のいずれかに分類して追加します。 */
    private void addClassifiedName(int start, int end, String normalized) {
      TokenKind kind;
      if (BOOLEANS.contains(normalized)) {
        kind = TokenKind.BOOLEAN_LITERAL;
      } else if (PARTICLES.contains(normalized)) {
        kind = TokenKind.PARTICLE;
      } else if (BINDING_NAMES.containsKey(normalized)) {
        kind = BINDING_NAMES.get(normalized);
      } else if (CONTROL_NAMES.containsKey(normalized)) {
        kind = CONTROL_NAMES.get(normalized);
      } else if (normalized.equals("各要素について")) {
        kind = TokenKind.ARRAY_LOOP_START;
      } else if (SYNTAX_NAMES.contains(normalized)) {
        kind = TokenKind.RESERVED_SYNTAX;
      } else {
        kind = TokenKind.IDENTIFIER;
      }
      addToken(kind, start, end, normalized);
      if (kind == TokenKind.RESERVED_SYNTAX && normalized.equals("とは") && !insideDefinition) {
        // 空白を挟んだ旧来の不正な定義開始でも、後続の「こと。」は定義終端として回復させる。
        insideDefinition = true;
        insideDeclaration = false;
      }
      if (kind == TokenKind.DECLARATION_MARKER && declarationKindFollows(end)) {
        insideDeclaration = true;
      }
      possibleDefinitionEnd = insideDefinition && normalized.equals("こと");
    }

    /** 現在位置より後の最初の非コメント候補が宣言種別または宣言終端かを調べます。 */
    private boolean declarationKindFollows(int start) {
      int cursor = start;
      while (cursor < source.text().length()) {
        int codePoint = source.text().codePointAt(cursor);
        if (isAllowedSeparator(codePoint) || UnicodeRules.isUnicodeWhitespace(codePoint)) {
          cursor += Character.charCount(codePoint);
          continue;
        }
        if (codePoint == '#') {
          while (cursor < source.text().length()) {
            char current = source.text().charAt(cursor);
            if (current == '\r' || current == '\n') {
              break;
            }
            cursor++;
          }
          continue;
        }
        if (codePoint == '。') {
          return true;
        }
        int end = scanCandidateEnd(cursor);
        String candidate = source.text().substring(cursor, end);
        return candidate.equals("定数")
            || candidate.equals("変数")
            || candidate.equals("論理接続")
            || candidate.equals("作業領域");
      }
      return false;
    }

    /** 次の区切りや記号の境界までのインデックスを探索します。 */
    private int scanCandidateEnd(int start) {
      int end = start;
      while (end < source.text().length()) {
        int codePoint = source.text().codePointAt(end);
        if (isCandidateBoundary(codePoint)
            || codePoint == ',' && !typeArgumentFrames.isEmpty() && typeArgumentFrames.peek()
            || UnicodeRules.isUnicodeWhitespace(codePoint)) {
          break;
        }
        end += Character.charCount(codePoint);
      }
      return end;
    }

    /** 数値リテラル（整数または小数）を走査し、バリデーションとトークン化を行います。 */
    private void scanNumber() {
      int start = index;
      index = scanNumberEnd(index);
      String candidate = source.text().substring(start, index);
      DecimalLexemeAnalyzer.Result analysis =
          DecimalLexemeAnalyzer.analyze(candidate, Integer.MAX_VALUE);
      if (analysis instanceof Invalid invalid) {
        // 数値として不正な形式の場合のエラー報告
        if (!reportMissingNumberSeparator(start, candidate)) {
          reportInvalidNumber(start, candidate, invalid);
        }
        possibleDefinitionEnd = false;
        return;
      }
      Valid valid = (Valid) analysis;
      TokenKind kind =
          valid.kind() == DecimalLexemeAnalyzer.Kind.INTEGER
              ? TokenKind.INTEGER_LITERAL
              : TokenKind.DECIMAL_LITERAL;

      // 桁数上限（4,096桁）の検査
      int digits = valid.inputDigits();
      if (digits > MAX_NUMBER_DIGITS) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_NUMBER_LIMIT,
                    Severity.ERROR,
                    DiagnosticStage.LEXICAL,
                    source.sourcePath(),
                    source.span(start, index))
                .limit("numberDigits", MAX_NUMBER_DIGITS, digits)
                .expected(MAX_NUMBER_DIGITS + "桁以下")
                .actual(digits + "桁")
                .fix("桁数を減らしてください")
                .build());
      } else {
        addToken(kind, start, index, candidate);
      }
      possibleDefinitionEnd = false;
    }

    private int scanNumberEnd(int start) {
      int end = start;
      while (end < source.text().length()) {
        int codePoint = source.text().codePointAt(end);
        if (isAllowedSeparator(codePoint)
            || codePoint == '。'
            || codePoint == '#'
            || codePoint == '【'
            || codePoint == '】'
            || codePoint == '<'
            || codePoint == '>'
            || UnicodeRules.isUnicodeWhitespace(codePoint)) {
          break;
        }
        end += Character.charCount(codePoint);
      }
      return end;
    }

    /** 数値の直後に区切り空白がない場合（例: 123abc）のエラーを報告します。 */
    private boolean reportMissingNumberSeparator(int start, String candidate) {
      int prefixEnd = validNumberPrefixEnd(candidate);
      if (prefixEnd <= 0 || prefixEnd == candidate.length()) {
        return false;
      }
      String suffix = candidate.substring(prefixEnd);
      if (startsWithExponentMarker(suffix) || !isIdentifierSuffix(suffix)) {
        return false;
      }

      int suffixStart = start + prefixEnd;
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_MISSING_SEPARATOR,
                  Severity.ERROR,
                  DiagnosticStage.LEXICAL,
                  source.sourcePath(),
                  source.span(suffixStart, index))
              .expected("数値後の区切り")
              .actual(suffix)
              .fix(candidate.substring(0, prefixEnd) + " " + suffix)
              .build());
      return true;
    }

    /** 不正な数値表記（先頭の0、不完全な小数など）に対する具体的なエラーメッセージと修正候補を報告します。 */
    private void reportInvalidNumber(int start, String candidate, Invalid invalid) {
      String expected = "整数または小数の正しい表記";
      String fix = "数値リテラルを修正してください";
      boolean exponentCandidate = containsExponentMarker(candidate);
      if (exponentCandidate) {
        switch (invalid.reason()) {
          case MISSING_EXPONENT_DIGITS -> {
            if (candidate.contains("e--") || candidate.contains("E--")) {
              expected = "指数符号1個とASCII数字";
              fix = "余分な-を削除してください";
            } else if (candidate.endsWith("+") || candidate.endsWith("-")) {
              expected = "指数符号後のASCII数字";
              fix = "指数符号の後へ数字を追加してください";
            } else {
              expected = "指数部のASCII数字";
              fix = candidate.endsWith("円") ? "指数数字を追加するかeの前へ空白を入れてください" : "指数部へ数字を追加してください";
            }
          }
          case INVALID_MANTISSA -> {
            expected = "整数部と小数部を持つ仮数";
            fix =
                candidate.startsWith(".")
                    ? "0" + candidate + "のように書いてください"
                    : candidate.replace(".", ".0") + "のように書いてください";
          }
          case LEADING_PLUS -> {
            expected = "先頭符号なしの正数";
            fix = "先頭の+を削除してください";
          }
          case LEADING_ZERO -> {
            expected = "0または非ゼロ数字で始まる整数部";
            fix = "先頭の0を削除してください";
          }
          case REPEATED_EXPONENT -> {
            expected = "指数記号1個";
            fix = "余分な指数記号を削除してください";
          }
        }
      } else if (candidate.matches("-?0[0-9]+")) {
        expected = "0または0以外で始まる整数";
        fix = stripLeadingZeros(candidate) + "を使用してください";
      } else if (candidate.matches("-?\\.[0-9]+")) {
        expected = "小数点前の数字";
        fix =
            (candidate.startsWith("-") ? "-0" + candidate.substring(1) : "0" + candidate)
                + "を使用してください";
      } else if (candidate.matches("-?(?:0|[1-9][0-9]*)\\.")) {
        expected = "小数点後の数字";
        fix = candidate + "0を使用してください";
      }
      Diagnostic.Builder builder =
          Diagnostic.builder(
                  DiagnosticCode.E_INVALID_NUMBER_LITERAL,
                  Severity.ERROR,
                  DiagnosticStage.LEXICAL,
                  source.sourcePath(),
                  source.span(start, index))
              .expected(expected)
              .actual(candidate)
              .fix(fix);
      if (exponentCandidate) {
        builder.field("reason", invalid.reason().stableId());
      }
      diagnostics.add(builder.build());
    }

    /** 文字列リテラル（「...」や "..."）または文字リテラル（'...'）を走査します。 */
    private void scanLiteral(int openingCodePoint) {
      int start = index;
      int closingCodePoint = openingCodePoint == '「' ? '」' : openingCodePoint;
      TokenKind kind =
          openingCodePoint == '\'' ? TokenKind.CHARACTER_LITERAL : TokenKind.STRING_LITERAL;
      index += Character.charCount(openingCodePoint);
      var value = new StringBuilder();
      long utf8Bytes = 0;

      while (index < source.text().length()) {
        int codePoint = source.text().codePointAt(index);
        int width = Character.charCount(codePoint);
        // 閉じ引用符に到達した場合
        if (codePoint == closingCodePoint) {
          index += width;
          finishLiteral(kind, start, value, utf8Bytes);
          return;
        }
        // 文字列の途中で物理改行が現れた場合は閉じ忘れエラー
        if (codePoint == '\r' || codePoint == '\n') {
          reportUnterminatedLiteral(closingCodePoint, "改行");
          return;
        }
        // エスケープシーケンス（\n, \t, \\u{...} 等）の解決
        if (codePoint == '\\') {
          EscapeResult escape = scanEscape();
          if (!escape.valid()) {
            skipInvalidLiteral(closingCodePoint);
            return;
          }
          codePoint = escape.codePoint();
          width = 0;
        } else {
          index += width;
        }

        utf8Bytes += utf8Length(codePoint);
        if (kind != TokenKind.STRING_LITERAL || utf8Bytes <= MAX_STRING_UTF8_BYTES) {
          value.appendCodePoint(codePoint);
        }
      }
      // ファイル終端（EOF）に達しても閉じられていない場合
      reportUnterminatedLiteral(closingCodePoint, "EOF");
    }

    /** バックスラッシュに続くエスケープシーケンスをパースします。 */
    private EscapeResult scanEscape() {
      int escapeStart = index;
      index++;
      if (index >= source.text().length()) {
        reportInvalidEscape(escapeStart, index);
        return EscapeResult.invalid();
      }
      int escaped = source.text().codePointAt(index);
      int escapedWidth = Character.charCount(escaped);
      int value =
          switch (escaped) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case '\\' -> '\\';
            case '"' -> '"';
            case '\'' -> '\'';
            case '「' -> '「';
            case '」' -> '」';
            default -> -1;
          };
      if (value >= 0) {
        index += escapedWidth;
        return EscapeResult.valid(value);
      }
      if (escaped == 'u') {
        return scanUnicodeEscape(escapeStart);
      }
      index += escapedWidth;
      reportInvalidEscape(escapeStart, index);
      return EscapeResult.invalid();
    }

    /** <code>\\u{XXXXXX}</code> 形式のUnicodeスカラー値エスケープを走査・検証します。 */
    private EscapeResult scanUnicodeEscape(int escapeStart) {
      index++;
      if (index >= source.text().length() || source.text().charAt(index) != '{') {
        reportInvalidEscape(escapeStart, index);
        return EscapeResult.invalid();
      }
      index++;
      int digitsStart = index;
      while (index < source.text().length() && isAsciiHexDigit(source.text().charAt(index))) {
        index++;
      }
      int digitCount = index - digitsStart;
      if (digitCount < 1
          || digitCount > 6
          || index >= source.text().length()
          || source.text().charAt(index) != '}') {
        if (index < source.text().length() && source.text().charAt(index) == '}') {
          index++;
        }
        reportInvalidEscape(escapeStart, index);
        return EscapeResult.invalid();
      }
      int value = Integer.parseInt(source.text().substring(digitsStart, index), 16);
      index++;
      // サロゲート領域などの無効なスカラー値の検査
      if (!Character.isValidCodePoint(value) || (value >= 0xD800 && value <= 0xDFFF)) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_INVALID_UNICODE_SCALAR,
                    Severity.ERROR,
                    DiagnosticStage.LEXICAL,
                    source.sourcePath(),
                    source.span(escapeStart, index))
                .expected("Unicodeスカラー値")
                .actual(UnicodeRules.codePointNotation(value))
                .fix("サロゲート以外を指定してください")
                .build());
        return EscapeResult.invalid();
      }
      return EscapeResult.valid(value);
    }

    private void reportInvalidEscape(int start, int end) {
      String escape = source.text().substring(start, end);
      String fix =
          escape.codePointCount(0, escape.length()) == 2
              ? escape.substring(1) + "を直接書くか定義済み表記を使ってください"
              : "定義済みのエスケープ表記を使用してください";
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_INVALID_ESCAPE,
                  Severity.ERROR,
                  DiagnosticStage.LEXICAL,
                  source.sourcePath(),
                  source.span(start, end))
              .expected("定義済みエスケープ")
              .actual(escape)
              .fix(fix)
              .build());
    }

    private void reportUnterminatedLiteral(int closingCodePoint, String actual) {
      String closing = new String(Character.toChars(closingCodePoint));
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_UNTERMINATED_STRING,
                  Severity.ERROR,
                  DiagnosticStage.LEXICAL,
                  source.sourcePath(),
                  source.positionAt(index))
              .expected(closing)
              .actual(actual)
              .fix("ここに" + closing + "を追加してください")
              .build());
      possibleDefinitionEnd = false;
    }

    private void skipInvalidLiteral(int closingCodePoint) {
      while (index < source.text().length()) {
        int codePoint = source.text().codePointAt(index);
        int width = Character.charCount(codePoint);
        if (codePoint == '\r' || codePoint == '\n') {
          return;
        }
        index += width;
        if (codePoint == '\\' && index < source.text().length()) {
          index += Character.charCount(source.text().codePointAt(index));
        } else if (codePoint == closingCodePoint) {
          return;
        }
      }
    }

    /** リテラルの走査完了処理（文字列のバイト上限検査、文字リテラルの1クラスタ検査）。 */
    private void finishLiteral(TokenKind kind, int start, StringBuilder value, long utf8Bytes) {
      if (kind == TokenKind.STRING_LITERAL && utf8Bytes > MAX_STRING_UTF8_BYTES) {
        diagnostics.add(
            Diagnostic.builder(
                    DiagnosticCode.E_STRING_LIMIT,
                    Severity.ERROR,
                    DiagnosticStage.LEXICAL,
                    source.sourcePath(),
                    source.span(start, index))
                .limit("stringUtf8Bytes", MAX_STRING_UTF8_BYTES, utf8Bytes)
                .expected(MAX_STRING_UTF8_BYTES + " UTF-8バイト以下")
                .actual(utf8Bytes + " UTF-8バイト")
                .fix("文字列を短くしてください")
                .build());
      } else if (kind == TokenKind.CHARACTER_LITERAL) {
        // 文字リテラルは厳密に「1書記素クラスタ（1文字）」でなければならない
        int clusters = UnicodeRules.graphemeClusterCount(value.toString());
        if (clusters != 1) {
          diagnostics.add(
              Diagnostic.builder(
                      DiagnosticCode.E_CHARACTER_LENGTH,
                      Severity.ERROR,
                      DiagnosticStage.LEXICAL,
                      source.sourcePath(),
                      source.span(start, index))
                  .expected("1クラスタ")
                  .actual(clusters + "クラスタ")
                  .fix(clusters == 0 ? "1文字を指定してください" : "1文字だけを指定してください")
                  .build());
        } else {
          addToken(kind, start, index, value.toString());
        }
      } else {
        addToken(kind, start, index, value.toString());
      }
      possibleDefinitionEnd = false;
    }

    private void reportUnexpectedCharacter(int codePoint, int width) {
      diagnostics.add(
          Diagnostic.builder(
                  DiagnosticCode.E_UNEXPECTED_CHARACTER,
                  Severity.ERROR,
                  DiagnosticStage.LEXICAL,
                  source.sourcePath(),
                  source.span(index, index + width))
              .field("codePoint", UnicodeRules.codePointNotation(codePoint))
              .expected("BSBの構文で使用できる文字")
              .actual(UnicodeRules.codePointNotation(codePoint))
              .fix("この文字を削除してください")
              .build());
      possibleDefinitionEnd = false;
      index += width;
    }

    /** トークンを生成してリストに追加します（トークン数上限のカウント・検証を含む）。 */
    private void addToken(TokenKind kind, int start, int end, String value) {
      addToken(kind, start, end, value, null);
    }

    private void addToken(
        TokenKind kind, int start, int end, String value, RegexLiteralMetadata regexMetadata) {
      SourceSpan span =
          new SourceSpan(positionCursor.positionAt(start), positionCursor.positionAt(end));
      if (kind.countsTowardLimit()) {
        int observed = countedTokenCount + 1;
        if (observed > MAX_TOKENS) {
          diagnostics.add(
              Diagnostic.builder(
                      DiagnosticCode.E_TOKEN_LIMIT,
                      Severity.ERROR,
                      DiagnosticStage.LEXICAL,
                      source.sourcePath(),
                      span)
                  .limit("tokens", MAX_TOKENS, observed)
                  .expected(MAX_TOKENS + "トークン以下")
                  .actual(observed + "トークン目")
                  .build());
          tokenLimitReached = true;
          return;
        }
        countedTokenCount = observed;
      }
      tokens.add(
          new Token(
              kind,
              source.text().substring(start, end),
              value,
              span,
              java.util.Optional.ofNullable(regexMetadata)));
    }
  }

  private static boolean isIdentifierSuffix(String suffix) {
    if (suffix.isEmpty()) {
      return false;
    }
    String normalized = UnicodeRules.normalizeIdentifier(suffix);
    return UnicodeRules.isValidIdentifier(normalized);
  }

  /** 候補先頭にある文法的に完成した数値の最長末尾を返します。 */
  private static int validNumberPrefixEnd(String candidate) {
    int cursor = candidate.startsWith("-") ? 1 : 0;
    if (cursor >= candidate.length() || !isAsciiDigit(candidate.charAt(cursor))) {
      return -1;
    }
    if (candidate.charAt(cursor) == '0') {
      cursor++;
    } else {
      while (cursor < candidate.length() && isAsciiDigit(candidate.charAt(cursor))) {
        cursor++;
      }
    }
    int validEnd = cursor;
    if (cursor < candidate.length() && candidate.charAt(cursor) == '.') {
      cursor++;
      int fractionStart = cursor;
      while (cursor < candidate.length() && isAsciiDigit(candidate.charAt(cursor))) {
        cursor++;
      }
      if (cursor == fractionStart) {
        return validEnd;
      }
      validEnd = cursor;
    }
    if (cursor < candidate.length() && isExponentMarker(candidate.charAt(cursor))) {
      cursor++;
      if (cursor < candidate.length()
          && (candidate.charAt(cursor) == '+' || candidate.charAt(cursor) == '-')) {
        cursor++;
      }
      int exponentStart = cursor;
      while (cursor < candidate.length() && isAsciiDigit(candidate.charAt(cursor))) {
        cursor++;
      }
      if (cursor > exponentStart) {
        validEnd = cursor;
      }
    }
    return validEnd;
  }

  private static boolean isAsciiDigit(char value) {
    return value >= '0' && value <= '9';
  }

  private static boolean isExponentMarker(char value) {
    return value == 'e' || value == 'E';
  }

  private static boolean startsWithExponentMarker(String value) {
    return !value.isEmpty() && isExponentMarker(value.charAt(0));
  }

  private static boolean containsExponentMarker(String value) {
    for (int index = 0; index < value.length(); index++) {
      if (isExponentMarker(value.charAt(index))) {
        return true;
      }
    }
    return false;
  }

  private static String stripLeadingZeros(String value) {
    boolean negative = value.startsWith("-");
    int start = negative ? 1 : 0;
    int index = start;
    while (index < value.length() - 1 && value.charAt(index) == '0') {
      index++;
    }
    return (negative ? "-" : "") + value.substring(index);
  }

  private static boolean isAsciiHexDigit(char value) {
    return (value >= '0' && value <= '9')
        || (value >= 'A' && value <= 'F')
        || (value >= 'a' && value <= 'f');
  }

  private static int utf8Length(int codePoint) {
    if (codePoint <= 0x7F) {
      return 1;
    }
    if (codePoint <= 0x7FF) {
      return 2;
    }
    if (codePoint <= 0xFFFF) {
      return 3;
    }
    return 4;
  }

  /** エスケープ解析の結果レコード */
  private record EscapeResult(boolean valid, int codePoint) {
    private static EscapeResult valid(int codePoint) {
      return new EscapeResult(true, codePoint);
    }

    private static EscapeResult invalid() {
      return new EscapeResult(false, -1);
    }
  }
}
