package jp.bsb.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jp.bsb.adapter.TomlAdapter;
import jp.bsb.adapter.TomlAdapter.Table;
import jp.bsb.frontend.IdentifierValidator;
import jp.bsb.frontend.UnicodeRules;
import jp.bsb.runtime.FileReadResult;
import jp.bsb.runtime.FileWriteResult;
import jp.bsb.runtime.LogicalFileName;
import jp.bsb.runtime.WorkspaceHandle;
import jp.bsb.runtime.WorkspacePolicy;
import jp.bsb.runtime.WorkspaceResolution;

/** direct指定と版1 TOMLを同じ有限登録能力へ変換します。 */
final class WorkspaceConfigLoader {
  static final long MAX_CONFIG_BYTES = 1_048_576;
  static final int MAX_WORKSPACES = 256;
  static final int MAX_FILES = 4_096;
  static final long DEFAULT_MAXIMUM_BYTES = 16_777_216;

  private static final Set<String> ROOT_KEYS = Set.of("schema-version", "workspaces");
  private static final Set<String> WORKSPACE_KEYS =
      Set.of("maximum-read-bytes", "maximum-write-bytes", "files");
  private static final Set<String> FILE_KEYS = Set.of("path", "access");

  private final WorkspaceFileAdapter adapter;

  WorkspaceConfigLoader(WorkspaceFileAdapter adapter) {
    this.adapter = java.util.Objects.requireNonNull(adapter, "adapter");
  }

  static WorkspaceConfigLoader withoutPhysicalIo() {
    return new WorkspaceConfigLoader(WorkspaceFileAdapter.recoverableFailure());
  }

  static WorkspaceConfigLoader systemFiles() {
    return new WorkspaceConfigLoader(JdkWorkspaceFileAdapter.system());
  }

  LoadedWorkspaceConfig loadDirect(List<DirectFileOption> options, Path startingDirectory)
      throws WorkspaceConfigException {
    if (options.isEmpty() || options.size() > MAX_FILES) {
      throw problem("--fileは1件以上" + MAX_FILES + "件以下で指定してください。");
    }
    Path base = absoluteDirectory(startingDirectory);
    var builders = new LinkedHashMap<String, WorkspaceBuilder>();
    for (DirectFileOption option : options) {
      validateWorkspaceName(option.workspaceName());
      validateLogicalName(option.logicalName());
      Path path = resolvePath(base, option.path());
      WorkspaceBuilder workspace =
          builders.computeIfAbsent(
              option.workspaceName(),
              ignored ->
                  new WorkspaceBuilder(
                      new WorkspacePolicy(DEFAULT_MAXIMUM_BYTES, DEFAULT_MAXIMUM_BYTES)));
      workspace.add(option.logicalName(), option.access(), path);
    }
    return build(builders);
  }

