package care.smith.fts.deidentifier;

import static java.util.Objects.requireNonNull;

import care.smith.fts.deidentifier.internal.PseudonymUuid;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;

/**
 * The one rule that keeps a de-identified bundle consistent: everything that names one resource —
 * its id, the {@code fullUrl} of its entry, and every reference to it — is replaced through the
 * same reference handler, so the two ends of every link still meet.
 *
 * <p>Construction is an explicit decision: {@link #of} applies the given reference handler, {@link
 * #none} states that no reference rule exists, so every value that names a resource is dropped
 * rather than leaked.
 */
public final class PseudonymIdentity {

  /** The paths the reference handler is called with for the two fields of the bundle skeleton. */
  private static final List<String> FULL_URL_PATH = List.of("Bundle", "entry", "fullUrl");

  private static final List<String> REQUEST_URL_PATH = List.of("Bundle", "entry", "request", "url");

  private static final PseudonymIdentity NONE = new PseudonymIdentity(Optional.empty());

  /** The two request url shapes the reference rule can pseudonymize, see {@link #requestUrl}. */
  private static final Pattern RELATIVE_REQUEST = Pattern.compile("[^/?]+/[^/?]+");

  private static final Pattern CONDITIONAL_REQUEST = Pattern.compile("[^/?]+\\?identifier=.*");

  /** Empty means: no reference rule, drop everything that names a resource. */
  private final Optional<DeidentifierHandler<StringType>> referenceHandler;

  private PseudonymIdentity(Optional<DeidentifierHandler<StringType>> referenceHandler) {
    this.referenceHandler = referenceHandler;
  }

  public static PseudonymIdentity of(DeidentifierHandler<StringType> referenceHandler) {
    return new PseudonymIdentity(Optional.of(referenceHandler));
  }

  public static PseudonymIdentity none() {
    return NONE;
  }

  /**
   * The pseudonymized {@code fullUrl} of an entry, or empty when the value cannot be pseudonymized
   * and has to be dropped. Only a {@code fullUrl} in literal urn form can be replaced; one that
   * names a server ({@code https://server.example/fhir/Patient/123}) identifies both the resource
   * and where it came from.
   *
   * <p>The reference rule turns the urn into {@code Type/<pseudonym>}, the pseudonym the entry's id
   * gets as well. A {@code fullUrl} has to stay an absolute URI, so the pseudonym is carried as a
   * UUID in urn form.
   */
  public Optional<String> fullUrl(String fullUrl, HandlerContext context) {
    requireNonNull(fullUrl);
    return Optional.of(fullUrl)
        .filter(PseudonymUuid::isUrnUuid)
        .flatMap(urn -> replace(FULL_URL_PATH, urn, context))
        .map(reference -> reference.substring(reference.indexOf('/') + 1))
        .map(pseudonym -> PseudonymUuid.URN_UUID_PREFIX + PseudonymUuid.uuidFrom(pseudonym));
  }

  /**
   * The {@code request.url} to keep, or empty when it cannot be pseudonymized and the whole request
   * has to go. A conditional create names its resource by an identifier of the source system
   * ({@code Patient?identifier=sys|12345}) and a targeted request names it by id ({@code
   * Patient/123}); both leak exactly what a reference of the same shape would, so both go through
   * the reference handler. A url that only names a resource type ({@code Patient}) says nothing
   * about a patient and stays as it is.
   */
  public Optional<String> requestUrl(String url, HandlerContext context) {
    requireNonNull(url);
    if (url.indexOf('/') < 0 && url.indexOf('?') < 0) {
      return Optional.of(url);
    }
    return Optional.of(url)
        .filter(PseudonymIdentity::isPseudonymizableRequestUrl)
        .flatMap(pseudonymizable -> replace(REQUEST_URL_PATH, pseudonymizable, context));
  }

  /**
   * The {@code request.url} of an entry that carries {@code resource}, its de-identified resource.
   * A url {@code Type/id} names that very resource, so it takes the id the profile gave the
   * resource, pseudonymized or kept, and never disagrees with the body. Without a surviving id the
   * url cannot name the resource and the request goes. Every other url follows {@link
   * #requestUrl(String, HandlerContext)}.
   */
  public Optional<String> requestUrl(String url, Resource resource, HandlerContext context) {
    requireNonNull(url);
    requireNonNull(resource);
    if (!RELATIVE_REQUEST.matcher(url).matches()) {
      return requestUrl(url, context);
    }
    return Optional.ofNullable(resource.getIdElement().getIdPart())
        .map(id -> resource.fhirType() + "/" + id);
  }

  private Optional<String> replace(List<String> path, String value, HandlerContext context) {
    return referenceHandler
        .flatMap(handler -> handler.apply(path, new StringType(value), context))
        .map(StringType::getValue);
  }

  /**
   * Only {@code Type/id} and {@code Type?identifier=…} can go through the reference rule. Any other
   * url that names a resource, e.g. a search by name or a query value that carries a slash, cannot
   * be pseudonymized and has to be dropped rather than crashed on or mangled.
   */
  private static boolean isPseudonymizableRequestUrl(String url) {
    return RELATIVE_REQUEST.matcher(url).matches() || CONDITIONAL_REQUEST.matcher(url).matches();
  }
}
