package jp.bsb.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** tomljの解析結果を、設定ローダー用の小さな不変APIへ変換する境界です。 */
public final class TomlAdapter {
  private TomlAdapter() {}

  public static ParseResult parse(String text) {
    TomlParseResult parsed = Toml.parse(Objects.requireNonNull(text, "text"));
    Optional<ParseError> error =
        parsed.hasErrors()
            ? Optional.of(
                new ParseError(
                    parsed.errors().getFirst().position().line(),
                    parsed.errors().getFirst().position().column()))
            : Optional.empty();
    return new ParseResult(new Table(parsed), error);
  }

  public record ParseResult(Table document, Optional<ParseError> error) {
    public ParseResult {
      Objects.requireNonNull(document, "document");
      Objects.requireNonNull(error, "error");
    }
  }

  public record ParseError(int line, int column) {}

  /** tomljのTomlTableを公開しない読取り専用ビューです。 */
  public static final class Table {
    private final TomlTable delegate;

    private Table(TomlTable delegate) {
      this.delegate = delegate;
    }

    public Set<String> keySet() {
      return delegate.keySet();
    }

    public int size() {
      return delegate.size();
    }

    public boolean isEmpty() {
      return delegate.isEmpty();
    }

    public boolean contains(String key) {
      return delegate.contains(List.of(key));
    }

    public Object get(String key) {
      Object value = delegate.get(List.of(key));
      if (value instanceof TomlTable table) {
        return new Table(table);
      }
      if (value instanceof TomlArray array) {
        return new Array(array);
      }
      return value;
    }
  }

  /** tomljのTomlArrayを公開しない読取り専用ビューです。 */
  public static final class Array {
    private final TomlArray delegate;

    private Array(TomlArray delegate) {
      this.delegate = delegate;
    }

    public int size() {
      return delegate.size();
    }

    public boolean isEmpty() {
      return delegate.isEmpty();
    }

    public Object get(int index) {
      Object value = delegate.get(index);
      if (value instanceof TomlTable table) {
        return new Table(table);
      }
      if (value instanceof TomlArray array) {
        return new Array(array);
      }
      return value;
    }
  }
}
