package jp.bsb.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.json.JsonNull;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ScalarType;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class HttpsHttpRequestRuntimeTest {
  private static final String SOURCE = "https-http-runtime.bsb";
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @Test
  void buildsImmutablePathQueryHeadersAndThreeBodyKinds() throws Exception {
    var stack = stack();
    execute("空のHTTP要求", stack);
    var original = (HttpRequestValue) stack.getFirst();

    stack.add(new StringValue("顧客 一覧/%"));
    execute("HTTP要求に経路を設定する", stack);
    var path = (HttpRequestValue) stack.getFirst();
    assertEquals("", original.path());
    assertEquals("顧客 一覧/%", path.path());
    assertEquals(
        "%E9%A1%A7%E5%AE%A2%20%E4%B8%80%E8%A6%A7/%25",
        HttpRequestSupport.percentEncode(path.path(), true));

    stack.add(new StringValue("tag"));
    stack.add(new StringValue("a"));
    execute("HTTP要求に問い合わせ項目を追加する", stack);
    stack.add(new StringValue("tag"));
    stack.add(new StringValue("b"));
    execute("HTTP要求に問い合わせ項目を追加する", stack);
    assertEquals(
        List.of(
            new HttpRequestValue.QueryItem("tag", "a"), new HttpRequestValue.QueryItem("tag", "b")),
        ((HttpRequestValue) stack.getFirst()).query());

    stack.add(new StringValue("Content-Type"));
    stack.add(new StringValue("text/plain"));
    execute("HTTP要求にヘッダーを設定する", stack);
    stack.add(new StringValue("CONTENT-TYPE"));
    stack.add(new StringValue("application/json"));
    execute("HTTP要求にヘッダーを設定する", stack);
    assertEquals(
        List.of(new HttpRequestValue.Header("content-type", "application/json")),
        ((HttpRequestValue) stack.getFirst()).headers());

    var bytes = ByteSequenceValue.copyOf(new byte[] {0, 1, (byte) 0xff});
    stack.add(bytes);
    execute("HTTP要求にバイト列本文を設定する", stack);
    assertSame(bytes, ((HttpRequestValue) stack.getFirst()).body().orElseThrow().bytes());

    stack.add(new StringValue("日本語"));
    execute("HTTP要求に文字列本文を設定する", stack);
    assertArrayEquals(
        "日本語".getBytes(StandardCharsets.UTF_8),
        ((HttpRequestValue) stack.getFirst()).body().orElseThrow().bytes().copyBytes());

    stack.add(new JsonRuntimeValue(JsonNull.INSTANCE));
    execute("HTTP要求にJSON本文を設定する", stack);
    assertArrayEquals(
        "null".getBytes(StandardCharsets.UTF_8),
        ((HttpRequestValue) stack.getFirst()).body().orElseThrow().bytes().copyBytes());
    assertEquals(1, ((HttpRequestValue) stack.getFirst()).headers().size());
  }

  @Test
  void distinguishesAbsentAndEmptyBodyAndExtractsResponses() throws Exception {
    var request = stack(HttpRequestValue.empty());
    execute("HTTP要求に本文がある", request);
    assertEquals(new BooleanValue(false), request.removeLast());
    request.add(ByteSequenceValue.empty());
    execute("HTTP要求にバイト列本文を設定する", request);
    execute("HTTP要求に本文がある", request);
    assertEquals(new BooleanValue(true), request.removeLast());

    var headers = new LinkedHashMap<String, List<String>>();
    headers.put("Set-Cookie", List.of("a=1", "b=2"));
    var body = ByteSequenceValue.copyOf(new byte[] {0, (byte) 0xff});
    var response = new HttpResponseValue(404, headers, body);
    var status = stack(response);
    execute("HTTP応答から状態コードを取り出す", status);
    assertEquals(new IntegerValue(BigInteger.valueOf(404)), status.getFirst());
    var values = stack(response, new StringValue("SET-COOKIE"));
    execute("HTTP応答からヘッダー値一覧を取り出す", values);
    assertEquals(
        new ArrayValue(ScalarType.STRING, List.of(new StringValue("a=1"), new StringValue("b=2"))),
        values.getFirst());
    var responseBody = stack(response);
    execute("HTTP応答から本文を取り出す", responseBody);
    assertSame(body, responseBody.getFirst());

    var failure = stack(new HttpSendFailureValue("tlsFailure"));
    execute("HTTP送信失敗の種類を取り出す", failure);
    assertEquals(new StringValue("tlsFailure"), failure.getFirst());
  }

  @Test
  void rejectsInvalidMetadataAtomicallyAndKeepsOpaqueTypesClosed() {
    var request = stack(HttpRequestValue.empty(), new StringValue("/absolute"));
    List<RuntimeValue> before = List.copyOf(request);
    RuntimeFailure path =
        assertThrows(RuntimeFailure.class, () -> execute("HTTP要求に経路を設定する", request));
    assertEquals(DiagnosticCode.E_HTTP_PATH_INVALID, path.diagnostic().code());
    assertEquals(before, request);

    var header =
        stack(
            HttpRequestValue.empty(), new StringValue("Authorization"), new StringValue("secret"));
    before = List.copyOf(header);
    RuntimeFailure reserved =
        assertThrows(RuntimeFailure.class, () -> execute("HTTP要求にヘッダーを設定する", header));
    assertEquals(DiagnosticCode.E_HTTP_HEADER_RESERVED, reserved.diagnostic().code());
    assertEquals(before, header);

    assertFalse(ValueType.HTTP_REQUEST.isArrayElementType());
    assertFalse(jp.bsb.stdlib.ValueTypeTraits.isDisplayable(ValueType.HTTP_RESPONSE));
    assertFalse(jp.bsb.stdlib.ValueTypeTraits.isEqualityComparable(ValueType.HTTP_SEND_FAILURE));
    assertEquals("HTTP要求:<redacted>", HttpRequestValue.empty().toString());
    assertTrue(HttpSendFailureValue.KINDS.size() == 9);
  }

  @Test
  void enforcesPathQueryAndHeaderLimitsBeforeReplacingTheRequest() throws Exception {
    var pathAt = stack(HttpRequestValue.empty(), new StringValue("a".repeat(8_192)));
    execute("HTTP要求に経路を設定する", pathAt);
    assertEquals(8_192, ((HttpRequestValue) pathAt.getFirst()).path().length());

    var pathOver = stack(HttpRequestValue.empty(), new StringValue("a".repeat(8_193)));
    List<RuntimeValue> before = List.copyOf(pathOver);
    RuntimeFailure pathFailure =
        assertThrows(RuntimeFailure.class, () -> execute("HTTP要求に経路を設定する", pathOver));
    assertEquals(DiagnosticCode.E_HTTP_PATH_LIMIT, pathFailure.diagnostic().code());
    assertEquals(before, pathOver);

    var queryAt =
        stack(HttpRequestValue.empty(), new StringValue("a".repeat(65_535)), new StringValue(""));
    execute("HTTP要求に問い合わせ項目を追加する", queryAt);
    assertEquals(
        65_536,
        HttpRequestSupport.encodedQueryLength(((HttpRequestValue) queryAt.getFirst()).query()));

    var queryOver =
        stack(HttpRequestValue.empty(), new StringValue("a".repeat(65_536)), new StringValue(""));
    before = List.copyOf(queryOver);
    RuntimeFailure queryFailure =
        assertThrows(RuntimeFailure.class, () -> execute("HTTP要求に問い合わせ項目を追加する", queryOver));
    assertEquals(DiagnosticCode.E_HTTP_QUERY_LIMIT, queryFailure.diagnostic().code());
    assertEquals(before, queryOver);

    var headers = stack(HttpRequestValue.empty());
    for (int index = 0; index < 128; index++) {
      headers.add(new StringValue("X-" + index));
      headers.add(new StringValue(""));
      execute("HTTP要求にヘッダーを設定する", headers);
    }
    headers.add(new StringValue("X-over"));
    headers.add(new StringValue(""));
    before = List.copyOf(headers);
    RuntimeFailure headerFailure =
        assertThrows(RuntimeFailure.class, () -> execute("HTTP要求にヘッダーを設定する", headers));
    assertEquals(DiagnosticCode.E_HTTP_HEADER_LIMIT, headerFailure.diagnostic().code());
    assertEquals(before, headers);
  }

  private static ArrayList<RuntimeValue> stack(RuntimeValue... values) {
    return new ArrayList<>(List.of(values));
  }

  private static void execute(String name, ArrayList<RuntimeValue> stack) throws RuntimeFailure {
    new BuiltinExecutor(
            SOURCE,
            new BoundedOutput(SOURCE, new MemoryOutputSink()),
            new ExecutionBudget(SOURCE, () -> 0L))
        .execute(BuiltinDictionary.find(name).orElseThrow(), stack, SPAN);
  }
}
