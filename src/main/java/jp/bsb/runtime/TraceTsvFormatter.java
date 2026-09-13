package jp.bsb.runtime;

import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.Collectors;

/** 適合データで定義された命令トレースをTSVへ変換します。 */
public final class TraceTsvFormatter {
  private static final String CORE_HEADER =
      "sequence\tword\topcode\tsourceLine\tsourceColumn\tdataBefore\tdataAfter\t"
          + "callDepthBefore\tcallDepthAfter\toutputHex\tparticles\n";
  private static final String FLOW_HEADER =
      "sequence\tword\topcode\tsourceLine\tsourceColumn\tdataBefore\tdataAfter\t"
          + "callDepthBefore\tcallDepthAfter\toutputHex\tparticles\tcontrolBefore\t"
          + "controlAfter\ttarget\tbranchTaken\n";
  private static final String BIND_HEADER =
      "sequence\tword\topcode\tsourceLine\tsourceColumn\tdataBefore\tdataAfter\t"
          + "callDepthBefore\tcallDepthAfter\toutputHex\tparticles\tcontrolBefore\t"
          + "controlAfter\ttarget\tbranchTaken\tbindingId\tbindingName\tbindingKind\t"
          + "storageScope\tvalueBefore\tvalueAfter\n";
  private static final String IO_HEADER =
      "sequence\tword\topcode\tsourceLine\tsourceColumn\tdataBefore\tdataAfter\t"
          + "callDepthBefore\tcallDepthAfter\toutputHex\tparticles\tcontrolBefore\t"
          + "controlAfter\ttarget\tbranchTaken\tbindingId\tbindingName\tbindingKind\t"
          + "storageScope\tvalueBefore\tvalueAfter\teffect\n";

  private TraceTsvFormatter() {}

  /**
   * イベント列をヘッダー付きTSVとして返します。
   *
   * @param events 実行順のイベント列
   * @return 末尾LFを持つTSV
   */
  public static String format(Iterable<TraceEvent> events) {
    return format(events, TraceProfile.CORE, TraceValuePolicy.bindings());
  }

  /**
   * 制御フローで追加した制御状態、飛び先、分岐結果を含むTSVを返します。
   *
   * @param events 実行順のイベント列
   * @return 制御フローヘッダーと末尾LFを持つTSV
   */
  public static String formatControlFlow(Iterable<TraceEvent> events) {
    return format(events, TraceProfile.FLOW, TraceValuePolicy.bindings());
  }

  /**
   * 束縛の保存領域6列を含むTSVを返します。
   *
   * @param events 実行順のイベント列
   * @return 束縛ヘッダーと末尾LFを持つTSV
   */
  public static String formatBinding(Iterable<TraceEvent> events) {
    return format(events, TraceProfile.BIND, TraceValuePolicy.bindings());
  }

  /**
   * 値開示ポリシーを指定して束縛TSVを返します。将来の資格情報型はここで内容を伏せられます。
   *
   * @param events 実行順のイベント列
   * @param valuePolicy 値の内容を開示してよいかを決めるポリシー
   * @return 束縛ヘッダーと末尾LFを持つTSV
   */
  public static String formatBinding(Iterable<TraceEvent> events, TraceValuePolicy valuePolicy) {
    return format(events, TraceProfile.BIND, Objects.requireNonNull(valuePolicy, "valuePolicy"));
  }

  /**
   * 束縛と同じ21列で、配列値と配列反復状態を含む配列TSVを返します。
   *
   * @param events 実行順のイベント列
   * @return 配列ヘッダーと末尾LFを持つTSV
   */
  public static String formatArray(Iterable<TraceEvent> events) {
    return format(events, TraceProfile.ARRAY, TraceValuePolicy.arrays());
  }

  /**
   * 配列TSVを指定した値開示ポリシーで返します。
   *
   * @param events 実行順のイベント列
   * @param valuePolicy 配列を含む値の内容を開示してよいかを決めるポリシー
   * @return 配列ヘッダーと末尾LFを持つTSV
   */
  public static String formatArray(Iterable<TraceEvent> events, TraceValuePolicy valuePolicy) {
    return format(events, TraceProfile.ARRAY, Objects.requireNonNull(valuePolicy, "valuePolicy"));
  }

  /**
   * 配列と同じ21列で、小数と丸め方法を開示する数値演算TSVを返します。
   *
   * @param events 実行順のイベント列
   * @return 数値演算ヘッダーと末尾LFを持つTSV
   */
  public static String formatNumeric(Iterable<TraceEvent> events) {
    return format(events, TraceProfile.NUM, TraceValuePolicy.numerics());
  }

