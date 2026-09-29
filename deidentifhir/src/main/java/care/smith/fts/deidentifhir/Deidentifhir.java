package care.smith.fts.deidentifhir;

import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;

import care.smith.fts.deidentifhir.internal.HapiReflection;
import care.smith.fts.deidentifhir.internal.HapiReflection.FhirChild;
import com.typesafe.config.Config;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

/**
 * Whitelist-based de-identification engine on the HAPI FHIR R4 typed model. Fields that no module
 * keeps are removed. The input resource is not modified; a fresh resource is built.
 */
public class Deidentifhir {

  private final Profile profile;

  private final PseudonymIdentity identity;

  Deidentifhir(Profile profile, PseudonymIdentity identity) {
    this.profile = profile;
    this.identity = identity;
  }

  public static Deidentifhir fromConfig(Config config) {
    return fromConfig(config, new Registry());
  }

  public static Deidentifhir fromConfig(Config config, Registry registry) {
    PseudonymIdentity identity =
        registry.referenceHandler().map(PseudonymIdentity::of).orElse(PseudonymIdentity.none());
    return new Deidentifhir(Profile.parse(config, registry), identity);
  }

  /**
   * @return the de-identified resource, or empty if every element was removed
   */
  public Optional<Resource> deidentify(Resource resource) {
    return deidentify(requireNonNull(resource), HandlerContext.empty());
  }

  /**
   * De-identifies a bundle. Unlike a single resource, a bundle never disappears: its skeleton
   * survives even when no entry does.
   *
   * @param patientIdentifier the patient this call is about, passed to every handler
   */
  public Bundle deidentifyBundle(Bundle bundle, String patientIdentifier) {
    return deidentifyBundle(
        requireNonNull(bundle), HandlerContext.of(List.of(), requireNonNull(patientIdentifier)));
  }

  private Optional<Resource> deidentify(Resource resource, HandlerContext context) {
    if (resource instanceof Bundle bundle) {
      return Optional.of(deidentifyBundle(bundle, context));
    }
    // the patterns are constant per resource, so they are matched once here
    Profile.Rules rules = profile.rulesFor(resource);
    return deidentifyElement(List.of(resource.fhirType()), resource, context, rules)
        .map(Resource.class::cast);
  }

  /**
   * De-identifies every entry of a bundle and rebuilds the bundle skeleton around the survivors.
   *
   * <p>The skeleton is what makes the output a readable FHIR bundle rather than a bare list of
   * resources: {@code Bundle.type} is kept, and so are the {@code method} and {@code url} of a
   * surviving {@code entry.request}, while {@code entry.fullUrl} and the {@code url} are
   * pseudonymized, because they carry what the source system called the resource. Everything else
   * has to be kept by a module. An entry whose resource no module keeps is dropped, and so is one
   * left with nothing at all.
   */
  private Bundle deidentifyBundle(Bundle bundle, HandlerContext context) {
    Bundle deidentifiedBundle = new Bundle();
    if (bundle.hasType()) {
      deidentifiedBundle.setType(bundle.getType());
    }
    HandlerContext bundleContext = context.withEntryTypes(entryTypes(bundle));
    HandlerContext entryContext = bundleContext.child(bundle);
    bundle.getEntry().stream()
        .flatMap(entry -> deidentifyEntry(entry, bundleContext, entryContext).stream())
        .forEach(deidentifiedBundle::addEntry);
    return deidentifiedBundle;
  }

  /**
   * The resource type of every entry that has a fullUrl and a resource. A urn reference names an
   * entry only by its fullUrl, so the reference rule looks the type up here. Of two entries with the
   * same fullUrl, the first wins.
   */
  private static Map<String, String> entryTypes(Bundle bundle) {
    return bundle.getEntry().stream()
        .filter(entry -> entry.getFullUrl() != null && entry.hasResource())
        .collect(
            toMap(
                BundleEntryComponent::getFullUrl,
                entry -> entry.getResource().fhirType(),
                (first, second) -> first));
  }

