package jp.bsb.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import jp.bsb.analyzer.AnalyzedProgram;
import jp.bsb.analyzer.SourceChecker;
import jp.bsb.analyzer.WordSignature;
import jp.bsb.binding.Binding;
import jp.bsb.binding.BindingId;
import jp.bsb.binding.BindingKind;
import jp.bsb.binding.BindingStorage;
import jp.bsb.binding.BindingTypeState;
import jp.bsb.binding.BindingUseKind;
import jp.bsb.binding.LexicalScope;
import jp.bsb.binding.LexicalScopeKind;
import jp.bsb.binding.NameResolution;
import jp.bsb.binding.ResolvedBindingUse;
import jp.bsb.binding.ScopeId;
import jp.bsb.binding.WordName;
import jp.bsb.diagnostics.DiagnosticCode;
import jp.bsb.diagnostics.SourcePosition;
import jp.bsb.diagnostics.SourceSpan;
import jp.bsb.frontend.ast.Program;
import jp.bsb.frontend.ast.StackEffect;
import jp.bsb.frontend.ast.ValueDeclaration;
import jp.bsb.frontend.ast.ValueReference;
import jp.bsb.frontend.ast.WordDefinition;
import jp.bsb.stdlib.ValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 束縛の宣言、参照、代入を検証済み保存スロットIRへ変換できることを検証します。 */
class BindingStorageIrGeneratorTest {
  private static final SourceSpan SPAN =
      new SourceSpan(new SourcePosition(0, 1, 1), new SourcePosition(1, 1, 2));

  @ParameterizedTest
  @ValueSource(
      strings = {
        "BIND-N001.bsb", "BIND-N002.bsb", "BIND-N003.bsb", "BIND-N004.bsb", "BIND-N005.bsb",
        "BIND-N006.bsb", "BIND-N007.bsb", "BIND-N008.bsb", "BIND-N009.bsb", "BIND-N010.bsb",
        "BIND-N011.bsb", "BIND-N012.bsb", "BIND-N013.bsb", "BIND-N014.bsb", "BIND-N015.bsb",
        "BIND-N016.bsb", "BIND-N017.bsb", "BIND-N018.bsb", "BIND-N019.bsb"
      })
  void generatesEveryNormalBindingSource(String sourceName) throws IOException {
    IrProgram program = generateSource(sourceName);

    assertTrue(program.instructionCount() > 0);
    assertTrue(
        program.globalInitializer().stream()
            .flatMap(word -> word.instructions().stream())
            .allMatch(instruction -> instruction.span() != null));
    assertTrue(
        program.userWords().values().stream()
            .flatMap(word -> word.instructions().stream())
            .allMatch(instruction -> instruction.span() != null));
  }

  @Test
  void lowersTheNormativeChapterToDeterministicStorageInstructions() throws IOException {
    var analysis = analyzeChapter();

    IrProgram first = new IrGenerator().generate(analysis).programForExecution();
    IrProgram second = new IrGenerator().generate(analysis).programForExecution();

    assertEquals(first, second);
    assertEquals(23, first.instructionCount());
    assertEquals(3, first.globalSlotCount());
    assertEquals(List.of("B1", "B2", "B3"), bindingIds(first.globalSlots()));
    assertEquals(List.of(0, 1, 2), slotIndices(first.globalSlots()));
    assertEquals(
        List.of("<大域初期化>", "メイン"), first.startupWords().stream().map(IrWord::name).toList());

    IrWord initializer = first.globalInitializer().orElseThrow();
    assertEquals(
        List.of(
            "PushConst",
            "InitializeGlobal",
            "PushConst",
            "InitializeGlobal",
            "PushConst",
            "InitializeGlobal",
            "Return"),
        opcodes(initializer));
    InitializeGlobal firstGlobal =
        assertInstanceOf(InitializeGlobal.class, initializer.instructions().get(1));
    assertEquals(new BindingId(1), firstGlobal.bindingId());
    assertEquals(0, firstGlobal.slotIndex());
    assertEquals(1, firstGlobal.span().start().line());
    assertEquals(5, firstGlobal.span().start().column());
    assertEquals(3, initializer.instructions().getLast().span().start().line());
    assertEquals(9, initializer.instructions().getLast().span().start().column());

    IrWord subtotal = word(first, "小計を加える");
    assertEquals(1, subtotal.localSlotCount());
    IrStorageSlot subtotalSlot = subtotal.localSlots().getFirst();
    assertEquals(new BindingId(4), subtotalSlot.bindingId());
    assertEquals(0, subtotalSlot.slotIndex());
    assertEquals(Optional.of("小計を加える"), subtotalSlot.ownerWord());
    assertEquals(
        List.of(
            "LoadGlobal",
            "LoadGlobal",
            "Call:掛ける",
            "InitializeLocal",
            "LoadGlobal",
            "LoadLocal",
            "Call:足す",
            "StoreGlobal",
            "Return"),
        opcodes(subtotal));
    InitializeLocal initializeLocal =
        assertInstanceOf(InitializeLocal.class, subtotal.instructions().get(3));
    assertEquals(subtotalSlot, initializeLocal.slot());
    assertEquals(6, initializeLocal.span().start().line());
    assertEquals(9, initializeLocal.span().start().column());
    StoreGlobal store = assertInstanceOf(StoreGlobal.class, subtotal.instructions().get(7));
    assertEquals(new BindingId(3), store.bindingId());
    assertEquals(7, store.span().start().line());
    assertEquals(25, store.span().start().column());
  }

