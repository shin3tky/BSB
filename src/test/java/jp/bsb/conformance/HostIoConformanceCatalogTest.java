package jp.bsb.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class HostIoConformanceCatalogTest {
  @Test
  void loadsEveryCaseDiagnosticResourceIoAndGeneratorWithoutUnusedKeys() throws Exception {
    var cases = HostIoConformanceData.loadCases();
    assertEquals(50, cases.cases().size());
    assertEquals(24, cases.normalIds().size());
    assertEquals(5, cases.staticFailureIds().size());
    assertEquals(20, cases.runtimeFailureIds().size());
    assertEquals(26, HostIoConformanceData.loadDiagnostics().size());
    var resources = HostIoConformanceData.loadResources();
    assertEquals(29, resources.size());
    Set<String> expectedResourceIds =
        IntStream.rangeClosed(1, 12)
            .mapToObj(index -> "IO-R%03d".formatted(index))
            .collect(Collectors.toUnmodifiableSet());
    assertEquals(
        expectedResourceIds,
        resources.stream()
            .map(HostIoConformanceData.ResourceSpec::id)
            .collect(Collectors.toUnmodifiableSet()));

    for (var spec : cases.cases()) {
      String io = spec.attributes().get("io");
      if (io != null) {
        HostIoConformanceData.loadIo(io);
      }
    }
    for (String id : expectedResourceIds) {
      HostIoConformanceData.loadGenerated(id);
    }
  }

  @Test
  void eventQueueRejectsBothShortageAndSurplus() {
    var shortage = new HostIoConformanceData.EventQueue<String>(List.of());
    var surplus = new HostIoConformanceData.EventQueue<>(List.of("unused"));

    assertThrows(IllegalStateException.class, shortage::take);
    assertThrows(IllegalStateException.class, surplus::assertExhausted);
    assertEquals("unused", surplus.take());
    surplus.assertExhausted();
  }
}