  /**
   * Rebuilds one entry around its de-identified resource. A DELETE entry carries a request and no
   * resource (rule bdl-5); there is nothing to de-identify in it, and dropping it would change what
   * the bundle asks the server to do. An entry whose resource no module keeps is dropped.
   */
  private Optional<BundleEntryComponent> deidentifyEntry(
      BundleEntryComponent entry, HandlerContext context, HandlerContext entryContext) {
    if (!entry.hasResource()) {
      return rebuildEntry(entry, Optional.empty(), entryContext);
    }
    return deidentify(entry.getResource(), context)
        .flatMap(resource -> rebuildEntry(entry, Optional.of(resource), entryContext));
  }

  /**
   * The {@code fullUrl} and the {@code request.url} name the resource the way a reference does, so
   * both go through {@link PseudonymIdentity}, which owns that rule and its drop cases. Only the
   * two request fields the skeleton needs survive; every other request field is de-identified away.
   */
  private Optional<BundleEntryComponent> rebuildEntry(
      BundleEntryComponent entry, Optional<Resource> resource, HandlerContext context) {
    BundleEntryComponent rebuilt = new BundleEntryComponent();
    resource.ifPresent(rebuilt::setResource);
    // hasFullUrl() is also true for an element that carries only an extension
    Optional.ofNullable(entry.getFullUrl())
        .flatMap(fullUrl -> identity.fullUrl(fullUrl, context))
        .ifPresent(rebuilt::setFullUrl);
    Optional.of(entry)
        .filter(BundleEntryComponent::hasRequest)
        .map(BundleEntryComponent::getRequest)
        .ifPresent(
            request ->
                Optional.ofNullable(request.getUrl())
                    .flatMap(
                        url ->
                            resource
                                .map(r -> identity.requestUrl(url, r, context))
                                .orElseGet(() -> identity.requestUrl(url, context)))
                    .ifPresent(
                        url -> rebuilt.getRequest().setMethod(request.getMethod()).setUrl(url)));
    return Optional.of(rebuilt).filter(not(BundleEntryComponent::isEmpty));
  }

  /** A child that survived de-identification, with the value to write into the fresh element. */
  private record Kept(FhirChild child, Object value) {}

  private Optional<Base> deidentifyElement(
      List<String> path, Base base, HandlerContext context, Profile.Rules rules) {
    HandlerContext childContext = context.child(base);
    List<Kept> kept =
        HapiReflection.childrenWithValue(base).stream()
            .flatMap(
                child ->
                    deidentifyValue(
                        append(path, HapiReflection.toPathElement(child.property(), child.value())),
                        child.value(),
                        childContext,
                        rules)
                        .map(value -> new Kept(child, value))
                        .stream())
            .toList();
    if (kept.isEmpty()) {
      return Optional.empty();
    }
    Base empty = HapiReflection.newEmptyInstance(base.getClass());
    kept.forEach(k -> k.child().copyInto(empty, k.value()));
    return Optional.of(empty);
  }

  private Optional<?> deidentifyValue(
      List<String> path, Object value, HandlerContext context, Profile.Rules rules) {
    return switch (value) {
      case PrimitiveType<?> primitive -> deidentifyPrimitive(path, primitive, context, rules);
      case Base base -> deidentifyElement(path, base, context, rules);
      case List<?> list -> deidentifyList(path, list, context, rules);
      // the narrative of a resource restates its content as text; de-identification always drops it
      case XhtmlNode ignored -> Optional.empty();
      default -> throw new IllegalStateException("Unexpected element of type " + value.getClass());
    };
  }

  /**
   * The survivors of a list, as a mutable list: {@link FhirChild#copyInto} stores it in the HAPI
   * field, and HAPI appends to that list later.
   */
  private Optional<List<Object>> deidentifyList(
      List<String> path, List<?> list, HandlerContext context, Profile.Rules rules) {
    List<Object> kept =
        list.stream()
            .flatMap(element -> deidentifyValue(path, element, context, rules).stream())
            .collect(toCollection(ArrayList::new));
    return Optional.of(kept).filter(not(List::isEmpty));
  }

