package jp.bsb.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jp.bsb.stdlib.ArrayLimits;

/** comma固定CSVとtab固定TSVを同じ状態機械で厳密に解析します。 */
final class DelimitedTextCodec {
  private DelimitedTextCodec() {}

  /** 表の構造・セルを規範順に検査し、完全な出力を割り当てずにUTF-8長を測ります。 */
  static WritePlan planWrite(ArrayValue table, char delimiter) {
    Objects.requireNonNull(table, "table");
    requireDelimiter(delimiter);
    for (int rowIndex = 0; rowIndex < table.size(); rowIndex++) {
      ArrayValue row = (ArrayValue) table.get(rowIndex);
      if (row.size() == 0) {
        return WritePlan.failure(table, delimiter, WriteProblem.emptyRow(rowIndex + 1));
      }
    }
    if (table.size() > 0) {
      int expectedColumns = ((ArrayValue) table.get(0)).size();
      for (int rowIndex = 1; rowIndex < table.size(); rowIndex++) {
        int actualColumns = ((ArrayValue) table.get(rowIndex)).size();
        if (actualColumns != expectedColumns) {
          return WritePlan.failure(
              table,
              delimiter,
              WriteProblem.columnCountMismatch(rowIndex + 1, expectedColumns, actualColumns));
        }
      }
    }

    long inputUtf8Bytes = 0;
    long outputUtf8Bytes = 0;
    for (int rowIndex = 0; rowIndex < table.size(); rowIndex++) {
      ArrayValue row = (ArrayValue) table.get(rowIndex);
      for (int columnIndex = 0; columnIndex < row.size(); columnIndex++) {
        String cell = ((StringValue) row.get(columnIndex)).value();
        if (cell.indexOf('\0') >= 0) {
          return WritePlan.failure(
              table, delimiter, WriteProblem.nulCell(rowIndex + 1, columnIndex + 1));
        }
        long cellBytes = Utf8Length.measureUpTo(cell, Long.MAX_VALUE).bytes();
        inputUtf8Bytes = saturatedAdd(inputUtf8Bytes, cellBytes);
        outputUtf8Bytes = saturatedAdd(outputUtf8Bytes, cellBytes);
        boolean quote = requiresQuote(cell, delimiter, rowIndex, columnIndex);
        if (quote) {
          outputUtf8Bytes = saturatedAdd(outputUtf8Bytes, 2);
          outputUtf8Bytes = saturatedAdd(outputUtf8Bytes, countQuotes(cell));
        }
        if (columnIndex > 0) {
          outputUtf8Bytes = saturatedAdd(outputUtf8Bytes, 1);
        }
      }
      outputUtf8Bytes = saturatedAdd(outputUtf8Bytes, 2);
    }
    return WritePlan.success(table, delimiter, inputUtf8Bytes, outputUtf8Bytes);
  }

  /** 検査済み計画からBOMなし・CRLF終端の一意な区切りテキストを構築します。 */
  static String write(WritePlan plan) {
    Objects.requireNonNull(plan, "plan");
    if (plan.problem().isPresent()) {
      throw new IllegalArgumentException("a failed delimited write plan cannot be rendered");
    }
    if (plan.outputUtf8Bytes() > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("delimited output is too large to render");
    }
    var output = new StringBuilder((int) plan.outputUtf8Bytes());
    ArrayValue table = plan.table();
    for (int rowIndex = 0; rowIndex < table.size(); rowIndex++) {
      ArrayValue row = (ArrayValue) table.get(rowIndex);
      for (int columnIndex = 0; columnIndex < row.size(); columnIndex++) {
        if (columnIndex > 0) output.append(plan.delimiter());
        String cell = ((StringValue) row.get(columnIndex)).value();
        boolean quote = requiresQuote(cell, plan.delimiter(), rowIndex, columnIndex);
        if (quote) output.append('"');
        for (int index = 0; index < cell.length(); index++) {
          char character = cell.charAt(index);
          output.append(character);
          if (quote && character == '"') output.append('"');
        }
        if (quote) output.append('"');
      }
      output.append("\r\n");
    }
    return output.toString();
  }

