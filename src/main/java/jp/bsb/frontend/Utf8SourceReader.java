package jp.bsb.frontend;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Optional;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticCollector;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.FileOffset;
import jp.bsb.diagnostics.Severity;

/**
 * ソースファイルをバイト列として読み込み、サイズ検証、先頭BOM処理、 および厳密なUTF-8デコードを行うソースリーダーです。
 *
 * <p>【コンピュータ科学の観点：文字エンコーディングの厳密性とセキュリティ】
 *
 * <ul>
 *   <li><b>厳密なデコード（Strict Decoding）</b>: 一部の簡易な文字列変換APIは、 不正なUTF-8バイト列を置換文字（U+FFFD REPLACEMENT
 *       CHARACTER）へ置き換えて処理を継続します。 しかし、プログラミング言語の処理系では、意図しない解釈やセキュリティ脆弱性（エンコーディングのすり抜け攻撃等）を防ぐため、
 *       不正バイトを一切容認せず、最初に不正が発生した正確なバイト位置でエラー（{@link DiagnosticCode#E_INVALID_UTF8}）を報告します。
 *   <li><b>BOM（Byte Order Mark: {@code EF BB BF}）の扱い</b>: Windows環境のエディタ等が付与するファイル先頭のUTF-8
 *       BOMを受理して除去します。 ただし、ファイル途中に現れるBOM（U+FEFF）は不可視文字として後続の字句解析で検出・拒否します。
 *   <li><b>資源制限（DoS攻撃防止）</b>: デコード前のファイルサイズを32MiB（33,554,432バイト）に制限し、 巨大なファイルによるメモリ使用量を一定範囲へ制限します。
 * </ul>
 */
public final class Utf8SourceReader {
  /** ソースファイルの最大許容バイト数（32 MiB） */
  public static final int MAX_SOURCE_BYTES = 32 * 1024 * 1024;

  /** UTF-8のBOMバイトシーケンス（0xEF, 0xBB, 0xBF） */
  private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  /** デコード処理用のバッファサイズ（8 KiB） */
  private static final int DECODE_BUFFER_SIZE = 8192;

  /** 厳密なUTF-8規則と固定サイズ上限を使う読取り器を作ります。 */
  public Utf8SourceReader() {}

  /**
   * 指定されたパスのファイルからソースコードを読み込み、{@link SourceText} を生成します。
   *
   * @param path ファイルパス
   * @param diagnostics 診断収集器
   * @return 読込み・デコードに成功した場合は {@link SourceText}、失敗した場合は {@link Optional#empty()}
   * @throws IOException ファイルの物理的なI/Oエラーが発生した場合
   */
  public Optional<SourceText> read(Path path, DiagnosticCollector diagnostics) throws IOException {
    String sourcePath = path.toString();
    long size = Files.size(path);
    if (size > MAX_SOURCE_BYTES) {
      diagnostics.add(sourceSizeDiagnostic(sourcePath, size));
      return Optional.empty();
    }
    return read(sourcePath, Files.readAllBytes(path), diagnostics);
  }

