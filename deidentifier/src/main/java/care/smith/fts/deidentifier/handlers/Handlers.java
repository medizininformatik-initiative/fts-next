package care.smith.fts.deidentifier.handlers;

import static java.util.Objects.requireNonNull;

import care.smith.fts.deidentifier.DeidentifierHandler;
import care.smith.fts.deidentifier.HandlerContext;
import care.smith.fts.deidentifier.internal.References;
import java.util.Optional;
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

  /** The placeholder system of an identifier without a system, where that is accepted. */
  String NO_SYSTEM = "<no_system>";

  /**
   * Truncates the postal code to its first three digits if it is five digits long. Any other postal
   * code is removed altogether.
   */
  static Optional<StringType> generalizePostalCode(StringType postalCode, HandlerContext context) {
    return Optional.ofNullable(postalCode.getValue())
        .filter(value -> value.length() == 5)
        .map(value -> new StringType(value.substring(0, 3)));
  }

  /**
   * Sets the day of the month to the 15th according to the MII/SMITH pseudonymization concept.
   * Dates with a precision below {@code DAY} are left untouched.
   */
  static Optional<DateType> generalizeDateHandler(DateType date, HandlerContext context) {
    // an element that carries only extensions has no value to generalize
    if (date.getValue() != null) {
      toMidMonth(date);
    }
    return Optional.of(date);
  }

  /** Changes {@code date} in place. */
  private static void toMidMonth(DateType date) {
    // do nothing if the precision is lower than DAY
    if (hasDay(date)) {
      date.setDay(15); // the day field is 1-indexed!
    }

    // even though the precision might only be DAY or lower, the object can hold a more specific
    // time stamp
    date.setHour(0);
    date.setMinute(0);
    date.setSecond(0);
    date.setMillis(0);
  }

  /**
   * A switch expression, so that the compiler checks that every precision is handled. A DateType
   * holds no time, so a precision finer than DAY is a broken element.
   */
  private static boolean hasDay(DateType date) {
    return switch (date.getPrecision()) {
      case YEAR, MONTH -> false;
      case DAY -> true;
      case MINUTE, SECOND, MILLI ->
          throw new IllegalArgumentException("Unexpected precision for object of type DateType!");
    };
  }

  /**
   * Replaces the given string with a predefined static string. The result keeps the class of the
   * element, so it also fits a field of a subtype such as MarkdownType. A string element without a
   * value carries only extensions; it passes through unchanged.
   */
  static DeidentifierHandler<StringType> stringReplacementHandler(String staticString) {
    requireNonNull(staticString);
    return (string, context) ->
        Optional.of(string.getValue() == null ? string : withValue(string, staticString));
  }

  /** A copy of {@code string}, so it keeps the class of the element, with {@code value}. */
  private static StringType withValue(StringType string, String value) {
    var replaced = string.copy();
    replaced.setValue(value);
    return replaced;
  }

  /**
   * Replaces the id part with its pseudonym. The resource type used for the lookup is taken from
   * the resource at the root of the context, not from the id itself.
   *
   * <p>An id element without an id part carries only extensions; it passes through unchanged.
   */
  static DeidentifierHandler<IdType> idReplacementHandler(IDReplacementProvider provider) {
    requireNonNull(provider);
    return (id, context) ->
        Optional.of(id.getIdPart() == null ? id : pseudonymized(id, provider, context));
  }

  /** The id with its id part replaced, looked up under the type of the resource of the context. */
  private static IdType pseudonymized(
      IdType id, IDReplacementProvider provider, HandlerContext context) {
    var resourceType = context.resource().getResourceType().toString();
    return new IdType(
        id.getResourceType(), provider.getIDReplacement(resourceType, id.getIdPart()));
  }

  /**
   * Replaces an identifier value with its pseudonym. Because the value is not unique on its own,
   * the system of the enclosing identifier, the parent of the value, is passed along.
   *
   * <p>A value element without a value carries only extensions, e.g. a data-absent-reason; it
   * passes through unchanged rather than being given an invented pseudonym.
   *
   * @param acceptNoSystem whether identifiers without a system are replaced under the placeholder
   *     system {@link #NO_SYSTEM} instead of being rejected
   * @throws IllegalStateException when the handler runs on an identifier without a system and does
   *     not accept that; the message does not name the value, as source data must not reach logs
   */
  static DeidentifierHandler<StringType> identifierValueReplacementHandler(
      IdentifierValueReplacementProvider provider, boolean acceptNoSystem) {
    requireNonNull(provider);
    return (value, context) ->
        Optional.of(
            value.getValue() == null
                ? value
                : pseudonymized(value, provider, acceptNoSystem, context));
  }

  /** The pseudonym of the value, looked up within the system of its identifier. */
  private static StringType pseudonymized(
      StringType value,
      IdentifierValueReplacementProvider provider,
      boolean acceptNoSystem,
      HandlerContext context) {
    var system = systemOf((Identifier) context.parent(), acceptNoSystem);
    return new StringType(provider.getValueReplacement(system, value.getValue()));
  }

  /**
   * The system of the identifier. Without one, {@link #NO_SYSTEM} if that is accepted.
   *
   * @throws IllegalStateException when the identifier has no system and that is not accepted
   */
  private static String systemOf(Identifier identifier, boolean acceptNoSystem) {
    if (identifier.getSystem() != null) {
      return identifier.getSystem();
    } else if (acceptNoSystem) {
      return NO_SYSTEM;
    } else {
      throw new IllegalStateException(
          "An identifier is missing a system, which this replacement requires.");
    }
  }

  /**
   * Replaces what a reference names by its pseudonym. The resource type used for the lookup is the
   * type the reference names, not the type of the resource that holds it.
   *
   * <ul>
   *   <li>{@code Type/id} becomes {@code Type/<pseudonym>}; a version {@code /_history/v} is
   *       dropped.
   *   <li>A contained reference {@code #id} and an element without a value, e.g. a
   *       data-absent-reason, pass through unchanged.
   *   <li>A {@code urn:uuid:} reference is rejected: it cannot be resolved from the reference
   *       alone.
   *   <li>A conditional reference {@code Type?identifier=...} is rejected like any other format: a
   *       server resolves it when it stores the resource, so a searchset does not carry one.
   * </ul>
   *
   * @throws IllegalArgumentException when the handler runs on an absolute reference, a {@code
   *     urn:uuid:} reference or a format it does not know; the message does not name the reference,
   *     as source data must not reach logs
   */
  static DeidentifierHandler<StringType> referenceReplacementHandler(
      IDReplacementProvider idProvider) {
    requireNonNull(idProvider);
    return (reference, context) -> {
      var value = reference.getValue();
      if (value == null || value.startsWith("#")) {
        return Optional.of(reference);
      }
      if (value.startsWith("urn:uuid:")) {
        throw new IllegalArgumentException("A urn:uuid reference cannot be resolved.");
      }
      return Optional.of(withValue(reference, replaceReference(value, idProvider)));
    };
  }

  private static String replaceReference(String reference, IDReplacementProvider idProvider) {
    if (References.ABSOLUTE.matcher(reference).lookingAt()) {
      throw new IllegalArgumentException("An absolute reference cannot be pseudonymized.");
    }
    var relative = References.RELATIVE.matcher(reference);
    if (relative.matches()) {
      return relative.group("type")
          + "/"
          + idProvider.getIDReplacement(relative.group("type"), relative.group("id"));
    }
    throw new IllegalArgumentException("A reference has a format that cannot be pseudonymized.");
  }
}
