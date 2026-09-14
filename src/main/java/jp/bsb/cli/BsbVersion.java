package jp.bsb.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** ビルド時に埋め込まれた処理系の版を提供します。 */
final class BsbVersion {
  private static final String RESOURCE = "/jp/bsb/cli/version.properties";
  private static final Properties CURRENT = load();

  private BsbVersion() {}

  static String current() {
    return required("version");
  }

  static String commit() {
    String commit = required("commit");
    if (!commit.equals("unknown") && !commit.matches("[0-9a-f]{40}|[0-9a-f]{64}")) {
      throw new IllegalStateException("commit is invalid");
    }
    return commit;
  }

  private static Properties load() {
    var properties = new Properties();
    try (InputStream input = BsbVersion.class.getResourceAsStream(RESOURCE)) {
      if (input == null) {
        throw new IllegalStateException("version resource is missing");
      }
      properties.load(input);
    } catch (IOException exception) {
      throw new IllegalStateException("version resource cannot be read", exception);
    }
    return properties;
  }

  private static String required(String name) {
    String value = CURRENT.getProperty(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " is missing");
    }
    return value;
  }
}
