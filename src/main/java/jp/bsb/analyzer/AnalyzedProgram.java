package jp.bsb.analyzer;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import jp.bsb.binding.NameResolution;
import jp.bsb.binding.WordName;
import jp.bsb.frontend.ast.ArrayLiteral;
import jp.bsb.frontend.ast.BodyElement;
import jp.bsb.frontend.ast.Program;
import jp.bsb.frontend.ast.WordCall;
import jp.bsb.frontend.ast.WordDefinition;
import jp.bsb.stdlib.ValueType;

/**
 * 名前・型・スタック効果の検査に成功し、IR生成へ渡せるプログラムです。
 *
 * <p>構文ASTと検証済みシグネチャを一緒に保持することで、IR生成側が未検査の宣言を誤って利用することを防ぎます。
 *
 * @param syntax 検査済みの元AST
 * @param userWordSignatures 正規名から利用者定義シグネチャへの対応
 * @param unreachableElements IR生成時に省略する、構造的に到達不能な本体要素
 * @param nameResolution 単語・定数・変数の宣言、字句スコープ、値利用の解決結果
 * @param arrayLiteralTypes 到達可能な配列リテラルと、検査で確定した具体的な配列型
 * @param callSignatures 到達可能な呼出しと、型変数を具体化した入力・出力型
 * @param irTypeMetadataComplete IR型検証に必要な呼出し効果を解析器が完全に保持したか
 * @param nonReturningWords 通常出口を持たない利用者定義単語名
 * @param logicalConnectionResolution 論理接続の宣言と静的引数の解決結果
 */
