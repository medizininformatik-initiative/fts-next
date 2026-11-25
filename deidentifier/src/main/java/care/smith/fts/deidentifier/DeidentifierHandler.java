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
   * A handler returns the value it got or a fresh instance, never a shared or cached one: the
   * engine changes the returned element, for example when it sets the extensions on a primitive.
   *
   * <p>On a primitive, the value a handler gets carries no input extensions; the input element with
   * its extensions is still part of the parent in the context. The engine keeps the extensions a
   * handler puts on the returned primitive as they are; the rule set does not filter them.
   *
   * @param value the current element
   * @param context the resource and the ancestor chain of the current element
   * @return the transformed element, or empty to remove it
   */
  Optional<T> apply(T value, HandlerContext context);
}
