package jp.bsb.runtime;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jp.bsb.ir.ParticleSource;

/**
 * 1個のIR命令について、実行前後の観測可能な状態を保存します。
 *
 * @param sequence 1始まりの実行順
 * @param word 命令を所有する利用者定義名
 * @param opcode トレース用命令名
 * @param sourceLine 1始まりのソース行
 * @param sourceColumn 1始まりの書記素クラスタ列
 * @param dataBefore 実行直前のデータスタック
 * @param dataAfter 実行直後のデータスタック
 * @param callDepthBefore 実行直前の呼出深さ
 * @param callDepthAfter 実行直後の呼出深さ
 * @param output この命令が出力したUTF-8バイト列
 * @param particles Callへ関連づけられた助詞情報
 * @param controlBefore 実行直前のループ状態。外側から内側の順
 * @param controlAfter 実行直後のループ状態。外側から内側の順
 * @param target 制御命令の移動先
 * @param branchTaken 制御命令が移動先を選んだか
 * @param storage Initialize、Load、Storeの保存領域状態。それ以外では空
 * @param effect この命令が成功時に委譲したホスト入出力能力効果。なければ空文字列
 */
public record TraceEvent(
    long sequence,
    String word,
    String opcode,
    int sourceLine,
    int sourceColumn,
    List<RuntimeValue> dataBefore,
    List<RuntimeValue> dataAfter,
    int callDepthBefore,
    int callDepthAfter,
    byte[] output,
    List<ParticleSource> particles,
    List<? extends ControlTraceState> controlBefore,
    List<? extends ControlTraceState> controlAfter,
    Optional<String> target,
    Optional<Boolean> branchTaken,
    Optional<StorageTraceState> storage,
    String effect) {
  /** 可変配列・リストをコピーし、トレースを後の実行から独立させます。 */
  public TraceEvent {
    dataBefore = List.copyOf(dataBefore);
    dataAfter = List.copyOf(dataAfter);
    output = output.clone();
    particles = List.copyOf(particles);
    controlBefore = List.copyOf(controlBefore);
    controlAfter = List.copyOf(controlAfter);
    target = Objects.requireNonNull(target, "target");
    branchTaken = Objects.requireNonNull(branchTaken, "branchTaken");
    storage = Objects.requireNonNull(storage, "storage");
    effect = Objects.requireNonNull(effect, "effect");
    if (effect.indexOf('\t') >= 0 || effect.indexOf('\n') >= 0 || effect.indexOf('\r') >= 0) {
      throw new IllegalArgumentException("trace effect must fit in one TSV field");
    }
    target.ifPresent(
        value -> {
          if (value.isBlank()) {
            throw new IllegalArgumentException("trace target must not be blank");
          }
        });
    if (target.isPresent() != branchTaken.isPresent()) {
      throw new IllegalArgumentException("control target and branch result must appear together");
    }
  }

  /**
   * 制御フローの制御列までを持つイベントを、従来と同じ引数で作ります。
   *
   * @param sequence 1始まりの実行順
   * @param word 命令を所有する利用者定義名
   * @param opcode トレース用命令名
   * @param sourceLine 1始まりのソース行
   * @param sourceColumn 1始まりの書記素クラスタ列
   * @param dataBefore 実行直前のデータスタック
   * @param dataAfter 実行直後のデータスタック
   * @param callDepthBefore 実行直前の呼出深さ
   * @param callDepthAfter 実行直後の呼出深さ
   * @param output この命令が出力したUTF-8バイト列
   * @param particles Callへ関連づけられた助詞情報
   * @param controlBefore 実行直前の回数ループ状態
   * @param controlAfter 実行直後の回数ループ状態
   * @param target 制御命令の移動先
   * @param branchTaken 制御命令が移動先を選んだか
   */
  public TraceEvent(
      long sequence,
      String word,
      String opcode,
      int sourceLine,
      int sourceColumn,
      List<RuntimeValue> dataBefore,
      List<RuntimeValue> dataAfter,
      int callDepthBefore,
      int callDepthAfter,
      byte[] output,
      List<ParticleSource> particles,
      List<? extends ControlTraceState> controlBefore,
      List<? extends ControlTraceState> controlAfter,
      Optional<String> target,
      Optional<Boolean> branchTaken,
      Optional<StorageTraceState> storage) {
    this(
        sequence,
        word,
        opcode,
        sourceLine,
        sourceColumn,
        dataBefore,
        dataAfter,
        callDepthBefore,
        callDepthAfter,
        output,
        particles,
        controlBefore,
        controlAfter,
        target,
        branchTaken,
        storage,
        "");
  }

  public TraceEvent(
      long sequence,
      String word,
      String opcode,
      int sourceLine,
      int sourceColumn,
      List<RuntimeValue> dataBefore,
      List<RuntimeValue> dataAfter,
      int callDepthBefore,
      int callDepthAfter,
      byte[] output,
      List<ParticleSource> particles,
      List<? extends ControlTraceState> controlBefore,
      List<? extends ControlTraceState> controlAfter,
      Optional<String> target,
      Optional<Boolean> branchTaken) {
    this(
        sequence,
        word,
        opcode,
        sourceLine,
        sourceColumn,
        dataBefore,
        dataAfter,
        callDepthBefore,
        callDepthAfter,
        output,
        particles,
        controlBefore,
        controlAfter,
        target,
        branchTaken,
        Optional.empty(),
        "");
  }

  /**
   * 言語コアの制御列を持たないイベントを、従来と同じ引数で作ります。
   *
   * @param sequence 1始まりの実行順
   * @param word 命令を所有する利用者定義名
   * @param opcode トレース用命令名
   * @param sourceLine 1始まりのソース行
   * @param sourceColumn 1始まりの書記素クラスタ列
   * @param dataBefore 実行直前のデータスタック
   * @param dataAfter 実行直後のデータスタック
   * @param callDepthBefore 実行直前の呼出深さ
   * @param callDepthAfter 実行直後の呼出深さ
   * @param output この命令が出力したUTF-8バイト列
   * @param particles Callへ関連づけられた助詞情報
   */
  public TraceEvent(
      long sequence,
      String word,
      String opcode,
      int sourceLine,
      int sourceColumn,
      List<RuntimeValue> dataBefore,
      List<RuntimeValue> dataAfter,
      int callDepthBefore,
      int callDepthAfter,
      byte[] output,
      List<ParticleSource> particles) {
    this(
        sequence,
        word,
        opcode,
        sourceLine,
        sourceColumn,
        dataBefore,
        dataAfter,
        callDepthBefore,
        callDepthAfter,
        output,
        particles,
        List.of(),
        List.of(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        "");
  }

  /**
   * 出力バイト列の防御的コピーを返します。
   *
   * @return この命令が出力したUTF-8バイト列
   */
  @Override
  public byte[] output() {
    return output.clone();
  }
}
