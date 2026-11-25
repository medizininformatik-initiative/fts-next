package care.smith.fts.deidentifier;

import java.util.List;

/**
 * The decision of a {@link RuleSet} for one element. {@link #REMOVE} drops it. {@link Apply} runs
 * the handler chain on it; an empty chain means keep unchanged.
 */
public sealed interface Rule {

  Rule REMOVE = new Remove();

  record Remove() implements Rule {}

  record Apply(List<DeidentifierHandler<Object>> handlers) implements Rule {}
}
