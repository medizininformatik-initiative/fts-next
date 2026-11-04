package care.smith.fts.deidentifier.handlers;

/** Supplies the pseudonym that replaces a resource id. */
@FunctionalInterface
public interface IDReplacementProvider {

  String getIDReplacement(String resourceType, String id);
}