  /** 入力を1回走査し、文法結果と飽和させない構造計数を返します。 */
  static ParseResult parse(String source, char delimiter) {
    Objects.requireNonNull(source, "source");
    requireDelimiter(delimiter);
    var cursor = new Cursor(source);
    if (!cursor.atEnd() && cursor.codePoint() == 0xFEFF) {
      cursor.consumeScalar();
    }
    if (cursor.atEnd()) {
      return ParseResult.success(List.of(), 0, 0, 0, 0);
    }

    var rows = new ArrayList<List<String>>();
    long rowCount = 0;
    long logicalCellCount = 0;
    long expectedColumns = -1;
    long firstOversizedRow = 0;
    long firstOversizedRowColumns = 0;
    boolean retainTable = true;
    while (!cursor.atEnd()) {
      RowResult row = parseRow(cursor, delimiter, expectedColumns, retainTable);
      if (row.failure().isPresent()) {
        return ParseResult.failure(row.failure().orElseThrow());
      }
      rowCount++;
      logicalCellCount = saturatedAdd(logicalCellCount, row.columnCount());
      if (expectedColumns < 0) {
        expectedColumns = row.columnCount();
      }
      if (firstOversizedRow == 0 && row.columnCount() > ArrayLimits.MAX_LENGTH) {
        firstOversizedRow = rowCount;
        firstOversizedRowColumns = row.columnCount();
      }
      if (retainTable
          && rowCount <= ArrayLimits.MAX_LENGTH
          && row.columnCount() <= ArrayLimits.MAX_LENGTH
          && logicalCellCount <= ArrayLimits.MAX_NESTED_LEAF_ELEMENTS) {
        rows.add(row.fields());
      } else {
        retainTable = false;
        rows.clear();
      }

      if (cursor.atEnd()) {
        break;
      }
      cursor.consumeLineBreak();
      if (cursor.atEnd()) {
        break;
      }
    }
    return ParseResult.success(
        rows, rowCount, firstOversizedRow, firstOversizedRowColumns, logicalCellCount);
  }

  private static RowResult parseRow(
      Cursor cursor, char delimiter, long expectedColumns, boolean retainFields) {
    var fields = new ArrayList<String>();
    long columnCount = 0;
    while (true) {
      long fieldNumber = columnCount + 1;
      if (expectedColumns >= 0 && fieldNumber > expectedColumns) {
        if (!cursor.atEnd() && cursor.codePoint() == 0) {
          return RowResult.failure(cursor.failure("nulCharacter"));
        }
        return RowResult.failure(cursor.failure("columnCountMismatch"));
      }

      FieldResult field = parseField(cursor, delimiter, retainFields);
      if (field.failure().isPresent()) {
        return RowResult.failure(field.failure().orElseThrow());
      }
      columnCount++;
      if (retainFields && columnCount <= ArrayLimits.MAX_LENGTH) {
        fields.add(field.value());
      }

      if (!cursor.atEnd() && cursor.codePoint() == delimiter) {
        cursor.consumeScalar();
        continue;
      }
      if (expectedColumns >= 0 && columnCount < expectedColumns) {
        return RowResult.failure(cursor.failure("columnCountMismatch"));
      }
      return RowResult.success(fields, columnCount);
    }
  }

