package jp.bsb.cli;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/** 公開CLI引数を検証し、位置に依存する解釈を不変値へ閉じ込めます。 */
final class CliArgumentParser {
  private CliArgumentParser() {}

  static CliArgumentResult parse(String[] arguments) {
    boolean checkJson =
        arguments.length >= 2 && "check".equals(arguments[0]) && "--json".equals(arguments[1]);
    boolean explainJson =
        arguments.length >= 2 && "explain".equals(arguments[0]) && "--json".equals(arguments[1]);
    boolean json = checkJson || explainJson;
    CliOutputFormat outputFormat =
        checkJson
            ? CliOutputFormat.CHECK_JSON
            : explainJson ? CliOutputFormat.EXPLAIN_JSON : CliOutputFormat.HUMAN;

    for (String argument : arguments) {
      if (argument == null) {
        return error(outputFormat, arguments, json, "引数にnullは指定できません。");
      }
      if (!hasOnlyUnicodeScalars(argument)) {
        return error(outputFormat, arguments, json, "引数に不正なUnicode文字は指定できません。");
      }
    }
    if (arguments.length == 0) {
      return error(outputFormat, arguments, json, "サブコマンドとソースファイルを1つずつ指定してください。");
    }
    if (!json
        && (arguments[0].equals("--json")
            || (arguments.length >= 2 && arguments[1].equals("--json")))) {
      return error(outputFormat, arguments, false, "--json は check または explain の直後にだけ指定できます。");
    }

    CliCommand command = command(arguments[0]);
    if (command == null) {
      return error(outputFormat, arguments, json, "未知のサブコマンドです: " + arguments[0]);
    }
    if (command == CliCommand.EXPLAIN && !json) {
      return error(outputFormat, arguments, false, "explain は --json と一緒に指定してください。");
    }
    if (command == CliCommand.RUN) {
      return parseRun(arguments);
    }
    return parseSingleSource(arguments, command, outputFormat, json);
  }

  private static CliArgumentResult parseRun(String[] arguments) {
    var directFiles = new ArrayList<DirectFileOption>();
    var mappingKeys = new LinkedHashSet<String>();
    Optional<Path> connections = Optional.empty();
    Optional<Path> workspaces = Optional.empty();
    int index = 1;
    while (index < arguments.length && arguments[index].startsWith("-")) {
      String option = arguments[index];
      switch (option) {
        case "--connections" -> {
          if (connections.isPresent()) return usage("--connections は1回だけ指定できます。");
          if (index + 1 >= arguments.length) {
            return usage("--connections の後に設定ファイルを1つ指定してください。");
          }
          Path path = optionPath(arguments[index + 1]);
          if (path == null) return usage("接続設定ファイルのパスが正しくありません。");
          connections = Optional.of(path);
          index += 2;
        }
        case "--workspaces" -> {
          if (workspaces.isPresent()) {
            return workspaceError("--workspacesは1回だけ指定してください。");
          }
          if (index + 1 >= arguments.length) {
            return workspaceError("--workspacesの後に設定ファイルを1つ指定してください。");
          }
          Path path = optionPath(arguments[index + 1]);
          if (path == null) return workspaceError("作業領域設定ファイルのパスが正しくありません。");
          workspaces = Optional.of(path);
          index += 2;
        }
        case "--file" -> {
          if (index + 4 >= arguments.length) {
            return workspaceError("--fileには作業領域名、論理名、access、OSパスが必要です。");
          }
          if (directFiles.size() >= WorkspaceConfigLoader.MAX_FILES) {
            return workspaceError("--fileは" + WorkspaceConfigLoader.MAX_FILES + "件以下で指定してください。");
          }
          String workspace = arguments[index + 1];
          String logicalName = arguments[index + 2];
          String access = arguments[index + 3];
          Path path = optionPath(arguments[index + 4]);
          if (!DirectFileOption.ACCESSES.contains(access)) {
            return workspaceError("--fileのaccessはread、write、read-writeのいずれかにしてください。");
          }
          if (path == null) return workspaceError("--fileのOSパスが正しくありません。");
          String key = workspace + "\u0000" + logicalName;
          if (!mappingKeys.add(key)) {
            return workspaceError("同じ作業領域名と論理ファイル名を重複指定できません。");
          }
          directFiles.add(new DirectFileOption(workspace, logicalName, access, path));
          index += 5;
        }
        case "--" -> {
          return usage("runのソースファイルを -- より前に指定してください。");
        }
        default -> {
          return usage("使用できないオプションです: " + option);
        }
      }
    }

    if (!directFiles.isEmpty() && workspaces.isPresent()) {
      return workspaceError("--fileと--workspacesは同時に指定できません。");
    }
    if (index >= arguments.length) {
      return usage("サブコマンドとソースファイルを1つずつ指定してください。");
    }
    String source = arguments[index];
    Path sourcePath = sourcePath(source);
    if (sourcePath == null) {
      return usage(
          source.isBlank()
              ? "ソースファイルのパスが空です。"
              : source.startsWith("-") ? "使用できないオプションです: " + source : "ソースファイルのパスが正しくありません。");
    }
    int separatorIndex = index + 1;
    if (arguments.length > separatorIndex && !arguments[separatorIndex].equals("--")) {
      String unexpected = arguments[separatorIndex];
      return usage(
          unexpected.startsWith("-")
              ? "使用できないオプションです: " + unexpected
              : "runの起動引数の前に -- を指定してください。");
    }
    List<String> programArguments =
        arguments.length > separatorIndex + 1
            ? Arrays.asList(arguments).subList(separatorIndex + 1, arguments.length)
            : List.of();
    return new CliInvocation(
        CliCommand.RUN,
        CliOutputFormat.HUMAN,
        source,
        sourcePath,
        connections,
        workspaces,
        directFiles,
        programArguments);
  }