  @Test
  void emitsNoSpecialWordWithoutGlobalsAndKeepsLocalsOnTheirOwningFrame() throws IOException {
    IrProgram program = generateSource("BIND-N004.bsb");

    assertTrue(program.globalInitializer().isEmpty());
    assertEquals(List.of("メイン"), program.startupWords().stream().map(IrWord::name).toList());
    IrWord localWord = word(program, "局所を表示する");
    assertEquals(1, localWord.localSlotCount());
    assertEquals(
        List.of("PushConst", "InitializeLocal", "LoadLocal", "Call:一行表示する", "Return"),
        opcodes(localWord));
    assertEquals(Optional.of("局所を表示する"), localWord.localSlots().getFirst().ownerWord());
    assertEquals(0, program.mainWord().localSlotCount());
  }

  @Test
  void emitsLoopLocalInitializationInsideTheBackEdge() throws IOException {
    IrWord main = generateSource("BIND-N014.bsb").mainWord();

    assertEquals(1, main.localSlotCount());
    assertEquals(
        List.of(
            "PushConst",
            "CountedLoopStart",
            "PushConst",
            "InitializeLocal",
            "LoadLocal",
            "PushConst",
            "Call:足す",
            "StoreLocal",
            "LoadLocal",
            "Call:一行表示する",
            "CountedLoopNext",
            "Return"),
        opcodes(main));
    CountedLoopNext backEdge = assertInstanceOf(CountedLoopNext.class, main.instructions().get(10));
    assertEquals(2, backEdge.bodyTargetIndex());
    assertInstanceOf(InitializeLocal.class, main.instructions().get(3));
  }

  @Test
  void countsEveryStorageInstructionTowardTheExistingIrLimit() throws IOException {
    IrProgram program = generateSource("BIND-N005.bsb");

    long storageCount =
        program.globalInitializer().stream()
                .flatMap(word -> word.instructions().stream())
                .filter(StorageInstruction.class::isInstance)
                .count()
            + program.userWords().values().stream()
                .flatMap(word -> word.instructions().stream())
                .filter(StorageInstruction.class::isInstance)
                .count();
    long actualCount =
        program.globalInitializer().stream().mapToLong(word -> word.instructions().size()).sum()
            + program.userWords().values().stream()
                .mapToLong(word -> word.instructions().size())
                .sum();

    assertEquals(3, storageCount);
    assertEquals(actualCount, program.instructionCount());
  }

