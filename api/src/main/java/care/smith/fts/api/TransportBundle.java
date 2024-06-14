package care.smith.fts.api;

import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;

public record TransportBundle(Bundle bundle, Set<String> transportIds) {}
