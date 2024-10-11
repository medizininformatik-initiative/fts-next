package care.smith.fts.api;

import jakarta.validation.constraints.NotNull;
import org.hl7.fhir.r4.model.Bundle;

public record TransportBundle(
    /* Bundle of Patient and (some of) their Resources */
    @NotNull Bundle bundle,

    /* Name of the transport ID -> research ID lookup table */
    String transferId) {}
