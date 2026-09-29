package care.smith.fts.deidentifier.handlers;

/** Supplies the pseudonym that replaces an identifier value within its system. */
@FunctionalInterface
public interface IdentifierValueReplacementProvider {

  String getValueReplacement(String system, String value);
}
