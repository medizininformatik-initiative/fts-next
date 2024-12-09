package care.smith.fts.util;

import static com.aventrix.jnanoid.jnanoid.NanoIdUtils.DEFAULT_ALPHABET;
import static com.aventrix.jnanoid.jnanoid.NanoIdUtils.DEFAULT_NUMBER_GENERATOR;
import static com.aventrix.jnanoid.jnanoid.NanoIdUtils.randomNanoId;

public interface NanoIdUtils {

  static String nanoId(int size) {
    return randomNanoId(DEFAULT_NUMBER_GENERATOR, DEFAULT_ALPHABET, size);
  }
}
