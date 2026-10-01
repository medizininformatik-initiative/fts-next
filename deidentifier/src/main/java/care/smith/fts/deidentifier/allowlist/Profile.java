package care.smith.fts.deidentifier.allowlist;

import care.smith.fts.deidentifier.Registry.Registration;
import care.smith.fts.deidentifier.Rule;
import care.smith.fts.deidentifier.RuleSet;
import java.util.List;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Resource;

/**
 * An allow-list profile: an element that no matched {@link Module} lists in its base is removed. If
 * several modules match, their handler chains for a path are merged.
 */
public record Profile(List<Module> modules) implements RuleSet {

  public Profile {
    modules = List.copyOf(modules);
  }

  /**
   * The rules that apply to one resource: the modules whose patterns match it, resolved once. The
   * patterns are constant per resource, so the engine asks once and queries the result for every
   * element instead of re-matching per primitive.
   */
  public static final class Rules implements ResourceRules {

    private final List<Module> matched;

    private Rules(List<Module> matched) {
      this.matched = matched;
    }

    /**
     * The merged decision for one element: the path handlers of all matched modules. An element
     * that no matched module lists in its base is removed; one that a module lists without a
     * handler is kept unchanged.
     */
    @Override
    public Rule ruleFor(List<String> path, Base element) {
      String pathKey = String.join(".", path);
      List<Module> keeping = matched.stream().filter(m -> m.base().contains(pathKey)).toList();
      if (keeping.isEmpty()) {
        return Rule.REMOVE;
      }
      return new Rule.Apply(
          keeping.stream()
              .flatMap(m -> m.pathHandlers().getOrDefault(pathKey, List.of()).stream())
              .map(Registration::handler)
              .toList());
    }
  }

  @Override
  public Rules rulesFor(Resource resource) {
    return new Rules(modules.stream().filter(m -> m.pattern().matches(resource)).toList());
  }
}
