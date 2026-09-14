package jp.bsb.frontend;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jp.bsb.adapter.IcuUnicodeAdapter;
import jp.bsb.adapter.IcuUnicodeAdapter.GraphemeCursor;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;

/**
 * UTF-8バイト列からデコードされたソースコード文字列を保持し、UTF-16コード単位のインデックスを元ファイルの正確な位置情報（UTF-8バイト、行番号、書記素クラスタ列）へ
 * 高速に対応付けるためのデータ構造です。
 *
 * <p>【コンピュータ科学の観点：文字エンコーディングと位置計算の計算量】
 *
 * <ul>
 *   <li><b>UTF-8 vs UTF-16</b>: Javaの {@link String} のインデックスと {@code char}
 *       は、API上UTF-16コード単位として扱われます。 1コード単位は16ビットですが、補助平面の文字はサロゲートペアという2コード単位で表します。一方、元のソースファイルは
 *       UTF-8 であり、ASCIIは1バイト、日本語は3バイト、絵文字は4バイトとなります。 そのため、Javaの文字列インデックスと元ファイルのバイト位置は単純には一致しません。
 *   <li><b>書記素クラスタ（Grapheme Cluster）</b>: 人間が視覚的に認識する「1文字」は、結合文字（濁点分離など）によって
 *       複数のUnicodeコードポイントから構成されることがあります。列番号（column）の計算にはUnicode境界アダプターを使用します。
 *   <li><b>チェックポイントと二分探索（O(log N)）</b>: およそ4,096 UTF-16コード単位ごとに位置情報のスナップショット（{@link
 *       Checkpoint}）を事前計算して保持することで、 ランダムアクセス時の位置計算コストを最小限に抑えます。
 *   <li><b>位置カーソル（{@link PositionCursor}）による高速化（償却 O(1)）</b>: 字句解析のようにソースを先頭から末尾へ順番に走査する場合、
 *       毎回先頭から計算し直すとファイル全体で O(N^2) の時間がかかってしまいます。 カーソルが直前の位置を記憶して前進差分のみを更新することで、全体を O(N)（1トークンあたり償却
 *       O(1)）で処理できます。
 * </ul>
 */
public final class SourceText {
  /** チェックポイントを作成する間隔（UTF-16コード単位） */
  private static final int CHECKPOINT_INTERVAL = 4096;

  /** ソースファイルのパス */
  private final String sourcePath;

  /** デコード済みのソースコード文字列 */
  private final String text;

  /** 事前計算されたチェックポイントのリスト（二分探索用） */
  private final List<Checkpoint> checkpoints;

  /**
   * ソーステキストインスタンスを構築し、位置計算用のチェックポイントテーブルを構築します。
   *
   * @param sourcePath ソースファイルのパス
   * @param text デコードされたソースコード文字列
   * @param initialUtf8Offset BOM（Byte Order Mark）等の除去による先頭オフセット（通常BOMありなら3、なしなら0）
   */
  public SourceText(String sourcePath, String text, long initialUtf8Offset) {
    if (sourcePath == null || sourcePath.isBlank()) {
      throw new IllegalArgumentException("sourcePath must not be blank");
    }
    this.sourcePath = sourcePath;
    this.text = Objects.requireNonNull(text, "text");
    if (initialUtf8Offset < 0) {
      throw new IllegalArgumentException("initialUtf8Offset must not be negative");
    }
    validateUnicodeScalars(text);
    checkpoints = buildCheckpoints(text, initialUtf8Offset);
  }

  /**
   * 診断表示に使うソース識別パスを返します。
   *
   * @return ソース識別パス
   */
  public String sourcePath() {
    return sourcePath;
  }

  /**
   * 厳密なUTF-8から復号したソース文字列を返します。
   *
   * @return 復号済みソース
   */
  public String text() {
    return text;
  }

