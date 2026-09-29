package care.smith.fts.deidentifier.handlers;

import static java.util.Objects.requireNonNull;

import care.smith.fts.deidentifier.DeidentifierHandler;
import care.smith.fts.deidentifier.HandlerContext;
import care.smith.fts.deidentifier.internal.PseudonymUuid;
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
 * care.smith.fts.deidentifier.DeidentifierHandler}, so it can be registered as a method reference.
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

  /**
   * A relative reference {@code Type/id}, optionally versioned as {@code Type/id/_history/n}. Group
   * 1 is the resource type and group 2 the id; the version is not captured.
   */
  Pattern RELATIVE_REFERENCE = Pattern.compile("([^/?#]+)/([^/?#]+)(?:/_history/[^/?#]+)?");

  /** A reference that names a server, {@code https://server.example/fhir/Patient/123}. */
  Pattern ABSOLUTE_REFERENCE = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://");

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

  /**
   * Replaces the given string with a predefined static string. The result keeps the class of the
   * element, so it also fits a field of a subtype such as MarkdownType. A string element without a
   * value carries only extensions; it passes through unchanged.
   */
  static DeidentifierHandler<StringType> stringReplacementHandler(String staticString) {
    requireNonNull(staticString);
    return (path, string, context) ->
        Optional.of(
            Optional.ofNullable(string.getValue())
                .map(
                    value -> {
                      StringType replaced = string.copy();
                      replaced.setValue(staticString);
                      return replaced;
                    })
                .orElse(string));
  }

  /**
   * Replaces the id part of a relative reference {@code Type/id} with its pseudonym, and the
   * identifier value of a conditional reference {@code Type?identifier=system|value}.
   *
   * <p>A reference in the literal urn form {@code urn:uuid:<uuid>}, the form transaction bundles
   * use to point at an entry that has no server id yet, names no resource type. The type comes from
   * the bundle entry whose {@code fullUrl} is that urn ({@link HandlerContext#entryType}), and the
   * provider is asked for the bare uuid under it: the same key {@link #idReplacementHandler} uses
   * for that entry. The result is the relative reference {@code Type/<pseudonym>}, so the link
   * still resolves once the {@code fullUrl} is gone, as it is in the research domain. A urn that no
   * entry of the bundle carries cannot be resolved; the reference is removed rather than left
   * pointing at nothing.
   *
   * <p>Any other reference format is rejected — an absolute URL loudly, rather than being split at
   * the {@code /} of its scheme and turned into nonsense.
   *
   * <p>A versioned reference {@code Type/id/_history/n} is pseudonymized under its id and loses its
   * version, which belongs to the source system. A contained reference {@code #id} points inside
   * its own resource and is kept as it is.
   *
   * <p>A reference element without a value carries only extensions, e.g. a data-absent-reason; it
   * passes through unchanged.
   *
   * <p>Conditional references are allowed in transaction bundles
   * (https://www.hl7.org/fhir/http.html#trules) and occur throughout the MII transport bundles. A
   * search URI keeps its type and system and has its identifier value replaced, exactly like {@link
   * #identifierValueReplacementHandler}. The system is optional: {@code Type?identifier=value} is a
   * legal search URI, and its value is replaced under the same {@code <no_system>} placeholder that
   * handler uses, so an identifier without a system maps the same way wherever it appears.
   */
  static DeidentifierHandler<StringType> referenceReplacementHandler(
      IDReplacementProvider idReplacementProvider,
      IdentifierValueReplacementProvider identifierValueReplacementProvider) {
    requireNonNull(idReplacementProvider);
    requireNonNull(identifierValueReplacementProvider);
    return (path, reference, context) -> {
      String value = reference.getValue();
      if (value == null) {
        return Optional.of(reference);
      }
      Matcher conditional = CONDITIONAL_REFERENCE.matcher(value);
      return (conditional.matches()
              ? Optional.of(
                  replaceConditionalReference(conditional, identifierValueReplacementProvider))
              : replaceReference(value, idReplacementProvider, context))
          .map(StringType::new);
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
  static DeidentifierHandler<IdType> idReplacementHandler(
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
   * <p>A value element without a value carries only extensions, e.g. a data-absent-reason; it
   * passes through unchanged rather than being given an invented pseudonym.
   *
   * @param acceptNoSystem whether identifiers without a system are replaced under the placeholder
   *     system {@code <no_system>} instead of being rejected
   */
  static DeidentifierHandler<StringType> identifierValueReplacementHandler(
      IdentifierValueReplacementProvider identifierValueReplacementProvider,
      boolean acceptNoSystem) {
    requireNonNull(identifierValueReplacementProvider);
    return (path, value, context) -> {
      if (value.getValue() == null) {
        return Optional.of(value);
      }
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

  private static Optional<String> replaceReference(
      String reference, IDReplacementProvider idReplacementProvider, HandlerContext context) {
    if (reference.startsWith("#")) {
      // a contained reference points inside its own resource and names no source id
      return Optional.of(reference);
    }
    if (PseudonymUuid.isUrnUuid(reference)) {
      return context
          .entryType(reference)
          .map(
              resourceType ->
                  pseudonymizedReference(
                      resourceType, PseudonymUuid.stripUrnUuid(reference), idReplacementProvider));
    }
    if (ABSOLUTE_REFERENCE.matcher(reference).find()) {
      // splitting this at the slash of its scheme would silently produce nonsense
      // the url is source data and must not reach the logs through this message
      throw new IllegalArgumentException(
          "absolute references are not supported, only relative and urn references are!");
    }
    Matcher relative = RELATIVE_REFERENCE.matcher(reference);
    if (!relative.matches()) {
      throw new IllegalArgumentException(
          "unexpected reference format. only relative references are supported right now!");
    }
    // a version names the same resource; it belongs to the source system and is dropped
    return Optional.of(
        pseudonymizedReference(relative.group(1), relative.group(2), idReplacementProvider));
  }

  private static String pseudonymizedReference(
      String resourceType, String id, IDReplacementProvider idReplacementProvider) {
    return resourceType + "/" + idReplacementProvider.getIDReplacement(resourceType, id);
  }
}
