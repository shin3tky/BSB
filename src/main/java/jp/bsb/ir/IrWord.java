package jp.bsb.ir;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import jp.bsb.binding.BindingStorage;

/**
 * 利用者定義単語1個を、実行順のIR命令列へ変換したものです。
 *
 * @param symbolId 単語の内部識別子
 * @param name 正規化済みの単語名
 * @param instructions 最後が必ずReturnである命令列
 * @param localSlots 呼出しフレームごとに確保する局所スロット表
 * @param stackEffect 検証済みの具体的な単語効果。旧来の合成IRでは空
 */
public record IrWord(
    SymbolId symbolId,
    String name,
    List<IrInstruction> instructions,
    List<IrStorageSlot> localSlots,
    Optional<IrStackEffect> stackEffect) {
  /**
   * 制御フローまでの局所スロットを持たないIR単語を作ります。
   *
   * @param symbolId 単語の内部識別子
   * @param name 正規化済みの単語名
   * @param instructions 最後が必ずReturnである命令列
   */
  public IrWord(SymbolId symbolId, String name, List<IrInstruction> instructions) {
    this(symbolId, name, instructions, List.of(), Optional.empty());
  }

  /**
   * 具体的な単語効果を持たない、束縛までの合成IR単語を作ります。
   *
   * @param symbolId 単語の内部識別子
   * @param name 正規化済みの単語名
   * @param instructions 最後が必ずReturnである命令列
   * @param localSlots 呼出しフレームごとに確保する局所スロット表
   */
  public IrWord(
      SymbolId symbolId,
      String name,
      List<IrInstruction> instructions,
      List<IrStorageSlot> localSlots) {
    this(symbolId, name, instructions, localSlots, Optional.empty());
  }

  /**
   * 解析済みの具体的なスタック効果を持つIR単語を作ります。
   *
   * @param symbolId 単語の内部識別子
   * @param name 正規化済みの単語名
   * @param instructions 最後が必ずReturnである命令列
   * @param localSlots 呼出しフレームごとに確保する局所スロット表
   * @param stackEffect 検証済みの具体的な入力・出力型
   */
  public IrWord(
      SymbolId symbolId,
      String name,
      List<IrInstruction> instructions,
      List<IrStorageSlot> localSlots,
      IrStackEffect stackEffect) {
    this(symbolId, name, instructions, localSlots, Optional.of(stackEffect));
  }

  /** 識別子、名前、命令列を検証して不変コピーにします。 */
  public IrWord {
    Objects.requireNonNull(symbolId, "symbolId");
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("word name must not be blank");
    }
    instructions = List.copyOf(instructions);
    localSlots = List.copyOf(localSlots);
    stackEffect = Objects.requireNonNull(stackEffect, "stackEffect");
    if (instructions.isEmpty() || !(instructions.getLast() instanceof Return)) {
      throw new IllegalArgumentException("an IR word must end with Return");
    }
    validateLocalSlots(name, localSlots);
    validateControlTargets(instructions);
    validateArrayLoopStructure(instructions);
    validateLocalStorageFlow(instructions, localSlots);
  }

  /**
   * 呼出しフレームが確保する局所値数を返します。
   *
   * @return 局所スロット数
   */
  public int localSlotCount() {
    return localSlots.size();
  }

  /**
   * 制御命令の飛び先が、この単語自身の命令境界に収まることを検証します。
   *
   * <p>命令位置は単語ごとの整数なので、別単語の位置を表せません。さらに上限をここで調べることで、不正IRを実行器へ渡す前に止めます。
   */
  private static void validateControlTargets(List<IrInstruction> instructions) {
    for (IrInstruction instruction : instructions) {
      int target =
          switch (instruction) {
            case Jump jump -> jump.targetIndex();
            case BranchIfFalse branch -> branch.targetIndex();
            case CountedLoopStart start -> start.exitTargetIndex();
            case CountedLoopNext next -> next.bodyTargetIndex();
            case ArrayLoopStart start -> start.exitTargetIndex();
            case ArrayLoopNext next -> next.bodyTargetIndex();
            default -> -1;
          };
      if (target >= instructions.size()) {
        throw new IllegalArgumentException(
            "control target must stay inside its IR word: " + target);
      }
    }
  }

  /** 構造化された配列反復の開始・次命令・本体・出口が正しく対応することを検証します。 */
  private static void validateArrayLoopStructure(List<IrInstruction> instructions) {
    var starts = new ArrayDeque<Integer>();
    for (int index = 0; index < instructions.size(); index++) {
      IrInstruction instruction = instructions.get(index);
      if (instruction instanceof ArrayLoopStart) {
        starts.addLast(index);
      } else if (instruction instanceof ArrayLoopNext next) {
        if (starts.isEmpty()) {
          throw new IllegalArgumentException("ArrayLoopNext has no matching ArrayLoopStart");
        }
        int startIndex = starts.removeLast();
        ArrayLoopStart start = (ArrayLoopStart) instructions.get(startIndex);
        if (next.bodyTargetIndex() != startIndex + 1) {
          throw new IllegalArgumentException(
              "ArrayLoopNext must target the body after its matching start");
        }
        if (start.exitTargetIndex() != index + 1) {
          throw new IllegalArgumentException(
              "ArrayLoopStart must target the instruction after its matching next");
        }
      }
    }
    if (!starts.isEmpty()) {
      throw new IllegalArgumentException("ArrayLoopStart has no matching ArrayLoopNext");
    }
  }

  private static void validateLocalSlots(String wordName, List<IrStorageSlot> slots) {
    for (int index = 0; index < slots.size(); index++) {
      IrStorageSlot slot = Objects.requireNonNull(slots.get(index), "local slot");
      if (slot.storage() != BindingStorage.LOCAL) {
        throw new IllegalArgumentException("an IR word may contain only local slots");
      }
      if (slot.slotIndex() != index) {
        throw new IllegalArgumentException("local slot indices must be contiguous from zero");
      }
      if (!slot.ownerWord().orElseThrow().equals(wordName)) {
        throw new IllegalArgumentException("a local slot must belong to its IR word");
      }
    }
  }

  /**
   * 制御フローの全到達経路でInitializeLocalがLoad/Storeに先行することを検証します。
   *
   * <p>合流点では初期化済み集合の共通部分だけを残します。これにより片側の分岐でしか初期化されないスロットや、ループ本体に入らない経路の利用をIR構築時に拒否できます。
   */
  private static void validateLocalStorageFlow(
      List<IrInstruction> instructions, List<IrStorageSlot> localSlots) {
    Set<IrStorageSlot> knownSlots = Set.copyOf(localSlots);
    for (IrInstruction instruction : instructions) {
      if (instruction instanceof StorageInstruction storage
          && storage.slot().storage() == BindingStorage.LOCAL
          && !knownSlots.contains(storage.slot())) {
        throw new IllegalArgumentException(
            "local instruction refers to a slot outside its IR word");
      }
    }

    var incoming = new BitSet[instructions.size()];
    incoming[0] = new BitSet(localSlots.size());
    var work = new ArrayDeque<Integer>();
    work.add(0);
    while (!work.isEmpty()) {
      int index = work.removeFirst();
      BitSet outgoing = (BitSet) incoming[index].clone();
      IrInstruction instruction = instructions.get(index);
      if (instruction instanceof InitializeLocal initialize) {
        outgoing.set(initialize.slotIndex());
      }
      for (int successor : successors(index, instruction, instructions.size())) {
        BitSet previous = incoming[successor];
        BitSet merged;
        if (previous == null) {
          merged = (BitSet) outgoing.clone();
        } else {
          merged = (BitSet) previous.clone();
          merged.and(outgoing);
        }
        if (!merged.equals(previous)) {
          incoming[successor] = merged;
          work.add(successor);
        }
      }
    }

    for (int index = 0; index < instructions.size(); index++) {
      BitSet initialized = incoming[index];
      if (initialized == null) {
        continue;
      }
      IrInstruction instruction = instructions.get(index);
      if ((instruction instanceof LoadLocal || instruction instanceof StoreLocal)
          && !initialized.get(((StorageInstruction) instruction).slotIndex())) {
        throw new IllegalArgumentException("local Load/Store requires definite initialization");
      }
    }
  }

  private static List<Integer> successors(
      int index, IrInstruction instruction, int instructionCount) {
    int next = index + 1;
    return switch (instruction) {
      case Return ignored -> List.of();
      case Jump jump -> List.of(jump.targetIndex());
      case BranchIfFalse branch -> withFallthrough(branch.targetIndex(), next, instructionCount);
      case CountedLoopStart start ->
          withFallthrough(start.exitTargetIndex(), next, instructionCount);
      case CountedLoopNext loopNext ->
          withFallthrough(loopNext.bodyTargetIndex(), next, instructionCount);
      case ArrayLoopStart start -> withFallthrough(start.exitTargetIndex(), next, instructionCount);
      case ArrayLoopNext loopNext ->
          withFallthrough(loopNext.bodyTargetIndex(), next, instructionCount);
      default -> next < instructionCount ? List.of(next) : List.of();
    };
  }

  private static List<Integer> withFallthrough(int target, int next, int instructionCount) {
    if (next >= instructionCount || target == next) {
      return List.of(target);
    }
    return List.of(target, next);
  }
}