  /**
   * 指定された UTF-16 インデックスに対応する正確な位置情報（バイト位置、行、書記素列）を返します。
   *
   * @param utf16Index ソース文字列内のインデックス（0以上 text.length() 以下）
   * @return 計算された {@link SourcePosition}
   * @throws IllegalArgumentException インデックスがサロゲートペアの途中（Low Surrogate）を指している場合
   */
  public SourcePosition positionAt(int utf16Index) {
    validateIndex(utf16Index);
    if (utf16Index > 0
        && utf16Index < text.length()
        && Character.isLowSurrogate(text.charAt(utf16Index))) {
      throw new IllegalArgumentException("utf16Index must be on a code point boundary");
    }

    Checkpoint checkpoint = checkpointAtOrBefore(utf16Index);
    if (checkpoint.utf16Index() == utf16Index) {
      return checkpoint.position();
    }
    return scanPosition(checkpoint, utf16Index);
  }

  /**
   * 開始インデックスと終了インデックスから、位置範囲（{@link SourceSpan}）を生成します。
   *
   * @param utf16Start 開始インデックス
   * @param utf16End 終了インデックス
   * @return 位置範囲オブジェクト
   */
  public SourceSpan span(int utf16Start, int utf16End) {
    if (utf16End < utf16Start) {
      throw new IllegalArgumentException("utf16End must not precede utf16Start");
    }
    return new SourceSpan(positionAt(utf16Start), positionAt(utf16End));
  }

  /**
   * 終端排他的UTF-8位置の直前を含む書記素クラスタの開始位置を返します。
   *
   * <p>JSON診断の包含終端を、終端位置からの列減算やソース再読込みなしで求めるために使用します。
   *
   * @param utf8EndExclusive BOMを含む元ファイル基準の終端排他的UTF-8位置
   * @return 直前バイトを含む拡張書記素クラスタの開始位置
   */
  public SourcePosition clusterPositionBefore(long utf8EndExclusive) {
    long contentStart = checkpoints.getFirst().position().utf8Offset();
    long contentEnd = checkpoints.getLast().position().utf8Offset();
    if (utf8EndExclusive <= contentStart || utf8EndExclusive > contentEnd) {
      throw new IllegalArgumentException("utf8EndExclusive must identify source content");
    }

    Checkpoint checkpoint = checkpointBeforeUtf8(utf8EndExclusive);
    GraphemeCursor iterator = IcuUnicodeAdapter.graphemeCursor(text);
    int clusterStart = checkpoint.utf16Index();
    int clusterEnd = iterator.following(clusterStart);
    long utf8Offset = checkpoint.position().utf8Offset();
    int line = checkpoint.position().line();
    int column = checkpoint.position().column();

    while (clusterEnd != IcuUnicodeAdapter.DONE) {
      long nextUtf8Offset = utf8Offset + utf8Length(text, clusterStart, clusterEnd);
      if (utf8EndExclusive <= nextUtf8Offset) {
        return new SourcePosition(utf8Offset, line, column);
      }
      if (isNewlineCluster(text, clusterStart, clusterEnd)) {
        line++;
        column = 1;
      } else {
        column = columnAfterCluster(text, clusterStart, clusterEnd, column);
      }
      utf8Offset = nextUtf8Offset;
      clusterStart = clusterEnd;
      clusterEnd = iterator.next();
    }
    throw new IllegalStateException("grapheme iteration ended before the requested UTF-8 offset");
  }

  /**
   * 先頭から末尾へ向かって順番にトークンを走査する処理系（字句解析器）向けに、 償却定数時間 O(1) で動作する位置カーソル（{@link PositionCursor}）を生成します。
   *
   * @return 新しい位置カーソル
   */
  public PositionCursor positionCursor() {
    return new PositionCursor();
  }

  private void validateIndex(int utf16Index) {
    if (utf16Index < 0 || utf16Index > text.length()) {
      throw new IndexOutOfBoundsException(utf16Index);
    }
  }

