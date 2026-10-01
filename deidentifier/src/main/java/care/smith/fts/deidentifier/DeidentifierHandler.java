package care.smith.fts.deidentifier;

import java.util.Optional;

/**
 * A transformation applied to a single element during de-identification.
 *
 * @param <T> the element type this handler operates on
 */
@FunctionalInterface
public interface DeidentifierHandler<T> {

  /**
   * @param value the current element
   * @param context the resource and the ancestor chain of the current element
   * @return the transformed element, or empty to remove it
   */
  Optional<T> apply(T value, HandlerContext context);
}