  private static FieldResult parseField(Cursor cursor, char delimiter, boolean retainValue) {
    var value = retainValue ? new StringBuilder() : null;
    if (!cursor.atEnd() && cursor.codePoint() == '"') {
      cursor.consumeScalar();
      while (!cursor.atEnd()) {
        int codePoint = cursor.codePoint();
        if (codePoint == 0) {
          return FieldResult.failure(cursor.failure("nulCharacter"));
        }
        if (codePoint == '"') {
          cursor.consumeScalar();
          if (!cursor.atEnd() && cursor.codePoint() == '"') {
            if (value != null) value.append('"');
            cursor.consumeScalar();
            continue;
          }
          if (cursor.atEnd()
              || cursor.codePoint() == delimiter
              || cursor.codePoint() == '\r'
              || cursor.codePoint() == '\n') {
            return FieldResult.success(value == null ? "" : value.toString());
          }
          if (cursor.codePoint() == 0) {
            return FieldResult.failure(cursor.failure("nulCharacter"));
          }
          return FieldResult.failure(cursor.failure("unexpectedCharacterAfterQuote"));
        }
        if (codePoint == '\r' || codePoint == '\n') {
          if (value != null) cursor.appendAndConsumeLineBreak(value);
          else cursor.consumeLineBreak();
        } else {
          if (value != null) value.appendCodePoint(codePoint);
          cursor.consumeScalar();
        }
      }
      return FieldResult.failure(cursor.failure("unterminatedQuotedField"));
    }

    while (!cursor.atEnd()) {
      int codePoint = cursor.codePoint();
      if (codePoint == delimiter || codePoint == '\r' || codePoint == '\n') {
        break;
      }
      if (codePoint == 0) {
        return FieldResult.failure(cursor.failure("nulCharacter"));
      }
      if (codePoint == '"') {
        return FieldResult.failure(cursor.failure("unexpectedQuote"));
      }
      if (value != null) value.appendCodePoint(codePoint);
      cursor.consumeScalar();
    }
    return FieldResult.success(value == null ? "" : value.toString());
  }

  private static long saturatedAdd(long left, long right) {
    return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
  }

  private static void requireDelimiter(char delimiter) {
    if (delimiter != ',' && delimiter != '\t') {
      throw new IllegalArgumentException("delimiter must be comma or tab");
    }
  }

  private static boolean requiresQuote(String cell, char delimiter, int rowIndex, int columnIndex) {
    return cell.indexOf(delimiter) >= 0
        || cell.indexOf('"') >= 0
        || cell.indexOf('\r') >= 0
        || cell.indexOf('\n') >= 0
        || rowIndex == 0 && columnIndex == 0 && cell.startsWith("\uFEFF");
  }

  private static long countQuotes(String cell) {
    long result = 0;
    for (int index = 0; index < cell.length(); index++) {
      if (cell.charAt(index) == '"') result++;
    }
    return result;
  }

  /** 直列化前検査の結果です。成功時だけ長さと入力表を出力構築へ渡せます。 */
  record WritePlan(
      ArrayValue table,
      char delimiter,
      long inputUtf8Bytes,
      long outputUtf8Bytes,
      Optional<WriteProblem> problem) {
    WritePlan {
      Objects.requireNonNull(table, "table");
      requireDelimiter(delimiter);
      problem = Objects.requireNonNull(problem, "problem");
      if (inputUtf8Bytes < 0 || outputUtf8Bytes < 0) {
        throw new IllegalArgumentException("delimited write metrics must not be negative");
      }
    }

    static WritePlan success(
        ArrayValue table, char delimiter, long inputUtf8Bytes, long outputUtf8Bytes) {
      return new WritePlan(table, delimiter, inputUtf8Bytes, outputUtf8Bytes, Optional.empty());
    }

    static WritePlan failure(ArrayValue table, char delimiter, WriteProblem problem) {
      return new WritePlan(table, delimiter, 0, 0, Optional.of(problem));
    }
  }

  /** 内容を保持しない、直列化入力の閉じた構造問題です。 */
  record WriteProblem(String kind, int row, int column, int expectedCount, int actualCount) {
    private static final java.util.Set<String> KINDS =
        java.util.Set.of("emptyRow", "columnCountMismatch", "nulCharacter");

    WriteProblem {
      if (!KINDS.contains(kind) || row < 1 || column < 0 || expectedCount < 0 || actualCount < 0) {
        throw new IllegalArgumentException("invalid delimited write problem");
      }
    }

