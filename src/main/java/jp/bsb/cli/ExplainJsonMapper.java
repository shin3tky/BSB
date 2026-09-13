package jp.bsb.cli;

import java.util.Objects;
import jp.bsb.explain.ProgramExplanation;
import jp.bsb.frontend.SourceText;

/** CLI非依存の説明モデルを`explain --json`版1 DTOへ写します。 */
final class ExplainJsonMapper {
  ExplainJson map(ProgramExplanation explanation, SourceText source) {
    Objects.requireNonNull(explanation, "explanation");
    Objects.requireNonNull(source, "source");
    return new ExplainJson(
        summary(explanation.summary()),
        explanation.builtinWords().stream().map(ExplainJsonMapper::builtin).toList(),
        explanation.userWords().stream().map(word -> user(word, source)).toList(),
        explanation.scopes().stream().map(scope -> scope(scope, source)).toList(),
        explanation.bindings().stream().map(binding -> binding(binding, source)).toList(),
        parameterized(explanation.parameterizedCapabilities(), source));
  }

  private static ExplainParameterizedCapabilitiesJson parameterized(
      ProgramExplanation.ParameterizedCapabilities value, SourceText source) {
    return new ExplainParameterizedCapabilitiesJson(
        value.schemaVersion(),
        value.declarations().stream()
            .map(
                declaration ->
                    new ExplainConnectionDeclarationJson(
                        declaration.name(),
                        declaration.spelling(),
                        JsonSourceLocationMapper.span(declaration.declaration(), source),
                        declaration.uses().stream()
                            .map(
                                use ->
                                    new ExplainConnectionUseJson(
                                        use.ownerWord(),
                                        use.operation(),
                                        use.reachableFromMain(),
                                        JsonSourceLocationMapper.span(use.location(), source)))
                            .toList()))
            .toList(),
        value.workspaceDeclarations().stream()
            .map(
                declaration ->
                    new ExplainConnectionDeclarationJson(
                        declaration.name(),
                        declaration.spelling(),
                        JsonSourceLocationMapper.span(declaration.declaration(), source),
                        declaration.uses().stream()
                            .map(
                                use ->
                                    new ExplainConnectionUseJson(
                                        use.ownerWord(),
                                        use.operation(),
                                        use.reachableFromMain(),
                                        JsonSourceLocationMapper.span(use.location(), source)))
                            .toList()))
            .toList(),
        value.summaryRequirements().stream().map(ExplainJsonMapper::requirement).toList(),
        value.userWords().stream()
            .map(
                word ->
                    new ExplainParameterizedUserWordJson(
                        word.name(),
                        word.directRequirements().stream()
                            .map(ExplainJsonMapper::requirement)
                            .toList(),
                        word.requirements().stream().map(ExplainJsonMapper::requirement).toList()))
            .toList());
  }

  private static ExplainResourceRequirementJson requirement(
      ProgramExplanation.ResourceRequirementEntry value) {
    return new ExplainResourceRequirementJson(value.kind(), value.name(), value.operations());
  }

  private static ExplainSummaryJson summary(ProgramExplanation.Summary value) {
    return new ExplainSummaryJson(
        value.entryPoint(),
        value.reachableUserWords(),
        value.reachableBuiltinWords(),
        value.capabilities(),
        value.effects());
  }

  private static ExplainBuiltinWordJson builtin(ProgramExplanation.BuiltinWordEntry value) {
    return new ExplainBuiltinWordJson(
        value.name(),
        value.aliases(),
        value.description(),
        stackEffect(value.stackEffect()),
        value.typeRule(),
        value.capabilities(),
        value.effects(),
        value.featureGroup(),
        value.example());
  }

  private static ExplainUserWordJson user(
      ProgramExplanation.UserWordEntry value, SourceText source) {
    return new ExplainUserWordJson(
        value.name(),
        value.spelling(),
        JsonSourceLocationMapper.span(value.declaration(), source),
        stackEffect(value.stackEffect()),
        value.reachableFromMain(),
        value.directCapabilities(),
        value.capabilities(),
        value.directEffects(),
        value.effects());
  }

  private static ExplainStackEffectJson stackEffect(ProgramExplanation.StackEffectEntry value) {
    return new ExplainStackEffectJson(value.inputs(), value.outputs(), value.returnsNormally());
  }

  private static ExplainScopeJson scope(ProgramExplanation.ScopeEntry value, SourceText source) {
    return new ExplainScopeJson(
        value.id(),
        value.kind(),
        value.parentId(),
        value.ownerWord(),
        JsonSourceLocationMapper.span(value.location(), source));
  }

  private static ExplainBindingJson binding(
      ProgramExplanation.BindingEntry value, SourceText source) {
    return new ExplainBindingJson(
        value.id(),
        value.name(),
        value.spelling(),
        value.kind(),
        value.storage(),
        value.scopeId(),
        value.type(),
        value.initializationOrder(),
        JsonSourceLocationMapper.span(value.declaration(), source),
        value.uses().stream().map(use -> bindingUse(use, source)).toList());
  }

  private static ExplainBindingUseJson bindingUse(
      ProgramExplanation.BindingUseEntry value, SourceText source) {
    return new ExplainBindingUseJson(
        value.spelling(), value.kind(), JsonSourceLocationMapper.span(value.location(), source));
  }
}
