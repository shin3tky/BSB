package jp.bsb.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import jp.bsb.binding.BindingId;
import jp.bsb.binding.BindingKind;
import jp.bsb.binding.BindingStorage;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.regex.RegexProgram;
import jp.bsb.runtime.ExecutionContext;
import jp.bsb.runtime.Interpreter;
import jp.bsb.runtime.MemoryOutputSink;
import jp.bsb.runtime.RegexValue;
import jp.bsb.runtime.StringValue;
import jp.bsb.runtime.TraceSink;
import jp.bsb.stdlib.BuiltinDictionary;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;

class TextRegexRegexTypeIrTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));
  private static final IrStackEffect REGEX_IDENTITY =
      new IrStackEffect(List.of(ValueType.REGEX), List.of(ValueType.REGEX));

  @Test
  void carriesRegexValuesThroughPushCallGlobalAndLocalStorage() {
    RegexValue original = regex("(?<letter>[a-z])", "i");
    RegexValue replacement = regex("\\p{Han}+", "");
    IrStorageSlot global = globalSlot();
    IrStorageSlot local = localSlot();
    var mainSymbol = new SymbolId(0);
    var identitySymbol = new SymbolId(1);
    var initializerSymbol = new SymbolId(2);

    var initializer =
        new IrWord(
            initializerSymbol,
            "<大域初期化>",
            List.of(
                new PushConst(original, SPAN),
                new InitializeGlobal(global, SPAN),
                new Return(SPAN)),
            List.of(),
            new IrStackEffect(List.of(), List.of()));
    var identity =
        new IrWord(identitySymbol, "正規表現恒等", List.of(new Return(SPAN)), List.of(), REGEX_IDENTITY);
    var main =
        new IrWord(
            mainSymbol,
            "メイン",
            List.of(
                new PushConst(replacement, SPAN),
                new StoreGlobal(global, SPAN),
                new LoadGlobal(global, SPAN),
                new InitializeLocal(local, SPAN),
                new PushConst(original, SPAN),
                new StoreLocal(local, SPAN),
                new LoadLocal(local, SPAN),
                new Call(identitySymbol, "正規表現恒等", List.of(), REGEX_IDENTITY, SPAN),
                new Return(SPAN)),
            List.of(local),
            new IrStackEffect(List.of(), List.of(ValueType.REGEX)));
    var program =
        new IrProgram(
            "regex-ir.bsb",
            mainSymbol,
            Map.of(mainSymbol, main, identitySymbol, identity),
            Map.of(),
            13,
            Optional.of(initializer),
            List.of(global));

    var result =
        new Interpreter()
            .execute(
                program, new ExecutionContext(new MemoryOutputSink(), () -> 0L, TraceSink.none()));

    assertTrue(result.successful(), result.diagnostic().toString());
    assertEquals(List.of(original), result.finalDataStack());
    assertEquals(Optional.of(replacement), result.finalGlobalValues().getFirst());
    assertEquals(0, result.regexWorkUnits());
  }

  @Test
  void rejectsARegexPushWhoseDeclaredIrOutputTypeIsDifferent() {
    var mainSymbol = new SymbolId(0);
    var main =
        new IrWord(
            mainSymbol,
            "メイン",
            List.of(new PushConst(regex("a", ""), SPAN), new Return(SPAN)),
            List.of(),
            new IrStackEffect(List.of(), List.of(ValueType.STRING)));

    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IrProgram(
                    "invalid-regex.bsb", mainSymbol, Map.of(mainSymbol, main), Map.of(), 2));

    assertEquals("Return stack differs from its IR word effect", failure.getMessage());
  }

  @Test
  void acceptsMatchingRegexBuiltinEffectsAndRejectsSyntheticMismatches() {
    var mainSymbol = new SymbolId(0);
    var builtinSymbol = new SymbolId(1);
    var matchingEffect =
        new IrStackEffect(List.of(ValueType.STRING, ValueType.REGEX), List.of(ValueType.BOOLEAN));
    var mismatchEffect =
        new IrStackEffect(List.of(ValueType.STRING, ValueType.STRING), List.of(ValueType.BOOLEAN));
    var builtin = BuiltinDictionary.find("正規表現に完全一致する").orElseThrow();
    var matching =
        new IrWord(
            mainSymbol,
            "メイン",
            List.of(
                new PushConst(new StringValue("abc"), SPAN),
                new PushConst(regex("abc", ""), SPAN),
                new Call(builtinSymbol, builtin.canonicalName(), List.of(), matchingEffect, SPAN),
                new Return(SPAN)),
            List.of(),
            new IrStackEffect(List.of(), List.of(ValueType.BOOLEAN)));

    new IrProgram(
        "matching-regex-call.bsb",
        mainSymbol,
        Map.of(mainSymbol, matching),
        Map.of(builtinSymbol, builtin),
        4);

    var mismatch =
        new IrWord(
            mainSymbol,
            "メイン",
            List.of(
                new PushConst(new StringValue("abc"), SPAN),
                new PushConst(regex("abc", ""), SPAN),
                new Call(builtinSymbol, builtin.canonicalName(), List.of(), mismatchEffect, SPAN),
                new Return(SPAN)),
            List.of(),
            new IrStackEffect(List.of(), List.of(ValueType.BOOLEAN)));
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IrProgram(
                    "mismatch-regex-call.bsb",
                    mainSymbol,
                    Map.of(mainSymbol, mismatch),
                    Map.of(builtinSymbol, builtin),
                    4));

    assertEquals("Call effect differs from its builtin target", failure.getMessage());
  }

  private static RegexValue regex(String rawPattern, String flags) {
    return new RegexValue(rawPattern, flags, new TestProgram(7, 1, List.of("letter")));
  }

  private static IrStorageSlot globalSlot() {
    return new IrStorageSlot(
        new BindingId(1),
        "大域式",
        BindingKind.VARIABLE,
        BindingStorage.GLOBAL,
        ValueType.REGEX,
        0,
        Optional.empty());
  }

  private static IrStorageSlot localSlot() {
    return new IrStorageSlot(
        new BindingId(2),
        "局所式",
        BindingKind.VARIABLE,
        BindingStorage.LOCAL,
        ValueType.REGEX,
        0,
        Optional.of("メイン"));
  }

  private record TestProgram(int instructionCount, int captureCount, List<String> namedCaptures)
      implements RegexProgram {
    private TestProgram {
      namedCaptures = List.copyOf(namedCaptures);
    }

    @Override
    public boolean matchesEntire(String input) {
      return false;
    }

    @Override
    public boolean containsMatch(String input) {
      return false;
    }

    @Override
    public java.util.Optional<jp.bsb.regex.RegexMatch> firstMatch(String input) {
      return java.util.Optional.empty();
    }

    @Override
    public jp.bsb.regex.RegexMatchCursor matchCursor(String input) {
      throw new UnsupportedOperationException();
    }
  }
}
