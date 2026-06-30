package care.smith.fts.util.tca;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Batch request from CDA to TCA for transport mappings of multiple patients. Bundling several
 * patients into one request lets the TCA collapse the per-domain gPAS pseudonym lookups into a
 * single call per domain instead of one call per patient.
 *
 * @param requests the per-patient transport mapping requests
 */
public record TransportMappingsRequest(
    @NotNull(groups = TransportMappingsRequest.class) @Valid
        List<TransportMappingRequest> requests) {}