public record AnalyzedProgram(
    Program syntax,
    Map<String, WordSignature> userWordSignatures,
    Set<BodyElement> unreachableElements,
    NameResolution nameResolution,
    Map<ArrayLiteral, ValueType> arrayLiteralTypes,
    Map<WordCall, WordSignature> callSignatures,
    boolean irTypeMetadataComplete,
    Set<String> nonReturningWords,
    LogicalConnectionResolution logicalConnectionResolution,
    WorkspaceResolution workspaceResolution) {
  /** 多次元配列までの完全な解析結果を作る互換コンストラクタです。 */
  public AnalyzedProgram(
      Program syntax,
      Map<String, WordSignature> userWordSignatures,
      Set<BodyElement> unreachableElements,
      NameResolution nameResolution,
      Map<ArrayLiteral, ValueType> arrayLiteralTypes,
      Map<WordCall, WordSignature> callSignatures,
      boolean irTypeMetadataComplete,
      Set<String> nonReturningWords,
      LogicalConnectionResolution logicalConnectionResolution) {
    this(
        syntax,
        userWordSignatures,
        unreachableElements,
        nameResolution,
        arrayLiteralTypes,
        callSignatures,
        irTypeMetadataComplete,
        nonReturningWords,
        logicalConnectionResolution,
        WorkspaceResolution.empty());
  }

  /**
   * 言語コアの互換用に、到達不能要素がない検査済みプログラムを作ります。
   *
   * @param syntax 検査済みの元AST
   * @param userWordSignatures 利用者定義単語のシグネチャ表
   */
  public AnalyzedProgram(Program syntax, Map<String, WordSignature> userWordSignatures) {
    this(
        syntax,
        userWordSignatures,
        Set.of(),
        wordsOnly(syntax),
        Map.of(),
        Map.of(),
        false,
        Set.of(),
        LogicalConnectionResolution.empty());
  }

  /**
   * 制御フローの互換用に、利用者定義単語だけを共通名前空間へ登録します。
   *
   * @param syntax 検査済みの元AST
   * @param userWordSignatures 利用者定義単語のシグネチャ表
   * @param unreachableElements IR生成時に省略する到達不能要素
   */
  public AnalyzedProgram(
      Program syntax,
      Map<String, WordSignature> userWordSignatures,
      Set<BodyElement> unreachableElements) {
    this(
        syntax,
        userWordSignatures,
        unreachableElements,
        wordsOnly(syntax),
        Map.of(),
        Map.of(),
        false,
        Set.of(),
        LogicalConnectionResolution.empty());
  }

  /**
   * 束縛までの呼出し側向けに、配列リテラル型を持たない検査済みプログラムを作ります。
   *
   * @param syntax 検査済みの元AST
   * @param userWordSignatures 利用者定義単語のシグネチャ表
   * @param unreachableElements IR生成時に省略する到達不能要素
   * @param nameResolution 単語・束縛・字句スコープの解決結果
   */
  public AnalyzedProgram(
      Program syntax,
      Map<String, WordSignature> userWordSignatures,
      Set<BodyElement> unreachableElements,
      NameResolution nameResolution) {
    this(
        syntax,
        userWordSignatures,
        unreachableElements,
        nameResolution,
        Map.of(),
        Map.of(),
        false,
        Set.of(),
        LogicalConnectionResolution.empty());
  }

  /**
   * 具体化済み呼出し効果を使わない処理系の呼出し側向けに、具体化済み呼出し効果を持たないプログラムを作ります。
   *
   * @param syntax 検査済みの元AST
   * @param userWordSignatures 利用者定義単語のシグネチャ表
   * @param unreachableElements IR生成時に省略する到達不能要素
   * @param nameResolution 単語・束縛・字句スコープの解決結果
   * @param arrayLiteralTypes 配列リテラルごとに確定した具体型
   */
  public AnalyzedProgram(
      Program syntax,
      Map<String, WordSignature> userWordSignatures,
      Set<BodyElement> unreachableElements,
      NameResolution nameResolution,
      Map<ArrayLiteral, ValueType> arrayLiteralTypes) {
    this(
        syntax,
        userWordSignatures,
        unreachableElements,
        nameResolution,
        arrayLiteralTypes,
        Map.of(),
        false,
        Set.of(),
        LogicalConnectionResolution.empty());
  }

  /**
   * 呼出し効果を渡す合成IR向け互換コンストラクタです。型メタデータは未完了として扱います。
   *
   * @param syntax 検査済みの元AST
   * @param userWordSignatures 利用者定義単語のシグネチャ表
   * @param unreachableElements IR生成時に省略する到達不能要素
   * @param nameResolution 単語・束縛・字句スコープの解決結果
   * @param arrayLiteralTypes 配列リテラルごとに確定した具体型
   * @param callSignatures 呼出しごとに具体化したスタック効果
   */
  public AnalyzedProgram(
      Program syntax,
      Map<String, WordSignature> userWordSignatures,
      Set<BodyElement> unreachableElements,
      NameResolution nameResolution,
      Map<ArrayLiteral, ValueType> arrayLiteralTypes,
      Map<WordCall, WordSignature> callSignatures) {
    this(
        syntax,
        userWordSignatures,
        unreachableElements,
        nameResolution,
        arrayLiteralTypes,
        callSignatures,
        false,
        Set.of(),
        LogicalConnectionResolution.empty());
  }

  /** 文字列・正規表現までの完全なIR型メタデータを渡す互換コンストラクタです。 */
  public AnalyzedProgram(
      Program syntax,
      Map<String, WordSignature> userWordSignatures,
      Set<BodyElement> unreachableElements,
      NameResolution nameResolution,
      Map<ArrayLiteral, ValueType> arrayLiteralTypes,
      Map<WordCall, WordSignature> callSignatures,
      boolean irTypeMetadataComplete) {
    this(
        syntax,
        userWordSignatures,
        unreachableElements,
        nameResolution,
        arrayLiteralTypes,
        callSignatures,
        irTypeMetadataComplete,
        Set.of(),
        LogicalConnectionResolution.empty());
  }

  /** 回復可能JSONまでの完全な解析結果を作る互換コンストラクタです。 */
  public AnalyzedProgram(
      Program syntax,
      Map<String, WordSignature> userWordSignatures,
      Set<BodyElement> unreachableElements,
      NameResolution nameResolution,
      Map<ArrayLiteral, ValueType> arrayLiteralTypes,
      Map<WordCall, WordSignature> callSignatures,
      boolean irTypeMetadataComplete,
      Set<String> nonReturningWords) {
    this(
        syntax,
        userWordSignatures,
        unreachableElements,
        nameResolution,
        arrayLiteralTypes,
        callSignatures,
        irTypeMetadataComplete,
        nonReturningWords,
        LogicalConnectionResolution.empty());
  }

  /** ASTを検証し、シグネチャ表と到達不能要素集合を不変のコピーとして保持します。 */
  public AnalyzedProgram {
    Objects.requireNonNull(syntax, "syntax");
    userWordSignatures = Collections.unmodifiableMap(new LinkedHashMap<>(userWordSignatures));
    userWordSignatures.forEach(
        (name, signature) -> {
          if (name == null || name.isBlank() || signature == null) {
            throw new IllegalArgumentException("signature names and values must be present");
          }
        });
    Objects.requireNonNull(unreachableElements, "unreachableElements");
    Set<BodyElement> unreachableCopy = Collections.newSetFromMap(new IdentityHashMap<>());
    unreachableCopy.addAll(unreachableElements);
    unreachableElements = Collections.unmodifiableSet(unreachableCopy);
    Objects.requireNonNull(nameResolution, "nameResolution");
    Objects.requireNonNull(arrayLiteralTypes, "arrayLiteralTypes");
    var arrayTypesCopy = new IdentityHashMap<ArrayLiteral, ValueType>();
    arrayLiteralTypes.forEach(
        (literal, type) -> {
          Objects.requireNonNull(literal, "array literal");
          Objects.requireNonNull(type, "array literal type");
          if (!type.isArray()) {
            throw new IllegalArgumentException("an array literal must have an array type");
          }
          arrayTypesCopy.put(literal, type);
        });
    arrayLiteralTypes = Collections.unmodifiableMap(arrayTypesCopy);
    Objects.requireNonNull(callSignatures, "callSignatures");
    var callSignaturesCopy = new IdentityHashMap<WordCall, WordSignature>();
    callSignatures.forEach(
        (call, signature) ->
            callSignaturesCopy.put(
                Objects.requireNonNull(call, "word call"),
                Objects.requireNonNull(signature, "call signature")));
    callSignatures = Collections.unmodifiableMap(callSignaturesCopy);
    Objects.requireNonNull(nonReturningWords, "nonReturningWords");
    nonReturningWords = Set.copyOf(nonReturningWords);
    Objects.requireNonNull(logicalConnectionResolution, "logicalConnectionResolution");
    Objects.requireNonNull(workspaceResolution, "workspaceResolution");
  }

  /**
   * 利用者定義単語の検証済みシグネチャを検索します。
   *
   * @param normalizedName 正規化済みの単語名
   * @return シグネチャ。組み込み語または未知の名前なら空
   */
  public Optional<WordSignature> findUserWord(String normalizedName) {
    return Optional.ofNullable(userWordSignatures.get(normalizedName));
  }

  /**
   * 指定要素が静的解析で到達可能と判定されたかを返します。
   *
   * @param element 調べるAST要素
   * @return IRへ生成する到達可能な要素ならtrue
   */
  public boolean isReachable(BodyElement element) {
    return !unreachableElements.contains(Objects.requireNonNull(element, "element"));
  }

  /**
   * 検査で確定した配列リテラルの具体型を、AST同一性で検索します。
   *
   * @param literal 検索する配列リテラルAST
   * @return 到達可能で検査済みなら具体的な配列型、それ以外は空
   */
  public Optional<ValueType> findArrayLiteralType(ArrayLiteral literal) {
    return Optional.ofNullable(arrayLiteralTypes.get(Objects.requireNonNull(literal, "literal")));
  }

  /**
   * 型変数を具体化した呼出しの入力・出力型を、AST同一性で検索します。
   *
   * @param call 検索する単語呼出しAST
   * @return 到達可能で検査済みなら具体化済みシグネチャ、それ以外は空
   */
  public Optional<WordSignature> findCallSignature(WordCall call) {
    return Optional.ofNullable(callSignatures.get(Objects.requireNonNull(call, "call")));
  }

  /**
   * @return 指定呼出しが呼出元の次命令へ戻り得る場合はtrue
   */
  public boolean callReturnsNormally(WordCall call) {
    Objects.requireNonNull(call, "call");
    return jp.bsb.stdlib.BuiltinDictionary.find(call.name())
        .map(jp.bsb.stdlib.BuiltinWord::returnsNormally)
        .orElse(!nonReturningWords.contains(call.name()));
  }

  private static NameResolution wordsOnly(Program syntax) {
    Objects.requireNonNull(syntax, "syntax");
    return NameResolution.wordsOnly(
        syntax.elements().stream()
            .filter(WordDefinition.class::isInstance)
            .map(WordDefinition.class::cast)
            .map(
                definition ->
                    new WordName(definition.name(), definition.lexeme(), definition.nameSpan()))
            .toList());
  }
}
