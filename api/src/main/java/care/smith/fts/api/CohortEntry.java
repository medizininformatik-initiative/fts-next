package care.smith.fts.api;

import static java.util.Objects.requireNonNull;

/**
 * A patient found by a cohort selection: consented, excluded for a stated reason, or not consented.
 */
public sealed interface CohortEntry {

  /** The patient identifier value. */
  String identifier();

  /** A patient consenting to all required policies. */
  record Consented(ConsentedPatient patient) implements CohortEntry {
    public Consented {
      requireNonNull(patient, "Patient cannot be null");
    }

    @Override
    public String identifier() {
      return patient.identifier();
    }
  }

  /**
   * A patient left out of the transfer, to be reported.
   *
   * @param identifier the patient identifier value
   * @param reason why the patient is excluded; must not contain patient data such as dates
   */
  record Excluded(String identifier, String reason) implements CohortEntry {
    public Excluded {
      requireNonNull(identifier, "Identifier cannot be null");
      requireNonNull(reason, "Reason cannot be null");
    }
  }

  /**
   * A patient whose consents do not cover all required policies. It is left out of the transfer,
   * and reported only if the transfer was requested for it.
   *
   * @param identifier the patient identifier value
   */
  record NotConsented(String identifier) implements CohortEntry {
    public NotConsented {
      requireNonNull(identifier, "Identifier cannot be null");
    }
  }
}
