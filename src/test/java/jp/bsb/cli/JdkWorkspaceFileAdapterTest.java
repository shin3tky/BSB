package jp.bsb.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import jp.bsb.runtime.ByteSequenceValue;
import jp.bsb.runtime.FileReadRequest;
import jp.bsb.runtime.FileReadResult;
import jp.bsb.runtime.FileWriteRequest;
import jp.bsb.runtime.FileWriteResult;
import jp.bsb.runtime.WorkspaceHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdkWorkspaceFileAdapterTest {
  @TempDir Path temporaryDirectory;
  private final WorkspaceHandle handle = WorkspaceHandle.opaque();

  @Test
  void readsEmptyExactAndGrowingFilesWithABoundedProbe() throws Exception {
    Path empty = temporaryDirectory.resolve("empty");
    Path exact = temporaryDirectory.resolve("exact");
    Files.write(empty, new byte[0]);
    Files.write(exact, new byte[] {1, 2, 3});
    JdkWorkspaceFileAdapter adapter = JdkWorkspaceFileAdapter.system();

    FileReadResult emptyResult = adapter.read(readRequest(0), empty);
    FileReadResult exactResult = adapter.read(readRequest(3), exact);
    assertEquals(FileReadResult.State.SUCCESS, emptyResult.state());
    assertEquals(0, emptyResult.observedBytes());
    assertEquals(FileReadResult.State.SUCCESS, exactResult.state());
    assertEquals(3, exactResult.observedBytes());

    var growing =
        new DelegatingOperations() {
          @Override
          public InputStream input(Path path) {
            return new ByteArrayInputStream(new byte[] {1, 2, 3, 4});
          }
        };
    FileReadResult tooLarge = new JdkWorkspaceFileAdapter(growing).read(readRequest(3), empty);
    assertFailure(tooLarge, "tooLarge");
  }

  @Test
  void classifiesMissingNonRegularOversizedAndIoReadFailures() throws Exception {
    Path directory = Files.createDirectory(temporaryDirectory.resolve("directory"));
    Path oversized = temporaryDirectory.resolve("oversized");
    Files.write(oversized, new byte[] {1, 2});
    JdkWorkspaceFileAdapter adapter = JdkWorkspaceFileAdapter.system();

    assertFailure(adapter.read(readRequest(1), temporaryDirectory.resolve("missing")), "notFound");
    assertFailure(adapter.read(readRequest(1), directory), "notRegularFile");
    assertFailure(adapter.read(readRequest(1), oversized), "tooLarge");

    var broken =
        new DelegatingOperations() {
          @Override
          public InputStream input(Path path) {
            return new InputStream() {
              private boolean first = true;

              @Override
              public int read() throws IOException {
                if (first) {
                  first = false;
                  return 1;
                }
                throw new IOException("secret read failure");
              }
            };
          }
        };
    FileReadResult failure = new JdkWorkspaceFileAdapter(broken).read(readRequest(2), oversized);
    assertFailure(failure, "ioFailure");
    assertFalse(failure.toString().contains("secret"));
  }

  @Test
  void rejectsAFinalSymbolicLinkWithoutFollowingIt() throws Exception {
    Path target = temporaryDirectory.resolve("target");
    Path link = temporaryDirectory.resolve("link");
    Files.write(target, new byte[] {1});
    Files.createSymbolicLink(link, target.getFileName());

    assertFailure(JdkWorkspaceFileAdapter.system().read(readRequest(1), link), "notRegularFile");
    assertFailure(
        JdkWorkspaceFileAdapter.system().write(writeRequest(new byte[] {2}, 1), link),
        "targetNotRegularFile");
    assertArrayEquals(new byte[] {1}, Files.readAllBytes(target));
  }

  @Test
  void atomicallyCreatesAndReplacesTargetsAndLeavesNoTemporaryFiles() throws Exception {
    Path target = temporaryDirectory.resolve("target");
    JdkWorkspaceFileAdapter adapter = JdkWorkspaceFileAdapter.system();

    FileWriteResult created = adapter.write(writeRequest(new byte[] {1, 2}, 2), target);
    assertEquals(FileWriteResult.State.SUCCESS, created.state());
    assertEquals(2, created.publishedBytes().orElseThrow());
    assertArrayEquals(new byte[] {1, 2}, Files.readAllBytes(target));

    FileWriteResult replaced = adapter.write(writeRequest(new byte[0], 1), target);
    assertEquals(FileWriteResult.State.SUCCESS, replaced.state());
    assertArrayEquals(new byte[0], Files.readAllBytes(target));
    try (var entries = Files.list(temporaryDirectory)) {
      assertEquals(List.of(target), entries.toList());
    }
  }

  @Test
  void classifiesWritePreconditionsWithoutChangingExistingTargets() throws Exception {
    Path old = temporaryDirectory.resolve("old");
    Files.write(old, new byte[] {9});
    Path notDirectory = temporaryDirectory.resolve("not-directory");
    Files.write(notDirectory, new byte[] {8});
    Path directoryTarget = Files.createDirectory(temporaryDirectory.resolve("target-directory"));
    JdkWorkspaceFileAdapter adapter = JdkWorkspaceFileAdapter.system();

    assertFailure(adapter.write(writeRequest(new byte[] {1, 2}, 1), old), "tooLarge");
    assertFailure(
        adapter.write(
            writeRequest(new byte[] {1}, 1), temporaryDirectory.resolve("missing/target")),
        "parentNotFound");
    assertFailure(
        adapter.write(writeRequest(new byte[] {1}, 1), notDirectory.resolve("target")),
        "parentNotFound");
    assertFailure(
        adapter.write(writeRequest(new byte[] {1}, 1), directoryTarget), "targetNotRegularFile");
    assertArrayEquals(new byte[] {9}, Files.readAllBytes(old));
  }

  @Test
  void doesNotFallbackWhenAtomicReplacementIsUnavailableAndCleansTheTemporaryFile()
      throws Exception {
    Path target = temporaryDirectory.resolve("target");
    Files.write(target, new byte[] {9});
    var unsupported =
        new DelegatingOperations() {
          @Override
          public void atomicReplace(Path source, Path destination) throws IOException {
            throw new AtomicMoveNotSupportedException(
                source.toString(), destination.toString(), "secret reason");
          }
        };

    FileWriteResult result =
        new JdkWorkspaceFileAdapter(unsupported).write(writeRequest(new byte[] {1}, 1), target);

    assertFailure(result, "atomicReplacementUnavailable");
    assertArrayEquals(new byte[] {9}, Files.readAllBytes(target));
    assertFalse(result.toString().contains("secret"));
    try (var entries = Files.list(temporaryDirectory)) {
      assertEquals(List.of(target), entries.toList());
    }
  }

  @Test
  void preservesTheTargetOnWriteFailureAndIgnoresCleanupFailureDetails() throws Exception {
    Path target = temporaryDirectory.resolve("target");
    Files.write(target, new byte[] {9});
    var broken =
        new DelegatingOperations() {
          @Override
          public OutputStream output(Path path) {
            return new OutputStream() {
              @Override
              public void write(int value) throws IOException {
                throw new IOException("secret output failure");
              }
            };
          }

          @Override
          public void deleteIfExists(Path path) throws IOException {
            super.deleteIfExists(path);
            throw new IOException("secret cleanup failure");
          }
        };

    FileWriteResult result =
        new JdkWorkspaceFileAdapter(broken).write(writeRequest(new byte[] {1}, 1), target);

    assertFailure(result, "ioFailure");
    assertArrayEquals(new byte[] {9}, Files.readAllBytes(target));
    assertFalse(result.toString().contains("secret"));
  }

  @Test
  void doesNotPublishWhenClosingTheCompletedTemporaryContentFails() throws Exception {
    Path target = temporaryDirectory.resolve("target");
    Files.write(target, new byte[] {9});
    var closeFailure =
        new DelegatingOperations() {
          @Override
          public OutputStream output(Path path) throws IOException {
            OutputStream delegate = super.output(path);
            return new OutputStream() {
              @Override
              public void write(int value) throws IOException {
                delegate.write(value);
              }

              @Override
              public void write(byte[] bytes) throws IOException {
                delegate.write(bytes);
              }

              @Override
              public void close() throws IOException {
                delegate.close();
                throw new IOException("secret close failure");
              }
            };
          }
        };

    FileWriteResult result =
        new JdkWorkspaceFileAdapter(closeFailure).write(writeRequest(new byte[] {1, 2}, 2), target);

    assertFailure(result, "ioFailure");
    assertArrayEquals(new byte[] {9}, Files.readAllBytes(target));
    try (var entries = Files.list(temporaryDirectory)) {
      assertEquals(List.of(target), entries.toList());
    }
  }

  @Test
  void mapsAnExistingThreadInterruptionToCancellationWithoutIo() throws Exception {
    Path target = temporaryDirectory.resolve("target");
    Files.write(target, new byte[] {9});
    JdkWorkspaceFileAdapter adapter = JdkWorkspaceFileAdapter.system();
    try {
      Thread.currentThread().interrupt();
      assertEquals(FileReadResult.State.CANCELLED, adapter.read(readRequest(1), target).state());
      assertEquals(
          FileWriteResult.State.CANCELLED,
          adapter.write(writeRequest(new byte[] {1}, 1), target).state());
    } finally {
      Thread.interrupted();
    }
    assertArrayEquals(new byte[] {9}, Files.readAllBytes(target));
  }

  private FileReadRequest readRequest(long maximum) {
    return new FileReadRequest("W", handle, "logical", maximum);
  }

  private FileWriteRequest writeRequest(byte[] bytes, long maximum) {
    return new FileWriteRequest("W", handle, "logical", ByteSequenceValue.copyOf(bytes), maximum);
  }

  private static void assertFailure(FileReadResult result, String kind) {
    assertEquals(FileReadResult.State.FAILURE, result.state());
    assertEquals(kind, result.failureKind().orElseThrow());
  }

  private static void assertFailure(FileWriteResult result, String kind) {
    assertEquals(FileWriteResult.State.FAILURE, result.state());
    assertEquals(kind, result.failureKind().orElseThrow());
  }

  private static class DelegatingOperations implements JdkWorkspaceFileAdapter.Operations {
    private final JdkWorkspaceFileAdapter.Operations delegate =
        JdkWorkspaceFileAdapter.Operations.jdk();

    @Override
    public BasicFileAttributes attributes(Path path, boolean noFollowLinks) throws IOException {
      return delegate.attributes(path, noFollowLinks);
    }

    @Override
    public InputStream input(Path path) throws IOException {
      return delegate.input(path);
    }

    @Override
    public Path createTemporaryFile(Path parent) throws IOException {
      return delegate.createTemporaryFile(parent);
    }

    @Override
    public OutputStream output(Path path) throws IOException {
      return delegate.output(path);
    }

    @Override
    public void atomicReplace(Path source, Path target) throws IOException {
      delegate.atomicReplace(source, target);
    }

    @Override
    public void deleteIfExists(Path path) throws IOException {
      delegate.deleteIfExists(path);
    }
  }
}
