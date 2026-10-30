package care.smith.fts.deidentifier.internal;

import java.util.regex.Pattern;

/** The shapes of a reference that the reference rule distinguishes. */
public interface References {

  /** {@code Type/id}, optionally with a version: {@code Type/id/_history/version}. */
  Pattern RELATIVE = Pattern.compile("(?<type>[^/?]+)/(?<id>[^/?]+)(?:/_history/[^/?]+)?");

  /**
   * {@code Type?identifier=token}, with the identifier as the only search parameter, so no other
   * search value reaches the reference rule.
   */
  Pattern CONDITIONAL = Pattern.compile("(?<type>[^/?]+)\\?identifier=(?<token>[^&]*)");

  /** The start of a reference with a scheme and an authority, e.g. {@code http://server/...}. */
  Pattern ABSOLUTE = Pattern.compile("[a-zA-Z][a-zA-Z0-9+.-]*://");
}
