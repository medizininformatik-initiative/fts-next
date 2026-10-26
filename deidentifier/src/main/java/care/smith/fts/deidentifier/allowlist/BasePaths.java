package care.smith.fts.deidentifier.allowlist;

import care.smith.fts.deidentifier.internal.FhirPaths;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.PrimitiveType;

/**
 * The paths a profile lists in its base. A base path has to name a leaf that the engine visits: the
 * engine asks the rule set only at primitives, and it drops the narrative unvisited.
 */
interface BasePaths {

  /**
   * The class of the element at {@code path}.
   *
   * @throws IllegalStateException when {@code path} names no FHIR element, lies in the narrative,
   *     or names a composite element
   */
  static Class<?> leafType(String path) {
    var type = FhirPaths.elementType(path).orElseThrow(() -> noSuchElement(path));
    requireOutsideNarrative(path);
    requireLeaf(path, type);
    return type;
  }

  private static void requireOutsideNarrative(String path) {
    if (inNarrative(path)) {
      throw new IllegalStateException(
          "The base path %s is in the narrative, which de-identification always drops!"
              .formatted(path));
    }
  }

  private static void requireLeaf(String path, Class<?> type) {
    if (!PrimitiveType.class.isAssignableFrom(type)) {
      throw new IllegalStateException(
          "The base path %s names a composite element, which the engine never visits; list its leaf paths!"
              .formatted(path));
    }
  }

  private static IllegalStateException noSuchElement(String path) {
    return new IllegalStateException("The base path %s names no FHIR element!".formatted(path));
  }

  /** Whether {@code path} or any prefix of it is a narrative, which the engine drops unvisited. */
  private static boolean inNarrative(String path) {
    var elements = List.of(path.split("\\."));
    return IntStream.rangeClosed(1, elements.size())
        .mapToObj(n -> String.join(".", elements.subList(0, n)))
        .map(FhirPaths::elementType)
        .flatMap(Optional::stream)
        .anyMatch(Narrative.class::equals);
  }
}
