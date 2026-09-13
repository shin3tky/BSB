package jp.bsb.ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import jp.bsb.binding.BindingId;
import jp.bsb.binding.BindingStorage;
import jp.bsb.stdlib.BuiltinWord;

/**
 * 名前解決済みIRプログラムです。
 *
 * <p>利用者定義と組み込み語は同じ {@link SymbolId} 名前空間を共有します。実行器は文字列検索を繰り返さず、IDから呼出先を一意に選べます。
 *
 * @param sourcePath 診断で使うソース識別パス
 * @param mainSymbol エントリポイントの識別子
 * @param userWords 利用者定義IDからIR単語への表
 * @param builtinWords 組み込みIDから共有辞書項目への表
 * @param instructionCount 専用大域初期化単語を含む全IR単語の命令数合計
 * @param globalInitializer 大域宣言がある場合だけ存在する専用初期化単語
 * @param globalSlots プログラム全体で共有する大域スロット表
 */
public record IrProgram(
    String sourcePath,
    SymbolId mainSymbol,
    Map<SymbolId, IrWord> userWords,
    Map<SymbolId, BuiltinWord> builtinWords,
    int instructionCount,
    Optional<IrWord> globalInitializer,
    List<IrStorageSlot> globalSlots) {
  /**
   * 制御フローまでの保存領域を持たないIRプログラムを作ります。
   *
   * @param sourcePath 診断で使うソース識別パス
   * @param mainSymbol エントリポイントの識別子
   * @param userWords 利用者定義IDからIR単語への表
   * @param builtinWords 組み込みIDから共有辞書項目への表
   * @param instructionCount 全IR単語の命令数合計
   */
  public IrProgram(
      String sourcePath,
      SymbolId mainSymbol,
      Map<SymbolId, IrWord> userWords,
      Map<SymbolId, BuiltinWord> builtinWords,
      int instructionCount) {
    this(
        sourcePath,
        mainSymbol,
        userWords,
        builtinWords,
        instructionCount,
        Optional.empty(),
        List.of());
  }

  /** 各表を挿入順の不変マップとして保持し、エントリポイントを検証します。 */
  public IrProgram {
    if (sourcePath == null || sourcePath.isBlank()) {
      throw new IllegalArgumentException("sourcePath must not be blank");
    }
    Objects.requireNonNull(mainSymbol, "mainSymbol");
    userWords = Collections.unmodifiableMap(new LinkedHashMap<>(userWords));
    builtinWords = Collections.unmodifiableMap(new LinkedHashMap<>(builtinWords));
    globalInitializer = Objects.requireNonNull(globalInitializer, "globalInitializer");
    globalSlots = List.copyOf(globalSlots);
    if (!userWords.containsKey(mainSymbol)) {
      throw new IllegalArgumentException("mainSymbol must identify a user word");
    }
    if (instructionCount < 1) {
      throw new IllegalArgumentException("instructionCount must be positive");
    }
    validateGlobalSlots(globalSlots);
    validateStorageProgram(userWords, builtinWords, globalInitializer, globalSlots);
    IrVerifier.validate(userWords, builtinWords, globalInitializer);
  }

  /**
   * 利用者定義単語を検索します。
   *
   * @param symbolId 呼出先ID
   * @return 一致した利用者定義
   */
  public Optional<IrWord> findUserWord(SymbolId symbolId) {
    return Optional.ofNullable(userWords.get(symbolId));
  }

  /**
   * 組み込み単語を検索します。
   *
   * @param symbolId 呼出先ID
   * @return 一致した辞書項目
   */
  public Optional<BuiltinWord> findBuiltinWord(SymbolId symbolId) {
    return Optional.ofNullable(builtinWords.get(symbolId));
  }

  /**
   * メイン単語を返します。
   *
   * @return エントリポイントのIR単語
   */
  public IrWord mainWord() {
    return userWords.get(mainSymbol);
  }

  /**
   * 実行開始時の単語順を返します。大域宣言があれば専用初期化単語、続いてメインです。
   *
   * @return 1個または2個の開始単語
   */
  public List<IrWord> startupWords() {
    var result = new ArrayList<IrWord>(2);
    globalInitializer.ifPresent(result::add);
    result.add(mainWord());
    return List.copyOf(result);
  }

  /**
   * プログラムが確保する大域値数を返します。
   *
   * @return 大域スロット数
   */
  public int globalSlotCount() {
    return globalSlots.size();
  }

  private static void validateGlobalSlots(List<IrStorageSlot> slots) {
    for (int index = 0; index < slots.size(); index++) {
      IrStorageSlot slot = Objects.requireNonNull(slots.get(index), "global slot");
      if (slot.storage() != BindingStorage.GLOBAL) {
        throw new IllegalArgumentException("an IR program may contain only global slots");
      }
      if (slot.slotIndex() != index) {
        throw new IllegalArgumentException("global slot indices must be contiguous from zero");
      }
    }
  }

  private static void validateStorageProgram(
      Map<SymbolId, IrWord> userWords,
      Map<SymbolId, BuiltinWord> builtinWords,
      Optional<IrWord> globalInitializer,
      List<IrStorageSlot> globalSlots) {
    if (globalInitializer.isPresent() != !globalSlots.isEmpty()) {
      throw new IllegalArgumentException(
          "global slots and the global initializer must either both exist or both be absent");
    }
    globalInitializer.ifPresent(
        initializer -> {
          if (userWords.containsKey(initializer.symbolId())
              || builtinWords.containsKey(initializer.symbolId())) {
            throw new IllegalArgumentException(
                "the global initializer must have a private symbol id");
          }
        });
    Set<IrStorageSlot> knownGlobals = Set.copyOf(globalSlots);
    var allBindingIds = new HashSet<BindingId>();
    for (IrStorageSlot slot : globalSlots) {
      if (!allBindingIds.add(slot.bindingId())) {
        throw new IllegalArgumentException("a binding id may identify only one storage slot");
      }
    }
    for (IrWord word : userWords.values()) {
      for (IrStorageSlot slot : word.localSlots()) {
        if (!allBindingIds.add(slot.bindingId())) {
          throw new IllegalArgumentException("a binding id may identify only one storage slot");
        }
      }
      validateUserWordStorage(word, knownGlobals);
    }
    globalInitializer.ifPresent(
        initializer -> validateGlobalInitializer(initializer, knownGlobals, globalSlots));
  }

  private static void validateUserWordStorage(IrWord word, Set<IrStorageSlot> knownGlobals) {
    for (IrInstruction instruction : word.instructions()) {
      if (instruction instanceof InitializeGlobal) {
        throw new IllegalArgumentException(
            "InitializeGlobal may appear only in its dedicated word");
      }
      if (instruction instanceof StorageInstruction storage
          && storage.slot().storage() == BindingStorage.GLOBAL
          && !knownGlobals.contains(storage.slot())) {
        throw new IllegalArgumentException("global instruction refers to an unknown slot");
      }
    }
  }

  private static void validateGlobalInitializer(
      IrWord initializer, Set<IrStorageSlot> knownGlobals, List<IrStorageSlot> globalSlots) {
    if (!initializer.name().equals("<大域初期化>")) {
      throw new IllegalArgumentException("the global initializer must have its normative name");
    }
    if (!initializer.localSlots().isEmpty()) {
      throw new IllegalArgumentException("the global initializer may not own local slots");
    }
    var initialized = new HashSet<IrStorageSlot>();
    int nextInitialization = 0;
    for (IrInstruction instruction : initializer.instructions()) {
      if (instruction instanceof InitializeGlobal initialize) {
        if (!knownGlobals.contains(initialize.slot())) {
          throw new IllegalArgumentException("global initializer refers to an unknown slot");
        }
        if (nextInitialization >= globalSlots.size()
            || !initialize.slot().equals(globalSlots.get(nextInitialization))) {
          throw new IllegalArgumentException("global slots must be initialized in source order");
        }
        initialized.add(initialize.slot());
        nextInitialization++;
      } else if (instruction instanceof LoadGlobal load) {
        if (!knownGlobals.contains(load.slot()) || !initialized.contains(load.slot())) {
          throw new IllegalArgumentException("LoadGlobal requires prior global initialization");
        }
      } else if (instruction instanceof StorageInstruction) {
        throw new IllegalArgumentException(
            "the global initializer may contain only global Initialize/Load instructions");
      }
    }
    if (nextInitialization != globalSlots.size()) {
      throw new IllegalArgumentException("every global slot must be initialized exactly once");
    }
  }
}
