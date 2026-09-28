package care.smith.fts.deidentifhir;

import java.util.List;
import java.util.Optional;

/**
 * A transformation applied to a single element during de-identification.
 *
 * @param <T> the element type this handler operates on
 */
@FunctionalInterface
public interface DeidentifhirHandler<T> {

  /**
   * @param path the path from the resource root to the current element
   * @param value the current element
   * @param context the resource, the ancestor chain, and the patient identifier of the call
   * @return the transformed element, or empty to remove it
   */
  Optional<T> apply(List<String> path, T value, HandlerContext context);
}
