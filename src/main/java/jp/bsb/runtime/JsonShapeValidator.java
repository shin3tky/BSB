package jp.bsb.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import jp.bsb.json.JsonArray;
import jp.bsb.json.JsonKind;
import jp.bsb.json.JsonObject;
import jp.bsb.json.JsonValue;

/** JSON形状を規範順に反復検証します。 */
final class JsonShapeValidator {
  record Outcome(
      List<JsonShapeFailureValue> failures,
      long workUnits,
      boolean workLimitExceeded,
      long pathLimitObserved) {
    Outcome {
      failures = List.copyOf(failures);
    }
  }

  private record Task(
      JsonValue value, JsonShapeValue shape, String path, boolean missing, boolean required) {}

  private JsonShapeValidator() {}

  static Outcome validate(JsonValue input, JsonShapeValue shape) {
    var failures = new ArrayList<JsonShapeFailureValue>();
    var work = new ArrayDeque<Task>();
    work.push(new Task(input, shape, "", false, false));
    long visited = 0;
    while (!work.isEmpty() && failures.size() < JsonShapeLimits.MAX_FAILURES) {
      Task task = work.pop();
      visited++;
      if (visited > JsonShapeLimits.MAX_WORK_UNITS) {
        return new Outcome(failures, visited, true, 0);
      }
      JsonShapeValue expected = task.shape();
      if (task.missing()) {
        if (!task.required()) {
          continue;
        }
        long pathBytes = Utf8Length.measureUpTo(task.path(), Long.MAX_VALUE).bytes();
        if (pathBytes > JsonShapeLimits.MAX_PATH_UTF8_BYTES) {
          return new Outcome(failures, visited, false, pathBytes);
        }
        failures.add(
            new JsonShapeFailureValue(
                "missingRequiredKey", task.path(), expected.expectedJsonKindName(), "missing"));
        continue;
      }

      JsonValue actual = task.value();
      if (actual.kind() == JsonKind.NULL) {
        if (expected.acceptsNull()) {
          continue;
        }
        Outcome failed =
            addFailure(
                failures,
                visited,
                "nullNotAllowed",
                task.path(),
                expected.expectedJsonKindName(),
                "null");
        if (failed != null) {
          return failed;
        }
        continue;
      }

      expected = expected.nonNullShape();
      JsonKind expectedKind = expected.kind().jsonKind();
      if (actual.kind() != expectedKind) {
        Outcome failed =
            addFailure(
                failures,
                visited,
                "kindMismatch",
                task.path(),
                expectedKind.diagnosticName(),
                actual.kind().diagnosticName());
        if (failed != null) {
          return failed;
        }
        continue;
      }

      if (expected.kind() == JsonShapeValue.Kind.ARRAY) {
        JsonArray array = (JsonArray) actual;
        for (int index = array.size() - 1; index >= 0; index--) {
          work.push(
              new Task(
                  array.get(index), expected.child(), task.path() + "/" + index, false, false));
        }
      } else if (expected.kind() == JsonShapeValue.Kind.OBJECT) {
        JsonObject object = (JsonObject) actual;
        List<JsonShapeValue.Member> members = expected.members();
        for (int index = members.size() - 1; index >= 0; index--) {
          JsonShapeValue.Member member = members.get(index);
          JsonValue value = object.find(member.name()).orElse(null);
          work.push(
              new Task(
                  value,
                  member.shape(),
                  task.path() + "/" + escapePointerToken(member.name()),
                  value == null,
                  member.required()));
        }
      }
    }
    return new Outcome(failures, visited, false, 0);
  }

  private static Outcome addFailure(
      List<JsonShapeFailureValue> failures,
      long visited,
      String kind,
      String path,
      String expected,
      String actual) {
    long pathBytes = Utf8Length.measureUpTo(path, Long.MAX_VALUE).bytes();
    if (pathBytes > JsonShapeLimits.MAX_PATH_UTF8_BYTES) {
      return new Outcome(failures, visited, false, pathBytes);
    }
    failures.add(new JsonShapeFailureValue(kind, path, expected, actual));
    return null;
  }

  static String escapePointerToken(String input) {
    return input.replace("~", "~0").replace("/", "~1");
  }
}