  LoadedWorkspaceConfig loadToml(Path configPath) throws WorkspaceConfigException {
    Path absoluteConfig = configPath.toAbsolutePath().normalize();
    byte[] bytes = read(absoluteConfig);
    String text = decode(bytes);
    TomlAdapter.ParseResult parsed = TomlAdapter.parse(text);
    if (parsed.error().isPresent()) {
      var position = parsed.error().orElseThrow();
      throw problem("TOML構文が正しくありません（行" + position.line() + "、列" + position.column() + "）。");
    }
    Table document = parsed.document();
    rejectUnknownKeys(document, ROOT_KEYS, "ルート");
    long schema = requireLong(document, "schema-version", "schema-version");
    if (schema != 1) throw problem("schema-versionは1でなければなりません。");
    Table workspaces = requireTable(document, "workspaces", "workspaces");
    if (workspaces.isEmpty() || workspaces.size() > MAX_WORKSPACES) {
      throw problem("workspacesは1個以上" + MAX_WORKSPACES + "個以下で指定してください。");
    }
    Path base = absoluteConfig.getParent();
    if (base == null) throw problem("作業領域設定ファイルの親を決定できません。");
    var builders = new LinkedHashMap<String, WorkspaceBuilder>();
    int totalFiles = 0;
    for (String workspaceName : workspaces.keySet()) {
      validateWorkspaceName(workspaceName);
      Object rawWorkspace = workspaces.get(workspaceName);
      if (!(rawWorkspace instanceof Table workspaceTable)) {
        throw problem("workspacesの各項目はテーブルでなければなりません。");
      }
      rejectUnknownKeys(workspaceTable, WORKSPACE_KEYS, "作業領域");
      long maximumRead =
          optionalLong(
              workspaceTable, "maximum-read-bytes", DEFAULT_MAXIMUM_BYTES, "maximum-read-bytes");
      long maximumWrite =
          optionalLong(
              workspaceTable, "maximum-write-bytes", DEFAULT_MAXIMUM_BYTES, "maximum-write-bytes");
      WorkspacePolicy policy;
      try {
        policy = new WorkspacePolicy(maximumRead, maximumWrite);
      } catch (IllegalArgumentException failure) {
        throw problem("作業領域の操作別上限が規範範囲外です。");
      }
      Table files = requireTable(workspaceTable, "files", "files");
      if (files.isEmpty()) throw problem("各作業領域のfilesは1件以上必要です。");
      var builder = new WorkspaceBuilder(policy);
      for (String logicalName : files.keySet()) {
        if (++totalFiles > MAX_FILES) {
          throw problem("全作業領域のfilesは" + MAX_FILES + "件以下で指定してください。");
        }
        validateLogicalName(logicalName);
        Object rawFile = files.get(logicalName);
        if (!(rawFile instanceof Table fileTable)) {
          throw problem("filesの各項目はテーブルでなければなりません。");
        }
        rejectUnknownKeys(fileTable, FILE_KEYS, "ファイル登録");
        String pathText = requireString(fileTable, "path", "path");
        String access = requireString(fileTable, "access", "access");
        if (!DirectFileOption.ACCESSES.contains(access)) {
          throw problem("accessはread、write、read-writeのいずれかにしてください。");
        }
        Path path;
        try {
          path = Path.of(pathText);
        } catch (InvalidPathException failure) {
          throw problem("pathが正しくありません。");
        }
        builder.add(logicalName, access, resolvePath(base, path));
      }
      builders.put(workspaceName, builder);
    }
    return build(builders);
  }

  private LoadedWorkspaceConfig build(Map<String, WorkspaceBuilder> builders)
      throws WorkspaceConfigException {
    var byName = new LinkedHashMap<String, ConfiguredWorkspace>();
    var byHandle = new LinkedHashMap<WorkspaceHandle, ConfiguredWorkspace>();
    for (Map.Entry<String, WorkspaceBuilder> entry : builders.entrySet()) {
      WorkspaceHandle handle = WorkspaceHandle.opaque();
      ConfiguredWorkspace configured = entry.getValue().finish(handle);
      byName.put(entry.getKey(), configured);
      byHandle.put(handle, configured);
    }
    Map<String, ConfiguredWorkspace> names = Map.copyOf(byName);
    Map<WorkspaceHandle, ConfiguredWorkspace> handles = Map.copyOf(byHandle);
    return new LoadedWorkspaceConfig(
        (name, operation) -> {
          ConfiguredWorkspace workspace = names.get(name);
          return workspace == null
              ? WorkspaceResolution.notConfigured(name, operation)
              : WorkspaceResolution.resolved(
                  name, operation, workspace.handle(), workspace.policy());
        },
        request -> {
          ConfiguredFile file = mapped(handles, request.handle(), request.logicalName());
          if (file == null) return FileReadResult.notMapped(request);
          if (!file.readable()) return FileReadResult.accessDenied(request);
          return adapter.read(request, file.path());
        },
        request -> {
          ConfiguredFile file = mapped(handles, request.handle(), request.logicalName());
          if (file == null) return FileWriteResult.notMapped(request);
          if (!file.writable()) return FileWriteResult.accessDenied(request);
          return adapter.write(request, file.path());
        });
  }

  private static ConfiguredFile mapped(
      Map<WorkspaceHandle, ConfiguredWorkspace> handles,
      WorkspaceHandle handle,
      String logicalName) {
    ConfiguredWorkspace workspace = handles.get(handle);
    return workspace == null ? null : workspace.files().get(logicalName);
  }

  private static byte[] read(Path path) throws WorkspaceConfigException {
    try {
      long size = Files.size(path);
      if (size > MAX_CONFIG_BYTES) throw problem("作業領域設定ファイルが1 MiBを超えています。");
      try (var input = Files.newInputStream(path)) {
        byte[] bytes = input.readNBytes(Math.toIntExact(MAX_CONFIG_BYTES + 1));
        if (bytes.length > MAX_CONFIG_BYTES) {
          throw problem("作業領域設定ファイルが1 MiBを超えています。");
        }
        return bytes;
      }
    } catch (IOException failure) {
      throw problem("作業領域設定ファイルを読み取れません。");
    }
  }

