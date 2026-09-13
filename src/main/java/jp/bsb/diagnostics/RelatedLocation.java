package jp.bsb.diagnostics;

import java.util.Objects;

/**
 * 診断（エラーや警告）の根本原因に関連する「別のソース位置」を表すレコードです。
 *
 * <p>【コンピュータ科学の観点：関連位置情報（Related Information）】 エラーの発生箇所だけでなく、「なぜエラーとみなされたのか」というコンテキストを示すために、
 * 別の場所への参照が必要になることがあります。 例えば「単語の二重定義エラー」の場合、2回目の定義箇所がエラーの主位置となりますが、 「1回目の定義がどこで行われたか」を関連位置（Related
 * Location）として提示することで、 プログラマは原因を素早く特定できるようになります。
 *
 * @param sourcePath 関連するソースファイルのパス。辞書項目などソース外の情報ではnull
 * @param position 関連する位置情報（行・列）。辞書項目などソース外の情報ではnull
 * @param description 関連する理由や説明（例: "前回の定義はこちらです"）
 */
public record RelatedLocation(String sourcePath, SourcePosition position, String description) {
  /**
   * コンパクトコンストラクタによるバリデーション。
   *
   * @throws IllegalArgumentException パスと位置の片方だけがない場合、または文字列が空白の場合
   */
  public RelatedLocation {
    if ((sourcePath == null) != (position == null)) {
      throw new IllegalArgumentException("sourcePath and position must both be present or absent");
    }
    if (sourcePath != null) {
      requireText(sourcePath, "sourcePath");
      Objects.requireNonNull(position, "position");
    }
    requireText(description, "description");
  }

  /**
   * 組み込み辞書の項目など、ソース位置を持たない関連情報を作ります。
   *
   * @param description 関連する理由や分類
   * @return ソース位置を持たない関連情報
   */
  public static RelatedLocation outsideSource(String description) {
    return new RelatedLocation(null, null, description);
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
