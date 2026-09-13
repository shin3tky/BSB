package jp.bsb.cli;

import java.nio.file.Path;
import jp.bsb.runtime.CapabilityException;
import jp.bsb.runtime.FileReadRequest;
import jp.bsb.runtime.FileReadResult;
import jp.bsb.runtime.FileWriteRequest;
import jp.bsb.runtime.FileWriteResult;

/** 設定済みexact pathだけを受け取る、差し替え可能な物理ファイルadapterです。 */
interface WorkspaceFileAdapter {
  FileReadResult read(FileReadRequest request, Path path) throws CapabilityException;

  FileWriteResult write(FileWriteRequest request, Path path) throws CapabilityException;

  /** 既定で実ファイルを開かない既定の閉じた偽adapterです。 */
  static WorkspaceFileAdapter recoverableFailure() {
    return new WorkspaceFileAdapter() {
      @Override
      public FileReadResult read(FileReadRequest request, Path path) {
        return FileReadResult.failure(request, "ioFailure");
      }

      @Override
      public FileWriteResult write(FileWriteRequest request, Path path) {
        return FileWriteResult.failure(request, "ioFailure");
      }
    };
  }
}
