package jp.bsb.cli;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;

/** 版1の診断オブジェクトを内部診断型から分離して保持します。 */
record DiagnosticJson(
    String code,
    String severity,
    String stage,
    String message,
    String sourcePath,
    JsonDiagnosticLocation location,
    SortedMap<String, String> fields,
    Optional<String> expected,
    Optional<String> actual,
    List<JsonRelatedLocation> relatedLocations,
    List<String> fixes,
    Optional<JsonResourceLimit> resourceLimit) {
  DiagnosticJson {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(stage, "stage");
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(sourcePath, "sourcePath");
    Objects.requireNonNull(location, "location");
    Objects.requireNonNull(fields, "fields");
    Objects.requireNonNull(expected, "expected");
    Objects.requireNonNull(actual, "actual");
    relatedLocations = List.copyOf(relatedLocations);
    fixes = List.copyOf(fixes);
    Objects.requireNonNull(resourceLimit, "resourceLimit");
  }
}

/** JSON診断の位置表現です。 */
sealed interface JsonDiagnosticLocation permits JsonSpan, JsonPoint, JsonOffset {}

/** 1始まりの行・書記素列です。 */
record JsonLineColumn(int line, int column) {}

/** 非空範囲の包含行列と半開UTF-8範囲です。 */
record JsonSpan(
    JsonLineColumn start, JsonLineColumn endInclusive, long utf8Start, long utf8EndExclusive)
    implements JsonDiagnosticLocation {}

/** 欠落、挿入、EOFまたはゼロ幅の位置です。 */
record JsonPoint(int line, int column, long utf8Offset) implements JsonDiagnosticLocation {}

/** 行・列へ復号できないファイル位置です。 */
record JsonOffset(long utf8Offset) implements JsonDiagnosticLocation {}

/** 診断原因に関連するソース内またはソース外情報です。 */
record JsonRelatedLocation(
    Optional<String> sourcePath, Optional<JsonPoint> point, String description) {
  JsonRelatedLocation {
    Objects.requireNonNull(sourcePath, "sourcePath");
    Objects.requireNonNull(point, "point");
    Objects.requireNonNull(description, "description");
    if (sourcePath.isPresent() != point.isPresent()) {
      throw new IllegalArgumentException("sourcePath and point must both be present or absent");
    }
  }
}

/** 資源診断の名前、上限、観測値です。 */
record JsonResourceLimit(String name, String limit, String observed) {}
