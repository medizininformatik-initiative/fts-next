package care.smith.fts.deidentifier;

import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toCollection;

import care.smith.fts.deidentifier.RuleSet.ResourceRules;
import care.smith.fts.deidentifier.internal.HapiReflection;
import care.smith.fts.deidentifier.internal.HapiReflection.FhirChild;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Resource;

/**
 * De-identification engine on the HAPI FHIR R4 typed model. It walks each resource and asks a
 * {@link RuleSet} what to do with every element; what an element that no rule names becomes is the
 * rule set's decision. The input resource is not modified; a fresh resource is built.
 */
public class Deidentifier {

  private final RuleSet ruleSet;

  public Deidentifier(RuleSet ruleSet) {
    this.ruleSet = requireNonNull(ruleSet);
  }

  /**
   * @return the de-identified resource, or empty if every element was removed
   */
  public Optional<Resource> deidentify(Resource resource) {
    requireNonNull(resource);
    // the rules are constant per resource, so they are resolved once here
    ResourceRules rules = ruleSet.rulesFor(resource);
    return deidentifyElement(List.of(resource.fhirType()), resource, HandlerContext.empty(), rules)
        .map(Resource.class::cast);
  }

  /**
   * A child that survived de-identification, with its path and the value to write into the fresh
   * element.
   */
  private record Kept(FhirChild child, List<String> path, Object value) {

    /**
     * A handler can return a value that the field does not accept; the error then names the path.
     */
    void writeInto(Base target) {
      try {
        child.copyInto(target, value);
      } catch (IllegalArgumentException e) {
        throw new IllegalStateException(
            "The handlers of %s returned a value that does not fit: %s"
                .formatted(String.join(".", path), e.getMessage()),
            e);
      }
    }
  }

  private Optional<Base> deidentifyElement(
      List<String> path, Base base, HandlerContext context, ResourceRules rules) {
    HandlerContext childContext = context.child(base);
    List<Kept> kept =
        HapiReflection.childrenWithValue(base).stream()
            .flatMap(
                child -> {
                  List<String> childPath =
                      append(path, HapiReflection.toPathElement(child.property(), child.value()));
                  return deidentifyValue(childPath, child.value(), childContext, rules)
                      .map(value -> new Kept(child, childPath, value))
                      .stream();
                })
            .toList();
    if (kept.isEmpty()) {
      return Optional.empty();
    }
    Base empty = HapiReflection.newEmptyInstance(base.getClass());
    kept.forEach(k -> k.writeInto(empty));
    return Optional.of(empty);
  }

  private Optional<?> deidentifyValue(
      List<String> path, Object value, HandlerContext context, ResourceRules rules) {
    return switch (value) {
      case PrimitiveType<?> primitive -> deidentifyPrimitive(path, primitive, context, rules);
      // the narrative restates the resource as XHTML, which no rule can look into; its status alone
      // is not a valid narrative, so de-identification always drops the whole element
      case Narrative ignored -> Optional.empty();
      case Base base -> deidentifyElement(path, base, context, rules);
      case List<?> list -> deidentifyList(path, list, context, rules);
      default -> throw new IllegalStateException("Unexpected element of type " + value.getClass());
    };
  }

  /**
   * The survivors of a list, as a mutable list: {@link FhirChild#copyInto} stores it in the HAPI
   * field, and HAPI appends to that list later.
   */
  private Optional<List<Object>> deidentifyList(
      List<String> path, List<?> list, HandlerContext context, ResourceRules rules) {
    List<Object> kept =
        list.stream()
            .flatMap(element -> deidentifyValue(path, element, context, rules).stream())
            .collect(toCollection(ArrayList::new));
    return Optional.of(kept).filter(not(List::isEmpty));
  }

  /**
   * The extensions of the input primitive are elements like any other: each goes through the rule
   * set, and those with a surviving part are kept. Extensions a handler adds are removed.
   */
  private Optional<PrimitiveType<?>> deidentifyPrimitive(
      List<String> path, PrimitiveType<?> primitive, HandlerContext context, ResourceRules rules) {
    PrimitiveType<?> copy = (PrimitiveType<?>) primitive.copy();
    // the copy carries the element id of the input, which the rule set is never asked about
    copy.setId(null);
    Optional<PrimitiveType<?>> deidentified =
        applyHandlers(path, primitive, copy, context, rules).map(value -> (PrimitiveType<?>) value);
    List<Extension> extensions = deidentifyExtensions(path, primitive, context, rules);
    if (deidentified.isEmpty() && extensions.isEmpty()) {
      return Optional.empty();
    }
    // a primitive that lost its value can still carry an extension
    PrimitiveType<?> result =
        deidentified.orElseGet(() -> HapiReflection.newEmptyInstance(primitive.getClass()));
    result.setExtension(new ArrayList<>(extensions));
    return Optional.of(result);
  }

  /**
   * The extensions of the input primitive that survive the rule set. They are children of the
   * primitive, so a handler under them sees it as an ancestor.
   */
  private List<Extension> deidentifyExtensions(
      List<String> path, PrimitiveType<?> primitive, HandlerContext context, ResourceRules rules) {
    List<String> extensionPath = append(path, "extension");
    HandlerContext extensionContext = context.child(primitive);
    return primitive.getExtension().stream()
        .flatMap(
            extension ->
                deidentifyElement(extensionPath, extension, extensionContext, rules)
                    .map(Extension.class::cast)
                    .stream())
        .toList();
  }

  /** The rule set sees the element of the input resource, the handlers work on its copy. */
  private Optional<Object> applyHandlers(
      List<String> path,
      PrimitiveType<?> input,
      PrimitiveType<?> copy,
      HandlerContext context,
      ResourceRules rules) {
    return switch (rules.ruleFor(path, input)) {
      case Rule.Remove ignored -> Optional.empty();
      case Rule.Apply apply -> applyChain(apply.handlers(), copy, context);
    };
  }

  /**
   * Applies the handlers in order, stopping at the first one that removes the value. A handler that
   * returns empty has decided the element is gone, and the next handler in the chain is written for
   * a value, not for its absence.
   */
  private static Optional<Object> applyChain(
      List<DeidentifierHandler<Object>> handlers, Object value, HandlerContext context) {
    return handlers.stream()
        .<Function<Optional<Object>, Optional<Object>>>map(
            handler -> current -> current.flatMap(v -> handler.apply(v, context)))
        .reduce(Function.identity(), Function::andThen)
        .apply(Optional.of(value));
  }

  private static <T> List<T> append(List<T> list, T element) {
    return Stream.concat(list.stream(), Stream.of(element)).toList();
  }
}
