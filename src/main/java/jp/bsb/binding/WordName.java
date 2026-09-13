package jp.bsb.binding;

import java.util.Objects;
import jp.bsb.diagnostics.SourceSpan;

/**
 * 定数・変数と同じ名前空間へ登録する利用者定義単語です。
 *
 * @param name Unicode正規化済みの名前
 * @param spelling 元ソースに書かれた表記
 * @param nameSpan 単語名のソース範囲
 */
public record WordName(String name, String spelling, SourceSpan nameSpan) implements DeclaredName {
  /** 必須値を検証します。 */
  public WordName {
    name = requireText(name, "name");
    spelling = requireText(spelling, "spelling");
    Objects.requireNonNull(nameSpan, "nameSpan");
  }

  @Override
  public DeclaredNameKind declarationKind() {
    return DeclaredNameKind.WORD;
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value;
  }
}
