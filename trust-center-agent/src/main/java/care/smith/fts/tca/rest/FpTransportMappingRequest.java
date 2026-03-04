package care.smith.fts.tca.rest;

import care.smith.fts.api.DateShiftPreserve;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Request from CDA to consolidate transport mappings after FHIR Pseudonymizer processing.
 *
 * @param patientIdentifier patient ID used to derive the deterministic dateshift seed
 * @param transportIds identity tIDs from $create-pseudonym (already stored as tid:tId→sId in Redis)
 * @param dateMappings tID→originalDate for each date element nullified by CDA
 * @param dateShiftDomain gPAS domain for fetching the dateshift seed
 * @param maxDateShift maximum shift duration (e.g., P14D)
 * @param dateShiftPreserve preservation strategy (NONE, WEEKDAY, DAYTIME)
 */
public record FpTransportMappingRequest(
    @NotBlank String patientIdentifier,
    @NotNull Set<String> transportIds,
    @NotNull Map<String, String> dateMappings,
    @NotBlank String dateShiftDomain,
    @NotNull Duration maxDateShift,
    @NotNull DateShiftPreserve dateShiftPreserve) {}
