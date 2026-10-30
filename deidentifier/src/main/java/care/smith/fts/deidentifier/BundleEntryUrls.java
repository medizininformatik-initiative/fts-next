package care.smith.fts.deidentifier;

import static java.util.Objects.requireNonNull;

import care.smith.fts.deidentifier.internal.PseudonymUuid;
import java.util.Optional;
import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;

/**
 * Decides which urls of a bundle entry may name a resource, and sends those that name it by what
 * the source system chose through the reference rule, e.g. the url of a request. Construction is an
 * explicit decision: {@link #none()} states that no reference rule exists, so every such value is
 * dropped rather than leaked.
 */
public final class BundleEntryUrls {

  private static final Pattern TYPE_ONLY_URL = Pattern.compile("[^/?]+");

  private static final Pattern TYPE_AND_ID_URL = Pattern.compile("[^/?]+/[^/?]+");

  /** Only the identifier, so no other search value reaches the reference rule. */
  private static final Pattern CONDITIONAL_URL = Pattern.compile("[^/?]+\\?identifier=[^&]*");

  private static final BundleEntryUrls NONE = new BundleEntryUrls(null);

  /** The rule that replaces a reference, or null for {@link #none()}. */
  private final DeidentifierHandler<StringType> referenceHandler;

  private BundleEntryUrls(DeidentifierHandler<StringType> referenceHandler) {
    this.referenceHandler = referenceHandler;
  }

  public static BundleEntryUrls of(DeidentifierHandler<StringType> referenceHandler) {
    return new BundleEntryUrls(requireNonNull(referenceHandler));
  }

  /** No reference rule: every value that would need one is dropped. */
  public static BundleEntryUrls none() {
    return NONE;
  }

  /**
   * The request url of an entry with a resource. A url that names only a type stays. A url {@code
   * Type/id} of the entry's own type names the entry's own resource, so it takes the id of the
   * de-identified resource and never disagrees with it; without a surviving id it goes. A {@code
   * Type/id} of another type names a resource other than the entry's, e.g. the target of a PATCH,
   * whose entry carries a {@code Parameters} or a {@code Binary}; it goes through the reference
   * rule like a conditional {@code Type?identifier=...}. Any other url goes.
   */
  public Optional<String> requestUrl(String url, Resource deidentified, HandlerContext context) {
    if (TYPE_ONLY_URL.matcher(url).matches()) {
      return Optional.of(url);
    }
    if (TYPE_AND_ID_URL.matcher(url).matches()) {
      if (!url.startsWith(deidentified.fhirType() + "/")) {
        return replace(url, context);
      }
      return Optional.ofNullable(deidentified.getIdElement().getIdPart())
          .map(id -> deidentified.fhirType() + "/" + id);
    }
    return CONDITIONAL_URL.matcher(url).matches() ? replace(url, context) : Optional.empty();
  }

  /**
   * The request url of an entry without a resource, i.e. a DELETE. There is no de-identified
   * resource to take an id from, so {@code Type/id} and {@code Type?identifier=...} go through the
   * reference rule. A url that names only a type goes: {@code DELETE Patient} names no source
   * resource, and a server may read it as delete-all.
   */
  public Optional<String> requestUrl(String url, HandlerContext context) {
    var namesAResource =
        TYPE_AND_ID_URL.matcher(url).matches() || CONDITIONAL_URL.matcher(url).matches();
    return namesAResource ? replace(url, context) : Optional.empty();
  }

  /**
   * The {@code fullUrl} of an entry with a resource. Only the literal form {@code urn:uuid:...}
   * survives, as the same form carrying the pseudonym; any other form goes, because it names a
   * location the source system chose.
   *
   * <p>The reference rule receives the {@code urn:uuid:} value unchanged and returns {@code
   * Type/<pseudonym>}; the new urn is derived from that whole result, so the type keeps two
   * resources apart whose pseudonyms coincide. The rule sees the entry's resource through the
   * context, as for any other reference.
   */
  public Optional<String> fullUrl(String fullUrl, HandlerContext context) {
    if (!PseudonymUuid.isUrnUuid(fullUrl)) {
      return Optional.empty();
    }
    return replace(fullUrl, context)
        .map(reference -> PseudonymUuid.URN_UUID_PREFIX + PseudonymUuid.uuidFrom(reference));
  }

  private Optional<String> replace(String url, HandlerContext context) {
    return Optional.ofNullable(referenceHandler)
        .flatMap(handler -> handler.apply(new StringType(url), context))
        .map(StringType::getValue);
  }
}
