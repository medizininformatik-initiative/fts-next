package care.smith.fts.deidentifhir.handlers;

import static java.util.Objects.requireNonNull;

import care.smith.fts.deidentifhir.DeidentifhirHandler;
import care.smith.fts.deidentifhir.HandlerContext;
import care.smith.fts.deidentifhir.internal.PseudonymUuid;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.StringType;

/**
 * The standard handler library. Every handler matches the signature of {@link
 * care.smith.fts.deidentifhir.DeidentifhirHandler}, so it can be registered as a method reference.
 * Handlers that need collaborators are created by the static factory methods.
 */
public interface Handlers {

  /**
   * {@code Type?identifier=system|value} and {@code Type?identifier=value}.
   *
   * <p>The resource type may not contain a {@code /}, so an absolute URL is never mistaken for one,
   * and the system is only what precedes the first {@code |}, so a system that is itself a URL
   * ({@code https://…}) stays intact. A search URI without a {@code |} has no system: group 2 stays
   * {@code null} and group 3 holds the whole value.
   */
  Pattern CONDITIONAL_REFERENCE = Pattern.compile("([^/?]+)\\?identifier=(?:([^|]*)\\|)?(.*)");

  /** Placeholder system used for identifiers without a system, when those are accepted. */
  String NO_SYSTEM = "<no_system>";

  /** A reference that names a server, {@code https://server.example/fhir/Patient/123}. */
  Pattern ABSOLUTE_REFERENCE = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://");

  /**
   * The resource type key under which {@code urn:uuid:} references are pseudonymized. A reference
   * in literal urn form does not name a resource type, so this constant takes its place in every
   * {@link IDReplacementProvider#getIDReplacement} call made for such a reference.
   */
  String URN_UUID_RESOURCE_TYPE = "urn:uuid";

  /**
   * Truncates the postal code to its first three digits if it is five digits long. Any other postal
   * code is removed altogether.
   */
  static Optional<StringType> generalizePostalCode(
      List<String> path, StringType postalCode, HandlerContext context) {
    return Optional.ofNullable(postalCode.getValue())
        .filter(value -> value.length() == 5)
        .map(value -> new StringType(value.substring(0, 3)));
  }

  /**
   * Sets the day of the month to the 15th according to the MII/SMITH pseudonymization concept.
   * Dates with a precision below {@code DAY} are left untouched.
   */
  static Optional<DateType> generalizeDateHandler(
      List<String> path, DateType date, HandlerContext context) {
    // an element that carries only extensions has no value to generalize
    return Optional.ofNullable(date.getValue())
        .map(value -> toMidMonth(date))
        .or(() -> Optional.of(date));
  }

  private static DateType toMidMonth(DateType date) {
    switch (date.getPrecision()) {
      case YEAR, MONTH -> {
        // do nothing if the precision is lower than DAY
      }
      case DAY -> date.setDay(15); // the day field is 1-indexed!
      case MINUTE, SECOND, MILLI ->
          throw new IllegalArgumentException("Unexpected precision for object of type DateType!");
      default -> throw new IllegalArgumentException("Encountered an unknown precision!");
    }

    // even though the precision might only be DAY or lower, the object can hold a more specific
    // time stamp
    date.setHour(0);
    date.setMinute(0);
    date.setSecond(0);
    date.setMillis(0);
    return date;
  }

  /** Replaces the given string with a predefined static string. */
  static DeidentifhirHandler<StringType> stringReplacementHandler(String staticString) {
    requireNonNull(staticString);
    return (path, string, context) -> Optional.of(new StringType(staticString));
  }

  /**
   * Replaces the id part of a relative reference {@code Type/id} with its pseudonym, and the
   * identifier value of a conditional reference {@code Type?identifier=system|value}.
   *
   * <p>A reference in the literal urn form {@code urn:uuid:<uuid>}, the form transaction bundles
   * use to point at an entry that has no server id yet, is pseudonymized as well. Such references
   * occur throughout the MII transport bundles, and rejecting them would leave the plain UUID of
   * the source system in the output. Two details of that replacement:
   *
   * <ul>
   *   <li>The provider is called with {@link #URN_UUID_RESOURCE_TYPE} as the resource type, because
   *       the reference itself does not name one, and with the bare uuid as the id, the same string
   *       {@link #idReplacementHandler} passes for the referenced entry. A provider that keys on
   *       the id alone therefore gives the reference and the id of the entry it points at the same
   *       pseudonym.
   *   <li>The {@code urn:uuid:} prefix stays on the result, so the value remains a reference in
   *       literal urn form rather than turning into a relative reference to a resource type that
   *       does not exist.
   * </ul>
   *
   * <p>Any other reference format is rejected — an absolute URL loudly, rather than being split at
   * the {@code /} of its scheme and turned into nonsense.
   *
   * <p>Conditional references are allowed in transaction bundles
   * (https://www.hl7.org/fhir/http.html#trules) and occur throughout the MII transport bundles. A
   * search URI keeps its type and system and has its identifier value replaced, exactly like {@link
   * #identifierValueReplacementHandler}. The system is optional: {@code Type?identifier=value} is a
   * legal search URI, and its value is replaced under the same {@code <no_system>} placeholder that
   * handler uses, so an identifier without a system maps the same way wherever it appears.
   */
  static DeidentifhirHandler<StringType> referenceReplacementHandler(
      IDReplacementProvider idReplacementProvider,
      IdentifierValueReplacementProvider identifierValueReplacementProvider) {
    requireNonNull(idReplacementProvider);
    requireNonNull(identifierValueReplacementProvider);
    return (path, reference, context) -> {
      String value =
          Optional.ofNullable(reference.getValue())
              .orElseThrow(
                  () ->
                      new IllegalArgumentException("a reference without a value is unsupported!"));
      Matcher conditional = CONDITIONAL_REFERENCE.matcher(value);
      String replaced =
          conditional.matches()
              ? replaceConditionalReference(conditional, identifierValueReplacementProvider)
              : replaceReference(value, idReplacementProvider);
      return Optional.of(new StringType(replaced));
    };
  }

