package care.smith.fts.api;

import java.util.Set;
import org.hl7.fhir.instance.model.api.IBaseBundle;

public record TransportBundle<B extends IBaseBundle>(B bundle, Set<String> transportIds) {}
