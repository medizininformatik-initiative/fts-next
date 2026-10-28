package care.smith.fts.deidentifier;

import static care.smith.fts.deidentifier.internal.HapiReflection.childrenWithValue;
import static care.smith.fts.deidentifier.internal.HapiReflection.newEmptyInstance;
import static care.smith.fts.deidentifier.internal.HapiReflection.toPathElement;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toCollection;

import care.smith.fts.deidentifier.RuleSet.ResourceRules;
import care.smith.fts.deidentifier.internal.HapiReflection.FhirChild;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleEntryRequestComponent;
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

  private static final Pattern TYPE_ONLY_URL = Pattern.compile("[^/?]+");

  private static final Pattern TYPE_AND_ID_URL = Pattern.compile("[^/?]+/[^/?]+");

  private final RuleSet ruleSet;

  public Deidentifier(RuleSet ruleSet) {
    this.ruleSet = requireNonNull(ruleSet);
  }

  /**
   * De-identifies a resource of the patient. A {@link Bundle} goes to {@link #deidentifyBundle}.
   *
   * @return the de-identified resource, or empty if every element was removed
   */
  public Optional<Resource> deidentify(Resource resource, String patientIdentifier) {
    requireNonNull(resource);
    if (resource instanceof Bundle bundle) {
      return Optional.of(deidentifyBundle(bundle, patientIdentifier));
    }
    return walk(resource, HandlerContext.of(List.of(), patientIdentifier));
  }

  /**
   * De-identifies every entry resource and builds a bundle skeleton around the survivors. A bundle
   * never disappears: its skeleton survives even when no entry does.
   */
  public Bundle deidentifyBundle(Bundle bundle, String patientIdentifier) {
    requireNonNull(bundle);
    var root = HandlerContext.of(List.of(), patientIdentifier);
    var result = new Bundle();
    if (bundle.hasType()) {
      result.setType(bundle.getType());
    }
    var entries = bundle.getEntry();
    for (var i = 0; i < entries.size(); i++) {
      deidentifyEntry(i, entries.get(i), root).ifPresent(result::addEntry);
    }
    return result;
  }

  private Optional<BundleEntryComponent> deidentifyEntry(
      int index, BundleEntryComponent entry, HandlerContext root) {
    if (!entry.hasResource()) {
      return Optional.empty();
    }
    var resource = entry.getResource();
    if (resource instanceof Bundle) {
      throw new IllegalArgumentException(
          "Bundle entry %d is a bundle: nested bundles are not supported".formatted(index));
    }
    return walk(resource, root).map(deidentified -> rebuildEntry(entry, deidentified));
  }

  /**
   * The entry around a de-identified resource. Of the request only {@code method} and {@code url}
   * survive, and only while the url names nothing the source system chose: see {@link #requestUrl}.
   */
  private static BundleEntryComponent rebuildEntry(
      BundleEntryComponent entry, Resource deidentified) {
    var rebuilt = new BundleEntryComponent().setResource(deidentified);
    // getRequest() would add an empty request to the input entry
    Optional.of(entry)
        .filter(BundleEntryComponent::hasRequest)
        .map(BundleEntryComponent::getRequest)
        .flatMap(request -> rebuildRequest(request, deidentified))
        .ifPresent(rebuilt::setRequest);
    return rebuilt;
  }

  /** The {@code method} and {@code url} of the request, or empty if its url does not survive. */
  private static Optional<BundleEntryRequestComponent> rebuildRequest(
      BundleEntryRequestComponent request, Resource deidentified) {
    return Optional.ofNullable(request.getUrl())
        .flatMap(url -> requestUrl(url, deidentified))
        .map(url -> new BundleEntryRequestComponent().setMethod(request.getMethod()).setUrl(url));
  }

  /**
   * A url that names only a type ({@code Patient}) stays. A url {@code Type/id} names the entry's
   * own resource, so it takes the id of the de-identified resource and never disagrees with it;
   * without a surviving id it goes. Any other url, e.g. a conditional {@code
   * Patient?identifier=sys|12345}, names the resource by what the source system chose and goes.
   */
  private static Optional<String> requestUrl(String url, Resource deidentified) {
    if (TYPE_ONLY_URL.matcher(url).matches()) {
      return Optional.of(url);
    }
    if (TYPE_AND_ID_URL.matcher(url).matches()) {
      return Optional.ofNullable(deidentified.getIdElement().getIdPart())
          .map(id -> deidentified.fhirType() + "/" + id);
    }
    return Optional.empty();
  }

  private Optional<Resource> walk(Resource resource, HandlerContext root) {
    // the rules are constant per resource, so they are resolved once here
    var rules = requireNonNull(ruleSet.rulesFor(resource), () -> ruleSet + " returned no rules");
    return new ResourceWalk(rules).deidentify(resource, root);
  }

  /**
   * The de-identification of one resource under the rules resolved for it. The rules stay the same
   * for the whole resource, so the walk holds them; where the walk is changes with every element,
   * so each step gets its {@link Position}.
   */
  private static final class ResourceWalk {

    private final ResourceRules rules;

    ResourceWalk(ResourceRules rules) {
      this.rules = rules;
    }

    Optional<Resource> deidentify(Resource resource, HandlerContext root) {
      return deidentifyElement(Position.root(resource, root), resource).map(Resource.class::cast);
    }

    /**
     * Where the walk is: the path the rule set is asked about, and the ancestors a handler sees.
     * Both go one level deeper together.
     */
    private record Position(List<String> path, HandlerContext context) {

      static Position root(Resource resource, HandlerContext context) {
        return new Position(List.of(resource.fhirType()), context);
      }

      /** The children of {@code parent}, which is at this position. */
      Siblings childrenOf(Base parent) {
        return new Siblings(path, context.child(parent));
      }
    }

    /**
     * The children of one element. They share one context, so a handler that keeps state per
     * context treats siblings alike, e.g. the start and end of a period.
     */
    private record Siblings(List<String> parentPath, HandlerContext context) {

      Position child(String element) {
        return new Position(append(parentPath, element), context);
      }
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
        } catch (IllegalArgumentException doesNotFit) {
          throw new IllegalStateException(
              "The handlers of %s returned a value that does not fit: %s"
                  .formatted(String.join(".", path), doesNotFit.getMessage()),
              doesNotFit);
        }
      }
    }

    private Optional<Base> deidentifyElement(Position at, Base base) {
      var siblings = at.childrenOf(base);
      var kept =
          childrenWithValue(base).stream()
              .flatMap(child -> deidentifyChild(siblings, child).stream())
              .toList();
      if (kept.isEmpty()) {
        return Optional.empty();
      }
      var fresh = newEmptyInstance(base.getClass());
      kept.forEach(k -> k.writeInto(fresh));
      return Optional.of(fresh);
    }

    /** One child, or empty if the rule set removes all of it. */
    private Optional<Kept> deidentifyChild(Siblings siblings, FhirChild child) {
      var at = siblings.child(toPathElement(child.property(), child.value()));
      return deidentifyValue(at, child.value()).map(value -> new Kept(child, at.path(), value));
    }

    private Optional<?> deidentifyValue(Position at, Object value) {
      return switch (value) {
        case PrimitiveType<?> primitive -> deidentifyPrimitive(at, primitive);
        // the narrative restates the resource as XHTML, which no rule can look into; its status
        // alone is not a valid narrative, so de-identification always drops the whole element
        case Narrative ignored -> Optional.empty();
        case Base base -> deidentifyElement(at, base);
        case List<?> list -> deidentifyList(at, list);
        default ->
            throw new IllegalStateException("Unexpected element of type " + value.getClass());
      };
    }

    /**
     * The survivors of a list, as a mutable list: {@link FhirChild#copyInto} stores it in the HAPI
     * field, and HAPI appends to that list later.
     */
    private Optional<List<Object>> deidentifyList(Position at, List<?> list) {
      List<Object> kept =
          list.stream()
              .flatMap(element -> deidentifyValue(at, element).stream())
              .collect(toCollection(ArrayList::new));
      return Optional.of(kept).filter(not(List::isEmpty));
    }

    /**
     * The extensions of the input primitive are elements like any other: each goes through the rule
     * set, and those with a surviving part are kept. The extensions a handler adds follow them,
     * unfiltered: a handler is registry code, not resource data.
     */
    private Optional<PrimitiveType<?>> deidentifyPrimitive(
        Position at, PrimitiveType<?> primitive) {
      var value = deidentifyPrimitiveValue(at, primitive, handlerInput(primitive));
      var extensions = deidentifyExtensions(at, primitive);
      if (value.isEmpty() && extensions.isEmpty()) {
        return Optional.empty();
      }
      // a primitive that lost its value can still carry an extension
      var result = value.orElseGet(() -> newEmptyInstance(primitive.getClass()));
      prependExtensions(result, extensions);
      return Optional.of(result);
    }

    /**
     * The rule set sees the input primitive, the handlers work on {@code copy}. The copy is made
     * before the rule set is asked, so a primitive that cannot be copied stops the resource even
     * where the rule set removes it.
     */
    private Optional<PrimitiveType<?>> deidentifyPrimitiveValue(
        Position at, PrimitiveType<?> primitive, PrimitiveType<?> copy) {
      return switch (rules.ruleFor(at.path(), primitive)) {
        case Rule.Remove ignored -> Optional.empty();
        case Rule.Apply apply ->
            applyChain(apply.handlers(), copy, at.context()).map(ResourceWalk::asPrimitive);
      };
    }

    /**
     * A copy of the input primitive without its element id, which the rule set is never asked
     * about, and without its extensions, which the rule set decides on separately. So every
     * extension on the handler result is one the handlers added.
     */
    private static PrimitiveType<?> handlerInput(PrimitiveType<?> primitive) {
      var copy = (PrimitiveType<?>) primitive.copy();
      copy.setId(null);
      copy.setExtension(new ArrayList<>());
      return copy;
    }

    private static PrimitiveType<?> asPrimitive(Object value) {
      return (PrimitiveType<?>) value;
    }

    /**
     * The extensions of the input primitive that survive the rule set. They are children of the
     * primitive, so a handler under them sees it as an ancestor.
     */
    private List<Extension> deidentifyExtensions(Position at, PrimitiveType<?> primitive) {
      var extensionAt = at.childrenOf(primitive).child("extension");
      return primitive.getExtension().stream()
          .flatMap(extension -> deidentifyExtension(extensionAt, extension).stream())
          .toList();
    }

    private Optional<Extension> deidentifyExtension(Position at, Extension extension) {
      return deidentifyElement(at, extension).map(Extension.class::cast);
    }

    /** The kept input extensions go first, the extensions the handlers added follow. */
    private static void prependExtensions(PrimitiveType<?> primitive, List<Extension> extensions) {
      var all = new ArrayList<Extension>(extensions);
      all.addAll(primitive.getExtension());
      primitive.setExtension(all);
    }

    /**
     * Applies the handlers in order, stopping at the first one that removes the value. A handler
     * that returns empty has decided the element is gone, and the next handler in the chain is
     * written for a value, not for its absence.
     */
    private static Optional<Object> applyChain(
        List<DeidentifierHandler<Object>> handlers, Object value, HandlerContext context) {
      var current = Optional.of(value);
      for (var handler : handlers) {
        current = current.flatMap(v -> handler.apply(v, context));
      }
      return current;
    }

    private static <T> List<T> append(List<T> list, T element) {
      return Stream.concat(list.stream(), Stream.of(element)).toList();
    }
  }
}
