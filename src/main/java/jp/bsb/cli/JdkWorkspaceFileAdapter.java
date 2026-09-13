package jp.bsb.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import jp.bsb.runtime.FileReadRequest;
import jp.bsb.runtime.FileReadResult;
import jp.bsb.runtime.FileWriteRequest;
import jp.bsb.runtime.FileWriteResult;

/** JDK NIOだけでbounded readと同親一時ファイルからの原子的全置換を行います。 */
final class JdkWorkspaceFileAdapter implements WorkspaceFileAdapter {
  private final Operations operations;

  JdkWorkspaceFileAdapter(Operations operations) {
    this.operations = java.util.Objects.requireNonNull(operations, "operations");
  }

  static JdkWorkspaceFileAdapter system() {
    return new JdkWorkspaceFileAdapter(Operations.jdk());
  }

  @Override
  public FileReadResult read(FileReadRequest request, Path path) {
    if (Thread.currentThread().isInterrupted()) return FileReadResult.cancelled(request);
    BasicFileAttributes attributes;
    try {
      attributes = operations.attributes(path, true);
    } catch (java.nio.file.NoSuchFileException failure) {
      return FileReadResult.failure(request, "notFound");
    } catch (IOException | SecurityException failure) {
      return readFailure(request, failure);
    }
    if (!attributes.isRegularFile()) {
      return FileReadResult.failure(request, "notRegularFile");
    }
    if (attributes.size() > request.maximumBytes()) {
      return FileReadResult.failure(request, "tooLarge");
    }
    int probeBytes = Math.toIntExact(request.maximumBytes() + 1);
    try (InputStream input = operations.input(path)) {
      byte[] bytes = input.readNBytes(probeBytes);
      if (Thread.currentThread().isInterrupted()) return FileReadResult.cancelled(request);
      return bytes.length > request.maximumBytes()
          ? FileReadResult.failure(request, "tooLarge")
          : FileReadResult.success(request, bytes);
    } catch (java.nio.file.NoSuchFileException failure) {
      return FileReadResult.failure(request, "notFound");
    } catch (IOException | SecurityException failure) {
      return readFailure(request, failure);
    }
  }

  @Override
  public FileWriteResult write(FileWriteRequest request, Path path) {
    if (request.body().length() > request.maximumBytes()) {
      return FileWriteResult.failure(request, "tooLarge");
    }
    if (Thread.currentThread().isInterrupted()) return FileWriteResult.cancelled(request);
    Path parent = path.getParent();
    if (parent == null) return FileWriteResult.failure(request, "parentNotFound");
    try {
      if (!operations.attributes(parent, false).isDirectory()) {
        return FileWriteResult.failure(request, "parentNotFound");
      }
    } catch (java.nio.file.NoSuchFileException failure) {
      return FileWriteResult.failure(request, "parentNotFound");
    } catch (IOException | SecurityException failure) {
      return writeFailure(request, failure);
    }
    try {
      if (!operations.attributes(path, true).isRegularFile()) {
        return FileWriteResult.failure(request, "targetNotRegularFile");
      }
    } catch (java.nio.file.NoSuchFileException ignored) {
      // 新規targetは許可します。
    } catch (IOException | SecurityException failure) {
      return writeFailure(request, failure);
    }

    Path temporary = null;
    try {
      temporary = operations.createTemporaryFile(parent);
      if (Thread.currentThread().isInterrupted()) return FileWriteResult.cancelled(request);
      try (OutputStream output = operations.output(temporary)) {
        output.write(request.bodyBytes());
      }
      if (Thread.currentThread().isInterrupted()) return FileWriteResult.cancelled(request);
      operations.atomicReplace(temporary, path);
      temporary = null;
      return FileWriteResult.success(request);
    } catch (AtomicMoveNotSupportedException | UnsupportedOperationException failure) {
      return FileWriteResult.failure(request, "atomicReplacementUnavailable");
    } catch (IOException | SecurityException failure) {
      return writeFailure(request, failure);
    } finally {
      if (temporary != null) {
        try {
          operations.deleteIfExists(temporary);
        } catch (IOException | SecurityException ignored) {
          // cleanupの詳細と一時名は公開せず、先に確定した操作結果を保ちます。
        }
      }
    }
  }

  private static FileReadResult readFailure(FileReadRequest request, Exception failure) {
    return cancelled(failure)
        ? FileReadResult.cancelled(request)
        : FileReadResult.failure(request, "ioFailure");
  }

  private static FileWriteResult writeFailure(FileWriteRequest request, Exception failure) {
    return cancelled(failure)
        ? FileWriteResult.cancelled(request)
        : FileWriteResult.failure(request, "ioFailure");
  }

  private static boolean cancelled(Exception failure) {
    return failure instanceof ClosedByInterruptException
        || failure instanceof InterruptedIOException
        || Thread.currentThread().isInterrupted();
  }

  interface Operations {
    BasicFileAttributes attributes(Path path, boolean noFollowLinks) throws IOException;

    InputStream input(Path path) throws IOException;

    Path createTemporaryFile(Path parent) throws IOException;

    OutputStream output(Path path) throws IOException;

    void atomicReplace(Path source, Path target) throws IOException;

    void deleteIfExists(Path path) throws IOException;

    static Operations jdk() {
      return new Operations() {
        @Override
        public BasicFileAttributes attributes(Path path, boolean noFollowLinks) throws IOException {
          return Files.readAttributes(
              path,
              BasicFileAttributes.class,
              noFollowLinks ? new LinkOption[] {LinkOption.NOFOLLOW_LINKS} : new LinkOption[0]);
        }

        @Override
        public InputStream input(Path path) throws IOException {
          Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
          return Channels.newInputStream(Files.newByteChannel(path, options));
        }

        @Override
        public Path createTemporaryFile(Path parent) throws IOException {
          return Files.createTempFile(parent, ".bsb-", ".tmp");
        }

        @Override
        public OutputStream output(Path path) throws IOException {
          return Files.newOutputStream(
              path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        }

        @Override
        public void atomicReplace(Path source, Path target) throws IOException {
          Files.move(
              source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public void deleteIfExists(Path path) throws IOException {
          Files.deleteIfExists(path);
        }
      };
    }
  }
}
