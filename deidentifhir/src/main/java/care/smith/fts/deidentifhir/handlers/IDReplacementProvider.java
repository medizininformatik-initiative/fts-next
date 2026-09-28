package care.smith.fts.deidentifhir.handlers;

/** Supplies the pseudonym that replaces a resource id. */
@FunctionalInterface
public interface IDReplacementProvider {

  String getIDReplacement(String resourceType, String id);
}
