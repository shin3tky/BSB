package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.runtime.ConnectionPolicy;
import jp.bsb.runtime.ConnectionResolution;
import jp.bsb.runtime.CredentialReference;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.ExecutionEnvironment;
import jp.bsb.runtime.HttpTransportHeader;
import jp.bsb.runtime.HttpTransportResult;
import jp.bsb.runtime.InputEvent;
import jp.bsb.runtime.MemoryConsoleInput;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.ProgramRunResult;
import jp.bsb.runtime.ProgramRunner;
import jp.bsb.runtime.TraceEvent;
import jp.bsb.runtime.TraceSink;
import org.junit.jupiter.api.Test;

/** HTTPSの中央カタログを公開ソースと参照API連携実行へ接続します。 */
class HttpsCentralConformanceTest {
  private static final Path SAMPLE = Path.of("samples/20-minimal-https.bsb");
  private static final byte[] INPUT = "{\"name\":\"山田 太郎\"}".getBytes(StandardCharsets.UTF_8);
  private static final byte[] RESPONSE =
      "{\"accepted\":true,\"id\":\"C-100\"}".getBytes(StandardCharsets.UTF_8);

  @Test
  void centralCatalogConsumesAllThirtySixIdsAndEveryVariantWithoutOrphans() throws Exception {
    Set<String> consumed = new LinkedHashSet<>();
    var variantKeys = new LinkedHashSet<String>();
    consume(HttpsConformanceData.loadUriVectors(), consumed, variantKeys, "uri");
    consume(HttpsConformanceData.loadHeaderVectors(), consumed, variantKeys, "header");
    consume(HttpsConformanceData.loadRequests(), consumed, variantKeys, "request");
    consume(HttpsConformanceData.loadResponses(), consumed, variantKeys, "response");
    consume(HttpsConformanceData.loadTransports(), consumed, variantKeys, "transport");
    consume(HttpsConformanceData.loadResources(), consumed, variantKeys, "resource");
    consume(HttpsConformanceData.loadDiagnostics(), consumed, variantKeys, "diagnostic");
    consume(HttpsConformanceData.loadTraces(), consumed, variantKeys, "trace");
    consumed.addAll(Set.of("HTTPS-N012", "HTTPS-F002", "HTTPS-F013"));

    Set<String> catalog =
        HttpsConformanceData.loadCatalog().stream()
            .map(HttpsConformanceData.CatalogSpec::id)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    assertEquals(catalog, consumed);
    assertEquals(36, consumed.size());
  }

  @Test
  void everyPublishedSourceHasItsCataloguedStaticOutcome() throws Exception {
    var outcomes = new java.util.LinkedHashMap<String, Boolean>();
    for (var item : HttpsConformanceData.loadCatalog()) {
      if (!item.scope().equals("public") || !item.evidence().endsWith(".bsb")) continue;
      boolean successful =
          outcomes.computeIfAbsent(
              item.evidence(),
              path -> {
                try {
                  return new SourceChecker()
                      .check(path, HttpsConformanceData.resourceBytes(path))
                      .successful();
                } catch (java.io.IOException failure) {
                  throw new java.io.UncheckedIOException(failure);
                }
              });
      assertEquals(item.kind().equals("normal"), successful, item.id());
    }
    assertEquals(
        Set.of(
            "sources/HTTPS-N-methods.bsb",
            "chapter/https-chapter.bsb",
            "sources/HTTPS-F-static.bsb"),
        outcomes.keySet());
  }

  @Test
  void referenceApplicationIsEquivalentWithAndWithoutTrace() throws Exception {
    Observation normal = runReference(false);
    Observation traced = runReference(true);

    assertTrue(normal.result().successful(), normal.result().diagnostics().toString());
    assertArrayEquals(
        "201\napplication/json\n{\"accepted\":true,\"id\":\"C-100\"}\n"
            .getBytes(StandardCharsets.UTF_8),
        normal.output().bytes());
    assertEquals(1, normal.resolverCalls());
    assertEquals(1, normal.transportCalls());
    assertEquals(68, normal.result().httpMetadataConstructionBytes());
    assertEquals(1, normal.result().httpSendCalls());
    assertEquals(INPUT.length, normal.result().httpRequestAttemptBytes());
    assertEquals(RESPONSE.length, normal.result().httpResponseReceivedBytes());
    assertEquivalent(normal.result(), traced.result());
    assertArrayEquals(normal.output().bytes(), traced.output().bytes());
    assertEquals(normal.resolverCalls(), traced.resolverCalls());
    assertEquals(normal.transportCalls(), traced.transportCalls());
    assertTrue(
        traced.events().stream()
            .anyMatch(event -> event.effect().equals("http.send:<redacted>:POST:response")));
    String published =
        traced.events().stream()
            .filter(event -> event.effect().startsWith("http.send:"))
            .toList()
            .toString();
    assertFalse(published.contains("api.example.invalid"));
    assertFalse(published.contains("山田 太郎"));
    assertFalse(published.contains("C-100"));
  }