  private static CliArgumentResult parseSingleSource(
      String[] arguments, CliCommand command, CliOutputFormat outputFormat, boolean json) {
    if (!json
        && arguments.length >= 2
        && (arguments[1].equals("--connections")
            || arguments[1].equals("--workspaces")
            || arguments[1].equals("--file"))) {
      return error(outputFormat, arguments, false, arguments[1] + " は run でだけ使用できます。");
    }
    int sourceIndex = json ? 2 : 1;
    if (arguments.length <= sourceIndex) {
      return error(
          outputFormat,
          arguments,
          json,
          json ? "ソースファイルを1つ指定してください。" : "サブコマンドとソースファイルを1つずつ指定してください。");
    }
    if (arguments.length > sourceIndex + 1) {
      return error(outputFormat, arguments, json, "余分な引数があります。");
    }
    String source = arguments[sourceIndex];
    Path sourcePath = sourcePath(source);
    if (sourcePath == null) {
      return new CliUsageError(
          outputFormat,
          Optional.empty(),
          source.isBlank()
              ? "ソースファイルのパスが空です。"
              : source.startsWith("-") ? "使用できないオプションです: " + source : "ソースファイルのパスが正しくありません。");
    }
    return new CliInvocation(
        command,
        outputFormat,
        source,
        sourcePath,
        Optional.empty(),
        Optional.empty(),
        List.of(),
        List.of());
  }

  private static Path optionPath(String value) {
    if (value.isBlank() || value.indexOf('\u0000') >= 0) return null;
    try {
      return Path.of(value);
    } catch (InvalidPathException exception) {
      return null;
    }
  }

  private static Path sourcePath(String source) {
    if (source.isBlank() || source.startsWith("-")) return null;
    try {
      return Path.of(source);
    } catch (InvalidPathException exception) {
      return null;
    }
  }

  private static CliUsageError usage(String message) {
    return new CliUsageError(CliOutputFormat.HUMAN, Optional.empty(), message);
  }

  private static WorkspaceCliConfigError workspaceError(String message) {
    return new WorkspaceCliConfigError(Optional.empty(), message);
  }

  private static CliUsageError error(
      CliOutputFormat outputFormat, String[] arguments, boolean json, String message) {
    int sourceIndex = json ? 2 : 1;
    Optional<String> source =
        arguments.length > sourceIndex ? validSource(arguments[sourceIndex]) : Optional.empty();
    return new CliUsageError(outputFormat, source, message);
  }

  private static Optional<String> validSource(String source) {
    if (source == null
        || source.isBlank()
        || source.startsWith("-")
        || !hasOnlyUnicodeScalars(source)) {
      return Optional.empty();
    }
    try {
      Path.of(source);
      return Optional.of(source);
    } catch (InvalidPathException exception) {
      return Optional.empty();
    }
  }

  private static CliCommand command(String command) {
    return switch (command) {
      case "check" -> CliCommand.CHECK;
      case "explain" -> CliCommand.EXPLAIN;
      case "run" -> CliCommand.RUN;
      case "format" -> CliCommand.FORMAT;
      default -> null;
    };
  }

  private static boolean hasOnlyUnicodeScalars(String value) {
    for (int index = 0; index < value.length(); ) {
      char character = value.charAt(index);
      if (Character.isHighSurrogate(character)) {
        if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
          return false;
        }
        index += 2;
      } else if (Character.isLowSurrogate(character)) {
        return false;
      } else {
        index++;
      }
    }
    return true;
  }
}
