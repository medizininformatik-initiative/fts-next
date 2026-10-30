package care.smith.fts.deidentifier.internal;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.regex.Pattern;

/**
 * The value of a conditional reference's {@code identifier} parameter: {@code system|value}, or
 * only {@code value}. The separator and the parts may be percent-encoded.
 */
public interface IdentifierToken {

  /** The separator in its plain form or percent-encoded, whatever the case of the hex digits. */
  Pattern SEPARATOR = Pattern.compile("\\||%7c", Pattern.CASE_INSENSITIVE);

  /**
   * Replaces the value of the token, keeping the shape of the rest: system and separator stay as
   * they are written. The value is everything after the first separator.
   *
   * @param noSystem the system to look up when the token has none or an empty one
   * @param replacement maps a decoded system and a decoded value to the pseudonym
   * @return empty when the token has no value or is malformed
   */
  static Optional<String> replace(
      String token, String noSystem, BinaryOperator<String> replacement) {
    var separator = SEPARATOR.matcher(token);
    var found = separator.find();
    var systemEnd = found ? separator.start() : 0;
    var valueStart = found ? separator.end() : 0;
    return decode(token.substring(0, systemEnd))
        .flatMap(
            system ->
                decode(token.substring(valueStart))
                    .filter(value -> !value.isEmpty())
                    .map(
                        value ->
                            token.substring(0, valueStart)
                                + encode(
                                    replacement.apply(
                                        system.isEmpty() ? noSystem : system, value))));
  }

  /** A space becomes %20: a plus sign in the query would read as a space only in some places. */
  private static String encode(String pseudonym) {
    return URLEncoder.encode(pseudonym, StandardCharsets.UTF_8).replace("+", "%20");
  }

  /**
   * Decodes the %XX escapes as UTF-8; a plus and any other character stay as they are, as only form
   * data reads a plus as a space. A malformed escape or a byte sequence that is not valid UTF-8 (a
   * lone {@code %C3}, an overlong {@code %C0%AF}) gives empty: the lenient decoding would replace
   * it with U+FFFD and look up a value other than the one written.
   */
  private static Optional<String> decode(String raw) {
    var decoded = new StringBuilder(raw.length());
    var bytes = new ByteArrayOutputStream();
    for (var i = 0; i < raw.length(); i++) {
      var c = raw.charAt(i);
      if (c == '%') {
        var high = i + 2 < raw.length() ? Character.digit(raw.charAt(i + 1), 16) : -1;
        var low = high < 0 ? -1 : Character.digit(raw.charAt(i + 2), 16);
        if (low < 0) {
          return Optional.empty();
        }
        bytes.write(high * 16 + low);
        i += 2;
      } else {
        if (!flush(bytes, decoded)) {
          return Optional.empty();
        }
        decoded.append(c);
      }
    }
    return flush(bytes, decoded) ? Optional.of(decoded.toString()) : Optional.empty();
  }

  /** Appends the pending escaped bytes as UTF-8; false when they are not valid UTF-8. */
  private static boolean flush(ByteArrayOutputStream bytes, StringBuilder decoded) {
    if (bytes.size() == 0) {
      return true;
    }
    try {
      decoded.append(
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes.toByteArray())));
      bytes.reset();
      return true;
    } catch (CharacterCodingException e) {
      return false;
    }
  }
}
