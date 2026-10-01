package care.smith.fts.deidentifier;

import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Resource;

/**
 * What a handler may ask about the element it transforms: the resource it belongs to, the parent
 * element, and the full ancestor chain.
 */
public final class HandlerContext {

  private static final HandlerContext EMPTY = new HandlerContext(List.of());

  /** All ancestors from the resource root to the parent of the current element, root first. */
  private final List<Base> ancestors;

  private HandlerContext(List<Base> ancestors) {
    this.ancestors = ancestors;
  }

  public static HandlerContext empty() {
    return EMPTY;
  }

  public static HandlerContext of(List<Base> ancestors) {
    return new HandlerContext(List.copyOf(ancestors));
  }

  /** The same call, one level deeper: {@code ancestor} appended to the chain. */
  public HandlerContext child(Base ancestor) {
    requireNonNull(ancestor);
    return new HandlerContext(Stream.concat(ancestors.stream(), Stream.of(ancestor)).toList());
  }

  /**
   * The resource the current element belongs to: the root of the ancestor chain. During
   * de-identification the chain always starts at the resource; a context built with an empty or
   * differently rooted chain, e.g. in a handler test, cannot answer this.
   */
  public Resource resource() {
    return ancestors.stream()
        .findFirst()
        .filter(Resource.class::isInstance)
        .map(Resource.class::cast)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "This context has no resource at the root of its ancestor chain."));
  }

  /**
   * The direct parent of the current element: the end of the ancestor chain. A context built with
   * an empty chain, e.g. in a handler test, cannot answer this.
   */
  public Base parent() {
    return Optional.of(ancestors)
        .filter(not(List::isEmpty))
        .map(List::getLast)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "This context has an empty ancestor chain and no parent."));
  }

  public List<Base> ancestors() {
    return ancestors;
  }
}
