package jp.bsb.analyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.ValueType;

/**
 * 実行時の値そのものではなく、値の型と由来位置だけを積む不変の抽象スタックです。
 *
 * <p>【コンピュータ科学の観点：抽象実行】静的検査では、例えば整数の値が {@code 42} か {@code 100}
 * かを知る必要はありません。「ここには整数がある」という情報だけで、多くの誤りを実行前に検出できます。また、不変オブジェクトにしておくと、分岐の入口で同じスタックを
 * 2本の経路へ安全に共有できます。一方の経路で値を積んでも、もう一方の経路は変化しません。
 */
public final class AbstractStack {
  private static final AbstractStack EMPTY = new AbstractStack(List.of());

  private final List<Slot> slots;

  private AbstractStack(List<Slot> slots) {
    this.slots = List.copyOf(slots);
  }

  /**
   * 空の抽象スタックを返します。
   *
   * @return 共有可能な空スタック
   */
  public static AbstractStack empty() {
    return EMPTY;
  }

  /**
   * 同じ由来位置を持つ型列から抽象スタックを作ります。
   *
   * <p>型列は左から右へ、スタックの底から頂上の順です。
   *
   * @param types 底から頂上へ並べた型
   * @param origin 各値の由来位置
   * @return 作成した抽象スタック
   */
  public static AbstractStack ofTypes(List<ValueType> types, SourceSpan origin) {
    Objects.requireNonNull(types, "types");
    Objects.requireNonNull(origin, "origin");
    var slots = new ArrayList<Slot>(types.size());
    for (ValueType type : types) {
      slots.add(new Slot(type, origin));
    }
    return slots.isEmpty() ? EMPTY : new AbstractStack(slots);
  }

  /**
   * 頂上へ値の型を1個積んだ、新しいスタックを返します。
   *
   * @param type 積む値の型
   * @param origin 値が作られたソース範囲
   * @return 元のスタックを変更せずに値を積んだスタック
   */
  public AbstractStack push(ValueType type, SourceSpan origin) {
    return push(new Slot(type, origin));
  }

  /**
   * 頂上へスロットを1個積んだ、新しいスタックを返します。
   *
   * @param slot 積む型と由来位置
   * @return 元のスタックを変更せずにスロットを積んだスタック
   */
  public AbstractStack push(Slot slot) {
    Objects.requireNonNull(slot, "slot");
    var result = new ArrayList<>(slots);
    result.add(slot);
    return new AbstractStack(result);
  }

  /**
   * 頂上から指定個数を除いた、新しいスタックを返します。
   *
   * @param count 取り除く値の個数
   * @return 元のスタックを変更せずに値を除いたスタック
   * @throws IllegalArgumentException 個数が負、または現在の要素数を超える場合
   */
  public AbstractStack removeTop(int count) {
    if (count < 0 || count > slots.size()) {
      throw new IllegalArgumentException("count must be between zero and stack size");
    }
    if (count == 0) {
      return this;
    }
    if (count == slots.size()) {
      return EMPTY;
    }
    return new AbstractStack(slots.subList(0, slots.size() - count));
  }

  /**
   * スタック頂上のスロットを返します。
   *
   * @return 頂上のスロット。空スタックなら空の{@link Optional}
   */
  public Optional<Slot> top() {
    return slots.isEmpty() ? Optional.empty() : Optional.of(slots.getLast());
  }

  /**
   * スタックの底から頂上へ並べた、不変のスロット一覧を返します。
   *
   * @return 不変のスロット一覧
   */
  public List<Slot> slots() {
    return slots;
  }

  /**
   * スタックの底から頂上へ並べた型だけを返します。
   *
   * @return 型の一覧
   */
  public List<ValueType> types() {
    return slots.stream().map(Slot::type).toList();
  }

  /**
   * 積まれている値の個数を返します。
   *
   * @return 値の個数
   */
  public int size() {
    return slots.size();
  }

  /**
   * 値が1個もないかを返します。
   *
   * @return 空ならtrue
   */
  public boolean isEmpty() {
    return slots.isEmpty();
  }

  /**
   * 値の由来位置を無視して、個数と各位置の型が一致するかを返します。
   *
   * <p>分岐の合流で必要なのは「その後の処理が同じ型として扱えるか」であり、値を作った行が同じかではありません。
   *
   * @param other 比較する抽象スタック
   * @return 個数と型列が一致すればtrue
   */
  public boolean hasSameShape(AbstractStack other) {
    Objects.requireNonNull(other, "other");
    return types().equals(other.types());
  }

  /**
   * 個数が同じ2スタックについて、型が最初に異なる底からの添字を返します。すべて一致する場合は空です。
   *
   * @param other 比較する抽象スタック
   * @return 最初の型不一致位置。一致する場合は空
   * @throws IllegalArgumentException スタックの個数が異なる場合
   */
  public Optional<Integer> firstTypeMismatch(AbstractStack other) {
    Objects.requireNonNull(other, "other");
    if (size() != other.size()) {
      throw new IllegalArgumentException("stack sizes must match");
    }
    for (int index = 0; index < size(); index++) {
      if (!slots.get(index).type().equals(other.slots.get(index).type())) {
        return Optional.of(index);
      }
    }
    return Optional.empty();
  }

  @Override
  public String toString() {
    return types().toString();
  }

  /**
   * 抽象スタックの1要素です。
   *
   * @param type 値の静的な型
   * @param origin 値がスタックへ置かれたソース範囲
   */
  public record Slot(ValueType type, SourceSpan origin) {
    /** 型と由来位置が存在することを検査します。 */
    public Slot {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(origin, "origin");
    }
  }
}
