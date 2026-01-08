package care.smith.fts.tca.rest;

import static java.util.Objects.requireNonNull;

import jakarta.validation.constraints.NotNull;

/**
 * Response DTO for RDA date shift endpoint.
 *
 * @param transportId the transport ID used to correlate with the pseudonymized bundle
 * @param dateShiftDays date shift amount in whole days (for FHIR Pseudonymizer's
 *     dateShiftFixedOffsetInDays parameter)
 */
public record DateShiftResponse(@NotNull String transportId, int dateShiftDays) {

  public DateShiftResponse {
    requireNonNull(transportId, "transportId is required");
  }
}
