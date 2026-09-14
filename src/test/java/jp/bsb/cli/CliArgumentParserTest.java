package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CliArgumentParserTest {
  @Test
  void acceptsVersionWithoutAFormatOrSource() {
    CliVersionInvocation result =
        assertInstanceOf(
            CliVersionInvocation.class, CliArgumentParser.parse(new String[] {"version"}));

    assertEquals(CliOutputFormat.HUMAN, result.outputFormat());
    assertEquals(Optional.empty(), result.source());
  }

  @Test
  void rejectsVersionArgumentsAndJson() {
    for (String[] arguments :
        List.of(new String[] {"version", "extra"}, new String[] {"version", "--json"})) {
      CliUsageError result =
          assertInstanceOf(CliUsageError.class, CliArgumentParser.parse(arguments));
      assertEquals(CliOutputFormat.HUMAN, result.outputFormat());
    }
  }

  @Test
  void acceptsHumanAndJsonCheckFormsAndJsonExplain() {
    CliInvocation human =
        assertInstanceOf(
            CliInvocation.class, CliArgumentParser.parse(new String[] {"check", "input.bsb"}));
    CliInvocation json =
        assertInstanceOf(
            CliInvocation.class,
            CliArgumentParser.parse(new String[] {"check", "--json", "input.bsb"}));
    CliInvocation explain =
        assertInstanceOf(
            CliInvocation.class,
            CliArgumentParser.parse(new String[] {"explain", "--json", "input.bsb"}));

    assertEquals(CliCommand.CHECK, human.command());
    assertEquals(CliOutputFormat.HUMAN, human.outputFormat());
    assertEquals(CliOutputFormat.CHECK_JSON, json.outputFormat());
    assertEquals("input.bsb", json.sourceArgument());
    assertEquals(List.of(), json.programArguments());
    assertEquals(CliCommand.EXPLAIN, explain.command());
    assertEquals(CliOutputFormat.EXPLAIN_JSON, explain.outputFormat());
  }

  @Test
  void recognizesJsonBeforeReportingFollowingUsageErrors() {
    CliUsageError missing =
        assertInstanceOf(
            CliUsageError.class, CliArgumentParser.parse(new String[] {"check", "--json"}));
    CliUsageError extra =
        assertInstanceOf(
            CliUsageError.class,
            CliArgumentParser.parse(new String[] {"check", "--json", "input.bsb", "extra"}));
    CliUsageError nullSource =
        assertInstanceOf(
            CliUsageError.class, CliArgumentParser.parse(new String[] {"check", "--json", null}));

    assertEquals(CliOutputFormat.CHECK_JSON, missing.outputFormat());
    assertEquals(Optional.empty(), missing.source());
    assertEquals(CliOutputFormat.CHECK_JSON, extra.outputFormat());
    assertEquals(Optional.of("input.bsb"), extra.source());
    assertEquals(CliOutputFormat.CHECK_JSON, nullSource.outputFormat());
  }

  @Test
  void rejectsJsonInEveryOtherPositionAsHumanUsage() {
    for (String[] arguments :
        List.of(
            new String[] {"--json", "check", "input.bsb"},
            new String[] {"check", "input.bsb", "--json"},
            new String[] {"--json", "explain", "input.bsb"},
            new String[] {"explain", "input.bsb", "--json"},
            new String[] {"run", "--json"},
            new String[] {"format", "--json"})) {
      CliUsageError result =
          assertInstanceOf(CliUsageError.class, CliArgumentParser.parse(arguments));
      assertEquals(CliOutputFormat.HUMAN, result.outputFormat());
    }
  }

  @Test
  void preservesJsonAfterRunSeparatorAsProgramArgument() {
    CliInvocation result =
        assertInstanceOf(
            CliInvocation.class,
            CliArgumentParser.parse(new String[] {"run", "input.bsb", "--", "--json"}));

    assertEquals(CliCommand.RUN, result.command());
    assertEquals(CliOutputFormat.HUMAN, result.outputFormat());
    assertEquals(List.of("--json"), result.programArguments());
  }

  @Test
  void acceptsOneExplicitConnectionConfigBeforeTheRunSource() {
    CliInvocation result =
        assertInstanceOf(
            CliInvocation.class,
            CliArgumentParser.parse(
                new String[] {
                  "run", "--connections", "connections.toml", "input.bsb", "--", "引数"
                }));

    assertEquals(CliCommand.RUN, result.command());
    assertEquals(
        Optional.of(java.nio.file.Path.of("connections.toml")), result.connectionConfigPath());
    assertEquals("input.bsb", result.sourceArgument());
    assertEquals(List.of("引数"), result.programArguments());
  }

  @Test
  void acceptsRepeatedFilesWorkspacesAndConnectionsInAnyPrefixOrder() {
    CliInvocation direct =
        assertInstanceOf(
            CliInvocation.class,
            CliArgumentParser.parse(
                new String[] {
                  "run",
                  "--file",
                  "帳票",
                  "入力.dat",
                  "read",
                  "in.dat",
                  "--connections",
                  "connections.toml",
                  "--file",
                  "帳票",
                  "出力.dat",
                  "write",
                  "out.dat",
                  "main.bsb",
                  "--",
                  "入力.dat"
                }));
    assertEquals(2, direct.directFiles().size());
    assertEquals("入力.dat", direct.directFiles().getFirst().logicalName());
    assertEquals(
        Optional.of(java.nio.file.Path.of("connections.toml")), direct.connectionConfigPath());
    assertEquals(List.of("入力.dat"), direct.programArguments());

    CliInvocation toml =
        assertInstanceOf(
            CliInvocation.class,
            CliArgumentParser.parse(
                new String[] {
                  "run",
                  "--connections",
                  "connections.toml",
                  "--workspaces",
                  "workspaces.toml",
                  "main.bsb"
                }));
    assertEquals(Optional.of(java.nio.file.Path.of("workspaces.toml")), toml.workspaceConfigPath());
    assertEquals(List.of(), toml.directFiles());
  }

  @Test
  void classifiesMalformedDirectAndMutuallyExclusiveFormsAsConfigurationErrors() {
    for (String[] arguments :
        List.of(
            new String[] {"run", "--file"},
            new String[] {"run", "--file", "A", "x", "execute", "p", "main.bsb"},
            new String[] {
              "run", "--file", "A", "x", "read", "a", "--file", "A", "x", "write", "b", "main.bsb"
            },
            new String[] {
              "run", "--file", "A", "x", "read", "a", "--workspaces", "w.toml", "main.bsb"
            },
            new String[] {"run", "--workspaces"},
            new String[] {"run", "--workspaces", "a.toml", "--workspaces", "b.toml", "main.bsb"})) {
      assertInstanceOf(WorkspaceCliConfigError.class, CliArgumentParser.parse(arguments));
    }
  }

  @Test
  void rejectsWorkspaceOptionsForNonRunCommands() {
    for (String option : List.of("--file", "--workspaces")) {
      CliUsageError result =
          assertInstanceOf(
              CliUsageError.class,
              CliArgumentParser.parse(new String[] {"check", option, "x", "main.bsb"}));
      assertEquals(CliOutputFormat.HUMAN, result.outputFormat());
    }
  }

  @Test
  void rejectsConnectionConfigForOtherCommandsAndIncompleteRunForms() {
    for (String[] arguments :
        List.of(
            new String[] {"check", "--connections", "connections.toml", "input.bsb"},
            new String[] {"format", "--connections", "connections.toml", "input.bsb"},
            new String[] {"run", "--connections"},
            new String[] {"run", "--connections", "connections.toml"},
            new String[] {"run", "--connections", "", "input.bsb"})) {
      assertInstanceOf(CliUsageError.class, CliArgumentParser.parse(arguments));
    }
  }

  @Test
  void rejectsUnpairedSurrogatesWithoutConstructingAPath() {
    CliUsageError result =
        assertInstanceOf(
            CliUsageError.class,
            CliArgumentParser.parse(new String[] {"check", "--json", "\uD800"}));

    assertEquals(CliOutputFormat.CHECK_JSON, result.outputFormat());
    assertEquals(Optional.empty(), result.source());
    assertEquals("引数に不正なUnicode文字は指定できません。", result.message());
  }

  @Test
  void keepsExplainJsonModeForAllFollowingUsageErrors() {
    for (String[] arguments :
        List.of(
            new String[] {"explain", "--json"},
            new String[] {"explain", "--json", ""},
            new String[] {"explain", "--json", "-source"},
            new String[] {"explain", "--json", "bad\u0000path"},
            new String[] {"explain", "--json", "input.bsb", "extra"},
            new String[] {"explain", "--json", null},
            new String[] {"explain", "--json", "\uD800"})) {
      CliUsageError result =
          assertInstanceOf(CliUsageError.class, CliArgumentParser.parse(arguments));
      assertEquals(CliOutputFormat.EXPLAIN_JSON, result.outputFormat());
    }
  }

  @Test
  void rejectsHumanExplainWithAnExplicitMessage() {
    CliUsageError result =
        assertInstanceOf(
            CliUsageError.class, CliArgumentParser.parse(new String[] {"explain", "input.bsb"}));

    assertEquals(CliOutputFormat.HUMAN, result.outputFormat());
    assertEquals("explain は --json と一緒に指定してください。", result.message());
  }
}
