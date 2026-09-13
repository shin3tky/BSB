package jp.bsb.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SyntaxDepthGuardTest {
  @Test
  void accepts256LevelsAndRejectsThe257thWithoutChangingItsState() {
    var guard = new SyntaxDepthGuard();

    for (int depth = 1; depth <= SyntaxDepthGuard.MAX_DEPTH; depth++) {
      assertTrue(guard.tryEnter(), "depth=" + depth);
    }
    assertFalse(guard.tryEnter());
    assertEquals(SyntaxDepthGuard.MAX_DEPTH, guard.depth());

    for (int depth = SyntaxDepthGuard.MAX_DEPTH; depth > 0; depth--) {
      guard.exit();
    }
    assertEquals(0, guard.depth());
  }
}
