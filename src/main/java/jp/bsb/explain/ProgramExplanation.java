package jp.bsb.explain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import jp.bsb.diagnostics.SourceSpan;

/** 検査済みプログラムから作る、CLI形式に依存しない不変の静的説明です。 */
public record ProgramExplanation(
    Summary summary,
    List<BuiltinWordEntry> builtinWords,
    List<UserWordEntry> userWords,
    List<ScopeEntry> scopes,
    List<BindingEntry> bindings,
    ParameterizedCapabilities parameterizedCapabilities) {
  /** 回復可能JSONまでの説明モデルを空のパラメータ付き能力で作ります。 */
  public ProgramExplanation(
      Summary summary,
      List<BuiltinWordEntry> builtinWords,
      List<UserWordEntry> userWords,
      List<ScopeEntry> scopes,
      List<BindingEntry> bindings) {
    this(summary, builtinWords, userWords, scopes, bindings, ParameterizedCapabilities.empty());
  }

  /** 全コレクションを不変コピーにします。 */
  public ProgramExplanation {
    Objects.requireNonNull(summary, "summary");
    builtinWords = List.copyOf(builtinWords);
    userWords = List.copyOf(userWords);
    scopes = List.copyOf(scopes);
    bindings = List.copyOf(bindings);
    Objects.requireNonNull(parameterizedCapabilities, "parameterizedCapabilities");
  }

  /** パラメータ付き能力説明の独立した版1モデルです。 */
  public record ParameterizedCapabilities(
      int schemaVersion,
      List<ConnectionDeclarationEntry> declarations,
      List<WorkspaceDeclarationEntry> workspaceDeclarations,
      List<ResourceRequirementEntry> summaryRequirements,
      List<ParameterizedUserWordEntry> userWords) {
    /** 多次元配列までの論理接続だけを持つ説明を作ります。 */
    public ParameterizedCapabilities(
        int schemaVersion,
        List<ConnectionDeclarationEntry> declarations,
        List<ResourceRequirementEntry> summaryRequirements,
        List<ParameterizedUserWordEntry> userWords) {
      this(schemaVersion, declarations, List.of(), summaryRequirements, userWords);
    }

    public ParameterizedCapabilities {
      if (schemaVersion != 1) {
        throw new IllegalArgumentException("only parameterized capability schema version 1 exists");
      }
      declarations = List.copyOf(declarations);
      workspaceDeclarations = List.copyOf(workspaceDeclarations);
      summaryRequirements = List.copyOf(summaryRequirements);
      userWords = List.copyOf(userWords);
    }

    static ParameterizedCapabilities empty() {
      return new ParameterizedCapabilities(1, List.of(), List.of(), List.of(), List.of());
    }
  }

  /** 論理接続宣言と直接利用です。 */
  public record ConnectionDeclarationEntry(
      String name, String spelling, SourceSpan declaration, List<ConnectionUseEntry> uses) {
    public ConnectionDeclarationEntry {
      name = requireText(name, "name");
      spelling = requireText(spelling, "spelling");
      Objects.requireNonNull(declaration, "declaration");
      uses = List.copyOf(uses);
    }
  }

  /** 静的接続引数の利用位置です。 */
  public record ConnectionUseEntry(
      String ownerWord, String operation, boolean reachableFromMain, SourceSpan location) {
    public ConnectionUseEntry {
      ownerWord = requireText(ownerWord, "ownerWord");
      operation = requireText(operation, "operation");
      Objects.requireNonNull(location, "location");
    }
  }

  /** 作業領域宣言と直接利用です。 */
  public record WorkspaceDeclarationEntry(
      String name, String spelling, SourceSpan declaration, List<ConnectionUseEntry> uses) {
    public WorkspaceDeclarationEntry {
      name = requireText(name, "name");
      spelling = requireText(spelling, "spelling");
      Objects.requireNonNull(declaration, "declaration");
      uses = List.copyOf(uses);
    }
  }

  /** 資源種別、名前、操作列からなる静的要求です。 */
  public record ResourceRequirementEntry(String kind, String name, List<String> operations) {
    public ResourceRequirementEntry {
      kind = requireText(kind, "kind");
      name = requireText(name, "name");
      operations = List.copyOf(operations);
      if (operations.isEmpty()) {
        throw new IllegalArgumentException("operations must not be empty");
      }
    }
  }

  /** 利用者定義語ごとの直接要求と推移要求です。 */
  public record ParameterizedUserWordEntry(
      String name,
      List<ResourceRequirementEntry> directRequirements,
      List<ResourceRequirementEntry> requirements) {
    public ParameterizedUserWordEntry {
      name = requireText(name, "name");
      directRequirements = List.copyOf(directRequirements);
      requirements = List.copyOf(requirements);
    }
  }

  /** `メイン`から静的に到達する語と外部要求の要約です。 */
  public record Summary(
      String entryPoint,
      List<String> reachableUserWords,
      List<String> reachableBuiltinWords,
      List<String> capabilities,
      List<String> effects) {
    /** 必須文字列と一覧を不変にします。 */
    public Summary {
      entryPoint = requireText(entryPoint, "entryPoint");
      reachableUserWords = List.copyOf(reachableUserWords);
      reachableBuiltinWords = List.copyOf(reachableBuiltinWords);
      capabilities = List.copyOf(capabilities);
      effects = List.copyOf(effects);
    }
  }

  /** JSONへ公開できる組み込み辞書項目です。 */
  public record BuiltinWordEntry(
      String name,
      List<String> aliases,
      String description,
      StackEffectEntry stackEffect,
      String typeRule,
      List<String> capabilities,
      List<String> effects,
      String featureGroup,
      String example) {
    /** 必須値と一覧を検証します。 */
    public BuiltinWordEntry {
      name = requireText(name, "name");
      aliases = List.copyOf(aliases);
      description = requireText(description, "description");
      Objects.requireNonNull(stackEffect, "stackEffect");
      typeRule = requireText(typeRule, "typeRule");
      capabilities = List.copyOf(capabilities);
      effects = List.copyOf(effects);
      featureGroup = requireText(featureGroup, "featureGroup");
      example = requireText(example, "example");
    }
  }

  /** 利用者定義語の検証済み効果と静的な外部要求です。 */
  public record UserWordEntry(
      String name,
      String spelling,
      SourceSpan declaration,
      StackEffectEntry stackEffect,
      boolean reachableFromMain,
      List<String> directCapabilities,
      List<String> capabilities,
      List<String> directEffects,
      List<String> effects) {
    /** 必須値と一覧を検証します。 */
    public UserWordEntry {
      name = requireText(name, "name");
      spelling = requireText(spelling, "spelling");
      Objects.requireNonNull(declaration, "declaration");
      Objects.requireNonNull(stackEffect, "stackEffect");
      directCapabilities = List.copyOf(directCapabilities);
      capabilities = List.copyOf(capabilities);
      directEffects = List.copyOf(directEffects);
      effects = List.copyOf(effects);
    }
  }

  /** 入力・出力型列と通常復帰可能性です。 */
  public record StackEffectEntry(
      List<String> inputs, List<String> outputs, boolean returnsNormally) {
    /** 型列を不変コピーにします。 */
    public StackEffectEntry {
      inputs = List.copyOf(inputs);
      outputs = List.copyOf(outputs);
    }
  }

  /** 親先行の字句スコープです。 */
  public record ScopeEntry(
      String id,
      String kind,
      Optional<String> parentId,
      Optional<String> ownerWord,
      SourceSpan location) {
    /** 必須値と省略値を検証します。 */
    public ScopeEntry {
      id = requireText(id, "id");
      kind = requireText(kind, "kind");
      parentId = Objects.requireNonNull(parentId, "parentId");
      ownerWord = Objects.requireNonNull(ownerWord, "ownerWord");
      Objects.requireNonNull(location, "location");
    }
  }

  /** 宣言を主とし、全利用をまとめた値束縛です。 */
  public record BindingEntry(
      String id,
      String name,
      String spelling,
      String kind,
      String storage,
      String scopeId,
      String type,
      OptionalInt initializationOrder,
      SourceSpan declaration,
      List<BindingUseEntry> uses) {
    /** 必須値と利用一覧を検証します。 */
    public BindingEntry {
      id = requireText(id, "id");
      name = requireText(name, "name");
      spelling = requireText(spelling, "spelling");
      kind = requireText(kind, "kind");
      storage = requireText(storage, "storage");
      scopeId = requireText(scopeId, "scopeId");
      type = requireText(type, "type");
      initializationOrder = Objects.requireNonNull(initializationOrder, "initializationOrder");
      Objects.requireNonNull(declaration, "declaration");
      uses = List.copyOf(uses);
    }
  }

  /** 1個のreadまたはwrite利用です。 */
  public record BindingUseEntry(String spelling, String kind, SourceSpan location) {
    /** 必須値を検証します。 */
    public BindingUseEntry {
      spelling = requireText(spelling, "spelling");
      kind = requireText(kind, "kind");
      Objects.requireNonNull(location, "location");
    }
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value;
  }
}
