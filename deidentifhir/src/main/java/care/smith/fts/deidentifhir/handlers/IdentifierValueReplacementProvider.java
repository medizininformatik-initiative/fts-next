package care.smith.fts.deidentifhir.handlers;

/** Supplies the pseudonym that replaces an identifier value within its system. */
@FunctionalInterface
public interface IdentifierValueReplacementProvider {

  String getValueReplacement(String system, String value);
}
