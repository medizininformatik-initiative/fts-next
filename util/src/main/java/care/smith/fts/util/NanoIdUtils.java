package care.smith.fts.util;

import static com.aventrix.jnanoid.jnanoid.NanoIdUtils.DEFAULT_ALPHABET;
import static com.aventrix.jnanoid.jnanoid.NanoIdUtils.DEFAULT_NUMBER_GENERATOR;
import static com.aventrix.jnanoid.jnanoid.NanoIdUtils.randomNanoId;

/** Utility for generating URL-safe unique identifiers using the NanoId algorithm. */
public interface NanoIdUtils {

  /**
   * Generates a NanoId with default size (21 characters).
   *
   * @return a URL-safe unique identifier
   */
  static String nanoId() {
    return randomNanoId();
  }

  /**
   * Generates a NanoId with the specified size.
   *
   * @param size the number of characters in the generated ID
   * @return a URL-safe unique identifier of the specified length
   */
  static String nanoId(int size) {
    return randomNanoId(DEFAULT_NUMBER_GENERATOR, DEFAULT_ALPHABET, size);
  }
}