  /**
   * 数値演算TSVを指定した値開示ポリシーで返します。
   *
   * @param events 実行順のイベント列
   * @param valuePolicy 小数・丸め方法・配列を含む値の開示ポリシー
   * @return 数値演算ヘッダーと末尾LFを持つTSV
   */
  public static String formatNumeric(Iterable<TraceEvent> events, TraceValuePolicy valuePolicy) {
    return format(events, TraceProfile.NUM, Objects.requireNonNull(valuePolicy, "valuePolicy"));
  }

  /**
   * 数値演算と同じ21列で、文字列・正規表現・文字列配列を開示する文字列・正規表現TSVを返します。
   *
   * @param events 実行順のイベント列
   * @return 文字列・正規表現ヘッダーと末尾LFを持つTSV
   */
  public static String formatTextRegex(Iterable<TraceEvent> events) {
    return format(events, TraceProfile.TEXT, TraceValuePolicy.textRegex());
  }

  /**
   * 文字列・正規表現TSVを指定した値開示ポリシーで返します。
   *
   * @param events 実行順のイベント列
   * @param valuePolicy 正規表現・文字列配列を含む値の開示ポリシー
   * @return 文字列・正規表現ヘッダーと末尾LFを持つTSV
   */
  public static String formatTextRegex(Iterable<TraceEvent> events, TraceValuePolicy valuePolicy) {
    return format(events, TraceProfile.TEXT, Objects.requireNonNull(valuePolicy, "valuePolicy"));
  }

  /** 文字列・正規表現までの21列を保ち、末尾へ能力effect列を加えたホスト入出力TSVを返します。 */
  public static String formatHostIo(Iterable<TraceEvent> events) {
    return format(events, TraceProfile.IO, TraceValuePolicy.hostIo());
  }

  /** 値開示ポリシーを指定したホスト入出力22列TSVを返します。 */
  public static String formatHostIo(Iterable<TraceEvent> events, TraceValuePolicy valuePolicy) {
    return format(events, TraceProfile.IO, Objects.requireNonNull(valuePolicy, "valuePolicy"));
  }

  /** ホスト入出力の22列を保ち、JSONとJSON入力になり得る文字列を既定非開示にするJSONTSVです。 */
  public static String formatJson(Iterable<TraceEvent> events) {
    return format(events, TraceProfile.IO, TraceValuePolicy.json());
  }

  /** 値開示ポリシーを指定したJSON22列TSVを返します。 */
  public static String formatJson(Iterable<TraceEvent> events, TraceValuePolicy valuePolicy) {
    return format(events, TraceProfile.IO, Objects.requireNonNull(valuePolicy, "valuePolicy"));
  }

  /** JSONの22列を保ち、任意値へ推移的な非開示規則を適用する任意値TSVです。 */
  public static String formatOptional(Iterable<TraceEvent> events) {
    return format(events, TraceProfile.IO, TraceValuePolicy.optional());
  }

  /** 値開示ポリシーを指定した任意値22列TSVを返します。 */
  public static String formatOptional(Iterable<TraceEvent> events, TraceValuePolicy valuePolicy) {
    return format(events, TraceProfile.IO, Objects.requireNonNull(valuePolicy, "valuePolicy"));
  }

  /** ホスト入出力の22列を保ち、結果値へ推移的な非開示規則を適用する結果値TSVです。 */
  public static String formatResult(Iterable<TraceEvent> events) {
    return format(events, TraceProfile.IO, TraceValuePolicy.result());
  }

  /** 値開示ポリシーを指定した結果値22列TSVを返します。 */
  public static String formatResult(Iterable<TraceEvent> events, TraceValuePolicy valuePolicy) {
    return format(events, TraceProfile.IO, Objects.requireNonNull(valuePolicy, "valuePolicy"));
  }

  /** 結果値の22列を保ち、JSON解析失敗を推移的に非開示にする回復可能JSONTSVです。 */
  public static String formatRecoverableJson(Iterable<TraceEvent> events) {
    return format(events, TraceProfile.IO, TraceValuePolicy.result());
  }

  /** 回復可能JSONの22列を保ち、バイト列と復号失敗を推移的に非開示にするバイト列TSVです。 */
  public static String formatByteSequence(Iterable<TraceEvent> events) {
    return format(events, TraceProfile.IO, TraceValuePolicy.byteSequence());
  }

