package care.smith.fts.tca.rest;

import static java.util.Objects.requireNonNull;

import jakarta.validation.constraints.NotNull;

/**
 * Response DTO for date shift endpoint.
 *
 * @param transferId session identifier for retrieving RDA's date shift later
 * @param dateShiftDays date shift amount in whole days (for FHIR Pseudonymizer's
 *     dateShiftFixedOffsetInDays parameter)
 */
public record DateShiftResponse(@NotNull String transferId, int dateShiftDays) {

  public DateShiftResponse {
    requireNonNull(transferId, "transferId is required");
  }
}