  private static String decode(byte[] bytes) throws WorkspaceConfigException {
    if (bytes.length >= 3
        && bytes[0] == (byte) 0xef
        && bytes[1] == (byte) 0xbb
        && bytes[2] == (byte) 0xbf) {
      throw problem("作業領域設定ファイルにUTF-8 BOMは使用できません。");
    }
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException failure) {
      throw problem("作業領域設定ファイルが正しいUTF-8ではありません。");
    }
  }

  private static void validateWorkspaceName(String name) throws WorkspaceConfigException {
    String normalized = UnicodeRules.normalizeIdentifier(name);
    if (!normalized.equals(name)
        || !UnicodeRules.isValidIdentifier(name)
        || name.codePointCount(0, name.length()) > IdentifierValidator.MAX_IDENTIFIER_CODE_POINTS) {
      throw problem("作業領域名は正規化済みの有効なソース識別子でなければなりません。");
    }
  }

  private static void validateLogicalName(String name) throws WorkspaceConfigException {
    if (LogicalFileName.problem(name) != null) {
      throw problem("論理ファイル名が規則に適合しません。");
    }
  }

  private static Path absoluteDirectory(Path directory) throws WorkspaceConfigException {
    Path result = directory.toAbsolutePath().normalize();
    if (result.getFileName() == null && result.getParent() == null && !result.isAbsolute()) {
      throw problem("開始作業ディレクトリを決定できません。");
    }
    return result;
  }

  private static Path resolvePath(Path base, Path path) throws WorkspaceConfigException {
    try {
      return (path.isAbsolute() ? path : base.resolve(path)).toAbsolutePath().normalize();
    } catch (InvalidPathException failure) {
      throw problem("pathが正しくありません。");
    }
  }

  private static void rejectUnknownKeys(Table table, Set<String> allowed, String scope)
      throws WorkspaceConfigException {
    var unknown = new HashSet<>(table.keySet());
    unknown.removeAll(allowed);
    if (!unknown.isEmpty()) throw problem(scope + "に未対応の項目があります。");
  }

  private static long requireLong(Table table, String key, String display)
      throws WorkspaceConfigException {
    Object value = table.get(key);
    if (!(value instanceof Long number)) throw problem(display + "には整数を指定してください。");
    return number;
  }

  private static long optionalLong(Table table, String key, long defaultValue, String display)
      throws WorkspaceConfigException {
    return table.contains(key) ? requireLong(table, key, display) : defaultValue;
  }

  private static String requireString(Table table, String key, String display)
      throws WorkspaceConfigException {
    Object value = table.get(key);
    if (!(value instanceof String text) || text.isEmpty() || text.indexOf('\u0000') >= 0) {
      throw problem(display + "には空でない文字列を指定してください。");
    }
    return text;
  }

  private static Table requireTable(Table table, String key, String display)
      throws WorkspaceConfigException {
    Object value = table.get(key);
    if (!(value instanceof Table nested)) throw problem(display + "にはテーブルを指定してください。");
    return nested;
  }

  private static WorkspaceConfigException problem(String message) {
    return new WorkspaceConfigException(message);
  }

  private static final class WorkspaceBuilder {
    private final WorkspacePolicy policy;
    private final Map<String, ConfiguredFile> files = new LinkedHashMap<>();

    private WorkspaceBuilder(WorkspacePolicy policy) {
      this.policy = policy;
    }

    private void add(String logicalName, String access, Path path) throws WorkspaceConfigException {
      if (files.putIfAbsent(logicalName, new ConfiguredFile(path, access)) != null) {
        throw problem("同じ作業領域名と論理ファイル名を重複指定できません。");
      }
    }

    private ConfiguredWorkspace finish(WorkspaceHandle handle) {
      return new ConfiguredWorkspace(handle, policy, Map.copyOf(files));
    }
  }

  private record ConfiguredWorkspace(
      WorkspaceHandle handle, WorkspacePolicy policy, Map<String, ConfiguredFile> files) {}

  private record ConfiguredFile(Path path, String access) {
    private boolean readable() {
      return access.equals("read") || access.equals("read-write");
    }

    private boolean writable() {
      return access.equals("write") || access.equals("read-write");
    }

    @Override
    public String toString() {
      return "<configured-file>";
    }
  }
}
