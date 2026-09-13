package jp.bsb.runtime;

/** `終了する`からインタープリタの命令境界までだけを移動する内部制御移行です。 */
final class ProgramTermination extends RuntimeException {
  private final int exitCode;

  ProgramTermination(int exitCode) {
    super(null, null, false, false);
    if (exitCode < 0 || exitCode > 255) {
      throw new IllegalArgumentException("program exit code must be between 0 and 255");
    }
    this.exitCode = exitCode;
  }

  int exitCode() {
    return exitCode;
  }
}
