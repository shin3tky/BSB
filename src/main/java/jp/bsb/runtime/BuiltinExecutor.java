package jp.bsb.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jp.bsb.adapter.IcuUnicodeAdapter;
import jp.bsb.diagnostics.Diagnostic;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.DiagnosticStage;
import jp.bsb.diagnostics.Severity;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.ir.IrStackEffect;
import jp.bsb.ir.LogicalConnectionReference;
import jp.bsb.ir.WorkspaceReference;
import jp.bsb.json.JsonArray;
import jp.bsb.json.JsonBoolean;
import jp.bsb.json.JsonCodec;
import jp.bsb.json.JsonDecimal;
import jp.bsb.json.JsonInteger;
import jp.bsb.json.JsonKind;
import jp.bsb.json.JsonLimits;
import jp.bsb.json.JsonMember;
import jp.bsb.json.JsonNull;
import jp.bsb.json.JsonObject;
import jp.bsb.json.JsonParseException;
import jp.bsb.json.JsonString;
import jp.bsb.json.JsonValue;
import jp.bsb.json.JsonWriteException;
import jp.bsb.numeric.DecimalLexemeAnalyzer;
import jp.bsb.numeric.DecimalLexemeAnalyzer.Valid;
import jp.bsb.regex.RegexMatch;
import jp.bsb.regex.RegexMatchCursor;
import jp.bsb.stdlib.ArrayLimits;
import jp.bsb.stdlib.ArrayType;
import jp.bsb.stdlib.BuiltinWord;
import jp.bsb.stdlib.ResultType;
import jp.bsb.stdlib.ScalarType;
import jp.bsb.stdlib.ValueType;

/** 共有辞書の処理識別子に従って、ホスト入出力の入出力・実行環境操作までの組み込み単語を実行します。 */
final class BuiltinExecutor {
  private static final int DEFAULT_DIVISION_PRECISION = 28;
  private static final int MINIMUM_DIVISION_PRECISION = 1;
  private static final int MAXIMUM_DIVISION_PRECISION = 4_096;
  private static final int MAXIMUM_NUMERIC_TEXT_DIGITS = 4_096;
  private static final HexFormat UPPER_HEX = HexFormat.of().withUpperCase();
  private static final ResultType RECOVERABLE_JSON_RESULT =
      ValueType.resultOf(ValueType.JSON, ValueType.JSON_PARSE_FAILURE);
  private static final ResultType UTF8_DECODE_RESULT =
      ValueType.resultOf(ValueType.STRING, ValueType.UTF8_DECODE_FAILURE);
  private static final ResultType BASE64_DECODE_RESULT =
      ValueType.resultOf(ValueType.BYTE_SEQUENCE, ValueType.BASE64_DECODE_FAILURE);
  private static final ResultType HTTP_SEND_RESULT =
      ValueType.resultOf(ValueType.HTTP_RESPONSE, ValueType.HTTP_SEND_FAILURE);
  private static final ResultType FILE_READ_RESULT =
      ValueType.resultOf(ValueType.BYTE_SEQUENCE, ValueType.FILE_READ_FAILURE);
  private static final ResultType FILE_WRITE_RESULT =
      ValueType.resultOf(ValueType.INTEGER, ValueType.FILE_WRITE_FAILURE);
  private static final ResultType DELIMITED_TABLE_RESULT =
      ValueType.resultOf(
          ValueType.arrayOf(ValueType.arrayOf(ValueType.STRING)),
          ValueType.DELIMITED_TEXT_PARSE_FAILURE);
  private static final ResultType JSON_SHAPE_RESULT =
      ValueType.resultOf(ValueType.JSON, ValueType.arrayOf(ValueType.JSON_SHAPE_FAILURE));
  private static final java.util.Set<String> WORKSPACE_INVALID_REASONS =
      java.util.Set.of(
          "invalidReference", "invalidPolicy", "inconsistentWorkspace", "inconsistentMapping");

  private final String sourcePath;
  private final BoundedOutput output;
  private final BoundedOutput errorOutput;
  private final ExecutionBudget budget;
  private final BoundedInput input;
  private final ExecutionEnvironment environment;
  private String effect = "";
  private long waitedMilliseconds;
  private long lastMonotonicMilliseconds = -1;
  private final Map<String, Long> lastHttpAttemptStartNanos = new java.util.HashMap<>();

  BuiltinExecutor(String sourcePath, BoundedOutput output, ExecutionBudget budget) {
    this.sourcePath = sourcePath;
    this.output = output;
    this.errorOutput =
        BoundedOutput.error(sourcePath, ExecutionEnvironment.builder(() -> 0L).build());
    this.budget = budget;
    environment = ExecutionEnvironment.builder(() -> 0L).build();
    input = new BoundedInput(sourcePath, environment, budget);
  }

  BuiltinExecutor(
      String sourcePath,
      BoundedOutput output,
      ExecutionBudget budget,
      ExecutionEnvironment environment) {
    this.sourcePath = sourcePath;
    this.output = output;
    this.errorOutput = BoundedOutput.error(sourcePath, environment);
    this.budget = budget;
    this.environment = environment;
    input = new BoundedInput(sourcePath, environment, budget);
  }

  byte[] execute(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    return execute(word, stack, span, Optional.empty(), Optional.empty());
  }

  byte[] execute(
      BuiltinWord word,
      ArrayList<RuntimeValue> stack,
      SourceSpan span,
      java.util.Optional<IrStackEffect> stackEffect)
      throws RuntimeFailure {
    return execute(word, stack, span, stackEffect, Optional.empty());
  }

  byte[] execute(
      BuiltinWord word,
      ArrayList<RuntimeValue> stack,
      SourceSpan span,
      Optional<IrStackEffect> stackEffect,
      Optional<LogicalConnectionReference> logicalConnection)
      throws RuntimeFailure {
    return execute(word, stack, span, stackEffect, logicalConnection, Optional.empty());
  }

  byte[] execute(
      BuiltinWord word,
      ArrayList<RuntimeValue> stack,
      SourceSpan span,
      Optional<IrStackEffect> stackEffect,
      Optional<LogicalConnectionReference> logicalConnection,
      Optional<WorkspaceReference> workspace)
      throws RuntimeFailure {
    effect = "";
    return switch (word.operation()) {
      case ADD -> arithmetic(word, stack, span, Arithmetic.ADD);
      case SUBTRACT -> arithmetic(word, stack, span, Arithmetic.SUBTRACT);
      case MULTIPLY -> arithmetic(word, stack, span, Arithmetic.MULTIPLY);
      case EQUALS -> equals(word, stack, span);
      case LESS_THAN -> lessThan(stack);
      case INTEGER_QUOTIENT -> integerDivision(word, stack, span, IntegerDivisionResult.QUOTIENT);
      case INTEGER_REMAINDER -> integerDivision(word, stack, span, IntegerDivisionResult.REMAINDER);
      case INTEGER_QUOTIENT_AND_REMAINDER ->
          integerDivision(word, stack, span, IntegerDivisionResult.BOTH);
      case ABSOLUTE -> absolute(stack);
      case MINIMUM -> extremum(stack, true);
      case MAXIMUM -> extremum(stack, false);
      case DECIMAL_DIVIDE -> decimalDivide(word, stack, span);
      case PRECISION_DECIMAL_DIVIDE -> precisionDecimalDivide(word, stack, span);
      case INTEGER_TO_DECIMAL -> integerToDecimal(stack);
      case DECIMAL_TO_INTEGER -> decimalToInteger(word, stack, span);
      case ROUND_TO_INTEGER -> roundToInteger(word, stack, span);
      case ROUNDING_MODE_VALUE ->
          throw new IllegalStateException("ROUNDING_MODE_VALUE calls must be lowered to PushConst");
      case STRING_CONCAT -> concatenate(word, stack, span);
      case STRING_COMPARE -> compareStrings(stack);
      case STRING_TO_UPPER_CASE -> mapStringCase(word, stack, span, true);
      case STRING_TO_LOWER_CASE -> mapStringCase(word, stack, span, false);
      case STRING_CASE_INSENSITIVE_COMPARE -> compareStringsIgnoringCase(stack);
      case STRING_TRIM -> trimString(stack);
      case GRAPHEME_LENGTH -> stringLength(stack, true);
      case GRAPHEME_GET -> stringGet(word, stack, span, true);
      case GRAPHEME_SLICE -> stringSlice(word, stack, span, true);
      case GRAPHEME_FIND -> stringFind(stack, true);
      case CODE_POINT_LENGTH -> stringLength(stack, false);
      case CODE_POINT_GET -> stringGet(word, stack, span, false);
      case CODE_POINT_SLICE -> stringSlice(word, stack, span, false);
      case CODE_POINT_FIND -> stringFind(stack, false);
      case STRING_REPLACE -> replaceString(word, stack, span);
      case STRING_SPLIT -> splitString(word, stack, span);
      case INTEGER_TO_STRING -> numericToString(stack);
      case DECIMAL_TO_STRING -> numericToString(stack);
      case STRING_TO_INTEGER -> stringToInteger(word, stack, span);
      case STRING_TO_DECIMAL -> stringToDecimal(word, stack, span);
      case REGEX_FULL_MATCH -> regexPredicate(word, stack, span, true);
      case REGEX_CONTAINS -> regexPredicate(word, stack, span, false);
      case REGEX_FIRST -> regexFirst(word, stack, span);
      case REGEX_NAMED_GROUP -> regexNamedGroup(word, stack, span);
      case REGEX_REPLACE -> regexReplace(word, stack, span);
      case REGEX_SPLIT -> regexSplit(word, stack, span);
      case EMPTY_ARRAY ->
          throw new IllegalStateException("EMPTY_ARRAY calls must be lowered to BuildArray");
      case ARRAY_LENGTH -> arrayLength(stack, span);
      case ARRAY_GET -> arrayGet(stack, span);
      case ARRAY_SLICE -> arraySlice(stack, span);
      case ARRAY_REPLACE -> arrayReplace(stack, span);
      case ARRAY_APPEND -> arrayAppend(stack, span);
      case ARRAY_CONCAT -> arrayConcat(stack, span);
      case ARRAY_PREPEND -> arrayPrepend(stack, span);
      case ARRAY_REVERSE -> arrayReverse(stack, span);
      case ARRAY_CONTAINS -> arraySearch(word, stack, span, true);
      case ARRAY_FIND -> arraySearch(word, stack, span, false);
      case ARRAY_IS_EMPTY -> arrayIsEmpty(stack, span);
      case ARRAY_FIRST_OPTIONAL -> arrayEdgeOptional(stack, span, true);
      case ARRAY_LAST_OPTIONAL -> arrayEdgeOptional(stack, span, false);
      case ARRAY_DELETE_FIRST -> arrayDeleteEdge(stack, span, true);
      case ARRAY_DELETE_LAST -> arrayDeleteEdge(stack, span, false);
      case ARRAY_DELETE_RANGE -> arrayDeleteRange(stack, span);
      case ARRAY_COUNT -> arrayCount(word, stack, span);
      case ARRAY_INSERT -> arrayInsert(stack, span);
      case ARRAY_DELETE_AT -> arrayDeleteAt(stack, span);
      case ARRAY_FIND_FROM -> arrayFindFrom(word, stack, span);
      case ARRAY_UNIQUE -> arrayUnique(word, stack, span);
      case ARRAY_REPEAT_VALUE -> arrayRepeatValue(stack, span);
      case ARRAY_GET_OPTIONAL -> arrayGetOptional(stack, span);
      case ARRAY_COLUMN_OPTIONAL -> arrayColumnOptional(stack, span);
      case DISPLAY -> display(word, stack, span, false, output);
      case DISPLAY_LINE -> display(word, stack, span, true, output);
      case NEWLINE -> newline(word, span, output);
      case ERROR_DISPLAY -> display(word, stack, span, false, errorOutput);
      case ERROR_DISPLAY_LINE -> display(word, stack, span, true, errorOutput);
      case ERROR_NEWLINE -> newline(word, span, errorOutput);
      case PROGRAM_EXIT -> programExit(word, stack, span);
      case READ_LINE -> readLine(word, stack, span);
      case INPUT_IS_LINE -> inputPredicate(word, stack, span, InputResultValue.State.LINE);
      case INPUT_IS_END -> inputPredicate(word, stack, span, InputResultValue.State.END);
      case INPUT_IS_CANCEL -> inputPredicate(word, stack, span, InputResultValue.State.CANCEL);
      case INPUT_TAKE_LINE -> inputTakeLine(word, stack, span);
      case INPUT_DROP -> inputDrop(stack);
      case PROCESS_ARGUMENTS -> processArguments(word, stack, span);
      case PROGRAM_NAME -> programIdentity(word, stack, span, false);
      case PROGRAM_LOCATION -> programIdentity(word, stack, span, true);
      case SLEEP -> sleep(word, stack, span);
      case MONOTONIC_MILLISECONDS -> monotonicMilliseconds(word, stack, span);
      case WALL_TIME -> wallTime(word, stack, span);
      case DATE_TIME_TO_STRING -> dateTimeToString(stack);
      case JSON_NULL -> jsonConstant(word, stack, span, JsonNull.INSTANCE, "null");
      case JSON_EMPTY_ARRAY ->
          jsonConstant(word, stack, span, new JsonArray(List.of()), "emptyArray");
      case JSON_EMPTY_OBJECT ->
          jsonConstant(word, stack, span, new JsonObject(List.of()), "emptyObject");
      case JSON_PARSE -> jsonParse(word, stack, span);
      case JSON_SERIALIZE -> jsonSerialize(word, stack, span);
      case BOOLEAN_TO_JSON -> booleanToJson(stack, span);
      case INTEGER_TO_JSON -> integerToJson(stack, span);
      case DECIMAL_TO_JSON -> decimalToJson(stack, span);
      case STRING_TO_JSON -> stringToJson(word, stack, span);
      case JSON_TO_BOOLEAN -> jsonToBoolean(word, stack, span);
      case JSON_TO_INTEGER -> jsonToInteger(word, stack, span);
      case JSON_TO_DECIMAL -> jsonToDecimal(word, stack, span);
      case JSON_TO_STRING -> jsonToString(word, stack, span);
      case JSON_IS_NULL -> jsonPredicate(word, stack, span, JsonKind.NULL);
      case JSON_IS_BOOLEAN -> jsonPredicate(word, stack, span, JsonKind.BOOLEAN);
      case JSON_IS_INTEGER -> jsonPredicate(word, stack, span, JsonKind.INTEGER);
      case JSON_IS_DECIMAL -> jsonPredicate(word, stack, span, JsonKind.DECIMAL);
      case JSON_IS_STRING -> jsonPredicate(word, stack, span, JsonKind.STRING);
      case JSON_IS_ARRAY -> jsonPredicate(word, stack, span, JsonKind.ARRAY);
      case JSON_IS_OBJECT -> jsonPredicate(word, stack, span, JsonKind.OBJECT);
      case JSON_TO_ARRAY -> jsonToArray(word, stack, span);
      case ARRAY_TO_JSON -> arrayToJson(stack, span);
      case JSON_ARRAY_LENGTH -> jsonArrayLength(word, stack, span);
      case JSON_ARRAY_GET -> jsonArrayGet(word, stack, span);
      case JSON_ARRAY_SLICE -> jsonArraySlice(word, stack, span);
      case JSON_ARRAY_REPLACE -> jsonArrayReplace(word, stack, span);
      case JSON_ARRAY_APPEND -> jsonArrayAppend(word, stack, span);
      case JSON_OBJECT_SIZE -> jsonObjectSize(word, stack, span);
      case JSON_OBJECT_KEYS -> jsonObjectKeys(word, stack, span);
      case JSON_OBJECT_CONTAINS_KEY -> jsonObjectContainsKey(word, stack, span);
      case JSON_OBJECT_GET_REQUIRED -> jsonObjectGetRequired(word, stack, span);
      case JSON_OBJECT_SET -> jsonObjectSet(word, stack, span);
      case JSON_OBJECT_DELETE -> jsonObjectDelete(word, stack, span);
      case JSON_POINTER_GET_OPTIONAL -> jsonPointerGetOptional(word, stack, span);
      case JSON_OBJECT_BUILD -> jsonObjectBuild(word, stack, span);
      case OPTIONAL_WRAP -> optionalWrap(stack);
      case OPTIONAL_PREDICATE -> optionalPredicate(word, stack, span);
      case OPTIONAL_UNWRAP -> optionalUnwrap(word, stack, span);
      case OPTIONAL_DROP -> optionalDrop(stack);
      case JSON_OBJECT_GET_OPTIONAL -> jsonObjectGetOptional(word, stack, span);
      case RESULT_SUCCESS_WRAP -> resultWrap(stack, stackEffect, true);
      case RESULT_FAILURE_WRAP -> resultWrap(stack, stackEffect, false);
      case RESULT_IS_SUCCESS -> resultPredicate(word, stack, span, true);
      case RESULT_IS_FAILURE -> resultPredicate(word, stack, span, false);
      case RESULT_SUCCESS_UNWRAP -> resultUnwrap(word, stack, span, true);
      case RESULT_FAILURE_UNWRAP -> resultUnwrap(word, stack, span, false);
      case RESULT_DROP -> resultDrop(stack);
      case JSON_PARSE_RESULT -> jsonParseResult(word, stack, span);
      case JSON_PARSE_FAILURE_KIND -> jsonParseFailureKind(stack);
      case JSON_PARSE_FAILURE_OFFSET -> jsonParseFailureOffset(stack);
      case JSON_PARSE_FAILURE_LINE -> jsonParseFailureLine(stack);
      case JSON_PARSE_FAILURE_COLUMN -> jsonParseFailureColumn(stack);
      case LOGICAL_CONNECTION_CHECK ->
          logicalConnectionCheck(
              word,
              span,
              logicalConnection.orElseThrow(
                  () -> new IllegalStateException("logical connection reference is missing")));
      case EMPTY_BYTE_SEQUENCE -> emptyByteSequence(word, stack, span);
      case BYTE_SEQUENCE_LENGTH -> byteSequenceLength(word, stack, span);
      case BYTE_SEQUENCE_SLICE -> byteSequenceSlice(word, stack, span);
      case STRING_TO_UTF8_BYTES -> stringToUtf8Bytes(word, stack, span);
      case UTF8_BYTES_TO_STRING_RESULT -> utf8BytesToStringResult(word, stack, span);
      case UTF8_DECODE_FAILURE_KIND -> utf8DecodeFailureKind(stack);
      case UTF8_DECODE_FAILURE_OFFSET -> utf8DecodeFailureOffset(stack);
      case BYTE_SEQUENCE_TO_BASE64 -> byteSequenceToBase64(word, stack, span);
      case BASE64_TO_BYTE_SEQUENCE_RESULT -> base64ToByteSequenceResult(word, stack, span);
      case BASE64_DECODE_FAILURE_KIND -> base64DecodeFailureKind(stack);
      case BASE64_DECODE_FAILURE_OFFSET -> base64DecodeFailureOffset(stack);
      case EMPTY_HTTP_REQUEST -> emptyHttpRequest(word, stack, span);
      case HTTP_REQUEST_SET_PATH -> httpRequestSetPath(word, stack, span);
      case HTTP_REQUEST_ADD_QUERY -> httpRequestAddQuery(word, stack, span);
      case HTTP_REQUEST_SET_HEADER -> httpRequestSetHeader(word, stack, span);
      case HTTP_REQUEST_SET_JSON_BODY -> httpRequestSetJsonBody(word, stack, span);
      case HTTP_REQUEST_SET_STRING_BODY -> httpRequestSetStringBody(word, stack, span);
      case HTTP_REQUEST_SET_BYTE_BODY -> httpRequestSetByteBody(stack);
      case HTTP_SEND ->
          httpSend(
              word,
              stack,
              span,
              logicalConnection.orElseThrow(
                  () -> new IllegalStateException("logical connection reference is missing")));
      case HTTP_RESPONSE_STATUS -> httpResponseStatus(stack);
      case HTTP_RESPONSE_HEADER_VALUES -> httpResponseHeaderValues(word, stack, span);
      case HTTP_RESPONSE_BODY -> httpResponseBody(stack);
      case HTTP_SEND_FAILURE_KIND -> httpSendFailureKind(stack);
      case HTTP_REQUEST_HAS_BODY -> httpRequestHasBody(word, stack, span);
      case FILE_READ ->
          fileRead(
              word,
              stack,
              span,
              workspace.orElseThrow(
                  () -> new IllegalStateException("workspace reference is missing")));
      case FILE_WRITE ->
          fileWrite(
              word,
              stack,
              span,
              workspace.orElseThrow(
                  () -> new IllegalStateException("workspace reference is missing")));
      case FILE_READ_FAILURE_KIND -> fileReadFailureKind(stack);
      case FILE_WRITE_FAILURE_KIND -> fileWriteFailureKind(stack);
      case CSV_PARSE_TABLE -> delimitedParseTable(word, stack, span, ',');
      case TSV_PARSE_TABLE -> delimitedParseTable(word, stack, span, '\t');
      case TABLE_TO_CSV -> delimitedWriteTable(word, stack, span, ',');
      case TABLE_TO_TSV -> delimitedWriteTable(word, stack, span, '\t');
      case DELIMITED_TEXT_PARSE_FAILURE_KIND -> delimitedTextParseFailureKind(stack);
      case DELIMITED_TEXT_PARSE_FAILURE_OFFSET -> delimitedTextParseFailureOffset(stack);
      case DELIMITED_TEXT_PARSE_FAILURE_LINE -> delimitedTextParseFailureLine(stack);
      case DELIMITED_TEXT_PARSE_FAILURE_COLUMN -> delimitedTextParseFailureColumn(stack);
      case JSON_SHAPE_NULL -> jsonShapeLeaf(word, stack, span, JsonShapeValue.Kind.NULL);
      case JSON_SHAPE_BOOLEAN -> jsonShapeLeaf(word, stack, span, JsonShapeValue.Kind.BOOLEAN);
      case JSON_SHAPE_INTEGER -> jsonShapeLeaf(word, stack, span, JsonShapeValue.Kind.INTEGER);
      case JSON_SHAPE_DECIMAL -> jsonShapeLeaf(word, stack, span, JsonShapeValue.Kind.DECIMAL);
      case JSON_SHAPE_STRING -> jsonShapeLeaf(word, stack, span, JsonShapeValue.Kind.STRING);
      case JSON_SHAPE_ARRAY -> jsonShapeWrap(word, stack, span, false);
      case JSON_SHAPE_EMPTY_OBJECT -> jsonShapeEmptyObject(word, stack, span);
      case JSON_SHAPE_SET_REQUIRED -> jsonShapeSetMember(word, stack, span, true);
      case JSON_SHAPE_SET_OPTIONAL -> jsonShapeSetMember(word, stack, span, false);
      case JSON_SHAPE_NULLABLE -> jsonShapeWrap(word, stack, span, true);
      case JSON_SHAPE_VALIDATE -> jsonShapeValidate(word, stack, span);
      case JSON_SHAPE_FAILURE_KIND -> jsonShapeFailureField(stack, 0);
      case JSON_SHAPE_FAILURE_PATH -> jsonShapeFailureField(stack, 1);
      case JSON_SHAPE_FAILURE_EXPECTED_KIND -> jsonShapeFailureField(stack, 2);
      case JSON_SHAPE_FAILURE_ACTUAL_KIND -> jsonShapeFailureField(stack, 3);
      case HTTP_FORM_URL_ENCODE -> httpFormUrlEncode(word, stack, span);
      case HTTP_RESPONSE_REQUIRE_SUCCESS -> httpResponseRequireSuccess(stack);
    };
  }

  private byte[] jsonShapeLeaf(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span, JsonShapeValue.Kind kind)
      throws RuntimeFailure {
    requireAdditionalStackCapacity(word, stack, span, 1);
    stack.add(JsonShapeValue.leaf(kind));
    return new byte[0];
  }

  private byte[] jsonShapeEmptyObject(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    requireAdditionalStackCapacity(word, stack, span, 1);
    stack.add(JsonShapeValue.emptyObject());
    return new byte[0];
  }