  @Test
  void publicRunResultRejectsEveryNegativeHttpCounter() {
    for (int negativeIndex = 0; negativeIndex < 4; negativeIndex++) {
      long[] counters = {0, 0, 0, 0};
      counters[negativeIndex] = -1;
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new ProgramRunResult(
                  0,
                  List.of(),
                  List.of(),
                  0,
                  0,
                  List.of(),
                  0,
                  0,
                  0,
                  0,
                  0,
                  0,
                  0,
                  counters[0],
                  counters[1],
                  counters[2],
                  counters[3],
                  0,
                  jp.bsb.runtime.ExecutionTermination.completed()));
    }
  }

  private static void consume(
      List<HttpsConformanceData.RowSpec> rows,
      Set<String> consumed,
      Set<String> variantKeys,
      String table) {
    for (var row : rows) {
      String id = row.value("case_id");
      consumed.add(id);
      assertTrue(variantKeys.add(table + '/' + id + '/' + row.value("variant")));
    }
  }

  private static Observation runReference(boolean tracing) throws Exception {
    var resolverCalls = new AtomicInteger();
    var transportCalls = new AtomicInteger();
    var credential = CredentialReference.opaque();
    ConnectionPolicy policy =
        new ConnectionPolicy(
            "https://api.example.invalid/",
            List.of("https://api.example.invalid"),
            List.of("POST"),
            "apiKey",
            Optional.of(credential),
            1_000,
            2_000,
            65_536,
            65_536,
            "deny",
            "none");
    var output = new MemoryOutputSink();
    var events = new ArrayList<TraceEvent>();
    var environment =
        ExecutionEnvironment.builder(() -> 0L)
            .consoleInput(
                new MemoryConsoleInput(List.of(new InputEvent.Line(INPUT, INPUT.length + 1))))
            .consoleOutput(output::write)
            .connectionResolver(
                (name, operation) -> {
                  resolverCalls.incrementAndGet();
                  assertEquals("顧客管理API", name);
                  assertEquals("resolve", operation);
                  return ConnectionResolution.resolved(name, policy);
                })
            .httpTransport(
                request -> {
                  transportCalls.incrementAndGet();
                  assertTrue(request.policy().credentialReference().orElseThrow() == credential);
                  assertEquals("POST", request.method());
                  assertEquals(
                      "https://api.example.invalid/v1/customers",
                      request.targetUri().toASCIIString());
                  assertEquals(
                      List.of(
                          new HttpTransportHeader("accept", "application/json"),
                          new HttpTransportHeader("content-type", "application/json")),
                      request.headers());
                  assertArrayEquals(INPUT, request.bodyBytes().orElseThrow());
                  return HttpTransportResult.response(
                      request.connectionName(),
                      request.method(),
                      201,
                      List.of(new HttpTransportHeader("content-type", "application/json")),
                      RESPONSE);
                })
            .redactLogicalConnectionNamesInTrace()
            .build();
    ProgramRunResult result =
        new ProgramRunner()
            .run(
                SAMPLE.toString(),
                Files.readAllBytes(SAMPLE),
                new ExecutionContext(
                    output, () -> 0L, tracing ? events::add : TraceSink.none(), environment));
    return new Observation(
        result, output, resolverCalls.get(), transportCalls.get(), List.copyOf(events));
  }

  private static void assertEquivalent(ProgramRunResult first, ProgramRunResult second) {
    assertEquals(first.exitCode(), second.exitCode());
    assertEquals(first.diagnostics(), second.diagnostics());
    assertEquals(first.finalDataStack(), second.finalDataStack());
    assertEquals(
        first.finalGlobalValues().stream().map(value -> value.map(Object::toString)).toList(),
        second.finalGlobalValues().stream().map(value -> value.map(Object::toString)).toList());
    assertEquals(first.executedInstructions(), second.executedInstructions());
    assertEquals(first.outputBytes(), second.outputBytes());
    assertEquals(first.errorOutputBytes(), second.errorOutputBytes());
    assertEquals(first.arrayConstructionUnits(), second.arrayConstructionUnits());
    assertEquals(first.arrayElementOperationUnits(), second.arrayElementOperationUnits());
    assertEquals(first.regexWorkUnits(), second.regexWorkUnits());
    assertEquals(first.jsonConstructionUnits(), second.jsonConstructionUnits());
    assertEquals(first.jsonWorkUnits(), second.jsonWorkUnits());
    assertEquals(first.byteSequenceConstructionBytes(), second.byteSequenceConstructionBytes());
    assertEquals(first.byteSequenceWorkBytes(), second.byteSequenceWorkBytes());
    assertEquals(first.httpMetadataConstructionBytes(), second.httpMetadataConstructionBytes());
    assertEquals(first.httpSendCalls(), second.httpSendCalls());
    assertEquals(first.httpRequestAttemptBytes(), second.httpRequestAttemptBytes());
    assertEquals(first.httpResponseReceivedBytes(), second.httpResponseReceivedBytes());
    assertEquals(first.termination(), second.termination());
  }

  private record Observation(
      ProgramRunResult result,
      MemoryOutputSink output,
      int resolverCalls,
      int transportCalls,
      List<TraceEvent> events) {}
}