  /** 指定されたインデックス直前（または一致する）チェックポイントを二分探索（O(log N)）で検索します。 */
  private Checkpoint checkpointAtOrBefore(int utf16Index) {
    int low = 0;
    int high = checkpoints.size() - 1;
    while (low <= high) {
      int middle = (low + high) >>> 1;
      Checkpoint candidate = checkpoints.get(middle);
      if (candidate.utf16Index() <= utf16Index) {
        low = middle + 1;
      } else {
        high = middle - 1;
      }
    }
    return checkpoints.get(high);
  }

  private Checkpoint checkpointBeforeUtf8(long utf8Offset) {
    int low = 0;
    int high = checkpoints.size() - 1;
    while (low <= high) {
      int middle = (low + high) >>> 1;
      Checkpoint candidate = checkpoints.get(middle);
      if (candidate.position().utf8Offset() < utf8Offset) {
        low = middle + 1;
      } else {
        high = middle - 1;
      }
    }
    return checkpoints.get(high);
  }

  /** チェックポイントの位置から目的のインデックスまでを走査し、正確な位置を計算します。 */
  private SourcePosition scanPosition(Checkpoint checkpoint, int targetIndex) {
    GraphemeCursor iterator = IcuUnicodeAdapter.graphemeCursor(text);
    int clusterStart = checkpoint.utf16Index();
    int clusterEnd = iterator.following(clusterStart);
    long utf8Offset = checkpoint.position().utf8Offset();
    int line = checkpoint.position().line();
    int column = checkpoint.position().column();

    while (clusterEnd != IcuUnicodeAdapter.DONE) {
      if (targetIndex < clusterEnd) {
        return new SourcePosition(
            utf8Offset + utf8Length(text, clusterStart, targetIndex), line, column);
      }
      utf8Offset += utf8Length(text, clusterStart, clusterEnd);
      if (isNewlineCluster(text, clusterStart, clusterEnd)) {
        line++;
        column = 1;
      } else {
        column = columnAfterCluster(text, clusterStart, clusterEnd, column);
      }
      if (targetIndex == clusterEnd) {
        return new SourcePosition(utf8Offset, line, column);
      }
      clusterStart = clusterEnd;
      clusterEnd = iterator.next();
    }
    throw new IllegalStateException("grapheme iteration ended before the requested position");
  }

