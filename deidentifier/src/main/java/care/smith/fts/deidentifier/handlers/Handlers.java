package care.smith.fts.deidentifier.handlers;

import static java.util.Objects.requireNonNull;

import care.smith.fts.deidentifier.DeidentifierHandler;
import care.smith.fts.deidentifier.HandlerContext;
import java.util.Optional;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.StringType;

/**
 * The standard handler library. Every handler matches the signature of {@link
 * care.smith.fts.deidentifier.DeidentifierHandler}, so it can be registered as a method reference.
 * Handlers that need collaborators are created by the static factory methods.
 */
public interface Handlers {

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
    // a switch expression, so that the compiler checks that every precision is handled
    boolean hasDay =
        switch (date.getPrecision()) {
          case YEAR, MONTH -> false;
          case DAY -> true;
          case MINUTE, SECOND, MILLI ->
              throw new IllegalArgumentException(
                  "Unexpected precision for object of type DateType!");
        };
    // do nothing if the precision is lower than DAY
    if (hasDay) {
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
   * Replaces the given string with a predefined static string. The result keeps the class of the
   * element, so it also fits a field of a subtype such as MarkdownType. A string element without a
   * value carries only extensions; it passes through unchanged.
   */
  static DeidentifierHandler<StringType> stringReplacementHandler(String staticString) {
    requireNonNull(staticString);
    return (string, context) ->
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
}
