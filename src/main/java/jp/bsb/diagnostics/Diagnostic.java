package jp.bsb.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 人間向けのエラー表示と、機械的な適合性検証（テスト・IDE連携）の両方で共有される「構造化診断オブジェクト」です。
 *
 * <p>【コンピュータ科学の観点：構造化診断（Structured Diagnostics）】 単なるエラーメッセージ文字列ではなく、エラーコード、重要度、発生フェーズ、正確なソース範囲、
 * 期待値（expected）と実値（actual）、関連位置（relatedLocations）、具体的な修正候補（fixes）、
 * 資源制限の測定値（limit/observed）などを独立したフィールドとして保持する不変（Immutable）オブジェクトです。
 * これにより、ユーザーへの親切な表示、修正候補の提示、厳密な自動テストが容易になります。現在の {@code fixes} は説明文字列であり、
 * 適用範囲と置換文字列を持つ自動修正データではありません。
 */
public final class Diagnostic {
  /**
   * 複数の診断を仕様で定められた規定順にソートするための比較器（Comparator）です。
   *
   * <ol>
   *   <li>処理段階（{@link #stage}）の早い順（例: 字句エラー → 構文エラー）
   *   <li>ソースファイル先頭からのバイト位置（{@code location().utf8Offset()}）の昇順
   *   <li>同一位置の場合は診断コード名（{@code code().name()}）の辞書順
   * </ol>
   */
  public static final Comparator<Diagnostic> ORDERING =
      Comparator.comparing(Diagnostic::stage)
          .thenComparingLong(diagnostic -> diagnostic.location().utf8Offset())
          .thenComparingLong(Diagnostic::samePositionRecoveryKey)
          .thenComparing(diagnostic -> diagnostic.code().name());

  /** 同一EOF位置の型終端不足は、開始位置が後ろにある内側の型から並べます。 */
  private static long samePositionRecoveryKey(Diagnostic diagnostic) {
    boolean typeEnd =
        diagnostic.code() == DiagnosticCode.E_EXPECTED_RESULT_TYPE_END
            || diagnostic.code() == DiagnosticCode.E_EXPECTED_OPTIONAL_TYPE_END
            || diagnostic.code() == DiagnosticCode.E_EXPECTED_ARRAY_TYPE_END;
    if (!typeEnd || diagnostic.relatedLocations().isEmpty()) {
      return 0;
    }
    RelatedLocation related = diagnostic.relatedLocations().getFirst();
    return related.position() == null ? 0 : -related.position().utf8Offset();
  }

  /** 診断を一意に特定するコード */
  private final DiagnosticCode code;

  /** 重要度（エラーまたは警告） */
  private final Severity severity;

  /** 発生した処理フェーズ */
  private final DiagnosticStage stage;

  /** ソースファイルのパス */
  private final String sourcePath;

  /** ソースコード上の発生位置 */
  private final DiagnosticLocation location;

  /** メッセージ埋め込み用のカスタム属性マップ */
  private final Map<String, String> fields;

  /** 期待されていた構文や値（例: "UTF-8", "1クラスタ" など） */
  private final String expected;

  /** 実際に現れた不正な値（例: "EF BB", "3クラスタ" など） */
  private final String actual;

  /** 原因に関連する別のソース位置のリスト */
  private final List<RelatedLocation> relatedLocations;

  /** ユーザーに対する具体的な修正提案のリスト */
  private final List<String> fixes;

  /** 資源制限の名前（例: "sourceBytes", "tokens"） */
  private final String limitName;

  /** 資源の許容上限値 */
  private final String limit;

  /** 実際に観測された資源量 */
  private final String observed;

  private Diagnostic(Builder builder) {
    code = Objects.requireNonNull(builder.code, "code");
    severity = Objects.requireNonNull(builder.severity, "severity");
    stage = Objects.requireNonNull(builder.stage, "stage");
    sourcePath = requireText(builder.sourcePath, "sourcePath");
    location = Objects.requireNonNull(builder.location, "location");
    fields = Collections.unmodifiableMap(new LinkedHashMap<>(builder.fields));
    expected = builder.expected;
    actual = builder.actual;
    relatedLocations = List.copyOf(builder.relatedLocations);
    fixes = List.copyOf(builder.fixes);
    limitName = builder.limitName;
    limit = builder.limit;
    observed = builder.observed;
  }

  /**
   * 診断オブジェクトを構築するための {@link Builder} を生成します。
   *
   * @param code 診断コード
   * @param severity 重要度
   * @param stage 発生段階
   * @param sourcePath ファイルパス
   * @param location ソース位置
   * @return 新しいビルダーインスタンス
   */
  public static Builder builder(
      DiagnosticCode code,
      Severity severity,
      DiagnosticStage stage,
      String sourcePath,
      DiagnosticLocation location) {
    return new Builder(code, severity, stage, sourcePath, location);
  }

  /**
   * 診断を一意に識別するコードを返します。
   *
   * @return 診断コード
   */
  public DiagnosticCode code() {
    return code;
  }

  /**
   * エラーまたは警告の重要度を返します。
   *
   * @return 診断の重要度
   */
  public Severity severity() {
    return severity;
  }

  /**
   * 診断が発生した処理段階を返します。
   *
   * @return 処理段階
   */
  public DiagnosticStage stage() {
    return stage;
  }

  /**
   * 診断対象のソースパスを返します。
   *
   * @return ソースパス
   */
  public String sourcePath() {
    return sourcePath;
  }

  /**
   * 主な診断位置を返します。
   *
   * @return 診断位置
   */
  public DiagnosticLocation location() {
    return location;
  }

