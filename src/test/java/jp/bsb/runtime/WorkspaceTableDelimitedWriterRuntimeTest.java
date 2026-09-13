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
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import jp.bsb.conformance.WorkspaceTableConformanceData;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

/** 独立期待に対するCSV/TSV直列化、診断、資源、原子性を検証します。 */
class WorkspaceTableDelimitedWriterRuntimeTest {
  private static final String SOURCE_PATH = "table.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void matchesEveryIndependentWriterVectorAndRoundTrips() throws Exception {
    for (var expected : WorkspaceTableConformanceData.loadDelimitedWrites()) {
      ArrayValue table = decodeTable(expected.value("table"));
      var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
      var stack = stack(table);

      execute(writerName(expected.value("format")), stack, budget);

      String output = ((StringValue) stack.getFirst()).value();
      assertEquals(decodeHex(expected.value("output_hex")), output, expected.value("variant"));
      long inputBytes = tableInputBytes(table);
      long outputBytes = output.getBytes(StandardCharsets.UTF_8).length;
      assertEquals(inputBytes + outputBytes, budget.delimitedTextWorkUnits());
      assertEquals(0, budget.arrayConstructionUnits());

      var parseStack = stack(new StringValue(output));
      execute(
          parserName(expected.value("format")),
          parseStack,
          new ExecutionBudget(SOURCE_PATH, () -> 0L));
      assertEquals(table, ((ResultValue) parseStack.getFirst()).value(), expected.value("variant"));
    }
  }

  @Test
  void reportsWriterDiagnosticsWithOnlyNormativeFieldsAndPreservesTheStack() throws Exception {
    for (var expected : WorkspaceTableConformanceData.loadDelimitedDiagnostics().subList(0, 4)) {
      ArrayValue table = diagnosticTable(expected.value("variant"));
      var budget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
      var stack = stack(table);

      RuntimeFailure failure =
          assertThrows(RuntimeFailure.class, () -> execute(expected.value("word"), stack, budget));

      Diagnostic diagnostic = failure.diagnostic();
      assertEquals(DiagnosticCode.valueOf(expected.value("code")), diagnostic.code());
      assertSame(table, stack.getFirst());
      assertEquals(expectedFields(expected.value("fields")), observableFields(diagnostic));
      assertEquals(0, budget.arrayConstructionUnits());
      assertEquals(0, budget.delimitedTextWorkUnits());
      assertDiagnosticHasNoCellContent(diagnostic, expected.value("forbidden_markers"));
    }
  }

  @Test
  void appliesEmptyRaggedAndNulPriorityBeforeMeasuring() throws Exception {
    ArrayValue emptyAfterRagged =
        table(List.of(List.of("a", "b"), List.of("NUL\0SECRET"), List.of()));
    RuntimeFailure emptyFailure = writeFailure(emptyAfterRagged, "表をCSVに変換する");
    assertEquals(DiagnosticCode.E_DELIMITED_TEXT_EMPTY_ROW, emptyFailure.diagnostic().code());
    assertEquals("3", emptyFailure.diagnostic().fields().get("row"));

    ArrayValue raggedBeforeNul = table(List.of(List.of("a", "b"), List.of("NUL\0SECRET")));
    RuntimeFailure raggedFailure = writeFailure(raggedBeforeNul, "表をCSVに変換する");
    assertEquals(
        DiagnosticCode.E_DELIMITED_TEXT_COLUMN_COUNT_MISMATCH, raggedFailure.diagnostic().code());
    assertEquals("2", raggedFailure.diagnostic().fields().get("row"));
  }

  @Test
  void acceptsTheExactOutputBoundaryAndRejectsOneByteOverWithoutChargingWork() throws Exception {
    String exactCell = "a".repeat(StringLimits.MAX_UTF8_BYTES - 2);
    ArrayValue exactTable = table(List.of(List.of(exactCell)));
    var exactBudget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    var exactStack = stack(exactTable);
    execute("表をCSVに変換する", exactStack, exactBudget);
    String exact = ((StringValue) exactStack.getFirst()).value();
    assertEquals(StringLimits.MAX_UTF8_BYTES, exact.getBytes(StandardCharsets.UTF_8).length);
    assertTrue(exact.endsWith("\r\n"));
    assertEquals(33_554_430, exactBudget.delimitedTextWorkUnits());

    String overCell = "a".repeat(StringLimits.MAX_UTF8_BYTES - 1);
    ArrayValue overTable = table(List.of(List.of(overCell)));
    var overBudget = new ExecutionBudget(SOURCE_PATH, () -> 0L);
    var overStack = stack(overTable);
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("表をCSVに変換する", overStack, overBudget));
    assertEquals(DiagnosticCode.E_DELIMITED_TEXT_OUTPUT_LIMIT, failure.diagnostic().code());
    assertEquals(
        Long.toString(StringLimits.MAX_UTF8_BYTES + 1L),
        failure.diagnostic().observed().orElseThrow());
    assertSame(overTable, overStack.getFirst());
    assertEquals(0, overBudget.delimitedTextWorkUnits());
  }

  @Test
  void workLimitIsAtomicAndArrayBudgetsAreUnaffected() throws Exception {
    ArrayValue table = table(List.of(List.of("a")));
    var exact =
        ExecutionBudget.withDelimitedTextWork(
            SOURCE_PATH, () -> 0L, 77, DelimitedTextLimits.MAX_WORK_UNITS - 4);
    var exactStack = stack(table);
    execute("表をCSVに変換する", exactStack, exact);
    assertEquals(DelimitedTextLimits.MAX_WORK_UNITS, exact.delimitedTextWorkUnits());
    assertEquals(77, exact.arrayConstructionUnits());

    var rejected =
        ExecutionBudget.withDelimitedTextWork(
            SOURCE_PATH, () -> 0L, 77, DelimitedTextLimits.MAX_WORK_UNITS - 3);
    var rejectedStack = stack(table);
    RuntimeFailure failure =
        assertThrows(RuntimeFailure.class, () -> execute("表をCSVに変換する", rejectedStack, rejected));
    assertEquals(DiagnosticCode.E_DELIMITED_TEXT_WORK_LIMIT, failure.diagnostic().code());
    assertSame(table, rejectedStack.getFirst());
    assertEquals(DelimitedTextLimits.MAX_WORK_UNITS - 3, rejected.delimitedTextWorkUnits());
    assertEquals(77, rejected.arrayConstructionUnits());
  }

  @Test
  void fixedSeedTablesRoundTripThroughBothWritersAndParsers() throws Exception {
    var random = new Random(0x19B5B17);
    for (String format : List.of("csv", "tsv")) {
      for (int example = 0; example < 500; example++) {
        int rowCount = random.nextInt(6);
        int columnCount = rowCount == 0 ? 0 : random.nextInt(5) + 1;
        var source = new ArrayList<List<String>>();
        for (int row = 0; row < rowCount; row++) {
          var cells = new ArrayList<String>();
          for (int column = 0; column < columnCount; column++) {
            cells.add(randomCell(random));
          }
          source.add(cells);
        }
        ArrayValue original = table(source);
        var writeStack = stack(original);
        execute(writerName(format), writeStack, new ExecutionBudget(SOURCE_PATH, () -> 0L));
        var parseStack = stack(writeStack.getFirst());
        execute(parserName(format), parseStack, new ExecutionBudget(SOURCE_PATH, () -> 0L));
        assertEquals(original, ((ResultValue) parseStack.getFirst()).value());
      }
    }
  }

  @Test
  void localeTimezoneAndOperatingSystemLineSeparatorCannotChangeOutput() throws Exception {
    ArrayValue table = table(List.of(List.of("\uFEFF名", "a\nb", "😀")));
    String baseline = writeOnce(table, "表をTSVに変換する");
    Locale previousLocale = Locale.getDefault();
    TimeZone previousZone = TimeZone.getDefault();
    String previousSeparator = System.getProperty("line.separator");
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
      System.setProperty("line.separator", "\n");
      assertEquals(baseline, writeOnce(table, "表をTSVに変換する"));
      assertTrue(baseline.endsWith("\r\n"));
    } finally {
      Locale.setDefault(previousLocale);
      TimeZone.setDefault(previousZone);
      if (previousSeparator == null) System.clearProperty("line.separator");
      else System.setProperty("line.separator", previousSeparator);
    }
  }

  @Test
  void writerDiagnosticContextRedactsTheWholeInputTable() {
    String source =
        "メインとは （--）\n"
            + "    【【「safe」、「RAGGED_CELL_SECRET」】、【「x」】】 を 表をCSVに変換する 一行表示する\n"
            + "こと。\n";
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                "writer.bsb",
                source.getBytes(StandardCharsets.UTF_8),
                ExecutionContext.standard(new MemoryOutputSink()));

    assertEquals(
        DiagnosticCode.E_DELIMITED_TEXT_COLUMN_COUNT_MISMATCH,
        result.diagnostics().getFirst().code());
    assertEquals(10, result.exitCode());
    Diagnostic diagnostic = result.diagnostics().getFirst();
    assertEquals(DiagnosticCode.E_DELIMITED_TEXT_COLUMN_COUNT_MISMATCH, diagnostic.code());
    assertEquals("[配列<配列<文字列>>:<redacted>]", diagnostic.fields().get("dataStack"));
    assertTrue(!diagnostic.toString().contains("RAGGED_CELL_SECRET"));
    assertEquals(0, result.delimitedTextWorkUnits());
  }

  private static RuntimeFailure writeFailure(ArrayValue table, String word) throws Exception {
    var stack = stack(table);
    return assertThrows(
        RuntimeFailure.class,
        () -> execute(word, stack, new ExecutionBudget(SOURCE_PATH, () -> 0L)));
  }

  private static String writeOnce(ArrayValue table, String word) throws Exception {
    var stack = stack(table);
    execute(word, stack, new ExecutionBudget(SOURCE_PATH, () -> 0L));
    return ((StringValue) stack.getFirst()).value();
  }

  private static ArrayValue diagnosticTable(String variant) {
    return switch (variant) {
      case "second-empty-row" -> table(List.of(List.of("a"), List.of()));
      case "second-short-row" -> table(List.of(List.of("a", "RAGGED_CELL_SECRET"), List.of("b")));
      case "second-row-first-cell" -> table(List.of(List.of("a"), List.of("NUL_CELL_SECRET\0")));
      case "one-over-output" ->
          table(List.of(List.of("a".repeat(StringLimits.MAX_UTF8_BYTES - 1))));
      default -> throw new IllegalArgumentException("unknown diagnostic variant: " + variant);
    };
  }

  private static Map<String, String> expectedFields(String encoded) {
    var fields = new java.util.LinkedHashMap<String, String>();
    for (String pair : encoded.split(";")) {
      String[] parts = pair.split("=", 2);
      fields.put(parts[0], parts[1]);
    }
    return fields;
  }

  private static Map<String, String> observableFields(Diagnostic diagnostic) {
    var fields = new java.util.LinkedHashMap<>(diagnostic.fields());
    diagnostic.limitName().ifPresent(value -> fields.put("limitName", value));
    diagnostic.limit().ifPresent(value -> fields.put("limit", value));
    diagnostic.observed().ifPresent(value -> fields.put("observed", value));
    return fields;
  }

  private static void assertDiagnosticHasNoCellContent(Diagnostic diagnostic, String marker) {
    String rendered = diagnostic.toString();
    assertTrue(!rendered.contains(marker));
    assertTrue(!rendered.contains("OUTPUT_PREFIX_SECRET"));
  }

  private static ArrayValue decodeTable(String encoded) {
    if (encoded.equals("[]")) return table(List.of());
    var rows = new ArrayList<List<String>>();
    for (String encodedRow : encoded.split(";", -1)) {
      var cells = new ArrayList<String>();
      for (String encodedCell : encodedRow.split(",", -1)) {
        cells.add(
            encodedCell.equals("_")
                ? ""
                : new String(HexFormat.of().parseHex(encodedCell), StandardCharsets.UTF_8));
      }
      rows.add(cells);
    }
    return table(rows);
  }

  private static ArrayValue table(List<List<String>> source) {
    var rows = new ArrayList<RuntimeValue>();
    for (List<String> sourceRow : source) {
      rows.add(
          new ArrayValue(
              ValueType.STRING,
              sourceRow.stream().map(StringValue::new).map(RuntimeValue.class::cast).toList()));
    }
    return new ArrayValue(ValueType.arrayOf(ValueType.STRING), rows);
  }

  private static long tableInputBytes(ArrayValue table) {
    long result = 0;
    for (RuntimeValue rowValue : table.elements()) {
      for (RuntimeValue cellValue : ((ArrayValue) rowValue).elements()) {
        result += ((StringValue) cellValue).value().getBytes(StandardCharsets.UTF_8).length;
      }
    }
    return result;
  }

  private static String randomCell(Random random) {
    String[] fragments = {"a", "", ",", "\t", "\"", "\r", "\n", "名", "😀", "\uFEFF"};
    var result = new StringBuilder();
    for (int index = 0; index < random.nextInt(5); index++) {
      result.append(fragments[random.nextInt(fragments.length)]);
    }
    return result.toString();
  }

  private static String decodeHex(String encoded) {
    return encoded.equals("-")
        ? ""
        : new String(HexFormat.of().parseHex(encoded), StandardCharsets.UTF_8);
  }

  private static String writerName(String format) {
    return format.equals("csv") ? "表をCSVに変換する" : "表をTSVに変換する";
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
