package jp.bsb.cli;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** `explain --json`版1の説明データです。 */
record ExplainJson(
    ExplainSummaryJson summary,
    List<ExplainBuiltinWordJson> builtinWords,
    List<ExplainUserWordJson> userWords,
    List<ExplainScopeJson> scopes,
    List<ExplainBindingJson> bindings,
    ExplainParameterizedCapabilitiesJson parameterizedCapabilities) {
  ExplainJson(
      ExplainSummaryJson summary,
      List<ExplainBuiltinWordJson> builtinWords,
      List<ExplainUserWordJson> userWords,
      List<ExplainScopeJson> scopes,
      List<ExplainBindingJson> bindings) {
    this(
        summary,
        builtinWords,
        userWords,
        scopes,
        bindings,
        new ExplainParameterizedCapabilitiesJson(1, List.of(), List.of(), List.of(), List.of()));
  }

  ExplainJson {
    Objects.requireNonNull(summary, "summary");
    builtinWords = List.copyOf(builtinWords);
    userWords = List.copyOf(userWords);
    scopes = List.copyOf(scopes);
    bindings = List.copyOf(bindings);
    Objects.requireNonNull(parameterizedCapabilities, "parameterizedCapabilities");
  }
}

record ExplainParameterizedCapabilitiesJson(
    int schemaVersion,
    List<ExplainConnectionDeclarationJson> declarations,
    List<ExplainConnectionDeclarationJson> workspaceDeclarations,
    List<ExplainResourceRequirementJson> summaryRequirements,
    List<ExplainParameterizedUserWordJson> userWords) {
  ExplainParameterizedCapabilitiesJson {
    if (schemaVersion != 1) {
      throw new IllegalArgumentException("unsupported parameterized capability schema version");
    }
    declarations = List.copyOf(declarations);
    workspaceDeclarations = List.copyOf(workspaceDeclarations);
    summaryRequirements = List.copyOf(summaryRequirements);
    userWords = List.copyOf(userWords);
  }
}

record ExplainConnectionDeclarationJson(
    String name, String spelling, JsonSpan declaration, List<ExplainConnectionUseJson> uses) {
  ExplainConnectionDeclarationJson {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(spelling, "spelling");
    Objects.requireNonNull(declaration, "declaration");
    uses = List.copyOf(uses);
  }
}

record ExplainConnectionUseJson(
    String ownerWord, String operation, boolean reachableFromMain, JsonSpan location) {
  ExplainConnectionUseJson {
    Objects.requireNonNull(ownerWord, "ownerWord");
    Objects.requireNonNull(operation, "operation");
    Objects.requireNonNull(location, "location");
  }
}

record ExplainResourceRequirementJson(String kind, String name, List<String> operations) {
  ExplainResourceRequirementJson {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(name, "name");
    operations = List.copyOf(operations);
  }
}

record ExplainParameterizedUserWordJson(
    String name,
    List<ExplainResourceRequirementJson> directRequirements,
    List<ExplainResourceRequirementJson> requirements) {
  ExplainParameterizedUserWordJson {
    Objects.requireNonNull(name, "name");
    directRequirements = List.copyOf(directRequirements);
    requirements = List.copyOf(requirements);
  }
}

record ExplainSummaryJson(
    String entryPoint,
    List<String> reachableUserWords,
    List<String> reachableBuiltinWords,
    List<String> capabilities,
    List<String> effects) {
  ExplainSummaryJson {
    Objects.requireNonNull(entryPoint, "entryPoint");
    reachableUserWords = List.copyOf(reachableUserWords);
    reachableBuiltinWords = List.copyOf(reachableBuiltinWords);
    capabilities = List.copyOf(capabilities);
    effects = List.copyOf(effects);
  }
}

record ExplainBuiltinWordJson(
    String name,
    List<String> aliases,
    String description,
    ExplainStackEffectJson stackEffect,
    String typeRule,
    List<String> capabilities,
    List<String> effects,
    String featureGroup,
    String example) {
  ExplainBuiltinWordJson {
    Objects.requireNonNull(name, "name");
    aliases = List.copyOf(aliases);
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(stackEffect, "stackEffect");
    Objects.requireNonNull(typeRule, "typeRule");
    capabilities = List.copyOf(capabilities);
    effects = List.copyOf(effects);
    Objects.requireNonNull(featureGroup, "featureGroup");
    Objects.requireNonNull(example, "example");
  }
}

record ExplainUserWordJson(
    String name,
    String spelling,
    JsonSpan declaration,
    ExplainStackEffectJson stackEffect,
    boolean reachableFromMain,
    List<String> directCapabilities,
    List<String> capabilities,
    List<String> directEffects,
    List<String> effects) {
  ExplainUserWordJson {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(spelling, "spelling");
    Objects.requireNonNull(declaration, "declaration");
    Objects.requireNonNull(stackEffect, "stackEffect");
    directCapabilities = List.copyOf(directCapabilities);
    capabilities = List.copyOf(capabilities);
    directEffects = List.copyOf(directEffects);
    effects = List.copyOf(effects);
  }
}

record ExplainStackEffectJson(List<String> inputs, List<String> outputs, boolean returnsNormally) {
  ExplainStackEffectJson {
    inputs = List.copyOf(inputs);
    outputs = List.copyOf(outputs);
  }
}

record ExplainScopeJson(
    String id,
    String kind,
    Optional<String> parentId,
    Optional<String> ownerWord,
    JsonSpan location) {
  ExplainScopeJson {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(kind, "kind");
    parentId = Objects.requireNonNull(parentId, "parentId");
    ownerWord = Objects.requireNonNull(ownerWord, "ownerWord");
    Objects.requireNonNull(location, "location");
  }
}

record ExplainBindingJson(
    String id,
    String name,
    String spelling,
    String kind,
    String storage,
    String scopeId,
    String type,
    OptionalInt initializationOrder,
    JsonSpan declaration,
    List<ExplainBindingUseJson> uses) {
  ExplainBindingJson {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(spelling, "spelling");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(storage, "storage");
    Objects.requireNonNull(scopeId, "scopeId");
    Objects.requireNonNull(type, "type");
    initializationOrder = Objects.requireNonNull(initializationOrder, "initializationOrder");
    Objects.requireNonNull(declaration, "declaration");
    uses = List.copyOf(uses);
  }
}

record ExplainBindingUseJson(String spelling, String kind, JsonSpan location) {
  ExplainBindingUseJson {
    Objects.requireNonNull(spelling, "spelling");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(location, "location");
  }
}
