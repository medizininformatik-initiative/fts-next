package care.smith.fts.deidentifier.allowlist;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toCollection;

import care.smith.fts.deidentifier.Registry.Registration;
import care.smith.fts.deidentifier.Rule;
import care.smith.fts.deidentifier.RuleSet;
import java.util.List;
import java.util.TreeSet;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Resource;

/**
 * An allow-list profile: an element that no matched {@link Module} lists in its base is removed. If
 * several modules match, their handler chains for a path are merged. A profile cannot be built if
 * its base lists a path that the engine never visits, or if a terminal handler can run before
 * another handler.
 */
public record Profile(List<Module> modules) implements RuleSet {

  public Profile {
    modules = List.copyOf(modules);
    modules.stream()
        .collect(groupingBy(module -> module.pattern().resourceType()))
        .values()
        .forEach(Profile::requireTerminalsLast);
  }

  /**
   * All modules of one resource type can match the same resource, in no defined order, so their
   * chains are checked together on every path that one of them keeps. Patterns cannot be evaluated
   * without a resource, so this over-approximates the modules that match at run time.
   */
  private static void requireTerminalsLast(List<Module> modulesOfOneType) {
    modulesOfOneType.stream()
        .flatMap(module -> module.base().stream())
        .collect(toCollection(TreeSet::new))
        .forEach(
            path ->
                HandlerChain.at(modulesOfOneType, path, BasePaths.leafType(path))
                    .requireTerminalLast(path));
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
     * The merged decision for one element: the type handlers of all matched modules for the exact
     * class of the element, then the path handlers of the modules that keep the path. An element
     * that no matched module lists in its base is removed, whatever the type handlers say; one that
     * a module lists without a handler is kept unchanged.
     */
    @Override
    public Rule ruleFor(List<String> path, Base element) {
      var pathKey = String.join(".", path);
      if (matched.stream().noneMatch(m -> m.base().contains(pathKey))) {
        return Rule.REMOVE;
      }
      return new Rule.Apply(
          HandlerChain.at(matched, pathKey, element.getClass()).all().stream()
              .map(Registration::handler)
              .toList());
    }
  }

  @Override
  public Rules rulesFor(Resource resource) {
    return new Rules(modules.stream().filter(m -> m.pattern().matches(resource)).toList());
  }
}
