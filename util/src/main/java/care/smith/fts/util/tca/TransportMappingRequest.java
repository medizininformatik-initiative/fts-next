package care.smith.fts.util.tca;

import care.smith.fts.api.DateShiftPreserve;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;

/**
 * Request from CDA to TCA for transport mappings.
 *
 * @param patientIdentifier the patient identifier
 * @param patientIdentifierSystem the patient identifier system
 * @param idMappings map of namespaced original ID → transport ID (originalID→tID)
 * @param dateMappings map of transport ID → original date value (tID→date)
 * @param tcaDomains TCA domain configuration
 * @param maxDateShift maximum date shift duration
 * @param dateShiftPreserve date shift preservation mode
 */
public record TransportMappingRequest(
    @NotNull(groups = TransportMappingRequest.class) String patientIdentifier,
    @NotNull(groups = TransportMappingRequest.class) String patientIdentifierSystem,
    @NotNull(groups = TransportMappingRequest.class) Map<String, String> idMappings,
    @NotNull(groups = TransportMappingRequest.class) Map<String, String> dateMappings,
    @NotNull(groups = TransportMappingRequest.class) TcaDomains tcaDomains,
    @NotNull(groups = TransportMappingRequest.class) Duration maxDateShift,
    @NotNull(groups = TransportMappingRequest.class) DateShiftPreserve dateShiftPreserve) {}
