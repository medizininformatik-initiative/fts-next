package care.smith.fts.deidentifier.handlers;

import static java.util.Objects.requireNonNull;

import care.smith.fts.deidentifier.DeidentifierHandler;
import care.smith.fts.deidentifier.HandlerContext;
import care.smith.fts.deidentifier.internal.PseudonymUuid;
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
        Optional.of(
            Optional.ofNullable(string.getValue())
                .map(value -> withValue(string, staticString))
                .orElse(string));
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
   * <p>HAPI reads the id of a transaction-bundle resource from the entry's {@code fullUrl} and
   * keeps a urn id in one piece, so the id part of such a resource is {@code urn:uuid:<uuid>}
   * rather than the raw {@code id} field of the JSON. The prefix is stripped before the lookup, so
   * the provider sees the same string either way.
   *
   * <p>An id element without an id part carries only extensions; it passes through unchanged.
   */
  static DeidentifierHandler<IdType> idReplacementHandler(IDReplacementProvider provider) {
    requireNonNull(provider);
    return (id, context) ->
        Optional.of(
            Optional.ofNullable(id.getIdPart())
                .map(PseudonymUuid::stripUrnUuid)
                .map(
                    idPart ->
                        new IdType(
                            id.getResourceType(),
                            provider.getIDReplacement(
                                context.resource().getResourceType().toString(), idPart)))
                .orElse(id));
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
            Optional.ofNullable(value.getValue())
                .map(
                    plain ->
                        new StringType(
                            provider.getValueReplacement(
                                systemOf((Identifier) context.parent(), acceptNoSystem), plain)))
                .orElse(value));
  }

  private static String systemOf(Identifier identifier, boolean acceptNoSystem) {
    return Optional.ofNullable(identifier.getSystem())
        .or(() -> Optional.of(NO_SYSTEM).filter(placeholder -> acceptNoSystem))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "An identifier is missing a system, which this replacement requires."));
  }
}