  /**
   * 生のバイト配列からソースコードを読み込み、厳密なUTF-8デコードを行います。
   *
   * @param sourcePath ソースの識別パス（エラー表示用）
   * @param originalBytes ファイルの生バイト配列
   * @param diagnostics 診断収集器
   * @return 成功時は {@link SourceText}、失敗時は {@link Optional#empty()}
   */
  public Optional<SourceText> read(
      String sourcePath, byte[] originalBytes, DiagnosticCollector diagnostics) {
    if (originalBytes.length > MAX_SOURCE_BYTES) {
      diagnostics.add(sourceSizeDiagnostic(sourcePath, originalBytes.length));
      return Optional.empty();
    }

    // 先頭にBOMが存在する場合はスキップ（先頭オフセットを3に設定）
    int contentStart = hasLeadingBom(originalBytes) ? UTF8_BOM.length : 0;
    // 不正バイトやマッピング不能文字を置換せず、即座にエラー報告する設定のデコーダ
    var decoder =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    var input = ByteBuffer.wrap(originalBytes);
    input.position(contentStart);
    var output = CharBuffer.allocate(DECODE_BUFFER_SIZE);
    var decoded = new StringBuilder(originalBytes.length - contentStart);

    while (true) {
      var result = decoder.decode(input, output, true);
      appendDecoded(output, decoded);
      if (result.isError()) {
        // デコードエラーが発生した位置と、そこまでに正常デコードできた接頭辞から診断を生成
        int invalidOffset = input.position();
        SourceText validPrefix = new SourceText(sourcePath, decoded.toString(), contentStart);
        diagnostics.add(
            invalidUtf8Diagnostic(sourcePath, originalBytes, invalidOffset, validPrefix));
        return Optional.empty();
      }
      if (result.isUnderflow()) {
        break;
      }
    }

    while (true) {
      var result = decoder.flush(output);
      appendDecoded(output, decoded);
      if (result.isUnderflow()) {
        break;
      }
    }
    return Optional.of(new SourceText(sourcePath, decoded.toString(), contentStart));
  }

  private static void appendDecoded(CharBuffer output, StringBuilder decoded) {
    output.flip();
    decoded.append(output);
    output.clear();
  }

  /** 先頭バイト列が UTF-8 BOM（EF BB BF）であるかを判定します。 */
  private static boolean hasLeadingBom(byte[] bytes) {
    if (bytes.length < UTF8_BOM.length) {
      return false;
    }
    for (int index = 0; index < UTF8_BOM.length; index++) {
      if (bytes[index] != UTF8_BOM[index]) {
        return false;
      }
    }
    return true;
  }

  /** ファイルサイズ超過エラー（E_SOURCE_SIZE_LIMIT）を生成します。 */
  private static Diagnostic sourceSizeDiagnostic(String sourcePath, long observed) {
    return Diagnostic.builder(
            DiagnosticCode.E_SOURCE_SIZE_LIMIT,
            Severity.ERROR,
            DiagnosticStage.UTF8,
            sourcePath,
            new FileOffset(MAX_SOURCE_BYTES))
        .limit("sourceBytes", MAX_SOURCE_BYTES, observed)
        .build();
  }

  /** 不正なUTF-8バイト列エラー（E_INVALID_UTF8）を生成します（16進数ダンプ付き）。 */
  private static Diagnostic invalidUtf8Diagnostic(
      String sourcePath, byte[] bytes, int invalidOffset, SourceText validPrefix) {
    int invalidLength = invalidSequenceLength(bytes, invalidOffset);
    String actual =
        HexFormat.of()
            .withUpperCase()
            .withDelimiter(" ")
            .formatHex(bytes, invalidOffset, invalidOffset + invalidLength);
    return Diagnostic.builder(
            DiagnosticCode.E_INVALID_UTF8,
            Severity.ERROR,
            DiagnosticStage.UTF8,
            sourcePath,
            validPrefix.positionAt(validPrefix.text().length()))
        .expected("UTF-8")
        .actual(actual)
        .fix("UTF-8で保存し直してください")
        .build();
  }

  /** UTF-8の先頭バイトのビットパターンから、期待されるバイトシーケンス長を判定します。 */
  private static int invalidSequenceLength(byte[] bytes, int offset) {
    int unsigned = Byte.toUnsignedInt(bytes[offset]);
    int expectedLength;
    if (unsigned >= 0xC2 && unsigned <= 0xDF) {
      expectedLength = 2; // 2バイト文字の先頭
    } else if (unsigned >= 0xE0 && unsigned <= 0xEF) {
      expectedLength = 3; // 3バイト文字の先頭
    } else if (unsigned >= 0xF0 && unsigned <= 0xF4) {
      expectedLength = 4; // 4バイト文字の先頭
    } else {
      expectedLength = 1; // 1バイト文字または不正な後続バイト等
    }
    return Math.min(expectedLength, bytes.length - offset);
  }
}