  /** バイト列の22列と非開示規則を保ち、二次元配列を階層ごとに省略する多次元配列TSVです。 */
  public static String formatNestedArray(Iterable<TraceEvent> events) {
    return format(events, TraceProfile.IO, TraceValuePolicy.byteSequence());
  }

  private static String format(
      Iterable<TraceEvent> events, TraceProfile profile, TraceValuePolicy valuePolicy) {
    Objects.requireNonNull(events, "events");
    String header =
        switch (profile) {
          case CORE -> CORE_HEADER;
          case FLOW -> FLOW_HEADER;
          case BIND, ARRAY, NUM, TEXT -> BIND_HEADER;
          case IO -> IO_HEADER;
        };
    var result = new StringBuilder(header);
    for (TraceEvent event : events) {
      result
          .append(event.sequence())
          .append('\t')
          .append(event.word())
          .append('\t')
          .append(event.opcode())
          .append('\t')
          .append(event.sourceLine())
          .append('\t')
          .append(event.sourceColumn())
          .append('\t')
          .append(formatStack(event.dataBefore(), valuePolicy))
          .append('\t')
          .append(formatStack(event.dataAfter(), valuePolicy))
          .append('\t')
          .append(event.callDepthBefore())
          .append('\t')
          .append(event.callDepthAfter())
          .append('\t')
          .append(HexFormat.of().withUpperCase().formatHex(event.output()))
          .append('\t')
          .append(
              event.particles().stream()
                  .map(
                      particle ->
                          particle.name()
                              + "@"
                              + particle.span().start().line()
                              + ":"
                              + particle.span().start().column())
                  .collect(Collectors.joining(",")));
      if (profile != TraceProfile.CORE) {
        result
            .append('\t')
            .append(formatControl(event.controlBefore()))
            .append('\t')
            .append(formatControl(event.controlAfter()))
            .append('\t')
            .append(event.target().orElse(""))
            .append('\t')
            .append(event.branchTaken().map(String::valueOf).orElse(""));
      }
      if (profile == TraceProfile.BIND
          || profile == TraceProfile.ARRAY
          || profile == TraceProfile.NUM
          || profile == TraceProfile.TEXT
          || profile == TraceProfile.IO) {
        appendStorage(result, event.storage(), valuePolicy);
      }
      if (profile == TraceProfile.IO) {
        result.append('\t').append(event.effect());
      }
      result.append('\n');
    }
    return result.toString();
  }

  private static String formatControl(java.util.List<? extends ControlTraceState> states) {
    return states.stream()
        .map(ControlTraceState::traceText)
        .collect(Collectors.joining(",", "[", "]"));
  }

  private static String formatStack(
      java.util.List<RuntimeValue> values, TraceValuePolicy valuePolicy) {
    return values.stream()
        .map(value -> TraceValueFormatter.format(value, valuePolicy))
        .collect(Collectors.joining(",", "[", "]"));
  }

  private static void appendStorage(
      StringBuilder result,
      java.util.Optional<StorageTraceState> storage,
      TraceValuePolicy valuePolicy) {
    if (storage.isEmpty()) {
      result.append("\t-\t-\t-\t-\t-\t-");
      return;
    }
    StorageTraceState state = storage.orElseThrow();
    var slot = state.slot();
    String scope =
        slot.storage() == jp.bsb.binding.BindingStorage.GLOBAL
            ? slot.storage().reportName()
            : slot.storage().reportName() + ":" + slot.ownerWord().orElseThrow();
    result
        .append('\t')
        .append(slot.bindingId().displayName())
        .append('\t')
        .append(slot.bindingName())
        .append('\t')
        .append(slot.bindingKind().reportName())
        .append('\t')
        .append(scope)
        .append('\t')
        .append(formatStorageValue(state.valueBefore(), valuePolicy))
        .append('\t')
        .append(formatStorageValue(state.valueAfter(), valuePolicy));
  }

  private static String formatStorageValue(
      java.util.Optional<RuntimeValue> value, TraceValuePolicy valuePolicy) {
    return value
        .map(runtimeValue -> TraceValueFormatter.format(runtimeValue, valuePolicy))
        .orElse("<uninitialized>");
  }

  private enum TraceProfile {
    CORE,
    FLOW,
    BIND,
    ARRAY,
    NUM,
    TEXT,
    IO
  }
}