  /**
   * 機械処理用の追加フィールドを返します。
   *
   * @return 不変の追加フィールド
   */
  public Map<String, String> fields() {
    return fields;
  }

  /**
   * 期待されていた構文または値を返します。
   *
   * @return 期待値。指定がなければ空
   */
  public Optional<String> expected() {
    return Optional.ofNullable(expected);
  }

  /**
   * 実際に観測した構文または値を返します。
   *
   * @return 実値。指定がなければ空
   */
  public Optional<String> actual() {
    return Optional.ofNullable(actual);
  }

  /**
   * 原因に関連する別位置を返します。
   *
   * @return 関連位置の不変一覧
   */
  public List<RelatedLocation> relatedLocations() {
    return relatedLocations;
  }

  /**
   * 人間向け修正候補を返します。
   *
   * @return 修正候補の不変一覧
   */
  public List<String> fixes() {
    return fixes;
  }

  /**
   * 資源上限の種類名を返します。
   *
   * @return 種類名。上限診断でなければ空
   */
  public Optional<String> limitName() {
    return Optional.ofNullable(limitName);
  }

  /**
   * 許容された資源上限を返します。
   *
   * @return 上限値。上限診断でなければ空
   */
  public Optional<String> limit() {
    return Optional.ofNullable(limit);
  }

  /**
   * 実際に観測した資源量を返します。
   *
   * @return 観測量。上限診断でなければ空
   */
  public Optional<String> observed() {
    return Optional.ofNullable(observed);
  }

  /**
   * メッセージテンプレート（{@code messages.properties}）のプレースホルダーに埋め込むための キーと値のマップを返します。専用の上限情報（limitName,
   * limit, observed）も統合されます。
   *
   * @return 埋め込み用パラメータの不変マップ
   */
  public Map<String, String> templateValues() {
    var values = new LinkedHashMap<>(fields);
    putIfPresent(values, "limitName", limitName);
    putIfPresent(values, "limit", limit);
    putIfPresent(values, "observed", observed);
    return Collections.unmodifiableMap(values);
  }

  private static void putIfPresent(Map<String, String> values, String name, String value) {
    if (value != null) {
      String previous = values.putIfAbsent(name, value);
      if (previous != null && !previous.equals(value)) {
        throw new IllegalStateException("conflicting diagnostic field: " + name);
      }
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }

  /** {@link Diagnostic} オブジェクトを安全かつ段階的に組み立てるためのビルダー（Builderパターン）クラスです。 */
  public static final class Builder {
    private final DiagnosticCode code;
    private final Severity severity;
    private final DiagnosticStage stage;
    private final String sourcePath;
    private final DiagnosticLocation location;
    private final Map<String, String> fields = new LinkedHashMap<>();
    private final List<RelatedLocation> relatedLocations = new ArrayList<>();
    private final List<String> fixes = new ArrayList<>();
    private String expected;
    private String actual;
    private String limitName;
    private String limit;
    private String observed;

    private Builder(
        DiagnosticCode code,
        Severity severity,
        DiagnosticStage stage,
        String sourcePath,
        DiagnosticLocation location) {
      this.code = code;
      this.severity = severity;
      this.stage = stage;
      this.sourcePath = sourcePath;
      this.location = location;
    }

    /**
     * メッセージテンプレートの埋め込みフィールドを追加します。
     *
     * @param name フィールド名
     * @param value フィールド値
     * @return このビルダー
     */
    public Builder field(String name, String value) {
      requireText(name, "field name");
      Objects.requireNonNull(value, "field value");
      if (fields.putIfAbsent(name, value) != null) {
        throw new IllegalArgumentException("duplicate diagnostic field: " + name);
      }
      return this;
    }

    /**
     * 期待されていた構文や仕様を設定します。
     *
     * @param value 期待値の表示
     * @return このビルダー
     */
    public Builder expected(String value) {
      expected = requireText(value, "expected");
      return this;
    }

    /**
     * 実際に観測された不正な記述を設定します。
     *
     * @param value 実値の表示
     * @return このビルダー
     */
    public Builder actual(String value) {
      actual = requireText(value, "actual");
      return this;
    }

    /**
     * 関連する別のソース位置情報を追加します。
     *
     * @param value 関連位置
     * @return このビルダー
     */
    public Builder relatedLocation(RelatedLocation value) {
      relatedLocations.add(Objects.requireNonNull(value, "relatedLocation"));
      return this;
    }

    /**
     * ユーザー向けの修正候補を追加します。
     *
     * @param value 修正案の説明
     * @return このビルダー
     */
    public Builder fix(String value) {
      fixes.add(requireText(value, "fix"));
      return this;
    }

    /**
     * 資源制限（数値）の情報を設定します。
     *
     * @param name 資源の種類名
     * @param maximum 許容上限
     * @param measured 観測量
     * @return このビルダー
     */
    public Builder limit(String name, long maximum, long measured) {
      if (maximum < 0 || measured < 0) {
        throw new IllegalArgumentException("limit values must not be negative");
      }
      return limit(name, Long.toString(maximum), Long.toString(measured));
    }

    /**
     * 資源制限（文字列）の情報を設定します。
     *
     * @param name 資源の種類名
     * @param maximum 許容上限の文字列表現
     * @param measured 観測量の文字列表現
     * @return このビルダー
     */
    public Builder limit(String name, String maximum, String measured) {
      limitName = requireText(name, "limitName");
      limit = requireText(maximum, "limit");
      observed = requireText(measured, "observed");
      return this;
    }

    /**
     * 不変の{@link Diagnostic}インスタンスを生成します。
     *
     * @return 設定済み情報を保持する診断
     */
    public Diagnostic build() {
      return new Diagnostic(this);
    }
  }
}