  @Test
  void accepts250000AndRejects250001WhenTheBoundaryIsMadeOfStorageInstructions() {
    IrGenerationResult accepted =
        new IrGenerator().generate(syntheticStorageProgram(IrGenerator.MAX_INSTRUCTIONS - 2));
    IrGenerationResult rejected =
        new IrGenerator().generate(syntheticStorageProgram(IrGenerator.MAX_INSTRUCTIONS - 1));

    assertTrue(accepted.successful());
    assertEquals(IrGenerator.MAX_INSTRUCTIONS, accepted.programForExecution().instructionCount());
    long acceptedStorageInstructions =
        accepted.programForExecution().mainWord().instructions().stream()
            .filter(StorageInstruction.class::isInstance)
            .count();
    assertEquals(IrGenerator.MAX_INSTRUCTIONS - 1L, acceptedStorageInstructions);
    assertFalse(rejected.successful());
    assertEquals(DiagnosticCode.E_IR_LIMIT, rejected.diagnostics().getFirst().code());
    assertEquals("250001", rejected.diagnostics().getFirst().observed().orElseThrow());
  }

  @Test
  void omitsAnUnreachableDeclarationItsInitializerAndItsSlot() {
    String source =
        "早く戻るとは （--）\n"
            + "    戻る\n"
            + "    値は 変数 1。\n"
            + "こと。\n\n"
            + "メインとは （--）\n"
            + "    早く戻る\n"
            + "こと。\n";
    var analysis =
        new SourceChecker()
            .check("unreachable-storage.bsb", source.getBytes(StandardCharsets.UTF_8));

    assertTrue(analysis.successful(), analysis.diagnostics().toString());
    IrProgram program =
        new IrGenerator().generate(analysis.programForIrGeneration()).programForExecution();
    IrWord early = word(program, "早く戻る");
    assertEquals(0, early.localSlotCount());
    assertEquals(List.of("Return", "Return"), opcodes(early));
    assertTrue(early.instructions().stream().noneMatch(StorageInstruction.class::isInstance));
  }

  @Test
  void rejectsConstantStoreUnknownSlotsAndLocalLoadsWithoutDefiniteInitialization() {
    IrStorageSlot constant = localSlot(1, BindingKind.CONSTANT, 0);
    IrStorageSlot variable = localSlot(2, BindingKind.VARIABLE, 0);
    IrStorageSlot outside = localSlot(3, BindingKind.VARIABLE, 1);

    assertThrows(IllegalArgumentException.class, () -> new StoreLocal(constant, SPAN));
    IrStorageSlot globalConstant =
        new IrStorageSlot(
            new BindingId(4),
            "大域定数",
            BindingKind.CONSTANT,
            BindingStorage.GLOBAL,
            ValueType.INTEGER,
            0,
            Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> new StoreGlobal(globalConstant, SPAN));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new IrWord(
                new SymbolId(0),
                "メイン",
                List.of(new LoadLocal(variable, SPAN), new Return(SPAN)),
                List.of(variable)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new IrWord(
                new SymbolId(0),
                "メイン",
                List.of(
                    new InitializeLocal(variable, SPAN),
                    new LoadLocal(outside, SPAN),
                    new Return(SPAN)),
                List.of(variable)));
  }

