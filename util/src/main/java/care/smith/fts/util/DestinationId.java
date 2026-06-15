package care.smith.fts.util;

import static java.util.Objects.requireNonNull;
import static java.util.regex.Pattern.CASE_INSENSITIVE;

import java.util.Locale;
import java.util.regex.Pattern;

public interface DestinationId {

  // Matches optional http/https scheme, captures the host, captures the optional path
  Pattern URL_PATTERN = Pattern.compile("^(?:https?://)?([^/]+)(.*)$", CASE_INSENSITIVE);

  static String fromBaseUrl(String baseUrl) {
    requireNonNull(baseUrl, "baseUrl must not be null");

    var matcher = URL_PATTERN.matcher(baseUrl.strip());
    if (!matcher.matches()) {
      return baseUrl.strip().toLowerCase(Locale.ROOT); // Fallback for unexpected formats
    }

    var host = matcher.group(1).toLowerCase(Locale.ROOT);
    var path = matcher.group(2);

    // Strip trailing slashes from the path components
    while (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }

    return host + path;
  }
}
