package jp.bsb.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 一度だけ解析・検証された不変のCLI呼出しです。 */
record CliInvocation(
    CliCommand command,
    CliOutputFormat outputFormat,
    String sourceArgument,
    Path sourcePath,
    Optional<Path> connectionConfigPath,
    Optional<Path> workspaceConfigPath,
    List<DirectFileOption> directFiles,
    List<String> programArguments)
    implements CliArgumentResult {
  CliInvocation {
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(outputFormat, "outputFormat");
    Objects.requireNonNull(sourceArgument, "sourceArgument");
    Objects.requireNonNull(sourcePath, "sourcePath");
    connectionConfigPath = Objects.requireNonNull(connectionConfigPath, "connectionConfigPath");
    workspaceConfigPath = Objects.requireNonNull(workspaceConfigPath, "workspaceConfigPath");
    directFiles = List.copyOf(directFiles);
    programArguments = List.copyOf(programArguments);
    if (command != CliCommand.RUN && !programArguments.isEmpty()) {
      throw new IllegalArgumentException("only run accepts program arguments");
    }
    if (command != CliCommand.RUN && connectionConfigPath.isPresent()) {
      throw new IllegalArgumentException("only run accepts a connection configuration");
    }
    if (command != CliCommand.RUN && (workspaceConfigPath.isPresent() || !directFiles.isEmpty())) {
      throw new IllegalArgumentException("only run accepts workspace configuration");
    }
    if (workspaceConfigPath.isPresent() && !directFiles.isEmpty()) {
      throw new IllegalArgumentException("workspace configuration forms are mutually exclusive");
    }
    boolean validOutputFormat =
        switch (command) {
          case CHECK ->
              outputFormat == CliOutputFormat.HUMAN || outputFormat == CliOutputFormat.CHECK_JSON;
          case EXPLAIN -> outputFormat == CliOutputFormat.EXPLAIN_JSON;
          case RUN, FORMAT -> outputFormat == CliOutputFormat.HUMAN;
        };
    if (!validOutputFormat) {
      throw new IllegalArgumentException("output format is not available for command");
    }
  }

  @Override
  public Optional<String> source() {
    return Optional.of(sourceArgument);
  }
}
