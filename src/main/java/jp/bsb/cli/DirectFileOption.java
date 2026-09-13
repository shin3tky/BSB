package jp.bsb.cli;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/** 1回の {@code --file} から構築した未正規化の直接登録です。 */
record DirectFileOption(String workspaceName, String logicalName, String access, Path path) {
  static final Set<String> ACCESSES = Set.of("read", "write", "read-write");

  DirectFileOption {
    Objects.requireNonNull(workspaceName, "workspaceName");
    Objects.requireNonNull(logicalName, "logicalName");
    Objects.requireNonNull(access, "access");
    Objects.requireNonNull(path, "path");
    if (!ACCESSES.contains(access)) throw new IllegalArgumentException("unknown file access");
  }

  @Override
  public String toString() {
    return "<direct-file-option>";
  }
}
