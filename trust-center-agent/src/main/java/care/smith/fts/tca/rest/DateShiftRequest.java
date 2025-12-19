package care.smith.fts.tca.rest;

import care.smith.fts.api.DateShiftPreserve;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

/**
 * Request DTO for generating date shift values for FHIR Pseudonymizer.
 *
 * @param patientId the patient identifier used to generate deterministic shifts
 * @param maxDateShift maximum date shift duration (e.g., PT336H for 14 days)
 * @param dateShiftPreserve preservation strategy for date shifts
 * @param dateShiftDomain gPAS domain name for fetching/storing dateShift seeds
 */
public record DateShiftRequest(
    @NotBlank String patientId,
    @NotNull Duration maxDateShift,
    @NotNull DateShiftPreserve dateShiftPreserve,
    @NotBlank String dateShiftDomain) {}
