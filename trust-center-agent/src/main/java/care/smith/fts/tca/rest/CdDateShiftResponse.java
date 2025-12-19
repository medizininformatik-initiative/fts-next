package care.smith.fts.tca.rest;

/**
 * Response DTO for CDA date shift endpoint.
 *
 * @param dateShiftDays date shift amount in whole days (for FHIR Pseudonymizer's
 *     dateShiftFixedOffsetInDays parameter)
 */
public record CdDateShiftResponse(int dateShiftDays) {}
