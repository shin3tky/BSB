package jp.bsb.frontend;

/**
 * 0始まりの行番号と、その行の先頭から数えたUTF-16コード単位位置です。
 *
 * <p>Language Server Protocolの既定位置表現と同じ座標系ですが、フロントエンドを特定の通信ライブラリへ依存させないための独立した値型です。
 *
 * @param line 0始まりの行番号
 * @param character 行内の0始まりUTF-16コード単位位置
 */
public record Utf16Position(int line, int character) {
  /** 座標がどちらも非負であることを検証します。 */
  public Utf16Position {
    if (line < 0) {
      throw new IllegalArgumentException("line must not be negative");
    }
    if (character < 0) {
      throw new IllegalArgumentException("character must not be negative");
    }
  }
}
