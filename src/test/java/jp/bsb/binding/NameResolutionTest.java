package jp.bsb.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.frontend.ast.WordCall;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class NameResolutionTest {
  @Test
  void keepsOneNamespaceStableIdsScopesAndBidirectionalUses() {
    WordName word = new WordName("メイン", "メイン", span(0, 9));
    Binding global =
        binding(1, "単価", BindingKind.CONSTANT, BindingStorage.GLOBAL, 1, OptionalInt.of(1), 20, 30);
    Binding local =
        binding(
            2, "小計", BindingKind.VARIABLE, BindingStorage.LOCAL, 3, OptionalInt.empty(), 60, 70);
    LexicalScope globalScope = LexicalScope.global(new ScopeId(1), span(0, 200));
    LexicalScope wordScope =
        new LexicalScope(
            new ScopeId(2),
            LexicalScopeKind.WORD_BODY,
            Optional.of(new ScopeId(1)),
            Optional.of("メイン"),
            span(10, 190));
    LexicalScope blockScope =
        new LexicalScope(
            new ScopeId(3),
            LexicalScopeKind.COUNTED_LOOP_BODY,
            Optional.of(new ScopeId(2)),
            Optional.of("メイン"),
            span(50, 150));
    WordCall readNode = new WordCall("小計", "小計", span(100, 106));
    WordCall equalButDistinctNode = new WordCall("小計", "小計", span(100, 106));
    WordCall writeNode = new WordCall("小計", "小計", span(110, 116));
    var read = new ResolvedBindingUse(readNode, "小計", BindingUseKind.READ, new BindingId(2));
    var write = new ResolvedBindingUse(writeNode, "小計", BindingUseKind.WRITE, new BindingId(2));

    var resolution =
        new NameResolution(
            List.of(word, global, local),
            List.of(globalScope, wordScope, blockScope),
            List.of(read, write));

    assertEquals(
        DeclaredNameKind.WORD, resolution.findDeclaration("メイン").orElseThrow().declarationKind());
    assertEquals(
        DeclaredNameKind.CONSTANT,
        resolution.findDeclaration("単価").orElseThrow().declarationKind());
    assertEquals("B1", resolution.bindings().getFirst().id().displayName());
    assertEquals("B2", resolution.bindings().getLast().id().displayName());
    assertSame(local, resolution.resolve(readNode).orElseThrow());
    assertFalse(resolution.findUse(equalButDistinctNode).isPresent());
    assertEquals(List.of(read, write), resolution.usesOf(new BindingId(2)));
    assertEquals(blockScope, resolution.findScope(new ScopeId(3)).orElseThrow());
    assertThrows(UnsupportedOperationException.class, () -> resolution.bindings().clear());
    assertThrows(
        UnsupportedOperationException.class, () -> resolution.usesOf(new BindingId(2)).clear());
  }

  @Test
  void rejectsDuplicateNamespaceEntriesAndUnstableBindingIds() {
    WordName word = new WordName("同名", "同名", span(0, 6));
    Binding duplicate =
        binding(1, "同名", BindingKind.CONSTANT, BindingStorage.GLOBAL, 1, OptionalInt.of(1), 10, 20);
    Binding skippedId =
        binding(2, "値", BindingKind.CONSTANT, BindingStorage.GLOBAL, 1, OptionalInt.of(1), 10, 20);
    LexicalScope globalScope = LexicalScope.global(new ScopeId(1), span(0, 100));

    assertThrows(
        IllegalArgumentException.class,
        () -> new NameResolution(List.of(word, duplicate), List.of(globalScope), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new NameResolution(List.of(skippedId), List.of(globalScope), List.of()));
  }

  @Test
  void rejectsInvalidScopeAndUseReferences() {
    Binding global =
        binding(1, "値", BindingKind.VARIABLE, BindingStorage.GLOBAL, 1, OptionalInt.of(1), 10, 20);
    LexicalScope globalScope = LexicalScope.global(new ScopeId(1), span(0, 100));
    LexicalScope skippedScope =
        new LexicalScope(
            new ScopeId(3),
            LexicalScopeKind.WORD_BODY,
            Optional.of(new ScopeId(1)),
            Optional.of("メイン"),
            span(20, 90));
    WordCall node = new WordCall("値", "値", span(30, 33));
    ResolvedBindingUse unknownUse =
        new ResolvedBindingUse(node, "値", BindingUseKind.READ, new BindingId(2));

    assertThrows(
        IllegalArgumentException.class,
        () -> new NameResolution(List.of(global), List.of(globalScope, skippedScope), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new NameResolution(List.of(global), List.of(globalScope), List.of(unknownUse)));
  }

  @Test
  void permitsTheSameLocalNameInSiblingScopes() {
    Binding trueLocal =
        binding(
            1, "一時", BindingKind.CONSTANT, BindingStorage.LOCAL, 3, OptionalInt.empty(), 30, 40);
    Binding falseLocal =
        binding(
            2, "一時", BindingKind.VARIABLE, BindingStorage.LOCAL, 4, OptionalInt.empty(), 60, 70);
    LexicalScope globalScope = LexicalScope.global(new ScopeId(1), span(0, 100));
    LexicalScope wordScope =
        new LexicalScope(
            new ScopeId(2),
            LexicalScopeKind.WORD_BODY,
            Optional.of(new ScopeId(1)),
            Optional.of("メイン"),
            span(10, 90));
    LexicalScope trueScope =
        new LexicalScope(
            new ScopeId(3),
            LexicalScopeKind.CONDITIONAL_TRUE,
            Optional.of(new ScopeId(2)),
            Optional.of("メイン"),
            span(20, 50));
    LexicalScope falseScope =
        new LexicalScope(
            new ScopeId(4),
            LexicalScopeKind.CONDITIONAL_FALSE,
            Optional.of(new ScopeId(2)),
            Optional.of("メイン"),
            span(51, 80));

    var resolution =
        new NameResolution(
            List.of(trueLocal, falseLocal),
            List.of(globalScope, wordScope, trueScope, falseScope),
            List.of());

    assertEquals(List.of(trueLocal, falseLocal), resolution.findDeclarations("一時"));
    assertFalse(resolution.findDeclaration("一時").isPresent());
  }

  private static Binding binding(
      int id,
      String name,
      BindingKind kind,
      BindingStorage storage,
      int scopeId,
      OptionalInt initializationOrder,
      long start,
      long end) {
    return new Binding(
        new BindingId(id),
        name,
        name,
        kind,
        storage,
        new ScopeId(scopeId),
        BindingTypeState.inferred(ValueType.INTEGER),
        initializationOrder,
        span(start, start + 3),
        span(start, end));
  }

  private static SourceSpan span(long start, long end) {
    return new SourceSpan(
        new SourcePosition(start, 1, Math.toIntExact(start + 1)),
        new SourcePosition(end, 1, Math.toIntExact(end + 1)));
  }
}
