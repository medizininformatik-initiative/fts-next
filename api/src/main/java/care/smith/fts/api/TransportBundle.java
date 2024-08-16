package care.smith.fts.api;

import org.hl7.fhir.r4.model.Bundle;

public record TransportBundle(
    /* Bundle of Patient and (some of) their Resources */
    Bundle bundle,

    /* Name of the original ID -> transport ID lookup table */
    String tIDMapName) {}