    static WriteProblem emptyRow(int row) {
      return new WriteProblem("emptyRow", row, 0, 0, 0);
    }

    static WriteProblem columnCountMismatch(int row, int expectedCount, int actualCount) {
      return new WriteProblem("columnCountMismatch", row, 0, expectedCount, actualCount);
    }

    static WriteProblem nulCell(int row, int column) {
      return new WriteProblem("nulCharacter", row, column, 0, 0);
    }
  }

  /** 解析成功の表・構造量、または回復可能な失敗の一方だけを保持します。 */
  record ParseResult(
      List<List<String>> rows,
      Optional<DelimitedTextParseFailureValue> failure,
      long rowCount,
      long firstOversizedRow,
      long firstOversizedRowColumns,
      long logicalCellCount) {
    ParseResult {
      rows = rows.stream().map(List::copyOf).toList();
      failure = Objects.requireNonNull(failure, "failure");
      if (rowCount < 0
          || firstOversizedRow < 0
          || firstOversizedRowColumns < 0
          || logicalCellCount < 0
          || failure.isPresent() && rowCount != 0) {
        throw new IllegalArgumentException("inconsistent delimited parse result");
      }
    }

    static ParseResult success(
        List<List<String>> rows,
        long rowCount,
        long firstOversizedRow,
        long firstOversizedRowColumns,
        long logicalCellCount) {
      return new ParseResult(
          rows,
          Optional.empty(),
          rowCount,
          firstOversizedRow,
          firstOversizedRowColumns,
          logicalCellCount);
    }

    static ParseResult failure(DelimitedTextParseFailureValue failure) {
      return new ParseResult(List.of(), Optional.of(failure), 0, 0, 0, 0);
    }
  }

  private record RowResult(
      List<String> fields, long columnCount, Optional<DelimitedTextParseFailureValue> failure) {
    static RowResult success(List<String> fields, long columnCount) {
      return new RowResult(List.copyOf(fields), columnCount, Optional.empty());
    }

    static RowResult failure(DelimitedTextParseFailureValue failure) {
      return new RowResult(List.of(), 0, Optional.of(failure));
    }
  }

  private record FieldResult(String value, Optional<DelimitedTextParseFailureValue> failure) {
    static FieldResult success(String value) {
      return new FieldResult(value, Optional.empty());
    }

    static FieldResult failure(DelimitedTextParseFailureValue failure) {
      return new FieldResult("", Optional.of(failure));
    }
  }

  /** UTF-16 indexとUTF-8・物理行・scalar列を同時に進めます。 */
  private static final class Cursor {
    private final String source;
    private int index;
    private long utf8Offset;
    private int line = 1;
    private int column = 1;

    private Cursor(String source) {
      this.source = source;
    }

    private boolean atEnd() {
      return index == source.length();
    }

    private int codePoint() {
      return source.codePointAt(index);
    }

    private void consumeScalar() {
      int codePoint = codePoint();
      index += Character.charCount(codePoint);
      utf8Offset += utf8Width(codePoint);
      column++;
    }

    private void consumeLineBreak() {
      int first = codePoint();
      if (first != '\r' && first != '\n') {
        throw new IllegalStateException("cursor is not at a line break");
      }
      index++;
      utf8Offset++;
      if (first == '\r' && !atEnd() && source.charAt(index) == '\n') {
        index++;
        utf8Offset++;
      }
      line++;
      column = 1;
    }

    private void appendAndConsumeLineBreak(StringBuilder output) {
      int start = index;
      consumeLineBreak();
      output.append(source, start, index);
    }

    private DelimitedTextParseFailureValue failure(String kind) {
      return new DelimitedTextParseFailureValue(kind, utf8Offset, line, column);
    }

    private static int utf8Width(int codePoint) {
      if (codePoint <= 0x7F) return 1;
      if (codePoint <= 0x7FF) return 2;
      return codePoint <= 0xFFFF ? 3 : 4;
    }
  }
}
