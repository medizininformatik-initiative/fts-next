package care.smith.fts.api;

import static java.util.Objects.requireNonNull;

/** A patient selected for a transfer: either consented, or excluded for a stated reason. */
public sealed interface CohortEntry {

  /** A patient consenting to all required policies. */
  record Consented(ConsentedPatient patient) implements CohortEntry {
    public Consented {
      requireNonNull(patient, "Patient cannot be null");
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
}
