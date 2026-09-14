package jp.bsb.diagnostics;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 診断コード（{@link DiagnosticCode}）に対応する人間向けの日本語メッセージテンプレートを管理し、 構造化診断オブジェクト（{@link
 * Diagnostic}）から最終的な表示文字列を生成するカタログクラスです。
 *
 * <p>【コンピュータ科学の観点：メッセージの国際化とテンプレートエンジン】 エラー文面をプログラムのコード内に直接ハードコード（ベタ書き）すると、保守性や翻訳（多言語対応）が困難になります。
 * 本クラスは、外部リソースファイル（{@code messages.properties}）から <code>
 * E_INVALID_IDENTIFIER={candidate} は識別子として使用できません</code> のようなテンプレートを読み込み、正規表現によるプレースホルダー（{@code
 * {key}}）の置換を行ってメッセージを生成します。
 */
public final class DiagnosticMessageCatalog {
  /** クラスパス上のデフォルトメッセージファイルへのパス */
  private static final String DEFAULT_RESOURCE = "/jp/bsb/diagnostics/messages.properties";

  /** 制御フローで追加したメッセージファイルへのパス */
  private static final String FLOW_RESOURCE =
      "/jp/bsb/diagnostics/control-flow-messages.properties";

  /** 束縛で追加したメッセージファイルへのパス */
  private static final String BIND_RESOURCE = "/jp/bsb/diagnostics/bindings-messages.properties";

  /** 配列で追加したメッセージファイルへのパス */
  private static final String ARRAY_RESOURCE = "/jp/bsb/diagnostics/arrays-messages.properties";

  /** 数値演算で追加したメッセージファイルへのパス */
  private static final String NUM_RESOURCE = "/jp/bsb/diagnostics/numerics-messages.properties";

  /** 文字列・正規表現で追加したメッセージファイルへのパス */
  private static final String TEXT_RESOURCE = "/jp/bsb/diagnostics/text-regex-messages.properties";

  /** ホスト入出力で追加したメッセージファイルへのパス */
  private static final String IO_RESOURCE = "/jp/bsb/diagnostics/host-io-messages.properties";

  /** JSONで追加したメッセージファイルへのパス */
  private static final String JSON_RESOURCE = "/jp/bsb/diagnostics/json-messages.properties";

  /** 任意値で追加したメッセージファイルへのパス */
  private static final String OPT_RESOURCE =
      "/jp/bsb/diagnostics/optional-values-messages.properties";

  /** 結果値で追加したメッセージファイルへのパス */
  private static final String RESULT_RESOURCE =
      "/jp/bsb/diagnostics/result-values-messages.properties";

  /** 論理接続で追加したメッセージファイルへのパス */
  private static final String CONN_RESOURCE =
      "/jp/bsb/diagnostics/logical-connections-messages.properties";

  /** バイト列で追加したメッセージファイルへのパス */
  private static final String BYTES_RESOURCE =
      "/jp/bsb/diagnostics/byte-sequences-messages.properties";

  /** HTTPSで追加したメッセージファイルへのパス */
  private static final String HTTPS_RESOURCE = "/jp/bsb/diagnostics/https-messages.properties";

  /** 多次元配列で追加したメッセージファイルへのパス */
  private static final String NARRAY_RESOURCE =
      "/jp/bsb/diagnostics/nested-arrays-messages.properties";

  /** 作業領域・区切り表で追加したメッセージファイルへのパス */
  private static final String WST_RESOURCE =
      "/jp/bsb/diagnostics/workspace-tables-messages.properties";

  /** JSON形状検証で追加したメッセージファイルへのパス */
  private static final String JSON_SHAPES_RESOURCE =
      "/jp/bsb/diagnostics/json-shapes-messages.properties";

