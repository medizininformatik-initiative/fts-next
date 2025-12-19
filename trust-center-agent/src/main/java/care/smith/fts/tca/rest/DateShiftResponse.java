package care.smith.fts.tca.rest;

/**
 * Response DTO for date shift endpoint.
 *
 * @param transferId session identifier for retrieving RDA's date shift later
 * @param dateShiftDays date shift amount in whole days (for FHIR Pseudonymizer's
 *     dateShiftFixedOffsetInDays parameter)
 */
public record DateShiftResponse(String transferId, int dateShiftDays) {}