  /**
   * Replaces the id part with its pseudonym. The resource type used for the lookup is taken from
   * the resource at the root of the context, not from the id itself.
   *
   * <p>HAPI reads the id of a transaction-bundle resource from the entry's {@code fullUrl} and
   * keeps a urn id in one piece, so the id part of such a resource is {@code urn:uuid:<uuid>}
   * rather than the raw {@code id} field of the JSON. The prefix is stripped before the lookup, so
   * the provider sees the same string either way and a resource keeps its pseudonym whether it
   * arrived inside a transaction bundle or on its own.
   *
   * <p>An id element without a value carries only extensions; it passes through unchanged.
   */
  static DeidentifhirHandler<IdType> idReplacementHandler(
      IDReplacementProvider idReplacementProvider) {
    requireNonNull(idReplacementProvider);
    return (path, id, context) ->
        Optional.of(
            Optional.ofNullable(id.getIdPart())
                .map(PseudonymUuid::stripUrnUuid)
                .map(
                    idPart ->
                        new IdType(
                            id.getResourceType(),
                            idReplacementProvider.getIDReplacement(
                                context.resource().getResourceType().toString(), idPart)))
                .orElse(id));
  }

  /**
   * Replaces an identifier value with its pseudonym. Because the value is not unique on its own,
   * the system of the enclosing identifier is used as a prefix.
   *
   * @param acceptNoSystem whether identifiers without a system are replaced under the placeholder
   *     system {@code <no_system>} instead of being rejected
   */
  static DeidentifhirHandler<StringType> identifierValueReplacementHandler(
      IdentifierValueReplacementProvider identifierValueReplacementProvider,
      boolean acceptNoSystem) {
    requireNonNull(identifierValueReplacementProvider);
    return (path, value, context) -> {
      Identifier identifier = (Identifier) context.parent();
      String system =
          Optional.ofNullable(identifier.getSystem())
              .or(() -> Optional.of(NO_SYSTEM).filter(placeholder -> acceptNoSystem))
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Identifier %s is missing a system, which this replacement requires."
                              .formatted(identifier)));
      return Optional.of(
          new StringType(
              identifierValueReplacementProvider.getValueReplacement(system, value.getValue())));
    };
  }

  private static String replaceConditionalReference(
      Matcher conditional, IdentifierValueReplacementProvider identifierValueReplacementProvider) {
    String resourceType = conditional.group(1);
    Optional<String> identifierSystem = Optional.ofNullable(conditional.group(2));
    String replacement =
        identifierValueReplacementProvider.getValueReplacement(
            identifierSystem.orElse(NO_SYSTEM), conditional.group(3));
    return identifierSystem
        .map(system -> "%s?identifier=%s|%s".formatted(resourceType, system, replacement))
        .orElseGet(() -> "%s?identifier=%s".formatted(resourceType, replacement));
  }

  private static String replaceReference(
      String reference, IDReplacementProvider idReplacementProvider) {
    if (PseudonymUuid.isUrnUuid(reference)) {
      String pseudonym =
          idReplacementProvider.getIDReplacement(
              URN_UUID_RESOURCE_TYPE, PseudonymUuid.stripUrnUuid(reference));
      return PseudonymUuid.URN_UUID_PREFIX + PseudonymUuid.uuidFrom(pseudonym);
    }
    if (ABSOLUTE_REFERENCE.matcher(reference).find()) {
      // splitting this at the slash of its scheme would silently produce nonsense
      throw new IllegalArgumentException(
          "absolute reference '%s' is not supported, only relative and urn references are!"
              .formatted(reference));
    }
    int separator = reference.indexOf('/');
    if (separator <= 0) {
      throw new IllegalArgumentException(
          "unexpected reference format. only relative references are supported right now!");
    }
    String resourceType = reference.substring(0, separator);
    String idPart = reference.substring(separator + 1);
    return resourceType + "/" + idReplacementProvider.getIDReplacement(resourceType, idPart);
  }
}