  /** 文字列内に孤立したサロゲート（ペアになっていない High / Low Surrogate）が含まれていないか検査します。 */
  private static void validateUnicodeScalars(String value) {
    for (int index = 0; index < value.length(); ) {
      char current = value.charAt(index);
      if (Character.isHighSurrogate(current)) {
        if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
          throw new IllegalArgumentException("text contains an unpaired high surrogate");
        }
        index += 2;
      } else if (Character.isLowSurrogate(current)) {
        throw new IllegalArgumentException("text contains an unpaired low surrogate");
      } else {
        index++;
      }
    }
  }

  /** テキスト全体を書記素クラスタ単位で走査し、およそ4,096 UTF-16コード単位ごとにチェックポイントを構築します。 */
  private static List<Checkpoint> buildCheckpoints(String text, long initialUtf8Offset) {
    var result = new ArrayList<Checkpoint>();
    long utf8Offset = initialUtf8Offset;
    int line = 1;
    int column = 1;
    int lastCheckpoint = 0;
    result.add(new Checkpoint(0, new SourcePosition(utf8Offset, line, column)));

    GraphemeCursor iterator = IcuUnicodeAdapter.graphemeCursor(text);
    int clusterStart = iterator.first();
    for (int clusterEnd = iterator.next();
        clusterEnd != IcuUnicodeAdapter.DONE;
        clusterEnd = iterator.next()) {
      utf8Offset += utf8Length(text, clusterStart, clusterEnd);
      if (isNewlineCluster(text, clusterStart, clusterEnd)) {
        line++;
        column = 1;
      } else {
        column = columnAfterCluster(text, clusterStart, clusterEnd, column);
      }
      if (clusterEnd - lastCheckpoint >= CHECKPOINT_INTERVAL) {
        result.add(new Checkpoint(clusterEnd, new SourcePosition(utf8Offset, line, column)));
        lastCheckpoint = clusterEnd;
      }
      clusterStart = clusterEnd;
    }

    if (lastCheckpoint != text.length()) {
      result.add(new Checkpoint(text.length(), new SourcePosition(utf8Offset, line, column)));
    }
    return List.copyOf(result);
  }

  /** 改行（CR, LF, または CRLF の2文字クラスタ）であるかを判定します。 */
  private static boolean isNewlineCluster(String value, int start, int end) {
    int length = end - start;
    return (length == 1 && (value.charAt(start) == '\r' || value.charAt(start) == '\n'))
        || (length == 2 && value.charAt(start) == '\r' && value.charAt(start + 1) == '\n');
  }

  private static int columnAfterCluster(String value, int start, int end, int column) {
    if (end - start == 1 && value.charAt(start) == '\t') {
      int columnsToNextStop = 4 - ((column - 1) % 4);
      return column + columnsToNextStop;
    }
    return column + 1;
  }

  /** 指定範囲の文字列が UTF-8 でエンコードされた場合のバイト数を計算します。 */
  private static long utf8Length(String value, int start, int end) {
    long length = 0;
    for (int index = start; index < end; ) {
      int codePoint = value.codePointAt(index);
      length += utf8Length(codePoint);
      index += Character.charCount(codePoint);
    }
    return length;
  }

  /**
   * 1つの Unicode コードポイントが UTF-8 で何バイトになるかを計算します。 (ASCII: 1バイト, U+0080〜U+07FF: 2バイト, U+0800〜U+FFFF:
   * 3バイト, U+10000〜: 4バイト)
   */
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

  /** 先頭から順方向にのみ移動し、位置計算を高速に行うステートフルなカーソルクラスです。 */
  public final class PositionCursor {
    private final GraphemeCursor iterator;
    private int clusterStart;
    private int clusterEnd;
    private int lastRequestedIndex;
    private long utf8Offset;
    private int line = 1;
    private int column = 1;

    private PositionCursor() {
      iterator = IcuUnicodeAdapter.graphemeCursor(text);
      clusterStart = iterator.first();
      clusterEnd = iterator.next();
      utf8Offset = checkpoints.getFirst().position().utf8Offset();
    }

    /**
     * 前回の位置より前方のインデックスの位置情報を、差分更新により O(1) 償却時間で取得します。
     *
     * @param utf16Index 目的のインデックス（前回の要求位置以上）
     * @return 計算された位置情報
     */
    public SourcePosition positionAt(int utf16Index) {
      validateIndex(utf16Index);
      if (utf16Index < lastRequestedIndex) {
        throw new IllegalArgumentException("position cursor cannot move backwards");
      }
      if (utf16Index > 0
          && utf16Index < text.length()
          && Character.isLowSurrogate(text.charAt(utf16Index))) {
        throw new IllegalArgumentException("utf16Index must be on a code point boundary");
      }

      while (clusterEnd != IcuUnicodeAdapter.DONE && utf16Index >= clusterEnd) {
        utf8Offset += utf8Length(text, clusterStart, clusterEnd);
        if (isNewlineCluster(text, clusterStart, clusterEnd)) {
          line++;
          column = 1;
        } else {
          column = columnAfterCluster(text, clusterStart, clusterEnd, column);
        }
        clusterStart = clusterEnd;
        clusterEnd = iterator.next();
      }
      lastRequestedIndex = utf16Index;
      return new SourcePosition(
          utf8Offset + utf8Length(text, clusterStart, utf16Index), line, column);
    }
  }

  /** チェックポイント（事前計算された特定インデックスとその位置情報のペア） */
  private record Checkpoint(int utf16Index, SourcePosition position) {}
}