  /**
   * Extensions on a primitive are whitelisted on their own, because the value does not carry them
   * along. Two sources feed the result:
   *
   * <ul>
   *   <li>An extension of the input goes through the profile filter, like any other element.
   *   <li>An extension the handler chain added passes unfiltered. A handler is registry code the
   *       caller wired up, not resource data, so what it puts on the element is meant to survive.
   *       The date shift of fts-next depends on this: it drops the value and hangs a transport id
   *       on the element as an extension.
   * </ul>
   *
   * <p>An extension counts as added when no input extension is {@code equalsDeep} to it. A handler
   * that removes or rewrites an input extension therefore does not change what the filter keeps.
   * Making the handler output the only source would drop whitelisted extensions on every field
   * whose handler returns a fresh element instead of its argument.
   */
  private Optional<PrimitiveType<?>> deidentifyPrimitive(
      List<String> path, PrimitiveType<?> primitive, HandlerContext context, Profile.Rules rules) {
    PrimitiveType<?> copy = (PrimitiveType<?>) primitive.copy();
    // the copy carries the element id of the input, which no rule whitelisted
    copy.setId(null);
    Optional<PrimitiveType<?>> deidentified =
        applyHandlers(path, copy, context, rules).map(value -> (PrimitiveType<?>) value);

    List<Extension> keptExtensions =
        Stream.concat(
                whitelistedExtensions(path, primitive, context, rules),
                addedExtensions(deidentified, primitive))
            .toList();

    if (deidentified.isEmpty() && keptExtensions.isEmpty()) {
      return Optional.empty();
    }
    PrimitiveType<?> result =
        deidentified.orElseGet(() -> HapiReflection.newEmptyInstance(primitive.getClass()));
    result.setExtension(new ArrayList<>(keptExtensions));
    return Optional.of(result);
  }

  private Stream<Extension> whitelistedExtensions(
      List<String> path, PrimitiveType<?> primitive, HandlerContext context, Profile.Rules rules) {
    List<String> extensionPath = append(path, "extension");
    return primitive.getExtension().stream()
        .flatMap(
            extension ->
                deidentifyElement(extensionPath, extension, context, rules)
                    .map(Extension.class::cast)
                    .stream());
  }

  /** The extensions the handler chain put on the element; empty if the chain removed it. */
  private static Stream<Extension> addedExtensions(
      Optional<PrimitiveType<?>> deidentified, PrimitiveType<?> primitive) {
    return deidentified.stream()
        .flatMap(element -> element.getExtension().stream())
        .filter(not(extension -> isOnInput(extension, primitive)));
  }

  private static boolean isOnInput(Extension extension, PrimitiveType<?> primitive) {
    return primitive.getExtension().stream().anyMatch(extension::equalsDeep);
  }

  private Optional<Object> applyHandlers(
      List<String> path, PrimitiveType<?> value, HandlerContext context, Profile.Rules rules) {
    return switch (rules.ruleFor(path, value.getClass())) {
      case Profile.Rule.Remove ignored -> Optional.empty();
      case Profile.Rule.Apply apply -> applyChain(apply.handlers(), path, value, context);
    };
  }

  /**
   * Applies the handlers in order, stopping at the first one that removes the value. A handler that
   * returns empty has decided the element is gone, and the next handler in the chain is written for
   * a value, not for its absence.
   */
  private static Optional<Object> applyChain(
      List<DeidentifhirHandler<Object>> handlers,
      List<String> path,
      Object value,
      HandlerContext context) {
    return handlers.stream()
        .<Function<Optional<Object>, Optional<Object>>>map(
            handler -> current -> current.flatMap(v -> handler.apply(path, v, context)))
        .reduce(Function.identity(), Function::andThen)
        .apply(Optional.of(value));
  }

  private static <T> List<T> append(List<T> list, T element) {
    return Stream.concat(list.stream(), Stream.of(element)).toList();
  }
}
