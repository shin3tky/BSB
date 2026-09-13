package jp.bsb.runtime;

/** 実行命令ごとの状態変化を受け取る内部トレース出力先です。 */
@FunctionalInterface
public interface TraceSink {
  /**
   * 1命令の実行が完了したイベントを受け取ります。
   *
   * @param event 不変スナップショット
   */
  void accept(TraceEvent event);

  /**
   * トレースイベントを必要とするか返します。
   *
   * <p>通常実行ではイベント構築前にfalseを確認し、巨大ループで不要なスナップショットを作りません。
   *
   * @return 通常の受信先はtrue、破棄先はfalse
   */
  default boolean enabled() {
    return true;
  }

  /**
   * イベントを破棄する通常実行用の出力先を返します。
   *
   * @return 何もしない出力先
   */
  static TraceSink none() {
    return DisabledTraceSink.INSTANCE;
  }

  /** 何も保存しない単一インスタンスです。 */
  enum DisabledTraceSink implements TraceSink {
    /** 破棄先として共有する唯一の値です。 */
    INSTANCE;

    @Override
    public void accept(TraceEvent event) {}

    @Override
    public boolean enabled() {
      return false;
    }
  }
}
