package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import jp.bsb.conformance.WorkspaceTableConformanceData;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

/** 独立corpusに対する厳密CSV/TSV解析、位置、資源、原子性を検証します。 */
class WorkspaceTableDelimitedParserRuntimeTest {
  private static final String SOURCE_PATH = "table.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void matchesEveryIndependentSuccessAndFailureVector() throws Exception {
    for (var expected : WorkspaceTableConformanceData.loadDelimitedParses()) {
      String input = decodeHex(expected.value("input_hex"));
      var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
      var stack = stack(new StringValue(input));

      execute(parserName(expected.value("format")), stack, budget);

      ResultValue result = (ResultValue) stack.getFirst();
      long expectedWork =
          input.getBytes(StandardCharsets.UTF_8).length + input.codePointCount(0, input.length());
      assertEquals(expectedWork, budget.delimitedTextWorkUnits(), expected.value("variant"));
      if (expected.value("outcome").equals("success")) {
        assertTrue(result.isSuccess(), expected.value("variant"));
        assertEquals(
            expectedTable(expected.value("table")), result.value(), expected.value("variant"));
        ArrayValue table = (ArrayValue) result.value();
        assertEquals(
            table.logicalLeafCount() + table.size(),
            budget.arrayConstructionUnits(),
            expected.value("variant"));
      } else {
        DelimitedTextParseFailureValue failure = (DelimitedTextParseFailureValue) result.value();
        assertEquals(expected.value("failure_kind"), failure.kind(), expected.value("variant"));
        assertEquals(
            Long.parseLong(expected.value("byte_position")),
            failure.utf8Offset(),
            expected.value("variant"));
        assertEquals(
            Integer.parseInt(expected.value("line")), failure.line(), expected.value("variant"));
        assertEquals(
            Integer.parseInt(expected.value("column")),
            failure.column(),
            expected.value("variant"));
        assertEquals(0, budget.arrayConstructionUnits(), expected.value("variant"));
      }
    }
  }

  @Test
  void workLimitIsAtomicAndPrecedesParsing() throws Exception {
    var exact =
        ExecutionBudget.withDelimitedTextWork(
            SOURCE_PATH, () -> 0L, 0, DelimitedTextLimits.MAX_WORK_UNITS - 2);
    var exactStack = stack(new StringValue("a"));
    execute("CSVを表として解析する", exactStack, exact);
    assertEquals(DelimitedTextLimits.MAX_WORK_UNITS, exact.delimitedTextWorkUnits());

    var rejected =
        ExecutionBudget.withDelimitedTextWork(
            SOURCE_PATH, () -> 0L, 0, DelimitedTextLimits.MAX_WORK_UNITS - 1);
    StringValue original = new StringValue("\u0000");
    var rejectedStack = stack(original);
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("CSVを表として解析する", rejectedStack, rejected));
    assertEquals(DiagnosticCode.E_DELIMITED_TEXT_WORK_LIMIT, failure.diagnostic().code());
    assertSame(original, rejectedStack.getFirst());
    assertEquals(DelimitedTextLimits.MAX_WORK_UNITS - 1, rejected.delimitedTextWorkUnits());
    assertEquals(0, rejected.arrayConstructionUnits());
  }

  @Test
  void checksRowOuterAndLogicalLimitsAfterGrammar() throws Exception {
    assertResourceFailure(",".repeat(65_536), DiagnosticCode.E_ARRAY_LENGTH_LIMIT);
    assertResourceFailure("\n".repeat(65_537), DiagnosticCode.E_ARRAY_LENGTH_LIMIT);
    String row = ",".repeat(999);
    String logicalAt = (row + "\n").repeat(1_000);
    assertResourceFailure(logicalAt, DiagnosticCode.E_ARRAY_CONSTRUCTION_LIMIT);
    String overRow = ",".repeat(9_900);
    String logicalOver = (overRow + "\n").repeat(100) + overRow;
    assertResourceFailure(logicalOver, DiagnosticCode.E_ARRAY_NESTED_ELEMENT_LIMIT);
  }

  @Test
  void acceptsExactRowAndOuterBoundariesWithSpecifiedConstructionWork() throws Exception {
    var rowBudget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    var rowStack = stack(new StringValue(",".repeat(65_535)));
    execute("CSVを表として解析する", rowStack, rowBudget);
    ArrayValue rowTable = (ArrayValue) ((ResultValue) rowStack.getFirst()).value();
    assertEquals(1, rowTable.size());
    assertEquals(65_536, rowTable.logicalLeafCount());
    assertEquals(65_537, rowBudget.arrayConstructionUnits());
    assertEquals(131_070, rowBudget.delimitedTextWorkUnits());

    var outerBudget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    var outerStack = stack(new StringValue("\n".repeat(65_536)));
    execute("CSVを表として解析する", outerStack, outerBudget);
    ArrayValue outerTable = (ArrayValue) ((ResultValue) outerStack.getFirst()).value();
    assertEquals(65_536, outerTable.size());
    assertEquals(65_536, outerTable.logicalLeafCount());
    assertEquals(131_072, outerBudget.arrayConstructionUnits());
    assertEquals(131_072, outerBudget.delimitedTextWorkUnits());
  }

  @Test
  void checksInputAndConstructionBoundariesInNormativeOrder() throws Exception {
    var exactInputBudget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    var exactInputStack = stack(new StringValue("a".repeat(StringLimits.MAX_UTF8_BYTES)));
    execute("CSVを表として解析する", exactInputStack, exactInputBudget);
    assertEquals(2, exactInputBudget.arrayConstructionUnits());
    assertEquals(33_554_432, exactInputBudget.delimitedTextWorkUnits());

    var overInputBudget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    StringValue overInput = new StringValue("a".repeat(StringLimits.MAX_UTF8_BYTES + 1));
    var overInputStack = stack(overInput);
    RuntimeFailure inputFailure =
        assertThrows(
            RuntimeFailure.class, () -> execute("CSVを表として解析する", overInputStack, overInputBudget));
    assertEquals(DiagnosticCode.E_STRING_UTF8_LIMIT, inputFailure.diagnostic().code());
    assertSame(overInput, overInputStack.getFirst());
    assertEquals(0, overInputBudget.delimitedTextWorkUnits());

    var exactConstruction =
        ExecutionBudget.withDelimitedTextWork(SOURCE_PATH, () -> 0L, 999_997, 0);
    var exactConstructionStack = stack(new StringValue(",\n"));
    execute("CSVを表として解析する", exactConstructionStack, exactConstruction);
    assertEquals(1_000_000, exactConstruction.arrayConstructionUnits());
    assertEquals(4, exactConstruction.delimitedTextWorkUnits());

    var overConstruction = ExecutionBudget.withDelimitedTextWork(SOURCE_PATH, () -> 0L, 999_998, 0);
    StringValue constructionInput = new StringValue(",\n");
    var overConstructionStack = stack(constructionInput);
    RuntimeFailure constructionFailure =
        assertThrows(
            RuntimeFailure.class,
            () -> execute("CSVを表として解析する", overConstructionStack, overConstruction));
    assertEquals(
        DiagnosticCode.E_ARRAY_CONSTRUCTION_LIMIT, constructionFailure.diagnostic().code());
    assertSame(constructionInput, overConstructionStack.getFirst());
    assertEquals(999_998, overConstruction.arrayConstructionUnits());
    assertEquals(4, overConstruction.delimitedTextWorkUnits());
  }

  @Test
  void grammarFailurePrecedesStructureAndNeverReservesConstruction() throws Exception {
    String input = ",".repeat(65_536) + "a\"";
    var budget = ExecutionBudget.withDelimitedTextWork(SOURCE_PATH, () -> 0L, 999_999, 0);
    var stack = stack(new StringValue(input));

    execute("CSVを表として解析する", stack, budget);

    ResultValue result = (ResultValue) stack.getFirst();
    assertEquals("unexpectedQuote", ((DelimitedTextParseFailureValue) result.value()).kind());
    assertEquals(999_999, budget.arrayConstructionUnits());
    assertTrue(budget.delimitedTextWorkUnits() > 0);
  }

  @Test
  void localeTimezoneAndRepeatedRunsCannotChangeTheResult() throws Exception {
    Locale previousLocale = Locale.getDefault();
    TimeZone previousZone = TimeZone.getDefault();
    try {
      RuntimeValue baseline = parseOnce("\uFEFF\"名,前\",😀\r\n東京,値", "CSVを表として解析する");
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
      assertEquals(baseline, parseOnce("\uFEFF\"名,前\",😀\r\n東京,値", "CSVを表として解析する"));
    } finally {
      Locale.setDefault(previousLocale);
      TimeZone.setDefault(previousZone);
    }
  }

  @Test
  void publicRunResultExposesDelimitedWork() {
    String source = "メインとは （--）\n    「a,b」 を CSVを表として解析する 結果を捨てる\nこと。\n";
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "table.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                ExecutionContext.standard(new MemoryOutputSink()));

    assertTrue(result.successful(), result.diagnostics().toString());
    assertEquals(6, result.delimitedTextWorkUnits());
    assertEquals(3, result.arrayConstructionUnits());
  }

  @Test
  void fixedSeedGeneratedValidTablesRoundTripThroughBothParsers() throws Exception {
    var random = new Random(0x19B5B);
    for (char delimiter : new char[] {',', '\t'}) {
      for (int example = 0; example < 500; example++) {
        int rowCount = random.nextInt(6);
        int columnCount = rowCount == 0 ? 0 : random.nextInt(5) + 1;
        var table = new ArrayList<List<String>>();
        for (int row = 0; row < rowCount; row++) {
          var cells = new ArrayList<String>();
          for (int column = 0; column < columnCount; column++) {
            cells.add(randomCell(random));
          }
          table.add(cells);
        }
        String document = encodeValidTable(table, delimiter);
        var stack = stack(new StringValue(document));
        execute(
            delimiter == ',' ? "CSVを表として解析する" : "TSVを表として解析する",
            stack,
            new ExecutionBudget(SOURCE_PATH, () -> 0L));
        assertEquals(expectedTableValue(table), ((ResultValue) stack.getFirst()).value());
      }
    }
  }

  @Test
  void tracingChangesNoResultOrBudgetAndRevealsNoParserInput() {
    String source = "メインとは （--）\n" + "    「PARSE_INPUT_SECRET,a」 を CSVを表として解析する 結果を捨てる\n" + "こと。\n";
    byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
    ProgramRunResult normal =
        new ProgramRunner()
            .run("normal.bsb", bytes, ExecutionContext.standard(new MemoryOutputSink()));
    var events = new ArrayList<TraceEvent>();
    ProgramRunResult traced =
        new ProgramRunner()
            .run(
                "traced.bsb",
                bytes,
                new ExecutionContext(new MemoryOutputSink(), () -> 0L, events::add));

    assertTrue(normal.successful(), normal.diagnostics().toString());
    assertTrue(traced.successful(), traced.diagnostics().toString());
    assertEquals(normal.finalDataStack(), traced.finalDataStack());
    assertEquals(normal.arrayConstructionUnits(), traced.arrayConstructionUnits());
    assertEquals(normal.delimitedTextWorkUnits(), traced.delimitedTextWorkUnits());
    assertEquals(normal.outputBytes(), traced.outputBytes());
    String trace = TraceTsvFormatter.formatNestedArray(events);
    assertTrue(trace.contains("結果<配列<配列<文字列>>,区切りテキスト解析失敗>:<redacted>"));
    assertTrue(!trace.contains("PARSE_INPUT_SECRET"));
  }

  private static void assertResourceFailure(String input, DiagnosticCode code) throws Exception {
    var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    StringValue original = new StringValue(input);
    var stack = stack(original);
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("CSVを表として解析する", stack, budget));
    assertEquals(code, failure.diagnostic().code());
    assertSame(original, stack.getFirst());
    assertEquals(0, budget.arrayConstructionUnits());
    assertTrue(budget.delimitedTextWorkUnits() > 0);
  }

  private static RuntimeValue parseOnce(String input, String word) throws Exception {
    var stack = stack(new StringValue(input));
    execute(word, stack, new ExecutionBudget(SOURCE_PATH, () -> 0L));
    return stack.getFirst();
  }

  private static ArrayValue expectedTable(String encoded) {
    if (encoded.equals("[]")) {
      return new ArrayValue(ValueType.arrayOf(ValueType.STRING), List.of());
    }
    var rows = new ArrayList<RuntimeValue>();
    for (String encodedRow : encoded.split(";", -1)) {
      var cells = new ArrayList<RuntimeValue>();
      for (String encodedCell : encodedRow.split(",", -1)) {
        cells.add(
            new StringValue(
                encodedCell.equals("_")
                    ? ""
                    : new String(HexFormat.of().parseHex(encodedCell), StandardCharsets.UTF_8)));
      }
      rows.add(new ArrayValue(ValueType.STRING, cells));
    }
    return new ArrayValue(ValueType.arrayOf(ValueType.STRING), rows);
  }

  private static ArrayValue expectedTableValue(List<List<String>> table) {
    var rows = new ArrayList<RuntimeValue>();
    for (List<String> sourceRow : table) {
      rows.add(
          new ArrayValue(
              ValueType.STRING,
              sourceRow.stream().map(StringValue::new).map(RuntimeValue.class::cast).toList()));
    }
    return new ArrayValue(ValueType.arrayOf(ValueType.STRING), rows);
  }

  private static String randomCell(Random random) {
    String[] fragments = {"a", "", ",", "\t", "\"", "\r", "\n", "名", "😀", "\uFEFF"};
    var result = new StringBuilder();
    for (int index = 0; index < random.nextInt(5); index++) {
      result.append(fragments[random.nextInt(fragments.length)]);
    }
    return result.toString();
  }

  private static String encodeValidTable(List<List<String>> table, char delimiter) {
    var output = new StringBuilder();
    for (int row = 0; row < table.size(); row++) {
      for (int column = 0; column < table.get(row).size(); column++) {
        if (column > 0) output.append(delimiter);
        String cell = table.get(row).get(column);
        boolean quote =
            cell.indexOf(delimiter) >= 0
                || cell.indexOf('"') >= 0
                || cell.indexOf('\r') >= 0
                || cell.indexOf('\n') >= 0
                || row == 0 && column == 0 && cell.startsWith("\uFEFF");
        if (quote) output.append('"');
        output.append(quote ? cell.replace("\"", "\"\"") : cell);
        if (quote) output.append('"');
      }
      output.append("\r\n");
    }
    return output.toString();
  }

  private static String decodeHex(String encoded) {
    return encoded.equals("-")
        ? ""
        : new String(HexFormat.of().parseHex(encoded), StandardCharsets.UTF_8);
  }

  private static String parserName(String format) {
    return format.equals("csv") ? "CSVを表として解析する" : "TSVを表として解析する";
  }

  private static ArrayList<RuntimeValue> stack(RuntimeValue value) {
    return new ArrayList<>(List.of(value));
  }

  private static void execute(String name, ArrayList<RuntimeValue> stack, ExecutionBudget budget)
      throws Exception {
    new BuiltinExecutor(SOURCE_PATH, new BoundedOutput(SOURCE_PATH, new MemoryOutputSink()), budget)
        .execute(BuiltinDictionary.find(name).orElseThrow(), stack, SPAN);
  }
}