  private byte[] jsonShapeWrap(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span, boolean nullable)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    JsonShapeValue child = (JsonShapeValue) stack.get(index);
    if (nullable
        && (child.kind() == JsonShapeValue.Kind.NULLABLE
            || child.kind() == JsonShapeValue.Kind.NULL)) {
      return new byte[0];
    }
    long nodes = child.nodeCount() + 1;
    int depth = child.depth() + 1;
    requireJsonShapeSize(word, span, depth, nodes);
    stack.set(index, nullable ? JsonShapeValue.nullable(child) : JsonShapeValue.array(child));
    return new byte[0];
  }

  private byte[] jsonShapeSetMember(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span, boolean required)
      throws RuntimeFailure {
    int objectIndex = stack.size() - 3;
    JsonShapeValue object = (JsonShapeValue) stack.get(objectIndex);
    if (object.kind() != JsonShapeValue.Kind.OBJECT) {
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_JSON_SHAPE_OBJECT_REQUIRED,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word.canonicalName())
              .field("actualShapeKind", object.kind().stableName())
              .expected("object")
              .actual(object.kind().stableName())
              .fix("空のJSONオブジェクト形状から形状を組み立ててください")
              .build());
    }
    String name = ((StringValue) stack.get(objectIndex + 1)).value();
    JsonShapeValue child = (JsonShapeValue) stack.get(objectIndex + 2);
    JsonShapeValue result = object.withMember(name, required, child);
    requireJsonShapeSize(word, span, result.depth(), result.nodeCount());
    stack.subList(objectIndex + 1, stack.size()).clear();
    stack.set(objectIndex, result);
    return new byte[0];
  }

  private byte[] jsonShapeValidate(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int jsonIndex = stack.size() - 2;
    JsonRuntimeValue json = (JsonRuntimeValue) stack.get(jsonIndex);
    JsonShapeValue shape = (JsonShapeValue) stack.get(jsonIndex + 1);
    JsonShapeValidator.Outcome outcome = JsonShapeValidator.validate(json.value(), shape);
    if (outcome.workLimitExceeded()) {
      budget.beforeJsonShapeWork(outcome.workUnits(), span, word.canonicalName());
      throw new IllegalStateException("JSON shape work limit was not rejected");
    }
    if (outcome.pathLimitObserved() != 0) {
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_JSON_SHAPE_PATH_LIMIT,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word.canonicalName())
              .limit(
                  "jsonShapePathUtf8Bytes",
                  JsonShapeLimits.MAX_PATH_UTF8_BYTES,
                  outcome.pathLimitObserved())
              .expected(JsonShapeLimits.MAX_PATH_UTF8_BYTES + " UTF-8バイト以下")
              .actual(outcome.pathLimitObserved() + " UTF-8バイト")
              .fix("形状のキー名または入れ子を短くしてください")
              .build());
    }
    budget.beforeJsonShapeWork(outcome.workUnits(), span, word.canonicalName());
    ResultValue result;
    if (outcome.failures().isEmpty()) {
      result = ResultValue.success(JSON_SHAPE_RESULT, json);
    } else {
      budget.beforeArrayWork(outcome.failures().size(), 0, span, "jsonShapeFailures");
      List<RuntimeValue> failures =
          outcome.failures().stream().map(RuntimeValue.class::cast).toList();
      result =
          ResultValue.failure(
              JSON_SHAPE_RESULT, new ArrayValue(ValueType.JSON_SHAPE_FAILURE, failures));
    }
    stack.removeLast();
    stack.set(jsonIndex, result);
    return new byte[0];
  }

  private static byte[] jsonShapeFailureField(ArrayList<RuntimeValue> stack, int field) {
    int index = stack.size() - 1;
    JsonShapeFailureValue failure = (JsonShapeFailureValue) stack.get(index);
    String value =
        switch (field) {
          case 0 -> failure.kind();
          case 1 -> failure.path();
          case 2 -> failure.expectedKind();
          case 3 -> failure.actualKind();
          default -> throw new IllegalArgumentException("unknown JSON shape failure field");
        };
    stack.set(index, new StringValue(value));
    return new byte[0];
  }

  private void requireJsonShapeSize(BuiltinWord word, SourceSpan span, int depth, long nodes)
      throws RuntimeFailure {
    if (depth > JsonShapeLimits.MAX_DEPTH) {
      throw jsonShapeSizeFailure(
          DiagnosticCode.E_JSON_SHAPE_DEPTH_LIMIT,
          word,
          span,
          "jsonShapeDepth",
          JsonShapeLimits.MAX_DEPTH,
          depth,
          "形状の入れ子を減らしてください");
    }
    if (nodes > JsonShapeLimits.MAX_NODES) {
      throw jsonShapeSizeFailure(
          DiagnosticCode.E_JSON_SHAPE_NODE_LIMIT,
          word,
          span,
          "jsonShapeNodes",
          JsonShapeLimits.MAX_NODES,
          nodes,
          "形状のメンバーまたは入れ子を減らしてください");
    }
  }

  private RuntimeFailure jsonShapeSizeFailure(
      DiagnosticCode code,
      BuiltinWord word,
      SourceSpan span,
      String resource,
      long limit,
      long observed,
      String fix) {
    return new RuntimeFailure(
        Diagnostic.builder(code, Severity.ERROR, DiagnosticStage.RUNTIME, sourcePath, span)
            .field("word", word.canonicalName())
            .limit(resource, limit, observed)
            .expected(limit + "以下")
            .actual(Long.toString(observed))
            .fix(fix)
            .build());
  }

  private byte[] fileRead(
      BuiltinWord word,
      ArrayList<RuntimeValue> stack,
      SourceSpan span,
      WorkspaceReference reference)
      throws RuntimeFailure {
    requireWorkspaceOperation(reference, "read");
    String logicalName = ((StringValue) stack.getLast()).value();
    requireLogicalFileName(word, span, logicalName);
    WorkspaceResolver resolver =
        environment
            .workspaceResolver()
            .orElseThrow(
                () ->
                    capabilityUnavailable(
                        word,
                        reference.argumentSpan(),
                        RuntimeCapability.WORKSPACE_RESOLVE,
                        "作業領域解決能力を持つ実行環境で実行してください"));
    FileReadCapability reader =
        environment
            .fileReadCapability()
            .orElseThrow(
                () ->
                    capabilityUnavailable(
                        word,
                        reference.argumentSpan(),
                        RuntimeCapability.FILE_READ,
                        "ファイル読取能力を持つ実行環境で実行してください"));
    budget.beforeFileRead(span, word.canonicalName());
    ResolvedWorkspace resolved =
        resolveWorkspace(word, reference, "read", resolver, reference.argumentSpan());
    long remaining = budget.fileReadRemainingBytes();
    long effectiveLimit = Math.min(resolved.policy().maximumReadBytes(), remaining);
    var request =
        new FileReadRequest(reference.name(), resolved.handle(), logicalName, effectiveLimit);
    String traceName = workspaceTraceName(reference.name());
    effect = fileEffect(RuntimeCapability.FILE_READ, traceName, "read", "capabilityFailure");
    FileReadResult result;
    long blockedAt = budget.beginBlocking();
    try {
      result = reader.read(request);
    } catch (CapabilityException failure) {
      if (failure.kind() != CapabilityException.Kind.FAILURE
          || failure.capability() != RuntimeCapability.FILE_READ
          || !failure.operation().equals("read")) {
        throw fileCapabilityContractFailure(word, span, RuntimeCapability.FILE_READ, "read");
      }
      throw capabilityFailure(
          word,
          span,
          failure,
          RuntimeCapability.FILE_READ,
          "成功するファイル読取能力",
          "実行環境のファイル読取能力を確認してください");
    } catch (RuntimeException failure) {
      throw capabilityFailure(
          word,
          span,
          RuntimeCapability.FILE_READ,
          "read",
          "成功するファイル読取能力",
          "実行環境のファイル読取能力を確認してください");
    } finally {
      budget.endBlocking(blockedAt);
    }
    requireMatchingReadResult(word, span, request, result);
    return applyFileReadResult(word, stack, span, reference, request, result, remaining, traceName);
  }

  private byte[] applyFileReadResult(
      BuiltinWord word,
      ArrayList<RuntimeValue> stack,
      SourceSpan span,
      WorkspaceReference reference,
      FileReadRequest request,
      FileReadResult result,
      long remaining,
      String traceName)
      throws RuntimeFailure {
    switch (result.state()) {
      case SUCCESS -> {
        if (!result.complete()
            || result.body().isEmpty()
            || result.failureKind().isPresent()
            || result.observedBytes() != result.body().orElseThrow().length()
            || result.observedBytes() > request.maximumBytes()) {
          throw fileCapabilityContractFailure(word, span, RuntimeCapability.FILE_READ, "read");
        }
        effect = fileEffect(RuntimeCapability.FILE_READ, traceName, "read", "success");
        budget.afterFileRead(result.observedBytes(), span, word.canonicalName());
        stack.set(
            stack.size() - 1, ResultValue.success(FILE_READ_RESULT, result.body().orElseThrow()));
      }
      case FAILURE -> {
        if (result.complete()
            || result.body().isPresent()
            || result.failureKind().isEmpty()
            || !FileReadFailureValue.KINDS.contains(result.failureKind().orElseThrow())) {
          throw fileCapabilityContractFailure(word, span, RuntimeCapability.FILE_READ, "read");
        }
        String kind = result.failureKind().orElseThrow();
        if (kind.equals("tooLarge")) {
          if (result.observedBytes() != request.maximumBytes() + 1) {
            throw fileCapabilityContractFailure(word, span, RuntimeCapability.FILE_READ, "read");
          }
          if (remaining <= request.maximumBytes()) {
            effect = fileEffect(RuntimeCapability.FILE_READ, traceName, "read", "limitExceeded");
            throw fileReadTotalLimit(word, span, remaining);
          }
        } else if (result.observedBytes() != 0) {
          throw fileCapabilityContractFailure(word, span, RuntimeCapability.FILE_READ, "read");
        }
        effect = fileEffect(RuntimeCapability.FILE_READ, traceName, "read", "failure:" + kind);
        stack.set(
            stack.size() - 1,
            ResultValue.failure(FILE_READ_RESULT, new FileReadFailureValue(kind)));
      }
      case NOT_MAPPED -> {
        requireEmptyReadResult(word, span, result);
        effect = fileEffect(RuntimeCapability.FILE_READ, traceName, "read", "notMapped");
        throw workspaceFileDiagnostic(
            DiagnosticCode.E_WORKSPACE_FILE_NOT_MAPPED, word, span, reference, "read", null);
      }
      case ACCESS_DENIED -> {
        requireEmptyReadResult(word, span, result);
        effect = fileEffect(RuntimeCapability.FILE_READ, traceName, "read", "accessDenied");
        throw workspaceFileDiagnostic(
            DiagnosticCode.E_WORKSPACE_FILE_ACCESS_DENIED, word, span, reference, "read", null);
      }
      case CANCELLED -> {
        requireEmptyReadResult(word, span, result);
        effect = fileEffect(RuntimeCapability.FILE_READ, traceName, "read", "cancelled");
        throw workspaceFileDiagnostic(
            DiagnosticCode.E_FILE_CANCELLED, word, span, reference, "read", null);
      }
      case UNKNOWN ->
          throw fileCapabilityContractFailure(word, span, RuntimeCapability.FILE_READ, "read");
    }
    return new byte[0];
  }

  private byte[] fileWrite(
      BuiltinWord word,
      ArrayList<RuntimeValue> stack,
      SourceSpan span,
      WorkspaceReference reference)
      throws RuntimeFailure {
    requireWorkspaceOperation(reference, "write");
    int nameIndex = stack.size() - 2;
    String logicalName = ((StringValue) stack.get(nameIndex)).value();
    ByteSequenceValue body = (ByteSequenceValue) stack.getLast();
    requireLogicalFileName(word, span, logicalName);
    WorkspaceResolver resolver =
        environment
            .workspaceResolver()
            .orElseThrow(
                () ->
                    capabilityUnavailable(
                        word,
                        reference.argumentSpan(),
                        RuntimeCapability.WORKSPACE_RESOLVE,
                        "作業領域解決能力を持つ実行環境で実行してください"));
    FileWriteCapability writer =
        environment
            .fileWriteCapability()
            .orElseThrow(
                () ->
                    capabilityUnavailable(
                        word,
                        reference.argumentSpan(),
                        RuntimeCapability.FILE_WRITE,
                        "ファイル書込能力を持つ実行環境で実行してください"));
    budget.beforeFileWrite(body.length(), span, word.canonicalName());
    ResolvedWorkspace resolved =
        resolveWorkspace(word, reference, "write", resolver, reference.argumentSpan());
    String traceName = workspaceTraceName(reference.name());
    if (body.length() > resolved.policy().maximumWriteBytes()) {
      effect = fileEffect(RuntimeCapability.FILE_WRITE, traceName, "write", "failure:tooLarge");
      stack.removeLast();
      stack.set(
          nameIndex, ResultValue.failure(FILE_WRITE_RESULT, new FileWriteFailureValue("tooLarge")));
      return new byte[0];
    }
    var request =
        new FileWriteRequest(
            reference.name(),
            resolved.handle(),
            logicalName,
            body,
            resolved.policy().maximumWriteBytes());
    effect = fileEffect(RuntimeCapability.FILE_WRITE, traceName, "write", "capabilityFailure");
    FileWriteResult result;
    long blockedAt = budget.beginBlocking();
    try {
      result = writer.write(request);
    } catch (CapabilityException failure) {
      if (failure.kind() != CapabilityException.Kind.FAILURE
          || failure.capability() != RuntimeCapability.FILE_WRITE
          || !failure.operation().equals("write")) {
        throw fileCapabilityContractFailure(word, span, RuntimeCapability.FILE_WRITE, "write");
      }
      throw capabilityFailure(
          word,
          span,
          failure,
          RuntimeCapability.FILE_WRITE,
          "成功するファイル書込能力",
          "実行環境のファイル書込能力を確認してください");
    } catch (RuntimeException failure) {
      throw capabilityFailure(
          word,
          span,
          RuntimeCapability.FILE_WRITE,
          "write",
          "成功するファイル書込能力",
          "実行環境のファイル書込能力を確認してください");
    } finally {
      budget.endBlocking(blockedAt);
    }
    requireMatchingWriteResult(word, span, request, result);
    return applyFileWriteResult(word, stack, span, reference, request, result, traceName);
  }

  private byte[] applyFileWriteResult(
      BuiltinWord word,
      ArrayList<RuntimeValue> stack,
      SourceSpan span,
      WorkspaceReference reference,
      FileWriteRequest request,
      FileWriteResult result,
      String traceName)
      throws RuntimeFailure {
    int nameIndex = stack.size() - 2;
    switch (result.state()) {
      case SUCCESS -> {
        if (result.failureKind().isPresent()
            || result.publishedBytes().isEmpty()
            || result.publishedBytes().orElseThrow() != request.body().length()) {
          throw fileCapabilityContractFailure(word, span, RuntimeCapability.FILE_WRITE, "write");
        }
        effect = fileEffect(RuntimeCapability.FILE_WRITE, traceName, "write", "success");
        stack.removeLast();
        stack.set(
            nameIndex,
            ResultValue.success(
                FILE_WRITE_RESULT,
                new IntegerValue(BigInteger.valueOf(result.publishedBytes().orElseThrow()))));
      }
      case FAILURE -> {
        if (result.publishedBytes().isPresent()
            || result.failureKind().isEmpty()
            || !FileWriteFailureValue.KINDS.contains(result.failureKind().orElseThrow())) {
          throw fileCapabilityContractFailure(word, span, RuntimeCapability.FILE_WRITE, "write");
        }
        String kind = result.failureKind().orElseThrow();
        effect = fileEffect(RuntimeCapability.FILE_WRITE, traceName, "write", "failure:" + kind);
        stack.removeLast();
        stack.set(
            nameIndex, ResultValue.failure(FILE_WRITE_RESULT, new FileWriteFailureValue(kind)));
      }
      case NOT_MAPPED -> {
        requireEmptyWriteResult(word, span, result);
        effect = fileEffect(RuntimeCapability.FILE_WRITE, traceName, "write", "notMapped");
        throw workspaceFileDiagnostic(
            DiagnosticCode.E_WORKSPACE_FILE_NOT_MAPPED, word, span, reference, "write", null);
      }
      case ACCESS_DENIED -> {
        requireEmptyWriteResult(word, span, result);
        effect = fileEffect(RuntimeCapability.FILE_WRITE, traceName, "write", "accessDenied");
        throw workspaceFileDiagnostic(
            DiagnosticCode.E_WORKSPACE_FILE_ACCESS_DENIED, word, span, reference, "write", null);
      }
      case CANCELLED -> {
        requireEmptyWriteResult(word, span, result);
        effect = fileEffect(RuntimeCapability.FILE_WRITE, traceName, "write", "cancelled");
        throw workspaceFileDiagnostic(
            DiagnosticCode.E_FILE_CANCELLED, word, span, reference, "write", null);
      }
      case UNKNOWN ->
          throw fileCapabilityContractFailure(word, span, RuntimeCapability.FILE_WRITE, "write");
    }
    return new byte[0];
  }

  private static byte[] fileReadFailureKind(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    stack.set(index, new StringValue(((FileReadFailureValue) stack.get(index)).kind()));
    return new byte[0];
  }

  private static byte[] fileWriteFailureKind(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    stack.set(index, new StringValue(((FileWriteFailureValue) stack.get(index)).kind()));
    return new byte[0];
  }

  private ResolvedWorkspace resolveWorkspace(
      BuiltinWord word,
      WorkspaceReference reference,
      String operation,
      WorkspaceResolver resolver,
      SourceSpan span)
      throws RuntimeFailure {
    String traceName = workspaceTraceName(reference.name());
    effect =
        fileEffect(RuntimeCapability.WORKSPACE_RESOLVE, traceName, operation, "capabilityFailure");
    WorkspaceResolution resolution;
    long blockedAt = budget.beginBlocking();
    try {
      resolution = resolver.resolve(reference.name(), operation);
    } catch (CapabilityException failure) {
      if (failure.kind() != CapabilityException.Kind.FAILURE
          || failure.capability() != RuntimeCapability.WORKSPACE_RESOLVE
          || !failure.operation().equals("resolve")) {
        throw fileCapabilityContractFailure(
            word, span, RuntimeCapability.WORKSPACE_RESOLVE, "resolve");
      }
      throw capabilityFailure(
          word,
          span,
          failure,
          RuntimeCapability.WORKSPACE_RESOLVE,
          "成功する作業領域解決能力",
          "実行環境の作業領域解決能力を確認してください");
    } catch (RuntimeException failure) {
      throw capabilityFailure(
          word,
          span,
          RuntimeCapability.WORKSPACE_RESOLVE,
          "resolve",
          "成功する作業領域解決能力",
          "実行環境の作業領域解決能力を確認してください");
    } finally {
      budget.endBlocking(blockedAt);
    }
    if (resolution == null
        || !resolution.workspaceName().equals(reference.name())
        || !resolution.operation().equals(operation)) {
      throw fileCapabilityContractFailure(
          word, span, RuntimeCapability.WORKSPACE_RESOLVE, "resolve");
    }
    switch (resolution.state()) {
      case RESOLVED -> {
        if (resolution.handle().isEmpty()
            || resolution.policy().isEmpty()
            || resolution.invalidReason().isPresent()) {
          throw fileCapabilityContractFailure(
              word, span, RuntimeCapability.WORKSPACE_RESOLVE, "resolve");
        }
        effect = fileEffect(RuntimeCapability.WORKSPACE_RESOLVE, traceName, operation, "resolved");
        return new ResolvedWorkspace(
            resolution.handle().orElseThrow(), resolution.policy().orElseThrow());
      }
      case NOT_CONFIGURED -> {
        requireEmptyWorkspaceResolution(word, span, resolution);
        effect =
            fileEffect(RuntimeCapability.WORKSPACE_RESOLVE, traceName, operation, "notConfigured");
        throw workspaceFileDiagnostic(
            DiagnosticCode.E_WORKSPACE_NOT_CONFIGURED, word, span, reference, operation, null);
      }
      case DENIED -> {
        requireEmptyWorkspaceResolution(word, span, resolution);
        effect = fileEffect(RuntimeCapability.WORKSPACE_RESOLVE, traceName, operation, "denied");
        throw workspaceFileDiagnostic(
            DiagnosticCode.E_WORKSPACE_ACCESS_DENIED, word, span, reference, operation, null);
      }
      case INVALID -> {
        if (resolution.handle().isPresent()
            || resolution.policy().isPresent()
            || resolution.invalidReason().isEmpty()
            || !WORKSPACE_INVALID_REASONS.contains(resolution.invalidReason().orElseThrow())) {
          throw fileCapabilityContractFailure(
              word, span, RuntimeCapability.WORKSPACE_RESOLVE, "resolve");
        }
        String reason = resolution.invalidReason().orElseThrow();
        effect =
            fileEffect(
                RuntimeCapability.WORKSPACE_RESOLVE, traceName, operation, "invalid:" + reason);
        throw workspaceFileDiagnostic(
            DiagnosticCode.E_WORKSPACE_CONFIGURATION_INVALID,
            word,
            span,
            reference,
            operation,
            reason);
      }
    }
    throw new IllegalStateException("unreachable workspace resolution state");
  }

  private void requireEmptyWorkspaceResolution(
      BuiltinWord word, SourceSpan span, WorkspaceResolution resolution) throws RuntimeFailure {
    if (resolution.handle().isPresent()
        || resolution.policy().isPresent()
        || resolution.invalidReason().isPresent()) {
      throw fileCapabilityContractFailure(
          word, span, RuntimeCapability.WORKSPACE_RESOLVE, "resolve");
    }
  }

  private void requireMatchingReadResult(
      BuiltinWord word, SourceSpan span, FileReadRequest request, FileReadResult result)
      throws RuntimeFailure {
    if (result == null
        || !result.workspaceName().equals(request.workspaceName())
        || result.handle() != request.handle()
        || !result.logicalName().equals(request.logicalName())) {
      throw fileCapabilityContractFailure(word, span, RuntimeCapability.FILE_READ, "read");
    }
  }

  private void requireMatchingWriteResult(
      BuiltinWord word, SourceSpan span, FileWriteRequest request, FileWriteResult result)
      throws RuntimeFailure {
    if (result == null
        || !result.workspaceName().equals(request.workspaceName())
        || result.handle() != request.handle()
        || !result.logicalName().equals(request.logicalName())) {
      throw fileCapabilityContractFailure(word, span, RuntimeCapability.FILE_WRITE, "write");
    }
  }

  private void requireEmptyReadResult(BuiltinWord word, SourceSpan span, FileReadResult result)
      throws RuntimeFailure {
    if (result.complete()
        || result.body().isPresent()
        || result.failureKind().isPresent()
        || result.observedBytes() != 0) {
      throw fileCapabilityContractFailure(word, span, RuntimeCapability.FILE_READ, "read");
    }
  }

  private void requireEmptyWriteResult(BuiltinWord word, SourceSpan span, FileWriteResult result)
      throws RuntimeFailure {
    if (result.publishedBytes().isPresent() || result.failureKind().isPresent()) {
      throw fileCapabilityContractFailure(word, span, RuntimeCapability.FILE_WRITE, "write");
    }
  }

  private static void requireWorkspaceOperation(WorkspaceReference reference, String operation) {
    if (!reference.operation().equals(operation)) {
      throw new IllegalStateException("file word and workspace operation disagree");
    }
  }

  private void requireLogicalFileName(BuiltinWord word, SourceSpan span, String logicalName)
      throws RuntimeFailure {
    String reason = LogicalFileName.problem(logicalName);
    if (reason == null) return;
    throw new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_LOGICAL_FILE_NAME_INVALID,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("reason", reason)
            .expected("NFCの単一論理ファイル名")
            .actual("不正な論理ファイル名")
            .fix("論理ファイル名の正規化、長さ、文字を確認してください")
            .build());
  }

  private RuntimeFailure workspaceFileDiagnostic(
      DiagnosticCode code,
      BuiltinWord word,
      SourceSpan span,
      WorkspaceReference reference,
      String operation,
      String reason) {
    var builder =
        Diagnostic.builder(code, Severity.ERROR, DiagnosticStage.RUNTIME, sourcePath, span)
            .field("word", word.canonicalName())
            .field("workspace", reference.name());
    if (code != DiagnosticCode.E_WORKSPACE_CONFIGURATION_INVALID) {
      builder.field("operation", operation);
    }
    if (reason != null) builder.field("reason", reason);
    return new RuntimeFailure(
        builder
            .expected("認可された有限ファイル登録")
            .actual(reason == null ? "利用不可" : reason)
            .fix("実行環境の作業領域設定を確認してください")
            .build());
  }

  private RuntimeFailure fileReadTotalLimit(BuiltinWord word, SourceSpan span, long remaining) {
    long used = budget.fileReadBytes();
    long requested = remaining + 1;
    long observed = used + requested;
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_FILE_READ_TOTAL_LIMIT,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("limitName", "fileReadBytes")
            .field("limit", Long.toString(FileLimits.MAX_READ_BYTES))
            .field("used", Long.toString(used))
            .field("requested", Long.toString(requested))
            .limit("fileReadBytes", FileLimits.MAX_READ_BYTES, observed)
            .expected("累積" + FileLimits.MAX_READ_BYTES + "バイト以下")
            .actual("累積" + observed + "バイト")
            .fix("1回の実行で読み取るファイル内容を減らしてください")
            .build());
  }

  private RuntimeFailure fileCapabilityContractFailure(
      BuiltinWord word, SourceSpan span, RuntimeCapability capability, String operation) {
    return capabilityFailure(
        word, span, capability, operation, "規範に適合する閉じた能力応答", "埋込みホストのファイル能力契約を確認してください");
  }

  private String workspaceTraceName(String name) {
    return environment.redactWorkspaceNamesInTrace() ? "<redacted>" : name;
  }

  private static String fileEffect(
      RuntimeCapability capability, String workspace, String operation, String outcome) {
    return capability.sourceName() + ":" + workspace + ":" + operation + ":" + outcome;
  }

  private record ResolvedWorkspace(WorkspaceHandle handle, WorkspacePolicy policy) {}

  private byte[] emptyHttpRequest(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    requireAdditionalStackCapacity(word, stack, span, 1);
    stack.add(HttpRequestValue.empty());
    return new byte[0];
  }

  private byte[] httpSend(
      BuiltinWord word,
      ArrayList<RuntimeValue> stack,
      SourceSpan span,
      LogicalConnectionReference reference)
      throws RuntimeFailure {
    SourceSpan wordSpan = logicalConnectionWordSpan(span, reference);
    String method =
        reference
            .httpMethod()
            .orElseThrow(() -> new IllegalStateException("HTTP send reference has no method"));
    var request = (HttpRequestValue) stack.getLast();
    requireValidHttpRequest(request);
    long bodyBytes = request.body().map(body -> (long) body.bytes().length()).orElse(0L);
    environment
        .connectionResolver()
        .orElseThrow(
            () ->
                capabilityUnavailable(
                    word,
                    wordSpan,
                    RuntimeCapability.CONNECTION_RESOLVE,
                    "論理接続解決能力を持つ実行環境で実行してください"));
    HttpTransport transport =
        environment
            .httpTransport()
            .orElseThrow(
                () ->
                    capabilityUnavailable(
                        word, wordSpan, RuntimeCapability.HTTP_SEND, "HTTP送信能力を持つ実行環境で実行してください"));
    // 初回はHTTPS機能グループの判定順を保ち、追加試行だけを待機後に予約する。
    budget.beforeHttpSend(bodyBytes, wordSpan, word.canonicalName());
    ConnectionPolicy policy = resolveLogicalConnection(word, span, reference);
    if (!policy.allowedMethods().contains(method)) {
      throw httpSendDiagnostic(
          DiagnosticCode.E_HTTP_METHOD_NOT_ALLOWED,
          word,
          wordSpan,
          java.util.Map.of("connection", reference.name(), "method", method),
          "接続で許可されたHTTP method",
          method,
          "接続方針または静的methodを確認してください");
    }
    if ((method.equals("GET") || method.equals("HEAD")) && request.body().isPresent()) {
      throw httpSendDiagnostic(
          DiagnosticCode.E_HTTP_BODY_NOT_ALLOWED,
          word,
          wordSpan,
          java.util.Map.of("method", method),
          "本文不在",
          "本文あり",
          "GETまたはHEADの本文を削除してください");
    }
    URI target = targetUri(word, wordSpan, request, policy);
    if (bodyBytes > policy.maximumRequestBytes()) {
      throw httpSendDiagnostic(
          DiagnosticCode.E_HTTP_REQUEST_SIZE_LIMIT,
          word,
          wordSpan,
          java.util.Map.of(
              "connection",
              reference.name(),
              "method",
              method,
              "limitName",
              "requestBytes",
              "limit",
              Long.toString(policy.maximumRequestBytes()),
              "observed",
              Long.toString(bodyBytes)),
          "接続方針の要求本文上限以下",
          Long.toString(bodyBytes),
          "要求本文を小さくしてください");
    }
    if (!policy.authenticationKind().equals("none")
        && !policy.authenticationKind().equals("apiKey")
        && !policy.authenticationKind().equals("basic")
        && !policy.authenticationKind().equals("bearer")) {
      throw httpSendDiagnostic(
          DiagnosticCode.E_HTTP_AUTHENTICATION_UNSUPPORTED,
          word,
          wordSpan,
          java.util.Map.of(
              "connection", reference.name(), "authenticationKind", policy.authenticationKind()),
          "none、apiKey、basic、bearerのいずれか",
          policy.authenticationKind(),
          "対応する認証方式を設定してください");
    }

    List<HttpTransportHeader> headers =
        request.headers().stream()
            .map(header -> new HttpTransportHeader(header.name(), header.value()))
            .toList();
    String traceName =
        environment.redactLogicalConnectionNamesInTrace() ? "<redacted>" : reference.name();
    HttpRetryPolicy retry = policy.httpRetryPolicy();
    String retryProblem = HttpRetryPolicyValidator.validate(retry);
    if (retryProblem != null) {
      throw httpReliabilityDiagnostic(
          DiagnosticCode.E_HTTP_RETRY_POLICY_INVALID,
          word,
          wordSpan,
          reference,
          Map.of("reason", retryProblem),
          "有効なHTTP再試行方針",
          retryProblem,
          "論理接続のHTTP再試行方針を確認してください");
    }
    boolean retryMethod = httpRetryMethodAllowed(retry, method, request);
    long retryAfter = 0;
    for (int attempt = 1; attempt <= retry.maximumAttempts(); attempt++) {
      long backoff = attempt == 1 ? 0 : retryBackoff(retry, attempt);
      waitBeforeHttpAttempt(
          word, wordSpan, reference, method, retry, Math.max(backoff, retryAfter));
      if (attempt > 1) {
        budget.beforeHttpSend(bodyBytes, wordSpan, word.canonicalName());
        budget.recordHttpRetryAttempt();
      }
      long responseRemaining = budget.httpResponseRemainingBytes();
      var transportRequest =
          new HttpTransportRequest(
              reference.name(),
              method,
              target,
              headers,
              request.body().map(HttpRequestValue.Body::bytes),
              policy,
              Math.min(policy.maximumResponseBytes(), responseRemaining),
              policy.maximumResponseBytes(),
              responseRemaining <= policy.maximumResponseBytes());
      lastHttpAttemptStartNanos.put(reference.name(), environment.resourceClock().nanoTime());
      HttpTransportResult result =
          invokeHttpTransport(
              word, wordSpan, reference, method, transport, transportRequest, traceName, attempt);
      boolean retryable =
          retryMethod && attempt < retry.maximumAttempts() && httpResultRetryable(retry, result);
      if (!retryable) {
        if (retryMethod
            && attempt == retry.maximumAttempts()
            && httpResultRetryable(retry, result)
            && !retry.finalFailurePolicy().equals("disabled")) {
          recordFinalHttpFailure(word, wordSpan, reference, method, retry, result, attempt);
        }
        return applyHttpTransportResult(
            word, stack, wordSpan, reference, policy, result, traceName);
      }
      chargeDiscardedHttpResult(word, wordSpan, policy, result);
      retryAfter = retryAfterDelay(word, wordSpan, reference, method, retry, result);
    }
    throw new IllegalStateException("HTTP retry loop did not return");
  }

  private void recordFinalHttpFailure(
      BuiltinWord word,
      SourceSpan span,
      LogicalConnectionReference reference,
      String method,
      HttpRetryPolicy policy,
      HttpTransportResult result,
      int attempts)
      throws RuntimeFailure {
    Optional<HttpFinalFailureSink> configured = environment.httpFinalFailureSink();
    if (configured.isEmpty()) {
      if (policy.finalFailurePolicy().equals("optional")) return;
      throw httpReliabilityDiagnostic(
          DiagnosticCode.E_HTTP_FINAL_FAILURE_UNAVAILABLE,
          word,
          span,
          reference,
          Map.of("method", method),
          "利用可能なHTTP最終失敗記録能力",
          "能力なし",
          "実行環境へ最終失敗記録能力を設定してください");
    }
    budget.beforeHttpFinalFailureRecord(span, word.canonicalName());
    HttpFinalFailureRecord record =
        result.state() == HttpTransportResult.State.RESPONSE
            ? new HttpFinalFailureRecord(
                reference.name(), method, attempts, "httpStatus", Optional.empty(), result.status())
            : new HttpFinalFailureRecord(
                reference.name(),
                method,
                attempts,
                "transportFailure",
                result.failureKind(),
                java.util.OptionalInt.empty());
    HttpFinalFailureSink.Result recorded;
    long blockedAt = budget.beginBlocking();
    try {
      recorded = configured.orElseThrow().record(record);
    } catch (CapabilityException | RuntimeException failure) {
      throw httpReliabilityDiagnostic(
          DiagnosticCode.E_HTTP_FINAL_FAILURE_FAILURE,
          word,
          span,
          reference,
          Map.of("method", method),
          "成功するHTTP最終失敗記録能力",
          "能力失敗",
          "実行環境の最終失敗記録能力を確認してください");
    } finally {
      budget.endBlocking(blockedAt);
    }
    if (recorded == null) throw new IllegalStateException("HTTP final failure sink returned null");
    if (recorded == HttpFinalFailureSink.Result.CANCELLED) {
      throw httpReliabilityDiagnostic(
          DiagnosticCode.E_HTTP_FINAL_FAILURE_CANCELLED,
          word,
          span,
          reference,
          Map.of("method", method),
          "完了するHTTP最終失敗記録",
          "取消",
          "取消を扱う上位実行環境を確認してください");
    }
  }

  private HttpTransportResult invokeHttpTransport(
      BuiltinWord word,
      SourceSpan span,
      LogicalConnectionReference reference,
      String method,
      HttpTransport transport,
      HttpTransportRequest request,
      String traceName,
      int attempt)
      throws RuntimeFailure {
    effect = httpSendEffect(traceName, method, "attempt:" + attempt + ":capabilityFailure");
    HttpTransportResult result;
    long blockedAt = budget.beginBlocking();
    try {
      result = transport.send(request);
    } catch (CapabilityException failure) {
      if (failure.kind() != CapabilityException.Kind.FAILURE
          || failure.capability() != RuntimeCapability.HTTP_SEND
          || !failure.operation().equals("send")) {
        throw new IllegalStateException("HTTP transport violated its failure contract");
      }
      throw capabilityFailure(
          word,
          span,
          RuntimeCapability.HTTP_SEND,
          "send",
          "成功するHTTP送信能力",
          "実行環境のHTTP送信能力を確認してください");
    } catch (RuntimeException failure) {
      throw capabilityFailure(
          word,
          span,
          RuntimeCapability.HTTP_SEND,
          "send",
          "成功するHTTP送信能力",
          "実行環境のHTTP送信能力を確認してください");
    } finally {
      budget.endBlocking(blockedAt);
    }
    if (result == null
        || !result.connectionName().equals(reference.name())
        || !result.method().equals(method)) {
      throw new IllegalStateException("HTTP transport returned a mismatched response");
    }
    if (result.receivedBodyBytes() > request.responseBodyLimit() + 1) {
      throw new IllegalStateException("HTTP transport read beyond its response limit");
    }
    if (result.contentDecodingWorkBytes() > request.decodedBodyLimit() + 1) {
      throw new IllegalStateException("HTTP transport decoded beyond its response limit");
    }
    return result;
  }

  private static boolean httpRetryMethodAllowed(
      HttpRetryPolicy policy, String method, HttpRequestValue request) {
    if (method.equals("GET") || method.equals("HEAD")) return true;
    if (policy.methodSafety().equals("apiGuaranteed")) return true;
    if (!policy.methodSafety().equals("idempotencyKey")) return false;
    String expected =
        policy.idempotencyKeyHeader().orElseThrow().toLowerCase(java.util.Locale.ROOT);
    return request.headers().stream()
        .anyMatch(header -> header.name().equals(expected) && !header.value().isEmpty());
  }

  private static long retryBackoff(HttpRetryPolicy policy, int attempt) {
    long delay = policy.initialDelayMilliseconds();
    for (int index = 2; index < attempt; index++) {
      delay = Math.min(policy.maximumDelayMilliseconds(), delay * policy.backoffMultiplier());
    }
    return delay;
  }

  private static boolean httpResultRetryable(HttpRetryPolicy policy, HttpTransportResult result) {
    return switch (result.state()) {
      case RESPONSE ->
          result.status().isPresent()
              && policy.retryableStatusCodes().contains(result.status().getAsInt());
      case FAILURE ->
          result.failureKind().isPresent()
              && policy.retryableFailureKinds().contains(result.failureKind().orElseThrow());
      default -> false;
    };
  }

  private void chargeDiscardedHttpResult(
      BuiltinWord word, SourceSpan span, ConnectionPolicy policy, HttpTransportResult result)
      throws RuntimeFailure {
    if (result.state() == HttpTransportResult.State.RESPONSE) {
      if (result.body().isEmpty() || result.status().isEmpty()) {
        throw new IllegalStateException("retryable HTTP response violated its contract");
      }
      requireValidHttpResponseHeaders(result.headers());
      budget.afterHttpResponseBytes(result.receivedBodyBytes(), span, word.canonicalName());
      budget.beforeByteSequenceWork(
          result.body().orElseThrow().length(),
          result.contentDecodingWorkBytes(),
          span,
          word.canonicalName());
    } else if (result.state() == HttpTransportResult.State.FAILURE) {
      if (result.knownResponseTotalBytes().isPresent()) {
        budget.rejectKnownHttpResponseBytes(
            result.knownResponseTotalBytes().orElseThrow(), span, word.canonicalName());
      }
      budget.afterHttpResponseBytes(result.receivedBodyBytes(), span, word.canonicalName());
      budget.beforeByteSequenceWork(
          0, result.contentDecodingWorkBytes(), span, word.canonicalName());
    } else {
      throw new IllegalStateException("non-retryable HTTP result selected for retry");
    }
  }

  private void waitBeforeHttpAttempt(
      BuiltinWord word,
      SourceSpan span,
      LogicalConnectionReference reference,
      String method,
      HttpRetryPolicy policy,
      long retryDelay)
      throws RuntimeFailure {
    long intervalDelay = 0;
    Long lastStart = lastHttpAttemptStartNanos.get(reference.name());
    if (lastStart != null && policy.minimumStartIntervalMilliseconds() > 0) {
      long now = environment.resourceClock().nanoTime();
      if (now < lastStart) throw new IllegalStateException("HTTP rate-limit clock moved backwards");
      long intervalNanos = policy.minimumStartIntervalMilliseconds() * 1_000_000L;
      long remaining = intervalNanos - (now - lastStart);
      if (remaining > 0) intervalDelay = (remaining + 999_999L) / 1_000_000L;
    }
    long delay = Math.max(retryDelay, intervalDelay);
    if (delay == 0) return;
    budget.beforeHttpReliabilityWait(delay, span, word.canonicalName());
    SleepCapability sleeper =
        environment
            .sleepCapability()
            .orElseThrow(
                () ->
                    httpReliabilityDiagnostic(
                        DiagnosticCode.E_HTTP_RETRY_WAIT_UNAVAILABLE,
                        word,
                        span,
                        reference,
                        Map.of("method", method),
                        "利用可能な待機能力",
                        "能力なし",
                        "実行環境へ待機能力を設定してください"));
    SleepCapability.Result result;
    long blockedAt = budget.beginBlocking();
    try {
      result = sleeper.sleep(delay);
    } catch (CapabilityException | RuntimeException failure) {
      throw httpReliabilityDiagnostic(
          DiagnosticCode.E_HTTP_RETRY_WAIT_FAILURE,
          word,
          span,
          reference,
          Map.of("method", method),
          "成功する待機能力",
          "能力失敗",
          "実行環境の待機能力を確認してください");
    } finally {
      budget.endBlocking(blockedAt);
    }
    if (result == null) throw new IllegalStateException("sleep capability returned null");
    if (result == SleepCapability.Result.CANCELLED) {
      throw httpReliabilityDiagnostic(
          DiagnosticCode.E_HTTP_RETRY_WAIT_CANCELLED,
          word,
          span,
          reference,
          Map.of("method", method),
          "完了する待機",
          "取消",
          "取消を扱う上位実行環境を確認してください");
    }
  }

  private long retryAfterDelay(
      BuiltinWord word,
      SourceSpan span,
      LogicalConnectionReference reference,
      String method,
      HttpRetryPolicy policy,
      HttpTransportResult result)
      throws RuntimeFailure {
    if (!policy.respectRetryAfter() || result.state() != HttpTransportResult.State.RESPONSE)
      return 0;
    List<String> values =
        result.headers().stream()
            .filter(header -> header.name().equalsIgnoreCase("retry-after"))
            .map(HttpTransportHeader::value)
            .toList();
    if (values.size() != 1) return 0;
    String value = values.getFirst();
    long delay;
    if (!value.isEmpty() && value.chars().allMatch(c -> c >= '0' && c <= '9')) {
      try {
        delay = Math.multiplyExact(Long.parseLong(value), 1_000L);
      } catch (ArithmeticException | NumberFormatException ignored) {
        delay = Long.MAX_VALUE;
      }
    } else {
      if (!value.matches(
          "(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun), [0-9]{2} (?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) [0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2} GMT")) {
        return 0;
      }
      java.time.Instant target;
      try {
        target =
            java.time.ZonedDateTime.parse(
                    value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant();
      } catch (java.time.DateTimeException invalid) {
        return 0;
      }
      WallTime wall =
          environment
              .wallTime()
              .orElseThrow(
                  () ->
                      httpReliabilityDiagnostic(
                          DiagnosticCode.E_HTTP_RETRY_CLOCK_UNAVAILABLE,
                          word,
                          span,
                          reference,
                          Map.of("method", method),
                          "利用可能な壁時計能力",
                          "能力なし",
                          "実行環境へ壁時計能力を設定してください"));
      WallTimeReading reading;
      try {
        reading = wall.now();
      } catch (CapabilityException | RuntimeException failure) {
        throw httpReliabilityDiagnostic(
            DiagnosticCode.E_HTTP_RETRY_CLOCK_FAILURE,
            word,
            span,
            reference,
            Map.of("method", method),
            "成功する壁時計能力",
            "能力失敗",
            "実行環境の壁時計能力を確認してください");
      }
      if (reading == null) throw new IllegalStateException("wall clock returned null");
      long targetMilliseconds = target.toEpochMilli();
      long currentMilliseconds = reading.epochMilliseconds();
      if (targetMilliseconds <= currentMilliseconds) {
        delay = 0;
      } else if (currentMilliseconds < 0
          && targetMilliseconds > Long.MAX_VALUE + currentMilliseconds) {
        delay = Long.MAX_VALUE;
      } else {
        delay = targetMilliseconds - currentMilliseconds;
      }
    }
    return Math.min(delay, policy.maximumRetryAfterMilliseconds());
  }

  private RuntimeFailure httpReliabilityDiagnostic(
      DiagnosticCode code,
      BuiltinWord word,
      SourceSpan span,
      LogicalConnectionReference reference,
      Map<String, String> additional,
      String expected,
      String actual,
      String fix) {
    var builder =
        Diagnostic.builder(code, Severity.ERROR, DiagnosticStage.RUNTIME, sourcePath, span)
            .field("word", word.canonicalName())
            .field("connection", reference.name());
    additional.forEach(builder::field);
    return new RuntimeFailure(builder.expected(expected).actual(actual).fix(fix).build());
  }

  private byte[] applyHttpTransportResult(
      BuiltinWord word,
      ArrayList<RuntimeValue> stack,
      SourceSpan span,
      LogicalConnectionReference reference,
      ConnectionPolicy policy,
      HttpTransportResult result,
      String traceName)
      throws RuntimeFailure {
    String method = reference.httpMethod().orElseThrow();
    switch (result.state()) {
      case RESPONSE -> {
        if (result.status().isEmpty()
            || result.body().isEmpty()
            || result.failureKind().isPresent()
            || result.credentialInvalidReason().isPresent()
            || result.knownResponseTotalBytes().isPresent()
            || result.body().orElseThrow().length() > policy.maximumResponseBytes()) {
          throw new IllegalStateException("HTTP transport returned an invalid response contract");
        }
        int status = result.status().orElseThrow();
        if (status < 200 || status > 599) {
          throw new IllegalStateException("HTTP transport returned a non-final status");
        }
        requireValidHttpResponseHeaders(result.headers());
        budget.afterHttpResponseBytes(result.receivedBodyBytes(), span, word.canonicalName());
        budget.beforeByteSequenceWork(
            result.body().orElseThrow().length(),
            result.contentDecodingWorkBytes(),
            span,
            word.canonicalName());
        effect = httpSendEffect(traceName, method, "response");
        var response = new HttpResponseValue(status, result.headers(), result.body().orElseThrow());
        stack.set(stack.size() - 1, ResultValue.success(HTTP_SEND_RESULT, response));
      }
      case FAILURE -> {
        if (result.status().isPresent()
            || !result.headers().isEmpty()
            || result.body().isPresent()
            || result.failureKind().isEmpty()
            || result.credentialInvalidReason().isPresent()
            || result.contentDecodingWorkBytes() != 0
                && !result.failureKind().orElseThrow().equals("contentDecodingFailure")
            || result.knownResponseTotalBytes().isPresent()
                && (!result.failureKind().orElseThrow().equals("responseTooLarge")
                    || result.receivedBodyBytes() != 0)) {
          throw new IllegalStateException("HTTP transport returned an invalid failure contract");
        }
        String kind = result.failureKind().orElseThrow();
        if (result.knownResponseTotalBytes().isPresent()) {
          budget.rejectKnownHttpResponseBytes(
              result.knownResponseTotalBytes().orElseThrow(), span, word.canonicalName());
        }
        budget.afterHttpResponseBytes(result.receivedBodyBytes(), span, word.canonicalName());
        budget.beforeByteSequenceWork(
            0, result.contentDecodingWorkBytes(), span, word.canonicalName());
        effect = httpSendEffect(traceName, method, "failure:" + kind);
        stack.set(
            stack.size() - 1,
            ResultValue.failure(HTTP_SEND_RESULT, new HttpSendFailureValue(kind)));
      }
      case CANCELLED -> {
        requireEmptyHttpTransportResult(result);
        effect = httpSendEffect(traceName, method, "cancelled");
        throw httpSendDiagnostic(
            DiagnosticCode.E_HTTP_CANCELLED,
            word,
            span,
            java.util.Map.of("connection", reference.name(), "method", method),
            "完了するHTTP送信",
            "取消",
            "取消を扱う上位実行環境を確認してください");
      }
      case CREDENTIAL_NOT_CONFIGURED -> {
        requireEmptyHttpTransportResult(result);
        effect = httpSendEffect(traceName, method, "credentialNotConfigured");
        throw credentialDiagnostic(
            DiagnosticCode.E_HTTP_CREDENTIAL_NOT_CONFIGURED,
            word,
            span,
            reference.name(),
            policy.authenticationKind(),
            null,
            "設定済み資格情報",
            "未設定");
      }
      case CREDENTIAL_DENIED -> {
        requireEmptyHttpTransportResult(result);
        effect = httpSendEffect(traceName, method, "credentialDenied");
        throw credentialDiagnostic(
            DiagnosticCode.E_HTTP_CREDENTIAL_ACCESS_DENIED,
            word,
            span,
            reference.name(),
            policy.authenticationKind(),
            null,
            "利用可能な資格情報",
            "拒否");
      }
      case CREDENTIAL_INVALID -> {
        if (result.status().isPresent()
            || !result.headers().isEmpty()
            || result.body().isPresent()
            || result.failureKind().isPresent()
            || result.credentialInvalidReason().isEmpty()) {
          throw new IllegalStateException("HTTP transport returned an invalid credential contract");
        }
        String reason = result.credentialInvalidReason().orElseThrow();
        effect = httpSendEffect(traceName, method, "credentialInvalid");
        throw credentialDiagnostic(
            DiagnosticCode.E_HTTP_CREDENTIAL_INVALID,
            word,
            span,
            reference.name(),
            policy.authenticationKind(),
            reason,
            "有効な資格情報",
            reason);
      }
    }
    return new byte[0];
  }

  private long transportRequestLimit(ConnectionPolicy policy) {
    return Math.min(policy.maximumResponseBytes(), budget.httpResponseRemainingBytes());
  }

  private static void requireValidHttpResponseHeaders(List<HttpTransportHeader> headers) {
    if (headers.size() > HttpLimits.MAX_RESPONSE_HEADER_VALUES) {
      throw new IllegalStateException("HTTP transport returned too many response headers");
    }
    long bytes = 0;
    for (HttpTransportHeader header : headers) {
      String normalized = HttpRequestSupport.normalizeHeaderName(header.name());
      if (HttpRequestSupport.headerNameProblem(header.name()) != null
          || HttpRequestSupport.headerValueProblem(header.value()) != null) {
        throw new IllegalStateException("HTTP transport returned an invalid response header");
      }
      bytes += HttpRequestSupport.headerFieldBytes(normalized, header.value());
      if (bytes > HttpLimits.MAX_RESPONSE_HEADER_BYTES) {
        throw new IllegalStateException("HTTP transport returned oversized response headers");
      }
    }
  }

  private static void requireEmptyHttpTransportResult(HttpTransportResult result) {
    if (result.status().isPresent()
        || !result.headers().isEmpty()
        || result.body().isPresent()
        || result.failureKind().isPresent()
        || result.credentialInvalidReason().isPresent()
        || result.knownResponseTotalBytes().isPresent()
        || result.contentDecodingWorkBytes() != 0
        || result.receivedBodyBytes() != 0) {
      throw new IllegalStateException("HTTP transport returned an invalid empty contract");
    }
  }

  private void requireValidHttpRequest(HttpRequestValue request) {
    if (HttpRequestSupport.pathProblem(request.path()) != null
        || HttpRequestSupport.utf8Length(request.path()) > HttpRequestSupport.MAX_PATH_BYTES
        || request.query().size() > HttpRequestSupport.MAX_QUERY_ITEMS
        || HttpRequestSupport.encodedQueryLength(request.query())
            > HttpRequestSupport.MAX_QUERY_BYTES
        || request.headers().size() > HttpRequestSupport.MAX_HEADER_VALUES
        || HttpRequestSupport.headerBytes(request.headers())
            > HttpRequestSupport.MAX_HEADER_BYTES) {
      throw new IllegalStateException("HTTP request value violated its internal invariant");
    }
    for (HttpRequestValue.Header header : request.headers()) {
      if (HttpRequestSupport.headerNameProblem(header.name()) != null
          || HttpRequestSupport.RESERVED_HEADERS.contains(header.name())
          || HttpRequestSupport.headerValueProblem(header.value()) != null) {
        throw new IllegalStateException("HTTP request header violated its internal invariant");
      }
    }
  }

  private URI targetUri(
      BuiltinWord word, SourceSpan span, HttpRequestValue request, ConnectionPolicy policy)
      throws RuntimeFailure {
    int pathBytes =
        HttpRequestSupport.percentEncodedLength(
            request.path(), true, HttpRequestSupport.MAX_TARGET_URI_BYTES);
    int queryBytes = HttpRequestSupport.encodedQueryLength(request.query());
    long observed =
        (long) policy.baseUri().length()
            + pathBytes
            + (request.query().isEmpty() ? 0L : 1L + queryBytes);
    if (observed > HttpRequestSupport.MAX_TARGET_URI_BYTES) {
      throw httpSendDiagnostic(
          DiagnosticCode.E_HTTP_TARGET_URI_LIMIT,
          word,
          span,
          java.util.Map.of(
              "limitName",
              "httpTargetUriBytes",
              "limit",
              Integer.toString(HttpRequestSupport.MAX_TARGET_URI_BYTES),
              "observed",
              Long.toString(observed)),
          "対象URI上限以下",
          Long.toString(observed),
          "経路または問い合わせを短くしてください");
    }
    String text = policy.baseUri() + HttpRequestSupport.percentEncode(request.path(), true);
    if (!request.query().isEmpty()) {
      text += "?" + HttpRequestSupport.encodeQuery(request.query());
    }
    URI target = URI.create(text);
    if (!ConnectionPolicyValidator.allowsTarget(policy, target)) {
      throw new IllegalStateException("validated HTTP target escaped its connection policy");
    }
    return target;
  }

  private RuntimeFailure credentialDiagnostic(
      DiagnosticCode code,
      BuiltinWord word,
      SourceSpan span,
      String connection,
      String authenticationKind,
      String reason,
      String expected,
      String actual) {
    var fields = new java.util.LinkedHashMap<String, String>();
    fields.put("connection", connection);
    fields.put("authenticationKind", authenticationKind);
    if (reason != null) fields.put("reason", reason);
    return httpSendDiagnostic(code, word, span, fields, expected, actual, "実行環境の資格情報設定を確認してください");
  }

  private RuntimeFailure httpSendDiagnostic(
      DiagnosticCode code,
      BuiltinWord word,
      SourceSpan span,
      java.util.Map<String, String> fields,
      String expected,
      String actual,
      String fix) {
    var builder =
        Diagnostic.builder(code, Severity.ERROR, DiagnosticStage.RUNTIME, sourcePath, span)
            .field("word", word.canonicalName());
    fields.forEach(builder::field);
    return new RuntimeFailure(builder.expected(expected).actual(actual).fix(fix).build());
  }

  private static String httpSendEffect(String connection, String method, String outcome) {
    return RuntimeCapability.HTTP_SEND.sourceName()
        + ":"
        + connection
        + ":"
        + method
        + ":"
        + outcome;
  }

  private byte[] httpRequestSetPath(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    int requestIndex = stack.size() - 2;
    var request = (HttpRequestValue) stack.get(requestIndex);
    String path = ((StringValue) stack.getLast()).value();
    String problem = HttpRequestSupport.pathProblem(path);
    if (problem != null) {
      throw httpRequestDiagnostic(
          word, span, DiagnosticCode.E_HTTP_PATH_INVALID, "reason", problem);
    }
    int bytes = HttpRequestSupport.utf8Length(path);
    if (bytes > HttpRequestSupport.MAX_PATH_BYTES) {
      throw httpLimitDiagnostic(
          word,
          span,
          DiagnosticCode.E_HTTP_PATH_LIMIT,
          "httpPathBytes",
          HttpRequestSupport.MAX_PATH_BYTES,
          bytes);
    }
    int encodedBytes =
        HttpRequestSupport.percentEncodedLength(
            path, true, HttpRequestSupport.MAX_TARGET_URI_BYTES);
    budget.beforeHttpMetadata(encodedBytes, span, word.canonicalName());
    stack.removeLast();
    stack.set(requestIndex, request.withPath(path));
    return new byte[0];
  }

  private byte[] httpRequestAddQuery(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    int requestIndex = stack.size() - 3;
    var request = (HttpRequestValue) stack.get(requestIndex);
    String name = ((StringValue) stack.get(requestIndex + 1)).value();
    String value = ((StringValue) stack.get(requestIndex + 2)).value();
    int count = request.query().size() + 1;
    if (count > HttpRequestSupport.MAX_QUERY_ITEMS) {
      throw httpLimitDiagnostic(
          word,
          span,
          DiagnosticCode.E_HTTP_QUERY_LIMIT,
          "httpQueryItems",
          HttpRequestSupport.MAX_QUERY_ITEMS,
          count);
    }
    HttpRequestValue replacement = request.withQueryItem(name, value);
    int bytes = HttpRequestSupport.encodedQueryLength(replacement.query());
    if (bytes > HttpRequestSupport.MAX_QUERY_BYTES) {
      throw httpLimitDiagnostic(
          word,
          span,
          DiagnosticCode.E_HTTP_QUERY_LIMIT,
          "httpQueryBytes",
          HttpRequestSupport.MAX_QUERY_BYTES,
          bytes);
    }
    long addedBytes =
        (request.query().isEmpty() ? 0L : 1L)
            + HttpRequestSupport.percentEncodedLength(name, false, Integer.MAX_VALUE)
            + 1L
            + HttpRequestSupport.percentEncodedLength(value, false, Integer.MAX_VALUE);
    budget.beforeHttpMetadata(addedBytes, span, word.canonicalName());
    stack.removeLast();
    stack.removeLast();
    stack.set(requestIndex, replacement);
    return new byte[0];
  }

  private byte[] httpRequestSetHeader(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    int requestIndex = stack.size() - 3;
    var request = (HttpRequestValue) stack.get(requestIndex);
    String name = ((StringValue) stack.get(requestIndex + 1)).value();
    String value = ((StringValue) stack.get(requestIndex + 2)).value();
    String nameProblem = HttpRequestSupport.headerNameProblem(name);
    if (nameProblem != null) {
      throw httpRequestDiagnostic(
          word, span, DiagnosticCode.E_HTTP_HEADER_NAME_INVALID, "reason", nameProblem);
    }
    String normalized = HttpRequestSupport.normalizeHeaderName(name);
    if (HttpRequestSupport.RESERVED_HEADERS.contains(normalized)) {
      throw httpRequestDiagnostic(
          word, span, DiagnosticCode.E_HTTP_HEADER_RESERVED, "header", normalized);
    }
    String valueProblem = HttpRequestSupport.headerValueProblem(value);
    if (valueProblem != null) {
      var builder =
          Diagnostic.builder(
                  DiagnosticCode.E_HTTP_HEADER_VALUE_INVALID,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word.canonicalName())
              .field("reason", valueProblem);
      if (valueProblem.equals("valueTooLong")) {
        builder
            .field("limit", Integer.toString(HttpRequestSupport.MAX_HEADER_VALUE_BYTES))
            .field("observed", Integer.toString(value.length()));
      }
      throw new RuntimeFailure(builder.expected("安全なASCIIヘッダー値").actual(valueProblem).build());
    }
    HttpRequestValue replacement = request.withHeader(normalized, value);
    if (replacement.headers().size() > HttpRequestSupport.MAX_HEADER_VALUES) {
      throw httpLimitDiagnostic(
          word,
          span,
          DiagnosticCode.E_HTTP_HEADER_LIMIT,
          "httpHeaderValues",
          HttpRequestSupport.MAX_HEADER_VALUES,
          replacement.headers().size());
    }
    int bytes = HttpRequestSupport.headerBytes(replacement.headers());
    if (bytes > HttpRequestSupport.MAX_HEADER_BYTES) {
      throw httpLimitDiagnostic(
          word,
          span,
          DiagnosticCode.E_HTTP_HEADER_LIMIT,
          "httpHeaderBytes",
          HttpRequestSupport.MAX_HEADER_BYTES,
          bytes);
    }
    budget.beforeHttpMetadata(
        HttpRequestSupport.headerFieldBytes(normalized, value), span, word.canonicalName());
    stack.removeLast();
    stack.removeLast();
    stack.set(requestIndex, replacement);
    return new byte[0];
  }

  private byte[] httpRequestSetJsonBody(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    int requestIndex = stack.size() - 2;
    var request = (HttpRequestValue) stack.get(requestIndex);
    String json =
        serializeJson(
            word, ((JsonRuntimeValue) stack.getLast()).value(), span, "httpRequestJsonBody");
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    budget.beforeByteSequenceWork(bytes.length, bytes.length, span, word.canonicalName());
    stack.removeLast();
    stack.set(
        requestIndex,
        request.withBody(HttpRequestValue.BodyKind.JSON, ByteSequenceValue.takeOwnership(bytes)));
    return new byte[0];
  }

  private byte[] httpRequestSetStringBody(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    int requestIndex = stack.size() - 2;
    var request = (HttpRequestValue) stack.get(requestIndex);
    String value = ((StringValue) stack.getLast()).value();
    long measured = Utf8Length.measureUpTo(value, ByteSequenceLimits.MAX_VALUE_BYTES).bytes();
    budget.beforeByteSequenceWork(measured, measured, span, word.canonicalName());
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length != measured) {
      throw new IllegalStateException("UTF-8 measurement and encoder disagree");
    }
    stack.removeLast();
    stack.set(
        requestIndex,
        request.withBody(HttpRequestValue.BodyKind.STRING, ByteSequenceValue.takeOwnership(bytes)));
    return new byte[0];
  }

  private static byte[] httpRequestSetByteBody(ArrayList<RuntimeValue> stack) {
    int requestIndex = stack.size() - 2;
    var request = (HttpRequestValue) stack.get(requestIndex);
    var body = (ByteSequenceValue) stack.getLast();
    stack.removeLast();
    stack.set(requestIndex, request.withBody(HttpRequestValue.BodyKind.BYTES, body));
    return new byte[0];
  }

  private static byte[] httpResponseStatus(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    var response = (HttpResponseValue) stack.get(index);
    stack.set(index, new IntegerValue(BigInteger.valueOf(response.status())));
    return new byte[0];
  }

  private byte[] httpResponseHeaderValues(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    int responseIndex = stack.size() - 2;
    var response = (HttpResponseValue) stack.get(responseIndex);
    String name = ((StringValue) stack.getLast()).value();
    List<RuntimeValue> values =
        response.headerValues(name).stream()
            .map(StringValue::new)
            .map(RuntimeValue.class::cast)
            .toList();
    budget.beforeArrayWork(values.size(), 0, span, word.canonicalName());
    stack.removeLast();
    stack.set(responseIndex, new ArrayValue(jp.bsb.stdlib.ScalarType.STRING, values));
    return new byte[0];
  }

  private static byte[] httpResponseBody(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    stack.set(index, ((HttpResponseValue) stack.get(index)).body());
    return new byte[0];
  }

  private byte[] httpFormUrlEncode(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    var table = (ArrayValue) stack.get(index);
    if (table.size() > HttpFormUrlEncoder.MAX_ITEMS) {
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_HTTP_FORM_ITEM_LIMIT,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word.canonicalName())
              .limit("httpFormItems", HttpFormUrlEncoder.MAX_ITEMS, table.size())
              .expected(HttpFormUrlEncoder.MAX_ITEMS + "項目以下")
              .actual(table.size() + "項目")
              .fix("フォーム項目を減らしてください")
              .build());
    }
    for (int rowIndex = 0; rowIndex < table.size(); rowIndex++) {
      var row = (ArrayValue) table.get(rowIndex);
      if (row.size() != 2) {
        throw new RuntimeFailure(
            Diagnostic.builder(
                    DiagnosticCode.E_HTTP_FORM_ROW_WIDTH,
                    Severity.ERROR,
                    DiagnosticStage.RUNTIME,
                    sourcePath,
                    span)
                .field("word", word.canonicalName())
                .field("row", Integer.toString(rowIndex + 1))
                .field("expectedColumns", "2")
                .field("actualColumns", Integer.toString(row.size()))
                .expected("各行が名前と値の2要素")
                .actual(row.size() + "要素")
                .fix("各フォーム項目を名前と値の2要素にしてください")
                .build());
      }
    }
    HttpFormUrlEncoder.Measurement measurement = HttpFormUrlEncoder.measure(table);
    if (measurement.outputBytes() > StringLimits.MAX_UTF8_BYTES) {
      throw stringUtf8Limit(word, span, measurement.outputBytes());
    }
    budget.beforeByteSequenceWork(0, measurement.workBytes(), span, word.canonicalName());
    stack.set(index, new StringValue(HttpFormUrlEncoder.encode(table, measurement.outputBytes())));
    return new byte[0];
  }

  private static byte[] httpResponseRequireSuccess(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    var response = (HttpResponseValue) stack.get(index);
    var type = new jp.bsb.stdlib.ResultType(ValueType.HTTP_RESPONSE, ValueType.HTTP_RESPONSE);
    stack.set(
        index,
        response.status() >= 200 && response.status() <= 299
            ? ResultValue.success(type, response)
            : ResultValue.failure(type, response));
    return new byte[0];
  }

  private static byte[] httpSendFailureKind(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    stack.set(index, new StringValue(((HttpSendFailureValue) stack.get(index)).kind()));
    return new byte[0];
  }

  private byte[] httpRequestHasBody(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    requireAdditionalStackCapacity(word, stack, span, 1);
    var request = (HttpRequestValue) stack.getLast();
    stack.add(new BooleanValue(request.body().isPresent()));
    return new byte[0];
  }

  private RuntimeFailure httpRequestDiagnostic(
      BuiltinWord word, SourceSpan span, DiagnosticCode code, String field, String value) {
    return new RuntimeFailure(
        Diagnostic.builder(code, Severity.ERROR, DiagnosticStage.RUNTIME, sourcePath, span)
            .field("word", word.canonicalName())
            .field(field, value)
            .expected("有効なHTTP要求metadata")
            .actual(value)
            .build());
  }

  private RuntimeFailure httpLimitDiagnostic(
      BuiltinWord word,
      SourceSpan span,
      DiagnosticCode code,
      String limitName,
      int limit,
      int observed) {
    return new RuntimeFailure(
        Diagnostic.builder(code, Severity.ERROR, DiagnosticStage.RUNTIME, sourcePath, span)
            .field("word", word.canonicalName())
            .field("limitName", limitName)
            .field("limit", Integer.toString(limit))
            .field("observed", Integer.toString(observed))
            .expected("最大" + limit)
            .actual(Integer.toString(observed))
            .build());
  }

  private byte[] emptyByteSequence(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    requireAdditionalStackCapacity(word, stack, span, 1);
    stack.add(ByteSequenceValue.empty());
    return new byte[0];
  }

  private byte[] byteSequenceLength(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    int index = stack.size() - 1;
    ByteSequenceValue input = (ByteSequenceValue) stack.get(index);
    budget.beforeByteSequenceWork(0, 1, span, word.canonicalName());
    stack.set(index, new IntegerValue(BigInteger.valueOf(input.length())));
    return new byte[0];
  }

  private byte[] byteSequenceSlice(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int inputIndex = stack.size() - 3;
    ByteSequenceValue input = (ByteSequenceValue) stack.get(inputIndex);
    BigInteger start = ((IntegerValue) stack.get(inputIndex + 1)).value();
    BigInteger end = ((IntegerValue) stack.get(inputIndex + 2)).value();
    int[] range = checkedByteSequenceRange(word, input.length(), start, end, span);
    budget.beforeByteSequenceWork(0, 1, span, word.canonicalName());
    ByteSequenceValue result = input.slice(range[0], range[1]);
    stack.removeLast();
    stack.removeLast();
    stack.set(inputIndex, result);
    return new byte[0];
  }

  private byte[] stringToUtf8Bytes(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    String input = ((StringValue) stack.get(index)).value();
    long resultLength = Utf8Length.measureUpTo(input, ByteSequenceLimits.MAX_VALUE_BYTES).bytes();
    requireByteSequenceSize(word, span, "utf8Encode", resultLength);
    budget.beforeByteSequenceWork(resultLength, resultLength, span, word.canonicalName());
    byte[] encoded = input.getBytes(StandardCharsets.UTF_8);
    if (encoded.length != resultLength) {
      throw new IllegalStateException("UTF-8 measurement and encoder disagree");
    }
    stack.set(index, ByteSequenceValue.takeOwnership(encoded));
    return new byte[0];
  }

  private byte[] utf8BytesToStringResult(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    int index = stack.size() - 1;
    ByteSequenceValue input = (ByteSequenceValue) stack.get(index);
    requireByteSequenceSize(word, span, "utf8Decode", input.length());
    budget.beforeByteSequenceWork(0, input.length(), span, word.canonicalName());
    var failure = StrictUtf8.firstFailure(input);
    if (failure.isPresent()) {
      StrictUtf8.Failure detail = failure.orElseThrow();
      stack.set(
          index,
          ResultValue.failure(
              UTF8_DECODE_RESULT,
              new Utf8DecodeFailureValue(detail.kind().stableName(), detail.offset())));
    } else {
      if (input.length() > StringLimits.MAX_UTF8_BYTES) {
        throw stringUtf8Limit(word, span, input.length());
      }
      stack.set(
          index,
          ResultValue.success(
              UTF8_DECODE_RESULT, new StringValue(StrictUtf8.decodeValidated(input))));
    }
    return new byte[0];
  }

  private static byte[] utf8DecodeFailureKind(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    var failure = (Utf8DecodeFailureValue) stack.get(index);
    stack.set(index, new StringValue(failure.kind()));
    return new byte[0];
  }

  private static byte[] utf8DecodeFailureOffset(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    var failure = (Utf8DecodeFailureValue) stack.get(index);
    stack.set(index, new IntegerValue(BigInteger.valueOf(failure.byteOffset())));
    return new byte[0];
  }

  private byte[] byteSequenceToBase64(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    int index = stack.size() - 1;
    ByteSequenceValue input = (ByteSequenceValue) stack.get(index);
    requireByteSequenceSize(word, span, "base64Encode", input.length());
    long outputLength = base64EncodedLength(input.length());
    if (outputLength > StringLimits.MAX_UTF8_BYTES) {
      throw stringUtf8Limit(word, span, outputLength);
    }
    budget.beforeByteSequenceWork(0, input.length() + outputLength, span, word.canonicalName());
    String encoded = StrictBase64.encode(input);
    if (encoded.length() != outputLength) {
      throw new IllegalStateException("Base64 measurement and encoder disagree");
    }
    stack.set(index, new StringValue(encoded));
    return new byte[0];
  }

  private byte[] base64ToByteSequenceResult(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    int index = stack.size() - 1;
    String input = ((StringValue) stack.get(index)).value();
    long inputBytes = Utf8Length.measureUpTo(input, Long.MAX_VALUE).bytes();
    budget.beforeByteSequenceWork(0, inputBytes, span, word.canonicalName());
    var failure = StrictBase64.firstFailure(input);
    if (failure.isPresent()) {
      StrictBase64.Failure detail = failure.orElseThrow();
      stack.set(
          index,
          ResultValue.failure(
              BASE64_DECODE_RESULT,
              new Base64DecodeFailureValue(detail.kind().stableName(), detail.position())));
    } else {
      long outputLength = base64DecodedLength(input);
      requireByteSequenceSize(word, span, "base64Decode", outputLength);
      budget.beforeByteSequenceWork(outputLength, outputLength, span, word.canonicalName());
      ByteSequenceValue decoded = StrictBase64.decodeValidated(input);
      if (decoded.length() != outputLength) {
        throw new IllegalStateException("Base64 measurement and decoder disagree");
      }
      stack.set(index, ResultValue.success(BASE64_DECODE_RESULT, decoded));
    }
    return new byte[0];
  }

  private static byte[] base64DecodeFailureKind(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    var failure = (Base64DecodeFailureValue) stack.get(index);
    stack.set(index, new StringValue(failure.kind()));
    return new byte[0];
  }

  private static byte[] base64DecodeFailureOffset(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    var failure = (Base64DecodeFailureValue) stack.get(index);
    stack.set(index, new IntegerValue(BigInteger.valueOf(failure.characterOffset())));
    return new byte[0];
  }

  private int[] checkedByteSequenceRange(
      BuiltinWord word, int length, BigInteger start, BigInteger end, SourceSpan span)
      throws RuntimeFailure {
    BigInteger maximum = BigInteger.valueOf(length);
    if (start.signum() >= 0 && start.compareTo(end) <= 0 && end.compareTo(maximum) <= 0) {
      return new int[] {start.intValueExact(), end.intValueExact()};
    }
    String fix;
    if (start.compareTo(end) > 0) {
      fix = "開始を終了以下にしてください";
    } else if (end.compareTo(maximum) > 0) {
      fix = "終了を" + length + "以下にしてください";
    } else {
      fix = "開始を0以上にしてください";
    }
    throw new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_BYTE_SEQUENCE_RANGE_OUT_OF_BOUNDS,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("unit", "byte")
            .field("start", start.toString())
            .field("end", end.toString())
            .field("length", Integer.toString(length))
            .field("validRange", "0 <= start <= end <= length")
            .expected("0 <= 開始 <= 終了 <= " + length)
            .actual("開始" + start + "、終了" + end)
            .fix(fix)
            .build());
  }

  private void requireByteSequenceSize(
      BuiltinWord word, SourceSpan span, String operation, long observed) throws RuntimeFailure {
    if (observed <= ByteSequenceLimits.MAX_VALUE_BYTES) {
      return;
    }
    throw new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_BYTE_SEQUENCE_SIZE_LIMIT,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("operation", operation)
            .limit("byteSequenceBytes", ByteSequenceLimits.MAX_VALUE_BYTES, observed)
            .expected(ByteSequenceLimits.MAX_VALUE_BYTES + "バイト以下")
            .actual(observed + "バイト")
            .fix("入力を" + ByteSequenceLimits.MAX_VALUE_BYTES + "バイト以下にしてください")
            .build());
  }

  private static long base64EncodedLength(long inputLength) {
    return ((inputLength + 2) / 3) * 4;
  }

  private static long base64DecodedLength(String input) {
    if (input.isEmpty()) {
      return 0;
    }
    int padding = input.endsWith("==") ? 2 : input.endsWith("=") ? 1 : 0;
    return ((long) input.length() / 4) * 3 - padding;
  }

  long errorOutputBytes() {
    return errorOutput.writtenBytes();
  }

  String effect() {
    return effect;
  }

  private byte[] logicalConnectionCheck(
      BuiltinWord word, SourceSpan span, LogicalConnectionReference reference)
      throws RuntimeFailure {
    resolveLogicalConnection(word, span, reference);
    return new byte[0];
  }

  private ConnectionPolicy resolveLogicalConnection(
      BuiltinWord word, SourceSpan span, LogicalConnectionReference reference)
      throws RuntimeFailure {
    SourceSpan wordSpan = logicalConnectionWordSpan(span, reference);
    ConnectionResolver resolver =
        environment
            .connectionResolver()
            .orElseThrow(
                () ->
                    capabilityUnavailable(
                        word,
                        wordSpan,
                        RuntimeCapability.CONNECTION_RESOLVE,
                        "論理接続解決能力を持つ実行環境で実行してください"));
    String traceName =
        environment.redactLogicalConnectionNamesInTrace() ? "<redacted>" : reference.name();
    effect = connectionEffect(traceName, "failure");
    ConnectionResolution resolution;
    try {
      resolution = resolver.resolve(reference.name(), reference.operation());
    } catch (CapabilityException failure) {
      if (failure.kind() != CapabilityException.Kind.FAILURE
          || failure.capability() != RuntimeCapability.CONNECTION_RESOLVE
          || !failure.operation().equals(reference.operation())) {
        throw new IllegalStateException("connection resolver violated its failure contract");
      }
      throw capabilityFailure(
          word,
          wordSpan,
          RuntimeCapability.CONNECTION_RESOLVE,
          reference.operation(),
          "成功する論理接続解決能力",
          "実行環境の論理接続解決能力を確認してください");
    }
    if (resolution == null
        || !resolution.connectionName().equals(reference.name())
        || !resolution.operation().equals(reference.operation())) {
      throw new IllegalStateException("connection resolver returned a mismatched response");
    }
    return switch (resolution.state()) {
      case NOT_CONFIGURED -> {
        requireEmptyResolution(resolution);
        effect = connectionEffect(traceName, "notConfigured");
        throw logicalConnectionFailure(
            DiagnosticCode.E_LOGICAL_CONNECTION_NOT_CONFIGURED,
            word,
            wordSpan,
            reference,
            null,
            "設定済み論理接続",
            "未設定",
            "実行環境に論理接続を設定してください");
      }
      case DENIED -> {
        requireEmptyResolution(resolution);
        effect = connectionEffect(traceName, "denied");
        throw logicalConnectionFailure(
            DiagnosticCode.E_LOGICAL_CONNECTION_ACCESS_DENIED,
            word,
            wordSpan,
            reference,
            null,
            "利用許可",
            "拒否",
            "実行環境の論理接続権限を確認してください");
      }
      case INVALID -> {
        if (resolution.policy().isPresent()
            || resolution.invalidReason().isEmpty()
            || !ConnectionPolicyValidator.validReason(resolution.invalidReason().orElseThrow())) {
          throw new IllegalStateException(
              "connection resolver returned an invalid reason contract");
        }
        String reason = resolution.invalidReason().orElseThrow();
        effect = connectionEffect(traceName, "invalid");
        throw logicalConnectionFailure(
            DiagnosticCode.E_LOGICAL_CONNECTION_CONFIGURATION_INVALID,
            word,
            wordSpan,
            reference,
            reason,
            "有効な接続設定",
            reason,
            "実行環境の論理接続設定を確認してください");
      }
      case RESOLVED -> {
        if (resolution.policy().isEmpty() || resolution.invalidReason().isPresent()) {
          throw new IllegalStateException(
              "connection resolver returned an invalid resolved contract");
        }
        ConnectionPolicy resolvedPolicy = resolution.policy().orElseThrow();
        String retryReason = HttpRetryPolicyValidator.validate(resolvedPolicy.httpRetryPolicy());
        if (reference.httpMethod().isPresent() && retryReason != null) {
          effect = connectionEffect(traceName, "invalid");
          throw httpReliabilityDiagnostic(
              DiagnosticCode.E_HTTP_RETRY_POLICY_INVALID,
              word,
              wordSpan,
              reference,
              Map.of("reason", retryReason),
              "有効なHTTP再試行方針",
              retryReason,
              "実行環境のHTTP再試行方針を確認してください");
        }
        String reason = ConnectionPolicyValidator.validate(resolvedPolicy);
        if (reason != null) {
          effect = connectionEffect(traceName, "invalid");
          throw logicalConnectionFailure(
              DiagnosticCode.E_LOGICAL_CONNECTION_CONFIGURATION_INVALID,
              word,
              wordSpan,
              reference,
              reason,
              "有効な接続設定",
              reason,
              "実行環境の論理接続設定を確認してください");
        }
        effect = connectionEffect(traceName, "resolved");
        yield resolvedPolicy;
      }
    };
  }

  private static SourceSpan logicalConnectionWordSpan(
      SourceSpan callSpan, LogicalConnectionReference reference) {
    SourcePosition argumentStart = reference.argumentSpan().start();
    if (argumentStart.line() != callSpan.start().line()
        || argumentStart.utf8Offset() <= callSpan.start().utf8Offset()
        || argumentStart.column() <= callSpan.start().column()) {
      throw new IllegalStateException("logical connection argument does not follow its word");
    }
    return new SourceSpan(
        callSpan.start(),
        new SourcePosition(
            argumentStart.utf8Offset() - 1, argumentStart.line(), argumentStart.column() - 1));
  }

  private static void requireEmptyResolution(ConnectionResolution resolution) {
    if (resolution.policy().isPresent() || resolution.invalidReason().isPresent()) {
      throw new IllegalStateException("connection resolver returned an invalid empty contract");
    }
  }

  private RuntimeFailure logicalConnectionFailure(
      DiagnosticCode code,
      BuiltinWord word,
      SourceSpan span,
      LogicalConnectionReference reference,
      String reason,
      String expected,
      String actual,
      String fix) {
    var builder =
        Diagnostic.builder(code, Severity.ERROR, DiagnosticStage.RUNTIME, sourcePath, span)
            .field("word", word.canonicalName())
            .field("capability", RuntimeCapability.CONNECTION_RESOLVE.sourceName())
            .field("connection", reference.name())
            .field("operation", reference.operation());
    if (reason != null) {
      builder.field("reason", reason);
    }
    return new RuntimeFailure(builder.expected(expected).actual(actual).fix(fix).build());
  }

  private static String connectionEffect(String name, String outcome) {
    return RuntimeCapability.CONNECTION_RESOLVE.sourceName() + "|" + name + "|" + outcome;
  }

  private byte[] sleep(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    SleepCapability capability =
        environment
            .sleepCapability()
            .orElseThrow(
                () ->
                    capabilityUnavailable(
                        word, span, RuntimeCapability.TIME_SLEEP, "待機能力を持つ実行環境で実行してください"));
    BigInteger requested = ((IntegerValue) stack.getLast()).value();
    if (requested.signum() < 0
        || requested.compareTo(BigInteger.valueOf(RuntimeLimits.WAIT_CALL_MILLISECONDS)) > 0) {
      String fix = requested.signum() < 0 ? "0以上の待機時間を指定してください" : "待機時間を1日以下にしてください";
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_WAIT_DURATION_RANGE,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word.canonicalName())
              .field("milliseconds", requested.toString())
              .field("valid", "0.." + RuntimeLimits.WAIT_CALL_MILLISECONDS)
              .expected("0以上" + RuntimeLimits.WAIT_CALL_MILLISECONDS + "以下")
              .actual(requested.toString())
              .fix(fix)
              .build());
    }
    long milliseconds = requested.longValueExact();
    long observed = waitedMilliseconds + milliseconds;
    if (observed > RuntimeLimits.WAIT_TOTAL_MILLISECONDS) {
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_WAIT_TOTAL_LIMIT,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word.canonicalName())
              .field("currentMilliseconds", Long.toString(waitedMilliseconds))
              .field("additionalMilliseconds", Long.toString(milliseconds))
              .limit("waitTotalMilliseconds", RuntimeLimits.WAIT_TOTAL_MILLISECONDS, observed)
              .expected(RuntimeLimits.WAIT_TOTAL_MILLISECONDS + "ミリ秒以下")
              .actual(observed + "ミリ秒")
              .fix("待機時間の合計を7日以下にしてください")
              .build());
    }

    SleepCapability.Result result;
    long blockedAt = budget.beginBlocking();
    try {
      result = capability.sleep(milliseconds);
    } catch (CapabilityException failure) {
      throw capabilityFailure(
          word, span, failure, RuntimeCapability.TIME_SLEEP, "成功する待機能力", "実行環境の待機能力を確認してください");
    } catch (RuntimeException failure) {
      throw capabilityFailure(
          word, span, RuntimeCapability.TIME_SLEEP, "sleep", "成功する待機能力", "実行環境の待機能力を確認してください");
    } finally {
      budget.endBlocking(blockedAt);
    }
    if (result == null) {
      throw new IllegalStateException("time.sleep returned null");
    }
    if (result == SleepCapability.Result.CANCELLED) {
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_WAIT_CANCELLED,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word.canonicalName())
              .field("milliseconds", Long.toString(milliseconds))
              .expected("完了する待機")
              .actual("取消")
              .fix("取消を扱う上位実行環境を確認してください")
              .build());
    }
    waitedMilliseconds = observed;
    stack.removeLast();
    effect = "time.sleep:" + milliseconds;
    return new byte[0];
  }

  private byte[] monotonicMilliseconds(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    MonotonicTime capability =
        environment
            .monotonicTime()
            .orElseThrow(
                () ->
                    capabilityUnavailable(
                        word, span, RuntimeCapability.TIME_MONOTONIC, "単調時計能力を持つ実行環境で実行してください"));
    long milliseconds;
    try {
      milliseconds = capability.milliseconds();
    } catch (CapabilityException failure) {
      throw capabilityFailure(
          word,
          span,
          failure,
          RuntimeCapability.TIME_MONOTONIC,
          "成功する単調時計能力",
          "実行環境の単調時計能力を確認してください");
    }
    if (milliseconds < 0 || milliseconds < lastMonotonicMilliseconds) {
      throw new IllegalStateException("time.monotonic returned a decreasing value");
    }
    requireAdditionalStackCapacity(word, stack, span, 1);
    stack.add(new IntegerValue(BigInteger.valueOf(milliseconds)));
    lastMonotonicMilliseconds = milliseconds;
    effect = "time.monotonic";
    return new byte[0];
  }

  private byte[] wallTime(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    WallTime capability =
        environment
            .wallTime()
            .orElseThrow(
                () ->
                    capabilityUnavailable(
                        word, span, RuntimeCapability.TIME_WALL, "日時能力を持つ実行環境で実行してください"));
    WallTimeReading reading;
    try {
      reading = capability.now();
    } catch (CapabilityException failure) {
      throw capabilityFailure(
          word, span, failure, RuntimeCapability.TIME_WALL, "成功する日時能力", "実行環境の日時能力を確認してください");
    } catch (RuntimeException failure) {
      throw capabilityFailure(
          word, span, RuntimeCapability.TIME_WALL, "now", "成功する日時能力", "実行環境の日時能力を確認してください");
    }
    if (reading == null) {
      throw new IllegalStateException("time.wall returned null");
    }
    if (reading.offsetMinutes() < -1_080 || reading.offsetMinutes() > 1_080) {
      throw dateTimeRange(
          word,
          span,
          "offsetMinutes",
          Integer.toString(reading.offsetMinutes()),
          "-1080..1080",
          "-18:00から+18:00",
          reading.offsetMinutes() + "分");
    }
    java.time.OffsetDateTime value;
    try {
      value =
          DateTimeValue.checkedOffsetDateTime(reading.epochMilliseconds(), reading.offsetMinutes());
    } catch (IllegalArgumentException failure) {
      throw dateTimeRange(
          word,
          span,
          "epochMilliseconds",
          Long.toString(reading.epochMilliseconds()),
          "representable",
          "表現可能な日時",
          "範囲外");
    }
    if (value.getYear() < 0 || value.getYear() > 9_999) {
      throw dateTimeRange(
          word,
          span,
          "year",
          Integer.toString(value.getYear()),
          "0000..9999",
          "0000年から9999年",
          value.getYear() + "年");
    }
    requireAdditionalStackCapacity(word, stack, span, 1);
    stack.add(new DateTimeValue(reading.epochMilliseconds(), reading.offsetMinutes()));
    effect = "time.wall";
    return new byte[0];
  }

  private RuntimeFailure dateTimeRange(
      BuiltinWord word,
      SourceSpan span,
      String field,
      String value,
      String valid,
      String expected,
      String actual) {
    String validField = field.equals("year") ? "validYear" : "valid";
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_DATETIME_RANGE,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field(field, value)
            .field(validField, valid)
            .expected(expected)
            .actual(actual)
            .fix("日時能力の値を確認してください")
            .build());
  }

  private static byte[] dateTimeToString(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    stack.set(index, new StringValue(((DateTimeValue) stack.get(index)).displayText()));
    return new byte[0];
  }

  private byte[] processArguments(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    ProcessArguments capability =
        environment
            .processArguments()
            .orElseThrow(
                () ->
                    capabilityUnavailable(
                        word, span, RuntimeCapability.PROCESS_ARGUMENTS, "起動引数能力を持つ実行環境で実行してください"));
    List<String> arguments;
    try {
      arguments = List.copyOf(capability.arguments());
    } catch (CapabilityException failure) {
      throw capabilityFailure(
          word,
          span,
          failure,
          RuntimeCapability.PROCESS_ARGUMENTS,
          "成功する起動引数能力",
          "実行環境の起動引数能力を確認してください");
    } catch (RuntimeException failure) {
      throw capabilityFailure(
          word,
          span,
          RuntimeCapability.PROCESS_ARGUMENTS,
          "arguments",
          "成功する起動引数能力",
          "実行環境の起動引数能力を確認してください");
    }
    if (arguments.size() > ArrayLimits.MAX_LENGTH) {
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_ARGUMENT_COUNT_LIMIT,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word.canonicalName())
              .limit("argumentCount", ArrayLimits.MAX_LENGTH, arguments.size())
              .expected(ArrayLimits.MAX_LENGTH + "要素以下")
              .actual(arguments.size() + "要素")
              .fix("引数を減らしてください")
              .build());
    }

    long total = 0;
    var values = new ArrayList<RuntimeValue>(arguments.size());
    for (String argument : arguments) {
      Utf8Length.Measurement measurement;
      try {
        measurement = Utf8Length.measureUpTo(argument, StringLimits.MAX_UTF8_BYTES);
      } catch (RuntimeException failure) {
        throw new IllegalStateException("process.arguments returned an invalid Unicode value");
      }
      if (measurement.exceededMaximum()) {
        throw stringUtf8Limit(word, span, measurement.bytes());
      }
      total += measurement.bytes();
      values.add(new StringValue(argument));
    }
    if (total > RuntimeLimits.ARGUMENT_TOTAL_UTF8_BYTES) {
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_ARGUMENT_TOTAL_LIMIT,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word.canonicalName())
              .limit("argumentTotalUtf8Bytes", RuntimeLimits.ARGUMENT_TOTAL_UTF8_BYTES, total)
              .expected(RuntimeLimits.ARGUMENT_TOTAL_UTF8_BYTES + "バイト以下")
              .actual(total + "バイト")
              .fix("引数全体を短くしてください")
              .build());
    }
    budget.beforeArrayWork(arguments.size(), 0, span, "arguments");
    requireAdditionalStackCapacity(word, stack, span, 1);
    stack.add(new ArrayValue(ScalarType.STRING, values));
    effect = "process.arguments:" + arguments.size();
    return new byte[0];
  }

  private byte[] programIdentity(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span, boolean location)
      throws RuntimeFailure {
    ProgramIdentity capability =
        environment
            .programIdentity()
            .orElseThrow(
                () ->
                    capabilityUnavailable(
                        word,
                        span,
                        RuntimeCapability.PROGRAM_IDENTITY,
                        "プログラム識別能力を持つ実行環境で実行してください"));
    ProgramMetadata metadata;
    try {
      metadata = capability.identity();
    } catch (CapabilityException failure) {
      throw capabilityFailure(
          word,
          span,
          failure,
          RuntimeCapability.PROGRAM_IDENTITY,
          "成功するプログラム識別能力",
          "実行環境のプログラム識別能力を確認してください");
    } catch (RuntimeException failure) {
      throw capabilityFailure(
          word,
          span,
          RuntimeCapability.PROGRAM_IDENTITY,
          "identity",
          "成功するプログラム識別能力",
          "実行環境のプログラム識別能力を確認してください");
    }

    String item = location ? "location" : "name";
    String value;
    if (location) {
      value =
          metadata
              .location()
              .orElseThrow(
                  () ->
                      capabilityUnavailable(
                          word,
                          span,
                          RuntimeCapability.PROGRAM_IDENTITY,
                          "プログラム場所を持つ実行環境で実行してください"));
      try {
        URI uri = new URI(value);
        if (!uri.isAbsolute()) {
          throw programMetadataInvalid(word, span, item, "notAbsoluteUri", value, "絶対URI");
        }
      } catch (URISyntaxException failure) {
        throw programMetadataInvalid(word, span, item, "notAbsoluteUri", value, "絶対URI");
      }
    } else {
      value = metadata.name();
      if (value.isEmpty()) {
        throw programMetadataInvalid(word, span, item, "empty", value, "空でない論理名");
      }
    }
    Utf8Length.Measurement measurement;
    try {
      measurement = Utf8Length.measureUpTo(value, StringLimits.MAX_UTF8_BYTES);
    } catch (RuntimeException failure) {
      throw programMetadataInvalid(word, span, item, "invalidUnicode", value, "Unicodeスカラー値列");
    }
    if (measurement.exceededMaximum()) {
      throw stringUtf8Limit(word, span, measurement.bytes());
    }
    requireAdditionalStackCapacity(word, stack, span, 1);
    stack.add(new StringValue(value));
    effect = "program.identity:" + item;
    return new byte[0];
  }

  private RuntimeFailure programMetadataInvalid(
      BuiltinWord word,
      SourceSpan span,
      String item,
      String reason,
      String value,
      String expected) {
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_PROGRAM_METADATA_INVALID,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("item", item)
            .field("reason", reason)
            .field("valuePreview", diagnosticTextPreview(value))
            .expected(expected)
            .actual(value.isEmpty() ? "空文字列" : diagnosticTextPreview(value))
            .fix("実行環境のプログラム" + (item.equals("location") ? "場所" : "名") + "を確認してください")
            .build());
  }

  private RuntimeFailure capabilityUnavailable(
      BuiltinWord word, SourceSpan span, RuntimeCapability capability, String fix) {
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_CAPABILITY_UNAVAILABLE,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("capability", capability.sourceName())
            .expected("利用可能な" + capability.sourceName())
            .actual("能力なし")
            .fix(fix)
            .build());
  }

  private RuntimeFailure capabilityFailure(
      BuiltinWord word,
      SourceSpan span,
      CapabilityException failure,
      RuntimeCapability expectedCapability,
      String expected,
      String fix) {
    if (failure.kind() != CapabilityException.Kind.FAILURE
        || failure.capability() != expectedCapability) {
      throw new IllegalStateException(expectedCapability + " violated its failure contract");
    }
    return capabilityFailure(word, span, expectedCapability, failure.operation(), expected, fix);
  }

  private RuntimeFailure capabilityFailure(
      BuiltinWord word,
      SourceSpan span,
      RuntimeCapability capability,
      String operation,
      String expected,
      String fix) {
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_CAPABILITY_FAILURE,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("capability", capability.sourceName())
            .field("operation", operation)
            .expected(expected)
            .actual("能力失敗")
            .fix(fix)
            .build());
  }

  private byte[] programExit(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    BigInteger value = ((IntegerValue) stack.getLast()).value();
    if (value.signum() < 0 || value.compareTo(BigInteger.valueOf(255)) > 0) {
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_EXIT_CODE_RANGE,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word.canonicalName())
              .field("exitCode", value.toString())
              .field("valid", "0..255")
              .expected("0以上255以下")
              .actual(value.toString())
              .fix("0から255の終了コードを指定してください")
              .build());
    }
    ProgramControl control =
        environment
            .programControl()
            .orElseThrow(
                () ->
                    new RuntimeFailure(
                        Diagnostic.builder(
                                DiagnosticCode.E_CAPABILITY_UNAVAILABLE,
                                Severity.ERROR,
                                DiagnosticStage.RUNTIME,
                                sourcePath,
                                span)
                            .field("word", word.canonicalName())
                            .field("capability", RuntimeCapability.PROCESS_EXIT.sourceName())
                            .expected("利用可能なprocess.exit")
                            .actual("能力なし")
                            .fix("終了能力を持つ実行環境で実行してください")
                            .build()));
    int exitCode = value.intValueExact();
    try {
      control.exit(exitCode);
    } catch (CapabilityException failure) {
      if (failure.kind() != CapabilityException.Kind.FAILURE
          || failure.capability() != RuntimeCapability.PROCESS_EXIT) {
        throw new IllegalStateException("process.exit violated its failure contract");
      }
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_CAPABILITY_FAILURE,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word.canonicalName())
              .field("capability", RuntimeCapability.PROCESS_EXIT.sourceName())
              .field("operation", failure.operation())
              .expected("成功する終了能力")
              .actual("能力失敗")
              .fix("実行環境の終了能力を確認してください")
              .build());
    } catch (RuntimeException failure) {
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_CAPABILITY_FAILURE,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word.canonicalName())
              .field("capability", RuntimeCapability.PROCESS_EXIT.sourceName())
              .field("operation", "exit")
              .expected("成功する終了能力")
              .actual("能力失敗")
              .fix("実行環境の終了能力を確認してください")
              .build());
    }
    stack.removeLast();
    effect = "process.exit:" + exitCode;
    throw new ProgramTermination(exitCode);
  }

  private byte[] readLine(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    InputResultValue result = input.readLine(word.canonicalName(), span);
    requireAdditionalStackCapacity(word, stack, span, 1);
    stack.add(result);
    effect =
        "console.input:"
            + switch (result.state()) {
              case LINE -> "line";
              case END -> "end";
              case CANCEL -> "cancel";
            };
    return new byte[0];
  }

  private void requireAdditionalStackCapacity(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span, int additional)
      throws RuntimeFailure {
    long observed = (long) stack.size() + additional;
    if (observed > RuntimeLimits.DATA_STACK_VALUES) {
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_DATA_STACK_LIMIT,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word.canonicalName())
              .limit("dataStackValues", RuntimeLimits.DATA_STACK_VALUES, observed)
              .expected(RuntimeLimits.DATA_STACK_VALUES + "値以下")
              .actual(observed + "値")
              .fix("データスタックへ同時に置く値を減らしてください。")
              .build());
    }
  }

  private byte[] inputPredicate(
      BuiltinWord word,
      ArrayList<RuntimeValue> stack,
      SourceSpan span,
      InputResultValue.State state)
      throws RuntimeFailure {
    InputResultValue input = (InputResultValue) stack.getLast();
    requireAdditionalStackCapacity(word, stack, span, 1);
    stack.add(new BooleanValue(input.state() == state));
    return new byte[0];
  }

  private byte[] inputTakeLine(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    InputResultValue input = (InputResultValue) stack.getLast();
    if (input.line().isEmpty()) {
      String stateName = input.state() == InputResultValue.State.END ? "end" : "cancel";
      String actual = input.state() == InputResultValue.State.END ? "終端" : "取消";
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_INPUT_RESULT_NOT_LINE,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word.canonicalName())
              .field("state", stateName)
              .expected("行")
              .actual(actual)
              .fix(
                  input.state() == InputResultValue.State.END
                      ? "入力終端であるで先に判定してください"
                      : "入力キャンセルであるで先に判定してください")
              .build());
    }
    String line = input.line().orElseThrow();
    stack.set(stack.size() - 1, new StringValue(line));
    return new byte[0];
  }

  private static byte[] optionalWrap(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    stack.set(index, OptionalValue.present(stack.get(index)));
    return new byte[0];
  }

  private byte[] optionalPredicate(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    OptionalValue optional = (OptionalValue) stack.getLast();
    requireAdditionalStackCapacity(word, stack, span, 1);
    stack.add(new BooleanValue(optional.isPresent()));
    return new byte[0];
  }

  private byte[] optionalUnwrap(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    OptionalValue optional = (OptionalValue) stack.getLast();
    if (!optional.isPresent()) {
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_OPTIONAL_VALUE_ABSENT,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word.canonicalName())
              .field("optionalType", optional.type().sourceName())
              .field("state", "absent")
              .expected("値がある任意値")
              .actual("ない")
              .fix("任意に値があるで分岐してから取り出してください")
              .build());
    }
    stack.set(stack.size() - 1, optional.value().orElseThrow());
    return new byte[0];
  }

  private static byte[] optionalDrop(ArrayList<RuntimeValue> stack) {
    stack.removeLast();
    return new byte[0];
  }

  private static byte[] resultWrap(
      ArrayList<RuntimeValue> stack,
      java.util.Optional<IrStackEffect> stackEffect,
      boolean success) {
    IrStackEffect effect =
        stackEffect.orElseThrow(
            () ->
                new IllegalStateException("a result construction call requires a concrete effect"));
    if (effect.outputTypes().size() != 1
        || !(effect.outputTypes().getFirst() instanceof ResultType resultType)) {
      throw new IllegalStateException(
          "a result construction call requires one concrete result output");
    }
    int index = stack.size() - 1;
    RuntimeValue payload = stack.get(index);
    stack.set(
        index,
        success
            ? ResultValue.success(resultType, payload)
            : ResultValue.failure(resultType, payload));
    return new byte[0];
  }

  private byte[] resultPredicate(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span, boolean success)
      throws RuntimeFailure {
    ResultValue result = (ResultValue) stack.getLast();
    requireAdditionalStackCapacity(word, stack, span, 1);
    stack.add(new BooleanValue(success == result.isSuccess()));
    return new byte[0];
  }

  private byte[] resultUnwrap(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span, boolean success)
      throws RuntimeFailure {
    ResultValue result = (ResultValue) stack.getLast();
    if (success != result.isSuccess()) {
      String expectedState = success ? "success" : "failure";
      String actualState = success ? "failure" : "success";
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_RESULT_STATE_MISMATCH,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word.canonicalName())
              .field("resultType", result.type().sourceName())
              .field("expectedState", expectedState)
              .field("actualState", actualState)
              .expected(success ? "成功の結果値" : "失敗の結果値")
              .actual(success ? "失敗の結果値" : "成功の結果値")
              .fix(success ? "結果が成功であるで分岐してから取り出してください" : "結果が失敗であるで分岐してから取り出してください")
              .build());
    }
    stack.set(stack.size() - 1, result.value());
    return new byte[0];
  }

  private static byte[] resultDrop(ArrayList<RuntimeValue> stack) {
    stack.removeLast();
    return new byte[0];
  }

  private static byte[] inputDrop(ArrayList<RuntimeValue> stack) {
    stack.removeLast();
    return new byte[0];
  }

  private byte[] arithmetic(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span, Arithmetic operation)
      throws RuntimeFailure {
    int firstIndex = stack.size() - 2;
    RuntimeValue first = stack.get(firstIndex);
    RuntimeValue second = stack.get(firstIndex + 1);
    if (first instanceof DecimalValue firstDecimal) {
      return decimalArithmetic(
          word, stack, span, firstIndex, firstDecimal, (DecimalValue) second, operation);
    }
    return integerArithmetic(
        word,
        stack,
        span,
        firstIndex,
        ((IntegerValue) first).value(),
        ((IntegerValue) second).value(),
        operation);
  }

  private byte[] integerArithmetic(
      BuiltinWord word,
      ArrayList<RuntimeValue> stack,
      SourceSpan span,
      int firstIndex,
      BigInteger first,
      BigInteger second,
      Arithmetic operation)
      throws RuntimeFailure {

    if (operation == Arithmetic.MULTIPLY && first.signum() != 0 && second.signum() != 0) {
      long minimumDigits = (long) digits(first) + digits(second) - 1;
      if (minimumDigits > RuntimeLimits.INTEGER_DIGITS) {
        throw integerLimit(word, span, minimumDigits);
      }
    }

    BigInteger result =
        switch (operation) {
          case ADD -> first.add(second);
          case SUBTRACT -> first.subtract(second);
          case MULTIPLY -> first.multiply(second);
        };
    int resultDigits = digits(result);
    if (resultDigits > RuntimeLimits.INTEGER_DIGITS) {
      throw integerLimit(word, span, resultDigits);
    }

    // 下側が第1入力、上側が第2入力です。2値を結果1値へ置換します。
    stack.removeLast();
    stack.set(firstIndex, new IntegerValue(result));
    return new byte[0];
  }

  private byte[] decimalArithmetic(
      BuiltinWord word,
      ArrayList<RuntimeValue> stack,
      SourceSpan span,
      int firstIndex,
      DecimalValue first,
      DecimalValue second,
      Arithmetic operation)
      throws RuntimeFailure {
    if (operation == Arithmetic.MULTIPLY
        && first.value().signum() != 0
        && second.value().signum() != 0) {
      long minimumPrecision = (long) first.precision() + second.precision() - 1;
      if (minimumPrecision > RuntimeLimits.DECIMAL_PRECISION) {
        throw decimalPrecisionLimit(word, span, minimumPrecision);
      }
    }

    BigDecimal rawResult =
        switch (operation) {
          case ADD -> first.value().add(second.value());
          case SUBTRACT -> first.value().subtract(second.value());
          case MULTIPLY -> first.value().multiply(second.value());
        };
    DecimalValue result = checkedDecimalResult(word, span, rawResult);
    stack.removeLast();
    stack.set(firstIndex, result);
    return new byte[0];
  }

  private byte[] equals(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int firstIndex = stack.size() - 2;
    RuntimeValue first = stack.get(firstIndex);
    RuntimeValue second = stack.get(firstIndex + 1);
    boolean equal;
    equal = runtimeValuesEqual(word, first, second, span);
    stack.removeLast();
    stack.set(firstIndex, new BooleanValue(equal));
    return new byte[0];
  }

  private boolean runtimeValuesEqual(
      BuiltinWord word, RuntimeValue first, RuntimeValue second, SourceSpan span)
      throws RuntimeFailure {
    RuntimeValue left = first;
    RuntimeValue right = second;
    while (true) {
      if (left instanceof OptionalValue leftOptional
          && right instanceof OptionalValue rightOptional) {
        if (leftOptional.isPresent() != rightOptional.isPresent()) {
          return false;
        }
        if (!leftOptional.isPresent()) {
          return true;
        }
        left = leftOptional.value().orElseThrow();
        right = rightOptional.value().orElseThrow();
      } else if (left instanceof ResultValue leftResult
          && right instanceof ResultValue rightResult) {
        if (leftResult.state() != rightResult.state()) {
          return false;
        }
        left = leftResult.value();
        right = rightResult.value();
      } else {
        break;
      }
    }
    if (left instanceof ArrayValue leftArray && right instanceof ArrayValue rightArray) {
      return arraysEqual(leftArray, rightArray, span);
    }
    if (left instanceof ByteSequenceValue leftBytes
        && right instanceof ByteSequenceValue rightBytes) {
      return byteSequencesEqual(word, leftBytes, rightBytes, span);
    }
    if (left instanceof JsonRuntimeValue leftJson && right instanceof JsonRuntimeValue rightJson) {
      budget.beforeJsonWork(
          0,
          saturatedAdd(jsonTraversalUnits(leftJson.value()), jsonTraversalUnits(rightJson.value())),
          span,
          "equals");
    }
    return left.equals(right);
  }

  private boolean byteSequencesEqual(
      BuiltinWord word, ByteSequenceValue first, ByteSequenceValue second, SourceSpan span)
      throws RuntimeFailure {
    if (first.length() != second.length()) {
      return false;
    }
    for (int index = 0; index < first.length(); index++) {
      budget.beforeByteSequenceWork(0, 1, span, word.canonicalName());
      if (first.byteAt(index) != second.byteAt(index)) {
        return false;
      }
    }
    return true;
  }

  private boolean arraysEqual(ArrayValue first, ArrayValue second, SourceSpan span)
      throws RuntimeFailure {
    if (first.size() != second.size()) {
      return false;
    }
    if (first.elementType() instanceof jp.bsb.stdlib.ArrayType rowType) {
      for (int index = 0; index < first.size(); index++) {
        ArrayValue firstRow = (ArrayValue) first.get(index);
        ArrayValue secondRow = (ArrayValue) second.get(index);
        if (firstRow.size() != secondRow.size()) {
          return false;
        }
      }
      long arrayWork = saturatedAdd(first.size(), first.logicalLeafCount());
      if (rowType.elementType() == ScalarType.JSON) {
        budget.beforeArrayAndJsonWork(
            0, arrayWork, 0, DisplayMetrics.nestedJsonEqualityWork(first, second), span, "equals");
      } else {
        budget.beforeArrayWork(0, arrayWork, span, "equals");
      }
      return first.equals(second);
    }
    if (first.elementType().isOptional() || first.elementType().isResult()) {
      long arrayWork = first.size();
      if (ValueType.arrayConstructorDepth(first.elementType()) > 0) {
        arrayWork = saturatedAdd(arrayWork, first.logicalLeafCount());
      }
      long jsonWork = DisplayMetrics.deepJsonEqualityWork(first, second);
      if (jsonWork > 0) {
        budget.beforeArrayAndJsonWork(0, arrayWork, 0, jsonWork, span, "equals");
      } else {
        budget.beforeArrayWork(0, arrayWork, span, "equals");
      }
      return first.equals(second);
    }
    for (int index = 0; index < first.size(); index++) {
      if (first.get(index) instanceof JsonRuntimeValue firstJson
          && second.get(index) instanceof JsonRuntimeValue secondJson) {
        budget.beforeArrayAndJsonWork(
            0,
            1,
            0,
            saturatedAdd(
                jsonTraversalUnits(firstJson.value()), jsonTraversalUnits(secondJson.value())),
            span,
            "equals");
      } else {
        budget.beforeArrayWork(0, 1, span, "equals");
      }
      if (!first.get(index).equals(second.get(index))) {
        return false;
      }
    }
    return true;
  }

  private static byte[] lessThan(ArrayList<RuntimeValue> stack) {
    int firstIndex = stack.size() - 2;
    RuntimeValue first = stack.get(firstIndex);
    RuntimeValue second = stack.get(firstIndex + 1);
    int comparison = compareNumeric(first, second);

    // 【コンピュータ科学の観点：順序を持つ二項演算】スタックでは先に置いた値が下側です。
    // そのため「3 5 比べて小さい」は、下側の3を左辺、上側の5を右辺として3 < 5を調べます。
    // compareToは巨大な整数も正規小数も切り詰めず、「左辺が小さい」ときだけ負値を返します。
    stack.removeLast();
    stack.set(firstIndex, new BooleanValue(comparison < 0));
    return new byte[0];
  }

  private static byte[] absolute(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    RuntimeValue input = stack.get(index);
    RuntimeValue result =
        switch (input) {
          case IntegerValue integer ->
              integer.value().signum() < 0 ? new IntegerValue(integer.value().negate()) : integer;
          case DecimalValue decimal ->
              decimal.value().signum() < 0 ? new DecimalValue(decimal.value().negate()) : decimal;
          default -> throw new IllegalStateException("absolute received a non-numeric value");
        };
    stack.set(index, result);
    return new byte[0];
  }

  private static byte[] extremum(ArrayList<RuntimeValue> stack, boolean minimum) {
    int firstIndex = stack.size() - 2;
    RuntimeValue first = stack.get(firstIndex);
    RuntimeValue second = stack.get(firstIndex + 1);
    int comparison = compareNumeric(first, second);
    RuntimeValue result =
        minimum ? (comparison <= 0 ? first : second) : (comparison >= 0 ? first : second);
    stack.removeLast();
    stack.set(firstIndex, result);
    return new byte[0];
  }

  private byte[] integerDivision(
      BuiltinWord word,
      ArrayList<RuntimeValue> stack,
      SourceSpan span,
      IntegerDivisionResult requested)
      throws RuntimeFailure {
    int dividendIndex = stack.size() - 2;
    BigInteger dividend = ((IntegerValue) stack.get(dividendIndex)).value();
    BigInteger divisor = ((IntegerValue) stack.get(dividendIndex + 1)).value();
    if (divisor.signum() == 0) {
      throw divisionByZero(word, span, dividend, divisor);
    }

    BigInteger[] quotientAndRemainder = dividend.divideAndRemainder(divisor);
    BigInteger quotient = quotientAndRemainder[0];
    BigInteger remainder = quotientAndRemainder[1];
    if (remainder.signum() != 0 && dividend.signum() != divisor.signum()) {
      quotient = quotient.subtract(BigInteger.ONE);
      remainder = remainder.add(divisor);
    }

    switch (requested) {
      case QUOTIENT -> {
        stack.removeLast();
        stack.set(dividendIndex, new IntegerValue(quotient));
      }
      case REMAINDER -> {
        stack.removeLast();
        stack.set(dividendIndex, new IntegerValue(remainder));
      }
      case BOTH -> {
        stack.set(dividendIndex, new IntegerValue(quotient));
        stack.set(dividendIndex + 1, new IntegerValue(remainder));
      }
    }
    return new byte[0];
  }

  private byte[] decimalDivide(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int dividendIndex = stack.size() - 2;
    RuntimeValue dividend = stack.get(dividendIndex);
    RuntimeValue divisor = stack.get(dividendIndex + 1);
    ensureNonzeroDivisor(word, span, dividend, divisor, null, null);

    DecimalValue result =
        divideToDecimal(
            word,
            span,
            dividend,
            divisor,
            DEFAULT_DIVISION_PRECISION,
            RoundingModeValue.NEAREST_EVEN);
    stack.removeLast();
    stack.set(dividendIndex, result);
    return new byte[0];
  }

  private byte[] precisionDecimalDivide(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    int dividendIndex = stack.size() - 4;
    RuntimeValue dividend = stack.get(dividendIndex);
    RuntimeValue divisor = stack.get(dividendIndex + 1);
    BigInteger precision = ((IntegerValue) stack.get(dividendIndex + 2)).value();
    RoundingModeValue rounding = (RoundingModeValue) stack.get(dividendIndex + 3);

    // 診断順序のため、除数0は精度範囲より先に検査します。
    ensureNonzeroDivisor(word, span, dividend, divisor, precision, rounding);
    int checkedPrecision = checkedDivisionPrecision(word, span, dividend, divisor, precision);
    DecimalValue result =
        divideToDecimal(word, span, dividend, divisor, checkedPrecision, rounding);

    stack.removeLast();
    stack.removeLast();
    stack.removeLast();
    stack.set(dividendIndex, result);
    return new byte[0];
  }

  private DecimalValue divideToDecimal(
      BuiltinWord word,
      SourceSpan span,
      RuntimeValue dividend,
      RuntimeValue divisor,
      int precision,
      RoundingModeValue rounding)
      throws RuntimeFailure {
    BigDecimal rawResult =
        asBigDecimal(dividend)
            .divide(asBigDecimal(divisor), new MathContext(precision, javaRoundingMode(rounding)));
    return checkedDecimalResult(word, span, rawResult);
  }

  private static byte[] integerToDecimal(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    BigInteger input = ((IntegerValue) stack.get(index)).value();
    stack.set(index, new DecimalValue(new BigDecimal(input)));
    return new byte[0];
  }

  private byte[] decimalToInteger(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    DecimalValue input = (DecimalValue) stack.get(index);
    if (input.scale() > 0) {
      throw decimalNotInteger(word, span, input);
    }
    int observedDigits = integerDigitsOfExactDecimal(input);
    if (observedDigits > RuntimeLimits.INTEGER_DIGITS) {
      throw integerLimit(word, span, observedDigits);
    }
    stack.set(index, new IntegerValue(input.value().toBigIntegerExact()));
    return new byte[0];
  }

  private byte[] roundToInteger(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int valueIndex = stack.size() - 2;
    DecimalValue input = (DecimalValue) stack.get(valueIndex);
    RoundingModeValue rounding = (RoundingModeValue) stack.get(valueIndex + 1);

    if (input.scale() <= 0) {
      int observedDigits = integerDigitsOfExactDecimal(input);
      if (observedDigits > RuntimeLimits.INTEGER_DIGITS) {
        throw integerLimit(word, span, observedDigits);
      }
    }
    BigInteger result = input.value().setScale(0, javaRoundingMode(rounding)).toBigIntegerExact();
    int observedDigits = digits(result);
    if (observedDigits > RuntimeLimits.INTEGER_DIGITS) {
      throw integerLimit(word, span, observedDigits);
    }
    stack.removeLast();
    stack.set(valueIndex, new IntegerValue(result));
    return new byte[0];
  }

  private static BigDecimal asBigDecimal(RuntimeValue value) {
    if (value instanceof IntegerValue integer) {
      return new BigDecimal(integer.value());
    }
    return ((DecimalValue) value).value();
  }

  private static RoundingMode javaRoundingMode(RoundingModeValue rounding) {
    return switch (rounding) {
      case NEAREST_EVEN -> RoundingMode.HALF_EVEN;
      case NEAREST_AWAY_FROM_ZERO -> RoundingMode.HALF_UP;
      case TOWARD_ZERO -> RoundingMode.DOWN;
      case TOWARD_POSITIVE_INFINITY -> RoundingMode.CEILING;
      case TOWARD_NEGATIVE_INFINITY -> RoundingMode.FLOOR;
    };
  }

  private static int integerDigitsOfExactDecimal(DecimalValue value) {
    if (value.value().signum() == 0) {
      return 1;
    }
    return Math.toIntExact((long) value.precision() - value.scale());
  }

  private static int compareNumeric(RuntimeValue first, RuntimeValue second) {
    if (first instanceof IntegerValue firstInteger) {
      return firstInteger.value().compareTo(((IntegerValue) second).value());
    }
    return ((DecimalValue) first).value().compareTo(((DecimalValue) second).value());
  }

  private byte[] display(
      BuiltinWord word,
      ArrayList<RuntimeValue> stack,
      SourceSpan span,
      boolean line,
      BoundedOutput target)
      throws RuntimeFailure {
    RuntimeValue value = stack.getLast();
    long candidateBytes = DisplayMetrics.utf8Bytes(value);
    if (line) {
      candidateBytes = saturatedAdd(candidateBytes, 1);
    }
    target.requireWithinLimit(candidateBytes, word.canonicalName(), span);
    String valueText = displayValue(word, value, span);
    String text = line ? valueText + "\n" : valueText;
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    target.write(bytes, word.canonicalName(), span);
    stack.removeLast();
    boolean error = target == errorOutput;
    effect = (error ? "console.error:" : "console.output:") + UPPER_HEX.formatHex(bytes);
    return error ? new byte[0] : bytes;
  }

  private String displayValue(BuiltinWord word, RuntimeValue value, SourceSpan span)
      throws RuntimeFailure {
    var prefix = new StringBuilder();
    RuntimeValue current = value;
    int wrapperDepth = 0;
    while (true) {
      if (current instanceof OptionalValue optional) {
        if (!optional.isPresent()) {
          return prefix.append("ない").append("）".repeat(wrapperDepth)).toString();
        }
        prefix.append("ある（");
        wrapperDepth++;
        current = optional.value().orElseThrow();
      } else if (current instanceof ResultValue result) {
        prefix.append(result.isSuccess() ? "成功（" : "失敗（");
        wrapperDepth++;
        current = result.value();
      } else {
        break;
      }
    }
    String inner;
    if (current instanceof ArrayValue array
        && array.elementType() instanceof jp.bsb.stdlib.ArrayType rowType) {
      long arrayWork = saturatedAdd(array.size(), array.logicalLeafCount());
      if (rowType.elementType() == ScalarType.JSON) {
        budget.beforeArrayAndJsonWork(
            0, arrayWork, 0, DisplayMetrics.nestedJsonWork(array), span, "display");
      } else {
        budget.beforeArrayWork(0, arrayWork, span, "display");
      }
      inner = current.displayText();
    } else if (current instanceof ArrayValue array && array.elementType() == ScalarType.JSON) {
      inner = displayJsonArray(word, array, span);
    } else if (current instanceof ArrayValue array
        && (array.elementType().isOptional() || array.elementType().isResult())) {
      long arrayWork = array.size();
      if (ValueType.arrayConstructorDepth(array.elementType()) > 0) {
        arrayWork = saturatedAdd(arrayWork, array.logicalLeafCount());
      }
      long jsonWork = DisplayMetrics.deepJsonWork(array);
      if (jsonWork > 0) {
        budget.beforeArrayAndJsonWork(0, arrayWork, 0, jsonWork, span, "display");
      } else {
        budget.beforeArrayWork(0, arrayWork, span, "display");
      }
      inner = current.displayText();
    } else if (current instanceof ArrayValue array) {
      budget.beforeArrayWork(0, array.size(), span, "display");
      inner = current.displayText();
    } else if (current instanceof JsonRuntimeValue json) {
      inner = serializeJson(word, json.value(), span, "display");
    } else {
      inner = current.displayText();
    }
    return prefix.append(inner).append("）".repeat(wrapperDepth)).toString();
  }

  private String displayJsonArray(BuiltinWord word, ArrayValue array, SourceSpan span)
      throws RuntimeFailure {
    long work = 0;
    for (RuntimeValue element : array.elements()) {
      JsonValue json = ((JsonRuntimeValue) element).value();
      long outputBytes = json.metrics().serializedUtf8Bytes();
      if (outputBytes > JsonLimits.OUTPUT_UTF8_BYTES) {
        throw jsonOutputLimit(word, span, outputBytes);
      }
      work = saturatedAdd(work, saturatedAdd(outputBytes, jsonTraversalUnits(json)));
    }
    budget.beforeArrayAndJsonWork(0, array.size(), 0, work, span, "display");
    var text = new StringBuilder().append('【');
    for (int index = 0; index < array.size(); index++) {
      if (index > 0) {
        text.append('、');
      }
      JsonValue json = ((JsonRuntimeValue) array.elements().get(index)).value();
      try {
        text.append(JsonCodec.serialize(json));
      } catch (JsonWriteException failure) {
        throw new IllegalStateException(
            "preflighted JSON array element was not serializable", failure);
      }
    }
    return text.append('】').toString();
  }

  private byte[] newline(BuiltinWord word, SourceSpan span, BoundedOutput target)
      throws RuntimeFailure {
    byte[] bytes = {'\n'};
    target.write(bytes, word.canonicalName(), span);
    boolean error = target == errorOutput;
    effect = (error ? "console.error:" : "console.output:") + UPPER_HEX.formatHex(bytes);
    return error ? new byte[0] : bytes;
  }

  private byte[] concatenate(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int firstIndex = stack.size() - 2;
    String first = ((StringValue) stack.get(firstIndex)).value();
    String second = ((StringValue) stack.get(firstIndex + 1)).value();
    long firstBytes = Utf8Length.measureUpTo(first, StringLimits.MAX_UTF8_BYTES).bytes();
    long secondBytes = Utf8Length.measureUpTo(second, StringLimits.MAX_UTF8_BYTES).bytes();
    long observed = firstBytes + secondBytes;
    if (observed > StringLimits.MAX_UTF8_BYTES) {
      throw stringUtf8Limit(word, span, observed);
    }
    StringValue result = new StringValue(first + second);
    stack.removeLast();
    stack.set(firstIndex, result);
    return new byte[0];
  }

  private static byte[] compareStrings(ArrayList<RuntimeValue> stack) {
    int firstIndex = stack.size() - 2;
    String first = ((StringValue) stack.get(firstIndex)).value();
    String second = ((StringValue) stack.get(firstIndex + 1)).value();
    int comparison = Integer.signum(UnicodeText.compareScalars(first, second));
    stack.removeLast();
    stack.set(firstIndex, new IntegerValue(BigInteger.valueOf(comparison)));
    return new byte[0];
  }

  private byte[] mapStringCase(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span, boolean upper)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    String input = ((StringValue) stack.get(index)).value();
    String mapped =
        upper ? IcuUnicodeAdapter.toUpperCase(input) : IcuUnicodeAdapter.toLowerCase(input);
    long observed = Utf8Length.measureUpTo(mapped, Long.MAX_VALUE).bytes();
    if (observed > StringLimits.MAX_UTF8_BYTES) {
      throw stringUtf8Limit(word, span, observed);
    }
    stack.set(index, new StringValue(mapped));
    return new byte[0];
  }

  private static byte[] compareStringsIgnoringCase(ArrayList<RuntimeValue> stack) {
    int firstIndex = stack.size() - 2;
    String first = IcuUnicodeAdapter.foldCase(((StringValue) stack.get(firstIndex)).value());
    String second = IcuUnicodeAdapter.foldCase(((StringValue) stack.get(firstIndex + 1)).value());
    int comparison = Integer.signum(UnicodeText.compareScalars(first, second));
    stack.removeLast();
    stack.set(firstIndex, new IntegerValue(BigInteger.valueOf(comparison)));
    return new byte[0];
  }

  private static byte[] trimString(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    String input = ((StringValue) stack.get(index)).value();
    stack.set(index, new StringValue(UnicodeText.trimUnicodeWhitespace(input)));
    return new byte[0];
  }

  private static byte[] stringLength(ArrayList<RuntimeValue> stack, boolean grapheme) {
    int index = stack.size() - 1;
    UnicodeText text = UnicodeText.of(((StringValue) stack.get(index)).value());
    int length = grapheme ? text.graphemeCount() : text.codePointCount();
    stack.set(index, new IntegerValue(BigInteger.valueOf(length)));
    return new byte[0];
  }

  private byte[] stringGet(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span, boolean grapheme)
      throws RuntimeFailure {
    int stringIndex = stack.size() - 2;
    UnicodeText text = UnicodeText.of(((StringValue) stack.get(stringIndex)).value());
    BigInteger requested = ((IntegerValue) stack.get(stringIndex + 1)).value();
    int length = grapheme ? text.graphemeCount() : text.codePointCount();
    int index = checkedStringIndex(word, requested, length, grapheme, span);
    String result = grapheme ? text.graphemeAt(index) : text.codePointAt(index);
    stack.removeLast();
    stack.set(stringIndex, new CharacterValue(result));
    return new byte[0];
  }

  private byte[] stringSlice(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span, boolean grapheme)
      throws RuntimeFailure {
    int stringIndex = stack.size() - 3;
    UnicodeText text = UnicodeText.of(((StringValue) stack.get(stringIndex)).value());
    BigInteger start = ((IntegerValue) stack.get(stringIndex + 1)).value();
    BigInteger end = ((IntegerValue) stack.get(stringIndex + 2)).value();
    int length = grapheme ? text.graphemeCount() : text.codePointCount();
    int[] range = checkedStringRange(word, start, end, length, grapheme, span);
    String result =
        grapheme ? text.graphemeSlice(range[0], range[1]) : text.codePointSlice(range[0], range[1]);
    stack.removeLast();
    stack.removeLast();
    stack.set(stringIndex, new StringValue(result));
    return new byte[0];
  }

  private static byte[] stringFind(ArrayList<RuntimeValue> stack, boolean grapheme) {
    int stringIndex = stack.size() - 2;
    UnicodeText text = UnicodeText.of(((StringValue) stack.get(stringIndex)).value());
    String needle = ((StringValue) stack.get(stringIndex + 1)).value();
    int found =
        grapheme ? text.findAtGraphemeBoundary(needle) : text.findAtCodePointBoundary(needle);
    stack.removeLast();
    stack.set(stringIndex, new IntegerValue(BigInteger.valueOf(found)));
    return new byte[0];
  }

  private byte[] replaceString(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int targetIndex = stack.size() - 3;
    String target = ((StringValue) stack.get(targetIndex)).value();
    String search = ((StringValue) stack.get(targetIndex + 1)).value();
    String replacement = ((StringValue) stack.get(targetIndex + 2)).value();
    if (search.isEmpty()) {
      throw emptyTextFailure(word, span, target, false);
    }

    UnicodeText text = UnicodeText.of(target);
    int matches = text.countNonOverlappingGraphemeMatches(search);
    long targetBytes = Utf8Length.measureUpTo(target, StringLimits.MAX_UTF8_BYTES).bytes();
    long searchBytes = Utf8Length.measureUpTo(search, StringLimits.MAX_UTF8_BYTES).bytes();
    long replacementBytes =
        Utf8Length.measureUpTo(replacement, StringLimits.MAX_UTF8_BYTES).bytes();
    long observed = targetBytes + (long) matches * (replacementBytes - searchBytes);
    if (observed > StringLimits.MAX_UTF8_BYTES) {
      throw stringUtf8Limit(word, span, observed);
    }

    StringValue result = new StringValue(text.replaceAtGraphemeBoundaries(search, replacement));
    stack.removeLast();
    stack.removeLast();
    stack.set(targetIndex, result);
    return new byte[0];
  }

  private byte[] splitString(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int targetIndex = stack.size() - 2;
    String target = ((StringValue) stack.get(targetIndex)).value();
    String delimiter = ((StringValue) stack.get(targetIndex + 1)).value();
    if (delimiter.isEmpty()) {
      throw emptyTextFailure(word, span, target, true);
    }

    UnicodeText text = UnicodeText.of(target);
    int resultLength = text.countNonOverlappingGraphemeMatches(delimiter) + 1;
    if (resultLength > ArrayLimits.MAX_LENGTH) {
      throw arrayLengthLimit(span, resultLength, "split");
    }
    budget.beforeArrayWork(resultLength, resultLength, span, "split");
    var elements =
        text.splitAtGraphemeBoundaries(delimiter, resultLength).stream()
            .map(part -> (RuntimeValue) new StringValue(part))
            .toList();
    ArrayValue result = new ArrayValue(jp.bsb.stdlib.ScalarType.STRING, elements);
    stack.removeLast();
    stack.set(targetIndex, result);
    return new byte[0];
  }

  private static byte[] numericToString(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    stack.set(index, new StringValue(stack.get(index).displayText()));
    return new byte[0];
  }

  private byte[] stringToInteger(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    String input = ((StringValue) stack.get(index)).value();
    if (!isIntegerText(input)) {
      throw invalidNumericText(word, span, input, false);
    }
    int digitCount = input.charAt(0) == '-' ? input.length() - 1 : input.length();
    checkNumericTextDigitLimit(word, span, input, digitCount, false);
    stack.set(index, new IntegerValue(new BigInteger(input)));
    return new byte[0];
  }

  private byte[] stringToDecimal(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    String input = ((StringValue) stack.get(index)).value();
    DecimalLexemeAnalyzer.Result analysis =
        DecimalLexemeAnalyzer.analyze(input, RuntimeLimits.DECIMAL_ABSOLUTE_SCALE);
    if (!(analysis instanceof Valid valid) || valid.kind() != DecimalLexemeAnalyzer.Kind.DECIMAL) {
      throw invalidNumericText(word, span, input, true);
    }
    checkNumericTextDigitLimit(word, span, input, valid.inputDigits(), true);
    checkDecimalTextScale(word, span, input, "rawScale", valid.rawScale());
    checkDecimalTextScale(word, span, input, "normalizedScale", valid.normalizedScale());

    BigInteger coefficient = new BigInteger(valid.normalizedCoefficientDigits());
    if (valid.negative()) {
      coefficient = coefficient.negate();
    }
    stack.set(index, new DecimalValue(coefficient, Math.toIntExact(valid.normalizedScale())));
    return new byte[0];
  }

  private RuntimeFailure invalidNumericText(
      BuiltinWord word, SourceSpan span, String input, boolean decimal) {
    String preview = diagnosticTextPreview(input);
    return new RuntimeFailure(
        Diagnostic.builder(
                decimal
                    ? DiagnosticCode.E_DECIMAL_TEXT_INVALID
                    : DiagnosticCode.E_INTEGER_TEXT_INVALID,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("inputPreview", preview)
            .field("inputCodePoints", Integer.toString(input.codePointCount(0, input.length())))
            .expected(decimal ? "小数点または指数部を持つASCII小数表記" : "-?(0|[1-9][0-9]*)")
            .actual(input.isEmpty() ? "空文字列" : preview)
            .fix(numericTextFix(input, decimal))
            .build());
  }

  private void checkDecimalTextScale(
      BuiltinWord word, SourceSpan span, String input, String metric, long scale)
      throws RuntimeFailure {
    long observed = Math.abs(scale);
    if (observed <= RuntimeLimits.DECIMAL_ABSOLUTE_SCALE) {
      return;
    }
    String preview = diagnosticTextPreview(input);
    throw new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_DECIMAL_SCALE_LIMIT,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("metric", metric)
            .field("inputPreview", preview)
            .field("inputCodePoints", Integer.toString(input.codePointCount(0, input.length())))
            .limit("decimalScale", RuntimeLimits.DECIMAL_ABSOLUTE_SCALE, observed)
            .expected("スケール絶対値" + RuntimeLimits.DECIMAL_ABSOLUTE_SCALE + "以下")
            .actual(Long.toString(observed))
            .fix("指数の絶対値または小数桁を減らしてください")
            .build());
  }

  private void checkNumericTextDigitLimit(
      BuiltinWord word, SourceSpan span, String input, int digitCount, boolean decimal)
      throws RuntimeFailure {
    if (digitCount <= MAXIMUM_NUMERIC_TEXT_DIGITS) {
      return;
    }
    throw new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_NUMERIC_TEXT_DIGIT_LIMIT,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("numericType", decimal ? "小数" : "整数")
            .field("inputCodePoints", Integer.toString(input.codePointCount(0, input.length())))
            .limit("numericTextDigits", MAXIMUM_NUMERIC_TEXT_DIGITS, digitCount)
            .expected("数字" + MAXIMUM_NUMERIC_TEXT_DIGITS + "桁以下")
            .actual(Integer.toString(digitCount))
            .fix("入力の桁数を" + MAXIMUM_NUMERIC_TEXT_DIGITS + "以下にしてください")
            .build());
  }

  private static boolean isIntegerText(String input) {
    int start = input.startsWith("-") ? 1 : 0;
    int digits = input.length() - start;
    if (digits < 1) {
      return false;
    }
    char first = input.charAt(start);
    if (first == '0') {
      return digits == 1;
    }
    if (first < '1' || first > '9') {
      return false;
    }
    return asciiDigits(input, start + 1, input.length());
  }

  private static boolean asciiDigits(String input, int start, int end) {
    for (int index = start; index < end; index++) {
      char character = input.charAt(index);
      if (character < '0' || character > '9') {
        return false;
      }
    }
    return true;
  }

  private static String numericTextFix(String input, boolean decimal) {
    if (input.startsWith("+")) {
      return "先頭の+を除いてください";
    }
    if (!input.equals(input.strip())) {
      return "前後の空白を除いてください";
    }
    int start = input.startsWith("-") ? 1 : 0;
    if (input.length() > start + 1 && input.charAt(start) == '0') {
      return "先頭0を除いてください";
    }
    return decimal ? "1.0または1e0のような小数表記にしてください" : "ASCII数字による整数表記にしてください";
  }

  private byte[] regexPredicate(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span, boolean fullMatch)
      throws RuntimeFailure {
    int inputIndex = stack.size() - 2;
    String input = ((StringValue) stack.get(inputIndex)).value();
    RegexValue regex = (RegexValue) stack.get(inputIndex + 1);
    beforeRegexMatch(word, input, regex, span);
    boolean result =
        fullMatch ? regex.program().matchesEntire(input) : regex.program().containsMatch(input);
    stack.removeLast();
    stack.set(inputIndex, new BooleanValue(result));
    return new byte[0];
  }

  private byte[] regexFirst(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int inputIndex = stack.size() - 2;
    String input = ((StringValue) stack.get(inputIndex)).value();
    RegexValue regex = (RegexValue) stack.get(inputIndex + 1);
    beforeRegexMatch(word, input, regex, span);
    RegexMatch match =
        regex.program().firstMatch(input).orElseThrow(() -> regexNoMatch(word, span, input, regex));
    stack.removeLast();
    stack.set(inputIndex, new StringValue(match.entire()));
    return new byte[0];
  }

  private byte[] regexNamedGroup(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int inputIndex = stack.size() - 3;
    String input = ((StringValue) stack.get(inputIndex)).value();
    RegexValue regex = (RegexValue) stack.get(inputIndex + 1);
    String group = ((StringValue) stack.get(inputIndex + 2)).value();
    if (!regex.program().namedCaptures().contains(group)) {
      throw regexGroupNotFound(word, span, regex, group);
    }
    beforeRegexMatch(word, input, regex, span);
    RegexMatch match =
        regex.program().firstMatch(input).orElseThrow(() -> regexNoMatch(word, span, input, regex));
    String captured =
        match
            .namedCaptures()
            .get(group)
            .orElseThrow(() -> regexGroupUnmatched(word, span, input, regex, group));
    stack.removeLast();
    stack.removeLast();
    stack.set(inputIndex, new StringValue(captured));
    return new byte[0];
  }

  private byte[] regexReplace(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int inputIndex = stack.size() - 3;
    String input = ((StringValue) stack.get(inputIndex)).value();
    RegexValue regex = (RegexValue) stack.get(inputIndex + 1);
    String templateSource = ((StringValue) stack.get(inputIndex + 2)).value();
    RegexReplacementTemplate template;
    try {
      template =
          RegexReplacementTemplate.parse(
              templateSource, regex.program().captureCount(), regex.program().namedCaptures());
    } catch (RegexReplacementTemplate.InvalidTemplate invalid) {
      throw regexReplacementTemplate(word, span, invalid);
    }

    beforeRegexMatch(word, input, regex, span);
    long resultBytes = 0;
    RegexMatchCursor measuring = regex.program().matchCursor(input);
    while (measuring.advance()) {
      resultBytes = addResultUtf8Bytes(resultBytes, measuring.textBeforeMatch());
      if (resultBytes > StringLimits.MAX_UTF8_BYTES) {
        throw stringUtf8Limit(word, span, resultBytes);
      }
      resultBytes +=
          template.measureExpansion(measuring.match(), StringLimits.MAX_UTF8_BYTES - resultBytes);
      if (resultBytes > StringLimits.MAX_UTF8_BYTES) {
        throw stringUtf8Limit(word, span, resultBytes);
      }
    }
    resultBytes = addResultUtf8Bytes(resultBytes, measuring.textAfterMatches());
    if (resultBytes > StringLimits.MAX_UTF8_BYTES) {
      throw stringUtf8Limit(word, span, resultBytes);
    }

    var result = new StringBuilder();
    RegexMatchCursor replacing = regex.program().matchCursor(input);
    while (replacing.advance()) {
      result.append(replacing.textBeforeMatch());
      template.appendExpansion(result, replacing.match());
    }
    result.append(replacing.textAfterMatches());
    stack.removeLast();
    stack.removeLast();
    stack.set(inputIndex, new StringValue(result.toString()));
    return new byte[0];
  }

  private byte[] regexSplit(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int inputIndex = stack.size() - 2;
    String input = ((StringValue) stack.get(inputIndex)).value();
    RegexValue regex = (RegexValue) stack.get(inputIndex + 1);
    beforeRegexMatch(word, input, regex, span);

    int resultLength = 1;
    RegexMatchCursor counting = regex.program().matchCursor(input);
    while (counting.advance()) {
      resultLength++;
      if (resultLength > ArrayLimits.MAX_LENGTH) {
        throw arrayLengthLimit(span, resultLength, "regexSplit");
      }
    }
    budget.beforeArrayWork(resultLength, resultLength, span, "regexSplit");

    var elements = new ArrayList<RuntimeValue>(resultLength);
    RegexMatchCursor splitting = regex.program().matchCursor(input);
    while (splitting.advance()) {
      elements.add(new StringValue(splitting.textBeforeMatch()));
    }
    elements.add(new StringValue(splitting.textAfterMatches()));
    stack.removeLast();
    stack.set(inputIndex, new ArrayValue(ScalarType.STRING, elements));
    return new byte[0];
  }

  private void beforeRegexMatch(BuiltinWord word, String input, RegexValue regex, SourceSpan span)
      throws RuntimeFailure {
    budget.beforeRegexWork(
        regex.program().instructionCount(),
        input.codePointCount(0, input.length()),
        span,
        word.canonicalName());
  }

  private static long addResultUtf8Bytes(long used, String text) {
    long remaining =
        Math.max(0, StringLimits.MAX_UTF8_BYTES - Math.min(used, StringLimits.MAX_UTF8_BYTES));
    return used + Utf8Length.measureUpTo(text, remaining).bytes();
  }

  private RuntimeFailure regexReplacementTemplate(
      BuiltinWord word, SourceSpan span, RegexReplacementTemplate.InvalidTemplate invalid) {
    String available = String.join(",", invalid.availableNames());
    String fix;
    if (!invalid.availableNames().isEmpty()) {
      fix = "${" + invalid.availableNames().getFirst() + "}を使用してください";
    } else if (invalid.groupCount() > 0) {
      fix = "$1を使用してください";
    } else {
      fix = "$$または$0を使用してください";
    }
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_REGEX_REPLACEMENT_TEMPLATE,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("templateOffset", Integer.toString(invalid.templateOffset()))
            .field("reference", diagnosticTextPreview(invalid.reference()))
            .field("available", available)
            .field("groupCount", Integer.toString(invalid.groupCount()))
            .expected("存在するキャプチャ参照")
            .actual(diagnosticTextPreview(invalid.expression()))
            .fix(fix)
            .build());
  }

  private RuntimeFailure regexNoMatch(
      BuiltinWord word, SourceSpan span, String input, RegexValue regex) {
    String inputPreview = diagnosticTextPreview(input);
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_REGEX_NO_MATCH,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("inputPreview", inputPreview)
            .field("patternPreview", diagnosticTextPreview(regex.rawPattern()))
            .expected("一致する入力")
            .actual(input.isEmpty() ? "空文字列" : inputPreview)
            .fix("パターンまたは入力を確認してください")
            .build());
  }

  private RuntimeFailure regexGroupNotFound(
      BuiltinWord word, SourceSpan span, RegexValue regex, String group) {
    String available = String.join(",", regex.program().namedCaptures());
    String fix =
        regex.program().namedCaptures().isEmpty()
            ? "パターンへ名前付きキャプチャを追加してください"
            : regex.program().namedCaptures().getFirst() + "を指定してください";
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_REGEX_GROUP_NOT_FOUND,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("group", group)
            .field("available", available)
            .expected("存在する名前付きキャプチャ")
            .actual(group.isEmpty() ? "空文字列" : diagnosticTextPreview(group))
            .fix(fix)
            .build());
  }

  private RuntimeFailure regexGroupUnmatched(
      BuiltinWord word, SourceSpan span, String input, RegexValue regex, String group) {
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_REGEX_GROUP_UNMATCHED,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("group", group)
            .field("inputPreview", diagnosticTextPreview(input))
            .field("patternPreview", diagnosticTextPreview(regex.rawPattern()))
            .expected("参加した名前付きキャプチャ")
            .actual(group)
            .fix("一致した選択枝で必ず参加する群を指定してください")
            .build());
  }

  private RuntimeFailure emptyTextFailure(
      BuiltinWord word, SourceSpan span, String target, boolean delimiter) {
    return new RuntimeFailure(
        Diagnostic.builder(
                delimiter ? DiagnosticCode.E_EMPTY_DELIMITER : DiagnosticCode.E_EMPTY_SEARCH_TEXT,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("targetPreview", diagnosticTextPreview(target))
            .expected(delimiter ? "non-empty delimiter" : "non-empty search text")
            .actual("空文字列")
            .fix(delimiter ? "区切り文字列を1書記素以上にしてください" : "検索文字列を1書記素以上にしてください")
            .build());
  }

  private static String diagnosticTextPreview(String value) {
    int codePoints = value.codePointCount(0, value.length());
    String preview = value;
    if (codePoints > 64) {
      int headEnd = value.offsetByCodePoints(0, 32);
      int tailStart = value.offsetByCodePoints(0, codePoints - 16);
      preview =
          value.substring(0, headEnd)
              + "…(+"
              + (codePoints - 48)
              + ")"
              + value.substring(tailStart);
    }
    return ArrayValue.escapeLiteral(preview, false);
  }

  private int checkedStringIndex(
      BuiltinWord word, BigInteger index, int length, boolean grapheme, SourceSpan span)
      throws RuntimeFailure {
    if (index.signum() >= 0 && index.compareTo(BigInteger.valueOf(length)) < 0) {
      return index.intValueExact();
    }
    String last = Integer.toString(length - 1);
    throw new RuntimeFailure(
        Diagnostic.builder(
                grapheme
                    ? DiagnosticCode.E_STRING_INDEX_OUT_OF_BOUNDS
                    : DiagnosticCode.E_CODE_POINT_INDEX_OUT_OF_BOUNDS,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("unit", grapheme ? "grapheme" : "codePoint")
            .field("index", index.toString())
            .field("length", Integer.toString(length))
            .field("valid", "0.." + last)
            .expected("0以上" + length + "未満")
            .actual(index.toString())
            .fix(grapheme ? "0から" + last + "の位置を指定してください" : "0から" + last + "のコードポイント位置を指定してください")
            .build());
  }

  private int[] checkedStringRange(
      BuiltinWord word,
      BigInteger start,
      BigInteger end,
      int length,
      boolean grapheme,
      SourceSpan span)
      throws RuntimeFailure {
    BigInteger maximum = BigInteger.valueOf(length);
    if (start.signum() >= 0 && start.compareTo(end) <= 0 && end.compareTo(maximum) <= 0) {
      return new int[] {start.intValueExact(), end.intValueExact()};
    }
    String fix;
    if (start.compareTo(end) > 0) {
      fix = "開始を終了以下にしてください";
    } else if (end.compareTo(maximum) > 0) {
      fix = "終了を" + length + "以下にしてください";
    } else {
      fix = "開始を0以上にしてください";
    }
    throw new RuntimeFailure(
        Diagnostic.builder(
                grapheme
                    ? DiagnosticCode.E_STRING_RANGE_OUT_OF_BOUNDS
                    : DiagnosticCode.E_CODE_POINT_RANGE_OUT_OF_BOUNDS,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("unit", grapheme ? "grapheme" : "codePoint")
            .field("start", start.toString())
            .field("end", end.toString())
            .field("length", Integer.toString(length))
            .expected("0 <= 開始 <= 終了 <= " + length)
            .actual("[" + start + ',' + end + ")")
            .fix(fix)
            .build());
  }

  private RuntimeFailure stringUtf8Limit(BuiltinWord word, SourceSpan span, long observed) {
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_STRING_UTF8_LIMIT,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .limit("stringUtf8Bytes", StringLimits.MAX_UTF8_BYTES, observed)
            .expected(StringLimits.MAX_UTF8_BYTES + " UTF-8バイト以下")
            .actual(observed + " UTF-8バイト")
            .fix("入力を短くしてください")
            .build());
  }

  private byte[] jsonConstant(
      BuiltinWord word,
      ArrayList<RuntimeValue> stack,
      SourceSpan span,
      JsonValue value,
      String operation)
      throws RuntimeFailure {
    requireAdditionalStackCapacity(word, stack, span, 1);
    budget.beforeJsonWork(1, 0, span, operation);
    stack.add(new JsonRuntimeValue(value));
    return new byte[0];
  }

  private byte[] jsonParse(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    String source = ((StringValue) stack.get(index)).value();
    long inputBytes = Utf8Length.measureUpTo(source, Long.MAX_VALUE).bytes();
    budget.beforeJsonWork(0, inputBytes, span, "parse");
    JsonValue parsed;
    try {
      parsed = JsonCodec.parse(source);
    } catch (JsonParseException failure) {
      throw jsonParseFailure(word, span, failure);
    }
    budget.beforeJsonWork(parsed.metrics().constructionUnits(), 0, span, "parse");
    stack.set(index, new JsonRuntimeValue(parsed));
    return new byte[0];
  }

  private byte[] jsonParseResult(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    String source = ((StringValue) stack.get(index)).value();
    long inputBytes = Utf8Length.measureUpTo(source, Long.MAX_VALUE).bytes();
    budget.beforeJsonWork(0, inputBytes, span, "parse", word.canonicalName());
    JsonValue parsed;
    try {
      parsed = JsonCodec.parse(source);
    } catch (JsonParseException failure) {
      switch (failure.kind()) {
        case SYNTAX, DUPLICATE_KEY -> {
          var value =
              new JsonParseFailureValue(
                  failure.reason(), failure.utf8Offset(), failure.line(), failure.column());
          stack.set(index, ResultValue.failure(RECOVERABLE_JSON_RESULT, value));
          return new byte[0];
        }
        case NUMBER_LIMIT,
            DEPTH_LIMIT,
            NODE_LIMIT,
            ARRAY_LENGTH_LIMIT,
            OBJECT_MEMBER_LIMIT,
            INPUT_LIMIT ->
            throw jsonParseFailure(word, span, failure);
      }
      throw new IllegalStateException("unhandled JSON parse failure kind");
    }
    budget.beforeJsonWork(
        parsed.metrics().constructionUnits(), 0, span, "parse", word.canonicalName());
    stack.set(index, ResultValue.success(RECOVERABLE_JSON_RESULT, new JsonRuntimeValue(parsed)));
    return new byte[0];
  }

  private static byte[] jsonParseFailureKind(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    JsonParseFailureValue failure = (JsonParseFailureValue) stack.get(index);
    stack.set(index, new StringValue(failure.kind()));
    return new byte[0];
  }

  private static byte[] jsonParseFailureOffset(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    JsonParseFailureValue failure = (JsonParseFailureValue) stack.get(index);
    stack.set(index, new IntegerValue(BigInteger.valueOf(failure.utf8Offset())));
    return new byte[0];
  }

  private static byte[] jsonParseFailureLine(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    JsonParseFailureValue failure = (JsonParseFailureValue) stack.get(index);
    stack.set(index, new IntegerValue(BigInteger.valueOf(failure.line())));
    return new byte[0];
  }

  private static byte[] jsonParseFailureColumn(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    JsonParseFailureValue failure = (JsonParseFailureValue) stack.get(index);
    stack.set(index, new IntegerValue(BigInteger.valueOf(failure.column())));
    return new byte[0];
  }

  private static byte[] delimitedTextParseFailureKind(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    DelimitedTextParseFailureValue failure = (DelimitedTextParseFailureValue) stack.get(index);
    stack.set(index, new StringValue(failure.kind()));
    return new byte[0];
  }

  private static byte[] delimitedTextParseFailureOffset(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    DelimitedTextParseFailureValue failure = (DelimitedTextParseFailureValue) stack.get(index);
    stack.set(index, new IntegerValue(BigInteger.valueOf(failure.utf8Offset())));
    return new byte[0];
  }

  private static byte[] delimitedTextParseFailureLine(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    DelimitedTextParseFailureValue failure = (DelimitedTextParseFailureValue) stack.get(index);
    stack.set(index, new IntegerValue(BigInteger.valueOf(failure.line())));
    return new byte[0];
  }

  private static byte[] delimitedTextParseFailureColumn(ArrayList<RuntimeValue> stack) {
    int index = stack.size() - 1;
    DelimitedTextParseFailureValue failure = (DelimitedTextParseFailureValue) stack.get(index);
    stack.set(index, new IntegerValue(BigInteger.valueOf(failure.column())));
    return new byte[0];
  }

  private byte[] delimitedParseTable(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span, char delimiter)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    String source = ((StringValue) stack.get(index)).value();
    Utf8Length.Measurement inputMeasurement =
        Utf8Length.measureUpTo(source, StringLimits.MAX_UTF8_BYTES);
    if (inputMeasurement.exceededMaximum()) {
      throw stringUtf8Limit(word, span, inputMeasurement.bytes());
    }
    long inputCodePoints = source.codePointCount(0, source.length());
    budget.beforeDelimitedTextWork(
        saturatedAdd(inputMeasurement.bytes(), inputCodePoints), span, word.canonicalName());

    DelimitedTextCodec.ParseResult parsed = DelimitedTextCodec.parse(source, delimiter);
    if (parsed.failure().isPresent()) {
      stack.set(index, ResultValue.failure(DELIMITED_TABLE_RESULT, parsed.failure().orElseThrow()));
      return new byte[0];
    }
    if (parsed.rowCount() > ArrayLimits.MAX_LENGTH) {
      throw arrayLengthLimit(span, parsed.rowCount(), "parseDelimitedRows");
    }
    if (parsed.firstOversizedRow() != 0) {
      throw arrayLengthLimit(span, parsed.firstOversizedRowColumns(), "parseDelimitedColumns");
    }
    ArrayNestedLimit.requireAllowed(
        sourcePath,
        span,
        "parseDelimited",
        ValueType.arrayOf(ValueType.STRING),
        parsed.logicalCellCount());
    long constructionUnits = saturatedAdd(parsed.logicalCellCount(), parsed.rowCount());
    budget.beforeArrayWork(constructionUnits, 0, span, "parseDelimited");

    var rows = new ArrayList<RuntimeValue>(parsed.rows().size());
    for (List<String> parsedRow : parsed.rows()) {
      var cells = new ArrayList<RuntimeValue>(parsedRow.size());
      for (String cell : parsedRow) {
        cells.add(new StringValue(cell));
      }
      rows.add(new ArrayValue(ValueType.STRING, cells));
    }
    var table =
        new ArrayValue(ValueType.arrayOf(ValueType.STRING), rows, parsed.logicalCellCount());
    stack.set(index, ResultValue.success(DELIMITED_TABLE_RESULT, table));
    return new byte[0];
  }

  private byte[] delimitedWriteTable(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span, char delimiter)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    ArrayValue table = (ArrayValue) stack.get(index);
    DelimitedTextCodec.WritePlan plan = DelimitedTextCodec.planWrite(table, delimiter);
    if (plan.problem().isPresent()) {
      throw delimitedWriteProblem(word, span, plan.problem().orElseThrow());
    }
    if (plan.outputUtf8Bytes() > StringLimits.MAX_UTF8_BYTES) {
      throw delimitedOutputLimit(word, span, plan.outputUtf8Bytes());
    }
    budget.beforeDelimitedTextWork(
        saturatedAdd(plan.inputUtf8Bytes(), plan.outputUtf8Bytes()), span, word.canonicalName());
    stack.set(index, new StringValue(DelimitedTextCodec.write(plan)));
    return new byte[0];
  }

  private RuntimeFailure delimitedWriteProblem(
      BuiltinWord word, SourceSpan span, DelimitedTextCodec.WriteProblem problem) {
    Diagnostic.Builder builder;
    switch (problem.kind()) {
      case "emptyRow" ->
          builder =
              Diagnostic.builder(
                      DiagnosticCode.E_DELIMITED_TEXT_EMPTY_ROW,
                      Severity.ERROR,
                      DiagnosticStage.RUNTIME,
                      sourcePath,
                      span)
                  .field("word", word.canonicalName())
                  .field("row", Integer.toString(problem.row()))
                  .expected("1セル以上の行")
                  .actual("0セルの行")
                  .fix("空行を削除するか、空文字列セルを1個以上置いてください");
      case "columnCountMismatch" ->
          builder =
              Diagnostic.builder(
                      DiagnosticCode.E_DELIMITED_TEXT_COLUMN_COUNT_MISMATCH,
                      Severity.ERROR,
                      DiagnosticStage.RUNTIME,
                      sourcePath,
                      span)
                  .field("word", word.canonicalName())
                  .field("row", Integer.toString(problem.row()))
                  .field("expectedCount", Integer.toString(problem.expectedCount()))
                  .field("actualCount", Integer.toString(problem.actualCount()))
                  .expected(problem.expectedCount() + "セルの行")
                  .actual(problem.actualCount() + "セルの行")
                  .fix("すべての行のセル数を揃えてください");
      case "nulCharacter" ->
          builder =
              Diagnostic.builder(
                      DiagnosticCode.E_DELIMITED_TEXT_CELL_INVALID,
                      Severity.ERROR,
                      DiagnosticStage.RUNTIME,
                      sourcePath,
                      span)
                  .field("word", word.canonicalName())
                  .field("row", Integer.toString(problem.row()))
                  .field("column", Integer.toString(problem.column()))
                  .field("reason", "nulCharacter")
                  .expected("NULを含まないセル")
                  .actual("規則に適合しないセル")
                  .fix("セルからNUL文字を取り除いてください");
      default -> throw new IllegalStateException("unknown delimited write problem");
    }
    return new RuntimeFailure(builder.build());
  }

  private RuntimeFailure delimitedOutputLimit(BuiltinWord word, SourceSpan span, long observed) {
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_DELIMITED_TEXT_OUTPUT_LIMIT,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .limit("delimitedTextOutputUtf8Bytes", StringLimits.MAX_UTF8_BYTES, observed)
            .expected(StringLimits.MAX_UTF8_BYTES + " UTF-8バイト以下")
            .actual(observed + " UTF-8バイト")
            .fix("入力表またはセルを小さくしてください")
            .build());
  }

  private byte[] jsonSerialize(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    JsonValue json = ((JsonRuntimeValue) stack.get(index)).value();
    stack.set(index, new StringValue(serializeJson(word, json, span, "serialize")));
    return new byte[0];
  }

  private String serializeJson(BuiltinWord word, JsonValue json, SourceSpan span, String operation)
      throws RuntimeFailure {
    long outputBytes = json.metrics().serializedUtf8Bytes();
    if (outputBytes > JsonLimits.OUTPUT_UTF8_BYTES) {
      throw jsonOutputLimit(word, span, outputBytes);
    }
    long work = saturatedAdd(outputBytes, jsonTraversalUnits(json));
    budget.beforeJsonWork(0, work, span, operation, word.canonicalName());
    try {
      return JsonCodec.serialize(json);
    } catch (JsonWriteException failure) {
      if (failure.limit() != JsonLimits.OUTPUT_UTF8_BYTES || failure.observed() != outputBytes) {
        throw new IllegalStateException(
            "JSON writer metrics differ from the runtime preflight", failure);
      }
      throw jsonOutputLimit(word, span, failure.observed());
    }
  }

  private byte[] booleanToJson(ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    boolean value = ((BooleanValue) stack.get(index)).value();
    budget.beforeJsonWork(1, 1, span, "booleanToJson");
    stack.set(index, new JsonRuntimeValue(new JsonBoolean(value)));
    return new byte[0];
  }

  private byte[] integerToJson(ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    BigInteger value = ((IntegerValue) stack.get(index)).value();
    budget.beforeJsonWork(1, 1, span, "integerToJson");
    stack.set(index, new JsonRuntimeValue(new JsonInteger(value)));
    return new byte[0];
  }

  private byte[] decimalToJson(ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    BigDecimal value = ((DecimalValue) stack.get(index)).value();
    budget.beforeJsonWork(1, 1, span, "decimalToJson");
    stack.set(index, new JsonRuntimeValue(new JsonDecimal(value)));
    return new byte[0];
  }

  private byte[] stringToJson(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    String value = ((StringValue) stack.get(index)).value();
    long bytes = Utf8Length.measureUpTo(value, Long.MAX_VALUE).bytes();
    budget.beforeJsonWork(1, saturatedAdd(1, bytes), span, "stringToJson", word.canonicalName());
    stack.set(index, new JsonRuntimeValue(new JsonString(value)));
    return new byte[0];
  }

  private byte[] jsonToBoolean(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    JsonBoolean value = (JsonBoolean) requireJsonKind(word, stack, index, JsonKind.BOOLEAN, span);
    budget.beforeJsonWork(0, 1, span, "jsonToBoolean");
    stack.set(index, new BooleanValue(value.value()));
    return new byte[0];
  }

  private byte[] jsonToInteger(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    JsonInteger value = (JsonInteger) requireJsonKind(word, stack, index, JsonKind.INTEGER, span);
    budget.beforeJsonWork(0, 1, span, "jsonToInteger");
    stack.set(index, new IntegerValue(value.value()));
    return new byte[0];
  }

  private byte[] jsonToDecimal(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    JsonDecimal value = (JsonDecimal) requireJsonKind(word, stack, index, JsonKind.DECIMAL, span);
    budget.beforeJsonWork(0, 1, span, "jsonToDecimal");
    stack.set(index, new DecimalValue(value.value()));
    return new byte[0];
  }

  private byte[] jsonToString(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    JsonString value = (JsonString) requireJsonKind(word, stack, index, JsonKind.STRING, span);
    long bytes = Utf8Length.measureUpTo(value.value(), Long.MAX_VALUE).bytes();
    budget.beforeJsonWork(0, saturatedAdd(1, bytes), span, "jsonToString");
    stack.set(index, new StringValue(value.value()));
    return new byte[0];
  }

  private byte[] jsonPredicate(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span, JsonKind expectedKind)
      throws RuntimeFailure {
    JsonValue value = ((JsonRuntimeValue) stack.getLast()).value();
    requireAdditionalStackCapacity(word, stack, span, 1);
    budget.beforeJsonWork(0, 1, span, "kindPredicate");
    stack.add(new BooleanValue(value.kind() == expectedKind));
    return new byte[0];
  }

  private byte[] jsonToArray(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    JsonArray array = (JsonArray) requireJsonKind(word, stack, index, JsonKind.ARRAY, span);
    budget.beforeArrayAndJsonWork(
        array.size(), 0, 0, saturatedAdd(array.size(), 1), span, "jsonToArray");
    List<RuntimeValue> elements =
        array.elements().stream().map(JsonRuntimeValue::new).map(RuntimeValue.class::cast).toList();
    stack.set(index, new ArrayValue(ScalarType.JSON, elements));
    return new byte[0];
  }

  private byte[] arrayToJson(ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    int index = stack.size() - 1;
    ArrayValue array = (ArrayValue) stack.get(index);
    var structure = new JsonStructure();
    array.elements().stream()
        .map(JsonRuntimeValue.class::cast)
        .map(JsonRuntimeValue::value)
        .forEach(structure::add);
    validateJsonStructure(structure, span, "arrayToJson");
    long units = saturatedAdd(array.size(), 1);
    budget.beforeJsonWork(units, units, span, "arrayToJson");
    List<JsonValue> elements =
        array.elements().stream()
            .map(JsonRuntimeValue.class::cast)
            .map(JsonRuntimeValue::value)
            .toList();
    stack.set(index, new JsonRuntimeValue(new JsonArray(elements)));
    return new byte[0];
  }

  private byte[] jsonArrayLength(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    JsonArray array = (JsonArray) requireJsonKind(word, stack, index, JsonKind.ARRAY, span);
    budget.beforeJsonWork(0, 1, span, "arrayLength");
    stack.set(index, new IntegerValue(BigInteger.valueOf(array.size())));
    return new byte[0];
  }

  private byte[] jsonArrayGet(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int arrayIndex = stack.size() - 2;
    JsonArray array = (JsonArray) requireJsonKind(word, stack, arrayIndex, JsonKind.ARRAY, span);
    BigInteger requested = ((IntegerValue) stack.get(arrayIndex + 1)).value();
    int index = checkedJsonIndex(word, array, requested, "get", span);
    budget.beforeJsonWork(0, 1, span, "arrayGet");
    stack.removeLast();
    stack.set(arrayIndex, new JsonRuntimeValue(array.get(index)));
    return new byte[0];
  }

  private byte[] jsonArraySlice(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int arrayIndex = stack.size() - 3;
    JsonArray array = (JsonArray) requireJsonKind(word, stack, arrayIndex, JsonKind.ARRAY, span);
    BigInteger start = ((IntegerValue) stack.get(arrayIndex + 1)).value();
    BigInteger end = ((IntegerValue) stack.get(arrayIndex + 2)).value();
    int[] range = checkedJsonRange(word, array, start, end, span);
    var structure = new JsonStructure();
    for (int item = range[0]; item < range[1]; item++) {
      structure.add(array.get(item));
    }
    validateJsonStructure(structure, span, "arraySlice");
    long units = saturatedAdd(range[1] - range[0], 1);
    budget.beforeJsonWork(units, units, span, "arraySlice");
    JsonArray result = new JsonArray(array.elements().subList(range[0], range[1]));
    stack.removeLast();
    stack.removeLast();
    stack.set(arrayIndex, new JsonRuntimeValue(result));
    return new byte[0];
  }

  private byte[] jsonArrayReplace(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int arrayIndex = stack.size() - 3;
    JsonArray array = (JsonArray) requireJsonKind(word, stack, arrayIndex, JsonKind.ARRAY, span);
    BigInteger requested = ((IntegerValue) stack.get(arrayIndex + 1)).value();
    int index = checkedJsonIndex(word, array, requested, "replace", span);
    JsonValue replacement = ((JsonRuntimeValue) stack.get(arrayIndex + 2)).value();
    var structure = new JsonStructure();
    for (int item = 0; item < array.size(); item++) {
      structure.add(item == index ? replacement : array.get(item));
    }
    validateJsonStructure(structure, span, "arrayReplace");
    long units = saturatedAdd(array.size(), 1);
    budget.beforeJsonWork(units, units, span, "arrayReplace");
    var elements = new ArrayList<>(array.elements());
    elements.set(index, replacement);
    JsonArray result = new JsonArray(elements);
    stack.removeLast();
    stack.removeLast();
    stack.set(arrayIndex, new JsonRuntimeValue(result));
    return new byte[0];
  }

  private byte[] jsonArrayAppend(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int arrayIndex = stack.size() - 2;
    JsonArray array = (JsonArray) requireJsonKind(word, stack, arrayIndex, JsonKind.ARRAY, span);
    int resultLength = array.size() + 1;
    if (resultLength > JsonLimits.ARRAY_LENGTH) {
      throw jsonArrayLengthLimit(span, resultLength, "append");
    }
    JsonValue appended = ((JsonRuntimeValue) stack.get(arrayIndex + 1)).value();
    var structure = new JsonStructure();
    array.elements().forEach(structure::add);
    structure.add(appended);
    validateJsonStructure(structure, span, "arrayAppend");
    long units = saturatedAdd(resultLength, 1);
    budget.beforeJsonWork(units, units, span, "arrayAppend");
    var elements = new ArrayList<>(array.elements());
    elements.add(appended);
    stack.removeLast();
    stack.set(arrayIndex, new JsonRuntimeValue(new JsonArray(elements)));
    return new byte[0];
  }

  private byte[] jsonObjectSize(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    JsonObject object = (JsonObject) requireJsonKind(word, stack, index, JsonKind.OBJECT, span);
    budget.beforeJsonWork(0, 1, span, "objectSize");
    stack.set(index, new IntegerValue(BigInteger.valueOf(object.size())));
    return new byte[0];
  }

  private byte[] jsonObjectKeys(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int index = stack.size() - 1;
    JsonObject object = (JsonObject) requireJsonKind(word, stack, index, JsonKind.OBJECT, span);
    long work = saturatedAdd(object.size(), 1);
    for (String key : object.keys()) {
      work = saturatedAdd(work, Utf8Length.measureUpTo(key, Long.MAX_VALUE).bytes());
    }
    budget.beforeArrayAndJsonWork(object.size(), 0, 0, work, span, "objectKeys");
    List<RuntimeValue> keys =
        object.keys().stream().map(StringValue::new).map(RuntimeValue.class::cast).toList();
    stack.set(index, new ArrayValue(ScalarType.STRING, keys));
    return new byte[0];
  }

  private byte[] jsonObjectContainsKey(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    int objectIndex = stack.size() - 2;
    JsonObject object =
        (JsonObject) requireJsonKind(word, stack, objectIndex, JsonKind.OBJECT, span);
    String key = ((StringValue) stack.get(objectIndex + 1)).value();
    budget.beforeJsonWork(0, 1, span, "objectContainsKey");
    stack.set(objectIndex + 1, new BooleanValue(object.containsKey(key)));
    return new byte[0];
  }

  private byte[] jsonObjectGetRequired(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    int objectIndex = stack.size() - 2;
    JsonObject object =
        (JsonObject) requireJsonKind(word, stack, objectIndex, JsonKind.OBJECT, span);
    String key = ((StringValue) stack.get(objectIndex + 1)).value();
    JsonValue result =
        object.find(key).orElseThrow(() -> jsonKeyNotFound(word, span, key, object.size()));
    budget.beforeJsonWork(0, 1, span, "objectGetRequired");
    stack.removeLast();
    stack.set(objectIndex, new JsonRuntimeValue(result));
    return new byte[0];
  }

  private byte[] jsonObjectGetOptional(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    int objectIndex = stack.size() - 2;
    JsonObject object =
        (JsonObject) requireJsonKind(word, stack, objectIndex, JsonKind.OBJECT, span);
    String key = ((StringValue) stack.get(objectIndex + 1)).value();
    budget.beforeJsonWork(0, 1, span, "objectGetOptional");
    OptionalValue result =
        object
            .find(key)
            .<OptionalValue>map(value -> OptionalValue.present(new JsonRuntimeValue(value)))
            .orElseGet(() -> OptionalValue.absent(ValueType.JSON));
    stack.removeLast();
    stack.set(objectIndex, result);
    return new byte[0];
  }

  private byte[] jsonPointerGetOptional(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    int jsonIndex = stack.size() - 2;
    JsonValue root = ((JsonRuntimeValue) stack.get(jsonIndex)).value();
    String pointer = ((StringValue) stack.get(jsonIndex + 1)).value();
    List<String> tokens;
    try {
      tokens = JsonPointer.parse(pointer);
    } catch (JsonPointer.SyntaxException failure) {
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_JSON_POINTER_SYNTAX,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word.canonicalName())
              .field("reason", failure.getMessage())
              .field("offset", Integer.toString(pointer.codePointCount(0, failure.offset())))
              .expected("空文字列または/で始まるRFC 6901 JSON Pointer")
              .actual("不正なJSON Pointer")
              .fix("~は~0、/は~1で表してください")
              .build());
    }
    long work =
        saturatedAdd(Utf8Length.measureUpTo(pointer, Long.MAX_VALUE).bytes(), tokens.size());
    budget.beforeJsonWork(0, work, span, "pointerGetOptional", word.canonicalName());
    OptionalValue result =
        JsonPointer.resolve(root, tokens)
            .<OptionalValue>map(value -> OptionalValue.present(new JsonRuntimeValue(value)))
            .orElseGet(() -> OptionalValue.absent(ValueType.JSON));
    stack.removeLast();
    stack.set(jsonIndex, result);
    return new byte[0];
  }

  private byte[] jsonObjectBuild(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int keysIndex = stack.size() - 2;
    ArrayValue keys = (ArrayValue) stack.get(keysIndex);
    ArrayValue values = (ArrayValue) stack.get(keysIndex + 1);
    if (keys.size() != values.size()) {
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_JSON_OBJECT_BUILD_LENGTH_MISMATCH,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word.canonicalName())
              .field("keyCount", Integer.toString(keys.size()))
              .field("valueCount", Integer.toString(values.size()))
              .expected("同じ要素数")
              .actual(keys.size() + "キー、" + values.size() + "値")
              .fix("キー配列と値配列の要素数を一致させてください")
              .build());
    }
    if (keys.size() > JsonLimits.OBJECT_MEMBERS) {
      throw jsonObjectMemberLimit(span, keys.size(), "objectBuild");
    }
    var seen = new java.util.HashSet<String>();
    var members = new ArrayList<JsonMember>(keys.size());
    long work = saturatedAdd(keys.size(), 1);
    for (int index = 0; index < keys.size(); index++) {
      String key = ((StringValue) keys.elements().get(index)).value();
      if (!seen.add(key)) {
        throw new RuntimeFailure(
            Diagnostic.builder(
                    DiagnosticCode.E_JSON_DUPLICATE_KEY,
                    Severity.ERROR,
                    DiagnosticStage.RUNTIME,
                    sourcePath,
                    span)
                .field("word", word.canonicalName())
                .field("keyPreview", diagnosticTextPreview(key))
                .field("operation", "objectBuild")
                .expected("重複しないキー")
                .actual("重複キー")
                .fix("キー配列の重複を除いてください")
                .build());
      }
      work = saturatedAdd(work, Utf8Length.measureUpTo(key, Long.MAX_VALUE).bytes());
      members.add(new JsonMember(key, ((JsonRuntimeValue) values.elements().get(index)).value()));
    }
    var structure = new JsonStructure();
    members.forEach(member -> structure.add(member.value()));
    validateJsonStructure(structure, span, "objectBuild");
    budget.beforeJsonWork(
        saturatedAdd(keys.size(), 1), work, span, "objectBuild", word.canonicalName());
    stack.removeLast();
    stack.set(keysIndex, new JsonRuntimeValue(new JsonObject(members)));
    return new byte[0];
  }

  private byte[] jsonObjectSet(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int objectIndex = stack.size() - 3;
    JsonObject object =
        (JsonObject) requireJsonKind(word, stack, objectIndex, JsonKind.OBJECT, span);
    String key = ((StringValue) stack.get(objectIndex + 1)).value();
    JsonValue value = ((JsonRuntimeValue) stack.get(objectIndex + 2)).value();
    boolean replacing = object.containsKey(key);
    int resultSize = replacing ? object.size() : object.size() + 1;
    if (resultSize > JsonLimits.OBJECT_MEMBERS) {
      throw jsonObjectMemberLimit(span, resultSize, "set");
    }
    var structure = new JsonStructure();
    for (JsonMember member : object.members()) {
      structure.add(member.key().equals(key) ? value : member.value());
    }
    if (!replacing) {
      structure.add(value);
    }
    validateJsonStructure(structure, span, "objectSet");
    long units = saturatedAdd(resultSize, 1);
    budget.beforeJsonWork(units, units, span, "objectSet");
    var members = new ArrayList<JsonMember>(resultSize);
    for (JsonMember member : object.members()) {
      members.add(member.key().equals(key) ? new JsonMember(key, value) : member);
    }
    if (!replacing) {
      members.add(new JsonMember(key, value));
    }
    stack.removeLast();
    stack.removeLast();
    stack.set(objectIndex, new JsonRuntimeValue(new JsonObject(members)));
    return new byte[0];
  }

  private byte[] jsonObjectDelete(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int objectIndex = stack.size() - 2;
    JsonObject object =
        (JsonObject) requireJsonKind(word, stack, objectIndex, JsonKind.OBJECT, span);
    String key = ((StringValue) stack.get(objectIndex + 1)).value();
    int resultSize = object.containsKey(key) ? object.size() - 1 : object.size();
    var structure = new JsonStructure();
    object.members().stream()
        .filter(member -> !member.key().equals(key))
        .map(JsonMember::value)
        .forEach(structure::add);
    validateJsonStructure(structure, span, "objectDelete");
    long units = saturatedAdd(resultSize, 1);
    budget.beforeJsonWork(units, units, span, "objectDelete");
    List<JsonMember> members =
        object.members().stream().filter(member -> !member.key().equals(key)).toList();
    stack.removeLast();
    stack.set(objectIndex, new JsonRuntimeValue(new JsonObject(members)));
    return new byte[0];
  }

  private JsonValue requireJsonKind(
      BuiltinWord word,
      ArrayList<RuntimeValue> stack,
      int index,
      JsonKind expected,
      SourceSpan span)
      throws RuntimeFailure {
    JsonValue value = ((JsonRuntimeValue) stack.get(index)).value();
    if (value.kind() != expected) {
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_JSON_KIND_MISMATCH,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("word", word.canonicalName())
              .field("expectedKind", expected.diagnosticName())
              .field("actualKind", value.kind().diagnosticName())
              .expected(expected.diagnosticName())
              .actual(value.kind().diagnosticName())
              .fix("JSON値種別を判定してから必要な操作を呼び出してください")
              .build());
    }
    return value;
  }

  private int checkedJsonIndex(
      BuiltinWord word, JsonArray array, BigInteger index, String operation, SourceSpan span)
      throws RuntimeFailure {
    if (index.signum() < 0 || index.compareTo(BigInteger.valueOf(array.size())) >= 0) {
      String validRange = "[0," + array.size() + ")";
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_JSON_INDEX_OUT_OF_BOUNDS,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("operation", operation)
              .field("word", word.canonicalName())
              .field("index", index.toString())
              .field("length", Integer.toString(array.size()))
              .field("validRange", validRange)
              .expected("0以上" + array.size() + "未満")
              .actual(index.toString())
              .fix(array.size() == 0 ? "空でないJSON配列を使用してください" : "有効範囲の添字を指定してください")
              .build());
    }
    return index.intValueExact();
  }

  private int[] checkedJsonRange(
      BuiltinWord word, JsonArray array, BigInteger start, BigInteger end, SourceSpan span)
      throws RuntimeFailure {
    BigInteger length = BigInteger.valueOf(array.size());
    boolean valid = start.signum() >= 0 && start.compareTo(end) <= 0 && end.compareTo(length) <= 0;
    if (!valid) {
      String condition = "0<=start<=end<=" + array.size();
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_JSON_RANGE_OUT_OF_BOUNDS,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("start", start.toString())
              .field("word", word.canonicalName())
              .field("end", end.toString())
              .field("length", Integer.toString(array.size()))
              .field("validCondition", condition)
              .expected("0 <= 開始 <= 終了 <= " + array.size())
              .actual("[" + start + ',' + end + ")")
              .fix(start.compareTo(end) > 0 ? "開始を終了以下にしてください" : "開始と終了を有効範囲にしてください")
              .build());
    }
    return new int[] {start.intValueExact(), end.intValueExact()};
  }

  private RuntimeFailure jsonParseFailure(
      BuiltinWord word, SourceSpan span, JsonParseException failure) {
    DiagnosticCode code =
        switch (failure.kind()) {
          case SYNTAX -> DiagnosticCode.E_JSON_SYNTAX;
          case DUPLICATE_KEY -> DiagnosticCode.E_JSON_DUPLICATE_KEY;
          case NUMBER_LIMIT -> DiagnosticCode.E_JSON_NUMBER_LIMIT;
          case DEPTH_LIMIT -> DiagnosticCode.E_JSON_DEPTH_LIMIT;
          case NODE_LIMIT -> DiagnosticCode.E_JSON_NODE_LIMIT;
          case ARRAY_LENGTH_LIMIT -> DiagnosticCode.E_JSON_ARRAY_LENGTH_LIMIT;
          case OBJECT_MEMBER_LIMIT -> DiagnosticCode.E_JSON_OBJECT_MEMBER_LIMIT;
          case INPUT_LIMIT -> DiagnosticCode.E_STRING_UTF8_LIMIT;
        };
    var builder =
        Diagnostic.builder(code, Severity.ERROR, DiagnosticStage.RUNTIME, sourcePath, span)
            .field("word", word.canonicalName())
            .field("reason", failure.reason())
            .field("jsonUtf8Offset", Long.toString(failure.utf8Offset()))
            .field("jsonLine", Integer.toString(failure.line()))
            .field("jsonColumn", Integer.toString(failure.column()));
    switch (failure.kind()) {
      case SYNTAX -> {
        builder.expected(failure.expected().orElse("有効なJSON"));
        builder.actual(failure.reason());
        builder.fix("JSON文法と入力内位置を確認してください");
      }
      case DUPLICATE_KEY -> {
        builder.field("keyPreview", "<duplicate>");
        builder.expected("オブジェクト内で一意なキー");
        builder.actual("重複キー");
        builder.fix("重複するキーを削除してください");
      }
      case NUMBER_LIMIT -> {
        long limit = parseExpectedLimit(failure);
        builder.field("metric", failure.reason());
        builder.limit("jsonNumber", limit, saturatedAdd(limit, 1));
        builder.expected(limit + "以下");
        builder.actual((limit + 1) + "以上");
        builder.fix("JSON数値の桁数、精度、指数を減らしてください");
      }
      case DEPTH_LIMIT -> addJsonParseLimit(builder, "jsonDepth", JsonLimits.DEPTH, "入れ子を浅くしてください");
      case NODE_LIMIT ->
          addJsonParseLimit(builder, "jsonNodes", JsonLimits.VALUE_NODES, "JSON値のノード数を減らしてください");
      case ARRAY_LENGTH_LIMIT ->
          addJsonParseLimit(
              builder, "jsonArrayLength", JsonLimits.ARRAY_LENGTH, "JSON配列の要素数を減らしてください");
      case OBJECT_MEMBER_LIMIT ->
          addJsonParseLimit(
              builder, "jsonObjectMembers", JsonLimits.OBJECT_MEMBERS, "JSONオブジェクトのメンバー数を減らしてください");
      case INPUT_LIMIT -> {
        builder.limit(
            "stringUtf8Bytes", JsonLimits.INPUT_UTF8_BYTES, JsonLimits.INPUT_UTF8_BYTES + 1);
        builder.expected(JsonLimits.INPUT_UTF8_BYTES + " UTF-8バイト以下");
        builder.actual((JsonLimits.INPUT_UTF8_BYTES + 1) + " UTF-8バイト以上");
        builder.fix("入力を短くしてください");
      }
    }
    return new RuntimeFailure(builder.build());
  }

  private static void addJsonParseLimit(
      Diagnostic.Builder builder, String name, long limit, String fix) {
    builder.limit(name, limit, saturatedAdd(limit, 1));
    builder.expected(limit + "以下");
    builder.actual((limit + 1) + "以上");
    builder.fix(fix);
  }

  private static long parseExpectedLimit(JsonParseException failure) {
    try {
      return Long.parseLong(failure.expected().orElseThrow());
    } catch (NumberFormatException exception) {
      throw new IllegalStateException(
          "JSON number limit did not expose a numeric limit", exception);
    }
  }

  private RuntimeFailure jsonOutputLimit(BuiltinWord word, SourceSpan span, long observed) {
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_JSON_OUTPUT_LIMIT,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .limit("jsonOutputUtf8Bytes", JsonLimits.OUTPUT_UTF8_BYTES, observed)
            .expected(JsonLimits.OUTPUT_UTF8_BYTES + " UTF-8バイト以下")
            .actual(observed + " UTF-8バイト")
            .fix("JSON値または文字列を小さくしてください")
            .build());
  }

  private RuntimeFailure jsonArrayLengthLimit(SourceSpan span, int observed, String operation) {
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_JSON_ARRAY_LENGTH_LIMIT,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("operation", operation)
            .limit("jsonArrayLength", JsonLimits.ARRAY_LENGTH, observed)
            .expected(JsonLimits.ARRAY_LENGTH + "要素以下")
            .actual(observed + "要素")
            .fix("末尾へ追加する前にJSON配列の要素数を減らしてください")
            .build());
  }

  private RuntimeFailure jsonObjectMemberLimit(SourceSpan span, int observed, String operation) {
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_JSON_OBJECT_MEMBER_LIMIT,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("operation", operation)
            .limit("jsonObjectMembers", JsonLimits.OBJECT_MEMBERS, observed)
            .expected(JsonLimits.OBJECT_MEMBERS + "メンバー以下")
            .actual(observed + "メンバー")
            .fix("新しいキーを設定する前にメンバー数を減らしてください")
            .build());
  }

  private RuntimeFailure jsonKeyNotFound(
      BuiltinWord word, SourceSpan span, String key, int memberCount) {
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_JSON_KEY_NOT_FOUND,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("keyPreview", diagnosticTextPreview(key))
            .field("memberCount", Integer.toString(memberCount))
            .expected("存在するキー")
            .actual("キー不在")
            .fix("JSONオブジェクトにキーがあるで先に確認してください")
            .build());
  }

  private void validateJsonStructure(JsonStructure structure, SourceSpan span, String operation)
      throws RuntimeFailure {
    if (structure.nodes > JsonLimits.VALUE_NODES) {
      throw jsonStructureLimit(
          DiagnosticCode.E_JSON_NODE_LIMIT,
          "jsonNodes",
          JsonLimits.VALUE_NODES,
          structure.nodes,
          operation,
          span,
          "結果JSONの値ノード数を減らしてください");
    }
    if (structure.depth > JsonLimits.DEPTH) {
      throw jsonStructureLimit(
          DiagnosticCode.E_JSON_DEPTH_LIMIT,
          "jsonDepth",
          JsonLimits.DEPTH,
          structure.depth,
          operation,
          span,
          "結果JSONの入れ子を浅くしてください");
    }
  }

  private RuntimeFailure jsonStructureLimit(
      DiagnosticCode code,
      String limitName,
      long limit,
      long observed,
      String operation,
      SourceSpan span,
      String fix) {
    return new RuntimeFailure(
        Diagnostic.builder(code, Severity.ERROR, DiagnosticStage.RUNTIME, sourcePath, span)
            .field("operation", operation)
            .limit(limitName, limit, observed)
            .expected(limit + "以下")
            .actual(Long.toString(observed))
            .fix(fix)
            .build());
  }

  private static long jsonTraversalUnits(JsonValue value) {
    return saturatedAdd(value.metrics().nodeCount(), value.metrics().containerReferences());
  }

  private static long saturatedAdd(long left, long right) {
    return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
  }

  private static final class JsonStructure {
    private long nodes = 1;
    private int depth = 1;

    private void add(JsonValue value) {
      nodes = saturatedAdd(nodes, value.metrics().nodeCount());
      depth = Math.max(depth, saturatedAddDepth(value.metrics().depth()));
    }

    private static int saturatedAddDepth(int childDepth) {
      return childDepth == Integer.MAX_VALUE ? Integer.MAX_VALUE : childDepth + 1;
    }
  }

  private byte[] arrayLength(ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    int arrayIndex = stack.size() - 1;
    ArrayValue array = (ArrayValue) stack.get(arrayIndex);
    budget.beforeArrayWork(0, 1, span, "length");
    stack.set(arrayIndex, new IntegerValue(BigInteger.valueOf(array.size())));
    return new byte[0];
  }

  private byte[] arrayGet(ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    int arrayIndex = stack.size() - 2;
    ArrayValue array = (ArrayValue) stack.get(arrayIndex);
    BigInteger indexValue = ((IntegerValue) stack.get(arrayIndex + 1)).value();
    int index = checkedIndex(array, indexValue, "get", span);
    budget.beforeArrayWork(0, 1, span, "get");
    RuntimeValue result = array.get(index);
    stack.removeLast();
    stack.set(arrayIndex, result);
    return new byte[0];
  }

  private byte[] arraySlice(ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    int arrayIndex = stack.size() - 3;
    ArrayValue array = (ArrayValue) stack.get(arrayIndex);
    BigInteger startValue = ((IntegerValue) stack.get(arrayIndex + 1)).value();
    BigInteger endValue = ((IntegerValue) stack.get(arrayIndex + 2)).value();
    int[] range = checkedRange(array, startValue, endValue, span);
    int resultLength = range[1] - range[0];
    long logicalLeafCount = ArrayNestedLimit.slice(array, range[0], range[1]);
    ArrayNestedLimit.requireAllowed(
        sourcePath, span, "slice", array.elementType(), logicalLeafCount);
    budget.beforeArrayWork(0, resultLength, span, "slice");
    ArrayValue result =
        new ArrayValue(
            array.elementType(), array.elements().subList(range[0], range[1]), logicalLeafCount);
    stack.removeLast();
    stack.removeLast();
    stack.set(arrayIndex, result);
    return new byte[0];
  }

  private byte[] arrayReplace(ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int arrayIndex = stack.size() - 3;
    ArrayValue array = (ArrayValue) stack.get(arrayIndex);
    BigInteger indexValue = ((IntegerValue) stack.get(arrayIndex + 1)).value();
    RuntimeValue replacement = stack.get(arrayIndex + 2);
    int index = checkedIndex(array, indexValue, "replace", span);
    long logicalLeafCount = ArrayNestedLimit.replace(array, index, replacement);
    ArrayNestedLimit.requireAllowed(
        sourcePath, span, "replace", array.elementType(), logicalLeafCount);
    budget.beforeArrayWork(1, 1, span, "replace");
    var elements = new ArrayList<>(array.elements());
    elements.set(index, replacement);
    ArrayValue result = new ArrayValue(array.elementType(), elements, logicalLeafCount);
    stack.removeLast();
    stack.removeLast();
    stack.set(arrayIndex, result);
    return new byte[0];
  }

  private byte[] arrayAppend(ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    int arrayIndex = stack.size() - 2;
    ArrayValue array = (ArrayValue) stack.get(arrayIndex);
    RuntimeValue element = stack.get(arrayIndex + 1);
    int resultLength = array.size() + 1;
    if (resultLength > ArrayLimits.MAX_LENGTH) {
      throw arrayLengthLimit(span, resultLength, "append");
    }
    long logicalLeafCount = ArrayNestedLimit.append(array, element);
    ArrayNestedLimit.requireAllowed(
        sourcePath, span, "append", array.elementType(), logicalLeafCount);
    budget.beforeArrayWork(1, 1, span, "append");
    var elements = new ArrayList<RuntimeValue>(resultLength);
    elements.addAll(array.elements());
    elements.add(element);
    ArrayValue result = new ArrayValue(array.elementType(), elements, logicalLeafCount);
    stack.removeLast();
    stack.set(arrayIndex, result);
    return new byte[0];
  }

  private byte[] arrayConcat(ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    int firstIndex = stack.size() - 2;
    ArrayValue first = (ArrayValue) stack.get(firstIndex);
    ArrayValue second = (ArrayValue) stack.get(firstIndex + 1);
    long resultLength = (long) first.size() + second.size();
    if (resultLength > ArrayLimits.MAX_LENGTH) {
      throw arrayLengthLimit(span, resultLength, "concat");
    }
    long logicalLeafCount = ArrayNestedLimit.concatenate(first, second);
    ArrayNestedLimit.requireAllowed(
        sourcePath, span, "concat", first.elementType(), logicalLeafCount);
    budget.beforeArrayWork(second.size(), second.size(), span, "concat");
    ArrayValue result = first.concatenated(second, logicalLeafCount);
    stack.removeLast();
    stack.set(firstIndex, result);
    return new byte[0];
  }

  private byte[] arrayPrepend(ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int arrayIndex = stack.size() - 2;
    ArrayValue array = (ArrayValue) stack.get(arrayIndex);
    RuntimeValue element = stack.get(arrayIndex + 1);
    long resultLength = (long) array.size() + 1;
    if (resultLength > ArrayLimits.MAX_LENGTH) {
      throw arrayLengthLimit(span, resultLength, "prepend");
    }
    long logicalLeafCount = ArrayNestedLimit.prepend(array, element);
    ArrayNestedLimit.requireAllowed(
        sourcePath, span, "prepend", array.elementType(), logicalLeafCount);
    budget.beforeArrayWork(1, 1, span, "prepend");
    ArrayValue result = array.prepended(element, logicalLeafCount);
    stack.removeLast();
    stack.set(arrayIndex, result);
    return new byte[0];
  }

  private byte[] arrayReverse(ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int arrayIndex = stack.size() - 1;
    ArrayValue array = (ArrayValue) stack.get(arrayIndex);
    budget.beforeArrayWork(array.size(), array.size(), span, "reverse");
    stack.set(arrayIndex, array.reversed());
    return new byte[0];
  }

  private byte[] arraySearch(
      BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span, boolean contains)
      throws RuntimeFailure {
    int arrayIndex = stack.size() - 2;
    ArrayValue array = (ArrayValue) stack.get(arrayIndex);
    RuntimeValue sought = stack.get(arrayIndex + 1);
    int found = -1;
    long arrayWork = 0;
    long jsonWork = 0;
    long byteSequenceWork = 0;
    for (int index = 0; index < array.size(); index++) {
      EqualityMeasurement measurement = measureEquality(array.get(index), sought);
      arrayWork = saturatedAdd(arrayWork, saturatedAdd(1, measurement.arrayWork()));
      jsonWork = saturatedAdd(jsonWork, measurement.jsonWork());
      byteSequenceWork = saturatedAdd(byteSequenceWork, measurement.byteSequenceWork());
      if (measurement.equal()) {
        found = index;
        break;
      }
    }
    budget.beforeArrayJsonAndByteSequenceWork(
        arrayWork, jsonWork, byteSequenceWork, span, word.canonicalName());
    RuntimeValue result =
        contains ? new BooleanValue(found >= 0) : new IntegerValue(BigInteger.valueOf(found));
    stack.removeLast();
    stack.set(arrayIndex, result);
    return new byte[0];
  }

  private EqualityMeasurement measureEquality(RuntimeValue first, RuntimeValue second) {
    RuntimeValue left = first;
    RuntimeValue right = second;
    while (true) {
      if (left instanceof OptionalValue leftOptional
          && right instanceof OptionalValue rightOptional) {
        if (leftOptional.isPresent() != rightOptional.isPresent()) {
          return EqualityMeasurement.notEqual();
        }
        if (!leftOptional.isPresent()) {
          return EqualityMeasurement.equalMeasurement();
        }
        left = leftOptional.value().orElseThrow();
        right = rightOptional.value().orElseThrow();
      } else if (left instanceof ResultValue leftResult
          && right instanceof ResultValue rightResult) {
        if (leftResult.state() != rightResult.state()) {
          return EqualityMeasurement.notEqual();
        }
        left = leftResult.value();
        right = rightResult.value();
      } else {
        break;
      }
    }
    if (left instanceof ArrayValue leftArray && right instanceof ArrayValue rightArray) {
      return measureArrayEquality(leftArray, rightArray);
    }
    if (left instanceof ByteSequenceValue leftBytes
        && right instanceof ByteSequenceValue rightBytes) {
      if (leftBytes.length() != rightBytes.length()) {
        return EqualityMeasurement.notEqual();
      }
      for (int index = 0; index < leftBytes.length(); index++) {
        if (leftBytes.byteAt(index) != rightBytes.byteAt(index)) {
          return new EqualityMeasurement(false, 0, 0, index + 1L);
        }
      }
      return new EqualityMeasurement(true, 0, 0, leftBytes.length());
    }
    if (left instanceof JsonRuntimeValue leftJson && right instanceof JsonRuntimeValue rightJson) {
      long work =
          saturatedAdd(jsonTraversalUnits(leftJson.value()), jsonTraversalUnits(rightJson.value()));
      return new EqualityMeasurement(left.equals(right), 0, work, 0);
    }
    return new EqualityMeasurement(left.equals(right), 0, 0, 0);
  }

  private EqualityMeasurement measureArrayEquality(ArrayValue first, ArrayValue second) {
    if (first.size() != second.size()) {
      return EqualityMeasurement.notEqual();
    }
    if (first.elementType() instanceof jp.bsb.stdlib.ArrayType rowType) {
      for (int index = 0; index < first.size(); index++) {
        if (((ArrayValue) first.get(index)).size() != ((ArrayValue) second.get(index)).size()) {
          return EqualityMeasurement.notEqual();
        }
      }
      long arrayWork = saturatedAdd(first.size(), first.logicalLeafCount());
      long jsonWork =
          rowType.elementType() == ScalarType.JSON
              ? DisplayMetrics.nestedJsonEqualityWork(first, second)
              : 0;
      return new EqualityMeasurement(first.equals(second), arrayWork, jsonWork, 0);
    }
    if (first.elementType().isOptional() || first.elementType().isResult()) {
      long arrayWork = first.size();
      if (ValueType.arrayConstructorDepth(first.elementType()) > 0) {
        arrayWork = saturatedAdd(arrayWork, first.logicalLeafCount());
      }
      return new EqualityMeasurement(
          first.equals(second), arrayWork, DisplayMetrics.deepJsonEqualityWork(first, second), 0);
    }
    long arrayWork = 0;
    long jsonWork = 0;
    for (int index = 0; index < first.size(); index++) {
      arrayWork = saturatedAdd(arrayWork, 1);
      RuntimeValue left = first.get(index);
      RuntimeValue right = second.get(index);
      if (left instanceof JsonRuntimeValue leftJson
          && right instanceof JsonRuntimeValue rightJson) {
        jsonWork =
            saturatedAdd(
                jsonWork,
                saturatedAdd(
                    jsonTraversalUnits(leftJson.value()), jsonTraversalUnits(rightJson.value())));
      }
      if (!left.equals(right)) {
        return new EqualityMeasurement(false, arrayWork, jsonWork, 0);
      }
    }
    return new EqualityMeasurement(true, arrayWork, jsonWork, 0);
  }

  private record EqualityMeasurement(
      boolean equal, long arrayWork, long jsonWork, long byteSequenceWork) {
    private static EqualityMeasurement notEqual() {
      return new EqualityMeasurement(false, 0, 0, 0);
    }

    private static EqualityMeasurement equalMeasurement() {
      return new EqualityMeasurement(true, 0, 0, 0);
    }
  }

  private byte[] arrayIsEmpty(ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int arrayIndex = stack.size() - 1;
    ArrayValue array = (ArrayValue) stack.get(arrayIndex);
    budget.beforeArrayWork(0, 1, span, "isEmpty");
    stack.set(arrayIndex, new BooleanValue(array.size() == 0));
    return new byte[0];
  }

  private byte[] arrayEdgeOptional(ArrayList<RuntimeValue> stack, SourceSpan span, boolean first)
      throws RuntimeFailure {
    int arrayIndex = stack.size() - 1;
    ArrayValue array = (ArrayValue) stack.get(arrayIndex);
    budget.beforeArrayWork(0, 1, span, first ? "firstOptional" : "lastOptional");
    OptionalValue result =
        array.size() == 0
            ? OptionalValue.absent(array.elementType())
            : OptionalValue.present(array.get(first ? 0 : array.size() - 1));
    stack.set(arrayIndex, result);
    return new byte[0];
  }

  private byte[] arrayDeleteEdge(ArrayList<RuntimeValue> stack, SourceSpan span, boolean first)
      throws RuntimeFailure {
    int arrayIndex = stack.size() - 1;
    ArrayValue array = (ArrayValue) stack.get(arrayIndex);
    int start = array.size() == 0 || first ? 0 : array.size() - 1;
    int end = array.size() == 0 || !first ? array.size() : 1;
    int resultLength = array.size() == 0 ? 0 : array.size() - 1;
    long logicalLeafCount = ArrayNestedLimit.deleteRange(array, start, end);
    budget.beforeArrayWork(0, resultLength, span, first ? "deleteFirst" : "deleteLast");
    stack.set(arrayIndex, array.deletedRange(start, end, logicalLeafCount));
    return new byte[0];
  }

  private byte[] arrayDeleteRange(ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int arrayIndex = stack.size() - 3;
    ArrayValue array = (ArrayValue) stack.get(arrayIndex);
    BigInteger startValue = ((IntegerValue) stack.get(arrayIndex + 1)).value();
    BigInteger endValue = ((IntegerValue) stack.get(arrayIndex + 2)).value();
    int[] range = checkedRange(array, startValue, endValue, span);
    int resultLength = array.size() - (range[1] - range[0]);
    long logicalLeafCount = ArrayNestedLimit.deleteRange(array, range[0], range[1]);
    budget.beforeArrayWork(0, resultLength, span, "deleteRange");
    ArrayValue result = array.deletedRange(range[0], range[1], logicalLeafCount);
    stack.removeLast();
    stack.removeLast();
    stack.set(arrayIndex, result);
    return new byte[0];
  }

  private byte[] arrayCount(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int arrayIndex = stack.size() - 2;
    ArrayValue array = (ArrayValue) stack.get(arrayIndex);
    RuntimeValue sought = stack.get(arrayIndex + 1);
    int count = 0;
    long arrayWork = 0;
    long jsonWork = 0;
    long byteSequenceWork = 0;
    for (RuntimeValue element : array.elements()) {
      EqualityMeasurement measurement = measureEquality(element, sought);
      arrayWork = saturatedAdd(arrayWork, saturatedAdd(1, measurement.arrayWork()));
      jsonWork = saturatedAdd(jsonWork, measurement.jsonWork());
      byteSequenceWork = saturatedAdd(byteSequenceWork, measurement.byteSequenceWork());
      if (measurement.equal()) {
        count++;
      }
    }
    budget.beforeArrayJsonAndByteSequenceWork(
        arrayWork, jsonWork, byteSequenceWork, span, word.canonicalName());
    stack.removeLast();
    stack.set(arrayIndex, new IntegerValue(BigInteger.valueOf(count)));
    return new byte[0];
  }

  private byte[] arrayInsert(ArrayList<RuntimeValue> stack, SourceSpan span) throws RuntimeFailure {
    int arrayIndex = stack.size() - 3;
    ArrayValue array = (ArrayValue) stack.get(arrayIndex);
    BigInteger indexValue = ((IntegerValue) stack.get(arrayIndex + 1)).value();
    RuntimeValue element = stack.get(arrayIndex + 2);
    int index = checkedArrayPosition(array, indexValue, true, "insert", span);
    long resultLength = (long) array.size() + 1;
    if (resultLength > ArrayLimits.MAX_LENGTH) {
      throw arrayLengthLimit(span, resultLength, "insert");
    }
    long logicalLeafCount = ArrayNestedLimit.append(array, element);
    ArrayNestedLimit.requireAllowed(
        sourcePath, span, "insert", array.elementType(), logicalLeafCount);
    budget.beforeArrayWork(1, 1, span, "insert");
    ArrayValue result = array.inserted(index, element, logicalLeafCount);
    stack.removeLast();
    stack.removeLast();
    stack.set(arrayIndex, result);
    return new byte[0];
  }

  private byte[] arrayDeleteAt(ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int arrayIndex = stack.size() - 2;
    ArrayValue array = (ArrayValue) stack.get(arrayIndex);
    BigInteger indexValue = ((IntegerValue) stack.get(arrayIndex + 1)).value();
    int index = checkedArrayPosition(array, indexValue, false, "deleteAt", span);
    int resultLength = array.size() - 1;
    long logicalLeafCount = ArrayNestedLimit.deleteRange(array, index, index + 1);
    budget.beforeArrayWork(0, resultLength, span, "deleteAt");
    stack.removeLast();
    stack.set(arrayIndex, array.deletedRange(index, index + 1, logicalLeafCount));
    return new byte[0];
  }

  private byte[] arrayFindFrom(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int arrayIndex = stack.size() - 3;
    ArrayValue array = (ArrayValue) stack.get(arrayIndex);
    RuntimeValue sought = stack.get(arrayIndex + 1);
    BigInteger startValue = ((IntegerValue) stack.get(arrayIndex + 2)).value();
    int start = checkedArrayPosition(array, startValue, true, "findFrom", span);
    int found = -1;
    long arrayWork = 0;
    long jsonWork = 0;
    long byteSequenceWork = 0;
    for (int index = start; index < array.size(); index++) {
      EqualityMeasurement measurement = measureEquality(array.get(index), sought);
      arrayWork = saturatedAdd(arrayWork, saturatedAdd(1, measurement.arrayWork()));
      jsonWork = saturatedAdd(jsonWork, measurement.jsonWork());
      byteSequenceWork = saturatedAdd(byteSequenceWork, measurement.byteSequenceWork());
      if (measurement.equal()) {
        found = index;
        break;
      }
    }
    budget.beforeArrayJsonAndByteSequenceWork(
        arrayWork, jsonWork, byteSequenceWork, span, word.canonicalName());
    stack.removeLast();
    stack.removeLast();
    stack.set(arrayIndex, new IntegerValue(BigInteger.valueOf(found)));
    return new byte[0];
  }

  private byte[] arrayUnique(BuiltinWord word, ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int arrayIndex = stack.size() - 1;
    ArrayValue array = (ArrayValue) stack.get(arrayIndex);
    var unique = new ArrayList<RuntimeValue>(array.size());
    long arrayWork = 0;
    long jsonWork = 0;
    long byteSequenceWork = 0;
    long remainingArrayWork =
        ArrayLimits.MAX_ELEMENT_OPERATION_UNITS - budget.arrayElementOperationUnits();
    uniqueLoop:
    for (RuntimeValue candidate : array.elements()) {
      boolean duplicate = false;
      for (RuntimeValue retained : unique) {
        EqualityMeasurement measurement = measureEquality(retained, candidate);
        arrayWork = saturatedAdd(arrayWork, saturatedAdd(1, measurement.arrayWork()));
        jsonWork = saturatedAdd(jsonWork, measurement.jsonWork());
        byteSequenceWork = saturatedAdd(byteSequenceWork, measurement.byteSequenceWork());
        if (arrayWork > remainingArrayWork) {
          break uniqueLoop;
        }
        if (measurement.equal()) {
          duplicate = true;
          break;
        }
      }
      if (!duplicate) {
        unique.add(candidate);
      }
    }
    budget.beforeArrayJsonAndByteSequenceWork(
        arrayWork, jsonWork, byteSequenceWork, span, word.canonicalName());
    long logicalLeafCount = ArrayNestedLimit.measure(array.elementType(), unique);
    stack.set(arrayIndex, new ArrayValue(array.elementType(), unique, logicalLeafCount));
    return new byte[0];
  }

  private byte[] arrayRepeatValue(ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int valueIndex = stack.size() - 2;
    RuntimeValue value = stack.get(valueIndex);
    BigInteger countValue = ((IntegerValue) stack.get(valueIndex + 1)).value();
    if (countValue.signum() < 0
        || countValue.compareTo(BigInteger.valueOf(ArrayLimits.MAX_LENGTH)) > 0) {
      throw requestedArrayLengthOutOfRange(span, countValue, "repeatValue");
    }
    int count = countValue.intValueExact();
    long logicalLeafCount = ArrayNestedLimit.repeat(value, count);
    ArrayNestedLimit.requireAllowed(
        sourcePath, span, "repeatValue", value.type(), logicalLeafCount);
    budget.beforeArrayWork(count, count, span, "repeatValue");
    ArrayValue result =
        new ArrayValue(value.type(), java.util.Collections.nCopies(count, value), logicalLeafCount);
    stack.removeLast();
    stack.set(valueIndex, result);
    return new byte[0];
  }

  private byte[] arrayGetOptional(ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int arrayIndex = stack.size() - 2;
    ArrayValue array = (ArrayValue) stack.get(arrayIndex);
    BigInteger index = ((IntegerValue) stack.get(arrayIndex + 1)).value();
    budget.beforeArrayWork(0, 1, span, "getOptional");
    OptionalValue result =
        index.signum() < 0 || index.compareTo(BigInteger.valueOf(array.size())) >= 0
            ? OptionalValue.absent(array.elementType())
            : OptionalValue.present(array.get(index.intValueExact()));
    stack.removeLast();
    stack.set(arrayIndex, result);
    return new byte[0];
  }

  private byte[] arrayColumnOptional(ArrayList<RuntimeValue> stack, SourceSpan span)
      throws RuntimeFailure {
    int arrayIndex = stack.size() - 2;
    ArrayValue table = (ArrayValue) stack.get(arrayIndex);
    BigInteger column = ((IntegerValue) stack.get(arrayIndex + 1)).value();
    var rowType = (ArrayType) table.elementType();
    boolean present = table.size() > 0 && column.signum() >= 0;
    int columnIndex = -1;
    if (present && column.compareTo(BigInteger.valueOf(ArrayLimits.MAX_LENGTH)) < 0) {
      columnIndex = column.intValueExact();
      for (RuntimeValue value : table.elements()) {
        if (((ArrayValue) value).size() <= columnIndex) {
          present = false;
          break;
        }
      }
    } else {
      present = false;
    }
    budget.beforeArrayWork(present ? table.size() : 0, table.size(), span, "columnOptional");
    OptionalValue result;
    if (!present) {
      result = OptionalValue.absent(rowType);
    } else {
      var elements = new ArrayList<RuntimeValue>(table.size());
      for (RuntimeValue value : table.elements()) {
        elements.add(((ArrayValue) value).get(columnIndex));
      }
      result = OptionalValue.present(new ArrayValue(rowType.elementType(), elements));
    }
    stack.removeLast();
    stack.set(arrayIndex, result);
    return new byte[0];
  }

  private int checkedArrayPosition(
      ArrayValue array, BigInteger index, boolean allowEnd, String operation, SourceSpan span)
      throws RuntimeFailure {
    int upper = allowEnd ? array.size() : array.size() - 1;
    if (index.signum() < 0 || index.compareTo(BigInteger.valueOf(upper)) > 0) {
      String validRange = allowEnd ? "[0," + array.size() + "]" : "[0," + array.size() + ")";
      String expected = allowEnd ? "0以上" + array.size() + "以下" : "0以上" + array.size() + "未満";
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_ARRAY_INDEX_OUT_OF_BOUNDS,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("operation", operation)
              .field("index", index.toString())
              .field("length", Integer.toString(array.size()))
              .field("validRange", validRange)
              .expected(expected)
              .actual(index.toString())
              .fix(allowEnd ? "位置を0から配列の長さまでにしてください" : "有効な要素位置を指定してください")
              .build());
    }
    return index.intValueExact();
  }

  private RuntimeFailure requestedArrayLengthOutOfRange(
      SourceSpan span, BigInteger observed, String operation) {
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_ARRAY_LENGTH_LIMIT,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("operation", operation)
            .limit("arrayLength", Integer.toString(ArrayLimits.MAX_LENGTH), observed.toString())
            .expected("0以上" + ArrayLimits.MAX_LENGTH + "要素以下")
            .actual(observed + "要素")
            .fix("個数を0から" + ArrayLimits.MAX_LENGTH + "までにしてください。")
            .build());
  }

  private int checkedIndex(ArrayValue array, BigInteger index, String operation, SourceSpan span)
      throws RuntimeFailure {
    if (index.signum() < 0 || index.compareTo(BigInteger.valueOf(array.size())) >= 0) {
      String validRange = "[0," + array.size() + ")";
      String fix =
          array.size() == 0 ? "空でない配列を使用してください" : "添字を0から" + (array.size() - 1) + "の範囲にしてください";
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_ARRAY_INDEX_OUT_OF_BOUNDS,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("operation", operation)
              .field("index", index.toString())
              .field("length", Integer.toString(array.size()))
              .field("validRange", validRange)
              .expected("0以上" + array.size() + "未満")
              .actual(index.toString())
              .fix(fix)
              .build());
    }
    return index.intValueExact();
  }

  private int[] checkedRange(ArrayValue array, BigInteger start, BigInteger end, SourceSpan span)
      throws RuntimeFailure {
    BigInteger length = BigInteger.valueOf(array.size());
    boolean valid = start.signum() >= 0 && start.compareTo(end) <= 0 && end.compareTo(length) <= 0;
    if (!valid) {
      String condition = "0<=start<=end<=" + array.size();
      String fix = start.compareTo(end) > 0 ? "開始を終了以下にしてください" : "開始と終了を有効範囲にしてください";
      throw new RuntimeFailure(
          Diagnostic.builder(
                  DiagnosticCode.E_ARRAY_RANGE_OUT_OF_BOUNDS,
                  Severity.ERROR,
                  DiagnosticStage.RUNTIME,
                  sourcePath,
                  span)
              .field("start", start.toString())
              .field("end", end.toString())
              .field("length", Integer.toString(array.size()))
              .field("validCondition", condition)
              .expected("0 <= 開始 <= 終了 <= " + array.size())
              .actual("[" + start + ',' + end + ")")
              .fix(fix)
              .build());
    }
    return new int[] {start.intValueExact(), end.intValueExact()};
  }

  private RuntimeFailure arrayLengthLimit(SourceSpan span, long observed, String operation) {
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_ARRAY_LENGTH_LIMIT,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("operation", operation)
            .limit("arrayLength", ArrayLimits.MAX_LENGTH, observed)
            .expected(ArrayLimits.MAX_LENGTH + "要素以下")
            .actual(observed + "要素")
            .fix(
                operation.equals("split") || operation.equals("regexSplit")
                    ? "区切りの出現数を減らしてください。"
                    : operation.startsWith("parseDelimited")
                        ? "CSV/TSVの行数または列数を減らしてください。"
                        : "末尾へ追加する前に配列の要素数を減らしてください。")
            .build());
  }

  private RuntimeFailure integerLimit(BuiltinWord word, SourceSpan span, long observed) {
    Diagnostic diagnostic =
        Diagnostic.builder(
                DiagnosticCode.E_INTEGER_RESULT_LIMIT,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .limit("integerDigits", RuntimeLimits.INTEGER_DIGITS, observed)
            .expected(RuntimeLimits.INTEGER_DIGITS + "桁以下")
            .actual(observed + "桁")
            .fix("整数演算の入力または回数を減らしてください。")
            .build();
    return new RuntimeFailure(diagnostic);
  }

  private DecimalValue checkedDecimalResult(BuiltinWord word, SourceSpan span, BigDecimal rawResult)
      throws RuntimeFailure {
    BigDecimal normalized = DecimalMetrics.normalize(rawResult);
    DecimalMetrics metrics = DecimalMetrics.ofNormalized(normalized);
    if (metrics.precision() > RuntimeLimits.DECIMAL_PRECISION) {
      throw decimalPrecisionLimit(word, span, metrics.precision());
    }
    if (metrics.absoluteScale() > RuntimeLimits.DECIMAL_ABSOLUTE_SCALE) {
      throw decimalScaleLimit(word, span, metrics.absoluteScale());
    }
    return new DecimalValue(normalized);
  }

  private RuntimeFailure divisionByZero(
      BuiltinWord word, SourceSpan span, BigInteger dividend, BigInteger divisor) {
    NumericPreview dividendPreview = NumericPreview.ofInteger(dividend);
    NumericPreview divisorPreview = NumericPreview.ofInteger(divisor);
    var builder =
        Diagnostic.builder(
                DiagnosticCode.E_DIVISION_BY_ZERO,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("dividendPreview", dividendPreview.text())
            .field("divisorPreview", divisorPreview.text())
            .expected("0でない整数除数")
            .actual(divisorPreview.text())
            .fix("除数を0以外にしてください");
    if (dividendPreview.truncated()) {
      builder.field("dividendCodePoints", Integer.toString(dividendPreview.codePoints()));
    }
    if (divisorPreview.truncated()) {
      builder.field("divisorCodePoints", Integer.toString(divisorPreview.codePoints()));
    }
    return new RuntimeFailure(builder.build());
  }

  private void ensureNonzeroDivisor(
      BuiltinWord word,
      SourceSpan span,
      RuntimeValue dividend,
      RuntimeValue divisor,
      BigInteger precision,
      RoundingModeValue rounding)
      throws RuntimeFailure {
    if (asBigDecimal(divisor).signum() != 0) {
      return;
    }
    NumericPreview dividendPreview = NumericPreview.ofValue(dividend);
    NumericPreview divisorPreview = NumericPreview.ofValue(divisor);
    var builder =
        Diagnostic.builder(
                DiagnosticCode.E_DIVISION_BY_ZERO,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("dividendPreview", dividendPreview.text())
            .field("divisorPreview", divisorPreview.text());
    if (precision != null) {
      NumericPreview precisionPreview = NumericPreview.ofInteger(precision);
      builder.field("precision", precisionPreview.text());
      addPreviewLength(builder, "precision", precisionPreview);
    }
    if (rounding != null) {
      builder.field("rounding", rounding.sourceName());
    }
    addPreviewLength(builder, "dividend", dividendPreview);
    addPreviewLength(builder, "divisor", divisorPreview);
    throw new RuntimeFailure(
        builder.expected("0でない数値除数").actual(divisorPreview.text()).fix("除数を0以外にしてください").build());
  }

  private int checkedDivisionPrecision(
      BuiltinWord word,
      SourceSpan span,
      RuntimeValue dividend,
      RuntimeValue divisor,
      BigInteger precision)
      throws RuntimeFailure {
    if (precision.compareTo(BigInteger.valueOf(MINIMUM_DIVISION_PRECISION)) >= 0
        && precision.compareTo(BigInteger.valueOf(MAXIMUM_DIVISION_PRECISION)) <= 0) {
      return precision.intValueExact();
    }
    NumericPreview dividendPreview = NumericPreview.ofValue(dividend);
    NumericPreview divisorPreview = NumericPreview.ofValue(divisor);
    NumericPreview precisionPreview = NumericPreview.ofInteger(precision);
    var builder =
        Diagnostic.builder(
                DiagnosticCode.E_DIVISION_PRECISION_OUT_OF_RANGE,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("dividendPreview", dividendPreview.text())
            .field("divisorPreview", divisorPreview.text())
            .field("precision", precisionPreview.text())
            .field("minimum", Integer.toString(MINIMUM_DIVISION_PRECISION))
            .field("maximum", Integer.toString(MAXIMUM_DIVISION_PRECISION));
    addPreviewLength(builder, "dividend", dividendPreview);
    addPreviewLength(builder, "divisor", divisorPreview);
    addPreviewLength(builder, "precision", precisionPreview);
    throw new RuntimeFailure(
        builder
            .expected(MINIMUM_DIVISION_PRECISION + "以上" + MAXIMUM_DIVISION_PRECISION + "以下")
            .actual(precisionPreview.text())
            .fix("精度を" + MINIMUM_DIVISION_PRECISION + "から" + MAXIMUM_DIVISION_PRECISION + "にしてください")
            .build());
  }

  private RuntimeFailure decimalNotInteger(BuiltinWord word, SourceSpan span, DecimalValue value) {
    NumericPreview preview = NumericPreview.ofValue(value);
    var builder =
        Diagnostic.builder(
                DiagnosticCode.E_DECIMAL_NOT_INTEGER,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("valuePreview", preview.text());
    addPreviewLength(builder, "value", preview);
    return new RuntimeFailure(
        builder
            .expected("小数部が0の小数")
            .actual(preview.text())
            .fix("丸め方法を明示して「整数に丸める」を使用してください")
            .build());
  }

  private static void addPreviewLength(
      Diagnostic.Builder builder, String fieldPrefix, NumericPreview preview) {
    if (preview.truncated()) {
      builder.field(fieldPrefix + "CodePoints", Integer.toString(preview.codePoints()));
    }
  }

  private RuntimeFailure decimalPrecisionLimit(BuiltinWord word, SourceSpan span, long observed) {
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_DECIMAL_RESULT_PRECISION_LIMIT,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("inputPreviewPolicy", "numerics")
            .limit("decimalPrecision", RuntimeLimits.DECIMAL_PRECISION, observed)
            .expected("有効桁" + RuntimeLimits.DECIMAL_PRECISION + "以下")
            .actual(observed + "桁")
            .fix("入力の有効桁または演算回数を減らしてください")
            .build());
  }

  private RuntimeFailure decimalScaleLimit(BuiltinWord word, SourceSpan span, long observed) {
    return new RuntimeFailure(
        Diagnostic.builder(
                DiagnosticCode.E_DECIMAL_SCALE_LIMIT,
                Severity.ERROR,
                DiagnosticStage.RUNTIME,
                sourcePath,
                span)
            .field("word", word.canonicalName())
            .field("inputPreviewPolicy", "numerics")
            .limit("decimalScale", RuntimeLimits.DECIMAL_ABSOLUTE_SCALE, observed)
            .expected("スケール絶対値" + RuntimeLimits.DECIMAL_ABSOLUTE_SCALE + "以下")
            .actual(Long.toString(observed))
            .fix("入力の小数桁または演算回数を減らしてください")
            .build());
  }

  private static int digits(BigInteger value) {
    return value.abs().toString().length();
  }

  private enum Arithmetic {
    ADD,
    SUBTRACT,
    MULTIPLY
  }

  private enum IntegerDivisionResult {
    QUOTIENT,
    REMAINDER,
    BOTH
  }
}