  /** テンプレート内のプレースホルダー（例: "{candidate}", "{limit}"）にマッチする正規表現 */
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)}");

  /** 各診断コードとメッセージテンプレート文字列の対応マップ */
  private final Map<DiagnosticCode, String> templates;

  private DiagnosticMessageCatalog(Map<DiagnosticCode, String> templates) {
    this.templates = Map.copyOf(templates);
  }

  /**
   * クラスパスから標準のメッセージ定義ファイル（{@code messages.properties}）を UTF-8 で読み込みます。
   *
   * @return 初期化済みのメッセージカタログ
   * @throws IOException ファイルの読込みに失敗した場合
   * @throws IllegalStateException リソースファイルが存在しない場合
   */
  public static DiagnosticMessageCatalog loadDefault() throws IOException {
    InputStream baseInput = DiagnosticMessageCatalog.class.getResourceAsStream(DEFAULT_RESOURCE);
    InputStream ControlFlowInput =
        DiagnosticMessageCatalog.class.getResourceAsStream(FLOW_RESOURCE);
    InputStream bindingsInput = DiagnosticMessageCatalog.class.getResourceAsStream(BIND_RESOURCE);
    InputStream arraysInput = DiagnosticMessageCatalog.class.getResourceAsStream(ARRAY_RESOURCE);
    InputStream numericsInput = DiagnosticMessageCatalog.class.getResourceAsStream(NUM_RESOURCE);
    InputStream TextRegexInput = DiagnosticMessageCatalog.class.getResourceAsStream(TEXT_RESOURCE);
    InputStream HostIoInput = DiagnosticMessageCatalog.class.getResourceAsStream(IO_RESOURCE);
    InputStream jsonInput = DiagnosticMessageCatalog.class.getResourceAsStream(JSON_RESOURCE);
    InputStream OptionalInput = DiagnosticMessageCatalog.class.getResourceAsStream(OPT_RESOURCE);
    InputStream ResultInput = DiagnosticMessageCatalog.class.getResourceAsStream(RESULT_RESOURCE);
    InputStream ConnectionInput = DiagnosticMessageCatalog.class.getResourceAsStream(CONN_RESOURCE);
    InputStream ByteSequenceInput =
        DiagnosticMessageCatalog.class.getResourceAsStream(BYTES_RESOURCE);
    InputStream httpsInput = DiagnosticMessageCatalog.class.getResourceAsStream(HTTPS_RESOURCE);
    InputStream NestedArrayInput =
        DiagnosticMessageCatalog.class.getResourceAsStream(NARRAY_RESOURCE);
    InputStream WorkspaceTableInput =
        DiagnosticMessageCatalog.class.getResourceAsStream(WST_RESOURCE);
    InputStream jsonShapesInput =
        DiagnosticMessageCatalog.class.getResourceAsStream(JSON_SHAPES_RESOURCE);
    if (baseInput == null
        || ControlFlowInput == null
        || bindingsInput == null
        || arraysInput == null
        || numericsInput == null
        || TextRegexInput == null
        || HostIoInput == null
        || jsonInput == null
        || OptionalInput == null
        || ResultInput == null
        || ConnectionInput == null
        || ByteSequenceInput == null
        || httpsInput == null
        || NestedArrayInput == null
        || WorkspaceTableInput == null
        || jsonShapesInput == null) {
      if (baseInput != null) {
        baseInput.close();
      }
      if (ControlFlowInput != null) {
        ControlFlowInput.close();
      }
      if (bindingsInput != null) {
        bindingsInput.close();
      }
      if (arraysInput != null) {
        arraysInput.close();
      }
      if (numericsInput != null) {
        numericsInput.close();
      }
      if (TextRegexInput != null) {
        TextRegexInput.close();
      }
      if (HostIoInput != null) {
        HostIoInput.close();
      }
      if (jsonInput != null) {
        jsonInput.close();
      }
      if (OptionalInput != null) {
        OptionalInput.close();
      }
      if (ResultInput != null) {
        ResultInput.close();
      }
      if (ConnectionInput != null) {
        ConnectionInput.close();
      }
      if (ByteSequenceInput != null) {
        ByteSequenceInput.close();
      }
      if (httpsInput != null) {
        httpsInput.close();
      }
      if (NestedArrayInput != null) {
        NestedArrayInput.close();
      }
      if (WorkspaceTableInput != null) {
        WorkspaceTableInput.close();
      }
      if (jsonShapesInput != null) {
        jsonShapesInput.close();
      }
      String missing =
          baseInput == null
              ? DEFAULT_RESOURCE
              : ControlFlowInput == null
                  ? FLOW_RESOURCE
                  : bindingsInput == null
                      ? BIND_RESOURCE
                      : arraysInput == null
                          ? ARRAY_RESOURCE
                          : numericsInput == null
                              ? NUM_RESOURCE
                              : TextRegexInput == null
                                  ? TEXT_RESOURCE
                                  : HostIoInput == null
                                      ? IO_RESOURCE
                                      : jsonInput == null
                                          ? JSON_RESOURCE
                                          : OptionalInput == null
                                              ? OPT_RESOURCE
                                              : ResultInput == null
                                                  ? RESULT_RESOURCE
                                                  : ConnectionInput == null
                                                      ? CONN_RESOURCE
                                                      : ByteSequenceInput == null
                                                          ? BYTES_RESOURCE
                                                          : httpsInput == null
                                                              ? HTTPS_RESOURCE
                                                              : NestedArrayInput == null
                                                                  ? NARRAY_RESOURCE
                                                                  : WorkspaceTableInput == null
                                                                      ? WST_RESOURCE
                                                                      : JSON_SHAPES_RESOURCE;
      throw new IllegalStateException("diagnostic message resource is missing: " + missing);
    }
    try (Reader baseReader = new InputStreamReader(baseInput, StandardCharsets.UTF_8);
        Reader ControlFlowReader = new InputStreamReader(ControlFlowInput, StandardCharsets.UTF_8);
        Reader bindingsReader = new InputStreamReader(bindingsInput, StandardCharsets.UTF_8);
        Reader arraysReader = new InputStreamReader(arraysInput, StandardCharsets.UTF_8);
        Reader numericsReader = new InputStreamReader(numericsInput, StandardCharsets.UTF_8);
        Reader TextRegexReader = new InputStreamReader(TextRegexInput, StandardCharsets.UTF_8);
        Reader HostIoReader = new InputStreamReader(HostIoInput, StandardCharsets.UTF_8);
        Reader jsonReader = new InputStreamReader(jsonInput, StandardCharsets.UTF_8);
        Reader OptionalReader = new InputStreamReader(OptionalInput, StandardCharsets.UTF_8);
        Reader ResultReader = new InputStreamReader(ResultInput, StandardCharsets.UTF_8);
        Reader ConnectionReader = new InputStreamReader(ConnectionInput, StandardCharsets.UTF_8);
        Reader ByteSequenceReader =
            new InputStreamReader(ByteSequenceInput, StandardCharsets.UTF_8);
        Reader httpsReader = new InputStreamReader(httpsInput, StandardCharsets.UTF_8);
        Reader NestedArrayReader = new InputStreamReader(NestedArrayInput, StandardCharsets.UTF_8);
        Reader WorkspaceTableReader =
            new InputStreamReader(WorkspaceTableInput, StandardCharsets.UTF_8);
        Reader jsonShapesReader = new InputStreamReader(jsonShapesInput, StandardCharsets.UTF_8)) {
      return load(
          baseReader,
          ControlFlowReader,
          bindingsReader,
          arraysReader,
          numericsReader,
          TextRegexReader,
          HostIoReader,
          jsonReader,
          OptionalReader,
          ResultReader,
          ConnectionReader,
          ByteSequenceReader,
          httpsReader,
          NestedArrayReader,
          WorkspaceTableReader,
          jsonShapesReader);
    }
  }

  /**
   * 指定された1個以上の {@link Reader} からメッセージ定義プロパティを読み込み、完全性を検証します。
   *
   * <p>重複キーの検出、未知の診断コードの拒否、未定義の診断コードの欠落チェックを厳格に行います。
   *
   * <p>【コンピュータ科学の観点：増分カタログ】機能グループごとにファイルを分けても、読み込み後は1個の辞書として扱います。これにより、
   * 言語コアの規範データを変更せず、制御フローの診断だけを追加できます。異なるファイル間の重複も同じキーの二重定義として拒否します。
   *
   * @param reader UTF-8で開かれた最初のReader
   * @param additionalReaders 続けて統合するUTF-8のReader
   * @return 初期化済みのメッセージカタログ
   * @throws IOException 読込みに失敗した場合
   * @throws IllegalArgumentException 重複、未知のキー、空白メッセージ、欠落がある場合
   */
  public static DiagnosticMessageCatalog load(Reader reader, Reader... additionalReaders)
      throws IOException {
    var properties = new DuplicateRejectingProperties();
    properties.load(Objects.requireNonNull(reader, "reader"));
    Objects.requireNonNull(additionalReaders, "additionalReaders");
    for (Reader additionalReader : additionalReaders) {
      properties.load(Objects.requireNonNull(additionalReader, "additionalReader"));
    }

    var templates = new EnumMap<DiagnosticCode, String>(DiagnosticCode.class);
    for (String key : properties.stringPropertyNames()) {
      DiagnosticCode code;
      try {
        code = DiagnosticCode.valueOf(key);
      } catch (IllegalArgumentException exception) {
        throw new IllegalArgumentException("unknown diagnostic code in message catalog: " + key);
      }
      String template = properties.getProperty(key);
      if (template == null || template.isBlank()) {
        throw new IllegalArgumentException("blank diagnostic message: " + key);
      }
      templates.put(code, template);
    }

    // 全ての DiagnosticCode に対するメッセージが定義されているかを厳格に検証
    var missing = new HashSet<>(Set.of(DiagnosticCode.values()));
    missing.removeAll(templates.keySet());
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException("missing diagnostic messages: " + missing);
    }
    return new DiagnosticMessageCatalog(templates);
  }

  /**
   * 診断オブジェクトのパラメータをメッセージテンプレートに埋め込み、完成したメッセージ文字列を返します。
   *
   * @param diagnostic 対象の構造化診断
   * @return プレースホルダーが実値で置換されたメッセージ文字列
   * @throws IllegalArgumentException テンプレートが要求するプレースホルダーの値が診断に含まれていない場合
   */
  public String format(Diagnostic diagnostic) {
    String template = templates.get(diagnostic.code());
    Map<String, String> values = diagnostic.templateValues();
    Matcher matcher = PLACEHOLDER.matcher(template);
    var result = new StringBuilder(template.length());
    while (matcher.find()) {
      String name = matcher.group(1);
      String value = values.get(name);
      if (value == null) {
        value = compatibilityValue(diagnostic, name);
      }
      if (value == null) {
        throw new IllegalArgumentException(
            "missing field '" + name + "' for " + diagnostic.code().name());
      }
      matcher.appendReplacement(result, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(result);

    String message = result.toString();
    // 置換されずに残った中括弧がないかチェック
    if (message.indexOf('{') >= 0 || message.indexOf('}') >= 0) {
      throw new IllegalArgumentException(
          "unresolved brace in message for " + diagnostic.code().name());
    }
    return message;
  }

  /** 既存コードを再利用する後段機能が異なる構造化フィールド名を持つ場合の表示用互換値です。 */
  private static String compatibilityValue(Diagnostic diagnostic, String name) {
    if (diagnostic.code() != DiagnosticCode.E_LOOP_STACK_MISMATCH
        || !name.equals("pathKind")
        || !diagnostic.fields().getOrDefault("loopKind", "").equals("配列")) {
      return null;
    }
    return switch (diagnostic.fields().get("path")) {
      case "normal" -> "通常終端";
      case "break" -> "脱出";
      case "continue" -> "継続";
      case null, default -> null;
    };
  }

  /** プロパティファイル内に重複したキーが存在する場合に例外をスローする安全な {@link Properties} 拡張クラス。 */
  private static final class DuplicateRejectingProperties extends Properties {
    @Override
    public synchronized Object put(Object key, Object value) {
      if (containsKey(key)) {
        throw new IllegalArgumentException("duplicate diagnostic message: " + key);
      }
      return super.put(key, value);
    }
  }
}
