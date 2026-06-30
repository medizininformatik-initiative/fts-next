package care.smith.fts.util.tca;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Batch response from TCA confirming transport mapping storage for multiple patients.
 *
 * <p>The transferIds are positional: {@code transferIds.get(i)} is the transferId for the i-th
 * request in the corresponding {@link TransportMappingsRequest}. A list (rather than a map keyed by
 * patient identifier) is required because a single patient can contribute several bundles (e.g.
 * paginated {@code $everything} responses), each needing its own transferId.
 *
 * @param transferIds the transferId of each request's transfer session, in request order
 */
public record TransportMappingsResponse(
    @NotNull(groups = TransportMappingsResponse.class) List<String> transferIds) {}