  @Test
  void rejectsAGlobalLoadThatPrecedesInitialization() {
    IrStorageSlot global =
        new IrStorageSlot(
            new BindingId(1),
            "値",
            BindingKind.VARIABLE,
            BindingStorage.GLOBAL,
            ValueType.INTEGER,
            0,
            Optional.empty());
    var initializerSymbol = new SymbolId(1);
    var initializer =
        new IrWord(
            initializerSymbol,
            "<大域初期化>",
            List.of(
                new LoadGlobal(global, SPAN),
                new InitializeGlobal(global, SPAN),
                new Return(SPAN)));
    var mainSymbol = new SymbolId(0);
    var main = new IrWord(mainSymbol, "メイン", List.of(new Return(SPAN)));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new IrProgram(
                "invalid.bsb",
                mainSymbol,
                java.util.Map.of(mainSymbol, main),
                java.util.Map.of(),
                4,
                Optional.of(initializer),
                List.of(global)));
  }

  private static IrStorageSlot localSlot(int id, BindingKind kind, int index) {
    return new IrStorageSlot(
        new BindingId(id),
        "値" + id,
        kind,
        BindingStorage.LOCAL,
        ValueType.INTEGER,
        index,
        Optional.of("メイン"));
  }

  private static AnalyzedProgram syntheticStorageProgram(int loadCount) {
    SourceSpan programSpan = span(0, 100);
    SourceSpan wordNameSpan = span(1, 2);
    SourceSpan declarationNameSpan = span(10, 11);
    SourceSpan declarationSpan = span(10, 20);
    SourceSpan referenceSpan = span(30, 31);
    var reference = new ValueReference("値", "値", referenceSpan);
    var declaration =
        new ValueDeclaration(
            "値",
            "値",
            declarationNameSpan,
            span(11, 12),
            BindingKind.VARIABLE,
            span(13, 14),
            List.of(),
            span(19, 20),
            declarationSpan);
    var effect = new StackEffect(List.of(), List.of(), span(3, 4));
    var main =
        new WordDefinition(
            "メイン",
            "メイン",
            wordNameSpan,
            effect,
            bodyWithRepeatedReference(declaration, reference, loadCount),
            span(90, 91),
            span(1, 91));
    var syntax = new Program("synthetic-storage.bsb", List.of(main), programSpan);

    var globalScope = LexicalScope.global(new ScopeId(1), programSpan);
    var wordScope =
        new LexicalScope(
            new ScopeId(2),
            LexicalScopeKind.WORD_BODY,
            Optional.of(globalScope.id()),
            Optional.of("メイン"),
            main.span());
    var binding =
        new Binding(
            new BindingId(1),
            "値",
            "値",
            BindingKind.VARIABLE,
            BindingStorage.LOCAL,
            wordScope.id(),
            BindingTypeState.inferred(ValueType.INTEGER),
            OptionalInt.empty(),
            declarationNameSpan,
            declarationSpan);
    var resolution =
        new NameResolution(
            List.of(new WordName("メイン", "メイン", wordNameSpan), binding),
            List.of(globalScope, wordScope),
            List.of(new ResolvedBindingUse(reference, "値", BindingUseKind.READ, binding.id())));
    return new AnalyzedProgram(
        syntax,
        Map.of("メイン", new WordSignature(List.of(), List.of())),
        java.util.Set.of(),
        resolution);
  }

  private static List<jp.bsb.frontend.ast.BodyElement> bodyWithRepeatedReference(
      ValueDeclaration declaration, ValueReference reference, int loadCount) {
    var body = new java.util.ArrayList<jp.bsb.frontend.ast.BodyElement>(loadCount + 1);
    body.add(declaration);
    body.addAll(Collections.nCopies(loadCount, reference));
    return List.copyOf(body);
  }

  private static SourceSpan span(long start, long end) {
    return new SourceSpan(
        new SourcePosition(start, 1, Math.toIntExact(start + 1)),
        new SourcePosition(end, 1, Math.toIntExact(end + 1)));
  }

  private static List<String> bindingIds(List<IrStorageSlot> slots) {
    return slots.stream().map(slot -> slot.bindingId().displayName()).toList();
  }

  private static List<Integer> slotIndices(List<IrStorageSlot> slots) {
    return slots.stream().map(IrStorageSlot::slotIndex).toList();
  }

  private static List<String> opcodes(IrWord word) {
    return word.instructions().stream().map(IrInstruction::opcode).toList();
  }

  private static jp.bsb.analyzer.AnalyzedProgram analyzeChapter() throws IOException {
    byte[] source = resourceBytes("chapter/bindings-chapter.bsb");
    var analysis = new SourceChecker().check("bindings-chapter.bsb", source);
    assertTrue(analysis.successful(), analysis.diagnostics().toString());
    return analysis.programForIrGeneration();
  }

  private static IrProgram generateSource(String sourceName) throws IOException {
    byte[] source = resourceBytes("sources/" + sourceName);
    var analysis = new SourceChecker().check(sourceName, source);
    assertTrue(analysis.successful(), analysis.diagnostics().toString());
    return new IrGenerator().generate(analysis.programForIrGeneration()).programForExecution();
  }

  private static IrWord word(IrProgram program, String name) {
    return program.userWords().values().stream()
        .filter(word -> word.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static byte[] resourceBytes(String relativePath) throws IOException {
    String resource = "/conformance/bindings/" + relativePath;
    try (var input = BindingStorageIrGeneratorTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("missing test resource: " + resource);
      }
      return input.readAllBytes();
    }
  }
}
